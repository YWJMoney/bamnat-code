package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FREE-09] 1.35 추천 테마: eight for each kind of work, looks only, every one read back whole and passing 올리기 전 검사 with no warning
 * (글자 대비도). With BAMNAT_WRITE_STARTERS=1 this writes `docs/recipes/theme-recipes.json` for the operator's 보여주기.
 */
class BuiltInThemesTest {
    @Test fun themesAreLooksOnlyAndPass() {
        val all = BuiltInThemes.all
        for (s in WorkStyle.entries) assertEquals("$s 테마", 8, BuiltInThemes.of(s).size)
        for (r in all) {
            val read = RecipeCodec.read(RecipeCodec.write(r))
            assertEquals("${r.title}: 조각이 다 읽혀요", r.pieces.size, read.pieces.size)
            assertEquals(r.title, RecipeCodec.write(read), RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(read))))
            assertTrue("${r.title}: 모양 조각만", BuiltInThemes.isTheme(read))
            val lines = RecipeLint.check(read)
            assertTrue("${r.title}: ${lines.filter { it.level != RecipeLint.Level.OK }.map { it.text }}", lines.all { it.level == RecipeLint.Level.OK })
            val view = read.pieces.first { it.kind == PieceKind.CAL_VIEW }.values
            assertEquals(null, CellLook.looksProblem(view["looks"] as String))
            assertEquals(null, CellPattern.problem(view["pattern"] as String))
        }
        assertEquals("제목이 겹치지 않아요", all.size, all.map { NameSkeleton.of(it.title) }.toSet().size)
        if (System.getenv("BAMNAT_WRITE_STARTERS") == "1") {
            val out = all.joinToString(",\n", "[\n", "\n]\n") { r -> """{"showcase":true,"recipe":${RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(r)))}}""" }
            File("../docs/recipes").mkdirs()
            File("../docs/recipes/theme-recipes.json").writeText(out)
        }
    }

    @Test fun aRecipeWithAnAlarmIsNotATheme() {
        val r = BuiltInThemes.all.first()
        assertTrue(!BuiltInThemes.isTheme(r.copy(pieces = r.pieces + Piece(PieceKind.ALARMS, mapOf("byRole" to emptyList<Any>())))))
    }

    @Test fun lintCatchesUnreadableScreenText() {
        val bad = Recipe("흐린 글자", "글자가 안 보이는 판", "누구에게 좋아요: 시험용 판이에요", setOf(WorkStyle.SHIFT),
            pieces = listOf(Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to ScreenLooks().with(ScreenLooks.ALL,
                ScreenLook(bg = 0xFFFFFFFF, ink = 0xFFEEEEEE)).encode()))))
        val line = RecipeLint.check(bad).first { it.code == "contrast" }
        assertEquals(RecipeLint.Level.WARN, line.level)
        val fixed = line.fix!!(bad)
        assertEquals(RecipeLint.Level.OK, RecipeLint.check(fixed).first { it.code == "contrast" }.level)
    }
}
