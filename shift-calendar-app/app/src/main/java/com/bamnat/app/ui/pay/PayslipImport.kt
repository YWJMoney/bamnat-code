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
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
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
    /**
     * 2.0.0 [RVJ-62] 검토 PAYU-42·50·U05-03: names the place that opens it, so what was read and fixed stays for the app's life when
     * the window goes without the user throwing it away ('직접 문의하기', the screen made again when the phone turns dark): opened
     * again from the same place, it goes on where it was. Null keeps it in this window only, as before.
     */
    keep: String? = null,
    onRead: (PayslipRead) -> Unit,
) {
    val context = LocalContext.current
    val fixes = remember { com.bamnat.app.data.ReadFixBook.get(context) }
    val work = remember(keep) { keep?.let(PayslipWorks::of) ?: PayslipWork() }
    if (work.text.isEmpty() && work.read == null && initialScan != null) { work.text = initialScan.text; work.scan = initialScan }
    var text by work::text
    var second by work::second
    var typing by work::typing
    var read by work::read
    // [READ-18] The photo read for each slot (its put-together lines and picture), and what was read before fixing.
    var scan by work::scan
    var scanSecond by work::scanSecond
    var asRead by work::asRead
    var unsure by work::unsure
    var same by work::same
    // [RULE-ED-01] The rules written as 칸 with no photo: the result opens on them.
    var byHandRules by work::byHandRules
    var reviewAsk by work::reviewAsk
    // 1.36 PC 사진 인식 W7: words read but no amount line found: said, with the words shown to fix, instead of an empty result.
    var notice by work::notice
    // [PAYIN-01] 1.37 B1: the photo that could not be read, kept on screen while the lines are typed by hand.
    var handPhoto by work::handPhoto
    val sheet = work.sheet
    // [RVJ-62] Thrown away (닫기, 버리기, put in): the kept work goes too. Gone without that ('직접 문의하기'): it stays.
    val close = onDismiss
    val dismiss: () -> Unit = { keep?.let(PayslipWorks::drop); close() }
    val away: () -> Unit = { close() }
    fun analyse() {
        val texts = listOf(text, second)
        val photos = PayPhotos.read(texts.filter { it.isNotBlank() }, rules?.kinds.orEmpty(), rules?.shiftTypes.orEmpty(), rules?.fiveOrMore ?: true)
        // The slot the payslip came from, and its photo's put-together reading when it was read from a photo as it is.
        val slot = texts.withIndex().filter { it.value.isNotBlank() }.getOrNull(photos.slipIndex)?.index
        val voted = slot?.let { listOf(scan, scanSecond)[it]?.takeIf { s -> s.text == texts[it] }?.slip }
        val raw = voted?.read ?: photos.slip ?: PayslipReader.read(text)
        val taught = SlipNames.apply(raw, fixes.slipNames())
        if (taught.lines.isEmpty() && photos.rules.isEmpty() && texts.any { it.isNotBlank() }) {
            notice = "글자는 읽었는데 금액 줄을 찾지 못했어요. 아래 읽힌 글에서 ‘기본급 2,508,000’처럼 이름과 금액이 한 줄에 있게 고친 뒤 ‘읽기’를 눌러 주세요."
            typing = true
            return
        }
        notice = null
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
            // 2.0.0 [RVL-17] (검토 SCAN-30) A pay read that changes the pay by much starts off, with the line that says so.
            sheet.reset(com.bamnat.core.pay.RuleSheet.read(findings, photos.unread, from, rules.shiftTypes.associate { it.id to it.name },
                current = com.bamnat.core.pay.RuleSheet.payNow(rules.profile, com.bamnat.core.time.Today.date())))
        }
    }
    // [PHOTO-01] A photo with no words found, or one that failed to read, opens the lines to fill by hand, never a dead end.
    fun byHand(photo: android.graphics.Bitmap? = null) {
        handPhoto = photo
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
        read = null; text = ""; second = ""; scan = null; scanSecond = null; byHandRules = false; handPhoto = null
        sheet.reset(com.bamnat.core.pay.RuleSheet())
    }
    androidx.compose.runtime.LaunchedEffect(initialScan) { if (initialScan != null && read == null) analyse() }
    androidx.compose.runtime.LaunchedEffect(startByHand) { if (startByHand && rules != null && initialScan == null && read == null) rulesByHand() }
    val photo = scan?.takeIf { it.text == text }?.image ?: handPhoto
    val r = read
    if (r == null) {
        PickDialog(
            title, rules, rulesOnly, text, second, typing, notice,
            onText = { text = it; notice = null }, onTyping = { typing = it },
            // 1.36 PC Y3: the payslip read goes straight to its result (the rules' photo, when opened, waits for '읽기').
            onScan = { s -> scan = s; text = s.text; if (rules == null || second.isBlank() && !typing) analyse() },
            onScanSecond = { s -> scanSecond = s; second = if (second.isBlank()) s.text else second + "\n" + s.text },
            onPaste = { t -> text = t; scan = null; if (rules == null) analyse() },
            onEmpty = { p -> byHand(p) }, onByHand = ::rulesByHand, onAnalyse = ::analyse, onDismiss = dismiss,
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
                r.lines.isNotEmpty() -> { keep?.let(PayslipWorks::drop); onRead(r) }
                // [PAYIN-03] 1.37 B3: rules put in with no payslip line went on and the window shut with no word (the setup then said
                // nothing about them); now the caller hears it as an empty payslip with its month, as 규칙으로 계산하기 does.
                chosen.isNotEmpty() -> { keep?.let(PayslipWorks::drop); onRead(PayslipRead(emptyList(), r.month)) }
                else -> dismiss()
            }
        }
    }
    PhotoResultScreen(
        // [J133-77] 알바's rules are the 근무지's.
        title = if (byHandRules) "${com.bamnat.app.ui.flags.rememberScreenStyle()?.employer ?: "회사"} 규칙 칸으로 적기" else title,
        photos = listOfNotNull(photo, scanSecond?.takeIf { it.text == second || second.contains(it.text) }?.image),
        read = r, unsure = unsure, same = same, fromPhoto = photo != null && photo !== handPhoto,
        onSame = { same = it },
        onSlip = { next, fixed -> read = next; unsure = fixed },
        sheet = sheet.takeIf { rules != null }, host = host,
        // [PAYIN-01] PC E10: a payslip typed by hand from its photo opens on the payslip's lines, not the company's rules.
        rulesOnly = (rulesOnly || byHandRules) && handPhoto == null,
        preview = shown.value, working = shown.working, action = action,
        onDismiss = dismiss, onRetake = ::retake, reviewAsk = reviewAsk, plaza = plaza,
        startTab = if (byHandRules) ResultTab.RULES else if (handPhoto != null) ResultTab.SLIP else null,
        slipExtras = {
            // [PHOTO-26] 1.30 AV: a payslip too long for one photo: '+ 한 장 더', read and put together with this one.
            scan?.takeIf { it.text == text && photo != null }?.let { s -> MorePageButton(s) { both -> scan = both; text = both.text; analyse() } }
            // [PHOTO-09] Read from a photo: '사진이 달라요'.
            // 2.0.0 [RVJ-62] 검토 PAYU-42: '직접 문의하기' closed the window and what was read and fixed went with it. With [keep] it stays,
            // and the place that opened it says '넣던 명세서 이어서'.
            if (scan != null) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.PAYSLIP, onAsk = away)
            // [READ-41] PC 11차: what the photo was read as, line by line, as the roster's 무엇을 읽었는지 보기 shows it.
            scan?.let { s -> ReadTextButton(s.text) }
        },
        rulesExtras = {
            if (scanSecond != null || scan != null && r.lines.isEmpty()) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.RULES, onAsk = away)
            (scanSecond ?: scan?.takeIf { r.lines.isEmpty() })?.let { s -> ReadTextButton(s.text) }
        },
    )
}

