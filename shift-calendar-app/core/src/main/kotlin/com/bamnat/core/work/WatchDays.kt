package com.bamnat.core.work

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [SHFT-08][REL-09] One day as the watch shows it (tile, complication, today, week and month): the shift's
 * name and short name, its colour and times, or a day off or leave. The phone sends the next weeks; the
 * watch picks the days by its own date, so it keeps working away from the phone.
 */
data class WatchDay(
    val date: LocalDate,
    val name: String,
    val label: String,
    val colorArgb: Long,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val off: Boolean = false,
    /** [WEAR-03] The shift's icon key ([com.bamnat.core.model.ShiftIcon]), for the watch face's small image. */
    val icon: String? = null,
) {
    /** "주간 09:00–18:00", "휴무", "연차". */
    val line: String
        get() = if (start != null && end != null && !off) "$name ${hm(start)}–${hm(end)}" else name

    /** "09:00–18:00", or empty on a day off. */
    val times: String get() = if (start != null && end != null && !off) "${hm(start)}–${hm(end)}" else ""
}

/** [SHFT-08] Everything the phone sends the watch at once: the days, the next alarm and when it was sent. */
data class WatchPayload(
    val days: List<WatchDay>,
    val syncedAt: LocalDateTime,
    /** The next shift alarm the phone will ring, or null when none is set. */
    val nextAlarm: LocalDateTime? = null,
    /** What the alarm is for ("주간 근무"), or empty. */
    val alarmLabel: String = "",
    /** The calendar's name ("내 달력"). */
    val calendar: String = "",
    /**
     * [SHFT-08] When it was sent, as epoch milliseconds (0 from an older phone app): the phone's clock or time
     * zone changing does not make a newer copy look older, as [syncedAt] (the phone's local time) can.
     */
    val sentAtMillis: Long = 0,
    /** [WEAR-01] The phone's first day of the week (1 = Monday … 7 = Sunday), so the watch's month matches (I 리뷰 I23). */
    val weekStart: Int = 1,
    /** [WEAR-03] This Monday-to-Sunday week's hours so far and planned, in minutes; −1 when not sent (I 리뷰 N7 a). */
    val weekMinutes: Int = -1,
    /** [WEAR-03] The weekly limit the phone warns at (52시간 unless the user changed it). */
    val weekLimitMinutes: Int = 52 * 60,
    /** [WEAR-03] What the watch can write for an 알바 calendar today: [Clock.NONE], [Clock.IN] or [Clock.OUT] (I 리뷰 N7 d). */
    val clock: Clock = Clock.NONE,
    /** [WEAR-03] Today has an alarm that can be turned off from the tile (I 리뷰 N7 c). */
    val alarmToday: Boolean = false,
    /** [WEAR-06] 1.31 N2: the phone rings shift alarms on the watch too (워치 진동 알람), so the watch asks for its notices. */
    val ringOnWatch: Boolean = false,
    /** 1.38 [LEDH-14] 워치로 돈 적기: the spending categories the phone used most lately, for the watch's chips (empty: none sent). */
    val moneyCategories: List<String> = emptyList(),
    /** 1.38 [SLEEP-14] The next sleep as the phone's 오늘의 흐름 says it ("잘 시간 08:00–15:00", "낮잠 15:00–16:30"), or empty. */
    val sleepLine: String = "",
    /** 2.0.0 [RVI-08] An 알바 calendar (출근·퇴근 from the watch), so a new day's 출근 shows before the phone sends again. */
    val hourly: Boolean = false,
    /** 2.0.0 [RVI-09] What a working day is called on this calendar: 근무, 영업 (가게), 일감 (현장), 일정 (투자). */
    val noun: String = "근무",
    /** 2.0.0 [RVI-10] (검토 U03-10) The amounts most written lately on the phone's 가계부, for one-tap chips on 돈 적기. */
    val recentAmounts: List<Long> = emptyList(),
    /** 2.0.0 [RVI-94] (검토 I03b-04, G5-M3) 잠 타일: the phone keeps a sleep log (오늘의 흐름 on), so the wrist may write to it. */
    val sleepOn: Boolean = false,
    /** 2.0.0 [RVI-94] Asleep since then (잠들었어요 on the phone or the wrist), or null when awake. */
    val asleepSince: LocalDateTime? = null,
    /** 2.0.0 [RVI-95] (검토 I03b-04·I07ab-04, TASK-35) 오늘 할 일 타일: the phone's 할 일 위젯 list, at most [WatchTask.MAX]. */
    val tasks: List<WatchTask> = emptyList(),
    /** 2.0.0 [RVI-95] The list's head as the widget says it ("오늘 할 일 3개 · 1개 늦음"). */
    val taskHead: String = "",
) {
    enum class Clock { NONE, IN, OUT }
}

