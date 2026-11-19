package com.bamnat.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVP-83] (디자인 품질 조건 11, 총괄이 J #448 계약서 창 사진에서 봄) Where words scroll inside a box that does not move (a
 * dialog's text under its title and above its buttons, a sheet's list), a 1dp line at the top while there is more above and at the
 * bottom while there is more below, so a line cut in half reads as "더 있어요" and not as a broken screen.
 *
 * Put it before the scroll, on the same state: `Column(Modifier.scrollEdges(s).verticalScroll(s))`, s = rememberScrollState().
 */
fun Modifier.scrollEdges(state: ScrollState): Modifier = composed {
    val line = MaterialTheme.colorScheme.outlineVariant
    drawWithContent {
        drawContent()
        val w = 1.dp.toPx()
        if (state.canScrollBackward) drawRect(line, size = Size(size.width, w))
        if (state.canScrollForward) drawRect(line, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
    }
}