/**
 * [PAYS-04] The first step: the photos to take (명세서, 회사 계산 방법), a payslip copied, pasted words, and with [rules] '사진 없이 칸으로
 * 적기' for a company where photos are not allowed.
 */
@Composable
private fun PickDialog(
    title: String, rules: RuleTarget?, rulesOnly: Boolean, text: String, second: String, typing: Boolean, notice: String?,
    onText: (String) -> Unit, onTyping: (Boolean) -> Unit,
    onScan: (PayslipScanner.Scan) -> Unit, onScanSecond: (PayslipScanner.Scan) -> Unit, onPaste: (String) -> Unit,
    onEmpty: (android.graphics.Bitmap?) -> Unit, onByHand: () -> Unit, onAnalyse: () -> Unit, onDismiss: () -> Unit,
) {
    val canRead = text.isNotBlank() || rulesOnly && second.isNotBlank()
    // 1.36 PC 사진 인식 W8: back (or 취소) with words typed or read asks before they go.
    var askDrop by remember { mutableStateOf(false) }
    val leave = { if (text.isNotBlank() || second.isNotBlank()) askDrop = true else onDismiss() }
    // W9: one big way first (the payslip's photo); the rules' photo, 칸 and pasting under '다른 방법'.
    var others by remember { mutableStateOf(false) }
    val othersOpen = others || typing || second.isNotBlank()
    // 뿌리 고침 3: the app's one 버릴까요 (계속 적기 / 버리기), in this sheet's words.
    if (askDrop) com.bamnat.app.ui.components.DiscardAsk(onKeep = { askDrop = false }, onDiscard = { askDrop = false; onDismiss() },
        what = "읽은 명세서와 붙여 넣은 글이 사라져요.")
    AlertDialog(
        onDismissRequest = leave,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
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
                    ScanButtons(onScan = onScan, onEmpty = onEmpty, keepSlips = true)
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    // [INPUT-04] A payslip copied from the company's app or a message: one tap.
                    com.bamnat.app.ui.components.ClipboardHint("명세서", fits = { PayslipReader.read(it).lines.isNotEmpty() }, onPaste = onPaste)
                    TextButton(onClick = { others = !othersOpen }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (othersOpen) "다른 방법 접기" else if (rules != null) "다른 방법: 회사 계산 방법 사진 · 칸으로 적기 · 글로 붙여 넣기" else "다른 방법: 글로 붙여 넣기")
                    }
                    if (othersOpen && rules != null) {
                        Text(when {
                            second.isNotBlank() -> "2. 회사 계산 방법 ✓ 읽었어요"
                            rulesOnly -> "2. 회사 계산 방법 (명세서가 없으면 이것만으로도 계산해요)"
                            else -> "2. 회사 계산 방법 (없으면 건너뛰어도 돼요)"
                        }, style = MaterialTheme.typography.titleSmall)
                        // 2.0.0 [RVL-41] A 회사 계산 방법 PDF is no 명세서: its button and its password sheet say so.
                        ScanButtons(onScan = onScanSecond, onEmpty = onEmpty, pdfLabel = "PDF로 받은 회사 계산 방법 고르기")
                        // [RULE-ED-01] 1.30 AW (사용자 "사진 촬영 금지 회사"): the rules as 칸, with common allowances to pick.
                        OutlinedButton(onClick = onByHand, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("사진 없이 칸으로 적기") }
                    }
                    if (othersOpen) TextButton(onClick = { onTyping(!typing) }) { Text(if (typing) "글로 붙여 넣기 접기" else "글로 붙여 넣기") }
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
        dismissButton = { TextButton(onClick = leave) { Text("취소") } },
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
    /** 2.0.0 [RVL-17] (검토 SCAN-30) The pay as set now: a sentence read as a much different pay starts off and says so. */
    current: RuleChange.Base? = null,
    /** 2.0.0 [RVJ-63] Whether something is written, picked or read here (the window around it asks before closing then). */
    onStarted: (Boolean) -> Unit = {},
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
    val started = text.isNotBlank() || built.isNotEmpty() || read
    androidx.compose.runtime.LaunchedEffect(started) { onStarted(started) }
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
                        if (fromPhoto) CellFrom.PHOTO else CellFrom.TEXT, host.shiftNames, current = current))
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

