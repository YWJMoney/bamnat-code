package com.bamnat.app.ui.settings

import com.bamnat.app.ui.parts.fillColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bamnat.app.ui.decor.decor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.calendar.cellEdge
import com.bamnat.app.data.AppSettings
import com.bamnat.app.recipe.LookImages
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.LocalMemoLook
import com.bamnat.app.ui.theme.LocalMoneyLook
import com.bamnat.app.ui.theme.LocalScreenLooks
import com.bamnat.app.ui.theme.ScreenSkin
import com.bamnat.app.ui.theme.rememberLookPicture
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.CellPattern
import com.bamnat.core.recipe.MemoLook
import com.bamnat.core.recipe.MoneyLook
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.PayLook
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

/** [FREE-03] The screens 화면 꾸미기 changes. The restricted ones keep their layout; only their colours and background change. */
enum class FreeTarget(val label: String, val restricted: Boolean = false, val note: String = "") {
    CALENDAR("달력"), MEMO("메모장"), MONEY("가계부"), PAY("급여"), STATS("통계"),
    PLAZA("마을", true, "마을은 배경과 이모티콘만 바꿔요. 이 휴대폰에서만 보이고 다른 분께는 보이지 않아요"),
    SETTINGS("설정"),
    ALARM("알람 화면", true, "알람 화면은 배치를 그대로 두고 색과 배경만 바꿔요. 끄기 단추는 어떤 배경에서도 또렷하게 보여요"),
    GUARDED("결제·처리방침", true, "결제, 처리방침, 동의 화면은 배치를 그대로 두고 색과 배경만 바꿔요"),
    ALL("앱 전체"),
    ;

    fun keyFor(style: WorkStyle): String = when (this) {
        CALENDAR -> "calendar"; MEMO -> "memo"; MONEY -> "money"; PAY -> "pay." + style.name; STATS -> "stats"; PLAZA -> ScreenLooks.PLAZA
        SETTINGS -> "settings"; ALARM -> ScreenLooks.ALARM; GUARDED -> ScreenLooks.GUARDED; ALL -> ScreenLooks.ALL
    }


    val piece: com.bamnat.core.recipe.PieceKind get() = when (this) {
        CALENDAR -> com.bamnat.core.recipe.PieceKind.CAL_VIEW
        MEMO -> com.bamnat.core.recipe.PieceKind.MEMO_LOOK
        MONEY -> com.bamnat.core.recipe.PieceKind.MONEY_LOOK
        else -> com.bamnat.core.recipe.PieceKind.SCREEN_LOOK
    }
}

// ---- 달력: 칸 디자인과 패턴 ----

private data class SampleCell(val label: String, val color: Long, val role: String, val memo: String? = null, val hours: String? = null, val off: Boolean = false)

private val SAMPLE_CELLS = listOf(
    SampleCell("주", 0xFFEF6C00, "day", "병원 예약", "8h"), SampleCell("주", 0xFFEF6C00, "day", hours = "8h"),
    SampleCell("야", 0xFF3949AB, "night", "인수인계", "10h"), SampleCell("야", 0xFF3949AB, "night", hours = "10h"),
    SampleCell("휴", 0xFF757575, "off", "가족 모임", off = true), SampleCell("휴", 0xFF757575, "off", off = true),
    SampleCell("오", 0xFF00857A, "eve", "택배", "8h"),
)

/**
 * The calendar's example: [rows] weeks drawn by the month's own cell ([com.bamnat.app.ui.calendar.CustomCellBody]) with the looks and
 * the pattern; a tap on a cell opens its look ([onCell] gets the look's index).
 */
