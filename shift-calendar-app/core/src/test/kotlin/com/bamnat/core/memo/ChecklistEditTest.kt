package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVI-35] (검토 MEMO-16) 체크리스트 기본기. */
class ChecklistEditTest {
    private val list = listOf(ChecklistItem("장갑"), ChecklistItem("물", true), ChecklistItem("열쇠"))

    @Test fun aPastedListBecomesALineEach() {
        // 카톡으로 받은 준비물, pasted into an empty line: was 2 lines ("장갑" and "물 - 열쇠 ☑ 보조배터리"), now 4.
        val (out, focus) = ChecklistEdit.typed(listOf(ChecklistItem("")), 0, "장갑\n- 물\n\n열쇠\n☑ 보조배터리")
        assertEquals(listOf(ChecklistItem("장갑"), ChecklistItem("물"), ChecklistItem("열쇠"), ChecklistItem("보조배터리", true)), out)
        assertEquals(3, focus)
    }

    @Test fun oneEnterStillMakesTheNextLine() {
        val (out, focus) = ChecklistEdit.typed(list, 0, "장갑\n")
        assertEquals(4, out.size)
        assertEquals(ChecklistItem(""), out[1])
        assertEquals(1, focus)
        // Plain typing changes the line only.
        assertEquals("장갑 두 켤레", ChecklistEdit.typed(list, 0, "장갑 두 켤레").first[0].text)
        assertNull(ChecklistEdit.typed(list, 0, "장갑 두 켤레").second)
    }

    @Test fun backspaceAtTheStartJoinsTheLineAbove() {
        val (out, line, caret) = ChecklistEdit.joinAbove(list, 2, 0)!!
        assertEquals(listOf(ChecklistItem("장갑열쇠"), ChecklistItem("물", true)), out)
        assertEquals(0, line)
        assertEquals(2, caret)
        // An empty line just goes.
        val empty = listOf(ChecklistItem("장갑"), ChecklistItem(""))
        assertEquals(listOf(ChecklistItem("장갑")), ChecklistEdit.joinAbove(empty, 1, 0)!!.first)
        // The first line has nothing above it.
        assertNull(ChecklistEdit.joinAbove(list, 0, null))
    }

    @Test fun linesMove() {
        assertEquals(listOf("열쇠", "장갑", "물"), ChecklistEdit.move(list, 2, 0).map { it.text })
        assertEquals(list, ChecklistEdit.move(list, 1, 1))
    }

    @Test fun checksSurviveTurningIntoWordsAndBack() {
        val text = ChecklistEdit.toText(list)
        assertEquals("장갑\n☑ 물\n열쇠", text)
        assertEquals(list, ChecklistEdit.fromText(text))
    }
}
