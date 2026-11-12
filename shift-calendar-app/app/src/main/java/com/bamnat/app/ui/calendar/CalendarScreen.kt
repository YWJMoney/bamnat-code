package com.bamnat.app.ui.calendar

import com.bamnat.app.ui.parts.partSpot
import com.bamnat.core.work.words
import com.bamnat.app.ui.decor.decor
import com.bamnat.app.ui.theme.monthTurn
import androidx.compose.ui.layout.layout
import com.bamnat.app.ui.theme.PhotoBackdrop
import com.bamnat.app.ui.components.labelWidth
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.em
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.schedule.ResolvedDay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import com.bamnat.app.appContainer
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.DropdownMenuItem
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.bamnat.app.ui.components.sheetScrollGuard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import com.bamnat.app.ui.theme.color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bamnat.app.alarm.Notifications
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.rules
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.appViewModel
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.SystemSettings
import com.bamnat.app.ui.components.rememberNotificationAsk
import com.bamnat.app.NoticeLinks
import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.app.review.InAppReview
import com.bamnat.app.review.InAppReview.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.AlarmOff
import com.bamnat.core.work.calendarStart
import java.time.LocalDate
import com.bamnat.app.ui.theme.LocalCalendarColors
import java.time.YearMonth
import com.bamnat.core.work.ShiftMonth
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.model.ShiftOverride
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.bamnat.app.data.PhoneDayView
import com.bamnat.core.time.Today
import androidx.compose.material.icons.filled.Today

internal const val PAGE_COUNT = 2400
/** [CAL-01] Alarm warnings show while an alarm is due within this many days. */
private const val ALARM_WARNING_DAYS = 7L

/** [ALRM-25] 1.38 PC 9차: notifications off; an alarm still sounds but its screen does not show, so nothing stops it on the lock screen. */
internal const val NOTICE_OFF_LINE = "알림이 꺼져 있어 알람이 소리만 나요"
/** [CAL-51] Under this many dp tall (360×640, 320×568) the summary is one line and the parts above the month fold into the notices. */
private const val LOW_SCREEN_DP = 700
/** [CAL-51] Under this many dp tall a phone is on its side: the bars share one row, only the 알림 line shows, cells are short. */
internal const val SIDEWAYS_SCREEN_DP = 480
internal const val CENTER_PAGE = PAGE_COUNT / 2
/** [VIS-02] A month slides this long after ‹ ›. */
private const val MONTH_SLIDE_MILLIS = 220
/** [CAL-03] How far a finger goes sideways on the week before it turns. */
private val WEEK_SWIPE = 48.dp
/** [CAL-01] The unfolded day panel: room for "10월 18일 (일) 오늘" and its arrows, never most of the screen. */
private val DAY_PANEL_MIN_WIDTH = 280.dp
private val DAY_PANEL_MAX_WIDTH = 420.dp
/** [CAL-45] The month keypad needs seven 44dp cells and their gaps. */
private val DUTY_PANEL_MIN_WIDTH = 340.dp

/** [CAL-30] A month kept as its "2026-11" text through a fold or a turn. */
internal val YearMonthSaver = androidx.compose.runtime.saveable.Saver<YearMonth?, String>(
    save = { it?.toString() ?: "" },
    restore = { it.takeIf(String::isNotEmpty)?.let(YearMonth::parse) },
)
/** [CAL-15] The floating button's height and the room it keeps from the screen's edge. */
private val FAB_HEIGHT = 56.dp

/**
 * 12.1: the month grid's rows the FAB must not sit on, counted from the top: up to the row of the month's last day, or the row
 * before when that day is in the left half (the FAB rests at the right, over next month's faded days then).
 */
/** 2.0.0 [RVH-03] 검토 CAL-04: the first day of [date]'s week as the month view draws weeks ([weekStart] 일요일 or 월요일). */
internal fun weekFirstDay(date: LocalDate, weekStart: java.time.DayOfWeek): LocalDate =
    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))

/** [RVH-03] "11월 셋째 주": the row of the month view [date] sits on, counted as that view counts them. */
internal fun weekOfMonthLabel(date: LocalDate, weekStart: java.time.DayOfWeek): String {
    val first = weekFirstDay(date.withDayOfMonth(1), weekStart)
    val row = (java.time.temporal.ChronoUnit.DAYS.between(first, weekFirstDay(date, weekStart)) / 7).toInt()
    val n = listOf("첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째").getOrElse(row) { "${row + 1}째" }
    return "${date.monthValue}월 $n 주"
}

internal fun fabKeepRows(month: YearMonth, weekStart: java.time.DayOfWeek): Int {
    val first = month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
    val index = java.time.temporal.ChronoUnit.DAYS.between(first, month.atEndOfMonth()).toInt()
    val row = index / 7
    return if (index % 7 < 4) row else row + 1
}
private val FAB_MARGIN = 16.dp
/** [CAL-15] Between the month's last row and the button riding under it. */
private val FAB_DOCK_GAP = 8.dp
/** [CAL-15] Room under the month for the button: the gap above it, its height and the same gap below. */
private val FAB_ROOM = FAB_DOCK_GAP + FAB_HEIGHT + FAB_DOCK_GAP
/** [DAILY-09] Above the month in a half-open fold's upper half: its 2026년 10월 row and weekday names. */
private val MONTH_ROW_ROOM = 96.dp
private const val FAB_STEP_MILLIS = 180
/** [CAL-17] The tip comes just after the tap has shown its day, so it is read as being about the tap. */
private const val TIP_DELAY_MILLIS = 400L

/** Fixed, so a saved pager page always maps back to the same month (even after a restart). */
internal val BASE_MONTH: YearMonth = YearMonth.of(2026, 1)

