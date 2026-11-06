package com.bamnat.core.schedule

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.work.WorkStyle

/**
 * [DAILY-10] The long-press menu's shift types, those the user picked most in the last [DAYS] days first.
 * Ties keep the user's own order (근무 유형 화면), so a calendar with no changes looks as before.
 */
object QuickOrder {
    const val DAYS = 60

    fun <T, K> byUse(items: List<T>, key: (T) -> K, used: List<K>): List<T> {
        if (used.isEmpty()) return items
        val counts = used.groupingBy { it }.eachCount()
        return items.sortedByDescending { counts[key(it)] ?: 0 }
    }

    /**
     * [CAL-37] I 리뷰 I9: the extra-work kinds in the order a new user of [style] most likely needs them on a day
     * off, before the last [DAYS] days say otherwise: 교대 근무 covers others first (대근, 특근, 잔업), a day worker
     * comes in for 특근 first (특근, 잔업, 대근). Kinds the user made come after, in their own order.
     */
    fun extraRank(style: WorkStyle?, kindId: Long): Int {
        val order = when (style) {
            WorkStyle.DAY -> listOf(ExtraKind.SPECIAL_ID, ExtraKind.OVERTIME_ID, ExtraKind.COVER_ID)
            else -> listOf(ExtraKind.COVER_ID, ExtraKind.SPECIAL_ID, ExtraKind.OVERTIME_ID)
        }
        return order.indexOf(kindId).let { if (it < 0) order.size else it }
    }

    /**
     * [CAL-37] [items] (each of a kind, [kindOf]) with the kinds used most in [used] first, then by
     * [extraRank] for [style]; equal ones keep their order. A null [style] ranks by use only (a working day,
     * whose 잔업 next to the shift already comes first).
     */
    fun <T> extras(items: List<T>, kindOf: (T) -> Long, used: List<Long>, style: WorkStyle?, everUsed: Set<Long>? = null): List<T> {
        val counts = used.groupingBy { it }.eachCount()
        val sorted = items.sortedWith(compareByDescending<T> { counts[kindOf(it)] ?: 0 }.thenBy { if (style == null) 0 else extraRank(style, kindOf(it)) })
        val fresh = everUsed?.let { newest(items.map(kindOf), it) } ?: return sorted
        return sorted.filter { kindOf(it) == fresh } + sorted.filter { kindOf(it) != fresh }
    }

    /**
     * [PAYX-51] 1.38 PC 12차 ('교육'을 만들었는데 달력 칸 칩에는 '+4'를 눌러야 나왔어요): the kind the user made last, while it has
     * never been put on a day ([everUsed]), comes first, so what was just made is there to tap. Ids grow, so the newest is the
     * largest made one ([ExtraKind.FIRST_CUSTOM_ID] on). Once used it takes its place by use like the rest.
     */
    fun newest(kindIds: List<Long>, everUsed: Set<Long>): Long? =
        kindIds.filter { it >= ExtraKind.FIRST_CUSTOM_ID }.maxOrNull()?.takeIf { it !in everUsed }
}
