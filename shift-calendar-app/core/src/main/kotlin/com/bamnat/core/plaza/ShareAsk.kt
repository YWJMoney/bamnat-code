package com.bamnat.core.plaza

/**
 * [PLAZA-04] When to ask again to put a hand-made setup on the plaza (U2: "많은 참여가 어플의 발전의 핵심"), without
 * nagging: the first "다음에" waits 14 days, the second 30, every one after 90; "그만 볼게요" ends it (결정 X-D5).
 * Only at a good moment ([Moment]), right after the work was done, so the reason is fresh.
 */
object ShareAsk {
    enum class Moment(val title: String) {
        /** The first setup was finished by hand: no company setup was used. */
        SETUP("직접 맞춘 근무, 같은 회사 분들께도 알려 줄까요?"),
        /** The pay setup was made by hand. */
        PAY("직접 맞춘 급여 방식, 광장에 올려 줄까요?"),
        /** The payslip and the app came to the same month. */
        MATCHED("명세서와 맞았어요. 이 방식을 동료와 나눌까요?"),
        /** Rules were read from a payslip or a company rule photo and applied (V 376). */
        PAYSLIP("명세서로 맞춘 규칙, 같은 회사 분들이 바로 쓰게 올려 줄까요?"),
        /** The user made an item of the company's own. */
        MADE("우리 회사 방식, 동료에게도 알려 줄까요?"),
    }

    private const val DAY = 24L * 60 * 60 * 1000
    val WAITS_DAYS = listOf(14L, 30L, 90L)

    /** Days to wait after the [laters]th "다음에" (1 for the first). */
    fun waitDays(laters: Int): Long = WAITS_DAYS[(laters - 1).coerceIn(0, WAITS_DAYS.lastIndex)]

    /** The time the card may show again after one more "다음에", from [now]. */
    fun nextDue(laters: Int, now: Long): Long = now + waitDays(laters) * DAY

    /** Whether to show: never after "그만 볼게요", not before [due], and not when the setup is the company's own anyway. */
    fun due(never: Boolean, due: Long, now: Long, usesCompanySetup: Boolean): Boolean = !never && now >= due && !usesCompanySetup
}
