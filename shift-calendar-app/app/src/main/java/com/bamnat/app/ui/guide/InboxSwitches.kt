package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.inbox.Inbox

/**
 * 2.0.0 [RVP-43] 검토 HQ-50 (사용자 11-12 20:38 KST "거부를 하면 여러 번 반복해서 알림으로 보여 주지 마 … 닫기 다시는 열지 않기 같은 것을
 * 넣어 줘"): in 기능 켜고 끄기 › 안내, beside '안 써 본 기능 알려 주기' (DISC-04): the 알림함's 권유 on or off, and every kind of
 * notice turned off with '다시 보지 않기', each back with one tap.
 */
@Composable
internal fun InboxSwitches() {
    val context = LocalContext.current
    val inbox = remember { Inbox.get(context) }
    val off by inbox.suggestOff.collectAsStateWithLifecycle()
    val muted by inbox.muted.collectAsStateWithLifecycle()
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("알림함 권유 받기", style = MaterialTheme.typography.bodyLarge)
            Text("안 써 본 기능, 친구 초대, 내 설정 나누기를 알림함(종)에 한 번에 하나씩 알려 드려요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = !off, onCheckedChange = { inbox.setSuggestOff(!it) },
            modifier = Modifier.semantics { contentDescription = "알림함 권유 받기" })
    }
    if (muted.isEmpty()) return
    Text("다시 보지 않기로 한 알림", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    muted.sorted().forEach { kind ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(inbox.label(kind), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { inbox.unmute(kind) }) { Text("다시 보기") }
        }
    }
}
