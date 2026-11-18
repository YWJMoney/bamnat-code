package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * [PAYP-18] 1.40 대표 기능 (공유급여 4-1, 사용자 결정 2026-11-07 19:21): one payslip picks the company's pay way that fits it best.
 * The company has a few versions of its way (부서마다, 고친 판마다); each is worked out on the phone for the payslip's month with
 * the payslip's own 기본급 and the allowances it names, and the one whose 지급 총액 comes closest is put in. No rate, multiplier or
 * formula of any version is shown: only how far each one is from the payslip and where it differs most ([SlipGlance]).
 *
 * Kept on the phone (결정 D36): the payslip's figures and which version fitted are never sent.
 */
object WayPick {
    /** What a payslip says that the pick uses: [gross] always, the rest when the payslip (or the user) gave it. */
    data class Slip(
        val month: YearMonth,
        val gross: Long,
        val base: Long?,
        val parts: Map<SlipGlance.Part, Long> = emptyMap(),
        /** Allowances and bonuses by the name printed on the payslip. */
        val allowances: Map<String, Long> = emptyMap(),
    ) {
        companion object {
            /** The pick's view of a payslip read from a photo or text ([PayslipRead.payslip]). */
            fun of(month: YearMonth, p: Payslip): Slip = Slip(
                month = month, gross = p.gross, base = p.base,
                parts = buildMap {
                    p.overtimePay?.let { put(SlipGlance.Part.OVERTIME, it) }
                    p.nightPay?.let { put(SlipGlance.Part.NIGHT, it) }
                    p.holidayPay?.let { put(SlipGlance.Part.HOLIDAY, it) }
                },
                allowances = p.allowances,
            )
        }
    }

    /**
     * One version worked out: [id] the server's setup id, [glance] how it stands against the payslip, [unknown] the names of
     * allowances it pays that differ by person and the payslip did not name (left out of its total, said under it).
     */
    data class Tried(val id: String, val month: MonthPay, val glance: SlipGlance.Result, val unknown: List<String>) {
        val gap: Long get() = abs(glance.diff)
    }

    /** At most this many versions are tried: each one is a take (하루 12번, 서버 TAKES_PER_DAY). */
    const val MAX_TRIED = 3

    /**
     * [p] (a version put on the user's pay, nothing saved) with the payslip's amounts where the version leaves them to each person:
     * the 기본급 for a monthly pay, and each allowance of the same name (spaces and case aside) that has no amount yet, split by
     * how often it comes in the month. [first] is the month worked out once before, for how often a per-shift allowance came up.
     */
    fun filled(p: PayProfile, slip: Slip, first: MonthPay? = null): PayProfile {
        var out = p
        val month = slip.month
        if (slip.base != null && slip.base > 0 && p.wageType == WageType.MONTHLY) out = BaseAsk.apply(out, slip.base, month)
        val from = out.period.start(month)
        val names = slip.allowances.mapKeys { norm(it.key) }
        for (item in out.items) {
            if (item.amount.entries.isNotEmpty() || item.isPercent || item.isCompanyFixed) continue
            if (item.cadence == Cadence.BONUS && item.percentOfBase != null) continue
            val amount = names[norm(item.name)] ?: continue
            val times = when (item.cadence) {
                Cadence.MONTHLY -> 1
                Cadence.BONUS -> item.timesIn(month)
                else -> first?.itemTimes?.get(item.id) ?: 0
            }
            if (times <= 0) continue
            val each = (amount.toDouble() / times).roundToLong()
            out = out.copy(
                items = out.items.map { if (it.id == item.id) it.copy(amount = ValueTimeline(listOf(DatedValue(from, each)))) else it },
                estimatedFields = out.estimatedFields - Unknowns.item(item.id),
            )
        }
        return out
    }

    /** [month] against the payslip. */
    fun tried(id: String, month: MonthPay, slip: Slip): Tried =
        Tried(id, month, SlipGlance.of(month, slip.gross, slip.parts), month.missing.map { it.name }.distinct())

    /** The versions tried, the closest first: a match before a miss, then the smaller gap, then the order the server gave. */
    fun rank(tried: List<Tried>): List<Tried> =
        tried.withIndex().sortedWith(compareBy<IndexedValue<Tried>>({ if (it.value.glance.matched) 0 else 1 }, { it.value.gap }, { it.index }))
            .map { it.value }

    /**
     * What the best one says: "이 방식이 10월 명세서와 3,200원 차이로 가장 맞아요". With one version only it says how close that one
     * is; with none close (all over [FAR_SHARE] of the payslip) it says so rather than calling the nearest one a fit.
     */
    fun line(best: Tried, count: Int, slip: Slip, won: (Long) -> String): String {
        val m = "${slip.month.monthValue}월 명세서"
        val gap = if (best.gap == 0L) "차이 없이" else "${won(best.gap)} 차이로"
        return when {
            far(best, slip) -> "${m}와 ${won(best.gap)} 차이가 나서 맞는 방식이 없어요. 기본급과 내 수당이 명세서와 같은지 먼저 봐 주세요"
            best.glance.matched && count > 1 -> "이 방식이 ${m}와 $gap 가장 맞아요"
            best.glance.matched -> "이 방식이 ${m}와 $gap 맞아요"
            count > 1 -> "${m}와 ${won(best.gap)} 차이가 나요. 고른 방식이 가장 가까워요"
            else -> "${m}와 ${won(best.gap)} 차이가 나요"
        }
    }

    /** Further than this share of the payslip's total is no fit at all: another company's way, or a month not worked in full. */
    const val FAR_SHARE = 0.15

    fun far(t: Tried, slip: Slip): Boolean = t.gap > slip.gross * FAR_SHARE

    private fun norm(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()
}
