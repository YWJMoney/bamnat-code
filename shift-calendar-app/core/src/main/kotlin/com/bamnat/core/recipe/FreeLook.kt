package com.bamnat.core.recipe

/**
 * [FREE-01] 1.35 완전 자유 꾸미기 (사용자 2026-10-30 15:27 "한 칸을 가지고 꾸며서 전체 적용… 몇 개를 패턴으로 하는지도 자유롭게"): one day
 * cell's own design. Every part null is the app's own, so an empty look is the cell as 달력 모양 draws it. Written as "bg:FF112233|ba:40|
 * dp:tl|mk:line" ([encode]); the server reads the same words (server/company-worker/src/recipes.js cellLooks).
 */
data class CellLook(
    /** The cell's background: an ARGB colour, [SHIFT] (the day's kind of work's colour) or [NONE]. */
    val bg: String? = null,
    /** How strong [bg] is, 0..100 %. */
    val bgAlpha: Int? = null,
    val border: String? = null,
    val borderColor: Long? = null,
    /** Corner radius, 0..16 dp. */
    val corner: Int? = null,
    /** Where the date sits: one of [DATE_PLACES] (top/middle/bottom × left/centre/right). */
    val datePlace: String? = null,
    val dateSize: String? = null,
    val dateBold: Boolean? = null,
    val dateShape: String? = null,
    /** The date's own background on an ordinary day (today keeps the theme's today colour). */
    val dateFill: Long? = null,
    /** The date's colour on a weekday; 토·일·공휴일 keep the calendar theme's. */
    val dateColor: Long? = null,
    val mark: String? = null,
    val markPlace: String? = null,
    /** Inside room, 0..8 dp. */
    val pad: Int? = null,
) {
    val isDefault: Boolean get() = this == CellLook()

    fun encode(): String = listOfNotNull(
        bg?.let { "bg:$it" }, bgAlpha?.let { "ba:$it" }, border?.let { "bd:$it" }, borderColor?.let { "bc:" + hex(it) },
        corner?.let { "cr:$it" }, datePlace?.let { "dp:$it" }, dateSize?.let { "ds:$it" }, dateBold?.let { "dw:" + if (it) "b" else "n" },
        dateShape?.let { "dsh:$it" }, dateFill?.let { "df:" + hex(it) }, dateColor?.let { "dc:" + hex(it) }, mark?.let { "mk:$it" },
        markPlace?.let { "mp:$it" }, pad?.let { "pd:$it" },
    ).joinToString("|")

    companion object {
        const val SHIFT = "shift"
        const val NONE = "none"
        const val MAX = 6
        val BORDERS = listOf("none", "thin", "thick", "dash")
        val DATE_PLACES = listOf("tl", "tc", "tr", "ml", "mc", "mr", "bl", "bc", "br")
        val DATE_SIZES = listOf("s", "m", "l", "xl")
        val DATE_SHAPES = listOf("none", "circle", "square")
        /** big·small: the badge; line: a bar; dot: a small round dot; fill: the whole cell in the colour; none: hidden (TalkBack still says it). */
        val MARKS = listOf("big", "small", "line", "dot", "fill", "none")
        /** side: beside the date; below: under the date; bottom: at the cell's foot. */
        val MARK_PLACES = listOf("side", "below", "bottom")

        private val HEX = Regex("^[0-9A-F]{8}$")
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        private fun color(s: String?): Long? = s?.takeIf { HEX.matches(it) }?.toLongOrNull(16)?.let { it or 0xFF000000L }
        private fun int(s: String?, lo: Int, hi: Int): Int? = s?.toIntOrNull()?.takeIf { it in lo..hi }

        /** One look from its words; a part it does not know or out of range is the app's own. */
        fun decode(s: String): CellLook {
            val m = s.split('|').mapNotNull { kv -> kv.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return CellLook(
                bg = m["bg"]?.takeIf { it == SHIFT || it == NONE || HEX.matches(it) },
                bgAlpha = int(m["ba"], 0, 100), border = m["bd"]?.takeIf { it in BORDERS }, borderColor = color(m["bc"]),
                corner = int(m["cr"], 0, 16), datePlace = m["dp"]?.takeIf { it in DATE_PLACES }, dateSize = m["ds"]?.takeIf { it in DATE_SIZES },
                dateBold = when (m["dw"]) { "b" -> true; "n" -> false; else -> null }, dateShape = m["dsh"]?.takeIf { it in DATE_SHAPES },
                dateFill = color(m["df"]), dateColor = color(m["dc"]), mark = m["mk"]?.takeIf { it in MARKS },
                markPlace = m["mp"]?.takeIf { it in MARK_PLACES }, pad = int(m["pd"], 0, 8),
            )
        }

        /** Several looks, "/" between them, at most [MAX]. */
        fun decodeAll(s: String?): List<CellLook> = s.orEmpty().split('/').filter { it.isNotEmpty() }.take(MAX).map(::decode)

        fun encodeAll(looks: List<CellLook>): String = looks.take(MAX).joinToString("/") { it.encode() }

        private val PART = Regex(
            "^(bg:([0-9A-F]{8}|shift|none)|ba:(100|[1-9]?[0-9])|bd:(none|thin|thick|dash)|bc:[0-9A-F]{8}|cr:(1[0-6]|[0-9])|" +
                "dp:(tl|tc|tr|ml|mc|mr|bl|bc|br)|ds:(s|m|l|xl)|dw:[nb]|dsh:(none|circle|square)|df:[0-9A-F]{8}|dc:[0-9A-F]{8}|" +
                "mk:(big|small|line|dot|fill|none)|mp:(side|below|bottom)|pd:[0-8])$",
        )

        /** A recipe's looks are each part known, each key once, at most [MAX] looks; null passes, else the refusal's code. */
        fun looksProblem(s: String): String? {
            if (s.isEmpty()) return null
            val looks = s.split('/')
            if (looks.size > MAX) return "looks"
            for (l in looks) {
                if (l.isEmpty()) continue
                val parts = l.split('|')
                if (!parts.all { PART.matches(it) }) return "looks"
                val keys = parts.map { it.substringBefore(':') }
                if (keys.size != keys.distinct().size) return "looks"
            }
            return null
        }
    }
}

/**
 * [FREE-02] How the looks take turns over the month: all the first one ([ONE]), N in a row by date ("cycle:2" — two give a
 * checkerboard, since a week is seven days), by the day of the week ("week:0,0,0,0,0,1,1" Monday first), or by the kind of day
 * ("shift:day=0,night=1,off=2"). [today] may give today its own look. An index past the looks there are falls back to the first.
 */
data class CellPattern(val mode: String = ONE, val n: Int = 2, val week: List<Int> = List(7) { 0 }, val shift: Map<String, Int> = emptyMap()) {
    val isDefault: Boolean get() = mode == ONE

    fun encode(): String = when (mode) {
        CYCLE -> "cycle:$n"
        WEEK -> "week:" + week.joinToString(",")
        SHIFT -> "shift:" + shift.entries.sortedBy { ShiftRole.KEYS.indexOf(it.key) }.joinToString(",") { "${it.key}=${it.value}" }
        else -> ONE
    }

    /** The look's index for [epochDay] ([dayOfWeek] 1 Monday..7 Sunday, [role] the day's kind) among [count] looks. */
    fun indexFor(epochDay: Long, dayOfWeek: Int, role: String?, count: Int): Int {
        if (count <= 1) return 0
        val i = when (mode) {
            CYCLE -> Math.floorMod(epochDay, n.toLong()).toInt()
            WEEK -> week.getOrElse(dayOfWeek - 1) { 0 }
            SHIFT -> role?.let { shift[it] } ?: 0
            else -> 0
        }
        return if (i in 0 until count) i else 0
    }

    companion object {
        const val ONE = "one"
        const val CYCLE = "cycle"
        const val WEEK = "week"
        const val SHIFT = "shift"
        val MODES = listOf(ONE, CYCLE, WEEK, SHIFT)
        private val RULE = Regex("^(one|cycle:[2-6]|week:[0-5](,[0-5]){6}|shift:(day|eve|night|long|off|other)=[0-5](,(day|eve|night|long|off|other)=[0-5]){0,5})$")

        fun problem(s: String): String? = if (s.isEmpty() || RULE.matches(s)) null else "pattern"

        fun decode(s: String?): CellPattern {
            if (s.isNullOrEmpty() || problem(s) != null) return CellPattern()
            val (mode, rest) = s.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            return when (mode) {
                CYCLE -> CellPattern(CYCLE, n = rest.toInt())
                WEEK -> CellPattern(WEEK, week = rest.split(',').map { it.toInt() })
                SHIFT -> CellPattern(SHIFT, shift = rest.split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() })
                else -> CellPattern()
            }
        }
    }
}

