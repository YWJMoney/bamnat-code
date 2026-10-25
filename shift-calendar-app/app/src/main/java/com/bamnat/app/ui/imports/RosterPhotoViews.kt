package com.bamnat.app.ui.imports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
                Text(if (askDays) "날짜가 적힌 줄 아무 곳이나 한 번 누르면 돼요." else "내 이름이나 내 근무가 적힌 줄 아무 곳이나 눌러 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    RosterPhoto(scan, marks = listOfNotNull(dayRow), lines = emptyList()) { x, y ->
                        if (askDays) { dayRow = x to y; askDays = false } else onDone(RosterHint(dayRow, x to y))
                    }
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
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary(read, rows), style = MaterialTheme.typography.bodyMedium)
                    Text("파란 틀은 날짜 줄, 초록은 근무 줄, 회색은 그 밖의 글이에요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (scan.image != null) RosterPhoto(scan, marks = emptyList(), lines = read.lines, onTap = null)
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

/** The photo as wide as the screen, [lines] framed on it, [marks] dotted, and a tap given back in the words' pixels. */
@Composable
private fun RosterPhoto(scan: RosterScanner.Scan, marks: List<Pair<Int, Int>>, lines: List<RosterPhotoRead.Line>, onTap: ((Int, Int) -> Unit)?) {
    val image = scan.image ?: return
    val bitmap = remember(image) { image.asImageBitmap() }
    val days = MaterialTheme.colorScheme.primary
    val duties = Color(0xFF2E7D32)
    val other = Color(0xFF9E9E9E)
    val mark = MaterialTheme.colorScheme.error
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
            .semantics { contentDescription = "근무표 사진" }
            .then(if (onTap != null) Modifier.pointerInput(scan) {
                detectTapGestures { p ->
                    // Screen → the screen copy → the pixels the words were read in.
                    val k = image.width / size.width.toFloat() * scan.scale
                    onTap((p.x * k).roundToInt(), (p.y * k).roundToInt())
                }
            } else Modifier),
    ) {
        drawImage(bitmap, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val k = size.width / (image.width * scan.scale)
        lines.forEach { l ->
            val color = when (l.kind) {
                RosterPhotoRead.Kind.DAYS, RosterPhotoRead.Kind.WEEKDAYS -> days
                RosterPhotoRead.Kind.DUTIES -> duties
                RosterPhotoRead.Kind.OTHER -> other
            }
            drawRect(color, Offset(l.left * k, l.top * k), Size((l.right - l.left) * k, (l.bottom - l.top) * k), style = Stroke(2.dp.toPx()))
        }
        marks.forEach { (x, y) -> drawCircle(mark, 8.dp.toPx(), Offset(x * k, y * k)) }
    }
}
