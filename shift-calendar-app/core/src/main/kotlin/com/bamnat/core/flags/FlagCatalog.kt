package com.bamnat.core.flags

import com.bamnat.core.leave.HourlyLeave
import com.bamnat.core.ops.PolicyConsent
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate

/**
 * [FLAG-05] The book: every dark-merged row the app knows, in the order the developer app and 진단 list them. The
 * server keeps the same keys (계약 2-1, 묶음 AM); keys only the server knows are skipped by the app, except
 * `free.<FEATURE>` ([free]).
 * [FLAG-34] 1.28 통합: this book is the one source. `server/company-worker/src/flag-catalog.js` is made from it by
 * `FlagCatalogExportTest` (names, words, basis, risk, default, conditions, sunset, the 처리방침 seed and the condition
 * names), and that test fails while the server's copy is old. Change the words here, never on the server.
 */
object FlagCatalog {
    const val BILLING = "billing"
    const val ADS = "ads"
    const val LEAVE_HOURLY = "leave.hourly"
    /** [FLAG-16] 순번 459: the extra usage-stats items (켜진 기능 목록, 앱 시작 시간) that 처리방침 3.2 adds. */
    const val STATS_MORE = "stats.more"
    /** [PHOTO-09] 1.29 AQ: '사진이 달라요' counts (날짜·화면·이유·횟수) sent to the server, once 처리방침 3.3 names them. */
    const val PHOTO_WRONG = "photo.wrong"
    /** [ITEM-03] 1.30 BC (결정 D102): a company's one allowance amount (회사 공통 정액) goes with the setup to the 광장. */
    const val PLAZA_FIXED_AMOUNT = "plaza.fixed_amount"
    /** [STAT-08] 1.31 N4: how long lookups took (정해진 구간 값만), sent with 사용 통계 once 처리방침 3.5 names it. */
    const val STATS_LOOKUP = "stats.lookup"
    /** 1.39 [SETUP-20]: how many taps first setup took (횟수 구간과 일하는 방식만), sent with 사용 통계 once 처리방침 3.12 names it. */
    const val STATS_SETUP = "stats.setup"
    /** 1.39 [STAT2-02]: 진입·머묾·회복 (처음 연 날, 1·7·30일째, 7·30일 넘게 쉬다 다시 연 날) as 사용 통계 counts, once 처리방침 3.12 names them. */
    const val STATS_LIFE = "stats.life"
    /** 1.39 [OBS2-08]: the kind of work chosen (교대·일근·알바·현장·자영업·투자) with the 진입·머묾 counts, once 처리방침 3.12 names it. */
    const val STATS_KIND = "stats.kind"
    /** 1.39 [OBS2-08]: which features were used on a day the phone came back to within 7·30 days, once 처리방침 3.12 names it. */
    const val STATS_KEEP = "stats.keep"
    /** 1.40 [DISC-06]: the four daily totals of '안 써 본 기능 알려 주기' (보임·열어 봄·나중에·그만 보기, 기능 이름 없이), once 처리방침 3.13 names them. */
    const val STATS_DISC = "stats.disc"
    /** 1.40 [DISC-06]: the 처리방침 판 that first names the four totals (사용자 결정 2026-11-11 '합계 4가지만'). */
    const val DISCOVER_POLICY = "3.13"
    /** 1.32.1 [STAB-10]: 멈춤 관찰 기록 in the 점검 기록 of an inquiry, once 처리방침 3.6 names it. */
    const val DIAG_OBSERVE = "diag.observe"
    /** 1.33 [SHR-20]: a coworker's 근무표 링크 carries the company setup (no amounts), once 처리방침 3.7 names it. */
    const val SHARE_SETUP = "share.setup"
    /** 1.37 [SHR-31][SHR-32] 묶음 E: a 근무표 링크 also carries the sharer's 테마 and 근무 설정 (no amounts), once 처리방침 3.11 names them. */
    const val SHARE_LOOK = "share.look"
    /** 1.34 [RCP-37] 나눔 판: reading the plaza's recipes (no information sent but the phone's install hash, as any plaza read). */
    const val RECIPE_BROWSE = "recipe.browse"
    /** 1.34 [RCP-37] Putting a recipe up, once 처리방침 3.8 names it. */
    const val RECIPE_PUBLISH = "recipe.publish"
    /** 2.0.0 [RVM-21] MKT-32: the 장터 cards' small fonts come from our public files (GitHub Pages), once 처리방침 3.13 names it. */
    const val CARD_FONTS = "market.cardfonts"
    /** 2.0.0 [RVM-21] The 처리방침 판 whose 7-day line names where the card fonts come from (총괄 11-12: 게시일 + 7일). */
    const val CARD_FONT_POLICY = "3.13"
    /** 1.34 [RCP-37] 좋아요·안좋아요 (with a reason) on recipes and their talk, once 3.8 names it. */
    const val RECIPE_VOTE = "recipe.vote"
    /** 1.34 [RCP-37] Writing in a recipe's talk, and (1.35) 요청 판·구독, once 3.8 names it. */
    const val RECIPE_TALK = "recipe.talk"
    /** 1.38 [P3-01][TEAM-09] Phase 3 M24 L1 리더 팀 근무표 (설계 1-2), shipped off. */
    const val TEAM_LEADER = "team.leader"
    /** 1.38 [P3-01][BOSS-01] Phase 3 M25 B1 사장님 급여 대장, shipped off. */
    const val BOSS_PAYROLL = "boss.payroll"
    /** 1.38 [P3-01][HLTH-01] Phase 3 M23 계획 대 실제 잠, shipped off. */
    const val HEALTH_COMPARE = "health.compare"
    /** 1.38 [SLEEP-01][SLEEP-12] 잠 자동으로 잡기(활동 인식)와 잘 시간 방해 금지. */
    const val SLEEP_AUTO = "sleep.auto"
    /** 1.38 [SLEEP-06][SLEEP-07] 밤 소리 측정과 클립(마이크, 휴대폰 안에서만). */
    const val SLEEP_SOUND = "sleep.sound"
    /** 1.38 [SLEEP-11] 공유한 달력에 '지금 자는 중'. */
    const val SLEEP_SHARE = "sleep.share"
    /** 1.39 [PAYP-05] 보완 요청 on a company's shared way (글 종류·다른 곳 항목, 올린 분 소식), once 처리방침's next version names it. */
    const val PLAZA_FIX_REQUEST = "plaza.fix_request"
    /** 1.39 [CARE-01~06] 마을 돌봄(부드러운 경고 → 의견 쉬기·마을 쉬기, 확인한 것만 세는 단계), once 처리방침's next version names it. */
    const val COMMUNITY_CARE = "community.care"
    /**
     * 2.0.0 [RVD-18] 검토 LEGAL-02 (사용자 결정 2026-11-12): '운영자가 확인한 것만 센다'만 처리방침 3.12 시행일(11-09)부터, 경고·쉬기는
     * [COMMUNITY_CARE]대로 12-09부터. 서버만 봐요(신고로 가려진 것은 운영자가 가린 채 둘 때 세요).
     */
    const val COMMUNITY_CONFIRMED = "community.confirmed"
    /** 1.39 [PLZC-01~16] 광장을 사람 먼저로: 이야기 흐름 첫 화면, 근무 태그(준 블라인드), 나도요, 렌즈 칩, once 처리방침 3.12 names the tag. */
    const val PLAZA_PEOPLE = "plaza.people"
    /** 1.39 G5-N4 가족 링크에 계획한 잠 시간(켜기 선택), once 처리방침 3.12 names it. */
    const val SLEEP_PLAN_SHARE = "sleep.plan_share"
    const val FREE_PREFIX = "free."
    /** [FLAG-34] A `free.<FEATURE>` row's words; [FREE_SLOT] is the feature's name (the server puts its Korean name). */
    const val FREE_FEATURE = "^[A-Z][A-Z0-9_]{1,40}$"
    const val FREE_SLOT = "{feature}"
    const val FREE_NAME = "무료로 연 프로 기능: {feature}"
    const val FREE_WHAT = "프로 기능 ‘{feature}’을 모두에게 무료로 열어요. 강제로 켜면 열리고, 풀면 다시 프로 기능이 돼요."
    const val FREE_WHY = "프로 기능이라 정식 출시 뒤에는 구독한 휴대폰만 쓰는 게 기본이에요."
    const val TYPE_PREFIX = "type."

