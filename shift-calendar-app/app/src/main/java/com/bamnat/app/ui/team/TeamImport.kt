package com.bamnat.app.ui.team

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.imports.AnyFile
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.imports.RosterGrid
import com.bamnat.core.team.TeamGridImport
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift

/** The file picker for 가져오기: 엑셀·CSV·PDF·사진 ([AnyFile.MIME_TYPES]). */
@Composable
fun rememberAnyFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPicked(uri) }
    return { runCatching { launcher.launch(AnyFile.MIME_TYPES) } }
}

/** What the leader chose: row i's member (null: new or not put in), the rows left out, and each code's kind of day. */
data class TeamImportChoice(val memberOf: List<String?>, val skipped: Set<Int>, val codes: Map<String, Long?>)

private const val NEW = "+new"
private const val SKIP = "-skip"

/**
 * 1.38 가져오기 ② (사용자 2026-11-05 "어떤 형식이든 … 틀에 관계없이"): what was read, in the leader's two steps on one page.
 * ① 이름: each line of the file is a member (같은 이름은 맞춰 둠), a new one, or left out. ② 코드: each code written in the file
 * (most cells first) is one of the team's kinds of day, or left blank for the keypad. Nothing is written until 근무표에 넣기.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamImportDialog(
    grid: RosterGrid, fromPhoto: Boolean, members: List<TeamMember>, shifts: List<TeamShift>,
    onDismiss: () -> Unit, onApply: (TeamImportChoice) -> Unit,
) {
    val rows = remember(grid) { TeamGridImport.rows(grid) }
    val counts = remember(rows) { TeamGridImport.codeCounts(rows) }
    val matched = remember(rows, members) { TeamGridImport.matchNames(rows, members) }
    val pick = remember(rows) { mutableStateListOf<String>().apply { addAll(matched.map { it ?: NEW }) } }
    val codes = remember(rows) { mutableStateMapOf<String, Long?>().apply { putAll(TeamGridImport.guessCodes(counts.map { it.first }, shifts)) } }
    val active = members.filter { it.active }
    val room = 60 - members.size
    val newCount = pick.count { it == NEW }
    val unpaired = counts.count { codes[it.first] == null }
    val doubt = grid.doubtfulCount
    AlertDialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("${Fmt.month(grid.month)} 근무표 가져오기") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${rows.size}명 · ${grid.days.size}일을 읽었어요." + (if (doubt > 0) " 자신 없는 칸 ${doubt}개는 넣은 뒤 먼저 보여 드려요." else ""),
                    style = MaterialTheme.typography.bodyMedium)
                if (fromPhoto) Text("사진에서 읽은 표예요. 넣은 뒤 노란 테두리 칸만 원본과 맞춰 보면 돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("① 이름 맞추기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                rows.forEachIndexed { i, r ->
                    var open by remember { mutableStateOf(false) }
                    val choice = pick[i]
                    val label = when (choice) {
                        NEW -> "새 팀원으로 더하기"
                        SKIP -> "넣지 않기"
                        else -> members.firstOrNull { it.id == choice }?.name ?: "새 팀원으로 더하기"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name + (r.role?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                            Text("${r.cells.size}칸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            TextButton(onClick = { open = true }) {
                                Text("→ $label", color = if (choice == SKIP) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                active.forEach { m ->
                                    val taken = pick.withIndex().any { (j, v) -> j != i && v == m.id }
                                    DropdownMenuItem(text = { Text(m.name + if (taken) " (다른 줄에 씀)" else "") }, enabled = !taken, onClick = { pick[i] = m.id; open = false })
                                }
                                DropdownMenuItem(text = { Text("새 팀원으로 더하기") }, onClick = { pick[i] = NEW; open = false })
                                DropdownMenuItem(text = { Text("넣지 않기") }, onClick = { pick[i] = SKIP; open = false })
                            }
                        }
                    }
                }
                if (newCount > room) Text("팀은 60명까지예요. 새로 더할 ${newCount}명 중 ${room.coerceAtLeast(0)}명만 들어가요.",
                    style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)

                HorizontalDivider()
                Text("② 코드 짝짓기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("파일에 쓰인 글자마다 우리 팀 근무를 골라요. 한 번 고르면 같은 글자 칸 모두에 들어가요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                counts.forEach { (code, n) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("‘$code’ · ${n}칸" + if (codes[code] == null) " · 아직 비워 둠" else "", style = MaterialTheme.typography.bodyMedium,
                            color = if (codes[code] == null) LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            shifts.forEach { s -> FilterChip(selected = codes[code] == s.id, onClick = { codes[code] = s.id }, label = { Text(s.label) }) }
                            FilterChip(selected = codes[code] == null, onClick = { codes[code] = null }, label = { Text("비워 두기") })
                        }
                    }
                }
                Text("이미 칠한 칸은 파일 것으로 바뀌고, 잠근 칸은 그대로예요. 넣은 뒤 마음에 안 들면 위의 되돌리기 한 번이면 모두 돌아가요." +
                    if (unpaired > 0) " 비워 둔 코드 ${unpaired}가지 칸은 확인할 칸으로 남겨요." else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }, confirmButton = {
            TextButton(enabled = pick.any { it != SKIP }, onClick = {
                onApply(TeamImportChoice(pick.map { if (it == NEW || it == SKIP) null else it }, pick.withIndex().filter { it.value == SKIP }.map { it.index }.toSet(), codes.toMap()))
            }) { Text("근무표에 넣기") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } })
}

/** The line after 가져오기: what went in, what was kept, and where the checking starts. */
fun importSummary(a: TeamGridImport.Applied, added: Int): String = buildString {
    append("${a.written}칸을 넣었어요.")
    if (added > 0) append(" 새 팀원 ${added}명을 더했어요.")
    if (a.toCheck.isNotEmpty()) append("\n확인할 칸 ${a.toCheck.size}개에 노란 테두리를 해 두었어요. 아래 ‘확인할 칸’ 단추로 하나씩 가요.")
    if (a.keptLocked > 0) append("\n잠근 칸 ${a.keptLocked}개는 그대로 두었어요.")
    if (a.outside > 0) append("\n다른 달 날짜 ${a.outside}칸은 넣지 않았어요. 그 달로 넘어가 한 번 더 가져오면 들어가요.")
    append("\n마음에 안 들면 위의 되돌리기 한 번이면 가져오기 전으로 돌아가요.")
}

/** 가져오기 entry for the board's tools. */
@Composable
fun ImportButton(onClick: () -> Unit) { OutlinedButton(onClick = onClick) { Text("파일·사진에서 가져오기") } }
