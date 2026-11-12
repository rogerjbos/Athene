package io.github.athene.srs

import kotlin.math.max
import kotlin.math.roundToInt

/** How well one item is remembered. Days are epoch days. */
data class Memory(
    /** Growth factor applied to the interval after each successful review. */
    val ease: Double,
    val intervalDays: Int,
    /** Successful reviews in a row. */
    val reps: Int,
    val lapses: Int,
    val dueDay: Long,
)

/** SM-2 style spaced repetition with a right/wrong grade. */
object Scheduler {
    const val START_EASE = 2.5
    const val MIN_EASE = 1.3
    private const val LAPSE_PENALTY = 0.2
    private const val MAX_INTERVAL = 365

    /** The memory after answering an item on [today]; [previous] is null the first time it is met. */
    fun review(previous: Memory?, correct: Boolean, today: Long): Memory {
        val ease = previous?.ease ?: START_EASE
        if (!correct) {
            return Memory(
                ease = max(MIN_EASE, ease - LAPSE_PENALTY),
                intervalDays = 1,
                reps = 0,
                lapses = (previous?.lapses ?: 0) + if (previous == null) 0 else 1,
                dueDay = today + 1,
            )
        }
        val reps = previous?.reps ?: 0
        val interval = when (reps) {
            0 -> 1
            1 -> 3
            else -> (previous!!.intervalDays * ease).roundToInt().coerceAtLeast(previous.intervalDays + 1).coerceAtMost(MAX_INTERVAL)
        }
        return Memory(ease, interval, reps + 1, previous?.lapses ?: 0, today + interval)
    }
}

/** Days in a row with activity, ending today or (if nothing has been done yet today) yesterday. */
fun currentStreak(activeDays: Set<Long>, today: Long): Int {
    var day = if (today in activeDays) today else today - 1
    var streak = 0
    while (day in activeDays) {
        streak++
        day--
    }
    return streak
}