    private val TYPE_NAMES = mapOf(
        WorkStyle.SHIFT to "교대", WorkStyle.DAY to "일근", WorkStyle.HOURLY to "시급",
        WorkStyle.FIELD to "현장", WorkStyle.BUSINESS to "자영업", WorkStyle.INVEST to "투자",
    )

    private val SWITCH_KEYS = mapOf(
        ServerSwitch.PLAZA_WRITE to "plaza.write",
        ServerSwitch.COMPANY_REPORT to "company.report",
        ServerSwitch.INQUIRY_PHOTOS to "inquiry.photos",
        ServerSwitch.SHARE_CREATE to "share.create",
        ServerSwitch.USAGE_STATS to "usage.stats",
        ServerSwitch.PLAZA_NEWS to "plaza.news",
    )

    /** [FLAG-34] The developer app's longer names; the app's own screens keep [ServerSwitch.label]. */
    private val SWITCH_NAMES = mapOf(
        ServerSwitch.PLAZA_WRITE to "광장 글쓰기(글·답글·제안·임금표)",
        ServerSwitch.COMPANY_REPORT to "회사 규칙 보내기",
        ServerSwitch.INQUIRY_PHOTOS to "문의 사진 받기",
        ServerSwitch.SHARE_CREATE to "근무표 링크 새로 만들기",
        ServerSwitch.USAGE_STATS to "사용 통계 받기",
        ServerSwitch.PLAZA_NEWS to "광장 소식 알림",
    )

    private val SWITCH_WHAT = mapOf(
        ServerSwitch.PLAZA_WRITE to "회사 광장과 유형별 광장에 글·답글·고치자는 제안·임금표를 올려요.",
        ServerSwitch.COMPANY_REPORT to "회사 근무·급여 방식(금액 없이)과 근무 주기를 광장에 보내고 저절로 나눠요.",
        ServerSwitch.INQUIRY_PHOTOS to "문의에 사진을 2장까지 붙여 보내요.",
        ServerSwitch.SHARE_CREATE to "근무표를 링크로 나누는 새 링크를 만들어요(이미 만든 링크는 그대로).",
        ServerSwitch.USAGE_STATS to "동의한 휴대폰이 하루 한 번 보내는 사용 횟수(휴대폰 구별 없음)를 서버가 받아요.",
        ServerSwitch.PLAZA_NEWS to "하루 한 번 광장 소식(내 규칙이 바뀌거나 채택됨)과 문의 답을 물어요.",
    )

    private val SWITCH_REQ = mapOf(
        ServerSwitch.PLAZA_WRITE to "SRV-04",
        ServerSwitch.COMPANY_REPORT to "SRV-04",
        ServerSwitch.INQUIRY_PHOTOS to "SRV-06",
        ServerSwitch.SHARE_CREATE to "SRV-05",
        ServerSwitch.USAGE_STATS to "STAT-01",
        ServerSwitch.PLAZA_NEWS to "COMM-10",
    )

    private val SWITCH_POLICY = mapOf(
        ServerSwitch.PLAZA_WRITE to "3.1 1-4항",
        ServerSwitch.COMPANY_REPORT to "3.1 1-2항",
        ServerSwitch.INQUIRY_PHOTOS to "3.1 1-3항",
        ServerSwitch.SHARE_CREATE to "3.1 1항",
        ServerSwitch.USAGE_STATS to "3.1 1-9항",
        ServerSwitch.PLAZA_NEWS to "3.1 1-4항",
    )

    /**
     * [PRIV-05] 결정 D111 (사용자 2026-10-30 "출시일 전에 개발한 모든 사항은 처리방침을 출시일로, 출시 이후에는 정상적으로"):
     * every LEGAL row 3.2~3.8 named waits for 3.9's date (정식 출시일) and, on a phone installed before 3.9 was posted, its
     * yes or 11-29. A row added after 정식 출시 waits for its own version's date again (9항: 7일·30일 전 고지).
     */
    private const val EARLY = PolicyConsent.VERSION
    /** 1.39 [PAYP-05] The 처리방침 version 1.39 ships with (보완 요청, PRIV-06 등 한 판); its dates come from the 관리자 앱 일정. */
    const val FIX_REQUEST_POLICY = "3.12"
    /** 1.39 [CARE-01] The 처리방침 version 1.39 ships with (공유 급여 보완 요청과 한 판); its dates come from the 관리자 앱 일정. */
    const val CARE_POLICY = FIX_REQUEST_POLICY
    /**
     * 2.0.0 [RVD-18] 검토 LEGAL-03: 처리방침 9항과 광장 운영 규칙 머리말이 약속한 마을 쉬기 날(모든 이용자 2026-12-09). 실서버 덮어쓰기 한 줄이
     * 아니라 코드 조건으로 지켜요(덮어쓰기를 지우거나 기본값으로 되돌려도 그 전에는 켜지지 않아요).
     */
    val CARE_FROM: LocalDate = LocalDate.of(2026, 12, 9)
    /** 1.39 [PLZC-10] 광장 근무 태그: the same 3.12 (1.39 총괄 11-07). */
    const val PEOPLE_POLICY = FIX_REQUEST_POLICY

    /** [FLAG-11] 정식 출시 retired: what the switch and the 3.1 hold did together (결정 D84). */
    private val launch: List<Condition> = listOf(
        Condition.Date(policy = PolicyConsent.LAUNCH_VERSION),
        Condition.Policy(PolicyConsent.LAUNCH_VERSION),
        Condition.Manual("play.production"),
        Condition.Manual("play.subscription"),
    )

    val BILLING_FLAG = Flag(
        BILLING, "결제(프로 구독)",
        what = "Play 프로 구독과 결제가 열리고 무료·프로가 나뉘어요. 처음 켜진 순간이 정식 출시일이라, 그 전에 설치한 휴대폰의 감사 보상과 보상 이용권이 이날부터 시작해요(서버가 한 번 기록).",
        whyOff = "처리방침 3.1 시행(동의하지 않은 휴대폰은 보류가 끝난 날)과 Play 프로덕션 승인·정기 결제 상품 확인 전에는 모두 무료여야 해요. 일찍 켜면 알리지 않은 결제가 돼요.",
        basis = Basis(policy = "3.1 1-6항", decision = "D84·D98·D99", requirement = "MON-02"),
        conditions = launch, default = false, risk = Risk.LEGAL, sunset = "1.30.0", group = FlagGroup.MONEY,
    )

    val ADS_FLAG = Flag(
        ADS, "광고(보상형)",
        what = "프로가 아닌 휴대폰에서 사용자가 고를 때만 보상형 광고가 나와요. 띠 광고는 없어요(1.36). 알람·잠금·첫 설정·달력·메모·설정 화면에는 없음.",
        whyOff = "결제와 같은 날, Google 광고 동의 화면이 막지 않을 때만 시작해요(아직 묻지 않았으면 동의 화면을 띄울 수 있게 켬, 거절하면 꺼짐). 결제와 따로 끌 수 있게 나눴어요.",
        basis = Basis(policy = "3.1 1-8항", decision = "D84·D98·D99", requirement = "MON-07"),
        conditions = launch + Condition.Consent("ads", unknownMeets = true), default = false, risk = Risk.LEGAL, sunset = "1.30.0",
        group = FlagGroup.MONEY,
    )

