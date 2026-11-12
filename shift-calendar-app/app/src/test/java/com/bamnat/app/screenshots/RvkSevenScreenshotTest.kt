package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * 2.0.0 갈래 K 7차 [RVK-42]: 분류 고치기는 종류마다 고친 것을 지키고 한 번에 저장 (검토 MONEY-15·U06a-15), 통계를 처음 셀 때 뼈대 줄
 * (MONEY-20·U06a-16), 카드 입금 예정은 여섯 줄과 '외 N건 더 보기' (U06a-21), 입금 확인한 날의 '확인 풀기' (MONEY-16), 작업 창의 작업 날짜
 * (U06a-22), 붙여 넣기가 너무 길면 앞만 읽었다고 (MONEY-26), 할부는 가게 이름을 가리지 않는 한 줄 (U06a-23). Light·dark × 글자
 * 100%·200% × 접은·편 폭. The code before draws the same scenes (the PR's 'before'). Today is 2026-10-14. Amounts and names are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkSevenScreenshotTest : ScreenshotBase() {
    /** 분류 고치기: 지출의 '카페'를 숨기고 수입 탭으로 가서 '급여'를 아래로. */
    private fun categories(): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        val book = runBlocking { app.container.moneyRepository.book.first() }
        content { com.bamnat.app.ui.money.CategoryEditor(book, onSave = { _ -> }, onDismiss = {}) }
        settle()
        tapText("카페 숨기기", exact = true)
        settle()
        tapText("수입", exact = true, scrolls = 0)
        settle()
        tapText("급여 아래로", exact = true)
        settle()
        reveal(AFTER_CATEGORIES, exact = false)
    }

    /** 통계, the first count not landed yet. */
    private fun counting(): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        val book = runBlocking { app.container.moneyRepository.book.first() }
        content {
            Surface(color = MaterialTheme.colorScheme.background) {
                com.bamnat.app.ui.money.LedgerReportsPane(
                    analysis = null, book = book, budget = null, style = WorkStyle.DAY, gate = com.bamnat.app.tier.Gate.OPEN, wide = false,
                    today = today, onCategory = {}, onOpen = {}, onPlace = {}, month = YearMonth.from(today),
                )
            }
        }
        settle()
    }

    /** 가게: 열하루치 카드 매출, 입금은 아직. */
    private fun deposits(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        runBlocking {
            val money = app.container.moneyRepository
            (1..11).forEach { k -> money.save(DailySales(today.minusDays(k.toLong()), sales = 700_000L + k * 23_000, card = 480_000L + k * 17_000)) }
        }
        settle()
        tab(1, wide)
        reveal("카드 입금 예정", exact = true)
        reveal(AFTER_DEPOSITS, exact = false)
    }

    /** 하루 마감 아래, 카드 입금을 확인한 날. */
    private fun settled(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        val day = DailySales(today.minusDays(3), sales = 820_000, card = 560_000, cash = 260_000, cardSettled = true)
        runBlocking { app.container.moneyRepository.save(day) }
        content {
            Surface(color = MaterialTheme.colorScheme.background) {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                    androidx.compose.material3.Text("10월 11일 (일) 매출 820,000원", style = MaterialTheme.typography.titleMedium)
                    com.bamnat.app.ui.money.SalesDayExtras(day)
                }
            }
        }
        settle()
    }

    /** 현장: 3일짜리 욕실 공사를 고치는 창. */
    private fun job(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        val j = Job(date = today.plusDays(2), endDate = today.plusDays(4), title = "욕실 리모델링", client = "가나다 인테리어", amount = 3_000_000,
            due = today.plusDays(14))
        content { com.bamnat.app.ui.money.JobDialog(today, j, onSave = {}, onDismiss = {}) }
        settle()
    }

    /** 문자 붙여 넣기에 카드 알림 문자 몇 달치(2만 자가 넘게). */
    private fun paste(): Screen.() -> Unit = style(WorkStyle.DAY) {
        val shops = listOf("김밥천국" to 8_500L, "메가커피" to 3_200L, "이마트" to 64_300L, "GS25" to 5_400L, "카카오T" to 12_800L)
        val text = (0 until 400).joinToString("") { k ->
            val (shop, won) = shops[k % shops.size]
            val (m, d) = if (k < 200) 9 to 1 + k % 30 else 10 to 1 + k % 13
            "[Web발신]\n신한카드(1234)승인 홍*동 ${com.bamnat.core.text.Numbers.grouped(won + k % 7 * 100)}원(일시불)" +
                String.format(java.util.Locale.ROOT, "%02d/%02d %02d:%02d", m, d, 8 + k % 12, k * 7 % 60) + " $shop\n"
        }
        content { com.bamnat.app.ui.money.PasteDialog(emptyList(), emptyMap(), onSave = {}, onDismiss = {}, initialText = text) }
        // No scroll: a drag on the sheet would ask '적은 내용을 버릴까요?' (조건 10), and the line is on the first screen.
        settle()
    }

    @Test fun categoriesLight() = shoot("rvk7_01_categories_folded", FOLDED, categories().then {
        assertTrue("지출 탭에 남은 고침", appears("지출, 저장 안 한 고침이 있어요", exact = false))
        assertTrue("두 종류를 한 번에", appears("지출·수입 분류 저장", exact = false))
    })
    @Test fun categoriesDarkBig() = shoot("rvk7_02_categories_folded_dark_200", DARK, 2f, categories())
    @Test fun categoriesWide() = shoot("rvk7_03_categories_unfolded", UNFOLDED, categories())
    @Test fun countingLight() = shoot("rvk7_04_stats_counting_folded", FOLDED, counting().then {
        assertTrue(appears("세는 중이에요", exact = true))
    })
    @Test fun countingDarkBig() = shoot("rvk7_05_stats_counting_folded_dark_200", DARK, 2f, counting())
    @Test fun depositsLight() = shoot("rvk7_06_card_deposits_folded", FOLDED, deposits().then {
        assertTrue("여섯 줄 뒤 나머지", appears("건 더 보기", exact = false))
    })
    @Test fun depositsDarkBig() = shoot("rvk7_07_card_deposits_folded_dark_200", DARK, 2f, deposits())
    @Test fun depositsWide() = shoot("rvk7_08_card_deposits_unfolded", UNFOLDED, deposits(wide = true))
    @Test fun settledLight() = shoot("rvk7_09_card_settled_folded", FOLDED, settled().then {
        assertTrue(appears("확인 풀기", exact = true))
    })
    @Test fun settledDarkBig() = shoot("rvk7_10_card_settled_folded_dark_200", DARK, 2f, settled())
    @Test fun jobLight() = shoot("rvk7_11_job_date_folded", FOLDED, job().then {
        assertTrue("작업 날짜를 바꿀 줄", appears("작업 날짜", exact = true))
    })
    @Test fun jobDarkBig() = shoot("rvk7_12_job_date_folded_dark_200", DARK, 2f, job())
    @Test fun pasteLight() = shoot("rvk7_13_paste_cut_folded", FOLDED, paste().then {
        assertTrue("말없이 자르지 않아요", appears(AFTER_PASTE, exact = false))
    })
    @Test fun pasteDarkBig() = shoot("rvk7_14_paste_cut_folded_dark_200", DARK, 2f, paste())
    @Test fun installmentLight() = shoot("rvk7_15_installment_line_folded", FOLDED, ledgerPage("내역", false) {
        settle()
        reveal("라마바전자", exact = false)
    })
    @Test fun installmentDarkBig() = shoot("rvk7_16_installment_line_folded_dark_200", DARK, 2f, ledgerPage("내역", false) {
        settle()
        reveal("라마바전자", exact = false)
    })

    private fun (Screen.() -> Unit).then(more: Screen.() -> Unit): Screen.() -> Unit = { this@then(this); more() }

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val AFTER_CATEGORIES = "분류 저장"
        const val AFTER_DEPOSITS = "들어올 카드 매출"
        const val AFTER_PASTE = "앞 20,000자만 읽었어요"
    }
}
