package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.roundToLong

/*
 * [RULE-ED-01] 1.30 묶음 AW (사용자 2026-10-27 "회사 규칙을 사진을 못 올리는 사람들도 있을 수 있으므로(사진 촬영 금지 회사) 글로
 * 제공하는 것이 아닌 칸 추가 방식으로 적을 수 있게"): the company's rules as one list of 칸, whatever way they came in. A rules
 * photo, words typed or pasted, 칸 put in by hand and a 광장 setup all make the same [RuleSheet]; the screens show it the same
 * way (사진 결과 화면의 '회사 규칙', 회사 규칙의 '우리 회사 항목', 칸만으로 적기) and it goes onto the pay setup as [RuleChange]s,
 * the one way rules have always gone in ([RuleText.apply]).
 *
 * A 칸 is a [RuleChange] (수당은 [RuleChange.Item] with 묶음 BC's 회사 공통·사람마다·%), so nothing here holds a second model of an
 * allowance: the builder ([com.bamnat.core.pay.PayItem], [CustomRule]) stays the one there is.
 */

/** [RULE-ED-01] Where a 칸 came from, said on its card. */
enum class CellFrom(val label: String) {
    PHOTO("사진에서 읽음"),
    TEXT("글에서 읽음"),
    SLIP("명세서로 찾음"),
    HAND("직접 넣음"),
    PLAZA("광장"),
    /** Already in the pay setup (회사 규칙 화면). */
    SAVED(""),
}

/** [RULE-ED-02] What a 칸 is, for its mark and the order of the list. */
enum class CellKind(val label: String) {
    ALLOWANCE("수당"),
    PREMIUM("더 받는 근무"),
    BONUS("상여"),
    DEDUCT("빠지는 돈"),
    WAY("계산 방식"),
}

/**
 * [RULE-ED-01][PHOTO-32] One 칸 of the company's rules: what it does ([change]), whether it goes in ([on]), the sentence it was read
 * from ([source]) and how sure the reading was. Its card reads in the user's words ([name] · [how] · [whenText] · [limits]): "휴일
 * 8시간 넘게 · 2배 (시급의 100% 더)", not the law's "휴일 8시간 초과 가산 100%".
 */
