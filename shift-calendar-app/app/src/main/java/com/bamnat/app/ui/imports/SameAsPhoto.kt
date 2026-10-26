package com.bamnat.app.ui.imports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.app.imports.RosterScanner
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.model.ShiftType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [READ-17] 1.27 순번 447 (사용자 "사진과 내용이 같은가요? 물어보고 다르면 직접 수정 버튼을 줘서 해당 부분만 수정할수
 * 있게"): the photo's row of dates and the user's own line, cut out of the photo, with what was read under each day.
 * '네, 같아요' takes the read cells as looked at (yellow cells too); '다른 곳이 있어요' lets each cell be tapped and fixed on
 * its own ([onFix] opens the one-cell keys). The photo stays in memory only.
 */
@Composable
internal fun SameAsPhotoCard(
    scan: RosterScanner.Scan, row: RosterRow, rank: Int, cells: Map<Int, DutyCell>, doubtful: Set<Int>, types: List<ShiftType>,
    days: Int, same: Boolean?, onAnswer: (Boolean) -> Unit, onFix: (Int) -> Unit, onAgain: () -> Unit,
) {
    val byId = remember(types) { types.associateBy { it.id } }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (same == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사진과 같다고 확인했어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    TextButton(onClick = onAgain) { Text("다시 보기") }
                }
                return@Column
            }
            Text("사진과 내용이 같은가요?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                if (same == false) "다른 칸을 눌러 그 칸만 고쳐 주세요. 고친 글자는 다음 근무표부터 배워요."
                else "위는 사진, 아래는 읽은 근무예요. 옆으로 밀어 끝까지 봐 주세요." +
                    (if (scan.ways > 1) " ${scan.ways}가지 방법으로 읽어 맞춰 봤어요." else "") +
                    (if (doubtful.isNotEmpty()) " 노란 칸 ${doubtful.size}개는 읽기가 갈렸어요." else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PhotoStrip(scan, row, rank, cells, doubtful, byId, days, tappable = same == false, onFix = onFix)
            if (same == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("네, 같아요") }
                OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("다른 곳이 있어요") }
            } else Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onAnswer(true) }) { Text("다 고쳤어요") }
            }
        }
    }
}

/** Where the dates and the user's line are on the photo (the words' pixels), and each day's middle across. */
internal class StripPlace(val days: RosterPhotoRead.Line?, val mine: RosterPhotoRead.Line?, val dayX: Map<Int, Int>)

/**
 * The row of dates (the first one read) and the [rank]-th line of work (the user's, in the table's order; by its name
 * when the count differs), with each date's middle from the words read on it.
 */
internal fun stripPlace(scan: RosterScanner.Scan, row: RosterRow, rank: Int, days: Int): StripPlace {
    val lines = scan.read.lines
    val dayLine = lines.firstOrNull { it.kind == RosterPhotoRead.Kind.DAYS }
    val duties = lines.filter { it.kind == RosterPhotoRead.Kind.DUTIES }.sortedBy { it.top }
    val mine = row.label.takeIf { it.isNotBlank() }?.let { l -> duties.firstOrNull { l in it.text } } ?: duties.getOrNull(rank)
    val dayX = HashMap<Int, Int>()
    if (dayLine != null) scan.words.filter { w -> w.centerY in dayLine.top..dayLine.bottom }.forEach { w ->
        val d = w.text.trim().trimEnd('.', '일').toIntOrNull()
        if (d != null && d in 1..days && d !in dayX) dayX[d] = w.centerX
    }
    // Days the photo's row of dates lost: in between the ones read, evenly.
    val known = dayX.toSortedMap()
    if (known.size >= 2) {
        val ds = known.keys.toList()
        val step = (known.getValue(ds.last()) - known.getValue(ds.first())).toDouble() / (ds.last() - ds.first())
        for (d in 1..days) if (d !in dayX) {
            val before = ds.lastOrNull { it < d }
            val after = ds.firstOrNull { it > d }
            dayX[d] = when {
                before != null && after != null -> (known.getValue(before) + (known.getValue(after) - known.getValue(before)) * (d - before).toDouble() / (after - before)).roundToInt()
                before != null -> (known.getValue(before) + step * (d - before)).roundToInt()
                else -> (known.getValue(after!!) - step * (after - d)).roundToInt()
            }
        }
    }
    return StripPlace(dayLine, mine, dayX)
}

