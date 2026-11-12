package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.EditValueDialog
import com.bamnat.app.ui.pay.ValueTarget
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 갈래 J, 통합 2차 PC 20:51 KST판(Q24·Q26): [RVJ-45] PC-108 한 주 15시간이 안 되는 직원의 4대보험 칩이 말없이 꺼져 있던 것,
 * [RVJ-46] PC-110 기본 금액 바꾸기 창이 금액을 바꾸기 전부터 '합계는 그대로예요 · … 마감한 달이에요'라고 하던 것.
 * The shots hold no assertion so the same test draws the 'before'; the tests that name what they look for check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjShortWeekScreenshotTest : ScreenshotBase() {
    private val four = StaffShift(LocalTime.of(10, 0), LocalTime.of(14, 0))

    private fun staff(tax: StaffTax): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "식당", fivePlus = false, payday = 10))
            // 토·일 10~14: 휴게 30분씩 빼면 한 주 7시간.
            store.saveStaff(StaffMember("s1", "주말알바", base = 10_320L, hiredOn = LocalDate.of(2026, 9, 5),
                weekly = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).associateWith { four }, insurance = Insurance(tax = tax)), "p1", 0)
        }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
        tapText("직원 정보", exact = true)
        settle()
        reveal("4대보험 (사장님 몫 계산)", exact = true)
        repeat(2) { scrollDown(200, 0.5f) }
    }

    @Test fun shortWeek() = shoot("rvj_110_short_week_insurance_folded", FOLDED, staff(StaffTax.WAGE))
    @Test fun shortWeekDarkBig() = shoot("rvj_111_short_week_insurance_folded_dark_200", DARK, 2f, staff(StaffTax.WAGE))

    @Test fun aShortWeekSaysWhyTheChipsAreOff() = shoot("rvj_112_short_week_insurance_said_folded", FOLDED) {
        staff(StaffTax.WAGE).invoke(this)
        assertTrue(scrolledTo("한 주 15시간(월 60시간)이 안 돼 국민연금·건강보험은 가입 대상이 아니에요"))
        assertTrue(scrolledTo("고용보험은 3개월 넘게 이어서 일하면 들어요"))
    }

    @Test fun aDailyWorkerHearsOfTheFirstDay() = shoot("rvj_113_short_week_daily_folded", FOLDED) {
        staff(StaffTax.DAILY).invoke(this)
        assertTrue(scrolledTo("일용직은 한 주 15시간이 안 돼도 고용보험은 첫날부터 들어요"))
        assertTrue(!shows("가입 대상이 아니에요"))
    }

    private fun amount(): Screen.() -> Unit = payScreen(Tier.PRO, withProfile = true) { vm ->
        val profile = vm.profile.collectAsState().value
        val snap = vm.snapshot.collectAsState().value
        if (profile != null && snap != null) EditValueDialog(vm, profile, snap, ValueTarget.Base, Today.month(), onDismiss = {})
    }

    @Test fun amountUntouched() = shoot("rvj_114_amount_untouched_folded", FOLDED, amount())
    @Test fun amountUntouchedDarkBig() = shoot("rvj_115_amount_untouched_folded_dark_200", DARK, 2f, amount())

    @Test fun anAmountNotChangedSaysWhatItWillShow() = shoot("rvj_116_amount_untouched_said_folded", FOLDED) {
        amount().invoke(this)
        assertTrue(appears("금액을 바꾸면 달마다 얼마가 달라지는지 여기에 보여 드려요"))
        assertTrue("아직 바꾸지 않았는데 그대로라고 하지 않아요", !shows("합계는 그대로예요"))
        assertTrue(!shows("마감한 달이에요"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
