package com.bamnat.core

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 PC eae4e024: a first setup of 일근 folds 대근 (잔업·특근 stay); kinds someone already changed are left alone. */
class ExtraKindsDayWorkTest {
    @Test fun defaultsFoldTheCoverOnly() {
        val day = ExtraKinds().forDayWork()
        assertTrue(day.of(ExtraKind.COVER_ID).hidden)
        assertEquals(listOf(ExtraKind.OVERTIME_ID, ExtraKind.SPECIAL_ID), day.visible().map { it.id })
        // Folded, not gone: a day that already has one shows it, and it can be turned on again.
        assertEquals(3, day.visible(listOf(ExtraKind.COVER_ID)).size)
    }

    @Test fun changedKindsStay() {
        val renamed = ExtraKinds().put(ExtraKinds().of(ExtraKind.COVER_ID).copy(name = "대타"))
        assertEquals(renamed, renamed.forDayWork())
        // A company file's own kinds (a 대근 for 야간 only) are not touched either.
        val company = ExtraKinds().put(ExtraKinds().of(ExtraKind.COVER_ID).copy(short = "근"))
        assertFalse(company.forDayWork().of(ExtraKind.COVER_ID).hidden)
    }
}
