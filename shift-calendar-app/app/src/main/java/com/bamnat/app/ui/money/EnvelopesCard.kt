package com.bamnat.app.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.money.Envelope
import com.bamnat.core.money.EnvelopeKind
import com.bamnat.core.money.EnvelopeMove
import com.bamnat.core.money.EnvelopeStatus
import com.bamnat.core.money.Envelopes
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import androidx.compose.ui.semantics.Role

/** 1.37 [LEDH-11][LEDH-13] What the envelopes card does, given by the money tab. */
internal interface EnvelopeActions {
    fun save(e: Envelope)
    fun delete(e: Envelope)
    fun move(amounts: Map<Long, Long>, date: LocalDate, note: String, entryId: Long?)
}

/**
 * 1.37 [LEDH-11] The last money in worth splitting into envelopes: pay (or, for a 세금 봉투, any work money) of the last
 * three days that no envelope took a share of yet.
 */
internal fun toSplit(book: MoneyBook, moves: List<EnvelopeMove>, today: LocalDate): MoneyEntry? {
    val taken = moves.mapNotNull { it.entryId }.toSet()
    return book.entries.filter { it.kind == MoneyKind.INCOME && it.amount >= 50_000 && it.id !in taken && !it.date.isBefore(today.minusDays(3)) && !it.date.isAfter(today) }
        .maxByOrNull { it.date }
}

/**
 * 1.37 [LEDH-11][LEDH-13] 모으기 봉투: each goal with its progress, the day it is reached at this pace, how much a month
 * makes its day, and how many more shifts would fill it ("야간 4번 더 하면", from the take-home per shift). New pay is
 * offered to be split by each envelope's share. 현장·자영업 are offered a 세금 봉투. Three goals are free (결정 5).
 */
@Composable
internal fun EnvelopesCard(
    book: MoneyBook, envelopes: List<Envelope>, moves: List<EnvelopeMove>, style: WorkStyle?, today: LocalDate,
    perShift: Long?, pro: Boolean, actions: EnvelopeActions,
) {
    val rows = remember(envelopes, moves, today) { Envelopes.status(envelopes, moves, today) }
    var editing by remember { mutableStateOf<Envelope?>(null) }
    var putting by remember { mutableStateOf<EnvelopeStatus?>(null) }
    val canAdd = Envelopes.canAdd(envelopes, pro)
    val taxSuggest = Envelopes.taxShareFor(style).takeIf { it > 0 && envelopes.none { e -> e.kind == EnvelopeKind.TAX && !e.closed } }
    SectionCard(title = "모으기 봉투", trailing = {
        TextButton(onClick = { editing = Envelope(name = "", created = today) }, enabled = canAdd) { Text("봉투 만들기") }
    }) {
        if (rows.isEmpty()) {
            // bc7e2d43 PC 확인 LOW-10: 근무 몇 번 only where there are shifts (투자 has none).
            Muted("여행·비상금·새 폰처럼 모으는 돈마다 봉투를 만들면, 이대로 언제 채우는지" + (if (style?.usesPayEngine == true) "와 근무 몇 번이면 되는지" else "") +
                " 알려 드려요. 돈은 통장에 그대로 있고 봉투는 그 돈의 이름표예요.")
        }
        rows.forEach { s -> EnvelopeRow(s, today, perShift, onPut = { putting = s }, onEdit = { editing = s.envelope }) }
        if (!canAdd) Muted("봉투는 ${Envelopes.FREE_COUNT}개까지 무료예요. 다 채운 봉투를 닫으면 새로 만들 수 있어요.")
        taxSuggest?.let { pct ->
            val what = if (style == WorkStyle.BUSINESS) "부가세·종소세" else "종소세"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("들어온 돈의 $pct%를 $what 봉투로 떼어 두면 신고 철에 덜 놀라요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.save(Envelope(name = what, kind = EnvelopeKind.TAX, sharePercent = pct, created = today, emoji = "🧾")) }) {
                    Text("세금 봉투 만들기")
                }
            }
        }
        // 월급 나누기: new money in, split by each envelope's share in one tap (되돌리기 takes it all back).
        val entry = remember(book, moves, today) { toSplit(book, moves, today) }
        val split = remember(entry, envelopes, moves) {
            entry?.let { e ->
                val taxOnly = e.category != MoneyCategories.SALARY && style != WorkStyle.FIELD && style != WorkStyle.BUSINESS
                Envelopes.split(e.amount, envelopes, moves, taxOnly = taxOnly && envelopes.none { it.kind == EnvelopeKind.GOAL && it.sharePercent > 0 })
            }.orEmpty()
        }
        if (entry != null && split.isNotEmpty()) {
            val names = envelopes.associateBy { it.id }
            Column(Modifier.padding(top = 8.dp)) {
                Text("${Fmt.monthDay(entry.date)} ${entry.title} ${Fmt.won(entry.amount)}을 나눌까요?", style = MaterialTheme.typography.titleSmall)
                Muted(split.entries.joinToString(" · ") { (id, a) -> "${names[id]?.name.orEmpty()} ${Fmt.won(a)}" })
                TextButton(onClick = { actions.move(split, entry.date, "${entry.title} 나누기", entry.id) }) { Text("나누기") }
            }
        }
    }
    editing?.let { e -> EnvelopeEditor(e, style, onSave = { actions.save(it); editing = null }, onDelete = { actions.delete(it); editing = null }, onDismiss = { editing = null }) }
    putting?.let { s ->
        PutDialog(s, onDone = { amount -> if (amount != 0L) actions.move(mapOf(s.envelope.id to amount), today, if (amount > 0) "넣기" else "빼기", null); putting = null },
            onDismiss = { putting = null })
    }
}

