package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [MON-07][REL-02] When an ad may show. 1.36 838 (사용자 2026-10-31 "띠 광고는 없애는게 좋을거 같아 압도적인 무료 성능과
 * 필수적인 보상 기능"): there is no banner anywhere. The only ad is a rewarded one, and only ever the user's choice;
 * none in the first three days after install, none for 프로, nothing before the `ads` row or without an ad unit id.
 */
object AdRules {
    const val QUIET_DAYS = 3

    fun rewardedOffer(live: Boolean, pro: Boolean, configured: Boolean, lastDayPass: LocalDate?, today: LocalDate): Boolean =
        live && !pro && configured && lastDayPass != today

    /** A day opened by a rewarded ad lasts until the next midnight on the phone's clock. */
    fun dayPassUntil(today: LocalDate, zone: ZoneId): Instant = today.plusDays(1).atStartOfDay(zone).toInstant()

    /**
     * [SRV-07] Whether Google's consent form and the ads SDK may start at all: not in the first days after
     * install either, so a new user is never asked about ads before any ad could show.
     */
    fun sdkMayStart(live: Boolean, pro: Boolean, installedAt: Instant?, now: Instant, quietDays: Int = QUIET_DAYS): Boolean =
        live && !pro && quietOver(installedAt, now, quietDays)

    /** G 1.32 [WTYP-01] 603: every ad starts muted. */
    const val MUTED = true

    /** The promise in words, for 설정 › 프로 and the ad's 닫기 sheet. */
    const val PROMISE = "광고 약속: 광고는 당신이 고를 때만, 하루 두 편까지, 소리 없이 나와요. 달력·알람·메모·설정에는 광고가 없어요. " +
        "광고가 안 와도 그냥 열려요. 프로에는 광고가 없어요."

    private fun quietOver(installedAt: Instant?, now: Instant, quietDays: Int): Boolean =
        installedAt == null || !now.isBefore(installedAt.plus(Duration.ofDays(quietDays.coerceAtLeast(0).toLong())))
}
