package com.bamnat.core.guide

import java.time.LocalDate

/**
 * AG 1.27 [INVITE-11][INVITE-12] What this phone remembers about the asks to grow the app together (계획/1.27-AG-개발계획.md
 * 434·435, 결정 AG-D6): the calendar's 친구 초대 card (the day it showed, the day it was closed), the 설정 card's close,
 * the last app-stop note already offered for a 문의, and the day's 되돌리기 count with the last day it led to a line.
 * Kept on the phone only (설정 파일 `grow`, not in a backup).
 */
data class GrowState(
    val inviteShownOn: LocalDate? = null,
    val inviteClosedOn: LocalDate? = null,
    val cardClosedOn: LocalDate? = null,
    val crashSeenAt: Long = 0,
    val undoDay: LocalDate? = null,
    val undoCount: Int = 0,
    val undoToldOn: LocalDate? = null,
)

/**
 * AG 1.27 [INVITE-11][INVITE-12] When to ask, so asking never becomes a bother (사용자 리뷰 2026-10-25: "많은 사람을 초대할수록
 * 어플에는 좋으니깐 … 버그나 오류 개선점을 많이 남길수 있도록 문의활용성을 권고해야해"). Each can be switched off in 기능 켜고 끄기.
 */
object GrowNudges {
    /** The calendar's invite card waits until the app has been on the phone this many days. */
    const val INVITE_AFTER_DAYS = 3L
    /** After it showed (it stays that day) or was closed, it comes again this many days on. */
    const val INVITE_EVERY_DAYS = 14L
    /** The 설정 card, closed, comes back this many days on. */
    const val CARD_AGAIN_DAYS = 30L
    /** 되돌리기 this many times in a day says the feedback line … */
    const val UNDO_COUNT = 3
    /** … at most once in this many days. */
    const val UNDO_EVERY_DAYS = 14L
    /** An app-stop older than this is not brought up any more. */
    const val CRASH_WITHIN_DAYS = 7L

    /** The calendar's 친구 초대 card today: after the first setup, [INVITE_AFTER_DAYS] after the install, a day at a time. */
    fun invite(s: GrowState, installedOn: LocalDate?, today: LocalDate, setupDone: Boolean): Boolean {
        if (!setupDone || installedOn == null || today.isBefore(installedOn.plusDays(INVITE_AFTER_DAYS))) return false
        if (s.inviteClosedOn != null && today.isBefore(s.inviteClosedOn.plusDays(INVITE_EVERY_DAYS))) return false
        return s.inviteShownOn == null || s.inviteShownOn == today || !today.isBefore(s.inviteShownOn.plusDays(INVITE_EVERY_DAYS))
    }

    /** The 설정 card '함께 만들어요': until closed, then again [CARD_AGAIN_DAYS] on. */
    fun card(s: GrowState, today: LocalDate): Boolean = s.cardClosedOn == null || !today.isBefore(s.cardClosedOn.plusDays(CARD_AGAIN_DAYS))

    /** An app-stop note at [lastCrashAt] (0: none) not yet offered, and recent, asks for a 문의 once. */
    fun crash(s: GrowState, lastCrashAt: Long, nowMillis: Long): Boolean =
        lastCrashAt > s.crashSeenAt && nowMillis - lastCrashAt <= CRASH_WITHIN_DAYS * 24 * 60 * 60 * 1000

    /** One more 되돌리기 on [today]: the day's count starts again on a new day. */
    fun undone(s: GrowState, today: LocalDate): GrowState =
        if (s.undoDay == today) s.copy(undoCount = s.undoCount + 1) else s.copy(undoDay = today, undoCount = 1)

    /** Whether today's 되돌리기 says "잘 안 되는 게 있었나요?": the [UNDO_COUNT]th of the day, once in [UNDO_EVERY_DAYS]. */
    fun feedbackAfterUndo(s: GrowState, today: LocalDate): Boolean =
        s.undoDay == today && s.undoCount >= UNDO_COUNT && (s.undoToldOn == null || !today.isBefore(s.undoToldOn.plusDays(UNDO_EVERY_DAYS)))
}
