package com.bamnat.app.ui.pro

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.ops.PolicyConsent

/**
 * [PRIV-03] 결정 D84: one ask, on the calendar, to a phone installed before 처리방침 3.1 was posted. A yes lets the
 * 정식 출시 switch reach this phone from 11-10; without one it waits until 11-24, the date 3.0 promised.
 */
@Composable
fun PolicyConsentNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    var asks by remember { mutableStateOf(c.policyAsks()) }
    var thanked by remember { mutableStateOf(false) }
    if (!asks && !thanked) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (thanked) {
                Text("고마워요. 11월 10일부터 새 처리방침·이용약관으로 써요.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { thanked = false }) { Text("닫기") }
                return@Column
            }
            Text("처리방침·이용약관이 바뀌어요", style = MaterialTheme.typography.titleMedium)
            PolicyConsent.lines.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
            Text("동의하지 않으셔도 지금처럼 써요. 그때는 원래 날짜(11월 24일)부터 시행돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.remoteConfig.agreePolicy(); asks = false; thanked = true }) { Text("동의해요") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("자세히 보기") }
            }
        }
    }
}
