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
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.pay.MorePageButton
import com.bamnat.app.ui.pay.PayslipFillDialog
import com.bamnat.app.ui.pay.RuleTarget
import com.bamnat.app.ui.pay.ScanButtonRow
import com.bamnat.app.ui.pay.SlipCheck
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PHOTO-20][PHOTO-26][PHOTO-28][PHOTO-29][HOURLY-01] 1.30 묶음 AV's screenshots, numbers 1400~1409: the scanner as the first,
 * big button (and '사진 찍기' on a phone without it); the yellow lines looked at one by one ('노란 n줄 확인하기', [맞아요]·
 * [고치기]); '+ 한 장 더' for a long payslip; '사진이 달라요' with 통상시급·기준 시간; the 기준 시간 the payslip's lines come to
 * (화학 공장 명세서 꼴, 이름 지움), at 320 wide, 200% text and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AVScreenshotTest : ScreenshotBase() {
    private fun buttons(scanner: Boolean): Screen.() -> Unit = {
        content {
            Column(Modifier.padding(16.dp)) { ScanButtonRow(scanner = scanner, enabled = true, onScanner = {}, onPick = {}, onTake = {}) }
        }
        settle()
    }

    @Test fun scannerFirst() = shoot("1400_av_scanner_first", FOLDED, buttons(true).then {
        reveal("스캐너로 찍기", exact = true)
        reveal("비스듬히 찍어도 반듯하게 펴서", exact = false)
    })
    @Test fun noScanner() = shoot("1401_av_no_scanner", FOLDED, buttons(false).then {
        reveal("사진 찍기", exact = true)
        assertTrue(!sees("스캐너로 찍기", exact = true))
    })

    /** 지급 three lines and its total, 공제 two and its total; two lines yellow. The sums meet. */
    private val READ = PayslipRead(listOf(
        SlipLine("기본급", SlipRole.BASE, 2_397_500), SlipLine("자격수당", SlipRole.ALLOWANCE, 57_500),
        SlipLine("야간근무수당", SlipRole.NIGHT, 691_778, 64 * 60), SlipLine("지급계", SlipRole.GROSS, 3_146_778),
        SlipLine("소득세", SlipRole.DEDUCTION, 173_130), SlipLine("건강보험", SlipRole.DEDUCTION, 280_730),
        SlipLine("공제계", SlipRole.DEDUCTION_TOTAL, 453_860),
    ))

    private fun check(reviewing: Boolean, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            var read by remember { mutableStateOf(READ) }
            var unsure by remember { mutableStateOf(setOf(1, 5)) }
            var same by remember { mutableStateOf<Boolean?>(null) }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SlipCheck(read, unsure, same, fromPhoto = true, onAnswer = { same = it }, onChange = { r, u -> read = r; unsure = u },
                    reviewingAtFirst = reviewing)
            }
        }
        settle()
        then()
    }

    @Test fun yellowFirst() = shoot("1402_av_yellow_first", FOLDED, check(false) {
        reveal("노란 줄 2개만 사진과 맞는지 봐 주세요", exact = false)
        reveal("노란 2줄 확인하기", exact = true)
        assertTrue("노란 줄이 있으면 '네, 같아요' 한 번으로 넘기지 않아요", !sees("네, 같아요", exact = true))
    })
    @Test fun yellowOneByOne() = shoot("1403_av_yellow_one_by_one", FOLDED, check(false) {
        tapText("노란 2줄 확인하기", exact = true)
        settle()
        reveal("맞아요", exact = true)
        reveal("노란 줄마다", exact = false)
    })
    @Test fun yellowAllAnswered() = shoot("1404_av_yellow_answered", FOLDED, check(true) {
        tapText("맞아요", exact = true)
        settle()
        tapText("맞아요", exact = true)
        settle()
        // The sums meet: the last 'that is right' is the photo looked at.
        reveal("사진과 같다고 확인했어요", exact = false)
    })

    @Test fun morePage() = shoot("1405_av_more_page", FOLDED, {
        val s = PayslipScanner.put(listOf("기본급 2,397,500"), null, null, emptyList())
        content { Column(Modifier.padding(16.dp)) { MorePageButton(s) {} } }
        settle()
        tapText("+ 한 장 더", exact = false)
        settle()
        reveal("사진 고르기", exact = true)
    })

    /** The 화학 공장 명세서's lines (names at x 250, amounts 15 px lower in a column of their own) and its 근태 hours. */
    private val SLIP = listOf("총지급액" to "10,326,809", "공제총액" to "3,220,588", "차감지급액" to "7,106,221")
    private val EARN = listOf(
        "기본급I" to "2,397,500", "기본급II" to "158,330", "자격수당" to "57,500", "위험예지훈련/LMS" to "66,598", "상여금" to "2,555,830",
        "휴일근로수당" to "2,626,592", "휴일연장수당" to "1,383,555", "야간근무수당" to "691,778", "향군수당" to "129,709",
        "소급분(지급)" to "259,417", "지급계" to "10,326,809",
    )
    private val DED = listOf(
        "소득세" to "1,731,400", "주민세" to "173,130", "건강보험" to "280,730", "고용보험" to "62,110", "국민연금" to "313,020",
        "볼링회(남부공장)" to "3,000", "축구회(AB/CD)" to "10,000", "한맥산악회(남부공장)" to "3,000", "OO화학 노동조합" to "25,558",
        "남부_사택관리비" to "580,890", "소급분(공제)" to "37,750", "공제계" to "3,220,588",
    )

    private fun words(): List<OcrLine> = buildList {
        fun cell(t: String, x: Int, y: Int) { val w = t.length * 22; add(OcrLine(t, x - w / 2, y - 14, x + w / 2, y + 14)) }
        SLIP.forEachIndexed { i, (n, a) -> cell(n, 100 + i * 560, 60); cell(a, 420 + i * 560, 90) }
        cell("지급내역", 250, 150); cell("공제내역", 1000, 150)
        EARN.forEachIndexed { i, (n, a) -> cell(n, 250, 200 + i * 60); cell(a, 700, 215 + i * 60) }
        DED.forEachIndexed { i, (n, a) -> cell(n, 1000, 200 + i * 60); cell(a, 1450, 215 + i * 60) }
        val y = 200 + 12 * 60 + 40
        listOf("휴일근로시간수" to "81.00", "휴일연장근로시간수" to "32.00", "야간근로시간수" to "64.00").forEachIndexed { i, (h, v) ->
            cell(h, 300 + i * 450, y); cell(v, 300 + i * 450, y + 50)
        }
    }

    private fun slip(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        val boxes = words()
        val photo = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
            boxes.forEach { canvas.drawText(it.text, it.left / 3f, it.bottom / 3f, paint) }
        }
        val s = PayslipScanner.put(listOf(OcrRows.join(boxes)), photo, null, listOf(boxes))
        content {
            PayslipFillDialog("명세서 사진으로 채우기", onDismiss = {}, initialScan = s,
                rules = RuleTarget(emptyList(), emptyList(), true) { _, _ -> }) {}
        }
        settle()
        then()
    }

    @Test fun photoWrongReasons() = shoot("1406_av_photo_wrong_reasons", FOLDED, slip {
        tapText("사진이 달라요", exact = true)
        settle()
        reveal("통상시급이 달라요", exact = false)
        reveal("기준 시간이 달라요", exact = false)
    })
    @Test fun hoursFromPayslip() = shoot("1407_av_hours_from_payslip", FOLDED, slip {
        reveal("명세서로 보면 통상임금 기준 180시간이에요", exact = false)
    })
    @Test fun yellowSmallBig() = shoot("1408_av_yellow_320_200", SMALL_PHONE, 2f, check(true))
    @Test fun yellowDark() = shoot("1409_av_yellow_dark", FOLDED_NIGHT, check(false))

    private fun (Screen.() -> Unit).then(more: Screen.() -> Unit): Screen.() -> Unit = { this@then(this); more() }

    private fun Screen.sees(text: String, exact: Boolean): Boolean = runCatching { reveal(text, exact = exact); true }.getOrDefault(false)

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
