package com.bamnat.core.pay

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PAYS-06] One pay rule read from a sentence, as a change to the pay setup. Each one says in words what it
 * does ([summary]); nothing is applied until the user agrees, with [RuleText.apply].
 */
sealed interface RuleChange {
    val summary: String

    data class Base(val type: WageType, val amount: Long) : RuleChange {
        override val summary get() = "${type.label()} ${won(amount)}"
    }
    data class Annual(val yearly: Long) : RuleChange {
        override val summary get() = "연봉 ${won(yearly)} (월 기본급 ${won(AnnualPay(yearly).breakdown().monthlyBase)})"
    }
    data class Allowance(val name: String, val amount: Long, val cadence: Cadence, val shiftTypeIds: Set<Long> = emptySet(), val ordinary: Boolean? = null) : RuleChange {
        override val summary get() = "$name ${cadence.label} ${won(amount)}" + when (ordinary) { true -> " · 통상임금"; false -> " · 통상임금 제외"; null -> "" }
    }
    data class Bonus(
        val name: String, val yearlyPercent: Double?, val amount: Long?, val months: Set<Int>, val festivals: Set<Festival> = emptySet(),
        /** [PHOTO-04] What the percent is of: "연 400%(통상임금)" is [BonusBasis.ORDINARY]. */
        val basis: BonusBasis = BonusBasis.BASE,
    ) : RuleChange {
        override val summary get() = "$name " + (yearlyPercent?.let { "연 ${ExtraRule.percent(it / 100)}" + if (basis != BonusBasis.BASE) "(${basis.label})" else "" } ?: "1회 ${won(amount ?: 0)}") +
            " · " + (months.sorted().joinToString(",").takeIf { it.isNotEmpty() }?.let { "${it}월" } ?: "") +
            festivals.joinToString("") { (if (months.isEmpty()) "" else "·") + it.label }
    }
    data class Premium(val target: PremiumTarget, val premium: Double, val over8h: Boolean = false) : RuleChange {
        override val summary get() = "${target.label}${if (over8h) " 8시간 초과" else ""} 가산 ${ExtraRule.percent(premium)}"
    }
    data class NightWindow(val start: LocalTime, val end: LocalTime) : RuleChange {
        override val summary get() = "야간 시간 ${RuleWhen.clock(start)}~${RuleWhen.clock(end)}시"
    }
    data class MonthlyHours(val hours: Int) : RuleChange {
        override val summary get() = "월 소정근로시간 ${hours}시간"
    }
    data class FiveOrMore(val value: Boolean) : RuleChange {
        override val summary get() = if (value) "직원 5명 이상" else "직원 5명 미만"
    }
    data class Breaks(val mode: BreakPay) : RuleChange {
        override val summary get() = "휴게 ${mode.label}"
    }
    data class PublicHolidayAs(val kind: Long, val kindName: String) : RuleChange {
        override val summary get() = "공휴일 근무는 $kindName 방식으로"
    }
    /** [HOLI-01] "빨간날도 교대제라 평일처럼". */
    data class HolidayMode(val mode: HolidayWork) : RuleChange {
        override val summary get() = "빨간날 근무는 ${mode.label}"
    }
    data class PublicHolidaysPaid(val value: Boolean) : RuleChange {
        override val summary get() = if (value) "공휴일 근무는 휴일근로" else "공휴일은 다른 날로 바꿔 쉼(휴일 대체)"
    }
    data class RestDay(val mode: RestDayWork) : RuleChange {
        override val summary get() = "쉬는 날 근무는 ${mode.label}"
    }
    data class ScheduleRegular(val value: Boolean) : RuleChange {
        override val summary get() = if (value) "근무표 시간은 모두 기본 근무" else "8시간 넘는 근무표 시간은 연장"
    }
    data class WeeklyHolidayDay(val day: DayOfWeek) : RuleChange {
        override val summary get() = "주휴일 ${RuleWhen.DAY_NAMES.getValue(day)}요일"
    }
    data class Raise(val month: Int, val percent: Double) : RuleChange {
        override val summary get() = "매년 ${month}월 ${"%.1f".format(percent).removeSuffix(".0")}% 인상"
    }
    data class Extra(val kind: Long, val kindName: String, val rule: ExtraRule) : RuleChange {
        override val summary get() = "$kindName: ${rule.describe()}"
    }
    data class Custom(val rule: CustomRule, val shiftNames: Map<Long, String> = emptyMap()) : RuleChange {
        override val summary get() = rule.describe(shiftNames)
    }
    /** [PHOTO-12] "통상임금: 기본급, 직책수당, 자격수당, 기술전수교육수당": these of the user's 수당 count in 통상임금. */
    data class OrdinaryItems(val names: List<String>) : RuleChange {
        override val summary get() = "통상임금에 드는 것: " + names.joinToString(", ")
    }
    /** [PHOTO-12] 세금 산정 기준 (부양가족 본인1 배우자1 직계비속1, 만8~20세 자녀 0): 간이세액표 가족 수. */
    data class TaxFamily(val family: Int, val children: Int) : RuleChange {
        override val summary get() = "세금 계산 가족 ${family}명 (8~20세 자녀 ${children}명)"
    }
    /**
     * [PHOTO-11] 통상시급 worked out from a payslip's own premium lines (휴일근로 81시간 2,626,592원 → 21,618.04원), and whether
     * each line's won are rounded up as that payslip's are.
     */
    data class OrdinaryHourly(val hourly: Double, val roundUp: Boolean) : RuleChange {
        override val summary get() = "명세서로 보면 통상시급 ${won(kotlin.math.round(hourly).toLong())}이에요" + if (roundUp) " (원 미만 올림)" else ""
    }
}

private fun WageType.label() = when (this) {
    WageType.MONTHLY -> "기본급"
    WageType.HOURLY -> "시급"
    WageType.DAILY -> "일당"
}

/** [PAYS-06] A change read from [sourceLine]; low [confidence] ones start unticked. */
data class RuleFinding(val sourceLine: String, val change: RuleChange, val confidence: Double)

/**
 * [PAYS-06] Reads pay rules written in plain words and turns each into a [RuleChange]: the pay itself, allowances
 * and bonuses, the premiums and night hours, how the company counts days off, 공휴일 and breaks, how each kind of
 * extra work is paid ("대근은 8시간에 기본급의 10%, 8시간 넘으면 50% 가산, 야간 50% 가산"), and any rule of the
 * company's own as a [CustomRule] ("토요일 근무는 시간당 50% 더", "19~23시 근무 25% 가산"). Sentences can be
 * pasted from a 취업규칙, a contract or a message from a colleague. Lines that are payslip amounts
 * (연장근로수당 523,000원) are left to [PayslipReader], so they are never counted twice. Plain pattern
 * matching, no network.
 */
object RuleText {
    private val percentRe = Regex("""(\d+(?:\.\d+)?)\s*(%|퍼센트|프로)""")
    private val timesRe = Regex("""(\d+(?:\.\d+)?)\s*배""")
    private val hoursRe = Regex("""(\d{1,2}(?:\.\d+)?)\s*시간""")
    private val clockRangeRe = Regex(
        """(?:(오전|오후|밤|새벽|저녁)\s*)?(\d{1,2})(?::(\d{2})|\s*시(?:\s*(\d{1,2})\s*분)?)?\s*(?:~|-|–|부터|에서)\s*(?:(오전|오후|밤|새벽|저녁)\s*)?(\d{1,2})(?::(\d{2})|\s*시(?:\s*(\d{1,2})\s*분)?)\s*(?:까지)?""",
    )
    private val monthListRe = Regex("""(\d{1,2})\s*월""")

    fun read(text: String, kinds: List<ExtraKind> = emptyList(), shiftTypes: List<ShiftType> = emptyList(), fiveOrMore: Boolean = true): List<RuleFinding> =
        sentences(tidy(text)).flatMap { readSentence(it, kinds, shiftTypes, fiveOrMore) } + listOfNotNull(taxFamily(text))

