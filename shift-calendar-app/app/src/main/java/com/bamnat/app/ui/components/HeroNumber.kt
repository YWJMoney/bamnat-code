package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * 1.38 [DSG-02] (흐름·디자인 계획 4장): "7시간 10분", "152,000원", "+3.2%" with the numbers at full size and the words
 * around them at [unitScale] of it, so the eye reads the number first. Digits, the marks that belong to a number
 * (, . + − : /) and spaces stay full; everything else is a unit.
 */
fun numberWithUnit(text: String, unitScale: Float = 0.55f): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val unit = !isNumberChar(text[i])
        var j = i
        while (j < text.length && !isNumberChar(text[j]) == unit) j++
        val part = text.substring(i, j)
        if (unit) withStyle(SpanStyle(fontSize = unitScale.em, fontWeight = FontWeight.Medium)) { append(part) } else append(part)
        i = j
    }
}

private fun isNumberChar(c: Char): Boolean = c.isDigit() || c in ",.+-−:/ "

/**
 * 1.38 [DSG-01] 한 숫자 머리: a summary page's one number, the biggest text on it, with one line under it that says what
 * it means ("지난주보다 40분 더 잤어요"). The number takes its [tone]'s meaning colour; the caption stays quiet. A
 * screen reader hears it as one sentence.
 */
@Composable
fun HeroNumber(
    value: String,
    unit: String? = null,
    caption: String? = null,
    tone: Tone = Tone.Plain,
    modifier: Modifier = Modifier,
) {
    val whole = if (unit.isNullOrEmpty()) value else value + unit
    Column(modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = listOfNotNull(whole, caption).joinToString(", ")
    }) {
        Text(numberWithUnit(whole), style = MaterialTheme.typography.displaySmall.merge(NumberStyle)
            .copy(fontWeight = FontWeight.Bold), color = tone.color())
        if (!caption.isNullOrEmpty()) Text(caption, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
