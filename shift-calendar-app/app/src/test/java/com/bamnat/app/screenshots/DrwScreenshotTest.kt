package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.PutOnNoticeHost
import com.bamnat.app.ui.recipe.ThemeDrawer
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DRW-01][DRW-02][DRW-03] 1.37 마을 › 테마 = 내 서랍, numbers 1490~1494: the grid with its 종류·묶음·찾기, one thing's sheet, 바로 입기 with
 * its 넣었어요 · 되돌리기 line, and big text on a small phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DrwScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val themes get() = BuiltInThemes.of(WorkStyle.SHIFT)

    /** Two themes worn before (the second still on), one of mine, a part kept, and a 묶음. */
    private fun fill() = runBlocking {
        val (a, b) = themes.take(2)
        c.wardrobe.wear(a, "builtin:${a.title}", own = false)
        c.wardrobe.wear(b, "builtin:${b.title}", own = false)
        c.wardrobe.saveLook(a.copy(title = "겨울 밤 내 테마"))
        c.wardrobe.keep(b, "", LookParts.of(b).take(1).map { it.id })
        c.wardrobe.setFolder("theme:own:겨울 밤 내 테마", "겨울")
        c.wardrobe.setFolder("theme:builtin:${a.title}", "겨울")
    }

    private fun Screen.drawer() {
        content {
            val snack = remember { SnackbarHostState() }
            PutOnNoticeHost(c, snack)
            Scaffold(snackbarHost = { SnackbarHost(snack) }) { p ->
                Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeDrawer(WorkStyle.SHIFT, onFind = {}, onFix = {}, onStudio = {}, onRecipe = {}, onUpload = {})
                }
            }
        }
        settle()
    }

    @Test fun theDrawerHoldsEverythingOfMine() = shoot("1490_drw_drawer", PHONE_393) {
        fill()
        drawer()
        assertTrue(appears("내 서랍", exact = true))
        assertTrue(appears("지금 입은 테마"))
        assertTrue(appears(themes[1].title))
        assertTrue(appears("전체 4", exact = true))
        assertTrue(appears("내가 만든 것 1", exact = true))
        assertTrue(appears("입었던 것 2", exact = true))
        assertTrue(appears("입는 중 · 남의 테마"))
        assertTrue(appears("담아 둔 조각 1", exact = true))
        assertTrue(appears("📁 겨울", exact = true))
    }

    @Test fun aFolderAndASearchNarrowIt() = shoot("1491_drw_filter", PHONE_393) {
        fill()
        drawer()
        tapText("📁 겨울", exact = true)
        assertTrue(appears("‘겨울’ 묶음 이름 바꾸기"))
        assertTrue(appears("🎨 겨울 밤 내 테마"))
        assertTrue(gone("조각 · 내 것"))
        tapText("모든 묶음", exact = true)
        typeInto("서랍에서 찾기", "조각")
        assertTrue(appears("조각 · 내 것"))
        assertFalse(shows("🎨 겨울 밤 내 테마"))
    }

    /** 스튜디오 9차 (조각 카드를 눌러도 반응 없음): a kept part's card itself opens what can be done with it. */
    @Test fun aPartsCardOpensItsSheet() = shoot("1496_drw_part", PHONE_393) {
        fill()
        drawer()
        tapText("조각 · 내 것")
        assertTrue(appears("스튜디오에서 꾸미기", exact = true))
        assertTrue(appears("서랍에서 빼기", exact = true))
    }

    @Test fun oneThingsSheet() = shoot("1492_drw_sheet", PHONE_393) {
        fill()
        drawer()
        tapText("🎨 겨울 밤 내 테마")
        assertTrue(appears("바로 입기", exact = true))
        assertTrue(appears("스튜디오로 고치기", exact = true))
        assertTrue(appears("묶음 바꾸기", exact = true))
        assertTrue(appears("지우기", exact = true))
    }

    @Test fun wornAtOnceWithItsUndoLine() = shoot("1493_drw_worn_undo", PHONE_393) {
        fill()
        drawer()
        tapText("🎨 겨울 밤 내 테마")
        tapText("바로 입기", exact = true)
        assertTrue(appears("입었어요. 기록은 그대로예요"))
        assertEquals("겨울 밤 내 테마", c.wardrobe.lease.value.worn?.title)
        tapText("되돌리기", exact = true)
        assertTrue(appears("되돌렸어요", exact = true))
        assertEquals(themes[1].title, c.wardrobe.lease.value.worn?.title)
    }

    @Test fun bigTextOnASmallPhone() = shoot("1494_drw_huge_text", SMALL_PHONE, fontScale = 2f) {
        fill()
        drawer()
        assertTrue(appears("내 서랍", exact = true))
    }

    /** PC 확인 e34fae4e (입은 뒤 되돌리기를 눌러도 그대로): the whole app, 마을 › 테마, a starter worn from 입어 보기, then 되돌리기. */
    @Test fun aStarterWornThenUndoneInTheApp() = shoot("1495_drw_app_undo", PHONE_393) {
        tab(4, wide = false)
        tapText("테마", exact = true)
        val first = themes.first().title
        tapText(first, exact = true)
        tapText("이 테마 입기", exact = true)
        assertTrue(appears("입었어요. 기록은 그대로예요"))
        assertEquals(first, c.wardrobe.lease.value.worn?.title)
        tapText("되돌리기", exact = true)
        assertTrue(appears("되돌렸어요", exact = true))
        assertEquals(null, c.wardrobe.lease.value.worn)
        assertTrue(appears("내 모양 (테마 없음)"))
        // The starters stay after one is worn (PC 확인 e34fae4e), and saving asks for a name.
        assertTrue(appears("처음이라면 이런 테마부터"))
        tapText("지금 모양을 내 테마로 저장", prefix = true)
        assertTrue(appears("어떤 이름으로 저장할까요?"))
        typeInto("테마 이름", "출근용")
        tapText("저장", exact = true)
        assertEquals(listOf("출근용"), c.wardrobe.lease.value.history.filter { it.own }.map { it.title })
        reveal("‘출근용’으로 서랍에 저장했어요")
        reveal("🎨 출근용")
    }

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
    }
}
