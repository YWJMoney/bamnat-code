package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.40 [SPREAD-37] 연말 근무 결산의 앞·뒤 화면 사진(2.0.0 디자인 품질 조건 1·2·3·5·7·8·12·13): 12월 22일의 알림함 줄(달력 위 띠가 아니라
 * 종 안 '안내', 누르면 바로 결산), 결산 창, 사진 창의 결산 한 줄. 밝은·어두운 × 글자 100%·200% × 접은 폭·편 폭 여덟 모습.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class YearRecapShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_light_200", FOLDED, 2f),
        Triple("c_folded_dark_100", FOLDED_NIGHT, 1f), Triple("d_folded_dark_200", FOLDED_NIGHT, 2f),
        Triple("e_open_light_100", UNFOLDED, 1f), Triple("f_open_light_200", UNFOLDED, 2f),
        Triple("g_open_dark_100", UNFOLDED_NIGHT, 1f), Triple("h_open_dark_200", UNFOLDED_NIGHT, 2f),
    )

    /** The 결산 shows from 12월 20일: the shots are taken on 12월 22일, the seeded 교대 calendar running since 7월. */
    @Before fun december() = Today.fix(java.time.ZonedDateTime.of(2026, 12, 22, 10, 30, 0, 0, SEOUL))
    @After fun clockBack() = Today.fix(SHOT_TIME)

    private fun all(name: String, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("yr_${name}_$look", q, scale, style(WorkStyle.SHIFT) { go() })
    }

    /**
     * 총괄 #433 검토 (앞 사진 하나의 미리보기가 비어 있었어요): the picture is drawn off the main thread, so a shot of a sheet waits
     * until it is on screen (its words are the Image's).
     */
    private fun Screen.drawn(preview: String) = assertTrue("미리보기가 그려져요", appears(preview, exact = true, ms = PICTURE_MS))

    /** The bell beside 공유, then the 결산's row in the 알림함's 안내. */
    private fun Screen.recapRow() {
        tapText("알림 ", prefix = true, scrolls = 0)
        settle()
        reveal(ROW, exact = true)
    }

    /** 조건 3·8: one row that says the year's numbers and goes straight to the 결산 (was: open the row, then the card's 결산 보기). */
    @Test fun inboxRow() = all("01_inbox") {
        recapRow()
        assertTrue("줄이 숫자를 말해요", appears("야간 ", exact = false))
        assertTrue("누르면 결산으로 가요", appears("결산 보기", exact = true))
    }

    @Test fun recapSheet() = all("02_sheet") {
        recapRow()
        tapText(ROW, exact = true)
        settle()
        drawn("결산 사진 미리보기")
        // 조건 7: a failure or a save is said in the sheet, never a toast under it.
        assertFalse(appears("저장하지 못했어요", exact = false, ms = 0))
    }

    /** 조건 2·12: the two buttons, at the sheet's end, whole at 200% on the folded screen. */
    @Test fun recapSheetButtons() = all("03_sheet_end") {
        recapRow()
        tapText(ROW, exact = true)
        settle()
        drawn("결산 사진 미리보기")
        reveal("보내기", exact = true)
        assertTrue(appears("보내기", exact = true))
    }

    /** 조건 1: the month's picture first in its own sheet; the 결산 is one quiet line under the title, not a button over the picture. */
    @Test fun pictureSheet() = all("04_picture") {
        val snap = runBlocking { app.container.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ImageShareSheet(snap, Today.month(), java.time.DayOfWeek.SUNDAY, emptyList(), {}, {},
            yearRecap = ROW to {}) }
        settle()
        drawn("보낼 사진 미리보기")
        assertTrue(appears("$ROW 사진도 만들 수 있어요", exact = false))
    }

    companion object {
        const val ROW = "2026년 내 근무 결산"
        const val PICTURE_MS = 15_000L
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
