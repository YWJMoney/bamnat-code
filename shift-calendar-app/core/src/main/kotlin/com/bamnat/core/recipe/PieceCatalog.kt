package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle

/**
 * [RCP-02] 조각 사전 (설계서 4-1): every kind of piece a recipe may hold, each field with its type and range, and the kinds of
 * work it fits (유형 렌즈). The catalog *is* the format: a field not listed here is dropped on the phone and on the server
 * (결정 D106), a value out of range refuses the recipe. The server's `RECIPE_PIECES` (server/company-worker/src/recipes.js)
 * is the same table; `docs/recipe-cases.json` holds the two together.
 */
sealed interface Field {
    val key: String
    /** Absent is allowed (and means "leave mine as it is"). */
    val optional: Boolean

    /** A line of text; [lines] keeps line breaks. */
    data class Text(
        override val key: String, val max: Int, val min: Int = 0, val lines: Boolean = false, override val optional: Boolean = false,
        /** May be the receiver's answer ([InputKind.TEXT]). */
        val input: Boolean = false,
        /** A further check on the cleaned text: null passes, else the refusal's code. */
        val check: ((String) -> String?)? = null,
        /** Checked for email, phone, amount, link, chat ([RecipeText.problem]); off only for a company file. */
        val guard: Boolean = true,
    ) : Field

    /** A number; [decimals] 0 is a whole number. */
    data class Num(
        override val key: String, val lo: Double, val hi: Double, val decimals: Int = 0, override val optional: Boolean = false,
        val input: Boolean = false,
    ) : Field

    /** An amount of won: always the receiver's to decide unless the maker keeps it ([InputExtract]). */
    data class Money(override val key: String, val lo: Long = 0, val hi: Long = 100_000_000, override val optional: Boolean = false) : Field

    /** An ARGB colour, or a theme token's ([Ref.Token]), or the receiver's ([InputKind.COLOR]). */
    data class Color(override val key: String, override val optional: Boolean = false) : Field

    /** One emoji the app knows ([RecipeText.oneEmoji]). */
    data class Emoji(override val key: String, override val optional: Boolean = true, val input: Boolean = true) : Field

    data class Flag(override val key: String, override val optional: Boolean = true, val input: Boolean = true) : Field

    /** One of [values]. */
    data class Pick(override val key: String, val values: List<String>, override val optional: Boolean = false, val input: Boolean = false) : Field

    /** HH:mm. */
    data class Clock(override val key: String, override val optional: Boolean = false, val input: Boolean = true) : Field

    /** A list of short texts, each 1..[itemMax] letters. */
    data class Texts(override val key: String, val max: Int, val itemMax: Int, override val optional: Boolean = true) : Field

    /** A list of numbers. */
    data class Nums(override val key: String, val max: Int, val lo: Double, val hi: Double, val decimals: Int = 0, override val optional: Boolean = true) : Field

    /** A list of days of the week (MONDAY..SUNDAY), each once. */
    data class Days(override val key: String, override val optional: Boolean = true) : Field

    /** A list of objects of [fields], at most [max]. */
    data class Items(override val key: String, val max: Int, val fields: List<Field>, val min: Int = 0, override val optional: Boolean = false) : Field
}

