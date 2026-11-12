package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 K 6차: 급여 설정 '쉬는 날이면' [RVK-38], 은행 출금 '이 돈은 [쓴 돈][내 계좌로 옮김]' [RVK-39], 순자산의 안 적은 카드값 한 줄
 * [RVK-37], 플랫폼 남은 돈은 3.3% 뺀 돈 [RVK-40], 할부 '11월부터 청구' [RVK-37]. Light·dark × 글자 100%·200% × 접은·편 폭. The code
 * before draws the same scenes (the PR's 'before'). Today is 2026-10-14; October's 25th is a Sunday. Amounts and names are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkSixScreenshotTest : ScreenshotBase() {
    /** [RVK-38] 급여 설정 with 20일 마감·25일 지급, at the 월급날 block. */
    private fun payDay(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        val c = app.container
        c.settingsRepository.update { it.copy(previewTier = null) }
        val vm = com.bamnat.app.ui.pay.PayViewModel(c)
        runBlocking { c.payStore.save(closeOn20(vm.sampleProfile()), keepUndo = false) }
        settle()
        content {
            val profile = vm.profile.collectAsState().value
            val snap = vm.snapshot.collectAsState().value
            if (profile != null && snap != null) com.bamnat.app.ui.pay.CompanyRulesEditor(vm, profile, snap, Today.month(), onClose = {}, onOpenKinds = {})
        }
        settle()
        reveal(AFTER_PAY, exact = false)
        assertTrue("월급날이 일요일이라 23일", appears("10월 23일에 들어와요", exact = false))
    }

    /** [RVK-39] Two bank 출금 texts read today: 통신비 to LG유플러스 and money to 김철수. */
    private fun withdrawals(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            val at = Today.millis()
            money.capture("[Web발신]\n신한 10/14 08:10\n110-***-123456\n출금 55,000\n잔액 1,231,000\nLG유플러스", "com.shinhan.sbanking", at - 2 * HOUR)
            money.capture("[Web발신]\n신한 10/14 09:30\n110-***-123456\n출금 450,000\n잔액 781,000\n김철수", "com.shinhan.sbanking", at - HOUR)
        }
        settle()
        tab(1, wide)
        reveal("새로 들어온 기록", exact = false)
        reveal(AFTER_WITHDRAW, exact = false)
    }

    /** [RVK-37] The card 신한 Deep (결제일 14일) used in July and August, its 카드값 never written. */
    private fun netWorth(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            val credit = money.allAccounts().first { it.name == "신한 Deep" }
            listOf(LocalDate.of(2026, 7, 18) to 286_000L, LocalDate.of(2026, 8, 21) to 312_000L, LocalDate.of(2026, 9, 9) to 254_000L).forEach { (d, a) ->
                money.save(MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = a, category = "장보기·쇼핑", merchant = "이마트", accountId = credit.id))
            }
        }
        settle()
        tab(1, wide)
        reveal(AFTER_NET, exact = false)
    }

    /** [RVK-40] 배달 (쿠팡이츠) ten days this month and a 유류비 written by hand. */
    private fun platform(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        app.container.calendars.updateField(PersonalCalendar.MAIN) { it.copy(mode = com.bamnat.core.money.FieldMode.PLATFORM) }
        runBlocking {
            val money = app.container.moneyRepository
            money.allJobs().forEach { money.delete(it) }
            (1..10).forEach { k ->
                money.save(PlatformWork.job(today.minusDays(k.toLong()), "쿠팡이츠", 18 + k % 5, 96_000L + k * 3_000, 300 + k * 10, 0))
            }
            money.save(MoneyEntry(date = today.minusDays(3), kind = MoneyKind.EXPENSE, amount = 62_000, category = "유류비", merchant = "SK에너지 역삼"))
        }
        settle()
        tab(1, wide = false)
        reveal("플랫폼 수입", exact = false)
        reveal("남은 돈", exact = false)
    }

    /** [RVK-37] 내역: the 3개월 할부 무선청소기 bought on 10/7 on the card whose bill comes on the 14th. */
    private fun installment(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledgerPage("내역", wide).invoke(this)
        settle()
        reveal("라마바전자", exact = false)
    }

    @Test fun payDayLight() = shoot("rvk6_01_payday_off_folded", FOLDED, payDay())
    @Test fun payDayDarkBig() = shoot("rvk6_02_payday_off_folded_dark_200", DARK, 2f, payDay())
    @Test fun payDayWide() = shoot("rvk6_03_payday_off_unfolded", UNFOLDED, payDay())
    @Test fun withdrawLight() = shoot("rvk6_04_withdraw_ask_folded", FOLDED, withdrawals {
        assertTrue("통신비 출금은 지출", appears("LG유플러스", exact = false))
    })
    @Test fun withdrawDarkBig() = shoot("rvk6_05_withdraw_ask_folded_dark_200", DARK, 2f, withdrawals())
    @Test fun withdrawWide() = shoot("rvk6_06_withdraw_ask_unfolded", UNFOLDED, withdrawals(wide = true))
    @Test fun netWorthLight() = shoot("rvk6_07_networth_unrecorded_folded", FOLDED, netWorth())
    @Test fun netWorthDarkBig() = shoot("rvk6_08_networth_unrecorded_folded_dark_200", DARK, 2f, netWorth())
    @Test fun platformLight() = shoot("rvk6_09_platform_net_folded", FOLDED, platform())
    @Test fun platformDarkBig() = shoot("rvk6_10_platform_net_folded_dark_200", DARK, 2f, platform())
    @Test fun installmentLight() = shoot("rvk6_11_installment_folded", FOLDED, installment())
    @Test fun installmentDarkBig() = shoot("rvk6_12_installment_folded_dark_200", DARK, 2f, installment())

    /** The chips under a bank 출금 to someone else: one tap keeps it a move and asks no more. */
    @Test fun movedIsRememberedAndAsksNoMore() = shoot("rvk6_13_withdraw_moved_folded", FOLDED, withdrawals {
        tapText("내 계좌로 옮김", exact = true)
        settle()
        assertTrue("다음부터 묻지 않아요", !appears("내 계좌로 옮김", exact = true))
        assertTrue(runBlocking { app.container.moneyRepository.rules() }["김철수"] == com.bamnat.core.money.LedgerSmarts.MOVE_CATEGORY)
    })

    private fun withdrawals(then: Screen.() -> Unit): Screen.() -> Unit = { withdrawals().invoke(this); then() }

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val HOUR = 60 * 60 * 1000L
        const val AFTER_PAY = "들어와요("
        const val AFTER_WITHDRAW = "이 돈은"
        const val AFTER_NET = "결제일에 나간 것으로"
    }
}
