package com.bamnat.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.core.schedule.MinuteNudge

/**
 * [EASE-26] 1.38: +10분·+30분·+1시간 (and − to take some off, 조퇴) as one row of chips, so a length is made with a tap or two
 * instead of a picker. [what] names the thing for TalkBack ("잔업", "근무 끝"). One line that scrolls sideways under a big font.
 */
@Composable
internal fun MinuteNudges(
    what: String,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier,
    steps: List<Int> = EXTRA_STEPS,
    lead: String? = null,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        for (m in steps) {
            val label = MinuteNudge.label(m)
            AssistChip(
                onClick = { onNudge(m) },
                label = { ChipText(label) },
                modifier = Modifier.heightIn(min = 40.dp).semantics {
                    contentDescription = "$what ${kotlin.math.abs(m).let { if (it % 60 == 0) "${it / 60}시간" else "${it}분" }} ${if (m < 0) "줄이기" else "늘리기"}"
                },
            )
        }
    }
}

/** Extra work: a little off, or 10분·30분·1시간 more. */
internal val EXTRA_STEPS = listOf(-30, 10, 30, 60)

/** The day's own end: 조퇴 an hour, half an hour or 10분 earlier, or 30분 later. */
internal val END_STEPS = listOf(-60, -30, -10, 30)
