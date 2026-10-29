package com.bamnat.app.ui.pay.rulesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.pay.FormStep
import com.bamnat.app.ui.pay.Hint
import com.bamnat.app.ui.pay.ItemDraft
import com.bamnat.app.ui.pay.Label
import com.bamnat.app.ui.pay.PhotoCellsLogic
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.CellPreset
import com.bamnat.core.pay.CellPresets
import com.bamnat.core.pay.Premium
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.PresetGroup
import com.bamnat.core.pay.RuleCell
import com.bamnat.core.pay.RuleChange
import kotlinx.coroutines.delay
import kotlin.math.abs

/** [RULE-ED-03] Where the 칸 더하기 sheet starts: a new 칸 (from a sentence a photo could not read, or none), or one to change. */
sealed interface CellStart {
    data class New(val sentence: String = "") : CellStart
    data class Edit(val cell: RuleCell) : CellStart
}

/**
 * [RULE-ED-03] 1.30 AW: 칸 더하기 in two steps on one sheet (사용자 2026-10-27 "칸 추가 방식으로 적을 수 있게. 이것 또한 사용자 경험이
 * 아주 뛰어나야 한다"). 1 무엇을: a search box and the chips people use most (자격수당, 휴일연장, 기숙사비 …); a chip fills in the rest
 * (매달·사람마다·통상임금, 2배, 22~06시). 2 얼마·조건: the builder of 묶음 BC (회사 공통·사람마다·%, conditions folded) or, for a premium of
 * the law, its rate. Under it, always, what the 칸 does to this month. Replaces the full-screen builder opened over the photo's result
 * (창 위의 창) and its seven kinds to read first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CellSheet(start: CellStart, host: RuleSheetHost, onDismiss: () -> Unit, onSave: (RuleChange) -> Unit) {
    val editing = (start as? CellStart.Edit)?.cell
    val sentence = (start as? CellStart.New)?.sentence.orEmpty()
    val id = remember { editing?.id ?: ("rule-" + System.currentTimeMillis().toString(36)) }  // real clock: an id, not a time
    val first = remember {
        editing?.change?.let { c -> (c as? RuleChange.Premium) }
    }
    var premium by remember { mutableStateOf(first) }
    var draft by remember { mutableStateOf(editing?.change?.let { PhotoCellsLogic.draft(it, host.month) }) }
    var query by remember { mutableStateOf("") }
    val step2 = premium != null || draft != null
    val periodStart = host.profile.period.start(host.month)
    val d = draft
    val change: RuleChange? = premium ?: d?.madeOrAsked(periodStart)?.let { PhotoCellsLogic.change(it, host.shiftNames, host.month) }
    val effect by produceState<CellEffect?>(null, change) {
        value = null
        val c = change ?: return@produceState
        delay(200)
        value = host.effect?.invoke(c)
    }
    fun pick(p: CellPreset) {
        if (p.isPremium) premium = p.premium() else draft = ItemDraft.ofPreset(p, id).also { it.source = sentence }
    }

    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!step2) {
                    Text("무엇을 더할까요?", style = MaterialTheme.typography.titleLarge)
                    if (sentence.isNotBlank()) SourceNote(sentence)
                    // [PHOTO-32] The sentence's own 수당 first, so '직접 넣기' is one tap more, never the sentence as a name.
                    val guess = remember(sentence) { CellPresets.inSentence(sentence) }
                    val named = remember(sentence) { if (guess == null) CellPresets.nameIn(sentence) else null }
                    if (guess != null || named != null) {
                        val label = guess?.name ?: named!!
                        FilledTonalButton(onClick = { if (guess != null) pick(guess) else draft = ItemDraft.ofName(named!!, id).also { it.source = sentence } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("‘$label’(으)로 시작하기") }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(20) },
                        label = { Text("이름으로 찾거나 새로 적기") },
                        placeholder = { Text("예: 자격수당, 휴일연장, 기숙사비") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    val shown = CellPresets.search(query)
                    PresetGroup.entries.forEach { g ->
                        val chips = shown.filter { it.group == g }
                        if (chips.isEmpty()) return@forEach
                        Label(g.label)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chips.forEach { p -> FilterChip(selected = false, onClick = { pick(p) }, label = { Text(p.name) }, modifier = Modifier.heightIn(min = 48.dp)) }
                        }
                    }
                    val typed = query.trim()
                    if (typed.length >= 2 && CellPresets.named(typed) == null) {
                        Button(onClick = { draft = ItemDraft.ofName(typed, id).also { it.source = sentence } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("‘$typed’ 새로 만들기")
                        }
                    } else if (shown.isEmpty()) Hint("찾는 이름이 없어요. 두 글자 넘게 적으면 그 이름으로 만들어요.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (editing == null) IconButton(onClick = { premium = null; draft = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "다른 것 고르기") }
                        Text(if (editing != null) "‘${editing.name}’ 고치기" else "얼마·언제 주나요?", style = MaterialTheme.typography.titleLarge)
                    }
                    if (sentence.isNotBlank()) SourceNote(sentence)
                    val p = premium
                    if (p != null) PremiumForm(p) { premium = it }
                    else d?.kind?.let { k ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FormStep(d, k, host.snapshot, gap = null, onChangeKind = null, fromCompany = host.companyWay ?: false, kindChips = true)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (step2) EffectLine(host, change, effect, asked = (change as? RuleChange.Item)?.asked == true,
                label = if (editing != null) "고치기" else "더하기") { change?.let(onSave) }
        }
    }
}

/** [PHOTO-32] The sentence a 칸 is made from, kept with it. */
@Composable
private fun SourceNote(sentence: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("사진에서 읽은 문장", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("“$sentence”", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * [PHOTO-32] A premium of the law as the company pays it: which work, and how much on top of the hour (1.5배면 50%). The same words
 * as its card ([RuleCell.premiumHow]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumForm(change: RuleChange.Premium, onChange: (RuleChange.Premium) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("어떤 근무인가요?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Triple(PremiumTarget.OVERTIME, false, "연장"), Triple(PremiumTarget.NIGHT, false, "야간"), Triple(PremiumTarget.HOLIDAY, false, "휴일"),
                Triple(PremiumTarget.HOLIDAY, true, "휴일 8시간 넘게")).forEach { (t, over, label) ->
                FilterChip(selected = change.target == t && change.over8h == over, onClick = { onChange(change.copy(target = t, over8h = over)) },
                    label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        PremiumRate(change, onChange)
    }
}

/** [PHOTO-32] Just the rate, as the card opens it in place. */
@Composable
internal fun PremiumRate(change: RuleChange.Premium, onChange: (RuleChange.Premium) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("시급에 몇 %를 더 주나요?")
        com.bamnat.app.ui.components.QuickDecimalChips(
            value = change.premium * 100, onChange = { onChange(change.copy(premium = it / 100)) }, name = "${change.target.label} 가산",
            quick = if (change.target == PremiumTarget.NIGHT) listOf(30.0, 50.0, 100.0) else listOf(50.0, 100.0, 150.0),
            step = 5.0, range = 0.0..(Premium.MAX_RATE * 100), unit = "%",
        )
        Text("= ${RuleCell.premiumHow(change)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Hint("1.5배면 50%, 2배면 100%예요.")
    }
}

/** [RULE-ED-03][RULE-ED-06] Under the sheet: what the 칸 does to this month, and the one button. */
@Composable
private fun EffectLine(host: RuleSheetHost, change: RuleChange?, effect: CellEffect?, asked: Boolean, label: String, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text("${host.month.monthValue}월 급여에", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    asked -> Text("사람마다 달라요. 금액은 급여 화면에서 넣어요", style = MaterialTheme.typography.titleSmall)
                    change == null -> Text("금액이나 %를 넣으면 바로 계산해요", style = MaterialTheme.typography.titleSmall)
                    effect != null -> Text(
                        (if (effect.amount >= 0) "+" else "−") + won(abs(effect.amount)) + (effect.times?.let { " · ${it}번" } ?: ""),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (effect.amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    host.effect == null -> Text("적용하면 급여에 들어가요", style = MaterialTheme.typography.titleSmall)
                    else -> Text("계산하는 중", style = MaterialTheme.typography.titleSmall)
                }
            }
            Button(onClick = onSave, enabled = change != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) }
        }
    }
}
