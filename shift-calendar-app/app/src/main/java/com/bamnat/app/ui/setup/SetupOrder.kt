package com.bamnat.app.ui.setup

import com.bamnat.app.ui.components.labelWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LayersClear
import com.bamnat.app.ui.components.FilledTonalButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.calendar.MonthGrid
import com.bamnat.app.ui.calendar.PaintTool
import com.bamnat.app.ui.calendar.WEEK_BAR_SPACE
import com.bamnat.app.ui.calendar.WeekdayHeader
import com.bamnat.app.ui.calendar.cellTextGrowth
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.PatternPresets
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.work.WorkStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun OrderStep(vm: SetupViewModel, d: SetupDraft) {
    when {
        d.usesRotation -> RotationOrder(vm, d)
        d.style == WorkStyle.SHIFT -> RosterPaint(vm, d)
        else -> OrderPart(vm, d, header = true)
    }
}

/**
 * [EASE-07] 1.38: the order, or the days of the week, on the times' screen (one screen less). With [header] it is a screen of its
 * own (a step kept from an older draft); without, its questions are said as small titles over the times.
 */
@Composable
internal fun OrderPart(vm: SetupViewModel, d: SetupDraft, header: Boolean = false) {
    when {
        d.usesRotation -> RotationOrder(vm, d, header)
        d.style == WorkStyle.HOURLY -> HourlyDays(vm, d, header)
        else -> {
            // 1.37 PC LOW-3: the example says this kind of work's own day (일근), not 교대's 주간.
            if (header) StepHeader("어느 요일에 일하세요?", "쉬는 요일은 비워 두세요. 어떤 주만 다르면 달력에서 그날만 바꾸면 돼요.")
            else PartTitle("일하는 요일", "쉬는 요일은 비워 두세요. 어떤 주만 다르면 달력에서 그날만 바꾸면 돼요.")
            WeekDays(d, onSet = vm::setWeekly)
            HolidaysOffRow(d.holidaysOff, vm::setHolidaysOff)
        }
    }
}

