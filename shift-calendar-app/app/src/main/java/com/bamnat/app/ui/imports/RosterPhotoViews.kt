package com.bamnat.app.ui.imports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import com.bamnat.app.ui.components.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.imports.RosterScanner
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterHint
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.model.ShiftType
import kotlin.math.roundToInt

/**
 * [READ-05] "어느 줄(조)이 내 것인가요?" (1.25 순번 369): a roster of several lines asks, every time, before anything goes
 * on the calendar; each line shows its first days so a 조 with no name is still told apart.
 */
@Composable
internal fun WhichLine(rows: List<RosterRow>, types: List<ShiftType>, onPick: (Int) -> Unit) {
    val byId = types.associateBy { it.id }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("어느 줄(조)이 내 것인가요?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text("근무표에서 ${rows.size}개 줄을 읽었어요. 고르면 다음 달 근무표에서는 같은 줄을 먼저 골라 둬요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            rows.forEachIndexed { i, r ->
                OutlinedButton(onClick = { onPick(i) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(r.label.ifBlank { "${i + 1}번째 줄" } + " · ${r.cells.size}일", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(preview(r, byId), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The first days of a line as they were written: "1일부터 D D E E N N O". */
internal fun preview(r: RosterRow, byId: Map<Long, ShiftType>): String {
    val first = r.cells.keys.minOrNull() ?: return ""
    return "${first}일부터 " + (first until first + 10).mapNotNull { d -> r.cells[d]?.let { short(it, byId) } }.joinToString(" ")
}

private fun short(c: DutyCell, byId: Map<Long, ShiftType>): String = when (c) {
    is DutyCell.Shift -> byId[c.shiftTypeId]?.label ?: "?"
    DutyCell.Off -> "휴"
    is DutyCell.Leave -> "연"
    is DutyCell.Unknown -> c.raw
}

/**
 * [READ-04] 사진 위에서 누르기 (1.25 순번 369): the photo, and two taps on it. First the row of dates ("날짜 줄이 없어요"
 * when the roster has none: the line is then read from the 1st), then the user's own line. The table is read again along
 * those two lines, whatever the photo's layout.
 */
@Composable
internal fun PhotoTapDialog(scan: RosterScanner.Scan, onDismiss: () -> Unit, onDone: (RosterHint) -> Unit) {
    var dayRow by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var askDays by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (askDays) "1. 날짜 줄(1 2 3 …)을 눌러 주세요" else "2. 내 줄(이름이나 근무 글자)을 눌러 주세요",
                    style = MaterialTheme.typography.titleMedium)
                // 2.0.0 [RVL-35] (검토 SCAN-19·U07b-03) A table of 20 people is tapped zoomed in, as the payslip photo is looked at.
                Text((if (askDays) "날짜가 적힌 줄 아무 곳이나 한 번 누르면 돼요." else "내 이름이나 내 근무가 적힌 줄 아무 곳이나 눌러 주세요.") +
                    " 촘촘하면 두 손가락이나 ⊕로 키워요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RosterPhoto(scan, marks = listOfNotNull(dayRow), lines = emptyList(), modifier = Modifier.weight(1f).fillMaxWidth()) { x, y ->
                    if (askDays) { dayRow = x to y; askDays = false } else onDone(RosterHint(dayRow, x to y))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (askDays) TextButton(onClick = { askDays = false }) { Text("날짜 줄이 없어요") }
                    else TextButton(onClick = { dayRow = null; askDays = true }) { Text("날짜 줄 다시 누르기") }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}

/**
 * [READ-05] 무엇을 읽었는지 보기: the photo with each line it read framed by what it took the line for (날짜·요일·근무·그
 * 밖), the tilt it measured, and the lines in words. When a roster reads wrong, this shows where.
 */
@Composable
internal fun ReadDiagnosisDialog(scan: RosterScanner.Scan, rows: List<RosterRow>, onDismiss: () -> Unit) {
    val read = scan.read
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("무엇을 읽었는지", style = MaterialTheme.typography.titleMedium)
                Text(summary(read, rows), style = MaterialTheme.typography.bodyMedium)
                Text("파란 틀은 날짜 줄, 초록은 근무 줄, 회색은 그 밖의 글이에요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The same zoomed photo as 사진에서 내 줄 알려 주기: a frame on a dense table is seen up close; the lines in words scroll under it.
                if (scan.image != null) RosterPhoto(scan, marks = emptyList(), lines = read.lines, modifier = Modifier.weight(0.6f).fillMaxWidth(), onTap = null)
                Column(Modifier.weight(0.4f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    read.lines.forEach { l ->
                        Text("${kindName(l.kind)} · ${l.text}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("닫기") } }
            }
        }
    }
}

internal fun summary(read: RosterPhotoRead, rows: List<RosterRow>): String = buildString {
    append("글자 줄 ${read.lines.size}개 · 날짜 줄 ${read.lines.count { it.kind == RosterPhotoRead.Kind.DAYS }}개 · ")
    append("근무 줄 ${rows.size}개")
    if (kotlin.math.abs(read.tilt) >= 0.3) append(" · 사진이 ${"%.1f".format(kotlin.math.abs(read.tilt))}° 기울어 바로 세워 읽었어요")
    read.problem?.let { append("\n").append(RosterReader.problem(it)) }
}

private fun kindName(k: RosterPhotoRead.Kind) = when (k) {
    RosterPhotoRead.Kind.DAYS -> "날짜 줄"
    RosterPhotoRead.Kind.WEEKDAYS -> "요일 줄"
    RosterPhotoRead.Kind.DUTIES -> "근무 줄"
    RosterPhotoRead.Kind.OTHER -> "그 밖"
}

/**
 * The photo as wide as [modifier]'s box, [lines] framed on it, [marks] dotted, and a tap given back in the words' pixels.
 * 2.0.0 [RVL-35] (검토 SCAN-19·U07b-03) Zoomed like the payslip photo ([com.bamnat.app.ui.pay.ZoomablePhoto]): two fingers zoom (to 6×),
 * one finger moves it, a double tap zooms in there or back out, and ⊕ ⊖ in the corner do the same without two fingers. A tap is read
 * through the zoom, so a line is picked as easily in a table of twenty as in one of five. A photo taller than the box moves up and
 * down with one finger (it was a scrolled page before).
 */
@Composable
private fun RosterPhoto(
    scan: RosterScanner.Scan, marks: List<Pair<Int, Int>>, lines: List<RosterPhotoRead.Line>, modifier: Modifier = Modifier,
    onTap: ((Int, Int) -> Unit)?,
) {
    val image = scan.image ?: return
    val bitmap = remember(image) { image.asImageBitmap() }
    val days = MaterialTheme.colorScheme.primary
    val duties = Color(0xFF2E7D32)
    val other = Color(0xFF9E9E9E)
    val mark = MaterialTheme.colorScheme.error
    var box by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(scan) { mutableFloatStateOf(1f) }
    var offset by remember(scan) { mutableStateOf(Offset.Zero) }
    fun place(o: Offset, z: Float) = photoPlace(o, z, box, image.width, image.height)
    fun zoomBy(f: Float, at: Offset = Offset(box.width / 2f, box.height / 2f)) {
        val next = (zoom * f).coerceIn(1f, MAX_ZOOM)
        val from = place(offset, zoom)
        offset = place(at - (at - from) * (next / zoom), next)
        zoom = next
    }
    fun move(dx: Float, dy: Float): Boolean { offset = place(place(offset, zoom) + Offset(dx, dy), zoom); return true }
    Box(
        modifier.clipToBounds().onSizeChanged { box = it }
            .semantics {
                contentDescription = if (onTap != null) "근무표 사진, 줄을 눌러 골라요" else "근무표 사진"
                customActions = listOf(
                    CustomAccessibilityAction("크게") { zoomBy(STEP); true },
                    CustomAccessibilityAction("작게") { zoomBy(1 / STEP); true },
                    CustomAccessibilityAction("사진을 왼쪽으로") { move(box.width / 4f, 0f) },
                    CustomAccessibilityAction("사진을 오른쪽으로") { move(-box.width / 4f, 0f) },
                    CustomAccessibilityAction("사진을 위로") { move(0f, box.height / 4f) },
                    CustomAccessibilityAction("사진을 아래로") { move(0f, -box.height / 4f) },
                )
            }
            .pointerInput(scan) {
                detectTransformGestures { centroid, pan, z, _ ->
                    val next = (zoom * z).coerceIn(1f, MAX_ZOOM)
                    val from = place(offset, zoom)
                    offset = place(centroid - (centroid - from) * (next / zoom) + pan, next)
                    zoom = next
                }
            }
            .pointerInput(scan, onTap != null) {
                detectTapGestures(
                    onTap = { p -> onTap?.let { tap -> photoTap(p, place(offset, zoom), zoom, box.width.toFloat() / image.width, scan.scale).let { (x, y) -> tap(x, y) } } },
                    onDoubleTap = { p -> if (zoom > 1.01f) { zoom = 1f; offset = place(Offset.Zero, 1f) } else zoomBy(DOUBLE_TAP, p) },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (size.width <= 0f) return@Canvas
            val fit = size.width / image.width
            val at = place(offset, zoom)
            val s = zoom * fit
            withTransform({ translate(at.x, at.y); scale(s, s, pivot = Offset.Zero) }) {
                drawImage(bitmap, dstSize = IntSize(image.width, image.height), filterQuality = FilterQuality.Medium)
                // The words' pixels → the photo's.
                val k = 1f / scan.scale
                lines.forEach { l ->
                    val color = when (l.kind) {
                        RosterPhotoRead.Kind.DAYS, RosterPhotoRead.Kind.WEEKDAYS -> days
                        RosterPhotoRead.Kind.DUTIES -> duties
                        RosterPhotoRead.Kind.OTHER -> other
                    }
                    drawRect(color, Offset(l.left * k, l.top * k), Size((l.right - l.left) * k, (l.bottom - l.top) * k), style = Stroke(2.dp.toPx() / s))
                }
                marks.forEach { (x, y) -> drawCircle(mark, 8.dp.toPx() / s, Offset(x * k, y * k)) }
            }
        }
        // ⊕ ⊖ for one hand, TalkBack and Switch Access; the zoom in words while zoomed.
        Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (zoom > 1.01f) Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(end = 4.dp).background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
            FilledTonalIconButton(onClick = { zoomBy(1 / STEP) }, enabled = zoom > 1.01f) { Icon(Icons.Filled.ZoomOut, contentDescription = "사진 작게") }
            FilledTonalIconButton(onClick = { zoomBy(STEP) }, enabled = zoom < MAX_ZOOM - 0.01f) { Icon(Icons.Filled.ZoomIn, contentDescription = "사진 크게") }
        }
    }
}

private const val MAX_ZOOM = 6f
private const val STEP = 1.5f
private const val DOUBLE_TAP = 2.5f

/**
 * Where the photo sits in a [box] at [zoom]: as wide as the box at 1×, in the middle when shorter than it, and never moved past its
 * own edges (so the empty box never shows where the photo could be).
 */
internal fun photoPlace(o: Offset, zoom: Float, box: IntSize, width: Int, height: Int): Offset {
    if (box.width == 0 || width <= 0) return o
    val w = box.width * zoom
    val h = box.width.toFloat() * height / width * zoom
    val x = o.x.coerceIn(minOf(0f, box.width - w), 0f)
    val y = if (h <= box.height) (box.height - h) / 2f else o.y.coerceIn(box.height - h, 0f)
    return Offset(x, y)
}

/** A tap at [p] on the photo placed at [at] and [zoom] ([fit] screen pixels a photo pixel at 1×), in the pixels the words were read in. */
internal fun photoTap(p: Offset, at: Offset, zoom: Float, fit: Float, wordsScale: Float): Pair<Int, Int> {
    val k = wordsScale / (zoom * fit)
    return ((p.x - at.x) * k).roundToInt() to ((p.y - at.y) * k).roundToInt()
}
