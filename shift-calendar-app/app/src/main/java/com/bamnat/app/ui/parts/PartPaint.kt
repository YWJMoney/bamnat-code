package com.bamnat.app.ui.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.addOutline
import com.bamnat.app.ui.theme.fillWith
import com.bamnat.app.ui.theme.todayMotion
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.PartStyle

/**
 * [BLU-05] 1.36 그리기 엔진: the blueprint the app draws with (settings `parts`, or the studio's draft in its preview). A part asks
 * [partStyle] for its style and lays it over what the older pieces drew: every field the blueprint leaves empty keeps the screen as it
 * was (docs/blueprint-format.md 1항 3).
 */
val LocalBlueprint = staticCompositionLocalOf { Blueprint.EMPTY }

/**
 * [BLU-12] PC 15차 (어두운 화면에서 바탕은 흰색 그대로인데 금액 글자만 옅은 회색): the blueprint with its `on: dark` lines still apart, so a
 * screen drawn on its own ground ([com.bamnat.app.ui.theme.ScreenSkin]: 화면 색·배경) folds them by that ground, not by the app's theme.
 */
val LocalBlueprintRaw = staticCompositionLocalOf { Blueprint.EMPTY }

@Composable
@ReadOnlyComposable
fun partStyle(id: String, states: Set<String> = emptySet()): PartStyle = LocalBlueprint.current.style(id, states)

fun Long.partColor(alpha: Int? = null): Color = Color(this.toInt()).let { c -> alpha?.let { c.copy(alpha = c.alpha * it.coerceIn(0, 100) / 100f) } ?: c }

/** The part's fill (with its strength), or null. */
val PartStyle.fillColor: Color? get() = fill?.partColor(fillA) ?: paint?.average?.partColor(fillA)

val PartStyle.lineColor: Color? get() = lineC?.partColor(lineA)

/** The part's corners over [default]; [default] itself when it sets none. */
fun PartStyle.shape(default: Shape): Shape {
    val c = corners
    if (c.all { it == null }) return default
    return RoundedCornerShape(topStart = (c[0] ?: 0).dp, topEnd = (c[1] ?: 0).dp, bottomEnd = (c[2] ?: 0).dp, bottomStart = (c[3] ?: 0).dp)
}

/** [MAT-05] Any font of the 1.36 글꼴 모음 or the user's own ([com.bamnat.app.ui.theme.AppFonts]); null for the app's own. */
fun fontOf(id: String?): FontFamily? = com.bamnat.app.ui.theme.AppFonts.family(id)

/** [base] with the part's letters over it. Size is in sp, so 큰 글자 still grows it. */
fun PartStyle.text(base: TextStyle = TextStyle.Default): TextStyle {
    // [BLU-05] A part with its own strong fill and no 글자 색 reads in black or white, whichever stands out on it (PC 1차 사용성 제안).
    val auto = if (ink == null) fillColor?.takeIf { it.alpha >= 0.5f }?.let { com.bamnat.app.ui.components.contentColorOn(it.copy(alpha = 1f)) } else null
    if (font == null && size == null && weight == null && ink == null && auto == null && track == null && leading == null && italic == null && talign == null &&
        tshadow == null && deco == null) return base
    val s = size?.sp ?: base.fontSize
    return base.merge(
        TextStyle(
            fontFamily = fontOf(font),
            fontSize = size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            fontWeight = weight?.let { FontWeight(it) },
            color = ink?.partColor() ?: auto ?: Color.Unspecified,
            letterSpacing = track?.let { (it / 100).em } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            lineHeight = leading?.let { if (s.isSp) (s.value * it / 100f).sp else (it / 100f).em } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            fontStyle = italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
            textAlign = when (talign) { "start" -> TextAlign.Start; "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Unspecified },
            // [BLU-08] 글자 그림자 (흐림·색·자리; 자리를 비우면 흐림의 반만큼 오른쪽 아래)와 밑줄·가운데 줄.
            shadow = textShadow(),
            textDecoration = when (deco) {
                "under" -> androidx.compose.ui.text.style.TextDecoration.Underline
                "strike" -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                "both" -> androidx.compose.ui.text.style.TextDecoration.combine(listOf(
                    androidx.compose.ui.text.style.TextDecoration.Underline, androidx.compose.ui.text.style.TextDecoration.LineThrough))
                "none" -> androidx.compose.ui.text.style.TextDecoration.None
                else -> null
            },
        ),
    )
}

/** [BLU-08] The letters' shadow. Compose takes px and [text] has no density, so dp go by a phone's usual 2.6 px each. */
fun PartStyle.textShadow(density: Float = 2.6f): androidx.compose.ui.graphics.Shadow? {
    val blur = tshadow?.takeIf { it > 0 } ?: return null
    val c = tshadowC?.partColor() ?: Color.Black.copy(alpha = 0.5f)
    val x = (tshadowX ?: blur / 2) * density
    val y = (tshadowY ?: blur / 2) * density
    return androidx.compose.ui.graphics.Shadow(c, Offset(x.toFloat(), y.toFloat()), (blur * density).toFloat())
}

/** The outline under the letters, as a second text drawn with a stroke ([com.bamnat.app.ui.parts.PartText]). */
fun PartStyle.outline(base: TextStyle): TextStyle? = stroke?.takeIf { it > 0 }?.let { w ->
    base.merge(TextStyle(color = strokeC?.partColor() ?: Color.Black, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = w.toFloat() * 2f)))
}

