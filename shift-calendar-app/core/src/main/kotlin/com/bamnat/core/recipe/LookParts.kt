package com.bamnat.core.recipe

import com.bamnat.core.layout.TabOrder

/**
 * [TRY-02] 1.35 부분만 담기 (사용자 2026-10-30 21:45 "각 탭별로 전체를 가져올수도 있고 일부분을 가져올수도 … 어느 테마의 어떤 부분이
 * 마음에 드는데 해당 부분만 저장해서 가져오는거지"): a theme's looks cut into the parts a person sees on each tab — the calendar's
 * cells, its pattern, its weekend colours, a screen's colours and background, a page's card order — so 입어 보기 can take the whole,
 * a tab, or a few parts, and 보관함 can keep just one. A part is a subset of one or more look pieces' values; what a part leaves
 * out stays the user's own when it is put on (each look piece already works that way).
 */
object LookParts {
    /** The screens a part belongs to, in the order 입어 보기 lists them. */
    const val WIDGET = "widget"
    val TABS: List<String> = listOf(TabOrder.CALENDAR, TabOrder.MEMOS, TabOrder.MONEY, TabOrder.STATS, TabOrder.PLAZA, TabOrder.SETTINGS, WIDGET)

    fun tabLabel(tab: String): String = when (tab) {
        TabOrder.CALENDAR -> "달력"; TabOrder.MEMOS -> "메모"; TabOrder.MONEY -> "가계부"; TabOrder.STATS -> "통계·급여"
        TabOrder.PLAZA -> "마을"; TabOrder.SETTINGS -> "앱 전체·설정"; WIDGET -> "위젯"; else -> tab
    }

    /** Which values of a piece a part takes. */
    sealed interface Take {
        val kind: PieceKind
        data class Keys(override val kind: PieceKind, val keys: Set<String>? = null) : Take
        /** Some screens of 화면 색·배경 ([ScreenLooks] keys); [pay] also takes 급여 모양. */
        data class Screens(val screens: (String) -> Boolean, val pay: Boolean = false) : Take { override val kind = PieceKind.SCREEN_LOOK }
        /** [BLU-05] 1.36 부품 꾸미기: the blueprint lines of the parts under [roots] (`cal` takes `cal`, `cal.head.title` …). */
        data class Parts(val roots: Set<String>) : Take { override val kind = PieceKind.PARTS }
        /** [LAY-01] The 꾸밈 층's items pinned to the places [at] says yes to. */
        data class Decor(val at: (String) -> Boolean) : Take { override val kind = PieceKind.DECOR }
    }

    data class Part(val id: String, val tab: String, val label: String, val takes: List<Take>)

    private val CELL_VIEW = setOf("size", "badge", "date", "text", "grid", "memo", "order", "hours", "lunar", "spend")

