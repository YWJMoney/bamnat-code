package com.bamnat.app.ui.money

import com.bamnat.app.ui.theme.fillWith
import com.bamnat.app.ui.parts.fillColor
import com.bamnat.app.ui.parts.shape
import com.bamnat.app.ui.parts.text
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilledTonalButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.money.Ledger
import com.bamnat.core.money.LedgerCsv
import com.bamnat.core.money.LedgerFilter
import com.bamnat.core.money.LedgerPeriod
import com.bamnat.core.money.LedgerSort
import com.bamnat.core.money.LedgerTotals
import com.bamnat.core.money.MoneyAccounts
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.bamnat.app.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import com.bamnat.core.time.Today

/** [LEDG-07] What the 내역 list shows: a period counted from the month on screen, conditions and an order. */
data class LedgerView(
    val period: LedgerPeriod = LedgerPeriod.MONTH,
    val filter: LedgerFilter = LedgerFilter(),
    val sort: LedgerSort = LedgerSort.NEWEST,
) {
    /** 2.0.0 [RVK-20] (U06a-07·MONEY-18) With 급여일 주기 on, '이 달' is the pay [cycle] ("10/25~11/24"). */
    fun range(month: YearMonth, cycle: com.bamnat.core.money.PayCycle.Range? = null): Pair<LocalDate?, LocalDate?> =
        if (period == LedgerPeriod.MONTH && cycle != null) cycle.from to cycle.to else period.range(month)
    fun applied(month: YearMonth, cycle: com.bamnat.core.money.PayCycle.Range? = null): LedgerFilter =
        range(month, cycle).let { (from, to) -> filter.copy(from = from, to = to) }
    val narrowed: Boolean get() = filter.count > 0 || filter.query.isNotBlank()
}

/** [SWIPE-01] How far a line must slide, as a share of its width, before letting go acts on it. */
internal const val SWIPE_THRESHOLD = 0.5f

/**
 * [SWIPE-01] Asked before a line slid to the left goes: what it is, so the right one is removed.
 * After 삭제 the money view model offers 되돌리기, which puts the same line back.
 */
