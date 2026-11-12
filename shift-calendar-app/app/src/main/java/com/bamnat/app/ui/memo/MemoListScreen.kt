package com.bamnat.app.ui.memo

import com.bamnat.app.ui.theme.washSurface
import com.bamnat.app.ui.parts.fillColor
import com.bamnat.app.ui.parts.shape
import com.bamnat.app.ui.parts.text
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.bamnat.app.appContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.BadgedBox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import com.bamnat.app.ui.components.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.IconCountBadge
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.memoBackground
import com.bamnat.app.ui.theme.MemoPaletteLight
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.memo.Memo
import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.bamnat.core.time.Today
import com.bamnat.app.ui.theme.color
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoListScreen(
    vm: MemoListViewModel,
    onOpen: (Long) -> Unit,
    onNew: (checklist: Boolean, date: LocalDate?) -> Unit,
    onOpenTrash: () -> Unit,
    style: com.bamnat.core.work.WorkStyle? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shiftDays by vm.shiftDays.collectAsStateWithLifecycle()
    // [STAB-01] Read at once, on the same frame as the key: a text box one frame behind the keyboard
    // doubles or drops half-built Korean letters and moves the cursor.
    val query by vm.query.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    BackHandler(enabled = state.selecting) { vm.clearSelection() }
    // [NAV-02] 1.25 Z (U4): back first clears a search, as settings' does, before leaving the tab.
    BackHandler(enabled = !state.selecting && query.isNotEmpty()) { vm.setQuery("") }
    var sharing by remember { mutableStateOf(false) }
    if (sharing) {
        MemoCalendarsDialog(
            initial = setOf(state.activeCalendar),
            calendars = state.calendars,
            title = "고른 메모 ${state.selection.size}개를 어느 달력에 보일까요?",
            onDismiss = { sharing = false },
            onSave = { vm.shareSelected(it); sharing = false },
        )
    }
    // [DAILY-15] 화면 꾸미기: which of the rows above the memos show, and in what order.
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val appSettings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val parts = memoParts(appSettings.memoLayout)
    var arranging by rememberSaveable { mutableStateOf(false) }
    // [DAILY-05] Filter chips fold into the search box's icon unless a filter is on or the user keeps them open.
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    // 2.0.0 [RVI-77] A '야간 날' chip counts too: the filter icon shows it and the chips stay open while it is on.
    val filterOn = state.tag != null || state.shift != null ||
        (if (state.tab == MemoTab.CALENDAR) state.calendarFilter != CalendarFilter.ALL else state.generalFilter != GeneralFilter.ALL)
    val fold = parts.any { it.part == MemoPart.FOLD && it.shown }
    val today = Today.date()
    var undo by remember { mutableStateOf<UndoBar?>(null) }
    val open: (Memo) -> Unit = { memo -> if (state.selecting) vm.toggleSelected(memo.id) else onOpen(memo.id) }
    val actions = CardActions(
        onOpen = open,
        onSelect = { vm.toggleSelected(it.id) },
        onTogglePin = vm::togglePin,
        onDuplicate = vm::duplicate,
        onDelete = vm::delete,
        // [TAB-02] 누락 점검 N9: a card moved on the 보드 or a 칸 picked in the 표 — saved at once, with 되돌리기 for 10 seconds (PC 확인 5ff9ae28: 4–6초 was missed).
        onChange = { before, after, said -> vm.saveEdited(after); undo = UndoBar(said, { vm.saveEdited(before) }, "바꾸기 전으로 돌아왔어요") },
        onDone = { memo, day, done ->
            vm.setDone(memo, day, done)
            undo = UndoBar(if (done) "끝냈어요" else "끝냄을 풀었어요", { vm.setDone(memo, day, !done) }, "바꾸기 전으로 돌아왔어요")
        },
        // 2.0.0 [RVI-74] (검토 U07a-01) A checklist line ticked on the card, with 되돌리기 as 끝냄 has.
        onCheckItem = { memo, index, checked ->
            vm.setItemChecked(memo, index, checked)
            undo = UndoBar(if (checked) "체크했어요" else "체크를 풀었어요", { vm.setItemChecked(memo, index, !checked) }, "바꾸기 전으로 돌아왔어요")
        },
    )
    androidx.compose.runtime.LaunchedEffect(undo) { undo?.let { u -> kotlinx.coroutines.delay(if (u.back == null) 3_000 else 10_000); undo = null } }
    // 2.0.0 [RVI-80] (검토 U07a-09) '오래된 할 일', from the '오늘' 묶음 or the 할 일 위젯's line.
    val askOld by MemoLinks.old.collectAsStateWithLifecycle()
    if (askOld && state.loaded) {
        val old = state.oldTasks
        if (old.isEmpty()) androidx.compose.runtime.LaunchedEffect(Unit) { MemoLinks.old.value = false }
        else OldTasksSheet(
            old = old,
            onOpen = { m -> MemoLinks.old.value = false; open(m) },
            onDone = { m -> actions.onDone?.invoke(m, m.date, true) },
            onAll = { how ->
                MemoLinks.old.value = false
                vm.settleOld(old, how)
                undo = UndoBar("${old.size}개를 ${how.said}", { vm.restoreOld(old) }, "바꾸기 전으로 돌아왔어요")
            },
            onClose = { MemoLinks.old.value = false },
        )
    }

    // 2.0.0 [RVP-06] The new-memo button shrinks to its icon while the list scrolls down.
    val fab = com.bamnat.app.ui.components.rememberFabOpen()
    Box(Modifier.fillMaxSize().nestedScroll(fab.connection)) {
        Column(Modifier.fillMaxSize()) {
            if (state.selecting) {
                SelectionBar(
                    count = state.selection.size,
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAllVisible,
                    onPin = vm::pinSelected,
                    onColor = vm::colorSelected,
                    onDelete = vm::deleteSelected,
                    // [NOTE-02] Several memos onto other calendars at once.
                    onShare = if (state.manyCalendars) ({ sharing = true }) else null,
                )
            } else {
                Header(state, onSort = vm::setSort, onOpenTrash = onOpenTrash, onArrange = { com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.STUDIO_MEMO) })
            }
            for (p in parts) {
                if (!p.shown) continue
                when (p.part) {
                    // 2.0.0 [RVI-77] Not while searching, filtering or picking memos: then the list is what is looked for.
                    MemoPart.TODAY -> if (state.loaded && !state.selecting && query.isEmpty() && !filterOn) MemoTodayBlock(
                        rows = state.todayTasks,
                        onOpen = open,
                        onDone = { memo, day, done -> actions.onDone?.invoke(memo, day, done) },
                        onWrite = { text, written ->
                            vm.addQuick(text) { m ->
                                written(m != null)
                                undo = if (m != null) UndoBar(quickWritten(m), { vm.unwrite(m) }, "적은 것을 지웠어요")
                                    else UndoBar("적지 못했어요. 다시 적어 주세요", null)
                            }
                        },
                        shifts = shiftDays,
                        old = state.oldTasks,
                        onOld = { MemoLinks.old.value = true },
                    )
                    MemoPart.NOTEBOOKS -> NotebookBar(state, vm::setNotebook)
                    MemoPart.SEARCH -> OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        // 2.0.0 [RVI-64] (디자인 품질 1·12) In the list pane at 200% the hint stood in four lines and pushed the
                        // memos to the bottom half: it keeps one line (TalkBack still reads all of it).
                        placeholder = { Text("제목, 내용, #태그 검색", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, colors = com.bamnat.app.ui.theme.PhotoGround.fieldColors(),
                        trailingIcon = {
                            Row {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "검색어 지우기") }
                                }
                                if (fold) IconButton(onClick = { filtersOpen = !filtersOpen }) {
                                    Icon(Icons.Filled.FilterList, if (filtersOpen) "필터 접기" else "필터 펼치기",
                                        tint = if (filterOn) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    MemoPart.FOLD, MemoPart.WEEK -> Unit
                }
            }
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = com.bamnat.app.ui.theme.PhotoGround.solid(Color.Transparent)) {
                for (tab in MemoTab.entries) {
                    val count = if (tab == MemoTab.CALENDAR) state.calendarCount else state.generalCount
                    Tab(
                        selected = state.tab == tab,
                        onClick = { vm.setTab(tab) },
                        text = { Text("${tab.label} $count", maxLines = 1) },
                    )
                }
            }
            // With the search box hidden there is no icon to unfold them, so they stay open.
            if (!fold || filtersOpen || filterOn || parts.none { it.part == MemoPart.SEARCH && it.shown }) FilterRow(state, vm)
            // [TAB-02] 1.36 메모장 보기: the memos on screen (after the tab, notebook, search and filters) as a list, board, table or gallery.
            val tabViews = remember(appSettings.tabViews) { com.bamnat.core.layout.TabViews.decode(appSettings.tabViews) }
            val memoView = tabViews.memo
            // 1.39 [FLOW-19] 주 띠: a week of days over the 달력 메모 list (not over its own 달력 보기), a dot for a day with memos;
            // a day picked shows only its memos, the same day again or '모두 보기' shows them all.
            androidx.compose.runtime.LaunchedEffect(appSettings.weekStart) { vm.setWeekStart(appSettings.weekStart) }
            if (state.tab == MemoTab.CALENDAR && state.week.isNotEmpty() && memoView.view != com.bamnat.core.memo.MemoView.CALENDAR &&
                parts.any { it.part == MemoPart.WEEK && it.shown }) MemoWeekStrip(state, today, vm)
            val shown = remember(state.tab, state.upcoming, state.past, state.general) {
                if (state.tab == MemoTab.CALENDAR) (state.upcoming + state.past).flatMap { it.memos }.distinctBy { it.id } else state.general
            }
            val shiftOf = remember(state.upcoming, state.past) {
                val m = (state.upcoming + state.past).associate { it.date to it.day?.shiftType?.name };
                { d: LocalDate -> m[d] }
            }
            val propColumns = remember(shown) { com.bamnat.core.memo.MemoProps.columns(shown) }
            // [TAB-01] A 틀's 고르기 choices for a 칸, so its 보드 shows every 칸 and its 표 offers every word.
            val templates by container.memoTemplates.items.collectAsStateWithLifecycle()
            val templateChoices = remember(templates) {
                { label: String -> templates.flatMap { t -> t.asks.filter { it.label == label && it.type == "pick" }.flatMap { it.choices } } }
            }
            val setView: (com.bamnat.core.memo.MemoView) -> Unit = { v ->
                // PC 확인 1be49fd7: memos picked in one view are let go when the view changes, so nothing stays picked unseen.
                if (v.view != memoView.view) vm.clearSelection()
                container.settingsRepository.update { it.copy(tabViews = com.bamnat.core.layout.TabViews.decode(it.tabViews).copy(memo = v).encode()) }
            }
            // PC 확인 bad7d860 사용성 제안: the chips show from the first visit, so the views are found before there are memos.
            // A 정렬 picked from the chip or a 표 head says what it did, with 되돌리기 (PC 확인 82196a74, fe5eb3cd).
            fun said(v: String, base: String) = when {
                v.isEmpty() -> base
                v == com.bamnat.core.memo.MemoView.SORT_TITLE -> "제목순"
                else -> "‘${v.removePrefix("-").removePrefix("p:")}’ ${if (v.startsWith("-")) "큰 순" else "작은 순"}"
            }
            val baseLabel = if (state.tab == MemoTab.CALENDAR) "날짜순" else state.sort.label
            val sortTo: (String) -> Unit = { key ->
                val before = memoView
                val beforeBase = baseLabel
                val beforeSort = state.sort
                val ms = key.removePrefix("ms:").takeIf { key.startsWith("ms:") }?.let { n -> MemoSort.entries.firstOrNull { it.name == n } }
                if (ms != null) vm.setSort(ms)
                val next = if (ms != null) "" else key
                setView(memoView.copy(sort = next))
                val now = ms?.label ?: said(next, baseLabel)
                undo = UndoBar("${now}${com.bamnat.app.ui.Fmt.ro(now)} 정렬했어요", { vm.setSort(beforeSort); setView(before) },
                    said(before.sort, beforeBase).let { "$it${com.bamnat.app.ui.Fmt.ro(it)} 돌아왔어요" })
            }
            // PC 확인 fe5eb3cd: a 칸 order whose 칸 is gone from the memos on screen goes back to the 기본 순서, and says so.
            androidx.compose.runtime.LaunchedEffect(memoView.sort, propColumns, state.loaded) {
                val label = memoView.sort.removePrefix("-").removePrefix("p:")
                if (state.loaded && shown.isNotEmpty() && memoView.sort.removePrefix("-").startsWith("p:") && propColumns.none { it.first == label }) {
                    val before = memoView
                    setView(memoView.copy(sort = ""))
                    undo = UndoBar("‘$label’ 칸이 없어져 기본 순서로 바꿨어요", { setView(before) }, "‘$label’ 순으로 돌아왔어요")
                }
            }
            if (state.loaded) MemoViewChips(memoView, propColumns, setView,
                baseOptions = if (state.tab == MemoTab.CALENDAR) listOf("" to "날짜순", com.bamnat.core.memo.MemoView.SORT_TITLE to "제목순")
                    else MemoSort.entries.map { "ms:${it.name}" to it.label },
                baseLabel = baseLabel, onSort = sortTo)
            when {
                state.loaded && state.shownCount == 0 -> EmptyMemos(state.tab, searching = state.filtering, style = style)
                memoView.view == com.bamnat.core.memo.MemoView.BOARD -> MemoBoardView(shown, memoView, shiftOf, state, actions, templateChoices)
                memoView.view == com.bamnat.core.memo.MemoView.TABLE -> MemoTableView(shown, memoView, onSort = { sort -> sortTo(sort)}, actions, templateChoices, state.selection, state.selecting)
                memoView.view == com.bamnat.core.memo.MemoView.GALLERY -> MemoGalleryView(shown, memoView, actions, state.selection, state.shiftOn)
                memoView.view == com.bamnat.core.memo.MemoView.CALENDAR -> MemoCalendarView(shown, appSettings.weekStart, today, actions, state.shiftOn, state.shiftsOf)
                !memoView.sort.isEmpty() && state.tab != MemoTab.CALENDAR -> GeneralMemoGrid(state.copy(general = memoView.sorted(state.general)), actions)
                state.tab == MemoTab.CALENDAR -> CalendarMemoList(state, today, actions)
                else -> GeneralMemoGrid(state, actions)
            }
        }

        // [TAB-02] 누락 점검 N9: what a 보드·표 change did, and 되돌리기, above the 새 메모 button.
        undo?.let { (said, back, backSaid) ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 88.dp).fillMaxWidth()
                    .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(said, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    // PC 확인 d0e5457e 사용성 제안: after 되돌리기 the bar says where it came back to, for a moment.
                    if (back != null) androidx.compose.material3.TextButton(onClick = { back(); undo = UndoBar(backSaid, null) }) {
                        Text("되돌리기", color = MaterialTheme.colorScheme.inversePrimary)
                    } else Spacer(Modifier.height(48.dp))
                }
            }
        }
        if (!state.selecting) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(com.bamnat.app.ui.theme.LocalSpacing.current.card),
            ) {
                // On the calendar tab a new memo starts on today so it lands in that list.
                val date = if (state.tab == MemoTab.CALENDAR) today else null
                // [DAILY-05] One button, then 메모 or 체크리스트 [UX-06]: said in words, a pencil alone was read as "edit".
                // 2.0.0 [RVI-61] (검토 U07a-04) A tap writes a memo at once (it took a second tap on 메모); a long press offers 체크리스트,
                // as the editor's ⋮ › 체크리스트로 바꾸기 does.
                var choosing by remember { mutableStateOf(false) }
                Box {
                    // 2.0.0 [RVP-06] The shared floating-button rule: words at rest, the icon alone while the list goes down.
                    // [RVI-61] (검토 U07a-04) One tap starts a memo; held, it asks memo or checklist (it asked on every tap).
                    com.bamnat.app.ui.components.AddFab(
                        "새 메모",
                        onClick = { onNew(false, date) },
                        open = fab.open,
                        icon = Icons.Outlined.Edit,
                        description = if (date != null) "오늘 날짜로 새 메모" else "새 메모",
                        modifier = Modifier
                            .pointerInput(Unit) {
                                // Seen before the button's own tap: held long enough, the menu opens and the tap does not happen.
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    }
                                    if (up == null) {
                                        choosing = true
                                        do {
                                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            e.changes.forEach { it.consume() }
                                        } while (e.changes.any { it.pressed })
                                    }
                                }
                            }
                            .semantics { onLongClick(label = "체크리스트로 새로 만들기") { choosing = true; true } },
                    )
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        DropdownMenuItem(text = { Text("메모") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { choosing = false; onNew(false, date) })
                        DropdownMenuItem(text = { Text("체크리스트") }, leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                            onClick = { choosing = false; onNew(true, date) })
                    }
                }
            }
        }
    }
    if (arranging) MemoEditSheet(appSettings.memoLayout, container.settingsRepository::update, onDismiss = { arranging = false })
}

