package com.bamnat.core.work

import kotlin.math.min

/**
 * [FIT-03] One kind of job as people say it, with the way of working it usually means: [style], and for
 * shift work whether the roster comes as a new sheet every month ([roster], 간호사) rather than a repeating
 * order (4조 2교대). [aliases] are other words for it (별칭, 줄임말, 영어). 1.25 묶음 Y (U23).
 */
data class Job(val name: String, val style: WorkStyle, val roster: Boolean = false, val aliases: List<String> = emptyList()) {
    val words: List<String> get() = listOf(name) + aliases
}

/** [FIT-03] A job the words matched, and how sure: [EXACT] the same word, then in it, by 초성, by a typo. */
data class JobMatch(val job: Job, val how: How, val score: Int) {
    enum class How { EXACT, CONTAINS, INITIALS, TYPO }
}

/**
 * [FIT-03] "어떤 일을 하고 계신가요?": the words the user typed matched to a kind of work, on the phone only
 * (D68; nothing is sent or kept). Jobs and their usual way of working follow the 1.21 직군 조사
 * (research/1.21-직군-조사.md) and the big groups of 한국고용직업분류(KECO); a job missing here falls back to
 * the kind-of-work cards, so a wrong guess costs one tap. Typos are measured on 자모 (Unicode 3.12 Hangul
 * syllable decomposition), and 초성 alone (ㄱㅎㅅ) is matched too.
 */
object JobDictionary {
    private val SHIFT = WorkStyle.SHIFT
    private val DAY = WorkStyle.DAY
    private val HOURLY = WorkStyle.HOURLY
    private val FIELD = WorkStyle.FIELD
    private val SHOP = WorkStyle.BUSINESS
    private val INVEST = WorkStyle.INVEST

    private fun j(name: String, style: WorkStyle, vararg aliases: String) = Job(name, style, false, aliases.toList())
    private fun r(name: String, vararg aliases: String) = Job(name, SHIFT, true, aliases.toList())

