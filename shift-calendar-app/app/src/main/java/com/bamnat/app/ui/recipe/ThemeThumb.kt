package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.recipe.ThemeSwatch
import com.bamnat.app.ui.calendar.cellEdge
import com.bamnat.app.ui.calendar.cellShape
import com.bamnat.app.ui.calendar.cellTint
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.CellPattern
import com.bamnat.core.recipe.Palette

/**
 * [FREE-08] 1.35 테마 미리 그림 (사용자 2026-10-30 "잘 만든 테마를 골라 사용"): a list card's small two weeks in the recipe's own page and card
 * colours, kinds of day, cell looks and their pattern, so a theme is picked by eye before it is opened. Drawn with the calendar's own
 * cell rules ([cellTint], [cellShape], [cellEdge]); the text keeps 4.5:1 on its ground ([Palette.inkOn]).
 */
@Composable
fun ThemeThumb(look: ThemeSwatch, modifier: Modifier = Modifier, width: Dp = 120.dp) {
    val scheme = MaterialTheme.colorScheme
    val page = look.bg?.let { Color((it or 0xFF000000L).toInt()) } ?: scheme.surface
    val card = look.card?.let { Color((it or 0xFF000000L).toInt()) } ?: page
    val ink = Color(Palette.inkOn(look.ink?.let { it or 0xFF000000L }, page.argb(), card.argb()).toInt())
    val view = remember(look) { CalendarView(badge = look.badge, looks = CellLook.decodeAll(look.cells), pattern = CellPattern.decode(look.pattern)) }
    val days = look.days.ifEmpty { listOf(ThemeSwatch.Day(0xFF1E88E5, "근", null, "day"), ThemeSwatch.Day(0xFF757575, "휴", null, "off")) }
    val accent = look.accent?.let { Color((it or 0xFF000000L).toInt()) } ?: scheme.primary
    // PC 확인 f39bcfbe: an example week's letters sat on the date numbers. A wide picture makes its cells taller for both; a small one
    // (넣기 창, 64dp) shows the letters alone.
    val numbers = !look.example || width >= 100.dp
    // PC 확인 13503764: a picture, not text to read: its letters keep their size under 큰 글자 and never wrap (10~14 broke in two at 96dp).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tiny = with(density) { 6.dp.toSp() }
    val small = with(density) { 7.dp.toSp() }
    val cellRatio = if (look.example && numbers) 0.6f else 0.8f
    Column(
        modifier.width(width).clip(RoundedCornerShape(10.dp)).background(page).padding(5.dp)
            .semantics { contentDescription = "테마 미리 그림" },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(card).padding(2.dp)) {
            for (week in 0 until 2) Row(Modifier.fillMaxWidth()) {
                for (dow in 1..7) {
                    val n = week * 7 + dow
                    val d = days[(n / 2) % days.size]
                    val today = n == 10
                    val cellLook = view.lookFor(n.toLong(), dow, d.role, today)
                    val color = Color(((d.color ?: 0xFF757575L) or 0xFF000000L).toInt())
                    val off = d.role == "off"
                    val shape = cellShape(view, cellLook)
                    val tint = cellTint(cellLook, color.takeIf { !off }, if (off) null else color.copy(alpha = 0.34f), null)
                    val number = when {
                        today -> look.today?.let { Color((it or 0xFF000000L).toInt()) } ?: accent
                        dow == 7 -> look.sun?.let { Color((it or 0xFF000000L).toInt()) } ?: Color(0xFFC62828)
                        dow == 6 -> look.sat?.let { Color((it or 0xFF000000L).toInt()) } ?: Color(0xFF1565C0)
                        else -> cellLook?.dateColor?.let { Color((it or 0xFF000000L).toInt()) } ?: ink
                    }
                    Box(Modifier.weight(1f).aspectRatio(cellRatio).cellEdge(view, cellLook, shape, ink.copy(alpha = 0.18f)).clip(shape)
                        .background(tint ?: Color.Transparent)) {
                        if (numbers) Text("$n", fontSize = tiny, color = number, fontWeight = if (cellLook?.dateBold == true || today) FontWeight.Bold else null,
                            modifier = Modifier.align(placeOf(cellLook?.datePlace)).padding(horizontal = 0.5.dp, vertical = 1.dp), lineHeight = small,
                            maxLines = 1, softWrap = false)
                        // PC 확인 V44: an example week says its days, 주·야·휴, as the calendar does.
                        if (look.example && d.label.isNotEmpty()) Text(d.label.take(1), fontSize = if (numbers) tiny else small, lineHeight = small, maxLines = 1, softWrap = false,
                            color = if (off) ink.copy(alpha = 0.6f) else color, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(if (numbers) Alignment.BottomCenter else Alignment.Center).padding(bottom = if (numbers) 1.dp else 0.dp))
                        else if (!off && cellLook?.mark != "none") Box(
                            Modifier.align(if (cellLook?.markPlace == "bottom") Alignment.BottomCenter else Alignment.Center)
                                .size(if (cellLook?.mark == "dot") 3.dp else 5.dp).clip(if (cellLook?.mark == "line") RoundedCornerShape(1.dp) else CircleShape)
                                .background(color),
                        )
                    }
                }
            }
        }
        // The memo layout as two small cards (포스트잇, 격자) or lines (목록, 다이어리).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val sticky = look.memo == "sticky"
            val cols = when (look.memo) { "grid2", "sticky" -> 2; "grid3" -> 3; else -> 1 }
            repeat(cols) { i ->
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(if (sticky) 1.dp else 3.dp))
                    .background(if (sticky) listOf(Color(0xFFFFF59D), Color(0xFFFFCCBC), Color(0xFFC8E6C9))[i % 3] else card))
            }
        }
    }
}

private fun placeOf(p: String?): Alignment = when (p) {
    "tl" -> Alignment.TopStart; "tr" -> Alignment.TopEnd; "ml" -> Alignment.CenterStart; "mc" -> Alignment.Center; "mr" -> Alignment.CenterEnd
    "bl" -> Alignment.BottomStart; "bc" -> Alignment.BottomCenter; "br" -> Alignment.BottomEnd
    else -> Alignment.TopCenter
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
