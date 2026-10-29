package com.bamnat.core.work

/**
 * How the user works, chosen on first launch [WORK-01]. It decides which setup comes first, what
 * the calendar shows and where the month's income comes from [WORK-04]; everything else stays
 * reachable from settings, so a wrong pick never hides data.
 */
enum class WorkStyle(val label: String, val description: String, val examples: String) {
    SHIFT("교대·주기 근무", "정해진 순서로 근무가 돌아가요", "3조 2교대, 4조 2교대, 24시간 맞교대"),
    DAY("일근 (평일 근무)", "매주 같은 요일에 같은 시간 일해요", "사무직, 공장 주간, 공무원"),
    HOURLY("아르바이트·시급", "일한 날과 시간만큼 시급으로 받아요", "편의점, 카페, 파트타임"),
    FIELD("현장·프리랜서", "일정을 잡고 건마다 돈을 받아요", "인테리어, 시공, 강사, 촬영"),
    BUSINESS("자영업", "가게를 운영하며 매일 매출이 생겨요", "식당, 카페, 매장 운영"),
    /** [INV-01] Not work but trading: the calendar holds trades, deposits and the day's gain or loss. */
    INVEST("투자", "주식·ETF·코인 매매를 기록하고 수익을 봐요", "국내·해외 주식, ETF, 코인, ISA·연금");

    /** A repeating pattern drives the calendar, so setup starts at the pattern screen. */
    val usesPattern: Boolean get() = this == SHIFT || this == DAY

    /** Income is wages from the pay engine (hours × rates) [WORK-04]. */
    val usesPayEngine: Boolean get() = this == SHIFT || this == DAY || this == HOURLY

    /** Days hold booked jobs with a price, deposit and paid state [WORK-02]. */
    val usesJobs: Boolean get() = this == FIELD

    /** Days hold the shop's sales and costs [WORK-03]. */
    val usesSales: Boolean get() = this == BUSINESS

    /** [INV-01] Days hold trades and the day's gain or loss. */
    val usesTrades: Boolean get() = this == INVEST

    /**
     * [J133-71] 1.33 (사용자 2026-10-29 "알바 파트타임 같은 유형에서 회사 검색같은것은 의미가 없지 않아? … 유형에 맞게 내용들이
     * 보여지게"): a company's shared setup (회사 찾기, 회사 고르기, 같은 회사 설정, 회사 게시판) is offered only to work that has one:
     * 교대·일근. 알바 has its own 알바 광장 ([com.bamnat.core.plaza.StylePlaza]); a part-timer who already joined a company keeps it.
     */
    val usesCompany: Boolean get() = this == SHIFT || this == DAY

    /** [J133-71] Whose rules the pay follows, in this kind of work's words: 회사, or 근무지 for a part-time job (가게·매장·센터). */
    val employer: String get() = if (this == HOURLY) "근무지" else "회사"

    /** What the money tab calls the main income line. */
    val incomeLabel: String get() = when (this) {
        SHIFT, DAY, HOURLY -> "급여 (세전 예상)"
        FIELD -> "일감 수입"
        BUSINESS -> "가게 순수익"
        INVEST -> "투자 손익"
    }
}
