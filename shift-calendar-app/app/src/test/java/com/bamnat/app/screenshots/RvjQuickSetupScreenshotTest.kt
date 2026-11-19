package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.pay.PayQuickSetup
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.app.tier.Tier
import com.bamnat.core.time.Today
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 J 스무째 PR [RVJ-104] (검토 PC-05·T05-08·U05-04): 알바 급여 빠른 설정 — 5인 물음은 '모르겠어요'로 시작해 5인 미만으로 굳지 않고,
 * 주 15시간 넘게 일하는 달력이면 세금 방식이 '떼지 않음'이 아니라 4대보험으로 시작해요. 밝은 100%·어두운 200% 접은 폭과 편 폭.
 * The shots hold no assertion that the old setup would fail, so the same file draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjQuickSetupScreenshotTest : ScreenshotBase() {
    private fun hourly(unknownFive: Boolean) = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.HOURLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 10_320L))),
        fiveOrMoreEmployees = false,
        estimatedFields = if (unknownFive) setOf(Unknowns.FIVE_OR_MORE) else emptySet(),
    )

    private fun quick(profile: PayProfile, at: String): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = { profile }) { m ->
            val p = m.profile.collectAsState().value
            val snap = m.snapshot.collectAsState().value
            if (p != null && snap != null) PayQuickSetup(
                initial = p, snapshot = snap,
                preview = { m.preview(it, snap, Today.month()) },
                slipRules = { base, read, rules -> m.slipRules(base, snap, read, rules) },
                onDone = { _, _ -> }, onDetailed = {}, onCancel = {}, startAt = at,
            )
        }.invoke(this)
        repeat(2) { settle() }
    }

    // ── 시급 쪽: 5인 물음 ──
    private fun five(): Screen.() -> Unit = {
        quick(hourly(unknownFive = true), "amount").invoke(this)
        reveal("같이 일하는 사람이 사장님 빼고 5명 이상인가요?", exact = true)
    }

    @Test fun fiveStartsOnNotSure() = shoot("rvj_389_quick_five_unknown_folded", FOLDED) {
        five().invoke(this)
        assertTrue("모르겠어요 칩", shows("모르겠어요", exact = true))
        assertTrue("아니요 칩", shows("아니요", exact = true))
        assertTrue("모르면 범위로 보여 준다고 말해요", scrolledTo("모르면 가산 없이 시급대로 계산하고", exact = false))
        assertFalse("옛 '모르면 이것' 글은 없어요", shows("모르면 이것", exact = false))
    }
    @Test fun fiveDarkBig() = shoot("rvj_390_quick_five_unknown_folded_dark_200", DARK, 2f, five())
    @Test fun fiveWide() = shoot("rvj_391_quick_five_unknown_unfolded", UNFOLDED, five())

    // ── 마지막 쪽: 달력이 주 15시간 넘게 일해서 4대보험으로 시작 ──
    private fun tax(): Screen.() -> Unit = {
        quick(hourly(unknownFive = false), "confirm").invoke(this)
        reveal("급여에서 떼는 돈", exact = false)
    }

    @Test fun fifteenHoursStartOnTheInsurances() = shoot("rvj_392_quick_tax_over15_folded", FOLDED) {
        tax().invoke(this)
        assertTrue("4대보험으로 시작한다고 한 줄", scrolledTo("일하면 보통 4대보험을 떼요", exact = false))
        assertFalse("알바는 떼지 않는 곳이 많다는 글은 없어요", shows("알바는 떼지 않는 곳이 많아요", exact = false))
    }
    @Test fun taxDarkBig() = shoot("rvj_393_quick_tax_over15_folded_dark_200", DARK, 2f, tax())
    @Test fun taxWide() = shoot("rvj_394_quick_tax_over15_unfolded", UNFOLDED, tax())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