    /**
     * [PHOTO-12] 1.29 AQ: a 세금 산정 기준 table (화학 공장 명세서의 계산 방법 사진): heads 본인·배우자·직계존속·직계비속·만8세 이상
     * 20세 이하 자녀 on one line, their numbers on a line under it, then 원천징수 비율. 공제대상가족 is 본인 + 배우자 + 직계존속 +
     * 직계비속 (the children of 8 to 20 are among them).
     */
    internal fun taxFamily(text: String): RuleFinding? {
        val lines = text.lines()
        val first = lines.indexOfFirst { l -> FAMILY_HEADS.count { (re, _) -> re.containsMatchIn(l.replace(" ", "")) } >= 2 }
        if (first < 0) return null
        // The numbers' line: small numbers, maybe a stray letter of a head beside them ("녀  1  1  0  1  0  100%").
        val small = Regex("""^\d{1,2}$""")
        val vi = (first + 1..minOf(lines.lastIndex, first + 4)).firstOrNull { i ->
            val t = lines[i].trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            t.count { small.matches(it) } >= 3 && t.none { w -> w.count { it in '가'..'힣' } >= 2 }
        } ?: return null
        val values = lines[vi].trim().split(Regex("""\s+""")).filter { small.matches(it) }.map { it.toInt() }
        // The heads, from the lines above the numbers (a photo can put one of them on a line of its own), in the form's order.
        val heads = (first until vi).flatMap { i -> lines[i].replace(" ", "").let { l -> FAMILY_HEADS.filter { (re, _) -> re.containsMatchIn(l) }.map { it.second } } }
            .distinct().sorted()
        if (heads.size < 2 || "본인" != FAMILY_ORDER.getOrNull(heads.first())) return null
        var family = 0
        var children = 0
        heads.forEachIndexed { i, rank ->
            val v = values.getOrNull(i) ?: return@forEachIndexed
            if (FAMILY_ORDER[rank] == "자녀") children = v else family += v
        }
        if (family !in 1..20 || children > family) return null
        return RuleFinding(heads.joinToString(" ") { FAMILY_ORDER[it] } + " / " + values.joinToString(" "), RuleChange.TaxFamily(family, children), 0.7)
    }

    private val FAMILY_ORDER = listOf("본인", "배우자", "직계존속", "직계비속", "형제자매", "자녀")
    private val FAMILY_HEADS = listOf(
        Regex("본인") to 0, Regex("배우자") to 1, Regex("직계존속") to 2, Regex("직계비속") to 3, Regex("형제자매") to 4,
        Regex("자녀|\\d+세이하|만\\d+세") to 5,
    )

    /**
     * [READ-08] A rules page read from a photo loses small marks (1.25 사진 고정 파일): the dot of "1.5배" ("15배", "1 5배"), the commas
     * and spaces between clauses ("이내는50%8시간초과분은100%"), and reads 지급 as 지금. Put back before reading; typed
     * text is left as it is.
     */
    internal fun tidy(text: String): String = unwrap(text)
        // [PHOTO-04] Digits read as letters inside a number (10O분의, 15O,000), 원 read as 윈, a comma read as a dot (200.000원).
        .replace(DIGIT_O, "0").replace(DIGIT_O, "0")
        .replace(Regex("""(?<=\d)[lI|](?=\d)"""), "1")
        .replace(Regex("""(?<=\d)\s?윈"""), "원")
        .replace(Regex("""(?<![\d.])(\d{1,3})((?:\.\d{3})+)(?![\d.])""")) { m -> m.groupValues[1] + m.groupValues[2].replace('.', ',') }
        .replace(Regex("""(?<![\d.])([12])\s?5\s*배"""), "$1.5배")
        // [PHOTO-04] 1.29: 취업규칙 wording. "통상임금의 100분의 50" is 50%; "× 1.5" (a table of 통상시급 × 배율) is 1.5배;
        // "오후 10시부터 다음 날 오전 6시까지" is a clock range.
        .replace(Regex("""100\s*분의\s*(\d{1,3})"""), "$1%")
        .replace(Regex("""(?:×|ｘ|(?<=[\s가-힣])[xX*])\s*(\d(?:\.\d{1,2})?)(?![\d,.])"""), " $1배")
        .replace(Regex("""(다음\s*날|익일|이튿날)\s*"""), "")
        .replace(Regex("""%(?=\s*\d)"""), "%, ")
        .replace(Regex("""(?<=[가-힣])(?=\d)"""), " ")
        .replace(Regex("지금(?=하|한|합|함|됨|된|액|일|기준)"), "지급").replace("통상시금", "통상시급")

    private val DIGIT_O = Regex("""(?<=\d)[Oo](?=[\d,.%원Oo분]|$)|(?<=[\d,])[Oo](?=\d)""")

