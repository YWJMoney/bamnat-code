package com.bamnat.app.imports

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ReadFixBook
import com.bamnat.app.imports.RosterPhotoTest.Companion.roster
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.RosterHint
import com.bamnat.core.imports.RosterWord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-80] (검토 I07bc-02 '근무표 회사 틀 학습, 내 폰 안에서') The ward's sheet learned from the month put in: next month's photo of
 * the same sheet finds the user's line by itself when a letter of the name was misread, is read along last month's taps without asking
 * when its row of days was not found, and the common codes the user saw were right stop being yellow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterFormLearnTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

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

    @After fun noFake() { RosterScanner.fake = null; RosterScanner.fakeQuality = null }

    private fun List<RosterWord>.renamed(from: String, to: String) = map { if (it.text == from) it.copy(text = to) else it }

    private fun putIn(vm: DutyRosterViewModel) {
        vm.apply()
        idleUntil("the month put in") { vm.ui.value.applied != null }
        idleUntil("the form kept") { ReadFixBook.get(app).rosterForm(PersonalCalendar.MAIN) != null }
    }

    @Test fun aNameReadALetterOffIsStillMine() = runBlocking {
        RosterScanner.fake = { roster(1.0) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/nov.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        vm.pick(vm.ui.value.rows!!.indexOfFirst { it.label == "이수진" })
        putIn(vm)
        assertEquals(1, ReadFixBook.get(app).rosterForm(PersonalCalendar.MAIN)!!.rank)

        // December's sheet: the photo read "이수신". Before, '어느 줄이 내 것인가요?' again.
        RosterScanner.fake = { roster(-1.0).renamed("이수진", "이수신") }
        val next = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov.plusMonths(1))
        next.readPhoto(Uri.parse("content://test/dec.jpg"))
        idleUntil("the second photo read") { next.ui.value.rows != null && !next.ui.value.reading }
        assertTrue(next.ui.value.chosen)
        assertEquals("이수신", next.ui.value.row?.label)
        // The name remembered is still the one the user picked.
        assertEquals("이수진", c.dutyCodes.mine(PersonalCalendar.MAIN))
    }

    @Test fun aSheetWithNoRowOfDaysIsReadAlongLastMonthsTaps() = runBlocking {
        val words = roster(1.0, days = false)
        RosterScanner.fake = { words }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/nov.jpg"))
        idleUntil("the photo read") { !vm.ui.value.reading && vm.ui.value.scan != null }
        val dot = words.filter { it.text == "·" }.let { it[it.size / 2] }
        val mine = words.first { it.text == "최유리" }
        vm.readTapped(RosterHint(dot.centerX to dot.centerY, mine.centerX to mine.centerY))
        idleUntil("the tapped read") { vm.ui.value.rows?.singleOrNull()?.label == "최유리" }
        putIn(vm)
        val form = ReadFixBook.get(app).rosterForm(PersonalCalendar.MAIN)!!
        assertNotNull(form.myAt)
        assertNotNull(form.dayAt)

        // December's photo of the same sheet, taken a little lower and bigger: read along the taps, no question, no tap.
        RosterScanner.fake = { roster(-0.5, days = false).map { w -> w.copy(left = w.left * 11 / 10, right = w.right * 11 / 10, top = w.top * 11 / 10 + 60, bottom = w.bottom * 11 / 10 + 60) } }
        val next = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov.plusMonths(1))
        val said = mutableListOf<String>()
        val listen = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { next.messages.collect { said += it.message } }
        next.readPhoto(Uri.parse("content://test/dec.jpg"))
        idleUntil("the second photo read") { !next.ui.value.reading && next.ui.value.rows != null }
        listen.cancel()
        assertEquals("최유리", next.ui.value.row?.label)
        assertTrue(next.ui.value.chosen)
        assertTrue(said.toString(), said.contains("지난달 근무표처럼 읽었어요"))
    }

    @Test fun commonCodesSeenRightStopBeingYellow() = runBlocking {
        // D·N·O are common codes, matched to 주간·야간·휴무 by a guess: yellow until looked at.
        val codes = mapOf("주간" to "D", "야간" to "N", "휴무" to "O")
        RosterScanner.fake = { roster(1.0) { p, d -> codes.getValue(RosterPhotoTest.codeOf(if (p == 2) 0 else p, d)) } }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/nov.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        vm.pick(0)
        assertTrue(vm.ui.value.doubtful.isNotEmpty())
        vm.sameAsPhoto(true)
        idleUntil("the codes learned") { c.dutyCodes.of(PersonalCalendar.MAIN).keys.containsAll(setOf("D", "N")) }

        val next = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov.plusMonths(1))
        next.readPhoto(Uri.parse("content://test/dec.jpg"))
        idleUntil("the second photo read") { next.ui.value.rows != null && !next.ui.value.reading }
        assertTrue(next.ui.value.chosen)
        assertTrue(next.ui.value.row!!.guessed.toString(), next.ui.value.row!!.guessed.isEmpty())
    }

    /** 2.0.0 [RVL-81] (검토 I07bc-03) Right after a photo that would read better again: one soft line, the month read kept, and the tip gone. */
    @Test fun aBlurredShotIsSaidRightAfterAndKeptAsItIs() = runBlocking {
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        idleUntil("the tip known") { first.tip.value }
        RosterScanner.fake = { roster(1.0) }
        RosterScanner.fakeQuality = com.bamnat.core.imports.PhotoQuality.Report(40, listOf(com.bamnat.core.imports.PhotoQuality.Issue.BLURRY))
        first.readPhoto(Uri.parse("content://test/nov.jpg"), taken = true)
        idleUntil("the photo read") { first.ui.value.rows != null && !first.ui.value.reading }
        assertEquals(com.bamnat.core.imports.PhotoQuality.Issue.BLURRY, first.ui.value.check)
        assertEquals("조금 흐려요. 다시 찍으면 더 잘 읽어요", first.ui.value.check!!.soft)
        first.keepShot()
        assertEquals(null, first.ui.value.check)
        assertEquals(4, first.ui.value.rows!!.size)
        // A good photo says nothing.
        RosterScanner.fakeQuality = com.bamnat.core.imports.PhotoQuality.Report(90, emptyList())
        first.readPhoto(Uri.parse("content://test/nov2.jpg"), taken = true)
        idleUntil("the photo read again") { first.ui.value.rows != null && !first.ui.value.reading && first.ui.value.source != null }
        assertEquals(null, first.ui.value.check)
        first.pick(0)
        putIn(first)
        // The tip is for a calendar with no sheet put in from a photo yet ([DutyRosterViewModel.tip] reads the form).
        assertNotNull(ReadFixBook.get(app).rosterForm(PersonalCalendar.MAIN))
    }

    @Test fun eachIssueSaysItSoftly() {
        com.bamnat.core.imports.PhotoQuality.Issue.entries.forEach { i ->
            assertTrue(i.soft, i.soft.contains("더 잘 읽어요"))
            assertTrue(i.soft, listOf("주세요", "못", "잘못", "실패").none { it in i.soft })
            assertTrue(i.soft, i.soft.length <= 40)
        }
    }

    /** 2.0.0 [PC-185] '어느 줄(조)이 내 것인가요?': each day in its place ('·' for a blank), in the photo's own words. */
    @Test fun theLinePreviewKeepsItsPlacesAndThePhotosWords() {
        val row = com.bamnat.core.imports.RosterRow("2조", mapOf(1 to com.bamnat.core.imports.DutyCell.Off, 2 to com.bamnat.core.imports.DutyCell.Off,
            4 to com.bamnat.core.imports.DutyCell.Off), raw = mapOf(1 to "O", 2 to "O", 4 to "O"))
        assertEquals("1일부터 O O · O", com.bamnat.app.ui.imports.preview(row, emptyMap()))
    }

    /** 2.0.0 [PC-183] While '사진과 내용이 같은가요?' waits, its answer is the filled one; once answered, '넣기' is. */
    @Test fun theQuestionIsTheOneFilledButtonUntilAnswered() = runBlocking {
        RosterScanner.fake = { roster(1.0) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/nov.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        vm.pick(0)
        assertTrue(vm.ui.value.asking)
        vm.sameAsPhoto(true)
        assertEquals(false, vm.ui.value.asking)
    }
}
