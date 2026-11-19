package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVP-46] (검토 UIX-03·T13-04·U13-16, 조건 2·8) A month's order made visible: the steps in a row, the one it is at in the
 * ink and bold, those behind it ticked, those ahead quiet. It is a picture of where the work is, not a row of buttons, so it adds no
 * button's weight; what is to be pressed now is the one filled button under it. [optional] are steps that may be left out (신청):
 * behind the current one they are not ticked, since nothing was done there.
 *
 * Each name is one to three words, so at 글자 200% on the folded width every step still reads whole.
 */
@Composable
fun StepBand(steps: List<String>, current: Int, modifier: Modifier = Modifier, optional: Set<Int> = emptySet(), name: String = "순서") {
    val said = "$name ${steps.size}단계 중 ${current + 1}단계 ${steps.getOrElse(current) { "" }}"
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, label ->
            val now = i == current
            val behind = i < current
            val ticked = behind && i !in optional
            Column(Modifier.weight(1f).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.defaultMinSize(24.dp, 24.dp).background(
                    when { now -> MaterialTheme.colorScheme.primary; ticked -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant },
                    CircleShape), contentAlignment = Alignment.Center) {
                    // The number is always laid out, so a ticked step's circle is as tall as the others' at any text size
                    // and the names under them stand on one line.
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium,
                        color = when { ticked -> Color.Transparent; now -> MaterialTheme.colorScheme.onPrimary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                    if (ticked) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    color = if (now) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
