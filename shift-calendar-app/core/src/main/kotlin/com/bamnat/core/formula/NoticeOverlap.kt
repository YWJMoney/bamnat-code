package com.bamnat.core.formula

/**
 * 1.39 [NTC-01] 겹치는 알림 (사용자 2026-11-08 "부품 기능을 넣을때 기존의 설정을 바꿀까요? 해서 번잡하지 않게끔. 지금 두 개가 겹치는
 * 게 뜨고 있어"): 장터 '오늘 요약 한 줄' 규칙(하루 처음 앱을 열면 "오늘 {근무} {근무시작}~{근무끝} · 이달 쓴 돈")이 이미 켜 둔
 * 잠금 화면 '오늘 근무'와 같은 말을 해서 알림 두 개가 함께 떴어요.
 *
 * A rule's 알림 overlaps
 * - one of the app's own notices ([BuiltIn]) when it says the same thing at about the same time, or
 * - another 알림 rule of mine that comes at the same time and reads the same numbers ([twin]).
 *
 * What overlaps is asked before it goes in (기존 설정을 바꿀까요?) and shown in the list of notices; a rule that only repeats
 * 오늘 근무 while the lock-screen notice is on is folded into that one notice ([foldsIntoToday], [withoutShift]).
 */
object NoticeOverlap {
    /** The app's own notices a rule can repeat; [key] is the setting's name (as recipe pieces keep settings). */
    enum class BuiltIn(val key: String, val label: String, val does: String) {
        TODAY("todayShiftNotice", "잠금 화면 오늘 근무", "오늘·내일 근무를 잠금 화면과 알림창에 늘 보여 줘요"),
        DAY_CLOSE("dayCloseNotice", "하루 마감", "근무가 끝나고 30분 뒤(쉬는 날 저녁 9시) 오늘 쓴 돈을 알려 줘요");

        companion object { fun of(key: String): BuiltIn? = entries.firstOrNull { it.key == key } }
    }

    /** [rule] overlaps [builtIn] or my rule [other]. */
    data class Hit(val rule: LogicRule, val builtIn: BuiltIn? = null, val other: LogicRule? = null) {
        /** "‘오늘 요약’은 켜 둔 ‘잠금 화면 오늘 근무’와 같은 말을 해요". */
        fun say(): String = "‘${rule.name}’" + com.bamnat.core.recipe.RecipeLint.topic(rule.name) + " " + when {
            builtIn != null -> "켜 둔 ‘${builtIn.label}’ 알림과 같은 말을 해요"
            else -> "내 ‘${other?.name}’ 규칙과 같은 때 같은 것을 알려요"
        }
    }

    private val SHIFT = setOf(FormulaName.SHIFT.ko, FormulaName.ROLE.ko, FormulaName.START.ko, FormulaName.END.ko)
    private val DAY_SPEND = setOf(FormulaName.SPEND.ko)
    /** Names every day has (날짜·요일…): two rules both saying the date do not say the same thing. */
    private val COMMON = FormulaName.entries.filter { it.group == FormulaName.Group.DAY }.map { it.ko }.toSet()

    /** The names the {formulas} in [src] read. */
    fun names(src: String): Set<String> = FormulaText.parse(src).getOrNull().orEmpty()
        .filterIsInstance<FormulaText.Part.Calc>().flatMap { it.formula.names }.toSet()

    private fun condNames(cond: String): Set<String> =
        if (cond.isBlank()) emptySet() else (Formula.parse(cond) as? Parsed.Ok)?.formula?.names.orEmpty()

    /** Its 알림 acts done at once (one that waits [LogicAct.after] comes at another time). */
    private fun notices(r: LogicRule): List<LogicAct> = r.acts.filter { it.verb == LogicVerb.NOTIFY && it.after == null }

    private fun said(r: LogicRule): Set<String> = notices(r).flatMap { names(it.arg) }.toSet()

    private fun hour(at: String?): Int? = at?.substringBefore(':')?.toIntOrNull()

