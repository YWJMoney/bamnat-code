package com.bamnat.core.invest

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * [INV-08][INV-14] When the investor's alarms and date notices come. Weekly alarms skip days their
 * market is shut ([MarketDays]: 국내 weekends, public holidays, 근로자의 날, 12월 31일; 미국 NYSE holidays; 코인 never)
 * unless told not to; a monthly plan on a shut day moves to the next open day, as a broker's 자동 적립 does.
 */
object InvestAlarms {
    /** Dates written down are told about at 08:30 that morning. */
    const val EVENT_NOTICE_MINUTE = 8 * 60 + 30
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    /** The Korean market is shut on [d] ([MarketDays.krClosed]). */
    fun marketClosed(d: LocalDate): Boolean = MarketDays.krClosed(d)

    /**
     * 2.0.0 [RVK-52] (검토 MONEY-72·T06b-10): the market an alarm follows. 장 시작·마감 전 are the Korean market's; a plan,
     * a target or the user's own alarm follows its holding's market (코인 never shuts, 미국 주식 by the NYSE's days).
     */
    fun venue(alarm: InvestAlarm): MarketDays.Venue = when (alarm.kind) {
        AlarmKind.MARKET_OPEN, AlarmKind.MARKET_CLOSE -> MarketDays.Venue.KR
        else -> MarketDays.venueOf(alarm.market)
    }

    /** Whether [alarm] passes over [d] because its market is shut. */
    fun skips(alarm: InvestAlarm, d: LocalDate): Boolean = alarm.skipHolidays && MarketDays.closed(d, venue(alarm))

    /**
     * The minute of the day [alarm] goes off on [d]: on 수능일 and the year's first trading day the Korean market opens an
     * hour late (and on 수능일 shuts an hour late), so 장 시작, 마감 전 and a morning plan move with it.
     */
    fun minuteOn(alarm: InvestAlarm, d: LocalDate): Int {
        if (venue(alarm) != MarketDays.Venue.KR) return alarm.minute
        val late = when (alarm.kind) {
            AlarmKind.MARKET_OPEN -> MarketDays.krOpenLate(d)
            AlarmKind.MARKET_CLOSE -> MarketDays.krCloseLate(d)
            AlarmKind.PLAN -> if (alarm.minute in 9 * 60 until 10 * 60) MarketDays.krOpenLate(d) else 0
            else -> 0
        }
        return (alarm.minute + late).coerceAtMost(23 * 60 + 59)
    }

    private fun timeOn(alarm: InvestAlarm, d: LocalDate): java.time.LocalTime = minuteOn(alarm, d).let { java.time.LocalTime.of(it / 60, it % 60) }

    /** A new alarm of [kind] with the usual time and days: 09:00 장 시작, 15:20 마감 전, the 25th for a plan. */
    fun default(kind: AlarmKind): InvestAlarm = when (kind) {
        AlarmKind.MARKET_OPEN -> InvestAlarm(kind = kind, minute = 9 * 60, weekdays = WEEKDAYS)
        AlarmKind.MARKET_CLOSE -> InvestAlarm(kind = kind, minute = 15 * 60 + 20, weekdays = WEEKDAYS)
        AlarmKind.PLAN -> InvestAlarm(kind = kind, minute = 9 * 60 + 10, monthDay = 25)
        AlarmKind.TARGET -> InvestAlarm(kind = kind, minute = 15 * 60, weekdays = WEEKDAYS)
        AlarmKind.CUSTOM -> InvestAlarm(kind = kind, minute = 20 * 60, weekdays = DayOfWeek.entries.toSet(), skipHolidays = false)
    }

    /** The next time [alarm] goes off strictly after [after]; null when it never will again (or is off). */
    fun next(alarm: InvestAlarm, after: LocalDateTime): LocalDateTime? {
        if (!alarm.enabled) return null
        alarm.once?.let { d -> return d.atTime(timeOn(alarm, d)).takeIf { it.isAfter(after) } }
        alarm.monthDay?.let { day ->
            var ym = YearMonth.from(after.toLocalDate()).minusMonths(1)
            repeat(26) {
                var d = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
                while (skips(alarm, d)) d = d.plusDays(1)
                val at = d.atTime(timeOn(alarm, d))
                if (at.isAfter(after)) return at
                ym = ym.plusMonths(1)
            }
            return null
        }
        if (alarm.weekdays.isEmpty()) return null
        var d = after.toLocalDate()
        repeat(400) {
            if (d.dayOfWeek in alarm.weekdays && !skips(alarm, d)) {
                val at = d.atTime(timeOn(alarm, d))
                if (at.isAfter(after)) return at
            }
            d = d.plusDays(1)
        }
        return null
    }

    /** The alarms that go off exactly at [at]. */
    fun dueAt(alarms: List<InvestAlarm>, at: LocalDateTime): List<InvestAlarm> = alarms.filter { next(it, at.minusMinutes(1)) == at }

    fun eventNotice(e: InvestEvent): LocalDateTime? = if (!e.remind) null else e.date.atTime(EVENT_NOTICE_MINUTE / 60, EVENT_NOTICE_MINUTE % 60)

    /** The soonest alarm or date notice after [after]. */
    fun nextAny(alarms: List<InvestAlarm>, events: List<InvestEvent>, after: LocalDateTime): LocalDateTime? =
        (alarms.mapNotNull { next(it, after) } + events.mapNotNull { eventNotice(it)?.takeIf { t -> t.isAfter(after) } }).minOrNull()

    fun eventsAt(events: List<InvestEvent>, at: LocalDateTime): List<InvestEvent> = events.filter { eventNotice(it) == at }

