package com.bamnat.app.ui.memo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import android.widget.Toast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.parts.fillColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Star
import com.bamnat.app.ui.components.LocalEdgeScroll
import com.bamnat.app.ui.components.edgeScrollViewport
import com.bamnat.app.ui.components.rememberSelectionDrag
import androidx.compose.runtime.CompositionLocalProvider
import com.bamnat.app.ui.components.memoBackground
import com.bamnat.app.ui.theme.MemoPaletteLight
import com.bamnat.core.memo.Memo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MemoEditorScreen(vm: MemoEditorViewModel, onBack: () -> Unit) {
    // [STAB-01] Read at once, on the same frame as the key: a text box one frame behind the keyboard
    // doubles or drops half-built Korean letters and moves the cursor.
    val memo by vm.memo.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val savedAt by vm.savedAt.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val offer by vm.scheduleOffer.collectAsStateWithLifecycle()
    val moneyOffer by vm.moneyOffer.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    // [MEMO-05][MEMO-06] 1.27 AK: the calendar's shifts ahead, and the last memo of the same shift.
    val shiftDays by vm.shiftDays.collectAsStateWithLifecycle()
    val lastSame by vm.lastSameShift.collectAsStateWithLifecycle()
    var showLast by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 2.0.0 [RVI-32] (검토 MEMO-12) Trashed from the list beside it on a fold: close, so a letter typed does not bring it back.
    val trashedOutside by vm.trashedOutside.collectAsStateWithLifecycle()
    LaunchedEffect(trashedOutside) { if (trashedOutside) onBack() }
    // 2.0.0 [RVI-30] What a change took with it (날짜를 떼서 반복도 꺼졌어요, 알림도 옮겼어요), said in one line under the chips,
    // next to what changed, instead of a toast (갈래 P 13:40 rule 5; the 알림 줄 of 뿌리 4 takes this over when it lands).
    var said by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(said) { if (said != null) { kotlinx.coroutines.delay(6_000); said = null } }
    LaunchedEffect(vm) { vm.sayings.collect { said = it } }
    // [SCHED-01] The schedule dialog, and the found time it answers when it was opened from the offer.
    var scheduling by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<com.bamnat.core.memo.MemoTime.Found?>(null) }

    var showColors by remember { mutableStateOf(false) }
    // [RCP-11] 1.34 메모 틀.
    val templates by vm.templates.collectAsStateWithLifecycle()
    var pickTemplate by remember { mutableStateOf(false) }
    // [CVW-10] 1.35 A template with 입력 칸 asks them first.
    var asking by remember { mutableStateOf<com.bamnat.core.memo.MemoTemplate?>(null) }
    if (pickTemplate) MemoTemplateDialog(templates, onPick = { t -> if (t.asks.isEmpty()) vm.startFrom(t) else asking = t; pickTemplate = false },
        onRemove = vm::removeTemplate, onDismiss = { pickTemplate = false })
    asking?.let { t -> TemplateAskDialog(t, onDone = { answers -> vm.startFrom(t, answers); asking = null }, onDismiss = { asking = null }) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    var toLedger by remember { mutableStateOf(false) }
    if (toLedger) MemoToLedgerDialog(memo) { toLedger = false }
    if (savingTemplate) SaveTemplateDialog(memo, onSave = { name, asked, picks ->
        val why = vm.saveAsTemplate(name, asked, picks)
        Toast.makeText(context, why ?: "‘${name.ifBlank { "새 틀" }}’ 틀로 저장했어요. 새 메모를 열면 ‘틀에서 시작’에 있어요", Toast.LENGTH_LONG).show()
        if (why == null) savingTemplate = false
    }, onDismiss = { savingTemplate = false })
    var copying by remember { mutableStateOf(false) }
    if (copying) com.bamnat.app.ui.components.CopyTextSheet(memo.title.ifBlank { "메모" }, shareText(memo)) { copying = false }
    val bodyTools = remember { MemoBodyTools() }
    // [MPH-02] 1.38 A chosen photo's or board's tools, fixed above the keyboard bar.
    val toolHost = remember { MemoToolHost() }
    // [MPH-01] 1.38 사진 넣기: at the caret of the field typed in last, or at the end of the words.
    // [MPH-07] A 자유 판's '+ 사진' sends the photos to that board instead (its block index).
    var boardTarget by remember { mutableStateOf<Int?>(null) }
    val (photoPicker, photoMenu) = rememberMemoPhotoPicker(
        left = { com.bamnat.core.memo.MemoPhotos.MAX_PER_MEMO - com.bamnat.core.memo.MemoPhotos.count(vm.memo.value.body) },
        onBoard = if (memo.isChecklist) null else { ->
            boardTarget = null
            val into = bodyTools.insertBoard
            if (into != null) into() else vm.setBody(com.bamnat.core.memo.MemoPhotos.insertBoard(vm.memo.value.body, null).text)
        },
        onOpen = { boardTarget = null },
    ) { names ->
        boardTarget?.let { at ->
            val now = vm.memo.value.body
            val b = com.bamnat.core.memo.MemoPhotos.parse(now).getOrNull(at) as? com.bamnat.core.memo.MemoPhotos.Board
            if (b != null) {
                val added = names.mapIndexed { k, name ->
                    val n = b.items.size + k
                    com.bamnat.core.memo.MemoPhotos.BoardItem(com.bamnat.core.memo.MemoPhotos.BoardKind.PHOTO, name,
                        x = 300 + (n % 4) * 120, y = 300 + (n % 3) * 150, w = 450)
                }
                vm.setBody(com.bamnat.core.memo.MemoPhotos.updateBoard(now, at, b.copy(items = b.items + added)))
            }
            return@rememberMemoPhotoPicker
        }
        val into = bodyTools.insertPhoto
        if (into != null && !memo.isChecklist) into(names)
        else if (!vm.addPhotos(names)) Toast.makeText(context, "메모 하나에 사진은 ${com.bamnat.core.memo.MemoPhotos.MAX_PER_MEMO}장까지 넣을 수 있어요", Toast.LENGTH_LONG).show()
    }
    bodyTools.onPhoto = photoPicker.open
    // [MPH-06] 1.38 스튜디오 부품 '메모 쓰기 종이'(memo.page): a theme's paper colour for a memo left in the app's colour.
    val pagePart = com.bamnat.app.ui.parts.partStyle("memo.page")
    val background = (if (memo.colorIndex == 0) pagePart.fillColor else null)
        ?: memoBackground(memo.colorIndex)
    val bodyStyle = com.bamnat.app.ui.parts.PartMods.text(pagePart, MaterialTheme.typography.bodyLarge)
    val paperLook = memoPaperLook(com.bamnat.core.memo.MemoPhotos.paper(memo.body))
    var choosingPaper by remember { mutableStateOf(false) }
    if (choosingPaper) MemoPaperDialog(
        current = com.bamnat.core.memo.MemoPhotos.paper(memo.body),
        themed = com.bamnat.app.ui.parts.partStyle("memo.page.rule").line.let { it != null && it != "none" },
        onPick = { k -> vm.setBody(com.bamnat.core.memo.MemoPhotos.withPaper(memo.body, k)); choosingPaper = false },
        onDismiss = { choosingPaper = false },
    )
    // [UX-11] 1.25 Z (U10): the line being typed is kept in view by moving this scroll only, never the window.
    val bodyScroll = rememberScrollState()
    val follow = remember { CaretFollow() }
    // [MEMO-SEL-01] 1.30 AY: a selection handle dragged to the top or bottom scrolls this too, and the selection grows.
    val edge = com.bamnat.app.ui.components.rememberEdgeScroll(bodyScroll)
    val editorScope = rememberCoroutineScope()
    val caretRoom = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
    val onCaret: (androidx.compose.ui.layout.LayoutCoordinates, androidx.compose.ui.geometry.Rect, Boolean) -> Unit = { field, caret, animate ->
        val content = follow.content
        if (content != null && content.isAttached && field.isAttached) {
            val top = content.localPositionOf(field, androidx.compose.ui.geometry.Offset(0f, caret.top)).y
            val bottom = content.localPositionOf(field, androidx.compose.ui.geometry.Offset(0f, caret.bottom)).y
            com.bamnat.core.memo.CaretScroll.target(bodyScroll.value, follow.viewport, top, bottom, caretRoom)?.let { to ->
                editorScope.launch { if (animate) bodyScroll.animateScrollTo(to) else bodyScroll.scrollTo(to) }
            }
        }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                title = {
                    // 2.0.0 [RVI-48] (디자인 품질 12) At 200% text on a folded phone it stood letter by letter: one line, and where the
                    // time does not fit beside the five buttons (the unfolded editor pane) only '저장됨', not '저장됨 · …'.
                    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp to androidx.compose.ui.platform.LocalDensity.current.fontScale
                    var short by remember(narrow) { mutableStateOf(false) }
                    Text(
                        savedAt?.let { if (short) "저장됨" else "저장됨 · " + savedTime(it) } ?: if (memo.id == 0L) "새 메모" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (it.hasVisualOverflow && !short && savedAt != null) short = true },
                    )
                },
                actions = {
                    // Nothing can be changed until an existing memo has finished loading.
                    if (!loaded) return@TopAppBar
                    IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "실행 취소") }
                    IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
                    // 1.39 [TASK-02] 끝냄, in sight like 고정 (a repeating memo: the day it was opened from).
                    val done = memo.isDoneOn(vm.doneDay)
                    // 2.0.0 [RVI-62] (검토 U07a-12) What the circle does was hard to tell: held, it says '끝냄 표시' (pressed, the line
                    // '끝냈어요 · 끝냄 풀기' under the chips already says what happened and the way back).
                    androidx.compose.material3.TooltipBox(
                        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(if (done) "끝냄 풀기" else "끝냄 표시") } },
                        state = androidx.compose.material3.rememberTooltipState(),
                    ) {
                        IconButton(onClick = { vm.setDone(!done) }) {
                            Icon(
                                if (done) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                if (done) "끝냄 풀기" else "끝냄 표시",
                                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = vm::togglePinned) {
                        Icon(
                            if (memo.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            if (memo.pinned) "고정 해제" else "고정",
                            tint = if (memo.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "더 보기") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (memo.isChecklist) "일반 메모로 바꾸기" else "체크리스트로 바꾸기") },
                                leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                onClick = { menu = false; vm.toggleChecklist() },
                            )
                            DropdownMenuItem(
                                text = { Text("이 메모를 틀로 저장") },
                                leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                // [TAB-03] PC 확인 bad7d860 "눌러도 반응이 없어요": a dialog asks the name and which 칸 to ask each time.
                                onClick = { menu = false; savingTemplate = true },
                                enabled = !memo.isBlank,
                            )
                            DropdownMenuItem(
                                text = { Text("가계부에 넣기") },
                                leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null) },
                                onClick = { menu = false; toLedger = true },
                                enabled = !memo.isBlank,
                            )
                            // [MPH-05] 1.38 메모 종이.
                            DropdownMenuItem(
                                text = { Text("종이(줄 노트·모눈·점)") },
                                leadingIcon = { Icon(Icons.Outlined.Description, null) },
                                onClick = { menu = false; choosingPaper = true },
                            )
                            DropdownMenuItem(
                                text = { Text("색상") },
                                leadingIcon = { Icon(Icons.Filled.Palette, null) },
                                onClick = { menu = false; showColors = !showColors },
                            )
                            DropdownMenuItem(
                                text = { Text("공유") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = {
                                    menu = false
                                    // [MPH-04] 1.38 A memo with photos shares them too, as pictures beside the words.
                                    val send = MemoPhotoFiles.shareIntent(context, memo.photos)
                                        .putExtra(Intent.EXTRA_SUBJECT, memo.title)
                                        .putExtra(Intent.EXTRA_TEXT, shareText(memo))
                                    runCatching { context.startActivity(Intent.createChooser(send, "메모 공유")) }
                                },
                                enabled = !memo.isBlank,
                            )
                            // A 1.32 [FIX132-05] 복사: the whole memo, or part of it in a box that scrolls with the selection.
                            DropdownMenuItem(
                                text = { Text("복사하기") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { menu = false; copying = true },
                                enabled = !memo.isBlank,
                            )
                            // 2.0.0 [RVI-52] (검토 MEMO-28) '복제' beside '복사하기' with the same picture: '하나 더 만들기', its own picture.
                            DropdownMenuItem(
                                text = { Text("하나 더 만들기") },
                                leadingIcon = { Icon(Icons.Outlined.AddToPhotos, null) },
                                onClick = {
                                    menu = false
                                    vm.duplicate()
                                    Toast.makeText(context, "같은 메모를 하나 더 만들었어요", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !memo.isBlank,
                            )
                            if (memo.isChecklist && memo.checkedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("완료 항목 지우기") },
                                    leadingIcon = { Icon(Icons.Filled.Check, null) },
                                    onClick = { menu = false; vm.clearChecked() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("지우기", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (showColors) {
                ColorRow(selected = memo.colorIndex, onSelect = vm::setColor)
            }
            // 2.0.0 [RVI-55] (검토 U07a-05) The chips ran off the edge (7 chips took a swipe to find): one line of as many as fit and
            // '+N' for the rest, every one in sight once it is opened, so the writing still starts in the top quarter (디자인 품질 1)
            // at 200% text on a folded phone. The day's shift rides in the date chip instead of taking a place of its own.
            var moreChips by rememberSaveable { mutableStateOf(false) }
            ChipLine(open = moreChips, onToggle = { moreChips = !moreChips }, modifier = Modifier.padding(horizontal = 16.dp)) {
                val date = memo.date
                // [RCP-11] 1.34: a memo not written yet can start from a 틀. [TAB-03] PC 확인 bf62e966: first in the row while the memo
                // is empty (at the end it took a swipe to find).
                if (memo.isBlank && templates.isNotEmpty()) AssistChip(
                    onClick = { pickTemplate = true },
                    label = { Text("틀에서 시작") },
                    leadingIcon = { Icon(Icons.Filled.Checklist, null, Modifier.size(18.dp)) },
                )
                // [J133-20] 1.33 (실사용 테스트 "알람 맞추기 칩이 옆으로 밀려 안 보여요"): first in the row, in sight on a narrow phone.
                ScheduleChip(
                    reminder = memo.reminder,
                    onOpen = { answering = null; scheduling = true },
                    onClear = { vm.setReminder(null) },
                    // 2.0.0 [RVI-49] (검토 MEMO-11) A repeating one says its rule and next day, not its first day.
                    line = memo.reminder?.let { r ->
                        val names = vm.shiftTypes.value.associate { it.id to it.name }
                        val next = if (com.bamnat.core.memo.MemoRepeat.followsSchedule(memo)) null
                            else com.bamnat.core.memo.MemoSchedules.nextOf(memo, Today.dateTime().minusMinutes(1))
                        scheduleLine(memo, r, next, { names[it] }, vm.workStyle)
                    },
                )
                // [TAB-03] 1.36 메모 → 가계부: a 금액 칸 offers one line, written only after the user checks it. Second in the row
                // (PC 확인 bad7d860: last, it sat past the screen's edge and was not found).
                // [MPH-01] 1.38 사진 넣기, in sight next to the schedule chip.
                MemoPhotoChip(com.bamnat.core.memo.MemoPhotos.count(memo.body), photoPicker, photoMenu)
                MemoToLedgerChip(memo)
                AssistChip(
                    onClick = { showDatePicker = true },
                    // This year's date without the year ("11월 7일 (토)"), so the chips after it stay in sight.
                    label = {
                        val dayText = if (date == null) "날짜 붙이기" else if (date.year == com.bamnat.core.time.Today.date().year) Fmt.dayTitle(date) else Fmt.fullDate(date)
                        // [RVI-55] The day's shift, as the label beside it said; a day with no shift (a shop, 투자) says nothing more.
                        val shift = day?.shiftType?.takeIf { date != null }
                        Text(shift?.let { "$dayText · ${it.name}" } ?: dayText, maxLines = 1)
                    },
                    leadingIcon = {
                        val shift = day?.shiftType?.takeIf { date != null }
                        if (shift != null) com.bamnat.app.ui.components.ShiftBadge(shift, size = 18.dp, showIcon = false) else Icon(Icons.Filled.Event, null, Modifier.size(18.dp))
                    },
                    // 2.0.0 [RVI-30] (검토 MEMO-09) No 18dp X inside the chip: one stray tap took the date and its repeat. '날짜 떼기' is
                    // in the date window too, and what went with it is said.
                )
                // 2.0.0 [RVP-10] (검토 UIX-10·U13-20) 날짜 떼기 is pressed as 48dp beside the chip, not 18dp inside it.
                if (date != null) com.bamnat.app.ui.components.ChipClear("날짜 떼기", onClick = { vm.setDate(null) })
                // [NOTE-05] A memo can come back every week, month or year, [EASE-22] or every 야간; [EASE-23] one with no date
                // gets the day to start from, and says so. Right after the date, before the shift label (1.38 PC 확인 9b028f4c:
                // after the label it sat past the screen's edge and took a swipe to find).
                val shiftTypes by vm.shiftTypes.collectAsStateWithLifecycle()
                val say: (String?) -> Unit = { it?.let { line -> said = line } }
                MemoRepeatChip(date, memo.repeat, { say(vm.setRepeatCode(it)) }, shiftTypes, day?.shiftType?.id, { say(vm.setShiftRepeat(it)) })
                // [NOTE-02] Which calendars show this memo; the shared notebook is every memo on more than one.
                MemoCalendarsChip(memo.calendars, calendars, vm::setCalendars)
            }
            said?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            // 1.39 [TASK-02][TASK-05] 끝냄: said in a line with the way back; all lines checked asks once whether the memo is done too.
            MemoDoneLine(memo, vm.doneDay, onDone = vm::setDone)
            // 1.38 (DB 28판) The days taken off this repeating memo (이 날만 빼기), and a way to put them back.
            if (memo.repeat.isNotEmpty() && memo.skips.isNotEmpty()) {
                val shown = memo.skips.sorted().take(SKIPS_SHOWN).joinToString(", ") { Fmt.monthDay(it) } +
                    if (memo.skips.size > SKIPS_SHOWN) " 외 ${memo.skips.size - SKIPS_SHOWN}일" else ""
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("뺀 날: $shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearSkips) { Text("다시 넣기") }
                }
            }
            // [MEMO-06] 1.27 AK: 인수인계 이어 보기 — the last day with the same shift, a tap away.
            lastSame?.let { (label, memos) ->
                // 2.0.0 [RVI-55] (디자인 품질 1·12) One line: at 200% '지난 주간(10/14) 메모 2개 보기' took two and pushed the writing
                // below the top quarter. The day is in the sheet it opens.
                if (memos.isNotEmpty()) TextButton(onClick = { showLast = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Event, null, Modifier.size(18.dp))
                    Text("  지난 ${label.substringBefore('(')} 메모 ${memos.size}개", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // [UX-12] 1.25 Z (U19): a long memo's 맨 밑으로 / 맨 위로, shown while it is scrolled and gone 2 s after.
            var jumpShown by remember { mutableStateOf(false) }
            LaunchedEffect(bodyScroll.isScrollInProgress) {
                if (bodyScroll.isScrollInProgress) jumpShown = true
                else { kotlinx.coroutines.delay(JUMP_SHOWN_MILLIS); jumpShown = false }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        val before = follow.viewport
                        follow.viewport = size.height
                        // The keyboard opening shrinks this: the caret's line comes back over it. Closing, nothing moves.
                        if (before > 0 && com.bamnat.core.memo.CaretScroll.followResize(before, size.height)) follow.again?.invoke()
                    }
                    .semantics {
                        // [UX-12] Without the button's moment on screen, TalkBack can still go to either end.
                        customActions = listOf(
                            androidx.compose.ui.semantics.CustomAccessibilityAction("맨 밑으로") { editorScope.launch { bodyScroll.animateScrollTo(bodyScroll.maxValue) }; true },
                            androidx.compose.ui.semantics.CustomAccessibilityAction("맨 위로") { editorScope.launch { bodyScroll.animateScrollTo(0) }; true },
                        )
                    }
                    .edgeScrollViewport(edge)
                    .verticalScroll(bodyScroll)
                    .onGloballyPositioned { follow.content = it }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                PlainField(
                    value = memo.title,
                    onValueChange = vm::setTitle,
                    placeholder = "제목",
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.size(8.dp))
                if (memo.isChecklist) {
                    // [J135-07] F-65: a list started from a template keeps the answers whole here, above the list.
                    if (com.bamnat.core.memo.MemoPhotos.has(memo.body)) {
                        // [MPH-01] 1.38 A checklist's photos, above its list.
                        MemoBlocksBody(
                            body = memo.body, onBody = vm::setBody, textStyle = MaterialTheme.typography.bodyMedium, trailingField = false,
                            current = { vm.memo.value.body },
                            field = { _, text, onText, _, _ ->
                                if (text.isNotBlank()) PlainField(value = text, onValueChange = onText, placeholder = "메모",
                                    textStyle = MaterialTheme.typography.bodyMedium, singleLine = false)
                            },
                            sideField = { _, text, onText, mod ->
                                PlainField(value = text, onValueChange = onText, placeholder = "사진 옆에 쓸 글",
                                    textStyle = MaterialTheme.typography.bodyMedium, singleLine = false, modifier = mod)
                            },
                        )
                        Spacer(Modifier.size(8.dp))
                    } else if (memo.body.isNotBlank()) {
                        PlainField(value = memo.body, onValueChange = vm::setBody, placeholder = "메모",
                            textStyle = MaterialTheme.typography.bodyMedium, singleLine = false)
                        Spacer(Modifier.size(8.dp))
                    }
                    ChecklistEditor(memo = memo, vm = vm)
                } else CompositionLocalProvider(LocalEdgeScroll provides edge, LocalMemoToolHost provides toolHost) {
                    // [MPH-01][MPH-02] 1.38 The body as its words and photos; without photos it is the one field it always was.
                    MemoBlocksBody(
                        body = memo.body,
                        onBody = vm::setBody,
                        textStyle = bodyStyle,
                        current = { vm.memo.value.body },
                        onBoardPhoto = { at -> photoPicker.pick(); boardTarget = at },
                        field = { _, text, onText, first, last ->
                            MemoBodyField(
                                value = text,
                                onValueChange = onText,
                                placeholder = if (first && last) "내용을 적어 보세요" else if (last && text.isEmpty()) "이어서 쓰기" else "",
                                textStyle = bodyStyle,
                                paper = paperLook,
                                modifier = if (last) Modifier.heightIn(min = if (first) 240.dp else 120.dp) else Modifier,
                                autoFocus = first && memo.id == 0L && memo.title.isEmpty(),
                                tools = bodyTools,
                                follow = follow,
                                onCaret = onCaret,
                            )
                        },
                        sideField = { _, text, onText, mod ->
                            MemoBodyField(
                                value = text,
                                onValueChange = onText,
                                placeholder = "사진 옆에 쓸 글",
                                textStyle = bodyStyle,
                                paper = paperLook,
                                modifier = mod,
                                tools = bodyTools,
                                follow = follow,
                                onCaret = onCaret,
                                takesPhotos = false,
                            )
                        },
                    )
                }
            }
                val long = bodyScroll.maxValue > follow.viewport / 2
                androidx.compose.animation.AnimatedVisibility(
                    visible = jumpShown && long,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    val down = bodyScroll.canScrollForward
                    androidx.compose.material3.SmallFloatingActionButton(onClick = {
                        editorScope.launch { if (down) bodyScroll.animateScrollTo(bodyScroll.maxValue) else bodyScroll.animateScrollTo(0) }
                    }) {
                        Icon(if (down) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, if (down) "맨 밑으로" else "맨 위로")
                    }
                }
            }
            offer?.let { found ->
                ScheduleOffer(
                    found = found,
                    changing = memo.reminder != null,
                    onAccept = {
                        val current = memo.reminder
                        if (current != null) vm.setReminder(current.copy(at = found.at), answered = found)
                        else { answering = found; scheduling = true }
                    },
                    onDecline = { vm.keepAsMemo(found) },
                    // 1.39 [CAL2-20] 근무와 겹치거나 야간 뒤 잘 시간이면 바로 한 줄.
                    clash = remember(found.at, shiftDays) { com.bamnat.core.memo.ShiftClash.of(found.at, shiftDays)?.text },
                )
            }
            // 2.0.0 [RVIB-08] (검토 I07ac-04) One offer at a time: a time's first, then a repeat with an amount.
            if (offer == null) moneyOffer?.let { found ->
                MoneyOffer(found, onAccept = { said = vm.acceptMoney(found) }, onDecline = { vm.keepMoneyAsMemo(found) })
            }
            if (!memo.isChecklist) MemoToolHostSlot(toolHost)
            if (!memo.isChecklist) MemoBodyToolbar(bodyTools)
            // [RUL-05] 1.36 사용자 "메모장 및 모든 곳에서": {근무}·{이번주야간} 같은 수식이 있으면 그날 값으로 어떻게 읽히는지.
            com.bamnat.app.logic.MemoFormulaPreview(memo, Modifier.padding(horizontal = 20.dp))
            val chars = if (memo.isChecklist) memo.checklist.sumOf { it.text.length } else memo.text.length
            Text(
                "${chars}자" + if (memo.isChecklist) " · 완료 ${memo.checkedCount}/${memo.itemCount}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    if (showDatePicker) {
        val initialMillis = (memo.date ?: Today.date()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        // 2.0.0 [RVI-30] (검토 MEMO-08) The schedule on the old day moves too, and says so.
                        said = vm.setDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                Row {
                    if (memo.date != null) TextButton(onClick = {
                        said = vm.setDate(null)
                        showDatePicker = false
                    }) { Text("날짜 떼기") }
                    TextButton(onClick = { showDatePicker = false }) { Text("취소") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (scheduling) {
        var pickedRepeat by remember { mutableStateOf<String?>(null) }
        val scheduleTypes by vm.shiftTypes.collectAsStateWithLifecycle()
        ScheduleDialog(
            initial = scheduleStart(answering, memo.reminder, memo.date, Today.dateTime(), shiftDays),
            current = memo.reminder,
            onDismiss = { scheduling = false },
            // 2.0.0 [RVI-30] (검토 MEMO-09·28) '알림 빼기' inside the window, not an 18dp X on the chip.
            onClear = { vm.setReminder(null); scheduling = false; said = "알림을 뺐어요 · 위 ↶로 되돌려요" },
            shiftTimes = remember(shiftDays) { com.bamnat.core.memo.ShiftMemoTime.suggestions(shiftDays, Today.dateTime()) },
            clashOf = { at -> com.bamnat.core.memo.ShiftClash.of(at, shiftDays) },
            heldAtWork = vm.holdAtWork,
            // 1.39 [TASK-44] PC 6차: 반복 here too (the chip by the date was not found); 쉬는 날 where the calendar has days off.
            repeat = memo.repeat,
            onRepeat = { code -> pickedRepeat = code },
            restRepeat = shiftDays.any { !it.work },
            shiftTypes = scheduleTypes,
            // 2.0.0 [RVIB-04] (검토 I07ac-01) 근무에 붙여 매번: the next time each choice falls on, from the calendar's next 60 days.
            workNext = { a -> com.bamnat.core.memo.ShiftMemoTime.workNext(shiftDays, a, Today.dateTime()) },
            wakeAfterMinutes = vm.sleepTarget,
            style = vm.workStyle,
            onSave = { r ->
                pickedRepeat?.let { said = vm.setRepeatCode(it, from = r.at.toLocalDate()) ?: said }
                pickedRepeat = null
                vm.setReminder(r, answered = answering)
                scheduling = false
                val again = com.bamnat.core.memo.MemoRepeat.label(vm.memo.value.repeat).let { if (it.isEmpty()) "" else "$it " }
                val at = r.work?.let { workLine(vm.memo.value, it, style = vm.workStyle) }
                    ?: if (again.isEmpty()) scheduleText(r.at) else again + scheduleText(r.at).substringAfter(") ")
                Toast.makeText(context, "${at}에 ${scheduleWays(r)}${com.bamnat.app.ui.Fmt.ro(scheduleWays(r))} 알려 드릴게요", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showLast) lastSame?.let { (label, memos) ->
        // 2.0.0 [RVI-85] (검토 T07a-11·I07ac-03) What was left unticked last time comes into this memo with one tap.
        val carry = remember(memos, memo) { com.bamnat.core.memo.TaskList.carried(memos, memo) }
        LastShiftDialog(label, memos, carry, onCarry = {
            vm.carry(carry)
            said = "지난 ${label.substringBefore('(')}에서 ${carry.size}줄을 가져왔어요 · 위 ↶로 되돌려요"
            showLast = false
        }) { showLast = false }
    }

    if (confirmDelete) {
        val deleteTypes by vm.shiftTypes.collectAsStateWithLifecycle()
        // 1.38 (DB 28판) A repeating memo asks: 이 날만 빼기 (the day it was opened from) or 반복 전부 지우기.
        MemoDeleteDialog(
            memo = memo,
            day = vm.openedOn,
            shiftName = { id -> deleteTypes.firstOrNull { it.id == id }?.name },
            plainTitle = "메모를 지울까요?",
            plainMessage = "휴지통으로 옮겨요. 30일 동안 되살릴 수 있어요.",
            onDismiss = { confirmDelete = false },
            onDeleteAll = {
                confirmDelete = false
                vm.delete(onBack)
            },
            onSkipDay = { d ->
                confirmDelete = false
                vm.skipDay(d, onBack)
            },
        )
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (i in MemoPaletteLight.indices) {
            val color = memoBackground(i)
            // 2.0.0 [RVI-48] (검토 MEMO-29) 48dp to the finger around the 36dp dot, and its colour's name for TalkBack.
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = memoColorName(i) + if (i == selected) ", 선택됨" else "" }
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (i == selected) 3.dp else 1.dp,
                        color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
            } }
        }
    }
}

/** 2.0.0 [RVI-48] The memo colours by name, in [MemoPaletteLight]'s order. */
internal fun memoColorName(i: Int): String = MEMO_COLOR_NAMES.getOrElse(i) { "색 ${i + 1}" }

private val MEMO_COLOR_NAMES = listOf("색 없음", "노랑", "주황", "분홍", "보라", "파랑", "민트", "연두")

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Default,
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        textStyle = textStyle.copy(color = color),
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                inner()
            }
        },
    )
}

/**
 * The memo's body. [NOTE-03] The line being typed stays above the keyboard: whenever the text or the
 * caret moves, the caret's line (and a little room under it) is scrolled into view, so a long memo
 * never runs on behind the keyboard. [NOTE-04] Enter at the end of "1. …" or "- …" starts the next item.
 * [NOTE-08] A numbered list stays in order after an edit inside it. [NOTE-07] A tap on a "☐ " box ticks
 * it, and the bar above the keyboard ([tools]) puts list marks and today's date on the caret's line.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MemoBodyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    tools: MemoBodyTools? = null,
    follow: CaretFollow? = null,
    /** [UX-11] The caret's line in the field ([LayoutCoordinates]) to keep in view; animated when typing moved it. */
    onCaret: ((androidx.compose.ui.layout.LayoutCoordinates, androidx.compose.ui.geometry.Rect, Boolean) -> Unit)? = null,
    /** [MPH-01] 1.38 False for the words beside a photo: a photo put in from there goes after it instead. */
    takesPhotos: Boolean = true,
    /** [MPH-05] 1.38 메모 종이: lines behind the words, at their line height. */
    paper: MemoPaperLook? = null,
) {
    val paperStep = with(androidx.compose.ui.platform.LocalDensity.current) {
        (if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else 16.sp.toPx()) * 1.5f
    }
    @Suppress("NAME_SHADOWING") val onValueChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val me = remember { Any() }
    val focus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value)) }
    // Undo, redo and the checklist switch change the text from outside: the caret goes to its end.
    if (field.text != value) field = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // [MEMO-SEL-01] 1.30 AY: a handle pulled to the memo's edge scrolls it; from there only the selection changes.
    val drag = rememberSelectionDrag(value = { field }, select = { field = it })
    // [UX-11] 1.25 Z (U10): the caret is followed when the text or the caret moves, not when the text is laid out
    // again or the focus changes (the moments the keyboard and the opening screen were still moving).
    fun caretNow(): Pair<androidx.compose.ui.layout.LayoutCoordinates, androidx.compose.ui.geometry.Rect>? {
        val l = layout ?: return null
        val c = coords ?: return null
        return c to l.getCursorRect(field.selection.end.coerceIn(0, l.layoutInput.text.length))
    }
    LaunchedEffect(field.selection, field.text) {
        // [MEMO-SEL-01] A selection being dragged is the edge scroll's to follow: an animated jump here slid the text
        // from under the finger. Typing, a tap and a keyboard's shift + arrows still bring the caret into view.
        if (!focused || (!field.selection.collapsed && drag.touch)) return@LaunchedEffect
        // One frame, so the new line is laid out before it is looked for.
        androidx.compose.runtime.withFrameNanos { }
        caretNow()?.let { (c, r) -> onCaret?.invoke(c, r, true) }
    }
    if (follow != null) {
        DisposableEffect(follow) {
            follow.again = { if (focused) caretNow()?.let { (c, r) -> onCaret?.invoke(c, r, false) } }
            onDispose { follow.again = null }
        }
    }
    if (tools != null && focused) {
        // [NOTE-07] The bar's taps change the text where the caret is, like typing would. [MPH-01] Only the field typed in owns it.
        tools.onAction = { action ->
            val at = com.bamnat.core.memo.ListContinuation.Edit(field.text, field.selection.end)
            val edit = when (action) {
                is MemoBodyTools.Action.Mark -> com.bamnat.core.memo.MemoText.toggleMark(at, action.mark)
                MemoBodyTools.Action.Today -> com.bamnat.core.memo.MemoText.insert(
                    field.text, field.selection.start, field.selection.end, com.bamnat.core.memo.MemoText.dateStamp(Today.date()),
                )
            }
            val done = com.bamnat.core.memo.ListContinuation.renumber(at, edit)
            field = androidx.compose.ui.text.input.TextFieldValue(done.text, androidx.compose.ui.text.TextRange(done.cursor))
            if (field.text != value) onValueChange(field.text)
        }
        // [MPH-01] 1.38 A photo goes in at the caret, on a line of its own.
        tools.insertPhoto = if (!takesPhotos) null else { names ->
            val done = com.bamnat.core.memo.MemoPhotos.insertAll(field.text, field.selection.end, names)
            field = androidx.compose.ui.text.input.TextFieldValue(done.text, androidx.compose.ui.text.TextRange(done.caret))
            onValueChange(done.text)
        }
        tools.insertBoard = if (!takesPhotos) null else { ->
            val done = com.bamnat.core.memo.MemoPhotos.insertBoard(field.text, field.selection.end)
            field = androidx.compose.ui.text.input.TextFieldValue(done.text, androidx.compose.ui.text.TextRange(done.caret))
            onValueChange(done.text)
        }
    }
    if (tools != null) {
        LaunchedEffect(focused) {
            if (focused) { tools.owner = me; tools.editing = true } else if (tools.owner === me) tools.editing = false
        }
        DisposableEffect(tools) {
            onDispose { if (tools.owner === me) { tools.owner = null; tools.editing = false; tools.insertPhoto = null; tools.insertBoard = null; tools.onAction = null } }
        }
    }
    val color = MaterialTheme.colorScheme.onSurface
    drag.Toolbar { BasicTextField(
        value = field,
        onValueChange = { typed ->
            val before = field
            // [MEMO-SEL-01] A finger on a handle: its end where the finger really is (the memo may have scrolled under it).
            val next = drag.filter(before, typed)
            if (next != typed) {
                field = next
                return@BasicTextField
            }
            val was = com.bamnat.core.memo.ListContinuation.Edit(before.text, before.selection.end)
            // [NOTE-07] A tap (the caret moved, nothing typed) on a check box ticks it.
            if (next.text == before.text && next.composition == null && next.selection.collapsed && next.selection != before.selection) {
                val ticked = com.bamnat.core.memo.MemoText.toggleCheck(next.text, next.selection.start)
                if (ticked != null) {
                    field = androidx.compose.ui.text.input.TextFieldValue(ticked.text, androidx.compose.ui.text.TextRange(ticked.cursor))
                    onValueChange(field.text)
                    return@BasicTextField
                }
            }
            // While a Korean letter is still being put together, the text is left exactly as the keyboard has it.
            val goOn = if (next.composition == null) {
                val typed = com.bamnat.core.memo.ListContinuation.Edit(next.text, next.selection.end)
                com.bamnat.core.memo.ListContinuation.renumber(was, com.bamnat.core.memo.ListContinuation.apply(was, typed))
            } else null
            field = if (goOn != null && goOn.text != next.text) {
                androidx.compose.ui.text.input.TextFieldValue(goOn.text, androidx.compose.ui.text.TextRange(goOn.cursor))
            } else next
            if (field.text != value) onValueChange(field.text)
        },
        onTextLayout = { layout = it; drag.layout = it },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                drag.focused = it.isFocused
                if (!it.isFocused) drag.reset()
            }
            .onGloballyPositioned { coords = it; drag.text = it }
            .memoPaper(paper, { layout }, paperStep),
        textStyle = textStyle.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                inner()
            }
        },
    ) }
}

