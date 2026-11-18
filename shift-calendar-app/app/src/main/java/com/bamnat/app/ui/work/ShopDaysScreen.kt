package com.bamnat.app.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.Fmt
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.NthWeekday
import com.bamnat.core.work.ShopDays
import com.bamnat.core.work.ShopRules
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.bamnat.core.time.Today

/**
 * [TYPE-03][TYPE-04] What a shop calendar needs instead of a shift order: the 영업 day kind with the
 * shop's hours, a weekly order of open days and 정기휴무, the monthly and holiday days off written
 * onto the calendar, and open days that passed with no sales turned into 임시휴무 by themselves.
 */
object ShopKeeper {
    /** The shop's day kinds: 영업 with its hours, and 단축영업 for a short day. */
    const val OPEN_NAME = "영업"
    const val SHORT_NAME = "단축영업"

    fun openType(types: List<ShiftType>): ShiftType? = types.firstOrNull { !it.isOff && it.name == OPEN_NAME }

    /** Whether the break of [rules] falls inside a day from [start] to [end] (a short day may end before it). */
    internal fun fits(rules: ShopRules, start: LocalTime?, end: LocalTime?): Boolean {
        val b = rules.breakStart ?: return false
        if (start == null || end == null) return false
        val span = com.bamnat.core.model.WorkTime.spanMinutes(start, end)
        val from = com.bamnat.core.model.WorkTime.spanMinutes(start, b).let { if (it == com.bamnat.core.model.WorkTime.MINUTES_PER_DAY) 0 else it }
        return from + rules.breakMinutes <= span
    }

    /** One change to shop days at a time, so a save and the catch-up on opening the app do not undo each other. */
    private val lock = Mutex()

    /** Saves [rules] for calendar [id]: its day kinds, weekly order and the days off the rules add or take back. */
    suspend fun apply(c: AppContainer, id: String, rules: ShopRules, today: LocalDate = Today.date()) = lock.withLock {
        val schedule = c.calendars.repository(id)
        val types = schedule.shiftTypes.first()
        val before = c.calendars.get(id)?.shop
        val open = openType(types)
        // [RVH-36] 검토 TYPE-23 브레이크 타임 goes onto the day kinds (hours and pay leave it out); a break set by hand on 영업
        // stays until the shop days set or take one away.
        val keepBreak = rules.breakEnd == null && before?.breakEnd == null
        fun ShiftType.withBreak(): ShiftType = when {
            keepBreak -> this
            rules.breakEnd != null && fits(rules, start, end) -> copy(breakMinutes = rules.breakMinutes, breakStart = rules.breakStart)
            else -> copy(breakMinutes = 0, breakStart = null)
        }
        // [RVH-33] The days already lived keep the hours they had (영업 hours of last month stay last month's).
        fun ShiftType.changedTo(next: ShiftType): ShiftType = if (this.id == 0L) next else withTimesFrom(next, today)
        val openId = schedule.saveShiftType(
            open?.let { it.changedTo(it.copy(start = rules.open, end = rules.close).withBreak()) }
                ?: ShiftType(0, OPEN_NAME, "영", 0xFF27AE60, rules.open, rules.close, sortOrder = -2, icon = "store").withBreak(),
        )
        // [RVH-36] 검토 WORK-23 단축영업 with the hours the shop days set, counting past midnight; a kind made by hand keeps its hours
        // until the shop days name some.
        val short = types.firstOrNull { !it.isOff && it.name == SHORT_NAME }
        val (shortStart, shortEnd) = rules.shortHours
        val shortId = when {
            short == null -> schedule.saveShiftType(ShiftType(0, SHORT_NAME, "단", 0xFFE0A100, shortStart, shortEnd, sortOrder = -1, icon = "store").withBreak())
            rules.shortOpen != null || rules.shortWeekdays.isNotEmpty() -> schedule.saveShiftType(short.changedTo(short.copy(start = shortStart, end = shortEnd).withBreak()))
            else -> short.id
        }
        val weekly = WorkPattern.Weekly(today, DayOfWeek.entries.associateWith { d ->
            when (d) {
                in rules.closedWeekdays -> null
                in rules.shortWeekdays -> shortId
                else -> openId
            }
        })
        // From today on only: the weeks already lived keep the days they had, and their sales stats with them.
        if (schedule.patterns.first().isEmpty()) schedule.setFirstPattern(weekly, today) else schedule.changePatternFrom(weekly, today)
        // The monthly days off and holidays a weekly order cannot hold go onto the days themselves; the
        // ones the old rules put there and the new ones do not are opened again.
        val to = today.plusDays(ShopDays.AHEAD_DAYS)
        val now = ShopDays.extraClosed(rules, today, to, KoreanHolidays::isHoliday).toSet()
        val was = before?.let { ShopDays.extraClosed(it, today, to, KoreanHolidays::isHoliday) }.orEmpty().toSet()
        val snapshot = schedule.snapshot.first()
        val byDate = snapshot.entries.associateBy { it.date }
        val close = now.filter { (byDate[it]?.override ?: ShiftOverride.None) == ShiftOverride.None }
        // Only days the old rules closed open again; a day the user closed by hand stays closed.
        val reopen = (was - now).filter { byDate[it]?.override == ShiftOverride.Off }
        if (close.isNotEmpty()) schedule.updateDays(close) { it.copy(override = ShiftOverride.Off) }
        if (reopen.isNotEmpty()) schedule.updateDays(reopen) { it.copy(override = ShiftOverride.None) }
        c.calendars.setShop(id, rules.copy(
            checkedUntil = before?.checkedUntil ?: today.minusDays(1),
            autoClosedDays = before?.autoClosedDays.orEmpty(),
        ))
    }

