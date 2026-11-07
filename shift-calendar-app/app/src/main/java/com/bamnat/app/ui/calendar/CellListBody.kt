package com.bamnat.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.layout.CellList
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatus
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.recipe.CalendarView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size

/**
 * [CELL-01] 1.39 날짜 칸에 일감·메모를 줄줄이 (사용자 관찰: 일감 위주 사용자의 칸에서 이름이 잘리고 메모가 안 보였음, 다른 일정 달력처럼
 * 칸 안에 여러 일이 여러 줄로 보이길 원함). One thing the day has, on its own line(s): a job in its client's colour with a bar at its left,
 * a memo with the memo colour's bar, a red day's name in red with no bar.
 */
internal data class CellLine(
    val text: String,
    val ink: Color,
    /** The bar at the line's left, or none. */
    val bar: Color? = null,
    /** The line's own ground, or none. */
    val fill: Color? = null,
    /** 가계약: a dashed outline instead of the fill. */
    val dashed: Boolean = false,
    /** 취소: struck through and faded. */
    val struck: Boolean = false,
    val bold: Boolean = false,
)

/** [CELL-01] The line at the foot of the list: 공수, what came in, the hours (the same line the plain cell has under its badge). */
internal data class CellFoot(val text: String, val color: Color, val bold: Boolean, val minor: Boolean = false)

/** Line height of the list's text, as [FOOTER_LINE] in [MonthGrid]. */
private const val LIST_LINE = 1.15f
private val LINE_GAP = 2.dp
private val LINE_PAD_V = 1.dp

/**
 * [CELL-01] Whether this calendar lists each day's jobs and memos in its cells: the user's choice, or by itself for field work (일감) and
 * for anyone who asked for memos in the cells (날짜 칸의 메모, or 달력 모양's 메모 줄).
 * [CELL-02] 1.39 (사용자 2026-11-07 "테마를 입으면 달력에 적어 둔 것이 안 보여요"): a theme or a 달력 모양 of the user's own lists too, in its
 * own colours, fonts and corners; only the sideways month keeps one line.
 */
internal fun cellListOn(choice: String, jobs: Boolean, memoInCells: Boolean, view: CalendarView, sideways: Boolean,
    /** [CELL-03] 1.39 (PC a483ca15: 사무직 달력에 메모가 점만 보여요) The calendar has memos on dates: 알아서 lists them too. */
    hasMemos: Boolean = false): Boolean =
    !sideways && when (choice) {
        "on" -> true
        "off" -> false
        else -> jobs || memoInCells || (view.memo ?: 0) > 0 || hasMemos
    }

/**
 * [TRY-05] The same choice for a kind of work's month drawn elsewhere (입어 보기, 스튜디오): 일감 for [style]'s calendar as the calendar's own
 * focus says, and [view] as the cells would have it. One choice, so the example lists exactly where the calendar does.
 */
internal fun cellListOn(choice: String, style: com.bamnat.core.work.WorkStyle?, memoInCells: Boolean, view: CalendarView,
                        /** G1 1.39 '알아서' lists a month with dated memos (the calendar passes its own); passed on as the calendar's own [cellListOn] takes it. */
                        hasMemos: Boolean = false): Boolean =
    cellListOn(choice, com.bamnat.core.work.CalendarFocus.of(style) == com.bamnat.core.work.CalendarFocus.JOBS, memoInCells, view, false, hasMemos)

/**
 * [CELL-03] 1.39 (PC a483ca15: 일감·메모가 없는 달도 칸이 높아 세 주만 보여요) Whether a listing month gets the tall cells
 * ([listCellHeight]): only a month with something to list, and only where lists were asked for (늘, 일감 달력, 날짜 칸의 메모, 메모 줄).
 * A list that came only because memos exist keeps the plain height: a memo day shows one line under its date.
 */
internal fun cellListTall(choice: String, jobs: Boolean, memoInCells: Boolean, view: CalendarView, monthHasLines: Boolean): Boolean =
    monthHasLines && (choice == "on" || jobs || memoInCells || (view.memo ?: 0) > 0)

/**
 * [CELL-04] 1.39 발굴 (일정 앱에서 온 사용자: 칸 안 메모가 모두 같은 색, 시간 순서가 아님) One memo of a day as its cell lists it:
 * the time of a schedule in front ("14:00 치과"), the colour the user gave the memo, and a checklist whose every item is done struck through.
 */
data class CellMemo(val text: String, val color: Int = 0, val done: Boolean = false)

/**
 * [CELL-04] A day's memos for its cell: schedules first, by their time, then the rest in the order they were (pinned first). A repeating
 * schedule shows its own time on each day it comes back.
 */
internal fun cellMemos(memos: List<com.bamnat.core.memo.Memo>, date: java.time.LocalDate): List<CellMemo> {
    fun time(m: com.bamnat.core.memo.Memo): java.time.LocalTime? =
        m.reminder?.at?.takeIf { it.toLocalDate() == date || m.repeat.isNotBlank() }?.toLocalTime()
    return memos.mapNotNull { m ->
        val title = m.title.ifBlank { m.preview }.trim().lineSequence().firstOrNull().orEmpty()
        if (title.isBlank()) null else Triple(m, time(m), title)
    }.sortedWith(compareBy({ it.second == null }, { it.second }, { !it.first.pinned }))
        .map { (m, t, title) ->
            CellMemo(
                text = t?.let { "%02d:%02d %s".format(it.hour, it.minute, title) } ?: title,
                color = m.colorIndex,
                done = m.isChecklist && m.checklist.isNotEmpty() && m.checklist.all { it.checked },
            )
        }
}

