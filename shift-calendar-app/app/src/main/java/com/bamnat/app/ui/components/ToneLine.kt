package com.bamnat.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * 2.0.0 [RVJ-19] 카드 안에서 한 줄로 알리는 글 (검토 HQ-12, 디자인 품질 조건 6·14, 갈래 P와 맞춘 모양). [NudgeCard]는 카드 한 장이라
 * 카드 안 줄에는 쓰지 못해요. 이 줄은 NudgeCard와 같은 아이콘(안내 ⓘ · 끝남 ✓ · 주의·위험 △)을 왼쪽에 두고 **뜻은 아이콘 색으로만**
 * 보여 줘요: 글은 조용한 색(위험만 본문 색)이라 한 화면에 여러 줄이 있어도 주황·빨강 글이 줄지어 서지 않고, 작은 글자의 대비도
 * 지켜져요. 화면 읽기는 '주의, …'로 한 번에 읽어요. 위험([Tone.Danger])은 법을 어겼거나 되돌릴 수 없는 것에만 쓰고, 미리 알리는
 * 것·권하는 것은 [Tone.Caution]이에요. [onClick]이 있으면 줄 전체가 48dp로 눌려요.
 */
@Composable
fun ToneLine(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Caution,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (tone == Tone.Plain) MaterialTheme.colorScheme.onSurfaceVariant else tone.color()
    val said = when (tone) { Tone.Caution -> "주의"; Tone.Danger -> "위험"; else -> null }
    val density = LocalDensity.current
    // The mark sits in the middle of the words' first line (its box is one line of [style] tall), so a second line of words does
    // not pull it down, and it grows with the words: at 글자 200% a 16dp mark beside 24sp words would be the one thing that says
    // '주의' and too small to see.
    val firstLine = with(density) { (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.4f).toDp() }
    val mark = with(density) { 16.sp.toDp() }
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = if (onClick != null) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
            Icon(when (tone) {
                Tone.Plain -> Icons.Outlined.Info
                Tone.Good -> Icons.Outlined.CheckCircle
                Tone.Caution, Tone.Danger -> Icons.Outlined.WarningAmber
            }, said, Modifier.size(mark), tint = tint)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = style, modifier = Modifier.weight(1f),
            color = if (tone == Tone.Danger) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