/**
 * The box of a part: 바깥 간격, 그림자, 투명도, 모양, 채우기, 선, 안 여백, in that order. [shape] is the part's shape as the older pieces
 * drew it; [inner] false leaves the padding to the caller (a cell that lays its own children).
 */
fun Modifier.partBox(s: PartStyle, shape: Shape = RoundedCornerShape(0.dp), inner: Boolean = true): Modifier {
    if (s.isEmpty) return this
    val sh = s.shape(shape)
    var m = this
    s.out?.let { m = m.padding(it.dp) }
    s.shadow?.takeIf { it > 0 }?.let { e ->
        val c = s.shadowC?.partColor() ?: Color.Black
        m = m.shadow(e.dp, sh, clip = false, ambientColor = c, spotColor = c)
    }
    s.alpha?.let { m = m.alpha(it / 100f) }
    // [MAT-01] 1.36 채우기 (그라데이션·무늬·질감·유리·셰이더) wins over the plain fill colour.
    if (s.paint != null) m = m.clip(sh).fillWith(s.paint, sh, alpha = (s.fillA ?: 100) / 100f)
    else s.fillColor?.let { m = m.clip(sh).background(it, sh) }
    m = m.partLine(s, sh)
    // [MAT-07] 부품 움직임.
    m = m.partMotion(s.motion, s.lineColor ?: s.fillColor ?: Color.White)
    if (inner) m = m.partPadding(s)
    return m
}

/** A solid line in [fill], [w] wide, inside [shape]'s edge. */
private fun Modifier.paintedLine(fill: com.bamnat.core.recipe.Fill, w: androidx.compose.ui.unit.Dp, shape: Shape): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
    clipPath(path) {
        drawPath(path, com.bamnat.app.ui.theme.lineBrushOf(fill, size), style = Stroke(w.toPx() * 2))
    }
}

/**
 * [MAT-07] A part's movement (숨쉬기·은은한 빛·도는 테·통통·반짝 지나가기) in [tint]. The app's 움직임 끄기 and the phone's 애니메이션 삭제
 * keep it still.
 */
fun Modifier.partMotion(kind: String?, tint: Color): Modifier = if (kind == null || kind == "none") this else composed {
    if (com.bamnat.app.ui.theme.LocalMotionOff.current) return@composed Modifier
    if (kind != "shimmer") return@composed Modifier.todayMotion(kind, tint)
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val v by t.animateFloat(-1f, 2f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800)), label = "v")
    Modifier.drawWithContent {
        drawContent()
        val x = size.width * v
        drawRect(androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
            start = Offset(x - size.width * 0.3f, 0f), end = Offset(x + size.width * 0.3f, size.height)))
    }
}

fun Modifier.partPadding(s: PartStyle): Modifier =
    if (s.padH == null && s.padV == null) this else padding(horizontal = (s.padH ?: 0).dp, vertical = (s.padV ?: 0).dp)

