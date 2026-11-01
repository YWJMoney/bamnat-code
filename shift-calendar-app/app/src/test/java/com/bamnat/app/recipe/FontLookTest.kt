package com.bamnat.app.recipe

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** 1.36 [SEC-10] 보안 점검 H3: a received font is looked at in the isolated process first; a first font waits, and the maker hears so. */
@RunWith(RobolectricTestRunner::class)
class FontLookTest {
    @After fun clear() { FontProbe.look = null }

    @Test fun aFontTheLookTurnsAwayIsNotAskedForAgain() = runBlocking {
        var asked = 0
        FontProbe.look = { _, _ -> asked++; false }
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(FontProbe.safe(ctx, "u00000000000000aa", File("x")))
        assertTrue(FontProbe.failed("u00000000000000aa"))
        assertFalse(FontProbe.safe(ctx, "u00000000000000aa", File("x")))
        assertEquals(1, asked)
        FontProbe.look = { _, _ -> true }
        assertTrue(FontProbe.safe(ctx, "u00000000000000bb", File("x")))
        assertFalse(FontProbe.failed("u00000000000000bb"))
    }

    @Test fun theMakerHearsThatAFirstFontWaitsForALook() {
        assertNull(RecipeFonts.keptNote(RecipeFonts.Sent(listOf("붓글씨"), emptyList())))
        val note = RecipeFonts.keptNote(RecipeFonts.Sent(listOf("붓글씨"), listOf("상용 글꼴"), waiting = listOf("붓글씨")))!!
        assertTrue(note, note.contains("‘상용 글꼴’ 글꼴은 공개 글꼴로 밝히지 않았거나"))
        assertTrue(note, note.contains("‘붓글씨’ 글꼴은 처음 나누는 글꼴이라 운영자가 라이선스를 확인한 뒤"))
        assertEquals(2, note.lines().size)
    }
}
