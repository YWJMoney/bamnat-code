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
 * [RCP-40] 시작 레시피 (10 for each kind of work) and 공개 자료 레시피 (결정 D105: each with its source in the description, no
 * 공공누리 4유형). Every one passes the same reader and 올리기 전 검사 a user's would. With BAMNAT_WRITE_STARTERS=1 this writes
 * `docs/recipes/starter-recipes.json`, which the operator pastes into 관리자 › 광장 › 나눔 판 › 공식·공개 자료.
 */
class StarterRecipesTest {
    private fun look(vararg items: Map<String, Any?>) = Piece(PieceKind.SHIFT_LOOK, mapOf("items" to items.toList()))
    private fun day(name: String, role: String, label: String, color: Long, emoji: String? = null) =
        mapOf("name" to name, "role" to role, "label" to label, "color" to color, "emoji" to emoji)
    private fun theme(sat: Long? = null, sun: Long? = null, holiday: Long? = null, today: Long? = null, cell: String? = null) =
        Piece(PieceKind.CAL_THEME, mapOf("sat" to sat, "sun" to sun, "holiday" to holiday, "today" to today, "cell" to cell).filterValues { it != null })
    private fun alarms(vararg byRole: Pair<String, List<Long>>, snooze: Long? = null, mission: String? = null, gradual: Boolean? = null) =
        Piece(PieceKind.ALARMS, mapOf("byRole" to byRole.map { mapOf("role" to it.first, "before" to it.second) }, "snooze" to snooze,
            "mission" to mission, "gradual" to gradual).filterValues { it != null })
    private fun memo(vararg items: Map<String, Any?>) = Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to items.toList()))
    private fun tpl(title: String, emoji: String?, check: List<String> = emptyList(), body: String? = null, repeat: String? = null) =
        mapOf("title" to title, "emoji" to emoji, "check" to check, "body" to body, "repeat" to repeat).filterValues { it != null }
    private fun recipe(title: String, summary: String, desc: String, styles: Set<WorkStyle>, a: Long, b: Long, emoji: String, vararg pieces: Piece,
        inputs: List<Input> = emptyList(), dir: Int = 2) =
        Recipe(title, summary, desc, styles, Cover(a, b, dir, emoji), inputs = inputs, pieces = pieces.toList())

    private val wage = Input("i1", "시급", InputKind.MONEY, default = 10_320L, min = 0.0, max = 1_000_000.0, hint = "처음 값은 2026 최저임금이에요. 내 시급으로 바꿔 주세요")
    private val dayRate = Input("i1", "일당", InputKind.MONEY, min = 0.0, max = 5_000_000.0, required = true, hint = "내 일당을 적어 주세요")

    private val shift = listOf(
        recipe("야간 파랑", "밤 근무가 한눈에 보이는 파란 근무색", "누구에게 좋아요: 야간이 많은 교대 근무자\n적용하면 바뀌는 것: 주간·야간·휴무의 글자와 색, 이모지\n주의할 점: 근무 이름이 다르면 역할(주간·야간)로 맞춰요",
            setOf(SHIFT), 0xFF0D47A1, 0xFF4FC3F7, "🌙", look(day("주간", "day", "주", 0xFFF9A825, "☀️"), day("야간", "night", "야", 0xFF1A237E, "🌙"), day("휴무", "off", "휴", 0xFF9E9E9E))),
        recipe("간호 3교대 알람", "데이·이브닝·나이트 근무 전 알람 두 번", "누구에게 좋아요: 3교대 간호사\n적용하면 바뀌는 것: 근무 90분·60분 전 알람을 더해요(지금 알람은 그대로)\n주의할 점: 알람은 더하기만 해요",
            setOf(SHIFT), 0xFF00897B, 0xFF80CBC4, "⏰", alarms("day" to listOf(90L, 60L), "eve" to listOf(90L, 60L), "night" to listOf(120L, 60L), snooze = 5L)),
        recipe("인수인계 메모 틀", "근무 끝에 빠뜨리기 쉬운 인수인계 목록", "누구에게 좋아요: 교대로 일을 넘겨받는 분\n적용하면 바뀌는 것: 메모 쓰기의 '틀에서 시작'에 인수인계 틀이 생겨요\n주의할 점: 메모는 만들어지지 않아요",
            setOf(SHIFT, DAY), 0xFF5E35B1, 0xFFB39DDB, "📋", memo(tpl("인수인계", "📋", listOf("특이 사항", "남은 일", "물품·장비 상태", "연락 올 곳", "다음 근무자 확인")))),
        recipe("파스텔 근무색", "눈이 편한 연한 색의 4교대 꾸미기", "누구에게 좋아요: 진한 색이 부담스러운 분\n적용하면 바뀌는 것: 주·야·비·휴 글자와 색\n주의할 점: 달력 칸은 옅게 칠해요",
            setOf(SHIFT), 0xFFF8BBD0, 0xFFB2EBF2, "🌸", look(day("주간", "day", "주", 0xFFF06292), day("야간", "night", "야", 0xFF7986CB), day("비번", "off", "비", 0xFF90A4AE), day("휴무", "off", "휴", 0xFFA5D6A7)), theme(cell = "tint")),
        recipe("또렷한 근무색", "글자가 잘 보이는 진한 대비 색", "누구에게 좋아요: 달력을 빨리 훑어보는 분, 큰 글씨를 쓰는 분\n적용하면 바뀌는 것: 근무 색과 달력 칸 진하게\n주의할 점: 밝은 화면에서 맞춘 색이에요",
            setOf(SHIFT, DAY, HOURLY), 0xFF212121, 0xFFFFC107, "🔆", look(day("주간", "day", "주", 0xFFE65100), day("야간", "night", "야", 0xFF311B92), day("휴무", "off", "휴", 0xFF424242)), theme(cell = "fill")),
        recipe("야간 전 낮잠 알람", "야간 근무 날 오후에 낮잠 깨우기", "누구에게 좋아요: 야간 전에 잠깐 자는 분\n적용하면 바뀌는 것: 야간 근무 4시간·2시간 전 알람, 끄려면 계산 문제\n주의할 점: 미션은 지금 미션이 없을 때만 바뀌어요",
            setOf(SHIFT), 0xFF263238, 0xFF5C6BC0, "😴", alarms("night" to listOf(240L, 120L), mission = "math")),
        recipe("4조 2교대 이모지", "주간 해, 야간 달, 비번 집", "누구에게 좋아요: 4조 2교대 공장·소방·경찰\n적용하면 바뀌는 것: 근무에 이모지가 붙어요\n주의할 점: 글자는 그대로예요",
            setOf(SHIFT), 0xFFFF7043, 0xFF3949AB, "🏭", look(day("주간", "day", "주", 0xFFFF8F00, "☀️"), day("야간", "night", "야", 0xFF283593, "🌙"), day("비번", "off", "비", 0xFF78909C, "🏠"), day("휴무", "off", "휴", 0xFF66BB6A, "🌿"))),
        recipe("토·일 강조 달력", "주말과 공휴일이 또렷한 달력", "누구에게 좋아요: 주말 근무가 많은 분\n적용하면 바뀌는 것: 토·일·공휴일 글자색과 오늘 동그라미\n주의할 점: 어두운 화면에서는 밝혀서 그려요",
            setOf(SHIFT, DAY, HOURLY, FIELD, BUSINESS, INVEST), 0xFF1565C0, 0xFFC62828, "📅", theme(sat = 0xFF1565C0, sun = 0xFFC62828, holiday = 0xFFAD1457, today = 0xFF2E7D32)),
        recipe("근무 전 준비물", "출근 전에 챙길 것 체크", "누구에게 좋아요: 준비물이 많은 교대 근무자\n적용하면 바뀌는 것: 메모 틀 '근무 준비물'\n주의할 점: 항목은 메모에서 고칠 수 있어요",
            setOf(SHIFT, DAY), 0xFF6D4C41, 0xFFBCAAA4, "🎒", memo(tpl("근무 준비물", "🎒", listOf("사원증", "도시락·물", "충전기", "근무복", "상비약")))),
        recipe("24시간 당직 꾸미기", "당직·비번이 잘 구별되는 색", "누구에게 좋아요: 격일제·당직 근무자\n적용하면 바뀌는 것: 당직·비번 글자와 색, 당직 전 알람\n주의할 점: 이름이 다르면 역할로 맞춰요",
            setOf(SHIFT), 0xFFB71C1C, 0xFF607D8B, "🚒", look(day("당직", "long", "당", 0xFFC62828, "🚨"), day("비번", "off", "비", 0xFF607D8B)), alarms("long" to listOf(90L))),
    )

    private val dayWork = listOf(
        recipe("출근 알람 둘", "출근 80분·50분 전 알람", "누구에게 좋아요: 매일 같은 시간에 출근하는 분\n적용하면 바뀌는 것: 근무 전 알람 두 개를 더해요\n주의할 점: 지금 알람은 그대로예요",
            setOf(DAY), 0xFF00796B, 0xFF4DB6AC, "⏰", alarms("day" to listOf(80L, 50L), snooze = 5L, gradual = true)),
        recipe("재택·출근 이모지", "재택과 출근 날이 한눈에", "누구에게 좋아요: 재택을 섞어 일하는 분\n적용하면 바뀌는 것: 근무에 이모지\n주의할 점: '재택' 근무가 없으면 주간에만 붙어요",
            setOf(DAY), 0xFF3F51B5, 0xFF8BC34A, "🏡", look(day("출근", "day", "출", 0xFF3F51B5, "🏢"), day("재택", "other", "재", 0xFF689F38, "🏡"))),
        recipe("주간 회의 메모", "매주 월요일 회의 메모 틀", "누구에게 좋아요: 정기 회의가 있는 직장인\n적용하면 바뀌는 것: 매주 월요일 반복 메모 틀\n주의할 점: 틀에서 시작할 때 날짜가 월요일로 맞춰져요",
            setOf(DAY, SHIFT), 0xFF455A64, 0xFF90A4AE, "📝", memo(tpl("주간 회의", "📝", listOf("지난주 한 일", "이번 주 할 일", "막힌 것", "공유할 것"), repeat = "W1"))),
        recipe("월말 정리 메모", "매달 25일 월말 체크", "누구에게 좋아요: 월말 보고·정산이 있는 분\n적용하면 바뀌는 것: 매달 25일 반복 메모 틀\n주의할 점: 메모는 만들어지지 않아요",
            setOf(DAY, SHIFT, HOURLY), 0xFF8D6E63, 0xFFD7CCC8, "🗂️", memo(tpl("월말 정리", "🗂️", listOf("근무 시간 확인", "급여 명세서 확인", "연차 남은 날 확인", "경비 영수증 제출"), repeat = "M25"))),
        recipe("차분한 사무실 달력", "회색·남색 차분한 색", "누구에게 좋아요: 차분한 화면을 좋아하는 분\n적용하면 바뀌는 것: 토·일 색, 칸 옅게\n주의할 점: 근무 색은 그대로예요",
            setOf(DAY), 0xFF37474F, 0xFF78909C, "🖋️", theme(sat = 0xFF3949AB, sun = 0xFFB71C1C, cell = "tint")),
        recipe("칠하지 않는 달력", "칸 색 없이 글자만 깔끔하게", "누구에게 좋아요: 달력을 깔끔하게 보고 싶은 분\n적용하면 바뀌는 것: 근무 날 칸을 칠하지 않아요\n주의할 점: 근무 글자는 그대로 보여요",
            setOf(DAY, SHIFT, HOURLY), 0xFFFAFAFA, 0xFF9E9E9E, "⬜", theme(cell = "dot")),
        recipe("점심 약속 틀", "점심 약속 메모 빨리 쓰기", "누구에게 좋아요: 점심 약속이 잦은 분\n적용하면 바뀌는 것: 메모 틀 '점심 약속'\n주의할 점: 연락처는 적지 않아요",
            setOf(DAY), 0xFFFF8A65, 0xFFFFE0B2, "🍱", memo(tpl("점심 약속", "🍱", body = "장소: \n시간: \n함께: "))),
        recipe("출장 준비 체크", "출장 전 챙길 것", "누구에게 좋아요: 출장이 있는 분\n적용하면 바뀌는 것: 메모 틀 '출장 준비'\n주의할 점: 항목은 고칠 수 있어요",
            setOf(DAY, SHIFT), 0xFF0277BD, 0xFF81D4FA, "🧳", memo(tpl("출장 준비", "🧳", listOf("교통편 예약", "숙소", "출장 신청서", "자료·노트북", "영수증 봉투")))),
        recipe("흔들어 깨우는 알람", "출근 알람을 흔들어서 끄기", "누구에게 좋아요: 알람을 끄고 다시 자는 분\n적용하면 바뀌는 것: 출근 60분 전 알람, 끄기 미션 흔들기\n주의할 점: 미션이 이미 있으면 바뀌어요",
            setOf(DAY, SHIFT), 0xFFE53935, 0xFFFFCDD2, "📳", alarms("day" to listOf(60L), mission = "shake")),
        recipe("초록 일근 꾸미기", "근무는 초록, 쉬는 날은 회색", "누구에게 좋아요: 평일 근무자\n적용하면 바뀌는 것: 근무·휴무 색\n주의할 점: 이름이 다르면 역할로 맞춰요",
            setOf(DAY), 0xFF2E7D32, 0xFFA5D6A7, "🌱", look(day("근무", "day", "근", 0xFF2E7D32, "🌱"), day("휴무", "off", "휴", 0xFF9E9E9E))),
    )

    private fun hourly(title: String, summary: String, desc: String, a: Long, b: Long, emoji: String, vararg extra: Pair<String, Any?>) =
        recipe(title, summary, desc, setOf(HOURLY), a, b, emoji, Piece(PieceKind.HOURLY_RULES, mapOf("wage" to Ref.Input("i1")) + extra), inputs = listOf(wage))

    private val hourlyWork = listOf(
        hourly("편의점 알바 기본", "주 15시간 넘는 편의점 알바", "누구에게 좋아요: 편의점·카페 주 15시간 이상 알바\n적용하면 바뀌는 것: 주휴수당 받음, 휴게 무급, 법정 휴게 빼기\n주의할 점: 시급은 내 금액으로 바꿔요",
            0xFF43A047, 0xFFFFEB3B, "🏪", "weeklyPay" to true, "breakPay" to "UNPAID", "legalBreaks" to true, "tax" to "NONE"),
        hourly("주 15시간 미만 알바", "주휴수당이 없는 짧은 알바", "누구에게 좋아요: 주 15시간이 안 되는 알바\n적용하면 바뀌는 것: 주휴수당 없음\n주의할 점: 근무가 늘면 다시 켜요",
            0xFF8BC34A, 0xFFDCE775, "⏳", "weeklyPay" to false, "breakPay" to "UNPAID"),
        hourly("3.3% 떼는 알바", "사업소득으로 3.3%를 떼는 곳", "누구에게 좋아요: 급여에서 3.3%를 떼는 알바\n적용하면 바뀌는 것: 세금 3.3% 떼기\n주의할 점: 4대보험이면 4대보험을 골라요",
            0xFF5D4037, 0xFFFFCC80, "🧾", "tax" to "WITHHOLDING_3_3", "weeklyPay" to true),
        hourly("4대보험 알바", "4대보험을 내는 알바", "누구에게 좋아요: 4대보험에 가입된 알바\n적용하면 바뀌는 것: 세금 4대보험\n주의할 점: 실제 공제는 명세서로 맞춰 보세요",
            0xFF1E88E5, 0xFF90CAF9, "🛡️", "tax" to "INSURANCE_4", "weeklyPay" to true),
        hourly("야간 알바 (5인 이상)", "밤 10시~6시 50% 더 받는 곳", "누구에게 좋아요: 5인 이상 매장의 야간 알바\n적용하면 바뀌는 것: 5인 이상, 야간 가산 50%\n주의할 점: 5인 미만이면 가산이 없을 수 있어요",
            0xFF1A237E, 0xFF7986CB, "🌃", "five" to true, "night" to 50L, "weeklyPay" to true),
        hourly("휴게도 시급 주는 곳", "휴게시간도 돈을 받는 알바", "누구에게 좋아요: 휴게시간이 유급인 곳\n적용하면 바뀌는 것: 휴게 유급\n주의할 점: 근로계약서를 확인하세요",
            0xFF00ACC1, 0xFFB2EBF2, "☕", "breakPay" to "PAID"),
        hourly("단시간 가산 받는 곳", "단시간 근로자 연장 가산", "누구에게 좋아요: 정한 시간보다 더 일하면 가산을 받는 알바\n적용하면 바뀌는 것: 단시간 가산 켜기\n주의할 점: 5인 이상 사업장 기준이에요",
            0xFF6A1B9A, 0xFFCE93D8, "➕", "partTime" to true, "five" to true),
        recipe("알바 출근 체크", "오픈 전 할 일 목록", "누구에게 좋아요: 오픈 알바\n적용하면 바뀌는 것: 메모 틀 '오픈 체크'\n주의할 점: 가게 이름은 적지 않아요",
            setOf(HOURLY, BUSINESS), 0xFFFFA000, 0xFFFFECB3, "🔑", memo(tpl("오픈 체크", "🔑", listOf("포스 켜기", "시재 확인", "냉장 온도", "청소", "유통기한")))),
        recipe("알바 두 곳 꾸미기", "두 근무지를 색으로 구별", "누구에게 좋아요: 알바를 두 곳 하는 분\n적용하면 바뀌는 것: 근무 두 개의 글자·색·이모지\n주의할 점: 이름이 다르면 역할로 맞춰요",
            setOf(HOURLY), 0xFFEF6C00, 0xFF5C6BC0, "🔀", look(day("오전", "day", "오", 0xFFEF6C00, "🥐"), day("저녁", "eve", "저", 0xFF5C6BC0, "🍺"))),
        recipe("알바 30분 전 알람", "출근 30분 전에 한 번", "누구에게 좋아요: 가까운 곳에서 알바하는 분\n적용하면 바뀌는 것: 근무 30분 전 알람\n주의할 점: 알람은 더하기만 해요",
            setOf(HOURLY), 0xFFD81B60, 0xFFF8BBD0, "🔔", alarms("day" to listOf(30L), "eve" to listOf(30L), "night" to listOf(30L))),
    )

    private fun field(title: String, summary: String, desc: String, a: Long, b: Long, emoji: String, values: Map<String, Any?>) =
        recipe(title, summary, desc, setOf(FIELD), a, b, emoji, Piece(PieceKind.FIELD_SHEET, values), inputs = if (values["dayRate"] != null) listOf(dayRate) else emptyList())

    private val fieldWork = listOf(
        field("인테리어 일당", "공수로 세는 인테리어 현장", "누구에게 좋아요: 일당(공수)으로 받는 인테리어 기술자\n적용하면 바뀌는 것: 공수 칩, 끝난 시각 규칙, 작업·경비 분류\n주의할 점: 일당은 내 금액으로 적어요",
            0xFF795548, 0xFFFFCC80, "🛠️", mapOf("mode" to "DAY_RATE", "dayRate" to Ref.Input("i1"), "chips" to listOf(0.5, 1.0, 1.5), "ends" to listOf(mapOf("end" to "17:00", "units" to 1.0), mapOf("end" to "19:00", "units" to 1.5)),
                "kinds" to listOf("목공", "도장", "타일", "철거"), "costs" to listOf("자재비", "공구", "유류비", "식대"))),
        field("전기 공사 일당", "전기 현장의 공수와 경비", "누구에게 좋아요: 전기 기술자\n적용하면 바뀌는 것: 공수 칩, 작업·경비 분류\n주의할 점: 일당은 내 금액으로",
            0xFFFFB300, 0xFF424242, "⚡", mapOf("mode" to "DAY_RATE", "dayRate" to Ref.Input("i1"), "chips" to listOf(0.5, 1.0, 2.0), "kinds" to listOf("배선", "조명", "분전반", "점검"), "costs" to listOf("자재비", "공구", "주차비"))),
        field("건별 디자인 외주", "일감마다 금액·선금·잔금", "누구에게 좋아요: 건별로 받는 프리랜서\n적용하면 바뀌는 것: 건별형, 작업 분류\n주의할 점: 금액은 일감마다 적어요",
            0xFF7E57C2, 0xFFE1BEE7, "🎨", mapOf("mode" to "PER_JOB", "chips" to listOf(1.0), "kinds" to listOf("로고", "상세페이지", "영상 편집", "수정"), "costs" to listOf("폰트·소재", "구독", "외주"))),
        field("배달 플랫폼", "배달 건수와 운행 시간", "누구에게 좋아요: 배민·쿠팡이츠 라이더\n적용하면 바뀌는 것: 플랫폼형, 경비 분류\n주의할 점: 주급 정산일은 앱에서 정해요",
            0xFF26A69A, 0xFF80DEEA, "🛵", mapOf("mode" to "PLATFORM", "chips" to listOf(1.0), "kinds" to listOf("배민", "쿠팡이츠", "요기요"), "costs" to listOf("유류비", "보험료", "수리비", "통신비"))),
        field("대리운전", "콜 건수와 이동 경비", "누구에게 좋아요: 대리운전 기사\n적용하면 바뀌는 것: 플랫폼형, 경비 분류\n주의할 점: 수수료는 앱에서 정해요",
            0xFF37474F, 0xFFFFD54F, "🚗", mapOf("mode" to "PLATFORM", "chips" to listOf(1.0), "kinds" to listOf("카카오", "대리 콜", "지인"), "costs" to listOf("택시비", "보험료", "프로그램비"))),
        field("용접 현장", "반공·한공·공반", "누구에게 좋아요: 용접·배관 기술자\n적용하면 바뀌는 것: 공수 칩과 끝난 시각 규칙\n주의할 점: 일당은 내 금액으로",
            0xFFE64A19, 0xFF263238, "🔥", mapOf("mode" to "DAY_RATE", "dayRate" to Ref.Input("i1"), "chips" to listOf(0.5, 1.0, 1.5), "ends" to listOf(mapOf("end" to "17:00", "units" to 1.0), mapOf("end" to "21:00", "units" to 2.0)),
                "kinds" to listOf("용접", "배관", "제관"), "costs" to listOf("가스", "용접봉", "보호구"))),
        recipe("현장 안전 체크", "작업 전 안전 확인", "누구에게 좋아요: 현장 근로자\n적용하면 바뀌는 것: 메모 틀 '안전 체크'\n주의할 점: 현장 규칙이 먼저예요",
            setOf(FIELD), 0xFFF9A825, 0xFF212121, "🦺", memo(tpl("안전 체크", "🦺", listOf("안전모·안전화", "추락 방지", "전기 차단 확인", "소화기 위치", "작업 허가")))),
        recipe("일감 견적 메모", "견적 낼 때 물어볼 것", "누구에게 좋아요: 견적을 자주 내는 분\n적용하면 바뀌는 것: 메모 틀 '견적'\n주의할 점: 연락처·금액은 메모에만 적어요",
            setOf(FIELD), 0xFF0097A7, 0xFFB2EBF2, "📐", memo(tpl("견적 문의", "📐", listOf("현장 위치", "면적·수량", "일정", "자재 누가", "선금 비율")))),
        recipe("현장 달력 진하게", "일한 날이 진하게 보이는 달력", "누구에게 좋아요: 일한 날을 세는 분\n적용하면 바뀌는 것: 근무 칸 진하게, 일요일 색\n주의할 점: 어두운 화면에서는 밝혀요",
            setOf(FIELD), 0xFF6D4C41, 0xFFFFAB91, "🧱", theme(sun = 0xFFC62828, cell = "fill")),
        field("청소 건별", "청소 일감 건별 관리", "누구에게 좋아요: 입주·이사 청소\n적용하면 바뀌는 것: 건별형, 작업·경비 분류\n주의할 점: 금액은 일감마다 적어요",
            0xFF4FC3F7, 0xFFE1F5FE, "🧽", mapOf("mode" to "PER_JOB", "chips" to listOf(1.0), "kinds" to listOf("입주 청소", "이사 청소", "정기 청소"), "costs" to listOf("세제", "장비", "교통비"))),
    )

    private fun shop(title: String, summary: String, desc: String, a: Long, b: Long, emoji: String, values: Map<String, Any?>) =
        recipe(title, summary, desc, setOf(BUSINESS), a, b, emoji, Piece(PieceKind.SHOP, values))

    private val week6 = listOf("TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    private val shopWork = listOf(
        shop("동네 카페", "월요일 쉬는 카페 영업 틀", "누구에게 좋아요: 동네 카페 사장님\n적용하면 바뀌는 것: 영업일·시간, 마감 체크, 경비 분류\n주의할 점: 카드 수수료는 내 가맹점 기준으로 고쳐요",
            0xFF6D4C41, 0xFFD7CCC8, "☕", mapOf("open" to week6, "opens" to "09:00", "closes" to "21:00", "holidaysClosed" to false, "closing" to listOf("머신 청소", "우유 정리", "시재 마감", "쓰레기"), "costs" to listOf("원두", "우유", "컵·포장", "임대료"))),
        shop("한식 음식점", "점심·저녁 장사 영업 틀", "누구에게 좋아요: 음식점 사장님\n적용하면 바뀌는 것: 영업일·시간, 마감 체크, 경비 분류\n주의할 점: 간이과세면 '간이과세' 레시피도 보세요",
            0xFFD84315, 0xFFFFCCBC, "🍲", mapOf("open" to week6, "opens" to "11:00", "closes" to "22:00", "closing" to listOf("가스 밸브", "냉장고 온도", "식재료 발주", "시재 마감"), "costs" to listOf("식재료", "주류", "가스비", "인건비", "배달앱수수료"))),
        shop("미용실", "예약 위주 미용실", "누구에게 좋아요: 1인 미용실\n적용하면 바뀌는 것: 영업일(화 휴무)·시간, 마감 체크, 경비\n주의할 점: 손님 이름은 적지 않아요",
            0xFFAD1457, 0xFFF8BBD0, "💇", mapOf("open" to listOf("MONDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"), "opens" to "10:00", "closes" to "20:00", "closing" to listOf("도구 소독", "수건 세탁", "약제 정리"), "costs" to listOf("약제", "소모품", "임대료"))),
        shop("온라인 쇼핑몰", "주말 쉬는 택배 발송", "누구에게 좋아요: 스마트스토어 운영자\n적용하면 바뀌는 것: 평일 영업, 정산 기간, 경비\n주의할 점: 정산일은 마켓마다 달라요",
            0xFF1976D2, 0xFFBBDEFB, "📦", mapOf("open" to listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"), "opens" to "09:00", "closes" to "18:00", "holidaysClosed" to true, "settleDays" to 7L, "closing" to listOf("송장 출력", "택배 접수", "재고 기록"), "costs" to listOf("택배비", "포장재", "광고비", "마켓수수료"))),
        shop("가게 마감 체크", "문 닫기 전 확인 목록", "누구에게 좋아요: 마감을 혼자 하는 사장님\n적용하면 바뀌는 것: 메모 틀 '마감 체크'\n주의할 점: 영업 시간은 그대로예요",
            0xFF455A64, 0xFFCFD8DC, "🔒", mapOf("open" to WEEK_DAYS, "closing" to listOf("불 끄기", "문단속", "포스 마감", "현금 금고", "보안 설정"))),
        shop("주말 장사", "토·일만 여는 가게", "누구에게 좋아요: 주말 매장·플리마켓\n적용하면 바뀌는 것: 영업일 토·일\n주의할 점: 공휴일은 따로 정해요",
            0xFFFF7043, 0xFFFFE0B2, "🎪", mapOf("open" to listOf("SATURDAY", "SUNDAY"), "opens" to "11:00", "closes" to "19:00")),
        recipe("오픈 준비 체크", "가게 열기 전 할 일", "누구에게 좋아요: 오픈을 맡은 분\n적용하면 바뀌는 것: 메모 틀 '오픈 준비'\n주의할 점: 항목은 고칠 수 있어요",
            setOf(BUSINESS), 0xFFFFCA28, 0xFFFFF8E1, "🌅", memo(tpl("오픈 준비", "🌅", listOf("문 열기", "포스 켜기", "시재 확인", "재료 확인", "간판 켜기")))),
        recipe("매달 세금 날짜", "부가세·원천세 날 메모", "누구에게 좋아요: 세금 날을 자주 잊는 사장님\n적용하면 바뀌는 것: 매달 10일 반복 메모 틀\n주의할 점: 실제 신고일은 국세청 안내를 따르세요",
            setOf(BUSINESS), 0xFF00695C, 0xFFB2DFDB, "🗓️", memo(tpl("원천세·4대보험 확인", "🧾", listOf("원천세 신고", "4대보험 납부", "카드 매출 확인"), repeat = "M10"))),
        recipe("매출 달력 꾸미기", "장사한 날이 진하게", "누구에게 좋아요: 매출을 달력으로 보는 사장님\n적용하면 바뀌는 것: 칸 진하게, 토·일 색\n주의할 점: 숫자는 그대로예요",
            setOf(BUSINESS), 0xFF2E7D32, 0xFFC8E6C9, "💹", theme(sat = 0xFF1565C0, sun = 0xFFC62828, cell = "fill")),
        shop("배달 전문점", "배달앱 위주 영업", "누구에게 좋아요: 배달 전문 매장\n적용하면 바뀌는 것: 영업 시간, 경비(배달앱 수수료 등)\n주의할 점: 수수료율은 앱마다 달라요",
            0xFF00ACC1, 0xFFE0F7FA, "🛵", mapOf("open" to WEEK_DAYS, "opens" to "11:00", "closes" to "23:30", "costs" to listOf("식재료", "포장재", "배달앱수수료", "배달대행비", "광고비"))),
    )

    private fun invest(title: String, summary: String, desc: String, a: Long, b: Long, emoji: String, rules: List<String>, journal: List<String> = emptyList()) =
        recipe(title, summary, desc, setOf(INVEST), a, b, emoji, Piece(PieceKind.INVEST, mapOf("rules" to rules.map { mapOf("text" to it) }, "journal" to journal)))

    private val investWork = listOf(
        invest("분할 매수 원칙", "한 번에 다 사지 않기", "누구에게 좋아요: 처음 투자하는 분\n적용하면 바뀌는 것: 투자 원칙 4개 더하기\n주의할 점: 종목 추천이 아니에요. 판단은 본인 몫이에요",
            0xFF1565C0, 0xFF90CAF9, "🧩", listOf("세 번에 나눠 산다", "산 이유를 적고 산다", "한 종목은 전체의 20%까지", "빚내서 사지 않는다")),
        invest("손절 원칙", "잃을 때 멈추는 규칙", "누구에게 좋아요: 손실이 커진 경험이 있는 분\n적용하면 바뀌는 것: 원칙 3개\n주의할 점: 기준은 내 상황에 맞게 고쳐요",
            0xFFC62828, 0xFFFFCDD2, "🛑", listOf("정한 손절 기준을 지킨다", "물타기 전에 이유를 다시 쓴다", "하루에 한 번만 본다")),
        invest("장기 적립", "매달 같은 날 같은 금액", "누구에게 좋아요: 적립식 투자자\n적용하면 바뀌는 것: 원칙과 일지 칸\n주의할 점: 금액은 앱에서 정해요",
            0xFF2E7D32, 0xFFC8E6C9, "🌳", listOf("매달 같은 날 산다", "떨어져도 계획대로", "1년에 한 번 비중을 맞춘다"), listOf("이번 달 적립", "비중 확인", "느낀 점")),
        invest("매매 일지 칸", "매매 후 돌아보는 칸", "누구에게 좋아요: 일지를 쓰고 싶은 분\n적용하면 바뀌는 것: 원칙 1개, 메모 틀 '투자일지'\n주의할 점: 종목 이름은 메모에만 적어요",
            0xFF6A1B9A, 0xFFE1BEE7, "📒", listOf("매매 후 일지를 쓴다"), listOf("산 이유", "판 이유", "감정 점수", "다음에 바꿀 것")),
        invest("뉴스에 흔들리지 않기", "뉴스 보고 바로 사지 않기", "누구에게 좋아요: 급하게 사고파는 분\n적용하면 바뀌는 것: 원칙 3개\n주의할 점: 종목 추천이 아니에요",
            0xFF455A64, 0xFFB0BEC5, "📰", listOf("뉴스 보고 하루 기다린다", "남의 추천만으로 사지 않는다", "모르는 것은 사지 않는다")),
        invest("배당 모으기", "배당 날짜를 챙기는 원칙", "누구에게 좋아요: 배당 투자자\n적용하면 바뀌는 것: 원칙과 일지 칸\n주의할 점: 배당은 바뀔 수 있어요",
            0xFF00897B, 0xFFB2DFDB, "💰", listOf("배당금은 다시 투자한다", "배당만 보고 사지 않는다"), listOf("받은 배당", "재투자", "메모")),
        invest("코인 원칙", "변동이 큰 자산의 규칙", "누구에게 좋아요: 코인 투자자\n적용하면 바뀌는 것: 원칙 4개\n주의할 점: 원금 손실이 클 수 있어요",
            0xFFF9A825, 0xFF212121, "🪙", listOf("잃어도 되는 돈만 넣는다", "레버리지는 쓰지 않는다", "밤에는 거래하지 않는다", "거래소를 나눠 쓴다")),
        invest("월말 돌아보기", "한 달 투자 돌아보기 칸", "누구에게 좋아요: 월마다 점검하는 분\n적용하면 바뀌는 것: 일지 칸, 원칙 1개\n주의할 점: 수익률은 앱이 계산해요",
            0xFF5C6BC0, 0xFFC5CAE9, "🔍", listOf("월말에 한 번 돌아본다"), listOf("잘한 것", "아쉬운 것", "다음 달 원칙")),
        recipe("수익·손실 달력", "오른 날 빨강, 내린 날 파랑", "누구에게 좋아요: 달력으로 수익을 보는 분\n적용하면 바뀌는 것: 칸 진하게\n주의할 점: 색은 한국 증시처럼 빨강이 오름이에요",
            setOf(INVEST), 0xFFD32F2F, 0xFF1976D2, "📈", theme(cell = "fill", today = 0xFF6A1B9A)),
        recipe("매매 전 체크", "사기 전에 확인할 것", "누구에게 좋아요: 충동 매매를 줄이고 싶은 분\n적용하면 바뀌는 것: 메모 틀 '매매 전 체크'\n주의할 점: 종목 추천이 아니에요",
            setOf(INVEST), 0xFF37474F, 0xFF80CBC4, "✅", memo(tpl("매매 전 체크", "✅", listOf("산 이유를 한 줄로", "손절 기준", "비중 20% 넘지 않나", "하루 기다렸나")))),
    )

    /** 공개 자료 (결정 D105): laws and public notices only, each named with its source; amounts the receiver checks. */
    private val publicData = listOf(
        hourly("2026 최저임금 알바", "2026년 최저임금을 처음 값으로", "출처: 최저임금위원회·고용노동부 「2026년 적용 최저임금 고시」(시간급, 앱의 처음 값으로 들어가요)\n누구에게 좋아요: 최저임금을 받는 알바\n적용하면 바뀌는 것: 시급 처음 값, 주휴수당 받음\n주의할 점: 내 시급이 더 많으면 바꿔요",
            0xFF1565C0, 0xFF64B5F6, "⚖️", "weeklyPay" to true, "legalBreaks" to true, "breakPay" to "UNPAID"),
        hourly("법정 휴게시간", "4시간에 30분, 8시간에 1시간", "출처: 근로기준법 제54조(휴게)\n누구에게 좋아요: 휴게시간을 빼고 계산하고 싶은 분\n적용하면 바뀌는 것: 법정 휴게 자동 빼기, 휴게 무급\n주의할 점: 실제 휴게가 다르면 끄세요",
            0xFF00796B, 0xFF80CBC4, "🕰️", "legalBreaks" to true, "breakPay" to "UNPAID"),
        hourly("야간 근로 50% 가산", "밤 10시~새벽 6시 50% 더", "출처: 근로기준법 제56조(연장·야간 및 휴일 근로)\n누구에게 좋아요: 5인 이상 사업장 야간 알바\n적용하면 바뀌는 것: 5인 이상, 야간 가산 50%\n주의할 점: 5인 미만은 법정 가산이 없어요",
            0xFF283593, 0xFF9FA8DA, "🌃", "five" to true, "night" to 50L),
        hourly("5인 미만 사업장", "가산수당 규정이 적용되지 않는 곳", "출처: 근로기준법 제11조, 시행령 제7조 별표 1\n누구에게 좋아요: 직원이 5명 안 되는 곳의 알바\n적용하면 바뀌는 것: 5인 미만, 야간 가산 없음\n주의할 점: 주휴수당·최저임금은 그대로 받아요",
            0xFF6D4C41, 0xFFBCAAA4, "🏠", "five" to false, "night" to 0L, "weeklyPay" to true),
        shop("간이과세 음식점", "업종별 부가가치율 15%", "출처: 부가가치세법 시행령 제111조 제2항(업종별 부가가치율, 음식점업 15%)\n누구에게 좋아요: 간이과세 음식점·소매점\n적용하면 바뀌는 것: 간이과세, 부가가치율 15%\n주의할 점: 업종이 다르면 율도 달라요",
            0xFFEF6C00, 0xFFFFE0B2, "🧮", mapOf("open" to WEEK_DAYS, "simpleTax" to true, "valueAdded" to 15L)),
        shop("간이과세 서비스업", "업종별 부가가치율 40%", "출처: 부가가치세법 시행령 제111조 제2항(업종별 부가가치율, 그 밖의 서비스업 40%)\n누구에게 좋아요: 간이과세 미용·학원 등 서비스업\n적용하면 바뀌는 것: 간이과세, 부가가치율 40%\n주의할 점: 업종 분류는 사업자등록증으로 확인하세요",
            0xFF6A1B9A, 0xFFE1BEE7, "🧮", mapOf("open" to WEEK_DAYS, "simpleTax" to true, "valueAdded" to 40L)),
    )

    private val all: List<Pair<Recipe, Boolean>> = (shift + dayWork + hourlyWork + fieldWork + shopWork + investWork).map { it to false } + publicData.map { it to true }

    @Test fun startersPassTheReaderAndTheChecks() {
        assertEquals(66, all.size)
        for ((r, _) in all) {
            val read = RecipeCodec.read(RecipeCodec.write(r))
            assertEquals(r.title, RecipeCodec.write(read), RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(read))))
            val lines = RecipeLint.check(read)
            assertTrue("${r.title}: ${lines.filter { it.level == RecipeLint.Level.ERROR }.map { it.text }}", RecipeLint.ready(lines))
            assertTrue("${r.title}: 금액이 그대로 나가요", InputExtract.found(read).isEmpty())
            assertTrue("${r.title}: 이름 같은 말", RecipeLint.nameLike(read).isEmpty())
        }
        assertEquals("제목이 겹치지 않아요", all.size, all.map { NameSkeleton.of(it.first.title) }.toSet().size)
        for (s in WorkStyle.entries) assertTrue("$s 시작 레시피 10개 이상", all.count { !it.second && s in it.first.styles } >= 10)
        if (System.getenv("BAMNAT_WRITE_STARTERS") == "1") {
            val out = all.joinToString(",\n", "[\n", "\n]\n") { (r, pub) -> """{"publicData":$pub,"recipe":${RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(r)))}}""" }
            File("../docs/recipes").mkdirs()
            File("../docs/recipes/starter-recipes.json").writeText(out)
        }
    }
}
