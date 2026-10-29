package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** [RCP-01][RCP-05][RCP-06][RCP-18][RCP-40] 1.34 나눔 판의 core: 읽기 한도, 입력 칸 추출, 검사, 이모지. */
class RecipeCoreTest {
    private fun hourly(wage: Any?) = Recipe(
        title = "편의점 알바", styles = setOf(WorkStyle.HOURLY),
        pieces = listOf(Piece(PieceKind.HOURLY_RULES, buildMap { wage?.let { put("wage", it) }; put("weeklyPay", true) })),
    )

    private fun refused(text: String): String = try { RecipeCodec.read(text); fail("read"); "" } catch (e: RecipeException) { e.code }

    @Test fun aBigOrDeepRecipeIsRefusedBeforeItIsRead() {
        val big = RecipeCodec.write(hourly(null)).replace("\"title\"", "\"x\":\"${"가".repeat(12_000)}\",\"title\"")
        assertEquals("too_big", refused(big))
        assertEquals("too_deep", refused("[[[[[[[1]]]]]]]"))
        assertEquals("json", refused("{\"format\":\"bamnat-recipe\",}"))
    }

    @Test fun amountsBecomeTheReceiversFields() {
        val r = InputExtract.extract(hourly(12_000L)) { k, f -> if (k == PieceKind.HOURLY_RULES && f == "wage") 10_320L else null }
        assertEquals(Ref.Input("i1"), r.pieces[0].values["wage"])
        assertEquals(10_320L, r.inputs.single().default)
        assertTrue(InputExtract.found(r).isEmpty())
        // The written recipe carries no 12,000 anywhere.
        assertFalse(RecipeCodec.write(r).contains("12000"))
        val kept = InputExtract.keep(r, "i1", 12_000L)
        assertEquals(12_000L, kept.pieces[0].values["wage"])
        assertTrue(kept.inputs.isEmpty())
    }

    @Test fun theLintSaysWhatToFix() {
        val r = hourly(12_000L).copy(title = "밤낮 공식 알바")
        val lines = RecipeLint.check(r)
        assertFalse(RecipeLint.ready(lines))
        val amount = lines.first { it.code == "amount" }
        assertNotNull(amount.fix)
        assertTrue(InputExtract.found(amount.fix!!(r)).isEmpty())
        val ok = RecipeLint.check(InputExtract.extract(hourly(12_000L)).copy(summary = "주휴 계산 맞게", desc = "편의점 알바를 처음 하는 분께 좋아요"))
        assertTrue(ok.joinToString { it.code + it.level }, RecipeLint.ready(ok))
    }

    @Test fun aPaleSundayIsDarkenedToRead() {
        val r = hourly(null).copy(
            styles = setOf(WorkStyle.HOURLY),
            pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFFFFC0C0L))),
        )
        val line = RecipeLint.check(r).first { it.code == "contrast" }
        assertEquals(RecipeLint.Level.WARN, line.level)
        val fixed = line.fix!!(r)
        assertEquals(RecipeLint.Level.OK, RecipeLint.check(fixed).first { it.code == "contrast" }.level)
    }

    @Test fun aNameBeforeATitleIsCaught() {
        val r = hourly(null).copy(desc = "김철수 과장님이 알려 준 방법")
        assertEquals(listOf("김철수 과장"), RecipeLint.nameLike(r))
    }

    @Test fun similarRecipesAreFound() {
        val a = hourly(null)
        assertNotNull(RecipeLint.similar(a.copy(title = "편의점  알바"), listOf("편의점 알바" to a)))
        assertNull(RecipeLint.similar(a.copy(title = "카페", pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("cell" to "dot")))), listOf("x" to a)))
    }

    @Test fun emojiAreOneKnownGrapheme() {
        assertEquals("☀️", RecipeText.oneEmoji("☀"))
        assertEquals("👍🏽", RecipeText.oneEmoji("👍🏽"))
        assertNull(RecipeText.oneEmoji("👍👍"))
        assertNull(RecipeText.oneEmoji("가"))
        assertTrue(RecipeText.searchEmoji("달").any { it.emoji == "🌙" })
        assertTrue(RecipeText.emojiGroups.size >= 8)
    }

    @Test fun hiddenCharactersGoButAnEmojisJoinerStays() {
        assertEquals("야간세트", RecipeText.clean("야간\u202E세트\u200B"))
        assertEquals("👩‍🍳 요리", RecipeText.clean("👩\u200D🍳 요리"))
        assertEquals("ab", RecipeText.clean("a\u200Db"))
    }

    @Test fun piecesOutsideTheKindOfWorkFold() {
        val r = Recipe(
            "같이", styles = setOf(WorkStyle.SHIFT, WorkStyle.BUSINESS),
            pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("cell" to "dot")), Piece(PieceKind.SHOP, mapOf("closing" to listOf("마감")))),
        )
        assertEquals(listOf(PieceKind.CAL_THEME), r.piecesFor(WorkStyle.SHIFT).map { it.kind })
        assertEquals(listOf(PieceKind.SHOP), r.piecesNotFor(WorkStyle.SHIFT).map { it.kind })
    }

    @Test fun rolesComeFromNamesThenTimes() {
        assertEquals(ShiftRole.NIGHT, ShiftRole.guess("나이트", false, 22, 600))
        assertEquals(ShiftRole.DAY, ShiftRole.guess("D", false, 7, 480))
        assertEquals(ShiftRole.OFF, ShiftRole.guess("비번", true, null, 0))
        assertEquals(ShiftRole.LONG, ShiftRole.guess("당직", false, 9, 24 * 60))
        assertEquals(ShiftRole.EVENING, ShiftRole.guess("B조", false, 14, 480))
    }
}
