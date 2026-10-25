package com.bamnat.app.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.widget.Toast

/**
 * [NAV-02][NAV-04] 1.25 Z (Z-D2): where back would close the app (the calendar's first screen, the first setup's
 * first question), the first back only says "한 번 더 누르면 밤낮이 닫혀요" and a second within two seconds closes it.
 */
class ExitGuard {
    private var armedAt = 0L

    /** True when this back closed the app. */
    fun press(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        // 0 is "not armed": the clock can be that small right after boot (and in tests).
        if (armedAt != 0L && now - armedAt <= WINDOW_MILLIS) {
            armedAt = 0L
            (context as? Activity)?.finish()
            return true
        }
        armedAt = now
        Toast.makeText(context, MESSAGE, Toast.LENGTH_SHORT).show()
        return false
    }

    companion object {
        const val MESSAGE = "한 번 더 누르면 밤낮이 닫혀요"
        /** How long the first back stays armed. */
        const val WINDOW_MILLIS = 2_000L
    }
}