object WatchDays {
    /** Days ahead the phone sends at least, so a watch away from the phone a while still knows. */
    const val DAYS = 45

    /**
     * 1.32 [WEAR-04] 조사 대조 C16 (조사 11-4 "컴플리케이션에 다음 근무까지 n시간"): one stretch the complication shows one thing
     * in: before a shift, the time to its start; on it, the time to its end. [to] is the moment counted to.
     */
    data class Countdown(val from: LocalDateTime, val to: LocalDateTime, val onShift: Boolean, val name: String)

    /** The stretches from [now] over the next [max] shifts of [days] (days off and leave are passed over). */
    fun countdowns(days: List<WatchDay>, now: LocalDateTime, max: Int = 14): List<Countdown> {
        val shifts = days.filter { it.start != null && it.end != null && !it.off }.map { d ->
            val s = d.date.atTime(d.start!!)
            val e = if (d.end!!.isAfter(d.start)) d.date.atTime(d.end) else d.date.plusDays(1).atTime(d.end)
            Triple(s, e, d.name)
        }.sortedBy { it.first }.filter { it.second.isAfter(now) }.take(max)
        var cursor = now
        val out = mutableListOf<Countdown>()
        for ((s, e, name) in shifts) {
            if (s.isAfter(cursor)) out += Countdown(cursor, s, onShift = false, name = name)
            val from = if (s.isAfter(cursor)) s else cursor
            if (e.isAfter(from)) out += Countdown(from, e, onShift = true, name = name)
            cursor = maxOf(cursor, e)
        }
        return out
    }

    /** "주간까지 5시간 20분", "근무 끝까지 2시간"; null with no shift ahead. */
    fun countdownLine(c: Countdown?, now: LocalDateTime): String? {
        c ?: return null
        val m = java.time.Duration.between(now, c.to).toMinutes().coerceAtLeast(0)
        val left = when {
            m >= 48 * 60 -> "${m / (24 * 60)}일"
            m >= 60 -> "${m / 60}시간" + if (m % 60 > 0 && m < 10 * 60) " ${m % 60}분" else ""
            else -> "${m}분"
        }
        return if (c.onShift) "근무 끝까지 $left" else "${c.name}까지 $left"
    }
    const val PATH = "/bamnat/days"
    const val KEY = "payload"
    private const val OFF_COLOR = 0xFF8E9AAFL
    private const val VERSION = "v1"

