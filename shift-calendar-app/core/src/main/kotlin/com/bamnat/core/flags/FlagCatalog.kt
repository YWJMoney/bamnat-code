package com.bamnat.core.flags

import com.bamnat.core.leave.HourlyLeave
import com.bamnat.core.ops.PolicyConsent
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.work.WorkStyle

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
    /** 1.32.1 [STAB-10]: 멈춤 관찰 기록 in the 점검 기록 of an inquiry, once 처리방침 3.6 names it. */
    const val DIAG_OBSERVE = "diag.observe"
    /** 1.33 [SHR-20]: a coworker's 근무표 링크 carries the company setup (no amounts), once 처리방침 3.7 names it. */
    const val SHARE_SETUP = "share.setup"
    /** 1.34 [RCP-37] 나눔 판: reading the plaza's recipes (no information sent but the phone's install hash, as any plaza read). */
    const val RECIPE_BROWSE = "recipe.browse"
    /** 1.34 [RCP-37] Putting a recipe up, once 처리방침 3.8 names it. */
    const val RECIPE_PUBLISH = "recipe.publish"
    /** 1.34 [RCP-37] 좋아요·안좋아요 (with a reason) on recipes and their talk, once 3.8 names it. */
    const val RECIPE_VOTE = "recipe.vote"
    /** 1.34 [RCP-37] Writing in a recipe's talk, and (1.35) 요청 판·구독, once 3.8 names it. */
    const val RECIPE_TALK = "recipe.talk"
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

    /** [FLAG-11] 정식 출시 retired: what the switch and the 3.1 hold did together (결정 D84). */
    private val launch: List<Condition> = listOf(
        Condition.Date(policy = PolicyConsent.VERSION),
        Condition.Policy(PolicyConsent.VERSION),
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
        ADS, "광고(띠·보상형)",
        what = "프로가 아닌 휴대폰에 근무·급여와 가계부 탭 아래 띠 광고 한 곳과 사용자가 고르는 보상형 광고가 보여요(알람·잠금·첫 설정 화면에는 없음).",
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
        whyOff = "처리방침 1-9항은 서버가 정한 이름의 하루 횟수만 적고 있어요. 서버로 새 정보를 보내는 일이라 3.2 시행과 따로 받는 동의, Play 데이터 보안 답을 고친 뒤에만 보내요.",
        basis = Basis(policy = "3.2 1-9항(개정 예정)", decision = "D100", requirement = "FLAG-16", note = "순번 459 법 검토"),
        conditions = listOf(Condition.Date(policy = "3.2"), Condition.Consent("usage"), Condition.Consent("usage.stats.more")),
        default = false, risk = Risk.LEGAL, group = FlagGroup.STATS,
    )

    /**
     * [PHOTO-09] 1.29 AQ: the presses of '사진이 달라요' wait on the phone and go only once 처리방침 3.3
     * lists them: a press is the user's own act each time, so no other yes is asked. Never the photo or its text.
     */
    val PHOTO_WRONG_FLAG = Flag(
        PHOTO_WRONG, "사진이 달라요 횟수 보내기",
        what = "사진을 읽은 결과 화면에서 ‘사진이 달라요’를 누르면 날짜·화면 종류·고른 이유·앱 버전의 횟수만 서버로 보내요(휴대폰 구별 없음). 사진과 글은 보내지 않아요.",
        whyOff = "처리방침 3.3(1-13항)이 시행된 뒤에만 보내요. 그 전에는 휴대폰에 횟수만 모아 두고(60일), ‘직접 문의하기’는 지금도 돼요.",
        basis = Basis(policy = "3.3 1-13항", decision = "D68", requirement = "PHOTO-09", note = "1.29 AQ 사진이 달라요"),
        conditions = listOf(Condition.Date(policy = "3.3")),
        default = false, risk = Risk.LEGAL, since = "1.29.0", group = FlagGroup.STATS,
    )

    /**
     * [ITEM-03] 1.30 BC: 결정 D102 (사용자 2026-10-27 "회사 공통 정액만 광장에 공유"). On from 처리방침 3.4's date; off sends 회사 공통 수당 without
     * its amount, so everyone types it as before. A person's own amount (사람마다) never goes, on or off.
     */
    val PLAZA_FIXED_AMOUNT_FLAG = Flag(
        PLAZA_FIXED_AMOUNT, "회사 공통 수당 금액 나누기",
        what = "회사 설정을 광장에 올리거나 고치자고 제안할 때, 작성자가 ‘회사 공통 금액’으로 정한 수당(식대·교통비 정액 같은)의 금액을 함께 올려요. 쓰는 분은 그 금액을 다시 적지 않아요. 사람마다 다른 수당의 금액은 보내지 않아요.",
        whyOff = "처리방침 3.4(1-2항)가 시행된 뒤에만 금액을 함께 올려요. 그 전이나 끄면 회사 공통 수당도 금액 없이 올라가고, 쓰는 분이 각자 적어요(1.29와 같음). 이미 올라간 금액은 그대로예요.",
        basis = Basis(policy = "3.4 1-2항", decision = "D102", requirement = "ITEM-03", note = "1.30 BC 수당 종류(회사 공통·사람마다·%)"),
        // 1.30 통합: 1-2항 said "수당 금액은 보내지 않습니다" until 3.4, so the amount waits for 3.4's date like photo.wrong does for 3.3.
        conditions = listOf(Condition.Date(policy = "3.4")),
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
        whyOff = "처리방침 3.5(1-9항)가 시행된 날부터 저절로 켜져요. 그 전에는 조회 시간을 이 휴대폰 안(자가 점검 › 조회 속도)에만 둬요.",
        basis = Basis(policy = "3.5 1-9항", decision = "D103", requirement = "STAT-08", note = "1.31 상세리뷰 N4"),
        conditions = listOf(Condition.Date(policy = "3.5"), Condition.Consent("usage"), Condition.Consent("usage.stats.more"), Condition.Consent("usage.stats.lookup")),
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
        whyOff = "처리방침 3.6(1-3항)이 시행된 날부터 저절로 켜져요. 그 전에는 이 기록을 휴대폰 안(알람 › 알람 점검 › 앱 멈춤 관찰)에만 두고 문의에는 앱 멈춤 기록만 가요.",
        basis = Basis(policy = "3.6 1-3항", decision = "D104", requirement = "STAB-10", note = "1.32.1 멈춤 관측"),
        conditions = listOf(Condition.Date(policy = "3.6")),
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
        whyOff = "처리방침 3.7(1항)이 시행된 날부터 저절로 켜져요. 그 전에는 링크에 담지 않고, 공유 창의 ‘회사 설정 파일도 보내기’로 파일을 직접 보내요(서버를 거치지 않음).",
        basis = Basis(policy = "3.7 1항", decision = "D105", requirement = "SHR-20", note = "1.33 공유 세 갈래(계획 690~694)"),
        conditions = listOf(Condition.Date(policy = "3.7")),
        default = false, risk = Risk.LEGAL, since = "1.33.0", group = FlagGroup.LAW,
    )

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

    private fun recipeLegal(key: String, name: String, what: String, req: String) = Flag(
        key, name, what = what,
        whyOff = "처리방침 3.8(1-4항)이 시행된 날(2026-11-29)부터 저절로 켜져요. 그 전에는 레시피를 보고 적용하는 것만 돼요.",
        basis = Basis(policy = "3.8 1-4항", decision = "D109", requirement = req, note = "1.34 나눔 판"),
        conditions = listOf(Condition.Date(policy = "3.8")),
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

    /** [FLAG-10] The six old 기능 스위치, on by default; the server turns one off to rest it. */
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
        DIAG_OBSERVE_FLAG, SHARE_SETUP_FLAG, RECIPE_BROWSE_FLAG, RECIPE_PUBLISH_FLAG, RECIPE_VOTE_FLAG, RECIPE_TALK_FLAG) +
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
