package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer

/**
 * 1.39 [SPREAD-11] '밤낮 표시' right where a 견적서·거래내역서 is sent (before, only 설정 › 데이터 had it). It is the same setting
 * as 설정 › 데이터 › '보내는 사진에 밤낮 표시' and the 근무표 사진 sheet's switch, so turning it off here keeps it off everywhere.
 */
@Composable
fun ExportMarkSwitch(line: String, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.appContainer.settingsRepository
    var on by remember { mutableStateOf(repo.current.exportMark) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Switch) { v ->
            on = v
            repo.update { it.copy(exportMark = v) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("밤낮 표시", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (on) "사진 맨 아래에 작게 ‘$line’. 받은 분이 앱을 찾을 수 있어요"
                else "표시 없이 보내요. 설정 › 데이터에서도 바꿀 수 있어요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}