/** The kinds of piece, each with the kinds of work it fits and the screen it comes from. */
enum class PieceKind(val key: String, val label: String, val styles: Set<WorkStyle>, val fields: List<Field>) {
    /** [RCP-08] 근무 꾸미기: names, letters, colours and emoji of the kinds of day. */
    SHIFT_LOOK(
        "shiftLook", "근무 꾸미기", setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY),
        listOf(
            Field.Items(
                "items", 12, min = 1,
                fields = listOf(
                    Field.Text("name", 20, min = 1),
                    Field.Pick("role", ShiftRole.KEYS),
                    Field.Text("label", 2, min = 1, check = { if (RecipeText.graphemes(it) > 2) "label" else null }),
                    Field.Color("color"),
                    Field.Emoji("emoji"),
                ),
            ),
        ),
    ),

    /** [RCP-09] 달력 테마: the calendar's own colours and how a day with a shift is drawn. */
    CAL_THEME(
        "calTheme", "달력 테마", WorkStyle.entries.toSet(),
        listOf(
            Field.Color("today", optional = true),
            Field.Color("sat", optional = true),
            Field.Color("sun", optional = true),
            Field.Color("holiday", optional = true),
            Field.Pick("cell", CalendarTheme.CELLS, optional = true),
        ),
    ),

    /** [RCP-10] 알람 묶음: alarms before each kind of shift, added to the receiver's (never one taken away, S3). */
    ALARMS(
        "alarms", "알람 묶음", setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY),
        listOf(
            Field.Items(
                "byRole", 6,
                fields = listOf(Field.Pick("role", ShiftRole.KEYS), Field.Nums("before", 4, 0.0, 720.0, optional = false)),
            ),
            Field.Num("snooze", 1.0, 30.0, optional = true),
            Field.Num("ring", 1.0, 30.0, optional = true),
            Field.Flag("gradual"),
            Field.Flag("vibrate"),
            Field.Pick("mission", listOf("none", "math", "shake"), optional = true),
        ),
    ),

    /** [RCP-11] 메모 틀: memos to start from (제목·체크 목록·반복). They are added as 틀, never as memos (S3). */
    MEMO_TEMPLATES(
        "memoTpl", "메모 틀", WorkStyle.entries.toSet(),
        listOf(
            Field.Items(
                "items", 8, min = 1,
                fields = listOf(
                    Field.Text("title", 30, min = 1),
                    Field.Emoji("emoji"),
                    Field.Texts("check", 20, 60),
                    Field.Text("body", 300, lines = true, optional = true),
                    Field.Text("repeat", 4, optional = true, check = { if (it.isEmpty() || REPEAT.matches(it)) null else "repeat" }),
                ),
            ),
        ),
    ),

    /** [RCP-12] 알바 근무지 규칙: how a part-time job pays, the 시급 always the receiver's. */
    HOURLY_RULES(
        "hourly", "알바 근무지 규칙", setOf(WorkStyle.HOURLY),
        listOf(
            Field.Money("wage", 0, 1_000_000, optional = true),
            Field.Flag("five"),
            Field.Flag("weeklyPay"),
            Field.Pick("weekStart", WEEK_DAYS, optional = true),
            Field.Pick("breakPay", listOf("UNPAID", "PAID", "AS_WORK"), optional = true),
            Field.Flag("legalBreaks"),
            Field.Num("night", 0.0, 200.0, optional = true),
            Field.Flag("partTime"),
            Field.Pick("rounding", listOf("HOURLY_FIRST", "AT_END", "AT_END_UP", "AT_END_DOWN"), optional = true),
            Field.Pick("tax", listOf("NONE", "WITHHOLDING_3_3", "INSURANCE_4"), optional = true),
        ),
    ),

    /** [RCP-13] 현장 단가표 틀: how a field calendar counts its work (FieldRules), the 일당 the receiver's; 작업·경비 이름 become 분류. */
    FIELD_SHEET(
        "field", "현장 단가표 틀", setOf(WorkStyle.FIELD),
        listOf(
            Field.Pick("mode", listOf("DAY_RATE", "PER_JOB", "PLATFORM")),
            Field.Money("dayRate", 0, 5_000_000, optional = true),
            Field.Nums("chips", 5, 0.1, 5.0, decimals = 2),
            Field.Items("ends", 6, optional = true, fields = listOf(Field.Clock("end"), Field.Num("units", 0.1, 5.0, decimals = 2))),
            Field.Texts("kinds", 12, 20),
            Field.Texts("costs", 12, 20),
        ),
    ),

    /** [RCP-14] 가게 영업 틀: open days and hours (ShopRules), card fee and settlement (ShopMoney), 마감 체크 and 경비 분류. */
    SHOP(
        "shop", "가게 영업 틀", setOf(WorkStyle.BUSINESS),
        listOf(
            Field.Days("open"),
            Field.Clock("opens", optional = true),
            Field.Clock("closes", optional = true),
            Field.Flag("holidaysClosed"),
            Field.Num("cardFee", 0.0, 5.0, decimals = 2, optional = true),
            Field.Num("settleDays", 0.0, 10.0, optional = true),
            Field.Flag("simpleTax"),
            Field.Num("valueAdded", 5.0, 40.0, optional = true),
            Field.Texts("closing", 20, 60),
            Field.Texts("costs", 20, 20),
        ),
    ),

    /** [RCP-15] 투자 원칙·일지 틀: 원칙 글 and the journal's fields. Never a stock pick (S6, 결정 AI-D8). */
    INVEST(
        "invest", "투자 원칙·일지 틀", setOf(WorkStyle.INVEST),
        listOf(
            Field.Items(
                "rules", 10, min = 1,
                fields = listOf(Field.Text("text", 80, min = 2, check = { if (RecipeText.investAdvice(it)) "invest_advice" else null })),
            ),
            Field.Texts("journal", 8, 20),
        ),
    ),

    /** [RCP-16] 근무 주기·급여 방식: the company file as the rule plaza shares it (bamnat-company), checked the same way. */
    COMPANY(
        "company", "근무 주기·급여 방식", setOf(WorkStyle.SHIFT, WorkStyle.DAY),
        listOf(Field.Text("setup", 24_000, min = 2, lines = true, guard = false, check = { CompanyPiece.check(it) })),
    ),

    /** [RCX-11] 1.35 묶음: other recipes by their codes, put in one after another (each piece still chosen on its own). */
    BUNDLE(
        "bundle", "레시피 묶음", WorkStyle.entries.toSet(),
        listOf(
            Field.Items(
                "items", 8, min = 2,
                fields = listOf(
                    Field.Text("code", 8, min = 8, guard = false, check = { if (RecipeCodec.CODE.matches(it)) null else "code" }),
                    Field.Text("note", 40, optional = true),
                ),
            ),
        ),
    ),

    /** [RCX-12] 1.35 화면 배치: the bottom tabs' order ([com.bamnat.core.layout.TabOrder]) and the calendar page's parts. */
    LAYOUT(
        "layout", "화면 배치", WorkStyle.entries.toSet(),
        listOf(
            Field.Texts("tabs", 5, 10),
            Field.Text("calendar", 400, optional = true, guard = false, check = ::layoutLine),
        ),
    ),

    /** [RCX-13] 1.35 통계·가계부·메모 카드: each page's parts in order and shown or not ([com.bamnat.core.layout.PageLayout]). */
    CARDS(
        "cards", "통계·가계부 카드", WorkStyle.entries.toSet(),
        listOf(
            Field.Text("stats", 400, optional = true, guard = false, check = ::layoutLine),
            Field.Text("money", 400, optional = true, guard = false, check = ::layoutLine),
            Field.Text("memo", 400, optional = true, guard = false, check = ::layoutLine),
        ),
    ),

    /** [RCX-14] 1.35 위젯 모양: how see-through the widgets are, light or dark, and the memo line. */
    WIDGET(
        "widget", "위젯 모양", WorkStyle.entries.toSet(),
        listOf(
            Field.Num("alpha", 30.0, 100.0, optional = true),
            Field.Pick("theme", listOf("system", "light", "dark"), optional = true),
            Field.Flag("memo"),
        ),
    );

    companion object {
        fun of(key: String): PieceKind? = entries.firstOrNull { it.key == key }
        val DAYS get() = WEEK_DAYS
    }
}

