package com.bamnat.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch
import com.bamnat.app.alarm.BootAlarms
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.diag.CrashLog

/**
 * [NXT-01] 1.29 AU (AO D8): also WorkManager's [androidx.work.Configuration.Provider]. Its androidx.startup initializer
 * is taken out of the manifest, so WorkManager (used by the ads SDK only) starts the first time someone asks for it,
 * never on a cold start that does not need it.
 */
class BamnatApp : Application(), androidx.work.Configuration.Provider {
    /** Built on first use: after a reboot the app may start before the phone is unlocked [REL-10]. */
    val container: AppContainer by lazy {
        AppContainer(this).also {
            it.start()
            StartupTrace.mark(StartupTrace.Step.CONTAINER)
            built.complete(it)
        }
    }
    @Volatile private var building: Thread? = null
    /** [PERF-09] 1.28 AO: the container once it is whole, for a screen that would rather not block waiting for it. */
    private val built = kotlinx.coroutines.CompletableDeferred<AppContainer>()

    /** [PERF-09] The container when it is already built, else null (it is being built, or not started yet while locked). */
    fun readyContainer(): AppContainer? = if (built.isCompleted) built.getCompleted() else null

    /**
     * [PERF-09] The container, waiting without blocking the caller's thread while the start's own thread builds it.
     * When no thread is building it (a locked phone just unlocked), it is built here, off the main thread.
     */
    suspend fun awaitContainer(): AppContainer =
        readyContainer() ?: if (building != null) built.await()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container }

    /**
     * [NXT-01] Built when WorkManager first starts. [NXT-11] Its JobScheduler ids are kept to a range of their own, away
     * from the app's own jobs (AutoBackupJob 4401, PlazaNewsJob 4402, OutboxJob 4403·4404, InquiryReplyJob 4405, StaffClockJob 4406, RosterFollowJob 4407), so neither replaces the other.
     */
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setJobSchedulerJobIdRange(WORK_JOB_IDS.first, WORK_JOB_IDS.last)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        StartupTrace.begin()
        super.onCreate()
        // 2.0.0 [RVP-28] (검토 UIX-51) Numbers are written as Korean writes them whatever the phone's language: "%02d" on a phone in
        // Nepali or Arabic gave its own digits, and "%.1f억" on a German phone "1,5억". Every String.format reads this category.
        keepKoreanNumbers()
        // 1.36 [SEC-10] The font look's isolated process starts nothing of the app's (it may not read the app's files).
        if (com.bamnat.app.recipe.FontProbe.inProbe()) return
        lastNight = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        CrashLog.install(this)
        // 1.32.1 [STAB-10] 멈춤 관찰 기록: the screen, the memory and how the app ended last time.
        com.bamnat.app.diag.Trail.install(this)
        // [QA130-01] Only the context; whether this is a Play pre-launch robot is read at the first server request.
        com.bamnat.app.diag.TestLab.attach(this)
        // [LAY-04][RUL-03] 1.36 꾸밈 층의 누르기는 규칙·단추의 단추를 눌러요 (갈래 C와 D를 잇는 곳).
        com.bamnat.app.ui.decor.DecorTap.buttons = { s -> com.bamnat.core.formula.Logic.decode(s.logic).buttons.map { it.id to "${it.emoji} ${it.label}" } }
        com.bamnat.app.ui.decor.DecorTap.press = { c, id, date -> com.bamnat.app.logic.LogicRunner.pressDecor(c, id, date) }
        // 1.39 [FX-03] 값 칸 numbers for 값() formulas, read from the container once it is there (nothing is read here).
        com.bamnat.app.logic.FxValues.attach { readyContainer() }
        // 1.36 [SEC-11] Play Integrity: nothing is asked here; the engine runs only when the server wants a verdict.
        com.bamnat.app.company.IntegrityProof.engine = { nonce -> playIntegrityToken(nonce) }
        watchMainThread()
        channelsOnce()
        // While locked the database and settings cannot be opened; alarms ring from BootAlarms then,
        // and the container starts with the first use after unlocking (BOOT_COMPLETED at the latest).
        // [PERF-06] 364 (검토 A1): built on a thread of its own, so its files are read while the main thread
        // makes the window; the first use on the main thread waits for it (lazy is synchronized).
        // C 1.32 [STAB-04] Counted before anything that could stop it; a phone still locked is not counted.
        if (!BootAlarms.isLocked(this)) com.bamnat.app.diag.StartGuard.begin(this)
        // 2.0.0 [RVL-45] (검토 SCAN-23) Pictures for reading left behind by an app closed meanwhile go, off the main thread.
        if (!BootAlarms.isLocked(this)) building = Thread({ container; runCatching { com.bamnat.app.imports.ScanCache.sweep(this) } }, "bamnat-container").apply { start() }
        StartupTrace.mark(StartupTrace.Step.APP)
    }

    /**
     * 2.0.0 [RVI-06] (검토 ALM-13) The phone turned light or dark: the widgets are drawn again, so their 꾸밈 그림 and the parts'
     * dark lines (drawn for one look) follow at once instead of at the next shift change, midnight or six hours on.
     */
    private var lastNight: Int? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // [RVP-28] A language change sets every locale category again.
        keepKoreanNumbers()
        val night = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val before = lastNight
        lastNight = night
        if (before == null || before == night) return
        val c = readyContainer() ?: return
        c.appScope.launch { runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(this@BamnatApp) } }
    }

    /** 1.36 [SEC-11] A Play Integrity token for [nonce], on the caller's (network) thread; null when Play cannot give one. */
    private fun playIntegrityToken(nonce: String): String? {
        if (isRobolectric) return null
        val request = com.google.android.play.core.integrity.IntegrityTokenRequest.builder().setNonce(nonce)
            .apply { BuildConfig.INTEGRITY_PROJECT.toLongOrNull()?.let { setCloudProjectNumber(it) } }.build()
        val task = com.google.android.play.core.integrity.IntegrityManagerFactory.create(this).requestIntegrityToken(request)
        return com.google.android.gms.tasks.Tasks.await(task, 20, java.util.concurrent.TimeUnit.SECONDS).token()
    }

    /**
     * [PERF-03] 324-2 (검토 A7): in a debug build the main thread says when it reads or writes the disk or the
     * network, in Logcat under StrictMode. Only a log: a tester's phone keeps working.
     */
    private fun watchMainThread() {
        if (!BuildConfig.DEBUG || isRobolectric) return
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build(),
        )
    }

    /**
     * [PERF-03] 324-2 (검토 A5): the notification channels are made when this version of the app first starts,
     * not at every start (that is several calls to the system). Only the app itself can delete a channel, and clearing
     * the app's data clears the mark with them.
     */
    private fun channelsOnce() {
        // Before the first unlock the app's own storage cannot be read: the channels are made, as before.
        if (BootAlarms.isLocked(this)) return Notifications.createChannels(this)
        val prefs = getSharedPreferences(APP_STATE, MODE_PRIVATE)
        // [TOWN-08] PC 확인 02af1eba (같은 판 번호로 덮어 깔면 새 소식 채널이 안 생겨요): a channel this version names but the phone
        // lacks is made all the same.
        if (prefs.getInt(K_CHANNELS_FOR, 0) == BuildConfig.VERSION_CODE && Notifications.channelsMade(this)) return
        Notifications.createChannels(this)
        prefs.edit().putInt(K_CHANNELS_FOR, BuildConfig.VERSION_CODE).apply()
    }

    /**
     * [QA-04] Never called on a phone (Android ends the process instead); Robolectric calls it when a test
     * ends. The app's background jobs stop with it, so they cannot act on the next test's data: an earlier
     * test's link sync or phone-calendar sync landing in a later test made ShareLinksProTest and
     * PhoneCalendarTest fail only in the whole-app run.
     */
    override fun onTerminate() {
        building?.join()
        if (containerStarted()) container.appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onTerminate()
    }

    private fun containerStarted() = (this::class.java.getDeclaredField("container\$delegate")
        .apply { isAccessible = true }.get(this) as Lazy<*>).isInitialized()

    private fun keepKoreanNumbers() = java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, java.util.Locale.KOREA)
}

private const val APP_STATE = "app_state"
private const val K_CHANNELS_FOR = "channels_for"
/** [NXT-11] WorkManager's JobScheduler ids; the app's own JobService ids are 4401~4404. */
internal val WORK_JOB_IDS = 100_000..199_999
private val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"

val Context.appContainer: AppContainer
    get() = (applicationContext as BamnatApp).container
