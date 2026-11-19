package com.bamnat.core.pay

import kotlin.math.abs

/**
 * 2.0.0 [RVN-24] 검토 I10ac-01 명세서 한 장으로 회사와 판을 저절로 맞추기 (공유급여 4-1 [PAYP-18] 위의 확장): how much of a payslip's
 * shape one version of the company's way has, item by item — the allowances by name, and each 가산 (연장·야간·휴일) by whether it is
 * paid on its own line and, when the payslip prints its hours, by what an hour of it comes to (the 가산 꼴: 통상시급 × 배율). The
 * amount gap ([SlipGlance]) says how close the total is; this says whether it is the same way of paying ("항목 9/10").
 *
 * Worked out on the phone with the version taken for the pick (never shown, 결정 D79·[PAYP-01]): what comes out is the count and
 * the names the payslip itself prints or the version's 수당 names the pick already says ([WayPick.Tried.unknown]); no rate,
 * multiplier or amount of the version. Nothing of the payslip leaves the phone (결정 D36).
 */
object SlipShape {
    /** One item looked at: [label] as the payslip prints it (or the 가산's name), [same] when the version has it alike. */
    data class Item(val label: String, val same: Boolean)

    data class Fit(val items: List<Item>) {
        val same: Int get() = items.count { it.same }
        val total: Int get() = items.size
        val misses: Int get() = total - same
        /** Every item looked at is alike (and there was at least one). */
        val whole: Boolean get() = total > 0 && misses == 0

        /** "항목 9/10", or null when nothing could be compared (a payslip typed as two numbers). */
        fun count(): String? = if (total == 0) null else "항목 $same/$total"

        /** The items that differ, the payslip's own names first, for "다른 곳: 야간·식대". */
        val differ: List<String> get() = items.filter { !it.same }.map { it.label }
    }

    /** An hour of a 가산 apart by more than this share is another 배율 (or another 통상시급). */
    const val HOUR_SLACK = 0.08

    /** The 가산 looked at, in the payslip's order. */
    private val PREMIUMS = listOf(SlipGlance.Part.OVERTIME, SlipGlance.Part.NIGHT, SlipGlance.Part.HOLIDAY)

    /**
     * [slip] against [month] (one version worked out on this calendar for the payslip's month). A part or an allowance the payslip
     * does not print counts against the version only when the payslip was read whole ([WayPick.Slip.whole]); two numbers typed by
     * hand say nothing of what else it pays.
     */
    fun of(slip: WayPick.Slip, month: MonthPay): Fit {
        val items = ArrayList<Item>()
        for (part in PREMIUMS) {
            val theirs = slip.parts[part]?.takeIf { it > 0 }
            val lines = month.lines.filter { it.kind in part.kinds && it.amount > 0 }
            val mine = lines.sumOf { it.amount }
            when {
                theirs != null && mine > 0 -> items += Item(part.label, sameHour(theirs, slip.minutes[part], mine, lines.sumOf { it.minutes }))
                theirs != null -> items += Item(part.label, false)
                mine > 0 && slip.whole -> items += Item(part.label, false)
            }
        }
        if (slip.whole || slip.allowances.isNotEmpty()) {
            // Allowances paid this month by the version, and those it leaves to each person (named, no amount yet).
            val theirsNames = slip.allowances.filterValues { it > 0 }.keys.toList()
            val mineNames = (month.lines.filter { it.kind == LineKind.ALLOWANCE && it.amount > 0 }.map { it.label } +
                month.missing.map { it.name }).distinctBy(::key)
            val used = HashSet<String>()
            for (n in theirsNames) {
                val hit = mineNames.firstOrNull { it !in used && alike(n, it) }
                if (hit != null) used += hit
                items += Item(n, hit != null)
            }
            if (slip.whole) mineNames.filter { it !in used && theirsNames.none { n -> alike(n, it) } }.forEach { items += Item(it, false) }
        }
        return Fit(items)
    }

    /**
     * An hour of the 가산 on the payslip and in the version come to the same (within [HOUR_SLACK]); without the payslip's hours
     * (or the version's) a line on both sides is all that can be said, and it is alike.
     */
    private fun sameHour(theirs: Long, theirMinutes: Int?, mine: Long, myMinutes: Int): Boolean {
        val tm = theirMinutes?.takeIf { it > 0 } ?: return true
        if (myMinutes <= 0) return true
        val a = theirs * 60.0 / tm
        val b = mine * 60.0 / myMinutes
        return abs(a - b) <= maxOf(a, b) * HOUR_SLACK
    }

    /** A name as compared: letters and digits only, small letters, without a closing '수당' (식대수당 = 식대). */
    internal fun key(name: String): String =
        name.substringBefore('(').filter { it.isLetterOrDigit() }.lowercase().removeSuffix("수당").ifEmpty { name.filter { it.isLetterOrDigit() } }

    /** Two names for one allowance: the same once compared, or one holding the other (교대 · 교대 근무 수당). */
    internal fun alike(a: String, b: String): Boolean {
        val x = key(a)
        val y = key(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        return minOf(x.length, y.length) >= 2 && (x.contains(y) || y.contains(x))
    }
}