    val jobs: List<Job> = listOf(
        // 보건·의료: rosters that come every month.
        r("간호사", "간호", "널스", "nurse", "rn", "병동간호사", "수간호사", "응급실간호사", "icu"),
        r("간호조무사", "조무사", "간조"),
        r("요양보호사", "요양사", "요양원", "요양병원"),
        r("응급구조사", "구급대원", "응급구조"),
        r("방사선사", "방사선"),
        r("임상병리사", "병리사"),
        r("전공의", "레지던트", "인턴의사"),
        r("병원 직원", "병원", "병원근무"),
        r("호텔리어", "호텔", "프런트", "호텔프론트"),
        r("항공 승무원", "승무원", "스튜어디스", "객실승무원"),
        r("콜센터 상담원", "콜센터", "상담원", "고객센터"),
        r("마트 직원", "마트", "대형마트", "이마트", "홈플러스"),
        r("백화점 판매원", "백화점", "매장직원", "판매직"),
        r("약국 근무약사", "근무약사", "병원약사"),
        // 순서가 돌아가는 교대.
        j("경찰관", SHIFT, "경찰", "순경", "지구대", "파출소", "경찰공무원"),
        j("소방관", SHIFT, "소방", "소방공무원", "119"),
        j("교도관", SHIFT, "교정직", "교도소"),
        j("해양경찰", SHIFT, "해경"),
        j("생산직", SHIFT, "생산", "공장", "공장근무", "제조", "현장직", "오퍼레이터"),
        j("반도체 생산", SHIFT, "반도체", "팹", "fab", "클린룸"),
        j("자동차 공장", SHIFT, "자동차", "완성차", "부품공장"),
        j("제철소", SHIFT, "제철", "철강", "포스코"),
        j("화학 공장", SHIFT, "화학", "석유화학", "정유", "정유사"),
        j("발전소", SHIFT, "발전", "원자력", "원전", "화력발전"),
        j("경비원", SHIFT, "경비", "아파트경비", "경비직"),
        j("보안요원", SHIFT, "보안", "시큐리티", "보안직"),
        j("청원경찰", SHIFT, "청경"),
        j("기관사", SHIFT, "지하철", "철도", "코레일", "열차"),
        j("버스 기사", SHIFT, "버스", "버스기사", "시내버스", "운수"),
        j("택시 기사", SHIFT, "택시", "법인택시"),
        j("관제사", SHIFT, "관제", "항공관제", "상황실"),
        j("물류센터", SHIFT, "물류", "센터", "풀필먼트"),
        j("교대 근무", SHIFT, "교대", "3교대", "2교대", "4조2교대", "3조2교대", "맞교대", "24시간", "격일제", "주야", "주야비휴"),
        j("당직 근무", SHIFT, "당직", "숙직"),
        j("방송국 기술", SHIFT, "방송", "송출", "방송기술"),
        j("수자원·가스 공기업", SHIFT, "가스공사", "수자원", "한전", "전력"),
        // 평일 근무.
        j("사무직", DAY, "사무", "회사원", "직장인", "오피스", "회사", "사원", "대리", "과장", "경리", "총무", "인사"),
        j("공무원", DAY, "행정직", "주무관", "구청", "시청", "주민센터", "공무직"),
        j("교사", DAY, "선생님", "교직원", "초등교사", "중학교교사", "고등학교교사", "교원"),
        j("어린이집 교사", DAY, "보육교사", "어린이집", "유치원교사", "유치원"),
        j("은행원", DAY, "은행", "금융권", "증권사직원"),
        j("연구원", DAY, "연구", "연구소"),
        j("개발자", DAY, "프로그래머", "개발", "코딩", "엔지니어", "it"),
        j("회사 디자이너", DAY, "인하우스디자이너"),
        j("영업직", DAY, "영업", "세일즈"),
        j("마케터", DAY, "마케팅", "홍보"),
        j("군인", DAY, "직업군인", "부사관", "장교", "군무원"),
        j("사회복지사", DAY, "복지사", "복지관"),
        j("물리치료사", DAY, "물리치료", "도수치료"),
        j("치과위생사", DAY, "치위생사", "치과"),
        j("공장 주간", DAY, "주간근무", "상주간"),
        j("비서", DAY),
        j("회계 사무", DAY, "회계", "세무사무실", "경리직"),
        j("학교 행정실", DAY, "행정실", "교육행정"),
        // 시급·아르바이트.
        j("아르바이트", HOURLY, "알바", "알바생", "파트타임", "파트타이머", "시급", "시간제", "단기알바", "주말알바"),
        j("편의점 알바", HOURLY, "편의점", "cu", "gs25", "세븐일레븐", "이마트24"),
        j("카페 알바", HOURLY, "카페", "바리스타", "스타벅스", "커피"),
        j("음식점 서빙", HOURLY, "서빙", "홀서빙", "식당알바", "홀"),
        j("주방 보조", HOURLY, "주방", "설거지", "주방알바"),
        j("패스트푸드", HOURLY, "맥도날드", "롯데리아", "버거킹", "패스트푸드점"),
        j("영화관 알바", HOURLY, "영화관", "cgv", "메가박스"),
        j("PC방 알바", HOURLY, "pc방", "피시방"),
        j("학원 조교", HOURLY, "조교", "학원알바"),
        j("상하차", HOURLY, "택배상하차", "물류알바", "쿠팡알바", "허브"),
        j("주유소 알바", HOURLY, "주유소"),
        j("마트 캐셔", HOURLY, "캐셔", "계산원"),
        j("베이커리 알바", HOURLY, "빵집알바", "베이커리"),
        j("놀이공원 알바", HOURLY, "놀이공원", "에버랜드", "롯데월드"),
        j("행사 스태프", HOURLY, "행사알바", "스태프", "이벤트알바"),
        j("독서실 총무", HOURLY, "독서실", "스터디카페"),
        // 현장·프리랜서.
        j("프리랜서", FIELD, "프리", "외주", "자유직"),
        j("인테리어", FIELD, "인테리어시공", "리모델링"),
        j("목수", FIELD, "형틀목공", "목공", "내장목수"),
        j("도배", FIELD, "도배사", "도배장판"),
        j("타일", FIELD, "타일공", "타일시공"),
        j("전기 기사", FIELD, "전기", "전기공사", "전기기능사"),
        j("설비", FIELD, "배관", "설비기사", "보일러"),
        j("용접", FIELD, "용접공", "용접사"),
        j("건설 일용직", FIELD, "일용직", "노가다", "막노동", "건설현장", "공사현장", "인력사무소", "보통인부", "잡부"),
        j("철근", FIELD, "철근공"),
        j("미장", FIELD, "미장공"),
        j("페인트", FIELD, "도장", "도장공"),
        j("강사", FIELD, "강의", "출강", "학원강사"),
        j("과외", FIELD, "과외선생", "튜터", "개인과외"),
        j("레슨", FIELD, "피아노레슨", "음악레슨", "보컬레슨"),
        j("트레이너", FIELD, "pt", "피티", "헬스트레이너", "필라테스강사", "요가강사"),
        j("사진작가", FIELD, "촬영", "사진", "포토그래퍼", "스냅"),
        j("영상 편집", FIELD, "영상", "유튜브편집", "편집자", "pd"),
        j("프리랜서 디자이너", FIELD, "디자이너", "디자인", "일러스트", "일러스트레이터"),
        j("번역가", FIELD, "번역", "통역", "통번역"),
        j("웨딩플래너", FIELD, "웨딩"),
        j("대리운전", FIELD, "대리기사"),
        j("택배 기사", FIELD, "택배", "택배배송", "쿠팡플렉스", "플렉스"),
        j("배달 라이더", FIELD, "배달", "라이더", "배민", "쿠팡이츠", "배달대행"),
        j("퀵서비스", FIELD, "퀵"),
        j("화물 기사", FIELD, "화물", "트럭", "화물차", "지입"),
        j("이사", FIELD, "이삿짐", "이사업체"),
        j("청소", FIELD, "청소대행", "입주청소", "가사도우미", "가사"),
        j("베이비시터", FIELD, "시터", "아이돌보미", "돌봄"),
        j("방송인·MC", FIELD, "mc", "사회자", "행사mc", "모델"),
        j("작가", FIELD, "웹툰", "웹소설", "글쓰기"),
        // 자영업.
        j("자영업", SHOP, "사장", "사장님", "자영업자", "개인사업자", "가게", "매장운영", "창업"),
        j("식당 운영", SHOP, "식당", "음식점", "식당사장", "한식당"),
        j("카페 운영", SHOP, "카페사장", "카페창업"),
        j("미용실", SHOP, "미용", "헤어샵", "미용사", "헤어디자이너"),
        j("네일샵", SHOP, "네일", "네일아트"),
        j("편의점 점주", SHOP, "점주", "편의점사장"),
        j("온라인 쇼핑몰", SHOP, "쇼핑몰", "스마트스토어", "온라인판매", "쿠팡셀러", "셀러"),
        j("꽃집", SHOP, "플라워샵", "화원"),
        j("학원 운영", SHOP, "학원원장", "원장", "공부방"),
        j("헬스장 운영", SHOP, "헬스장", "체육관", "필라테스샵"),
        j("치킨집", SHOP, "치킨", "프랜차이즈"),
        j("술집", SHOP, "호프", "포차", "와인바"),
        j("빵집", SHOP, "제과점", "베이커리사장"),
        j("무인점포", SHOP, "무인", "무인아이스크림", "무인카페"),
        j("세탁소", SHOP, "빨래방", "코인세탁"),
        j("공방", SHOP, "핸드메이드", "공예"),
        j("푸드트럭", SHOP, "노점", "포장마차"),
        j("숙박업", SHOP, "펜션", "게스트하우스", "모텔"),
        j("약국 운영", SHOP, "약국", "개국약사"),
        // 투자.
        j("주식 투자", INVEST, "주식", "투자", "주린이", "개미", "국내주식"),
        j("해외 주식", INVEST, "미국주식", "미주", "나스닥", "해외주식투자"),
        j("코인", INVEST, "비트코인", "가상화폐", "암호화폐", "코인투자", "업비트"),
        j("전업 투자자", INVEST, "전업투자", "트레이더", "데이트레이딩", "단타", "스윙"),
        j("ETF 투자", INVEST, "etf", "적립식", "연금저축", "isa"),
        j("배당 투자", INVEST, "배당", "배당주"),
        j("재테크", INVEST, "자산관리", "투자기록"),
    )

