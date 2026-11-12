package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [PC-33] A 칸 디자인 that shows shifts by colour alone, and the same look keeping their letters ('글자 남기기'). */
class CellLettersTest {
    private fun work(looks: String, badge: String? = null) = Recipe(
        title = "칸 시험", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.CAL_VIEW, buildMap { put("looks", looks); badge?.let { put("badge", it) } })),
    )

    @Test fun aDotOrALineOrAFullCellIsColourOnly() {
        // 야간 회복 스테이션's three looks: 점, 점, 표시 없음 (쉬는 날).
        val night = "bg:FF0F1630|ba:80|cr:12|mk:dot|mp:below|pd:2/cr:12|dc:FFB49CFF|mk:dot|mp:below/cr:12|dc:FF4B557A|mk:none|pd:2"
        assertEquals("dot", CellLetters.colourOnly(work(night)))
        assertEquals("line", CellLetters.colourOnly(work("cr:6|mk:line|mp:bottom")))
        assertEquals("fill", CellLetters.colourOnly(work("mk:fill|ba:80|cr:8")))
        // A look that names no mark takes 달력 모양's badge.
        assertEquals("dot", CellLetters.colourOnly(work("cr:4|pd:3", badge = "dot")))
        assertNull(CellLetters.colourOnly(work("cr:4|mk:small|mp:below")))
        assertNull(CellLetters.colourOnly(work("cr:4|pd:3", badge = "small")))
        assertNull(CellLetters.colourOnly(Recipe(title = "칸 없음", styles = emptySet(), pieces = emptyList())))
    }

    @Test fun keepingLettersChangesOnlyTheMark() {
        assertEquals("bg:FF0F1630|ba:80|cr:12|mk:small|mp:below/cr:12|mk:none",
            CellLetters.keepLetters("bg:FF0F1630|ba:80|cr:12|mk:dot|mp:below/cr:12|mk:none"))
        assertEquals("cr:6|mk:small|mp:bottom", CellLetters.keepLetters("cr:6|mk:line|mp:bottom"))
        // 꽉 채우기 keeps the shift's colour as the cell's own, as strong as it was.
        assertEquals("mk:small|ba:80|cr:8|bg:shift", CellLetters.keepLetters("mk:fill|ba:80|cr:8"))
        assertEquals("mk:small|cr:8|bg:shift|ba:85", CellLetters.keepLetters("mk:fill|cr:8"))
        val kept = CellLetters.keep(work("cr:4|pd:3", badge = "dot"))
        assertEquals("small", kept.pieces.single().values["badge"])
        assertNull(CellLetters.colourOnly(kept))
        assertNull(CellLetters.colourOnly(CellLetters.keep(work("mk:fill|cr:8/mk:dot"))))
        // Every look still reads as a look (the server and the app check the same text).
        assertNull(CellLook.looksProblem(CellLetters.keepLetters("mk:fill|ba:80|cr:8/cr:12|dc:FFB49CFF|mk:dot|mp:below")))
    }

    @Test fun itIsSaidInTheUsersWords() {
        assertEquals("칸의 근무 글자 대신 색 점만 보여요", CellLetters.said("dot"))
        assertEquals("칸의 근무 글자 대신 칸 색만 보여요", CellLetters.said("fill"))
    }
}
