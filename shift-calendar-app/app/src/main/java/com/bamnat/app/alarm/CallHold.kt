package com.bamnat.app.alarm

import android.media.AudioManager

/**
 * 2.0.0 [RVI-36] (검토 PC-26·ALM-01) The alarm's wait for a call, step by step, kept out of the service so a 4분 통화 can be
 * replayed in a test. A phone call (ringing, in a call, screening) holds the sound until it ends, at most
 * [AlarmService.CALL_HOLD_MILLIS]; a call in an app (메신저 통화 mode, which some apps leave on) at most
 * [AlarmService.APP_CALL_HOLD_MILLIS]. A mode whose hold ran out does not hold again (1.39.1 R391-02).
 *
 * PC 9차 (11-12): a 255초 phone call kept the alarm quiet and it rang 2초 after the call ended. That is this rule for a phone
 * call (끊으면 울림, 30분까지); the 3분 is for a call in an app. The ring now also starts the moment the mode changes (Android 12+),
 * not up to 1.5초 later, and each step is written to logcat (tag [TAG]) so a long call can be checked on the PC.
 */
internal class CallHold {
    enum class Outcome { FREE, HOLD, CAPPED, ENDED }

    /** What [check] decided: [outcome] for the phone's [mode], having held [heldMillis] so far. */
    data class Step(val outcome: Outcome, val mode: Int, val heldMillis: Long) {
        val holds: Boolean get() = outcome == Outcome.HOLD
    }

    private var since = 0L
    private var spentMode: Int? = null

    /** The mode being held for, null when not holding. */
    var holding: Int? = null
        private set

    fun check(mode: Int, now: Long): Step {
        if (spentMode != null && spentMode != mode) spentMode = null
        val held = if (since == 0L) 0L else now - since
        if (spentMode == mode) {
            holding = null
            return Step(Outcome.FREE, mode, 0L)
        }
        val holds = AlarmService.holdsForCall(mode, held)
        val wasHolding = since != 0L
        since = if (holds) (if (since == 0L) now else since) else 0L
        holding = if (holds) mode else null
        return when {
            holds -> Step(Outcome.HOLD, mode, held)
            wasHolding && mode != AudioManager.MODE_NORMAL -> { spentMode = mode; Step(Outcome.CAPPED, mode, held) }
            wasHolding -> Step(Outcome.ENDED, mode, held)
            else -> Step(Outcome.FREE, mode, 0L)
        }
    }

    fun reset() {
        since = 0L
        spentMode = null
        holding = null
    }

    companion object {
        const val TAG = "BamnatAlarm"

        /** The notice's line while holding for [mode]: a phone call rings when it ends; a call in an app within 3분. */
        fun line(mode: Int?): String = when (mode) {
            AudioManager.MODE_IN_COMMUNICATION -> "통화 앱이 소리를 쓰는 중이라 잠시 멈췄어요 · 3분 안에 다시 울려요"
            else -> "통화 중이라 잠시 멈췄어요 · 통화가 끝나면 바로 울려요"
        }
    }
}
