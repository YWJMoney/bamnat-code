package com.bamnat.core.guide

import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.words

/**
 * [GUIDE-01] Where a feature opens in the app. The app maps each place to its screen (a tab, a screen of
 * its own, or a group of settings with [FeatureRoute.focus] as the row to point at), so a new place
 * fails to compile until it has one.
 */
enum class Place {
    CALENDAR, TODAY, MONEY, AUTO_RECORD, MEMOS, STATS,
    PAY, PATTERN, SHIFT_TYPES, EXTRA_KINDS, WORK_STYLE, CALENDARS, CALENDAR_EDIT, SHOP_DAYS, COMBINED, OWED,
    SLEEP, DUTY_ROSTER, HEALTH_REPORT, TEAMS, YEAR, ATTENDANCE_IMPORT, COMMUTE, CALENDAR_IMPORT, SHARE, RECEIVE,
    DIAGNOSTICS, INQUIRY, FEATURES, PRO,
    /** [ONBOARD-04] 추천 기능 한 번에 켜기. */
    RECOMMENDED,
    // [LEAVE-06] 1.22 묶음 D: the 연차 원장 of the calendar on screen.
    LEAVE,
    // [EASY-08][OBS-02] 1.23 묶음 N: 내 정보 and 밤낮이 한 일.
    PROFILE, JOURNAL,
    SETTINGS_WORK, SETTINGS_ALARM, SETTINGS_NOTICES, SETTINGS_PAY, SETTINGS_CALENDAR,
    SETTINGS_DATA, SETTINGS_PRIVACY,
    ;

    /** A group of settings: [FeatureRoute.focus] names its row. */
    val isSettings: Boolean get() = name.startsWith("SETTINGS_")
}

/** [GUIDE-01] The deep link of a feature: a place and, inside it, the row or tool to open ("offers", "백업 저장"). */
data class FeatureRoute(val place: Place, val focus: String? = null)

/**
 * [GUIDE-05] Whether a feature can be turned off, and where its switch is kept:
 * [CALENDAR] is the app's own switch for one calendar (a card in 통계 or 돈), [APP] the app's own switch
 * for every calendar (the guide itself, widget lines), [ELSEWHERE] a switch that was there before
 * (매출 없으면 휴무, 출퇴근 자동 기록, 결제 알림 읽기…) which the 기능 켜고 끄기 screen shows instead of copying.
 */
enum class Off { CALENDAR, APP, ELSEWHERE }

/**
 * [GUIDE-03] The good moments for a one-line tip. The app works out which hold now ([GuideMoments]),
 * the guide picks one feature for them.
 */
enum class Moment {
    /** A shop wrote its first 하루 마감. */
    FIRST_DAY_CLOSE,
    /** The pay engine's 급여일, and the three days after. */
    PAYDAY,
    /** A part-timer's week reached 15 hours. */
    HOURLY_15H,
    /** A day worker is still on the clock after the end time. */
    PAST_END_TIME,
    /** A field calendar has its first job. */
    FIRST_JOB,
    /** A field calendar has money still owed. */
    UNPAID_JOB,
    /** A shift worker came off a night shift. */
    AFTER_NIGHT,
    /** An investor calendar has its first trade. */
    FIRST_TRADE,
}

/**
 * [GUIDE-01] One thing the user can do in the app: its [id] (stable, kept in the guide's state), the
 * requirement it came from, its name as the screen says it, what it does in a line, the kinds of work it
 * is for ([styles], empty for every kind), where it is ([where], the way there as the screens name it),
 * its deep link, other words people use for it, whether and how it can be turned off, a feature it
 * belongs to ([under]: off when that one is), and a tip for its good moment.
 */
data class AppFeature(
    val id: String,
    val req: String,
    val name: String,
    val what: String,
    val styles: Set<WorkStyle>,
    val where: String,
    val route: FeatureRoute,
    val keywords: List<String> = emptyList(),
    val off: Off? = null,
    val under: String? = null,
    val moment: Moment? = null,
    val tip: String? = null,
) {
    /** For this kind of work; a main calendar from before kinds of work were asked is a shift calendar. */
    fun fits(style: WorkStyle?): Boolean = styles.isEmpty() || (style ?: WorkStyle.SHIFT) in styles
    val canTurnOff: Boolean get() = off != null
}

/**
 * [GUIDE-01] Every user-facing feature, in the order they are suggested. To add one (or a new kind of
 * work, like 투자): add its [AppFeature] here with a [Place] the app can open, list the new kind in the
 * features it fits, and give the kind its checklist in [GuideChecklist.ORDER]. A new [Place] also needs
 * its screen in the app's `FeatureLinks`; an [Off.CALENDAR] card needs `FeatureCard(id)` around it, and an
 * [Off.ELSEWHERE] switch needs its adapter in the app's `FeatureSwitches`.
 */
object FeatureRegistry {
    private val SHIFT = WorkStyle.SHIFT
    private val DAY = WorkStyle.DAY
    private val HOURLY = WorkStyle.HOURLY
    private val FIELD = WorkStyle.FIELD
    private val SHOP = WorkStyle.BUSINESS
    private val INVEST = WorkStyle.INVEST
    private val PAID = setOf(SHIFT, DAY, HOURLY)
    private val ALL = emptySet<WorkStyle>()

    /** The tab that holds hours and money for [style], as the bottom bar names it. */
    private fun tab(style: WorkStyle) = style.words().tab
    private const val DAY_SHEET = "달력 › 날짜 누르기"

    private fun f(
        id: String, req: String, name: String, what: String, styles: Set<WorkStyle>, where: String, route: FeatureRoute,
        vararg keywords: String, off: Off? = null, under: String? = null, moment: Moment? = null, tip: String? = null,
    ) = AppFeature(id, req, name, what, styles, where, route, keywords.toList(), off, under, moment, tip)

    private fun at(place: Place, focus: String? = null) = FeatureRoute(place, focus)

