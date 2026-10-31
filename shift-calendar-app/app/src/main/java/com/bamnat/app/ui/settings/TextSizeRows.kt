package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.theme.TextSize

/**
 * U 1.32 [UX132-05] 글자 크기: five steps, each chip drawn at its own size so the choice is seen, not read. The phone's own
 * 글꼴 크기 is kept and multiplied, so someone who already made the phone larger is not made smaller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TextSizePicker(step: Int, onPick: (Int) -> Unit, selected: Int = step) {
    val base = LocalDensity.current
    // The chips show each step against the phone's own size, not the step already chosen.
    val phone = base.fontScale / TextSize.STEPS[step.coerceIn(0, TextSize.STEPS.lastIndex)]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextSize.LABELS.forEachIndexed { i, label ->
            CompositionLocalProvider(LocalDensity provides Density(base.density, TextSize.scale(phone, i))) {
                FilterChip(selected = selected == i, onClick = { onPick(i) }, label = { Text(label) })
            }
        }
    }
}

/**
 * [J133-23] 1.33 (실사용 테스트 "누를 때마다 화면 전체가 바뀌어 창이 뛰어요"): a chip shows that size in the card under it only;
 * 이 크기로 puts it on the whole app.
 */
@Composable
private fun TextSizePreview(step: Int) {
    run {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Image }) {
            Column(Modifier.padding(12.dp)) {
                Text("미리 보기 · ${TextSize.LABELS[step.coerceIn(0, TextSize.LABELS.lastIndex)]}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [R136-17] 1.36: the same for every kind of work (no 주간 or 예상 급여 for a shop or an investor).
                Text("10월 28일 (수) · 달력", style = MaterialTheme.typography.titleSmall)
                Text("09:00 – 18:00 · 메모 2개 · 이번 달 2,840,000원", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The phone's own scale times [step], for drawing a preview at a step not yet chosen. */
@Composable
private fun scaleOf(chosen: Int, step: Int): Float {
    val base = LocalDensity.current
    val phone = base.fontScale / TextSize.STEPS[chosen.coerceIn(0, TextSize.STEPS.lastIndex)]
    return TextSize.scale(phone, step)
}

@Composable
internal fun TextSizeRow(step: Int, onPick: (Int) -> Unit) {
    var pending by androidx.compose.runtime.saveable.rememberSaveable(step) { androidx.compose.runtime.mutableIntStateOf(step) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("글자 크기", style = MaterialTheme.typography.titleSmall)
        Text("${TextSize.LABELS[step.coerceIn(0, TextSize.LABELS.lastIndex)]} · 휴대폰 글꼴 크기와 함께 적용돼요. 화면 배치는 글자에 맞춰 바뀌어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextSizePicker(step, { pending = it }, selected = pending)
        val scale = scaleOf(step, pending)
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) { TextSizePreview(pending) }
        if (pending != step) Button(onClick = { onPick(pending) }) { Text("이 크기로") }
    }
}

/**
 * U 1.32 [UX132-05] (사용자 2026-10-28 "시작시 글자 크기를 조절하게 하고 설정에서도 조정할 수 있게"): once, the first time the
 * app opens after this version. [J133-23] 1.33: the chips change the preview; 이 크기로 시작 puts it on.
 */
@Composable
fun TextSizeFirstAsk(step: Int, onPick: (Int) -> Unit, onDone: () -> Unit) {
    var pending by androidx.compose.runtime.saveable.rememberSaveable(step) { androidx.compose.runtime.mutableIntStateOf(step) }
    val scale = scaleOf(step, pending)
    AlertDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("글자 크기를 골라 주세요") },
        text = {
            Column {
                Text("누르면 아래 미리 보기가 바뀌어요. 맞는 크기에서 ‘이 크기로 시작’을 눌러 주세요.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextSizePicker(step, { pending = it }, selected = pending)
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) { TextSizePreview(pending) }
                Spacer(Modifier.height(8.dp))
                Text("나중에 설정 › 화면과 글자에서 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { if (pending != step) onPick(pending); onDone() }) { Text("이 크기로 시작") } },
    )
}
