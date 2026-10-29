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

/**
 * 1.32 [WTYP-19] (근무유형 조사 7장 프리랜서·N잡) 소득 종류: how a calendar's income is taxed, for 한 해 소득 종류별 합계. The kind of work
 * gives the first guess ([guess]); the user can say otherwise per calendar (3.3% 프리랜서 is 사업, 8.8% 강연·원고료 is 기타).
 */
enum class IncomeType(val label: String, val note: String) {
    WAGE("근로소득", "회사가 연말정산으로 끝내요. 다른 소득이 있으면 5월에 합쳐 신고해요."),
    DAILY("일용근로소득", "일당에서 세금을 떼면 그걸로 끝나요(분리과세). 신고에 넣지 않아요."),
    BUSINESS("사업소득", "3.3%를 뗐어도 다음 해 5월 종합소득세 신고 대상이에요."),
    OTHER("기타소득", "8.8%를 뗀 강연·원고료 등. 한 해 기타소득금액이 300만 원 이하면 분리과세를 고를 수 있어요."),
    ;

    companion object {
        fun guess(style: com.bamnat.core.work.WorkStyle?, field: FieldRules? = null): IncomeType? = when (style) {
            null, com.bamnat.core.work.WorkStyle.SHIFT, com.bamnat.core.work.WorkStyle.DAY, com.bamnat.core.work.WorkStyle.HOURLY -> WAGE
            com.bamnat.core.work.WorkStyle.FIELD -> if (field?.dayRateMode == true) DAILY else BUSINESS
            com.bamnat.core.work.WorkStyle.BUSINESS -> BUSINESS
            // 투자 손익은 양도·금융소득으로 따로예요.
            com.bamnat.core.work.WorkStyle.INVEST -> null
        }

        fun of(name: String?): IncomeType? = entries.firstOrNull { it.name == name }
    }
}

/** 1.32 [WTYP-19] The year's income by [IncomeType], the largest first; calendars without a type are left out. */
fun AllCalendarsYear.byType(typeOf: (String) -> IncomeType?): List<Pair<IncomeType, Long>> =
    rows.mapNotNull { r -> typeOf(r.calendar)?.let { it to r.income } }.groupBy({ it.first }, { it.second })
        .map { (t, l) -> t to l.sum() }.filter { it.second > 0 }.sortedByDescending { it.second }
