package com.bamnat.app.recipe

import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.ui.recipe.MarketDisk
import com.bamnat.app.ui.recipe.MarketFonts
import com.bamnat.app.ui.theme.AppFonts
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.39 [MKP-06][MKP-07] 장터 로딩 2차 (사용자 2026-11-08 "아직도 장터 로딩이 너무 느려"): a card names its fonts as card fonts and never
 * starts a whole font coming down; a list is kept on the phone and read back as it came.
 */
@RunWith(AndroidJUnit4::class)
class MarketSpeedTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()

    @Test fun aWorksOpenFontsAreFoundInItsPartsScreensAndDates() {
        val body = """{"pieces":[{"kind":"parts","parts":[{"id":"app","font":"montserrat"},{"id":"app.card","font":"jua"},{"id":"app.bar","font":"sans"}]},""" +
            """{"kind":"screenLook","screens":"all~bg:FF0D1B2A|ft:gaegu"},{"kind":"calView","looks":"mk:fill|ff:notoserifkr/bg:FF14233A"},""" +
            """{"kind":"memoLook","font":"u0123456789abcdef"},{"kind":"note","text":"font: jua 처럼 쓴 글"}]}"""
        // The phone's own fonts (sans) and a user's own font (u + 16) are not fonts a card brings down.
        assertEquals(setOf("montserrat", "jua", "gaegu", "notoserifkr"), MarketFonts.ids(body))
        assertEquals(emptySet<String>(), MarketFonts.ids("""{"pieces":[{"kind":"calTheme","today":4284790262}]}"""))
    }

    @Test fun aCardsPartsReadTheCardFontAndNothingElseDoes() {
        val small = FontFamily.Serif
        val lines = MarketFonts.lines(listOf(mapOf("id" to "app", "font" to "jua"), mapOf("id" to "app.card", "font" to "montserrat"), mapOf("id" to "app.bar")),
            mapOf("jua" to small))
        assertEquals("card:jua", lines[0]["font"])
        // A font the card could not have stays as it was (its closest phone font is drawn).
        assertEquals("montserrat", lines[1]["font"])
        assertNull(lines[2]["font"])
        AppFonts.cards["card:jua"] = small
        try {
            assertEquals(small, AppFonts.family(app, "card:jua"))
            // Outside a card, the same font is never the small one.
            assertTrue(AppFonts.family(app, "jua") != small)
            // A card font not made: the font as anywhere else.
            assertEquals(AppFonts.family(app, "montserrat"), AppFonts.family(app, "card:montserrat"))
        } finally { AppFonts.cards.clear() }
    }

    @Test fun aListIsKeptOnThePhoneAndReadBackWithItsWorks() = runBlocking {
        val item = JSONObject().put("code", "ABCD1234").put("title", "한밤의 유리").put("summary", "").put("styles", JSONArray().put("SHIFT"))
            .put("version", 3).put("at", "2026-11-08T07:00:00Z").put("hash", "h1").put("body", """{"pieces":[]}""")
        val raw = JSONObject().put("items", JSONArray().put(item)).put("next", 30).toString()
        val page = RecipeClient.pageOf(JSONObject(raw))
        assertEquals(30, page.next)
        assertEquals("h1" to """{"pieces":[]}""", page.bodies["ABCD1234@3"])
        assertNull(MarketDisk.list(app, "SHIFT|hot||theme"))
        MarketDisk.keepList(app, "SHIFT|hot||theme", raw)
        val kept = MarketDisk.list(app, "SHIFT|hot||theme")
        assertNotNull(kept)
        val (back, at) = kept!!
        assertEquals(listOf("ABCD1234"), back.items.map { it.code })
        assertEquals("한밤의 유리", back.items.single().title)
        assertEquals(page.bodies, back.bodies)
        assertTrue(at > 0)
        // Another 구간 is its own list.
        assertNull(MarketDisk.list(app, "SHIFT|new||theme"))
    }

    @Test fun aCardIsKeptOnlyWhenItsFontsWereThere() {
        val body = """{"pieces":[{"kind":"parts","parts":[{"id":"app","font":"jua"}]}]}"""
        assertFalse(MarketDisk.complete(app, body))
        assertTrue(MarketDisk.complete(app, """{"pieces":[{"kind":"parts","parts":[{"id":"app","font":"sans"}]}]}"""))
    }
}
