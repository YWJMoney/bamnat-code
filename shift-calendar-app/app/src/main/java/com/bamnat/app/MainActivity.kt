package com.bamnat.app

import com.bamnat.app.ui.theme.safeLooks
import androidx.compose.ui.graphics.luminance
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.share.ShareLinkId
import com.bamnat.app.ui.AppRoot
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.diag.DiagEvents
import com.bamnat.core.time.Today

class MainActivity : ComponentActivity() {
    // [LOCK-01] The phone's own confirm screen before Android 11.
    private val confirmLock = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        AppLock.onConfirmed(it.resultCode == RESULT_OK)
    }

    /** 1.39 [LOOP-13] Play's update screens report back here; a declined urgent one is asked again on the next start. */
    private val updateFlow = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { }

    /**
     * [PERF-09] 1.28 AO (검토 6-1 병목 3): the container once this screen has it. Until then the window and Compose are
     * made (side by side with the container's own thread) and the splash screen stays; nothing of the app is drawn.
     */
    private val shown = androidx.compose.runtime.mutableStateOf<AppContainer?>(null)
    /** [PERF-09] [onStart]'s part ran for this start (it waits for the container too); [onStop]'s part only follows it. */
    private var startDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { shown.value == null }
        super.onCreate(savedInstanceState)
        StartupTrace.activityCreated(restoring = savedInstanceState != null)
        HomeRequests.onCreate(
            restoring = savedInstanceState != null,
            awaitingResult = savedInstanceState?.getBoolean(KEY_AWAITING) == true,
            leftAt = savedInstanceState?.getLong(KEY_LEFT_AT) ?: 0L,
        )
        enableEdgeToEdge()
        com.bamnat.app.ui.theme.AppFonts.init(this)
        // 1.38 PC 12차 충돌(11-12 22:00, 켠 지 4초): Fmt's 가리기 state was first made inside the first composition's snapshot and
        // then read from another one ("Reading a state that was created after the snapshot was taken"). Made here, outside any.
        com.bamnat.app.ui.Fmt.masked
        setContent { shown.value?.let { Content(it) } }
        // 1.32.1 [STAB-10][STAB-11] 멈춤 관찰: the screen made again (a Fold, a theme) and every draw of the window.
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.MADE,
            "restore=${if (savedInstanceState != null) 1 else 0} ${com.bamnat.app.diag.Trail.size(resources.configuration)}")
        // 1.36 PC A21 (안전 모드 중 휴대폰 글자 크기를 1.3배로 바꾸면 '멈춰 있던 적' 카드): made again after a setting changed, the
        // whole screen is drawn anew; that slow drawing is no stop.
        if (savedInstanceState != null) com.bamnat.app.diag.MainStall.redrawAhead("remade")
        window.decorView.viewTreeObserver.addOnDrawListener { com.bamnat.app.diag.MainStall.drew() }
        // 1.36 PC C22: how long the first screen took to draw after the app started, with the text size, for 진단.
        val firstDraw = java.util.concurrent.atomic.AtomicBoolean(false)
        window.decorView.viewTreeObserver.addOnDrawListener {
            if (shown.value != null && firstDraw.compareAndSet(false, true)) {
                val ms = android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
                com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.MADE,
                    "first_draw ms=$ms font=${resources.configuration.fontScale} wait=${StartupTrace.millis(StartupTrace.Step.CONTAINER_WAIT) ?: -1}")
            }
        }
        val app = application as BamnatApp
        val ready = app.readyContainer()
        if (ready != null) {
            StartupTrace.containerReached(0)
            open(ready, savedInstanceState)
        } else {
            // [PERF-09] The rest of the opening runs on the main thread as soon as the container is whole, in the same order.
            val waitFrom = System.nanoTime()
            lifecycleScope.launch {
                val c = app.awaitContainer()
                StartupTrace.containerReached(System.nanoTime() - waitFrom)
                open(c, savedInstanceState)
            }
        }
    }

    /** [PERF-09] What [onCreate] did with the container, then [onStart]'s part if the screen is already started, then the app. */
    private fun open(container: AppContainer, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            container.telemetry.event(DiagEvents.APP_OPEN)
            openLink(intent)
            NoticeLinks.read(this, intent)
            com.bamnat.app.logic.FxOutside.pressOpened(container, intent) // [RVM-14] a 위젯·타일·알림 단추 that opens a screen
            // [ROST-09] A shared roster photo or text first, so it never reads as a 회사 파일 or backup.
            if (!IncomingRoster.read(intent)) IncomingFiles.read(intent) // [CAL-47]
        }
        // [LOCK-01] 앱 잠금 is decided before the first frame of the app, as when onStart came after setContent.
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && !startDone) started(container)
        shown.value = container
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @androidx.compose.runtime.Composable
    private fun Content(container: AppContainer) {
        val stored by container.settingsRepository.settings.collectAsStateWithLifecycle()
        // 1.36 [SEC-06] 안전 모드: drawn with the app's own looks (a look from the 장터 may be what stopped it), the settings untouched.
        val safe by com.bamnat.app.diag.StartGuard.safe.collectAsStateWithLifecycle()
        val settings = if (safe) stored.safeLooks() else stored
        // [SHFT-05] 야간 근무 중 어둡게 turns into dark or the phone's theme as the night shift starts and ends.
        val themeMode = com.bamnat.app.ui.theme.rememberThemeMode(settings.themeMode, container)
        // 1.39 [FLOW-14] 밤 색: when chosen (잘 시간엔 앱 전체, not the default), warm and dark in the sleep planned around the work, while asleep and at 01~05 (설정 › 화면과 글자).
        val night = if (safe) false else com.bamnat.app.ui.theme.rememberAppNight(settings, container)
        val dark = night || when (themeMode) {
            ThemeMode.SYSTEM, ThemeMode.NIGHT_SHIFT -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // [SRV-08] P9: with 앱 잠금 on, the recents screen and screenshots show no calendar or money.
        DisposableEffect(settings.appLock) {
            AppLock.secure(window, settings.appLock)
            onDispose { }
        }
        // 2.0.0 [RVL-09] (검토 DATA-11) 금액 가리기 keeps the recents list from showing the app's amounts too.
        DisposableEffect(settings.hideAmounts, settings.appLock) {
            AppLock.hideRecents(this@MainActivity, settings.hideAmounts && !settings.appLock)
            onDispose { }
        }
        // Status bar icons follow the app theme, not only the system one.
        // 2.0.0 [RVIB-15] (PC-123) While the night screen (or the sleep page's night look) is up, dark bars with no pale scrim.
        val nightBars = com.bamnat.app.ui.components.DarkSystemBars.held > 0
        DisposableEffect(dark, nightBars) {
            if (nightBars) enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            ) else enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
            onDispose { }
        }
        // G 1.32 [WTYP-07] 오전/오후로 보기: every time on screen is written by Fmt, so the root is drawn again on a change.
        com.bamnat.app.ui.Fmt.clock12 = settings.clock12
        // 1.37 [LEDH-22] 가리기, set here so the first frame is already hidden (a SideEffect would show amounts for one frame).
        // Its state is made in onCreate, outside any snapshot (1.38 PC 12차 충돌).
        com.bamnat.app.ui.Fmt.masked = settings.hideAmounts
        val photoLook = androidx.compose.runtime.remember(settings.photoLook) { com.bamnat.core.recipe.PhotoLook.decode(settings.photoLook) }
        // [R200-01] 근무 그림 belong to one calendar's kinds of day: the shown calendar's are drawn.
        val shownCalendar by container.calendars.active.collectAsStateWithLifecycle()
        val shiftIcons = androidx.compose.runtime.remember(photoLook, shownCalendar) { com.bamnat.core.model.ShiftRef.of(photoLook.icons, shownCalendar) }
        // [STU-37] 1.36 근무에 따라 바뀌는 모양: 야간 근무 중·쉬는 날의 화면 색과 달력 색 over the usual ones.
        val shiftLooks = androidx.compose.runtime.remember(settings.shiftLooks) { com.bamnat.core.recipe.ShiftLooks.decode(settings.shiftLooks) }
        val shiftMode = com.bamnat.app.ui.theme.rememberShiftLookMode(shiftLooks, container)
        val (screensNow, calNow) = androidx.compose.runtime.remember(shiftLooks, shiftMode, settings.screenLooks, settings.calTheme) {
            shiftLooks.apply(shiftMode, settings.screenLooks, settings.calTheme)
        }
        // 1.36 [SEC-13] 보안 점검 M2: 안전 모드 draws without the blueprint (a received one may be what stopped the app); 평소대로 brings it back.
        val partsResting by com.bamnat.app.diag.StartGuard.safe.collectAsStateWithLifecycle()
        // 1.39 [LINK-03] 근무 입력·탭 연동 꾸밈 (대근·특근·연차 날 꾸밈, 새 수식 이름) is a 1.39 프로 장치: drawn with 프로, a 하루 써 보기
        // or while a work is tried on (입혀 보기 is free); otherwise those lines rest (kept in settings, back with 프로).
        val linkOn = com.bamnat.app.logic.rememberLinkOn(container)
        val motion = androidx.compose.runtime.remember(settings.motion) {
            com.bamnat.core.recipe.Motion.decode(settings.motion).effective(com.bamnat.app.ui.theme.systemAnimationsOff(this))
        }
        // 2.0.0 [RVM-21] MKT-32 (사용자 2026-11-12 'Google 요청은 글꼴을 고를 때만'): a theme tried on the real app draws its open fonts as card
        // fonts from our public files (or the phone's closest), never the whole font from Google Fonts; that comes with 입기.
        val trial by com.bamnat.app.ui.recipe.LiveTryOn.trial.collectAsStateWithLifecycle()
        val trialFonts = androidx.compose.runtime.remember(trial) {
            trial?.takeIf { it.showing && it.hasLook }?.let { t -> com.bamnat.app.ui.recipe.MarketFonts.ids(t.look.values.joinToString("|")) }
        }
        val trialCards = trialFonts?.let { ids ->
            androidx.compose.runtime.produceState(emptyMap<String, androidx.compose.ui.text.font.FontFamily>(), ids) {
                value = com.bamnat.app.ui.recipe.MarketFonts.ready(applicationContext, ids)
            }.value
        }
        androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.theme.LocalCardFonts provides trialCards) {
        BamnatTheme(themeMode = themeMode, dynamicColor = settings.dynamicColor, textStep = settings.textStep, compact = settings.compact,
            corners = photoLook.corners, accent = settings.accentColor, night = night,
            parts = androidx.compose.runtime.remember(settings.parts, partsResting) {
                if (partsResting) com.bamnat.core.recipe.Blueprint.EMPTY else com.bamnat.core.recipe.Blueprint.decode(settings.parts)
            }.let { bp -> androidx.compose.runtime.remember(bp, linkOn) { if (linkOn) bp else com.bamnat.core.formula.FxPro.restLink(bp) } }) {
            // [RCP-09] 1.34 달력 테마, lifted for the dark calendar.
            val darkSurface = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val look = androidx.compose.runtime.remember(calNow, darkSurface) {
                com.bamnat.app.ui.theme.CalendarLook.of(com.bamnat.core.recipe.CalendarTheme.decode(calNow), darkSurface)
            }
            androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.calendar.LocalExtraKinds provides settings.extraKinds,
                com.bamnat.app.ui.theme.LocalCalendarLook provides look, com.bamnat.app.ui.theme.LocalPhotoLook provides photoLook, com.bamnat.app.ui.theme.LocalShiftIcons provides shiftIcons,
                // [CVW-01][CVW-09] 1.35 달력 모양 and 날짜를 길게 누르면.
                com.bamnat.app.ui.calendar.LocalCalendarView provides androidx.compose.runtime.remember(settings.calView) { com.bamnat.core.recipe.CalendarView.decode(settings.calView) },
                com.bamnat.app.ui.calendar.LocalDayHold provides settings.longPress,
                // [FREE-04][FREE-05][FREE-06][FREE-07] 1.35 완전 자유 꾸미기.
                // 1.39 [FLOW-14] 밤 색 covers the screens' own grounds too (PC 5c4ad849: a light studio ground under night ink).
                com.bamnat.app.ui.theme.LocalScreenLooks provides androidx.compose.runtime.remember(screensNow, night) { com.bamnat.core.recipe.ScreenLooks.decode(if (night) null else screensNow) },
                com.bamnat.app.ui.theme.LocalMemoLook provides androidx.compose.runtime.remember(settings.memoLook) { com.bamnat.core.recipe.MemoLook.decode(settings.memoLook) },
                com.bamnat.app.ui.theme.LocalMoneyLook provides androidx.compose.runtime.remember(settings.moneyLook) { com.bamnat.core.recipe.MoneyLook.decode(settings.moneyLook) },
                com.bamnat.app.ui.theme.LocalPayLooks provides androidx.compose.runtime.remember(settings.payLooks) { com.bamnat.core.recipe.PayLook.decodeAll(settings.payLooks) },
                // [LAY-03] 1.36 꾸밈 층.
                com.bamnat.app.ui.decor.LocalDecor provides androidx.compose.runtime.remember(settings.decor, linkOn) {
                    com.bamnat.core.recipe.Decor.decode(settings.decor).let { if (linkOn) it else com.bamnat.core.formula.FxPro.restLink(it) }
                },
                // [LAY-11] 꾸밈 단추: a decoration that names a 규칙 단추 presses it (갈래 D's engine through DecorTap).
                com.bamnat.app.ui.decor.LocalDecorTap provides androidx.compose.runtime.remember(container) {
                    { id: String, date: java.time.LocalDate? -> com.bamnat.app.ui.decor.DecorTap.press(container, id, date ?: com.bamnat.core.time.Today.date()) }
                },
                // [MAT-07] 1.36 움직임, stopped by 움직임 끄기 or the phone's own 'remove animations'.
                com.bamnat.app.ui.theme.LocalMotion provides motion, com.bamnat.app.ui.theme.LocalMotionOff provides motion.off,
                // [FLOW-26] 1.38 설정 › 화면 › 칸이 보이기.
                com.bamnat.app.ui.components.LocalShowKani provides settings.showKani) {
                val ground = androidx.compose.runtime.remember(screensNow) { com.bamnat.core.recipe.ScreenLooks.decode(screensNow).of(com.bamnat.core.recipe.ScreenLooks.ALL).ground }
                    ?.let { androidx.compose.ui.graphics.Color(it.toInt()) } ?: androidx.compose.material3.MaterialTheme.colorScheme.background
                com.bamnat.app.ui.theme.WearTransition(screensNow, ground, motion) {
                    // 2.0.0 [RVP-21] No key on 오전/오후 any more: Fmt.clock12 is state, so only the times redraw.
                    AppRoot(windowSizeClass = calculateWindowSizeClass(this), container = container)
                }
                // U 1.32 [UX132-05] 처음 시작할 때 글자 크기를 골라요 (설정 › 화면과 글자에서 바꿔요).
                // A new phone picks it on the first screen; a phone updated to 1.32 is asked once over its calendar.
                // [J133-53] Not while the first setup (which offers the chip) is still open.
                val updated = androidx.compose.runtime.remember {
                    val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                    info != null && info.firstInstallTime != info.lastUpdateTime
                }
                val consent by container.consentStore.seen.collectAsStateWithLifecycle()
                // [J133-87] A size already picked (settings, or the first screen of 1.32) is an answer too.
                // 2.0.0 [RVP-22] With the other opening windows, one at a time (OpenAsk).
                if (!settings.textAsked && settings.textStep == com.bamnat.app.data.AppSettings().textStep && updated && consent != null && settings.setupDone &&
                    com.bamnat.app.ui.components.AskTurn.turn(com.bamnat.core.guide.OpenAsk.TEXT_SIZE)) com.bamnat.app.ui.settings.TextSizeFirstAsk(settings.textStep,
                    onPick = { step -> container.settingsRepository.update { it.copy(textStep = step) } },
                    onDone = { container.settingsRepository.update { it.copy(textAsked = true) } })
                // 1.39 [LOOP-13] The update downloaded behind the app: 다시 시작 when the user wants, never by itself.
                val updateReady by com.bamnat.app.review.InAppUpdate.ready.collectAsStateWithLifecycle()
                // 2.0.0 [RVP-22] (검토 U13b-08) Never over a sheet being filled in, and one opening window at a time (OpenAsk).
                if (updateReady && com.bamnat.app.ui.components.AskTurn.turn(com.bamnat.core.guide.OpenAsk.UPDATE_READY)) com.bamnat.app.ui.components.AlertDialog(
                    onDismissRequest = { com.bamnat.app.review.InAppUpdate.later() },
                    title = { androidx.compose.material3.Text("새 버전을 받았어요") },
                    text = { androidx.compose.material3.Text("다시 시작하면 바로 새 버전으로 열려요. 몇 초면 끝나고, 기록은 그대로예요.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { com.bamnat.app.review.InAppUpdate.restart() }) { androidx.compose.material3.Text("다시 시작") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { com.bamnat.app.review.InAppUpdate.later() }) { androidx.compose.material3.Text("나중에") } },
                )
                // [LOCK-01] Over the app, so the screen the user was on is still there after.
                val locked by AppLock.locked.collectAsStateWithLifecycle()
                if (locked) com.bamnat.app.ui.LockCover { AppLock.ask(this, confirmLock::launch) }
            }
        }
        }
    }

    // Every ActivityResult launcher (file pickers, permission requests) goes through here.
    @Deprecated("Called by the ActivityResult API")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        HomeRequests.awaitingResult = true
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
        // [ROST-09] shared while the app was open. 2.0.0 [RVL-14] (검토 DATA-06): a backup or a 회사 설정 file opened while the app was
        // open came here and was never read (the app only came forward); it is offered now as on a first open.
        if (!IncomingRoster.read(intent)) IncomingFiles.read(intent)
        // [SLP-15] A tapped notice opens its own screen, so coming back is not sent to the calendar.
        if (NoticeLinks.read(this, intent)) HomeRequests.followingLink()
        com.bamnat.app.logic.FxOutside.pressOpened(appContainer, intent) // [RVM-14]
    }

    /** [SHR-02] A shared calendar link tapped in a message or on the shared page: the calendar offers it. */
    private fun openLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // Reopened from Recents, Android hands back the link the app was first opened with: it was offered then.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // [RCP-22] 1.34: a recipe link (bamnat://r/CODE from the recipe's public page).
        // [PRF-11] 1.36: a maker's profile link (bamnat://m/<id> from the profile's public page).
        if (intent.data?.scheme == "bamnat" && intent.data?.host == "m") {
            com.bamnat.app.recipe.RecipeLinks.openMaker(com.bamnat.app.recipe.RecipeLinks.findMaker(intent.dataString)); return
        }
        if (intent.data?.scheme == "bamnat" && intent.data?.host == "r") {
            com.bamnat.app.recipe.RecipeLinks.open(com.bamnat.app.recipe.RecipeLinks.find(intent.dataString)); return
        }
        if (!appContainer.shareApi.enabled) return
        ShareLinkId.find(intent.dataString)?.let(appContainer.received::open)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_AWAITING, HomeRequests.awaitingResult)
        // [NAV-01] When the app went to the background, so a return soon after keeps this screen.
        outState.putLong(KEY_LEFT_AT, HomeRequests.leftAt)
    }

    override fun onStart() {
        super.onStart()
        // C 1.32 [STAB-07] The freeze watch runs only while the app is on screen.
        com.bamnat.app.diag.MainStall.start(this)
        // [PERF-09] Before the container is whole this part waits for it ([open] runs it).
        shown.value?.let(::started)
        // 1.32.1 [STAB-10][STAB-11] Back on screen: a line of 멈춤 관찰, and the next draw is waited for.
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.FRONT,
            "${com.bamnat.app.diag.Trail.size(resources.configuration)} lock=${if (AppLock.locked.value) 1 else 0}")
        com.bamnat.app.diag.MainStall.expectDraw()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        com.bamnat.app.diag.MainStall.focus(hasFocus)
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.FOCUS, if (hasFocus) "1" else "0")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // [STAB-10] A Fold opened or closed, the phone turned: the screen's new size.
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.LAYOUT, com.bamnat.app.diag.Trail.size(newConfig))
        com.bamnat.app.diag.MainStall.redrawAhead("config")
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // [STAB-11] Taps that change nothing on screen are counted (no place, only that a finger lifted).
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) com.bamnat.app.diag.MainStall.tapped()
        // 1.39 [SETUP-20] 첫 설정 누른 횟수: one finger down, only while a new install has not saved its first setup.
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) setupTaps?.tap()
        return super.dispatchTouchEvent(ev)
    }

    /** 1.39 [SETUP-20] Set once the container is there; taps before it (the splash) are not counted. */
    @Volatile private var setupTaps: com.bamnat.app.diag.SetupTapStats? = null

    private fun started(c: AppContainer) {
        startDone = true
        c.settingsRepository.current.let { s -> c.setupTaps.start(s.setupDone, s.setupLater) }
        setupTaps = c.setupTaps
        // [LOCK-01] Read before HomeRequests clears whether a screen the app opened is being returned from.
        AppLock.onStart(this, c.settingsRepository.settings.value.appLock, HomeRequests.awaitingResult)
        HomeRequests.onStart()
        c.verifyAlarms()
        // [TYPE-04] Shop days that passed with no sales become 임시휴무 when the app is opened.
        // [PERF-03] 324-2 (검토 A6): at most once in [CATCH_UP_EVERY]; a day turns over far less often than the app
        // comes back. 알람 점검 and the 정식 출시 switch below keep running every time (they keep their own pace).
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastCatchUp == 0L || now - lastCatchUp >= CATCH_UP_EVERY) {
            lastCatchUp = now
            c.appScope.launch { runCatching { com.bamnat.app.ui.work.ShopKeeper.catchUp(c) } }
            // [MAT-06] PC 확인 6차: shared fonts a theme was waiting for (the operator had not looked yet) come down by themselves.
            c.appScope.launch {
                val got = runCatching { com.bamnat.app.recipe.RecipeFonts.retryWaiting(c) }.getOrDefault(emptyList())
                // PC 확인 7차: a line over the calendar with 되돌리기, not a toast.
                if (got.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { com.bamnat.app.ui.calendar.FontArrivals.add(got) }
            }
        }
        // [MON-02] The server's answer, passes and Play's purchases. [MON-07] Google's consent form and
        // the ads SDK start only when an ad may show: [FLAG-09] with the `ads` row on, without 프로, with ad ids, and
        // [SRV-07] not in the first three days after install.
        // 1.36 PC 재검토: the first start of this process is an open afresh (the 정식 출시 switch is asked again sooner).
        c.syncPro(opened = !openedOnce).also { openedOnce = true }
        // 1.39 [LOOP-13] Play 인앱 업데이트, only after the first setup and as the server's numbers say.
        if (c.settingsRepository.settings.value.setupDone) c.remoteConfig.current.let { r ->
            com.bamnat.app.review.InAppUpdate.check(this, updateFlow,
                com.bamnat.app.review.InAppUpdate.due(BuildConfig.VERSION_CODE, r.updateUrgentMin, r.updateFlexibleMin))
        }
        if (com.bamnat.core.tier.AdRules.sdkMayStart(c.flags.on(com.bamnat.core.flags.FlagCatalog.ADS), c.featureGate.state.value.allows(com.bamnat.app.tier.Feature.NO_ADS),
                c.installedAt, Today.instant(), c.remoteConfig.current.adQuietDays)) c.ads.start(this)
    }

    override fun onResume() {
        super.onResume()
        // 2.0.0 [RVL-09] (검토 DATA-11) A screen's result came in just before this: the lock that waited for it closes now.
        AppLock.onResume()
    }

    override fun onStop() {
        super.onStop()
        com.bamnat.app.diag.MainStall.stop()
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.BACK, com.bamnat.app.diag.Trail.memoryNow())
        // [PERF-15] A screen gone before its month showed gives no startup sample.
        if (!isChangingConfigurations) StartupTrace.activityStopped()
        if (!startDone) return
        startDone = false
        HomeRequests.onStop(isChangingConfigurations)
        AppLock.onStop(isChangingConfigurations)
        // 2.0.0 [RVL-05] (검토 DATA-02) What Android's own backup takes is written as the app goes to the background: its backup runs
        // while the phone rests, so a setting changed today is in it.
        if (!isChangingConfigurations) appContainer.let { c -> c.appScope.launch { runCatching { c.phoneMove.keep() } } }
    }

    private companion object {
        const val CATCH_UP_EVERY = 30 * 60 * 1000L
        @Volatile var lastCatchUp = 0L
        /** 1.36 PC 재검토: this process already had its first start (later starts come back from the background). */
        @Volatile var openedOnce = false
        const val KEY_AWAITING = "awaiting_result"
        const val KEY_LEFT_AT = "left_at"
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