    /**
     * [PHOTO-04] A 취업규칙 page wraps its sentences: "연장근로에 대하여는 통상임금의" / "100분의 70을 가산하여 지급한다." A line that
     * ends mid-sentence (on 의·에·는·을 and the like, or a comma) is joined to the next, unless the next starts a new item (제N조, ①, 1., -).
     */
    internal fun unwrap(text: String): String {
        val lines = text.lines()
        val out = StringBuilder()
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd()
            out.append(line)
            val next = lines.getOrNull(i + 1)?.trim() ?: break
            val open = line.length >= 6 && Regex("""(의|에|에는|에게|은|는|을|를|이|가|로|으로|및|과|와|하여|대하여|따라|경우|때|중|시|,|，)$""").containsMatchIn(line) &&
                !Regex("""(원|%|배|시간|다|요|함|음)$""").containsMatchIn(line)
            val newItem = next.isEmpty() || Regex("""^(제\s*\d+\s*조|[①-⑳]|\d{1,2}[.)]\s|[-•·*○●▶□■]|\(\d+\))""").containsMatchIn(next)
            out.append(if (open && !newItem) " " else "\n")
        }
        return out.toString()
    }

    /**
     * Lines, and sentences within a line; a dot between digits (3.5%) does not end one. [PHOTO-04] An article's heading
     * ("제20조(연장·야간근로)") and its item marks (①, ②) are not part of the rule: the heading's words would name the wrong premium.
     */
    internal fun sentences(text: String): List<String> =
        text.lines().flatMap { it.split(Regex("""(?<!\d)\.(?!\d)|;|。|(?=[①-⑳])""")) }
            .map {
                it.trim().replace(Regex("""^제\s*\d+\s*조\s*(의\s*\d+\s*)?(\([^)]*\)|【[^】]*】|\[[^]]*])?"""), "").trim()
                    .trimStart('-', '•', '·', '*', '○', '●', '▶', ' ', '□', '■').replace(Regex("""^[①-⑳]\s*"""), "").trim()
            }.filter { it.length >= 2 }

    /**
     * [PHOTO-04] 1.29: the sentences of [text] that read as a pay rule (an amount, a %, 수당·가산·지급·공제) but gave no rule:
     * shown on the photo's result as '직접 넣기' rows instead of being dropped (사용자 2026-10-27 "사진이 틀릴 시 사용자가 칸을 직접
     * 추가해서"). Sentences the law already covers (주휴수당 1일분, 연차 유급휴가) and payslip amounts are left out.
     */
    fun unread(text: String, kinds: List<ExtraKind> = emptyList(), shiftTypes: List<ShiftType> = emptyList(), fiveOrMore: Boolean = true): List<String> =
        sentences(tidy(text)).filter { s ->
            val c = s.replace(" ", "")
            c.length >= 4 && c.any { it.isDigit() } && c.count { it in '가'..'힣' } >= 2 && RULE_SIGNAL.containsMatchIn(c) && !LAW_ALREADY.containsMatchIn(c) &&
                !EXPLAIN.containsMatchIn(c) && !isPayslipAmount(s, c) &&
                !PayslipReader.isHeader(c.filter { it.isLetter() }) && readSentence(s, kinds, shiftTypes, fiveOrMore).isEmpty()
        }.distinct()

    private val RULE_SIGNAL = Regex("""\d\s*(원|%|배|시간|회|년|개월)|만원|수당|가산|상여|공제|차감|한도""")

    /** What the law pays already and the app counts by itself: nothing to put in. */
    private val LAW_ALREADY = Regex("""주휴(수당|일).*(1일분|유급|지급)|연차(유급)?휴가|근로기준법(에|에서)?(따라|정한|의)|최저임금(이상|법)""")

    /**
     * [PHOTO-12] Rows of a 계산 방법 table that explain rather than set a rule: a rule for 사무직 only (보상휴가), and what the
     * payslip's 미공제 lines mean. Neither a rule nor a '직접 넣기' row.
     */
    private val EXPLAIN = Regex("""사무직限|사무직한정|사무직만|보상휴가로부여|이전미공제|다음공제예정|미공제된금액|원천징수비율""")

    private fun readSentence(line: String, kinds: List<ExtraKind>, shiftTypes: List<ShiftType>, fiveOrMore: Boolean): List<RuleFinding> {
        val c = line.replace(" ", "")
        if (isPayslipAmount(line, c)) return emptyList()
        if (EXPLAIN.containsMatchIn(c)) return emptyList()
        formulaRow(line, c)?.let { return it }
        ordinaryItems(line, c)?.let { return listOf(it) }
        // [PAYS-07] Money taken off is never a way of paying extra work: "식대 근무일마다 3,000원 공제" holds "대근".
        val takenOff = Regex("차감|공제|감액").containsMatchIn(c)
        if (!takenOff) extraRule(line, c, kinds, fiveOrMore)?.let { return listOf(it) }
        if (!takenOff) publicHoliday(line, c, kinds)?.let { return listOf(it) }
        // [PHOTO-04] Rules of one sentence that commas would cut apart.
        holidayTiers(line, c)?.let { return it }
        if (!takenOff) tenure(line, c)?.let { return limited(line, c, it) }
        // Several rules on a line, split at commas: "연장 150%, 야간 50%". [PHOTO-04] A list of months ("3, 6, 9, 12월") is one
        // clause, and so are rules joined by "지급하고"·"이고" ("연장근로는 150%를 지급하고 야간근로는 50%를 가산한다").
        val joined = line.replace(Regex("""(\d{1,2})\s*[,，]\s*(?=\d{1,2}\s*(?:[,，]|월))"""), "$1·")
        val clauses = joined.split(Regex("""(?<!\d)[,，]|[,，](?!\d{3})|그리고|(?<=(?:지급|가산|적용))(?:하고|하며)|(?<=(?:원|%|배))(?:이고|이며)"""))
            .map { it.trim() }.filter { it.isNotEmpty() }
        val found = clauses.filterNot { clause -> clauses.size > 1 && LIMIT_ONLY.matches(clause.replace(" ", "")) }
            .flatMap { clause -> readClause(line, clause, clause.replace(" ", ""), shiftTypes) }
        return limited(line, c, found)
    }

    /**
     * [PHOTO-12] 1.29 AQ: a row of a 계산 방법 table, the name then how it is worked out (화학 공장 명세서 사진: "휴일근로수당 해당시간
     * × 1.5 × (통상임금 / 시수)", "휴일연장수당 해당시간 × 2 × …", "야간근로수당 해당시간 × 0.5 × …"). The name says which premium:
     * 휴일연장 is 휴일 past 8 hours, 평일연장 is 연장 (1.28 read 평일 as a day rule, "평일 가산"). A row paid by days
     * (휴가보상 해당일수 × …) is left for the user ('직접 넣기').
     */
    private fun formulaRow(line: String, c: String): List<RuleFinding>? {
        val at = Regex("해당(시간|일수)|(근로|근무)시간수?(?=\\d|×|x)").find(c) ?: return null
        if (!Regex("통상임금|통상시급|기준급|시급|시수").containsMatchIn(c)) return null
        if (at.value.endsWith("일수")) return emptyList()
        val head = c.substring(0, at.range.first)
        val times = timesRe.find(c.substring(at.range.last + 1))?.groupValues?.get(1)?.toDouble() ?: return null
        val (target, over8h) = when {
            head.contains("휴일") && head.contains("연장") -> PremiumTarget.HOLIDAY to true
            head.contains("야간") || head.contains("심야") -> PremiumTarget.NIGHT to false
            head.contains("휴일") || head.contains("특근") -> PremiumTarget.HOLIDAY to false
            head.contains("연장") || head.contains("시간외") -> PremiumTarget.OVERTIME to false
            else -> return null
        }
        val premium = (if (times < 1.0) times else times - 1.0).coerceIn(0.0, Premium.MAX_RATE)
        return listOf(RuleFinding(line, RuleChange.Premium(target, premium, over8h), 0.85))
    }

    /**
     * [PHOTO-12] "통상임금  기본급, 직책수당, 자격수당, 기술전수교육수당 등 … 임금 일체": what counts in 통상임금, named. Read as which
     * of the user's 수당 count (commas would cut it into clauses that read nothing).
     */
    private fun ordinaryItems(line: String, c: String): RuleFinding? {
        val m = Regex("^통상임금(의범위|범위|은|는|:|=|이란)?").find(c) ?: return null
        val rest = line.substring(line.indexOf("통상임금") + 4)
        val names = Regex("""[가-힣]{1,10}(수당|급|비)(?![가-힣])""").findAll(rest).map { it.value }
            .filter { it != "통상임금" && it != "기준급" && !it.endsWith("성") }.distinct().toList()
        if (names.size < 2 || m.value.isEmpty() && rest.isBlank()) return null
        return RuleFinding(line, RuleChange.OrdinaryItems(names), 0.75)
    }

    /** A clause that only limits the rule before it ("월 10회 한도", "월 100,000원까지"): read with the line by [limited]. */
    private val LIMIT_ONLY = Regex("""^\(?(월|한달|매월)?(최대)?[\d,]+(원|회)?(한도|까지|이내|상한)\)?$""")

    /**
     * [PHOTO-04] "휴일근로 8시간 이내 150%, 8시간 초과 200%": both halves are about 휴일, though the second does not say so (1.28 read
     * it as a rule of its own, "8시간 초과 가산").
     */
    private fun holidayTiers(line: String, c: String): List<RuleFinding>? {
        if (!c.contains("휴일") || Regex("휴일대체|대체휴일").containsMatchIn(c)) return null
        val m = Regex("""(\d{1,2})시간(이내|까지|이하)[^\d%]*?(\d+(?:\.\d+)?)(%|배).*?(초과|넘|이후)[^\d%]*?(\d+(?:\.\d+)?)(%|배)""").find(c) ?: return null
        fun premium(v: String, unit: String): Double {
            val n = v.toDouble()
            return when {
                unit == "배" -> n - 1.0
                n >= 100 && "가산" !in c -> n / 100 - 1.0
                else -> n / 100
            }.coerceIn(0.0, Premium.MAX_RATE)
        }
        return listOf(
            RuleFinding(line, RuleChange.Premium(PremiumTarget.HOLIDAY, premium(m.groupValues[3], m.groupValues[4])), 0.8),
            RuleFinding(line, RuleChange.Premium(PremiumTarget.HOLIDAY, premium(m.groupValues[6], m.groupValues[7]), over8h = true), 0.8),
        )
    }

    /**
     * [PHOTO-04][PHOTO-05] 근속수당 by years: "근속 3년 이상 월 50,000원", "근속수당: 1년 이상 30,000원, 3년 이상 50,000원, 5년 이상
     * 100,000원", "근속 5년 이상 기본급의 5%". Each step pays from its years until the next step's.
     */
    private fun tenure(line: String, c: String): List<RuleFinding>? {
        if (!c.contains("근속")) return null
        // "1년 이상 3년 미만 20,000원": a step with its own end; else a step lasts until the next one's years.
        val steps = Regex("""(\d{1,2})년(?:이상|이후|부터|차|째|넘|초과)?(?:~?(\d{1,2})년(미만|이하|까지))?[^\d]{0,12}?(?:월|매월|매달)?(\d{1,3}(?:,\d{3})+|\d+(?:\.\d+)?)(원|만원|%)""")
            .findAll(c).mapNotNull { m ->
                val years = m.groupValues[1].toInt().takeIf { it in 1..40 } ?: return@mapNotNull null
                val end = m.groupValues[2].toIntOrNull()?.let { if (m.groupValues[3] == "미만") it * 12 else (it + 1) * 12 }
                val v = m.groupValues[4].replace(",", "").toDouble()
                val pay: RulePay = when (m.groupValues[5]) {
                    "%" -> if ("통상임금" in c) RulePay.OfOrdinaryMonthly(v / 100) else RulePay.OfBaseMonthly(v / 100)
                    "만원" -> RulePay.Monthly((v * 10_000).toLong())
                    else -> RulePay.Monthly(v.toLong())
                }
                Triple(years, end, pay)
            }.toList()
        if (steps.isEmpty()) return null
        val name = Regex("""^[가-힣]*근속[가-힣]*?수당""").find(c)?.value?.take(CustomRule.MAX_NAME) ?: "근속수당"
        return steps.mapIndexed { i, (years, end, pay) ->
            val next = end ?: steps.getOrNull(i + 1)?.first?.takeIf { it > years }?.times(12)
            val rule = CustomRule("rule-" + (line + years).hashCode().toUInt().toString(16), if (steps.size > 1) "$name ${years}년" else name,
                RuleWhen(), pay, source = line.trim(), minTenureMonths = years * 12, maxTenureMonths = next ?: 0)
            RuleFinding(line, RuleChange.Custom(rule), 0.8)
        }
    }

    /**
     * [PHOTO-04][PHOTO-05] A line's limits put on what it gave: "월 100,000원 한도"·"월 최대 10만원" (a cap), "월 10회까지" (times),
     * "2026년 7월부터"·"12월까지" (months). An allowance paid by the day or the month becomes a rule of the company's own that
     * carries them; one with nothing to limit is left as it is.
     */
    private fun limited(line: String, c: String, found: List<RuleFinding>): List<RuleFinding> {
        if (found.isEmpty()) return found
        val amounts = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*(만원|원)""").findAll(c).map { m ->
            val v = m.groupValues[1].replace(",", "").toLong()
            if (m.groupValues[2] == "만원") v * 10_000 else v
        }.toList()
        val cap = Regex("""(?:월|한달|매월)(?:최대|상한)?(\d{1,3}(?:,\d{3})+|\d+)(만원|원)(?:한도|까지|이내|상한)|(?:월|한달)?(?:최대|상한)(\d{1,3}(?:,\d{3})+|\d+)(만원|원)""")
            .find(c)?.let { m ->
                val v = (m.groupValues[1].ifEmpty { m.groupValues[3] }).replace(",", "").toLong()
                if ((m.groupValues[2].ifEmpty { m.groupValues[4] }) == "만원") v * 10_000 else v
            }?.takeIf { amounts.size >= 2 } ?: 0L
        val times = Regex("""(?:월|한달|매월)(?:최대)?(\d{1,2})회(?:한도|까지|이내|에한)|최대(\d{1,2})회""").find(c)
            ?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt() } ?: 0
        val from = Regex("""(20\d{2})(?:년|[./-])(\d{1,2})(?:월|[./-]\d{1,2})?\.?(?:부터|이후|시행|적용)""").find(c)
            ?.let { runCatching { YearMonth.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() }
        val until = Regex("""(20\d{2})(?:년|[./-])(\d{1,2})(?:월|[./-]\d{1,2})?\.?(?:까지)""").find(c)
            ?.let { runCatching { YearMonth.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() }
        if (cap == 0L && times == 0 && from == null && until == null) return found
        return found.map { f ->
            val ch = f.change
            val rule = when (ch) {
                is RuleChange.Custom -> ch.rule
                is RuleChange.Allowance -> when (ch.cadence) {
                    Cadence.MONTHLY -> CustomRule("rule-" + (line + ch.name).hashCode().toUInt().toString(16), ch.name.take(CustomRule.MAX_NAME), RuleWhen(),
                        RulePay.Monthly(ch.amount), source = line.trim())
                    Cadence.PER_WORK_DAY -> CustomRule("rule-" + (line + ch.name).hashCode().toUInt().toString(16), ch.name.take(CustomRule.MAX_NAME),
                        RuleWhen(dayKind = RuleDayKind.WORK_DAY), RulePay.PerTime(ch.amount), source = line.trim())
                    Cadence.PER_SHIFT_TYPE -> CustomRule("rule-" + (line + ch.name).hashCode().toUInt().toString(16), ch.name.take(CustomRule.MAX_NAME),
                        RuleWhen(shiftTypeIds = ch.shiftTypeIds), RulePay.PerTime(ch.amount), source = line.trim())
                    else -> null
                }
                else -> null
            } ?: return@map f
            // The cap is never the amount the rule pays each time.
            val capOk = cap.takeIf { it > 0 && it != (rule.pay as? RulePay.PerTime)?.amount && it != (rule.pay as? RulePay.Monthly)?.amount } ?: 0L
            val limitedRule = rule.copy(capPerMonth = capOk.takeIf { it > 0 } ?: rule.capPerMonth, maxTimes = times.takeIf { it > 0 } ?: rule.maxTimes,
                from = from ?: rule.from, until = until ?: rule.until)
            if (limitedRule == rule) f else RuleFinding(f.sourceLine, RuleChange.Custom(limitedRule, (ch as? RuleChange.Custom)?.shiftNames.orEmpty()), f.confidence)
        }
    }

    /** A payslip line: an amount for a premium or a total with no rule words, e.g. "연장근로수당 523,000원". */
    private fun isPayslipAmount(line: String, c: String): Boolean {
        val role = PayslipReader.roleOf(Regex("""^[가-힣A-Za-z]+""").find(c)?.value ?: return false) ?: return false
        if (role !in setOf(SlipRole.OVERTIME, SlipRole.NIGHT, SlipRole.HOLIDAY, SlipRole.WEEKLY_HOLIDAY, SlipRole.GROSS,
                SlipRole.NET, SlipRole.DEDUCTION, SlipRole.DEDUCTION_TOTAL, SlipRole.TOTAL, SlipRole.EXTRA_WORK)) return false
        val hasRuleWords = percentRe.containsMatchIn(line) || timesRe.containsMatchIn(line) ||
            listOf("시간당", "1회", "회당", "가산", "기본급의", "통상시급", "인정", "까지", "부터").any { it in c } ||
            // [PAYS-07] "기숙사비 매달 5만원 차감" is a rule; "기숙사비 50,000" is a payslip line.
            role == SlipRole.DEDUCTION && Regex("매달|매월|마다|차감").containsMatchIn(c) ||
            Regex("차감|공제").containsMatchIn(c) && Regex("매달|매월|마다").containsMatchIn(c)
        return !hasRuleWords && RuleImport.parseAmount(line) != null || Regex("""^[가-힣]+\d{1,3}(,\d{3})+$""").matches(c)
    }

    // ---- extra work ----

    private fun kindOf(c: String, kinds: List<ExtraKind>): Pair<Long, String>? {
        val own = kinds.filter { it.name.replace(" ", "").length >= 2 && it.name.replace(" ", "") in c }
            .maxByOrNull { it.name.replace(" ", "").length }
        if (own != null) return own.id to own.name
        return when {
            "대근" in c || "대체근무" in c -> ExtraKind.COVER_ID to "대근"
            "특근" in c || "특별근무" in c -> ExtraKind.SPECIAL_ID to "특근"
            "잔업" in c -> ExtraKind.OVERTIME_ID to "잔업"
            else -> null
        }
    }

    /**
     * "대근은 8시간에 기본급의 10%, 8시간 넘으면 50% 가산, 야간 50% 가산", "특근 시간당 2만원", "대근 1회 8만원 +
     * 연장 가산". A premium the sentence names makes it a rule of an hour's pay and ticked premiums; the ones it does
     * not name stay as the law has them, so nothing is paid below the law by leaving a word out.
     */
    private fun extraRule(line: String, c: String, kinds: List<ExtraKind>, fiveOrMore: Boolean): RuleFinding? {
        // "공휴일 근무는 대근으로" is about holidays, not how 대근 is paid.
        if (Regex("공휴일|명절|휴일근무는|빨간날").containsMatchIn(c) && Regex("(으로|로|처럼|방식|같이)").containsMatchIn(c) &&
            percentRe.find(c) == null && RuleImport.parseAmount(line) == null) return null
        val (kind, name) = kindOf(c, kinds) ?: return null
        if (c.startsWith("공휴일") || c.startsWith("명절")) return null
        val clauses = line.split(Regex("""(?<!\d)[,，]|[,，](?!\d{3})|\+|그리고""")).map { it.replace(" ", "") }
        val minHours = Regex("""최소(\d{1,2})시간""").find(c)?.groupValues?.get(1)?.toInt()
        val noNight = Regex("야간(가산)?(없|제외|미|안)").containsMatchIn(c)
        val legal = if (fiveOrMore) 0.5 else 0.0
        var rule = ExtraRule.lawUnit(overtime = legal, night = legal, holiday = legal, restAsHoliday = false)
        var unitSet = false
        var named = false
        var bonus = 0L
        val custom = mutableListOf<Premium>()
        for ((i, cl) in clauses.withIndex()) {
            val pct = percentRe.find(cl)?.groupValues?.get(1)?.toDouble()
            val times = timesRe.find(cl)?.groupValues?.get(1)?.toDouble()
            val amount = RuleImport.parseAmount(cl)
            val hrs = hoursRe.find(cl)?.groupValues?.get(1)?.toDouble()?.let { (it * 60).toInt() }
            val window = clockRange(cl)
            when {
                // The hour's pay.
                (pct != null) && ("기본급" in cl || "월급" in cl) && !unitSet -> {
                    rule = if ("1회" in cl || "회당" in cl) ExtraRule(ExtraBasis.PERCENT_OF_BASE, percentOfBase = pct / 100, coverMinutes = hrs ?: 480)
                    else rule.copy(unit = PayUnit.BASE_SHARE, percentOfBase = pct / 100, unitMinutes = hrs ?: 480)
                    unitSet = true
                }
                "통상" in cl && (pct != null || times != null) && !unitSet && window == null && !Regex("넘|초과|이후").containsMatchIn(cl) -> {
                    val m = times ?: (pct!! / 100)
                    // "통상시급의 200%" is the whole rate: the hour at 100% and the rest as a premium on every minute.
                    rule = rule.copy(unit = PayUnit.ORDINARY)
                    if (m > 1.0) custom += Premium("c${custom.size + 1}", "$name 가산", m - 1.0, scope = PremiumScope.All)
                    unitSet = true
                }
                amount != null && Regex("시간당|1시간|매시간").containsMatchIn(cl) && !unitSet -> {
                    rule = rule.copy(unit = PayUnit.PER_HOUR, amount = amount); unitSet = true
                }
                amount != null && !unitSet && (i == 0 || Regex("1회|회당|매회").containsMatchIn(cl)) -> {
                    rule = rule.copy(unit = PayUnit.PER_TIME, amount = amount, coverMinutes = hrs?.takeIf { "까지" in cl || "기준" in cl })
                    unitSet = true
                }
                amount != null && unitSet -> bonus += amount
                pct != null || times != null -> {
                    val p = when {
                        times != null -> times - 1.0
                        pct!! >= 100 && "가산" !in cl -> pct / 100 - 1.0
                        else -> pct / 100
                    }
                    when {
                        window != null -> custom += Premium("c${custom.size + 1}", "${RuleWhen.clock(window.first)}~${RuleWhen.clock(window.second)}시 가산", p,
                            scope = PremiumScope.Window(window.first, window.second))
                        hrs != null && Regex("넘|초과|이후|부터").containsMatchIn(cl) ->
                            rule = rule.tick(Premium.OVERTIME, p) { it.copy(scope = PremiumScope.Over(hrs), name = "${PayCalculator.hours(hrs)} 넘는 시간 가산") }
                        "야간" in cl || "심야" in cl -> rule = rule.tick(Premium.NIGHT, p)
                        "휴무일" in cl || "쉬는날" in cl -> rule = rule.tick(Premium.REST, p)
                        "휴일" in cl -> rule = rule.tick(Premium.HOLIDAY, p)
                        "연장" in cl -> rule = rule.tick(Premium.OVERTIME, p)
                        else -> continue
                    }
                    named = true
                }
            }
        }
        if (!unitSet && !named && bonus == 0L) return null
        if (noNight) rule = rule.copy(premiums = rule.premiums.map { if (it.key == Premium.NIGHT) it.copy(on = false) else it })
        rule = if (rule.basis == ExtraBasis.UNIT) rule.copy(premiums = rule.premiums + custom.take(Premium.MAX_CUSTOM))
        // A payment each time with premiums named: the law's 연장·휴일 premiums on top, the night one stays in the month's line.
        else rule.copy(legalPremiums = named, nightPremium = !noNight)
        rule = rule.copy(minMinutes = (minHours ?: 0) * 60, bonus = bonus)
        return RuleFinding(line, RuleChange.Extra(kind, name, rule), if (unitSet) 0.85 else 0.6)
    }

    private fun ExtraRule.tick(key: String, rate: Double, change: (Premium) -> Premium = { it }): ExtraRule =
        copy(premiums = premiums.map { if (it.key == key) change(it.copy(rate = rate, on = rate > 0)) else it })

    /** "공휴일 근무는 대근처럼", "공휴일은 휴일 대체". */
    private fun publicHoliday(line: String, c: String, kinds: List<ExtraKind>): RuleFinding? {
        if (!Regex("공휴일|빨간날|법정휴일").containsMatchIn(c)) return null
        if ("대체" in c && ("휴일대체" in c || "바꿔" in c || "대체휴" in c)) return RuleFinding(line, RuleChange.PublicHolidaysPaid(false), 0.8)
        // [HOLI-01] "빨간날도 교대제라 평일처럼", "공휴일 상관없이 근무표대로".
        if (Regex("평일(처럼|과같|로)|상관없|무관|근무표대로").containsMatchIn(c)) return RuleFinding(line, RuleChange.HolidayMode(HolidayWork.SHIFT_NORMAL), 0.75)
        val kind = kindOf(c.replace("휴일근로", ""), kinds)
        if (kind != null && Regex("(으로|로|처럼|방식|같이|대입)").containsMatchIn(c)) return RuleFinding(line, RuleChange.PublicHolidayAs(kind.first, kind.second), 0.85)
        if (Regex("휴일근로로|휴일근무로|휴일수당").containsMatchIn(c) && percentRe.find(c) == null) return RuleFinding(line, RuleChange.PublicHolidaysPaid(true), 0.75)
        return null
    }

    // ---- everything else, one clause at a time ----

    /** [PHOTO-04] An amount in a table cell has no 원: "직무수당  매월  100,000". Only a number with thousands commas counts. */
    private fun bareAmount(clause: String): Long? =
        Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+)(?![\d,]|\s*(?:%|배|시간|분|명|년|개월))""").find(clause)
            ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.takeIf { it >= 1_000 }

    private fun readClause(line: String, clause: String, c: String, shiftTypes: List<ShiftType>): List<RuleFinding> {
        val amount = RuleImport.parseAmount(clause) ?: bareAmount(clause)
        val pct = percentRe.find(clause)?.groupValues?.get(1)?.toDouble()
        val times = timesRe.find(clause)?.groupValues?.get(1)?.toDouble()
        fun one(change: RuleChange, confidence: Double) = listOf(RuleFinding(line, change, confidence))

        // The pay itself.
        if ("연봉" in c && amount != null) return one(RuleChange.Annual(amount), 0.85)
        if (Regex("^(기본급|기본월급|월급|기본임금)").containsMatchIn(c) && amount != null && pct == null) return one(RuleChange.Base(WageType.MONTHLY, amount), 0.9)
        if (Regex("^(시급|기본시급)").containsMatchIn(c) && amount != null) return one(RuleChange.Base(WageType.HOURLY, amount), 0.9)
        if (Regex("^(일당|일급)").containsMatchIn(c) && amount != null) return one(RuleChange.Base(WageType.DAILY, amount), 0.85)
        if (("인상" in c || "상승" in c) && pct != null) {
            val month = monthListRe.find(c)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..12 } ?: 1
            return one(RuleChange.Raise(month, pct), 0.8)
        }

        // How the company counts.
        if (Regex("5인미만|5명미만|다섯명미만").containsMatchIn(c)) return one(RuleChange.FiveOrMore(false), 0.9)
        if (Regex("5인이상|5명이상").containsMatchIn(c)) return one(RuleChange.FiveOrMore(true), 0.9)
        if ("휴게" in c) {
            when {
                Regex("근무시간|근로시간|일한시간|대기").containsMatchIn(c) && !Regex("아니|않|제외").containsMatchIn(c) -> return one(RuleChange.Breaks(BreakPay.AS_WORK), 0.75)
                // [PHOTO-04] "휴게시간은 근로시간에 포함하지 아니한다" (1.28 read nothing).
                Regex("근무시간|근로시간").containsMatchIn(c) && Regex("포함하지|포함되지|아니|않|제외").containsMatchIn(c) -> return one(RuleChange.Breaks(BreakPay.UNPAID), 0.8)
                "무급" in c || Regex("안나|빼").containsMatchIn(c) -> return one(RuleChange.Breaks(BreakPay.UNPAID), 0.8)
                "유급" in c || Regex("돈이나|급여가나|임금지급").containsMatchIn(c) -> return one(RuleChange.Breaks(BreakPay.PAID), 0.8)
            }
        }
        Regex("""(소정근로|근로시수|소정시간|월소정|기준시간|근무시수)[^\d]{0,6}(\d{2,3})\s*시간?""").find(c)?.let {
            val h = it.groupValues[2].toInt()
            if (h in 100..320) return one(RuleChange.MonthlyHours(h), 0.85)
        }
        Regex("""^(월)?(\d{3})시간(이에요|입니다|기준)?$""").find(c)?.let {
            val h = it.groupValues[2].toInt()
            if (h in 100..320) return one(RuleChange.MonthlyHours(h), 0.6)
        }
        if (("주휴일" in c || "주휴" in c && "일" in c) && amount == null && pct == null) {
            RuleWhen.DAY_NAMES.entries.firstOrNull { (_, n) -> "${n}요일" in c }?.let { return one(RuleChange.WeeklyHolidayDay(it.key), 0.8) }
        }
        if (Regex("쉬는날|휴무일|비번").containsMatchIn(c) && Regex("근무|일하|나오").containsMatchIn(c) && amount == null && pct == null) {
            when {
                Regex("휴일근로|휴일근무|휴일로").containsMatchIn(c) -> return one(RuleChange.RestDay(RestDayWork.HOLIDAY), 0.8)
                Regex("연장근로로|연장으로|전부연장|모두연장").containsMatchIn(c) -> return one(RuleChange.RestDay(RestDayWork.OVERTIME), 0.8)
                "법대로" in c -> return one(RuleChange.RestDay(RestDayWork.LAW), 0.7)
            }
        }
        if (Regex("근무표|정해진근무|교대근무|12시간근무").containsMatchIn(c) && Regex("기본근무|소정근로|기본급에포함|연장아님|연장없").containsMatchIn(c)) {
            return one(RuleChange.ScheduleRegular(true), 0.75)
        }

        // The night window: "야간은 23시~07시", "야간 시간 22:00-06:00".
        val window = clockRange(clause) ?: nightHours(c)
        if (window != null && ("야간" in c || "심야" in c) && pct == null && times == null && amount == null) {
            return one(RuleChange.NightWindow(window.first, window.second), 0.85)
        }
        // [PHOTO-04] "야간수당 시간당 3,000원": the night hours, each paid (1.28 read it as 3,000원 a month).
        if (window == null && Regex("야간|심야").containsMatchIn(c) && amount != null && Regex("시간당|1시간당|매시간").containsMatchIn(c) &&
            !Regex("차감|공제|감액").containsMatchIn(c)) {
            val name = nameOf(clause).replace(Regex("""\s*(시간당|1시간당|매시간)\s*$"""), "").ifEmpty { "야간 수당" }.take(CustomRule.MAX_NAME)
            val rule = CustomRule("rule-" + (line + clause).hashCode().toUInt().toString(16), name, RuleWhen(from = LocalTime.of(22, 0), to = LocalTime.of(6, 0)),
                RulePay.PerHour(amount), source = line.trim())
            return one(RuleChange.Custom(rule), 0.75)
        }

        // Bonuses: "상여금 800% 짝수달", "명절상여 50만원 설·추석".
        if (Regex("상여|보너스|명절").containsMatchIn(c) && (pct != null || amount != null)) {
            val festivals = buildSet {
                if (Regex("설|명절").containsMatchIn(c)) add(Festival.SEOLLAL)
                if (Regex("추석|명절").containsMatchIn(c)) add(Festival.CHUSEOK)
            }
            val months = months(c)
            val name = Regex("""^[가-힣]*(상여금?|보너스)""").find(c)?.value?.removeSuffix("은")?.takeIf { it.length in 2..CustomRule.MAX_NAME && festivals.isEmpty() }
                ?: if (festivals.isNotEmpty() && months.isEmpty()) "명절상여" else "상여금"
            if (pct != null) {
                val paidIn = months.ifEmpty { if (festivals.isEmpty()) setOf(2, 4, 6, 8, 10, 12) else emptySet() }
                // [PHOTO-04] "3, 6, 9, 12월에 각 기본급의 100%" is 100% each time, 400% a year (1.28 read 100% a year).
                val each = !Regex("연간?|년간|매년|1년").containsMatchIn(c) && Regex("각|매회|1회|회당|마다|씩|지급시|분기별").containsMatchIn(c)
                val yearly = if (each) pct * (paidIn.size + festivals.size).coerceAtLeast(1) else pct
                val basis = when {
                    Regex("통상임금|통상급").containsMatchIn(c) -> BonusBasis.ORDINARY
                    Regex("고정수당|기본급\\+|기본급과|기본급및").containsMatchIn(c) -> BonusBasis.BASE_FIXED
                    else -> BonusBasis.BASE
                }
                return one(RuleChange.Bonus(name, yearly, null, paidIn, festivals, basis), 0.75)
            }
            return one(RuleChange.Bonus(name, null, amount, months, festivals.takeIf { it.isNotEmpty() || months.isNotEmpty() } ?: setOf(Festival.SEOLLAL, Festival.CHUSEOK)), 0.7)
        }

        // [PAYS-07] Money the company takes off: "지각 1회 1만원 차감", "기숙사비 매달 5만원 공제".
        deduction(line, clause, c, amount, pct, shiftTypes)?.let { return listOf(it) }
        // Money taken off that the calendar cannot count (지각 1회 1만원 차감) is never read as money paid.
        if (amount != null && Regex("차감|공제|감액").containsMatchIn(c)) return emptyList()

        // A rule of the company's own: a condition (a day, a clock window, hours beyond some) and a way to pay.
        custom(line, clause, c, amount, pct, times, window, shiftTypes)?.let { return listOf(it) }

        // The month's premiums: "연장 150%", "야간 가산 50%", "휴일 8시간 넘으면 200%".
        if ((pct != null || times != null) && Regex("연장|야간|심야|휴일").containsMatchIn(c) && amount == null) {
            val target = when {
                "야간" in c || "심야" in c -> PremiumTarget.NIGHT
                "휴일" in c -> PremiumTarget.HOLIDAY
                else -> PremiumTarget.OVERTIME
            }
            // [PHOTO-04] "연장·야간·휴일근로는 통상시급의 1.5배": one rate for each premium named in a list (1.28 set the night one only).
            val listed = Regex("""(연장|야간|심야|휴일)(근로|근무)?[·,，및와과/ㆍ](연장|야간|심야|휴일)""").containsMatchIn(c)
            val targets = if (!listed) listOf(target) else buildList {
                if ("연장" in c) add(PremiumTarget.OVERTIME)
                if ("야간" in c || "심야" in c) add(PremiumTarget.NIGHT)
                if ("휴일" in c) add(PremiumTarget.HOLIDAY)
            }
            val over8h = Regex("8시간(을|를)?(넘|초과|이상)").containsMatchIn(c)
            val found = targets.map { t ->
                val premium = when {
                    // "× 0.5": the premium itself.
                    times != null -> if (times < 1.0) times else times - 1.0
                    "가산" in c || t == PremiumTarget.NIGHT || pct!! < 100 -> pct!! / 100
                    else -> pct / 100 - 1.0
                }
                RuleFinding(line, RuleChange.Premium(t, premium.coerceIn(0.0, Premium.MAX_RATE), t == PremiumTarget.HOLIDAY && over8h), 0.75)
            }
            // [PHOTO-04] "야간(23~07) 50%": the night hours too (1.28 dropped them).
            val night = window?.takeIf { PremiumTarget.NIGHT in targets }?.let { listOf(RuleFinding(line, RuleChange.NightWindow(it.first, it.second), 0.8)) }.orEmpty()
            return night + found
        }

        // [PHOTO-04] "직급수당 기본급의 5%", "자격수당 통상임금의 3%": a share of the month's pay, every month (1.28 read nothing).
        if (pct != null && amount == null && Regex("기본급|통상임금|월급").containsMatchIn(c) && !Regex("상여|보너스|인상|차감|공제").containsMatchIn(c)) {
            val name = nameOf(clause).replace(Regex("""\s*(기본급|통상임금|월급)의?\s*$"""), "").trim()
            if (name.length >= 2) {
                val pay = if ("통상임금" in c) RulePay.OfOrdinaryMonthly(pct / 100) else RulePay.OfBaseMonthly(pct / 100)
                val rule = CustomRule("rule-" + (line + clause).hashCode().toUInt().toString(16), name.take(CustomRule.MAX_NAME), RuleWhen(), pay, source = line.trim())
                return one(RuleChange.Custom(rule), 0.7)
            }
        }

        // [PHOTO-04] Paid once a year ("학자금 연 200만원 한도"): not a month's allowance; left for the user to put in.
        if (amount != null && Regex("""(^|[^가-힣]|당)연\s?\d|연간|년간|매년|연1회|년1회""").containsMatchIn(clause)) return emptyList()

        // Allowances: "자격수당 월 5만원", "나이트 수당 1회 3만원", "야간 근무마다 2만원".
        if (amount != null) {
            val name = nameOf(clause)
            val shifts = shiftTypes.filter { it.name.length >= 1 && Regex("${Regex.escape(it.name.replace(" ", ""))}(근무|조|번)?(마다|1회|당|할때)").containsMatchIn(c) }
            val cadence = when {
                shifts.isNotEmpty() -> Cadence.PER_SHIFT_TYPE
                Regex("나이트|야간근무|밤근무").containsMatchIn(c) && Regex("1회|회당|근무당|마다|매회").containsMatchIn(c) -> Cadence.PER_NIGHT_SHIFT
                Regex("1회|회당|근무당|1일|근무일|출근|매회|마다|한번에|번에").containsMatchIn(c) -> Cadence.PER_WORK_DAY
                else -> Cadence.MONTHLY
            }
            val ordinary = when {
                Regex("통상임금(제외|아님|미포함|안)").containsMatchIn(c) -> false
                "통상임금" in c -> true
                else -> null
            }
            if (name.isNotEmpty() || Regex("수당|식대|보조|지원").containsMatchIn(c)) {
                return one(RuleChange.Allowance(name.ifEmpty { "수당" }, amount, cadence, shifts.map { it.id }.toSet(), ordinary),
                    if (name.isNotEmpty() && Regex("수당|식대|식비|보조|지원|비$").containsMatchIn(name)) 0.8 else 0.5)
            }
        }
        return emptyList()
    }

    /** "토요일 근무는 시간당 50% 더", "19~23시 근무 25% 가산", "일요일 근무 1회 2만원", "12시간 넘는 시간은 시간당 3,000원". */
    private fun custom(
        line: String, clause: String, c: String, amount: Long?, pct: Double?, times: Double?,
        window: Pair<LocalTime, LocalTime>?, shiftTypes: List<ShiftType>,
    ): RuleFinding? {
        val days = buildSet {
            RuleWhen.DAY_NAMES.forEach { (d, n) -> if ("${n}요일" in c) add(d) }
            if ("주말" in c) { add(DayOfWeek.SATURDAY); add(DayOfWeek.SUNDAY) }
            if ("평일" in c) addAll(DayOfWeek.entries.filter { it.value <= 5 })
        }
        val dayKind = when {
            Regex("공휴일|빨간날").containsMatchIn(c) -> RuleDayKind.HOLIDAY
            Regex("쉬는날|휴무일|비번날").containsMatchIn(c) -> RuleDayKind.OFF_DAY
            else -> RuleDayKind.ANY
        }
        val shifts = shiftTypes.filter { s ->
            val n = s.name.replace(" ", "")
            n.isNotEmpty() && Regex("${Regex.escape(n)}(근무|조|번)(때|일|에|는|시|한날|날)").containsMatchIn(c)
        }
        val over = Regex("""(\d{1,2})시간(?:을|를|이)?(넘|초과|이후)""").find(c)?.groupValues?.get(1)?.toInt()?.times(60)
        val atLeast = Regex("""(\d{1,2})시간이상""").find(c)?.groupValues?.get(1)?.toInt()?.times(60)
        // A night window on its own is the night premium; a window with "근무" or a named day is the company's own.
        val windowRule = window != null && !Regex("^(야간|심야)").containsMatchIn(c) &&
            // [PHOTO-04] "야간근로(오후 10시부터 오전 6시까지)에 대하여는 50%를 가산": the night premium and its hours, not a rule of its own.
            !(Regex("^[가-힣]{0,4}(야간|심야)(근로|근무)?\\(").containsMatchIn(c) && amount == null)
        // [PHOTO-04] "휴일은 8시간 넘으면 2.5배", "연장 8시간 넘는 시간 200%": a premium the law names, read below (1.28 made "8시간 초과 가산").
        if (amount == null && days.isEmpty() && !windowRule && Regex("(?<!공)휴일|연장").containsMatchIn(c) && !Regex("휴무일|쉬는날|빨간날").containsMatchIn(c) &&
            shiftTypes.none { s -> s.name.isNotEmpty() && s.name.replace(" ", "") in c }) return null
        val hasCondition = days.isNotEmpty() || shifts.isNotEmpty() || over != null || atLeast != null || windowRule ||
            dayKind != RuleDayKind.ANY && (amount != null || "시간당" in c)
        if (!hasCondition) return null
        val pay: RulePay = when {
            pct != null && ("기본급" in c) -> RulePay.OfBase(pct / 100, hoursRe.findAll(c).map { (it.groupValues[1].toDouble() * 60).toInt() }
                .firstOrNull { it != over && it != atLeast } ?: 480)
            amount != null && Regex("시간당|1시간|매시간").containsMatchIn(c) -> RulePay.PerHour(amount)
            amount != null -> RulePay.PerTime(amount)
            times != null -> RulePay.OfHourly(times - 1.0)
            pct != null -> RulePay.OfHourly(if (pct >= 100 && "가산" !in c && "더" !in c && "추가" !in c) pct / 100 - 1.0 else pct / 100)
            else -> return null
        }
        if (pay is RulePay.OfHourly && pay.rate <= 0) return null
        val whenever = RuleWhen(
            dayKind = dayKind,
            daysOfWeek = days,
            shiftTypeIds = shifts.map { it.id }.toSet(),
            from = window?.first?.takeIf { windowRule }, to = window?.second?.takeIf { windowRule },
            overMinutes = over ?: 0,
            minMinutes = if (pay is RulePay.PerTime) atLeast ?: 0 else 0,
        )
        val what = when {
            days.isNotEmpty() -> when (days) {
                setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "주말"
                DayOfWeek.entries.filter { it.value <= 5 }.toSet() -> "평일"
                else -> days.sorted().joinToString("·") { RuleWhen.DAY_NAMES.getValue(it) } + "요일"
            }
            shifts.isNotEmpty() -> shifts.joinToString("·") { it.name }
            windowRule -> "${RuleWhen.clock(window!!.first)}~${RuleWhen.clock(window.second)}시"
            over != null -> "${PayCalculator.hours(over)} 초과"
            dayKind != RuleDayKind.ANY -> dayKind.label
            else -> "${PayCalculator.hours(atLeast ?: 0)} 이상"
        }
        val name = (what + if (pay is RulePay.PerTime) " 수당" else " 가산").take(CustomRule.MAX_NAME)
        val rule = CustomRule("rule-" + (line + clause).hashCode().toUInt().toString(16), name, whenever, pay, source = line.trim())
        return RuleFinding(line, RuleChange.Custom(rule, shiftTypes.associate { it.id to it.name }), 0.75)
    }

    /**
     * [PAYS-07] "기숙사비 매달 5만원 차감", "식대 근무일마다 3,000원 공제", "조합비 기본급의 1% 공제": money the company
     * takes off what it pays. Taxes and 4대보험 come off after the total the app works out, so they are left out,
     * and so is anything the calendar cannot count (지각, 조퇴).
     */
    private fun deduction(line: String, clause: String, c: String, amount: Long?, pct: Double?, shiftTypes: List<ShiftType>): RuleFinding? {
        if (!Regex("차감|공제|빼고|빼서|뺀다|뺌|감액").containsMatchIn(c)) return null
        if (Regex("국민연금|건강보험|고용보험|장기요양|소득세|주민세|세금|4대보험|공제합계|공제총액|공제계|지각|조퇴|외출").containsMatchIn(c)) return null
        val days = buildSet { RuleWhen.DAY_NAMES.forEach { (d, n) -> if ("${n}요일" in c) add(d) } }
        val shifts = shiftTypes.filter { s ->
            val n = s.name.replace(" ", "")
            n.isNotEmpty() && Regex("${Regex.escape(n)}(근무|조|번)").containsMatchIn(c)
        }
        val perDay = days.isNotEmpty() || shifts.isNotEmpty() || Regex("1회|회당|번당|건당|마다|근무일|출근").containsMatchIn(c)
        val pay: RulePay = when {
            pct != null && "기본급" in c -> RulePay.OfBaseMonthly(pct / 100)
            amount == null || amount <= 0 -> return null
            perDay -> RulePay.PerTime(amount)
            else -> RulePay.Monthly(amount)
        }
        val whenever = if (pay is RulePay.PerTime) RuleWhen(RuleDayKind.WORK_DAY, days, shifts.map { it.id }.toSet()) else RuleWhen()
        val name = nameOf(clause).replace(Regex("""\s*(근무일|출근|근무|매일|차감|공제|기본급의|기본급)\s*$"""), "").trim()
            .ifEmpty { "차감" }.take(CustomRule.MAX_NAME)
        val rule = CustomRule("rule-" + (line + clause).hashCode().toUInt().toString(16), name, whenever, pay, source = line.trim(), deduct = true)
        return RuleFinding(line, RuleChange.Custom(rule, shiftTypes.associate { it.id to it.name }), 0.7)
    }

    /** Clock range in a clause, e.g. 19~23시, 22:00-06:00, 밤 10시부터 새벽 6시까지. */
    internal fun clockRange(text: String): Pair<LocalTime, LocalTime>? {
        val m = clockRangeRe.find(text) ?: return null
        // A bare "8-12" with no 시 or : is not a clock range.
        if (!Regex("시|:").containsMatchIn(m.value)) return null
        fun hour(prefix: String, h: Int): Int = when (prefix) {
            "오후", "저녁", "밤" -> if (h in 1..11) h + 12 else h
            "새벽", "오전" -> if (h == 12) 0 else h
            else -> h
        }
        val sh = hour(m.groupValues[1], m.groupValues[2].toInt())
        val sm = (m.groupValues[3].ifEmpty { m.groupValues[4] }).toIntOrNull() ?: 0
        val eh = hour(m.groupValues[5], m.groupValues[6].toInt())
        val em = (m.groupValues[7].ifEmpty { m.groupValues[8] }).toIntOrNull() ?: 0
        if (sh !in 0..24 || eh !in 0..24 || sm !in 0..59 || em !in 0..59) return null
        return LocalTime.of(sh % 24, sm) to LocalTime.of(eh % 24, em)
    }

    /** [PHOTO-04] "야간(22~06) 50%": bare hours after 야간, read as a clock range only there (a bare "8-12" elsewhere is not one). */
    private fun nightHours(c: String): Pair<LocalTime, LocalTime>? {
        val m = Regex("""(야간|심야)[가-힣]{0,4}\(?(\d{1,2})\s*[~\-–]\s*(\d{1,2})\)?""").find(c) ?: return null
        val s = m.groupValues[2].toInt(); val e = m.groupValues[3].toInt()
        if (s !in 18..24 || e !in 0..9) return null
        return LocalTime.of(s % 24, 0) to LocalTime.of(e, 0)
    }

    private fun months(c: String): Set<Int> = when {
        "짝수" in c -> setOf(2, 4, 6, 8, 10, 12)
        "홀수" in c -> setOf(1, 3, 5, 7, 9, 11)
        "분기" in c -> setOf(3, 6, 9, 12)
        "반기" in c -> setOf(6, 12)
        "매달" in c || "매월" in c -> (1..12).toSet()
        // "3·6·9·12월", "6, 12월", "12월".
        else -> Regex("""(?<![\d.,])(\d{1,2}(?:\s*[·,/、ㆍ]\s*\d{1,2})*)\s*월""").findAll(c.substringBefore("%"))
            .flatMap { m -> Regex("""\d{1,2}""").findAll(m.groupValues[1]).map { it.value.toInt() } }
            .filter { it in 1..12 }.toSet()
    }

    /** The words before the first number, without bullets and joining words. */
    private fun nameOf(clause: String): String =
        clause.split(Regex("""[\d:：]"""), limit = 2).first()
            .replace(Regex("""^[\s\-•·*○●▶\[\(]+"""), "")
            .replace(Regex("""(은|는|이|가)?\s*(월|1회|회당|근무당|매달|매월|마다|당)?\s*$"""), "")
            .trim()

    /**
     * The setup with [changes] made, from [from] on for amounts. Each answer it settles leaves the 확인 필요 set.
     * [kinds] names extra work in the changes' summaries only.
     */
    fun apply(profile: PayProfile, changes: List<RuleChange>, from: YearMonth): PayProfile = changes.fold(profile) { p, change ->
        val since = p.period.start(from)
        fun money(v: Long) = ValueTimeline(listOf(DatedValue(since, v)))
        when (change) {
            is RuleChange.Base -> p.copy(
                wageType = change.type,
                base = if (p.wageType == change.type) BulkEditPlanner.apply(p.base, change.amount, EditScope.FromMonth(from)) else money(change.amount),
                annual = null,
                estimatedFields = p.estimatedFields - Unknowns.BASE,
            )
            is RuleChange.Annual -> {
                val a = AnnualPay(change.yearly)
                p.copy(wageType = WageType.MONTHLY, annual = a, estimatedFields = p.estimatedFields - Unknowns.BASE,
                    base = if (p.wageType == WageType.MONTHLY) BulkEditPlanner.apply(p.base, a.breakdown().monthlyBase, EditScope.FromMonth(from))
                    else money(a.breakdown().monthlyBase))
            }
            is RuleChange.Allowance -> {
                val existing = p.items.firstOrNull { it.cadence != Cadence.BONUS && it.name.replace(" ", "") == change.name.replace(" ", "") }
                if (existing != null) p.copy(
                    items = p.items.map {
                        if (it.id != existing.id) it else it.copy(
                            amount = BulkEditPlanner.apply(it.amount, change.amount, EditScope.FromMonth(from)), cadence = change.cadence,
                            shiftTypeIds = change.shiftTypeIds.ifEmpty { it.shiftTypeIds }, ordinary = change.ordinary ?: it.ordinary,
                        )
                    },
                    estimatedFields = p.estimatedFields - existing.id - Unknowns.item(existing.id),
                ) else p.copy(items = p.items + PayItem(newId("item", change.name, p), change.name, money(change.amount), change.cadence,
                    shiftTypeIds = change.shiftTypeIds, ordinary = change.ordinary ?: (change.cadence == Cadence.MONTHLY && !welfare(change.name))))
            }
            is RuleChange.Bonus -> {
                val count = (change.months.size + change.festivals.size).coerceAtLeast(1)
                p.copy(items = p.items + PayItem(newId("bonus", change.name, p), change.name, money(change.amount ?: 0), Cadence.BONUS,
                    ordinary = true, months = change.months, festivals = change.festivals,
                    percentOfBase = change.yearlyPercent?.let { it / 100 / count }, bonusBasis = change.basis))
            }
            is RuleChange.Premium -> p.withRules {
                when (change.target) {
                    PremiumTarget.OVERTIME -> it.copy(overtimePremium = change.premium)
                    PremiumTarget.NIGHT -> it.copy(nightPremium = change.premium)
                    PremiumTarget.HOLIDAY -> if (change.over8h) it.copy(holidayPremiumOver8h = change.premium) else it.copy(holidayPremiumWithin8h = change.premium)
                }
            }
            is RuleChange.NightWindow -> p.withRules { it.copy(nightStart = change.start, nightEnd = change.end) }
            is RuleChange.MonthlyHours -> p.copy(monthlyHours = change.hours, estimatedFields = p.estimatedFields - Unknowns.MONTHLY_HOURS - RuleOnlyNeeds.HOURS_UNSET)
            is RuleChange.FiveOrMore -> p.copy(fiveOrMoreEmployees = change.value, estimatedFields = p.estimatedFields - Unknowns.FIVE_OR_MORE)
            is RuleChange.Breaks -> p.withRules { it.copy(breakPay = change.mode) }.let { Unknowns.answered(it, Unknowns.BREAK_PAY) }
            // [HOLI-01] Through the one 빨간날 choice, so 특근처럼 also judges a night shift by the day it starts.
            is RuleChange.PublicHolidayAs -> p.withRules { it.withHolidayWork(HolidayWork.AS_EXTRA, change.kind) }
                .let { Unknowns.answered(it, Unknowns.PUBLIC_HOLIDAYS) }
            is RuleChange.PublicHolidaysPaid -> p.withRules { it.withHolidayWork(if (change.value) HolidayWork.PREMIUM else HolidayWork.SUBSTITUTE) }
                .let { Unknowns.answered(it, Unknowns.PUBLIC_HOLIDAYS) }
            is RuleChange.HolidayMode -> p.withRules { it.withHolidayWork(change.mode) }.let { Unknowns.answered(it, Unknowns.PUBLIC_HOLIDAYS) }
            is RuleChange.RestDay -> p.withRules { it.copy(restDayWork = change.mode) }.let { Unknowns.answered(it, Unknowns.REST_DAY_WORK) }
            is RuleChange.ScheduleRegular -> p.copy(scheduleIsRegular = change.value).let { Unknowns.answered(it, Unknowns.SCHEDULE_IS_REGULAR) }
            is RuleChange.WeeklyHolidayDay -> p.withRules { it.copy(weeklyHoliday = WeeklyHoliday.Fixed(change.day)) }
            is RuleChange.Raise -> p.copy(base = p.base.copy(raise = RaiseRule(month = change.month, percent = change.percent, unit = 1_000)))
            is RuleChange.Extra -> p.withRules { it.copy(extra = it.extra + (change.kind to change.rule)) }
            is RuleChange.OrdinaryItems -> {
                val wanted = change.names.map { it.replace(" ", "") }.toSet()
                p.copy(items = p.items.map { if (it.cadence != Cadence.BONUS && it.name.replace(" ", "") in wanted) it.copy(ordinary = true) else it })
            }
            is RuleChange.TaxFamily -> p.copy(taxFamily = change.family, taxChildren = change.children)
            is RuleChange.OrdinaryHourly -> p.copy(ordinaryHourly = change.hourly)
                .withRules { it.copy(wonRounding = if (change.roundUp) WonRounding.AT_END_UP else WonRounding.AT_END) }
            is RuleChange.Custom -> p.copy(customRules = (p.customRules.filterNot { it.id == change.rule.id || it.source.isNotEmpty() && it.source == change.rule.source && it.name == change.rule.name } +
                change.rule).takeLast(CustomRule.MAX))
        }
    }

    /** 식대·교통비 and the like: welfare paid to everyone, usually left out of 통상임금 by companies. */
    private fun welfare(name: String) = Regex("식대|식비|교통|차량|복지|통신|자녀|가족|학자").containsMatchIn(name)

    private fun newId(prefix: String, name: String, p: PayProfile): String {
        var i = 0
        while (true) {
            val id = "$prefix-text-${name.hashCode().toUInt().toString(16)}" + if (i == 0) "" else "-$i"
            if (p.items.none { it.id == id }) return id
            i++
        }
    }
}
