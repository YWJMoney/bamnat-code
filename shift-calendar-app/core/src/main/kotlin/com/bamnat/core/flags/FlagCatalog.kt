package com.bamnat.core.flags

import com.bamnat.core.leave.HourlyLeave
import com.bamnat.core.ops.PolicyConsent
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.work.WorkStyle

/**
 * [FLAG-05] The book: every dark-merged row the app knows, in the order the developer app and 진단 list them. The
 * server keeps the same keys (계약 2-1, 묶음 AM); keys only the server knows are skipped by the app, except
 * `free.<FEATURE>` ([free]).
 */
object FlagCatalog {
    const val BILLING = "billing"
    const val ADS = "ads"
    const val LEAVE_HOURLY = "leave.hourly"
    /** [FLAG-16] 순번 459: the extra usage-stats items (켜진 기능 목록, 앱 시작 시간) that 처리방침 3.2 adds. */
    const val STATS_MORE = "stats.more"
    const val FREE_PREFIX = "free."
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

    private val SWITCH_WHAT = mapOf(
        ServerSwitch.PLAZA_WRITE to "광장에 글·답글·제안을 쓸 수 있어요.",
        ServerSwitch.COMPANY_REPORT to "회사 규칙·근무 주기를 광장에 보내고 저절로 나눠요.",
        ServerSwitch.INQUIRY_PHOTOS to "문의에 사진을 2장까지 붙여요.",
        ServerSwitch.SHARE_CREATE to "근무표 링크를 새로 만들어요.",
        ServerSwitch.USAGE_STATS to "동의한 휴대폰이 하루 사용 횟수를 보내요.",
        ServerSwitch.PLAZA_NEWS to "하루 한 번 광장 소식·문의 답을 물어요.",
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
        what = "Play 프로 구독과 결제가 열리고 무료·프로가 나뉘어요. 처음 켜진 날이 감사 보상의 기준일이에요(서버가 기록).",
        whyOff = "Play 정식 출시 승인과 처리방침 3.1 시행 전에는 모두 무료여야 해요.",
        basis = Basis(policy = "3.1 1-6항", decision = "D84", requirement = "MON-02"),
        conditions = launch, default = false, risk = Risk.LEGAL, sunset = "1.30.0",
    )

    val ADS_FLAG = Flag(
        ADS, "광고",
        what = "프로가 아닌 휴대폰에 근무·급여와 돈 탭 아래 띠 광고 한 곳과 고르는 보상형 광고가 보여요.",
        whyOff = "결제와 같은 날 시작해요. 결제와 따로 끌 수 있게 나눴어요.",
        basis = Basis(policy = "3.1 1-8항", decision = "D84", requirement = "MON-07"),
        conditions = launch + Condition.Consent("ads", unknownMeets = true), default = false, risk = Risk.LEGAL, sunset = "1.30.0",
    )

    /** [FLAG-12] One row per kind of work, on by default: off hides it only where a kind is newly picked. */
    val TYPES: List<Flag> = WorkStyle.entries.map { s ->
        Flag(
            typeKey(s), "${TYPE_NAMES.getValue(s)} 근무 유형",
            what = "첫 질문·첫 설정·새 달력·근무 형태 바꾸기·하는 일 찾기·둘러보기에 ‘${TYPE_NAMES.getValue(s)}’ 유형이 보여요.",
            whyOff = "기본으로 켜 둬요. 끄면 새로 고르는 곳에서만 빠지고 이미 쓰는 달력·백업·회사 파일은 그대로예요.",
            basis = Basis(requirement = if (s == WorkStyle.INVEST) "INV-01" else "WORK-01", note = "근무 유형 노출(순번 454)"),
            conditions = emptyList(), default = true,
        )
    }