    /** The chips under the question: what most people type. */
    val common: List<String> = listOf("간호사", "생산직", "경찰관", "소방관", "사무직", "편의점 알바", "카페 알바", "택배 기사", "식당 운영", "주식 투자")

    fun byName(name: String): Job? = jobs.firstOrNull { it.name == name }

    /**
     * The jobs [text] may mean, best first (at most [limit]). Words like "이에요", "하고 있어요", "에서 일해요" and
     * spaces do not count. Nothing for fewer than two letters.
     */
    fun suggest(text: String, limit: Int = 3): List<JobMatch> {
        val q = normalize(text)
        if (q.length < 2) return emptyList()
        val best = HashMap<Job, JobMatch>()
        fun offer(m: JobMatch) {
            val now = best[m.job]
            if (now == null || m.score > now.score) best[m.job] = m
        }
        val initialsOnly = q.all(::isInitial)
        val qJamo = jamo(q)
        for (job in jobs) for (word in job.words) {
            val w = normalize(word)
            if (w.isEmpty()) continue
            when {
                q == w -> offer(JobMatch(job, JobMatch.How.EXACT, 100 + w.length))
                w.length >= 2 && q.contains(w) -> offer(JobMatch(job, JobMatch.How.CONTAINS, 80 + w.length))
                q.length >= 2 && w.contains(q) -> offer(JobMatch(job, JobMatch.How.CONTAINS, 70 + q.length))
                initialsOnly && initials(w).startsWith(q) -> offer(JobMatch(job, JobMatch.How.INITIALS, 60 + q.length))
                !initialsOnly -> {
                    val wJamo = jamo(w)
                    val allowed = if (w.length <= 3) 1 else 2
                    if (kotlin.math.abs(wJamo.length - qJamo.length) <= allowed) {
                        val d = distance(qJamo, wJamo, allowed)
                        if (d in 1..allowed) offer(JobMatch(job, JobMatch.How.TYPO, 50 - d * 5 + min(w.length, 6)))
                    }
                }
            }
        }
        return best.values.sortedWith(compareByDescending<JobMatch> { it.score }.thenBy { jobs.indexOf(it.job) }).take(limit)
    }

