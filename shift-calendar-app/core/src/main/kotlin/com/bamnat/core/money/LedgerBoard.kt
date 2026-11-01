package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth

/**
 * [TAB-06] 1.36 가계부 판 (사용자 2026-11-01 "가계부도 같은 수준"): the same 내역 — after the period and filters the user set — as a
 * 목록 (by day, as before), a 달력 of each day's money in and out, a 분류 보드 of a column a category with its total, or a 표 sorted by a
 * column. The board never changes a line.
 */
enum class LedgerBoard(val key: String, val label: String) {
    LIST("list", "목록"), CAL("cal", "달력"), BOARD("board", "분류 보드"), TABLE("table", "표");

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): LedgerBoard = entries.firstOrNull { it.key == key } ?: LIST

        /** A category column: its name, lines, and money out and in (a split line counts its share only). */
        data class Column(val category: String, val entries: List<MoneyEntry>, val expense: Long, val income: Long) {
            val total: Long get() = expense + income
        }

        /** The 분류 보드 of [shown]: the biggest categories first; transfers are left out (they move money, not spend it). */
        fun columns(shown: List<MoneyEntry>): List<Column> {
            val out = LinkedHashMap<String, Triple<MutableList<MoneyEntry>, LongArray, Unit>>()
            for (e in shown) {
                if (e.kind == MoneyKind.TRANSFER) continue
                for ((cat, amount) in e.shares()) {
                    val slot = out.getOrPut(cat.ifBlank { "분류 없음" }) { Triple(ArrayList(), LongArray(2), Unit) }
                    if (slot.first.lastOrNull() !== e) slot.first += e
                    if (e.kind == MoneyKind.EXPENSE) slot.second[0] += amount else slot.second[1] += amount
                }
            }
            return out.map { (k, v) -> Column(k, v.first, v.second[0], v.second[1]) }.sortedByDescending { it.total }
        }

        /** A 달력 day: money out and in, and how many lines. */
        data class Day(val date: LocalDate, val expense: Long, val income: Long, val count: Int)

        fun days(shown: List<MoneyEntry>, month: YearMonth): Map<LocalDate, Day> = shown
            .filter { YearMonth.from(it.date) == month && it.kind != MoneyKind.TRANSFER }
            .groupBy { it.date }
            .mapValues { (d, es) ->
                Day(d, es.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amount }, es.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amount }, es.size)
            }

        /** 표's columns; [sortTable] orders by one of them. */
        val TABLE_COLUMNS = listOf("date" to "날짜", "category" to "분류", "what" to "내용", "amount" to "금액")

        fun sortTable(shown: List<MoneyEntry>, column: String, down: Boolean): List<MoneyEntry> {
            val sorted = when (column) {
                "category" -> shown.sortedBy { it.category }
                "what" -> shown.sortedBy { what(it) }
                "amount" -> shown.sortedBy { signed(it) }
                else -> shown.sortedWith(compareBy<MoneyEntry> { it.date }.thenBy { it.time })
            }
            return if (down) sorted.reversed() else sorted
        }

        /** The words a line goes by in the 표: where, else the user's own memo, else the payment text. */
        fun what(e: MoneyEntry): String = e.merchant.ifBlank { e.memo }.ifBlank { e.note }

        /** Money in plus, out minus; a transfer is 0 so it sorts in the middle. */
        fun signed(e: MoneyEntry): Long = when (e.kind) { MoneyKind.INCOME -> e.amount; MoneyKind.EXPENSE -> -e.amount; MoneyKind.TRANSFER -> 0 }
    }
}
