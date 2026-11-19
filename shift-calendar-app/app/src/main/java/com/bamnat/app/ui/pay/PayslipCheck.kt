package com.bamnat.app.ui.pay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
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
import com.bamnat.app.ui.components.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.ui.theme.color
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.container
import com.bamnat.app.ui.theme.color

/** [READ-18] The photo read, small; a tap shows it whole to compare line by line. In memory only. */
@Composable
internal fun PhotoThumb(photo: Bitmap, modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
    var big by remember { mutableStateOf(false) }
    val image = remember(photo) { photo.asImageBitmap() }
    Image(
        image, contentDescription = "읽은 사진, 누르면 크게 봐요", contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "사진 크게 보기") { big = true },
    )
    if (big) Dialog(onDismissRequest = { big = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                // 2.0.0 [RVJ-61] HQ-81 같은 구조: the whole photo opened to compare was a page to scroll with no zoom; it is the pinned
                // photo's own (two fingers, double tap, ⊖ ⊕, read-aloud actions), one finger moving a long payslip up and down.
                // (HQ-102) ⊖ ⊕ in the 닫기 row under it, never on the photo (they hid its last line).
                val zoom = rememberPhotoZoom(photo)
                ZoomablePhoto(photo, Modifier.weight(1f).fillMaxWidth(), zoom)
                PhotoBand(zoom, close = BandAction("닫기") { big = false })
            }
        }
    }
}

/**
 * [READ-41] 1.38 PC 11차: 무엇을 읽었는지 보기 for a payslip or rules photo, the lines as the phone read them (in memory only),
 * so a line the screen lost can be told from a line the photo lost.
 */
