package com.bamnat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [BIGTXT-01] 1.39 (사용자 사진: 글자를 크게 하면 '메모 [자세히 쓰기]'의 '메모'가 '메/모'로, '일정 시간도 적기'가 한 글자씩 세로로 섰음).
 * A title (or any text) with something at its end — a button, an amount: side by side while the [start] keeps at least [minStart] (or all
 * of itself, if it is shorter), else the [end] goes under it, at the end. A row that used `weight(1f)` for the start squeezed it to one
 * letter a line once the end grew with the font; this never does.
 */
@Composable
fun SideOrBelow(
    modifier: Modifier = Modifier,
    minStart: Dp = 112.dp,
    gap: Dp = 8.dp,
    /** Where the [end] sits when it goes under the start. */
    belowAlign: Alignment.Horizontal = Alignment.End,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(contents = listOf(start, end), modifier = modifier) { (s, e), c ->
        val startM = s.firstOrNull()
        val endM = e.firstOrNull()
        val maxW = if (c.hasBoundedWidth) c.maxWidth else (startM?.maxIntrinsicWidth(Constraints.Infinity) ?: 0) + (endM?.maxIntrinsicWidth(Constraints.Infinity) ?: 0) + gap.roundToPx()
        if (endM == null) {
            val p = startM?.measure(c.copy(minWidth = maxW, maxWidth = maxW))
            return@Layout layout(maxW, maxOf(p?.height ?: 0, c.minHeight)) { p?.place(0, 0) }
        }
        val gapPx = gap.roundToPx()
        val endW = endM.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxW)
        val startWhole = startM?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val startNeed = minOf(startWhole, minStart.roundToPx())
        val loose = Constraints(maxHeight = if (c.hasBoundedHeight) c.maxHeight else Constraints.Infinity)
        if (maxW - endW - gapPx >= startNeed) {
            val sw = maxW - endW - gapPx
            val ep = endM.measure(loose.copy(maxWidth = endW))
            val sp = startM?.measure(loose.copy(minWidth = sw, maxWidth = sw))
            val h = maxOf(sp?.height ?: 0, ep.height, c.minHeight)
            layout(maxW, h) {
                sp?.place(0, (h - sp.height) / 2)
                ep.place(maxW - ep.width, (h - ep.height) / 2)
            }
        } else {
            val sp = startM?.measure(loose.copy(minWidth = maxW, maxWidth = maxW))
            val ep = endM.measure(loose.copy(maxWidth = maxW))
            val sh = sp?.height ?: 0
            val h = maxOf(sh + ep.height, c.minHeight)
            layout(maxW, h) {
                sp?.place(0, 0)
                ep.place(belowAlign.align(ep.width, maxW, layoutDirection), sh)
            }
        }
    }
}
