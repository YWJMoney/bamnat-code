package com.bamnat.core.formula

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * 1.39 [FX-03] 값 칸 (조사 18장 B-1 "작품이 자기 값을 세고 저장"): a number a work keeps and counts by itself — 물 잔 수, 이달 배달 건수,
 * 이번 주 야식 횟수. A button or a rule adds to it or sets it ([LogicVerb.VAL_ADD]·[LogicVerb.VAL_SET]), a formula reads it
 * (`값("물 잔")`) and a 단추 위젯 shows it beside its goal. It starts again by itself on its [reset] day.
 *
 * Only a number and the period it belongs to are kept, on the phone (and in its backup); taking the work off keeps the numbers until
 * the user clears them, so putting it back goes on from where it was.
 */
data class LogicValue(
    val name: String,
    val reset: ValueReset = ValueReset.NEVER,
    /** What it is at the start of each period. */
    val start: Double = 0.0,
    /** A goal to show progress toward (물 8잔), or null. */
    val goal: Double? = null,
    /** A word after the number (잔, 건, 원), or "". */
    val unit: String = "",
    /** 1.39 [FX-13] 숫자, 시각(지금 시각 적기), 글(글 적기). */
    val kind: ValueKind = ValueKind.NUM,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        if (kind != ValueKind.NUM) put("kind", kind.key)
        if (reset != ValueReset.NEVER) put("reset", reset.key)
        if (start != 0.0) put("start", start)
        goal?.let { put("goal", it) }
        if (unit.isNotEmpty()) put("unit", unit)
    }

    /** "물 잔: 매일 0으로 · 목표 8잔". */
    fun say(): String = buildString {
        append(name).append(": ").append(reset.label)
        if (reset != ValueReset.NEVER) append(" ").append(Formula.numText(start)).append("으로")
        goal?.let { append(" · 목표 ").append(Formula.numText(it)).append(unit) }
    }

    /** "6/8잔", "12건"; a 시각 "18:30" (another day "11/6 18:30"). */
    fun show(v: Double, today: LocalDate? = null): String = when (kind) {
        ValueKind.TIME -> if (v <= 0) "아직 없음" else ValueBook.clock(v, today)
        else -> Formula.numText(v) + (goal?.let { "/" + Formula.numText(it) } ?: "") + unit
    }

    /** As [show], the 글 of a 글 값 칸. */
    fun show(k: ValueBook.Kept?, today: LocalDate): String =
        if (kind == ValueKind.TEXT) k?.t?.takeIf { k.period == reset.period(today) }.orEmpty().ifEmpty { "비어 있음" }
        else show(ValueBook.now(k, this, today), today)

    companion object {
        const val MAX = 8
        const val MAX_UNIT = 4

        /** A 값 칸 name: 1~12 letters with a letter in it, at most three words, no separator (the same as a memo 칸 name, shorter). */
        fun isName(s: String): Boolean = s.length in 1..12 && com.bamnat.core.memo.MemoProps.isLabel(s)

        fun from(m: Map<String, Any?>): LogicValue? {
            val name = (m["name"] as? String)?.trim()?.takeIf(::isName) ?: return null
            return LogicValue(
                name = name,
                reset = ValueReset.of(m["reset"] as? String),
                start = (m["start"] as? Number)?.toDouble() ?: 0.0,
                goal = (m["goal"] as? Number)?.toDouble()?.takeIf { it > 0 },
                unit = (m["unit"] as? String).orEmpty().trim().take(MAX_UNIT),
                kind = ValueKind.of(m["kind"] as? String),
            )
        }
    }
}

/** 1.39 [FX-13] What a 값 칸 holds. */
enum class ValueKind(val key: String, val label: String) {
    NUM("num", "숫자"), TIME("time", "시각"), TEXT("text", "글");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ValueKind = entries.firstOrNull { it.key == key } ?: NUM
    }
}

/** When a 값 칸 starts again. */
enum class ValueReset(val key: String, val label: String) {
    DAY("day", "매일"),
    WEEK("week", "매주 월요일"),
    MONTH("month", "매달 1일"),
    NEVER("never", "계속 쌓기"),
    ;

    /** The period [day] is in: two days of one period keep counting, a new period starts from [LogicValue.start]. */
    fun period(day: LocalDate): String = when (this) {
        DAY -> day.toString()
        WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { "${it.get(IsoFields.WEEK_BASED_YEAR)}-W${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" }
        MONTH -> "${day.year}-${day.monthValue}"
        NEVER -> ""
    }

