package com.bamnat.app.ui.sleep

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.db.SleepEventEntity
import com.bamnat.app.sleep.SleepRecordService
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.sleep.ClipPicker
import com.bamnat.core.sleep.SoundEvent
import com.bamnat.core.sleep.SoundKind
import com.bamnat.core.time.Today
import java.time.Instant
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-06][SLEEP-07] 자는 동안 소리 재기: 측정 시작 before bed, and in the morning what was heard, with three sounds to
 * listen to first (different kinds, the loudest of each). Kept clips stay past the 7 days.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun SleepNightCard(
    last: SleepRecordService.Companion.LastNight?,
    events: List<SleepEventEntity>,
    onKeep: (SleepEventEntity, Boolean) -> Unit,
    smartWake: Int,
    onSmartWake: (Int) -> Unit,
) {
    val context = LocalContext.current
    val recording by SleepRecordService.recording.collectAsStateWithLifecycle()
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        if (ok) SleepRecordService.start(context)
    }
    SectionCard(title = "자는 동안 소리") {
        val r = recording
        if (r != null) {
            Text("${Fmt.time(local(r.startedAt).toLocalTime())}부터 재는 중 · 들린 소리 ${r.events}번", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { SleepRecordService.stop(context) }, modifier = Modifier.padding(top = 8.dp)) { Text("측정 끝") }
        } else {
            Text("머리맡에 두고 ‘측정 시작’을 누르면 코골이·잠꼬대·기침·이갈이를 시간과 함께 남겨요. 휴대폰 안에서만 듣고, 짧은 소리는 7일 뒤 지워요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (refused) Text("마이크를 허용해야 잴 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                if (SleepRecordService.permitted(context)) SleepRecordService.start(context) else ask.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.padding(top = 8.dp)) { Text("측정 시작") }
        }
        // 1.38 [SLEEP-08] 깨우는 구간: only earlier than the alarm, and only while measuring.
        Text("깨우는 구간 (재는 동안, 알람보다 일찍 얕은 잠일 때)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.core.sleep.SmartWakeRule.CHOICES_MINUTES.forEach { m ->
                com.bamnat.app.ui.components.FilterChip(selected = smartWake == m, onClick = { onSmartWake(m) },
                    label = { Text(com.bamnat.core.sleep.SmartWakeRule.label(m)) })
            }
        }
        if (smartWake > 0) Text("근무 알람은 정시에 그대로 울려요. 구간 안에 뒤척이면 그때 먼저 깨우고, 정시 알람은 꺼요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r == null && last != null && last.end.isAfter(Today.dateTime().minusHours(18))) {
            Text("지난밤 ${Fmt.time(last.start.toLocalTime())}–${Fmt.time(last.end.toLocalTime())} · ${last.line}" +
                if (last.battery >= 0) " · 배터리 ${last.battery}%" else "",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            val withClip = events.filter { it.file != null }
            val listen = ClipPicker.listen(withClip.map { it.toEvent() }).mapNotNull { e -> withClip.firstOrNull { it.startMillis == e.startMillis } }
            if (listen.isNotEmpty()) {
                Text("들어 볼 소리", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                listen.forEach { e -> ClipRow(e, onKeep) }
            }
        }
    }
}

@Composable
private fun ClipRow(e: SleepEventEntity, onKeep: (SleepEventEntity, Boolean) -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(e.id) { onDispose { player?.release(); player = null } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${Fmt.time(local(e.startMillis).toLocalTime())} ${SoundKind.of(e.kind).label}", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        TextButton(onClick = {
            player?.release()
            player = if (player != null) null else runCatching {
                MediaPlayer().apply {
                    setDataSource(SleepRecordService.clip(context, e.file!!).path)
                    setOnCompletionListener { it.release(); player = null }
                    prepare(); start()
                }
            }.getOrNull()
        }) { Text(if (player != null) "멈춤" else "듣기") }
        TextButton(onClick = { onKeep(e, !e.keep) }) { Text(if (e.keep) "보관됨" else "보관") }
    }
}

private fun SleepEventEntity.toEvent() = SoundEvent(startMillis, endMillis, SoundKind.of(kind), level)

private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Today.zone())