    /** Every part there is, whether a theme has it or not. */
    val ALL: List<Part> = listOf(
        Part("cal.cells", TabOrder.CALENDAR, "칸 디자인", listOf(Take.Keys(PieceKind.CAL_VIEW, setOf("looks", "today")))),
        Part("cal.pattern", TabOrder.CALENDAR, "칸 패턴", listOf(Take.Keys(PieceKind.CAL_VIEW, setOf("pattern")))),
        Part("cal.view", TabOrder.CALENDAR, "칸 높이·표시", listOf(Take.Keys(PieceKind.CAL_VIEW, CELL_VIEW))),
        Part("cal.days", TabOrder.CALENDAR, "오늘·주말 색", listOf(Take.Keys(PieceKind.CAL_THEME))),
        Part("cal.photo", TabOrder.CALENDAR, "달력 그림", listOf(Take.Keys(PieceKind.PHOTO_LOOK, setOf("bg", "dim", "head", "icons")))),
        Part("cal.colors", TabOrder.CALENDAR, "달력 색·배경", listOf(Take.Screens({ it == "calendar" }))),
        Part("cal.cards", TabOrder.CALENDAR, "달력 카드 배치", listOf(Take.Keys(PieceKind.LAYOUT, setOf("calendar")))),
        Part("memo.look", TabOrder.MEMOS, "메모장 모양", listOf(Take.Keys(PieceKind.MEMO_LOOK))),
        Part("memo.colors", TabOrder.MEMOS, "메모 색·배경", listOf(Take.Screens({ it == "memo" }))),
        Part("memo.cards", TabOrder.MEMOS, "메모 카드 배치", listOf(Take.Keys(PieceKind.CARDS, setOf("memo")))),
        Part("money.look", TabOrder.MONEY, "가계부 모양", listOf(Take.Keys(PieceKind.MONEY_LOOK))),
        Part("money.colors", TabOrder.MONEY, "가계부 색·배경", listOf(Take.Screens({ it == "money" }))),
        Part("money.cards", TabOrder.MONEY, "가계부 카드 배치", listOf(Take.Keys(PieceKind.CARDS, setOf("money")))),
        Part("stats.pay", TabOrder.STATS, "급여 모양·색", listOf(Take.Screens({ it.startsWith("pay.") }, pay = true))),
        Part("stats.colors", TabOrder.STATS, "통계 색·배경", listOf(Take.Screens({ it == "stats" }))),
        Part("stats.cards", TabOrder.STATS, "통계 카드 배치", listOf(Take.Keys(PieceKind.CARDS, setOf("stats")))),
        Part("plaza.colors", TabOrder.PLAZA, "마을 배경·이모티콘", listOf(Take.Screens({ it == ScreenLooks.PLAZA }))),
        Part("app.colors", TabOrder.SETTINGS, "앱 전체 색·배경", listOf(Take.Screens({ it in setOf(ScreenLooks.ALL, "settings", ScreenLooks.ALARM, ScreenLooks.GUARDED) }))),
        Part("app.tabs", TabOrder.SETTINGS, "아래 탭 순서", listOf(Take.Keys(PieceKind.LAYOUT, setOf("tabs")))),
        Part("app.corners", TabOrder.SETTINGS, "모서리", listOf(Take.Keys(PieceKind.PHOTO_LOOK, setOf("corners")))),
        Part("widget", WIDGET, "위젯", listOf(Take.Keys(PieceKind.WIDGET), Take.Keys(PieceKind.PHOTO_LOOK, setOf("widget")))),
        // [BLU-05] 1.36 부품 꾸미기, one part per screen, so 바꾼 곳·부분만 담기·처음대로 see them like the rest.
        Part("cal.parts", TabOrder.CALENDAR, "달력 부품", listOf(Take.Parts(setOf("cal")))),
        Part("memo.parts", TabOrder.MEMOS, "메모 부품", listOf(Take.Parts(setOf("memo")))),
        Part("money.parts", TabOrder.MONEY, "가계부 부품", listOf(Take.Parts(setOf("money")))),
        Part("stats.parts", TabOrder.STATS, "급여 부품", listOf(Take.Parts(setOf("pay")))),
        Part("app.parts", TabOrder.SETTINGS, "앱 전체 부품", listOf(Take.Parts(setOf("app")))),
        Part("widget.parts", WIDGET, "위젯 부품", listOf(Take.Parts(setOf("widget")))),
        // [LAY-01] 1.36 꾸밈 층, a part on each tab it is pinned to.
        Part("cal.decor", TabOrder.CALENDAR, "달력 꾸밈", listOf(Take.Decor { it.startsWith("cal.") || it == "bg.calendar" })),
        Part("memo.decor", TabOrder.MEMOS, "메모 꾸밈", listOf(Take.Decor { it == "bg.memo" })),
        Part("money.decor", TabOrder.MONEY, "가계부 꾸밈", listOf(Take.Decor { it == "bg.money" })),
        Part("stats.decor", TabOrder.STATS, "통계·급여 꾸밈", listOf(Take.Decor { it == "bg.stats" || it == "bg.pay" })),
        Part("app.decor", TabOrder.SETTINGS, "앱 전체 꾸밈", listOf(Take.Decor { it == com.bamnat.core.recipe.Decor.APP || it == "bg.settings" || it == "bg.plaza" })),
        Part("widget.decor", WIDGET, "위젯 꾸밈", listOf(Take.Decor { it == com.bamnat.core.recipe.Decor.WIDGET })),
    )