/**
 * [FREE-04] 1.35 메모장 모양 ("layout=sticky;card=own;lines=4;font=hand;bg=FFFFF8E1"): how the memo screen lays its memos out and how a
 * memo card looks. Each part null is the app's own (a list of the memos' own colours).
 */
data class MemoLook(
    val layout: String? = null,
    val card: String? = null,
    val cardColor: Long? = null,
    val border: String? = null,
    val corner: Int? = null,
    val lines: Int? = null,
    val title: String? = null,
    val bold: Boolean? = null,
    val font: String? = null,
    val date: Boolean? = null,
    val bg: Long? = null,
) {
    val isDefault: Boolean get() = this == MemoLook()

    /** Columns the layout gives on a phone held upright. */
    val columns: Int get() = when (layout) { "grid2", "sticky" -> 2; "grid3" -> 3; else -> 1 }

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        layout?.let { "layout=$it" }, card?.let { "card=$it" }, cardColor?.let { "cardColor=" + hex(it) }, border?.let { "border=$it" },
        corner?.let { "corner=$it" }, lines?.let { "lines=$it" }, title?.let { "title=$it" }, bold?.let { "bold=" + if (it) 1 else 0 },
        font?.let { "font=$it" }, date?.let { "date=" + if (it) 1 else 0 }, bg?.let { "bg=" + hex(it) },
    ).joinToString(";")

    companion object {
        /** list (the app's own), grid2·grid3, sticky (tilted coloured notes), diary (ruled paper), compact (one line each). */
        val LAYOUTS = listOf("list", "grid2", "grid3", "sticky", "diary", "compact")
        /** own: each memo's own colour (the app's own); one: [cardColor] for all; none: no card, a line between memos. */
        val CARDS = listOf("own", "one", "none")
        val BORDERS = listOf("none", "thin")
        val TITLES = listOf("s", "m", "l")
        val FONTS = listOf("sans", "serif", "mono", "hand")
        const val MAX_LINES = 10

        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

        val PRESETS: List<Pair<String, MemoLook>> = listOf(
            "기본" to MemoLook(),
            "포스트잇" to MemoLook(layout = "sticky", card = "own", corner = 2, lines = 6, font = "hand"),
            "격자" to MemoLook(layout = "grid2", lines = 5),
            "다이어리" to MemoLook(layout = "diary", card = "none", font = "serif", lines = 8, bg = 0xFFFFFBF0),
            "촘촘히" to MemoLook(layout = "compact", lines = 1, date = false),
        )

        fun decode(s: String): MemoLook {
            if (s.isBlank()) return MemoLook()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun color(k: String) = m[k]?.toLongOrNull(16)?.let { it or 0xFF000000L }
            fun flag(k: String) = when (m[k]) { "1" -> true; "0" -> false; else -> null }
            return MemoLook(
                layout = m["layout"]?.takeIf { it in LAYOUTS }, card = m["card"]?.takeIf { it in CARDS }, cardColor = color("cardColor"),
                border = m["border"]?.takeIf { it in BORDERS }, corner = m["corner"]?.toIntOrNull()?.coerceIn(0, 24),
                lines = m["lines"]?.toIntOrNull()?.coerceIn(1, MAX_LINES), title = m["title"]?.takeIf { it in TITLES }, bold = flag("bold"),
                font = m["font"]?.takeIf { it in FONTS }, date = flag("date"), bg = color("bg"),
            )
        }
    }
}

