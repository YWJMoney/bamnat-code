package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.components.DraftStore
import com.bamnat.app.ui.pay.AddBonusDialog
import com.bamnat.app.ui.pay.OtherLine
import com.bamnat.app.ui.pay.PayScaleDialog
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.app.ui.pay.PayslipFillDialog
import com.bamnat.app.ui.pay.PayslipMatchDialog
import com.bamnat.app.ui.pay.PayslipWorks
import com.bamnat.app.ui.pay.RuleTarget
import com.bamnat.app.ui.pay.SlipDraft
import com.bamnat.app.ui.pay.SlipForm
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * 2.0.0 갈래 J 일곱 번째 (믿음 줄, 조건 10 '적던 것은 사라지지 않아요'): [RVJ-62] 읽던 명세서 사진이 '다시 하기'·'직접 문의하기'·화면이 다시
 * 만들어질 때 사라지지 않음, [RVJ-63] 급여 창들이 닫기·뒤로·밖·취소 한 번에 적던 것을 버리지 않고 한 번 물음, [RVJ-64] 명세서로 맞춰 보기의
 * 넣던 숫자를 이 휴대폰에 임시 저장하고 '넣던 명세서 이어서'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjTrustTest : ScreenshotBase() {
    private val ask = "적은 내용을 버릴까요?"

    @Before fun clean() {
        PayslipWorks.clearForTest()
        DraftStore.clear(app, SlipDraft.KEY)
    }

    @After fun after() {
        PayslipWorks.clearForTest()
        DraftStore.clear(app, SlipDraft.KEY)
    }

    // ── [RVJ-64] 맞춰 보기의 넣던 숫자 ──

    @Test fun theSlipDraftComesBackWhole() {
        val form = SlipForm().apply {
            gross = 3_200_000; base = 2_500_000
            overtime.amount = 300_000; overtime.stepped = 750
            items["meal"] = 200_000
            others += OtherLine().also { it.name = "자격\t수당"; it.amount = 50_000 }
            others += OtherLine()  // an empty line added and left: not kept
        }
        val month = YearMonth.of(2026, 10)
        val raw = SlipDraft.encode(form, month)
        val back = SlipForm()
        assertEquals(month, SlipDraft.decode(raw, back, setOf("meal")))
        assertEquals(3_200_000L, back.gross)
        assertEquals(2_500_000L, back.base)
        assertEquals(300_000L, back.overtime.amount)
        assertEquals(750, back.overtime.stepped)
        assertNull(back.night.amount)
        assertEquals(200_000L, back.items["meal"])
        assertEquals(listOf("자격 수당" to 50_000L), back.others.map { it.name to it.amount })
        // An allowance taken out of the setup since is left out; the rest still comes back.
        val gone = SlipForm()
        assertEquals(month, SlipDraft.decode(raw, gone, emptySet()))
        assertTrue(gone.items.isEmpty())
        // Nothing typed, nothing kept; what is not a draft brings nothing back.
        assertEquals("", SlipDraft.encode(SlipForm(), month))
        assertNull(SlipDraft.decode("", SlipForm(), emptySet()))
        assertNull(SlipDraft.decode("다른 글", SlipForm(), emptySet()))
    }

    private var vm: PayViewModel? = null

    @Composable private fun Match(model: PayViewModel) {
        val profile = model.profile.collectAsState().value
        val snap = model.snapshot.collectAsState().value
        if (profile != null && snap != null) PayslipMatchDialog(model, profile, snap, Today.month(), onDismiss = {})
    }

    /** 맞춰 보기 opened, 그 달 근무가 같았다고 하고, 지급 총액 3,200,000원을 넣은 데까지. */
    private fun typedMatch(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Match(m) }.invoke(this)
        tapText("네, 같았어요", exact = true)
        settle()
        typeInto("지급 총액", "3200000")
        repeat(2) { settle() }
    }

    /** The window goes the way a screen made again or Android closing the app takes it (no 닫기), and opens again. */
    private fun goneAndBack(): Screen.() -> Unit = {
        content {}
        settle()
        val m = vm!!
        content { Match(m) }
        repeat(2) { settle() }
    }

    @Test fun theMatchKeepsWhatWasTyped() = shoot("rvj_170_match_kept_folded", FOLDED) {
        typedMatch().invoke(this)
        goneAndBack().invoke(this)
        assertTrue(DraftStore.read(app, SlipDraft.KEY).contains("g\t3200000"))
        assertTrue(appears("넣던 ${Today.month().monthValue}월 명세서를 이어서 적어요"))
        assertTrue(shows("3200000") || shows("3,200,000"))
    }

    @Test fun theMatchKeptDarkBig() = shoot("rvj_171_match_kept_folded_dark_200", DARK, 2f) {
        typedMatch().invoke(this)
        goneAndBack().invoke(this)
    }

    @Test fun startingOverEmptiesIt() = shoot("rvj_172_match_fresh_folded", FOLDED) {
        typedMatch().invoke(this)
        goneAndBack().invoke(this)
        tapText("처음부터", exact = true)
        repeat(2) { settle() }
        assertEquals("", DraftStore.read(app, SlipDraft.KEY))
        assertTrue(appears("어느 달 명세서인가요?"))
        assertFalse(shows("이어서 적어요"))
    }

    // ── [RVJ-63] 닫기 전에 한 번 묻기 ──

    @Test fun closingTheMatchAsks() = shoot("rvj_173_match_close_asks_folded", FOLDED) {
        typedMatch().invoke(this)
        tapText("이전", exact = true)
        settle()
        tapText("닫기", exact = true)
        settle()
        assertTrue(appears(ask))
        tapText("버리기", exact = true)
        repeat(2) { settle() }
        // Thrown away: nothing comes back next time.
        assertEquals("", DraftStore.read(app, SlipDraft.KEY))
    }

    @Test fun nothingTypedClosesAtOnce() = shoot("rvj_174_match_close_quiet_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true) { m -> Match(m) }.invoke(this)
        tapText("닫기", exact = true)
        settle()
        assertFalse(shows(ask))
    }

    @Test fun closingAnAllowanceAsks() = shoot("rvj_175_item_close_asks_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        tapText("내 급여 값", exact = true, scrolls = 0)
        settle()
        tapText("수당 추가", exact = true, scrolls = 14)
        typeInto("한 달 금액", "200000")
        repeat(2) { settle() }
        // A kind picked in the window first puts '종류 다시 고르기' where ✕ is; it keeps what was typed and ✕ is one step back.
        if (!shows("닫기", exact = true)) { tapText("종류 다시 고르기", exact = true); settle() }
        tapText("닫기", exact = true)
        settle()
        assertTrue(appears(ask))
        tapText("계속 적기", exact = true)
        settle()
        assertFalse(shows(ask))
    }

    @Test fun theBonusAsksBeforeItGoes() = shoot("rvj_176_bonus_close_asks_folded", FOLDED) {
        content { AddBonusDialog(onDismiss = {}, onAdd = { _, _, _, _, _, _, _ -> }) }
        settle()
        tapText("반기 (6·12월)", exact = true)
        settle()
        tapText("취소", exact = true)
        settle()
        assertTrue(appears(ask))
    }

    @Test fun thePayScaleAsksBeforeItGoes() = shoot("rvj_177_scale_close_asks_folded_dark_200", DARK, 2f) {
        payScreen(Tier.PRO, withProfile = true) { m ->
            val profile = m.profile.collectAsState().value
            if (profile != null) PayScaleDialog(m, profile, onDismiss = {})
        }.invoke(this)
        typeInto("1호봉 기본급", "2400000")
        repeat(2) { settle() }
        tapText("취소", exact = true)
        settle()
        assertTrue(appears(ask))
    }

    // ── [RVJ-62] 읽던 명세서 사진 ──

    private val key = "test.slip"

    private fun words(): List<OcrLine> = buildList {
        fun cell(t: String, x: Int, y: Int) { val w = t.length * 22; add(OcrLine(t, x - w / 2, y - 14, x + w / 2, y + 14)) }
        listOf("기본급" to "2,500,000", "식대" to "200,000", "연장근로수당" to "312,000", "지급계" to "3,012,000").forEachIndexed { i, (n, a) ->
            cell(n, 250, 200 + i * 60); cell(a, 700, 200 + i * 60)
        }
    }

    @Composable private fun Fill(scan: PayslipScanner.Scan?) {
        PayslipFillDialog("명세서 사진으로 채우기", onDismiss = {}, initialScan = scan, keep = key,
            rules = RuleTarget(emptyList(), emptyList(), true) { _, _ -> }) {}
    }

    private fun read(): Screen.() -> Unit = {
        val boxes = words()
        val photo = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
            boxes.forEach { canvas.drawText(it.text, it.left / 3f, it.bottom / 3f, paint) }
        }
        val s = PayslipScanner.put(listOf(OcrRows.join(boxes)), photo, null, listOf(boxes))
        content { Fill(s) }
        repeat(2) { settle() }
    }

    @Test fun retakeAsks() = shoot("rvj_178_photo_retake_asks_folded", FOLDED) {
        read().invoke(this)
        tapText("다시 하기", exact = true)
        settle()
        assertTrue(appears("사진을 다시 고를까요?"))
        assertTrue(shows("다시 고르기"))
    }

    @Test fun theReadPayslipWaitsForItsPlace() = shoot("rvj_179_photo_kept_folded", FOLDED) {
        read().invoke(this)
        val kept = PayslipWorks.waiting(key)
        assertNotNull(kept)
        // The window goes without 닫기 ('직접 문의하기', the screen made again): opened again from the same place, it is where it was.
        content {}
        settle()
        assertNotNull(PayslipWorks.waiting(key))
        content { Fill(null) }
        repeat(2) { settle() }
        assertTrue(appears("기본급", exact = true))
        assertTrue(shows("다시 하기", exact = true))
    }

    @Test fun throwingItAwayEmptiesThePlace() = shoot("rvj_180_photo_dropped_folded", FOLDED) {
        read().invoke(this)
        tapText("닫기", exact = true)
        settle()
        tapText("버리기", exact = true)
        repeat(2) { settle() }
        assertNull(PayslipWorks.waiting(key))
    }

    // ── [RVJ-65] 지운 숫자가 남던 칸 ──

    @Test fun emptyingTheTypedHourlyTakesItBack() = shoot("rvj_181_hourly_typed_emptied_folded", FOLDED) {
        var terms = com.bamnat.core.pay.CommonTerms()
        content {
            var t by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.bamnat.core.pay.CommonTerms()) }
            terms = t
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.bamnat.app.ui.pay.rulesheet.HourlyCard(null, Today.month(), t, { t = it })
            }
        }
        settle()
        tapText("통상시급을 모르겠어요", exact = true)
        settle()
        typeInto("통상시급을 알면 직접 (선택)", "21000")
        settle()
        assertEquals(21_000.0, terms.hourly ?: 0.0, 0.1)
        typeInto("통상시급을 알면 직접 (선택)", "")
        settle()
        assertNull(terms.hourly)
    }

    @Test fun anEmptiedAmountStaysEmptyWhileTyping() = shoot("rvj_182_amount_emptied_folded", FOLDED) {
        var kept = 0L
        content {
            var v by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(50_000) }
            kept = v ?: 0
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                // As a 규칙 칸's 1회 금액: the screen keeps the old amount until a new one comes.
                com.bamnat.app.ui.pay.MoneyField("1회 금액", v, { n -> n?.takeIf { it > 0 }?.let { v = it } })
            }
        }
        settle()
        tapText("1회 금액", exact = true)
        settle()
        typeInto("1회 금액", "")
        settle()
        assertFalse(shows("50000"))
        assertEquals(50_000L, kept)
        typeInto("1회 금액", "70000")
        settle()
        assertEquals(70_000L, kept)
        assertTrue(shows("70000"))
    }

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
