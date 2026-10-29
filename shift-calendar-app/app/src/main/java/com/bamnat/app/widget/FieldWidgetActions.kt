package com.bamnat.app.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bamnat.app.appContainer
import com.bamnat.app.data.GuideStore
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * G 1.32 [WTYP-23] 조사 대조 W29: 배달·대리 "+1건" from the today widget. One more 건 on today's job for the platform used most
 * (its amount is filled at 하루 마감 in the app), said back in a short toast. Only on 내 달력 in 플랫폼형.
 */
class PlatformPlusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.appContainer
        val text = runCatching {
            val rules = c.calendars.get(PersonalCalendar.MAIN)?.field
            if (c.settingsRepository.current.workStyle != WorkStyle.FIELD || rules?.platformMode != true) return@runCatching null
            val money = c.calendars.money(PersonalCalendar.MAIN)
            val today = Today.date()
            val jobs = money.book.first().jobs
            val platform = PlatformWork.usual(today, jobs) ?: return@runCatching "앱에서 날짜를 눌러 플랫폼을 먼저 골라 주세요"
            val saved = money.save(PlatformWork.plusOne(today, platform, jobs))
            "$platform 오늘 ${PlatformWork.count(saved)}건"
        }.getOrNull()
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, text ?: "지금은 적을 수 없어요. 앱에서 적어 주세요", Toast.LENGTH_SHORT).show()
        }
        WidgetRefresher.refresh(context)
    }
}

/**
 * G 1.32 [WTYP-23] 조사 대조 W38: 현장 "오늘 1공" from the today widget, the first 공수 chip at today's 일당 for the last 업체.
 * A day already written is left as it is; without an 일당 yet the button opens today instead (see [FieldWidgetButtons]).
 */
class DayRateTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.appContainer
        val text = runCatching {
            val rules = c.calendars.get(PersonalCalendar.MAIN)?.field
            if (c.settingsRepository.current.workStyle != WorkStyle.FIELD || rules?.dayRateMode != true) return@runCatching null
            val money = c.calendars.money(PersonalCalendar.MAIN)
            val today = Today.date()
            val jobs = money.book.first().jobs
            DayRate.on(today, jobs)?.let { j -> return@runCatching "오늘은 벌써 ${DayRate.label(j.units ?: 0.0)}로 적었어요" }
            val u = rules.chips.first()
            money.save(DayRate.job(today, u, rules.rateOn(today), rules.lastClient))
            "오늘 ${DayRate.label(u)} 적었어요"
        }.getOrNull()
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, text ?: "지금은 적을 수 없어요. 앱에서 적어 주세요", Toast.LENGTH_SHORT).show()
        }
        WidgetRefresher.refresh(context)
    }
}

/** G 1.32 [WTYP-23] The +1건 and 오늘 1공 buttons can be turned off in 기능 켜고 끄기. */
@Composable
private fun fieldButtonsOff(): Boolean = GuideStore.get(LocalContext.current).isOff("widget.fieldActions", PersonalCalendar.MAIN)

/** G 1.32 [WTYP-23] Under a field worker's jobs: 오늘 n건 · 이번 주 n건 with +1건, or today's 공수 with 오늘 1공. */
@Composable
internal fun FieldWidgetButtons(data: WidgetData) {
    val rules = data.fieldRules ?: return
    val context = LocalContext.current
    when {
        rules.platformMode -> data.platformDay?.let { p ->
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Text("오늘 ${p.today}건 · 이번 주 ${p.week}건", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight())
                if (!fieldButtonsOff()) {
                    Spacer(GlanceModifier.width(6.dp))
                    if (p.platform != null) WidgetButton("+1건", actionRunCallback<PlatformPlusAction>())
                    else WidgetButton("적기", openDay(context, data.today))
                }
            }
        }
        rules.dayRateMode && !fieldButtonsOff() -> {
            val done = DayRate.on(data.today, data.jobs)
            Spacer(GlanceModifier.height(6.dp))
            when {
                done != null -> Text("오늘 ${DayRate.label(done.units ?: 0.0)} 적었어요",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                rules.rateOn(data.today) > 0 -> WidgetButton("오늘 ${DayRate.label(rules.chips.first())}", actionRunCallback<DayRateTodayAction>())
                else -> WidgetButton("오늘 공수 적기", openDay(context, data.today))
            }
        }
    }
}

/** G 1.32 [WTYP-23] 공무원 (or a 시간외 상한): "10월 시간외 41시간 / 57시간" with a bar, red past the cap. */
@Composable
internal fun OvertimeWidgetLine(data: WidgetData) {
    val o = data.overtime ?: return
    Column(GlanceModifier.fillMaxWidth()) {
        Spacer(GlanceModifier.height(6.dp))
        Text("${data.today.monthValue}월 시간외 ${HoursFormat.korean(o.minutes)} / ${o.capHours}시간",
            style = TextStyle(color = if (o.over) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp,
                fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(progress = o.progress, modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = if (o.over) GlanceTheme.colors.error else GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant)
    }
}

@Composable
private fun WidgetButton(label: String, action: Action) {
    Text(
        label,
        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clickable(action),
    )
}