/**
 * [DAILY-15] The rows above the memos the user can order or hide (화면 꾸미기). [FOLD] is a switch, not a row:
 * on, the filter chips fold into the search box's icon ([DAILY-05], on unless turned off).
 */
internal enum class MemoPart(val id: String, val label: String, val what: String, val movable: Boolean = true) {
    /** 2.0.0 [RVI-77] (검토 U07a-02·I07ab-01) 늦음·오늘·고정 and the line to write the next one, first on the tab. */
    TODAY("today", "오늘 할 일", "늦은 것·오늘·고정을 맨 위에, 동그라미로 끝내고 한 줄로 적기"),
    NOTEBOOKS("notebooks", "공책", "공책을 나눠 쓸 때 위에 고르는 줄"),
    SEARCH("search", "검색 칸", "제목·내용·#태그 찾기"),
    FOLD("fold", "필터는 검색 칸 아이콘으로 접기", "끄면 필터·태그 칩이 늘 펼쳐져 있어요", movable = false),
    /** 1.39 [FLOW-19] 주 띠 over the 달력 메모 list. */
    WEEK("week", "주 띠", "달력 메모 위에 한 주 7일, 날을 누르면 그날 메모만", movable = false),
}

internal fun memoParts(layout: String) = com.bamnat.core.layout.PageLayout.decode(layout).arrange(MemoPart.entries.toList()) { it.id }

