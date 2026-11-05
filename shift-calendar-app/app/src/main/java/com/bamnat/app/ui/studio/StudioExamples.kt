package com.bamnat.app.ui.studio

import com.bamnat.core.layout.TabOrder

/**
 * [CNV-09] 1.37 실제 화면처럼: what each card of 가계부 and 통계 shows on the phone, drawn on the studio's example with example numbers
 * (PC 확인 1.37 A2: 가계부 예시가 카드 이름만 있는 회색 상자). Never the phone's own money or hours: the example is also the 올리기
 * cover picture others see, so it stays the same made-up month for everyone.
 */
internal object StudioExamples {
    /** One card's look on the example: its lines (words · amount), and how full its bar is (null for none). */
    data class Body(val rows: List<Pair<String, String>>, val meter: Float? = null)

    fun body(page: String, id: String): Body? = when (page) {
        TabOrder.MONEY -> money[id]
        TabOrder.STATS -> stats[id]
        else -> null
    }

    private val money = mapOf(
        "hero" to Body(listOf("수입" to "3,120,000원", "지출" to "1,846,500원", "남은 돈" to "1,273,500원"), meter = 0.59f),
        "new" to Body(listOf("자동 기록 3건" to "확인하기 ›")),
        "ask" to Body(listOf("처음 보는 카드" to "1건", "원래 결제를 못 찾은 환불" to "12,000원")),
        "recent" to Body(listOf("편의점" to "-4,800원", "점심" to "-9,500원", "교통" to "-1,550원", "급여" to "+2,850,000원")),
        "income" to Body(listOf("급여 예상" to "2,850,000원", "적은 수입" to "270,000원")),
        "tax" to Body(listOf("세전" to "3,120,000원", "세후 예상" to "2,794,000원", "올해 낼 세금" to "약 310,000원")),
        "owed" to Body(listOf("빌려준 돈" to "50,000원", "안 받은 일감" to "1건")),
        "work" to Body(listOf("입금 맞출 일감" to "2건", "카드 매출 입금 예정" to "412,000원")),
        "all" to Body(listOf("내 달력" to "-1,846,500원", "가게" to "+640,000원")),
        "budget" to Body(listOf("식비" to "212,000 / 300,000원", "교통" to "48,000 / 80,000원"), meter = 0.7f),
        "other" to Body(listOf("자주 가는 곳" to "카페 ○○ → 간식")),
        "upcoming" to Body(listOf("통신비 · 25일" to "55,000원", "보험 · 28일" to "72,000원")),
        "repeat" to Body(listOf("매달 10일쯤" to "13,900원")),
        "accounts" to Body(listOf("급여 통장" to "1,920,000원", "체크카드" to "이번 달 386,000원")),
        "pace" to Body(listOf("이대로면 월말 지출" to "2,410,000원", "오늘 쓸 수 있는 돈" to "41,000원"), meter = 0.64f),
        "subs" to Body(listOf("구독 4개" to "월 38,600원", "1년치" to "463,200원")),
        "card25" to Body(listOf("총급여 25% 문턱" to "780만 원 중 612만 원"), meter = 0.78f),
        "cardbill" to Body(listOf("다음 카드값 · 15일" to "386,000원")),
        "install" to Body(listOf("이번 달 할부" to "120,000원", "남은 잔액" to "480,000원")),
        "shiftspend" to Body(listOf("야간 다음 날" to "하루 23,000원", "쉬는 날" to "하루 31,000원")),
        "salessplit" to Body(listOf("카드" to "62%", "현금" to "8%", "이체" to "12%", "배달앱" to "18%")),
        "freelance" to Body(listOf("뗀 3.3%" to "92,400원", "5월 신고" to "준비하기 ›")),
        "auto" to Body(listOf("결제 알림 읽기" to "켜기 ›")),
    )

    private val stats = mapOf(
        "tiles" to Body(listOf("총 근무" to "184h", "근무표대로" to "168h", "추가 근무" to "16h", "쉬는 날 근무" to "1일")),
        "pay" to Body(listOf("이번 달 예상 급여" to "3,120,000원", "기본급" to "2,410,000원", "수당" to "710,000원")),
        "kinds" to Body(listOf("잔업" to "4회 · 8h", "특근" to "1회 · 8h")),
        "three" to Body(listOf("3개월 주평균" to "43.5h", "52시간 기준" to "여유 8.5h"), meter = 0.84f),
        "weeks" to Body(listOf("이번 주" to "44h")),
        "trend" to Body(listOf("6개월 평균" to "178h")),
        "compare" to Body(listOf("지난달보다" to "+12h", "작년 같은 달보다" to "-4h")),
        "year" to Body(listOf("올해 근무" to "1,842h", "올해 급여" to "31,240,000원")),
    )
}
