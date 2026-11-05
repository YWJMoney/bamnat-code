package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.alarm.AlarmSoon
import com.bamnat.app.alarm.UpcomingAlarm
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.diag.alarmChecks
import com.bamnat.app.ui.diag.applyAlarmFix
import com.bamnat.core.time.Today
import java.time.LocalDate

/** [EASE-17] The row title settings search points at. */
const val SKIP_ONCE_ROW = "이번만 끄기"

/**
 * [EASE-17][EASE-21] 1.38 (오늘근무 분석 P5 '믿음'): the top of 설정 › 알람. The next alarm in words, 이번만 끄기 for that one
 * alone (the next after it still rings, 다시 켜기 brings it back), and the alarm checks in a line: all ready, or what to fix with
 * its button right here. How a call is handled is said too, and 알람 점검 is one tap away for the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmTrustCard(settings: AppSettings, onOpenDiagnostics: (Boolean) -> Unit) {
    val context = LocalContext.current
    val c = context.appContainer
    val upcoming by c.alarmScheduler.upcoming.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    // Back from the phone's settings: read the checks and the skipped alarm again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val checks = remember(refresh, settings.alarmsEnabled, upcoming) { runCatching { alarmChecks(c, context) }.getOrDefault(emptyList()) }
    val skipped = remember(refresh, upcoming) { AlarmSoon.skipped(context) }
    val bad = checks.filter { it.ok == false }
    SectionCard(title = "다음 알람", modifier = Modifier.focusMark(SKIP_ONCE_ROW)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(nextLine(settings.alarmsEnabled, upcoming, Today.date()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (skipped != null) {
                Text("${whenText(AlarmScheduler.millisToLocal(skipped).toLocalDate(), Today.date())} ${Fmt.time(AlarmScheduler.millisToLocal(skipped).toLocalTime())} 알람은 이번만 꺼 뒀어요",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val next = upcoming
                // One alarm is put aside at a time: the one already off comes back first.
                if (skipped == null && settings.alarmsEnabled && next != null) {
                    FilledTonalButton(onClick = { AlarmSoon.skip(context, next.triggerAtMillis); refresh++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("$SKIP_ONCE_ROW · ${Fmt.time(next.plan.triggerAt.toLocalTime())}")
                    }
                }
                if (skipped != null) {
                    OutlinedButton(onClick = { AlarmSoon.unskip(context); refresh++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("다시 켜기 · ${Fmt.time(AlarmScheduler.millisToLocal(skipped).toLocalTime())}")
                    }
                }
            }
            if (skipped == null && settings.alarmsEnabled && upcoming != null) {
                Text("이 알람 하나만 쉬고, 다음 알람부터는 그대로 울려요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (bad.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                    tint = if (bad.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp).size(20.dp))
                Text(
                    if (bad.isEmpty()) "알람이 울릴 준비가 다 됐어요 · ${checks.size}가지 확인" else "알람이 안 울리거나 늦을 수 있어요 · 고칠 것 ${bad.size}가지",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (bad.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
            for (check in bad.take(MAX_SHOWN)) {
                Column(Modifier.padding(start = 28.dp)) {
                    Text(check.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(check.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { applyAlarmFix(c, context, check.fix, "alarm settings"); refresh++ },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text(check.fixLabel) }
                }
            }
            if (bad.size > MAX_SHOWN) {
                Text("그 밖에 ${bad.size - MAX_SHOWN}가지는 알람 점검에서 볼 수 있어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
            }
            Text(CALL_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp))
            TextButton(onClick = { onOpenDiagnostics(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("알람 점검 · 시험 알람 울려 보기")
            }
        }
    }
}

private const val MAX_SHOWN = 3

/** [EASE-21] How the alarm meets a call ([com.bamnat.app.alarm.AlarmService.holdsForCall]). */
internal const val CALL_LINE = "통화 중에 울릴 때는 소리를 멈췄다가 통화가 끝나면 다시 울려요. 메신저 통화는 3분, 전화는 30분이 넘으면 그대로 울려요"

/** "내일 (11월 6일 목) 05:30 · 주간 근무 알람", or why there is none. */
internal fun nextLine(enabled: Boolean, next: UpcomingAlarm?, today: LocalDate): String = when {
    !enabled -> "알람이 모두 꺼져 있어요"
    next == null -> "120일 안에 울릴 알람이 없어요"
    else -> {
        val at = next.plan.triggerAt
        val what = if (next.plan.shiftName.isBlank()) "이 날 알람" else "${next.plan.shiftName} 근무 알람"
        "${whenText(at.toLocalDate(), today)} ${Fmt.time(at.toLocalTime())} · $what"
    }
}

/** "오늘", "내일 (11월 6일 목)", "11월 8일 (토)". */
internal fun whenText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    else -> Fmt.dayTitle(date)
}
