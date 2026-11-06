package com.bamnat.app.data

import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.SleepCheckEntity
import com.bamnat.app.data.db.SleepLogEntity
import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepMood
import com.bamnat.core.sleep.SleepTag
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/** [SLP-06] The sleep log: two taps, kept on the phone and in the backup file only. */
class SleepRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = Today::millis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val dao = db.sleepDao()

    /** The last [days] days of sleep, newest first, plus a sleep still going on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun recent(days: Long = 30): Flow<List<SleepEntry>> =
        dao.observeSince(clock() - days * DAY_MILLIS).map { list -> list.map { it.toModel() } }

    /**
     * "잠들었어요": starts a sleep, and returns it. A sleep already open stays as it is (a second tap
     * changes nothing) [SLP-14]; one open for more than 16 hours, a missed "깼어요", is closed where
     * it stands: too long to count, but kept rather than deleted.
     */
    suspend fun fellAsleep(): Asleep {
        val now = clock()
        dao.open()?.let { open ->
            if (now - open.startMillis <= STALE_MILLIS) return Asleep(open.toModel(), started = false)
            dao.finish(open.id, now)
        }
        val id = dao.insert(SleepLogEntity(startMillis = now, endMillis = null))
        return Asleep(SleepEntry(id, local(now), null), started = true)
    }

    /** The open sleep after "잠들었어요", and whether this tap started it. */
    data class Asleep(val entry: SleepEntry, val started: Boolean)

    /**
     * "깼어요": ends the open sleep now, or at [atMillis] when the user says later when they woke
     * [SLP-14]; returns the finished sleep, or null when none was open.
     */
    suspend fun wokeUp(atMillis: Long? = null): SleepEntry? {
        val open = dao.open() ?: return null
        val now = clock()
        val end = (atMillis ?: now).coerceIn(open.startMillis, now)
        dao.finish(open.id, end)
        return SleepEntry(open.id, local(open.startMillis), local(end))
    }

    /** Undoes a "깼어요": the sleep is open again. */
    suspend fun reopen(id: Long) = dao.reopen(id)

    suspend fun delete(id: Long) = dao.delete(id)

    /** [SLP-16] A sleep the user forgot to tap, written in afterwards; returns its id. */
    suspend fun add(start: LocalDateTime, end: LocalDateTime): Long =
        dao.insert(SleepLogEntity(startMillis = millis(start), endMillis = millis(end)))

    /** [SLP-16] Moves a logged sleep's times; an open one may stay open. */
    suspend fun edit(id: Long, start: LocalDateTime, end: LocalDateTime?) = dao.update(id, millis(start), end?.let(::millis))

    /** Puts back a sleep deleted a moment ago (undo). */
    suspend fun restore(entry: SleepEntry): Long = dao.insert(SleepLogEntity(startMillis = millis(entry.start), endMillis = entry.end?.let(::millis),
        source = if (entry.auto) SleepLogEntity.SOURCE_AUTO else SleepLogEntity.SOURCE_TAP))

    /** 1.38 [SLEEP-01] A sleep the phone noticed, now in the log because the user said yes (or trusts it); returns its id. */
    suspend fun addAuto(start: LocalDateTime, end: LocalDateTime): Long = dao.insertAuto(millis(start), millis(end))

    // ---- 1.38 [SLEEP-03] 아침 한 장 ----

    /** The answers of the last [days] days, newest first. */
    fun checks(days: Long = 60): Flow<List<SleepCheck>> =
        dao.observeChecksSince(clock() - days * DAY_MILLIS).map { list -> list.map { it.toModel() } }

    /** Answers [mood] and [tags] for [sleep]; a second answer for the same sleep replaces the first. Returns the answer's id. */
    suspend fun answer(sleep: SleepEntry, mood: SleepMood?, tags: Set<SleepTag>, note: String?, existing: SleepCheck?): Long {
        val end = sleep.end ?: return 0
        val enc = SleepTag.encode(tags)
        return if (existing != null && existing.id != 0L) {
            dao.updateCheck(existing.id, mood?.value, enc, note, clock()); existing.id
        } else dao.insertCheck(SleepCheckEntity(startMillis = millis(sleep.start), endMillis = millis(end), mood = mood?.value, tags = enc, note = note, atMillis = clock()))
    }

    suspend fun unanswer(id: Long) = dao.deleteCheck(id)

    // ---- 1.38 [SLEEP-07] 소리 사건 ----

    /** The night's sounds between [from] and [to]. */
    fun events(from: LocalDateTime, to: LocalDateTime): Flow<List<com.bamnat.app.data.db.SleepEventEntity>> =
        dao.observeEvents(millis(from), millis(to))

    suspend fun addEvents(events: List<com.bamnat.app.data.db.SleepEventEntity>) = events.forEach { dao.insertEvent(it) }

    /** 보관: a clip the user kept stays past the 7 days. */
    suspend fun keepClip(id: Long, keep: Boolean) = dao.keepEvent(id, keep)

    /** The clips past [days] days and not kept: the files go (by [delete]), the times and kinds stay. Returns how many. */
    suspend fun dropOldClips(days: Long, delete: (String) -> Unit): Int {
        val old = dao.oldClips(clock() - days * DAY_MILLIS)
        old.forEach { e -> e.file?.let(delete); dao.dropClip(e.id) }
        return old.size
    }

    private fun SleepCheckEntity.toModel() = SleepCheck(id, local(startMillis), local(endMillis), SleepMood.of(mood), SleepTag.decode(tags), note)

    private fun SleepLogEntity.toModel() = SleepEntry(id, local(startMillis), endMillis?.let(::local), auto = source == SleepLogEntity.SOURCE_AUTO)
    private fun millis(t: LocalDateTime): Long = t.atZone(zone()).toInstant().toEpochMilli()
    private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val STALE_MILLIS = SleepDebt.MAX_SLEEP_MINUTES * 60 * 1000L
    }
}