@Composable
internal fun CellWeeks(view: CalendarView, rows: Int, onCell: ((Int) -> Unit)?,
                       /**
                        * [TRY-04] 1.36 (빌드 6 확인: 칸 디자인을 넣으면 예시가 가짜 달력으로 바뀌어 전·후가 다른 달력): the phone's own days from
                        * [start] with [todayDate], drawn in the looks, instead of the made-up weeks.
                        */
                       start: LocalDate? = null, todayDate: LocalDate? = null, days: List<com.bamnat.app.ui.calendar.SampleDay>? = null,
                       /** PC 확인 W38 1.36 (오늘 색을 바꿔도 예시의 오늘 동그라미가 파랑): the 오늘 colour being made, null for the app's own. */
                       todayColor: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    val cal = LocalCalendarColors.current
    val first = start ?: LocalDate.of(2026, 11, 2)
    val today = todayDate ?: first.plusDays(2)
    val shown = if (view.isDefault) view.copy(size = "normal") else view
    val height = when (shown.size) { "short" -> 52.dp; "tall" -> 86.dp; "huge" -> 118.dp; else -> 64.dp }
    val blueprint = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(Modifier.fillMaxWidth().padding(4.dp)) {
        Row(Modifier.fillMaxWidth().decor(com.bamnat.core.recipe.Decor.WEEK).then(com.bamnat.app.ui.parts.partSpotModifier("cal.week")).then(com.bamnat.app.ui.parts.PartMods.box(blueprint.style("cal.week"), RoundedCornerShape(0.dp)))) {
            (0 until 7).map { first.plusDays(it.toLong()).dayOfWeek }.forEach { w ->
                val wd = blueprint.style("cal.week.day", setOfNotNull("sat".takeIf { w == java.time.DayOfWeek.SATURDAY }, "sun".takeIf { w == java.time.DayOfWeek.SUNDAY }))
                Text(listOf("월", "화", "수", "목", "금", "토", "일")[w.value - 1], Modifier.weight(1f).then(com.bamnat.app.ui.parts.partSpotModifier("cal.week.day")), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = com.bamnat.app.ui.parts.PartMods.text(wd.copy(ink = null), androidx.compose.material3.LocalTextStyle.current),
                    color = wd.ink?.let { Color(it.toInt()) } ?: when (w) { java.time.DayOfWeek.SATURDAY -> cal.saturday; java.time.DayOfWeek.SUNDAY -> cal.sunday; else -> scheme.onSurfaceVariant })
            }
        }
        // PC 11차 (예시 눌러 열기가 숫자와 칸만 오감): the weeks as 칸 판, so a tap again goes on 숫자 → 칸 → 칸 판 → 달력.
        Column(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("cal.grid"))) {
        for (r in 0 until rows) Row(Modifier.fillMaxWidth().height(height)) {
            for (c in 0 until 7) {
                val date = first.plusDays((r * 7 + c).toLong())
                val s = days?.getOrNull(r * 7 + c)?.let { d -> SampleCell(d.label, d.color, d.role ?: if (d.off) "off" else "day", d.memos.firstOrNull(), d.hours, d.off) }
                    ?: SAMPLE_CELLS[(r * 3 + c) % SAMPLE_CELLS.size]
                val isToday = date == today
                val look = shown.lookFor(date.toEpochDay(), date.dayOfWeek.value, s.role, isToday)
                val index = if (shown.looks.isEmpty()) 0 else shown.todayLook?.takeIf { isToday && it in shown.looks.indices }
                    ?: shown.pattern.indexFor(date.toEpochDay(), date.dayOfWeek.value, s.role, shown.looks.size)
                val color = Color(s.color.toInt())
                val own = if (s.off) null else color.copy(alpha = 0.16f)
                val tint = com.bamnat.app.ui.calendar.cellTint(look, color.takeIf { !s.off }, own, null)
                val shape = com.bamnat.app.ui.calendar.cellShape(shown, look)
                val number = when (date.dayOfWeek) { java.time.DayOfWeek.SUNDAY -> cal.sunday; java.time.DayOfWeek.SATURDAY -> cal.saturday; else -> scheme.onSurface }
                // [BLU-05] 1.36 부품 꾸미기 in the example too, by the same states as the phone's cells.
                val states = com.bamnat.app.ui.parts.dayStates(s.role, date.dayOfWeek, com.bamnat.core.holiday.KoreanHolidays.nameOf(date) != null, true, isToday, false)
                val cellPart = if (blueprint.isEmpty) com.bamnat.core.recipe.PartStyle.NONE else blueprint.style("cal.cell", states)
                val parts = if (blueprint.isEmpty) null else com.bamnat.app.ui.parts.CellParts.of(blueprint, states)
                val pShape = com.bamnat.app.ui.parts.PartMods.shapeOf(cellPart, shape)
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .then(com.bamnat.app.ui.parts.partSpotModifier("cal.cell"))
                        .cellEdge(shown, look, pShape, scheme.outlineVariant)
                        .clip(pShape).background(tint ?: Color.Transparent)
                        .then(com.bamnat.app.ui.parts.PartMods.box(cellPart.copy(pad = null, padX = null, padY = null, out = null, shadow = null), pShape))
                        // [LAY-03] The 꾸밈 층 on the example cells too.
                        .decor(com.bamnat.core.recipe.Decor.CELL, remember(date, s.role, isToday) { com.bamnat.app.ui.decor.decorDay(date, s.role, isToday) })
                        .then(if (onCell == null) Modifier else Modifier.clickable(onClickLabel = "이 칸의 모양 고치기") { onCell(index) }),
                ) {
                    com.bamnat.app.ui.calendar.CustomCellBody(
                        parts = parts,
                        view = shown, cellLook = look, filled = tint?.takeIf { look?.mark == "fill" || look?.bg != null }, date = date, isToday = isToday,
                        numberColor = number, dateSize = 13.sp, footerSize = 9.sp, todayColor = todayColor, holidayName = null, hasMemo = s.memo != null,
                        alarmOff = false, marks = emptyList(), barColor = color.takeIf { !s.off },
                        dayMark = { size ->
                            Box(Modifier.size(size).background(if (s.off) Color.Transparent else color, RoundedCornerShape(4.dp))
                                .then(if (s.off) Modifier.border(1.dp, color, RoundedCornerShape(4.dp)) else Modifier), contentAlignment = Alignment.Center) {
                                Text(s.label, fontSize = (size.value * 0.55f).sp, lineHeight = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold,
                                    color = if (s.off) color else contentColorOn(color))
                            }
                        },
                        texts = buildMap { s.memo?.let { put("memo", it) }; s.hours?.let { put("hours", it) } },
                        moneyColor = cal.income,
                    )
                }
            }
        }
        }
        if (onCell != null && shown.looks.size > 1) Text("칸을 누르면 그 칸의 모양을 고쳐요", fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

private val CELL_PRESETS: List<Pair<String, CalendarView.() -> CalendarView>> = listOf(
    "다이어리 칸" to { copy(size = size ?: "tall", looks = listOf(CellLook(border = "thin", corner = 0, datePlace = "tl", mark = "line", pad = 3)), pattern = CellPattern()) },
    "스티커" to { copy(looks = listOf(CellLook(bg = CellLook.SHIFT, bgAlpha = 30, corner = 14, datePlace = "tc", dateShape = "circle", mark = "dot")), pattern = CellPattern()) },
    "체크무늬 2칸" to {
        copy(looks = listOf(CellLook(bg = "FFFFFFFF", corner = 4), CellLook(bg = "FFF1F3F9", corner = 4)), pattern = CellPattern(CellPattern.CYCLE, n = 2))
    },
    "주말 강조" to {
        copy(looks = listOf(CellLook(), CellLook(bg = "FFFFF1E6", border = "thin", borderColor = 0xFFF2994AL, dateBold = true)),
            pattern = CellPattern(CellPattern.WEEK, week = listOf(0, 0, 0, 0, 0, 1, 1)))
    },
    "근무 색 꽉 채우기" to { copy(looks = listOf(CellLook(mark = "fill", corner = 6, datePlace = "tl")), pattern = CellPattern()) },
)

/** The looks one by one: 모양 1, 2 … (at most six), each in folds of its own parts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CellDesigner(view: CalendarView, index: Int, onIndex: (Int) -> Unit, onChange: (CalendarView) -> Unit) {
    var removing by remember { mutableStateOf(false) }
    Text("칸 하나를 꾸미면 달력 전체에 들어가요. 모양을 여러 개 만들어 패턴으로 번갈아 쓸 수도 있어요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("바로 시작하기", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CELL_PRESETS.forEach { (name, make) -> FilterChip(selected = false, onClick = { onIndex(0); onChange(view.make()) }, label = { Text(name) }) }
        if (view.looks.isNotEmpty()) FilterChip(selected = false, onClick = { onIndex(0); onChange(view.copy(looks = emptyList(), pattern = CellPattern(), todayLook = null)) },
            label = { Text("칸 디자인 없애기") })
    }
    val looks = view.looks.ifEmpty { listOf(CellLook()) }
    val i = index.coerceIn(0, looks.lastIndex)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        looks.indices.forEach { n -> FilterChip(selected = n == i, onClick = { onIndex(n) }, label = { Text("모양 ${n + 1}") }) }
        if (looks.size < CellLook.MAX) TextButton(onClick = { onChange(view.copy(looks = looks + looks[i])); onIndex(looks.size) }) { Text("+ 모양 더하기") }
    }
    val l = looks[i]
    fun set(n: CellLook) = onChange(view.copy(looks = looks.toMutableList().also { it[i] = n }))

    Group("칸 배경") {
        Chips(listOf(null to "처음대로", CellLook.SHIFT to "근무 색", "own" to "내 색", CellLook.NONE to "없음"),
            when (l.bg) { null -> null; CellLook.SHIFT, CellLook.NONE -> l.bg; else -> "own" }) { v ->
            set(l.copy(bg = when (v) { "own" -> "FFFFF4CC"; else -> v }))
        }
        if (l.bg != null && l.bg != CellLook.SHIFT && l.bg != CellLook.NONE) {
            ColorPick("칸 색", l.bg?.toLongOrNull(16)?.let { it or 0xFF000000L }, allowNone = false) { c -> set(l.copy(bg = c?.let { hex(it) })) }
        }
        if (l.bg != null && l.bg != CellLook.NONE || l.mark == "fill") {
            LabeledSlider("진하기", (l.bgAlpha ?: if (l.bg == CellLook.SHIFT) 35 else if (l.mark == "fill") 85 else 100).toFloat(), 10f..100f, "%") { set(l.copy(bgAlpha = it.toInt())) }
        }
    }
    Group("테두리·모서리") {
        Chips(listOf(null to "처음대로", "none" to "없음", "thin" to "가늘게", "thick" to "굵게", "dash" to "점선"), l.border) { set(l.copy(border = it)) }
        if (l.border != null && l.border != "none") ColorPick("테두리 색", l.borderColor) { set(l.copy(borderColor = it)) }
        LabeledSlider("모서리", (l.corner ?: 10).toFloat(), 0f..16f, "dp") { set(l.copy(corner = it.toInt())) }
    }
    Group("날짜") {
        Text("자리", style = MaterialTheme.typography.bodyMedium)
        PlaceGrid(l.datePlace ?: if (view.date == "left") "tl" else "tc") { set(l.copy(datePlace = it)) }
        Chips(listOf(null to "처음대로", "s" to "작게", "m" to "보통", "l" to "크게", "xl" to "아주 크게"), l.dateSize) { set(l.copy(dateSize = it)) }
        Chips(listOf(null to "처음대로", false to "보통 굵기", true to "굵게"), l.dateBold) { set(l.copy(dateBold = it)) }
        Chips(listOf(null to "모양 없음", "circle" to "동그라미", "square" to "네모"), l.dateShape?.takeIf { it != "none" }) { set(l.copy(dateShape = it)) }
        if (l.dateShape != null && l.dateShape != "none") ColorPick("날짜 바탕", l.dateFill) { set(l.copy(dateFill = it)) }
        ColorPick("평일 날짜 색", l.dateColor) { set(l.copy(dateColor = it)) }
        Text("토·일·공휴일 색은 ‘오늘·주말 색’에서 바꿔요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Group("근무 표시") {
        Chips(listOf(null to "처음대로", "big" to "크게", "small" to "작게", "line" to "색 띠", "dot" to "점", "fill" to "칸 전체 색", "none" to "숨기기"), l.mark) { set(l.copy(mark = it)) }
        if (l.mark != "fill" && l.mark != "none") {
            Chips(listOf(null to "처음대로", "side" to "날짜 옆", "below" to "날짜 아래", "bottom" to "칸 맨 아래"), l.markPlace) { set(l.copy(markPlace = it)) }
        }
        if (l.mark == "none") Text("숨겨도 화면 읽기는 그날 근무를 말해 줘요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Group("안쪽 여백") {
        LabeledSlider("여백", (l.pad ?: 3).toFloat(), 0f..8f, "dp") { set(l.copy(pad = it.toInt())) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { set(CellLook()) }, enabled = !l.isDefault) { Text("이 모양 처음대로") }
        if (looks.size > 1) OutlinedButton(onClick = { removing = true }) { Text("모양 ${i + 1} 지우기") }
    }
    if (removing) AlertDialog(
        onDismissRequest = { removing = false },
        text = { Text("모양 ${i + 1}을 지울까요? 패턴에서 이 모양을 쓰던 칸은 모양 1이 돼요") },
        confirmButton = { TextButton(onClick = { removing = false; onIndex(0); onChange(view.copy(looks = looks.filterIndexed { n, _ -> n != i })) }) { Text("지우기") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("그대로 두기") } },
    )
}

/** Nine places for the date, as a small grid of buttons. */
@Composable
private fun PlaceGrid(value: String, onPick: (String) -> Unit) {
    val names = mapOf('t' to "위", 'm' to "가운데", 'b' to "아래", 'l' to "왼쪽", 'c' to "가운데", 'r' to "오른쪽")
    Column(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(4.dp)) {
        for (v in listOf('t', 'm', 'b')) Row {
            for (h in listOf('l', 'c', 'r')) {
                val k = "$v$h"
                val on = k == value
                Box(
                    Modifier.size(44.dp).padding(3.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(k) }
                        .semantics { contentDescription = "날짜 ${names[v]} ${names[h]}"; selected = on; role = Role.RadioButton },
                    contentAlignment = Alignment.Center,
                ) { Text("1", fontSize = 12.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatternEditor(view: CalendarView, onChange: (CalendarView) -> Unit) {
    val n = view.looks.size
    if (n < 2) {
        Text("모양이 두 개 이상이면 번갈아 쓸 수 있어요. ‘칸 디자인’에서 + 모양 더하기를 눌러요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val p = view.pattern
    Chips(listOf(CellPattern.ONE to "모두 모양 1", CellPattern.CYCLE to "몇 칸마다 반복", CellPattern.WEEK to "요일별", CellPattern.SHIFT to "근무별"), p.mode) { m ->
        onChange(view.copy(pattern = when (m) {
            CellPattern.CYCLE -> CellPattern(CellPattern.CYCLE, n = minOf(n, 2).coerceAtLeast(2))
            CellPattern.WEEK -> CellPattern(CellPattern.WEEK, week = listOf(0, 0, 0, 0, 0, minOf(1, n - 1), minOf(1, n - 1)))
            CellPattern.SHIFT -> CellPattern(CellPattern.SHIFT, shift = mapOf("off" to minOf(1, n - 1)))
            else -> CellPattern()
        }))
    }
    val choices = (0 until n).map { it to "${it + 1}" }
    when (p.mode) {
        CellPattern.CYCLE -> {
            Text("몇 칸마다", style = MaterialTheme.typography.bodyMedium)
            Chips((2..6).map { it to "${it}칸" }, p.n) { k -> onChange(view.copy(pattern = p.copy(n = k))) }
            Text("날짜 순서대로 모양 1, 2, …를 되풀이해요. 2칸이면 바둑판처럼 보이고, 달이 바뀌어도 이어져요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CellPattern.WEEK -> listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { d, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium)
                Chips(choices, p.week.getOrElse(d) { 0 }) { k -> onChange(view.copy(pattern = p.copy(week = p.week.toMutableList().also { it[d] = k }))) }
            }
        }
        CellPattern.SHIFT -> ShiftRole.entries.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                Chips(choices, p.shift[r.key] ?: 0) { k -> onChange(view.copy(pattern = p.copy(shift = p.shift + (r.key to k)))) }
            }
        }
        else -> {}
    }
    Text("오늘 칸", style = MaterialTheme.typography.bodyMedium)
    Chips(listOf<Pair<Int?, String>>(null to "패턴대로") + (0 until n).map { it to "모양 ${it + 1}" }, view.todayLook) { k -> onChange(view.copy(todayLook = k)) }
}

internal fun cellSummary(v: CalendarView): String = if (v.looks.isEmpty()) "칸 디자인 없음 (처음대로)" else "모양 ${v.looks.size}개"

internal fun patternSummary(v: CalendarView): String = when (v.pattern.mode) {
    CellPattern.CYCLE -> "${v.pattern.n}칸마다 반복"
    CellPattern.WEEK -> "요일별"
    CellPattern.SHIFT -> "근무별"
    else -> "모두 같게"
} + (v.todayLook?.let { " · 오늘은 모양 ${it + 1}" } ?: "")

// ---- 메모장·가계부·급여 ----

@Composable
internal fun MemoLookEditor(l: MemoLook, onChange: (MemoLook) -> Unit) {
    Text("바로 고르기", style = MaterialTheme.typography.labelLarge)
    Chips(MemoLook.PRESETS.map { it.second to it.first }, MemoLook.PRESETS.firstOrNull { it.second == l }?.second) { p -> onChange(p ?: MemoLook()) }
    Group("배치") {
        Chips(listOf(null to "처음대로", "list" to "목록", "grid2" to "2열", "grid3" to "3열", "sticky" to "포스트잇", "diary" to "다이어리", "compact" to "촘촘히"), l.layout) { onChange(l.copy(layout = it)) }
    }
    Group("카드") {
        Chips(listOf(null to "메모마다 색", "one" to "한 가지 색", "none" to "카드 없이"), l.card?.takeIf { it != "own" }) { onChange(l.copy(card = it)) }
        if (l.card == "one") ColorPick("카드 색", l.cardColor, allowNone = false) { onChange(l.copy(cardColor = it)) }
        Chips(listOf(null to "테두리 처음대로", "none" to "테두리 없음", "thin" to "가는 테두리"), l.border) { onChange(l.copy(border = it)) }
        LabeledSlider("모서리", (l.corner ?: 16).toFloat(), 0f..24f, "dp") { onChange(l.copy(corner = it.toInt())) }
    }
    Group("글") {
        LabeledSlider("미리 보이는 줄", (l.lines ?: 8).toFloat(), 1f..MemoLook.MAX_LINES.toFloat(), "줄") { onChange(l.copy(lines = it.toInt())) }
        Chips(listOf(null to "제목 보통", "s" to "작게", "l" to "크게"), l.title?.takeIf { it != "m" }) { onChange(l.copy(title = it)) }
        Chips(listOf(null to "굵기 처음대로", true to "제목 굵게", false to "제목 얇게"), l.bold) { onChange(l.copy(bold = it)) }
        Chips(listOf(null to "기본 글꼴", "serif" to "명조", "mono" to "고정폭", "hand" to "손글씨 느낌"), l.font?.takeIf { it != "sans" }) { onChange(l.copy(font = it)) }
        Chips(listOf(null to "날짜 보이기", false to "날짜 숨기기"), l.date?.takeIf { !it }) { onChange(l.copy(date = it)) }
    }
    Group("메모장 바탕") {
        ColorPick("바탕색", l.bg) { onChange(l.copy(bg = it)) }
        Text("사진 배경은 아래 ‘색·배경’에서 골라요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun memoSummary(l: MemoLook): String = if (l.isDefault) "처음대로 (목록, 메모마다 색)" else
    listOfNotNull(mapOf("list" to "목록", "grid2" to "2열", "grid3" to "3열", "sticky" to "포스트잇", "diary" to "다이어리", "compact" to "촘촘히")[l.layout],
        l.font?.let { mapOf("serif" to "명조", "mono" to "고정폭", "hand" to "손글씨")[it] }, l.lines?.let { "${it}줄" }).joinToString(" · ").ifEmpty { "바꿈" }

@Composable
internal fun MoneyLookEditor(l: MoneyLook, onChange: (MoneyLook) -> Unit) {
    Group("줄") {
        Chips(listOf(null to "처음대로", "compact" to "촘촘히", "roomy" to "넉넉히"), l.rows?.takeIf { it != "normal" }) { onChange(l.copy(rows = it)) }
        Chips(listOf(null to "분류 그림", "dot" to "색 점", "none" to "표시 없음"), l.mark?.takeIf { it != "emoji" }) { onChange(l.copy(mark = it)) }
        Chips(listOf(null to "금액 보통", "s" to "작게", "l" to "크게"), l.amount?.takeIf { it != "m" }) { onChange(l.copy(amount = it)) }
        Chips(listOf(null to "날짜 줄", "band" to "색 띠", "big" to "큰 날짜"), l.dayHead?.takeIf { it != "line" }) { onChange(l.copy(dayHead = it)) }
    }
    Group("색") {
        ColorPick("수입 색", l.income) { onChange(l.copy(income = it)) }
        ColorPick("지출 색", l.expense) { onChange(l.copy(expense = it)) }
        Text("글씨가 잘 안 보이는 색이면 처음 색으로 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun moneySummary(l: MoneyLook): String = if (l.isDefault) "처음대로" else listOfNotNull(
    mapOf("compact" to "촘촘히", "roomy" to "넉넉히")[l.rows], mapOf("dot" to "색 점", "none" to "표시 없음")[l.mark],
    mapOf("s" to "금액 작게", "l" to "금액 크게")[l.amount], "수입·지출 색".takeIf { l.income != null || l.expense != null },
).joinToString(" · ").ifEmpty { "바꿈" }

/** A money list's example: a day's line and three records, by the real colours and sizes and the 가계부 parts (BLU-05·BLU-09). */
@Composable
internal fun MoneySample(look: MoneyLook, onTap: () -> Unit) {
    val (income, expense) = com.bamnat.app.ui.money.moneyInks()
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "가계부 모양 고치기", onClick = onTap)) {
        val band = look.dayHead == "band"
        val day = bp.style("money.day")
        val dayShape = com.bamnat.app.ui.parts.PartMods.shapeOf(day, RoundedCornerShape(0.dp))
        Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("money.day"))
            .then(com.bamnat.app.ui.parts.PartMods.box(day.copy(fill = null, fillA = null, pad = null, padX = null, padY = null), dayShape))
            .background(day.fillColor ?: if (band) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, dayShape)
            .padding(horizontal = (day.padH ?: 16).dp, vertical = (day.padV ?: 8).dp)) {
            Text("11월 4일 (수)", modifier = Modifier.weight(1f),
                style = com.bamnat.app.ui.parts.PartMods.text(day, (if (look.dayHead == "big") MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge)
                    .copy(color = if (band) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)))
            Text("-13,500원", style = MaterialTheme.typography.labelMedium, color = expense)
        }
        listOf(Triple("🍚", "점심", -9_000L), Triple("☕", "커피", -4_500L), Triple("💼", "월급", 3_120_000L)).forEach { (emoji, title, amount) ->
            val (minH, padV) = when (look.rows) { "compact" -> 48.dp to 4.dp; "roomy" -> 76.dp to 12.dp; else -> 64.dp to 8.dp }
            val row = bp.style("money.row")
            val amountPart = bp.style("money.amount", setOf(if (amount < 0) "out" else "in"))
            Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("money.row")).heightIn(min = row.h?.dp ?: minH)
                .background(MaterialTheme.colorScheme.surface)
                .then(com.bamnat.app.ui.parts.PartMods.box(row.copy(pad = null, padX = null, padY = null), androidx.compose.ui.graphics.RectangleShape))
                .padding(horizontal = row.padH?.dp ?: 16.dp, vertical = row.padV?.dp ?: padV),
                verticalAlignment = Alignment.CenterVertically) {
                val ink = if (amount < 0) expense else income
                when (look.mark) {
                    "dot" -> Box(Modifier.size(12.dp).clip(CircleShape).background(ink))
                    "none" -> {}
                    else -> Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Text(emoji) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = com.bamnat.app.ui.parts.PartMods.text(row, MaterialTheme.typography.bodyLarge))
                    Text("체크카드", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (amount < 0) "-" else "+") + "%,d원".format(kotlin.math.abs(amount)),
                    color = amountPart.ink?.let { Color(it.toInt()) } ?: ink, fontWeight = amountPart.weight?.let { FontWeight(it) } ?: FontWeight.Bold,
                    modifier = Modifier.then(com.bamnat.app.ui.parts.partSpotModifier("money.amount"))
                        .then(com.bamnat.app.ui.parts.PartMods.box(amountPart.copy(ink = null), RoundedCornerShape(4.dp))),
                    style = com.bamnat.app.ui.parts.PartMods.text(amountPart.copy(ink = null),
                        when (look.amount) { "s" -> MaterialTheme.typography.bodyMedium; "l" -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleSmall }))
            }
        }
    }
}

@Composable
internal fun PayLookEditor(l: PayLook, onChange: (PayLook) -> Unit) {
    Text("금액, ‘예상’·‘추정’ 표시와 법 안내는 모양과 상관없이 늘 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Group("합계") {
        Chips(listOf(null to "보통", "m" to "작게", "xl" to "아주 크게"), l.total?.takeIf { it != "l" }) { onChange(l.copy(total = it)) }
        ColorPick("합계 카드 색", l.plus) { onChange(l.copy(plus = it)) }
    }
    Group("항목") {
        Chips(listOf(null to "줄 간격 보통", "compact" to "촘촘히", "roomy" to "넉넉히"), l.rows?.takeIf { it != "normal" }) { onChange(l.copy(rows = it)) }
        ColorPick("빼는 돈 색", l.minus) { onChange(l.copy(minus = it)) }
        Chips(listOf(null to "풀이 접어 두기", true to "풀이 펼쳐 두기"), l.open?.takeIf { it }) { onChange(l.copy(open = it)) }
        Chips(listOf(null to "날짜별 내역 보이기", false to "날짜별 내역 숨기기"), l.days?.takeIf { !it }) { onChange(l.copy(days = it)) }
    }
}

internal fun paySummary(l: PayLook?): String = if (l == null || l.isDefault) "처음대로" else listOfNotNull(
    mapOf("m" to "합계 작게", "xl" to "합계 아주 크게")[l.total], "합계 카드 색".takeIf { l.plus != null },
    mapOf("compact" to "촘촘히", "roomy" to "넉넉히")[l.rows], "풀이 펼침".takeIf { l.open == true }, "날짜별 숨김".takeIf { l.days == false },
).joinToString(" · ").ifEmpty { "바꿈" }

/** The pay screen's example: its head, its total card and three lines, by the look of [style] and the 급여 parts (BLU-11). */
@Composable
internal fun PaySample(look: PayLook, style: WorkStyle, onTap: () -> Unit) {
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "급여 모양 고치기", onClick = onTap).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val head = bp.style("pay.head")
        if (head.hide != true) Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("pay.head"))
            .then(com.bamnat.app.ui.parts.PartMods.box(head.copy(ink = null), RoundedCornerShape(0.dp))), verticalAlignment = Alignment.CenterVertically) {
            com.bamnat.app.ui.parts.PartText("${style.label.substringBefore(' ')} · 월급", head, MaterialTheme.typography.titleMedium, Modifier.weight(1f))
            Text("‹ 11월 ›", style = MaterialTheme.typography.titleSmall)
        }
        val total = bp.style("pay.total")
        val fill = total.fillColor ?: look.plus?.let { Color((it or 0xFF000000L).toInt()) } ?: MaterialTheme.colorScheme.primaryContainer
        val on = total.ink?.let { Color(it.toInt()) } ?: if (look.plus != null || total.fillColor != null) contentColorOn(fill.copy(alpha = 1f)) else MaterialTheme.colorScheme.onPrimaryContainer
        val shape = com.bamnat.app.ui.parts.PartMods.shapeOf(total, MaterialTheme.shapes.large)
        Column(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("pay.total"))
            .then(com.bamnat.app.ui.parts.PartMods.box(total.copy(fill = null, fillA = null, pad = null, padX = null, padY = null), shape))
            .clip(shape).background(fill).padding(horizontal = (total.padH ?: 16).dp, vertical = (total.padV ?: 16).dp)) {
            Text("11월 예상 급여 (세전) · ${style.label.substringBefore(' ')}", style = MaterialTheme.typography.labelLarge, color = on)
            Text("3,120,000원", color = on, fontWeight = FontWeight.Bold,
                style = com.bamnat.app.ui.parts.PartMods.text(total.copy(ink = null),
                    when (look.total) { "m" -> MaterialTheme.typography.headlineMedium; "xl" -> MaterialTheme.typography.displayMedium; else -> MaterialTheme.typography.displaySmall }))
        }
        listOf("기본급" to 2_397_500L, "야간근로수당" to 691_778L, "결근 공제" to -96_000L).forEach { (name, v) ->
            val row = bp.style("pay.row", setOf(if (v < 0) "out" else "in"))
            val rowShape = com.bamnat.app.ui.parts.PartMods.shapeOf(row, MaterialTheme.shapes.small)
            Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("pay.row"))
                .then(com.bamnat.app.ui.parts.PartMods.box(row.copy(pad = null, padX = null, padY = null), rowShape))
                .padding(horizontal = (row.padH ?: 0).dp, vertical = row.padV?.dp ?: when (look.rows) { "compact" -> 4.dp; "roomy" -> 12.dp; else -> 8.dp })) {
                Text(name, Modifier.weight(1f), style = com.bamnat.app.ui.parts.PartMods.text(row, MaterialTheme.typography.bodyLarge))
                Text("%,d원".format(v), style = com.bamnat.app.ui.parts.PartMods.text(row.copy(ink = null), MaterialTheme.typography.titleSmall),
                    color = row.ink?.let { Color(it.toInt()) } ?: look.minus?.takeIf { v < 0 }?.let { Color((it or 0xFF000000L).toInt()) } ?: MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---- 색·배경 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenLookEditor(target: FreeTarget, l: ScreenLook, shown: ScreenLook, onChange: (ScreenLook) -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    val pick = rememberPhotoPicker(LookImages.Slot.BG, onPicked = { id -> problem = null; onChange(l.copy(photo = id)) }, onProblem = { problem = it })
    val picture = rememberLookPicture(l.photo)
    if (target != FreeTarget.ALL) Text("비워 둔 것은 ‘앱 전체’를 따라요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // PC 확인 W45 1.36 (색·배경에 바탕색 칸이 없음 — '배경색'으로 따로 불려 못 찾음): one word, 바탕색, as 한 색 and 위젯 say it.
    ColorPick("바탕색", l.bg) { onChange(l.copy(bg = it)) }
    Group("배경 사진") {
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp, 84.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                if (picture != null) androidx.compose.foundation.Image(picture.first, "배경 사진", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            TextButton(onClick = pick) { Text(if (l.photo == null) "사진 고르기" else "바꾸기") }
            if (l.photo != null) TextButton(onClick = { onChange(l.copy(photo = null)) }) { Text("없애기") }
        }
        if (l.photo != null || shown.photo != null) LabeledSlider("사진 위 흐리게", (l.dim ?: 30).toFloat(), 20f..80f, "%") { onChange(l.copy(dim = it.toInt())) }
    }
    if (target == FreeTarget.PLAZA) {
        Group("이모티콘") {
            Text("마을 제목 앞에 붙어요. 이 휴대폰에서만 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chips(listOf<Pair<String?, String>>(null to "없음") + listOf("🌙", "☀️", "🌿", "🐻", "🍀", "⭐", "💬", "🔥", "🎈", "🧸").map { it to it }, l.emoji) { onChange(l.copy(emoji = it)) }
        }
        return
    }
    ColorPick("카드 색", l.card) { onChange(l.copy(card = it)) }
    ColorPick("글자색", l.ink) { onChange(l.copy(ink = it)) }
    val ground = (shown.bg ?: 0xFFF7F8FCL)
    if (shown.ink != null && Palette.inkOn(shown.ink, ground, shown.card ?: ground) != shown.ink) {
        Text("고른 글자색은 바탕에서 잘 안 보여서 검정이나 흰색으로 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    ColorPick("강조색 (단추·고른 것)", l.accent) { onChange(l.copy(accent = it)) }
    if (target == FreeTarget.ALARM) Text("끄기 단추는 어떤 색에서도 글자가 또렷하게 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!l.isDefault) OutlinedButton(onClick = { onChange(ScreenLook()) }) { Text("이 화면 색·배경 처음대로") }
}

internal fun colorSummary(l: ScreenLook?): String = if (l == null || l.isDefault) "처음대로" else listOfNotNull(
    "바탕색".takeIf { l.bg != null }, "배경 사진".takeIf { l.photo != null }, "카드 색".takeIf { l.card != null }, "글자색".takeIf { l.ink != null },
    "강조색".takeIf { l.accent != null }, l.emoji,
).joinToString(" · ")

/** A screen's example for the screens without their own: a bar, a card with words, a switch and a button. */
@Composable
internal fun ScreenSample(target: FreeTarget, onTap: () -> Unit) {
    val emoji = com.bamnat.app.ui.theme.LocalScreenLook.current.emoji
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "색·배경 고치기", onClick = onTap).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(listOfNotNull(emoji, target.label).joinToString(" "), style = MaterialTheme.typography.titleLarge)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("카드 제목", style = MaterialTheme.typography.titleSmall)
                Text("설명 글은 이렇게 보여요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("스위치", Modifier.weight(1f))
                    Switch(true, {})
                }
                Button(onClick = onTap) { Text("단추") }
            }
        }
    }
}

/** The alarm screen's example: its time and the two big buttons, with the dismiss button on its own plate. */
@Composable
internal fun AlarmSample(onTap: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "색·배경 고치기", onClick = onTap).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("06:30", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text("주간 근무 알람", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTap) { Text("5분 뒤") }
            Button(onClick = onTap) { Text("끄기") }
        }
    }
}

// ---- 작은 조각 ----

@Composable
internal fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Chips(options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (v, label) -> FilterChip(selected = v == value, onClick = { onPick(v) }, label = { Text(label) }) }
    }
}

