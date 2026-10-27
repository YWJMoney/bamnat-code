package com.bamnat.app

import android.app.Application
import android.content.Context
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
     * from the app's own jobs (AutoBackupJob 4401, PlazaNewsJob 4402, OutboxJob 4403·4404), so neither replaces the other.
     */
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setJobSchedulerJobIdRange(WORK_JOB_IDS.first, WORK_JOB_IDS.last)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        StartupTrace.begin()
        super.onCreate()
        CrashLog.install(this)
        // [QA130-01] Only the context; whether this is a Play pre-launch robot is read at the first server request.
        com.bamnat.app.diag.TestLab.attach(this)
        watchMainThread()
        channelsOnce()
        // While locked the database and settings cannot be opened; alarms ring from BootAlarms then,
        // and the container starts with the first use after unlocking (BOOT_COMPLETED at the latest).
        // [PERF-06] 364 (검토 A1): built on a thread of its own, so its files are read while the main thread
        // makes the window; the first use on the main thread waits for it (lazy is synchronized).
        if (!BootAlarms.isLocked(this)) building = Thread({ container }, "bamnat-container").apply { start() }
        StartupTrace.mark(StartupTrace.Step.APP)
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
        if (prefs.getInt(K_CHANNELS_FOR, 0) == BuildConfig.VERSION_CODE) return
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
}

private const val APP_STATE = "app_state"
private const val K_CHANNELS_FOR = "channels_for"
/** [NXT-11] WorkManager's JobScheduler ids; the app's own JobService ids are 4401~4404. */
internal val WORK_JOB_IDS = 100_000..199_999
private val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"

val Context.appContainer: AppContainer
    get() = (applicationContext as BamnatApp).container