/** The part's line: solid as a border, dash·dot·double drawn over the content. */
fun Modifier.partLine(s: PartStyle, shape: Shape): Modifier {
    val kind = s.line ?: if (s.lineW != null || s.lineC != null) "solid" else return this
    if (kind == "none") return this
    val w = (s.lineW ?: 1.0).toFloat().dp
    val color = s.lineColor ?: Color.Gray
    // [MAT-02] 1.36 선 채우기: a gradient (or a fill's colour) along the line.
    s.linePaint?.let { lp -> if (kind == "solid") return paintedLine(lp, w, shape) }
    if (kind == "solid") return border(w, color, shape)
    val corners = s.corners.map { (it ?: 0).toFloat() }
    return drawWithContent {
        drawContent()
        val px = w.toPx()
        val r = CornerRadius(corners.maxOrNull()?.dp?.toPx() ?: 0f)
        val effect = when (kind) {
            "dash" -> PathEffect.dashPathEffect(floatArrayOf(px * 4, px * 3))
            "dot" -> PathEffect.dashPathEffect(floatArrayOf(px, px * 2))
            else -> null
        }
        drawRoundRect(color, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), r, style = Stroke(px, pathEffect = effect))
        if (kind == "double") {
            val gap = px * 3
            drawRoundRect(color, Offset(gap, gap), Size(size.width - gap * 2, size.height - gap * 2), r, style = Stroke(px))
        }
    }
}

/** 배치: the cross-axis alignment of a column's children. */
fun PartStyle.horizontal(default: Alignment.Horizontal): Alignment.Horizontal = when (align) {
    "start" -> Alignment.Start; "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> default
}

fun PartStyle.vertical(default: Alignment.Vertical): Alignment.Vertical = when (align) {
    "start" -> Alignment.Top; "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> default
}

/** 배치: the main-axis arrangement with the part's gap. */
fun PartStyle.arrangement(defaultGap: Int = 1): Arrangement.Horizontal {
    val g = (gap ?: defaultGap).dp
    return when (justify) {
        "center" -> Arrangement.spacedBy(g, Alignment.CenterHorizontally)
        "end" -> Arrangement.spacedBy(g, Alignment.End)
        "between" -> Arrangement.SpaceBetween
        "around" -> Arrangement.SpaceAround
        else -> Arrangement.spacedBy(g)
    }
}

fun PartStyle.verticalArrangement(defaultGap: Int = 1): Arrangement.Vertical {
    val g = (gap ?: defaultGap).dp
    return when (justify) {
        "center" -> Arrangement.spacedBy(g, Alignment.CenterVertically)
        "end" -> Arrangement.spacedBy(g, Alignment.Bottom)
        "between" -> Arrangement.SpaceBetween
        "around" -> Arrangement.SpaceAround
        else -> Arrangement.spacedBy(g)
    }
}

/** 겹침 배치의 자리. */
fun placeOf(p: String?, default: Alignment = Alignment.TopStart): Alignment = when (p) {
    "tl" -> Alignment.TopStart; "tc" -> Alignment.TopCenter; "tr" -> Alignment.TopEnd
    "ml" -> Alignment.CenterStart; "mc" -> Alignment.Center; "mr" -> Alignment.CenterEnd
    "bl" -> Alignment.BottomStart; "bc" -> Alignment.BottomCenter; "br" -> Alignment.BottomEnd
    else -> default
}

/**
 * [LINK-01] 1.39 A calendar day's states from what the day really has ([com.bamnat.core.schedule.DayWork]: 쉬는 날의 대근·특근은 일한 날,
 * 연차·대근·잔업·특근·직접 바꾼 날도 상태로). Every screen that draws a day with a blueprint goes through here.
 */
fun dayStates(work: com.bamnat.core.schedule.DayWork, dayOfWeek: java.time.DayOfWeek, holiday: Boolean, inMonth: Boolean, today: Boolean, selected: Boolean): Set<String> =
    dayStates(work.role?.key, dayOfWeek, holiday, inMonth, today, selected) + work.states

/** The states of a calendar day, as the blueprint names them. */
fun dayStates(role: String?, dayOfWeek: java.time.DayOfWeek, holiday: Boolean, inMonth: Boolean, today: Boolean, selected: Boolean): Set<String> = buildSet {
    role?.let { add("role.$it") }
    if (dayOfWeek == java.time.DayOfWeek.SATURDAY) add("sat")
    if (dayOfWeek == java.time.DayOfWeek.SUNDAY) add("sun")
    if (holiday) add("holiday")
    if (!inMonth) add("dim")
    if (today) add("today")
    if (selected) add("sel")
}

