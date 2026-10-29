package com.bamnat.core.recipe

/**
 * [RCP-24][RCP-25] A piece as it goes on this phone: each 받는 사람이 정할 칸 ([Ref.Input]) replaced by the receiver's answer (or
 * the maker's default), each theme token ([Ref.Token]) by its light colour. A field left empty (no answer, no default) is
 * dropped, which every adapter reads as "leave mine as it is".
 */
object RecipeResolve {
    /** The answers for [r]'s inputs: [given] where the receiver filled one, else the default; empty answers are left out. */
    fun answers(r: Recipe, given: Map<String, Any?>): Map<String, Any> = buildMap {
        for (i in r.inputs) {
            val v = if (given.containsKey(i.id)) given[i.id] else i.default
            if (v != null && v != "") put(i.id, v)
        }
    }

    fun piece(r: Recipe, p: Piece, answers: Map<String, Any>): Map<String, Any?> = resolve(r, p.values, answers) as Map<String, Any?>

    private fun resolve(r: Recipe, v: Any?, answers: Map<String, Any>): Any? = when (v) {
        is Ref.Input -> answers[v.id]
        is Ref.Token -> r.token(v.name)?.light
        is Map<*, *> -> buildMap<String, Any?> {
            for ((k, x) in v) { val y = resolve(r, x, answers); if (y != null) put(k.toString(), y) }
        }
        is List<*> -> v.mapNotNull { resolve(r, it, answers) }
        else -> v
    }

    /** The inputs a piece of [r] reads, for 입력 칸 채우기 next to the piece's switch. */
    fun inputsOf(r: Recipe, p: Piece): List<Input> {
        val ids = LinkedHashSet<String>()
        fun walk(v: Any?) {
            when (v) {
                is Ref.Input -> ids += v.id
                is Map<*, *> -> v.values.forEach(::walk)
                is List<*> -> v.forEach(::walk)
            }
        }
        walk(p.values)
        return ids.mapNotNull { r.input(it) }
    }
}
