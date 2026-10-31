package com.bamnat.core.recipe

/**
 * [LIN-02] 1.35 달라진 점 (사용자 2026-10-30 "내꺼랑 어떤점이 달라졌는지"): what a 수정판 changed from the recipe it was made from, piece
 * by piece and field by field, in the app's words. The same paths as [Merge3] (named items matched by name or role), so the
 * maker's '내 판에 가져오기' takes exactly the fields shown here.
 */
object RecipeDiff {
    enum class Kind { ADDED, REMOVED, CHANGED }

    /** One field: its [path] for [Merge3.apply], its words, and the two values said in words ([before] null when new). */
    data class Line(val path: String, val label: String, val before: String?, val after: String?)

    /** One piece: added, taken away or changed, with its field lines (empty for a piece added or taken away whole). */
    data class Piece(val kind: PieceKind, val change: Kind, val lines: List<Line>)

    /** What [after] changed from [before]; the title, cover and words are not pieces and are left to the screen. */
    fun of(before: Recipe, after: Recipe): List<Piece> {
        val out = ArrayList<Piece>()
        val kinds = (before.pieces.map { it.kind } + after.pieces.map { it.kind }).distinct()
        for (k in kinds) {
            val b = before.pieces.firstOrNull { it.kind == k }
            val a = after.pieces.firstOrNull { it.kind == k }
            when {
                b == null && a != null -> out += Piece(k, Kind.ADDED, lines(k, emptyMap(), a.values))
                a == null && b != null -> out += Piece(k, Kind.REMOVED, emptyList())
                a != null && b != null -> lines(k, b.values, a.values).takeIf { it.isNotEmpty() }?.let { out += Piece(k, Kind.CHANGED, it) }
            }
        }
        return out
    }

    /** The paths [after] changed (for [Merge3.apply] with every line picked). */
    fun paths(p: Piece): Set<String> = p.lines.map { it.path }.toSet()

    private fun lines(kind: PieceKind, before: Map<String, Any?>, after: Map<String, Any?>): List<Line> {
        val b = Merge3.flat(before)
        val a = Merge3.flat(after)
        return (b.keys + a.keys).distinct().filter { p -> !same(b[p], a[p]) }.map { p -> Line(p, label(kind, p), b[p]?.let { say(it, leaf(p)) }, a[p]?.let { say(it, leaf(p)) }) }
    }

    private fun same(x: Any?, y: Any?) = x == y || (x != null && y != null && RecipeCodec.canonical(x) == RecipeCodec.canonical(y))

    /** "items[name=야간].color" → "목록 ‘야간’ 색". */
    fun label(kind: PieceKind?, path: String): String = path.split('.').joinToString(" ") { part ->
        val m = ITEM.matchEntire(part)
        if (m != null) "${RecipeWords.key(kind, m.groupValues[1])} ‘${m.groupValues[3].let { RecipeWords.PICKS[it] ?: it }}’"
        else RecipeWords.key(kind, part)
    }

    private fun leaf(path: String) = path.substringAfterLast('.').substringBefore('[')

    private val COLOURS = setOf("color", "today", "sat", "sun", "holiday", "accent")

    private val ITEM = Regex("^(\\w+)\\[(\\w+)=(.+)]$")

    /** A value in words: yes/no, a colour as #RRGGBB, a choice by its word, a list joined, a 받는 분 칸 as such. */
    fun say(v: Any?, key: String = ""): String = when (v) {
        null -> ""
        is Boolean -> if (v) "켬" else "끔"
        is Ref.Input -> "받는 분이 정해요"
        is Ref.Token -> "테마 색"
        is Map<*, *> -> when {
            v.containsKey("input") -> "받는 분이 정해요"
            v.containsKey("token") -> "테마 색 ${v["token"]}"
            else -> v.values.joinToString(" · ") { say(it) }
        }
        is List<*> -> v.joinToString(", ") { say(it) }.ifEmpty { "없음" }
        is Long -> if (key in COLOURS) "#" + (v and 0xFFFFFF).toString(16).uppercase().padStart(6, '0') else v.toString()
        is String -> RecipeWords.PICKS[v] ?: v
        else -> v.toString()
    }
}
