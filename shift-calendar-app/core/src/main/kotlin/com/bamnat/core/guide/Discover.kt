package com.bamnat.core.guide

import com.bamnat.core.formula.NoticeOverlap
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 1.40 [DISC-01]~[DISC-05] 안 써 본 기능 가끔 알려 주기 (사용자 2026-11-11: "밤낮은 많은 기능들이 있는데 이것을 모르고 오랫동안 사용 안 하고
 * 있다면 가끔 알려 주는 기능이 필요해. 위트 있고 디자인이 뛰어나야 해. 너무 불편하지 않아야 하고 좋은 빈도를 거쳐야 해").
 * 설계 정본: docs/plans/1.40-이후-논의/안쓰는기능-알려주기.md. Everything here is decided on the phone: nothing is sent.
 */

/** [DISC-02] What a feature needs before it is worth telling about, so a tap never lands on an empty screen. */
enum class DiscoverNeed {
    /** The calendar has days of work (a pattern or painted days). */
    DAYS,
    /** The pay is set up. */
    PAY,
    /** A field calendar has jobs. */
    JOBS,
    /** A shop has written sales. */
    SALES,
    /** An investor has trades. */
    TRADES,
    /** The 가계부 has entries. */
    MONEY,
}

/**
 * [DISC-02] The card's words for one feature: a [title] with the wit (the person's situation, never the feature's name), a [body] that
 * says only what the feature does (its registry `what`), the button, and how much it is worth knowing ([value], 1~10).
 * [styles] narrows a feature for every kind of work to the kinds whose words fit it; [notice] is the app's own notice it turns on.
 */
data class Discover(
    val title: String,
    val body: String,
    val action: String,
    val value: Int,
    val needs: DiscoverNeed? = null,
    val styles: Set<WorkStyle>? = null,
    val notice: NoticeOverlap.BuiltIn? = null,
)

/** [DISC-01] What the person did with a card. */
enum class DiscoverAnswer {
    /** 열어 보기. */
    OPENED,
    /** 나중에: the same gap, and this feature may come once more after [DiscoverRules.LATER_AGAIN_DAYS]. */
    LATER,
    /** ×, or the day ended with no answer: the gap doubles. */
    IGNORED,
    /** ⋮ › 이미 써요: counted as used. */
    USED,
    /** ⋮ › 이 기능은 안 볼래요. */
    NEVER,
    /** ⋮ › 이런 안내 줄이기: the gap doubles, not counted as ignored. */
    FEWER,
}

/** [DISC-04] One feature told about: when and what was answered (null while it is today's card). */
data class DiscoverTold(val id: String, val on: LocalDate, val answer: DiscoverAnswer? = null)

/**
 * [DISC-01] What this phone remembers about the cards (설정 파일 `discover`, on the phone only, not in a backup): every card shown and
 * its answer, the gap to the next, the unanswered cards in a row, the earliest day of the next card, the day a backup came back,
 * and the last alarm's end.
 */
data class DiscoverState(
    val told: List<DiscoverTold> = emptyList(),
    val gap: Int = DiscoverRules.FIRST_GAP,
    val ignoredInRow: Int = 0,
    val nextOn: LocalDate? = null,
    val restoredOn: LocalDate? = null,
    val alarmEndedAt: LocalDateTime? = null,
) {
    /** Today's card, if one was shown today. */
    fun todays(today: LocalDate): DiscoverTold? = told.lastOrNull()?.takeIf { it.on == today }
}

/** [DISC-01] The facts of today the app reads, from which the quiet days follow. */
data class DiscoverFacts(
    val now: LocalDateTime,
    val installedOn: LocalDate?,
    val setupDone: Boolean,
    /** '이 달력으로 할 수 있는 것' (its first 14 days) or the 시작 단계 card is still up. */
    val guideUp: Boolean,
    /** Another card above the month has something to say (this card is always the last). */
    val othersUp: Boolean,
    /** The first setup's day ([quietToday]). */
    val quietToday: Boolean,
    /** The day this version was put on the phone (업데이트 소식 has those days). */
    val updatedOn: LocalDate? = null,
    /** The day the app last stopped by itself. */
    val crashedOn: LocalDate? = null,
    /** Today's one-line tip ([GuideTips]) was shown: one guide a day. */
    val tipToday: Boolean = false,
)

/** [DISC-01] The rules of when (사용자 결정 2026-11-11 빈도 A: 7일에 한 장, 안 눌러 주면 간격 두 배 7→14→28→56일). */
object DiscoverRules {
    const val FIRST_GAP = 7
    const val MAX_GAP = 56
    /** Unanswered this many times in a row: a long rest. */
    const val IGNORED_REST = 3
    const val REST_DAYS = 60L
    /** No card until the app has been on the phone this long (처음에는 '할 수 있는 것' 안내가 있어요). */
    const val AFTER_INSTALL_DAYS = 21L
    /** After a backup came back the 써 봄 marks are gone, so it rests this long. */
    const val AFTER_RESTORE_DAYS = 30L
    const val AFTER_UPDATE_DAYS = 3L
    const val AFTER_CRASH_DAYS = 7L
    const val AFTER_ALARM_MINUTES = 5L
    /** Opened between 22:00 and 06:00: no new things to try before sleep. */
    const val NIGHT_FROM = 22
    const val NIGHT_UNTIL = 6
    const val MONTH_MAX = 4
    const val YEAR_MAX = 20
    /** A feature put off with 나중에 may come once more after this many days. */
    const val LATER_AGAIN_DAYS = 60L
    /** Once everything was told, one this many days apart. */
    const val ALL_TOLD_GAP = 60L
    /** A 프로 feature is at most one card in this many, and only once every free one was told. */
    const val PRO_ONE_IN = 4

    /** The day [s] settles: yesterday's card (or older) with no answer counts as ignored. */
    fun settle(s: DiscoverState, today: LocalDate): DiscoverState {
        val last = s.told.lastOrNull() ?: return s
        if (last.answer != null || !last.on.isBefore(today)) return s
        return answer(s, last.on, DiscoverAnswer.IGNORED)
    }

    /** [s] after the answer to today's card ([on] its day). */
    fun answer(s: DiscoverState, on: LocalDate, a: DiscoverAnswer): DiscoverState {
        val told = s.told.toMutableList()
        val i = told.indexOfLast { it.on == on }
        if (i >= 0 && told[i].answer == null) told[i] = told[i].copy(answer = a)
        val ignored = if (a == DiscoverAnswer.IGNORED) s.ignoredInRow + 1 else 0
        val gap = when (a) {
            DiscoverAnswer.OPENED -> FIRST_GAP
            DiscoverAnswer.IGNORED, DiscoverAnswer.FEWER -> (s.gap * 2).coerceAtMost(MAX_GAP)
            else -> s.gap
        }
        val next = if (ignored >= IGNORED_REST) on.plusDays(REST_DAYS) else on.plusDays(gap.toLong())
        return s.copy(told = told, gap = gap, ignoredInRow = if (ignored >= IGNORED_REST) 0 else ignored, nextOn = next)
    }

    /** [s] with today's card shown (once a day; the same card stays all day). */
    fun shown(s: DiscoverState, id: String, today: LocalDate): DiscoverState =
        if (s.todays(today) != null) s else s.copy(told = s.told + DiscoverTold(id, today))

    /** [DISC-04] '다시 알려 주세요': the feature may be told about again. */
    fun again(s: DiscoverState, id: String): DiscoverState = s.copy(told = s.told.filter { it.id != id || it.answer == null })

    /** Whether a new card may come today at all (before picking the feature). */
    fun quiet(s: DiscoverState, f: DiscoverFacts): Boolean {
        val today = f.now.toLocalDate()
        if (!f.setupDone || f.quietToday || f.guideUp || f.othersUp || f.tipToday) return true
        if (f.installedOn == null || today.isBefore(f.installedOn.plusDays(AFTER_INSTALL_DAYS))) return true
        if (s.restoredOn != null && today.isBefore(s.restoredOn.plusDays(AFTER_RESTORE_DAYS))) return true
        if (f.updatedOn != null && today.isBefore(f.updatedOn.plusDays(AFTER_UPDATE_DAYS))) return true
        if (f.crashedOn != null && today.isBefore(f.crashedOn.plusDays(AFTER_CRASH_DAYS))) return true
        if (s.alarmEndedAt != null && !f.now.isAfter(s.alarmEndedAt.plusMinutes(AFTER_ALARM_MINUTES)) && !f.now.isBefore(s.alarmEndedAt)) return true
        if (f.now.hour >= NIGHT_FROM || f.now.hour < NIGHT_UNTIL) return true
        if (s.nextOn != null && today.isBefore(s.nextOn)) return true
        val month = s.told.count { ChronoUnit.DAYS.between(it.on, today) in 0..29 }
        val year = s.told.count { ChronoUnit.DAYS.between(it.on, today) in 0..364 }
        return month >= MONTH_MAX || year >= YEAR_MAX
    }
}

/** [DISC-02] The features worth telling about, and their words. A feature not here never comes on the card. */
object DiscoverCatalog {
    private val SHIFT = WorkStyle.SHIFT
    private val DAY = WorkStyle.DAY
    private val HOURLY = WorkStyle.HOURLY
    private val WORK = setOf(SHIFT, DAY, HOURLY)

    val lines: Map<String, Discover> = linkedMapOf(
        // 교대
        "alarm.check" to Discover("알람, 진짜 울릴까 불안한 밤엔", "다음 알람과 꺼진 권한을 하나씩 확인하고 그 자리에서 고쳐 줘요.", "점검해 보기", 9),
        "shift.swap" to Discover("바꿔 준 근무, 누가 갚을 차례더라", "날짜마다 누구와 바꿨는지 적어 두면 달마다 사람별로 모아 줘요.", "적어 보기", 8, DiscoverNeed.DAYS),
        "shift.health" to Discover("야간이 몸에 남긴 1년 성적표", "야간 비율과 11시간 못 쉰 날을 1년으로 모아 보여 줘요.", "열어 보기", 7, DiscoverNeed.DAYS),
        "shift.restRules" to Discover("우리 일터 기준이 따로 있다면", "휴식 11시간·연속 야간 같은 근무표 점검 기준을 일터에 맞춰 바꿔요.", "기준 맞추기", 5),
        "sleep.adapt" to Discover("주간에서 나이트로, 몸이 헷갈릴 때", "근무가 바뀌기 3일 전부터 하루 30분씩 잘 시간을 옮기게 도와요.", "열어 보기", 7, DiscoverNeed.DAYS),
        "pay.gap" to Discover("월급날, 통장이 계산과 다르다면", "급여일에 들어온 급여와 계산한 급여의 차이를 알려 줘요.", "견줘 보기", 7, DiscoverNeed.PAY),
        // 일근
        "day.leaveNow" to Discover("퇴근 시간은 지났는데 아직 책상 앞", "‘지금 퇴근’을 누르면 더 일한 시간이 야근으로 적혀요.", "열어 보기", 9),
        "day.bridges" to Discover("연차 하루로 닷새 쉬는 달력", "연차 하루이틀로 길게 쉬는 날을 12개월 안에서 찾아 줘요.", "찾아 보기", 8, styles = setOf(DAY, SHIFT)),
        "day.overtimeCap" to Discover("이번 달 야근, 어디까지 괜찮을까", "한 달 시간외 상한을 정하면 남은 시간과 넘었을 때를 알려 줘요.", "상한 정하기", 6),
        "day.attendanceCompare" to Discover("회사 근태와 내 기록이 다를 때", "근태 엑셀을 고르면 내 기록과 다른 날과 회사가 덜 센 시간을 보여 줘요.", "견줘 보기", 6),
        "day.commute" to Discover("출퇴근 기록, 휴대폰이 대신 적어요", "회사에 닿고 떠날 때를 알아채 적어요. 위치는 휴대폰 안에만 있어요.", "켜는 곳 보기", 6),
        "leave.ledger" to Discover("내 연차, 정말 그만큼 남았을까", "입사일로 연차를 법대로 세고 쓴 날·남은 날을 한 줄씩 보여 줘요.", "열어 보기", 8, styles = setOf(DAY, SHIFT)),
        "pay.whatIf" to Discover("월급이 오르면 이 달은 얼마일까", "기본급·시급을 올리거나 근무를 더하면 이 달이 얼마인지 바로 봐요.", "계산해 보기", 6, DiscoverNeed.PAY),
        // 알바
        "hourly.takeHome" to Discover("통장에 찍힐 돈이 궁금하다면", "없음·3.3%·4대보험 중에 골라 손에 쥐는 돈을 보여 줘요.", "골라 보기", 9),
        "hourly.weeks" to Discover("이번 주 15시간, 채웠을까", "주마다 근무시간과 주휴수당을 받을 수 있는지 보여 줘요.", "주별로 보기", 9, DiscoverNeed.DAYS),
        "hourly.breaks" to Discover("쉬는 시간까지 일한 시간으로 세고 있다면", "4시간에 30분, 8시간에 1시간 법정 휴게를 저절로 빼요.", "켜는 곳 보기", 6),
        "hourly.offers" to Discover("두 알바 사이에서 고민 중이라면", "시급·주휴·세금·교통비까지 따져 시간당 남는 돈을 나란히 봐요.", "견줘 보기", 7),
        "hourly.contract" to Discover("계약서는 사진 한 장으로 챙겨 두면", "계약서 사진과 시급·기간을 보관하고 끝나기 7일 전에 알려 줘요.", "보관해 보기", 6),
        "hourly.attendance" to Discover("늦은 날도 한 번만 눌러 적어요", "지각·조퇴·결근을 하루 화면에서 한 번에 적고 대타는 대근으로 남겨요.", "열어 보기", 5, DiscoverNeed.DAYS),
        // 현장
        "field.due" to Discover("안 들어온 돈은 달력이 대신 기억해요", "일감마다 입금 약속일을 정하면 지나도 안 들어왔을 때 알려 줘요.", "정해 보기", 9, DiscoverNeed.JOBS),
        "field.quote" to Discover("견적, 말로만 하면 금방 잊혀요", "품목·수량·단가로 견적서 사진을 만들어 보내고 좋다면 일감으로 저장해요.", "만들어 보기", 7),
        "field.deposit" to Discover("입금 문자 올 때마다 장부를 뒤진다면", "은행 입금 알림을 받을 돈이 남은 일감에 바로 맞춰요.", "이어 보기", 6, DiscoverNeed.JOBS),
        "field.year" to Discover("5월 종합소득세, 미리 모아 두면 편해요", "올해 번 돈·뗀 3.3%·경비와 출역일을 한곳에 모아 봐요.", "모아 보기", 7, DiscoverNeed.JOBS),
        "field.status" to Discover("가계약인지 확정인지 헷갈린다면", "일감마다 가계약 → 확정 → 완료 → 입금으로 상태를 바꿔요.", "바꿔 보기", 5, DiscoverNeed.JOBS),
        "field.dailyTax" to Discover("일당에서 떼는 세금, 얼마쯤일까", "이번 달 일당에서 떼는 일용근로소득세를 보여 줘요.", "열어 보기", 5, DiscoverNeed.JOBS),
        // 자영업
        "shop.cardDue" to Discover("카드 매출, 언제 들어오더라", "수수료를 뗀 카드 매출이 언제 들어오는지 달력에 보여 줘요.", "달력에서 보기", 9, DiscoverNeed.SALES),
        "shop.cash" to Discover("마감 때 돈통이 안 맞는다면", "있어야 할 현금과 센 현금의 차이를 바로 알려 줘요.", "맞춰 보기", 7, DiscoverNeed.SALES),
        "shop.target" to Discover("오늘 얼마 팔면 이번 달 목표일까", "한 달 목표를 정하면 남은 영업일로 나눈 하루 목표를 보여 줘요.", "정해 보기", 8),
        "shop.weather" to Discover("비 오는 날 매출, 정말 줄까", "하루 마감에 날씨를 고르면 비 온 날과 맑은 날 평균을 견줘요.", "열어 보기", 6, DiscoverNeed.SALES),
        "shop.simpleTax" to Discover("올해 매출, 간이과세 선을 넘을까", "올해 매출과 연말 예상을 간이과세 기준과 견줘요.", "견줘 보기", 6, DiscoverNeed.SALES),
        "shop.vat" to Discover("부가세 달이 오기 전에", "반기 매출과 경비로 대략의 부가세를 보고 신고 달에 알려 줘요.", "어림해 보기", 7, DiscoverNeed.SALES),
        "shop.split" to Discover("카드냐 현금이냐, 이번 달은", "이번 달 매출을 카드·현금·계좌·배달앱으로 나눠 보여 줘요.", "열어 보기", 5, DiscoverNeed.SALES),
        // 투자
        "invest.journal" to Discover("그때 왜 샀더라", "이유와 복기를 남기면 나중에 마음과 원칙별 손익을 볼 수 있어요.", "적어 보기", 9),
        "invest.goal" to Discover("언제까지 얼마, 정해 두면 보여요", "총자산·수익률 목표와 날짜를 정하면 진행률과 매달 넣을 돈을 알려 줘요.", "정해 보기", 8),
        "invest.mind" to Discover("조급할 때 산 종목의 성적은", "감정 태그와 원칙 지킴에 따라 손익을 나눠 봐요.", "열어 보기", 7, DiscoverNeed.TRADES),
        "invest.dividend" to Discover("배당이 들어오는 날의 작은 기쁨", "세전 금액과 원천징수, 실수령을 적고 종목별 합계를 봐요.", "적어 보기", 6, DiscoverNeed.TRADES),
        "invest.events" to Discover("배당락·실적 발표 날을 아침에", "적어 둔 투자 일정을 그날 아침 알려 줘요.", "적어 보기", 6),
        "invest.weekdays" to Discover("월요일엔 왜 늘 손이 갈까", "요일마다 손익과 평균 보유일을 봐요.", "열어 보기", 5, DiscoverNeed.TRADES),
        // 모든 유형
        "data.backup" to Discover("폰을 잃어버리는 건 늘 갑자기라서", "적어 둔 기록을 모두 파일로 만들어 새 휴대폰으로 옮겨요.", "백업해 두기", 9),
        "share.link" to Discover("매달 근무표 사진 찍어 보내는 중이라면", "앱 없이 열리는 링크 하나면 바뀐 근무도 따라가요.", "링크 만들기", 9, DiscoverNeed.DAYS, styles = WORK),
        "lock.today" to Discover("폰만 켜도 오늘 근무가 보이게", "오늘·내일 근무를 소리 없는 알림으로 잠금 화면에 늘 보여 줘요.", "켜는 곳 보기", 7, DiscoverNeed.DAYS,
            notice = NoticeOverlap.BuiltIn.TODAY),
        "sleep.noise" to Discover("잠이 안 오는 낮잠 시간엔", "빗소리·선풍기·파도를 섞어 틀고 정한 시간에 서서히 꺼져요.", "들어 보기", 7),
        "sleep.morning" to Discover("아침마다 피곤한 까닭을 찾고 싶다면", "깬 뒤 기분과 어젯밤 일을 눌러 남기면 내 기록에서 까닭을 찾아 줘요.", "남겨 보기", 7),
        "calendar.year" to Discover("올해 쉰 날, 한눈에 세어 볼까요", "열두 달을 한 화면에 근무 색으로 보고 종류별 날 수를 세요.", "펼쳐 보기", 6, DiscoverNeed.DAYS, styles = WORK),
        "money.owed" to Discover("빌려준 돈, 적어 두면 잊지 않아요", "빌려준 돈·외상을 적어 두고 받으면 표시해요.", "적어 보기", 6),
        "money.repeat" to Discover("매달 빠져나가는 돈 모아 보기", "석 달 넘게 비슷하게 나간 돈을 찾아 반복 결제로 정리하자고 해요.", "찾아 보기", 6, DiscoverNeed.MONEY),
        "memo.board" to Discover("메모에 사진과 스티커를 마음대로", "메모 안에 판을 넣고 사진·글상자·스티커를 아무 자리에 놓아요.", "꾸며 보기", 5),
    )

    /** [DISC-02] The words that make a card nag, blame or sell: never on a card (사용자 요청 '너무 불편하지 않아야'). */
    val NAGGING = listOf("아직도", "모르셨", "모르시", "왜 안", "놓치", "!", "꼭 ", "지금 바로", "사세요", "구매", "결제하", "서두르", "후회", "손해 보")

    const val TITLE_MAX = 24
    const val BODY_MAX = 48
    const val ACTION_MAX = 7

    fun of(id: String): Discover? = lines[id]
}

/** [DISC-01][DISC-05] Which feature today's card is about. */
object DiscoverPicker {
    data class Pick(val feature: AppFeature, val line: Discover, val isNew: Boolean)

    /**
     * Today's card, or null. [ready] says whether a [DiscoverNeed] holds now; [alreadyOn] whether the feature is effectively in use
     * already (its own switch is on, or the same notice is said by the app or a rule of mine, [NoticeOverlap]); [isPro] marks a 프로 feature.
     */
    fun pick(
        s: DiscoverState,
        guide: GuideState,
        calendar: String,
        style: WorkStyle?,
        facts: DiscoverFacts,
        ready: (DiscoverNeed) -> Boolean,
        alreadyOn: (AppFeature) -> Boolean = { false },
        isPro: (String) -> Boolean = ProCatalog::isPro,
    ): Pick? {
        val switch = FeatureRegistry.get(SWITCH) ?: return null
        if (guide.isOff(switch, calendar)) return null
        val today = facts.now.toLocalDate()
        fun fits(f: AppFeature): Boolean {
            val line = DiscoverCatalog.of(f.id) ?: return false
            return f.fits(style) && (line.styles == null || (style ?: WorkStyle.SHIFT) in line.styles) && !guide.isUsed(f.id) &&
                f.id !in guide.never && !guide.isOff(f, calendar) && (line.needs == null || ready(line.needs)) && !alreadyOn(f)
        }
        s.todays(today)?.let { t ->
            if (t.answer != null) return null
            val f = FeatureRegistry.get(t.id) ?: return null
            return if (fits(f)) Pick(f, DiscoverCatalog.of(f.id)!!, isNew = false) else null
        }
        if (DiscoverRules.quiet(s, facts)) return null
        val lastTold = s.told.groupBy { it.id }.mapValues { (_, v) -> v.last() }
        // Never told, or put off with 나중에 once and that long ago.
        fun fresh(id: String) = id !in lastTold
        fun again(id: String) = lastTold[id]?.let { it.answer == DiscoverAnswer.LATER && s.told.count { t -> t.id == id } == 1 &&
            !today.isBefore(it.on.plusDays(DiscoverRules.LATER_AGAIN_DAYS)) } == true
        val all = FeatureRegistry.all.filter(::fits)
        val free = all.filter { !isPro(it.id) }
        val pro = all.filter { isPro(it.id) }
        val proTold = s.told.count { isPro(it.id) }
        val proRoom = (proTold + 1) * DiscoverRules.PRO_ONE_IN <= s.told.size + 1
        val freshFree = free.filter { fresh(it.id) }
        val pool = when {
            freshFree.isNotEmpty() -> freshFree
            proRoom && pro.any { fresh(it.id) } -> pro.filter { fresh(it.id) }
            else -> {
                // Everything told: one now and then, from what was put off with 나중에.
                val last = s.told.lastOrNull()?.on
                if (last != null && today.isBefore(last.plusDays(DiscoverRules.ALL_TOLD_GAP))) return null
                (free + if (proRoom) pro else emptyList()).filter { again(it.id) }
            }
        }
        if (pool.isEmpty()) return null
        val usedPlaces = guide.used.mapNotNull { FeatureRegistry.get(it)?.route?.place }.toSet()
        // A change of scene now and then: after two cards from the same place, another place first.
        val recent = s.told.takeLast(2).mapNotNull { FeatureRegistry.get(it.id)?.route?.place }
        val tired = recent.size == 2 && recent[0] == recent[1]
        val next = pool.sortedWith(
            compareBy<AppFeature> { tired && it.route.place == recent[0] }
                .thenByDescending { it.route.place in usedPlaces }
                .thenByDescending { DiscoverCatalog.of(it.id)!!.value },
        ).first()
        return Pick(next, DiscoverCatalog.of(next.id)!!, isNew = true)
    }

    const val SWITCH = "guide.discover"
}
