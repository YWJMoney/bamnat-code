package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.pay.PayPeriod
import androidx.compose.foundation.verticalScroll
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVJ-18] 뿌리 고침 6 '굳은 달': the 급여 screen's "이대로 마감할까요?" card (9월 급여, 10/10 지급, 오늘 10/14), the 날짜별 내역's
 * 합 line and 도구 › ○월 마감 with 저절로 마감, light·dark × 글자 100%·200% × 접은·편 폭. Written against what both the old and new
 * screens show, so the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjFrozenScreenshotTest : ScreenshotBase() {
    private val paidOnTheTenth: (com.bamnat.core.pay.PayProfile) -> com.bamnat.core.pay.PayProfile =
        { it.copy(period = PayPeriod(payDay = 10, payNextMonth = true)) }

    private fun ask(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        if (scrolledTo("이대로 마감할까요?")) reveal("이대로 마감할까요?")
    }

    private fun days(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        reveal("일 더 보기")
    }

    private fun tools(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        tapText("도구", exact = true, scrolls = 0)
        settle()
        reveal("이 달 마감하기")
    }

    /** 2.0.0 [RVJ-20] 검토 BOSS-62: 가게 › 직원 근무표, three staff (the week grid was 456dp wide) and the 요율 line. */
    private fun staffWeek(bottom: Boolean): Screen.() -> Unit = {
        val roster = com.bamnat.core.work.StaffRoster(listOf(
            com.bamnat.core.work.Staff("a", "김민지", 10_320, shifts = java.time.DayOfWeek.entries.take(5).associateWith {
                com.bamnat.core.work.StaffShift(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(13, 0)) }),
            com.bamnat.core.work.Staff("b", "이준호", 10_320, insured = true, shifts = java.time.DayOfWeek.entries.drop(2).associateWith {
                com.bamnat.core.work.StaffShift(java.time.LocalTime.of(13, 0), java.time.LocalTime.of(21, 30)) }),
            com.bamnat.core.work.Staff("c", "박서연", 10_320, shifts = listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY).associateWith {
                com.bamnat.core.work.StaffShift(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(18, 0)) }),
        ))
        com.bamnat.app.data.WorkToolsStore.get(app).update(com.bamnat.app.data.PersonalCalendar.MAIN) { it.copy(roster = roster) }
        settle()
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.bamnat.app.ui.work.ShopToolCards(com.bamnat.app.data.PersonalCalendar.MAIN, com.bamnat.core.money.MoneyBook(), java.time.YearMonth.from(com.bamnat.core.time.Today.date()))
            }
        }
        settle()
        tapText("근무표", exact = true, scrolls = 0)
        settle()
        if (bottom) reveal("4대보험 사장님 몫은")
    }

    @Test fun weekLight() = shoot("rvj_19_staff_week_folded", FOLDED, staffWeek(false))
    @Test fun weekDarkBig() = shoot("rvj_20_staff_week_folded_dark_200", DARK, 2f, staffWeek(false))
    @Test fun weekWide() = shoot("rvj_21_staff_week_unfolded", UNFOLDED, staffWeek(false))
    @Test fun weekRates() = shoot("rvj_22_staff_rates_folded", FOLDED, staffWeek(true))

    /**
     * 2.0.0 [RVJ-21] 검토 HQ-07 (조건 12): at 200% on the folded width the 항목별 내역's names broke inside the word ('연장근/로') and the
     * 추정 chip touched the amount. Each line is now [com.bamnat.app.ui.components.AmountRow]: nothing cut, and a one-word name stays whole.
     */
    @Test fun itemsBigNothingCut() = shoot("rvj_23_items_folded_200", FOLDED, 2f) {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        reveal("항목별 내역", exact = true)
        val m = app.resources.displayMetrics
        val all = nodes(merged = false)
        val cuts = LayoutCheck.cuts(all, m.widthPixels, m.heightPixels)
        LayoutCheck.report("rvj_23_items", cuts)
        org.junit.Assert.assertEquals("$cuts", emptyList<LayoutCheck.Cut>(), cuts)
        org.junit.Assert.assertEquals(emptyList<String>(), brokenWords(all, m.heightPixels))
    }

    /** [RVJ-21] At the usual size a folded phone's line stays one line: the name, the chip and the amount side by side (조건 1: 목록이 길어지지 않게). */
    @Test fun itemsStayOnOneLineAtTheUsualSize() = shoot("rvj_27_items_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true, adjust = paidOnTheTenth).invoke(this)
        reveal("항목별 내역", exact = true)
        val all = nodes(merged = false)
        fun one(text: String) = all.first { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text == text } == true }.boundsInWindow
        val name = one("연장근로")
        val amount = all.filter { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text.endsWith("원") } == true }
            .map { it.boundsInWindow }.filter { it.left > name.right && it.top < name.bottom && it.bottom > name.top }
        org.junit.Assert.assertTrue("금액이 이름 옆 줄에 있어야 해요: $name", amount.isNotEmpty())
    }

    /** One Korean word (no space, up to 8 letters) laid out on more than one line, like '연장근/로'. */
    private fun brokenWords(nodes: List<androidx.compose.ui.semantics.SemanticsNode>, height: Int): List<String> = buildList {
        for (n in nodes) {
            val b = n.boundsInWindow
            if (b.height < 1f || b.bottom <= 0f || b.top >= height) continue
            val action = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action ?: continue
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            if (action(results) != true) continue
            for (r in results) {
                val t = r.layoutInput.text.text
                if (t.length in 2..8 && t.none { it.isWhitespace() } && t.any { it in '가'..'힣' } && r.lineCount > 1) add(t)
            }
        }
    }

    @Test fun askLight() = shoot("rvj_12_close_ask_folded", FOLDED, ask())
    @Test fun askDarkBig() = shoot("rvj_13_close_ask_folded_dark_200", DARK, 2f, ask())
    @Test fun askWide() = shoot("rvj_14_close_ask_unfolded", UNFOLDED, ask())
    @Test fun daysLight() = shoot("rvj_15_day_split_folded", FOLDED, days())
    @Test fun daysBig() = shoot("rvj_16_day_split_folded_200", FOLDED, 2f, days())
    @Test fun toolsLight() = shoot("rvj_17_close_month_folded", FOLDED, tools())
    @Test fun toolsDarkBig() = shoot("rvj_18_close_month_folded_dark_200", DARK, 2f, tools())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