@Composable
internal fun DeleteLineDialog(e: MoneyEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = listOfNotNull(Fmt.shortDate(e.date), e.title.ifBlank { e.category }, signedWon(e)).joinToString(" · ")
    com.bamnat.app.ui.components.ConfirmDialog(
        title = "지울까요?",
        message = "$what\n\n지운 뒤 나오는 ‘되돌리기’나, 30일 동안 가계부 메뉴 › 최근 지운 기록에서 살릴 수 있어요.",
        confirmLabel = "지우기",
        destructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** What the list can do to its lines; the money view model does it, with undo. */
interface LedgerListActions {
    fun open(e: MoneyEntry)
    fun delete(list: List<MoneyEntry>)
    fun duplicate(e: MoneyEntry)
    fun recategorize(list: List<MoneyEntry>, category: String)
    fun message(text: String)
}

/**
 * [LEDG-07] Every line, found by words and conditions over any period, in days with their own
 * totals. A line slides right to copy it to today and left to remove it (asked first [SWIPE-01], with undo); a long press
 * picks several to move to a category or remove together. Screen readers get the same actions.
 * [LEDG-12] What is shown can leave as a CSV file. On a wide screen the conditions stay open beside
 * the list.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerListPane(
    book: MoneyBook,
    month: YearMonth,
    view: LedgerView,
    onView: (LedgerView) -> Unit,
    selection: Set<Long>,
    onSelection: (Set<Long>) -> Unit,
    actions: LedgerListActions,
    wide: Boolean,
    onAdd: () -> Unit,
    /** 2.0.0 [RVK-20] The pay cycle '이 달' stands for, with 급여일 주기 on. */
    cycle: com.bamnat.core.money.PayCycle.Range? = null,
) {
    val filter = remember(view, month, cycle) { view.applied(month, cycle) }
    val shown = remember(book, filter, view.sort) { Ledger.search(book.entries, filter, view.sort, book.accounts) }
    // 1.37 [LEDH-36] 평소보다 큰 지출, marked quietly on its line.
    val bigOnes = remember(book.entries) { com.bamnat.core.money.LedgerSmarts.unusual(book.entries, com.bamnat.core.time.Today.date()) }
    val totals = remember(shown, filter) { Ledger.totals(shown, filter) }
    val byDate = view.sort == LedgerSort.NEWEST || view.sort == LedgerSort.OLDEST
    val days = remember(shown, byDate) { if (byDate) Ledger.days(shown) else emptyList() }
    // 2.0.0 [RVK-03] (MONEY-01): open sheets come back after a rebuilt screen.
    var filtering by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    // 2.0.0 [RVK-10] (MONEY-04·U06a-11): the 휴지통 of picked lines asks first, saying how many and how much ('모두 고르기' with
    // 전체 can be the whole 가계부); what goes stays 30 days in 최근 지운 기록.
    var askingDelete by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    val picked = remember(shown, selection) { shown.filter { it.id in selection } }
    BackHandler(enabled = selecting) { onSelection(emptySet()) }
    // Lines picked and then filtered away are let go.
    LaunchedEffect(shown) { if (selecting) onSelection(selection.filter { id -> shown.any { it.id == id } }.toSet()) }

    // [TAB-06] 1.36 가계부 판: kept with the other tabs' views in settings.
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val appSettings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val board = remember(appSettings.tabViews) { com.bamnat.core.layout.TabViews.decode(appSettings.tabViews).money }
    val pickBoard: (com.bamnat.core.money.LedgerBoard) -> Unit = { b ->
        container.settingsRepository.update { it.copy(tabViews = com.bamnat.core.layout.TabViews.decode(it.tabViews).copy(money = b).encode()) }
    }
    // 1.38 PC 7차: 기간이 이 달보다 길면 달력은 그 안의 달을 따로 넘겨요(처음엔 보고 있던 달).
    var calMonth by remember(month, view.period) { mutableStateOf(month) }
    val calWithin: ClosedRange<YearMonth>? = remember(view.period, month, shown) {
        if (view.period == LedgerPeriod.MONTH) null else {
            val (from, to) = view.period.range(month)
            val dates = shown.map { it.date }
            val start = (from ?: dates.minOrNull())?.let(YearMonth::from) ?: month
            val end = (to ?: dates.maxOrNull())?.let(YearMonth::from) ?: month
            minOf(start, month)..maxOf(end, month)
        }
    }
    var pickedDay by remember(month, calMonth) { mutableStateOf<LocalDate?>(null) }
    var tableSort by remember { mutableStateOf("date" to true) }
    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            if (selecting) {
                SelectionBar(picked.size, all = picked.size == shown.size,
                    onClose = { onSelection(emptySet()) },
                    onAll = { onSelection(if (picked.size == shown.size) emptySet() else shown.map { it.id }.toSet()) },
                    onMove = { moving = true },
                    onDelete = { askingDelete = true })
            }
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FabClearance + 8.dp)) {
                if (!wide) {
                    item(key = "tools") {
                        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchRow(view, onView, onFilter = { filtering = true }, onExport = { exporting = true })
                            PeriodChips(view, month, onView, cycle)
                            ActiveConditions(book, view, onView)
                            if (shown.isNotEmpty()) TotalsStrip(totals, view.filter.categories.isNotEmpty())
                            // PC 확인 bad7d860 사용성 제안: the 판 chips show before the first line too, so they are found.
                            LedgerBoardChips(board, pickBoard)
                        }
                    }
                }
                if ((shown.isNotEmpty() || board == com.bamnat.core.money.LedgerBoard.CAL) && board != com.bamnat.core.money.LedgerBoard.LIST) item(key = "board-${board.key}") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        when (board) {
                            com.bamnat.core.money.LedgerBoard.CAL -> LedgerMonthGrid(if (calWithin == null) month else calMonth, shown, pickedDay,
                                { pickedDay = if (pickedDay == it) null else it }, calWithin, { calMonth = it })
                            com.bamnat.core.money.LedgerBoard.BOARD -> LedgerCategoryBoard(shown, actions::open)
                            com.bamnat.core.money.LedgerBoard.TABLE -> LedgerTable(shown, tableSort, { tableSort = it }, actions::open)
                            com.bamnat.core.money.LedgerBoard.LIST -> Unit
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        when {
                            // 2.0.0 [RVK-35] (PC-126) One '돈 적기': the floating one, always there (this one stood beside it). Named as it
                            // shows: with big letters it is only its +.
                            book.entries.isEmpty() -> LedgerEmpty(Icons.AutoMirrored.Outlined.ReceiptLong, "아직 적은 돈이 없어요",
                                (if (com.bamnat.app.ui.components.bigLetters()) "아래 + 단추" else "아래 ‘돈 적기’") + "를 누르거나 위 ⋮ › 자동 기록을 켜면 여기에 쌓여요.",
                                kani = com.bamnat.app.ui.components.KaniMood.Point)
                            view.narrowed -> LedgerEmpty(Icons.Outlined.SearchOff, "조건에 맞는 기록이 없어요",
                                "기간을 넓히거나 조건을 지워 보세요.", action = "조건 지우기", onAction = { onView(view.copy(filter = LedgerFilter())) })
                            else -> LedgerEmpty(Icons.AutoMirrored.Outlined.ReceiptLong, "이 기간에 적은 돈이 없어요",
                                "다른 달로 넘기거나 기간을 ‘전체’로 바꿔 보세요.", action = "전체 기간 보기", onAction = { onView(view.copy(period = LedgerPeriod.ALL)) })
                        }
                    }
                }
                if (board == com.bamnat.core.money.LedgerBoard.BOARD || board == com.bamnat.core.money.LedgerBoard.TABLE) {
                    // The board or table above holds the lines.
                } else if (board == com.bamnat.core.money.LedgerBoard.CAL) {
                    // [TAB-06] Under the month, the picked day's lines (or, with none picked, a word on how).
                    val day = pickedDay
                    val lines = if (day == null) emptyList() else shown.filter { it.date == day }
                    if (day == null) item(key = "cal-hint") {
                        Text("날짜를 누르면 그날 적은 돈이 여기 나와요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(lines, key = { it.id }) { e ->
                        LedgerRow(e, book, selecting, e.id in selection, actions, big = e.id in bigOnes,
                            onToggle = { onSelection(if (e.id in selection) selection - e.id else selection + e.id) },
                            modifier = Modifier.animateItem())
                    }
                } else if (byDate) {
                    days.forEach { d ->
                        stickyHeader(key = "d${d.date}") { DayHeader(d.date, d.expense, d.income) }
                        items(d.entries, key = { it.id }) { e ->
                            LedgerRow(e, book, selecting, e.id in selection, actions, big = e.id in bigOnes,
                                onToggle = { onSelection(if (e.id in selection) selection - e.id else selection + e.id) },
                                modifier = Modifier.animateItem())
                        }
                    }
                } else {
                    items(shown, key = { it.id }) { e ->
                        LedgerRow(e, book, selecting, e.id in selection, actions, showDate = true, big = e.id in bigOnes,
                            onToggle = { onSelection(if (e.id in selection) selection - e.id else selection + e.id) },
                            modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            list(Modifier.weight(1.4f).fillMaxSize())
            Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchRow(view, onView, onFilter = null, onExport = { exporting = true })
                PeriodChips(view, month, onView, cycle)
                if (shown.isNotEmpty()) TotalsStrip(totals, view.filter.categories.isNotEmpty())
                // [TAB-06] The 판 chips on a wide screen too.
                LedgerBoardChips(board, pickBoard)
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("거르기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                            if (view.filter.count > 0) TextButton(onClick = { onView(view.copy(filter = LedgerFilter(query = view.filter.query))) }) { Text("모두 지우기") }
                        }
                        FilterControls(book, view.filter) { onView(view.copy(filter = it)) }
                    }
                }
                Spacer(Modifier.height(FabClearance))
            }
        }
    } else {
        list(Modifier.fillMaxSize())
    }

    if (filtering) {
        FilterSheet(book, view, month, cycle, onApply = { onView(it); filtering = false }, onDismiss = { filtering = false })
    }
    if (exporting) {
        ExportDialog(shown, book, view.range(month, cycle), actions::message) { exporting = false }
    }
    if (askingDelete && picked.isNotEmpty()) {
        val done = { askingDelete = false }
        if (picked.size == 1) DeleteLineDialog(picked.single(), onDismiss = done, onConfirm = { done(); actions.delete(picked); onSelection(emptySet()) })
        else com.bamnat.app.ui.components.ConfirmDialog(
            title = "기록 ${picked.size}건을 지울까요?",
            message = "${Fmt.shortDate(picked.minOf { it.date })}~${Fmt.shortDate(picked.maxOf { it.date })} · 모두 ${Fmt.won(picked.sumOf { kotlin.math.abs(it.amount) })}\n\n" +
                "지운 기록은 30일 동안 가계부 메뉴 › 최근 지운 기록에서 되살릴 수 있어요.",
            confirmLabel = "${picked.size}건 지우기",
            destructive = true,
            onDismiss = done,
            onConfirm = { done(); actions.delete(picked); onSelection(emptySet()) },
        )
    }
    if (moving && picked.isNotEmpty()) {
        MoveDialog(book, picked, onPick = { c -> actions.recategorize(picked, c); onSelection(emptySet()); moving = false }, onDismiss = { moving = false })
    }
}

