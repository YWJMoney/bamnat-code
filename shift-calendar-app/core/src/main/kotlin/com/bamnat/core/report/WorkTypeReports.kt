package com.bamnat.core.report

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.model.isNight
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatement
import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.YearMonth

private fun hm(m: Int) = "${m / 60}시간" + if (m % 60 > 0) " ${m % 60}분" else ""
private fun won(v: Long) = "%,d원".format(v)
private fun md(v: LocalDate) = "${v.monthValue}/${v.dayOfMonth}(${"월화수목금토일"[v.dayOfWeek.value - 1]})"

/**
 * 1.32 [WTYP-10] (근무유형 조사 1장 교대: "1년 동안 야간 몇 번, 휴일 몇 번, 수당 합계를 한 장으로" 프로): a year of a shift
 * calendar on one page, month by month: days worked, 야간 shifts, 공휴일 and 쉬는 날 work, hours and the month's 예상 급여.
 */
object YearShiftReport {
    data class Month(val month: YearMonth, val workDays: Int, val nights: Int, val holidayDays: Int, val restDayWork: Int,
                     val minutes: Int, val extraMinutes: Int, val pay: Long?)

    fun months(year: Int, through: LocalDate, resolve: (LocalDate) -> ResolvedDay, pay: (YearMonth) -> Long?): List<Month> =
        (1..12).map { YearMonth.of(year, it) }.filter { !it.atDay(1).isAfter(through) }.map { m ->
            val last = minOf(m.atEndOfMonth(), through)
            val days = generateSequence(m.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map(resolve).toList()
            val working = days.filter { it.totalMinutes > 0 }
            Month(
                month = m,
                workDays = working.size,
                nights = days.count { it.isWorkDay && it.shiftType?.isNight == true } + days.sumOf { d -> d.extras.count { it.covered?.isNight == true } },
                holidayDays = working.count { KoreanHolidays.isHoliday(it.date) },
                restDayWork = days.count { it.isRestDay && it.extraMinutes > 0 },
                minutes = working.sumOf { it.totalMinutes },
                extraMinutes = days.sumOf { it.extraMinutes },
                pay = pay(m),
            )
        }

    fun doc(year: Int, who: String, months: List<Month>, made: LocalDate): ReportDoc {
        val pays = months.mapNotNull { it.pay }
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOfNotNull(
            "근무한 날" to "${months.sumOf { it.workDays }}일",
            "야간" to "${months.sumOf { it.nights }}번",
            "공휴일 근무" to "${months.sumOf { it.holidayDays }}일",
            "쉬는 날 근무" to "${months.sumOf { it.restDayWork }}일",
            "일한 시간" to hm(months.sumOf { it.minutes }),
            "추가 근무" to hm(months.sumOf { it.extraMinutes }),
            pays.takeIf { it.isNotEmpty() }?.let { "예상 급여 합계(세전)" to won(it.sum()) },
            who.takeIf { it.isNotBlank() }?.let { "이름" to it },
        ))
        blocks += ReportDoc.Heading("달마다")
        blocks += ReportDoc.Table(
            listOf("달", "근무", "야간", "공휴일", "쉬는 날", "일한 시간", "추가 근무", "예상 급여"),
            months.map { listOf("${it.month.monthValue}월", "${it.workDays}일", "${it.nights}번", "${it.holidayDays}일", "${it.restDayWork}일",
                hm(it.minutes), hm(it.extraMinutes), it.pay?.let(::won) ?: "-") },
            listOf(0.7f, 0.8f, 0.8f, 0.9f, 0.9f, 1.3f, 1.3f, 1.4f), right = setOf(1, 2, 3, 4, 5, 6, 7),
            total = listOf("합계", "${months.sumOf { it.workDays }}일", "${months.sumOf { it.nights }}번", "${months.sumOf { it.holidayDays }}일",
                "${months.sumOf { it.restDayWork }}일", hm(months.sumOf { it.minutes }), hm(months.sumOf { it.extraMinutes }),
                if (pays.isEmpty()) "-" else won(pays.sum())),
        )
        blocks += ReportDoc.Paragraph("야간은 야간 근무와 야간 대근을 센 거예요. 예상 급여는 달력과 급여 설정으로 어림한 세전 금액이고, " +
            "실제로 받은 돈과 다를 수 있어요. 만든 날 $made · 밤낮", small = true)
        return ReportDoc("${year}년 교대 근무 리포트", blocks, footer = "밤낮 · 연간 교대 리포트")
    }
}

