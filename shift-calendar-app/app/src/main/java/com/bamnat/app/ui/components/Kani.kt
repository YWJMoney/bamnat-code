package com.bamnat.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 1.38 [FLOW-26] 칸이's faces; 2.0.0 [RVP-59] (검토 14장, 사용자 11-12 시안 A '말랑 칸이') adds 안녕 (first open, 새 소식), 월급날 (pay
 * day) and 야간 근무 (a night shift's day), and every face is drawn again soft and round. [Point] shows the way on an empty
 * screen; [Worry] goes with an error or no connection; [Off] with a day off after a run of work.
 */
enum class KaniMood(val label: String) {
    Basic("기본"), Done("해냈어요"), Stretch("기지개"), Sleepy("졸려요"), Off("쉬는 날"), Point("여기예요"), Worry("걱정돼요"),
    Hello("안녕"), Payday("월급날"), NightShift("야간 근무");

    /** The headband's own day when no shift colour is given: sun, moon or leaf. */
    internal val day: KaniDay get() = when (this) { Sleepy, NightShift -> KaniDay.NIGHT; Off -> KaniDay.OFF; else -> KaniDay.DAY }
}

/** [RVP-59] The headband's own colour (top and bottom of its shine) and the pin on it: sun, sunset, moon or leaf. */
internal enum class KaniDay(val band: Color, val shine: Color) {
    DAY(Color(0xFFF6C24F), Color(0xFFFFE08A)), EVENING(Color(0xFFF28C6A), Color(0xFFFFB497)),
    NIGHT(Color(0xFF3C4AA6), Color(0xFF5B6AD0)), OFF(Color(0xFF4DBE93), Color(0xFF7BDDB4));
}

// [RVP-59] 말랑 칸이's colours (검토 14-3): a deep navy line instead of near-black, cream body, pink cheeks; the same family as
// the logo's navy, cream and gold (UIX-23), so the character also joins the icon to the app.
private val Line = Color(0xFF2B3157)
private val EyeInk = Color(0xFF20243F)
private val Cream = Color(0xFFFFF8EA)
private val Feet = Color(0xFFF3E6CC)
private val Cheek = Color(0xFFFF9FA6)
private val Mouth = Color(0xFF3A2540)
private val Gold = Color(0xFFF6C24F)
private val Mint = Color(0xFF4DBE93)
private val Leafy = Color(0xFF3FA57F)

/**
 * 1.38 [FLOW-26] 칸이: a day of the calendar with a face. 2.0.0 [RVP-59] 말랑 칸이 (검토 14-3·14-5): a soft square that is a little
 * fuller at the bottom, a navy line, shining eyes, small round hands and feet, a headband shaped like a fringe with a light
 * stripe and a pin (sun, sunset, moon, leaf), and the two calendar rings. The headband is the day's shift colour ([band]; the
 * mood's own colour when none is given). [night] only lightens the rings: the body is light, so one drawing serves both.
 * Drawn in code from `docs/brand/kani2/kani2-<mood>.svg` (viewBox 160), so it stays sharp at any [size] with no image files.
 *
 * [RVP-60] (검토 14-4) It is alive: it blinks every 4~6 seconds, breathes a little, gives one soft bounce when it comes in and
 * squishes when touched, all still when 움직임 끄기 or the phone's 애니메이션 삭제 is on ([com.bamnat.app.ui.theme.LocalMotionOff]).
 * [date] writes a day number on the headband (hidden under 40dp) and [tomorrow] folds the corner to show tomorrow's colour
 * (72dp and up); both wait for the user's word (검토 11장 18번) and no screen passes them yet.
 *
 * [RVP-32] (검토 UIX-55) It is a picture for the eye only: a screen reader skips it unless [speak] (where the face itself is the
 * message, such as the 디자인 시험 page) so an empty screen is not read twice. Callers check [com.bamnat.app.data.AppSettings.showKani].
 */
@Composable
fun Kani(
    mood: KaniMood,
    night: Boolean = false,
    band: Color = Color.Unspecified,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    speak: Boolean = false,
    date: Int? = null,
    tomorrow: Color? = null,
) {
    val shapes = remember(mood) { KaniShapes.of(mood) }
    val day = mood.day
    val headband = if (band == Color.Unspecified) day.band else band
    val shine = if (band == Color.Unspecified) day.shine else lerp(band, Color.White, 0.35f)
    val still = com.bamnat.app.ui.theme.LocalMotionOff.current
    // [RVP-60] One soft bounce in, a blink every 4~6 s and a slow breath; a touch squishes it.
    val drop = remember { Animatable(if (still) 0f else -10f) }
    val squish = remember { Animatable(1f) }
    var blink by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(still) {
        if (still) { drop.snapTo(0f); return@LaunchedEffect }
        drop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(still, mood) {
        blink = 1f
        if (still || !shapes.blinks) return@LaunchedEffect
        val r = java.util.Random(mood.ordinal.toLong() * 31 + System.nanoTime() % 997)
        while (true) {
            delay(4_000L + r.nextInt(2_000))
            for (f in listOf(0.55f, 0.1f, 0.55f, 1f)) { blink = f; delay(45) }
        }
    }
    // Read only while drawing, so a breath redraws the picture and never the screen around it.
    val breath = if (still) null else rememberInfiniteTransition(label = "kani").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Reverse), label = "breath",
    )
    val described = if (speak) Modifier.clearAndSetSemantics { contentDescription = "칸이, ${mood.label}"; role = Role.Image }
    else Modifier.clearAndSetSemantics { }
    val touch = if (still) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures(onPress = {
            scope.launch { squish.animateTo(0.9f, tween(90, easing = FastOutSlowInEasing)); squish.animateTo(1f, spring(dampingRatio = 0.4f)) }
        })
    }
    val big = size.value
    Canvas(modifier.size(size).then(described).then(touch)) {
        val k = this.size.minDimension / 160f
        scale(k, k, pivot = Offset.Zero) {
            // Breath and squish swell the body up from its feet; the bounce drops it in.
            val breathe = 1f + 0.018f * FastOutSlowInEasing.transform(breath?.value ?: 0f)
            translate(top = drop.value) {
                scale(scaleX = (2f - squish.value) * (1f + (breathe - 1f) * 0.4f), scaleY = squish.value * breathe, pivot = Offset(80f, 146f)) {
                    drawKani(shapes, mood, headband, shine, night, blink,
                        date = date?.takeIf { big >= 40f }, tomorrow = tomorrow?.takeIf { big >= 72f })
                }
            }
        }
    }
}

