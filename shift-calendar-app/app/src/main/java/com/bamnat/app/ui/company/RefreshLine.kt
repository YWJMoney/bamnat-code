package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * [SPD-13] 1.30 묶음 AP1 (사용자 2026-10-27 "조회하는 로딩이 너무 느리다"): a screen that shows what the phone kept from before
 * while it reads again says so with a thin line on top ([refreshing]), never a spinner over an empty screen; when reading
 * again failed ([stale]) one line says the screen is the last one seen, with 다시 시도. Nothing at all otherwise.
 */
@Composable
fun RefreshLine(refreshing: Boolean, stale: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier, seenAt: String? = null) {
    when {
        refreshing -> LinearProgressIndicator(modifier.fillMaxWidth().semantics { contentDescription = "새로 불러오는 중" })
        stale -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 1.38 (장터 그림 LOW, PC 제안 '○시에 읽은 목록 · 다시 시도'): when known, the time the shown list was read.
            Text(if (seenAt != null) "$seenAt에 읽은 목록이에요. 인터넷이 되면 새로 불러와요." else "최근 본 내용이에요. 인터넷이 되면 새로 불러와요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(end = 8.dp))
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 시도") }
        }
    }
}
