package com.bamnat.app.ui.pay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole

/** [READ-18] The photo read, small; a tap shows it whole to compare line by line. In memory only. */
@Composable
internal fun PhotoThumb(photo: Bitmap, modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
    var big by remember { mutableStateOf(false) }
    val image = remember(photo) { photo.asImageBitmap() }
    Image(
        image, contentDescription = "읽은 사진, 누르면 크게 봐요", contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "사진 크게 보기") { big = true },
    )
    if (big) Dialog(onDismissRequest = { big = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Image(image, contentDescription = "읽은 사진", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { big = false }) { Text("닫기") } }
            }
        }
    }
}

/**
 * [READ-18] 1.27 순번 447: the lines read from a payslip, "사진과 내용이 같은가요?" under them. '네, 같아요' takes them as looked
 * at; '다른 곳이 있어요' makes each line a button that fixes only that line ([SlipLineEditor], under it), with 줄 더하기. Lines read
 * two ways ([unsure], by index) are yellow until looked at. [onChange] gets the payslip and the lines still to look at.
 * [PHOTO-29] 1.30 AV (R3 A-6 상): with yellow lines, '네, 같아요' is not the first button any more: it took every line as looked at
 * in one tap, and taught pressing it without looking. '노란 n줄 확인하기' opens each yellow line with [맞아요]·[고치기]; when the
 * last one is answered and the payslip's own sums meet, the photo counts as looked at.
 */
