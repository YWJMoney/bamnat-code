package com.bamnat.app.rules

import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

/**
 * 2.0.0 [RVR-44] (총괄 HQ-123) No test leans on what an earlier test left. Robolectric gives each test a new app but keeps
 * one JVM, so the app's objects (a statement kept in memory, the one tip a run may show, the line under the screen) went
 * on from the test before: VScreenshotTest failed 3 of 20 run alone, because an earlier test's 명세서 work turned
 * '모르겠으면 명세서 사진으로 맞추기' into '넣던 명세서 이어서' and hid '다른 방법:'. [clear] runs before every Robolectric test
 * ([Plugin], found through `META-INF/services`), so a test starts as the app does on a phone: nothing in memory.
 *
 * The stores that read settings or files are one per app ([com.bamnat.app.data.PerApp]) and need nothing here. An object
 * that keeps something for the app's run goes in [clear]; [TestStateTest] checks both.
 */
object TestState {
    /** How many times [clear] ran in this JVM ([TestStateTest] sees it run before each test). */
    @Volatile var clears = 0
        private set

    fun clear() {
        // The screen's own line, opening windows' turns and what is open ([ComposeMainReset.leftovers], RVG-37).
        ComposeMainReset.leftovers()
        // A 명세서 read or filled and not yet put in (RVJ-62), kept by the place that opens it.
        com.bamnat.app.ui.pay.PayslipWorks.clearForTest()
        // The one tip a run of the app may show (J133-51), 마을's count of new answers.
        com.bamnat.app.ui.guide.OnceTipSlot.resetForTest()
        com.bamnat.app.company.VillageReplies.resetForTest()
        // What screenshots cleared each by hand ([com.bamnat.app.screenshots.ScreenshotBase], AGScreenshotTest): a search left
        // open, setup's '이번 달이 채워졌어요', a closed look line, a month put in, the 프로 sheet, a settings or inquiry link.
        com.bamnat.app.ui.company.PublicBrowse.reset()
        com.bamnat.app.ui.calendar.SetupFill.close()
        com.bamnat.app.ui.calendar.ShiftLookShut.mode = null
        com.bamnat.app.ui.RecentFill.reset()
        com.bamnat.app.ui.pro.ProOffer.close()
        com.bamnat.app.ui.settings.InquiryLinks.take()
        com.bamnat.app.ui.settings.SettingsLinks.target.value?.let { com.bamnat.app.ui.settings.SettingsLinks.consume(it) }
        clears++
    }

    /** Called by Robolectric as it sets up each test's app, before the app starts. */
    class Plugin : TestEnvironmentLifecyclePlugin {
        override fun onSetupApplicationState() = clear()
    }
}
