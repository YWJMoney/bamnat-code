package com.bamnat.core.money

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToLong

/**
 * 1.38 [LEDH-24] 근무 전 한 줄 예고 (조사 B4): 통계를 행동으로. "내일 야간이에요. 지난 야간 날엔 배달에 평균 1만 8천 원 썼어요."
 * 그 근무 날 한 분류에 평소보다 뚜렷이 더 쓴 때만(같은 근무 3번 이상, 평균 5천 원 이상, 다른 날 평균의 1.5배 이상) 말해요.
 * 주 1회까지, 전날 고른 시각(기본 저녁 8시)에. 금액·분류는 휴대폰 밖으로 나가지 않아요.
 */
object ShiftPreview {
    const val LOOK_BACK = 90L
    const val MIN_DAYS = 3
    const val MIN_AVERAGE = 5_000L
    const val RATIO = 1.5
    const val EVERY_DAYS = 7L
    const val DEFAULT_HOUR = 20

    data class Tip(val day: LocalDate, val label: String, val category: String, val average: Long, val usual: Long)

    /**
     * The tip for [day] whose work is [label]: the category spent on most more on such days over the last [LOOK_BACK] days
     * than on other days, or null when nothing stands out. [labelOf] gives each day's work name (null for a day off).
     */
    fun tipFor(day: LocalDate, label: String, entries: List<MoneyEntry>, today: LocalDate, labelOf: (LocalDate) -> String?): Tip? {
        val from = today.minusDays(LOOK_BACK)
        val days = (0 until LOOK_BACK).map { today.minusDays(it + 1) }
        val same = days.filter { labelOf(it) == label }.toSet()
        if (same.size < MIN_DAYS) return null
        val others = days.size - same.size
        if (others <= 0) return null
        val spend = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && it.source != MoneySource.FIXED && it.fixedId == null &&
            it.amount < Budgets.LUMP && !it.date.isBefore(from) && it.date.isBefore(today) }
        return spend.groupBy { it.category }.mapNotNull { (cat, list) ->
            val on = list.filter { it.date in same }
            if (on.map { it.date }.distinct().size < 2) return@mapNotNull null
            val avg = (on.sumOf { it.amount }.toDouble() / same.size).roundToLong()
            val usual = (list.filter { it.date !in same }.sumOf { it.amount }.toDouble() / others).roundToLong()
            if (avg < MIN_AVERAGE || avg < usual * RATIO) null else Tip(day, label, cat, avg, usual)
        }.maxByOrNull { it.average - it.usual }
    }

    /**
     * The next preview after [now]: the soonest workday from tomorrow within a week whose work has a tip, told the evening
     * before at [hour]; none while one was told less than [EVERY_DAYS] days ago ([lastTold]).
     */
    fun next(now: LocalDateTime, hour: Int, lastTold: LocalDate?, entries: List<MoneyEntry>, labelOf: (LocalDate) -> String?): Pair<LocalDateTime, Tip>? {
        val today = now.toLocalDate()
        val earliest = lastTold?.plusDays(EVERY_DAYS)
        for (ahead in 1L..8L) {
            val day = today.plusDays(ahead)
            val at = day.minusDays(1).atTime(LocalTime.of(hour.coerceIn(0, 23), 0))
            if (!at.isAfter(now) || (earliest != null && at.toLocalDate().isBefore(earliest))) continue
            val label = labelOf(day) ?: continue
            val tip = tipFor(day, label, entries, today, labelOf) ?: continue
            return at to tip
        }
        return null
    }

    /** "내일 야간이에요" and "지난 야간 날엔 배달에 평균 1만 8천 원 썼어요. 도시락을 챙기면 아낄 수 있어요". */
    fun words(tip: Tip, won: (Long) -> String): Pair<String, String> {
        val hint = when (tip.category) {
            "배달", "식비", "편의점" -> " 도시락이나 간식을 챙기면 아낄 수 있어요."
            "카페" -> " 마실 것을 챙겨 가면 아낄 수 있어요."
            "교통" -> " 돌아오는 길을 미리 정해 두면 아낄 수 있어요."
            else -> ""
        }
        val last = tip.label.lastOrNull { it in '가'..'힣' }
        val ieyo = if (last == null || (last - '가') % 28 != 0) "이에요" else "예요"
        return "내일 ${tip.label}$ieyo" to "지난 ${tip.label} 날엔 ${tip.category}에 평균 ${won(tip.average)} 썼어요(다른 날 ${won(tip.usual)}).$hint"
    }
}
