package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * [MON-03] A Play subscription as the phone or the server last saw it. Play keeps a canceled
 * subscription until its [expiry], and a payment that failed keeps it for the grace period; on hold,
 * paused, expired or still pending it gives nothing.
 */
data class PlaySub(
    val state: State,
    val expiry: Instant?,
    val trial: Boolean,
    val autoRenew: Boolean,
    val purchasedAt: Instant,
    /** The server asked Play itself; false when only the phone's Play said so. */
    val verified: Boolean = false,
) {
    enum class State { ACTIVE, GRACE, CANCELED, ON_HOLD, PAUSED, EXPIRED, PENDING }

    fun entitled(now: Instant): Boolean = when (state) {
        // 1.39.1 [R391-11] (검토 PRO-08) A subscription set to renew is renewed by Play at [expiry], but the phone learns the new
        // end only when the app reads its purchases again: a 5시 alarm waking the app the night after a renewal found 프로 gone
        // and rang without its 끄기 미션. Until [RENEW_LEEWAY] past it, it still counts; one that will not renew ends on time.
        State.ACTIVE -> expiry == null || now < (if (autoRenew) expiry.plus(RENEW_LEEWAY) else expiry)
        State.GRACE, State.CANCELED -> expiry == null || now < expiry
        else -> false
    }

    companion object {
        /** 1.39.1 [R391-11] As long as Play's own grace period for a failed renewal (3일). */
        val RENEW_LEEWAY: Duration = Duration.ofDays(3)
    }
}

/** [MON-09] This phone tied to an admin account in the developer app, as the server last said. */
data class AdminLink(val approved: Boolean, val checkedAt: Instant)

/** Why this phone has 프로 now. */
enum class ProReason(val words: String) {
    NOT_LAUNCHED("정식 출시 전이라 모든 기능이 무료예요"),
    TRIAL("1개월 무료 체험 중이에요"),
    SUBSCRIPTION("프로를 구독 중이에요"),
    GRACE("결제가 안 됐어요. Play에서 결제 수단을 확인해 주세요"),
    ADMIN("관리자 휴대폰이라 프로를 무료로 써요"),
    PASS("보상 이용권으로 프로를 써요"),
    /** 1.36 841: the 1년 이용권 bought on Play, no automatic payment. */
    PASS_1Y("1년 이용권으로 프로를 써요 · 자동 결제 없음"),
    /** 1.38 Phase 3 요금: 팀 요금제·직원 요금제는 개인 프로 기능을 포함해요. */
    WORK_PLAN("팀·직원 요금제에 든 프로 기능을 써요"),
    THANKS("테스트에 참여해 주셔서 드린 프로 기간이에요"),
    DAY_PASS("광고를 보고 오늘 하루 프로 기능을 열었어요"),
    /** 1.36 [PSH-01] 프로 나눔 1인. */
    SHARED("프로를 산 분이 나눠 준 프로예요. 그분이 프로를 쓰는 동안 이어져요"),
    NONE("무료로 쓰고 있어요"),
    /** A tester's preview from the alarm check screen; [ProAccess.resolve] never answers it. */
    PREVIEW("알람 점검 화면에서 고른 등급으로 미리 보고 있어요"),
}

/** Everything that can give this phone 프로, as last known on the phone. */
data class ProSources(
    /** [MON-02] The developer app's 정식 출시 switch; off, every feature is open as before. */
    val billingLive: Boolean,
    val subscription: PlaySub? = null,
    /** [MON-08] The end of the stacked reward passes; null when there are none. */
    val passUntil: Instant? = null,
    val admin: AdminLink? = null,
    /** [MON-10] When this app was first installed and when billing was switched on, for the testers' thank-you. */
    val installedAt: Instant? = null,
    val launchedAt: Instant? = null,
    val thanksMonths: Int = 0,
    /** [MON-07] A rewarded ad watched today opens the pro features until midnight. */
    val dayPassUntil: Instant? = null,
    /** 1.36 [PSH-01] The 프로 a buyer gave this phone, until their subscription ends. */
    val sharedUntil: Instant? = null,
    /** 1.36 841: the end of the 1년 이용권 bought (stacked, [YearPass.until]). */
    val yearPassUntil: Instant? = null,
    /** 1.38 Phase 3 요금: the end of a live 팀·직원 요금제 (subscription or 1년 이용권), which includes 프로. */
    val workUntil: Instant? = null,
)

