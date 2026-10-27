package com.bamnat.app.ui.pay.rulesheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.pay.Hint
import com.bamnat.app.ui.pay.Label
import com.bamnat.app.ui.pay.MoneyField
import com.bamnat.app.ui.pay.PhotoCellsLogic
import com.bamnat.app.ui.pay.Toggle
import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.CellFrom
import com.bamnat.core.pay.CellKind
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.ItemShares
import com.bamnat.core.pay.RuleCell
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.ValueTimeline
import java.time.LocalTime

/**
 * [RULE-ED-02][PHOTO-32] The sheet's 칸 as cards on a screen that holds a [RuleSheetState]: ticked on or off, opened where they are to
 * change, taken out; '직접 넣기' for a sentence not read; '칸 더하기' at the end. New 칸 say they were put in by hand.
 */
@Composable
internal fun RuleSheetCells(state: RuleSheetState, host: RuleSheetHost, modifier: Modifier = Modifier, empty: String? = null) {
    val sheet = state.sheet
    RuleCellList(
        cells = sheet.cells, unread = sheet.unread, host = host, modifier = modifier,
        onToggle = state::toggle, onPut = state::put, onRemove = state::remove, onUnreadDone = state::dropUnread,
        onAdd = { change, source ->
            state.update { s -> s.add(RuleCell.of(change, source = source, from = CellFrom.HAND, shiftNames = host.shiftNames, salt = s.cells.size)) }
        },
        empty = empty,
    )
}

/**
 * [RULE-ED-02] The 칸 as cards. [onToggle] null shows no tick ([ticks] picks which do, as 회사 규칙 shows a switch on its own rules
 * only). A card opens in place ([PHOTO-31]: no window over the screen); '조건까지 고치기' and '칸 더하기' are one sheet ([CellSheet]).
 */
@Composable
internal fun RuleCellList(
    cells: List<RuleCell>,
    unread: List<String>,
    host: RuleSheetHost,
    onToggle: ((RuleCell) -> Unit)?,
    onPut: (RuleCell) -> Unit,
    onRemove: (RuleCell) -> Unit,
    onUnreadDone: (Int) -> Unit,
    onAdd: (RuleChange, String) -> Unit,
    modifier: Modifier = Modifier,
    ticks: (RuleCell) -> Boolean = { true },
    addLabel: String = "칸 더하기",
    empty: String? = null,
) {
    var open by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<CellStart?>(null) }
    // The '직접 넣기' row a new 칸 comes from, taken off once it is made.
    var fromUnread by remember { mutableStateOf(-1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cells.isEmpty() && unread.isEmpty() && empty != null) Hint(empty)
        cells.forEach { cell ->
            CellCard(
                cell, open = open == cell.id, host = host,
                onOpen = { open = if (open == cell.id) null else cell.id },
                onToggle = onToggle?.takeIf { ticks(cell) }?.let { t -> { t(cell) } },
                onChange = { onPut(cell.copy(change = it, confidence = 1.0, on = true)) },
                onMore = if (cell.change is RuleChange.Premium || PhotoCellsLogic.editable(cell.change)) ({ sheet = CellStart.Edit(cell) }) else null,
                onRemove = { onRemove(cell); if (open == cell.id) open = null },
            )
        }
        if (unread.isNotEmpty()) {
            Text("읽지 못한 문장 ${unread.size}개", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Hint("규칙 같은데 앱이 알아듣지 못했어요. ‘직접 넣기’로 칸을 만들면 계산에 들어가요.")
            unread.forEachIndexed { i, s ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("“$s”", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { fromUnread = i; sheet = CellStart.New(s) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("직접 넣기") }
                        IconButton(onClick = { onUnreadDone(i) }) { Icon(Icons.Filled.Close, "이 문장 넘기기") }
                    }
                }
            }
        }
        OutlinedButton(onClick = { fromUnread = -1; sheet = CellStart.New() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Text("  $addLabel")
        }
    }
    sheet?.let { start ->
        CellSheet(start, host, onDismiss = { sheet = null }) { change ->
            when (start) {
                is CellStart.Edit -> onPut(start.cell.copy(change = change, confidence = 1.0, on = true))
                is CellStart.New -> {
                    onAdd(change, start.sentence)
                    if (fromUnread >= 0) onUnreadDone(fromUnread)
                }
            }
            fromUnread = -1
            sheet = null
        }
    }
}

