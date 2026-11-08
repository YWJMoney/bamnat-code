package com.bamnat.core

import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.keepMyCellContent
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** [CELL-02] 1.39 Someone else's theme changes how the cells look, never what they show. */
class KeepMyCellContentTest {
    private val theme = Recipe(
        title = "테마", styles = WorkStyle.entries.toSet(),
        pieces = listOf(
            Piece(PieceKind.CAL_VIEW, mapOf("size" to "tall", "badge" to "line", "looks" to "x", "memo" to 0L, "order" to "holiday,memo",
                "hours" to false, "lunar" to true, "spend" to true)),
            Piece(PieceKind.CAL_THEME, mapOf("today" to 1L, "memo" to "kept")),
        ),
    )

    @Test fun theLookStaysAndWhatTheCellsShowIsLeftOut() {
        val r = theme.keepMyCellContent()
        assertEquals(mapOf("size" to "tall", "badge" to "line", "looks" to "x"), r.pieces[0].values)
    }

    @Test fun otherPiecesAreUntouched() {
        assertEquals(theme.pieces[1], theme.keepMyCellContent().pieces[1])
        assertEquals(theme.title, theme.keepMyCellContent().title)
    }
}
