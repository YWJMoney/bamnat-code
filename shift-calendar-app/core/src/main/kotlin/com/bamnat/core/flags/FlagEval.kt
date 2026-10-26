package com.bamnat.core.flags

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [FLAG-07] What the phone knows about itself, read on the phone and never sent anywhere. Dates are worked out in
 * [KST] from [now] (the phone's clock) and [serverNow] ([FLAG-04] the last `/api/config` time plus the time since,
 * null when not known).
 */
data class PhoneFacts(
    val now: Instant,
    val serverNow: Instant? = null,
    val versionCode: Int,
    val installed: LocalDate? = null,
    /** 처리방침 versions this phone said yes to. */
    val agreed: Set<String> = emptySet(),
    /** Consents by key; a key not here was not asked yet. */
    val consents: Map<String, Boolean> = emptyMap(),
    val profile: Set<String> = emptySet(),
    /** The calendar in use's kind of work ([com.bamnat.core.work.WorkStyle] name); null before the first setup. */
    val workType: String? = null,
    /** A hash of the random install id (never the id itself); null in a build without the company server. */
    val installHash: String? = null,
    /** Linked to an admin account and approved. */
    val admin: Boolean = false,
    /** [FLAG-13] Linked by a code the owner made. */
    val owner: Boolean = false,
    /** [FLAG-15] Rows this admin phone previews (step 3); ignored on a phone that is not an admin's. */
    val previews: Set<String> = emptySet(),
) {
    val today: LocalDate get() = now.atZone(KST).toLocalDate()
    val serverToday: LocalDate? get() = serverNow?.atZone(KST)?.toLocalDate()
}

/** [FLAG-02] Where a row's answer came from. */
enum class FlagSource(val words: String) {
    FORCE_OFF("서버 강제"),
    APP_RANGE("앱 판"),
    ADMIN_PREVIEW("관리자 미리 보기"),
    FORCE_ON("서버 강제"),
    CONDITIONS("앱 조건"),
    DEFAULT("앱 기본값"),
}

/** [FLAG-02] One line of a row's checklist. */
data class Check(val label: String, val status: Status, val detail: String? = null) {
    enum class Status(val mark: String, val words: String) { MET("✓", "채움"), UNMET("○", "남음"), UNKNOWN("?", "모름") }
}

/** [FLAG-02] A row on this phone now: on or off, one sentence why, where the answer came from, and the checklist. */
data class FlagState(
    val key: String,
    val on: Boolean,
    val source: FlagSource,
    val reason: String,
    val checks: List<Check>,
)

/**
 * [FLAG-02] The one answer to "is this row on here": the first step that decides wins (계약 2-1).
 * 1 강제 끔 → 2 앱 판 밖 → 3 관리자 미리 보기 → 4 강제 켬 → 5 조건(모두 채우면 켬, 아니면 기본값).
 * [FLAG-04] A [Risk.LEGAL] row reads dates from [PhoneFacts.serverNow] and keeps its [Condition.Policy] check through
 * steps 3 and 4, so a wrong force from the server cannot start payments before the policy allows.
 */
