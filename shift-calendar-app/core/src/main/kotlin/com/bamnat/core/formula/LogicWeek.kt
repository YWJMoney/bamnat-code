package com.bamnat.core.formula

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 1.39 [FX-18] 일주일 미리 돌려 보기 (제작자 시험대, 무료): a work's rules run over 7 days of the phone's own days ([DayFactsBook]) without
 * doing anything. Each line says when, which rule, and what would happen; 값 칸 change as they would (값이 바뀌면 규칙도 이어 돌아요), so
 * a goal reached on 수요일 shows on 수요일. Rules that wait for a tap or a save are listed apart (누르거나 적어야 돌아요).
 */
object LogicWeek {
    data class Line(val date: LocalDate, val at: LocalTime?, val who: String, val what: String, val ran: Boolean = true)

    data class Week(val lines: List<Line>, val waiting: List<String>, val values: Map<String, String>)

    /** At most this many lines, so a 30분마다 rule cannot fill the screen. */
    const val MAX_LINES = 200

    fun run(l: Logic, facts: DayFactsBook, from: LocalDate, days: Int = 7, start: Map<String, ValueBook.Kept> = emptyMap()): Week {
        val book = start.toMutableMap()
        val out = mutableListOf<Line>()
        val byName = l.values.associateBy { it.name }

        fun scope(date: LocalDate, at: LocalTime?): FormulaScope {
            val base = facts.scope(date)
            val now = date.atTime(at ?: LocalTime.NOON)
            return object : FormulaScope {
                override fun get(name: String) = base.get(name)
                override fun input(label: String) = base.input(label)
                override fun value(name: String) = byName[name]?.let { ValueSeries(it, book[name], date, now).on(date) }
                override fun series(name: String) = byName[name]?.let { ValueSeries(it, book[name], date, now) }
            }
        }

        fun change(a: LogicAct, date: LocalDate, at: LocalTime?, s: FormulaScope): String? {
            val v = byName[a.arg] ?: return null
            val before = ValueBook.now(book[v.name], v, date)
            book[v.name] = when (a.verb) {
                LogicVerb.VAL_ADD -> ValueBook.add(book[v.name], v, date, a.by ?: 1.0)
                LogicVerb.VAL_SET -> ValueBook.set(v, date, a.by ?: 0.0, book[v.name])
                LogicVerb.VAL_STAMP -> ValueBook.set(v, date, ValueBook.minutes(date.atTime(at ?: LocalTime.NOON)), book[v.name])
                LogicVerb.VAL_TEXT -> ValueBook.set(v, date, before, book[v.name], runCatching { FormulaText.render(a.text, s) }.getOrDefault(a.text).take(LogicAct.MAX_TEXT))
                else -> return null
            }
            return v.name
        }

        // [date] is the day the rule belongs to (a night's 끝 belongs to the night's own day, as the runner keeps it: 그날 메모, 그날 조건);
        // [shown] is when it really happens (the next morning), for the line.
        fun fire(r: LogicRule, date: LocalDate, at: LocalTime?, depth: Int, shown: LocalDateTime? = null) {
            if (out.size >= MAX_LINES) return
            val lineDate = shown?.toLocalDate() ?: date
            val lineAt = shown?.toLocalTime() ?: at
            val s = scope(date, at)
            val holds = r.cond.isEmpty() || Formula.eval(r.cond, s)?.truthy == true
            val steps = r.steps(holds)
            if (steps.isEmpty()) {
                out += Line(lineDate, lineAt, who(r, date, lineDate), "조건(${EasyWhen.read(r.cond)?.say() ?: "수식"})이 맞지 않아 쉬어요", ran = false)
                return
            }
            // Each act is said after the ones before it ran, as the phone runs them: '야간' 0 → 1, then the memo says 1번째.
            val words = mutableListOf<String>()
            val next = mutableListOf<LogicRule>()
            for (a in steps) {
                words += LogicTryout.result(a, scope(date, at))
                val before = byName[a.arg]?.let { ValueBook.now(book[it.name], it, date) }
                val name = change(a, date, at?.plusMinutes((a.after ?: 0).toLong()), scope(date, at)) ?: continue
                if (depth >= 3) continue
                val v = byName.getValue(name)
                val after = ValueBook.now(book[name], v, date)
                l.rules.filter { it.on == RuleTrigger.VALUE && it.of == name }.forEach { vr ->
                    val goal = v.goal
                    if (!vr.goal || (goal != null && before != null && before < goal && after >= goal)) next += vr
                }
            }
            out += Line(lineDate, lineAt, who(r, date, lineDate), (if (!holds) "‘아니면’으로 " else "") + words.joinToString(" · "))
            next.forEach { fire(it, date, at, depth + 1, shown) }
        }

        // From the day before, so last night's 끝 this morning is in; only what happens inside the 7 days is shown.
        val first = from.atStartOfDay()
        val last = from.plusDays(days.toLong()).atStartOfDay()
        val events = mutableListOf<Triple<LocalDateTime, LocalDate, LogicRule>>()
        for (i in -1 until days) {
            val date = from.plusDays(i.toLong())
            val v = facts.values(date)
            for (r in l.rules) when (r.on) {
                RuleTrigger.START, RuleTrigger.END, RuleTrigger.DAILY -> LogicTryout.rule(r, date, facts).let { t ->
                    if (t.at != null && (t.runs || r.cond.isNotEmpty())) events += Triple(date.atTime(t.at).plusDays(if (overnight(r, v)) 1 else 0), date, r)
                }
                RuleTrigger.EVERY -> everyTimes(r, date, v).forEach { events += Triple(it, date, r) }
                else -> Unit
            }
        }
        events.filter { !it.first.isBefore(first) && it.first.isBefore(last) }.sortedBy { it.first }
            .forEach { (at, date, r) -> fire(r, date, at.toLocalTime(), 0, at) }
        val waiting = l.rules.filter { !LogicTryout.timed(it) && it.on != RuleTrigger.VALUE }.map { "${it.name}: ${it.sayWhen()} 돌아요" }
        val end = from.plusDays((days - 1).toLong())
        val values = l.values.associate { v -> v.name to v.show(book[v.name], end) }
        return Week(out.take(MAX_LINES), waiting, values)
    }

