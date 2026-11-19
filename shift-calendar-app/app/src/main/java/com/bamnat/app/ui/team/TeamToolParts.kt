package com.bamnat.app.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.AppSheet

/**
 * 2.0.0 [RVJ-69] (총괄 HQ-108, 디자인 품질 조건 2·8) The 팀 근무표's month tools. Nine outlined buttons and a filled 링크 다시 보내기 stood
 * in five rows on a folded phone (Q1's picture), all of one weight. Now the month's [TeamStage] fills its one button, the stage's two
 * most used stand beside it as 글자 단추, and '더 보기' holds every tool in three groups (받기·채우기·보기), in the same place whatever the
 * stage, so a tool learnt once is found again.
 */
internal enum class TeamStage {
    /** Cells read from a file or photo are being checked: their row's 다음 칸 is the one filled button. */
    REVIEW,
    /** 신청 is open: the 신청 card's own buttons are the stage's. */
    ASKING,
    /** Not confirmed and cells are empty: 빈칸 자동 채우기. */
    FILL,
    /** Painted, not confirmed: 확정하고 팀원 링크 보내기. */
    CONFIRM,
    /** Confirmed, then changed: 바뀐 칸 n개로 다시 확정. */
    RECONFIRM,
    /** Confirmed and sent, nothing changed: no filled button (링크 다시 보내기 is a 글자 단추). */
    SENT,
}

/** One tool: what it says, whether it can be pressed now, and what it does. */
internal class TeamTool(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** The month's tools; null where a tool is not there now (신청 already open, a 바꾸기 round already open, 확정 hidden for new links). */
internal class TeamTools(
    val ask: TeamTool?, val wishes: TeamTool, val swap: TeamTool?,
    val fill: TeamTool, val copy: TeamTool, val import: TeamTool, val need: TeamTool,
    val fair: TeamTool, val members: TeamTool,
    val confirm: TeamTool?,
) {
    /** What the bar shows in [stage]: the one filled, the two beside it, and '더 보기''s groups. */
    class Layout(val filled: TeamTool?, val beside: List<TeamTool>, val groups: List<Pair<String, List<TeamTool>>>)

    fun layout(stage: TeamStage): Layout {
        val filled = when (stage) {
            TeamStage.FILL -> fill
            TeamStage.CONFIRM, TeamStage.RECONFIRM -> confirm
            TeamStage.REVIEW, TeamStage.ASKING, TeamStage.SENT -> null
        }
        // The two the leader reaches for most at that point of the month (확정 always stays outside while it is not the filled one).
        val beside = when (stage) {
            TeamStage.REVIEW -> listOfNotNull(confirm, fill)
            TeamStage.ASKING -> listOfNotNull(wishes, confirm)
            TeamStage.FILL -> listOfNotNull(copy, confirm)
            TeamStage.CONFIRM -> listOfNotNull(fair, wishes)
            TeamStage.RECONFIRM -> listOfNotNull(swap ?: fair, if (swap != null) fair else members)
            TeamStage.SENT -> listOfNotNull(swap ?: fair, confirm)
        }.filter { it !== filled }.take(2)
        val groups = listOf(
            "받기" to listOfNotNull(ask, wishes, swap),
            "채우기" to listOf(fill, copy, import, need),
            "보기" to listOf(fair, members),
        )
        return Layout(filled, beside, groups)
    }
}

/** The bar under the board: the stage's filled button (full width), its two 글자 단추 and '더 보기'. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamToolBar(stage: TeamStage, tools: TeamTools) {
    val l = tools.layout(stage)
    var more by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        l.filled?.let { t -> Button(onClick = t.onClick, enabled = t.enabled, modifier = Modifier.fillMaxWidth()) { Text(t.label) } }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            l.beside.forEach { t -> TextButton(onClick = t.onClick, enabled = t.enabled) { Text(t.label) } }
            TextButton(onClick = { more = true }) { Text("더 보기") }
        }
    }
    if (more) TeamToolSheet(l.groups) { more = false }
}

/** '더 보기': every tool of the month in its group, one 48dp row each; a row closes the sheet and does its job. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TeamToolSheet(groups: List<Pair<String, List<TeamTool>>>, onDismiss: () -> Unit) {
    AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("이달 근무표 도구", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            groups.filter { it.second.isNotEmpty() }.forEach { (name, list) ->
                Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
                list.forEach { t ->
                    Text(t.label, style = MaterialTheme.typography.bodyLarge,
                        color = if (t.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(enabled = t.enabled, role = Role.Button) { onDismiss(); t.onClick() }
                            .wrapContentHeight(Alignment.CenterVertically))
                }
            }
        }
    }
}

/**
 * After 다시 확정: the links are the same, so the members are told how the change reaches them. 2.0.0 [RVJ-98] 검토 U05-06(PAYU-15):
 * the ones whose today or tomorrow moved ([soon]) are named first with one filled 지금 알리기 (조건 2), since a phone takes up to 6 hours
 * to follow and that is too late for tomorrow's 출근. '그래도 알리기' still sends every link.
 */
@Composable
internal fun ReconfirmedDialog(version: Int, soon: List<com.bamnat.core.team.TeamReceive.Soon>, onTellSoon: () -> Unit, onSendAll: () -> Unit,
                               onClose: () -> Unit) {
    com.bamnat.app.ui.components.AlertDialog(onDismissRequest = onClose, title = { Text("${version}판으로 다시 확정했어요") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (soon.isNotEmpty()) {
                Text("오늘·내일 근무가 바뀐 ${soon.size}명", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                soon.forEach { s -> Text("${s.member.name} · ${s.days.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium) }
                Text("휴대폰에 들어가기까지 6시간이 걸릴 수 있어 지금 알려 주세요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onTellSoon, modifier = Modifier.fillMaxWidth()) { Text("${soon.size}명에게 지금 알리기") }
            }
            Text("팀원 링크는 그대로이고, 링크를 열면 바뀐 근무가 보여요. 밤낮 1.39 이상을 쓰는 팀원은 6시간 안에 바뀐 날이 자기 달력과 알람에 들어가요. " +
                "아이폰 팀원이 링크에서 '캘린더에 넣기'를 해 두었다면 그 캘린더도 몇 시간 안에 따라 바뀌어요. 그 밖의 팀원은 저절로 알지 못하니 '그래도 알리기'로 알려 주세요.",
                style = if (soon.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = if (soon.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("알겠어요") } },
        dismissButton = { TextButton(onClick = onSendAll) { Text("그래도 알리기") } })
}

