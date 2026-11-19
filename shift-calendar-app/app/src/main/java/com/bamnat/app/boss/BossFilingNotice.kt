package com.bamnat.app.boss

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.money.ReceiverSteps
import com.bamnat.app.ui.work.Filed
import com.bamnat.core.boss.BossFilings
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-96] 검토 U05c-05·I05ca-03 (사장님이 신고 기한을 놓치면 가산세): the 다가올 신고·지급 of the 급여 대장 said once,
 * [BossFilings.REMIND_DAYS] days before their day (moved off a 휴일 already), at 9시: 급여 지급, 원천세 신고·납부, 4대보험 취득·상실,
 * 지급명세서. A line ticked 했어요 or said before is not said again. Only on a phone with a 급여 대장 (사장님 급여 on and a 사업장 made);
 * on by default, off in 설정 › 알림과 잠 or on the 신고·지급 card itself. Inexact, like the other daily notices. The tap opens the 급여
 * 대장 on its 다가올 신고·지급.
 */
object BossFilingNotice {
    private const val REQUEST = 3760
    private const val REQUEST_OPEN = 3761
    const val ID = 7660
    private const val ACTION_FIRE = "com.bamnat.app.BOSS_FILING"
    /** What the 관리 화면 opens on from the notice ([com.bamnat.app.imports.SharedToManage.openLedger]). */
    const val OPENS = "FILINGS"
    private val AT: LocalTime = LocalTime.of(9, 0)

    /** The setting is on, 사장님 급여 is on and a 사업장 is made. */
    private suspend fun wanted(c: AppContainer): Boolean =
        c.settingsRepository.current.bossFilingNotice && c.flags.on(FlagCatalog.BOSS_PAYROLL) && c.phaseThree.workplaces.first().isNotEmpty()

    /** The next 9시 after [now] while [wanted], else nothing. */
    suspend fun reschedule(context: Context, now: LocalDateTime = Today.dateTime()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!runCatching { wanted(context.appContainer) }.getOrDefault(false)) {
            am.cancel(pending(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(now).atZone(Today.zone()).toInstant().toEpochMilli(), pending(context))
    }

    /** Today's 9시 while it is before, else tomorrow's. */
    internal fun next(now: LocalDateTime): LocalDateTime = now.toLocalDate().atTime(AT).let { if (it.isAfter(now)) it else it.plusDays(1) }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, BossFilingReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** [RVJ-100] A 가게's own request code for its notice's open, so two 가게 notices keep their own 대장. */
    internal fun openRequest(placeId: String) = REQUEST_OPEN * 1000 + Math.floorMod(placeId.hashCode(), 1000)

    /** [RVJ-100] The notice's payload read back: the 가게 it is for, or null when it is not this notice's. */
    fun placeOf(payload: String?): String? = payload?.takeIf { it == OPENS || it.startsWith("$OPENS@") }?.substringAfter('@', "")

    /** The 9시 look: [tell] while [wanted]. */
    internal suspend fun post(context: Context): Int = if (!wanted(context.appContainer)) 0 else tell(context)

    /** Says each 사업장's lines due within [BossFilings.REMIND_DAYS] days, once each; returns how many were said. */
    internal suspend fun tell(context: Context): Int {
        val c = context.appContainer
        val store = c.phaseThree
        val today = Today.date()
        val done = Filed.all(context)
        var said = 0
        store.workplaces.first().forEach { place ->
            val staff = store.staff(place.id).first()
            if (staff.isEmpty()) return@forEach
            // The months [BossFilings.upcoming] reads, each worked out once (the same window as the card's).
            val first = YearMonth.from(today).minusMonths(3)
            val ledgers = (0..4).associate { i -> first.plusMonths(i.toLong()).let { m -> m to store.ledgerOf(place, m) } }
            val dues = BossFilings.upcoming(place, staff, today) { m -> ledgers[m] ?: com.bamnat.core.boss.PayrollLedger.month(place, staff, m, emptyList()) }
            val say = BossFilings.toRemind(dues, done, Filed.told(context), today)
            if (say.isEmpty()) return@forEach
            val title = BossFilings.remindTitle(place.name, say, today)
            val lines = say.map(BossFilings::remindLine)
            val n = NotificationCompat.Builder(context, Notifications.FILING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(title)
                .setContentText(lines.joinToString(" · "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n") + "\n'했어요'를 누른 것은 다시 알리지 않아요. 설정 › 알림과 잠 › $ROW에서 끌 수 있어요."))
                // [RVJ-100] 검토 T05-05: each 가게's notice opens that 가게's 대장 (its own request, so one does not take the other's).
                .setContentIntent(Notifications.open(context, NoticeLinks.Target.MANAGE, openRequest(place.id), payload = "$OPENS@${place.id}"))
                .setAutoCancel(true)
                .build()
            Notifications.post(context, ID, n, tag = place.id)
            Filed.tell(context, say.map { it.key })
            said += say.size
        }
        return said
    }

    /** Its row's name (설정 › 알림과 잠 · 알림 한눈에, and the card's line). */
    const val ROW = "신고·지급 기한 알림"

    internal const val FIRE = ACTION_FIRE
}

class BossFilingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                if (intent.action == BossFilingNotice.FIRE) runCatching { BossFilingNotice.post(context) }
            } finally {
                ReceiverSteps.thenFinish({ BossFilingNotice.reschedule(context, Today.dateTime().plusMinutes(1)) }, pending::finish)
            }
        }
    }
}
