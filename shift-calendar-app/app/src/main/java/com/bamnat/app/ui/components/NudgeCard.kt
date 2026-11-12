package com.bamnat.app.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * 2.0.0 [RVP-01] (검토 UIX-01·UIX-02, 보고서 5장 '안내 카드는 한 모양', 사용자 11-12 카드 '추천대로 전체 적용') What a tip, a reminder or a
 * warning looks like everywhere: one quiet ground for all of them, the meaning only in the colour of the icon on the left
 * ([tone]: [Tone.Plain] 안내, [Tone.Caution] 주의, [Tone.Good] 끝남; [Tone.Danger] only when it cannot be undone), one line of words
 * (a second line of [detail] at most; the words are never cut, and with big letters, when they do not fit beside it, the button goes under them), one text button
 * that goes where it says and one X. It is 56dp high on one line, so the
 * calendar keeps the top of the screen. It never fills its button: the page's one filled button stays the page's own thing
 * to do (보고서 5장 '화면마다 채운 단추 하나').
 *
 * 칸이 ([kani]) can say the line instead of the icon (검토 붙임 2 '캐릭터 한마디도 같은 띠 자리에').
 */
@Composable
fun NudgeCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Plain,
    icon: ImageVector? = null,
    detail: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    closeLabel: String = "이 안내 닫기",
    kani: KaniMood? = null,
    onClick: (() -> Unit)? = null,
    /** 2.0.0 [RVP-01] A small picture in the icon's place (what a permission gives, 1.39 FLOW-05), at most 40dp high. */
    lead: (@Composable () -> Unit)? = null,
) {
    val tint = when (tone) { Tone.Plain -> MaterialTheme.colorScheme.primary; else -> tone.color() }
    val mark = icon ?: when (tone) {
        Tone.Plain -> Icons.Outlined.Info
        Tone.Good -> Icons.Outlined.CheckCircle
        Tone.Caution, Tone.Danger -> Icons.Outlined.WarningAmber
    }
    val ground = MaterialTheme.colorScheme.surfaceContainerLow
    val box = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    val measurer = rememberTextMeasurer()
    // 갈래 P 3/3: two lines of words share them evenly ('잠금 화면에 알람 / 화면이 안 떠요', not '… 안 / 떠요').
    val textStyle = MaterialTheme.typography.bodyMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val detailStyle = MaterialTheme.typography.bodySmall
    val actionStyle = MaterialTheme.typography.labelLarge
    val showKani = kani != null && LocalShowKani.current
    val body: @Composable () -> Unit = {
        // 총괄 #445 검토 (조건 12): with big letters or on a narrow screen the button and the X left the words a sliver, and a line
        // like '잠금 화면에 알람 화면이 안 떠요' ended in '…'. The words are never cut now; there, the button goes under them.
        BoxWithConstraints {
            // 갈래 J (조건 12): a card with no button was held to two lines with big letters too; 'big' is worked out apart from the button.
            val density = LocalDensity.current
            val big = density.fontScale >= STACK_FONT_SCALE || maxWidth < STACK_WIDTH
            // 2.0.0 [RVP-45] (갈래 P 3/3 사진: 편 폭의 달력 칸은 320dp보다 좁아, 버튼 옆 두 줄이면 되는 '잠금 화면에 알람 화면이 안 떠요'의
            // '허용'도 아래로 내려가 띠가 세 줄 높이가 됐어요): the button goes under the words only when, beside it, they would take more
            // than two lines (two lines of words still sit in the 56dp, 조건 1); the words are never cut either way.
            val fits = action != null && big && with(density) {
                val button = maxOf(ButtonDefaults.MinWidth, measurer.measure(action, actionStyle, maxLines = 1, softWrap = false).size.width.toDp() + 24.dp)
                val side = 12.dp + (if (lead != null) 40.dp else if (showKani) 36.dp else 22.dp) + 10.dp + (if (onClose != null) 48.dp else 8.dp)
                val room = (maxWidth - button - side).roundToPx()
                fun lines(t: String?, style: androidx.compose.ui.text.TextStyle) = if (t.isNullOrBlank()) 0
                    else measurer.measure(t, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = room)).lineCount
                room >= MIN_WORDS.roundToPx() && lines(text, textStyle) + lines(detail, detailStyle) <= 2
            }
            val stacked = action != null && big && !fits
            val lead: @Composable () -> Unit = {
                if (lead != null) lead()
                else if (showKani) Kani(kani!!, night = isDarkSurface(), size = 36.dp)
                else Icon(mark, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            val words: @Composable (Modifier) -> Unit = { m ->
                Column(m.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
                    if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (big) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val close: @Composable () -> Unit = {
                if (onClose != null) IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, closeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!stacked) Row(
                Modifier.heightIn(min = NudgeHeight).padding(start = 12.dp, end = if (onClose != null) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lead()
                Spacer(Modifier.width(10.dp))
                words(Modifier.weight(1f))
                if (action != null) TextButton(onClick = onAction) { Text(action, maxLines = 1) }
                close()
            } else Column(Modifier.padding(start = 12.dp, end = if (onClose != null) 0.dp else 8.dp, bottom = 4.dp)) {
                Row(Modifier.heightIn(min = NudgeHeight), verticalAlignment = Alignment.CenterVertically) {
                    lead()
                    Spacer(Modifier.width(10.dp))
                    words(Modifier.weight(1f))
                    close()
                }
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) { Text(action!!) }
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = box, shape = MaterialTheme.shapes.medium, color = ground, content = body)
    else Surface(modifier = box, shape = MaterialTheme.shapes.medium, color = ground, content = body)
}

/** [RVP-01] The one-line nudge's height (검토 UIX-01 '높이 56dp 띠 하나'). */
val NudgeHeight = 56.dp

/** From these on, a [NudgeCard]'s button goes under its words (글자 130% and up, or narrower than a folded phone's cover screen). */
private const val STACK_FONT_SCALE = 1.3f
private val STACK_WIDTH = 320.dp
/** Narrower than this beside the button, the words go above it whatever their length. */
private val MIN_WORDS = 96.dp

/** [RVP-13] (검토 UIX-15) The tone that matches the old warning colours, for screens moving to [NudgeCard]. */
fun toneOf(warning: Boolean, danger: Boolean = false): Tone = when {
    danger -> Tone.Danger
    warning -> Tone.Caution
    else -> Tone.Plain
}

/** [RVP-13] A quiet text colour for a neutral explanation (검토 UIX-15: 중립 설명은 onSurfaceVariant, 주황은 '주의' 하나). */
val QuietInk: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
