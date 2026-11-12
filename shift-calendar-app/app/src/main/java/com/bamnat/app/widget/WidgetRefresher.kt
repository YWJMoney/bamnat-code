package com.bamnat.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * Redraws every widget and keeps a wake-up for the next midnight so the date moves on [REL-03], or
 * sooner when the line of 오늘의 흐름 changes before then [SLP-14], a shift starts or ends [WIDG-01], or a
 * countdown on the home screen is due to move on [WIDG-05].
 */
object WidgetRefresher {
    /**
     * Redraws every placed widget. 2.0.0 [RVI-37] (도약 7) [why] and how long it took go to logcat (tag `BamnatWidget`), so the PC
     * can time a shift change or midnight to the home screen.
     */
    suspend fun refresh(context: Context, why: String = "change") {
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            draw(context)
        } finally {
            android.util.Log.i(TAG, "widgets drawn ($why) in ${android.os.SystemClock.elapsedRealtime() - started} ms for ${Today.date()}")
        }
    }

    private const val TAG = "BamnatWidget"

    private suspend fun draw(context: Context) {
        val app = context.applicationContext
        // 2.0.0 [RVI-05] Something changed: the widgets drawn now share one new reading.
        WidgetData.invalidate()
        // [FEAT-18] The lock-screen 오늘 근무 notice moves on with the same wake-up as the widgets.
        val notice = runCatching { com.bamnat.app.alarm.TodayShiftNotice.update(app) }.getOrDefault(false)
        // [STAB-01] Every edit asks for this; with no widget on the home screen there is nothing to draw or wake for.
        val placed = runCatching {
            val manager = GlanceAppWidgetManager(app)
            listOf(TodayWidget::class.java, WeekWidget::class.java, MonthWidget::class.java, TeamsWidget::class.java, CountdownWidget::class.java,
                MoneyWidget::class.java, TaskWidget::class.java)
                .any { manager.getGlanceIds(it).isNotEmpty() }
        }.getOrDefault(true)
        // 1.38 [EASE-29] The widget list shows the user's own widgets (Android 15+, once a day), placed or not.
        runCatching { WidgetPreviews.publish(app) }
        if (!placed) {
            if (notice) scheduleNext(app, null)
            return
        }
        // [OBS-01] 기록 지점 6: one line a day, counted.
        runCatching { app.appContainer.journal.note(com.bamnat.core.journal.JournalKind.WIDGETS) }
        runCatching {
            // 1.39.1 [R391-03] Stamped, so a widget whose session is still running reads the data again.
            stampAndUpdate(app, TodayWidget())
            stampAndUpdate(app, WeekWidget())
            stampAndUpdate(app, MonthWidget())
            stampAndUpdate(app, TeamsWidget())
            stampAndUpdate(app, CountdownWidget())
            MoneyWidget.redraw(app)
            // 1.39 [TASK-07] 날이 바뀌면 오늘 할 일도 바뀌어요.
            TaskWidget.redraw(app)
        }
        val data = runCatching { WidgetData.load(app) }.getOrNull()
        // [WIDG-05] A countdown on the home screen moves on every 15 minutes; the others wait for the next change.
        val counting = runCatching { GlanceAppWidgetManager(app).getGlanceIds(CountdownWidget::class.java).isNotEmpty() }.getOrDefault(false)
        val tick = if (counting && data?.countdown != null) Today.dateTime().plusMinutes(COUNTDOWN_MINUTES) else null
        // [WIDG-01] I 리뷰 I20: also when a shift starts or ends, so "출근" turns into "퇴근" on time.
        scheduleNext(app, listOfNotNull(data?.flow?.changesAt, data?.restLine?.changesAt, data?.nextChange, tick).minOrNull())
    }

    /** 1.39.1 [R391-03] Each placed [widget] gets a new [WIDGET_DATA_STAMP] and is redrawn. */
    internal suspend fun stampAndUpdate(context: Context, widget: androidx.glance.appwidget.GlanceAppWidget) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass)
        val now = Today.millis()
        ids.forEach { id ->
            runCatching { androidx.glance.appwidget.state.updateAppWidgetState(context, id) { it[WIDGET_DATA_STAMP] = now } }
            widget.update(context, id)
        }
    }

    private const val COUNTDOWN_MINUTES = 15L

    /**
     * A non-waking alarm: widgets are only seen with the screen on, so it waits for the screen. 2.0.0 [RVI-37] (검토 PC-07: 자정 뒤
     * 3분 50초 동안 어제) It is exact where the app may set exact alarms (USE_EXACT_ALARM, an alarm app): an inexact one could come
     * up to an hour late, leaving yesterday on the home screen after midnight. Exact and non-waking, it is drawn at 00:00:05 when
     * the screen is on, or the moment it is turned on.
     */
    private fun scheduleNext(context: Context, flowChanges: LocalDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = Today.zone()
        val midnight = Today.date(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000
        val at = flowChanges?.atZone(zone)?.toInstant()?.toEpochMilli()?.plus(5_000)?.coerceAtMost(midnight) ?: midnight
        val pi = PendingIntent.getBroadcast(
            context, REQ_MIDNIGHT,
            Intent(context, MidnightReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        if (exact) runCatching { am.setExact(AlarmManager.RTC, at, pi) }.onFailure { am.setWindow(AlarmManager.RTC, at, 60_000L, pi) }
        else am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
    }

    private const val REQ_MIDNIGHT = 2001
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val pending = goAsync()
        container.appScope.launch {
            try {
                WidgetRefresher.refresh(context, "time")
                // 2.0.0 [RVI-08] (검토 ALM-09) Midnight and each change of 오늘의 흐름: the watch gets the new day's 오늘 알람,
                // 출근, week and 잘 시간 too (it was sent only when a shift, the next alarm or a sleep changed).
                runCatching { com.bamnat.app.watch.WatchSync.push(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