    /** [FLAG-12] One row per kind of work, on by default: off hides it only where a kind is newly picked. */
    val TYPES: List<Flag> = WorkStyle.entries.map { s ->
        Flag(
            typeKey(s), "${TYPE_NAMES.getValue(s)} 근무 유형",
            what = "첫 질문·첫 설정·새 달력·근무 형태 바꾸기·하는 일 찾기·둘러보기에 ‘${TYPE_NAMES.getValue(s)}’ 유형이 보여요.",
            whyOff = "기본으로 켜 둬요. 끄면 새로 고르는 곳에서만 빠지고 이미 쓰는 달력·백업·회사 파일은 그대로예요. 관리자 앱에서 미리 보기를 받은 관리자 휴대폰에만 숨긴 유형이 보여요.",
            basis = Basis(decision = "D98", requirement = if (s == WorkStyle.INVEST) "INV-01" else "WORK-01", note = "근무 유형 노출(순번 454)"),
            conditions = emptyList(), default = true, group = FlagGroup.TYPE,
        )
    }

    val LEAVE_HOURLY_FLAG = Flag(
        LEAVE_HOURLY, "시간 단위 연차",
        what = "연차를 시간 단위로 적고 원장에서 시간 ÷ 소정근로시간으로 빼요.",
        whyOff = "근로기준법 개정(법률 제21784호)의 시간 단위 연차가 2027년 6월 10일 시행이라 그 전에는 하루 단위 규칙이 맞아요. 앱 안 날짜 조건이라 인터넷이 없어도 그날 켜져요.",
        basis = Basis(decision = "D98", requirement = "LEAVE-12", note = "근로기준법 개정(법률 제21784호, 2027-06-10 시행)"),
        conditions = listOf(Condition.Date(from = HourlyLeave.FROM)), default = false, sunset = "1.40.0", group = FlagGroup.LAW,
    )

