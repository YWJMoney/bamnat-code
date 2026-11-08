package com.bamnat.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.TabPick
import com.bamnat.app.ui.TabPicks
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.core.layout.TabSlots
import com.bamnat.core.work.WorkStyle

/** [NAV-07] 1.26 AD, [NAV2-03] 1.39: the row's name, which settings search and help open. */
const val TAB_ORDER_ROW = "아래 메뉴 채우기·순서"

/** [NAV2-03] What the row says now: the screen the app opens on, then the places in order (a 묶음 by its name). */
internal fun tabOrderLine(settings: AppSettings): String {
    val slots = TabSlots.resolve(settings.tabSlots, settings.tabOrder, TabPicks.known)
    val home = TabPicks.of(TabSlots.home(slots), settings.workStyle)?.label ?: "달력"
    val names = slots.map { s ->
        when (s) {
            is TabSlots.One -> TabPicks.of(s.key, settings.workStyle)?.label ?: s.key
            is TabSlots.Group -> "${s.name}(${s.members.size})"
        }
    }
    return "앱을 켜면 $home · " + names.joinToString(", ") + ". 칸마다 원하는 화면을 두고, 여러 화면을 묶을 수 있어요"
}

/** [NAV-07][NAV2-03] 설정 › 화면과 글자 › 아래 메뉴 채우기·순서: the bar's editor, the way to it without a long press (GestureAlternativesTest). */
@Composable
internal fun TabOrderRow(settings: AppSettings) {
    NavRow(TAB_ORDER_ROW, tabOrderLine(settings)) { com.bamnat.app.ui.BarEditLinks.ask() }
}

/** What the screen picker is choosing for: the place at [index] alone, or one more screen in it (a new place past the end). */
private data class PickFor(val index: Int, val add: Boolean)

/**
 * [NAV2-03] 1.39 (사용자 2026-11-07 "메인 5칸을 사용자가 원하는 기능들로 채울 수 있게, 중첩 탭"): the bar's five places, each
 * holding one screen or a 묶음 of several. A place is changed, given one more screen (it becomes a 묶음, which opens a list
 * when tapped), renamed, moved up or down or taken out; the bar changes at once behind the dialog. The first tab in it is the
 * app's first screen (D82). 처음대로 puts back the five tabs.
 */
