package com.bamnat.app.share

import com.bamnat.core.model.ShiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import com.bamnat.core.time.Today

/** [SHR-02] A shift as someone else's link describes it. */
data class SharedType(val id: Long, val name: String, val short: String, val color: Long, val time: String?, val off: Boolean, val icon: String?) {
    /** The same shift as this app draws it, for the calendar's own badges. */
    fun asShiftType(): ShiftType {
        val parts = time?.split("-")
        fun at(s: String?) = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return ShiftType(-id - 1, name, short, color, at(parts?.getOrNull(0)), at(parts?.getOrNull(1)), isOff = off, icon = icon)
    }
}

data class SharedExtra(val name: String, val time: String?, val minutes: Int?)

data class SharedDay(
    val date: LocalDate,
    val type: Long?,
    val second: Long?,
    val leave: String?,
    val memo: String?,
    val extras: List<SharedExtra>,
    /** Whether the day has no work at all as the sharer's app sees it [SHR-04]; null on links made before 1.10. */
    val rest: Boolean?,
) {
    /** Leave for the whole day: 반차, 반반차 and 0.75일 leave part of it for work. */
    val fullLeave: Boolean get() = leave != null && !leave.contains("반") && !leave.startsWith("0.75")
}

/** [SHR-02] Someone's shared schedule: the shifts, and what else they ticked [SHR-01]. */
data class SharedSchedule(
    val title: String,
    val types: List<SharedType>,
    val days: Map<LocalDate, SharedDay>,
    val pay: List<SharedPay>,
    val shows: Set<String>,
    /** [SHR-11] What [pay] is: 급여, 일감 수입 or 매출. */
    val payKind: com.bamnat.core.work.MoneyShare = com.bamnat.core.work.MoneyShare.PAY,
    /** [SHR-20] 1.33: who the sharer made it for; null for a link from before 1.33. */
    val audience: ShareAudience? = null,
    /** [SHR-20] A coworker's company setup file (no amounts), to put in with 같은 조예요·다른 조예요. */
    val setup: String? = null,
    /** 1.37 [SHR-31] The sender's 테마 (looks only), to try on; null when the link has none or it could not be read. */
    val look: com.bamnat.core.recipe.Recipe? = null,
    /** 1.37 [SHR-32] The sender's 근무 설정 (근무 꾸미기·알람 시각 틀·근무 주기), to pick from; never pay. */
    val kit: com.bamnat.core.recipe.Recipe? = null,
    /** 1.38 [SLEEP-11] 지금 자는 중 as the sender's app last sent it; shown only while [SleepNow.shownAt]. */
    val sleepNow: SleepNow? = null,
) {
    private val byId = types.associateBy { it.id }
    fun type(id: Long?): SharedType? = id?.let(byId::get)

    val first: LocalDate? = days.keys.minOrNull()
    val last: LocalDate? = days.keys.maxOrNull()
    fun covers(date: LocalDate): Boolean = first != null && last != null && !date.isBefore(first) && !date.isAfter(last)
    fun covers(month: YearMonth): Boolean = first != null && last != null &&
        !month.isBefore(YearMonth.from(first)) && !month.isAfter(YearMonth.from(last))

    /**
     * [SHR-04] Whether they have the day off: what their app said, else no shift, an off shift or
     * leave that is not a half day, with nothing covered. Null outside the dates the link covers.
     */
    fun isRest(date: LocalDate): Boolean? {
        if (!covers(date)) return null
        val d = days[date] ?: return true
        d.rest?.let { return it }
        if (d.second != null || d.extras.isNotEmpty()) return false
        if (d.fullLeave) return true
        return d.type == null || type(d.type)?.off == true
    }

    companion object {
        /** Reads the server's schedule JSON, skipping anything it does not know. */
        fun parse(o: JSONObject): SharedSchedule {
            val types = o.optJSONArray("types").objects().mapNotNull { t ->
                val id = t.optLong("id", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return@mapNotNull null
                val color = t.optString("color").removePrefix("#").toLongOrNull(16)?.let { 0xFF000000 or it } ?: 0xFF9E9E9E
                SharedType(id, t.optString("name"), t.optString("short").ifEmpty { t.optString("name").take(1) }, color,
                    t.str("time"), t.optBoolean("off"), t.str("icon"))
            }
            val days = o.optJSONArray("days").objects().mapNotNull { d ->
                val date = runCatching { LocalDate.parse(d.optString("date")) }.getOrNull() ?: return@mapNotNull null
                SharedDay(
                    date = date,
                    type = d.long("type"),
                    second = d.long("second"),
                    leave = d.str("leave"),
                    memo = d.str("memo"),
                    // [SHR-11] A freelancer's jobs show where extra work does: a name and a time on the day.
                    extras = d.optJSONArray("extras").objects().map { e ->
                        SharedExtra(e.optString("name"), e.str("time"), e.optInt("minutes", 0).takeIf { it > 0 })
                    } + d.optJSONArray("jobs").objects().map { j -> SharedExtra(j.optString("name"), j.str("time"), null) },
                    rest = if (d.has("rest")) d.optBoolean("rest") else null,
                )
            }.associateBy { it.date }
            val pay = o.optJSONArray("pay").objects().mapNotNull { p ->
                val month = runCatching { YearMonth.parse(p.optString("month")) }.getOrNull() ?: return@mapNotNull null
                SharedPay(month, p.optLong("amount"), p.optString("label"))
            }
            val shows = o.optJSONArray("shows")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
            // [SHR-20] Only a company file is taken as a setup.
            val setup = o.str("setup")?.takeIf { com.bamnat.app.data.CompanyShare.isCompanyFile(it) }
            return SharedSchedule(o.optString("title"), types, days, pay, shows, com.bamnat.core.work.MoneyShare.ofKey(o.str("payKind")),
                ShareAudience.of(o.str("audience")), setup,
                // 1.37 [SHR-31][SHR-32] Read as any recipe: what the codec refuses, or a kit's pay, is left out.
                com.bamnat.core.recipe.ShareKit.readLook(o.str("look")), com.bamnat.core.recipe.ShareKit.readKit(o.str("kit")),
                SleepNow.of(o.optJSONObject("sleepNow")))
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
        private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    }
}

/**
 * [SHR-02] A received calendar kept on this phone beside the user's own. [schedule] is null once the
 * link was cut or ran out: what the sharer took back does not stay here.
 */
data class ReceivedCalendar(
    val id: String,
    val title: String,
    val color: Long,
    val addedAt: Instant,
    val fetchedAt: Instant?,
    val updatedAt: Instant?,
    val expiresAt: Instant?,
    val raw: JSONObject?,
    /** [SHR-12] The server's tag for [raw], so the next look downloads it only when the sharer changed it. */
    val etag: String? = null,
    /** [SHR-13] Why the developer took the link down, when it was taken down rather than cut by the sharer. */
    val removedReason: String? = null,
    /** 1.38 PC 12.4.1 [P3-L-19] The user's own work comes from this link (the setup was started from it): settings say 받은 근무표, not 근무 주기. */
    val mine: Boolean = false,
    /** 1.39 [TEAM-22] '내 달력에 넣기' was pressed for this link once: a later change is offered too. */
    val adopted: Boolean = false,
    /** 1.39 [TEAM-22] What to do when the link changes ([RosterFollower.Mode] key); null is the default for [mine]/[adopted]. */
    val follow: String? = null,
    /** 1.39 [TEAM-22] The last change said over the calendar (put in, asked, or the link gone), until closed. */
    val note: FollowNote? = null,
) {
    val schedule: SharedSchedule? by lazy { raw?.let { runCatching { SharedSchedule.parse(it) }.getOrNull() } }
    val gone: Boolean get() = raw == null
    /** Like an account name: what the sharer typed, e.g. 민지. */
    val name: String get() = title.trim().ifEmpty { "이름 없음" }
    /** 민지의 근무표, as the shared page says. */
    val fullName: String get() = title.trim().takeIf { it.isNotEmpty() }?.let { "${it}의 근무표" } ?: "이름 없는 근무표"
    /** The letter in its avatar. */
    // [J133-79] A name like "[테스트] 민수" starts its face with 테, not "[".
    val initial: String get() = title.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "?"

    companion object {
        /** Avatar colours, one per calendar in the order they were added. */
        val COLORS = listOf(0xFFE8833A, 0xFF2E9D8F, 0xFF7C5CC4, 0xFFD45087, 0xFF3D7DD8, 0xFF8A9A2B)
    }
}

/**
 * 1.39 [TEAM-22] What a change of a followed link did, kept with the link until the user closes it: [kind] "done" (put in: [undo] holds
 * each day's override as it was), "ask" ([apply] waits for one press) or "gone" (the link was cut; the user's days stay). Day codes
 * are "n" (follows the pattern), "o" (off) or "s<id>" (that shift).
 */
data class FollowNote(
    val kind: String,
    val at: Instant,
    val lines: List<String>,
    val apply: Map<java.time.LocalDate, String> = emptyMap(),
    val undo: Map<java.time.LocalDate, String> = emptyMap(),
    val kept: List<java.time.LocalDate> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind); put("at", at.toString()); put("lines", JSONArray(lines))
        put("apply", JSONObject().apply { apply.forEach { (d, c) -> put(d.toString(), c) } })
        put("undo", JSONObject().apply { undo.forEach { (d, c) -> put(d.toString(), c) } })
        put("kept", JSONArray(kept.map { it.toString() }))
    }

    companion object {
        const val DONE = "done"
        const val ASK = "ask"
        const val GONE = "gone"

        fun fromJson(o: JSONObject?): FollowNote? = o?.let {
            runCatching {
                fun days(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associate { java.time.LocalDate.parse(it) to m.getString(it) } }.orEmpty()
                FollowNote(o.getString("kind"), Instant.parse(o.getString("at")),
                    o.optJSONArray("lines")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(), days("apply"), days("undo"),
                    o.optJSONArray("kept")?.let { a -> List(a.length()) { java.time.LocalDate.parse(a.getString(it)) } }.orEmpty())
            }.getOrNull()
        }
    }
}