    /**
     * [TYPE-04] Open days that passed with nothing sold become 임시휴무, once each, in every shop
     * calendar. Run when the app comes to the front.
     */
    suspend fun catchUp(c: AppContainer, today: LocalDate = Today.date()) = lock.withLock {
        val mainStyle = c.settingsRepository.current.workStyle
        for (cal in c.calendars.calendars.value) {
            val rules = cal.shop ?: continue
            if (c.calendars.styleOf(cal.id, mainStyle) != WorkStyle.BUSINESS) continue
            val schedule = c.calendars.repository(cal.id)
            val snapshot = schedule.snapshot.first()
            val byDate = snapshot.entries.associateBy { it.date }
            val book = c.calendars.money(cal.id).book.first()
            val sold = book.sales.filter { it.sales > 0 }.map { it.date }.toSet() + book.entries.filter { it.kind == com.bamnat.core.money.MoneyKind.INCOME }.map { it.date }
            // Only days that open by the weekly order: a day the user set to 단축영업 or anything else stays theirs.
            val closed = ShopDays.autoClosed(rules, today, { d ->
                (byDate[d]?.override ?: ShiftOverride.None) == ShiftOverride.None && snapshot.resolver.resolve(d).isWorkDay
            }) { it in sold }
            if (closed.isNotEmpty()) schedule.updateDays(closed) { it.copy(override = ShiftOverride.Off) }
            val keepFrom = today.minusDays(ShopDays.LOOKBACK_DAYS * 2)
            c.calendars.updateShop(cal.id) { r ->
                r.copy(checkedUntil = today.minusDays(1), autoClosedDays = (r.autoClosedDays + closed).filterTo(mutableSetOf()) { !it.isBefore(keepFrom) })
            }
        }
    }

    /**
     * [TYPE-04] Sales written on a day that was closed by itself open it again; a day the user or the
     * rules closed stays closed. Called wherever sales are saved, for the calendar they were saved in.
     */
    suspend fun salesSaved(c: AppContainer, calendarId: String, date: LocalDate, sales: Long) = lock.withLock {
        if (sales <= 0) return@withLock
        val rules = c.calendars.get(calendarId)?.shop ?: return@withLock
        if (date !in rules.autoClosedDays) return@withLock
        val schedule = c.calendars.repository(calendarId)
        val entry = schedule.snapshot.first().entries.firstOrNull { it.date == date }
        if (entry?.override == ShiftOverride.Off) schedule.updateDay(date) { it.copy(override = ShiftOverride.None) }
        c.calendars.updateShop(calendarId) { it.copy(autoClosedDays = it.autoClosedDays - date) }
    }
}

