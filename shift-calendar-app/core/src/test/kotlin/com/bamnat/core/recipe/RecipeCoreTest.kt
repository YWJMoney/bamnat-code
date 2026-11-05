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
        assertEquals("too_deep", refused("[[[[[[[[[1]]]]]]]]]"))
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

    /** fc374a23 PC 확인: 가계부 틀's 예산·봉투 목표 inside lists become the receiver's fields too, by the item's name. */
    @Test fun amountsInListsBecomeTheReceiversFields() {
        val ledger = Recipe(
            title = "자취 첫 달", styles = setOf(WorkStyle.SHIFT),
            pieces = listOf(Piece(PieceKind.LEDGER, mapOf(
                "costs" to listOf("야식", "식비"), "incomes" to listOf("급여"),
                "budgets" to listOf(mapOf("category" to "식비", "amount" to 300_000L)),
                "envelopes" to listOf(mapOf("name" to "여행", "target" to 1_000_000L, "emoji" to "✈️")),
            ))),
        )
        assertEquals(listOf("식비 예산", "여행 목표"), InputExtract.found(ledger).map { it.label })
        val r = InputExtract.extract(ledger)
        assertEquals(listOf("식비 예산", "여행 목표"), r.inputs.map { it.label })
        assertFalse(r.inputs.any { it.required })
        assertTrue(InputExtract.found(r).isEmpty())
        val text = RecipeCodec.write(r)
        assertFalse(text, text.contains("\"amount\":300000") || text.contains("\"target\":1000000"))
        // It reads back (the codec takes an input inside a list), and an empty answer leaves the amount out.
        val back = RecipeCodec.read(text)
        assertEquals(2, back.inputs.size)
        @Suppress("UNCHECKED_CAST")
        val budgets = RecipeResolve.piece(back, back.pieces[0], emptyMap())["budgets"] as List<Map<String, Any?>>
        assertEquals(mapOf("category" to "식비"), budgets.single())
        assertEquals("ledger.budgets.0.amount", InputExtract.keyOf(r, "i1"))
        val kept = InputExtract.keep(r, "i1", 300_000L)
        @Suppress("UNCHECKED_CAST")
        assertEquals(300_000L, (kept.pieces[0].values["budgets"] as List<Map<String, Any?>>)[0]["amount"])
        assertEquals(listOf("여행 목표"), InputExtract.extract(ledger, keep = setOf("ledger.budgets.0.amount")).inputs.map { it.label })
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
        // 1.35: a shop or a place before the title is who the recipe is for, not a person.
        val shops = hourly(null).copy(desc = "소매점 사장님, 편의점 점장님, 공장 반장님, 카페 사장님께 좋아요")
        assertEquals(emptyList<String>(), RecipeLint.nameLike(shops))
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

    /** [STU-25] An empty 틀 채우기 and the studio's starting one-liner are not the maker's words: both stay a warning. */
    @Test fun emptyTemplateAndStarterWordsAreNotADescription() {
        val base = InputExtract.extract(hourly(12_000L))
        val lines = RecipeLint.check(base.copy(summary = "내가 꾸민 모양", desc = "누구에게 좋아요: \n적용하면 바뀌는 것: \n주의할 점: "))
        assertEquals(RecipeLint.Level.WARN, lines.first { it.code == "summary" }.level)
        // PC 확인 20ab0254: ‘내가 꾸민 모양’은, not 는.
        assertTrue(lines.first { it.code == "summary" }.text.contains("‘내가 꾸민 모양’은 처음 넣어 둔 말"))
        assertEquals("는", RecipeLint.topic("테마"))
        assertEquals("은", RecipeLint.topic("모양"))
        assertEquals(RecipeLint.Level.WARN, lines.first { it.code == "desc" }.level)
        val filled = RecipeLint.check(base.copy(summary = "주휴 계산 맞게", desc = "누구에게 좋아요: 편의점 알바를 처음 하는 분\n주의할 점: "))
        assertEquals(RecipeLint.Level.OK, filled.first { it.code == "desc" }.level)
    }
}
