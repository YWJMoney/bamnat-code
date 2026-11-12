package com.bamnat.core.sleep

import com.bamnat.core.schedule.ScheduleResolver
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * [SLP-16] What the sleep log says about the user's own sleep around their work: how long they
 * sleep after a night shift, before an early start and on a day off, against their target; whether
 * the last week was shorter than the one before; how much bedtimes move. Each finding is one plain
 * line with a small, safe thing to try. Reference only, like all of sleep [SLP-13]: no diagnosis,
 * no score, nothing promised.
 */
object SleepInsights {
    /** The work a sleep sits next to. */
    enum class Context(val label: String) {
        AFTER_NIGHT("야간 뒤"),
        BEFORE_EARLY("아침 출근 전"),
        WORK_DAY("근무일"),
        DAY_OFF("쉬는 날"),
    }

    /** One finished sleep with the work around it. */
    data class Night(val entry: SleepEntry, val context: Context) {
        val minutes: Long get() = entry.minutes ?: 0
        val nap: Boolean get() = minutes < TodayFlow.MAIN_SLEEP_MINUTES
    }

    /**
     * 1.38 [SLEEP-02] The sleeps of one rest between two works, joined: a split sleep after a night (4 hours, up, 2 more)
     * or a nap after the main sleep counts as one rest, not as a short sleep and a nap. Two sleeps are one rest when no
     * work starts between them and they are at most [BLOCK_GAP_HOURS] apart. Its context is that of its longest sleep.
     */
    data class Block(val nights: List<Night>) {
        val minutes: Long get() = nights.sumOf { it.minutes }
        val start: LocalDateTime get() = nights.minOf { it.entry.start }
        val main: Night get() = nights.maxBy { it.minutes }
        val context: Context get() = main.context
        val split: Boolean get() = nights.size > 1
        /** A rest too short to be the main sleep of the day: a nap on its own. */
        val nap: Boolean get() = minutes < TodayFlow.MAIN_SLEEP_MINUTES
    }

    data class Group(val context: Context, val count: Int, val averageMinutes: Long, val averageBedtime: Int?) {
        /** Enough sleeps for the average to mean something. */
        val enough: Boolean get() = count >= MIN_FOR_AVERAGE
    }

    enum class Tone { GOOD, NOTE, WARN }

    data class Finding(val key: String, val tone: Tone, val title: String, val text: String)

    data class Report(
        val nights: List<Night>,
        val groups: List<Group>,
        /** Last 7 and the 7 before that. */
        val week: SleepDebt,
        val weekBefore: SleepDebt,
        /** How far bedtimes spread on main sleeps that were not after a night shift, in minutes (one standard deviation). */
        val bedtimeSpread: Long?,
        val naps: Int,
        val findings: List<Finding>,
        /** Days with a log in the window. */
        val loggedDays: Int,
        /** 1.38 [SLEEP-02] The rests, newest first; [splitRests] of them were more than one sleep. */
        val blocks: List<Block> = emptyList(),
    ) {
        val splitRests: Int get() = blocks.count { it.split && !it.nap }
        fun group(c: Context): Group? = groups.firstOrNull { it.context == c }
        val isEmpty: Boolean get() = nights.isEmpty()
    }

    const val WINDOW_DAYS = 28L
    const val MIN_FOR_AVERAGE = 2
    /** Work that ended up to this long before falling asleep counts as the work the sleep came after. */
    const val AFTER_WORK_HOURS = 6L
    /** Work starting up to this long after waking counts as the work the sleep was before. */
    const val BEFORE_WORK_HOURS = 4L
    /** How much shorter than the target is worth saying. */
    const val SHORT_MINUTES = 60L
    /** How much more on days off reads as catching up. */
    const val CATCH_UP_MINUTES = 90L
    /** A spread of bedtimes larger than this is worth saying. */
    const val SPREAD_MINUTES = 75L

    /** The context of each finished sleep, newest first; an open sleep or a missed wake tap is left out. */
    fun nights(log: List<SleepEntry>, resolver: ScheduleResolver, today: LocalDate, days: Long = WINDOW_DAYS): List<Night> =
        nightsAndWork(log, resolver, today, days).first