/**
 * Checklist editing: Enter adds the next item, unchecked items stay on top and finished
 * ones collect under "완료". 2.0.0 [RVI-35] (검토 MEMO-16) 체크리스트 기본기: a pasted list becomes a line each, ⌫ at the start of a
 * line joins it to the line above, the handle (or 위로·아래로 for TalkBack) moves a line, and the X is a full 48dp.
 */
@Composable
private fun ChecklistEditor(memo: Memo, vm: MemoEditorViewModel) {
    var focusIndex by remember { mutableStateOf<Int?>(null) }
    var focusCaret by remember { mutableStateOf<Int?>(null) }
    val open = memo.checklist.withIndex().filter { !it.value.checked }
    val done = memo.checklist.withIndex().filter { it.value.checked }
    // The line being dragged by its handle (its place in the whole list) and how far the finger is past its last move.
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0f) }

    Column {
        for ((k, entry) in open.withIndex()) {
            val index = entry.index
            val above = open.getOrNull(k - 1)?.index
            val below = open.getOrNull(k + 1)?.index
            ChecklistRow(
                text = entry.value.text,
                checked = false,
                starred = entry.value.starred,
                time = entry.value.time,
                note = entry.value.note,
                onStar = { vm.starItem(index, !entry.value.starred)?.let { focusIndex = it; focusCaret = null } },
                onTime = { vm.setItemTime(index, it) },
                onNote = { vm.setItemNote(index, it) },
                requestFocus = focusIndex == index,
                caret = focusCaret,
                onFocusHandled = { focusIndex = null; focusCaret = null },
                onTextChange = { value -> vm.typeItem(index, value)?.let { focusIndex = it; focusCaret = null } },
                onCheckedChange = { vm.setItemChecked(index, it) },
                onNext = { focusIndex = vm.insertItemAfter(index); focusCaret = null },
                onRemove = { vm.removeItem(index) },
                onJoinAbove = if (above == null) null else {
                    { vm.joinItemAbove(index, above)?.let { (line, at) -> focusIndex = line; focusCaret = at } }
                },
                onMoveUp = above?.let { to -> { vm.moveItem(index, to) } },
                onMoveDown = below?.let { to -> { vm.moveItem(index, to) } },
                dragged = dragging == index,
                dragOffset = if (dragging == index) dragOffset else 0f,
                onHeight = { rowHeight = it },
                onDrag = { dy ->
                    val from = dragging ?: index.also { dragging = it }
                    dragOffset += dy
                    val list = open.map { it.index }
                    val at = list.indexOf(from)
                    if (rowHeight > 0f && at >= 0) {
                        if (dragOffset > rowHeight * 0.6f && at < list.lastIndex) {
                            vm.moveItem(from, list[at + 1]); dragging = list[at + 1]; dragOffset -= rowHeight
                        } else if (dragOffset < -rowHeight * 0.6f && at > 0) {
                            vm.moveItem(from, list[at - 1]); dragging = list[at - 1]; dragOffset += rowHeight
                        }
                    }
                },
                onDragEnd = { dragging = null; dragOffset = 0f },
            )
        }
        TextButton(onClick = {
            val last = memo.checklist.indexOfLast { !it.checked }
            focusIndex = vm.insertItemAfter(if (last >= 0) last else memo.checklist.size - 1)
            focusCaret = null
        }) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("항목 추가")
        }
        if (done.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "완료 ${done.size}개",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            for ((index, item) in done) {
                ChecklistRow(
                    text = item.text,
                    checked = true,
                    starred = item.starred,
                    time = item.time,
                    note = item.note,
                    requestFocus = false,
                    caret = null,
                    onFocusHandled = {},
                    onTextChange = { vm.setItemText(index, it.replace("\n", " ")) },
                    onCheckedChange = { vm.setItemChecked(index, it) },
                    onNext = {},
                    onRemove = { vm.removeItem(index) },
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    text: String,
    checked: Boolean,
    requestFocus: Boolean,
    // 2.0.0 [RVIB-02] (검토 I07ab-03) The line's star, time and note; an open line ([onStar] set) changes them from the chips that
    // show under it while it is being written.
    starred: Boolean = false,
    time: java.time.LocalTime? = null,
    note: String = "",
    onStar: (() -> Unit)? = null,
    onTime: ((java.time.LocalTime?) -> Unit)? = null,
    onNote: ((String) -> Unit)? = null,
    caret: Int?,
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
    onJoinAbove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    dragged: Boolean = false,
    dragOffset: Float = 0f,
    onHeight: (Float) -> Unit = {},
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    // The line's own cursor, so ⌫ at its start can be told from ⌫ inside it; the words come from [text].
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shown = if (field.text == text) field else TextFieldValue(text, TextRange(field.selection.end.coerceAtMost(text.length)))
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            field = TextFieldValue(text, TextRange((caret ?: text.length).coerceIn(0, text.length)))
            runCatching { focus.requestFocus() }
            onFocusHandled()
        }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (checked) TextDecoration.LineThrough else null,
    )
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    // 2.0.0 [RVIB-02] The chips show while the line or its note is being written; the note's own line once it has words.
    var lineFocused by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val noteFocus = remember { FocusRequester() }
    LaunchedEffect(noteOpen) { if (noteOpen) runCatching { noteFocus.requestFocus() } }
    Column(
        modifier = Modifier
            .onSizeChanged { onHeight(it.height.toFloat()) }
            .graphicsLayer { translationY = dragOffset; shadowElevation = if (dragged) 6f else 0f }
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { up -> CustomAccessibilityAction("위로 옮기기") { up(); true } },
                    onMoveDown?.let { down -> CustomAccessibilityAction("아래로 옮기기") { down(); true } },
                    onStar?.let { star -> CustomAccessibilityAction(if (starred) "별표 빼기" else "별표 달고 위로") { star(); true } },
                )
            },
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onDrag != null) {
            Icon(
                Icons.Filled.DragIndicator, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { dragEnd() },
                            onDragCancel = { dragEnd() },
                        ) { change, amount -> change.consume(); drag?.invoke(amount.y) }
                    },
            )
        } else {
            // 2.0.0 [RVI-35] A finished line (no handle) keeps its box under the open lines' boxes.
            Spacer(Modifier.minimumInteractiveComponentSize())
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        BasicTextField(
            value = shown,
            onValueChange = { v -> field = v; if (v.text != text) onTextChange(v.text) },
            modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = 8.dp)
                .onFocusChanged { lineFocused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    val join = onJoinAbove
                    if (join != null && e.type == KeyEventType.KeyDown && e.key == Key.Backspace &&
                        shown.selection.collapsed && shown.selection.start == 0
                    ) {
                        join(); true
                    } else {
                        false
                    }
                },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("할 일", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    inner()
                }
            },
        )
        // 2.0.0 [RVIB-02] A starred line says so by its words, and its time sits beside them.
        if (starred) Icon(Icons.Filled.Star, "별표", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        time?.let {
            Text(Fmt.time(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, "항목 지우기", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
    // Under the words, where they start: the line's note, then the chips.
    val under = Modifier.padding(start = 96.dp, end = 48.dp)
    if (note.isNotEmpty() || noteOpen) {
        if (onNote == null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = under.padding(bottom = 6.dp))
        } else {
            val noteStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = note,
                onValueChange = { onNote(it.replace("\n", " ")) },
                singleLine = true,
                textStyle = noteStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = under.fillMaxWidth().padding(bottom = 6.dp).focusRequester(noteFocus)
                    .onFocusChanged { f -> noteFocused = f.isFocused; if (!f.isFocused && note.isEmpty()) noteOpen = false },
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty()) Text("메모", style = noteStyle.copy(color = noteStyle.color.copy(alpha = 0.5f)))
                        inner()
                    }
                },
            )
        }
    }
    if (onStar != null && (lineFocused || noteFocused)) {
        FlowRow(under.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = starred, onClick = onStar, label = { Text(if (starred) "별표 빼기" else "별표 달고 위로") },
                leadingIcon = { Icon(Icons.Filled.Star, null, modifier = Modifier.size(18.dp)) })
            if (onTime != null) {
                if (time == null) FilterChip(selected = false, onClick = { picking = true }, label = { Text("시각") })
                else FilterChip(selected = true, onClick = { onTime(null) }, label = { Text(Fmt.time(time)) },
                    trailingIcon = { Icon(Icons.Filled.Close, "시각 빼기", modifier = Modifier.size(18.dp)) })
            }
            if (onNote != null && note.isEmpty() && !noteOpen) FilterChip(selected = false, onClick = { noteOpen = true }, label = { Text("메모") })
        }
    }
    }
    if (picking && onTime != null) {
        TimePickerDialog(
            title = "이 줄의 시각",
            initial = time ?: Today.dateTime().toLocalTime().withSecond(0).withNano(0),
            onDismiss = { picking = false },
        ) { onTime(it); picking = false }
    }
}

