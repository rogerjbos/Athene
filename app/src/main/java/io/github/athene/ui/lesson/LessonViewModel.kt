package io.github.athene.ui.lesson

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.athene.data.Item
import io.github.athene.data.ItemState
import io.github.athene.data.LessonProgress
import io.github.athene.lesson.Build
import io.github.athene.lesson.Choice
import io.github.athene.lesson.Exercise
import io.github.athene.lesson.ExerciseGenerator
import io.github.athene.lesson.Grader
import io.github.athene.lesson.Intro
import io.github.athene.lesson.Speak
import io.github.athene.lesson.Type
import io.github.athene.lesson.Verdict
import io.github.athene.athene
import io.github.athene.speech.Listener
import io.github.athene.srs.Scheduler
import io.github.athene.srs.currentStreak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class Feedback(val verdict: Verdict, val correctAnswer: String, val given: String)

data class Summary(val xp: Int, val mistakes: Int, val streak: Int)

data class LessonUi(
    val loading: Boolean = true,
    val isReview: Boolean = false,
    /** Name of the language being learned, for instructions. */
    val languageName: String = "",
    val languageCode: String = "",
    /** BCP-47 tag for speech, e.g. "es-ES". */
    val locale: String = "",
    /** Whether the device can read this language aloud. */
    val hasVoice: Boolean = false,
    val exercise: Exercise? = null,
    /** Changes whenever a new exercise (or a retry of one) is shown; use it to reset input state. */
    val sequence: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val feedback: Feedback? = null,
    val summary: Summary? = null,
)

