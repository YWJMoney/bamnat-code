package com.bamnat.app.screenshots

import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.getAllSemanticsNodes
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.MainActivity
import com.bamnat.app.ui.recipe.MarketFonts
import com.bamnat.app.ui.recipe.MarketShots
import com.bamnat.app.ui.recipe.ShotPhone
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.39 [MKP-06] 장터 카드 글꼴: the letters the example phone shows ([ShotPhone]) must be in [MarketFonts.CHARS], so a card's small font
 * (only those letters) draws every word in the work's own font. Draws a work of each kind of work on every face and reads the words.
 * `-Pbamnat.marketChars=all` draws every 출시 작품 and writes the letters to `build/market-chars.txt` (to make CHARS again).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MarketCharsTest : ScreenshotBase() {
    private val faces = listOf(MarketShots.Face.Tab(TabOrder.CALENDAR), MarketShots.Face.Tab(TabOrder.MONEY), MarketShots.Face.Tab(TabOrder.MEMOS),
        MarketShots.Face.Pay, MarketShots.Face.Widget, MarketShots.Face.Alarm, MarketShots.Face.Rules)

    private fun rootOf(v: View): ViewRootForTest? {
        if (v is ViewRootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) rootOf(v.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun theExamplePhonesLettersAreInTheCardFont() {
        val all = System.getProperty("bamnat.marketChars") == "all"
        val works = JSONArray(File("../docs/recipes/market-139.json").readText())
        val picked = (0 until works.length()).map { works.getJSONObject(it).getJSONObject("recipe").toString() }
            .let { if (all) it else it.filterIndexed { i, _ -> i % 12 == 0 } }
        seed()
        val seen = sortedSetOf<Char>()
        RuntimeEnvironment.setQualifiers("w400dp-h900dp-xxhdpi")
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            for ((n, body) in picked.withIndex()) {
                if (all && n % 10 == 0) File("build/market-chars.progress").writeText("$n/${picked.size} ${seen.size}")
                val r = runCatching { RecipeCodec.read(body) }.getOrNull() ?: continue
                val styles = if (all) r.styles.ifEmpty { WorkStyle.entries.toSet() }.toList() else listOf(r.styles.firstOrNull() ?: WorkStyle.SHIFT)
                for (style in styles) for (face in faces) {
                    scenario.onActivity { a -> a.setContent { BamnatTheme { Box(Modifier.size(MarketShots.PHONE_W.dp, MarketShots.PHONE_H.dp)) { ShotPhone(face, r, style, 1f) } } } }
                    settle()
                    scenario.onActivity { a ->
                        rootOf(a.window.decorView)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = false)?.forEach { n ->
                            (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).forEach { t -> t.forEach { seen += it } }
                        }
                    }
                }
            }
        }
        val words = seen.filter { MarketFonts.drawn(it) }.joinToString("")
        if (all) File("build/market-chars.txt").writeText(words)
        val missing = words.filter { it !in MarketFonts.CHARS }
        assertTrue("MarketFonts.CHARS에 없는 글자: $missing", missing.isEmpty())
    }
}
