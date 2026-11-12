package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeResolve
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 2.0.0 [RVS-12] (갈래 R SD-07 장터 사진 대조): a work with one {"fx"} 수식 in its 부품 줄 put on none of its parts, without a word, since
 * 1.36 (RUL-02): 넣기 cleaned the parts again after RecipeResolve had read the formula, the cleaner threw on it and the throw was swallowed.
 * Every market work with a formula in its parts now puts them on (the studio's 고쳐 만들기 goes the same way, through onDraft).
 */
@RunWith(AndroidJUnit4::class)
class PartsFormulaPutInTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    private fun market(name: String): List<Recipe> {
        val a = org.json.JSONArray(File("../docs/recipes/$name.json").readText())
        return (0 until a.length()).mapNotNull { i -> runCatching { RecipeCodec.read(a.getJSONObject(i).getJSONObject("recipe").toString()) }.getOrNull() }
    }

    private fun Recipe.fxParts() = pieces.firstOrNull { it.kind == PieceKind.PARTS && RecipeCodec.write(copy(pieces = listOf(it))).contains("\"fx\"") }

    @Test fun everyMarketWorkWithAFormulaInItsPartsPutsThemOn() = runBlocking {
        val works = listOf("market-136", "market-138-studio", "market-139").flatMap(::market).filter { it.fxParts() != null }
        assertTrue("수식이 든 부품 작품: ${works.size}", works.size > 100)
        val plain = c.settingsRepository.current
        val missed = works.filter { r ->
            val values = RecipeResolve.piece(r, r.fxParts()!!, RecipeResolve.answers(r, emptyMap()))
            c.recipes.pieces.onDraft(plain, PieceKind.PARTS, values).parts.let { it == plain.parts || "\"fx\"" !in it }
        }.map { it.title }
        assertEquals("부품이 안 들어가는 작품", emptyList<String>(), missed)
    }

    @Test fun puttingOneOnChangesTheCalendarAndMissesNothing() = runBlocking {
        val r = market("market-136").first { it.title == "아침저녁 다른 오늘 칸" }
        val id = c.recipes.apply(r, "FXPARTS1", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.PARTS, RecipePieces.Mode.REPLACE)), emptyMap())
        assertTrue("넣은 기록", id != null)
        assertTrue(c.settingsRepository.current.parts, "\"fx\"" in c.settingsRepository.current.parts)
        assertEquals(emptyList<PieceKind>(), c.recipes.lastMissed)
    }
}
