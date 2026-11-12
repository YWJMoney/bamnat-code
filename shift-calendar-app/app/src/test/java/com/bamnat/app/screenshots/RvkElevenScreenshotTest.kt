package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.FixedCost
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobPayment
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.Receivable
import com.bamnat.core.money.ReceivableKind
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 11차 [RVK-46][RVK-47]: DB 36판이 담은 것을 화면에서. 반복 이체 '어디로'(MONEY-07), 일감 나눠 받은 돈(MONEY-29·T06a-01·I06ab-04),
 * 줄 돈(T06a-05·I06ab-03), 예산 '이 달만'(I06aa-05), 분류 아이콘·색(U06a-15·I06aa-09), 주의 색 남은 것(HQ-101), 받을 돈 설명의 유형 말(PC-169).
 * Light·dark × 글자 100%·200% × 접은·편 폭. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkElevenScreenshotTest : ScreenshotBase() {
    /** 월급 통장에서 적금 통장으로 매달 내일 날짜에 30만(곧 나갈 돈에 보여요). */
    private fun transfer(): Screen.() -> Unit = {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            val bank = money.allAccounts().first { it.name == "국민 월급통장" }
            val saving = money.save(MoneyAccount(name = "카카오 적금", type = AccountType.BANK))
            money.save(FixedCost(name = "적금 넣기", amount = 300_000, day = today.dayOfMonth + 1, kind = MoneyKind.TRANSFER, category = "저축",
                accountId = bank.id, toAccountId = saving.id))
        }
        tab(1, false)
        tapText("적금 넣기", exact = true)
        reveal("어디로", exact = true)
    }

    /** 현장: 베란다 확장 300만, 계약금 50만에 중도금 100만을 나눠 받음. */
    private fun fieldJob() = runBlocking {
        app.container.moneyRepository.save(Job(date = today.minusDays(20), title = "베란다 확장", client = "가나다 고객", amount = 3_000_000,
            deposit = 500_000, depositPaid = true, due = today.plusDays(10),
            payments = listOf(JobPayment(date = today.minusDays(6), amount = 1_000_000, label = "중도금"))))
    }

    /** 현장: 자재상에 갚을 타일값 45만 중 15만 갚음, 25일에 나머지. */
    private fun supplyOwed() = runBlocking {
        val money = app.container.moneyRepository
        val r = money.save(Receivable(who = "다라마 자재상", title = "타일 30박스", amount = 450_000, date = today.minusDays(12), due = today.plusDays(11),
            kind = ReceivableKind.SUPPLY, countAsIncome = false, toPay = true))
        money.receive(r, 150_000, today.minusDays(4))
    }

    private fun jobSheet(): Screen.() -> Unit = owed(WorkStyle.FIELD) {
        fieldJob()
        settle()
        tapText("베란다 확장", exact = false)
        reveal("일부만 들어왔나요?", exact = true)
    }

    private fun jobDialog(): Screen.() -> Unit = owed(WorkStyle.FIELD) {
        fieldJob()
        settle()
        tapText("베란다 확장", exact = false)
        tapText("일감 고치기", exact = true)
        reveal("나눠 받은 돈", exact = true)
    }

    private fun payables(wide: Boolean = false): Screen.() -> Unit = owed(WorkStyle.FIELD, wide) {
        supplyOwed()
        settle()
        tapText("줄 돈", exact = true, scrolls = 0)
        assertTrue("줄 돈 목록", scrolledTo("타일 30박스", exact = false))
    }

    /** 현장 장부 요약의 받을 돈 카드: 줄 돈 한 줄과 현장 말 설명. */
    private fun summaryOwed(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        supplyOwed()
        tab(1, false)
        reveal("줄 돈 1건", exact = false)
    }

    private fun budgetEditor(): Screen.() -> Unit = ledgerPage(null) {
        reveal("분류별 예산", exact = true)
        tapText("고치기", exact = true)
        tapText("${today.monthValue}월만", exact = true)
    }

    /** 카페를 하트·분홍으로 바꾼 뒤의 예산 카드: 배달은 넘침(주황 ⚠ 하나). */
    private fun budgetCard(): Screen.() -> Unit = ledgerPage(null) {
        runBlocking {
            app.container.moneyRepository.saveCategories(MoneyKind.EXPENSE, listOf(CategoryEdit(MoneyKind.EXPENSE, "카페", 0, icon = "heart", color = 7)))
        }
        settle()
        reveal("분류별 예산", exact = true)
    }

    private fun categoryLook(wide: Boolean = false): Screen.() -> Unit = ledgerPage(null, wide) {
        tapText("돈 적기", exact = true, scrolls = 0)
        tapText("분류 고치기", exact = true)
        tapText("식비 아이콘·색 바꾸기", exact = true, scrolls = 0)
        tapText("운동", exact = true, scrolls = 0)
        tapText("색 3번", exact = true, scrolls = 0)
    }

    @Test fun transferLight() = shoot("rvk11_01_transfer_where_folded", FOLDED, transfer())
    @Test fun transferDarkBig() = shoot("rvk11_02_transfer_where_folded_dark_200", DARK, 2f, transfer())
    @Test fun jobSheetLight() = shoot("rvk11_03_job_part_sheet_folded", FOLDED, jobSheet())
    @Test fun jobSheetDarkBig() = shoot("rvk11_04_job_part_sheet_folded_dark_200", DARK, 2f, jobSheet())
    @Test fun jobDialogLight() = shoot("rvk11_05_job_payments_dialog_folded", FOLDED, jobDialog())
    @Test fun payablesLight() = shoot("rvk11_06_payables_folded", FOLDED, payables())
    @Test fun payablesDarkBig() = shoot("rvk11_07_payables_folded_dark_200", DARK, 2f, payables())
    @Test fun payablesWide() = shoot("rvk11_08_payables_unfolded", UNFOLDED, payables(wide = true))
    @Test fun summaryOwedLight() = shoot("rvk11_09_summary_owed_payable_folded", FOLDED, summaryOwed())
    @Test fun budgetEditorLight() = shoot("rvk11_10_budget_this_month_folded", FOLDED, budgetEditor())
    @Test fun budgetEditorDarkBig() = shoot("rvk11_11_budget_this_month_folded_dark_200", DARK, 2f, budgetEditor())
    @Test fun budgetCardLight() = shoot("rvk11_12_budget_over_caution_folded", FOLDED, budgetCard())
    @Test fun budgetCardDarkBig() = shoot("rvk11_13_budget_over_caution_folded_dark_200", DARK, 2f, budgetCard())
    @Test fun categoryLookLight() = shoot("rvk11_14_category_look_folded", FOLDED, categoryLook())
    @Test fun categoryLookDarkBig() = shoot("rvk11_15_category_look_folded_dark_200", DARK, 2f, categoryLook())
    @Test fun categoryLookWide() = shoot("rvk11_16_category_look_unfolded", UNFOLDED, categoryLook(wide = true))

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