@Composable
private fun PhotoStrip(
    scan: RosterScanner.Scan, row: RosterRow, rank: Int, cells: Map<Int, DutyCell>, doubtful: Set<Int>, byId: Map<Long, ShiftType>,
    days: Int, tappable: Boolean, onFix: (Int) -> Unit,
) {
    val place = remember(scan, row, rank) { stripPlace(scan, row, rank, days) }
    val image = scan.image
    val density = LocalDensity.current
    val xs = place.dayX
    // The words' pixels → dp: a day at least 34dp wide, so a cell can be tapped.
    val spacing = if (xs.size >= 2) (xs.values.max() - xs.values.min()).toFloat() / (xs.size - 1).coerceAtLeast(1) else 0f
    val left = listOfNotNull(place.days?.left, place.mine?.left, xs.values.minOrNull()?.minus(spacing.toInt())).minOrNull() ?: 0
    val right = listOfNotNull(place.days?.right, place.mine?.right, xs.values.maxOrNull()?.plus(spacing.toInt())).maxOrNull() ?: 0
    val dpPerPx = if (spacing > 0) 34f / spacing else 0f
    val width: Dp = if (right > left && dpPerPx > 0) ((right - left) * dpPerPx).dp else 0.dp
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        if (image != null && width > 0.dp) {
            val bitmap = remember(image) { image.asImageBitmap() }
            listOfNotNull(place.days, place.mine).forEach { l ->
                val pad = ((l.bottom - l.top) * 0.25f).toInt()
                val top = max(0, l.top - pad)
                val bottom = min((image.height * scan.scale).toInt(), l.bottom + pad)
                val h = ((bottom - top) * dpPerPx).dp
                Canvas(Modifier.width(width).height(h).semantics { contentDescription = "사진 속 ${if (l === place.days) "날짜 줄" else "내 줄"}" }) {
                    val s = 1f / scan.scale
                    drawImage(
                        bitmap,
                        srcOffset = IntOffset((left * s).roundToInt().coerceAtLeast(0), (top * s).roundToInt().coerceAtLeast(0)),
                        srcSize = IntSize(((right - left) * s).roundToInt().coerceIn(1, image.width), ((bottom - top) * s).roundToInt().coerceIn(1, image.height)),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                }
            }
        }
        // What was read, under its own date; or, when the dates were not placed, one after another.
        val placed = width > 0.dp && xs.size == days
        Box(Modifier.then(if (placed) Modifier.width(width) else Modifier).height(56.dp)) {
            if (!placed) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..days).forEach { d -> Cell(d, cells[d], d in doubtful, byId, tappable, onFix, Modifier) }
            } else (1..days).forEach { d ->
                val x = with(density) { ((xs.getValue(d) - left) * dpPerPx).dp - 16.dp }
                Cell(d, cells[d], d in doubtful, byId, tappable, onFix, Modifier.offset(x = x))
            }
        }
    }
}

@Composable
private fun Cell(day: Int, cell: DutyCell?, doubt: Boolean, byId: Map<Long, ShiftType>, tappable: Boolean, onFix: (Int) -> Unit, modifier: Modifier) {
    val text = when (cell) {
        null -> "·"
        is DutyCell.Shift -> byId[cell.shiftTypeId]?.label ?: "?"
        DutyCell.Off -> "휴"
        is DutyCell.Leave -> if (cell.quarters < 4) "반" else "연"
        is DutyCell.Unknown -> cell.raw.take(3)
    }
    val name = when (cell) {
        null -> "비어 있음"
        is DutyCell.Shift -> byId[cell.shiftTypeId]?.name ?: "모르는 근무"
        DutyCell.Off -> "쉬는 날"
        is DutyCell.Leave -> "휴가"
        is DutyCell.Unknown -> "‘${cell.raw}’ 모르는 글자"
    }
    val warn = Warn
    Column(
        modifier.width(32.dp)
            .then(if (doubt) Modifier.background(warn.copy(alpha = 0.35f), RoundedCornerShape(6.dp)) else Modifier)
            .then(if (tappable) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = "${day}일 고치기") { onFix(day) } else Modifier)
            .semantics { contentDescription = "${day}일 $name" + if (doubt) ", 확인 필요" else "" }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** The yellow of a cell to look at, as on the month. */
private val Warn = androidx.compose.ui.graphics.Color(0xFFFFC107)