    /** The rule's name, and which night it belongs to when it shows the morning after (4차 PC: 끝나는 날 아래에). */
    private fun who(r: LogicRule, date: LocalDate, shown: LocalDate): String = when {
        shown == date -> r.name
        r.on == RuleTrigger.END -> "${r.name}(전날 근무 끝)"
        r.on == RuleTrigger.EVERY -> "${r.name}(전날 근무 중)"
        else -> r.name
    }

    /** A 근무 끝 rule on a day whose shift ends after midnight (end not after start): it comes the next morning, as the runner keeps it. */
    private fun overnight(r: LogicRule, v: Map<String, Any?>): Boolean {
        if (r.on != RuleTrigger.END) return false
        fun time(s: Any?) = (s as? String)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val start = time(v[FormulaName.START.ko]) ?: return false
        val end = time(v[FormulaName.END.ko]) ?: return false
        return !end.isAfter(start)
    }

    /**
     * When a 몇 분마다 rule runs for [day] ([v] its values, [DayFactsBook.values]): 사이 from 시작 (00:00) every [LogicRule.min] minutes
     * before 까지 (24:00); 근무 중 one step after the work starts up to its end (a night shift runs past midnight). [LogicRule.EVERY_DAY_MAX]
     * at most. The runner and [run] both read this.
     */
    fun everyTimes(r: LogicRule, day: LocalDate, v: Map<String, Any?>): List<LocalDateTime> {
        val step = r.min.coerceIn(LogicRule.EVERY_MIN, LogicRule.EVERY_MAX).toLong()
        fun time(s: Any?) = (s as? String)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val (from, to) = if (r.inWork) {
            if (v[FormulaName.OFF.ko] == true) return emptyList()
            val start = time(v[FormulaName.START.ko]) ?: return emptyList()
            val end = time(v[FormulaName.END.ko]) ?: return emptyList()
            day.atTime(start) to (if (!end.isAfter(start)) day.plusDays(1).atTime(end) else day.atTime(end))
        } else {
            val start = time(r.at) ?: LocalTime.MIDNIGHT
            val end = time(r.until)
            day.atTime(start) to (if (end == null) day.plusDays(1).atStartOfDay() else if (!end.isAfter(start)) day.plusDays(1).atTime(end) else day.atTime(end))
        }
        // A shift's first round is one step in (2시간마다 from 09:00 is 11:00, not the start itself).
        var t = if (r.inWork) from.plusMinutes(step) else from
        val out = mutableListOf<LocalDateTime>()
        while (!t.isAfter(to) && out.size < LogicRule.EVERY_DAY_MAX) {
            if (r.inWork || t.isBefore(to)) out += t
            t = t.plusMinutes(step)
        }
        return out
    }
}