/**
 * [FREE-05] 1.35 앱 색 ("bg=FFFDF6E3;card=FFFFFFFF;ink=FF222222;dark=0"): the app's background, cards and text in the user's colours.
 * [dark] true uses them on the dark screen too; else the dark screen keeps the app's own. The text is never left hard to read:
 * [inkOn] picks black or white when the chosen ink does not stand 4.5:1 against the background.
 */
data class Palette(val bg: Long? = null, val card: Long? = null, val ink: Long? = null, val dark: Boolean = false) {
    val isDefault: Boolean get() = bg == null && card == null && ink == null

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        bg?.let { "bg=" + hex(it) }, card?.let { "card=" + hex(it) }, ink?.let { "ink=" + hex(it) }, "dark=1".takeIf { dark },
    ).joinToString(";")

    companion object {
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

        fun decode(s: String): Palette {
            if (s.isBlank()) return Palette()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun color(k: String) = m[k]?.toLongOrNull(16)?.let { it or 0xFF000000L }
            return Palette(color("bg"), color("card"), color("ink"), m["dark"] == "1")
        }

        /** WCAG relative luminance of an ARGB colour. */
        fun luminance(c: Long): Double {
            fun ch(v: Long): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
        }

        fun contrast(a: Long, b: Long): Double {
            val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
            return (hi + 0.05) / (lo + 0.05)
        }

        /** [ink] when it reads on every one of [grounds] (4.5:1), else black or white, whichever reads better on them all. */
        fun inkOn(ink: Long?, vararg grounds: Long): Long {
            if (ink != null && grounds.all { contrast(ink, it) >= 4.5 }) return ink
            val black = 0xFF1B1B1BL
            val white = 0xFFFFFFFFL
            return if (grounds.minOf { contrast(black, it) } >= grounds.minOf { contrast(white, it) }) black else white
        }
    }
}

