package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.imports.RosterPhotoTest
import com.bamnat.app.imports.RosterScanner
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.app.ui.pay.PayslipFillDialog
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.work.WorkStyle
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [READ-15][READ-17][READ-18][PAYS-17][PAYS-19][PAYS-20] 묶음 AJ's screenshots, numbers 1140~1151 (1.27 순번 446~449):
 * "사진과 내용이 같은가요?" over the photo's dates and line (phone, small phone, dark, big text), '다른 곳이 있어요' and one cell
 * fixed, the first setup's card, the payslip's photo and its lines fixed, "이것만 넣으면 정확해져요" and '명세서와 맞춰 보기'
 * under the total, a line's 이 값 고치기, and the company's rules alone worked out. The photos are drawn from the words ML Kit
 * would give ([RosterScanner.fakeReads]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AJScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val SLIP = "2026년 9월 급여명세서\n기본급 2,508,000원\n연장근로수당 288,000원\n야간근로수당 154,000원\n식대 200,000원\n지급합계 3,150,000원\n" +
            "국민연금 141,750원\n소득세 52,000원\n공제총액 193,750원\n실수령액 2,956,250원"
    }

    @After fun noFake() {
        RosterScanner.fakeReads = null
        RosterScanner.fakeImage = null
        PayslipScanner.fake = null
    }

    /** The words drawn as the photo they came from: white paper, black print. */
    private fun picture(words: List<RosterWord>): Bitmap {
        val w = words.maxOf { it.right } + 60
        val h = words.maxOf { it.bottom } + 60
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
        words.forEach { canvas.drawText(it.text, it.left.toFloat(), it.bottom - 8f, paint) }
        return bmp
    }

    /** Four readings of one November roster; two of them read 이수진's 5th as 야간, the photo's own word. */
    private fun fourWays() {
        val reads = (0 until 4).map { way ->
            RosterPhotoTest.roster(0.0) { p, d -> if (p == 1 && d == 5 && way >= 2) "야간" else RosterPhotoTest.codeOf(p, d) }
        }
        RosterScanner.fakeReads = { reads }
        RosterScanner.fakeImage = { picture(reads[2]) }
    }

    private fun asked(then: Screen.(DutyRosterViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        fourWays()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        settle()
        reveal("이수진")
        vm.pick(1)
        settle()
        then(vm)
        settle()
    }

    // ---- 447 듀티표: 사진과 내용이 같은가요? ----
    @Test fun rosterAsk() = shoot("1140_aj_roster_same_ask", PHONE_393, asked { reveal("사진과 내용이 같은가요?") })
    @Test fun rosterAskDark() = shoot("1142_aj_roster_same_dark", FOLDED_NIGHT, asked { reveal("사진과 내용이 같은가요?") })
    @Test fun rosterAskHuge() = shoot("1143_aj_roster_same_huge_text", PHONE, fontScale = 2f, asked { reveal("다른 곳이 있어요") })
    @Test fun rosterFixing() = shoot("1144_aj_roster_fixing", PHONE_393, asked { vm ->
        tapText("다른 곳이 있어요")
        assertTrue(vm.ui.value.same == false)
        reveal("다른 칸을 눌러 그 칸만 고쳐 주세요")
    })
    @Test fun rosterOneCell() = shoot("1145_aj_roster_one_cell", PHONE_393, asked { vm ->
        tapText("다른 곳이 있어요")
        settle()
        tapText("5일 주간", scrolls = 4)
        settle()
        assertTrue(appears("그대로 두기"))
    })

    // ---- 447 첫 설정 ----
    @Test fun setupAsk() = shoot("1146_aj_setup_same_ask", PHONE_393, setup { reveal("사진과 내용이 같은가요?") })
    // The month screen's keypad takes the lower half of a 320×568 phone, where the test's drag starts, so the small phone is
    // shown on the first setup's roster step, which has the same card.
    @Test fun setupAskSmall() = shoot("1141_aj_setup_same_small_phone", SMALL_PHONE, setup { reveal("네, 같아요") })

    private fun setup(then: Screen.() -> Unit): Screen.() -> Unit = {
        fourWays()
        val id = c.calendars.begin("병동")
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c, calendarId = id)
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(com.bamnat.core.schedule.ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
        settle()
        vm.readRosterPhoto(app, Uri.parse("content://test/roster.jpg"))
        settle()
        // Four readings take a moment off the main thread: the lines to pick from first.
        reveal("어느 줄(조)이 내 것인가요?")
        vm.useRosterRow(1)
        settle()
        then()
    }

    // ---- 447 명세서: 사진과 함께, 줄 고치기 ----
    private fun slip(then: Screen.() -> Unit): Screen.() -> Unit = {
        val photo = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
            SLIP.lines().forEachIndexed { i, l -> canvas.drawText(l, 30f, 60f + i * 44, paint) }
        }
        // Read three ways; one lost the 야간 line's last digit, so it is yellow until looked at.
        val scan = PayslipScanner.put(listOf(SLIP, SLIP.replace("154,000", "15,400"), SLIP.replace("154,000", "15,400").replace("지급합계", "지급")), photo, null)
        content { PayslipFillDialog("계산 방식 찾기", onDismiss = {}, initialScan = scan) {} }
        settle()
        then()
    }
    @Test fun slipAsk() = shoot("1147_aj_slip_same_ask", PHONE_393, slip { reveal("사진과 내용이 같은가요?") })
    @Test fun slipLine() = shoot("1148_aj_slip_line_fix", PHONE_393, slip {
        tapText("다른 곳이 있어요")
        settle()
        tapText("야간근로수당", scrolls = 4)
        settle()
        assertTrue(appears("이 줄 고치기"))
    })

    // ---- 448·449 급여 화면 ----
    private fun needsPay(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = { it.copy(estimatedFields = it.estimatedFields + RuleOnlyNeeds.HOURS_UNSET, verified = null) }).invoke(this)
        then()
    }
    @Test fun payNeeds() = shoot("1149_aj_pay_needs_card", PHONE_393, needsPay { reveal("만 넣으면 정확해져요") })
    @Test fun payMatchCard() = shoot("1150_aj_pay_match_card", PHONE_393, needsPay {
        reveal("명세서와 맞춰 보기")
        assertTrue(appears("금액 비교하기"))
        assertTrue(appears("계산 방식 찾기"))
    })
    @Test fun payHours() = shoot("1151_aj_pay_hours_dialog", PHONE_393, needsPay {
        reveal("월 소정근로시간(시수)")
        tapAction("월 소정근로시간(시수) 넣기")
        settle()
        assertTrue(appears("통상시급 = 기본급"))
    })
    @Test fun rulesOnly() = shoot("1152_aj_rules_only", PHONE_393, {
        payScreen(Tier.PRO, withProfile = true) { vm -> PayScreen(vm, onBack = {}) }.invoke(this)
        tapText("계산 방식 찾기", scrolls = 40)
        settle()
        tapText("글로 붙여 넣기", exact = true)
        typeInto("명세서 글", "야간근로는 통상시급의 60%를 가산하여 지급한다.")
        tapText("읽기", exact = true)
        settle()
        assertTrue(appears("명세서 없이 회사 계산 방법만으로 계산해요"))
        // [PHOTO-34] 1.30 AW: what the rules come to shows under them as they are, before anything is applied.
        waitFor(800)
        assertTrue(appears("월 규칙으로 계산"))
        tapText("규칙으로 계산하기", exact = true)
        settle()
        waitFor(800)
        assertTrue(!shows("규칙으로 계산하기", exact = true))
    })
}
