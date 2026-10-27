package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.pay.PayslipFillDialog
import com.bamnat.app.ui.pay.RuleCells
import com.bamnat.app.ui.pay.RuleTarget
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.RuleText
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [PHOTO-01][PHOTO-06][PHOTO-09][PHOTO-11][PHOTO-12] 1.29 묶음 AQ's screenshots, numbers 1250~1259: a payslip photo whose
 * amounts sit half a row under their names in a column of their own (화학 공장 명세서 꼴, 이름 지움) read as the table it
 * is, with the 통상시급 the payslip's own lines say; '사진이 달라요' and its reasons; the 계산 방법 page's rows as cells to
 * tick, change or put in by hand, at 320 wide, 200% text and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AQScreenshotTest : ScreenshotBase() {
    /** Names at x 250, amounts right-aligned at 760 and 15 px lower; the 근태 table's heads over their hours. */
    private val SLIP = listOf(
        "총지급액" to "9,871,085", "공제총액" to "3,220,588", "차감지급액" to "6,650,497",
    )
    private val EARN = listOf(
        "기본급I" to "2,397,500", "기본급II" to "158,330", "자격수당" to "57,500", "상여금" to "2,555,830",
        "휴일근로수당" to "2,626,592", "휴일연장수당" to "1,383,555", "야간근무수당" to "691,778", "지급계" to "9,871,085",
    )
    private val DED = listOf(
        "소득세" to "1,731,400", "주민세" to "173,130", "건강보험" to "280,730", "고용보험" to "62,110", "국민연금" to "313,020",
        "노동조합" to "25,558", "사택관리비" to "580,890", "공제계" to "3,220,588",
    )
    private val REST = "기타공제" to "53,750"

    private fun words(): List<OcrLine> = buildList {
        fun cell(t: String, x: Int, y: Int) { val w = t.length * 22; add(OcrLine(t, x - w / 2, y - 14, x + w / 2, y + 14)) }
        SLIP.forEachIndexed { i, (n, a) -> cell(n, 100 + i * 560, 60); cell(a, 420 + i * 560, 90) }
        cell("지급내역", 250, 150); cell("공제내역", 1000, 150)
        EARN.forEachIndexed { i, (n, a) -> cell(n, 250, 200 + i * 60); cell(a, 700, 215 + i * 60) }
        (DED.dropLast(1) + REST + DED.last()).forEachIndexed { i, (n, a) -> cell(n, 1000, 200 + i * 60); cell(a, 1450, 215 + i * 60) }
        val y = 200 + 9 * 60 + 40
        listOf("휴일근로시간수" to "81.00", "휴일연장근로시간수" to "32.00", "야간근로시간수" to "64.00").forEachIndexed { i, (h, v) ->
            cell(h, 300 + i * 450, y); cell(v, 300 + i * 450, y + 50)
        }
    }

    private fun scan(): PayslipScanner.Scan {
        val boxes = words()
        val text = OcrRows.join(boxes)
        val photo = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f }
            boxes.forEach { canvas.drawText(it.text, it.left / 3f, it.bottom / 3f, paint) }
        }
        return PayslipScanner.put(listOf(text), photo, null, listOf(boxes))
    }

    private fun slip(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        val s = scan()
        content {
            PayslipFillDialog("명세서 사진으로 채우기", onDismiss = {}, initialScan = s,
                rules = RuleTarget(emptyList(), emptyList(), true) { _, _ -> }) {}
        }
        settle()
        then()
    }

    @Test fun payslipTable() = shoot("1250_aq_payslip_table", FOLDED, slip {
        assertTrue("기본급I의 금액이 반 줄 아래에서 읽혀야 해요", sees("2,397,500", exact = false))
    })
    @Test fun payslipHourly() = shoot("1251_aq_payslip_hourly", FOLDED, slip {
        reveal("명세서로 보면 통상시급 21,618원이에요", exact = false)
    })
    @Test fun payslipWrong() = shoot("1252_aq_photo_wrong", FOLDED, slip {
        tapText("사진이 달라요", exact = true)
        settle()
        assertTrue(sees("숫자가 틀려요", exact = false))
    })
    @Test fun payslipSmallBig() = shoot("1253_aq_payslip_320_200", SMALL_PHONE, 2f, slip())

    private val RULES = """
        휴일근로수당  해당시간 × 1.5 × ( 통상임금 / 시수 )
        평일연장수당  해당시간 × 1.5 × ( 통상임금 / 시수 )
        휴일연장수당  해당시간 × 2 × ( 통상임금 / 시수 )
        야간근로수당  해당시간 × 0.5 × ( 통상임금 / 시수 )
        보상휴가금액(사무직限)  해당시간 × 1.5 × ( 기준급 / 시수 ) ※사무직 평일연장/야간/휴일근로에 대해 보상휴가로 부여함
        휴가보상  해당일수 × 1.5 × ( 기준급 / 시수 × 8 )
        이전 미공제액  직전 지급건 중에 미공제 된 금액으로 이번 지급건에서 공제된 금액
        기준급  기본급, 직책수당, 자격수당, 기술전수교육수당
        통상임금  기본급, 직책수당, 자격수당, 기술전수교육수당 등 소정근로대가성, 정기성, 일률성, 고정성이 인정되는 임금 일체
        본인  배우자  직계존속  직계비속  만20세이하 자녀
        1  1  0  1  0  100%
    """.trimIndent()

    private fun cells(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            val ticks = remember { mutableStateListOf<Pair<RuleFinding, Boolean>>().apply { addAll(RuleText.read(RULES).map { it to true }) } }
            val unread = remember { mutableStateListOf<String>().apply { addAll(RuleText.unread(RULES)) } }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                RuleCells(ticks, unread, RuleTarget(emptyList(), emptyList(), true) { _, _ -> }, YearMonth.of(2026, 11))
            }
        }
        settle()
        then()
    }

    @Test fun ruleCells() = shoot("1254_aq_rule_cells", FOLDED, cells {
        assertTrue(sees("휴일 8시간 초과 가산 100%", exact = false))
        assertTrue(sees("세금 계산 가족 3명", exact = false))
    })
    @Test fun ruleCellsByHand() = shoot("1255_aq_rule_cells_by_hand", FOLDED, cells { reveal("휴가보상", exact = false) })
    @Test fun ruleCellsSmallBig() = shoot("1256_aq_rule_cells_320_200", SMALL_PHONE, 2f, cells())
    @Test fun ruleCellsDark() = shoot("1257_aq_rule_cells_dark", FOLDED_NIGHT, cells())

    private fun Screen.sees(text: String, exact: Boolean): Boolean = runCatching { reveal(text, exact = exact); true }.getOrDefault(false)

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
