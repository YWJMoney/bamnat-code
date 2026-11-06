package com.bamnat.app.ui.sleep

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.sleep.SleepAutoWatch
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.settings.SwitchRow
import com.bamnat.core.sleep.SleepAuto
import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepInsights
import com.bamnat.core.sleep.SleepMood
import com.bamnat.core.sleep.SleepTag
import com.bamnat.core.time.Today
import com.bamnat.app.ui.Fmt

/**
 * 1.38 [SLEEP-01] "자동으로 잡은 잠, 맞나요?": the same question as the quiet notice, so a swiped notice loses nothing.
 * 맞아요 puts it in the log as 자동, 고쳐서 넣기 opens the edit with these times, 아니에요 never asks about it again.
 */
@Composable
internal fun AutoOfferCard(offer: SleepAuto.Segment, onYes: () -> Unit, onFix: () -> Unit, onNo: () -> Unit) {
    SectionCard(title = "자동으로 잡은 잠, 맞나요?") {
        Text(SleepAuto.line(offer, Today.dateTime()), style = MaterialTheme.typography.titleMedium)
        Text("휴대폰이 어둡고 가만히 있던 때와 끈 알람으로 잡았어요. 소리는 듣지 않아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onYes) { Text("맞아요") }
            OutlinedButton(onClick = onFix) { Text("고쳐서 넣기") }
            TextButton(onClick = onNo) { Text("아니에요") }
        }
    }
}

/**
 * 1.38 [SLEEP-03] (디자인 스레드의 아침 카드) 아침 한 장: one tap for how the user woke, a few habit tags, and it folds into one line.
 * No score: the sleep against the plan and the morning feeling are what the user sees.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MorningCard(
    sleep: SleepEntry,
    check: SleepCheck?,
    onAnswer: (SleepMood?, Set<SleepTag>) -> Unit,
    onUndo: () -> Unit,
) {
    var reopened by rememberSaveable(sleep.id, check?.id) { mutableStateOf(false) }
    val title = "${Fmt.time(sleep.start.toLocalTime())}–${Fmt.time(sleep.end!!.toLocalTime())} · " +
        SleepInsights.hours(sleep.minutes ?: 0)
    if (check != null && !reopened) {
        // Answered: one line, with a way to change it.
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 아침 · ${com.bamnat.core.sleep.SleepMorning.summary(check)}", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { reopened = true }) { Text("고치기") }
            }
        }
        return
    }
    var mood by remember(check) { mutableStateOf(check?.mood) }
    var tags by remember(check) { mutableStateOf(check?.tags.orEmpty()) }
    SectionCard(title = "잘 잤어요?") {
        Text(title + if (sleep.auto) " · 자동" else "", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SleepMood.entries.forEach { m ->
                FilterChip(selected = mood == m, onClick = {
                    mood = m
                    // One tap is an answer; tags can follow.
                    onAnswer(m, tags)
                }, label = { Text(m.label) })
            }
        }
        Text("어제 있었던 일 (고르면 원인 찾기에 써요)", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SleepTag.entries.forEach { t ->
                FilterChip(selected = t in tags, onClick = {
                    tags = if (t in tags) tags - t else tags + t
                    if (mood != null || check != null) onAnswer(mood, tags)
                }, label = { Text(t.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (check != null) TextButton(onClick = { onUndo(); reopened = false }) { Text("답 지우기") }
            if (mood == null && check == null) TextButton(onClick = { onAnswer(null, tags) }) { Text("건너뛰기") }
            else TextButton(onClick = { reopened = false }) { Text("접기") }
        }
    }
}

/**
 * 1.38 [SLEEP-01] 잠 자동으로 잡기 on or off, asking for the activity recognition permission when it is turned on.
 * Shown only while the `sleep.auto` flag is open (처리방침 새 판 뒤).
 */
@Composable
internal fun SleepAutoSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(SleepAutoWatch.permitted(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
        if (ok) onChange(true)
    }
    val warning = when {
        refused -> "‘신체 활동’ 권한을 허용해야 켜져요"
        on && !granted -> "‘신체 활동’ 권한이 꺼져 있어 지금은 잡지 못해요"
        else -> null
    }
    SwitchRow(SLEEP_AUTO_ROW, "잠든 때를 휴대폰이 알아채면(어둡고 가만히 있을 때, 소리는 안 들어요) 아침에 ‘이 잠 맞나요?’ 한 번 물어요. " +
        "두 번 맞으면 그다음부터는 묻지 않고 넣어요. 휴대폰 밖으로 나가지 않아요",
        on && granted, warning = warning, fixLabel = "허용",
        onFix = { com.bamnat.app.ui.components.SystemSettings.app(context) }) { want ->
        if (want && Build.VERSION.SDK_INT >= 29 && !granted) ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        else onChange(want)
    }
}

internal const val SLEEP_AUTO_ROW = "잠 자동으로 잡기"
