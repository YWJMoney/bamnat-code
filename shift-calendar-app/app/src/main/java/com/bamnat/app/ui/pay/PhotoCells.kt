package com.bamnat.app.ui.pay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Premium
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import java.time.LocalDate
import java.time.YearMonth

/**
 * [PHOTO-06] 1.29 AQ: the rules read from a rules photo as cells the user ticks, changes or takes out before anything is
 * saved, the sentences it could not read as '직접 넣기' rows, and '칸 더하기' for a rule the photo did not have. A cell is
 * made and changed with the one builder ([CustomItemDialog]): kind, basis, rate, amount and conditions (근속·한도·기간).
 */
@Composable
internal fun RuleCells(
    ticks: SnapshotStateList<Pair<RuleFinding, Boolean>>,
    unread: SnapshotStateList<String>,
    target: RuleTarget,
    month: YearMonth,
) {
    var editor by remember { mutableStateOf<CellEdit?>(null) }
    val shiftNames = target.shiftTypes.associate { it.id to it.name }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (ticks.isNotEmpty()) Text("회사 계산 방법에서 읽은 규칙 ${ticks.size}개", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        ticks.forEachIndexed { i, (f, on) ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { ticks[i] = f to !on }, verticalAlignment = Alignment.Top) {
                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(f.change.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("“${f.sourceLine}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (f.confidence < 0.6) Text("확실하지 않아요. 맞는지 봐 주세요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (PhotoCellsLogic.editable(f.change)) IconButton(onClick = { editor = CellEdit.Change(i) }) { Icon(Icons.Filled.Edit, "‘${f.change.summary}’ 고치기") }
                IconButton(onClick = { ticks.removeAt(i) }) { Icon(Icons.Filled.Close, "‘${f.change.summary}’ 빼기") }
            }
        }
        if (unread.isNotEmpty()) {
            Text("읽지 못한 문장 ${unread.size}개", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text("규칙 같은데 앱이 알아듣지 못했어요. ‘직접 넣기’로 칸을 만들면 계산에 들어가요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            unread.forEachIndexed { i, s ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(Modifier.padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("“$s”", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editor = CellEdit.New(s, i) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("직접 넣기") }
                        IconButton(onClick = { unread.removeAt(i) }) { Icon(Icons.Filled.Close, "이 문장 넘기기") }
                    }
                }
            }
        }
        com.bamnat.app.ui.components.AddButton("칸 더하기 (사진에 없는 규칙)", onClick = { editor = CellEdit.New("", -1) })
    }

    val profile = target.profile ?: PhotoCellsLogic.blank
    val snapshot = target.snapshot ?: ScheduleSnapshot(target.shiftTypes, emptyList(), emptyList())
    val effect: suspend (PayProfile, ScheduleSnapshot, YearMonth, MadeItem) -> ItemEffect? = target.effectOf ?: { _, _, _, _ -> null }
    when (val e = editor) {
        null -> Unit
        is CellEdit.New -> CustomItemDialog(profile, snapshot, month, onDismiss = { editor = null }, keepRule = false, startName = e.sentence,
            effectOf = effect, onSave = { made ->
                ticks += RuleFinding(e.sentence.ifEmpty { "직접 넣은 칸" }, PhotoCellsLogic.change(made, shiftNames, month), 1.0) to true
                if (e.unreadIndex in unread.indices) unread.removeAt(e.unreadIndex)
                editor = null
            })
        is CellEdit.Change -> {
            val f = ticks.getOrNull(e.index)
            when (val ch = f?.first?.change) {
                is RuleChange.Premium -> PremiumCellDialog(ch, onDismiss = { editor = null }) { next ->
                    ticks[e.index] = f.first.copy(change = next, confidence = 1.0) to true
                    editor = null
                }
                null -> editor = null
                else -> {
                    val (rule, item) = PhotoCellsLogic.toEdit(ch, month) ?: (null to null)
                    if (rule == null && item == null) editor = null
                    else CustomItemDialog(profile, snapshot, month, onDismiss = { editor = null }, editing = rule, editingItem = item, effectOf = effect,
                        onSave = { made ->
                            ticks[e.index] = f.first.copy(change = PhotoCellsLogic.change(made, shiftNames, month), confidence = 1.0) to true
                            editor = null
                        })
                }
            }
        }
    }
}

private sealed interface CellEdit {
    data class Change(val index: Int) : CellEdit
    data class New(val sentence: String, val unreadIndex: Int) : CellEdit
}

/** [PHOTO-06] A premium read from the photo, its rate changed: "연장 가산 50%" → 70%. */
@Composable
private fun PremiumCellDialog(change: RuleChange.Premium, onDismiss: () -> Unit, onSave: (RuleChange.Premium) -> Unit) {
    var pct by remember { mutableStateOf(change.premium * 100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${change.target.label} 가산 고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("통상시급에 더하는 몫이에요. 1.5배면 50%, 2배면 100%예요.", style = MaterialTheme.typography.bodySmall)
                com.bamnat.app.ui.components.QuickDecimalChips(value = pct, onChange = { pct = it }, name = "${change.target.label} 가산",
                    quick = listOf(30.0, 50.0, 100.0, 150.0), step = 5.0, range = 0.0..(Premium.MAX_RATE * 100), unit = "%")
            }
        },
        confirmButton = { Button(onClick = { onSave(change.copy(premium = pct / 100)) }) { Text("고치기") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** [PHOTO-06] Turning a rule read from a photo into the builder's cell and back. */
internal object PhotoCellsLogic {
    /** A setup to work a cell out on when the screen has none: 통상시급 is not known, so percent cells show no amount. */
    val blank = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(emptyList()))

    fun editable(change: RuleChange): Boolean = when (change) {
        is RuleChange.Premium -> true
        is RuleChange.Custom -> ItemKind.of(change.rule) != null
        is RuleChange.Allowance -> change.cadence in setOf(Cadence.MONTHLY, Cadence.PER_WORK_DAY, Cadence.PER_SHIFT_TYPE)
        else -> false
    }

    /** The rule or pay item the builder edits [change] as; both null when it cannot. */
    fun toEdit(change: RuleChange, month: YearMonth): Pair<CustomRule?, PayItem?>? = when (change) {
        is RuleChange.Custom -> change.rule to null
        is RuleChange.Allowance -> when (change.cadence) {
            Cadence.MONTHLY, Cadence.PER_WORK_DAY -> null to PayItem("cell-" + change.name.hashCode().toUInt().toString(16), change.name,
                ValueTimeline(listOf(DatedValue(month.atDay(1), change.amount))), change.cadence, ordinary = change.ordinary ?: false)
            Cadence.PER_SHIFT_TYPE -> CustomRule("cell-" + change.name.hashCode().toUInt().toString(16), change.name.take(CustomRule.MAX_NAME),
                RuleWhen(shiftTypeIds = change.shiftTypeIds), RulePay.PerTime(change.amount)) to null
            else -> null
        }
        else -> null
    }

    /** What the builder made, as the change the photo's result applies. */
    fun change(made: MadeItem, shiftNames: Map<Long, String>, month: YearMonth): RuleChange = when (made) {
        is MadeItem.Rule -> RuleChange.Custom(made.rule, shiftNames)
        is MadeItem.Item -> RuleChange.Allowance(made.item.name, made.item.amount.on(LocalDate.of(month.year, month.monthValue, 1)).value,
            made.item.cadence, made.item.shiftTypeIds, made.item.ordinary)
    }
}
