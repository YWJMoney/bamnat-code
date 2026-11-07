package com.bamnat.core.pay

import java.time.LocalDate

/**
 * 1.39 [PAYX-62] 발굴 G3-M3: 알바 주 15시간 경계 알림. 주휴수당은 한 주 소정근로가 15시간 이상일 때 받아요
 * (근로기준법 제18조 제3항, 제55조). 이번 주(달력에 넣은 근무 포함)가 15시간 바로 아래면 얼마 더 일하면 받는지,
 * 바로 위면 줄면 없어진다는 것을, 이 달의 다른 주가 자꾸 15시간 바로 아래로 맞춰져 있으면 그것도 한 줄로 말해요.
 * 끄고 켜는 곳은 설정 › 알림과 잠 › 돈 › 주 15시간 경계 알림이에요(앱 안 한 줄, 소리 나는 알림은 아니에요).
 */
object WeekHoursNudge {
    private const val LINE = 15 * 60
    /** 이만큼 아래부터 '바로 아래'로 봐요(3시간). */
    private const val NEAR_BELOW = 3 * 60
    /** 이만큼 위까지 '바로 위'로 봐요(1시간). */
    private const val NEAR_ABOVE = 60

    const val ROW = "주 15시간 경계 알림"
    const val WHERE = "설정 › 알림과 잠 › 돈 › $ROW"

    fun lines(weeks: List<HolidayWeek>, today: LocalDate): List<String> {
        val out = mutableListOf<String>()
        val now = weeks.firstOrNull { !today.isBefore(it.from) && !today.isAfter(it.to) && !it.absent }
        if (now != null) {
            val w = now.workedMinutes
            when {
                w in (LINE - NEAR_BELOW) until LINE ->
                    out += "이번 주(${md(now.from)}~${md(now.to)}) 근무가 ${hm(w)}이에요. ${hm(LINE - w)} 더 일하면 주 15시간이 돼 주휴수당을 받아요(근로기준법 제55조)."
                w in LINE..(LINE + NEAR_ABOVE) ->
                    out += "이번 주(${md(now.from)}~${md(now.to)}) 근무가 ${hm(w)}이라 주휴수당을 받아요. ${if (w == LINE) "조금이라도" else "지금보다 ${hm(w - LINE)} 넘게"} 줄면 15시간 아래라 주휴수당이 없어요."
            }
        }
        val justBelow = weeks.count { !it.absent && it.workedMinutes in (LINE - 2 * 60) until LINE }
        if (justBelow >= 2) out += "이 달 ${justBelow}주가 14시간 언저리(13~15시간 사이)예요. 주 15시간 바로 아래로 맞춰진 근무표일 수 있어요."
        return out
    }

    private fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

    /** 14시간 30분, 30분, 2시간. */
    private fun hm(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}분"
            m == 0 -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }
}