    /** Whether the blueprint part [id] sits under one of [roots]. */
    fun under(id: String?, roots: Set<String>): Boolean = id != null && roots.any { id == it || id.startsWith("$it.") }

    fun byId(id: String): Part? = ALL.firstOrNull { it.id == id } ?: id.removePrefix(UNIT).takeIf { id.startsWith(UNIT) && it in BlueprintParts.UNITS }?.let(::unit)

    /** [BLU-07] "part:cal.head": one 부품 (a blueprint unit) as a part of its own, to keep, lay on and send alone. */
    const val UNIT = "part:"

    /** The screen a blueprint unit belongs to. */
    fun tabOfUnit(unit: String): String = when (unit.substringBefore('.')) {
        "cal" -> TabOrder.CALENDAR; "memo" -> TabOrder.MEMOS; "money" -> TabOrder.MONEY; "pay" -> TabOrder.STATS; "widget" -> WIDGET; else -> TabOrder.SETTINGS
    }

    fun unit(unit: String): Part = Part(UNIT + unit, tabOfUnit(unit), "부품 · " + (BlueprintParts.LABELS[unit] ?: unit), listOf(Take.Parts(setOf(unit))))

    /** The 부품 units [r]'s blueprint touches, in the order of [BlueprintParts.UNITS]. */
    fun units(r: Recipe): List<String> {
        val ids = (r.pieces.firstOrNull { it.kind == PieceKind.PARTS }?.values?.get("parts") as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.get("id") as? String }.orEmpty().map(BlueprintParts::unitOf).toSet()
        return BlueprintParts.UNITS.filter { it in ids }
    }

    /**
     * [BLU-07] The part [r] is when it is a 부품 work (장터 '부품' 구간, the server's scope `part`, recipes.js partUnitOf): its only piece is
     * 부품 꾸미기 with every line in one unit ("part:cal.head"), or one tab's 판 (갈래 E TAB_VIEW: "cal.board", "memo.view", "money.board").
     * Null for a theme or a tab's looks. The answer is a [byId] id.
     */
    fun partUnit(r: Recipe): String? {
        val p = r.pieces.singleOrNull() ?: return null
        if (p.kind == PieceKind.PARTS) return units(r).singleOrNull()?.let { UNIT + it }
        // 갈래 E's piece, read by its name so this file stands before and after it.
        if (p.kind.name != "TAB_VIEW") return null
        val keys = p.values.filterValues { it != null }.keys
        return VIEW_UNITS.entries.firstOrNull { (_, ks) -> keys.isNotEmpty() && ks.containsAll(keys) }?.key
    }

    /** 갈래 E's 판 parts by the TAB_VIEW keys each takes. */
    private val VIEW_UNITS = mapOf("cal.board" to setOf("cal"), "memo.view" to setOf("memo", "memoBy", "memoSort"), "money.board" to setOf("money"))