internal fun pageToMonth(base: YearMonth, page: Int): YearMonth = base.plusMonths((page - CENTER_PAGE).toLong())
internal fun monthToPage(base: YearMonth, month: YearMonth): Int =
    (CENTER_PAGE + ChronoUnit.MONTHS.between(base, month)).toInt().coerceIn(0, PAGE_COUNT - 1)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    wide: Boolean,
    snackbarHostState: SnackbarHostState,
    /** A memo by id, and the day it was opened from (a repeating memo's day, for 이 날만 빼기), null for none. */
    onOpenMemo: (Long, LocalDate?) -> Unit,
    onNewMemo: (LocalDate) -> Unit,
    onOpenPattern: () -> Unit,
    onOpenShiftTypes: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenMoney: () -> Unit = {},
    onOpenPay: () -> Unit = onOpenStats,
    onOpenSettings: () -> Unit,
    onOpenExtraKinds: () -> Unit = {},
    /** [MCAL-02] Starts the setup of a new calendar with this id. */
    onSetupCalendar: (String) -> Unit = {},
    /** [MCAL-04] The calendar list: order, names, removing one. */
    onManageCalendars: () -> Unit = {},
    /** [CALS-01] One of the user's calendars: its name, colour, kind of work and settings. */
    onEditCalendar: (String) -> Unit = {},
    /** [COMN-01] 모든 달력 합쳐 보기. */
    onOpenCombined: () -> Unit = {},
    /**
     * [READ-10] 이번 달 전부 넣기 → 한 달 근무 넣기 on that month (1.25 순번 371, 사용자 "빠른입력에서 이번달 전체넣기에 듀티표
     * 사진과 입력 도구"): the keypad there, with 사진·엑셀·CSV beside it. Null keeps the keypad sheet over the calendar.
     */
    onFillMonth: ((YearMonth) -> Unit)? = null,
) {
    // [DAILY-09] Half open like a laptop (Flex): the day sits below the hinge as it sits beside the month when
    // unfolded, so everything a wide screen does (no sheet, the day always shown) holds there too.
    val tabletop = com.bamnat.app.ui.LocalTabletop.current
    // [CAL-51] 상-4: a phone on its side (under 480dp tall) keeps the rail but not the two panes: beside the day panel
    // its month had no room left under the bars above it.
    @Suppress("NAME_SHADOWING") val wide = wide && LocalConfiguration.current.screenHeightDp >= SIDEWAYS_SCREEN_DP || tabletop != null
    val context = LocalContext.current
    // Re-checked on every return to the app, since the user may change it in system settings.
    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    val askNotifications = rememberNotificationAsk()
    var fullScreenAllowed by remember { mutableStateOf(Notifications.canUseFullScreen(context)) }
    var alarmChannelTrouble by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = Notifications.canPost(context)
        fullScreenAllowed = Notifications.canUseFullScreen(context)
        com.bamnat.app.alarm.AlarmsOff.activeUntil(context)
        alarmChannelTrouble = if (notificationsAllowed) Notifications.alarmChannelTrouble(context) else null
    }
    // 2.0.0 [RVH-15] 검토 CAL-20: going behind another app keeps the day and month, for opening there if the phone closes 밤낮 meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.keepPlace() }
    // [ALRM-09] 오늘 남은 알람 모두 끄기, until when.
    val alarmsOffUntil by com.bamnat.app.alarm.AlarmsOff.until.collectAsStateWithLifecycle()
    val safeStart by com.bamnat.app.diag.StartGuard.safe.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    // [PERF-01] The month is there: Android's "Fully drawn" for this start, and the startup budget's last step.
    androidx.activity.compose.ReportDrawnWhen { snapshot != null }
    // [PERF-11] 1.28 AO: false until the first month of this run is on screen; see [FirstMonth].
    val settled by FirstMonth.settled
    LaunchedEffect(snapshot != null) {
        if (snapshot != null) {
            // [PERF-15] 1.28 AO: the opening's times, kept on this phone (진단) and summed up for 사용 통계 when allowed.
            com.bamnat.app.StartupTrace.monthDrawn()?.let(context.appContainer.startupStats::record)
            // [PERF-03] The month is there: the jobs that waited for it start now.
            context.appContainer.afterFirstFrame()
            // [PERF-11] The frame after it brings the neighbouring months and the cards under the month.
            androidx.compose.runtime.withFrameNanos { }
            FirstMonth.settled.value = true
        }
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    // [CUST-01] The page's parts in the user's order (화면 꾸미기).
    // [FIT-01][FIT-02] Only the parts that suit this calendar's kind of work, and those the user added to it.
    val fitCalendar by vm.activeCalendar.collectAsStateWithLifecycle()
    val fitAdded = com.bamnat.app.ui.guide.rememberFitAdded(fitCalendar.id)
    val calendarParts = remember(settings, fitAdded) { settings.calendarParts(fitAdded) }
    fun partShown(p: CalendarPart) = calendarParts.firstOrNull { it.part == p }?.shown != false
    // [CVW-06] 1.35: which side of the month each part stands, by where 이번 달 달력 is in the order.
    val isAbove = remember(calendarParts) { calendarAbove(calendarParts.map { it.part }) }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val paint by vm.paint.collectAsStateWithLifecycle()
    // [CAL-30] Saved, so the month being typed is still open after folding or unfolding (I 리뷰 P11).
    var typingMonth by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf<YearMonth?>(null) }
    // [READ-10] Unfolded, the keypad stays beside the month (CAL-45); elsewhere the month opens in 한 달 근무 넣기.
    val fillMonth: (YearMonth) -> Unit = { m -> if (wide && tabletop == null || onFillMonth == null) typingMonth = m else onFillMonth(m) }
    // [REST-01] 근무표 점검 기준, opened from the check's card.
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    val lastPaint by vm.lastPaint.collectAsStateWithLifecycle()
    val coverAsk by vm.coverAsk.collectAsStateWithLifecycle()
    val restAlarms by vm.restAlarms.collectAsStateWithLifecycle()
    // [CUST-01] 화면 꾸미기: the page's parts in the user's order, and the sheet that changes them.
    var editingLayout by rememberSaveable { mutableStateOf(false) }
    // [CUST-01] 화면 꾸미기 opened from the help search or the update notice.
    val featureFocus by com.bamnat.app.ui.guide.FeatureLinks.focus.collectAsStateWithLifecycle()
    LaunchedEffect(featureFocus) {
        if (featureFocus == "customize" && com.bamnat.app.ui.guide.FeatureLinks.take("customize")) com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.STUDIO_CALENDAR)
    }
    val memoDates by vm.memoDates.collectAsStateWithLifecycle()
    val moneyBook by vm.moneyBook.collectAsStateWithLifecycle()
    val datedMemos by vm.datedMemos.collectAsStateWithLifecycle()
    // [CAL-05..CAL-13] What the calendar is about, its view, its alarms and quick input's marks.
    val focus by vm.focus.collectAsStateWithLifecycle()
    val listView by vm.listView.collectAsStateWithLifecycle()
    val calendarAlarmsOn by vm.calendarAlarmsOn.collectAsStateWithLifecycle()
    val mark by vm.mark.collectAsStateWithLifecycle()
    // [CAL-38] Quick input's extra-work stamps.
    val stamp by vm.stamp.collectAsStateWithLifecycle()
    // [CAL-45] Unfolded, 모든 조 can sit beside the month in place of the day.
    var teamsBeside by rememberSaveable { mutableStateOf(false) }
    val rangeFrom by vm.rangeFrom.collectAsStateWithLifecycle()
    val holidaysKept by vm.holidaysKept.collectAsStateWithLifecycle()
    val payProfile by vm.payProfile.collectAsStateWithLifecycle()
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    // [SHR-02] Calendars others shared, switched to like accounts.
    val received = appViewModel { ReceivedViewModel(it) }
    val receivedList by received.calendars.collectAsStateWithLifecycle()
    val viewingReceived by received.viewing.collectAsStateWithLifecycle()
    val openingLink by received.opening.collectAsStateWithLifecycle()
    var switching by rememberSaveable { mutableStateOf(false) }
    // [MCAL-01] The user's own calendars, the one on screen, and the list's order with the received ones.
    val myCalendars by vm.calendars.collectAsStateWithLifecycle()
    val activeCalendar by vm.activeCalendar.collectAsStateWithLifecycle()
    // 1.32 [WTYP-11] 희망 오프 of this calendar, on the grid while the feature is on and the calendar is 교대.
    val wishAll by context.appContainer.wishOff.all.collectAsStateWithLifecycle()
    val wishOff = wishAll[activeCalendar.id] ?: com.bamnat.core.work.WishOff()
    val guideNow by com.bamnat.app.data.GuideStore.get(context).state.collectAsStateWithLifecycle()
    val wishShown = wishOff.days.isNotEmpty() && com.bamnat.core.work.WishOff.offered(settings.workStyle) &&
        remember(guideNow, activeCalendar.id) { !com.bamnat.app.ui.guide.FeatureSwitches.hidden(context.appContainer, com.bamnat.app.calendar.DateNoticeAlarm.WISH_OFF, activeCalendar.id) }
    val calendarOrder by vm.calendarOrder.collectAsStateWithLifecycle()
    val onAddCalendar = rememberAddCalendar(context.appContainer, onSetupCalendar)
    // A link id to open, "" to paste one, null when closed.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openingLink) {
        openingLink?.let { id ->
            adding = id
            received.consumeOpening()
        }
    }
    // [FEAT-17][DAY-04] Cells can show the start of the day's first memo under the date.
    // [CVW-01] 1.35 달력 모양's 메모 줄: each memo of the day a line, as many as it gives (the cell cuts them to its width).
    val calView = LocalCalendarView.current
    val memoTexts = remember(datedMemos, settings.memoInCells, calView.memo) {
        val lines = calView.memo
        when {
            lines != null && lines > 0 -> datedMemos.groupBy { it.date!! }.mapValues { (_, l) ->
                l.take(lines).joinToString("\n") { m -> m.title.ifBlank { m.preview }.trim().lineSequence().firstOrNull().orEmpty() } }
                .filterValues { it.isNotBlank() }
            lines == 0 || !settings.memoInCells -> emptyMap()
            else -> datedMemos.groupBy { it.date!! }.mapValues { (_, l) -> l.first().let { m -> m.title.ifBlank { m.preview } }.trim().take(4) }
                .filterValues { it.isNotEmpty() }
        }
    }
    // [CELL-01] 1.39 Every memo of a day by its title, for cells that list the day's jobs and memos a line each.
    // [CELL-02] 메모 줄 0 in the user's own 달력 모양 keeps memos out of the list too (the jobs stay).
    // [CELL-07] A schedule's time against the calendar's own shifts (the night before included), as the memo editor warns.
    val clashSnap = snapshot
    val memoLists = remember(datedMemos, calView.memo, clashSnap) {
        if (calView.memo == 0) return@remember emptyMap<java.time.LocalDate, List<CellMemo>>()
        // [CELL-04] Schedules by their time, each memo in its own colour, a finished checklist struck through.
        val clashOf: ((java.time.LocalDateTime) -> String?)? = clashSnap?.let { snap -> { at ->
            val days = listOf(at.toLocalDate().minusDays(1), at.toLocalDate()).mapNotNull { d ->
                val r = snap.resolver.resolve(d)
                val type = r.shiftType ?: return@mapNotNull null
                com.bamnat.core.memo.ShiftMemoTime.Day(r.date, type.name, r.isWorkDay, r.start, r.end)
            }
            com.bamnat.core.memo.ShiftClash.of(at, days)?.text
        } }
        datedMemos.groupBy { it.date!! }.mapValues { (d, l) -> cellMemos(l, d, clashOf) }
    }
    var quickMoney by rememberSaveable { mutableStateOf(false) }
    // [INV-01] An investor calendar's trades, cells and sheets.
    val investVm = if (focus == com.bamnat.core.work.CalendarFocus.INVEST) com.bamnat.app.ui.invest.investViewModel(activeCalendar.id) else null
    val investState = investVm?.state?.collectAsStateWithLifecycle()?.value
    val investCells = remember(investState) { investState?.takeIf { it.loaded }?.let { com.bamnat.app.ui.invest.InvestCells.of(it) } }
    var investSheet by remember { mutableStateOf<com.bamnat.app.ui.invest.InvestSheet?>(null) }

    // [CAL-25] Today moves at midnight while the app is open.
    val today = rememberToday()

    // [OPS-20] Ask for a store review once, shortly after the first pattern was saved.
    // [DAILY-17] And once more after the first pay month is closed, a month or more after the first.
    val reviewMoment = InAppReview.due(settings, today)
    LaunchedEffect(reviewMoment) {
        if (reviewMoment != null) {
            delay(1500)
            // 1.39 [SPREAD-06] Not right after an ad, a payment window or an app-stop: the moment waits for the next opening.
            val crashAt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { InAppReview.lastCrash(context) }.getOrDefault(0L) }
            if (!InAppReview.calm(com.bamnat.core.time.Today.millis(), crashAt)) return@LaunchedEffect
            context.findActivity()?.let { InAppReview.ask(it) }
            vm.markReviewAsked(reviewMoment)
        }
    }

    // On phones the day opens in a sheet over the calendar. [285] Saved, so it is still open after a trip to the
    // memo editor, pay or shift types and back.
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // [CAL-03] On phones set to 그 주만 보기, a tapped date folds the month to its week with the day under it.
    var weekOnly by rememberSaveable { mutableStateOf(false) }
    val selectedNow: () -> LocalDate = { vm.selected.value }
    val dayView = settings.phoneDayView
    // [DAY-03] While the sheet is open its own snackbar says what happened, above the sheet, not hidden under it.
    val sheetSnackbar = com.bamnat.app.ui.components.AppSnack.rememberLane()
    val messageHost by rememberUpdatedState(if (sheetOpen && !wide) sheetSnackbar else snackbarHostState)
    LaunchedEffect(vm) {
        // [CAL-10] The newest change replaces a message still showing, so undo always means the last thing done.
        vm.messages.collectLatest { msg ->
            val result = messageHost.showSnackbar(
                message = msg.message,
                actionLabel = if (msg.undo != null) msg.actionLabel else null,
                duration = if (msg.long || msg.undo != null) SnackbarDuration.Long else SnackbarDuration.Short, // [J133-63] 되돌리기 stays 10 s, not 4.
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.runUndo(msg)
                // AG 1.27 [INVITE-12] The third 되돌리기 of a day, once in two weeks: a line asking what did not go well.
                if (msg.actionLabel == "되돌리기") com.bamnat.app.ui.grow.GrowAsk.afterUndo(context.appContainer, messageHost)
            }
        }
    }

    LaunchedEffect(vm) { vm.openMemo.collect { onOpenMemo(it, null) } }

    val snap = snapshot
    // [RUL-05] 1.36 탭 사이 흐름: the days' values formulas read (근무·메모·꼬리표·지출), for 조건부 꾸미기, 단추 and 규칙.
    val dayFacts = com.bamnat.app.logic.rememberDayFacts(snap, datedMemos, moneyBook, settings.workStyle, today)
    // [SPD-28] 1.31 N5: the month kept from last time, read off the main thread, drawn while the data is read.
    val ctx31 = LocalContext.current
    val firstFrame by androidx.compose.runtime.produceState<FirstFrame.Frame?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FirstFrame.load(ctx31) }
    }
    val shownMonth by vm.shownMonth.collectAsStateWithLifecycle()
    if (snap == null) {
        val f = firstFrame
        if (f != null && f.month == YearMonth.from(today) && shownMonth == f.month && viewingReceived == null) FirstFrameGrid(f)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // [SPD-28] This month as drawn, kept for the next start.
    LaunchedEffect(snap, settings.weekStart, viewingReceived) {
        if (viewingReceived == null && vm.shownMonth.value == YearMonth.from(today)) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FirstFrame.save(ctx31, snap, YearMonth.from(today), settings.weekStart)
        }
    }
    val pendingSetup by vm.setupDraft.collectAsStateWithLifecycle()
    // [WEEK-01] Hours always count Monday to Sunday, the week the law means; 한 주의 시작 only moves the grid.
    // 1.37 PC MED (알바 52/64시간 섞임; 통계 화면은 PC f42b2eba에서 맞춤): the calendar's week lines say one limit too, as 근무·급여 does:
    // the law's for the pay setup (52, or the exception's), else the user's own 한 주 근무 경고.
    val limits = remember(settings.limits, payProfile, settings.workStyle) {
        val week = (if (settings.workStyle?.usesPayEngine != false) payProfile?.let { com.bamnat.app.ui.stats.legalWeekHours(it) } else null)
            ?: settings.limits.weeklyLimitHours
        settings.limits.copy(legalWeeklyHours = week, weeklyLimitHours = week)
    }
    val calculator = remember(snap, limits) {
        WorkHoursCalculator(snap.resolver, java.time.DayOfWeek.MONDAY, limits)
    }
    // [CAL-24] A calendar of 교대 근무 whose order repeats can be split into teams: 모든 조 is on the month itself.
    val openTeams: (() -> Unit)? = remember(snap, settings.workStyle, viewingReceived, today) {
        val rotation = rotationOf(snap, today)
        if (rotation != null && viewingReceived == null && settings.workStyle == com.bamnat.core.work.WorkStyle.SHIFT) {
            { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.TEAMS) }
        } else null
    }
    // [CAL-38] The extra-work stamps quick input offers, from the calendar's own kinds and shifts.
    // 1.38 사용자 2026-11-06: no 주간·야간 대근 stamp of a kind put away ('안 쓰는 종류', 현장·자영업·투자 start so).
    val foldedKinds = com.bamnat.app.ui.shifts.foldedHere()
    // 2.0.0 [RVG-10] 검토 CAL-08: no 대근·특근·잔업 stamps for an owner (가게·투자), as their day sheet has none.
    val stamps = remember(snap.kinds, snap.shiftTypes, foldedKinds, settings.workStyle) {
        if (settings.workStyle == com.bamnat.core.work.WorkStyle.BUSINESS || settings.workStyle == com.bamnat.core.work.WorkStyle.INVEST) emptyList()
        else ExtraText.stampChoices(snap.kinds, snap.shiftTypes.filter { it.id !in foldedKinds })
    }
    // [HRLY-02][HRLY-03] An 알바 calendar's cells show times and pay, and each week its 주휴.
    val hourlyCells = remember(settings.workStyle, payProfile) {
        if (settings.workStyle != com.bamnat.core.work.WorkStyle.HOURLY) null
        else HourlyCells(payProfile, payProfile?.holidayWeekFrom ?: java.time.DayOfWeek.MONDAY)
    }
    // [CAL-14] Stable references, so the cells' painting hooks never change under them.
    val paintDays: (List<LocalDate>) -> Unit = remember(vm) { vm::paintDays }
    val startRange: (LocalDate) -> Unit = remember(vm) { vm::startRange }

    val baseMonth = BASE_MONTH
    val pagerState = rememberPagerState(initialPage = monthToPage(baseMonth, shownMonth)) { PAGE_COUNT }
    val visibleMonth = pageToMonth(baseMonth, pagerState.currentPage)
    // 2.0.0 [RVP-75] (검토 REST-18) 기능 찾기 › 이번 달 전부 넣기 opens the keypad for the month on screen, as the tool's own button does
    // (before, it opened today's sheet).
    com.bamnat.app.ui.guide.FocusedTool("typeMonth", "shift.dutyTyping") {
        if (viewingReceived == null && snap.shiftTypes.isNotEmpty()) fillMonth(visibleMonth)
    }
    // [CAL-06] The month on screen in the kind of work's own figures; pay is worked out off the main thread.
    // 1.32 [CALX-06] The month's days and hours over the 급여 기간 when 통계 counts by it, so both say the same.
    // [EASE-04] 1.38 월급날 '급여' on the cells, where the pay is counted (not 현장·자영업·투자).
    val payShown = focus == com.bamnat.core.work.CalendarFocus.SHIFTS && settings.workStyle?.usesPayEngine != false
    // 1.39 [PAYX-60] (사용자 2026-11-07): with a 급여 기간 of 21일~20일 the month's hours are 11월 월급's, 10/21~11/20, by itself.
    val payRange = payProfile?.period?.takeIf { settings.statsPeriod.byPay(it) && payShown }
        ?.let { com.bamnat.core.hours.StatsRange.of(com.bamnat.core.hours.StatsRange.Mode.PAY, visibleMonth, today, it) }
    val overview = remember(snap, visibleMonth, focus, moneyBook, payRange) {
        com.bamnat.core.work.MonthOverview.of(visibleMonth, focus, snap.resolver, moneyBook.jobs, moneyBook.sales, payRange)
    }
    // 1.36 U20: what the cards above the month said, on the quick-input sheet's head while they are hidden.
    val paintSummary = if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS)
        "${visibleMonth.monthValue}월${if (payRange != null) " 월급분" else ""} 근무 ${overview.workDays}일 · ${com.bamnat.core.hours.HoursFormat.korean(overview.workMinutes)}" else null
    // [CAL-35] Last month's figures, for the few words of change under the hours.
    // [R136-03] 1.36 (리뷰 R-04: 오늘 깐 앱이 '지난달보다 +1일'): the day this phone started, so a month wholly before it,
    // with nothing written by hand, is not compared as if worked.
    // The later of the install and this calendar's setup ('이 달력으로 할 수 있는 것' starts on it), so a phone set up again counts too.
    // [R136-03] 1.36 빌드 7 (시계를 옮기면 '11/11 전은'으로 바뀜): the day is kept once found, so it never moves after.
    val startDay = remember(activeCalendar.id) {
        val prefs = context.getSharedPreferences("calendar_start", android.content.Context.MODE_PRIVATE)
        prefs.getLong(activeCalendar.id, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { java.time.LocalDate.ofEpochDay(it) } ?: run {
            val installed = context.appContainer.installedAt?.atZone(Today.zone())?.toLocalDate()
            val setUp = com.bamnat.app.data.GuideStore.get(context).state.value.checklists[activeCalendar.id]?.startedOn
            listOfNotNull(installed, setUp).maxOrNull()?.also { prefs.edit().putLong(activeCalendar.id, it.toEpochDay()).apply() }
        }
    }
    val versus = remember(snap, visibleMonth, focus, overview) {
        val before = visibleMonth.minusMonths(1)
        val unwritten = startDay != null && before.atEndOfMonth().isBefore(startDay) &&
            snap.entries.none { YearMonth.from(it.date) == before && it.override != ShiftOverride.None }
        if (focus != com.bamnat.core.work.CalendarFocus.SHIFTS || unwritten) null
        else overview.versus(com.bamnat.core.work.MonthOverview.of(visibleMonth.minusMonths(1), focus, snap.resolver,
            range = payRange?.let { payProfile?.period?.let { p -> com.bamnat.core.hours.StatsRange.of(com.bamnat.core.hours.StatsRange.Mode.PAY, visibleMonth.minusMonths(1), today, p) } }))
    }
    val payEstimate by produceState<Long?>(null, payProfile, snap, visibleMonth, focus) {
        value = null
        if (focus != com.bamnat.core.work.CalendarFocus.SHIFTS || payProfile == null) return@produceState
        delay(150)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.monthEstimate(payProfile, snap, visibleMonth) }
    }
    // [PAYS-17] 1.27: values the pay still needs (기본급, 시수, 근무 시각 …), said under the estimate; the tap opens the pay.
    val payNeeds = remember(payProfile, snap, visibleMonth, focus) {
        val p = payProfile
        if (focus != com.bamnat.core.work.CalendarFocus.SHIFTS || p == null) 0
        else runCatching { com.bamnat.app.ui.pay.payNeeds(p, snap, visibleMonth).size }.getOrDefault(0)
    }
    // [TAX-02] 세후 beside it (프로).
    val takeHome by produceState<Long?>(null, payEstimate) { value = payEstimate?.let { vm.takeHome(payProfile, it, visibleMonth) } }
    val scope = rememberCoroutineScope()
    // A month picked from the list is there at once.
    val goToMonth: (YearMonth) -> Unit = { m -> scope.launch { pagerState.scrollToPage(monthToPage(baseMonth, m)) } }
    // [VIS-02] ‹ ›, 오늘 and a day of the next month slide over 220 ms, so it is clear which way it went.
    // Counted from where a slide is heading, so quick taps on › each move a month.
    val headingMonth = { pageToMonth(baseMonth, pagerState.targetPage) }
    val slideToMonth: (YearMonth) -> Unit = { m ->
        scope.launch {
            pagerState.animateScrollToPage(monthToPage(baseMonth, m), animationSpec = tween(MONTH_SLIDE_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    // After swiping to another month, select today (if shown) or the 1st so the detail follows.
    LaunchedEffect(pagerState, baseMonth) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            val m = pageToMonth(baseMonth, page)
            vm.showMonth(m)
            val sel = vm.selected.value
            if (YearMonth.from(sel) != m) {
                val now = Today.date()
                vm.select(if (YearMonth.from(now) == m) now else m.atDay(1))
            }
        }
    }

    // [NAV-01] Turned in 돈 or 근무·급여, the month is the one shown here on coming back.
    LaunchedEffect(shownMonth) {
        if (pageToMonth(baseMonth, pagerState.settledPage) != shownMonth) pagerState.scrollToPage(monthToPage(baseMonth, shownMonth))
    }
    // Unfolded, the day shows beside the calendar; a sheet left open must not pop up again on folding.
    LaunchedEffect(wide) { if (wide) { sheetOpen = false; weekOnly = false } }
    LaunchedEffect(dayView) { if (dayView != PhoneDayView.WEEK) weekOnly = false }
    // [CAL-03] Back to the month, it opens on the month of the day picked in the week.
    LaunchedEffect(weekOnly) {
        if (!weekOnly) {
            val m = YearMonth.from(vm.selected.value)
            if (pageToMonth(baseMonth, pagerState.currentPage) != m) pagerState.scrollToPage(monthToPage(baseMonth, m))
        }
    }
    // [CAL-03] Back opens the whole month again.
    androidx.activity.compose.BackHandler(enabled = weekOnly && !wide && paint == null) { weekOnly = false }
    // Back ends quick input instead of leaving the app.
    androidx.activity.compose.BackHandler(enabled = paint != null) { vm.setPaint(null) }
    // Left open past midnight, the selection moves on with today when it was on today.
    var lastToday by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (today != lastToday) {
            if (selected == lastToday) {
                vm.select(today)
                // [CAL-25] Past midnight on the last of the month the month on screen follows the day (I 리뷰 P2),
                // or the sheet's title and the calendar under it would be a month apart.
                val m = YearMonth.from(today)
                if (pageToMonth(baseMonth, pagerState.settledPage) == YearMonth.from(lastToday) && m != YearMonth.from(lastToday)) {
                    pagerState.scrollToPage(monthToPage(baseMonth, m))
                }
            }
            lastToday = today
        }
    }
    // [UX-03][CAL-17] Things pointed out once on this phone.
    val hints = remember { HintStore(context) }
    var longPressHintDone by remember { mutableStateOf(hints.done(HintStore.LONG_PRESS)) }
    val longPressSeen: () -> Unit = {
        if (!longPressHintDone) { hints.markDone(HintStore.LONG_PRESS); longPressHintDone = true }
    }
    // [DAY-01] A tap picks the day and shows it under the calendar; a tap on the picked day opens it all.
    val onDayClickNow: (LocalDate) -> Unit = { date ->
        val painting = paint != null
        val again = date == selected
        // [CAL-17] Changing one day in place is a long press away, which nothing on screen shows: the
        // first plain tap on a date says so once.
        // [J133-64] 1.33 (중간 빌드 확인): not in the first two days, when setup's own cards and questions are still about; a tip
        // over the 돈 line on the first tap of a new phone was one more thing in the way.
        val settled = context.appContainer.installedAt?.let { java.time.Duration.between(it, com.bamnat.core.time.Today.instant()).toDays() >= 2 } ?: true
        if (!painting && mark == null && rangeFrom == null && !longPressHintDone && settled) {
            longPressSeen()
            val tip = focus.longPressTip()
            scope.launch { delay(TIP_DELAY_MILLIS); vm.tip(tip) }
        }
        vm.onDayTapped(date)
        // [RUL-04] 날짜를 누르면 규칙 (메모 틀 열기·알림 …), never while painting days.
        if (!painting && mark == null && rangeFrom == null) com.bamnat.app.logic.LogicRunner.onTap(context.appContainer, date, dayFacts)
        val m = YearMonth.from(date)
        if (m != visibleMonth && !weekOnly) slideToMonth(m)
        // [CAL-20] With the day card on, the first tap shows it and the second opens the day.
        if (!wide && !painting) when {
            dayView == PhoneDayView.WEEK -> weekOnly = true
            settings.showDayCard -> if (again) sheetOpen = true
            // B 1.32 [CALX-03] (사용자 2026-10-28 "한 번 눌러 선택, 두 번째에 동작"): the first tap picks, the second opens.
            settings.tapSelectFirst -> if (again) sheetOpen = true
            else -> sheetOpen = true
        }
    }
    // [DAY-01] ‹ › and swipes in the day header move the calendar along.
    val onDateChange: (LocalDate) -> Unit = { date ->
        vm.select(date)
        val m = YearMonth.from(date)
        // [CAL-03] The month is not on screen in the week view; it is put right when it comes back.
        if (m != visibleMonth && !(weekOnly && !wide)) slideToMonth(m)
    }
    val groups = rememberDayGroupState()
    // [STAB-01] Counted across a font or theme change too, so a memo tap from before does not open the keyboard again.
    var memoFocus by rememberSaveable { mutableIntStateOf(0) }
    // In the week view the whole day is on the page already.
    val showsDay = wide || weekOnly
    val onMemo: () -> Unit = {
        memoFocus++
        if (!showsDay) sheetOpen = true
    }
    // [SCHED-03] The day "이 날부터 순서 다시 맞추기" was asked on; survives a fold.
    var realigning by rememberSaveable(stateSaver = DateSaver) { mutableStateOf<LocalDate?>(null) }
    realigning?.let { date ->
        RealignDialog(date, snap, onDismiss = { realigning = null }, onConfirm = { step, clear ->
            realigning = null
            vm.realignFrom(date, step, clear)
        }, onPause = { days -> realigning = null; vm.pausePattern(date, days) })
    }
    val onQuickActionNow: (LocalDate, QuickAction) -> Unit = { date, action ->
        // [CAL-17] Found the long press already: the tip is not needed.
        longPressSeen()
        val day = snap.resolver.resolve(date)
        when (action) {
            is QuickAction.Shift -> vm.assignQuick(day, action.override, snap)
            is QuickAction.Extra -> vm.toggleExtra(day, action.choice)
            is QuickAction.SetLeave -> vm.setLeave(date, action.leave, announce = true)
            is QuickAction.Alarm -> vm.setAlarmSkipped(date, action.skipped, announce = true)
            QuickAction.AddMoney -> {
                vm.select(date)
                quickMoney = true
            }
            QuickAction.Memo -> {
                vm.select(date)
                onMemo()
            }
            QuickAction.Open -> {
                vm.select(date)
                if (!showsDay) sheetOpen = true
            }
            QuickAction.Realign -> realigning = date
        }
    }
    // [CAL-14] The same two lambdas for the whole life of the screen: a tap that changes the selection
    // then redraws only the two cells it touches, not all 42 of every month in the pager.
    val dayClickLatest by rememberUpdatedState(onDayClickNow)
    val quickActionLatest by rememberUpdatedState(onQuickActionNow)
    val onDayClick: (LocalDate) -> Unit = remember { { d: LocalDate -> dayClickLatest(d) } }
    val onQuickAction: (LocalDate, QuickAction) -> Unit = remember { { d: LocalDate, a: QuickAction -> quickActionLatest(d, a) } }
    // [EXTRA-05] Records from before 1.9 that need a look, offered once above the calendar.
    val toReview by vm.extrasToReview.collectAsStateWithLifecycle()
    var reviewing by remember { mutableStateOf(false) }
    val swapCovers = remember(snap, settings.swapCoversAnswered) {
        if (settings.swapCoversAnswered) emptyList()
        else com.bamnat.core.schedule.ExtraReview.shiftSwapCovers(snap.shiftTypes, snap.patterns, snap.entries)
    }
    var explainingSwap by remember { mutableStateOf(false) }

    var showMonthPicker by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    // 1.38 [EASE-28] The month about to go out as a picture: the sheet shows it and what rides on it first.
    var imageMonth by remember { mutableStateOf<YearMonth?>(null) }
    // [PROV-04] Which of the calendar's links the sheet opens on: the first, or the one a notice is about.
    var shareSlot by remember { mutableIntStateOf(1) }
    val shareLinks = remember { com.bamnat.app.share.ShareLinkClient().enabled }
    // [UX-03] Pointed out once, the first time there is a schedule worth sending and nothing was sent yet.
    var shareHintDone by remember { mutableStateOf(hints.done(HintStore.SHARE)) }
    val shareCalendar: () -> Unit = {
        if (!shareHintDone) { hints.markDone(HintStore.SHARE); shareHintDone = true }
        shareSlot = 1
        // [MKT-09] With a link server the sheet offers picture or link; without one it sends the picture.
        if (shareLinks) showShare = true
        else imageMonth = visibleMonth
    }
    // [MCAL-03] Keyed by the calendar, so a sheet opened as the calendar changes starts from that calendar's link.
    if (showShare) androidx.compose.runtime.key(activeCalendar?.id) {
        ShareDialog(
            snapshot = snap, month = visibleMonth,
            calendar = activeCalendar,
            startSlot = shareSlot,
            onImage = { imageMonth = visibleMonth },
            onDismiss = { showShare = false },
            onOpenReceived = { showShare = false; adding = "" },
        )
    }
    // 1.40 [SPREAD-37] 연말 근무 결산 (12-20~1-10): the year whose sheet is open, from the card or the picture sheet.
    var yearRecapOpen by remember { mutableStateOf<Int?>(null) }
    val recapStyle = activeCalendar.style ?: settings.workStyle
    val recapHere = viewingReceived == null && !activeCalendar.isExample && focus != com.bamnat.core.work.CalendarFocus.INVEST
    yearRecapOpen?.let { y ->
        val recap by produceState<com.bamnat.core.work.YearRecap?>(null, y, snap, focus, moneyBook, payProfile) {
            value = runCatching { vm.yearRecap(y, today, startDay, snap, focus, recapStyle, payProfile, moneyBook) }.getOrNull()
        }
        YearRecapSheet(recap, onSent = { vm.closeYearRecap(today) }, onDismiss = { yearRecapOpen = null })
    }
    imageMonth?.let { m ->
        ImageShareSheet(
            snapshot = snap, month = m, weekStart = settings.weekStart, memos = datedMemos,
            startName = remember(activeCalendar.id) { context.appContainer.shareLinksOf(com.bamnat.app.share.ShareLinkKey.of(activeCalendar.id, 1)).link.value?.title.orEmpty() },
            onSent = { vm.logShare() },
            onDismiss = { imageMonth = null },
            yearRecap = com.bamnat.core.work.YearRecap.yearOf(today)?.takeIf { recapHere }?.let { y -> y to { imageMonth = null; yearRecapOpen = y } },
            style = settings.workStyle,
        )
    }
    // [SHR-02][MCAL-01] Whose calendar is on screen, once there is more than one.
    val calendarRows = remember(calendarOrder, myCalendars, receivedList, received.enabled, showShare, switching) {
        CalendarRow.of(calendarOrder, myCalendars, if (received.enabled) receivedList else emptyList()) { id ->
            if (shareLinks) com.bamnat.app.share.ShareLinkKey.all(id).firstNotNullOfOrNull { context.appContainer.shareLinksOf(it).link.value } else null
        }
    }
    val pickCalendar: (CalendarRow) -> Unit = { row ->
        vm.setPaint(null)
        when (row) {
            is CalendarRow.Mine -> { received.view(null); vm.viewCalendar(row.cal.id) }
            is CalendarRow.Got -> received.view(row.cal.id)
        }
    }
    val currentRow: CalendarRow = viewingReceived?.let { CalendarRow.Got(it) }
        ?: calendarRows.firstOrNull { it.key == com.bamnat.app.data.CalendarKey.Mine(activeCalendar.id) } ?: CalendarRow.Mine(activeCalendar, null)
    // 1.38 [EASE-30] 같이 보기 goes once this calendar has a link out; the faces take its place once one is taken in.
    var together by remember { mutableStateOf(false) }
    val togetherStyle = activeCalendar.style ?: settings.workStyle
    val togetherDone = remember(activeCalendar.id, showShare) { context.appContainer.hasShareLink(activeCalendar.id) }
    // 2.0.0 [RVP-45] Where 같이 보기 opens from: the head row on a wide screen, the calendar chip's list on a phone.
    val togetherAt: (() -> Unit)? = if (shareLinks && viewingReceived == null && !togetherDone && togetherStyle != com.bamnat.core.work.WorkStyle.INVEST) ({ together = true }) else null
    // [UX-03] Always there: whose calendar this is (a tap opens the switcher, where a coworker's calendar
    // is taken in too) and 공유, on every screen size.
    val identityBar: @Composable () -> Unit = {
        CalendarIdentityBar(
            rows = calendarRows,
            current = currentRow,
            home = activeCalendar,
            onOpenSwitcher = { switching = true },
            onPick = pickCalendar,
            onShare = shareCalendar,
            // 1.38 [EASE-30] Until a link went out or another calendar sits beside this one.
            // 1.38 PC 4a5c3828 (자영업·투자 달력에 '가족·연인과 근무 같이 보기'): 투자 has no days to see together; a shop's are its 영업일.
            onTogether = if (shareLinks && viewingReceived == null && !togetherDone && togetherStyle != com.bamnat.core.work.WorkStyle.INVEST) ({ together = true }) else null,
            togetherShop = togetherStyle == com.bamnat.core.work.WorkStyle.BUSINESS,
            togetherField = togetherStyle == com.bamnat.core.work.WorkStyle.FIELD,
        )
    }
    if (together) TogetherSheet(
        shop = togetherStyle == com.bamnat.core.work.WorkStyle.BUSINESS,
        field = togetherStyle == com.bamnat.core.work.WorkStyle.FIELD,
        onSend = { together = false; shareCalendar() },
        onTake = if (received.enabled) ({ together = false; adding = "" }) else null,
        onDismiss = { together = false },
    )
    // Back from someone's calendar returns to the user's own, like leaving another account.
    androidx.activity.compose.BackHandler(enabled = viewingReceived != null) { received.view(null) }

    val memoPart: @Composable () -> Unit = {
        if (settings.calendarMemos) {
            MonthMemoCard(
                month = visibleMonth,
                memos = remember(datedMemos, visibleMonth) { datedMemos.filter { YearMonth.from(it.date!!) == visibleMonth } },
                showEmpty = settings.memoInCells,
                shiftOn = { d -> snap.resolver.resolve(d).shiftType }, // [EASE-24]
                onOpen = onOpenMemo,
                onNew = { onNewMemo(if (YearMonth.from(selected) == visibleMonth) selected else visibleMonth.atDay(1)) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }

    // [CAL-15] No date is ever under the FAB, so a tap meant for a day never opens quick input. There is
    // room for the FAB under the month: while the month's last row (the 30th, the 31st) is lower than
    // where the FAB rests, the FAB rides in that room just under the row, coming up from the screen's
    // edge with it, and it rests in its usual place once the month is above. Read while placing:
    // scrolling moves it without recomposing; a jump (another view, another layout) is animated.
    val pageScroll = rememberScrollState()
    var gridBottom by remember { mutableFloatStateOf(Float.NaN) }
    // [DAILY-09] Where the month starts in the window, so the half above a fold's hinge can open on it.
    var gridTop by remember { mutableFloatStateOf(Float.NaN) }
    var fabAreaBottom by remember { mutableFloatStateOf(Float.NaN) }
    // How far down the month the FAB may not go: the rows with this month's days where the FAB rests (the right side).
    var ownRowsPx by remember { mutableFloatStateOf(Float.NaN) }
    // 1.39 [CALX-07] (사용자 2026-11-08 "달력만 보기인데 밑에 더 이상 내용이 없는데 스크롤이 돼"): how tall what stands under the month is
    // (안내·카드). With nothing there the page keeps only the FAB's room under the month, not the room for a last card as well, so a
    // month that fits does not scroll into empty space.
    var belowPx by remember { mutableIntStateOf(-1) }
    val docking = paint == null && !listView && !(weekOnly && !wide)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fabLift = with(density) { (FAB_MARGIN + FAB_HEIGHT).toPx() }
    val fabHeight = with(density) { FAB_HEIGHT.toPx() }
    val dockGap = with(density) { FAB_DOCK_GAP.toPx() }
    val fabJump = with(density) { 8.dp.toPx() }
    val fabTarget: () -> Float = {
        if (!docking || gridBottom.isNaN() || fabAreaBottom.isNaN()) 0f
        else {
            // 1.37 PC 736fb7a6 (첫 근무를 칠하면 위에 카드가 생겨 달력이 내려가고, '빠른 입력'이 화면 아래로 숨어 끝까지 내려야 보임):
            // it rides under the month only when the whole month shows at the page's top. A longer page keeps it in its usual
            // place all the way, so it never hides below the edge and does not jump while scrolling.
            val ride = gridBottom + dockGap - (fabAreaBottom - fabLift)
            // PC 8eae2521 (달력만 보기에서 달력 아래에 붙은 단추가 아래 탭을 반쯤 가림): it never goes lower than its own area's bottom.
            if (ride + pageScroll.value >= fabLift - 1f) 0f else ride.coerceIn(0f, fabLift - fabHeight)
        }
    }
    val fabDrop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(docking, fabLift) {
        androidx.compose.runtime.snapshotFlow { fabTarget() }.collectLatest { t ->
            if (kotlin.math.abs(t - fabDrop.value) > fabJump) fabDrop.animateTo(t, tween(FAB_STEP_MILLIS, easing = FastOutSlowInEasing))
            else fabDrop.snapTo(t)
        }
    }
    // [DAY-01] The FAB folds to its icon while the page goes on under it (the day card, the month's hours),
    // so what it would cover stays readable and in reach; at the page's end there is room for its label.
    // [CAL-15] It says "빠른 입력" at the top of the page and while it rides under the month, so the
    // first look tells what it is.
    val fabExpanded by remember { derivedStateOf { fabDrop.value > 0.5f || pageScroll.value == 0 || !pageScroll.canScrollForward } }
    // Below the screen's edge with the month, it is not there for TalkBack either.
    // 12.1 화면 사진(오늘의 흐름 한 줄이 달력 위로 오며 달력이 내려감, 큰 글씨에서 '빠른 입력'이 21~24일을 가림): where the
    // month is longer than the room above the FAB, it steps aside while it would sit on this month's own days, and comes back
    // once the page goes on past them. Next month's faded days in the last row may go under it.
    val fabOnDays by remember(fabLift) {
        derivedStateOf {
            if (!docking || ownRowsPx.isNaN() || gridTop.isNaN() || fabAreaBottom.isNaN()) false
            else {
                val top = fabAreaBottom - fabLift + fabDrop.value
                top < gridTop + ownRowsPx && top + fabHeight > gridTop
            }
        }
    }
    val fabGone by remember(fabLift) { derivedStateOf { fabDrop.value >= fabLift - 1f || fabOnDays } }
    // PC 8eae2521 (근무 0개: 안내 카드의 '빠른 입력'과 떠 있는 '빠른 입력' 두 개, 떠 있는 것이 날짜 칸을 가림): while the empty
    // calendar's start card is up, its own button is the one way in.
    var startCardShown by remember { mutableStateOf(false) }

    // [SLP-15] A tapped day notice opens on today: the user's own calendar, this month, the page's top.
    val noticeTarget by NoticeLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.TODAY) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.TODAY)
        received.view(null)
        // [MCAL-01] Day notices follow the main calendar; [GUIDE-01] a feature of today opens on its own calendar, with the day open.
        val feature = com.bamnat.app.ui.guide.FeatureLinks.takeToday()
        vm.viewCalendar(feature ?: com.bamnat.app.data.PersonalCalendar.MAIN)
        vm.setPaint(null)
        // 2.0.0 [RVI-68] (PC-49) 오늘 보기 opens on today's flow, not its folded line.
        if (feature == null) vm.openFlowNow()
        sheetOpen = feature != null && !showsDay
        vm.select(Today.date())
        pagerState.scrollToPage(monthToPage(baseMonth, Today.month()))
        pageScroll.scrollTo(0)
    }
    // [DAILY-06] A widget's date opens that day on 내 달력, as tapping it on the calendar does.
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.DAY) return@LaunchedEffect
        // 2.0.0 [RVP-26] Read before it is taken: taking the target empties its day and calendar.
        val day = NoticeLinks.payload.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Today.date()
        val noticeCalendar = NoticeLinks.calendar.value
        NoticeLinks.consume(NoticeLinks.Target.DAY)
        received.view(null)
        // [DAILY-07] 근무·급여's week bar names its calendar; a widget's date is always 내 달력.
        vm.viewCalendar(noticeCalendar ?: com.bamnat.app.data.PersonalCalendar.MAIN)
        vm.setPaint(null)
        vm.select(day)
        pagerState.scrollToPage(monthToPage(baseMonth, YearMonth.from(day)))
        sheetOpen = !showsDay
    }
    // [SHR-12] The link's expiry notice opens the share sheet on the user's own calendar, to 늘리기.
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.SHARE) return@LaunchedEffect
        // [MCAL-03] An added calendar's notice opens that calendar's sheet.
        // [PROV-04] A notice about a second link names it too, and the sheet opens on it. 2.0.0 [RVP-26] Read before it is taken.
        val key = NoticeLinks.calendar.value
            ?: (if (NoticeLinks.payload.value == NoticeLinks.ON_SCREEN) context.appContainer.calendars.active.value else null)
            ?: com.bamnat.app.data.PersonalCalendar.MAIN
        NoticeLinks.consume(NoticeLinks.Target.SHARE)
        received.view(null)
        vm.viewCalendar(com.bamnat.app.share.ShareLinkKey.calendar(key))
        shareSlot = com.bamnat.app.share.ShareLinkKey.slot(key)
        if (shareLinks) showShare = true
        else imageMonth = visibleMonth
    }
    // [UX-03] 동료 근무표 받기 from settings or a help answer: the sheet that takes a received link.
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.RECEIVE) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.RECEIVE)
        received.view(null)
        if (received.enabled) adding = ""
    }
    // [CAL-01] The picked day's week over the hour limit, said in one line under the calendar.
    val weekNote = remember(calculator, selected, limits, settings.stretchLimitHours, settings.workStyle, snap) {
        val week = calculator.week(selected).takeIf { it.overLimit }?.let {
            "이 주 ${HoursFormat.korean(it.totalMinutes)} · ${limits.weeklyLimitHours}시간 넘음"
        }
        // 1.32 [WTYP-12] 연속 근무 경고: a stretch that starts on the picked day and runs past the limit.
        val stretch = if (settings.workStyle == com.bamnat.core.work.WorkStyle.BUSINESS) null else com.bamnat.core.hours.Stretches.over(com.bamnat.core.hours.Stretches.of(selected, selected, snap.resolver::resolve), settings.stretchLimitHours)
            .firstOrNull()?.let { com.bamnat.core.hours.Stretches.line(it, settings.stretchLimitHours) }
        listOfNotNull(week, stretch).joinToString(" · ").ifEmpty { null }
    }
    // [CAL-01] An alarm due within a week, whether or not the master switch lets it ring.
    val alarmSoon = remember(snap, today) {
        AlarmPlanner.nextAlarm(snap.resolver, Today.dateTime(), lookaheadDays = ALARM_WARNING_DAYS) != null
    }

    // [CAL-03] The tapped date's week and the whole day under it: no sheet over the calendar, and the rest of the week a tap
    // away. A sideways swipe goes a week back or on.
    // 2.0.0 [RVH-03] 검토 CAL-04: the week starts on the day the month view starts it (카드 배치 › 한 주의 시작), so a Sunday tapped
    // in a Sunday-first month is the first day of its week here too, and the range says which week of the month it is.
    val weekPart: @Composable () -> Unit = {
        val monday = weekFirstDay(selected, settings.weekStart)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${weekOfMonthLabel(selected, settings.weekStart)} · ${Fmt.monthDay(monday)} ~ ${Fmt.monthDay(monday.plusDays(6))}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { weekOnly = false }) { Text("달 전체 보기") }
        }
        WeekdayHeader(settings.weekStart, Modifier.padding(start = 8.dp + WEEK_BAR_SPACE, end = 8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            // [CELL-01] 1.39 A week of cells listing their jobs and memos is as tall as the month's.
            val listOn = cellListOn(settings.cellList, focus == com.bamnat.core.work.CalendarFocus.JOBS, settings.memoInCells, calView, false, hasMemos = datedMemos.isNotEmpty())
            val tall = listOn && cellListTall(settings.cellList, focus == com.bamnat.core.work.CalendarFocus.JOBS, settings.memoInCells, calView, true)
            val cellHeight = (if (tall) maxOf(calView.cellHeight(maxWidth), listCellHeight(maxWidth)) else calView.cellHeight(maxWidth)) + cellTextGrowth()
            val swipe = with(androidx.compose.ui.platform.LocalDensity.current) { WEEK_SWIPE.toPx() }
            val turnTo by rememberUpdatedState(onDateChange)
            WeekStrip(
                monday = monday,
                snapshot = snap,
                selected = selected,
                today = today,
                memoDates = memoDates,
                showHours = settings.showHoursInCells,
                showLunar = settings.showLunar,
                cellHeight = cellHeight,
                onDayClick = onDayClick,
                onQuickAction = onQuickAction,
                money = moneyBook,
                showMoney = focus.moneyFirst,
                showSpend = settings.spendInCells,
                memoTexts = memoTexts,
                memoLists = memoLists.takeIf { listOn },
                listEmptyDays = tall,
                focus = focus,
                hourly = hourlyCells,
                invest = investCells,
                // [DAYW-01] A day calendar shows its exceptions only.
                quietWorkDays = settings.workStyle == com.bamnat.core.work.WorkStyle.DAY,
                modifier = Modifier.pointerInput(Unit) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            if (dragged > swipe) turnTo(selectedNow().minusWeeks(1))
                            else if (dragged < -swipe) turnTo(selectedNow().plusWeeks(1))
                        },
                    ) { change, amount ->
                        change.consume()
                        dragged += amount
                    }
                },
            )
        }
        // [CAL-34] I 리뷰 I6: the week's own total under the week (Monday to Sunday, the strip's and the law's week).
        if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS) {
            val glance = remember(calculator, monday) { calculator.glance(monday) }
            if (glance.minutes > 0) {
                Text(
                    "이 주 ${glance.text}",
                    style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                    color = if (glance.leftToLegal < 0) LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DayPanel(
            date = selected,
            today = today,
            snapshot = snap,
            vm = vm,
            groups = groups,
            onDateChange = onDateChange,
            onOpenMemo = onOpenMemo,
            onNewMemo = onNewMemo,
            onOpenShiftTypes = onOpenShiftTypes,
            onOpenPay = onOpenPay,
            onOpenExtraKinds = onOpenExtraKinds,
            memoFocus = memoFocus,
            onMemo = onMemo,
            bottomPadding = 12.dp,
            weekNote = weekNote,
            scrollGroups = false,
        )
    }

    val calendarPart: @Composable () -> Unit = {
        // [CAL-51] 상-4: under 700dp tall (360×640, 320×568) the month's summary is one line and 오늘의 흐름 waits in the
        // notices; under 480dp (a phone on its side) the bars above share one row and only the 알림 line shows.
        val screenHeight = LocalConfiguration.current.screenHeightDp
        // [VIS-04] At 150% text and more every card is twice as tall: the screen is as low as a small phone's.
        val low = screenHeight < LOW_SCREEN_DP || LocalDensity.current.fontScale >= 1.5f
        val sideways = screenHeight < SIDEWAYS_SCREEN_DP
        val notices = rememberNoticeStackState()
        val linkMade = remember(activeCalendar.id, showShare) {
            shareLinks && context.appContainer.hasShareLink(activeCalendar.id)
        }
        // 2.0.0 [RVH-42] 검토 PC-29·30: the work's 바로 단추 on the head, as buttons of its row (the phone's own calendar only; a
        // received calendar on screen has none, the buttons write the user's own day).
        val decorLayer = com.bamnat.app.ui.decor.LocalDecor.current
        val decorTap = com.bamnat.app.ui.decor.LocalDecorTap.current
        val headQuick = remember(decorLayer, settings.logic, decorTap, viewingReceived) {
            if (decorTap == null || viewingReceived != null) emptyList()
            else HeadQuick.of(decorLayer) { id -> runCatching { com.bamnat.app.logic.LogicRunner.logic(context.appContainer).button(id) }.getOrNull() }
        }
        val header: @Composable (Modifier) -> Unit = { headerPlace -> CalendarHeader(
            modifier = headerPlace,
            quick = headQuick,
            onQuick = { id -> decorTap?.invoke(id, null) },
            month = if (weekOnly && !wide) YearMonth.from(selected) else visibleMonth,
            // [CAL-03] With one week on screen, ‹ › go a week back or on.
            onPrev = { if (weekOnly) onDateChange(selected.minusWeeks(1)) else slideToMonth(headingMonth().minusMonths(1)) },
            onNext = { if (weekOnly) onDateChange(selected.plusWeeks(1)) else slideToMonth(headingMonth().plusMonths(1)) },
            onToday = {
                vm.select(Today.date())
                slideToMonth(Today.month())
            },
            // [285] Already on today: the button stays where it is, greyed, so the bar does not jump.
            atToday = selected == Today.date() && (if (weekOnly && !wide) YearMonth.from(selected) else visibleMonth) == Today.month(),
            onPickMonth = { showMonthPicker = true },
            byWeek = weekOnly,
            onShare = shareCalendar,
            onOpenPattern = onOpenPattern,
            onAddCalendar = onAddCalendar,
            onReceive = if (received.enabled) ({ adding = "" }) else null,
            // [CALS-01] The calendar on screen is changed from its own page: name, kind of work, settings.
            onEditCalendar = if (viewingReceived == null) ({ onEditCalendar(activeCalendar.id) }) else null,
            // [CNV-08] 1.37 카드 배치 is the studio's 달력 화면 now (사용자 2026-11-04 "스튜디오랑 겹치고").
            onCustomize = if (viewingReceived == null) ({ com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.STUDIO_CALENDAR) }) else null,
            calendarName = activeCalendar.name,
            calendarId = activeCalendar.id,
            onOpenTeams = openTeams,
            // [NURS-01] A month's duty typed one day after another, for rosters that come as a sheet.
            onTypeMonth = if (viewingReceived == null && snap.shiftTypes.isNotEmpty()) ({ fillMonth(visibleMonth) }) else null,
            style = settings.workStyle,
            // [NAV-03] Which tiles this kind of calendar has at all, so the others keep their places while one waits.
            toolFit = ToolFit(teams = settings.workStyle == com.bamnat.core.work.WorkStyle.SHIFT, receive = received.enabled,
                viewingReceived = viewingReceived != null, hasShiftTypes = snap.shiftTypes.isNotEmpty(), teamsReady = openTeams != null || viewingReceived != null,
                pushReady = snap.resolver.patternOn(today) is com.bamnat.core.model.WorkPattern.Rotation,
                leader = context.appContainer.flags.byKey.collectAsStateWithLifecycle().value[com.bamnat.core.flags.FlagCatalog.TEAM_LEADER]?.on == true),
            onPushToday = { realigning = today }, // [EASE-25]
            // 2.0.0 [RVP-45] 검토 U13-01: whose calendar, at the head row's start (the line of its own is gone).
            chip = HeadChip(
                rows = calendarRows, current = currentRow, onOpenSwitcher = { switching = true }, onPick = pickCalendar,
                onShare = shareCalendar, shareName = com.bamnat.core.work.shareTitle(togetherStyle),
                onTogether = togetherAt,
                togetherShop = togetherStyle == com.bamnat.core.work.WorkStyle.BUSINESS,
                togetherField = togetherStyle == com.bamnat.core.work.WorkStyle.FIELD,
            ),
            bell = notices,
        ) }
        // 2.0.0 [RVP-45] 검토 UIX-01·U13-01 (달력 위가 화면의 절반): the head is one row, with whose calendar at its start, on a phone on
        // its side too. [CAL-50] The 공유 hint is one of the notices now (its band points at 공유 by saying it).
        header(Modifier)
        // [SETUP-01] A first setup left for later comes first; it fills the calendar.
        val waiting = pendingSetup?.takeIf { it.later && !settings.setupDone }
        // 2.0.0 [RVP-45] 검토 UIX-01·PC-55 (투자 첫 화면 58%: 시작 카드가 달력 위): an empty calendar's first step is the band over the
        // month, in the one shape, its button saying the step; the bell's sheet has the card whole. The 빠른 입력 button waits while it is
        // the band, so the step is the one thing to do (R136-02).
        val startStyle = settings.workStyle
        val startWords = startStyle.calendarStart()
        val startAction: () -> Unit = {
            when {
                startStyle?.usesPattern != false -> onOpenPattern()
                startStyle == com.bamnat.core.work.WorkStyle.HOURLY -> vm.setPaint(PaintTool.Pick)
                startStyle == com.bamnat.core.work.WorkStyle.INVEST ->
                    investSheet = com.bamnat.app.ui.invest.InvestSheet.CashEdit(com.bamnat.core.invest.CashKind.DEPOSIT)
                else -> {
                    if (YearMonth.from(today) == visibleMonth) vm.select(today)
                    quickMoney = true
                }
            }
        }
        androidx.compose.runtime.SideEffect { startCardShown = notices.bandKey == START_NOTICE }
        // [CAL-50] 상-4: one notice at a time above the month; 2.0.0 [RVP-45] one band, the rest in the bell on the head's row.
        val linePlace = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        // [LAY-10] PC 확인 C9 1.36: a 꾸밈 사진 waiting for the operator is said here, in one line, not over the month.
        com.bamnat.app.ui.decor.DecorWaitingLine("bg.calendar", Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        val guideHere = waiting == null && paint == null && viewingReceived == null && visibleMonth == YearMonth.from(today)
        val askOn = partShown(CalendarPart.ASK)
        // [CAL-01] What keeps an alarm from working, the most serious one, only while one is due within a week.
        // 2.0.0 [RVP-45][RVP-13] The band says it in one line with its one button; red only where an alarm will not work as it should
        // (DSG-03 '빨강은 위험만'), orange where the user turned it off.
        val alarmLine: AlarmNote? = when {
            // [ALRM-09] Turned off for today from the ringing alarm: said first, with the way back.
            alarmsOffUntil != null -> AlarmNote("오늘 알람 끔",
                "오늘 알람 끔 · ${com.bamnat.app.alarm.AlarmsOff.untilText(com.bamnat.app.alarm.AlarmScheduler.millisToLocal(alarmsOffUntil!!))}까지",
                "다시 켜기", { com.bamnat.app.alarm.AlarmsOff.turnOn(context) }, com.bamnat.app.ui.theme.Tone.Caution)
            !alarmSoon -> null
            // [CAL-13] An added calendar kept from ringing: the master switch and permissions do not matter here.
            // [EASY-01] The example never rings, so it has nothing to turn on.
            !calendarAlarmsOn -> if (activeCalendar.isExample) null else AlarmNote("‘${activeCalendar.name}’ 알람이 꺼져 있어요",
                "‘${activeCalendar.name}’ 달력은 알람이 꺼져 있어요", "켜기", { vm.setCalendarAlarms(true) }, com.bamnat.app.ui.theme.Tone.Caution)
            // [SET-01] With the master switch off nothing rings: said where the user looks first.
            !settings.alarmsEnabled -> AlarmNote("알람이 모두 꺼져 있어요", "알람이 모두 꺼져 있어요", "켜기", { vm.setAlarmsEnabled(true) },
                com.bamnat.app.ui.theme.Tone.Caution)
            // [REL-11][SLP-15] Without notifications an alarm still sounds, but nothing on the lock
            // screen stops it. Asked here, where it is needed, instead of on the first start.
            // 1.38 PC 9차: the app's window that said why (화면이 안 뜨고 소리만) is gone, so the line says it.
            !notificationsAllowed -> AlarmNote(NOTICE_OFF_LINE, NOTICE_OFF_LINE, "허용", askNotifications, com.bamnat.app.ui.theme.Tone.Danger,
                picture = com.bamnat.app.ui.components.PermissionResult.LOCK_ALARM)
            // [REL-12] Android 14+ can block the full-screen alarm; then only a small notification shows.
            !fullScreenAllowed -> AlarmNote("잠금 화면에 알람 화면이 안 떠요", "잠금 화면에 알람 화면이 안 떠요", "허용", {
                if (android.os.Build.VERSION.SDK_INT >= 34) SystemSettings.open(context,
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        android.net.Uri.parse("package:${context.packageName}")))
            }, com.bamnat.app.ui.theme.Tone.Danger)
            // [ALRM-07] The 근무 알람 notice turned down to 조용히 or hidden: the ringing screen cannot show either.
            alarmChannelTrouble != null -> AlarmNote(alarmChannelTrouble!!, alarmChannelTrouble!!, "고치기", {
                SystemSettings.open(context, android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, Notifications.ALARM_CHANNEL_ID))
            }, com.bamnat.app.ui.theme.Tone.Danger)
            else -> null
        }
        // [CUST-01] The user's parts above the month (화면 꾸미기), by part: the name the 알림 line gives it, and the part.
        val aboveParts: Map<CalendarPart, Pair<String, @Composable () -> Unit>> = mapOf(
            CalendarPart.TODAY_FLOW to ("오늘의 흐름" to @Composable {
                // [SLP-14] 오늘의 흐름 while this month is on screen, for work that comes in shifts and hours.
                // 1.39 PC 12.5 '가게·현장 흐름 스위치': 현장·가게·투자 have it when they added it (카드 배치 › 더 보기), as the layout says.
                if (settings.sleepTips && settings.partFits(CalendarPart.TODAY_FLOW, fitAdded) && !snap.isEmpty && visibleMonth == YearMonth.from(today)) {
                    TodayFlowSection(vm, snap, settings, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }),
            CalendarPart.TODAY_WORK to ((if (focus == com.bamnat.core.work.CalendarFocus.SALES) "오늘 매출" else if (focus == com.bamnat.core.work.CalendarFocus.INVEST) "오늘 투자" else "오늘 일감") to @Composable {
                // [CAL-05] Today for field and shop work, where 오늘의 흐름 shows shifts.
                if (visibleMonth == YearMonth.from(today) && paint == null) when (focus) {
                    com.bamnat.core.work.CalendarFocus.JOBS -> if (moneyBook.jobs.isNotEmpty()) TodayJobsCard(
                        today = today, jobs = moneyBook.jobs,
                        onOpen = { d -> onDateChange(d); if (!showsDay) sheetOpen = true },
                        onAdd = { vm.select(today); quickMoney = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    com.bamnat.core.work.CalendarFocus.SALES -> if (moneyBook.sales.isNotEmpty()) TodaySalesCard(
                        today = today, sales = moneyBook.sales,
                        onWrite = { vm.select(today); quickMoney = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // [INV-01] Today's result, 총자산 and 수익률, and the next alarm.
                    com.bamnat.core.work.CalendarFocus.INVEST -> if (investVm != null) com.bamnat.app.ui.invest.TodayInvestCard(
                        vm = investVm, today = today, onTrade = { vm.select(today); quickMoney = true }, onOpen = onOpenStats,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    else -> Unit
                }
            }),
        )
        val holidayDays = remember(snap, visibleMonth, holidaysKept, settings.workStyle, activeCalendar.id) { vm.holidayWork(snap, visibleMonth) }
        // [GUIDE-20] 1.31 P2: the first week's 시작 단계, read again when the settings or the days change.
        val c31 = LocalContext.current.appContainer
        var startHidden by remember { mutableStateOf(false) }
        val startRows = remember(settings.alarmsEnabled, settings.workStyle, activeCalendar.id, snap.isEmpty, startHidden, today, pendingSetup) {
            // 1.36 PC C3: from the day after the first setup, so its first day asks one thing only (the pay sheet).
            // 03df48fb Y7: while '근무 설정을 마저 해 주세요' is up, it is the one card (its 이어서 하기 is the next step).
            val resuming = pendingSetup?.let { it.later && !settings.setupDone } == true
            if (startHidden || resuming || viewingReceived != null || c31.firstSetup.quietToday()) null else com.bamnat.app.ui.guide.StartStepsState.rows(c31, !snap.isEmpty, activeCalendar.style ?: settings.workStyle)
        }
        // [R136-02] 1.36 (리뷰 R-42: 투자 첫 화면 '입금 적기' 카드 밑에 '할 수 있는 것 1번 원금 입금·출금'): while the empty
        // calendar's first-step card is up, it is the one guide; '할 수 있는 것' comes once the first record is in.
        val startCardUp = paint == null && !settings.rosterMonthly && !(pendingSetup?.later == true && !settings.setupDone) && settings.workStyle.let { style ->
            when {
                style?.usesPattern != false -> snap.isEmpty
                style == com.bamnat.core.work.WorkStyle.HOURLY -> overview.isEmpty && !snap.hasPattern
                style == com.bamnat.core.work.WorkStyle.INVEST -> investState?.loaded == true && investState.full.isEmpty
                else -> overview.isEmpty && YearMonth.from(today) == visibleMonth
            }
        }
        // 1.36 U7·U19 (빠른 입력: 첫 칸을 칠하면 위에 카드가 생겨 달력이 밀리고, 다음 누름이 빈 곳에 가요). 745fd183 PC 확인
        // (높이를 붙잡으니 위가 휑하고 아래 카드가 잘림) and U20: while painting, nothing is above the month; the calendar moves up
        // once as painting starts and stays put, and the month's count is on the painting sheet's head instead.
        val holding = paint != null
        com.bamnat.app.ui.components.HoldHands(holding)
        // What goes away above the month as painting starts is scrolled off too, so the day under the finger stays put.
        val aboveHeights = remember { IntArray(2) }
        LaunchedEffect(holding) {
            if (holding) {
                val gone = aboveHeights.sum()
                if (gone > 0 && pageScroll.value > 0) pageScroll.scrollTo((pageScroll.value - gone).coerceAtLeast(0))
            }
        }
        // C 1.32 [STAB-04] Opened safely after stopping in a row: said first, with the way back.
        // 1.36 PC A21 (안전 모드 중 글자 크기를 1.3배로 바꾸면 띠가 사라짐): the line stands above the notices, never in them, so no
        // other card (a '멈춰 있던 적' card, a phone on its side with no card open) folds it into '알림 N개 더 보기'.
        // 1.39 [FLOW-03] The month just set up fills from today, with its one line over it (2.0.0 [RVP-45]: the band, 칸이 saying it).
        val fillMoment by SetupFill.now.collectAsStateWithLifecycle()
        val fillMine = fillMoment?.takeIf { it.calendar == activeCalendar.id && paint == null }
        // 2.0.0 [RVIB-17] (PC-129) The band's line is counted again from the calendar as it is now, as the card under it is.
        val fillLine = rememberFillLine(fillMine, snap)
        // Its work all taken away (되돌리기, 한꺼번에 지우기, a roster that emptied it), the moment is over, not left saying the old count.
        LaunchedEffect(fillMine, fillLine) { if (fillMine != null && fillLine == null) SetupFill.close() }
        if (!holding) Column(Modifier.onSizeChanged { aboveHeights[0] = it.height }) {
        if (safeStart) {
            // PC 19·20차 사용성 제안 (감겨서 달력이 밀림): a line short enough not to wrap; 알람 점검 says the rest.
            AlarmWarningLine((if (com.bamnat.app.diag.StartGuard.byHand) "점검용 안전 모드" else "멈춰서 안전 모드") + " · 꾸밈·규칙 쉼",
                "평소대로", { context.appContainer.wakeAfterSafe() }, linePlace, tone = com.bamnat.app.ui.theme.Tone.Caution)
        }
        // 1.39 [CALX-07] 8차 PC (식당: 시작 카드·급여 대장·알림 1개 더 보기·보기 고르기 네 칸이 쌓여 달력이 맨 아래로): the one-time
        // '달력만 볼까요' is a notice, the open one, so the others fold under it; it waits while the start card is up and while the
        // first setup's month fills.
        val fillingNow = SetupFill.now.collectAsStateWithLifecycle().value?.calendar == activeCalendar.id
        // 2.0.0 [RVP-45] 검토 PC-81: the look the studio just put on, and the look of the day's shift, are notices of the calendar now
        // (they were lines over every tab that came 1~2 s late and pushed the month down).
        val lookUndo = rememberLookUndo()
        // 총괄 11-12 (HQ-49 '달력 위는 꼭 봐야 하는 것들로만'): the empty calendar's first step and the moment the first setup filled it are
        // the band once; seen, the step is a row of the 알림함 and the moment goes.
        val startBandSeen = remember(activeCalendar.id) { hints.done(HintStore.START_BAND) }
        LaunchedEffect(notices.bandKey) {
            if (notices.bandKey == START_NOTICE || notices.bandKey == "setupResume") hints.markDone(HintStore.START_BAND)
        }
        if (fillMine != null) androidx.compose.runtime.DisposableEffect(fillMine) { onDispose { SetupFill.close() } }
        val shiftLook = rememberShiftLookNote()
        // 2.0.0 [RVG-27] 검토 SN-13: this calendar's link went out without a part the server would not take (an emoji 근무 그림);
        // said once over the month, so the sender knows without opening the share sheet (before, it said nothing and never went).
        val shareLeftOut = com.bamnat.app.share.ShareLinkKey.all(activeCalendar.id).map { k -> k to c31.shareLinksOf(k).dropped.collectAsStateWithLifecycle().value }
        NoticeStack(state = notices, open = if (sideways) 0 else 1, notices = buildList {
            if (fillLine != null) fillMine?.let { m ->
                add(Notice("setupFill", "채워진 달력", line = fillLine.head,
                    button = false, onClose = SetupFill::close, kani = com.bamnat.app.ui.components.KaniMood.Done, urgent = true) {
                    SetupFillCard(m, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), snap = snap)
                })
            }
            // [SETUP-01] A first setup left for later comes first; it fills the calendar.
            if (waiting != null) add(Notice("setupResume", "근무 설정 이어서 하기", line = "근무 설정을 마저 해 주세요", action = "이어서 하기",
                onAction = vm::resumeSetup, kani = com.bamnat.app.ui.components.KaniMood.Point, urgent = !startBandSeen) {
                SetupResumeCard(waiting, onResume = vm::resumeSetup)
            })
            // [WORK-01][CAL-05] Only work that repeats needs a pattern; field, shop and hourly work start from a first record, each in its
            // own words.
            else if (startCardUp) add(Notice(START_NOTICE, "처음 할 일", line = startWords.title, action = startWords.action, onAction = startAction,
                kani = com.bamnat.app.ui.components.KaniMood.Point, urgent = !startBandSeen) {
                CalendarStartCard(start = startWords, onAction = startAction, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            })
            if (!settings.viewAsked && paint == null && viewingReceived == null && !startCardUp && !fillingNow) add(Notice("viewAsk", "보기 고르기", line = calendarViewAskWords(settings.calendarOnly).title,
                onClose = {
                    vm.updateSettings { it.copy(viewAsked = true) }
                    com.bamnat.app.ui.components.AppSnack.show("나중에 달력 › 도구나 설정 › 달력에서 바꿀 수 있어요")
                }) {
                CalendarViewAsk(settings.calendarOnly, onAnswer = { only -> vm.updateSettings { it.copy(calendarOnly = only, viewAsked = true) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            })
            // [MAT-06] 1.36 PC 확인 7차: shared fonts a theme was waiting for, come down by themselves (was a toast). PC 8차: first, so it
            // is the one open (it was folded as '글꼴' under the others); it goes once closed or taken back.
            if (FontArrivals.fonts.isNotEmpty()) add(Notice("fontArrived", "기다리던 글꼴", line = "기다리던 글꼴이 도착했어요") { FontArrivedLine(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) })
            // 2.0.0 [RVL-18] (검토 U07b-11) A month just put in (한 달 근무 넣기, 근태·캘린더 가져오기): 되돌리기 over the calendar for 10분,
            // as the user comes back from that screen. Draws nothing (and is not counted) otherwise.
            // [RVP-43] HQ-49: it can take back a whole month, so it may be the band (its line and 되돌리기; the '10분' line is in the card
            // opened whole in the 알림함). What the undo did is a row of the 알림함's 안내, said once.
            if (paint == null && viewingReceived == null) {
                rememberRecentFill(activeCalendar.id)?.let { e ->
                    add(Notice("recentFill", "방금 넣은 것", line = e.text, action = "되돌리기", onAction = { com.bamnat.app.ui.RecentFill.undo(e) },
                        onClose = { com.bamnat.app.ui.RecentFill.close(e) }, urgent = true) { RecentFillNotice(e) })
                }
                add(Notice("recentFillSaid", "되돌림", line = com.bamnat.app.ui.RecentFill.said.collectAsStateWithLifecycle().value ?: "방금 넣은 것을 되돌렸어요",
                    onClose = com.bamnat.app.ui.RecentFill::heard) { RecentFillSaid() })
            }
            // PC 재검토 N3 1.36: for a day after the studio put a look on, the look from before is one tap away.
            lookUndo?.let { u ->
                add(Notice("lookUndo", "입힌 모양", line = u.line, action = "입히기 전으로", onAction = u.undo, onClose = u.close, urgent = u.band) { LookUndoLine(u) })
            }
            // 1.39 [TEAM-22] D9: a 리더's change of the team's roster, put into the user's days (근무 알람 follows) or asked; first after
            // the fonts, so it is open and never folded, with 되돌리기 in reach.
            receivedList.filter { it.note != null }.forEach { cal ->
                if (paint == null && viewingReceived == null) add(Notice("rosterFollow:${cal.id}", "받은 근무표 바뀜", line = "‘${cal.fullName}’ 근무표가 바뀌었어요", urgent = true) {
                    RosterFollowLine(cal, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                })
            }
            // 2.0.0 [RVL-02][RVL-06][RVL-09] (검토 DATA-08·U12-03·DATA-11·U12-08): a restore put back whole, a phone the data came to by
            // Android's own backup, 앱 잠금 resting for want of the phone's lock. What the user trusts the app with comes right after the
            // 리더's change, so it is the open one; each draws nothing (and is not counted) when it has nothing to say.
            // [RVP-43] HQ-49: they keep what the user trusts the app with (기록, 알람이 다시 걸렸는지, 잠금), so each may be the band; its
            // '보기' opens the card whole in the 알림함.
            if (paint == null && viewingReceived == null) {
                add(Notice("restoreStopped", "불러오기 멈춤", line = "불러오기가 멈춰 전 기록으로 돌려놓았어요", tone = com.bamnat.app.ui.theme.Tone.Caution,
                    urgent = true) { com.bamnat.app.ui.settings.RestoreStoppedNotice() })
                add(Notice("phoneMoved", "옮겨 온 것 확인", line = "새 휴대폰으로 옮겨 왔어요", urgent = true) { com.bamnat.app.ui.settings.PhoneMovedNotice() })
                add(Notice("appLockResting", "앱 잠금 쉼", line = "휴대폰 잠금이 없어 앱 잠금이 쉬고 있어요", tone = com.bamnat.app.ui.theme.Tone.Caution,
                    urgent = true) { com.bamnat.app.ui.settings.AppLockRestingNotice(settings.appLock) })
            }
            shareLeftOut.forEach { (k, words) ->
                // [RVP-43] HQ-49: a part left out is something to know, not to do now, so a row of the 알림함's 안내 (총괄 11-12).
                // [RVG-27] S7-3: but a change turned down twice running has not reached the link at all, and the people reading it see
                // old days: that is to be seen, so it is the band, with 공유 to send it again.
                val refused = words == com.bamnat.app.share.ShareLinkPayload.REFUSED_AGAIN
                if (words != null && paint == null && viewingReceived == null) add(Notice("shareLeftOut:$k", "근무표 링크 안내", line = words,
                    tone = com.bamnat.app.ui.theme.Tone.Caution, button = refused, action = if (refused) "공유" else null,
                    onAction = if (refused) shareCalendar else null, urgent = refused, onClose = { c31.shareLinksOf(k).setDropped(null) }) {
                    com.bamnat.app.ui.components.NudgeCard(words, tone = com.bamnat.app.ui.theme.Tone.Caution,
                        onClose = { c31.shareLinksOf(k).setDropped(null) }, closeLabel = "링크 안내 닫기",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                })
            }
            // 빌드 e382585e (돈 줄이 '알림 3개 더 보기' 안에 접혀요): first, so it is the one open.
            // 1.36 실사용 S16 (현장: 입금 약속일이 지난 받을 돈이 달력 어디에도 안 보여요): the money promised and not paid, on the first
            // screen too, for a phone whose notices are off.
            val lateOwed = remember(moneyBook, today) { moneyBook.owed(today).filter { it.status(today) == com.bamnat.core.money.ReceivableStatus.OVERDUE } }
            if (paint == null && viewingReceived == null && lateOwed.isNotEmpty()) add(Notice("owedLate", "약속일 지난 받을 돈", line = "약속일 지난 받을 돈 ${lateOwed.size}건 · ${Fmt.won(lateOwed.sumOf { it.left })}",
                tone = com.bamnat.app.ui.theme.Tone.Caution, action = "보기",
                onAction = { com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.OWED)) }) {
                AlarmWarningLine("약속일 지난 받을 돈 ${lateOwed.size}건 · ${Fmt.won(lateOwed.sumOf { it.left })}", "보기", {
                    com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.OWED))
                }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), tone = com.bamnat.app.ui.theme.Tone.Caution)
            })
            // [PRIV-03] 결정 D84: the 3.1 ask comes first while this phone still owes it (testers only).
            if (paint == null && viewingReceived == null && LocalContext.current.appContainer.policyAsks())
                add(Notice("policy31", "처리방침 3.1", line = "처리방침·이용약관이 바뀌어요", urgent = true) { com.bamnat.app.ui.pro.PolicyConsentNotice() })
            // [FLAG-16] 1.28 AL: once 처리방침 3.2 is in force, one ask for 사용 통계's two extra items.
            if (paint == null && viewingReceived == null && com.bamnat.app.ui.flags.statsMoreAsks(LocalContext.current.appContainer))
                add(Notice("statsMore", "사용 통계", line = "사용 통계로 보내는 내용이 늘어요. 괜찮으신가요?", urgent = true) { com.bamnat.app.ui.flags.StatsMoreNotice() })
            // 2.0.0 [RVP-43] 검토 U13b-02·U13b-08 (업데이트한 날 창을 2~4번 닫아야 달력, '새 버전을 받았어요'가 적는 중에도 위에 뜸): one
            // opening window a visit; the others wait in the 알림함 as a row each, to open now with a tap (or 다시 시작 for a downloaded
            // update), else they come on a later visit.
            if (paint == null) com.bamnat.app.ui.components.AskTurn.waiting.forEach { ask ->
                val (label, line, act) = waitingWords(ask)
                val go: () -> Unit = if (ask == com.bamnat.core.guide.OpenAsk.UPDATE_READY) ({ com.bamnat.app.review.InAppUpdate.restart() })
                    else ({ com.bamnat.app.ui.components.AskTurn.request(ask) })
                add(Notice("openAsk:${ask.name}", label, line = line, action = act, onAction = go, kind = "calendar.openAsk") {
                    com.bamnat.app.ui.components.NudgeCard(line, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), action = act, onAction = go)
                })
            }
            // [INVITE-03] AE 1.26: an invite code from Play's install referrer, told to the server only on 알리기.
            // AG 1.27 [INVITE-10]: it was first, never folded in "알림 n개 더 보기" (리뷰 2026-10-25), until 2.0.0; it shows only
            // for a phone that did not answer at the end of the first setup, and after the report the friend's own invite.
            // 2.0.0 [RVP-43] HQ-49: a row of the 알림함's 안내; the row says where it stands, so one already told is not asked again there.
            val arrival = context.appContainer.invites.store.state.collectAsStateWithLifecycle().value
            val arrivalAsks = arrival.found != null && !arrival.offerClosed && arrival.reportedCode == null && !arrival.agreed
            if (paint == null && viewingReceived == null) add(Notice("inviteArrival", "친구 초대", line = if (arrivalAsks) "친구 초대로 오셨나요?" else "초대한 분께 알렸어요") { com.bamnat.app.ui.pro.InviteArrivalNotice() })
            // AG 1.27 [INVITE-12] After the app stopped by itself: one ask to say what happened, once a note.
            if (paint == null && viewingReceived == null) add(Notice("crashAsk", "불편한 점 알려 주기", line = "앱이 멈추거나 닫혔던 적이 있어요. 알려 주실래요?") { com.bamnat.app.ui.grow.CrashAskNotice() })
            // [GUIDE-20] 1.31 P2: in the first week, the 시작 단계 card in place of the question card.
            if (paint == null && startRows != null) add(Notice("start", "시작 단계", line = "처음 할 일을 하나씩 해 볼까요?", kani = com.bamnat.app.ui.components.KaniMood.Point) {
                com.bamnat.app.ui.guide.StartStepsCard(startRows, onDismiss = { com.bamnat.app.ui.guide.StartStepsState.dismiss(c31); startHidden = true },
                    onShifts = { vm.setPaint(PaintTool.Pick) })
            })
            // 1.39 G2 통합 5차 PC (식당 사장: 아래 칸에 '관리'가 없어 급여 대장까지 멀어요): a shop's calendar says once where it is.
            val payrollOn = context.appContainer.flags.byKey.collectAsStateWithLifecycle().value[com.bamnat.core.flags.FlagCatalog.BOSS_PAYROLL]?.on == true
            val barNow = remember(settings.tabSlots, settings.tabOrder) {
                com.bamnat.core.layout.TabSlots.resolve(settings.tabSlots, settings.tabOrder, com.bamnat.app.ui.TabPicks.known)
            }
            val ledgerHintDone = remember { com.bamnat.app.ui.calendar.HintStore(context).done(com.bamnat.app.ui.work.BossLedgerLine.KEY) }
            if (paint == null && viewingReceived == null && !ledgerHintDone && com.bamnat.app.ui.work.BossLedgerLine.wanted(settings.workStyle, payrollOn, barNow))
                add(Notice("bossLedger", "급여 대장", line = "직원이 있으면 급여 대장을 써 보세요", section = com.bamnat.app.inbox.InboxSection.SUGGEST) {
                    com.bamnat.app.ui.work.BossLedgerNotice(settings, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                })
            // [GUIDE-02][GUIDE-03][EASY-05] The question card first; on a day it was shown the one-line tip waits (질문 우선).
            // [EASE-03] 1.38: the question card waits for the day after the first setup too (its 시작 단계 waits already).
            if (guideHere && askOn && startRows == null && !c31.firstSetup.quietToday()) add(Notice("ask", "질문 1개", line = "오늘 물어볼 것이 하나 있어요", kani = com.bamnat.app.ui.components.KaniMood.Point) {
                com.bamnat.app.ui.guide.AskArea(context.appContainer, activeCalendar.id, settings.workStyle,
                    com.bamnat.core.profile.AskPlace.Home, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), today = today, compact = true,
                    moments = remember(snap, moneyBook, payProfile, settings.workStyle, today) {
                        com.bamnat.core.guide.GuideMoments.of(com.bamnat.app.ui.guide.factsOf(settings.workStyle, Today.dateTime(), snap, moneyBook, payProfile, settings.weekStart))
                    })
            })
            // 1.36 U7: the first day painted brings the next alarm into being; its line waits until quick input closes.
            if (paint == null) alarmLine?.let { a ->
                add(Notice("alarm", a.label, line = a.text, tone = a.tone, action = a.action, onAction = a.onAction, urgent = true) {
                    AlarmWarningLine(a.text, a.action, a.onAction, linePlace, picture = a.picture, tone = a.tone)
                })
            }
            // 빌드 e382585e (빠른 입력에서 첫 칸을 칠하면 위에 카드가 생겨 달력이 밀려 다음 누름이 다른 칸에): nothing new comes up above the
            // month while painting; the review waits until quick input closes.
            if (paint != null) Unit
            else if (swapCovers.isNotEmpty()) add(Notice("review", "대근 ${swapCovers.size}일 고치기", line = "쉬는 날 대근 ${swapCovers.size}일을 고쳐 주세요",
                tone = com.bamnat.app.ui.theme.Tone.Caution, action = "자세히", onAction = { explainingSwap = true }) {
                ExtraReviewCard(
                    title = "쉬는 날 대근 ${swapCovers.size}일을 고쳐 주세요",
                    message = "근무를 바꿔서 적은 대근은 쉬는 날 근무로 계산되지 않아요. 한 번에 대근으로 고칠 수 있어요.",
                    action = "자세히",
                    onAction = { explainingSwap = true },
                    onLater = vm::keepSwapCovers,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }) else if (toReview > 0) add(Notice("review", "추가 근무 ${toReview}건 확인", line = "옮겨 온 추가 근무 ${toReview}건을 확인해 주세요",
                action = "확인하기", onAction = { reviewing = true }) {
                ExtraReviewCard(
                    title = "옮겨 온 추가 근무 ${toReview}건을 확인해 주세요",
                    message = "이제 잔업·특근·대근마다 시각이 있어요. 예전 기록의 시간이 맞는지 한 번만 봐 주세요.",
                    action = "확인하기",
                    onAction = { reviewing = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            })
            // [CAL-07] Weekday work set by the week, on this month's public holidays: asked once.
            if (holidayDays.isNotEmpty() && paint == null) add(Notice("holiday", "공휴일 근무 ${holidayDays.size}일", line = "${visibleMonth.monthValue}월 공휴일에 근무가 ${holidayDays.size}일 있어요") {
                HolidayWorkCard(
                    month = visibleMonth, dates = holidayDays,
                    onRest = { vm.restHolidays(holidayDays) },
                    onKeep = { vm.keepHolidayWork(visibleMonth) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onAlways = vm::restHolidaysAlways,
                )
            })
            // [SETUP-02] A roster that comes every month: this month's or next month's, until a day of it is painted.
            if (settings.rosterMonthly && paint == null && waiting == null) {
                val thisMonth = YearMonth.from(today)
                val unpainted = (visibleMonth == thisMonth || visibleMonth == thisMonth.plusMonths(1)) &&
                    snap.entries.none { YearMonth.from(it.date) == visibleMonth && it.override != ShiftOverride.None }
                if (unpainted) add(Notice("roster", "${visibleMonth.monthValue}월 근무표 넣기", line = "${visibleMonth.monthValue}월 근무표를 넣어 주세요",
                    action = "넣기", onAction = { vm.setPaint(PaintTool.Pick) }) { RosterCard(visibleMonth, onPaint = { vm.setPaint(PaintTool.Pick) }) })
            }
            // [ALRM-03] 스마트 알람 제안: one suggestion at a time, changed only on a tap.
            if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS && paint == null && viewingReceived == null) add(Notice("alarmSuggest", "알람 제안", line = "알람 시각을 이렇게 바꿔 볼까요?", section = com.bamnat.app.inbox.InboxSection.SUGGEST) {
                val suggestion by vm.alarmSuggestion.collectAsStateWithLifecycle()
                suggestion?.let { s ->
                    AlarmSuggestCard(
                        s,
                        onApply = { vm.applyAlarmSuggestion(s) },
                        onNo = { vm.declineAlarmSuggestion(s, forGood = false) },
                        onNever = { vm.declineAlarmSuggestion(s, forGood = true) },
                    )
                }
            })
            // 1.40 [SPREAD-37] 연말 근무 결산, 12-20~1-10: once in 12월 (the year so far), once more in 1월 (the whole year).
            if (paint == null && recapHere) add(Notice("yearRecap", "연말 결산", line = "올해 근무 결산이 나왔어요", kani = com.bamnat.app.ui.components.KaniMood.Done) {
                val due = remember(today, settings.yearRecapSeen) { com.bamnat.core.work.YearRecap.due(today, settings.yearRecapSeen) }
                due?.let { y ->
                    val recap by produceState<com.bamnat.core.work.YearRecap?>(null, y, snap, focus, moneyBook, payProfile) {
                        value = runCatching { vm.yearRecap(y, today, startDay, snap, focus, recapStyle, payProfile, moneyBook) }.getOrNull()
                    }
                    recap?.takeIf { it.worthShowing }?.let { r ->
                        YearRecapCard(r, onOpen = { yearRecapOpen = y }, onClose = { vm.closeYearRecap(today) })
                    }
                }
            })
            // [CAL-46] I 리뷰 N9: once the month's last shift is done, the month in one line and its picture to share.
            if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS && paint == null && viewingReceived == null) add(Notice("recap", "지난달 정리", line = "지난달 근무를 한 줄로 정리했어요", kani = com.bamnat.app.ui.components.KaniMood.Done) {
                val recapMonth = remember(snap, today, settings.monthRecapSeen) {
                    com.bamnat.core.work.MonthRecap.due(today, settings.monthRecapSeen) { m ->
                        snap.resolver.resolveRange(m.atDay(1), m.atEndOfMonth()).lastOrNull { it.totalMinutes > 0 }?.date
                    }
                }
                recapMonth?.let { m ->
                    val recap = remember(snap, m) { com.bamnat.core.work.MonthRecap.of(m, snap.resolver.resolveRange(m.atDay(1), m.atEndOfMonth())) }
                    MonthRecapCard(
                        recap,
                        onShare = { imageMonth = m },
                        onClose = { vm.closeMonthRecap(m) },
                    )
                }
            })
            // [PLAZA-04] Right after a setup made by hand: put it on the plaza for coworkers (14·30·90일 간격, 그만 볼게요).
            // 2.0.0 [RVP-43] 검토 HQ-50: only a 교대·일근 calendar set up by hand (not from a company's) is asked; 알바·현장·가게·투자 never.
            if (paint == null && viewingReceived == null && (settings.workStyle == null || settings.workStyle == com.bamnat.core.work.WorkStyle.SHIFT ||
                    settings.workStyle == com.bamnat.core.work.WorkStyle.DAY))
                add(Notice("plazaShare", "회사 규칙으로 올리기", line = "직접 만든 설정을 같은 회사 동료와 나눠 볼까요?", section = com.bamnat.app.inbox.InboxSection.SUGGEST,
                    kind = "suggest.companyRule", action = "올리기",
                    onAction = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.COMPANY_REPORT) }) {
                com.bamnat.app.ui.company.ShareInviteNotice(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            })
            // [SPAT-04][SPAT-05] After a public cycle went in: the pay, then the plaza (14·30·90일 간격, 그만 볼게요).
            // [CALI-04] 1.30 BB: the calendar on screen's own (1.29: 내 달력's only); never the example's.
            val companyCalendar = activeCalendar.id
            if (paint == null && viewingReceived == null && !activeCalendar.isExample) add(Notice("publicCycle", "근무주기 다음 단계", line = "근무주기를 넣었어요. 급여도 넣어 볼까요?", section = com.bamnat.app.inbox.InboxSection.SUGGEST) {
                androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides companyCalendar) {
                    com.bamnat.app.ui.company.PublicWayNotice(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        onPay = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PAY) },
                        onShare = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.COMPANY_REPORT) })
                }
            })
            // [PLAZA-32] 1.27 AI (443): a joined company's 정보 광장, one line from the calendar.
            if (paint == null && viewingReceived == null && !activeCalendar.isExample) add(Notice("plazaRoom", "회사 정보 광장", line = "우리 회사 정보 광장이 있어요", action = "가기",
                onAction = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PLAZA) }) {
                androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides companyCalendar) {
                    com.bamnat.app.ui.company.PlazaRoomLine(Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        onOpen = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PLAZA) })
                }
            })
            // AG 1.27 [INVITE-11] Inviting friends, three days after the install, two weeks apart (결정 AG-D6).
            // 2.0.0 [RVP-43] HQ-50·HQ-48 (갈래 O): the 알림함's 권유 says the invite card's own name and promise, and goes to 프로, where that
            // card stands first on a phone without 프로.
            val inviteRules = context.appContainer.invites.store.state.collectAsStateWithLifecycle().value.rules
            if (paint == null && viewingReceived == null) add(Notice("inviteNudge", "친구를 초대하면 무료로 써요", line = inviteRules.words(),
                section = com.bamnat.app.inbox.InboxSection.SUGGEST, kind = "invite.friend", action = "보기",
                // Seen once it is tapped; its X rests it two weeks, as the card's 닫기 did (a 권유 is not back the next day).
                onAction = {
                    context.appContainer.grow.inviteShown(Today.date())
                    com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.PRO))
                },
                onClose = { context.appContainer.grow.inviteClosed(Today.date()) }) { com.bamnat.app.ui.pro.InviteNudgeNotice() })
            // 1.32 [PROX-02] A money day's one 하루 무료 권유 (급여일 사흘, 근무표 넣은 날, 신고 달).
            if (paint == null && viewingReceived == null && !activeCalendar.isExample) add(Notice("proMoment", "프로 제안", line = "프로 기능을 써 볼 수 있는 날이에요", section = com.bamnat.app.inbox.InboxSection.SUGGEST) {
                val payday = payProfile?.period?.let { p ->
                    p.payWeekday == today.dayOfWeek || listOf(YearMonth.from(today), YearMonth.from(today).minusMonths(1)).mapNotNull(p::payDate)
                        .any { !it.isAfter(today) && !it.isBefore(today.minusDays(2)) }
                } == true
                com.bamnat.app.ui.pro.ProWelcomeCard()
                com.bamnat.app.ui.pro.ProMomentNotice(settings.workStyle, payday)
            })
            // 1.32 [STAB-08] 백업한 지 30일, without 매일 자동 백업.
            if (paint == null && viewingReceived == null && !activeCalendar.isExample) add(Notice("backupNudge", "백업 알림", line = "백업한 지 오래됐어요. 지금 백업해 둘까요?", tone = com.bamnat.app.ui.theme.Tone.Caution) {
                com.bamnat.app.ui.settings.BackupNudgeNotice(hasRecords = settings.setupDone)
            })
            // [STU-37] PC 확인 S6: while a 근무에 따라 look is on, which one, until the mode changes.
            shiftLook?.let { l -> add(Notice("shiftLook", "근무에 따른 모양", line = l.line, button = false, onClose = l.close) { ShiftLookLine(l) }) }
            // [UX-03][CAL-50] 공유 pointed out once, the first time there is a schedule worth sending and nothing was sent yet; not on the
            // first setup's day (EASE-03).
            if (!shareHintDone && !linkMade && !snap.isEmpty && paint == null && viewingReceived == null && waiting == null) {
                val word = com.bamnat.core.work.scheduleWord(settings.workStyle)
                val shareLine = "‘공유’로 $word${Fmt.obj(word)} 보낼 수 있어요"
                val shareDone = { hints.markDone(HintStore.SHARE); shareHintDone = true }
                add(Notice("shareHint", "공유 안내", line = shareLine, action = "보내기", onAction = shareCalendar, onClose = shareDone, section = com.bamnat.app.inbox.InboxSection.SUGGEST) {
                    com.bamnat.app.ui.components.NudgeCard(shareLine, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        icon = Icons.Filled.Share, detail = "동료·가족에게 이미지나 링크로 보내요. 링크는 앱이 없어도 열려요.",
                        action = "보내기", onAction = shareCalendar, onClose = shareDone, closeLabel = "공유 안내 닫기")
                })
            }
            // [SRV-04] The developer's notice, one line, only while it runs.
            add(Notice("devNotice", "공지", line = "새 공지가 있어요") { AppNoticeLine(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) })
            // [J133-68] 1.33 (중간 빌드 확인: 첫 화면이 시작 단계와 '할 수 있는 것' 카드로 다 덮였어요): while the 시작 단계 card is up it is
            // the one guide on the calendar; '할 수 있는 것' and the day's tip come after it.
            // [EASE-01] 1.38: '할 수 있는 것'·오늘의 팁 is under the month now (guidePart), so the month opens at the top.
            if (guideHere && partShown(CalendarPart.JOURNAL)) add(Notice("journal", "밤낮이 한 일", line = "밤낮이 오늘 한 일이 있어요", kani = com.bamnat.app.ui.components.KaniMood.Basic) {
                com.bamnat.app.ui.guide.JournalHomeLine(Modifier.padding(horizontal = 16.dp), today = today)
            })
            // 1.40 [DISC-01] 안 써 본 기능 가끔 알려 주기: always the last card, and only on a day nothing else above the month speaks
            // (the user's own parts and 밤낮이 한 일 do not count as speaking). At most one in 7 days, rarer when not answered.
            if (guideHere && paint == null && viewingReceived == null && !activeCalendar.isExample) add(Notice("discover", "이런 기능도 있어요", line = "안 써 본 기능 하나를 알려 드려요", kani = com.bamnat.app.ui.components.KaniMood.Point,
                section = com.bamnat.app.inbox.InboxSection.SUGGEST, kind = "suggest.discover") {
                val ownParts = remember(calendarParts) { calendarParts.map { it.part.id }.toSet() + setOf("discover", "journal") }
                val trades = investState?.full?.trades?.size ?: 0
                com.bamnat.app.ui.guide.DiscoverNotice(
                    calendar = activeCalendar.id, style = settings.workStyle,
                    othersUp = { notices.heights.any { (k, h) -> h > 0 && k !in ownParts } },
                    startUp = startRows != null || startCardUp,
                    ready = { need ->
                        when (need) {
                            com.bamnat.core.guide.DiscoverNeed.DAYS -> !snap.isEmpty
                            com.bamnat.core.guide.DiscoverNeed.PAY -> payProfile != null
                            com.bamnat.core.guide.DiscoverNeed.JOBS -> moneyBook.jobs.any { !it.cancelled }
                            com.bamnat.core.guide.DiscoverNeed.SALES -> moneyBook.sales.any { it.sales > 0 }
                            com.bamnat.core.guide.DiscoverNeed.TRADES -> trades > 0
                            com.bamnat.core.guide.DiscoverNeed.MONEY -> moneyBook.entries.size >= 10
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            })
        }.let { all ->
            // [EASE-03] 1.38 PC 994c2328 (첫날 알림 허용 줄 밑에 '알림 1개 더 보기 · 근무주기 다음 단계'): on the first setup's day only
            // what keeps the calendar working stays (the alarm line, money promised and late, the developer’s notice, what an invite
            // the user just told became, the user's own parts); the next steps, asks and nudges wait for the next day.
            if (c31.firstSetup.quietToday()) all.filter { it.key !in FIRST_DAY_WAITS } else all
        })
        }
        // [EASE-01] 1.38 (오늘근무 분석: 달력이 위로): '할 수 있는 것'·오늘의 팁 under the month, never on the first setup's day (EASE-03).
        val guidePart: @Composable () -> Unit = {
            if (guideHere && startRows == null && !startCardUp && !c31.firstSetup.quietToday()) {
                val askStates by context.appContainer.asks.states.collectAsStateWithLifecycle()
                val tipAllowed = !askOn || com.bamnat.core.profile.AskScheduler.tipAllowed(askStates[activeCalendar.id] ?: com.bamnat.core.profile.AskState(), today)
                com.bamnat.app.ui.guide.GuideArea(activeCalendar.id, settings.workStyle, snap, moneyBook, payProfile, settings.weekStart,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp), trades = investState?.full?.trades?.size ?: 0, tipAllowed = tipAllowed)
            }
        }
        // [CUST-01] The parts under the month, by part; [CVW-06] 1.35 any of them can stand above it too.
        val partBody: @Composable (CalendarPart) -> Unit = { part ->
            when (part) {
                CalendarPart.DAY_CARD -> {
                    if (!wide) {
                        // [CAL-05] One day card for every kind of work: its jobs, sales or shift, with the chips that fit.
                        DayPreview(
                            day = remember(snap, selected) { snap.resolver.resolve(selected) },
                            today = today,
                            snapshot = snap,
                            vm = vm,
                            onDateChange = onDateChange,
                            onOpen = { sheetOpen = true },
                            onMemo = onMemo,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            weekNote = weekNote,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                CalendarPart.HOURS -> if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS) {
                    HoursLine(
                        calculator = calculator,
                        limits = limits,
                        selected = selected,
                        month = visibleMonth,
                        onOpenStats = onOpenStats,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        tabName = settings.workStyle.words().tab,
                    )
                }
                CalendarPart.REST -> if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS) {
                    val rules = settings.rules
                    val rest = remember(snap, visibleMonth, rules) { vm.restIssues(snap, visibleMonth, rules) }
                    // [NURS-05][JOB-03] Nights, night pay and a 공무원's 시간외, worked out off the main thread.
                    val nights by produceState<CalendarViewModel.MonthNights?>(null, payProfile, snap, visibleMonth) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.monthNights(payProfile, snap, visibleMonth) }
                    }
                    MonthRestCard(rest, nights, onPick = vm::select, onRules = { rulesOpen = true },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                }
                CalendarPart.MEMOS -> memoPart()
                // 1.39 [FLOW-22] 하루 이야기 of the picked day, as a card the user placed.
                CalendarPart.DAY_STORY -> DayStoryCalendarCard(vm, snap, selected, onOpenMemo = onOpenMemo,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                else -> Unit
            }
        }
        // [CUST-01] The parts above the month in the user's order (화면 꾸미기); [CVW-06] the ones moved over it from below too.
        // [CNV-06] 요약·횟수 줄 above the month stay just over it (below).
        // 2.0.0 [RVP-45] 조건 1 '달력 위는 한 줄' (검토 UIX-01: 근무 중 줄·알림 띠·보기 줄이 쌓여 달력이 화면 41~58%에서 시작): they stand over
        // the month while no band is up; with a band they are the first thing under the month. A low screen or very large text keeps
        // 오늘의 흐름's one line only (1.38 PC 12.3 빈틈 7), the rest under the month as before.
        val overParts = calendarParts.filter { isAbove(it.part) && it.part.movable && it.shown && it.part != CalendarPart.MONTH && it.part != CalendarPart.SUMMARY && it.part != CalendarPart.COUNTS }
        val partsOver = when {
            holding -> emptyList()
            // One week and its whole day ([CAL-03]) has no place under a month: the parts keep their place over it.
            notices.bandKey != null && !(weekOnly && !wide) -> emptyList()
            low -> overParts.filter { it.part == CalendarPart.TODAY_FLOW }
            else -> overParts
        }
        val partsUnder = if (holding) emptyList() else overParts - partsOver.toSet()
        if (!holding) {
            if (partsOver.isEmpty()) aboveHeights[1] = 0
            else Column(Modifier.onSizeChanged { aboveHeights[1] = it.height }) {
                partsOver.forEach { placed -> aboveParts[placed.part]?.second?.invoke() ?: partBody(placed.part) }
            }
        }
        // [CAL-03] One week and the whole day under it, or the month.
        if (weekOnly && !wide) {
            weekPart()
        } else {
            // [CAL-06] The month in the kind of work's own three figures; [CAL-51] one line on a low screen.
            // [CNV-06] 1.37: drawn over the month (summaryPart) or, moved, under it (summaryBelow).
            val summaryAt: @Composable (Modifier, Boolean) -> Unit = { place, below ->
                if (!partShown(CalendarPart.SUMMARY) || isAbove(CalendarPart.SUMMARY) == below) Unit
                else if (focus == com.bamnat.core.work.CalendarFocus.INVEST && investVm != null) {
                    // [INV-06] The month in the investor's own figures.
                    com.bamnat.app.ui.invest.InvestMonthBar(investVm, visibleMonth, onOpen = onOpenStats, modifier = place.padding(horizontal = 16.dp, vertical = 4.dp))
                } else if (!(focus == com.bamnat.core.work.CalendarFocus.SHIFTS && overview.isEmpty && payEstimate == null)) {
                    // [SRV-10] N5: the month's rest signs, counted where the month is summed up.
                    val restSigns = if (focus != com.bamnat.core.work.CalendarFocus.SHIFTS || !settings.restCheck) 0
                        else remember(snap, visibleMonth, settings.rules) { vm.restIssues(snap, visibleMonth, settings.rules).size }
                    MonthSummaryBar(
                        overview = overview,
                        payEstimate = payEstimate,
                        takeHome = takeHome,
                        payNeeds = payNeeds,
                        versus = versus,
                        onOpen = if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS) onOpenPay else onOpenMoney,
                        modifier = place.padding(horizontal = 16.dp, vertical = 4.dp),
                        restSigns = restSigns,
                        compact = low,
                        tabName = settings.workStyle.words().tab,
                        // [R136-03] In the month the phone started, the days before it are the 근무표's, and it says so.
                        rangeLabel = payRange?.let { "${it.from.monthValue}/${it.from.dayOfMonth}~${it.to.monthValue}/${it.to.dayOfMonth} · ${visibleMonth.monthValue}월 월급분" } ?: startDay?.takeIf { YearMonth.from(it) == visibleMonth && it.dayOfMonth > 1 &&
                            snap.entries.none { e -> e.date.isBefore(it) && e.override != ShiftOverride.None } }
                            ?.let { "${it.monthValue}/${it.dayOfMonth} 전은 근무표" },
                        // 1.39 [PAYX-64] 발굴 G3-M1: 1일~말일 ↔ 월급분 right here, the same setting as 통계's chips (a big-text or low screen keeps the room).
                        rangeSwitch = payProfile?.period?.takeIf { payShown && !low && com.bamnat.app.data.StatsPeriod.PAY.byPay(it) }?.let { p ->
                            if (payRange != null) "1일~말일로 보기" else "${visibleMonth.monthValue}월 월급분(${p.label(visibleMonth)})으로 보기"
                        },
                        onRangeSwitch = {
                            vm.updateSettings { s -> s.copy(statsPeriod = if (payRange != null) com.bamnat.app.data.StatsPeriod.MONTH else com.bamnat.app.data.StatsPeriod.PAY) }
                        },
                    )
                }
            }
            val summaryPart: @Composable (Modifier) -> Unit = { summaryAt(it, false) }
            val summaryBelow: @Composable (Modifier) -> Unit = { summaryAt(it, true) }
            // [CAL-31] I 리뷰 N10: past the checked table the red days are worked out, without 임시공휴일 or 선거일.
            val computedNote: @Composable () -> Unit = {
                if (com.bamnat.core.holiday.KoreanHolidays.isComputed(visibleMonth.year) &&
                    (1..visibleMonth.lengthOfMonth()).any { com.bamnat.core.holiday.KoreanHolidays.isHoliday(visibleMonth.atDay(it)) }
                ) {
                    Text(
                        "${visibleMonth.year}년 공휴일은 계산값이에요. 임시공휴일·선거일은 빠질 수 있어요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
            }
            // [CAL-06][CAL-08] The month's shifts at a glance, what the marks mean, and grid or list.
            // [TAB-04] 1.36 달력 판: 월·목록·근무 띠·타임라인. A 목록 kept from before 1.36 (the old switch) still reads as 목록.
            val savedBoard = remember(settings.tabViews, listView) {
                com.bamnat.core.layout.TabViews.decode(settings.tabViews).cal.let { if (it == com.bamnat.core.layout.CalendarBoard.MONTH && listView) com.bamnat.core.layout.CalendarBoard.LIST else it }
            }
            val board = if (paint == null) savedBoard else com.bamnat.core.layout.CalendarBoard.MONTH
            val showList = board != com.bamnat.core.layout.CalendarBoard.MONTH
            val pickBoard: (com.bamnat.core.layout.CalendarBoard) -> Unit = { b ->
                vm.setListView(b == com.bamnat.core.layout.CalendarBoard.LIST)
                vm.setBoard(b)
            }
            val countsPart: @Composable (Modifier) -> Unit = { place ->
                // [TAB-04] PC 확인 bf62e966: at 1.3× text the '월 ▾' button pushed '휴 10' off the row, so with big text the counts
                // take a line of their own under the buttons.
                val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.2f
                val teamsChip: @Composable () -> Unit = {
                    openTeams?.let { open ->
                        androidx.compose.material3.AssistChip(
                            // [CAL-45] Unfolded, the teams open beside the month; on the cover screen, their own page.
                            onClick = { if (wide && tabletop == null) teamsBeside = !teamsBeside else open() },
                            label = { Text("모든 조", maxLines = 1, softWrap = false) },
                            leadingIcon = { Icon(Icons.Outlined.Groups, null, Modifier.size(18.dp)) },
                            modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = "모든 조 보기와 대근 찾기" },
                        )
                    }
                }
                val switch: @Composable () -> Unit = {
                    Box {
                        var boardMenu by remember { mutableStateOf(false) }
                        CalendarViewSwitch(listView = showList, onListView = { if (paint == null) boardMenu = true }, onLegend = { legendOpen = true }, boardLabel = board.label)
                        CalendarBoardMenu(boardMenu, board, com.bamnat.core.layout.CalendarBoard.expertFor(settings.workStyle), onPick = pickBoard, onDismiss = { boardMenu = false })
                    }
                }
                // PC 확인 c9a89b20: '타임라인 ▾'·'근무 띠 ▾' are longer than '월 ▾' and cut '휴 10', so other 판 take the line below too.
                if (bigText || board != com.bamnat.core.layout.CalendarBoard.MONTH) Column(place.fillMaxWidth().padding(start = 16.dp, end = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        teamsChip()
                        Spacer(Modifier.weight(1f))
                        switch()
                    }
                    if (partShown(CalendarPart.COUNTS) && isAbove(CalendarPart.COUNTS)) MonthShiftCounts(snap, visibleMonth)
                } else Row(place.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The counts take what is left, so the two buttons always sit at the right edge.
                    // [CAL-24] 모든 조 보기 one tap from the month, before the counts, on every screen width.
                    teamsChip()
                    Box(Modifier.weight(1f)) { if (partShown(CalendarPart.COUNTS) && isAbove(CalendarPart.COUNTS)) MonthShiftCounts(snap, visibleMonth) }
                    switch()
                }
            }
            if (sideways) {
                // [CAL-51] On its side the summary's one line and the counts share a row.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(0.45f)) { summaryPart(Modifier) }
                    countsPart(Modifier.weight(0.55f))
                }
                computedNote()
            } else if (paint == null) Column {
                summaryPart(Modifier)
                // 2.0.0 [RVP-45] 조건 1: on the month board the 모든 조·횟수·보기 row and the 공휴일 note are right under the month; the other
                // boards keep the row over them, where the way back to the month is.
                if (board != com.bamnat.core.layout.CalendarBoard.MONTH) {
                    computedNote()
                    countsPart(Modifier)
                }
            }
            // 1.39 [FLOW-03] The month just set up fills from today (its one line is the band over the month).
            val fillNow = rememberSetupFill(fillMine, activeCalendar.id, ready = !snap.isEmpty)
            if (!showList) WeekdayHeader(settings.weekStart, Modifier.padding(start = 8.dp + WEEK_BAR_SPACE, end = 8.dp))
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = if (showList) 0.dp else 8.dp)
                    // [CAL-15] Where the month's last row ends, for the FAB to keep under it.
                    .onGloballyPositioned { c -> gridTop = c.positionInWindow().y; gridBottom = gridTop + c.size.height },
            ) {
                // [CAL-51] On its side a phone keeps its cells as short as they hold, so weeks show under the bars.
                // [CELL-01] 1.39 Cells that list each day's jobs and memos are taller, so the lines have room (not on its side).
                val listOn = cellListOn(settings.cellList, focus == com.bamnat.core.work.CalendarFocus.JOBS, settings.memoInCells, calView, sideways, hasMemos = datedMemos.isNotEmpty())
                // [CELL-03] Tall only for a month that has jobs or memos to list; an empty month keeps its weeks in sight.
                val monthHasLines = remember(datedMemos, moneyBook.jobs, visibleMonth, focus) {
                    datedMemos.any { YearMonth.from(it.date!!) == visibleMonth } ||
                        focus != com.bamnat.core.work.CalendarFocus.SHIFTS && moneyBook.jobs.any { j ->
                            !j.date.isAfter(visibleMonth.atEndOfMonth()) && !(j.endDate ?: j.date).isBefore(visibleMonth.atDay(1))
                        }
                }
                val tall = listOn && cellListTall(settings.cellList, focus == com.bamnat.core.work.CalendarFocus.JOBS, settings.memoInCells, calView, monthHasLines)
                val cellHeight = (if (tall) maxOf(calView.cellHeight(maxWidth, sideways), listCellHeight(maxWidth)) else calView.cellHeight(maxWidth, sideways)) + cellTextGrowth()
                val coverRows = fabKeepRows(visibleMonth, settings.weekStart)
                val ownPx = if (showList) Float.NaN else with(density) { cellHeight.toPx() } * coverRows
                androidx.compose.runtime.SideEffect { ownRowsPx = ownPx }
                // [CAL-09] With something to stamp, a drag paints and a long press starts a range.
                val stamping = mark != null || (paint != null && paint != PaintTool.Pick)
                val memoCounts = remember(datedMemos) { datedMemos.groupingBy { it.date!! }.eachCount() }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.bamnat.app.logic.LocalDayFacts provides dayFacts,
                    // [CYC-04] 1.38 업종 기념일.
                    LocalOrgDayGroup provides orgGroupOf(settings),
                ) {
                HorizontalPager(
                    state = pagerState,
                    // [CAL-08] The list is as tall as its month; the grid is always six weeks.
                    modifier = Modifier.fillMaxWidth().then(if (showList) Modifier.animateContentSize() else Modifier.height(cellHeight * GRID_ROWS)),
                    // [PERF-11] The months either side are made once the first month is on screen, not with it.
                    beyondViewportPageCount = if (showList || !settled) 0 else 1,
                    key = { it },
                    verticalAlignment = Alignment.Top,
                    // [CAL-01] With a stamp chosen, a sideways drag paints days instead of turning the month.
                    // [TAB-04] N1 연간 판 turns by its own year arrows; a sideways swipe would only creep a month along the same year.
                    userScrollEnabled = !stamping && board != com.bamnat.core.layout.CalendarBoard.YEAR,
                ) { page ->
                    if (board == com.bamnat.core.layout.CalendarBoard.WEEK) {
                        val titles = remember(datedMemos) {
                            datedMemos.groupBy { it.date!! }.mapValues { (_, l) -> l.map { m -> m.title.ifBlank { m.preview }.trim().lineSequence().firstOrNull().orEmpty() }.filter { it.isNotBlank() } }
                        }
                        WeekCardsMonth(pageToMonth(baseMonth, page), snap, settings.weekStart, today, selected, titles, onDayClick)
                    } else if (board == com.bamnat.core.layout.CalendarBoard.YEAR) {
                        val year = pageToMonth(baseMonth, page).year
                        YearBoard(year, snap, settings.weekStart, today,
                            onYear = { d -> goToMonth(pageToMonth(baseMonth, page).plusYears(d.toLong())) },
                            onOpen = { m -> goToMonth(m); pickBoard(com.bamnat.core.layout.CalendarBoard.MONTH) }, style = settings.workStyle)
                    } else if (board == com.bamnat.core.layout.CalendarBoard.BAND) {
                        ShiftBandMonth(pageToMonth(baseMonth, page), snap, settings.weekStart, today, selected, onDayClick)
                    } else if (board == com.bamnat.core.layout.CalendarBoard.LINE) {
                        TimelineMonth(pageToMonth(baseMonth, page), snap, today, selected, onDayClick)
                    } else if (showList) {
                        AgendaList(
                            month = pageToMonth(baseMonth, page),
                            focus = focus,
                            snapshot = snap,
                            money = moneyBook,
                            memoCounts = memoCounts,
                            weekStart = settings.weekStart,
                            today = today,
                            selected = selected,
                            onDay = onDayClick,
                            onQuickAction = onQuickAction,
                        )
                    } else MonthGrid(
                        // [MAT-07] 1.36 달 넘김 (밀기·스르륵·책장·다가오기·겹쳐 오기·움직이지 않기).
                        modifier = Modifier.monthTurn(com.bamnat.app.ui.theme.LocalMotion.current.turn) {
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        },
                        month = pageToMonth(baseMonth, page),
                        snapshot = snap,
                        calculator = calculator,
                        weekStart = settings.weekStart,
                        selected = selected,
                        today = today,
                        memoDates = memoDates,
                        showHours = settings.showHoursInCells,
                        showLunar = settings.showLunar,
                        cellHeight = cellHeight,
                        onDayClick = onDayClick,
                        onQuickAction = onQuickAction,
                        money = moneyBook,
                        showMoney = focus.moneyFirst,
                        showSpend = settings.spendInCells,
                        spendHeat = settings.spendHeat && !stamping,
                        memoTexts = memoTexts,
                        memoLists = memoLists.takeIf { listOn && !stamping },
                        listEmptyDays = tall,
                        onPaint = if (stamping) paintDays else null,
                        weeklyLimitHours = limits.weeklyLimitHours,
                        focus = focus,
                        onLongPress = if (stamping) startRange else null,
                        rangeFrom = rangeFrom,
                        hourly = hourlyCells,
                        invest = investCells,
                        // [DAYW-01] A day calendar shows its exceptions only; painting shows every day.
                        quietWorkDays = settings.workStyle == com.bamnat.core.work.WorkStyle.DAY && !stamping,
                        // 1.32 [CALX-06] 마감 다음 날 lines, while 통계 counts by the 급여 기간.
                        cutDays = if (payRange == null || stamping) emptySet() else pageToMonth(baseMonth, page).let { m ->
                            com.bamnat.core.hours.StatsRange.cutDays(payProfile?.period, m.atDay(1).minusDays(7), m.atEndOfMonth().plusDays(14))
                        },
                        // 1.32 [WTYP-11] 희망 오프 ♡, unless it is off in 기능 켜고 끄기.
                        wishDays = if (stamping || !wishShown) emptySet() else wishOff.days,
                        // [EASE-04] 1.38 월급날 '급여', where the pay is counted.
                        payDays = payDaysOn(payProfile?.period, pageToMonth(baseMonth, page)).takeUnless { stamping || !payShown }.orEmpty(),
                        fill = fillNow.takeIf { pageToMonth(baseMonth, page) == YearMonth.from(today) },
                    )
                }
                }
                // B 1.32 [CALX-01] (사용자 2026-10-28 "달 넘길 때 몇 월인지 인지 부족"): while the month turns, the month it lands on
                // shows big over the grid, then fades; the top bar's small title alone was missed mid-swipe.
                SwipeMonthBadge(pagerState, baseMonth, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
            }
            if (!sideways && paint == null && board == com.bamnat.core.layout.CalendarBoard.MONTH) {
                countsPart(Modifier)
                computedNote()
            }
            // [RUL-03] 1.36 단추 on the calendar screen, for the picked day.
            if (paint == null) com.bamnat.app.logic.LogicButtons(com.bamnat.core.formula.ButtonPlace.CALENDAR, selected)
            // 1.32 [LEDX-23] What the strips under the dates mean, while the heat map is on.
            if (settings.spendHeat && focus == com.bamnat.core.work.CalendarFocus.SHIFTS && paint == null) {
                com.bamnat.app.ui.money.SpendHeatLegend(Modifier.padding(start = 16.dp, top = 4.dp))
            }
            // 1.32 [CALX-06] "이번 급여 기간은 10월 20일에 끝나요 · 5일 남았어요" (or the 주급 week), on this month only.
            if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS && paint == null && visibleMonth == YearMonth.from(today)) {
                com.bamnat.core.hours.StatsRange.endsLine(payProfile?.period, today, payProfile?.holidayWeekFrom ?: java.time.DayOfWeek.MONDAY)?.let { line ->
                    // [BLU-05] 1.36 부품 '달력 아래 요약'(cal.foot).
                    val foot = com.bamnat.app.ui.parts.partStyle("cal.foot")
                    if (foot.hide != true) com.bamnat.app.ui.parts.PartText(line, foot,
                        MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                            .then(com.bamnat.app.ui.parts.PartMods.box(foot, MaterialTheme.shapes.small)))
                }
            }
            // [CAL-15] Room for the floating button under the month, so its last row (the 30th, the 31st)
            // can always be scrolled clear of it.
            Spacer(Modifier.height(if (paint == null) FAB_ROOM else 12.dp))
            // [CUST-01] The parts under the month in the user's order (화면 꾸미기); each keeps its own switch.
            // [PERF-11] 1.28 AO (검토 6-1 병목 5): made from the frame after the first month, so the month comes alone first.
            Column(Modifier.fillMaxWidth().onSizeChanged { belowPx = it.height }) {
            // [RVP-45] The parts that stand over the month when no band is up.
            if (settled) partsUnder.forEach { placed -> aboveParts[placed.part]?.second?.invoke() ?: partBody(placed.part) }
            if (settled) guidePart()
            if (settled) calendarParts.filter { !isAbove(it.part) && it.shown && it.part != CalendarPart.MONTH }.forEach { placed ->
                when (placed.part) {
                    // [CNV-06] 1.37 요약·횟수 줄 moved under the month.
                    CalendarPart.SUMMARY -> Box(Modifier.fillMaxWidth()) { summaryBelow(Modifier) }
                    CalendarPart.COUNTS -> MonthShiftCounts(snap, visibleMonth, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    else -> aboveParts[placed.part]?.second?.invoke() ?: partBody(placed.part)
                }
            }
            }
        }
    }

    val detailPart: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { m, bottom ->
        DayPanel(
            date = selected,
            today = today,
            snapshot = snap,
            vm = vm,
            groups = groups,
            onDateChange = onDateChange,
            onOpenMemo = onOpenMemo,
            onNewMemo = onNewMemo,
            onOpenShiftTypes = onOpenShiftTypes,
            onOpenPay = onOpenPay,
            onOpenExtraKinds = onOpenExtraKinds,
            modifier = m,
            memoFocus = memoFocus,
            onMemo = onMemo,
            bottomPadding = bottom,
            weekNote = weekNote,
            compact = wide,
            onRealign = { realigning = it }, // 1.38 [FLOW-08]
        )
    }

    val bottomSpace = if (paint != null) 132.dp else 88.dp
    // 1.39 [CALX-07] The scrolling page's own end: nothing under the month, no room for a card that is not there.
    val pageEnd = if (paint == null && settled && belowPx == 0) 0.dp else bottomSpace
    val fab: @Composable BoxScope.() -> Unit = {
        // [CAL-15] Where the FAB rests: the bottom of the area it floats in.
        Spacer(Modifier.matchParentSize().onGloballyPositioned { c -> fabAreaBottom = c.positionInWindow().y + c.size.height })
        val style = settings.workStyle
        val place = Modifier.align(Alignment.BottomEnd).padding(FAB_MARGIN).offset { IntOffset(0, fabDrop.value.roundToInt()) }
            // Coming up from the edge it fades in with how much of it shows, so a sliver is not a stray shape.
            .graphicsLayer { alpha = ((fabLift - fabDrop.value) / fabHeight).coerceIn(0f, 1f) }
        if (startCardShown && paint == null) Unit
        else if (!fabGone && paint == null && style != null && !style.usesPayEngine) {
            // [WORK-02][WORK-03] Field and shop work add the day's job or sales instead of stamping shifts.
            val label = if (style.usesJobs) "일감 적기" else if (style.usesTrades) "매매 기록" else "매출 적기"
            // 2.0.0 [RVP-06] The shared floating-button rule: TalkBack reads the words, big letters keep it to the icon.
            com.bamnat.app.ui.components.AddFab(label, onClick = { quickMoney = true }, open = fabExpanded, modifier = place)
        } else if (!fabGone && paint == null) {
            com.bamnat.app.ui.components.AddFab(
                "빠른 입력",
                // [CAL-01] Nothing is chosen yet, so the first tap cannot paint a day by surprise.
                onClick = { vm.setPaint(PaintTool.Pick) },
                open = fabExpanded,
                icon = Icons.Filled.EditCalendar,
                modifier = place,
            )
        }
    }
    val them = viewingReceived
    if (them != null) {
        // Each received calendar opens on its own month and day, not the last one's.
        key(them.id) {
            ReceivedCalendarView(
                vm = received,
                cal = them,
                mine = snap,
                weekStart = settings.weekStart,
                wide = wide,
                today = today,
                startMonth = visibleMonth,
                identityBar = identityBar,
                rules = settings.rules,
                onSay = vm::say,
            )
        }
    } else Box(Modifier.fillMaxSize().clipToBounds()) {
        // [RCX-30] 1.35 그림 꾸미기: the background picture under the page.
        PhotoBackdrop()
        if (tabletop != null) {
            // [DAILY-09] The month above the hinge, the day panel below it; nothing sits on the fold itself.
            var areaTop by remember { mutableIntStateOf(0) }
            // The month, not the cards above it, is what the half above the hinge is for: it opens there once.
            LaunchedEffect(tabletop, gridTop.isNaN()) {
                if (!gridTop.isNaN()) {
                    val room = with(density) { MONTH_ROW_ROOM.toPx() }
                    pageScroll.scrollTo((pageScroll.value + gridTop - areaTop - room).roundToInt().coerceAtLeast(0))
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { areaTop = it.positionInWindow().y.roundToInt() }) {
                val upper = with(density) { tabletop.upperHeight(areaTop, constraints.maxHeight).toDp() }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(upper).clipToBounds()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(pageScroll)
                                .padding(bottom = pageEnd),
                        ) { calendarPart() }
                        fab()
                    }
                    Spacer(Modifier.height(with(density) { tabletop.hinge.toDp() }))
                    HorizontalDivider()
                    // [CAL-45] With quick input on, the half under the thumbs is a pad of big keys (I 리뷰 N5 c):
                    // the month above, every stamp in reach below, nothing floating over the days.
                    if (paint != null) {
                        PaintBar(snapshot = snap, tool = paint, last = lastPaint, onTool = vm::setPaint, onUndo = vm::undoLastPaint,
                            mark = mark, onMark = vm::setMark, focus = focus, onTypeMonth = { fillMonth(visibleMonth) },
                            stamp = stamp, stamps = stamps, onStamp = vm::setStamp, large = true, summary = paintSummary,
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp))
                    } else detailPart(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), 16.dp)
                }
            }
        } else if (wide) {
            // [CAL-01] About 3:2, so the unfolded calendar's days are as big as on the cover screen; the
            // day panel keeps the room its title and switches need.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val panelWidth = (maxWidth * 0.4f).coerceIn(DAY_PANEL_MIN_WIDTH, DAY_PANEL_MAX_WIDTH)
                Row(Modifier.fillMaxSize()) {
                    // [CAL-15] Clipped, so the FAB riding under the month slides out under the pane's edge.
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(pageScroll)
                                .padding(bottom = pageEnd),
                        ) { calendarPart() }
                        fab()
                    }
                    VerticalDivider()
                    val typing = typingMonth
                    // [NAV-02] 1.25 Z (U4): back closes the panel beside the month, as its own close does; it used to close the app.
                    androidx.activity.compose.BackHandler(enabled = typing != null) { typingMonth = null }
                    androidx.activity.compose.BackHandler(enabled = typing == null && teamsBeside && openTeams != null) { teamsBeside = false }
                    when {
                        // [CAL-45] Unfolded, the month being typed sits beside the calendar instead of in a sheet over it
                        // (I 리뷰 N5 a): the roster on paper, the keypad on the right, the month filling in on the left.
                        typing != null -> DutyTypingPanel(snap, typing, onDismiss = { typingMonth = null },
                            onSave = { changes -> vm.applyDuty(changes); typingMonth = null },
                            rules = vm.rulesFor(snap, typing.atDay(1), settings.rules), onRules = { rulesOpen = true },
                            weekStart = settings.weekStart,
                            modifier = Modifier.width(panelWidth.coerceAtLeast(DUTY_PANEL_MIN_WIDTH)).fillMaxHeight().padding(top = 8.dp))
                        // [CAL-45] 모든 조 beside the month (I 리뷰 N5 b).
                        teamsBeside && openTeams != null -> TeamsPanel(activeCalendar.id, snap, visibleMonth, selected,
                            onClose = { teamsBeside = false },
                            modifier = Modifier.width(panelWidth).fillMaxHeight().padding(top = 8.dp))
                        // [DAY-01] The same day panel as the sheet: the header stays, the groups scroll.
                        else -> detailPart(Modifier.width(panelWidth).fillMaxHeight().padding(top = 4.dp), bottomSpace)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(pageScroll)
                    .padding(bottom = pageEnd),
            ) {
                com.bamnat.app.ui.theme.PhotoHead()
                calendarPart()
            }
        }

        if (!wide) fab()
        if (paint != null && tabletop == null) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                PaintBar(snapshot = snap, tool = paint, last = lastPaint, onTool = vm::setPaint, onUndo = vm::undoLastPaint,
                    mark = mark, onMark = vm::setMark, focus = focus, onTypeMonth = { fillMonth(visibleMonth) },
                    stamp = stamp, stamps = stamps, onStamp = vm::setStamp, summary = paintSummary)
            }
        }
    }

    typingMonth?.takeIf { !wide || tabletop != null }?.let { m ->
        DutyTypingSheet(snap, m, onDismiss = { typingMonth = null }, onSave = { changes -> vm.applyDuty(changes); typingMonth = null },
            rules = vm.rulesFor(snap, m.atDay(1), settings.rules), onRules = { rulesOpen = true }, weekStart = settings.weekStart)
    }
    if (rulesOpen) {
        RestRulesSheet(settings.rules, vm.rotation(snap, visibleMonth.atDay(1)), onDismiss = { rulesOpen = false }) { r ->
            vm.setRestRules(r); rulesOpen = false
        }
    }

    if (investVm != null) {
        com.bamnat.app.ui.invest.InvestMessages(investVm, snackbarHostState)
        investSheet?.let { s -> com.bamnat.app.ui.invest.InvestSheetHost(investVm, s) { investSheet = it } }
    }
    if (quickMoney && investVm != null) {
        // [INV-03] The trade sheet for the picked day.
        LaunchedEffect(Unit) {
            investSheet = com.bamnat.app.ui.invest.InvestSheet.TradeEdit(investVm.draftFor(null, selected))
            quickMoney = false
        }
    } else if (quickMoney) {
        if (settings.workStyle?.usesJobs == true) {
            com.bamnat.app.ui.money.JobDialog(selected, null, onSave = { vm.saveJob(it); quickMoney = false }, onDismiss = { quickMoney = false })
        } else {
            com.bamnat.app.ui.money.SalesDialog(selected, moneyBook.on(selected).sales,
                onSave = { vm.saveSales(it); quickMoney = false }, onDismiss = { quickMoney = false })
        }
    }

    if (editingLayout) {
        CalendarEditSheet(settings, update = vm::updateSettings, onDismiss = { editingLayout = false }, calendar = activeCalendar.id)
    }
    coverAsk?.let { ask -> CoverAskDialog(ask, vm.coverWord(), onAnswer = { asCover, keep -> vm.answerCover(ask, asCover, keep) }, onDismiss = vm::dismissCover) }
    restAlarms?.let { ask -> RestAlarmDialog(ask, onAnswer = { off -> vm.answerRestAlarms(ask, off) }, onDismiss = vm::closeRestAlarms) }

    if (sheetOpen && !wide) {
        DaySheet(snackbar = sheetSnackbar, onDismiss = { sheetOpen = false }) { detailPart(Modifier.fillMaxWidth(), 24.dp) }
    }
    if (reviewing) {
        ExtraReviewDialog(snap, vm, onOpenDay = { d ->
            reviewing = false
            onDateChange(d)
            if (!wide) sheetOpen = true
        }, onDismiss = { reviewing = false })
    }
    if (explainingSwap) {
        SwapCoverDialog(
            days = swapCovers,
            snapshot = snap,
            onConvert = { explainingSwap = false; vm.convertSwapCovers(swapCovers) },
            onKeep = { explainingSwap = false; vm.keepSwapCovers() },
            onDismiss = { explainingSwap = false },
        )
    }

    if (switching) {
        CalendarSwitcherSheet(
            rows = calendarRows,
            currentKey = currentRow.key,
            onPick = { row -> switching = false; pickCalendar(row) },
            onAddCalendar = { switching = false; onAddCalendar() },
            onAddReceived = if (received.enabled) ({ switching = false; adding = "" }) else null,
            onManage = { switching = false; onManageCalendars() },
            onEdit = { cal -> switching = false; onEditCalendar(cal.id) },
            onRemove = { cal -> received.remove(cal.id) },
            onDismiss = { switching = false },
            onCombined = { switching = false; onOpenCombined() },
            onTogether = togetherAt?.let { t -> { switching = false; t() } },
            togetherShop = togetherStyle == com.bamnat.core.work.WorkStyle.BUSINESS,
            togetherField = togetherStyle == com.bamnat.core.work.WorkStyle.FIELD,
            // [UX-03] Each of the user's calendars shares from here, as that calendar.
            onShare = { cal ->
                switching = false
                received.view(null)
                vm.setPaint(null)
                vm.viewCalendar(cal.id)
                // The sheet is keyed by the calendar on screen, so it opens as this one.
                shareCalendar()
            },
        )
    }
    adding?.let { start ->
        AddReceivedSheet(
            vm = received,
            startId = start.ifEmpty { null },
            today = today,
            onDismiss = { adding = null },
            onAdded = { adding = null; vm.setPaint(null) },
        )
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = visibleMonth,
            onDismiss = { showMonthPicker = false },
            onPick = { m ->
                showMonthPicker = false
                goToMonth(m)
            },
            selected = selected,
            onPickDate = { d ->
                showMonthPicker = false
                vm.select(d)
                goToMonth(YearMonth.from(d))
            },
        )
    }
    if (legendOpen) CalendarLegendSheet(focus, snap, onDismiss = { legendOpen = false },
        payDayOn = payShown && payProfile?.period?.payDay != null, orgGroup = orgGroupOf(settings),
        payShown = payShown, hasPay = payProfile != null, style = settings.workStyle)
}

