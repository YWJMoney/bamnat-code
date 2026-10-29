package com.bamnat.app.ui.memo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import android.widget.Toast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ConfirmDialog
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    // [MEMO-05][MEMO-06] 1.27 AK: the calendar's shifts ahead, and the last memo of the same shift.
    val shiftDays by vm.shiftDays.collectAsStateWithLifecycle()
    val lastSame by vm.lastSameShift.collectAsStateWithLifecycle()
    var showLast by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // [SCHED-01] The schedule dialog, and the found time it answers when it was opened from the offer.
    var scheduling by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<com.bamnat.core.memo.MemoTime.Found?>(null) }

    var showColors by remember { mutableStateOf(false) }
    // [RCP-11] 1.34 메모 틀.
    val templates by vm.templates.collectAsStateWithLifecycle()
    var pickTemplate by remember { mutableStateOf(false) }
    if (pickTemplate) MemoTemplateDialog(templates, onPick = { vm.startFrom(it); pickTemplate = false }, onRemove = vm::removeTemplate,
        onDismiss = { pickTemplate = false })
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    if (copying) com.bamnat.app.ui.components.CopyTextSheet(memo.title.ifBlank { "메모" }, shareText(memo)) { copying = false }
    val bodyTools = remember { MemoBodyTools() }
    val background = memoBackground(memo.colorIndex)
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
                    Text(
                        savedAt?.let { "저장됨 · " + savedTime(it) } ?: if (memo.id == 0L) "새 메모" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    // Nothing can be changed until an existing memo has finished loading.
                    if (!loaded) return@TopAppBar
                    IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "실행 취소") }
                    IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
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
                                onClick = {
                                    menu = false
                                    if (vm.saveAsTemplate()) Toast.makeText(context, "틀로 저장했어요. 새 메모에서 '틀에서 시작'을 누르세요", Toast.LENGTH_LONG).show()
                                },
                                enabled = !memo.isBlank,
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
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
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
                            DropdownMenuItem(
                                text = { Text("복제") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    menu = false
                                    vm.duplicate()
                                    Toast.makeText(context, "복사본을 만들었어요", Toast.LENGTH_SHORT).show()
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
                                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val date = memo.date
                // [J133-20] 1.33 (실사용 테스트 "알람 맞추기 칩이 옆으로 밀려 안 보여요"): first in the row, in sight on a narrow phone.
                ScheduleChip(
                    reminder = memo.reminder,
                    onOpen = { answering = null; scheduling = true },
                    onClear = { vm.setReminder(null) },
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(if (date != null) Fmt.fullDate(date) else "날짜 붙이기") },
                    leadingIcon = { Icon(Icons.Filled.Event, null, Modifier.size(18.dp)) },
                    trailingIcon = if (date != null) {
                        { Icon(Icons.Filled.Close, "날짜 떼기", Modifier.size(18.dp).clickable { vm.setDate(null) }) }
                    } else {
                        null
                    },
                )
                if (date != null) ShiftLabel(day)
                // [NOTE-05] A dated memo can come back every week, month or year.
                if (date != null) MemoRepeatChip(date, memo.repeat, vm::setRepeat)
                // [NOTE-02] Which calendars show this memo; the shared notebook is every memo on more than one.
                MemoCalendarsChip(memo.calendars, calendars, vm::setCalendars)
                // [RCP-11] 1.34: a memo not written yet can start from a 틀.
                if (memo.isBlank && templates.isNotEmpty()) AssistChip(
                    onClick = { pickTemplate = true },
                    label = { Text("틀에서 시작") },
                    leadingIcon = { Icon(Icons.Filled.Checklist, null, Modifier.size(18.dp)) },
                )
            }
            // [MEMO-06] 1.27 AK: 인수인계 이어 보기 — the last day with the same shift, a tap away.
            lastSame?.let { (label, memos) ->
                if (memos.isNotEmpty()) TextButton(onClick = { showLast = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Event, null, Modifier.size(18.dp))
                    Text("  지난 $label 메모 ${memos.size}개 보기")
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
                    ChecklistEditor(memo = memo, vm = vm)
                } else CompositionLocalProvider(LocalEdgeScroll provides edge) {
                    MemoBodyField(
                        value = memo.body,
                        onValueChange = vm::setBody,
                        placeholder = "내용을 적어 보세요",
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.heightIn(min = 240.dp),
                        autoFocus = memo.id == 0L && memo.title.isEmpty(),
                        tools = bodyTools,
                        follow = follow,
                        onCaret = onCaret,
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
                )
            }
            if (!memo.isChecklist) MemoBodyToolbar(bodyTools)
            val chars = if (memo.isChecklist) memo.checklist.sumOf { it.text.length } else memo.body.length
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
                        vm.setDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (scheduling) {
        ScheduleDialog(
            initial = scheduleStart(answering, memo.reminder, memo.date, Today.dateTime()),
            current = memo.reminder,
            onDismiss = { scheduling = false },
            shiftTimes = remember(shiftDays) { com.bamnat.core.memo.ShiftMemoTime.suggestions(shiftDays, Today.dateTime()) },
            onSave = { r ->
                vm.setReminder(r, answered = answering)
                scheduling = false
                Toast.makeText(context, "${scheduleText(r.at)}에 ${scheduleWays(r)}${com.bamnat.app.ui.Fmt.ro(scheduleWays(r))} 알려 드릴게요", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showLast) lastSame?.let { (label, memos) -> LastShiftDialog(label, memos) { showLast = false } }

    if (confirmDelete) {
        ConfirmDialog(
            title = "메모를 지울까요?",
            message = "휴지통으로 옮겨요. 30일 동안 되살릴 수 있어요.",
            confirmLabel = "삭제",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
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
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (i == selected) 3.dp else 1.dp,
                        color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Filled.Check, "선택됨", Modifier.size(18.dp))
            }
        }
    }
}

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
private fun MemoBodyField(
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
) {
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
    if (tools != null) {
        // [NOTE-07] The bar's taps change the text where the caret is, like typing would.
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
        LaunchedEffect(focused) { tools.editing = focused }
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
            .onGloballyPositioned { coords = it; drag.text = it },
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
 * ones collect under "완료".
 */
@Composable
private fun ChecklistEditor(memo: Memo, vm: MemoEditorViewModel) {
    var focusIndex by remember { mutableStateOf<Int?>(null) }
    val open = memo.checklist.withIndex().filter { !it.value.checked }
    val done = memo.checklist.withIndex().filter { it.value.checked }

    Column {
        for ((index, item) in open) {
            ChecklistRow(
                text = item.text,
                checked = false,
                requestFocus = focusIndex == index,
                onFocusHandled = { focusIndex = null },
                onTextChange = { value ->
                    val newline = value.indexOf('\n')
                    if (newline >= 0) {
                        vm.setItemText(index, value.substring(0, newline))
                        focusIndex = vm.insertItemAfter(index, value.substring(newline + 1).replace("\n", " "))
                    } else {
                        vm.setItemText(index, value)
                    }
                },
                onCheckedChange = { vm.setItemChecked(index, it) },
                onNext = { focusIndex = vm.insertItemAfter(index) },
                onRemove = { vm.removeItem(index) },
            )
        }
        TextButton(onClick = {
            val last = memo.checklist.indexOfLast { !it.checked }
            focusIndex = vm.insertItemAfter(if (last >= 0) last else memo.checklist.size - 1)
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
                    requestFocus = false,
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
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focus.requestFocus() }
            onFocusHandled()
        }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (checked) TextDecoration.LineThrough else null,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = 8.dp),
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
        IconButton(onClick = onRemove, modifier = Modifier.widthIn(max = 40.dp)) {
            Icon(Icons.Filled.Close, "항목 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

private val savedFormat = DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN)

private fun savedTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(Today.zone()).toLocalTime().format(savedFormat)

private fun shareText(memo: Memo): String = buildString {
    if (memo.title.isNotBlank()) appendLine(memo.title)
    memo.date?.let { appendLine(Fmt.fullDate(it)) }
    if (memo.title.isNotBlank() || memo.date != null) appendLine()
    if (memo.isChecklist) {
        memo.checklist.filter { it.text.isNotBlank() }.forEach { appendLine((if (it.checked) "☑ " else "☐ ") + it.text) }
    } else {
        append(memo.body)
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
private fun LastShiftDialog(label: String, memos: List<Memo>, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("지난 $label 메모") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (m in memos) Column {
                    if (m.title.isNotBlank()) Text(m.title, style = MaterialTheme.typography.titleSmall)
                    val body = if (m.isChecklist) m.checklist.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else m.body
                    Text(com.bamnat.core.memo.MemoSearch.excerpt(body, ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
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
    androidx.compose.material3.AlertDialog(
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
                                if (t.check.isNotEmpty()) add("체크 ${t.check.size}개")
                                com.bamnat.core.memo.MemoRepeat.label(t.repeat).takeIf { it.isNotEmpty() }?.let { add(it) }
                                if (t.from != null) add("나눔 판에서 받음")
                            }.joinToString(" · ")
                            if (what.isNotEmpty()) Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(t.id) }) { Icon(Icons.Filled.Delete, "이 틀 지우기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
