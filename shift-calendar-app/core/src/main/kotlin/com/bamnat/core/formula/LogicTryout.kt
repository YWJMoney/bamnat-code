package com.bamnat.core.formula

import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.39 [MKC-03] 도구 전후 해 보기 (사용자 2026-11-07 "해당 도구를 누르면 적용 전후 모습을 직접 조작하면서 바뀐 점을 알 수 있게"): what a
 * 장터 규칙·단추 would do on this phone, worked out on the phone's own days ([DayFactsBook]) and never done. A button pressed on the
 * example, a rule's 상황 made on a day: whether it shows or runs that day, why, and what would happen, in the receiver's words.
 *
 * The received form is used ([Logic.received]): 가계부 금액 held as they would be when put in.
 */
object LogicTryout {
    /** One try: [runs] whether it would act, [why] the reason in a line, [result] what would happen ("" when nothing would). */
    data class Try(val date: LocalDate, val runs: Boolean, val why: String, val result: String, val at: LocalTime? = null)

    /** What [act] would do on [scope]'s day, as it would be said after ("가계부에 지출 ‘커피 4,500원’ 한 줄이 생겨요"). */
    fun result(act: LogicAct, scope: FormulaScope): String {
        val words = runCatching { FormulaText.render(act.arg, scope) }.getOrDefault(act.arg).trim()
        val money = act.amount?.let { " " + LogicAct.won(it) }.orEmpty()
        return when (act.verb) {
            LogicVerb.NOTIFY -> "‘${words.ifEmpty { "알림" }}’ 알림이 떠요"
            LogicVerb.MEMO_TEMPLATE -> "‘${words.ifEmpty { "메모" }}’ 메모 틀이 열려요"
            LogicVerb.SCREEN -> "${LogicVerb.SCREENS[act.arg] ?: "화면"} 화면이 열려요"
            LogicVerb.NEW_MEMO -> "그날 메모 ‘${words.ifEmpty { "메모" }}’가 생겨요"
            LogicVerb.CHECK_RESET -> if (act.arg.isEmpty()) "그날 체크 목록이 모두 비워져요" else "‘${act.arg}’ 체크가 모두 비워져요"
            LogicVerb.ADD_MONEY -> "가계부에 지출 ‘${words.ifEmpty { "기타" }}$money’ 한 줄이 생겨요"
            LogicVerb.ADD_INCOME -> "가계부에 수입 ‘${words.ifEmpty { "기타" }}$money’ 한 줄이 생겨요"
            LogicVerb.MEMO_LINE -> "그날 메모에 ‘${words.ifEmpty { "한 줄" }}’ 한 줄이 더해져요"
            LogicVerb.ENVELOPE -> (if (act.arg.isEmpty()) "맨 위 봉투에" else "‘${act.arg}’ 봉투에") + (if (money.isEmpty()) " 돈이" else "${money}이") + " 들어가요"
        }
    }

    /** [b] pressed on [date]: hidden on a day its 보이는 조건 is false. */
    fun press(b: LogicButton, date: LocalDate, facts: DayFactsBook): Try {
        val scope = facts.scope(date)
        if (b.show.isNotEmpty() && Formula.eval(b.show, scope)?.truthy != true)
            return Try(date, false, "이날은 단추가 안 보여요 (${EasyWhen.read(b.show)?.say()?.let(::ifWord) ?: "조건이 맞는 날"}만 보여요)", "")
        return Try(date, true, "누르면", result(b.act, scope))
    }

    /**
     * [r]'s 상황 made on [date]: for 근무 시작·끝 the day's work must be the kind it waits for (a day off only for a rule on 쉬는 날), for
     * 매일 its hour; then its 조건 on that day's values.
     */
    fun rule(r: LogicRule, date: LocalDate, facts: DayFactsBook): Try {
        val v = facts.values(date)
        var at: LocalTime? = null
        if (r.on == RuleTrigger.START || r.on == RuleTrigger.END) {
            val kind = v[FormulaName.ROLE.ko] as? String
            val off = v[FormulaName.OFF.ko] == true
            val wanted = r.role?.let { DayFactsBook.ROLE_WORDS[it] }
            if (off && r.role != "off") return Try(date, false, "이날은 쉬는 날이라 근무 ${if (r.on == RuleTrigger.START) "시작" else "끝"}이 없어요", "")
            if (wanted != null && kind != wanted) return Try(date, false, "이날은 ${kind?.ifEmpty { null } ?: "근무 없음"}이라 ${wanted} 근무를 기다리는 이 규칙은 쉬어요", "")
            val t = (v[(if (r.on == RuleTrigger.START) FormulaName.START else FormulaName.END).ko] as? String)?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            if (t == null && r.role != "off") return Try(date, false, "이날 근무에 ${if (r.on == RuleTrigger.START) "시작" else "끝"} 시각이 없어요", "")
            at = t?.plusMinutes(r.min.toLong())
        }
        if (r.on == RuleTrigger.DAILY) at = runCatching { LocalTime.parse(r.at ?: "09:00") }.getOrNull()
        val scope = facts.scope(date)
        if (r.cond.isNotEmpty() && Formula.eval(r.cond, scope)?.truthy != true)
            return Try(date, false, "조건(${EasyWhen.read(r.cond)?.say() ?: "수식"})이 이날은 맞지 않아요", "", at)
        val whenWords = at?.let { "%d:%02d에".format(it.hour, it.minute) } ?: r.sayWhen()
        return Try(date, true, whenWords, result(r.act, scope), at)
    }

    /** The days of [days] on which [r] would run by itself (근무 시작·끝·매일), for "앞으로 2주 동안 N번". Others wait for a tap or a save. */
    fun runsOn(r: LogicRule, days: List<LocalDate>, facts: DayFactsBook): List<LocalDate> = days.filter { rule(r, it, facts).runs }

    /** Whether [r] runs by itself on a time (근무 시작·끝·매일), not on something done (날짜 누르기, 메모·가계부 저장, 앱 열기). */
    fun timed(r: LogicRule): Boolean = r.on == RuleTrigger.START || r.on == RuleTrigger.END || r.on == RuleTrigger.DAILY

    /** The 상황 to make for [r], said as a button ("근무 끝 만들어 보기", "메모 저장해 보기"). */
    fun situation(r: LogicRule): String = when (r.on) {
        RuleTrigger.TAP -> "이날을 눌러 보기"
        RuleTrigger.HOLD -> "이날을 길게 눌러 보기"
        RuleTrigger.START -> "근무 시작 만들어 보기"
        RuleTrigger.END -> "근무 끝 만들어 보기"
        RuleTrigger.DAILY -> "${r.at ?: "09:00"} 만들어 보기"
        RuleTrigger.MEMO -> "메모 저장해 보기"
        RuleTrigger.OPEN -> "앱 처음 열어 보기"
        RuleTrigger.MONEY -> "가계부에 적어 보기"
    }
}
