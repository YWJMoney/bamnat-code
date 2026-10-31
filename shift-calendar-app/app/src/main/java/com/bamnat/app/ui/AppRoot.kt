package com.bamnat.app.ui

import com.bamnat.app.HomeRequests
import com.bamnat.app.NoticeLinks
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import android.os.Build
import androidx.compose.foundation.layout.Column
import com.bamnat.app.alarm.AlarmService
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.bamnat.app.appContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.bamnat.app.ui.company.companyCalendarOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bamnat.core.work.words
import com.bamnat.app.AppContainer
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.ui.calendar.CalendarScreen
import com.bamnat.app.ui.calendar.CalendarViewModel
import com.bamnat.app.ui.calendar.CalendarsScreen
import com.bamnat.app.ui.calendar.CalendarsViewModel
import com.bamnat.app.ui.calendar.rememberAddCalendar
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.components.AdSlot
import com.bamnat.app.ui.diag.DiagnosticsScreen
import com.bamnat.app.ui.diag.DiagnosticsViewModel
import com.bamnat.core.diag.DiagEvents
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.app.ui.memo.MemoEditorScreen
import com.bamnat.app.ui.memo.MemoEditorViewModel
import com.bamnat.app.ui.memo.MemoListScreen
import com.bamnat.app.ui.memo.MemoListViewModel
import com.bamnat.app.ui.memo.MemoTrashScreen
import com.bamnat.app.ui.memo.MemoTrashViewModel
import com.bamnat.app.ui.settings.ExtraKindsScreen
import com.bamnat.app.ui.settings.ExtraKindsViewModel
import com.bamnat.app.ui.settings.InquiryScreen
import com.bamnat.app.ui.settings.InquiryViewModel
import com.bamnat.app.ui.settings.SettingsScreen
import com.bamnat.app.ui.settings.SettingsGo
import com.bamnat.app.ui.settings.SettingsLink
import com.bamnat.app.ui.settings.SettingsLinks
import androidx.compose.material3.BadgedBox
import com.bamnat.app.ui.settings.SettingsViewModel
import com.bamnat.app.ui.shifts.PatternScreen
import com.bamnat.app.ui.shifts.ShiftTypesScreen
import com.bamnat.app.ui.shifts.ShiftsViewModel
import com.bamnat.app.ui.stats.StatsScreen
import com.bamnat.app.ui.stats.StatsViewModel
import com.bamnat.app.ui.money.MoneyScreen
import com.bamnat.app.ui.money.MoneyViewModel
import com.bamnat.app.ui.work.WorkStyleScreen
import com.bamnat.app.ui.work.changedMessage
import com.bamnat.app.ui.setup.SetupHost
import com.bamnat.app.ui.onboarding.ConsentPages
import com.bamnat.app.ui.onboarding.NewConsentPrompt
import com.bamnat.app.ui.setup.SetupViewModel
import com.bamnat.app.ui.setup.SetupPaySheet
import com.bamnat.app.ui.company.CompanyBoardScreen
import com.bamnat.app.ui.company.CompanyBoardViewModel
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyFindScreen
import com.bamnat.app.ui.company.CompanyFindViewModel
import com.bamnat.app.ui.company.CompanyNudgeCards
import com.bamnat.app.ui.company.CompanyNudgeViewModel
import com.bamnat.app.ui.company.CompanyPayScreen
import com.bamnat.app.ui.company.CompanyPayViewModel
import com.bamnat.app.ui.company.CompanyReportScreen
import com.bamnat.app.ui.company.CompanyReportViewModel
import com.bamnat.app.ui.company.CompanySentScreen
import com.bamnat.app.ui.company.CompanySettingsRows
import com.bamnat.app.ui.company.FixPlace
import com.bamnat.app.ui.company.InviteSheet
import androidx.compose.runtime.rememberCoroutineScope
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.guide.Place
import com.bamnat.app.ui.guide.settingsPage
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.bamnat.core.time.Today

