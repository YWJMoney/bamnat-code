package com.bamnat.core.formula

/**
 * [FXR-04] 1.36 받는 칸을 수식이 읽기: the 받는 사람이 정할 칸 a text names through `칸("이름")` / `칸("이름", 기본값)` (`input(...)`).
 * A recipe keeps an input a formula reads this way even when no field points at it (`{"input":"i1"}`), so the receiver is still asked
 * for it and the formula reads the answer. Every text of a piece is looked at (a `when` line, a `{"fx":…}` value, a rule's 조건, a
 * message with `{…}`), so it is a scan of the words rather than a parse; the server's `formulaInputLabels` (recipes.js) is the same.
 */
object FormulaInputs {
    private const val Q = "\"“”'‘’"
    private val CALL = Regex("(?<![\\p{L}\\p{N}_])(?:칸|[iI][nN][pP][uU][tT])\\s*\\(\\s*[$Q]([^$Q]*)[$Q]")

    /** The 칸 names [text] reads, as written (trimmed). */
    fun labels(text: String): Set<String> =
        if ('(' !in text) emptySet() else CALL.findAll(text).mapNotNullTo(LinkedHashSet()) { it.groupValues[1].trim().takeIf(String::isNotEmpty) }

    /** The 칸 names read anywhere in [v] (a piece's values: maps, lists, texts and formula values). */
    fun labelsIn(v: Any?, into: MutableSet<String> = LinkedHashSet()): Set<String> {
        when (v) {
            is String -> into += labels(v)
            is com.bamnat.core.recipe.Ref.Formula -> into += labels(v.src)
            is Map<*, *> -> v.values.forEach { labelsIn(it, into) }
            is List<*> -> v.forEach { labelsIn(it, into) }
        }
        return into
    }
}
