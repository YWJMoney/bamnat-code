package com.bamnat.core.notify

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth

/**
 * 1.32 [WTYP-11][WTYP-29] Notices tied to a date rather than a shift, one alarm for the next of them:
 * - 희망 오프 신청 마감 (근무유형 조사 3장 의료): two days before the day the next month's requests close, and on the day,
 *   saying how many days are picked for that month.
 * - 코인 12월 31일 시가 기록 (조사 10장 투자): on 12/31, to write down each coin's price as the year ends (2027년 가상자산
 *   과세는 연말 시가가 기준이 될 수 있어서, 참고용).
 */
object DateNotices {
    enum class Kind { WISH_OFF, COIN_YEAR_END }

    data class Notice(val kind: Kind, val at: LocalDateTime, val title: String, val text: String, val calendarId: String, val key: String)

    val AT: LocalTime = LocalTime.of(9, 0)
    const val DAYS_BEFORE = 2L

    /** When a calendar's next month requests close in [month]. */
    fun deadline(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The 희망 오프 notices of one calendar from [now] on, the next two. */
    fun wishOff(calendarId: String, name: String, deadlineDay: Int?, picked: (YearMonth) -> Int, now: LocalDateTime): List<Notice> {
        val day = deadlineDay ?: return emptyList()
        return (0L..2L).flatMap { plus ->
            val m = YearMonth.from(now).plusMonths(plus)
            val close = deadline(m, day)
            val target = m.plusMonths(1)
            val n = picked(target)
            val who = if (name.isBlank()) "" else "$name · "
            listOf(
                Notice(Kind.WISH_OFF, close.minusDays(DAYS_BEFORE).atTime(AT), "${who}${target.monthValue}월 희망 오프 마감이 모레예요",
                    if (n > 0) "고른 날 ${n}일 · ${close.monthValue}월 ${close.dayOfMonth}일까지 신청해요" else "아직 고른 날이 없어요 · 달력에서 날짜를 눌러 ‘희망 오프’를 골라요",
                    calendarId, "wish:$calendarId:$close:before"),
                Notice(Kind.WISH_OFF, close.atTime(AT), "${who}오늘 ${target.monthValue}월 희망 오프 마감이에요",
                    if (n > 0) "고른 날 ${n}일" else "아직 고른 날이 없어요", calendarId, "wish:$calendarId:$close:day"),
            )
        }.filter { it.at.isAfter(now) }.sortedBy { it.at }.take(2)
    }

    private val YEAR_END: MonthDay = MonthDay.of(12, 31)

    /** The 12/31 notice of an investor calendar that holds coins. */
    fun coinYearEnd(calendarId: String, coins: Int, now: LocalDateTime): Notice? {
        if (coins <= 0) return null
        val thisYear = YEAR_END.atYear(now.year).atTime(AT)
        val at = if (thisYear.isAfter(now)) thisYear else YEAR_END.atYear(now.year + 1).atTime(AT)
        return Notice(Kind.COIN_YEAR_END, at, "올해 마지막 날 코인 시가를 적어 둘까요?",
            "코인 ${coins}개 · 오늘 값을 투자일지에 남겨 두면 내년 세금 계산에 쓸 수 있어요(참고용)", calendarId, "coin:$calendarId:${at.year}")
    }

    /** The next notice of all, and every one due at that minute. */
    fun next(all: List<Notice>, after: LocalDateTime): List<Notice> {
        val first = all.filter { it.at.isAfter(after) }.minByOrNull { it.at } ?: return emptyList()
        return all.filter { it.at == first.at }
    }
}
