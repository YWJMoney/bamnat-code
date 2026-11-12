package com.bamnat.app.ui.team

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.SideOrBelow
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.Tone
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
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
import com.bamnat.app.ui.theme.color
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
data class TeamImportChoice(val memberOf: List<String?>, val skipped: Set<Int>, val codes: Map<String, Long?>,
                            /** 1.38 [P3-L-17] 신청으로 넣기 (종이 신청표): each code's request ([com.bamnat.core.team.WishKind.code]); null puts 근무 in. */
                            val wishCodes: Map<String, String?>? = null,
                            /** 2.0.0 [RVJ-27] 검토 PAYU-57: a new member's name as fixed here, by row. */
                            val names: Map<Int, String> = emptyMap())

private const val NEW = "+new"
private const val SKIP = "-skip"

/**
 * 1.38 가져오기 ② (사용자 2026-11-05 "어떤 형식이든 … 틀에 관계없이"): what was read, in the leader's two steps on one page.
 * ① 이름: each line of the file is a member (같은 이름은 맞춰 둠), a new one, or left out. ② 코드: each code written in the file
 * (most cells first) is one of the team's kinds of day, or left blank for the keypad. Nothing is written until 근무표에 넣기.
 *
 * 2.0.0 [RVJ-27] 검토 PAYU-57·T05b-13 (사진이 한 글자 틀리게 읽은 이름이 새 팀원이 되어 팀원이 겹쳐 생김): a name a letter off one member
 * is that member first, with a 주의 줄 to check; a line that does become a new member shows the name to fix before it goes in; a
 * line opens to the cells read. It has fields now, so it is the 입력 시트 (공통 부품: 입력은 아래 시트, 채운 단추 하나) and asks
 * before back or a swipe throws the choices away; the window in the middle could not be closed by either and cut its list
 * off above the buttons.
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
    val close = remember(rows, members) { TeamGridImport.closeNames(rows, members, matched) }
    val pick = remember(rows) { mutableStateListOf<String>().apply { addAll(rows.indices.map { matched[it] ?: close[it] ?: NEW }) } }
    val names = remember(rows) { mutableStateListOf<String>().apply { addAll(rows.map(TeamGridImport::newName)) } }
    val guessed = remember(rows) { TeamGridImport.guessCodes(counts.map { it.first }, shifts) }
    val codes = remember(rows) { mutableStateMapOf<String, Long?>().apply { putAll(guessed) } }
    // [RVJ-27] 검토 U05b-16: a code paired by the words rosters use (A as D, 연 as 휴무), not by the team's own, says so until changed.
    val loose = remember(rows) { TeamGridImport.looselyGuessed(counts.map { it.first }, shifts) }
    val active = members.filter { it.active }
    val room = 60 - members.size
    val newCount = pick.count { it == NEW }
    val unpaired = counts.count { codes[it.first] == null }
    val doubt = grid.doubtfulCount
    // 1.38 [P3-L-17] 종이 신청표: a sheet with a few 휴·연·교 per person opens as 신청; the leader can switch.
    var asWishes by remember(rows) { mutableStateOf(TeamGridImport.looksLikeRequests(rows, grid.days.size, shifts)) }
    val wishCodes = remember(rows) { mutableStateMapOf<String, String?>().apply { putAll(TeamGridImport.guessWishes(counts.map { it.first }, shifts)) } }
    val wishChoices = remember(shifts) {
        listOf(com.bamnat.core.team.WishKind.OFF, com.bamnat.core.team.WishKind.LEAVE, com.bamnat.core.team.WishKind.HALF, com.bamnat.core.team.WishKind.EDU,
            com.bamnat.core.team.WishKind.AVOID_NIGHT, com.bamnat.core.team.WishKind.WORK).map { it.code(null) to it.short } +
            shifts.filter { !it.isOff }.map { com.bamnat.core.team.WishKind.SHIFT.code(it.id) to "${it.label} 원해요" }
    }
    val unnamed = rows.indices.any { pick[it] == NEW && names[it].isBlank() }
    FormSheet(onDismissRequest = onDismiss, unsaved = true,
        title = { Text("${Fmt.month(grid.month)} " + if (asWishes) "신청표 가져오기" else "근무표 가져오기") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !asWishes, onClick = { asWishes = false }, label = { Text("근무표로 넣기") })
                    FilterChip(selected = asWishes, onClick = { asWishes = true }, label = { Text("신청으로 넣기 (종이 신청표)") })
                }
                if (asWishes) Text("사람마다 쉬고 싶은 날·연차·교육을 적은 신청표로 읽었어요. 표에는 칠하지 않고 신청으로 넣어서, 자동 채우기가 지키고 공정성 장부의 '신청 지킴'에 들어가요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${rows.size}명 · ${grid.days.size}일을 읽었어요." + (if (doubt > 0) " 자신 없는 칸 ${doubt}개는 넣은 뒤 먼저 보여 드려요." else ""),
                    style = MaterialTheme.typography.bodyMedium)
                if (fromPhoto) Text("사진에서 읽은 표예요. 넣은 뒤 노란 테두리 칸만 원본과 맞춰 보면 돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("① 이름 맞추기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                rows.forEachIndexed { i, r ->
                    var open by remember { mutableStateOf(false) }
                    var cells by remember { mutableStateOf(false) }
                    val choice = pick[i]
                    val label = when (choice) {
                        NEW -> "새 팀원으로 더하기"
                        SKIP -> "넣지 않기"
                        else -> members.firstOrNull { it.id == choice }?.name ?: "새 팀원으로 더하기"
                    }
                    Column {
                        // The name keeps its line: with big letters the choice goes under it instead of squeezing it.
                        SideOrBelow(Modifier.fillMaxWidth(), start = {
                            // [RVJ-27] ④ The line opens to what was read for it, to tell whose line it is before choosing.
                            Column(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = if (cells) "읽은 칸 접기" else "읽은 칸 보기") { cells = !cells }
                                .semantics { stateDescription = if (cells) "읽은 칸 펼침" else "읽은 칸 접힘" }, verticalArrangement = Arrangement.Center) {
                                Text(r.name + (r.role?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${r.cells.size}칸", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    // As big as the letters beside it (16dp at 글자 200% is a speck).
                                    Icon(if (cells) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(with(LocalDensity.current) { 16.sp.toDp() }),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }, end = {
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
                        })
                        // [RVJ-27] ① A name a letter off a member's: that member, and the leader looks once.
                        if (close[i] != null && choice == close[i]) ToneLine("비슷한 이름이라 ${label}님으로 맞춰 뒀어요. 맞는지 봐 주세요.", Modifier.padding(bottom = 4.dp))
                        if (cells) Text(readCells(r, grid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp))
                        // [RVJ-27] ② A new member's name, to fix here before a misread one joins the team.
                        if (choice == NEW) OutlinedTextField(names[i], { names[i] = it.take(com.bamnat.core.team.TeamPaste.NAME_MAX) }, Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            label = { Text("새 팀원 이름") }, singleLine = true, isError = names[i].isBlank(),
                            supportingText = if (names[i].isBlank()) ({ Text("이름을 적어 주세요") }) else null)
                    }
                }
                if (newCount > 0) {
                    val added = rows.indices.filter { pick[it] == NEW }.map { names[it].trim() }.filter { it.isNotEmpty() }
                    ToneLine("새 팀원 ${newCount}명을 더해요" + (if (added.isEmpty()) "" else ": " + added.take(NEW_NAMES).joinToString(", ") + if (added.size > NEW_NAMES) " 외 ${added.size - NEW_NAMES}명" else "") +
                        ". 이미 있는 팀원이면 그 줄의 →를 눌러 골라 주세요.", tone = Tone.Plain)
                }
                if (newCount > room) ToneLine("팀은 60명까지예요. 새로 더할 ${newCount}명 중 ${room.coerceAtLeast(0)}명만 들어가요.")

                HorizontalDivider()
                Text("② 코드 짝짓기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (asWishes) "적힌 글자마다 어떤 신청인지 골라요. 한 번 고르면 같은 글자 칸 모두에 들어가요." else "파일에 쓰인 글자마다 우리 팀 근무를 골라요. 한 번 고르면 같은 글자 칸 모두에 들어가요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (asWishes) counts.forEach { (code, n) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("‘$code’ · ${n}칸" + if (wishCodes[code] == null) " · 넣지 않음" else "", style = MaterialTheme.typography.bodyMedium,
                            color = if (wishCodes[code] == null) Tone.Caution.color() else MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            wishChoices.forEach { (k, label) -> FilterChip(selected = wishCodes[code] == k, onClick = { wishCodes[code] = k }, label = { Text(label) }) }
                            FilterChip(selected = wishCodes[code] == null, onClick = { wishCodes[code] = null }, label = { Text("넣지 않기") })
                        }
                    }
                }
                if (!asWishes) counts.forEach { (code, n) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("‘$code’ · ${n}칸" + when {
                            codes[code] == null -> " · 아직 비워 둠"
                            code in loose && codes[code] == guessed[code] -> " · 짐작으로 골랐어요"
                            else -> ""
                        }, style = MaterialTheme.typography.bodyMedium,
                            color = if (codes[code] == null) Tone.Caution.color() else MaterialTheme.colorScheme.onSurface)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            shifts.forEach { s -> FilterChip(selected = codes[code] == s.id, onClick = { codes[code] = s.id }, label = { Text(s.label) }) }
                            FilterChip(selected = codes[code] == null, onClick = { codes[code] = null }, label = { Text("비워 두기") })
                        }
                    }
                }
                // [RVJ-27] ③ What 되돌리기 takes back said as it is: the cells, and the new members asked about then.
                if (!asWishes) Text("이미 칠한 칸은 파일 것으로 바뀌고, 잠근 칸은 그대로예요. 넣은 뒤 마음에 안 들면 위의 되돌리기 한 번이면 칸이 모두 돌아가요." +
                    (if (newCount > 0) " 새 팀원도 그때 함께 뺄지 물어봐요." else "") +
                    if (unpaired > 0) " 비워 둔 코드 ${unpaired}가지 칸은 확인할 칸으로 남겨요." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }, confirmButton = {
            Button(enabled = pick.any { it != SKIP } && !unnamed, onClick = {
                onApply(TeamImportChoice(pick.map { if (it == NEW || it == SKIP) null else it }, pick.withIndex().filter { it.value == SKIP }.map { it.index }.toSet(), codes.toMap(),
                    wishCodes.toMap().takeIf { asWishes }, names.withIndex().filter { pick[it.index] == NEW }.associate { it.index to it.value }))
            }) { Text(if (asWishes) "신청으로 넣기" else "근무표에 넣기") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } })
}

/** How many new names the line under ① spells out. */
private const val NEW_NAMES = 5

