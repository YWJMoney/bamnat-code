package com.bamnat.app.ui.decor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorDay
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind

/** [LAY-03] The 꾸밈 층 the screens draw: the phone's own, or the example's in 스튜디오·입어 보기. */
val LocalDecor = staticCompositionLocalOf { Decor() }

/**
 * [LAY-03] 1.36 꾸밈 그리기: [anchor]'s decorations drawn over this part — under its content (the date, the work, the words), or over
 * it for the ones whose maker asked ([DecorItem.over]). For a day cell, [day] says which stand on it. Drawing only: no touch is taken and
 * TalkBack reads nothing of it. With nothing on [anchor] the modifier does nothing.
 */
@Composable
fun Modifier.decor(anchor: String, day: DecorDay? = null, layer: Decor = LocalDecor.current): Modifier {
    if (!layer.has(anchor)) return this
    val items = remember(layer, anchor, day) { layer.on(anchor).filter { day == null || it.standsOn(day) } }
    if (items.isEmpty()) return this
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val under = items.filter { !it.over }
    val over = items.filter { it.over }
    return drawWithContent {
        val box = Rect(Offset.Zero, size)
        under.forEach { drawDecor(it, box, measurer, accent, ink) }
        drawContent()
        over.forEach { drawDecor(it, box, measurer, accent, ink) }
    }
}

private val iconPaths = HashMap<String, Path?>()

/** An icon's outline in its 24×24 box, made once. */
internal fun iconPath(id: String): Path? = synchronized(iconPaths) {
    iconPaths.getOrPut(id) {
        DecorIcons.of(id)?.let { v ->
            val out = Path()
            fun walk(g: androidx.compose.ui.graphics.vector.VectorGroup) {
                g.forEach { n ->
                    when (n) {
                        is androidx.compose.ui.graphics.vector.VectorPath -> out.addPath(n.pathData.toPath())
                        is androidx.compose.ui.graphics.vector.VectorGroup -> walk(n)
                    }
                }
            }
            walk(v.root)
            out
        }
    }
}

/** Where [item] stands in [box], in px: its centre and its size. */
internal fun DrawScope.placeOf(item: DecorItem, box: Rect): Rect {
    val w = item.w * density
    val h = item.h * density
    val cx = box.left + box.width * item.x / 1000f
    val cy = box.top + box.height * item.y / 1000f
    return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}

private fun Color.times(a: Float) = copy(alpha = (alpha * a).coerceIn(0f, 1f))

/**
 * [LAY-03] One decoration drawn in [box] (the part's own bounds). [accent] is a shape's colour when it has none chosen, [ink] an icon's
 * or a text's.
 */