    /** The day [n] periods before [day] (1: yesterday, last week, last month). */
    fun back(day: LocalDate, n: Int): LocalDate = when (this) {
        DAY -> day.minusDays(n.toLong())
        WEEK -> day.minusWeeks(n.toLong())
        MONTH -> day.minusMonths(n.toLong())
        NEVER -> day
    }

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ValueReset = entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/**
 * 1.39 [FX-03] The 값 칸 numbers as kept on the phone: name → (number, period). Pure, so the app's store and the tests share it.
 */
object ValueBook {
    /**
     * [v] in [period]; 1.39 [FX-13] [t] a 글 값 칸's words, [past] each earlier period's last number (newest last, at most [PAST]).
     */
    data class Kept(val v: Double, val period: String, val t: String? = null, val past: List<Pair<String, Double>> = emptyList())

    /** 1.39 [FX-13] How many earlier periods are kept (two months of days). */
    const val PAST = 62

    /** "18:30", or "11/6 18:30" when not [today]: [v] is minutes since 1970 in the phone's zone. */
    fun clock(v: Double, today: LocalDate?): String {
        val t = java.time.LocalDateTime.ofEpochSecond(v.toLong() * 60, 0, java.time.ZoneOffset.UTC)
        val hm = "%02d:%02d".format(t.hour, t.minute)
        return if (today == null || t.toLocalDate() == today) hm else "${t.monthValue}/${t.dayOfMonth} $hm"
    }

    /** Minutes since 1970 of a local time ([VAL_STAMP][LogicVerb.VAL_STAMP]'s number). */
    fun minutes(at: java.time.LocalDateTime): Double = (at.toEpochSecond(java.time.ZoneOffset.UTC) / 60).toDouble()

    /** [kept] moved on to [period]: the number it had goes to [Kept.past]. */
    private fun carry(kept: Kept?, period: String): List<Pair<String, Double>> {
        if (kept == null) return emptyList()
        if (kept.period == period || kept.period.isEmpty()) return kept.past
        return (kept.past.filter { it.first != kept.period } + (kept.period to kept.v)).takeLast(PAST)
    }

    /** [value]'s number in the period of [day] ([today] being now), or null when nothing was kept for it. */
    fun on(kept: Kept?, value: LogicValue, day: LocalDate, today: LocalDate): Double? {
        val p = value.reset.period(day)
        if (p == value.reset.period(today)) return now(kept, value, today)
        if (kept == null) return null
        if (kept.period == p) return kept.v
        return kept.past.lastOrNull { it.first == p }?.second
    }

    /** [n] periods back (0: now): null when none was kept. */
    fun back(kept: Kept?, value: LogicValue, today: LocalDate, n: Int): Double? =
        on(kept, value, value.reset.back(today, n), today)

    /** How many periods in a row reached the goal, counting now when it has (else from the one before). */
    fun streak(kept: Kept?, value: LogicValue, today: LocalDate): Int {
        val goal = value.goal ?: return 0
        if (value.reset == ValueReset.NEVER) return if (now(kept, value, today) >= goal) 1 else 0
        var n = if (now(kept, value, today) >= goal) 1 else 0
        for (k in 1..PAST) {
            val v = back(kept, value, today, k) ?: break
            if (v < goal) break
            n++
        }
        return n
    }

    /** The sum of the last [n] periods (now included); a period with nothing kept counts as its start. */
    fun total(kept: Kept?, value: LogicValue, today: LocalDate, n: Int): Double =
        (0 until n.coerceIn(1, PAST + 1)).sumOf { back(kept, value, today, it) ?: value.start }

    /**
     * 1.39 [FX-26] 잘못 누른 하루 바로잡기: [value]'s number in the period of [day] set to [to] (now's or a kept past one; a past period
     * with nothing kept gets one). [today] is now.
     */
    fun fix(kept: Kept?, value: LogicValue, day: LocalDate, today: LocalDate, to: Double): Kept {
        val p = value.reset.period(day)
        val v = to.coerceIn(-LIMIT, LIMIT)
        if (p == value.reset.period(today)) return set(value, today, v, kept)
        val k = kept ?: Kept(value.start, value.reset.period(today))
        if (k.period == p) return k.copy(v = v)
        val past = if (k.past.any { it.first == p }) k.past.map { if (it.first == p) p to v else it }
            else (k.past + (p to v)).sortedBy { e -> (0..PAST).firstOrNull { value.reset.period(value.reset.back(today, it)) == e.first } ?: PAST + 1 }.reversed()
        return k.copy(past = past.takeLast(PAST))
    }

