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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bamnat.app.ui.theme.color
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
 *
 * 1.39 [FLOW-12] Its shape: the sounds worth hearing first (경쟁 앱 리뷰 "클립이 어디 있어요"), the hours slept as one number, five
 * faces in one row, and the habit chips in their meaning colours (도움 된 것 초록, 살펴볼 것 주황).
 * [FLOW-16] With the alarm's 오늘 카드 in it ([today]: "오늘 야간 19:00~07:00" · "이번 주 38시간 · 내일 쉬는 날"), so the
 * morning has one card; the alarm's notice opens this one (AfterAlarmCard's 잘 잤어요?).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MorningCard(
    sleep: SleepEntry,
    check: SleepCheck?,
    onAnswer: (SleepMood?, Set<SleepTag>) -> Unit,
    onUndo: () -> Unit,
    today: Pair<String, String>? = null,
    listen: List<com.bamnat.app.data.db.SleepEventEntity> = emptyList(),
    onKeep: (com.bamnat.app.data.db.SleepEventEntity, Boolean) -> Unit = { _, _ -> },
) {
    var reopened by rememberSaveable(sleep.id, check?.id) { mutableStateOf(false) }
    val times = "${Fmt.time(sleep.start.toLocalTime())}–${Fmt.time(sleep.end!!.toLocalTime())}" + if (sleep.auto) " · 자동" else ""
    val slept = SleepInsights.hours(sleep.minutes ?: 0)
    if (check != null && !reopened) {
        // Answered: one line, with a way to change it.
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘 아침 · $slept · ${com.bamnat.core.sleep.SleepMorning.summary(check)}", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { reopened = true }) { Text("고치기") }
            }
            today?.let { (a, b) -> TodayLine(a, b) }
        }
        return
    }
    var mood by remember(check) { mutableStateOf(check?.mood) }
    var tags by remember(check) { mutableStateOf(check?.tags.orEmpty()) }
    SectionCard(title = "잘 잤어요?") {
        // [FLOW-12] 들어 볼 소리 first, not at the bottom of another card.
        if (listen.isNotEmpty()) {
            Text("들어 볼 소리 ${listen.size}개", style = MaterialTheme.typography.labelLarge)
            listen.forEach { e -> ClipRow(e, onKeep) }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.bamnat.app.ui.components.HeroNumber(slept, caption = times, modifier = Modifier.weight(1f))
            // Design: 기지개 while asking, 기본 once a mood is picked.
            if (com.bamnat.app.ui.components.LocalShowKani.current) com.bamnat.app.ui.components.Kani(
                if (mood == null) com.bamnat.app.ui.components.KaniMood.Stretch else com.bamnat.app.ui.components.KaniMood.Basic, size = 48.dp)
        }
        today?.let { (a, b) -> TodayLine(a, b) }
        Text("일어났을 때 기분", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        MoodFaces(mood) { m ->
            mood = m
            // One tap is an answer; tags can follow.
            onAnswer(m, tags)
        }
        Text("어제 있었던 일 (고르면 원인 찾기에 써요)", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SleepTag.entries.forEach { t ->
                val tone = if (t.helps) com.bamnat.app.ui.theme.Tone.Good else com.bamnat.app.ui.theme.Tone.Caution
                FilterChip(selected = t in tags, onClick = {
                    tags = if (t in tags) tags - t else tags + t
                    if (mood != null || check != null) onAnswer(mood, tags)
                }, label = { Text(t.label) }, leadingIcon = {
                    val dot = tone.color()
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
                })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (check != null) TextButton(onClick = { onUndo(); reopened = false }) { Text("답 지우기") }
            if (mood == null && check == null) TextButton(onClick = { onAnswer(null, tags) }) { Text("건너뛰기") }
            else TextButton(onClick = { reopened = false }) { Text("접기") }
        }
    }
}

/** [FLOW-16] The alarm's 오늘 카드, two quiet lines in the morning card. */
@Composable
private fun TodayLine(title: String, text: String) {
    Text("$title · $text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
}

/**
 * 1.39 [FLOW-12] 기분 얼굴 다섯 한 줄: each face a 48dp target with its word under it, the picked one filled in the primary colour.
 * Read as "일어났을 때 기분, 개운, 선택됨".
 */
@Composable
private fun MoodFaces(picked: SleepMood?, onPick: (SleepMood) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        SleepMood.entries.forEach { m ->
            val on = picked == m
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onPick(m) })
                    .semantics(mergeDescendants = true) { contentDescription = "일어났을 때 기분, ${m.label}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(40.dp).background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(face(m), null, modifier = Modifier.size(28.dp),
                        tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(m.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun face(m: SleepMood): androidx.compose.ui.graphics.vector.ImageVector = when (m) {
    SleepMood.DRAINED -> androidx.compose.material.icons.Icons.Outlined.SentimentVeryDissatisfied
    SleepMood.TIRED -> androidx.compose.material.icons.Icons.Outlined.SentimentDissatisfied
    SleepMood.OKAY -> androidx.compose.material.icons.Icons.Outlined.SentimentNeutral
    SleepMood.FRESH -> androidx.compose.material.icons.Icons.Outlined.SentimentSatisfied
    SleepMood.GREAT -> androidx.compose.material.icons.Icons.Outlined.SentimentVerySatisfied
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
