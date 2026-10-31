package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RCP-03] The cases the phone and the server share (`docs/recipe-cases.json`): each recipe read the same way (the same written
 * form and hash, or the same refusal), the same name skeletons and the same ranking numbers. With BAMNAT_WRITE_RECIPE_CASES=1
 * this writes the expected answers ("want") from core; the server's test then holds `cleanRecipe()` to them.
 */
class RecipeCasesTest {
    private val file = File("../docs/recipe-cases.json")

    @Suppress("UNCHECKED_CAST")
    private fun read(): Map<String, Any?> = Json.parse(file.readText()) as Map<String, Any?>

    private fun answerRead(input: Any?): Map<String, Any?> = try {
        val text = RecipeCodec.canonical(input)
        val r = RecipeCodec.read(text)
        mapOf("ok" to true, "canonical" to RecipeCodec.write(r), "hash" to RecipeCodec.hash(r))
    } catch (e: RecipeException) {
        mapOf("ok" to false, "code" to e.code)
    }

    private fun r6(d: Double?): Any? = d?.let { Math.round(it * 1_000_000) }

    @Suppress("UNCHECKED_CAST")
    private fun answers(all: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap(all)
        out["read"] = (all["read"] as List<Map<String, Any?>>).map { it + ("want" to answerRead(it["in"])) }
        out["skeleton"] = (all["skeleton"] as List<Map<String, Any?>>).map {
            val n = it["name"] as String
            it + ("want" to mapOf("skeleton" to NameSkeleton.of(n), "reserved" to NameSkeleton.reserved(n)))
        }
        out["mixed"] = (all["mixed"] as List<Map<String, Any?>>).map { it + ("want" to NameSkeleton.mixedScript(it["text"] as String)) }
        out["rank"] = (all["rank"] as List<Map<String, Any?>>).map {
            val l = (it["likes"] as Long).toDouble()
            val d = (it["dislikes"] as Long).toDouble()
            val a = it["applied"] as Long
            val k = it["kept7"] as Long
            val h = (it["hours"] as Long).toDouble()
            it + ("want" to mapOf("good" to r6(Rank.good(l, d, a, k)), "hot" to r6(Rank.hot(l, a, h)), "kept" to Rank.keptPercent(a, k)?.toLong()).filterValues { v -> v != null })
        }
        return out
    }

    @Test
    fun theSharedCasesHold() {
        val all = read()
        val got = answers(all)
        if (System.getenv("BAMNAT_WRITE_RECIPE_CASES") == "1") {
            val b = StringBuilder("{\n")
            got.entries.forEachIndexed { i, (k, v) ->
                b.append(RecipeCodec.canonical(k)).append(":")
                if (v is List<*>) {
                    b.append("[\n")
                    v.forEachIndexed { j, x -> b.append(" ").append(RecipeCodec.canonical(x)).append(if (j < v.size - 1) ",\n" else "\n") }
                    b.append("]")
                } else b.append(RecipeCodec.canonical(v))
                b.append(if (i < got.size - 1) ",\n" else "\n")
            }
            file.writeText(b.append("}\n").toString())
            return
        }
        for (section in listOf("read", "skeleton", "mixed", "rank")) {
            @Suppress("UNCHECKED_CAST")
            val want = all[section] as List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val have = got[section] as List<Map<String, Any?>>
            want.zip(have).forEach { (w, h) -> assertEquals("${section}: ${w["name"] ?: w["text"] ?: w}", w["want"], h["want"]) }
        }
    }
}