private val savedFormat = DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN)

/**
 * 2.0.0 [RVI-55] (검토 U07a-05) The editor's chips: on one line as many as fit and a '+N' chip for the rest; opened, all of them flow
 * onto as many lines as they take, with '접기' last. When all fit there is no '+N'.
 */
@Composable
private fun ChipLine(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, chips: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val gap = 8.dp.roundToPx()
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val items = subcompose("chips", chips).map { it.measure(loose) }
        val width = c.maxWidth
        fun toggle(slot: String, hidden: Int) = subcompose(slot) { MoreChips(open, hidden, onToggle) }.map { it.measure(loose) }.first()
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        if (items.sumOf { it.width } + gap * (items.size - 1).coerceAtLeast(0) <= width) {
            rows += items
        } else if (!open) {
            // '+N' is about as wide for any N under ten: measured once for the most it could hide, then for what it does.
            val probe = toggle("probe", items.size)
            var shown = 0
            var x = 0
            while (shown < items.size && x + items[shown].width + gap + probe.width <= width) { x += items[shown].width + gap; shown++ }
            val more = toggle("more", items.size - shown.coerceAtLeast(1))
            // The first chip always shows; when it fills the line itself (a long 알림 at 200%), '+N' goes under it.
            if (shown == 0) { rows += listOf(items.first()); rows += listOf(more) } else rows += items.take(shown) + more
        } else {
            var row = mutableListOf<androidx.compose.ui.layout.Placeable>()
            var x = 0
            for (p in items + toggle("more", 0)) {
                if (row.isNotEmpty() && x + gap + p.width > width) { rows += row; row = mutableListOf(); x = 0 }
                x += (if (row.isEmpty()) 0 else gap) + p.width
                row += p
            }
            if (row.isNotEmpty()) rows += row
        }
        val heights = rows.map { r -> r.maxOf { it.height } }
        layout(width, heights.sum()) {
            var y = 0
            rows.forEachIndexed { i, r ->
                var x = 0
                r.forEach { p -> p.placeRelative(x, y + (heights[i] - p.height) / 2); x += p.width + gap }
                y += heights[i]
            }
        }
    }
}

