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
import com.bamnat.app.pay.PayStore
import com.bamnat.app.ui.pay.PayHistoryDialog
import com.bamnat.app.ui.pay.PinnedPhotos
import com.bamnat.app.ui.pay.RestoreBand
import com.bamnat.app.ui.pay.SlipCheck
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PAYIN-01][PAYIN-02][PAYIN-06] 1.37 묶음 B's screenshots, numbers 2370~2379: the payslip's lines 지급 left and 공제 right with
 * 실수령 worked out, a quick name tapped to type only its amount, the photo pinned over empty lines typed by hand, the 7 days of
 * changes, and the band to bring an emptied setup back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PayInScreenshotTest : ScreenshotBase() {
    private val READ = PayslipRead(listOf(
        SlipLine("기본급", SlipRole.BASE, 2_397_500), SlipLine("식대", SlipRole.ALLOWANCE, 200_000),
        SlipLine("야간근무수당", SlipRole.NIGHT, 691_778, 64 * 60), SlipLine("지급계", SlipRole.GROSS, 3_289_278),
        SlipLine("소득세", SlipRole.DEDUCTION, 173_130), SlipLine("건강보험", SlipRole.DEDUCTION, 280_730),
        SlipLine("공제계", SlipRole.DEDUCTION_TOTAL, 453_860), SlipLine("실수령액", SlipRole.NET, 2_835_418),
    ))

    private fun split(start: PayslipRead, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            var read by remember { mutableStateOf(start) }
            var unsure by remember { mutableStateOf(emptySet<Int>()) }
            var same by remember { mutableStateOf<Boolean?>(false) }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SlipCheck(read, unsure, same, fromPhoto = false, onAnswer = { same = it }, onChange = { r, u -> read = r; unsure = u })
            }
        }
        settle()
        then()
    }

    @Test fun twoSides() = shoot("2370_payin_two_sides", FOLDED, split(READ) {
        reveal("지급", exact = true)
        reveal("공제", exact = true)
        // 3,289,278 − 453,860 = 2,835,418, the payslip's own 실수령.
        reveal("명세서의 실수령과 맞아요", exact = false)
    })

    @Test fun quickName() = shoot("2371_payin_quick_name", FOLDED, split(PayslipRead(emptyList())) {
        reveal("아직 없어요", exact = false)
        tapText("+ 국민연금", exact = true)
        settle()
        reveal("‘국민연금’ 금액 넣기", exact = false)
        typeInto("금액", "141750")
        settle()
        tapText("더하기", exact = true)
        settle()
        reveal("− 141,750원", exact = false)
        assertTrue("넣은 이름은 빠른 이름에서 빠져요", gone("+ 국민연금", exact = true))
    })

    @Test fun narrowStacks() = shoot("2372_payin_narrow", SMALL_PHONE, split(READ) {
        reveal("공제 합계", exact = true)
    })

    @Test fun pinnedPhoto() = shoot("2373_payin_pinned_photo", PHONE, {
        val photo = payslip()
        content {
            Column {
                PinnedPhotos(listOf(photo))
                Column(Modifier.padding(16.dp)) {
                    SlipCheck(PayslipRead(emptyList()), emptySet(), false, fromPhoto = false, onAnswer = {}, onChange = { _, _ -> })
                }
            }
        }
        settle()
        reveal("사진 크게", exact = true)
        tapText("접기", exact = true)
        settle()
        reveal("사진 펴기", exact = true)
    })

    private fun profile(base: Long) = PayProfile(Occupation.CUSTOM, WageType.MONTHLY,
        ValueTimeline(listOf(DatedValue(Today.month().atDay(1).minusYears(1), base))))

    @Test fun history() = shoot("2374_payin_history", FOLDED, {
        val now = Today.millis()
        val changes = listOf(
            PayStore.Change(now - 60_000, "명세서에 맞춰 바꿨어요", profile(2_400_000)),
            PayStore.Change(now - 3 * 3_600_000, "급여 설정을 만들었어요", null),
        )
        content { PayHistoryDialog(changes, profile(2_508_000), onRestore = {}, onDismiss = {}) }
        settle()
        reveal("고친 기록 (7일)", exact = true)
        reveal("명세서에 맞춰 바꿨어요", exact = true)
        tapText("전으로", exact = true)
        settle()
        reveal("지금 설정은 기록에 남아서", exact = false)
    })

    @Test fun restoreBand() = shoot("2375_payin_restore_band", FOLDED, {
        val c = PayStore.Change(Today.millis() - 2 * 86_400_000L, PayStore.RESTART, profile(2_508_000))
        content { Column(Modifier.padding(16.dp)) { RestoreBand(c, onRestore = {}) } }
        settle()
        reveal("지운 급여 설정이 있어요 (5일 동안 보관)", exact = false)
        reveal("2,508,000원", exact = false)
    })

    /** A payslip-like picture: a white page with grey rows. */
    private fun payslip(): Bitmap = Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.GRAY; textSize = 28f }
        repeat(20) { i -> c.drawText("기본급 2,397,500   국민연금 141,750", 20f, 60f + i * 56, p) }
    }
}