    /** [SHFT-08] The days from [from] to [until] off the calendar [resolve]. */
    fun of(from: LocalDate, until: LocalDate, resolve: (LocalDate) -> ResolvedDay): List<WatchDay> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }.map { day(resolve(it)) }.toList()

    /**
     * What the watch window covers around [today]: the watch's month view pages last, this and next month, so
     * from last month's 1st to the end of next month, and at least [DAYS] ahead.
     */
    fun window(today: LocalDate): Pair<LocalDate, LocalDate> {
        val nextMonthEnd = today.plusMonths(1).let { it.withDayOfMonth(it.lengthOfMonth()) }
        return today.minusMonths(1).withDayOfMonth(1) to maxOf(today.plusDays(DAYS.toLong()), nextMonthEnd)
    }

    fun day(d: ResolvedDay): WatchDay {
        val type = d.shiftType
        val leave = d.leave
        return when {
            leave != null && leave.isFullDay -> WatchDay(d.date, leave.label, leave.label.take(1), OFF_COLOR, off = true)
            d.isWorkDay -> WatchDay(d.date, (type?.name ?: "근무") + (leave?.let { " · ${it.label}" } ?: ""), type?.label ?: "근",
                type?.colorArgb ?: 0xFF2F80EDL, d.start, d.end, icon = type?.icon)
            d.extras.isNotEmpty() -> d.extras.first().let { e ->
                // [DAYW-10] Two pieces on a day off (특근 and a 야간 대근) both show: "특근 외 1건" (I 리뷰 1-5).
                val first = e.covered?.name?.let { "$it ${e.kind.name}" } ?: e.kind.name
                val more = d.extras.size - 1
                WatchDay(d.date, if (more > 0) "$first 외 ${more}건" else first, e.kind.mark, e.kind.colorArgb, e.start, e.end)
            }
            type != null -> WatchDay(d.date, type.name, type.label, type.colorArgb, off = true, icon = type.icon)
            else -> WatchDay(d.date, "쉬는 날", "휴", OFF_COLOR, off = true)
        }
    }

    /**
     * Plain text, so the watch reads it without any library: a header line (version, sent at, next alarm,
     * its label, calendar) and one line per day, fields apart by tabs.
     */
    fun encode(p: WatchPayload): String {
        // [WEAR-01][WEAR-03] Fields 6 on are new in 1.23; an older watch reads the first six and ignores them.
        val head = listOf(VERSION, p.syncedAt.toString(), p.nextAlarm?.toString().orEmpty(), clean(p.alarmLabel), clean(p.calendar), p.sentAtMillis.toString(),
            p.weekStart.toString(), p.weekMinutes.toString(), p.weekLimitMinutes.toString(), p.clock.name, if (p.alarmToday) "1" else "0",
            if (p.ringOnWatch) "1" else "0", p.moneyCategories.joinToString(",") { clean(it).replace(",", " ") }, clean(p.sleepLine),
            // 2.0.0 [RVI-08][RVI-09][RVI-10] Fields 14 on; an older watch ignores them.
            if (p.hourly) "1" else "0", clean(p.noun), p.recentAmounts.joinToString(","),
            // 2.0.0 [RVI-94][RVI-95] Fields 17 on.
            if (p.sleepOn) "1" else "0", p.asleepSince?.toString().orEmpty(), WatchTask.encode(p.tasks), clean(p.taskHead))
            .joinToString("\t")
        return (listOf(head) + p.days.map { d ->
            listOf(d.date.toString(), clean(d.name), clean(d.label), d.colorArgb.toString(), d.start?.let(::hm).orEmpty(),
                d.end?.let(::hm).orEmpty(), if (d.off) "1" else "0", d.icon.orEmpty()).joinToString("\t")
        }).joinToString("\n")
    }

    /** What [encode] wrote, or null when it does not read; day lines that do not read are skipped. */
    fun decode(text: String): WatchPayload? {
        val lines = text.lines()
        val head = lines.firstOrNull()?.split('\t') ?: return null
        if (head.size < 5 || head[0] != VERSION) return null
        val synced = runCatching { LocalDateTime.parse(head[1]) }.getOrNull() ?: return null
        val days = lines.drop(1).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 7) return@mapNotNull null
            runCatching {
                WatchDay(LocalDate.parse(f[0]), f[1], f[2], f[3].toLong(), f[4].takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    f[5].takeIf { it.isNotEmpty() }?.let(LocalTime::parse), f[6] == "1", f.getOrNull(7)?.takeIf { it.isNotEmpty() })
            }.getOrNull()
        }
        return WatchPayload(days, synced, head[2].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }, head[3], head[4],
            head.getOrNull(5)?.toLongOrNull() ?: 0,
            weekStart = head.getOrNull(6)?.toIntOrNull()?.takeIf { it in 1..7 } ?: 1,
            weekMinutes = head.getOrNull(7)?.toIntOrNull() ?: -1,
            weekLimitMinutes = head.getOrNull(8)?.toIntOrNull()?.takeIf { it > 0 } ?: 52 * 60,
            clock = head.getOrNull(9)?.let { c -> WatchPayload.Clock.entries.firstOrNull { it.name == c } } ?: WatchPayload.Clock.NONE,
            alarmToday = head.getOrNull(10) == "1",
            ringOnWatch = head.getOrNull(11) == "1",
            moneyCategories = head.getOrNull(12)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(WatchMoney.MAX_CATEGORIES).orEmpty(),
            sleepLine = head.getOrNull(13).orEmpty(),
            hourly = head.getOrNull(14) == "1",
            noun = head.getOrNull(15)?.takeIf { it.isNotBlank() } ?: "근무",
            recentAmounts = head.getOrNull(16)?.split(',')?.mapNotNull { it.trim().toLongOrNull()?.takeIf { a -> a in 1..WatchMoney.MAX_AMOUNT } }
                ?.take(WatchMoney.MAX_AMOUNTS).orEmpty(),
            sleepOn = head.getOrNull(17) == "1",
            asleepSince = head.getOrNull(18)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            tasks = WatchTask.decode(head.getOrNull(19).orEmpty()),
            taskHead = head.getOrNull(20).orEmpty())
    }

    /*
     * 2.0.0 [RVI-08] (검토 ALM-09) What the phone said about "today" holds for the day it was sent on. After midnight the watch
     * works it out itself from what it has (the next alarm, the days), so the tile's 오늘 알람 끄기, an 알바's 출근, this week's hours
     * and the 잘 시간 line are never yesterday's.
     */

    /** The phone's date when it sent [p]. */
    fun sentOn(p: WatchPayload): LocalDate = p.syncedAt.toLocalDate()

    /** Today has an alarm still to ring that 오늘 알람 끄기 can turn off. */
    fun alarmTodayNow(p: WatchPayload, now: LocalDateTime): Boolean {
        val next = p.nextAlarm
        if (next != null && next.toLocalDate() == now.toLocalDate() && next.isAfter(now)) return true
        return p.alarmToday && sentOn(p) == now.toLocalDate() && (next == null || next.isAfter(now))
    }

    /** What 출근·퇴근 the watch offers now: the phone's word on its own day, a new working day's 출근 after it. */
    fun clockNow(p: WatchPayload, now: LocalDateTime): WatchPayload.Clock {
        val today = now.toLocalDate()
        if (sentOn(p) == today) return p.clock
        if (!p.hourly && p.clock == WatchPayload.Clock.NONE) return WatchPayload.Clock.NONE
        val d = on(p.days, today) ?: return WatchPayload.Clock.NONE
        return if (!d.off && d.start != null) WatchPayload.Clock.IN else WatchPayload.Clock.NONE
    }

    /** This week's minutes as sent, only while it is still the week they were counted in (Monday to Sunday); −1 after. */
    fun weekMinutesNow(p: WatchPayload, today: LocalDate): Int {
        if (p.weekMinutes < 0) return -1
        fun monday(d: LocalDate) = d.minusDays((d.dayOfWeek.value - 1).toLong())
        return if (monday(sentOn(p)) == monday(today)) p.weekMinutes else -1
    }

    /** The 잘 시간 line for a while after it was sent (it names times of one night or day), else empty. */
    fun sleepLineNow(p: WatchPayload, now: LocalDateTime): String =
        if (p.sleepLine.isNotBlank() && !now.isBefore(p.syncedAt) && java.time.Duration.between(p.syncedAt, now).toHours() < SLEEP_LINE_HOURS) p.sleepLine else ""

    const val SLEEP_LINE_HOURS = 8L

    /**
     * [SHFT-08] Whether [next] may replace [kept] on the watch: by the epoch milliseconds both were sent at; a copy
     * without them (an older phone app) by its local time, and one with them always over one without.
     */
    fun replaces(next: WatchPayload, kept: WatchPayload?): Boolean = when {
        kept == null -> true
        next.sentAtMillis > 0 && kept.sentAtMillis > 0 -> next.sentAtMillis >= kept.sentAtMillis
        next.sentAtMillis > 0 -> true
        else -> !kept.syncedAt.isAfter(next.syncedAt)
    }

    /** The day for [date] among what the phone sent, or null when it is not there. */
    fun on(days: List<WatchDay>, date: LocalDate): WatchDay? = days.firstOrNull { it.date == date }

    /** The next alarm still ahead of [now], or null. */
    fun alarmAhead(p: WatchPayload, now: LocalDateTime): LocalDateTime? = p.nextAlarm?.takeIf { it.isAfter(now) }

    /** "방금", "15분 전", "3시간 전", "2일 전": how old the watch's copy is. */
    fun age(syncedAt: LocalDateTime, now: LocalDateTime): String {
        val minutes = java.time.Duration.between(syncedAt, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 2 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            minutes < 48 * 60 -> "${minutes / 60}시간 전"
            else -> "${minutes / (24 * 60)}일 전"
        }
    }

    /** Weeks of [month] for the month view from [weekStart] (1 = Monday), null for the days of other months. */
    fun monthGrid(year: Int, month: Int, weekStart: Int = 1): List<List<LocalDate?>> {
        val first = LocalDate.of(year, month, 1)
        val lead = Math.floorMod(first.dayOfWeek.value - weekStart, 7)
        val cells = List(lead) { null } + (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
        return cells.chunked(7).map { w -> w + List(7 - w.size) { null } }
    }

    /** [WEAR-01] Day-of-week names from [weekStart], for the month view's head row. */
    fun weekHead(weekStart: Int): List<String> = (0 until 7).map { WEEKDAYS[Math.floorMod(weekStart - 1 + it, 7)] }

    /**
     * [WEAR-01] Months the watch can page to: as far as the days the phone sent reach (I 리뷰 I24), at least this
     * month. Offsets from this month, e.g. −1..2.
     */
    fun monthRange(days: List<WatchDay>, today: LocalDate): IntRange {
        val first = days.minOfOrNull { it.date } ?: return 0..0
        val last = days.maxOf { it.date }
        fun off(d: LocalDate) = ((d.year - today.year) * 12 + d.monthValue - today.monthValue)
        return minOf(0, off(first))..maxOf(0, off(last))
    }

    /** [WEAR-03] "이번 주 38시간 / 52시간": the week's share of the limit for the ranged complication. */
    fun weekText(p: WatchPayload, today: LocalDate? = null): String? {
        // 2.0.0 [RVI-08] Last week's hours are not shown as this week's.
        val m = if (today == null) p.weekMinutes else weekMinutesNow(p, today)
        return if (m < 0) null else "이번 주 ${m / 60}시간" + (if (m % 60 != 0) " ${m % 60}분" else "") + " / ${p.weekLimitMinutes / 60}시간"
    }

    /** 2.0.0 [RVI-11] (검토 ALM-10) Turning 오늘 알람 끄기 back from the tile (워치에서 되돌리기). */
    const val ALARM_ON_PATH = "/bamnat/sync/alarm-on"

    /** [WEAR-03] Messages the watch sends the phone: turn today's alarm off, and an 알바's 출근·퇴근. */
    const val ALARM_OFF_PATH = "/bamnat/sync/alarm-off"
    const val CLOCK_PATH = "/bamnat/sync/clock"
    /**
     * [WEAR-06] 1.31 N2 워치 진동 알람: the phone tells the watch an alarm rings ([RING_PATH], with what it is for) and that it
     * stopped ([RING_STOP_PATH]); the watch's 끄기 and 다시 울림 go back as [RING_OFF_PATH] and [RING_SNOOZE_PATH]. Phone and
     * watch only (Data Layer messages), never a server.
     */
    const val RING_PATH = "/bamnat/ring"
    const val RING_STOP_PATH = "/bamnat/ring/stop"
    const val RING_OFF_PATH = "/bamnat/sync/ring-off"
    const val RING_SNOOZE_PATH = "/bamnat/sync/ring-snooze"
    /** 1.38 [LEDH-14] 워치로 돈 적기: one line of spending from the watch ([WatchMoney.encode]). */
    const val MONEY_PATH = "/bamnat/sync/money"
    /** 2.0.0 [RVI-94] 잠들었어요·깼어요 from the wrist ([WatchSleep.encode]). */
    const val SLEEP_PATH = "/bamnat/sync/sleep"
    /** 2.0.0 [RVI-95] A to-do finished (or back to do) on the wrist ([WatchTask.doneMessage]). */
    const val TASK_PATH = "/bamnat/sync/task"

    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

/**
 * 1.38 [LEDH-14] 워치로 돈 적기 (조사 D1): the watch picks an amount (dial or ±) and a category and sends one line to the phone, which
 * writes it like 돈 적기 (지출, 오늘, 지금 시각). Watch and phone only (Data Layer message), never a server. [id] lets the phone write
 * a line the watch sent twice only once.
 */
object WatchMoney {
    const val MAX_CATEGORIES = 5
    const val MAX_AMOUNT = 10_000_000L
    /** 2.0.0 [RVI-10] How many recent amounts the phone sends for the watch's chips. */
    const val MAX_AMOUNTS = 3
    /** 2.0.0 [RVI-10] Chips when the phone sent none: 5천·1만·2만. */
    val CHIPS = listOf(5_000L, 10_000L, 20_000L)

    /** The amounts written most often among [amounts] (lately spent), the most frequent first, at most [MAX_AMOUNTS]. */
    fun recentAmounts(amounts: List<Long>): List<Long> =
        amounts.filter { it in 1..MAX_AMOUNT }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key }).map { it.key }.take(MAX_AMOUNTS)

    /** "5천", "1만", "1만 2천", "37,500": a chip's short word. */
    fun short(amount: Long): String = when {
        amount % 1_000L != 0L -> String.format(java.util.Locale.KOREA, "%,d", amount)
        amount >= 10_000L && amount % 10_000L == 0L -> "${amount / 10_000}만"
        amount >= 10_000L -> "${amount / 10_000}만 ${(amount % 10_000) / 1_000}천"
        else -> "${amount / 1_000}천"
    }
    /** The watch's ± steps. */
    val STEPS = listOf(1_000L, 10_000L)
    /** When the phone sent none: the most common everyday ones. */
    val FALLBACK = listOf("식비", "카페", "편의점", "교통", "기타")

    data class Line(val id: String, val amount: Long, val category: String, val atMillis: Long)

    fun encode(l: Line): String = listOf(l.id.filter { it.isLetterOrDigit() || it == '-' }.take(40), l.amount.toString(),
        l.category.replace('\t', ' ').replace('\n', ' ').take(20), l.atMillis.toString()).joinToString("\t")

    /** What [encode] wrote, or null when it does not read or the amount is out of range. */
    fun decode(text: String): Line? {
        val f = text.split('\t')
        if (f.size < 4) return null
        val amount = f[1].toLongOrNull()?.takeIf { it in 1..MAX_AMOUNT } ?: return null
        val at = f[3].toLongOrNull() ?: return null
        val id = f[0].takeIf { it.isNotBlank() } ?: return null
        return Line(id, amount, f[2].trim().ifBlank { "기타" }, at)
    }
}

