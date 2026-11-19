package com.bamnat.core.money

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVK-01][RVK-02] (PC-01, 도약 숫자 21번) 한 줄로 적기 예문 장부: 사람들이 실제로 치는 모양의 100줄에서 분류를 90줄 넘게
 * 맞히고 금액은 모두 맞혀요. 9차 PC에서는 12줄 가운데 5줄만 맞았어요(버스·택시·약국·통신비가 '카페'). 예문은 지어낸 것이에요.
 */
class QuickLineCorpusTest {
    private val expense = MoneyCategories.of(MoneyKind.EXPENSE)
    private val income = MoneyCategories.of(MoneyKind.INCOME)

    /** 한 줄 → (금액, 분류). */
    private val corpus: List<Triple<String, Long, String>> = listOf(
        // 9차 PC 확인에서 틀린 모양들
        Triple("버스 1,500", 1_500, "교통"), Triple("택시 12,500", 12_500, "교통"), Triple("약국 8천", 8_000, "의료"),
        Triple("통신비 55,000", 55_000, "통신"), Triple("지하철 1400", 1_400, "교통"), Triple("점심 9천 카드", 9_000, "식비"),
        Triple("커피 4500", 4_500, "카페"), Triple("편의점 3,200", 3_200, "편의점"), Triple("관리비 18만", 180_000, "공과금"),
        Triple("월세 50만", 500_000, "주거"), Triple("주유 6만", 60_000, "자동차"), Triple("넷플릭스 13,500", 13_500, "구독"),
        // 먹는 것
        Triple("저녁 회식 4만", 40_000, "식비"), Triple("어제 김밥 3500", 3_500, "식비"), Triple("야식 치킨 2만3천", 23_000, "식비"),
        Triple("국밥 9,000", 9_000, "식비"), Triple("아침 토스트 4천", 4_000, "식비"),         Triple("떡볶이 6천", 6_000, "식비"), Triple("맥주 소주 2만", 20_000, "식비"), Triple("삼겹살 5.5만", 55_000, "식비"),
        Triple("도시락 5,800", 5_800, "식비"), Triple("짜장면 7천 현금", 7_000, "식비"), Triple("밥값 1만2천", 12_000, "식비"),
        Triple("피자 25000", 25_000, "식비"), Triple("햄버거 8,900", 8_900, "식비"),
        // 카페
        Triple("아아 2천", 2_000, "카페"), Triple("스벅 라떼 6100", 6_100, "카페"), Triple("케이크 3만2천", 32_000, "카페"),
        Triple("빵 7천", 7_000, "카페"), Triple("메가커피 1500", 1_500, "카페"),         // 배달·편의점·장보기
        Triple("배민 2만1천", 21_000, "배달"), Triple("요기요 18,000", 18_000, "배달"), Triple("CU 4,300", 4_300, "편의점"),
        Triple("마트 장보기 6만7천", 67_000, "장보기·쇼핑"), Triple("다이소 5천", 5_000, "장보기·쇼핑"),
        Triple("쿠팡 3만9천", 39_000, "장보기·쇼핑"), Triple("코스트코 15만", 150_000, "장보기·쇼핑"), Triple("올리브영 2만", 20_000, "장보기·쇼핑"),
        Triple("시장 1만", 10_000, "장보기·쇼핑"),
        // 교통·자동차
        Triple("KTX 5만9800", 59_800, "교통"), Triple("고속버스 2만3천", 23_000, "교통"), Triple("비행기표 12만", 120_000, "교통"),
        Triple("택시비 만원", 10_000, "교통"), Triple("교통카드 충전 3만", 30_000, "교통"), Triple("주차 3천", 3_000, "자동차"),
        Triple("톨비 4,600", 4_600, "자동차"), Triple("세차 1만", 10_000, "자동차"), Triple("기름 7만 카드", 70_000, "자동차"),
        Triple("엔진오일 교체 9만", 90_000, "자동차"),
        // 몸·집·생활
        Triple("병원 진료 1만2천", 12_000, "의료"), Triple("치과 4만5천", 45_000, "의료"), Triple("감기약 6천", 6_000, "의료"),
        Triple("한의원 1만", 10_000, "의료"), Triple("안경 13만", 130_000, "의료"), Triple("핸드폰 요금 6만9천", 69_000, "통신"),
        Triple("인터넷 3만3천", 33_000, "통신"), Triple("전기세 4만1천", 41_000, "공과금"), Triple("가스비 3만7천", 37_000, "공과금"),
        Triple("수도요금 1만5천", 15_000, "공과금"), Triple("이사 80만", 800_000, "주거"), Triple("세탁 9천", 9_000, "생활"),
        Triple("택배 4천", 4_000, "생활"), Triple("휴지 세제 2만", 20_000, "생활"),
        // 사람·배움·놀이
        Triple("축의금 10만", 100_000, "경조사"), Triple("조의금 5만", 50_000, "경조사"), Triple("학원비 25만", 250_000, "교육"),
        Triple("책 1만8천", 18_000, "교육"), Triple("강의 수강료 9만9천", 99_000, "교육"), Triple("영화 1만5천", 15_000, "여가"),
        Triple("노래방 2만", 20_000, "여가"), Triple("PC방 5천", 5_000, "여가"), Triple("헬스 6만", 60_000, "여가"),
        Triple("여행 숙소 18만", 180_000, "여가"), Triple("콘서트 15만4천", 154_000, "여가"),         Triple("유튜브 프리미엄 14,900", 14_900, "구독"), Triple("구독료 9900", 9_900, "구독"),
        // 옷·미용·보험·반려·아이
        Triple("미용실 커트 1만5천", 15_000, "의류·미용"), Triple("옷 5만9천", 59_000, "의류·미용"), Triple("신발 8만9천", 89_000, "의류·미용"),
        Triple("네일 4만", 40_000, "의류·미용"), Triple("화장품 3만2천", 32_000, "의류·미용"), Triple("보험료 12만", 120_000, "보험"),
        Triple("사료 3만", 30_000, "반려동물"), Triple("동물병원 7만", 70_000, "반려동물"), Triple("기저귀 3만5천", 35_000, "육아"),
        Triple("분유 2만8천", 28_000, "육아"), Triple("어린이집 15만", 150_000, "육아"),
        // 큰 금액(MONEY-57)
        Triple("보증금 2천만", 20_000_000, "주거"), Triple("전세 1억 2천만", 120_000_000, "주거"), Triple("대출이자 3백만", 3_000_000, MoneyCategories.INTEREST),
        Triple("수수료 1,500", 1_500, MoneyCategories.INTEREST),
        // 들어온 돈
        Triple("+ 용돈 3만5천", 35_000, "용돈"), Triple("+ 월급 285만", 2_850_000, MoneyCategories.SALARY), Triple("+ 보너스 50만", 500_000, "상여"),
        Triple("+ 연말정산 환급 32만", 320_000, "환급"), Triple("+ 중고 판매 4만", 40_000, "판매"), Triple("+ 예금 이자 1만2천", 12_000, "이자"),
        Triple("+ 알바비 15만", 150_000, "부수입"), Triple("+ 팁 2만", 20_000, "부수입"), Triple("+ 세뱃돈 10만", 100_000, "용돈"),
            )