/** The search box with the conditions, order and export beside it. */
@Composable
private fun SearchRow(view: LedgerView, onView: (LedgerView) -> Unit, onFilter: (() -> Unit)?, onExport: () -> Unit) {
    var sorting by remember { mutableStateOf(false) }
    // 2.0.0 [RVK-43] (검토 MONEY-31) What is typed shows at once; the view model's copy comes a frame later ([EchoText]).
    val echo = remember { EchoText(view.filter.query, max = 40) }
    LaunchedEffect(view.filter.query) { echo.outside(view.filter.query) }
    LaunchedEffect(echo.text) { kotlinx.coroutines.delay(1_000); echo.settle() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = echo.text,
            onValueChange = { onView(view.copy(filter = view.filter.copy(query = echo.typed(it)))) },
            singleLine = true,
            placeholder = { Text("기록 찾기", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = null,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, colors = com.bamnat.app.ui.theme.PhotoGround.fieldColors(),
            trailingIcon = {
                if (echo.text.isNotEmpty()) IconButton(onClick = { onView(view.copy(filter = view.filter.copy(query = echo.typed("")))) }) {
                    Icon(Icons.Filled.Close, "찾는 말 지우기")
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).semantics { contentDescription = "가게, 메모, #태그, 금액으로 기록 찾기" },
        )
        if (onFilter != null) {
            IconButton(onClick = onFilter) {
                BadgedBox(badge = { if (view.filter.count > 0) Badge { Text("${view.filter.count}") } }) {
                    Icon(Icons.Outlined.FilterList, if (view.filter.count > 0) "거르기, ${view.filter.count}개 켜짐" else "거르기")
                }
            }
        }
        Box {
            IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "정렬, ${view.sort.label}") }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                LedgerSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        leadingIcon = { if (s == view.sort) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { onView(view.copy(sort = s)); sorting = false },
                    )
                }
            }
        }
        IconButton(onClick = onExport) { Icon(Icons.Outlined.FileDownload, "CSV로 내보내기") }
    }
}