@Composable
internal fun BarSlotsDialog(
    settings: AppSettings,
    style: WorkStyle?,
    manageOn: Boolean,
    onChange: (List<TabSlots.Slot>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    /** 1.39 [NAV2-07] Opened beside the rail (an unfolded phone): two more places, up to [TabSlots.MAX_WIDE]. */
    wide: Boolean = false,
) {
    val slots = TabSlots.resolve(settings.tabSlots, settings.tabOrder, TabPicks.known)
    val cap = if (wide) TabSlots.MAX_WIDE else TabSlots.MAX
    val haptics = LocalHapticFeedback.current
    var picking by remember { mutableStateOf<PickFor?>(null) }
    // The 묶음 being named, by a screen in it (its place can move as a screen leaves another place).
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val change: (List<TabSlots.Slot>) -> Unit = { made ->
        if (made != slots) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onChange(made)
        }
    }
    val label: (String) -> String = { k -> TabPicks.of(k, style)?.label ?: k }
    val home = label(TabSlots.home(slots))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TAB_ORDER_ROW) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 1.39 PC NAV2 (안내 글이 길어 목록이 밀려요): one line, the rest folded.
                var more by rememberSaveable { mutableStateOf(false) }
                Text(
                    "칸 이름을 누르면 바꾸고, ⋮로 여러 화면을 묶어요." + if (more) " 묶은 칸을 누르면 목록이 떠서 바로 가요. " +
                        "앱은 맨 앞 탭($home)으로 열려요. 막대에 없는 탭과 설정은 화면 위 ‘나’ 단추에 있어요." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!more) Text("자세히", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { more = true }.padding(vertical = 6.dp))
                Spacer(Modifier.height(8.dp))
                slots.forEachIndexed { i, slot ->
                    // 1.39 [NAV2-07] The places past five show only beside the rail of an unfolded phone; folded, they wait by 나.
                    if (i == TabSlots.MAX) Text(
                        if (wide) "아래 두 칸은 펼친 화면 옆 막대에만 보여요. 접으면 ‘나’ 단추에 있어요." else "아래 칸은 펼친 화면 옆 막대에만 보여요. 지금은 ‘나’ 단추에 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    else if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SlotLine(
                        index = i, count = slots.size, slot = slot, style = style, isHome = TabSlots.home(slots) in slot.keys,
                        onMove = { by -> change(TabSlots.move(slots, i, i + by)) },
                        onReplace = { picking = PickFor(i, add = false) },
                        onAdd = { picking = PickFor(i, add = true) },
                        onRename = { renaming = slot.keys.first() },
                        onRemove = { change(TabSlots.remove(slots, i)) },
                        onRemoveMember = { k -> change(TabSlots.without(slots, k)) },
                        onMoveMember = { from, to -> change(TabSlots.moveMember(slots, i, from, to)) },
                    )
                }
                if (slots.size < cap) {
                    TextButton(onClick = { picking = PickFor(slots.size, add = true) }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Text(" 칸 더하기 (${slots.size}/$cap)" + if (wide && slots.size >= TabSlots.MAX) " · 펼친 화면만" else "")
                    }
                }
                // 1.39 PC NAV2 (150%·200%에서 '처음대로'가 창 밖으로 잘림): in the list, not beside 완료.
                TextButton(onClick = onReset, enabled = settings.tabSlots.isNotEmpty() || settings.tabOrder.isNotEmpty()) { Text("처음대로 (다섯 탭)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
        dismissButton = {
            // [RCX-12] 1.35 화면 배치 piece: this bar (and the calendar's cards) as a recipe.
            com.bamnat.app.ui.recipe.ShareThisSetting(com.bamnat.core.recipe.PieceKind.LAYOUT)
        },
    )
    picking?.let { p ->
        val at = slots.getOrNull(p.index)
        ScreenPicker(
            title = when {
                at == null -> "새 칸에 둘 화면"
                p.add && at is TabSlots.Group -> "‘${at.name}’에 더할 화면"
                p.add -> label(at.keys.single()).let { "‘$it’${com.bamnat.core.text.Josa.gwawa(it)} 묶을 화면" }
                else -> "${p.index + 1}번 칸에 둘 화면"
            },
            picks = TabPicks.all(style, manageOn).filter { at == null || it.key !in at.keys },
            slots = slots,
            style = style,
            // 1.39 PC NAV2 (빼면 마을이 어디로 가는지 안 보여요): a tab taken off the bar waits by the 나 button.
            note = (at as? TabSlots.One)?.takeIf { !p.add }?.let { one ->
                "지금: ${label(one.key)}" + if (TabPicks.topOf(one.key) != null) " · 바꾸면 ${label(one.key)}${com.bamnat.core.text.Josa.eunneun(label(one.key))} 화면 위 ‘나’에서 열어요" else ""
            },
            onPick = { k ->
                picking = null
                // 1.39 PC NAV2 2차 (기본 이름 '모음'): a new 묶음 starts named for its two screens, which the name box then shows.
                val name = (at as? TabSlots.One)?.let { TabSlots.cleanName(label(it.key) + "·" + label(k)) } ?: TabSlots.DEFAULT_GROUP_NAME
                change(if (p.add) TabSlots.addTo(slots, p.index, k, name, max = cap) else TabSlots.replace(slots, p.index, k))
                // A new 묶음 is named at once, so it does not stay '모음'.
                if (p.add && at is TabSlots.One) renaming = k
            },
            onDismiss = { picking = null },
        )
    }
    renaming?.let { k ->
        // Shown once the bar holds the 묶음 (the change is saved a moment after the tap).
        val i = slots.indexOfFirst { it is TabSlots.Group && k in it.keys }
        val group = slots.getOrNull(i) as? TabSlots.Group
        if (group != null) GroupNameDialog(group.name, onDone = { n -> renaming = null; change(TabSlots.rename(slots, i, n)) }, onDismiss = { renaming = null })
    }
}

