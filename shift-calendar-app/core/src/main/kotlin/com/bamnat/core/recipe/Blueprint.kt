package com.bamnat.core.recipe

import kotlin.math.roundToInt

/**
 * [BLU-01] 1.36 부품 설계도 (docs/blueprint-format.md, 계획 1.36-스튜디오-완전자유도-개발계획.md 갈래 A): the app's screens as a tree of
 * parts, each with the same style fields (채우기·선·모서리·그림자·여백·글자·투명도·보이기) and layout fields (방향·간격·정렬·순서·늘어남).
 * In a recipe it is one piece ([PieceKind.PARTS]): a list of lines, one per part and state. Values only (결정 D106); the app's own
 * engine draws them over what the older pieces already drew, so a theme without parts looks as it always did.
 * The server's `PART_IDS`·`RECIPE_PIECES.parts` (server/company-worker/src/recipes.js) is the same table.
 */
object BlueprintParts {
    /** Every part, parent before child. A part's parent is the id before its last dot; `cal`·`memo`·`money`·`pay`'s is `app`. */
    val IDS: List<String> = listOf(
        "app", "app.bar", "app.bar.title", "app.tabs", "app.tab", "app.button", "app.chip", "app.card", "app.sheet",
        "cal", "cal.head", "cal.head.title", "cal.head.nav", "cal.head.today", "cal.week", "cal.week.day", "cal.grid",
        "cal.cell", "cal.cell.date", "cal.cell.mark", "cal.cell.memo", "cal.cell.money", "cal.cell.hours", "cal.cell.spend",
        "cal.cell.lunar", "cal.cell.holiday", "cal.foot",
        // [TAB-04] 1.36 달력 판: 근무 띠, 타임라인.
        "cal.band", "cal.band.slot", "cal.line", "cal.line.row", "cal.line.bar",
        "memo.head", "memo.tags", "memo.card", "memo.card.title", "memo.card.body", "memo.card.date",
        // [TAB-01][TAB-02] 1.36 메모 속성 알약, 보드, 표.
        "memo.prop", "memo.board", "memo.board.col", "memo.board.head", "memo.table", "memo.table.head", "memo.table.row",
        "money.head", "money.day", "money.row", "money.amount",
        // [TAB-06] 1.36 가계부 판: 달력, 분류 보드, 표.
        "money.cal", "money.cal.cell", "money.board", "money.board.col", "money.table", "money.table.row",
        "pay.head", "pay.total", "pay.row",
        "widget", "widget.head", "widget.cell",
    )

    /** The words the studio shows for each part. */
    val LABELS: Map<String, String> = mapOf(
        "app" to "앱 전체", "app.bar" to "위 막대", "app.bar.title" to "위 막대 제목", "app.tabs" to "아래 탭 막대", "app.tab" to "탭 하나",
        "app.button" to "단추", "app.chip" to "칩", "app.card" to "카드", "app.sheet" to "아래 창",
        "cal" to "달력", "cal.head" to "달력 머리", "cal.head.title" to "달 이름", "cal.head.nav" to "이동 단추", "cal.head.today" to "오늘 단추",
        "cal.week" to "요일 줄", "cal.week.day" to "요일 글자", "cal.grid" to "칸 판", "cal.cell" to "날짜 칸", "cal.cell.date" to "날짜 숫자",
        "cal.cell.mark" to "근무 표시", "cal.cell.memo" to "메모 줄", "cal.cell.money" to "번 돈 줄", "cal.cell.hours" to "근무 시간 줄",
        "cal.cell.spend" to "쓴 돈 줄", "cal.cell.lunar" to "음력 줄", "cal.cell.holiday" to "공휴일 이름", "cal.foot" to "달력 아래 요약",
        "memo.head" to "메모장 머리", "memo.tags" to "꼬리표 줄", "memo.card" to "메모 카드", "memo.card.title" to "메모 제목",
        "memo.card.body" to "메모 내용", "memo.card.date" to "메모 날짜",
        "cal.band" to "근무 띠 판", "cal.band.slot" to "근무 띠 하루", "cal.line" to "타임라인 판", "cal.line.row" to "타임라인 하루 줄", "cal.line.bar" to "타임라인 근무 막대",
        "memo.prop" to "메모 속성", "memo.board" to "메모 보드", "memo.board.col" to "보드 칸", "memo.board.head" to "보드 칸 머리",
        "memo.table" to "메모 표", "memo.table.head" to "표 머리줄", "memo.table.row" to "표 한 줄",
        "money.cal" to "가계부 달력", "money.cal.cell" to "가계부 달력 칸", "money.board" to "분류 보드", "money.board.col" to "분류 칸",
        "money.table" to "가계부 표", "money.table.row" to "가계부 표 한 줄",
        "money.head" to "가계부 머리", "money.day" to "가계부 날짜 머리", "money.row" to "가계부 한 줄", "money.amount" to "금액",
        "pay.head" to "급여 머리", "pay.total" to "급여 합계", "pay.row" to "급여 한 줄",
        "widget" to "위젯", "widget.head" to "위젯 머리", "widget.cell" to "위젯 칸",
    )

