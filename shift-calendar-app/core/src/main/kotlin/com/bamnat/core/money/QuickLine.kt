package com.bamnat.core.money

import java.time.LocalDate

/**
 * 1.37 [LEDH-28] 한 줄로 적기: "점심 9천 카드", "어제 택시 12,500", "+ 용돈 5만" read into the 돈 적기 sheet's fields. Only fills what it
 * is sure of; the sheet shows the rest as before. Words it does not use become the place.
 */
object QuickLine {
    data class Draft(
        val amount: Long?,
        val kind: MoneyKind,
        /** Days before today ("어제" 1, "그제" 2). */
        val daysAgo: Int,
        /** One of the categories given, when a word names it. */
        val category: String?,
        /** The account given whose name a word names, else null. */
        val accountId: Long?,
        /** "현금", "체크", "신용" said without a name: the sheet picks the kind of account. */
        val accountType: AccountType?,
        /** "카드" said without a name: a check or credit card, whichever is used most. */
        val anyCard: Boolean = false,
        val place: String,
    )

    private val INCOME = setOf("수입", "입금", "받음", "받았어", "용돈", "월급", "팁", "환급")
    private val DAYS = mapOf("오늘" to 0, "어제" to 1, "그제" to 2, "그저께" to 2)
    private val TYPES = mapOf("신용" to AccountType.CREDIT, "신용카드" to AccountType.CREDIT,
        "체크" to AccountType.CHECK, "체크카드" to AccountType.CHECK, "현금" to AccountType.CASH)

    fun parse(text: String, categories: List<String>, accounts: List<MoneyAccount> = emptyList()): Draft? {
        var rest = text.trim()
        if (rest.isEmpty()) return null
        var kind = MoneyKind.EXPENSE
        if (rest.startsWith("+") || rest.startsWith("＋")) { kind = MoneyKind.INCOME; rest = rest.drop(1).trim() }
        else if (rest.startsWith("-") || rest.startsWith("－")) rest = rest.drop(1).trim()
        // The amount: a unit (억·만·천·백·원) or a comma marks it; else the longest bare number of three digits or more.
        // 2.0.0 [RVK-02] (검토 MONEY-57) '2천만'·'1억'·'3백만'도 한 금액으로(예전에는 '2천'만 읽고 '만'은 쓴 곳 글로 남았어요).
        val all = KoreanAmount.find(rest)
        val m = all.firstOrNull { it.unit } ?: all.filter { it.text.trim().length >= 3 }.maxByOrNull { it.text.length }
        val amount = m?.value?.takeIf { it > 0 }
        if (m != null && amount != null) rest = (rest.substring(0, m.range.first) + " " + rest.substring(m.range.last + 1)).trim()
        val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var daysAgo = 0
        var category: String? = null
        var accountId: Long? = null
        var type: AccountType? = null
        var anyCard = false
        val left = mutableListOf<String>()
        for (w in words) {
            val bare = when { w.endsWith("으로") -> w.dropLast(2); w.endsWith("로") && w.length > 1 -> w.dropLast(1); else -> w }
            when {
                DAYS.containsKey(w) -> daysAgo = DAYS.getValue(w)
                // 25f2364b PC 확인 ('+ 용돈 3만5천'이 급여로 저장됨): an income word that is a category is that category too.
                w in INCOME -> { kind = MoneyKind.INCOME; if (category == null && categories.contains(w)) category = w; left += w }
                accountId == null && accounts.any { a -> !a.hidden && a.name.isNotBlank() && (a.name == w || a.name == bare) } ->
                    accountId = accounts.first { a -> !a.hidden && (a.name == w || a.name == bare) }.id
                type == null && TYPES.containsKey(bare) -> type = TYPES.getValue(bare)
                bare == "카드" -> anyCard = true
                category == null && categories.contains(w) -> { category = w; left += w }
                else -> left += w
            }
        }
        if (amount == null && left.isEmpty()) return null
        return Draft(amount, kind, daysAgo, category, accountId, type.takeIf { accountId == null },
            anyCard = anyCard && accountId == null && type == null, place = left.joinToString(" ").take(40))
    }

