package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.backup.BackupNudge
import com.bamnat.core.time.Today

/**
 * 1.32 [STAB-08] (근무유형 조사 공통 6장): the calendar's one line when there has been no backup for 30 days and no 매일 자동
 * 백업. '백업 저장' opens 설정 › 데이터 on its row, '나중에' waits a week, and the switch is in 설정 › 데이터.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupNudgeNotice(hasRecords: Boolean, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val s by c.backupNudge.state.collectAsStateWithLifecycle()
    val auto by c.autoBackup.state.collectAsStateWithLifecycle()
    val today = Today.date()
    val lastAuto = auto.lastOk?.atZone(Today.zone())?.toLocalDate()
    if (!BackupNudge.due(s, settings.backupNudge, auto.on, lastAuto, hasRecords, today)) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(BackupNudge.words(s, lastAuto, today) + "이에요", style = MaterialTheme.typography.titleMedium)
            Text("기록은 이 휴대폰에만 있어요. 휴대폰을 잃어버리거나 바꾸면 백업 파일이 있어야 되살릴 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { SettingsLinks.open(SettingsPage.DATA, "백업 저장") }) { Text("백업 저장") }
                TextButton(onClick = { c.backupNudge.later(today) }) { Text("나중에") }
            }
            Text("이 알림은 설정 › 데이터 › ‘$BACKUP_NUDGE_ROW’에서 끌 수 있어요. 매일 자동 백업(프로)을 켜면 뜨지 않아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
