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

    /** PC 확인 C4: '돈' finds 💰 first and not 💇 (머리 정돈). */
    @Test
    fun emojiFoundByName() {
        val found = RecipeText.findEmoji("돈").map { it.emoji }
        assertTrue("💰 among the first: $found", "💰" in found.take(6))
        assertFalse("💇" in found)
    }

    /** [LAY-10][LAY-11] 누락 점검 N12·N8: a photo by its picture id and a decoration that presses a 규칙 단추 go through settings and recipe. */
    @Test
    fun photosAndButtonsGoThrough() {
        val photo = DecorItem(DecorKind.PHOTO, "0123456789abcdef", "bg.memo", w = 96, h = 72, r = 8)
        val bell = DecorItem(DecorKind.EMOJI, "🔔", Decor.HEAD, tap = "alarm1")
        val d = Decor(listOf(photo, bell))
        assertEquals(d, Decor.decode(d.encode()))
        assertEquals(setOf("0123456789abcdef"), d.images)
        val r = RecipeCodec.read(RecipeCodec.write(Recipe("꾸밈", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT),
            pieces = listOf(Piece(PieceKind.DECOR, d.values())))))
        assertEquals(d, Decor.of(r.pieces.single().values))
        // 사진 빼고 올리기 keeps the bell, leaves the photo on the phone.
        val bare = LookParts.withoutPhotos(r)
        assertEquals(Decor(listOf(bell)), Decor.of(bare.pieces.single().values))
        assertTrue(LookParts.hasPhotos(r))
        // PC 확인 C7: a 꾸밈 of one photo alone is a photo too, and goes as a whole.
        val alone = Recipe("꾸밈", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.DECOR, Decor(listOf(photo)).values())))
        assertTrue(LookParts.hasPhotos(alone))
        assertTrue(LookParts.withoutPhotos(alone).pieces.isEmpty())
        // A photo that is not a picture id, or a button id that is not one, is not taken.
        assertEquals(Decor(), Decor.decode("""[{"photo":"../x","at":"app","w":10,"h":10}]"""))
        assertNull(Decor.decode("""[{"emoji":"⭐","at":"app","tap":"Bad-Id","w":10,"h":10}]""").items.single().tap)
        assertEquals("tap", Decor.tapProblem("Bad-Id"))
        assertEquals("photo", Decor.photoProblem("ABC"))
    }

    /** PC 확인 C6 1.36: 화면 가득 is a screen background's own; on a cell it is not kept. */
    @Test
    fun fullIsForBackgroundsOnly() {
        val bg = DecorItem(DecorKind.PHOTO, "0123456789abcdef", "bg.calendar", full = true)
        assertEquals(Decor(listOf(bg)), Decor.decode(Decor(listOf(bg)).encode()))
        assertFalse(Decor.decode("""[{"photo":"0123456789abcdef","at":"cal.cell","w":10,"h":10,"full":true}]""").items.single().full)
    }
}