class ShopDaysViewModel(private val c: AppContainer, val calendarId: String) : ViewModel() {
    private val _rules = MutableStateFlow(c.calendars.get(calendarId)?.shop ?: ShopRules())
    val rules: StateFlow<ShopRules> = _rules.asStateFlow()
    /** 1.37 PC 4차: what was saved last, so leaving with taps not saved asks first. */
    private var kept = _rules.value
    fun changed(now: ShopRules): Boolean = now != kept
    val name: String get() = c.calendars.get(calendarId)?.name ?: PersonalCalendar.MAIN_NAME
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun edit(transform: (ShopRules) -> ShopRules) { _rules.value = transform(_rules.value) }

    init {
        // [RVH-36] Shop days saved before 2.0.0 hold no short hours or break: show the ones the day kinds already have.
        viewModelScope.launch {
            val types = c.calendars.repository(calendarId).shiftTypes.first()
            val short = types.firstOrNull { !it.isOff && it.name == ShopKeeper.SHORT_NAME }
            val open = ShopKeeper.openType(types)
            fun ShopRules.known(): ShopRules {
                var r = this
                if (r.shortOpen == null && short?.start != null && short.end != null) r = r.copy(shortOpen = short.start, shortClose = short.end)
                if (r.breakStart == null && open?.breakStart != null && open.breakMinutes > 0) r = r.copy(breakStart = open.breakStart, breakMinutes = open.breakMinutes)
                return r
            }
            kept = kept.known()
            _rules.value = _rules.value.known()
        }
    }

    fun save(onDone: (String) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            ShopKeeper.apply(c, calendarId, _rules.value)
            kept = _rules.value
            c.telemetry.event("shop_days")
            _busy.value = false
            onDone("영업일을 저장했어요. ${ShopDays.describe(_rules.value)}")
        }
    }
}