@Composable
internal fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    /** [BLU-06] Set when nothing is chosen yet: shown in place of the number ("처음대로 · 지금 8dp"); −/+ start from [value]. */
    unset: String? = null,
    /** How far one press of −/+ goes (보이는 정도 5%씩, PC 3차). Held, it keeps going and is kept once when the finger lifts. */
    step: Int = 1,
    onDone: (Float) -> Unit,
) {
    var v by remember(value) { mutableFloatStateOf(value) }
    var moved by remember(value, unset) { mutableStateOf(false) }
    val showUnset = unset != null && !moved
    fun nudge(by: Int) { moved = true; v = ((Math.round(v / step) + by) * step).toFloat().coerceIn(range) }
    Column {
        // [BLU-06] 1.36 −/+ for a value hard to hit with a finger on the slider (PC 1차 사용성 제안 '둥글기 −/+').
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (showUnset) "$label · $unset" else "$label ${v.toInt()}$unit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StepButton("−", "$label ${step}씩 줄이기", v > range.start, { nudge(-1) }) { onDone(v) }
            StepButton("+", "$label ${step}씩 늘리기", v < range.endInclusive, { nudge(1) }) { onDone(v) }
        }
        Slider(v, { moved = true; v = it }, valueRange = range, onValueChangeFinished = { onDone(v) }, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** − or +: a tap moves one step; held, it repeats faster until the finger lifts, and [onRelease] keeps the value once (one 되돌리기). */
@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, onStep: () -> Unit, onRelease: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val step by androidx.compose.runtime.rememberUpdatedState(onStep)
    val release by androidx.compose.runtime.rememberUpdatedState(onRelease)
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .semantics {
                contentDescription = description
                role = androidx.compose.ui.semantics.Role.Button
                if (!enabled) disabled()
                onClick { if (enabled) { step(); release() }; enabled }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    step()
                    val held = scope.launch {
                        // About 8 in the first second and 30 by the third (PC 4차: 2.5초에 40은 너무 빠름, PC 7차: 첫 1초에 4는 너무 느림).
                        kotlinx.coroutines.delay(300)
                        var n = 0
                        while (true) { step(); n++; kotlinx.coroutines.delay(if (n < 6) 110L else maxOf(80L, 110L - (n - 6) * 10L)) }
                    }
                    tryAwaitRelease()
                    held.cancel()
                    release()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

/** How many of [SWATCHES] are the light ones (the top row). */
private const val LIGHT_SWATCHES = 10

private val SWATCHES: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF7F8FC, 0xFFFFF8E1, 0xFFFFF4CC, 0xFFFFE2CC, 0xFFFFDDE4, 0xFFE6DDFF, 0xFFD9E7FF, 0xFFCFF3EA, 0xFFE8F5D0,
    // PC 제안 1.36: the deep row by kind of colour, each kind light to dark, 검정 last.
    0xFFF2994A, 0xFFE5484D, 0xFF4D2230, 0xFFE84393, 0xFF9B51E0, 0xFF2F5BEA, 0xFF8E9AAF, 0xFF4A5068, 0xFF1E335C, 0xFF0F1220,
    0xFF00A3C4, 0xFF00857A, 0xFF16443B, 0xFF27AE60, 0xFF1B1B1B,
)

/**
 * 1.35 [STU-12] Colours used lately in this 스튜디오 (사용자 실사용 "색 동그라미가 줄줄이"): offered first in every colour line, so a
 * look stays in a few colours without hunting. The studio provides it; elsewhere nothing is kept.
 */
class ColorMemory(
    val recent: List<Long> = emptyList(),
    val used: (Long) -> Unit = {},
    /** [STU-40] 1.36 내 색 모음: colours pinned for good (settings), first in every line; [pin] adds or takes one away. */
    val pinned: List<Long> = emptyList(),
    val pin: ((Long) -> Unit)? = null,
)
val LocalColorMemory = androidx.compose.runtime.staticCompositionLocalOf { ColorMemory() }

/**
 * A colour in one line: what it is now, the colours used lately, then the swatches in a row that scrolls sideways, and 직접 고르기
 * (빛깔·맑기·밝기, or a code). 처음대로 when [allowNone].
 */
@Composable
fun ColorPick(label: String, value: Long?, allowNone: Boolean = true, onPick: (Long?) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val memory = LocalColorMemory.current
    fun pick(c: Long?) { c?.let(memory.used); onPick(c) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // PC 확인 1.36 (색·배경의 바탕색 줄에 이름표가 안 보임): the colour's name as a heading, like 배경 사진 above the rows.
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(value?.let { colorName(it) } ?: "처음대로", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // [STU-40] ★ keeps this colour in 내 색 (or lets it go).
            if (value != null && memory.pin != null) TextButton(onClick = { memory.pin.invoke(value) }) {
                Text(if (value in memory.pinned) "★ 내 색" else "☆ 내 색에")
            }
            if (allowNone && value != null) TextButton(onClick = { onPick(null) }) { Text("처음대로") }
        }
        // PC 제안 1.36 (25개가 한 줄이라 넘기기 힘듦): two rows that scroll together — 최근·밝은 색 위, 진한·어두운 색 아래.
        val recent = (memory.pinned + memory.recent.filter { it !in memory.pinned }.take(6)).filter { it !in SWATCHES }.distinct()
        val row = recent + SWATCHES
        val names = remember(row) { colorNames(row) }
        @Composable fun Dot(i: Int) {
            val c = row[i]
            val on = value == c
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c.toInt()))
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { pick(c) }.semantics { contentDescription = "$label ${names[i]}" + (if (c in memory.pinned) " (내 색)" else if (i < recent.size) " (최근)" else ""); selected = on },
            )
        }
        val split = recent.size + LIGHT_SWATCHES
        // PC 제안 1.36: on a new phone, where the colours used will gather.
        if (recent.isEmpty() && memory.pin != null) Text("쓴 색과 ★ 내 색은 줄 맨 앞에 모여요", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { custom = true }
                    .semantics { contentDescription = "$label 직접 고르기" },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until split) {
                        if (i == recent.size && recent.isNotEmpty()) Box(Modifier.size(1.dp, 26.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Dot(i)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (i in split until row.size) Dot(i) }
            }
        }
    }
    if (custom) CustomColorDialog(value ?: 0xFF2F5BEA, onDismiss = { custom = false }) { custom = false; pick(it) }
}

