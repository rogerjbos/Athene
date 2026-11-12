package io.github.athene.lesson

import io.github.athene.data.Item
import kotlin.random.Random

sealed interface Exercise {
    /** The items this exercise practises. */
    val items: List<Item>
}

/** First meeting with a word or phrase; nothing to answer. */
data class Intro(val item: Item) : Exercise {
    override val items get() = listOf(item)
}

/**
 * Pick the right option. With [toTarget] the options are in the language being learned.
 * With [audio] the prompt is spoken instead of shown.
 */
data class Choice(
    val item: Item,
    val prompt: String,
    val toTarget: Boolean,
    val options: List<String>,
    val answer: String,
    val audio: Boolean = false,
) : Exercise {
    override val items get() = listOf(item)
}

/** Pair each word with its meaning. */
data class Match(override val items: List<Item>) : Exercise

/**
 * Put word tiles in order to translate [prompt]. With [audio] the prompt is the sentence itself,
 * spoken instead of shown, and the tiles spell out what was heard.
 */
data class Build(
    val item: Item,
    val prompt: String,
    val toTarget: Boolean,
    val tiles: List<String>,
    val answers: List<String>,
    val audio: Boolean = false,
) : Exercise {
    override val items get() = listOf(item)
}

/** Type the word or phrase that means [prompt]. */
data class Type(val item: Item, val prompt: String, val answers: List<String>) : Exercise {
    override val items get() = listOf(item)
}

/** Say [item] aloud. Never counts against the learner: speech recognition is too unreliable for that. */
data class Speak(val item: Item) : Exercise {
    override val items get() = listOf(item)
}

/**
 * Turns items into a run of exercises.
 *
 * [pool] is other words and phrases of the same language; wrong options and spare tiles come from it.
 */
