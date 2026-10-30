package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.RecipeTokens
import com.bamnat.core.work.WorkStyle

/**
 * [RCX-18] 1.35 테마 색 in 만들기 › 모양: tie the recipe's colours to named colours, change one and every place follows.
 */
@Composable
internal fun ThemeTokens(r: Recipe, edit: ((Recipe) -> Recipe) -> Unit) {
    val hasColours = r.pieces.any { it.kind == PieceKind.SHIFT_LOOK || it.kind == PieceKind.CAL_THEME }
    if (!hasColours && r.tokens.isEmpty()) return
    Text("테마 색", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
    if (r.tokens.isEmpty()) {
        Text("같은 색을 한 이름으로 묶으면, 그 색 하나만 바꿔도 근무 색·달력 색이 함께 바뀌어요 (12개까지)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { edit(RecipeTokens::bind) }) { Text("색을 테마 색으로 묶기") }
        return
    }
    r.tokens.forEach { t ->
        ColorSwatch(t.light, "${t.name} · ${RecipeTokens.uses(r, t.name)}곳", { c -> edit { RecipeTokens.set(it, t.name, c) } })
    }
    TextButton(onClick = { edit(RecipeTokens::unbind) }) { Text("테마 색 풀기 (색을 칸마다 따로)") }
}

/**
 * [RCX-17] 1.35 예시 달력 미리 보기 (만들기 5단계): for each kind of work it is made for, one example week with this recipe in it
 * (근무 꾸미기·달력 색), and what it changes there and what that kind of calendar does not take.
 */
@Composable
internal fun ExampleCalendars(r: Recipe) {
    Text("예시 달력에 넣어 보면", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    WorkStyle.entries.filter { it in r.styles }.forEach { style ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${RecipeDescribe.short(style)} 달력", style = MaterialTheme.typography.labelLarge)
                ExampleWeek(r, style)
                r.piecesFor(style).forEach { p ->
                    Text("• ${p.kind.label}: ${RecipeDescribe.line(p, amounts = false)}", style = MaterialTheme.typography.bodySmall)
                }
                val not = r.piecesNotFor(style)
                if (not.isNotEmpty()) Text("이 유형에는 없어요: ${not.joinToString(", ") { it.kind.label }}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@Suppress("UNCHECKED_CAST")
private fun ExampleWeek(r: Recipe, style: WorkStyle) {
    val pieces = r.piecesFor(style)
    val look = pieces.firstOrNull { it.kind == PieceKind.SHIFT_LOOK }?.let { RecipeResolve.piece(r, it, emptyMap()) }
    val theme = pieces.firstOrNull { it.kind == PieceKind.CAL_THEME }?.let { RecipeResolve.piece(r, it, emptyMap()) }
    val items = (look?.get("items") as? List<Map<String, Any?>>).orEmpty()
    if (items.isEmpty() && theme == null) return
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val said = days.indices.joinToString(", ") { i -> "${days[i]} ${items.getOrNull(i % items.size.coerceAtLeast(1))?.get("name") ?: "쉬는 날"}" }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "예시 한 주: $said" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            val item = items.getOrNull(i % items.size.coerceAtLeast(1))
            val bg = (item?.get("color") as? Long)?.asColor() ?: MaterialTheme.colorScheme.surfaceVariant
            val dayColor = when {
                i == 5 -> (theme?.get("sat") as? Long)?.asColor()
                i == 6 -> (theme?.get("sun") as? Long)?.asColor()
                i == 2 -> (theme?.get("today") as? Long)?.asColor()
                else -> null
            } ?: MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d, style = MaterialTheme.typography.labelSmall, color = dayColor)
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text((item?.get("emoji") as? String)?.takeIf { it.isNotEmpty() } ?: (item?.get("label") as? String).orEmpty(),
                        color = contentColorOn(bg), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * [RCX-27] 내 달력에서 미리 보기 (설계서 3-2 1, 4-3 3): this month of MY calendar, as it is now and as it would be with the chosen
 * 근무 꾸미기·달력 색, one tap apart. Only colours and the weekend/holiday/today marks change; the days themselves never do.
 */
@Composable
internal fun MyMonthPreview(calendar: String, looks: List<Pair<com.bamnat.core.model.ShiftType, com.bamnat.core.model.ShiftType>>,
    theme: Map<String, Any?>?) {
    if (looks.isEmpty() && theme == null) return
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val today = com.bamnat.app.ui.calendar.rememberToday()
    val month = java.time.YearMonth.from(today)
    val snap by remember(calendar) { c.scopeOf(calendar).schedule.snapshot }.collectAsStateWithLifecycle(null)
    val days by androidx.compose.runtime.produceState<Map<java.time.LocalDate, com.bamnat.core.schedule.ResolvedDay>>(emptyMap(), snap) {
        val s = snap ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            s.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()).associateBy { it.date }
        }
    }
    var after by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    val swap = looks.associate { (a, b) -> a.id to b }
    val dark = com.bamnat.app.ui.components.isDarkSurface()
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    val mineLook = com.bamnat.app.ui.theme.CalendarLook.of(com.bamnat.core.recipe.CalendarTheme.decode(c.settingsRepository.current.calTheme), dark)
    val look = if (after && theme != null) com.bamnat.app.ui.theme.CalendarLook.of(com.bamnat.core.recipe.CalendarTheme(
        theme["today"] as? Long, theme["sat"] as? Long, theme["sun"] as? Long, theme["holiday"] as? Long, theme["cell"] as? String), dark)
        else mineLook
    Text("내 ${month.monthValue}월 달력에서 보면", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.bamnat.app.ui.components.FilterChip(!after, { after = false }, label = { Text("지금") })
        com.bamnat.app.ui.components.FilterChip(after, { after = true }, label = { Text("넣은 뒤") })
    }
    val weekStart = c.settingsRepository.current.weekStart
    val first = month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(first, month.atEndOfMonth()) / 7) + 1).toInt()
    val changed = days.values.count { d -> d.shiftType?.id?.let { id -> swap[id] != null && swap[id] != d.shiftType } == true }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "${month.monthValue}월 달력 " + (if (after) "넣은 뒤" else "지금") + ", 모양이 바뀌는 날 ${changed}일"
    }) {
        repeat(weeks) { w ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { d ->
                    val date = first.plusDays((w * 7 + d).toLong())
                    val inMonth = java.time.YearMonth.from(date) == month
                    val day = days[date].takeIf { inMonth }
                    val type = day?.shiftType?.let { t -> if (after) swap[t.id] ?: t else t }
                    val fill = if (type != null && day.isWorkDay) type.colorArgb.asColor() else androidx.compose.ui.graphics.Color.Transparent
                    val holiday = com.bamnat.core.holiday.KoreanHolidays.isHoliday(date)
                    val digit = when {
                        type != null && day.isWorkDay -> contentColorOn(fill)
                        holiday -> look.holiday(cal)
                        date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> look.sunday(cal)
                        date.dayOfWeek == java.time.DayOfWeek.SATURDAY -> look.saturday(cal)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).background(fill, RoundedCornerShape(4.dp))
                        .then(if (date == today && inMonth) Modifier.border(2.dp, look.today ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (inMonth) Text(type?.label?.takeIf { it.isNotEmpty() && day.isWorkDay } ?: date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall, color = digit, maxLines = 1)
                    }
                }
            }
        }
    }
    Text(if (looks.isEmpty()) "근무 날은 그대로이고, 주말·공휴일·오늘 색만 바뀌어요" else "이번 달 ${changed}일의 모양이 바뀌어요. 근무 날짜는 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