data class RuleCell(
    val id: String,
    val change: RuleChange,
    val on: Boolean = true,
    val source: String = "",
    val confidence: Double = 1.0,
    val from: CellFrom = CellFrom.HAND,
    /** For [RuleChange.Custom]'s words: the shift names its 근무 condition uses. */
    val shiftNames: Map<Long, String> = emptyMap(),
) {
    /** Read well enough to start ticked (as 1.27 has it). */
    val sure: Boolean get() = confidence >= SURE

    val kind: CellKind get() = when (val c = change) {
        is RuleChange.Premium -> CellKind.PREMIUM
        is RuleChange.Bonus -> CellKind.BONUS
        is RuleChange.Allowance, is RuleChange.Item -> CellKind.ALLOWANCE
        is RuleChange.Custom -> when {
            c.rule.deduct -> CellKind.DEDUCT
            c.rule.pay is RulePay.OfHourly || c.rule.pay is RulePay.OfBase -> CellKind.PREMIUM
            else -> CellKind.ALLOWANCE
        }
        else -> CellKind.WAY
    }

    /** "자격수당", "휴일 8시간 넘게", "야간 시간". */
    val name: String get() = when (val c = change) {
        is RuleChange.Premium -> premiumName(c)
        is RuleChange.Allowance -> c.name
        is RuleChange.Item -> c.item.name
        is RuleChange.Bonus -> c.name
        is RuleChange.Custom -> c.rule.name
        is RuleChange.Extra -> c.kindName
        is RuleChange.NightWindow -> "야간 시간"
        is RuleChange.MonthlyHours -> "통상시급 기준 시간"
        is RuleChange.OrdinaryHourly -> "통상시급"
        is RuleChange.OrdinaryItems -> "통상임금에 드는 것"
        is RuleChange.TaxFamily -> "세금 계산 가족"
        is RuleChange.Base -> if (c.type == WageType.MONTHLY) "기본급" else c.summary.substringBefore(' ')
        else -> c.summary
    }

    /** How much, in words: "매달 57,500원", "사람마다 달라요", "기본급의 10%", "1.5배 (시급의 50% 더)". */
    val how: String get() = when (val c = change) {
        is RuleChange.Premium -> premiumHow(c)
        is RuleChange.Allowance -> "${c.cadence.label} ${won(c.amount)}"
        is RuleChange.Item -> when {
            c.item.isPercent -> "매달 ${ItemShares.percentText(c.item)}"
            c.asked -> "사람마다 달라요 · 각자 넣어요"
            c.item.share == ItemShare.FIXED -> "회사 공통 · ${c.item.cadence.label} ${won(c.amount ?: 0)}"
            else -> "${c.item.cadence.label} ${won(c.amount ?: 0)}"
        }
        is RuleChange.Bonus -> c.yearlyPercent?.let { "연 ${ExtraRule.percent(it / 100)}" + if (c.basis != BonusBasis.BASE) " (${c.basis.label} 기준)" else "" }
            ?: "1회 ${won(c.amount ?: 0)}"
        is RuleChange.Custom -> c.rule.pay.describe() + if (c.rule.deduct) " 빼요" else ""
        is RuleChange.Extra -> c.rule.describe()
        is RuleChange.NightWindow -> "${RuleWhen.clock(c.start)}~${RuleWhen.clock(c.end)}시"
        is RuleChange.MonthlyHours -> "월 ${c.hours}시간"
        is RuleChange.OrdinaryHourly -> won(kotlin.math.round(c.hourly).toLong())
        is RuleChange.OrdinaryItems -> c.names.joinToString(", ")
        is RuleChange.TaxFamily -> "${c.family}명 (8~20세 자녀 ${c.children}명)"
        is RuleChange.Base -> won(c.amount)
        else -> ""
    }

    /** When it is paid, "" when always: "토요일 근무일", "3·6·9·12월 · 설·추석". */
    val whenText: String get() = when (val c = change) {
        is RuleChange.Custom -> if (c.rule.pay.monthly) "" else c.rule.whenever.describe(shiftNames)
        is RuleChange.Bonus -> listOfNotNull(c.months.sorted().joinToString("·").takeIf { it.isNotEmpty() }?.let { "${it}월" },
            c.festivals.joinToString("·") { it.label }.takeIf { it.isNotEmpty() }).joinToString(" · ")
        is RuleChange.Allowance -> if (c.cadence == Cadence.PER_SHIFT_TYPE && c.shiftTypeIds.isNotEmpty()) c.shiftTypeIds.joinToString("·") { shiftNames[it] ?: "근무" } else ""
        else -> ""
    }

    /** 근속·한도·기간: "근속 3년 이상 · 월 100,000원까지". */
    val limits: String get() = (change as? RuleChange.Custom)?.rule?.limits().orEmpty()

    /** In 통상임금 (true), out (false), or not a question for this 칸 (null). */
    val ordinary: Boolean? get() = when (val c = change) {
        is RuleChange.Allowance -> c.ordinary
        is RuleChange.Item -> c.item.ordinary
        else -> null
    }

    /** [RULE-ED-07] Each person puts in their own amount (a 사람마다 칸 with none). */
    val askedOfEach: Boolean get() = (change as? RuleChange.Item)?.asked == true

    /** The card's one line. */
    val summary: String get() = listOf(name, how, whenText, limits, if (ordinary == true) "통상임금" else "")
        .filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        const val SURE = 0.6

        /** [PHOTO-32] 휴일연장 is what people call 휴일 past 8 hours. */
        fun premiumName(c: RuleChange.Premium): String = when (c.target) {
            PremiumTarget.OVERTIME -> "연장 근무"
            PremiumTarget.NIGHT -> "야간 근무"
            PremiumTarget.HOLIDAY -> if (c.over8h) "휴일 8시간 넘게 (휴일연장)" else "휴일 근무"
        }

        /** [PHOTO-32] As a payslip's 계산 방법 says it: "2배 (시급의 100% 더)"; 야간 is paid on top of the hour itself. */
        fun premiumHow(c: RuleChange.Premium): String =
            if (c.target == PremiumTarget.NIGHT) "시급의 ${ExtraRule.percent(c.premium)} 더"
            else "${times(1 + c.premium)}배 (시급의 ${ExtraRule.percent(c.premium)} 더)"

        fun times(v: Double): String = if (kotlin.math.abs(v - kotlin.math.round(v)) < 1e-9) kotlin.math.round(v).toLong().toString()
        else "%.2f".format(v).trimEnd('0').trimEnd('.')

        /** A 칸 for [change] with an id of its own: the item's or rule's id, else one made from what it says. */
        fun of(change: RuleChange, on: Boolean = true, source: String = "", confidence: Double = 1.0, from: CellFrom = CellFrom.HAND,
               shiftNames: Map<Long, String> = emptyMap(), salt: Int = 0): RuleCell =
            RuleCell(idOf(change, salt), change, on, source, confidence, from, shiftNames)

        private fun idOf(change: RuleChange, salt: Int): String = when (change) {
            is RuleChange.Item -> change.item.id
            is RuleChange.Custom -> change.rule.id
            else -> "cell-" + (change.summary + "#" + salt).hashCode().toUInt().toString(16)
        }
    }
}