internal enum class TopLevel(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CALENDAR("calendar", "달력", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    /** [WORK-06] Income, spending and what is left, for every kind of work. */
    MONEY("money", "가계부", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    MEMOS("memos", "메모", Icons.AutoMirrored.Outlined.StickyNote2, Icons.AutoMirrored.Filled.StickyNote2),
    /** [NAV-01] Hours and pay of the month together; field and shop work, paid in 돈, keep 통계. */
    STATS("stats", "근무·급여", Icons.Outlined.Insights, Icons.Filled.Insights),
    /** 1.35 [RCX-29] 광장 and its 나눔 판, a tab of their own (they were behind 설정 and the pay screen). */
    // 1.35 [HUB-01] (사용자 2026-10-30 18:46): 마을, 광장 with 테마·장터(나눔 판)·공방(스튜디오) in it.
    PLAZA("plaza", com.bamnat.app.ui.hub.HUB_NAME, Icons.Outlined.Forum, Icons.Filled.Forum),
    SETTINGS("settings", "설정", Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    /** [TYPE-02] The hours-and-money tab is named for the work of the calendar on screen. */
    // U 1.32 [UX132-01] (사용자 2026-10-28 "돈 메뉴 이름을 가계부와 통계를 포괄하는 말로"): 가계부, and 장부 for a shop or field calendar.
    fun labelFor(style: WorkStyle?): String = when (this) {
        STATS -> style.words().tab
        MONEY -> com.bamnat.core.work.moneyTab(style)
        else -> label
    }

    companion object {
        /** [NAV-05] The tabs in the order kept in settings ([com.bamnat.core.layout.TabOrder] keys are the routes). */
        fun ordered(saved: List<String>): List<TopLevel> =
            com.bamnat.core.layout.TabOrder.of(saved, entries.map { it.route }).map { k -> entries.first { it.route == k } }
    }
}

/** [MON-07] Where the banner may go: under 근무·급여 and 돈 only ([com.bamnat.core.tier.AdRules.banner]). */
private fun TopLevel?.adPlace(): com.bamnat.core.tier.AdRules.Place = when (this) {
    TopLevel.MONEY -> com.bamnat.core.tier.AdRules.Place.MONEY
    TopLevel.STATS -> com.bamnat.core.tier.AdRules.Place.STATS
    TopLevel.CALENDAR -> com.bamnat.core.tier.AdRules.Place.CALENDAR
    TopLevel.MEMOS -> com.bamnat.core.tier.AdRules.Place.MEMOS
    TopLevel.SETTINGS -> com.bamnat.core.tier.AdRules.Place.SETTINGS
    TopLevel.PLAZA, null -> com.bamnat.core.tier.AdRules.Place.OTHER
}

private object Routes {
    // [MCAL-01] Shifts and the order are edited for one calendar: the main one unless one is named.
    const val SHIFT_TYPES = "shift_types?calendar={calendar}&edit={edit}"
    const val PATTERN = "pattern?weekdays={weekdays}&setup={setup}&calendar={calendar}"
    // [CALS-02] The kind of work of one calendar: the main one unless one is named.
    const val WORK_STYLE = "work_style?calendar={calendar}"
    fun workStyle(calendar: String = PersonalCalendar.MAIN) = "work_style?calendar=$calendar"
    // [CALS-01] One calendar's own page: name, colour, kind of work and everything set for it.
    const val CALENDAR_EDIT = "calendar_edit/{id}"
    fun calendarEdit(id: String) = "calendar_edit/$id"
    // [TYPE-03] A shop calendar's hours and regular days off.
    const val SHOP_DAYS = "shop_days?calendar={calendar}"
    fun shopDays(calendar: String = PersonalCalendar.MAIN) = "shop_days?calendar=$calendar"

    /** [DAILY-08] [edit] opens that shift type's editor at once (the day's shift, from its sheet). */
    fun shiftTypes(calendar: String = PersonalCalendar.MAIN, edit: Long = 0L) = "shift_types?calendar=$calendar&edit=$edit"
    fun pattern(weekdays: Boolean = false, setup: Boolean = false, calendar: String = PersonalCalendar.MAIN) =
        "pattern?weekdays=$weekdays&setup=$setup&calendar=$calendar"

    // [MCAL-02][MCAL-04] Setting up a new calendar, and the calendar list.
    const val CALENDAR_SETUP = "calendar_setup/{id}"
    const val CALENDARS = "calendars"
    fun calendarSetup(id: String) = "calendar_setup/$id"
    const val MEMO_TRASH = "memo_trash"
    const val DIAGNOSTICS = "diagnostics?testing={testing}"

    fun diagnostics(testing: Boolean) = "diagnostics?testing=$testing"
    /** [MCAL-06] The pay of one of the user's calendars; without one, the main calendar's. */
    const val PAY = "pay?calendar={calendar}&focus={focus}"
    // U 1.32 [UX132-02] [focus] is a [com.bamnat.app.ui.pay.PayFocus] key: the tab and value the screen opens on.
    fun pay(calendar: String = PersonalCalendar.MAIN, focus: String? = null) = "pay?calendar=$calendar" + (focus?.let { "&focus=$it" } ?: "")
    /** [LEAVE-06] One calendar's 연차 원장; without one, the main calendar's. */
    const val LEAVE = "leave?calendar={calendar}"
    fun leave(calendar: String = PersonalCalendar.MAIN) = "leave?calendar=$calendar"
    /** [MCAL-06] How extra work is paid, in one calendar's pay; without one, the main calendar's. */
    const val EXTRA_KINDS = "extra_kinds?calendar={calendar}"
    fun extraKinds(calendar: String = PersonalCalendar.MAIN) = "extra_kinds?calendar=$calendar"
    const val MEMO = "memo/{id}?date={date}&checklist={checklist}&notebook={notebook}"

    /** [notebook]: opened from the memo tab, so a new memo goes into the notebook shown there. */
    fun memo(id: Long, date: LocalDate? = null, checklist: Boolean = false, notebook: Boolean = false) =
        "memo/$id?date=${date?.toString() ?: ""}&checklist=$checklist&notebook=$notebook"

    // [COMM-01][COMM-02] The company rules server: find a company, share a setup, follow what was sent.
    // [CALI-03] 1.30 BB: each with the calendar it is for ("" = the calendar on screen, [companyCalendarOf]).
    const val COMPANY_FIND = "company_find?setup={setup}&weekdays={weekdays}&calendar={calendar}"
    const val COMPANY = "company/{id}?setup={setup}&weekdays={weekdays}&calendar={calendar}"
    const val COMPANY_REPORT = "company_report?calendar={calendar}"
    /** [PLAZA-06] 처음 설정 다시 받기. */
    const val SETUP_AGAIN = "setup_again"
    const val COMPANY_SENT = "company_sent?calendar={calendar}"
    // [COMM-07] 회사 규칙 광장: every company's rule sets, to vote on, talk over and use.
    const val COMPANY_BOARD = "company_board?calendar={calendar}"
    const val PLAZA_RATES = "plaza_rates"
    /** [RCP-21..38] 1.34 나눔 판: the list of one calendar's kind of work, one recipe, 만들기 and 설정 › 나눔 판. */
    const val RECIPES = "recipes?calendar={calendar}&kind={kind}&code={code}"
    /** [HUB-02] [kind] opens on one kind (RecipeClient.KINDS, "" for all); [code] opens with 코드로 받기 asked. */
    fun recipes(calendar: String = PersonalCalendar.MAIN, kind: String = "", code: Boolean = false) = "recipes?calendar=$calendar&kind=$kind&code=$code"
    const val RECIPE = "recipe/{code}?calendar={calendar}"
    fun recipe(code: String, calendar: String = PersonalCalendar.MAIN) = "recipe/$code?calendar=$calendar"
    const val RECIPE_MAKE = "recipe_make?calendar={calendar}&draft={draft}&kind={kind}&code={code}"
    fun recipeMake(calendar: String = PersonalCalendar.MAIN, draft: Long = 0, kind: String = "", code: String = "") =
        "recipe_make?calendar=$calendar&draft=$draft&kind=$kind&code=$code"
    const val RECIPE_MINE = "recipe_mine"
    /** [STU-10] 1.35 스튜디오; [start] "mine", "plain" or "theme:<key>". */
    const val STUDIO = "studio?start={start}"
    fun studio(start: String = "mine") = "studio?start=${android.net.Uri.encode(start)}"
    /** [RCX-05] 1.35 a maker's page; [RCX-04] the 요청 판 of a calendar's kind of work. */
    const val RECIPE_MAKER = "recipe_maker/{id}"
    fun recipeMaker(id: String) = "recipe_maker/$id"
    const val RECIPE_REQUESTS = "recipe_requests?calendar={calendar}"
    fun recipeRequests(calendar: String = PersonalCalendar.MAIN) = "recipe_requests?calendar=$calendar"
    /** [PLAZA-32] 1.27 AI: the rule plaza of every company, past a company's 정보 광장. */
    const val COMPANY_BOARD_ALL = "company_board_all"
    /** [RCP-34] 1.34: 교대 광장·일근 광장, the plaza of the calendar's kind of work itself. */
    const val STYLE_PLAZA = "style_plaza?calendar={calendar}"
    /** [ADMIN-02] 설정 > 문의하기. */
    const val INQUIRY = "inquiry"
    /** [SHR-14] 우리 조 모아 보기: links put together under a 6-letter code. */
    const val SHARE_GROUP = "share_group"

    // [SLP-16][RECV-02][IMPORT-01] The sleep log, money owed, and bringing in the phone's calendar.
    const val SLEEP = "sleep"
    const val OWED = "owed"
    const val CALENDAR_IMPORT = "calendar_import"
    /** [COMN-01] 모든 달력 합쳐 보기. */
    const val COMBINED = "combined"
    // [SHFT-06][SHFT-07][DAYW-06][DAYW-07] Shift and day work's own tools, for the calendar on screen.
    const val DUTY_ROSTER = "duty_roster"
    /** [READ-10] 한 달 근무 넣기 on one month: the calendar's 이번 달 전부 넣기 (1.25 순번 371). */
    const val DUTY_ROSTER_MONTH = "duty_roster_month/{month}"
    fun dutyRoster(month: java.time.YearMonth) = "duty_roster_month/$month"
    const val HEALTH_REPORT = "health_report"
    const val TEAMS = "teams"
    const val YEAR = "year"
    const val ATTENDANCE_IMPORT = "attendance_import"
    const val COMMUTE = "commute"
    /** [GUIDE-05] 설정 › 기능 켜고 끄기. */
    const val FEATURES = "features"
    /** [MON-04] 프로: what it adds, the plans, passes and the admin link. */
    const val PRO = "pro"
    /** [ONBOARD-04] 추천 기능 한 번에 켜기, from settings and the 1.22 update notice. */
    const val RECOMMENDED = "recommended"
    // [EASY-08][OBS-02] 1.23 묶음 N.
    const val PROFILE = "profile"
    const val JOURNAL = "journal"

    fun companyFind(setup: Boolean = false, weekdays: Boolean = false, calendar: String = "") =
        "company_find?setup=$setup&weekdays=$weekdays&calendar=$calendar"
    fun company(id: String, setup: Boolean, weekdays: Boolean, calendar: String = "") =
        "company/$id?setup=$setup&weekdays=$weekdays&calendar=$calendar"
    fun companyBoard(calendar: String = "") = "company_board?calendar=$calendar"
    fun stylePlaza(calendar: String = "") = "style_plaza?calendar=$calendar"
    fun companyReport(calendar: String = "") = "company_report?calendar=$calendar"
    fun companySent(calendar: String = "") = "company_sent?calendar=$calendar"
}

@Composable
fun AppRoot(windowSizeClass: WindowSizeClass, container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = TopLevel.entries.firstOrNull { it.route == route }
    val showNav = topLevel != null || route == null
    // Fold unfolded, tablets and landscape get a navigation rail and two-pane calendar.
    val wide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val snackbar = remember { SnackbarHostState() }

    // [SETUP-01] First launch asks how the user works and sets it all up before the calendar. Phones
    // that already have a pattern were set up before this question existed, so they are shift workers
    // and are not asked; a setup on its way (or being saved) is never taken for one of those.
    val settingsNow by container.settingsRepository.settings.collectAsStateWithLifecycle()
    // [CALS-02] The calendar on screen and its kind of work, which names the tabs and opens its own settings.
    val activeCalendarId by container.calendars.active.collectAsStateWithLifecycle()
    val calendarList by container.calendars.calendars.collectAsStateWithLifecycle()
    val activeStyle = calendarList.firstOrNull { it.id == activeCalendarId }?.takeIf { !it.isMain }?.style ?: settingsNow.workStyle
    val setupDraft by container.setupDrafts.draft.collectAsStateWithLifecycle()
    val needsSetup by produceState<Boolean?>(if (settingsNow.workStyle != null) false else null, settingsNow.workStyle, setupDraft == null) {
        value = when {
            settingsNow.workStyle != null -> {
                // Set some other way meanwhile (a backup restored in settings): the setup left is not needed.
                if (setupDraft?.committing == false) container.setupDrafts.clear()
                false
            }
            setupDraft == null && container.scheduleRepository.patterns.first().isNotEmpty() -> {
                container.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
                false
            }
            else -> true
        }
    }
    // [ONBOARD-01] A new phone sees what the app may ask for, and why, before the first setup.
    val consentSeen by container.consentStore.seen.collectAsStateWithLifecycle()
    // [EASY-01] 우선 둘러볼게요: the app opens on its example calendar; the first write asks for the setup.
    val browse by container.browse.state.collectAsStateWithLifecycle()
    val rootScope = rememberCoroutineScope()
    var browseFirst by rememberSaveable { mutableStateOf(false) }
    val startMine: (Boolean) -> Unit = { keep -> rootScope.launch { container.example.startMine(keep) } }
    // [SETUP-15] "무슨 일 하세요?" before the consent screen: its answer ("" when skipped) shapes that screen and starts the setup.
    var firstStyleKey by rememberSaveable { mutableStateOf<String?>(null) }
    when (needsSetup) {
        null -> return
        // "나중에" opens the calendar with a card to go on from where the user stopped.
        true -> if (setupDraft?.later != true && !browse.browsing) {
            // [NAV-03] 1.25 Z (U4): a help link tapped during the setup is not followed later, out of the blue, once it is done.
            LaunchedEffect(Unit) { SettingsLinks.target.collect { it?.let(SettingsLinks::consume) } }
            if (consentSeen == null && setupDraft == null) {
                val key = firstStyleKey
                if (key == null) {
                    // [NAV-04] 1.25 Z 후속: the very first screen closes only on a second back, as the calendar does.
                    val context = LocalContext.current
                    val exit = remember { ExitGuard() }
                    androidx.activity.compose.BackHandler { exit.press(context) }
                    val shown by container.settingsRepository.settings.collectAsStateWithLifecycle()
                    // [R136-01] 1.36: this screen shows the size chips at its top, so going on from it answers the size too;
                    // the 1.32 size dialog is not shown again after the first save (it was, over the pay sheet, on a phone set up again).
                    val sizeSeen = { container.settingsRepository.update { it.copy(textAsked = true) } }
                    com.bamnat.app.ui.onboarding.FirstStyleScreen(onPick = { sizeSeen(); firstStyleKey = it.key }, onSkip = { sizeSeen(); firstStyleKey = "" },
                        textStep = shown.textStep, onTextStep = { step -> container.settingsRepository.update { it.copy(textStep = step, textAsked = true) } })
                    return
                }
                val first = com.bamnat.app.ui.onboarding.FirstStyle.of(key)
                androidx.activity.compose.BackHandler { firstStyleKey = null }
                ConsentPages(container, onDone = {}, style = first?.style, onBrowse = {
                    // With the work known, 우선 둘러볼게요 opens its example at once; skipped, the setup asks which.
                    if (first == null) browseFirst = true
                    else { firstStyleKey = ""; rootScope.launch { container.example.browse(first.style) } }
                })
                return
            }
            SetupHost(appViewModel { SetupViewModel(it) },
                onBrowse = { style -> browseFirst = false; rootScope.launch { container.example.browse(style) } }, browseFirst = browseFirst,
                firstStyle = com.bamnat.app.ui.onboarding.FirstStyle.of(firstStyleKey)?.let { it.style to it.roster },
                onFirstStyleUsed = { firstStyleKey = "" })
            return
        }
        false -> Unit
    }

    // Which screens get used decides what is worth building next [MEAS-03].
    LaunchedEffect(route) {
        route?.let { container.telemetry.event(DiagEvents.SCREEN, it.substringBefore('/').substringBefore('?')) }
        // [GUIDE-04] A screen that holds a feature ticks it in the guide; kept on this phone only.
        route?.let { com.bamnat.app.ui.guide.GuideUse.screen(com.bamnat.app.data.GuideStore.get(container.appContext), it.substringBefore('/').substringBefore('?')) }
    }

    // [NAV-05] 1.26 AD: the tabs in the user's order (D82); the first is home: the app opens there, back ends there.
    val tabEdit = remember { TabEdit() }
    val savedTabs = remember(settingsNow.tabOrder) { TopLevel.ordered(settingsNow.tabOrder) }
    val home = savedTabs.first()
    val tabs = if (tabEdit.editing) tabEdit.order.map { k -> TopLevel.entries.first { it.route == k } } else savedTabs

    // Opening the app (again) always lands on the first tab (the calendar unless the user moved another first).
    val homeRequests by HomeRequests.count.collectAsStateWithLifecycle()
    LaunchedEffect(homeRequests) {
        // Kept per process with the count: a screen rebuilt (a fold, a restore) does not take one twice or miss one.
        if (homeRequests != HomeRequests.handled) {
            HomeRequests.handled = homeRequests
            // Only from another tab: an editor the user left open (pay setup, a pattern) keeps their input.
            // A tapped notice says where to go instead [SLP-15].
            val current = nav.currentBackStackEntry?.destination?.route
            val homeRoute = TopLevel.ordered(container.settingsRepository.current.tabOrder).first().route
            if (NoticeLinks.target.value == null && current != homeRoute && TopLevel.entries.any { it.route == current }) {
                TabHistory.clear()
                nav.navigate(homeRoute) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
            // [NAV-01] Away long enough to count as opening the app again: this month, like a fresh start.
            if (NoticeLinks.target.value == null && TopLevel.entries.any { it.route == current }) {
                container.shownMonth.show(Today.month())
            }
        }
    }

    // [NAV-08] Back to the app after the month turned: the month on screen follows today when it was today's.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { container.shownMonth.followToday() }

    // [IMPORT-02] When the user asked for it, new events in the chosen calendars are offered on opening the app.
    LaunchedEffect(homeRequests) {
        // [PERF-19] 1.28 AO: the phone calendar read waits for the first month (at most 3 s, another home tab
        // never draws one), so it does not share the disk with it.
        kotlinx.coroutines.withTimeoutOrNull(FIRST_MONTH_WAIT_MILLIS) {
            androidx.compose.runtime.snapshotFlow { com.bamnat.app.ui.calendar.FirstMonth.settled.value }.first { it }
        }
        val count = runCatching { container.calendarImporter.countNew(Today.date()) }.getOrDefault(0)
        if (count > 0 && snackbar.showSnackbar("캘린더에 새 일정 ${count}개가 있어요", actionLabel = "가져오기",
                duration = androidx.compose.material3.SnackbarDuration.Long) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            NoticeLinks.open(NoticeLinks.Target.CALENDAR_IMPORT)
        }
    }

    // [SLP-15] A tapped notice opens the screen it is about. The calendar finds today itself.
    val noticeTarget by NoticeLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(noticeTarget) {
        val target = noticeTarget ?: return@LaunchedEffect
        when (target) {
            // Over an editor the calendar goes on top, so the editor's input is still there after back.
            // [SHR-12] The calendar opens its share sheet itself.
            NoticeLinks.Target.TODAY, NoticeLinks.Target.SHARE, NoticeLinks.Target.RECEIVE, NoticeLinks.Target.DAY -> {
                val current = nav.currentBackStackEntry?.destination?.route
                when {
                    current == TopLevel.CALENDAR.route -> Unit
                    TopLevel.entries.any { it.route == current } -> nav.navigateTopLevel(TopLevel.CALENDAR.route)
                    else -> nav.navigate(TopLevel.CALENDAR.route) { launchSingleTop = true }
                }
            }
            NoticeLinks.Target.MONEY -> { nav.navigateTopLevel(TopLevel.MONEY.route); NoticeLinks.consume(target) }
            // [INV-08] An investor notice opens its calendar's 투자 tab, which opens the trade itself.
            NoticeLinks.Target.INVEST, NoticeLinks.Target.INVEST_TRADE -> {
                // [MCAL-03] No calendar named is the main one; its 투자 tab takes the notice only once it is shown.
                val cal = NoticeLinks.calendar.value ?: com.bamnat.app.data.PersonalCalendar.MAIN
                val gone = cal != com.bamnat.app.data.PersonalCalendar.MAIN && container.calendars.get(cal) == null
                container.calendars.setActive(cal)
                nav.navigateTopLevel(TopLevel.STATS.route)
                // The trade sheet waits for the 투자 tab to read it; a plain notice, or one of a calendar since removed, is done here.
                if (target == NoticeLinks.Target.INVEST || gone) NoticeLinks.consume(target)
            }
            // [LEDG-16] The money tab takes this one itself, to open 자동 기록.
            NoticeLinks.Target.AUTO_RECORD -> nav.navigateTopLevel(TopLevel.MONEY.route)
            // [TAX-02] Likewise, to open 세금·실수령.
            NoticeLinks.Target.TAX -> nav.navigateTopLevel(TopLevel.MONEY.route)
            NoticeLinks.Target.MEMOS -> { nav.navigateTopLevel(TopLevel.MEMOS.route); NoticeLinks.consume(target) }
            NoticeLinks.Target.LEAVE -> { nav.navigate(Routes.leave()) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.SLEEP -> { nav.navigate(Routes.SLEEP) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.OWED -> { nav.navigate(Routes.OWED) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.CALENDAR_IMPORT -> { nav.navigate(Routes.CALENDAR_IMPORT) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.DUTY_ROSTER -> { nav.navigate(Routes.DUTY_ROSTER) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.HEALTH_REPORT -> { nav.navigate(Routes.HEALTH_REPORT) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.TEAMS -> { nav.navigate(Routes.TEAMS) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.YEAR -> { nav.navigate(Routes.YEAR) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.ATTENDANCE_IMPORT -> { nav.navigate(Routes.ATTENDANCE_IMPORT) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.COMMUTE -> { nav.navigate(Routes.COMMUTE) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.PRO -> { nav.navigate(Routes.PRO) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.PLAZA -> { nav.navigateTopLevel(TopLevel.PLAZA.route); NoticeLinks.consume(target) }
            NoticeLinks.Target.COMPANY_FIND -> { nav.navigate(Routes.companyFind()) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.COMPANY_REPORT -> { nav.navigate(Routes.companyReport()) { launchSingleTop = true }; NoticeLinks.consume(target) }
            // [CALI-04] 1.30 BB: a company notice's 급여 is the pay of the calendar on screen (내 달력 when it is the only one).
            NoticeLinks.Target.PAY -> { nav.navigate(Routes.pay(companyCalendarOf(container, null))) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.PAY_GAP -> {
                NoticeLinks.calendar.value?.takeIf { container.calendars.get(it) != null }?.let(container.calendars::setActive)
                com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(Place.STATS, "pay.gap"))
                NoticeLinks.consume(target)
            }
            NoticeLinks.Target.SETUP_AGAIN -> { nav.navigate(Routes.SETUP_AGAIN) { launchSingleTop = true }; NoticeLinks.consume(target) }
            // [INQ-05] 밤낮 소식's answer line opens 내 문의 (문의하기 opens there when an answer is new).
            NoticeLinks.Target.INQUIRY -> { nav.navigate(Routes.INQUIRY) { launchSingleTop = true }; NoticeLinks.consume(target) }
            // 1.32.1 [STAB-13] Not 알람 점검: a 앱 멈춤 문의 whose 점검 기록 starts with the stop.
            NoticeLinks.Target.STOP_REPORT -> {
                com.bamnat.app.ui.grow.GrowAsk.feedback(com.bamnat.app.ui.grow.STOP_ABOUT, kind = com.bamnat.app.company.InquiryKind.BUG, withCheck = true)
                NoticeLinks.consume(target)
            }
            NoticeLinks.Target.GROUP -> { nav.navigate(Routes.SHARE_GROUP) { launchSingleTop = true }; NoticeLinks.consume(target) }
            NoticeLinks.Target.PHONE_CALENDAR -> {
                com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, com.bamnat.app.ui.settings.PHONE_CALENDAR_ROW)
                NoticeLinks.consume(target)
            }
            // [SRV-09] Shown by the update's own dialog below, which takes the target.
            NoticeLinks.Target.WHATS_NEW -> Unit
            NoticeLinks.Target.BACKUP -> {
                com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, com.bamnat.app.ui.settings.AUTO_BACKUP_ROW)
                NoticeLinks.consume(target)
            }
            // [SET-01] Settings takes the target itself, to open on the alarm's state from wherever it was.
            NoticeLinks.Target.ALARM_CHECK -> {
                val current = nav.currentBackStackEntry?.destination?.route
                when {
                    current == TopLevel.SETTINGS.route -> Unit
                    TopLevel.entries.any { it.route == current } -> nav.navigateTopLevel(TopLevel.SETTINGS.route)
                    else -> nav.navigate(TopLevel.SETTINGS.route) { launchSingleTop = true }
                }
            }
        }
    }

    // [UX-04] A help answer, a search result or a screen's own "설정" opens the setting or screen it names.
    // A group of settings opens over an inner screen, so back returns there; a screen opens on top.
    // [CAL-47] A file opened from outside: 설정 > 데이터, whose screen reads it and asks before changing anything.
    val incomingFile by com.bamnat.app.IncomingFiles.file.collectAsStateWithLifecycle()
    LaunchedEffect(incomingFile) {
        if (incomingFile != null) SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA)
    }
    // [ROST-09] A roster shared from the gallery or a messenger: 한 달 근무 넣기, which reads it.
    val sharedRoster by com.bamnat.app.IncomingRoster.shared.collectAsStateWithLifecycle()
    // [INVA-01] AR 1.29: a photo shared on another kind of calendar while an investor calendar exists, asked where it goes.
    var shotToPlace by remember { mutableStateOf<Pair<android.net.Uri, com.bamnat.app.data.PersonalCalendar>?>(null) }
    var shotIsRoster by remember { mutableStateOf(false) }
    LaunchedEffect(sharedRoster) {
        // [INV-40] On an investor calendar a shared screenshot is a 체결 내역 capture for its 투자 tab; one shared while
        // 한 달 근무 넣기 (U, ROST-09) is open stays that screen's.
        val rosterOpen = nav.currentBackStackEntry?.destination?.route in setOf(Routes.DUTY_ROSTER, Routes.DUTY_ROSTER_MONTH)
        val shot = sharedRoster as? com.bamnat.app.IncomingRoster.Shared.Photo
        if (shot != null && activeStyle == WorkStyle.INVEST && !rosterOpen) {
            com.bamnat.app.IncomingRoster.take()
            com.bamnat.app.invest.IncomingFills.put(shot.uri)
            NoticeLinks.open(NoticeLinks.Target.INVEST, activeCalendarId)
            return@LaunchedEffect
        }
        val investor = calendarList.firstOrNull { !it.isExample && container.calendars.styleOf(it.id, settingsNow.workStyle) == WorkStyle.INVEST }
        if (shot != null && investor != null && !rosterOpen && !shotIsRoster) {
            com.bamnat.app.IncomingRoster.take()
            shotToPlace = shot.uri to investor
            return@LaunchedEffect
        }
        shotIsRoster = false
        if (sharedRoster != null && !rosterOpen) {
            nav.navigate(Routes.DUTY_ROSTER) { launchSingleTop = true }
        }
    }
    shotToPlace?.let { (uri, investor) ->
        com.bamnat.app.ui.invest.SharedShotChoice(
            investName = investor.name,
            onInvest = {
                shotToPlace = null
                com.bamnat.app.invest.IncomingFills.put(uri)
                NoticeLinks.open(NoticeLinks.Target.INVEST, investor.id)
            },
            onRoster = {
                shotToPlace = null
                shotIsRoster = true
                com.bamnat.app.IncomingRoster.put(com.bamnat.app.IncomingRoster.Shared.Photo(uri))
            },
            onDismiss = { shotToPlace = null },
        )
    }
    val settingsLink by SettingsLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(settingsLink) {
        when (val link = settingsLink ?: return@LaunchedEffect) {
            is SettingsLink.Page, SettingsLink.Search -> {
                val current = nav.currentBackStackEntry?.destination?.route
                when {
                    current == TopLevel.SETTINGS.route -> Unit
                    TopLevel.entries.any { it.route == current } -> nav.navigateTopLevel(TopLevel.SETTINGS.route)
                    else -> nav.navigate(TopLevel.SETTINGS.route) { launchSingleTop = true }
                }
            }
            is SettingsLink.Go -> {
                SettingsLinks.consume(link)
                // [J133-77] 근무 주기 of the calendar on screen: a shop's is its 영업일·정기휴무.
                if (link.go == SettingsGo.PATTERN) nav.openPattern(container, companyCalendarOf(container, null))
                else link.go.route()?.let { r -> nav.navigate(r) { launchSingleTop = true } }
            }
        }
    }
    // [J133-77] A 자영업 calendar just set up opens on its 영업일·정기휴무, once the setup screen is gone.
    LaunchedEffect(Unit) {
        container.pendingShopDays.collect { id ->
            if (id == null) return@collect
            // The kind of work is saved a moment after the setup closes: wait for it, a few seconds at most.
            val saved = kotlinx.coroutines.withTimeoutOrNull(3_000) {
                while (container.calendars.styleOf(id, container.settingsRepository.current.workStyle) != WorkStyle.BUSINESS) kotlinx.coroutines.delay(100)
                true
            }
            container.pendingShopDays.value = null
            if (saved == true) { kotlinx.coroutines.delay(300); nav.openPattern(container, id) }
        }
    }
    // [GUIDE-01] The checklist, a tip, the help search or 기능 켜고 끄기 opens a feature where it lives,
    // for the calendar on screen.
    val featureLink by com.bamnat.app.ui.guide.FeatureLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(featureLink) {
        val link = featureLink ?: return@LaunchedEffect
        com.bamnat.app.ui.guide.FeatureLinks.consume(link)
        nav.openFeature(container, link)
    }
    // [GUIDE-06] A feature turned off from its ⋮: where it comes back, one tap away.
    LaunchedEffect(Unit) {
        com.bamnat.app.ui.guide.FeatureLinks.turnedOff.collect { name ->
            val result = snackbar.showSnackbar("‘$name’ 꺼졌어요. 설정 › 기능 켜고 끄기에서 다시 켤 수 있어요",
                actionLabel = "바로 가기", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) nav.navigate(Routes.FEATURES) { launchSingleTop = true }
        }
    }
    // [UX-05] A new answer to the user's question shows on the 설정 tab from the moment the app opens.
    val inquiryUnread by container.inquiryStore.unread.collectAsStateWithLifecycle()
    LaunchedEffect(homeRequests) {
        if (!container.inquiryApi.enabled || !container.inquiryStore.asked) return@LaunchedEffect
        runCatching { container.inquiryApi.mine(container.companyStore.device) }.onSuccess {
            container.inquiryStore.update(it)
            // [J133-24] A question still waiting is looked at every few hours, so its answer is told at once.
            if (it.any { q -> q.waiting } && container.settingsRepository.current.plazaNews) com.bamnat.app.company.InquiryReplyJob.schedule(container.appContext)
        }
    }

    // [GUIDE-08] Once after an update: what it brought, each with a way to open it.
    val whatsNew = remember {
        val ctx = container.appContext
        val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        // [J133-53] A phone with no setup yet (reinstalled, data cleared) has nothing new to compare against.
        val fresh = info == null || info.firstInstallTime == info.lastUpdateTime || container.settingsRepository.current.workStyle == null
        mutableStateOf(com.bamnat.app.data.GuideStore.get(ctx).whatsNew(com.bamnat.app.BuildConfig.VERSION_CODE, fresh))
    }
    // [ONBOARD-04] A phone set up before 1.22 keeps its own values; the update notice offers the rest once.
    val recommendOff = remember {
        val ctx = container.appContext
        val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
        val on = com.bamnat.app.data.RecommendedSettings.on(container.settingsRepository.current)
        if (com.bamnat.core.consent.RecommendedPlan.offerOnce(fresh, container.consentStore.recommendOffered, on))
            com.bamnat.core.consent.RecommendedPlan.offNow(on).size else 0
    }
    // [SRV-09] A notice's 새 기능 보기 button: this version's lines again, from wherever.
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.WHATS_NEW) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.WHATS_NEW)
        whatsNew.value = com.bamnat.core.guide.WhatsNew.releases.filter { it.versionCode <= com.bamnat.app.BuildConfig.VERSION_CODE }.take(1)
    }
    if (whatsNew.value.isNotEmpty()) {
        val closeNew = {
            com.bamnat.app.data.GuideStore.get(container.appContext).whatsNewSeen(com.bamnat.app.BuildConfig.VERSION_CODE)
            if (recommendOff > 0) container.consentStore.markRecommendOffered()
            whatsNew.value = emptyList()
        }
        com.bamnat.app.ui.guide.WhatsNewDialog(
            releases = whatsNew.value,
            recommendOff = recommendOff,
            onRecommend = { closeNew(); SettingsLinks.go(SettingsGo.RECOMMENDED) },
            onOpen = { id -> closeNew(); com.bamnat.core.guide.FeatureRegistry.get(id)?.let(com.bamnat.app.ui.guide.FeatureLinks::open) },
            onAsk = { closeNew(); com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "업데이트")) },
            onDismiss = closeNew,
        )
    } else {
        // [MON-06][MON-10] One at a time, after what the update brought.
        com.bamnat.app.ui.pro.TrialConsentPrompt(container)
        // [FREE-10] 1.35 테마 기간: asked when the app opens (never over an alarm, which has its own activity).
        com.bamnat.app.ui.recipe.ThemeExpiryAsk()
        com.bamnat.app.ui.recipe.ThemeUpdateAsk()
        com.bamnat.app.ui.pro.LaunchThanks(container)
    }

    // [SHR-02] A tapped shared calendar link opens on the calendar. Over an editor it goes on top,
    // so the editor's input is still there after back.
    val openingLink by container.received.opening.collectAsStateWithLifecycle()
    LaunchedEffect(openingLink) {
        if (openingLink == null) return@LaunchedEffect
        val current = nav.currentBackStackEntry?.destination?.route
        when {
            current == TopLevel.CALENDAR.route -> Unit
            TopLevel.entries.any { it.route == current } -> nav.navigateTopLevel(TopLevel.CALENDAR.route)
            else -> nav.navigate(TopLevel.CALENDAR.route) { launchSingleTop = true }
        }
    }

    // [RCP-22] 1.34: a recipe link (bamnat://r/CODE) tapped outside the app, or a code pasted: the recipe opens on top.
    val openingRecipe by com.bamnat.app.recipe.RecipeLinks.opening.collectAsStateWithLifecycle()
    LaunchedEffect(openingRecipe) {
        val code = openingRecipe ?: return@LaunchedEffect
        com.bamnat.app.recipe.RecipeLinks.opened()
        nav.navigate(Routes.recipe(code)) { launchSingleTop = true }
    }

    val sharing by com.bamnat.app.recipe.RecipeLinks.make.collectAsStateWithLifecycle()
    LaunchedEffect(sharing) {
        val kind = sharing ?: return@LaunchedEffect
        com.bamnat.app.recipe.RecipeLinks.shared()
        nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.START_FEATURE + kind.key)) { launchSingleTop = true }
    }

    // AG 1.27 [MON-12] A tap on a locked 프로 feature: 프로, the rewarded ad, or 친구 초대 (ProOffer).
    com.bamnat.app.ui.pro.ProOfferHost(container)

    // [PLAZA-07] 결정 D79: the one-time ask to share the work order and pay way; yes opens the company's 광장.
    // [PLAZA-34] 1.27 AI: each change asks with what changed; 저절로 나누기 says it in one line with 되돌리기.
    com.bamnat.app.ui.company.AutoShareHost(container, snackbar = snackbar,
        onOpen = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false)) { launchSingleTop = true } })
    // [PLAZA-14] 1.27 AH: "이 정보를 다른 사람이 사용해도 될까요?" after a cycle picked from a company search.
    com.bamnat.app.ui.company.LeadShareHost(container) { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false)) { launchSingleTop = true } }

    // Undo for memo deletion, wherever the memo was deleted from.
    val lastDeleted by container.memoRepository.lastDeleted.collectAsStateWithLifecycle()
    LaunchedEffect(lastDeleted) {
        val deleted = lastDeleted ?: return@LaunchedEffect
        val title = deleted.label
        val result = snackbar.showSnackbar(
            message = when {
                deleted.memos.size > 1 -> "메모 ${deleted.memos.size}개를 휴지통으로 옮겼어요"
                title.isBlank() -> "메모를 휴지통으로 옮겼어요"
                else -> "‘$title’ 메모를 휴지통으로 옮겼어요"
            },
            actionLabel = "되돌리기",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            container.appScope.launch { container.memoRepository.undoDelete() }
        } else {
            container.memoRepository.consumeLastDeleted()
        }
    }

    // [UNDO-01][UNDO-02][UNDO-03] A kind of work changed, records cleared or a calendar removed: 되돌리기 at
    // once, wherever it was done, and 설정 › 데이터 › 최근 지운 기록 for 7 days after.
    val styleExtras = remember { mutableStateOf<StyleExtras?>(null) }
    LaunchedEffect(Unit) {
        container.recordsTrash.offers.collect { offer ->
            val result = snackbar.showSnackbar(offer.message, actionLabel = "되돌리기", duration = SnackbarDuration.Long)
            // Read once the snackbar is answered: the change sets it right after it is offered.
            val extras = styleExtras.value?.takeIf { it.entryId == offer.entry.id }
            if (extras != null) styleExtras.value = null
            if (result == SnackbarResult.ActionPerformed) {
                val ok = undoOffer(container, offer.entry.id, extras)
                // [UNDO-01] Its next step closes too.
                if (ok) extras?.opened?.let { nav.popFrom(it) }
                snackbar.showSnackbar(if (ok) "되돌렸어요" else "되돌리지 못했어요. 설정 › 데이터 › 최근 지운 기록에서 다시 해 주세요")
            }
        }
    }

    val navigateTop: (TopLevel) -> Unit = { dest -> nav.navigateTopLevel(dest.route) }
    // [NAV-05] The tab the app opens on: home when the app starts, kept through a turn of the screen with the back stack.
    val startTab = rememberSaveable { home.route }

    // [NAV-02] 1.25 Z (U4): back goes where the user was. From a tab reached from another tab, that tab, as it was
    // left (Android's default went to the calendar from every tab); from the calendar itself, a second back within
    // two seconds closes the app, so one stray back does not. Screens and sheets inside take back first.
    // [NAV-05] 1.26 AD: "the calendar" is the first tab ([home]), whichever the user put there, even when it was moved
    // first in this run (the bottom of the back stack is then still the tab the app opened on, [start]).
    // One callback, added again after every move so it comes after the NavHost's own back (added once, at the
    // start), which would otherwise pop straight to the calendar; screens composed with the move add theirs later
    // and still go first.
    val context = LocalContext.current
    val exitGuard = remember { ExitGuard() }
    val tabBack = remember { TabBackCallback() }
    // On every tab; what to do is decided when back comes, from the back stack as it is then (it is not state, and
    // a tab brought back with its screens is still settling when this is drawn).
    tabBack.isEnabled = topLevel != null
    tabBack.onBack = onBack@{
        val previousRoute = nav.previousBackStackEntry?.destination?.route
        val current = TopLevel.entries.firstOrNull { it.route == nav.currentBackStackEntry?.destination?.route }
        val start = nav.graph.findStartDestination().route
        when {
            // A tab put over an inner screen goes back to that screen, as anything else that is the NavHost's.
            current == null || (previousRoute != null && previousRoute != start) -> return@onBack false
            current == home -> { if (exitGuard.press(context)) TabHistory.clear() }
            TabHistory.previous() != null -> TabHistory.back()?.let { prev -> nav.navigateTopLevel(prev, record = false) }
            // Right over home: the NavHost pops to it.
            previousRoute == home.route -> return@onBack false
            // Home was moved first in this run, so it is not under this tab: it is brought up as a tab switch.
            else -> nav.navigateTopLevel(home.route, record = false)
        }
        true
    }
    val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(dispatcher, lifecycleOwner, backStack?.id) {
        tabBack.remove()
        tabBack.dispatcher = dispatcher
        dispatcher?.addCallback(lifecycleOwner, tabBack)
        onDispose { tabBack.remove() }
    }

    // [NAV-06] 1.26 AD: a long press on a tab moves the tabs; a tap anywhere else, back, or leaving the tabs saves.
    val saveTabs: () -> Unit = {
        if (tabEdit.editing) {
            val made = tabEdit.finish()
            if (made != savedTabs.map { it.route }) {
                container.settingsRepository.update { it.copy(tabOrder = com.bamnat.core.layout.TabOrder.toSave(made)) }
            }
        }
    }
    if (tabEdit.editing) androidx.activity.compose.BackHandler { saveTabs() }
    LaunchedEffect(route, showNav) { if (!showNav || topLevel == null) saveTabs() }
    val tabNames: (String) -> String = { k -> TopLevel.entries.first { it.route == k }.labelFor(activeStyle) }
    val tabsShown: () -> List<String> = { tabs.map { it.route } }
    val onTab: (TopLevel) -> Unit = { dest -> if (!tabEdit.editing) navigateTop(dest) }

    // [DAILY-09] Half open like a laptop (Flex): the calendar splits at the hinge.
    val tabletop = rememberTabletop()
    androidx.compose.runtime.CompositionLocalProvider(LocalTabletop provides tabletop) {
        Row(Modifier.fillMaxSize()) {
            if (wide && showNav) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEachIndexed { index, dest ->
                        androidx.compose.runtime.key(dest.route) {
                            val selected = dest == topLevel
                            val label = dest.labelFor(activeStyle)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onTab(dest) },
                                icon = { WigglingIcon(tabEdit.editing, index) { TabIcon(dest, selected, if (dest == TopLevel.SETTINGS) inquiryUnread else 0) } },
                                label = { TabLabel(label) },
                                modifier = tabEditModifier(tabEdit, dest.route, index, tabs.size, label, horizontal = false, tabsShown, tabNames, saveTabs),
                                // [VIS-03] The chosen tab in the same blue as every other chosen thing.
                                colors = com.bamnat.app.ui.components.Selection.navRailItemColors(),
                            )
                        }
                    }
                }
            }
            // [FREE-05] 1.35 화면 색·배경: the screen on show and its tab bar in that screen's look.
            com.bamnat.app.ui.theme.ScreenSkin(com.bamnat.app.ui.theme.screenKeyOf(route, activeStyle), Modifier.weight(1f), bars = true) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbar) },
                // Inner screens with their own top bar handle insets themselves.
                contentWindowInsets = if (showNav) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!wide && showNav) {
                        Column {
                            // [NAV-06] While the tabs are moved, the line about it takes the ad's place.
                            if (tabEdit.editing) TabEditHint(tabEdit.said, saveTabs) else AdSlot(topLevel.adPlace())
                            NavigationBar {
                                tabs.forEachIndexed { index, dest ->
                                    androidx.compose.runtime.key(dest.route) {
                                        val selected = dest == topLevel
                                        val label = dest.labelFor(activeStyle)
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { onTab(dest) },
                                            icon = { WigglingIcon(tabEdit.editing, index) { TabIcon(dest, selected, if (dest == TopLevel.SETTINGS) inquiryUnread else 0) } },
                                            label = { TabLabel(label) },
                                            modifier = tabEditModifier(tabEdit, dest.route, index, tabs.size, label, horizontal = true, tabsShown, tabNames, saveTabs),
                                            colors = com.bamnat.app.ui.components.Selection.navBarItemColors(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    Column(Modifier.fillMaxSize()) {
                        if (activeCalendarId == com.bamnat.app.data.PersonalCalendar.EXAMPLE) {
                            com.bamnat.app.ui.guide.ExampleBand(onStart = { startMine(false) })
                        }
                        RingingBanner()
                        AlarmNoticeAsk()
                        AppNavHost(
                            nav = nav,
                            container = container,
                            wide = wide,
                            snackbar = snackbar,
                            styleExtras = styleExtras,
                            start = startTab,
                            modifier = Modifier.weight(1f),
                        )
                        if (wide && showNav) AdSlot(topLevel.adPlace())
                    }
                    // [NAV-06] Over the screen while the tabs are moved: a tap here saves, and does nothing else. Beside the
                    // rail the line about it lies over the bottom of the screen, which keeps its size.
                    if (tabEdit.editing) {
                        TabEditCatcher(saveTabs, Modifier.matchParentSize())
                        if (wide) TabEditHint(tabEdit.said, saveTabs, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            }
        }
    }

    // [ONBOARD-01] A phone set up before the consent pages is asked only about what is new since.
    NewConsentPrompt(container)

    // [EASY-02] A write on the example calendar asks here, over whichever screen began it.
    com.bamnat.app.ui.guide.WriteGateCard(container, start = startMine)
    LaunchedEffect(route, activeCalendarId) {
        val kind = route?.let(com.bamnat.app.ui.guide.WriteGate::kindOf) ?: return@LaunchedEffect
        com.bamnat.app.ui.guide.WriteGate.request(container, kind) {}
    }

    // [SETUP-01] Right after the first setup, once: the pay comes next for work paid by the hour or month.
    val justSaved by container.firstSetup.saved.collectAsStateWithLifecycle()
    val saved = justSaved
    if (saved != null) {
        val payProfile by container.payStore.profile.collectAsStateWithLifecycle()
        // [PAYF-05] 1.29 AP: 근무주기 → 정보 제공 → 급여, one after the other: the one question about sharing the cycle first
        // (LeadShare), the pay after it is answered, never two sheets over each other.
        val leadAsking by container.leadShare.step.collectAsStateWithLifecycle()
        if (saved.style?.usesPayEngine != true || payProfile != null) {
            LaunchedEffect(saved) {
                container.firstSetup.consumeSaved()
                // [FIN130-07] A coworkers' setup with its pay on a pay already set up: its way waits for the 기본급 on the pay
                // screen, which opens next (the cycle is in already).
                if (saved.style?.usesPayEngine == true && com.bamnat.app.company.PendingWays.of(container.companyOf(PersonalCalendar.MAIN)).pending.value != null) {
                    nav.navigate(Routes.pay()) { launchSingleTop = true }
                }
            }
        } else if (leadAsking == null) {
            // [PAYF-02][PAYF-05] 1.29 AP: the company named in the setup is asked for while the user reads this, so the pay's
            // first question lists its pay way at once.
            val prefetch = rememberCoroutineScope()
            LaunchedEffect(saved) {
                com.bamnat.app.ui.company.PayFindSearch.prefetch(container, prefetch, container.companyOf(PersonalCalendar.MAIN).find.known())
            }
            SetupPaySheet(
                companyPay = container.companyOf(PersonalCalendar.MAIN).store.pendingPay != null,
                onNow = {
                    container.firstSetup.consumeSaved()
                    nav.navigate(Routes.pay()) { launchSingleTop = true }
                },
                onLater = { container.firstSetup.consumeSaved() },
            )
        }
    }
}

/** [EASY-05] The features of the pay screen, for the questions asked above it. */
private val PAY_ASKS = setOf("pay.calc", "pay.report", "pay.gap", "pay.whatIf", "pay.compLeave", "hourly.takeHome", "hourly.weeks", "leave.ledger")

/** [UNDO-01] A kind-of-work change's own extras, for its 되돌리기: the 시급 types it added and the next step it opened (a route pattern). */
internal class StyleExtras(val entryId: String, val calendar: String, val addedTypes: List<Long>, val opened: String?)

/** [UNDO-01] Adds what [style] brings to calendar [calendar] (시급 types for hourly work); the ids added, for 되돌리기. */
internal suspend fun addStyleTypes(container: AppContainer, calendar: String, style: WorkStyle): List<Long> {
    if (style != WorkStyle.HOURLY) return emptyList()
    val schedule = container.calendars.repository(calendar)
    val typesBefore = schedule.shiftTypes.first().map { it.id }.toSet()
    schedule.addHourlyTypes()
    return schedule.shiftTypes.first().map { it.id }.filter { it !in typesBefore }
}

/** [UNDO-01] 되돌리기 of trash entry [entryId]; with a kind-of-work change's [extras] the 시급 types it added go too. */
internal suspend fun undoOffer(container: AppContainer, entryId: String, extras: StyleExtras?): Boolean {
    val ok = container.recordsTrash.restore(entryId)
    if (ok && extras != null && extras.entryId == entryId) {
        val schedule = container.calendars.repository(extras.calendar)
        extras.addedTypes.forEach { schedule.deleteShiftType(it) }
    }
    return ok
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    container: AppContainer,
    wide: Boolean,
    snackbar: SnackbarHostState,
    styleExtras: androidx.compose.runtime.MutableState<StyleExtras?>,
    start: String,
    modifier: Modifier = Modifier,
) {
    // Outlives the screen that starts it, e.g. a message shown after 회사 찾기 closes.
    val scope = rememberCoroutineScope()
    // [VIS-02] Short, quiet motion: tabs fade through each other, a screen opened from one slides in a
    // little and fades. The system's "remove animations" setting turns both off.
    NavHost(
        navController = nav,
        // [NAV-05] The first tab (D82); read once when the app starts, so a move in this run does not reset the screens.
        startDestination = start,
        modifier = modifier,
        enterTransition = {
            if (betweenTabs()) fadeIn(tween(TAB_IN_MILLIS, delayMillis = TAB_OUT_MILLIS))
            else slideInHorizontally(tween(SCREEN_MILLIS, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(SCREEN_MILLIS))
        },
        exitTransition = {
            if (betweenTabs()) fadeOut(tween(TAB_OUT_MILLIS)) else fadeOut(tween(SCREEN_MILLIS / 2))
        },
        popEnterTransition = {
            if (betweenTabs()) fadeIn(tween(TAB_IN_MILLIS, delayMillis = TAB_OUT_MILLIS)) else fadeIn(tween(SCREEN_MILLIS))
        },
        popExitTransition = {
            if (betweenTabs()) fadeOut(tween(TAB_OUT_MILLIS))
            else slideOutHorizontally(tween(SCREEN_MILLIS, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(SCREEN_MILLIS))
        },
    ) {
        composable(TopLevel.CALENDAR.route) {
            val vm = appViewModel { CalendarViewModel(it) }
            CalendarScreen(
                vm = vm,
                // [REL-16] 364 (검토 M-E5): a 7-inch tablet held upright (600dp) keeps the rail but not the day beside
                // the month: in half of 600dp the days were too narrow for their shift chips (660 shot).
                wide = wide && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= CALENDAR_BESIDE_DP,
                snackbarHostState = snackbar,
                onOpenMemo = { id -> nav.navigate(Routes.memo(id)) },
                onNewMemo = { date -> nav.navigate(Routes.memo(0, date)) },
                // [MCAL-01] The calendar on screen is the one these edit.
                onOpenPattern = { nav.openPattern(container, container.calendars.active.value) },
                onOpenShiftTypes = { nav.navigate(Routes.shiftTypes(container.calendars.active.value)) { launchSingleTop = true } },
                onOpenStats = { nav.navigateTopLevel(TopLevel.STATS.route) },
                onOpenMoney = { nav.navigateTopLevel(TopLevel.MONEY.route) },
                onOpenPay = { nav.navigate(Routes.pay(container.calendars.active.value)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigateTopLevel(TopLevel.SETTINGS.route) },
                onOpenExtraKinds = { nav.navigate(Routes.extraKinds(container.calendars.active.value)) { launchSingleTop = true } },
                onSetupCalendar = { id -> nav.navigate(Routes.calendarSetup(id)) { launchSingleTop = true } },
                onManageCalendars = { nav.navigate(Routes.CALENDARS) { launchSingleTop = true } },
                onEditCalendar = { id -> nav.navigate(Routes.calendarEdit(id)) { launchSingleTop = true } },
                onOpenCombined = { nav.navigate(Routes.COMBINED) { launchSingleTop = true } },
                onFillMonth = { m -> nav.navigate(Routes.dutyRoster(m)) { launchSingleTop = true } },
            )
        }
        // [PLAZA-06] U25-나: the first setup again, in its own draft; done or given up, back where the user was.
        composable(Routes.SETUP_AGAIN) {
            val vm = appViewModel(key = "setup_again") { SetupViewModel(it, again = true) }
            val done by vm.done.collectAsStateWithLifecycle()
            LaunchedEffect(done) { if (done) nav.popFrom(Routes.SETUP_AGAIN) }
            SetupHost(vm)
        }
        composable(Routes.COMBINED) { com.bamnat.app.ui.calendar.CombinedScreen(onBack = { nav.popFrom(Routes.COMBINED) }) }
        composable(Routes.CALENDAR_SETUP, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm = appViewModel(key = "calendar_setup_$id") { SetupViewModel(it, calendarId = id) }
            val done by vm.done.collectAsStateWithLifecycle()
            val profile by remember(id) { container.calendars.pay(id).profile }.collectAsStateWithLifecycle()
            // [MCAL-06] As after the first setup: work paid by the hour or month is offered its pay next.
            val asksPay = done && container.calendars.get(id)?.let { it.ready && it.style?.usesPayEngine == true } == true &&
                profile == null
            LaunchedEffect(done) { if (done && !asksPay) nav.popFrom(Routes.CALENDAR_SETUP) }
            SetupHost(vm)
            if (asksPay) SetupPaySheet(
                // [CALI-05] 1.30 BB: a coworkers' setup that came with its pay, for this calendar.
                companyPay = remember(id) { container.companyOf(id).store.pendingPay != null },
                onNow = { nav.navigate(Routes.pay(id)) { popUpTo(Routes.CALENDAR_SETUP) { inclusive = true }; launchSingleTop = true } },
                onLater = { nav.popFrom(Routes.CALENDAR_SETUP) },
                calendar = id, // [PAYG-07]
            )
        }
        composable(Routes.CALENDARS) {
            val vm = appViewModel { CalendarsViewModel(it) }
            val add = rememberAddCalendar(container) { id -> nav.navigate(Routes.calendarSetup(id)) { launchSingleTop = true } }
            // [UX-03] 공유 on a calendar of the list: that calendar, on the calendar tab, with its share sheet.
            CalendarsScreen(vm, onBack = { nav.popFrom(Routes.CALENDARS) }, onAddCalendar = add, onShare = { id ->
                nav.navigateTopLevel(TopLevel.CALENDAR.route)
                NoticeLinks.open(NoticeLinks.Target.SHARE, id)
            }, onEdit = { id -> nav.navigate(Routes.calendarEdit(id)) { launchSingleTop = true } })
        }
        composable(Routes.CALENDAR_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "calendar_edit_$id") { com.bamnat.app.ui.calendar.CalendarEditViewModel(it, id) }
            com.bamnat.app.ui.calendar.CalendarEditScreen(
                vm = vm,
                onBack = { nav.popFrom(Routes.CALENDAR_EDIT) },
                onWorkStyle = { nav.navigate(Routes.workStyle(id)) { launchSingleTop = true } },
                onPattern = { nav.openPattern(container, id) },
                onShiftTypes = { nav.navigate(Routes.shiftTypes(id)) { launchSingleTop = true } },
                onPay = { nav.navigate(Routes.pay(id)) { launchSingleTop = true } },
                onMoney = { container.calendars.setActive(id); nav.navigateTopLevel(TopLevel.MONEY.route) },
                onShare = {
                    container.calendars.setActive(id)
                    nav.navigateTopLevel(TopLevel.CALENDAR.route)
                    NoticeLinks.open(NoticeLinks.Target.SHARE, id)
                },
                onDeleted = { nav.popFrom(Routes.CALENDAR_EDIT) },
            )
        }
        composable(Routes.SHOP_DAYS, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "shop_$calendar") { com.bamnat.app.ui.work.ShopDaysViewModel(it, calendar) }
            com.bamnat.app.ui.work.ShopDaysScreen(vm = vm, onBack = { nav.popFrom(Routes.SHOP_DAYS) }, onSaved = { msg ->
                nav.popFrom(Routes.SHOP_DAYS)
                scope.launch { snackbar.showSnackbar(msg) }
            })
        }
        composable(TopLevel.MONEY.route) {
            // [MCAL-06] The money of the calendar on the calendar tab, a view of its own per calendar.
            val calendar by container.calendars.active.collectAsStateWithLifecycle()
            val vm = appViewModel(key = "money_$calendar") { MoneyViewModel(it, calendar) }
            MoneyScreen(vm = vm, wide = wide, snackbar = snackbar, onOpenPay = { nav.navigate(Routes.pay(vm.calendarId)) { launchSingleTop = true } })
        }
        composable(Routes.WORK_STYLE, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            val list by container.calendars.calendars.collectAsStateWithLifecycle()
            val cal = list.firstOrNull { it.id == calendar }
            val isMain = cal?.isMain != false
            val current = if (isMain) settings.workStyle else cal?.style ?: settings.workStyle
            // [UNDO-01] A calendar with records is asked first whether they stay; the answer can be taken back for 7 days.
            var asking by remember { mutableStateOf<Pair<WorkStyle, com.bamnat.app.data.RecordsTrash.Records>?>(null) }
            // clear: null when nothing is changed from a kind of work (the first one, or the same again).
            val apply: (WorkStyle, Boolean?) -> Unit = { style, clear ->
                val before = current
                // [WORK-07] A new kind of work goes straight on to what it still needs, as its card said
                // ("다음: …"): the order for shift work, the days for weekday work, the wage for hourly work.
                // What it already has is kept, so switching back and forth never asks twice.
                scope.launch {
                    if (clear == null) {
                        // [CALS-02] Each calendar keeps its own kind of work: the main one in settings, an added one in its entry.
                        if (isMain) container.settingsRepository.update { it.copy(workStyle = style) } else container.calendars.setStyle(calendar, style)
                    }
                    // [UNDO-01] Kept or cleared, the change is offered back (되돌리기) and listed for 7 days.
                    val entry = if (clear == null) null else container.recordsTrash.changeStyle(
                        calendar, style, clear,
                        if (clear) "기록을 지우고 ${style.label}${com.bamnat.app.ui.Fmt.ro(style.label)} 새로 시작해요" else style.changedMessage(),
                    )
                    val schedule = container.calendars.repository(calendar)
                    val added = addStyleTypes(container, calendar, style)
                    val patterns = schedule.patterns.first()
                    val pay = container.calendars.pay(calendar).profile.value != null
                    var opened: String? = null
                    fun go(route: String, pattern: String) {
                        opened = pattern
                        nav.navigate(route) { popUpTo(Routes.WORK_STYLE) { inclusive = true }; launchSingleTop = true }
                    }
                    when {
                        style == before -> nav.popFrom(Routes.WORK_STYLE)
                        style == WorkStyle.SHIFT && patterns.none { it is WorkPattern.Rotation } -> go(Routes.pattern(weekdays = false, setup = !pay, calendar = calendar), Routes.PATTERN)
                        style == WorkStyle.DAY && patterns.none { it is WorkPattern.Weekly } -> go(Routes.pattern(weekdays = true, setup = !pay, calendar = calendar), Routes.PATTERN)
                        style == WorkStyle.HOURLY && !pay -> go(Routes.pay(calendar), Routes.PAY)
                        // [TYPE-03] A shop sets its hours and regular days off next.
                        style == WorkStyle.BUSINESS -> go(Routes.shopDays(calendar), Routes.SHOP_DAYS)
                        else -> {
                            nav.popFrom(Routes.WORK_STYLE)
                            if (clear == null) snackbar.showSnackbar(style.changedMessage())
                        }
                    }
                    // [UNDO-01] What 되돌리기 takes back besides the trash entry: the types added and the step opened.
                    if (entry != null) styleExtras.value = StyleExtras(entry.id, calendar, added, opened)
                }
            }
            WorkStyleScreen(current = current, title = if (isMain) "근무 형태 바꾸기" else "‘${cal?.name}’ 근무 형태", onBack = { nav.popFrom(Routes.WORK_STYLE) }, onPick = { style ->
                if (current == null || style == current) apply(style, null)
                else scope.launch {
                    val records = container.recordsTrash.records(calendar)
                    if (records.isEmpty) apply(style, false) else asking = style to records
                }
            })
            asking?.let { (style, records) ->
                com.bamnat.app.ui.work.KeepRecordsDialog(
                    calendarName = cal?.name ?: PersonalCalendar.MAIN_NAME,
                    from = current ?: style,
                    to = style,
                    records = records,
                    onKeep = { asking = null; apply(style, false) },
                    onClear = { asking = null; apply(style, true) },
                    onDismiss = { asking = null },
                )
            }
        }
        composable(TopLevel.MEMOS.route) {
            val vm = appViewModel { MemoListViewModel(it) }
            // [DAILY-15] Unfolded, a memo opens in the right pane (list 40%, memo 60%) instead of a screen of its own.
            var pane by rememberSaveable(stateSaver = com.bamnat.app.ui.memo.MemoPane.Saver) { mutableStateOf<com.bamnat.app.ui.memo.MemoPane?>(null) }
            fun next(id: Long, checklist: Boolean = false, date: LocalDate? = null) =
                com.bamnat.app.ui.memo.MemoPane(id, checklist, date, (pane?.n ?: 0) + 1)
            val list = @Composable {
                MemoListScreen(
                    vm = vm,
                    onOpen = { id -> if (wide) pane = next(id) else nav.navigate(Routes.memo(id)) },
                    onNew = { checklist, date -> if (wide) pane = next(0, checklist, date) else nav.navigate(Routes.memo(0, date, checklist, notebook = true)) },
                    onOpenTrash = { nav.navigate(Routes.MEMO_TRASH) { launchSingleTop = true } },
                )
            }
            if (wide) com.bamnat.app.ui.memo.MemoPanes(pane, onOpenChange = { pane = it }, onNew = { pane = next(0) }) { list() }
            else {
                // Folded again with a memo open: it carries on full screen (a new one not yet saved is in the list).
                LaunchedEffect(Unit) {
                    val p = pane ?: return@LaunchedEffect
                    pane = null
                    if (p.id != 0L) nav.navigate(Routes.memo(p.id))
                }
                list()
            }
        }
        composable(Routes.MEMO_TRASH) {
            val vm = appViewModel { MemoTrashViewModel(it) }
            MemoTrashScreen(vm = vm, onBack = { nav.popFrom(Routes.MEMO_TRASH) })
        }
        composable(TopLevel.STATS.route) {
            val calendar by container.calendars.active.collectAsStateWithLifecycle()
            // [INV-01] An investor calendar's tab is its own: 투자·수익.
            val cals by container.calendars.calendars.collectAsStateWithLifecycle()
            val mainStyle by container.settingsRepository.settings.collectAsStateWithLifecycle()
            val tabStyle = cals.firstOrNull { it.id == calendar }?.takeIf { !it.isMain }?.style ?: mainStyle.workStyle
            if (tabStyle == WorkStyle.INVEST) {
                com.bamnat.app.ui.invest.InvestScreen(vm = com.bamnat.app.ui.invest.investViewModel(calendar), wide = wide, snackbar = snackbar)
                return@composable
            }
            val vm = appViewModel(key = "stats_$calendar") { StatsViewModel(it, calendar) }
            StatsScreen(vm = vm, wide = wide, onOpenPay = { nav.navigate(Routes.pay(vm.calendarId)) { launchSingleTop = true } },
                onOpenPayFocus = { f -> nav.navigate(Routes.pay(vm.calendarId, f)) { launchSingleTop = true } })
        }
        composable(TopLevel.SETTINGS.route) {
            val vm = appViewModel { SettingsViewModel(it) }
            val nudges = appViewModel { CompanyNudgeViewModel(it) }
            SettingsScreen(
                vm = vm,
                // [CALS-02] 내 근무 and 급여 set up the calendar chosen at their top: each calendar its own.
                onOpenShiftTypes = { nav.navigate(Routes.shiftTypes(container.calendars.active.value)) { launchSingleTop = true } },
                onOpenPattern = { nav.openPattern(container, container.calendars.active.value) },
                onOpenDiagnostics = { testing -> nav.navigate(Routes.diagnostics(testing)) { launchSingleTop = true } },
                onOpenWorkStyle = { nav.navigate(Routes.workStyle(container.calendars.active.value)) { launchSingleTop = true } },
                onOpenExtraKinds = { nav.navigate(Routes.extraKinds(container.calendars.active.value)) { launchSingleTop = true } },
                onOpenPay = { nav.navigate(Routes.pay(container.calendars.active.value)) { launchSingleTop = true } },
                wide = wide,
                snackbar = snackbar,
                company = if (nudges.enabled) ({
                    CompanySettingsRows(
                        nudges,
                        onFind = { nav.navigate(Routes.companyFind()) { launchSingleTop = true } },
                        onBoard = { nav.navigate(Routes.companyBoard()) { launchSingleTop = true } },
                        onShare = { nav.navigate(Routes.companyReport()) { launchSingleTop = true } },
                        onSent = { nav.navigate(Routes.companySent()) { launchSingleTop = true } },
                        onFix = { nav.fixCompanySetup(it) },
                    )
                }) else null,
                onOpenInquiry = { nav.navigate(Routes.INQUIRY) { launchSingleTop = true } },
                // [J133-77] Every kind of work has its 광장 (알바 광장, 가게 광장 …), not only work with a company.
                onOpenBoard = if (container.companyApi.enabled) ({ nav.navigateTopLevel(TopLevel.PLAZA.route) }) else null,
                onOpenCalendars = { nav.navigate(Routes.CALENDARS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SHARE_GROUP) {
            val vm = appViewModel { com.bamnat.app.ui.calendar.ShareGroupViewModel(it) }
            com.bamnat.app.ui.calendar.ShareGroupScreen(vm, onBack = { nav.popFrom(Routes.SHARE_GROUP) },
                onShare = { nav.popFrom(Routes.SHARE_GROUP); NoticeLinks.open(NoticeLinks.Target.SHARE) })
        }
        composable(Routes.INQUIRY) {
            val vm = appViewModel { InquiryViewModel(it) }
            InquiryScreen(vm = vm, onBack = { nav.popFrom(Routes.INQUIRY) })
        }
        composable(Routes.PAY, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN },
            navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val payFocus = com.bamnat.app.ui.pay.PayFocus.of(entry.arguments?.getString("focus"))
            val vm = appViewModel(key = "pay_$calendar") { PayViewModel(it, calendar) }
            val profile by vm.profile.collectAsStateWithLifecycle()
            // [CALI-04] 1.30 BB: the calendar's own company: its coworkers' setup, pay way, notices and 광장.
            val company = remember(calendar) { container.companyOf(calendar) }
            val pendingPay by company.store.pendingPayFlow.collectAsStateWithLifecycle()
            // [MCAL-06] A coworker's company setup is for the pay of the calendar it went into (1.29: the main one's only).
            if (profile == null && pendingPay != null) {
                // [COMM-01] A coworker's setup came with its pay: only the user's own amounts are left.
                val companyPay = appViewModel(key = "company-pay-$calendar-${pendingPay.hashCode()}") { CompanyPayViewModel(it, calendar) }
                CompanyPayScreen(companyPay, onBack = { nav.popFrom(Routes.PAY) }, onByHand = { company.store.pendingPay = null })
                return@composable
            }
            val nudges = appViewModel(key = "company-nudges-$calendar") { CompanyNudgeViewModel(it, calendar) }
            val publicUse by company.store.publicUse.collectAsStateWithLifecycle()
            // [PAYF-01][PAYF-07] 1.29 AP: a company's pay way is for wages only, never a 투자·가게·현장 calendar.
            // [CALI-04] 1.30 BB: on every calendar's pay with wages, with that calendar's company.
            val payEngine = remember(calendar) {
                container.calendars.styleOf(calendar, container.settingsRepository.current.workStyle)?.usesPayEngine != false
            }
            // [J133-71] 1.33: and only for work that has a company (교대·일근), or an 알바 calendar that already joined one.
            val companyPay = payEngine && calendar != PersonalCalendar.EXAMPLE && remember(calendar) { container.companyFits(calendar) }
            // [COMM-02] Right after a pay setup made by hand under a company's name, the invitation slides up once.
            var wasEmpty by rememberSaveable { mutableStateOf(profile == null) }
            var inviting by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(profile == null) {
                if (profile == null) wasEmpty = true
                else if (wasEmpty) {
                    wasEmpty = false
                    if (nudges.invite.value != null) inviting = true
                }
            }
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
            PayScreen(vm = vm, onBack = { nav.popFrom(Routes.PAY) }, focus = payFocus,
                onOpenExtraKinds = { nav.navigate(Routes.extraKinds(calendar)) { launchSingleTop = true } },
                onOpenLeave = { nav.navigate(Routes.leave(calendar)) { launchSingleTop = true } },
                top = {
                    // [EASY-05] A pay fact still guessed, asked here where it is used: only those one tap answers.
                    if (profile != null) com.bamnat.app.ui.guide.AskArea(container, calendar,
                        container.calendars.styleOf(calendar, container.settingsRepository.current.workStyle),
                        com.bamnat.core.profile.AskPlace.Screen(PAY_ASKS), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), inCardOnly = true)
                    CompanyNudgeCards(nudges, onShare = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
                        onFix = { nav.fixCompanySetup(it, calendar) })
                    if (companyPay) {
                        // [PAYF-04] The company pay way in use: 맞아요·달라요 and 고쳐서 제안하기 on the pay screen itself, and its 기본급.
                        com.bamnat.app.ui.company.PayWayCard(profile, update = vm::update, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        // [PAYF-01] A pay set up by hand: the search box one tap away until a company's way is in use.
                        if (profile != null) com.bamnat.app.ui.company.PayFindLine(onSaved = { p -> vm.update { p } },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    // [SPAT-05] A public cycle in use and its pay just set: the plaza, the reward said first.
                    if (profile != null && calendar != PersonalCalendar.EXAMPLE) com.bamnat.app.ui.company.PublicWayNotice(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        onPay = {}, onShare = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } })
                    // [COMM-12] The company is known and no coworker's rules are in use: the plaza, with its name typed.
                    if (calendar != PersonalCalendar.EXAMPLE) com.bamnat.app.ui.company.PlazaEntryCard(container, calendar, onOpen = { nav.navigate(Routes.companyBoard(calendar)) { launchSingleTop = true } },
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                },
                // [DAILY-16] 광장 시세 of the user's own job and region next to their pay, compared on the phone.
                underTotal = {
                    com.bamnat.app.ui.company.RateHintLine(profile) { nav.navigate(Routes.PLAZA_RATES) { launchSingleTop = true } }
                    // [COMM-12] One line into the plaza from the pay; hidden without the company server.
                    if (calendar != PersonalCalendar.EXAMPLE) com.bamnat.app.ui.company.PlazaEntryLine(container, onOpen = { nav.navigate(Routes.companyBoard(calendar)) { launchSingleTop = true } })
                },
                // [ADMIN-02] 계산이 이상해요 is a question in the app; a build without the company server shares it.
                onReport = if (container.inquiryApi.enabled) { check ->
                    container.pendingPayCheck = check
                    nav.navigate(Routes.INQUIRY) { launchSingleTop = true }
                } else null,
                // [PAYS-09] The invitation after a match or an item of the company's own sends to 내 설정 나누기.
                onShareWay = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
                // [PAYS-10][UX-05] '?' opens the pay answers first, then a question if they don't help.
                onHelp = { com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "급여", help = true)) },
                // [SPAT-04] The public cycle in use, for the first pay setup. [CALI-04] The calendar's own (1.29: the main one's).
                fromCycle = publicUse?.let { u -> u.cycle?.let { it to u.codes } },
                // [PLAZA-30] 1.27 AI: the plaza form's 명세서로 맞춰 보기, once.
                startMatching = remember { container.pendingMatch.also { container.pendingMatch = false } },
                // [PLAZA-16] 1.27 AH: 우리 회사 급여 방식 찾기, for the main job's pay. [PAYF-01] 1.29: the setup's first question.
                companyFind = companyPay)
            if (inviting) {
                InviteSheet(nudges, onShare = { inviting = false; nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
                    // [J133-17] 나중에 is a no for now: the card on the pay screen waits too, not asking again right after.
                    onDismiss = { inviting = false; nudges.closeInvite() })
            }
            }
        }
        composable(
            Routes.COMPANY_FIND,
            arguments = listOf(
                navArgument("setup") { type = NavType.BoolType; defaultValue = false },
                navArgument("weekdays") { type = NavType.BoolType; defaultValue = false },
                navArgument("calendar") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val setup = entry.arguments?.getBoolean("setup") ?: false
            val weekdays = entry.arguments?.getBoolean("weekdays") ?: false
            // [CALI-03] 1.30 BB: the company is chosen for this calendar only.
            val calendar = remember(entry) { companyCalendarOf(container, entry.arguments?.getString("calendar")) }
            val vm = appViewModel(key = "company-find-$calendar") { CompanyFindViewModel(it, calendar) }
            val profile by remember(calendar) { container.companyOf(calendar).pay.profile }.collectAsStateWithLifecycle()
            val ready = !setup && profile != null
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
            CompanyFindScreen(
                vm = vm,
                setup = setup,
                ready = ready,
                onBack = { nav.popFrom(Routes.COMPANY_FIND) },
                onOpen = { id -> nav.navigate(Routes.company(id, setup, weekdays, calendar)) },
                onByHand = { nav.afterCompanyByHand(setup, weekdays, ready, calendar) },
                onBoard = if (setup || !container.companyApi.enabled) null else ({ nav.navigate(Routes.companyBoard(calendar)) { launchSingleTop = true } }),
                // [SPAT-04][SPAT-05] A public cycle just put in: on to the pay (with its usual allowances), or with pay set, the plaza.
                onPay = { nav.navigate(Routes.pay(calendar)) { popUpTo(Routes.COMPANY_FIND) { inclusive = true }; launchSingleTop = true } },
                onShare = { nav.navigate(Routes.companyReport(calendar)) { popUpTo(Routes.COMPANY_FIND) { inclusive = true }; launchSingleTop = true } },
            )
            }
        }
        composable(
            Routes.COMPANY,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("setup") { type = NavType.BoolType; defaultValue = false },
                navArgument("weekdays") { type = NavType.BoolType; defaultValue = false },
                navArgument("calendar") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val setup = entry.arguments?.getBoolean("setup") ?: false
            val weekdays = entry.arguments?.getBoolean("weekdays") ?: false
            // [CALI-03] 1.30 BB: 이 설정 쓰기 goes into this calendar only; the others keep theirs.
            val calendar = remember(entry) { companyCalendarOf(container, entry.arguments?.getString("calendar")) }
            val company = remember(calendar) { container.companyOf(calendar) }
            val vm = appViewModel(key = "company-$id-$calendar") { CompanyDetailViewModel(it, id, calendar) }
            val profile by company.pay.profile.collectAsStateWithLifecycle()
            val ready = !setup && profile != null
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
            CompanyDetailScreen(
                vm = vm,
                setup = setup,
                ready = ready,
                onBack = { nav.popFrom(Routes.COMPANY) },
                onApplied = { applied, name ->
                    scope.launch {
                        val hasPattern = company.schedule.patterns.first().isNotEmpty()
                        val pay = company.pay.profile.value != null
                        when {
                            // A setup without a rotation (요일 고정 회사): the days are still to be set.
                            !hasPattern -> nav.navigate(Routes.pattern(weekdays = weekdays || applied.cycleDays == null, setup = true, calendar = calendar)) {
                                popUpTo(Routes.COMPANY_FIND) { inclusive = true }
                            }
                            applied.pendingPay || (setup && !pay) -> nav.navigate(Routes.pay(calendar)) {
                                popUpTo(Routes.COMPANY_FIND) { inclusive = true }
                                launchSingleTop = true
                            }
                            // [PAYF-03][PAYF-06] 1.29 AP: the pay way went onto a pay with amounts: its 기본급 is checked there next.
                            applied.rules && company.payWays.askBase.value -> nav.navigate(Routes.pay(calendar)) {
                                popUpTo(Routes.COMPANY_FIND) { inclusive = true }
                                launchSingleTop = true
                            }
                            else -> {
                                // From 회사 규칙 광장 there is no 회사 찾기 below: back to the board.
                                if (!nav.popBackStack(Routes.COMPANY_FIND, inclusive = true)) nav.popFrom(Routes.COMPANY)
                                val done = "‘$name’ 설정을 넣었어요" + (applied.cycleDays?.let { " · ${it}일 순서" } ?: "")
                                // [PLAZA-32] 1.27 AI (443): the company's 정보 광장 opens at once, with one line on what just happened.
                                if (container.companyApi.enabled && company.store.member.value != null) {
                                    container.pendingPlazaNote = "$done. 이제 같은 회사 분들끼리 이야기하는 정보 광장이에요"
                                    nav.navigate(Routes.companyBoard(calendar)) { launchSingleTop = true }
                                } else snackbar.showSnackbar(done)
                            }
                        }
                    }
                },
                onByHand = { nav.afterCompanyByHand(setup, weekdays, ready, calendar) },
            )
            }
        }
        composable(Routes.COMPANY_BOARD, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = "" })) { entry ->
            PlazaHost(nav, container, entry.arguments?.getString("calendar"), onBack = { nav.popFrom(Routes.COMPANY_BOARD) })
        }
        // 1.35 [RCX-29] (사용자 2026-10-30 "광장이나 나눔판이 사람들에게 노출이 잘되게 위치를 바꿔줘 지금 너무 숨어있어"): 광장 is a tab
        // of its own, with the 나눔 판 at its top; no back arrow, it is a place of the app like 달력.
        composable(TopLevel.PLAZA.route) {
            // 1.35 [HUB-01] 마을: 광장, 테마, 장터 (나눔 판) and 공방 (스튜디오) of the calendar's kind of work, one tab.
            val calendar = remember { companyCalendarOf(container, null) }
            val style = remember(calendar) { container.calendars.styleOf(calendar, container.settingsRepository.current.workStyle) }
            val recipeCal = PersonalCalendar.MAIN
            com.bamnat.app.ui.hub.HubScreen(container, style,
                talk = { PlazaHost(nav, container, null, onBack = null, inHub = true) },
                share = { kind ->
                    val vm = appViewModel(key = "recipes-$recipeCal") { com.bamnat.app.ui.recipe.RecipeListViewModel(it, recipeCal) }
                    // 테마's 장터에서 새 테마 찾기 opens 장터 on 테마·스킨; the 장터 tab itself opens as it was left.
                    LaunchedEffect(kind) { if (kind != null && vm.state.value.kind != kind) vm.kind(kind) }
                    com.bamnat.app.ui.recipe.RecipeListScreen(vm, onBack = null,
                        onOpen = { code -> nav.navigate(Routes.recipe(code, recipeCal)) },
                        onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } },
                        onMine = { nav.navigate(Routes.RECIPE_MINE) { launchSingleTop = true } },
                        title = com.bamnat.app.ui.hub.HubSection.SHARE.label)
                },
                onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } },
                onMine = { nav.navigate(Routes.RECIPE_MINE) { launchSingleTop = true } },
                onRequests = { nav.navigate(Routes.recipeRequests(recipeCal)) { launchSingleTop = true } },
                onCode = { nav.navigate(Routes.recipes(recipeCal, code = true)) { launchSingleTop = true } },
                onStudio = { start -> nav.navigate(Routes.studio(start)) { launchSingleTop = true } },
            )
        }
        composable(Routes.STUDIO, arguments = listOf(navArgument("start") { type = NavType.StringType; defaultValue = "mine" })) { entry ->
            val start = entry.arguments?.getString("start") ?: "mine"
            val vm = appViewModel(key = "studio-$start") { com.bamnat.app.ui.studio.StudioViewModel(it, start) }
            // [STU-21] 1.36 올리기 happens inside the studio; once up, the recipe opens in place of it.
            com.bamnat.app.ui.studio.StudioScreen(vm, onBack = { nav.popFrom(Routes.STUDIO) }, onOpen = { code ->
                nav.navigate(Routes.recipe(code)) { popUpTo(Routes.STUDIO) { inclusive = true } }
            })
        }
        composable(Routes.STYLE_PLAZA, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = "" })) { entry ->
            val calendar = remember { companyCalendarOf(container, entry.arguments?.getString("calendar")) }
            val recipesOn by container.recipes.on.collectAsStateWithLifecycle()
            val home = appViewModel(key = "style-plaza-$calendar") { com.bamnat.app.ui.company.PlazaHomeViewModel(it, calendar, own = true) }
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
                com.bamnat.app.ui.company.PlazaHomeScreen(home, onBack = { nav.popFrom(Routes.STYLE_PLAZA) },
                    onAll = { nav.navigate(Routes.COMPANY_BOARD_ALL) { launchSingleTop = true } },
                    onCompany = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false, calendar = calendar)) },
                    onRates = { nav.navigate(Routes.PLAZA_RATES) { launchSingleTop = true } },
                    onShare = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
                    header = {
                        if (recipesOn && container.recipeApi.enabled) com.bamnat.app.ui.recipe.PlazaRecipeBlock(container, calendar,
                            onOpen = { nav.navigate(Routes.recipes(calendar)) { launchSingleTop = true } },
                            onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } },
                            onItem = { code -> nav.navigate(Routes.recipe(code, calendar)) },
                            onRequests = { nav.navigate(Routes.recipeRequests(calendar)) { launchSingleTop = true } },
                        )
                    })
            }
        }
        // [PLAZA-32] 1.27 AI: every company's rules, from a company's or a kind of work's plaza.
        composable(Routes.COMPANY_BOARD_ALL) {
            val vm = appViewModel { CompanyBoardViewModel(it) }
            CompanyBoardScreen(
                vm = vm,
                onBack = { nav.popFrom(Routes.COMPANY_BOARD_ALL) },
                onOpen = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false)) },
                onShare = { nav.navigate(Routes.companyReport()) { launchSingleTop = true } },
                onRates = { nav.navigate(Routes.PLAZA_RATES) { launchSingleTop = true } },
            )
        }
        composable(Routes.RECIPES, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN },
            navArgument("kind") { type = NavType.StringType; defaultValue = "" },
            navArgument("code") { type = NavType.BoolType; defaultValue = false })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val kind = entry.arguments?.getString("kind").orEmpty()
            val vm = appViewModel(key = "recipes-$calendar-$kind") { com.bamnat.app.ui.recipe.RecipeListViewModel(it, calendar) }
            LaunchedEffect(kind) { if (kind.isNotEmpty() && vm.state.value.kind != kind) vm.kind(kind) }
            com.bamnat.app.ui.recipe.RecipeListScreen(vm, onBack = { nav.popFrom(Routes.RECIPES) },
                onOpen = { code -> nav.navigate(Routes.recipe(code, calendar)) },
                onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } },
                onMine = { nav.navigate(Routes.RECIPE_MINE) { launchSingleTop = true } },
                title = if (kind == "skin") "다른 분들이 만든 테마" else com.bamnat.app.ui.hub.HubSection.SHARE.label,
                askCode = entry.arguments?.getBoolean("code") == true)
        }
        composable(Routes.RECIPE, arguments = listOf(navArgument("code") { type = NavType.StringType },
            navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val code = entry.arguments?.getString("code").orEmpty()
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "recipe-$code-$calendar") { com.bamnat.app.ui.recipe.RecipeDetailViewModel(it, code, calendar) }
            com.bamnat.app.ui.recipe.RecipeDetailScreen(vm, onBack = { nav.popFrom(Routes.RECIPE) }, onRemake = { body ->
                com.bamnat.app.recipe.RecipeLinks.remake = body to code
                nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.recipeStart(null)))
            }, onOpen = { other -> nav.navigate(Routes.recipe(other, calendar)) }, onMaker = { id -> nav.navigate(Routes.recipeMaker(id)) },
                onNewVersion = { body ->
                    // [RCX-31] The same code, its next 판.
                    com.bamnat.app.recipe.RecipeLinks.remake = body to code
                    nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.recipeStart(code)))
                },
                onTakeInto = { mine, body ->
                    // [LIN-03] My recipe's next 판, with what the 수정판 changed in it.
                    com.bamnat.app.recipe.RecipeLinks.remake = body to mine
                    nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.recipeStart(mine)))
                })
        }
        composable(Routes.RECIPE_MAKER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm = appViewModel(key = "recipe-maker-$id") { com.bamnat.app.ui.recipe.MakerViewModel(it, id) }
            com.bamnat.app.ui.recipe.MakerScreen(vm, onBack = { nav.popFrom(Routes.RECIPE_MAKER) }, onOpen = { code -> nav.navigate(Routes.recipe(code)) })
        }
        composable(Routes.RECIPE_REQUESTS, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "recipe-requests-$calendar") { com.bamnat.app.ui.recipe.RequestsViewModel(it, calendar) }
            com.bamnat.app.ui.recipe.RequestsScreen(vm, onBack = { nav.popFrom(Routes.RECIPE_REQUESTS) },
                onOpen = { code -> nav.navigate(Routes.recipe(code, calendar)) }, onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } })
        }
        composable(Routes.RECIPE_MAKE, arguments = listOf(
            navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN },
            navArgument("draft") { type = NavType.LongType; defaultValue = 0L },
            navArgument("kind") { type = NavType.StringType; defaultValue = "" },
            navArgument("code") { type = NavType.StringType; defaultValue = "" },
        )) { entry ->
            val a = entry.arguments
            val calendar = a?.getString("calendar") ?: PersonalCalendar.MAIN
            val draft = a?.getLong("draft") ?: 0L
            val kind = a?.getString("kind")?.let(com.bamnat.core.recipe.PieceKind::of)
            val own = a?.getString("code")?.ifEmpty { null }
            val remake = remember { com.bamnat.app.recipe.RecipeLinks.remake.also { com.bamnat.app.recipe.RecipeLinks.remake = null } }
            val vm = appViewModel(key = "recipe-make-$calendar-$draft-$kind-$own-${remake?.second}") {
                com.bamnat.app.ui.recipe.RecipeMakeViewModel(it, calendar, draft, remake?.first, remake?.second?.takeIf { own == null }, kind, own)
            }
            com.bamnat.app.ui.recipe.RecipeMakeScreen(vm, onBack = { nav.popFrom(Routes.RECIPE_MAKE) },
                onOpen = { code -> nav.navigate(Routes.recipe(code, calendar)) { popUpTo(Routes.RECIPE_MAKE) { inclusive = true } } })
        }
        composable(Routes.RECIPE_MINE) {
            val vm = appViewModel { com.bamnat.app.ui.recipe.RecipeMineViewModel(it) }
            com.bamnat.app.ui.recipe.RecipeMineScreen(vm, onBack = { nav.popFrom(Routes.RECIPE_MINE) },
                onOpen = { code -> nav.navigate(Routes.recipe(code)) },
                onDraft = { id -> nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.START_DRAFT + id)) },
                onList = { nav.navigate(Routes.recipes()) { launchSingleTop = true } },
                onMaker = { id -> nav.navigate(Routes.recipeMaker(id)) },
                onNewVersion = { code, body ->
                    com.bamnat.app.recipe.RecipeLinks.remake = body to code
                    nav.navigate(Routes.studio(com.bamnat.app.ui.studio.StudioViewModel.recipeStart(code)))
                })
        }
        composable(Routes.PLAZA_RATES) {
            val vm = appViewModel { com.bamnat.app.ui.company.RatesViewModel(it) }
            com.bamnat.app.ui.company.RatesScreen(vm = vm, onBack = { nav.popFrom(Routes.PLAZA_RATES) })
        }
        composable(Routes.COMPANY_REPORT, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = "" })) { entry ->
            // [CALI-02] 1.30 BB: the calendar's own setup, under its own company, kept as sent by it.
            val calendar = remember(entry) { companyCalendarOf(container, entry.arguments?.getString("calendar")) }
            val vm = appViewModel(key = "company-report-$calendar") { CompanyReportViewModel(it, calendar) }
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
            CompanyReportScreen(
                vm = vm,
                onBack = { nav.popFrom(Routes.COMPANY_REPORT) },
                onDone = { nav.popFrom(Routes.COMPANY_REPORT) },
                onOpenSent = { nav.navigate(Routes.companySent(calendar)) { popUpTo(Routes.COMPANY_REPORT) { inclusive = true } } },
                // [SPAT-05] Sent from a public row: that company's plaza page, in place of the form.
                onOpenCompany = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false, calendar = calendar)) { popUpTo(Routes.COMPANY_REPORT) { inclusive = true } } },
                // [PLAZA-30] 1.27 AI: 명세서로 맞춰 보기 on the calendar's pay; back comes to this form with the tick open.
                onMatch = { nav.navigate(Routes.pay(calendar)) { launchSingleTop = true } },
                // [FIN130-01] 이 규칙 쓰기 with its pay way: the pay screen asks the 기본급 and puts the way in.
                onOpenPay = { nav.navigate(Routes.pay(calendar)) { popUpTo(Routes.COMPANY_REPORT) { inclusive = true }; launchSingleTop = true } },
            )
            }
        }
        composable(Routes.COMPANY_SENT, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = "" })) { entry ->
            val calendar = remember(entry) { companyCalendarOf(container, entry.arguments?.getString("calendar")) }
            val vm = appViewModel(key = "company-report-$calendar") { CompanyReportViewModel(it, calendar) }
            CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
            CompanySentScreen(
                vm = vm,
                onBack = { nav.popFrom(Routes.COMPANY_SENT) },
                onShare = { nav.navigate(Routes.companyReport(calendar)) { popUpTo(Routes.COMPANY_SENT) { inclusive = true } } },
            )
            }
        }
        // [EXTRA-04] Names, hours and pay of each kind of extra work, from settings, a day or the pay rules.
        composable(Routes.SLEEP) {
            val vm = appViewModel { com.bamnat.app.ui.sleep.SleepViewModel(it) }
            com.bamnat.app.ui.sleep.SleepScreen(vm = vm, wide = wide, onBack = { nav.popFrom(Routes.SLEEP) })
        }
        composable(Routes.OWED) {
            val calendar by container.calendars.active.collectAsStateWithLifecycle()
            val vm = appViewModel(key = "owed_$calendar") { com.bamnat.app.ui.money.OwedViewModel(it, calendar) }
            com.bamnat.app.ui.money.OwedScreen(vm = vm, wide = wide, onBack = { nav.popFrom(Routes.OWED) })
        }
        composable(Routes.CALENDAR_IMPORT) {
            val vm = appViewModel { com.bamnat.app.ui.imports.CalendarImportViewModel(it) }
            com.bamnat.app.ui.imports.CalendarImportScreen(vm = vm, onBack = { nav.popFrom(Routes.CALENDAR_IMPORT) })
        }
        composable(Routes.DUTY_ROSTER) {
            val calendar = remember { container.calendars.active.value }
            val vm = appViewModel(key = "duty_$calendar") { com.bamnat.app.ui.imports.DutyRosterViewModel(it, calendar) }
            com.bamnat.app.ui.imports.DutyRosterScreen(vm = vm, onBack = { nav.popFrom(Routes.DUTY_ROSTER) })
        }
        composable(Routes.DUTY_ROSTER_MONTH, arguments = listOf(navArgument("month") { type = NavType.StringType })) { entry ->
            val calendar = remember { container.calendars.active.value }
            val month = remember { entry.arguments?.getString("month")?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() } }
            val vm = appViewModel(key = "duty_${calendar}_$month") { com.bamnat.app.ui.imports.DutyRosterViewModel(it, calendar, month) }
            com.bamnat.app.ui.imports.DutyRosterScreen(vm = vm, onBack = { nav.popFrom(Routes.DUTY_ROSTER_MONTH) })
        }
        composable(Routes.TEAMS) {
            val calendar = remember { container.calendars.active.value }
            val vm = appViewModel(key = "teams_$calendar") { com.bamnat.app.ui.calendar.TeamsViewModel(it, calendar) }
            // [CREW-04] Opened from a day's 대근 찾기, the finder starts on that day.
            val startDay = remember { NoticeLinks.payload.value?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } }
            com.bamnat.app.ui.calendar.TeamsScreen(vm = vm, onBack = { nav.popFrom(Routes.TEAMS) },
                onOpenPattern = { nav.openPattern(container, calendar) }, startDay = startDay)
        }
        composable(Routes.YEAR) {
            val calendar = remember { container.calendars.active.value }
            val vm = appViewModel(key = "year_$calendar") { com.bamnat.app.ui.calendar.YearViewModel(it, calendar) }
            com.bamnat.app.ui.calendar.YearScreen(vm = vm, onBack = { nav.popFrom(Routes.YEAR) })
        }
        composable(Routes.HEALTH_REPORT) {
            val calendar = remember { container.calendars.active.value }
            val vm = appViewModel(key = "health_$calendar") { com.bamnat.app.ui.stats.HealthReportViewModel(it, calendar) }
            com.bamnat.app.ui.stats.HealthReportScreen(vm = vm, onBack = { nav.popFrom(Routes.HEALTH_REPORT) })
        }
        composable(Routes.ATTENDANCE_IMPORT) {
            val calendar = remember { container.calendars.active.value }
            val vm = appViewModel(key = "attendance_$calendar") { com.bamnat.app.ui.imports.AttendanceImportViewModel(it, calendar) }
            com.bamnat.app.ui.imports.AttendanceImportScreen(vm = vm, onBack = { nav.popFrom(Routes.ATTENDANCE_IMPORT) })
        }
        composable(Routes.COMMUTE) {
            val vm = appViewModel { com.bamnat.app.ui.work.CommuteViewModel(it) }
            com.bamnat.app.ui.work.CommuteScreen(vm = vm, onBack = { nav.popFrom(Routes.COMMUTE) })
        }
        composable(Routes.LEAVE, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "leave_$calendar") { com.bamnat.app.ui.leave.LeaveViewModel(it, calendar) }
            com.bamnat.app.ui.leave.LeaveScreen(vm = vm, onBack = { nav.popFrom(Routes.LEAVE) },
                onEditRules = { nav.navigate(Routes.pay(calendar)) { launchSingleTop = true } })
        }
        composable(Routes.FEATURES) { com.bamnat.app.ui.guide.FeatureSwitchesScreen(onBack = { nav.popFrom(Routes.FEATURES) }) }
        composable(Routes.PRO) { com.bamnat.app.ui.pro.ProScreen(onBack = { nav.popFrom(Routes.PRO) }) }
        composable(Routes.PROFILE) { com.bamnat.app.ui.guide.ProfileScreen(onBack = { nav.popFrom(Routes.PROFILE) }) }
        composable(Routes.JOURNAL) { com.bamnat.app.ui.guide.JournalScreen(onBack = { nav.popFrom(Routes.JOURNAL) }) }
        composable(Routes.RECOMMENDED) {
            com.bamnat.app.ui.onboarding.RecommendedScreen(container, firstStart = false, onDone = { nav.popFrom(Routes.RECOMMENDED) })
        }
        composable(Routes.EXTRA_KINDS, arguments = listOf(navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN })) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "extra_kinds_$calendar") { ExtraKindsViewModel(it, calendar) }
            ExtraKindsScreen(vm = vm, onBack = { nav.popFrom(Routes.EXTRA_KINDS) },
                // Back to the pay screen when it opened this one, so the stack does not grow.
                onOpenPay = { if (!nav.popBackStack(Routes.PAY, inclusive = false)) nav.navigate(Routes.pay(calendar)) { launchSingleTop = true } })
        }
        composable(
            Routes.DIAGNOSTICS,
            arguments = listOf(navArgument("testing") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val vm = appViewModel { DiagnosticsViewModel(it) }
            DiagnosticsScreen(vm = vm, testing = entry.arguments?.getBoolean("testing") == true, onBack = { nav.popFrom(Routes.DIAGNOSTICS) })
        }
        composable(Routes.SHIFT_TYPES, arguments = listOf(
            navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN },
            navArgument("edit") { type = NavType.LongType; defaultValue = 0L },
        )) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "shifts_$calendar") { ShiftsViewModel(it, calendar) }
            ShiftTypesScreen(vm = vm, onBack = { nav.popFrom(Routes.SHIFT_TYPES) }, editId = entry.arguments?.getLong("edit") ?: 0L)
        }
        composable(
            Routes.PATTERN,
            arguments = listOf(
                navArgument("weekdays") { type = NavType.BoolType; defaultValue = false },
                navArgument("setup") { type = NavType.BoolType; defaultValue = false },
                navArgument("calendar") { type = NavType.StringType; defaultValue = PersonalCalendar.MAIN },
            ),
        ) { entry ->
            val calendar = entry.arguments?.getString("calendar") ?: PersonalCalendar.MAIN
            val vm = appViewModel(key = "shifts_$calendar") { ShiftsViewModel(it, calendar) }
            val setup = entry.arguments?.getBoolean("setup") ?: false
            PatternScreen(
                vm = vm,
                startWeekdays = entry.arguments?.getBoolean("weekdays") ?: false,
                setup = setup,
                onBack = { nav.popFrom(Routes.PATTERN) },
                // [PAYX-13] Right after the first cycle, the pay setup opens in its place.
                onSaved = if (setup) ({
                    nav.navigate(Routes.pay(calendar)) { popUpTo(Routes.PATTERN) { inclusive = true }; launchSingleTop = true }
                }) else null,
                onOpenShiftTypes = { nav.navigate(Routes.shiftTypes(calendar)) { launchSingleTop = true } },
                // [SPAT-02] The company's cycle is for the main calendar, as a coworker's setup is (MCAL-06).
                onFindCompany = if (!setup && calendar == PersonalCalendar.MAIN) ({
                    vm.draft.forget()
                    nav.navigate(Routes.companyFind()) { popUpTo(Routes.PATTERN) { inclusive = true }; launchSingleTop = true }
                }) else null,
            )
        }
        composable(
            Routes.MEMO,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("checklist") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("notebook") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val args = entry.arguments
            val id = args?.getLong("id") ?: 0L
            val date = args?.getString("date")?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val checklist = args?.getBoolean("checklist") ?: false
            val notebook = args?.getBoolean("notebook") ?: false
            val vm = appViewModel(key = "memo-$id") { MemoEditorViewModel(it, id, date, checklist, notebook) }
            MemoEditorScreen(vm = vm, onBack = { nav.popFrom(Routes.MEMO) })
        }
    }
}

