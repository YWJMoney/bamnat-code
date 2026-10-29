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
internal fun TextSizePicker(step: Int, onPick: (Int) -> Unit) {
    val base = LocalDensity.current
    // The chips show each step against the phone's own size, not the step already chosen.
    val phone = base.fontScale / TextSize.STEPS[step.coerceIn(0, TextSize.STEPS.lastIndex)]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextSize.LABELS.forEachIndexed { i, label ->
            CompositionLocalProvider(LocalDensity provides Density(base.density, TextSize.scale(phone, i))) {
                FilterChip(selected = step == i, onClick = { onPick(i) }, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun TextSizeRow(step: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("글자 크기", style = MaterialTheme.typography.titleSmall)
        Text("${TextSize.LABELS[step.coerceIn(0, TextSize.LABELS.lastIndex)]} · 휴대폰 글꼴 크기와 함께 적용돼요. 화면 배치는 글자에 맞춰 바뀌어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TextSizePicker(step, onPick)
    }
}

/**
 * U 1.32 [UX132-05] (사용자 2026-10-28 "시작시 글자 크기를 조절하게 하고 설정에서도 조정할 수 있게"): once, the first time the
 * app opens after this version. Each tap changes the whole app behind it at once, so the user sees the real screens.
 */
@Composable
fun TextSizeFirstAsk(step: Int, onPick: (Int) -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("글자 크기를 골라 주세요") },
        text = {
            Column {
                Text("누르면 바로 바뀌어요. 뒤에 보이는 화면으로 맞는지 보세요.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextSizePicker(step, onPick)
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.Image }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("10월 28일 (수) · 주간", style = MaterialTheme.typography.titleSmall)
                        Text("09:00 – 18:00 · 이번 달 예상 급여 2,840,000원", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("나중에 설정 › 화면과 글자에서 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("이대로 시작") } },
    )
}