@Composable
internal fun MemoEditSheet(layout: String, update: ((com.bamnat.app.data.AppSettings) -> com.bamnat.app.data.AppSettings) -> Unit, onDismiss: () -> Unit) {
    val placed = memoParts(layout)
    fun write(next: List<com.bamnat.core.layout.PageLayout.Placed<MemoPart>>) =
        update { it.copy(memoLayout = com.bamnat.core.layout.PageLayout.of(next) { p -> p.id }.encode()) }
    val rows = { movable: Boolean ->
        placed.filter { it.part.movable == movable }.map { com.bamnat.app.ui.components.EditRow(it.part.id, it.part.label, it.part.what, it.shown, movable, it.isNew) }
    }
    com.bamnat.app.ui.components.PageEditSheet(
        title = "메모 카드 배치",
        groups = listOf(
            com.bamnat.app.ui.components.EditGroup("위쪽 줄 순서", rows(true)),
            com.bamnat.app.ui.components.EditGroup("위쪽 필터", rows(false)),
        ),
        middle = "달력 메모 · 일반 메모",
        onShow = { id, shown -> write(placed.map { if (it.part.id == id) it.copy(shown = shown) else it }) },
        onMove = { id, by ->
            val fixed = placed.filterNot { it.part.movable }
            val movable = placed.filter { it.part.movable }
            write(com.bamnat.core.layout.PageLayout.move(movable, movable.indexOfFirst { it.part.id == id }, by) + fixed)
        },
        onReset = { update { it.copy(memoLayout = "") } },
        onAsk = {
            onDismiss()
            com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "카드 배치"))
        },
        onDismiss = onDismiss,
    )
}

internal class CardActions(
    val onOpen: (Memo) -> Unit,
    val onSelect: (Memo) -> Unit,
    val onTogglePin: (Memo) -> Unit,
    val onDuplicate: (Memo) -> Unit,
    val onDelete: (Memo) -> Unit,
    /**
     * [TAB-02] 누락 점검 N9: a memo changed in a view (moved to another 보드 칸, a 표 칸 picked) — saved, with [said] and 되돌리기 to [before].
     */
    val onChange: (before: Memo, after: Memo, said: String) -> Unit = { _, _, _ -> },
    /** 1.39 [TASK-02] 끝냄 표시 / 풀기 on [day] (a repeating memo's day; the memo's own for one that does not repeat). */
    val onDone: ((memo: Memo, day: java.time.LocalDate?, done: Boolean) -> Unit)? = null,
    /** 2.0.0 [RVI-74] (검토 U07a-01·I07aa-01) Line [index] of a checklist ticked on the card itself; null: the lines are only shown. */
    val onCheckItem: ((memo: Memo, index: Int, checked: Boolean) -> Unit)? = null,
)

