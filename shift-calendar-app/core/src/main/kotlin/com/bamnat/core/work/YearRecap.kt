package com.bamnat.core.work

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.model.NightShift
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.TodayFlow
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 1.40 [SPREAD-37] 연말 근무 결산 (마케팅 `앱안-퍼짐-설계.md` 2장, 12-20~1-10): the year in the user's own kind of work, as a few
 * tiles for a picture to keep or send. 야간 횟수·공휴일 근무·평균 잠·가장 길게 쉰 날 for shifts, 일감 for field work, 문 연 날 for
 * a shop. Only lines with something in them are shown, so a 일근 sees no 야간 and a shop no 연차. The money tile is
 * [Tile.money]: hidden until the user shows it, and on the picture only then. Worked out on the phone, nothing is sent.
 *
 * The days count from [from], the later of 1월 1일 and the day this calendar started (the pattern before it was never the
 * user's record, [R136-03]), to [to]: today in 12월 (the picture says '12월 22일까지'), 12월 31일 in 1월.
 */
data class YearRecap(
    val year: Int,
    val from: LocalDate,
    val to: LocalDate,
    val focus: CalendarFocus,
    val workDays: Int = 0,
    val workMinutes: Int = 0,
    val nights: Int = 0,
    val nightMinutes: Int = 0,
    val holidayWorkDays: Int = 0,
    /** Extra work by its kind's name, most first: 특근 12, 대근 3. */
    val extras: List<Pair<String, Int>> = emptyList(),
    val leaveDays: Double = 0.0,
    /** The longest run of days off in a row (연차 included); only for work that runs on a pattern. */
    val longestRest: Rest? = null,
    /** The month with the most days of work (일감 days, 문 연 날), with that count. */
    val busiestMonth: Pair<YearMonth, Int>? = null,
    val jobs: Int = 0,
    val jobDays: Int = 0,
    val jobHolidayDays: Int = 0,
    val openDays: Int = 0,
    val holidayOpenDays: Int = 0,
    /** The shop's best day by sales: only its date shows, the amount is money. */
    val bestDay: LocalDate? = null,
    val sleepDays: Int = 0,
    val sleepAverageMinutes: Long = 0,
    /** The year's money in this kind of work's words ([moneyLabel]); null when there is none to say. */
    val money: Long? = null,
    val moneyLabel: String = "",
) {
    data class Rest(val from: LocalDate, val to: LocalDate) {
        val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    }

    /** One tile: "야간" / "87번" / "밤에 일한 시간 약 700시간". A [money] tile is shown as [HIDDEN] until the user opens it. */
    data class Tile(val label: String, val value: String, val note: String = "", val money: Boolean = false)

    /** Started after 1월 1일: the picture says from when. */
    val partial: Boolean get() = from != LocalDate.of(year, 1, 1)
    /** Counted to a day before 12월 31일 (a 결산 seen in December). */
    val unfinished: Boolean get() = to != LocalDate.of(year, 12, 31)

    /** "1월 1일~12월 31일", "3월 4일부터 12월 22일까지". */
    val period: String get() = when {
        !partial && !unfinished -> "1월 1일~12월 31일"
        else -> "${md(from)}부터 ${md(to)}까지"
    }

    val title: String get() = "${year}년 내 근무 결산"

    /** Nothing to say: no work, no 일감, no 문 연 날. */
    val isEmpty: Boolean get() = when (focus) {
        CalendarFocus.SHIFTS -> workDays == 0
        CalendarFocus.JOBS -> jobs == 0 && jobDays == 0
        CalendarFocus.SALES -> openDays == 0
        CalendarFocus.INVEST -> true
    }

    /** Worth a card on the calendar: something to say over at least [MIN_DAYS] days of record. */
    val worthShowing: Boolean get() = !isEmpty && ChronoUnit.DAYS.between(from, to) + 1 >= MIN_DAYS

    /** The big number at the top of the picture. */
    val hero: Tile get() = when (focus) {
        CalendarFocus.SHIFTS -> if (nights > 0) Tile("야간", "${nights}번", "밤에 일한 시간 약 ${hours(nightMinutes)}")
            else Tile("일한 날", "${workDays}일", "모두 ${hours(workMinutes)}")
        CalendarFocus.JOBS -> Tile("해낸 일감", "${jobs}건", if (jobDays > 0) "일한 날 ${jobDays}일" else "")
        CalendarFocus.SALES -> Tile("문 연 날", "${openDays}일", "")
        CalendarFocus.INVEST -> Tile("", "")
    }

    /** The tiles under the [hero], each only when it has something; the money tile last. */
    val tiles: List<Tile> get() = buildList {
        when (focus) {
            CalendarFocus.SHIFTS -> {
                if (nights > 0) add(Tile("일한 날", "${workDays}일", "모두 ${hours(workMinutes)}"))
                if (holidayWorkDays > 0) add(Tile("공휴일 근무", "${holidayWorkDays}일", "남들 쉴 때 일했어요"))
                // All extra work in one tile, its kinds in the note: 특근 12 · 대근 3.
                if (extras.isNotEmpty()) add(Tile(if (extras.size == 1) extras[0].first else "추가 근무", "${extras.sumOf { it.second }}번",
                    if (extras.size == 1) "" else extras.take(3).joinToString(" · ") { (name, n) -> "$name $n" }))
                if (leaveDays > 0) add(Tile("쓴 연차·휴가", "${days(leaveDays)}일"))
                longestRest?.takeIf { it.days >= 2 }?.let { add(Tile("가장 길게 쉰 날", "${it.days}일 연속", "${md(it.from)}~${md(it.to)}")) }
                busiestMonth?.let { (m, n) -> add(Tile("가장 바빴던 달", "${m.monthValue}월", "${n}일 일했어요")) }
            }
            CalendarFocus.JOBS -> {
                busiestMonth?.let { (m, n) -> add(Tile("가장 바빴던 달", "${m.monthValue}월", "일한 날 ${n}일")) }
                if (jobHolidayDays > 0) add(Tile("공휴일에 한 일", "${jobHolidayDays}일"))
            }
            CalendarFocus.SALES -> {
                if (holidayOpenDays > 0) add(Tile("공휴일에도 문 연 날", "${holidayOpenDays}일"))
                bestDay?.let { add(Tile("가장 잘된 날", md(it))) }
                busiestMonth?.let { (m, _) -> add(Tile("가장 잘된 달", "${m.monthValue}월")) }
            }
            CalendarFocus.INVEST -> Unit
        }
        if (sleepDays >= MIN_SLEEP_DAYS) add(Tile("평균 잠", HoursFormat.korean(sleepAverageMinutes.toInt()), "잠 기록 ${sleepDays}일"))
        money?.takeIf { it != 0L && moneyLabel.isNotBlank() }?.let { add(Tile(moneyLabel, manWon(it), money = true)) }
    }

    /** The card's one line on the calendar, never the money: "야간 87번 · 공휴일 근무 9일 · 가장 길게 쉰 날 5일". */
    val line: String get() = (listOf(hero) + tiles).filterNot { it.money }.take(3).joinToString(" · ") { "${it.label} ${it.value}" }

    companion object {
        /** The 결산 shows from 12월 20일 to 1월 10일 (마케팅 결정). */
        val OPENS: MonthDay = MonthDay.of(12, 20)
        val CLOSES: MonthDay = MonthDay.of(1, 10)
        /** Fewer days of record than this make no year to sum up: no card (the user can still open it from the picture sheet). */
        const val MIN_DAYS = 28L
        /** An average of fewer logged nights says little. */
        const val MIN_SLEEP_DAYS = 7
        const val HIDDEN = "●●●만 원"

        /** The year the window is about on [today], or null outside 12-20~1-10. */
        fun yearOf(today: LocalDate): Int? = when {
            !MonthDay.from(today).isBefore(OPENS) -> today.year
            !MonthDay.from(today).isAfter(CLOSES) -> today.year - 1
            else -> null
        }

        /**
         * The card's key on [today]: yyyy1 in December (the year still running), yyyy2 in January (the whole year), so a card
         * closed in December comes once more with the year finished. 0 outside the window.
         */
        fun key(today: LocalDate): Int {
            val y = yearOf(today) ?: return 0
            return y * 10 + if (today.year == y) 1 else 2
        }

        /** The year whose card is due on [today], given the last key closed ([seen]); null when none. */
        fun due(today: LocalDate, seen: Int): Int? = key(today).takeIf { it > seen }?.let { it / 10 }

        /**
         * The days counted for [year] on [today]: from the later of 1월 1일 and [startDay] (or the first day written by hand,
         * if earlier), to today in December or 12월 31일 after it.
         */
        fun range(year: Int, today: LocalDate, startDay: LocalDate?, firstWritten: LocalDate? = null): Pair<LocalDate, LocalDate> {
            val first = LocalDate.of(year, 1, 1)
            val last = minOf(LocalDate.of(year, 12, 31), today)
            val begin = listOfNotNull(startDay, firstWritten).minOrNull()?.coerceAtLeast(first) ?: first
            return minOf(begin, last) to last
        }

        /**
         * [days] are the calendar's days from [from] to [to] (resolved); [restCounts] when the days off are the user's own
         * (a pattern runs the calendar), not just days nobody wrote down. [jobs], [sales] and [sleep] are the whole records;
         * only those in the range count. [money] is worked out by the app (the pay engine lives there).
         */
        fun of(
            year: Int,
            from: LocalDate,
            to: LocalDate,
            focus: CalendarFocus,
            days: List<ResolvedDay>,
            restCounts: Boolean,
            jobs: List<Job> = emptyList(),
            sales: List<DailySales> = emptyList(),
            sleep: List<SleepEntry> = emptyList(),
            money: Long? = null,
            moneyLabel: String = "",
            today: LocalDate = to,
        ): YearRecap {
            fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
            val inside = days.filter { inRange(it.date) }
            val worked = inside.filter { it.totalMinutes > 0 }
            val nightDays = worked.filter(MonthRecap::nightShift)
            val nightMinutes = nightDays.sumOf { d ->
                val own = if (d.isWorkDay && d.leave?.isFullDay != true && d.start != null && d.end != null) NightShift.minutes(d.start, d.end) else 0
                own + d.extras.sumOf { e -> val s = e.start; val t = e.end; if (s != null && t != null) NightShift.minutes(s, t) else 0 }
            }
            val rest = if (!restCounts) null else longestRun(inside.filter { it.totalMinutes == 0 }.map { it.date })
            val workBusiest = worked.groupingBy { YearMonth.from(it.date) }.eachCount().maxByOrNull { it.value }?.toPair()

            val done = jobs.filter { !it.cancelled && inRange(it.date) && it.isDone(today) }
            val jobDates = done.flatMap { j ->
                generateSequence(j.date) { it.plusDays(1) }.takeWhile { !it.isAfter(j.lastDate) && !it.isAfter(to) }.toList()
            }.toSet()
            val jobBusiest = jobDates.groupingBy { YearMonth.from(it) }.eachCount().maxByOrNull { it.value }?.toPair()

            val shop = sales.filter { inRange(it.date) && (it.sales != 0L || it.costs != 0L) }
            val best = shop.maxByOrNull { it.sales }?.takeIf { it.sales > 0 }
            val bestMonth = shop.groupBy { YearMonth.from(it.date) }.mapValues { (_, l) -> l.sumOf { it.sales } }
                .maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { it.key to 0 }

            val slept = sleep
                .filter { e -> e.wakeDate?.let { it.year == year && !it.isAfter(to) } == true }
                .filter { (it.minutes ?: 0) in TodayFlow.MIN_LOGGED_MINUTES..SleepDebt.MAX_SLEEP_MINUTES }
                .groupBy { it.wakeDate!! }
                .mapValues { (_, l) -> l.sumOf { it.minutes!! } }
                .filterValues { it >= SleepDebt.MIN_DAY_MINUTES }

            return YearRecap(
                year = year, from = from, to = to, focus = focus,
                workDays = worked.size,
                workMinutes = worked.sumOf { it.totalMinutes },
                nights = nightDays.size,
                nightMinutes = nightMinutes,
                holidayWorkDays = worked.count { KoreanHolidays.isHoliday(it.date) },
                extras = inside.flatMap { d -> d.extras.map { it.kind.name } }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key to it.value },
                leaveDays = inside.sumOf { d -> d.leave?.days ?: 0.0 },
                longestRest = rest,
                busiestMonth = when (focus) {
                    CalendarFocus.SHIFTS -> workBusiest
                    CalendarFocus.JOBS -> jobBusiest
                    CalendarFocus.SALES -> bestMonth
                    CalendarFocus.INVEST -> null
                }?.takeIf { YearMonth.from(from) != YearMonth.from(to) },
                jobs = done.size,
                jobDays = jobDates.size,
                jobHolidayDays = jobDates.count { KoreanHolidays.isHoliday(it) },
                openDays = shop.size,
                holidayOpenDays = shop.count { KoreanHolidays.isHoliday(it.date) },
                bestDay = best?.date,
                sleepDays = slept.size,
                sleepAverageMinutes = if (slept.isEmpty()) 0 else slept.values.sum() / slept.size,
                money = money,
                moneyLabel = moneyLabel,
            )
        }

        /** The longest run of consecutive [dates]; null when there are none. */
        fun longestRun(dates: List<LocalDate>): Rest? {
            var best: Rest? = null
            var start: LocalDate? = null
            var prev: LocalDate? = null
            for (d in dates.sorted()) {
                if (prev == null || d != prev.plusDays(1)) start = d
                prev = d
                val run = Rest(start!!, d)
                if (best == null || run.days > best.days) best = run
            }
            return best
        }

        /** "1,234만 원"; under 1만 원, "8,000원". */
        fun manWon(v: Long): String {
            val sign = if (v < 0) "−" else ""
            val a = kotlin.math.abs(v)
            return if (a < 10_000) "$sign%,d원".format(a) else "$sign%,d만 원".format(Math.round(a / 10_000.0))
        }

        private fun md(d: LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일"
        private fun hours(minutes: Int) = "%,d시간".format(Math.round(minutes / 60.0))
        private fun days(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    }
}
