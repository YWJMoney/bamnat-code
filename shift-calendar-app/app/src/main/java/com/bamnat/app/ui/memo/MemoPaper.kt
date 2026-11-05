package com.bamnat.app.ui.memo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import com.bamnat.app.ui.parts.fillColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.parts.lineColor
import com.bamnat.app.ui.parts.partStyle
import com.bamnat.core.memo.MemoPhotos.PaperKind

/**
 * [MPH-05][MPH-06] 1.38 메모 종이: how the lines behind the words look. Without a theme it is the app's own soft line; a theme's 부품
 * '메모 종이 줄' (memo.page.rule) gives the colour, thickness and kind (실선·점선·점) and, with its 선 set, makes every memo without its
 * own 종이 a ruled one (a diary theme reaches the writing page).
 */
@Immutable
data class MemoPaperLook(val kind: PaperKind, val color: Color, val width: Float, val dash: String? = null)

/** The paper a memo is drawn on: its own choice, else the theme's ruled page, else none. */
@Composable
@ReadOnlyComposable
internal fun memoPaperLook(own: PaperKind?): MemoPaperLook? {
    val rule = partStyle("memo.page.rule")
    val themed = rule.line?.takeIf { it != "none" }
    val kind = own ?: (if (themed != null) PaperKind.LINES else null) ?: return null
    val color = rule.lineColor ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    return MemoPaperLook(kind, color, (rule.lineW ?: 1.0).toFloat().coerceIn(0.5f, 4f), themed)
}

/**
 * Draws [look] behind a text field: a line under every line of words ([layout]), and on down to the field's end at the same step, so
 * the words sit on the lines however big the letters are.
 */
internal fun Modifier.memoPaper(look: MemoPaperLook?, layout: () -> TextLayoutResult?, fallbackStep: Float): Modifier {
    if (look == null) return this
    return drawBehind {
        val l = layout()
        val step = l?.takeIf { it.lineCount > 0 }?.let { it.getLineBottom(0) - it.getLineTop(0) }?.takeIf { it > 4f } ?: fallbackStep
        val ys = ArrayList<Float>()
        if (l != null) for (i in 0 until l.lineCount) ys += l.getLineBottom(i)
        var y = (ys.lastOrNull() ?: 0f) + step
        if (ys.isEmpty()) y = step
        while (y <= size.height + 0.5f) { ys += y; y += step }
        val stroke = look.width * density
        val effect = if (look.dash == "dash") PathEffect.dashPathEffect(floatArrayOf(6 * density, 4 * density)) else null
        when (look.kind) {
            PaperKind.LINES -> ys.forEach { yy ->
                if (look.dash == "dot") {
                    var x = 0f
                    while (x <= size.width) { drawCircle(look.color, stroke, Offset(x, yy)); x += 4 * density }
                } else drawLine(look.color, Offset(0f, yy), Offset(size.width, yy), stroke, pathEffect = effect)
            }
            PaperKind.GRID -> {
                ys.forEach { yy -> drawLine(look.color, Offset(0f, yy), Offset(size.width, yy), stroke * 0.8f, pathEffect = effect) }
                var x = 0f
                while (x <= size.width) { drawLine(look.color, Offset(x, 0f), Offset(x, size.height), stroke * 0.8f, pathEffect = effect); x += step }
            }
            PaperKind.DOTS -> ys.forEach { yy ->
                var x = step / 2
                while (x <= size.width) { drawCircle(look.color, stroke * 1.2f, Offset(x, yy)); x += step }
            }
        }
    }
}

/** [MPH-05] ⋮ › 종이: 없음, 줄 노트, 모눈, 점. */
@Composable
internal fun MemoPaperDialog(current: PaperKind?, themed: Boolean, onPick: (PaperKind?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모 종이") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val choices = listOf<Pair<PaperKind?, String>>(null to (if (themed) "테마 종이 그대로" else "없음(빈 종이)")) +
                    listOf(PaperKind.LINES to "줄 노트", PaperKind.GRID to "모눈", PaperKind.DOTS to "점")
                choices.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(k) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == k, onClick = { onPick(k) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text("줄은 글자 크기에 맞춰 그려져 글이 줄 위에 앉아요. 줄 색·굵기는 스튜디오 › 메모 › 부품 ‘메모 종이 줄’에서 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}


/**
 * [MPH-06] The studio's example of the writing page (부품 '메모 쓰기 종이'·'메모 종이 줄'·'메모 사진'), with made-up words and a grey
 * picture in place of a photo; nothing here is the user's data.
 */
@Composable
internal fun MemoPageSample() {
    val page = partStyle("memo.page")
    val look = memoPaperLook(null) ?: MemoPaperLook(PaperKind.LINES, MaterialTheme.colorScheme.outlineVariant, 1f)
    val photo = partStyle("memo.photo")
    val shape = com.bamnat.app.ui.parts.PartMods.shapeOf(photo, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
    val style = com.bamnat.app.ui.parts.PartMods.text(page, MaterialTheme.typography.bodyLarge)
    val ground = page.fillColor ?: MaterialTheme.colorScheme.surfaceContainerLow
    Column(Modifier.fillMaxWidth().then(androidx.compose.ui.Modifier.drawBehind { drawRect(ground) }).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            androidx.compose.foundation.layout.Box(
                Modifier.width(96.dp).then(com.bamnat.app.ui.parts.PartMods.box(photo, shape)).aspectRatio(1f)
                    .clip(shape).drawBehind { drawRect(Color(0xFF9EB7C7)) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            var layout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<TextLayoutResult?>(null) }
            Text("바다에 다녀왔어요.\n물이 맑았고\n다음엔 야간 끝나고 또!", style = style, onTextLayout = { layout = it },
                modifier = Modifier.weight(1f).memoPaper(look, { layout }, 24f))
        }
    }
}
