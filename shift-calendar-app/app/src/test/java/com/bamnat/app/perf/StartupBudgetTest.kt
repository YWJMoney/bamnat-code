package com.bamnat.app.perf

import android.Manifest
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.AppContainer
import com.bamnat.app.BamnatApp
import com.bamnat.app.MainActivity
import com.bamnat.app.StartupTrace
import com.bamnat.core.consent.ConsentItem
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [PERF-01] The startup budget, checked without a phone: what a cold start builds and runs before the
 * calendar's month is on screen. Times here are the JVM's, many times a phone's, so their limits are
 * loose and only catch a step that grew badly; the counts are exact and catch new work added to the start.
 * A real phone's cold start is measured by hand (사용자 할 일), or by a Macrobenchmark once there is a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StartupBudgetTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    /** [QA-09] A Compose dispatch dropped at the end of an earlier test in this JVM is posted again. */
    @org.junit.Before fun mainThread() = com.bamnat.app.rules.ComposeMainReset.now()

    /** A shift worker who finished the first setup: the app opens on the calendar. */
    private fun settledUser(c: AppContainer) = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        c.consentStore.markSeen(ConsentItem.entries)
        c.scheduleRepository.ensureDefaults()
        val types = c.scheduleRepository.shiftTypes.first().associateBy { it.name }
        val ids = listOf("주간", "주간", "야간", "야간", "휴무").map { types.getValue(it).id }
        c.scheduleRepository.savePattern(WorkPattern.Rotation(LocalDate.now().minusDays(10), ids))
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
    }

    /** Runs the main thread until the calendar has its month, or [limitMillis] of waiting passed. */
    private fun untilCalendar(limitMillis: Long) {
        val end = System.currentTimeMillis() + limitMillis
        while (StartupTrace.millis(StartupTrace.Step.CALENDAR) == null && System.currentTimeMillis() < end) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** What the activity shows, for a failure that says where the start stopped instead of the month. */
    private fun ActivityScenario<MainActivity>.shown(): String {
        var words = ""
        onActivity { a ->
            fun root(v: android.view.View): androidx.compose.ui.platform.ViewRootForTest? {
                if (v is androidx.compose.ui.platform.ViewRootForTest) return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) root(v.getChildAt(i))?.let { return it }
                return null
            }
            words = root(a.window.decorView)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true).orEmpty()
                .flatMap { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text } }
                .joinToString(" | ").take(600)
        }
        val c = app.container
        return "style=${c.settingsRepository.current.workStyle} draft=${c.setupDrafts.draft.value} consent=${c.consentStore.seen.value} " +
            "trace=${StartupTrace.Step.entries.associateWith { StartupTrace.millis(it) }} screen: $words"
    }

    /** [PERF-10] Runs the main thread (its idle moments start the next group) until every waiting group started. */
    private fun untilAllGroups(c: AppContainer, limitMillis: Long = 15_000) {
        val end = System.currentTimeMillis() + limitMillis
        while (c.laterGroups < 3 && System.currentTimeMillis() < end) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun lazyBuilt(c: AppContainer, name: String): Boolean {
        val field = AppContainer::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }
        return (field.get(c) as Lazy<*>).isInitialized()
    }

    @Test fun theCalendarOpensWithinItsBudget() {
        val c = app.container
        val built = StartupTrace.millis(StartupTrace.Step.CONTAINER)
        assertNotNull("the container is built while the app starts", built)
        assertTrue("container and its start took $built ms (budget $CONTAINER_BUDGET_MS)", built!! <= CONTAINER_BUDGET_MS)
        settledUser(c)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            untilCalendar(limitMillis = 30_000)
            val activity = StartupTrace.millis(StartupTrace.Step.ACTIVITY)
            val calendar = StartupTrace.millis(StartupTrace.Step.CALENDAR)
            assertNotNull("the activity started", activity)
            assertNotNull("the calendar showed its month: ${scenario.shown()}", calendar)
            val toMonth = calendar!! - activity!!
            // [PERF-09] 1.28 AO: when the screen had the container; here it was built before the screen, so no wait.
            val reached = StartupTrace.millis(StartupTrace.Step.CONTAINER_WAIT)
            assertNotNull("[PERF-09] the screen noted when it had the container", reached)
            assertTrue("the container reached the screen before its month", reached!! <= calendar)
            // Kept in the test report (system-out) so a slower start shows before it fails.
            println("[PERF-01] container ${built} ms, activity to month ${toMonth} ms, screen had the container at ${reached - activity} ms")
            assertTrue("activity to month took $toMonth ms (budget $FIRST_MONTH_BUDGET_MS)", toMonth <= FIRST_MONTH_BUDGET_MS)
            // [PERF-15] The opening was kept for 진단, as a cold one (this process started for it).
            val last = c.startupStats.last
            assertNotNull("[PERF-15] the opening's times are kept on the phone", last)
            assertEquals(StartupTrace.Kind.COLD, last!!.kind)
        }
    }

    @Test fun theStartLeavesWhatItDoesNotNeedForLater() {
        val c = app.container
        settledUser(c)
        ActivityScenario.launch(MainActivity::class.java).use {
            untilCalendar(limitMillis = 30_000)
            // Before the 정식 출시 switch nothing is bought: Play's billing client is not even built.
            assertFalse("Play billing waits until it is used", lazyBuilt(c, "playBilling"))
            // Tools opened from settings or a menu. (The phone calendar importer is asked at every start whether
            // the user chose calendars to watch, by design: IMPORT-02; 최근 지운 기록 drops what is past 7 days: UNDO-01; an added calendar's setup left unsaved is dropped: MCAL-02.)
            for (name in listOf("importUndo", "companyShare")) {
                assertFalse("$name waits until it is used", lazyBuilt(c, name))
            }
        }
    }

    @Test fun theStartRunsOnlyItsBudgetOfJobs() {
        val c = app.container
        // The jobs that follow the data for as long as the app runs (alarms, notices, widgets, the watch …), counted
        // by name: one-off jobs of the start (a Pro check, the alarm check) end at their own pace and are not counted.
        c.afterFirstFrame()
        c.afterFirstFrame()
        // [PERF-10] 1.28 AO: right after the month only the first group (settings followers) is running.
        val atOnce = c.kept.toList() - c.keptBeforeLater!!.toSet()
        assertEquals("[PERF-10] the first group: $atOnce", setOf("today notice", "widgetweek", "widgetflow", "widgetmemo", "trialnotice"), atOnce.toSet())
        untilAllGroups(c)
        assertEquals("every waiting group started", 3, c.laterGroups)
        val first = c.keptBeforeLater!!
        assertTrue("[PERF-03] ${first.size} jobs before the first month (budget $FIRST_JOBS_BUDGET): $first", first.size <= FIRST_JOBS_BUDGET)
        assertTrue("the alarms are planned from the start", "schedule" in first && "bootcopy" in first)
        val jobs = c.kept.toList()
        assertEquals("each job is kept once", jobs.size, jobs.toSet().size)
        assertTrue("${jobs.size} jobs at start (budget $JOBS_BUDGET): a new one belongs in this budget on purpose: $jobs", jobs.size <= JOBS_BUDGET)
        assertTrue("the start does follow the data", jobs.size >= 10)
    }

    /** [PERF-03] The calendar's month comes first: what can wait starts once it is on screen. */
    @Test fun whatCanWaitStartsOnceTheMonthIsThere() {
        val c = app.container
        settledUser(c)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            untilCalendar(limitMillis = 30_000)
            assertNotNull("the calendar showed its month: ${scenario.shown()}", StartupTrace.millis(StartupTrace.Step.CALENDAR))
            val before = c.keptBeforeLater!!
            assertTrue("${before.size} jobs before the month (budget $FIRST_JOBS_BUDGET): $before", before.size <= FIRST_JOBS_BUDGET)
            // [PERF-10] The network, phone-calendar and watch group waits for idle moments after the month.
            assertFalse("the watch does not start with the month: ${c.kept}", "watch" in c.kept)
            assertTrue("the widgets follow the data from the month on", "widgetweek" in c.kept)
            untilAllGroups(c)
            assertTrue("widgets and the watch follow the data after the month", "watch" in c.kept && "widgetweek" in c.kept)
            assertTrue("money before the network group", c.kept.indexOf("money") < c.kept.indexOf("watch"))
        }
    }

    /** [PERF-03] Started for an alarm or a widget, with no calendar to wait for, the jobs still start. */
    @Test fun withoutACalendarTheWaitingJobsStartAnyway() {
        val c = app.container
        val end = System.currentTimeMillis() + 20_000
        while ("watch" !in c.kept && System.currentTimeMillis() < end) Thread.sleep(50)
        assertTrue("the waiting jobs started without a calendar: ${c.kept}", "watch" in c.kept)
    }

    /** [PERF-15] 1.28 AO: what an opening is: cold for a new process's first screen, warm later, none for a turn or a stop. */
    @Test fun openingsAreToldApart() {
        StartupTrace.begin()
        StartupTrace.activityCreated(restoring = false)
        StartupTrace.containerReached(30_000_000)
        val cold = StartupTrace.monthDrawn()
        assertEquals(StartupTrace.Kind.COLD, cold!!.kind)
        assertEquals(30L, cold.containerWait)
        assertEquals("the second month drawn is no opening", null, StartupTrace.monthDrawn())
        // A turn of the phone makes the screen again: no opening.
        StartupTrace.activityCreated(restoring = true)
        assertEquals(null, StartupTrace.monthDrawn())
        // The app brought back while its process ran: warm, timed from the screen.
        StartupTrace.activityCreated(restoring = false)
        StartupTrace.containerReached(0)
        val warm = StartupTrace.monthDrawn()
        assertEquals(StartupTrace.Kind.WARM, warm!!.kind)
        assertEquals(0L, warm.activity)
        // Left before the month (another tab first, back pressed): nothing.
        StartupTrace.activityCreated(restoring = false)
        StartupTrace.activityStopped()
        assertEquals(null, StartupTrace.monthDrawn())
    }

    @Test fun eachStepIsNotedOnce() {
        StartupTrace.begin()
        StartupTrace.mark(StartupTrace.Step.ACTIVITY)
        val first = StartupTrace.millis(StartupTrace.Step.ACTIVITY)
        Thread.sleep(5)
        StartupTrace.mark(StartupTrace.Step.ACTIVITY)
        assertEquals(first, StartupTrace.millis(StartupTrace.Step.ACTIVITY))
        assertEquals(null, StartupTrace.millis(StartupTrace.Step.CALENDAR))
    }

    private companion object {
        /**
         * Measured on the build machine (Robolectric, cold JVM) on 2026-10-22, with room for a slower CI:
         * see 계획/1.22-F-품질-성능-접근성-개발계획.md for the numbers these came from.
         */
        const val CONTAINER_BUDGET_MS = 4_000L
        const val FIRST_MONTH_BUDGET_MS = 8_000L
        /**
         * The jobs [AppContainer.start] keeps running today: 17 after 1.22 묶음 B, and since 1.23 [PERF-03] the four
         * that were started without a name (메모 일정, 투자 알림·월말, 출퇴근) are counted too, so 21 with no new job.
         * Raising it is a decision, not an accident. 1.32 조사 대조 [WTYP-11][WTYP-16][WTYP-29]: 22, one job ("datenotices")
         * for 희망 오프 마감, 퇴근 시각 알림 and 코인 12월 31일 together, started with the later groups, after the month.
         */
        const val JOBS_BUDGET = 22
        /** [PERF-03] Of those, the ones that start before the calendar's first month: alarms, notices, sleep, backup. */
        const val FIRST_JOBS_BUDGET = 7
    }
}