object FlagEval {
    fun state(flag: Flag, phone: PhoneFacts, override: FlagOverride? = null, calendar: PolicyCalendar = PolicyCalendar.SEED): FlagState {
        val legal = flag.risk == Risk.LEGAL
        val conditions = override?.conditions ?: flag.conditions
        val checks = conditions.map { check(it, flag, phone, override, calendar) }
        val forceLive = override?.force != null && (override.until == null || clock(phone, legal)?.isBefore(override.until) == true)
        val why = override?.why?.trim()?.takeIf { it.isNotEmpty() }

        // 1. 강제 끔: always wins.
        if (forceLive && override?.force == Force.OFF) {
            return FlagState(flag.key, false, FlagSource.FORCE_OFF,
                "관리자가 잠시 껐어요" + (why?.let { ": $it" } ?: ".") + until(override.until), checks)
        }
        // 2. 앱 판 밖.
        val min = override?.minApp ?: flag.minApp
        val max = override?.maxApp ?: flag.maxApp
        if ((min != null && phone.versionCode < min) || (max != null && phone.versionCode > max)) {
            return FlagState(flag.key, false, FlagSource.APP_RANGE, "이 앱 판(${phone.versionCode})에서는 쓰지 않아요. 앱을 업데이트하면 켜질 수 있어요.", checks)
        }
        // [FLAG-04] What a LEGAL row still needs when something else turns it on: its policy conditions.
        val policyLeft = if (!legal) null else conditions.policyOnly().map { check(it, flag, phone, override, calendar) }
            .firstOrNull { it.status != Check.Status.MET }
        // 3. 관리자 미리 보기.
        if (phone.admin && flag.key in phone.previews) {
            if (policyLeft != null) return FlagState(flag.key, false, FlagSource.ADMIN_PREVIEW,
                "미리 보기를 켰지만 법적 조건이 남아 꺼져 있어요: ${policyLeft.label}.", checks)
            return FlagState(flag.key, true, FlagSource.ADMIN_PREVIEW, "관리자 휴대폰에서 미리 보기로 켰어요.", checks)
        }
        // 4. 강제 켬.
        if (forceLive && override?.force == Force.ON) {
            if (policyLeft != null) return FlagState(flag.key, false, FlagSource.FORCE_ON,
                "관리자가 켰지만 이 휴대폰은 처리방침 조건이 남아 꺼져 있어요: ${policyLeft.label}.", checks)
            return FlagState(flag.key, true, FlagSource.FORCE_ON, "관리자가 켰어요" + (why?.let { ": $it" } ?: ".") + until(override.until), checks)
        }
        // 5. 조건.
        if (conditions.isEmpty()) {
            return FlagState(flag.key, flag.default, FlagSource.DEFAULT, if (flag.default) "기본으로 켜져 있어요." else "기본으로 꺼져 있어요. 관리자가 켜면 열려요.", checks)
        }
        val left = conditions.zip(checks).filterNot { (c, k) -> counts(c, k) }.map { it.second }
        if (left.isEmpty()) return FlagState(flag.key, true, FlagSource.CONDITIONS, "조건을 모두 채워서 켜졌어요.", checks)
        val first = left.first()
        val rest = if (left.size > 1) " 외 ${left.size - 1}개" else ""
        val what = if (first.status == Check.Status.UNKNOWN) "아직 모르는 조건" else "남은 조건"
        val tail = if (left.size == 1 && first.status == Check.Status.UNMET) " 채우면 바로 켜져요." else ""
        return FlagState(flag.key, flag.default, FlagSource.CONDITIONS,
            (if (flag.default) "켜져 있어요(기본값). " else "대기 중이에요. ") + "$what: ${first.label}$rest.$tail", checks)
    }

    /** Every row of the book (and the server's `free.*`) at once, in the book's order. */
    fun all(phone: PhoneFacts, overrides: Map<String, FlagOverride>, calendar: PolicyCalendar = PolicyCalendar.SEED): List<FlagState> =
        FlagCatalog.withFree(overrides.keys).map { state(it, phone, overrides[it.key], calendar) }

    /** [FLAG-04] The instant a row reads: the server's for a LEGAL row (null when not known), the phone's otherwise. */
    private fun clock(phone: PhoneFacts, legal: Boolean): Instant? = if (legal) phone.serverNow else phone.now

    private fun dateOf(phone: PhoneFacts, legal: Boolean): LocalDate? = if (legal) phone.serverToday else phone.today

    /** A consent not asked yet that [Condition.Consent.unknownMeets] counts as given. */
    private fun counts(c: Condition, k: Check): Boolean =
        k.status == Check.Status.MET || (c is Condition.Consent && c.unknownMeets && k.status == Check.Status.UNKNOWN)

    private fun List<Condition>.policyOnly(): List<Condition> = flatMap {
        when (it) {
            is Condition.Policy -> listOf(it)
            else -> emptyList()
        }
    }