/** [RVJ-27] ④ One line's cells as read, day by day ('1 D · 2 D · 3 E?'): '?' is a cell the reader was unsure of, '-' an empty one. */
internal fun readCells(row: TeamGridImport.Row, grid: RosterGrid): String =
    grid.days.joinToString(" · ") { d -> "${d.dayOfMonth} ${row.cells[d]?.trim()?.ifEmpty { null } ?: "-"}" + if (d in row.doubtful) "?" else "" }

/**
 * 2.0.0 [RVJ-27] 검토 PAYU-57 ③ (되돌려도 새 팀원은 남는데 안내는 '모두 돌아가요'): asked when 되돌리기 takes a 가져오기 back that added
 * members. The cells are back already; the members go only if the leader says so.
 */
@Composable
fun ImportUndoAsk(made: List<TeamMember>, onRemove: () -> Unit, onKeep: () -> Unit) {
    val who = made.take(3).joinToString(", ") { it.name } + if (made.size > 3) "님 외 ${made.size - 3}명은" else "님은"
    AlertDialog(onDismissRequest = onKeep, title = { Text("새 팀원 ${made.size}명도 뺄까요?") },
        text = { Text("가져오기를 되돌려서 칠한 칸은 돌아갔어요. 그때 더한 $who 팀원 목록에 남아 있어요.") },
        confirmButton = { TextButton(onClick = onRemove) { Text("함께 빼기") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("그대로 두기") } })
}