    /** The most a 값 칸 may hold either way (a runaway rule cannot grow it without end). */
    const val LIMIT = 1_000_000_000.0

    /** [value]'s number on [today]: its start when nothing is kept or the kept one is of an earlier period. */
    fun now(kept: Kept?, value: LogicValue, today: LocalDate): Double =
        kept?.takeIf { it.period == value.reset.period(today) }?.v ?: value.start

    fun add(kept: Kept?, value: LogicValue, today: LocalDate, by: Double): Kept = value.reset.period(today).let { p ->
        Kept((now(kept, value, today) + by).coerceIn(-LIMIT, LIMIT), p, kept?.t?.takeIf { kept.period == p }, carry(kept, p))
    }

    fun set(value: LogicValue, today: LocalDate, to: Double, kept: Kept? = null, t: String? = null): Kept = value.reset.period(today).let { p ->
        Kept(to.coerceIn(-LIMIT, LIMIT), p, t ?: kept?.t?.takeIf { kept.period == p }, carry(kept, p))
    }

    /** As the phone keeps them: `{"물 잔":[3,"2026-11-07"]}`, with the words and the past after (`[3,"…","글",[["2026-11-06",8]]]`). */
    fun encode(all: Map<String, Kept>): String =
        com.bamnat.core.recipe.RecipeCodec.canonical(all.mapValues { (_, k) ->
            if (k.t == null && k.past.isEmpty()) listOf(k.v, k.period) else listOf(k.v, k.period, k.t.orEmpty(), k.past.map { listOf(it.first, it.second) })
        })

    @Suppress("UNCHECKED_CAST")
    fun decode(s: String?): Map<String, Kept> {
        if (s.isNullOrBlank()) return emptyMap()
        val m = runCatching { com.bamnat.core.remote.Json.parse(s) as? Map<String, Any?> }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) ->
            val l = v as? List<Any?> ?: return@mapNotNull null
            val n = (l.getOrNull(0) as? Number)?.toDouble() ?: return@mapNotNull null
            val past = (l.getOrNull(3) as? List<Any?>).orEmpty().mapNotNull { e ->
                val x = e as? List<Any?> ?: return@mapNotNull null
                val p = x.getOrNull(0) as? String ?: return@mapNotNull null
                val w = (x.getOrNull(1) as? Number)?.toDouble() ?: return@mapNotNull null
                p to w
            }.takeLast(PAST)
            k to Kept(n, (l.getOrNull(1) as? String).orEmpty(), (l.getOrNull(2) as? String)?.takeIf { it.isNotEmpty() }, past)
        }.toMap()
    }
}

/**
 * 1.39 [FX-13] One 값 칸 as a formula reads it on a day: its number (or words) then, [back] periods before, how many in a row reached
 * its goal, the sum of the last few, and how long since a 시각 was written. [now] is the phone's time.
 */
class ValueSeries(val value: LogicValue, val kept: ValueBook.Kept?, val today: LocalDate, val now: java.time.LocalDateTime) {
    /** What `값("이름")` gives on [day]: the number (a 글 값 칸's words), or null when nothing was kept then. */
    fun on(day: LocalDate): Value? {
        if (value.kind == ValueKind.TEXT) {
            val p = value.reset.period(day)
            return kept?.takeIf { it.period == p }?.t?.let { Value.Text(it) } ?: if (p == value.reset.period(today)) Value.Text("") else null
        }
        return ValueBook.on(kept, value, day, today)?.let { Value.Num(it) }
    }
    fun back(n: Int): Double? = ValueBook.back(kept, value, today, n)
    fun streak(): Int = ValueBook.streak(kept, value, today)
    fun total(n: Int): Double = ValueBook.total(kept, value, today, n)
    /** Hours (one decimal) since the 시각 kept, or null when none. */
    fun hoursSince(): Double? {
        val v = ValueBook.now(kept, value, today).takeIf { value.kind == ValueKind.TIME && it > 0 } ?: return null
        return Math.round((ValueBook.minutes(now) - v) / 6.0) / 10.0
    }
}
