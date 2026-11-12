package com.bamnat.core.imports

import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [IMPORT-01] Events read from the phone's calendars (Samsung Calendar, Google Calendar and any app
 * that writes to Android's calendar store), as the importer sees them. [key] stays the same for the
 * same occurrence, so an event brought in once is never brought in twice.
 */
data class DeviceEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
) {
    val key: String get() = "$eventId@${begin}"
    val date: LocalDate get() = begin.toLocalDate()
    /** Last day it covers; an all-day event ends at midnight of the day after. */
    val lastDate: LocalDate get() {
        val e = if (allDay || (end.toLocalTime() == LocalTime.MIDNIGHT && end.isAfter(begin))) end.minusMinutes(1) else end
        return maxOf(begin.toLocalDate(), e.toLocalDate())
    }
}

/** A calendar on the phone, to pick from. */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val colorArgb: Long? = null,
) {
    /** Samsung's own calendars, so the picker can say "삼성 캘린더". */
    val samsung: Boolean get() = accountType.startsWith("com.osp.app.signin") || accountType.contains("samsung", ignoreCase = true) ||
        (accountType == "LOCAL" && (name.contains("내 캘린더") || name.contains("My calendar", ignoreCase = true)))
    /** Holidays and birthdays the calendar app adds by itself: the app has its own holidays [FEAT-05]. */
    val generated: Boolean get() = GENERATED.containsMatchIn(name) || GENERATED.containsMatchIn(account)

    val source: String get() = when {
        samsung -> "삼성 캘린더"
        accountType == "com.google" -> "Google 캘린더"
        accountType == "LOCAL" -> "휴대폰"
        else -> account.ifBlank { "기타" }
    }

    private companion object {
        val GENERATED = Regex("공휴일|휴일|holiday|birthday|생일|기념일|음력|lunar|week number|주 번호", RegexOption.IGNORE_CASE)
    }
}

/**
 * [IMPORT-01] Reads calendar events as work where they plainly are work, and as memos otherwise.
 *
 * An event is a shift when its title is a shift's name or label ("야간", "N", "주"), a common word for
 * one ("나이트", "데이", "비번", "오프"), or when it is not all-day and its hours match a shift within
 * [TIME_SLACK_MINUTES] and its title is short or says 근무. Everything else becomes a memo on its
 * day with its time and place. One shift per day: a second one on the same day becomes a memo.
 */
object CalendarImport {
    /** 2.0.0 [RVL-30] (검토 SCAN-26·T07b-08) [LEAVE]: a 연차·반차·휴가 event goes in as leave, not as a plain day off. */
    enum class As { SHIFT, OFF, MEMO, LEAVE }

    data class Proposal(
        val event: DeviceEvent,
        val kind: As,
        /** The shift for [As.SHIFT]; for [As.OFF] the first off type, or null for a plain day off. */
        val shiftTypeId: Long? = null,
        /** Why, in a few words: "이름이 같아요", "시간이 같아요". */
        val reason: String,
        /** Already brought in before, or already what the calendar says: left out unless the user asks. */
        val skip: Skip? = null,
        /** 2.0.0 [RVL-30] One day of an event over several (여름휴가 8/4~8/8); null for the event's own day. */
        val day: LocalDate? = null,
        /** 2.0.0 [RVL-30] The leave for [As.LEAVE]. */
        val leave: Leave? = null,
    ) {
        val date: LocalDate get() = day ?: event.date
        /** 2.0.0 [RVL-30] What this proposal is remembered by once brought in: the event, and its day when it spans several. */
        val key: String get() = if (day == null || day == event.date) event.key else "${event.key}#$day"
    }

    enum class Skip(val label: String) { IMPORTED("이미 가져왔어요"), SAME("이미 같은 근무예요"), GENERATED("공휴일·생일 달력") }

    const val TIME_SLACK_MINUTES = 30L
    const val SHORT_TITLE = 6

    /** Words people write for a kind of shift, as the kind of shift they name. */
    private val WORDS: Map<String, Word> = buildMap {
        listOf("주간", "주", "데이", "day", "d", "오전", "아침").forEach { put(it, Word.DAY) }
        listOf("오후", "이브닝", "evening", "eve", "e", "저녁", "석", "중간", "미들", "middle", "m").forEach { put(it, Word.EVENING) }
        listOf("야간", "야", "나이트", "night", "n", "밤", "철야").forEach { put(it, Word.NIGHT) }
        listOf("휴무", "휴", "휴일", "비번", "비", "오프", "off", "o", "쉼", "쉬는날").forEach { put(it, Word.OFF) }
        listOf("당직", "당번", "당", "24", "24시간").forEach { put(it, Word.DUTY) }
    }

