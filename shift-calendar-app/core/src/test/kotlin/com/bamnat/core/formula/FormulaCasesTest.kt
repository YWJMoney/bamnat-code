package com.bamnat.core.formula

import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RUL-01] The formula cases the phone and the server share (`docs/formula-cases.json`): each formula reads or is refused with the
 * same code at the same letter, and runs to the same value. BAMNAT_WRITE_FORMULA_CASES=1 writes "want" from core.
 */
class FormulaCasesTest {
    private val file = File("../docs/formula-cases.json")

    @Suppress("UNCHECKED_CAST")
    @Test
    fun theSharedCasesHold() {
        val all = Json.parse(file.readText()) as Map<String, Any?>
        val scope = FormulaScope.of(all["vars"] as Map<String, Any?>, all["inputs"] as Map<String, Any?>)
        val cases = (all["cases"] as List<Map<String, Any?>>).map { c ->
            val src = c["src"] as String
            val want: Map<String, Any?> = when (val p = Formula.parse(src)) {
                is Parsed.Bad -> mapOf("ok" to false, "code" to p.error.code, "from" to p.error.from.toLong())
                is Parsed.Ok -> p.formula.run(scope).let { v ->
                    if (v == null) mapOf("ok" to true, "fails" to true) else mapOf("ok" to true, "type" to type(v), "text" to v.text, "truthy" to v.truthy)
                }
            }
            c to want
        }
        if (System.getenv("BAMNAT_WRITE_FORMULA_CASES") == "1") {
            val b = StringBuilder("{\n")
            b.append("\"_note\":").append(RecipeCodec.canonical(all["_note"])).append(",\n")
            b.append("\"vars\":").append(RecipeCodec.canonical(all["vars"])).append(",\n")
            b.append("\"inputs\":").append(RecipeCodec.canonical(all["inputs"])).append(",\n")
            b.append("\"cases\":[\n")
            cases.forEachIndexed { i, (c, w) ->
                b.append(" ").append(RecipeCodec.canonical(mapOf("src" to c["src"], "want" to w))).append(if (i < cases.size - 1) ",\n" else "\n")
            }
            b.append("]\n}\n")
            file.writeText(b.toString())
            return
        }
        for ((c, w) in cases) assertEquals(c["src"].toString(), c["want"], w)
    }

    private fun type(v: Value) = when (v) {
        is Value.Num -> "num"; is Value.Text -> "text"; is Value.Bool -> "bool"; is Value.Many -> "many"; Value.None -> "none"
    }
}
