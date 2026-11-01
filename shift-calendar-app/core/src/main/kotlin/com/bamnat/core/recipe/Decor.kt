package com.bamnat.core.recipe

import com.bamnat.core.remote.Json

/**
 * [LAY-01] 1.36 꾸밈 층 (개발계획 874, 제안 6·25, 사용자 2026-11-01 "제약을 없애고 완전한 자유도: 자유 배치(도형·스티커·글상자·아이콘)"):
 * shapes, icons, emoji and text boxes placed freely over a part of the app. Each one is only values (결정 D106): what it is, the part it
 * is pinned to ([at], the part ids of 부품 설계도 1판 plus each screen's own background), where in that part (‰ of its width and height,
 * so a phone of any size puts it in the same place), its size in dp, its turn and its look in the part style's own field names
 * (`fill`·`fillA`·`line`·`lineW`·`lineC`·`r`·`shadow`·`font`·`size`·`weight`·`ink`·`italic`·`alpha`·`hide`, 형식 1판 4항).
 *
 * Nothing in a decoration runs, and a decoration never covers the day's date or work unless its maker asks ([DecorItem.over], off by
 * default). TalkBack never reads one. Pinned to [CELL], it can be pinned to days too ([DecorItem.on]): the part states (today, sat, sun,
 * holiday, role.night …) and days of its own — every 25th (d25), every Saturday (w6), one day a year (m12d25) — which only 밤낮 can do.
 *
 * In a recipe it is the piece `decor` (items, checked by [RecipeCodec] like any piece, so colours may be tokens and a text box may be the
 * receiver's answer); on the phone the settings keep the same items as JSON ([encode]).
 */
data class DecorItem(
    val kind: DecorKind,
    /** A shape's or icon's id in [DecorGlyphs]; the emoji itself for [DecorKind.EMOJI]; the words for [DecorKind.TEXT]. */
    val glyph: String,
    val at: String,
    /** For [Decor.CELL]: which days it stands on ([Decor.onProblem]); null is every day. */
    val on: String? = null,
    /** The centre, ‰ of the part's width (0 left … 1000 right) and height. */
    val x: Int = 500,
    val y: Int = 500,
    /** Size in dp. */
    val w: Int = 24,
    val h: Int = 24,
    /** Turn, degrees clockwise. */
    val rot: Int = 0,
    val fill: Long? = null,
    /** How strong [fill] is, 0..100 %. */
    val fillA: Int = 100,
    /** [Decor.LINES]. */
    val line: String? = null,
    /** dp, one decimal. */
    val lineW: Double = 0.0,
    val lineC: Long? = null,
    /** A shape's corners, dp (네모·말풍선 …). */
    val r: Int = 0,
    /** Shadow height, dp. */
    val shadow: Int = 0,
    /** A text box's font id (형식 1판: sans·serif·mono·hand·round; 갈래 B adds more). */
    val font: String? = null,
    /** A text box's size in sp. */
    val size: Double = 14.0,
    val weight: Int = 400,
    /** A text box's or an icon's colour. */
    val ink: Long? = null,
    val italic: Boolean = false,
    /** The whole decoration's opacity, 0..100 %. */
    val alpha: Int = 100,
    /** 레이어 패널: kept but not drawn. */
    val hide: Boolean = false,
    /** Drawn over the day's date and work; off, it is drawn under them (보고서 5-2 '데이터 위로 올리기는 기본으로 꺼 둬요'). */
    val over: Boolean = false,
    /** Mirrored left to right. */
    val flip: Boolean = false,
    /** 레이어 패널: a finger cannot move it by mistake. */
    val lock: Boolean = false,
    /** 묶기: items of the same group (1..9) move together; 0 is none. */
    val group: Int = 0,
    /**
     * [LAY-11] 1.36 누락 점검 N8: a 규칙 단추's id (갈래 D, `^[a-z0-9]{1,12}$`) — pressing the decoration presses that button, as the
     * 단추 does wherever it is placed. Null is a picture only; a button the theme does not have leaves it a picture too.
     */
    val tap: String? = null,
    /**
     * PC 확인 C6 1.36 (배경 사진은 화면을 채우게): on a screen's background, the whole screen — the place and size are kept for when it is
     * turned off. Elsewhere it is ignored.
     */
    val full: Boolean = false,
) {
    /** Its piece values: what is the app's own is left out, so a recipe holds only what was chosen. */
    fun values(): Map<String, Any?> = buildMap {
        when (kind) {
            DecorKind.SHAPE -> put("shape", glyph)
            DecorKind.ICON -> put("icon", glyph)
            DecorKind.EMOJI -> put("emoji", glyph)
            DecorKind.TEXT -> put("text", glyph)
            DecorKind.PHOTO -> put("photo", glyph)
        }
        put("at", at)
        on?.let { put("on", it) }
        put("x", x.toLong()); put("y", y.toLong()); put("w", w.toLong()); put("h", h.toLong())
        if (rot != 0) put("rot", rot.toLong())
        fill?.let { put("fill", it) }
        if (fillA != 100) put("fillA", fillA.toLong())
        line?.let { put("line", it) }
        if (lineW != 0.0) put("lineW", num(lineW))
        lineC?.let { put("lineC", it) }
        if (r != 0) put("r", r.toLong())
        if (shadow != 0) put("shadow", shadow.toLong())
        font?.let { put("font", it) }
        if (this@DecorItem.size != 14.0) put("size", num(this@DecorItem.size))
        if (weight != 400) put("weight", weight.toLong())
        ink?.let { put("ink", it) }
        if (italic) put("italic", true)
        if (alpha != 100) put("alpha", alpha.toLong())
        if (hide) put("hide", true)
        if (over) put("over", true)
        if (flip) put("flip", true)
        if (lock) put("lock", true)
        if (group != 0) put("group", group.toLong())
        tap?.let { put("tap", it) }
        if (full) put("full", true)
    }

    /** Whether it stands on a day with these facts (always, off [Decor.CELL]). */
    fun standsOn(day: DecorDay): Boolean {
        val o = on ?: return true
        return when {
            o == "today" -> day.today
            o == "holiday" -> day.holiday
            o == "sat" -> day.dayOfWeek == 6
            o == "sun" -> day.dayOfWeek == 7
            o == "dim" -> day.dim
            o.startsWith("role.") -> day.role == o.removePrefix("role.")
            o.startsWith("w") -> o.drop(1).toIntOrNull() == day.dayOfWeek
            o.startsWith("m") -> o.drop(1).split('d').let { it.size == 2 && it[0].toIntOrNull() == day.month && it[1].toIntOrNull() == day.dayOfMonth }
            o.startsWith("d") -> o.drop(1).toIntOrNull() == day.dayOfMonth
            else -> false
        }
    }

    /** What the 레이어 패널 calls it. */
    val label: String get() = when (kind) {
        DecorKind.SHAPE -> DecorGlyphs.shape(glyph)?.label ?: "도형"
        DecorKind.ICON -> DecorGlyphs.icon(glyph)?.label?.substringBefore(' ') ?: "아이콘"
        DecorKind.EMOJI -> glyph
        DecorKind.TEXT -> "“" + glyph.take(12) + (if (glyph.length > 12) "…" else "") + "”"
        DecorKind.PHOTO -> "사진"
    }

    private fun num(d: Double): Any = if (d == kotlin.math.round(d)) d.toLong() else d
}

