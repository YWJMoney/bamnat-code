package com.bamnat.app.ui.recipe

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.company.CompanyException
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MakerSet
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/**
 * 2.0.0 [RVM-51] 검토 MKT-22: '프로필과 작품 지우기'가 지우는 도중 인터넷이 끊겨 작품 일부가 남아도 "프로필과 작품을 지웠어요"라고 했어요.
 * 이제 지우지 못한 것을 세어 말해요.
 */
@RunWith(AndroidJUnit4::class)
class ProfileClearSaysWhatStayedTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun settle(until: () -> Boolean) = repeat(80) { if (!until()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(20) } }

    private fun item(code: String) = RecipeItem(code, "작품 $code", "", setOf(WorkStyle.SHIFT), com.bamnat.core.recipe.Cover(), "나", 1, likes = 0, dislikes = 0, users = 0,
        kept = 0, official = false, publicData = false, parent = null, at = Instant.parse("2026-11-09T00:00:00Z"), showcase = false, scope = "theme")

    /** My page with five works and a 모음; deleting [failing] works fails (the connection dropped). */
    private inner class Mine(private val failing: Int) : RecipeApi {
        val deleted = ArrayList<String>()
        override val enabled = true
        override suspend fun maker(device: String?, id: String) = MakerPage(id, "나", "🦉", null, mine = true,
            recipes = (1..5).map { item("ABCD000$it") }.filter { it.code !in deleted }, likes = 0, users = 0, followers = 0, following = false,
            badges = emptyList(), sets = listOf(MakerSet("s1", "야간 모음", "", emptyList())))
        override suspend fun delete(device: String, code: String) {
            if (code.last().digitToInt() <= failing) throw CompanyException("offline")
            deleted += code
        }
        override suspend fun makerMe(device: String, face: String?, color: Long?, set: Boolean) = "나"
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(emptyList(), null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = throw CompanyException("not_found")
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, "", "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, "", "public", scope ?: "public")
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun inbox(device: String, since: Instant?) = null
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
        override suspend fun monthMakers(style: WorkStyle?) = emptyList<com.bamnat.app.recipe.MakerFaceItem>()
    }

    private fun clear(api: Mine): String? {
        c.recipeApi = api
        val vm = MakerViewModel(c, "me")
        settle { vm.state.value.page != null }
        vm.clearProfile(works = true)
        settle { vm.state.value.message != null }
        return vm.state.value.message
    }

    @Test fun whatCouldNotBeDeletedIsSaid() {
        assertEquals("작품 2개는 지우지 못해 장터에 남아 있어요. 인터넷을 확인하고 한 번 더 지워 주세요", clear(Mine(failing = 2)))
    }

    @Test fun allDeletedSaysSo() {
        assertEquals("프로필과 작품을 지웠어요", clear(Mine(failing = 0)))
    }
}