/**
 * [HOURLY-04] The values every 칸 is worked out on, kept apart from the 칸: the 통상시급 기준 시간 and the 통상시급 itself, each with
 * where it came from ([HourlySource]: 명세서·회사 규칙·광장·직접) and the words that showed it ([hoursWhy], [hourlyWhy]). Null leaves
 * the pay setup's own. [offers] are all such values read (명세서로 보면 180시간이에요), taken or not, for the card to show.
 */
data class CommonTerms(
    val hours: Int? = null,
    val hoursSource: HourlySource? = null,
    val hoursWhy: String = "",
    /** The 통상시급 set; 0 takes a set one off (worked out again from 통상임금 ÷ 기준 시간). */
    val hourly: Double? = null,
    val hourlySource: HourlySource? = null,
    val hourlyWhy: String = "",
    val roundUp: Boolean = false,
    val roundDown: Boolean = false,
    val estimated: Boolean = false,
    val offers: List<RuleFinding> = emptyList(),
) {
    fun toChanges(): List<RuleChange> = listOfNotNull(
        hours?.let { RuleChange.MonthlyHours(it, hoursSource ?: HourlySource.TYPED) },
        hourly?.let { RuleChange.OrdinaryHourly(it, roundUp, roundDown, estimated, hourlySource ?: HourlySource.TYPED) },
    )

    /** [finding] (기준 시간 or 통상시급) taken. */
    fun take(finding: RuleFinding): CommonTerms = when (val c = finding.change) {
        is RuleChange.MonthlyHours -> copy(hours = c.hours, hoursSource = c.source, hoursWhy = finding.sourceLine)
        is RuleChange.OrdinaryHourly -> copy(hourly = c.hourly, hourlySource = c.source, hourlyWhy = finding.sourceLine,
            roundUp = c.roundUp, roundDown = c.roundDown, estimated = c.estimated)
        else -> this
    }

    /** Whether [finding] is what is taken now. */
    fun taken(finding: RuleFinding): Boolean = when (val c = finding.change) {
        is RuleChange.MonthlyHours -> hours == c.hours && hoursSource == c.source
        is RuleChange.OrdinaryHourly -> hourly != null && kotlin.math.abs(hourly - c.hourly) < 0.005
        else -> false
    }

    /** [finding] no longer taken: its value goes back to the setup's own. */
    fun drop(finding: RuleFinding): CommonTerms = when (finding.change) {
        is RuleChange.MonthlyHours -> if (taken(finding)) copy(hours = null, hoursSource = null, hoursWhy = "") else this
        is RuleChange.OrdinaryHourly -> if (taken(finding)) copy(hourly = null, hourlySource = null, hourlyWhy = "", roundUp = false, roundDown = false, estimated = false) else this
        else -> this
    }

    /** [HOURLY-04] Hours the user picked (칩·직접): [HourlySource.TYPED], or the 광장's / 명세서's when that is where the chip came from. */
    fun withHours(h: Int, source: HourlySource = HourlySource.TYPED): CommonTerms = copy(hours = h, hoursSource = source, hoursWhy = "")

    /** [HOURLY-04] A 통상시급 the user said (직접, or 기본급의 몇 %); 0 or less goes back to working it out. */
    fun withHourly(v: Double, source: HourlySource = HourlySource.TYPED): CommonTerms =
        copy(hourly = if (v > 0) v else 0.0, hourlySource = source.takeIf { v > 0 }, hourlyWhy = "", roundUp = false, roundDown = false, estimated = false)

    companion object {
        /** Read changes that are common values, not 칸. */
        fun isCommon(change: RuleChange): Boolean = change is RuleChange.MonthlyHours || change is RuleChange.OrdinaryHourly
    }
}

