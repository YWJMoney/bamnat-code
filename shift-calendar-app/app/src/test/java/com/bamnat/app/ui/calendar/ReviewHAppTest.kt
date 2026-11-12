package com.bamnat.app.ui.calendar

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.CalendarPlaceStore
import com.bamnat.app.remote.RemoteFilesStore
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.remote.RemoteFiles
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 2.0.0 검토 갈래 H: 앱 전체를 띄워 보는 재현 시험(검토 01-달력). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewHAppTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    @After fun tearDown() {
        KoreanHolidays.use(null)
        RemoteFilesStore.holidays.value = KoreanHolidays.revision
        Today.reset()
    }

    /** [RVH-15] 검토 CAL-20: the day and month left come back when 밤낮 opens again within 6 hours, and today after that. */
    @Test fun theDayAndMonthLeftComeBackWithinSixHours() {
        val left = LocalDate.of(2026, 11, 12).atTime(9, 0).atZone(ZoneId.systemDefault())
        Today.fix(left)
        val store = CalendarPlaceStore(app)
        assertNull(store.month())
        store.keep(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 20))
        Today.fix(left.plusHours(5))
        assertEquals(YearMonth.of(2026, 12), CalendarPlaceStore(app).month())
        assertEquals(LocalDate.of(2026, 12, 20), CalendarPlaceStore(app).day())
        Today.fix(left.plusHours(7))
        assertNull(CalendarPlaceStore(app).month())
        assertNull(CalendarPlaceStore(app).day())
    }

    /** [RVH-14] 검토 CAL-19: a 임시공휴일 file that comes while the app is open gives every screen and the alarms a new snapshot. */
    @Test fun aNewHolidayFileMakesANewSnapshot() = runBlocking {
        val day = LocalDate.of(2027, 3, 2)
        assertNull(KoreanHolidays.nameOf(day))
        val before = c.scheduleRepository.snapshot.first()
        val y27 = (0 until 365L).map { LocalDate.of(2027, 1, 1).plusDays(it) }.mapNotNull { d -> KoreanHolidays.nameOf(d)?.let { d to it } }
            .joinToString(",") { """{"date":"${it.first}","name":"${it.second}"}""" }
        val file = RemoteFiles.parse(
            """{"format":1,"kind":"holidays","version":2027010101,"issued":"2026-12-20","minApp":34,"data":{"version":"2027.01a",
                "fromYear":2027,"toYear":2027,"days":[$y27,{"date":"2027-03-02","name":"임시공휴일"}]}}""".toByteArray(),
        )
        RemoteFilesStore.apply(file)
        val after = c.scheduleRepository.snapshot.first()
        assertEquals("임시공휴일", KoreanHolidays.nameOf(day))
        assertNotEquals(before, after)
        assertEquals(KoreanHolidays.revision, after.holidays)
    }
}