    val all: List<AppFeature> = listOf(
        // The guide itself [GUIDE-02][GUIDE-03].
        f("guide.checklist", "GUIDE-02", "이 달력으로 할 수 있는 것", "새 달력이나 일하는 방식을 바꾼 달력에서 처음 며칠 쓸 만한 기능을 골라 보여 줘요",
            ALL, "달력 맨 위 카드", at(Place.FEATURES), "처음", "시작", "체크리스트", "안내 카드", "할 수 있는 것", off = Off.APP),
        f("guide.tips", "GUIDE-03", "알맞은 때 한 줄 안내", "급여일·첫 하루 마감처럼 알맞은 때 쓸 만한 기능을 하루 한 번까지 한 줄로 알려 줘요",
            ALL, "달력 맨 위 한 줄", at(Place.FEATURES), "팁", "안내", "알려 주기", "추천", off = Off.APP),
        // [ONBOARD-03][ONBOARD-04] The recommended features and what they need, on one screen.
        f("settings.recommended", "ONBOARD-04", "추천 기능 한 번에 켜기", "켜 둬도 괜찮은 알림과 필요한 허용을 한 화면에서 켜고, 끄는 곳을 알려 줘요",
            ALL, "설정 › 기능 켜고 끄기 › 추천 기능 한 번에 켜기", at(Place.RECOMMENDED),
            "추천 기능", "한 번에 켜기", "모두 켜기", "권한 한 번에", "허용", "기본 설정", "끄는 법"),

        // Shift work.
        f("shift.pattern", "SET-01", "근무 주기", "주주야야휴휴처럼 돌아가는 순서와 시작일을 정하면 달력이 채워져요",
            setOf(SHIFT), "설정 › 내 근무 › 근무 주기", at(Place.PATTERN), "순서", "패턴", "로테이션", "교대 순서", "시작일"),
        f("shift.premium", "SHFT-01", "근무 종류별 수당", "근무 종류마다 ‘이 근무 수당 n%’와 야간 여부를 정해 급여에 더해요",
            setOf(SHIFT), "설정 › 내 근무 › 근무 종류와 시간", at(Place.SHIFT_TYPES), "근무 코드", "야간 수당", "가산율", "수당"),
        f("alarm.check", "REL-13", "알람 점검", "다음 알람, 꺼진 권한, 배터리 최적화를 하나씩 확인하고 그 자리에서 고쳐요",
            PAID, "설정 › 알람 › 알람 점검", at(Place.DIAGNOSTICS), "알람이 안 울려요", "늦게 울려요", "시험 알람", "권한"),
        // [PROV-06][PROV-07] 확실한 알람 (프로, 1.20.0).
        f("alarm.mission", "PROV-06", "끄기 미션", "알람을 끌 때 계산 문제를 풀거나 휴대폰을 흔들어야 꺼져요. 근무마다 다르게 고를 수 있어요",
            PAID, "설정 › 알람 › 끄기 미션", at(Place.SETTINGS_ALARM, "끄기 미션"), "미션", "계산 문제", "흔들기", "못 일어나요", "다시 잠들어요"),
        f("alarm.wakeCheck", "PROV-07", "일어났는지 확인", "알람을 끄고 몇 분 뒤 ‘일어났나요?’를 묻고, 답이 없으면 한 번 더 울려요",
            PAID, "설정 › 알람 › 일어났는지 확인", at(Place.SETTINGS_ALARM, "일어났는지 확인"), "일어났나요", "기상 확인", "또 잠들", "두 번 울림"),
        // [SET-05] 1.23.0: every shift's alarm moved at once, for the week the bus comes earlier.
        f("alarm.moveAll", "SET-05", "모든 근무 알람 한 번에 옮기기", "근무마다 맞춘 알람을 한꺼번에 5분 단위로 앞당기거나 늦춰요",
            setOf(SHIFT, DAY), "설정 › 알람 › 모든 근무 알람 한 번에 옮기기", at(Place.SETTINGS_ALARM, "모든 근무 알람 한 번에 옮기기"),
            "알람 앞당기기", "알람 늦추기", "한꺼번에", "출근 시간 바뀜", "일찍 깨우기"),
        f("share.link", "SHR-01", "근무표 공유", "이번 달을 이미지로 보내거나 앱 없이 열리는 링크를 만들어요",
            ALL, "달력 › 공유 (좁은 화면은 ⋮ › 근무표 공유)", at(Place.SHARE), "공유", "링크", "카톡", "가족", "동료", "보내기"),
        f("share.receive", "UX-03", "동료 근무표 받기", "받은 링크를 넣고 내 달력과 바꿔 보며 같이 쉬는 날을 찾아요",
            ALL, "달력 › ⋮ › 동료 근무표 받기", at(Place.RECEIVE), "받은 링크", "바꿔 보기", "같이 쉬는 날", "남편", "아내"),
        // [PROV-04][FEAT-15][PROV-05] 프로, 1.20.0.
        f("share.moreLinks", "PROV-04", "공유 링크 여러 개", "달력마다 링크를 5개까지 따로 만들어 보낼 것과 기간을 다르게 하고, 180일·1년도 골라요",
            ALL, "달력 › 공유 › 링크 › + 새 링크", at(Place.SHARE), "링크 여러 개", "링크 추가", "1년 링크", "180일", "기간 늘리기"),
        f("share.feed", "FEAT-15", "캘린더 구독 주소", "근무표 링크를 구글·아이폰·아웃룩 캘린더가 구독하게 해서 바뀐 근무가 따라오게 해요",
            ALL, "달력 › 공유 › 링크 › 캘린더 구독 주소", at(Place.SHARE), "구독", "ICS", "아이폰 캘린더", "아웃룩", "캘린더 구독"),
        f("share.rosterPdf", "PROV-05", "근무표 PDF", "한 달이나 1년 근무표를 A4로 인쇄할 수 있는 PDF로 만들어요",
            ALL, "달력 › 공유 › ○월 PDF·○년 PDF", at(Place.SHARE), "PDF", "인쇄", "출력", "A4", "근무표 뽑기"),
        f("shift.firstCard", "SHFT-02", "이번 달 근무 카드", "근무 코드별 횟수, 야간 횟수, 예상 급여를 한 카드로 보여 줘요",
            setOf(SHIFT), "${tab(SHIFT)} › ○월 근무", at(Place.STATS), "코드별 횟수", "야간 횟수", "근무 횟수", off = Off.CALENDAR),
        f("shift.swap", "SHFT-03", "근무 교환·대근 기록", "날짜마다 누구와 바꿨는지 적고 달마다 모아 봐요",
            setOf(SHIFT), "$DAY_SHEET › 바꾼 사람", at(Place.TODAY), "교환", "대근", "바꿈", "근무 바꾸기", "맞교대"),
        f("shift.swapCard", "SHFT-03", "교환·대근 모아 보기", "이번 달 바꾼 근무와 대근을 사람별로 모아 보여 줘요",
            setOf(SHIFT), "${tab(SHIFT)} › 교환·대근", at(Place.STATS), "교환 모아", "대근 횟수", off = Off.CALENDAR),
        // [IMPORT-03] 1.22: an 엑셀·CSV roster too, for wards and for part-timers' irregular rosters.
        f("shift.roster", "SHFT-06", "한 달 근무 넣기", "그 달 달력 위 키패드로 한 달을 바로 넣고, 듀티표 사진·엑셀·CSV·붙여 넣은 글에서 내 줄을 찾아 같은 달력에 채워요",
            setOf(SHIFT, HOURLY), "설정 › 내 근무 › 한 달 근무 넣기", at(Place.DUTY_ROSTER), "듀티표", "듀티표 넣기", "한 달 근무", "직접 입력", "공유로 받기", "근무표 사진", "글자 인식", "간호사", "OCR",
            "근무표 파일", "엑셀 근무표", "CSV", "불규칙 근무표", "편의점 근무표"),
        f("shift.teams", "CREW-01", "모든 조 보기", "3조 2교대·4조 2교대처럼 조가 있는 근무에서 모든 조의 근무를 날짜별로 보고, 쉬는 조를 찾아 대근을 부탁해요",
            setOf(SHIFT), "달력 › 모든 조 (또는 도구 › 모든 조 보기)", at(Place.TEAMS), "모든 조", "전체 조", "조별", "다른 조", "A조", "B조", "대근 구하기", "쉬는 조"),
        f("shift.dutyTyping", "NURS-01", "이번 달 전부 넣기", "근무표를 보며 D·E·N·OFF를 누르면 다음 날로 넘어가며 한 달을 넣고, 역순·나이트 뒤 쉬는 날·연속 근무를 알려 줘요",
            setOf(SHIFT), "달력 › 도구 › 이번 달 전부 넣기 (빠른 입력 › 이번 달 전부 넣기)", at(Place.CALENDAR),
            "연달아", "키패드", "한 달 입력", "듀티 입력", "간호사", "나이트", "역순", "근무표 점검"),
        f("shift.restRules", "REST-01", "근무표 점검 기준", "휴식 11시간·나이트 뒤 쉬는 날·연속 나이트·연속 근무·한 달 나이트 기준을 병원·회사에 맞추고, 이달 나이트 수와 시간외를 함께 봐요",
            setOf(SHIFT), "달력 › 휴식 점검 카드 › 기준 바꾸기 (설정 › 알림과 잠 › 근무표 점검 기준)", at(Place.SETTINGS_NOTICES, "근무표 점검 기준"),
            "휴식 점검", "근무표 점검", "N-O-D", "나오데", "이브데이", "연속 나이트", "나이트 개수", "11시간", "점검 끄기", off = Off.ELSEWHERE),
        f("shift.health", "SHFT-07", "연간 건강 지표", "야간 비율, 가장 길게 이어진 야간, 11시간 못 쉰 날을 1년으로 봐요",
            setOf(SHIFT), "${tab(SHIFT)} › ○년 건강 지표", at(Place.HEALTH_REPORT), "야간 비율", "연속 야간", "건강", "휴식",
            off = Off.CALENDAR, moment = Moment.AFTER_NIGHT,
            tip = "야간 근무 수고했어요. ‘연간 건강 지표’에서 야간 비율과 쉰 시간을 볼 수 있어요"),
        f("shift.nightDark", "SHFT-05", "야간 근무 중 어둡게", "야간 근무 시간에는 화면을 저절로 어둡게 바꿔요",
            setOf(SHIFT), "설정 › 화면과 글자 › 테마", at(Place.SETTINGS_CALENDAR, "테마"), "다크 모드", "어둡게", "야간 모드", "눈부심"),
        f("sleep.log", "SLP-16", "수면 기록", "잠든 시간과 깬 시간을 적고 근무별 평균을 봐요",
            setOf(SHIFT), "달력 › 오늘의 흐름 › 수면 기록", at(Place.SLEEP), "잠", "수면", "잠들었어요", "깼어요", "낮잠"),
        f("today.flow", "SLP-14", "오늘의 흐름", "달력 맨 위에 지금 할 일 하나와 잘 시간·다음 근무를 보여 줘요",
            PAID, "설정 › 알림과 잠 › 오늘의 흐름 보기", at(Place.SETTINGS_NOTICES, "오늘의 흐름 보기"), "잘 시간", "카페인", "낮잠", "오늘 카드",
            off = Off.ELSEWHERE),

        // Pay, for every kind of work paid by the hour or the month.
        f("pay.calc", "PAY-01", "급여 계산", "근무와 시간으로 이번 달 예상 급여를 계산하고 명세서와 맞춰 봐요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여", at(Place.PAY), "월급", "급여", "시급", "주휴수당", "명세서", "수당", "기본급"),
        f("pay.publicGrade", "JOB-02", "공무원 봉급표", "공무원·경찰·소방은 계급·호봉으로 봉급, 시간외(월 57시간)·야간·휴일 수당, 정근수당·명절휴가비를 채워요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 내 급여 값 › 공무원 봉급표", at(Place.PAY, "items"), "공무원", "경찰", "소방", "호봉", "봉급표", "시간외", "57시간", "정근수당", "명절휴가비"),
        f("pay.gap", "SHFT-04", "월급날 비교", "급여일에 들어온 급여와 계산한 급여의 차이를 알려 줘요",
            PAID, "${tab(SHIFT)} › ○월 월급날 비교", at(Place.STATS, "pay.gap"), "월급날", "비교", "입금", "차이", "들어온 돈", "실수령", "세후",
            off = Off.CALENDAR, moment = Moment.PAYDAY,
            tip = "오늘은 급여일이에요. 들어온 급여를 적으면 계산과 얼마나 다른지 ‘월급날 비교’로 알려 드려요"),
        // [PROV-01][PROV-02] 프로, 1.20.0.
        f("pay.report", "PROV-01", "급여 리포트", "한 달이나 1년 급여를 계산 풀이와 날짜별 근무까지 PDF로 남기거나 CSV로 엑셀에서 열어요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 도구 › 급여 리포트", at(Place.PAY, "tools"), "리포트", "급여 PDF", "CSV", "엑셀", "근거", "급여 내역"),
        f("stats.compare", "PROV-02", "지난달·작년 비교", "근무시간·야간·휴일·추가 근무·급여를 지난달, 작년 같은 달과 나란히 보고 올해를 달마다 봐요",
            PAID, "${tab(SHIFT)} › 지난달·작년 비교", at(Place.STATS), "비교", "지난달", "작년", "올해 근무", "연간 리포트"),
        // [LEAVE-01..06][PAYX-18] 1.22 묶음 D: 연차 원장.
        f("leave.ledger", "LEAVE-06", "연차 원장", "입사일로 연차를 법대로 세고, 달력에 넣은 연차·받은 날·이월·소멸을 한 줄씩 보여 줘요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 연차 카드 › 연차 화면", at(Place.LEAVE), "연차", "남은 연차", "연차 계산", "소멸", "이월", "입사일", "회계연도"),
        f("leave.promotion", "LEAVE-03", "연차 사용 촉진 알림", "연차가 없어지기 6개월 전·2개월 전에 쓸 예정을 빼고도 남는 날을 알려 줘요",
            PAID, "연차 화면 › 쓰라고 알려 주기", at(Place.LEAVE), "연차 알림", "소멸 알림", "사용 촉진", "연차 촉진", off = Off.ELSEWHERE),
        f("leave.payout", "LEAVE-05", "미사용 연차수당", "못 쓴 연차가 돈으로 얼마인지 셈하고, 받으면 원장에 정산으로 남겨요",
            PAID, "연차 화면 › 못 쓴 연차, 돈으로는", at(Place.LEAVE), "연차수당", "미사용 연차", "연차 정산", "수당"),
        f("pay.compLeave", "PAYX-18", "보상휴가", "추가 근무를 돈 대신 쉬는 시간으로 받으면 가산까지 더해 연차 화면에 쌓아요",
            setOf(SHIFT, DAY), "$DAY_SHEET › 추가 근무 › 돈 대신 보상휴가로 받기", at(Place.TODAY), "보상휴가", "대체휴가", "휴가로 받기", "근로기준법 57조"),
        // [PAYX-38..45][LEAVE-10..13] 1.23 묶음 K.
        f("pay.slipAudit", "PAYX-41", "내 월급 맞나요", "명세서를 줄마다 앱 계산과 맞춰 회사가 적게 준 줄과 앱에 빠진 줄을 나누고, 공제 5줄을 실수령 예상과 나란히 봐요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 명세서와 맞춰 보기 › 금액 비교하기 › 줄마다 맞춰 보기", at(Place.PAY), "명세서 대조", "월급 맞나", "덜 받은", "공제", "국민연금", "1350"),
        f("pay.whatIf", "PAYX-38", "만약에 계산", "기본급·시급을 올리거나 야간을 더 하거나 월 소정시간이 다르면 이 달이 얼마인지 바로 봐요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 도구 › 만약에 계산", at(Place.PAY, "tools"), "만약에", "인상", "야간 더", "소정시간", "209시간"),
        f("pay.yearEnd", "PAYX-42", "연말정산 미리보기", "1월부터 근무로 내년 2월에 돌려받을지 더 낼지 어림해요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 도구 › 연말정산 미리보기", at(Place.PAY, "tools"), "연말정산", "환급", "결정세액", "13월의 월급"),
        f("pay.counsel", "PAYX-45", "상담용 묶음", "급여 PDF·CSV, 주별 근무시간, 연차 원장을 한 번에 묶어 노무사나 1350 상담에 보여 줘요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 도구 › 급여 리포트 › 상담용 묶음", at(Place.PAY, "tools"), "노무사", "상담", "고용노동부", "체불", "묶음"),
        f("leave.promotionLog", "LEAVE-11", "연차 사용 촉진 기록", "회사가 보낸 촉진 통보와 내가 정한 날을 날짜로 남기고, 법대로 했는지 수당을 받을 수 있는지 알려 줘요",
            PAID, "연차 화면 › 회사의 사용 촉진 기록", at(Place.LEAVE), "촉진 통보", "연차 촉진", "61조", "미사용 수당"),
        f("leave.plan", "LEAVE-13", "없어지기 전에 쓰는 법", "없어질 연차를 공휴일·쉬는 날 옆에 놓아 가장 길게 쉬는 날을 골라 줘요",
            PAID, "연차 화면 › 없어지기 전에 쓰는 법", at(Place.LEAVE), "연차 계획", "연휴", "징검다리", "연차 쓰기"),
        f("extra.kinds", "EXTRA-04", "잔업·특근·대근", "추가 근무의 이름과 가산 방식을 회사에 맞게 정하고 날짜마다 적어요",
            setOf(SHIFT, DAY), "설정 › 내 근무 › 연장 휴무일 근무 지정", at(Place.EXTRA_KINDS), "잔업", "특근", "대근", "연장", "야근", "휴일 근무"),

        // Day work.
        f("day.firstCard", "DAYW-02", "이번 달 한눈에", "이번 달 야근 시간, 연차 사용·잔여, 간이세액표로 계산한 예상 실수령을 한 카드로 보여 줘요",
            setOf(DAY), "${tab(DAY)} › ○월 한눈에", at(Place.STATS), "야근 시간", "연차 잔여", "실수령", "간이세액표", "부양가족", "자녀 수",
            off = Off.CALENDAR),
        f("day.leaveNow", "DAYW-03", "지금 퇴근", "누르면 퇴근 시각까지 남은 시간을 야근으로 적어요. 위젯에도 있어요",
            setOf(DAY), "$DAY_SHEET › 지금 퇴근", at(Place.TODAY), "퇴근", "야근 적기", "칼퇴", "퇴근 버튼",
            moment = Moment.PAST_END_TIME, tip = "퇴근 시간이 지났어요. ‘지금 퇴근’을 누르면 더 일한 시간이 야근으로 적혀요"),
        f("day.overtimeCap", "DAYW-05", "월 시간외 상한", "한 달 시간외 상한을 정하면 남은 시간과 넘었을 때 알려 줘요",
            setOf(DAY), "${tab(DAY)} › 시간외 상한", at(Place.STATS), "시간외", "상한", "야근 한도", "초과", off = Off.CALENDAR),
        // [LEAVE-04] 1.22: every kind of work that takes leave; 교대·알바 by the roster's own days off.
        f("day.bridges", "DAYW-04", "징검다리 연휴", "연차 하루이틀로 길게 쉬는 날을 12개월 안에서 찾아 줘요. 교대·알바는 근무표의 쉬는 날로 찾아요",
            PAID, "${tab(DAY)} › 징검다리 연휴 (연차 화면에도)", at(Place.STATS), "연휴", "연차 추천", "샌드위치", "휴가 계획", "길게 쉬기", off = Off.CALENDAR),
        f("day.attendance", "DAYW-06", "근태 엑셀 가져오기", "회사 근태 시스템의 엑셀·CSV를 읽어 늦은 출근·야근·연차를 적어요",
            setOf(DAY), "설정 › 내 근무 › 근태 엑셀 가져오기", at(Place.ATTENDANCE_IMPORT), "엑셀", "CSV", "근태", "xlsx", "출퇴근 기록"),
        // 1.32 [WTYP-14] 조사 대조 W11.
        f("day.attendanceCompare", "WTYP-14", "회사 근태와 내 기록 비교", "회사 근태 엑셀을 고르면 달력은 그대로 두고 출근·퇴근·연장이 내 기록과 다른 날과 회사가 덜 센 시간을 보여 주고 복사해요",
            setOf(DAY), "설정 › 내 근무 › 근태 엑셀 가져오기 › 내 기록과 비교", at(Place.ATTENDANCE_IMPORT), "근태 비교", "조퇴 처리", "회사 기록", "시프티", "출퇴근 차이",
            under = "day.attendance"),
        f("day.commute", "DAYW-07", "출퇴근 자동 기록", "회사에 도착하고 떠날 때를 휴대폰이 알아채 출퇴근을 적어요. 위치는 휴대폰 안에만 있어요",
            setOf(DAY), "설정 › 내 근무 › 출퇴근 자동 기록", at(Place.COMMUTE), "위치", "자동 출근", "자동 퇴근", "GPS", "지오펜스",
            off = Off.ELSEWHERE),

        // Part-time work.
        f("hourly.clock", "HRLY-08", "출근·퇴근 버튼", "누르면 실제 시각으로 적고, 근무 기록을 CSV로 내보내요",
            setOf(HOURLY), "$DAY_SHEET › 출근·퇴근", at(Place.TODAY), "출근", "퇴근", "출퇴근 기록", "근무 기록", "CSV"),
        f("hourly.attendance", "HRLY-01", "지각·조퇴·결근 한 번에", "하루 화면에서 한 번 눌러 지각·조퇴·결근을 적고, 대타는 대근으로 적어요",
            setOf(HOURLY), "$DAY_SHEET › 지각·조퇴·결근", at(Place.TODAY), "지각", "조퇴", "결근", "대타", "출결"),
        f("hourly.weeks", "HRLY-02", "주휴수당 주별 확인", "주마다 근무시간과 15시간을 채웠는지, 주휴 시간이 얼마인지 보여 줘요",
            setOf(HOURLY), "${tab(HOURLY)} › 주휴수당", at(Place.STATS), "주휴", "주휴수당", "15시간", "개근",
            off = Off.CALENDAR, moment = Moment.HOURLY_15H,
            tip = "이번 주 15시간을 채웠어요. ‘주휴수당 주별 확인’에서 주휴수당을 받을 수 있는지 봐요"),
        f("hourly.takeHome", "HRLY-04", "세금 방식과 예상 실수령", "없음·3.3%·4대보험 중에 골라 손에 쥐는 돈을 보여 줘요",
            setOf(HOURLY), "${tab(HOURLY)} › ○월 예상 급여 › 세금", at(Place.PAY), "세금", "3.3%", "4대보험", "실수령", "세후"),
        f("hourly.breaks", "HRLY-05", "휴게시간 자동 차감", "4시간 일하면 30분, 8시간이면 1시간 법정 휴게를 저절로 빼요",
            setOf(HOURLY), "${tab(HOURLY)} › ○월 예상 급여 › 휴게시간", at(Place.PAY), "휴게", "쉬는 시간", "브레이크"),
        f("hourly.tools", "HRLY-09", "알바 도구 카드", "두 알바 비교, 근로계약서, 그만두는 날 정리를 한 카드에 모아요",
            setOf(HOURLY), "${tab(HOURLY)} › 알바 도구", at(Place.STATS), "알바 도구", off = Off.CALENDAR),
        f("hourly.offers", "HRLY-09", "두 알바 비교하기", "시급·주휴·세금·교통비까지 따져 시간당 실제로 남는 돈을 나란히 봐요",
            setOf(HOURLY), "${tab(HOURLY)} › 알바 도구 › 두 알바 비교하기", at(Place.STATS, "offers"), "비교", "이직", "어느 알바", "계산기",
            under = "hourly.tools"),
        f("hourly.contract", "HRLY-10", "근로계약서 보관", "계약서 사진과 시급·근무 요일·계약 기간을 보관하고 끝나기 7일 전에 알려 줘요",
            setOf(HOURLY), "${tab(HOURLY)} › 알바 도구 › 근로계약서", at(Place.STATS, "contract"), "계약서", "근로계약", "계약 기간", "사진",
            under = "hourly.tools"),
        f("hourly.quit", "HRLY-11", "그만두는 날 정리", "마지막 주 주휴, 남은 연차수당, 퇴직금을 확인하고 그날 뒤 근무를 멈춰요",
            setOf(HOURLY), "${tab(HOURLY)} › 알바 도구 › 그만두는 날 정리", at(Place.STATS, "quit"), "퇴사", "그만두기", "퇴직금", "마지막 주",
            under = "hourly.tools"),

        // Field work.
        f("field.mode", "FLD-01", "일당형·건별형 고르기", "현장 달력을 공수(일당)로 셀지 건별 금액으로 셀지 정해요",
            setOf(FIELD), "달력 › ⋮ › 고치기 › 세는 방식", at(Place.CALENDAR_EDIT), "일당", "공수", "건별", "세는 방식"),
        f("field.units", "FLD-02", "공수 버튼", "날짜에 1·0.5·1.5 공수를 한 번에 적고 일당×공수로 계산해요",
            setOf(FIELD), "$DAY_SHEET › 공수", at(Place.TODAY), "공수", "반공", "일당", "품"),
        f("field.status", "FLD-03", "일감 상태", "가계약 → 확정 → 완료 → 입금, 취소까지 일감마다 상태를 바꿔요",
            setOf(FIELD), "$DAY_SHEET › 일감 › 상태", at(Place.TODAY), "가계약", "확정", "완료", "입금", "취소"),
        f("field.due", "FLD-09", "입금 약속일과 미수 알림", "일감마다 입금 약속일을 정하면 지나도 안 들어왔을 때 알려 줘요",
            setOf(FIELD), "$DAY_SHEET › 일감 › 입금 약속일", at(Place.TODAY), "약속일", "미수", "안 들어온 돈", "외상",
            moment = Moment.FIRST_JOB, tip = "일감을 적었어요. ‘입금 약속일’을 정해 두면 늦을 때 알려 드려요"),
        f("field.deposit", "FLD-06", "입금 알림을 미수 일감에 잇기", "은행 입금 알림이 오면 받을 돈이 남은 일감에 바로 맞춰요",
            setOf(FIELD), "가계부 › 입금 확인", at(Place.MONEY), "입금 확인", "미수금", "입금 매칭", off = Off.CALENDAR,
            moment = Moment.UNPAID_JOB, tip = "받을 돈이 남은 일감이 있어요. 입금 알림이 오면 ‘입금 확인’에서 한 번에 맞출 수 있어요"),
        f("field.statement", "FLD-05", "거래내역서 보내기", "거래처마다 한 달 일감을 정산 요청용 이미지로 만들어 보내요",
            setOf(FIELD), "${tab(FIELD)} › 거래처별 › 거래처 누르기", at(Place.STATS), "거래명세서", "정산", "거래처", "내역서"),
        f("field.year", "FLD-07", "연간 3.3%·경비 합계와 출역일", "종합소득세 신고에 쓸 올해 번 돈·뗀 3.3%·경비와 출역일을 모아 봐요",
            setOf(FIELD), "${tab(FIELD)} › 출역일", at(Place.STATS), "종합소득세", "5월", "출역일", "퇴직공제", "경비", off = Off.CALENDAR),
        f("field.dailyTax", "FLD-13", "일용직 세금", "이번 달 일당에서 떼는 일용근로소득세를 한 번에·날마다 받을 때로 보여 줘요",
            setOf(FIELD), "${tab(FIELD)} › 일용직 세금", at(Place.STATS), "원천징수", "일용근로", "세금", "2.7%", off = Off.CALENDAR),
        f("field.quote", "FLD-11", "견적서", "품목·수량·단가로 견적서 사진을 만들어 보내고, 좋다고 하면 일감으로 저장해요",
            setOf(FIELD), "${tab(FIELD)} › 견적서 › 새 견적서", at(Place.STATS, "quote"), "견적", "견적서", "부가세", "단가", off = Off.CALENDAR),
        f("field.tradeWage", "FLD-12", "공종별 평균 일당", "대한건설협회 시중노임단가(반기마다 저절로 새 값)를 보고 일당을 골라 넣어요",
            setOf(FIELD), "$DAY_SHEET › 공수 › 직접 › 평균 일당 보기", at(Place.TODAY), "노임단가", "평균 일당", "공종", "단가표"),
        f("field.receipt", "FLD-08", "영수증 사진", "경비를 적을 때 증빙과 영수증 사진을 함께 남겨요",
            setOf(FIELD, SHOP), "가계부 › 지출 적기 › 증빙", at(Place.MONEY), "영수증", "증빙", "사진", "경비"),

        // Shops.
        f("shop.days", "TYPE-03", "영업일·정기휴무", "영업시간, 매주 쉬는 요일, 매달 n째 요일, 공휴일 휴무를 달력에 바로 반영해요",
            setOf(SHOP), "설정 › 내 근무 › 영업일·정기휴무", at(Place.SHOP_DAYS), "영업시간", "정기휴무", "휴무일", "쉬는 요일"),
        f("shop.dayClose", "SHOP-01", "하루 마감", "카드·현금·계좌·배달앱으로 나눠 매출을 적고 합계를 봐요",
            setOf(SHOP), "$DAY_SHEET › 매출 적기", at(Place.TODAY), "마감", "매출 적기", "정산", "카드 매출", "현금 매출"),
        f("shop.cardDue", "SHOP-02", "카드 입금 예정", "수수료를 뗀 카드 매출이 언제 들어오는지 달력에 보이고 입금을 확인해요",
            setOf(SHOP), "설정 › 내 근무 › 영업일·정기휴무 › 카드 매출 입금", at(Place.SHOP_DAYS), "카드 입금", "수수료", "정산일", "카드사",
            moment = Moment.FIRST_DAY_CLOSE, tip = "첫 하루 마감을 했어요. 카드 매출이 언제 얼마 들어오는지 ‘카드 입금 예정’으로 볼 수 있어요"),
        f("shop.autoClose", "TYPE-04", "매출 없으면 휴무", "지난 영업일에 매출이 없으면 앱을 열 때 휴무로 바꿔요. 나중에 매출을 적으면 다시 영업",
            setOf(SHOP), "설정 › 내 근무 › 영업일·정기휴무", at(Place.SHOP_DAYS), "자동 휴무", "매출 없는 날", "휴무 자동", off = Off.ELSEWHERE),
        f("shop.cash", "SHOP-03", "현금 시재 맞추기", "있어야 할 현금과 센 현금의 차이를 알려 줘요",
            setOf(SHOP), "$DAY_SHEET › 매출 적기 › 현금 시재", at(Place.TODAY), "시재", "현금", "돈통", "차이"),
        f("shop.split", "SHOP-01", "결제 수단별 매출", "이번 달 매출을 카드·현금·계좌·배달앱으로 나눠 보여 줘요",
            setOf(SHOP), "${tab(SHOP)} › 결제 수단별", at(Place.STATS), "결제 수단", "카드 비율", "배달앱", off = Off.CALENDAR),
        f("shop.target", "SHOP-05", "목표 매출", "한 달 목표를 정하면 남은 영업일로 나눈 하루 목표를 보여 줘요",
            setOf(SHOP), "${tab(SHOP)} › 목표 매출", at(Place.STATS), "목표", "하루 목표", "매출 목표", off = Off.CALENDAR),
        f("money.incomeTax", "LEDG-26", "종합소득세 참고 정리", "올해 수입·적은 경비·떼인 3.3%를 모으고 5월 신고 기간을 알려 줘요(참고용, 세무 조언 아님)",
            setOf(FIELD, SHOP), "${tab(FIELD)} › ○년 종합소득세 참고", at(Place.STATS), "종합소득세", "5월", "3.3%", "경비", "신고", "세금", off = Off.CALENDAR),
        f("shop.vat", "SHOP-04", "부가세 예상", "반기 매출과 경비로 대략의 부가세를 보고 신고 달에 알려 줘요",
            setOf(SHOP), "${tab(SHOP)} › 부가세 예상", at(Place.STATS), "부가세", "세금", "신고", "1월", "7월", off = Off.CALENDAR),
        f("shop.staff", "SHOP-11", "직원 인건비", "직원·시급·주휴·4대보험으로 근무표를 짜고 월 인건비와 인건비율을 봐요",
            setOf(SHOP), "${tab(SHOP)} › 직원 인건비 › 근무표 짜기", at(Place.STATS, "staff"), "직원", "인건비", "알바생", "근무표 짜기",
            off = Off.CALENDAR),
        f("shop.weather", "SHOP-10", "날씨별 매출", "하루 마감에 날씨를 고르면 비 오는 날과 맑은 날 평균 매출을 견줘요",
            setOf(SHOP), "${tab(SHOP)} › 날씨별 매출", at(Place.STATS), "날씨", "비", "맑음", off = Off.CALENDAR),
        f("shop.simpleTax", "SHOP-09", "간이과세 기준", "올해 매출과 연말 예상을 간이과세 기준(1억 400만 원)과 견줘요",
            setOf(SHOP), "${tab(SHOP)} › 간이과세 기준", at(Place.STATS), "간이과세", "일반과세", "연 매출", off = Off.CALENDAR),
        f("shop.capture", "SHOP-08", "카드 단말기·배달앱 알림을 매출로", "결제 알림 읽기를 켜면 단말기·배달앱 알림이 매출로, 정산 입금이 카드 입금으로 적혀요",
            setOf(SHOP), "가계부 › ⋮ › 자동 기록", at(Place.AUTO_RECORD), "단말기", "배달의민족", "쿠팡이츠", "포스", "매출 알림"),

        // Investing [INV-01..INV-21]: the 투자·수익 tab's tabs (한눈에·종목·기록·통계·일지·알람) and sheets.
        f("invest.deposit", "INV-02", "원금 입금·출금", "계좌에 넣고 뺀 돈을 적으면 원금이 정해지고 수익률·현금이 바로 맞아요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 입금 적기", at(Place.STATS, "invest.cash"), "원금", "입금", "출금", "계좌", "현금"),
        f("invest.trade", "INV-03", "매매 기록", "종목·수량·단가·수수료와 이유·마음을 적어요. 수수료·거래세는 알아서 제안해요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 매수·매도 (달력 날짜를 길게 눌러도)", at(Place.STATS, "invest.trade"),
            "매수", "매도", "매매", "주식 기록", "거래", "코인"),
        f("invest.taxReport", "INV-37", "해외 주식 세금 신고 자료", "작년에 판 해외 주식을 먼저 산 것부터 한 줄씩, 기본공제·예상 세금까지 표로 만들어 5월 신고에 써요",
            setOf(INVEST), "${tab(INVEST)} › 내보내기 › 해외 주식 세금 신고 자료", at(Place.STATS, "invest.overview"), "양도소득세", "해외주식 세금", "5월 신고", "250만"),
        f("invest.alerts", "INV-30", "체결 알림으로 매매 적기", "증권사·거래소 체결 알림을 휴대폰 안에서 읽어 매수·매도를 찾고, 확인하면 알림 온 앱·코인/주식 표시와 함께 적어요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 알림으로 찾은 거래", at(Place.STATS, "invest.overview"),
            "체결 알림", "자동 기록", "업비트", "증권사 알림", "코인", "바로 적기"),
        f("invest.quotes", "INV-04", "현재가 적기", "보유 종목의 현재가를 적으면 평가손익과 총자산이 보여요",
            setOf(INVEST), "${tab(INVEST)} › 종목 › 현재가", at(Place.STATS, "invest.quotes"), "현재가", "평가손익", "보유 종목", "평단", "평균단가"),
        f("invest.returns", "INV-05", "수익률 세 가지", "원금 대비·금액가중·시간가중 수익률을 설명과 함께 봐요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에", at(Place.STATS, "invest.overview"), "수익률", "시간가중", "금액가중", "원금 대비"),
        f("invest.history", "INV-21", "총자산 흐름", "현재가가 다 있는 날의 총자산을 모아 흐름 그래프로 보여 줘요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 총자산 흐름", at(Place.STATS, "invest.overview"), "총자산", "그래프", "자산 추이",
            off = Off.CALENDAR),
        f("invest.stats", "INV-06", "일·월·연 손익과 승률", "기간별 손익, 승률, 종목별 손익, 월별 막대를 봐요",
            setOf(INVEST), "${tab(INVEST)} › 통계", at(Place.STATS, "invest.stats"), "손익", "승률", "월별", "종목별", "통계"),
        f("invest.mind", "INV-13", "마음과 원칙별 손익", "감정 태그·원칙 지킴에 따른 손익, 손익비, 연속 수익·손실일을 봐요",
            setOf(INVEST), "${tab(INVEST)} › 통계 › 마음과 원칙에 따라", at(Place.STATS, "invest.stats"), "감정", "원칙", "손익비", "복기",
            off = Off.CALENDAR),
        f("invest.weekdays", "INV-18", "요일별 손익", "요일마다 손익과 평균 보유일을 봐요",
            setOf(INVEST), "${tab(INVEST)} › 통계 › 요일별 손익", at(Place.STATS, "invest.stats"), "요일", "보유일", off = Off.CALENDAR),
        f("invest.tax", "INV-12", "투자 세금 미리 보기", "해외주식 양도세, 배당·금융소득, ISA·연금 계좌를 대략 계산해요",
            setOf(INVEST), "${tab(INVEST)} › 통계 › 세금 미리 보기", at(Place.STATS, "invest.stats"), "양도세", "250만", "금융소득", "ISA", "세금",
            off = Off.CALENDAR),
        f("invest.journal", "INV-07", "투자일지", "날마다 시장 메모·복기·원칙 체크·하루 평가를 남겨요",
            setOf(INVEST), "${tab(INVEST)} › 일지", at(Place.STATS, "invest.journal"), "일지", "복기", "매매일지", "원칙 체크", "메모",
            moment = Moment.FIRST_TRADE, tip = "첫 매매를 적었어요. ‘투자일지’에 이유와 복기를 남기면 나중에 마음과 원칙별 손익을 볼 수 있어요"),
        f("invest.alarms", "INV-08", "투자 알람", "장 시작·마감, 정기 매수일, 목표가 확인 알람을 맞추고 알림에서 바로 기록해요",
            setOf(INVEST), "${tab(INVEST)} › 알람", at(Place.STATS, "invest.alarms"), "장 시작", "장 마감", "정기 매수", "적립식", "목표가"),
        f("invest.dividend", "INV-09", "배당·이자 기록", "세전 금액과 원천징수, 실수령을 적고 종목별 배당 합계를 봐요",
            setOf(INVEST), "${tab(INVEST)} › 기록 › 배당", at(Place.STATS, "invest.dividend"), "배당", "이자", "배당금", "원천징수"),
        f("invest.goal", "INV-11", "투자 목표", "총자산·수익률 목표와 날짜를 정하면 진행률과 매달 넣을 돈을 알려 줘요",
            setOf(INVEST), "${tab(INVEST)} › ⋮ › 목표와 수익률", at(Place.STATS, "invest.goal"), "목표", "총자산 목표", "매달 넣을 돈", "진행률",
            off = Off.CALENDAR),
        f("invest.events", "INV-14", "투자 일정", "배당락·실적 발표·공시·청약 날을 적으면 그날 아침 알려 줘요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 다가오는 일", at(Place.STATS, "invest.event"), "배당락", "실적 발표", "공시", "청약", "일정",
            off = Off.CALENDAR),
        f("invest.accounts", "INV-15", "계좌별 보기", "계좌를 골라 그 계좌만의 수익률과 보유 종목을 봐요",
            setOf(INVEST), "${tab(INVEST)} › 맨 위 계좌 칩", at(Place.STATS, "invest.overview"), "계좌별", "ISA", "연금저축", "증권사"),
        f("invest.hide", "INV-17", "금액 가리기", "남이 볼 때 금액을 가리고 수익률만 보여요",
            setOf(INVEST), "${tab(INVEST)} › 눈 아이콘", at(Place.STATS, "invest.overview"), "가리기", "숨기기", "금액 숨김", "개인정보"),
        f("invest.csv", "INV-10", "투자 기록 CSV", "매매·입출금·보유·일지를 CSV로 내보내요",
            setOf(INVEST), "${tab(INVEST)} › ⋮ › CSV로 내보내기", at(Place.STATS, "invest.overview"), "CSV", "엑셀", "내보내기"),

        // Money and records, for every kind of work.
        f("money.auto", "LEDG-16", "결제 알림 자동 기록", "카드·은행 결제 알림을 읽어 가계부에 저절로 적어요",
            ALL, "가계부 › ⋮ › 자동 기록", at(Place.AUTO_RECORD), "가계부", "카드 알림", "문자", "자동 기록", "결제 알림", off = Off.ELSEWHERE),
        f("money.confirm", "COMN-04", "확인하고 적기", "결제 알림을 바로 적지 않고 확인 대기함에 두었다가 확인한 것만 적어요",
            ALL, "가계부 › ⋮ › 자동 기록 › 확인하고 적기", at(Place.AUTO_RECORD), "확인 대기함", "바로 적기", "검토"),
        f("money.rules", "LEDG-21", "자동 분류 규칙", "가게마다 배운 분류를 보고 바꾸거나, ‘이 말이 들어가면’ 규칙을 더해요",
            ALL, "가계부 › ⋮ › 자동 기록 › 자동 분류 규칙", at(Place.AUTO_RECORD), "분류", "카테고리", "기타", "규칙"),
        f("money.otherPlaces", "LEDG-23", "기타로 남은 곳 정리", "기타로 남은 가게를 모아 한 번에 분류해요",
            ALL, "가계부 › 요약 › 기타로 남은 곳", at(Place.MONEY), "기타", "미분류", "분류 정리", off = Off.CALENDAR),
        f("money.repeat", "LEDG-24", "매달 나가는 돈 알아보기", "석 달 넘게 비슷하게 나간 돈을 반복 결제로 등록하자고 제안해요",
            ALL, "가계부 › 요약 › 매달 나가는 돈 같아요", at(Place.MONEY), "반복 결제", "구독", "고정비", "정기 결제", off = Off.CALENDAR),
        f("money.owed", "RECV-02", "받을 돈", "빌려준 돈, 외상, 못 받은 급여를 적고 받으면 표시해요",
            ALL, "가계부 › 받을 돈", at(Place.OWED), "빌려준 돈", "외상", "미수금", "갚을 돈"),

        // Calendars, memos and data.
        f("calendars.multi", "MCAL-01", "달력 여러 개", "알바·부업처럼 일을 달력마다 따로 적어요",
            ALL, "달력 › 달력 이름 › 새 달력 만들기", at(Place.CALENDARS), "달력 추가", "새 달력", "투잡", "부업", "알바"),
        f("calendar.edit", "CALS-01", "달력 고치기", "달력마다 이름·색·일하는 방식·알람·결제 알림·공유를 한곳에서 바꿔요",
            ALL, "달력 › ⋮ › ‘달력 이름’ 고치기", at(Place.CALENDAR_EDIT), "달력 수정", "이름 바꾸기", "색 바꾸기", "일하는 방식", "근무 유형"),
        f("calendar.customize", "CUST-01", "화면 꾸미기", "달력과 근무·급여 화면의 카드를 켜고 끄고 순서를 바꿔요. 미리보기로 바로 보여요",
            ALL, "달력 › ⋮ › 화면 꾸미기", at(Place.CALENDAR, "customize"), "배치", "순서", "카드 숨기기", "하루 카드", "레이아웃", "편집"),
        f("help.inquiry", "INQ-02", "문의와 진행 상황", "불편한 점·제안을 보내면 접수부터 반영 버전까지 단계가 보이고, 답을 평가할 수 있어요",
            ALL, "설정 › 도움말과 문의", at(Place.INQUIRY), "문의", "건의", "제보", "버그", "진행 상황", "답변"),
        f("calendar.year", "CAL-22", "1년 달력", "열두 달을 한 화면에 근무 색으로 보고, 한 해의 근무 종류별 날 수와 휴가를 세요",
            ALL, "달력 › ⋮ › 1년 달력", at(Place.YEAR), "연간 달력", "1년", "한 해", "열두 달", "휴가 계획", "연간 근무"),
        f("privacy.lock", "LOCK-01", "앱 잠금", "열 때 지문·얼굴·PIN을 물어 급여·메모를 지켜요. 알람은 그대로 울려요",
            ALL, "설정 › 개인정보·앱 정보 › 앱 잠금", at(Place.SETTINGS_PRIVACY, "앱 잠금"), "잠금", "비밀번호", "지문", "생체 인증", "PIN", "숨기기"),
        f("calendar.combined", "COMN-01", "모든 달력 합쳐 보기", "모든 달력을 한 달력에 색 막대로 보고 겹치는 시간을 알려 줘요",
            ALL, "달력 › 달력 이름 › 모든 달력 합쳐 보기", at(Place.COMBINED), "합쳐 보기", "겹치는", "한눈에", "모든 달력"),
        f("stats.all", "COMN-03", "통계 전체 보기", "모든 달력의 총수입·총지출·순저축을 한 번에 봐요",
            ALL, "통계 탭 › 전체", at(Place.STATS), "전체", "모든 달력 통계", "총수입"),
        f("memo.sharing", "MEMO-02", "메모 공유", "메모를 달력마다 따로 두거나, 몇 달력끼리 함께 봐요",
            ALL, "설정 › 화면과 글자 › 메모 공유", at(Place.SETTINGS_CALENDAR, "메모 공유"), "공용 메모장", "메모 같이", "달력별 메모"),
        f("memo.repeat", "NOTE-05", "반복 메모", "매달 25일, 매주 화요일처럼 돌아오는 메모를 적어요",
            ALL, "메모 › 메모 쓰기 › 반복", at(Place.MEMOS), "반복", "매달", "매주", "정기"),
        f("trash.records", "UNDO-01", "최근 지운 기록", "일하는 방식을 바꾸거나 지운 기록·달력을 7일 동안 되돌려요",
            ALL, "설정 › 데이터 › 최근 지운 기록", at(Place.SETTINGS_DATA, "최근 지운 기록"), "되돌리기", "휴지통", "복구", "실수로 지움"),
        f("data.backup", "REL-04", "백업 저장", "근무·메모·돈 기록을 파일로 만들어 새 휴대폰으로 옮겨요",
            ALL, "설정 › 데이터 › 백업 저장", at(Place.SETTINGS_DATA, "백업 저장"), "백업", "옮기기", "휴대폰 바꾸기", "복원"),
        f("data.autoBackup", "PROV-03", "매일 자동 백업", "고른 파일(구글 드라이브 등)에 암호 건 백업을 하루 한 번 덮어써요. 3일 넘게 못 하면 알려 줘요",
            ALL, "설정 › 데이터 › 매일 자동 백업", at(Place.SETTINGS_DATA, "매일 자동 백업"), "자동 백업", "매일 백업", "구글 드라이브", "클라우드"),
        f("calendar.phone", "FEAT-07", "휴대폰 캘린더에 근무 쓰기", "휴대폰 안 ‘밤낮’ 캘린더에 근무를 적어 삼성·구글 캘린더 앱에서도 보여요. 끄면 그 캘린더를 지워요",
            ALL, "설정 › 데이터 › 휴대폰 캘린더에 근무 쓰기", at(Place.SETTINGS_DATA, "휴대폰 캘린더에 근무 쓰기"), "구글 캘린더", "삼성 캘린더", "캘린더 연동", "캘린더에 쓰기"),
        f("app.pro", "MON-04", "프로", "광고 없이 쓰고, 명세서 대조·급여 리포트·휴대폰 캘린더 쓰기·매일 자동 백업·끄기 미션·돈 통계·달력 여러 개를 열어요. 1개월 무료로 써 볼 수 있어요",
            ALL, "설정 › 프로", at(Place.PRO), "구독", "광고 없애기", "무료 체험", "유료", "결제", "이용권", "복구 코드"),
        f("privacy.adChoices", "MON-07", "광고 개인정보 선택", "광고가 쓰는 정보를 보고, 법이 묻는 지역에서는 동의를 다시 골라요",
            ALL, "설정 › 개인정보·앱 정보 › 광고 개인정보 선택", at(Place.SETTINGS_PRIVACY, "광고 개인정보 선택"), "광고", "맞춤 광고", "광고 ID", "동의"),
        // [STAT-01][COMM-10] 1.22.0: usage stats only with a yes, the plaza's daily news on for those who take part.
        f("privacy.usageStats", "STAT-01", "사용 통계 보내기", "어떤 기능을 하루에 몇 번 썼는지만 보내요. 보낼 내용을 미리 볼 수 있고, 꺼도 앱은 똑같이 돼요",
            ALL, "설정 › 개인정보·앱 정보 › 사용 통계 보내기", at(Place.SETTINGS_PRIVACY, "사용 통계 보내기"), "통계", "사용 기록", "보내기", "익명", off = Off.ELSEWHERE),
        f("plaza.news", "COMM-10", "밤낮 소식", "문의 답, 받은 근무표의 바뀜, 공지, 광장의 규칙 바뀜·채택·신고 결과를 하루 한 번 묶어서 알려 줘요",
            ALL, "설정 › 알림과 잠 › 밤낮 소식", at(Place.SETTINGS_NOTICES, "밤낮 소식"), "광장 소식", "문의 답", "광장", "규칙 바뀜", "채택", "회사 소식", "알림 끄기", off = Off.ELSEWHERE),
        f("lock.today", "FEAT-18", "잠금 화면 오늘 근무", "오늘·내일 근무를 소리 없는 알림으로 잠금 화면에 늘 보여 줘요",
            PAID, "설정 › 알림과 잠 › 잠금 화면 오늘 근무", at(Place.SETTINGS_NOTICES, "잠금 화면 오늘 근무"), "잠금 화면", "상시 알림", off = Off.ELSEWHERE),
        f("widget.lines", "COMN-05", "위젯 일하는 방식 줄", "오늘 위젯에 알바 주 시간·주휴, 일근 남은 연차·야근, 현장 이번 주 공수, 배달 오늘·이번 주 건수, 공무원 이번 달 시간외/57시간, 자영업 영업·휴무 일수를 한 줄 더 보여 줘요",
            setOf(DAY, HOURLY, FIELD, SHIFT, SHOP), "홈 화면 › 밤낮 오늘 위젯", at(Place.FEATURES), "위젯", "홈 화면", "시간외 위젯", "영업일", "건수", off = Off.APP),
        // G 1.32 [WTYP-23] 조사 대조 W29 W38.
        f("widget.fieldActions", "WTYP-23", "위젯 +1건·오늘 1공", "오늘 위젯에서 배달·대리는 ‘+1건’으로 오늘 건수를 하나 올리고, 현장은 ‘오늘 1공’으로 오늘 공수를 바로 적어요",
            setOf(FIELD), "홈 화면 › 밤낮 오늘 위젯", at(Place.FEATURES), "+1건", "오늘 1공", "위젯 버튼", "한 손 입력", off = Off.APP),
        f("field.platformCut", "WTYP-23", "콜 출처별 수수료·보험료", "카카오T 대리·로지·콜마너·티맵 대리처럼 콜 출처마다 수수료 %와 건당 보험료를 정하면 남은 돈·시간당 순수익·입금 확인에서 빼고 셈해요",
            setOf(FIELD), "$DAY_SHEET › 플랫폼 기록 › 수수료·보험료", at(Place.TODAY), "수수료", "보험료", "대리", "로지", "콜마너", "카카오T 대리", "티맵 대리", "콜 출처"),
        f("field.unitRules", "WTYP-27", "자주 쓰는 공수 버튼·끝난 시각 규칙", "날짜에 나오는 공수 버튼을 내 것으로 바꾸고, ‘17시까지 1공, 19시까지 1.5공’처럼 정해 두면 끝난 시각만 골라 공수를 적어요",
            setOf(FIELD), "달력 고치기 › 일 세는 방법", at(Place.TODAY), "공수 버튼", "공수 칩", "끝난 시각", "공수 규칙", "잔업 공수", "1.5공"),
        // 1.22 묶음 E: on by default (S10); each says where it turns off.
        f("widget.memo", "DAILY-01", "위젯에 메모 줄", "오늘·이번 주 위젯에 그날 메모 첫 줄이 보여요. 날짜를 누르면 그날이 열려요",
            ALL, "설정 › 화면과 글자 › 위젯에 메모 보이기", at(Place.SETTINGS_CALENDAR, "위젯에 메모 보이기"), "위젯", "메모", "홈 화면", "끄기"),
        f("widget.teams", "DAILY-03", "오늘 모든 조 위젯", "홈 화면에서 조마다 오늘·내일 근무를 봐요. 내 조는 굵게 보여요",
            setOf(SHIFT), "홈 화면 › 위젯 › 밤낮 오늘 모든 조", at(Place.TEAMS), "위젯", "모든 조", "조별 근무", "홈 화면"),
        f("money.receiptScan", "LEDG-25", "영수증 사진 읽기", "영수증을 찍으면 금액·날짜·시간·가게를 채워요. 휴대폰 안에서만 읽고 저장 전에 확인해요",
            ALL, "가계부 › 돈 적기 › 지출 › 영수증 사진 읽기", at(Place.MONEY), "영수증", "사진", "OCR", "자동 입력"),
        f("money.owedCsv", "LEDG-27", "받을 돈 CSV", "받을 돈 목록을 남은 돈·약속일·상태까지 엑셀에서 여는 CSV로 보내요",
            ALL, "가계부 › 받을 돈 › 내보내기", at(Place.OWED), "CSV", "엑셀", "받을 돈", "내보내기", under = "money.owed"),
        f("data.backupPhotos", "BACK-01", "사진도 백업", "영수증·계약서 사진을 백업 파일에 같이 넣어 새 휴대폰에서도 보여요",
            ALL, "설정 › 데이터 › 사진도 백업", at(Place.SETTINGS_DATA, "사진도 백업"), "사진", "백업", "영수증", "계약서", under = "data.backup"),
        f("invest.monthSummary", "INV-34", "월말 투자 요약 알림", "달 마지막 날 저녁 8시에 그달 매수·매도와 실현 손익을 알려 줘요. 금액 가리기를 따르고 거래 없는 달은 조용해요",
            setOf(INVEST), "${tab(INVEST)} › ⋮ › 목표와 수익률 › 월말 요약 알림", at(Place.STATS, "invest.goal"), "월말", "요약", "알림", "한 달 정리"),
        f("money.customize", "DAILY-15", "가계부·메모 화면 꾸미기", "가계부 요약 카드와 메모 위 줄의 순서와 켜고 끄기를 바꿔요",
            ALL, "가계부 › ⋮ › 화면 꾸미기, 메모 › 화면 꾸미기", at(Place.MONEY), "화면 꾸미기", "순서", "카드 숨기기", "필터 접기", under = "calendar.customize"),
        f("pay.rateHint", "DAILY-16", "광장 시세와 견주기", "광장 임금표에서 본 내 직군·내 지역 시세와 내 일당·시급·월급이 10% 넘게 다르면 급여 화면에 카드로 보여요. 휴대폰 안에서만 견줘요",
            setOf(SHIFT, DAY, HOURLY, FIELD), "설정 › 급여와 연차 › 광장 시세와 내 단가 견주기", at(Place.SETTINGS_PAY, "광장 시세와 내 단가 견주기"), "시세", "일당", "시급", "비교"),
        // 1.23 묶음 I: the calendar, day sheet, patterns, widgets and watch.
        f("calendar.quickText", "CAL-43", "메모 한 줄로 넣기", "날짜 시트 메모에 ‘내일 야간 대근 7시’, ‘다음주 월화 연차’처럼 적으면 근무·추가 근무·연차로 넣을지 물어요",
            ALL, "$DAY_SHEET › 빠른 메모", at(Place.CALENDAR), "빠른 입력", "말로 입력", "메모로 넣기", "자연어", "한 줄 입력"),
        f("calendar.monthRecap", "CAL-46", "한 달 마감 카드", "달이 끝나면 그달 일한 날·시간·야간과 지난달과의 차이를 한 장으로 보여 주고 달력 사진으로 보내요",
            ALL, "달력 맨 위 카드 (달이 바뀐 뒤)", at(Place.CALENDAR), "월말", "한 달 정리", "마감", "달력 사진", "지난달 비교"),
        f("data.openFile", "CAL-47", "받은 파일 바로 열기", "카톡·파일 앱에서 받은 백업이나 회사 근무표 파일을 누르면 밤낮이 열려 읽어요",
            ALL, "파일 앱·메신저 › 파일 누르기 › 밤낮으로 열기", at(Place.SETTINGS_DATA, "파일 불러오기"), "파일 열기", "카톡 파일", "백업 열기", "공유 받기",
            under = "data.backup"),
        f("pattern.realign", "SCHED-03", "이 날부터 순서 다시 맞추기", "순서가 어긋난 날을 길게 눌러 그날이 몇 번째인지 고르면 그날부터 다시 돌고, 지난 날은 그대로 남아요",
            setOf(SHIFT, HOURLY), "달력 › 날짜 길게 누르기 › 이 날부터 순서 다시 맞추기", at(Place.CALENDAR), "순서 어긋남", "다시 맞추기", "밀림", "근무 주기",
            under = "shift.pattern"),
        f("pattern.weeklyOptions", "SCHED-04", "공휴일 쉬기·격주 토요일", "요일마다 정한 근무에서 공휴일은 쉬고, 토요일은 격주로만 일하게 정해요",
            setOf(DAY, HOURLY, FIELD), "설정 › 내 근무 › 근무 주기 › 요일마다", at(Place.PATTERN), "공휴일", "격주 토요일", "격주 근무", "빨간 날"),
        f("shift.swapFind", "SCHED-07", "맞바꿀 날 찾기", "모든 조에서 내 근무와 맞바꿔도 양쪽 다 쉬는 시간이 모자라지 않는 날을 찾아 부탁 글까지 써 줘요",
            setOf(SHIFT), "달력 › 모든 조 › 날짜 누르기 › 맞바꾸기", at(Place.TEAMS), "맞바꾸기", "근무 교환", "바꿔 줄 사람", "교대 바꾸기",
            under = "shift.teams"),
        f("widget.countdown", "WIDG-05", "다음 근무까지 위젯", "다음 근무가 몇 시간 남았는지, 지금 근무가 언제 끝나는지 홈·잠금 화면 위젯으로 보여요",
            ALL, "홈 화면 › 위젯 › 밤낮 다음 근무", at(Place.FEATURES), "위젯", "카운트다운", "남은 시간", "다음 근무", "잠금 화면"),
        // O 1.23 (L): [ALRM-04] 증명 사진, one of 끄기 미션's choices (고르는 것이라 기본 꺼짐, D42 예외).
        f("alarm.missionPhoto", "ALRM-04", "증명 사진 미션", "욕실 거울·현관처럼 일어나면 가는 곳을 찍어 두면, 알람 때 같은 곳을 찍어야 꺼져요. 사진은 흑백 지문만 남기고 바로 지워요",
            PAID, "설정 › 알람 › 증명 사진 기준", at(Place.SETTINGS_ALARM, "증명 사진 기준"), "증명 사진", "사진 미션", "사진 찍어 끄기", "욕실", "못 일어나요",
            under = "alarm.mission"),
        // O 1.23 (I): 스마트 알람 제안.
        f("alarm.suggest", "ALRM-03", "알람 제안", "알람을 늦게 끄거나 먼저 깨는 일이 잦으면 그 근무 알람을 옮기자고, 잠이 모자라면 잘 시간 알림을 켜자고 카드로 물어요. 한 번 눌러 바꾸고 되돌릴 수 있어요",
            setOf(SHIFT, DAY, HOURLY, FIELD), "설정 › 알람 › 알람 제안", at(Place.SETTINGS_ALARM, "알람 제안"), "알람 제안", "늦게 끔", "알람 시간 추천", "스마트 알람", "기상",
            off = Off.ELSEWHERE),
        // 1.23 묶음 N: 내 정보 한곳에서, and what the app did by itself.
        f("profile.summary", "EASY-08", "내 정보 한곳에서 바꾸기", "근무·알람·급여·연차·세금·잠 정보를 한 화면에서 보고, 누른 자리에서 바로 고쳐요. 고치면 쓰는 기능에 바로 적용돼요",
            ALL, "설정 맨 위 카드 › 내 정보", at(Place.PROFILE), "내 정보", "프로필", "정보 바꾸기", "처음부터", "추정", "모르는 것"),
        f("journal.list", "OBS-02", "밤낮이 한 일", "알람 다시 잡기·백업·계산 자료 갱신처럼 앱이 알아서 한 일을 날짜별로 이유와 함께 보여 줘요",
            ALL, "설정 맨 위 카드 › 밤낮이 한 일", at(Place.JOURNAL), "한 일", "기록", "자동으로", "왜 바뀌었", "알아서"),
        // 1.24 묶음 Q: 회사 규칙을 글 대신 골라 담기 (계획/1.24-Q-개발계획.md 350).
        f("pay.ruleBuilder", "INPUT-03", "회사 규칙 골라 담기", "대근·특근·토요일 근무가 기본급의 몇 %인지, 몇 시간 넘으면 얼마 더인지 눌러 고르면 한 문장으로 보여 주고 그대로 계산에 넣어요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 내 급여 값 › 회사만의 규칙", at(Place.PAY, "items"), "회사 규칙", "규칙 조립", "대근 계산", "가산", "기본급의 %",
            "토요일 근무", "취업규칙", under = "pay.calc"),
        // 1.25 묶음 AB: 알람을 버튼으로 끄기, 오늘 남은 알람 모두 끄기 (계획/1.25-AB-개발계획.md 411·412).
        f("alarm.buttons", "ALRM-08", "버튼으로 알람 끄기", "알람이 울릴 때 소리 줄이기·키우기 버튼이나 전원 버튼을 누르면 꺼져요. 다시 울림이나 아무것도 안 함으로 바꿀 수 있고, 끄기 미션이 있으면 소리만 30초 줄어요",
            setOf(SHIFT, DAY, HOURLY, FIELD), "설정 › 알람 › 버튼으로 알람 끄기", at(Place.SETTINGS_ALARM, "버튼으로 알람 끄기"), "볼륨 버튼", "전원 버튼", "잠금 버튼", "소리 줄이기", "버튼으로 끄기"),
        f("alarm.allOff", "ALRM-09", "오늘 남은 알람 모두 끄기", "일어났으면 알람 화면이나 알림에서 한 번 눌러 오늘 남은 알람·다시 울림·일어났나요?를 모두 멈춰요. 내일 알람은 그대로고, 달력에서 다시 켤 수 있어요",
            setOf(SHIFT, DAY, HOURLY, FIELD), "알람이 울릴 때 › 오늘 남은 알람 모두 끄기", at(Place.DIAGNOSTICS), "오늘 알람 끄기", "전체 알람 끄기", "알람 또 울려요", "일어났어요", "모두 끄기"),
        // 1.25 묶음 W: 세금 예측·실수령(프로)·사업자 세금·투자 수수료 (계획/1.25-W-개발계획.md 379~384).
        f("money.takeHome", "TAX-02", "세금·실수령 예상", "이달 세전 옆에 세후(실수령)를 보여 줘요. 들어온 월급과 지난 명세서로 회사가 실제로 떼는 만큼 맞춰요",
            PAID, "가계부 › 요약 › 세금·실수령", at(Place.MONEY, "tax"), "세후", "실수령", "세금 얼마", "떼는 돈", "4대 보험", "월급 들어옴", "세전"),
        f("money.payRecords", "TAX-04", "지난 월급 적기", "지난달들의 세전·실수령을 적거나 명세서 사진으로 읽어 두면 세금 예측이 정확해져요. 시급·3.3%·일당도 돼요",
            PAID, "가계부 › 요약 › 세금·실수령 › 지난 월급 적기", at(Place.MONEY, "tax"), "명세서", "지난 월급", "급여 기록", "연말정산", "올해 세금",
            under = "money.takeHome"),
        f("money.bizTax", "TAX-05", "사업자 세금 예상", "적은 매출·경비·영수증으로 올해 종합소득세·부가세를 어림하고, 내는 날과 매달 모아 둘 돈을 알려 줘요",
            setOf(FIELD, SHOP), "가계부 › 요약 › 세금·실수령 › 자세히·설정", at(Place.MONEY, "tax"), "종합소득세", "부가세", "사업자 세금", "경비율",
            "5월 신고", "간이과세", "세금 얼마"),
        f("invest.feeRate", "INV-38", "기본 수수료 정하기", "매수·매도 수수료를 한 번 정하면(처음엔 0.05%) 매매를 적을 때마다 그대로 계산하고, 매도 단가를 넣으면 거래세·세금도 바로 나와요",
            setOf(INVEST), "${tab(INVEST)} › 매수·매도 › 기본 수수료", at(Place.STATS, "invest.trade"), "수수료", "0.05", "거래세", "매도 세금",
            "수수료율", under = "invest.trade"),
        // 1.25 W 409 (결정 D77): 단가 없는 체결 알림 채우기.
        f("invest.priceFill", "INV-40", "체결 내역 캡처로 단가 채우기", "단가 없이 온 체결 알림은 금액 ÷ 수량, 이어 온 알림 묶기로 채우고, 그래도 없으면 증권사 체결 내역 캡처를 휴대폰 안에서 읽어 채워요. 안 맞는 숫자는 노랗게 물어요",
            setOf(INVEST), "${tab(INVEST)} › 한눈에 › 알림으로 찾은 거래 › 체결 내역 캡처로 채우기", at(Place.STATS, "invest.overview"),
            "단가 없음", "체결 내역", "캡처", "스크린샷", "평균 단가", "나눠 체결", under = "invest.alerts"),
        f("invest.taxRemind", "INV-41", "해외 주식 5월 신고 알림", "지난해 해외 주식 이익이 250만 원 공제를 넘으면 5월 1일과 신고 마감 일주일 전에 예상 세금과 함께 알려 줘요",
            setOf(INVEST), "${tab(INVEST)} › 통계 › 세금 미리 보기", at(Place.STATS, "invest.stats"), "5월 신고", "양도소득세 신고", "해외 주식 신고",
            "신고 알림", under = "invest.tax"),
        // 1.25 묶음 V: 빨간날 근무, 명세서로 회사 방식 맞추기 (계획/1.25-V-개발계획.md 374·376).
        f("pay.holidayWork", "HOLI-01", "빨간날 근무 수당", "빨간날(공휴일)에 일하면 법대로·특근처럼(근무 종류마다 1회 금액)·대체휴일·교대제 중 회사 방식대로 계산하고, 빨간날 휴가는 근무일로만 셈해요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 회사 규칙 › 바꾸기 › 빨간날(공휴일)에 일하면", at(Place.PAY, "rules"), "빨간날", "공휴일", "휴일 근무", "공휴일 수당",
            "특근", "대근", "대체휴일", under = "pay.calc"),
        // [PAYS-19] 1.27 AJ: 이름·자리 바꿈 (합계 아래 '명세서와 맞춰 보기' 카드), 명세서 없이 회사 계산 방법만으로도 돼요.
        f("pay.slipRules", "PAYS-14", "계산 방식 찾기", "급여 명세서나 회사 계산 방법(취업규칙·수당표) 사진을 휴대폰 안에서 읽어 기본급·가산율·대근 금액·수당·빨간날 방식을 찾아 넣어요. 명세서가 없어도 돼요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 명세서와 맞춰 보기 › 계산 방식 찾기", at(Place.PAY), "명세서 사진", "회사 계산 방법", "취업규칙 사진", "자동 설정",
            "급여 설정", "역산", "사진으로 회사 방식 맞추기", "명세서로 회사 방식 맞추기", "규칙만", under = "pay.calc"),
        // ---- 1.25 묶음 X (계획/1.25-X-개발계획.md 386·390) ----
        f("plaza.diff", "PLAZA-02", "다른 점 말하기", "같은 회사 설정이 우리와 조금 다르면 그 회사 값이 채워진 창에서 다른 곳만 골라 바꿔요. 바꾼 칸은 제안으로 올라가고, 급여 단계에서 고치면 내 급여도 고친 대로 계산해요",
            ALL, "설정 › 회사 규칙 광장 › 회사 카드 › 우리는 달라요", at(Place.PAY), "다른 점", "달라요", "제안", "대표 판", "비교", "되돌리기", "초성", "회사 설정"),
        f("setup.again", "PLAZA-06", "처음 설정 다시 받기", "회사 찾기부터 근무·급여까지 첫 설정 질문을 다시 받아요. 저장해야 오늘부터 바뀌고, 그만두면 그대로예요",
            ALL, "설정 › 내 근무 › 처음 설정 다시 받기", at(Place.SETTINGS_WORK, "처음 설정 다시 받기"), "첫 설정", "처음 설정", "다시", "건너뛴", "회사 찾기"),
        // 1.25 묶음 Y: 프로 기능 모아보기 and the pro features that had no line here (계획/1.25-Y-개발계획.md 393).
        f("pro.collection", "TIER-07", "프로 기능 모아보기", "프로 기능을 한 화면에 모아 이 달력에 맞는 것부터 보여 주고, 누르면 그 기능으로 바로 가요",
            ALL, "설정 › 프로 › 프로 기능 모아보기", at(Place.PRO), "프로 기능", "모아보기", "유료 기능", "프로 표시", "프로 목록"),
        f("pay.ruleImport", "PAYS-04", "임금 규칙 자동 인식", "회사 임금 규칙 글을 붙여 넣으면 대근·연장·야간 가산을 읽어 급여 규칙에 채워요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 내 급여 값 › 회사 규칙 글로 채우기", at(Place.PAY, "items"), "규칙 인식", "임금 규칙", "취업규칙 붙여넣기", "자동 채우기",
            under = "pay.calc"),
        f("pay.bulkEdit", "PAYX-03", "값 일괄 적용", "기본급·시급 같은 값을 고른 기간에 한 번에 바꾸고, 연도별 인상으로 지난 급여를 다시 계산해요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 내 급여 값 › 기간 지정", at(Place.PAY, "items"), "일괄 적용", "기간 지정", "인상 계산", "소급", under = "pay.calc"),
        f("money.stats", "TIER-05", "가계부 통계", "지난달과 비교, 분류별 지출, 쓰는 속도, 올해 흐름을 그림으로 보여 줘요",
            ALL, "가계부 › 통계", at(Place.MONEY, "report"), "분석", "지출 통계", "분류별", "쓰는 속도", "올해 흐름"),
        // 1.25 묶음 Z (계획/1.25-Z-개발계획.md 402).
        f("data.movePhone", "BAK-01", "새 휴대폰으로 옮기기", "기록과 모든 설정을 백업 파일 하나로 옮겨요. 이 휴대폰에만 남는 것(지도 위치·알람 소리 등)도 알려 줘요",
            ALL, "설정 › 데이터 › 새 휴대폰으로 옮기기", at(Place.SETTINGS_DATA, "새 휴대폰으로 옮기기"), "새 휴대폰", "기기 변경", "옮기기", "모든 설정",
            under = "data.backup"),
        // ---- 1.26 묶음 AD (계획/1.26-AD-개발계획.md 419~421) ----
        f("nav.tabOrder", "NAV-05", "첫 화면·메뉴 순서", "아래 메뉴를 길게 눌러 끌면 순서가 바뀌고, 맨 앞 메뉴가 앱을 켤 때 처음 보이는 화면이 돼요. 설정에서 위·아래로도 바꿔요",
            ALL, "설정 › 화면과 글자 › 첫 화면·메뉴 순서", at(Place.SETTINGS_CALENDAR, "첫 화면·메뉴 순서"), "첫 화면", "시작 화면", "메인 화면", "메뉴 순서",
            "탭 순서", "길게 누르기"),
        // ---- 1.26 묶음 AC (계획/1.26-AC-개발계획.md 414·417) ----
        f("plaza.cycles", "SPAT-02", "회사 근무주기 찾기", "회사 이름·초성·업종·근무형태로 공개 자료의 근무주기를 찾아 순서와 오늘 날짜만 맞추면 달력에 넣어요. 없으면 처음으로 넣어 광장에 올려요",
            setOf(SHIFT), "설정 › 내 근무 › 근무 주기 › 회사 이름으로 근무주기 찾기", at(Place.PATTERN), "회사 근무주기", "공개 자료",
            "근무주기 찾기", "회사 검색", "4조 2교대", "교대 주기", "처음 제공"),
        // 1.26 묶음 AE (계획/1.26-AE-개발계획.md 424·425).
        f("pro.invite", "INVITE-02", "친구 초대", "초대 링크로 깐 친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또 1개월(운영자가 정한 규칙, 상한 없음). 링크로 깔면 저절로 알아보고, 링크 없이 깐 친구는 설치 7일 안에 코드를 넣어요",
            ALL, "설정 맨 위 › 친구 초대 (설정 › 프로 › 친구 초대)", at(Place.PRO), "초대", "친구 초대", "추천", "초대 코드", "초대 링크", "프로 한 달", "공짜 프로"),
        // AF 1.26 시작 (계획/1.26-AF-개발계획.md, 결정 D79)
        f("plaza.room", "PLAZA-09", "우리 회사 광장", "참여한 회사에서는 다른 분들이 바꾼 값만 ‘야간 50% → 60%로 바꾼 사람 3명’처럼 카드로 보이고, ‘이 값 쓰기’로 그 값만 가져와요. 내 금액은 그대로예요",
            ALL, "설정 › 회사 규칙 광장 › 회사 카드 › 우리 회사 광장", at(Place.PAY), "회사 광장", "참여", "이 값 쓰기", "바꾼 사람", "안정", "제보 1", "급여 방식 있음"),
        f("plaza.autoShare", "PLAZA-07", "회사 광장에 저절로 나누기", "한 번 허락하면 근무 순서·계산 방식을 바꿀 때마다 하루 한 번 회사 광장에 고쳐 올려요. 금액·이름·연락처는 보내지 않아요",
            ALL, "설정 › 개인정보·앱 정보 › 회사 광장에 저절로 나누기", at(Place.SETTINGS_PRIVACY, "회사 광장에 저절로 나누기"), "저절로 나누기", "자동 공유", "회사 광장", "공유 끄기"),
        // AF 1.26 끝
        // AK 1.27 시작 (계획/1.27-AK-개발계획.md 450·451)
        // [INVA-02] 1.29 AR: 바로 넣기가 처음부터 켜짐, 바꾸는 곳은 ⋮의 '넣기 전에 묻기'.
        f("invest.captureTrust", "INV-44", "캡처로 바로 넣기", "체결 내역 캡처에서 시각·수량·단가가 모두 읽히고 금액과 맞는 체결은 묻지 않고 바로 넣고(되돌리기), 이미 적은 건 건너뛰고, 다르거나 흐린 것만 물어요. 하나씩 묻게 하려면 ⋮에서 ‘넣기 전에 묻기’",
            setOf(INVEST), "${tab(INVEST)} › ⋮ › 캡처로 채우기: 넣기 전에 묻기", at(Place.STATS, "invest.overview"), "묻지 않기", "바로 넣기", "자동 넣기",
            "넣기 전에 묻기", "다시 묻기", "캡처 확인", "이미 있음", "중복 체결", "자동 인식", under = "invest.priceFill"),
        f("memo.search", "MEMO-03", "메모 빠른 검색", "글자·초성(ㅇㄱ)·날짜(10월 19일)·그날 근무(야간)·#태그 앞부분으로 찾고, ‘야간 날’ 칩으로 그 근무 날 메모만 봐요",
            ALL, "메모 › 검색 칸", at(Place.MEMOS), "메모 검색", "초성", "날짜로 찾기", "근무로 찾기", "야간 메모", "태그"),
        f("memo.shiftRemind", "MEMO-05", "근무 기준 메모 알림", "‘다음 야간 때’처럼 쓰면 다음 그 근무 출근 1시간 전(쉬는 날은 아침)에 알려 드릴지 묻고, 알람 창의 ‘근무에 맞춰’로도 골라요",
            ALL, "메모 › 메모 쓰기 › 알람 맞추기 › 근무에 맞춰", at(Place.MEMOS), "다음 야간", "출근 전 알림", "쉬는 날 알림", "근무 알림"),
        f("memo.lastShift", "MEMO-06", "지난 같은 근무 메모", "날짜 메모를 쓰면서 같은 근무였던 지난 날의 메모(인수인계)를 바로 봐요",
            ALL, "메모 › 날짜 메모 쓰기 › 지난 야간 메모 보기", at(Place.MEMOS), "인수인계", "지난 메모", "이어 보기", "같은 근무"),
        // AK 1.27 끝
        // AI 1.27 시작 (계획/1.27-AI-개발계획.md 441~445)
        f("plaza.info", "PLAZA-32", "회사 정보 광장", "회사 설정을 쓰거나 보내면 광장이 ‘우리 회사 정보 광장’으로 바뀌어요. 같은 회사 분들끼리만 참여자 번호로 자유롭게 이야기하고, 근무·급여 정보와 고친 점을 함께 봐요",
            ALL, "광장 › ‘회사’ 정보 광장", at(Place.PAY), "정보 광장", "회사 광장", "자유 토의", "회사 게시판", "동료", "익명", "고친 점"),
        f("plaza.styles", "PLAZA-33", "일 유형별 광장", "현장은 지역·직종 일당 시세와 현장 이야기, 가게는 영업·매출 팁, 알바는 지역 시급과 알바 이야기, 투자는 종목 추천 없는 투자 이야기를 나눠요",
            setOf(FIELD, SHOP, HOURLY, INVEST), "광장", at(Place.PAY), "현장 광장", "가게 광장", "알바 광장", "투자 이야기", "단가", "일당", "매출 팁"),
        // AI 1.27 끝
        // AG 1.27 시작 (계획/1.27-AG-개발계획.md 434·435, 결정 AG-D6)
        f("invite.nudge", "INVITE-11", "달력의 친구 초대 카드", "설치 사흘 뒤부터 2주에 한 번, 그날만 달력 위에 친구 초대 카드를 보여요. 닫거나 끌 수 있어요",
            ALL, "달력 맨 위 카드", at(Place.FEATURES), "초대 카드", "친구 초대 알림", "초대 권유", off = Off.APP),
        f("grow.card", "INVITE-12", "함께 만들어요 카드", "설정 맨 위에서 친구 초대와 ‘불편한 점 알려 주기’를 바로 해요. 보낸 문의에는 개발자가 답을 드려요",
            ALL, "설정 맨 위", at(Place.FEATURES), "불편한 점", "버그 신고", "오류 신고", "개선 제안", "건의", "함께 만들어요", off = Off.APP),
        // AG 1.27 끝
        // AH 1.27 시작 (계획/1.27-AH-개발계획.md 436~440)
        f("plaza.leadCycle", "PLAZA-14", "회사 대표 주기", "회사 이름을 치는 대로 같은 회사 분들이 쓰는 대표 주기와 쓰는 사람 수가 보여요. 고른 주기를 나누면 그 회사(또는 사업장)의 대표 주기가 되고, 맞아요·달라요로 다듬어요",
            setOf(WorkStyle.SHIFT), "설정 › 내 근무 › 근무 주기 › 회사 이름으로 근무주기 찾기", at(Place.PATTERN), "대표 주기", "사업장", "맞아요", "달라요",
            "쓰고 있어요", "회사 이름", "자동 완성", "다른 사람이 사용"),
        f("plaza.payFind", "PLAZA-16", "우리 회사 급여 방식 찾기", "급여 간편 설정에서 회사 이름으로 같은 회사 분들이 확인한 계산 방식을 찾아요. 쓰기 전에는 ‘급여 방식 있음 · 맞아요 n명’만 보이고, 쓰면 내 금액만 넣어요",
            ALL, "급여 › 급여 계산 설정 › 우리 회사 급여 방식 찾기", at(Place.PAY), "급여 방식 찾기", "회사 급여", "계산 방식", "수당 가져오기"),
        // AH 1.27 끝
        // ---- 1.27 묶음 AJ (계획/1.27-AJ-개발계획.md 446~449) ----
        f("photo.sameCheck", "READ-17", "사진과 같은가요?", "듀티표·명세서 사진을 읽으면 사진 옆에 읽은 칸·줄을 보여 주고 같은지 물어요. 다른 곳만 눌러 그 자리에서 고치고, 고친 글자는 다음부터 배워요",
            setOf(SHIFT, HOURLY), "설정 › 내 근무 › 한 달 근무 넣기 › 사진으로 › 사진과 내용이 같은가요?", at(Place.DUTY_ROSTER), "사진과 같은가요", "다른 곳이 있어요",
            "잘못 읽음", "인식 오류", "칸 고치기", "줄 고치기", "사진 인식", under = "shift.roster"),
        f("photo.scanner", "READ-15", "스캐너로 반듯하게 찍기", "구글 문서 스캐너가 휴대폰 안에서 표 테두리를 찾고 비스듬한 사진을 펴고 그림자를 지워요. 펴진 사진을 여러 방법으로 읽어 맞춰 봐요",
            setOf(SHIFT, HOURLY), "설정 › 내 근무 › 한 달 근무 넣기 › 스캐너로 반듯하게 찍기", at(Place.DUTY_ROSTER), "스캐너", "문서 스캐너", "반듯하게",
            "그림자", "비스듬", "사진 인식", under = "shift.roster"),
        f("pay.ruleNeeds", "PAYS-17", "이것만 넣으면 정확해져요", "명세서 없이 회사 규칙만 넣어도 달력 근무로 급여를 계산해요. 비어 있거나 짐작한 기본급·월 소정근로시간·근무 시각·수당 금액은 급여 합계 바로 아래에서 바로 넣어요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 이것만 넣으면 정확해져요", at(Place.PAY), "규칙만", "명세서 없이", "시수", "월 소정근로시간",
            "기본급 넣기", "0원", "필수 값", under = "pay.calc"),
        // ---- 1.32 (계획/1.32-개발계획.md 554~614, U 사용성 조사) ----
        f("view.textSize", "UX132-05", "글자 크기", "글자를 작게·보통·크게·더 크게·아주 크게 다섯 단계로 바꿔요. 휴대폰 글꼴 크기와 함께 적용되고, 화면 배치가 글자에 맞춰 바뀌어요",
            ALL, "설정 › 화면과 글자 › 글자 크기", at(Place.SETTINGS_CALENDAR, "글자 크기"), "글자 크기", "글씨 크게", "큰 글씨", "노안", "작게", "안 보여요", "글꼴"),
        f("view.compact", "UX132-06", "촘촘하게", "카드 사이를 줄여 한 화면에 더 많이 보여요. 끄면 넉넉하게 띄워요",
            ALL, "설정 › 화면과 글자 › 촘촘하게", at(Place.SETTINGS_CALENDAR, "촘촘하게"), "촘촘", "넉넉", "간격", "밀도", "한 화면에 많이"),
        f("view.clock12", "WTYP-07", "오전/오후로 보기", "시각을 '오후 3:05'처럼 12시간으로 보여요. 기본은 '15:05'예요",
            ALL, "설정 › 화면과 글자 › 오전/오후로 보기", at(Place.SETTINGS_CALENDAR, "오전/오후로 보기"), "오전", "오후", "12시간", "24시간", "시각 표기"),
        f("pay.fixChips", "UX132-02", "급여 고치기 칩", "근무·급여 맨 위 예상 급여 카드 아래 기본급·수당·근무시간·월급날·회사 규칙 칩을 누르면 그 값으로 바로 가요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 고치기 칩", at(Place.PAY, "base"), "기본급 바꾸기", "수당 바꾸기", "월급날", "정산 기간", "바로 고치기", under = "pay.calc"),
        f("pay.tabs", "UX132-03", "급여 화면 네 탭", "급여 화면이 결과·내 급여 값·회사 규칙·도구로 나뉘어 고칠 곳을 바로 찾아요",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 결과·내 급여 값·회사 규칙·도구", at(Place.PAY), "내 급여 값", "회사 규칙", "도구", "급여 화면", under = "pay.calc"),
        f("pay.coverHours", "WTYP-07", "포괄 연장 시간", "고정 연장수당(포괄임금)이 정한 시간과 이번 달 실제 연장을 견주고, 넘으면 알려 줘요",
            PAID, "${tab(DAY)} › 포괄 연장 시간", at(Place.STATS), "포괄임금", "고정 연장", "고정OT", "연장 초과", off = Off.CALENDAR),
        f("pay.tenure", "WTYP-07", "근속", "입사일로 근속 기간과 다음 해까지 남은 날, 퇴직금 1년을 채웠는지 보여 줘요",
            PAID, "${tab(DAY)} › 근속", at(Place.STATS), "근속", "입사일", "퇴직금", "몇 년 차", off = Off.CALENDAR),
        f("all.yearIncome", "WTYP-07", "올해 달력별 수입", "달력이 둘 이상이면 올해 달력마다 들어온 돈과 몫을 보여 주고, N잡이면 5월 신고를 알려 줘요",
            ALL, "${tab(SHIFT)} › 전체 › 올해 달력별 수입", at(Place.STATS), "N잡", "투잡", "부업 수입", "달력별 수입", "종합소득세", off = Off.CALENDAR),
        f("widget.nextOff", "WTYP-05", "다음 오프까지", "다음 근무 위젯 아래에 다음 쉬는 날까지 며칠인지 보여 줘요",
            setOf(SHIFT, DAY, HOURLY), "홈 화면 › 다음 근무 위젯", at(Place.FEATURES), "다음 오프", "쉬는 날까지", "위젯", "카운트다운"),
        f("shift.pause", "WTYP-07", "명절·공장 휴무로 멈추기", "명절이나 공장 휴무로 며칠 쉬면 근무 순서를 그만큼 멈췄다가 이어 가요. 되돌리기가 있어요",
            setOf(SHIFT), "달력 › 날짜 누르기 › 순서 맞추기 › 며칠 멈추기", at(Place.CALENDAR), "명절", "휴무", "순서 멈추기", "공장 휴무", "설 연휴", "추석"),
        f("field.platform", "WTYP-02", "배달·운전 플랫폼", "배민·쿠팡이츠·대리운전 같은 플랫폼 일을 건수·금액·운행 시간으로 적고, 주마다 정산 날과 시간당 순수익을 보여 줘요",
            setOf(FIELD), "설정 › 내 근무 › 일하는 방식 › 플랫폼, 달력 › 날짜 누르기", at(Place.CALENDAR), "배달", "배민", "쿠팡이츠", "대리운전", "라이더", "정산", "시간당"),
        f("field.rateFrom", "WTYP-04", "이 날부터 일당 바꾸기", "일당이 오르면 그날부터만 새 일당으로 다시 셈해요. 공수는 소수 셋째 자리까지 넣어요",
            setOf(FIELD), "달력 › 날짜 누르기 › 공수 › 직접", at(Place.CALENDAR), "일당 인상", "단가 변경", "공수 소수", "0.125"),
        f("shop.staffFree", "WTYP-03", "직원 근무표 3명 무료", "직원 3명까지 근무표와 인건비를 무료로 써요. 4명부터와 직원별 PDF는 프로예요",
            setOf(SHOP), "${tab(SHOP)} › 직원 인건비 › 직원", at(Place.STATS), "직원", "알바 근무표", "인건비", "직원 PDF", under = "shop.staff"),
        f("pay.workProof", "WTYP-06", "근무 기록 증빙", "고른 기간의 날짜별 근무·휴게·실제 시각과 주 52시간 넘은 주를 PDF 한 장으로 만들어요(프로)",
            PAID, "${tab(SHIFT)} › ○월 예상 급여 › 도구 › 급여 리포트 › 근무 기록 증빙", at(Place.PAY, "tools"), "근무 기록", "증빙", "노동청", "52시간", "출퇴근 기록 PDF"),
        f("money.netLine", "TAXP-01", "실수령으로 보기", "요약의 들어온 돈을 명세서·입금·추정 순으로 실수령으로 보여 줘요. 추정 세후는 프로예요",
            PAID, "가계부 › 요약 › 들어온 돈", at(Place.MONEY), "실수령", "세후", "들어온 돈", "입금"),
        f("money.cardBill", "TAXP-04", "다음 카드값", "카드마다 이번 청구 기간에 쓴 돈과 지금 속도로 쓰면 얼마가 될지 알려 줘요(프로)",
            ALL, "가계부 › 요약 › 다음 카드값", at(Place.MONEY), "카드값", "카드 대금", "청구", "결제일", "할부", off = Off.ELSEWHERE),
        f("money.shiftSpend", "TAXP-05", "근무별 소비", "야간 다음 날·쉬는 날처럼 근무별로 하루에 쓰는 돈과 연장 많은 달의 지출을 견줘요(프로)",
            setOf(SHIFT), "가계부 › 요약 › 근무별 소비", at(Place.MONEY), "야간 다음 날", "근무별 지출", "소비 습관", off = Off.ELSEWHERE),
        f("money.yearReport", "TAXP-09", "연간 가계부 리포트", "한 해 수입·지출·분류별 합계를 PDF로 만들어요(프로). CSV는 무료예요",
            ALL, "가계부 › 통계 › 연간 리포트", at(Place.MONEY, "report"), "연간 리포트", "1년 정리", "CSV", "PDF", "가계부 내보내기"),
        f("field.mayPack", "TAXP-10", "3.3% 합계와 5월 신고 준비", "올해 떼인 3.3%와 업체별 수입을 모아 5월 종합소득세 신고 준비 묶음을 만들어요(프로)",
            setOf(FIELD), "장부 › 요약 › 3.3% 합계", at(Place.MONEY), "3.3%", "원천징수", "5월 신고", "종합소득세", "프리랜서"),
        f("shop.salesSplit", "TAXP-06", "매출 나눠 보기", "매출을 현금·카드·이체·배달앱으로 나눠 보고, 카드 매출 입금이 늦으면 알려 줘요",
            setOf(SHOP), "장부 › 요약 › 매출 나눠 보기", at(Place.MONEY), "카드 매출", "현금 매출", "배달앱", "입금 대조", "카드 수수료"),
        f("money.subscriptions", "LEDX-09", "구독 점검", "오래 안 쓴 반복 결제를 모아 보여 줘요",
            ALL, "가계부 › 요약 › 구독 점검", at(Place.MONEY), "구독", "정기 결제", "넷플릭스", "해지", off = Off.ELSEWHERE),
        f("money.cardThreshold", "LEDX-10", "카드 소득공제 문턱", "총급여 25%까지는 신용카드, 넘으면 체크카드가 유리한지 게이지로 보여 줘요",
            PAID, "가계부 › 요약 › 카드 소득공제 문턱", at(Place.MONEY), "소득공제", "25%", "체크카드", "신용카드", "연말정산", off = Off.ELSEWHERE),
        f("money.learnSort", "LEDX-05", "분류 배우기", "적은 내역에서 휴대폰 안에서 분류를 배워, 자신 없으면 후보 두 개를 칩으로 보여 줘요",
            ALL, "가계부 › 내역 › 분류 후보", at(Place.MONEY), "자동 분류", "카테고리 추천", "분류 학습", under = "money.rules"),
        f("money.inCells", "LEDX-07", "날짜 밑에 그날 쓴 돈", "달력 날짜 밑에 그날 쓴 돈을 작게 보여 줘요",
            ALL, "설정 › 화면과 글자 › 날짜 밑에 그날 쓴 돈", at(Place.SETTINGS_CALENDAR, "날짜 밑에 그날 쓴 돈"), "달력 지출", "그날 쓴 돈", "칸에 금액", off = Off.ELSEWHERE),
        f("pay.depositCompare", "PAYA-05", "월급 들어오면 바로 비교", "급여 입금 알림이 오면 예상 급여와 바로 견줘 알려 줘요",
            PAID, "설정 › 알림과 잠 › 급여 입금 비교", at(Place.SETTINGS_NOTICES, "급여 입금 비교"), "월급 입금", "급여 알림", "예상과 차이", "덜 받은 돈", off = Off.ELSEWHERE),
        // 1.32 조사 대조 (계획/1.32-조사-대조표.md): 빠졌거나 줄었던 가계부·프로 기능.
        f("money.merchant", "LEDX-12", "가맹점 이름 정리", "알림의 ‘(주)’·지점·결제대행 이름을 덜어 목록에 알아보기 쉬운 이름으로 보여요. 원래 이름은 그대로 남아요",
            ALL, "가계부 › 내역 › 항목 누르기", at(Place.MONEY), "가맹점", "상호", "결제대행", "이름 정리", "KG이니시스", under = "money.auto"),
        f("money.route", "LEDX-13", "결제 경로", "네이버페이·카카오페이·토스로 낸 돈을 어느 카드·계좌로 냈는지 함께 적고, 충전은 지출로 두 번 세지 않아요",
            ALL, "가계부 › 내역 › 항목 누르기", at(Place.MONEY), "간편결제", "네이버페이", "카카오페이", "토스페이", "충전", "두 번", under = "money.auto"),
        f("money.billCheck", "LEDX-14", "카드값 맞춰 보기", "카드 대금이 빠져나가면 그 카드 청구 기간에 적은 돈과 견줘 빠진 결제가 있는지 알려 줘요",
            ALL, "가계부 › 내역 › 카드값 항목", at(Place.MONEY), "카드 대금", "청구서", "빠진 결제", "카드값 차이", under = "money.auto"),
        f("money.asks", "LEDX-16", "확인할 것", "원래 결제를 못 찾은 환불과 처음 보는 카드를 모아 한 번에 물어요. 환불은 원래 결제와 짝지어 지출에서 빼요",
            ALL, "가계부 › 요약 › 확인할 것", at(Place.MONEY), "환불", "취소", "처음 보는 카드", "체크카드", "짝 찾기", off = Off.ELSEWHERE),
        f("money.installPlan", "LEDX-17", "할부 청구 기준", "할부를 달마다 나갈 돈·수수료와 남은 잔액으로 보여 주고, 무이자가 아니면 수수료를 연 %로 넣어 셈해요",
            ALL, "가계부 › 요약 › 할부 청구 기준", at(Place.MONEY), "할부", "할부 수수료", "남은 할부", "무이자", "할부 잔액", off = Off.ELSEWHERE),
        f("money.tune", "LEDX-15", "더 정확하게 적기", "내 이름(내 계좌끼리 옮긴 돈 가려내기), 해외 결제 수수료와 자주 쓰는 환율을 정해 자동 기록을 더 맞게 해요",
            ALL, "가계부 › ⋮ › 자동 기록 › 더 정확하게 적기", at(Place.AUTO_RECORD), "내 이름", "내 계좌 이체", "해외 결제", "환율", "해외 수수료", under = "money.auto"),
        f("money.heat", "LEDX-23", "지출 히트맵", "많이 쓴 날일수록 달력 날짜 아래 줄을 진하게 칠해 이달 소비 흐름을 한눈에 봐요. 금액은 보이지 않아요",
            ALL, "설정 › 화면과 글자 › 지출 히트맵", at(Place.SETTINGS_CALENDAR, "지출 히트맵"), "히트맵", "많이 쓴 날", "소비 패턴", "지출 색", off = Off.ELSEWHERE),
        f("pro.moments", "PROX-02", "돈이 걸린 날 하루 무료 권유", "급여일·근무표 넣은 날·신고 달에만, 그날 쓸 프로 기능을 하루 무료로 열어 볼지 한 번 물어요. 2주에 한 번까지예요",
            ALL, "달력 맨 위 카드", at(Place.FEATURES), "하루 무료", "체험", "프로 권유", "광고 보고 열기", off = Off.APP),
        f("backup.nudge", "STAB-08", "백업 안 한 지 30일 알림", "매일 자동 백업을 쓰지 않을 때 마지막 백업이 30일 지나면 달력 위에 한 줄로 알려 줘요. 데이터 화면에 마지막 백업 날도 보여요",
            ALL, "설정 › 데이터 › 백업 안 한 지 30일 알림", at(Place.SETTINGS_DATA, "백업 안 한 지 30일 알림"), "백업 알림", "마지막 백업", "기록 잃어버림", "백업 언제", off = Off.ELSEWHERE),
        f("stats.range", "CALX-06", "통계 기간 고르기", "통계를 1일~말일, 급여 기간, 주, 직접 고른 날로 세고, 달력 위에 이번 급여 기간이 끝나는 날을 한 줄로 보여 줘요",
            PAID, "통계 › 기간 칩", at(Place.STATS), "기간", "급여 기간", "주별", "직접 기간", "마감일", "주급"),
        f("shift.callout", "WTYP-09", "비상소집 한 번에 기록", "쉬는 날 불려 나가면 날짜를 누르고 ‘비상소집’ 한 번으로 시작·끝 시간을 추가 근무로 넣어요",
            setOf(SHIFT, DAY), "$DAY_SHEET › 비상소집", at(Place.TODAY), "비상소집", "동원", "소집", "불려 나감", "비번 출근", off = Off.CALENDAR),
        f("hours.stretch", "WTYP-12", "연속 근무 경고·전공의 한도", "당직 뒤 바로 이어진 근무까지 한 번에 센 시간이 정한 시간을 넘으면 알려 주고, 전공의 값(주 80·평균 72·연속 24시간)을 한 번에 정해요",
            setOf(SHIFT, DAY), "설정 › 알림과 잠 › 근무시간 경고 › 연속 근무 경고", at(Place.SETTINGS_NOTICES, "연속 근무 경고"), "전공의", "연속 근무", "연속 24시간", "주 80시간", "당직"),
        f("day.selective", "WTYP-15", "선택근무 정산 기간", "선택근무제 정산 기간(1개월·3개월) 기준 시간(일수 ÷ 7 × 40시간)과 일한 시간, 남은 시간과 하루 평균을 보여 줘요",
            setOf(DAY), "${tab(DAY)} › 선택근무 정산 기간", at(Place.STATS), "선택근무", "선택적 근로시간제", "정산 기간", "유연근무", "코어타임", off = Off.CALENDAR),
        f("day.leaveTime", "WTYP-16", "퇴근 시각 알림", "근무가 끝나는 시각에 소리 없이 알려 주고, 알림의 ‘지금 퇴근’을 누르면 잔업을 그 시각까지 적어요",
            setOf(DAY), "설정 › 알림과 잠 › 퇴근 시각 알림", at(Place.SETTINGS_NOTICES, "퇴근 시각 알림"), "지금 퇴근", "퇴근 버튼", "잔업 기록", "퇴근 알림", off = Off.ELSEWHERE),
        f("public.tracks", "WTYP-25", "직렬로 한 번에(교사·군인)", "시간외 상한을 정할 때 일반 행정·교사·군인을 고르면 그 직렬의 상한과 자주 쓰는 추가 근무(학부모 상담·생활지도·당직·훈련)를 한 번에 넣어요",
            setOf(DAY), "${tab(DAY)} › 시간외 상한 › 바꾸기 › 직렬로 한 번에", at(Place.STATS), "교사", "교원", "군인", "직렬", "학부모 상담", "생활지도", "당직", "시간외 상한",
            under = "day.overtimeCap"),
        f("shift.wishOff", "WTYP-11", "희망 오프", "다음 달 쉬고 싶은 날을 ♡로 표시하고, 신청 마감 이틀 전과 당일에 고른 날 수를 알려 줘요. 고른 날은 복사해 보낼 수 있어요",
            setOf(SHIFT), "$DAY_SHEET › 희망 오프", at(Place.TODAY), "희망 오프", "희망 휴무", "오프 신청", "듀티 신청", "신청 마감", off = Off.CALENDAR),
        f("invest.yearEnd", "WTYP-29", "연말 손실 정리", "손실 난 해외 주식을 지금 값으로 팔면 올해 양도세가 얼마 줄어드는지 미리 보고, 코인이 있으면 12월 31일에 시가를 적어 두라고 알려 줘요",
            setOf(INVEST), "${tab(INVEST)} › 통계 › 연말 손실 정리 미리 보기", at(Place.STATS, "invest.stats"), "손실 정리", "절세 매도", "연말 매도", "코인 시가", "12월 31일",
            off = Off.CALENDAR, under = "invest.tax"),
    )

    private val byId: Map<String, AppFeature> = all.associateBy { it.id }

    fun get(id: String): AppFeature? = byId[id]

    /** The features of [style], in the registry's order. */
    fun of(style: WorkStyle?): List<AppFeature> = all.filter { it.fits(style) }
}