/**
 * 2.0.0 [RVI-94] (검토 I03b-04, 대기열 G5-M3) 워치에서 잠들었어요·깼어요: the wrist writes the same sleep log as the phone's button, without
 * picking the phone up in the dark. [atMillis] is when it was pressed on the wrist (a message a little late still says the right
 * time); [id] lets the phone write a press it got twice only once.
 */
object WatchSleep {
    data class Press(val asleep: Boolean, val atMillis: Long, val id: String)

    fun encode(p: Press): String = listOf(if (p.asleep) "asleep" else "awake", p.atMillis.toString(), p.id.filter { it.isLetterOrDigit() || it == '-' }.take(40))
        .joinToString("\t")

    fun decode(text: String): Press? {
        val f = text.split('\t')
        if (f.size < 3) return null
        val asleep = when (f[0]) { "asleep" -> true; "awake" -> false; else -> return null }
        val at = f[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Press(asleep, at, f[2].takeIf { it.isNotBlank() } ?: return null)
    }

    /**
     * What the 잠 tile says: asleep since when (the wrist's own press until the phone answers), and the one button. [pressed] is the
     * wrist's last press not yet in a copy from the phone ([pressedAt] when).
     */
    fun asleepNow(p: WatchPayload, pressed: Boolean?, pressedAt: LocalDateTime?): LocalDateTime? = when (pressed) {
        true -> pressedAt
        false -> null
        null -> p.asleepSince
    }
}

/**
 * 2.0.0 [RVI-95] (검토 I03b-04·I07ab-04, 대기열 TASK-35) 워치 '오늘 할 일' 타일: the rows the phone's 할 일 위젯 shows ([com.bamnat.core.memo.TaskList]),
 * worked out on the phone and sent with the days; a tap on the wrist finishes one on its day, as the widget's circle does.
 */
data class WatchTask(val id: Long, val text: String, val day: LocalDate?, val done: Boolean, val late: Boolean) {
    companion object {
        /** Rows sent: the tile shows the first few open ones and how many more. */
        const val MAX = 8
        private const val ROW = '\u001E'
        private const val FIELD = '\u001F'

        fun encode(tasks: List<WatchTask>): String = tasks.take(MAX).joinToString(ROW.toString()) { t ->
            listOf(t.id.toString(), t.day?.toString().orEmpty(), if (t.done) "1" else "0", if (t.late) "1" else "0",
                t.text.replace('\t', ' ').replace('\n', ' ').replace(ROW, ' ').replace(FIELD, ' ')).joinToString(FIELD.toString())
        }

        fun decode(text: String): List<WatchTask> = if (text.isEmpty()) emptyList() else text.split(ROW).mapNotNull { r ->
            val f = r.split(FIELD)
            if (f.size < 5) return@mapNotNull null
            val id = f[0].toLongOrNull() ?: return@mapNotNull null
            WatchTask(id, f[4], f[1].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, f[2] == "1", f[3] == "1")
        }.take(MAX)

        /** The phone's rows for the watch: [rows] as the widget orders them (late, today, pinned, then finished). */
        fun of(rows: List<com.bamnat.core.memo.TaskList.Row>, now: LocalDateTime): List<WatchTask> = rows.take(MAX).map { r ->
            WatchTask(r.memo.id, r.text, r.day, r.done, com.bamnat.core.memo.TaskList.overdue(r, now))
        }

        /** "id\tday\t1": finished (1) or back to do (0) on its day. */
        fun doneMessage(t: WatchTask, done: Boolean): String = listOf(t.id.toString(), t.day?.toString().orEmpty(), if (done) "1" else "0").joinToString("\t")

        fun readDone(text: String): Triple<Long, LocalDate?, Boolean>? {
            val f = text.split('\t')
            if (f.size < 3) return null
            val id = f[0].toLongOrNull() ?: return null
            val day = f[1].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return Triple(id, day, f[2] == "1")
        }

        /**
         * What the tile shows: the open rows first ([shown] of them) with [pressed] (ids finished on the wrist, not yet in a copy
         * from the phone) taken out, and how many more are open.
         */
        fun tile(tasks: List<WatchTask>, pressed: Set<Long>, shown: Int = 3): Pair<List<WatchTask>, Int> {
            val open = tasks.filter { !it.done && it.id !in pressed }
            return open.take(shown) to (open.size - shown).coerceAtLeast(0)
        }
    }
}
