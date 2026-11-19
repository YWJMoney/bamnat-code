package com.bamnat.app.data

import android.content.ContentValues
import android.database.Cursor
import androidx.room.InvalidationTracker
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.time.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 2.0.0 [RVL-76] (검토 I12b-02) 날짜별 되돌리기: "어제 실수로 한 달을 지웠어요" is the most common accident, and 최근 지운 기록 only
 * keeps what was cleared in one go. Every calendar's days (근무 주기·바꾼 날·추가 근무) and its 가계부 lines are kept as they were at
 * each moment they settled after a change, for [KEEP_DAYS] days, so either can go back to "어제 이 시각" on its own.
 *
 * - Watching, not hooking: Room tells when the tables change ([InvalidationTracker]), whatever wrote them (a tap, a roster, 결제 알림,
 *   a restore), and [SETTLE_MILLIS] after the last change the tables are copied into a file of their own. A copy the same as the one
 *   before (a change undone) is not kept twice.
 * - Phone only: the files are in no-backup storage, never in a backup or sent anywhere.
 * - Going back first keeps the state now as a point of its own, so going back can itself be undone.
 */
class TimeMachine(
    private val dir: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = Today::millis,
    private val settleMillis: Long = SETTLE_MILLIS,
    /** How long after a calendar is first watched its first copy is taken (the app's start is left alone). */
    private val firstDelayMillis: Long = FIRST_DELAY_MILLIS,
    /** A calendar's database when it was not opened yet this run (an added one), to look back at and go back in. */
    private val opener: (String) -> AppDatabase? = { null },
) {
    enum class Part(val key: String, val tables: List<String>) {
        /**
         * 달력: the kinds of work, the work pattern, the days changed and their extra work. The kinds go with the days: a day or a
         * pattern step going back to a kind deleted since would otherwise point at nothing (deleting a kind by mistake comes back too).
         */
        DAYS("days", listOf("shift_types", "pattern", "day_entries", "day_extras")),
        /** 가계부: its lines. */
        MONEY("money", listOf("money_entries")),
    }

    /** A moment a [part] of a calendar can go back to. */
    data class Point(val calendarId: String, val part: Part, val at: Long, internal val file: File, internal val hash: String)

    /** What going back to a [Point] would change, against now. */
    data class Diff(
        /** [Part.DAYS]: the days whose work would change, in order. */
        val days: List<LocalDate> = emptyList(),
        /** [Part.DAYS]: whether the work pattern differs too. */
        val pattern: Boolean = false,
        /** [Part.DAYS]: whether the kinds of work (names, times, alarms) differ too. */
        val kinds: Boolean = false,
        /** [Part.MONEY]: lines that would come back, go, or change. */
        val back: Int = 0, val gone: Int = 0, val changed: Int = 0,
    ) {
        val isEmpty: Boolean get() = days.isEmpty() && !pattern && !kinds && back == 0 && gone == 0 && changed == 0
    }

    private val dbs = ConcurrentHashMap<String, AppDatabase>()

    private fun dbOf(calendarId: String): AppDatabase? = dbs[calendarId] ?: runCatching { opener(calendarId) }.getOrNull()?.also { watch(calendarId, it) }
    private val waiting = ConcurrentHashMap<String, Job>()
    private val lock = Mutex()

    /** Starts keeping [calendarId]'s moments ([db] is its open database); a calendar already watched is left as it is. */
    fun watch(calendarId: String, db: AppDatabase) {
        if (dbs.put(calendarId, db) === db) return
        for (part in Part.entries) {
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(part.tables.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) = changed(calendarId, part)
            })
            // The state before the first change from now on; one kept within the hour is enough.
            scope.launch {
                delay(firstDelayMillis)
                val last = points(calendarId, part, all = true).firstOrNull()
                if (last == null || clock() - last.at > HOUR_MILLIS) runCatching { keep(calendarId, part) }
            }
        }
    }

    /** Stops watching a calendar that was removed, and forgets its moments (최근 지운 기록 keeps the calendar itself). */
    fun forget(calendarId: String) {
        dbs.remove(calendarId)
        File(dir, safe(calendarId)).deleteRecursively()
    }

    private fun changed(calendarId: String, part: Part) {
        val key = "$calendarId/${part.key}"
        waiting.remove(key)?.cancel()
        waiting[key] = scope.launch {
            delay(settleMillis)
            runCatching { keep(calendarId, part) }
        }
    }

    /**
     * The moments to go back to, newest first. The newest copy, the same as now, is left out unless [all]: going back to it would
     * change nothing.
     */
    suspend fun points(calendarId: String, part: Part, all: Boolean = false): List<Point> = withContext(Dispatchers.IO) {
        val folder = File(dir, "${safe(calendarId)}/${part.key}")
        val list = (folder.listFiles() ?: emptyArray()).mapNotNull { f -> pointOf(calendarId, part, f) }.sortedByDescending { it.at }
        if (all) return@withContext list
        val now = runCatching { hashNow(calendarId, part) }.getOrNull()
        list.dropWhile { it.hash == now }
    }

    /** Copies [part] of [calendarId] now; null when nothing changed since the last copy or the calendar is not open. */
    suspend fun keep(calendarId: String, part: Part): Point? = lock.withLock {
        withContext(Dispatchers.IO) {
            val db = dbOf(calendarId) ?: return@withContext null
            val folder = File(dir, "${safe(calendarId)}/${part.key}").apply { mkdirs() }
            val at = clock()
            val tmp = File(folder, "$at.tmp")
            tmp.delete()
            var hash = ""
            db.runInTransaction(Runnable {
                val live = SupportDb(db.openHelper.writableDatabase)
                hash = hashOf(live, part)
                val last = (folder.listFiles() ?: emptyArray()).mapNotNull { pointOf(calendarId, part, it) }.maxByOrNull { it.at }
                if (last?.hash != hash) fileDb(tmp).use { snap -> part.tables.forEach { t -> dump(live, snap, t) } }
            })
            if (!tmp.exists()) return@withContext null
            val file = File(folder, "$at-$hash.db")
            if (!tmp.renameTo(file)) { tmp.delete(); return@withContext null }
            File(folder, "$at.tmp-journal").delete()
            prune(folder)
            Point(calendarId, part, at, file, hash)
        }
    }

    /** What going back to [point] would change against now. */
    suspend fun diff(point: Point): Diff = withContext(Dispatchers.IO) {
        val db = dbOf(point.calendarId) ?: return@withContext Diff()
        fileDb(point.file).use { snap ->
            val live = SupportDb(db.openHelper.readableDatabase)
            when (point.part) {
                Part.DAYS -> {
                    val was = byDay(snap)
                    val now = byDay(live)
                    val days = (was.keys + now.keys).filter { was[it] != now[it] }.sorted().map(LocalDate::ofEpochDay)
                    Diff(days = days, pattern = rows(snap, "pattern").sorted() != rows(live, "pattern").sorted(),
                        kinds = rows(snap, "shift_types").sorted() != rows(live, "shift_types").sorted())
                }
                Part.MONEY -> {
                    val was = byId(snap, "money_entries")
                    val now = byId(live, "money_entries")
                    Diff(
                        back = was.keys.count { it !in now }, gone = now.keys.count { it !in was },
                        changed = was.keys.count { it in now && was[it] != now[it] },
                    )
                }
            }
        }
    }

    /**
     * [part] of the calendar as it was at [point]. The state now is kept first and returned, to go back again ("되돌리기");
     * null when the calendar is not open or the copy cannot be read (nothing changed then).
     */
    suspend fun restore(point: Point): Point? {
        val db = dbOf(point.calendarId) ?: return null
        val diff = diff(point)
        // Kept first, or found when nothing changed since the last copy.
        val before = keep(point.calendarId, point.part) ?: points(point.calendarId, point.part, all = true).firstOrNull()
        return lock.withLock {
            withContext(Dispatchers.IO) {
                if (!point.file.exists()) return@withContext null
                fileDb(point.file).use { snap ->
                    val there = tablesOf(db.openHelper.writableDatabase)
                    val tables = point.part.tables.filter { it in there && it in tablesOf(snap) }
                    db.runInTransaction(Runnable {
                        val live = db.openHelper.writableDatabase
                        tables.reversed().forEach { live.execSQL("DELETE FROM `$it`") }
                        tables.forEach { copyRows(snap, SupportDb(live), it) }
                        // 최근 바꾼 기록 says it on each day it changed.
                        if (point.part == Part.DAYS && "day_changes" in there) {
                            val now = clock()
                            val words = "${said(point.at)} 때로 되돌림"
                            diff.days.forEach { d ->
                                live.insert("day_changes", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE,
                                    ContentValues().apply { put("epochDay", d.toEpochDay()); put("atMillis", now); put("text", words) })
                            }
                        }
                    })
                }
                before
            }
        }
    }

    /** Copies older than [KEEP_DAYS] go; past the last day only the last copy of each day stays; at most [MAX_POINTS]. */
    private fun prune(folder: File) {
        val now = clock()
        val all = (folder.listFiles() ?: emptyArray()).filter { it.name.endsWith(".db") }.mapNotNull { f -> atOf(f)?.let { it to f } }
            .sortedByDescending { it.first }
        val days = mutableSetOf<LocalDate>()
        var kept = 0
        for ((at, f) in all) {
            val stays = when {
                now - at > KEEP_DAYS * DAY_MILLIS -> false
                now - at <= DAY_MILLIS -> true
                else -> days.add(Instant.ofEpochMilli(at).atZone(Today.zone()).toLocalDate())
            }
            if (stays && kept < MAX_POINTS) kept++ else f.delete()
        }
    }

    private fun pointOf(calendarId: String, part: Part, f: File): Point? {
        if (!f.name.endsWith(".db")) return null
        val at = atOf(f) ?: return null
        return Point(calendarId, part, at, f, f.name.removeSuffix(".db").substringAfter('-', ""))
    }

    private fun atOf(f: File): Long? = f.name.substringBefore('-').removeSuffix(".db").toLongOrNull()

    private fun hashNow(calendarId: String, part: Part): String? {
        val db = dbOf(calendarId) ?: return null
        var hash: String? = null
        db.runInTransaction(Runnable { hash = hashOf(SupportDb(db.openHelper.writableDatabase), part) })
        return hash
    }

    private fun hashOf(db: Db, part: Part): String {
        val md = MessageDigest.getInstance("SHA-256")
        val there = tablesOf(db).toSet()
        for (t in part.tables.filter { it in there }) {
            md.update(t.toByteArray())
            rows(db, t).sorted().forEach { md.update(it.toByteArray()); md.update(0) }
        }
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    /** Each row of [table] as one line of its values, in column order. */
    private fun rows(db: Db, table: String): List<String> {
        if (table !in tablesOf(db)) return emptyList()
        return db.query("SELECT * FROM `$table`").use { c -> buildList { while (c.moveToNext()) add(line(c, skip = null)) } }
    }

    /** The day rows and their extra work by day (the extra work's own ids left out: they are made again on each write). */
    private fun byDay(db: Db): Map<Long, String> {
        val out = HashMap<Long, StringBuilder>()
        val there = tablesOf(db).toSet()
        for (t in listOf("day_entries", "day_extras").filter { it in there }) {
            db.query("SELECT * FROM `$t`").use { c ->
                val day = c.getColumnIndex("epochDay")
                while (c.moveToNext()) out.getOrPut(c.getLong(day)) { StringBuilder() }.append(t).append(':').append(line(c, skip = "id")).append('\n')
            }
        }
        return out.mapValues { (_, b) -> b.lines().sorted().joinToString("\n") }
    }

    private fun byId(db: Db, table: String): Map<Long, String> {
        if (table !in tablesOf(db)) return emptyMap()
        return db.query("SELECT * FROM `$table`").use { c ->
            val id = c.getColumnIndex("id")
            buildMap { while (c.moveToNext()) put(c.getLong(id), line(c, skip = null)) }
        }
    }

    private fun line(c: Cursor, skip: String?): String = (0 until c.columnCount).filter { c.getColumnName(it) != skip }.joinToString("\u0001") { i ->
        when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> "\u0000"
            Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).joinToString("") { "%02x".format(it) }
            else -> c.getString(i)
        }
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        const val KEEP_DAYS = 7L
        /** Changes this close together are one moment (a roster put in, 결제 알림 writing a few lines). */
        const val SETTLE_MILLIS = 60_000L
        const val FIRST_DELAY_MILLIS = 15_000L
        /** Per calendar and part: a day of every moment, then one a day for a week, never more than this. */
        const val MAX_POINTS = 60
        private const val HOUR_MILLIS = 60 * 60 * 1000L
        private const val DAY_MILLIS = 24 * HOUR_MILLIS

        /** "11월 12일 14:32", the moment as the user reads it. */
        fun said(at: Long): String {
            val t = Instant.ofEpochMilli(at).atZone(Today.zone())
            return "${t.monthValue}월 ${t.dayOfMonth}일 %02d:%02d".format(t.hour, t.minute)
        }
    }
}