    /**
     * 2.0.0 [RVK-01] (PC-01: 한 줄로 적기 12개 가운데 7개가 다른 분류, 버스·택시·약국·통신비가 '카페') 쓴 곳 글에서 분류를 골라요.
     * 예전에는 분류 이름과 똑같은 낱말만 봤고, 못 찾으면 창에 남아 있던 분류(마지막에 쓴 '카페')가 그대로 저장됐어요.
     * 순서: 분류 이름 그대로 → 내 규칙(가맹점 → 분류) → 말 사전([WORDS], '점심'·'택시비'·'통신비') → 결제 알림의 가게 사전.
     * 고른 분류가 이 달력에 없으면 비슷한 분류([NEAR], 현장 '유류비' ↔ 집 '자동차')로, 그래도 없으면 null(창이 고르지 않음).
     */
    fun categoryFor(place: String, kind: MoneyKind, categories: List<String>, rules: Map<String, String> = emptyMap()): String? {
        val text = place.trim()
        if (text.isEmpty() || kind == MoneyKind.TRANSFER) return null
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        words.firstOrNull { it in categories && it != MoneyCategories.OTHER }?.let { return it }
        fun usable(c: String?): String? = c?.takeIf { it != MoneyCategories.OTHER }?.let { c0 ->
            if (c0 in categories) c0 else NEAR[c0]?.firstOrNull { it in categories && it != MoneyCategories.OTHER }
        }
        if (kind == MoneyKind.EXPENSE) usable(PaymentParser.ruleFor(text, rules))?.let { return it }
        val dict = if (kind == MoneyKind.INCOME) INCOME_WORDS else WORDS
        for (w in words) {
            dict.firstOrNull { (re, _) -> re.containsMatchIn(w) }?.let { (_, c) -> usable(c)?.let { return it } }
        }
        if (kind == MoneyKind.EXPENSE) usable(PaymentParser.guessCategory(text, rules))?.let { return it }
        return null
    }

    /** 한 줄로 적기에서 흔한 말 → 분류. 좁은 것이 먼저('동물병원'이 '병원'보다, '대출이자'가 '이자'보다). */
    private val WORDS: List<Pair<Regex, String>> = listOf(
        "동물병원|사료|애견|강아지|고양이|펫" to "반려동물",
        "기저귀|분유|어린이집|유치원|장난감|아기" to "육아",
        "축의|부조|조의|부의|결혼식|장례|돌잔치|화환|돌반지" to "경조사",
        "통신|폰요금|휴대폰|핸드폰|요금제|인터넷|알뜰폰" to "통신",
        "관리비|전기세|전기요금|가스비|가스요금|수도세|수도요금|난방비|공과금|과태료|지방세|재산세|자동차세" to "공과금",
        "월세|전세|집세|보증금|이사|부동산|복비" to "주거",
        "보험" to "보험",
        "넷플|유튜브|구독|멜론|티빙|쿠팡와우|디즈니|스포티파이|왓챠" to "구독",
        "대출이자|이자|수수료|연체료" to MoneyCategories.INTEREST,
        "약국|약값|^약$|병원|진료|치과|한의원|의원|안과|내과|피부과|정형외과|검진|영양제|감기약|파스|안경" to "의료",
        // 차는 현장 달력의 세 분류가 먼저이고, 집 가계부에서는 [NEAR]로 '자동차'.
        "톨비|통행료|하이패스|주차" to "톨비·주차",
        "주유|기름|휘발유|경유|충전소" to "유류비",
        "세차|정비|타이어|엔진오일|차량|카센터" to "차량유지",
        "버스|택시|지하철|전철|기차|KTX|ktx|SRT|srt|고속|비행기|항공|교통|따릉이|킥보드|티머니|카카오T" to "교통",
        "배달|배민|요기요|쿠팡이츠" to "배달",
        "편의점|씨유|^CU$|^cu$|GS25|gs25|세븐일레븐|이마트24|미니스톱" to "편의점",
        "커피|카페|아아|라떼|아메리카노|음료|디저트|케이크|빵|베이커리|스벅|스타벅스|메가커피|빽다방|공차|투썸|이디야" to "카페",
        "점심|저녁|아침|밥|식사|외식|회식|야식|식당|국밥|김밥|라면|분식|치킨|피자|햄버거|버거|고기|삼겹살|초밥|짜장|짬뽕|떡볶이|도시락|맥주|소주|술값|술집|호프|안주|간식|중식|한식|일식|양식|냉면|칼국수|순대" to "식비",
        "장보기|^장$|마트|이마트|홈플러스|코스트코|쿠팡|다이소|올리브영|생필품|쇼핑|컬리|시장|트레이더스" to "장보기·쇼핑",
        "^옷$|옷값|신발|바지|티셔츠|패딩|코트|미용실|머리|커트|^컷$|펌|염색|네일|화장품|무신사|유니클로" to "의류·미용",
        "학원|^책$|책값|교재|수업|강의|수강|등록금|과외|시험|응시료" to "교육",
        "영화|노래방|코노|피시방|PC방|pc방|게임|여행|숙소|호텔|펜션|헬스|운동|필라테스|요가|볼링|공연|콘서트|전시|놀이공원|캠핑|취미|골프" to "여가",
        "세탁|빨래|택배|수선|열쇠|생활용품|청소|휴지|세제|철물" to "생활",
        // 현장·가게 달력의 분류(그 달력에서만 고를 수 있어요. 집 가계부에서는 [NEAR]로 비슷한 것).
        "자재|시멘트|목재|합판|철근|타일|페인트|실리콘" to "자재비",
        "공구|장비|드릴" to "공구·장비",
        "작업복|안전화|안전모|작업장갑" to "작업복·안전",
        "재료|식자재|원두|야채|채소|정육" to "재료비",
        "포장|일회용|용기|소모품" to "포장·소모품",
        "임대료|가게세" to "임대료",
        "인건비|알바비|일당지급|급여지급" to "인건비",
        "광고|전단|마케팅" to "광고·마케팅",
    ).map { (re, c) -> Regex(re) to c }