    /** Parts that hold data: hidden, they still speak to TalkBack (as `mark:none`). */
    val DATA = setOf("cal.cell.date", "cal.cell.mark", "cal.cell.holiday", "money.amount", "pay.total")

    /** Roots that do not take the app's letters (the home screen draws its own). */
    private val OWN_ROOTS = setOf("app", "widget")

    fun parentOf(id: String): String? = when {
        id in OWN_ROOTS -> null
        '.' !in id -> "app"
        id.substringBeforeLast('.') in IDS -> id.substringBeforeLast('.')
        else -> "app"
    }

    /** A part's children by their last name, in the app's own order: what `order` may name. */
    val CHILDREN: Map<String, List<String>> = IDS.groupBy { parentOf(it) }.filterKeys { it != null }
        .mapKeys { it.key!! }.mapValues { (_, v) -> v.map { it.substringAfterLast('.') } }

    /** The states a line may be for (`on`), in the order they lay over each other. */
    val STATES: List<String> = listOf(
        "role.day", "role.eve", "role.night", "role.long", "role.off", "role.other", "in", "out", "sat", "sun", "holiday", "dim", "today", "sel",
    )

    val LINES = listOf("none", "solid", "dash", "dot", "double")
    val DIRS = listOf("row", "col", "stack")
    val ALIGNS = listOf("start", "center", "end", "stretch")
    val JUSTIFIES = listOf("start", "center", "end", "between", "around")
    val TALIGNS = listOf("start", "center", "end")
    val PLACES = listOf("tl", "tc", "tr", "ml", "mc", "mr", "bl", "bc", "br")
    /** The fonts the app draws itself; another id (갈래 B's catalogue, `u`+16 hex my own) falls back to [FONTS] first. */
    val FONTS = listOf("sans", "serif", "mono", "hand", "round")
    private val FONT_ID = Regex("^[a-z][a-z0-9-]{1,39}$")
    const val MAX_LINES = 96

    /** The fields a part may carry besides `id` and `on`, every one optional. */
    val STYLE_FIELDS: List<Field> = listOf(
        Field.Color("fill", optional = true), Field.Num("fillA", 0.0, 100.0, optional = true),
        Field.Pick("line", LINES, optional = true), Field.Num("lineW", 0.0, 8.0, decimals = 1, optional = true),
        Field.Color("lineC", optional = true), Field.Num("lineA", 0.0, 100.0, optional = true),
        Field.Num("r", 0.0, 48.0, optional = true), Field.Num("rTL", 0.0, 48.0, optional = true), Field.Num("rTR", 0.0, 48.0, optional = true),
        Field.Num("rBR", 0.0, 48.0, optional = true), Field.Num("rBL", 0.0, 48.0, optional = true),
        Field.Num("shadow", 0.0, 24.0, optional = true), Field.Color("shadowC", optional = true),
        Field.Num("pad", 0.0, 32.0, optional = true), Field.Num("padX", 0.0, 32.0, optional = true), Field.Num("padY", 0.0, 32.0, optional = true),
        Field.Num("out", 0.0, 32.0, optional = true),
        Field.Text("font", 40, optional = true, guard = false, check = { if (FONT_ID.matches(it)) null else "font" }),
        Field.Num("size", 6.0, 96.0, decimals = 1, optional = true), Field.Num("weight", 100.0, 900.0, optional = true),
        Field.Color("ink", optional = true), Field.Num("track", -5.0, 30.0, decimals = 1, optional = true),
        Field.Num("leading", 80.0, 300.0, optional = true), Field.Num("stroke", 0.0, 4.0, decimals = 1, optional = true),
        Field.Color("strokeC", optional = true), Field.Flag("italic", input = false), Field.Pick("talign", TALIGNS, optional = true),
        Field.Num("lines", 1.0, 10.0, optional = true), Field.Num("alpha", 0.0, 100.0, optional = true), Field.Flag("hide", input = false),
        // 배치
        Field.Pick("dir", DIRS, optional = true), Field.Num("gap", 0.0, 32.0, optional = true), Field.Pick("align", ALIGNS, optional = true),
        Field.Pick("justify", JUSTIFIES, optional = true), Field.Text("order", 200, optional = true, guard = false, check = ::orderWords),
        Field.Pick("place", PLACES, optional = true), Field.Num("grow", 0.0, 10.0, optional = true),
        Field.Num("w", 0.0, 600.0, optional = true), Field.Num("h", 0.0, 600.0, optional = true),
    )

