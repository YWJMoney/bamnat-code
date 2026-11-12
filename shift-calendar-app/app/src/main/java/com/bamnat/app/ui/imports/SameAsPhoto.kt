package com.bamnat.app.ui.imports

import com.bamnat.app.ui.theme.color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.bamnat.core.text.Josa
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [READ-17] 1.27 순번 447 (사용자 "사진과 내용이 같은가요? 물어보고 다르면 직접 수정 버튼을 줘서 해당 부분만 수정할수
 * 있게"): the photo's row of dates and the user's own line, cut out of the photo, with what was read under each day.
 * '네, 같아요' takes the read cells as looked at; '다른 곳이 있어요' lets each cell be tapped and fixed on its own ([onFix] opens
 * the one-cell keys). The photo stays in memory only.
 * 2.0.0 [RVL-36] (검토 U07b-04) Yellow cells first, as the payslip's yellow lines ([com.bamnat.app.ui.pay.SlipCheck]): '네, 같아요' took
 * them all, blanks the repeat filled too, in one tap. Now '노란 n칸 확인하기' points at each in turn (the strip slides to it) with
 * '맞아요' ([onRight]) and '고치기'; '네, 같아요' shows only once none is left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SameAsPhotoCard(
    scan: RosterScanner.Scan, row: RosterRow, rank: Int, cells: Map<Int, DutyCell>, doubtful: Set<Int>, types: List<ShiftType>,
    days: Int, same: Boolean?, onAnswer: (Boolean) -> Unit, onFix: (Int) -> Unit, onAgain: () -> Unit, onRight: (Int) -> Unit,
) {
    val byId = remember(types) { types.associateBy { it.id } }
    val yellow = doubtful.filter { it in 1..days }.sorted()
    var reviewing by rememberSaveable(row.label, rank) { mutableStateOf(false) }
    val pointed = yellow.firstOrNull()?.takeIf { reviewing && same == null }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (same == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사진과 같다고 확인했어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                    TextButton(onClick = { reviewing = false; onAgain() }) { Text("다시 보기") }
                }
                return@Column
            }
            Text(if (same == null && yellow.isNotEmpty()) "노란 칸 ${yellow.size}개만 사진과 맞는지 봐 주세요" else "사진과 내용이 같은가요?",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                when {
                    same == false -> "다른 칸을 눌러 그 칸만 고쳐 주세요. 고친 글자는 다음 근무표부터 배워요."
                    pointed != null -> "노란 칸마다 ‘맞아요’나 ‘고치기’를 눌러 주세요."
                    else -> "위는 사진, 아래는 읽은 근무예요. 옆으로 밀어 끝까지 봐 주세요." +
                        (if (scan.ways > 1) " ${scan.ways}가지 방법으로 읽어 맞춰 봤어요." else "") +
                        // [READ-41] PC 12차: a blank the repeat filled went in with no word; it is said apart, so a real blank is not missed.
                        row.filled.filter { it in doubtful }.let { f ->
                            val split = doubtful.size - f.size
                            (if (split > 0) " 노란 칸 ${split}개는 읽기가 갈렸어요." else "") +
                                (if (f.isNotEmpty()) " ${f.sorted().joinToString(", ") { "${it}일" }}은 사진에 글자가 없어 앞뒤 반복으로 채웠어요." else "")
                        }
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PhotoStrip(scan, row, rank, cells, doubtful, byId, days, tappable = same == false || pointed != null, pointed = pointed, onFix = onFix)
            if (pointed != null) {
                // The cell pointed at, in words, with its two answers (an unknown code has only 고치기).
                val cell = cells[pointed]
                Text(
                    "${pointed}일 · " + when {
                        cell is DutyCell.Unknown -> "‘${cell.raw}’ 모르는 글자예요"
                        pointed in row.filled -> cellName(cell, byId).let { "사진에 글자가 없어 앞뒤 반복으로 $it${Josa.eulreul(it)} 넣었어요" }
                        else -> cellName(cell, byId).let { "$it${Josa.euro(it)} 읽었어요" }
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cell != null && cell !is DutyCell.Unknown) OutlinedButton(onClick = { onRight(pointed) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("맞아요") }
                    TextButton(onClick = { onFix(pointed) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("고치기") }
                }
            }
            // The screen's one filled button is '넣기' under the month (조건 2): the card's answer is outlined, the other a text button,
            // and while a cell is pointed at its own two answers are the only ones.
            if (same == null) { if (pointed == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // [PHOTO-29]'s order for the roster: yellow cells get their own answers, not one '같아요' for all.
                OutlinedButton(onClick = { if (yellow.isEmpty()) onAnswer(true) else reviewing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (yellow.isEmpty()) "네, 같아요" else "노란 ${yellow.size}칸 확인하기")
                }
                TextButton(onClick = { reviewing = false; onAnswer(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다른 곳이 있어요") }
            } } else Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onAnswer(true) }) { Text("다 고쳤어요") }
            }
        }
    }
}

private fun cellName(cell: DutyCell?, byId: Map<Long, ShiftType>): String = when (cell) {
    null -> "빈칸"
    is DutyCell.Shift -> byId[cell.shiftTypeId]?.name ?: "모르는 근무"
    DutyCell.Off -> "쉬는 날"
    is DutyCell.Leave -> if (cell.quarters < 4) "반차" else "연차"
    is DutyCell.Unknown -> "‘${cell.raw}’"
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
    val mine = mineOf(duties, row.label, rank)
    val dayX = HashMap<Int, Int>()
    if (dayLine != null) {
        val found = scan.words.filter { w -> w.centerY in dayLine.top..dayLine.bottom }.mapNotNull { w ->
            w.text.trim().trimEnd('.', '일').toIntOrNull()?.takeIf { it in 1..days }?.let { it to w.centerX }
        }
        dayX.putAll(inOrder(found))
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

/**
 * 2.0.0 [RVL-36] (검토 SCAN-25) The user's line on the photo. It was the first line with the name anywhere in it, so '김간' found
 * '김간호' above and showed another person's line. Now: the line whose first words make up the whole name (words read apart, '김 간호',
 * join), the n-th such line for a twin's "김간호 (2)"; else the [rank]-th line, as before.
 */
internal fun mineOf(duties: List<RosterPhotoRead.Line>, label: String, rank: Int): RosterPhotoRead.Line? {
    val twin = TWIN_LABEL.matchEntire(label.trim())
    val name = (twin?.groupValues?.get(1) ?: label).filterNot { it.isWhitespace() }
    if (name.isEmpty()) return duties.getOrNull(rank)
    val nth = twin?.groupValues?.get(2)?.toIntOrNull() ?: 1
    val named = duties.filter { l ->
        l.text.split(' ').filter { it.isNotBlank() }.take(NAME_WORDS).runningReduce { a, b -> a + b }.any { it == name }
    }
    return named.getOrNull(nth - 1) ?: named.singleOrNull() ?: duties.getOrNull(rank)
}

/** A twin's label: "김간호 (2)" ([com.bamnat.core.imports.DutyRoster.twin]). */
private val TWIN_LABEL = Regex("""^(.*\S) \((\d+)\)$""")

/** A name is read as at most this many words ("김 간 호"). */
private const val NAME_WORDS = 4

/**
 * [READ-37] 1.38 PC: "12" read at the 1st's place drew 12일's cell over 1일's and left its own place empty. The dates of
 * a row stand left to right in order, so of every date read (a date may be read twice), the most that do are kept:
 * the longest run rising in both date and place. A date out of that order is placed between its neighbours instead.
 */
internal fun inOrder(found: List<Pair<Int, Int>>): Map<Int, Int> {
    val c = found.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    if (c.isEmpty()) return emptyMap()
    val best = IntArray(c.size) { 1 }
    val prev = IntArray(c.size) { -1 }
    for (i in c.indices) for (j in 0 until i) {
        if (c[j].first < c[i].first && c[j].second < c[i].second && best[j] + 1 > best[i]) { best[i] = best[j] + 1; prev[i] = j }
    }
    var at = best.indices.maxBy { best[it] }
    val out = HashMap<Int, Int>()
    while (at >= 0) { out[c[at].first] = c[at].second; at = prev[at] }
    return out
}

@Composable
private fun PhotoStrip(
    scan: RosterScanner.Scan, row: RosterRow, rank: Int, cells: Map<Int, DutyCell>, doubtful: Set<Int>, byId: Map<Long, ShiftType>,
    days: Int, tappable: Boolean, pointed: Int?, onFix: (Int) -> Unit,
) {
    val place = remember(scan, row, rank) { stripPlace(scan, row, rank, days) }
    val image = scan.image
    val density = LocalDensity.current
    val xs = place.dayX
    // 2.0.0 [RVL-36] (검토 U07b-07) A day is 48dp across (wider with big letters), so each cell is pressed in 48dp: the cell
    // itself stays its size in the middle. The photo above is cut at the same scale, so a day stays under its date.
    val pitch = STRIP_DAY * max(1f, density.fontScale * 0.75f)
    val spacing = if (xs.size >= 2) (xs.values.max() - xs.values.min()).toFloat() / (xs.size - 1).coerceAtLeast(1) else 0f
    val left = listOfNotNull(place.days?.left, place.mine?.left, xs.values.minOrNull()?.minus(spacing.toInt())).minOrNull() ?: 0
    val right = listOfNotNull(place.days?.right, place.mine?.right, xs.values.maxOrNull()?.plus(spacing.toInt())).maxOrNull() ?: 0
    val dpPerPx = if (spacing > 0) pitch.value / spacing else 0f
    val width: Dp = if (right > left && dpPerPx > 0) ((right - left) * dpPerPx).dp else 0.dp
    val placed = width > 0.dp && xs.size == days
    // The middle of each day across the strip.
    fun middle(d: Int): Dp = if (placed) ((xs.getValue(d) - left) * dpPerPx).dp else pitch * (d - 1) + pitch / 2 + 2.dp * (d - 1)
    val scroll = rememberScrollState()
    val motionOff = com.bamnat.app.ui.theme.LocalMotionOff.current
    // The yellow cell pointed at slides to the middle of the card.
    LaunchedEffect(pointed) {
        val d = pointed ?: return@LaunchedEffect
        val to = (with(density) { middle(d).toPx() } - scroll.viewportSize / 2f).roundToInt().coerceIn(0, scroll.maxValue.coerceAtLeast(0))
        if (motionOff) scroll.scrollTo(to) else scroll.animateScrollTo(to)
    }
    Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
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
        Box(Modifier.then(if (placed) Modifier.width(width) else Modifier).heightIn(min = 56.dp)) {
            if (!placed) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..days).forEach { d -> Cell(d, cells[d], d in doubtful, d == pointed, byId, tappable, onFix, pitch, Modifier) }
            } else (1..days).forEach { d ->
                Cell(d, cells[d], d in doubtful, d == pointed, byId, tappable, onFix, pitch, Modifier.offset(x = middle(d) - pitch / 2))
            }
        }
    }
}

/** How wide a day is on the strip at ordinary letters: one finger's press. */
private val STRIP_DAY = 48.dp

@Composable
private fun Cell(
    day: Int, cell: DutyCell?, doubt: Boolean, pointed: Boolean, byId: Map<Long, ShiftType>, tappable: Boolean, onFix: (Int) -> Unit,
    pitch: Dp, modifier: Modifier,
) {
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
    // 2.0.0 [RVP-12] (검토 UIX-12) The app's one caution colour, not a yellow of its own. [RVL-36] Its caution, not the
    // calendar's warning: that one is the Sunday red, and a yellow cell drawn red said danger (조건 6).
    val warn = com.bamnat.app.ui.theme.Tone.Caution.color()
    // The whole day's width presses; the cell drawn in it keeps its size (8dp each side).
    Box(
        modifier.width(pitch).heightIn(min = 56.dp)
            .then(if (tappable) Modifier.clickable(role = Role.Button, onClickLabel = "${day}일 고치기") { onFix(day) } else Modifier)
            .semantics { contentDescription = "${day}일 $name" + (if (doubt) ", 확인 필요" else "") + (if (pointed) ", 지금 보는 칸" else "") },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.width(pitch - 16.dp)
                .then(if (doubt) Modifier.background(warn.copy(alpha = 0.22f), MaterialTheme.shapes.extraSmall) else Modifier)
                .then(when {
                    pointed -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                    tappable -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    else -> Modifier
                })
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Box(Modifier.fillMaxWidth().heightIn(min = 26.dp), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