/** A text in a part's letters, its outline drawn under it when the part has one. */
@Composable
fun PartText(
    text: String, part: PartStyle, base: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val style = part.text(base)
    val lines = part.lines ?: maxLines
    androidx.compose.foundation.layout.Box(modifier) {
        part.outline(style)?.let { androidx.compose.material3.Text(text, style = it, maxLines = lines, overflow = overflow) }
        androidx.compose.material3.Text(text, style = style, maxLines = lines, overflow = overflow)
    }
}

/** [BLU-05] A day cell's parts, found once per cell: the cell and the pieces inside it. */
class CellParts(val cell: PartStyle, val date: PartStyle, val mark: PartStyle, val lines: Map<String, PartStyle>, val order: List<String>) {
    companion object {
        val LINE_IDS = listOf("memo", "money", "hours", "spend", "lunar", "holiday")

        /** The parts for a cell in [states], or null when the blueprint leaves the cell's inside alone. */
        fun of(bp: Blueprint, states: Set<String>, scope: com.bamnat.core.formula.FormulaScope? = null): CellParts? {
            val inside = listOf("date", "mark") + LINE_IDS
            val cell = bp.style("cal.cell", states, scope)
            if (!cell.hasLayout && inside.none { bp.touches("cal.cell.$it") } && cell.padH == null && cell.padV == null) return null
            return CellParts(
                cell, bp.style("cal.cell.date", states, scope), bp.style("cal.cell.mark", states, scope),
                LINE_IDS.associateWith { bp.style("cal.cell.$it", states, scope) }, bp.childOrder("cal.cell", states),
            )
        }
    }
}

/** The engine's helpers by one name, for screens that do not import them one by one. */
object PartMods {
    fun box(s: PartStyle, shape: Shape): Modifier = Modifier.partBox(s, shape)
    fun text(s: PartStyle, base: TextStyle): TextStyle = s.text(base)
    fun shapeOf(s: PartStyle, default: Shape): Shape = s.shape(default)
}

@Composable
fun PartStyle.fillColorOr(default: Color): Color = fillColor ?: default

/**
 * [BLU-05] The app-wide parts as the theme: 앱 전체 (바탕·글자·글꼴), 단추 (강조색), 카드 (색·모서리), 칩·아래 창 (모서리). The screens keep
 * drawing with MaterialTheme, so every card, chip and button of the app follows without each screen asking.
 */
object PartTheme {
    private fun on(c: Color): Color = if (c.luminance() > 0.45f) Color.Black else Color.White

    fun colors(c: androidx.compose.material3.ColorScheme, bp: Blueprint): androidx.compose.material3.ColorScheme {
        if (bp.isEmpty) return c
        val app = bp.own("app")
        val button = bp.own("app.button")
        val card = bp.own("app.card")
        var s = c
        app.fillColor?.let { s = s.copy(background = it, surface = it, onBackground = on(it), onSurface = on(it)) }
        app.ink?.partColor()?.let { s = s.copy(onBackground = it, onSurface = it) }
        button.fillColor?.let { s = s.copy(primary = it, onPrimary = button.ink?.partColor() ?: on(it)) }
        card.fillColor?.let { s = s.copy(surfaceContainerLow = it, surfaceContainer = it, surfaceContainerHigh = it) }
        return s
    }

    fun shapes(base: androidx.compose.material3.Shapes, bp: Blueprint): androidx.compose.material3.Shapes {
        if (bp.isEmpty) return base
        return base.copy(
            small = bp.own("app.chip").shape(base.small) as? androidx.compose.foundation.shape.CornerBasedShape ?: base.small,
            medium = bp.own("app.card").shape(base.medium) as? androidx.compose.foundation.shape.CornerBasedShape ?: base.medium,
            extraLarge = bp.own("app.sheet").shape(base.extraLarge) as? androidx.compose.foundation.shape.CornerBasedShape ?: base.extraLarge,
        )
    }

    fun typography(t: androidx.compose.material3.Typography, bp: Blueprint): androidx.compose.material3.Typography {
        val f = fontOf(bp.own("app").font) ?: return t
        fun TextStyle.f() = copy(fontFamily = f)
        return t.copy(
            displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
            headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
            titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
            bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
            labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
        )
    }
}
