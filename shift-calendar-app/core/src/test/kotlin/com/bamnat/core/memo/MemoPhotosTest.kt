package com.bamnat.core.memo

import com.bamnat.core.memo.MemoPhotos.Align
import com.bamnat.core.memo.MemoPhotos.Photo
import com.bamnat.core.memo.MemoPhotos.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MPH-01][MPH-02][MPH-04] 1.38 메모 사진: the photo lines in the body read and write back the same. */
class MemoPhotosTest {
    private val a = "0123abcd-aaaa.jpg"
    private val b = "4567efgh-bbbb.jpg"

    @Test
    fun aBodyWithoutPhotosIsOneText() {
        assertEquals(listOf(Text("장보기\n우유")), MemoPhotos.parse("장보기\n우유"))
        assertEquals("장보기\n우유", MemoPhotos.plain("장보기\n우유"))
        assertFalse(MemoPhotos.has("장보기"))
    }

    @Test
    fun photosAndWordsReadBackTheSame() {
        val bodies = listOf(
            "위\n⟦사진 $a 60 왼쪽⟧\n아래",
            "⟦사진 $a 100 가운데⟧",
            "⟦사진 $a 100 가운데⟧\n⟦사진 $b 40 오른쪽⟧",
            "⟦사진 $a 100 가운데⟧\n\n⟦사진 $b 40 오른쪽⟧\n",
            "글\n⟦사진 $a 45 왼쪽 옆⟧\n옆에 쓴 말\n두 줄\n⟦/사진⟧\n밑에 쓴 말",
            "⟦사진 $a 45 오른쪽 옆⟧\n\n⟦/사진⟧",
        )
        for (body in bodies) assertEquals(body, body, MemoPhotos.join(MemoPhotos.parse(body)))
    }

    @Test
    fun wordsBesideAPhotoAreItsOwn() {
        val blocks = MemoPhotos.parse("글\n⟦사진 $a 45 왼쪽 옆⟧\n옆 말\n⟦/사진⟧\n밑")
        assertEquals(listOf(Text("글"), Photo(a, 45, Align.LEFT, "옆 말"), Text("밑")), blocks)
        assertEquals("글\n옆 말\n밑", MemoPhotos.plain("글\n⟦사진 $a 45 왼쪽 옆⟧\n옆 말\n⟦/사진⟧\n밑"))
    }

    @Test
    fun aLineThatIsNotQuiteAPhotoStaysWords() {
        val body = "⟦사진 ../../x.jpg 50 왼쪽⟧\n⟦사진 $a 50 위쪽⟧"
        assertEquals(listOf(Text(body)), MemoPhotos.parse(body))
        assertEquals(emptyList<String>(), MemoPhotos.names(body))
    }

    @Test
    fun widthIsKeptInRange() {
        assertEquals(Photo(a, MemoPhotos.MIN_WIDTH, Align.LEFT), MemoPhotos.parse("⟦사진 $a 3 왼쪽⟧").single())
        assertEquals(100, (MemoPhotos.parse("⟦사진 $a 300 왼쪽⟧").single() as Photo).width)
        assertEquals(Photo(a), MemoPhotos.parse("⟦사진 $a⟧").single())
    }

    @Test
    fun insertGoesWhereTheCaretIs() {
        val mid = MemoPhotos.insert("가나|다라".replace("|", ""), 2, a)
        assertEquals("가나\n⟦사진 $a 100 가운데⟧\n다라", mid.text)
        assertEquals("가나\n⟦사진 $a 100 가운데⟧\n".length, mid.caret)
        assertEquals("⟦사진 $a 100 가운데⟧\n", MemoPhotos.insert("", 0, a).text)
        assertEquals("글\n⟦사진 $a 100 가운데⟧\n", MemoPhotos.insert("글", null, a).text)
        assertEquals("⟦사진 $a 100 가운데⟧\n글", MemoPhotos.insert("글", 0, a).text)
        val parsed = MemoPhotos.parse(mid.text)
        assertEquals(listOf(Text("가나"), Photo(a), Text("다라")), parsed)
    }

    @Test
    fun resizeAlignAndWordsBeside() {
        var body = "위\n⟦사진 $a 100 가운데⟧\n아래"
        body = MemoPhotos.update(body, 1) { it.copy(width = 40, align = Align.RIGHT, side = "") }
        assertEquals("위\n⟦사진 $a 40 오른쪽 옆⟧\n\n⟦/사진⟧\n아래", body)
        body = MemoPhotos.update(body, 1) { it.copy(side = "옆 말") }
        // Back to the middle: the words beside it are kept, below the photo.
        body = MemoPhotos.update(body, 1) { it.copy(align = Align.CENTER) }
        assertEquals("위\n⟦사진 $a 40 가운데⟧\n옆 말\n아래", body)
    }

