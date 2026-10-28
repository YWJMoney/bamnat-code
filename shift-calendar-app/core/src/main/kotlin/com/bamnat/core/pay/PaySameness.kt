package com.bamnat.core.pay

import java.time.LocalDate
import java.time.YearMonth

/**
 * A 1.32 [FIX132-03] (사용자 2026-10-28 "급여 설정을 바꾸면 어떤 달은 옛 설정이 적용돼 통일이 안 돼"): why a month is worked out
 * with something other than today's setup, in words, and the one step that makes every month the same. Three things kept a
 * month on an older value in 1.31: a value entered '이 달부터' (months before keep the old one), a month 마감 (its result
 * kept as it was), and a change started from this pay period to keep a payslip-matched month ([VerifiedChange.startingOn]).
 */
object PaySameness {
    /** One value this month reads that is not the latest one entered. */
    data class Older(val name: String, val here: Long, val latest: Long, val latestFrom: LocalDate) {
        val text: String get() = "$name ${won(here)} (${latestFrom.year}년 ${latestFrom.monthValue}월부터는 ${won(latest)})"
    }

    data class Report(val month: YearMonth, val older: List<Older>, val closed: Boolean) {
        val same: Boolean get() = older.isEmpty() && !closed
        val line: String
            get() = when {
                closed && older.isEmpty() -> "이 달은 마감해서 마감할 때 설정으로 보여요."
                closed -> "이 달은 마감했고, 예전 값으로 계산돼 있어요: " + older.joinToString(" · ") { it.text }
                else -> "이 달은 예전 값으로 계산돼요: " + older.joinToString(" · ") { it.text }
            }
    }

    /** [month] of [p]: the entered values older than the latest, and whether the month is [closed]. */
    fun of(p: PayProfile, month: YearMonth, closed: Boolean = false): Report {
        // As the calculator reads a month's values (PayCalculator: base.on(month)).
        val day = month.atDay(1)
        fun older(name: String, t: ValueTimeline): Older? {
            val latest = t.entries.maxByOrNull { it.from } ?: return null
            val here = t.on(day).basedOn ?: return null
            if (here == latest || here.value == latest.value) return null
            return Older(name, t.on(day).value, latest.value, latest.from)
        }
        val list = listOfNotNull(older(if (p.wageType == WageType.HOURLY) "시급" else if (p.wageType == WageType.DAILY) "일당" else "기본급", p.base)) +
            p.items.mapNotNull { older(it.name, it.amount) }
        return Report(month, list, closed)
    }

    /** Every value at its latest entered amount for every month (raises kept): '모든 달 같게'. */
    fun unify(p: PayProfile): PayProfile {
        fun one(t: ValueTimeline): ValueTimeline {
            val latest = t.entries.maxByOrNull { it.from } ?: return t
            return BulkEditPlanner.apply(t, latest.value, EditScope.AllMonths)
        }
        return p.copy(base = one(p.base), items = p.items.map { it.copy(amount = one(it.amount)) })
    }
}
