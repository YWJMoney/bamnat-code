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
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
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

    /** "6/8잔", "12건". */
    fun show(v: Double): String = Formula.numText(v) + (goal?.let { "/" + Formula.numText(it) } ?: "") + unit

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
            )
        }
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

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): ValueReset = entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/**
 * 1.39 [FX-03] The 값 칸 numbers as kept on the phone: name → (number, period). Pure, so the app's store and the tests share it.
 */
object ValueBook {
    data class Kept(val v: Double, val period: String)

    /** The most a 값 칸 may hold either way (a runaway rule cannot grow it without end). */
    const val LIMIT = 1_000_000_000.0

    /** [value]'s number on [today]: its start when nothing is kept or the kept one is of an earlier period. */
    fun now(kept: Kept?, value: LogicValue, today: LocalDate): Double =
        kept?.takeIf { it.period == value.reset.period(today) }?.v ?: value.start

    fun add(kept: Kept?, value: LogicValue, today: LocalDate, by: Double): Kept =
        Kept((now(kept, value, today) + by).coerceIn(-LIMIT, LIMIT), value.reset.period(today))

    fun set(value: LogicValue, today: LocalDate, to: Double): Kept = Kept(to.coerceIn(-LIMIT, LIMIT), value.reset.period(today))

    /** As the phone keeps them: `{"물 잔":[3,"2026-11-07"]}`. */
    fun encode(all: Map<String, Kept>): String =
        com.bamnat.core.recipe.RecipeCodec.canonical(all.mapValues { (_, k) -> listOf(k.v, k.period) })

    @Suppress("UNCHECKED_CAST")
    fun decode(s: String?): Map<String, Kept> {
        if (s.isNullOrBlank()) return emptyMap()
        val m = runCatching { com.bamnat.core.remote.Json.parse(s) as? Map<String, Any?> }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) ->
            val l = v as? List<Any?> ?: return@mapNotNull null
            val n = (l.getOrNull(0) as? Number)?.toDouble() ?: return@mapNotNull null
            k to Kept(n, (l.getOrNull(1) as? String).orEmpty())
        }.toMap()
    }
}