    val STATS_MORE_FLAG = Flag(
        STATS_MORE, "사용 통계 더 보내기",
        what = "사용 통계를 켜고 따로 동의한 휴대폰이 켜진 기능 목록과 앱 시작에 걸린 시간을 함께 보내요(휴대폰 구별 없음).",
        whyOff = "처리방침 1-9항은 서버가 정한 이름의 하루 횟수만 적고 있어요. 서버로 새 정보를 보내는 일이라 정식 출시일(처리방침 3.9가 3.2를 앞당긴 날)과 따로 받는 동의, Play 데이터 보안 답을 고친 뒤에만 보내요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요.",
        basis = Basis(policy = "3.2 1-9항(개정 예정)", decision = "D100", requirement = "FLAG-16", note = "순번 459 법 검토"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY), Condition.Consent("usage"), Condition.Consent("usage.stats.more")),
        default = false, risk = Risk.LEGAL, group = FlagGroup.STATS,
    )

    /**
     * [PHOTO-09] 1.29 AQ: the presses of '사진이 달라요' wait on the phone and go only once 처리방침 3.3
     * lists them: a press is the user's own act each time, so no other yes is asked. Never the photo or its text.
     */
    val PHOTO_WRONG_FLAG = Flag(
        PHOTO_WRONG, "사진이 달라요 횟수 보내기",
        what = "사진을 읽은 결과 화면에서 ‘사진이 달라요’를 누르면 날짜·화면 종류·고른 이유·앱 버전의 횟수만 서버로 보내요(휴대폰 구별 없음). 사진과 글은 보내지 않아요.",
        whyOff = "정식 출시일(처리방침 3.9가 3.3(1-13항)을 앞당긴 날)부터 보내요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전에는 휴대폰에 횟수만 모아 두고(60일), ‘직접 문의하기’는 지금도 돼요.",
        basis = Basis(policy = "3.3 1-13항", decision = "D68", requirement = "PHOTO-09", note = "1.29 AQ 사진이 달라요"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY)),
        default = false, risk = Risk.LEGAL, since = "1.29.0", group = FlagGroup.STATS,
    )

    /**
     * [ITEM-03] 1.30 BC: 결정 D102 (사용자 2026-10-27 "회사 공통 정액만 광장에 공유"). On from 처리방침 3.4's date; off sends 회사 공통 수당 without
     * its amount, so everyone types it as before. A person's own amount (사람마다) never goes, on or off.
     */
    val PLAZA_FIXED_AMOUNT_FLAG = Flag(
        PLAZA_FIXED_AMOUNT, "회사 공통 수당 금액 나누기",
        what = "회사 설정을 광장에 올리거나 고치자고 제안할 때, 작성자가 ‘회사 공통 금액’으로 정한 수당(식대·교통비 정액 같은)의 금액을 함께 올려요. 쓰는 분은 그 금액을 다시 적지 않아요. 사람마다 다른 수당의 금액은 보내지 않아요.",
        whyOff = "정식 출시일(처리방침 3.9가 3.4(1-2항)를 앞당긴 날)부터 금액을 함께 올려요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전이나 끄면 회사 공통 수당도 금액 없이 올라가고, 쓰는 분이 각자 적어요(1.29와 같음). 이미 올라간 금액은 그대로예요.",
        basis = Basis(policy = "3.4 1-2항", decision = "D102", requirement = "ITEM-03", note = "1.30 BC 수당 종류(회사 공통·사람마다·%)"),
        // 1.30 통합: 1-2항 said "수당 금액은 보내지 않습니다" until 3.4, so the amount waits for 3.4's date like photo.wrong does for 3.3.
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY)),
        default = false, risk = Risk.LEGAL, since = "1.30.0", group = FlagGroup.LAW,
    )

    /**
     * [STAT-08] 1.31 N4 (사용자 2026-10-28 "스위치는 기간이 되면 자동으로 켜지는 현 방식"): on by itself from 처리방침 3.5's
     * date, for phones with 사용 통계 and its separate yes given on 1.31 or later, whose words name it
     * (`usage.stats.lookup`: the same 더 보내기 동의, given after seeing 조회 시간). Only the
     * day's middle wait per kind, rounded up to a fixed bucket; never what was looked up.
     */
    val STATS_LOOKUP_FLAG = Flag(
        STATS_LOOKUP, "조회 시간 보내기",
        what = "사용 통계를 켜고 따로 동의한 휴대폰이 회사 찾기·회사 화면·광장 조회에 걸린 시간을 하루 가운데 값 하나씩, 정해진 구간 값으로만 함께 보내요(휴대폰 구별 없음). 무엇을 찾았는지는 보내지 않아요.",
        whyOff = "정식 출시일(처리방침 3.9가 3.5(1-9항)를 앞당긴 날)부터 저절로 켜져요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전에는 조회 시간을 이 휴대폰 안(자가 점검 › 조회 속도)에만 둬요.",
        basis = Basis(policy = "3.5 1-9항", decision = "D103", requirement = "STAT-08", note = "1.31 상세리뷰 N4"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY), Condition.Consent("usage"), Condition.Consent("usage.stats.more"), Condition.Consent("usage.stats.lookup")),
        default = false, risk = Risk.LEGAL, since = "1.31.0", group = FlagGroup.STATS,
    )

    /**
     * 1.32.1 [STAB-10] (사용자 2026-10-29 "멈춘 상태를 어플이 인지해서 문의로 보고하면 해결할 수 있게"): the 멈춤 관찰 기록
     * (화면 꺼짐·켜짐·잠금 풀림 시각, 앱 앞·뒤, 화면 크기, 메모리 수치, 화면 멈춤·안 그려짐) goes in an inquiry's 점검 기록 only
     * from 처리방침 3.6's date, and only when the person ticks 점검 기록 and sends. The phone keeps it from 1.32.1 on.
     */
    val DIAG_OBSERVE_FLAG = Flag(
        DIAG_OBSERVE, "문의에 멈춤 관찰 기록 붙이기",
        what = "문의할 때 ‘점검 기록도 보내기’를 고르면, 앱이 멈추기 전 3일 동안의 화면 꺼짐·켜짐·잠금 풀림 시각, 앱이 앞·뒤로 간 때, 화면 크기, 앱이 쓴 메모리, 화면이 멈추거나 그려지지 않은 때도 함께 보내요. 적은 글·금액·메모는 들어가지 않아요.",
        whyOff = "정식 출시일(처리방침 3.9가 3.6(1-3항)을 앞당긴 날)부터 저절로 켜져요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전에는 이 기록을 휴대폰 안(알람 › 알람 점검 › 앱 멈춤 관찰)에만 두고 문의에는 앱 멈춤 기록만 가요.",
        basis = Basis(policy = "3.6 1-3항", decision = "D104", requirement = "STAB-10", note = "1.32.1 멈춤 관측"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY)),
        default = false, risk = Risk.LEGAL, since = "1.32.1", group = FlagGroup.STATS,
    )

    /**
     * 1.33 [SHR-20] (계획 690~694, 사용자 2026-10-29 "보고서에 적힌 전체 진행"): the 같은 회사 동료 way of a 근무표 링크 puts the
     * company setup in the link (근무 주기·근무 시간·조·잔업 종류·급여 규칙·수당 이름, 금액 없이) only from 처리방침 3.7's date; before
     * it, the sheet sends the same setup as a file the sharer passes on themselves, which goes through no server.
     */
    val SHARE_SETUP_FLAG = Flag(
        SHARE_SETUP, "동료 링크에 회사 설정 담기",
        what = "근무표 링크를 ‘같은 회사 동료’에게 보낼 때 회사 설정(근무 주기·근무 시간·조·잔업 종류·급여 규칙·수당 이름)도 링크에 함께 담아요. 받은 동료는 앱에서 기본급만 넣으면 돼요. 기본급·입사일·연차·알람·메모·금액은 담지 않아요(회사 공통 정액만 1-2항처럼).",
        whyOff = "정식 출시일(처리방침 3.9가 3.7(1항)을 앞당긴 날)부터 저절로 켜져요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전에는 링크에 담지 않고, 공유 창의 ‘회사 설정 파일도 보내기’로 파일을 직접 보내요(서버를 거치지 않음).",
        basis = Basis(policy = "3.7 1항", decision = "D105", requirement = "SHR-20", note = "1.33 공유 세 갈래(계획 690~694)"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY)),
        default = false, risk = Risk.LEGAL, since = "1.33.0", group = FlagGroup.LAW,
    )

    /**
     * 1.37 [SHR-31][SHR-32] 묶음 E (사용자 2026-11-04 "달력 공유 시 테마와 설정까지"): the sharer may tick 내 테마 (the look pieces, no
     * photos) and 근무 설정 (근무 꾸미기·알람 시각 틀·근무 주기, never 급여 규칙 or an amount) to go in the link, for the receiving app
     * to try on and pick from. Both reach the share server, which 처리방침 1항 lists item by item, so they wait for 3.11: posted
     * before 정식 출시, it starts on that day (결정 D111, 사용자 2026-11-04 "11월10일로"). The admin app can move its dates.
     */
    val SHARE_LOOK_FLAG = Flag(
        SHARE_LOOK, "링크에 테마·근무 설정 담기",
        what = "근무표 링크를 만들 때 ‘내 테마’와 ‘근무 설정’을 고르면 링크에 함께 담아요. 테마는 달력·메모·가계부의 모양(사진은 빼요), 근무 설정은 근무 이름·색·글자, 근무 전 알람 시각, 근무 주기예요. 급여 규칙·기본급·금액·메모·사진은 담지 않아요. 받는 분은 밤낮 앱에서 입어 보고 고른 것만 넣어요.",
        whyOff = "처리방침 3.11의 시행일(2026-11-07)부터 저절로 켜져요. 3.11 게시 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 30일 고지가 끝나는 12-07부터예요. 그 전에는 공유 창에 그날을 알려 주고 링크에는 담지 않아요.",
        basis = Basis(policy = "3.11 1항", requirement = "SHR-31", note = "1.37 묶음 E 달력 공유에 테마·설정"),
        conditions = listOf(Condition.Date(policy = SHARE_LOOK_POLICY), Condition.Policy(SHARE_LOOK_POLICY)),
        default = false, risk = Risk.LEGAL, since = "1.37.0", group = FlagGroup.LAW,
    )
    /** The 처리방침 version that names a link's 테마·근무 설정. */
    const val SHARE_LOOK_POLICY = PolicyConsent.SHARE_LOOK_VERSION

    /**
     * 1.34 [RCP-37] 나눔 판 (계획 745~787, 결정 D109): looking at the plaza's recipes, previewing and applying one happen on the phone
     * and read the public list only, so they are open from 1.34 (the server can still rest them). Sending — a recipe, a vote with
     * its reason, a post in a recipe's talk — waits for 처리방침 3.8 (게시 2026-10-30, 시행 2026-11-29, 1-4항).
     */
    val RECIPE_BROWSE_FLAG = Flag(
        RECIPE_BROWSE, "나눔 판 보기·적용",
        what = "유형별 광장의 레시피를 찾아보고, 내 달력에서 미리 보고, 골라 적용하고, 7일 안에 되돌려요. 휴대폰 안에서 하고 공개 목록을 읽기만 해요.",
        whyOff = "기본으로 켜 둬요. 레시피에 문제가 생기면 관리자가 잠시 강제로 끌 수 있고, 그동안 앱은 ‘잠시 쉬고 있어요’로 보여요.",
        basis = Basis(policy = "3.8 1-4항", decision = "D109", requirement = "RCP-21", note = "1.34 나눔 판"),
        conditions = emptyList(), default = true, since = "1.34.0", group = FlagGroup.SERVER,
    )

    /**
     * 2.0.0 [RVM-21] MKT-32 (사용자 카드 11-12 '우리 쪽에서 글꼴 내주기'): the 장터 cards' small fonts (letters of the cards only) come
     * from our public files, checked by sha256. Asking our GitHub Pages is a new place the phone reaches, named in 처리방침 3.13's 7-day
     * line, so it waits for that line's day; until then the cards draw in the phone's fonts as before 2.0.0 (총괄 11-12).
     * 총괄 11-12 (나): the day is [PolicyConsent.REVISION_EFFECTIVE] (게시일 + 7일, moved with it by `tools/legal_dates.py`), not the
     * server calendar's 3.13 날짜, which is the 30-day line (12-12). 3.13 has no consent card, so no [Condition.Policy] hold either (its
     * hold would end on the 30-day day too). Judged by the server's clock like every LEGAL row.
     */
    val CARD_FONTS_FLAG = Flag(
        CARD_FONTS, "장터 카드 글꼴 받기",
        what = "장터 목록·작품 상세·예시로 보기에서 작품 카드의 글꼴을 밤낮 공개 자료(GitHub Pages)에서 받아요. 카드 글자만 담은 작은 글꼴이고, 앱에 적힌 sha256과 같을 때만 써요. 한 번 받으면 휴대폰에 남아요.",
        whyOff = "처리방침 3.13의 7일 줄 시행일(${PolicyConsent.REVISION_EFFECTIVE.monthValue}월 ${PolicyConsent.REVISION_EFFECTIVE.dayOfMonth}일, 게시일 + 7일)부터 서버 시각으로 저절로 켜져요. 그 전에는 카드를 휴대폰 글꼴로 그리고 공개 자료에도 Google에도 묻지 않아요.",
        basis = Basis(policy = "3.13", requirement = "RVM-21", note = "2.0.0 장터 카드 글꼴(MKT-32)"),
        conditions = listOf(Condition.Date(from = PolicyConsent.REVISION_EFFECTIVE)),
        default = false, risk = Risk.LEGAL, since = "2.0.0", group = FlagGroup.LAW,
    )

    private fun recipeLegal(key: String, name: String, what: String, req: String) = Flag(
        key, name, what = what,
        whyOff = "정식 출시일(처리방침 3.9가 3.8(1-4항)을 앞당긴 날)부터 저절로 켜져요. 3.9 전에 깐 휴대폰이 달력 위 카드에 동의하지 않았으면 11-29부터예요. 그 전에는 레시피를 보고 적용하는 것만 돼요.",
        basis = Basis(policy = "3.8 1-4항", decision = "D109", requirement = req, note = "1.34 나눔 판"),
        conditions = listOf(Condition.Date(policy = EARLY), Condition.Policy(EARLY)),
        default = false, risk = Risk.LEGAL, since = "1.34.0", group = FlagGroup.LAW,
    )

    val RECIPE_PUBLISH_FLAG = recipeLegal(
        RECIPE_PUBLISH, "레시피 올리기",
        "내 설정으로 만든 레시피를 광장에 공개하거나 링크로 나눠요. 시급·일당 같은 금액은 받는 사람이 정할 칸으로 바뀌고, 이름·연락처·링크는 담지 않아요.",
        "RCP-29",
    )
    val RECIPE_VOTE_FLAG = recipeLegal(
        RECIPE_VOTE, "레시피 좋아요·안좋아요",
        "레시피와 이야기 글에 좋아요·안좋아요를 눌러요. 안좋아요는 이유(고르기와 짧은 의견)를 꼭 적고, 이유는 누가 적었는지 없이 만든 사람에게만 보여요.",
        "RCP-31",
    )
    val RECIPE_TALK_FLAG = recipeLegal(
        RECIPE_TALK, "레시피 이야기",
        "레시피마다 질문·의견·아이디어·고친 후기를 써요. 1.35부터 요청 판과 구독도 이 조건을 따라요.",
        "RCP-35",
    )

    /**
     * 1.38 [P3-01] Phase 3 (사용자 2026-11-05 "1.38에서 개발 시작", 결정 P3-D1 B: 꺼진 채 개발, 켜는 건 관문 뒤). Each row waits for a fact
     * only the developer confirms in the admin app (파일럿 시작, 워치 수면 확인); until then only an admin phone given 미리 보기 sees it.
     * None sends anything new to a server (설계 1-1 원칙 1): 팀 표·직원 정보·잠은 휴대폰 안, a member's link is today's 근무표 링크.
     */
    /**
     * 1.38 Phase 3 (사용자 2026-11-05 C안 "모든 기능을 해당 일에 푸는 거야, 내가 따로 지정하면 그날에"): every Phase 3 row opens on
     * the effective day of 처리방침 [PHASE3_POLICY] as the server's calendar has it, so moving that day in the admin app moves them all.
     * 3.11 names what they send (팀원·직원 이름, 신청·출퇴근 링크, 지금 자는 중), so a phone installed before 3.11 was posted waits for
     * its yes on the calendar card or the end of the 30-day notice (12-07), like `share.look`; force-off in the admin app still rests one.
     */
    private val phase3Launch: List<Condition> = listOf(Condition.Date(policy = PHASE3_POLICY), Condition.Policy(PHASE3_POLICY))

    /**
     * 1.38 출시 점검 E2 (사용자 2026-11-07, 1.38 마무리): the 처리방침 판 whose 시행일 opens the Phase 3 and 수면 rows: 3.11, the one
     * 판 that names them (게시·시행 2026-11-07). Moving 3.11's 시행일 in the admin app's 처리방침 일정 moves all six rows with it.
     */
    const val PHASE3_POLICY: String = PolicyConsent.SHARE_LOOK_VERSION

    val TEAM_LEADER_FLAG = Flag(
        TEAM_LEADER, "리더 팀 근무표",
        what = "달력 › 도구 › ‘내 팀 근무표 짜기’(모든 조 위 막대에도 있어요)에서 팀을 만들고 팀원×날짜 표를 칠해요(단톡방 명단 붙여 넣기·지난달 이어 칠하기·맞바꾸기·바로 검사·희망 휴무·빈칸 자동 채우기). 확정하면 팀원마다 그 사람 근무만 담은 근무표 링크를 만들어요. 팀 표는 리더 휴대폰에만 있어요.",
        whyOff = "정식 출시일(처리방침 3.1 시행일, 관리자 앱 처리방침 날짜로 옮겨요)에 다른 기능과 함께 켜져요(사용자 2026-11-05 C안). 그 전에는 미리 보기를 받은 관리자 휴대폰에만 보여요.",
        basis = Basis(decision = "P3-D1·P3-D2·P3-D5", requirement = "TEAM-09", note = "Phase 3 M24 L1(순번 482~488)"),
        conditions = phase3Launch, default = false, since = "1.38.0", group = FlagGroup.PHASE3,
    )

    val BOSS_PAYROLL_FLAG = Flag(
        BOSS_PAYROLL, "사장님 급여 대장",
        what = "가게 달력의 ‘직원 인건비’가 사업장·직원·월 급여 대장으로 바뀌어요. 평소 근무에서 바뀐 날만 고치면 직원마다 기본급·주휴·연장·야간·휴일이 계산되고(누르면 풀이), 임금대장 파일(시행령 제27조 항목)로 내보내요. 직원 정보는 휴대폰과 암호 백업에만 있어요.",
        whyOff = "정식 출시일(처리방침 3.1 시행일)에 자영업 달력에서 켜져요(사용자 2026-11-05 C안). 그 전에는 지금 ‘직원 인건비’ 그대로예요.",
        basis = Basis(decision = "P3-D4·P3-D6·P3-D8", requirement = "BOSS-01", note = "Phase 3 M25 B1(순번 499~502)"),
        conditions = phase3Launch + Condition.WorkType(setOf(WorkStyle.BUSINESS.name)), default = false, since = "1.38.0",
        group = FlagGroup.PHASE3,
    )

    val HEALTH_COMPARE_FLAG = Flag(
        HEALTH_COMPARE, "계획 대 실제 잠",
        what = "날짜 화면 수면 칸과 수면 기록 화면에 근무에 맞춘 계획 잠과 실제로 잔 잠(워치·휴대폰이 잰 것, 직접 적은 것)을 나란히 보여 줘요. 휴대폰 안에서 그때그때 계산하고 저장하거나 보내지 않아요.",
        whyOff = "정식 출시일(처리방침 3.1 시행일)에 켜져요(사용자 2026-11-05 C안). 워치 수면이 헬스 커넥트로 오지 않는 휴대폰은 직접 적은 잠과 휴대폰이 잰 잠으로 견줘요.",
        basis = Basis(requirement = "HLTH-01", note = "Phase 3 M23(순번 510, SLP-08)"),
        conditions = phase3Launch, default = false, since = "1.38.0", group = FlagGroup.PHASE3,
    )

    val SLEEP_AUTO_FLAG = Flag(
        SLEEP_AUTO, "잠 자동으로 잡기",
        what = "설정 › 알림과 잠에서 켜면 휴대폰이 빛과 움직임으로 알아챈 잠(Google Play 서비스 수면 감지)을 아침에 ‘맞나요?’로 물어 수면 기록에 넣어요. 잘 시간 방해 금지도 여기에 묶여요. 휴대폰 안에서만 쓰고 보내지 않아요.",
        whyOff = "활동 인식 권한과 방해 금지 접근을 새로 쓰니 처리방침 새 판 시행일(정식 출시일)에 켜져요(사용자 2026-11-06 수면 도약 승인).",
        basis = Basis(requirement = "SLEEP-01", note = "1.38 수면 도약 S1·S3"),
        conditions = phase3Launch, default = false, since = "1.38.0", group = FlagGroup.PHASE3,
    )

    val SLEEP_SOUND_FLAG = Flag(
        SLEEP_SOUND, "밤 소리 측정",
        what = "밤 화면에서 ‘측정 시작’을 누르면 마이크로 코골이·잠꼬대·이갈이·기침을 휴대폰 안에서 알아채 짧은 소리만 남겨요(기본 7일 뒤 지움). 소리와 기록은 휴대폰 밖으로 나가지 않아요.",
        whyOff = "마이크 사용을 처리방침 새 판에 적고 시행일(정식 출시일)에 켜져요(사용자 2026-11-06 수면 도약 승인).",
        basis = Basis(requirement = "SLEEP-06", note = "1.38 수면 도약 S2"),
        conditions = phase3Launch, default = false, since = "1.38.0", group = FlagGroup.PHASE3,
    )

    val SLEEP_SHARE_FLAG = Flag(
        SLEEP_SHARE, "지금 자는 중 나누기",
        what = "달력을 공유한 사람에게 ‘지금 자는 중 · 14:10쯤 깸’ 한 줄을 보여 줘요. 공유 링크마다 켜고 끌 수 있어요.",
        whyOff = "공유 서버로 잠 상태(자는 중·깰 시각)를 새로 보내니 처리방침 새 판 시행일(정식 출시일)에 켜져요(사용자 2026-11-06 수면 도약 승인).",
        basis = Basis(requirement = "SLEEP-11", note = "1.38 수면 도약 S3"),
        conditions = phase3Launch, default = false, since = "1.38.0", group = FlagGroup.PHASE3,
    )

    /**
     * 1.39 [PAYP-05] (사용자 2026-11-07 "보완요청을 하면 어떤점이 다른거 같아요를 이야기할수 있게해서 제공한 사용자가 수정"): a new kind of post
     * and a new line of 밤낮 소식, which 처리방침 1-4항 names only from its next version. Off until the owner turns it on at that
     * version's date (관리자 앱 › 기능 관리), the rest of 1.39's shared pay changes (가리기·맞아요 순·급여 탭 잠금) need no flag.
     */
    val PLAZA_FIX_REQUEST_FLAG = Flag(
        PLAZA_FIX_REQUEST, "공유 급여 보완 요청",
        what = "회사 광장의 급여 방식 판에 ‘보완 요청’을 써요. 어디가 다른지(연장·야간·수당 등) 고르고 한 줄 적으면 그 판을 올린 분이 밤낮 소식으로 받아 고치거나 답해요. 다른 분은 ‘나도 그래요’를 눌러요.",
        whyOff = "새 글 종류와 올린 분께 가는 소식이라 처리방침 3.12(1.39 출시 개정, 1-4항)의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 단추가 보이지 않고 서버도 받지 않아요.",
        basis = Basis(policy = "3.12 1-4항", requirement = "PAYP-05", note = "1.39 공유 급여 비공개 (사용자 2026-11-07 '1.39 출시 때 자동으로 켜지게')"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY)),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.LAW,
    )

    /**
     * 1.39 [PLZC-01~16] (사용자 2026-11-07 "광장은 급여를 나누는 것 말고는 하는 것이 전혀 없어 … 유형별 태그를 달아 어떤 근무를 하는지 …
     * 준 블라인드"): 광장 첫 화면이 이야기 흐름이 되고, 유형별 광장 글에 그 사람 달력 설정에서 저절로 붙는 근무 태그가 보여요. 태그는
     * 처리방침 1-4항의 다음 판(3.12)에서 처음 적어요. 설계: 프로젝트 `계획/1.39-광장-커뮤니티-설계.md`.
     */
    val PLAZA_PEOPLE_FLAG = Flag(
        PLAZA_PEOPLE, "광장 사람 먼저(근무 태그)",
        what = "광장을 열면 이야기 흐름이 먼저 나오고, 회사 규칙·시세는 위쪽 문으로 가요. 유형별 광장 글에 그분 달력의 근무 설정에서 저절로 붙는 근무 태그(교대 · 4조 2교대 · 밤낮 7개월)가 보이고, ‘나와 같은 근무’·‘지금 근무 중’·‘답 기다리는 질문’으로 걸러 봐요. 이야기 글 반응은 ‘나도요’ 하나예요.",
        whyOff = "근무 태그를 처리방침 3.12(1.39 출시 개정, 1-4항)에 적고 그 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 지금 광장 그대로이고 서버도 태그를 받지 않아요.",
        basis = Basis(policy = "3.12 1-4항", requirement = "PLZC-10", note = "1.39 광장 커뮤니티 (사용자 2026-11-07, 총괄 '3.12에 묶음')"),
        conditions = listOf(Condition.Date(policy = PEOPLE_POLICY), Condition.Policy(PEOPLE_POLICY)),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.LAW,
    )

    /**
     * 1.39 [SETUP-20] (사용자 결정 2026-11-07 '추천대로', 발굴 G6 4-1 '처음 1분을 숫자로'): 설치부터 첫 설정을 저장해 달력이 찰 때까지
     * 화면을 누른 횟수를, 정해진 구간 값 하나와 고른 일하는 방식만 이미 동의한 사용 통계에 실어요. 무엇을 눌렀는지·시각·휴대폰 구별은
     * 없어요. 처리방침 3.12(1-9항 사용 통계 항목)의 시행일에 저절로 켜지고, 그 전에는 휴대폰 안에만 둬요.
     */
    val STATS_SETUP_FLAG = Flag(
        STATS_SETUP, "첫 설정 누른 횟수 보내기",
        what = "사용 통계를 켠 휴대폰이 처음 깔고 첫 설정을 저장할 때까지 화면을 누른 횟수를 정해진 구간 값(5·10·15번…)으로 한 번, 고른 일하는 방식(교대·일근 등)과 함께 보내요. 무엇을 눌렀는지와 시각은 보내지 않아요.",
        whyOff = "사용 통계 항목이 늘어 처리방침 3.12(1.39 출시 개정, 1-9항)의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 센 횟수를 휴대폰 안에만 두고, 사용 통계를 끄면 지워요.",
        basis = Basis(policy = "3.12 1-9항", requirement = "SETUP-20", note = "1.39 발굴 G6 4-1 (사용자 결정 2026-11-07 '추천대로')"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY), Condition.Consent("usage")),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.STATS,
    )

    /**
     * 1.39 [STAT2-02] (사용자 2026-11-08 "통계쪽 혁신 … 어플의 정상도 사용자 이탈 회복 진입 등 모든 것을 전반적으로"): 이미 동의한 사용 통계에
     * 그날이 '처음 연 날'인지, 설치하고 1·7·30일째인지, 7일·30일 넘게 쉬다 다시 연 날인지를 다른 이벤트처럼 날마다 횟수로만 실어요. 설치한 날짜·
     * 쉰 날 수 자체와 휴대폰 구별은 보내지 않아요. 처리방침 3.12(1-9항 사용 통계 항목)의 시행일에 저절로 켜지고, 그 전에는 세지 않아요.
     */
    val STATS_LIFE_FLAG = Flag(
        STATS_LIFE, "처음 연 날·다시 연 날 보내기",
        what = "사용 통계를 켠 휴대폰이 그날이 처음 연 날인지, 깐 지 1·7·30일째인지, 7일·30일 넘게 쉬다 다시 연 날인지를 다른 사용 통계처럼 날마다 횟수로만 보내요. 관리자 앱 › 숫자 › 한눈에서 진입·머묾·회복·이탈 어림으로 보여요. 설치한 날짜와 휴대폰 구별은 보내지 않아요.",
        whyOff = "사용 통계 항목이 늘어 처리방침 3.12(1.39 출시 개정, 1-9항)의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 세지도 보내지도 않아요.",
        basis = Basis(policy = "3.12 1-9항", requirement = "STAT2-02", note = "1.39 통계 개편 (사용자 2026-11-08 '정상도·이탈·회복·진입')"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY), Condition.Consent("usage")),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.STATS,
    )

    /**
     * 1.39 [OBS2-08] (사용자 2026-11-08 관측 데이터 카드 '유형별·기능 둘만', "통계를 통한 완전한 자가 발전"): 진입·머묾(life_*)과 첫 열기를
     * 고른 일하는 방식별로도 세요. 방식 하나(여섯 가지 중)만 사용 통계 꾸러미에 붙고, 휴대폰 구별·회사·근무표는 없어요. 처리방침 3.12 시행일에
     * 저절로 켜지고, 그 전에는 붙이지 않아요.
     */
    val STATS_KIND_FLAG = Flag(
        STATS_KIND, "근무 유형별 흐름 보내기",
        what = "사용 통계를 켠 휴대폰이 진입·머묾 횟수와 함께 고른 일하는 방식(교대·일근·알바·현장·자영업·투자 중 하나)을 보내요. 관리자 앱 › 숫자 › 한눈에서 유형마다 처음 연 날 → 1·7·30일째를 봐요. 회사·근무표·휴대폰 구별은 보내지 않아요.",
        whyOff = "사용 통계 항목이 늘어 처리방침 3.12(1.39 출시 개정, 1-9항)의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 방식을 붙이지 않아요.",
        basis = Basis(policy = "3.12 1-9항", requirement = "OBS2-08", note = "1.39 관측 (사용자 결정 2026-11-08 '유형별·기능 둘만')"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY), Condition.Consent("usage")),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.STATS,
    )

    /**
     * 1.39 [OBS2-08] 기능과 머묾: 그날 쓴 기능 이름을, 그 뒤 7일 안(또는 30일 안)에 다시 열었을 때만 '그날·창·이름'으로 보내요
     * ([com.bamnat.core.stats.KeepMarks]). 서버가 그날 그 기능을 쓴 휴대폰 수로 나눠 '이 기능을 쓴 사람이 다시 오는지'를 봐요.
     */
    val STATS_KEEP_FLAG = Flag(
        STATS_KEEP, "기능 쓴 뒤 다시 왔는지 보내기",
        what = "사용 통계를 켠 휴대폰이 앱을 다시 열면, 바로 전에 연 날 쓴 기능 이름과 그 날, 7일 안에 왔는지 30일 안에 왔는지만 보내요. 관리자 앱이 기능마다 '쓴 사람이 7일 안에 돌아온 비율'을 전체와 견줘요. 며칠 만인지 정확한 수와 휴대폰 구별은 보내지 않아요.",
        whyOff = "사용 통계 항목이 늘어 처리방침 3.12(1.39 출시 개정, 1-9항)의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 세지도 보내지도 않아요.",
        basis = Basis(policy = "3.12 1-9항", requirement = "OBS2-08", note = "1.39 관측 (사용자 결정 2026-11-08 '유형별·기능 둘만')"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY), Condition.Consent("usage")),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.STATS,
    )

    /**
     * 1.40 [DISC-06] (사용자 결정 2026-11-11 '측정은 합계 4가지만'): '안 써 본 기능 알려 주기' 카드가 그날 몇 번 보였는지·열어 봤는지·나중에를
     * 눌렀는지·그만 보기를 눌렀는지를 이미 동의한 사용 통계에 날마다 횟수로만 실어요. 어떤 기능을 알렸는지와 휴대폰 구별은 보내지 않아요.
     * 처리방침 3.13의 시행일에 저절로 켜지고, 그 전에는 세지도 보내지도 않아요(카드는 이것 없이도 휴대폰 안에서만 돌아요).
     */
    val STATS_DISC_FLAG = Flag(
        STATS_DISC, "안 써 본 기능 카드 합계 보내기",
        what = "사용 통계를 켠 휴대폰이 ‘안 써 본 기능 알려 주기’ 카드가 그날 보인 횟수·열어 본 횟수·나중에를 누른 횟수·그만 보기를 누른 횟수만 보내요. 관리자 앱이 그만 보기 비율로 빈도와 문구가 알맞은지 봐요. 어떤 기능을 알렸는지와 휴대폰 구별은 보내지 않아요.",
        whyOff = "사용 통계 항목이 4가지 늘어 처리방침 3.13의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.13 날짜를 넣으면 그날부터예요. 그 전에는 세지도 보내지도 않아요.",
        basis = Basis(policy = "3.13 1-9항", requirement = "DISC-06", note = "1.40 안 써 본 기능 알려 주기 (사용자 결정 2026-11-11 '합계 4가지만')"),
        conditions = listOf(Condition.Date(policy = DISCOVER_POLICY), Condition.Policy(DISCOVER_POLICY), Condition.Consent("usage")),
        default = false, risk = Risk.LEGAL, since = "1.40.0", group = FlagGroup.STATS,
    )

    /** [FLAG-10] The six old 기능 스위치, on by default; the server turns one off to rest it. */
    /**
     * 1.39 [CARE-01~06] (사용자 2026-11-07 "악성 유저는 차단을 통해 몇일 의견제시나 커뮤니티 사용중단, 그전에 미리 경고도 부드럽게", "2회는 너무
     * 타이트, 실제로 잘못된 행동을 하고 있을 때 제재"): 경고 기록과 운영자가 고르는 쉬기는 처리방침 1-4항과 광장 운영 규칙 6항의 다음 판에서
     * 처음 적어요. 그 시행일(관리자 앱 › 기능 관리 › 일정의 3.12)에 저절로 켜지고, 그 전에는 1.36 신고 누적 제한(30일 안 2번) 그대로예요.
     */
    val COMMUNITY_CARE_FLAG = Flag(
        COMMUNITY_CARE, "마을 돌봄(경고·쉬기)",
        what = "마을에서 운영 원칙에 어긋난 글은 운영자가 확인한 것만 세고, 60일 안 1번은 안내, 2번은 경고, 3번부터 며칠 쓰는 횟수가 줄어요. 운영자는 경고를 먼저 보낸 뒤 1~30일 의견 쉬기·마을 쉬기를 걸 수 있어요. 앱은 마을을 열 때 안내를 한 번 보여 주고, 쉬는 동안 마을 맨 위에 한 줄로 알려요.",
        whyOff = "경고 기록과 운영자 쉬기를 처리방침 3.12(1-4항)와 광장 운영 규칙 다음 판(6항)에 적었고, 처리방침 9항대로 모든 이용자에게 2026년 12월 9일부터예요. 그 날짜는 코드에 있어 덮어쓰기를 지워도 그 전에는 켜지지 않아요. 그 전에는 신고 누적 쓰기 제한(30일 안 2번, 운영자가 확인한 것만)이 그대로예요.",
        basis = Basis(policy = "3.12 1-4항·9항", requirement = "CARE-01", note = "1.39 마을 돌봄 (사용자 2026-11-07), 2.0.0 RVD-18 12-09 코드로 (사용자 2026-11-12)"),
        conditions = listOf(Condition.Date(policy = CARE_POLICY), Condition.Date(from = CARE_FROM), Condition.Policy(CARE_POLICY)),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.LAW,
    )

    /**
     * 2.0.0 [RVD-18] 검토 LEGAL-02 (사용자 결정 2026-11-12 03:50Z): 처리방침 3.12 1-4항 "앱 1.39.0부터(처리방침 3.12 시행일부터)는 운영자가 확인해
     * 가린 것만 셉니다"를 그 시행일부터 지켜요. 신고로 먼저 가려진 것은 '확인 대기'로 두고, 운영자가 가린 채 두면 그때 세요. 이용자에게 유리한
     * 쪽이라 미리 알릴 기간이 없어요. 경고·쉬기와 60일 3번 규칙은 [COMMUNITY_CARE_FLAG](12-09)를 따라요.
     */
    val COMMUNITY_CONFIRMED_FLAG = Flag(
        COMMUNITY_CONFIRMED, "신고는 운영자가 확인한 것만 세기",
        what = "마을에서 신고로 먼저 가려진 글·작품은 쓰기 제한 횟수에 바로 세지 않고, 운영자가 보고 가린 채 두었을 때만 세요. 운영자가 직접 가린 것은 그대로 세요.",
        whyOff = "처리방침 3.12(1-4항)가 그 시행일부터 이렇게 센다고 적어 그날 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 신고로 가려진 것도 바로 셌어요.",
        basis = Basis(policy = "3.12 1-4항", requirement = "RVD-18", note = "2.0.0 검토 LEGAL-02 (사용자 결정 2026-11-12)"),
        conditions = listOf(Condition.Date(policy = CARE_POLICY)),
        default = false, risk = Risk.LEGAL, since = "2.0.0", group = FlagGroup.LAW,
    )

    /**
     * 1.39 G5-N4 (사용자 결정 1차 13번, 2026-11-07 "추천대로": 3.12 문장 넣고, 기본은 꺼짐 · 보내는 분이 켬): a family's link may carry the
     * sleep the app planned around the work, two weeks of from~to and nothing else. 처리방침 names it from 3.12, so it opens on that date.
     */
    val SLEEP_PLAN_SHARE_FLAG = Flag(
        SLEEP_PLAN_SHARE, "계획한 잠 시간 나누기",
        what = "달력을 공유한 가족이 받은 달력에서 ‘오늘 09:00~16:00 잘 계획이에요’ 한 줄을 봐요. 앱이 근무에 맞춰 계획한 잘 시간(2주치 시작·끝)만 보내고, 실제 잔 기록·소리는 보내지 않아요. 공유 링크마다 보내는 분이 켜야 해요(기본 꺼짐).",
        whyOff = "공유 서버로 계획한 잠 시간을 새로 보내니 처리방침 3.12의 시행일에 저절로 켜져요. 관리자 앱 › 기능 관리 › 일정에 3.12 날짜를 넣으면 그날부터예요. 그 전에는 공유 창에 줄이 보이지 않아요.",
        basis = Basis(policy = "3.12 1항", requirement = "SLEEP-11", note = "1.39 G5-N4 (사용자 결정 1차 13번)"),
        conditions = listOf(Condition.Date(policy = FIX_REQUEST_POLICY), Condition.Policy(FIX_REQUEST_POLICY)),
        default = false, risk = Risk.LEGAL, since = "1.39.0", group = FlagGroup.LAW,
    )

    val SWITCHES: List<Flag> = ServerSwitch.entries.map { s ->
        Flag(
            switchKey(s), SWITCH_NAMES.getValue(s),
            what = SWITCH_WHAT.getValue(s),
            whyOff = "기본으로 켜 둬요. 스팸·사고·사용량이 몰릴 때 관리자가 잠시 강제로 끄면 앱은 ‘잠시 쉬고 있어요’로 보이고 서버도 받지 않아요(옛 ‘기능 스위치’).",
            basis = Basis(policy = SWITCH_POLICY.getValue(s), decision = "D97", requirement = SWITCH_REQ.getValue(s)),
            conditions = emptyList(), default = true, since = "1.22.0", group = FlagGroup.SERVER,
        )
    }

    val ALL: List<Flag> = listOf(BILLING_FLAG, ADS_FLAG) + TYPES + listOf(LEAVE_HOURLY_FLAG, STATS_MORE_FLAG, PHOTO_WRONG_FLAG, PLAZA_FIXED_AMOUNT_FLAG, STATS_LOOKUP_FLAG,
        DIAG_OBSERVE_FLAG, SHARE_SETUP_FLAG, SHARE_LOOK_FLAG, RECIPE_BROWSE_FLAG, RECIPE_PUBLISH_FLAG, RECIPE_VOTE_FLAG, RECIPE_TALK_FLAG, TEAM_LEADER_FLAG, BOSS_PAYROLL_FLAG, HEALTH_COMPARE_FLAG,
        SLEEP_AUTO_FLAG, SLEEP_SOUND_FLAG, SLEEP_SHARE_FLAG, PLAZA_FIX_REQUEST_FLAG, COMMUNITY_CARE_FLAG, COMMUNITY_CONFIRMED_FLAG, PLAZA_PEOPLE_FLAG, SLEEP_PLAN_SHARE_FLAG, STATS_SETUP_FLAG,
        STATS_LIFE_FLAG, STATS_KIND_FLAG, STATS_KEEP_FLAG, STATS_DISC_FLAG, CARD_FONTS_FLAG) +
        SWITCHES

    private val byKey = ALL.associateBy { it.key }

    /** The row of [key]: one of the book, or a `free.<FEATURE>` row; null for a key this app does not know. */
    fun of(key: String): Flag? = byKey[key] ?: free(key)

    /**
     * [FLAG-05] AL-D4 `free.<FEATURE>`: off by default with no condition; the server's force on opens that 프로 feature to
     * everyone. The app reads it only for a 프로 feature, so it can never lock a free one.
     * [NXT-03] 1.29 AU: the feature is named from [ProFeatures] ("무료로 연 프로 기능: 가계부 통계"), as the server names it.
     */
    fun free(key: String): Flag? {
        if (!key.startsWith(FREE_PREFIX)) return null
        val code = key.removePrefix(FREE_PREFIX)
        if (!Regex(FREE_FEATURE).matches(code)) return null
        val feature = ProFeatures.name(code)
        return Flag(key, FREE_NAME.replace(FREE_SLOT, feature), what = FREE_WHAT.replace(FREE_SLOT, feature),
            whyOff = FREE_WHY, basis = Basis(requirement = "MON-02"),
            conditions = emptyList(), default = false, since = "1.19.0", group = FlagGroup.FREE)
    }

    /** The book plus the `free.*` rows among [serverKeys]. */
    fun withFree(serverKeys: Collection<String>): List<Flag> = ALL + serverKeys.sorted().mapNotNull { free(it) }

    fun typeKey(s: WorkStyle): String = TYPE_PREFIX + s.name.lowercase()
    fun switchKey(s: ServerSwitch): String = SWITCH_KEYS.getValue(s)
    fun switchOf(key: String): ServerSwitch? = SWITCH_KEYS.entries.firstOrNull { it.value == key }?.key
}

