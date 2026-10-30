package com.bamnat.core.recipe

/**
 * Words for the piece catalog's keys and choices, as the app says them: the studio's forms, 달라진 점 (RecipeDiff) and the
 * admin's 나눔 판 read these, so a field is called the same everywhere. A key's word can depend on its piece (memo = 메모 한 줄 in
 * 위젯, 메모 줄 in 달력 모양): [key] takes the piece first.
 */
object RecipeWords {
    val KEYS = mapOf(
    "items" to "목록", "name" to "이름", "role" to "어떤 날", "label" to "글자", "color" to "색", "emoji" to "이모지",
    "today" to "오늘 표시", "sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일", "cell" to "근무 날 칸",
    "byRole" to "근무마다 알람", "before" to "시작 몇 분 전", "snooze" to "다시 울림(분)", "ring" to "울리는 시간(분)", "gradual" to "점점 크게",
    "vibrate" to "진동", "mission" to "끄기 미션", "title" to "제목", "check" to "체크 목록", "body" to "내용", "repeat" to "반복",
    "five" to "5인 이상 사업장", "weeklyPay" to "주휴수당", "weekStart" to "주 시작 요일", "breakPay" to "휴게시간", "legalBreaks" to "법정 휴게 자동 빼기",
    "night" to "야간 가산(%)", "partTime" to "단시간 가산", "rounding" to "시간 반올림", "tax" to "세금", "mode" to "받는 방식",
    "chips" to "공수 칩", "ends" to "끝난 시각 규칙", "end" to "끝난 시각", "units" to "공수", "kinds" to "작업 분류", "costs" to "경비 분류",
    "open" to "영업일", "opens" to "여는 시각", "closes" to "닫는 시각", "holidaysClosed" to "공휴일 쉬기", "cardFee" to "카드 수수료(%)",
    "settleDays" to "카드 정산(일 뒤)", "simpleTax" to "간이과세", "valueAdded" to "부가가치율(%)", "closing" to "마감 체크", "rules" to "투자 원칙",
    "text" to "원칙", "journal" to "일지 칸", "alpha" to "배경 보이기(%)", "theme" to "밝기", "memo" to "메모 한 줄", "dim" to "배경 흐리게(%)",
    "corners" to "모서리", "wage" to "시급", "dayRate" to "일당",
    "asks" to "입력 칸", "type" to "칸 종류", "choices" to "고를 것", "size" to "칸 높이", "badge" to "근무 표시", "date" to "날짜 자리",
    "grid" to "칸 선", "hours" to "근무 시간", "lunar" to "음력", "spend" to "쓴 돈", "order" to "줄 순서", "dynamic" to "기기 색 따르기",
    "accent" to "강조색", "textStep" to "글자 크기", "compact" to "촘촘하게", "clock12" to "12시간제", "tap" to "날짜를 누르면", "hold" to "날짜를 길게 누르면",
    "dayOrder" to "하루 화면 순서", "flow" to "오늘의 흐름", "rest" to "휴식 점검", "payPeriod" to "통계를 급여 기간으로", "tabs" to "아래 탭 순서",
    "calendar" to "달력 화면 카드", "stats" to "통계 카드", "money" to "가계부 카드", "bg" to "배경 그림", "head" to "머리 그림", "widget" to "위젯 배경", "icons" to "근무 그림",
    "setup" to "회사 설정",
)

    val PICKS = mapOf(
    "fill" to "진하게", "tint" to "옅게", "dot" to "점만", "none" to "없음", "math" to "계산 문제", "shake" to "흔들기",
    "UNPAID" to "무급", "PAID" to "유급", "AS_WORK" to "근무로 셈", "NONE" to "떼지 않음", "WITHHOLDING_3_3" to "3.3%", "INSURANCE_4" to "4대보험",
    "HOURLY_FIRST" to "시간마다", "AT_END" to "끝에 반올림", "AT_END_UP" to "끝에 올림", "AT_END_DOWN" to "끝에 버림",
    "DAY_RATE" to "일당(공수)", "PER_JOB" to "건별", "PLATFORM" to "플랫폼", "system" to "휴대폰 따라", "light" to "밝게", "dark" to "어둡게",
    "round" to "둥글게", "soft" to "보통", "square" to "각지게",
    "short" to "낮게", "normal" to "보통", "tall" to "높게", "huge" to "아주 높게", "big" to "크게", "small" to "작게", "line" to "선·띠",
    "center" to "가운데", "left" to "왼쪽 위", "large" to "크게", "night" to "야간 근무 때 어둡게", "preview" to "아래에 짧게", "sheet" to "하루 화면",
    "week" to "그 주만", "menu" to "빠른 메뉴", "memo" to "바로 메모", "open" to "하루 열기", "text" to "글", "num" to "숫자", "star" to "별점", "pick" to "고르기",
    "MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목", "FRIDAY" to "금", "SATURDAY" to "토", "SUNDAY" to "일",
) + ShiftRole.entries.associate { it.key to it.label }
}


private val BY_PIECE = mapOf(
    "calView.memo" to "칸의 메모 줄", "calView.text" to "칸 글씨", "appFeel.theme" to "밝기", "widget.memo" to "메모 한 줄",
    "appFeel.textStep" to "글자 크기 단계", "layout.calendar" to "달력 화면 카드", "photoLook.widget" to "위젯 배경 그림",
)

/** A field's word in [kind] (its piece first, then the catalog's), or the key itself. */
fun RecipeWords.key(kind: PieceKind?, key: String): String = kind?.let { BY_PIECE["${it.key}.$key"] } ?: KEYS[key] ?: key