/** [DAY-01][DAY-03] The whole day over the calendar, open at once, with its own snackbar for undo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(snackbar: SnackbarHostState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // [STAB-01] A message still showing here when the sheet closed used to wait for a host that was gone. 2.0.0 [RVP-23]: the app's
    // one line moves to the host under the sheet by itself (AppSnack), so a 되돌리기 made in the sheet is still seen after it closes.
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = state, holdHeight = true) {
        // [UX-13] 1.25 Z (U13): scrolling the day up to its top no longer goes on to close it.
        Box(Modifier.fillMaxWidth().sheetScrollGuard()) {
            content()
            com.bamnat.app.ui.components.AppSnackHost(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }
}

/** How many of each shift this month, e.g. 주 10 · 야 10 · 휴 6 · 특근 2. */
@Composable
private fun MonthShiftCounts(snapshot: ScheduleSnapshot, month: YearMonth, modifier: Modifier = Modifier) {
    val counts = remember(snapshot, month) {
        val days = snapshot.resolver.resolveRange(month.atDay(1), month.atEndOfMonth())
        // [STAT-07] The same count as 근무·급여 and 통계: a working day taken off whole with leave is 휴가, not its code.
        val byType = ShiftMonth.counts(days, snapshot.shiftTypes).map { it.type to it.days }
        // [EXTRA-01] Pieces of extra work by kind, e.g. 대근 3.
        val extras = days.flatMap { it.extras }.groupingBy { it.kind }.eachCount()
        Triple(byType, extras, ShiftMonth.leaveDays(days))
    }
    if (counts.first.isEmpty() && counts.second.isEmpty() && counts.third == 0.0) return
    // [CAL-06] Beside the view buttons a narrow screen drops the badges' icons, and the row fades out
    // at the edge so what is left to scroll to shows.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val icons = maxWidth >= 320.dp
        val scroll = rememberScrollState()
        Row(
            Modifier.fillMaxWidth().fadingEdges(back = { scroll.canScrollBackward }, forward = { scroll.canScrollForward }).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((type, n) in counts.first) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShiftBadge(type, size = 18.dp, outlined = type.isOff, showIcon = icons)
                    Text(" $n", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
                }
            }
            for ((kind, n) in counts.second) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExtraKindMark(kind, size = 18.dp)
                    Text(" ${kind.name} $n", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
                }
            }
            if (counts.third > 0) Text("휴가 ${leaveDays(counts.third)}", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
            Spacer(Modifier.width(20.dp))
        }
    }
}