/**
 * [FLAG-12][FLAG-13] Which kinds of work a place where a kind is newly picked shows. A hidden kind is left out, except
 * on the owner's phone, which shows it marked [Choice.hidden]. The kind in use ([keep]) always stays. If every kind
 * would be hidden, 교대 shows anyway (the first screen is never empty).
 */
object TypeExposure {
    data class Choice(val style: WorkStyle, val hidden: Boolean)

    // S 1.32 [SECX-01] Only a phone the developer app gave this kind's 미리 보기 sees it, marked so.
    const val HIDDEN_MARK = "미리 보기 · 이 휴대폰에만 보여요"

    fun choices(on: (WorkStyle) -> Boolean, owner: Boolean, keep: WorkStyle? = null): List<Choice> = choices(on, { owner }, keep)

    /** S 1.32 [SECX-01] [previewed]: the hidden kinds this admin phone was given 미리 보기 for in the developer app. */
    fun choices(on: (WorkStyle) -> Boolean, previewed: (WorkStyle) -> Boolean, keep: WorkStyle? = null): List<Choice> {
        val open = WorkStyle.entries.filter { on(it) || it == keep }.toMutableSet()
        if (open.isEmpty()) open += WorkStyle.SHIFT
        return WorkStyle.entries.mapNotNull { s ->
            when {
                s in open -> Choice(s, hidden = false)
                previewed(s) -> Choice(s, hidden = true)
                else -> null
            }
        }
    }

    /** Every kind shows: nothing hidden and nothing to mark (the common case, and the app before 1.28). */
    val ALL: List<Choice> = WorkStyle.entries.map { Choice(it, false) }
}
