package com.bamnat.core.recipe

import com.bamnat.core.layout.TabOrder
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TRY-02] 1.35 부분만 담기: a theme cut into parts per tab, and only the parts picked put back together. */
class LookPartsTest {
    private val theme = BuiltInThemes.of(WorkStyle.SHIFT).first()

    @Test fun everyBuiltInThemeHasPartsOnTheCalendar() {
        WorkStyle.entries.forEach { st ->
            BuiltInThemes.of(st).forEach { r ->
                val parts = LookParts.of(r)
                assertTrue(r.title, parts.isNotEmpty())
                assertTrue(r.title, parts.any { it.tab == TabOrder.CALENDAR })
            }
        }
    }

    @Test fun onePartTakesOnlyItsValues() {
        val days = LookParts.pick(theme, listOf("cal.days"))
        assertEquals(listOf(PieceKind.CAL_THEME), days.pieces.map { it.kind })
        val colors = LookParts.pick(theme, listOf("cal.colors"))
        colors.pieces.forEach { p ->
            assertEquals(PieceKind.SCREEN_LOOK, p.kind)
            val keys = ScreenLooks.decode(p.values["screens"] as String).looks.keys
            assertEquals(setOf("calendar"), keys)
        }
    }

    @Test fun allPartsTogetherKeepEveryLook() {
        val all = LookParts.pick(theme, LookParts.of(theme).map { it.id })
        val looks = theme.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.map { it.kind }.toSet()
        assertEquals(looks, all.pieces.map { it.kind }.toSet())
    }

    @Test fun twoScreenPartsJoin() {
        val r = Recipe("t", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.SCREEN_LOOK,
            mapOf("screens" to ScreenLooks().with("calendar", ScreenLook(bg = 0xFF102030)).with("memo", ScreenLook(bg = 0xFF203040)).encode()))))
        val both = LookParts.pick(r, listOf("cal.colors", "memo.colors"))
        assertEquals(setOf("calendar", "memo"), ScreenLooks.decode(both.pieces.single().values["screens"] as String).looks.keys)
        assertFalse(LookParts.of(r).any { it.id == "money.colors" })
    }

    @Test fun layerKeepsWhatTheTopLeavesOut() {
        val base = LookParts.pick(theme, listOf("cal.days"))
        val top = Recipe("t", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFF00FF00))))
        val out = LookParts.layer(base, top).pieces.single().values
        assertEquals(0xFF00FF00, out["sun"])
        assertEquals(base.pieces.single().values["sat"], out["sat"])
    }

    @Test fun titlesSayWhereFromAndWhat() {
        assertEquals("‘다크 골드’의 칸 디자인", LookParts.title("다크 골드", listOf("cal.cells")))
        assertEquals("‘다크 골드’의 칸 디자인 외 1", LookParts.title("다크 골드", listOf("cal.cells", "cal.days")))
    }

    @Test fun changedSaysThePartsMadeOnesOwn() {
        assertEquals(emptyList<String>(), LookParts.changed(theme, theme))
        val other = LookParts.layer(theme, Recipe("x", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("today" to 0xFF123456L)))))
        assertEquals(listOf("cal.days"), LookParts.changed(theme, other))
    }

    @Test fun withoutPhotosKeepsColoursAndDropsPictures() {
        val r = Recipe("p", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to "all~bg:FFFDF6E3|ph:0123456789abcdef|dm:30")),
            Piece(PieceKind.PHOTO_LOOK, mapOf("bg" to "0123456789abcdef", "corners" to "round")),
        ))
        assertTrue(LookParts.hasPhotos(r))
        val bare = LookParts.withoutPhotos(r)
        assertFalse(LookParts.hasPhotos(bare))
        assertEquals("all~bg:FFFDF6E3", bare.pieces.first { it.kind == PieceKind.SCREEN_LOOK }.values["screens"])
        assertEquals(mapOf("corners" to "round"), bare.pieces.first { it.kind == PieceKind.PHOTO_LOOK }.values)
        assertFalse(LookParts.hasPhotos(theme.copy(pieces = theme.pieces.filter { it.kind == PieceKind.CAL_THEME })))
    }

    /** [TRY-04] 비교의 한 뼈대: the side before takes the other's places (cards, cell heights, tabs) and keeps its own looks. */
    @Test fun frameTakesPlacesNotLooks() {
        val before = Recipe("b", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.LAYOUT, mapOf("calendar" to "summary,month", "tabs" to listOf("calendar", "memos"))),
            Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFFFF0000))))
        val after = Recipe("a", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.LAYOUT, mapOf("calendar" to "month,summary", "tabs" to listOf("memos", "calendar"))),
            Piece(PieceKind.CAL_VIEW, mapOf("size" to "big")),
            Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFF00FF00))))
        val framed = LookParts.frame(before, after)
        val layout = framed.pieces.first { it.kind == PieceKind.LAYOUT }.values
        assertEquals("month,summary", layout["calendar"])
        assertEquals(listOf("memos", "calendar"), layout["tabs"])
        assertEquals("big", framed.pieces.first { it.kind == PieceKind.CAL_VIEW }.values["size"])
        assertEquals(0xFFFF0000, framed.pieces.first { it.kind == PieceKind.CAL_THEME }.values["sun"])
        assertTrue(LookParts.changed(LookParts.frame(before, after), after).none { it in LookParts.FRAME })
    }
}