    /** The keys a child takes from its parent when it has none of its own. */
    val TEXT_KEYS = setOf("font", "size", "weight", "ink", "track", "leading", "stroke", "strokeC", "italic")

    private val ORDER = Regex("^[a-z]{1,12}(,[a-z]{1,12}){0,15}$")
    private fun orderWords(s: String): String? = if (ORDER.matches(s) && s.split(',').let { it.toSet().size == it.size }) null else "order"

    val FIELDS: List<Field> = listOf(
        Field.Items(
            "parts", MAX_LINES, min = 1,
            fields = listOf(Field.Pick("id", IDS), Field.Pick("on", STATES, optional = true)) + STYLE_FIELDS,
            check = ::linesProblem,
        ),
    )

    /** Each (id, on) once, and an `order` names only that part's own children. */
    fun linesProblem(lines: List<Map<String, Any?>>): String? {
        val seen = HashSet<String>()
        for (l in lines) {
            val id = l["id"] as String
            if (!seen.add(id + "|" + (l["on"] ?: ""))) return "dup"
            val order = l["order"] as? String ?: continue
            val kids = CHILDREN[id].orEmpty()
            if (order.split(',').any { it !in kids }) return "order"
        }
        return null
    }
}

/**
 * [BLU-02] One part's style as drawn: every field null is "as the older pieces draw it". [resolve] lays the lines that fit a part
 * and its states over each other, letters taken from the parent where the part has none.
 */
data class PartStyle(
    val fill: Long? = null, val fillA: Int? = null,
    val line: String? = null, val lineW: Double? = null, val lineC: Long? = null, val lineA: Int? = null,
    val r: Int? = null, val rTL: Int? = null, val rTR: Int? = null, val rBR: Int? = null, val rBL: Int? = null,
    val shadow: Int? = null, val shadowC: Long? = null,
    val pad: Int? = null, val padX: Int? = null, val padY: Int? = null, val out: Int? = null,
    val font: String? = null, val size: Double? = null, val weight: Int? = null, val ink: Long? = null,
    val track: Double? = null, val leading: Int? = null, val stroke: Double? = null, val strokeC: Long? = null,
    val italic: Boolean? = null, val talign: String? = null, val lines: Int? = null, val alpha: Int? = null, val hide: Boolean? = null,
    val dir: String? = null, val gap: Int? = null, val align: String? = null, val justify: String? = null, val order: List<String>? = null,
    val place: String? = null, val grow: Int? = null, val w: Int? = null, val h: Int? = null,
) {
    val isEmpty: Boolean get() = this == NONE

    /** The four corners (top-left, top-right, bottom-right, bottom-left), each its own or [r]; null where neither is set. */
    val corners: List<Int?> get() = listOf(rTL ?: r, rTR ?: r, rBR ?: r, rBL ?: r)
    val padH: Int? get() = padX ?: pad
    val padV: Int? get() = padY ?: pad
    val hasLayout: Boolean get() = dir != null || gap != null || align != null || justify != null || order != null

    /** [o]'s fields over these. */
    fun over(o: PartStyle): PartStyle = PartStyle(
        o.fill ?: fill, o.fillA ?: fillA, o.line ?: line, o.lineW ?: lineW, o.lineC ?: lineC, o.lineA ?: lineA,
        o.r ?: r, o.rTL ?: rTL, o.rTR ?: rTR, o.rBR ?: rBR, o.rBL ?: rBL, o.shadow ?: shadow, o.shadowC ?: shadowC,
        o.pad ?: pad, o.padX ?: padX, o.padY ?: padY, o.out ?: out,
        o.font ?: font, o.size ?: size, o.weight ?: weight, o.ink ?: ink, o.track ?: track, o.leading ?: leading,
        o.stroke ?: stroke, o.strokeC ?: strokeC, o.italic ?: italic, o.talign ?: talign, o.lines ?: lines, o.alpha ?: alpha, o.hide ?: hide,
        o.dir ?: dir, o.gap ?: gap, o.align ?: align, o.justify ?: justify, o.order ?: order, o.place ?: place, o.grow ?: grow, o.w ?: w, o.h ?: h,
    )

    /** Only the letters of these, for a child to take. */
    fun letters(): PartStyle = PartStyle(font = font, size = size, weight = weight, ink = ink, track = track, leading = leading,
        stroke = stroke, strokeC = strokeC, italic = italic)

    companion object {
        val NONE = PartStyle()

        /**
         * One line's values (as [RecipeCodec] cleaned them, colours already plain) as a style; a [Ref] still in it (a token the caller
         * did not turn into a colour, an input not answered) is left as the app's own.
         */
        fun of(m: Map<String, Any?>): PartStyle {
            fun c(k: String) = m[k] as? Long
            fun i(k: String) = (m[k] as? Number)?.toDouble()?.roundToInt()
            fun d(k: String) = (m[k] as? Number)?.toDouble()
            fun s(k: String) = m[k] as? String
            fun b(k: String) = m[k] as? Boolean
            return PartStyle(
                c("fill"), i("fillA"), s("line"), d("lineW"), c("lineC"), i("lineA"),
                i("r"), i("rTL"), i("rTR"), i("rBR"), i("rBL"), i("shadow"), c("shadowC"),
                i("pad"), i("padX"), i("padY"), i("out"),
                s("font"), d("size"), i("weight")?.let { ((it / 100.0).roundToInt() * 100).coerceIn(100, 900) }, c("ink"),
                d("track"), i("leading"), d("stroke"), c("strokeC"), b("italic"), s("talign"), i("lines"), i("alpha"), b("hide"),
                s("dir"), i("gap"), s("align"), s("justify"), s("order")?.split(',')?.filter { it.isNotEmpty() }, s("place"), i("grow"), i("w"), i("h"),
            )
        }
    }
}