@Composable
private fun Header(state: MemoListState, onSort: (MemoSort) -> Unit, onOpenTrash: () -> Unit, onArrange: () -> Unit = {}) {
    var sortMenu by remember { mutableStateOf(false) }
    // [BLU-05] 1.36 부품 '메모장 머리'(memo.head).
    val head = com.bamnat.app.ui.parts.partStyle("memo.head")
    Row(
        Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(head.copy(pad = null, padX = null, padY = null), RoundedCornerShape(0.dp))).padding(start = 20.dp, top = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.bamnat.app.ui.parts.PartText("메모", head, MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        // PC 확인 fe5eb3cd: the 정렬 lives in the 보기 chips' 정렬 alone; this icon shows only before the chips do.
        if (state.tab == MemoTab.GENERAL && !state.loaded) {
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "정렬: ${state.sort.label}")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (s in MemoSort.entries) {
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            trailingIcon = { if (s == state.sort) Icon(Icons.Outlined.Check, null) },
                            onClick = {
                                sortMenu = false
                                onSort(s)
                            },
                        )
                    }
                }
            }
        }
        // [DAILY-15] 화면 꾸미기, as on 달력 and 근무·급여.
        IconButton(onClick = onArrange) { Icon(Icons.Outlined.Dashboard, contentDescription = "카드 배치") }
        IconButton(onClick = onOpenTrash) {
            BadgedBox(badge = { if (state.trashCount > 0) IconCountBadge(state.trashCount, "메모 ${state.trashCount}개") }) {
                Icon(Icons.Outlined.Delete, contentDescription = "휴지통")
            }
        }
        com.bamnat.app.ui.MeButton() // [NAV2-02]
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
    onColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    var palette by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "선택 해제") }
            Text("${count}개 선택", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "모두 선택") }
            IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, "고정 / 해제") }
            Box {
                IconButton(onClick = { palette = true }) { Icon(Icons.Outlined.Palette, "색 바꾸기") }
                DropdownMenu(expanded = palette, onDismissRequest = { palette = false }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in MemoPaletteLight.indices) {
                            Box(
                                Modifier.minimumInteractiveComponentSize()
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(memoBackground(i))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable(role = Role.Button) {
                                        palette = false
                                        onColor(i)
                                    },
                            )
                        }
                    }
                }
            }
            onShare?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Groups, "보일 달력 정하기") } }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "휴지통으로 옮기기") }
        }
    }
}

/** 1.39 [FLOW-19] 주 띠 (DSG-06): ‹ week › with a dot under each day that has memos; the picked day's line under it with 모두 보기. */
@Composable
private fun MemoWeekStrip(state: MemoListState, today: LocalDate, vm: MemoListViewModel) = Column(Modifier.padding(horizontal = 4.dp)) {
    val back = @Composable { IconButton(onClick = { vm.moveWeek(-1, today) }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난주") } }
    val next = @Composable { IconButton(onClick = { vm.moveWeek(1, today) }) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 주") } }
    val strip = @Composable { m: Modifier ->
        com.bamnat.app.ui.components.WeekStrip(
            days = state.week, selected = state.day ?: LocalDate.MIN, today = today,
            onPick = vm::pickDay, modifier = m, counts = state.weekCounts, countWord = "메모",
        )
    }
    // 2.0.0 [RVI-64] (디자인 품질 4·12) In the 40% list pane of an unfolded phone at 200% letters seven days and two arrows left a
    // day about 20dp: the arrows go over the strip with the week's dates between them, and the days get the whole width.
    val numberStyle = MaterialTheme.typography.titleSmall
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val need = remember(numberStyle, density) { with(density) { measurer.measure("30", numberStyle, maxLines = 1).size.width.toDp() } + 10.dp }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if ((maxWidth - 96.dp) / 7 >= need) {
            Row(verticalAlignment = Alignment.CenterVertically) { back(); strip(Modifier.weight(1f)); next() }
        } else Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                back()
                val first = state.week.firstOrNull()
                val last = state.week.lastOrNull()
                Text(when {
                    first == null || last == null -> ""
                    first.month == last.month -> "${first.monthValue}/${first.dayOfMonth}~${last.dayOfMonth}"
                    else -> "${first.monthValue}/${first.dayOfMonth}~${last.monthValue}/${last.dayOfMonth}"
                }, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                next()
            }
            strip(Modifier.fillMaxWidth())
        }
    }
    state.day?.let { d ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val n = state.weekCounts[d] ?: 0
            // PC 4차: '메모 0개 · 이날은 메모가 없어요' said it twice.
            Text("${d.monthValue}월 ${d.dayOfMonth}일 " + if (n == 0) "· 이날은 메모가 없어요" else "메모 ${n}개",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = vm::clearDay) { Text("모두 보기") }
        }
    }
}

@Composable
private fun FilterRow(state: MemoListState, vm: MemoListViewModel) = Column {
    // [BLU-05] 1.36 부품 '꼬리표 줄'(memo.tags): the row's board and the gap between its chips.
    val tags = com.bamnat.app.ui.parts.partStyle("memo.tags")
    Row(
        Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(tags.copy(pad = null, padX = null, padY = null), RoundedCornerShape(0.dp)))
            .horizontalScroll(rememberScrollState()).padding(horizontal = (tags.padH ?: 16).dp, vertical = (tags.padV ?: 6).dp),
        horizontalArrangement = Arrangement.spacedBy((tags.gap ?: 8).dp),
    ) {
        if (state.tab == MemoTab.CALENDAR) {
            for (f in CalendarFilter.entries) {
                FilterChip(selected = state.calendarFilter == f, onClick = { vm.setCalendarFilter(f) }, label = { Text(f.label) })
            }
        } else {
            for (f in GeneralFilter.entries) {
                FilterChip(selected = state.generalFilter == f, onClick = { vm.setGeneralFilter(f) }, label = { Text(f.label) })
            }
        }
        for (tag in state.tags.take(MAX_TAG_CHIPS)) {
            FilterChip(selected = state.tag == tag, onClick = { vm.toggleTag(tag) }, label = { Text("#$tag") })
        }
    }
    // [MEMO-03] 1.27 AK: the memos of one kind of shift's days ('야간 날'), on a row of their own so they are seen without scrolling.
    if (state.tab == MemoTab.CALENDAR && state.shifts.isNotEmpty()) Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("근무별", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (name in state.shifts.take(MAX_SHIFT_CHIPS)) {
            FilterChip(selected = state.shift == name, onClick = { vm.toggleShift(name) }, label = { Text("$name 날") })
        }
    }
    // [RUL-03] 1.36 단추 on the memo screen, for today.
    com.bamnat.app.logic.LogicButtons(com.bamnat.core.formula.ButtonPlace.MEMO, com.bamnat.core.time.Today.date())
}

