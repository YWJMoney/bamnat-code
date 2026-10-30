package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RCX-18] 테마 색: one change follows everywhere the colour was used, and it survives writing and reading. */
class RecipeTokensTest {
    private val night = 0xFF3F51B5L
    private val r = Recipe(
        title = "밤 세트", styles = setOf(WorkStyle.SHIFT),
        pieces = listOf(
            Piece(PieceKind.SHIFT_LOOK, mapOf("items" to listOf(
                mapOf("name" to "주간", "role" to "day", "label" to "주", "color" to 0xFFFFA000L),
                mapOf("name" to "야간", "role" to "night", "label" to "야", "color" to night),
            ))),
            Piece(PieceKind.CAL_THEME, mapOf("today" to night, "sun" to 0xFFE53935L)),
        ),
    )

    @Test fun bindChangeAndRead() {
        val b = RecipeTokens.bind(r)
        assertEquals(listOf("주간", "야간", "일요일"), b.tokens.map { it.name })
        assertEquals(2, RecipeTokens.uses(b, "야간"))
        val changed = RecipeTokens.set(b, "야간", 0xFF000080L)
        val back = RecipeCodec.read(RecipeCodec.write(changed))
        assertEquals(changed.tokens, back.tokens)
        val resolved = RecipeResolve.piece(back, back.pieces[1], emptyMap())
        assertEquals(0xFF000080L, resolved["today"])
        assertTrue(RecipeTokens.unbind(changed).tokens.isEmpty())
        assertEquals(0xFF000080L, RecipeTokens.unbind(changed).pieces[1].values["today"])
    }
}
