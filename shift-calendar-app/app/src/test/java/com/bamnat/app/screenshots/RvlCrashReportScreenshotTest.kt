package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.Inquiry
import com.bamnat.app.company.InquiryApi
import com.bamnat.app.company.InquiryInfo
import com.bamnat.app.company.InquiryKind
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVL-84] (검토 HQ-118) '앱이 갑자기 닫혔어요' → '멈춤 기록과 함께 알려 주기' → 보내기, followed to what goes to the server:
 * 1.39.0's four such questions came with the app, phone and Android only. The record must go with it, the stop first, and a check
 * that throws (1.39.0 closed the form then) sends the stop's record alone ([aCheckThatFailsStillSendsTheStop]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlCrashReportScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** The fake server, keeping what the app sent with the question. */
    private class Kept(private val inner: InquiryApi) : InquiryApi by inner {
        @Volatile var info: InquiryInfo? = null
        override suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo): Inquiry {
            this.info = info
            return inner.send(device, kind, text, info)
        }
        override suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo, requestId: String): Inquiry =
            send(device, kind, text, info)
    }

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private fun reported(fails: Throwable? = null, send: Boolean = true, wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        com.bamnat.app.ui.settings.InquiryViewModel.checkFails = fails
        val kept = Kept(FakeInquiryApi())
        c.inquiryApi = kept
        com.bamnat.app.diag.CrashLog.write(app, "main", IllegalStateException("stopped"), Today.millis() - 60_000)
        tab(1, wide = wide); tab(0, wide = wide)
        openNotice("불편한 점 알려 주기")
        reveal("앱이 갑자기 닫혔어요")
        tapText("멈춤 기록과 함께 알려 주기")
        typeInto("[앱이 갑자기 닫힘] ", "[앱이 갑자기 닫힘] 달력을 넘기다 닫혔어요")
        // Said right above 보내기 before it goes: the record goes with it.
        reveal("멈춤 기록이 함께 가요")
        assertTrue(appears("멈춤 기록이 함께 가요"))
        if (!send) { com.bamnat.app.diag.CrashLog.clear(app); return@style }
        tapText("보내기", exact = true)
        waitFor("the question sent") { kept.info != null }
        val diag = kept.info?.diag
        assertNotNull("멈춤 기록 없이 갔어요", diag)
        assertTrue(diag!!.take(80), diag.startsWith("[앱 멈춤"))
        // The newest stop's own record first, then the counts.
        assertTrue(diag.take(120), diag.lines()[1] == com.bamnat.app.ui.diag.NEWEST_STOP)
        assertTrue(diag.take(400), diag.contains("IllegalStateException"))
        // 1.39.0 closed here when a part of the check threw; 2.0.0 sends the stop's record and names what it could not read.
        if (fails != null) assertTrue(diag, diag.contains("\n\n[점검 못 읽음]\n(못 읽음) 나머지 점검: ${fails.javaClass.simpleName}@"))
        com.bamnat.app.ui.settings.InquiryViewModel.checkFails = null
        com.bamnat.app.diag.CrashLog.clear(app)
    }

    /** Opens 도움말과 문의 as 설정 › 문의하기 does. */
    private fun Screen.openHelp() {
        com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(help = true))
        settle()
    }

    /** PC-191: the card pressed and left without a word; 문의하기 later still finishes it with the record on. */
    private fun leftUnsent(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        c.inquiryApi = FakeInquiryApi()
        com.bamnat.app.diag.CrashLog.write(app, "main", IllegalStateException("stopped"), Today.millis() - 60_000)
        tab(1, wide = false); tab(0, wide = false)
        openNotice("불편한 점 알려 주기")
        reveal("앱이 갑자기 닫혔어요")
        tapText("멈춤 기록과 함께 알려 주기")
        assertTrue(appears("새 문의"))
        back()
        openHelp()
        assertTrue(appears("앱이 닫힌 것을 아직 알리지 않았어요"))
        tapText("이어 쓰기", exact = true)
        reveal("멈춤 기록이 함께 가요")
        assertTrue(appears("멈춤 기록이 함께 가요"))
        com.bamnat.app.diag.CrashLog.clear(app)
    }

    /** PC-191 ②: a stop of the last days, no draft: 문의하기 offers to tell it, with the record. */
    private fun toldFromHelp(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        c.inquiryApi = FakeInquiryApi()
        com.bamnat.app.diag.CrashLog.write(app, "main", IllegalStateException("stopped"), Today.millis() - 60_000)
        app.container.inquiryStore.clearDraft()
        openHelp()
        assertTrue(appears("최근에 앱이 닫힌 적이 있어요"))
        tapText("알리기", exact = true)
        reveal("멈춤 기록이 함께 가요")
        assertTrue(appears("멈춤 기록이 함께 가요"))
        com.bamnat.app.diag.CrashLog.clear(app)
    }

    @Test fun leftUnsentFoldLight() = shoot("rvl86_left_unsent_fold_light_100", FOLDED, leftUnsent())
    @Test fun toldFromHelpFoldLight() = shoot("rvl86_told_from_help_fold_light_100", FOLDED, toldFromHelp())

    @Test fun reportFoldLight() = shoot("rvl84_crash_report_fold_light_100", FOLDED, reported())

    /** The form as it stands before 보내기: the line says the stop's record goes with it. */
    @Test fun beforeSendFoldLight() = shoot("rvl84_before_send_fold_light_100", FOLDED, reported(send = false))
    @Test fun beforeSendFoldDarkBig() = shoot("rvl84_before_send_fold_dark_200", "w369dp-h905dp-port-night-420dpi", fontScale = 2f, reported(send = false))
    @Test fun beforeSendOpenLight() = shoot("rvl84_before_send_open_light_100", OPEN, reported(send = false, wide = true))

    /** HQ-118: the check this phone cannot read (a new Android's permission) no longer closes the form nor loses the stop. */
    @Test fun aCheckThatFailsStillSendsTheStop() =
        shoot("rvl84_crash_report_check_failed_fold_light_100", FOLDED, reported(SecurityException("denied")))

    /** A stop section long with counts and frozen moments still puts the newest stop's record first, under the cut. */
    @Test fun theNewestStopGoesFirstWhateverComesBefore() {
        val counts = (1..400).map { "  멈춘 곳 $it" }
        val stop = listOf("최근 30일: 멈춤 9회") + counts + listOf("남은 기록: 3개", com.bamnat.app.ui.diag.NEWEST_STOP, "11/18 21:04 IllegalStateException@a.b.c")
        val r = com.bamnat.app.ui.diag.DiagReport(listOf("알람 상태" to listOf("알람 사용: 켜짐"), com.bamnat.app.ui.diag.STOP_SECTION to stop))
        val text = r.stopFirst().asText().take(com.bamnat.app.company.InquiryClient.DIAG_MAX)
        assertTrue(text.take(200), text.startsWith("[앱 멈춤]\n${com.bamnat.app.ui.diag.NEWEST_STOP}\n11/18 21:04 IllegalStateException"))
    }

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val OPEN = "w707dp-h823dp-port-420dpi"
    }
}