    val LEAVE_HOURLY_FLAG = Flag(
        LEAVE_HOURLY, "시간 단위 연차",
        what = "연차를 시간 단위로 적고 원장에서 시간 ÷ 소정근로시간으로 빼요.",
        whyOff = "법률 제21784호의 시간 단위 연차가 2027년 6월 10일 시행이라 그날 켜져요. 앱 안 조건이라 인터넷이 없어도 켜져요.",
        basis = Basis(requirement = "LEAVE-12", note = "법률 제21784호"),
        conditions = listOf(Condition.Date(from = HourlyLeave.FROM)), default = false, sunset = "1.40.0",
    )

    val STATS_MORE_FLAG = Flag(
        STATS_MORE, "사용 통계 더 보내기",
        what = "사용 통계를 켜고 따로 동의한 휴대폰이 켜진 기능 목록과 앱 시작에 걸린 시간을 함께 보내요(휴대폰 구별 없음).",
        whyOff = "처리방침 1-9항은 서버가 정한 이름의 하루 횟수만 적고 있어요. 3.2 시행과 따로 받는 동의 전에는 보내지 않아요.",
        basis = Basis(policy = "3.2 1-9항(개정 예정)", requirement = "FLAG-16", note = "순번 459 법 검토"),
        conditions = listOf(Condition.Date(policy = "3.2"), Condition.Consent("usage"), Condition.Consent("usage.stats.more")),
        default = false, risk = Risk.LEGAL,
    )

    /** [FLAG-10] The six old 기능 스위치, on by default; the server turns one off to rest it. */
    val SWITCHES: List<Flag> = ServerSwitch.entries.map { s ->
        Flag(
            switchKey(s), s.label,
            what = SWITCH_WHAT.getValue(s),
            whyOff = "기본으로 켜 둬요. 사고가 나면 관리자가 잠시 꺼요(옛 ‘기능 스위치’).",
            basis = Basis(policy = SWITCH_POLICY.getValue(s), requirement = "SRV-04"),
            conditions = emptyList(), default = true,
        )
    }

    val ALL: List<Flag> = listOf(BILLING_FLAG, ADS_FLAG) + TYPES + listOf(LEAVE_HOURLY_FLAG, STATS_MORE_FLAG) + SWITCHES

    private val byKey = ALL.associateBy { it.key }

    /** The row of [key]: one of the book, or a `free.<FEATURE>` row; null for a key this app does not know. */
    fun of(key: String): Flag? = byKey[key] ?: free(key)

    /**
     * [FLAG-05] AL-D4 `free.<FEATURE>`: off by default with no condition; the server's force on opens that 프로 feature to
     * everyone. The app reads it only for a 프로 feature, so it can never lock a free one.
     */
    fun free(key: String): Flag? {
        if (!key.startsWith(FREE_PREFIX)) return null
        val feature = key.removePrefix(FREE_PREFIX)
        if (!Regex("^[A-Z][A-Z0-9_]{1,40}$").matches(feature)) return null
        return Flag(key, "무료로 연 기능: $feature", what = "프로 기능 ‘$feature’을 모두에게 무료로 열어요.",
            whyOff = "프로 기능이라 기본은 구독한 휴대폰만 써요.", basis = Basis(requirement = "MON-02"),
            conditions = emptyList(), default = false)
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

    const val HIDDEN_MARK = "숨김 · 관리자에게만 보여요"

    fun choices(on: (WorkStyle) -> Boolean, owner: Boolean, keep: WorkStyle? = null): List<Choice> {
        val open = WorkStyle.entries.filter { on(it) || it == keep }.toMutableSet()
        if (open.isEmpty()) open += WorkStyle.SHIFT
        return WorkStyle.entries.mapNotNull { s ->
            when {
                s in open -> Choice(s, hidden = false)
                owner -> Choice(s, hidden = true)
                else -> null
            }
        }
    }

    /** Every kind shows: nothing hidden and nothing to mark (the common case, and the app before 1.28). */
    val ALL: List<Choice> = WorkStyle.entries.map { Choice(it, false) }
}