    private enum class Word { DAY, EVENING, NIGHT, OFF, DUTY }

    /** Lower case, no spaces, brackets, emoji or "근무". */
    fun normalize(title: String): String = title.lowercase()
        .replace(Regex("근무|출근|shift"), "")
        .replace(Regex("[\\s\\[\\]()<>{}·.,:;!?~*#/\\\\|_\\-]+"), "")
        .filter { it.isLetterOrDigit() }

    /**
     * 2.0.0 [RVL-30] (검토 SCAN-26) 연차·반차·휴가 and the leave words of a company calendar: the leave each is. 병가·경조·공가 are
     * paid by most companies' rules ([LeaveKind.PAID]); the user can change any proposal.
     */
    private fun leaveOf(n: String): Leave? = when (n) {
        in setOf("반반차", "반반") -> Leave(LeaveKind.ANNUAL, 1)
        in setOf("오전반차", "오전반", "am반차") -> Leave(LeaveKind.ANNUAL, 2, fromStart = true)
        in setOf("반차", "오후반차", "오후반", "pm반차") -> Leave(LeaveKind.ANNUAL, 2)
        in setOf("무급휴가", "무급") -> Leave(LeaveKind.UNPAID)
        in setOf("연차", "월차", "휴가", "연가", "연차휴가", "여름휴가", "하계휴가", "겨울휴가", "동계휴가", "정기휴가", "개인휴가",
            "annual", "annualleave", "vacation", "pto") -> Leave(LeaveKind.ANNUAL)
        in setOf("병가", "경조", "경조휴가", "경조사", "공가", "특별휴가", "보건휴가", "포상휴가") -> Leave(LeaveKind.PAID)
        else -> null
    }

    /** 2.0.0 [RVL-30] Days an event over several is split into for leave or days off; longer ones stay one memo. */
    const val MAX_SPLIT_DAYS = 31

    fun propose(
        events: List<DeviceEvent>,
        types: List<ShiftType>,
        imported: Set<String> = emptySet(),
        /** The shift type each date already shows, to leave out what would change nothing. */
        current: (LocalDate) -> Long? = { null },
        generatedCalendars: Set<Long> = emptySet(),
    ): List<Proposal> {
        val work = types.filter { it.hasTimes }
        val offs = types.filter { it.isOff }
        val taken = HashSet<LocalDate>()
        return events.sortedWith(compareBy({ it.begin }, { it.eventId })).flatMap { e ->
            val p = classify(e, work, offs)
            val span = if (e.allDay) java.time.temporal.ChronoUnit.DAYS.between(e.date, e.lastDate) + 1 else 1
            // 2.0.0 [RVL-30] (검토 SCAN-26·T07b-08) 여름휴가 8/4~8/8: each day its own leave (or day off), not one memo whose reason
            // said "같은 날 근무가 이미 있어요". Any other event over several days is one memo, and says why.
            val days = when {
                span <= 1 -> listOf(p)
                (p.kind == As.LEAVE || p.kind == As.OFF) && span <= MAX_SPLIT_DAYS ->
                    (0 until span).map { i -> p.copy(day = e.date.plusDays(i)) }
                else -> listOf(p.copy(kind = As.MEMO, shiftTypeId = null, leave = null, reason = "여러 날 일정이라 메모로"))
            }
            days.map { d ->
                val proposal = if (d.kind == As.MEMO || taken.add(d.date)) d
                    else d.copy(kind = As.MEMO, shiftTypeId = null, leave = null, reason = "같은 날 근무가 이미 있어요")
                proposal.copy(skip = skipOf(proposal, imported, current, generatedCalendars, offs))
            }
        }
    }

    private fun skipOf(proposal: Proposal, imported: Set<String>, current: (LocalDate) -> Long?, generatedCalendars: Set<Long>, offs: List<ShiftType>): Skip? {
        return when {
            proposal.key in imported -> Skip.IMPORTED
            proposal.event.calendarId in generatedCalendars -> Skip.GENERATED
            proposal.kind == As.SHIFT && current(proposal.date) == proposal.shiftTypeId -> Skip.SAME
            proposal.kind == As.OFF && current(proposal.date)?.let { id -> offs.any { it.id == id } } == true -> Skip.SAME
            else -> null
        }
    }

