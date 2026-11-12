package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeException
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * 2.0.0 [RVS-07] 검토 SN-08·SD-04: a draft that grew past what 올리기 takes (조각 17개 이상: the look and every 기능) used to do nothing on
 * 이어서 하기, as if what was being made had gone. It opens now; one that cannot be read is said, and its row is not written over.
 */
@RunWith(AndroidJUnit4::class)
class StudioBigDraftTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun until(ok: () -> Boolean) {
        repeat(60) { if (!ok()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }
    }

    @Test fun aDraftWithEveryPieceOpens() {
        val all = WorkStyle.entries.toSet()
        fun fits(p: Piece) = runCatching { RecipeCodec.read(RecipeCodec.write(Recipe("한 조각", styles = all, pieces = listOf(p)))) }.isSuccess
        // One piece of every kind the market's works use (22 kinds in market-136·138), each as a work carries it.
        val market = java.io.File("../docs/recipes").listFiles { f -> f.name.matches(Regex("market-[0-9a-z-]+\\.json")) && !f.name.endsWith("-plan.json") }.orEmpty().sortedBy { it.name }
            .flatMap { f ->
                val a = org.json.JSONArray(f.readText())
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.optJSONObject("recipe")?.let { runCatching { RecipeCodec.read(it.toString()) }.getOrNull() } }
            }
        val pieces = market.flatMap { it.pieces }.filter(::fits).distinctBy { it.kind }.sortedBy { it.kind.ordinal }
        val extras = pieces.filter { it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS && it.kind != com.bamnat.core.recipe.PieceKind.BUNDLE }
        assertTrue("조각이 ${RecipeCodec.MAX_PIECES}개보다 많아야 해요: ${pieces.size}", pieces.size > RecipeCodec.MAX_PIECES)
        val body = RecipeCodec.write(Recipe("모든 조각을 담은 초안", styles = all, pieces = pieces))
        // What it was: 올리기 reading refuses it, and 이어서 하기 used only that.
        try { RecipeCodec.read(body); fail("올리기 읽기는 한도를 지켜요") } catch (e: RecipeException) { assertEquals("pieces", e.code) }
        val id = runBlocking { c.recipes.saveDraft(0, body, 1, null, "public") }

        val vm = StudioViewModel(c)
        idle()
        vm.resume(id)
        until { vm.state.value.draftId == id }
        assertEquals("이어서 하기로 열려요", id, vm.state.value.draftId)
        assertEquals(extras.map { it.kind }.toSet(), vm.state.value.extras.keys)
        assertEquals("모든 조각을 담은 초안", vm.state.value.origin.title)
    }

    @Test fun aDraftThatCannotBeReadIsSaidAndKept() {
        val id = runBlocking { c.recipes.saveDraft(0, "{\"format\":\"bamnat-recipe\",\"v\":1,\"title\":", 1, null, "public") }
        val vm = StudioViewModel(c)
        idle()
        vm.resume(id)
        until { vm.state.value.said != null }
        assertTrue(vm.state.value.said.orEmpty(), vm.state.value.said.orEmpty().contains("열지 못했어요"))
        assertEquals("열지 못한 초안 위에 새로 적지 않아요", 0L, vm.state.value.draftId)
        assertTrue(runBlocking { c.database.recipeDao().drafts().any { it.id == id } })
    }
}
