package com.bamnat.core.flags

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [FLAG-01] 1.28 묶음 AL (순번 454, 사용자 2026-10-26 "처리방침·기간 근거로 다크머지된 기능이 그날 자동으로 켜짐"): one row of the
 * dark-merge book. The code of every feature ships in the app; a row says when the phone turns it on by itself
 * ([conditions]), why it was off, and what it rests on, so the developer app and the 진단 screen read the same words.
 * The server only overrides a row ([FlagOverride]); without it the phone decides alone, offline included.
 */
data class Flag(
    /** The same name in the app, the server and the logs (`billing`, `type.invest`, …). */
    val key: String,
    /** Korean name, as the developer app's list and the 진단 screen show it. */
    val name: String,
    /** What the user gets when it is on. */
    val what: String,
    /** Why it shipped off (or what turning it off means for a flag that is on). */
    val whyOff: String,
    val basis: Basis,
    /** All must hold for the flag to turn on by itself; none leaves it at [default]. */
    val conditions: List<Condition>,
    /** What the app has when no condition is set or not all hold. */
    val default: Boolean,
    val risk: Risk = Risk.NORMAL,
    /** The version after which the flag is taken out of the code (it has been on for two versions by then). */
    val sunset: String? = null,
    val owner: String = "dd",
    /** The version the flag came in. */
    val since: String = "1.28.0",
    /** [FLAG-02] Step 2: outside this versionCode range the flag is off; the server may set its own range. */
    val minApp: Int? = null,
    val maxApp: Int? = null,
    /** [FLAG-34] Which heading the developer app lists it under (the server's catalog is made from this book). */
    val group: FlagGroup = FlagGroup.SERVER,
)

/** [FLAG-34] The developer app's headings for the book; [key] is the name the server and its screens use. */
enum class FlagGroup(val key: String, val words: String) {
    MONEY("money", "결제·광고"),
    TYPE("type", "근무 유형"),
    LAW("law", "법 시행"),
    SERVER("server", "서버 기능"),
    STATS("stats", "사용 통계"),
    FREE("free", "무료로 연 프로 기능"),
}

/** [FLAG-04] LEGAL rows (payments, ads, new data sent) read the date the server confirmed and keep their policy check. */
enum class Risk(val words: String) { NORMAL("보통"), LEGAL("법적") }

/** [FLAG-01] What a row rests on: 처리방침 판·항, a decision (D…), a requirement ID, or a law. */
data class Basis(
    val policy: String? = null,
    val decision: String? = null,
    val requirement: String? = null,
    val note: String? = null,
) {
    val words: String get() = listOfNotNull(policy?.let { "처리방침 $it" }, decision?.let { "결정 $it" }, requirement, note).joinToString(" · ")
}

/**
 * [FLAG-01] One condition of a row (계약 2-1: date, policy, consent, profile, app, workType, rollout, manual, admin), plus
 * [AnyOf] for '또는'. Conditions of a row are joined by '그리고'.
 */
sealed interface Condition {
    /**
     * From 0시 KST of [from], or of the effective day of 처리방침 [policy] as [PolicyCalendar] has it now (so a date the
     * server moves moves every row resting on that version). Exactly one of the two is set.
     */
    data class Date(val from: LocalDate? = null, val policy: String? = null) : Condition {
        init { require((from == null) != (policy == null)) { "date: from or policy" } }
    }

    /**
     * 처리방침 [version] is not held on this phone: it agreed (or was installed on or after the day it was posted), or the
     * version is not posted yet, or its held-until day passed. The same as 1.27 `PolicyConsent.asks` (결정 D84).
     */
    data class Policy(val version: String) : Condition

    /**
     * A consent the user gave on this phone ([key]: `ads` Google's ad consent, `usage` 사용 통계, `usage.stats.more` the
     * 3.2 extra items). [unknownMeets]: not asked yet counts as given, so the path that asks can start (AL-D5).
     */
    data class Consent(val key: String, val unknownMeets: Boolean = false) : Condition

    /** Something the user told the app ([key]: `pay.setup`, `company.linked`, `setup.done`). */
    data class Profile(val key: String) : Condition

    /** The app's versionCode in [min]..[max] (open ends null). */
    data class App(val min: Int? = null, val max: Int? = null) : Condition

    /** The calendar in use is one of [types] ([com.bamnat.core.work.WorkStyle] names). */
    data class WorkType(val types: Set<String>) : Condition

    /** [percent]% of installs, by a stable hash of the install id and the key: the same phone always lands in the same place. */
    data class Rollout(val percent: Int) : Condition

    /** A fact only the developer can confirm ([key]: `play.production`, `play.subscription`, `data.safety`), read from the server. */
    data class Manual(val key: String) : Condition

    /** This phone is linked to an admin account and approved. */
    data object Admin : Condition

    /** '또는': one of [any] holds. */
    data class AnyOf(val any: List<Condition>) : Condition
}

enum class Force { ON, OFF }

/**
 * [FLAG-02] What the server says about one row (`/api/config` `flags.<key>`): a force on or off with its reason and an
 * end, conditions in place of the app's own, the versionCode range, and the [confirmed] manual facts.
 */
data class FlagOverride(
    val force: Force? = null,
    val why: String? = null,
    /** Replaces the app's conditions when set (AL-D1: a list with a kind this app does not know is dropped). */
    val conditions: List<Condition>? = null,
    /** The force ends here; null for no end. */
    val until: Instant? = null,
    val minApp: Int? = null,
    val maxApp: Int? = null,
    /** AL-D2: manual facts the developer confirmed, by key, with when. */
    val confirmed: Map<String, Instant?> = emptyMap(),
)

/** KST: legal dates (처리방침, 법 시행일) are Korean days, whatever zone the phone is in. */
val KST: ZoneId = ZoneId.of("Asia/Seoul")
