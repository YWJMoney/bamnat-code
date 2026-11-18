package com.bamnat.app.imports

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.core.imports.RosterGrid
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [READ-31] 1.39 (리더·사장님 화면 연결): a roster photo or file shared from 카카오톡·갤러리 went only to 한 달 근무 넣기 (내 달력),
 * even for a leader whose team's roster it is, or a 사장님 sending the staff list. With a team or a shop on this phone, the share
 * asks where it goes; the team's board or the 급여 대장 then opens with it read and waiting for its usual check (① 이름 ② 코드,
 * or the staff list's one page). Nothing else changes: the reading is [ManageImport]'s, on the phone.
 */
object SharedToManage {
    /** Where a shared roster may go besides 내 달력. */
    sealed interface Place {
        data class Team(val id: String, val name: String) : Place
        data object Staff : Place
    }

    /** A team's roster read from the share, waiting for its board to open. */
    data class ForTeam(val teamId: String, val grid: RosterGrid, val fromPhoto: Boolean)

    private val team = MutableStateFlow<ForTeam?>(null)
    private val staff = MutableStateFlow<Uri?>(null)

    fun putTeam(t: ForTeam) { team.value = t }
    fun putStaff(uri: Uri) { staff.value = uri }

    /** The board's roster, once. */
    fun takeTeam(): ForTeam? = team.value.also { team.value = null }

    /** The staff list to read, once. */
    fun takeStaff(): Uri? = staff.value.also { staff.value = null }

    val staffWaiting: Boolean get() = staff.value != null

    private val ledger = MutableStateFlow(false)

    /** 1.39 G2 통합 6차 PC: the calendar's 급여 대장 line opens 관리 straight on its 급여 대장 (it took three presses). */
    fun openLedger() { ledger.value = true }

    val ledgerAsked: kotlinx.coroutines.flow.StateFlow<Boolean> get() = ledger

    /** True once after [openLedger]. */
    fun takeLedger(): Boolean = ledger.value.also { ledger.value = false }

    /** The staff list waiting, watched so a 관리 화면 already open goes to its 급여 대장. */
    val staffShared: kotlinx.coroutines.flow.StateFlow<Uri?> get() = staff

    /** The choices a share gets: 내 달력 always, then each team (leader), then 직원 명단 (사장님); empty when there is nothing but 내 달력. */
    fun places(teams: List<Place.Team>, shop: Boolean): List<Place> = buildList {
        addAll(teams.take(MAX_TEAMS))
        if (shop) add(Place.Staff)
    }

    private const val MAX_TEAMS = 4
}

/** [READ-31] "어디에 넣을까요?" for a shared roster: 내 달력, a team's roster, or the staff list. */
@Composable
fun SharedToManageChoice(places: List<SharedToManage.Place>, busy: String?, note: String? = null, onMine: () -> Unit, onPlace: (SharedToManage.Place) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text("받은 근무표를 어디에 넣을까요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("사진·파일은 휴대폰 안에서만 읽어요. 넣기 전에 한 번 더 확인해요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onMine, enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("내 달력 (내 근무만)") }
                places.forEach { p ->
                    OutlinedButton(onClick = { onPlace(p) }, enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(when (p) {
                            is SharedToManage.Place.Team -> "${p.name} 팀 근무표 (팀원 모두)"
                            SharedToManage.Place.Staff -> "가게 직원 명단 (급여 대장)"
                        })
                    }
                }
                busy?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                // A file the team's reading could not use: said here, so 내 달력 or another place can still be picked.
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = busy == null) { Text("취소") } },
    )
}
