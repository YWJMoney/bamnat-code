package com.bamnat.app.ui.village

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.core.plaza.MakerMark

/**
 * 1.39 [PLZC-40] 제작자 표식, one look for 광장 and 장터 (장터 스레드와 공용): the name in the mark's color with its badge, a tap to the
 * 프로필. Without a mark it is the plain name.
 */
@Composable
fun markColor(mark: MakerMark?): Color = when (mark?.tone) {
    MakerMark.Tone.GOLD -> if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFFFFD36B) else Color(0xFF9A6A00)
    MakerMark.Tone.STAR -> MaterialTheme.colorScheme.tertiary
    null -> MaterialTheme.colorScheme.onSurface
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** The badge alone (🏅 이달의 제작자), for a narrow card. */
@Composable
fun MakerBadge(mark: MakerMark, short: Boolean = false, modifier: Modifier = Modifier) {
    Text(if (short) mark.badge else "${mark.badge} ${mark.words}", style = MaterialTheme.typography.labelSmall, color = markColor(mark),
        fontWeight = FontWeight.Bold, modifier = modifier.semantics { contentDescription = mark.words })
}

/** A maker's name with its mark; [onProfile] null when there is no 프로필 to go to (or it is mine). */
@Composable
fun MakerName(
    name: String,
    mark: MakerMark?,
    onProfile: (() -> Unit)?,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    modifier: Modifier = Modifier,
    /** 2.0.0 [RVN-06] COMP-08 One line with … where the head is tight (광장 카드), so the ⋮ beside it stays. */
    maxLines: Int = Int.MAX_VALUE,
) {
    val speech = listOfNotNull(name, mark?.words, if (onProfile != null) "프로필 보기" else null).joinToString(", ")
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onProfile != null) modifier.clickable(onClickLabel = "프로필 보기", onClick = onProfile).heightIn(min = 32.dp) else modifier)
            .clearAndSetSemantics { contentDescription = speech }) {
        Text(name + if (onProfile != null) " ›" else "", style = style, color = if (mark != null) markColor(mark)
            else if (onProfile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (mark != null) FontWeight.Bold else null, maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        mark?.let { Spacer(Modifier.width(3.dp)); Text(it.badge, style = style) }
    }
}
