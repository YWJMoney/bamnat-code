package com.bamnat.core.tier

import java.net.URLDecoder
import java.time.Duration
import java.time.Instant

/**
 * [INVITE-08] The rules the company server applies, as the developer app set them: [need] friends who each used the
 * app [days] days give the inviter [months] of 프로, again for every [need] more, up to [capMonths] in a year
 * (0: no cap). The app only says them; the server decides (company-worker inviteSettings).
 */
data class InviteTerms(val need: Int, val days: Int, val months: Int, val capMonths: Int) {
    /**
     * AG 1.27 [INVITE-09] As the user meant it (리뷰 2026-10-25): months for each [need] different friends who each used
     * the app [days] days, not for one friend's days. "친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또
     * 1개월(상한 없음)".
     */
    fun words(): String = "친구 ${need}명이 각자 ${days}일 이상 쓰면 나에게 프로 ${months}개월, ${need}명 더 채울 때마다 또 ${months}개월" +
        if (capMonths > 0) "(1년에 ${capMonths}개월까지)" else "(상한 없음)"

    /** AG 1.27 [INVITE-09] The short form for a button or a line: "친구 5명이 7일 쓰면 프로 1개월". */
    fun short(): String = "친구 ${need}명이 ${days}일 쓰면 프로 ${months}개월"

    companion object {
        /** Until the server says otherwise: the developer app's defaults. */
        val DEFAULT = InviteTerms(need = 5, days = 7, months = 1, capMonths = 0)
    }
}

/**
 * [INVITE-02][INVITE-03] 친구 초대 on the phone: the code as the company server makes it, the Play install referrer
 * that carries it, how long a code can still be typed by hand, and the words the inviter shares and sees.
 * The rules that give the months (the period of use, how many friends, once per phone, a cap) are the server's
 * (company-worker invites, [InviteTerms]).
 */
object InviteRules {
    /** The referrer's utm_source for an invite; utm_content is the code (company-worker storeLink). */
    const val SOURCE = "bamnat_invite"
    /** A code typed by hand counts only this many days after the install (research/1.26-AE-초대-조사.md 4-6). */
    val TYPE_WITHIN: Duration = Duration.ofDays(7)
    /** Play keeps the install referrer this long (Install Referrer API); after it there is nothing to read. */
    val REFERRER_WITHIN: Duration = Duration.ofDays(90)

    /** No 0/O, 1/I: eight of these 32, as the server makes them. */
    private val CODE = Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}")

    /** A code as a person may type it ("abcd-2345", with spaces), or null when it cannot be one. */
    fun code(raw: String?): String? {
        val c = raw?.uppercase()?.filter { it in 'A'..'Z' || it in '0'..'9' } ?: return null
        return c.takeIf { CODE.matches(it) }
    }

    /** "ABCD-2345": easier to read out and type. */
    fun shown(code: String): String = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code

    /**
     * The code in a Play install referrer ("utm_source=bamnat_invite&utm_content=ABCD2345"), or null for any other
     * referrer (a store search gives "utm_source=google-play&utm_medium=organic").
     */
    fun fromReferrer(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        val params = referrer.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to runCatching { URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrDefault("")
        }.toMap()
        if (params["utm_source"] != SOURCE) return null
        return code(params["utm_content"])
    }

    /** Whether a code can still be typed by hand: within [TYPE_WITHIN] of the install. */
    fun mayType(installedAt: Instant?, now: Instant): Boolean =
        installedAt != null && !now.isBefore(installedAt) && Duration.between(installedAt, now) <= TYPE_WITHIN

    /** Whether Play may still hold this install's referrer. */
    fun mayReadReferrer(installedAt: Instant?, now: Instant): Boolean =
        installedAt == null || Duration.between(installedAt, now) <= REFERRER_WITHIN

    /**
     * [INVITE-02] What the inviter sends with the share sheet: the link, and the code for a phone the link did not reach.
     * AG 1.27 [INVITE-13]: what the app does first, then that the link is all it takes (the app recognises the invite).
     */
    fun shareText(link: String, code: String): String =
        "밤낮 근무 달력 같이 써요. 근무표·알람·월급 계산을 한 앱에서 보고, 쉬는 날도 한눈에 맞춰 볼 수 있어요.\n$link\n" +
            "이 링크로 깔면 초대가 저절로 알려져요. 링크가 안 열리면 앱을 깔고 7일 안에 설정 맨 위 ‘친구 초대’ › ‘초대 코드 넣기’에 ${shown(code)}를 넣어 주세요."

    /** [INVITE-05] The inviter's line: "초대한 친구 2명 · 프로 2개월". */
    fun summary(friends: Int, months: Int): String = "초대한 친구 ${friends}명 · 프로 ${months}개월"

    /** [INVITE-08] Toward the next months: "3/5명 · 2명은 사용 기간 채우는 중". */
    fun progress(progress: Int, need: Int, waiting: Int): String =
        "$progress/${need}명" + if (waiting > 0) " · ${waiting}명은 사용 기간 채우는 중" else ""

    /** AG 1.27 [INVITE-09] The friends still to go for the next months: "다음 프로까지 2명". */
    fun toNext(progress: Int, need: Int): String = "다음 프로까지 ${(need - progress).coerceAtLeast(1)}명"

    /** What a refusal of a friend's report means, in the friend's words. */
    fun refused(reason: String?): String = when (reason) {
        "self" -> "내 초대 코드는 넣을 수 없어요"
        "phone" -> "이 휴대폰은 이미 초대로 한 번 셌어요. 초대는 휴대폰마다 한 번이에요"
        else -> "이 초대는 셀 수 없어요"
    }
}
