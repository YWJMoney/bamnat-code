package com.bamnat.app.screenshots

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
import com.bamnat.app.ui.pay.SlipCheck
import com.bamnat.core.pay.NameCheck
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVL-46][RVL-47][RVL-48] 갈래 L 6차의 화면 (디자인 품질 조건): 명세서 줄의 '이름 확인'과 '금액 확인'을 나눠 말하고, 이름은 그 물음으로
 * (야간수당인가요? [네][아니요] · ‘식비’가 맞나요? [네][사진대로] · 이름이 맞나요? [맞아요][고치기]) 금액 다음에 하나씩, 또는
 * '네, 같아요' 한 번으로 답해요. 같아 보이는 상여는
 * 꺼진 채 한 줄로 말하고, '매년' 없는 인상은 한 번만으로 꺼진 채 보여요. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlNameScreenshotTest : ScreenshotBase() {
    /**
     * 지급: 기본급, 야근수당 (야간수당 a stroke away), 식비 (사진에는 숙비), 자격수당 read two ways; 공제: 소득세, 볼링회 (a name no list
     * holds, under 공제). The sums meet.
     */
    private val READ = PayslipRead(listOf(
        SlipLine("기본급", SlipRole.BASE, 2_500_000),
        SlipLine("야근수당", SlipRole.ALLOWANCE, 150_000, check = NameCheck("야근수당", "야간수당", SlipRole.NIGHT)),
        SlipLine("식비", SlipRole.ALLOWANCE, 200_000, check = NameCheck("숙비")),
        SlipLine("자격수당", SlipRole.ALLOWANCE, 57_500),
        SlipLine("지급합계", SlipRole.GROSS, 2_907_500),
        SlipLine("소득세", SlipRole.DEDUCTION, 58_880),
        SlipLine("볼링회", SlipRole.DEDUCTION, 10_000, check = NameCheck("볼링회")),
        SlipLine("공제합계", SlipRole.DEDUCTION_TOTAL, 68_880),
    ))

    private var last: PayslipRead? = null

    private fun check(reviewing: Boolean, amounts: Set<Int>, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            var read by remember { mutableStateOf(READ) }
            var unsure by remember { mutableStateOf(amounts) }
            var same by remember { mutableStateOf<Boolean?>(null) }
            androidx.compose.material3.Surface { Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SlipCheck(read, unsure, same, fromPhoto = true, onAnswer = { same = it }, onChange = { r, u -> read = r; unsure = u; last = r },
                    reviewingAtFirst = reviewing)
            } }
        }
        settle()
        then()
    }

    /** An amount read two ways and three names: the amount is asked first, as before; each line says what it is for. */
    private fun apart(): Screen.() -> Unit = check(false, setOf(3)) {
        // Top to bottom, as a person reads it: at 200% the card sits below the lines.
        reveal("이름 확인 · 야간수당일 수도 있어요")
        reveal("이름 확인 · 사진에는 ‘숙비’")
        reveal("금액 확인", exact = true)
        reveal("이름 확인 · 짐작한 이름")
        reveal("금액 1줄만 사진과 맞는지 봐 주세요")
        reveal("이름은 그다음에 봐요")
        reveal("1줄 확인하기", exact = true)
    }

    @Test fun apartFoldLight() = shoot("rvl46_apart_fold_light_100", FOLDED, apart())
    @Test fun apartFoldDarkHuge() = shoot("rvl46_apart_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, apart())
    @Test fun apartOpenLight() = shoot("rvl46_apart_open_light_100", UNFOLDED, apart())

    /** The sums vouch for every amount: only the names, one tap for all or each on its own. */
    private fun names(): Screen.() -> Unit = check(false, emptySet()) {
        reveal("이름 3개만 사진과 맞는지 봐 주세요")
        reveal("금액은 명세서 합계와 맞아요")
        reveal("네, 같아요", exact = true)
        reveal("하나씩 보기", exact = true)
    }

    @Test fun namesFoldLight() = shoot("rvl46_names_fold_light_100", FOLDED, names())
    @Test fun namesFoldDarkHuge() = shoot("rvl46_names_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, names())
    @Test fun namesOpenLight() = shoot("rvl46_names_open_light_100", UNFOLDED, names())

    private fun asking(backUp: Boolean = false): Screen.() -> Unit = check(true, emptySet()) {
        reveal("야간수당인가요?")
        reveal("‘식비’가 맞나요?")
        reveal("사진대로", exact = true)
        reveal("이름이 맞나요?")
        reveal("나머지도 맞아요", exact = true)
        // At 200% the card is a screen below: the shot goes back up to the questions.
        if (backUp) { repeat(6) { scrollDown(500) }; settle(); assertTrue(appears("야간수당인가요?")) }
    }

    @Test fun askingFoldLight() = shoot("rvl46_asking_fold_light_100", FOLDED, asking())
    @Test fun askingFoldDarkHuge() = shoot("rvl46_asking_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, asking(backUp = true))
    @Test fun askingOpenLight() = shoot("rvl46_asking_open_light_100", UNFOLDED, asking())

    /** The amount, then each name, one tap each: the photo is looked at and the lines are as the user said. */
    private fun answered(): Screen.() -> Unit = check(true, setOf(3)) {
        assertTrue("names wait for the amounts", gone("야간수당인가요?"))
        tapText("맞아요", exact = true) // 자격수당's amount
        settle()
        assertTrue(appears("이름 3개만 사진과 맞는지 봐 주세요"))
        tapText("하나씩 보기", exact = true)
        settle()
        tapText("네", exact = true) // 야간수당인가요? → 네
        settle()
        tapText("사진대로", exact = true) // ‘식비’가 맞나요? → 사진대로 ‘숙비’
        settle()
        tapText("맞아요", exact = true) // 볼링회's name
        settle()
        assertTrue(appears("사진과 같다고 확인했어요", exact = false))
        val r = last!!
        assertEquals(SlipLine("야근수당", SlipRole.NIGHT, 150_000), r.lines[1])
        assertEquals(SlipLine("숙비", SlipRole.ALLOWANCE, 200_000), r.lines[2])
        assertTrue(r.names.isEmpty())
        // The kind it was taken as shows under the payslip's own name.
        assertTrue(appears("야간"))
    }

    @Test fun answeredFoldLight() = shoot("rvl46_answered_fold_light_100", FOLDED, answered())

    /** 1.39's one tap stays one tap: every name as shown. */
    @Test fun oneTapForAllNames() = shoot("rvl46_one_tap_fold_light_100", FOLDED, check(false, emptySet()) {
        tapText("네, 같아요", exact = true)
        settle()
        assertTrue(appears("사진과 같다고 확인했어요", exact = false))
        val r = last!!
        assertTrue(r.names.isEmpty())
        assertEquals(READ.lines.map { it.copy(check = null) }, r.lines)
    })

    // ---- RVL-47: a 상여 like one already set, under another name ----
    private fun twinBonus(): Screen.() -> Unit = {
        val set = listOf(com.bamnat.core.pay.PayItem("bonus-1", "상여금",
            com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(java.time.LocalDate.of(2026, 1, 1), 0))),
            com.bamnat.core.pay.Cadence.BONUS, months = setOf(2, 4, 6, 8, 10, 12), percentOfBase = 4.0 / 6))
        content {
            val state = com.bamnat.app.ui.pay.rulesheet.rememberRuleSheetState {
                com.bamnat.core.pay.RuleSheet.read(com.bamnat.core.pay.RuleText.read("정기상여 연 400% 짝수달"), from = com.bamnat.core.pay.CellFrom.TEXT, bonuses = set)
            }
            val host = remember {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetHost(com.bamnat.app.ui.pay.PhotoCellsLogic.blank,
                    com.bamnat.app.data.ScheduleSnapshot(emptyList(), emptyList(), emptyList()), java.time.YearMonth.of(2026, 11))
            }
            androidx.compose.material3.Surface { Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetCells(state, host)
            } }
        }
        settle()
        assertTrue(appears("이미 있는 ‘상여금’과 같아 보여요. 다른 상여일 때만 켜 주세요"))
    }

    @Test fun twinBonusFoldLight() = shoot("rvl47_twin_bonus_fold_light_100", FOLDED, twinBonus())
    @Test fun twinBonusFoldDarkHuge() = shoot("rvl47_twin_bonus_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, twinBonus())

    // ---- RVL-48: a raise with no word for every year is once, and starts off ----
    private fun raiseOnce(): Screen.() -> Unit = {
        content {
            val state = com.bamnat.app.ui.pay.rulesheet.rememberRuleSheetState {
                com.bamnat.core.pay.RuleSheet.read(com.bamnat.core.pay.RuleText.read("2027년 3월 기본급 3.5% 인상"), from = com.bamnat.core.pay.CellFrom.TEXT)
            }
            val host = remember {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetHost(com.bamnat.app.ui.pay.PhotoCellsLogic.blank,
                    com.bamnat.app.data.ScheduleSnapshot(emptyList(), emptyList(), emptyList()), java.time.YearMonth.of(2026, 11))
            }
            androidx.compose.material3.Surface { Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                com.bamnat.app.ui.pay.rulesheet.RuleSheetCells(state, host)
            } }
        }
        settle()
        assertTrue(appears("3월 한 번만 기본급 3.5% 인상", exact = false))
    }

    @Test fun raiseOnceFoldLight() = shoot("rvl48_raise_once_fold_light_100", FOLDED, raiseOnce())
    @Test fun raiseOnceFoldDarkHuge() = shoot("rvl48_raise_once_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, raiseOnce())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
    }
}
