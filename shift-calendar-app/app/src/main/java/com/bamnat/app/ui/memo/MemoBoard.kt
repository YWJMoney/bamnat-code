package com.bamnat.app.ui.memo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.core.memo.MemoPhotos
import com.bamnat.core.memo.MemoPhotos.Board
import com.bamnat.core.memo.MemoPhotos.BoardItem
import com.bamnat.core.memo.MemoPhotos.BoardKind
import kotlin.math.roundToInt

/**
 * [MPH-07] 1.38 자유 판 (사용자 2026-11-05 "파워포인트나 한글처럼 자유자재의 메모"): a board inside the memo where photos, text boxes and
 * stickers go anywhere. Tap the board to work on it; tap a piece to choose it, then one finger moves it and two fingers make it bigger or
 * smaller and turn it. The row under the board adds pieces and changes the chosen one (글 고치기·글자 크기·색·앞으로·뒤로·복제·지우기) and
 * the board (크게·작게·위로·아래로·빼기). 완료 lets it go. Every place is kept in ‰ of the board, so any phone draws it the same.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoBoardBlock(
    board: Board,
    chosen: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onChoose: () -> Unit,
    onChange: (Board) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onAddPhoto: () -> Unit,
    textStyle: TextStyle,
) {
    var picked by remember(chosen) { mutableStateOf<Int?>(null) }
    // While a finger moves a piece it is drawn from here; it is written to the memo when the finger lets go (one step to undo).
    var live by remember(board) { mutableStateOf(board) }
    val latest by rememberUpdatedState(board)
    var editing by remember { mutableStateOf<Int?>(null) }
    var stickers by remember { mutableStateOf(false) }
    fun put(b: Board) { live = b; onChange(b) }
    fun item(i: Int) = live.items.getOrNull(i)
    fun change(i: Int, f: (BoardItem) -> BoardItem) {
        val items = live.items.toMutableList(); if (i !in items.indices) return
        items[i] = f(items[i]).fixed(); put(live.copy(items = items))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(100f / live.ratio)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp),
                )
                .clipToBounds()
                .pointerInput(chosen) { detectTapGestures { if (!chosen) onChoose() else picked = null } }
                .semantics { contentDescription = "자유 판, 조각 ${live.items.size}개" + if (chosen) "" else ". 누르면 고쳐요" },
        ) {
            val wPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
            val hPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
            if (live.items.isEmpty()) Text(
                if (chosen) "아래에서 글상자·사진·스티커를 더해 보세요" else "빈 자유 판 · 눌러서 꾸미기",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
            live.items.forEachIndexed { i, it ->
                BoardPiece(
                    item = it, boardWidth = maxWidth, wPx = wPx, hPx = hPx,
                    selected = chosen && picked == i, editable = chosen, textStyle = textStyle,
                    onTap = { if (chosen) picked = i else onChoose() },
                    onDouble = { if (it.kind == BoardKind.TEXT) editing = i },
                    onLive = { moved -> live = live.copy(items = live.items.toMutableList().also { l -> if (i in l.indices) l[i] = moved.fixed() }) },
                    onDone = { if (live != latest) onChange(live) },
                )
            }
        }
        if (chosen) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                val full = live.items.size >= MemoPhotos.MAX_BOARD_ITEMS
                TextButton(enabled = !full, onClick = {
                    put(live.copy(items = live.items + BoardItem(BoardKind.TEXT, "글", 500, 500, 400)))
                    picked = live.items.lastIndex; editing = live.items.lastIndex
                }) { Text("+ 글상자") }
                TextButton(enabled = !full, onClick = onAddPhoto) { Text("+ 사진") }
                TextButton(enabled = !full, onClick = { stickers = true }) { Text("+ 스티커") }
                val p = picked?.let { i -> item(i)?.let { i to it } }
                if (p != null) {
                    val (i, it) = p
                    if (it.kind == BoardKind.TEXT) {
                        TextButton(onClick = { editing = i }) { Text("글 고치기") }
                        TextButton(onClick = { change(i) { x -> x.copy(size = x.size + 2) } }) { Text("가+") }
                        TextButton(onClick = { change(i) { x -> x.copy(size = x.size - 2) } }) { Text("가−") }
                        TextButton(onClick = { change(i) { x -> x.copy(ink = nextInk(x.ink)) } }) { Text("글자 색") }
                        TextButton(onClick = { change(i) { x -> x.copy(fill = nextFill(x.fill)) } }) { Text("바탕 색") }
                    }
                    TextButton(onClick = { change(i) { x -> x.copy(w = x.w + 80) } }) { Text("크게") }
                    TextButton(onClick = { change(i) { x -> x.copy(w = x.w - 80) } }) { Text("작게") }
                    TextButton(onClick = { change(i) { x -> x.copy(rot = x.rot + 15) } }) { Text("↻ 돌리기") }
                    // 1.38 통합(GestureAlternativesTest): 끌어 옮기기 대신 버튼으로도 한 칸(5%)씩.
                    TextButton(onClick = { change(i) { x -> x.copy(x = x.x - 50) } }) { Text("← 왼쪽") }
                    TextButton(onClick = { change(i) { x -> x.copy(x = x.x + 50) } }) { Text("오른쪽 →") }
                    TextButton(onClick = { change(i) { x -> x.copy(y = x.y - 50) } }) { Text("↑ 올리기") }
                    TextButton(onClick = { change(i) { x -> x.copy(y = x.y + 50) } }) { Text("↓ 내리기") }
                    TextButton(enabled = i < live.items.lastIndex, onClick = {
                        val l = live.items.toMutableList(); val x = l.removeAt(i); l.add(i + 1, x); put(live.copy(items = l)); picked = i + 1
                    }) { Text("앞으로") }
                    TextButton(enabled = i > 0, onClick = {
                        val l = live.items.toMutableList(); val x = l.removeAt(i); l.add(i - 1, x); put(live.copy(items = l)); picked = i - 1
                    }) { Text("뒤로") }
                    TextButton(enabled = !full, onClick = {
                        put(live.copy(items = live.items + it.copy(x = it.x + 40, y = it.y + 40).fixed())); picked = live.items.lastIndex
                    }) { Text("복제") }
                    TextButton(onClick = { put(live.copy(items = live.items.filterIndexed { j, _ -> j != i })); picked = null }) {
                        Text("조각 지우기", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { put(live.copy(ratio = live.ratio + 25)) }) { Text("판 길게") }
                    TextButton(onClick = { put(live.copy(ratio = (live.ratio - 25).coerceAtLeast(MemoPhotos.MIN_RATIO))) }) { Text("판 짧게") }
                    TextButton(enabled = canUp, onClick = { onMove(-1) }) { Text("↑ 위로") }
                    TextButton(enabled = canDown, onClick = { onMove(1) }) { Text("↓ 아래로") }
                    TextButton(onClick = onRemove) { Text("판 빼기", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onChoose) { Text("완료") }
            }
        }
        if (chosen) Text(
            if (picked == null) "조각을 누르면 고를 수 있어요. 고른 조각은 한 손가락으로 옮기고, 두 손가락으로 키우고 돌려요"
            else "한 손가락으로 옮기고, 두 손가락으로 벌리면 커지고 돌리면 돌아가요. 글상자는 두 번 누르면 글을 고쳐요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
    editing?.let { i ->
        val it = item(i)
        if (it == null) editing = null else TextBoxDialog(it.text, onDone = { t -> change(i) { x -> x.copy(text = t) }; editing = null }, onDismiss = { editing = null })
    }
    if (stickers) StickerDialog(onPick = { e ->
        stickers = false
        put(live.copy(items = live.items + BoardItem(BoardKind.STICKER, e, 500, 500, 160)))
        picked = live.items.lastIndex
    }, onDismiss = { stickers = false })
}

@Composable
private fun BoardPiece(
    item: BoardItem,
    boardWidth: androidx.compose.ui.unit.Dp,
    wPx: Float,
    hPx: Float,
    selected: Boolean,
    editable: Boolean,
    textStyle: TextStyle,
    onTap: () -> Unit,
    onDouble: () -> Unit,
    onLive: (BoardItem) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = boardWidth * (item.w / 1000f)
    val latest by rememberUpdatedState(item)
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        Modifier
            .offset {
                IntOffset((item.x / 1000f * wPx - size.width / 2f).roundToInt(), (item.y / 1000f * hPx - size.height / 2f).roundToInt())
            }
            .width(w)
            .wrapContentSize()
            .onSizeChanged { size = it }
            .graphicsLayer { rotationZ = item.rot.toFloat() }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
            .pointerInput(editable, selected) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = if (editable) { _ -> onDouble() } else null)
            }
            .then(if (!selected) Modifier else Modifier.pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, turn ->
                    val c = latest
                    onLive(c.copy(
                        x = c.x + (pan.x / wPx * 1000).roundToInt(),
                        y = c.y + (pan.y / hPx * 1000).roundToInt(),
                        w = (c.w * zoom).roundToInt(),
                        rot = c.rot + turn.roundToInt(),
                    ))
                }
            }.pointerInput(Unit) {
                awaitEachGestureEnd { onDone() }
            }),
    ) {
        when (item.kind) {
            BoardKind.PHOTO -> {
                val px = with(density) { w.toPx() }.toInt()
                val ratio by produceState<Float?>(null, item.text) { value = MemoPhotoFiles.ratio(context, item.text) }
                val img by produceState<ImageBitmap?>(null, item.text, px / 200) { value = MemoPhotoFiles.load(context, item.text, px) }
                Box(Modifier.width(w).aspectRatio(ratio ?: (4f / 3f)).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
            BoardKind.STICKER -> Text(item.text, fontSize = with(density) { (w.toPx() * 0.8f).toSp() }, modifier = Modifier.width(w),
                style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            BoardKind.TEXT -> Text(
                item.text.ifEmpty { " " },
                style = textStyle.copy(fontSize = item.size.sp, color = item.ink?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .width(w)
                    .then(item.fill?.let { Modifier.background(Color(it.toInt()), RoundedCornerShape(6.dp)) } ?: Modifier)
                    .padding(6.dp),
            )
        }
    }
}

/** Calls [onEnd] each time every finger has left (the end of a move or a pinch). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitEachGestureEnd(onEnd: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
            if (e.changes.all { !it.pressed } && e.changes.any { it.previousPressed }) onEnd()
        }
    }
}

private val INKS = listOf(null, 0xFF212121L, 0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L, 0xFFFB8C00L, 0xFF8E24AAL, 0xFFFFFFFFL)
private val FILLS = listOf(null, 0xFFFFF59DL, 0xFFFFCDD2L, 0xFFBBDEFBL, 0xFFC8E6C9L, 0xFFFFFFFFL, 0xCC000000L)

private fun nextInk(c: Long?) = INKS[(INKS.indexOf(c) + 1).mod(INKS.size)]
private fun nextFill(c: Long?) = FILLS[(FILLS.indexOf(c) + 1).mod(FILLS.size)]

@Composable
private fun TextBoxDialog(text: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("글상자") },
        text = {
            OutlinedTextField(t, { t = it.take(MemoPhotos.MAX_ITEM_TEXT) }, modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("글") })
        },
        confirmButton = { TextButton(onClick = { onDone(t) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val STICKERS = listOf(
    "❤️", "⭐", "✨", "🌙", "☀️", "🌈", "☁️", "🌸", "🌷", "🍀", "🌊", "🔥", "🎉", "🎂", "🎁", "📌", "📎", "✅", "❗", "❓",
    "😀", "😊", "😍", "😴", "😭", "😡", "👍", "👏", "🙏", "💪", "☕", "🍱", "🍜", "🍰", "🍺", "🚗", "✈️", "🏠", "💼", "💰",
    "📷", "🎵", "📚", "✏️", "💊", "🏥", "🐶", "🐱", "👶", "💤",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("스티커") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                STICKERS.forEach { e ->
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 26.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