/** [RVM-18] PC-46: also drawn as the 달력 머리 of a work's 미리 그림 ([com.bamnat.app.ui.recipe.MockMonth]), so the two are one drawing. */
@Composable
internal fun CalendarHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickMonth: () -> Unit,
    onOpenPattern: () -> Unit,
    atToday: Boolean = false,
    onShare: () -> Unit = {},
    /** [CAL-03] ‹ › step a week while one week is on screen. */
    byWeek: Boolean = false,
    /** [MCAL-02] Adds a calendar of the user's own. */
    onAddCalendar: () -> Unit = {},
    /** [UX-03] Opens the sheet that takes a coworker's link; null without a share server. */
    onReceive: (() -> Unit)? = null,
    /** [CALS-01] The calendar on screen's own page; null while a received calendar is shown. */
    onEditCalendar: (() -> Unit)? = null,
    calendarName: String = "",
    /** [READ-11] The calendar on screen, whose last month put in 도구 can take back. */
    calendarId: String = "",
    /** [CUST-01] 화면 꾸미기 for the calendar page; null while a received calendar is shown. */
    onCustomize: (() -> Unit)? = null,
    /** [CAL-24] 모든 조 보기; null when the calendar has no rotation to split into teams. */
    onOpenTeams: (() -> Unit)? = null,
    onTypeMonth: (() -> Unit)? = null,
    /** [FIT-01] The calendar on screen's kind of work: the tools it does not use wait under 더 보기 (kept per [calendarId]). */
    style: com.bamnat.core.work.WorkStyle? = null,
    /** [NAV-03] Which tiles this kind of calendar has and which have to wait (1.25 Z). */
    toolFit: ToolFit = ToolFit(),
    /** [EASE-25] 오늘부터 하루 밀기: the push-and-realign window on today. */
    onPushToday: () -> Unit = {},
    /** 2.0.0 [RVH-42] 검토 PC-29·30: the work's 바로 단추 on the head, as buttons of the head's row. */
    quick: List<HeadQuick> = emptyList(),
    onQuick: (String) -> Unit = {},
    /**
     * 2.0.0 [RVP-45] 검토 U13-01 (달력 머리 두 줄): whose calendar is on screen, at the row's start ([CalendarChip]); null keeps the head
     * without it (a received calendar has its own bar).
     */
    chip: HeadChip? = null,
    /** 2.0.0 [RVP-43] The bell (알림함) of the notices above the month. */
    bell: NoticeStackState? = null,
    modifier: Modifier = Modifier,
) {
    // [QA-01] A cover screen (~344dp) cannot fit four icons, the month and 오늘. 2.0.0 [RVP-45] U13-01: the whole head is one row on
    // every width and text size ([HeadRoom]); what does not fit turns into its icon or waits in 도구 and the calendar chip's list.
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val narrow = maxWidth < 392.dp
    val wideTitle = maxWidth >= 600.dp
    val fontScale = LocalDensity.current.fontScale
    val me = com.bamnat.app.ui.LocalMe.current.let { it.show && !it.inRail }
    var menu by remember { mutableStateOf(false) }
    // [BLU-05] 1.36 부품 꾸미기: 달력 머리, 달 이름, 이동 단추, 오늘 단추.
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    // [FXR-03] With the shown month's day (today in this month, else its first), so 조건부 꾸미기 works on the head too.
    val headScope = com.bamnat.app.logic.scopeOn(com.bamnat.app.logic.dayOfMonth(month))
    val headPart = bp.style("cal.head", scope = headScope)
    val navPart = bp.style("cal.head.nav", scope = headScope)
    val todayPart = bp.style("cal.head.today", scope = headScope)
    val titlePart = bp.style("cal.head.title", scope = headScope)
    val navTint = navPart.ink?.let { androidx.compose.ui.graphics.Color(it.toInt()) } ?: androidx.compose.material3.LocalContentColor.current
    // [CAL-52] A tablet or a phone on its side has the room for a bigger month.
    val baseTitle = when {
        narrow -> MaterialTheme.typography.titleMedium
        wideTitle -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    val workTitle = with(com.bamnat.app.ui.parts.PartMods) { text(titlePart, baseTitle) }
    val otherRows = chip?.rows?.filter { it.key != chip.current.key }.orEmpty()
    val others = otherRows.size
    // 2.0.0 [RVG-34] A 팀 리더's link goes first among the faces, and stays longest (HeadRoom.team).
    val teamRow = otherRows.firstOrNull { (it as? CalendarRow.Got)?.cal?.fromLeader == true }
    fun roomFor(sp: Float) = HeadRoom.of(
        maxWidth.value, fontScale, sp, quick = quick.size, bell = bell != null, me = me, others = others,
        together = chip?.onTogether != null, share = chip?.onShare != null, nameChars = chip?.current?.title?.length ?: 0,
        team = teamRow != null,
    ).let { if (chip == null) it.copy(name = false) else it }
    val titleSp = workTitle.fontSize.takeIf { it.isSp }?.value ?: baseTitle.fontSize.value
    // 검토 HQ-22 (큰 달 제목 26~30의 작품을 입으면 접은 폭에서 달 제목이 '2026'으로 아주 작게 줄어요, 조건 12): the month is never shrunk
    // to fit; when even the folded row has no room for the work's big month, it steps down to the screen's own size.
    val firstRoom = roomFor(titleSp)
    val stepDown = firstRoom.stepDown && titleSp > baseTitle.fontSize.value
    val room = if (stepDown) roomFor(baseTitle.fontSize.value) else firstRoom
    val titleText = if (stepDown) workTitle.copy(fontSize = baseTitle.fontSize) else workTitle
    val quickRoom = room.quick
    val quickShown = quickRoom.shown
    Row(
        // [LAY-03] 1.36 꾸밈 층 on the calendar's top (달력 머리).
        // [RVH-42] Where a tap presses the work's buttons (the phone's own calendar), they are the row's buttons below, not drawn over it.
        Modifier.fillMaxWidth().then(
            // [PC-62] A head with its own box (바깥 여백·모서리) has the photo's ground under that box, not round the row.
            if (headPart.out == null && headPart.corners.all { it == null }) com.bamnat.app.ui.theme.PhotoGround.head(top = 8.dp)
            else com.bamnat.app.ui.theme.PhotoGround.head(top = 8.dp + (headPart.out ?: 0).dp, side = 4.dp + (headPart.out ?: 0).dp,
                bottom = (headPart.out ?: 0).dp, shape = com.bamnat.app.ui.parts.PartMods.shapeOf(headPart, MaterialTheme.shapes.medium))
        ).partSpot("cal.head").decor(com.bamnat.core.recipe.Decor.HEAD, taps = com.bamnat.app.ui.decor.LocalDecorTap.current == null).padding(start = 4.dp, end = 4.dp, top = 8.dp)
            .then(with(com.bamnat.app.ui.parts.PartMods) { box(headPart, MaterialTheme.shapes.medium) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // [RVP-45] Whose calendar: its face (and its name where there is room), the list of calendars a tap away.
        chip?.let { c ->
            CalendarChip(c.current, name = room.name, onOpen = c.onOpenSwitcher)
            teamRow?.let { t ->
                if (room.team == HeadRoom.WORDS) CalendarFace(t, c.onPick)
                else if (room.team == HeadRoom.ICON) CalendarFaceOnly(t, c.onPick)
            }
            if (room.faces > 0) CalendarFaces(otherRows.filter { it !== teamRow || room.team == 0 }, room.faces, c.onPick)
            if (room.together && c.onTogether != null) TogetherChip(c.onTogether, shop = c.togetherShop, field = c.togetherField, words = true)
        }
        if (room.arrows && navPart.hide != true) IconButton(onClick = onPrev, modifier = with(com.bamnat.app.ui.parts.PartMods) { box(navPart.copy(ink = null), androidx.compose.foundation.shape.CircleShape) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (byWeek) "이전 주" else "이전 달", tint = navTint) }
        // [UX-06] The month and › take only what is left, so 오늘 and 도구 stay on screen instead of being pushed off it.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // [VIS-02] The month opens the month list: 48dp tall, with ▾ to show it can be tapped. [RVP-45] Without the arrows, TalkBack
            // still steps a month (or a week) from here.
            val prevLabel = if (byWeek) "이전 주" else "이전 달"
            val nextLabel = if (byWeek) "다음 주" else "다음 달"
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "달 고르기", role = Role.Button, onClick = onPickMonth)
                    .semantics {
                        if (!room.arrows) customActions = listOf(
                            androidx.compose.ui.semantics.CustomAccessibilityAction(prevLabel) { onPrev(); true },
                            androidx.compose.ui.semantics.CustomAccessibilityAction(nextLabel) { onNext(); true },
                        )
                    }
                    .padding(start = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // [J133-22] 1.33: a small phone shows this year's month without the year, and the title is never squeezed out by the
                // buttons around it. [RVP-45] HQ-22: never below 14sp either (it used to shrink to a few pixels).
                // [BLU-05] The small year takes the title's own letters (a little lighter), so it reads on the part's fill too (PC 2차).
                val yearInk = titleText.color.takeIf { titlePart.ink != null || titlePart.fill != null }?.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified }?.let { it.copy(alpha = it.alpha * 0.8f) }
                com.bamnat.app.ui.FitText(monthTitle(month, short = room.shortTitle, yearInk = yearInk), titleText,
                    Modifier.weight(1f, fill = false).partSpot("cal.head.title").then(with(com.bamnat.app.ui.parts.PartMods) { box(titlePart.copy(ink = null), MaterialTheme.shapes.small) }),
                    minSp = MONTH_TITLE_MIN_SP)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (room.arrows && navPart.hide != true) IconButton(onClick = onNext, modifier = with(com.bamnat.app.ui.parts.PartMods) { box(navPart.copy(ink = null), androidx.compose.foundation.shape.CircleShape) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, nextLabel, tint = navTint) }
        }
        // [RVH-42] The work's 바로 단추, before 오늘.
        if (quickRoom.round) HeadQuickRound(quick.first(), onQuick)
        else quick.take(quickShown).forEach { HeadQuickButton(it, onQuick) }
        val todayMod = with(com.bamnat.app.ui.parts.PartMods) { box(todayPart.copy(ink = null), MaterialTheme.shapes.extraLarge) }
        if (todayPart.hide == true) Unit
        else if (!room.todayWords) IconButton(onClick = onToday, enabled = !atToday, modifier = todayMod) { Icon(Icons.Filled.Today, "오늘") }
        // PC 확인 C15 1.36 (사진 위 '오늘' 2.1:1): on this month already the button is off, and its 38% grey vanished on a 꾸밈 사진; there it
        // keeps most of the ink (still lighter than when it can be pressed).
        else TextButton(onClick = onToday, enabled = !atToday, modifier = todayMod,
            colors = if (com.bamnat.app.ui.decor.LocalPhotoGround.current == null) androidx.compose.material3.ButtonDefaults.textButtonColors()
                // PC 확인 C16: the pressable 파랑 too stands on the photo (1.4:1 on a dark one before).
                else androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = com.bamnat.app.ui.decor.readableOnPhoto(MaterialTheme.colorScheme.primary),
                    // PC 확인 8d0cddf6 (80%로는 4.45:1·3.86:1): on a photo the off button keeps the full ink, like the month title beside it.
                    disabledContentColor = MaterialTheme.colorScheme.onSurface)) {
            Text("오늘", maxLines = 1, softWrap = false, style = with(com.bamnat.app.ui.parts.PartMods) { text(todayPart, androidx.compose.material3.LocalTextStyle.current) })
        }
        // [UX-03] 공유 where there is room: in words on a wide screen, its icon on a phone that has the room; else in 도구 (always there).
        chip?.onShare?.let { share ->
            when (room.share) {
                HeadRoom.WORDS -> ShareAction(share, Modifier.padding(start = 4.dp), chip.shareName)
                HeadRoom.ICON -> IconButton(onClick = share) { Icon(Icons.Filled.Share, chip.shareName) }
                else -> Unit
            }
        }
        // [RVP-43] HQ-49 The bell (알림함) beside 공유, always there; its number is what waits besides the band.
        // (사용자 11-13 00:35 KST 카드: '공유 옆에 따로', '나' 안이 아니에요.)
        bell?.let { NoticeBell(it) }
        // [CAL-23] A button with its name instead of ⋮: people did not know three dots held the tools.
        // [A11Y-03] Where the word does not fit, a round icon button takes its place, and TalkBack still reads its name.
        if (!room.toolsWords) {
            androidx.compose.material3.FilledTonalIconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.Apps, "도구", Modifier.size(20.dp))
            }
        } else {
            androidx.compose.material3.FilledTonalButton(
                onClick = { menu = true },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 40.dp).padding(end = 4.dp),
            ) {
                Icon(Icons.Outlined.Apps, null, Modifier.size(18.dp))
                Text(" 도구", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
            }
        }
        // [NAV2-02] 1.39 설정 and the tabs not in the bar, always (PC NAV2: hidden on a small phone, settings was hard to find).
        com.bamnat.app.ui.MeButton()
    }
    if (menu) {
        // [FIT-01] Every tool with its id in the fit table; [FIT-02] the ones this kind of work does not use wait under 더 보기.
        // [READ-11] A month put in at once, taken back here for a while after (its own screen may be closed).
        val recent = remember { com.bamnat.app.ui.RecentFill.current(calendarId) }
        // [NAV-03] 1.25 Z (U4): every tile keeps its place. A tool that has to wait (a received calendar on screen,
        // no shifts or order yet) stays, greyed with why; before, the tiles after it moved up, and a thumb used to
        // 화면 꾸미기 landed on 달력 보기 설정 and the settings tab. The ways out of the calendar are in a row of their own.
        val all = CalendarToolSlots.build(toolFit).map { slot ->
            val label = when (slot.id) {
                CalendarToolSlots.EDIT -> "‘$calendarName’ 고치기"
                CalendarToolSlots.SHARE -> com.bamnat.core.work.shareTitle(style) // [J133-77]
                else -> slot.label
            }
            CalendarTool(label, slot.icon(), id = slot.id, onClick = when (slot.id) {
                CalendarToolSlots.EDIT -> onEditCalendar
                CalendarToolSlots.TEAMS -> onOpenTeams
                CalendarToolSlots.LEADER -> ({ com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.LEADER_TEAMS)) })
                CalendarToolSlots.TYPE_MONTH -> onTypeMonth
                CalendarToolSlots.PUSH -> onPushToday
                CalendarToolSlots.SHARE -> onShare
                CalendarToolSlots.YEAR -> ({ com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.YEAR) })
                CalendarToolSlots.RECEIVE -> onReceive
                CalendarToolSlots.PATTERN -> onOpenPattern
                CalendarToolSlots.ADD -> onAddCalendar
                CalendarToolSlots.CUSTOMIZE -> onCustomize
                CalendarToolSlots.VIEW_SETTINGS -> ({ com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.CALENDAR) })
                else -> ({ com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "달력", help = true)) })
            }.takeIf { slot.enabled }, note = slot.note, away = slot.away)
        }
        val added = com.bamnat.app.ui.guide.rememberFitAdded(calendarId)
        val context = LocalContext.current
        val (tools, others) = all.partition { t -> t.id == null || FitTable.visible(FitSurface.CALENDAR_TOOLS, t.id, style, added) }
        val offered = all.filter { t -> t.id != null && FitTable.item(FitSurface.CALENDAR_TOOLS, t.id)?.fits(style) == false }
        CalendarToolsSheet(
            onDismiss = { menu = false },
            tools = tools.let { slots ->
                // [READ-11] 방금 넣은 것 되돌리기, after the tools so none of them moves (NAV-03).
                val at = slots.indexOfFirst { it.away }.takeIf { it >= 0 } ?: slots.size
                slots.toMutableList().apply {
                    // B 1.32 [CALX-04] 달력만 보기 in one tap, and back.
                    val only = context.appContainer.settingsRepository.current.calendarOnly
                    add(at, CalendarTool(if (only) "카드도 함께 보기" else "달력만 보기", Icons.Outlined.Apps, onClick = {
                        context.appContainer.settingsRepository.update { it.copy(calendarOnly = !only, viewAsked = true) }
                    }))
                    recent?.let { r -> add(at, CalendarTool("방금 넣은 것 되돌리기 (${r.text})", Icons.AutoMirrored.Filled.Undo, onClick = { com.bamnat.app.ui.RecentFill.undo(r) })) }
                    // [RVH-42] The work's 바로 단추 the head had no room for.
                    quick.drop(quickShown).forEach { q ->
                        add(at, CalendarTool(listOf(q.emoji, q.text).filter { it.isNotBlank() }.joinToString(" "), Icons.Outlined.TouchApp, onClick = { onQuick(q.id) }))
                    }
                    // [PLAZA-06] U25-나: the first setup again, a way out of the calendar, so last in that row and nothing moves
                    // (the main calendar only; an added one has its own 고치기).
                    if (onEditCalendar == null) add(CalendarTool("처음 설정 다시 받기", Icons.Filled.EventRepeat, away = true,
                        onClick = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.SETUP_AGAIN) }))
                }
            },
            others = offered.map { com.bamnat.app.ui.components.OtherRow(it.id!!, it.label, "이 달력의 일에는 잘 쓰지 않는 도구예요", it !in others) },
            onAdd = { id, on -> com.bamnat.app.ui.guide.setFitAdded(context, calendarId, FitSurface.CALENDAR_TOOLS, id, on) },
        )
    }
    }
}

