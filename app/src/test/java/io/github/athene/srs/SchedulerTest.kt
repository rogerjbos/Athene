package io.github.athene.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val today = 20_000L

    @Test
    fun aNewItemAnsweredCorrectlyIsDueTomorrow() {
        val memory = Scheduler.review(null, correct = true, today = today)
        assertEquals(today + 1, memory.dueDay)
        assertEquals(1, memory.reps)
        assertEquals(0, memory.lapses)
    }

    @Test
    fun intervalsGrowWithEachCorrectReview() {
        var memory = Scheduler.review(null, correct = true, today = today)
        val intervals = mutableListOf(memory.intervalDays)
        repeat(5) {
            memory = Scheduler.review(memory, correct = true, today = memory.dueDay)
            intervals += memory.intervalDays
        }
        assertEquals(listOf(1, 3, 8, 20, 50, 125), intervals)
        assertEquals(intervals.sorted(), intervals)
    }

    @Test
    fun aWrongAnswerStartsOverAndLowersEase() {
        var memory = Scheduler.review(null, correct = true, today = today)
        memory = Scheduler.review(memory, correct = true, today = today + 1)
        val lapsed = Scheduler.review(memory, correct = false, today = today + 4)
        assertEquals(today + 5, lapsed.dueDay)
        assertEquals(0, lapsed.reps)
        assertEquals(1, lapsed.lapses)
        assertTrue(lapsed.ease < memory.ease)
        // After a lapse the item is relearned from the short intervals.
        assertEquals(1, Scheduler.review(lapsed, correct = true, today = today + 5).intervalDays)
    }

    @Test
    fun aNewItemAnsweredWronglyIsNotALapse() {
        val memory = Scheduler.review(null, correct = false, today = today)
        assertEquals(0, memory.lapses)
        assertEquals(today + 1, memory.dueDay)
    }

    @Test
    fun easeNeverDropsBelowTheFloor() {
        var memory = Scheduler.review(null, correct = true, today = today)
        repeat(20) { memory = Scheduler.review(memory, correct = false, today = today) }
        assertEquals(Scheduler.MIN_EASE, memory.ease, 1e-9)
    }

    @Test
    fun intervalsAreCappedAtAYear() {
        var memory = Scheduler.review(null, correct = true, today = today)
        repeat(30) { memory = Scheduler.review(memory, correct = true, today = memory.dueDay) }
        assertEquals(365, memory.intervalDays)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        assertEquals(0, currentStreak(emptySet(), today))
        assertEquals(1, currentStreak(setOf(today), today))
        assertEquals(3, currentStreak(setOf(today, today - 1, today - 2, today - 4), today))
        // Nothing done yet today: yesterday's streak still stands.
        assertEquals(2, currentStreak(setOf(today - 1, today - 2), today))
        // A missed day ends it.
        assertEquals(0, currentStreak(setOf(today - 2, today - 3), today))
    }
}