/**
 * [FREE-05] 1.35 화면 색·배경 (사용자 2026-10-30 15:30 "제한을 두는 탭은 디자인 일관성을 위해 배경과 색상은 바꿀 수 있게"): one screen's
 * background colour or picture, its cards, text and accent. Every screen has one, the restricted ones too (알람 끄기·잠금·결제·권한·처리방침
 * keep their layout; only these colours and the background change). [emoji] decorates the screen's top (광장's own choice).
 */
data class ScreenLook(
    val bg: Long? = null,
    /** A picture's id (16 hex) under files/looks, as 그림 꾸미기 keeps them. */
    val photo: String? = null,
    val dim: Int? = null,
    val card: Long? = null,
    val ink: Long? = null,
    val accent: Long? = null,
    val emoji: String? = null,
) {
    val isDefault: Boolean get() = this == ScreenLook()

    fun encode(): String = listOfNotNull(
        bg?.let { "bg:" + hex(it) }, photo?.let { "ph:$it" }, dim?.let { "dm:$it" }, card?.let { "cd:" + hex(it) }, ink?.let { "ik:" + hex(it) },
        accent?.let { "ac:" + hex(it) }, emoji?.let { "em:" + it.codePoints().toArray().joinToString("+") { cp -> cp.toString(16) } },
    ).joinToString("|")

    /** This look over [base] (the whole app's): what this one leaves out is the base's. */
    fun over(base: ScreenLook): ScreenLook = ScreenLook(
        bg ?: base.bg, if (bg != null && photo == null) null else photo ?: base.photo, dim ?: base.dim, card ?: base.card, ink ?: base.ink,
        accent ?: base.accent, emoji ?: base.emoji,
    )

    companion object {
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        private val HEX = Regex("^[0-9A-F]{8}$")
        private val ID = Regex("^[0-9a-f]{16}$")
        private fun color(s: String?): Long? = s?.takeIf { HEX.matches(it) }?.toLongOrNull(16)?.let { it or 0xFF000000L }

        fun decode(s: String): ScreenLook {
            val m = s.split('|').mapNotNull { kv -> kv.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return ScreenLook(
                color(m["bg"]), m["ph"]?.takeIf { ID.matches(it) }, m["dm"]?.toIntOrNull()?.coerceIn(0, 80), color(m["cd"]), color(m["ik"]), color(m["ac"]),
                m["em"]?.let { e ->
                    runCatching { e.split('+').map { it.toInt(16) }.let { cps -> String(cps.toIntArray(), 0, cps.size) } }.getOrNull()
                        ?.let { RecipeText.oneEmoji(it) }
                },
            )
        }

        val PART = Regex("^(bg:[0-9A-F]{8}|ph:[0-9a-f]{16}|dm:([0-7]?[0-9]|80)|cd:[0-9A-F]{8}|ik:[0-9A-F]{8}|ac:[0-9A-F]{8}|em:[0-9a-f]{2,6}(\\+[0-9a-f]{2,6}){0,7})$")
    }
}

/**
 * [FREE-05] Every screen's [ScreenLook] by its key ([KEYS]; 급여 by kind of work, "pay.SHIFT"), "all" under the rest. Written as
 * "all~bg:FFFDF6E3/calendar~cd:FFFFFFFF|ik:FF222222/pay.FIELD~ac:FF00857A".
 */
data class ScreenLooks(val looks: Map<String, ScreenLook> = emptyMap()) {
    val isDefault: Boolean get() = looks.values.all { it.isDefault }

    /** The look [key] is drawn with: its own over "all"; 급여 of a kind of work over 급여 over "all". */
    fun of(key: String): ScreenLook {
        val all = looks[ALL] ?: ScreenLook()
        val parent = key.substringBefore('.').takeIf { it != key }?.let { looks[it] }
        val mid = parent?.over(all) ?: all
        return looks[key]?.over(mid) ?: mid
    }

    fun with(key: String, look: ScreenLook): ScreenLooks = ScreenLooks(if (look.isDefault) looks - key else looks + (key to look))

    /** Every picture the looks use, so the ones nothing uses can be let go. */
    val images: Set<String> get() = looks.values.mapNotNull { it.photo }.toSet()

    fun encode(): String = looks.filterValues { !it.isDefault }.entries.sortedBy { KEYS.indexOf(it.key.substringBefore('.')) * 10 + (if ('.' in it.key) 1 else 0) }
        .joinToString("/") { "${it.key}~${it.value.encode()}" }

    companion object {
        const val ALL = "all"
        /**
         * The screens. 광장 takes only a background and an emoji (사용자 2026-10-30: 광장은 배경이나 이모티콘에 한해서), and the restricted
         * ones — 알람 끄기 ([ALARM]), 잠금, 결제, 권한, 처리방침 ([GUARDED]) — only colours and a background, never their layout.
         */
        val KEYS = listOf(ALL, "calendar", "money", "memo", "stats", "pay", "plaza", "settings", "alarm", "guarded")
        const val ALARM = "alarm"
        const val GUARDED = "guarded"
        const val PLAZA = "plaza"
        val PAY_STYLES = com.bamnat.core.work.WorkStyle.entries.map { "pay.${it.name}" }
        private val KEY = Regex("^(all|calendar|money|memo|stats|pay|plaza|settings|alarm|guarded|pay\\.(SHIFT|DAY|HOURLY|FIELD|BUSINESS|INVEST))$")

        fun decode(s: String?): ScreenLooks = ScreenLooks(
            s.orEmpty().split('/').mapNotNull { e ->
                val k = e.substringBefore('~', "")
                if (!KEY.matches(k)) null else k to ScreenLook.decode(e.substringAfter('~'))
            }.toMap().filterValues { !it.isDefault },
        )

        /** A recipe's screens: known keys, each once, each part known; null passes. */
        fun problem(s: String): String? {
            if (s.isEmpty()) return null
            val entries = s.split('/')
            if (entries.size > KEYS.size + PAY_STYLES.size) return "screens"
            val keys = entries.map { it.substringBefore('~', "") }
            if (keys.any { !KEY.matches(it) } || keys.size != keys.distinct().size) return "screens"
            for (e in entries) {
                val parts = e.substringAfter('~').split('|').filter { it.isNotEmpty() }
                if (!parts.all { ScreenLook.PART.matches(it) }) return "screens"
                if (parts.map { it.substringBefore(':') }.let { it.size != it.distinct().size }) return "screens"
            }
            return null
        }
    }
}

/**
 * [FREE-06] 1.35 가계부 모양 ("rows=roomy;amount=l;mark=emoji;income=FF1B7F3B;expense=FFC4261A"): how the money list reads. Each part
 * null is the app's own. The colours keep 4.5:1 against the cards (the app lifts or deepens them if not).
 */
data class MoneyLook(
    val rows: String? = null,
    val amount: String? = null,
    val mark: String? = null,
    val income: Long? = null,
    val expense: Long? = null,
    val dayHead: String? = null,
) {
    val isDefault: Boolean get() = this == MoneyLook()

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        rows?.let { "rows=$it" }, amount?.let { "amount=$it" }, mark?.let { "mark=$it" }, income?.let { "income=" + hex(it) },
        expense?.let { "expense=" + hex(it) }, dayHead?.let { "dayHead=$it" },
    ).joinToString(";")

    companion object {
        val ROWS = listOf("compact", "normal", "roomy")
        val AMOUNTS = listOf("s", "m", "l")
        /** How a record's kind is marked: its emoji (the app's own), a colour dot, or nothing. */
        val MARKS = listOf("emoji", "dot", "none")
        /** A day's heading: a line (the app's own), a band of colour, or a big date. */
        val DAY_HEADS = listOf("line", "band", "big")
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

        fun decode(s: String): MoneyLook {
            if (s.isBlank()) return MoneyLook()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun color(k: String) = m[k]?.toLongOrNull(16)?.let { it or 0xFF000000L }
            return MoneyLook(
                m["rows"]?.takeIf { it in ROWS }, m["amount"]?.takeIf { it in AMOUNTS }, m["mark"]?.takeIf { it in MARKS },
                color("income"), color("expense"), m["dayHead"]?.takeIf { it in DAY_HEADS },
            )
        }
    }
}

