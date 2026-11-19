package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVH-46] 총괄 #451 사진 검토(접은 폭 200%에서 '순서대로 돌아요'가 '순서대로'로 잘림, 조건 12): one choice out of two to four, as
 * the [SegmentedButton] row it replaces. Its words never get cut. In one row while every option fits, on one line or two (broken
 * between words, never inside one); four options that do not go two rows of two; else (큰 글자, 접은 폭) they stack one under another,
 * each as wide as the row. TalkBack reads each as one of a group, as the row does, by [descriptions] when a word is short for the eye.
 */
@Composable
fun ChoiceRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The check before the chosen option; off where the row is tight by nature (a short word each). */
    check: Boolean = true,
    /** What TalkBack says for each option, when its word on the screen is short ('격주' for 격주 휴무). */
    descriptions: List<String>? = null,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // An option's room for its words: its share of the row less the button's own padding (12dp a side) and the check (18dp + 8dp).
        val padPx = with(density) { (24.dp + (if (check) 26.dp else 0.dp)).roundToPx() }
        val widthPx = with(density) { maxWidth.roundToPx() }
        val layout = remember(options, widthPx, padPx, style, density) {
            ChoiceFit.layout(options, widthPx, padPx) { measure(measurer, it, style) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.indices.chunked(layout.perRow).forEach { row ->
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { k, i ->
                        SegmentedButton(
                            selected = selected == i,
                            onClick = { onSelect(i) },
                            shape = SegmentedButtonDefaults.itemShape(k, row.size),
                            icon = { if (check) SegmentedButtonDefaults.Icon(selected == i) },
                            // Read as one phrase even when its words show on two lines.
                            modifier = (descriptions?.getOrNull(i) ?: options[i].takeIf { layout.labels[i] != it })
                                ?.let { d -> Modifier.semantics { contentDescription = d } } ?: Modifier,
                        ) { Text(layout.labels[i], textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

private fun measure(measurer: TextMeasurer, text: String, style: TextStyle): Int =
    measurer.measure(text, style, softWrap = false, maxLines = 1).size.width

/** [RVH-46] How [ChoiceRow]'s options fit: pure, so a test can hold it to its words. */
object ChoiceFit {
    /** Options [perRow] to a row, each with its words as they show (one line, or two broken between words). */
    data class Layout(val perRow: Int, val labels: List<String>)

    /**
     * All in one row if each option's words fit its share on at most two lines; four or more two to a row next; else one to a row (each
     * then breaks between words as its full width needs). [pad] is an option's own padding.
     */
    fun layout(options: List<String>, width: Int, pad: Int, measure: (String) -> Int): Layout {
        val n = options.size.coerceAtLeast(1)
        val tries = listOfNotNull(n, ((n + 1) / 2).takeIf { n >= 4 }).distinct()
        for (per in tries) {
            val room = width / per - pad
            if (room <= 0) continue
            if (options.all { measure(it) <= room }) return Layout(per, options)
            val two = options.map { lines(it, room, measure)?.takeIf { l -> l.size <= 2 } }
            if (two.all { it != null }) return Layout(per, two.map { it!!.joinToString("\n") })
        }
        val room = width - pad
        return Layout(1, options.map { lines(it, room, measure)?.joinToString("\n") ?: it })
    }

    /** [text] packed into lines of at most [room], breaking only between words; null when a word alone is wider. */
    fun lines(text: String, room: Int, width: (String) -> Int): List<String>? {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.any { width(it) > room }) return null
        val out = mutableListOf<String>()
        var line = ""
        for (w in words) {
            val next = if (line.isEmpty()) w else "$line $w"
            if (width(next) <= room) line = next else { out += line; line = w }
        }
        if (line.isNotEmpty()) out += line
        return out
    }
}
