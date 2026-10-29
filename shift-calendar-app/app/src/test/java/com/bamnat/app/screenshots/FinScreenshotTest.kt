package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PayWay
import com.bamnat.app.ui.company.CompanyReportViewModel
import com.bamnat.app.ui.company.GuardDonePanel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [FIN130-01][FIN130-09] 1.30 마무리's screenshots, numbers 1470~1479: 회사 규칙 설정's 통상시급 hours as one set of chips (the
 * company's "회사 규칙 180시간" first, then 180·209·226·243 once each, and hours of the user's own sent as an opinion), and 이 규칙
 * 쓰기 with the plaza's pay way ending on the pay screen, where its 기본급 is asked. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FinScreenshotTest : ScreenshotBase() {
    private companion object {
        const val NAME = "(주)예시정밀"
    }

    // ---- [FIN130-09] 통상시급 기준 시간: one set of chips ----

    private fun rulesHours(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        val c = companyOn()
        // The pay uses the company's way (180 hours); the company's page is the one kept on the phone.
        c.payWays.use(PayWay("c1", NAME, "생산팀", "s1"))
        rulesEditor { companyRules }.invoke(this)
        // Far down the editor at 200% text: a few long drags first, then the last bit to it.
        repeat(10) { if (!shows("회사 규칙 180시간", exact = true)) scrollUp(500) }
        reveal("회사 규칙 180시간", exact = true)
        then()
    }

    @Test fun rulesHoursOneSet() = shoot("1470_fin_rules_hours_one_set", FOLDED, rulesHours {
        // The company's first, and not again as a plain 180시간 chip (1.30 had three sets of chips here).
        assertTrue(appears("회사 규칙 180시간", exact = true))
        assertTrue(gone("180시간", exact = true))
        assertTrue(appears("226시간", exact = true))
        // The user's 209 against the company's 180: one tap sends it as an opinion.
        assertTrue(appears("209시간으로 의견 보내기", exact = true))
    })

    @Test fun rulesHoursHugeText() = shoot("1471_fin_rules_hours_200", FOLDED, 2f, rulesHours())

    // ---- [FIN130-01] 이 규칙 쓰기 with the pay way: the pay screen is next ----

    private fun usedWithPay(opened: () -> Unit = {}, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            GuardDonePanel(CompanyReportViewModel.Sending.Used(NAME, "c1", "생산팀", withPay = true, toPay = true),
                onDone = {}, onOpenCompany = {}, onOpenPay = opened)
        }
        settle()
        then()
    }

    @Test fun usedWithPayGoesToThePay() {
        var opened = false
        shoot("1472_fin_used_with_pay", FOLDED, usedWithPay({ opened = true }) {
            assertTrue(appears("급여 화면에서 기본급을 넣으면 들어가요", exact = false))
            assertTrue(appears("‘$NAME’ 광장 보기", exact = true))
            tapText("급여 화면에서 기본급 넣기", exact = true)
            settle()
            assertTrue(opened)
        })
    }

    @Test fun usedWithPayHugeText() = shoot("1473_fin_used_with_pay_320_200", SMALL_PHONE, 2f, usedWithPay {
        reveal("급여 화면에서 기본급 넣기", exact = true)
    })
}