/** The days of the week as a recipe writes them. */
val WEEK_DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

/** A memo template's 반복: M<day of month> or W<1 Monday..7 Sunday> (Memo.repeat), or none. */
private val REPEAT = Regex("^(M([1-9]|[12][0-9]|3[01])|W[1-7])$")

/** A page's parts as [com.bamnat.core.layout.PageLayout] writes them ("hours,-rest,memos"): ids only, at most 40. */
private val LAYOUT_LINE = Regex("^-?[A-Za-z][A-Za-z0-9]{0,23}(,-?[A-Za-z][A-Za-z0-9]{0,23}){0,39}$")
internal fun layoutLine(s: String): String? = if (s.isEmpty() || LAYOUT_LINE.matches(s)) null else "layout"

/**
 * [RCP-08] What a kind of day is, so a recipe's 근무 꾸미기 meets the receiver's own (주간 ↔ 데이 ↔ D): its name first, then this.
 */
enum class ShiftRole(val key: String, val label: String) {
    DAY("day", "주간"), EVENING("eve", "오후·저녁"), NIGHT("night", "야간"), LONG("long", "24시간·당직"), OFF("off", "휴무·비번"), OTHER("other", "그 밖");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ShiftRole = entries.firstOrNull { it.key == key } ?: OTHER