    private fun classify(e: DeviceEvent, work: List<ShiftType>, offs: List<ShiftType>): Proposal {
        val n = normalize(e.title)
        if (n.isNotEmpty()) {
            (work + offs).firstOrNull { normalize(it.name) == n || normalize(it.label) == n }?.let { t ->
                return Proposal(e, if (t.isOff) As.OFF else As.SHIFT, t.id, "이름이 같아요")
            }
            leaveOf(n)?.let { l -> return Proposal(e, As.LEAVE, null, "휴가로 읽었어요", leave = l) }
            WORDS[n]?.let { w ->
                if (w == Word.OFF) return Proposal(e, As.OFF, offs.firstOrNull()?.id, "쉬는 날로 읽었어요")
                byWord(w, work)?.let { return Proposal(e, As.SHIFT, it.id, "‘${e.title.trim()}’를 ${it.name}으로 읽었어요") }
            }
        }
        if (!e.allDay) {
            val shortTitle = n.length <= SHORT_TITLE || e.title.contains("근무")
            byTime(e, work)?.takeIf { shortTitle }?.let { return Proposal(e, As.SHIFT, it.id, "시간이 ${it.name}과 같아요") }
        }
        return Proposal(e, As.MEMO, null, "메모로 넣어요")
    }

    private fun byWord(w: Word, work: List<ShiftType>): ShiftType? {
        fun named(vararg words: String) = work.firstOrNull { t -> words.any { normalize(t.name).contains(it) } }
        return when (w) {
            Word.DAY -> named("주간", "데이", "day", "오전") ?: work.filter { it.start!!.hour in 4..10 }.minByOrNull { it.start!! }
            Word.EVENING -> named("오후", "이브닝", "evening", "중간") ?: work.firstOrNull { it.start!!.hour in 11..17 }
            Word.NIGHT -> named("야간", "나이트", "night") ?: work.firstOrNull { it.start!!.hour >= 18 || it.start!!.hour < 4 }
            Word.DUTY -> named("당직", "당번", "24") ?: work.firstOrNull { it.durationMinutes >= 20 * 60 }
            Word.OFF -> null
        }
    }

    private fun byTime(e: DeviceEvent, work: List<ShiftType>): ShiftType? = work.minByOrNull { t ->
        gap(e.begin.toLocalTime(), t.start!!) + gap(e.end.toLocalTime(), t.end!!)
    }?.takeIf { t -> gap(e.begin.toLocalTime(), t.start!!) <= TIME_SLACK_MINUTES && gap(e.end.toLocalTime(), t.end!!) <= TIME_SLACK_MINUTES }

    /** Minutes between two clock times, the short way round midnight. */
    private fun gap(a: LocalTime, b: LocalTime): Long {
        val d = Math.abs(Duration.between(a, b).toMinutes())
        return minOf(d, 24 * 60 - d)
    }

    /** The memo an event becomes: its title, then when and where. */
    fun memoText(e: DeviceEvent): String = buildString {
        append(e.title.trim().ifBlank { "(제목 없음)" })
        val hm = { t: LocalDateTime -> "%02d:%02d".format(t.hour, t.minute) }
        val time = when {
            e.allDay && e.lastDate != e.date -> "~${e.lastDate.monthValue}/${e.lastDate.dayOfMonth}"
            e.allDay -> null
            e.end.toLocalDate() != e.begin.toLocalDate() -> "${hm(e.begin)}–${e.end.monthValue}/${e.end.dayOfMonth} ${hm(e.end)}"
            else -> "${hm(e.begin)}–${hm(e.end)}"
        }
        if (time != null) append("\n").append(time)
        if (e.location.isNotBlank()) append(if (time != null) " · " else "\n").append(e.location.trim())
        if (e.description.isNotBlank()) append("\n").append(e.description.trim().take(MAX_DESCRIPTION))
    }

    private const val MAX_DESCRIPTION = 500

    /** How many of each kind will come in, for the button: "근무 12 · 메모 5". */
    data class Count(val shifts: Int, val offs: Int, val memos: Int, val leaves: Int = 0) {
        val total: Int get() = shifts + offs + memos + leaves
    }

    fun count(proposals: List<Proposal>): Count {
        val todo = proposals.filter { it.skip == null }
        return Count(todo.count { it.kind == As.SHIFT }, todo.count { it.kind == As.OFF }, todo.count { it.kind == As.MEMO }, todo.count { it.kind == As.LEAVE })
    }
}