/**
 * 1.38 PC 12.4.1 [P3-L-19] Where a member's work comes from, for settings: whose link, up to which day it holds, and when the next
 * month comes. [short] is the settings list's summary; otherwise the 내 근무 row's line.
 */
fun ReceivedCalendar.mineLine(short: Boolean = false): String {
    if (gone) return if (short) "받은 근무표 · 링크가 끊겼어요" else "${name} 링크가 끊겼어요 · 새 링크를 받으면 이어서 와요"
    // 1.38 PC 12.5 빈틈 3: a roster is a month's; '12월 2일까지' (the last day painted) read as if the rest of 12월 were missing.
    val first = schedule?.first?.let(java.time.YearMonth::from)
    val last = schedule?.last?.let(java.time.YearMonth::from)
    fun m(ym: java.time.YearMonth) = "${ym.monthValue}월"
    val months = when {
        first == null || last == null -> null
        first == last -> m(last)
        else -> "${m(first)}~${m(last)}"
    }
    return if (short) listOfNotNull("받은 근무표", name, months?.let { "$it 근무표" }).joinToString(" · ")
    else listOfNotNull("${name}에서 받아요", months?.let { "$it 근무표를 받았어요" }, "다음 달 근무표는 확정되면 알림으로 와요").joinToString(" · ")
}