    /** "평일 09:00", "매달 25일 09:10", "11월 3일 20:00". */
    fun describe(alarm: InvestAlarm): String {
        val time = "%02d:%02d".format(java.util.Locale.ROOT, alarm.minute / 60, alarm.minute % 60)
        val days = when {
            alarm.once != null -> "${alarm.once.monthValue}월 ${alarm.once.dayOfMonth}일"
            alarm.monthDay != null -> "매달 ${alarm.monthDay}일"
            alarm.weekdays == WEEKDAYS -> "평일"
            alarm.weekdays.size == 7 -> "매일"
            else -> DayOfWeek.entries.filter { it in alarm.weekdays }.joinToString("·") { DAY_NAMES[it.ordinal] }
        }
        return "$days $time"
    }

    private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")
}

/** [INV-08] How a 정기 매수 plan is going: how many times it was kept and what went in. */
data class PlanProgress(val alarm: InvestAlarm, val times: Int, val spent: Long, val last: LocalDate?, val doneThisPeriod: Boolean) {
    /** [INV-08] Said when [doneThisPeriod], by the plan's own stretch (as [InvestPlans.progress] counts it). */
    val doneLabel: String get() = when {
        alarm.monthDay != null -> "이번 달 완료"
        alarm.once != null -> "완료"
        alarm.weekdays.size >= 5 -> "오늘 완료"
        else -> "이번 주 완료"
    }
}

/**
 * [INV-08] A trade sheet filled from an alarm: the plan's holding, side, account and quantity (its
 * amount at the last typed price), so a tap on the notice and 저장 is the whole record.
 */
data class TradeDraft(
    val symbol: String = "",
    val name: String = "",
    val market: Market = Market.KR_STOCK,
    val side: Side = Side.BUY,
    val quantity: Double? = null,
    val price: Double? = null,
    val accountId: Long? = null,
    val alarmId: Long? = null,
    val date: LocalDate? = null,
    /** [INV-32] Tags the trade starts with, such as the app a notification came from. */
    val tags: List<String> = emptyList(),
    /** [INV-32] The waiting notification this draft confirms; it leaves the list once saved. */
    val alertAt: Long? = null,
)

object InvestPlans {
    fun progress(alarm: InvestAlarm, trades: List<Trade>, today: LocalDate): PlanProgress {
        val mine = trades.filter { it.alarmId == alarm.id }
        val last = mine.maxOfOrNull { it.date }
        val done = last != null && when {
            alarm.monthDay != null -> YearMonth.from(last) == YearMonth.from(today)
            alarm.once != null -> true
            alarm.weekdays.size >= 5 -> last == today
            else -> last.get(IsoFields.WEEK_BASED_YEAR) == today.get(IsoFields.WEEK_BASED_YEAR) &&
                last.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }
        return PlanProgress(alarm, mine.size, mine.filter { it.side == Side.BUY }.sumOf { it.grossWon + it.costsWon }.roundToLong(), last, done)
    }

    fun draft(alarm: InvestAlarm, book: InvestBook, today: LocalDate): TradeDraft {
        val price = book.quote(alarm.key)?.price ?: alarm.targetPrice
        val fx = if (alarm.market.foreign) book.settings.usdKrw else 1.0
        val qty = alarm.quantity ?: alarm.amount?.let { a -> price?.let { InvestFees.unitsFor(a, it, alarm.market, fx) } }
        return TradeDraft(alarm.symbol, alarm.name, alarm.market, alarm.side, qty, price,
            alarm.accountId ?: book.settings.lastAccountId, alarm.id, today)
    }

    /** [INV-08] A draft complete enough to be saved from the notice without opening the app. */
    fun canSaveAtOnce(d: TradeDraft): Boolean = d.key.isNotEmpty() && (d.quantity ?: 0.0) > 0 && (d.price ?: 0.0) > 0

    private val TradeDraft.key get() = holdingKey(symbol, name)
}

/** [INV-11] How near the goal is: by amount, by return, and when it may be reached. */
data class GoalProgress(
    val amountRatio: Double?,
    val amountLeft: Long?,
    val returnRatio: Double?,
    /** With [InvestSettings.monthlyAdd] and no growth, the months until the amount goal. */
    val monthsLeft: Int?,
    /** Won to add each month to reach the amount by [InvestSettings.goalDate]. */
    val perMonthToDate: Long?,
) {
    val hasGoal: Boolean get() = amountRatio != null || returnRatio != null
}

object InvestGoal {
    fun progress(settings: InvestSettings, summary: PortfolioSummary, today: LocalDate): GoalProgress {
        val goal = settings.goalAmount?.takeIf { it > 0 }
        val assets = summary.totalAssets
        val left = goal?.let { (it - assets).coerceAtLeast(0) }
        val returnGoal = settings.goalReturnPercent?.takeIf { it > 0 }
        val months = left?.takeIf { it > 0 }?.let { l -> settings.monthlyAdd?.takeIf { it > 0 }?.let { ceil(l.toDouble() / it).toInt() } }
            ?: if (left == 0L) 0 else null
        val perMonth = left?.takeIf { it > 0 }?.let { l ->
            settings.goalDate?.takeIf { it.isAfter(today) }?.let { d ->
                val m = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(d)).coerceAtLeast(1)
                ceil(l.toDouble() / m).toLong()
            }
        }
        return GoalProgress(
            amountRatio = goal?.let { (assets.toDouble() / it).coerceAtLeast(0.0) },
            amountLeft = left,
            returnRatio = returnGoal?.let { g -> summary.simpleReturn?.let { it * 100 / g } },
            monthsLeft = months,
            perMonthToDate = perMonth,
        )
    }
}
