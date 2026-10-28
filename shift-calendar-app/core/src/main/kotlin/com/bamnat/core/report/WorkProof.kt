package com.bamnat.core.report

import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.work.LaborCost
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * G 1.32 [WTYP-06] 608 근무 기록 증빙 (프로, 조사 `근무유형별-서비스-조사.md` 3·4·5장: 전공의·간호사·사무직·알바가 노조·노무
 * 상담·노동청에 내는 한 장): for the days picked, each day's shift, start and end, break, worked time and whether the times
 * are the calendar's own record (출퇴근 기록이나 직접 고친 시각) or the roster's, then each week's total against 52시간.
 */
object WorkProof {
    data class Day(val date: LocalDate, val shift: String, val start: LocalTime?, val end: LocalTime?, val breakMinutes: Int,
                   val minutes: Int, val extraMinutes: Int, val recorded: Boolean)

    fun days(from: LocalDate, to: LocalDate, resolve: (LocalDate) -> ResolvedDay): List<Day> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val r = resolve(d)
            val extra = r.extras.sumOf { e -> if (e.from != null && e.to != null) java.time.Duration.between(e.from, e.to).toMinutes().toInt() else 0 }
            Day(d, r.shiftType?.name ?: "", r.start, r.end, r.breakMinutes, r.scheduledMinutes, extra, r.hasCustomTimes)
        }.filter { it.minutes > 0 || it.extraMinutes > 0 }.toList()

    data class Week(val monday: LocalDate, val minutes: Int) {
        val over52: Boolean get() = minutes > 52 * 60
    }

    fun weeks(days: List<Day>): List<Week> = days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (m, l) -> Week(m, l.sumOf { it.minutes + it.extraMinutes }) }.sortedBy { it.monday }

    private fun hm(m: Int) = "${m / 60}시간" + if (m % 60 > 0) " ${m % 60}분" else ""
    private fun t(v: LocalTime?) = v?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "-"
    private fun d(v: LocalDate) = "${v.monthValue}/${v.dayOfMonth}(${"월화수목금토일"[v.dayOfWeek.value - 1]})"

    fun doc(who: String, from: LocalDate, to: LocalDate, days: List<Day>, made: LocalDate): ReportDoc {
        val weeks = weeks(days)
        val total = days.sumOf { it.minutes + it.extraMinutes }
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOf("기간" to "$from ~ $to", "근무한 날" to "${days.size}일", "일한 시간" to hm(total),
            "52시간 넘은 주" to "${weeks.count { it.over52 }}주") + listOfNotNull(who.takeIf { it.isNotBlank() }?.let { "이름" to it }))
        blocks += ReportDoc.Heading("날짜별 기록")
        blocks += ReportDoc.Table(listOf("날짜", "근무", "시작", "끝", "휴게", "일한 시간", "더 일함", "기록"),
            days.map { listOf(d(it.date), it.shift, t(it.start), t(it.end), "${it.breakMinutes}분", hm(it.minutes),
                if (it.extraMinutes > 0) hm(it.extraMinutes) else "", if (it.recorded) "실제 기록" else "근무표") },
            listOf(1.4f, 1.2f, 1f, 1f, 0.8f, 1.3f, 1.2f, 1f), right = setOf(5, 6),
            total = listOf("합계", "", "", "", "", hm(days.sumOf { it.minutes }), hm(days.sumOf { it.extraMinutes }), ""))
        blocks += ReportDoc.Heading("주별 합계")
        blocks += ReportDoc.Table(listOf("주 (월요일)", "일한 시간", "52시간"), weeks.map { listOf(d(it.monday), hm(it.minutes), if (it.over52) "넘음" else "") },
            listOf(1.5f, 1.5f, 1f), right = setOf(1))
        blocks += ReportDoc.Paragraph("'실제 기록'은 출퇴근 자동 기록이나 그날 직접 고친 시각이고, '근무표'는 정해 둔 근무 시간이에요. " +
            "휴게시간은 근로기준법 제54조대로 뺐어요. 만든 날 $made · 밤낮", small = true)
        return ReportDoc("근무 기록 증빙 · $from ~ $to", blocks, footer = "밤낮 · 근무 기록 증빙")
    }
}

/**
 * G 1.32 [WTYP-03] 605 사장님 직원 근무표: 3명까지 무료 (조사 6장: 경쟁 앱이 3인 이하까지 구독을 걸어 화를 샀어요). A fourth and
 * more, and the per-staff PDF, are 프로. Staff already saved are always shown and kept, whatever the tier.
 */
object StaffReport {
    const val FREE_STAFF = 3

    fun canAdd(roster: StaffRoster, pro: Boolean): Boolean = pro || roster.staff.size < FREE_STAFF

    /** One staff member's month: each working day's hours, then pay, 주휴 and the employer's 4대보험 share. */
    fun doc(s: Staff, month: YearMonth, roster: StaffRoster, shop: String, made: LocalDate): ReportDoc {
        val line = LaborCost.line(s, month, roster.legalBreaks)
        val rows = (1..month.lengthOfMonth()).map { month.atDay(it) }.mapNotNull { day ->
            s.shifts[day.dayOfWeek]?.let { sh ->
                listOf("${day.monthValue}/${day.dayOfMonth}(${"월화수목금토일"[day.dayOfWeek.value - 1]})",
                    "%02d:%02d~%02d:%02d".format(sh.start.hour, sh.start.minute, sh.end.hour, sh.end.minute),
                    "${sh.paidMinutes(roster.legalBreaks) / 60.0}".removeSuffix(".0") + "시간")
            }
        }
        fun won(v: Long) = "%,d원".format(v)
        val blocks = listOf(
            ReportDoc.Facts(listOf("이름" to s.name.ifBlank { "이름 없음" }, "시급" to won(s.hourlyWage), "일한 시간" to "${line.workMinutes / 60}시간",
                "주휴수당" to if (s.holidayPay) "줌" else "안 줌")),
            ReportDoc.Table(listOf("날짜", "시간", "인정 시간"), rows, listOf(1.4f, 2f, 1f), right = setOf(2)),
            ReportDoc.Facts(listOf("급여" to won(line.base), "주휴수당" to won(line.holidayPay), "합계" to won(line.pay)) +
                listOfNotNull(if (line.insurance > 0) "4대보험 (사장님 몫)" to won(line.insurance) else null)),
            ReportDoc.Paragraph("근무표대로 셌어요. 야간·휴일 가산과 근로자 몫 4대보험·세금은 빠져 있어요. 참고용 · 만든 날 $made · 밤낮", small = true),
        )
        return ReportDoc("${shop.ifBlank { "가게" }} · ${s.name.ifBlank { "직원" }} ${month.year}년 ${month.monthValue}월 근무·급여", blocks, footer = "밤낮 · 직원 근무")
    }
}
