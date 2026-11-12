package com.bamnat.app.ui.sleep

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
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
import kotlinx.coroutines.launch
import com.bamnat.app.appContainer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.db.SleepEventEntity
import com.bamnat.app.sleep.SleepRecordService
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.color
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
    work: List<TimelineSpan> = emptyList(),
    /** 1.39 [FLOW-12] The morning card shows the sounds to hear first; here they are not said twice. */
    listenShown: Boolean = false,
    onNight: () -> Unit = {},
) {
    val context = LocalContext.current
    val recording by SleepRecordService.recording.collectAsStateWithLifecycle()
    val saved by SleepRecordService.saved.collectAsStateWithLifecycle()
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refused = !ok
        if (ok) { SleepRecordService.start(context); onNight() }
    }
    SectionCard(title = "자는 동안 소리") {
        val r = recording
        if (r != null) {
            Text("${Fmt.time(local(r.startedAt).toLocalTime())}부터 재는 중 · 들린 소리 ${r.events}번", style = MaterialTheme.typography.bodyMedium)
            // PC 실사용(경쟁 앱이 짧은 측정을 끝에 와서야 거절): what a short night gives is said while measuring, not after.
            // 1.39 [SLEEP-06] When it ends by itself, said before the night rather than found in the morning.
            r.endsAt?.let { end ->
                Text("알람을 끄거나 깼어요를 누르면 끝나요. 늦어도 ${Fmt.time(local(end).toLocalTime())}에 저절로 멈춰요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("휴대폰이 15분 넘게 가만히 있어야 잠든 때를 알아요. 소리는 그 전부터 남겨요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { SleepRecordService.stop(context) }, modifier = Modifier.padding(top = 8.dp)) { Text("측정 끝") }
        } else {
            Text("머리맡에 두고 ‘측정 시작’을 누르면 코골이·잠꼬대·기침·이갈이를 시간과 함께 남겨요. 휴대폰 안에서만 듣고, 짧은 소리는 7일 뒤 지워요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (refused) Text("마이크를 허용해야 잴 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            ClearClipsRow()
            // 1.39 G5-B2: a night that never got saved is said, with what helps, instead of just missing in the morning.
            remember(r, saved) { SleepRecordService.cutOff(context) }?.let { from ->
                Text("${Fmt.time(from.toLocalTime())}에 시작한 측정이 저장되기 전에 멈췄어요. 휴대폰이 꺼졌거나 다시 켜졌을 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.Tone.Caution.color(), modifier = Modifier.padding(top = 4.dp))
            }
            // 1.39 G5-B3: a whole night of listening on a low battery, said before 측정 시작 rather than found at dawn.
            remember(r) { lowBattery(context) }?.let { pct ->
                Text("배터리가 $pct%예요. 밤새 재려면 충전기에 꽂아 두세요.",
                    style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.Tone.Caution.color(), modifier = Modifier.padding(top = 4.dp))
            }
            Button(onClick = {
                // 1.39 [FLOW-13] Measuring starts the night: the dark night screen opens.
                if (SleepRecordService.permitted(context)) { SleepRecordService.start(context); onNight() } else ask.launch(Manifest.permission.RECORD_AUDIO)
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
                (if (last.battery >= 0) " · 배터리 ${last.battery}%" else "") +
                // 1.39 G5-B2: ended by the phone turning off, not by the morning.
                (if (last.cut) " · 휴대폰이 꺼져 여기까지 쟀어요" else ""),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            // [SLEEP-21] The night on an hour scale, the work on the same axis; a dot plays its sound.
            var dotPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
            DisposableEffect(last.start) { onDispose { dotPlayer?.release() } }
            NightTimeline(last.start.minusHours(1), last.end.plusHours(1),
                work + TimelineSpan(last.start, last.end, work = false),
                events.filter { SoundKind.of(it.kind) != SoundKind.OTHER }.map { TimelineDot(local(it.startMillis), SoundKind.of(it.kind), it.id) }) { id ->
                events.firstOrNull { it.id == id }?.file?.let { f ->
                    dotPlayer?.release()
                    dotPlayer = runCatching { MediaPlayer().apply { setDataSource(SleepRecordService.clip(context, f).path); prepare(); start() } }.getOrNull()
                }
            }
            val withClip = events.filter { it.file != null }
            val listen = ClipPicker.listen(withClip.map { it.toEvent() }).mapNotNull { e -> withClip.firstOrNull { it.startMillis == e.startMillis } }
            if (listen.isNotEmpty() && !listenShown) {
                Text("들어 볼 소리", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                listen.forEach { e -> ClipRow(e, onKeep) }
            }
        }
    }
}

@Composable
internal fun ClipRow(e: SleepEventEntity, onKeep: (SleepEventEntity, Boolean) -> Unit) {
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

/** 1.39 G5-B3: the battery percent when it is under [LOW_BATTERY] and not charging; null otherwise. */
private fun lowBattery(context: android.content.Context): Int? {
    val b = context.getSystemService(android.os.BatteryManager::class.java) ?: return null
    if (b.isCharging) return null
    return b.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0 until LOW_BATTERY }
}

private const val LOW_BATTERY = 30

private fun SleepEventEntity.toEvent() = SoundEvent(startMillis, endMillis, SoundKind.of(kind), level)

private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Today.zone())

/** 1.39 [FLOW-12] The three sounds worth hearing first from [events] (different kinds, the loudest of each), for the morning card. */
internal fun listenFirst(events: List<SleepEventEntity>): List<SleepEventEntity> {
    val withClip = events.filter { it.file != null }
    return ClipPicker.listen(withClip.map { it.toEvent() }).mapNotNull { e -> withClip.firstOrNull { it.startMillis == e.startMillis } }
}

/**
 * [R200-02] (검토 ALM-03 넘긴 일) 소리 클립 지금 모두 지우기: the clips need not wait for their 7 days. Shown only when there are
 * some and not while a night is being measured (its file is still being written); asks first, since kept ones go too.
 */
@Composable
private fun ClearClipsRow() {
    val c = LocalContext.current.appContainer
    val count by remember { c.sleepRepository.clipCount }.collectAsStateWithLifecycle(0)
    var asking by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Int?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (count > 0) {
        androidx.compose.material3.TextButton(onClick = { asking = true }, modifier = Modifier.padding(top = 4.dp)) { Text("$CLEAR_CLIPS ($count)") }
    } else done?.let {
        Text("소리 클립을 모두 지웠어요. 들린 시각과 종류는 남아요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    if (asking) com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("소리 클립을 모두 지울까요?") },
        text = { Text("보관한 것까지 휴대폰에 남은 소리 $count 개를 지금 지워요. 들린 시각과 종류는 기록에 남아요. 지운 소리는 되돌릴 수 없어요.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                asking = false
                scope.launch { done = c.sleepRepository.clearClips(java.io.File(c.appContext.filesDir, SleepRecordService.CLIP_DIR)) }
            }) { Text("모두 지우기") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("그대로 두기") } },
    )
}

internal const val CLEAR_CLIPS = "소리 클립 지금 모두 지우기"
