package com.bamnat.core.money

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.work.WorkStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [SHOP-04] Tax deadlines worth a nudge: 부가세 on 1/25 and 7/25 for a shop, and 종합소득세 on 5/31
 * for a shop and for field work (3.3% 환급). Each is told a week ahead and the day before, at nine.
 * A deadline on a weekend or public holiday moves to the next working day (국세기본법 제5조).
 */
object TaxDeadlines {
    data class Deadline(val date: LocalDate, val name: String, val text: String)
    data class Alert(val at: LocalDateTime, val key: String, val title: String, val text: String)

    val AT: LocalTime = LocalTime.of(9, 0)

    /** 2.0.0 [RVK-40] (검토 MONEY-50·T06b-02) A shop's 부가세: 일반과세 or 간이과세 (가게 설정의 간이과세). */
    enum class Vat { GENERAL, SIMPLE }

    /**
     * Deadlines for [styles] from [from] to [to]. 2.0.0 [RVK-40] (검토 MONEY-50·T06b-02) A shop's 부가세 by its [vat] (일반과세 when not
     * known): 일반과세 개인사업자 files 1/25 and 7/25 and pays the 예정고지 sent for 4/25 and 10/25 (부가가치세법 제48조 제3항);
     * 간이과세 files once a year on 1/25 for the year before (제67조) and pays on 7/25 only when a 예정부과 고지서 came (제66조).
     */
    fun deadlines(from: LocalDate, to: LocalDate, styles: Set<WorkStyle>, vat: Set<Vat> = emptySet()): List<Deadline> {
        val shop = WorkStyle.BUSINESS in styles
        val income = shop || WorkStyle.FIELD in styles
        if (!income) return emptyList()
        val kinds = if (!shop) emptySet() else vat.ifEmpty { setOf(Vat.GENERAL) }
        return (from.year..to.year).flatMap { y ->
            fun on(m: Int, d: Int) = workday(LocalDate.of(y, m, d))
            listOfNotNull(
                if (Vat.GENERAL in kinds) Deadline(on(1, 25), "부가세 신고", "지난해 7~12월 부가세 신고·납부 마감이에요")
                else if (Vat.SIMPLE in kinds) Deadline(on(1, 25), "부가세 신고", "지난해 부가세 신고·납부 마감이에요. 간이과세는 한 해에 한 번이에요") else null,
                if (Vat.GENERAL in kinds) Deadline(on(4, 25), "부가세 예정고지 납부", "1~3월 몫 예정고지서가 왔다면 납부 마감이에요") else null,
                Deadline(on(5, 31), "종합소득세 신고", "지난해 소득 신고 마감이에요. 3.3% 뗀 돈은 이때 돌려받을 수 있어요"),
                if (Vat.GENERAL in kinds) Deadline(on(7, 25), "부가세 신고", "올해 1~6월 부가세 신고·납부 마감이에요")
                else if (Vat.SIMPLE in kinds) Deadline(on(7, 25), "부가세 예정부과 납부", "고지서가 왔다면 납부 마감이에요. 간이과세는 신고하지 않아요") else null,
                if (Vat.GENERAL in kinds) Deadline(on(10, 25), "부가세 예정고지 납부", "7~9월 몫 예정고지서가 왔다면 납부 마감이에요") else null,
            )
        }.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
    }

    /** [date], or the next day that is not a weekend or a public holiday. */
    fun workday(date: LocalDate): LocalDate {
        var d = date
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || KoreanHolidays.isHoliday(d)) d = d.plusDays(1)
        return d
    }

    /** Reminders a week before and the day before each deadline from [today] on (about 13 months ahead). */
    fun alerts(today: LocalDate, styles: Set<WorkStyle>, vat: Set<Vat> = emptySet()): List<Alert> =
        deadlines(today, today.plusMonths(13), styles, vat).flatMap { d ->
            listOf(7L to "일주일 뒤", 1L to "내일").mapNotNull { (before, word) ->
                val day = d.date.minusDays(before)
                if (day.isBefore(today)) null
                else Alert(day.atTime(AT), "tax-${d.date}-$before", "$word ${d.name}", "${d.date.monthValue}월 ${d.date.dayOfMonth}일 · ${d.text}")
            }
        }
}