/** [TYPE-03] 영업일·정기휴무: the shop's hours and the days it is always closed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShopDaysScreen(vm: ShopDaysViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Pick?>(null) }
    // 1.37 PC 4차: 요일을 누르고 저장 없이 뒤로 가면 말없이 사라져, 첫 설정 뒤 정기휴무가 '없음'이었어요. 나가기 전에 물어요.
    var leaving by remember { mutableStateOf(false) }
    val leave = { if (vm.changed(rules) && !busy) leaving = true else onBack() }
    androidx.activity.compose.BackHandler(enabled = vm.changed(rules) && !busy) { leaving = true }
    if (leaving) com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("바꾼 영업일을 저장할까요?") },
        text = { Text("저장하지 않고 나가면 고른 요일과 시간이 사라져요.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; vm.save(onSaved) }) { Text("저장") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; onBack() }) { Text("저장 안 함") } },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("영업일·정기휴무") },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "‘${vm.name}’ 달력의 영업일이에요. 정한 대로 달력이 채워지고, 쉬는 날은 휴무로 보여요. 하루만 다르면 달력에서 그날을 눌러 바꿔요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("영업시간", style = MaterialTheme.typography.titleMedium)
                        HoursButtons(rules.open, rules.close, "문 여는 시간", "닫는 시간", { picking = Pick.OPEN }, { picking = Pick.CLOSE })
                        // [RVH-36] 검토 WORK-23 자정을 넘겨 닫는 가게: say it, so 02:00 does not read as two in the afternoon's mistake.
                        if (com.bamnat.core.model.WorkTime.isOvernight(rules.open, rules.close)) Hint("자정을 넘겨 다음 날 ${Fmt.time(rules.close)}에 닫아요")
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        // [RVH-36] 검토 TYPE-23 브레이크 타임 한 줄.
                        val breakEnd = rules.breakEnd
                        SwitchLine(
                            "브레이크 타임",
                            if (breakEnd != null) "${Fmt.time(rules.breakStart!!)}~${Fmt.time(breakEnd)}에는 손님을 받지 않아요. 일한 시간에서 빼요"
                            else "점심 장사 뒤처럼 문을 잠깐 닫는 때가 있으면 켜요",
                            breakEnd != null,
                        ) { on -> vm.edit { it.copy(breakStart = if (on) LocalTime.of(15, 0) else null, breakMinutes = if (on) 120 else 0) } }
                        if (breakEnd != null) HoursButtons(rules.breakStart!!, breakEnd, "쉬기 시작", "다시 열기", { picking = Pick.BREAK_START }, { picking = Pick.BREAK_END })
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // [RVH-36] 검토 TYPE-23 검토 I02b-03 요일 줄마다 영업·단축·격주 휴무·휴무: a Saturday that closes early, a Sunday off every other week.
                        Text("요일마다", style = MaterialTheme.typography.titleMedium)
                        val today = Today.date()
                        DayOfWeek.entries.forEach { d ->
                            val now = rules.dayOf(d)
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(NthWeekday.DAY_NAMES[d.ordinal].take(1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
                                // 2.0.0 [RVH-48] 총괄 #451 사진 검토: four chips wrapped '휴무' to a second line at 100% on a folded phone (seven
                                // weekdays, fourteen lines). One row of four at the usual size, even 360dp wide; two rows of two only at a large font.
                                val kinds = com.bamnat.core.work.ShopDay.entries
                                com.bamnat.app.ui.components.ChoiceRow(
                                    kinds.map { it.short }, selected = kinds.indexOf(now), check = false,
                                    descriptions = kinds.map { "${NthWeekday.DAY_NAMES[d.ordinal]} ${it.label}" },
                                    onSelect = { i -> vm.edit { it.withDay(d, kinds[i], today) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (rules.shortWeekdays.isNotEmpty()) {
                            val (shortStart, shortEnd) = rules.shortHours
                            Text("단축영업 시간", style = MaterialTheme.typography.titleSmall)
                            HoursButtons(shortStart, shortEnd, "여는 시간", "닫는 시간", { picking = Pick.SHORT_OPEN }, { picking = Pick.SHORT_CLOSE })
                        }
                        val from = rules.everyOtherFrom
                        if (rules.everyOther.isNotEmpty() && from != null) {
                            val thisWeek = today.with(DayOfWeek.MONDAY)
                            val closedNow = Math.floorMod(java.time.temporal.ChronoUnit.WEEKS.between(from.with(DayOfWeek.MONDAY), thisWeek), 2L) == 0L
                            val first = rules.everyOther.min()
                            Text("격주 휴무는 언제 쉬어요?", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true to thisWeek, false to thisWeek.plusWeeks(1)).forEach { (isThis, week) ->
                                    FilterChip(
                                        selected = closedNow == isThis,
                                        onClick = { vm.edit { it.copy(everyOtherFrom = week) } },
                                        label = { Text((if (isThis) "이번 주" else "다음 주") + " ${Fmt.monthDay(week.with(first))}부터") },
                                    )
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("매달 쉬는 날", style = MaterialTheme.typography.titleMedium)
                        Text("둘째·넷째 월요일처럼 달마다 정해진 휴무예요.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MonthlyPicker(rules) { vm.edit { r -> r.copy(monthly = it) } }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        SwitchLine("공휴일은 쉬어요", "설·추석·대체공휴일까지 달력에 휴무로 넣어요", rules.holidaysClosed) { on ->
                            vm.edit { it.copy(holidaysClosed = on) }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SwitchLine(
                            "매출 없는 영업일은 휴무로",
                            "문 여는 날인데 매출을 적지 않고 지나가면 다음 날 휴무(임시휴무)로 바꿔요. 나중에 매출을 적으면 다시 영업일이 돼요.",
                            rules.autoClosed,
                        ) { on -> vm.edit { it.copy(autoClosed = on) } }
                    }
                }
                // [SHOP-02][SHOP-03] Card deposits and the till, saved as they change.
                ShopMoneySettings(vm.calendarId)
                Text(ShopDays.describe(rules), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { vm.save(onSaved) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "저장하는 중…" else "저장")
                }
            }
        }
    }
    picking?.let { pick ->
        val (shortStart, shortEnd) = rules.shortHours
        com.bamnat.app.ui.components.TimePickerDialog(
            title = pick.title,
            initial = when (pick) {
                Pick.OPEN -> rules.open
                Pick.CLOSE -> rules.close
                Pick.SHORT_OPEN -> shortStart
                Pick.SHORT_CLOSE -> shortEnd
                Pick.BREAK_START -> rules.breakStart ?: LocalTime.of(15, 0)
                Pick.BREAK_END -> rules.breakEnd ?: LocalTime.of(17, 0)
            },
            onDismiss = { picking = null },
            presets = when (pick) {
                Pick.BREAK_START, Pick.BREAK_END -> listOf(LocalTime.of(14, 0), LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(16, 30), LocalTime.of(17, 0))
                else -> listOf(LocalTime.of(7, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(17, 0), LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(0, 0))
            },
            onConfirm = { t: LocalTime ->
                vm.edit { r ->
                    when (pick) {
                        Pick.OPEN -> r.copy(open = t)
                        Pick.CLOSE -> r.copy(close = t)
                        Pick.SHORT_OPEN -> r.copy(shortOpen = t, shortClose = shortEnd)
                        Pick.SHORT_CLOSE -> r.copy(shortOpen = shortStart, shortClose = t)
                        // The break keeps its length when it starts later; its end sets the length (at least 10 minutes).
                        Pick.BREAK_START -> r.copy(breakStart = t)
                        Pick.BREAK_END -> r.copy(breakMinutes = com.bamnat.core.model.WorkTime.spanMinutes(r.breakStart ?: t, t).takeIf { it < com.bamnat.core.model.WorkTime.MINUTES_PER_DAY }?.coerceAtLeast(10) ?: r.breakMinutes)
                    }
                }
                picking = null
            },
        )
    }
}

/** Which time the picker sets. */
private enum class Pick(val title: String) {
    OPEN("문 여는 시간"), CLOSE("닫는 시간"), SHORT_OPEN("단축영업 여는 시간"), SHORT_CLOSE("단축영업 닫는 시간"),
    BREAK_START("브레이크 타임 시작"), BREAK_END("브레이크 타임 끝"),
}

