package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.components.ExplainSeen
import com.bamnat.core.time.Today
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 P 묶음 2 [RVP-47] (검토 U13-18·T13-02, 조건 1·4): 사장님 급여 대장은 달 위의 두 회색 문단을 첫 번만 길게, 다음부터 한 줄 + ⓘ로 하고,
 * 이달 인건비 큰 숫자가 사업장 카드 맨 위(주의 줄·5인 질문보다 먼저)에서 시작해요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP7PayrollTest : ScreenshotBase() {
    private val roster = StaffRoster(listOf(
        Staff("s1", "김알바", 10_320, shifts = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }),
        Staff("s2", "이주말", 10_320, shifts = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).associateWith { StaffShift(LocalTime.of(10, 0), LocalTime.of(19, 0)) }),
    ))

    private fun ledger(seen: Boolean): Screen.() -> Unit = {
        ExplainSeen.reset(app)
        if (seen) app.getSharedPreferences("explain_seen", android.content.Context.MODE_PRIVATE).edit()
            .putInt("payroll.notice", 1).putInt("payroll.month", 1).commit()
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
        assertTrue("이달 인건비가 먼저", shows("${YearMonth.from(Today.date()).monthValue}월 인건비", exact = true))
        assertTrue("두 번째부터 한 줄 요약과 ⓘ", !seen || appears("설명 보기", exact = true))
    }

    @Test fun firstTimeLight() = shoot("rvp7_payroll_first_folded_light", FOLDED, ledger(false))
    @Test fun secondTimeLight200() = shoot("rvp7_payroll_second_folded_light_200", FOLDED, 2f, ledger(true))
    @Test fun secondTimeDark200() = shoot("rvp7_payroll_second_folded_dark_200", DARK, 2f, ledger(true))

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
