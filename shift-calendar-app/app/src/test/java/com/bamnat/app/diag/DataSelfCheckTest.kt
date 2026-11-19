package com.bamnat.app.diag

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.data.db.DayEntryEntity
import com.bamnat.core.journal.JournalKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/**
 * 2.0.0 [RVL-71] (검토 I12b-01·U12-09) 데이터 점검 by itself once a week: what can be put right is (a day set to a kind of work that is gone
 * follows the pattern, as deleting a kind does) and said in '밤낮이 한 일'; what cannot is said once in a notice; off is off.
 */
@RunWith(AndroidJUnit4::class)
class DataSelfCheckTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private var now = Instant.parse("2026-11-12T03:00:00Z")  // a fixed clock for the check, not the phone's
    private val shade get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before fun setUp() {
        runBlocking { c.scheduleRepository.ensureDefaults() }
        Notifications.createChannels(app)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("data_self_check", 0).edit().clear().commit()
    }

    private fun lost(day: Long, start: Int? = null) = DayEntryEntity(
        epochDay = day, overrideMode = DayEntryEntity.MODE_SHIFT, overrideShiftTypeId = 987_654,
        customStartMinute = start, customEndMinute = start?.plus(60), customBreakMinutes = null, alarmSkipped = false,
    )

    @Test fun aDayPointingAtAGoneKindFollowsThePatternAndIsSaidInTheJournal() = runBlocking {
        val dao = c.database.dayEntryDao()
        dao.upsert(lost(20_400))
        dao.upsert(lost(20_401, start = 9 * 60))
        val check = DataSelfCheck(c, clock = { now })

        val s = check.runNow()

        assertEquals(2, s.fixedDays)
        assertTrue(s.problems.toString(), s.problems.isEmpty())
        assertNull("a day with nothing else of its own goes", dao.get(20_400))
        val kept = dao.get(20_401)!!
        assertEquals("its own times stay", 9 * 60, kept.customStartMinute)
        assertEquals(DayEntryEntity.MODE_NONE, kept.overrideMode)
        assertNull(kept.overrideShiftTypeId)
        assertTrue("데이터 점검 now says all is well", DataCheck.run(c).first { it.text.contains("바뀐 날") }.ok)
        val line = c.journal.load().last { it.kind == JournalKind.DATA_CHECK }
        assertEquals("2 0 0", line.detail)
        assertEquals("데이터 점검 · 지운 근무를 가리키던 날 2개 근무표대로", com.bamnat.core.journal.Journal.text(line))
        assertTrue(DataSelfCheck.words(s, today = java.time.LocalDate.of(2026, 11, 12), zone = java.time.ZoneOffset.ofHours(9))
            .startsWith("오늘 살펴봤어요 · 지운 근무를 가리키던 날 2개"))
        assertTrue("nothing to tell", shade.allNotifications.isEmpty())
    }

    @Test fun itRunsOnceAWeekAndNotWhenTurnedOff() = runBlocking {
        val check = DataSelfCheck(c, clock = { now })
        assertTrue("never run", check.due(now))
        check.runNow()
        assertFalse(check.due(now.plus(Duration.ofDays(6))))
        assertTrue(check.due(now.plus(Duration.ofDays(7))))
        c.settingsRepository.update { it.copy(dataSelfCheck = false) }
        try {
            assertFalse(check.due(now.plus(Duration.ofDays(30))))
            now = now.plus(Duration.ofDays(30))
            assertNull("off is off", check.runIfDue())
        } finally {
            c.settingsRepository.update { it.copy(dataSelfCheck = true) }
        }
    }

    @Test fun whatItCannotPutRightIsSaidOnceAndTheNoticeGoesWhenItIsRight() = runBlocking {
        var lines = listOf(DataCheck.Line(false, "추가 달력 파일이 없어요: 알바"), DataCheck.Line(false, "목록에 없는 달력 파일 1개가 있어요", note = true))
        val check = DataSelfCheck(c, clock = { now }, check = { lines })

        check.runNow()
        val n = shade.allNotifications.single()
        assertEquals(DataSelfCheck.TITLE, n.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("only the fault, never the note", "추가 달력 파일이 없어요: 알바", n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("추가 달력 파일이 없어요: 알바"), check.state.value.problems)

        app.getSystemService(NotificationManager::class.java).cancelAll()
        now = now.plus(Duration.ofDays(7))
        check.runNow()
        assertTrue("the same problem is not said again next week", shade.allNotifications.isEmpty())

        lines = listOf(DataCheck.Line(false, "추가 달력 파일이 없어요: 알바"))
        check.runNow()
        Notifications.post(app, Notifications.DATA_CHECK_ID, n)
        lines = listOf(DataCheck.Line(true, "추가 달력 1개: 파일이 모두 있어요"))
        check.runNow()
        assertTrue("put right: the notice goes", shade.allNotifications.isEmpty())
        assertTrue(DataSelfCheck.words(check.state.value, today = java.time.LocalDate.of(2026, 11, 19), zone = java.time.ZoneOffset.ofHours(9))
            .endsWith("이상 없음"))
    }

    @Test fun anotherProblemIsToldEvenAfterOne() {
        assertTrue(DataSelfCheck.tell(listOf("a"), emptyList()))
        assertFalse(DataSelfCheck.tell(listOf("a"), listOf("a")))
        assertTrue(DataSelfCheck.tell(listOf("a", "b"), listOf("a")))
        assertFalse(DataSelfCheck.tell(emptyList(), listOf("a")))
    }
}