    /** The one job to offer, when the best match is clearly the one. */
    fun best(text: String): JobMatch? = suggest(text, 1).firstOrNull()

    private val TAILS = listOf("하고있어요", "하고있습니다", "에서일해요", "에서일합니다", "에다녀요", "다녀요", "이에요", "예요", "입니다", "이요", "해요",
        "합니다", "일해요", "근무해요", "근무", "하는사람", "이고", "임")

    internal fun normalize(text: String): String {
        var s = text.lowercase().filter { it.isLetterOrDigit() }
        // A sentence keeps only its job: "저는 간호사예요" → "간호사".
        s = s.removePrefix("저는").removePrefix("나는")
        var changed = true
        while (changed) {
            changed = false
            for (t in TAILS) if (s.length > t.length + 1 && s.endsWith(t)) { s = s.dropLast(t.length); changed = true }
        }
        return s
    }

    private const val SYLLABLE_FIRST = 0xAC00
    private const val SYLLABLE_LAST = 0xD7A3
    private val LEADS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val TAILS_JAMO = listOf("", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ",
        "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

    private fun isInitial(c: Char): Boolean = c in LEADS

    /** 간호사 → ㄱㅎㅅ; letters that are not Hangul syllables stay as they are. */
    internal fun initials(s: String): String = s.map { c ->
        if (c.code in SYLLABLE_FIRST..SYLLABLE_LAST) LEADS[(c.code - SYLLABLE_FIRST) / 588] else c
    }.joinToString("")

    /** 간 → ㄱㅏㄴ: SIndex = S − 0xAC00, L = SIndex / 588, V = (SIndex % 588) / 28, T = SIndex % 28 (Unicode 3.12). */
    internal fun jamo(s: String): String = buildString {
        for (c in s) {
            if (c.code in SYLLABLE_FIRST..SYLLABLE_LAST) {
                val i = c.code - SYLLABLE_FIRST
                append(LEADS[i / 588]); append(VOWELS[(i % 588) / 28]); append(TAILS_JAMO[i % 28])
            } else append(c)
        }
    }

    /** Levenshtein distance, stopping early above [cap]. */
    internal fun distance(a: String, b: String, cap: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (k in 1..b.length) {
                cur[k] = minOf(prev[k] + 1, cur[k - 1] + 1, prev[k - 1] + if (a[i - 1] == b[k - 1]) 0 else 1)
                rowMin = min(rowMin, cur[k])
            }
            if (rowMin > cap) return cap + 1
            prev = cur
        }
        return prev[b.length]
    }
}