    @Test
    fun removeKeepsTheWordsBesideAndJoinsTheText() {
        val body = "위\n⟦사진 $a 40 왼쪽 옆⟧\n옆 말\n⟦/사진⟧\n아래"
        assertEquals("위\n옆 말\n아래", MemoPhotos.remove(body, 1))
        assertEquals("위\n아래", MemoPhotos.remove("위\n⟦사진 $a 100 가운데⟧\n아래", 1))
    }

    @Test
    fun unwrapPutsTheWordsUnderThePhoto() {
        val body = "⟦사진 $a 40 왼쪽 옆⟧\n옆 말\n⟦/사진⟧\n아래"
        assertEquals("⟦사진 $a 40 왼쪽⟧\n옆 말\n아래", MemoPhotos.unwrap(body, 0))
    }

    @Test
    fun movePastTheNextBlock() {
        val body = "위\n⟦사진 $a 100 가운데⟧\n아래"
        assertEquals("⟦사진 $a 100 가운데⟧\n위\n아래", MemoPhotos.move(body, 1, -1))
        assertEquals("위\n아래\n⟦사진 $a 100 가운데⟧", MemoPhotos.move(body, 1, 1))
        assertEquals(body, MemoPhotos.move(body, 0, 1))
    }

    @Test
    fun theMemoShowsWordsOnly() {
        val m = Memo(title = "", body = "⟦사진 $a 100 가운데⟧\n#장보기 우유\n⟦사진 $b 50 왼쪽⟧")
        assertEquals("#장보기 우유", m.preview)
        assertEquals(listOf("장보기"), m.tags)
        assertEquals("#장보기 우유".length, m.characterCount)
        assertTrue(m.matches("우유"))
        assertFalse(m.matches("jpg"))
        assertFalse(m.isBlank)
        assertEquals(listOf(a, b), m.photos)
        assertEquals("사진 2장", Memo(body = "⟦사진 $a⟧\n⟦사진 $b⟧").preview)
    }

    /** [MPH-05] 메모 종이: one line at the top, never in the words. */
    @Test
    fun paperSitsAtTheTop() {
        val body = MemoPhotos.withPaper("장보기\n우유", MemoPhotos.PaperKind.LINES)
        assertEquals("⟦종이 줄⟧\n장보기\n우유", body)
        assertEquals(MemoPhotos.PaperKind.LINES, MemoPhotos.paper(body))
        assertEquals("장보기\n우유", MemoPhotos.plain(body))
        assertEquals("⟦종이 모눈⟧\n장보기\n우유", MemoPhotos.withPaper(body, MemoPhotos.PaperKind.GRID))
        assertEquals("장보기\n우유", MemoPhotos.withPaper(body, null))
        assertEquals(listOf(MemoPhotos.Paper(MemoPhotos.PaperKind.DOTS), Text("")), MemoPhotos.parse(MemoPhotos.withPaper("", MemoPhotos.PaperKind.DOTS)))
        assertEquals("장보기", Memo(body = "⟦종이 줄⟧\n장보기").preview)
    }

    /** [MPH-07] 자유 판: pieces read back the same, words searchable, photos counted, a cut or odd piece left out. */
    @Test
    fun boardReadsBackAndShowsItsWords() {
        val board = MemoPhotos.Board(60, listOf(
            MemoPhotos.BoardItem(MemoPhotos.BoardKind.PHOTO, a, 300, 400, 500, 350),
            MemoPhotos.BoardItem(MemoPhotos.BoardKind.TEXT, "오늘, 바다; 100% ⟧ 좋음\n둘째 줄", 700, 200, 400, 10, 22, 0xFF112233, null),
            MemoPhotos.BoardItem(MemoPhotos.BoardKind.STICKER, "🌊", 900, 900, 120),
        ))
        val body = MemoPhotos.insertBoard("위", null, board).text
        val parsed = MemoPhotos.parse(body)
        assertEquals(listOf(Text("위"), board, Text("")), parsed)
        assertEquals("위\n오늘, 바다; 100% ⟧ 좋음\n둘째 줄\n", MemoPhotos.plain(body))
        assertEquals(listOf(a), MemoPhotos.names(body))
        assertEquals(1, MemoPhotos.count(body))
        assertTrue(Memo(body = body).matches("바다"))
        // Taken out, its words stay.
        assertEquals("위\n오늘, 바다; 100% ⟧ 좋음\n둘째 줄\n", MemoPhotos.remove(body, 1))
        val odd = "⟦자유판 999 p,1,1,1,0,18,-,-,..%2F..%2Fx.jpg;q,1;t,5000,-3,10,725,200,-,-,hi⟧"
        val b = MemoPhotos.parse(odd).single() as MemoPhotos.Board
        assertEquals(MemoPhotos.MAX_RATIO, b.ratio)
        assertEquals(listOf(MemoPhotos.BoardItem(MemoPhotos.BoardKind.TEXT, "hi", 1000, 0, MemoPhotos.MIN_ITEM_W, 5, 72)), b.items)
    }
}
