package com.bamnat.app.alarm

import android.view.KeyEvent

/**
 * [ALRM-08] 버튼으로 알람 끄기 (결정 D78). The volume keys reach the ringing screen; the power button only shows as
 * the screen going off, so that counts only while the ringing screen is in front, where [android.view.WindowManager
 * .LayoutParams.FLAG_KEEP_SCREEN_ON] keeps the screen from timing out by itself.
 */
object AlarmButtons {
    enum class Action(val key: String, val label: String) {
        DISMISS("dismiss", "끄기"),
        SNOOZE("snooze", "다시 울림"),
        NONE("none", "아무것도 안 함");

        companion object {
            fun of(key: String?): Action = entries.firstOrNull { it.key == key } ?: DISMISS
        }
    }

    /** What a press does: with a mission, 끄기 only quiets the sound for [QUIET_SECONDS] and the mission stays. */
    enum class Outcome { DISMISS, SNOOZE, QUIET, NOTHING }

    const val QUIET_SECONDS = 30

    fun outcome(action: Action, mission: Boolean): Outcome = when (action) {
        Action.DISMISS -> if (mission) Outcome.QUIET else Outcome.DISMISS
        Action.SNOOZE -> Outcome.SNOOZE
        Action.NONE -> Outcome.NOTHING
    }

    val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

    /** A screen that has just come on can go off again by itself; that is no press. */
    const val SETTLE_MILLIS = 1_000L
    /** The screen going off stops the ringing screen too, sometimes just before the broadcast comes. */
    const val HIDDEN_GRACE_MILLIS = 2_000L

    /**
     * The screen went off at [now]: a press of the power button when the ringing screen was in front, shown at
     * [shownAt] (0 never) and last hidden at [hiddenAt] (0 never).
     */
    fun screenOffIsPress(shownAt: Long, hiddenAt: Long, now: Long): Boolean {
        if (shownAt == 0L || now - shownAt < SETTLE_MILLIS) return false
        val inFront = hiddenAt < shownAt
        return inFront || now - hiddenAt < HIDDEN_GRACE_MILLIS
    }

    /** The line on the ringing screen that says what the buttons do. */
    fun hint(action: Action, mission: Boolean): String? = when (outcome(action, mission)) {
        Outcome.DISMISS -> "소리·전원 버튼을 눌러도 꺼져요"
        Outcome.SNOOZE -> "소리·전원 버튼을 누르면 다시 울림이에요"
        Outcome.QUIET -> "소리·전원 버튼은 소리만 ${QUIET_SECONDS}초 줄여요. 미션을 마치면 꺼져요"
        Outcome.NOTHING -> null
    }
}
