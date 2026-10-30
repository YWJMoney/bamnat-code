package com.bamnat.app.ui.settings

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
import androidx.compose.material3.AlertDialog
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
internal fun CellWeeks(view: CalendarView, rows: Int, onCell: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val cal = LocalCalendarColors.current
    val first = LocalDate.of(2026, 11, 2)
    val today = first.plusDays(2)
    val shown = if (view.isDefault) view.copy(size = "normal") else view
    val height = when (shown.size) { "short" -> 52.dp; "tall" -> 86.dp; "huge" -> 118.dp; else -> 64.dp }
    Column(Modifier.fillMaxWidth().padding(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
                Text(d, Modifier.weight(1f), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = when (i) { 5 -> cal.saturday; 6 -> cal.sunday; else -> scheme.onSurfaceVariant })
            }
        }
        for (r in 0 until rows) Row(Modifier.fillMaxWidth().height(height)) {
            for (c in 0 until 7) {
                val date = first.plusDays((r * 7 + c).toLong())
                val s = SAMPLE_CELLS[(r * 3 + c) % SAMPLE_CELLS.size]
                val isToday = date == today
                val look = shown.lookFor(date.toEpochDay(), date.dayOfWeek.value, s.role, isToday)
                val index = if (shown.looks.isEmpty()) 0 else shown.todayLook?.takeIf { isToday && it in shown.looks.indices }
                    ?: shown.pattern.indexFor(date.toEpochDay(), date.dayOfWeek.value, s.role, shown.looks.size)
                val color = Color(s.color.toInt())
                val own = if (s.off) null else color.copy(alpha = 0.16f)
                val tint = com.bamnat.app.ui.calendar.cellTint(look, color.takeIf { !s.off }, own, null)
                val shape = com.bamnat.app.ui.calendar.cellShape(shown, look)
                val number = when (date.dayOfWeek) { java.time.DayOfWeek.SUNDAY -> cal.sunday; java.time.DayOfWeek.SATURDAY -> cal.saturday; else -> scheme.onSurface }
                Box(
                    Modifier.weight(1f).fillMaxSize()
                        .cellEdge(shown, look, shape, scheme.outlineVariant)
                        .clip(shape).background(tint ?: Color.Transparent)
                        .clickable(onClickLabel = "이 칸의 모양 고치기") { onCell(index) },
                ) {
                    com.bamnat.app.ui.calendar.CustomCellBody(
                        view = shown, cellLook = look, filled = tint?.takeIf { look?.mark == "fill" || look?.bg != null }, date = date, isToday = isToday,
                        numberColor = number, dateSize = 13.sp, footerSize = 9.sp, todayColor = null, holidayName = null, hasMemo = s.memo != null,
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
        if (shown.looks.size > 1) Text("칸을 누르면 그 칸의 모양을 고쳐요", fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
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

/** A money list's example: a day's line and three records, by the real colours and sizes. */
@Composable
internal fun MoneySample(look: MoneyLook, onTap: () -> Unit) {
    val (income, expense) = com.bamnat.app.ui.money.moneyInks()
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "가계부 모양 고치기", onClick = onTap)) {
        val band = look.dayHead == "band"
        Row(Modifier.fillMaxWidth().background(if (band) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("11월 4일 (수)", style = if (look.dayHead == "big") MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                color = if (band) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("-13,500원", style = MaterialTheme.typography.labelMedium, color = expense)
        }
        listOf(Triple("🍚", "점심", -9_000L), Triple("☕", "커피", -4_500L), Triple("💼", "월급", 3_120_000L)).forEach { (emoji, title, amount) ->
            val (minH, padV) = when (look.rows) { "compact" -> 48.dp to 4.dp; "roomy" -> 76.dp to 12.dp; else -> 64.dp to 8.dp }
            Row(Modifier.fillMaxWidth().heightIn(min = minH).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = padV),
                verticalAlignment = Alignment.CenterVertically) {
                val ink = if (amount < 0) expense else income
                when (look.mark) {
                    "dot" -> Box(Modifier.size(12.dp).clip(CircleShape).background(ink))
                    "none" -> {}
                    else -> Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Text(emoji) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text("체크카드", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (amount < 0) "-" else "+") + "%,d원".format(kotlin.math.abs(amount)), color = ink, fontWeight = FontWeight.Bold,
                    style = when (look.amount) { "s" -> MaterialTheme.typography.bodyMedium; "l" -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleSmall })
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

/** The pay screen's example: its total card and three lines, by the look of [style]. */
@Composable
internal fun PaySample(look: PayLook, style: WorkStyle, onTap: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "급여 모양 고치기", onClick = onTap).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val fill = look.plus?.let { Color((it or 0xFF000000L).toInt()) } ?: MaterialTheme.colorScheme.primaryContainer
        val on = if (look.plus != null) contentColorOn(fill) else MaterialTheme.colorScheme.onPrimaryContainer
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(fill).padding(16.dp)) {
            Text("11월 예상 급여 (세전) · ${style.label.substringBefore(' ')}", style = MaterialTheme.typography.labelLarge, color = on)
            Text("3,120,000원", color = on, fontWeight = FontWeight.Bold,
                style = when (look.total) { "m" -> MaterialTheme.typography.headlineMedium; "xl" -> MaterialTheme.typography.displayMedium; else -> MaterialTheme.typography.displaySmall })
        }
        listOf("기본급" to 2_397_500L, "야간근로수당" to 691_778L, "결근 공제" to -96_000L).forEach { (name, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = when (look.rows) { "compact" -> 4.dp; "roomy" -> 12.dp; else -> 8.dp })) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("%,d원".format(v), style = MaterialTheme.typography.titleSmall,
                    color = look.minus?.takeIf { v < 0 }?.let { Color((it or 0xFF000000L).toInt()) } ?: MaterialTheme.colorScheme.onSurface)
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
    ColorPick("배경색", l.bg) { onChange(l.copy(bg = it)) }
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
    "배경색".takeIf { l.bg != null }, "배경 사진".takeIf { l.photo != null }, "카드 색".takeIf { l.card != null }, "글자색".takeIf { l.ink != null },
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
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (v, label) -> FilterChip(selected = v == value, onClick = { onPick(v) }, label = { Text(label) }) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$label ${v.toInt()}$unit", style = MaterialTheme.typography.bodyMedium)
        Slider(v, { v = it }, valueRange = range, onValueChangeFinished = { onDone(v) }, modifier = Modifier.semantics { contentDescription = label })
    }
}

private val SWATCHES: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF7F8FC, 0xFFFFF8E1, 0xFFFFF4CC, 0xFFFFE2CC, 0xFFFFDDE4, 0xFFE6DDFF, 0xFFD9E7FF, 0xFFCFF3EA, 0xFFE8F5D0,
    0xFFF2994A, 0xFFE5484D, 0xFFE84393, 0xFF9B51E0, 0xFF2F5BEA, 0xFF00A3C4, 0xFF00857A, 0xFF27AE60, 0xFF8E9AAF, 0xFF4A5068,
    0xFF1B1B1B, 0xFF0F1220, 0xFF1E335C, 0xFF16443B, 0xFF4D2230,
)

/**
 * 1.35 [STU-12] Colours used lately in this 스튜디오 (사용자 실사용 "색 동그라미가 줄줄이"): offered first in every colour line, so a
 * look stays in a few colours without hunting. The studio provides it; elsewhere nothing is kept.
 */
class ColorMemory(val recent: List<Long> = emptyList(), val used: (Long) -> Unit = {})
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
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value?.let { colorName(it) } ?: "처음대로", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (allowNone && value != null) TextButton(onClick = { onPick(null) }) { Text("처음대로") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { custom = true }
                    .semantics { contentDescription = "$label 직접 고르기" },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            val recent = memory.recent.filter { it !in SWATCHES }.take(6)
            (recent + SWATCHES).forEachIndexed { i, c ->
                if (i == recent.size && recent.isNotEmpty()) Box(Modifier.size(1.dp, 26.dp).background(MaterialTheme.colorScheme.outlineVariant))
                val on = value == c
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(c.toInt()))
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { pick(c) }.semantics { contentDescription = "$label ${colorName(c)}" + if (i < recent.size) " (최근)" else ""; selected = on },
                )
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

/** A colour said in words for 화면 읽기: light or dark and its kind. */
private fun colorName(c: Long): String {
    val a = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toInt(), a)
    val tone = when { a[2] < 0.25f -> "어두운"; a[1] < 0.12f && a[2] > 0.9f -> "흰"; a[1] < 0.25f -> "연한"; else -> "" }
    val kind = when {
        a[1] < 0.12f -> if (a[2] > 0.9f) "색" else "회색"
        a[0] < 20 || a[0] >= 340 -> "빨강"; a[0] < 45 -> "주황"; a[0] < 70 -> "노랑"; a[0] < 160 -> "초록"; a[0] < 200 -> "청록"
        a[0] < 260 -> "파랑"; a[0] < 300 -> "보라"; else -> "분홍"
    }
    return listOf(tone, kind).filter { it.isNotEmpty() }.joinToString(" ")
}