/** 2.0.0 [RVP-45] 검토 HQ-22: the head's month is never drawn smaller than this, whatever the work's title or the room (조건 12). */
private const val MONTH_TITLE_MIN_SP = 14f

/** [CAL-52] 중-4: "2026년 10월" with the year smaller, so the month is what reads first and the line fits a phone. */
@Composable
internal fun monthTitle(month: YearMonth, short: Boolean = false, yearInk: androidx.compose.ui.graphics.Color? = null): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.72.em, color = yearInk ?: MaterialTheme.colorScheme.onSurfaceVariant))
    if (!short || month.year != Today.date().year) append(if (short) "${month.year % 100}년 " else "${month.year}년 ")
    pop()
    append("${month.monthValue}월")
}

/** [CAL-23] One tool of the calendar's tool sheet: a name people can read, not a hidden menu row. */
internal data class CalendarTool(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** [FIT-01] The tool's id in [FitTable] (CALENDAR_TOOLS), the same as its [CalendarToolSlots] id. */
    val id: String? = null,
    /** Null while the tool has to wait; the tile stays, greyed, with [note]. */
    val onClick: (() -> Unit)?,
    val note: String? = null,
    /** [NAV-03] Opens another tab (settings, help): shown in a row of its own under the tools. */
    val away: Boolean = false,
)