@Composable
private fun MoreChips(open: Boolean, hidden: Int, onToggle: () -> Unit) {
    AssistChip(
        onClick = onToggle,
        label = { Text(if (open) "접기" else "+$hidden", maxLines = 1) },
        leadingIcon = { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp)) },
        modifier = Modifier.semantics { contentDescription = if (open) "칩 접기" else "칩 ${hidden}개 더 보기" },
    )
}

private fun savedTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(Today.zone()).toLocalTime().format(savedFormat)

private fun shareText(memo: Memo): String = buildString {
    if (memo.title.isNotBlank()) appendLine(memo.title)
    memo.date?.let { appendLine(Fmt.fullDate(it)) }
    if (memo.title.isNotBlank() || memo.date != null) appendLine()
    if (memo.isChecklist) {
        memo.checklist.filter { it.text.isNotBlank() }.forEach { appendLine(com.bamnat.core.memo.ChecklistEdit.shared(it)) }
    } else {
        // [MPH-04] 1.38 The words only; the photos go with the share as pictures (MemoPhotoFiles.shareIntent).
        append(memo.text)
    }
}.trimEnd()

/** [UX-11] What the memo's scroll and its body field share to keep the caret's line in view. */
internal class CaretFollow {
    var content: androidx.compose.ui.layout.LayoutCoordinates? = null
    var viewport = 0
    /** Brings the caret back into view now (the keyboard opened); set by the body field while it is there. */
    var again: (() -> Unit)? = null
}