/**
 * [BLU-01][BLU-02] A whole blueprint: the cleaned lines of a [PieceKind.PARTS] piece. [style] answers "how is this part drawn in these
 * states", resolved once per part and set of states and kept, so a month of cells costs a lookup each (drawn on one thread).
 */
class Blueprint(val lines: List<Map<String, Any?>>) {
    private val byKey: Map<String, PartStyle> = lines.associate { (it["id"] as String) + "|" + (it["on"] as? String ?: "") to PartStyle.of(it) }
    private val cache = HashMap<String, PartStyle>()

    val isEmpty: Boolean get() = lines.isEmpty()
    val ids: Set<String> = lines.mapTo(LinkedHashSet()) { it["id"] as String }

    /** Whether any line touches [id] or a part under it. */
    fun touches(id: String): Boolean = ids.any { it == id || it.startsWith("$id.") }

    /** The part's own lines for [states] laid in [BlueprintParts.STATES] order, without what it takes from its parent. */
    fun own(id: String, states: Set<String> = emptySet()): PartStyle {
        var s = byKey["$id|"] ?: PartStyle.NONE
        for (st in BlueprintParts.STATES) if (st in states) byKey["$id|$st"]?.let { s = s.over(it) }
        return s
    }

    /** How [id] is drawn in [states]: its own lines over the letters its parents give it. */
    fun style(id: String, states: Set<String> = emptySet()): PartStyle {
        if (lines.isEmpty()) return PartStyle.NONE
        val key = id + "|" + states.sorted().joinToString(",")
        cache[key]?.let { return it }
        val parent = BlueprintParts.parentOf(id)
        val inherited = parent?.let { style(it, states).letters() } ?: PartStyle.NONE
        val s = inherited.over(own(id, states))
        cache[key] = s
        return s
    }

    /** [id]'s children by last name in the blueprint's order: those [order] names first, the rest after in the app's order. */
    fun childOrder(id: String, states: Set<String> = emptySet(), own: List<String> = BlueprintParts.CHILDREN[id].orEmpty()): List<String> {
        val wanted = style(id, states).order ?: return own
        return wanted.filter { it in own } + own.filter { it !in wanted }
    }

    /** The lines for [id] and every part under it: a part to take on its own (갈래 F 부품 단위 리믹스). */
    fun subtree(id: String): List<Map<String, Any?>> = lines.filter { val p = it["id"] as String; p == id || p.startsWith("$id.") }

