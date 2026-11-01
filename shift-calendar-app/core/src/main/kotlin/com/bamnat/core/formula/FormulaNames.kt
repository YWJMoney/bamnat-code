package com.bamnat.core.formula

/**
 * [RUL-01][RUL-05] 1.36 수식이 읽을 수 있는 값 (탭 사이 흐름: 달력 ↔ 메모 ↔ 가계부 ↔ 급여). Each has a Korean main name, other spellings
 * and an English one; the editor lists them by [group] with a line on what they are and an example. The app fills them in
 * (app `DayFacts`); a name not filled where a formula runs is 없음.
 */
enum class FormulaName(
    val ko: String,
    val en: String,
    val group: Group,
    val type: Type,
    val help: String,
    val sample: Any?,
    val also: List<String> = emptyList(),
) {
    DATE("날짜", "date", Group.DAY, Type.TEXT, "그날 날짜(2026-11-01)", "2026-11-14"),
    MONTH("월", "month", Group.DAY, Type.NUM, "몇 월", 11),
    DAY("일", "day", Group.DAY, Type.NUM, "며칠", 14),
    WEEKDAY("요일", "weekday", Group.DAY, Type.TEXT, "월·화·수·목·금·토·일", "금"),
    WEEKEND("주말", "weekend", Group.DAY, Type.BOOL, "토요일이나 일요일", false),
    HOLIDAY("공휴일", "holiday", Group.DAY, Type.BOOL, "공휴일인가", false),
    HOLIDAY_NAME("공휴일이름", "holidayName", Group.DAY, Type.TEXT, "공휴일 이름(아니면 빈칸)", ""),
    TODAY("오늘", "isToday", Group.DAY, Type.BOOL, "오늘인가", false),
    PAST("지난날", "isPast", Group.DAY, Type.BOOL, "오늘보다 앞날인가", false),
    DAYS_AWAY("며칠뒤", "daysAway", Group.DAY, Type.NUM, "오늘에서 며칠 뒤(앞날은 음수)", 3, listOf("남은날")),
    STYLE("유형", "workStyle", Group.DAY, Type.TEXT, "내 근무 유형(교대·일근·알바·현장·자영업·투자)", "교대"),

    SHIFT("근무", "shift", Group.WORK, Type.TEXT, "그날 근무 이름(없으면 빈칸)", "야간"),
    ROLE("근무종류", "shiftKind", Group.WORK, Type.TEXT, "주간·저녁·야간·당직·휴무·기타", "야간"),
    START("근무시작", "shiftStart", Group.WORK, Type.TEXT, "시작 시각(22:00)", "22:00"),
    END("근무끝", "shiftEnd", Group.WORK, Type.TEXT, "끝 시각(07:00)", "07:00"),
    HOURS("근무시간", "hours", Group.WORK, Type.NUM, "그날 일한 시간", 9),
    OFF("쉬는날", "isOff", Group.WORK, Type.BOOL, "근무가 없거나 쉬는 근무", false),
    LEAVE("휴가", "leave", Group.WORK, Type.TEXT, "연차·반차 같은 휴가 이름(없으면 빈칸)", ""),
    WEEK_NIGHTS("이번주야간", "weekNights", Group.WORK, Type.NUM, "그 주 야간 근무 수", 3),
    WEEK_HOURS("이번주근무시간", "weekHours", Group.WORK, Type.NUM, "그 주 일한 시간", 40),
    MONTH_NIGHTS("이달야간", "monthNights", Group.WORK, Type.NUM, "그달 야간 근무 수", 8),
    MONTH_HOURS("이달근무시간", "monthHours", Group.WORK, Type.NUM, "그달 일한 시간", 168),
    STREAK("연속근무", "streak", Group.WORK, Type.NUM, "그날까지 쉬지 않고 이어 일한 날 수", 4),

    MEMO_COUNT("메모수", "memoCount", Group.MEMO, Type.NUM, "그날 메모 수", 1),
    MEMO("메모", "memo", Group.MEMO, Type.TEXT, "그날 메모 글(제목과 내용)", "인수인계 #병원"),
    TAGS("꼬리표", "tags", Group.MEMO, Type.LIST, "그날 메모의 꼬리표(# 없이)", listOf("인수인계", "병원"), listOf("태그")),
    CHECK_RATE("체크율", "checkRate", Group.MEMO, Type.NUM, "그날 체크 목록을 끝낸 비율(0~100)", 60),
    CHECKS_LEFT("남은체크", "checksLeft", Group.MEMO, Type.NUM, "그날 아직 안 한 체크 수", 2),

    SPEND("지출", "spend", Group.MONEY, Type.NUM, "그날 쓴 돈", 52000),
    INCOME("수입", "income", Group.MONEY, Type.NUM, "그날 들어온 돈", 0),
    SPEND_KINDS("지출분류", "spendKinds", Group.MONEY, Type.LIST, "그날 쓴 돈의 분류", listOf("식비")),
    MONTH_SPEND("이달지출", "monthSpend", Group.MONEY, Type.NUM, "그달 쓴 돈(그날까지)", 640000),
    MONTH_INCOME("이달수입", "monthIncome", Group.MONEY, Type.NUM, "그달 들어온 돈(그날까지)", 0),

    MONTH_PAY("이달급여", "monthPay", Group.PAY, Type.NUM, "그달 예상 급여(세전)", 3120000),
    HOUR_PAY("그날급여", "dayPay", Group.PAY, Type.NUM, "그날 일로 번 돈(예상)", 135000),

    NOW("지금시각", "now", Group.TIME, Type.TEXT, "지금 시각(규칙이 돌 때, 21:30)", "21:30"),
    ;

    enum class Group(val label: String) { DAY("날짜"), WORK("근무"), MEMO("메모"), MONEY("가계부"), PAY("급여"), TIME("시각") }
    enum class Type(val label: String) { NUM("숫자"), TEXT("글"), BOOL("참·거짓"), LIST("목록") }
}

