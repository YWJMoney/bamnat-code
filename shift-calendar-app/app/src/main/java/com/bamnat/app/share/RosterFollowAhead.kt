package com.bamnat.app.share

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bamnat.app.alarm.BootAlarms
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.appContainer
import com.bamnat.app.money.ReceiverSteps
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * 2.0.0 [RVJ-99] 검토 PAYU-15: a 리더's change made the night before reached a following member only at the next 6-hourly look
 * ([RosterFollowJob]), which a sleeping phone puts off further, so the old alarm could ring. While a link is followed the phone also
 * looks once [LEAD_MS] before its next work alarm, with a while-idle alarm (Android lets the app reach the network for a few seconds
 * then, even asleep). A change found there goes in and moves the alarm as any look does; its notice is silent, since the alarm is
 * what wakes the member ([RosterFollower.hush]).
 */
object RosterFollowAhead {
    const val ACTION = "com.bamnat.app.share.FOLLOW_AHEAD"
    private const val REQUEST = 3770
    /** 1시간 반 before the alarm: room for the look, and the new alarm when the change brings it nearer. */
    val LEAD_MS: Long = TimeUnit.MINUTES.toMillis(90)
    /** The look ends within this, inside the short window a while-idle alarm is given. */
    const val LOOK_MS = 9_000L

    /** When to look: [LEAD_MS] before the first work alarm whose look is still ahead (snoozes, 일어났나요?, 낮잠·메모 일정 left out). */
    internal fun at(upcoming: List<RingRequest>, now: Long): Long? =
        upcoming.filter { !it.isSnooze && !it.recheck && !it.nap && it.label == null }
            .map { it.triggerAtMillis - LEAD_MS }.filter { it > now + 60_000L }.minOrNull()

    private fun intent(context: Context) = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, RosterFollowAheadReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Plans the next look, or none when no link is followed or no work alarm is ahead. After each plan of the alarms and each look. */
    fun reschedule(context: Context, now: Long = Today.millis()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val c = context.appContainer
        val follows = c.shareApi.enabled && c.received.calendars.value.any { !it.gone && RosterFollower.followed(it) }
        val at = if (follows) at(BootAlarms.upcoming(context), now) else null
        if (at == null) am.cancel(intent(context)) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
    }
}

class RosterFollowAheadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RosterFollowAhead.ACTION) return
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                RosterFollower.hush(RosterFollowAhead.LOOK_MS * 2)
                withTimeoutOrNull(RosterFollowAhead.LOOK_MS) { runCatching { RosterFollower.checkAll(c) } }
            } finally {
                ReceiverSteps.thenFinish({ RosterFollowAhead.reschedule(context) }, pending::finish)
            }
        }
    }
}
