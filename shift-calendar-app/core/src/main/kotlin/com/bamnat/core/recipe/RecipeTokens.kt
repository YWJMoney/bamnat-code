package com.bamnat.core.recipe

import com.bamnat.core.layout.Contrast

/**
 * [RCX-18] 1.35 테마 색 (설계서 4-3, KWGT 전역처럼): the colours a recipe writes become up to [RecipeCodec.MAX_TOKENS] named
 * colours, and each colour field points at one, so the maker changes 야간's colour once and every place that uses it follows.
 * The same colour in two places is one token. [unbind] puts the colours back.
 */
object RecipeTokens {
    private val LABELS = mapOf("today" to "오늘", "sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일")

    /** Every colour written in [r]'s pieces as a token; colours already tokens stay. */
    fun bind(r: Recipe): Recipe {
        val tokens = r.tokens.toMutableList()
        val byColor = LinkedHashMap<Long, String>()
        tokens.forEach { byColor.putIfAbsent(it.light, it.name) }
        fun tokenFor(c: Long, hint: String): Ref.Token? {
            byColor[c]?.let { return Ref.Token(it) }
            if (tokens.size >= RecipeCodec.MAX_TOKENS) return null
            val base = hint.trim().ifEmpty { "색" }.take(RecipeCodec.TOKEN_NAME)
            var name = base
            var n = 2
            while (tokens.any { it.name == name }) name = (base.take(RecipeCodec.TOKEN_NAME - 2) + " $n").also { n++ }
            tokens += Token(name, c, Contrast.onBlack(c))
            byColor[c] = name
            return Ref.Token(name)
        }
        val pieces = r.pieces.map { p -> p.copy(values = map(p.kind.fields, p.values, null) { c, hint -> tokenFor(c, hint) ?: c }) }
        return r.copy(tokens = tokens, pieces = pieces)
    }

    /** The colours back in place of the tokens, and no tokens. */
    fun unbind(r: Recipe): Recipe = r.copy(tokens = emptyList(), pieces = r.pieces.map { p ->
        p.copy(values = mapRefs(p.kind.fields, p.values) { t -> r.token(t.name)?.light })
    })

    /** [name]'s colours; the dark one follows the light one unless given. */
    fun set(r: Recipe, name: String, light: Long, dark: Long = Contrast.onBlack(light)): Recipe =
        r.copy(tokens = r.tokens.map { if (it.name == name) it.copy(light = light, dark = dark) else it })

    /** How many colour fields point at [name]. */
    fun uses(r: Recipe, name: String): Int {
        var n = 0
        r.pieces.forEach { p -> mapRefs(p.kind.fields, p.values) { t -> if (t.name == name) n++; t } }
        return n
    }

    // ---- walking the colour fields ----

    @Suppress("UNCHECKED_CAST")
    private fun map(fields: List<Field>, values: Map<String, Any?>, itemName: String?, f: (Long, String) -> Any): Map<String, Any?> =
        values.mapValues { (k, v) ->
            when (val field = fields.firstOrNull { it.key == k }) {
                is Field.Color -> if (v is Long) f(v, itemName ?: LABELS[k] ?: k) else v
                is Field.Items -> (v as? List<Any?>)?.map { item ->
                    (item as? Map<String, Any?>)?.let { m -> map(field.fields, m, (m["name"] as? String) ?: (m["role"] as? String)) { c, h -> f(c, h) } } ?: item
                } ?: v
                else -> v
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun mapRefs(fields: List<Field>, values: Map<String, Any?>, f: (Ref.Token) -> Any?): Map<String, Any?> =
        values.mapValues { (k, v) ->
            when (val field = fields.firstOrNull { it.key == k }) {
                is Field.Color -> if (v is Ref.Token) f(v) else v
                is Field.Items -> (v as? List<Any?>)?.map { item -> (item as? Map<String, Any?>)?.let { mapRefs(field.fields, it, f) } ?: item } ?: v
                else -> v
            }
        }
}
