package io.github.athene.lesson

import io.github.athene.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExerciseGeneratorTest {
    private fun word(text: String, meaning: String, note: String = "noun") =
        Item("es:w:$text", "es", Item.KIND_WORD, text, null, meaning, "", note, "")

    private fun sentence(id: Int, text: String, meaning: String, alt: String = "") =
        Item("es:s:$id", "es", Item.KIND_SENTENCE, text, null, meaning, alt, "", "")

    private val words = listOf(
        word("casa", "house"), word("perro", "dog"), word("gato", "cat"),
        word("coche", "car"), word("auto", "car"), word("comer", "to eat", "verb"),
    )
    private val sentences = listOf(
        sentence(1, "El perro come.", "The dog eats.", "The dog is eating."),
        sentence(2, "Tengo un gato.", "I have a cat."),
    )
    private val pool = listOf(word("libro", "book"), word("agua", "water"), word("mesa", "table"), word("día", "day"))

    private fun lesson(seed: Int) = ExerciseGenerator(Random(seed)).lesson(words + sentences, pool)

    @Test
    fun everyItemIsIntroducedBeforeItIsTested() {
        repeat(20) { seed ->
            val introduced = mutableSetOf<String>()
            for (exercise in lesson(seed)) {
                if (exercise is Intro) {
                    introduced += exercise.item.id
                } else {
                    for (item in exercise.items.filterNot { it.isSentence }) {
                        assertTrue("${item.text} tested before its introduction", item.id in introduced)
                    }
                }
            }
            assertEquals(words.map { it.id }.toSet(), introduced)
        }
    }

    @Test
    fun everyItemIsPractisedAndSentencesComeAfterTheWords() {
        val exercises = lesson(1)
        val practised = exercises.filterNot { it is Intro }.flatMap { it.items }.map { it.id }.toSet()
        assertEquals((words + sentences).map { it.id }.toSet(), practised)
        val lastIntro = exercises.indexOfLast { it is Intro }
        val firstSentence = exercises.indexOfFirst { it.items.any { item -> item.isSentence } }
        assertTrue(firstSentence > lastIntro)
    }

    @Test
    fun choicesHaveOneRightAnswerAndNoLookalikes() {
        repeat(20) { seed ->
            for (choice in lesson(seed).filterIsInstance<Choice>()) {
                assertEquals(ExerciseGenerator.OPTIONS, choice.options.size)
                assertEquals(choice.options.size, choice.options.toSet().size)
                assertEquals(1, choice.options.count { it == choice.answer })
                // "coche" and "auto" both mean "car": neither may be offered as a wrong answer for the other.
                if (choice.item.meaning == "car") {
                    val wrong = choice.options - choice.answer
                    assertFalse(wrong.any { it in setOf("coche", "auto", "car") })
                }
            }
        }
    }

    @Test
    fun buildTilesCanSpellTheAnswer() {
        repeat(20) { seed ->
            for (build in lesson(seed).filterIsInstance<Build>()) {
                val needed = ExerciseGenerator.tilesOf(build.answers.first())
                val bank = build.tiles.toMutableList()
                for (tile in needed) assertTrue("missing tile $tile", bank.remove(tile))
                assertEquals(Verdict.CORRECT, Grader.grade(needed.joinToString(" "), build.answers, lenient = false))
                // The spare tiles left over must not repeat a needed word.
                assertTrue(bank.none { spare -> needed.any { it.equals(spare, ignoreCase = true) } })
            }
        }
    }

    @Test
    fun typedAnswersAcceptWordsWithTheSameMeaning() {
        val types = (0 until 20).flatMap { lesson(it).filterIsInstance<Type>() }
        val car = types.first { it.item.meaning == "car" }
        assertEquals(setOf("coche", "auto"), car.answers.toSet())
        assertEquals(car.item.text, car.answers.first())
    }

    @Test
    fun aReviewHasOneExercisePerItem() {
        val items = words + sentences
        val exercises = ExerciseGenerator(Random(3)).review(items, pool)
        assertEquals(items.size, exercises.size)
        assertEquals(items.map { it.id }.toSet(), exercises.flatMap { it.items }.map { it.id }.toSet())
        assertTrue(exercises.none { it is Intro })
    }

    @Test
    fun withoutSpeechThereIsNothingToHearOrSay() {
        repeat(20) { seed ->
            val exercises = lesson(seed) + ExerciseGenerator(Random(seed)).review(words + sentences, pool)
            assertTrue(exercises.none { it is Speak })
            assertTrue(exercises.filterIsInstance<Choice>().none { it.audio })
            assertTrue(exercises.filterIsInstance<Build>().none { it.audio })
        }
    }

    @Test
    fun listeningExercisesAreAnsweredInTheLanguageHeard() {
        val three = sentences + sentence(3, "No tengo coche.", "I have no car.")
        val exercises = ExerciseGenerator(Random(5), listening = true).lesson(words + three, pool)
        val heardWords = exercises.filterIsInstance<Choice>().filter { it.audio }
        assertTrue(heardWords.isNotEmpty())
        // A heard word is answered with its meaning.
        assertTrue(heardWords.all { !it.toTarget && it.answer == it.item.meaning })
        val heardSentences = exercises.filterIsInstance<Build>().filter { it.audio }
        assertEquals(1, heardSentences.size)
        // A heard sentence is rebuilt word for word.
        assertTrue(heardSentences.all { it.toTarget && it.answers == listOf(it.item.text) })
    }

    @Test
    fun speakingComesLastAndUsesSentences() {
        val exercises = ExerciseGenerator(Random(5), speaking = true).lesson(words + sentences, pool)
        val spoken = exercises.takeLastWhile { it is Speak }
        assertEquals(sentences.map { it.id }, spoken.map { it.items.single().id })
        assertTrue(exercises.dropLast(spoken.size).none { it is Speak })
    }

    @Test
    fun textWithoutSpacesIsBuiltFromItsRecordedWordsAndNeverTyped() {
        val japanese = listOf(
            Item("ja:w:彼", "ja", Item.KIND_WORD, "彼", "かれ", "he", "", "pronoun", ""),
            Item("ja:w:学生", "ja", Item.KIND_WORD, "学生", "がくせい", "student", "", "noun", ""),
            Item("ja:w:です", "ja", Item.KIND_WORD, "です", null, "is (polite)", "", "", ""),
            Item("ja:w:は", "ja", Item.KIND_WORD, "は", null, "topic marker", "", "particle", ""),
            Item("ja:w:本", "ja", Item.KIND_WORD, "本", "ほん", "book", "", "noun", ""),
            Item("ja:s:1", "ja", Item.KIND_SENTENCE, "彼は学生です。", "かれはがくせいです。", "He is a student.", "", "",
                "彼\tかれ · he\nは\ttopic marker\n学生\tがくせい · student\nです\tis (polite)"),
            Item("ja:s:2", "ja", Item.KIND_SENTENCE, "彼は本です。", null, "He is a book.", "", "",
                "彼\tかれ · he\nは\ttopic marker\n本\tほん · book\nです\tis (polite)"),
        )
        repeat(20) { seed ->
            val generator = ExerciseGenerator(Random(seed), typing = false)
            val exercises = generator.lesson(japanese, emptyList()) + generator.review(japanese, emptyList())
            assertTrue(exercises.none { it is Type })
            val builds = exercises.filterIsInstance<Build>().filter { it.toTarget && it.item.id == "ja:s:1" }
            for (build in builds) {
                assertTrue(build.tiles.containsAll(listOf("彼", "は", "学生", "です")))
                assertEquals(Verdict.CORRECT, Grader.grade("彼 は 学生 です", build.answers, lenient = false))
                assertEquals(Verdict.WRONG, Grader.grade("は 彼 学生 です", build.answers, lenient = false))
            }
        }
    }

    @Test
    fun lettersAreNotOfferedAsWrongAnswersForWords() {
        val kana = listOf("あ" to "a", "い" to "i", "う" to "u", "え" to "e").map { (k, sound) ->
            Item("ja:k:$k", "ja", Item.KIND_WORD, k, null, sound, "", "hiragana", "")
        }
        val vocabulary = listOf("彼" to "he", "本" to "book", "人" to "person", "家" to "house", "車" to "car").map { (w, m) ->
            Item("ja:w:$w", "ja", Item.KIND_WORD, w, null, m, "", "noun", "")
        }
        repeat(10) { seed ->
            val choices = ExerciseGenerator(Random(seed), typing = false).lesson(vocabulary.take(3), kana + vocabulary)
                .filterIsInstance<Choice>()
            assertTrue(choices.isNotEmpty())
            for (choice in choices) assertTrue(choice.options.none { it in setOf("a", "i", "u", "e", "あ", "い", "う", "え") })
        }
    }

    @Test
    fun tilesDropTheLeadingCapitalButKeepNames() {
        assertEquals(listOf("the", "dog", "eats"), ExerciseGenerator.tilesOf("The dog eats."))
        assertEquals(listOf("I", "know", "Tom"), ExerciseGenerator.tilesOf("I know Tom."))
        assertEquals(listOf("dónde", "está", "el", "baño"), ExerciseGenerator.tilesOf("¿Dónde está el baño?"))
        assertEquals(listOf("Tom", "no", "está"), ExerciseGenerator.tilesOf("Tom no está."))
    }
}
