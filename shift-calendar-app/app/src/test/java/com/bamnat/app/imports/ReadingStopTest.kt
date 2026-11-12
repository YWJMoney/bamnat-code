package com.bamnat.app.imports

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.pay.OcrModel
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.readingDone
import com.bamnat.app.ui.imports.readingWords
import com.bamnat.core.imports.RosterWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-31][RVL-32][RVL-33] (검토 SCAN-03·SCAN-04·SCAN-05·U07b-01·U07b-02) 한 달 근무 넣기 while a photo is read: 멈추기 stops the
 * reading itself (not only the spinner), the reading model's failures say why with the one fix their cause asks for, and a photo
 * that gave nothing leaves the three ways on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReadingStopTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
    }

    @After fun noFake() {
        RosterScanner.fake = null
        RosterScanner.fakeReads = null
        OcrModel.forceOnline = null
    }

    @Test fun stoppingAReadingStopsItsWork() {
        val started = CompletableDeferred<Unit>()
        var stopped = false
        RosterScanner.fake = {
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped = true }
        }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        assertTrue(vm.ui.value.reading)
        idleUntil("the reading under way") { started.isCompleted }
        vm.stopReading()
        assertFalse(vm.ui.value.reading)
        assertNull(vm.ui.value.step)
        // The reading itself was cancelled: no way of it runs on behind the keypad (검토 SCAN-03).
        idleUntil("the reading cancelled") { stopped }
        // Nothing of it lands afterwards.
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(vm.ui.value.rows)
        assertNull(vm.ui.value.error)
    }

    @Test fun aNewReadingTakesThePlaceOfOneUnderWay() {
        val first = CompletableDeferred<Unit>()
        var firstStopped = false
        var calls = 0
        RosterScanner.fake = {
            if (calls++ == 0) {
                first.complete(Unit)
                try { awaitCancellation() } finally { firstStopped = true }
            } else emptyList<RosterWord>()
        }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/a.jpg"))
        idleUntil("the first reading under way") { first.isCompleted }
        vm.readPhoto(Uri.parse("content://test/b.jpg"))
        idleUntil("the first reading cancelled") { firstStopped }
        idleUntil("the second reading done") { !vm.ui.value.reading }
    }

    @Test fun aPhotoThatGaveNothingSaysWhyAndWhereToGo() {
        RosterScanner.fake = { emptyList() }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the reading done") { !vm.ui.value.reading }
        assertTrue(vm.ui.value.error!!.isNotBlank())
        // Nothing failed on the phone's side: no fix button, the three ways on under the card.
        assertNull(vm.ui.value.fix)
        // '직접 칠하기' puts the failure away, the mark on the first empty day.
        vm.byHand()
        assertNull(vm.ui.value.error)
        assertEquals(1, vm.ui.value.cursor)
    }

    @Test fun theModelNotThereSaysWhyWithItsFix() {
        // Each cause of a model that did not come down, in words, with the button for it (검토 SCAN-05·U07b-01).
        assertEquals(OcrModel.Fix.WIFI, OcrModel.fix(OcrModel.NotReady(OcrModel.Wait.NETWORK)))
        assertEquals(OcrModel.Fix.STORAGE, OcrModel.fix(OcrModel.NotReady(OcrModel.Wait.STORAGE)))
        assertEquals(OcrModel.Fix.PLAY, OcrModel.fix(OcrModel.NotReady(OcrModel.Wait.FAILED)))
        assertEquals(OcrModel.Fix.AGAIN, OcrModel.fix(OcrModel.NotReady(OcrModel.Wait.SLOW)))
        assertEquals(OcrModel.Fix.AGAIN, OcrModel.fix(OcrModel.NotReady(OcrModel.Wait.STOPPED)))
        assertNull(OcrModel.fix(IllegalStateException("x")))
        // Play services' own codes: no room, no network, anything else.
        assertEquals(OcrModel.Wait.STORAGE, OcrModel.failed(com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes.INSUFFICIENT_STORAGE))
        assertEquals(OcrModel.Wait.NETWORK, OcrModel.failed(com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR))
        assertEquals(OcrModel.Wait.FAILED, OcrModel.failed(com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes.MODULE_NOT_FOUND))
        // Each says what to do, and none is the old one-for-all "잠시 뒤".
        val texts = OcrModel.Wait.entries.map { it.text }
        assertEquals(texts.size, texts.toSet().size)
        assertTrue(OcrModel.Wait.NETWORK.text.contains("와이파이"))
        assertTrue(OcrModel.Wait.STORAGE.text.contains("150MB"))
        // In words for the screen, with the event log's code.
        assertEquals(OcrModel.Wait.NETWORK.text, OcrModel.said(app, OcrModel.NotReady(OcrModel.Wait.NETWORK)))
    }

    @Test fun aScannedPageWithoutTheModelSaysWhyAndItsFix() = runBlocking {
        RosterScanner.fakePicture = { throw OcrModel.NotReady(OcrModel.Wait.NETWORK) }
        try {
            val page = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
            val r = com.bamnat.app.ui.imports.RosterReader.pages(app, listOf(page), com.bamnat.core.imports.DutyCodes(emptyList()),
                nov.atDay(1)) { _, _ -> }
            assertEquals(OcrModel.Wait.NETWORK.text, r.error)
            assertEquals(OcrModel.Fix.WIFI, r.fix)
        } finally {
            RosterScanner.fakePicture = null
        }
    }

    @Test fun theReadingLineSaysHowFarItGot() {
        assertEquals("글자 읽기 준비물 받는 중 · 42%", readingWords(null, 42).first)
        assertEquals("글자 읽기 준비물 받는 중", readingWords(null, -1).first)
        assertEquals("휴대폰 안에서 읽는 중 · 5번 중 3번째", readingWords(3 to 5, null).first)
        assertEquals("휴대폰 안에서 읽는 중", readingWords(1 to 0, null).first)
        assertEquals(0.42f, readingDone(null, 42)!!, 0.001f)
        assertNull(readingDone(null, -1))
        assertEquals(0.6f, readingDone(3 to 5, null)!!, 0.001f)
        assertNull(readingDone(null, null))
    }

    @Test fun aRosterMemoByItsOldNameIsNotPutInTwice() = runBlocking {
        // 2.0.0 [RVL-34] (검토 U07b-14) '근무표 표시' now; a day that has it as '듀티표 표시' from before does not get it again.
        val day = nov.atDay(3)
        assertNotNull(c.memoRepository.addQuickOnce(day, "듀티표 표시: N*"))
        assertNull(c.memoRepository.addQuickOnce(day, "근무표 표시: N*", alsoAs = listOf("듀티표 표시: N*")))
        assertNotNull(c.memoRepository.addQuickOnce(nov.atDay(4), "근무표 표시: N*", alsoAs = listOf("듀티표 표시: N*")))
        Unit
    }

    @Test fun offlineIsToldApartFromOnline() {
        OcrModel.forceOnline = false
        assertFalse(OcrModel.online(app))
        OcrModel.forceOnline = true
        assertTrue(OcrModel.online(app))
    }
}