// 2.0.0 [RVK-35] (PC-126) On the folded phone '전체' was cut at the right edge of a row that slid: the five wrap now, all seen.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodChips(view: LedgerView, month: YearMonth, onView: (LedgerView) -> Unit, cycle: com.bamnat.core.money.PayCycle.Range? = null) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LedgerPeriod.entries.forEach { p ->
            val label = when (p) {
                LedgerPeriod.MONTH -> cycle?.label ?: Fmt.monthShort(month)
                LedgerPeriod.YEAR -> "${month.year}년"
                else -> p.label
            }
            FilterChip(selected = view.period == p, onClick = { onView(view.copy(period = p)) }, label = { Text(label) })
        }
    }
}

/** The conditions on, each a chip that takes itself off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveConditions(book: MoneyBook, view: LedgerView, onView: (LedgerView) -> Unit) {
    val f = view.filter
    if (f.count == 0) return
    fun set(next: LedgerFilter) = onView(view.copy(filter = next))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        f.kinds.forEach { k -> Removable(k.label) { set(f.copy(kinds = f.kinds - k)) } }
        f.categories.forEach { c -> Removable(c) { set(f.copy(categories = f.categories - c)) } }
        f.accounts.forEach { id -> Removable(if (id == LedgerFilter.NO_ACCOUNT) "결제수단 없음" else book.account(id)?.label ?: "결제수단") { set(f.copy(accounts = f.accounts - id)) } }
        f.tags.forEach { t -> Removable("#$t") { set(f.copy(tags = f.tags - t)) } }
        if (f.minAmount != null || f.maxAmount != null) {
            Removable(amountRange(f.minAmount, f.maxAmount)) { set(f.copy(minAmount = null, maxAmount = null)) }
        }
    }
}

private fun amountRange(min: Long?, max: Long?): String = when {
    min != null && max != null -> "${wonBrief(min)}~${wonBrief(max)}원"
    min != null -> "${wonBrief(min)}원 이상"
    else -> "${wonBrief(max ?: 0)}원 이하"
}

@Composable
private fun Removable(label: String, onRemove: () -> Unit) {
    InputChip(selected = true, onClick = onRemove, label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Filled.Close, "$label 조건 빼기", Modifier.size(18.dp)) })
}

/** [LEDG-07] What the lines shown add up to; with categories picked, only their parts. */
@Composable
private fun TotalsStrip(t: LedgerTotals, partsOnly: Boolean) {
    val cal = LocalCalendarColors.current
    val out = (if (t.expense > 0) "-" else "") + Fmt.won(t.expense)
    val ins = (if (t.income > 0) "+" else "") + Fmt.won(t.income)
    // [BLU-05] 1.36 부품 '가계부 머리'(money.head): the totals board.
    val head = com.bamnat.app.ui.parts.partStyle("money.head")
    val headShape = head.shape(MaterialTheme.shapes.medium)
    // Its small labels read on the board's own fill too (PC 2차: 부품 안의 모든 글자에 같은 대비).
    val labelInk = head.text(androidx.compose.ui.text.TextStyle()).color.takeIf { head.fill != null && it != Color.Unspecified }?.copy(alpha = 0.8f)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = headShape, color = head.fillColor ?: MaterialTheme.colorScheme.surfaceContainer,
        contentColor = head.ink?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.onSurface,
        shadowElevation = (head.shadow ?: 0).dp,
        modifier = Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(head.copy(fill = null, shadow = null, pad = null, padX = null, padY = null), headShape))) {
        if (LocalDensity.current.fontScale < 1.3f) {
            Row(Modifier.padding(horizontal = (head.padH ?: 14).dp, vertical = (head.padV ?: 10).dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${t.count}건", style = head.text(MaterialTheme.typography.labelLarge))
                Column(Modifier.weight(1f)) {
                    Text("나간 돈", style = MaterialTheme.typography.labelSmall, color = labelInk)
                    FitText(out, MaterialTheme.typography.titleSmall, cal.expense)
                }
                Column(Modifier.weight(1f)) {
                    // fc374a23 PC 확인 (내역과 요약의 '들어온 돈'이 달라요): this is the sum of the lines written here; 요약's 들어온 돈 also
                    // counts paid 일감, 가게 순수익 and the pay estimate, so this one is named for what it adds up.
                    Text("적은 수입", style = MaterialTheme.typography.labelSmall, color = labelInk)
                    FitText(ins, MaterialTheme.typography.titleSmall, cal.income)
                }
            }
        } else {
            // [LEDG-15] At very large text each total gets the whole width, so no amount is cut.
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("${t.count}건", style = MaterialTheme.typography.labelLarge)
                listOf(Triple("나간 돈", out, cal.expense), Triple("적은 수입", ins, cal.income)).forEach { (label, amount, color) ->
                    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = labelInk,
                            modifier = Modifier.padding(end = 8.dp))
                        FitText(amount, MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.End), color, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (partsOnly) {
        Text("나눠 적은 결제는 고른 분류의 몫만 더했어요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayHeader(date: LocalDate, expense: Long, income: Long) {
    val cal = LocalCalendarColors.current
    // [FREE-06] 가계부 모양's 날짜 줄: a line (the app's own), a band of colour, or a big date.
    val look = com.bamnat.app.ui.theme.LocalMoneyLook.current
    val band = look.dayHead == "band"
    // [BLU-05] 1.36 부품 '가계부 날짜 머리'(money.day), over the 가계부 모양.
    val day = com.bamnat.app.logic.partStyleOn("money.day", date)
    val dayShape = day.shape(androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
    Row(
        Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(day.copy(fill = null, paint = null, pad = null, padX = null, padY = null), dayShape))
            .background(day.fillColor ?: if (band) MaterialTheme.colorScheme.primaryContainer else com.bamnat.app.ui.theme.PhotoGround.solid(MaterialTheme.colorScheme.surface), dayShape)
            .then(day.paint?.let { Modifier.fillWith(it, dayShape) } ?: Modifier)
            .padding(horizontal = (day.padH ?: 16).dp, vertical = (day.padV ?: if (look.dayHead == "big") 10 else 8).dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fmt.dayTitle(date) + if (date.year != Today.date().year) " ${date.year}" else "",
            style = day.text((if (look.dayHead == "big") MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge)
                .copy(color = if (band) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)),
            modifier = Modifier.weight(1f))
        if (expense != 0L) Text("-" + Fmt.won(expense), style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = moneyInks().second)
        if (income != 0L) {
            Spacer(Modifier.width(8.dp))
            Text("+" + Fmt.won(income), style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = moneyInks().first)
        }
    }
}

/** The bar shown while lines are picked: how many, and what to do with them. */
@Composable
private fun SelectionBar(count: Int, all: Boolean, onClose: () -> Unit, onAll: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "고르기 끝내기") }
            Text("${count}건 골랐어요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            IconButton(onClick = onAll) { Icon(Icons.Outlined.SelectAll, if (all) "모두 풀기" else "모두 고르기") }
            IconButton(onClick = onMove, enabled = count > 0) { Icon(Icons.AutoMirrored.Outlined.Label, "분류 바꾸기") }
            IconButton(onClick = onDelete, enabled = count > 0) { Icon(Icons.Outlined.Delete, "고른 기록 지우기") }
        }
    }
}

/**
 * [LEDG-07] One line in the list. It slides right to be copied to today and left to be removed; a
 * long press starts picking. Each action is offered to screen readers by name as well.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LedgerRow(
    e: MoneyEntry,
    book: MoneyBook,
    selecting: Boolean,
    selected: Boolean,
    actions: LedgerListActions,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    /** 1.37 [LEDH-36] Much more than this category usually is. */
    big: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    // [SWIPE-01] A slide to the left never removes the line by itself: it springs back and asks first,
    // and it has to go half the row's width, so a slanted scroll cannot set it off.
    var asking by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                asking = true
                false
            } else true
        },
        positionalThreshold = { it * SWIPE_THRESHOLD },
    )
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> { haptics.performHapticFeedback(HapticFeedbackType.Confirm); actions.duplicate(e); state.reset() }
            SwipeToDismissBoxValue.EndToStart -> state.reset()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    if (asking) {
        DeleteLineDialog(e, onDismiss = { asking = false }, onConfirm = { asking = false; actions.delete(listOf(e)) })
    }
    val cal = LocalCalendarColors.current
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        gesturesEnabled = !selecting,
        backgroundContent = {
            val toRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            val tone = if (toRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            val bg by animateColorAsState(
                when {
                    state.dismissDirection == SwipeToDismissBoxValue.Settled -> Color.Transparent
                    armed -> tone
                    else -> tone.copy(alpha = 0.3f)
                }, label = "swipe")
            Row(Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End) {
                val fg = if (armed) (if (toRight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError) else MaterialTheme.colorScheme.onSurface
                if (toRight) {
                    Icon(Icons.Outlined.ContentCopy, null, tint = fg)
                    Spacer(Modifier.width(8.dp))
                    Text("오늘로 복사", color = fg, style = MaterialTheme.typography.labelLarge)
                } else if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Text("지우기", color = fg, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Delete, null, tint = fg)
                }
            }
        },
    ) {
        val account = MoneyAccounts.of(e, book.accounts)?.let(book::account)
        val shares = e.shares()
        val caption = listOfNotNull(
            if (showDate) Fmt.shortDate(e.date) else null,
            e.time?.let(Fmt::time),
            if (shares.size > 1) "${e.category} 외 ${shares.size - 1}" else e.category.takeIf { it != e.title },
            account?.let { a -> if (e.kind == MoneyKind.TRANSFER) "${a.name} → ${book.account(e.toAccountId)?.name ?: "?"}" else a.name },
            when (e.source) {
                MoneySource.NOTIFICATION -> "알림"; MoneySource.PASTE -> "문자"; MoneySource.MEMO -> "메모"; MoneySource.FIXED -> "반복"; MoneySource.MANUAL -> null
            },
            "평소보다 큼".takeIf { big },
        ).joinToString(" · ")
        val said = listOf(e.title, signedWon(e).replace("-", "마이너스 "), caption).joinToString(", ")
        Surface(
            color = if (selected) com.bamnat.app.ui.components.Selection.container else com.bamnat.app.ui.theme.PhotoGround.row(MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selecting) onToggle() else actions.open(e) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
                    onLongClickLabel = "고르기",
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = said
                    if (selecting) this.selected = selected
                    customActions = listOf(
                        CustomAccessibilityAction("오늘로 복사") { actions.duplicate(e); true },
                        CustomAccessibilityAction("지우기") { asking = true; true },
                        CustomAccessibilityAction(if (selected) "고르기 풀기" else "고르기") { onToggle(); true },
                    )
                },
        ) {
            // [FREE-06] 가계부 모양: how close the lines sit, how a record's kind is marked and how big its amount is.
            val look = com.bamnat.app.ui.theme.LocalMoneyLook.current
            val (minH, padV) = when (look.rows) { "compact" -> 48.dp to 4.dp; "roomy" -> 76.dp to 12.dp; else -> 64.dp to 8.dp }
            // [BLU-05] 1.36 부품 꾸미기: 가계부 한 줄과 금액 (수입 in·지출 out).
            val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
            // [FXR-03] With the record's day, so 조건부 꾸미기 (만약 지출 > 50000 …) works on the line as it does in the month's cells.
            // PC 4277abca: and the line's own 분류·종류·금액, so '분류 = "식비"' colours that line.
            val dayScope = com.bamnat.app.logic.scopeFor(e.date) { com.bamnat.core.layout.TabFacts.money(e) }
            // PC d0799995 (수입 금빛 줄): the line takes 들어온 돈·나간 돈 (on "in"/"out") as its 금액 does, so a 수입 line's own fill shows.
            val inOut = setOfNotNull(when (e.kind) { MoneyKind.INCOME -> "in"; MoneyKind.EXPENSE -> "out"; else -> null })
            val rowPart = bp.style("money.row", inOut, dayScope)
            val amountPart = bp.style("money.amount", inOut, dayScope)
            Row(Modifier.heightIn(min = rowPart.h?.dp ?: minH).then(com.bamnat.app.ui.parts.PartMods.box(rowPart.copy(pad = null, padX = null, padY = null), androidx.compose.ui.graphics.RectangleShape))
                .padding(horizontal = rowPart.padH?.dp ?: 16.dp, vertical = rowPart.padV?.dp ?: padV), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle() }, modifier = Modifier.size(40.dp))
                } else if (look.mark == "dot") {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(amountColor(e)))
                } else if (look.mark != "none") {
                    Box {
                        CategoryAvatar(e.category)
                        if (!e.reviewed) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // 2.0.0 [RVK-42] Big letters: a long name ('라마바전자 무선청소기') takes two lines before it is cut.
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = if (com.bamnat.app.ui.components.bigLetters()) 2 else 1,
                        overflow = TextOverflow.Ellipsis)
                    if (caption.isNotEmpty()) {
                        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // E 1.32 [LEDX-04] Which 회차 this month is, and what is still to pay. 2.0.0 [RVK-42] (조건 12) Its own line under the
                    // name: beside the amount it squeezed the name to '라마바전…' and the card to '신한 De…' on a folded phone.
                    if (e.installments > 1) {
                        Text(com.bamnat.core.money.LedgerSmarts.installmentText(e, com.bamnat.core.time.Today.date(), book.accounts) ?: "${e.installments}개월 할부",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    if (e.tags.isNotEmpty() || e.memo.isNotBlank()) {
                        Text((e.tags.map { "#$it" } + listOfNotNull(e.memo.takeIf { it.isNotBlank() })).joinToString(" "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(signedWon(e), style = when (look.amount) {
                        "s" -> MaterialTheme.typography.bodyMedium
                        "l" -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleSmall
                    }.merge(NumberStyle).let { com.bamnat.app.ui.parts.PartMods.text(amountPart.copy(ink = null), it) },
                        fontWeight = amountPart.weight?.let { FontWeight(it) } ?: FontWeight.Bold,
                        color = amountPart.ink?.let { androidx.compose.ui.graphics.Color(it.toInt()) } ?: amountColor(e), maxLines = 1)
                    when {
                        e.installments > 1 -> Unit
                        e.kind == MoneyKind.TRANSFER -> Text("이체", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        e.amount < 0 -> Text("취소", style = MaterialTheme.typography.labelSmall, color = cal.income)
                        // 1.37 [LEDH-07] 근무 시간 환산, for spending of 5만 원 and more.
                        e.kind == MoneyKind.EXPENSE -> LocalWorkTime.current?.words(e.amount)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** [LEDG-07] The conditions, as chips: kinds, categories, accounts, tags and an amount range. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterControls(book: MoneyBook, f: LedgerFilter, onChange: (LedgerFilter) -> Unit) {
    // Only categories that have lines (and any already picked), in the user's order: an empty one finds nothing.
    val categories = remember(book, f.kinds, f.categories) {
        val kinds = f.kinds.ifEmpty { MoneyKind.entries.toSet() }
        val used = book.entries.filter { it.kind in kinds }.flatMap { e -> e.shares().map { it.first } }.toSet() + f.categories
        val order = kinds.flatMap { book.categoriesOf(it) }.distinct()
        (order.filter { it in used } + used.filter { it !in order }.sorted())
            .distinct().let { list -> list.filter { it != MoneyCategories.OTHER } + list.filter { it == MoneyCategories.OTHER } }
    }
    val tags = remember(book) { Ledger.tags(book.entries).take(16) }
    FilterTitle("구분")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MoneyKind.entries.forEach { k ->
            FilterChip(selected = k in f.kinds, onClick = { onChange(f.copy(kinds = f.kinds.toggle(k))) }, label = { Text(k.label) })
        }
    }
    if (categories.isNotEmpty()) FilterTitle("분류")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        categories.forEach { c ->
            FilterChip(selected = c in f.categories, onClick = { onChange(f.copy(categories = f.categories.toggle(c))) }, label = { Text(c) },
                leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(18.dp)) })
        }
    }
    if (book.accounts.isNotEmpty()) {
        FilterTitle("결제수단")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            book.accounts.sortedWith(compareBy({ it.hidden }, { it.order })).forEach { a ->
                FilterChip(selected = a.id in f.accounts, onClick = { onChange(f.copy(accounts = f.accounts.toggle(a.id))) }, label = { Text(a.label) },
                    leadingIcon = { Icon(accountIcon(a.type), null, Modifier.size(18.dp)) })
            }
            FilterChip(selected = LedgerFilter.NO_ACCOUNT in f.accounts, onClick = { onChange(f.copy(accounts = f.accounts.toggle(LedgerFilter.NO_ACCOUNT))) },
                label = { Text("안 고른 기록") })
        }
    }
    FilterTitle("금액")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AmountField("최소", amountDigits(f.minAmount ?: 0), { onChange(f.copy(minAmount = it.toAmount().takeIf { v -> v > 0 })) }, Modifier.weight(1f))
        Text("~")
        AmountField("최대", amountDigits(f.maxAmount ?: 0), { onChange(f.copy(maxAmount = it.toAmount().takeIf { v -> v > 0 })) }, Modifier.weight(1f))
    }
    if (tags.isNotEmpty()) {
        FilterTitle("태그")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { t -> FilterChip(selected = t in f.tags, onClick = { onChange(f.copy(tags = f.tags.toggle(t))) }, label = { Text("#$t") }) }
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun FilterTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

/** [LEDG-07] The conditions in a sheet, with how many lines they would leave before they are applied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(book: MoneyBook, view: LedgerView, month: YearMonth, cycle: com.bamnat.core.money.PayCycle.Range?,
                        onApply: (LedgerView) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(view.filter) }
    val count = remember(book, f, view.period, month, cycle) { Ledger.search(book.entries, view.copy(filter = f).applied(month, cycle), view.sort, book.accounts).size }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, closeButton = false, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("거르기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(enabled = f.count > 0, onClick = { f = LedgerFilter(query = f.query) }) { Text("모두 지우기") }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterControls(book, f) { f = it }
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider()
            Button(onClick = { onApply(view.copy(filter = f)) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 48.dp)) {
                Text("${count}건 보기")
            }
        }
    }
}

/** [LEDG-07] Moves the picked lines to one category; lines of another kind are left as they are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(book: MoneyBook, picked: List<MoneyEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val kind = picked.groupingBy { it.kind }.eachCount().maxBy { it.value }.key
    val same = picked.count { it.kind == kind }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("분류 바꾸기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (same == picked.size) "${kind.label} ${same}건을 어느 분류로 옮길까요?"
                    else "${kind.label} ${same}건만 옮겨요. 다른 구분의 기록은 그대로예요.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    book.categoriesOf(kind).forEach { c ->
                        FilterChip(selected = false, onClick = { onPick(c) }, label = { Text(c) },
                            leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(18.dp), tint = categoryColor(c)) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [LEDG-12] What is shown, as a CSV file for a spreadsheet: shared through the phone's share sheet
 * or saved where the user picks. It is made on the phone and goes nowhere else.
 */
@Composable
private fun ExportDialog(shown: List<MoneyEntry>, book: MoneyBook, range: Pair<LocalDate?, LocalDate?>, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = remember(range) { LedgerCsv.fileName(range.first, range.second) }
    // Oldest first, as a spreadsheet reads.
    val csv = remember(shown, book) { LedgerCsv.write(shown.sortedWith(compareBy<MoneyEntry> { it.date }.thenBy { it.time }.thenBy { it.id }), book.accounts) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LedgerExport.MIME)) { uri ->
        if (uri == null) onDismiss() else scope.launch {
            onMessage(if (LedgerExport.save(context, uri, csv)) "파일로 저장했어요" else "파일을 저장하지 못했어요")
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FileDownload, null) },
        title = { Text("CSV로 내보내기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("지금 보이는 기록 ${shown.size}건을 엑셀·구글 시트에서 여는 파일로 만들어요.", style = MaterialTheme.typography.bodyMedium)
                Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("파일은 이 휴대폰에서 만들어지고, 보낼 곳은 직접 골라요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [SHOP-07] Daily sales and jobs as their own files.
                WorkCsvButtons(book, range, onMessage, onDismiss)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = shown.isNotEmpty(), onClick = { runCatching { save.launch(name) }.onFailure { onMessage("파일을 저장할 곳을 열지 못했어요"); onDismiss() } }) {
                    Text("파일로 저장")
                }
                FilledTonalButton(enabled = shown.isNotEmpty(), onClick = {
                    scope.launch {
                        runCatching { LedgerExport.share(context, csv, name) }.onFailure { onMessage("공유하지 못했어요") }
                        onDismiss()
                    }
                }) { Text("공유하기") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