/** 빛깔 (hue), 맑기 (saturation) and 밝기 (value), or the colour's code. */
@Composable
private fun CustomColorDialog(start: Long, onDismiss: () -> Unit, onDone: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toInt(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    var code by remember(color) { mutableStateOf(hex(color.toArgb().toLong() and 0xFFFFFFFFL).drop(2)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("색 직접 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text("가나다 123", color = contentColorOn(color))
                }
                Text("빛깔", style = MaterialTheme.typography.bodySmall); Slider(h, { h = it }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "빛깔" })
                Text("맑기", style = MaterialTheme.typography.bodySmall); Slider(s, { s = it }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "맑기" })
                Text("밝기", style = MaterialTheme.typography.bodySmall); Slider(v, { v = it }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "밝기" })
                OutlinedTextField(code, { t ->
                    code = t.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    code.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { c ->
                        val a = FloatArray(3); android.graphics.Color.colorToHSV((c or 0xFF000000L).toInt(), a); h = a[0]; s = a[1]; v = a[2]
                    }
                }, label = { Text("색 코드 (예: 2F5BEA)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(color.toArgb().toLong() and 0xFFFFFFFFL) }) { Text("이 색으로") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

/** PC 확인 W33 1.36 (흰색 둘, 연한 파랑 둘): each colour of a row said in words, a number added where two read the same. */
internal fun colorNames(cs: List<Long>): List<String> {
    val names = cs.map(::colorName)
    val seen = mutableMapOf<String, Int>()
    fun light(c: Long): Float { val a = FloatArray(3); android.graphics.Color.colorToHSV(c.toInt(), a); return a[2] * (1f - a[1] / 2f) }
    return names.mapIndexed { i, n ->
        val same = names.indices.filter { names[it] == n }
        // PC 제안 1.36: colours that read alike are told apart in words by how light they are (two: 더 밝은·더 짙은; three: 밝은·중간·짙은).
        val rank = same.sortedByDescending { light(cs[it]) }.indexOf(i)
        when {
            same.size < 2 -> n
            same.size == 2 -> (if (rank == 0) "더 밝은 " else "더 짙은 ") + n
            same.size == 3 -> listOf("밝은 ", "중간 ", "짙은 ")[rank] + n
            else -> "$n ${seen.merge(n, 1, Int::plus)}"
        }
    }
}

/** A colour said in words for 화면 읽기: light or dark and its kind. */
internal fun colorName(c: Long): String {
    val a = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toInt(), a)
    // PC 확인 W33 1.36 (흰 동그라미 셋이 모두 '흰 색'): greys by how light, near-whites by their hint of colour.
    if (a[1] < 0.04f) return when { a[2] > 0.97f -> "흰색"; a[2] > 0.85f -> "밝은 회색"; a[2] > 0.6f -> "회색"; a[2] > 0.25f -> "진한 회색"; else -> "검정" }
    val hue = when { a[0] < 20 || a[0] >= 340 -> "빨강"; a[0] < 45 -> "주황"; a[0] < 70 -> "노랑"; a[0] < 160 -> "초록"; a[0] < 200 -> "청록"
        a[0] < 245 -> "파랑"; a[0] < 300 -> "보라"; else -> "분홍" }
    if (a[1] < 0.12f) return if (a[2] > 0.85f) "아주 연한 $hue" else "회색빛 $hue"
    // PC 확인 1.36 (연한 파랑이 셋: 하늘색·연보라·회청색): a dull one is 회색빛, not 연한.
    // PC 확인 1.36 ('어두운 파랑'이 '짙은 파랑'과 헷갈림): the darkest say 아주 짙은, above the 밝은·짙은 of a group.
    val tone = when { a[2] < 0.25f -> "아주 짙은"; a[1] < 0.25f && a[2] < 0.8f -> "회색빛"; a[1] < 0.25f -> "연한"; else -> "" }
    return listOf(tone, hue).filter { it.isNotEmpty() }.joinToString(" ")
}
