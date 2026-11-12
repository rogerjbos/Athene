package io.github.athene.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.athene.srs.Memory
import kotlinx.coroutines.flow.Flow

// The learner's own data, kept apart from content.db so that new content never wipes it.
// Days are counted as java.time.LocalDate.toEpochDay() in the device's time zone.

/** Spaced-repetition state of one item the learner has met. */
@Entity(tableName = "item_state")
data class ItemState(
    @PrimaryKey val itemId: String,
    val lang: String,
    val ease: Double,
    val intervalDays: Int,
    val reps: Int,
    val lapses: Int,
    val dueDay: Long,
) {
    val memory: Memory get() = Memory(ease, intervalDays, reps, lapses, dueDay)

    companion object {
        fun of(itemId: String, lang: String, memory: Memory) =
            ItemState(itemId, lang, memory.ease, memory.intervalDays, memory.reps, memory.lapses, memory.dueDay)
    }
}

@Entity(tableName = "lesson_progress")
data class LessonProgress(
    @PrimaryKey val lessonId: String,
    val lang: String,
    val completedDay: Long,
    /** Wrong answers in the best run so far. */
    val mistakes: Int,
)

@Entity(tableName = "daily_activity")
data class DailyActivity(
    @PrimaryKey val day: Long,
    val xp: Int,
)

@Dao
abstract class UserDao {
    @Upsert
    abstract suspend fun saveStates(states: List<ItemState>)

    @Query("SELECT * FROM item_state WHERE itemId IN (:ids)")
    abstract suspend fun states(ids: List<String>): List<ItemState>

    @Query("SELECT itemId FROM item_state WHERE lang = :lang AND dueDay <= :today ORDER BY dueDay, itemId LIMIT :limit")
    abstract suspend fun dueItemIds(lang: String, today: Long, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM item_state WHERE lang = :lang AND dueDay <= :today")
    abstract fun dueCount(lang: String, today: Long): Flow<Int>

    @Query("SELECT * FROM item_state WHERE lang = :lang")
    abstract suspend fun allStates(lang: String): List<ItemState>

    @Query("SELECT COUNT(*) FROM item_state WHERE lang = :lang")
    abstract fun learnedCount(lang: String): Flow<Int>

    @Query("SELECT * FROM lesson_progress WHERE lang = :lang")
    abstract fun lessonProgress(lang: String): Flow<List<LessonProgress>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    abstract suspend fun progressOf(lessonId: String): LessonProgress?

    @Upsert
    abstract suspend fun saveProgress(progress: LessonProgress)

    @Query("SELECT * FROM daily_activity ORDER BY day DESC LIMIT 800")
    abstract fun activity(): Flow<List<DailyActivity>>

    @Query("SELECT * FROM daily_activity ORDER BY day DESC LIMIT 800")
    abstract suspend fun activityNow(): List<DailyActivity>

    @Query("SELECT xp FROM daily_activity WHERE day = :day")
    protected abstract suspend fun xpOn(day: Long): Int?

    @Upsert
    protected abstract suspend fun saveActivity(activity: DailyActivity)

    @Transaction
    open suspend fun addXp(day: Long, xp: Int) = saveActivity(DailyActivity(day, (xpOn(day) ?: 0) + xp))
}

@Database(
    entities = [ItemState::class, LessonProgress::class, DailyActivity::class],
    version = 1,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun dao(): UserDao

    companion object {
        fun open(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "user.db").build()
    }
}
