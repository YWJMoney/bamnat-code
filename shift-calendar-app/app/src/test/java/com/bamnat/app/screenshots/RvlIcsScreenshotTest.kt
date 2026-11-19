package com.bamnat.app.screenshots

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.imports.CalendarImporter
import com.bamnat.app.imports.CalendarSource
import com.bamnat.app.ui.imports.CalendarImportViewModel
import com.bamnat.core.imports.CalendarImport
import com.bamnat.core.imports.DeviceCalendar
import com.bamnat.core.imports.DeviceEvent
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 2.0.0 [RVL-73] (검토 I07bb-05) 갈래 L 10차의 화면 (디자인 품질 조건): 캘린더에서 가져오기의 '달력 파일(.ics)로 가져오기' — 캘린더 권한 없이 다른
 * 앱이 내보낸 파일을 읽어 근무·휴가·메모로 보여 주고, .ics가 아닌 파일은 까닭과 다른 파일 고르기. 밝은·어두운 × 글자 100%·200% × 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlIcsScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private fun day(plus: Long) = Today.date().plusDays(plus).format(DateTimeFormatter.BASIC_ISO_DATE)

    private fun ics() = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" +
        "BEGIN:VEVENT\r\nUID:n1\r\nSUMMARY:야간\r\nDTSTART:${day(2)}T190000\r\nDTEND:${day(3)}T070000\r\nRRULE:FREQ=DAILY;INTERVAL=4;COUNT=3\r\nEND:VEVENT\r\n" +
        "BEGIN:VEVENT\r\nUID:n2\r\nSUMMARY:연차\r\nDTSTART;VALUE=DATE:${day(5)}\r\nEND:VEVENT\r\n" +
        "BEGIN:VEVENT\r\nUID:n3\r\nSUMMARY:치과 예약\r\nDTSTART:${day(6)}T150000\r\nDTEND:${day(6)}T160000\r\nLOCATION:역 앞\r\nEND:VEVENT\r\n" +
        "END:VCALENDAR\r\n"

    /** A phone without the calendar permission: the file is the way in. */
    private fun fromFile(text: String, name: String, then: Screen.(CalendarImportViewModel) -> Unit): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        val source = object : CalendarSource {
            override fun allowed() = false
            override fun calendars() = emptyList<DeviceCalendar>()
            override fun events(calendarIds: Set<Long>, from: LocalDate, toInclusive: LocalDate) = emptyList<DeviceEvent>()
        }
        val importer = CalendarImporter(app, source, c.scheduleRepository, c.memoRepository, c.database.importedEventDao())
        val vm = CalendarImportViewModel(c, importer)
        content { com.bamnat.app.ui.imports.CalendarImportScreen(vm, onBack = {}) }
        settle()
        assertTrue(appears("달력 파일(.ics)로 가져오기"))
        val f = File(app.cacheDir, name).apply { writeText(text) }
        vm.readFile(Uri.fromFile(f))
        waitFor("the file read") { vm.ui.value.proposals != null }
        then(vm)
        settle()
    }

    private fun read(): Screen.() -> Unit = fromFile(ics(), "근무표.ics") { vm ->
        val p = vm.ui.value.proposals!!
        assertEquals(3, p.count { it.event.title == "야간" })
        assertTrue(p.any { it.kind == CalendarImport.As.LEAVE })
        assertTrue(p.any { it.kind == CalendarImport.As.MEMO })
        assertTrue(appears("‘근무표.ics’에서 읽은 일정이에요"))
    }

    @Test fun readFoldLight() = shoot("rvl73_ics_fold_light_100", FOLDED, read())
    @Test fun readFoldDarkHuge() = shoot("rvl73_ics_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, read())
    @Test fun readOpenLight() = shoot("rvl73_ics_open_light_100", UNFOLDED, read())

    private fun notIcs(): Screen.() -> Unit = fromFile("이름,1,2,3\n김민지,D,E,N", "근무표.csv") { vm ->
        assertEquals(CalendarImportViewModel.NOT_ICS, vm.ui.value.fileProblem)
        assertTrue(appears("파일을 읽지 못했어요"))
    }

    @Test fun notIcsFoldLight() = shoot("rvl73_not_ics_fold_light_100", FOLDED, notIcs())
    @Test fun notIcsFoldDarkHuge() = shoot("rvl73_not_ics_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, notIcs())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