    private val INCOME_WORDS: List<Pair<Regex, String>> = listOf(
        "월급|급여|봉급" to MoneyCategories.SALARY,
        "상여|보너스|성과급|인센티브" to "상여",
        "용돈|세뱃돈" to "용돈",
        "환급|연말정산|돌려받" to "환급",
        "이자|배당" to "이자",
        "중고|당근|팔았|판매|매각" to "판매",
        "카드매출" to "카드매출",
        "현금매출" to "현금매출",
        "배달매출|배민매출" to "배달앱매출",
        "일당" to "일당",
        "선금|계약금" to "선금",
        "잔금" to "잔금",
        "부업|알바|투잡|팁|부수입|리워드|캐시백|포인트" to "부수입",
    ).map { (re, c) -> Regex(re) to c }

    /** 고른 분류가 이 달력에 없을 때 대신 쓸 것(현장·가게 달력은 일 분류가 앞, 집 가계부는 집 분류만 있어요). */
    private val NEAR = mapOf(
        "자동차" to listOf("유류비", "차량유지", "톨비·주차"),
        "식비" to listOf("현장식대"),
        "유류비" to listOf("자동차"), "차량유지" to listOf("자동차"), "톨비·주차" to listOf("자동차"),
        "현장식대" to listOf("식비"),
        "자재비" to listOf("생활"), "공구·장비" to listOf("생활"), "작업복·안전" to listOf("의류·미용"),
        "재료비" to listOf("장보기·쇼핑"), "포장·소모품" to listOf("생활"), "임대료" to listOf("주거"),
        "인건비" to listOf("외주·인건비"),
        MoneyCategories.INTEREST to listOf("대출이자"),
        "일당" to listOf("부수입"), "선금" to listOf("부수입"), "잔금" to listOf("부수입"),
        "카드매출" to listOf("판매"), "현금매출" to listOf("판매"), "배달앱매출" to listOf("판매"),
        "상여" to listOf(MoneyCategories.SALARY),
    )

    /** The account a [Draft] means: the one named, else the most used open one of the kind it said. */
    fun account(d: Draft, accounts: List<MoneyAccount>, entries: List<MoneyEntry>): Long? {
        d.accountId?.let { return it }
        val types = d.accountType?.let { setOf(it) } ?: if (d.anyCard) setOf(AccountType.CREDIT, AccountType.CHECK) else return null
        return accounts.filter { !it.hidden && it.type in types }.maxByOrNull { a -> entries.count { it.accountId == a.id } }?.id
    }
}
