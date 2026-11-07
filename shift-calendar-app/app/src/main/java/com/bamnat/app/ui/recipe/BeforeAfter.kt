package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.recipe.RecipePieces

/**
 * 1.39 [MKP-05] 기능 조각 전후 (사용자 2026-11-07 "기능 조각들도 어떠한지 전후를 알 수 있으면 좋겠는데"): what a piece touches on this phone,
 * 지금 ↔ 넣으면, side by side. What changes is bright with a mark; what stays is dimmed and says 그대로. Only [fold] rows show at first.
 *
 * 큰 글자 (1.39 G1 큰 글자 배치): when three columns would not fit, each row goes in two lines (이름, then 지금 → 넣으면).
 */
@Composable
internal fun BeforeAfter(sides: List<RecipePieces.Side>, modifier: Modifier = Modifier, fold: Int = 4, title: Boolean = true) {
    if (sides.isEmpty()) return
    val changed = sides.count { it.changed }
    var open by rememberSaveable(sides.size) { mutableStateOf(false) }
    // What changes first, then what stays, so a folded table shows the changes.
    val ordered = sides.sortedBy { if (it.changed) 0 else 1 }
    val shown = if (open || ordered.size <= fold + 1) ordered else ordered.take(fold)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title) Text(if (changed == 0) "넣어도 지금과 같아요" else "지금 ↔ 넣으면 · 바뀌는 곳 ${changed}곳", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = LocalDensity.current.fontScale
            val narrow = maxWidth / scale < 300.dp
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!narrow) Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1.1f))
                    Text("지금", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("넣으면", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                shown.forEach { s -> SideRow(s, narrow) }
            }
        }
        if (ordered.size > shown.size || open && ordered.size > fold + 1) TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (open) "접기" else "${ordered.size - shown.size}곳 더 보기")
        }
    }
}

@Composable
private fun SideRow(s: RecipePieces.Side, narrow: Boolean) {
    val said = "${s.what}: 지금 ${s.now.ifEmpty { "색" }}, 넣으면 " + if (s.changed) s.then.ifEmpty { "다른 색" } else "그대로"
    val dim = if (s.changed) 1f else 0.6f
    if (narrow) Column(Modifier.fillMaxWidth().alpha(dim).clearAndSetSemantics { contentDescription = said }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text((if (s.changed) "• " else "") + s.what, style = MaterialTheme.typography.bodyMedium, fontWeight = if (s.changed) FontWeight.Bold else null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Cell(s.now, s.nowColor, false, Modifier.weight(1f, fill = false))
            Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Cell(if (s.changed) s.then else "그대로", if (s.changed) s.thenColor else null, s.changed, Modifier.weight(1f, fill = false))
        }
    } else Row(Modifier.fillMaxWidth().alpha(dim).clearAndSetSemantics { contentDescription = said }, verticalAlignment = Alignment.CenterVertically) {
        Text((if (s.changed) "• " else "") + s.what, Modifier.weight(1.1f).padding(end = 6.dp), style = MaterialTheme.typography.bodySmall,
            fontWeight = if (s.changed) FontWeight.Bold else null)
        Cell(s.now, s.nowColor, false, Modifier.weight(1f).padding(end = 6.dp))
        Cell(if (s.changed) s.then else "그대로", if (s.changed) s.thenColor else null, s.changed, Modifier.weight(1f))
    }
}

@Composable
private fun Cell(text: String, color: Long?, strong: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        color?.let {
            androidx.compose.foundation.layout.Box(Modifier.size(14.dp).clip(CircleShape).background(Color(it))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            if (text.isNotEmpty()) Spacer(Modifier.width(2.dp))
        }
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (strong) FontWeight.Bold else null)
    }
}