/**
 * [SHR-02] Received calendars in one file under no-backup storage: someone else's schedule is not
 * the user's to back up. Which calendar is on screen lives only in memory, so the app always opens
 * on the user's own.
 */
class ReceivedCalendarStore(
    private val file: File,
    private val clock: () -> Instant = Today::instant,
    /** [SHR-02] Where the file is written: off the main thread, one write at a time, in order. */
    private val writer: java.util.concurrent.Executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "received-calendars").apply { isDaemon = true }
    },
) {
    private val _calendars = MutableStateFlow(load())
    val calendars: StateFlow<List<ReceivedCalendar>> = _calendars.asStateFlow()

    private val _viewing = MutableStateFlow<String?>(null)
    /** The received calendar on screen; null is the user's own. */
    val viewing: StateFlow<String?> = _viewing.asStateFlow()

    private val _opening = MutableStateFlow<String?>(null)
    /** A link id the app was opened with, waiting for the calendar to offer it. */
    val opening: StateFlow<String?> = _opening.asStateFlow()

    fun get(id: String): ReceivedCalendar? = _calendars.value.firstOrNull { it.id == id }

    /** 1.38 PC 12.4.1 [P3-L-19] The link the user's own work comes from, if the setup was started from one. */
    fun mine(): ReceivedCalendar? = _calendars.value.firstOrNull { it.mine }

    fun view(id: String?) {
        _viewing.value = id?.takeIf { i -> _calendars.value.any { it.id == i } }
    }

    fun open(id: String?) {
        _opening.value = id
    }

    /** Brings a kept calendar up to date; null when it was taken out meanwhile, which it stays. */
    fun update(id: String, share: ReceivedShare): ReceivedCalendar? = if (get(id) == null) null else put(id, share)

    /** Adds the link, or brings an existing one up to date, and returns it. */
    fun put(id: String, share: ReceivedShare, mine: Boolean = false): ReceivedCalendar {
        val now = clock()
        val old = get(id)
        val title = share.schedule.optString("title").trim()
        val color = old?.color ?: ReceivedCalendar.COLORS[_calendars.value.size % ReceivedCalendar.COLORS.size]
        val cal = ReceivedCalendar(id, title, color, old?.addedAt ?: now, now, share.updatedAt, share.expiresAt, share.schedule, share.etag,
            mine = mine || old?.mine == true, adopted = old?.adopted == true, follow = old?.follow, note = old?.note)
        write(if (old == null) _calendars.value + cal else _calendars.value.map { if (it.id == id) cal else it })
        return cal
    }

    /** [SHR-12] Asked and nothing changed: the copy here is current as of now. */
    fun touch(id: String) {
        val now = clock()
        if (get(id)?.gone == false) write(_calendars.value.map { if (it.id == id) it.copy(fetchedAt = now) else it })
    }

    /** The sharer cut the link or it ran out: the schedule goes, the entry stays to say so; [reason] the developer's, if they took it down. */
    fun markGone(id: String, reason: String? = null) {
        val now = clock()
        write(_calendars.value.map { if (it.id == id) it.copy(raw = null, fetchedAt = now, etag = null, removedReason = reason) else it })
    }

    /** Clears what ran out by the phone's own clock, even with no internet to ask. */
    fun expire(now: Instant = clock()) {
        if (_calendars.value.any { !it.gone && it.expiresAt?.isBefore(now) == true }) {
            write(_calendars.value.map { if (!it.gone && it.expiresAt?.isBefore(now) == true) it.copy(raw = null, etag = null) else it })
        }
    }

    /** 1.39 [TEAM-22] '내 달력에 넣기' was pressed: a later change of this link is offered. */
    fun markAdopted(id: String) {
        if (get(id)?.adopted == false) write(_calendars.value.map { if (it.id == id) it.copy(adopted = true) else it })
    }

    /** 1.39 [TEAM-22] 바뀌면 바로 넣기 / 물어보기 / 넣지 않기 for this link. */
    fun setFollow(id: String, key: String?) {
        write(_calendars.value.map { if (it.id == id) it.copy(follow = key) else it })
    }

    /** 1.39 [TEAM-22] The change said over the calendar; null closes it. */
    fun setNote(id: String, note: FollowNote?) {
        write(_calendars.value.map { if (it.id == id) it.copy(note = note) else it })
    }

    fun remove(id: String) {
        write(_calendars.value.filter { it.id != id })
        if (_viewing.value == id) _viewing.value = null
    }

    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Updates the list at once and saves it in the background: up to ten schedules serialised on the
     * main thread froze the calendar while it opened or refreshed. Writes queued close together
     * collapse into one that saves the latest list.
     */
    private fun write(list: List<ReceivedCalendar>) {
        _calendars.value = list
        if (pending.compareAndSet(false, true)) writer.execute {
            pending.set(false)
            save(_calendars.value)
        }
    }

    private fun save(list: List<ReceivedCalendar>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): List<ReceivedCalendar> = runCatching {
        if (!file.exists()) return emptyList()
        val a = JSONArray(file.readText())
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::fromJson) }
    }.getOrDefault(emptyList())

    private fun ReceivedCalendar.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("color", color)
        put("addedAt", addedAt.toString())
        fetchedAt?.let { put("fetchedAt", it.toString()) }
        updatedAt?.let { put("updatedAt", it.toString()) }
        expiresAt?.let { put("expiresAt", it.toString()) }
        raw?.let { put("schedule", it) }
        etag?.let { put("etag", it) }
        removedReason?.let { put("removedReason", it) }
        if (mine) put("mine", true)
        if (adopted) put("adopted", true)
        follow?.let { put("follow", it) }
        note?.let { put("note", it.toJson()) }
    }

    private fun fromJson(o: JSONObject): ReceivedCalendar? {
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return null
        fun time(key: String) = o.optString(key).takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return ReceivedCalendar(id, o.optString("title"), o.optLong("color", ReceivedCalendar.COLORS[0]),
            time("addedAt") ?: Instant.EPOCH, time("fetchedAt"), time("updatedAt"), time("expiresAt"), o.optJSONObject("schedule"),
            o.optString("etag").takeIf { it.isNotEmpty() }, o.optString("removedReason").takeIf { it.isNotEmpty() }, o.optBoolean("mine", false),
            adopted = o.optBoolean("adopted", false), follow = o.optString("follow").takeIf { it.isNotEmpty() },
            note = FollowNote.fromJson(o.optJSONObject("note")))
    }

    companion object {
        const val MAX = 10
    }
}
