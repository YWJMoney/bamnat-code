package com.bamnat.app.imports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.imports.DutyRosterViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-79] (검토 I07bc-01 '다음 달 근무표 왔어요' 두 번 누르기) A roster shared in says the month at a glance above the one button that
 * puts it in, and after it what else moved: the alarms, and the month's pay.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterSummaryTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val dec = YearMonth.of(2026, 12)

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
    }

    private val days = (1..10).joinToString(" ")

    @Test fun theMonthReadIsOneLineAndWhatMovedIsSaidAfter() = runBlocking {
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
        first.readText("2026년 12월 근무표\n$days\n김민지 주 주 야 야 휴 휴 주 주 야 야")
        idleUntil("the roster read") { first.ui.value.rows != null }
        val before = c.scheduleRepository.snapshot.first()
        val line = DutyRosterViewModel.summary(first.ui.value, before)
        assertTrue(line, line.contains("12월 10일"))
        assertTrue(line, line.endsWith("줄 · 12월 10일") || line.contains("지금 달력과 다른 날"))
        // What another test left in the band (it is the app's one band) is not this one's line.
        val earlier = com.bamnat.app.ui.RecentFill.entry.value
        first.apply()
        idleUntil("the month written") { runBlocking { c.scheduleRepository.snapshot.first() }.resolver.resolve(dec.atDay(10)).shiftType?.name == "야간" }
        idleUntil("the month said") { com.bamnat.app.ui.RecentFill.entry.value.let { it != null && it !== earlier && it.text.startsWith("2026년 12월") } }
        val said = com.bamnat.app.ui.RecentFill.entry.value!!.text
        assertTrue(said, said.contains(" · 알람도 맞췄어요"))

        // The same roster again, two days changed: the line says the two.
        val again = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
        again.readText("2026년 12월 근무표\n$days\n김민지 주 주 야 야 휴 휴 주 야 야 휴")
        idleUntil("the roster read again") { again.ui.value.rows != null }
        val snap = c.scheduleRepository.snapshot.first()
        val againLine = DutyRosterViewModel.summary(again.ui.value, snap)
        assertTrue(againLine, againLine.endsWith(" 줄 · 12월 10일 · 지금 달력과 다른 날 2일"))
    }

    @Test fun alarmsOffAreNotSaid() = runBlocking {
        c.settingsRepository.update { it.copy(alarmsEnabled = false) }
        try {
            val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
            vm.readText("2026년 12월 근무표\n$days\n야 야 휴 휴 주 주 야 야 휴 휴")
            idleUntil("the roster read") { vm.ui.value.rows != null }
            val firstSaid = com.bamnat.app.ui.RecentFill.entry.value
            vm.apply()
            idleUntil("the month said") { com.bamnat.app.ui.RecentFill.entry.value.let { it != null && it !== firstSaid } }
            val said = com.bamnat.app.ui.RecentFill.entry.value!!.text
            assertTrue(said, !said.contains("알람"))
        } finally {
            c.settingsRepository.update { it.copy(alarmsEnabled = true) }
        }
    }
}
