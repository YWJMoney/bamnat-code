package com.bamnat.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVP-04] (검토 UIX-18, 더할 것 I13a-02) Where a list from the server will be, shown while it comes: grey rows the shape of the
 * rows to come (a dot, a long line and a short one), gently breathing unless 움직임 끄기. The list does not jump when it arrives
 * and a slow connection does not look like an empty page, unlike a lone spinner. A screen reader hears "불러오는 중" once.
 */
@Composable
fun SkeletonRows(count: Int = 3, modifier: Modifier = Modifier, dot: Boolean = true) {
    val still = com.bamnat.app.ui.theme.LocalMotionOff.current
    val glow = if (still) null else rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    val grey = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxWidth().semantics { contentDescription = "불러오는 중" }.graphicsLayer { alpha = glow?.value ?: 1f },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dot) Box(Modifier.size(36.dp).clip(CircleShape).background(grey))
                Column(Modifier.weight(1f).padding(start = if (dot) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.8f else 0.65f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(grey))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).background(grey))
                }
            }
        }
    }
}
