package com.bamnat.core.hours

import com.bamnat.core.schedule.ResolvedDay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.32 [WTYP-12] (근무유형 조사 3장 의료) 연속 근무: the shift and the extra work around it, joined when less than [GAP_MINUTES]
 * lie between (당직 뒤 바로 낮 근무), as one stretch from its first start to its last end. A stretch longer than the limit the
 * user set is pointed out; 전공의 미리 정한 값 ([Preset.RESIDENT]) is 연속 24시간 (응급 28시간), 주 최대 80시간·평균 72시간
 * (2026-02-21 전공의법 개정 시행·시범사업 기준, 조사 3장).
 */
object Stretches {
    const val GAP_MINUTES = 60L

    data class Stretch(val from: LocalDateTime, val to: LocalDateTime) {
        val minutes: Long get() = Duration.between(from, to).toMinutes()
        val hoursText: String get() = HoursFormat.korean(minutes.toInt())
    }

    /** Every stretch that starts in [from]..[to], read from a day before and after so one crossing midnight is whole. */
    fun of(from: LocalDate, to: LocalDate, resolve: (LocalDate) -> ResolvedDay): List<Stretch> {
        val spans = generateSequence(from.minusDays(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(to.plusDays(1)) }.flatMap { d ->
            val r = resolve(d)
            listOfNotNull(r.shiftFrom?.let { f -> r.shiftTo?.let { t -> f to t } }) +
                r.extras.mapNotNull { e -> e.from?.let { f -> e.to?.let { t -> f to t } } }
        }.filter { (f, t) -> t.isAfter(f) }.sortedBy { it.first }.toList()
        val out = ArrayList<Stretch>()
        for ((f, t) in spans) {
            val last = out.lastOrNull()
            if (last != null && !f.isAfter(last.to.plusMinutes(GAP_MINUTES))) out[out.size - 1] = last.copy(to = maxOf(last.to, t))
            else out += Stretch(f, t)
        }
        return out.filter { !it.from.toLocalDate().isBefore(from) && !it.from.toLocalDate().isAfter(to) }
    }

    /** The stretches over [limitHours]; none when the limit is 0 (not checked). */
    fun over(stretches: List<Stretch>, limitHours: Int): List<Stretch> =
        if (limitHours <= 0) emptyList() else stretches.filter { it.minutes > limitHours * 60L }

    /** One line for a stretch over the limit, e.g. "10/3 08:00부터 30시간 연속 · 24시간 넘음". */
    fun line(s: Stretch, limitHours: Int): String =
        "${s.from.monthValue}/${s.from.dayOfMonth} %02d:%02d부터 ${s.hoursText} 연속 · ${limitHours}시간 넘음".format(s.from.hour, s.from.minute)

    /** The 근무시간 경고 values a tap sets: 한 주, 3개월 평균, 연속. */
    enum class Preset(val label: String, val weekly: Int, val average: Int, val stretch: Int, val note: String) {
        GENERAL("일반", 64, 52, 0, "주 64시간 · 3개월 평균 52시간 (근로기준법 제53조)"),
        RESIDENT("전공의", 80, 72, 24, "주 최대 80시간 · 평균 72시간 · 연속 24시간(응급 28시간) — 2026년 2월 전공의법 개정·시범사업 기준"),
        ;

        fun matches(weekly: Int, average: Int, stretch: Int): Boolean = this.weekly == weekly && this.average == average && this.stretch == stretch
    }

    /** 응급 시 연속 한도 (전공의). */
    const val RESIDENT_EMERGENCY_HOURS = 28
}