/** [UX-06] A tab's name, whole at every text size: at the largest sizes "근무·급여" was cut to "근무·". */
@Composable
private fun TabLabel(text: String) {
    // [VIS-04] 중-3: from 150% a two-word name ("근무·급여") used to take two lines and push its icon above the others'
    // (A11Y-04); now it keeps one line with its last word ("급여"), as big as the other tabs, and TalkBack reads it whole.
    val parts = text.split('·')
    if (parts.size == 2 && androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
        FitText(parts[1], MaterialTheme.typography.labelMedium, Modifier.clearAndSetSemantics { contentDescription = text })
    } else {
        FitText(text, MaterialTheme.typography.labelMedium)
    }
}

/** [UX-05] A tab's icon; 설정 carries how many answers to the user's questions are new. */
@Composable
private fun TabIcon(dest: TopLevel, selected: Boolean, badge: Int) {
    val icon = if (selected) dest.selectedIcon else dest.icon
    if (badge <= 0) {
        Icon(icon, contentDescription = null)
        return
    }
    BadgedBox(badge = { IconCountBadge(badge, "문의에 새 답 ${badge}개", cap = 9) }) { Icon(icon, contentDescription = null) }
}

/** [TYPE-03] 근무 주기 of a calendar; a shop's is its 영업일·정기휴무. */
private fun NavHostController.openPattern(c: com.bamnat.app.AppContainer, calendar: String) {
    val style = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    navigate(if (style == WorkStyle.BUSINESS) Routes.shopDays(calendar) else Routes.pattern(weekdays = style == WorkStyle.DAY, calendar = calendar)) { launchSingleTop = true }
}

