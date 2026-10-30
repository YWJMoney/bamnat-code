package com.bamnat.core.layout

/**
 * [NAV-05][NAV-06] 1.26 AD (결정 D82): the order of the app's bottom tabs, by their keys ("calendar", "money", ...).
 * Every tab is always there (none can be hidden); the first one is the screen the app opens on and where back
 * goes home. An empty saved order is [DEFAULT], so a phone that never changed it sees the tabs as before.
 */
object TabOrder {
    const val CALENDAR = "calendar"
    const val MONEY = "money"
    const val MEMOS = "memos"
    const val STATS = "stats"
    /** 1.35 [RCX-29] 광장 and its 나눔 판 (사용자 2026-10-30 "너무 숨어있어"). */
    const val PLAZA = "plaza"
    const val SETTINGS = "settings"

    /** The usual order ('처음 순서로'); 1.35 put 광장 before 설정. */
    val DEFAULT: List<String> = listOf(CALENDAR, MONEY, MEMOS, STATS, PLAZA, SETTINGS)

    /** A tab added in a later version and the tab it goes in front of in an order saved before it came. */
    private val ADDED_BEFORE = mapOf(PLAZA to SETTINGS)

    /**
     * The order to show for [saved]: keys the app does not know (or knows twice) are dropped and the tabs left out
     * go at the end in their usual order, so a backup from another version never loses a tab.
     */
    fun of(saved: List<String>, all: List<String> = DEFAULT): List<String> {
        val kept = saved.filter { it in all }.distinct().toMutableList()
        if (kept.isEmpty()) return all
        // A tab newer than the saved order goes where it would have been (광장 in front of 설정), the rest at the end.
        for ((tab, before) in ADDED_BEFORE) {
            if (tab in all && tab !in kept && before in kept) kept.add(kept.indexOf(before), tab)
        }
        return kept + all.filter { it !in kept }
    }

    /** What to save for [order]: empty when it is the usual one, so the default can still change in a later version. */
    fun toSave(order: List<String>, all: List<String> = DEFAULT): List<String> = of(order, all).takeIf { it != all }.orEmpty()

    /** [order] with [key] moved to [to] (clamped to the ends). */
    fun move(order: List<String>, key: String, to: Int): List<String> {
        val from = order.indexOf(key)
        if (from < 0) return order
        val target = to.coerceIn(0, order.lastIndex)
        if (target == from) return order
        return order.toMutableList().apply { removeAt(from); add(target, key) }
    }

    /** [order] with [key] one place nearer the front (-1) or the end (+1). */
    fun step(order: List<String>, key: String, by: Int): List<String> = move(order, key, order.indexOf(key) + by)

    /** The start screen: the first tab. */
    fun home(saved: List<String>): String = of(saved).first()
}