/** What a day cell's decorations look at. [dayOfWeek] is 1 Monday … 7 Sunday; [role] a [ShiftRole] key. */
data class DecorDay(
    val month: Int, val dayOfMonth: Int, val dayOfWeek: Int, val role: String?, val today: Boolean, val holiday: Boolean, val dim: Boolean = false,
)

enum class DecorKind(val key: String, val label: String) {
    SHAPE("shape", "도형"), ICON("icon", "아이콘"), EMOJI("emoji", "이모지"), TEXT("text", "글상자"),
    /**
     * [LAY-10] 1.36 누락 점검 N12: a photo of the user's own, made small on the phone without its place or camera ([glyph] is its
     * 16-hex picture id, as 그림 꾸미기's). Sent with a recipe it passes the server's three photo checks (갈래 F photo-guard): a new maker's
     * first is looked at first, two reports hide it, and others draw a grey box until they may see it.
     */
    PHOTO("photo", "사진");
}

/** The whole layer, in drawing order (the first is at the back). */
data class Decor(val items: List<DecorItem> = emptyList()) {
    val isDefault: Boolean get() = items.isEmpty()

    /** As the settings keep it: the piece's items as JSON, empty for none. */
    fun encode(): String = if (items.isEmpty()) "" else RecipeCodec.canonical(items.map { it.values() })

    /** What is drawn on [anchor], back to front. */
    fun on(anchor: String): List<DecorItem> = items.filter { it.at == anchor && !it.hide }

    fun has(anchor: String): Boolean = items.any { it.at == anchor && !it.hide }

    /** [LAY-10] The picture ids its photos name. */
    val images: Set<String> get() = items.filter { it.kind == DecorKind.PHOTO }.map { it.glyph }.toSet()

    /** [LAY-10] 사진 빼고 올리기: the layer without its photos. */
    fun withoutPhotos(): Decor = Decor(items.filter { it.kind != DecorKind.PHOTO })

    /** The piece's values. */
    fun values(): Map<String, Any?> = if (items.isEmpty()) emptyMap() else mapOf("items" to items.map { it.values() })

