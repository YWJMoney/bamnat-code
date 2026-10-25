package com.bamnat.app.ui.guide

import com.bamnat.core.guide.FeatureRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [PAYX-48] '사용하러 가기'·'바로 가기'·'해 보기' never land on nothing (U9-나): every feature whose way in names a card or a
 * tool inside a screen ([com.bamnat.core.guide.FeatureRoute.focus], not a settings row) has a screen that takes that key
 * ([FocusedTool] or [FeatureLinks.take]), so the tap opens the card and not just the top of the tab.
 */
class FeatureDestinationsTest {
    private val sources: String by lazy {
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    private fun takes(key: String): Boolean =
        Regex("""FocusedTool\(\s*"${Regex.escape(key)}"""").containsMatchIn(sources) ||
            Regex("""take\(\s*"${Regex.escape(key)}"\s*\)""").containsMatchIn(sources) ||
            // A screen that takes a family of keys by their start (invest.*).
            Regex("""startsWith\("${Regex.escape(key.substringBefore('.'))}\."\)""").containsMatchIn(sources)

    @Test
    fun everyCardKeyHasAScreenThatTakesIt() {
        val missing = FeatureRegistry.all.filter { f -> f.route.focus != null && !f.route.place.isSettings && !takes(f.route.focus!!) }
        assertTrue("받는 화면이 없는 바로 가기: " + missing.joinToString { "${it.id}(${it.route.focus})" }, missing.isEmpty())
    }

    @Test
    fun payGapOpensItsCard() {
        assertTrue(FeatureRegistry.get("pay.gap")!!.route.focus == "pay.gap")
        assertTrue(takes("pay.gap"))
    }
}
