package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.EditValueDialog
import com.bamnat.app.ui.pay.ValueTarget
import com.bamnat.core.time.Today
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J (급여·사장님): the screens its first PR touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after
 * sheet (디자인 품질 조건 16가지). Written against what both the old and new screens show, so the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjScreenshotTest : ScreenshotBase() {
    private val roster = StaffRoster(listOf(
        Staff("s1", "김알바", 10_320, shifts = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }),
    ))

    private fun staffDialog(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
        tapText("직원 정보", exact = true)
        settle()
        // After: the 고정 수당 rows with 식대 (세금 없는 돈) and the 가족 수. Before: the 4대보험 chips where they were.
        if (scrolledTo("+ 식대", exact = true, scrolls = 14)) tapText("+ 식대", exact = true, scrolls = 0)
        reveal("4대보험 (사장님 몫 계산)", exact = true)
    }

    private fun placeDialog(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
        tapText("사업장 설정", exact = true)
        settle()
        reveal("급여 주는 날", exact = true)
    }

    private fun bulkEdit(): Screen.() -> Unit = payScreen(Tier.PRO, withProfile = true) { vm ->
        val profile = vm.profile.collectAsState().value
        val snap = vm.snapshot.collectAsState().value
        if (profile != null && snap != null) EditValueDialog(vm, profile, snap, ValueTarget.Base, Today.month(), onDismiss = {})
    }

    private fun rules(): Screen.() -> Unit = {
        rulesEditor { companyRules }.invoke(this)
        reveal("밤샘 근무는 시작한 날로 휴일 판단", exact = true)
    }

    /**
     * [RVJ-17] 검토 BOSS-55 '직원에게 줬는지도 안 남아요': a signed contract has the switch '직원에게 한 부 줬어요'; on, it shows the day,
     * and off takes a mistaken press back (the model and the PDF line had it since the first PR; nothing on the screen set it).
     */
    private fun contractGiven(undo: Boolean): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
        tapText("계약서", exact = true)
        settle()
        tapText("종이로 받았어요", exact = true)
        settle()
        // [RVJ-24] 검토 BOSS-53: it asks the day the paper came back before it marks anything.
        tapText("받았어요", exact = true)
        settle()
        tapText("직원에게 한 부 줬어요", exact = true)
        settle()
        org.junit.Assert.assertTrue("준 날이 보여야 해요", scrolledTo("에 줬어요 · 계약서 PDF에 남아요"))
        if (undo) {
            tapText("직원에게 한 부 줬어요", exact = true)
            settle()
            org.junit.Assert.assertTrue(gone("에 줬어요 · 계약서 PDF에 남아요"))
            org.junit.Assert.assertTrue(scrolledTo("주고 나서 켜 두면 준 날이 계약서 PDF에 남아요", exact = true))
        }
    }
    @Test fun contractGivenLight() = shoot("rvj_24_contract_given_folded", FOLDED, contractGiven(false))
    @Test fun contractGivenDarkBig() = shoot("rvj_25_contract_given_folded_dark_200", DARK, 2f, contractGiven(false))
    @Test fun contractGivenTakenBack() = shoot("rvj_26_contract_given_undone_folded", FOLDED, contractGiven(true))

    @Test fun staffLight() = shoot("rvj_01_staff_folded", FOLDED, staffDialog())
    @Test fun staffDark() = shoot("rvj_02_staff_folded_dark", DARK, staffDialog())
    @Test fun staffBig() = shoot("rvj_03_staff_folded_200", FOLDED, 2f, staffDialog())
    @Test fun staffWide() = shoot("rvj_04_staff_unfolded", UNFOLDED, staffDialog())
    @Test fun placeLight() = shoot("rvj_05_place_folded", FOLDED, placeDialog())
    @Test fun placeDarkBig() = shoot("rvj_06_place_folded_dark_200", DARK, 2f, placeDialog())
    @Test fun bulkLight() = shoot("rvj_07_bulk_edit_folded", FOLDED, bulkEdit())
    @Test fun bulkDarkBig() = shoot("rvj_08_bulk_edit_folded_dark_200", DARK, 2f, bulkEdit())
    @Test fun bulkWide() = shoot("rvj_09_bulk_edit_unfolded", UNFOLDED, bulkEdit())
    @Test fun rulesLight() = shoot("rvj_10_rules_folded", FOLDED, rules())
    @Test fun rulesBig() = shoot("rvj_11_rules_folded_200", FOLDED, 2f, rules())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
