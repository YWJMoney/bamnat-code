package com.bamnat.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.PartStyle

/**
 * [BLU-10] 1.36 누락 점검 N2 (제안 26 "위젯도 부품·배치·꾸밈 층"): the home-screen widgets drawn with the blueprint's 위젯 parts —
 * `widget` (판 전체: 바탕색·모서리·여백·글자), `widget.head` (위젯 머리: 달 이름, '이번 주 근무', 오늘 날짜 줄) and `widget.cell` (위젯 칸: 달 위젯의
 * 날짜 칸, 주 위젯의 하루, 오늘 위젯의 오늘·내일 줄), each in its day's states (오늘·토·일·공휴일·다른 달·근무 종류) as the calendar's cells.
 *
 * A home screen is drawn by the launcher (Glance → RemoteViews), so a part there takes what RemoteViews can draw: 채우기 (색·진하기), 모서리
 * (안드로이드 12부터), 안 여백, 숨기기, and the letters 글꼴(바탕·명조·고정폭·손글씨)·크기·굵기(보통·중간·굵게)·색·기울임·정렬·밑줄. 선·그림자·
 * 글자 테두리·자간은 위젯에서는 그리지 않아요 (docs/blueprint-format.md 8-1항).
 */
internal val LocalWidgetParts = compositionLocalOf { Blueprint.EMPTY }

/** The style of a widget part in [states], or [PartStyle.NONE] when the blueprint leaves the widget alone. */
@Composable
internal fun widgetPart(id: String, states: Set<String> = emptySet()): PartStyle {
    val bp = LocalWidgetParts.current
    return if (bp.isEmpty || !bp.touches("widget")) PartStyle.NONE else bp.style(id, states)
}

internal fun PartStyle.widgetFill(): Color? = fill?.let { c -> Color(c.toInt()).let { k -> fillA?.let { a -> k.copy(alpha = k.alpha * a.coerceIn(0, 100) / 100f) } ?: k } }

/** A part's box on the widget: its fill in its corners, then its own padding. */
internal fun GlanceModifier.widgetBox(p: PartStyle, defaultCorner: Int = 6): GlanceModifier {
    if (p.isEmpty) return this
    var m = this
    p.widgetFill()?.let { f ->
        val r = p.r ?: p.corners.firstNotNullOfOrNull { it } ?: defaultCorner
        m = m.background(ColorProvider(f)).cornerRadius(r.dp)
    } ?: (p.r ?: p.corners.firstNotNullOfOrNull { it })?.let { r -> m = m.cornerRadius(r.dp) }
    if (p.padH != null || p.padV != null) m = m.padding(horizontal = (p.padH ?: 0).dp, vertical = (p.padV ?: 0).dp)
    return m
}

/**
 * [base] with the part's letters over it, as a widget can draw them. A part with a strong fill and no 글자 색 reads in black or white.
 * Size is in sp times the 위젯 글자 크기 (wsp), so 큰 글자 still grows it.
 */
@Composable
internal fun TextStyle.widgetLetters(p: PartStyle): TextStyle {
    if (p.isEmpty) return this
    val auto = if (p.ink == null) p.widgetFill()?.takeIf { it.alpha >= 0.5f }?.let { com.bamnat.app.ui.components.contentColorOn(it.copy(alpha = 1f)) } else null
    return copy(
        color = p.ink?.let { ColorProvider(Color(it.toInt())) } ?: auto?.let { ColorProvider(it) } ?: color,
        fontSize = p.size?.let { wsp(it) } ?: fontSize,
        fontWeight = p.weight?.let { w -> when { w >= 600 -> FontWeight.Bold; w >= 500 -> FontWeight.Medium; else -> FontWeight.Normal } } ?: fontWeight,
        fontStyle = p.italic?.let { if (it) FontStyle.Italic else FontStyle.Normal } ?: fontStyle,
        textAlign = when (p.talign) { "start" -> TextAlign.Start; "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> textAlign },
        textDecoration = when (p.deco) {
            "under" -> TextDecoration.Underline
            "strike" -> TextDecoration.LineThrough
            "both" -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            "none" -> TextDecoration.None
            else -> textDecoration
        },
        fontFamily = widgetFont(p.font) ?: fontFamily,
    )
}

/** 1판 fonts a launcher has: 명조·고정폭·손글씨, the rest (갈래 B's own fonts too) in the phone's 바탕 글꼴. */
internal fun widgetFont(id: String?): FontFamily? = when (id) {
    null -> null
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "hand" -> FontFamily("cursive")
    else -> FontFamily.SansSerif
}

/** A day's states on a widget, as the calendar names them ([com.bamnat.app.ui.parts.dayStates]): its kind of work, 토·일·공휴일, 다른 달, 오늘. */
internal fun widgetDayStates(day: com.bamnat.core.schedule.ResolvedDay, today: java.time.LocalDate, inMonth: Boolean = true): Set<String> {
    val kind = day.shiftType ?: day.coveredShift
    val role = kind?.let { com.bamnat.core.recipe.ShiftRole.guess(it.name, it.isOff, it.start?.hour, if (it.isOff) 0 else it.durationMinutes).key }
    return com.bamnat.app.ui.parts.dayStates(role, day.date.dayOfWeek, com.bamnat.core.holiday.KoreanHolidays.isHoliday(day.date), inMonth, day.date == today, false)
}
