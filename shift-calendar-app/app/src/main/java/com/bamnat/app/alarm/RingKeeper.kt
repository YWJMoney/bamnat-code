package com.bamnat.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bamnat.core.diag.DiagEvents
import com.bamnat.core.diag.EventBook
import com.bamnat.core.time.Today

/**
 * 2.0.0 [RVIB-28] (PC-188, 안드로이드 17 폴드: 시험 알람이 울리는 중 구글 플레이 서비스가 갱신되자 앱 프로세스가 죽었고, 알람 화면과 소리가
 * 사라진 뒤 다시 울리지 않았어요) A ringing alarm outlives the app: while it rings, an exact alarm ([KEEP_AHEAD_MILLIS] on) is kept
 * pushed back every [PUSH_EVERY_MILLIS]. When the app dies mid-ring nothing pushes it, so it goes off and [AlarmReceiver] rings the
 * same alarm again, from where it was: the mission's 5 minutes and 자동으로 끄기 count from the first ring ([since]).
 *
 * Written to the device-protected store, so it also rings again on a phone still locked after a reboot. Each cut is put in the
 * event log ([DiagEvents.ALARM_CUT]): 알람 점검 shows it, and one that could not ring again is among '놓쳤을 수 있는 알람'.
 */
object RingKeeper {
    const val ACTION = "com.bamnat.app.action.RING_KEEPER"
    /** The keeping alarm goes off this long after it was last pushed: the gap a cut ring is silent, at most. */
    const val KEEP_AHEAD_MILLIS = 30_000L
    /** How often a ring that goes on pushes it back. */
    const val PUSH_EVERY_MILLIS = 10_000L
    /** A ring cut more than this many times is not rung again: something kills the app as it rings (the notice stays). */
    const val MAX_RESUMES = 3
    private const val REQ = 1006
    private const val PREFS = "ring_keeper"
    private const val K_RING = "ring"
    private const val K_SINCE = "since"
    private const val K_PUSHED = "pushed"
    private const val K_RESUMES = "resumes"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What was ringing when the app went away, as [kept] reads it. */
    data class Kept(val ring: RingRequest, val since: Long, val pushedAt: Long, val resumes: Int)

    fun kept(context: Context): Kept? {
        val p = prefs(context)
        val ring = RingRequest.decode(p.getString(K_RING, null)) ?: return null
        return Kept(ring, p.getLong(K_SINCE, 0L), p.getLong(K_PUSHED, 0L), p.getInt(K_RESUMES, 0))
    }

    /**
     * [ring] began ringing (or rings again after a cut). Returns when it first rang: [Today.millis] for a new ring, the kept time
     * for the same ring rung again.
     */
    fun started(context: Context, ring: RingRequest): Long {
        val now = Today.millis()
        val old = kept(context)
        val same = old != null && old.ring.encode() == ring.encode()
        val since = if (same) old!!.since.takeIf { it in 1..now } ?: now else now
        prefs(context).edit().putString(K_RING, ring.encode()).putLong(K_SINCE, since)
            .putInt(K_RESUMES, if (same) old!!.resumes else 0).putLong(K_PUSHED, now).commit()
        push(context, now)
        return since
    }

    /** Still ringing: the keeping alarm moves on. */
    fun push(context: Context, now: Long = Today.millis()) {
        if (prefs(context).getString(K_RING, null) == null) return
        prefs(context).edit().putLong(K_PUSHED, now).apply()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { AlarmScheduler.setAlarm(context, am, now + KEEP_AHEAD_MILLIS, intent(context, create = true)!!) }
    }

    /** The ring ended as it should (끄기, 다시 울림, 자동으로 끄기, 오늘 모두 끄기): nothing to keep. */
    fun ended(context: Context) {
        prefs(context).edit().clear().commit()
        val pi = intent(context, create = false) ?: return
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pi) }
        pi.cancel()
    }

    private fun intent(context: Context, create: Boolean): PendingIntent? {
        val i = Intent(context, AlarmReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQ, i, flags)
    }

    /** What the keeping alarm does when it goes off. */
    enum class Outcome { RING_AGAIN, STILL_RINGING, NOTHING_KEPT, OVER, TOO_MANY }

    /**
     * The keeping alarm went off: the ring is rung again unless it still rings here (the app lived and only the push was late), its
     * 자동으로 끄기 time has passed, or it was cut too often. [ringMinutes] is 자동으로 끄기.
     */
    internal fun decide(kept: Kept?, ringingHere: Boolean, now: Long, ringMinutes: Int): Outcome = when {
        kept == null -> Outcome.NOTHING_KEPT
        ringingHere -> Outcome.STILL_RINGING
        now >= kept.since + ringMinutes.coerceIn(1, 60) * 60_000L -> Outcome.OVER
        kept.resumes >= MAX_RESUMES -> Outcome.TOO_MANY
        else -> Outcome.RING_AGAIN
    }

    /**
     * From [AlarmReceiver]: rings the kept alarm again, or says why not. [log] is the event log (null while the phone is still
     * locked after a reboot, where only the ring matters).
     */
    fun wentOff(context: Context, ringMinutes: Int, log: ((String, String) -> Unit)?, ring: (RingRequest) -> Unit) {
        val kept = kept(context)
        val now = Today.millis()
        val outcome = decide(kept, AlarmService.ringing.value != null, now, ringMinutes)
        if (kept != null && outcome != Outcome.STILL_RINGING) {
            // How long it was silent: from the last push (the app was still there) to now, less nothing: the keeper's own wait.
            val gap = ((now - kept.pushedAt) / 1000).coerceAtLeast(0)
            log?.invoke(DiagEvents.ALARM_CUT, EventBook.triggerDetail(kept.ring.triggerAtMillis,
                "gap_s=$gap resumed=${if (outcome == Outcome.RING_AGAIN) 1 else 0} why=${outcome.name.lowercase()}"))
        }
        when (outcome) {
            Outcome.RING_AGAIN -> {
                prefs(context).edit().putInt(K_RESUMES, kept!!.resumes + 1).commit()
                ring(kept.ring)
            }
            Outcome.STILL_RINGING -> push(context, now)
            Outcome.OVER, Outcome.TOO_MANY -> {
                // Not rung again: the ringing notice stays so the cut is seen, and the keeper is done.
                if (outcome == Outcome.TOO_MANY) runCatching { Notifications.postFallbackAlarm(context, kept!!.ring) }
                ended(context)
            }
            Outcome.NOTHING_KEPT -> Unit
        }
    }

    /**
     * At the app's start: a ring kept long past its keeping alarm (the app was stopped by force, which also takes its alarms) is put
     * in the log as cut and not rung again, so 알람 점검 can say so.
     */
    fun settle(context: Context, log: (String, String) -> Unit) {
        val kept = kept(context) ?: return
        if (AlarmService.ringing.value != null) return
        val now = Today.millis()
        if (now - kept.pushedAt < KEEP_AHEAD_MILLIS * 4) return
        log(DiagEvents.ALARM_CUT, EventBook.triggerDetail(kept.ring.triggerAtMillis,
            "gap_s=${(now - kept.pushedAt) / 1000} resumed=0 why=stopped"))
        ended(context)
    }
}