@Composable
private fun SlotLine(
    index: Int,
    count: Int,
    slot: TabSlots.Slot,
    style: WorkStyle?,
    isHome: Boolean,
    onMove: (Int) -> Unit,
    onReplace: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onMoveMember: (Int, Int) -> Unit,
) {
    val first = index == 0
    val last = index == count - 1
    val picks = slot.keys.mapNotNull { TabPicks.of(it, style) }
    val name = when (slot) {
        is TabSlots.One -> picks.firstOrNull()?.label ?: slot.key
        is TabSlots.Group -> slot.name
    }
    var menu by remember { mutableStateOf(false) }
    val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics(mergeDescendants = true) {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction("위로") { onMove(-1); true }.takeIf { !first },
                        CustomAccessibilityAction("아래로") { onMove(1); true }.takeIf { !last },
                        CustomAccessibilityAction("다른 화면으로 바꾸기") { onReplace(); true },
                        CustomAccessibilityAction("화면 더해 묶기") { onAdd(); true }.takeIf { slot.keys.size < TabSlots.MAX_IN_GROUP },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
            Icon(picks.firstOrNull()?.icon ?: TabPicks.groupIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(onClick = onReplace)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (slot is TabSlots.Group) Tag("묶음 ${slot.members.size}개", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    if (isHome) Tag("첫 화면", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            // 1.39 PC NAV2 (150%·200%에서 칸 이름이 '가/계/부'로 세로로 섬): at large text the arrows wait in ⋮.
            if (!bigText) {
                IconButton(onClick = { onMove(-1) }, enabled = !first) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "$name 위로") }
                IconButton(onClick = { onMove(1) }, enabled = !last) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "$name 아래로") }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "$name 고치기") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (bigText && !first) DropdownMenuItem(text = { Text("앞으로 옮기기") }, onClick = { menu = false; onMove(-1) })
                    if (bigText && !last) DropdownMenuItem(text = { Text("뒤로 옮기기") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("다른 화면으로 바꾸기") }, onClick = { menu = false; onReplace() })
                    if (slot.keys.size < TabSlots.MAX_IN_GROUP) {
                        DropdownMenuItem(text = { Text(if (slot is TabSlots.Group) "이 묶음에 화면 더하기" else "화면 더해 묶음 만들기") }, onClick = { menu = false; onAdd() })
                    }
                    if (slot is TabSlots.Group) DropdownMenuItem(text = { Text("묶음 이름 바꾸기") }, onClick = { menu = false; onRename() })
                    if (count > 1) DropdownMenuItem(text = { Text("이 칸 빼기") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        // A 묶음's screens, in the order its list shows them, each movable and removable.
        if (slot is TabSlots.Group) {
            picks.forEachIndexed { m, p ->
                Row(
                    Modifier.fillMaxWidth().padding(start = if (bigText) 24.dp else 52.dp).heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(p.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (m > 0) IconButton(onClick = { onMoveMember(m, m - 1) }) { Icon(Icons.Filled.KeyboardArrowUp, "${p.label} 묶음에서 위로", Modifier.size(20.dp)) }
                    IconButton(onClick = { onRemoveMember(p.key) }) { Icon(Icons.Outlined.Close, "${p.label} 묶음에서 빼기", Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Surface(color = fill, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * [NAV2-03] Every screen a place can hold, by kind: the ones for this calendar's kind of work first, the rest under
 * '다른 일의 화면' (never hidden). A screen already in a place says which, and moves from there.
 */
@Composable
private fun ScreenPicker(title: String, picks: List<TabPick>, slots: List<TabSlots.Slot>, style: WorkStyle?, note: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val where: (String) -> String? = { k -> slots.indexOfFirst { k in it.keys }.takeIf { it >= 0 }?.let { "${it + 1}번 칸에서 옮겨요" } }
    val fit = picks.filter { it.fits }
    val other = picks.filter { !it.fits }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                fit.groupBy { it.kind }.forEach { (kind, list) ->
                    PickHeading(TabPicks.kindName(kind, style))
                    list.forEach { p -> PickLine(p, where(p.key), onPick) }
                }
                if (other.isNotEmpty()) {
                    PickHeading("다른 일의 화면")
                    other.forEach { p -> PickLine(p, where(p.key), onPick) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun PickHeading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun PickLine(p: TabPick, where: String?, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).clickable { onPick(p.key) }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.label, style = MaterialTheme.typography.bodyLarge)
            where?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun GroupNameDialog(start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(start) }
    val ok = TabSlots.cleanName(name).isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("묶음 이름") },
        text = {
            Column {
                Text("아래 막대에 이 이름으로 보여요(${TabSlots.NAME_MAX}자까지).", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(TabSlots.NAME_MAX) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (ok) onDone(name) }),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Names that come up most, one tap each.
                    listOf("돈", "잠·쉼", "일", "함께").forEach { s ->
                        androidx.compose.material3.SuggestionChip(onClick = { name = s }, label = { Text(s) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = ok) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
