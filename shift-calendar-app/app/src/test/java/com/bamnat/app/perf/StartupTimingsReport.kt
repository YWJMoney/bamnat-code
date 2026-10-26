package com.bamnat.app.perf

import android.Manifest
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.MainActivity
import com.bamnat.app.StartupTrace
import com.bamnat.core.consent.ConsentItem
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * [PERF-07][PERF-09] 1.28 AO: the start timed on the JVM for a user with three years of history (a changed day
 * every other day, extra work on every fourth), printed to the test report as `[AO]` lines. JVM numbers, many
 * times a phone's and noisy: only before/after on the same machine means anything (계획/1.28-AO 4장). It asserts
 * only that the month came, so it never fails on a slow machine.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StartupTimingsReport {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @org.junit.Before fun mainThread() = com.bamnat.app.rules.ComposeMainReset.now()

    private fun heavyUser() = runBlocking {
        val c = app.container
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        c.consentStore.markSeen(ConsentItem.entries)
        val repo = c.scheduleRepository
        repo.ensureDefaults()
        val types = repo.shiftTypes.first().associateBy { it.name }
        val ids = listOf("주간", "주간", "야간", "야간", "휴무").map { types.getValue(it).id }
        val today = LocalDate.now()
        repo.savePattern(WorkPattern.Rotation(today.minusYears(3), ids))
        val days = (0 until 3 * 365 step 2).map { today.minusDays(it.toLong()) }
        repo.updateDays(days) { it.copy(override = ShiftOverride.Shift(types.getValue("야간").id)) }
        val extraDays = (0 until 3 * 365 step 4).map { today.minusDays(it.toLong()) }
        repo.updateDays(extraDays) { it.copy(extras = it.extras + ExtraEntry(kindId = com.bamnat.core.model.ExtraKind.OVERTIME_ID, minutes = 90)) }
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
    }

    private fun millis(block: () -> Unit): Long {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1_000_000
    }

    private fun median(list: List<Long>) = list.sorted()[list.size / 2]

    @Test fun report() {
        heavyUser()
        val repo = app.container.scheduleRepository
        val month = YearMonth.now()
        // Five reads each after one to warm the JVM. (The line marked AO-WINDOW is taken out to run this on the old code.)
        val full = (0..5).map { millis { runBlocking { repo.snapshot.first() } } }.drop(1)
        val window: List<Long>? = (0..5).map { millis { runBlocking { repo.windowSnapshot(month) } } }.drop(1) // AO-WINDOW
        println("[AO] history rows: entries=${runBlocking { repo.snapshot.first().entries.size }}")
        println("[AO] whole-history snapshot read: median ${median(full)} ms of $full")
        println("[AO] window snapshot read: ${window?.let { "median ${median(it)} ms of $it" } ?: "not in this build"}")
        println("[AO] container built at ${StartupTrace.millis(StartupTrace.Step.CONTAINER)} ms")
        ActivityScenario.launch(MainActivity::class.java).use {
            val end = System.currentTimeMillis() + 30_000
            while (StartupTrace.millis(StartupTrace.Step.CALENDAR) == null && System.currentTimeMillis() < end) {
                Thread.sleep(5)
                shadowOf(Looper.getMainLooper()).idle()
            }
            val steps = StartupTrace.Step.entries.associateWith { StartupTrace.millis(it) }
            println("[AO] trace $steps")
            val activity = StartupTrace.millis(StartupTrace.Step.ACTIVITY)
            val calendar = StartupTrace.millis(StartupTrace.Step.CALENDAR)
            assertNotNull("the month came", calendar)
            println("[AO] activity to month ${calendar!! - activity!!} ms")
        }
    }
}
