package com.bamnat.app.money

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.bamnat.app.appContainer
import com.bamnat.core.money.PaymentParser
import com.bamnat.core.money.ShopCapture
import kotlinx.coroutines.launch

/**
 * [LEDG-02] Writes payments down from card, bank and pay-app notifications (and the SMS app's
 * notifications), ported from 살림왕's CaptureService. Every other notification is looked at once
 * on the phone and dropped; nothing is sent anywhere. No SMS permission is used.
 */
class CaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching { read(sbn) }
    }

    /**
     * 2.0.0 [RVK-16] (검토 MONEY-13): the phone connected the reader (at start, after it was cut, after 다시 연결). Payment
     * notifications still in the shade from the last day are read now, so one that came while it was cut is not lost; one read
     * before is known again by its key and time ([com.bamnat.app.data.CaptureSeen]) and not written twice.
     */
    override fun onListenerConnected() {
        val container = applicationContext.appContainer
        // The very first time it is turned on, only what comes next is read, as the 읽어도 될까요 page says.
        val before = container.captureHealth.state.value.connectedAt
        container.captureHealth.connected()
        if (before == null) return
        val since = com.bamnat.core.time.Today.millis() - CATCH_UP_MILLIS
        runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.postTime >= since }
            .sortedBy { it.postTime }
            .forEach { runCatching { read(it) } }
    }

    /** 2.0.0 [RVK-16]: the phone cut the reader; 자동 기록 says so, and the phone is asked to connect it again. */
    override fun onListenerDisconnected() {
        applicationContext.appContainer.captureHealth.cut()
        if (Build.VERSION.SDK_INT >= 24) runCatching { NotificationListenerService.requestRebind(component(this)) }
    }

    private fun read(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bodies = ArrayList<String>()
        // 2.0.0 [RVK-14] (검토 MONEY-43·62): what tells one message from another — a chat message's own time, else this
        // notification's key and time — so the same message posted again is read once, and the same app's next one is not.
        val origins = ArrayList<String>()
        @Suppress("DEPRECATION")
        val messages = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!messages.isNullOrEmpty()) {
            // A messaging-style notification stacks texts; each is one message (repeats are dropped later).
            // The whole stack is posted again with each new message, so only recent ones are read; an
            // old line the user already deleted or fixed must not come back.
            messages.forEach { m ->
                val b = m as? Bundle ?: return@forEach
                val at = b.getLong("time", sbn.postTime)
                if (at >= sbn.postTime - STACK_WINDOW_MILLIS) b.getCharSequence("text")?.toString()?.trim()?.let {
                    bodies += it
                    origins += "${sbn.packageName}|m$at"
                }
            }
        } else {
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            bodies += big.ifEmpty { ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty() }
            origins += "${sbn.packageName}|${sbn.key}|${sbn.postTime}"
        }
        val appName = lazy {
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
                .getOrDefault(sbn.packageName)
        }
        // [SHOP-08] A delivery app's order or a card terminal's approval is read too, for a shop calendar.
        // [INV-30] A broker's or exchange's 체결 notification is read too, for an investor calendar.
        val found = bodies.mapIndexed { i, b -> "$title\n$b".trim() to origins.getOrElse(i) { "${sbn.packageName}|${sbn.postTime}" } }.filter { (it, _) ->
            PaymentParser.looksFinancial(it) || ShopCapture.looksLikeSale(sbn.packageName, appName.value, it) ||
                com.bamnat.app.invest.TradeCapture.worthReading(sbn.packageName, it, sbn.packageName in MESSENGERS)
        }
        if (found.isEmpty()) return
        val app = appName.value
        val container = applicationContext.appContainer
        // [LEDG-22] Listed for the user to turn off even when it is off already; then their filter.
        val prefs = container.autoRecordPrefs
        prefs.sawApp(sbn.packageName, app)
        // 2.0.0 [RVK-16] "마지막으로 읽음": a payment notification reached the reader, whatever is done with it next.
        container.captureHealth.sawPayment()
        val filter = prefs.current.filter
        val texts = found.filter { (it, _) -> !filter.drops(sbn.packageName, it) }
        if (texts.isEmpty()) return
        val messenger = sbn.packageName in MESSENGERS
        val pkg = sbn.packageName
        container.appScope.launch {
            texts.forEach { (text, origin) ->
                // Any text from any app reaches the parser; one it trips on must not take the app down.
                // [CALS-04] Written into the calendar its card, account or app belongs to, and only there.
                runCatching { container.capture(text, pkg, app, sbn.postTime, messenger, origin) }
            }
        }
    }

    companion object {
        private const val STACK_WINDOW_MILLIS = 10L * 60 * 1000

        /** 2.0.0 [RVK-16] How far back a reconnected reader looks in the shade: well inside what CaptureSeen remembers (2 days). */
        private const val CATCH_UP_MILLIS = 24L * 60 * 60 * 1000

        /** 2.0.0 [RVK-16] 다시 연결: asks the phone to connect the reader again (it stays on and allowed). */
        fun reconnect(context: Context) {
            if (!isOn(context)) return
            if (Build.VERSION.SDK_INT >= 24) runCatching { NotificationListenerService.requestRebind(component(context)) }
            // Off and on again makes the phone bind it anew where a rebind alone does not.
            setOn(context, false)
            setOn(context, true)
        }

        /** Chat apps: card and bank 알림톡 arrive here too, but so do people's own messages. */
        private val MESSENGERS = setOf(
            "com.kakao.talk", "jp.naver.line.android", "org.telegram.messenger", "com.whatsapp",
            "com.facebook.orca", "com.instagram.android", "com.discord", "com.google.android.apps.dynamite",
        )

        private fun component(context: Context) = ComponentName(context, CaptureService::class.java)

        /** Turned on in the app (the service exists) — access may still be missing. */
        fun isOn(context: Context): Boolean =
            context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        /** The user granted notification access in the phone's settings. */
        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun setOn(context: Context, on: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        /** The phone's page for this app's notification access. */
        fun accessSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            } else {
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
    }
}
