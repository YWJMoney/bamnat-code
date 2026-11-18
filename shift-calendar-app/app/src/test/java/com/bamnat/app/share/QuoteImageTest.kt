package com.bamnat.app.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.Quote
import com.bamnat.core.money.QuoteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 [RVP-64] (검토 REST-05) A 견적서 goes to a customer: a long note (조건, 계좌) and an item's long name are on the picture whole,
 * wrapped, never cut with '…' at about 30 and 9 letters as before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QuoteImageTest {
    private val note = "자재 포함, 철거 폐기물 별도. 계약금 30% 선입금, 잔금은 공사 끝난 날 입금해 주세요. 국민 123-456-7890123 홍길동. " +
        "공사 중 추가로 생기는 일은 미리 말씀드리고 따로 견적을 드려요. 주말 공사는 이웃 동의가 있어야 해요."
    private val quote = Quote(
        date = LocalDate.of(2026, 11, 12), client = "김민지", title = "욕실 리모델링",
        items = listOf(QuoteItem("욕실 타일 철거 및 방수 공사", 1.0, "식", 1_800_000), QuoteItem("도기", 2.0, "개", 150_000)),
        note = note,
    )

    @Test fun aLongNoteAndANameAreWholeOnThePicture() {
        val lines = QuoteImage.lines(quote, "밤낮 인테리어")
        val said = lines.note.joinToString(" ")
        assertFalse("the note was cut: $said", said.contains('…'))
        assertEquals(note.replace(" ", ""), said.replace(" ", ""))
        assertTrue("the note wraps", lines.note.size >= 3)
        val name = lines.names.first()
        assertEquals("욕실타일철거및방수공사", name.joinToString("").replace(" ", ""))
        assertFalse(name.joinToString().contains('…'))
        assertTrue("a long name takes its second line", name.size == 2)
        assertEquals(listOf("도기"), lines.names[1])
    }

    @Test fun thePictureGrowsToHoldThem() {
        val short = QuoteImage.render(quote.copy(note = "자재 포함"), "", mark = false)
        val long = QuoteImage.render(quote, "", mark = false)
        assertTrue("${long.height} > ${short.height}", long.height > short.height)
    }
}
