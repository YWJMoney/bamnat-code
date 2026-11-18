package com.bamnat.app.sleep

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.sleep.SleepAuto
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.time.Today
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.location.SleepSegmentRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-01] 잠 자동으로 잡기: Google Play services tells the app when the phone saw a sleep (light and stillness, no
 * microphone); the app keeps the pieces here, joins them with what it knows (the alarm the user turned off, the sleep already
 * in the log) and asks once in the morning, "07:40–14:10 · 6시간 30분, 맞나요?". Everything stays on the phone.
 *
 * On only with the setting, the `sleep.auto` flag (처리방침 새 판) and the activity recognition permission; [sync] keeps the
 * subscription to that, and is called again after a reboot or an update (subscriptions do not survive them).
 */
object SleepAutoWatch {
    private const val PREFS = "sleep_auto"
    private const val K_SEGMENTS = "segments"
    private const val K_ALARMS = "alarms_off"
    private const val K_DECLINED = "declined"
    private const val K_STREAK = "streak"
    private const val K_ON = "subscribed"
    private const val REQ_UPDATES = 3401
    private const val REQ_YES = 3402
    private const val REQ_NO = 3403
    const val ACTION_YES = "com.bamnat.app.sleep.AUTO_YES"
    const val ACTION_NO = "com.bamnat.app.sleep.AUTO_NO"
    const val EXTRA_KEY = "key"
    /** Pieces and alarms older than this are of no use to a sleep still to be offered. */
    private const val KEEP_MILLIS = 3 * 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Whether the phone is listening now: the setting on, the flag open and the permission given. */
    fun active(context: Context): Boolean {
        val c = context.appContainer
        return c.settingsRepository.current.sleepAuto && c.flags.on(FlagCatalog.SLEEP_AUTO) && permitted(context)
    }

    /** Subscribes or lets go, to match [active]. Safe to call often. */
    fun sync(context: Context) {
        val want = active(context)
        val client = ActivityRecognition.getClient(context)
        if (want) {
            runCatching {
                @Suppress("MissingPermission")
                client.requestSleepSegmentUpdates(updates(context), SleepSegmentRequest(SleepSegmentRequest.SEGMENT_EVENTS_ONLY))
                    .addOnSuccessListener { prefs(context).edit().putBoolean(K_ON, true).apply() }
            }
        } else if (prefs(context).getBoolean(K_ON, false)) {
            runCatching { client.removeSleepSegmentUpdates(updates(context)) }
            prefs(context).edit().putBoolean(K_ON, false).apply()
        }
    }