/**
 * [RULE-ED-01] The company's rules: the common values and the 칸, and the sentences a photo or words had that were not read
 * ([unread], each a '직접 넣기' row).
 */
data class RuleSheet(
    val common: CommonTerms = CommonTerms(),
    val cells: List<RuleCell> = emptyList(),
    val unread: List<String> = emptyList(),
) {
    /** What goes onto the pay setup: the common values, then the 칸 that are on, in the list's order. */
    fun toChanges(): List<RuleChange> = common.toChanges() + cells.filter { it.on }.map { it.change }

    fun apply(profile: PayProfile, month: YearMonth): PayProfile = RuleText.apply(profile, toChanges(), month)

    fun add(cell: RuleCell): RuleSheet = copy(cells = cells + cell)

    /** [cell] where the one of its id was (or at the end). */
    fun put(cell: RuleCell): RuleSheet {
        val i = cells.indexOfFirst { it.id == cell.id }
        return if (i < 0) add(cell) else copy(cells = cells.toMutableList().also { it[i] = cell })
    }

    fun remove(id: String): RuleSheet = copy(cells = cells.filterNot { it.id == id })

    fun toggle(id: String): RuleSheet = copy(cells = cells.map { if (it.id == id) it.copy(on = !it.on) else it })

    /** The '직접 넣기' row [i] done (made into a 칸 or passed over). */
    fun dropUnread(i: Int): RuleSheet = if (i in unread.indices) copy(unread = unread.filterIndexed { j, _ -> j != i }) else this

    fun withCommon(terms: CommonTerms): RuleSheet = copy(common = terms)

    val onCount: Int get() = cells.count { it.on }

    val isEmpty: Boolean get() = cells.isEmpty() && unread.isEmpty() && common.offers.isEmpty() && common.toChanges().isEmpty()

    companion object {
        /**
         * [PHOTO-30] What a photo or words were read as: the 기준 시간·통상시급 found go to [common] (taken when read well), every
         * other rule is a 칸 (ticked when read well), and the sentences not read are '직접 넣기' rows.
         */
        fun read(findings: List<RuleFinding>, unread: List<String> = emptyList(), from: CellFrom = CellFrom.PHOTO,
                 shiftNames: Map<Long, String> = emptyMap()): RuleSheet {
            val (common, cells) = findings.partition { CommonTerms.isCommon(it.change) }
            var terms = CommonTerms(offers = common)
            common.filter { it.confidence >= RuleCell.SURE }.forEach { terms = terms.take(it) }
            val made = cells.mapIndexed { i, f ->
                RuleCell.of(f.change, on = f.confidence >= RuleCell.SURE, source = f.sourceLine, confidence = f.confidence,
                    from = if (f.change is RuleChange.Premium && f.sourceLine.startsWith("명세서로 보면")) CellFrom.SLIP else from,
                    shiftNames = shiftNames, salt = i)
            }
            return RuleSheet(terms, made, unread)
        }

        /**
         * [RULE-ED-05] The company's own 칸 a pay setup has (회사 규칙 › 우리 회사 항목): its own rules, then its allowances (not 상여,
         * not a 고정 연장수당, not the 13번째 몫 the app makes).
         */
        fun of(profile: PayProfile, shiftNames: Map<Long, String> = emptyMap()): RuleSheet = RuleSheet(
            cells = profile.items.filter(::isOwnItem).map { RuleCell.of(RuleChange.Item(it), from = CellFrom.SAVED) } +
                profile.customRules.map { RuleCell.of(RuleChange.Custom(it, shiftNames), on = it.on, from = CellFrom.SAVED, shiftNames = shiftNames) },
        )

        /** An allowance shown and changed as a 칸. */
        fun isOwnItem(item: PayItem): Boolean = item.cadence != Cadence.BONUS && !item.isFixedOvertime && item.id != Unknowns.ANNUAL_13_ITEM

        /**
         * [RULE-ED-05] [cell] made or changed on a pay setup straight away (회사 규칙 화면): an allowance or a rule where it was (the
         * order the user sees stays), anything else as [RuleText.apply] puts it.
         */
        fun put(profile: PayProfile, cell: RuleCell, month: YearMonth): PayProfile = when (val c = cell.change) {
            is RuleChange.Custom -> {
                val rule = c.rule.copy(on = cell.on)
                val i = profile.customRules.indexOfFirst { it.id == rule.id }
                profile.copy(customRules = if (i >= 0) profile.customRules.toMutableList().also { it[i] = rule } else (profile.customRules + rule).takeLast(CustomRule.MAX))
            }
            else -> RuleText.apply(profile, listOf(c), month)
        }

        /** [RULE-ED-05] [cell] taken off a pay setup. */
        fun remove(profile: PayProfile, cell: RuleCell): PayProfile = when (val c = cell.change) {
            is RuleChange.Custom -> profile.copy(customRules = profile.customRules.filterNot { it.id == c.rule.id })
            is RuleChange.Item -> profile.copy(items = profile.items.filterNot { it.id == c.item.id },
                estimatedFields = profile.estimatedFields - Unknowns.item(c.item.id) - c.item.id)
            else -> profile
        }

        /** [RULE-ED-05] A rule switched on or off where it is; an allowance has no switch. */
        fun setOn(profile: PayProfile, cell: RuleCell, on: Boolean): PayProfile = when (val c = cell.change) {
            is RuleChange.Custom -> profile.copy(customRules = profile.customRules.map { if (it.id == c.rule.id) it.copy(on = on) else it })
            else -> profile
        }
    }
}

