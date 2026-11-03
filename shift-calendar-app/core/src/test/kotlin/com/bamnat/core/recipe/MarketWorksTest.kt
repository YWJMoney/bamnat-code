package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MKS-02] 1.36 장터 출시 작품 (docs/plans/1.36-장터-출시작품-기획.md 5장 3항): every work in `docs/recipes/market-136.json` (made by
 * docs/recipes/market-136/make/, checked by the server with tools/market-seed.mjs) is read by the phone as the server reads it, comes back the
 * same when written again, and has no 올리기 전 검사 error. Warnings are allowed (a contrast note on a dark screen is the maker's choice).
 */
class MarketWorksTest {
    private val file = File("../docs/recipes/market-136.json")

    @Test fun everyWorkReadsOnThePhone() {
        if (!file.exists()) return
        @Suppress("UNCHECKED_CAST")
        val works = Json.parse(file.readText()) as List<Map<String, Any?>>
        assertTrue("작품이 190개 넘게 있어야 해요: ${works.size}", works.size >= 190)
        val titles = HashSet<String>()
        for (w in works) {
            val r = RecipeCodec.clean(w["recipe"])
            assertTrue("같은 제목: ${r.title}", titles.add(r.title))
            val again = RecipeCodec.read(RecipeCodec.write(r))
            assertEquals(r.title, RecipeCodec.write(r), RecipeCodec.write(again))
            val errors = RecipeLint.check(again).filter { it.level == RecipeLint.Level.ERROR }
            assertTrue("${r.title}: ${errors.map { it.text }}", errors.isEmpty())
        }
    }
}
