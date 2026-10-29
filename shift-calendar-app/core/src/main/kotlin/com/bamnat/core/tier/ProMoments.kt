package com.bamnat.core.tier

import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/**
 * 1.32 [PROX-01][PROX-02] (가계부 조사 5장, 근무유형 조사 11장): when and how 프로 is offered.
 * - [opened]: what a 하루 이용권 opened today for this kind of work, each with where it is, so the day is spent on the
 *   features that answer a money question, and the line that says a subscription keeps them every day.
 * - [due]: the one "오늘 하루 무료로 열어 볼까요?" of a day the user's money is at stake (급여일, the day a 듀티표 went in,
 *   5월 종소세, 1·7월 부가세, 11~12월 연말정산 미리보기, 1~2월 연말정산), once a season each and never two within
 *   [GAP_DAYS]. Never with 프로 on, never before the 정식 출시 switch (the app checks both).
 */
object ProMoments {
    /** A 프로 feature a day pass opens: its guide id ([FeatureRegistry]) for the tap that opens it, and its tier name. */
    data class Opened(val featureId: String, val tier: String, val name: String, val what: String, val styles: Set<WorkStyle> = emptySet()) {
        fun fits(style: WorkStyle?): Boolean = styles.isEmpty() || (style ?: WorkStyle.SHIFT) in styles
    }

    private val PAID = setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY)
    private val OWN = setOf(WorkStyle.FIELD, WorkStyle.BUSINESS)

    val OPENED: List<Opened> = listOf(
        Opened("pay.yearEnd", "TAX_FORECAST", "연말정산 미리보기 전부", "카드·의료비·월세·연금저축까지 넣어 2월에 돌려받을 돈", PAID),
        Opened("pay.slipAudit", "PAYSLIP_MATCH", "명세서로 맞춰 보기", "받은 급여와 계산이 다른 줄과 그 까닭", PAID),
        Opened("money.takeHome", "TAX_FORECAST", "세전·세후 실수령", "이번 달 떼일 돈과 손에 들어올 돈", PAID),
        Opened("stats.compare", "WORK_COMPARE", "지난달·작년과 비교", "근무 시간·수당이 어떻게 달라졌는지", PAID),
        Opened("leave.payout", "LEAVE_PAYOUT", "미사용 연차수당", "남은 연차가 돈으로 얼마인지", PAID),
        Opened("money.bizTax", "TAX_FORECAST", "사업자 세금 한 해", "5월 종소세·부가세 어림과 세금 통장에 모을 돈", OWN),
        Opened("money.stats", "MONEY_STATS", "가계부 통계", "다음 카드값 이대로면 얼마, 근무별 소비, 달마다 비교"),
    )

    /** [PROX-01] What today's 하루 이용권 opened for [style], most useful first. */
    fun opened(style: WorkStyle?): List<Opened> = OPENED.filter { it.fits(style) }

    const val EVERY_DAY = "프로 구독이면 이것들이 매일 열려 있고, 연말정산은 10월부터 1월까지 매주 다시 계산해 알려 줘요."

    /** [PROX-02] The days money is at stake. [id] is kept with the year it was offered in. */
    enum class Moment(val id: String, val title: String, val words: String, val feature: String, val styles: Set<WorkStyle>) {
        YEAR_END("yearEnd", "연말정산 달이에요", "명세서로 맞춰 보기와 연말정산 미리보기를 오늘 하루 무료로 열어 볼까요?", "TAX_FORECAST", PAID),
        YEAR_END_PREVIEW("yearEndPreview", "연말정산 미리보기 철이에요", "카드·의료비·월세까지 넣어 2월에 돌려받을 돈을 오늘 하루 무료로 볼까요?", "TAX_FORECAST", PAID),
        MAY_TAX("mayTax", "5월 종합소득세 신고 달이에요", "올해 수입·경비와 낼 세금 어림을 오늘 하루 무료로 볼까요?", "TAX_FORECAST", OWN + WorkStyle.HOURLY),
        VAT("vat", "부가세 신고 달이에요", "매출 나눠 보기와 부가세 어림을 오늘 하루 무료로 볼까요?", "TAX_FORECAST", setOf(WorkStyle.BUSINESS)),
        PAYDAY("payday", "급여가 들어오는 날이에요", "명세서로 맞춰 보기를 오늘 하루 무료로 열어 계산과 다른 줄이 없는지 볼까요?", "PAYSLIP_MATCH", PAID),
        ROSTER("roster", "근무표를 넣었어요", "이번 달 수당과 실수령 예상을 오늘 하루 무료로 볼까요?", "TAX_FORECAST", setOf(WorkStyle.SHIFT)),
    }

    /** No two offers within this many days, whatever the moment. */
    const val GAP_DAYS = 14L

    /** The season key a moment is offered once in: the year for the yearly ones, the quarter for 급여일 and 근무표. */
    fun seasonOf(m: Moment, today: LocalDate): String = when (m) {
        Moment.PAYDAY, Moment.ROSTER -> "${m.id}:${today.year}Q${(today.monthValue - 1) / 3}"
        Moment.VAT -> "${m.id}:${today.year}H${if (today.monthValue <= 6) 1 else 2}"
        else -> "${m.id}:${today.year}"
    }

    /**
     * [PROX-02] The moment to offer today, or null: [payday] is within the 급여일's three days, [rosterToday] a 듀티표 went in
     * today, [offered] the seasons already offered and [lastOffer] the day of the last one.
     */
    fun due(today: LocalDate, style: WorkStyle?, payday: Boolean, rosterToday: Boolean, offered: Set<String>, lastOffer: LocalDate?): Moment? {
        if (lastOffer != null && ChronoUnit.DAYS.between(lastOffer, today) < GAP_DAYS) return null
        val s = style ?: WorkStyle.SHIFT
        val month = today.month
        val now = buildList {
            if (month == Month.JANUARY || month == Month.FEBRUARY) add(Moment.YEAR_END)
            if (month == Month.NOVEMBER || month == Month.DECEMBER) add(Moment.YEAR_END_PREVIEW)
            if (month == Month.MAY) add(Moment.MAY_TAX)
            if (month == Month.JANUARY || month == Month.JULY) add(Moment.VAT)
            if (payday) add(Moment.PAYDAY)
            if (rosterToday) add(Moment.ROSTER)
        }
        return now.firstOrNull { s in it.styles && seasonOf(it, today) !in offered }
    }
}