/**
 * [HOURLY-04] 1.30 AW (사용자 2026-10-27 "통상시급의 뜻을 잘 모르겠다. 통상시급이 개인정보라면 기본급의 %로 통상시급이 결정되는지 물어보든지,
 * 명세서와 규칙 기반으로 자동 적용"): what the 통상시급 card says. 통상시급 is the pay for one hour that 연장·야간·휴일 are counted on
 * (시간당 기본 단가): 통상임금 (기본급 + 매달 같은 수당 + 상여 월할) ÷ 기준 시간, or the one a payslip's lines were paid on.
 */
object HourlyFacts {
    /** The 통상시급 a monthly setup uses in [month], and what it comes from. */
    data class View(
        val hourly: Long,
        /** 통상임금 a month (기본급 + 통상 수당 + 상여 월할). */
        val ordinary: Long,
        val base: Long,
        val hours: Int,
        /** The 통상시급 was set (명세서·직접), not worked out. */
        val set: Boolean,
        val source: HourlySource?,
        val hoursSource: HourlySource?,
    ) {
        /** 통상임금 as a share of 기본급, in whole %: 100 when it is 기본급 alone. */
        val percentOfBase: Int get() = if (base <= 0) 100 else (ordinary * 100.0 / base).roundToLong().toInt()

        /** How it is worked out, in words: "통상임금 3,891,245원 ÷ 180시간". */
        val formula: String get() = if (set) "명세서·직접 넣은 값이에요" else "통상임금 ${won(ordinary)} ÷ ${hours}시간"
    }

    /** Null for pay that is not monthly (시급·일당 are their own hour) or with no 기본급 yet. */
    fun of(profile: PayProfile, month: YearMonth): View? {
        if (profile.wageType != WageType.MONTHLY) return null
        val wage = PayCalculator.ordinaryWage(profile, month)
        if (wage.base <= 0) return null
        val set = profile.ordinaryHourly?.takeIf { it > 0 }
        val hourly = set?.roundToLong() ?: (wage.total.toDouble() / profile.monthlyHours.coerceAtLeast(1)).roundToLong()
        return View(hourly, wage.total, wage.base, profile.monthlyHours, set != null,
            if (set != null) profile.ordinarySource else profile.hoursSource, profile.hoursSource)
    }