/**
 * [FREE-07] 1.35 급여 모양, by kind of work (사용자 2026-10-30 "급여(이것은 각 근무 유형별로)"): how big the month's total is, the
 * colours of what is added and taken off, how close the lines sit, and whether the 풀이 and the day-by-day list start open.
 * The figures, their 예상·추정 marks and the law's notes always show, whatever the look.
 */
data class PayLook(
    val total: String? = null,
    val plus: Long? = null,
    val minus: Long? = null,
    val rows: String? = null,
    val open: Boolean? = null,
    val days: Boolean? = null,
) {
    val isDefault: Boolean get() = this == PayLook()

    fun encode(): String = listOfNotNull(
        total?.let { "tt:$it" }, plus?.let { "pl:" + hex(it) }, minus?.let { "mn:" + hex(it) }, rows?.let { "rw:$it" },
        open?.let { "op:" + if (it) 1 else 0 }, days?.let { "dy:" + if (it) 1 else 0 },
    ).joinToString("|")

    companion object {
        val TOTALS = listOf("m", "l", "xl")
        val ROWS = listOf("compact", "normal", "roomy")
        private fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        private val HEX = Regex("^[0-9A-F]{8}$")
        private fun color(s: String?) = s?.takeIf { HEX.matches(it) }?.toLongOrNull(16)?.let { it or 0xFF000000L }
        val PART = Regex("^(tt:(m|l|xl)|pl:[0-9A-F]{8}|mn:[0-9A-F]{8}|rw:(compact|normal|roomy)|op:[01]|dy:[01])$")

        fun decode(s: String): PayLook {
            val m = s.split('|').mapNotNull { kv -> kv.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return PayLook(
                m["tt"]?.takeIf { it in TOTALS }, color(m["pl"]), color(m["mn"]), m["rw"]?.takeIf { it in ROWS },
                m["op"]?.let { it == "1" }, m["dy"]?.let { it == "1" },
            )
        }

        /** By kind of work ("SHIFT~tt:xl/FIELD~pl:FF1B7F3B"). */
        fun decodeAll(s: String?): Map<com.bamnat.core.work.WorkStyle, PayLook> = s.orEmpty().split('/').mapNotNull { e ->
            val style = com.bamnat.core.work.WorkStyle.entries.firstOrNull { it.name == e.substringBefore('~', "") } ?: return@mapNotNull null
            style to decode(e.substringAfter('~'))
        }.toMap().filterValues { !it.isDefault }

        fun encodeAll(m: Map<com.bamnat.core.work.WorkStyle, PayLook>): String =
            m.filterValues { !it.isDefault }.entries.sortedBy { it.key.ordinal }.joinToString("/") { "${it.key.name}~${it.value.encode()}" }

        fun problem(s: String): String? {
            if (s.isEmpty()) return null
            val entries = s.split('/')
            val keys = entries.map { it.substringBefore('~', "") }
            if (keys.any { k -> com.bamnat.core.work.WorkStyle.entries.none { it.name == k } } || keys.size != keys.distinct().size) return "pay"
            for (e in entries) {
                val parts = e.substringAfter('~').split('|').filter { it.isNotEmpty() }
                if (!parts.all { PART.matches(it) } || parts.map { it.substringBefore(':') }.let { it.size != it.distinct().size }) return "pay"
            }
            return null
        }
    }
}
