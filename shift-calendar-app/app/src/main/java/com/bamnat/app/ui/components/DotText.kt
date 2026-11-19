package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text

/**
 * 2.0.0 [RVO-29] HQ-152 (조건 12·4): a line of parts joined by " · " (the price line, 결제 고지, 오늘 0원 · …) as one line when it fits, and
 * otherwise one part a line with no dot, so at 글자 200% or the folded width a dot is never left alone at a line's end or start.
 */
@Composable
fun DotText(line: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    modifier: Modifier = Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val parts = line.split(" · ")
        val fits = parts.size < 2 || measurer.measure(line, style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        if (fits) Text(line, style = style, color = color)
        else Column(Modifier.clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(line) }) {
            parts.forEach { Text(it, style = style, color = color) }
        }
    }
}
