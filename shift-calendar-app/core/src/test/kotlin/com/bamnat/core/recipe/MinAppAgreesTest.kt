package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-18] 작품 호환 (검토 SI-04·SD-03 ④): the studio's '작품 크기·호환' chip ([RecipeNeeds]) says what the server's partsMinApp
 * will, for every work kept in docs/recipes (the 장터 batches and the edge works E1~E12). The cases are written for the server's own test
 * (`server/company-worker/test/rvs18-min-app-agrees.test.mjs`), which reads the same files; this fails while they are old:
 *
 *     BAMNAT_WRITE_MIN_APP=1 ./gradlew -p core test --tests '*MinAppAgreesTest*' --rerun
 */
class MinAppAgreesTest {
    private val docs = File("../docs/recipes")
    private val cases = File("../server/company-worker/test/fixtures/min-app-cases.json")

    /**
     * The batches already made (a new batch another 갈래 adds does not make this old; add its folder here when it should be held too) and
     * the edge works.
     */
    private val held = listOf("edge-200", "market-136", "market-138", "market-138-refresh", "market-138-studio", "market-139")

    /** Each kept work: its file under docs/recipes, its place in that file (-1: the file is one work) and the recipe as JSON. */
    private fun works(): List<Triple<String, Int, Any?>> =
        held.asSequence().flatMap { File(docs, it).walkTopDown() }.filter { it.isFile && it.extension == "json" && it.parentFile.name != "make" }
            .sortedBy { it.relativeTo(docs).invariantSeparatorsPath }
            .flatMap { f ->
                val rel = f.relativeTo(docs).invariantSeparatorsPath
                when (val v = runCatching { Json.parse(f.readText()) }.getOrNull()) {
                    is Map<*, *> -> if (v["format"] == "bamnat-recipe") listOf(Triple(rel, -1, v)) else emptyList()
                    is List<*> -> v.withIndex().mapNotNull { (i, x) -> (x as? Map<*, *>)?.get("recipe")?.let { Triple(rel, i, it) } }
                    else -> emptyList()
                }
            }.toList()

    @Test fun theServerReadsTheSameNeed() {
        val all = works()
        assertTrue("docs/recipes has works", all.size > 100)
        val want = buildString {
            appendLine("[")
            all.forEachIndexed { i, (file, at, r) ->
                val need = RecipeNeeds.ofJson(r).app.takeIf { it > RecipeNeeds.OLDEST }
                append("  {\"file\": ${RecipeCodec.canonical(file)}, \"at\": $at, \"need\": ${need ?: "null"}}")
                appendLine(if (i < all.size - 1) "," else "")
            }
            appendLine("]")
        }
        if (System.getenv("BAMNAT_WRITE_MIN_APP") == "1") cases.writeText(want)
        assertEquals("${cases.path} is old: BAMNAT_WRITE_MIN_APP=1 ./gradlew -p core test --tests '*MinAppAgreesTest*' --rerun",
            want, if (cases.exists()) cases.readText() else "")
    }

    /** E8 (밤 화면) needs 1.39, and says so with the part's name; a plain 근무 꾸미기 opens on every app. */
    @Test fun theChipSaysWhichAppAndWhy() {
        val e8 = RecipeCodec.read(File(docs, "edge-200/E08-밤-화면.json").readText())
        val n = RecipeNeeds.of(e8)
        assertTrue("E8 ${n.app}", n.app >= 53)
        assertTrue(n.line, n.line.startsWith("${RecipeNeeds.nameOf(n.app - 1)} 이하 폰에선"))
        assertTrue(n.whyWords().toString(), n.whyWords().isNotEmpty() && n.whyWords().none { it.isBlank() })
        val plain = Recipe("작품", styles = com.bamnat.core.work.WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.SHIFT_LOOK, mapOf("items" to emptyList<Any>()))))
        val p = RecipeNeeds.of(plain)
        assertTrue(p.everyApp)
        assertEquals("모든 판", p.chip)
        assertEquals("1.36.0부터 모든 폰에서 열려요", p.line)
    }

    /** Every app the table names has a name people know. */
    @Test fun everyCodeHasAName() {
        for (m in PartsSinceTable.MAP.values) for (v in m.values) assertTrue("$v", v in RecipeNeeds.NAMES)
    }
}
