package io.github.athene.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.athene.data.CourseUnit
import io.github.athene.data.Language
import io.github.athene.data.Lesson
import io.github.athene.data.LessonProgress
import io.github.athene.athene
import io.github.athene.srs.currentStreak
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class LessonStatus { DONE, CURRENT, LOCKED }

data class LessonRow(
    val lesson: Lesson,
    /** Number within its unit, from 1. */
    val number: Int,
    val status: LessonStatus,
)

data class UnitSection(val unit: CourseUnit, val lessons: List<LessonRow>)

data class HomeUi(
    val loading: Boolean = true,
    val language: Language? = null,
    val sections: List<UnitSection> = emptyList(),
    val dueCount: Int = 0,
    val learnedCount: Int = 0,
    val xpToday: Int = 0,
    val dailyGoal: Int = 0,
    val streak: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val content = app.athene.content
    private val user = app.athene.user
    private val settings = app.athene.settings

    private data class Selection(val language: String?, val goal: Int, val today: Long)

    private fun selection() = Selection(settings.language, settings.dailyGoal, LocalDate.now().toEpochDay())

    private val selected = MutableStateFlow(selection())

    /** Picks up a changed language, goal or date; call when the screen comes back into view. */
    fun refresh() {
        selected.value = selection()
    }

    /** Marks every lesson before [lessonId] as done, for learners who already know that material. */
    fun skipTo(lessonId: String) {
        viewModelScope.launch {
            val lang = settings.language ?: return@launch
            val lessons = content.lessons(lang)
            val target = lessons.firstOrNull { it.id == lessonId } ?: return@launch
            val today = LocalDate.now().toEpochDay()
            for (lesson in lessons) {
                if (lesson.ord < target.ord && user.progressOf(lesson.id) == null) {
                    user.saveProgress(LessonProgress(lesson.id, lang, today, mistakes = 0))
                }
            }
        }
    }

    private class Course(val language: Language, val units: List<CourseUnit>, val lessons: List<Lesson>)

    val ui: StateFlow<HomeUi> = selected.flatMapLatest { (code, goal, today) ->
        flow {
            val language = code?.let { content.language(it) }
            emit(language?.let { Course(it, content.units(it.code), content.lessons(it.code)) })
        }.flatMapLatest { course ->
            if (course == null) return@flatMapLatest flow { emit(HomeUi(loading = false)) }
            val lang = course.language.code
            combine(
                user.lessonProgress(lang),
                user.dueCount(lang, today),
                user.learnedCount(lang),
                user.activity(),
            ) { progress, due, learned, activity ->
                val done = progress.map { it.lessonId }.toSet()
                val current = course.lessons.firstOrNull { it.id !in done }
                val rows = course.lessons.groupBy { it.unitId }
                val sections = course.units.map { unit ->
                    UnitSection(
                        unit,
                        rows[unit.id].orEmpty().mapIndexed { index, lesson ->
                            val status = when {
                                lesson.id in done -> LessonStatus.DONE
                                lesson.id == current?.id -> LessonStatus.CURRENT
                                else -> LessonStatus.LOCKED
                            }
                            LessonRow(lesson, index + 1, status)
                        },
                    )
                }
                HomeUi(
                    loading = false,
                    language = course.language,
                    sections = sections,
                    dueCount = due,
                    learnedCount = learned,
                    xpToday = activity.firstOrNull { it.day == today }?.xp ?: 0,
                    dailyGoal = goal,
                    streak = currentStreak(activity.filter { it.xp > 0 }.map { it.day }.toSet(), today),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}
