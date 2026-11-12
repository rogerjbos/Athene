package io.github.athene.lesson

import java.text.Normalizer

enum class Verdict {
    CORRECT,

    /** Right apart from accent marks. */
    ACCENTS,

    /** Right apart from a slip of the finger. */
    TYPO,
    WRONG;

    val passed: Boolean get() = this != WRONG
}

object Grader {
    private val punctuation = Regex("[^\\p{L}\\p{N}\\s]")
    private val spaces = Regex("\\s+")
    private val marks = Regex("\\p{Mn}+")

    /** Lower case, no punctuation, single spaces. Accents are kept. */
    fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
            .replace(punctuation, "").replace(spaces, " ").trim()

    /** [plain] without any spaces, for comparing text built from tiles in languages written without them. */
    fun squash(text: String): String = plain(text).replace(" ", "")

    /** [plain] with accent marks removed as well. */
    fun fold(text: String): String =
        Normalizer.normalize(plain(text), Normalizer.Form.NFD).replace(marks, "")

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** 0..100, as used for pronunciation scores. */
    fun similarity(a: String, b: String): Int {
        val x = fold(a).replace(" ", "")
        val y = fold(b).replace(" ", "")
        val longest = maxOf(x.length, y.length)
        return if (longest == 0) 100 else ((1.0 - distance(x, y).toDouble() / longest) * 100).toInt()
    }

    /**
     * Grades [input] against the accepted [answers]. With [lenient] (typed answers) missing accents
     * and small slips still pass; without it (answers built from tiles) only an exact match does.
     */
    fun grade(input: String, answers: List<String>, lenient: Boolean): Verdict {
        val given = plain(input)
        if (given.isEmpty()) return Verdict.WRONG
        if (answers.any { plain(it) == given }) return Verdict.CORRECT
        if (!lenient) {
            // Tiles carry no spacing of their own: 彼 は 学生 です is 彼は学生です.
            return if (answers.any { squash(it) == squash(input) }) Verdict.CORRECT else Verdict.WRONG
        }
        val folded = fold(input)
        if (answers.any { fold(it) == folded }) return Verdict.ACCENTS
        val close = answers.any { answer ->
            val target = fold(answer)
            val allowed = when {
                target.length >= 9 -> 2
                target.length >= 5 -> 1
                else -> 0
            }
            distance(folded, target) <= allowed
        }
        return if (close) Verdict.TYPO else Verdict.WRONG
    }
}
