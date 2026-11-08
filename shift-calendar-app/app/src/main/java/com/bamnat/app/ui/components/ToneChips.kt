package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/** One chip of [ToneChipRows]: its words and, when it leads somewhere, what a tap does. */
data class ToneChip(val words: String, val onClick: (() -> Unit)? = null)

/**
 * 1.39 [FLOW-18][FLOW-20] 칩 두 줄 (흐름·디자인 계획 3-9, DSG-03 의미색): what went well in green and what asks for care in orange, each a
 * row of chips under a small name, so a summary is read without reading a list. A row with nothing is left out, and with both empty
 * nothing is drawn (DSG-09: no empty frame). The colour is a dot, and TalkBack hears the row's name, so the meaning is not colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToneChipRows(goodLabel: String, good: List<ToneChip>, cautionLabel: String, caution: List<ToneChip>, modifier: Modifier = Modifier) {
    if (good.isEmpty() && caution.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Triple(goodLabel, good, Tone.Good), Triple(cautionLabel, caution, Tone.Caution)).forEach { (label, chips, tone) ->
            if (chips.isEmpty()) return@forEach
            Text(label, style = MaterialTheme.typography.labelMedium, color = tone.color())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { c ->
                    FilterChip(
                        selected = false,
                        onClick = { c.onClick?.invoke() },
                        enabled = true,
                        label = { Text(c.words, maxLines = 1) },
                        leadingIcon = { Box(Modifier.size(8.dp).background(tone.color(), CircleShape)) },
                        modifier = Modifier.semantics { contentDescription = "$label: ${c.words}" },
                    )
                }
            }
        }
    }
}

/** The small grey line under [ToneChipRows] that says how it was read. */
@Composable
fun ToneChipNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
}
