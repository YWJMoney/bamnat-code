package com.bamnat.app.ui.pay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.components.edgeScrollViewport
import com.bamnat.app.ui.pay.rulesheet.RuleSheetCells
import com.bamnat.app.ui.pay.rulesheet.RuleSheetHost
import com.bamnat.app.ui.pay.rulesheet.SheetAction
import com.bamnat.app.ui.pay.rulesheet.rememberRuleSheetState
import com.bamnat.app.ui.pay.rulesheet.rememberSheetPreview
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.CellFrom
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.PayPhotos
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipRole

// [PHOTO-20] 1.30 AV: ScanButtons moved to ScanButtons.kt.

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
    /**
     * [PHOTO-06] 1.29: the pay setup and calendar a rule cell made here is worked out on ("10월엔 +80,000원"); null shows the
     * cell without the month's amount. Before [onRules], so callers that pass it as a trailing lambda keep working.
     */
    val profile: com.bamnat.core.pay.PayProfile? = null,
    val snapshot: com.bamnat.app.data.ScheduleSnapshot? = null,
    val effectOf: (suspend (com.bamnat.core.pay.PayProfile, com.bamnat.app.data.ScheduleSnapshot, java.time.YearMonth, MadeItem) -> ItemEffect?)? = null,
    val onRules: (List<RuleChange>, java.time.YearMonth?) -> Unit,
)

/**
 * [PAYS-04][PAYS-05] 명세서로 채우기: a photo, a screenshot or pasted text of one payslip becomes its lines, shown
 * before anything is filled. [onRead] gets them once the user agrees.
 * [READ-08] 1.25 (사용자 2026-10-25 "명세서와 회사 계산방법 사진을 두 장 보낼 수 있게"): with [rules], a second photo of the
 * company's pay rules (취업규칙 임금 조항, 수당표) can go with it, in either slot: [PayPhotos] tells which is which. Its
 * rules go to [RuleTarget.onRules] with the payslip; 공제·실수령 are shown for the tax estimate.
 * [READ-18] 1.27 순번 447: the photo beside what was read, "사진과 내용이 같은가요?", each line fixed where it is; a line read
 * two ways is yellow. A kind the user gives a line's name is learned for the next payslip ([com.bamnat.app.data.ReadFixBook], [SlipNames]).
 * [PAYS-16] 1.27 순번 448: with [rulesOnly], the company's way of counting alone (no payslip) goes on: [onRead] gets an
 * empty payslip with the rules, and the pay is worked out from the rules and the calendar.
 * [PHOTO-30] 1.30 AW: what was read opens on one full screen ([PhotoResultScreen]) with two tabs (명세서 줄 · 회사 규칙), the rules as
 * the one list of 칸 ([com.bamnat.core.pay.RuleSheet]) with the 통상시급 card, and under them what the month comes to ([preview]).
 * [RULE-ED-01] '사진 없이 칸으로 적기' (촬영이 안 되는 회사) opens the same screen with no photo, on the 칸.
 */
