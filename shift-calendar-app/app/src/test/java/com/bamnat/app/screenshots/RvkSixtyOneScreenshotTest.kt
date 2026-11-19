package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-61] (총괄 HQ-111 묶음, 조건 8·10·12): a 가계부 line slid to the left or deleted from its sheet goes at once, as it is kept
 * 30 days in 최근 지운 기록 and 되돌리기 puts it back; before it sprang back and asked 지울까요?. The bar says where it is kept and stays long,
 * and does not hide under the 돈 적기 bar at 200%. Several lines still ask. The code before draws the same scenes. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkSixtyOneScreenshotTest : ScreenshotBase() {
    /** 내역에서 '팀 점심'을 왼쪽으로 끝까지 밀기. */
    private fun slide(): Screen.() -> Unit = style(WorkStyle.DAY, ledgerPage("내역", false) {
        settle()
        reveal("팀 점심", exact = false)
        swipeRowLeft("팀 점심", until = "없는 글", dp = 380, times = 1)
        settle()
    })

    /** 줄을 눌러 연 창의 휴지통 단추. */
    private fun sheet(): Screen.() -> Unit = style(WorkStyle.DAY, ledgerPage("내역", false) {
        settle()
        tapText("팀 점심", exact = false)
        tapText("지우기", exact = true, scrolls = 0)
        settle()
    })

    @Test fun slideFolded() = shoot("rvk61_01_slide_delete_folded", FOLDED, slide())
    @Test fun slideDarkBig() = shoot("rvk61_02_slide_delete_folded_dark_200", DARK, 2f, slide())
    @Test fun sheetFolded() = shoot("rvk61_03_sheet_delete_folded", FOLDED, sheet())
    @Test fun slideWide() = shoot("rvk61_04_slide_delete_unfolded", UNFOLDED, style(WorkStyle.DAY, ledgerPage("내역", true) {
        settle()
        reveal("팀 점심", exact = false)
        swipeRowLeft("팀 점심", until = "없는 글", dp = 380, times = 1)
        settle()
    }))

    /** The words the new code says; not in the photos of the code before. */
    @Test fun goesAtOnceAndSaysWhereItIsKept() = shoot("rvk61_05_said", FOLDED, style(WorkStyle.DAY, ledgerPage("내역", false) {
        settle()
        reveal("팀 점심", exact = false)
        swipeRowLeft("팀 점심", until = "없는 글", dp = 380, times = 1)
        settle()
        assertTrue("바로 지웠다고 말해요", appears("‘팀 점심’ 기록을 지웠어요. 최근 지운 기록에 30일 있어요", exact = true))
        assertFalse("지울까요 창은 뜨지 않아요", shows("지울까요?", exact = true))
        assertTrue("되돌리기", shows("되돌리기", exact = true))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        assertTrue("되돌리면 줄이 돌아와요", scrolledTo("팀 점심", exact = false))
    }))

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