fun DrawScope.drawDecor(item: DecorItem, box: Rect, measurer: TextMeasurer, accent: Color, ink: Color) {
    val at = placeOf(item, box)
    val a = item.alpha / 100f
    if (a <= 0f) return
    val fill = (item.fill?.let { Color(it.toInt()) } ?: accent).times(item.fillA / 100f * a)
    val lineColor = (item.lineC?.let { Color(it.toInt()) } ?: ink).times(a)
    val lineW = item.lineW.toFloat() * density
    val stroke = item.line?.takeIf { it != "none" && lineW > 0f }?.let { kind -> strokeOf(kind, lineW) }
    translate(at.left, at.top) {
        rotate(item.rot.toFloat(), pivot = Offset(at.width / 2, at.height / 2)) {
            scale(if (item.flip) -1f else 1f, 1f, pivot = Offset(at.width / 2, at.height / 2)) {
                val shadow = item.shadow * density
                when (item.kind) {
                    DecorKind.SHAPE -> {
                        val path = DecorShapes.path(item.glyph, at.width, at.height, item.r * density)
                        if (item.glyph in DecorShapes.STROKES) {
                            val w = if (lineW > 0f) lineW else 2f * density
                            val c = item.lineC?.let { Color(it.toInt()).times(a) } ?: fill
                            if (shadow > 0f) translate(shadow / 2, shadow / 2) { drawPath(path, Color.Black.times(0.22f * a), style = strokeOf(item.line ?: "solid", w)) }
                            drawPath(path, c, style = strokeOf(item.line?.takeIf { it != "none" } ?: if (item.glyph == "dash_h") "dash" else "solid", w))
                        } else {
                            if (shadow > 0f) translate(shadow / 2, shadow / 2) { drawPath(path, Color.Black.times(0.22f * a)) }
                            drawPath(path, fill, style = Fill)
                            stroke?.let { s -> drawPath(path, lineColor, style = s); if (item.line == "double") drawInner(path, lineColor, lineW) }
                        }
                    }
                    DecorKind.ICON -> iconPath(item.glyph)?.let { p ->
                        val c = (item.ink ?: item.fill)?.let { Color(it.toInt()).times(a) } ?: ink.times(a)
                        val s = minOf(at.width, at.height) / 24f
                        translate((at.width - 24f * s) / 2, (at.height - 24f * s) / 2) {
                            scale(s, s, pivot = Offset.Zero) {
                                if (shadow > 0f) translate(shadow / 2 / s, shadow / 2 / s) { drawPath(p, Color.Black.times(0.22f * a)) }
                                drawPath(p, c)
                            }
                        }
                    }
                    DecorKind.EMOJI -> {
                        val px = minOf(at.width, at.height) * 0.82f
                        val layout = measurer.measure(item.glyph, TextStyle(fontSize = (px / fontScale / density).sp, color = Color.Black),
                            constraints = Constraints(maxWidth = (at.width * 2).toInt().coerceAtLeast(1)))
                        drawText(layout, topLeft = Offset((at.width - layout.size.width) / 2, (at.height - layout.size.height) / 2), alpha = a)
                    }
                    DecorKind.TEXT -> {
                        if (item.fill != null || stroke != null) {
                            val path = DecorShapes.path("rect", at.width, at.height, item.r * density)
                            if (item.fill != null) drawPath(path, fill)
                            stroke?.let { drawPath(path, lineColor, style = it) }
                        }
                        val c = item.ink?.let { Color(it.toInt()) } ?: ink
                        val style = TextStyle(color = c.times(a), fontSize = item.size.sp, fontWeight = FontWeight(item.weight),
                            fontStyle = if (item.italic) FontStyle.Italic else FontStyle.Normal, fontFamily = familyOf(item.font), textAlign = TextAlign.Center)
                        val layout = measurer.measure(item.glyph, style, constraints = Constraints(maxWidth = at.width.toInt().coerceAtLeast(1)), maxLines = 4)
                        if (shadow > 0f) drawText(measurer, item.glyph, Offset((at.width - layout.size.width) / 2 + shadow / 2, (at.height - layout.size.height) / 2 + shadow / 2),
                            style.copy(color = Color.Black.times(0.25f * a)), size = Size(at.width, at.height + layout.size.height))
                        drawText(layout, topLeft = Offset((at.width - layout.size.width) / 2, (at.height - layout.size.height) / 2))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawInner(path: Path, c: Color, w: Float) {
    // 겹선: a thin second line inside the first.
    val b = path.getBounds()
    val k = 3 * w
    if (b.width <= 2 * k || b.height <= 2 * k) return
    scale((b.width - 2 * k) / b.width, (b.height - 2 * k) / b.height, pivot = b.center) { drawPath(path, c, style = Stroke(w * 0.6f)) }
}

private fun strokeOf(kind: String, w: Float): Stroke = when (kind) {
    "dash" -> Stroke(w, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2)))
    "dot" -> Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, w * 2)))
    else -> Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/** 형식 1판's fonts; any other id (갈래 B's) is drawn in the app's own until B's catalog reads it. */
internal fun familyOf(id: String?): FontFamily = when (id) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "hand" -> FontFamily.Cursive
    "round" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

/** [LAY-03] A day cell's facts for its decorations. */
fun decorDay(date: java.time.LocalDate, role: String?, today: Boolean, dim: Boolean = false): DecorDay =
    DecorDay(date.monthValue, date.dayOfMonth, date.dayOfWeek.value, role, today, com.bamnat.core.holiday.KoreanHolidays.nameOf(date) != null, dim)

/** [LAY-03] For call sites that would rather not import the extension: `Modifier.then(with(DecorMod) { decorOf(anchor) })`. */
object DecorMod {
    @Composable
    fun decorOf(anchor: String, day: DecorDay? = null): Modifier = Modifier.decor(anchor, day)
}

/**
 * [LAY-03] A screen's background decorations ([Decor.APP] and "bg." + [screenKey]) as a layer the size of its parent: [over] false
 * for the ones under the screen's words, true for the ones its maker put over them.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ScreenDecor(screenKey: String, over: Boolean) {
    val layer = LocalDecor.current
    if (layer.isDefault) return
    val bg = "bg." + screenKey.substringBefore('.')
    val shown = remember(layer, bg, over) {
        Decor(layer.items.filter { (it.at == Decor.APP || it.at == bg) && it.over == over && !it.hide }.map { it.copy(over = false, at = Decor.APP) })
    }
    if (shown.isDefault || bg.removePrefix("bg.") !in Decor.SCREENS) return
    androidx.compose.foundation.layout.Box(Modifier.matchParentSize().decor(Decor.APP, layer = shown))
}
