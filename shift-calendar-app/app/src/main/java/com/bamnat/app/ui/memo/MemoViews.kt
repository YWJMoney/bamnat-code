package com.bamnat.app.ui.memo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.memoBackground
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoProps
import com.bamnat.core.memo.MemoView
import java.time.LocalDate

/**
 * [TAB-02] 1.36 메모장 보기 칩: 목록 · 보드 · 표 · 갤러리, and for a 보드 what makes its 칸 and for every view the order. The 칸 names the
 * memos on screen carry ([MemoProps.columns]) are offered as 묶음 and 정렬, so a 틀 with 매출·상태 칸 can be seen by them at once.
 */
@Composable
internal fun MemoViewChips(view: MemoView, columns: List<Pair<String, MemoProps.Type>>, onChange: (MemoView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (v in MemoView.VIEWS) {
            FilterChip(
                selected = view.view == v,
                onClick = { onChange(view.copy(view = v)) },
                label = { Text(MemoView.LABELS[v]!!) },
                modifier = Modifier.semantics { contentDescription = "${MemoView.LABELS[v]}${Fmt.ro(MemoView.LABELS[v]!!)} 보기" },
            )
        }
        if (view.view == MemoView.BOARD) {
            val byLabel = MemoView.BY_LABELS[view.by] ?: view.by.removePrefix("p:")
            PickChip("묶음: $byLabel", MemoView.BY_FIXED.map { it to MemoView.BY_LABELS[it]!! } + columns.map { "p:${it.first}" to it.first }) {
                onChange(view.copy(by = it))
            }
        }
        val sortLabel = when {
            view.sort.isEmpty() -> "기본 순서"
            view.sort == MemoView.SORT_TITLE -> "제목 순"
            view.sort.startsWith("-") -> view.sort.drop(3) + " 큰 순"
            else -> view.sort.drop(2) + " 작은 순"
        }
        PickChip("정렬: $sortLabel", listOf("" to "기본 순서", MemoView.SORT_TITLE to "제목 순") +
            columns.flatMap { (c, _) -> listOf("p:$c" to "$c 작은 순", "-p:$c" to "$c 큰 순") }) { onChange(view.copy(sort = it)) }
    }
}

@Composable
private fun PickChip(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { open = true }, label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((key, text) in options) DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(key) })
        }
    }
}