@Composable
private fun EnvelopeRow(s: EnvelopeStatus, today: LocalDate, perShift: Long?, onPut: () -> Unit, onEdit: () -> Unit) {
    val e = s.envelope
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onEdit).padding(vertical = 6.dp)
        .semantics { contentDescription = "${e.name} 봉투, ${Fmt.won(s.saved)}" + if (e.target > 0) " / ${Fmt.won(e.target)}" else "" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((e.emoji.ifBlank { if (e.kind == EnvelopeKind.TAX) "🧾" else "✉️" }) + " " + e.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(Fmt.won(s.saved) + if (e.target > 0) " / ${Fmt.wonShort(e.target)}" else "", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onPut) { Text("넣기") }
        }
        if (e.target > 0) LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
        val words = when {
            s.full -> "다 채웠어요!"
            e.kind == EnvelopeKind.TAX -> "들어온 돈의 ${e.sharePercent}%를 떼어 둬요. 세무 조언이 아니라 미리 모아 두는 돈이에요."
            else -> listOfNotNull(
                s.eta?.let { "이대로면 ${Fmt.monthDay(it)}" },
                s.neededPerMonth?.let { "${e.due?.let(Fmt::monthDay)}까지 매달 ${Fmt.won(it)}" },
                Envelopes.shiftsFor(s.left, perShift)?.let { "근무 ${it}번이면 채워요" },
            ).joinToString(" · ").ifBlank { if (e.target > 0) "넣기를 누르거나 월급이 들어올 때 나눠 보세요" else "" }
        }
        if (words.isNotBlank()) Text(words, style = MaterialTheme.typography.bodySmall,
            color = if (s.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PutDialog(s: EnvelopeStatus, onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    var out by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.envelope.name} 봉투") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bamnat.app.ui.components.FilterChip(!out, { out = false }, { Text("넣기") })
                    com.bamnat.app.ui.components.FilterChip(out, { out = true }, { Text("빼기") })
                }
                AmountField("금액", digits, { digits = it })
                Muted("지금 ${Fmt.won(s.saved)}" + if (s.envelope.target > 0) " · 남은 ${Fmt.won(s.left)}" else "")
                // 1.37 PC 확인 6a4efc92 제안: 넣기가 통장 돈을 옮기는 게 아니라는 것을 넣는 자리에서도.
                Muted("돈은 통장에 그대로 있어요. 봉투는 그중 이만큼은 ‘${s.envelope.name}’에 쓸 돈이라고 붙여 두는 이름표예요.")
            }
        },
        confirmButton = {
            TextButton(onClick = { val a = digits.toAmount(); onDone(if (out) -minOf(a, s.saved.coerceAtLeast(0)) else a) }, enabled = digits.toAmount() > 0) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EnvelopeEditor(e: Envelope, style: WorkStyle?, onSave: (Envelope) -> Unit, onDelete: (Envelope) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(e.name) }
    var target by rememberSaveable { mutableStateOf(amountDigits(e.target)) }
    var share by rememberSaveable { mutableStateOf(e.sharePercent.takeIf { it > 0 }?.toString().orEmpty()) }
    var due by rememberSaveable { mutableStateOf(e.due?.toString().orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val isNew = e.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "봉투 만들기" else "봉투 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(Envelope.MAX_NAME) }, label = { Text("이름 (여행, 비상금…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (e.kind == EnvelopeKind.GOAL) {
                    AmountField("모을 금액 (비워도 돼요)", target, { target = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(due.takeIf { it.isNotBlank() }?.let { "${Fmt.monthDay(LocalDate.parse(it))}까지" } ?: "날짜 없음", modifier = Modifier.weight(1f))
                        TextButton(onClick = { picking = true }) { Text("날짜 고르기") }
                        if (due.isNotBlank()) TextButton(onClick = { due = "" }) { Text("빼기") }
                    }
                }
                OutlinedTextField(share, { share = it.filter(Char::isDigit).take(3) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (e.kind == EnvelopeKind.TAX) "들어온 돈에서 뗄 %" else "월급이 들어오면 넣을 % (비워도 돼요)") }, suffix = { Text("%") })
                if (e.kind == EnvelopeKind.TAX) Muted("현장 3.3% 일감은 5월 종소세, 자영업은 부가세까지 생각해 넉넉히 잡았어요. 실제 낼 세금은 세무서·세무사에게 확인하세요.")
                if (!isNew) TextButton(onClick = { onDelete(e) }) { Text("봉투 지우기", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(e.copy(name = name.trim(), target = target.toAmount(), due = due.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                    sharePercent = (share.toIntOrNull() ?: 0).coerceIn(0, 100)))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
    if (picking) {
        val start = due.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: com.bamnat.core.time.Today.date().plusMonths(3)
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        com.bamnat.app.ui.components.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { androidx.compose.material3.DatePicker(state, showModeToggle = false) }
    }
}
