package com.bamnat.app.ui.recipe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.theme.ShiftPalette
import com.bamnat.core.layout.Contrast
import com.bamnat.core.recipe.RecipeText

/**
 * [RCP-18] 이모지 고르기 (설계서 4-6): every Unicode emoji the app knows (Emoji 15.0까지), by group, with 한국어·초성 검색 and the
 * skin-tone forms on a long list under the picked one. What it returns always passes [RecipeText.oneEmoji].
 */
@Composable
fun EmojiPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(RecipeText.emojiGroups.firstOrNull().orEmpty()) }
    var toned by remember { mutableStateOf<RecipeText.EmojiEntry?>(null) }
    val shown = remember(query, group) {
        if (query.isNotBlank()) RecipeText.searchEmoji(query, 240) else RecipeText.emoji.filter { it.group == group }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이모지 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("찾기 (예: 달, ㄷ, 커피)") })
                if (query.isBlank()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecipeText.emojiGroups.forEach { g -> FilterChip(selected = g == group, onClick = { group = g }, label = { Text(g) }) }
                    }
                }
                toned?.let { e ->
                    Text("피부색 고르기", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (listOf(e.emoji) + e.tones).forEach { t -> EmojiCell(t) { onPick(t) } }
                    }
                }
                if (shown.isEmpty()) Text("맞는 이모지가 없어요. 다른 말로 찾아 보세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = { it.emoji }) { e ->
                        EmojiCell(e.emoji) { if (e.tones.isEmpty()) onPick(e.emoji) else toned = e }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        dismissButton = onClear?.let { { TextButton(onClick = it) { Text("이모지 빼기") } } },
    )
}

@Composable
private fun EmojiCell(e: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "이 이모지 고르기", onClick = onClick)
            .semantics { contentDescription = e },
        contentAlignment = Alignment.Center,
    ) { Text(e, fontSize = 24.sp) }
}

/**
 * [RCP-18] 색 고르기 (설계서 4-6): the app's palette, a 색상환 (hue, then 채도·밝기 on a square) and 16진수. Below, how the colour
 * reads as text on the light calendar and what the dark calendar draws in its place ([Contrast.onBlack]), so the maker sees both.
 */
@Composable
fun ColorPickerDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit, title: String = "색 고르기") {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    fun current(): Long = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL
    fun set(c: Long) {
        val f = FloatArray(3); android.graphics.Color.colorToHSV(c.toInt(), f)
        hue = f[0]; sat = f[1]; value = f[2]; hex = hexOf(c)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShiftPalette.forEach { c ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.asColor()).clickable(onClickLabel = "이 색 고르기") { set(c) }
                            .semantics { contentDescription = hexOf(c) })
                    }
                }
                // 채도 (left → right) and 밝기 (top → bottom) of the hue below.
                val base = Color.hsv(hue, 1f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, base)))
                        .pointerInput(Unit) {
                            fun at(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f); value = 1f - (o.y / size.height).coerceIn(0f, 1f); hex = hexOf(current())
                            }
                            detectTapGestures { at(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f); value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                hex = hexOf(current())
                            }
                        }
                        .semantics {
                            contentDescription = "채도와 밝기"
                            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f, 19)
                            setProgress { v -> value = v.coerceIn(0f, 1f); hex = hexOf(current()); true }
                        },
                ) {
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(Color.White, 10.dp.toPx(), Offset(sat * size.width, (1 - value) * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                // 색상환 as a strip: the whole circle of hues, left to right.
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width * 360f).coerceIn(0f, 359.9f); hex = hexOf(current()) } }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ -> hue = (change.position.x / size.width * 360f).coerceIn(0f, 359.9f); hex = hexOf(current()) }
                        }
                        // [A11Y-04] TalkBack: 올리기·내리기 moves the hue, like the finger does.
                        .semantics {
                            contentDescription = "색상"
                            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(hue, 0f..359.9f, 35)
                            setProgress { v -> hue = v.coerceIn(0f, 359.9f); hex = hexOf(current()); true }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(Color.White, 11.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                OutlinedTextField(hex, { t ->
                    hex = t.uppercase().take(7)
                    parseHex(hex)?.let { c -> val f = FloatArray(3); android.graphics.Color.colorToHSV(c.toInt(), f); hue = f[0]; sat = f[1]; value = f[2] }
                }, singleLine = true, label = { Text("16진수 (#RRGGBB)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                val c = current()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Preview(c, Color.White, "밝은 달력")
                    Preview(Contrast.onBlack(c), Color(0xFF121212), "어두운 달력")
                }
                val ratio = Contrast.ratio(c or 0xFF000000L, 0xFFFFFFFFL)
                if (ratio < 4.5) Text("밝은 달력에서 글자로 쓰기엔 흐려요 (%.1f:1). 조금 어둡게 하면 잘 보여요".format(ratio),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("이 색으로") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun Preview(c: Long, bg: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 96.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(c.asColor()))
                Spacer(Modifier.width(6.dp))
                Text("토 6", color = c.asColor(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** A colour as #RRGGBB. */
fun hexOf(c: Long): String = "#%06X".format(c and 0xFFFFFF)

/** #RRGGBB (or RRGGBB) as an opaque colour; null when it is not one. */
fun parseHex(s: String): Long? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6 || t.any { it.uppercaseChar() !in "0123456789ABCDEF" }) return null
    return 0xFF000000L or t.toLong(16)
}

/** A swatch that opens [ColorPickerDialog]. */
@Composable
fun ColorSwatch(color: Long, label: String, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "$label 색 바꾸기") { open = true }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.asColor()).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center) {
            Text("", color = contentColorOn(color.asColor()))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
    if (open) ColorPickerDialog(color, { onPick(it); open = false }, { open = false }, "$label 색")
}

