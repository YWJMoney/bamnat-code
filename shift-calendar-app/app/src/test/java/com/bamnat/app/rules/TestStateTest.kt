package com.bamnat.app.rules

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.GuideStore
import com.bamnat.app.ui.guide.OnceTipSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * 2.0.0 [RVR-44] (총괄 HQ-123) Each test starts from nothing an earlier test left ([TestState]).
 *
 * a_ and b_ run in this order in one JVM (names, [FixMethodOrder]): what a_ leaves, b_ must not find. The two rules below
 * keep it so for what is added later: a store read from a [android.content.Context] is one per app, and every
 * resetForTest/clearForTest the app has is called from [TestState.clear].
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestStateTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun a_leavesATipAndAStoreBehind() {
        assertTrue("[TestState.Plugin] ran before this test", TestState.clears > 0)
        clearsSeen = TestState.clears
        storeSeen = GuideStore.get(app)
        assertTrue(OnceTipSlot.claim("a_ 의 안내"))
    }

    @Test fun b_findsNeither() {
        assertTrue("[TestState.Plugin] ran again before this test", TestState.clears > clearsSeen)
        assertTrue("an earlier test's tip is not this run's", OnceTipSlot.claim("b_ 의 안내"))
        storeSeen?.let { assertNotSame("a store of an earlier test's app", it, GuideStore.get(app)) }
    }

    /** A `get(context)` kept in a field reads the first app's settings for ever: use [com.bamnat.app.data.PerApp]. */
    @Test fun storesFromAContextAreOnePerApp() {
        val kept = Regex("""fun \w+\((?:context|c|ctx): Context\)[^=]*=\s*\w+\s*\?:\s*synchronized""")
        val found = main.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> "${f.name}:${i + 1}".takeIf { kept.containsMatchIn(l) && "takeIf" !in l } } }
        assertEquals("one per app ([PerApp] or takeIf { it.app === context.applicationContext })", emptyList<String>(), found)
    }

    /** An object's resetForTest that [TestState.clear] does not call leaves its state to the next test. */
    @Test fun everyTestResetIsCalledBeforeEachTest() {
        val calls = listOf("TestState.kt", "ComposeMainReset.kt").joinToString("\n") { File(rules, it).readText() }
        val hook = Regex("""fun (resetForTest|clearForTest)\(""")
        val owner = Regex("""^\s*(?:internal |private )?(?:data )?object (\w+)""")
        val missing = main.flatMap { f ->
            var name: String? = null
            f.readLines().mapNotNull { l ->
                owner.find(l)?.let { name = it.groupValues[1] }
                hook.find(l)?.let { h -> name?.let { "$it.${h.groupValues[1]}" }?.takeIf { it !in calls } }
            }
        }
        assertEquals("call these in TestState.clear", emptyList<String>(), missing)
    }

    private companion object {
        var clearsSeen = 0
        var storeSeen: GuideStore? = null
        // Gradle runs the app's tests in app/ (as AppDialogsTest and NumberTextGuardTest read src/main).
        val main = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val rules = File("src/test/java/com/bamnat/app/rules")
    }
}