/**
 * [PHOTO-32] One 칸: its name, how much in plain words, and its conditions as small tags; a 칸 read badly is tinted and says what the
 * photo had. A tap opens it in place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CellCard(
    cell: RuleCell, open: Boolean, host: RuleSheetHost,
    onOpen: () -> Unit, onToggle: (() -> Unit)?, onChange: (RuleChange) -> Unit, onMore: (() -> Unit)?, onRemove: () -> Unit,
) {
    val unsure = !cell.sure && cell.from != CellFrom.SAVED
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = when { unsure -> colors.tertiaryContainer.copy(alpha = 0.55f); !cell.on -> colors.surfaceContainerLowest; else -> colors.surfaceContainerLow },
        border = if (unsure) BorderStroke(1.dp, colors.tertiary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "접기" else "그 자리에서 고치기") { onOpen() }
                    .heightIn(min = 56.dp).padding(start = if (onToggle != null) 0.dp else 14.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggle != null) Checkbox(checked = cell.on, onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = "‘${cell.name}’ ${if (cell.on) "넣어요" else "빼고 적용해요"}" })
                Column(Modifier.weight(1f)) {
                    Text(cell.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (cell.on) colors.onSurface else colors.onSurfaceVariant)
                    if (cell.how.isNotEmpty()) Text(cell.how, style = MaterialTheme.typography.bodyMedium, color = if (cell.on) colors.primary else colors.onSurfaceVariant)
                    val tags = listOf(cell.whenText, cell.limits, if (cell.ordinary == true) "통상임금" else "", if (cell.kind == CellKind.DEDUCT) "빠지는 돈" else "",
                        cell.from.label).filter { it.isNotBlank() }
                    if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp)) { tags.forEach { Tag(it) } }
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = colors.onSurfaceVariant)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "‘${cell.name}’ 빼기") }
            }
            if (unsure) Text(
                "확실하지 않아요. 맞는지 봐 주세요." + if (cell.source.isNotBlank()) " 사진에서 “${cell.source}”로 읽었어요." else "",
                style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            ) else if (cell.source.isNotBlank() && cell.from != CellFrom.HAND) Text("“${cell.source}”", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp))
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InlineEdit(cell.change, host, onChange)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onMore != null) TextButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("조건까지 자세히 고치기") }
                        TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("다 됐어요") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/**
 * [PHOTO-31][PHOTO-32] The one value a 칸 is mostly changed for, right in the card: a premium's %, an allowance's amount and who sets
 * it, a rule's amount or %, the night hours. Conditions (근무·요일·근속·한도) are the sheet's ('조건까지 자세히 고치기').
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineEdit(change: RuleChange, host: RuleSheetHost, onChange: (RuleChange) -> Unit) {
    val start = host.profile.period.start(host.month)
    fun once(v: Long?) = if (v == null || v <= 0) ValueTimeline() else ValueTimeline(listOf(DatedValue(start, v)))
    when (change) {
        is RuleChange.Premium -> PremiumRate(change) { onChange(it) }
        is RuleChange.Item -> {
            val item = change.item
            if (item.isPercent) {
                Percent("${item.percentOf.label}의", item.percent ?: 0.0, listOf(1.0, 5.0, 10.0, 15.0, 20.0), 0.5..ItemShares.MAX_PERCENT * 100) {
                    onChange(RuleChange.Item(item.copy(percent = it)))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BonusBasis.entries.forEach { b ->
                        FilterChip(selected = item.percentOf == b, onClick = { onChange(RuleChange.Item(item.copy(percentOf = b))) }, label = { Text(b.label) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            } else {
                Label("금액은 누가 정하나요?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ItemShare.FIXED, ItemShare.PERSONAL).forEach { s ->
                        FilterChip(selected = item.share == s, onClick = { onChange(RuleChange.Item(item.copy(share = s))) }, label = { Text(s.label) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                MoneyField("${item.cadence.label} 금액" + if (item.share == ItemShare.PERSONAL) " (모르면 비워 두세요)" else "", change.amount,
                    { v -> onChange(RuleChange.Item(item.copy(amount = once(v)))) })
                if (change.asked) Hint("비워 두면 급여 화면이 ‘${item.name} 금액’을 물어요. 같은 회사 분들도 각자 넣어요.")
            }
            Toggle("통상임금에 넣어요", "매달 모두에게 같은 수당이면 켜요. 연장·야간 수당도 함께 올라가요.", item.ordinary) {
                onChange(RuleChange.Item(item.copy(ordinary = it)))
            }
        }
        is RuleChange.Allowance -> {
            MoneyField("${change.cadence.label} 금액", change.amount, { v -> onChange(change.copy(amount = v ?: 0)) })
            Toggle("통상임금에 넣어요", "매달 모두에게 같은 수당이면 켜요.", change.ordinary == true) { onChange(change.copy(ordinary = it)) }
        }
        is RuleChange.Custom -> {
            val rule = change.rule
            fun pay(p: RulePay) = onChange(change.copy(rule = rule.copy(pay = p)))
            when (val p = rule.pay) {
                is RulePay.PerTime -> MoneyField("1회 금액", p.amount, { v -> v?.takeIf { it > 0 }?.let { pay(RulePay.PerTime(it)) } })
                is RulePay.PerHour -> MoneyField("1시간 금액", p.amount, { v -> v?.takeIf { it > 0 }?.let { pay(RulePay.PerHour(it)) } })
                is RulePay.Monthly -> MoneyField("한 달 금액", p.amount, { v -> v?.takeIf { it > 0 }?.let { pay(RulePay.Monthly(it)) } })
                is RulePay.OfHourly -> Percent("통상시급에 더하는 몫", p.rate, listOf(25.0, 50.0, 100.0), 1.0..1000.0) { pay(RulePay.OfHourly(it)) }
                is RulePay.OfBaseMonthly -> Percent("매달 기본급의", p.share, listOf(1.0, 5.0, 10.0), 0.5..500.0) { pay(RulePay.OfBaseMonthly(it)) }
                is RulePay.OfOrdinaryMonthly -> Percent("매달 통상임금의", p.share, listOf(1.0, 5.0, 10.0), 0.5..500.0) { pay(RulePay.OfOrdinaryMonthly(it)) }
                is RulePay.OfBase -> Percent("기본급의", p.share, listOf(5.0, 10.0, 15.0), 0.5..100.0) { pay(p.copy(share = it)) }
            }
        }
        is RuleChange.Bonus -> change.yearlyPercent?.let { y ->
            Label("1년에 기본급의 몇 %인가요?")
            com.bamnat.app.ui.components.QuickDecimalChips(value = y, onChange = { onChange(change.copy(yearlyPercent = it)) }, name = "상여 연 %",
                quick = listOf(400.0, 600.0, 800.0), step = 50.0, range = 50.0..2000.0, unit = "%")
        } ?: Hint("상여 금액은 급여 화면에서 고쳐요.")
        is RuleChange.NightWindow -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            com.bamnat.app.ui.components.CountStepper(change.start.hour, { onChange(change.copy(start = LocalTime.of(it, 0))) }, name = "야간 시작",
                range = 0..23, wrap = true, format = { "${it}시" }, valueWidth = 56.dp)
            Text("~")
            com.bamnat.app.ui.components.CountStepper(change.end.hour, { onChange(change.copy(end = LocalTime.of(it, 0))) }, name = "야간 끝",
                range = 0..23, wrap = true, format = { "${it}시" }, valueWidth = 56.dp)
        }
        else -> Hint("이 칸은 켜고 끄기만 해요. 다르면 빼고 ‘칸 더하기’로 새로 넣어 주세요.")
    }
}

/** A share in %, the usual ones as chips ([v] 0.5 = 50%). */
@Composable
private fun Percent(label: String, v: Double, quick: List<Double>, range: ClosedFloatingPointRange<Double>, onChange: (Double) -> Unit) {
    Label(label)
    com.bamnat.app.ui.components.QuickDecimalChips(value = v * 100, onChange = { onChange(it / 100) }, name = label, quick = quick,
        step = 0.5, range = range, unit = "%")
}