    /** These lines with [other]'s over them, line by line (same id and state: [other]'s wins). */
    fun plus(other: List<Map<String, Any?>>): Blueprint {
        val m = LinkedHashMap<String, Map<String, Any?>>()
        for (l in lines + other) {
            val k = (l["id"] as String) + "|" + (l["on"] as? String ?: "")
            m[k] = (m[k] ?: emptyMap()) + l
        }
        return Blueprint(m.values.toList())
    }

    /** These lines in the order the codec writes them, as the setting and the recipe piece hold them. */
    fun encode(): String = if (lines.isEmpty()) "" else RecipeCodec.canonical(sorted())

    fun sorted(): List<Map<String, Any?>> = lines.sortedWith(
        compareBy<Map<String, Any?>>({ BlueprintParts.IDS.indexOf(it["id"] as String) }, { (it["on"] as? String)?.let { s -> BlueprintParts.STATES.indexOf(s) + 1 } ?: 0 }),
    )

    companion object {
        val EMPTY = Blueprint(emptyList())

        /** A blueprint from its setting (the codec's written list); anything that does not read is no blueprint. */
        @Suppress("UNCHECKED_CAST")
        fun decode(s: String?): Blueprint {
            if (s.isNullOrBlank()) return EMPTY
            return try {
                val raw = com.bamnat.core.remote.Json.parse(s) as? List<Any?> ?: return EMPTY
                Blueprint(clean(raw))
            } catch (e: Exception) {
                EMPTY
            }
        }

        /** [raw] lines checked as a recipe would check them; refuses ([RecipeException]) what a recipe would refuse. */
        fun clean(raw: List<Any?>): List<Map<String, Any?>> {
            val recipe = mapOf(
                "format" to Recipe.FORMAT, "v" to Recipe.VERSION.toLong(), "title" to "부품", "styles" to listOf("SHIFT"),
                "pieces" to listOf(mapOf("kind" to PieceKind.PARTS.key, "parts" to raw)),
            )
            @Suppress("UNCHECKED_CAST")
            return RecipeCodec.clean(recipe).pieces.first().values["parts"] as List<Map<String, Any?>>
        }

        /** The problem with [s] as a setting, or null. */
        fun problem(s: String): String? = if (s.isEmpty()) null else try {
            val raw = com.bamnat.core.remote.Json.parse(s) as? List<Any?>
            if (raw == null) "parts" else { clean(raw); null }
        } catch (e: RecipeException) {
            e.code
        } catch (e: Exception) {
            "parts"
        }
    }
}

/**
 * [BLU-03] 옛 조각 옮겨 읽기: the older pieces' looks as the same parts, so the studio's 부품 view starts from what is on screen and an
 * old recipe opens as parts. Drawing never goes through this (the older pieces still draw, the blueprint over them), so reading old
 * looks this way cannot change how a theme looks.
 */
