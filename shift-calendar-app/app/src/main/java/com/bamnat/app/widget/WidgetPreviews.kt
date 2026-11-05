package com.bamnat.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.bamnat.core.time.Today

/**
 * 1.38 [EASE-29] (오늘근무 분석 R8·P11: 위젯 설정 미리보기가 견본 데이터라 놓기 전엔 내 위젯을 몰라요, 공개 관찰): the widgets
 * drawn with the user's own month, the same code that draws them on the home screen.
 *  - In the app, 설정 › 달력 › 위젯 shows them live as the look, see-through and memo rows change.
 *  - On Android 15 and later the home screen's widget list shows them too ([publish], once a day: the phone allows
 *    only a few such updates an hour), so the picker shows "내 근무" instead of the drawn sample.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
object WidgetPreviews {
    enum class Kind(val label: String, val size: DpSize) {
        TODAY("오늘·내일", DpSize(220.dp, 110.dp)),
        WEEK("이번 주", DpSize(320.dp, 110.dp)),
        MONEY("오늘 쓸 돈", DpSize(220.dp, 90.dp)),
    }

    /** The widget of [kind] as the user's home screen would show it now. */
    suspend fun views(context: Context, kind: Kind): RemoteViews {
        val app = context.applicationContext
        return when (kind) {
            Kind.MONEY -> {
                val line = runCatching { MoneyWidget.load(app) }.getOrNull() ?: MoneyWidget.Line(null, 0, false)
                GlanceRemoteViews().compose(app, kind.size) {
                    WidgetLookTheme {
                        Box(GlanceModifier.fillMaxSize().background(widgetBg()).cornerRadius(widgetCorner()).padding(12.dp),
                            contentAlignment = Alignment.CenterStart) { MoneyContent(line) }
                    }
                }.remoteViews
            }
            else -> {
                val data = WidgetData.load(app)
                GlanceRemoteViews().compose(app, kind.size) {
                    WidgetLookTheme { WidgetFrame { if (kind == Kind.TODAY) TodayContent(data) else WeekContent(data) } }
                }.remoteViews
            }
        }
    }

    /**
     * Puts today's widgets into the home screen's widget list (Android 15+), at most once a day, and never from a phone
     * with an empty calendar (the drawn sample says more then).
     */
    suspend fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = Today.date().toString()
        if (prefs.getString(K_DAY, null) == day) return
        val empty = runCatching { WidgetData.load(app).let { d -> !d.hasPattern && (0L..6L).none { d.resolver.resolve(d.today.plusDays(it)).shiftType != null } } }.getOrDefault(true)
        if (empty) return
        val manager = AppWidgetManager.getInstance(app) ?: return
        val done = listOf(
            Kind.TODAY to TodayWidgetReceiver::class.java,
            Kind.WEEK to WeekWidgetReceiver::class.java,
            Kind.MONEY to MoneyWidgetReceiver::class.java,
        ).count { (kind, receiver) ->
            runCatching {
                manager.setWidgetPreview(ComponentName(app, receiver), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views(app, kind))
            }.getOrDefault(false)
        }
        // Refused (the hourly limit): tried again on the next refresh instead of tomorrow.
        if (done > 0) prefs.edit { putString(K_DAY, day) }
    }

    private const val PREFS = "widget_previews"
    private const val K_DAY = "published_on"
}
