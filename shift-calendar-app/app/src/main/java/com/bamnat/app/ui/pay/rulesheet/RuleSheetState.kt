package com.bamnat.app.ui.pay.rulesheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.pay.CommonTerms
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleCell
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleSheet
import java.time.YearMonth

/*
 * [RULE-ED-02] 1.30 묶음 AW: the 칸 편집기's screen pieces. One list of the company's rules ([RuleSheet]) whatever way it came in: the
 * rules tab of the photo's result ([com.bamnat.app.ui.pay.PhotoResultScreen]), 칸만으로 적기 (촬영이 안 되는 회사), 회사 규칙의 '우리 회사
 * 항목', and the 광장's 제안 ([RuleSheetEditor]). Nothing here is saved until the screen that holds it applies it.
 */

/** [RULE-ED-02] The sheet being made or changed on a screen. */
@Stable
class RuleSheetState(initial: RuleSheet) {
    var sheet by mutableStateOf(initial)
        private set

    fun update(change: (RuleSheet) -> RuleSheet) { sheet = change(sheet) }

    fun toggle(cell: RuleCell) = update { it.toggle(cell.id) }
    fun put(cell: RuleCell) = update { it.put(cell) }
    fun remove(cell: RuleCell) = update { it.remove(cell.id) }
    fun dropUnread(i: Int) = update { it.dropUnread(i) }
    fun terms(t: CommonTerms) = update { it.withCommon(t) }

    /** Starts over on [next] (a photo read again). */
    fun reset(next: RuleSheet) { sheet = next }
}

@Composable
fun rememberRuleSheetState(initial: () -> RuleSheet = { RuleSheet() }): RuleSheetState = remember { RuleSheetState(initial()) }

/**
 * [RULE-ED-06] What the sheet comes to this month, worked out off the main thread by the screen that holds it: the month's pay
 * ([total]), the 통상시급 it is counted on, and with a payslip what it said ([gross]) and what changes ([changes]).
 */
data class SheetPreview(
    val month: YearMonth,
    val total: Long,
    val hourly: Long? = null,
    val gross: Long? = null,
    val changes: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val close: Boolean = false,
    /** The setup as the sheet makes it, for the 통상시급 card. */
    val profile: PayProfile? = null,
) {
    val gap: Long? get() = gross?.let { it - total }
}

/** [RULE-ED-03] What one 칸 does to the month: the amount, and how many times it came up when that is known. */
data class CellEffect(val amount: Long, val times: Int?)

/**
 * [RULE-ED-02] What the 칸 are made and worked out on: the pay setup (its period, 근무), the calendar, the month on screen, and how one
 * 칸 is worked out ([effect], null shows no amount). [companyWay] says the pay came from a company's way (나만 받는 수당, 묶음 BC).
 */
class RuleSheetHost(
    val profile: PayProfile,
    val snapshot: ScheduleSnapshot,
    val month: YearMonth,
    val effect: (suspend (RuleChange) -> CellEffect?)? = null,
    val companyWay: Boolean? = false,
) {
    val shiftNames: Map<Long, String> get() = snapshot.shiftTypes.associate { it.id to it.name }

    companion object {
        /**
         * [RULE-ED-06] How one 칸 is worked out: an allowance or a rule by the builder's own count ([made], "+80,000원 · 4번") on the
         * setup with the sheet's other 칸; anything else (a premium's rate) as the month with it less without ([total]). Null with neither.
         */
        fun effectOf(
            made: (suspend (com.bamnat.app.ui.pay.MadeItem) -> com.bamnat.app.ui.pay.ItemEffect?)?,
            total: (suspend (List<RuleChange>) -> Long?)?,
            sheet: () -> RuleSheet,
        ): (suspend (RuleChange) -> CellEffect?)? {
            if (made == null && total == null) return null
            val byTotal = total?.let { effectBy(sheet, it) }
            return { change ->
                val item = when (change) {
                    is RuleChange.Item -> com.bamnat.app.ui.pay.MadeItem.Item(change.item)
                    is RuleChange.Custom -> com.bamnat.app.ui.pay.MadeItem.Rule(change.rule)
                    else -> null
                }
                if (item != null && made != null) made(item)?.let { CellEffect(it.amount, it.times) } else byTotal?.invoke(change)
            }
        }

        /**
         * [RULE-ED-06] A 칸's effect worked out as the month with it less the month without it, on [preview] (the whole sheet's pay):
         * for screens that work the sheet out but have no per-item count.
         */
        fun effectBy(sheet: () -> RuleSheet, total: suspend (List<RuleChange>) -> Long?): suspend (RuleChange) -> CellEffect? = { change ->
            val without = sheet().toChanges().filterNot { it == change }
            val before = total(without)
            val after = total(without + change)
            if (before != null && after != null) CellEffect(after - before, null) else null
        }
    }
}
