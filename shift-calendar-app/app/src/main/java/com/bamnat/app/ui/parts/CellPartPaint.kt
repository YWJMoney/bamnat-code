package com.bamnat.app.ui.parts

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import com.bamnat.app.ui.theme.fillWith
import com.bamnat.core.recipe.PartStyle

/**
 * 2.0.0 [RVS-03] 한 그리기 길(검토 SN-01·SD-01) (스튜디오 조사 보고서 5장 S1): a 달력 칸 부품 as the calendar ([com.bamnat.app.ui.calendar.MonthGrid])
 * draws it and the studio's example cells ([com.bamnat.app.ui.settings.FreeLookScreen]) show it, through the same three steps. Before,
 * the calendar drew a cell's 움직임 only when the line also had 바깥 간격 or 그림자 (장터 394개 작품의 '오늘 칸 빛' never showed), and the
 * example showed the 움직임 but neither 바깥 간격 nor 그림자, and put 투명도 under the fill where the calendar puts it over.
 */
object CellPart {
    /**
     * [RVS-03] 움직임 예산(검토 SN-03) (설계 S2 ⑥): of a month of cells only 오늘 and the day picked move, however the line is written (134 장터 줄
     * move every cell). A moving 셰이더 already moves only in 오늘 ([fillWith]'s `animate`).
     */
    fun motionOf(p: PartStyle, isToday: Boolean, isPicked: Boolean): String? =
        p.motion?.takeIf { it != "none" && (isToday || isPicked) }
}

/** The cell's outside, before its edge and its fill: 바깥 간격 and 그림자. */
fun Modifier.cellPartOutside(p: PartStyle, shape: Shape): Modifier =
    if (p.out == null && (p.shadow ?: 0) <= 0) this
    else partBox(
        p.copy(fill = null, paint = null, line = null, lineW = null, lineC = null, alpha = null, pad = null, padX = null, padY = null, motion = null),
        shape, inner = false,
    )

/**
 * The cell's inside, after its own look's fill and the day's colour, inside its edge: the part's fill, its line, its 투명도 (over the
 * fill, as the calendar has always drawn it) and its 움직임 ([CellPart.motionOf]), which moves only how the cell is drawn, never where
 * a finger lands ([RVS-02], [partMotion]).
 */
fun Modifier.cellPartInside(p: PartStyle, shape: Shape, isToday: Boolean, isPicked: Boolean): Modifier {
    var m = this
    m = p.paint?.let { m.fillWith(it, alpha = (p.fillA ?: 100) / 100f, animate = isToday) } ?: p.fillColor?.let { m.background(it) } ?: m
    m = m.partLine(p, shape)
    p.alpha?.let { m = m.alpha(it / 100f) }
    return m.partMotion(CellPart.motionOf(p, isToday, isPicked), p.lineColor ?: p.fillColor ?: Color.Unspecified)
}

/**
 * 2.0.0 [RVS-02] Clips what follows to [shape] as it is drawn, but not where a finger lands. [androidx.compose.ui.draw.clip] puts the
 * shape on a layer, and a layer's shape cuts the touch area too: a round 칸 (둥글기 48) on the open fold lost its corners. Before
 * Android 9 a drawing clip has no smooth edge, so the layer's clip stays there.
 */
fun Modifier.drawClip(shape: Shape): Modifier = when {
    shape === RectangleShape -> clipToBounds()
    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> clip(shape)
    else -> drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
    }
}