object LegacyParts {
    fun of(
        calTheme: String? = null, calView: String? = null, screenLooks: String? = null, memoLook: String? = null, moneyLook: String? = null,
    ): List<Map<String, Any?>> {
        val out = LinkedHashMap<String, MutableMap<String, Any?>>()
        fun put(id: String, on: String?, k: String, v: Any?) {
            if (v == null) return
            out.getOrPut(id + "|" + (on ?: "")) { linkedMapOf<String, Any?>("id" to id).also { m -> on?.let { m["on"] = it } } }[k] = v
        }
        val t = CalendarTheme.decode(calTheme.orEmpty())
        put("cal.cell.date", "today", "fill", t.today)
        for ((on, c) in listOf("sat" to t.sat, "sun" to t.sun)) {
            put("cal.week.day", on, "ink", c)
            put("cal.cell.date", on, "ink", c)
        }
        put("cal.cell.date", "holiday", "ink", t.holiday)
        put("cal.cell.holiday", null, "ink", t.holiday)

        val v = CalendarView.decode(calView.orEmpty())
        if (v.grid == "line") { put("cal.cell", null, "line", "solid"); put("cal.cell", null, "lineW", 0.5) }
        v.order?.takeIf { it.isNotEmpty() }?.let { put("cal.cell", null, "order", (listOf("date", "mark") + it).distinct().joinToString(",")) }
        // One look for every cell is the same as a plain cell's style; several taking turns stay the 칸 디자이너's own.
        v.looks.singleOrNull()?.takeIf { v.todayLook == null }?.let { l ->
            l.bg?.takeIf { it != CellLook.SHIFT && it != CellLook.NONE }?.let { put("cal.cell", null, "fill", it.toLong(16) or 0xFF000000L) }
            put("cal.cell", null, "fillA", l.bgAlpha?.toLong())
            when (l.border) {
                "thin" -> { put("cal.cell", null, "line", "solid"); put("cal.cell", null, "lineW", 0.5) }
                "thick" -> { put("cal.cell", null, "line", "solid"); put("cal.cell", null, "lineW", 1.5) }
                "dash" -> { put("cal.cell", null, "line", "dash"); put("cal.cell", null, "lineW", 1L) }
                "none" -> put("cal.cell", null, "line", "none")
            }
            put("cal.cell", null, "lineC", l.borderColor)
            put("cal.cell", null, "r", l.corner?.toLong())
            put("cal.cell", null, "pad", l.pad?.toLong())
            put("cal.cell.date", null, "ink", l.dateColor)
            put("cal.cell.date", null, "fill", l.dateFill)
            put("cal.cell.date", null, "weight", l.dateBold?.let { if (it) 700L else 400L })
        }

        val s = ScreenLooks.decode(screenLooks).of(ScreenLooks.ALL)
        put("app", null, "fill", s.bg)
        put("app", null, "ink", s.ink)
        put("app.card", null, "fill", s.card)
        put("app.button", null, "fill", s.accent)

        val m = MemoLook.decode(memoLook.orEmpty())
        put("memo.card", null, "fill", m.cardColor)
        if (m.border == "thin") { put("memo.card", null, "line", "solid"); put("memo.card", null, "lineW", 1L) }
        if (m.border == "none") put("memo.card", null, "line", "none")
        put("memo.card", null, "r", m.corner?.toLong())
        put("memo.card", null, "font", m.font?.takeIf { it in BlueprintParts.FONTS })
        put("memo.card.title", null, "weight", m.bold?.let { if (it) 700L else 400L })
        put("memo.card.body", null, "lines", m.lines?.toLong()?.coerceIn(1, 10))

        val y = MoneyLook.decode(moneyLook.orEmpty())
        put("money.amount", "in", "ink", y.income)
        put("money.amount", "out", "ink", y.expense)
        return Blueprint(out.values.toList()).sorted()
    }

    /** An old recipe's look pieces as parts (a token's light colour for a token, an input left out), and its own parts over them. */
    @Suppress("UNCHECKED_CAST")
    fun of(r: Recipe): List<Map<String, Any?>> {
        fun values(k: PieceKind) = r.pieces.firstOrNull { it.kind == k }?.values.orEmpty()
        fun color(v: Any?): Long? = when (v) { is Long -> v; is Ref.Token -> r.token(v.name)?.light; else -> null }
        fun hex(c: Long?) = c?.let { (it and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0') }
        val t = values(PieceKind.CAL_THEME)
        val calTheme = CalendarTheme(color(t["today"]), color(t["sat"]), color(t["sun"]), color(t["holiday"]), t["cell"] as? String).encode()
        val v = values(PieceKind.CAL_VIEW)
        val calView = listOfNotNull(
            (v["grid"] as? String)?.let { "grid=$it" }, (v["order"] as? String)?.let { "order=$it" }, (v["looks"] as? String)?.let { "looks=$it" },
            (v["today"] as? Number)?.let { "today=${it.toInt()}" },
        ).joinToString(";")
        val m = values(PieceKind.MEMO_LOOK)
        val memo = listOfNotNull(
            hex(color(m["cardColor"]))?.let { "cardColor=$it" }, (m["border"] as? String)?.let { "border=$it" },
            (m["corner"] as? Number)?.let { "corner=${it.toInt()}" }, (m["lines"] as? Number)?.let { "lines=${it.toInt()}" },
            (m["bold"] as? Boolean)?.let { "bold=" + if (it) 1 else 0 }, (m["font"] as? String)?.let { "font=$it" },
        ).joinToString(";")
        val y = values(PieceKind.MONEY_LOOK)
        val money = listOfNotNull(hex(color(y["income"]))?.let { "income=$it" }, hex(color(y["expense"]))?.let { "expense=$it" }).joinToString(";")
        val old = of(calTheme, calView, values(PieceKind.SCREEN_LOOK)["screens"] as? String, memo, money)
        val own = (values(PieceKind.PARTS)["parts"] as? List<Map<String, Any?>>).orEmpty()
            .map { l -> l.mapValues { (_, x) -> if (x is Ref) color(x) else x }.filterValues { it != null } }
        return Blueprint(old).plus(own).sorted()
    }
}
