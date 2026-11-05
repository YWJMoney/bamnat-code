package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.company.CompanyInlineResults
import com.bamnat.app.ui.company.PublicBrowse
import com.bamnat.app.ui.company.PublicCycleSection
import com.bamnat.app.ui.company.shiftsLine
import com.bamnat.core.plaza.PublicCycles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [CYC-09][CYC-10] 1.38 묶음 K, build 012815b6 PC 1차: the order typed as said ('당비비', '주야비휴') finds its rows under the
 * first setup's name box too, a picked 업종 chip folds the 25 chips so its rows come right under it, a closed search keeps
 * its chip, and a row with one shift's hours says which hours are filled in. Shots 2460~2465.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CycSearchScreenshotTest : ScreenshotBase() {
    private fun Screen.page(body: @androidx.compose.runtime.Composable () -> Unit) {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                body()
            }
        }
        settle()
    }

    @Test fun theOrderAsSaidFindsRowsUnderTheNameBox() = shoot("2460_cyc_inline_order_dnbo", PHONE) {
        page { CompanyInlineResults("주야비휴", onServer = null, onPick = { _, _ -> }) }
        assertTrue(appears("찾은 곳 · 주야비휴", exact = true))
        assertFalse(appears("여기 없어요, ‘주야비휴’ 직접 추가", exact = true, ms = 300))
    }

    @Test fun dangBiBiFindsTheFireRows() = shoot("2461_cyc_inline_order_xbo", PHONE) {
        page { CompanyInlineResults("당비비", onServer = null, onPick = { _, _ -> }) }
        assertTrue(appears("찾은 곳 · 당비비", exact = true))
        assertTrue(appears("소방", exact = false))
    }

    @Test fun aPickedGroupFoldsTheChips() = shoot("2462_cyc_group_folded", PHONE) {
        page { PublicCycleSection("", browse = true) }
        tapText("소방 ", prefix = true)
        settle()
        assertTrue(appears("다른 근무형태·업종 보기", exact = true))
        assertFalse(appears("업종으로 찾기", exact = true, ms = 300))
        assertEquals("fire", PublicBrowse.group)
        tapText("다른 근무형태·업종 보기", exact = true)
        settle()
        assertTrue(appears("업종으로 찾기", exact = true))
    }

    @Test fun theChipStaysWhenTheSearchOpensAgain() = shoot("2463_cyc_group_kept", PHONE) {
        PublicBrowse.group = "police"
        page { PublicCycleSection("", browse = true) }
        assertTrue(appears("다른 근무형태·업종 보기", exact = true))
        assertTrue(appears(com.bamnat.core.plaza.IndustryGroups.byKey("police")!!.note, exact = true))
    }

    @Test fun givenHoursComeFirstAndTheFilledOnesSaySo() {
        val c = PublicCycles.all.first { it.company == "포항남부경찰서 상대지구대" }
        assertEquals("야간 17~08 · 주간 08~17 (주간: 출처에 없어 채운 시간)", shiftsLine(c))
    }
}
