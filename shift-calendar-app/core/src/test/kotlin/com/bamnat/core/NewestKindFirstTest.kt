package com.bamnat.core

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.schedule.QuickOrder
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [PAYX-51] 1.38 PC 12차: the kind made last is the first chip until it is put on a day; then use decides as before. */
class NewestKindFirstTest {
    private val cover = ExtraKind.COVER_ID
    private val special = ExtraKind.SPECIAL_ID
    private val overtime = ExtraKind.OVERTIME_ID
    private val mine = ExtraKind.FIRST_CUSTOM_ID
    private val schooling = ExtraKind.FIRST_CUSTOM_ID + 1

    private fun order(used: List<Long>, ever: Set<Long>?) =
        QuickOrder.extras(listOf(overtime, special, cover, mine, schooling), { it }, used, WorkStyle.SHIFT, ever)

    @Test fun aKindJustMadeComesFirst() {
        assertEquals(listOf(schooling, cover, special, overtime, mine), order(emptyList(), emptySet()))
    }

    @Test fun itComesFirstEvenOverKindsInUse() {
        assertEquals(schooling, order(listOf(special, special), setOf(special)).first())
    }

    @Test fun onceUsedItTakesItsPlaceByUse() {
        val o = order(listOf(special, special, schooling), setOf(special, schooling))
        assertEquals(listOf(special, schooling), o.take(2))
    }

    @Test fun withoutTheHistoryNothingMoves() {
        assertEquals(listOf(cover, special, overtime, mine, schooling), order(emptyList(), null))
    }

    @Test fun builtInKindsAreNeverTheNewest() {
        assertNull(QuickOrder.newest(listOf(overtime, special, cover), emptySet()))
        assertNull(QuickOrder.newest(listOf(cover, mine), setOf(mine)))
    }
}