    /**
     * [HOURLY-04] "통상임금이 기본급의 몇 %인가요?": the 통상시급 when 통상임금 is [percent]% of [base], on [hours]. 100% is 기본급 alone.
     * Asked in place of the 통상시급 itself (a person's own number); the % is the company's (기본급 + 매달 같은 수당 + 상여 몫).
     */
    fun fromPercent(base: Long, percent: Double, hours: Int): Double =
        if (base <= 0 || hours <= 0 || percent <= 0) 0.0 else Math.round(base * percent / 100.0 / hours * 100) / 100.0

    /** The percent chips: 기본급만, and what 매달 수당·상여 usually add. */
    val PERCENT_CHOICES = listOf(100, 120, 150, 200)
}

/**
 * [RULE-ED-03] A 칸 people often put in, chosen by name: the rest starts filled (매달 정액, 통상임금, 22~06시 …), so the user only
 * says how much. [way] is how it is paid; for a 가산 ([CellWay.PREMIUM]) [target]·[over8h]·[rate] are the premium's.
 */
data class CellPreset(
    val name: String,
    val group: PresetGroup,
    val way: CellWay,
    val ordinary: Boolean = false,
    val share: ItemShare = ItemShare.PERSONAL,
    val basis: BonusBasis = BonusBasis.BASE,
    val target: PremiumTarget? = null,
    val over8h: Boolean = false,
    val rate: Double? = null,
    /** 22~06 for 심야·야간 시간 칸. */
    val window: Pair<Int, Int>? = null,
    val dayKind: RuleDayKind = RuleDayKind.ANY,
    /** Other names it goes by, for search and for finding it in a sentence. */
    val also: List<String> = emptyList(),
) {
    /** A rate of the law (연장·야간·휴일), not a rule of the company's own. */
    val isPremium: Boolean get() = way == CellWay.PREMIUM

    /** The change a premium preset is, at [rate] (its own when not given). */
    fun premium(rate: Double = this.rate ?: 0.5): RuleChange.Premium = RuleChange.Premium(target ?: PremiumTarget.OVERTIME, rate, over8h)

    fun matches(query: String): Boolean {
        val q = query.filterNot { it.isWhitespace() }
        if (q.isEmpty()) return true
        return (listOf(name) + also).any { it.filterNot(Char::isWhitespace).contains(q) || q.contains(it.filterNot(Char::isWhitespace)) && it.length >= 2 }
    }
}

/** [RULE-ED-03] The groups of [CellPreset] chips, in the order they show. */
enum class PresetGroup(val label: String) {
    ALLOWANCE("매달 받는 수당"),
    PREMIUM("더 받는 근무 (몇 배)"),
    PER_WORK("근무할 때마다"),
    DEDUCT("빠지는 돈"),
}

/** [RULE-ED-03] How a [CellPreset] is paid, as the builder's kinds (앱 ItemKind) and the law's premiums. */
enum class CellWay { MONTHLY, PER_DAY, PER_SHIFT, PER_HOUR, PERCENT, PREMIUM, DEDUCT_MONTHLY, DEDUCT_PER_DAY }