    private fun updates(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_UPDATES, Intent(context, SleepSegmentReceiver::class.java),
        // Play services fills in the events, so this one has to stay mutable (Android 12+).
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    // ---- what the phone saw ----

    internal fun addSegments(context: Context, segments: List<SleepAuto.Segment>, now: Long = Today.millis()) {
        val keep = readSegments(context).filter { it.end.millis() > now - KEEP_MILLIS } + segments
        val a = JSONArray()
        keep.distinctBy { it.key }.forEach { a.put(JSONObject().put("s", it.start.millis()).put("e", it.end.millis())) }
        prefs(context).edit().putString(K_SEGMENTS, a.toString()).apply()
    }

    fun readSegments(context: Context): List<SleepAuto.Segment> = runCatching {
        val a = JSONArray(prefs(context).getString(K_SEGMENTS, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { SleepAuto.Segment(local(it.getLong("s")), local(it.getLong("e"))) } }
    }.getOrDefault(emptyList())

    /** The alarm turned off now: the likely end of a sleep. Called from the alarm service; cheap, and only kept a few days. */
    fun alarmOff(context: Context, now: Long = Today.millis()) {
        val list = readAlarms(context).map { it.millis() }.filter { it > now - KEEP_MILLIS } + now
        prefs(context).edit().putString(K_ALARMS, list.joinToString(",")).apply()
    }

    private fun readAlarms(context: Context): List<LocalDateTime> =
        prefs(context).getString(K_ALARMS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull()?.let(::local) }

    private fun declined(context: Context): Set<String> = prefs(context).getStringSet(K_DECLINED, emptySet()).orEmpty()

    fun streak(context: Context): Int = prefs(context).getInt(K_STREAK, 0)

    /** The sleeps to offer now, oldest first. */
    fun candidates(context: Context, logged: List<SleepEntry>, now: LocalDateTime = Today.dateTime()): List<SleepAuto.Segment> =
        SleepAuto.candidates(readSegments(context), logged, readAlarms(context), declined(context), now)

    /** "맞아요": into the log as 자동, and one more yes towards trusting it. */
    suspend fun accept(context: Context, s: SleepAuto.Segment, fixed: Boolean = false) {
        context.appContainer.sleepRepository.addAuto(s.start, s.end)
        prefs(context).edit().putInt(K_STREAK, if (fixed) 0 else streak(context) + 1).apply()
        Notifications.cancel(context, Notifications.SLEEP_AUTO_ID)
    }

    /** "아니에요": not asked again, and the trust starts over. */
    fun decline(context: Context, s: SleepAuto.Segment) {
        prefs(context).edit().putStringSet(K_DECLINED, declined(context) + s.key).putInt(K_STREAK, 0).apply()
        Notifications.cancel(context, Notifications.SLEEP_AUTO_ID)
    }

    /**
     * After new pieces: trusted sleeps go in by themselves (with a quiet line saying so), otherwise the newest is asked about
     * on a quiet notice. The sleep screen asks the same in a card, so nothing is lost if the notice is swiped away.
     */
    suspend fun review(context: Context, phone: Boolean = false) {
        // 2.0.0 [RVI-96] A night the charger saw ([SleepNoTap]) needs no activity permission.
        if (!active(context) && !(phone && SleepNoTap.on(context))) return
        val c = context.appContainer
        val logged = c.sleepRepository.recent(days = 3).first() + c.healthSleep.sleeps.value
        val offer = candidates(context, logged)
        if (offer.isEmpty()) return
        if (SleepAuto.trusted(streak(context))) {
            offer.forEach { c.sleepRepository.addAuto(it.start, it.end) }
            post(context, offer.last(), asked = false)
        } else post(context, offer.last(), asked = true)
    }

    private fun post(context: Context, s: SleepAuto.Segment, asked: Boolean) {
        val line = SleepAuto.line(s, Today.dateTime())
        val b = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentTitle(if (asked) "자동으로 잡은 잠, 맞나요?" else "자동으로 잡은 잠을 넣었어요")
            .setContentText(if (asked) line else "$line · 다르면 수면 기록에서 고쳐요")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.SLEEP, 3404))
        if (asked) {
            b.addAction(action(context, "맞아요", ACTION_YES, REQ_YES, s.key))
            b.addAction(action(context, "아니에요", ACTION_NO, REQ_NO, s.key))
        }
        Notifications.post(context, Notifications.SLEEP_AUTO_ID, b.build())
    }

    private fun action(context: Context, title: String, action: String, req: Int, key: String) =
        NotificationCompat.Action.Builder(0, title, PendingIntent.getBroadcast(
            context, req, Intent(context, SleepAutoActionReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )).setAuthenticationRequired(false).build()

    private fun LocalDateTime.millis(): Long = atZone(Today.zone()).toInstant().toEpochMilli()
    private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Today.zone())

    internal fun segmentOf(e: SleepSegmentEvent): SleepAuto.Segment? =
        if (e.status != SleepSegmentEvent.STATUS_SUCCESSFUL) null
        else SleepAuto.Segment(local(e.startTimeMillis), local(e.endTimeMillis))
}

/** Sleep pieces from Play services, kept and then looked at. */
class SleepSegmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SleepSegmentEvent.hasEvents(intent)) return
        val segments = SleepSegmentEvent.extractEvents(intent).mapNotNull(SleepAutoWatch::segmentOf)
        if (segments.isEmpty()) return
        SleepAutoWatch.addSegments(context, segments)
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try { SleepAutoWatch.review(context) } finally { pending.finish() }
        }
    }
}

/** 맞아요 / 아니에요 from the notice, from the lock screen too. */
class SleepAutoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(SleepAutoWatch.EXTRA_KEY) ?: return
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                val c = context.appContainer
                val logged = c.sleepRepository.recent(days = 3).first() + c.healthSleep.sleeps.value
                val s = SleepAutoWatch.candidates(context, logged).firstOrNull { it.key == key } ?: return@launch
                when (intent.action) {
                    SleepAutoWatch.ACTION_YES -> SleepAutoWatch.accept(context, s)
                    SleepAutoWatch.ACTION_NO -> SleepAutoWatch.decline(context, s)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