/** [NAV-03] Each slot's icon. */
private fun CalendarToolSlots.Slot.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (id) {
    CalendarToolSlots.EDIT -> Icons.Outlined.Edit
    CalendarToolSlots.TEAMS -> Icons.Outlined.Groups
    CalendarToolSlots.LEADER -> Icons.Outlined.Groups
    CalendarToolSlots.TYPE_MONTH -> Icons.Outlined.Keyboard
    CalendarToolSlots.PUSH -> Icons.Filled.Autorenew
    CalendarToolSlots.SHARE -> Icons.Filled.Share
    CalendarToolSlots.YEAR -> Icons.Outlined.CalendarViewMonth
    CalendarToolSlots.RECEIVE -> Icons.Outlined.GroupAdd
    CalendarToolSlots.PATTERN -> Icons.Filled.EventRepeat
    CalendarToolSlots.ADD -> Icons.Outlined.LibraryAdd
    CalendarToolSlots.CUSTOMIZE -> Icons.Outlined.Dashboard
    CalendarToolSlots.VIEW_SETTINGS -> Icons.Outlined.Tune
    else -> Icons.AutoMirrored.Outlined.HelpOutline
}

/** [CAL-23] The calendar's tools as big named tiles, three or four to a row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarToolsSheet(
    tools: List<CalendarTool>, onDismiss: () -> Unit,
    others: List<com.bamnat.app.ui.components.OtherRow> = emptyList(),
    onAdd: (String, Boolean) -> Unit = { _, _ -> },
) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            CalendarToolsGrid(tools, onPicked = onDismiss)
            // [FIT-02] What this kind of work does not use, put away and added back here.
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
                com.bamnat.app.ui.components.FitMore(others, onAdd, title = "이 달력에 안 맞아 숨긴 도구")
            }
        }
    }
}

@Composable
internal fun CalendarToolsGrid(tools: List<CalendarTool>, onPicked: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text("달력 도구", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth >= 480.dp) 4 else 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.filter { !it.away }.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tool ->
                            val click = tool.onClick
                            Surface(
                                onClick = { if (click != null) { onPicked(); click() } },
                                enabled = click != null,
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f).heightIn(min = 84.dp)
                                    .alpha(if (click != null) 1f else 0.5f),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(tool.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                                    if (click == null && tool.note != null) {
                                        Text(tool.note, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // [NAV-03] Ways out of the calendar, apart from its tools and said as such: they open another tab.
        val away = tools.filter { it.away }
        if (away.isNotEmpty()) {
            Text("다른 화면으로", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            for (tool in away) {
                val click = tool.onClick ?: continue
                Surface(
                    onClick = { onPicked(); click() },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).heightIn(min = 48.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(tool.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupResumeCard(d: com.bamnat.core.setup.SetupDraft, onResume: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("근무 설정을 마저 해 주세요", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (d.style == null) "어떻게 일하는지만 고르면 나머지는 하나씩 물어볼게요."
                else "${d.steps.size}단계 중 ${d.position}단계까지 했어요. 고른 답은 그대로 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = onResume) { Text("이어서 하기") }
        }
    }
}

/** 1.40 [SPREAD-37] "2026년 내 근무 결산이 나왔어요": a few of the year's numbers (never the money), 결산 보기 and 닫기. */
@Composable
internal fun YearRecapCard(recap: com.bamnat.core.work.YearRecap, onOpen: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = androidx.compose.ui.graphics.Color(0xFF1E2447),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("${recap.year}년 내 근무 결산이 나왔어요", style = MaterialTheme.typography.titleSmall,
                color = androidx.compose.ui.graphics.Color(0xFFF2C46D))
            Text(recap.line, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFFF6F1E7),
                modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFA9AFCB))) { Text("닫기") }
                TextButton(onClick = onOpen, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFF2C46D))) { Text("결산 보기") }
            }
        }
    }
}

