package com.bamnat.app.ui.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch

/** [EASE-17] The row title settings search points at. */
const val SKIP_ONCE_ROW = "이번만 끄기"
/** [ALRM-27] */
const val GUARD_ROW = "알람 지킴이"

/**
 * [EASE-17][EASE-21] 1.38 (오늘근무 분석 P5 '믿음'): the top of 설정 › 알람. The next alarm in words and [ALRM-26] the next three,
 * each with 이번만 끄기 for that one alone (the others still ring, 다시 켜기 brings it back), and the alarm checks in a line: all ready, or what to fix with
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
    val ahead by c.alarmScheduler.ahead.collectAsStateWithLifecycle()
    val skips = remember(refresh, upcoming, ahead) { AlarmSoon.skippedAll(context).toSet() }
    val bad = checks.filter { it.ok == false }
    val today = Today.date()
    SectionCard(title = "다음 알람", modifier = Modifier.focusMark(SKIP_ONCE_ROW)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nextLine(settings.alarmsEnabled, upcoming, today, settings.workStyle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // [ALRM-26] 1.38: the next few, each with its own 이번만 끄기 / 다시 켜기 (not only the very next one).
            // 2.0.0 [RVI-65] (디자인 품질 2·12) Three filled '이번만 끄기' stood in a column, and at 200% they left the time a word
            // per line ('내일 / 06:30 / 주간 / 근무'): each alarm is a row with a switch, as an alarm list is, the whole row a target.
            if (settings.alarmsEnabled) for (r in ahead) {
                val off = r.triggerAtMillis in skips
                val at = AlarmScheduler.millisToLocal(r.triggerAtMillis)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = !off,
                        enabled = off || skips.size < AlarmSoon.MAX_SKIPS,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { on ->
                            if (on) AlarmSoon.unskip(context, r.triggerAtMillis) else AlarmSoon.skip(context, r.triggerAtMillis)
                            refresh++
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("${whenText(at.toLocalDate(), today)} ${Fmt.time(at.toLocalTime())}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (off) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (off) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        Text(if (off) "이번만 꺼 뒀어요" else r.title(settings.workStyle), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = !off, onCheckedChange = null, enabled = off || skips.size < AlarmSoon.MAX_SKIPS)
                }
            }
            if (settings.alarmsEnabled && ahead.isNotEmpty()) {
                Text("$SKIP_ONCE_ROW · 끈 알람만 한 번 쉬고 나머지는 그대로 울려요", style = MaterialTheme.typography.bodySmall,
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
            // 2.0.0 [RVI-91] (검토 I03c-02) How the week went, and the silent daytime rehearsals: trust shown, not only promised.
            val trust by androidx.compose.runtime.produceState<String?>(null, refresh) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.bamnat.core.diag.EventBook.trustLine(c.telemetry.log.read(), Today.millis()) }.getOrNull()
                }
            }
            trust?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp),
                    color = if ("늦게" in line || "기록 없음" in line || "오지 않았어요" in line) com.bamnat.app.ui.theme.Tone.Caution.color()
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 2.0.0 [RVI-65] (디자인 품질 2·4, the settings top card [RVI-38] alike) The most serious one with its button; the rest are
            // counted and wait in 알람 점검 below, which lists each with its own fix. Three paragraphs and three buttons read as a page.
            bad.firstOrNull()?.let { check ->
                Column(Modifier.padding(start = 28.dp)) {
                    Text(check.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(check.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { applyAlarmFix(c, context, check.fix, "alarm settings"); refresh++ },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text(check.fixLabel) }
                }
            }
            if (bad.size > 1) {
                Text("고칠 것이 ${bad.size - 1}가지 더 있어요 · 아래 알람 점검에서 모두 고칠 수 있어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
            }
            Text(CALL_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp))
        }
        // [ALRM-27] 알람 지킴이: the same checks a few hours before each alarm, said while there is time to fix them.
        // 2.0.0 [RVI-65] (디자인 품질 4) One line; what it looks at is in 알람 점검 below.
        SwitchRow(GUARD_ROW, "알람을 막는 것이 있으면 ${com.bamnat.app.alarm.AlarmGuard.LEAD_MILLIS / 3_600_000}시간 전에 미리 알려요 · 휴가 뒤 첫 알람은 전날 저녁에",
            settings.alarmGuard) { on ->
            c.settingsRepository.update { it.copy(alarmGuard = on) }
            c.appScope.launch { runCatching { c.rescheduleAlarms() } }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = { onOpenDiagnostics(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("알람 점검 · 시험 알람 울려 보기")
            }
        }
    }
}


/** [EASE-21] How the alarm meets a call ([com.bamnat.app.alarm.AlarmService.holdsForCall]). */
// 2.0.0 [RVI-65] (디자인 품질 4) One line, as the call wait now is ([RVI-36]: 끝나면 바로, 메신저 3분·전화 30분 한도).
internal const val CALL_LINE = "통화 중이면 끝나는 대로 울려요 · 메신저 3분, 전화 30분이 넘으면 그대로"

/** "내일 (11월 6일 목) 05:30 · 주간 근무 알람", or why there is none. */
internal fun nextLine(enabled: Boolean, next: UpcomingAlarm?, today: LocalDate, style: com.bamnat.core.work.WorkStyle? = null): String = when {
    !enabled -> "알람이 모두 꺼져 있어요"
    next == null -> "120일 안에 울릴 알람이 없어요"
    else -> {
        val at = next.plan.triggerAt
        val what = if (next.plan.shiftName.isBlank()) "이 날 알람" else com.bamnat.core.alarm.AlarmWords.alarm(next.plan.shiftName, style) // 1.39 [SHOP-13]
        "${whenText(at.toLocalDate(), today)} ${Fmt.time(at.toLocalTime())} · $what"
    }
}

/** "오늘", "내일 (11월 6일 목)", "11월 8일 (토)". */
internal fun whenText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    else -> Fmt.dayTitle(date)
}
