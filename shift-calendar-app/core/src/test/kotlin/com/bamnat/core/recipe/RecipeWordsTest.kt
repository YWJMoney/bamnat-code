package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVP-65][RVP-72] (검토 REST-06·REST-14·REST-20) A recipe's summary calls a thing what the app calls it, and a part without a
 * name does not break the summary.
 */
class RecipeWordsTest {
    @Test fun aThemesNightIsNotTheNightShift() {
        assertEquals("야간 근무 때 어둡게", RecipeWords.PICKS["night"])
        assertEquals("주간", RecipeWords.PICKS["day"])
        assertEquals("세는 방식", RecipeWords.KEYS["mode"])
    }

    @Test fun theScreensByTheStudiosNames() {
        val names = RecipeDescribe.SCREEN_NAMES.values
        assertTrue("마을" in names)
        assertFalse("광장 is the old name of 마을: $names", "광장" in names)
        assertFalse(BuiltInThemes.all.any { "광장" in it.desc }) // the 48 theme notes
    }

    @Test fun aFontFromTheCatalogIsNamed() {
        assertEquals("명조", RecipeDescribe.fontName("serif"))
        val f = FontCatalog.ALL.first { it.id !in setOf("sans", "serif", "mono", "hand") }
        assertEquals("${f.name} 글꼴", RecipeDescribe.fontName(f.id))
    }

    @Test fun aPartWithoutANameIsPassedOver() {
        val p = Piece(PieceKind.PARTS, mapOf("parts" to listOf(mapOf("id" to 7L), mapOf("id" to "cal.cell"))))
        val line = RecipeDescribe.line(p)
        assertTrue(line, line.startsWith("부품 1개 꾸밈"))
    }
}