class ExerciseGenerator(
    private val random: Random = Random.Default,
    /** Include exercises that are heard; needs a text-to-speech voice. */
    private val listening: Boolean = false,
    /** Include exercises that are spoken; needs speech recognition. */
    private val speaking: Boolean = false,
    /** Include exercises that are typed; off for languages a plain keyboard cannot write. */
    private val typing: Boolean = true,
) {

    /** A first lesson on [items]: meet each word, recognise it, then produce it; sentences come last. */
    fun lesson(items: List<Item>, pool: List<Item>): List<Exercise> {
        val short = items.filterNot { it.isSentence }
        val sentences = items.filter { it.isSentence }
        val everything = (pool + items).distinctBy { it.id }
        val result = mutableListOf<Exercise>()

        for (group in short.chunked(3)) {
            group.forEachIndexed { index, item ->
                result += Intro(item)
                if (index > 0) result += choice(group[index - 1], toTarget = false, everything)
            }
            result += choice(group.last(), toTarget = false, everything, audio = listening)
        }
        if (short.size >= 3) result += Match(short.take(MATCH_PAIRS).shuffled(random))

        val production = short.shuffled(random).mapIndexed { index, item ->
            when {
                targetWords(item).size > 1 -> build(item, toTarget = true, items, everything)
                index % 2 == 0 || !typing -> choice(item, toTarget = true, everything)
                else -> type(item, everything)
            }
        }
        val reading = sentences.mapIndexed { index, item ->
            when (index % 3) {
                0 -> build(item, toTarget = false, items, everything)
                1 -> build(item, toTarget = true, items, everything)
                else -> build(item, toTarget = listening, items, everything, audio = listening)
            }
        }
        result += interleave(production, reading)
        if (speaking) {
            // Finish by saying the longest things learned: sentences if there are any, otherwise phrases.
            val sayable = sentences.ifEmpty { short.filter { targetWords(it).size > 1 || it.kind == Item.KIND_PHRASE } }
            result += sayable.takeLast(SPOKEN).map(::Speak)
        }
        return result
    }

    /** A review of items met before: one exercise each, in random order. */
    fun review(items: List<Item>, pool: List<Item>): List<Exercise> {
        val everything = (pool + items).distinctBy { it.id }
        val result = items.map { item ->
            when {
                item.isSentence -> when (random.nextInt(3)) {
                    0 -> build(item, toTarget = false, items, everything)
                    1 -> build(item, toTarget = true, items, everything)
                    else -> build(item, toTarget = listening, items, everything, audio = listening)
                }
                targetWords(item).size > 1 -> build(item, toTarget = true, items, everything)
                else -> when (random.nextInt(3)) {
                    0 -> choice(item, toTarget = false, everything, audio = listening && random.nextBoolean())
                    1 -> choice(item, toTarget = true, everything)
                    else -> if (typing) type(item, everything) else choice(item, toTarget = true, everything)
                }
            }
        }.shuffled(random)
        if (speaking) {
            val sayable = items.filter { targetWords(it).size > 1 || it.kind != Item.KIND_WORD }
            return result + sayable.shuffled(random).take(1).map(::Speak)
        }
        return result
    }

    private fun choice(item: Item, toTarget: Boolean, pool: List<Item>, audio: Boolean = false): Choice {
        val answer = if (toTarget) item.text else item.meaning
        // Never offer another word with the same meaning (or the same spelling) as a wrong option.
        val candidates = pool
            .filter { it.id != item.id && !it.isSentence }
            // Letters of a script are only confused with other letters, and words with other words.
            .filter { (it.note in SCRIPT_NOTES) == (item.note in SCRIPT_NOTES) }
            .filter { Grader.plain(it.meaning) != Grader.plain(item.meaning) && Grader.plain(it.text) != Grader.plain(item.text) }
            .shuffled(random)
            .sortedByDescending { (it.kind == item.kind).toInt() + (it.note == item.note).toInt() }
        val wrong = candidates.map { if (toTarget) it.text else it.meaning }
            .distinctBy { Grader.plain(it) }
            .take(OPTIONS - 1)
        return Choice(
            item = item,
            prompt = if (toTarget) item.meaning else item.text,
            toTarget = toTarget,
            options = (wrong + answer).shuffled(random),
            answer = answer,
            audio = audio,
        )
    }

    private fun type(item: Item, pool: List<Item>): Type {
        val synonyms = pool.filter { !it.isSentence && Grader.plain(it.meaning) == Grader.plain(item.meaning) }.map { it.text }
        return Type(item, item.meaning, (listOf(item.text) + synonyms).distinct())
    }

    private fun build(item: Item, toTarget: Boolean, lesson: List<Item>, pool: List<Item>, audio: Boolean = false): Build {
        val words = if (toTarget) targetTiles(item) else tilesOf(item.meaning)
        val used = words.map { it.lowercase() }.toSet()
        // Spare tiles: words from the rest of the lesson first, then from the wider pool.
        val others = (lesson.shuffled(random) + pool.shuffled(random)).filter { it.id != item.id }
        val spare = others.asSequence()
            .flatMap { if (toTarget) targetTiles(it) else tilesOf(it.meaning.replace(brackets, "")) }
            .filter { it.lowercase() !in used && it.length <= 12 }
            .distinctBy { it.lowercase() }
            .take(SPARE_TILES)
            .toList()
        return Build(
            item = item,
            prompt = if (toTarget && !audio) item.meaning else item.text,
            toTarget = toTarget,
            tiles = (words + spare).shuffled(random),
            answers = if (toTarget) listOf(item.text) else item.meanings,
            audio = audio,
        )
    }

    private fun interleave(a: List<Exercise>, b: List<Exercise>): List<Exercise> = buildList {
        for (index in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(index)?.let(::add)
            b.getOrNull(index)?.let(::add)
        }
    }

    private fun Boolean.toInt() = if (this) 1 else 0

    companion object {
        const val OPTIONS = 4
        const val MATCH_PAIRS = 6
        const val SPARE_TILES = 4
        const val SPOKEN = 2
        private val SCRIPT_NOTES = setOf("hiragana", "katakana")
        private val edges = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
        private val brackets = Regex("\\([^)]*\\)")
        private val names =setOf("Tom", "Mary", "María", "Maria", "I")

        /** The words of an item in the language being learned. Text without spaces relies on the recorded split. */
        fun targetWords(item: Item): List<String> =
            if (item.unspaced) item.surfaces.ifEmpty { listOf(item.text) } else wordsOf(item.text)

        /** [targetWords] as tiles; see [tilesOf]. */
        fun targetTiles(item: Item): List<String> = if (item.unspaced) targetWords(item) else tilesOf(item.text)

        /** The words of a text, without the punctuation around them. */
        fun wordsOf(text: String): List<String> =
            text.split(Regex("\\s+")).map { it.replace(edges, "") }.filter { it.isNotEmpty() }

        /** [wordsOf], with the sentence-initial capital removed so that it does not give the order away. */
        fun tilesOf(text: String): List<String> = wordsOf(text).mapIndexed { index, word ->
            val keep = index > 0 || word in names || word.startsWith("I'") || word.startsWith("I’")
            if (keep) word else word.replaceFirstChar { it.lowercase() }
        }
    }
}