/** [RULE-ED-03] The chips of '칸 더하기', and finding one in a sentence a photo could not read. */
object CellPresets {
    val ALL: List<CellPreset> = listOf(
        CellPreset("자격수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, ordinary = true, also = listOf("면허수당", "자격")),
        CellPreset("직책수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, ordinary = true, also = listOf("직무수당", "직급수당")),
        CellPreset("근속수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, ordinary = true, also = listOf("호봉")),
        CellPreset("교대수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, ordinary = true, share = ItemShare.FIXED),
        CellPreset("위험수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, ordinary = true, share = ItemShare.FIXED, also = listOf("유해수당")),
        CellPreset("식대", PresetGroup.ALLOWANCE, CellWay.MONTHLY, share = ItemShare.FIXED, also = listOf("식비", "중식대")),
        CellPreset("교통비", PresetGroup.ALLOWANCE, CellWay.MONTHLY, share = ItemShare.FIXED, also = listOf("차량유지비", "통근")),
        CellPreset("가족수당", PresetGroup.ALLOWANCE, CellWay.MONTHLY, also = listOf("자녀수당", "부양")),
        CellPreset("기본급2", PresetGroup.ALLOWANCE, CellWay.PERCENT, ordinary = true, share = ItemShare.PERCENT, also = listOf("기본급II", "기본급Ⅱ")),
        CellPreset("위험예지", PresetGroup.ALLOWANCE, CellWay.PERCENT, share = ItemShare.PERCENT, basis = BonusBasis.ORDINARY, also = listOf("위험예지훈련")),
        CellPreset("연장 근무", PresetGroup.PREMIUM, CellWay.PREMIUM, target = PremiumTarget.OVERTIME, rate = 0.5, also = listOf("연장수당", "시간외", "잔업")),
        CellPreset("야간 근무", PresetGroup.PREMIUM, CellWay.PREMIUM, target = PremiumTarget.NIGHT, rate = 0.5, also = listOf("야간수당", "야간근로")),
        CellPreset("휴일 근무", PresetGroup.PREMIUM, CellWay.PREMIUM, target = PremiumTarget.HOLIDAY, rate = 0.5, also = listOf("휴일수당", "휴일근로", "특근")),
        CellPreset("휴일연장", PresetGroup.PREMIUM, CellWay.PREMIUM, target = PremiumTarget.HOLIDAY, over8h = true, rate = 1.0, also = listOf("휴일 8시간 넘게", "휴일연장수당")),
        CellPreset("나이트 수당", PresetGroup.PER_WORK, CellWay.PER_SHIFT, share = ItemShare.FIXED, also = listOf("야간근무수당", "나이트")),
        CellPreset("토요일 근무", PresetGroup.PER_WORK, CellWay.PER_SHIFT, share = ItemShare.FIXED, also = listOf("토요일")),
        CellPreset("출근 식대", PresetGroup.PER_WORK, CellWay.PER_DAY, share = ItemShare.FIXED, also = listOf("일한 날 식대")),
        CellPreset("심야 시간 수당", PresetGroup.PER_WORK, CellWay.PER_HOUR, window = 22 to 6, also = listOf("심야수당", "심야")),
        CellPreset("기숙사비", PresetGroup.DEDUCT, CellWay.DEDUCT_MONTHLY, also = listOf("사택관리비", "사택")),
        CellPreset("식대 공제", PresetGroup.DEDUCT, CellWay.DEDUCT_PER_DAY, also = listOf("식비 공제")),
    )

    /** The chips for what is typed, in group order; all of them for nothing typed. */
    fun search(query: String): List<CellPreset> = ALL.filter { it.matches(query) }

    /** An exact chip for [name] (자격수당, 휴일연장 …), or null. */
    fun named(name: String): CellPreset? {
        val n = name.filterNot(Char::isWhitespace)
        return ALL.firstOrNull { p -> (listOf(p.name) + p.also).any { it.filterNot(Char::isWhitespace) == n } }
    }

    /**
     * [PHOTO-32] The chip a sentence a photo could not read is about ("근속수당은 근속 3년 이상 월 5만원" → 근속수당), so '직접 넣기'
     * starts on it instead of putting the whole sentence in the name (1.29 did).
     */
    fun inSentence(sentence: String): CellPreset? {
        val s = sentence.filterNot(Char::isWhitespace)
        return ALL.flatMap { p -> (listOf(p.name) + p.also).filter { it.length >= 2 }.map { it.filterNot(Char::isWhitespace) to p } }
            .filter { (n, _) -> s.contains(n) }.maxByOrNull { (n, _) -> n.length }?.second
    }

    /** A name in [sentence] that reads as an allowance's ("○○수당", "○○비"), when no chip is. */
    fun nameIn(sentence: String): String? =
        Regex("""[가-힣A-Za-z0-9]{1,10}(수당|비|급|금)(?![가-힣])""").find(sentence)?.value?.takeIf { it.length in 2..16 && it != "통상임금" && it != "기본급" }
}
