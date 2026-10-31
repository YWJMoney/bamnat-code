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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    val picked = r.inputs.any { it.kind == com.bamnat.core.recipe.InputKind.COLOR }
    Text("테마 색", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
    if (r.tokens.isEmpty() && !picked) {
        Text("같은 색을 한 이름으로 묶으면, 그 색 하나만 바꿔도 근무 색·달력 색이 함께 바뀌어요 (12개까지)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { edit(RecipeTokens::bind) }) { Text("색을 테마 색으로 묶기") }
        return
    }
    r.tokens.forEach { t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ColorSwatch(t.light, "${t.name} · ${RecipeTokens.uses(r, t.name)}곳에 쓰여요", { c -> edit { RecipeTokens.set(it, t.name, c) } }) }
            // 1.35 받는 분이 고르는 색: the receiver picks this one, the maker's colour first.
            TextButton(onClick = { edit { RecipeTokens.toInput(it, t.name) ?: it } }) { Text("받는 분이 고르게") }
        }
    }
    r.inputs.filter { it.kind == com.bamnat.core.recipe.InputKind.COLOR }.forEach { i ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎨 ‘${i.label}’은 받는 분이 골라요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { edit { RecipeTokens.fromInput(it, i.id) ?: it } }) { Text("내 색으로 정하기") }
        }
    }
    Text("받는 분이 고르게 하면 넣을 때 자기 색을 골라요. 근무 색을 사람마다 다르게 쓰고 싶을 때 좋아요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // 빌드 8: '화면 색·배경: 화면 색·배경: 가계부' — a line that already starts with the piece's name says it once.
                    Text("• ${p.kind.label}: ${RecipeDescribe.line(p, amounts = false).removePrefix("${p.kind.label}: ")}", style = MaterialTheme.typography.bodySmall)
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
    // [STU-27] 1.36 빌드 8 (마치기 예시 한 주가 모두 쉬는 날): this week of the phone's own calendar, each day in the recipe's look for its
    // kind; the made-up week only when the phone has no days yet.
    val mine = rememberMyWeeks(items, weeks = 1)?.takeIf { style == r.styles.firstOrNull() || r.styles.size == 1 }
    val days0 = listOf("월", "화", "수", "목", "금", "토", "일")
    val days = mine?.let { m -> (0 until 7).map { days0[m.first.plusDays(it.toLong()).dayOfWeek.value - 1] } } ?: days0
    val said = days.indices.joinToString(", ") { i -> "${days[i]} " + (mine?.days?.getOrNull(i)?.let { d -> if (d.off) "쉬는 날" else d.label }
        ?: items.getOrNull(i % items.size.coerceAtLeast(1))?.get("name") ?: "쉬는 날") }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "예시 한 주: $said" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            val real = mine?.days?.getOrNull(i)
            val item = if (mine != null) null else items.getOrNull(i % items.size.coerceAtLeast(1))
            val bg = real?.let { androidx.compose.ui.graphics.Color(it.color.toInt()).copy(alpha = if (it.off) 0.25f else 1f) }
                ?: (item?.get("color") as? Long)?.asColor() ?: MaterialTheme.colorScheme.surfaceVariant
            val date = mine?.first?.plusDays(i.toLong())
            val dayColor = when {
                date != null && date == mine.today -> (theme?.get("today") as? Long)?.asColor()
                d == "토" -> (theme?.get("sat") as? Long)?.asColor()
                d == "일" -> (theme?.get("sun") as? Long)?.asColor()
                date == null && i == 2 -> (theme?.get("today") as? Long)?.asColor()
                else -> null
            } ?: MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d, style = MaterialTheme.typography.labelSmall, color = dayColor)
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(real?.label ?: (item?.get("emoji") as? String)?.takeIf { it.isNotEmpty() } ?: (item?.get("label") as? String).orEmpty(),
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
    theme: Map<String, Any?>?,
    /** [RCX-31] 1.35: the 근무 주기·급여 방식 piece's setup when it is on: 넣은 뒤 shows its days from today, as putting it in does. */
    company: String? = null) {
    if (looks.isEmpty() && theme == null && company == null) return
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val shared = remember(company) { company?.let { runCatching { c.companyShare.read(it) }.getOrNull() } }
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
    // [RCX-31] The company's days from today: its rotation from its own start day (or today), or its days of the week.
    fun theirs(date: java.time.LocalDate): com.bamnat.core.model.ShiftType? {
        val s = shared ?: return null
        val name = when {
            s.order.isNotEmpty() -> s.order[Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(s.anchor ?: today, date), s.order.size.toLong()).toInt()]
            s.weekly.size == 7 -> s.weekly[date.dayOfWeek.value - 1]
            else -> return null
        } ?: return null
        return snap?.shiftTypes?.firstOrNull { it.name == name } ?: s.types.firstOrNull { it.name == name }
    }
    val moves = shared != null && (shared.order.isNotEmpty() || shared.weekly.size == 7)
    val changed = days.values.count { d ->
        if (moves && !d.date.isBefore(today)) theirs(d.date)?.name != d.shiftType?.takeIf { d.isWorkDay }?.name
        else d.shiftType?.id?.let { id -> swap[id] != null && swap[id] != d.shiftType } == true
    }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "${month.monthValue}월 달력 " + (if (after) "넣은 뒤" else "지금") + ", 모양이 바뀌는 날 ${changed}일"
    }) {
        repeat(weeks) { w ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { d ->
                    val date = first.plusDays((w * 7 + d).toLong())
                    val inMonth = java.time.YearMonth.from(date) == month
                    val day = days[date].takeIf { inMonth }
                    val company = after && moves && inMonth && !date.isBefore(today)
                    val type = if (company) theirs(date)?.let { t -> swap[t.id] ?: t } else day?.shiftType?.let { t -> if (after) swap[t.id] ?: t else t }
                    val works = if (company) type != null && !type.isOff else day?.isWorkDay == true
                    val fill = if (type != null && works) type.colorArgb.asColor() else androidx.compose.ui.graphics.Color.Transparent
                    val holiday = com.bamnat.core.holiday.KoreanHolidays.isHoliday(date)
                    val digit = when {
                        type != null && works -> contentColorOn(fill)
                        holiday -> look.holiday(cal)
                        date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> look.sunday(cal)
                        date.dayOfWeek == java.time.DayOfWeek.SATURDAY -> look.saturday(cal)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).background(fill, RoundedCornerShape(4.dp))
                        .then(if (date == today && inMonth) Modifier.border(2.dp, look.today ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (inMonth) Text(type?.label?.takeIf { it.isNotEmpty() && works } ?: date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall, color = digit, maxLines = 1)
                    }
                }
            }
        }
    }
    Text(when {
        // [RCX-31] The piece that moves the days says so: before 1.35 this said they stayed.
        moves -> "근무 주기가 오늘부터 바뀌어요 (${shared!!.order.size.takeIf { it > 0 } ?: 7}일 주기). 이번 달 ${changed}일이 달라져요. 지난 날은 그대로예요"
        company != null -> "근무 시간과 급여 방식이 바뀌어요. 근무 날짜는 그대로예요"
        looks.isEmpty() -> "근무 날은 그대로이고, 주말·공휴일·오늘 색만 바뀌어요"
        else -> "이번 달 ${changed}일의 모양이 바뀌어요. 근무 날짜는 그대로예요"
    },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 1.35 화면이 이렇게 바뀌어요 (사용자 2026-10-30 "멋지고 이쁘게"): what 화면 배치·카드·위젯 모양·모서리 do, drawn rather than told —
 * the bottom tabs in their new order, each page's parts in order with the hidden ones struck through, the widget's see-through
 * card and the corners. [on] says which pieces are switched on (the receiver's choice, or all of them while making).
 */
@Composable
internal fun ScreenPreview(r: Recipe, on: (PieceKind) -> Boolean = { true }) {
    fun values(k: PieceKind) = r.pieces.firstOrNull { it.kind == k && on(k) }?.values
    val layout = values(PieceKind.LAYOUT)
    val cards = values(PieceKind.CARDS)
    val widget = values(PieceKind.WIDGET)
    val corners = values(PieceKind.PHOTO_LOOK)?.get("corners") as? String
    if (layout == null && cards == null && widget == null && corners == null) return
    Text("화면이 이렇게 바뀌어요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Card(Modifier.fillMaxWidth(), shape = com.bamnat.app.ui.theme.shapesFor(corners).medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @Suppress("UNCHECKED_CAST")
            (layout?.get("tabs") as? List<String>)?.takeIf { it.isNotEmpty() }?.let { tabs ->
                Text("아래 탭 순서", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    com.bamnat.core.layout.TabOrder.of(tabs).forEach { key ->
                        val t = com.bamnat.app.ui.TopLevel.entries.firstOrNull { it.route == key } ?: return@forEach
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Icon(t.icon, null, Modifier.size(22.dp))
                            Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            (layout?.get("calendar") as? String)?.let { PartsPreview("달력 화면", it, com.bamnat.app.ui.calendar.CalendarPart.entries.associate { p -> p.id to p.label }) }
            (cards?.get("stats") as? String)?.let { PartsPreview("통계 카드", it, com.bamnat.app.ui.stats.StatsPart.entries.associate { p -> p.id to p.label }) }
            (cards?.get("money") as? String)?.let { PartsPreview("가계부 카드", it, com.bamnat.app.ui.money.MoneyPart.entries.associate { p -> p.id to p.label }) }
            (cards?.get("memo") as? String)?.let { PartsPreview("메모 화면", it, com.bamnat.app.ui.memo.MemoPart.entries.associate { p -> p.id to p.label }) }
            widget?.let { w ->
                val alpha = ((w["alpha"] as? Number)?.toInt() ?: 100).coerceIn(30, 100)
                val dark = when (w["theme"] as? String) { "dark" -> true; "light" -> false; else -> com.bamnat.app.ui.components.isDarkSurface() }
                val bg = if (dark) androidx.compose.ui.graphics.Color(0xFF1E1E24) else androidx.compose.ui.graphics.Color(0xFFFAFAFA)
                Text("위젯", style = MaterialTheme.typography.labelLarge)
                Box(Modifier.fillMaxWidth().height(64.dp).background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5C6BC0), androidx.compose.ui.graphics.Color(0xFF26A69A))),
                    RoundedCornerShape(12.dp)).padding(10.dp)) {
                    Column(Modifier.fillMaxWidth().height(44.dp).background(bg.copy(alpha = alpha / 100f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center) {
                        Text("오늘 · 주간 07:00", style = MaterialTheme.typography.labelMedium, color = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1B1B1F))
                        if (w["memo"] == true) Text("메모 한 줄도 보여요", style = MaterialTheme.typography.labelSmall,
                            color = (if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1B1B1F)).copy(alpha = 0.7f))
                    }
                }
                Text("배경이 ${alpha}% 보이게 · " + when (w["theme"] as? String) { "dark" -> "늘 어둡게"; "light" -> "늘 밝게"; else -> "휴대폰 화면을 따라" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            corners?.let { cn ->
                Text("모서리", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("round" to "둥글게", "soft" to "보통", "square" to "각지게").forEach { (k, label) ->
                        val chosen = k == cn
                        Box(Modifier.size(width = 72.dp, height = 40.dp)
                            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, com.bamnat.app.ui.theme.shapesFor(k).medium)
                            .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, com.bamnat.app.ui.theme.shapesFor(k).medium) else Modifier),
                            contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** One page's parts as [line] puts them: in order, the hidden ones struck through. */
@Composable
private fun PartsPreview(title: String, line: String, labels: Map<String, String>) {
    val l = com.bamnat.core.layout.PageLayout.decode(line)
    val parts = l.order.filter { it in labels }
    if (parts.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { i, id ->
            val hidden = id in l.hidden
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (hidden) "–" else "${i + 1 - parts.take(i).count { it in l.hidden }}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                Box(Modifier.weight(1f).background(
                    if (hidden) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(labels.getValue(id) + if (hidden) " · 숨김" else "", style = MaterialTheme.typography.bodySmall,
                        color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
                        textDecoration = if (hidden) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
            }
        }
    }
}