/** [EASE-07] A question inside a screen: smaller than the screen's own, with its one line. */
@Composable
internal fun PartTitle(title: String, line: String? = null) {
    Column(Modifier.padding(top = 4.dp).semantics { heading() }) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ---- a repeating order ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RotationOrder(vm: SetupViewModel, d: SetupDraft, header: Boolean = true) {
    if (header) StepHeader("근무가 어떤 순서로 돌아가요?", "회사 근무표에서 한 바퀴만 보면 돼요. 오늘이 어디쯤인지는 다음에 물어요.")
    else PartTitle("근무 순서", "회사 근무표에서 한 바퀴만 보면 돼요. 오늘이 어디쯤인지는 다음에 물어요.")
    val types = remember(d.types) { d.types.associateBy { it.id } }
    val shared = remember(d.source) { vm.sourceOrder(d) }
    val presets = remember(d.bundle, d.roles, d.types) { vm.presets(d).map { it to PatternPresets.toSteps(it, d.roles) } }
    // [SETUP-14] Kept through folding and unfolding.
    var custom by rememberSaveable {
        mutableStateOf(d.rotation.isNotEmpty() && d.rotation != shared && presets.none { it.second == d.rotation })
    }
    // [EASE-07] On the times' screen an order already picked (the company's, or the bundle's most usual one) is shown alone,
    // the others one tap away, so the times stay in sight.
    val picked = d.rotation.isNotEmpty() && !custom
    var others by rememberSaveable { mutableStateOf(header || !picked) }
    val source = d.source
    val publicRow = remember(source) { source?.takeIf { it.kind == com.bamnat.core.setup.SetupSource.Kind.PUBLIC }?.id?.let(com.bamnat.core.plaza.PublicCycles::byId) }
    if (shared != null && (others || d.rotation == shared)) {
        OrderCard(
            title = when {
                source?.kind == com.bamnat.core.setup.SetupSource.Kind.TEMPLATE -> "${source.name} 순서"
                // [EASE-06] A public row that gave only "6근2휴": the usual order for it, said so.
                publicRow != null && publicRow.orderState != com.bamnat.core.plaza.OrderState.EXACT -> "흔한 순서 · 공개 자료엔 ‘${publicRow.orderText.substringBefore(" (").take(16)}’까지만"
                source?.name.isNullOrBlank() -> "받은 설정의 순서"
                else -> "${source?.name} 순서"
            },
            steps = shared,
            types = types,
            selected = !custom && d.rotation == shared,
            onClick = {
                custom = false
                vm.setRotation(shared)
            },
        )
    }
    presets.filter { it.second != shared && (others || it.second == d.rotation) }.forEach { (p, steps) ->
        OrderCard(
            title = p.name,
            steps = steps,
            types = types,
            selected = !custom && d.rotation == steps,
            onClick = {
                custom = false
                vm.pickPreset(p)
            },
        )
    }
    // [GROW-05] 1.38 다른 앱에서 옮기기: the month view of the app used before, captured, gives the order. Shown before the
    // other orders fold away (#189): someone coming from another app takes this way first.
    val context = androidx.compose.ui.platform.LocalContext.current
    val capturing by vm.captureReading.collectAsStateWithLifecycle()
    val capture = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { custom = true; vm.orderFromCapture(context, uri) }
    }
    ChoiceCard(
        title = if (capturing) "캡처를 읽는 중이에요…" else "다른 앱 화면 캡처로 가져오기",
        // 1.39 [FLOW-29] 조건은 미리: the picture is read only with the month's dates (four weeks or more) and a 근무 on 7 days or more.
        subtitle = "쓰던 근무 앱이나 캘린더의 한 달 화면을 캡처해 고르면 순서와 오늘 근무를 찾아요. " +
            "날짜가 ${com.bamnat.core.imports.CalendarShot.MIN_DAYS}칸 넘게, 근무 글자가 ${com.bamnat.core.imports.CalendarShot.MIN_CELLS}칸 넘게 보여야 읽혀요. 사진은 휴대폰 안에서만 읽어요",
        icon = Icons.Filled.PhotoCamera,
        selected = false,
        onClick = { if (!capturing) capture.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
    if (!others) {
        TextButton(onClick = { others = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("순서가 달라요 · 다른 순서 고르기")
        }
        return
    }
    ChoiceCard(
        title = "직접 만들기",
        subtitle = "근무를 하루씩 눌러 순서를 만들어요",
        icon = Icons.Filled.Edit,
        selected = custom,
        onClick = { custom = true },
    )
    if (custom) {
        val today = remember(d.types) { vm.questionDate(d) }
        RotationEditor(d, types, onChange = vm::setRotation, today = today, realToday = vm.today, onToday = { step -> vm.answerToday(today, step) },
            weekStart = vm.weekStart)
    }
}

/** One order to pick: its days as badges, how often it comes round and the week's average. */
@Composable
private fun OrderCard(title: String, steps: List<Long?>, types: Map<Long, ShiftType>, selected: Boolean, onClick: () -> Unit) {
    ChoiceCard(
        title = title,
        subtitle = orderLine(steps, types),
        selected = selected,
        onClick = onClick,
        extra = { BadgeStrip(steps, types, Modifier.padding(top = 6.dp)) },
    )
}

/** "8일마다 반복 · 주 평균 38시간 30분" */
internal fun orderLine(steps: List<Long?>, types: Map<Long, ShiftType>): String =
    "${steps.size}일마다 반복 · 주 평균 ${HoursFormat.korean(ShiftBundles.weeklyMinutes(steps, types))}"

/** [SETUP-02] An order typed a day at a time; a day tapped is changed by the next shift tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RotationEditor(
    d: SetupDraft, types: Map<Long, ShiftType>, onChange: (List<Long?>) -> Unit,
    today: java.time.LocalDate, onToday: (Int) -> Unit = {}, realToday: java.time.LocalDate = today,
    /** [RVH-32] 검토 WORK-09 The week as the calendar shows it (it was always Sunday first here). */
    weekStart: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY,
) {
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val steps = d.rotation
    // [EASE-14] Where today is in the order, kept while the order is typed (a change of the order clears the setup's answer,
    // so a start chosen here is put back after each key).
    var todayStep by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(todayStepOf(d.anchor, d.rotation.size, today)) }
    var startChosen by rememberSaveable { mutableStateOf(d.anchor != null) }
    androidx.compose.runtime.LaunchedEffect(steps) {
        if (startChosen && steps.isNotEmpty() && d.anchor == null) onToday(Math.floorMod(todayStep, steps.size))
    }
    val choices: List<Long?> = d.types.map { it.id } + if (d.types.none { it.isOff }) listOf<Long?>(null) else emptyList()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val i = picked?.takeIf { it < steps.size }
            Text(
                when {
                    i != null -> "${i + 1}번째 날을 무엇으로 바꿀까요?"
                    steps.isEmpty() -> "첫날부터 순서대로 눌러 주세요"
                    else -> "이어서 누르거나, 바꿀 날을 눌러 고쳐요"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (steps.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    steps.forEachIndexed { index, id ->
                        StepCell(index, id?.let(types::get), selected = index == i) { picked = if (picked == index) null else index }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { id ->
                    val t = id?.let(types::get)
                    FilledTonalButton(
                        onClick = {
                            if (i != null) {
                                onChange(steps.toMutableList().also { it[i] = id })
                                picked = null
                            } else if (steps.size < SetupViewModel.MAX_STEPS) {
                                onChange(steps + id)
                            }
                        },
                        enabled = i != null || steps.size < SetupViewModel.MAX_STEPS,
                    ) {
                        ShiftBadge(t, size = 20.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
                        Spacer(Modifier.width(6.dp))
                        Text(t?.name ?: "휴무")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(steps.dropLast(1)); picked = null }, enabled = steps.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, null)
                    Spacer(Modifier.width(6.dp))
                    Text("하나 지우기")
                }
                TextButton(onClick = { onChange(emptyList()); picked = null }, enabled = steps.isNotEmpty()) { Text("모두 지우기") }
            }
            if (steps.isNotEmpty()) {
                Text(orderLine(steps, types), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                // [EASE-14] The order repeated over the months at once, and today's place in it.
                OrderPreview(steps, types, today, todayStep, onToday = { s -> todayStep = s; startChosen = true; onToday(s) }, weekStart = weekStart, realToday = realToday)
            }
        }
    }
}

@Composable
private fun StepCell(index: Int, type: ShiftType?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            // [VIS-02] 48dp to the finger, though the badge is smaller.
            .sizeIn(minWidth = 44.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
            .toggleable(value = selected, role = Role.Button, onValueChange = { onClick() })
            .semantics { contentDescription = "${index + 1}번째 날 ${type?.name ?: "휴무"}" }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShiftBadge(type, size = 26.dp, text = type?.label ?: "휴", outlined = type == null || type.isOff)
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- the same days every week ----

private val WEEK = DayOfWeek.entries

/**
 * [WORK-01] Monday to Sunday. With one shift the days are seven toggles; with several each day picks
 * its shift or none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekDays(d: SetupDraft, onSet: (Int, Long?) -> Unit) {
    val work = d.workTypes
    if (work.size == 1) {
        val t = work.first()
        val color = t.colorArgb.asColor()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WEEK.forEachIndexed { i, day ->
                val on = d.weekly.getOrNull(i) == t.id
                // [RVH-31] 검토 WORK-10 48dp to tap, the circle as before. [J133-39] 순번 719: an off day reads as 쉼, not as the shift it would be.
                com.bamnat.app.ui.shifts.DayToggle(day, on, color, null, Modifier.weight(1f), size = 44.dp,
                    said = "${Fmt.dayOfWeekShort(day)}요일 ${if (on) t.name else "쉼"}", style = MaterialTheme.typography.titleMedium,
                ) { onSet(i, if (it) t.id else null) }
            }
        }
        Text(
            "${t.name} ${hoursOf(t)} · 일하는 날 ${d.weekly.count { it == t.id }}일",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // 1.37 PC MED-2 사용성 제안 (알바 출근 요일: 칩 4개가 한 줄에 빽빽하고 '저녁 알바'가 안 보임): a row of the seven days for each
    // kind of shift, as for one, so every kind is in sight; a day takes one kind (another tapped on it takes its place).
    work.forEach { t ->
        val color = t.colorArgb.asColor()
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShiftBadge(t, size = 20.dp)
            Text("${t.name} ${hoursOf(t)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${d.weekly.count { it == t.id }}일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WEEK.forEachIndexed { i, day ->
                val on = d.weekly.getOrNull(i) == t.id
                val other = d.weekly.getOrNull(i)?.takeIf { it != t.id && !d.isOff(it) }?.let(d::type)
                com.bamnat.app.ui.shifts.DayToggle(day, on, color, other, Modifier.weight(1f), size = 40.dp,
                    said = "${Fmt.dayOfWeekShort(day)}요일 ${t.name} " + when {
                        on -> "일해요"
                        other != null -> "아니고 ${other.name}"
                        else -> "쉼"
                    },
                ) { onSet(i, if (it) t.id else null) }
            }
        }
    }
    Text("한 요일에는 한 가지만 넣어요. 다른 줄에서 같은 요일을 누르면 그쪽으로 옮겨요.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HourlyDays(vm: SetupViewModel, d: SetupDraft, header: Boolean = true) {
    if (header) StepHeader("일하는 요일이 정해져 있나요?", "정해져 있으면 달력에 미리 채워 둘게요.")
    else PartTitle("일하는 요일이 정해져 있나요?", "정해져 있으면 달력에 미리 채워 둘게요.")
    ChoiceCard(
        title = "네, 정해져 있어요",
        subtitle = "요일마다 일하는 시간대를 골라요",
        selected = !d.noFixedDays,
        onClick = { vm.setNoFixedDays(false) },
    )
    ChoiceCard(
        title = "아니요, 그때그때 달라요",
        subtitle = "일한 날 달력을 눌러 적으면 돼요",
        icon = Icons.Filled.EventBusy,
        selected = d.noFixedDays,
        onClick = { vm.setNoFixedDays(true) },
    )
    if (!d.noFixedDays) {
        WeekDays(d, onSet = vm::setWeekly)
        HolidaysOffRow(d.holidaysOff, vm::setHolidaysOff)
    }
}

/** [J133-03] 공휴일에 쉬나요? asked once with the days, on by default. */
@Composable
private fun HolidaysOffRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("공휴일(빨간날)에는 쉬어요", style = MaterialTheme.typography.titleSmall)
            Text(if (on) "한글날·성탄절 같은 날은 쉬는 날로 둬요. 출근하는 날만 달력에서 바꾸면 돼요."
                else "공휴일에도 요일대로 일해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
    }
}

// ---- a roster painted every month ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterPaint(vm: SetupViewModel, d: SetupDraft) {
    StepHeader(
        "이번 달 근무표를 넣어 볼까요?",
        "근무표 사진이나 파일이 있으면 읽어서 칠해 드려요. 없으면 근무를 고르고 날짜를 누르거나 손가락으로 쓸어 칠해요.",
    )
    val tool by vm.paintTool.collectAsStateWithLifecycle()
    val month by vm.paintMonth.collectAsStateWithLifecycle()
    val canUndo by vm.canUndoPaint.collectAsStateWithLifecycle()
    RosterSource(vm, d)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        d.types.forEach { t ->
            val stamp = PaintTool.Shift(t.id)
            FilterChip(
                selected = tool == stamp,
                onClick = { vm.setPaintTool(stamp) },
                label = { Text(t.name) },
                leadingIcon = { ShiftBadge(t, size = 18.dp, outlined = t.isOff) },
                modifier = Modifier.semantics { contentDescription = "${t.name} 칠하기" },
            )
        }
        if (d.types.none { it.isOff }) {
            FilterChip(selected = tool == PaintTool.Off, onClick = { vm.setPaintTool(PaintTool.Off) }, label = { Text("쉬는 날") })
        }
        FilterChip(
            selected = tool == PaintTool.Clear,
            onClick = { vm.setPaintTool(PaintTool.Clear) },
            label = { Text("지우기") },
            leadingIcon = { Icon(Icons.Filled.LayersClear, null, Modifier.size(18.dp)) },
        )
    }
    val thisMonth = YearMonth.from(vm.today)
    SetupMonth(
        month = month,
        snapshot = remember(d.types, d.painted) { ScheduleSnapshot(d.types, null, d.paintedEntries()) },
        weekStart = vm.weekStart,
        today = vm.today,
        selected = null,
        // This month and the next: a roster further ahead is not out yet.
        onMonth = vm::showPaintMonth,
        first = thisMonth,
        last = thisMonth.plusMonths(1),
        onDayClick = vm::paintTap,
        onPaint = if (tool != PaintTool.Pick) vm::paint else null,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (d.painted.isEmpty()) "아직 칠한 날이 없어요" else "칠한 날 ${d.painted.size}일",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canUndo) {
            TextButton(onClick = vm::undoPaint) {
                Icon(Icons.AutoMirrored.Filled.Undo, null)
                Spacer(Modifier.width(6.dp))
                Text("되돌리기")
            }
        }
    }
}

/**
 * 1.36 PC W10: the roster being read, the reading under way and a way out. 2.0.0 [RVL-32] (검토 SCAN-04·U07b-01·I07ba-01) the
 * same reading line as 한 달 근무 넣기: the reading model coming down with its percent, which way is being read, and 멈추기.
 */
@Composable
private fun RosterReadingCard(vm: SetupViewModel) {
    val step by vm.rosterStep.collectAsStateWithLifecycle()
    val preparing by vm.rosterPreparing.collectAsStateWithLifecycle()
    com.bamnat.app.ui.imports.ReadingLine(step.takeIf { it.first > 0 }, preparing, action = "멈추기", onAction = vm::cancelRosterRead)
}

/**
 * [SETUP-15] 듀티표가 있어요 → 사진 · 파일 · 직접 칠하기 (중-8): a nurse gets the month's roster every month, so reading
 * it is offered first; what is read is painted below, where any day is fixed by painting over it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterSource(vm: SetupViewModel, d: SetupDraft) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val roster by vm.roster.collectAsStateWithLifecycle()
    val reading by vm.rosterReading.collectAsStateWithLifecycle()
    val files = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.readRosterFile(context, it) }
    }
    // [ROST-09] A roster shared to the app while the setup is open is read here.
    val shared by com.bamnat.app.IncomingRoster.shared.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(shared) {
        when (val s = if (shared != null) com.bamnat.app.IncomingRoster.take() else null) {
            is com.bamnat.app.IncomingRoster.Shared.Photo -> vm.readRosterPhoto(context, s.uri)
            is com.bamnat.app.IncomingRoster.Shared.Text -> vm.readRosterText(s.text)
            is com.bamnat.app.IncomingRoster.Shared.File -> vm.readRosterFile(context, s.uri)
            null -> Unit
        }
    }
    // 2.0.0 [RVL-34] (검토 SCAN-20·U07b-08·U07b-14) The same ways and names as 한 달 근무 넣기: Google's scanner, or '사진 찍기' on a
    // phone that cannot run it (no chip that leads nowhere), '사진 고르기' and '파일 고르기', each one tap as before.
    val ways = com.bamnat.app.ui.imports.rememberRosterWays(
        onPhoto = { vm.readRosterPhoto(context, it) }, onTaken = { vm.readRosterPhoto(context, it) },
        onFile = { vm.readRosterFile(context, it) }, onPaste = {}, onError = { vm.say(it) },
    )
    // 2.0.0 [RVL-33] (검토 U07b-02·I07ba-01) A reading that gave nothing: why with its one fix, and the next steps on the card itself.
    val failed = roster?.takeIf { !reading && it.rows.isEmpty() }?.error
    val modelOnly = roster?.fix.let { it != null && it != com.bamnat.app.pay.OcrModel.Fix.AGAIN }
    Text("근무표가 있어요", style = MaterialTheme.typography.titleSmall)
    // Under a failure the card holds the next steps (as on 한 달 근무 넣기), so the chips do not stand beside it twice; X brings them back.
    if (failed == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // [READ-15] 1.27: straightened by Google's scanner on the phone.
        FilterChip(selected = false, enabled = !reading, onClick = ways.first,
            label = { Text(if (ways.scanner) com.bamnat.app.ui.imports.SCANNER else com.bamnat.app.ui.imports.CAMERA) },
            leadingIcon = { Icon(if (ways.scanner) Icons.Filled.DocumentScanner else Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)) })
        FilterChip(selected = false, enabled = !reading, onClick = ways.pick,
            label = { Text(com.bamnat.app.ui.imports.PICK) }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)) })
        FilterChip(selected = false, enabled = !reading, onClick = { files.launch(com.bamnat.app.ui.imports.SheetFiles.ROSTER_TYPES) },
            label = { Text(com.bamnat.app.ui.imports.FILE) }, leadingIcon = { Icon(Icons.Filled.Description, null, Modifier.size(18.dp)) })
        FilterChip(selected = roster == null, onClick = vm::clearRoster, label = { Text("직접 칠하기") }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) })
    }
    // [READ-04][READ-05] The photo itself: tapped when it read nothing or the wrong thing, or looked at to see what was read.
    var tapping by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var seeing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // 1.36 PC W10 (사진을 고른 뒤 20초 넘게 아무 말 없음): at once a line that says it is reading and how far, with 멈추기.
    if (reading) RosterReadingCard(vm)
    if (failed != null) com.bamnat.app.ui.imports.ReadFailed(
        failed, roster?.fix,
        onFix = {
            when (val f = roster?.fix) {
                com.bamnat.app.pay.OcrModel.Fix.AGAIN -> vm.readRosterAgain()
                null -> Unit
                else -> com.bamnat.app.ui.imports.openFix(context, f)
            }
        },
        onClose = vm::heardRosterError,
        onTapPhoto = roster?.scan?.image?.let { { tapping = true } },
        // Only the reading model was missing: the same photo read again once that is fixed.
        onRetake = if (modelOnly) vm::readRosterAgain else ways.first,
        onByHand = vm::clearRoster,
        retakeLabel = when {
            modelOnly -> "다시 읽기"
            ways.scanner -> "스캐너로 다시 찍기"
            else -> "다시 찍기"
        },
    )
    roster?.advice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    roster?.scan?.let { scan ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Under a failure the card above has it already.
            if (scan.image != null && failed == null) FilterChip(selected = false, enabled = !reading, onClick = { tapping = true }, label = { Text(com.bamnat.app.ui.imports.TAP_PHOTO) })
            FilterChip(selected = false, onClick = { seeing = true }, label = { Text("무엇을 읽었는지 보기") })
        }
        // [PHOTO-09] 1.29 통합: the first setup's roster photo gets '사진이 달라요' too, like every other photo result.
        if (roster?.from == com.bamnat.core.journal.RosterFrom.PHOTO && !reading) {
            com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.ROSTER)
        }
        if (tapping) com.bamnat.app.ui.imports.PhotoTapDialog(scan, onDismiss = { tapping = false }) { hint -> tapping = false; vm.readRosterTapped(hint) }
        if (seeing) com.bamnat.app.ui.imports.ReadDiagnosisDialog(scan, roster?.rows.orEmpty(), onDismiss = { seeing = false })
    }
    val rows = roster?.rows.orEmpty()
    if (rows.size > 1) com.bamnat.app.ui.imports.WhichLine(rows, d.types, onPick = vm::useRosterRow)
    // [READ-17] 1.27: the picked line beside the photo, "사진과 내용이 같은가요?", a cell fixed on its own.
    val same by vm.rosterSame.collectAsStateWithLifecycle()
    var fixing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val photoScan = roster?.scan
    val painting by vm.paintMonth.collectAsStateWithLifecycle()
    val month = roster?.month ?: painting
    if (photoScan != null && rows.size == 1 && roster?.from == com.bamnat.core.journal.RosterFrom.PHOTO && !reading) {
        val row = rows[0]
        val cells = (1..month.lengthOfMonth()).mapNotNull { day ->
            val date = month.atDay(day)
            val cell = d.leaves[date]?.let { com.bamnat.core.imports.DutyCell.Leave(it.kind, it.quarters, it.fromStart) }
                ?: if (d.painted.containsKey(date)) d.painted[date]?.let { com.bamnat.core.imports.DutyCell.Shift(it) } ?: com.bamnat.core.imports.DutyCell.Off
                else row.cells[day]?.takeIf { it is com.bamnat.core.imports.DutyCell.Unknown }
            cell?.let { day to it }
        }.toMap()
        com.bamnat.app.ui.imports.SameAsPhotoCard(
            photoScan, row, vm.rosterRank, cells, if (same == true) emptySet() else row.doubtful(month.lengthOfMonth()), d.types,
            month.lengthOfMonth(), same, onAnswer = { vm.rosterSame(it) }, onFix = { fixing = it }, onAgain = { vm.rosterSame(null) },
        )
    }
    fixing?.let { day ->
        com.bamnat.app.ui.imports.CellDialog(month.atDay(day), d.types, onDismiss = { fixing = null }) { cell -> vm.fixRosterDay(month.atDay(day), cell); fixing = null }
    }
    // [READ-36] 1.38 PC 8차: the sheet says 주·야·비·휴 and the setup 데이·나이트·오프: its own letters offered as the kinds, once.
    val sheetKinds by vm.sheetKinds.collectAsStateWithLifecycle()
    sheetKinds?.let { kinds ->
        androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("근무표의 글자로 근무 종류를 맞출까요?", style = MaterialTheme.typography.titleSmall)
                Text(kinds.joinToString(" · ") { k -> "${k.type.name}(${k.type.label})" + (k.type.start?.let { s -> " ${s.hour}~${k.type.end?.hour ?: ""}시" } ?: "") },
                    style = MaterialTheme.typography.bodyMedium)
                Text("지금은 ${d.types.joinToString("·") { it.name }}로 들어가요. 맞추면 비번과 휴무도 따로 남아요. " +
                    (if (vm.sheetKindsKeepTimes(kinds)) "시간은 고른 시간 그대로예요. " else "근무표에 시간이 없어 흔한 시간으로 넣어요. ") + "시간은 근무 시간 단계에서 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bamnat.app.ui.components.Button(onClick = vm::useSheetKinds) { Text("글자대로 맞추기") }
                    androidx.compose.material3.TextButton(onClick = vm::keepKinds) { Text("지금 그대로") }
                }
            }
        }
    }
    // [SETUP-15] A code the setup's kinds do not cover: asked here, one at a time, so it goes in with the setup.
    val asks by vm.rosterAsks.collectAsStateWithLifecycle()
    asks.firstOrNull()?.let { (ask, days) ->
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // [READ-36] 1.38 PC 8차: "(1일)" read as the 1st; the days themselves are said ("21일", "3·10·17일 외 2칸").
                val sorted = days.map { it.dayOfMonth }.sorted()
                val where = sorted.take(4).joinToString("·") + "일" + if (sorted.size > 4) " 외 ${sorted.size - 4}칸" else ""
                Text("‘${ask.raw}’로 읽은 칸($where)은 무엇인가요?", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 2.0.0 [RVL-21] (검토 SCAN-06) AM·V·반·생 are asked now: the leave each most often is, first.
                    com.bamnat.core.imports.DutyCodes.guessOf(ask.raw)?.takeIf { !ask.leave }?.let { (name, cell) ->
                        FilterChip(selected = false, onClick = { vm.answerRosterCode(ask.raw, cell) }, label = { Text(name) })
                    }
                    if (!ask.leave) d.types.forEach { t ->
                        FilterChip(selected = false, onClick = { vm.answerRosterCode(ask.raw, com.bamnat.core.imports.DutyCell.Shift(t.id)) }, label = { Text(t.name) })
                    }
                    com.bamnat.core.model.LeaveKind.entries.forEach { k ->
                        FilterChip(selected = false, onClick = { vm.answerRosterCode(ask.raw, com.bamnat.core.imports.DutyCell.Leave(k)) }, label = { Text(k.label) })
                    }
                    FilterChip(selected = false, onClick = { vm.answerRosterCode(ask.raw, null) }, label = { Text("넣지 않기") })
                }
            }
        }
    }
}

/**
 * A month of the draft as the calendar will show it, with ‹ › between [first] and [last]. Painting
 * is on while [onPaint] is given; the long-press menu of the calendar is not offered here.
 */
@Composable
internal fun SetupMonth(
    month: YearMonth,
    snapshot: ScheduleSnapshot,
    weekStart: DayOfWeek,
    today: LocalDate,
    selected: LocalDate?,
    onMonth: (YearMonth) -> Unit,
    first: YearMonth,
    last: YearMonth,
    onDayClick: (LocalDate) -> Unit = {},
    onPaint: ((List<LocalDate>) -> Unit)? = null,
) {
    val calculator = remember(snapshot) { WorkHoursCalculator(snapshot.resolver) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > first) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                }
                Text(Fmt.month(month), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < last) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                }
            }
            WeekdayHeader(weekStart, Modifier.padding(start = WEEK_BAR_SPACE))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Same cell as 내 달력, so a holiday name under the shift has its line (a 60dp cell cut it off on a folded phone).
                val cellHeight = (maxWidth / 7 * 1.3f).coerceIn(66.dp, 100.dp) + cellTextGrowth()
                MonthGrid(
                    month = month,
                    snapshot = snapshot,
                    calculator = calculator,
                    weekStart = weekStart,
                    selected = selected ?: LocalDate.MIN,
                    today = today,
                    memoDates = emptySet(),
                    showHours = false,
                    cellHeight = cellHeight,
                    onDayClick = onDayClick,
                    onQuickAction = { _, _ -> },
                    onPaint = onPaint,
                    quickMenu = false,
                    modifier = Modifier.heightIn(min = cellHeight * 5),
                )
            }
        }
    }
}
