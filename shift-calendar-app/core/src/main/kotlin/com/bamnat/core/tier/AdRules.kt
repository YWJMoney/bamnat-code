package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [MON-07][REL-02] When an ad may show (결정 1: A+D). One banner under the 근무·급여 and 돈 tabs, never
 * on the calendar, memos, settings, alarms or the day screen; none in the first three days after
 * install; none for 프로; nothing before the 정식 출시 switch or without ad unit ids. A rewarded ad is
 * only ever the user's choice, once a day, and opens the pro features until midnight.
 */
object AdRules {
    const val QUIET_DAYS = 3

    enum class Place { CALENDAR, MONEY, STATS, MEMOS, SETTINGS, OTHER }

    private val bannerPlaces = setOf(Place.MONEY, Place.STATS)

    fun banner(place: Place, live: Boolean, pro: Boolean, configured: Boolean, installedAt: Instant?, now: Instant, quietDays: Int = QUIET_DAYS): Boolean =
        live && !pro && configured && place in bannerPlaces && quietOver(installedAt, now, quietDays)

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

    /**
     * G 1.32 [WTYP-01] 603: the promises shown to the user (조사 `근무유형별-서비스-조사.md` 1·3장: 실행 직후 광고, 소리, 닫기 없음이
     * 경쟁 앱 ★1의 첫 이유). Every ad starts muted ([MUTED]); no banner for [LAUNCH_QUIET_SECONDS] after the app comes to the
     * front (오늘 근무를 보려고 연 순간), none while the keyboard is up (적는 중), and a 닫기 is always beside it: closed, it
     * stays away until the app is opened again.
     */
    const val MUTED = true
    const val LAUNCH_QUIET_SECONDS = 20L

    fun bannerNow(allowed: Boolean, openedAt: Instant?, now: Instant, typing: Boolean, closed: Boolean): Boolean =
        allowed && !typing && !closed && (openedAt == null || !now.isBefore(openedAt.plusSeconds(LAUNCH_QUIET_SECONDS)))

    /** The promise in words, for 설정 › 프로 and the ad's 닫기 sheet. */
    const val PROMISE = "광고 약속: 소리 없이 나와요. 앱을 켠 직후와 글을 쓰는 중에는 나오지 않아요. 닫기 버튼이 늘 옆에 있어요. " +
        "달력·메모·알람·설정 화면에는 광고가 없어요. 프로에서는 광고가 하나도 없어요."

    private fun quietOver(installedAt: Instant?, now: Instant, quietDays: Int): Boolean =
        installedAt == null || !now.isBefore(installedAt.plus(Duration.ofDays(quietDays.coerceAtLeast(0).toLong())))
}