/** Runs one lesson (route argument "lessonId") or, without it, a review of the items that are due. */
class LessonViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val content = app.athene.content
    private val user = app.athene.user
    private val settings = app.athene.settings
    private val speaker = app.athene.speaker
    private val lessonId: String? = handle["lessonId"]

    private val queue = ArrayDeque<Exercise>()
    private val practised = LinkedHashMap<String, Item>()
    private val missed = mutableSetOf<String>()
    private var mistakes = 0
    private var finished = false

    private val _ui = MutableStateFlow(LessonUi(isReview = lessonId == null))
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val language = settings.language?.let { content.language(it) }
        if (language == null) {
            _ui.update { it.copy(loading = false) }
            return
        }
        val hasVoice = speaker.supports(language.locale)
        val generator = ExerciseGenerator(
            listening = hasVoice && settings.listening,
            speaking = settings.speaking && Listener(getApplication()).available,
            typing = language.code !in NO_TYPING,
        )
        val exercises = if (lessonId != null) {
            val lesson = content.lesson(lessonId)
            val items = content.lessonItems(lessonId)
            val ord = lesson?.ord ?: 1
            generator.lesson(items, content.shortItemsInLessons(language.code, ord - POOL_BEFORE, ord + POOL_AFTER))
        } else {
            val ids = user.dueItemIds(language.code, today(), REVIEW_SIZE)
            val items = content.items(ids)
            val ord = items.firstOrNull()?.let { content.lessonOrdOf(it.id) } ?: 1
            generator.review(items, content.shortItemsInLessons(language.code, ord - POOL_BEFORE, ord + POOL_AFTER))
        }
        queue += exercises
        exercises.flatMap { it.items }.forEach { practised[it.id] = it }
        _ui.update {
            it.copy(
                loading = false,
                languageName = language.name,
                languageCode = language.code,
                locale = language.locale,
                hasVoice = hasVoice,
                exercise = queue.firstOrNull(),
                total = queue.size,
            )
        }
        announce()
    }

    /** Reads [text] in the language being learned, if the device has a voice for it. */
    fun say(text: String, slow: Boolean = false) {
        val state = _ui.value
        if (state.hasVoice) speaker.speak(text, state.locale, slow)
    }

    /** Says what the new exercise is about when hearing it is the point. */
    private fun announce() {
        when (val exercise = _ui.value.exercise) {
            is Intro -> say(exercise.item.text)
            is Choice -> if (exercise.audio) say(exercise.item.text)
            is Build -> if (exercise.audio) say(exercise.item.text)
            else -> Unit
        }
    }

    /** Grades the learner's answer to the current exercise and shows the result. */
    fun submit(input: String) {
        val state = _ui.value
        val exercise = state.exercise ?: return
        if (state.feedback != null) return
        val (verdict, answer) = when (exercise) {
            is Choice -> (if (Grader.plain(input) == Grader.plain(exercise.answer)) Verdict.CORRECT else Verdict.WRONG) to exercise.answer
            is Build -> Grader.grade(input, exercise.answers, lenient = false) to exercise.answers.first()
            is Type -> Grader.grade(input, exercise.answers, lenient = true) to exercise.answers.first()
            // The screen only reports speech that was close enough; see heardWell().
            is Speak -> Verdict.CORRECT to exercise.item.text
            else -> return
        }
        if (!verdict.passed) {
            mistakes++
            missed += exercise.items.map { it.id }
        }
        _ui.update { it.copy(feedback = Feedback(verdict, answer, input)) }
        // Let the learner hear the answer whenever it is in the new language.
        val inTarget = when (exercise) {
            is Choice -> exercise.toTarget
            is Build -> exercise.toTarget
            is Type -> true
            else -> false
        }
        if (inTarget) say(exercise.items.first().text)
    }

    /** 0..100: how close the best of the recogniser's [transcriptions] is to the current speaking exercise. */
    fun spokenScore(transcriptions: List<String>): Int {
        val item = (_ui.value.exercise as? Speak)?.item ?: return 0
        // The recogniser may answer in kana where the text has kanji, so the reading counts as well.
        val targets = listOfNotNull(item.text, item.reading)
        return transcriptions.maxOfOrNull { heard -> targets.maxOf { Grader.similarity(heard, it) } } ?: 0
    }

    /** Drops the speaking exercises from this session, for when the learner cannot talk or the microphone fails. */
    fun skipSpeaking() {
        val removed = queue.count { it is Speak }
        queue.removeAll { it is Speak }
        _ui.update { it.copy(total = it.total - removed, feedback = null) }
        if (queue.isEmpty()) {
            finish()
        } else {
            _ui.update { it.copy(exercise = queue.first(), sequence = it.sequence + 1) }
            announce()
        }
    }

    /** A finished matching exercise; [missedIds] are the items that were paired wrongly at least once. */
    fun matchDone(missedIds: Set<String>) {
        mistakes += missedIds.size
        missed += missedIds
        next()
    }

    /** Moves on from an introduction or from the feedback on an answer. A failed exercise comes back at the end. */
    fun next() {
        if (queue.isEmpty()) return
        val exercise = queue.removeFirst()
        val failed = _ui.value.feedback?.verdict?.passed == false
        if (failed) queue.addLast(exercise)
        if (queue.isEmpty()) {
            finish()
            return
        }
        _ui.update {
            it.copy(
                exercise = queue.first(),
                sequence = it.sequence + 1,
                done = if (failed) it.done else it.done + 1,
                feedback = null,
            )
        }
        announce()
    }

    override fun onCleared() {
        speaker.stop()
    }

    private fun finish() {
        if (finished) return
        finished = true
        viewModelScope.launch {
            val today = today()
            val lang = _ui.value.languageCode
            val ids = practised.keys.toList()
            val known = user.states(ids).associateBy { it.itemId }
            user.saveStates(ids.mapNotNull { id ->
                val previous = known[id]?.memory
                val correct = id !in missed
                // Getting an item right before it is due (when a lesson is repeated) proves little: leave its schedule alone.
                if (previous != null && correct && previous.dueDay > today) null
                else ItemState.of(id, lang, Scheduler.review(previous, correct, today))
            })
            val xp = if (lessonId != null) {
                val best = user.progressOf(lessonId)?.mistakes
                user.saveProgress(LessonProgress(lessonId, lang, today, minOf(best ?: mistakes, mistakes)))
                LESSON_XP + if (mistakes == 0) PERFECT_BONUS else 0
            } else {
                ids.size.coerceIn(REVIEW_MIN_XP, REVIEW_MAX_XP)
            }
            user.addXp(today, xp)
            val active = user.activityNow().filter { it.xp > 0 }.map { it.day }.toSet()
            _ui.update {
                it.copy(
                    exercise = null,
                    feedback = null,
                    done = it.total,
                    summary = Summary(xp, mistakes, currentStreak(active, today)),
                )
            }
        }
    }

    private fun today() = LocalDate.now().toEpochDay()

    companion object {
        const val REVIEW_SIZE = 12
        const val LESSON_XP = 10
        const val PERFECT_BONUS = 5
        const val REVIEW_MIN_XP = 5
        const val REVIEW_MAX_XP = 12

        /** Similarity (0..100) from which spoken words count as said correctly. */
        const val SPOKEN_PASS = 75

        /** Languages that need an input method the learner may not have. */
        private val NO_TYPING = setOf("ja")
        private const val POOL_BEFORE = 8
        private const val POOL_AFTER = 1
    }
}