/** [GUIDE-01] Where each [com.bamnat.core.guide.Place] of the feature list is, for the calendar on screen. */
private fun NavHostController.openFeature(c: AppContainer, link: com.bamnat.core.guide.FeatureRoute) {
    val cal = c.calendars.active.value
    fun screen(r: String) = navigate(r) { launchSingleTop = true }
    when (link.place) {
        // [CUST-01] 화면 꾸미기 opens over the month, not over today.
        Place.CALENDAR -> if (link.focus == "customize") navigateTopLevel(TopLevel.CALENDAR.route) else {
            navigateTopLevel(TopLevel.CALENDAR.route)
            com.bamnat.app.ui.guide.FeatureLinks.today(cal)
            NoticeLinks.open(NoticeLinks.Target.TODAY)
        }
        Place.TODAY -> {
            navigateTopLevel(TopLevel.CALENDAR.route)
            com.bamnat.app.ui.guide.FeatureLinks.today(cal)
            NoticeLinks.open(NoticeLinks.Target.TODAY)
        }
        Place.MONEY -> if (link.focus == "tax") NoticeLinks.open(NoticeLinks.Target.TAX) else navigateTopLevel(TopLevel.MONEY.route)
        Place.AUTO_RECORD -> NoticeLinks.open(NoticeLinks.Target.AUTO_RECORD)
        Place.MEMOS -> navigateTopLevel(TopLevel.MEMOS.route)
        Place.STATS -> navigateTopLevel(TopLevel.STATS.route)
        Place.PAY -> screen(Routes.pay(cal, link.focus?.takeIf { com.bamnat.app.ui.pay.PayFocus.of(it) != null }))
        Place.PATTERN -> openPattern(c, cal)
        Place.SHIFT_TYPES -> screen(Routes.shiftTypes(cal))
        Place.EXTRA_KINDS -> screen(Routes.extraKinds(cal))
        Place.WORK_STYLE -> screen(Routes.workStyle(cal))
        Place.CALENDARS -> screen(Routes.CALENDARS)
        Place.CALENDAR_EDIT -> screen(Routes.calendarEdit(cal))
        Place.SHOP_DAYS -> screen(Routes.shopDays(cal))
        Place.COMBINED -> screen(Routes.COMBINED)
        Place.OWED -> screen(Routes.OWED)
        Place.SLEEP -> screen(Routes.SLEEP)
        Place.DUTY_ROSTER -> screen(Routes.DUTY_ROSTER)
        Place.HEALTH_REPORT -> screen(Routes.HEALTH_REPORT)
        Place.TEAMS -> screen(Routes.TEAMS)
        Place.YEAR -> screen(Routes.YEAR)
        Place.ATTENDANCE_IMPORT -> screen(Routes.ATTENDANCE_IMPORT)
        Place.COMMUTE -> screen(Routes.COMMUTE)
        Place.CALENDAR_IMPORT -> screen(Routes.CALENDAR_IMPORT)
        Place.SHARE -> { navigateTopLevel(TopLevel.CALENDAR.route); NoticeLinks.open(NoticeLinks.Target.SHARE, cal) }
        Place.RECEIVE -> { navigateTopLevel(TopLevel.CALENDAR.route); NoticeLinks.open(NoticeLinks.Target.RECEIVE) }
        Place.DIAGNOSTICS -> screen(Routes.diagnostics(false))
        Place.INQUIRY -> com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(help = true))
        Place.FEATURES -> screen(Routes.FEATURES)
        Place.PRO -> screen(Routes.PRO)
        Place.RECOMMENDED -> screen(Routes.RECOMMENDED)
        Place.LEAVE -> screen(Routes.leave(cal))
        Place.PROFILE -> screen(Routes.PROFILE)
        Place.JOURNAL -> screen(Routes.JOURNAL)
        Place.RECIPES -> screen(if (c.recipes.on.value) Routes.recipes(cal) else Routes.RECIPE_MINE)
        Place.RECIPE_MINE -> screen(Routes.RECIPE_MINE)
        Place.SETTINGS_WORK, Place.SETTINGS_ALARM, Place.SETTINGS_NOTICES, Place.SETTINGS_PAY,
        Place.SETTINGS_CALENDAR, Place.SETTINGS_DATA, Place.SETTINGS_PRIVACY ->
            link.place.settingsPage()?.let { SettingsLinks.open(it, link.focus) }
    }
}

