package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.bamnat.app.tier.Tier

/** What TalkBack says for the badge: 프로 기능, and 잠김 when this phone cannot use it yet. */
fun proBadgeWords(locked: Boolean): String = if (locked) "프로 기능, 잠겨 있어요" else "프로 기능"

/**
 * [TIER-06] 1.25 묶음 Y (U18): "프로" beside every pro feature, before the 정식 출시 switch too, so people
 * know which ones they are; a lock is added only while this phone cannot use it. One badge for the whole
 * app: [com.bamnat.app.ui.money.ProPill] is this badge.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier, locked: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = proBadgeWords(locked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (locked) {
            Icon(Icons.Filled.Lock, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(Tier.PRO.label, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer, maxLines = 1, softWrap = false)
    }
}