/** [CAL-46] "이번 달 내 기록": the finished month in one line, shared as the month's picture, closed for good. */
@Composable
internal fun MonthRecapCard(recap: com.bamnat.core.work.MonthRecap, onShare: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("${recap.month.monthValue}월 내 기록", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(recap.line.substringAfter(": ") + " · ${HoursFormat.korean(recap.workMinutes)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("닫기") }
                TextButton(onClick = onShare) { Text("달력 사진 보내기") }
            }
        }
    }
}

/**
 * [ALRM-03] "알람 제안": what was seen and what one tap would change, with 아니요 (a month's rest) and 다시 보지
 * 않기. The buttons wrap at large text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmSuggestCard(s: com.bamnat.core.alarm.AlarmSuggest.Suggestion, onApply: () -> Unit, onNo: () -> Unit, onNever: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("알람 제안", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(s.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = 8.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
                TextButton(onClick = onNever) { Text("다시 보지 않기") }
                TextButton(onClick = onNo) { Text("아니요") }
                TextButton(onClick = onApply) { Text(if (s.bedtime) "알려 주세요" else "바꾸기") }
            }
        }
    }
}

/** [SETUP-02] The month's roster is not painted yet: one tap opens quick input to paint it. */
@Composable
private fun RosterCard(month: YearMonth, onPaint: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${month.monthValue}월 근무표를 칠해 주세요", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("근무를 고르고 날짜를 쓸어 칠하면 돼요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Button(onClick = onPaint) { Text("칠하기") }
        }
    }
}

