package com.bamnat.core.recipe

/**
 * 2.0.0 [RVM-23] RCP-17·STU-19 (보이지 않는 꾸밈 단추, 칸 전체를 덮는 꾸밈 단추가 날짜 누르기를 가로채 받은 단추를 누름): which
 * decorations may take a tap as a 규칙 단추. One that may not stays a picture (its [DecorItem.tap] is dropped when a layer is read,
 * [clean]); the studio's 누르면 and 올리기 점검 ([RecipeLint]) say so first, and the server refuses it (decor.js `decorTapProblem`).
 *
 * A decoration takes a tap only when one can see it ([seen]: not hidden, at least [SEEN] % strong, a body one can see), when it is
 * not a whole-screen picture ([DecorItem.full]), when it is button-sized (at most [MAX_W] × [MAX_H] dp: 장터의 머리 단추는 104 × 26),
 * and at most [PER_PLACE] on one part. On the phone it also takes at most half the part's place (the app's `pressRect`).
 * The server's decor.js `DECOR_TAP` holds the same numbers and `decorSeen`·`decorTapProblem` the same rule (ReceivedSafetyTest reads it).
 */
object DecorTapRule {
    /** How strong (%) a decoration must be drawn, all told, to take a tap. */
    const val SEEN = 40
    const val MAX_W = 160
    const val MAX_H = 64
    const val PER_PLACE = 3

    /** Shapes drawn as lines (the app's DecorShapes.STROKES, the same list). */
    val STROKES = setOf("ring", "frame", "line_h", "line_v", "line_d", "dash_h", "zigzag", "squiggle", "check", "underline", "circle_mark", "snow")

    private fun alphaOf(c: Long?): Double = if (c == null) 1.0 else ((c ushr 24) and 0xFF) / 255.0

    /** [i] as drawn can be seen: not hidden, and what it draws at least [SEEN] % strong. */
    fun seen(i: DecorItem): Boolean {
        if (i.hide) return false
        val a = i.alpha / 100.0
        val enough = SEEN / 100.0
        if (a < enough) return false
        val fill = alphaOf(i.fill) * i.fillA / 100.0 * a
        val line = i.line != null && i.line != "none" && i.lineW >= 1.0 && alphaOf(i.lineC) * a >= enough
        return when (i.kind) {
            DecorKind.SHAPE -> if (i.glyph in STROKES) (if (i.lineC != null) alphaOf(i.lineC) * a else fill) >= enough else fill >= enough || line
            DecorKind.ICON -> alphaOf(i.ink ?: i.fill) * a >= enough
            DecorKind.TEXT -> alphaOf(i.ink) * a >= enough || (i.fill != null && fill >= enough) || line
            DecorKind.EMOJI, DecorKind.PHOTO -> true
        }
    }

    /** [i] names a 규칙 단추 and may take a tap for it (apart from how many are on its part). */
    fun canTap(i: DecorItem): Boolean = i.tap != null && !i.full && i.w <= MAX_W && i.h <= MAX_H && seen(i)

    /** Why [i] may not take its tap, in the maker's words; null when it may (or names none). */
    fun why(i: DecorItem): String? = when {
        i.tap == null -> null
        i.full -> "화면 가득 그림은 누르는 단추가 될 수 없어요"
        i.w > MAX_W || i.h > MAX_H -> "누르는 꾸밈은 ${MAX_W}×${MAX_H}dp까지예요"
        !seen(i) -> "보이지 않는 꾸밈은 누르는 단추가 될 수 없어요(투명도 ${SEEN}% 이상)"
        else -> null
    }

    /** The decorations of [items] that take a tap: those that may, the first [PER_PLACE] on each part. */
    fun taps(items: List<DecorItem>): List<DecorItem> {
        val n = HashMap<String, Int>()
        return items.filter { canTap(it) && (n.merge(it.at, 1, Int::plus) ?: 0) <= PER_PLACE }
    }

    /**
     * A 꾸밈 층 piece's [values] with the tap of every decoration that may not take one taken off (the decoration stays a picture),
     * item by item as written, so nothing else of the piece changes (올리기 점검's 고치기).
     */
    fun cleanValues(values: Map<String, Any?>): Map<String, Any?> {
        val raw = values["items"] as? List<*> ?: return values
        val n = HashMap<String, Int>()
        var changed = false
        val items = raw.map { m ->
            val map = m as? Map<*, *> ?: return@map m
            if (map["tap"] == null) return@map m
            val item = Decor.of(mapOf("items" to listOf(map))).items.firstOrNull()
            val ok = item != null && canTap(item) && (n.merge(item.at, 1, Int::plus) ?: 0) <= PER_PLACE
            if (ok) m else { changed = true; map.filterKeys { it != "tap" } }
        }
        return if (changed) values + ("items" to items) else values
    }

    /** How many of [items]' 꾸밈 단추 press nothing, and the first reason in the maker's words; null when every one presses. */
    fun problem(items: List<DecorItem>): Pair<Int, String>? {
        val ok = taps(items).toSet()
        val bad = items.filter { it.tap != null && it !in ok }
        if (bad.isEmpty()) return null
        return bad.size to (bad.firstNotNullOfOrNull { why(it) } ?: "한 곳에 누르는 꾸밈은 ${PER_PLACE}개까지예요")
    }

    /** [d] with the tap of every decoration that may not take one dropped (it stays a picture). */
    fun clean(d: Decor): Decor {
        val ok = taps(d.items).toSet()
        return if (d.items.all { it.tap == null || it in ok }) d else Decor(d.items.map { if (it.tap != null && it !in ok) it.copy(tap = null) else it })
    }
}