    companion object {
        /** At most this many decorations in all (그리는 데 드는 힘, 보고서 5-2 '꾸밈 수에는 한도'). */
        const val MAX = 40
        /** At most this many are drawn in the day cells: each is drawn in every cell of the month. */
        const val MAX_CELL = 12
        const val MAX_TEXT = 40
        const val MAX_SIZE = 400
        const val APP = "app"
        const val CELL = "cal.cell"
        const val HEAD = "cal.head"
        const val WEEK = "cal.week"
        const val FOOT = "cal.foot"
        const val WIDGET = "widget"
        /**
         * One screen's background: "bg." + a [ScreenLooks] key ([APP] is every screen's). Not the alarm's nor 결제·처리방침's: they stay as
         * the app draws them.
         */
        val SCREENS = listOf("calendar", "memo", "money", "stats", "pay", "plaza", "settings")
        val ANCHORS: List<String> = listOf(APP) + SCREENS.map { "bg.$it" } + listOf(HEAD, WEEK, CELL, WIDGET)
        val LINES = listOf("none", "solid", "dash", "dot", "double")
        /** 형식 1판's fonts; 갈래 B's catalog ids and u+16 hex pass too ([fontProblem]). */
        private val FONT = Regex("^([a-z][a-z0-9-]{1,39}|u\\+[0-9a-f]{16})$")

        fun anchorLabel(a: String): String = when (a) {
            APP -> "모든 화면 배경"; CELL -> "달력 칸"; HEAD -> "달력 머리(달 이름)"; WEEK -> "요일 줄"; WIDGET -> "위젯"
            "bg.calendar" -> "달력 화면 배경"; "bg.memo" -> "메모 화면 배경"; "bg.money" -> "가계부 화면 배경"; "bg.stats" -> "통계 화면 배경"
            "bg.pay" -> "급여 화면 배경"; "bg.plaza" -> "마을 화면 배경"; "bg.settings" -> "설정 화면 배경"
            else -> a
        }

        /** The part the 레이어 패널 hangs an anchor under (부품 나무, 형식 1판 3항). */
        fun parentOf(a: String): String = when {
            a.startsWith("bg.") || a == APP -> APP
            a == WIDGET -> WIDGET
            else -> "cal"
        }

        /** The days a cell decoration may stand on: the part states and days of its own. */
        private val ON = Regex("^(today|sat|sun|holiday|dim|role\\.(day|eve|night|long|off|other)|d([1-9]|[12][0-9]|3[01])|w[1-7]|m([1-9]|1[0-2])d([1-9]|[12][0-9]|3[01]))$")
        fun onProblem(s: String): String? = if (s.isEmpty() || ON.matches(s)) null else "on"
        fun fontProblem(s: String): String? = if (s.isEmpty() || FONT.matches(s)) null else "font"
        private val PHOTO = Regex("^[0-9a-f]{16}$")
        private val TAP = Regex("^[a-z0-9]{1,12}$")
        /** [LAY-10] A photo is named by its 16-hex picture id only (server decor.js decorPhoto). */
        fun photoProblem(s: String): String? = if (s.isEmpty() || PHOTO.matches(s)) null else "photo"
        /** [LAY-11] A 규칙 단추's id (갈래 D Logic, server decor.js decorTap). */
        fun tapProblem(s: String): String? = if (s.isEmpty() || TAP.matches(s)) null else "tap"

        fun onLabel(on: String?): String = when {
            on == null -> "모든 날"
            on == "today" -> "오늘"
            on == "holiday" -> "공휴일"
            on == "sat" -> "토요일"
            on == "sun" -> "일요일"
            on == "dim" -> "다른 달 날"
            on.startsWith("role.") -> ShiftRole.entries.firstOrNull { it.key == on.removePrefix("role.") }?.label?.let { "$it 근무 날" } ?: on
            on.startsWith("w") -> listOf("월", "화", "수", "목", "금", "토", "일").getOrNull((on.drop(1).toIntOrNull() ?: 0) - 1)?.let { "매주 ${it}요일" } ?: on
            on.startsWith("m") -> on.drop(1).split('d').let { "매년 ${it[0]}월 ${it.getOrNull(1)}일" }
            on.startsWith("d") -> "매달 ${on.drop(1)}일"
            else -> on
        }

        /** The layer from the piece's values (tokens and answers already put in, [RecipeResolve]); an item it cannot draw is left out. */
        fun of(values: Map<String, Any?>?): Decor = Decor(fromItems(values?.get("items") as? List<*>))

        /** The layer as the settings keep it. */
        fun decode(s: String?): Decor {
            if (s.isNullOrBlank()) return Decor()
            return Decor(fromItems(runCatching { Json.parse(s) }.getOrNull() as? List<*>))
        }

        private fun fromItems(list: List<*>?): List<DecorItem> {
            var cells = 0
            return list.orEmpty().take(MAX).mapNotNull { (it as? Map<*, *>)?.let(::item) }.filter { it.at != CELL || ++cells <= MAX_CELL }
        }

        private fun item(m: Map<*, *>): DecorItem? {
            val (kind, glyph) = (m["text"] as? String)?.takeIf { it.isNotEmpty() }?.let { DecorKind.TEXT to it }
                ?: (m["emoji"] as? String)?.let { DecorKind.EMOJI to it }
                ?: (m["icon"] as? String)?.takeIf { it in DecorGlyphs.ICONS }?.let { DecorKind.ICON to it }
                ?: (m["shape"] as? String)?.takeIf { it in DecorGlyphs.SHAPES }?.let { DecorKind.SHAPE to it }
                ?: (m["photo"] as? String)?.takeIf { photoProblem(it) == null && it.isNotEmpty() }?.let { DecorKind.PHOTO to it }
                ?: return null
            val at = (m["at"] as? String)?.takeIf { it in ANCHORS } ?: return null
            fun int(k: String, d: Int, lo: Int, hi: Int) = ((m[k] as? Number)?.toDouble()?.let { kotlin.math.round(it).toInt() } ?: d).coerceIn(lo, hi)
            fun dbl(k: String, d: Double, lo: Double, hi: Double) = ((m[k] as? Number)?.toDouble() ?: d).coerceIn(lo, hi)
            fun col(k: String) = (m[k] as? Long)?.let { it and 0xFFFFFFFFL }
            return DecorItem(
                kind, glyph, at, (m["on"] as? String)?.takeIf { at == CELL && onProblem(it) == null && it.isNotEmpty() },
                int("x", 500, 0, 1000), int("y", 500, 0, 1000), int("w", 24, 2, MAX_SIZE), int("h", 24, 2, MAX_SIZE), int("rot", 0, 0, 359),
                col("fill"), int("fillA", 100, 0, 100), (m["line"] as? String)?.takeIf { it in LINES }, dbl("lineW", 0.0, 0.0, 8.0), col("lineC"),
                int("r", 0, 0, 48), int("shadow", 0, 0, 24), (m["font"] as? String)?.takeIf { fontProblem(it) == null && it.isNotEmpty() },
                dbl("size", 14.0, 6.0, 96.0), int("weight", 400, 100, 900), col("ink"), m["italic"] == true, int("alpha", 100, 0, 100),
                m["hide"] == true, m["over"] == true, m["flip"] == true, m["lock"] == true, int("group", 0, 0, 9),
                (m["tap"] as? String)?.takeIf { tapProblem(it) == null && it.isNotEmpty() },
                m["full"] == true && (at == APP || at.startsWith("bg.")),
            )
        }

        /** The piece's fields (core [PieceKind.DECOR], server decor.js the same). */
        val FIELDS: List<Field> = listOf(
            Field.Pick("shape", DecorGlyphs.SHAPE_LIST.map { it.id }, optional = true),
            Field.Pick("icon", DecorGlyphs.ICON_LIST.map { it.id }, optional = true),
            Field.Emoji("emoji", input = false),
            // The receiver may fill a text box in (이름·가게 이름 …), so the maker's words need not be theirs.
            Field.Text("text", MAX_TEXT, optional = true, input = true),
            Field.Pick("at", ANCHORS),
            Field.Text("on", 12, optional = true, guard = false, check = ::onProblem),
            Field.Num("x", 0.0, 1000.0), Field.Num("y", 0.0, 1000.0),
            Field.Num("w", 2.0, MAX_SIZE.toDouble()), Field.Num("h", 2.0, MAX_SIZE.toDouble()),
            Field.Num("rot", 0.0, 359.0, optional = true),
            Field.Color("fill", optional = true), Field.Num("fillA", 0.0, 100.0, optional = true),
            Field.Pick("line", LINES, optional = true), Field.Num("lineW", 0.0, 8.0, decimals = 1, optional = true), Field.Color("lineC", optional = true),
            Field.Num("r", 0.0, 48.0, optional = true), Field.Num("shadow", 0.0, 24.0, optional = true),
            Field.Text("font", 40, optional = true, guard = false, check = ::fontProblem),
            Field.Num("size", 6.0, 96.0, decimals = 1, optional = true), Field.Num("weight", 100.0, 900.0, optional = true),
            Field.Color("ink", optional = true), Field.Flag("italic", input = false),
            Field.Num("alpha", 0.0, 100.0, optional = true),
            Field.Flag("hide", input = false), Field.Flag("over", input = false), Field.Flag("flip", input = false),
            Field.Flag("lock", input = false), Field.Num("group", 0.0, 9.0, optional = true),
            Field.Text("photo", 16, optional = true, guard = false, check = ::photoProblem),
            Field.Text("tap", 12, optional = true, guard = false, check = ::tapProblem),
            Field.Flag("full", input = false),
        )
    }
}