/** [UX-04] The route a [SettingsGo] opens; the calendar's own sheets are opened through [NoticeLinks] instead. */
private fun SettingsGo.route(): String? = when (this) {
    SettingsGo.WORK_STYLE -> Routes.workStyle()
    SettingsGo.PATTERN -> Routes.pattern()
    SettingsGo.SHIFT_TYPES -> Routes.shiftTypes()
    SettingsGo.EXTRA_KINDS -> Routes.extraKinds()
    SettingsGo.PAY -> Routes.pay()
    SettingsGo.PAY_BASE -> Routes.pay(focus = com.bamnat.app.ui.pay.PayFocus.BASE.key)
    SettingsGo.PAY_ITEMS -> Routes.pay(focus = com.bamnat.app.ui.pay.PayFocus.ITEMS.key)
    SettingsGo.PAY_PERIOD -> Routes.pay(focus = com.bamnat.app.ui.pay.PayFocus.PERIOD.key)
    SettingsGo.CALENDARS -> Routes.CALENDARS
    SettingsGo.DIAGNOSTICS -> Routes.diagnostics(false)
    SettingsGo.INQUIRY -> Routes.INQUIRY
    SettingsGo.BOARD -> Routes.companyBoard()
    SettingsGo.FEATURES -> Routes.FEATURES
    SettingsGo.PRO -> Routes.PRO
    SettingsGo.RECOMMENDED -> Routes.RECOMMENDED
    SettingsGo.PROFILE -> Routes.PROFILE
    SettingsGo.JOURNAL -> Routes.JOURNAL
    SettingsGo.RECIPES -> Routes.RECIPE_MINE
    SettingsGo.STUDIO -> Routes.studio()
    SettingsGo.SHARE, SettingsGo.RECEIVE, SettingsGo.SLEEP, SettingsGo.CALENDAR_IMPORT,
    SettingsGo.DUTY_ROSTER, SettingsGo.HEALTH_REPORT, SettingsGo.ATTENDANCE_IMPORT, SettingsGo.COMMUTE -> null
}

