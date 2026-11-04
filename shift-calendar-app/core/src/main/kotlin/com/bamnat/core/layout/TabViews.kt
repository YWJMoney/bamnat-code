package com.bamnat.core.layout

import com.bamnat.core.memo.MemoView
import com.bamnat.core.money.LedgerBoard

/**
 * [TAB-02][TAB-04][TAB-06] 1.36 탭 보기: the board the calendar is laid on, how the 메모장 shows its memos and the 가계부's board, kept
 * together in one setting (and in the backup) the way a recipe's `tabView` piece carries them ([TAB-07]). Each part left out is the
 * app's own: 월, 목록, 목록.
 */
data class TabViews(
    val cal: CalendarBoard = CalendarBoard.MONTH,
    val memo: MemoView = MemoView(),
    val money: LedgerBoard = LedgerBoard.LIST,
) {
    val isDefault: Boolean get() = this == TabViews()

    fun encode(): String = listOfNotNull(
        cal.takeIf { it != CalendarBoard.MONTH }?.let { "cal=${it.key}" },
        memo.takeIf { !it.isDefault }?.let { "memo=" + it.encode() },
        money.takeIf { it != LedgerBoard.LIST }?.let { "money=${it.key}" },
    ).joinToString(";")

    companion object {
        fun decode(raw: String?): TabViews {
            val parts = raw.orEmpty().split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
            return TabViews(
                cal = CalendarBoard.of(parts["cal"]),
                memo = MemoView.decode(parts["memo"]),
                money = LedgerBoard.of(parts["money"]),
            )
        }
    }
}