    /** The values [t] takes from [values], or null when it takes nothing there. */
    fun take(t: Take, values: Map<String, Any?>): Map<String, Any?>? = when (t) {
        is Take.Keys -> (if (t.keys == null) values else values.filterKeys { it in t.keys }).filterValues { it != null }.takeIf { it.isNotEmpty() }
        is Take.Screens -> {
            val screens = (values["screens"] as? String)?.let { ScreenLooks.decode(it) }?.let { all -> ScreenLooks(all.looks.filterKeys(t.screens)) }
                ?.encode()?.takeIf { it.isNotEmpty() }
            val pay = (values["pay"] as? String)?.takeIf { t.pay && it.isNotEmpty() }
            listOfNotNull(screens?.let { "screens" to it }, pay?.let { "pay" to it }).toMap().takeIf { it.isNotEmpty() }
        }
        is Take.Parts -> (values["parts"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.filter { under(it["id"] as? String, t.roots) }
            ?.takeIf { it.isNotEmpty() }?.let { mapOf("parts" to it) }
        is Take.Decor -> (values["items"] as? List<*>)?.filter { t.at(((it as? Map<*, *>)?.get("at") as? String).orEmpty()) }
            ?.takeIf { it.isNotEmpty() }?.let { mapOf("items" to it) }
    }

    /** The parts [r] has, in the order of [TABS]. */
    fun of(r: Recipe): List<Part> = (ALL.filter { p -> p.takes.none { it is Take.Parts } && p.takes.any { t -> r.pieces.firstOrNull { it.kind == t.kind }?.let { take(t, it.values) } != null } } +
        // [BLU-07] 부품 one by one ('부품 · 달력 머리'), so 담아 두기 and 입어 보기 can take one of them.
        units(r).map(::unit)).sortedBy { TABS.indexOf(it.tab) }

    /** [r] with only the parts [ids] (its other pieces — 알람, 규칙 — are not looks and are left out too). */
    fun pick(r: Recipe, ids: Collection<String>): Recipe {
        val out = LinkedHashMap<PieceKind, Map<String, Any?>>()
        ids.mapNotNull(::byId).forEach { p ->
            p.takes.forEach { t ->
                val v = r.pieces.firstOrNull { it.kind == t.kind }?.let { take(t, it.values) } ?: return@forEach
                out[t.kind] = merge(t.kind, out[t.kind], v)
            }
        }
        return r.copy(pieces = out.map { (k, v) -> Piece(k, v) }.sortedBy { it.kind.ordinal })
    }

    /** Two parts of one piece together ([ScreenLooks] screens joined, the rest key by key). */
    private fun merge(kind: PieceKind, a: Map<String, Any?>?, b: Map<String, Any?>): Map<String, Any?> {
        if (a == null) return b
        if (kind == PieceKind.PARTS) {
            // Line by line: [b]'s line for a part and state takes the place of [a]'s, the others stay.
            fun lines(m: Map<String, Any?>) = (m["parts"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
            fun key(l: Map<String, Any?>) = listOf(l["id"], l["on"], l["when"])
            val over = lines(b)
            val keys = over.map(::key).toSet()
            return mapOf("parts" to (lines(a).filterNot { key(it) in keys } + over).sortedWith(compareBy({ it["id"] as? String }, { it["on"] as? String ?: "" })))
        }
        // [LAY-01] Two parts' decorations together; laid on a base, [b]'s places take the place of [a]'s there.
        if (kind == PieceKind.DECOR) {
            val bi = (b["items"] as? List<*>).orEmpty()
            val places = bi.map { (it as? Map<*, *>)?.get("at") }.toSet()
            return mapOf("items" to ((a["items"] as? List<*>).orEmpty().filter { (it as? Map<*, *>)?.get("at") !in places } + bi).take(com.bamnat.core.recipe.Decor.MAX))
        }
        if (kind != PieceKind.SCREEN_LOOK) return a + b
        val screens = listOfNotNull(a["screens"] as? String, b["screens"] as? String).map { ScreenLooks.decode(it) }
            .fold(ScreenLooks()) { acc, s -> s.looks.entries.fold(acc) { x, (k, l) -> x.with(k, l) } }.encode().takeIf { it.isNotEmpty() }
        return listOfNotNull(screens?.let { "screens" to it }, ((b["pay"] ?: a["pay"]) as? String)?.let { "pay" to it }).toMap()
    }

    /** [base] with [over]'s pieces laid on top, key by key (스튜디오에서 조각 얹기): what [over] leaves out stays [base]'s. */
    fun layer(base: Recipe, over: Recipe): Recipe {
        val out = LinkedHashMap<PieceKind, Map<String, Any?>>()
        base.pieces.forEach { out[it.kind] = it.values }
        over.pieces.forEach { p -> out[p.kind] = merge(p.kind, out[p.kind], p.values) }
        return base.copy(pieces = out.map { (k, v) -> Piece(k, v) }.sortedBy { it.kind.ordinal })
    }

    /** A short name for a kept part set: "‘다크 골드’의 칸 디자인", or "… 외 2" for more. */
    fun title(from: String, ids: List<String>): String {
        val names = ids.mapNotNull { byId(it)?.label }
        val what = when {
            names.isEmpty() -> "전체"
            names.size == 1 -> names.first()
            else -> names.first() + " 외 ${names.size - 1}"
        }
        return "‘${from.take(16)}’의 $what"
    }

    /** The parts whose values differ between [a] and [b] (what was made one's own, 직접 바꾼 곳). */
    /**
     * [MKX-02] 1.36 기능별 탭: the one tab [r]'s looks change, or null when they change more than one (or the whole app: 앱 전체 색,
     * 아래 탭, 모서리) — a 테마 — or none.
     */
    fun onlyTab(r: Recipe): String? = of(r).map { it.tab }.distinct().singleOrNull()?.takeIf { it != TabOrder.SETTINGS }

    /** [TRY-04] The parts that place things (카드 순서·보이기, 칸 높이, 아래 탭): one frame for both sides of 비교. */
    val FRAME: List<String> = listOf("cal.cards", "cal.view", "memo.cards", "money.cards", "stats.cards", "app.tabs")

    /**
     * [TRY-04] 1.36 (사용자 2026-10-31 "서로 다르게 보이는 문제 … 동일하게 움직이게 만들고 달라진 점만 보이게", R-50): [base] laid out as
     * [over] is — its cards, cell heights and tabs in [over]'s places — so the two sides of 비교 meet on the same day and card and only
     * the looks differ.
     */
    fun frame(base: Recipe, over: Recipe): Recipe = layer(base, pick(over, FRAME))

    fun changed(a: Recipe, b: Recipe): List<String> = ALL.filter { p -> pick(a, listOf(p.id)).pieces != pick(b, listOf(p.id)).pieces }.map { it.id }

    /**
     * [THM-02] 1.35 Parts changed by hand before one's own theme counts as made (사용자 2026-10-30 21:53, 복제 올리기 막기): fewer and a
     * theme started from someone else's stays theirs (조합), with its days and ad.
     */
    const val OWN_PARTS = 3

    /** Whether [r] names any picture (배경 사진, 머리 그림, 근무 그림, 위젯 사진). */
    fun hasPhotos(r: Recipe): Boolean = withoutPhotos(r) != r

    /** [THM-03] 사진 빼고 올리기: [r] with every picture left out (each screen keeps its colours); the phone keeps its own. */
    fun withoutPhotos(r: Recipe): Recipe = r.copy(pieces = r.pieces.mapNotNull { p ->
        when (p.kind) {
            PieceKind.SCREEN_LOOK -> {
                val line = p.values["screens"] as? String
                if (line == null) p else {
                    val bare = ScreenLooks(ScreenLooks.decode(line).looks.mapValues { (_, l) -> l.copy(photo = null, dim = null) }).encode()
                    p.copy(values = if (bare.isEmpty()) p.values - "screens" else p.values + ("screens" to bare)).takeIf { it.values.isNotEmpty() }
                }
            }
            PieceKind.PHOTO_LOOK -> p.copy(values = p.values - PHOTO_KEYS).takeIf { it.values.isNotEmpty() }
            else -> p
        }
    })

    private val PHOTO_KEYS = setOf("bg", "dim", "head", "widget", "memoBg", "icons")
}