@Composable
private fun CalendarMemoList(state: MemoListState, today: LocalDate, actions: CardActions) {
    val look = com.bamnat.app.ui.theme.LocalMemoLook.current
    // [FREE-04] 메모장 모양: in columns, the dates and sections stay a whole line each.
    if (look.columns > 1) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(look.columns),
            modifier = Modifier.fillMaxSize().memoPaper(look),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            fun section(label: String, groups: List<MemoDateGroup>) {
                if (groups.isEmpty()) return
                item(key = "label-$label", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) { SectionLabel(label) }
                for (group in groups) {
                    item(key = "date-${group.date}", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) { DateHeader(group.date, group.day, today) }
                    items(group.memos, key = { it.id }) { memo ->
                        MemoCard(memo, state.query, selected = memo.id in state.selection, selecting = state.selecting, showDate = false, actions = actions)
                    }
                }
            }
            section("오늘과 다가오는 날", state.upcoming)
            section("지난 날", state.past)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().memoPaper(look),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(if (look.layout == "compact") 4.dp else 8.dp),
    ) {
        fun section(label: String, groups: List<MemoDateGroup>) {
            if (groups.isEmpty()) return
            item(key = "label-$label") { SectionLabel(label) }
            for (group in groups) {
                item(key = "date-${group.date}") { DateHeader(group.date, group.day, today) }
                items(group.memos, key = { it.id }) { memo ->
                    MemoCard(memo, state.query, selected = memo.id in state.selection, selecting = state.selecting,
                        showDate = false, actions = actions)
                }
            }
        }
        section("오늘과 다가오는 날", state.upcoming)
        section("지난 날", state.past)
    }
}

@Composable
private fun GeneralMemoGrid(state: MemoListState, actions: CardActions) {
    val look = com.bamnat.app.ui.theme.LocalMemoLook.current
    LazyVerticalStaggeredGrid(
        // [FREE-04] The app's own: as many 160dp columns as fit; else the look's. 2.0.0 [RVI-48] (디자인 품질 12) 160dp grows with the
        // text size: at 200% on a folded phone two columns left five letters a line, so it is one column there, two unfolded.
        columns = if (look.layout == null) StaggeredGridCells.Adaptive(minSize = 160.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 2f))
            else StaggeredGridCells.Fixed(look.columns),
        modifier = Modifier.fillMaxSize().memoPaper(look),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = if (look.layout == "compact") 4.dp else 10.dp,
    ) {
        items(state.general, key = { it.id }) { memo ->
            MemoCard(memo, state.query, selected = memo.id in state.selection, selecting = state.selecting,
                showDate = true, actions = actions)
        }
    }
}

/** [FREE-04] The memo screen's own paper colour, and a diary's ruled lines under the memos. */
internal fun Modifier.memoPaper(look: com.bamnat.core.recipe.MemoLook): Modifier = this
    .then(look.bg?.let { Modifier.background(Color((it or 0xFF000000L).toInt())) } ?: Modifier)

/** [FREE-04] 메모장 모양's 글꼴; [MAT-05] 1.36 any font of the 글꼴 모음 or the user's own. */
@Composable
internal fun memoFont(font: String?): androidx.compose.ui.text.font.FontFamily? = com.bamnat.app.ui.theme.rememberFontFamily(font)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DateHeader(date: LocalDate, day: ResolvedDay?, today: LocalDate) {
    // [MEMO-07] 1.27 AK: with big letters the shift goes under the date instead of being cut to "주간…".
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 2.0.0 [RVI-64] (디자인 품질 12) In the narrow list pane at 200% 'D-5' was cut to 'D-' beside the date: it is its own
        // piece of its own small row, so it goes under the date when they do not fit side by side.
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Fmt.dayTitle(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 6.dp))
            val days = ChronoUnit.DAYS.between(today, date)
            val badge = when {
                days == 0L -> "오늘"
                days == 1L -> "내일"
                days in 2..99 -> "D-$days"
                else -> null
            }
            if (badge != null) Box(Modifier.align(Alignment.CenterVertically)) {
                Pill(badge, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
        }
        ShiftLabel(day, Modifier.align(Alignment.CenterVertically))
    }
}

