package com.bamnat.core.flags

import com.bamnat.core.ops.PolicyConsent
import java.time.LocalDate

/**
 * [FLAG-03] The one place 처리방침 dates live (검토 2-4): each version's posted day, effective day and the day it applies
 * without a yes ([heldUntil]). The app ships with [SEED]; the server's `policy` list replaces a version's dates
 * ([merged]), so moving a date no longer needs a new app (1.27 changed three constants in `PolicyConsent` and shipped).
 */
data class PolicyVersion(
    val version: String,
    val posted: LocalDate,
    val effective: LocalDate,
    /** Until this day a phone installed before [posted] that did not say yes is held; null is [effective]. */
    val heldUntil: LocalDate? = null,
) {
    val holdEnds: LocalDate get() = heldUntil ?: effective
}

data class PolicyCalendar(val versions: List<PolicyVersion>) {
    fun of(version: String): PolicyVersion? = versions.firstOrNull { it.version == version }

    /** The server's entries over these, by version; versions only one side has are kept. */
    fun merged(server: List<PolicyVersion>): PolicyCalendar {
        if (server.isEmpty()) return this
        val byVersion = LinkedHashMap<String, PolicyVersion>()
        versions.forEach { byVersion[it.version] = it }
        server.forEach { byVersion[it.version] = it }
        return PolicyCalendar(byVersion.values.sortedWith { a, b -> compareVersions(a.version, b.version) })
    }

    /**
     * Whether 처리방침 [version] holds this phone back on [date]: posted, not yet past its held-until day, the phone was
     * installed before it was posted (or that is not known) and has not said yes to it or a later version. Null when
     * the version is not in the calendar. The same answer as 1.27 `PolicyConsent.asks` for 3.1.
     */
    fun holds(version: String, installed: LocalDate?, agreed: Set<String>, date: LocalDate): Boolean? {
        val v = of(version) ?: return null
        if (agreedAtLeast(agreed, version)) return false
        return date >= v.posted && date < v.holdEnds && (installed == null || installed < v.posted)
    }

    companion object {
        /**
         * 3.1 from `ops/PolicyConsent` (결정 D84). 3.2 (사용 통계에 켜진 기능 목록·앱 시작 시간, 순번 459, 결정 D100) as the
         * published page says (`privacy/index.html`, `LegalPagesTest`): posted 2026-10-28, effective 2026-11-27 (30 days, items
         * added), no hold (its own yes is the gate). If the page goes up on another day, the server's `policy` (관리자 앱
         * 기능 관리 › 일정 › 처리방침 일정) moves both dates without a new app.
         */
        val SEED = PolicyCalendar(listOf(
            PolicyVersion(PolicyConsent.VERSION, PolicyConsent.POSTED, PolicyConsent.EFFECTIVE, PolicyConsent.HELD_UNTIL),
            PolicyVersion("3.2", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27)),
            // [PHOTO-09] 1.29: 사진이 달라요 횟수 (1-13항); the same day as 3.2 so both change at once.
            PolicyVersion("3.3", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27)),
            // [ITEM-03] 1.30: 회사 공통 수당 금액(결정 D102)과 사진이 달라요 이유 두 개; the same day again so all change at once.
            PolicyVersion("3.4", LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27)),
            // [STAT-08] 1.31: 조회에 걸린 시간 구간 (1-9항, N4); posted with 1.31 the next day, 30 days as items are added.
            PolicyVersion("3.5", LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 28)),
        ))

        /** "3.10" after "3.9": compared part by part as numbers. */
        fun compareVersions(a: String, b: String): Int {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }

        fun agreedAtLeast(agreed: Set<String>, version: String): Boolean = agreed.any { compareVersions(it, version) >= 0 }
    }
}
