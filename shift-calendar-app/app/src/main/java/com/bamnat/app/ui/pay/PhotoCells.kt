package com.bamnat.app.ui.pay

import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import java.time.YearMonth

/*
 * [PHOTO-06] 1.29 AQ made the rules read from a photo cells to tick, change or take out. [PHOTO-32] 1.30 AW: the cells are the
 * 칸 편집기's now (ui/pay/rulesheet: one list for 사진·글·직접), changed where they are, and a cell made in the builder keeps what it
 * is ([RuleChange.Item]: 회사 공통·사람마다·%); what is left here is turning a rule into the builder's draft and back.
 */

/** [PHOTO-06] Turning a rule read from a photo into the builder's cell and back. */
internal object PhotoCellsLogic {
    /** A setup to work a cell out on when the screen has none: 통상시급 is not known, so percent cells show no amount. */
    val blank = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(emptyList()))

    /** Whether the builder ([ItemDraft]) can change [change], conditions and all. */
    fun editable(change: RuleChange): Boolean = when (change) {
        is RuleChange.Custom -> ItemKind.of(change.rule) != null
        is RuleChange.Item -> ItemDraft.ofItem(change.item) != null
        is RuleChange.Allowance -> change.cadence in setOf(Cadence.MONTHLY, Cadence.PER_WORK_DAY, Cadence.PER_SHIFT_TYPE)
        else -> false
    }

    /** The rule or pay item the builder edits [change] as; both null when it cannot. */
    fun toEdit(change: RuleChange, month: YearMonth): Pair<CustomRule?, PayItem?>? = when (change) {
        is RuleChange.Custom -> change.rule to null
        is RuleChange.Item -> null to change.item
        is RuleChange.Allowance -> when (change.cadence) {
            Cadence.MONTHLY, Cadence.PER_WORK_DAY -> null to PayItem("cell-" + change.name.hashCode().toUInt().toString(16), change.name,
                ValueTimeline(listOf(DatedValue(month.atDay(1), change.amount))), change.cadence, ordinary = change.ordinary ?: false)
            Cadence.PER_SHIFT_TYPE -> CustomRule("cell-" + change.name.hashCode().toUInt().toString(16), change.name.take(CustomRule.MAX_NAME),
                RuleWhen(shiftTypeIds = change.shiftTypeIds), RulePay.PerTime(change.amount)) to null
            else -> null
        }
        else -> null
    }

    /** The builder's draft for [change], or null when it is not one the builder makes. */
    fun draft(change: RuleChange, month: YearMonth): ItemDraft? {
        val (rule, item) = toEdit(change, month) ?: return null
        return rule?.let { ItemDraft.of(it) } ?: item?.let { ItemDraft.ofItem(it) }
    }

    /**
     * What the builder made, as the change the photo's result applies. [RULE-ED-01] 1.30 AW: an allowance goes whole
     * ([RuleChange.Item]); 1.29 made it a name and an amount ([RuleChange.Allowance]), so a % or 사람마다 칸 came in as 0원.
     */
    fun change(made: MadeItem, shiftNames: Map<Long, String>, @Suppress("UNUSED_PARAMETER") month: YearMonth): RuleChange = when (made) {
        is MadeItem.Rule -> RuleChange.Custom(made.rule, shiftNames)
        is MadeItem.Item -> RuleChange.Item(made.item)
    }
}
