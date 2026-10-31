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
 * [PRIV-03] 결정 D84·D111: one ask, on the calendar, to a phone installed before 처리방침 3.1 or 3.9 was posted. A yes lets
 * 결제·광고 and the 3.2~3.8 features reach this phone from 정식 출시일; without one they wait until the dates first promised
 * (11-24 for 3.1, 11-29 for 3.9).
 */
@Composable
fun PolicyConsentNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    var asks by remember { mutableStateOf(c.policyAsks()) }
    var thanked by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    if (!asks && !thanked) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (thanked) {
                Text("고마워요. 정식 출시일(11월 10일 예정)부터 새 처리방침·이용약관으로 써요.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { thanked = false }) { Text("닫기") }
                return@Column
            }
            Text("처리방침·이용약관이 바뀌어요", style = MaterialTheme.typography.titleMedium)
            // [R136-02] 1.36 (리뷰 R-03: 첫 화면이 이 카드로 덮여 달력이 안 보였어요): the first line only; the rest on 바뀌는 것 보기.
            PolicyConsent.lines.firstOrNull()?.let { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
            if (open) {
                PolicyConsent.lines.drop(1).forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                Text("동의하지 않으셔도 지금처럼 써요. 그때는 원래 알린 날짜(11월 24~29일)부터 시행돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.remoteConfig.agreePolicy(); asks = false; thanked = true }) { Text("동의해요") }
                if (!open && PolicyConsent.lines.size > 1) TextButton(onClick = { open = true }) { Text("바뀌는 것 보기") }
                else TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("자세히 보기") }
            }
        }
    }
}