@Composable
fun PayslipFillDialog(
    title: String, onDismiss: () -> Unit, rules: RuleTarget? = null, rulesOnly: Boolean = false,
    /** A photo already read (a test, or a photo shared to the app): shown at once. */
    initialScan: PayslipScanner.Scan? = null,
    /**
     * [PHOTO-34] What the payslip and the rules come to, worked out off the main thread (the setup with them, the month's pay, and with
     * a payslip how far apart they are). Null shows the payslip's own total under the result.
     */
    preview: (suspend (PayslipRead, List<RuleChange>) -> com.bamnat.app.ui.pay.rulesheet.SheetPreview?)? = null,
    /** [HOURLY-04] The company's way in the 광장 (기준 시간), for the 통상시급 card. */
    plaza: com.bamnat.app.ui.company.CompanyWayText? = null,
    /** [RULE-ED-01] Opens on the 칸 with no photo (촬영이 안 되는 회사). */
    startByHand: Boolean = false,
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
    // [RULE-ED-01] The rules written as 칸 with no photo: the result opens on them.
    var byHandRules by remember { mutableStateOf(false) }
    var reviewAsk by remember { mutableIntStateOf(0) }
    val sheet = rememberRuleSheetState()
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
        byHandRules = false
        if (rules != null) {
            // [PHOTO-11] 1.29: the 통상시급 the payslip's own 가산 lines were worked out on ("명세서로 보면 통상시급 21,618원이에요").
            // [HOURLY-01..03] 1.30 AV: also the 기준 시간 and a 배율 other than the law's; a guess from one line starts unticked.
            // [PHOTO-30] 1.30 AW: the 기준 시간·통상시급 go on the 통상시급 card, the rest are 칸.
            val findings = photos.rules + com.bamnat.core.pay.SlipHourly.findings(taught, photos.rules)
            val from = if (scan != null || scanSecond != null) CellFrom.PHOTO else CellFrom.TEXT
            sheet.reset(com.bamnat.core.pay.RuleSheet.read(findings, photos.unread, from, rules.shiftTypes.associate { it.id to it.name }))
        }
    }
    // [PHOTO-01] A photo with no words found, or one that failed to read, opens the lines to fill by hand, never a dead end.
    fun byHand() {
        val empty = PayslipRead(emptyList(), null)
        read = empty
        asRead = empty
        unsure = emptySet()
        same = false
        sheet.reset(com.bamnat.core.pay.RuleSheet())
    }
    // [RULE-ED-01] No photo at all: the company's rules as 칸.
    fun rulesByHand() {
        byHand()
        byHandRules = true
    }
    fun retake() {
        read = null; text = ""; second = ""; scan = null; scanSecond = null; byHandRules = false
        sheet.reset(com.bamnat.core.pay.RuleSheet())
    }
    androidx.compose.runtime.LaunchedEffect(initialScan) { if (initialScan != null) analyse() }
    androidx.compose.runtime.LaunchedEffect(startByHand) { if (startByHand && rules != null && initialScan == null) rulesByHand() }
    val photo = scan?.takeIf { it.text == text }?.image
    val r = read
    if (r == null) {
        PickDialog(
            title, rules, rulesOnly, text, second, typing,
            onText = { text = it }, onTyping = { typing = it },
            onScan = { s -> scan = s; text = s.text; if (rules == null) analyse() },
            onScanSecond = { s -> scanSecond = s; second = if (second.isBlank()) s.text else second + "\n" + s.text },
            onPaste = { t -> text = t; scan = null; if (rules == null) analyse() },
            onEmpty = ::byHand, onByHand = ::rulesByHand, onAnalyse = ::analyse, onDismiss = onDismiss,
        )
        return
    }
    val month = r.month ?: com.bamnat.core.time.Today.month()
    val host = rules?.let { t ->
        val profile = t.profile ?: PhotoCellsLogic.blank
        val snapshot = t.snapshot ?: com.bamnat.app.data.ScheduleSnapshot(t.shiftTypes, emptyList(), emptyList())
        val made: (suspend (MadeItem) -> ItemEffect?)? = t.effectOf?.let { e -> { m -> e(profile, snapshot, month, m) } }
        val total: (suspend (List<RuleChange>) -> Long?)? = preview?.let { p -> { changes -> p(r, changes)?.total } }
        RuleSheetHost(profile, snapshot, month, RuleSheetHost.effectOf(made, total) { sheet.sheet })
    }
    val previewOf: (suspend (List<RuleChange>) -> com.bamnat.app.ui.pay.rulesheet.SheetPreview?)? =
        remember(r, preview) { preview?.let { p -> { changes -> p(r, changes) } } }
    val shown = rememberSheetPreview(sheet.sheet, previewOf.takeIf { rules != null })
    val chosen = if (rules != null) sheet.sheet.toChanges() else emptyList()
    // [READ-18] A photo's payslip not sure (a line read two ways, or lines that do not add up to a stated 지급 총액)
    // waits for "사진과 같은가요?" (조용히 틀린 값 0).
    val sure = unsure.isEmpty() && com.bamnat.core.pay.SlipVote.adds(r) && com.bamnat.core.pay.SlipVote.stated(r)
    val waiting = r.lines.isNotEmpty() && photo != null && same == null && !sure
    val action = when {
        r.lines.isEmpty() && chosen.isEmpty() -> SheetAction("칸을 넣어 주세요", enabled = false) {}
        // [PHOTO-30] The button sends the user to what is left to look at, never a grey button with nothing to press.
        waiting -> SheetAction(if (unsure.isNotEmpty()) "노란 ${unsure.size}줄 확인하기" else "명세서 줄이 사진과 같은지 봐 주세요") { reviewAsk++ }
        else -> SheetAction(when {
            r.lines.isEmpty() && (rulesOnly || byHandRules) -> "규칙으로 계산하기"
            chosen.isNotEmpty() -> "이대로 채우고 규칙 넣기"
            else -> "이대로 채우기"
        }) {
            asRead?.let { before -> fixes.learnSlip(SlipNames.learned(before, r)) }
            // [PHOTO-24] 1.30 AV: the payslip as confirmed, kept as the company's template for next month's photo.
            scan?.takeIf { it.text == text && r.lines.isNotEmpty() && it.boxes.isNotEmpty() }?.let { s -> fixes.learnTemplate(r, s.boxes) }
            if (rules != null && chosen.isNotEmpty()) rules.onRules(chosen, r.month)
            when {
                r.lines.isNotEmpty() -> onRead(r)
                (rulesOnly || byHandRules) && chosen.isNotEmpty() -> onRead(PayslipRead(emptyList(), r.month))
                else -> onDismiss()
            }
        }
    }
    PhotoResultScreen(
        // [J133-77] 알바's rules are the 근무지's.
        title = if (byHandRules) "${com.bamnat.app.ui.flags.rememberScreenStyle()?.employer ?: "회사"} 규칙 칸으로 적기" else title,
        photos = listOfNotNull(photo, scanSecond?.takeIf { it.text == second || second.contains(it.text) }?.image),
        read = r, unsure = unsure, same = same, fromPhoto = photo != null,
        onSame = { same = it },
        onSlip = { next, fixed -> read = next; unsure = fixed },
        sheet = sheet.takeIf { rules != null }, host = host,
        rulesOnly = rulesOnly || byHandRules,
        preview = shown.value, working = shown.working, action = action,
        onDismiss = onDismiss, onRetake = ::retake, reviewAsk = reviewAsk, plaza = plaza,
        startTab = if (byHandRules) ResultTab.RULES else null,
        slipExtras = {
            // [PHOTO-26] 1.30 AV: a payslip too long for one photo: '+ 한 장 더', read and put together with this one.
            scan?.takeIf { it.text == text && photo != null }?.let { s -> MorePageButton(s) { both -> scan = both; text = both.text; analyse() } }
            // [PHOTO-09] Read from a photo: '사진이 달라요'.
            if (scan != null) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.PAYSLIP, onAsk = onDismiss)
        },
        rulesExtras = {
            if (scanSecond != null || scan != null && r.lines.isEmpty()) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.RULES, onAsk = onDismiss)
        },
    )
}

