package com.bamnat.app.ui.theme

import com.bamnat.app.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.36 [SEC-06] 안전 모드 draws with the app's own looks; everything else stays the user's. */
class SafeLooksTest {
    @Test fun looksGoBackToTheAppsOwnOthersStay() {
        val worn = AppSettings(calTheme = "x", photoLook = "p", calView = "v", screenLooks = "s", memoLook = "m", moneyLook = "w", payLooks = "y",
            weeklyLimitHours = 52)
        val safe = worn.safeLooks()
        val d = AppSettings()
        assertEquals(d.calTheme, safe.calTheme)
        assertEquals(d.photoLook, safe.photoLook)
        assertEquals(d.calView, safe.calView)
        assertEquals(d.screenLooks, safe.screenLooks)
        assertEquals(d.memoLook, safe.memoLook)
        assertEquals(d.moneyLook, safe.moneyLook)
        assertEquals(d.payLooks, safe.payLooks)
        assertEquals(52, safe.weeklyLimitHours)
    }
}
