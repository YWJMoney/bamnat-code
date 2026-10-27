package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.pay.PayslipFillDialog
import com.bamnat.app.ui.pay.RuleTarget
import com.bamnat.app.ui.pay.rulesheet.CellEffect
import com.bamnat.app.ui.pay.rulesheet.HourlyCard
import com.bamnat.app.ui.pay.rulesheet.RuleSheetEditor
import com.bamnat.app.ui.pay.rulesheet.RuleSheetHost
import com.bamnat.app.ui.pay.rulesheet.SheetPreview
import com.bamnat.app.ui.pay.rulesheet.rememberRuleSheetState
import com.bamnat.core.pay.CommonTerms
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HourlySource
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [PHOTO-30..36][RULE-ED-01..08][HOURLY-04] 1.30 묶음 AW's screenshots, numbers 1410~1429: what a photo was read as on one full screen
 * with two tabs (명세서 줄 · 회사 규칙) and the month under them; a payslip line and a rule 칸 fixed where they are; '칸 더하기' in two
 * steps (무엇을 → 얼마·언제) with what it adds to the month; the 통상시급 card by where its value came from (명세서·회사 규칙·광장·직접)
 * and "통상임금이 기본급의 몇 %인가요?"; '사진 없이 칸으로 적기' end to end; at 320 wide, 200% text and dark. The payslip is glyph boxes
 * drawn here (화학 공장 명세서 꼴, 이름 지움), never a real photo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AWScreenshotTest : ScreenshotBase() {
    private val MONTH = YearMonth.of(2026, 11)

    // ---- 사진 결과 화면 (PHOTO-30..34) ----
    private val SLIP = listOf("총지급액" to "9,871,085", "공제총액" to "3,220,588", "차감지급액" to "6,650,497")
    private val EARN = listOf(
        "기본급I" to "2,397,500", "기본급II" to "158,330", "자격수당" to "57,500", "상여금" to "2,555,830",
        "휴일근로수당" to "2,626,592", "휴일연장수당" to "1,383,555", "야간근무수당" to "691,778", "지급계" to "9,871,085",
    )
    private val DED = listOf(
        "소득세" to "1,731,400", "주민세" to "173,130", "건강보험" to "280,730", "고용보험" to "62,110", "국민연금" to "313,020",
        "노동조합" to "25,558", "사택관리비" to "580,890", "기타공제" to "53,750", "공제계" to "3,220,588",
    )

    private fun words(): List<OcrLine> = buildList {
        fun cell(t: String, x: Int, y: Int) { val w = t.length * 22; add(OcrLine(t, x - w / 2, y - 14, x + w / 2, y + 14)) }
        SLIP.forEachIndexed { i, (n, a) -> cell(n, 100 + i * 560, 60); cell(a, 420 + i * 560, 90) }
        cell("지급내역", 250, 150); cell("공제내역", 1000, 150)
        EARN.forEachIndexed { i, (n, a) -> cell(n, 250, 200 + i * 60); cell(a, 700, 215 + i * 60) }
        DED.forEachIndexed { i, (n, a) -> cell(n, 1000, 200 + i * 60); cell(a, 1450, 215 + i * 60) }
        val y = 200 + 9 * 60 + 40
        listOf("휴일근로시간수" to "81.00", "휴일연장근로시간수" to "32.00", "야간근로시간수" to "64.00").forEachIndexed { i, (h, v) ->
            cell(h, 300 + i * 450, y); cell(v, 300 + i * 450, y + 50)
        }
    }

    private fun scan(): PayslipScanner.Scan {
        val boxes = words()
        val photo = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f }
            boxes.forEach { canvas.drawText(it.text, it.left / 3f, it.bottom / 3f, paint) }
        }
        return PayslipScanner.put(listOf(OcrRows.join(boxes)), photo, null, listOf(boxes))
    }

    /** A monthly setup of 기본급 2,500,000 on 209 hours, for the 칸's amounts and the 통상시급 card. */
    private val PROFILE = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(MONTH.atDay(1).minusYears(1), 2_500_000))))
    private val SNAPSHOT = ScheduleSnapshot(emptyList(), emptyList(), emptyList())

    /** What the screen is told the month comes to: the setup's base and each 칸's own amount, as the pay screen works it out. */
    private fun previewOf(changes: List<RuleChange>, gross: Long?): SheetPreview {
        val p = com.bamnat.core.pay.RuleText.apply(PROFILE, changes, MONTH)
        val items = p.items.sumOf { it.amount.on(MONTH).value }
        val total = 2_500_000 + items
        return SheetPreview(MONTH, total, com.bamnat.core.pay.HourlyFacts.of(p, MONTH)?.hourly, gross,
            changes = changes.map { it.summary }, close = gross != null && kotlin.math.abs(gross - total) < 30_000, profile = p)
    }

    private var applied: List<RuleChange> = emptyList()

    private fun result(byHand: Boolean = false, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        val s = if (byHand) null else scan()
        content {
            PayslipFillDialog("명세서 사진으로 채우기", onDismiss = {}, initialScan = s, rulesOnly = byHand,
                rules = RuleTarget(emptyList(), emptyList(), true, PROFILE, SNAPSHOT, { _, _, _, made ->
                    // A 칸's own amount this month, as PayViewModel.effectOf works it out for a one-a-month allowance.
                    (made as? com.bamnat.app.ui.pay.MadeItem.Item)?.item?.amount?.on(MONTH)?.value?.takeIf { it > 0 }
                        ?.let { com.bamnat.app.ui.pay.ItemEffect(it, 1) }
                }) { changes, _ -> applied = changes },
                preview = { read, changes -> previewOf(changes, read.gross) },
                startByHand = byHand,
            ) {}
        }
        settle()
        then()
    }

    private fun Screen.rulesTab() { tapText("회사 규칙", prefix = true); settle() }

    @Test fun resultSlipTab() = shoot("1410_aw_result_slip_tab", FOLDED, result {
        assertTrue(shows("명세서 줄", exact = false))
        assertTrue(shows("회사 규칙", exact = false))
        reveal("2,397,500", exact = false)
    })
    @Test fun resultRulesTab() = shoot("1411_aw_result_rules_tab", FOLDED, result {
        rulesTab()
        reveal("시간당 기본 단가", exact = true)
        reveal("명세서로 보면 통상시급", exact = false)
    })
    @Test fun resultLineFixedInPlace() = shoot("1412_aw_slip_line_inline", FOLDED, result {
        tapText("자격수당", exact = false)
        reveal("이 줄 고치기", exact = true)
        assertTrue("고치는 줄은 목록 안에서 열려야 해요", shows("더 보기", exact = true) || shows("이 줄 빼기", exact = true))
    })
    @Test fun resultCellInPlace() = shoot("1413_aw_rule_cell_inline", FOLDED, editor(RULES) {
        tapText("야간 근무", exact = false)
        reveal("다 됐어요", exact = true)
    })
    @Test fun resultSmallBig() = shoot("1414_aw_result_320_200", SMALL_PHONE, 2f, result())
    @Test fun resultRulesSmallBig() = shoot("1415_aw_rules_320_200", SMALL_PHONE, 2f, result { rulesTab() })
    @Test fun resultRulesDark() = shoot("1416_aw_rules_dark", FOLDED_NIGHT, result { rulesTab() })

    // ---- 칸만으로 적기 (RULE-ED-01..08) ----
    /** A 계산 방법 page's rows, as AQ's photo has them. */
    private val RULES = """
        휴일연장수당  해당시간 × 2 × ( 통상임금 / 시수 )
        야간근로수당  해당시간 × 0.5 × ( 통상임금 / 시수 )
    """.trimIndent()

    private fun editor(words: String = "", then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            val state = rememberRuleSheetState {
                com.bamnat.core.pay.RuleSheet.read(com.bamnat.core.pay.RuleText.read(words), from = com.bamnat.core.pay.CellFrom.TEXT)
            }
            val host = remember {
                RuleSheetHost(PROFILE, SNAPSHOT, MONTH, { change ->
                    (change as? RuleChange.Item)?.amount?.takeIf { it > 0 }?.let { CellEffect(it, null) }
                })
            }
            RuleSheetEditor("회사 규칙 칸으로 적기", state, host, onDismiss = {}, onDone = {}, preview = { previewOf(it, null) })
        }
        settle()
        then()
    }

    @Test fun editorEmpty() = shoot("1417_aw_editor_empty", FOLDED, editor { reveal("칸 더하기", exact = false) })
    @Test fun addStepOne() = shoot("1418_aw_add_step_what", FOLDED, editor {
        tapText("칸 더하기", exact = false)
        reveal("무엇을 더할까요?", exact = true)
        reveal("자격수당", exact = true)
    })
    @Test fun addStepTwo() = shoot("1419_aw_add_step_how_much", FOLDED, editor {
        tapText("칸 더하기", exact = false)
        tapText("자격수당", exact = true)
        reveal("얼마·언제 주나요?", exact = true)
        typeInto("한 달 금액", "57500")
        reveal("11월 급여에", exact = false)
        assertTrue("더하기 전에 이번 달에 더해지는 금액이 보여야 해요", shows("57,500", exact = false))
    })
    @Test fun addedShowsTheMonth() = shoot("1420_aw_added_preview", FOLDED, editor {
        tapText("칸 더하기", exact = false)
        tapText("자격수당", exact = true)
        typeInto("한 달 금액", "57500")
        tapText("더하기", exact = true)
        settle()
        waitFor(400)
        reveal("자격수당", exact = false)
        assertTrue(shows("2,557,500", exact = false))
    })
    @Test fun addStepsBigText() = shoot("1421_aw_add_steps_200", FOLDED, 2f, editor {
        tapText("칸 더하기", exact = false)
        tapText("자격수당", exact = true)
    })
    @Test fun addSearchNew() = shoot("1422_aw_add_new_name", FOLDED, editor {
        tapText("칸 더하기", exact = false)
        typeInto("이름으로 찾거나 새로 적기", "현장수당")
        reveal("‘현장수당’ 새로 만들기", exact = false)
    })

    /** [RULE-ED-01] No photo at all: the pick step's '사진 없이 칸으로 적기', one 칸, applied as a 회사 공통 allowance. */
    @Test fun byHandEndToEnd() = shoot("1423_aw_by_hand_end_to_end", FOLDED, {
        applied = emptyList()
        content {
            PayslipFillDialog("회사 계산 방식 찾기", onDismiss = {}, rulesOnly = true,
                rules = RuleTarget(emptyList(), emptyList(), true, PROFILE, SNAPSHOT) { changes, _ -> applied = changes }) {}
        }
        settle()
        tapText("사진 없이 칸으로 적기", exact = true)
        reveal("회사 규칙 칸으로 적기", exact = true)
        tapText("칸 더하기", exact = false)
        tapText("자격수당", exact = true)
        typeInto("한 달 금액", "57500")
        tapText("더하기", exact = true)
        settle()
        tapText("규칙으로 계산하기", exact = true)
        settle()
        val item = applied.filterIsInstance<RuleChange.Item>().single()
        assertEquals("자격수당", item.item.name)
        assertEquals(57_500L, item.amount)
    })

    // ---- 통상시급 카드 (HOURLY-04) ----
    private fun card(start: CommonTerms, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            var terms by remember { mutableStateOf(start) }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) { HourlyCard(PROFILE, MONTH, terms, { terms = it }) }
        }
        settle()
        then()
    }
    private val READ_HOURS = RuleFinding("휴일근로수당 81시간 2,626,592원", RuleChange.MonthlyHours(180, HourlySource.SLIP), 0.9)

    @Test fun cardFromPayslip() = shoot("1424_aw_hourly_from_slip", FOLDED, card(CommonTerms(offers = listOf(READ_HOURS)).take(READ_HOURS)) {
        reveal("명세서에서", exact = false)
        assertTrue(shows("180시간 · 명세서", exact = false))
    })
    @Test fun cardFromRules() = shoot("1425_aw_hourly_from_rules", FOLDED, card(CommonTerms().withHours(226, HourlySource.RULES)) {
        reveal("회사 규칙에서", exact = false)
    })
    @Test fun cardFromPlaza() = shoot("1426_aw_hourly_from_plaza", FOLDED, card(CommonTerms().withHours(243, HourlySource.PLAZA)) {
        reveal("광장에서", exact = false)
    })
    @Test fun cardTyped() = shoot("1427_aw_hourly_typed", FOLDED, card(CommonTerms(offers = listOf(READ_HOURS))) {
        reveal("이 값 쓰기", exact = true)
        tapText("226시간", exact = true)
        reveal("직접 넣음", exact = false)
    })
    @Test fun cardPercentOfBase() = shoot("1428_aw_hourly_percent", FOLDED, card(CommonTerms()) {
        tapText("통상시급을 모르겠어요", exact = true)
        reveal("통상임금이 기본급의 몇 %인가요?", exact = true)
        tapText("120%", exact = true)
        reveal("이 값으로 계산하기", exact = true)
        assertTrue(shows("× 120%", exact = false))
    })
    @Test fun cardBigDark() = shoot("1429_aw_hourly_200_dark", FOLDED_NIGHT, 2f, card(CommonTerms(offers = listOf(READ_HOURS)).take(READ_HOURS)))

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
