package com.bamnat.core.recipe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LAY-01][LAY-02] 1.36 꾸밈 층: values in and out, the days a cell decoration stands on, and the server's ids the same as core's. */
class DecorTest {
    private val star = DecorItem(DecorKind.SHAPE, "star5", Decor.CELL, on = "d25", x = 820, y = 200, w = 12, h = 12, fill = 0xFFFFC107, lineW = 1.5)

    @Test
    fun itemsGoThroughSettingsAndRecipeTheSame() {
        val d = Decor(listOf(star, DecorItem(DecorKind.TEXT, "힘내요", "bg.calendar", size = 13.5, weight = 700, italic = true, group = 2),
            DecorItem(DecorKind.EMOJI, "🌙", Decor.HEAD, rot = 15, alpha = 60)))
        assertEquals(d, Decor.decode(d.encode()))
        val r = RecipeCodec.read(RecipeCodec.write(Recipe("꾸밈", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT),
            pieces = listOf(Piece(PieceKind.DECOR, d.values())))))
        assertEquals(d, Decor.of(r.pieces.single().values))
    }

    @Test
    fun cellDecorationsStandOnTheirDays() {
        fun day(dom: Int, dow: Int = 3, role: String? = "day", today: Boolean = false, holiday: Boolean = false, month: Int = 11) =
            DecorDay(month, dom, dow, role, today, holiday)
        assertTrue(star.standsOn(day(25)))
        assertFalse(star.standsOn(day(24)))
        assertTrue(star.copy(on = "role.night").standsOn(day(3, role = "night")))
        assertTrue(star.copy(on = "w6").standsOn(day(1, dow = 6)))
        assertTrue(star.copy(on = "sun").standsOn(day(2, dow = 7)))
        assertTrue(star.copy(on = "m12d25").standsOn(day(25, month = 12)))
        assertFalse(star.copy(on = "m12d25").standsOn(day(25, month = 11)))
        assertTrue(star.copy(on = "holiday").standsOn(day(3, holiday = true)))
        assertTrue(star.copy(on = null).standsOn(day(9)))
    }

    @Test
    fun aMonthDrawsAtMostTwelveInItsCells() {
        val many = Decor(List(20) { star.copy(x = it * 40) } + DecorItem(DecorKind.ICON, "star", Decor.APP))
        val back = Decor.decode(many.encode())
        assertEquals(Decor.MAX_CELL, back.on(Decor.CELL).size)
        assertEquals(1, back.on(Decor.APP).size)
    }

    @Test
    fun whatItCannotDrawIsLeftOut() {
        assertTrue(Decor.decode("""[{"shape":"unicorn","at":"app","x":1,"y":1,"w":9,"h":9},{"icon":"star","at":"nowhere","x":1,"y":1,"w":9,"h":9}]""").isDefault)
        assertTrue(Decor.decode("not json").isDefault)
        assertNull(Decor.decode("""[{"shape":"rect","at":"app","on":"d25","x":1,"y":1,"w":9,"h":9}]""").items.single().on) // days only in cells
    }

    @Test
    fun hiddenOnesAreKeptButNotDrawn() {
        val d = Decor(listOf(star.copy(hide = true)))
        assertEquals(1, Decor.decode(d.encode()).items.size)
        assertTrue(d.on(Decor.CELL).isEmpty())
    }

    @Test
    fun theServerKnowsTheSameGlyphsAndPlaces() {
        val js = File("../server/company-worker/src/decor.js").readText()
        fun list(name: String) = Regex("export const $name = \\[([^\\]]*)\\]").find(js)!!.groupValues[1].let { Regex("\"([a-z0-9_]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        assertEquals(DecorGlyphs.SHAPE_LIST.map { it.id }, list("DECOR_SHAPES"))
        assertEquals(DecorGlyphs.ICON_LIST.map { it.id }, list("DECOR_ICONS"))
        assertEquals(Decor.SCREENS, list("DECOR_SCREENS"))
        assertEquals(Decor.LINES, list("DECOR_LINES"))
        assertEquals(DecorGlyphs.SHAPE_LIST.size, DecorGlyphs.SHAPES.size)
        assertEquals(DecorGlyphs.ICON_LIST.size, DecorGlyphs.ICONS.size)
    }

    @Test
    fun theLayerSaysWhatItHas() {
        val line = RecipeDescribe.line(Piece(PieceKind.DECOR, Decor(listOf(star, star.copy(kind = DecorKind.EMOJI, glyph = "🌙"))).values()))
        assertTrue(line, line.startsWith("꾸밈 2개"))
    }
}
