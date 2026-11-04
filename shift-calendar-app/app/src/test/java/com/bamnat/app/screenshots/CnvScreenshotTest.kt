package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [CNV-03][CNV-06] 1.37 캔버스 스튜디오, numbers 1500~: a picked card lets go, and a card sent under the month and back leaves the others as they were. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CnvScreenshotTest : ScreenshotBase() {

    private fun shift() = app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }

    /** PC 확인 1.37 A1: 이번 달 요약을 고른 뒤 ‘고르기 끝’이 고르기를 풀지 못했어요. */
    @Test fun aPickedCardLetsGo() { shift(); shoot("1500_cnv_card_let_go", PHONE) {
        tapText("도구")
        tapText("카드 배치", exact = true)
        tapText("255h", exact = true)
        assertTrue("the card's bar", appears("달력 아래로 보내기", exact = true))
        tapText("고르기 끝", exact = true)
        assertTrue("let go", appears("화면: 달력 ▾", exact = true))
        assertTrue(gone("달력 아래로 보내기", exact = true))
    } }

    /** PC 확인 1.37 A1: 요약을 달력 아래로 보냈다 올리면 하루 카드가 말없이 숨겨졌어요. */
    @Test fun downAndUpKeepsTheOtherCards() { shift(); shoot("1501_cnv_card_across", PHONE) {
        tapText("도구")
        tapText("카드 배치", exact = true)
        val chips = listOf("＋ 카드 넣기") + (1..5).map { "＋ 카드 넣기 $it" }
        val before = chips.firstOrNull { shows(it, exact = true) }
        tapText("255h", exact = true)
        tapText("달력 아래로 보내기", exact = true)
        tapText("달력 위로 올리기", exact = true)
        tapText("고르기 끝", exact = true)
        assertTrue("화면: 달력 ▾ again", appears("화면: 달력 ▾", exact = true))
        org.junit.Assert.assertEquals("no card hidden by the move", before, chips.firstOrNull { shows(it, exact = true) })
    } }

    /** PC 확인 1.37 A2: 메모 › 카드 배치 › 뒤로 다음 달력 › 도구 › 카드 배치가 ‘화면: 메모’로 열렸어요. */
    @Test fun eachPageOpensItsOwnScreen() { shift(); shoot("1502_cnv_page_own_screen", PHONE) {
        tab(2, wide = false)
        tapText("카드 배치", exact = true)
        assertTrue(appears("화면: 메모 ▾", exact = true))
        back()
        tab(0, wide = false)
        tapText("도구")
        tapText("카드 배치", exact = true)
        assertTrue("달력, not 메모", appears("화면: 달력 ▾", exact = true))
    } }
}