/**
 * [PAYS-04] The first step: the photos to take (명세서, 회사 계산 방법), a payslip copied, pasted words, and with [rules] '사진 없이 칸으로
 * 적기' for a company where photos are not allowed.
 */
@Composable
private fun PickDialog(
    title: String, rules: RuleTarget?, rulesOnly: Boolean, text: String, second: String, typing: Boolean,
    onText: (String) -> Unit, onTyping: (Boolean) -> Unit,
    onScan: (PayslipScanner.Scan) -> Unit, onScanSecond: (PayslipScanner.Scan) -> Unit, onPaste: (String) -> Unit,
    onEmpty: () -> Unit, onByHand: () -> Unit, onAnalyse: () -> Unit, onDismiss: () -> Unit,
) {
    val canRead = text.isNotBlank() || rulesOnly && second.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // [MEMO-SEL-02] 1.30 AY: a long payslip pasted: its selection handles scroll the dialog.
            val scroll = rememberScrollState()
            val edge = com.bamnat.app.ui.components.rememberEdgeScroll(scroll)
            CompositionLocalProvider(com.bamnat.app.ui.components.LocalEdgeScroll provides edge) {
                Column(Modifier.edgeScrollViewport(edge).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    ScanButtons(onScan = onScan, onEmpty = onEmpty)
                    if (rules != null) {
                        Text(when {
                            second.isNotBlank() -> "2. 회사 계산 방법 ✓ 읽었어요"
                            rulesOnly -> "2. 회사 계산 방법 (명세서가 없으면 이것만으로도 계산해요)"
                            else -> "2. 회사 계산 방법 (없으면 건너뛰어도 돼요)"
                        }, style = MaterialTheme.typography.titleSmall)
                        ScanButtons(onScan = onScanSecond, onEmpty = onEmpty)
                        // [RULE-ED-01] 1.30 AW (사용자 "사진 촬영 금지 회사"): the rules as 칸, with common allowances to pick.
                        OutlinedButton(onClick = onByHand, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("사진 없이 칸으로 적기") }
                    }
                    // [INPUT-04] A payslip copied from the company's app or a message: one tap. Typing it is folded away.
                    com.bamnat.app.ui.components.ClipboardHint("명세서", fits = { PayslipReader.read(it).lines.isNotEmpty() }, onPaste = onPaste)
                    TextButton(onClick = { onTyping(!typing) }) { Text(if (typing) "글로 붙여 넣기 접기" else "글로 붙여 넣기") }
                    if (typing) com.bamnat.app.ui.components.LongTextField(
                        value = text, onValueChange = onText,
                        label = { Text("명세서 글") },
                        placeholder = { Text("기본급 2,508,000\n연장근로수당 288,000\n식대 200,000\n지급합계 2,996,000") },
                        minLines = 4, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { if (typing || (rules != null && canRead)) Button(onClick = onAnalyse, enabled = canRead) { Text("읽기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [PAYS-06] 규칙을 말로 적기: sentences from a 취업규칙, a contract or a colleague, typed, pasted or read from a
 * photo, become changes the user ticks before they apply. Anything the app does not know as a preset becomes a
 * rule of the company's own.
 * [RULE-ED-02] 1.30 AW: what was read is the same list of 칸 as the photo's result ([RuleSheetCells]).
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
    // [PHOTO-04][PHOTO-06] 1.29: whether the words came from a photo ('사진이 달라요').
    var fromPhoto by remember { mutableStateOf(false) }
    var read by remember { mutableStateOf(false) }
    val sheet = rememberRuleSheetState()
    val month = com.bamnat.core.time.Today.month()
    val host = remember(shiftTypes) {
        RuleSheetHost(PhotoCellsLogic.blank, com.bamnat.app.data.ScheduleSnapshot(shiftTypes, emptyList(), emptyList()), month)
    }
    val employer = com.bamnat.app.ui.flags.rememberScreenStyle()?.employer ?: "회사" // [J133-77]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!read) {
            // In the long rules editor the builder opens on a tap, so the sections under it stay close.
            if (building || built.isNotEmpty()) com.bamnat.app.ui.components.RuleBuilder(built, { built = it })
            else com.bamnat.app.ui.components.AddButton("$employer 규칙 골라 담기", onClick = { building = true })
            TextButton(onClick = { words = !words }) { Text(if (words || text.isNotBlank()) "글로 적기 접기" else "글로 적거나 사진으로 읽기") }
            if (words || text.isNotBlank()) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("$employer 규칙을 적어 주세요") },
                    placeholder = { Text("예)\n토요일 근무는 50% 더\n상여금 연 800% 짝수달") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                ScanButtons(onText = { t -> text = if (text.isBlank()) t else text + "\n" + t; fromPhoto = true })
                Hint("한 줄에 규칙 하나씩 적으면 가장 잘 읽어요. 명세서 금액(연장근로수당 523,000원 같은 줄)은 ‘명세서로 맞춰 보기’에 넣어 주세요.")
            }
            val all = com.bamnat.app.ui.components.ruleWords(built, text)
            Button(
                onClick = {
                    sheet.reset(com.bamnat.core.pay.RuleSheet.read(
                        RuleText.read(all, kinds, shiftTypes, fiveOrMore), RuleText.unread(all, kinds, shiftTypes, fiveOrMore),
                        if (fromPhoto) CellFrom.PHOTO else CellFrom.TEXT, host.shiftNames))
                    read = true
                },
                enabled = all.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("읽기") }
        } else {
            if (sheet.sheet.isEmpty) Text("읽을 수 있는 규칙을 못 찾았어요. ‘야간 50% 가산’, ‘토요일 근무 1회 2만원’처럼 무엇에 얼마인지 함께 적거나 아래 ‘칸 더하기’로 넣어 주세요.",
                style = MaterialTheme.typography.bodyMedium)
            // [PHOTO-06][RULE-ED-02] One flow with the photo's result: 칸 to tick, change or take out, '직접 넣기' rows, '칸 더하기'.
            RuleSheetCells(sheet, host)
            if (fromPhoto) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.RULES)
            val chosen = sheet.sheet.toChanges()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { read = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 쓰기") }
                Button(
                    onClick = { onApply(chosen); text = ""; built = emptyList(); building = false; sheet.reset(com.bamnat.core.pay.RuleSheet()); fromPhoto = false; read = false },
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
