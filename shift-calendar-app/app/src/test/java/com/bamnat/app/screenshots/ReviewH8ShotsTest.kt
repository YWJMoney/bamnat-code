package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.FieldRules
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVH-69] 검토 PC-135 (통합 3차 PC: 현장 공수 창에서 일당을 적을 때 자판이 '저장'을 가려요): the 창 sits above the keyboard, its
 * fields scroll, and the keyboard's 완료 on 업체 saves. Four looks of the 창 (접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%,
 * 편 폭 어두운 100%); the keyboard itself is the PC's to see.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH8ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun dayRate(look: Int) {
        val (l, q, scale) = looks[look]
        shoot("rvh8_47_day_rate_$l", q, scale) {
            content {
                com.bamnat.app.ui.calendar.DayRateDialog(today, 1.0, FieldRules(dayRate = 230_000, lastClient = "OO건설"), null,
                    onDismiss = {}, onSave = { _, _ -> })
            }
            typeInto("일당 (1공)", "250000")
        }
    }
    @Test fun dayRateA() = dayRate(0)
    @Test fun dayRateB() = dayRate(1)
    @Test fun dayRateC() = dayRate(2)
    @Test fun dayRateD() = dayRate(3)

    /** 완료 on the last field saves what 저장 would: 1공 × 250,000원 for 한빛건설. */
    @Test fun theKeyboardsDoneSaves() {
        var saved: com.bamnat.core.money.Job? = null
        shoot("rvh8_47_day_rate_done", FOLDED) {
            content {
                com.bamnat.app.ui.calendar.DayRateDialog(today, 1.0, FieldRules(dayRate = 230_000, lastClient = "OO건설"), null,
                    onDismiss = {}, onSave = { job, _ -> saved = job })
            }
            typeInto("일당 (1공)", "250000")
            typeInto("업체", "한빛건설")
            imeAction("업체")
        }
        assertEquals("한빛건설", saved?.client)
        assertEquals(250_000L, saved?.amount)
    }
}