/**
 * 2.0.0 [RVJ-62] 검토 PAYU-42·50·U05-03: what one [PayslipFillDialog] has read and fixed (the photos, the lines, the 칸). Kept in
 * [PayslipWorks] by the place that opens the window, it outlives the window: '직접 문의하기' and back, or the screen made again (the
 * phone turning dark at sunset, a bigger text size), and the window goes on where it was. On the phone only, in memory only.
 */
@androidx.compose.runtime.Stable
internal class PayslipWork {
    var text by mutableStateOf("")
    var second by mutableStateOf("")
    var typing by mutableStateOf(false)
    var read by mutableStateOf<PayslipRead?>(null)
    var scan by mutableStateOf<PayslipScanner.Scan?>(null)
    var scanSecond by mutableStateOf<PayslipScanner.Scan?>(null)
    var asRead by mutableStateOf<PayslipRead?>(null)
    var unsure by mutableStateOf(emptySet<Int>())
    var same by mutableStateOf<Boolean?>(null)
    var byHandRules by mutableStateOf(false)
    var reviewAsk by mutableIntStateOf(0)
    var notice by mutableStateOf<String?>(null)
    var handPhoto by mutableStateOf<android.graphics.Bitmap?>(null)
    val sheet = com.bamnat.app.ui.pay.rulesheet.RuleSheetState(com.bamnat.core.pay.RuleSheet())

    /** Lines read and 칸 filled: what going away would lose. */
    val kept: Int get() = (read?.lines?.size ?: 0) + sheet.sheet.toChanges().size
}

/** 2.0.0 [RVJ-62] The [PayslipWork]s waiting, by the place that opens them (a key of [PayslipFillDialog]'s `keep`). */
internal object PayslipWorks {
    private val works = HashMap<String, PayslipWork>()
    /** Turns when one is thrown away or put in, so a place showing '넣던 명세서 이어서' hears it. */
    private val turn = mutableIntStateOf(0)

    fun of(key: String): PayslipWork = works.getOrPut(key) { PayslipWork() }

    /** One with something read or filled, to go on with; null when there is none. */
    fun waiting(key: String): PayslipWork? {
        turn.intValue
        return works[key]?.takeIf { it.read != null && it.kept > 0 }
    }

    fun drop(key: String) {
        if (works.remove(key) != null) turn.intValue++
    }

    internal fun clearForTest() { works.clear(); turn.intValue++ }

    /** The places that open the window, one key each. */
    const val MATCH = "pay.match"
    const val WIZARD = "pay.wizard"
    const val QUICK = "pay.quick"
    const val WAY = "company.way"
}
