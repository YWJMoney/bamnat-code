package com.bamnat.app.ui.pay

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.PayPhotos
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * [PAYS-05] Two buttons that read the text of a photo on the phone: one picks a photo or screenshot, one takes a
 * picture. The text goes to [onText]; a photo taken here is deleted once read.
 * [READ-13][READ-14] 1.27: the photo is read several ways and put together ([PayslipScanner.scan]), "n/m번째 방법으로 읽는
 * 중" shows while it does, and a photo too dark, blurred or small says how to take a better one. [onScan], when given,
 * gets the whole reading (the put-together payslip, its lines to look at, the photo for "사진과 같은가요?") instead of
 * the text alone.
 */
@Composable
internal fun ScanButtons(
    onText: (String) -> Unit = {}, modifier: Modifier = Modifier,
    onScan: ((PayslipScanner.Scan) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var advice by remember { mutableStateOf<String?>(null) }

    fun read(uri: Uri, taken: Boolean) {
        reading = true
        error = null
        advice = null
        step = null
        scope.launch {
            try {
                val scan = PayslipScanner.scan(context, uri) { n, m -> step = n to m }
                val retake = scan.quality?.takeIf { it.retake }?.line
                if (scan.text.isBlank()) error = "글자를 찾지 못했어요. 밝은 곳에서 명세서가 화면에 꽉 차게 다시 찍어 주세요." + (retake?.let { " $it" } ?: "")
                else {
                    advice = retake
                    if (onScan != null) onScan(scan) else onText(scan.text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.bamnat.app.pay.OcrModel.NotReady) {
                error = e.message
            } catch (e: Exception) {
                error = "사진을 읽지 못했어요. 다른 사진으로 해 보거나 글로 붙여 넣어 주세요."
            } finally {
                if (taken) PayslipScanner.clear(context)
                reading = false
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) read(uri, taken = false) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot
        if (ok && uri != null) read(uri, taken = true) else PayslipScanner.clear(context)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !reading,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("사진 고르기")
            }
            OutlinedButton(
                onClick = {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", PayslipScanner.scanFile(context))
                    shot = uri
                    try {
                        take.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        error = "카메라 앱을 찾지 못했어요. 사진을 찍어 둔 뒤 ‘사진 고르기’로 골라 주세요."
                    }
                },
                enabled = !reading,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("사진 찍기")
            }
        }
        // [READ-15] Edges found, straightened and shadows taken out by Google's scanner on the phone.
        com.bamnat.app.imports.ScannerButton(onImage = { read(it, taken = false) }, onError = { error = it }, enabled = !reading)
        if (reading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(step?.let { (n, m) -> if (m > 0) "  휴대폰 안에서 ${n}/${m}번째 방법으로 읽는 중" else null } ?: "  휴대폰 안에서 글자를 읽고 있어요",
                style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        advice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    }
}

internal fun SlipRole.short(): String = when (this) {
    SlipRole.GROSS, SlipRole.TOTAL -> "지급 총액"
    SlipRole.BASE -> "기본급"
    SlipRole.HOURLY -> "시급"
    SlipRole.OVERTIME -> "연장"
    SlipRole.NIGHT -> "야간"
    SlipRole.HOLIDAY -> "휴일"
    SlipRole.WEEKLY_HOLIDAY -> "주휴"
    SlipRole.EXTRA_WORK -> "잔업·특근·대근"
    SlipRole.BONUS -> "상여"
    SlipRole.ALLOWANCE -> "수당"
    SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL -> "공제"
    SlipRole.NET -> "실수령"
}

/** [PAYS-05] What was read from a payslip, line by line, and whether the lines add up to its total. */
@Composable
internal fun SlipPreview(read: PayslipRead) {
    val shown = read.lines.filter { it.role != SlipRole.DEDUCTION }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                Text(line.role.short(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(76.dp))
                Text(line.name + (line.minutes?.let { " · ${com.bamnat.core.pay.PayCalculator.hours(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(line.amount?.let(::won) ?: "", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val deductions = read.lines.count { it.role == SlipRole.DEDUCTION }
        if (deductions > 0) Text("공제 ${deductions}줄은 빼고 봐요", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        read.gross?.let { g ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (read.consistent) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("  항목을 더하면 지급 총액 ${won(g)}과 맞아요", style = MaterialTheme.typography.bodySmall)
                } else Text("항목 합 ${won(read.earningsSum)} · 지급 총액 ${won(g)}. 빠진 줄이 있으면 맞춰 보기 화면에서 고쳐 주세요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/**
 * [READ-08] The company's way of counting pay, read with the payslip: what a found rule is checked against, and where
 * the ticked ones go (회사 규칙, 묶음 V).
 */
class RuleTarget(
    val kinds: List<ExtraKind>,
    val shiftTypes: List<ShiftType>,
    val fiveOrMore: Boolean,
    val onRules: (List<RuleChange>, java.time.YearMonth?) -> Unit,
)

/**
 * [PAYS-04][PAYS-05] 명세서로 채우기: a photo, a screenshot or pasted text of one payslip becomes its lines, shown
 * before anything is filled. [onRead] gets them once the user agrees.
 * [READ-08] 1.25 (사용자 2026-10-25 "명세서와 회사 계산방법 사진을 두 장 보낼 수 있게"): with [rules], a second photo of the
 * company's pay rules (취업규칙 임금 조항, 수당표) can go with it, in either slot: [PayPhotos] tells which is which. Its
 * rules are listed to tick, and go to [RuleTarget.onRules] with the payslip; 공제·실수령 are shown for the tax estimate.
 * [READ-18] 1.27 순번 447: the photo beside what was read, "사진과 내용이 같은가요?", and on '다른 곳이 있어요' each line fixed
 * where it is (its kind and amount), lines added or taken away; a line read two ways is yellow. A kind the user gives a
 * line's name is learned for the next payslip ([com.bamnat.app.data.ReadFixBook], [SlipNames]).
 * [PAYS-16] 1.27 순번 448: with [rulesOnly], the company's way of counting alone (no payslip) goes on: [onRead] gets an
 * empty payslip with the ticked rules, and the pay is worked out from the rules and the calendar.
 */
@Composable
fun PayslipFillDialog(
    title: String, onDismiss: () -> Unit, rules: RuleTarget? = null, rulesOnly: Boolean = false,
    /** A photo already read (a test, or a photo shared to the app): shown at once. */
    initialScan: PayslipScanner.Scan? = null,
    onRead: (PayslipRead) -> Unit,
) {
    val context = LocalContext.current
    val fixes = remember { com.bamnat.app.data.ReadFixBook.get(context) }
    var text by remember { mutableStateOf(initialScan?.text.orEmpty()) }
    var second by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf<PayslipRead?>(null) }
    // [READ-18] The photo read for each slot (its put-together lines and picture), and what was read before fixing.
    var scan by remember { mutableStateOf(initialScan) }
    var scanSecond by remember { mutableStateOf<PayslipScanner.Scan?>(null) }
    var asRead by remember { mutableStateOf<PayslipRead?>(null) }
    var unsure by remember { mutableStateOf(emptySet<Int>()) }
    var same by remember { mutableStateOf<Boolean?>(null) }
    val ticks = remember { mutableStateListOf<Pair<RuleFinding, Boolean>>() }
    fun analyse() {
        val texts = listOf(text, second)
        val photos = PayPhotos.read(texts.filter { it.isNotBlank() }, rules?.kinds.orEmpty(), rules?.shiftTypes.orEmpty(), rules?.fiveOrMore ?: true)
        // The slot the payslip came from, and its photo's put-together reading when it was read from a photo as it is.
        val slot = texts.withIndex().filter { it.value.isNotBlank() }.getOrNull(photos.slipIndex)?.index
        val voted = slot?.let { listOf(scan, scanSecond)[it]?.takeIf { s -> s.text == texts[it] }?.slip }
        val raw = voted?.read ?: photos.slip ?: PayslipReader.read(text)
        val taught = SlipNames.apply(raw, fixes.slipNames())
        read = taught
        asRead = taught
        unsure = voted?.unsure.orEmpty()
        same = null
        ticks.clear()
        if (rules != null) ticks += photos.rules.map { it to (it.confidence >= 0.6) }
    }
    androidx.compose.runtime.LaunchedEffect(initialScan) { if (initialScan != null) analyse() }
    val photo = scan?.takeIf { it.text == text }?.image ?: scanSecond?.takeIf { it.text == second }?.image
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = read
                if (r == null) {
                    Text(when {
                        rulesOnly -> "명세서 사진, 회사 계산 방법(취업규칙·수당표) 사진, 둘 중 하나만 있어도 돼요. 앱이 휴대폰 안에서 읽고, 사진과 금액은 어디에도 보내지 않아요."
                        rules != null -> "명세서 사진 한 장, 있으면 회사 계산 방법(취업규칙·수당표) 사진 한 장 더. 앱이 휴대폰 안에서 읽고, 사진과 금액은 어디에도 보내지 않아요."
                        else -> "명세서 사진이나 캡처 한 장이면 돼요. 앱이 휴대폰 안에서 읽고, 사진과 금액은 어디에도 보내지 않아요."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rules != null) Text(when {
                        text.isNotBlank() -> "1. 명세서 ✓ 읽었어요"
                        rulesOnly -> "1. 명세서 (없으면 건너뛰어도 돼요)"
                        else -> "1. 명세서"
                    }, style = MaterialTheme.typography.titleSmall)
                    ScanButtons(onScan = { s -> scan = s; text = s.text; if (rules == null) analyse() })
                    if (rules != null) {
                        Text(when {
                            second.isNotBlank() -> "2. 회사 계산 방법 ✓ 읽었어요"
                            rulesOnly -> "2. 회사 계산 방법 (명세서가 없으면 이것만으로도 계산해요)"
                            else -> "2. 회사 계산 방법 (없으면 건너뛰어도 돼요)"
                        }, style = MaterialTheme.typography.titleSmall)
                        ScanButtons(onScan = { s -> scanSecond = s; second = if (second.isBlank()) s.text else second + "\n" + s.text })
                    }
                    // [INPUT-04] A payslip copied from the company's app or a message: one tap. Typing it is folded away.
                    com.bamnat.app.ui.components.ClipboardHint("명세서", fits = { PayslipReader.read(it).lines.isNotEmpty() },
                        onPaste = { t -> text = t; scan = null; if (rules == null) analyse() })
                    TextButton(onClick = { typing = !typing }) { Text(if (typing) "글로 붙여 넣기 접기" else "글로 붙여 넣기") }
                    if (typing) OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("명세서 글") },
                        placeholder = { Text("기본급 2,508,000\n연장근로수당 288,000\n식대 200,000\n지급합계 2,996,000") },
                        minLines = 4, modifier = Modifier.fillMaxWidth(),
                    )
                } else if (r.lines.isEmpty() && ticks.isEmpty()) {
                    Text(if (rulesOnly) "금액도 규칙도 찾지 못했어요. 명세서는 ‘기본급 2,508,000’처럼 이름과 금액이, 회사 계산 방법은 ‘야간 50% 가산’처럼 무엇에 얼마인지 보이게 찍어 주세요."
                        else "금액을 찾지 못했어요. ‘기본급 2,508,000’처럼 이름과 금액이 함께 보이게 찍거나 붙여 넣어 주세요.")
                } else {
                    r.month?.let { Text("${it.year}년 ${it.monthValue}월 명세서", style = MaterialTheme.typography.titleSmall) }
                    if (photo != null) PhotoThumb(photo)
                    if (r.lines.isNotEmpty()) {
                        SlipCheck(r, unsure, same, fromPhoto = photo != null,
                            onAnswer = { same = it },
                            onChange = { next, fixed ->
                                read = next
                                unsure = fixed
                            })
                    } else if (rulesOnly) Text("명세서 없이 회사 계산 방법만으로 계산해요. 기본급·시간 같은 꼭 필요한 값은 급여 화면이 바로 넣게 알려 줘요. " +
                        "명세서를 넣으면 실제 입금액·기본급과 맞춰 볼 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // [READ-08] What the tax estimate (묶음 W) starts from: 공제 합계 and 실수령, worked out when the photo cut them.
                    val net = r.net
                    if (r.deducted != null || net != null) Text(
                        listOfNotNull(r.deducted?.let { "공제 ${won(it)}" }, net?.let { "실수령 ${won(it)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ticks.isNotEmpty()) {
                        Text("회사 계산 방법에서 읽은 규칙 ${ticks.size}개", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                        ticks.forEachIndexed { i, (f, on) ->
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { ticks[i] = f to !on }, verticalAlignment = Alignment.Top) {
                                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                                    Text(f.change.summary, style = MaterialTheme.typography.bodyMedium)
                                    Text("“${f.sourceLine}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else if (second.isNotBlank()) Text("회사 계산 방법 사진에서 규칙을 찾지 못했어요. 규칙은 급여 설정의 ‘회사 규칙’에서 글로 적어도 돼요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            val r = read
            // [READ-18] A photo's payslip not sure (a line read two ways, or lines that do not add up to a stated 지급 총액)
            // waits for "사진과 같은가요?" (조용히 틀린 값 0).
            val sure = r != null && unsure.isEmpty() && com.bamnat.core.pay.SlipVote.adds(r) && com.bamnat.core.pay.SlipVote.stated(r)
            val waiting = r != null && r.lines.isNotEmpty() && photo != null && same == null && !sure
            val canRead = text.isNotBlank() || rulesOnly && second.isNotBlank()
            when {
                r == null -> if (typing || (rules != null && canRead)) Button(onClick = ::analyse, enabled = canRead) { Text("읽기") }
                r.lines.isEmpty() && ticks.isEmpty() -> Button(onClick = { read = null }) { Text("다시 하기") }
                waiting -> Button(onClick = {}, enabled = false) { Text("사진과 같은지 먼저 봐 주세요") }
                else -> Button(onClick = {
                    val chosen = ticks.filter { it.second }.map { it.first.change }
                    asRead?.let { before -> fixes.learnSlip(SlipNames.learned(before, r)) }
                    if (rules != null && chosen.isNotEmpty()) rules.onRules(chosen, r.month)
                    when {
                        r.lines.isNotEmpty() -> onRead(r)
                        rulesOnly && chosen.isNotEmpty() -> onRead(PayslipRead(emptyList(), r.month))
                        else -> onDismiss()
                    }
                }) {
                    Text(when {
                        r.lines.isEmpty() && rulesOnly -> "규칙으로 계산하기"
                        ticks.any { it.second } -> "이대로 채우고 규칙 넣기"
                        else -> "이대로 채우기"
                    })
                }
            }
        },
        dismissButton = {
            if (read != null) TextButton(onClick = { read = null; text = ""; second = ""; ticks.clear(); scan = null; scanSecond = null }) { Text("다시 하기") }
            else TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

/**
 * [PAYS-06] 규칙을 말로 적기: sentences from a 취업규칙, a contract or a colleague, typed, pasted or read from a
 * photo, become changes the user ticks before they apply. Anything the app does not know as a preset becomes a
 * rule of the company's own.
 */
@Composable
internal fun RuleSentenceBox(
    kinds: List<ExtraKind>,
    shiftTypes: List<ShiftType>,
    fiveOrMore: Boolean,
    onApply: (List<RuleChange>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    // [INPUT-03] Rules put together by taps come first; writing or a photo of the 취업규칙 is folded under them.
    var built by remember { mutableStateOf(emptyList<String>()) }
    var words by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Pair<RuleFinding, Boolean>>() }
    var read by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!read) {
            // In the long rules editor the builder opens on a tap, so the sections under it stay close.
            if (building || built.isNotEmpty()) com.bamnat.app.ui.components.RuleBuilder(built, { built = it })
            else com.bamnat.app.ui.components.AddButton("회사 규칙 골라 담기", onClick = { building = true })
            TextButton(onClick = { words = !words }) { Text(if (words || text.isNotBlank()) "글로 적기 접기" else "글로 적거나 사진으로 읽기") }
            if (words || text.isNotBlank()) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("회사 규칙을 적어 주세요") },
                    placeholder = { Text("예)\n토요일 근무는 50% 더\n상여금 연 800% 짝수달") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                ScanButtons(onText = { t -> text = if (text.isBlank()) t else text + "\n" + t })
                Hint("한 줄에 규칙 하나씩 적으면 가장 잘 읽어요. 명세서 금액(연장근로수당 523,000원 같은 줄)은 ‘명세서로 맞춰 보기’에 넣어 주세요.")
            }
            val all = com.bamnat.app.ui.components.ruleWords(built, text)
            Button(
                onClick = {
                    found.clear()
                    found += RuleText.read(all, kinds, shiftTypes, fiveOrMore).map { it to (it.confidence >= 0.6) }
                    read = true
                },
                enabled = all.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("읽기") }
        } else {
            if (found.isEmpty()) Text("읽을 수 있는 규칙을 못 찾았어요. ‘야간 50% 가산’, ‘토요일 근무 1회 2만원’처럼 무엇에 얼마인지 함께 적어 주세요.",
                style = MaterialTheme.typography.bodyMedium)
            found.forEachIndexed { i, (f, on) ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { found[i] = f to !on }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(f.change.summary, style = MaterialTheme.typography.bodyLarge)
                        Text("“${f.sourceLine}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (f.confidence < 0.6) Text("확실하지 않아요. 맞는지 봐 주세요.", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            val chosen = found.filter { it.second }.map { it.first.change }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { read = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 쓰기") }
                Button(
                    onClick = { onApply(chosen); text = ""; built = emptyList(); building = false; found.clear(); read = false },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("${chosen.size}개 넣기") }
            }
        }
    }
}

/** [PAYS-06] The user's own rules, each with its sentence, on or off, or removed. */
@Composable
internal fun CustomRuleList(
    rules: List<CustomRule>,
    shiftNames: Map<Long, String>,
    extraNames: Map<Long, String>,
    onChange: (List<CustomRule>) -> Unit,
) {
    rules.forEach { rule ->
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                    Text(rule.describe(shiftNames, extraNames), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rule.on, onCheckedChange = { on -> onChange(rules.map { if (it.id == rule.id) it.copy(on = on) else it }) })
                IconButton(onClick = { onChange(rules.filterNot { it.id == rule.id }) }) { Icon(Icons.Filled.Delete, "‘${rule.name}’ 지우기") }
            }
        }
    }
}
