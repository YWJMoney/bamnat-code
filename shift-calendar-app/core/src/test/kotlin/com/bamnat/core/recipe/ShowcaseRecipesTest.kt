package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.WorkStyle.BUSINESS
import com.bamnat.core.work.WorkStyle.DAY
import com.bamnat.core.work.WorkStyle.FIELD
import com.bamnat.core.work.WorkStyle.HOURLY
import com.bamnat.core.work.WorkStyle.INVEST
import com.bamnat.core.work.WorkStyle.SHIFT
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RCX-32] 1.35 보여주기 판 (사용자 2026-10-30 "나눔판 기능을 활용해 만들수 있는 최상의 하이엔드 커스텀을 각 유형별로 5개씩"): five for
 * each kind of work, each putting many pieces together (꾸미기·테마·알람·메모 틀·규칙·화면 배치·카드·위젯·모서리) so the 광장 shows how far
 * a 판 can go. Every one passes the reader and 올리기 전 검사 with no warning at all. With BAMNAT_WRITE_STARTERS=1 this writes
 * `docs/recipes/showcase-recipes.json`, which the operator pastes into 관리자 › 광장 › 나눔 판 › 보여주기.
 */
class ShowcaseRecipesTest {
    private fun look(vararg items: Map<String, Any?>) = Piece(PieceKind.SHIFT_LOOK, mapOf("items" to items.toList()))
    private fun day(name: String, role: String, label: String, color: Any, emoji: String? = null) =
        mapOf("name" to name, "role" to role, "label" to label, "color" to color, "emoji" to emoji)
    private fun theme(sat: Any? = null, sun: Any? = null, holiday: Any? = null, today: Any? = null, cell: String? = null) =
        Piece(PieceKind.CAL_THEME, mapOf("sat" to sat, "sun" to sun, "holiday" to holiday, "today" to today, "cell" to cell).filterValues { it != null })
    private fun alarms(vararg byRole: Pair<String, List<Long>>, snooze: Long? = null, ring: Long? = null, mission: String? = null,
        gradual: Boolean? = null, vibrate: Boolean? = null) =
        Piece(PieceKind.ALARMS, mapOf("byRole" to byRole.map { mapOf("role" to it.first, "before" to it.second) }, "snooze" to snooze,
            "ring" to ring, "mission" to mission, "gradual" to gradual, "vibrate" to vibrate).filterValues { it != null })
    private fun memo(vararg items: Map<String, Any?>) = Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to items.toList()))
    private fun tpl(title: String, emoji: String?, check: List<String> = emptyList(), body: String? = null, repeat: String? = null) =
        mapOf("title" to title, "emoji" to emoji, "check" to check, "body" to body, "repeat" to repeat).filterValues { it != null }
    private fun layout(tabs: List<String>? = null, calendar: String? = null) =
        Piece(PieceKind.LAYOUT, mapOf("tabs" to tabs, "calendar" to calendar).filterValues { it != null })
    private fun cards(stats: String? = null, money: String? = null, memo: String? = null) =
        Piece(PieceKind.CARDS, mapOf("stats" to stats, "money" to money, "memo" to memo).filterValues { it != null })
    private fun widget(alpha: Long? = null, theme: String? = null, memo: Boolean? = null) =
        Piece(PieceKind.WIDGET, mapOf("alpha" to alpha, "theme" to theme, "memo" to memo).filterValues { it != null })
    private fun corners(c: String) = Piece(PieceKind.PHOTO_LOOK, mapOf("corners" to c))
    private fun tok(name: String) = Ref.Token(name)
    private fun recipe(title: String, summary: String, desc: String, styles: Set<WorkStyle>, a: Long, b: Long, emoji: String, vararg pieces: Piece,
        inputs: List<Input> = emptyList(), tokens: List<Token> = emptyList(), dir: Int = 2) =
        Recipe(title, summary, desc, styles, Cover(a, b, dir, emoji), tokens = tokens, inputs = inputs, pieces = pieces.toList())

    private val wage = Input("i1", "시급", InputKind.MONEY, default = 10_320L, min = 0.0, max = 1_000_000.0, hint = "처음 값은 올해 최저임금이에요. 내 시급으로 바꿔 넣어요")
    private val dayRate = Input("i1", "일당", InputKind.MONEY, min = 0.0, max = 5_000_000.0, required = true, hint = "내 일당을 적어 주세요")
    private val accent = Input("i2", "강조색", InputKind.COLOR, default = 0xFF3949ABL, hint = "오늘 표시와 근무 색에 쓸 내 색을 골라요")

    private val all6 = listOf("calendar", "money", "memos", "stats", "plaza", "settings")

    private val shift = listOf(
        recipe("3교대 간호 풀세트", "데이·이브·나이트 색, 두 번 알람, SBAR 인계 틀까지",
            "누구에게 좋아요: 병동 3교대 간호사\n적용하면 바뀌는 것:\n· D·E·N·Off 글자·색·이모지\n· 근무별 알람 두 번 (나이트는 낮잠 깨우기 포함)\n· SBAR 인수인계·근무 준비물 메모 틀\n· 달력 위 오늘의 흐름, 아래 휴식 점검\n· 통계는 추가 근무·3개월 평균 먼저\n· 위젯에 메모 한 줄, 둥근 모서리\n주의할 점: 알람은 더하기만 해요. 근무 이름이 달라도 역할로 맞춰요",
            setOf(SHIFT), 0xFF00897B, 0xFF1A237E, "🩺",
            look(day("데이", "day", "D", 0xFFE65100, "☀️"), day("이브닝", "eve", "E", 0xFF6A1B9A, "🌆"), day("나이트", "night", "N", 0xFF1A237E, "🌙"), day("오프", "off", "O", 0xFF546E7A, "🛌")),
            alarms("day" to listOf(90L, 60L), "eve" to listOf(90L, 45L), "night" to listOf(240L, 60L), snooze = 5L, gradual = true, vibrate = true),
            memo(tpl("SBAR 인수인계", "🩺", listOf("S 상황: 지금 상태", "B 배경: 진단·경과", "A 평가: 활력징후·변화", "R 요청: 다음 근무에 할 일", "투약·처치 시각 확인")),
                tpl("근무 준비물", "🎒", listOf("사원증", "펜·가위", "간식·물", "충전기", "여벌 양말"))),
            theme(today = 0xFF00897B, sun = 0xFFC62828, holiday = 0xFFAD1457, cell = "tint"),
            layout(calendar = "todayFlow,counts,summary,dayCard,rest,hours,memos"),
            cards(stats = "tiles,pay,kinds,three,weeks,compare"),
            widget(alpha = 90, memo = true), corners("round")),
        recipe("야간 생존 키트", "밤 근무가 많은 분을 위한 어두운 판과 낮잠 알람",
            "누구에게 좋아요: 야간 비중이 큰 교대 근무자\n적용하면 바뀌는 것:\n· 주간·야간·비번·휴무 색과 이모지 (어두운 화면에서도 또렷하게)\n· 야간 4시간 전 낮잠 깨우기, 1시간 전 출근 알람, 끄려면 계산 문제\n· 야간 뒤 회복 루틴 메모 틀\n· 가계부는 근무별 소비를 앞으로\n· 위젯은 어둡게 반쯤 비치게\n주의할 점: 미션은 지금 미션이 없을 때만 바뀌어요",
            setOf(SHIFT), 0xFF0D1B2A, 0xFF3949AB, "🦉",
            look(day("주간", "day", "주", 0xFFEF6C00, "☀️"), day("야간", "night", "야", tok("night"), "🦉"), day("비번", "off", "비", 0xFF455A64, "🛏️"), day("휴무", "off", "휴", 0xFF2E7D32, "🌿")),
            alarms("night" to listOf(240L, 60L), "day" to listOf(70L), mission = "math", snooze = 10L),
            memo(tpl("야간 뒤 회복", "🌅", listOf("퇴근길 선글라스", "가볍게 먹기", "암막 커튼", "4시간 이상 자기", "저녁 햇빛 산책"))),
            theme(today = tok("night"), sat = 0xFF1565C0, sun = 0xFFC62828, cell = "dot"),
            layout(calendar = "todayFlow,summary,counts,rest,dayCard,memos"),
            cards(stats = "tiles,pay,three,trend,weeks", money = "hero,shiftspend,pace,recent,budget"),
            widget(alpha = 60, theme = "dark", memo = true), corners("soft"),
            tokens = listOf(Token("night", 0xFF283593, 0xFF9FA8DA))),
        recipe("24시간 당직 마스터", "당·비·휴가 또렷하고 출동 장비 점검까지",
            "누구에게 좋아요: 소방·경비·병원 당직처럼 24시간 근무하는 분\n적용하면 바뀌는 것:\n· 당직·비번·휴무 글자·색·이모지\n· 당직 2시간·1시간 전 알람, 점점 크게\n· 당직 인수인계·장비 점검 메모 틀\n· 휴식 점검을 달력 위쪽으로\n· 통계는 3개월 평균과 주별 시간을 먼저\n주의할 점: 근무 이름이 달라도 역할(24시간·휴무)로 맞춰요",
            setOf(SHIFT), 0xFFB71C1C, 0xFF263238, "🚒",
            look(day("당직", "long", "당", 0xFFC62828, "🚨"), day("비번", "off", "비", 0xFF546E7A, "😴"), day("휴무", "off", "휴", 0xFF2E7D32, "🌿")),
            alarms("long" to listOf(120L, 60L), snooze = 5L, gradual = true),
            memo(tpl("당직 인수인계", "📋", listOf("출동·민원 건수", "남은 처리 건", "차량·장비 상태", "다음 당직자 확인")),
                tpl("장비 점검", "🧯", listOf("무전기 충전", "개인 보호구", "차량 연료", "응급 가방"), repeat = "W1")),
            theme(today = 0xFFC62828, sun = 0xFFC62828, holiday = 0xFFAD1457, cell = "fill"),
            layout(calendar = "rest,todayFlow,summary,counts,dayCard,hours,memos"),
            cards(stats = "tiles,three,weeks,pay,kinds,year"),
            widget(alpha = 100, memo = false), corners("square")),
        recipe("4조 2교대 공장 대시보드", "근무표·급여·특근이 한 화면에, 통계 탭을 앞으로",
            "누구에게 좋아요: 4조 2교대 생산직\n적용하면 바뀌는 것:\n· 주간·야간·비번·휴무 글자·색·이모지\n· 근무 80분·50분 전 알람\n· 설비 인계·주간 안전 점검 메모 틀\n· 아래 탭 순서: 달력 다음에 통계\n· 통계는 급여·추가 근무 종류·작년 비교 먼저\n주의할 점: 탭 순서는 설정에서 언제든 되돌려요",
            setOf(SHIFT), 0xFFFF6F00, 0xFF283593, "🏭",
            look(day("주간", "day", "주", 0xFFE65100, "☀️"), day("야간", "night", "야", 0xFF283593, "🌙"), day("비번", "off", "비", 0xFF607D8B, "🏠"), day("휴무", "off", "휴", 0xFF2E7D32, "🌿")),
            alarms("day" to listOf(80L, 50L), "night" to listOf(80L, 50L), snooze = 5L),
            memo(tpl("설비 인계", "⚙️", listOf("가동 상태", "불량·정지 이력", "자재 남은 양", "다음 조 전달")),
                tpl("주간 안전 점검", "🦺", listOf("보호구", "비상구", "소화기", "정리정돈"), repeat = "W1")),
            theme(today = 0xFFE65100, sat = 0xFF1565C0, sun = 0xFFC62828, cell = "fill"),
            layout(tabs = listOf("calendar", "stats", "money", "memos", "plaza", "settings"), calendar = "counts,summary,todayFlow,dayCard,hours,rest,memos"),
            cards(stats = "pay,tiles,kinds,compare,year,three"),
            widget(alpha = 85)),
        recipe("교대 살림 판", "교대 근무에 맞춘 가계부 카드와 월급날 정리",
            "누구에게 좋아요: 교대 근무하며 살림도 챙기는 분\n적용하면 바뀌는 것:\n· 내가 고른 강조색으로 오늘 표시와 주간 색\n· 가계부는 이번 달 흐름·근무별 소비·카드값·구독을 앞으로\n· 매달 25일 월급날 정리 메모 틀\n· 메모는 공책 줄을 먼저\n· 위젯에 메모 한 줄\n주의할 점: 강조색은 넣을 때 내가 골라요",
            setOf(SHIFT), 0xFF7B1FA2, 0xFFF06292, "👛",
            look(day("주간", "day", "주", Ref.Input("i2"), "☀️"), day("야간", "night", "야", 0xFF311B92, "🌙"), day("휴무", "off", "휴", 0xFF616161)),
            memo(tpl("월급날 정리", "💌", listOf("명세서 확인", "적금 넣기", "카드값 확인", "고정 지출 점검", "이번 달 예산 정하기"), repeat = "M25")),
            theme(today = Ref.Input("i2"), cell = "tint"),
            layout(calendar = "summary,todayFlow,counts,dayCard,memos"),
            cards(money = "hero,pace,shiftspend,cardbill,subs,budget,upcoming", memo = "notebooks,search"),
            widget(alpha = 90, memo = true), corners("round"),
            inputs = listOf(accent)),
    )

    private val dayWork = listOf(
        recipe("칼퇴 직장인 대시보드", "오늘 흐름·회의 틀·52시간 점검을 한 번에",
            "누구에게 좋아요: 평일 9시 출근 직장인\n적용하면 바뀌는 것:\n· 출근 80분·50분 전 알람, 점점 크게\n· 매주 월요일 주간 회의, 하루 마무리 메모 틀\n· 달력 위 오늘의 흐름, 아래 주 근무시간 줄\n· 통계는 주별 시간·3개월 평균 먼저\n· 위젯 반투명과 메모 한 줄\n주의할 점: 알람은 더하기만 해요",
            setOf(DAY), 0xFF1565C0, 0xFF00ACC1, "💼",
            alarms("day" to listOf(80L, 50L), snooze = 5L, gradual = true),
            memo(tpl("주간 회의", "📝", listOf("지난주 한 일", "이번 주 할 일", "막힌 것", "공유할 것"), repeat = "W1"),
                tpl("하루 마무리", "🌇", listOf("내일 할 일 세 개", "답장 못 한 메일", "책상 정리"))),
            theme(today = 0xFF1565C0, sat = 0xFF1565C0, sun = 0xFFC62828, cell = "tint"),
            layout(calendar = "todayFlow,summary,dayCard,hours,memos,counts"),
            cards(stats = "tiles,weeks,three,pay,trend"),
            widget(alpha = 80, memo = true), corners("soft")),
        recipe("재택·출근 하이브리드", "재택과 출근 날이 한눈에, 날마다 다른 준비",
            "누구에게 좋아요: 재택을 섞어 일하는 분\n적용하면 바뀌는 것:\n· 출근·재택·휴무 글자·색·이모지\n· 출근 날만 70분 전 알람\n· 재택 시작·출근 준비 메모 틀\n· 달력은 하루 카드를 위로\n주의할 점: '재택' 근무가 없으면 그 칸은 넘어가요",
            setOf(DAY), 0xFF3949AB, 0xFF7CB342, "🏡",
            look(day("출근", "day", "출", 0xFF3949AB, "🏢"), day("재택", "other", "재", 0xFF558B2F, "🏡"), day("휴무", "off", "휴", 0xFF757575)),
            alarms("day" to listOf(70L), snooze = 5L),
            memo(tpl("재택 시작", "💻", listOf("메신저 상태 켜기", "화상 회의 링크 확인", "환기", "점심 시간 정하기")),
                tpl("출근 준비", "🎒", listOf("사원증", "노트북·충전기", "우산"))),
            theme(today = 0xFF558B2F, sun = 0xFFC62828, cell = "tint"),
            layout(calendar = "todayFlow,dayCard,summary,memos,hours"),
            widget(alpha = 90, memo = true)),
        recipe("공무원 일근 당직 판", "평일 근무에 가끔 서는 당직까지 깔끔하게",
            "누구에게 좋아요: 일근에 당직이 섞인 공무원·공공기관\n적용하면 바뀌는 것:\n· 근무·당직·휴무 글자·색·이모지\n· 근무 60분 전, 당직 90분 전 알람\n· 당직 일지·월말 정리 메모 틀\n· 공휴일 색 따로\n· 통계는 3개월 평균과 올해 합계\n주의할 점: 당직 수당은 회사 급여 설정에서 정해요",
            setOf(DAY), 0xFF283593, 0xFF5C6BC0, "🏛️",
            look(day("근무", "day", "근", 0xFF283593, "🏛️"), day("당직", "long", "당", 0xFFC62828, "🌙"), day("휴무", "off", "휴", 0xFF616161)),
            alarms("day" to listOf(60L), "long" to listOf(90L), snooze = 5L),
            memo(tpl("당직 일지", "📒", listOf("민원·전화", "순찰 시각", "특이 사항", "인계할 것")),
                tpl("월말 정리", "🗂️", listOf("근무 시간 확인", "초과근무 신청", "연가 남은 날"), repeat = "M25")),
            theme(sat = 0xFF1565C0, sun = 0xFFC62828, holiday = 0xFFAD1457, cell = "tint"),
            cards(stats = "tiles,three,weeks,year,pay")),
        recipe("월급 관리 일근", "급여·세금·나갈 돈을 가계부 맨 위로",
            "누구에게 좋아요: 월급을 계획대로 쓰고 싶은 직장인\n적용하면 바뀌는 것:\n· 가계부: 합계 다음에 수입·세금·나갈 돈·구독·카드값·예산\n· 월급날 정리·카드 결제일 메모 틀\n· 아래 탭: 달력 다음에 가계부\n· 위젯에 메모 한 줄\n주의할 점: 카드 순서는 가계부에서 다시 바꿀 수 있어요",
            setOf(DAY), 0xFF00695C, 0xFFFFC107, "💳",
            memo(tpl("월급날 정리", "💌", listOf("명세서 확인", "적금 넣기", "고정 지출 확인", "예산 정하기"), repeat = "M25"),
                tpl("카드 결제일", "💳", listOf("이번 달 카드값", "할부 남은 달", "안 쓰는 구독 해지"), repeat = "M14")),
            theme(today = 0xFF00695C, cell = "tint"),
            layout(tabs = listOf("calendar", "money", "stats", "memos", "plaza", "settings")),
            cards(money = "hero,income,tax,upcoming,subs,cardbill,budget,pace,recent"),
            widget(alpha = 95, memo = true)),
        recipe("미니멀 흑백 달력", "색 없이 글자만, 군더더기 뺀 조용한 화면",
            "누구에게 좋아요: 화면이 복잡한 게 싫은 분\n적용하면 바뀌는 것:\n· 근무·휴무를 먹색·회색 두 가지로\n· 달력 칸은 칠하지 않고 점만\n· 달력 아래 꼭 필요한 것만 (주 근무시간·휴식 점검 숨기기)\n· 메모는 검색 칸만 위에\n· 위젯은 밝게, 모서리는 각지게\n주의할 점: 숨긴 카드는 설정 › 화면 배치에서 다시 켜요",
            setOf(DAY), 0xFF212121, 0xFFBDBDBD, "◻️",
            look(day("근무", "day", "근", 0xFF212121), day("휴무", "off", "휴", 0xFF757575)),
            theme(today = 0xFF212121, sat = 0xFF424242, sun = 0xFF424242, holiday = 0xFF424242, cell = "dot"),
            layout(calendar = "summary,dayCard,memos,-hours,-rest,-todayFlow"),
            cards(memo = "search,-notebooks"),
            widget(alpha = 100, theme = "light", memo = false), corners("square")),
    )

    private val hourlyWork = listOf(
        recipe("편의점 야간 알바 완전판", "야간 가산·주휴·휴게까지 맞춘 계산과 인수 체크",
            "누구에게 좋아요: 5인 이상 편의점 야간 알바\n적용하면 바뀌는 것:\n· 시급(내가 넣어요), 야간 가산 50%, 주휴수당, 법정 휴게 빼기\n· 야간 글자·색·이모지, 60분·30분 전 알람\n· 야간 인수 체크 메모 틀 (시재·폐기·진열)\n· 달력은 주 근무시간 줄을 위로\n· 통계는 급여 카드와 주별 시간 먼저\n주의할 점: 5인 미만 매장이면 야간 가산이 없을 수 있어요",
            setOf(HOURLY), 0xFF1A237E, 0xFF43A047, "🏪",
            Piece(PieceKind.HOURLY_RULES, mapOf("wage" to Ref.Input("i1"), "five" to true, "night" to 50L, "weeklyPay" to true, "legalBreaks" to true,
                "breakPay" to "UNPAID", "rounding" to "AT_END", "tax" to "NONE")),
            look(day("야간", "night", "야", 0xFF1A237E, "🌙"), day("주간", "day", "주", 0xFF2E7D32, "🏪")),
            alarms("night" to listOf(60L, 30L), "day" to listOf(40L), snooze = 5L),
            memo(tpl("야간 인수 체크", "🔦", listOf("시재 맞추기", "폐기 상품 정리", "음료 진열", "택배 보관", "다음 근무자 전달"))),
            theme(today = 0xFF1A237E, sun = 0xFFC62828, cell = "tint"),
            layout(calendar = "hours,todayFlow,summary,dayCard,memos"),
            cards(stats = "pay,tiles,weeks,three,trend", money = "hero,income,recent,budget"),
            widget(alpha = 85, memo = true),
            inputs = listOf(wage)),
        recipe("카페 오픈·마감 알바", "오픈·마감을 색으로 나누고 체크 목록을 손에",
            "누구에게 좋아요: 카페·베이커리 알바\n적용하면 바뀌는 것:\n· 오픈·마감 글자·색·이모지\n· 오픈 50분 전, 마감 40분 전 알람\n· 오픈 체크·마감 체크 메모 틀\n· 시급(내가 넣어요), 주휴수당, 법정 휴게 빼기\n· 둥근 모서리, 위젯에 메모\n주의할 점: 주 15시간이 안 되면 주휴수당을 꺼요",
            setOf(HOURLY), 0xFF6D4C41, 0xFFFFB74D, "☕",
            Piece(PieceKind.HOURLY_RULES, mapOf("wage" to Ref.Input("i1"), "weeklyPay" to true, "legalBreaks" to true, "breakPay" to "UNPAID")),
            look(day("오픈", "day", "오", 0xFFBF360C, "🥐"), day("마감", "eve", "마", 0xFF4E342E, "🌙")),
            alarms("day" to listOf(50L), "eve" to listOf(40L), snooze = 5L),
            memo(tpl("오픈 체크", "🔑", listOf("머신 예열", "원두 그라인더", "우유·시럽 채우기", "포스 켜기", "테이블 정리")),
                tpl("마감 체크", "🧽", listOf("머신 청소", "우유 정리", "시재 마감", "쓰레기", "문단속"))),
            theme(today = 0xFFBF360C, cell = "tint"),
            cards(stats = "pay,weeks,tiles"),
            widget(alpha = 90, memo = true), corners("round"),
            inputs = listOf(wage)),
        recipe("투잡 알바 한 판", "두 곳 근무를 색으로 나누고 수입을 한눈에",
            "누구에게 좋아요: 알바를 두 곳 하는 분\n적용하면 바뀌는 것:\n· 오전·저녁 두 근무지 글자·색·이모지\n· 근무 40분 전 알람\n· 이동 준비 메모 틀\n· 가계부는 수입 내역·3.3% 합계 먼저\n· 통계는 급여·지난달 비교\n주의할 점: 두 번째 근무지 시급은 근무지 설정에서 따로 넣어요",
            setOf(HOURLY), 0xFFEF6C00, 0xFF3949AB, "🔀",
            look(day("오전", "day", "오", 0xFFE65100, "🥐"), day("저녁", "eve", "저", 0xFF3949AB, "🍽️")),
            alarms("day" to listOf(40L), "eve" to listOf(40L), snooze = 5L),
            memo(tpl("이동 준비", "🚌", listOf("유니폼 갈아입기", "교통카드", "간단한 끼니"))),
            theme(today = 0xFFE65100, cell = "tint"),
            layout(calendar = "todayFlow,summary,hours,dayCard,memos"),
            cards(stats = "pay,compare,weeks,tiles", money = "hero,income,freelance,recent"),
            widget(alpha = 90, memo = true)),
        recipe("3.3% 학원 강사", "사업소득 3.3%와 수업 준비를 함께",
            "누구에게 좋아요: 3.3%를 떼는 학원·과외 강사\n적용하면 바뀌는 것:\n· 시급(내가 넣어요), 세금 3.3%\n· 수업 글자·색·이모지, 수업 60분 전 알람\n· 수업 준비·5월 종합소득세 메모 틀\n· 가계부는 3.3% 합계·세금을 앞으로\n주의할 점: 4대보험을 내는 곳이면 세금을 4대보험으로 바꿔요",
            setOf(HOURLY), 0xFF6A1B9A, 0xFF26A69A, "📚",
            Piece(PieceKind.HOURLY_RULES, mapOf("wage" to Ref.Input("i1"), "tax" to "WITHHOLDING_3_3", "weeklyPay" to false, "breakPay" to "PAID")),
            look(day("수업", "eve", "수", 0xFF6A1B9A, "📚")),
            alarms("eve" to listOf(60L), snooze = 5L),
            memo(tpl("수업 준비", "📚", listOf("진도 확인", "자료 출력", "숙제 검사", "결석생 연락")),
                tpl("종합소득세 준비", "🧾", listOf("원천징수 영수증 모으기", "경비 영수증", "홈택스 신고"), repeat = "M1")),
            theme(today = 0xFF6A1B9A, cell = "tint"),
            cards(money = "freelance,tax,income,hero,recent", stats = "pay,weeks,trend"),
            corners("soft"),
            inputs = listOf(wage)),
        recipe("주휴수당 지킴이", "주 15시간을 넘는지 늘 보이게",
            "누구에게 좋아요: 주휴수당을 꼭 챙기고 싶은 알바\n적용하면 바뀌는 것:\n· 시급(내가 넣어요), 주휴수당, 주 시작 월요일, 법정 휴게 빼기\n· 달력 맨 위에 이 주 근무시간 줄\n· 통계는 주별 시간·3개월 평균 먼저\n· 매주 일요일 이번 주 시간 확인 메모 틀\n주의할 점: 근로계약서의 주 시작 요일과 맞춰 보세요",
            setOf(HOURLY), 0xFF2E7D32, 0xFFFFEB3B, "🗓️",
            Piece(PieceKind.HOURLY_RULES, mapOf("wage" to Ref.Input("i1"), "weeklyPay" to true, "weekStart" to "MONDAY", "legalBreaks" to true, "breakPay" to "UNPAID")),
            memo(tpl("이번 주 시간 확인", "⏱️", listOf("이번 주 근무시간 보기", "15시간 넘었나", "빠진 근무 적기"), repeat = "W7")),
            theme(today = 0xFF2E7D32, cell = "tint"),
            layout(calendar = "hours,summary,todayFlow,dayCard,memos"),
            cards(stats = "weeks,three,pay,tiles"),
            widget(alpha = 90),
            inputs = listOf(wage)),
    )

    private val fieldWork = listOf(
        recipe("인테리어 반장 풀세트", "공수 칩·끝난 시각·작업·경비에 견적 틀까지",
            "누구에게 좋아요: 인테리어 목수·반장\n적용하면 바뀌는 것:\n· 일당(내가 넣어요), 공수 칩 0.5·1·1.5·2\n· 5시면 1공, 7시면 1.5공, 9시면 2공\n· 작업 분류 6개, 경비 분류 6개\n· 현장 일지·견적 문의 메모 틀\n· 달력은 오늘 일감 카드를 위로, 가계부는 받을 돈 먼저\n주의할 점: 일당은 받는 분마다 달라 비워 두었어요",
            setOf(FIELD), 0xFF795548, 0xFFFFB300, "🛠️",
            Piece(PieceKind.FIELD_SHEET, mapOf("mode" to "DAY_RATE", "dayRate" to Ref.Input("i1"), "chips" to listOf(0.5, 1.0, 1.5, 2.0),
                "ends" to listOf(mapOf("end" to "17:00", "units" to 1.0), mapOf("end" to "19:00", "units" to 1.5), mapOf("end" to "21:00", "units" to 2.0)),
                "kinds" to listOf("목공", "도장", "타일", "철거", "설비", "마감"), "costs" to listOf("자재비", "공구", "유류비", "식대", "주차비", "폐기물"))),
            memo(tpl("현장 일지", "📒", listOf("오늘 한 공정", "투입 인원", "자재 들어온 것", "내일 할 일", "사진 찍기")),
                tpl("견적 문의", "📐", listOf("현장 위치", "면적·수량", "일정", "자재 누가", "선금 비율"))),
            theme(today = 0xFF795548, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "owed,work,freelance,hero,recent"),
            widget(alpha = 90, memo = true), corners("square"),
            inputs = listOf(dayRate)),
        recipe("배달 라이더 대시보드", "플랫폼별 수입과 차량 경비, 정비 알림까지",
            "누구에게 좋아요: 배민·쿠팡이츠·요기요 라이더\n적용하면 바뀌는 것:\n· 플랫폼형, 플랫폼 4개, 경비 6개\n· 매달 1일 오토바이 정비 메모 틀, 운행 전 점검 틀\n· 달력은 오늘 일감과 이번 달 요약 먼저\n· 가계부는 일감 돈·이번 달 흐름·3.3% 합계\n· 위젯은 어둡게\n주의할 점: 수수료·정산일은 각 앱 기준이에요",
            setOf(FIELD), 0xFF00897B, 0xFF263238, "🛵",
            Piece(PieceKind.FIELD_SHEET, mapOf("mode" to "PLATFORM", "chips" to listOf(1.0), "kinds" to listOf("배민", "쿠팡이츠", "요기요", "지역 대행"),
                "costs" to listOf("유류비", "보험료", "수리비", "통신비", "장비", "식대"))),
            memo(tpl("오토바이 정비", "🔧", listOf("엔진오일", "타이어 공기압", "브레이크 패드", "체인 장력"), repeat = "M1"),
                tpl("운행 전 점검", "🪖", listOf("헬멧·장갑", "보조 배터리", "우비", "휴대폰 거치대"))),
            theme(today = 0xFF00897B, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "work,pace,freelance,hero,recent"),
            widget(alpha = 70, theme = "dark", memo = true)),
        recipe("프리랜서 디자이너 건별", "일감마다 선금·잔금, 받을 돈이 맨 위",
            "누구에게 좋아요: 건별로 받는 디자이너·편집자\n적용하면 바뀌는 것:\n· 건별형, 작업 분류 6개, 경비 분류 5개\n· 작업 의뢰서·마감 전 확인 메모 틀\n· 아래 탭: 달력 다음에 가계부\n· 가계부는 받을 돈·일감 돈·3.3% 합계·세금 먼저\n· 둥근 모서리\n주의할 점: 금액은 일감마다 적어요",
            setOf(FIELD), 0xFF7E57C2, 0xFFEC407A, "🎨",
            Piece(PieceKind.FIELD_SHEET, mapOf("mode" to "PER_JOB", "chips" to listOf(1.0), "kinds" to listOf("로고", "상세페이지", "영상 편집", "썸네일", "인쇄물", "수정"),
                "costs" to listOf("폰트·소재", "구독", "외주", "장비", "교통비"))),
            memo(tpl("작업 의뢰서", "🧾", body = "작업 내용:\n분량:\n마감일:\n수정 횟수:\n선금·잔금 날짜:\n원본 파일 전달:"),
                tpl("마감 전 확인", "✅", listOf("오탈자", "해상도", "파일 이름 규칙", "원본 백업", "잔금 요청"))),
            theme(today = 0xFF7E57C2, cell = "tint"),
            layout(tabs = listOf("calendar", "money", "memos", "stats", "plaza", "settings"), calendar = "todayWork,summary,memos,dayCard"),
            cards(money = "owed,work,freelance,tax,hero,recent"),
            corners("round")),
        recipe("용달·화물 운행 판", "운행 건별 기록과 톨비·유류비 정리",
            "누구에게 좋아요: 용달·화물·퀵 기사\n적용하면 바뀌는 것:\n· 플랫폼형, 일감 분류 5개, 경비 6개\n· 운행 전 점검·매달 정비 메모 틀\n· 달력 칸 진하게, 일요일 색\n· 가계부는 일감 돈·받을 돈 먼저\n주의할 점: 화물 정보망 수수료는 경비로 적어요",
            setOf(FIELD), 0xFF37474F, 0xFFFFA000, "🚚",
            Piece(PieceKind.FIELD_SHEET, mapOf("mode" to "PLATFORM", "chips" to listOf(1.0), "kinds" to listOf("용달", "화물", "이사", "퀵", "고정 거래처"),
                "costs" to listOf("유류비", "톨비", "정비", "보험료", "정보망 수수료", "주차비"))),
            memo(tpl("운행 전 점검", "🚚", listOf("타이어", "적재 끈", "운송장", "블랙박스")),
                tpl("차량 정비", "🔧", listOf("엔진오일", "브레이크", "타이어 교체 시기", "보험 만기"), repeat = "M5")),
            theme(today = 0xFFFFA000, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "work,owed,hero,pace,recent"),
            widget(alpha = 85, memo = true)),
        recipe("촬영 스태프 일당표", "밤샘 촬영 공수와 촬영 준비물",
            "누구에게 좋아요: 방송·영화·광고 촬영 스태프\n적용하면 바뀌는 것:\n· 일당(내가 넣어요), 공수 칩 1·1.5·2\n· 10시면 1공, 자정이면 1.5공\n· 부서 분류 5개, 경비 5개\n· 콜시트 확인·촬영 준비물 메모 틀\n· 달력은 오늘 일감과 하루 카드 먼저\n주의할 점: 공수 기준은 현장마다 달라 고칠 수 있어요",
            setOf(FIELD), 0xFF212121, 0xFFE53935, "🎬",
            Piece(PieceKind.FIELD_SHEET, mapOf("mode" to "DAY_RATE", "dayRate" to Ref.Input("i1"), "chips" to listOf(1.0, 1.5, 2.0),
                "ends" to listOf(mapOf("end" to "22:00", "units" to 1.0), mapOf("end" to "23:59", "units" to 1.5)),
                "kinds" to listOf("촬영부", "조명부", "연출부", "미술부", "동시녹음"), "costs" to listOf("교통비", "식대", "숙박", "장비 대여", "소모품"))),
            memo(tpl("콜시트 확인", "📋", listOf("집합 시각", "장소", "준비물", "의상", "다음 날 콜")),
                tpl("촬영 준비물", "🎒", listOf("보조 배터리", "장갑", "핫팩·쿨팩", "우비", "개인 상비약"))),
            theme(today = 0xFFE53935, cell = "fill"),
            layout(calendar = "todayWork,dayCard,summary,memos"),
            cards(money = "work,owed,freelance,hero"),
            widget(alpha = 80, theme = "dark"), corners("square"),
            inputs = listOf(dayRate)),
    )

    private val week6 = listOf("TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

    private val shopWork = listOf(
        recipe("동네 카페 운영 풀세트", "영업일·카드 정산·마감·발주를 한 판에",
            "누구에게 좋아요: 혼자 또는 둘이 하는 동네 카페\n적용하면 바뀌는 것:\n· 월요일 쉬는 영업일, 9시~21시\n· 카드 수수료 1.5%, 정산 3일\n· 마감 체크 5개, 경비 분류 6개\n· 오픈 준비·매주 화요일 발주 메모 틀\n· 가계부는 매출 나눠 보기·카드 매출 입금 먼저\n주의할 점: 카드 수수료는 내 가맹점 기준으로 고쳐요",
            setOf(BUSINESS), 0xFF5D4037, 0xFFFFCC80, "☕",
            Piece(PieceKind.SHOP, mapOf("open" to week6, "opens" to "09:00", "closes" to "21:00", "holidaysClosed" to false, "cardFee" to 1.5, "settleDays" to 3L,
                "closing" to listOf("머신 청소", "우유·디저트 정리", "시재 마감", "쓰레기", "문단속"), "costs" to listOf("원두", "우유", "디저트 재료", "컵·포장", "임대료", "공과금"))),
            memo(tpl("오픈 준비", "🌅", listOf("머신 예열", "원두 확인", "디저트 진열", "포스 켜기", "간판 켜기")),
                tpl("주간 발주", "📦", listOf("원두", "우유", "컵·뚜껑", "시럽", "세제"), repeat = "W2")),
            theme(today = 0xFF5D4037, sat = 0xFF1565C0, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "hero,salessplit,work,cardbill,budget,upcoming,recent"),
            widget(alpha = 90, memo = true), corners("round")),
        recipe("한식당 장사 관리", "점심·저녁 장사, 배달앱 수수료와 식자재 발주",
            "누구에게 좋아요: 홀과 배달을 같이 하는 음식점\n적용하면 바뀌는 것:\n· 월요일 쉬는 영업일, 11시~22시\n· 정산 3일, 마감 체크 6개, 경비 분류 7개\n· 식자재 발주·위생 점검 메모 틀\n· 가계부는 매출 나눠 보기·이번 달 흐름 먼저\n주의할 점: 간이과세면 부가가치율도 넣어요",
            setOf(BUSINESS), 0xFFBF360C, 0xFFFFE0B2, "🍲",
            Piece(PieceKind.SHOP, mapOf("open" to week6, "opens" to "11:00", "closes" to "22:00", "settleDays" to 3L,
                "closing" to listOf("가스 밸브", "냉장고 온도", "식재료 정리", "시재 마감", "배달앱 영업 종료", "문단속"),
                "costs" to listOf("식재료", "주류", "가스비", "인건비", "배달앱수수료", "포장재", "임대료"))),
            memo(tpl("식자재 발주", "🥬", listOf("채소", "고기", "쌀", "양념", "음료·주류"), repeat = "W2"),
                tpl("위생 점검", "🧼", listOf("냉장·냉동 온도", "유통기한", "손 씻기 비누", "방충망", "주방 바닥"), repeat = "W1")),
            theme(today = 0xFFBF360C, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "hero,salessplit,pace,work,budget,upcoming"),
            widget(alpha = 90, memo = true)),
        recipe("1인 네일·미용숍", "예약 위주 영업과 상담 틀, 재료 관리",
            "누구에게 좋아요: 혼자 하는 네일·미용·속눈썹 숍\n적용하면 바뀌는 것:\n· 화요일 쉬는 영업일, 10시~20시\n· 마감 체크 4개, 경비 분류 5개\n· 예약 상담·재료 점검 메모 틀\n· 둥근 모서리, 분홍 계열 달력\n주의할 점: 손님 이름·연락처는 메모에 적지 않는 게 좋아요",
            setOf(BUSINESS), 0xFFAD1457, 0xFFF8BBD0, "💅",
            Piece(PieceKind.SHOP, mapOf("open" to listOf("MONDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"), "opens" to "10:00", "closes" to "20:00",
                "closing" to listOf("도구 소독", "수건 세탁", "재료 정리", "시재 마감"), "costs" to listOf("젤·재료", "소모품", "임대료", "광고비", "교육비"))),
            memo(tpl("예약 상담", "💬", body = "원하는 디자인:\n지난 시술:\n알레르기·주의:\n걸리는 시간:\n다음 예약 권하기:"),
                tpl("재료 점검", "🧴", listOf("젤 색 재고", "리무버", "솜·호일", "소독제"), repeat = "W1")),
            theme(today = 0xFFAD1457, sat = 0xFF1565C0, sun = 0xFFC62828, cell = "tint"),
            layout(calendar = "todayWork,summary,memos,dayCard"),
            cards(money = "hero,salessplit,work,budget,recent"),
            widget(alpha = 90, memo = true), corners("round")),
        recipe("스마트스토어 셀러 판", "평일 발송, 마켓 정산, 광고 점검 루틴",
            "누구에게 좋아요: 온라인 쇼핑몰·마켓 셀러\n적용하면 바뀌는 것:\n· 평일 영업, 공휴일 쉬기, 9시~18시\n· 정산 7일, 마감 체크 4개, 경비 분류 6개\n· 상품 등록·매주 광고 점검 메모 틀\n· 아래 탭: 달력 다음에 가계부\n· 가계부는 일감·매출 돈과 카드값 먼저\n주의할 점: 정산일은 마켓마다 달라요",
            setOf(BUSINESS), 0xFF1976D2, 0xFF00C853, "📦",
            Piece(PieceKind.SHOP, mapOf("open" to listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), "opens" to "09:00", "closes" to "18:00",
                "holidaysClosed" to true, "settleDays" to 7L, "closing" to listOf("송장 출력", "택배 접수", "재고 기록", "문의 답변"),
                "costs" to listOf("택배비", "포장재", "광고비", "마켓수수료", "사입", "촬영"))),
            memo(tpl("상품 등록", "🛍️", listOf("대표 사진", "상세페이지", "옵션·재고", "배송비", "검색어 태그")),
                tpl("광고 점검", "📊", listOf("클릭 수", "전환율", "광고비 확인", "키워드 정리"), repeat = "W1")),
            theme(today = 0xFF1976D2, holiday = 0xFFAD1457, cell = "tint"),
            layout(tabs = listOf("calendar", "money", "stats", "memos", "plaza", "settings"), calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "hero,work,cardbill,budget,upcoming,recent"),
            widget(alpha = 95)),
        recipe("간이과세 소매점", "간이과세 부가가치율과 신고 준비",
            "누구에게 좋아요: 간이과세로 편의점·소매점을 하는 분\n적용하면 바뀌는 것:\n· 매일 영업, 8시~23시, 간이과세, 부가가치율 15%\n· 마감 체크 5개, 경비 분류 6개\n· 1월 부가세 신고 준비·매주 재고 점검 메모 틀\n· 가계부는 매출 나눠 보기·예산 먼저\n주의할 점: 업종이 다르면 부가가치율도 달라요. 사업자등록증으로 확인하세요",
            setOf(BUSINESS), 0xFF2E7D32, 0xFFFFF176, "🧮",
            Piece(PieceKind.SHOP, mapOf("open" to WEEK_DAYS, "opens" to "08:00", "closes" to "23:00", "simpleTax" to true, "valueAdded" to 15L, "settleDays" to 3L,
                "closing" to listOf("시재 마감", "냉장고 온도", "유통기한", "셔터", "보안 설정"), "costs" to listOf("상품 사입", "임대료", "전기료", "인건비", "포장재", "카드 단말기"))),
            memo(tpl("부가세 신고 준비", "🧾", listOf("매출 합계 확인", "매입 세금계산서 모으기", "카드 매출 자료", "홈택스 신고"), repeat = "M5"),
                tpl("재고 점검", "📋", listOf("많이 팔린 것", "유통기한 임박", "발주할 것"), repeat = "W1")),
            theme(today = 0xFF2E7D32, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,dayCard,memos"),
            cards(money = "hero,salessplit,budget,upcoming,work"),
            widget(alpha = 90, memo = true), corners("soft")),
    )

    private fun invest(rules: List<String>, journal: List<String>) =
        Piece(PieceKind.INVEST, mapOf("rules" to rules.map { mapOf("text" to it) }, "journal" to journal))

    private val investWork = listOf(
        recipe("가치투자 일지 판", "원칙 다섯, 일지 칸, 사기 전 점검까지",
            "누구에게 좋아요: 오래 들고 가는 투자를 하고 싶은 분\n적용하면 바뀌는 것:\n· 투자 원칙 5개, 일지 칸 5개\n· 사기 전 점검·분기 실적 확인 메모 틀\n· 달력은 오늘 투자 카드와 메모 먼저\n· 오른 날 빨강, 내린 날 파랑처럼 보이는 칸\n주의할 점: 종목을 알려 주는 판이 아니에요. 판단은 본인 몫이에요",
            setOf(INVEST), 0xFF1A237E, 0xFFC62828, "📈",
            invest(listOf("사업을 한 줄로 설명할 수 있을 때만 산다", "세 번에 나눠 산다", "한 종목은 전체의 20%까지", "산 이유가 사라지면 판다", "빚내서 사지 않는다"),
                listOf("산 이유", "적정 가치 생각", "판 이유", "감정 점수", "다음에 바꿀 것")),
            memo(tpl("사기 전 점검", "✅", listOf("산 이유를 한 줄로", "비중 20% 넘지 않나", "하루 기다렸나", "팔 기준")),
                tpl("분기 실적 확인", "📊", listOf("매출·이익 흐름", "산 이유 그대로인가", "비중 다시 보기"))),
            theme(today = 0xFF1A237E, sat = 0xFF1565C0, sun = 0xFFC62828, cell = "fill"),
            layout(calendar = "todayWork,summary,memos,dayCard"),
            widget(alpha = 90, memo = true), corners("soft")),
        recipe("적립식 루틴 판", "매달 같은 날 넣고 1년에 한 번 맞추기",
            "누구에게 좋아요: 매달 적립하는 분\n적용하면 바뀌는 것:\n· 원칙 4개, 일지 칸 3개\n· 매달 5일 적립 확인 메모 틀\n· 매년 1월 비중 맞추기 메모 틀\n· 위젯은 밝게 반투명\n주의할 점: 적립일은 내 월급날에 맞춰 메모에서 바꿔요",
            setOf(INVEST), 0xFF2E7D32, 0xFFA5D6A7, "🌳",
            invest(listOf("매달 같은 날 넣는다", "떨어져도 계획대로 넣는다", "1년에 한 번 비중을 맞춘다", "생활비 석 달치는 따로 둔다"),
                listOf("이번 달 적립", "비중 확인", "느낀 점")),
            memo(tpl("적립 확인", "🌱", listOf("이체 됐나", "비중 확인", "일지 한 줄"), repeat = "M5"),
                tpl("비중 맞추기", "⚖️", listOf("목표 비중", "지금 비중", "옮길 것", "수수료 확인"))),
            theme(today = 0xFF2E7D32, cell = "tint"),
            layout(calendar = "summary,todayWork,memos"),
            widget(alpha = 85, theme = "light", memo = true), corners("round")),
        recipe("매매 복기 노트", "매매마다 이유와 감정을 남기고 매주 돌아보기",
            "누구에게 좋아요: 단기 매매를 하며 실수를 줄이고 싶은 분\n적용하면 바뀌는 것:\n· 원칙 4개, 일지 칸 6개\n· 매주 토요일 주간 복기 메모 틀\n· 메모는 공책 줄과 검색 먼저\n· 아래 탭: 달력 다음에 메모\n주의할 점: 손실 기준은 내 상황에 맞게 고쳐요",
            setOf(INVEST), 0xFF4A148C, 0xFF7C4DFF, "📒",
            invest(listOf("매매 뒤 바로 일지를 쓴다", "정한 손절 기준을 지킨다", "하루 세 번 넘게 사고팔지 않는다", "잃은 날은 더 사지 않는다"),
                listOf("들어간 이유", "나온 이유", "계획대로였나", "감정 점수", "배운 것", "다음 규칙")),
            memo(tpl("주간 복기", "🔍", listOf("이번 주 잘한 매매", "원칙 어긴 매매", "감정이 흔든 순간", "다음 주 규칙 하나"), repeat = "W6")),
            theme(today = 0xFF6A1B9A, cell = "tint"),
            layout(tabs = listOf("calendar", "memos", "money", "stats", "plaza", "settings"), calendar = "todayWork,memos,summary"),
            cards(memo = "notebooks,search"),
            widget(alpha = 80, theme = "dark", memo = true)),
        recipe("배당 모으기 달력", "배당 들어오는 날을 챙기고 다시 넣는 습관",
            "누구에게 좋아요: 배당으로 현금 흐름을 만드는 분\n적용하면 바뀌는 것:\n· 원칙 3개, 일지 칸 3개\n· 매달 15일 배당 확인 메모 틀\n· 달력 칸 진하게, 오늘 초록\n· 위젯에 메모 한 줄\n주의할 점: 배당은 회사 사정에 따라 바뀔 수 있어요",
            setOf(INVEST), 0xFF00695C, 0xFFFFD54F, "💰",
            invest(listOf("받은 배당은 다시 넣는다", "배당만 보고 사지 않는다", "배당을 줄인 곳은 이유를 다시 본다"),
                listOf("받은 배당", "다시 넣은 곳", "메모")),
            memo(tpl("배당 확인", "💰", listOf("들어온 배당 적기", "다시 넣기", "세금 뗀 것 확인"), repeat = "M15")),
            theme(today = 0xFF00695C, cell = "fill"),
            layout(calendar = "todayWork,summary,memos"),
            widget(alpha = 90, memo = true), corners("round")),
        recipe("흔들리지 않는 투자 판", "뉴스·소문에 바로 움직이지 않게 잡아 주는 원칙",
            "누구에게 좋아요: 급하게 사고팔아 후회한 적이 있는 분\n적용하면 바뀌는 것:\n· 원칙 5개, 일지 칸 4개\n· 충동 멈춤 체크·월말 돌아보기 메모 틀\n· 차분한 회색 달력, 각진 모서리\n· 위젯은 메모 없이 깔끔하게\n주의할 점: 종목을 알려 주는 판이 아니에요",
            setOf(INVEST), 0xFF37474F, 0xFF90A4AE, "🧘",
            invest(listOf("뉴스 보고 하루 기다린다", "남의 말만 듣고 사지 않는다", "모르는 것은 사지 않는다", "잃어도 되는 돈만 넣는다", "밤에는 사고팔지 않는다"),
                listOf("무엇을 보고 움직였나", "하루 뒤 생각", "감정 점수", "지킨 원칙")),
            memo(tpl("충동 멈춤 체크", "✋", listOf("왜 지금인가", "하루 뒤에도 같은가", "원칙에 맞나", "비중은 괜찮나")),
                tpl("월말 돌아보기", "🔍", listOf("잘한 것", "아쉬운 것", "다음 달 원칙"), repeat = "M28")),
            theme(today = 0xFF37474F, sat = 0xFF455A64, sun = 0xFF455A64, cell = "dot"),
            layout(calendar = "summary,todayWork,memos"),
            widget(alpha = 100, memo = false), corners("square")),
    )

    private val all: List<Recipe> = shift + dayWork + hourlyWork + fieldWork + shopWork + investWork

    @Test fun showcasesPassWithNoWarning() {
        assertEquals(30, all.size)
        for (r in all) {
            val read = RecipeCodec.read(RecipeCodec.write(r))
            assertEquals(r.title, RecipeCodec.write(read), RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(read))))
            assertEquals("${r.title}: 조각이 다 읽혀요", r.pieces.size, read.pieces.size)
            val lines = RecipeLint.check(read)
            assertTrue("${r.title}: ${lines.filter { it.level != RecipeLint.Level.OK }.map { it.text }}", lines.all { it.level == RecipeLint.Level.OK })
            assertTrue("${r.title}: 금액이 그대로 나가요", InputExtract.found(read).isEmpty())
            assertTrue("${r.title}: 이름 같은 말", RecipeLint.nameLike(read).isEmpty())
            // A showcase shows many pieces working together.
            assertTrue("${r.title}: 조각 ${read.pieces.size}개", read.pieces.size >= 4)
        }
        assertEquals("제목이 겹치지 않아요", all.size, all.map { NameSkeleton.of(it.title) }.toSet().size)
        for (s in WorkStyle.entries) assertTrue("$s 보여주기 5개", all.count { it.styles == setOf(s) } == 5)
        if (System.getenv("BAMNAT_WRITE_STARTERS") == "1") {
            val out = all.joinToString(",\n", "[\n", "\n]\n") { r -> """{"showcase":true,"recipe":${RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(r)))}}""" }
            File("../docs/recipes").mkdirs()
            File("../docs/recipes/showcase-recipes.json").writeText(out)
        }
    }
}
