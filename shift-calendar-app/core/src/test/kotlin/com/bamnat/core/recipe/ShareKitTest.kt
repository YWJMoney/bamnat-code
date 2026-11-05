package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.37 [SHR-31][SHR-32] 묶음 E: what a 근무표 링크 carries of the sharer's 테마 and 근무 설정, and that pay never goes. */
class ShareKitTest {
    private val cycle = """{"app":"com.bamnat.app","format":"bamnat-company","v":1,"company":"","shiftTypes":[]}"""
    private val paid = """{"app":"com.bamnat.app","format":"bamnat-company","v":1,"company":"","shiftTypes":[],"pay":{"items":[]}}"""
    private val shiftLook = Piece(PieceKind.SHIFT_LOOK, mapOf("items" to listOf(
        mapOf("name" to "주간", "role" to "day", "label" to "주", "color" to 0xFF2196F3L, "emoji" to null),
        mapOf("name" to "야간", "role" to "night", "label" to "야", "color" to 0xFF3F51B5L, "emoji" to null),
    )))
    private val alarms = Piece(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "day", "before" to listOf(60L, 30L)))))

    @Test
    fun aThemeGoesWithItsLooksOnlyAndComesBackTheSame() {
        val theme = BuiltInThemes.of(WorkStyle.SHIFT).first()
        val mixed = theme.copy(pieces = theme.pieces + alarms)
        val text = ShareKit.lookText(mixed)!!
        assertTrue(text.length <= ShareKit.LOOK_CHARS)
        val back = ShareKit.readLook(text)!!
        assertTrue(back.pieces.all { it.kind in BuiltInThemes.LOOK_KINDS })
        assertEquals(theme.pieces.map { it.kind }.toSet(), back.pieces.map { it.kind }.toSet())
        // No look at all: nothing goes, and a kit is no theme.
        assertNull(ShareKit.lookText(Recipe("없음", styles = setOf(WorkStyle.SHIFT), pieces = listOf(alarms))))
        assertNull(ShareKit.readLook(ShareKit.kitText(Recipe("설정", styles = setOf(WorkStyle.SHIFT), pieces = listOf(alarms)))))
    }

    @Test
    fun aKitCarriesShiftsAlarmsAndTheCycleButNeverPay() {
        val company = Piece(PieceKind.COMPANY, mapOf("setup" to cycle))
        val wage = Piece(PieceKind.HOURLY_RULES, mapOf("wage" to 10_030L, "five" to true))
        val r = Recipe("민지의 근무 설정", styles = setOf(WorkStyle.SHIFT, WorkStyle.HOURLY), pieces = listOf(shiftLook, alarms, company, wage))
        val back = ShareKit.readKit(ShareKit.kitText(r))!!
        assertEquals(listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS, PieceKind.COMPANY), back.pieces.map { it.kind })
        // A 근무 주기 with its pay part stays home, on the way out and on the way in.
        val withPay = r.copy(pieces = listOf(shiftLook, Piece(PieceKind.COMPANY, mapOf("setup" to paid))))
        assertEquals(listOf(PieceKind.SHIFT_LOOK), ShareKit.readKit(ShareKit.kitText(withPay))!!.pieces.map { it.kind })
        val sneaked = RecipeCodec.write(withPay)
        assertEquals(listOf(PieceKind.SHIFT_LOOK), ShareKit.readKit(sneaked)!!.pieces.map { it.kind })
        assertTrue(ShareKit.paid(Piece(PieceKind.COMPANY, mapOf("setup" to "{not json"))))
        assertFalse(ShareKit.paid(alarms))
        // Too big or not a recipe: nothing.
        assertNull(ShareKit.readKit("x".repeat(ShareKit.KIT_CHARS + 1)))
        assertNull(ShareKit.readKit("{}"))
        assertNull(ShareKit.readKit(null))
    }

    @Test
    fun eachKindOfWorkGetsWhatFitsIt() {
        assertEquals(listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS, PieceKind.COMPANY), ShareKit.kindsFor(WorkStyle.SHIFT, cycle = true))
        assertEquals(listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS), ShareKit.kindsFor(WorkStyle.DAY, cycle = false))
        assertEquals(listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS), ShareKit.kindsFor(WorkStyle.HOURLY, cycle = true))
        for (s in listOf(WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST)) assertFalse(s.name, ShareKit.kitFits(s))
        assertTrue(ShareKit.kitFits(WorkStyle.SHIFT))
    }

    @Test
    fun theShareServerKnowsTheSameKinds() {
        val js = File("../server/share-worker/src/index.js").readText()
        fun list(name: String) = Regex("export const $name = \\[([^\\]]*)\\]").find(js)!!.groupValues[1]
            .let { Regex("\"([A-Za-z]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() }
        assertEquals(BuiltInThemes.LOOK_KINDS.map { it.key }.toSet(), list("LOOK_KINDS"))
        assertEquals(ShareKit.KINDS.map { it.key }.toSet(), list("KIT_KINDS"))
        assertNotNull(Regex("look: 32 \\* 1024").find(js))
        assertNotNull(Regex("kit: 24 \\* 1024").find(js))
    }
}