    @Test fun ninetyOfAHundredGoWhereTheyBelong() {
        assertEquals(100, corpus.size)
        val wrong = corpus.filter { (line, _, want) ->
            val d = QuickLine.parse(line, expense + income)!!
            val list = if (d.kind == MoneyKind.INCOME) income else expense
            val got = d.category ?: QuickLine.categoryFor(d.place, d.kind, list)
            got != want
        }.map { (line, _, want) -> "$line → $want" }
        assertTrue("분류가 틀린 줄 ${wrong.size}개: $wrong", wrong.size <= 10)
    }

    @Test fun everyAmountIsReadAsSaid() {
        val wrong = corpus.filter { (line, amount, _) -> QuickLine.parse(line, expense + income)!!.amount != amount }
        assertEquals(emptyList<Triple<String, Long, String>>(), wrong)
    }

    @Test fun aFieldCalendarGetsItsOwnCategories() {
        val field = MoneyCategories.of(MoneyKind.EXPENSE, WorkStyle.FIELD)
        assertEquals("자동차", QuickLine.categoryFor("기름", MoneyKind.EXPENSE, expense))
        assertEquals("자재비", QuickLine.categoryFor("합판 자재", MoneyKind.EXPENSE, field))
        assertEquals("톨비·주차", QuickLine.categoryFor("톨비", MoneyKind.EXPENSE, field.filter { it != "자동차" }))
        // 집 가계부에 '자재비'가 없으면 비슷한 '생활'로, 아무것도 모르면 고르지 않아요(창의 분류를 그대로 둠).
        assertEquals("생활", QuickLine.categoryFor("시멘트", MoneyKind.EXPENSE, expense))
        assertEquals(null, QuickLine.categoryFor("홍길동", MoneyKind.EXPENSE, expense))
    }

    @Test fun theUsersOwnRuleWins() {
        assertEquals("교육", QuickLine.categoryFor("스타벅스", MoneyKind.EXPENSE, expense, mapOf("스타벅스" to "교육")))
    }

    @Test fun koreanAmountsReadAsSpoken() {
        assertEquals(20_000_000L, KoreanAmount.valueOf("2천만"))
        assertEquals(120_000_000L, KoreanAmount.valueOf("1억 2천만"))
        assertEquals(3_000_000L, KoreanAmount.valueOf("3백만"))
        assertEquals(15_000L, KoreanAmount.valueOf("15천원"))
        assertEquals(35_000L, KoreanAmount.valueOf("3만5천"))
        assertEquals(35_200L, KoreanAmount.valueOf("3만 5천 200"))
        assertEquals(15_000_000L, KoreanAmount.valueOf("1,500만"))
        assertEquals(15_000L, KoreanAmount.valueOf("1.5만"))
        assertEquals(12_500L, KoreanAmount.valueOf("12,500원"))
        assertEquals(listOf(20_000_000L), MemoAmounts.find("보증금 2천만원 냈다").map { it.amount })
        assertEquals(listOf(15_000L), MemoAmounts.find("점심 15천원 씀").map { it.amount })
        // 원 없이 천만 있는 말은 돈이 아니에요(예전과 같아요).
        assertEquals(emptyList<Long>(), MemoAmounts.find("오늘 3천 걸음 씀").map { it.amount })
    }
}
