package com.bamnat.core.report

import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.39 G5-N1 (사용자 결정 1차 14번: 무료) 2주 수면 일지: the last two weeks as one table to take to a doctor or the 사내 보건실,
 * the work of each day next to the sleep that ended on it ([SleepEntry.wakeDate], as the sleep screen counts it), the naps and
 * the morning's mood. Made on the phone from the phone's own records; the words are a record, never a diagnosis.
 */
object SleepDiary {
    const val DAYS = 14L
    /** Shorter than this is a nap, in its own column. */
    const val NAP_MINUTES = 180L

    fun doc(
        today: LocalDate,
        entries: List<SleepEntry>,
        checks: List<SleepCheck>,
        work: Map<LocalDate, List<SleepSchedule.Work>>,
        targetMinutes: Int,
        now: LocalDateTime = today.atTime(12, 0),
    ): ReportDoc {
        val from = today.minusDays(DAYS - 1)
        val dates = (0 until DAYS).map { from.plusDays(it) }
        val done = entries.filter { (it.minutes ?: 0) in 1..SleepDebt.MAX_SLEEP_MINUTES }
        val byDay = done.groupBy { it.wakeDate!! }
        val rows = dates.map { d ->
            val day = byDay[d].orEmpty().sortedBy { it.start }
            val (main, naps) = day.partition { (it.minutes ?: 0) >= NAP_MINUTES }
            listOf(
                "${d.monthValue}/${d.dayOfMonth} (${weekday(d.dayOfWeek)})",
                work[d].orEmpty().joinToString(", ") { "${it.name} ${hm(it.from)}~${hm(it.to)}" }.ifEmpty { "쉼" },
                main.joinToString(", ") { span(it) }.ifEmpty { "—" },
                main.sumOf { it.minutes ?: 0 }.takeIf { it > 0 }?.let { HoursFormat.korean(it.toInt()) } ?: "—",
                naps.joinToString(", ") { span(it) }.ifEmpty { "" },
                day.firstNotNullOfOrNull { e -> checks.firstOrNull { it.start == e.start && it.end == e.end }?.mood?.label }.orEmpty(),
            )
        }
        val recorded = dates.count { d -> byDay[d].orEmpty().any { (it.minutes ?: 0) >= NAP_MINUTES } }
        val mainMinutes = dates.sumOf { d -> byDay[d].orEmpty().filter { (it.minutes ?: 0) >= NAP_MINUTES }.sumOf { it.minutes ?: 0 } }
        val nights = dates.sumOf { d -> work[d].orEmpty().count { it.night } }
        val facts = listOfNotNull(
            "기간" to "${from.monthValue}/${from.dayOfMonth}~${today.monthValue}/${today.dayOfMonth} (${DAYS}일)",
            "잠을 적은 날" to "${recorded}일",
            ("평균 잔 시간(적은 날)" to HoursFormat.korean((mainMinutes / recorded).toInt())).takeIf { recorded > 0 },
            "목표 잠" to HoursFormat.korean(targetMinutes),
            "야간 근무" to "${nights}번",
            dates.sumOf { d -> byDay[d].orEmpty().count { it.imported } }.takeIf { it > 0 }?.let { "워치·헬스 앱에서 읽은 잠" to "${it}번" },
        )
        return ReportDoc(
            title = "2주 수면 일지",
            blocks = listOf(
                ReportDoc.Facts(facts),
                ReportDoc.Table(
                    headers = listOf("날짜", "근무", "잠 (깬 날 기준)", "잔 시간", "낮잠", "아침 기분"),
                    rows = rows,
                    weights = listOf(1.1f, 2.2f, 2.2f, 1.3f, 1.4f, 1.0f),
                ),
                ReportDoc.Paragraph("밤낮 앱에 적은 근무와 잠을 정리한 기록이에요. 진단이 아니며, 상담할 때 함께 보여 주세요. " +
                    "잠은 깬 날에 적고, ${NAP_MINUTES / 60}시간보다 짧으면 낮잠 칸에 적어요.", small = true),
            ),
            footer = "밤낮 · ${now.toLocalDate()} ${hm(now)} 이 휴대폰에서 만듦",
        )
    }

    private fun span(e: SleepEntry) = "${hm(e.start)}~${hm(e.end!!)}" + if (e.imported) " (워치)" else ""
    private fun hm(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
    private fun weekday(d: DayOfWeek) = "월화수목금토일"[d.value - 1].toString()
}
