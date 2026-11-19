package com.bamnat.app.share

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 2.0.0 [RVL-78] (검토 I12c-02 '받는 쪽이 끊기지 않는 공유') How a received link stands, for the one line under its calendar and the one card
 * that says what to do when it is about to stop: the link was cut or ran out ([Kind.GONE]), it ends within [ENDS_SOON] ([Kind.ENDS_SOON]),
 * or the days it holds run out within [LAST_DAYS] with nothing after ([Kind.RUNS_OUT]). Each comes with a message the user can send the
 * sender in one press ([ask]), in the app's own words, so the fix is one press away.
 *
 * Nothing new is asked of the server or told to anyone: it reads only what the link already says (its end date, its days, when it last
 * changed) and when this phone last got it.
 */
object LinkHealth {
    enum class Kind { OK, GONE, REMOVED, ENDS_SOON, RUNS_OUT }

    data class Health(
        val kind: Kind,
        /** The card's line; null for [Kind.OK]. */
        val line: String? = null,
        /** What the button says. */
        val action: String? = null,
        /** The message to the sender the button opens to send; null when there is no one to ask ([Kind.REMOVED]). */
        val ask: String? = null,
        /** What a closed card is remembered by: the same trouble later (a new end date, a new last day) shows again. */
        val stamp: String = "",
    )

    /** Within this, the sender's own reminder (3 days ahead, [ShareExpiryReminder.lead]) has passed and 프로 did not keep it open. */
    val ENDS_SOON: Duration = Duration.ofDays(2)
    /** Days left in the link before its last day, when the next month's roster is usually out. */
    const val LAST_DAYS = 5L

    fun of(cal: ReceivedCalendar, now: Instant, today: LocalDate): Health {
        val roster = cal.roster
        if (cal.gone) {
            return if (cal.removedReason != null) Health(Kind.REMOVED)
            else Health(Kind.GONE, "링크가 끝났어요", "새 링크 부탁하기",
                "밤낮에서 보던 $roster 링크가 끝났어요. 새 링크 하나 보내 줄래요? (밤낮 달력 위 ‘공유’ → ‘링크로 보내기’)", "gone")
        }
        val ends = cal.expiresAt
        if (ends != null && ends.isAfter(now) && Duration.between(now, ends) <= ENDS_SOON) {
            val days = ChronoUnit.DAYS.between(today, ends.atZone(com.bamnat.core.time.Today.zone()).toLocalDate())
            val left = when { days <= 0L -> "오늘"; days == 1L -> "내일"; else -> "${days}일 뒤에" }
            return Health(Kind.ENDS_SOON, "이 링크는 $left 끝나요", "연장 부탁하기",
                "밤낮에서 보던 $roster 링크가 $left 끝난대요. 밤낮 ‘공유’에서 ‘기간 늘리기’를 한 번 눌러 줄래요?", "ends:$ends")
        }
        val last = cal.schedule?.last
        if (last != null && !today.isAfter(last) && ChronoUnit.DAYS.between(today, last) < LAST_DAYS) {
            val day = "${last.monthValue}월 ${last.dayOfMonth}일"
            return Health(Kind.RUNS_OUT, "$day 뒤로는 아직 없어요", "다음 것 부탁하기",
                "밤낮에서 보던 $roster${com.bamnat.app.ui.Fmt.iga(roster)} ${day}까지만 있어요. 다음 것이 나오면 밤낮에 넣어 줄래요? 링크로 이어서 볼게요.",
                "last:$last")
        }
        return Health(Kind.OK)
    }

    /**
     * The quiet line under the calendar: when the sender last changed it (what the link already says) and until when it stays, so a copy
     * that has not changed for long is plain to see.
     */
    fun line(cal: ReceivedCalendar, today: LocalDate): String {
        val zone = com.bamnat.core.time.Today.zone()
        fun day(at: Instant): String = at.atZone(zone).toLocalDate().let { d ->
            when (ChronoUnit.DAYS.between(d, today)) {
                0L -> "오늘"
                1L -> "어제"
                else -> "${d.monthValue}월 ${d.dayOfMonth}일"
            }
        }
        return listOfNotNull(
            cal.updatedAt?.let { "보낸 분이 마지막으로 고친 날 ${day(it)}" },
            "${cal.expiresAt?.atZone(zone)?.toLocalDate()?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "기간 끝"}까지 볼 수 있어요",
            "이 휴대폰에만 있어요",
        ).joinToString(" · ")
    }
}
