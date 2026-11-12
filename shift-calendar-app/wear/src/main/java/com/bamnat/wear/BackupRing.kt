package com.bamnat.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bamnat.core.time.Today
import com.bamnat.core.work.WatchPayload
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 2.0.0 [RVI-90] (검토 I03b-01) 워치 예비 알람. The watch kept no alarm of its own ([Ring]), so a phone whose battery ran out in the
 * night woke nobody. With 워치 진동 알람 on, the watch now sets one of its own a minute after the phone's next alarm (the one the
 * phone registered, so 이번만 끄기 and 모두 끄기 are already in it). When it comes, the wrist buzzes only if the phone's ring never
 * reached the watch and the phone cannot be reached at all: a phone that is there rang (or was turned off) by itself.
 */
object BackupRing {
    /** How long after the phone's alarm the watch waits for its ring. */
    const val WAIT_MS = 60_000L
    /** A ring from the phone this long before the alarm still counts (an early 깨우는 구간 ring, a clock a little off). */
    const val EARLY_MS = 45 * 60_000L
    /** 다시 울림 on a ring the watch started itself: the phone may be gone, so the watch rings again by itself. */
    const val SNOOZE_MS = 5 * 60_000L
    private const val REQUEST = 7402
    private const val EXTRA_AT = "at"
    private const val PREFS = "backup_ring"
    private const val K_RANG = "phone_rang"

    /** Why the backup stays quiet, or [Why.RING]. */
    enum class Why { RING, NO_ALARM, MOVED, PHONE_RANG, PHONE_THERE }

    /**
     * What to do when the backup for [at] comes. [payload] is what the watch holds now, [phoneRangAt] when the phone's ring last
     * reached the watch (0: never), [phoneThere] whether the phone answers.
     */
    fun decide(at: Long, payload: WatchPayload?, phoneRangAt: Long, phoneThere: Boolean): Why = when {
        payload == null || !payload.ringOnWatch || payload.nextAlarm == null -> Why.NO_ALARM
        millis(payload.nextAlarm!!) != at -> Why.MOVED
        phoneRangAt >= at - EARLY_MS -> Why.PHONE_RANG
        phoneThere -> Why.PHONE_THERE
        else -> Why.RING
    }

    /** The watch's own alarm for [p]'s next alarm, [WAIT_MS] after it; none without 워치 진동 알람 or an alarm ahead. */
    fun plan(context: Context, p: WatchPayload?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = p?.takeIf { it.ringOnWatch }?.nextAlarm?.let(::millis)?.takeIf { it + WAIT_MS > Today.millis() }
        if (next == null) {
            am.cancel(pending(context, 0L))
            return
        }
        set(am, next + WAIT_MS, pending(context, next))
    }

    /** The phone's ring reached the watch ([Ring.start]). */
    fun phoneRang(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_RANG, Today.millis()).apply()
    }

    /** 다시 울림 on the watch's own ring: it comes again in [SNOOZE_MS] whether or not the phone hears the button. */
    fun snooze(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        set(am, Today.millis() + SNOOZE_MS, pending(context, SNOOZE_AT))
    }

    internal fun due(context: Context, at: Long, finish: () -> Unit) {
        scope.launch {
            try {
                val rang = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_RANG, 0L)
                val p = WatchStore.load(context)
                // 다시 울림 on a ring the watch started: rings again unless the phone's own ring came meanwhile.
                val why = if (at == SNOOZE_AT) (if (rang >= Today.millis() - SNOOZE_MS - WAIT_MS) Why.PHONE_RANG else Why.RING)
                    else decide(at, p, rang, phoneThere(context))
                if (why == Why.RING) Ring.start(context, Ring.BACKUP_PREFIX + (p?.alarmLabel?.takeIf { it.isNotBlank() } ?: "알람"), backup = true)
                // The next one, from what the watch holds.
                if (at != SNOOZE_AT) plan(context, p)
            } finally {
                finish()
            }
        }
    }

    private suspend fun phoneThere(context: Context): Boolean = withTimeoutOrNull(5_000L) {
        await(Wearable.getNodeClient(context).connectedNodes).orEmpty().isNotEmpty()
    } ?: false

    private fun set(am: AlarmManager, at: Long, op: PendingIntent) {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    private fun pending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(context, if (at == SNOOZE_AT) REQUEST + 1 else REQUEST,
        Intent(context, BackupRingReceiver::class.java).putExtra(EXTRA_AT, at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun at(intent: Intent): Long = intent.getLongExtra(EXTRA_AT, 0L)

    private fun millis(t: java.time.LocalDateTime): Long = t.atZone(Today.zone()).toInstant().toEpochMilli()

    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
    }

    /** The snooze's own slot, never an alarm time. */
    private const val SNOOZE_AT = -1L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/** 2.0.0 [RVI-90] The watch's own alarm a minute after the phone's. */
class BackupRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        BackupRing.due(context.applicationContext, BackupRing.at(intent)) { pending.finish() }
    }
}