/** 1.32 [WTYP-20] (근무유형 조사 6·9장 "거래처·업체별 정산서 PDF"): the 거래내역서 of one client's month, on paper. */
object ClientStatementDoc {
    fun doc(s: JobStatement.Statement, sender: String, made: LocalDate): ReportDoc {
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOfNotNull("거래처" to s.client, "기간" to "${s.month.year}년 ${s.month.monthValue}월",
            sender.takeIf { it.isNotBlank() }?.let { "보내는 사람" to it }))
        blocks += ReportDoc.Table(
            listOf("날짜", "일", "금액", "3.3%", "받은 돈", "남은 돈"),
            s.lines.map { l ->
                listOf(md(l.date) + if (l.lastDate != l.date) "~${l.lastDate.monthValue}/${l.lastDate.dayOfMonth}" else "", l.title,
                    won(l.amount), if (l.withheld > 0) won(l.withheld) else "", won(l.received), won(l.unpaid))
            },
            listOf(1.3f, 2.2f, 1.2f, 1f, 1.2f, 1.2f), right = setOf(2, 3, 4, 5),
            total = listOf("합계", "${s.lines.size}건", won(s.amount), if (s.withheld > 0) won(s.withheld) else "", won(s.received), won(s.unpaid)),
        )
        if (s.unpaid > 0) blocks += ReportDoc.Paragraph("남은 돈 ${won(s.unpaid)}을 보내 주세요.")
        blocks += ReportDoc.Paragraph("3.3%는 소득세 3%와 지방소득세 0.3%예요. 만든 날 $made · 밤낮", small = true)
        return ReportDoc("거래내역서 · ${s.client} · ${s.month.year}년 ${s.month.monthValue}월", blocks, footer = "밤낮 · 거래내역서")
    }
}

/**
 * 1.32 [WTYP-28] (근무유형 조사 9장 건설: "실업급여·퇴직공제 신청 때 낼 근무일 증빙" 프로): every day worked from [from] to
 * [to] with where and for whom, month by month, and the counts the two applications ask about. Reference only.
 */
object FieldDaysProof {
    data class Day(val date: LocalDate, val client: String, val place: String, val title: String, val units: Double?)

    fun days(jobs: List<Job>, from: LocalDate, to: LocalDate): List<Day> = jobs.filter { !it.cancelled }
        .flatMap { j ->
            generateSequence(maxOf(j.date, from)) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(j.lastDate, to)) }
                .map { Day(it, j.client.trim(), j.place.trim(), j.title.trim(), j.units) }.toList()
        }
        .groupBy { it.date }.map { (_, l) -> l.first() }.sortedBy { it.date }

    /** Days worked in the month before [to] (구직급여 일용 신청: 10일 미만인지). */
    fun lastMonthDays(days: List<Day>, to: LocalDate): Int = days.count { it.date.isAfter(to.minusMonths(1)) && !it.date.isAfter(to) }

    fun doc(who: String, from: LocalDate, to: LocalDate, days: List<Day>, made: LocalDate): ReportDoc {
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOfNotNull("기간" to "$from ~ $to", "일한 날" to "${days.size}일",
            "신청 전 1개월 일한 날" to "${lastMonthDays(days, to)}일", who.takeIf { it.isNotBlank() }?.let { "이름" to it }))
        blocks += ReportDoc.Heading("달마다 일한 날")
        val byMonth = days.groupBy { YearMonth.from(it.date) }.toSortedMap()
        blocks += ReportDoc.Table(listOf("달", "일한 날", "공수"), byMonth.map { (m, l) ->
            listOf("${m.year}.${m.monthValue}", "${l.size}일", l.mapNotNull { it.units }.takeIf { it.isNotEmpty() }?.sum()?.let(DayRate::label) ?: "-")
        }, listOf(1f, 1f, 1f), right = setOf(1, 2), total = listOf("합계", "${days.size}일", days.mapNotNull { it.units }.takeIf { it.isNotEmpty() }?.sum()?.let(DayRate::label) ?: "-"))
        blocks += ReportDoc.Heading("날짜별")
        blocks += ReportDoc.Table(listOf("날짜", "현장", "업체", "일", "공수"),
            days.map { listOf(md(it.date), it.place, it.client, it.title, it.units?.let(DayRate::label) ?: "") },
            listOf(1.2f, 1.6f, 1.4f, 1.6f, 0.7f), right = setOf(4))
        blocks += ReportDoc.Paragraph("밤낮에 적은 일감으로 만든 참고 자료예요. 구직급여(실업급여)는 고용보험 근로내용확인신고, 퇴직공제는 " +
            "건설근로자공제회에 신고된 날로 정해져요. 적은 날과 다르면 고용센터·공제회에 이 표를 보여 주고 물어보세요. 만든 날 $made · 밤낮", small = true)
        return ReportDoc("근무일 증빙 · $from ~ $to", blocks, footer = "밤낮 · 근무일 증빙")
    }
}
