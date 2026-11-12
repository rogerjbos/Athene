package io.github.athene.data

import android.content.Context

/** Small per-user values that do not warrant a database table. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Code of the language being learned, or null before onboarding. */
    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** XP the learner aims to earn each day. */
    var dailyGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
        set(value) = prefs.edit().putInt(KEY_DAILY_GOAL, value).apply()

    /** Whether lessons include exercises that are heard. */
    var listening: Boolean
        get() = prefs.getBoolean(KEY_LISTENING, true)
        set(value) = prefs.edit().putBoolean(KEY_LISTENING, value).apply()

    /** Whether lessons include exercises that are spoken into the microphone. */
    var speaking: Boolean
        get() = prefs.getBoolean(KEY_SPEAKING, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAKING, value).apply()

    companion object {
        const val DEFAULT_DAILY_GOAL = 20
        val DAILY_GOALS = listOf(10, 20, 30, 50)
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_LISTENING = "listening"
        private const val KEY_SPEAKING = "speaking"
    }
}