/**
 * [CAL-01] The hours in one line under the calendar: the picked week and the three-month average
 * ([CAL-06] the month's hours are in the summary over the calendar). A number over its limit is marked in words and color; the week's own warning is the bar
 * beside it and the line under the day. A tap opens 근무·급여.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoursLine(
    calculator: WorkHoursCalculator,
    limits: com.bamnat.core.hours.HourLimits,
    selected: LocalDate,
    month: YearMonth,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    /** 2.0.0 [RVH-27] The tab it opens, as the bar names it for this kind of work. */
    tabName: String = "근무·급여",
) {
    val week = remember(calculator, selected) { calculator.week(selected) }
    // [CAL-32] I 리뷰 P9: a night shift over the week's edge is counted on the day it starts; say the other way once.
    val split = remember(calculator, selected) { calculator.weekSplitAtMidnight(selected) }
    val three = remember(calculator, month) { calculator.threeMonths(month) }
    val cal = LocalCalendarColors.current
    Surface(
        onClick = onOpenStats,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HoursPart("이 주", HoursFormat.korean(week.totalMinutes),
                    limits.weeklyLimitHours.takeIf { week.overLimit }, cal.warning)
                if (split != week.totalMinutes) {
                    Text("자정에 나눠 세면 ${HoursFormat.korean(split)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Its own limit (52), not the week's (64), so the number it is over is said.
                HoursPart("3개월 주평균", HoursFormat.korean(Math.round(three.averageWeeklyMinutes).toInt()),
                    limits.threeMonthAverageLimitHours.takeIf { three.overLimit }, cal.warning)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "$tabName 열기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "이 주 73시간 30분", and "· 64시간 넘음" in the warning color when it is over [overLimit] hours. */
@Composable
private fun HoursPart(label: String, value: String, overLimit: Int?, warning: Color) {
    // One text, so on a narrow phone with large text the "· 64시간 넘음" wraps under the hours instead of over them (1.24 통합 직접 테스트).
    val base = MaterialTheme.typography.titleSmall.merge(NumberStyle)
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            withStyle(MaterialTheme.typography.bodySmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label ") }
            withStyle(base.toSpanStyle().copy(color = if (overLimit != null) warning else MaterialTheme.colorScheme.onSurface)) { append(value) }
            if (overLimit != null) {
                withStyle(MaterialTheme.typography.labelMedium.toSpanStyle().copy(color = warning, fontWeight = FontWeight.Bold)) { append(" · ${overLimit}시간 넘음") }
            }
        },
        style = base,
    )
}

/** 2.0.0 [RVP-45] What keeps an alarm due this week from working: the band's words, its one button and how serious it is. */
internal class AlarmNote(
    val label: String,
    val text: String,
    val action: String,
    val onAction: () -> Unit,
    val tone: com.bamnat.app.ui.theme.Tone,
    val picture: com.bamnat.app.ui.components.PermissionResult? = null,
)

/**
 * [CAL-01] One line about what keeps an alarm due this week from working; the whole line fixes it. 2.0.0 [RVP-01] 검토 PC-55 (알림
 * 꺼짐 띠는 붉은 바탕, 다른 띠는 저마다 다른 모양): the one shape of a nudge, the meaning only in its icon's colour ([tone]: red where an
 * alarm will not work as it should, orange where it was turned off).
 */
@Composable
private fun AlarmWarningLine(
    text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier,
    /** 1.39 [FLOW-05] A permission asked here for the first time: what it gives, drawn small where the warning mark was. */
    picture: com.bamnat.app.ui.components.PermissionResult? = null,
    tone: com.bamnat.app.ui.theme.Tone = com.bamnat.app.ui.theme.Tone.Danger,
) {
    val mark = if (tone == com.bamnat.app.ui.theme.Tone.Plain) MaterialTheme.colorScheme.primary else tone.color()
    com.bamnat.app.ui.components.NudgeCard(
        text, modifier, tone = tone, action = action, onAction = onAction, onClick = onAction,
        lead = picture?.let { p -> { com.bamnat.app.ui.components.PermissionPicture(p, accent = mark, ink = MaterialTheme.colorScheme.inverseSurface, height = 36.dp) } },
    )
}

@Composable
private fun PaintBar(
    snapshot: ScheduleSnapshot,
    tool: PaintTool?,
    last: CalendarViewModel.PaintUndo?,
    onTool: (PaintTool?) -> Unit,
    onUndo: () -> Unit,
    mark: DayMark? = null,
    onMark: (DayMark?) -> Unit = {},
    focus: com.bamnat.core.work.CalendarFocus = com.bamnat.core.work.CalendarFocus.SHIFTS,
    onTypeMonth: (() -> Unit)? = null,
    /** [CAL-38] The extra-work stamp chosen, and the stamps to offer. */
    stamp: ExtraChoice? = null,
    stamps: List<ExtraChoice> = emptyList(),
    onStamp: (ExtraChoice?) -> Unit = {},
    /** [CAL-45] Below the hinge of a half-open fold: the chips wrap into a pad of big keys, not one scrolling row. */
    large: Boolean = false,
    /** 1.36 U20: the month's count, here while nothing is shown above the month. */
    summary: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth().padding(12.dp),
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            summary?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) }
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("빠른 입력", style = MaterialTheme.typography.titleSmall)
                    // [CAL-01] What to do now, or what the last drag did. [CAL-09] Two lines, so the range hint fits.
                    Text(
                        last?.text ?: when {
                            tool == PaintTool.Pick && mark == null -> "고르고 날짜를 누르거나 끌어서 칠하세요"
                            mark?.leave != null -> "근무일을 누르거나 끌면 ${mark.label}${Fmt.iga(mark.label)} 들어가요 · 길게 누르면 기간"
                            mark == DayMark.ALARM_OFF -> "누르거나 끌면 그날 알람이 꺼져요 · 길게 누르면 기간"
                            stamp != null -> "누르거나 끌면 ${stamp.name}${Fmt.obj(stamp.name)} 넣어요 · 다시 누르면 빼요"
                            else -> "누르거나 끌어서 칠해요 · 다시 누르면 취소 · 길게 누르면 기간"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (last != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (last?.undo != null) TextButton(onClick = onUndo) { Text("되돌리기") }
                IconButton(onClick = { onTool(null) }) { Icon(Icons.Filled.Close, "빠른 입력 끝내기") }
            }
            // [ROST-07] The whole month from a keypad, on its own line above the stamps and filled, so it is seen first
            // (리뷰 개선 가) without pushing the kinds of work along the row.
            onTypeMonth?.let { open ->
                androidx.compose.material3.FilledTonalButton(
                    onClick = open,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = if (large) 56.dp else 40.dp),
                ) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = null, Modifier.size(18.dp))
                    Text(" 이번 달 전부 넣기", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // 1.38 사용자 2026-11-06 (교대가 없는 유형에 주·야): kinds put away ('안 쓰는 종류') are not offered to paint with.
            val folded = com.bamnat.app.ui.shifts.foldedHere()
            // 2.0.0 [RVG-10] 검토 CAL-08 (PC 9차: 가게의 칠하기 키가 주간·오후·야간): a shop paints its own 영업 kinds, as its day sheet offers.
            val paintStyle = ExtraText.styleHere()
            val paintTypes = remember(snapshot, folded, paintStyle) {
                if (paintStyle == com.bamnat.core.work.WorkStyle.BUSINESS) dayTypes(snapshot, paintStyle, null, folded) else snapshot.shiftTypes.filter { it.id !in folded }
            }
            val keys: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                // The chips say what a tap on the calendar will do, for TalkBack.
                for (type in paintTypes) {
                    val t = PaintTool.Shift(type.id)
                    FilterChip(
                        selected = tool == t && mark == null && stamp == null,
                        onClick = { onTool(if (tool == t) PaintTool.Pick else t) },
                        label = { Text(type.name, maxLines = 1, softWrap = false) },
                        leadingIcon = { ShiftBadge(type, size = 18.dp, outlined = type.isOff) },
                        modifier = Modifier.then(if (large) Modifier.heightIn(min = 56.dp) else Modifier).semantics { contentDescription = "${type.name} 칠하기" },
                    )
                }
                if (snapshot.shiftTypes.none { it.isOff }) {
                    FilterChip(
                        selected = tool == PaintTool.Off && mark == null,
                        onClick = { onTool(if (tool == PaintTool.Off) PaintTool.Pick else PaintTool.Off) },
                        label = { Text("쉬는 날") },
                        modifier = Modifier.semantics { contentDescription = "쉬는 날 칠하기" },
                    )
                }
                // 1.37 시간 바퀴 PC LOW: a weekday calendar (일근·요일 근무) has no cycle to go back to; 되돌리기 and the
                // day sheet's '평소대로' undo a painted day there.
                // 2.0.0 [RVH-04] 검토 CAL-07: a calendar with no cycle at all (알바·현장·가게, or shifts painted by hand) has no
                // '근무 주기' either: the chip says what it does there (지우기), as the day sheet and long press already do.
                if (!snapshot.weekdayOnly) FilterChip(
                    selected = tool == PaintTool.Clear,
                    onClick = { onTool(if (tool == PaintTool.Clear) PaintTool.Pick else PaintTool.Clear) },
                    label = { Text(snapshot.backWord, maxLines = 1, softWrap = false) }, // 1.36 PC X7: never cut onto two lines
                    leadingIcon = { Icon(if (snapshot.hasPattern) Icons.Filled.Autorenew else Icons.Filled.Close, contentDescription = null) },
                    modifier = Modifier.semantics { contentDescription = if (snapshot.hasPattern) "${snapshot.backWord} 되돌리기" else "칠한 것 지우기" },
                )
                // [CAL-09] Leave and 알람 끄기 painted like shifts, for work kept in shifts.
                if (focus == com.bamnat.core.work.CalendarFocus.SHIFTS || snapshot.hasPattern) {
                    VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically))
                    // [RVG-10] An owner (가게·투자) has no 연차 of their own: only 알람 끄기 is painted.
                    val ownerPaint = paintStyle == com.bamnat.core.work.WorkStyle.BUSINESS || paintStyle == com.bamnat.core.work.WorkStyle.INVEST
                    for (m in DayMark.entries) {
                        if (ownerPaint && m.leave != null) continue
                        FilterChip(
                            selected = mark == m,
                            onClick = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                onMark(if (mark == m) null else m)
                            },
                            label = { Text(m.label, maxLines = 1, softWrap = false) },
                            leadingIcon = {
                                val leave = m.leave
                                if (leave != null) com.bamnat.app.ui.components.LeaveMark(leave, size = 18.dp)
                                else Icon(Icons.Filled.AlarmOff, contentDescription = null, Modifier.size(18.dp))
                            },
                            modifier = Modifier.semantics { contentDescription = "${m.label} 칠하기" },
                        )
                    }
                }
                // [CAL-38] Extra work stamped like shifts: 잔업 after the shift, 특근 at its hours, a 대근 on days off.
                if (stamps.isNotEmpty() && (focus == com.bamnat.core.work.CalendarFocus.SHIFTS || snapshot.hasPattern)) {
                    VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically))
                    for (c in stamps) {
                        FilterChip(
                            selected = stamp == c,
                            onClick = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                                onStamp(if (stamp == c) null else c)
                            },
                            label = { Text(c.label, maxLines = 1, softWrap = false) },
                            leadingIcon = { ExtraKindMark(c.kind, size = 18.dp) },
                            modifier = Modifier.then(if (large) Modifier.heightIn(min = 56.dp) else Modifier).semantics { contentDescription = "${c.name} 넣기" },
                        )
                    }
                }
            }
            if (large) {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { keys() }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { keys() }
            }
        }
    }
}

/**
 * 1.32 [WTYP-21] (근무유형 조사 6장 자영업, 오늘얼마 리뷰 "작년 같은 달을 보려면 한 달씩 넘겨야") The month in a screen's title
 * row: a tap opens 연·월 바로 이동, as the calendar's own title does.
 */
@Composable
internal fun MonthJumpTitle(month: YearMonth, onPick: (YearMonth) -> Unit, modifier: Modifier = Modifier,
                            style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium, suffix: String = "",
                            // 2.0.0 [RVK-35] (PC-126, 가계부·통계 머리) Large letters on a folded phone: '10월' ('25년 10월' another year), as the
                            // calendar's head does, so ‹ ›, 찾기, ⋮ and 나 keep their places (나 was pushed past the edge at 200%).
                            short: Boolean = LocalDensity.current.fontScale >= 1.3f && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400) {
    var open by remember { mutableStateOf(false) }
    val title = if (!short) Fmt.month(month) else if (month.year == Today.date().year) Fmt.monthShort(month) else "${month.year % 100}년 ${month.monthValue}월"
    Text(title + suffix, style = style, maxLines = 1, softWrap = false,
        modifier = modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "연·월 고르기", role = Role.Button) { open = true }
            .padding(horizontal = 4.dp, vertical = 6.dp))
    if (open) MonthPickerDialog(month, onDismiss = { open = false }, onPick = { open = false; onPick(it) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit,
    selected: LocalDate = initial.atDay(1),
    /** [CAL-06] A date far away, picked on a full calendar instead of month by month. */
    onPickDate: ((LocalDate) -> Unit)? = null,
) {
    var year by remember { mutableStateOf(initial.year) }
    var pickingDate by remember { mutableStateOf(false) }
    if (pickingDate && onPickDate != null) {
        val utc = java.time.ZoneOffset.UTC
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(utc).toInstant().toEpochMilli(),
        )
        com.bamnat.app.ui.components.DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPickDate(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) }
                    pickingDate = false
                }) { Text("이 날로 가기") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("취소") } },
        ) {
            // The title and the picked day in the app's own words, whatever the phone's language.
            androidx.compose.material3.DatePicker(
                state = state,
                title = { Text("갈 날짜 고르기", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
                headline = {
                    Text(
                        state.selectedDateMillis?.let { Fmt.dayTitle(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) } ?: "날짜를 눌러 주세요",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
            )
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 해") }
                Text("${year}년", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 해") }
            }
        },
        text = {
            // [CAL-42] I 리뷰 P27: three to a row at a big font, and the rows as tall as their words, so 200% shows
            // every month whole instead of a fixed 180dp box that scrolled and cut "10월".
            val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            val perRow = if (big) 3 else 4
            Column(
                Modifier.widthIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (row in (1..12).chunked(perRow)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in row) {
                        val ym = YearMonth.of(year, m)
                        val isCurrent = ym == initial
                        Surface(
                            onClick = { onPick(ym) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("${m}월", fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(Today.month()) }) { Text("이번 달") }
        },
        dismissButton = {
            Row {
                if (onPickDate != null) TextButton(onClick = { pickingDate = true }) { Text("날짜 고르기") }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

/**
 * [FEAT-17] This month's memos in short under the calendar, for people who plan by their notes:
 * the date, then the title or first line. Tapping one opens it.
 */
@Composable
private fun MonthMemoCard(
    month: YearMonth,
    memos: List<com.bamnat.core.memo.Memo>,
    /** [DAY-04] With memos under the dates, the card stays to say where the month's memos gather. */
    showEmpty: Boolean,
    onOpen: (Long, LocalDate?) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
    /** [EASE-24] 1.38: the kind of day on a date, shown beside it so a memo reads with its shift ("6일 목 [야] 도시락"). */
    shiftOn: (LocalDate) -> com.bamnat.core.model.ShiftType? = { null },
) {
    if (memos.isEmpty() && !showEmpty) return
    var all by remember(month) { mutableStateOf(false) }
    val shown = if (all) memos else memos.take(MEMO_PREVIEW_ROWS)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${month.monthValue}월 메모" + if (memos.isNotEmpty()) " ${memos.size}" else "",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onNew) { Text("메모 쓰기") }
            }
            if (memos.isEmpty()) {
                Text("이번 달 메모가 없어요. 날짜를 누르고 메모를 쓰면 여기 모여요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            for (m in shown) {
                val d = m.date!!
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpen(m.id, m.date) }.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${d.dayOfMonth}일 ${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN)}",
                        style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.labelWidth(56.dp),
                    )
                    // [EASE-24] The day's shift as its badge (its name for TalkBack), the same mark as on the calendar.
                    val shift = shiftOn(d)
                    if (shift != null) {
                        ShiftBadge(shift, size = 20.dp, outlined = shift.isOff,
                            modifier = Modifier.semantics { contentDescription = shift.name })
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        val title = m.title.ifBlank { m.preview }
                        Text(title.ifBlank { "빈 메모" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val rest = if (m.title.isNotBlank()) m.preview else ""
                        if (rest.isNotBlank()) Text(rest, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (m.isChecklist && m.itemCount > 0) {
                        Text("${m.checkedCount}/${m.itemCount}", style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (memos.size > MEMO_PREVIEW_ROWS) {
                androidx.compose.material3.TextButton(onClick = { all = !all }, modifier = Modifier.padding(start = 4.dp)) {
                    Text(if (all) "접기" else "${memos.size - MEMO_PREVIEW_ROWS}개 더 보기")
                }
            }
        }
    }
}

private const val MEMO_PREVIEW_ROWS = 4

/**
 * [SLP-01][MKT-07] The month's rest-gap signs under the hours summary: short rest between
 * shifts, a day shift soon after a night, backward rotation, long night runs. Tapping a row
 * picks that day. Shown only when there is something to say. [NURS-05][JOB-03] Its first line is the month's
 * nights (and night pay) and a 공무원's 시간외 against the 57-hour cap; [REST-01] '기준 바꾸기' opens the limits,
 * and each sign says where its limit comes from [REST-02].
 */
@Composable
private fun MonthRestCard(
    issues: List<com.bamnat.core.schedule.RestCheck.Issue>,
    nights: CalendarViewModel.MonthNights?,
    onPick: (LocalDate) -> Unit,
    onRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = nights?.nights ?: 0
    val overtime = nights?.overtime
    if (issues.isEmpty() && count == 0 && overtime == null) return
    var all by remember(issues) { mutableStateOf(false) }
    // [REST-02] Folded, the same sign over the month is one line ("연속 야간 3번 · 2·10·18일"); 근거 opens each day.
    val groups = remember(issues) { com.bamnat.core.schedule.RestCheck.group(issues) }
    val shown = if (all) issues.map { com.bamnat.core.schedule.RestCheck.Group(it.kind, it.pattern, listOf(it)) } else groups.take(REST_PREVIEW_ROWS)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (issues.isEmpty()) "근무표 점검" else "휴식 점검 ${issues.size}건 (참고)", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onRules) { Text("기준 바꾸기") }
            }
            if (count > 0 || overtime != null) {
                val parts = listOfNotNull(
                    count.takeIf { it > 0 }?.let { com.bamnat.app.ui.Fmt.hide(com.bamnat.core.pay.NightSummary.line(it, nights?.nightPay)) },
                    overtime?.let { "이달 시간외 ${it.text}" },
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                    color = if (overtime?.over == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                if (overtime?.over == true) Text("시간외는 한 달 ${com.bamnat.core.pay.PublicPayTable.MONTHLY_CAP}시간까지만 수당이 나와요 (공무원수당 등에 관한 규정 제15조)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            for (group in shown) {
                val issue = group.issues.first()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(group.date) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${group.date.dayOfMonth}일", style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.labelWidth(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.bodyMedium)
                        Text(group.text, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (all && issue.source.isNotEmpty()) Text("근거: ${issue.source}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (issues.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = { all = !all }, modifier = Modifier.padding(start = 4.dp)) {
                    Text(when {
                        all -> "접기"
                        groups.size > REST_PREVIEW_ROWS -> "${groups.size - REST_PREVIEW_ROWS}가지 더 · 날짜별로 보기 · 근거"
                        else -> "날짜별로 보기 · 근거"
                    })
                }
            }
        }
    }
}

private const val REST_PREVIEW_ROWS = 3

/** [CAL-17] The one-time tip on the first tap of a date, in the words of the kind of work. */
private fun com.bamnat.core.work.CalendarFocus.longPressTip(): String = "날짜를 길게 누르면 빠른 설정이 열려요. " + when (this) {
    com.bamnat.core.work.CalendarFocus.SHIFTS -> "근무·연차를 바로 바꿀 수 있어요"
    com.bamnat.core.work.CalendarFocus.JOBS -> "일감을 바로 적을 수 있어요"
    com.bamnat.core.work.CalendarFocus.SALES -> "매출을 바로 적을 수 있어요"
    com.bamnat.core.work.CalendarFocus.INVEST -> "매매를 바로 적을 수 있어요"
}

/**
 * [ALRM-20] A day just made a day off or leave had alarms: 끌까요? [J135-10] The shift's own alarms stopped with it and alarms
 * set by hand still ring; 끄기 leaves none, 그대로 울리기 keeps every one, closing changes nothing more.
 */
@Composable
private fun RestAlarmDialog(ask: CalendarViewModel.RestAlarmAsk, onAnswer: (turnOff: Boolean) -> Unit, onDismiss: () -> Unit) {
    val one = ask.days.singleOrNull()
    val times = ask.days.flatMap { d -> d.was.map { Fmt.time(it.triggerAt.toLocalTime()) } }.distinct().take(3).joinToString(", ")
    // 2.0.0 [RVIB-11] (PC-75, ★ 출근 알람) Whether the day had work before it became a day off, at a glance: 끄기 on a day that
    // was a 야간 is not the same answer as on a day that was already free.
    fun was(date: LocalDate) = ask.was[date]?.let { w -> w.name + (w.start?.let { " ${Fmt.time(it)}" } ?: "") }
    val worked = ask.days.mapNotNull { d -> was(d.date)?.let { "${Fmt.monthDay(d.date)} $it" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AlarmOff, null) },
        title = { Text("알람을 끌까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (one != null) "${Fmt.monthDay(one.date)}${was(one.date)?.let { "(원래 $it)" } ?: ""}은 쉬는 날이 됐어요. 이날 $times 알람을 어떻게 할까요?"
                    else "쉬는 날이 된 ${ask.days.size}일에 알람($times)이 있었어요. 어떻게 할까요?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (one == null && worked.isNotEmpty()) Text(
                    "원래 " + worked.take(3).joinToString(" · ") + if (worked.size > 3) " 외 ${worked.size - 3}일" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("끄면 그 날 알람이 울리지 않아요. 어느 쪽이든 바로 되돌릴 수 있고, 날짜를 눌러 알람에서 다시 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onAnswer(true) }) { Text("알람 끄기") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("그대로 울리기") } },
    )
}

/**
 * [CAL-19] "근무일이 아니에요": a working shift stamped on days off. 대근 keeps the roster and adds the
 * shift's hours as a 대근; the other answer makes them working days. Closing it writes nothing.
 */
@Composable
private fun CoverAskDialog(ask: CalendarViewModel.CoverAsk, word: String, onAnswer: (asCover: Boolean, keep: Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(false) }
    val name = ask.type.name
    // 2.0.0 [RVH-07] 검토 CAL-11: 알바 calls it 대타, as the bar and the day's chips do.
    val days = if (ask.offDays.size == 1) "${Fmt.monthDay(ask.offDays.first())}은 근무일이 아니에요" else "고른 날 중 ${ask.offDays.size}일은 근무일이 아니에요"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WarningAmber, null) },
        title = { Text(days) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$name${Fmt.obj(name)} $word${Fmt.ro(word)} 넣을까요?", style = MaterialTheme.typography.bodyLarge)
                Text("$word${Fmt.ro(word)} 넣으면 근무표는 쉬는 날 그대로 두고, $name 시간이 $word(휴일 근무)로 따로 잡혀 수당에 들어가요. " +
                    "근무표가 바뀐 거라면 ‘근무일로 바꾸기’를 고르세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { keep = !keep },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("이번 빠른 입력 동안 다시 묻지 않기", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { Button(onClick = { onAnswer(true, keep) }) { Text("$word${Fmt.ro(word)}") } },
        dismissButton = { TextButton(onClick = { onAnswer(false, keep) }) { Text("근무일로 바꾸기") } },
    )
}

/**
 * [PERF-11] 1.28 AO: whether this run's first calendar month is on screen. Until then the calendar makes only its
 * month and what sits above it; the months either side and the cards under the month (day card, hours, rest check,
 * memos) follow one frame later. Once per process: coming back to the tab later draws everything at once.
 */
internal object FirstMonth {
    val settled = androidx.compose.runtime.mutableStateOf(false)
}


/** B 1.32 [CALX-01] The month a swipe lands on, big and centred while the pager moves (and a moment after). */
@Composable
internal fun SwipeMonthBadge(pager: androidx.compose.foundation.pager.PagerState, baseMonth: java.time.YearMonth, modifier: Modifier = Modifier) {
    val moving = pager.isScrollInProgress
    val target = pageToMonth(baseMonth, if (moving) pager.targetPage else pager.currentPage)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(moving, target) {
        if (moving) shown = true else { kotlinx.coroutines.delay(300); shown = false }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown, modifier = modifier,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
    ) {
        // [J133-25] 1.33 (실사용 테스트 "달 넘김 상자가 격자 가운데 1초 가려요"): a slim pill over the first row, not a box over the days.
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        ) {
            Text((if (target.year != com.bamnat.core.time.Today.date().year) "${target.year}년 " else "") + "${target.monthValue}월",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
    }
}

/**
 * 1.36 U7·U19: while [hold], the content keeps the height it was last laid out at (cut to it, or left with room), so what
 * changes inside does not move what is below it.
 */
@Composable
internal fun Modifier.holdHeight(hold: Boolean): Modifier {
    val last = androidx.compose.runtime.remember { IntArray(1) { -1 } }
    return this.then(if (hold) Modifier.clipToBounds() else Modifier).layout { m, c ->
        val p = m.measure(c)
        if (!hold || last[0] < 0) last[0] = p.height
        val h = if (hold) last[0] else p.height
        layout(p.width, h) { p.place(0, 0) }
    }
}

/** 1.37 시간 바퀴 PC LOW: every pattern is a week (일근·요일 근무), so there is no '근무 주기' to speak of. */
internal val ScheduleSnapshot.weekdayOnly: Boolean
    get() = patterns.isNotEmpty() && patterns.all { it is com.bamnat.core.model.WorkPattern.Weekly }

/** What putting a painted day back is called: the cycle for shift work, the usual weekdays for 일근, 지우기 without a pattern. */
internal val ScheduleSnapshot.backWord: String
    get() = when {
        !hasPattern -> "지우기"
        weekdayOnly -> "평소대로"
        else -> "근무 주기대로"
    }

/** 2.0.0 [RVH-05] 검토 CAL-13: what the cycle is called on screen: 평소 on a weekday calendar, else 근무 주기 (never 패턴). */
internal val ScheduleSnapshot.cycleWord: String
    get() = if (weekdayOnly) "평소" else "근무 주기"

/** [RVH-04] 검토 CAL-07: what the bar says after a day is put back, in the words of [backWord]. */
internal val ScheduleSnapshot.backDone: String
    get() = when {
        !hasPattern -> "지웠어요"
        weekdayOnly -> "평소대로 되돌렸어요"
        else -> "근무 주기대로 되돌렸어요"
    }

/** [EASE-03] Cards above the month that wait for the day after the first setup. */
private val FIRST_DAY_WAITS = setOf(
    "statsMore", "crashAsk", "review", "holiday", "alarmSuggest", "recap", "plazaShare", "publicCycle",
    "plazaRoom", "inviteNudge", "proMoment", "backupNudge", "journal", "discover", "shareHint",
)

/** 2.0.0 [RVP-45] The empty calendar's first step, the band while it is up (the 빠른 입력 button waits). */
private const val START_NOTICE = "startCard"

/** [RVP-43] U13b-02: how the 알림함 names an opening window that waits its turn: title, line, what a tap does. */
private fun waitingWords(ask: com.bamnat.core.guide.OpenAsk): Triple<String, String, String> = when (ask) {
    com.bamnat.core.guide.OpenAsk.NEW_CONSENT -> Triple("새 기능 동의", "새로 생긴 기능을 쓰려면 동의가 필요해요", "보기")
    com.bamnat.core.guide.OpenAsk.TRIAL_CONSENT -> Triple("체험 끝", "체험이 곧 끝나요. 계속 쓸지 골라 주세요", "고르기")
    com.bamnat.core.guide.OpenAsk.WHATS_NEW -> Triple("새로 생긴 것", "업데이트로 새로 생긴 것이 있어요", "보기")
    com.bamnat.core.guide.OpenAsk.THEME_EXPIRY -> Triple("테마 기간 끝", "입은 테마의 기간이 끝났어요", "보기")
    com.bamnat.core.guide.OpenAsk.UPDATE_READY -> Triple("새 버전", "새 버전을 받았어요. 다시 시작하면 바로 열려요", "다시 시작")
    com.bamnat.core.guide.OpenAsk.THEME_UPDATE -> Triple("테마 새 판", "입은 테마에 새 판이 나왔어요", "보기")
    com.bamnat.core.guide.OpenAsk.TEXT_SIZE -> Triple("글자 크기", "읽기 편한 글자 크기를 골라 보세요", "고르기")
    com.bamnat.core.guide.OpenAsk.LAUNCH_THANKS -> Triple("고마워요", "정식 출시 전부터 함께해 주셔서 고마워요", "보기")
}