/** The day's shift as a colored badge plus its name and hours, e.g. "주간 08:00–20:00". */
@Composable
fun ShiftLabel(day: ResolvedDay?, modifier: Modifier = Modifier) {
    val type = day?.shiftType
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (type == null) {
            Text("근무 없음", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Row
        }
        ShiftBadge(type, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        val start = day.start
        val end = day.end
        val text = if (day.isWorkDay && start != null && end != null) {
            "${type.name} ${Fmt.time(start)}–${Fmt.time(end)}"
        } else {
            type.name
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (day.isWorkDay && day.scheduledMinutes > 0) {
            Text(" · ${HoursFormat.short(day.scheduledMinutes)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoCard(
    memo: Memo,
    query: String,
    selected: Boolean,
    selecting: Boolean,
    showDate: Boolean,
    actions: CardActions,
) {
    var menu by remember { mutableStateOf(false) }
    var toLedger by remember { mutableStateOf(false) }
    if (toLedger) MemoToLedgerDialog(memo) { toLedger = false }
    // 1.39 [TASK-02] A repeating memo in the list is finished day by day: today's.
    val doneDay = if (memo.repeat.isNotEmpty()) Today.date() else memo.date
    val done = memo.isDoneOn(doneDay)
    // [FREE-04] 메모장 모양: the card's colour, edge, corners and paper; a 포스트잇 leans a little, one way or the other by the memo.
    val look = com.bamnat.app.ui.theme.LocalMemoLook.current
    // [BLU-05] 1.36 부품 꾸미기: 메모 카드와 카드 속 제목, over the 메모장 모양.
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    // [FXR-03] With the memo's day, so 조건부 꾸미기 (만약 야간이면 …) works on the card as it does in the month's cells.
    // PC 4277abca: and the memo's own 고정·체크율·꼬리표·색번호, so '고정' rings a pinned card.
    val memoScope = com.bamnat.app.logic.scopeFor(memo.date) { com.bamnat.core.layout.TabFacts.memo(memo) }
    val cardPart = bp.style("memo.card", scope = memoScope)
    val titlePart = bp.style("memo.card.title", scope = memoScope)
    val shape = com.bamnat.app.ui.parts.PartMods.shapeOf(cardPart, look.corner?.let { RoundedCornerShape(it.dp) } ?: MaterialTheme.shapes.large)
    val own = memoBackground(memo.colorIndex)
    val cardColor = cardPart.fill?.let { Color(it.toInt()).copy(alpha = (cardPart.fillA ?: 100) / 100f) } ?: when (look.card) {
        "one" -> look.cardColor?.let { Color((it or 0xFF000000L).toInt()) } ?: own
        "none" -> Color.Transparent
        else -> if (look.layout == "sticky" && memo.colorIndex == 0) memoBackground(1 + (memo.id % 7).toInt().let { if (it < 0) -it else it }) else own
    }
    val edge = when {
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        cardPart.line == "none" -> null
        cardPart.line != null || cardPart.lineC != null || cardPart.lineW != null ->
            BorderStroke((cardPart.lineW ?: 1.0).toFloat().dp, cardPart.lineC?.let { Color(it.toInt()).copy(alpha = (cardPart.lineA ?: 100) / 100f) } ?: MaterialTheme.colorScheme.outlineVariant)
        look.border == "none" || look.card == "none" -> null
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (look.border == "thin") 1f else 0.5f))
    }
    val ink = if (cardPart.ink != null) Color(cardPart.ink!!.toInt()) else if (cardColor.alpha > 0.5f && cardColor != Color.Unspecified && look.card == "one") com.bamnat.app.ui.components.contentColorOn(cardColor) else androidx.compose.ui.graphics.Color.Unspecified
    // PC d0799995 (체크 진행 카드): the part's 투명도 (alpha), so '체크율 >= 100이면 흐리게' lets a finished card step back.
    Box(Modifier.then(if (look.layout == "sticky") Modifier.graphicsLayer { rotationZ = if (memo.id % 2 == 0L) -1.2f else 1.2f } else Modifier)
        .then(cardPart.alpha?.let { a -> Modifier.graphicsLayer { alpha = a.coerceIn(0, 100) / 100f } }
            ?: if (done) Modifier.graphicsLayer { alpha = DONE_ALPHA } else Modifier)) {
        Surface(
            shape = shape,
            color = cardColor,
            contentColor = if (ink != Color.Unspecified) ink else androidx.compose.material3.contentColorFor(cardColor).takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
            shadowElevation = cardPart.shadow?.dp ?: if (look.layout == "sticky") 3.dp else 0.dp,
            border = edge,
            modifier = Modifier
                .fillMaxWidth()
                .then(com.bamnat.app.ui.parts.partSpotModifier("memo.card"))
                .clip(shape)
                .then(if (look.layout == "diary") Modifier.drawBehind {
                    val gap = 22.dp.toPx()
                    var y = gap
                    while (y < size.height) { drawLine(Color(0x22000000), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f); y += gap }
                } else Modifier)
                .combinedClickable(onClick = { actions.onOpen(memo) }, onLongClick = { actions.onSelect(memo) }),
        ) {
          androidx.compose.runtime.CompositionLocalProvider(LocalMemoCardLook provides look) {
            // 2.0.0 [RVI-74] (검토 U07a-01·I07aa-01) 끝내기 was ⋮ › 끝냄 표시 (2번): a to-do's card has a circle on its left that
            // finishes it in one tap, with 되돌리기 (48dp to the finger, its mark where the card's words began).
            val circle = actions.onDone?.takeIf { !selecting && com.bamnat.core.memo.TaskList.finishable(memo) }
            Column(Modifier.padding(start = if (circle != null) 2.dp else 14.dp, top = if (look.layout == "compact") 6.dp else 10.dp, bottom = if (look.layout == "compact") 6.dp else 12.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (selected) "선택됨" else "선택 안 됨",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (circle != null) {
                        IconButton(
                            onClick = { circle(memo, doneDay, !done) },
                            modifier = Modifier.semantics { stateDescription = if (done) "끝냄" else "안 끝냄" },
                        ) {
                            Icon(
                                if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = if (done) "끝냄 풀기" else "끝냄 표시",
                                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    Text(
                        highlighted(memo.title.ifBlank { memo.preview.take(com.bamnat.core.memo.MemoSearch.CARD_CHARS) }, query),
                        style = when (look.title) {
                            "s" -> MaterialTheme.typography.labelLarge
                            "l" -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }.let { st -> memoFont(look.font)?.let { st.copy(fontFamily = it) } ?: st }
                            .let { st -> when (look.bold) { true -> st.copy(fontWeight = FontWeight.Bold); false -> st.copy(fontWeight = FontWeight.Normal); else -> st } }
                            .let { st -> com.bamnat.app.ui.parts.PartMods.text(titlePart, st) }
                            .let { st -> if (done) st.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else st },
                        maxLines = if (look.layout == "compact") 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).then(com.bamnat.app.ui.parts.partSpotModifier("memo.card.title")),
                    )
                    if (done && circle == null) {
                        Icon(Icons.Filled.CheckCircle, "끝냄", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (memo.pinned) {
                        Icon(Icons.Filled.PushPin, "고정됨", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (!selecting) {
                        // 2.0.0 [RVI-48] (검토 MEMO-29) The card's ⋮ is 48dp to the finger (it was 32dp).
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, "메모 메뉴", Modifier.size(20.dp))
                        }
                    } else {
                        Spacer(Modifier.width(10.dp))
                    }
                }
                // The lines under the title stay where they were (14dp in), so a checklist's boxes line up under the circle.
                Column(Modifier.padding(start = if (circle != null) 12.dp else 0.dp, end = 10.dp)) {
                    if (look.layout != "compact") MemoContent(memo, query, actions.onCheckItem?.takeIf { !selecting })
                    // [TAB-01] 1.36 메모 속성: the memo's "칸: 값" lines as pills.
                    if (look.layout != "compact") PropPills(memo, modifier = Modifier.padding(top = 6.dp))
                    Footer(memo, showDate && look.date != false)
                }
            }
          }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            actions.onDone?.let { onDone ->
                DropdownMenuItem(
                    text = { Text(if (done) "끝냄 풀기" else "끝냄 표시") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                    onClick = { menu = false; onDone(memo, doneDay, !done) },
                )
            }
            DropdownMenuItem(
                text = { Text(if (memo.pinned) "고정 해제" else "맨 위에 고정") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = { menu = false; actions.onTogglePin(memo) },
            )
            // 2.0.0 [RVI-52] (검토 MEMO-28) The same words as the editor's menu.
            DropdownMenuItem(
                text = { Text("하나 더 만들기") },
                leadingIcon = { Icon(Icons.Outlined.AddToPhotos, null) },
                onClick = { menu = false; actions.onDuplicate(memo) },
            )
            // [TAB-03] PC 확인 bad7d860: '가계부에 넣기' from a card's menu too.
            DropdownMenuItem(
                text = { Text("가계부에 넣기") },
                leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null) },
                onClick = { menu = false; toLedger = true },
            )
            DropdownMenuItem(
                text = { Text("여러 개 선택") },
                leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                onClick = { menu = false; actions.onSelect(memo) },
            )
            DropdownMenuItem(
                text = { Text("휴지통으로") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { menu = false; actions.onDelete(memo) },
            )
        }
    }
}

/** [FREE-04] The memo look inside a card (lines, font). */
private val LocalMemoCardLook = androidx.compose.runtime.staticCompositionLocalOf { com.bamnat.core.recipe.MemoLook() }

@Composable
private fun MemoContent(memo: Memo, query: String, onCheck: ((memo: Memo, index: Int, checked: Boolean) -> Unit)? = null) {
    // [MPH-04] 1.38 A memo's photos as small pictures on its card.
    val photos = remember(memo.body) { memo.photos }
    if (photos.isNotEmpty()) MemoPhotoStrip(photos)
    // The title row already shows the preview line when there is no title; with photos the lines after it show under them.
    if (memo.isChecklist) {
        // [J135-07] A template's answers kept above the list.
        if (memo.text.isNotBlank()) Text(highlighted(memo.text, query), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        // 2.0.0 [RVI-74] (검토 U07a-01) By their place in the whole list, so a tick on the card lands on the right line.
        val items = memo.checklist.withIndex().filter { it.value.text.isNotBlank() }
        val cardLook = LocalMemoCardLook.current
        val shown = cardLook.lines ?: 6
        for ((index, item) in items.take(shown)) {
            // 2.0.0 [RVI-74] (검토 U07a-01·I07aa-01) The card's lines tick without opening the memo: the whole line is the box,
            // 44dp tall (디자인 품질 9: lines stacked in a card, the width is the whole card).
            val tick = onCheck?.let { check ->
                Modifier.heightIn(min = 44.dp).toggleable(value = item.checked, role = androidx.compose.ui.semantics.Role.Checkbox) { check(memo, index, it) }
            } ?: Modifier.padding(top = 4.dp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(tick)) {
                Icon(
                    if (item.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(if (onCheck != null) 20.dp else 16.dp),
                    tint = if (onCheck != null && !item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    highlighted(item.text, query),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (items.size > shown) {
            Text("외 ${items.size - shown}개", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    } else if (memo.text.isNotBlank() && (memo.title.isNotBlank() || photos.isNotEmpty())) {
        // PC 확인 d5678f31: a "칸: 값" line shown as a pill under the card (PropPills, the first 3) is not written again in the text.
        val body = remember(memo.body, memo.title) {
            val pills = com.bamnat.core.memo.MemoProps.of(memo).take(3).map { it.label }.toSet()
            // PC 확인 c9a89b20: a line of 꼬리표 alone is shown by the tag chips, not again in the text.
            val lines = memo.text.lines().filterNot { com.bamnat.core.memo.MemoProps.labelOfLine(it) in pills || TAG_LINE.matches(it.trim()) }
            // [MPH-04] (PC 확인 3dd61e00) A memo without a title but with photos: its first line is the title row, the words after it
            // go under the photos, so the card shows what was written and not only pictures.
            val rest = if (memo.title.isBlank()) lines.dropWhile { it.isBlank() }.drop(1) else lines
            rest.joinToString("\n").trim()
        }
        if (body.isEmpty()) return
        // [MEMO-04] 1.27 AK: a long memo is drawn as its start, or the lines around the word searched for, never whole.
        val cardLook = LocalMemoCardLook.current
        // [BLU-05] 1.36 부품 '메모 내용'(memo.card.body) over the 메모장 모양.
        val bodyPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.card.body", scope = com.bamnat.app.logic.scopeFor(memo.date) { com.bamnat.core.layout.TabFacts.memo(memo) })
        if (bodyPart.hide != true) Text(
            highlighted(com.bamnat.core.memo.MemoSearch.excerpt(body, query), query),
            style = bodyPart.text(memoFont(cardLook.font)?.let { MaterialTheme.typography.bodyMedium.copy(fontFamily = it) } ?: MaterialTheme.typography.bodyMedium),
            maxLines = bodyPart.lines ?: cardLook.lines ?: 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).then(com.bamnat.app.ui.parts.partSpotModifier("memo.card.body")),
        )
    }
}

@Composable
private fun Footer(memo: Memo, showDate: Boolean) {
    val date = memo.date?.takeIf { showDate }
    val tags = memo.tags
    val progress = memo.isChecklist && memo.itemCount > 0
    val reminder = memo.reminder
    if (date == null && tags.isEmpty() && !progress && reminder == null) return
    // 2.0.0 [RVIB-04] (디자인 품질 13) A schedule told by the work in the kind's words (가게 '영업 시작 1시간 전마다').
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val style = remember(reminder?.work) { if (reminder?.work != null) container.settingsRepository.current.workStyle else null }
    Row(
        Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val pillColor = MaterialTheme.colorScheme.washSurface.copy(alpha = 0.6f)
        // [BLU-05] 1.36 부품 '메모 날짜'(memo.card.date).
        val datePart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.card.date", scope = com.bamnat.app.logic.scopeFor(memo.date) { com.bamnat.core.layout.TabFacts.memo(memo) })
        if (date != null && datePart.hide != true) {
            val dateShape = datePart.shape(MaterialTheme.shapes.small)
            Row(
                Modifier.then(com.bamnat.app.ui.parts.partSpotModifier("memo.card.date")).clip(dateShape).background(datePart.fillColor ?: pillColor, dateShape).then(com.bamnat.app.ui.parts.PartMods.box(datePart.copy(fill = null, pad = null, padX = null, padY = null), dateShape))
                    .padding(horizontal = (datePart.padH ?: 6).dp, vertical = (datePart.padV ?: 2).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Event, null, Modifier.size(12.dp), tint = datePart.ink?.let { Color(it.toInt()) } ?: androidx.compose.material3.LocalContentColor.current)
                Spacer(Modifier.width(3.dp))
                Text(Fmt.dayTitle(date), style = datePart.text(MaterialTheme.typography.labelSmall))
            }
        }
        // [SCHED-01] A memo that is a schedule says when it rings or notifies.
        if (reminder != null) {
            // 2.0.0 [RVI-63] (검토 U07a-08) Late, as the 할 일 위젯 counts it (gone past its time and not finished): the card says '늦음' in
            // the caution colour, not only the widget.
            val late = remember(memo, Today.date(), Today.dateTime().hour) {
                com.bamnat.core.memo.TaskList.of(listOf(memo), Today.date()).firstOrNull()?.let { com.bamnat.core.memo.TaskList.overdue(it, Today.dateTime()) } == true
            }
            val tone = if (late) com.bamnat.app.ui.theme.Tone.Caution.color() else androidx.compose.material3.LocalContentColor.current
            Row(
                Modifier.clip(MaterialTheme.shapes.small).background(pillColor).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (reminder.ring) Icons.Filled.Alarm else Icons.Filled.EventAvailable, null, Modifier.size(12.dp), tint = tone)
                Spacer(Modifier.width(3.dp))
                // 2.0.0 [RVI-49] (검토 MEMO-11) "매일 오전 9:00", not the first day as if it were past.
                Text((if (late) "늦음 · " else "") + scheduleLine(memo, reminder, style = style), style = MaterialTheme.typography.labelSmall, color = tone)
            }
        }
        for (tag in tags.take(3)) {
            Pill("#$tag", pillColor, MaterialTheme.colorScheme.primary)
        }
        if (progress) {
            Text(
                "${memo.checkedCount}/${memo.itemCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyMemos(tab: MemoTab, searching: Boolean, style: com.bamnat.core.work.WorkStyle? = null) {
    // 2.0.0 [RVI-70] (PC 통합 1차 PC-65, 디자인 품질 7·16) The empty memo tab was an icon over two paragraphs: it is the shared empty
    // screen (칸이, one line, the 새 메모 button below starts one). (검토 T07a-07, 디자인 품질 13) The general tab's example was
    // '인수인계, 준비물, 교대 약속' for every kind of work: each says its own.
    com.bamnat.app.ui.components.EmptyState(
        title = when {
            searching -> "찾는 메모가 없어요"
            tab == MemoTab.CALENDAR -> "아직 달력 메모가 없어요"
            else -> "아직 일반 메모가 없어요"
        },
        detail = when {
            searching -> "검색어나 필터를 바꿔 보세요"
            // PC 확인 5c21f3bb: 투자 has no 근무 — the day's trades show with it; 자영업 its 영업일, 현장 its 일감.
            tab == MemoTab.CALENDAR -> "날짜에 남긴 메모는 그날 " + when (style) {
                com.bamnat.core.work.WorkStyle.INVEST -> "매매 기록과"
                com.bamnat.core.work.WorkStyle.BUSINESS -> "영업 여부와"
                com.bamnat.core.work.WorkStyle.FIELD -> "일감과"
                else -> "근무와"
            } + " 함께 보여요"
            else -> generalExample(style) + "처럼 날짜와 상관없는 메모를 적어 두세요"
        },
        modifier = Modifier.padding(top = 32.dp),
    )
}

/** 2.0.0 [RVI-70] (검토 T07a-07) What a general memo is for, in each kind of work's own words. */
internal fun generalExample(style: com.bamnat.core.work.WorkStyle?): String = when (style) {
    com.bamnat.core.work.WorkStyle.DAY -> "회의 준비, 할 일, 준비물"
    com.bamnat.core.work.WorkStyle.HOURLY -> "근무 바꾸기 약속, 준비물, 매장 안내"
    com.bamnat.core.work.WorkStyle.FIELD -> "자재 목록, 거래처 연락처, 견적 메모"
    com.bamnat.core.work.WorkStyle.BUSINESS -> "재료 주문, 단골 메모, 가게 할 일"
    com.bamnat.core.work.WorkStyle.INVEST -> "매매 원칙, 관심 종목, 공부 메모"
    else -> "인수인계, 준비물, 교대 약속"
}

/** Highlights every case-insensitive match of each word in [query]. */
@Composable
internal fun highlighted(text: String, query: String): AnnotatedString {
    val words = query.trim().split(Regex("\\s+")).map { it.removePrefix("#") }.filter { it.isNotEmpty() }
    if (words.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, fontWeight = FontWeight.Bold)
    val marks = BooleanArray(text.length)
    for (w in words) {
        var i = text.indexOf(w, ignoreCase = true)
        while (i >= 0) {
            for (k in i until i + w.length) marks[k] = true
            i = text.indexOf(w, i + w.length, ignoreCase = true)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val on = marks[i]
            var j = i
            while (j < text.length && marks[j] == on) j++
            if (on) withStyle(style) { append(text.substring(i, j)) } else append(text.substring(i, j))
            i = j
        }
    }
}

private const val MAX_TAG_CHIPS = 12
private const val MAX_SHIFT_CHIPS = 6

/**
 * [FREE-06] 화면 꾸미기's example memo screen: four memos drawn by the real card in [look]'s layout, in rows of its columns; a tap
 * anywhere is [onTap] (the look's editor).
 */
@Composable
fun MemoLookSample(look: com.bamnat.core.recipe.MemoLook,
    /** [CNV-12] My own memos to draw in the look (the example ones when null). */
    mine: List<Memo>? = null,
    /** PC 확인 e50bb622 (자영업 장터 그림에 '인수인계'·'월급날'): the two work memos in this kind of work's own words. */
    style: com.bamnat.core.work.WorkStyle? = null, onTap: () -> Unit) {
    val (work, workBody, money, moneyBody) = when (style) {
        com.bamnat.core.work.WorkStyle.DAY -> listOf("회의 메모", "금요일 보고서 마감\n자료는 공유 폴더에", "월급날", "적금 넣기")
        com.bamnat.core.work.WorkStyle.HOURLY -> listOf("대타 부탁", "토요일 오후 대신 근무\n17시~22시", "알바비 들어오는 날", "적금 넣기")
        com.bamnat.core.work.WorkStyle.FIELD -> listOf("자재 챙기기", "실리콘 2통, 장갑\n현장 사진 찍기", "일당 받는 날", "통장 확인")
        com.bamnat.core.work.WorkStyle.BUSINESS -> listOf("재료 주문", "양파 2망, 식용유\n목요일 오전 배송", "카드 정산일", "매출 통장 확인")
        com.bamnat.core.work.WorkStyle.INVEST -> listOf("매매 일지", "분할 매수 2회차\n손절선 지키기", "배당 들어오는 날", "재투자 정하기")
        else -> listOf("인수인계", "3층 장비 점검 끝\n다음 근무자에게 열쇠 전달", "월급날", "적금 넣기")
    }
    val memos = mine ?: listOf(
        Memo(id = 1, title = "장보기", isChecklist = true, colorIndex = 1,
            checklist = listOf(com.bamnat.core.memo.ChecklistItem("우유"), com.bamnat.core.memo.ChecklistItem("달걀", checked = true), com.bamnat.core.memo.ChecklistItem("두부"))),
        Memo(id = 2, title = work, body = workBody, colorIndex = 0),
        Memo(id = 3, title = "여행 계획", body = "11월 셋째 주 쉬는 날에 바다 보러 가기", colorIndex = 4, pinned = true),
        Memo(id = 4, title = money, body = moneyBody, colorIndex = 6),
    )
    val actions = CardActions({ onTap() }, { onTap() }, {}, {}, {})
    androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.theme.LocalMemoLook provides look) {
        Column(Modifier.fillMaxWidth().memoPaper(look).padding(8.dp), verticalArrangement = Arrangement.spacedBy(if (look.layout == "compact") 4.dp else 8.dp)) {
            val cols = if (look.layout == null) 2 else look.columns
            memos.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m -> Box(Modifier.weight(1f)) { MemoCard(m, "", selected = false, selecting = false, showDate = true, actions = actions) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** [TAB-02] What a 보드·표 change did, how to undo it, and what the bar says once undone (null [back]: a short note alone). */
private data class UndoBar(val said: String, val back: (() -> Unit)?, val backSaid: String = "되돌렸어요")

/** A body line that is only 꼬리표 ("#집안일 #급함"). */
private val TAG_LINE = Regex("^(#[\\p{L}\\p{N}_]+\\s*)+$")

/** 1.39 [TASK-02] How far a finished memo's card steps back. */
private const val DONE_ALPHA = 0.6f