@Composable
internal fun SlipCheck(
    read: PayslipRead, unsure: Set<Int>, same: Boolean?, fromPhoto: Boolean,
    onAnswer: (Boolean) -> Unit, onChange: (PayslipRead, Set<Int>) -> Unit,
    /** [PHOTO-29] Tests open the yellow lines' answers at once. */
    reviewingAtFirst: Boolean = false,
    /** [PHOTO-30] Goes up when the result's button sends the user to the yellow lines: they open as '노란 n줄 확인하기' would. */
    reviewAsk: Int = 0,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var reviewing by remember { mutableStateOf(reviewingAtFirst) }
    LaunchedEffect(reviewAsk) { if (reviewAsk > 0 && unsure.isNotEmpty()) reviewing = true }
    val fixing = same == false || !fromPhoto && same != true
    // [PHOTO-29] One yellow line answered 'that is right': the rest stay; the last one, with the sums meeting, ends the look.
    fun rightAsRead(i: Int) {
        val left = unsure - i
        onChange(read, left)
        if (left.isEmpty() && com.bamnat.core.pay.SlipVote.adds(read) && com.bamnat.core.pay.SlipVote.stated(read)) { reviewing = false; onAnswer(true) }
    }
    fun delete(i: Int) {
        onChange(read.copy(lines = read.lines.filterIndexed { j, _ -> j != i }), unsure.filter { it != i }.map { if (it > i) it - 1 else it }.toSet())
        editing = null
    }
    fun save(i: Int, fixed: SlipLine) {
        if (i < 0) {
            // A line the reader missed goes before the totals, where payslips list their items.
            val at = read.lines.indexOfFirst { it.role in TOTALS }.takeIf { it >= 0 } ?: read.lines.size
            val lines = read.lines.toMutableList().apply { add(at, fixed) }
            onChange(read.copy(lines = lines), unsure.map { if (it >= at) it + 1 else it }.toSet())
        } else {
            val next = read.copy(lines = read.lines.mapIndexed { j, l -> if (j == i) fixed else l })
            onChange(next, unsure - i)
            // [PHOTO-29] The last yellow line fixed while looking at them, the sums meeting: looked at.
            if (reviewing && (unsure - i).isEmpty() && com.bamnat.core.pay.SlipVote.adds(next) && com.bamnat.core.pay.SlipVote.stated(next)) { reviewing = false; onAnswer(true) }
        }
        editing = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        read.lines.forEachIndexed { i, line ->
            val doubt = i in unsure
            Row(
                Modifier.fillMaxWidth()
                    .then(if (doubt) Modifier.background(WARN.copy(alpha = 0.3f), RoundedCornerShape(6.dp)) else Modifier)
                    // [PHOTO-31] 1.30 AW: any line opens under itself on a tap, not only after '다른 곳이 있어요'.
                    .clickable(role = Role.Button, onClickLabel = "이 줄 고치기") { editing = if (editing == i) null else i }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { if (doubt) contentDescription = "${line.name} ${line.amount?.let(::won).orEmpty()}, 사진에서 두 가지로 읽혔어요" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.role.short(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(76.dp))
                Text(line.name + (line.minutes?.let { " · ${com.bamnat.core.pay.PayCalculator.hours(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(line.amount?.let(::won) ?: "", style = MaterialTheme.typography.bodyMedium)
                if (fixing) Text("  고치기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            // [PHOTO-29] A yellow line being looked at: is it as the photo shows?
            if (reviewing && doubt && !fixing) Row(Modifier.fillMaxWidth().padding(start = 76.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { rightAsRead(i) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("맞아요") }
                TextButton(onClick = { editing = i }, modifier = Modifier.heightIn(min = 48.dp)) { Text("고치기") }
            }
            // [PHOTO-31] 1.30 AW: the line is fixed where it is, under itself, not in a window over the list.
            if (editing == i) key(i, line) { SlipLineEditor(line, onDismiss = { editing = null }, onDelete = { delete(i) }, onSave = { save(i, it) }) }
        }
        read.gross?.let { g ->
            Text(
                if (read.consistent) "항목을 더하면 지급 총액 ${won(g)}과 맞아요" else "항목 합 ${won(read.earningsSum)} · 지급 총액 ${won(g)}. 빠지거나 잘못 읽은 줄을 고쳐 주세요",
                style = MaterialTheme.typography.bodySmall,
                color = if (read.consistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // [PHOTO-06] 1.29: a line can be added any time, not only after '다른 곳이 있어요'.
        if (editing == -1) SlipLineEditor(null, onDismiss = { editing = null }, onDelete = null, onSave = { save(-1, it) })
        else com.bamnat.app.ui.components.AddButton("칸 더하기 (빠진 줄)", onClick = { editing = -1 })
        when {
            same == true -> Text("사진과 같다고 확인했어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fromPhoto && same == null -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (unsure.isNotEmpty()) "노란 줄 ${unsure.size}개만 사진과 맞는지 봐 주세요" else "사진과 내용이 같은가요?",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (unsure.isNotEmpty()) Text(
                        if (reviewing) "노란 줄마다 ‘맞아요’나 ‘고치기’를 눌러 주세요. 사진을 누르면 크게 보여요."
                        else "사진에서 두 가지로 읽혔거나 짐작한 줄이에요. 나머지는 명세서 합계로 맞는 걸 확인했어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // [PHOTO-29] Yellow lines first: their own answers, not one '같아요' for all.
                        if (unsure.isEmpty()) Button(onClick = { onAnswer(true); onChange(read, emptySet()) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("네, 같아요") }
                        else if (!reviewing) Button(onClick = { reviewing = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("노란 ${unsure.size}줄 확인하기") }
                        OutlinedButton(onClick = { reviewing = false; onAnswer(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("다른 곳이 있어요") }
                    }
                }
            }
            same == false -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("다른 줄을 눌러 그 줄만 고쳐 주세요", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = { onAnswer(true); onChange(read, emptySet()) }) { Text("다 고쳤어요") }
            }
        }
    }
}

private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)

/** [PHOTO-06] Lines that can carry hours. */
private val TIMED = setOf(SlipRole.OVERTIME, SlipRole.NIGHT, SlipRole.HOLIDAY, SlipRole.WEEKLY_HOLIDAY, SlipRole.EXTRA_WORK)

/** The kinds a line can be told to be, in a payslip's order. */
private val ROLES = listOf(
    SlipRole.BASE, SlipRole.OVERTIME, SlipRole.NIGHT, SlipRole.HOLIDAY, SlipRole.WEEKLY_HOLIDAY, SlipRole.EXTRA_WORK,
    SlipRole.BONUS, SlipRole.ALLOWANCE, SlipRole.HOURLY, SlipRole.GROSS, SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL, SlipRole.NET,
)

/** [PHOTO-31] The kinds most lines are; the rest behind '더 보기'. */
private val COMMON_ROLES = listOf(SlipRole.BASE, SlipRole.ALLOWANCE, SlipRole.OVERTIME, SlipRole.NIGHT, SlipRole.HOLIDAY, SlipRole.DEDUCTION)

/**
 * [READ-18] One payslip line fixed where it was read: what kind it is, its name, its amount (in words under the digits,
 * so a missing 0 shows). A new one when [line] is null.
 * [PHOTO-31] 1.30 AW (R3 A-3 "창 위에 창"): it opens under the line itself, in the list, not as a window over the result; the six
 * kinds most lines are show first and the rest come with '더 보기'.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlipLineEditor(line: SlipLine?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (SlipLine) -> Unit) {
    var role by remember { mutableStateOf(line?.role ?: SlipRole.ALLOWANCE) }
    var name by remember { mutableStateOf(line?.name.orEmpty()) }
    var amount by remember { mutableStateOf(line?.amount) }
    var more by remember { mutableStateOf(role !in COMMON_ROLES) }
    // [PHOTO-06] The hours a premium line shows (연장 12시간), in hours as payslips print them.
    var hours by remember { mutableStateOf(line?.minutes?.let { com.bamnat.app.ui.components.decimalText(it / 60.0) }.orEmpty()) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (line == null) "빠진 줄 더하기" else "이 줄 고치기", style = MaterialTheme.typography.titleSmall)
            Text("어떤 줄인가요?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (if (more) ROLES else COMMON_ROLES).forEach { r ->
                    FilterChip(selected = role == r, onClick = { role = r; if (name.isBlank()) name = r.label }, label = { Text(r.label) })
                }
                if (!more) TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("더 보기") }
            }
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("명세서에 적힌 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MoneyField("금액", amount, { amount = it })
            if (role in TIMED) OutlinedTextField(hours, { v -> hours = v.filter { it.isDigit() || it == '.' }.take(6) }, label = { Text("시간 (선택)") },
                suffix = { Text("시간") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())
            if (line != null && line.role != role) Text("다음 명세서부터 ‘${name.ifBlank { line.name }}’ 줄은 ${role.label}(으)로 읽어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("이 줄 빼기") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
                Button(onClick = {
                    val minutes = if (role in TIMED) hours.toDoubleOrNull()?.takeIf { it > 0 && it < 500 }?.let { Math.round(it * 60).toInt() } else null
                    onSave(SlipLine(name.trim().ifBlank { role.label }, role, amount, minutes, line?.source.orEmpty()))
                }, enabled = amount != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (line == null) "더하기" else "고치기") }
            }
        }
    }
}

private val WARN = Color(0xFFFFC107)
