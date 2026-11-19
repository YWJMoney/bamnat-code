package com.bamnat.app.ui.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.core.work.WorkStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/**
 * 2.0.0 [RVM-63] 검토 MKT-01·MKT-02·U08a-02: a work the server moved up between two pages comes once, and a next page that fails is
 * said at the end of the list (moreError) while the works already shown stay, not as the whole list's error at the top.
 */
@RunWith(AndroidJUnit4::class)
class MarketPagingTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private lateinit var before: RecipeApi
    private var failNext = false

    private fun item(code: String) = RecipeItem(code, "작품 $code", "", setOf(WorkStyle.SHIFT), com.bamnat.core.recipe.Recipe("x", styles = setOf(WorkStyle.SHIFT), pieces = emptyList()).cover, "밤낮", 1, likes = 1, dislikes = 0, users = 1,
        kept = 0, official = false, publicData = false, parent = null, at = Instant.parse("2026-11-09T00:00:00Z"), scope = "theme")

    @Before fun fake() {
        before = c.recipeApi
        c.recipeApi = object : RecipeApi by before {
            override val enabled = true
            override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?): RecipePage = when (cursor) {
                0 -> RecipePage(listOf(item("AAAA2345"), item("BBBB2345"), item("CCCC2345")), 3)
                // The server moved CCCC up between the two reads, so the second page starts with it again.
                3 -> if (failNext) throw java.io.IOException("down") else RecipePage(listOf(item("CCCC2345"), item("DDDD2345")), 5)
                else -> throw java.io.IOException("down")
            }
        }
    }

    @After fun end() { c.recipeApi = before }

    private fun RecipeListViewModel.settled() {
        repeat(200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (!state.value.loading) return
            Thread.sleep(5)
        }
    }

    @Test fun aWorkMovedUpBetweenPagesComesOnce() {
        val vm = RecipeListViewModel(c).also { it.settled() }
        assertEquals(3, vm.state.value.items.size)
        vm.load(more = true); vm.settled()
        assertEquals(listOf("AAAA2345", "BBBB2345", "CCCC2345", "DDDD2345"), vm.state.value.items.map { it.code })
    }

    @Test fun aNextPageThatFailsIsSaidAtTheEnd() {
        failNext = true
        val vm = RecipeListViewModel(c).also { it.settled() }
        vm.load(more = true); vm.settled()
        assertNotNull("다음 쪽을 못 읽은 것은 목록 끝에서 말해요", vm.state.value.moreError)
        assertNull("맨 위 오류(목록 전체)는 아니에요", vm.state.value.error)
        assertEquals("읽은 작품은 그대로예요", 3, vm.state.value.items.size)
        failNext = false
        vm.load(more = true); vm.settled()
        assertNull(vm.state.value.moreError)
        assertEquals(4, vm.state.value.items.size)
    }
}