@Composable
internal fun ReadTextButton(text: String) {
    if (text.isBlank()) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("무엇을 읽었는지 보기") }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("무엇을 읽었는지", style = MaterialTheme.typography.titleMedium)
                Text("사진에서 읽은 글 그대로예요. 여기에 없는 줄은 사진에서 못 읽은 줄이에요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text.trim(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { open = false }) { Text("닫기") } }
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
 * 2.0.0 [RVL-46] (검토 SCAN-29·I07ba-05) A line is looked at for its amount ('금액 확인') or its name ('이름 확인', [SlipLine.check]),
 * said in words on the line. Amounts first, as before; then 'n개 이름만 …' [네, 같아요][하나씩 보기], each name with its own
 * question (야간수당인가요? [네][아니요] · ‘식비’가 맞나요? [네][사진대로]). Names never hold the payslip back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlipCheck(
    read: PayslipRead, unsure: Set<Int>, same: Boolean?, fromPhoto: Boolean,
    onAnswer: (Boolean) -> Unit, onChange: (PayslipRead, Set<Int>) -> Unit,
    /** [PHOTO-29] Tests open the yellow lines' answers at once. */
    reviewingAtFirst: Boolean = false,
    /** [PHOTO-30] Goes up when the result's button sends the user to the yellow lines: they open as 'n줄 확인하기' would. */
    reviewAsk: Int = 0,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var reviewing by remember { mutableStateOf(reviewingAtFirst) }
    // 2.0.0 [RVL-46] (검토 SCAN-29·I07ba-05) Two things to look at, said apart: an amount read two ways ([unsure]) and a name the
    // reader put right or guessed ([SlipLine.check]). The payslip's sums vouch for an amount, never for a name.
    // Amounts first, as 1.39 asked them (the same taps); the names after, and they never hold the payslip back: a name not
    // answered stays as shown (a kind-changing guess stays the plain 수당) and is asked again next month.
    val names = read.names
    val asking = names.isNotEmpty() && unsure.isEmpty()
    LaunchedEffect(reviewAsk) { if (reviewAsk > 0 && unsure.isNotEmpty()) reviewing = true }
    val fixing = same == false || !fromPhoto && same != true
    fun done(next: PayslipRead, left: Set<Int>) = left.isEmpty() && com.bamnat.core.pay.SlipVote.adds(next) && com.bamnat.core.pay.SlipVote.stated(next)
    // [PHOTO-29] One yellow line answered 'that is right': the rest stay; the last one, with the sums meeting, ends the look.
    fun rightAsRead(i: Int) {
        val left = unsure - i
        onChange(read, left)
        if (done(read, left)) { reviewing = false; onAnswer(true) }
    }
    // 2.0.0 [RVL-46] A name answered: the line as the user said. The last one ends the look.
    fun named(i: Int, line: SlipLine) {
        val next = read.copy(lines = read.lines.mapIndexed { j, l -> if (j == i) line else l })
        onChange(next, unsure)
        if (next.names.isEmpty() && done(next, unsure)) { reviewing = false; onAnswer(true) }
    }
    fun delete(i: Int) {
        onChange(read.copy(lines = read.lines.filterIndexed { j, _ -> j != i }), unsure.filter { it != i }.map { if (it > i) it - 1 else it }.toSet())
        editing = null
    }
    fun save(i: Int, fixed: SlipLine) {
        if (i < 0) {
            // A line the reader missed goes before the totals, where payslips list their items; [PAYIN-02] a 공제 before 공제 합계.
            val at = (if (fixed.role in RIGHT) read.lines.indexOfFirst { it.role == SlipRole.DEDUCTION_TOTAL || it.role == SlipRole.NET }
                else read.lines.indexOfFirst { it.role in TOTALS }).takeIf { it >= 0 } ?: read.lines.size
            val lines = read.lines.toMutableList().apply { add(at, fixed) }
            onChange(read.copy(lines = lines), unsure.map { if (it >= at) it + 1 else it }.toSet())
        } else {
            val next = read.copy(lines = read.lines.mapIndexed { j, l -> if (j == i) fixed else l })
            onChange(next, unsure - i)
            // [PHOTO-29] The last yellow line fixed while looking at them, the sums meeting: looked at.
            if (reviewing && done(next, unsure - i) && (!asking || next.names.isEmpty())) { reviewing = false; onAnswer(true) }
        }
        editing = null
    }
    // [PAYIN-02] 1.37 B2 (사용자 2026-11-04 "지급내역과 공제내역을 분리해서 좌우로 배열"): the lines as the payslip prints them, 지급 on
    // the left and 공제 on the right, each with its quick names to add; 실수령 worked out under them. A line still opens under the two.
    var preset by remember { mutableStateOf<SlipLine?>(null) }
    val editorAt = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(editing, preset) { if (editing != null) { kotlinx.coroutines.delay(80); runCatching { editorAt.bringIntoView() } } }
    @Composable
    fun cell(i: Int, line: SlipLine) {
        val doubt = i in unsure
        val check = line.check
        // 2.0.0 [RVL-46] What is looked at, in words beside the colour (조건 14): 이름 확인, 금액 확인 or both.
        val tag = when {
            check != null && doubt -> "이름·금액 확인"
            check != null -> "이름 확인"
            doubt -> "금액 확인"
            else -> null
        }
        val why = when {
            check == null -> null
            check.maybe != null -> "${check.maybe}일 수도 있어요"
            check.renamed(line) -> "사진에는 ‘${check.printed}’"
            else -> "짐작한 이름"
        }
        val caution = com.bamnat.app.ui.theme.Tone.Caution.color()
        Column(
            Modifier.fillMaxWidth()
                // 2.0.0 [RVP-12] (검토 UIX-12) The app's one caution colour; [RVL-46] on a line asked about by name or amount.
                // 2.0.0 [RVJ-66] (HQ-103, 조건 6) 노란 줄은 주의 색이에요(빨강이었어요).
                .then(if (tag != null) Modifier.background(Tone.Caution.container(), MaterialTheme.shapes.extraSmall) else Modifier)
                .then(if (editing == i) Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall) else Modifier)
                // [PHOTO-31] 1.30 AW: any line opens on a tap, not only after '다른 곳이 있어요'.
                .clickable(role = Role.Button, onClickLabel = "이 줄 고치기") { preset = null; editing = if (editing == i) null else i }
                .heightIn(min = 44.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .semantics { if (tag != null) contentDescription = "${line.name} ${line.amount?.let(::won).orEmpty()}, $tag" + (why?.let { ", $it" } ?: "") },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // PC 5566ff20 ('실지급액객'): a total's name is its kind's own word; what the photo printed stays under it.
                val shownName = if (line.role in TOTALS) line.role.label else line.name
                Text(shownName + (line.minutes?.let { " · ${com.bamnat.core.pay.PayCalculator.hours(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                if (fixing) Text("고치기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            // 2.0.0 [RVL-46] (조건 12) At 200% in half a fold the bold total left its kind's word no width: '지/급/합/계' stood a
            // letter a line under the amount. The amount now goes whole to its own line, still on the right, when both do not fit.
            KindAndAmount(
                { Text((if (line.role in TOTALS) line.name.takeIf { it != line.role.label } else line.role.short().takeIf { it != line.name }) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                { Text(line.amount?.let(::won) ?: "금액 없음", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (line.role in TOTALS) androidx.compose.ui.text.font.FontWeight.Bold else null) },
            )
            val questioned = reviewing && asking && check != null && !fixing
            // A question that says it all ("야간수당인가요?") leaves the line's tag at "이름 확인" (조건 4).
            if (tag != null) Text(listOfNotNull(tag, why.takeUnless { questioned && check?.maybe != null }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = caution, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 3)
            // 2.0.0 [RVL-46] A name being looked at, once the amounts are: its own question (야간수당인가요? · ‘식비’가 맞나요? ·
            // 이름이 맞나요?).
            if (questioned && check != null) {
                val renamed = check.renamed(line)
                Text(when {
                    check.maybe != null -> "${check.maybe}인가요?"
                    renamed -> "‘${line.name}’${com.bamnat.core.text.Josa.iga(line.name)} 맞나요?"
                    else -> "이름이 맞나요?"
                }, style = MaterialTheme.typography.bodySmall)
                when {
                    check.maybe != null -> Answers("네", { named(i, check.asMaybe(line)) }, "아니요", { named(i, line.copy(check = null)) })
                    renamed -> Answers("네", { named(i, line.copy(check = null)) }, "사진대로", { named(i, check.asPrinted(line)) })
                    else -> Answers("맞아요", { named(i, line.copy(check = null)) }, "고치기", { preset = null; editing = i })
                }
            }
            // [PHOTO-29] A yellow line being looked at: is it as the photo shows?
            else if (reviewing && doubt && !fixing) Answers("맞아요", { rightAsRead(i) }, "고치기", { preset = null; editing = i })
        }
    }
    @Composable
    fun side(title: String, indices: List<Int>, quick: List<Pair<SlipRole, String>>, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
            if (indices.isEmpty()) Text("아직 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp))
            indices.forEach { i -> key(i) { cell(i, read.lines[i]) } }
            // [PAYIN-02] The names payslips print most, one tap to a new line with its kind and name (only the amount to type). A
            // kind a payslip has once (기본급, 지급·공제 합계, 연장·야간·휴일) is not offered again; a side with lines folds them under one button.
            val have = read.lines.map { it.name.filterNot(Char::isWhitespace) }.toSet()
            val roles = read.lines.map { it.role }.toSet()
            val left = quick.filter { (role, n) -> n.filterNot(Char::isWhitespace) !in have && !(role in ONCE && (role in roles || role == SlipRole.GROSS && SlipRole.TOTAL in roles)) }
            var open by remember { mutableStateOf(false) }
            if (left.isNotEmpty()) {
                if (indices.size >= 3 && !open) TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("+ 자주 쓰는 이름 ${left.size}개", style = MaterialTheme.typography.labelMedium)
                } else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    left.forEach { (role, name) ->
                        androidx.compose.material3.SuggestionChip(onClick = { preset = SlipLine(name, role, null); editing = -1 },
                            label = { Text("+ $name", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
    val payIdx = read.lines.indices.filter { read.lines[it].role !in RIGHT && read.lines[it].role != SlipRole.NET }
    val dedIdx = read.lines.indices.filter { read.lines[it].role in RIGHT }
    val netIdx = read.lines.indices.filter { read.lines[it].role == SlipRole.NET }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 320.dp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                side("지급", payIdx, QUICK_PAY, Modifier.weight(1f))
                side("공제", dedIdx, QUICK_DED, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                side("지급", payIdx, QUICK_PAY, Modifier.fillMaxWidth())
                side("공제", dedIdx, QUICK_DED, Modifier.fillMaxWidth())
            }
        }
        netIdx.forEach { i -> key(i) { cell(i, read.lines[i]) } }
        // [READ-38] 1.38 PC 10차 (사용성, 줄이 빠진 사진: '칸 더하기'가 카드 아래라 내려야 보임): when the lines fall short of the
        // totals the payslip states, the gap is said first, with one tap to the missing line, its amount already in.
        if (editing != -1) listOfNotNull(
            read.missingEarning?.let { Triple("지급", SlipRole.ALLOWANCE, it) },
            read.missingDeduction?.let { Triple("공제", SlipRole.DEDUCTION, it) },
        ).forEach { (side, role, gap) ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$side 줄이 ${won(gap)}만큼 덜 읽혔어요", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("사진에서 안 읽힌 줄이 있어요. 그 줄 이름만 넣으면 돼요(여러 줄이면 금액을 나눠 넣어요).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    OutlinedButton(onClick = { preset = SlipLine("", role, gap); editing = -1 }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("빠진 $side 줄 더하기 (${won(gap)})")
                    }
                }
            }
        }
        SlipSums(read)
        // [PHOTO-31] 1.30 AW: the line is fixed in the list, not in a window over it; [PAYIN-02] under the two sides.
        Column(Modifier.bringIntoViewRequester(editorAt)) {
            editing?.takeIf { it >= 0 && it < read.lines.size }?.let { i ->
                val line = read.lines[i]
                key(i, line) { SlipLineEditor(line, onDismiss = { editing = null }, onDelete = { delete(i) }, onSave = { save(i, it) }) }
            }
            if (editing == -1) key(preset) { SlipLineEditor(null, onDismiss = { editing = null; preset = null }, onDelete = null, onSave = { save(-1, it); preset = null }, preset = preset) }
        }
        read.gross?.let { g ->
            Text(
                if (read.consistent) "항목을 더하면 지급 총액 ${won(g)}과 맞아요" else "항목 합 ${won(read.earningsSum)} · 지급 총액 ${won(g)}. 빠지거나 잘못 읽은 줄을 고쳐 주세요",
                style = MaterialTheme.typography.bodySmall,
                color = if (read.consistent) MaterialTheme.colorScheme.primary else Tone.Caution.color(), // 2.0.0 [RVP-13] (UIX-15) 주황은 주의 하나
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // [PHOTO-06] 1.29: a line can be added any time, not only after '다른 곳이 있어요'.
        if (editing != -1) com.bamnat.app.ui.components.AddButton("칸 더하기 (빠진 줄)", onClick = { preset = null; editing = -1 })
        when {
            // 2.0.0 [RVL-46] (검토 I07ba-05) The amounts looked at (or vouched for) and names left: they are asked on their own.
            fromPhoto && same != false && asking -> LookCard(
                "이름 ${names.size}개만 사진과 맞는지 봐 주세요",
                if (reviewing) "줄마다 답을 눌러 주세요. 사진을 누르면 크게 보여요." else "금액은 명세서 합계와 맞아요. 비슷한 이름으로 읽었거나 짐작한 이름이에요.",
                (if (reviewing) "나머지도 맞아요" else "네, 같아요") to { reviewing = false; onAnswer(true); onChange(read.namesLooked(), emptySet()) },
                if (reviewing) null else "하나씩 보기" to { reviewing = true },
            )
            same == true -> Text("사진과 같다고 확인했어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fromPhoto && same == null -> LookCard(
                if (unsure.isNotEmpty()) "금액 ${unsure.size}줄만 사진과 맞는지 봐 주세요" else "사진과 내용이 같은가요?",
                when {
                    unsure.isEmpty() -> null
                    reviewing -> "줄마다 답을 눌러 주세요. 사진을 누르면 크게 보여요."
                    else -> "사진에서 두 가지로 읽힌 금액이에요. 나머지는 명세서 합계와 맞아요." + if (names.isNotEmpty()) " 이름은 그다음에 봐요." else ""
                },
                // [PHOTO-29] Yellow lines first: their own answers, not one '같아요' for all.
                when {
                    unsure.isEmpty() -> "네, 같아요" to { onAnswer(true); onChange(read.namesLooked(), emptySet()) }
                    !reviewing -> "${unsure.size}줄 확인하기" to { reviewing = true }
                    else -> null
                },
                "다른 곳이 있어요" to { reviewing = false; onAnswer(false) },
            )
            same == false -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("다른 줄을 눌러 그 줄만 고쳐 주세요", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = { onAnswer(true); onChange(read.namesLooked(), emptySet()) }) { Text("다 고쳤어요") }
            }
        }
    }
}

/**
 * 2.0.0 [RVL-46] A line's kind on the left and its amount on the right; when the two do not fit side by side (글자 200%, half
 * a fold) the amount goes whole to the next line, still on the right, never broken as '150,000 / 원' (조건 12).
 */
@Composable
private fun KindAndAmount(kind: @Composable () -> Unit, amount: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(contents = listOf(kind, amount)) { (k, a), c ->
        val gap = 8.dp.roundToPx()
        val free = c.copy(minWidth = 0)
        val ap = a.first().measure(free)
        val wide = k.first().maxIntrinsicWidth(c.maxHeight)
        val w = if (c.hasBoundedWidth) c.maxWidth else wide + gap + ap.width
        if (wide + gap + ap.width <= w) {
            val kp = k.first().measure(free.copy(maxWidth = w - gap - ap.width))
            val h = maxOf(kp.height, ap.height)
            layout(w, h) { kp.place(0, (h - kp.height) / 2); ap.place(w - ap.width, (h - ap.height) / 2) }
        } else {
            val kp = k.first().measure(free)
            layout(w, kp.height + ap.height) { kp.place(0, 0); ap.place(w - ap.width, kp.height) }
        }
    }
}

/**
 * 2.0.0 [RVL-46] The card under a payslip's lines: what is left to look at, a line on it, the one filled button (what to do
 * now) and one outlined.
 */
@Composable
private fun LookCard(title: String, said: String?, filled: Pair<String, () -> Unit>?, outlined: Pair<String, () -> Unit>?) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            val buttons = listOfNotNull(
                filled?.let { (label, go) -> @Composable { Button(onClick = go, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) } } },
                outlined?.let { (label, go) -> @Composable { OutlinedButton(onClick = go, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) } } },
            )
            SideOrStacked(buttons)
        }
    }
}

/**
 * 2.0.0 [RVL-46] (조건 12) The card's buttons side by side in two halves while each word fits its half, else one above the
 * other at full width: at 200% in a fold the halves broke '네, 같아요' and '하나씩 보기' over two lines each.
 */
@Composable
private fun SideOrStacked(buttons: List<@Composable () -> Unit>) {
    androidx.compose.ui.layout.Layout(contents = buttons) { parts, c ->
        val gap = 8.dp.roundToPx()
        val w = c.maxWidth
        val each = if (parts.size > 1) (w - gap * (parts.size - 1)) / parts.size else w
        val side = parts.all { it.first().maxIntrinsicWidth(c.maxHeight) <= each }
        val placed = parts.map { it.first().measure(androidx.compose.ui.unit.Constraints.fixedWidth(if (side) each else w)) }
        val h = if (side) placed.maxOf { it.height } else placed.sumOf { it.height } + gap * (placed.size - 1)
        layout(w, h) {
            var at = 0
            placed.forEach { p -> if (side) { p.place(at, 0); at += each + gap } else { p.place(0, at); at += p.height + gap } }
        }
    }
}

/**
 * 2.0.0 [RVL-46] A line's two answers ([맞아요][고치기], [네][아니요], [네][사진대로]), side by side in half a payslip's width: the
 * outlined one with the app's 16dp sides so both fit on one row at 100% (1.39's 24dp put 고치기 under 맞아요).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Answers(yes: String, onYes: () -> Unit, other: String, onOther: () -> Unit) {
    // Material's own 40dp face keeps a one-letter '네' a pill, not an egg; its touch area is still 48dp
    // (minimumInteractiveComponentSize), so both fit half a fold's width side by side.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onYes, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) { Text(yes) }
        TextButton(onClick = onOther) { Text(other) }
    }
}

private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)

/** [PAYIN-02] Kinds a payslip prints once: not offered as a quick name when there is one. */
private val ONCE = setOf(SlipRole.BASE, SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.OVERTIME, SlipRole.NIGHT, SlipRole.HOLIDAY)

/** [PAYIN-02] The 공제 side of a payslip. */
private val RIGHT = setOf(SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL)

/** [PAYIN-02] Names most payslips print, one tap each to a new line. */
private val QUICK_PAY = listOf(
    SlipRole.BASE to "기본급", SlipRole.OVERTIME to "연장근로수당", SlipRole.NIGHT to "야간근로수당", SlipRole.HOLIDAY to "휴일근로수당",
    SlipRole.ALLOWANCE to "식대", SlipRole.BONUS to "상여금", SlipRole.GROSS to "지급합계",
)
private val QUICK_DED = listOf(
    SlipRole.DEDUCTION to "국민연금", SlipRole.DEDUCTION to "건강보험", SlipRole.DEDUCTION to "장기요양보험", SlipRole.DEDUCTION to "고용보험",
    SlipRole.DEDUCTION to "소득세", SlipRole.DEDUCTION to "지방소득세", SlipRole.DEDUCTION_TOTAL to "공제합계",
)

/**
 * [PAYIN-02] Under the two sides: 지급 − 공제 = 실수령, worked out from the lines as they are now, and whether the payslip's own
 * 실수령 says the same. Nothing to work out yet says so.
 */
@Composable
internal fun SlipSums(read: PayslipRead) {
    val pay = read.gross
    val ded = read.deducted
    val stated = read.lines.firstOrNull { it.role == SlipRole.NET && it.amount != null }?.amount
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("지급 합계", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(pay?.let(::won) ?: "-", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("공제 합계", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(ded?.let { "− " + won(it) } ?: "-", style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            val worked = if (pay != null && ded != null) pay - ded else null
            Row(Modifier.fillMaxWidth()) {
                Text("실수령", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text((worked ?: stated)?.let(::won) ?: "-", style = MaterialTheme.typography.titleSmall)
            }
            when {
                worked != null && stated != null && kotlin.math.abs(worked - stated) > com.bamnat.core.pay.PayslipMatch.tolerance(worked) ->
                    Text("명세서의 실수령 ${won(stated)}과 ${won(kotlin.math.abs(worked - stated))} 달라요. 빠진 공제 줄이 있는지 봐 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = Tone.Caution.color())
                worked != null && stated != null -> Text("명세서의 실수령과 맞아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                pay == null -> Text("왼쪽에 지급 줄을 넣으면 합계가 바로 나와요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ded == null -> Text("오른쪽에 공제 줄을 넣으면 실수령이 나와요 (급여 계산에는 지급 줄만 써요)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
internal fun SlipLineEditor(
    line: SlipLine?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (SlipLine) -> Unit,
    /** [PAYIN-02] A new line's kind and name picked from the quick names; only the amount is left to type. */
    preset: SlipLine? = null,
) {
    var role by remember { mutableStateOf(line?.role ?: preset?.role ?: SlipRole.ALLOWANCE) }
    var name by remember { mutableStateOf(line?.name ?: preset?.name.orEmpty()) }
    var amount by remember { mutableStateOf(line?.amount) }
    var more by remember { mutableStateOf(role !in COMMON_ROLES) }
    // [PHOTO-06] The hours a premium line shows (연장 12시간), in hours as payslips print them.
    val huge = rememberHugeCheck()
    var hours by remember { mutableStateOf(line?.minutes?.let { com.bamnat.app.ui.components.decimalText(it / 60.0) }.orEmpty()) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (line == null) (preset?.let { "‘${it.name}’ 금액 넣기" } ?: "빠진 줄 더하기") else "이 줄 고치기", style = MaterialTheme.typography.titleSmall)
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
                visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth())
            if (line != null && line.role != role) Text("다음 명세서부터 ‘${name.ifBlank { line.name }}’ 줄은 ${role.label}${com.bamnat.app.ui.Fmt.ro(role.label)} 읽어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("이 줄 빼기") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
                Button(onClick = {
                    val minutes = if (role in TIMED) hours.toDoubleOrNull()?.takeIf { it > 0 && it < 500 }?.let { Math.round(it * 60).toInt() } else null
                    huge.run(amount) { onSave(SlipLine(name.trim().ifBlank { role.label }, role, amount, minutes, line?.source.orEmpty())) }
                }, enabled = amount != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (line == null) "더하기" else "고치기") }
            }
        }
    }
}