    /** The app notice [r] repeats, or null. */
    fun builtIn(r: LogicRule): BuiltIn? {
        val acts = notices(r)
        if (acts.isEmpty()) return null
        val said = said(r)
        val evening = r.on == RuleTrigger.END || r.on == RuleTrigger.DAILY && (hour(r.at) ?: 0) >= 18
        val words = acts.joinToString(" ") { it.arg }
        return when {
            (r.on == RuleTrigger.OPEN || r.on == RuleTrigger.DAILY) && said.any { it in SHIFT } -> BuiltIn.TODAY
            evening && ((said + condNames(r.cond)).any { it in DAY_SPEND } || "가계부" in words || "오늘 쓴 돈" in words) -> BuiltIn.DAY_CLOSE
            else -> null
        }
    }

    private fun sameTime(a: LogicRule, b: LogicRule): Boolean = a.on == b.on && when (a.on) {
        RuleTrigger.OPEN -> true
        RuleTrigger.DAILY -> hour(a.at) == hour(b.at)
        RuleTrigger.START, RuleTrigger.END -> a.min == b.min && (a.role == null || b.role == null || a.role == b.role)
        RuleTrigger.MONEY, RuleTrigger.MEMO, RuleTrigger.ALARM_OFF -> true
        else -> false
    }

    /** Two different 알림 rules that come at the same time and say the same numbers (or repeat the same app notice). */
    fun twin(a: LogicRule, b: LogicRule): Boolean {
        if (a.key == b.key || notices(a).isEmpty() || notices(b).isEmpty() || !sameTime(a, b)) return false
        val bi = builtIn(a)
        return (said(a) - COMMON).intersect(said(b) - COMMON).isNotEmpty() || bi != null && bi == builtIn(b)
    }

    /**
     * What [got] (a piece about to go in) overlaps: app notices that are [on], and my rules for which [ruleOn] holds. One hit a rule
     * of theirs (an app notice before a rule of mine). A rule I already have (same key) is not new and overlaps nothing.
     */
    fun check(mine: Logic, got: Logic, on: Set<BuiltIn>, ruleOn: (LogicRule) -> Boolean = { true }): List<Hit> {
        val had = mine.rules.map { it.key }.toSet()
        return got.rules.filter { it.key !in had }.mapNotNull { r ->
            builtIn(r)?.takeIf { it in on }?.let { Hit(r, builtIn = it) }
                ?: mine.rules.firstOrNull { m -> ruleOn(m) && twin(m, r) }?.let { Hit(r, other = it) }
        }
    }

    /** The overlaps among what this phone has on now (for the list of notices and turning an app notice on). */
    fun now(mine: Logic, on: Set<BuiltIn>, ruleOn: (LogicRule) -> Boolean): List<Hit> {
        val live = mine.rules.filter(ruleOn)
        return live.mapIndexedNotNull { i, r ->
            builtIn(r)?.takeIf { it in on }?.let { Hit(r, builtIn = it) }
                ?: live.take(i).firstOrNull { twin(it, r) }?.let { Hit(r, other = it) }
        }
    }

    /** The rules of mine (on) that repeat [b], asked about when [b] is turned on. */
    fun repeating(mine: Logic, b: BuiltIn, ruleOn: (LogicRule) -> Boolean): List<LogicRule> =
        mine.rules.filter { ruleOn(it) && builtIn(it) == b }

    /**
     * A rule that only says 오늘 근무 (and maybe more) as a plain 알림, which the lock-screen 오늘 근무 notice says already: while that
     * is on, the rest of its words go into that one notice instead of a second one. One with 알림 단추 keeps its own notice.
     */
    fun foldsIntoToday(r: LogicRule): Boolean = builtIn(r) == BuiltIn.TODAY

    private val SEP = Regex("""\s*·\s*|\n""")

    /** [arg] without its parts (split at " · " or a line) that say today's shift: "오늘 {근무} {근무시작}~{근무끝} · 이달 쓴 돈 …" → "이달 쓴 돈 …". */
    fun withoutShift(arg: String): String = arg.split(SEP).filter { seg -> seg.isNotBlank() && names(seg).none { it in SHIFT } }.joinToString(" · ")

    /**
     * Words once worked out, tidied: a "~" left between two empty times (a day off: "오늘 휴무 ~ · 이달 쓴 돈") goes, with the gaps and
     * the " · " it leaves alone.
     */
    fun tidy(text: String): String = text.lines().joinToString("\n") { line ->
        line.replace(Regex("""(^|\s)~\s*(?=·|$)"""), "$1")
            .replace(Regex("""\s*·\s*(·\s*)+"""), " · ")
            .replace(Regex("""^\s*·\s*|\s*·\s*$"""), "")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }.trim()
}
