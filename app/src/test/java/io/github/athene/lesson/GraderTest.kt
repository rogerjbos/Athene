package io.github.athene.lesson

import org.junit.Assert.assertEquals
import org.junit.Test

class GraderTest {
    @Test
    fun caseAndPunctuationAreIgnored() {
        assertEquals(Verdict.CORRECT, Grader.grade("dónde está el baño", listOf("¿Dónde está el baño?"), lenient = false))
        assertEquals(Verdict.CORRECT, Grader.grade("  ¡HOLA! ", listOf("Hola"), lenient = false))
        assertEquals(Verdict.CORRECT, Grader.grade("i dont know", listOf("I don't know."), lenient = false))
    }

    @Test
    fun missingAccentsPassWithANoteWhenTyping() {
        assertEquals(Verdict.ACCENTS, Grader.grade("adios", listOf("adiós"), lenient = true))
        assertEquals(Verdict.ACCENTS, Grader.grade("el", listOf("él"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("adios", listOf("adiós"), lenient = false))
    }

    @Test
    fun smallSlipsPassOnlyInLongerWords() {
        assertEquals(Verdict.TYPO, Grader.grade("grasias", listOf("gracias"), lenient = true))
        assertEquals(Verdict.TYPO, Grader.grade("necesitr", listOf("necesitar"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("lo", listOf("la"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("casa", listOf("cosa"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("gato", listOf("gracias"), lenient = true))
    }

    @Test
    fun anyAcceptedAnswerWill() {
        assertEquals(Verdict.CORRECT, Grader.grade("auto", listOf("coche", "auto"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("", listOf("coche"), lenient = true))
        assertEquals(Verdict.WRONG, Grader.grade("?!", listOf("coche"), lenient = true))
    }

    @Test
    fun similarityIsAPercentage() {
        assertEquals(100, Grader.similarity("¿Cómo estás?", "como estas"))
        assertEquals(0, Grader.similarity("abc", "xyz"))
        assertEquals(75, Grader.similarity("hola", "hole"))
    }
}