/**
 * [ALRM-25] 1.36 빌드 8 (F-106: 알림을 끈 폰은 알람이 소리만 나고 화면이 안 떠요, Android가 막아요): with a shift alarm on and
 * notifications off, the app asks once (and again a week after 나중에), saying why; 알람 설정 keeps its red line.
 */
@Composable
private fun AlarmNoticeAsk() {
    val context = LocalContext.current
    val c = context.appContainer
    val snap by c.scheduleRepository.snapshot.collectAsStateWithLifecycle(null)
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle(c.settingsRepository.current)
    var canPost by remember { mutableStateOf(com.bamnat.app.alarm.Notifications.canPost(context)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        canPost = com.bamnat.app.alarm.Notifications.canPost(context)
    }
    val prefs = remember { context.getSharedPreferences("device", android.content.Context.MODE_PRIVATE) }
    var later by remember { mutableStateOf(prefs.getLong("alarm_notice_later", 0L)) }
    val alarmsOn = settings.alarmsEnabled && snap?.shiftTypes?.any { it.alarmEnabled && it.alarms.isNotEmpty() } == true
    if (canPost || !alarmsOn || com.bamnat.core.time.Today.millis() - later < 7 * 86_400_000L) return
    val ask = com.bamnat.app.ui.components.rememberNotificationAsk()
    fun putOff() { later = com.bamnat.core.time.Today.millis(); prefs.edit().putLong("alarm_notice_later", later).apply() }
    AlertDialog(
        onDismissRequest = ::putOff,
        title = { Text("알람 화면을 띄우려면 알림 허용이 필요해요") },
        text = { Text("알림이 꺼져 있으면 근무 알람이 울려도 화면이 뜨지 않고 소리만 나요. 잠금 화면에서 바로 끄고 다시 울림을 누르려면 알림을 허용해 주세요.") },
        confirmButton = { TextButton(onClick = { putOff(); ask() }) { Text("허용하기") } },
        dismissButton = { TextButton(onClick = ::putOff) { Text("나중에") } },
    )
}

/**
 * Controls for a ringing alarm inside the app. The notification normally offers them, but it can
 * be hidden (notifications turned off), so the app itself must always be able to stop the sound.
 */
@Composable
private fun RingingBanner() {
    val ring by AlarmService.ringing.collectAsStateWithLifecycle()
    val current = ring ?: return
    val context = LocalContext.current
    // [ALRM-22] 1.35: an alarm that starts while the app is in use opens its ringing screen over it (Android shows only a
    // notice then); the banner stays for when the screen was left.
    androidx.compose.runtime.LaunchedEffect(current.triggerAtMillis, current.isSnooze) {
        // [ALRM-25] 1.36 빌드 7 (알람 화면에서 밀어 끄는 중 '응답 없음'): once per ring, never while the screen is up, so coming back
        // to the app does not open it again over itself and the two windows do not pass the focus back and forth.
        if (!AlarmService.openScreenOnce(current)) return@LaunchedEffect
        runCatching {
            context.startActivity(android.content.Intent(context, com.bamnat.app.alarm.AlarmActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(current.title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(AlarmService.describe(current), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = { AlarmService.snooze(context) }) { Text("다시 울림") }
            Button(onClick = { AlarmService.askToDismiss(context) }) { Text("끄기") }
        }
    }
}

/**
 * [COMM-02] The company is not there (or not the user's department): during first setup the work is
 * set up by hand; once set up, the user's own setup is shared under the company's name.
 */
private fun NavHostController.afterCompanyByHand(setup: Boolean, weekdays: Boolean, ready: Boolean, calendar: String = PersonalCalendar.MAIN) {
    when {
        setup -> navigate(Routes.pattern(weekdays = weekdays, setup = true, calendar = calendar)) { popUpTo(Routes.COMPANY_FIND) { inclusive = true } }
        ready -> navigate(Routes.companyReport(calendar)) { popUpTo(Routes.COMPANY_FIND) { inclusive = true } }
        else -> navigate(Routes.pattern(weekdays = weekdays, calendar = calendar)) { popUpTo(Routes.COMPANY_FIND) { inclusive = true } }
    }
}

/** [COMM-03] After 달라요: where the setup is corrected before the right one is shared. */
private fun NavHostController.fixCompanySetup(place: FixPlace, calendar: String = PersonalCalendar.MAIN) {
    when (place) {
        FixPlace.PAY -> navigate(Routes.pay(calendar)) { launchSingleTop = true }
        FixPlace.SHIFT_TYPES -> navigate(Routes.shiftTypes(calendar)) { launchSingleTop = true }
        FixPlace.PATTERN -> navigate(Routes.pattern(calendar = calendar)) { launchSingleTop = true }
        FixPlace.NONE -> navigate(Routes.companyReport(calendar)) { launchSingleTop = true }
    }
}

/**
 * [PLAZA-32][RCP-19] 광장: the joined company's 정보 광장 or the plaza of the calendar's kind of work, the 나눔 판 above it.
 * [onBack] null when it is the 광장 tab (1.35 [RCX-29]).
 */
@Composable
private fun PlazaHost(nav: NavHostController, container: AppContainer, calendarArg: String?, onBack: (() -> Unit)?, inHub: Boolean = false) {
    // [CALI-08] 1.30 BB: the 광장 of one calendar's company; with two calendars or more, chips above choose which.
    var calendar by rememberSaveable { mutableStateOf(companyCalendarOf(container, calendarArg)) }
    val company = remember(calendar) { container.companyOf(calendar) }
    val recipesOn by container.recipes.on.collectAsStateWithLifecycle()
    val chips: @Composable () -> Unit = {
        com.bamnat.app.ui.company.PlazaCalendarChips(container, calendar, onPick = { calendar = it },
            Modifier.padding(horizontal = 16.dp))
        // [RCP-19] 1.34 나눔 판 on every kind of work's 광장, while it is on (설정 › 나눔 판).
        // [HUB-01] In 마을 the recipes have their own 장터 next to 광장.
        if (!inHub && recipesOn && container.recipeApi.enabled) com.bamnat.app.ui.recipe.PlazaRecipeBlock(container, calendar,
            onOpen = { nav.navigate(Routes.recipes(calendar)) { launchSingleTop = true } },
            onMake = { nav.navigate(Routes.studio()) { launchSingleTop = true } },
            onItem = { code -> nav.navigate(Routes.recipe(code, calendar)) },
            onRequests = { nav.navigate(Routes.recipeRequests(calendar)) { launchSingleTop = true } },
        )
    }
    // [PLAZA-32][PLAZA-33] 1.27 AI: 광장 opens to the joined company's 정보 광장, or the plaza of the kind of work.
    val member by company.store.member.collectAsStateWithLifecycle()
    val style = remember(calendar) { container.calendars.styleOf(calendar, container.settingsRepository.current.workStyle) }
    val plan = remember(member, style) { com.bamnat.core.plaza.StylePlaza.plan(style, member) }
    CompositionLocalProvider(com.bamnat.app.ui.company.LocalCompanyCalendar provides calendar) {
    if (container.companyApi.enabled && plan.room != null) {
        val home = appViewModel(key = "plaza-home-$calendar-${plan.room}") { com.bamnat.app.ui.company.PlazaHomeViewModel(it, calendar) }
        com.bamnat.app.ui.company.PlazaHomeScreen(home, onBack = onBack,
            onAll = { nav.navigate(Routes.COMPANY_BOARD_ALL) { launchSingleTop = true } },
            onCompany = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false, calendar = calendar)) },
            onRates = { nav.navigate(Routes.PLAZA_RATES) { launchSingleTop = true } },
            onShare = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
            header = chips)
    } else {
        val vm = appViewModel(key = "company-board-$calendar") { CompanyBoardViewModel(it, calendar) }
        CompanyBoardScreen(
            vm = vm,
            onBack = onBack,
            onOpen = { id -> nav.navigate(Routes.company(id, setup = false, weekdays = false, calendar = calendar)) },
            onShare = { nav.navigate(Routes.companyReport(calendar)) { launchSingleTop = true } },
            onRates = { nav.navigate(Routes.PLAZA_RATES) { launchSingleTop = true } },
            onFind = if (container.companyApi.enabled && member == null) ({ nav.navigate(Routes.companyFind(calendar = calendar)) { launchSingleTop = true } }) else null,
            header = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    chips()
                    // [RCP-34] 1.34: 교대·일근 have a plaza of their own, one tap from the rule plaza.
                    if (container.companyApi.enabled) plan.alsoRoom?.let { room ->
                        com.bamnat.app.ui.company.StylePlazaLink(room) {
                            nav.navigate(Routes.stylePlaza(calendar)) { launchSingleTop = true }
                        }
                    }
                }
            },
        )
    }
    }
}

