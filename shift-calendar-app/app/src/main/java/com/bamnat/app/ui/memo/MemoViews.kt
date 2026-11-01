package com.bamnat.app.ui.memo

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
                modifier = Modifier.semantics { contentDescription = "${MemoView.LABELS[v]}로 보기" },
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
 */
@Composable
internal fun MemoBoardView(memos: List<Memo>, view: MemoView, shiftOf: (LocalDate) -> String?, state: MemoListState, actions: CardActions) {
    val columns = remember(memos, view, shiftOf) { view.board(memos, shiftOf) }
    val money = remember(memos) { MemoProps.columns(memos).firstOrNull { it.second == MemoProps.Type.MONEY }?.first }
    val colPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.board.col")
    val headPart = com.bamnat.app.ui.parts.LocalBlueprint.current.style("memo.board.head")
    LazyRow(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(columns, key = { it.key }) { col ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(250.dp).fillParentMaxHeight().then(com.bamnat.app.ui.parts.PartMods.box(colPart, RoundedCornerShape(14.dp))),
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
                            MemoCard(memo, state.query, selected = memo.id in state.selection, selecting = state.selecting, showDate = true, actions = actions)
                        }
                    }
                }
            }
        }
    }
}

/**
 * [TAB-02] 표: 제목 and the memos' 칸 as columns (날짜 and 꼬리표 too), a head tap orders by that column (again: the other way), and a
 * 합계 row under 숫자·금액 columns. The 제목 column stays while the rest slide sideways together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoTableView(memos: List<Memo>, view: MemoView, onSort: (String) -> Unit, actions: CardActions) {
    val columns = remember(memos) { MemoProps.columns(memos) }
    val rows = remember(memos, view) { view.sorted(memos).map { it to MemoProps.of(it).associateBy { p -> p.label } } }
    val sideways = rememberScrollState()
    val titleW = 132.dp
    val colW = 104.dp
    // PC 확인 bad7d860: "11월 30일" was cut; the date column takes the room it needs.
    val dateW = 120.dp
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
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.border(1.dp, line, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))) {
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
                        for ((c, t) in columns) Text(props[c]?.let { shown(it, t) } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.width(colW).padding(horizontal = 8.dp))
                        Text(memo.date?.let { Fmt.monthDay(it) } ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
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
