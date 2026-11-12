package com.bamnat.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.bamnat.core.work.WorkStyle

/**
 * 2.0.0 [RVI-83] (검토 U03-08·T03-03) The phone's widget list shows only the widgets that fit the calendars' kinds of work: all
 * nine stood there whatever the work (오늘 모든 조 for an investor or a shop). A widget that does not fit is taken out of the list
 * by turning its receiver off, never while one is on the home screen (turning it off would take that one away too).
 */
object WidgetChoices {
    /** Whether the widget drawn by [receiver] fits a phone whose calendars are of [styles] (none chosen yet: all fit). */
    internal fun fits(receiver: Class<*>, styles: Set<WorkStyle>): Boolean = when (receiver) {
        // 조마다 오늘과 내일: only a 교대 근무표 has 조.
        TeamsWidgetReceiver::class.java -> styles.isEmpty() || WorkStyle.SHIFT in styles
        else -> true
    }

    /** The receivers [fits] can take out; the rest are always in the list. */
    internal val choosy: List<Class<*>> = listOf(TeamsWidgetReceiver::class.java)

    fun sync(context: Context, styles: Set<WorkStyle>) {
        val pm = context.packageManager
        val placed = AppWidgetManager.getInstance(context)
        for (cls in choosy) {
            val name = ComponentName(context, cls)
            val keep = fits(cls, styles) || runCatching { placed.getAppWidgetIds(name).isNotEmpty() }.getOrDefault(true)
            val state = if (keep) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(name) != state) pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
        }
    }
}