private fun round(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawKani(
    s: KaniShapes, mood: KaniMood, band: Color, shine: Color, night: Boolean, blink: Float, date: Int?, tomorrow: Color?,
) {
    // The shadow on the floor.
    drawOval(Color(0xFF12162C).copy(alpha = 0.13f), Offset(40f, 143.5f), Size(80f, 9f))
    // Hands behind the body, then feet.
    s.arms.forEach { a -> rotate(a.rot, Offset(a.cx, a.cy)) {
        drawOval(Cream, Offset(a.cx - a.rx, a.cy - a.ry), Size(a.rx * 2, a.ry * 2))
        drawOval(Line, Offset(a.cx - a.rx, a.cy - a.ry), Size(a.rx * 2, a.ry * 2), style = Stroke(3f))
    } }
    for (x in listOf(62f, 98f)) {
        drawOval(Feet, Offset(x - 11f, 135f), Size(22f, 13f))
        drawOval(Line, Offset(x - 11f, 135f), Size(22f, 13f), style = Stroke(3f))
    }
    drawPath(KaniShapes.body, Cream)
    clipPath(KaniShapes.body) {
        drawPath(KaniShapes.band, Brush.verticalGradient(listOf(shine, band), startY = 30f, endY = 68f))
        drawPath(KaniShapes.bandEdge, Line.copy(alpha = 0.28f), style = Stroke(2f))
        drawPath(KaniShapes.bandShine, Color.White.copy(alpha = 0.5f), style = round(3.2f))
        drawPin(mood.day)
        // The soft shade toward the bottom, so it reads as a little cushion, not a flat sticker.
        drawRect(Brush.verticalGradient(0.5f to Color(0x00E6C894), 1f to Color(0xFFE0BC80).copy(alpha = 0.5f), startY = 30f, endY = 145f),
            Offset(20f, 30f), Size(120f, 115f))
        if (tomorrow != null) drawPath(KaniShapes.cornerShade, tomorrow)
    }
    drawPath(KaniShapes.body, Line, style = Stroke(3f, join = StrokeJoin.Round))
    if (tomorrow != null) {
        drawPath(KaniShapes.corner, Color(0xFFFFFDF7))
        drawPath(KaniShapes.corner, Line, style = Stroke(2.4f, join = StrokeJoin.Round))
        drawPath(KaniShapes.cornerCrease, Color(0xFFE6D3AE), style = round(1.6f))
    }
    // The calendar's two rings, a step lighter on a dark page so they do not sink into it.
    val ring = if (night) Color(0xFF8490E0) else Line
    val ringShine = if (night) Color(0xFFDCE2FF) else Color(0xFF8C97D8)
    for (x in listOf(58f, 102f)) {
        drawRoundRect(ring, Offset(x - 5.5f, 25f), Size(11f, 22f), CornerRadius(5.5f))
        drawLine(ringShine, Offset(x - 2.2f, 29.5f), Offset(x - 2.2f, 38.5f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    if (mood == KaniMood.Off) {
        drawLine(Leafy, Offset(80f, 37f), Offset(80f, 27f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawPath(KaniShapes.sproutLeft, Color(0xFF63CFA2)); drawPath(KaniShapes.sproutLeft, Leafy, style = round(1.6f))
        drawPath(KaniShapes.sproutRight, Color(0xFF7BDDB4)); drawPath(KaniShapes.sproutRight, Leafy, style = round(1.6f))
    }
    if (date != null) drawDate(date, mood.day)
    val cheek = Cheek.copy(alpha = if (mood == KaniMood.Worry) 0.45f else 0.7f)
    drawOval(cheek, Offset(36.5f, 102.8f), Size(17f, 10.4f))
    drawOval(cheek, Offset(106.5f, 102.8f), Size(17f, 10.4f))
    s.face(this, blink)
    s.around(this)
}

private fun DrawScope.drawPin(day: KaniDay) {
    val p = KaniShapes
    when (day) {
        KaniDay.DAY -> {
            val ray = Color(0xFFFFF6D2)
            for (a in 0 until 360 step 45) rotate(a.toFloat(), Offset(113f, 53f)) {
                drawLine(ray, Offset(113f, 53f), Offset(113f, 45.2f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            drawCircle(ray, 4.8f, Offset(113f, 53f))
        }
        KaniDay.EVENING -> {
            drawPath(p.sunset, Color(0xFFFFF1D6))
            drawLine(Color(0xFFFFF1D6), Offset(103f, 62.5f), Offset(123f, 62.5f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        KaniDay.NIGHT -> {
            drawPath(p.moon, Color(0xFFFFE9A8))
            drawPath(star(49f, 52f, 2.6f), Color(0xFFFFE9A8).copy(alpha = 0.9f))
            drawPath(star(77f, 48.5f, 1.8f), Color(0xFFFFE9A8).copy(alpha = 0.75f))
        }
        KaniDay.OFF -> {
            drawPath(p.leaf, Color(0xFFEFFFF6))
            drawLine(Mint, Offset(108.5f, 58.5f), Offset(115.5f, 51.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
    }
}

/** [RVP-60] The day number on the headband (검토 14-3 '더할 수 있는 것' 1), drawn without a font as a plain bold text. */
private fun DrawScope.drawDate(date: Int, day: KaniDay) {
    val ink = if (day == KaniDay.DAY) android.graphics.Color.rgb(0x5B, 0x3F, 0x06) else android.graphics.Color.WHITE
    drawContext.canvas.nativeCanvas.drawText(date.toString(), 80f, 55f, android.graphics.Paint().apply {
        isAntiAlias = true; color = ink; textSize = 15f; textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    })
}

private fun star(cx: Float, cy: Float, r: Float): Path {
    val k = r * 0.32f
    return Path().apply {
        moveTo(cx, cy - r); lineTo(cx + k, cy - k); lineTo(cx + r, cy); lineTo(cx + k, cy + k)
        lineTo(cx, cy + r); lineTo(cx - k, cy + k); lineTo(cx - r, cy); lineTo(cx - k, cy - k); close()
    }
}

/** A hand: an ellipse at ([cx], [cy]) turned by [rot] degrees. */
private class Arm(val cx: Float, val cy: Float, val rot: Float, val rx: Float, val ry: Float)

private enum class Pose { DOWN, UP, POINT, FRONT }

/** The face, hands and things around one mood, parsed once from the SVG's own path data (`docs/brand/kani2/`). */
private class KaniShapes(
    left: Pose,
    right: Pose,
    /** Faces with open eyes blink; closed or arched ones do not. */
    val blinks: Boolean = false,
    val face: DrawScope.(blink: Float) -> Unit,
    val around: DrawScope.() -> Unit = {},
) {
    val arms: List<Arm> = listOfNotNull(arm(-1, left), arm(1, right))

    companion object {
        private fun p(d: String): Path = PathParser().parsePathString(d).toPath()
        val body = p("M80 36C100 36 116 37 124 44C131 50 133 63 133 88C133 112 134 125 127 132" +
            "C119 140 101 141 80 141C59 141 41 140 33 132C26 125 27 112 27 88C27 63 29 50 36 44C44 37 60 36 80 36Z")
        val band = p("M20 30H140V68Q80 50 20 68Z")
        val bandEdge = p("M20 68Q80 50 140 68")
        val bandShine = p("M38 50Q52 41 70 40")
        val sunset = p("M105 59a8 8 0 0 1 16 0z")
        val moon = p("M116 46.5a8.5 8.5 0 1 0 5.5 13.6a6.8 6.8 0 0 1 -5.5 -13.6z")
        val leaf = p("M107 60c0-8 6-12.5 13-12.5c0 8-5.5 12.5-13 12.5z")
        val sproutLeft = p("M80 28c-1-7-7-10-13-9c0 7 6 10.5 13 9z")
        val sproutRight = p("M80 28c1-6 6-9 12-8c0 6-5 9-12 8z")
        val cornerShade = p("M140 114L108 145H140Z")
        val corner = p("M133.5 116.5L110.5 140.5Q107.5 121 110.5 119Q113 116.5 133.5 116.5Z")
        val cornerCrease = p("M114 122.5q9 -1.5 14 -1.5")
        private val smile = p("M74 104.5q6 5.5 12 0")
        private val smileSide = p("M77 104.5q6 5.5 12 0")
        private val smallSmile = p("M75 100.5q5 4.5 10 0")
        private val openSmile = p("M72 103h16q0 10 -8 10q-8 0 -8 -10z")
        private val tongue = p("M75.5 110.5q4.5 -3.2 9 0q-1.6 2.5 -4.5 2.5q-2.9 0 -4.5 -2.5z")
        private val payMouth = p("M73 103.5h14q0 8.5 -7 8.5q-7 0 -7 -8.5z")
        private val brows = p("M51 89.5l12 -4M109 89.5l-12 -4")
        private val wobble = p("M73 108q3.5 -4 7 0t7 0")
        private val drop = p("M132 62q6 9 0 13.5q-6 -4.5 0 -13.5z")
        private val zBig = p("M126 30h10l-10 12h10")
        private val zSmall = p("M140 15h7l-7 8.5h7")
        private val note = p("M138 44v-11l9 -2.4v10.6")
        private val cup = p("M66 116h28v9a11 11 0 0 1 -11 11h-6a11 11 0 0 1 -11 -11z")
        private val heart = p("M80 127.2c-2.2 -2.6 -5.4 -0.4 -3.6 2.2l3.6 3.4l3.6 -3.4c1.8 -2.6 -1.4 -4.8 -3.6 -2.2z")
        private val won = p("M138.4 42.4l2.3 7.4l2.3 -6.2l2.3 6.2l2.3 -7.4M137.4 46h11.2")
        private val waves = listOf(p("M148 50q5 4 4 10"), p("M153 44q8 6 7 16"))
        private val stretchRays = listOf(p("M13 40l-7 -5"), p("M9 54h-8"), p("M147 40l7 -5"), p("M151 54h8"))

        private fun arm(side: Int, pose: Pose): Arm? = when (pose) {
            Pose.DOWN -> Arm(80f + side * 54, 107f, -side * 20f, 7.5f, 10.5f)
            Pose.UP -> Arm(80f + side * 56, 70f, side * 34f, 7.5f, 12f)
            Pose.POINT -> Arm(80f + side * 62, 100f, side * 104f, 7f, 15f)
            Pose.FRONT -> null
        }

        /** Open eye with two shines; [look] turns it sideways, [big] for 월급날. [blink] closes it from the middle. */
        private fun DrawScope.eye(cx: Float, cy: Float, blink: Float, look: Float = 0f, big: Boolean = false) {
            val rx = if (big) 7.2f else 6.4f
            val ry = (if (big) 8.2f else 7.4f) * blink
            drawOval(EyeInk, Offset(cx + look - rx, cy - ry), Size(rx * 2, ry * 2))
            if (blink > 0.5f) {
                drawCircle(Color.White, if (big) 3.1f else 2.5f, Offset(cx + look - 1.9f + look * 0.25f, cy - 2.8f * blink))
                drawCircle(Color.White.copy(alpha = 0.9f), 1.15f, Offset(cx + look + 2.3f, cy + 3f * blink))
            }
        }

        private fun DrawScope.arcEye(cx: Float, cy: Float, up: Boolean = true, h: Float = 7f, w: Float = 7f) {
            val s = if (up) -1f else 1f
            drawPath(Path().apply { moveTo(cx - w, cy); relativeQuadraticTo(w, s * h, 2 * w, 0f) }, EyeInk, style = round(3.2f))
        }

        private fun DrawScope.mouth(path: Path) = drawPath(path, EyeInk, style = round(2.8f))

        private fun DrawScope.stars(vararg s: Triple<Float, Float, Float>, color: Color = Gold) =
            s.forEach { (x, y, r) -> drawPath(star(x, y, r), color) }

        fun of(mood: KaniMood): KaniShapes = when (mood) {
            KaniMood.Basic -> KaniShapes(Pose.DOWN, Pose.DOWN, blinks = true, face = { b -> eye(59f, 97f, b); eye(101f, 97f, b); mouth(smile) })
            KaniMood.Hello -> KaniShapes(Pose.DOWN, Pose.UP, blinks = true, face = { b -> eye(59f, 97f, b); eye(101f, 97f, b); mouth(smile) },
                around = { waves.forEach { drawPath(it, Gold, style = round(2.6f)) } })
            KaniMood.Point -> KaniShapes(Pose.DOWN, Pose.POINT, blinks = true,
                face = { b -> eye(61f, 97f, b, look = 2.2f); eye(103f, 97f, b, look = 2.2f); mouth(smileSide) })
            KaniMood.Done -> KaniShapes(Pose.UP, Pose.UP, face = {
                arcEye(59f, 99f); arcEye(101f, 99f)
                drawPath(openSmile, Mouth); drawPath(openSmile, EyeInk, style = Stroke(2.4f, join = StrokeJoin.Round))
                drawPath(tongue, Color(0xFFFF8D97))
            }, around = {
                stars(Triple(22f, 40f, 8f), Triple(140f, 26f, 6f))
                drawPath(star(146f, 54f, 3.6f), Color(0xFFF28C6A)); drawPath(star(12f, 64f, 3.4f), Mint)
                drawCircle(Color(0xFF5B6AD0), 2.6f, Offset(34f, 20f)); drawCircle(Cheek, 2.2f, Offset(128f, 44f))
            })
            KaniMood.Stretch -> KaniShapes(Pose.UP, Pose.UP, face = {
                arcEye(59f, 96f, up = false, h = 6f); arcEye(101f, 96f, up = false, h = 6f)
                drawOval(Mouth, Offset(75f, 101f), Size(10f, 12f)); drawOval(EyeInk, Offset(75f, 101f), Size(10f, 12f), style = Stroke(2.2f))
            }, around = { stretchRays.forEach { drawPath(it, Gold, style = round(3f)) } })
            KaniMood.Sleepy -> KaniShapes(Pose.DOWN, Pose.DOWN, face = {
                arcEye(59f, 97f, up = false, h = 5.5f); arcEye(101f, 97f, up = false, h = 5.5f)
                drawOval(Mouth, Offset(76.8f, 103.2f), Size(6.4f, 7.6f))
            }, around = {
                drawPath(zBig, Color(0xFF8E9BE8), style = round(3f)); drawPath(zSmall, Color(0xFF8E9BE8), style = round(2.4f))
            })
            KaniMood.Off -> KaniShapes(Pose.DOWN, Pose.DOWN, face = { arcEye(59f, 99f, h = 6f); arcEye(101f, 99f, h = 6f); mouth(smile) }, around = {
                drawPath(note, Leafy, style = round(2.4f))
                drawOval(Leafy, Offset(132f, 41.8f), Size(6.6f, 5.2f)); drawOval(Leafy, Offset(141f, 39.2f), Size(6.6f, 5.2f))
            })
            KaniMood.Worry -> KaniShapes(Pose.DOWN, Pose.DOWN, blinks = true, face = { b ->
                for ((x, sx) in listOf(59f to 57.6f, 101f to 99.6f)) {
                    drawOval(EyeInk, Offset(x - 5f, 99f - 5.8f * b), Size(10f, 11.6f * b))
                    if (b > 0.5f) drawCircle(Color.White, 1.9f, Offset(sx, 97f))
                }
                mouth(brows); mouth(wobble)
            }, around = { drawPath(drop, Color(0xFF9DD1FF)); drawPath(drop, Line, style = Stroke(2f)) })
            KaniMood.Payday -> KaniShapes(Pose.DOWN, Pose.UP, blinks = true, face = { b ->
                eye(59f, 97f, b, big = true); eye(101f, 97f, b, big = true)
                if (b > 0.5f) {
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.3f, Offset(56f, 101.5f)); drawCircle(Color.White.copy(alpha = 0.9f), 1.3f, Offset(98f, 101.5f))
                }
                drawPath(payMouth, Mouth); drawPath(payMouth, EyeInk, style = Stroke(2.4f, join = StrokeJoin.Round))
            }, around = {
                drawCircle(Gold, 11f, Offset(143f, 46f)); drawCircle(Color(0xFFB9862A), 11f, Offset(143f, 46f), style = Stroke(2.4f))
                drawCircle(Color(0xFFFFE7A3), 7.4f, Offset(143f, 46f), style = Stroke(1.6f))
                drawPath(won, Color(0xFF8A5F12), style = round(1.5f))
                stars(Triple(126f, 28f, 4f)); drawPath(star(154f, 26f, 3f), Gold.copy(alpha = 0.8f))
            })
            KaniMood.NightShift -> KaniShapes(Pose.FRONT, Pose.FRONT, blinks = true,
                face = { b -> eye(59f, 93f, b); eye(101f, 93f, b); mouth(smallSmile) }, around = {
                    drawPath(cup, Color.White); drawPath(cup, Line, style = Stroke(2.8f, join = StrokeJoin.Round))
                    drawLine(Color(0xFFB9794A), Offset(69.5f, 120.4f), Offset(90.5f, 120.4f), strokeWidth = 3.4f, cap = StrokeCap.Round)
                    drawPath(heart, Cheek)
                    for (x in listOf(64f, 96f)) {
                        drawOval(Cream, Offset(x - 6.5f, 117f), Size(13f, 16f)); drawOval(Line, Offset(x - 6.5f, 117f), Size(13f, 16f), style = Stroke(2.8f))
                    }
                    drawPath(star(140f, 40f, 5f), Color(0xFFFFE9A8)); drawPath(star(150f, 58f, 3f), Color(0xFFFFE9A8).copy(alpha = 0.8f))
                })
        }
    }
}

/** 1.38 [FLOW-26] 설정 › 화면 › 칸이 보이기, handed down from MainActivity; false hides 칸이 everywhere. */
val LocalShowKani = androidx.compose.runtime.staticCompositionLocalOf { true }
