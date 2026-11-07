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
            // 1.39 [FX-03] 값 칸: what it would be after (the default when the phone has counted none yet).
            LogicVerb.VAL_ADD -> {
                val by = act.by ?: 1.0
                val now = scope.value(act.arg)?.number ?: 0.0
                "‘${act.arg}’ 값이 ${Formula.numText(now)} → ${Formula.numText(now + by)}이 돼요"
            }
            LogicVerb.VAL_SET -> "‘${act.arg}’ 값이 ${Formula.numText(act.by ?: 0.0)}이 돼요"
            LogicVerb.VAL_STAMP -> "‘${act.arg}’에 지금 시각이 적혀요"
            LogicVerb.VAL_TEXT -> "‘${act.arg}’에 ‘${runCatching { FormulaText.render(act.text, scope) }.getOrDefault(act.text).take(24)}’이 적혀요"
        } + (act.after?.let { " (${LogicAct.afterWords(it)} 뒤에)" } ?: "")
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
        val holds = r.cond.isEmpty() || Formula.eval(r.cond, scope)?.truthy == true
        if (!holds && r.otherwise.isEmpty())
            return Try(date, false, "조건(${EasyWhen.read(r.cond)?.say() ?: "수식"})이 이날은 맞지 않아요", "", at)
        val whenWords = (at?.let { "%d:%02d에".format(it.hour, it.minute) } ?: r.sayWhen()) + (if (!holds) " 조건이 맞지 않아 ‘아니면’으로" else "")
        // 1.39 [FX-11] 동작 줄: each act's result in order.
        return Try(date, true, whenWords, r.steps(holds).joinToString(" · ") { result(it, scope) }, at)
    }

    /**
     * 1.39 [MKD-04] 값 칸 해 보기: the work's own numbers as tried on the 장터 page, never kept. Each starts at its [LogicValue.start];
     * a press moves them as the phone would ([ValueBook.add]·[ValueBook.set] limits), and 값이 바뀌면 rules ([RuleTrigger.VALUE]) follow,
     * a 목표 rule only when the goal is first reached, as [com.bamnat.app.logic.LogicRunner] does.
     */
    data class Tried(val nums: Map<String, Double> = emptyMap(), val texts: Map<String, String> = emptyMap()) {
        /** "3/8잔", "18:30", the 글 or "비어 있음". */
        fun show(v: LogicValue, today: LocalDate): String =
            if (v.kind == ValueKind.TEXT) texts[v.name].orEmpty().ifEmpty { "비어 있음" } else v.show(nums[v.name] ?: v.start, today)

        /** How far toward the goal (0..1), or null without one. */
        fun toward(v: LogicValue): Float? = v.goal?.takeIf { it > 0 && v.kind == ValueKind.NUM }
            ?.let { ((nums[v.name] ?: v.start) / it).toFloat().coerceIn(0f, 1f) }
    }

    /** One press: the values after and what happened, in order ("‘물 잔’ 값이 2 → 3이 돼요", "그러면 ‘다 마셨어요’: …"). */
    data class Pressed(val after: Tried, val said: List<String>)

    fun start(l: Logic): Tried = Tried(l.values.filter { it.kind != ValueKind.TEXT }.associate { it.name to it.start })

    /** [b] pressed on [date] at [at] with the values [was]; nothing is written. */
    fun press(l: Logic, b: LogicButton, was: Tried, date: LocalDate, facts: DayFactsBook, at: java.time.LocalDateTime): Pressed {
        val said = ArrayList<String>()
        var now = was
        val base = facts.scope(date)
        val holds = b.cond.isEmpty() || Formula.eval(b.cond, over(base, now, l))?.truthy == true
        val steps = b.steps(holds)
        if (steps.isEmpty()) return Pressed(was, listOf("조건(${EasyWhen.read(b.cond)?.say() ?: "수식"})이 맞지 않아 아무 일도 없어요"))
        fun go(acts: List<LogicAct>, depth: Int) {
            for (act in acts) {
                val scope = over(base, now, l)
                said += (if (depth > 0) "  " else "") + result(act, scope)
                if (!act.verb.value) continue
                val def = l.value(act.arg) ?: continue
                val before = now.nums[act.arg] ?: def.start
                now = when (act.verb) {
                    LogicVerb.VAL_ADD -> now.copy(nums = now.nums + (act.arg to (before + (act.by ?: 1.0)).coerceIn(-ValueBook.LIMIT, ValueBook.LIMIT)))
                    LogicVerb.VAL_SET -> now.copy(nums = now.nums + (act.arg to (act.by ?: 0.0).coerceIn(-ValueBook.LIMIT, ValueBook.LIMIT)))
                    LogicVerb.VAL_STAMP -> now.copy(nums = now.nums + (act.arg to ValueBook.minutes(at)))
                    else -> now.copy(texts = now.texts + (act.arg to runCatching { FormulaText.render(act.text, scope) }.getOrDefault(act.text).take(40)))
                }
                if (depth >= CHAIN) continue
                val after = now.nums[act.arg] ?: def.start
                for (r in l.rules.filter { it.on == RuleTrigger.VALUE && it.of == act.arg }) {
                    if (r.goal) { val g = def.goal ?: continue; if (!(before < g && after >= g)) continue }
                    val ok = r.cond.isEmpty() || Formula.eval(r.cond, over(base, now, l))?.truthy == true
                    val next = r.steps(ok)
                    if (next.isEmpty()) continue
                    said += "그러면 ‘${r.name}’ 규칙이 돌아요"
                    go(next, depth + 1)
                }
            }
        }
        go(steps, 0)
        return Pressed(now, said)
    }

    /** As the phone's runner stops a chain of 값이 바뀌면 rules. */
    private const val CHAIN = 3

    /** [base] with the work's 값 칸 read from [t]. */
    private fun over(base: FormulaScope, t: Tried, l: Logic): FormulaScope = object : FormulaScope {
        override fun get(name: String): Value? = base.get(name)
        override fun input(label: String): Value? = base.input(label)
        override fun value(name: String): Value? = l.value(name)?.let { v ->
            if (v.kind == ValueKind.TEXT) Value.Text(t.texts[name].orEmpty()) else Value.Num(t.nums[name] ?: v.start)
        } ?: base.value(name)
        override fun series(name: String): ValueSeries? = base.series(name)
    }

    /** The days of [days] on which [r] would run by itself (근무 시작·끝·매일), for "앞으로 2주 동안 N번". Others wait for a tap or a save. */
    fun runsOn(r: LogicRule, days: List<LocalDate>, facts: DayFactsBook): List<LocalDate> = days.filter { rule(r, it, facts).runs }

    /** Whether [r] runs by itself on a time (근무 시작·끝·매일), not on something done (날짜 누르기, 메모·가계부 저장, 앱 열기). */
    fun timed(r: LogicRule): Boolean = r.on == RuleTrigger.START || r.on == RuleTrigger.END || r.on == RuleTrigger.DAILY || r.on == RuleTrigger.EVERY

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
        RuleTrigger.VALUE -> "‘${r.of.orEmpty()}’ 바꿔 보기"
        RuleTrigger.EVERY -> "다음 때 만들어 보기"
        RuleTrigger.ALARM_OFF -> "알람 꺼 보기"
    }
}
