package com.bamnat.app.screenshots

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.monthSwipe
import com.bamnat.core.money.Challenges
import com.bamnat.core.money.Envelope
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 K 8차 [RVK-43]: 가계부 낱말표 '들어온 돈 − 나간 돈' (검토 MONEY-25), 결제수단 종류를 바꿀 때 잔액이 지워진다는 한 줄 (U06a-10),
 * 분류 보드 '외 N건 더 보기' (U06a-13), 통계 '많이 쓴 곳' 아래 '예산 정하기' (U06a-27), 끝난 도전의 다음 도전 (U06a-28), 내년 목표일의 연도
 * (U06a-25), 일감 잔금의 '곧 받을 날' (U06b-15), 가로로 미는 줄 끝에서 달이 넘어가지 않음 (MONEY-32). Light·dark × 글자 100%·200% ×
 * 접은·편 폭. The code before draws the same scenes (the PR's 'before'). Today is 2026-10-14. Amounts and names are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkEightScreenshotTest : ScreenshotBase() {
    /** 요약's first card: 10월's money in less money out. */
    private fun hero(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledgerPage(null, wide)()
        settle()
    }

    /** 결제수단 고치기 on 국민 월급통장 (1,850,000원 to start), turned into a 신용카드. */
    private fun account(): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        val book = runBlocking { app.container.moneyRepository.book.first() }
        val bank = book.accounts.first { it.name == "국민 월급통장" }
        content { com.bamnat.app.ui.money.AccountEditor(bank, null, book, onSave = {}, onRemove = {}, onDismiss = {}) }
        settle()
        tapText("신용카드", exact = true, scrolls = 0)
        settle()
    }

    /** 분류 보드 with 36 카페 lines this month. */
    private fun board(): Screen.() -> Unit = style(WorkStyle.DAY) {
        val lines = (0 until 36).map { k ->
            MoneyEntry(id = 1_000L + k, date = today.minusDays((k % 13).toLong()), kind = MoneyKind.EXPENSE, amount = 3_200L + k % 5 * 700,
                category = "카페", merchant = listOf("메가커피", "빽다방", "컴포즈커피", "스타벅스")[k % 4])
        } + (0 until 6).map { k ->
            MoneyEntry(id = 2_000L + k, date = today.minusDays(k.toLong()), kind = MoneyKind.EXPENSE, amount = 8_000L + k * 1_500, category = "식비",
                merchant = "김밥천국")
        }
        content {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    com.bamnat.app.ui.money.LedgerCategoryBoard(lines, onOpen = {})
                }
            }
        }
        settle()
        reveal(AFTER_BOARD, exact = false)
    }

    /** 통계 with 교통 up from last month and no budget for it. */
    private fun stats(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        runBlocking {
            val money = app.container.moneyRepository
            listOf(3 to 46_000L, 5 to 38_500L, 8 to 52_000L, 10 to 41_200L).forEach { (d, a) ->
                money.save(MoneyEntry(date = today.withDayOfMonth(d), kind = MoneyKind.EXPENSE, amount = a, category = "교통", merchant = "카카오T"))
            }
        }
        settle()
        tab(1, wide)
        tapText("통계", exact = true, scrolls = 0)
        settle()
        reveal(AFTER_STATS, exact = false)
    }

    /** 무지출 날 도전, its seven days over. */
    private fun challenge(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        ledger()
        val book = runBlocking { app.container.moneyRepository.book.first() }
        content {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    com.bamnat.app.ui.money.ChallengeCard(Challenges.Run(Challenges.Kind.NO_SPEND, today.minusDays(8)), book, emptyList(), today,
                        isNight = { false }, picking = false, onStart = {}, onStop = {}, onClosePicker = {})
                }
            }
        }
        settle()
    }

    /** 모으기 봉투: 제주 여행 by next March, 이사 by 12월. */
    private fun envelopes(): Screen.() -> Unit = style(WorkStyle.DAY) {
        ledger()
        val book = runBlocking { app.container.moneyRepository.book.first() }
        val goals = listOf(
            Envelope(id = 1, name = "제주 여행", target = 1_200_000, due = LocalDate.of(2027, 3, 1), created = today.minusDays(20)),
            Envelope(id = 2, name = "이사", target = 3_000_000, due = LocalDate.of(2026, 12, 20), created = today.minusDays(40)),
        )
        val actions = object : com.bamnat.app.ui.money.EnvelopeActions {
            override fun save(e: Envelope) = Unit
            override fun delete(e: Envelope) = Unit
            override fun move(amounts: Map<Long, Long>, date: LocalDate, note: String, entryId: Long?) = Unit
        }
        content {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    com.bamnat.app.ui.money.EnvelopesCard(book, goals, emptyList(), WorkStyle.DAY, today, perShift = null, pro = true, actions = actions)
                }
            }
        }
        settle()
    }

    /** 현장 받을 돈: 욕실 공사의 잔금, 입금 약속일 이틀 뒤. */
    private fun owedJob(): Screen.() -> Unit = owed(WorkStyle.FIELD) {
        runBlocking {
            app.container.moneyRepository.save(Job(date = today.minusDays(9), title = "욕실 리모델링", client = "이OO 고객", amount = 2_400_000,
                deposit = 800_000, depositPaid = true, due = today.plusDays(2)))
        }
        settle()
        reveal("욕실 리모델링", exact = false)
    }

    @Test fun heroLight() = shoot("rvk8_01_hero_words_folded", FOLDED, hero().then {
        assertTrue("가계부 낱말", appears("들어온 돈 − 나간 돈", exact = false))
    })
    @Test fun heroDarkBig() = shoot("rvk8_02_hero_words_folded_dark_200", DARK, 2f, hero())
    @Test fun heroWide() = shoot("rvk8_03_hero_words_unfolded", UNFOLDED, hero(wide = true))
    @Test fun accountLight() = shoot("rvk8_04_account_kind_folded", FOLDED, account().then {
        assertTrue("잔액이 지워진다고 미리", appears("처음 들어 있던 돈 1,850,000원은 지워져요", exact = false))
    })
    @Test fun accountDarkBig() = shoot("rvk8_05_account_kind_folded_dark_200", DARK, 2f, account())
    @Test fun boardLight() = shoot("rvk8_06_board_more_folded", FOLDED, board().then {
        assertTrue("서른 줄 뒤 나머지", appears("외 6건 더 보기", exact = true))
    })
    @Test fun boardDarkBig() = shoot("rvk8_07_board_more_folded_dark_200", DARK, 2f, board())
    @Test fun statsLight() = shoot("rvk8_08_stats_budget_folded", FOLDED, stats().then {
        assertTrue("본 뒤 할 일", appears("예산 정하기", exact = false))
    })
    @Test fun statsDarkBig() = shoot("rvk8_09_stats_budget_folded_dark_200", DARK, 2f, stats())
    @Test fun statsWide() = shoot("rvk8_10_stats_budget_unfolded", UNFOLDED, stats(wide = true))
    @Test fun budgetOpened() = shoot("rvk8_11_budget_focus_folded", FOLDED, stats().then {
        tapText("예산 정하기", exact = false, scrolls = 0)
        settle()
        assertTrue(appears("교통 예산", exact = false))
    })
    @Test fun challengeLight() = shoot("rvk8_12_challenge_next_folded", FOLDED, challenge().then {
        assertTrue("다음 도전 두 개", appears("다음 도전", exact = true) && appears("배달 줄이기 주간", exact = false))
    })
    @Test fun challengeDarkBig() = shoot("rvk8_13_challenge_next_folded_dark_200", DARK, 2f, challenge())
    @Test fun envelopesLight() = shoot("rvk8_14_envelope_year_folded", FOLDED, envelopes().then {
        assertTrue("내년 목표일의 연도", appears("2027년 3월 1일까지", exact = false))
    })
    @Test fun envelopesDarkBig() = shoot("rvk8_15_envelope_year_folded_dark_200", DARK, 2f, envelopes())
    @Test fun owedLight() = shoot("rvk8_16_owed_job_soon_folded", FOLDED, owedJob().then {
        assertTrue("일감 잔금도 곧 받을 날", appears("곧 받을 날", exact = false))
    })

    /** MONEY-32: dragging a sideways row past its end does not turn the month; a drag elsewhere still does. */
    @Test fun aSidewaysRowKeepsItsDragAtTheEnd() {
        var steps = 0
        shoot("rvk8_17_sideways_row_folded", FOLDED) {
            content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().monthSwipe(Unit) { steps += it }) {
                        Column(Modifier.fillMaxWidth().padding(top = 120.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                (1..12).forEach { k -> Text("가로 줄 $k", Modifier.width(96.dp).padding(8.dp)) }
                            }
                            Text("빈 자리", Modifier.padding(top = 240.dp, start = 16.dp))
                        }
                    }
                }
            }
            settle()
            swipeRowLeft("가로 줄 1", until = "없는 글", dp = 260, times = 6)
            assertEquals("the row's end does not turn the month", 0, steps)
            swipeRowLeft("빈 자리", until = "없는 글", dp = 200, times = 1)
            assertEquals("elsewhere the swipe still turns it", 1, steps)
        }
    }

    private fun (Screen.() -> Unit).then(more: Screen.() -> Unit): Screen.() -> Unit = { this@then(this); more() }

    private companion object {
        /** The folded phone at night, as [RvkTwoScreenshotTest] draws it. */
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val AFTER_BOARD = "더 보기"
        const val AFTER_STATS = "분류별 지출"
    }
}
