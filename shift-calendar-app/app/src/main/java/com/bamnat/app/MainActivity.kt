package com.bamnat.app

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
        setContent { shown.value?.let { Content(it) } }
        // 1.32.1 [STAB-10][STAB-11] 멈춤 관찰: the screen made again (a Fold, a theme) and every draw of the window.
        com.bamnat.app.diag.Trail.add(com.bamnat.core.diag.StopTrail.Kind.MADE,
            "restore=${if (savedInstanceState != null) 1 else 0} ${com.bamnat.app.diag.Trail.size(resources.configuration)}")
        window.decorView.viewTreeObserver.addOnDrawListener { com.bamnat.app.diag.MainStall.drew() }
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
            NoticeLinks.read(intent)
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
        val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
        // [SHFT-05] 야간 근무 중 어둡게 turns into dark or the phone's theme as the night shift starts and ends.
        val themeMode = com.bamnat.app.ui.theme.rememberThemeMode(settings.themeMode, container)
        val dark = when (themeMode) {
            ThemeMode.SYSTEM, ThemeMode.NIGHT_SHIFT -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // [SRV-08] P9: with 앱 잠금 on, the recents screen and screenshots show no calendar or money.
        DisposableEffect(settings.appLock) {
            AppLock.secure(window, settings.appLock)
            onDispose { }
        }
        // Status bar icons follow the app theme, not only the system one.
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
            )
            onDispose { }
        }
        // G 1.32 [WTYP-07] 오전/오후로 보기: every time on screen is written by Fmt, so the root is drawn again on a change.
        com.bamnat.app.ui.Fmt.clock12 = settings.clock12
        val photoLook = androidx.compose.runtime.remember(settings.photoLook) { com.bamnat.core.recipe.PhotoLook.decode(settings.photoLook) }
        BamnatTheme(themeMode = themeMode, dynamicColor = settings.dynamicColor, textStep = settings.textStep, compact = settings.compact,
            corners = photoLook.corners) {
            // [RCP-09] 1.34 달력 테마, lifted for the dark calendar.
            val darkSurface = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val look = androidx.compose.runtime.remember(settings.calTheme, darkSurface) {
                com.bamnat.app.ui.theme.CalendarLook.of(com.bamnat.core.recipe.CalendarTheme.decode(settings.calTheme), darkSurface)
            }
            androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.calendar.LocalExtraKinds provides settings.extraKinds,
                com.bamnat.app.ui.theme.LocalCalendarLook provides look, com.bamnat.app.ui.theme.LocalPhotoLook provides photoLook) {
                androidx.compose.runtime.key(settings.clock12) { AppRoot(windowSizeClass = calculateWindowSizeClass(this), container = container) }
                // U 1.32 [UX132-05] 처음 시작할 때 글자 크기를 골라요 (설정 › 화면과 글자에서 바꿔요).
                // A new phone picks it on the first screen; a phone updated to 1.32 is asked once over its calendar.
                // [J133-53] Not while the first setup (which offers the chip) is still open.
                val updated = androidx.compose.runtime.remember {
                    val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                    info != null && info.firstInstallTime != info.lastUpdateTime
                }
                val consent by container.consentStore.seen.collectAsStateWithLifecycle()
                // [J133-87] A size already picked (settings, or the first screen of 1.32) is an answer too.
                if (!settings.textAsked && settings.textStep == com.bamnat.app.data.AppSettings().textStep && updated && consent != null && settings.workStyle != null) com.bamnat.app.ui.settings.TextSizeFirstAsk(settings.textStep,
                    onPick = { step -> container.settingsRepository.update { it.copy(textStep = step) } },
                    onDone = { container.settingsRepository.update { it.copy(textAsked = true) } })
                // [LOCK-01] Over the app, so the screen the user was on is still there after.
                val locked by AppLock.locked.collectAsStateWithLifecycle()
                if (locked) com.bamnat.app.ui.LockCover { AppLock.ask(this, confirmLock::launch) }
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
        IncomingRoster.read(intent) // [ROST-09] shared while the app was open
        // [SLP-15] A tapped notice opens its own screen, so coming back is not sent to the calendar.
        if (NoticeLinks.read(intent)) HomeRequests.followingLink()
    }

    /** [SHR-02] A shared calendar link tapped in a message or on the shared page: the calendar offers it. */
    private fun openLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // Reopened from Recents, Android hands back the link the app was first opened with: it was offered then.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // [RCP-22] 1.34: a recipe link (bamnat://r/CODE from the recipe's public page).
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
        // G 1.32 [WTYP-01] No banner right after the app comes to the front.
        com.bamnat.app.ui.components.AdMoment.opened(com.bamnat.core.time.Today.instant())
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
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // [STAB-11] Taps that change nothing on screen are counted (no place, only that a finger lifted).
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) com.bamnat.app.diag.MainStall.tapped()
        return super.dispatchTouchEvent(ev)
    }

    private fun started(c: AppContainer) {
        startDone = true
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
        }
        // [MON-02] The server's answer, passes and Play's purchases. [MON-07] Google's consent form and
        // the ads SDK start only when an ad may show: [FLAG-09] with the `ads` row on, without 프로, with ad ids, and
        // [SRV-07] not in the first three days after install.
        c.syncPro()
        if (com.bamnat.core.tier.AdRules.sdkMayStart(c.flags.on(com.bamnat.core.flags.FlagCatalog.ADS), c.featureGate.state.value.allows(com.bamnat.app.tier.Feature.NO_ADS),
                c.installedAt, Today.instant(), c.remoteConfig.current.adQuietDays)) c.ads.start(this)
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
    }

    private companion object {
        const val CATCH_UP_EVERY = 30 * 60 * 1000L
        @Volatile var lastCatchUp = 0L
        const val KEY_AWAITING = "awaiting_result"
        const val KEY_LEFT_AT = "left_at"
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