    private fun nightsAndWork(log: List<SleepEntry>, resolver: ScheduleResolver, today: LocalDate, days: Long): Pair<List<Night>, List<SleepSchedule.Work>> {
        val from = today.minusDays(days - 1)
        val done = log.filter { e ->
            e.end != null && (e.minutes ?: 0) in TodayFlow.MIN_LOGGED_MINUTES..SleepDebt.MAX_SLEEP_MINUTES &&
                e.wakeDate!!.let { !it.isBefore(from) && !it.isAfter(today) }
        }
        if (done.isEmpty()) return emptyList<Night>() to emptyList()
        val first = done.minOf { it.start.toLocalDate() }.minusDays(1)
        val last = done.maxOf { it.end!!.toLocalDate() }.plusDays(1)
        val work = resolver.resolveRange(first, last).flatMap { SleepSchedule.work(it) }
        return done.sortedByDescending { it.start }.map { e -> Night(e, contextOf(e.start, e.end!!, work)) } to work
    }

    /** 1.38 [SLEEP-02] Two sleeps further apart than this are two rests even with no work between them. */
    const val BLOCK_GAP_HOURS = 3L

    /** 1.38 [SLEEP-02] [nights] joined into rests between works, newest first. */
    fun blocks(nights: List<Night>, work: List<SleepSchedule.Work>): List<Block> {
        val out = ArrayList<MutableList<Night>>()
        for (n in nights.sortedBy { it.entry.start }) {
            val cur = out.lastOrNull()
            val end = cur?.maxOf { it.entry.end!! }
            val joins = cur != null && end != null && !n.entry.start.isAfter(end.plusHours(BLOCK_GAP_HOURS)) &&
                work.none { w -> w.from.isAfter(end.minusMinutes(1)) && w.from.isBefore(n.entry.start) }
            if (joins) cur!!.add(n) else out.add(mutableListOf(n))
        }
        return out.map { Block(it.sortedByDescending { n -> n.entry.start }) }.sortedByDescending { it.start }
    }

    internal fun contextOf(start: LocalDateTime, end: LocalDateTime, work: List<SleepSchedule.Work>): Context {
        val before = work.filter { !it.to.isAfter(start.plusHours(1)) && it.to.isAfter(start.minusHours(AFTER_WORK_HOURS)) }
        if (before.any { it.night || it.overnight }) return Context.AFTER_NIGHT
        val after = work.firstOrNull { !it.from.isBefore(end.minusHours(1)) && it.from.isBefore(end.plusHours(BEFORE_WORK_HOURS)) }
        if (after != null && after.early) return Context.BEFORE_EARLY
        val day = end.toLocalDate()
        return if (work.any { it.date == day } || before.isNotEmpty() || after != null) Context.WORK_DAY else Context.DAY_OFF
    }

    /**
     * 1.39 PC c0ca3d9f 유형별 빈틈 (가게에 '야간 뒤·쉬는 날'): the kinds of day the averages split into, in the words of the work.
     * 야간 뒤 is said to 교대 only (사용자 2026-11-06), as the sleep screen's 이렇게 쓰여요 does.
     */
    fun groupsWord(style: com.bamnat.core.work.WorkStyle?): String = when (style) {
        null, com.bamnat.core.work.WorkStyle.SHIFT -> "야간 뒤·쉬는 날"
        com.bamnat.core.work.WorkStyle.INVEST -> "평일·쉬는 날"
        else -> "일한 날·쉬는 날"
    }

    fun report(log: List<SleepEntry>, resolver: ScheduleResolver, today: LocalDate, targetMinutes: Int,
               style: com.bamnat.core.work.WorkStyle? = null): Report {
        val (nights, work) = nightsAndWork(log, resolver, today, WINDOW_DAYS)
        val blocks = blocks(nights, work)
        // [SLEEP-02] Averages are per rest: a split sleep counts whole, a nap on its own is left out.
        val mainRests = blocks.filterNot { it.nap }
        val main = mainRests.map { it.main }
        val groups = Context.entries.mapNotNull { c ->
            val list = mainRests.filter { it.context == c }
            if (list.isEmpty()) null
            else Group(c, list.size, list.sumOf { it.minutes } / list.size, averageClock(list.map { it.start }))
        }
        val week = SleepDebt.of(log, today, targetMinutes)
        val weekBefore = SleepDebt.of(log, today.minusDays(SleepDebt.WINDOW_DAYS), targetMinutes)
        val spread = spread(main.filter { it.context != Context.AFTER_NIGHT }.map { it.entry.start })
        val loggedDays = nights.mapNotNull { it.entry.wakeDate }.toSet().size
        val report = Report(nights, groups, week, weekBefore, spread, blocks.count { it.nap }, emptyList(), loggedDays, blocks)
        return report.copy(findings = findings(report, targetMinutes, style))
    }

