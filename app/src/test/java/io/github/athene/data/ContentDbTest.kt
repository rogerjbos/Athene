package io.github.athene.data

import io.github.athene.lesson.ExerciseGenerator
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Checks the shipped content database: every course must be complete and learnable in order. */
class ContentDbTest {
    companion object {
        private lateinit var db: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val file = File("src/main/assets/content.db")
            assertTrue("run tools/build_content.py first: ${file.absolutePath}", file.exists())
            db = DriverManager.getConnection("jdbc:sqlite:${file.path}")
        }

        @AfterClass
        @JvmStatic
        fun close() = db.close()
    }

    private fun <T> query(sql: String, row: (java.sql.ResultSet) -> T): List<T> =
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(row(rs)) }
            }
        }

    private data class Row(val lang: String, val lessonOrd: Int, val id: String, val kind: String, val text: String,
                           val meaning: String, val tokens: String)

    private val rows by lazy {
        query(
            "SELECT lesson.lang, lesson.ord, item.id, item.kind, item.text, item.meaning, item.tokens " +
                "FROM lesson JOIN lesson_item ON lesson_item.lessonId = lesson.id JOIN item ON item.id = lesson_item.itemId " +
                "ORDER BY lesson.lang, lesson.ord, lesson_item.ord",
        ) { Row(it.getString(1), it.getInt(2), it.getString(3), it.getString(4), it.getString(5), it.getString(6), it.getString(7)) }
    }

    private val languages by lazy { query("SELECT code FROM language") { it.getString(1) } }

    @Test
    fun versionMatchesTheRoomDatabase() {
        assertEquals(listOf(3), query("PRAGMA user_version") { it.getInt(1) })
    }

    @Test
    fun everyLanguageHasACourse() {
        assertTrue(languages.isNotEmpty())
        for (lang in languages) {
            val items = rows.filter { it.lang == lang }
            assertTrue("$lang words", items.count { it.kind == "word" } >= 1000)
            assertTrue("$lang sentences", items.count { it.kind == "sentence" } >= 500)
            assertEquals("$lang phrases", 18, items.count { it.kind == "phrase" })
        }
    }

    @Test
    fun lessonsAreNumberedWithoutGapsAndBelongToUnits() {
        for (lang in languages) {
            val ords = query("SELECT ord FROM lesson WHERE lang = '$lang' ORDER BY ord") { it.getInt(1) }
            assertEquals(lang, (1..ords.size).toList(), ords)
        }
        assertEquals(0, query("SELECT COUNT(*) FROM lesson WHERE unitId NOT IN (SELECT id FROM unit)") { it.getInt(1) }.single())
        assertEquals(0, query("SELECT COUNT(*) FROM lesson_item WHERE itemId NOT IN (SELECT id FROM item)") { it.getInt(1) }.single())
    }

    @Test
    fun everyLessonHasEnoughToPractise() {
        for ((lesson, items) in rows.groupBy { it.lang to it.lessonOrd }) {
            val short = items.count { it.kind != "sentence" }
            assertTrue("$lesson has $short words", short in 3..8)
        }
    }

    @Test
    fun everyItemIsTaughtOnceAndHasTextAndMeaning() {
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        assertEquals(rows.size, query("SELECT COUNT(*) FROM item") { it.getInt(1) }.single())
        for (row in rows) {
            assertTrue(row.id, row.text.isNotBlank() && row.meaning.isNotBlank())
            assertTrue(row.id, row.id.startsWith("${row.lang}:"))
            assertEquals(row.id, row.text.trim(), row.text)
        }
    }

    @Test
    fun japaneseWordsInKanjiHaveReadings() {
        val kanji = Regex("[\\u4e00-\\u9fff]")
        val missing = query("SELECT text FROM item WHERE lang = 'ja' AND kind != 'phrase' AND reading IS NULL") { it.getString(1) }
            .filter { kanji.containsMatchIn(it) }
        assertTrue("no reading for $missing", missing.isEmpty())
    }

    @Test
    fun wordsInOneLessonHaveDistinctMeanings() {
        for ((lesson, items) in rows.filter { it.kind == "word" }.groupBy { it.lang to it.lessonOrd }) {
            assertEquals("$lesson", items.size, items.map { it.meaning }.toSet().size)
        }
    }

    /** A sentence may only appear once every one of its words has a meaning the learner can look up. */
    @Test
    fun everySentenceWordHasAHint() {
        for (row in rows.filter { it.kind == "sentence" }) {
            val lines = row.tokens.lines()
            if (row.lang == "ja") {
                // No spaces to go by: the recorded words must add up to the sentence itself.
                assertTrue("${row.id}: ${row.text}", lines.size in 3..9)
                val unpunctuated = row.text.filter { it.isLetterOrDigit() || it == 'ー' || it == '々' }
                assertEquals(row.id, unpunctuated, lines.joinToString("") { it.substringBefore('\t') }.filter { it.isLetterOrDigit() || it == 'ー' || it == '々' })
            } else {
                val words = ExerciseGenerator.wordsOf(row.text)
                assertTrue("${row.id}: ${row.text}", words.size in 3..8)
                assertEquals("${row.id}: ${row.text}", words.size, lines.size)
            }
            for (line in lines) {
                val parts = line.split('\t')
                assertEquals(row.id, 2, parts.size)
                assertTrue("${row.id}: no hint for ${parts[0]}", parts[1].isNotBlank())
            }
        }
    }
}
