package com.bamnat.app.company

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVN-22] 검토 I10bb-04: '해결됐어요' on the notice of an answer goes at once, without opening the app (the question's
 * fixed answer, the same as the button in 내 문의). The notice then says it was told, and goes by itself; when it could not go
 * it says so and opens the question to try again there.
 */
class InquiryFixedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() } ?: return
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                val ok = runCatching { c.inquiryApi.fixed(c.companyStore.device, id, true) }.isSuccess
                said(context, id, ok)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "com.bamnat.app.INQUIRY_FIXED"
        private const val EXTRA_ID = "id"

        fun intent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
            context, 2207, Intent(context, InquiryFixedReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id)
                .setData(android.net.Uri.parse("bamnat-inquiry://fixed/$id")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** What the notice says after the press: thanks (gone in a few seconds), or that it did not go (opens the question). */
        internal fun said(context: Context, id: String, ok: Boolean) {
            val b = NotificationCompat.Builder(context, Notifications.INQUIRY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(Notifications.open(context, NoticeLinks.Target.INQUIRY, 2205, payload = id))
            if (ok) b.setContentTitle("해결됐다고 알려 드렸어요").setContentText("고마워요. 개발자에게 바로 전해졌어요").setTimeoutAfter(6_000)
            else b.setContentTitle("알려 드리지 못했어요").setContentText("인터넷이 이어지면 눌러서 문의에서 다시 알려 주세요")
            Notifications.post(context, Notifications.INQUIRY_ID, b.build())
        }
    }
}
