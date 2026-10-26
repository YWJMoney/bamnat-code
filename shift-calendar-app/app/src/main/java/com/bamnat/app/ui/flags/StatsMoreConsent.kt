package com.bamnat.app.ui.flags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.flags.Check
import com.bamnat.core.flags.FlagCatalog

/** [FLAG-16][PERF-16] What 처리방침 3.2 adds (1.28 통합: the times go as AO's buckets, not exact milliseconds) to 사용 통계, in the words of the one ask (순번 459). */
const val STATS_MORE_TITLE = "보내는 내용이 두 가지 늘어요(켜진 기능 목록, 앱 시작에 걸린 시간)"
const val STATS_MORE_BODY = "사용 통계를 켜 두셔서 여쭤봐요. 이 휴대폰에서 켜진 기능 이름 목록과, 앱을 열 때 달력이 보이기까지 걸린 시간(0.1초~15초의 정해진 구간 값)을 " +
    "하루 한 번 함께 보내요. 휴대폰을 구별하는 번호는 보내지 않고, 안 하셔도 앱은 똑같이 돼요."

/**
 * [FLAG-16] 1.28 AL: whether to ask the 3.2 extra yes now: 처리방침 3.2 is in force (the `stats.more` row's date, by the
 * server's clock), 사용 통계 is on, and the user has not answered.
 */
fun statsMoreAsks(c: AppContainer): Boolean {
    val s = c.settingsRepository.current
    if (!s.shareUsageStats || s.usageStatsMore != null) return false
    return c.flags.state(FlagCatalog.STATS_MORE)?.checks?.firstOrNull()?.status == Check.Status.MET
}

/** [FLAG-16] The one-time card over the calendar: 동의 / 안 함, either answer kept (and backed up with 사용 통계). */
@Composable
fun StatsMoreNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    var asks by remember { mutableStateOf(statsMoreAsks(c)) }
    if (!asks) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(STATS_MORE_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(STATS_MORE_BODY, style = MaterialTheme.typography.bodyMedium)
            Text("설정 › 개인정보 › 사용 통계에서 언제든 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { answer(c, true); asks = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("동의") }
                TextButton(onClick = { answer(c, false); asks = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("안 함") }
            }
        }
    }
}

/**
 * [FLAG-16] The same yes under 설정 › 개인정보 › 사용 통계, shown with 사용 통계 on once 처리방침 3.2 is in force (and to a
 * phone that already answered, to change it).
 */
@Composable
fun StatsMoreRow() {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val inForce = rows[FlagCatalog.STATS_MORE]?.checks?.firstOrNull()?.status == Check.Status.MET
    if (!settings.shareUsageStats || (!inForce && settings.usageStatsMore == null)) return
    com.bamnat.app.ui.settings.SwitchRow(
        "켜진 기능 목록·앱 시작 시간도 보내기",
        STATS_MORE_BODY,
        settings.usageStatsMore == true,
        onChange = { answer(c, it) },
    )
}

private fun answer(c: AppContainer, yes: Boolean) {
    c.settingsRepository.update { it.copy(usageStatsMore = yes) }
}