    private fun findings(r: Report, target: Int, style: com.bamnat.core.work.WorkStyle? = null): List<Finding> = buildList {
        if (r.isEmpty) return@buildList
        val night = r.group(Context.AFTER_NIGHT)
        if (night != null && night.enough && target - night.averageMinutes >= SHORT_MINUTES) {
            add(Finding("after-night", Tone.WARN, "야간 뒤 잠이 짧아요",
                "야간 뒤에는 평균 ${hours(night.averageMinutes)} 잤어요. 목표보다 ${hours(target - night.averageMinutes)} 적어요. " +
                    "방을 어둡고 조용하게 해 두거나, 다음 야간 전에 낮잠을 넣어 나눠 자는 방법도 있어요."))
        }
        val early = r.group(Context.BEFORE_EARLY)
        if (early != null && early.enough && target - early.averageMinutes >= SHORT_MINUTES) {
            add(Finding("before-early", Tone.WARN, "아침 출근 전 잠이 짧아요",
                "아침 출근 전에는 평균 ${hours(early.averageMinutes)} 잤어요. 전날 잠드는 시각을 30분씩 당겨 보세요."))
        }
        val off = r.group(Context.DAY_OFF)
        val work = listOfNotNull(r.group(Context.WORK_DAY), early, night).filter { it.enough }
        if (off != null && off.enough && work.isNotEmpty()) {
            val workAvg = work.sumOf { it.averageMinutes * it.count } / work.sumOf { it.count }
            if (off.averageMinutes - workAvg >= CATCH_UP_MINUTES) {
                add(Finding("catch-up", Tone.NOTE, "쉬는 날 몰아서 자요",
                    "쉬는 날은 근무일보다 평균 ${hours(off.averageMinutes - workAvg)} 더 잤어요. 근무일에 모자란 잠을 채우는 모습이에요. " +
                        "근무 사이 낮잠으로 조금씩 나눠 채워 보는 것도 방법이에요."))
            }
        }
        r.bedtimeSpread?.takeIf { it >= SPREAD_MINUTES }?.let { s ->
            add(Finding("spread", Tone.NOTE, "잠드는 시각이 들쭉날쭉해요",
                "야간 뒤를 빼고도 잠드는 시각이 보통 ±${hours(s)} 움직여요. 교대 근무라면 당연한 일이에요. 같은 근무 뒤에는 같은 시각에 눕는 것부터 맞춰 보세요."))
        }
        if (r.week.days >= 3 && r.weekBefore.days >= 3) {
            val diff = r.week.averageMinutes - r.weekBefore.averageMinutes
            if (abs(diff) >= 30) {
                add(if (diff > 0) Finding("week-up", Tone.GOOD, "지난주보다 더 잤어요", "최근 7일은 그 전 7일보다 하루 평균 ${hours(diff)} 더 잤어요.")
                else Finding("week-down", Tone.WARN, "지난주보다 덜 잤어요", "최근 7일은 그 전 7일보다 하루 평균 ${hours(-diff)} 덜 잤어요. 다음 쉬는 날은 일정을 가볍게 두세요."))
            }
        }
        if (none { it.tone == Tone.WARN } && r.week.days >= 3 && r.week.shortMinutes == 0L) {
            add(Finding("on-target", Tone.GOOD, "목표만큼 자고 있어요", "최근 7일 기록한 날은 모두 목표 ${hours(target.toLong())}을 채웠어요."))
        }
        if (r.loggedDays < 5) {
            add(Finding("more-logs", Tone.NOTE, "기록이 쌓이면 더 알려 드려요",
                "근무별로 2번 넘게 기록하면 ${groupsWord(style)} 평균을 따로 보여 줘요. 지금 ${r.loggedDays}일 기록했어요."))
        }
    }

    /**
     * [SLP-16] What the user's own sleep after a night shift was, for 오늘의 흐름 on the way home:
     * the average of the last [WINDOW_DAYS] days, or null with fewer than two.
     */
    fun afterNightAverage(log: List<SleepEntry>, resolver: ScheduleResolver, today: LocalDate): Long? {
        val (nights, work) = nightsAndWork(log, resolver, today, WINDOW_DAYS)
        return blocks(nights, work).filter { !it.nap && it.context == Context.AFTER_NIGHT }
            .takeIf { it.size >= MIN_FOR_AVERAGE }?.let { list -> list.sumOf { it.minutes } / list.size }
    }

    /**
     * 1.38 [SLEEP-02] How much the user slept in the [hours] before [workStart], every sleep counted (naps and split
     * sleep too): "출근 전 24시간 동안 6시간 40분".
     */
    fun sleptBefore(log: List<SleepEntry>, workStart: LocalDateTime, hours: Long = 24): Long {
        val from = workStart.minusHours(hours)
        return log.filter { it.end != null && (it.minutes ?: 0) <= SleepDebt.MAX_SLEEP_MINUTES }.sumOf { e ->
            val a = maxOf(e.start, from); val b = minOf(e.end!!, workStart)
            if (b.isAfter(a)) java.time.Duration.between(a, b).toMinutes() else 0L
        }
    }