/** [TAB-01] A memo's properties as small pills (at most [max]), shown on its card in every view. */
@Composable
internal fun PropPills(memo: Memo, max: Int = 3, modifier: Modifier = Modifier) {
    val props = remember(memo.body, memo.date) { MemoProps.of(memo) }
    if (props.isEmpty()) return
    // [TAB-07] 부품 memo.prop: a blueprint can draw the pills its own way.
    val part = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.prop")
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (p in props.take(max)) {
            Text(
                "${p.label} ${shown(p)}",
                style = com.bamnat.app.ui.parts.PartMods.text(part, MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .then(com.bamnat.app.ui.parts.PartMods.box(part, RoundedCornerShape(6.dp)))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** How a property reads in a pill or a 표 칸: money with commas and 원, a check as ✓/–, the rest as written. */
internal fun shown(p: MemoProps.Prop, column: MemoProps.Type? = null): String = when {
    p.type == MemoProps.Type.MONEY || (column == MemoProps.Type.MONEY && p.type == MemoProps.Type.NUM) || (p.type == MemoProps.Type.NUM && MemoProps.isMoneyLabel(p.label)) ->
        p.num?.let { Fmt.won(it.toLong()) } ?: p.raw
    else -> shownPlain(p)
}

private fun shownPlain(p: MemoProps.Prop): String = when (p.type) {
    MemoProps.Type.CHECK -> if ((p.num ?: 0.0) > 0) "✓" else "–"
    else -> p.raw
}

/**
 * [TAB-02] 보드: one 칸 a 꼬리표 (근무, 할 일 상태, 색, or a property's value), side by side; each scrolls on its own and its head counts
 * the memos and, for a 금액 칸 in the memos, adds them up.
 *
 * 누락 점검 N9 (2차 5-7 "보드에서 카드를 끌어 다른 칸으로"): when the 묶음 is 꼬리표·색·칸 ([MemoView.canMove]) each card has a ⠿ 손잡이. Dragged,
 * the card's title follows the finger, the 칸 under it lights up, and letting go there moves it ([MemoView.moved]: the 칸's value, the
 * #꼬리표 or the colour). Near either edge the board scrolls by itself. Tapped, the same 손잡이 lists the other 칸 to move it to — the way a
 * screen reader or one hand does it. [CardActions.onChange] saves it and offers 되돌리기.
 */
@Composable
internal fun MemoBoardView(
    memos: List<Memo>, view: MemoView, shiftOf: (LocalDate) -> String?, state: MemoListState, actions: CardActions,
    templateChoices: (String) -> List<String> = { emptyList() },
) {
    // PC 확인 dc7f51ae: a 칸 묶음 also shows the values it can take with no memo yet (상태 → 할 일 · 하는 중 · 끝), as empty 칸 to drop into.
    val columns = remember(memos, view, shiftOf, templateChoices) {
        val extra = if (view.by.startsWith("p:")) view.by.drop(2).let { MemoView.knownChoices(it, templateChoices(it)) } else emptyList()
        view.board(memos, shiftOf, extra)
    }
    // Where a card can go: the other 칸, and for 색 every colour (the board shows only the colours in use).
    val targets = remember(columns, view.by) {
        columns.map { it.key to it.label } + if (view.by == MemoView.BY_COLOR)
            com.bamnat.app.ui.theme.MemoPaletteLight.indices.map { it.toString() }.filter { k -> columns.none { it.key == k } }
                .map { it to if (it == "0") "기본 색" else "색 $it" } else emptyList()
    }
    val canAdd = view.by == MemoView.BY_TAG || view.by.startsWith("p:")
    val money = remember(memos) { MemoProps.columns(memos).firstOrNull { it.second == MemoProps.Type.MONEY }?.first }
    val colPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.board.col")
    val headPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.board.head")
    val movable = MemoView.canMove(view.by) && !state.selecting
    // The drag under way (window px). PC 확인 5ff9ae28: the 칸 under the finger is read from the row's layout as it is now, so a board
    // slid along drops into the 칸 that is seen there (it kept old places of 칸 that had left the screen).
    var drag by remember { mutableStateOf<BoardDrag?>(null) }
    var boardOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var boardWidth by remember { mutableStateOf(0f) }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val keyAt: (androidx.compose.ui.geometry.Offset) -> String? = { at ->
        val info = rowState.layoutInfo
        val x = at.x - boardOrigin.x + info.viewportStartOffset
        if (at.y < boardOrigin.y) null else info.visibleItemsInfo.firstOrNull { x >= it.offset && x < it.offset + it.size }?.key as? String
    }
    val target = drag?.let { keyAt(it.at) }
    val move: (Memo, String, String) -> Unit = { memo, from, to ->
        MemoView.moved(memo, view.by, from, to)?.let { after ->
            val label = targets.firstOrNull { it.first == to }?.second ?: if (to == MemoView.NONE) "없음" else if (view.by == MemoView.BY_TAG) "#$to" else to
            actions.onChange(memo, after, "‘$label’ 칸으로 옮겼어요")
        }
    }
    // Held near an edge, the board turns one 칸 on, then another while the finger stays (PC 확인 5ff9ae28 사용성 제안).
    androidx.compose.runtime.LaunchedEffect(drag != null) {
        while (drag != null) {
            val x = (drag?.at?.x ?: break) - boardOrigin.x
            val step = when {
                x > boardWidth - edge -> 1
                x < edge -> -1
                else -> 0
            }
            if (step != 0) {
                val to = (rowState.firstVisibleItemIndex + step).coerceIn(0, (columns.size - 1).coerceAtLeast(0))
                rowState.animateScrollToItem(to)
                kotlinx.coroutines.delay(550)
            } else kotlinx.coroutines.delay(32)
        }
    }
    val dropNow by androidx.compose.runtime.rememberUpdatedState { d: BoardDrag -> keyAt(d.at)?.let { to -> move(d.memo, d.from, to) } }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { boardOrigin = it.positionInWindow(); boardWidth = it.size.width.toFloat() }
            // Once a 손잡이 starts a drag the board follows the finger itself, so the drag lives on when the card's 칸 slides off screen.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val d = drag ?: continue
                        val ch = e.changes.firstOrNull() ?: continue
                        if (ch.pressed) {
                            drag = d.copy(at = boardOrigin + ch.position)
                            ch.consume()
                        } else {
                            drag = null
                            dropNow(d)
                        }
                    }
                }
            },
    ) {
        LazyRow(
            Modifier.fillMaxSize().then(com.bamnat.app.ui.parts.PartMods.box(com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.board"), RoundedCornerShape(0.dp))),
            state = rowState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(columns, key = { it.key }) { col ->
                val lit = drag != null && target == col.key && drag?.from != col.key
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (lit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (lit) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    // PC 확인 9aaf3eef 사용성 제안: an empty 칸 stays narrow and opens wide only while a card is dragged.
                    modifier = Modifier.width(if (col.memos.isEmpty() && drag == null) 132.dp else 250.dp).animateItem().fillParentMaxHeight()
                        .then(com.bamnat.app.ui.parts.PartMods.box(colPart, RoundedCornerShape(14.dp))),
                ) {
                    Column {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(col.label, style = com.bamnat.app.ui.parts.PartMods.text(headPart, MaterialTheme.typography.titleSmall), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).semantics { heading() })
                            Text("${col.memos.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        money?.let { m -> MemoProps.total(col.memos, m)?.let { t ->
                            Text("$m 합계 ${Fmt.won(t.toLong())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
                        } }
                        LazyColumn(
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(col.memos, key = { "${col.key}-${it.id}" }) { memo ->
                                Box(Modifier.then(if (drag?.memo?.id == memo.id && drag?.from == col.key) Modifier.alpha(0.35f) else Modifier)) {
                                    MemoCard(memo, state.query, selected = memo.id in state.selection, selecting = state.selecting, showDate = true, actions = actions)
                                    if (movable) MoveHandle(
                                        others = targets.filter { it.first != col.key },
                                        newLabel = if (!canAdd) null else if (view.by == MemoView.BY_TAG) "새 꼬리표" else "새 ${view.by.drop(2)} 값",
                                        onPick = { to -> move(memo, col.key, to) },
                                        onDragStart = { at -> drag = BoardDrag(memo, col.key, at) },
                                        // The board takes the drag from here on (above) and drops it where the finger lifts.
                                        onDrag = {},
                                        onDragEnd = {},
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // The card being dragged, as its title under the finger.
        drag?.let { d ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((d.at.x - boardOrigin.x - 60.dp.toPx()).toInt(), (d.at.y - boardOrigin.y - 24.dp.toPx()).toInt()) }
                    .width(180.dp),
            ) {
                Text(d.memo.title.ifBlank { d.memo.preview }.ifBlank { "(빈 메모)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

private data class BoardDrag(val memo: Memo, val from: String, val at: androidx.compose.ui.geometry.Offset)

/** The ⠿ on a board card: drag it to another 칸, or tap it for the list of 칸. Positions are in window px. */
@Composable
private fun MoveHandle(
    others: List<Pair<String, String>>,
    newLabel: String?,
    onPick: (String) -> Unit,
    onDragStart: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDrag: (androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var at by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val latestStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val latestDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val latestEnd by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    Box(modifier) {
        Box(
            Modifier.size(44.dp)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> at = origin + o; latestStart(at) },
                        onDrag = { change, amount -> change.consume(); at += amount; latestDrag(at) },
                        onDragEnd = { latestEnd() },
                        onDragCancel = { latestEnd() },
                    )
                }
                .clickable(onClickLabel = "다른 칸으로 옮기기") { open = true }
                .semantics { contentDescription = "다른 칸으로 옮기기. 끌어서 놓거나 눌러서 고르기" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DragIndicator, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("옮길 칸", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            for ((key, label) in others) DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(key) })
            // PC 확인 dc7f51ae: with one 칸 the list was empty; a new 칸 is always one choice away.
            if (newLabel != null) DropdownMenuItem(text = { Text("$newLabel 만들기…") }, onClick = { open = false; adding = true })
            if (others.isEmpty() && newLabel == null) Text("옮길 다른 칸이 없어요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    if (adding && newLabel != null) {
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(newLabel) },
            text = {
                androidx.compose.material3.OutlinedTextField(text, { text = it.take(20).replace("\n", " ").replace("#", "") }, singleLine = true,
                    placeholder = { Text(if (newLabel == "새 꼬리표") "예: 가게" else "예: 할 일") })
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { adding = false; onPick(text.trim().replace(' ', '_').takeIf { newLabel == "새 꼬리표" } ?: text.trim()) },
                enabled = text.isNotBlank()) { Text("옮기기") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { adding = false }) { Text("닫기") } },
        )
    }
}

/**
 * [TAB-02] 표: 제목 and the memos' 칸 as columns (날짜 and 꼬리표 too), a head tap orders by that column (again: the other way), and a
 * 합계 row under 숫자·금액 columns. The 제목 column stays while the rest slide sideways together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoTableView(memos: List<Memo>, view: MemoView, onSort: (String) -> Unit, actions: CardActions, templateChoices: (String) -> List<String> = { emptyList() }) {
    val columns = remember(memos) { MemoProps.columns(memos) }
    val rows = remember(memos, view) { view.sorted(memos).map { it to MemoProps.of(it).associateBy { p -> p.label } } }
    // 누락 점검 N9: a 고르기 칸's words, offered when its 칸 is tapped.
    val choices = remember(memos, columns) { columns.filter { it.second == MemoProps.Type.PICK }.associate { (c, _) -> c to MemoView.choicesFor(c, memos, templateChoices(c)) } }
    val sideways = rememberScrollState()
    val titleW = 132.dp
    val colW = 104.dp
    // PC 확인 bad7d860: "11월 30일" was cut; the date column takes the room it needs.
    val dateW = 80.dp
    val line = MaterialTheme.colorScheme.outlineVariant
    val rowPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.table.row")
    @Composable fun headCell(text: String, key: String, width: androidx.compose.ui.unit.Dp) {
        val on = view.sort.removePrefix("-") == key
        Row(
            Modifier.width(width).clickable { onSort(if (view.sort == key) "-$key" else key) }.padding(horizontal = 8.dp, vertical = 10.dp)
                .semantics { contentDescription = "$text 열, 눌러 정렬"; selected = on },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (on) Icon(if (view.sort.startsWith("-")) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(14.dp))
        }
    }
    // [TAB-07] 부품 memo.table · memo.table.head.
    val headRowPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.table.head")
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).then(com.bamnat.app.ui.parts.PartMods.box(com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.table"), RoundedCornerShape(10.dp)))) {
        Row(Modifier.border(1.dp, line, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .then(com.bamnat.app.ui.parts.PartMods.box(headRowPart, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))) {
            headCell("제목", MemoView.SORT_TITLE, titleW)
            Row(Modifier.horizontalScroll(sideways)) {
                for ((c, _) in columns) headCell(c, "p:$c", colW)
                Text("날짜", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(dateW).padding(8.dp, 10.dp))
                Text("꼬리표", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(colW).padding(8.dp, 10.dp))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 160.dp)) {
            items(rows, key = { it.first.id }) { (memo, props) ->
                Row(
                    Modifier.heightIn(min = 44.dp).background(memoBackground(memo.colorIndex).copy(alpha = 0.35f))
                        .then(com.bamnat.app.ui.parts.PartMods.box(rowPart, RoundedCornerShape(0.dp)))
                        .combinedClickable(onClick = { actions.onOpen(memo) }, onLongClick = { actions.onSelect(memo) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(memo.title.ifBlank { memo.preview }.ifBlank { "(빈 메모)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.width(titleW).padding(horizontal = 8.dp, vertical = 6.dp))
                    Row(Modifier.horizontalScroll(sideways)) {
                        for ((c, t) in columns) {
                            if (t == MemoProps.Type.PICK || t == MemoProps.Type.STAR) PickCell(memo, c, t, props[c], choices[c].orEmpty(), colW, actions)
                            else EditCell(memo, c, t, props[c], colW, actions)
                        }
                        // PC 확인 bf62e966: still cut at 120dp with big text, so the 표 writes a short "11/12".
                        Text(memo.date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                            modifier = Modifier.width(dateW).padding(horizontal = 8.dp))
                        Text(memo.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(colW).padding(horizontal = 8.dp))
                    }
                }
                HorizontalDivider(color = line.copy(alpha = 0.5f))
            }
            val sums = columns.filter { it.second == MemoProps.Type.MONEY || it.second == MemoProps.Type.NUM || it.second == MemoProps.Type.STAR }
            if (sums.isNotEmpty()) item(key = "sum") {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sums.all { it.second == MemoProps.Type.STAR }) "평균" else "합계 · 평균", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(titleW).padding(horizontal = 8.dp))
                    Row(Modifier.horizontalScroll(sideways)) {
                        for ((c, t) in columns) {
                            // PC 확인 bad7d860 사용성 제안: 별점은 합계가 아니라 평균 (★ 4.2).
                            val foot = when (t) {
                                MemoProps.Type.MONEY -> MemoProps.total(memos, c)?.let { Fmt.won(it.toLong()) }
                                MemoProps.Type.NUM -> MemoProps.total(memos, c)?.let(::trim)
                                MemoProps.Type.STAR -> MemoProps.average(memos, c)?.let { "★ " + "%.1f".format(it) }
                                else -> null
                            }
                            Text(foot ?: "",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(colW).padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
        if (columns.isEmpty()) Text(
            "메모에 ‘매출: 32000’처럼 ‘칸: 값’ 줄을 쓰면 여기 열이 생겨요. 메모 틀의 입력 칸도 이렇게 적혀요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * [TAB-01] 누락 점검 N9 고르기: a 표 칸 of a 고르기 칸 (상태: 할 일 · 하는 중 · 끝) or a 별점, tapped to pick its value in place — the words
 * already used, 직접 쓰기 for a new one, or 비우기 — without opening the memo. Saved through [CardActions.onChange] with 되돌리기.
 */
@Composable
private fun PickCell(memo: Memo, label: String, type: MemoProps.Type, prop: MemoProps.Prop?, choices: List<String>, width: androidx.compose.ui.unit.Dp, actions: CardActions) {
    var open by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    val set: (String) -> Unit = { v ->
        val after = MemoProps.set(memo, label, v)
        if (after != memo) actions.onChange(memo, after, if (v.isBlank()) "‘$label’ 칸을 비웠어요" else "‘$label’${Fmt.obj(label)} ‘$v’${Fmt.ro(v)} 바꿨어요")
    }
    Box(Modifier.width(width)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = "$label 고르기") { open = true }.padding(horizontal = 8.dp)
                .semantics { contentDescription = "$label ${prop?.raw ?: "비어 있음"}, 눌러서 고르기" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prop?.let { shown(it, type) } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = if (type == MemoProps.Type.STAR) (5 downTo 1).map { "★".repeat(it) + "☆".repeat(5 - it) } else choices
            for (o in options) DropdownMenuItem(
                text = { Text(o, fontWeight = if (o == prop?.raw) FontWeight.Bold else null) },
                onClick = { open = false; set(o) },
            )
            if (type == MemoProps.Type.PICK) DropdownMenuItem(text = { Text("직접 쓰기…") }, onClick = { open = false; writing = true })
            if (prop != null) DropdownMenuItem(text = { Text("비우기", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; set("") })
        }
    }
    if (writing) {
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("$label 새 값") },
            text = {
                androidx.compose.material3.OutlinedTextField(text, { text = it.take(20).replace("\n", " ") }, singleLine = true,
                    placeholder = { Text(choices.firstOrNull()?.let { "예: $it" } ?: "예: 하는 중") })
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { writing = false; set(text) }, enabled = text.isNotBlank()) { Text("넣기") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { writing = false }) { Text("닫기") } },
        )
    }
}

/**
 * [TAB-01] PC 확인 5ff9ae28 사용성 제안 "표 금액 칸을 누르면 메모가 열려 그 자리에서 못 바꿔요": a 표 칸 of 금액·숫자·글·날짜·시각 opens a
 * small box to change it in place (a number pad for 금액·숫자), a 체크 칸 flips ✓/☐. Saved through [CardActions.onChange] with 되돌리기.
 */
@Composable
private fun EditCell(memo: Memo, label: String, type: MemoProps.Type, prop: MemoProps.Prop?, width: androidx.compose.ui.unit.Dp, actions: CardActions) {
    var editing by remember { mutableStateOf(false) }
    val set: (String) -> Unit = { v ->
        val after = MemoProps.set(memo, label, v)
        // PC 확인 9aaf3eef: the 띠 says the value as the 표 shows it ("‘20,000원’으로"), not as typed.
        val said = if (v.isBlank()) "" else shown(MemoProps.read(label, v), type)
        if (after != memo) actions.onChange(memo, after, if (v.isBlank()) "‘$label’ 칸을 비웠어요" else "‘$label’${Fmt.obj(label)} ‘$said’${Fmt.ro(said)} 바꿨어요")
    }
    val check = type == MemoProps.Type.CHECK
    Text(
        prop?.let { shown(it, type) } ?: "",
        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).heightIn(min = 44.dp)
            .clickable(onClickLabel = if (check) "$label 바꾸기" else "$label 고치기") {
                if (check) set(if ((prop?.num ?: 0.0) > 0) "☐" else "✓") else editing = true
            }
            .semantics { contentDescription = "$label ${prop?.let { shown(it, type) } ?: "비어 있음"}, 눌러서 ${if (check) "바꾸기" else "고치기"}" }
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
    if (editing) {
        val numeric = type == MemoProps.Type.MONEY || type == MemoProps.Type.NUM
        var text by remember { mutableStateOf(if (numeric) prop?.num?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "" else prop?.raw.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                androidx.compose.material3.OutlinedTextField(text, { text = it.take(40).replace("\n", " ") }, singleLine = true,
                    suffix = if (type == MemoProps.Type.MONEY) ({ Text("원") }) else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
                    supportingText = { Text("비우면 이 메모에서 ‘$label’ 줄을 지워요") })
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    editing = false
                    val v = text.trim()
                    // A 금액 written as a bare number stays 금액 under a name that does not say money.
                    set(if (type == MemoProps.Type.MONEY && v.isNotEmpty() && v.all { it.isDigit() || it == ',' } && !MemoProps.isMoneyLabel(label)) v + "원" else v)
                }) { Text("저장") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text("닫기") } },
        )
    }
}

/**
 * [TAB-02] 누락 점검 N9 메모 '달력' 보기 (2차 5-7): the memos on screen on a month — each by its date or its first 날짜 칸 ([MemoView.dayOf]) —
 * with ‹ › for the month. A day shows its first titles; tapping it lists that day's memos under the month to open. Memos with no date
 * are counted under it, so none seems lost. Parts memo.cal · memo.cal.cell.
 */
@Composable
internal fun MemoCalendarView(memos: List<Memo>, weekStart: java.time.DayOfWeek, today: LocalDate, actions: CardActions) {
    val byDay = remember(memos) { MemoView.byDay(memos) }
    val undated = remember(memos, byDay) { memos.size - byDay.values.sumOf { it.size } }
    var month by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(java.time.YearMonth.from(today).toString()) }
    val ym = java.time.YearMonth.parse(month)
    var picked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val first = ym.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(first, ym.atEndOfMonth()) / 7) + 1).toInt()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 160.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { month = ym.minusMonths(1).toString(); picked = null }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                }
                Text("${ym.year}년 ${ym.monthValue}월", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() })
                androidx.compose.material3.IconButton(onClick = { month = ym.plusMonths(1).toString(); picked = null }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                }
            }
        }
        item(key = "grid") {
            Column(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("memo.cal"), RoundedCornerShape(10.dp))),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dow = weekStart.plus(i.toLong())
                        Text(Fmt.dayOfWeekShort(dow), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (dow == java.time.DayOfWeek.SUNDAY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                    }
                }
                for (w in 0 until weeks) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (d in 0..6) {
                        val date = first.plusDays(w * 7L + d)
                        val inMonth = java.time.YearMonth.from(date) == ym
                        val list = byDay[date].orEmpty()
                        val isPicked = picked == date.toString()
                        val states = setOfNotNull("today".takeIf { date == today }, "sel".takeIf { isPicked })
                        Column(
                            Modifier.weight(1f).heightIn(min = 64.dp)
                                .background(if (isPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                                .then(if (date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("memo.cal.cell", states), RoundedCornerShape(8.dp)))
                                .clickable { picked = if (isPicked) null else date.toString() }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = Fmt.monthDay(date) + if (list.isEmpty()) " 메모 없음" else " 메모 ${list.size}개: " + list.joinToString(", ") { it.title.ifBlank { it.preview } }
                                    selected = isPicked
                                }
                                .padding(3.dp),
                        ) {
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (date == today) FontWeight.Bold else null,
                                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            for (m in list.take(2)) Text(m.title.ifBlank { m.preview }.ifBlank { "(빈 메모)" }, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().background(memoBackground(m.colorIndex).copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 2.dp))
                            if (list.size > 2) Text("+${list.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        val day = picked?.let(LocalDate::parse)
        if (day != null) {
            val list = byDay[day].orEmpty()
            item(key = "day") {
                Text(Fmt.monthDay(day) + if (list.isEmpty()) " · 메모 없음" else " · 메모 ${list.size}개", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() })
            }
            items(list, key = { "d-${it.id}" }) { m ->
                Box(Modifier.padding(vertical = 4.dp)) { MemoCard(m, "", selected = false, selecting = false, showDate = false, actions = actions) }
            }
        }
        item(key = "note") {
            Text(
                (if (undated > 0) "날짜가 없는 메모 ${undated}개는 목록·보드에서 보여요. " else "") +
                    "메모에 날짜를 정하거나 ‘마감일: 11월 5일’처럼 날짜 칸을 쓰면 달력에 놓여요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

private fun trim(d: Double): String = if (d == Math.floor(d)) "%,d".format(d.toLong()) else "%,.2f".format(d)

/** [TAB-02] 갤러리: two big cards a row, the title large and the 칸 under it, for 레시피·여행·독서 같은 모음. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoGalleryView(memos: List<Memo>, view: MemoView, actions: CardActions, selection: Set<Long>) {
    val ordered = remember(memos, view) { view.sorted(memos) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(ordered, key = { it.id }) { memo ->
            val title = memo.title.ifBlank { memo.preview }.ifBlank { "(빈 메모)" }
            val lead = title.codePointAt(0).let { String(Character.toChars(it)) }
            val emoji = lead.firstOrNull()?.let { Character.getType(it) == Character.SURROGATE.toInt() || Character.getType(it) == Character.OTHER_SYMBOL.toInt() } == true
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = memoBackground(memo.colorIndex),
                border = if (memo.id in selection) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).combinedClickable(onClick = { actions.onOpen(memo) }, onLongClick = { actions.onSelect(memo) }),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 54.dp), contentAlignment = Alignment.CenterStart) {
                        Text(if (emoji) lead else title.take(1), style = MaterialTheme.typography.displaySmall)
                    }
                    Text(if (emoji) title.drop(lead.length).trim() else title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PropPills(memo, max = 4)
                    memo.date?.let { Text(Fmt.monthDay(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