/** The line after 가져오기: what went in, what was kept, and where the checking starts. */
fun importSummary(a: TeamGridImport.Applied, added: Int): String = buildString {
    append("${a.written}칸을 넣었어요.")
    if (added > 0) append(" 새 팀원 ${added}명을 더했어요.")
    if (a.toCheck.isNotEmpty()) append("\n확인할 칸 ${a.toCheck.size}개에 노란 테두리를 해 두었어요. 아래 ‘확인할 칸’ 단추로 하나씩 가요.")
    if (a.keptLocked > 0) append("\n잠근 칸 ${a.keptLocked}개는 그대로 두었어요.")
    if (a.outside > 0) append("\n다른 달 날짜 ${a.outside}칸은 넣지 않았어요. 그 달로 넘어가 한 번 더 가져오면 들어가요.")
    // [RVJ-27] 검토 PAYU-57 ③: the new members are not part of 되돌리기 itself; it asks about them.
    append(if (added > 0) "\n마음에 안 들면 위의 되돌리기 한 번이면 칸이 가져오기 전으로 돌아가고, 새 팀원도 함께 뺄지 물어봐요."
        else "\n마음에 안 들면 위의 되돌리기 한 번이면 가져오기 전으로 돌아가요.")
}

/** 가져오기 entry for the board's tools. */
@Composable
fun ImportButton(onClick: () -> Unit) { OutlinedButton(onClick = onClick) { Text("파일·사진에서 가져오기") } }