    /** Mean clock time, minutes after midnight, on a circle so 23:30 and 00:30 average to 00:00. */
    internal fun averageClock(times: List<LocalDateTime>): Int? {
        if (times.isEmpty()) return null
        val angles = times.map { (it.hour * 60 + it.minute) * 2 * Math.PI / DAY }
        val x = angles.sumOf { kotlin.math.cos(it) }
        val y = angles.sumOf { kotlin.math.sin(it) }
        if (abs(x) < 1e-9 && abs(y) < 1e-9) return null
        val a = kotlin.math.atan2(y, x).let { if (it < 0) it + 2 * Math.PI else it }
        return ((a * DAY / (2 * Math.PI)).roundToLong() % DAY).toInt()
    }

    /** One standard deviation of clock times around their mean, null with fewer than three. */
    internal fun spread(times: List<LocalDateTime>): Long? {
        if (times.size < 3) return null
        val mean = averageClock(times) ?: return null
        val diffs = times.map { t ->
            val m = t.hour * 60 + t.minute
            var d = (m - mean) % DAY
            if (d > DAY / 2) d -= DAY
            if (d < -DAY / 2) d += DAY
            d.toDouble()
        }
        return sqrt(diffs.sumOf { it * it } / diffs.size).roundToLong()
    }

    fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /**
     * 2.0.0 [RVI-71] (검토 U03-05) What '깼어요' says, the same on the calendar, the sleep page and the night screen: a sleep under
     * an hour said '0시간 잤어요' or nothing, and kept no 아침 한 장 without a word why.
     */
    fun wokeLine(minutes: Long): String = when {
        minutes > SleepDebt.MAX_SLEEP_MINUTES -> "16시간이 넘게 열려 있던 기록이라 수면 통계에서 뺐어요"
        minutes < SleepMorning.MIN_MINUTES -> "${minutes.coerceAtLeast(1)}분 잤어요 · 짧아서 아침 한 장 없이 기록에만 남겨요"
        else -> "${hours(minutes)} 잤어요"
    }

    fun hours(m: Long): String = when {
        m < 60 -> "${m}분"
        m % 60 == 0L -> "${m / 60}시간"
        else -> "${m / 60}시간 ${m % 60}분"
    }

    private const val DAY = 24 * 60
}

/**
 * 1.39 사용자 2026-11-08 ("잠 앱을 배워 만들었는데 사용자 패턴 카드가 하나도 없어요"): the pattern cards wait for enough records, so
 * a new log showed none and said nothing of them. What is still to come, each with how many more it takes, so the screen tells
 * what the taps will open: 근무별 평균 ([SleepInsights.MIN_FOR_AVERAGE] of a kind of day), 내 기록에서 찾은 것 ([SleepCauses.MIN_TIMES]
 * mornings answered) and the week against the one before ([WEEK_DAYS] days logged). One that is open already is left out.
 */
object SleepUnlocks {
    const val WEEK_DAYS = 7

    data class Unlock(val key: String, val title: String, val line: String, val have: Int, val need: Int)

    fun of(report: SleepInsights.Report, answeredMornings: Int, style: com.bamnat.core.work.WorkStyle?): List<Unlock> = buildList {
        if (report.groups.none { it.enough }) {
            val have = report.groups.maxOfOrNull { it.count } ?: 0
            val left = SleepInsights.MIN_FOR_AVERAGE - have
            add(Unlock("groups", "근무별 평균", "같은 종류 날 잠을 ${left}번 더 적으면 ${SleepInsights.groupsWord(style)} 평균을 따로 보여 줘요",
                have, SleepInsights.MIN_FOR_AVERAGE))
        }
        if (answeredMornings < SleepCauses.MIN_TIMES) {
            val left = SleepCauses.MIN_TIMES - answeredMornings
            add(Unlock("causes", "내 기록에서 찾은 것", "깬 뒤 아침 한 장에 ${left}번 더 답하면 피곤한 아침과 함께 온 것(늦은 카페인·야식…)을 찾아 줘요",
                answeredMornings, SleepCauses.MIN_TIMES))
        }
        if (report.loggedDays < WEEK_DAYS) {
            val left = WEEK_DAYS - report.loggedDays
            add(Unlock("week", "한 주 흐름", "${left}일 더 적으면 최근 7일 평균과 목표를 견주고, 잠드는 시각이 들쭉날쭉한지 알려 줘요",
                report.loggedDays, WEEK_DAYS))
        }
    }
}
