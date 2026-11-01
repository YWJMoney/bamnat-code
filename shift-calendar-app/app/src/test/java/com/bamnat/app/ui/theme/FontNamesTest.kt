package com.bamnat.app.ui.theme

import com.bamnat.app.recipe.RecipeFonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MAT-06] PC 확인 6차: a paid foundry's font is told on the phone, as the server would turn it away. */
class FontNamesTest {
    private fun bytes(name: String) = javaClass.classLoader!!.getResourceAsStream("font-files/$name")!!.readBytes()

    @Test fun aFontThatNamesAPaidFoundryIsSold() {
        val info = FontNames.read(bytes("sold-test.ttf"))
        assertEquals("Bamnat Sold Test", info.family)
        assertEquals("Sandoll", info.maker)
        assertTrue(FontNames.sold(info))
    }

    @Test fun itsOwnOflWordsLetItThrough() {
        val info = FontNames.Info(maker = "Sandoll", license = "This Font Software is licensed under the SIL Open Font License, Version 1.1")
        assertFalse(FontNames.sold(info))
        assertFalse(FontNames.sold(FontNames.Info(maker = "Google", copyright = "Copyright 2018 The Gaegu Project Authors")))
    }

    @Test fun theLooksPickTheStandIn() {
        assertEquals("hand", FontNames.lookalike(FontNames.Info(panoseFamily = 3)))
        assertEquals("hand", FontNames.lookalike(FontNames.Info(familyClass = 10)))
        assertEquals("serif", FontNames.lookalike(FontNames.Info(familyClass = 1)))
        assertEquals("mono", FontNames.lookalike(FontNames.Info(fixedPitch = true)))
        assertEquals(null, FontNames.lookalike(FontNames.Info()))
        assertEquals("hand", UserFonts.guessFallback("예쁜글씨"))
        // Gaegu says nothing of its looks; the app knows the family as 손글씨.
        assertEquals("hand", UserFonts.lookOf(FontNames.Info(family = "Gaegu"), "Gaegu-Regular"))
        assertEquals(null, UserFonts.lookOf(FontNames.Info(family = "Bamnat Sold Test"), "BamnatSoldTest-Regular"))
    }

    @Test fun theMakerHearsWhyASoldFontStays() {
        val note = RecipeFonts.keptNote(RecipeFonts.Sent(emptyList(), emptyList(), sold = listOf("산돌체")))!!
        assertTrue(note, note.contains("‘산돌체’ 글꼴은 글꼴 안에 유료 글꼴 회사가 적혀 있어 나눌 수 없어요"))
    }

    /** PC 확인 7차: a font without 한글 is told when it goes in. Both cmap forms the phone reads (4, and 12 for fonts past the BMP). */
    @Test fun itTellsAFontWithoutHangul() {
        assertEquals(false, FontNames.hangul(bytes("sold-test.ttf")))
        assertEquals(true, FontNames.hangul(bytes("hangul-test.otf")))
        assertEquals(true, FontNames.hangul(bytes("hangul-test-12.otf")))
        assertEquals(null, FontNames.hangul(ByteArray(40)))
    }

    /** 1.36 [SEC-10] M8: bytes past the last table are left out (the server turns such a file away); a whole font is kept as it is. */
    @Test fun bytesPastTheTablesAreLeftOut() {
        val font = bytes("sold-test.ttf")
        assertTrue(FontNames.end(font)!! >= font.size)
        assertTrue(FontNames.trimmed(font) === font)
        val tail = font + ByteArray(4) + "PK".toByteArray()
        assertTrue(font.contentEquals(FontNames.trimmed(tail).copyOf(font.size)))
        assertEquals(FontNames.end(font)!!.coerceAtMost(tail.size), FontNames.trimmed(tail).size)
        assertTrue(FontNames.trimmed(ByteArray(8)).size == 8)
    }
}
