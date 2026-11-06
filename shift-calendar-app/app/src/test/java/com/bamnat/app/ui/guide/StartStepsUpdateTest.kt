package com.bamnat.app.ui.guide

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.BuildConfig
import com.bamnat.core.time.Today
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.38 PC 4a5c3828, 통합 8차: the first week's 시작 단계 is for a new phone, not for a calendar that came by an update. Told by the
 * versionCode the first setup was saved with, never by dates, and decided once.
 */
@RunWith(AndroidJUnit4::class)
class StartStepsUpdateTest {
    private val now = BuildConfig.VERSION_CODE
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val zone = ZoneId.of("Asia/Seoul")

    @After fun clock() = Today.reset()

    @Test fun aSetupOnThisAppIsNew() {
        assertEquals(false, StartStepsState.cameByUpdate(now, now, setupDone = true, updated = false))
        // Set up and updated (to this very app) on the same day: still new.
        assertEquals(false, StartStepsState.cameByUpdate(now, now, setupDone = true, updated = true))
    }

    @Test fun aSetupOnAnOlderAppCameByUpdate() {
        assertEquals(true, StartStepsState.cameByUpdate(now - 1, now, setupDone = true, updated = true))
        // 1.36·1.37 kept no version: set up and updated since is an update, whatever the days.
        assertEquals(true, StartStepsState.cameByUpdate(null, now, setupDone = true, updated = true))
    }

    /** 1.38 통합 9차: a template ('3조 2교대 일반형') is no company, so 우리 회사 고르기 stays on the first week's card. */
    @Test fun onlyACompanysOwnSetupDoesTheCompanyStep() {
        fun src(kind: com.bamnat.core.setup.SetupSource.Kind, name: String) = com.bamnat.core.setup.SetupSource(kind, "", name = name)
        assertFalse(StartStepsState.fromCompany(src(com.bamnat.core.setup.SetupSource.Kind.TEMPLATE, "3조 2교대 일반형")))
        assertFalse(StartStepsState.fromCompany(null))
        assertFalse(StartStepsState.fromCompany(src(com.bamnat.core.setup.SetupSource.Kind.FILE, "")))
        assertTrue(StartStepsState.fromCompany(src(com.bamnat.core.setup.SetupSource.Kind.SERVER, "예시정밀")))
        assertTrue(StartStepsState.fromCompany(src(com.bamnat.core.setup.SetupSource.Kind.PUBLIC, "예시정밀")))
        assertTrue(StartStepsState.fromCompany(src(com.bamnat.core.setup.SetupSource.Kind.FILE, "예시정밀")))
    }

    @Test fun nothingIsDecidedBeforeTheSetup() {
        assertNull(StartStepsState.cameByUpdate(null, now, setupDone = false, updated = true))
        assertEquals(false, StartStepsState.cameByUpdate(null, now, setupDone = true, updated = false))
    }

    /** Old app put on and updated the same day (11/6), the clock then moved +5 and +6 days: the steps stay away. */
    @Test fun anUpdaterStaysAnUpdaterWhenTheClockMoves() {
        val c = app.container
        val first = c.appContext.getSharedPreferences("device", 0)
        first.edit().putString("first_setup_day", "2026-11-06").putInt("first_setup_version", now - 1).commit()
        listOf(6, 11, 12).forEach { d ->
            Today.fix(ZonedDateTime.of(2026, 11, d, 9, 0, 0, 0, zone))
            assertTrue("11/$d", StartStepsState.cameByUpdate(c))
            assertNull("11/$d", StartStepsState.rows(c, hasShifts = true))
        }
        // Decided once: what the setup says later changes nothing.
        first.edit().putInt("first_setup_version", now).commit()
        assertTrue(StartStepsState.cameByUpdate(c))
    }

    /** A new phone set up on this app sees the steps, on the day after and five days on. */
    @Test fun aNewPhoneKeepsItsSteps() {
        val c = app.container
        c.appContext.getSharedPreferences("device", 0).edit().putString("first_setup_day", "2026-11-06").putInt("first_setup_version", now).commit()
        listOf(7, 11).forEach { d ->
            Today.fix(ZonedDateTime.of(2026, 11, d, 9, 0, 0, 0, zone))
            assertFalse("11/$d", StartStepsState.cameByUpdate(c))
        }
    }
}