/** [CELL-02] 1.39 What a listing cell shows under its date, handed to a theme's own cell ([CustomCellBody]). */
internal class CellListContent(val lines: List<CellLine>, val foot: CellFoot?)

/**
 * [CELL-01] A month cell's height when its days list their jobs and memos: a little taller than 달력 모양's 높게 (×2.3 of a column,
 * 100~170dp), so a 50dp-wide cell holds the date and four or five lines.
 */
internal fun listCellHeight(width: Dp): Dp = (width / 7 * 2.3f).coerceIn(100.dp, 170.dp)

/** [CELL-01] A field day's jobs as lines: the 업체 (or the job's name), a platform's 건수, 가계약 dashed and 취소 struck through. */
internal fun jobLines(jobs: List<Job>?, dark: Boolean, ink: Color): List<CellLine> = jobs.orEmpty().map { j ->
    val color = clientColor(j.client.ifBlank { j.title })
    val name = if (PlatformWork.isPlatform(j)) "${j.client.ifBlank { j.title }.trim()} ${PlatformWork.count(j)}건"
        else j.client.ifBlank { j.title }.trim().ifBlank { "일감" }
    CellLine(
        text = name,
        ink = ink.copy(alpha = if (j.cancelled) 0.5f else 1f),
        bar = color,
        fill = if (j.status == JobStatus.TENTATIVE) null else color.copy(alpha = if (j.cancelled) 0.12f else if (dark) 0.40f else 0.22f),
        dashed = j.status == JobStatus.TENTATIVE,
        struck = j.cancelled,
        bold = true,
    )
}

/**
 * [CELL-01] The lines under the date: as many as the cell has room for, each item whole where there is room ([CellList.fit]) and "+n"
 * where there is not. The text keeps the cell's own size (11sp, at most 13dp with a bigger font), so a large font fits fewer lines
 * instead of breaking the cell.
 */
@Composable
internal fun CellListLines(lines: List<CellLine>, foot: CellFoot?, size: TextUnit, more: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val style = remember(size) { NumberStyle.merge(FooterTrim).merge(TextStyle(fontSize = size, lineHeight = size * LIST_LINE)) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The line as it really stands (Hangul and digits, with this style), not size × line height: a guess cut the foot off.
        val lineH = with(density) {
            val one = measurer.measure("가1", style).size.height
            maxOf(one.toDp(), (size * LIST_LINE).toDp())
        }
        // The row's padding (bar 4dp + 1dp) rounds on its own: a pixel short, so a line that only just fits is counted as wrapping
        // (PC 확인 1.39: '대체공휴일' given two lines needed three and was cut at the foot).
        val textW = with(density) { ((maxWidth - 5.dp).toPx() - 2f).toInt().coerceAtLeast(1) }
        val wants = remember(lines, textW, style) {
            lines.map { l -> measurer.measure(l.text, style, constraints = Constraints(maxWidth = textW)).lineCount }
        }
        val room = if (constraints.hasBoundedHeight) maxHeight else lineH * 8
        val all = CellList.slots(room.value, (lineH + LINE_PAD_V * 2).value, LINE_GAP.value)
        // [CELL-03] 1.39 No room for even one line (a low cell whose mark took its own line): a dot in the first thing's colour, as the
        // plain cell's memo dot, not a "+1" cut in half. The day's button still reads them all.
        if (all <= 0) {
            if (lines.isNotEmpty()) Box(Modifier.padding(start = 2.dp, top = 1.dp).size(5.dp)
                .background(lines.first().bar ?: lines.first().ink, androidx.compose.foundation.shape.CircleShape))
            return@BoxWithConstraints
        }
        val withFoot = CellList.fit(lines.size, (all - if (foot != null) 1 else 0).coerceAtLeast(0), wants)
        // The hours or the lunar date give their line to the day's own things when those do not all fit; money and 공수 keep theirs.
        val dropFoot = foot != null && foot.minor && withFoot.hidden > 0
        val fit = if (dropFoot) CellList.fit(lines.size, all, wants) else withFoot
        val shownFoot = foot.takeUnless { dropFoot }
        Column(verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            fit.lines.forEachIndexed { i, n -> CellLineRow(lines[i], n, style) }
            if (fit.hidden > 0) Text(
                "+${fit.hidden}", style = style, color = more, maxLines = 1, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            shownFoot?.let { f ->
                Text(f.text, style = style, color = f.color, maxLines = 1, overflow = TextOverflow.Clip,
                    fontWeight = if (f.bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 1.dp))
            }
        }
    }
}

@Composable
private fun CellLineRow(line: CellLine, maxLines: Int, style: TextStyle) {
    val shape = RoundedCornerShape(3.dp)
    Text(
        line.text,
        style = style,
        color = line.ink,
        maxLines = maxLines,
        // One line keeps going to the cell's edge ("엄마 MRI 1…"), not stopping at the last whole word ("엄마").
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (line.bold) FontWeight.SemiBold else FontWeight.Normal,
        textDecoration = if (line.struck) TextDecoration.LineThrough else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(line.fill?.let { Modifier.background(it) } ?: Modifier)
            .drawBehind {
                val bar = line.bar ?: return@drawBehind
                if (line.dashed) drawRoundRect(bar, cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                drawRect(bar, topLeft = Offset.Zero, size = Size(2.5.dp.toPx(), size.height))
            }
            .padding(start = if (line.bar != null) 4.dp else 1.dp, end = 1.dp, top = LINE_PAD_V, bottom = LINE_PAD_V)
            // [CELL-03] A line may end in '…' where the cell is narrow; the day's button reads every job and memo whole instead.
            .clearAndSetSemantics {},
    )
}

/** [CELL-01] The date row's text colour for a day's "+n": quiet, like the counts elsewhere in the cell. */
@Composable
internal fun cellMoreColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