        private val NAMES = listOf(
            NIGHT to Regex("야|나이트|night|^n$|밤|심야", RegexOption.IGNORE_CASE),
            OFF to Regex("휴|비번|off|쉬|오프|^o$|^x$", RegexOption.IGNORE_CASE),
            LONG to Regex("당직|24|맞교대|당번|full", RegexOption.IGNORE_CASE),
            EVENING to Regex("오후|저녁|이브|eve|^e$|석|중간", RegexOption.IGNORE_CASE),
            DAY to Regex("주|데이|day|^d$|오전|아침|일근|평일|근무", RegexOption.IGNORE_CASE),
        )

        /** A kind of day's role from its name, times and whether it is off (the 근무 꾸미기 piece matches by it). */
        fun guess(name: String, off: Boolean, startHour: Int?, minutes: Int): ShiftRole {
            if (off) return OFF
            if (minutes >= 20 * 60) return LONG
            NAMES.firstOrNull { it.second.containsMatchIn(name.trim()) }?.let { if (it.first != OFF) return it.first }
            return when (startHour) {
                null -> OTHER
                in 4..10 -> DAY
                in 11..16 -> EVENING
                else -> NIGHT
            }
        }
    }
}

/**
 * [RCP-09] 달력 테마 (1.34 새 기능): the calendar's own colours for today, 토·일·공휴일 and how a working day's cell is drawn. Kept
 * in 설정 as one line ([encode]); empty is the app's own look. The colours are as chosen for the light calendar; the dark one lifts
 * them itself so they read on black.
 */
data class CalendarTheme(
    val today: Long? = null,
    val sat: Long? = null,
    val sun: Long? = null,
    val holiday: Long? = null,
    /** One of [CELLS]; null is the app's own (tint). */
    val cell: String? = null,
) {
    val isDefault: Boolean get() = this == CalendarTheme()

    fun encode(): String = if (isDefault) "" else listOfNotNull(
        today?.let { "today=" + hex(it) }, sat?.let { "sat=" + hex(it) }, sun?.let { "sun=" + hex(it) },
        holiday?.let { "holiday=" + hex(it) }, cell?.let { "cell=$it" },
    ).joinToString(";")

    companion object {
        /** tint: a light wash with the shift's badge (the app's own); fill: a stronger wash; dot: no wash, the badge only. */
        val CELLS = listOf("fill", "tint", "dot")

        private fun hex(c: Long) = "%08X".format(c and 0xFFFFFFFFL)

        fun decode(s: String): CalendarTheme {
            if (s.isBlank()) return CalendarTheme()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun color(k: String) = m[k]?.toLongOrNull(16)?.let { it or 0xFF000000L }
            return CalendarTheme(color("today"), color("sat"), color("sun"), color("holiday"), m["cell"]?.takeIf { it in CELLS })
        }
    }
}

/** [RCP-16] The company file inside a recipe: the phone's own reader checks it whole when it is previewed. */
object CompanyPiece {
    /** A quick check of the text's shape (JSON object of format bamnat-company); null passes. */
    fun check(text: String): String? {
        val parsed = runCatching { com.bamnat.core.remote.Json.parse(text) }.getOrNull() as? Map<*, *> ?: return "setup"
        return if (parsed["format"] == "bamnat-company") null else "setup"
    }
}

/**
 * [RCX-14] 1.35 위젯 모양, as the settings keep it ("alpha=80;theme=dark"): how opaque the widgets' background is (30..100 %) and
 * whether it follows the phone's light or dark screen. Empty is the app's own (100 %, following the phone).
 */
data class WidgetLook(val alpha: Int = 100, val theme: String = "system") {
    val isDefault: Boolean get() = this == WidgetLook()

    fun encode(): String = if (isDefault) "" else "alpha=$alpha;theme=$theme"

    companion object {
        val THEMES = listOf("system", "light", "dark")

        fun decode(s: String): WidgetLook {
            if (s.isBlank()) return WidgetLook()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return WidgetLook(m["alpha"]?.toIntOrNull()?.coerceIn(30, 100) ?: 100, m["theme"]?.takeIf { it in THEMES } ?: "system")
        }
    }
}
