package com.bamnat.app.ui.studio

import com.bamnat.core.layout.PageLayout
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.BlueprintParts

/**
 * [CNV-03][CNV-04] 1.37 캔버스 스튜디오 (사용자 2026-11-04 "예시화면과 상호작용을 하면서 제작"): what a tap on the example picks and what
 * the tool line over it changes, without drawing anything — so it is tested on its own.
 *
 * A pick is a part of the blueprint (`cal.cell.date`) or a card of a page (`card:calendar:todayFlow`). A part's tools write its line in
 * the draft's `parts` for the state (`on`) and the condition (`when`) chosen beside it, the way the 부품 도구 does.
 */
internal object StudioEdit {
    const val CARD = "card:"

    /** One quick tool of the line on top (Canva·PPT 상황 도구 줄). */
    enum class Quick(val label: String) {
        FILL("바탕색"), INK("글자색"), SIZE("글자 크기"), WEIGHT("굵기"), FONT("글꼴"), ALIGN("정렬"),
        LINE("테두리"), CORNER("모서리"), SHADOW("그림자"), PAD("안 여백"), GAP("사이 간격"), ALPHA("투명도"),
    }

    /** Parts that are words (a number, a name, a line): their line starts with the letters. */
    private val TEXT = setOf(
        "app.bar.title", "cal.head.title", "cal.week.day", "cal.cell.date", "cal.cell.memo", "cal.cell.money", "cal.cell.hours",
        "cal.cell.spend", "cal.cell.lunar", "cal.cell.holiday", "cal.foot", "memo.card.title", "memo.card.body", "memo.card.date",
        "memo.board.head", "memo.table.head", "money.amount", "money.day", "pay.total",
    )

    fun isText(id: String): Boolean = id in TEXT

    /** The quick tools for [id], most used first: words start with the letters, a box with its fill. */
    fun tools(id: String): List<Quick> = if (isText(id)) {
        listOf(Quick.INK, Quick.SIZE, Quick.WEIGHT, Quick.FONT, Quick.ALIGN, Quick.FILL, Quick.ALPHA)
    } else buildList {
        add(Quick.FILL); add(Quick.INK); add(Quick.LINE); add(Quick.CORNER); add(Quick.SHADOW); add(Quick.PAD)
        if (BlueprintParts.CHILDREN[id].orEmpty().size > 1) add(Quick.GAP)
        add(Quick.SIZE); add(Quick.FONT); add(Quick.ALPHA)
    }

    /** Whether a part can be hidden from the tool line (the whole app or a whole tab cannot: nothing would be left to tap). */
    fun canHide(id: String): Boolean = id != "app" && '.' in id

    /** The parts from the outermost to [id] (달력 › 날짜 칸 › 날짜 숫자); the app itself is left out unless it is what was picked. */
    fun trail(id: String): List<String> {
        if (id.startsWith(CARD)) return listOf(id)
        val out = ArrayList<String>()
        var p: String? = id
        while (p != null && p !in out) { out += p; p = BlueprintParts.parentOf(p) }
        return out.reversed().filter { it != "app" || id == "app" }
    }

    fun label(id: String, cardLabel: (String, String) -> String? = { _, _ -> null }): String =
        card(id)?.let { (tab, c) -> cardLabel(tab, c) ?: c } ?: BlueprintParts.LABELS[id] ?: id

    fun cardId(tab: String, id: String) = "$CARD$tab:$id"

    /** A card pick's tab and card id, or null for a part. */
    fun card(sel: String?): Pair<String, String>? = sel?.takeIf { it.startsWith(CARD) }?.removePrefix(CARD)?.split(':', limit = 2)
        ?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    /** The line of [id] for [on] and [whenF] in [parts] (empty when there is none yet). */
    fun line(parts: String, id: String, on: String?, whenF: String?): Map<String, Any?> =
        Blueprint.decode(parts).lines.firstOrNull { it["id"] == id && it["on"] == on && it["when"] == whenF } ?: emptyMap()

    /**
     * [parts] with [kv] set on [id]'s line for [on] and [whenF] (a null value takes the field off; a line left with nothing goes), or
     * null when the result would not be a valid blueprint.
     */
    fun set(parts: String, id: String, on: String?, whenF: String?, vararg kv: Pair<String, Any?>): String? {
        val all = Blueprint.decode(parts).lines
        val cur = all.firstOrNull { it["id"] == id && it["on"] == on && it["when"] == whenF } ?: emptyMap()
        val next = (cur + ("id" to id) + (on?.let { mapOf("on" to it) } ?: emptyMap()) + (whenF?.let { mapOf("when" to it) } ?: emptyMap()) + kv)
            .filterValues { it != null }
        val others = all.filterNot { it["id"] == id && it["on"] == on && it["when"] == whenF }
        val lines = if (next.keys.all { it == "id" || it == "on" || it == "when" }) others else others + next
        if (lines.isEmpty()) return ""
        return runCatching { Blueprint(Blueprint.clean(lines)).encode() }.getOrNull()
    }

    /** The conditions [id] already has a line for under [on]. */
    fun conditions(parts: String, id: String, on: String?): List<String> =
        Blueprint.decode(parts).lines.filter { it["id"] == id && it["on"] == on }.mapNotNull { it["when"] as? String }.distinct()

    /** Whether [id] has any line at all (꾸민 부품, ● in the 층 list). */
    fun touched(parts: String, id: String): Boolean = Blueprint.decode(parts).lines.any { it["id"] == id }

    /**
     * [CNV-06] A page's card [id] moved [by] places among [ids] (the page's cards in their default order) in the [line] kept for the page,
     * stepping over cards [seen] says are not on this page; the fixed ones stay. The new line, or [line] when it cannot move.
     */
    fun moveCard(line: String, ids: List<String>, fixed: Set<String>, id: String, by: Int, seen: (String) -> Boolean = { true }): String {
        val placed = PageLayout.decode(line).arrange(ids) { it }
        val movable = placed.filter { it.part !in fixed }.toMutableList()
        val from = movable.indexOfFirst { it.part == id }
        var to = from + by
        while (to in movable.indices && !seen(movable[to].part)) to += by
        if (from < 0 || to !in movable.indices) return line
        movable.add(to, movable.removeAt(from))
        return PageLayout.of(placed.filter { it.part in fixed } + movable) { it }.encode()
    }

    /** [line] with card [id] shown or hidden. */
    fun showCard(line: String, ids: List<String>, id: String, shown: Boolean): String {
        val placed = PageLayout.decode(line).arrange(ids) { it }.map { if (it.part == id) it.copy(shown = shown) else it }
        return PageLayout.of(placed) { it }.encode()
    }

    /** Whether card [id] shows in [line]. */
    fun cardShown(line: String, ids: List<String>, id: String): Boolean =
        PageLayout.decode(line).arrange(ids) { it }.firstOrNull { it.part == id }?.shown != false

    // ---- [CNV-02] 확대 ----

    const val ZOOM_MIN = 0.5f
    const val ZOOM_MAX = 3f

    /** The zoom one step in ([by] = 1) or out (-1) from [now]: 50·75·100·125·150·200·250·300%. */
    fun zoomStep(now: Float, by: Int): Float {
        val steps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)
        return if (by > 0) steps.firstOrNull { it > now + 0.01f } ?: ZOOM_MAX else steps.lastOrNull { it < now - 0.01f } ?: ZOOM_MIN
    }
}
