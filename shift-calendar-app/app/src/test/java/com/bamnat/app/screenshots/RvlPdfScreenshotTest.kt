package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.imports.PdfLock
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.PdfSlips
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipVote
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVL-41][RVL-44] 갈래 L 5차의 화면 (디자인 품질 조건): 명세서로 맞춰 보기에서 암호가 걸린 PDF의 암호 칸(아래에서 올라오는 시트, 칸에 바로
 * 글자가 들어가고 채운 단추 '열기' 하나), 틀린 암호, 여러 달 명세서가 든 PDF에서 볼 것 고르기(본 것에 ✓, 화면을 다시 열면 맨 위에). 밝은·어두운 ×
 * 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlPdfScreenshotTest : ScreenshotBase() {
    @After fun letGo() = PdfSlips.forget()

    private fun match(over: @androidx.compose.runtime.Composable () -> Unit = {}): Screen.() -> Unit = payScreen(Tier.PRO, withProfile = true) { vm ->
        val profile = vm.profile.collectAsState().value
        val snap = vm.snapshot.collectAsState().value
        if (profile != null && snap != null) {
            com.bamnat.app.ui.pay.PayslipMatchDialog(vm, profile, snap, Today.month(), onDismiss = {})
            over()
        }
    }

    private fun locked(lock: PdfLock): Screen.() -> Unit = {
        // The sheet comes over the match screen once it is up, as it does after a file is picked there.
        val asking = androidx.compose.runtime.mutableStateOf(false)
        match { if (asking.value) com.bamnat.app.ui.imports.PdfPasswordDialog(lock, working = false, payslip = true, onOpen = {}, onDismiss = {}) }()
        asking.value = true
        settle()
        assertTrue(appears("암호가 걸린 PDF예요", exact = true))
        assertTrue(appears("생년월일 6자리"))
        if (lock == PdfLock.WRONG) assertTrue(appears("암호가 맞지 않아요. 다시 넣어 주세요", exact = true))
    }

    @Test fun lockedFoldLight() = shoot("rvl41_locked_fold_light_100", FOLDED, locked(PdfLock.ASK))
    @Test fun lockedFoldDarkHuge() = shoot("rvl41_locked_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, locked(PdfLock.ASK))
    @Test fun lockedOpenLight() = shoot("rvl41_locked_open_light_100", UNFOLDED, locked(PdfLock.ASK))
    @Test fun wrongFoldLight() = shoot("rvl41_wrong_fold_light_100", FOLDED, locked(PdfLock.WRONG))
    @Test fun wrongFoldDarkHuge() = shoot("rvl41_wrong_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, locked(PdfLock.WRONG))

    /** Three months of one person's payslips, as a PDF of a year's would be read. */
    private fun months(): PayslipScanner.PdfRead.Slips {
        val year = Today.month().year
        fun scan(month: Int, gross: Long, net: Long) = PayslipScanner.Scan(
            "기본급 ${gross - 200_000}\n식대 200,000\n지급합계 $gross\n실수령액 $net", emptyList(),
            SlipVote.Result(PayslipRead(listOf(
                SlipLine("기본급", SlipRole.BASE, gross - 200_000), SlipLine("식대", SlipRole.ALLOWANCE, 200_000),
                SlipLine("지급합계", SlipRole.GROSS, gross), SlipLine("실수령액", SlipRole.NET, net),
            ), YearMonth.of(year, month)), emptySet(), ""),
            emptyList(), null, null,
        )
        return PayslipScanner.PdfRead.Slips(listOf(scan(8, 2_850_000, 2_512_340), scan(9, 3_120_000, 2_731_880), scan(10, 2_910_000, 2_560_210)), null, null, listOf(0, 1, 2))
    }

    /** The payslip screen opened again after 8월 was matched: the file's other two wait at the top. */
    private fun next(): Screen.() -> Unit = {
        val held = PdfSlips.put(months(), locked = false)
        PdfSlips.looked(held.id, 0)
        match()()
        assertTrue(appears("방금 고른 PDF에 아직 안 본 명세서가 2장 있어요", exact = true))
        assertTrue(appears("9월 명세서", exact = true))
        assertTrue(appears("실수령 2,731,880원", exact = true))
    }

    @Test fun nextFoldLight() = shoot("rvl44_next_fold_light_100", FOLDED, next())
    @Test fun nextFoldDarkHuge() = shoot("rvl44_next_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, next())
    @Test fun nextOpenLight() = shoot("rvl44_next_open_light_100", UNFOLDED, next())

    /** Picked: that payslip gets its ✓, the month moves to it, and it fills the amounts once the roster question is answered. */
    @Test fun pickedGoesOn() = shoot("rvl44_picked_fold_light_100", FOLDED) {
        next()()
        tapText("9월 명세서", exact = true)
        settle()
        assertEquals(setOf(0, 1), PdfSlips.get()?.seen)
        assertTrue(appears("9월 명세서 골랐어요 · 실수령 2,731,880원. 아래 물음에 답하면 바로 채워요.", exact = true))
    }

    /** As the file is read: every payslip in it, none looked at yet. */
    private fun first(): Screen.() -> Unit = {
        content {
            androidx.compose.material3.Surface {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                    com.bamnat.app.ui.pay.PdfSlipsChooser(PdfSlips.put(months(), locked = false), again = false, enabled = true, onPick = {}, onForget = {})
                }
            }
        }
        settle()
        assertTrue(appears("이 PDF에 명세서가 3장 들어 있어요. 볼 것을 골라 주세요", exact = true))
    }

    @Test fun firstFoldLight() = shoot("rvl44_first_fold_light_100", FOLDED, first())
    @Test fun firstFoldDarkHuge() = shoot("rvl44_first_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, first())

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}

