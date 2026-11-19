package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 갈래 J [RVJ-100] 검토 T05-05·U05c-09: a 사장님 with two 가게 picks the 대장 by the chips on top; another is added from 사업장
 * 설정, and an empty one can be taken away again. The 직원 요금제 counts everyone in both.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjShopsScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    private fun seed(two: Boolean) = runBlocking {
        val store = app.container.phaseThree
        store.saveWorkplace(Workplace("p1", "식당", fivePlus = true, payday = 10))
        store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, hiredOn = LocalDate.of(2026, 3, 2), job = "홀",
            weekly = DayOfWeek.entries.take(5).associateWith { nine }, insurance = Insurance.ALL), "p1", 0)
        if (two) {
            store.saveWorkplace(Workplace("p2", "카페", fivePlus = false, payday = 25, payNextMonth = false))
            store.saveStaff(StaffMember("s2", "이바리", base = 11_000L, hiredOn = LocalDate.of(2026, 5, 1), job = "바리스타",
                weekly = DayOfWeek.entries.drop(5).associateWith { nine }, insurance = Insurance.ALL), "p2", 0)
        }
    }

    private fun ledger(two: Boolean, placeId: String? = null): Screen.() -> Unit = {
        seed(two)
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null, placeId = placeId) {} }
        repeat(2) { settle() }
    }

    private fun second(): Screen.() -> Unit = {
        ledger(true).invoke(this)
        tapText("카페", exact = true)
        repeat(2) { settle() }
    }

    @Test fun twoShops() = shoot("rvj_360_shops_folded", FOLDED, second())
    @Test fun twoShopsDarkBig() = shoot("rvj_361_shops_folded_dark_200", DARK, 2f, second())
    @Test fun twoShopsWide() = shoot("rvj_362_shops_unfolded", UNFOLDED, second())

    @Test fun theChipsPickTheShop() = shoot("rvj_363_shops_pick_folded", FOLDED) {
        ledger(true).invoke(this)
        assertTrue(appears("식당"))
        assertTrue("처음 가게의 직원", scrolledTo("김알바"))
        assertFalse(shows("이바리"))
        tapText("카페", exact = true)
        repeat(2) { settle() }
        assertTrue("카페의 직원", scrolledTo("이바리"))
        assertFalse(shows("김알바"))
    }

    @Test fun aNoticeOpensItsOwnShop() = shoot("rvj_364_shops_open_second_folded", FOLDED) {
        ledger(true, placeId = "p2").invoke(this)
        assertTrue(scrolledTo("이바리"))
        assertEquals("p2", com.bamnat.app.boss.BossFilingNotice.placeOf("FILINGS@p2"))
        assertEquals("", com.bamnat.app.boss.BossFilingNotice.placeOf("FILINGS"))
        assertNull(com.bamnat.app.boss.BossFilingNotice.placeOf("OTHER"))
    }

    @Test fun oneShopHasNoChipsAndAddsAnotherFromSettings() = shoot("rvj_365_shops_add_folded", FOLDED) {
        ledger(false).invoke(this)
        tapText("사업장 설정", exact = true)
        settle()
        assertTrue(scrolledTo("다른 가게 더하기"))
        assertFalse("빈 가게가 아니면 지우기가 없어요", shows("이 가게 지우기"))
        tapText("다른 가게 더하기", exact = true)
        repeat(2) { settle() }
        val places = runBlocking { app.container.phaseThree.workplaces.first() }
        assertEquals(2, places.size)
        assertTrue("새 가게는 비어 있어 지울 수 있어요", scrolledTo("이 가게 지우기"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
