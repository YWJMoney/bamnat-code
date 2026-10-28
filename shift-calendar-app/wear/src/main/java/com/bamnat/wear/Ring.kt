package com.bamnat.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bamnat.core.work.WatchDays
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * [WEAR-06] 1.31 N2 워치 진동 알람. The phone's alarm rings and tells the watch ([WatchDays.RING_PATH], with what it is
 * for); the watch shows it over everything and vibrates until 끄기 or 다시 울림 (sent back to the phone, which turns its
 * own alarm off or snoozes it) or until the phone says it stopped ([WatchDays.RING_STOP_PATH]). The watch keeps no
 * alarm of its own, so nothing rings when the phone is away (the phone then rings by itself).
 */
object Ring {
    private const val CHANNEL = "ring"
    private const val ID = 7401

    /** What rings now ("근무 알람 · 주간"), or null once it stopped. The open [RingActivity] closes on null. */
    val ringing = MutableStateFlow<String?>(null)

    fun start(context: Context, what: String) {
        ringing.value = what.ifBlank { "근무 알람" }
        // The vibration is the alarm on the wrist, with or without the screen: it goes until 끄기, the phone's stop, or [LONGEST_MS].
        vibrate(context, true)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop(context) }, LONGEST_MS)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "휴대폰 알람이 울릴 때 워치에서도 알려요"
            enableVibration(false) // [vibrate] goes on for as long as it rings
        })
        val open = PendingIntent.getActivity(context, 0, Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("밤낮 알람")
            .setContentText(ringing.value)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setOngoing(true)
            // Also from the notice itself, when the screen did not open (no full-screen permission).
            .addAction(0, "끄기", answerIntent(context, WatchDays.RING_OFF_PATH, 1))
            .addAction(0, "다시 울림", answerIntent(context, WatchDays.RING_SNOOZE_PATH, 2))
            .build()
        runCatching { nm.notify(ID, n) }
        // Without the notification permission the full-screen route is closed; open the screen directly then.
        if (!nm.areNotificationsEnabled()) runCatching { context.startActivity(Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun stop(context: Context) {
        handler.removeCallbacksAndMessages(null)
        vibrate(context, false)
        ringing.value = null
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /** 끄기 or 다시 울림: quiet at once, then the button goes to the phone (outliving the screen, which closes now). */
    fun answer(context: Context, path: String) {
        stop(context)
        scope.launch { WatchStore.send(context.applicationContext, path, "") }
    }

    private fun answerIntent(context: Context, path: String, code: Int): PendingIntent = PendingIntent.getBroadcast(context, code,
        Intent(context, RingActionReceiver::class.java).putExtra(RingActionReceiver.EXTRA_PATH, path), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The phone stops its alarm after at most 60 minutes; the watch never goes on longer, even when the phone's stop is lost. */
    private const val LONGEST_MS = 60 * 60 * 1000L

    private fun vibrate(context: Context, on: Boolean) {
        val v = (if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)) ?: return
        if (on) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 1)) else v.cancel()
    }
}

/** [WEAR-06] The phone's ring messages; only Google Play services binds it. */
class RingListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WatchDays.RING_PATH -> Ring.start(applicationContext, String(event.data))
            WatchDays.RING_STOP_PATH -> Ring.stop(applicationContext)
        }
    }
}

/** [WEAR-06] 끄기 and 다시 울림 pressed on the notice. */
class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf { it == WatchDays.RING_OFF_PATH || it == WatchDays.RING_SNOOZE_PATH } ?: return
        Ring.answer(context, path)
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}

/** [WEAR-06] The ringing screen: vibrates, with 끄기 and 다시 울림 as two large buttons. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch { Ring.ringing.collect { if (it == null) finish() } }
        setContent { MaterialTheme { RingScreen(Ring.ringing.value ?: "근무 알람", onOff = { answer(WatchDays.RING_OFF_PATH) }, onSnooze = { answer(WatchDays.RING_SNOOZE_PATH) }) } }
    }

    private fun answer(path: String) {
        Ring.answer(applicationContext, path)
    }

}

@Composable
private fun RingScreen(what: String, onOff: () -> Unit, onSnooze: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(what, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2)
        Button(onClick = onOff, modifier = Modifier.fillMaxWidth(), label = { Text("끄기") })
        Button(onClick = onSnooze, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors(), label = { Text("다시 울림") })
    }
}
