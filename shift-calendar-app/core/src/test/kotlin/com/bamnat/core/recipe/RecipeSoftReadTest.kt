package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 2.0.0 [RVS-05] 받는 쪽 읽기 (검토 SD-03·RCP-07·U09b-12): a work from a newer app opens here with what this app knows of it, and says
 * what it left out, where [RecipeCodec.read] refuses it whole. Soft reading never keeps less than [RecipeCodec.read] and never lets in
 * what a maker could not send.
 */
class RecipeSoftReadTest {
    private fun refused(code: String, block: () -> Unit) {
        try {
            block()
            fail("expected $code")
        } catch (e: RecipeException) {
            assertEquals(code, e.code)
        }
    }

    /** Every case the phone and the server share reads the same either way: a work [RecipeCodec.read] takes is the same work. */
    @Suppress("UNCHECKED_CAST")
    @Test fun aWorkThisAppReadsIsTheSameWork() {
        val cases = (Json.parse(File("../docs/recipe-cases.json").readText()) as Map<String, Any?>)["read"] as List<Map<String, Any?>>
        var whole = 0
        for (c in cases) {
            val text = RecipeCodec.canonical(c["in"])
            val strict = try { RecipeCodec.read(text) } catch (e: RecipeException) { null } ?: continue
            val soft = RecipeCodec.readSoft(text)
            assertEquals(c["name"].toString(), RecipeCodec.write(strict), RecipeCodec.write(soft.recipe))
            whole++
        }
        assertTrue("cases read whole: $whole", whole > 30)
    }

    /** A newer app's work: its new part, new state, new field, new motion and new kind of piece are left out, and named. */
    @Suppress("UNCHECKED_CAST")
    @Test fun aNewerWorkKeepsWhatThisAppKnows() {
        val text = """{"format":"bamnat-recipe","v":1,"title":"새 판 작품","styles":["SHIFT"],"pieces":[
            {"kind":"parts","glow":3,"parts":[{"id":"cal.cell","fill":4294940928},{"id":"cal.future","fill":4280295456},
              {"id":"cal.cell","on":"brandnew","fill":4280295456},{"id":"cal.cell","on":"today","fill":4280295456,"blur":2}]},
            {"kind":"appFeel","motion":"tn:warp","accent":4280295456},
            {"kind":"hologram","x":1},
            {"kind":"memoLook","font":"nanumpen"}]}"""
        refused("pick") { RecipeCodec.read(text) }
        val soft = RecipeCodec.readSoft(text)
        assertEquals(
            listOf("pieces.0.glow", "pieces.0.parts.1", "pieces.0.parts.2", "pieces.0.parts.3.blur", "pieces.1.motion", "pieces.2"),
            soft.skipped.sorted(),
        )
        assertEquals(6, soft.left)
        val r = soft.recipe
        assertEquals(listOf(PieceKind.PARTS, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK), r.pieces.map { it.kind })
        val lines = r.pieces[0].values["parts"] as List<Map<String, Any?>>
        // The line for a state this app does not know is gone, never drawn as the line for every day.
        assertEquals(listOf("cal.cell" to null, "cal.cell" to "today"), lines.map { it["id"] to it["on"] })
        assertEquals(4294940928L, lines[0]["fill"])
        assertEquals(4280295456L, r.pieces[1].values["accent"])
        assertEquals(null, r.pieces[1].values["motion"])
        // What is left is a work this app reads whole, and writes as it always does.
        assertEquals(RecipeCodec.write(r), RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(r))))
    }

    /** A list that loses every line it must have takes its piece with it, said once (not each line). */
    @Test fun aPieceThatLosesAllItMustHaveGoes() {
        val text = """{"format":"bamnat-recipe","v":1,"title":"새 부품만","styles":["SHIFT"],"pieces":[
            {"kind":"parts","parts":[{"id":"cal.future","fill":4294940928},{"id":"cal.other","fill":4294940928}]},
            {"kind":"appFeel","accent":4280295456}]}"""
        val soft = RecipeCodec.readSoft(text)
        assertEquals(listOf("pieces.0"), soft.skipped)
        assertEquals(listOf(PieceKind.APP_FEEL), soft.recipe.pieces.map { it.kind })
    }

    /** Soft reading is not loose: what a maker could not send is still refused, and a work with nothing this app knows does not open. */
    @Test fun softIsNotLoose() {
        refused("pieces") { RecipeCodec.readSoft("""{"format":"bamnat-recipe","v":1,"title":"전부 새것","styles":["SHIFT"],"pieces":[{"kind":"hologram"}]}""") }
        refused("update") { RecipeCodec.readSoft("""{"format":"bamnat-recipe","v":${Recipe.VERSION + 1},"title":"다음 형식","styles":["SHIFT"],"pieces":[]}""") }
        refused("too_big") { RecipeCodec.readSoft("{\"title\":\"" + "가".repeat(RecipeCodec.MAX_BYTES) + "\"}") }
        refused("too_deep") { RecipeCodec.readSoft("[".repeat(RecipeCodec.MAX_DEPTH + 1) + "]".repeat(RecipeCodec.MAX_DEPTH + 1)) }
        refused("text") { RecipeCodec.readSoft("""{"format":"bamnat-recipe","v":1,"title":"","styles":["SHIFT"],"pieces":[{"kind":"appFeel","accent":4280295456}]}""") }
        // A piece's field its own check refuses is left out and named; the rest of the piece is kept.
        val bad = RecipeCodec.readSoft("""{"format":"bamnat-recipe","v":1,"title":"글자","styles":["SHIFT"],"pieces":[
            {"kind":"appFeel","accent":4280295456,"dayOrder":"모르는 순서"}]}""")
        assertEquals(listOf("pieces.0.dayOrder"), bad.skipped)
    }

    /**
     * RCP-07: what came is checked by the hash of its text, as the server names it (sha256 of the body as served), so a field this app
     * drops on reading no longer reads as '오는 길에 바뀌었어요'. For a work this app reads whole the two checks agree.
     */
    @Suppress("UNCHECKED_CAST")
    @Test fun theTextsHashIsTheWorksHash() {
        val cases = (Json.parse(File("../docs/recipe-cases.json").readText()) as Map<String, Any?>)["read"] as List<Map<String, Any?>>
        for (c in cases) {
            val want = c["want"] as Map<String, Any?>
            if (want["ok"] != true) continue
            val body = want["canonical"] as String
            assertEquals(c["name"].toString(), want["hash"], RecipeCodec.sha256(body))
            assertEquals(c["name"].toString(), RecipeCodec.hash(RecipeCodec.read(body)), RecipeCodec.sha256(body))
        }
    }
}