/** Names: their main spelling from any spelling, and a near one when a name is mistyped. */
object FormulaNames {
    private val BY: Map<String, String> = FormulaName.entries.flatMap { n ->
        (listOf(n.ko, n.en.lowercase()) + n.also).map { it to n.ko }
    }.toMap()

    fun main(name: String): String? = BY[name] ?: BY[name.lowercase()]

    fun of(main: String): FormulaName? = FormulaName.entries.firstOrNull { it.ko == main }

    /** A sample scope (the editor's 미리 보기 before there is a real day): every name's [FormulaName.sample]. */
    val SAMPLE: FormulaScope = FormulaScope.of(FormulaName.entries.associate { it.ko to it.sample })

    /** Every function's main name, other name, help and example, for the editor's list. */
    val FUNCTIONS: List<FunctionHelp> = Fn.entries.map { FunctionHelp(it.ko, it.en, it.help, it.example) }

    data class FunctionHelp(val ko: String, val en: String, val help: String, val example: String)

    /** The names and functions starting with [prefix] (자동 완성), names first. */
    fun complete(prefix: String, limit: Int = 8): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val names = FormulaName.entries.filter { it.ko.startsWith(prefix) || it.en.lowercase().startsWith(p) || it.also.any { a -> a.startsWith(prefix) } }.map { it.ko }
        val fns = Fn.entries.filter { it.ko.startsWith(prefix) || it.en.lowercase().startsWith(p) }.map { it.ko + "(" }
        return (names + fns).distinct().take(limit)
    }

    /** The nearest main name or function to a mistyped [word] (one letter off, or it starts the same), or null. */
    fun near(word: String): String? {
        val all = FormulaName.entries.map { it.ko } + Fn.entries.map { it.ko }
        return all.firstOrNull { dist(it, word) <= 1 } ?: all.firstOrNull { word.length >= 2 && it.startsWith(word.take(2)) }
    }

    private fun dist(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length]
    }
}

/**
 * [RUL-02] 글 속 수식 ("이번 주 야간 {이번주야간}번"): text with formulas in braces. `{{` and `}}` write a brace.
 */
object FormulaText {
    sealed interface Part {
        data class Plain(val text: String) : Part
        data class Calc(val formula: Compiled) : Part
    }

    /** The parts, or the first formula's error (its letters moved to where it sits in [src]). */
    fun parse(src: String): Result<List<Part>> {
        if (src.length > Formula.MAX_LEN) return Result.failure(FormulaError("long", "글이 너무 길어요(${Formula.MAX_LEN}자까지).", Formula.MAX_LEN, src.length))
        val out = ArrayList<Part>()
        val b = StringBuilder()
        var i = 0
        var calcs = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '{' && i + 1 < src.length && src[i + 1] == '{' -> { b.append('{'); i += 2 }
                c == '}' && i + 1 < src.length && src[i + 1] == '}' -> { b.append('}'); i += 2 }
                c == '{' -> {
                    val close = src.indexOf('}', i + 1)
                    if (close < 0) return Result.failure(FormulaError("brace", "'{'가 닫히지 않았어요.", i, src.length))
                    if (++calcs > 8) return Result.failure(FormulaError("big", "글 하나에 수식은 8개까지예요.", i, close + 1))
                    if (b.isNotEmpty()) { out += Part.Plain(b.toString()); b.clear() }
                    when (val p = Formula.parse(src.substring(i + 1, close))) {
                        is Parsed.Ok -> out += Part.Calc(p.formula)
                        is Parsed.Bad -> return Result.failure(FormulaError(p.error.code, p.error.messageKo, i + 1 + p.error.from, i + 1 + p.error.to))
                    }
                    i = close + 1
                }
                c == '}' -> return Result.failure(FormulaError("brace", "'}' 앞에 '{'가 없어요.", i, i + 1))
                else -> { b.append(c); i++ }
            }
        }
        if (b.isNotEmpty()) out += Part.Plain(b.toString())
        return Result.success(out)
    }

    fun problem(src: String): String? = (parse(src).exceptionOrNull() as? FormulaError)?.code

    /** The text in [scope]; a formula that fails leaves its place empty. */
    fun render(parts: List<Part>, scope: FormulaScope): String = parts.joinToString("") {
        when (it) {
            is Part.Plain -> it.text
            is Part.Calc -> it.formula.run(scope)?.text.orEmpty()
        }
    }.take(Formula.MAX_TEXT)

    fun render(src: String, scope: FormulaScope): String = parse(src).getOrNull()?.let { render(it, scope) } ?: src
}
