package com.bamnat.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.bamnat

/**
 * 2.0.0 [RVN-22] 검토 I10bb-03 보내기 전 폰 안 검사: what the server would not take in a box (메일·전화번호·계좌번호·금액, core
 * TextRules, the server's own table), said while typing, so a post is never refused after it goes. The very letters are marked in the
 * box ([rememberMarkLetters]: caution colour and an underline, so colour is not the only sign) and this line says why under it, with
 * the same △ as every 주의 line ([ToneLine]); a screen reader hears it as it appears.
 */
@Composable
fun TextStopLine(line: String?, modifier: Modifier = Modifier) {
    if (line == null) return
    ToneLine(line, modifier.semantics { liveRegion = LiveRegionMode.Polite }, tone = Tone.Caution)
}

/** The box's letters in [range] marked (caution colour, underlined); the text and the caret stay as typed. None when [range] is null. */
@Composable
fun rememberMarkLetters(range: IntRange?): VisualTransformation {
    val color = MaterialTheme.bamnat.caution
    return remember(range, color) {
        if (range == null) VisualTransformation.None else VisualTransformation { s ->
            val end = (range.last + 1).coerceIn(0, s.length)
            val start = range.first.coerceIn(0, end)
            val marked = buildAnnotatedString {
                append(s)
                addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, end)
            }
            TransformedText(marked, OffsetMapping.Identity)
        }
    }
}
