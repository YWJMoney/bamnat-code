package com.bamnat.app.company

import com.bamnat.core.plaza.CareStanding
import com.bamnat.core.time.Today

/**
 * 2.0.0 [RVN-22] 검토 I10bc-01: the 마을 돌봄 standing last read (경고·쉬기·쓰기 제한), from the day's digest or the village's own ask, in
 * memory only. The village shows it at once and asks again only when it is older than [FRESH_MS] (before, every return to the village
 * asked). Nothing here decides anything: the server holds every limit itself. Kept for one [CompanyApi] (the app's one; a test's own
 * fake never sees another's).
 */
object CareKept {
    /** How long a standing read is shown without asking again. */
    const val FRESH_MS = 5 * 60 * 1000L

    private class Kept(val api: CompanyApi, val standing: CareStanding, val at: Long)
    @Volatile private var kept: Kept? = null

    fun keep(api: CompanyApi, s: CareStanding, now: Long = Today.millis()) {
        kept = Kept(api, s, now)
    }

    /** The standing last read through [api], or null. */
    fun of(api: CompanyApi): CareStanding? = kept?.takeIf { it.api === api }?.standing

    fun fresh(api: CompanyApi, now: Long = Today.millis()): Boolean =
        kept?.let { it.api === api && now - it.at in 0 until FRESH_MS } == true

    /** Forgets it (모두 지우기). */
    fun clear() {
        kept = null
    }
}