    fun check(c: Condition, flag: Flag, phone: PhoneFacts, override: FlagOverride?, calendar: PolicyCalendar): Check {
        val legal = flag.risk == Risk.LEGAL
        val date = dateOf(phone, legal)
        val serverNote = if (legal) " · 서버 시각 기준" else ""
        return when (c) {
            is Condition.Date -> {
                val from = c.from ?: calendar.of(c.policy!!)?.effective
                val label = if (c.policy != null) "처리방침 ${c.policy} 시행일" + (from?.let { "(${day(it)})" } ?: "") + "부터$serverNote"
                    else "${day(from!!)}부터$serverNote"
                when {
                    from == null || date == null -> Check(label, Check.Status.UNKNOWN, if (date == null) "서버 시각을 아직 몰라요" else "처리방침 일정에 없어요")
                    date >= from -> Check(label, Check.Status.MET)
                    else -> Check(label, Check.Status.UNMET, "${java.time.temporal.ChronoUnit.DAYS.between(date, from)}일 남음")
                }
            }
            is Condition.Policy -> {
                val v = calendar.of(c.version)
                val label = "처리방침 ${c.version}에 동의했거나 " + (v?.let { "${day(it.holdEnds)}이 지남" } ?: "보류가 끝남")
                val held = if (date == null) null else calendar.holds(c.version, phone.installed, phone.agreed, date)
                when (held) {
                    null -> Check(label, Check.Status.UNKNOWN, if (v == null) "처리방침 일정에 없어요" else "서버 시각을 아직 몰라요")
                    false -> Check(label, Check.Status.MET)
                    true -> Check(label, Check.Status.UNMET, "달력 위 동의 카드에서 동의하면 바로 채워져요")
                }
            }
            is Condition.Consent -> {
                val label = CONSENTS[c.key] ?: "동의: ${c.key}"
                when (phone.consents[c.key]) {
                    true -> Check(label, Check.Status.MET)
                    false -> Check(label, Check.Status.UNMET)
                    null -> Check(label, Check.Status.UNKNOWN, if (c.unknownMeets) "아직 묻지 않았어요(물을 수 있어요)" else "아직 묻지 않았어요")
                }
            }
            is Condition.Profile -> Check(PROFILE[c.key] ?: "정보: ${c.key}", if (c.key in phone.profile) Check.Status.MET else Check.Status.UNMET)
            is Condition.App -> {
                val label = "앱 판 " + listOfNotNull(c.min?.let { "$it 이상" }, c.max?.let { "$it 이하" }).joinToString(" ")
                val ok = (c.min == null || phone.versionCode >= c.min) && (c.max == null || phone.versionCode <= c.max)
                Check(label, if (ok) Check.Status.MET else Check.Status.UNMET, "이 휴대폰 ${phone.versionCode}")
            }
            is Condition.WorkType -> {
                val label = "근무 유형: " + c.types.joinToString("·") { TYPE_WORDS[it] ?: it }
                when (phone.workType) {
                    null -> Check(label, Check.Status.UNKNOWN, "첫 설정 전이에요")
                    in c.types -> Check(label, Check.Status.MET)
                    else -> Check(label, Check.Status.UNMET)
                }
            }
            is Condition.Rollout -> {
                val label = "휴대폰 ${c.percent.coerceIn(0, 100)}%에 먼저"
                val bucket = phone.installHash?.let { bucket(it, flag.key) }
                when {
                    bucket == null -> Check(label, Check.Status.UNKNOWN, "설치 번호가 없어요")
                    bucket < c.percent -> Check(label, Check.Status.MET, "이 휴대폰 자리 ${bucket + 1}/100")
                    else -> Check(label, Check.Status.UNMET, "이 휴대폰 자리 ${bucket + 1}/100")
                }
            }
            is Condition.Manual -> {
                val label = (MANUAL[c.key] ?: c.key) + "(관리자가 확인)"
                when {
                    override == null -> Check(label, Check.Status.UNKNOWN, "서버 답을 아직 못 받았어요")
                    c.key in override.confirmed -> Check(label, Check.Status.MET, override.confirmed[c.key]?.let { "확인 ${day(it.atZone(KST).toLocalDate())}" })
                    else -> Check(label, Check.Status.UNMET)
                }
            }
            Condition.Admin -> Check("관리자 휴대폰", if (phone.admin) Check.Status.MET else Check.Status.UNMET)
            is Condition.AnyOf -> {
                val parts = c.any.map { check(it, flag, phone, override, calendar) }
                val status = when {
                    parts.any { it.status == Check.Status.MET } -> Check.Status.MET
                    parts.any { it.status == Check.Status.UNKNOWN } -> Check.Status.UNKNOWN
                    else -> Check.Status.UNMET
                }
                Check(parts.joinToString(" 또는 ") { it.label }, status)
            }
        }
    }

    /**
     * [FLAG-01] Rollout: 0..99 from SHA-256 of the install hash and the key, so one phone is not first in every
     * rollout, and the same phone keeps its place across runs and versions.
     */
    fun bucket(installHash: String, key: String): Int {
        val d = MessageDigest.getInstance("SHA-256").digest("$installHash|$key".toByteArray(Charsets.UTF_8))
        val n = ((d[0].toLong() and 0xff) shl 24) or ((d[1].toLong() and 0xff) shl 16) or ((d[2].toLong() and 0xff) shl 8) or (d[3].toLong() and 0xff)
        return (n % 100).toInt()
    }

    private val DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
    fun day(d: LocalDate): String = DAY.format(d)

    private fun until(at: Instant?): String = at?.let { " (${day(it.atZone(KST).toLocalDate())}까지)" }.orEmpty()

    val CONSENTS = mapOf(
        "ads" to "광고 동의(Google 동의 화면)",
        "usage" to "사용 통계 보내기 동의",
        "usage.stats.more" to "사용 통계 더 보내기 동의(켜진 기능 목록·앱 시작 시간)",
    )
    val PROFILE = mapOf(
        "setup.done" to "첫 설정을 마침",
        "pay.setup" to "급여 설정을 마침",
        "company.linked" to "회사를 연결함",
    )
    val MANUAL = mapOf(
        "play.production" to "Play 프로덕션 승인",
        "play.subscription" to "정기 결제 상품 활성",
        "data.safety" to "Play 데이터 보안 답 고침",
    )
    val TYPE_WORDS = mapOf(
        "SHIFT" to "교대", "DAY" to "일근", "HOURLY" to "시급", "FIELD" to "현장", "BUSINESS" to "자영업", "INVEST" to "투자",
    )
}