/**
 * [NAV-02] To a tab. From another tab it is a tab switch (each tab keeps its own screens) and the tab left is
 * remembered for back ([record]); from a screen opened over a tab, the tab goes on top of that screen, so back
 * returns to it instead of the screen being thrown away.
 */
private fun NavHostController.navigateTopLevel(route: String, record: Boolean = true) {
    val current = currentBackStackEntry?.destination?.route
    val fromTab = current == null || TopLevel.entries.any { it.route == current }
    if (!fromTab) {
        navigate(route) { launchSingleTop = true }
        return
    }
    if (record && current != null && current != route) TabHistory.left(current, route)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** [NAV-02] The tabs the user went through, latest last, for back. Kept for the app's run; opening the app again clears it. */
internal object TabHistory {
    private val stack = ArrayDeque<String>()
    private const val MAX = 10

    /** Left tab [from] for tab [to]: [to] is taken out further down, so going round the tabs does not grow the list. */
    fun left(from: String, to: String) {
        stack.remove(from)
        stack.remove(to)
        stack.addLast(from)
        while (stack.size > MAX) stack.removeFirst()
    }

    fun previous(): String? = stack.lastOrNull()

    fun back(): String? = stack.removeLastOrNull()

    fun clear() = stack.clear()
}

/** [NAV-02] Back between tabs and out of the calendar ([AppRoot]); what it does is set on every composition. */
private class TabBackCallback : androidx.activity.OnBackPressedCallback(false) {
    /** False hands back on to the callbacks under this one. */
    var onBack: () -> Boolean = { false }
    var dispatcher: androidx.activity.OnBackPressedDispatcher? = null

    override fun handleOnBackPressed() {
        if (onBack()) return
        isEnabled = false
        try { dispatcher?.onBackPressed() } finally { isEnabled = true }
    }
}

/**
 * Pops only if [route] is still on top, so a double tap on back or save (or a callback that
 * arrives after the user already left) can never pop the screen underneath.
 */
private fun NavHostController.popFrom(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}

/** [VIS-02] Tabs fade in over 150 ms after the old one fades out over 90 ms; other screens move over 250 ms. */
private const val TAB_IN_MILLIS = 150
/** [PERF-19] 1.28 AO: how long the calendar-import offer waits for the first month to settle. */
private const val FIRST_MONTH_WAIT_MILLIS = 3_000L
private const val TAB_OUT_MILLIS = 90
private const val SCREEN_MILLIS = 250

/** Both ends of the move are tabs, so it is a tab switch, not a screen opened from one. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    TopLevel.entries.any { it.route == initialState.destination.route } &&
        TopLevel.entries.any { it.route == targetState.destination.route }

/** [REL-16] The narrowest window that shows the day beside the month: the Fold's inner screen (707dp) does, 600dp does not. */
private const val CALENDAR_BESIDE_DP = 700
