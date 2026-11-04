package com.bamnat.app.ui.parts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.bamnat.core.recipe.BlueprintParts

/**
 * [BLU-09] 1.36 누락 점검 N4 (2차 사용성 제안 1 "누른 요소가 테두리로 짚이고 스타일 칸이 바로 열림"): where each part is drawn in the studio's
 * example, so a tap there opens that part in the 부품 도구, and a tap on the same spot again goes up to the part around it (날짜 숫자 →
 * 날짜 칸 → 달력). A part marks itself with [partSpot]; outside the studio no one listens ([LocalPartSpots] is null) and it costs nothing.
 */
class PartSpots {
    /** Each place drawn: a key per drawing (a month has many 날짜 칸) to its part and its box in the root's coordinates. */
    internal val spots = mutableStateMapOf<Any, Pair<String, Rect>>()

    /**
     * The part a tap at [at] (root coordinates) picks: the deepest part drawn there; when [now] is one of the parts there, the one
     * around it (the next shallower), and from the outermost back to the deepest.
     */
    fun pick(at: Offset, now: String?): String? {
        val here = spots.values.filter { it.second.contains(at) }
            .sortedWith(compareByDescending<Pair<String, Rect>> { depth(it.first) }.thenBy { it.second.width * it.second.height })
            .map { it.first }.distinct()
        if (here.isEmpty()) return null
        val i = here.indexOf(now)
        return if (i < 0) here.first() else here[(i + 1) % here.size]
    }

    /** Every box [id] is drawn in. */
    fun boxes(id: String?): List<Rect> = if (id == null) emptyList() else spots.values.filter { it.first == id }.map { it.second }

    private fun depth(id: String): Int {
        var d = 0
        var p: String? = id
        while (p != null) { d++; p = BlueprintParts.parentOf(p).takeIf { it != p } }
        return d
    }
}

val LocalPartSpots = staticCompositionLocalOf<PartSpots?> { null }

/** Marks where part [id] is drawn, for the studio's example to find it by a tap. */
fun Modifier.partSpot(id: String): Modifier = composed {
    val spots = LocalPartSpots.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(spots, key) { onDispose { spots.spots.remove(key) } }
    onGloballyPositioned { c -> if (c.isAttached) spots.spots[key] = id to c.boundsInRoot() }
}

/** [partSpot] as a Modifier on its own, for a screen that chains it with `then`. */
@Composable
fun partSpotModifier(id: String): Modifier = if (LocalPartSpots.current == null) Modifier else Modifier.partSpot(id)

/**
 * The example's tap layer: a tap (not a drag, not a long press) over a marked part picks it and goes no further, so the example's own
 * taps (영역 열기) do not happen too; the picked part is ringed. [picked] is the part open now.
 */
fun Modifier.partPicking(spots: PartSpots?, picked: String?, onPick: (String) -> Unit): Modifier = if (spots == null) this else composed {
    val origin = remember { mutableListOf(Offset.Zero) }
    // PC 11차 사용성 제안 (모든 숫자에 점선이 붙어 숫자가 가려짐): the one tapped is ringed strongly, the others of that part faintly.
    var tapped by remember { mutableStateOf<Offset?>(null) }
    this
        .onGloballyPositioned { origin[0] = it.positionInRoot() }
        .pointerInput(spots, picked) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val longAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if ((c.position - down.position).getDistance() > slop || c.uptimeMillis > longAt) break
                    if (!c.pressed) {
                        val id = spots.pick(down.position + origin[0], picked)
                        if (id != null) { c.consume(); tapped = down.position + origin[0]; onPick(id) }
                        break
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val o = origin[0]
            val w = 2.dp.toPx()
            val boxes = spots.boxes(picked)
            val hit = tapped?.let { t -> boxes.filter { it.contains(t) }.minByOrNull { it.width * it.height } }
            boxes.forEach { r ->
                val strong = hit == null || r == hit
                val sw = if (strong) w else w / 2
                drawRoundRect(Color(0xFFE91E63).copy(alpha = if (strong) 1f else 0.3f), Offset(r.left - o.x - sw / 2, r.top - o.y - sw / 2),
                    androidx.compose.ui.geometry.Size(r.width + sw, r.height + sw), CornerRadius(3.dp.toPx()),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2))))
            }
        }
}