data class Access(val pro: Boolean, val reason: ProReason, val until: Instant? = null)

/**
 * [MON-02] The one answer to "does this phone have 프로": a Play subscription (trial and grace
 * included), an approved admin phone, reward passes, the testers' thank-you after launch, or a day
 * opened by a rewarded ad, in that order. Before the 정식 출시 switch every feature is open.
 */
object ProAccess {
    /** [MON-09] An admin phone that could not reach the server for a week stops getting 프로 until it does. */
    val ADMIN_CHECK: Duration = Duration.ofDays(7)

    fun resolve(s: ProSources, now: Instant, zone: ZoneId): Access {
        if (!s.billingLive) return Access(true, ProReason.NOT_LAUNCHED)
        s.subscription?.takeIf { it.entitled(now) }?.let { sub ->
            val reason = when {
                sub.state == PlaySub.State.GRACE -> ProReason.GRACE
                sub.trial -> ProReason.TRIAL
                else -> ProReason.SUBSCRIPTION
            }
            return Access(true, reason, sub.expiry)
        }
        s.workUntil?.takeIf { now < it }?.let { return Access(true, ProReason.WORK_PLAN, it) }
        s.admin?.takeIf { it.approved && Duration.between(it.checkedAt, now) < ADMIN_CHECK }?.let { return Access(true, ProReason.ADMIN) }
        s.yearPassUntil?.takeIf { now < it }?.let { return Access(true, ProReason.PASS_1Y, it) }
        s.passUntil?.takeIf { now < it }?.let { return Access(true, ProReason.PASS, it) }
        thanksUntil(s.installedAt, s.launchedAt, s.thanksMonths, zone)?.takeIf { now < it }?.let { return Access(true, ProReason.THANKS, it) }
        s.sharedUntil?.takeIf { now < it }?.let { return Access(true, ProReason.SHARED, it) }
        s.dayPassUntil?.takeIf { now < it }?.let { return Access(true, ProReason.DAY_PASS, it) }
        return Access(false, ProReason.NONE)
    }

    /**
     * [MON-10] Phones installed before the launch get [months] of 프로 from the launch: they tested the
     * app while everything was free. None for a phone installed on or after it.
     */
    fun thanksUntil(installedAt: Instant?, launchedAt: Instant?, months: Int, zone: ZoneId): Instant? {
        if (installedAt == null || launchedAt == null || months <= 0 || !installedAt.isBefore(launchedAt)) return null
        return launchedAt.atZone(zone).plusMonths(months.toLong()).toInstant()
    }
}

/**
 * 1.36 841 1년 이용권 (수익 3판 4절): a one-time Play product, no automatic payment. Each one bought adds [DAYS] days from its purchase
 * or from the end of the one before, whichever is later ("다시 사면 이어져요"). One notice [NOTICE_DAYS] days before the end; Play lets
 * the same product be bought again once the phone has used it up (consumed), which the app does from [REBUY_DAYS] before the end.
 */
object YearPass {
    const val PRODUCT = "bamnat_pro_pass_1y"
    const val DAYS = 365L
    const val NOTICE_DAYS = 7L
    const val REBUY_DAYS = 30L

    fun until(purchases: Collection<Instant>): Instant? = purchases.sorted().fold(null as Instant?) { end, at ->
        maxOf(end ?: at, at).plus(Duration.ofDays(DAYS))
    }

    /** When the end's notice is due, or null without a pass. */
    fun noticeAt(until: Instant?): Instant? = until?.minus(Duration.ofDays(NOTICE_DAYS))

    /** Whether [now] is close enough to [until] (or past it) that the pass may be bought again. */
    fun rebuy(until: Instant?, now: Instant): Boolean = until == null || now >= until.minus(Duration.ofDays(REBUY_DAYS))
}
