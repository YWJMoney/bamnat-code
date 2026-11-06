package com.bamnat.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** [SLP-06] One logged sleep; [endMillis] is null until the user taps "깼어요". Kept on the phone and in the backup file. */
@Entity(tableName = "sleep_logs", indices = [Index("startMillis")])
data class SleepLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long?,
    /** 1.38 [SLEEP-01] (DB 31) "tap" for the user's own taps and writing, "auto" for a sleep the phone noticed and the user kept. */
    @ColumnInfo(defaultValue = "tap") val source: String = SOURCE_TAP,
) {
    companion object {
        const val SOURCE_TAP = "tap"
        const val SOURCE_AUTO = "auto"
    }
}

/**
 * 1.38 [SLEEP-03] (DB 31) 아침 한 장: the user's answer about one sleep, matched to it by its times so a sleep from Health
 * Connect (no row of ours) can be answered too. On the phone and in the backup file only.
 */
@Entity(tableName = "sleep_checks", indices = [Index("endMillis")])
data class SleepCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val mood: Int?,
    @ColumnInfo(defaultValue = "") val tags: String = "",
    val note: String? = null,
    val atMillis: Long,
)

/**
 * 1.38 [SLEEP-07] (DB 31) A sound heard in the night (코골이·말·기침·이갈이·뒤척임), worked out on the phone. [file] is the short clip
 * kept for listening, under the app's own files, deleted after the days the user chose unless [keep]. Never sent anywhere.
 */
@Entity(tableName = "sleep_events", indices = [Index("startMillis")])
data class SleepEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val kind: String,
    /** How loud, in dB of the phone's microphone (relative, not calibrated). */
    val level: Float,
    val file: String? = null,
    @ColumnInfo(defaultValue = "0") val keep: Boolean = false,
)

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_logs WHERE startMillis >= :sinceMillis OR endMillis IS NULL ORDER BY startMillis DESC")
    fun observeSince(sinceMillis: Long): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_logs ORDER BY startMillis")
    suspend fun all(): List<SleepLogEntity>

    @Query("SELECT * FROM sleep_logs WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun open(): SleepLogEntity?

    @Insert
    suspend fun insert(log: SleepLogEntity): Long

    @Query("UPDATE sleep_logs SET endMillis = :endMillis WHERE id = :id")
    suspend fun finish(id: Long, endMillis: Long)

    /** [SLP-14] Undoes a "깼어요". */
    @Query("UPDATE sleep_logs SET endMillis = NULL WHERE id = :id")
    suspend fun reopen(id: Long)

    /** [SLP-16] A sleep the user corrects by hand. */
    @Query("UPDATE sleep_logs SET startMillis = :startMillis, endMillis = :endMillis WHERE id = :id")
    suspend fun update(id: Long, startMillis: Long, endMillis: Long?)

    @Query("DELETE FROM sleep_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sleep_logs")
    suspend fun clear()

    /** 1.38 [SLEEP-01] A sleep the phone noticed and the user kept (or trusted to go in). */
    @Query("INSERT INTO sleep_logs (startMillis, endMillis, source) VALUES (:startMillis, :endMillis, 'auto')")
    suspend fun insertAuto(startMillis: Long, endMillis: Long): Long

    // ---- 1.38 [SLEEP-03] 아침 한 장 ----

    @Query("SELECT * FROM sleep_checks WHERE endMillis >= :sinceMillis ORDER BY endMillis DESC")
    fun observeChecksSince(sinceMillis: Long): Flow<List<SleepCheckEntity>>

    @Query("SELECT * FROM sleep_checks ORDER BY endMillis")
    suspend fun allChecks(): List<SleepCheckEntity>

    @Insert
    suspend fun insertCheck(check: SleepCheckEntity): Long

    @Query("UPDATE sleep_checks SET mood = :mood, tags = :tags, note = :note, atMillis = :atMillis WHERE id = :id")
    suspend fun updateCheck(id: Long, mood: Int?, tags: String, note: String?, atMillis: Long)

    @Query("DELETE FROM sleep_checks WHERE id = :id")
    suspend fun deleteCheck(id: Long)

    @Query("DELETE FROM sleep_checks")
    suspend fun clearChecks()

    // ---- 1.38 [SLEEP-07] 소리 사건 ----

    @Query("SELECT * FROM sleep_events WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    fun observeEvents(fromMillis: Long, toMillis: Long): Flow<List<SleepEventEntity>>

    @Query("SELECT * FROM sleep_events ORDER BY startMillis")
    suspend fun allEvents(): List<SleepEventEntity>

    @Insert
    suspend fun insertEvent(event: SleepEventEntity): Long

    @Query("UPDATE sleep_events SET keep = :keep WHERE id = :id")
    suspend fun keepEvent(id: Long, keep: Boolean)

    /** Clips older than [beforeMillis] and not kept: their files go, the counts stay. */
    @Query("SELECT * FROM sleep_events WHERE file IS NOT NULL AND keep = 0 AND startMillis < :beforeMillis")
    suspend fun oldClips(beforeMillis: Long): List<SleepEventEntity>

    @Query("UPDATE sleep_events SET file = NULL WHERE id = :id")
    suspend fun dropClip(id: Long)

    @Query("DELETE FROM sleep_events")
    suspend fun clearEvents()
}
