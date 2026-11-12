package com.bamnat.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bamnat.core.recipe.Motion
import kotlin.math.abs
import kotlin.math.hypot

/** [MAT-07] Whether the phone's own 'remove animations' (애니메이션 삭제, 배율 0) is on: the app never moves against it. */
fun systemAnimationsOff(c: Context): Boolean =
    runCatching { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/**
 * [MAT-07] 1.36 입히기 전환: when [key] (the looks being drawn) changes, the old ground [ground] goes away as [Motion.wear] says —
 * 스르륵, 닦아 내기, 동그랗게 퍼지기 — over the new look, without rebuilding what is under it (J135-15: one shape of tree).
 */
@Composable
fun WearTransition(key: Any, ground: Color, motion: Motion, content: @Composable () -> Unit) {
    var last by remember { mutableStateOf(key) }
    var old by remember { mutableStateOf(ground) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == last) return@LaunchedEffect
        last = key
        if (motion.wear == "none" || motion.off) { old = ground; return@LaunchedEffect }
        p.snapTo(1f)
        p.animateTo(0f, tween(motion.millis(480), easing = FastOutSlowInEasing))
        old = ground
    }
    LaunchedEffect(ground) { if (!p.isRunning) old = ground }
    Box(Modifier.drawWithContent {
        drawContent()
        val a = p.value
        if (a <= 0f) return@drawWithContent
        when (motion.wear) {
            "wipe" -> drawRect(old, topLeft = Offset(size.width * (1 - a), 0f), size = Size(size.width * a, size.height))
            "circle" -> {
                val r = hypot(size.width, size.height) * (1 - a)
                val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                clipPath(hole, ClipOp.Difference) { drawRect(old) }
            }
            else -> drawRect(old, alpha = a)
        }
    }) { content() }
}

/**
 * [MAT-07] 1.36 달 넘김: how a month page at [offset] (0 shown, ±1 a page away) is drawn — 밀기 (the pager's own), 스르륵, 책장 넘기기,
 * 다가오기, 겹쳐 오기, 움직이지 않기.
 *
 * 2.0.0 [PC-28] Only the drawing moves, and a month not seen is not moved at all. The months either side are kept made ([PERF-11])
 * and 스르륵·책장·움직이지 않기 used to bring them back over the month on screen with the layer's own translation, unseen (alpha 0). A
 * layer's translation also moves where a finger lands, and the later month is on top: 11월 12일 tapped opened 12월 10일, a long press
 * saved the shift there, and with 움직임 끄기 or the phone's 'remove animations' (every turn is then 움직이지 않기) it happened with no theme
 * worn at all. The slide back is now done while drawing ([monthTurnShift]), so a month answers a finger only where the pager has
 * it — at rest, the month on screen. 책장's turned page is left flat once it is out of sight (from halfway): a page turned 75° in
 * perspective, though off to the side, still answered fingers on the month shown.
 */
fun Modifier.monthTurn(turn: String?, offset: () -> Float): Modifier = when (turn) {
    null, "slide" -> this
    else -> drawWithContent {
        val shift = monthTurnShift(turn, offset().coerceIn(-1f, 1f)) * size.width
        if (shift == 0f) drawContent() else translate(left = shift) { this@drawWithContent.drawContent() }
    }.graphicsLayer {
        val o = offset().coerceIn(-1f, 1f)
        val near = abs(o) < 0.5f
        when (turn) {
            "fade" -> alpha = 1f - abs(o)
            "flip" -> if (near) { cameraDistance = 12f * density; rotationY = o * -75f } else alpha = 0f
            "zoom" -> { val s = 1f - 0.18f * abs(o); scaleX = s; scaleY = s; alpha = 1f - abs(o) * 0.8f }
            "stack" -> if (o < 0) { val s = 1f - 0.1f * abs(o); scaleX = s; scaleY = s; alpha = 1f - abs(o) * 0.5f }
            "none" -> if (!near) alpha = 0f
        }
    }
}

/**
 * 2.0.0 [PC-28] How far, in widths of the page, a month [o] pages away is drawn from where the pager has it: 스르륵·책장·움직이지 않기
 * stay in the middle (the pager's slide undone), 겹쳐 오기 comes in from twice as far, the others ride the pager.
 */
internal fun monthTurnShift(turn: String?, o: Float): Float = when (turn) {
    "fade", "flip", "none" -> o
    "stack" -> if (o < 0) -o else 0f
    else -> 0f
}

/** [MAT-07] 1.36 오늘 강조: today's date moving as [Motion.today] says (숨쉬기, 은은한 빛, 도는 테, 통통), in [tint]. */
fun Modifier.todayMotion(kind: String?, tint: Color): Modifier = if (kind == null || kind == "none") this else composed {
    val t = rememberInfiniteTransition(label = "today")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (kind == "ring") 2400 else 1100, easing = FastOutSlowInEasing),
        if (kind == "ring") RepeatMode.Restart else RepeatMode.Reverse), label = "v")
    when (kind) {
        "pulse" -> graphicsLayer { val s = 1f + 0.12f * v; scaleX = s; scaleY = s }
        "bounce" -> graphicsLayer { translationY = -3.dp.toPx() * v }
        "glow" -> drawBehind { drawCircle(tint.copy(alpha = 0.18f + 0.22f * v), radius = size.minDimension * (0.62f + 0.12f * v)) }
        "ring" -> drawBehind {
            val w = 1.6.dp.toPx()
            drawArc(tint, startAngle = 360f * v, sweepAngle = 110f, useCenter = false, topLeft = Offset(-w, -w),
                size = Size(size.width + 2 * w, size.height + 2 * w), style = Stroke(width = w))
        }
        else -> Modifier
    }
}

/**
 * 1.38 [DSG-08] (흐름·디자인 계획 4장): the app moves in three ways only. A bar or ring fills (0.8 s), a card opens
 * (260 ms), a number counts up (0.6 s). With 움직임 끄기 or the phone's 애니메이션 삭제 on, each is 0 and things simply appear.
 */
object BamnatMotion {
    const val FILL_MS = 800
    const val OPEN_MS = 260
    const val COUNT_MS = 600

    @Composable fun fill(): Int = ms(FILL_MS)
    @Composable fun open(): Int = ms(OPEN_MS)
    @Composable fun count(): Int = ms(COUNT_MS)

    /** A tween of [fill], [open] or [count]'s length, for animate*AsState. */
    @Composable fun <T> spec(millis: Int): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        tween(durationMillis = millis, easing = FastOutSlowInEasing)

    /** 0 when 움직임 끄기 or the phone's 애니메이션 삭제 is on ([LocalMotionOff], set from both in MainActivity). */
    @Composable private fun ms(full: Int): Int = if (LocalMotionOff.current) 0 else full
}
