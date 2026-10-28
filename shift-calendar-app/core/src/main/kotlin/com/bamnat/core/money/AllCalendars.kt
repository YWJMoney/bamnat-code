package com.bamnat.core.money

/** [COMN-02][COMN-03] One calendar's month in 모든 달력 합계, with its work days and hours. */
data class CalendarMonth(
    val calendar: String,
    val name: String,
    val money: MonthMoney,
    val workDays: Int = 0,
    val workMinutes: Int = 0,
)

/**
 * [COMN-02][COMN-03] Every calendar's month added up: what came in, what went out and what is left,
 * whatever kind of work each calendar is. Each calendar keeps its own book, so nothing is counted twice.
 */
data class AllCalendarsMonth(val rows: List<CalendarMonth>) {
    val income: Long get() = rows.sumOf { it.money.income }
    val spent: Long get() = rows.sumOf { it.money.spent }
    val balance: Long get() = income - spent
    val workDays: Int get() = rows.sumOf { it.workDays }
    val workMinutes: Int get() = rows.sumOf { it.workMinutes }
}

/**
 * G 1.32 [WTYP-07] 609 N잡: the year so far by calendar, each one's income and its share, for the 5월 종합소득세 talk
 * (근로소득 말고 다른 수입이 있으면 신고해요).
 */
data class AllCalendarsYear(val year: Int, val months: List<AllCalendarsMonth>) {
    data class Row(val calendar: String, val name: String, val income: Long, val workDays: Int)

    val rows: List<Row> get() = months.flatMap { it.rows }.groupBy { it.calendar }.map { (id, l) ->
        Row(id, l.last().name, l.sumOf { it.money.income }, l.sumOf { it.workDays })
    }.sortedByDescending { it.income }
    val income: Long get() = rows.sumOf { it.income }
    fun share(r: Row): Int = if (income <= 0) 0 else (r.income * 100 / income).toInt()
    /** More than one calendar brought money in: the 5월 신고 line is worth a look. */
    val manyJobs: Boolean get() = rows.count { it.income > 0 } >= 2
}
