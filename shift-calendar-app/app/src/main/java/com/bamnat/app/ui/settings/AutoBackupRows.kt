package com.bamnat.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.backup.AutoBackup
import com.bamnat.app.data.BackupManager
import com.bamnat.app.tier.Feature
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.pro.openPro
import java.time.ZoneId
import com.bamnat.core.time.Today

/** [PROV-03] The row's title, which its notice and settings search point at. */
const val AUTO_BACKUP_ROW = "매일 자동 백업"

/**
 * [PROV-03] 매일 자동 백업 (프로): a password, then a file the user picks (Google Drive works), then a
 * sealed backup there every day. The row says when it last worked and why not when it does not.
 * 2.0.0 [RVL-52] (검토 U12-06) A folder first: a dated file a day, the newest seven kept. One file stays for Google Drive.
 * 2.0.0 [RVL-51] (검토 U12-05) Without 프로 the row says it waits, and 지금 한 번 저장 still saves by hand.
 */
@Composable
internal fun AutoBackupRows(vm: SettingsViewModel) {
    val context = LocalContext.current
    val state by vm.autoBackup.collectAsStateWithLifecycle()
    val gate by vm.gate.collectAsStateWithLifecycle()
    val open = gate.allows(Feature.AUTO_BACKUP)
    var asking by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.FILE_MIME)) { uri ->
        if (uri != null) vm.startAutoBackup(context, uri) else vm.prepareAutoBackup(null)
    }
    val createIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.startAutoBackup(context, uri, folder = true) else vm.prepareAutoBackup(null)
    }
    val move = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.FILE_MIME)) { uri ->
        uri?.let(vm::moveAutoBackup)
    }
    val moveIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.moveAutoBackup(it, folder = true) }
    }
    SwitchRow(
        title = AUTO_BACKUP_ROW,
        pro = true, proLocked = !open, // [TIER-06]
        subtitle = "하루 한 번 암호 건 백업을 저장하고 다시 읽어 확인해요. 폴더를 고르면 최근 7일치를 보관해요",
        // 2.0.0 [RVO-17] 검토 SET-11·U11a-08 '끄기는 누구나, 켜기만 프로': stopped because 프로 ended, the switch stays as the user left
        // it with the line why ([statusLine]), and turning it off needs no 프로 (as 앱 잠금 resting, RVL-09).
        checked = state.on,
    ) { want ->
        when {
            !want -> confirmOff = true
            !open -> openPro(com.bamnat.app.tier.Feature.AUTO_BACKUP)
            else -> asking = true
        }
    }
    if (state.on) {
        val line = statusLine(state, open)
        Text(line.first, style = MaterialTheme.typography.bodySmall,
            color = if (line.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        // 2.0.0 [RVL-52] (검토 HQ-149, 총괄이 #536 사진에서) The row's own one: '지금 백업' (or '지금 한 번 저장'), and '위치 바꾸기' a
        // text button under it; from 150% type the two stand one above the other, so neither breaks into two lines (조건 2·12).
        val now: @Composable (Modifier) -> Unit = { m ->
            OutlinedButton(onClick = vm::backupNow, enabled = !state.running, modifier = m.heightIn(min = 48.dp)) {
                Text(when {
                    state.running -> "백업하는 중…"
                    open -> "지금 백업"
                    else -> SAVE_ONCE
                })
            }
        }
        val where: @Composable (Modifier) -> Unit = { m ->
            androidx.compose.material3.TextButton(onClick = { moving = true }, enabled = !state.running, modifier = m.heightIn(min = 48.dp)) {
                Text("위치 바꾸기")
            }
        }
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                now(Modifier.fillMaxWidth())
                where(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                now(Modifier.weight(1f))
                where(Modifier)
            }
        }
    }
    if (asking) {
        AutoBackupPasswordDialog(onDismiss = { asking = false }) { password, folder ->
            asking = false
            vm.prepareAutoBackup(password)
            if (folder) createIn.launch(null) else create.launch(AutoBackup.FILE_NAME)
        }
    }
    if (moving) {
        AutoBackupWhereDialog(onDismiss = { moving = false }) { folder ->
            moving = false
            if (folder) moveIn.launch(null) else move.launch(AutoBackup.FILE_NAME)
        }
    }
    if (confirmOff) {
        ConfirmDialog(
            title = "자동 백업을 끌까요?",
            message = "지금까지 저장한 파일은 그대로 남아요. ‘파일 불러오기’로 암호를 넣어 열 수 있어요. 다시 켜면 암호와 위치를 새로 정해요.",
            confirmLabel = "끄기",
            onDismiss = { confirmOff = false },
            onConfirm = { confirmOff = false; vm.stopAutoBackup() },
        )
    }
}

/** 2.0.0 [RVL-51] The row's button while 프로 has ended: one backup by hand, the same place and password. */
internal const val SAVE_ONCE = "지금 한 번 저장"

/** What the row says under the switch, and whether it is a problem. */
internal fun statusLine(s: AutoBackup.State, open: Boolean, zone: ZoneId = Today.zone()): Pair<String, Boolean> {
    val last = s.lastOk?.atZone(zone)?.let { "${Fmt.monthDay(it.toLocalDate())} ${Fmt.time(it.toLocalTime())}" }
    // [RVL-52] A folder says how many days it holds.
    val where = s.name?.let { if (!s.folder) " · $it" else if (s.kept > 0) " · $it 폴더에 ${s.kept}일치" else " · $it 폴더" }.orEmpty()
    return when {
        !open -> "프로 기간이 끝나 매일 저장은 쉬고 있어요. ‘$SAVE_ONCE’은 언제든 돼요." + (last?.let { " 마지막 백업 $it$where" }.orEmpty()) to false
        s.running -> "백업하는 중…$where" to false
        s.error != null -> s.error + (last?.let { " (마지막 성공 $it)" } ?: "") to true
        last != null -> "마지막 백업 $last$where" to false
        else -> "첫 백업을 기다리는 중$where" to false
    }
}
