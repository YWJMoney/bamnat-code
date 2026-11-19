package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.money.DailySales
import com.bamnat.core.work.StaffShift
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 2.0.0 갈래 J 14차 앞·뒤 사진. [RVJ-84] 검토 BOSS-63: 간이과세 가게의 부가세 예상에 업종(부가가치율) 줄과 고르는 창. [RVJ-87] 검토 BOSS-66:
 * 영업일·정기휴무의 스위치 줄(한 번에 켜고 끄기). [RVJ-85] 검토 BOSS-64: 15~23시로 바꿔 둔 토요일을 결근으로 돌린 뒤의 대타 찾기 창.
 * The same scenes run on the code before the batch for the 앞 pictures (no new names here but the trade dialog's).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjShopVatScreenshotTest : ScreenshotBase() {
    /** A 간이과세 shop with a half year of sales (1.2백만 원 × 40일) and its 매출·영업 tab at 부가세 예상. */
    private fun vat(then: Screen.() -> Unit = {}): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        app.container.calendars.updateShopMoney(PersonalCalendar.MAIN) { it.copy(simpleTax = true) }
        runBlocking {
            val money = app.container.moneyRepository
            (1..40).forEach { k -> money.save(DailySales(today.minusDays(k * 2L), sales = 1_200_000, costs = 380_000)) }
        }
        settle()
        FeatureLinks.open(FeatureRoute(Place.STATS))
        settle()
        reveal("부가세 예상", exact = false)
        then()
    }

    @Test fun vatFolded() = shoot("rvj_311_shop_vat_simple_folded", FOLDED, vat())
    @Test fun vatFoldedDarkBig() = shoot("rvj_312_shop_vat_simple_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, vat())
    @Test fun vatUnfolded() = shoot("rvj_313_shop_vat_simple_unfolded", UNFOLDED, vat())

    /** After only: '업종 고르기' opens the six trades with their rates; 숙박 picked reads back on the card. */
    private fun trade(pick: Boolean): Screen.() -> Unit = vat {
        tapText("업종 고르기", exact = true)
        settle()
        assertTrue(shows("간이과세 업종", exact = true))
        assertTrue(shows("숙박 · 25%", exact = true))
        if (pick) {
            tapText("숙박 · 25%", exact = true)
            settle()
            assertTrue(shownWords().joinToString(" | "), appears("업종 숙박 · 부가가치율 25%"))
        }
    }

    @Test fun tradeDialogFolded() = shoot("rvj_314_shop_vat_trade_dialog_folded", FOLDED, trade(false))
    @Test fun tradeDialogDarkBig() = shoot("rvj_315_shop_vat_trade_dialog_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, trade(false))
    @Test fun tradePicked() = shoot("rvj_316_shop_vat_trade_picked_folded", FOLDED, trade(true))

    /** 영업일·정기휴무: the switch rows (공휴일은 쉬어요 …). */
    private fun shopDays(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        FeatureLinks.open(FeatureRoute(Place.SHOP_DAYS))
        settle()
        reveal("공휴일은 쉬어요", exact = true)
        // One focus that says 켜짐·꺼짐: the row is the switch (its words and the switch read together), not a row and a switch apart.
        compose.onNode(androidx.compose.ui.test.hasText("공휴일은 쉬어요", substring = true) and
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch) and
            androidx.compose.ui.test.isToggleable()).assertExists()
    }

    @Test fun shopDaysFolded() = shoot("rvj_317_shop_days_switch_folded", FOLDED, shopDays())
    @Test fun shopDaysDarkBig() = shoot("rvj_318_shop_days_switch_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, shopDays())

    /** A Saturday whose hours the boss changed to 15~23, then turned to 결근: who could come, for those hours. */
    private fun cover(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val usual = StaffShift(LocalTime.of(10, 0), LocalTime.of(18, 0))
        val hired = LocalDate.of(2026, 3, 2)
        val place = Workplace("p", "말랑 카페", fivePlus = false, minuteUnit = 1)
        val staff = listOf(
            StaffMember("a", "김하늘", base = 10_320, hiredOn = hired, weekly = mapOf(DayOfWeek.SATURDAY to usual)),
            StaffMember("b", "이바다", base = 10_320, hiredOn = hired,
                weekly = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(15, 0)))),
            StaffMember("c", "박노을", base = 11_000, hiredOn = hired,
                weekly = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }),
        )
        // What the 결근 row holds once the batch keeps the day's hours (before it, the row had none and the usual 10~18 was asked).
        val work = listOf(StaffWork("a", sat, WorkKind.ABSENT, LocalTime.of(15, 0), LocalTime.of(23, 0)))
        content { com.bamnat.app.ui.work.BossCoverDialog(place, staff, work, "a", sat) {} }
        settle()
    }

    @Test fun coverFolded() = shoot("rvj_319_boss_cover_changed_hours_folded", FOLDED, cover())
    @Test fun coverDarkBig() = shoot("rvj_320_boss_cover_changed_hours_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, cover())
}
