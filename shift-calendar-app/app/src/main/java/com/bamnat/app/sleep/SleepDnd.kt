package com.bamnat.app.sleep

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 1.38 [SLEEP-12] 자는 동안 방해 금지 (켜기 선택): while a sleep is open (잠들었어요, 측정 시작, the bedtime notice), the phone
 * goes to 중요한 알림만 (the user's own 방해 금지 exceptions: alarms always ring, starred people as they set them), and back
 * when the sleep ends, but only if it was 밤낮 that turned it on and the user did not change it meanwhile.
 * Needs 방해 금지 접근 (notification policy access), asked when the user turns it on.
 */
object SleepDnd {
    private const val PREFS = "sleep_dnd"
    private const val K_SET = "set_by_us"

    fun permitted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Follows the sleep: [sleeping] with [enabled] turns it on, the end of the sleep turns back what we turned on. */
    fun follow(context: Context, sleeping: Boolean, enabled: Boolean) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (!nm.isNotificationPolicyAccessGranted) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ours = prefs.getBoolean(K_SET, false)
            val filter = nm.currentInterruptionFilter
            when {
                sleeping && enabled && !ours && filter == NotificationManager.INTERRUPTION_FILTER_ALL -> {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    prefs.edit().putBoolean(K_SET, true).apply()
                }
                (!sleeping || !enabled) && ours -> {
                    // Only back from what we set; a change the user made in the night stays.
                    if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    prefs.edit().putBoolean(K_SET, false).apply()
                }
            }
        }
    }
}
