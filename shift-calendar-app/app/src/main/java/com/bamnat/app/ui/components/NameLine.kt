package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVS-16] 이름표 줄 (총괄 #495 사진 검토, 디자인 품질 조건 12: 글자 200% · 접은 폭에서 색 고르기의 '바탕색'이 '바 / 탕 / 색'으로
 * 섰어요): a name ([name]: 바탕색, 진하기 40%, 글꼴 …) with what goes after it ([end]: 지금 값, ☆ 내 색에, 처음대로, −/+) on one line, the
 * end at the far side. When the name would be squeezed there — less room than it needs on one line and under [share] of the width, which
 * is big text on a narrow phone — the name has the line to itself and [end] goes on the next line at the same far side, so no word
 * stands up letter by letter and the buttons stay where the thumb expects them.
 *
 * [widest] is the end at its fullest (with the buttons a chosen value brings), measured but never drawn: the line is laid out for it, so
 * picking the first colour does not push the rows under it down (PC 확인 1.37 A10, 줄이 밀림).
 */
@Composable
fun NameLine(
    name: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    share: Float = 0.45f,
    widest: (@Composable () -> Unit)? = null,
    end: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { name() }
            Row(verticalAlignment = Alignment.CenterVertically) { end() }
            if (widest != null) Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) { widest() }
        },
        modifier = modifier,
    ) { ms, c ->
        val gap = 8.dp.roundToPx()
        val min = minHeight.roundToPx()
        val endP = ms[1].measure(Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight))
        val endW = maxOf(endP.width, ms.getOrNull(2)?.maxIntrinsicWidth(Constraints.Infinity) ?: 0)
        val nameNeeds = ms[0].maxIntrinsicWidth(Constraints.Infinity)
        val width = if (c.hasBoundedWidth) c.maxWidth else nameNeeds + gap + endW
        val room = width - (if (endW > 0) endW + gap else 0)
        val oneLine = room >= nameNeeds || room >= width * share
        if (oneLine) {
            val nameP = ms[0].measure(Constraints(maxWidth = room.coerceAtLeast(0), maxHeight = c.maxHeight))
            val h = maxOf(min, nameP.height, endP.height).coerceIn(c.minHeight, c.maxHeight)
            layout(width, h) {
                nameP.place(0, (h - nameP.height) / 2)
                endP.place(width - endP.width, (h - endP.height) / 2)
            }
        } else {
            val nameP = ms[0].measure(Constraints(maxWidth = width, maxHeight = c.maxHeight))
            val h = (nameP.height + endP.height).coerceIn(c.minHeight, c.maxHeight)
            layout(width, h) {
                nameP.place(0, 0)
                endP.place(width - endP.width, nameP.height)
            }
        }
    }
}
