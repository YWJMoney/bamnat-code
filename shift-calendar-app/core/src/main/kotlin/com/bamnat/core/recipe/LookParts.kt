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
    )

    fun byId(id: String): Part? = ALL.firstOrNull { it.id == id }

    /** The values [t] takes from [values], or null when it takes nothing there. */
    fun take(t: Take, values: Map<String, Any?>): Map<String, Any?>? = when (t) {
        is Take.Keys -> (if (t.keys == null) values else values.filterKeys { it in t.keys }).filterValues { it != null }.takeIf { it.isNotEmpty() }
        is Take.Screens -> {
            val screens = (values["screens"] as? String)?.let { ScreenLooks.decode(it) }?.let { all -> ScreenLooks(all.looks.filterKeys(t.screens)) }
                ?.encode()?.takeIf { it.isNotEmpty() }
            val pay = (values["pay"] as? String)?.takeIf { t.pay && it.isNotEmpty() }
            listOfNotNull(screens?.let { "screens" to it }, pay?.let { "pay" to it }).toMap().takeIf { it.isNotEmpty() }
        }
    }

    /** The parts [r] has, in the order of [TABS]. */
    fun of(r: Recipe): List<Part> = ALL.filter { p -> p.takes.any { t -> r.pieces.firstOrNull { it.kind == t.kind }?.let { take(t, it.values) } != null } }
        .sortedBy { TABS.indexOf(it.tab) }

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
