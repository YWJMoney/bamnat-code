package com.bamnat.core.recipe

/**
 * 2.0.0 [RVS-19] 위젯 미리 보기 = 실제 위젯 (검토 SN-09, 스튜디오 조사 2절·설계 S1): a home-screen widget is drawn by the launcher (Glance →
 * RemoteViews), which takes only part of a 부품 줄 (docs/blueprint-format.md 8-1항). The widget (app widget/WidgetParts.kt) and the studio's
 * widget example both draw [widgetDraws] of a part, so what a maker sees is what a home screen shows, and the 부품 도구 says what the widget
 * leaves out ([widgetLeaves]) instead of the example promising it.
 */
object WidgetDraws {
    /** The fonts a launcher has; any other font is drawn in the phone's own 바탕 글꼴 (null here). */
    val FONTS = setOf("serif", "mono", "hand")

    /** [p] as a widget draws it: one colour (a 무늬·그러데이션·유리·셰이더 by its [Fill.average]), one corner, 안 여백, 숨기기 and the letters. */
    fun of(p: PartStyle): PartStyle {
        if (p.isEmpty) return p
        val r = p.r ?: p.corners.firstNotNullOfOrNull { it }
        return PartStyle(
            fill = p.paint?.average ?: p.fill, fillA = p.fillA,
            r = r, pad = p.pad, padX = p.padX, padY = p.padY,
            font = p.font?.takeIf { it in FONTS }, size = p.size,
            weight = p.weight?.let { w -> when { w >= 600 -> 700; w >= 500 -> 500; else -> 400 } },
            ink = p.ink, italic = p.italic, talign = p.talign, deco = p.deco, hide = p.hide,
        )
    }

    /** What [p] has that a widget does not draw, in a maker's words, each once (empty when the widget draws all of it). */
    fun leaves(p: PartStyle): List<String> = buildList {
        if (p.paint != null && p.paint !is Fill.Solid) add("무늬·그러데이션(한 색으로)")
        if (p.line != null || p.lineW != null || p.lineC != null || p.linePaint != null) add("선")
        if (p.shadow != null || p.tshadow != null) add("그림자")
        if (p.rTL != null || p.rTR != null || p.rBR != null || p.rBL != null) {
            if (p.corners.distinct().size > 1) add("모서리 따로(한 가지로)")
        }
        if (p.stroke != null) add("글자 테두리")
        if (p.track != null || p.leading != null) add("자간·줄 간격")
        if (p.font != null && p.font !in FONTS) add("글꼴(폰 기본 글꼴로)")
        if (p.motion != null) add("움직임")
        if (p.out != null || p.w != null || p.h != null || p.place != null || p.grow != null) add("크기·자리")
        if (p.alpha != null) add("투명도")
        if (p.hasLayout) add("배치")
        if (p.photo != null || p.frame != null) add("사진")
    }
}

/** [WidgetDraws.of]. */
fun PartStyle.widgetDraws(): PartStyle = WidgetDraws.of(this)