/**
 * Two time buttons side by side at half the width each, or one above the other when either does not fit in half (글자 200%·접은
 * 폭), so a time never breaks over two lines.
 */
@Composable
private fun HoursButtons(start: LocalTime, end: LocalTime, startLabel: String, endLabel: String, onStart: () -> Unit, onEnd: () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = {
            OutlinedButton(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) { Text("$startLabel ${Fmt.time(start)}") }
            OutlinedButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) { Text("$endLabel ${Fmt.time(end)}") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { ms, c ->
        val gap = 8.dp.roundToPx()
        val w = c.maxWidth
        val half = (w - gap) / 2
        if (ms.all { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) <= half }) {
            val ps = ms.map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(half)) }
            val h = ps.maxOf { it.height }
            layout(w, h) { ps.forEachIndexed { i, p -> p.place(i * (half + gap), (h - p.height) / 2) } }
        } else {
            val ps = ms.map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(w)) }
            layout(w, ps.sumOf { it.height } + gap) { ps[0].place(0, 0); ps[1].place(0, ps[0].height + gap) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlyPicker(rules: ShopRules, onChange: (List<NthWeekday>) -> Unit) {
    var day by remember { mutableStateOf(rules.monthly.firstOrNull()?.day ?: DayOfWeek.MONDAY) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(selected = d == day, onClick = { day = d }, label = { Text(NthWeekday.DAY_NAMES[d.ordinal].take(1)) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(1, 2, 3, 4, 5, -1)).forEach { n ->
            val item = NthWeekday(n, day)
            val on = item in rules.monthly
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) rules.monthly - item else rules.monthly + item) },
                label = { Text(item.label.substringBefore(' ')) },
            )
        }
    }
    if (rules.monthly.isNotEmpty()) {
        Text("고른 날: " + rules.monthly.joinToString(", ") { it.label }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SwitchLine(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onChange)
    }
}