/** [UX-12] How long 맨 밑으로 stays after the scroll stops. */
private const val JUMP_SHOWN_MILLIS = 2_000L

/** [MEMO-06] 1.27 AK: the memos of the last day with the same shift, to read while writing today's. */
@Composable
private fun LastShiftDialog(label: String, memos: List<Memo>, carry: List<String> = emptyList(), onCarry: () -> Unit = {}, onDismiss: () -> Unit) {
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("지난 $label 메모") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (m in memos) Column {
                    if (m.title.isNotBlank()) Text(m.title, style = MaterialTheme.typography.titleSmall)
                    val body = if (m.isChecklist) (listOfNotNull(m.body.ifBlank { null }) + m.checklist.filter { it.text.isNotBlank() }.map { com.bamnat.core.memo.ChecklistEdit.shared(it) }).joinToString("\n") else m.body
                    Text(com.bamnat.core.memo.MemoSearch.excerpt(body, ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        // [RVI-85] 안 끝낸 줄 가져오기 is the one thing to do here when there are such lines; 닫기 beside it.
        confirmButton = {
            if (carry.isNotEmpty()) androidx.compose.material3.Button(onClick = onCarry) { Text("안 끝낸 ${carry.size}줄 가져오기") }
            else TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = if (carry.isNotEmpty()) ({ TextButton(onClick = onDismiss) { Text("닫기") } }) else null,
    )
}

/** [RCP-11] 1.34 메모 틀 고르기: a tap starts the memo from it; 지우기 takes the template away (memos made from it stay). */
@Composable
private fun MemoTemplateDialog(
    templates: List<com.bamnat.core.memo.MemoTemplate>,
    onPick: (com.bamnat.core.memo.MemoTemplate) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 1.35 자유도 점검: 지우기 asks first, like every other 지우기 (RCX-26).
    var removing by remember { mutableStateOf<com.bamnat.core.memo.MemoTemplate?>(null) }
    removing?.let { t ->
        com.bamnat.app.ui.components.AlertDialog(onDismissRequest = { removing = null }, title = { Text("이 틀을 지울까요?") },
            text = { Text("‘${t.title}’ 틀만 지워요. 이 틀로 쓴 메모는 그대로예요" + if (t.from != null) ". 장터에서 받은 틀은 작품을 다시 넣으면 돌아와요" else "") },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(t.id) }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("닫기") } })
    }
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("틀에서 시작") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                templates.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "이 틀로 시작") { onPick(t) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(t.emoji, t.title).joinToString(" "), style = MaterialTheme.typography.bodyLarge)
                            val what = buildList {
                                if (t.asks.isNotEmpty()) add("입력 칸 ${t.asks.size}개")
                                if (t.check.isNotEmpty()) add("체크 ${t.check.size}개")
                                com.bamnat.core.memo.MemoRepeat.label(t.repeat).takeIf { it.isNotEmpty() }?.let { add(it) }
                                if (t.from != null) add("장터에서 받음")
                            }.joinToString(" · ")
                            if (what.isNotEmpty()) Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { removing = t }) { Icon(Icons.Filled.Delete, "이 틀 지우기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * [CVW-10] 1.35 메모 틀의 입력 칸: each thing the template asks, as its kind — a line, a number, 별점 1~5 or one of its choices — then
 * the memo starts with "칸: 값" lines. Anything left empty is left out; 건너뛰기 starts the memo with none.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TemplateAskDialog(t: com.bamnat.core.memo.MemoTemplate, onDone: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val answers = remember(t.id) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(listOfNotNull(t.emoji, t.title).joinToString(" ")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                t.asks.forEachIndexed { i, a ->
                    // By place, so two 칸 with one name keep their own answers (F-55).
                    val key = "#$i"
                    val v = answers[key].orEmpty()
                    when (a.type) {
                        "star" -> Column {
                            Text(a.label, style = MaterialTheme.typography.bodyMedium)
                            Row {
                                (1..5).forEach { n ->
                                    val on = (v.toIntOrNull() ?: 0) >= n
                                    TextButton(onClick = { answers[key] = if (v == "$n") "" else "$n" }, modifier = Modifier.semantics { contentDescription = "${a.label} 별 ${n}개" }) {
                                        Text(if (on) "★" else "☆", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        "pick" -> Column {
                            Text(a.label, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                a.choices.forEach { c -> com.bamnat.app.ui.components.FilterChip(selected = v == c, onClick = { answers[key] = if (v == c) "" else c }, label = { Text(c) }) }
                            }
                        }
                        // [TAB-03] 1.36 체크: 예 or 아니오.
                        "check" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            for (c in listOf("예", "아니오")) com.bamnat.app.ui.components.FilterChip(selected = v == c, onClick = { answers[key] = if (v == c) "" else c }, label = { Text(c) })
                        }
                        // [TAB-03] 날짜: 오늘·내일 one tap, or typed as 11월 3일 / 2026-11-03.
                        "date" -> Column {
                            androidx.compose.material3.OutlinedTextField(v, { answers[key] = it.take(20) }, label = { Text(a.label) }, placeholder = { Text("11월 3일") },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val today = com.bamnat.core.time.Today.date()
                                for ((n, d) in listOf("오늘" to today, "내일" to today.plusDays(1))) {
                                    val s = "${d.monthValue}월 ${d.dayOfMonth}일"
                                    com.bamnat.app.ui.components.FilterChip(selected = v == s, onClick = { answers[key] = s }, label = { Text(n) })
                                }
                            }
                        }
                        else -> androidx.compose.material3.OutlinedTextField(v, { answers[key] = (if (a.type == "num" || a.type == "money") numberOnly(it) else it).take(100) }, label = { Text(a.label) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            suffix = if (a.type == "money") ({ Text("원") }) else null,
                            placeholder = if (a.type == "time") ({ Text("22:30") }) else null,
                            keyboardOptions = if (a.type == "num" || a.type == "money") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                                else if (a.type == "time") androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                else androidx.compose.foundation.text.KeyboardOptions.Default)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(answers.toMap()) }) { Text("메모 시작") } },
        dismissButton = { TextButton(onClick = { onDone(emptyMap()) }) { Text("건너뛰기") } },
    )
}

/** [J135-06] F-62: a 숫자 칸 keeps digits, one point and a leading minus, so a typed letter or a pasted word does not stay. */
internal fun numberOnly(typed: String): String {
    val sb = StringBuilder()
    var point = false
    typed.forEachIndexed { i, ch ->
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !point -> { point = true; sb.append(ch) }
            ch == '-' && i == 0 -> sb.append(ch)
        }
    }
    return sb.toString()
}

/** 1.38 How many days taken off a repeating memo the editor names before "외 N일". */
private const val SKIPS_SHOWN = 3

/**
 * 1.39 [TASK-02] "끝냈어요 · 11월 7일 오후 2:30" with 끝냄 풀기, on a finished memo. [TASK-05] A checklist whose every line is checked
 * and that is not finished asks "모두 체크했어요. 이 메모도 끝낼까요?" (할 일 앱 리뷰가 바란 하위 할 일 자동 완료, 묻고 나서); 아니요 is
 * kept for this visit, and a line unchecked again asks again later.
 */
@Composable
internal fun MemoDoneLine(memo: com.bamnat.core.memo.Memo, day: java.time.LocalDate, onDone: (Boolean) -> Unit) {
    val done = memo.isDoneOn(day)
    val allChecked = memo.isChecklist && memo.itemCount > 0 && memo.checkedCount == memo.itemCount
    var declined by rememberSaveable { mutableStateOf(false) }
    if (!allChecked) declined = false
    if (done) {
        val whenText = if (memo.repeat.isNotEmpty()) Fmt.dayTitle(day) + " 몫"
            else memo.doneAtMillis?.let { Fmt.monthDay(Instant.ofEpochMilli(it).atZone(Today.zone()).toLocalDate()) + " " + savedTime(it) }.orEmpty()
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("  끝냈어요" + if (whenText.isNotEmpty()) " · $whenText" else "", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDone(false) }) { Text("끝냄 풀기") }
        }
    } else if (allChecked && !declined) {
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("모두 체크했어요. 이 메모도 끝낼까요?", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                TextButton(onClick = { declined = true }) { Text("아니요") }
                TextButton(onClick = { onDone(true) }) { Text("끝내기") }
            }
        }
    }
}
