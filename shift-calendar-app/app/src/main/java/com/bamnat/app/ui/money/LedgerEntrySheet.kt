package com.bamnat.app.ui.money

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.db.decodeParts
import com.bamnat.app.data.db.encodeParts
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.money.EntryPart
import com.bamnat.core.money.Ledger
import com.bamnat.core.money.MoneyAccounts
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/** Most months a card payment is spread over. */
private const val MAX_INSTALLMENTS = 36

/**
 * [LEDG-13] The book of the calendar on screen, for editors opened without one (the calendar's
 * day sheet): its accounts, categories and past lines make the smart defaults.
 */
@Composable
fun rememberLedger(given: MoneyBook?): MoneyBook {
    if (given != null) return given
    val c = LocalContext.current.appContainer
    val active by c.calendars.active.collectAsStateWithLifecycle()
    val book by remember(active) { c.calendars.money(active).book }.collectAsStateWithLifecycle(null)
    return book ?: MoneyBook.EMPTY
}

/**
 * Writes or edits one ledger line [LEDG-01], as a sheet with the amount first. An [initial] with
 * id 0 is a new line filled in ahead (from a notification the app could not finish [LEDG-02]).
 * [LEDG-13] A new line starts on today, the last category and account used for its kind, and a
 * place written before fills its category, amount and account with one tap. [LEDG-08] picks the
 * account (a transfer, both ends; a credit card, its installments) and [LEDG-11] adds a memo,
 * tags and parts for other categories.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryDialog(
    date: LocalDate,
    initial: MoneyEntry?,
    defaultKind: MoneyKind = MoneyKind.EXPENSE,
    onSave: (MoneyEntry) -> Unit,
    onDelete: ((MoneyEntry) -> Unit)? = null,
    onDismiss: () -> Unit,
    frequent: (MoneyKind) -> List<Long> = { emptyList() },
    rules: Map<String, String> = emptyMap(),
    book: MoneyBook? = null,
    /** [LEDG-07] Copies a saved line to today. */
    onDuplicate: ((MoneyEntry) -> Unit)? = null,
    /** [LEDG-13] Saves and opens a fresh line, for a pile of receipts. */
    onSaveAndNext: ((MoneyEntry) -> Unit)? = null,
    onEditCategories: (() -> Unit)? = null,
    /** 2.0.0 [RVK-04] (U06a-02, MONEY-27): 저장하고 하나 더 starts on the account of the line just saved. */
    startAccount: Long? = null,
) {
    val ledger = rememberLedger(book)
    val entries = ledger.entries
    val accounts = ledger.activeAccounts
    val isNew = initial == null || initial.id == 0L
    val haptics = LocalHapticFeedback.current
    // 1.38 PC 621c55f4 LOW (돈 적기 시트를 아래로 쓸면 쓰던 금액이 사라져요): a new line closed without 저장 is kept for a while on
    // this phone's memory, and the next 돈 적기 starts from it with a way to clear it, whichever way the sheet was closed.
    // 2.0.0 [RVK-04] (MONEY-27): the draft is this calendar's, and keeps every field (a day changed to yesterday, the card picked).
    val context = LocalContext.current
    val calendarId = remember { runCatching { context.appContainer.calendars.active.value }.getOrDefault("") }
    val draft = remember { if (initial == null) EntryDraft.take(date, calendarId) else null }
    var kind by rememberSaveable { mutableStateOf(draft?.kind ?: initial?.kind ?: defaultKind) }
    val categories = remember(ledger, kind) { ledger.categoriesOf(kind) }
    // A cancellation is stored negative (or, from a kept foreign text, still 0 and marked "결제 취소").
    val cancel = (initial?.amount ?: 0) < 0 || initial?.note?.startsWith("결제 취소") == true
    var digits by rememberSaveable { mutableStateOf(draft?.digits ?: amountDigits(abs(initial?.amount ?: 0))) }
    var category by rememberSaveable {
        mutableStateOf(draft?.category?.takeIf { it in categories } ?: initial?.category ?: Ledger.lastCategory(entries, kind)?.takeIf { it in categories } ?: categories.first())
    }
    var categoryTouched by rememberSaveable { mutableStateOf(initial != null || draft != null) }
    var place by rememberSaveable { mutableStateOf(draft?.place ?: initial?.merchant?.ifBlank { initial.note }.orEmpty()) }
    var day by rememberSaveable { mutableStateOf((draft?.day ?: initial?.date ?: date).toString()) }
    val givenAccount = draft?.account ?: startAccount?.takeIf { id -> ledger.accounts.any { it.id == id } }
    var account by rememberSaveable {
        mutableStateOf(initial?.accountId ?: givenAccount ?: if (isNew) MoneyAccounts.lastUsed(entries, kind, ledger.accounts) else null)
    }
    var toAccount by rememberSaveable { mutableStateOf(draft?.toAccount ?: initial?.toAccountId) }
    var accountTouched by rememberSaveable { mutableStateOf(!isNew || givenAccount != null) }
    // [LEDG-13] Opened from a day, the book may arrive after the sheet: the defaults follow it once, unless already chosen.
    LaunchedEffect(entries.isEmpty(), ledger.accounts.isEmpty()) {
        if (initial != null) return@LaunchedEffect
        if (!accountTouched && account == null) account = MoneyAccounts.lastUsed(entries, kind, ledger.accounts)
        if (!categoryTouched && place.isBlank()) Ledger.lastCategory(entries, kind)?.takeIf { it in categories }?.let { category = it }
    }
    var memo by rememberSaveable { mutableStateOf(draft?.memo ?: initial?.memo.orEmpty()) }
    var fromDraft by rememberSaveable { mutableStateOf(draft != null) }
    // 1.38 (1.37 PC LOW '쓸면 적던 것이 사라져요'): the 한 줄로 적기 text counts as typed too, so a swipe asks first.
    var line by rememberSaveable { mutableStateOf("") }
    var tagsText by rememberSaveable { mutableStateOf(draft?.tags ?: tagsOf(initial)) }
    var partsText by rememberSaveable { mutableStateOf(draft?.parts ?: encodeParts(initial?.parts.orEmpty())) }
    var installments by rememberSaveable { mutableIntStateOf(draft?.installments ?: initial?.installments ?: 1) }
    // 1.32 [LEDX-17] 유이자 할부: the card's 연 수수료율, so each 회차's 수수료 joins the bill.
    var rateText by rememberSaveable { mutableStateOf(draft?.rate ?: rateOf(initial)) }
    // 1.32 [LEDX-16] A refund the app could not tie to its payment: the user picks it here.
    var pickingRefund by remember { mutableStateOf(false) }
    // [FLD-08] 증빙 and a receipt photo, for field and shop costs.
    var proof by rememberSaveable { mutableStateOf(draft?.proof ?: initial?.proof.orEmpty()) }
    var receipt by rememberSaveable { mutableStateOf(draft?.receipt ?: initial?.receipt.orEmpty()) }
    // [LEDG-25] The time a receipt said, for a new line filled from it.
    var readTime by rememberSaveable { mutableStateOf<String?>(null) }
    var scanNote by rememberSaveable { mutableStateOf<String?>(null) }
    val proofs = ledger.style == com.bamnat.core.work.WorkStyle.FIELD || ledger.style == com.bamnat.core.work.WorkStyle.BUSINESS
    var more by rememberSaveable {
        mutableStateOf((initial != null && (initial.memo.isNotBlank() || initial.tags.isNotEmpty() || initial.parts.isNotEmpty())) ||
            (draft != null && (draft.memo.isNotBlank() || draft.tags.isNotBlank() || draft.parts.isNotBlank())))
    }
    var pickingDate by remember { mutableStateOf(false) }
    var placeFocused by remember { mutableStateOf(false) }
    val onDay = LocalDate.parse(day)
    val amount = digits.toAmount()
    val parts = decodeParts(partsText)
    val partsSum = parts.sumOf { it.amount }
    val selected = ledger.account(account)
    val credit = kind == MoneyKind.EXPENSE && selected?.isCredit == true
    val splitOk = parts.isEmpty() || partsSum <= amount
    val amountFocus = remember { FocusRequester() }
    // A new line starts in the amount with the keypad up: amount first, the rest is optional.
    if (isNew && initial == null) LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    fun build(): MoneyEntry {
        // A cancelled payment is stored negative; editing it keeps the sign.
        val signed = if (cancel) -amount else amount
        val base = initial ?: MoneyEntry(date = onDay, kind = kind, amount = signed, category = category)
        return base.copy(
            date = onDay, kind = kind, amount = signed, category = category, merchant = place.trim(),
            note = if (initial?.merchant.isNullOrBlank()) "" else initial!!.note, reviewed = true,
            accountId = account, toAccountId = toAccount.takeIf { kind == MoneyKind.TRANSFER && it != account },
            memo = memo.trim(), tags = Ledger.parseTags(tagsText),
            parts = if (kind == MoneyKind.TRANSFER) emptyList() else parts.filter { it.amount > 0 && it.category != category },
            installments = if (credit) installments else 1,
            installmentRate = if (credit && installments > 1) rateText.toDoubleOrNull()?.coerceIn(0.0, MoneyEntry.MAX_INSTALLMENT_RATE) ?: 0.0 else 0.0,
            proof = if (kind == MoneyKind.EXPENSE) proof else "", receipt = if (kind == MoneyKind.EXPENSE) receipt else "",
            time = readTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: base.time,
        )
    }

    // 1.37 PC 확인 6a4efc92 (높음): 시트 안에서 위로 가려고 아래로 끌면 시트가 닫히고 금액·날짜·카드가 말없이 사라졌어요. 적은 것이
    // 있으면 끌기·뒤로·바깥 누르기는 먼저 물어요 (FormSheet와 같은 방식). 제목 줄의 닫기는 그대로 바로 닫아요.
    // 2.0.0 [RVK-04] (MONEY-27): a change to tags, parts, 할부, 수수료율, 어디로, 증빙 or the receipt counts too, so it is not dropped unasked.
    val typed = if (initial == null) amount > 0 || place.isNotBlank() || memo.isNotBlank() || tagsText.isNotBlank() || line.isNotBlank() ||
        partsText.isNotBlank() || receipt.isNotBlank()
    else amount != abs(initial.amount) || kind != initial.kind || category != initial.category || day != initial.date.toString() ||
        account != initial.accountId || place.trim() != initial.merchant.ifBlank { initial.note }.trim() || memo.trim() != initial.memo.trim() ||
        Ledger.parseTags(tagsText) != initial.tags || partsText != encodeParts(initial.parts) || installments != initial.installments ||
        rateText != rateOf(initial) || toAccount != initial.toAccountId || proof != initial.proof || receipt != initial.receipt
    // Kept whenever the sheet goes without 저장 (a swipe, 뒤로, ✕ or 버리기): 저장 clears it.
    val close = {
        if (initial == null && (amount > 0 || place.isNotBlank() || memo.isNotBlank())) EntryDraft.keep(
            EntryDraft.Saved(calendarId, date, onDay, kind, digits, category, place, memo, account.takeIf { accountTouched }, toAccount,
                tagsText, partsText, installments, rateText, proof, receipt),
        )
        onDismiss()
    }
    com.bamnat.app.ui.components.GuardedSheet(onDismissRequest = close, unsaved = typed, closeButton = false) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            // Title row: what this is, and the line's own actions while editing.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "돈 적기" else "기록 고치기", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
                if (!isNew && onDuplicate != null) {
                    IconButton(onClick = { onDuplicate(initial!!) }) { Icon(Icons.Outlined.ContentCopy, "오늘 날짜로 복사") }
                }
                if (!isNew && onDelete != null) {
                    // PC 확인 5c21f3bb (지우기가 확인 없이 바로, 되돌리기 띠는 금방 사라짐): asks first, as a slide to the left does [SWIPE-01].
                    var askDelete by remember { mutableStateOf(false) }
                    IconButton(onClick = { askDelete = true }) { Icon(Icons.Outlined.Delete, "지우기", tint = MaterialTheme.colorScheme.error) }
                    if (askDelete) com.bamnat.app.ui.components.AlertDialog(
                        onDismissRequest = { askDelete = false },
                        title = { Text("이 기록을 지울까요?") },
                        text = { Text("‘${initial!!.title}’ ${Fmt.won(initial.amount)}. 지운 뒤 나오는 ‘되돌리기’나, 30일 동안 가계부 메뉴 › 최근 지운 기록에서 살릴 수 있어요.") },
                        confirmButton = { TextButton(onClick = { askDelete = false; onDelete(initial!!) }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("취소") } },
                    )
                }
                IconButton(onClick = close) { Icon(Icons.Filled.Close, "닫기") }
            }
            Column(
                Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (fromDraft) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("닫기 전에 적던 내용이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        digits = ""; place = ""; memo = ""; tagsText = ""; partsText = ""; installments = 1; rateText = ""; proof = ""; receipt = ""
                        day = date.toString(); toAccount = null; accountTouched = false
                        account = MoneyAccounts.lastUsed(entries, kind, ledger.accounts); fromDraft = false
                    }) { Text("지우고 새로") }
                }
                com.bamnat.app.ui.components.ChoiceRow(MoneyKind.entries.map { it.label }, selected = kind.ordinal, onSelect = { i ->
                    val k = MoneyKind.entries[i]
                    if (kind != k) {
                        kind = k
                        val list = ledger.categoriesOf(k)
                        if (category !in list) category = (if (isNew) Ledger.lastCategory(entries, k)?.takeIf { it in list } else null) ?: list.first()
                        if (isNew) account = MoneyAccounts.lastUsed(entries, k, ledger.accounts) ?: account
                    }
                })
                // [LEDG-25] A new spending line can be filled from a receipt photo, read on the phone.
                if (isNew && kind == MoneyKind.EXPENSE) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReceiptScanChip(
                            onRead = { d, name ->
                                receipt = name
                                d.amount?.let { digits = amountDigits(it) }
                                d.date?.let { day = it.toString() }
                                readTime = d.time?.toString()
                                if (d.merchant.isNotBlank()) place = d.merchant
                            },
                            onMessage = { scanNote = it },
                        )
                        scanNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
                // 1.37 [LEDH-28] 한 줄로 적기: "점심 9천 카드" fills the amount, place, day, category and card it names.
                if (isNew && kind != MoneyKind.TRANSFER) {
                    var lineOpen by rememberSaveable { mutableStateOf(false) }
                    var lineNote by remember { mutableStateOf<String?>(null) }
                    if (!lineOpen) AssistChip(onClick = { lineOpen = true }, label = { Text("한 줄로 적기") })
                    else {
                        fun fill() {
                            val all = ledger.categoriesOf(MoneyKind.EXPENSE) + ledger.categoriesOf(MoneyKind.INCOME)
                            val d = com.bamnat.core.money.QuickLine.parse(line, all, ledger.accounts)
                            if (d == null) { lineNote = "금액이나 쓴 곳을 적어 주세요"; return }
                            if (d.kind != kind) {
                                kind = d.kind
                                val list = ledger.categoriesOf(kind)
                                if (category !in list) category = Ledger.lastCategory(entries, kind)?.takeIf { it in list } ?: list.first()
                            }
                            d.amount?.let { digits = amountDigits(it) }
                            // 2.0.0 [RVK-01] (PC-01, 9차 PC '버스·택시·약국·통신비가 카페로'): the line's own words choose the category
                            // (a category named in it, the user's rule, then what the words mean), not the last one used. A line it
                            // cannot place goes to '기타' rather than keep someone else's category, unless the user picked one.
                            val list = ledger.categoriesOf(kind)
                            val chosen = d.category?.takeIf { it in list }
                                ?: com.bamnat.core.money.QuickLine.categoryFor(d.place, kind, list, rules)
                                ?: MoneyCategories.OTHER.takeIf { !categoryTouched && it in list }
                            chosen?.let { category = it; categoryTouched = true }
                            if (d.place.isNotBlank()) place = d.place
                            day = com.bamnat.core.time.Today.date().minusDays(d.daysAgo.toLong()).toString()
                            com.bamnat.core.money.QuickLine.account(d, ledger.accounts, entries)?.let { account = it; accountTouched = true }
                            // PC 확인 (조사 '점심로·1일 전로'): what was filled is listed, then said, with no particle glued to it. The category
                            // is listed too, so a wrong guess shows before 저장.
                            lineNote = "채웠어요: " + listOfNotNull(d.amount?.let { Fmt.won(it) }, d.place.takeIf { it.isNotBlank() },
                                "분류 $category",
                                if (d.daysAgo == 1) "어제" else if (d.daysAgo > 1) "${d.daysAgo}일 전" else null).joinToString(" · ") + ". 아래에서 고칠 수 있어요"
                            line = ""
                        }
                        OutlinedTextField(
                            value = line, onValueChange = { line = it.take(60); lineNote = null }, singleLine = true,
                            label = { Text("한 줄로 적기") }, placeholder = { Text("예: 점심 9천 카드, 어제 택시 12,500") },
                            trailingIcon = { TextButton(onClick = { fill() }, enabled = line.isNotBlank()) { Text("채우기") } },
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { fill() }),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        lineNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
                // 2.0.0 [RVK-15] (검토 U06a-01): + − × ÷ under the box while it is typed in ("36000÷3" → 12,000).
                CalcAmountField(
                    if (cancel) "돌려받은 금액" else "금액", digits, { digits = it }, Modifier.focusRequester(amountFocus),
                    textStyle = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
                )
                // 1.37 [LEDH-07] 근무 시간 환산: "근무 6시간이에요", for spending of 5만 원 and more (설정 › 가계부에서 끔).
                if (kind == MoneyKind.EXPENSE && !cancel) LocalWorkTime.current?.let { say -> say.words(amount)?.let { w ->
                    Text("$w${Fmt.ieyo(w)}" + (say.basis()?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                val quick = remember(kind) { frequent(kind) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    quick.forEach { a -> FilterChip(selected = amount == a, onClick = { digits = amountDigits(a) }, label = { Text(Fmt.won(a)) }) }
                    // Cash is counted out in notes: add a note at a time.
                    listOf(10_000L, 50_000L).forEach { step ->
                        AssistChip(onClick = { digits = amountDigits((amount + step).coerceAtMost(99_999_999_999L)) },
                            label = { Text("+${wonBrief(step)}") })
                    }
                }
                DateRow(onDay, onPick = { day = it.toString() }, onOpen = { pickingDate = true })
                OutlinedTextField(
                    value = place,
                    onValueChange = { v ->
                        place = v.take(40)
                        // A place seen before (or a known brand) fills its category until the user picks one.
                        // 2.0.0 [RVK-01]: the same reading as 한 줄로 적기, so '버스' typed here is 교통 too (and 들어온 돈 '월급' is 급여).
                        if (!categoryTouched && kind != MoneyKind.TRANSFER) {
                            com.bamnat.core.money.QuickLine.categoryFor(place.trim(), kind, categories, rules)?.let { category = it }
                        }
                    },
                    singleLine = true,
                    label = { Text(if (kind == MoneyKind.INCOME) "어디서 받은 돈 (선택)" else "어디서·무엇 (선택)") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { placeFocused = it.isFocused },
                )
                val suggestions = remember(entries, place, kind) { Ledger.suggest(entries, place, kind, limit = 4) }
                if (suggestions.isNotEmpty() && (placeFocused || (isNew && place.isBlank() && amount == 0L))) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s ->
                            SuggestionChip(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    place = s.merchant
                                    if (s.category in categories) { category = s.category; categoryTouched = true }
                                    if (amount == 0L) digits = amountDigits(s.amount)
                                    s.accountId?.takeIf { id -> accounts.any { it.id == id } }?.let { account = it; accountTouched = true }
                                },
                                icon = { Icon(Icons.Outlined.History, null, Modifier.size(16.dp)) },
                                label = { Text("${s.merchant} · ${wonBrief(s.amount)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                FieldTitle("분류")
                // 1.37 [LEDH-35] 추천 분류 3개: the three used most in the last 60 days come first, so the long list is seldom read.
                val top = remember(entries, kind, categories) {
                    val since = com.bamnat.core.time.Today.date().minusDays(60)
                    entries.filter { it.kind == kind && !it.date.isBefore(since) && it.category in categories }
                        .groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
                        .takeIf { categories.size > 6 && it.size >= 2 }.orEmpty()
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    (top + categories.filter { it !in top }).forEach { c ->
                        FilterChip(
                            selected = category == c, onClick = { category = c; categoryTouched = true },
                            label = { Text(c) },
                            leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(18.dp), tint = if (category == c) categoryColor(c) else MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                    if (onEditCategories != null) {
                        AssistChip(onClick = onEditCategories, label = { Text("분류 고치기") },
                            leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp)) })
                    }
                }
                if (accounts.isNotEmpty()) {
                    FieldTitle(if (kind == MoneyKind.TRANSFER) "어디서" else if (kind == MoneyKind.INCOME) "들어온 곳" else "결제수단")
                    AccountChips(accounts, account) { account = it; accountTouched = true }
                    if (kind == MoneyKind.TRANSFER) {
                        // fc374a23 PC 확인 (카드값 이체 때 '어디로'가 비어 순자산이 그냥 줄어요): 카드값 goes to a card by itself —
                        // the one most used. Another one can still be picked.
                        androidx.compose.runtime.LaunchedEffect(category, account) {
                            if (toAccount == null && category == "카드값") {
                                val credit = com.bamnat.core.money.AccountType.CREDIT
                                // PC 확인 46200ae4 (카드로 쓴 뒤 첫 카드값 이체에 카드가 '어디서'에 들어감): a card picked as 어디서
                                // (the last account used) is where the money goes; 어디서 becomes the bank most used.
                                val from = accounts.firstOrNull { it.id == account }
                                if (from?.type == credit) {
                                    toAccount = from.id
                                    account = from.payFromId ?: accounts.filter { it.type != credit && it.type != com.bamnat.core.money.AccountType.CHECK }
                                        .maxByOrNull { a -> entries.count { it.accountId == a.id } }?.id
                                // PC 확인 621c55f4 (CardB로 쓴 직후인데 '어디로'가 앞서 이체한 CardA): the card spent on last, not the
                                // one used most.
                                } else toAccount = accounts.filter { it.id != account && it.type == credit }
                                    .maxByOrNull { a -> entries.filter { it.accountId == a.id && it.kind == MoneyKind.EXPENSE }
                                        .maxOfOrNull { it.date.toEpochDay() * 1_000_000_000L + it.id } ?: Long.MIN_VALUE }?.id
                            }
                        }
                        FieldTitle("어디로")
                        AccountChips(accounts.filter { it.id != account }, toAccount) { toAccount = it }
                        if (toAccount == null) Text("어디로를 고르면 통장·카드 잔액과 순자산이 맞게 바뀌어요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (credit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("할부", style = MaterialTheme.typography.bodyLarge)
                            if (installments > 1 && amount > 0) {
                                Text("한 달에 약 ${Fmt.won(amount / installments)}씩 카드값에 들어가요", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(enabled = installments > 1, onClick = { installments-- }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text(if (installments <= 1) "일시불" else "${installments}개월", style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = installments < MAX_INSTALLMENTS, onClick = { installments = if (installments <= 1) 2 else installments + 1 }) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (installments > 1) {
                        OutlinedTextField(value = rateText, onValueChange = { v -> rateText = v.filter { it.isDigit() || it == '.' }.take(5) },
                            label = { Text("할부 수수료 (연 %)") }, placeholder = { Text("무이자면 비워 두세요") }, singleLine = true,
                            visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        val fees = remember(amount, installments, rateText) {
                            rateText.toDoubleOrNull()?.takeIf { it > 0 && amount > 0 }?.let { r ->
                                com.bamnat.core.money.LedgerChecks.installmentFees(MoneyEntry(date = onDay, kind = MoneyKind.EXPENSE, amount = amount, category = "",
                                    installments = installments, installmentRate = r.coerceIn(0.0, MoneyEntry.MAX_INSTALLMENT_RATE)))
                            }
                        }
                        fees?.let { Text("수수료는 모두 약 ${Fmt.won(it)}이에요. 회차마다 남은 원금에 붙어 카드값에 더해져요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                // 1.32 [LEDX-16]
                if (initial != null && com.bamnat.core.money.LedgerChecks.isUnmatchedRefund(initial)) {
                    OutlinedButton(onClick = { pickingRefund = true }, modifier = Modifier.fillMaxWidth()) { Text("원래 결제 고르기") }
                }
                // 1.32 [LEDX-12] The list shows the shop people know; the line keeps what the text said.
                if (place.isNotBlank()) {
                    val shown = remember(place) { com.bamnat.core.money.MerchantNames.display(place.trim()) }
                    if (shown != place.trim()) Text("목록에는 ‘$shown’(으)로 보여요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (kind == MoneyKind.EXPENSE && (proofs || proof.isNotBlank() || receipt.isNotBlank())) {
                    ProofSection(proof, { proof = it }, receipt, { receipt = it })
                }
                if (!initial?.note.isNullOrBlank() && !initial?.merchant.isNullOrBlank()) {
                    Text(initial!!.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    kind == MoneyKind.TRANSFER -> Hint("내 돈을 옮긴 거라 들어온 돈·나간 돈에는 넣지 않아요. 카드값도 여기에 적어요.")
                    kind == MoneyKind.INCOME && category == MoneyCategories.SALARY -> Hint("실제로 받은 급여를 적으면 그달은 예상 급여 대신 이 금액으로 계산해요.")
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { more = !more }.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("메모·태그·나눠 적기", style = MaterialTheme.typography.titleSmall)
                        val summary = listOfNotNull(
                            memo.trim().takeIf { it.isNotEmpty() }?.let { "메모" },
                            Ledger.parseTags(tagsText).takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                            parts.takeIf { it.isNotEmpty() }?.let { "${it.size + 1}개 분류로 나눔" },
                        ).joinToString(" · ")
                        if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (more) "접기" else "펼치기")
                }
                AnimatedVisibility(more) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = memo, onValueChange = { memo = it.take(200) }, label = { Text("메모") },
                            modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        TagsField(tagsText, Ledger.tags(entries).take(8)) { tagsText = it }
                        if (kind != MoneyKind.TRANSFER) {
                            SplitEditor(category, categories, amount, parts) { partsText = encodeParts(it) }
                        }
                    }
                }
                Spacer(Modifier.padding(bottom = 4.dp))
            }
            // The buttons stay in reach above the keyboard.
            HorizontalDivider()
            val ready = amount > 0 && splitOk
            // PC 확인 2b9f19ab: with big letters and the keyboard up, the two stacked buttons covered the 분류 chips; then they sit
            // side by side again, '저장하고 하나 더' said shorter.
            val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            val imeUp = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
            val short = big && imeUp
            val next: (@Composable (Modifier) -> Unit)? = if (isNew && onSaveAndNext != null) { m ->
                OutlinedButton(enabled = ready, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    EntryDraft.clear(); onSaveAndNext(build())
                }, modifier = m.heightIn(min = 48.dp)) { Text(if (short) "하나 더" else "저장하고 하나 더", maxLines = 1) }
            } else null
            val save: @Composable (Modifier) -> Unit = { m ->
                com.bamnat.app.ui.components.SaveButton(enabled = ready, onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    EntryDraft.clear(); onSave(build())
                }, modifier = m.heightIn(min = 48.dp)) { Text("저장") }
            }
            // PC 확인 b4735c07 ("아주 크게"에서 '저장하고 하나 더'가 두 줄로 꺾여요): with big letters the two buttons stack, 저장 first.
            if (big && !imeUp && next != null) Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                save(Modifier.fillMaxWidth())
                next(Modifier.fillMaxWidth())
            } else Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                next?.invoke(Modifier.weight(1f))
                save(Modifier.weight(1f))
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = onDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickingDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("취소") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
    if (pickingRefund && initial != null) {
        val all = book?.entries.orEmpty()
        RefundPickDialog(initial, all,
            onPick = { t -> pickingRefund = false; onSave(com.bamnat.core.money.LedgerChecks.linkRefund(initial, t, all)) },
            onKeep = { pickingRefund = false; onSave(initial.copy(note = initial.note.split(" · ").filter { it != "환불 받은 돈" && it != "원래 결제를 골라 주세요" }.joinToString(" · "), reviewed = true)) },
            onDismiss = { pickingRefund = false })
    }
}

@Composable
private fun FieldTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** ‹ the day › with 오늘 and 어제 one tap away; the date itself opens a calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateRow(onDay: LocalDate, onPick: (LocalDate) -> Unit, onOpen: () -> Unit) {
    val today = Today.date()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPick(onDay.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "전날") }
        TextButton(onClick = onOpen, modifier = Modifier.weight(1f).semantics { contentDescription = "${Fmt.dayTitle(onDay)}, 날짜 고르기" }) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(Fmt.dayTitle(onDay) + if (onDay.year != today.year) " ${onDay.year}" else "", style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center, maxLines = 1)
        }
        IconButton(onClick = { onPick(onDay.plusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음날") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = onDay == today, onClick = { onPick(today) }, label = { Text("오늘") })
        FilterChip(selected = onDay == today.minusDays(1), onClick = { onPick(today.minusDays(1)) }, label = { Text("어제") })
        FilterChip(selected = onDay == today.minusDays(2), onClick = { onPick(today.minusDays(2)) }, label = { Text("그저께") })
    }
}

/** [LEDG-08] The accounts to pick one from; a second tap on the picked one clears it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<com.bamnat.core.money.MoneyAccount>, selected: Long?, onPick: (Long?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text("안 고름") })
        accounts.forEach { a ->
            FilterChip(
                selected = selected == a.id, onClick = { onPick(if (selected == a.id) null else a.id) },
                label = { Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(accountIcon(a.type), null, Modifier.size(18.dp), tint = if (selected == a.id) accountColor(a) else MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }
}

/** [LEDG-11] Tags typed with or without '#', the ones used most a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsField(text: String, recent: List<String>, onChange: (String) -> Unit) {
    val now = Ledger.parseTags(text)
    OutlinedTextField(
        value = text, onValueChange = { onChange(it.take(80)) }, singleLine = true,
        label = { Text("태그") }, placeholder = { Text("#여행 #회식") }, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${Ledger.MAX_TAGS}개까지, 띄어 쓰면 따로 붙어요") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
    if (recent.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            recent.forEach { t ->
                val on = t in now
                FilterChip(selected = on, onClick = {
                    val next = if (on) now - t else (now + t).take(Ledger.MAX_TAGS)
                    onChange(next.joinToString(" ") { "#$it" })
                }, label = { Text("#$t") })
            }
        }
    }
}

/**
 * [LEDG-11] One payment over several categories: each part names its category and amount, and
 * what is left stays with the line's own category.
 */
@Composable
private fun SplitEditor(main: String, categories: List<String>, amount: Long, parts: List<EntryPart>, onChange: (List<EntryPart>) -> Unit) {
    val cal = LocalCalendarColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("나눠 적기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            com.bamnat.app.ui.components.AddButton("분류 더하기", onClick = {
                val next = categories.firstOrNull { c -> c != main && parts.none { it.category == c } }
                if (next != null) onChange(parts + EntryPart(next, 0))
            })
        }
        if (parts.isEmpty()) {
            Hint("장보며 생활용품도 샀다면 한 결제를 여러 분류로 나눠 적을 수 있어요.")
            return@Column
        }
        parts.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPicker(p.category, categories.filter { it != main }, Modifier.weight(1f)) { c ->
                    onChange(parts.mapIndexed { k, q -> if (k == i) q.copy(category = c) else q })
                }
                AmountField("금액", amountDigits(p.amount), { d -> onChange(parts.mapIndexed { k, q -> if (k == i) q.copy(amount = d.toAmount()) else q }) },
                    Modifier.weight(1.3f))
                IconButton(onClick = { onChange(parts.filterIndexed { k, _ -> k != i }) }) { Icon(Icons.Filled.Close, "${p.category} 나눔 빼기") }
            }
        }
        val left = amount - parts.sumOf { it.amount }
        Text(
            if (left < 0) "나눈 금액이 전체보다 ${Fmt.won(-left)} 많아요" else "나머지 ${Fmt.won(left)}은 $main",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color = if (left < 0) Tone.Caution.color() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A category as a button that opens the list to pick another. */
@Composable
fun CategoryPicker(value: String, options: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(categoryIcon(value), null, Modifier.size(18.dp), tint = categoryColor(value))
            Spacer(Modifier.width(6.dp))
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, leadingIcon = { Icon(categoryIcon(c), null, tint = categoryColor(c)) },
                    onClick = { onPick(c); open = false })
            }
        }
    }
}

/**
 * 1.38 PC 621c55f4 LOW: the last new line closed without 저장, in memory only (never on disk or in a backup), for
 * [KEEP_MINUTES], taken once by the next 돈 적기 on the same day.
 */
internal object EntryDraft {
    /**
     * 2.0.0 [RVK-04] (MONEY-27): every field, the calendar it was written in and the day the sheet was [opened] on (the line's own
     * [day] may have been moved to yesterday), so another calendar never gets it and a moved day comes back as it was.
     */
    data class Saved(
        val calendar: String, val opened: LocalDate, val day: LocalDate, val kind: MoneyKind, val digits: String, val category: String,
        val place: String, val memo: String, val account: Long? = null, val toAccount: Long? = null, val tags: String = "",
        val parts: String = "", val installments: Int = 1, val rate: String = "", val proof: String = "", val receipt: String = "",
    )

    private const val KEEP_MINUTES = 30L
    private var saved: Saved? = null
    private var at: Long = 0L

    fun keep(d: Saved) { saved = d; at = com.bamnat.core.time.Today.millis() }

    fun clear() { saved = null }

    fun take(opened: LocalDate, calendar: String): Saved? {
        val d = saved ?: return null
        if (d.calendar != calendar) return null
        saved = null
        return d.takeIf { it.opened == opened && com.bamnat.core.time.Today.millis() - at < KEEP_MINUTES * 60_000 }
    }
}

/** The tags of [e] as the tag field shows them. */
private fun tagsOf(e: MoneyEntry?): String = e?.tags.orEmpty().joinToString(" ") { "#$it" }

/** [LEDX-17] The 연 수수료율 of [e] as the field shows it ("" for none). */
private fun rateOf(e: MoneyEntry?): String = e?.installmentRate?.takeIf { it > 0 }?.let { "%.2f".format(it).trimEnd('0').trimEnd('.') }.orEmpty()
