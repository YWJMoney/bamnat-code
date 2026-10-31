package com.bamnat.core.recipe

/**
 * [RCX-03] 1.35 새 판 반영 (설계서 4-3 6번): three values of every field of a piece — what the recipe set when it was put in
 * ([base]), what this phone has now ([mine]) and what the new version says ([theirs]) — and what to do with each.
 *
 * - The new version did not change it → mine stays (nothing to show).
 * - Only the new version changed it → offered, on by default.
 * - Only I changed it → mine stays (nothing to show).
 * - Both changed it, to different values → shown as 부딪힘, mine kept unless the user picks theirs.
 *
 * Lists of named items (근무 꾸미기의 items, 알람의 byRole …) are matched by their name or role, not by place, so a new item in the
 * middle does not look like every item changed. Values are compared as written ([RecipeCodec.canonical]).
 */
object Merge3 {
    enum class Kind { THEIRS, CONFLICT }

    /** One field the new version touches: its [path] ("items[야간].color"), the three values and what it is. */
    data class Field(val path: String, val base: Any?, val mine: Any?, val theirs: Any?, val kind: Kind)

    data class Result(val kind: PieceKind, val fields: List<Field>) {
        val conflicts: Int get() = fields.count { it.kind == Kind.CONFLICT }
    }

    /** Item keys a list is matched by, in order of preference. */
    private val KEYS = listOf("name", "role", "title", "id", "kind")

    fun of(kind: PieceKind, base: Map<String, Any?>?, mine: Map<String, Any?>?, theirs: Map<String, Any?>): Result {
        val b = flat(base.orEmpty())
        val m = flat(mine.orEmpty())
        val t = flat(theirs)
        val out = ArrayList<Field>()
        for (path in (b.keys + t.keys).distinct().sorted()) {
            val bv = b[path]; val mv = if (mine == null) bv else m[path]; val tv = t[path]
            if (same(tv, bv)) continue
            if (same(mv, tv)) continue
            out += Field(path, bv, mv, tv, if (same(mv, bv)) Kind.THEIRS else Kind.CONFLICT)
        }
        return Result(kind, out)
    }

    /**
     * The values to put in: [mine] with each field of [picked] (paths) set to the new version's value. A path whose new value is
     * gone takes the field away. The result is a piece of the new version's shape, so it goes through [PieceCatalog] again.
     */
    fun apply(mine: Map<String, Any?>, theirs: Map<String, Any?>, picked: Set<String>): Map<String, Any?> {
        val m = flat(mine).toMutableMap()
        val t = flat(theirs)
        for (p in picked) { val v = t[p]; if (v == null) m.remove(p) else m[p] = v }
        return unflat(m, theirs)
    }

    // ---- flattening ----

    private fun same(a: Any?, b: Any?): Boolean = a == b || (a != null && b != null && RecipeCodec.canonical(a) == RecipeCodec.canonical(b))

    private fun keyOf(item: Map<*, *>): String? = KEYS.firstNotNullOfOrNull { k -> (item[k] as? String)?.takeIf { it.isNotEmpty() }?.let { "$k=$it" } }

    /** Every leaf by its path; a list of named items by `field[key=value]`, any other list as one value. */
    fun flat(values: Map<String, Any?>, prefix: String = ""): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in values) {
            val p = if (prefix.isEmpty()) k else "$prefix.$k"
            when {
                v is Map<*, *> && !isRef(v) -> @Suppress("UNCHECKED_CAST") out.putAll(flat(v as Map<String, Any?>, p))
                v is List<*> && v.isNotEmpty() && v.all { it is Map<*, *> && keyOf(it) != null } &&
                    v.map { keyOf(it as Map<*, *>) }.distinct().size == v.size -> v.forEach { item ->
                    @Suppress("UNCHECKED_CAST") val m = item as Map<String, Any?>
                    out.putAll(flat(m, "$p[${keyOf(m)}]"))
                }
                else -> out[p] = v
            }
        }
        return out
    }

    private fun isRef(m: Map<*, *>) = m.size == 1 && (m.containsKey("input") || m.containsKey("token"))

    /** Back from paths, in the shape of [shape] (the new version): lists of named items come back as lists, in its order. */
    @Suppress("UNCHECKED_CAST")
    private fun unflat(paths: Map<String, Any?>, shape: Map<String, Any?>, prefix: String = ""): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = LinkedHashSet<String>()
        shape.keys.forEach { keys += it }
        paths.keys.filter { prefix.isEmpty() || it.startsWith("$prefix.") }.forEach { full ->
            val rest = if (prefix.isEmpty()) full else full.removePrefix("$prefix.")
            keys += rest.substringBefore('.').substringBefore('[')
        }
        for (k in keys) {
            val p = if (prefix.isEmpty()) k else "$prefix.$k"
            if (paths.containsKey(p)) { out[k] = paths[p]; continue }
            val s = shape[k]
            val under = paths.keys.filter { it.startsWith("$p.") || it.startsWith("$p[") }
            if (under.isEmpty()) continue
            if (paths.keys.any { it.startsWith("$p[") }) {
                // A list of named items: the new version's order first, then mine that it does not have.
                val names = LinkedHashSet<String>()
                (s as? List<*>)?.forEach { (it as? Map<*, *>)?.let(::keyOf)?.let(names::add) }
                under.filter { it.startsWith("$p[") }.forEach { names += it.removePrefix("$p[").substringBefore(']') }
                out[k] = names.mapNotNull { n ->
                    val sub = (s as? List<*>)?.firstOrNull { (it as? Map<*, *>)?.let(::keyOf) == n } as? Map<String, Any?> ?: emptyMap()
                    unflat(paths, sub, "$p[$n]").takeIf { it.isNotEmpty() }
                }
            } else out[k] = unflat(paths, s as? Map<String, Any?> ?: emptyMap(), p)
        }
        return out
    }
}
