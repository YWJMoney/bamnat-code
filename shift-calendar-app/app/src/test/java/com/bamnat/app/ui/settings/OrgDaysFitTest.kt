package com.bamnat.app.ui.settings

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 [RVO-27] HQ-141 (조건 13): 우리 업종 기념일 (소방의 날·경찰의 날) is offered to 교대·일근 only. */
class OrgDaysFitTest {
    @Test fun onlyAgencyWork() {
        assertEquals(setOf(WorkStyle.SHIFT, WorkStyle.DAY), WorkStyle.entries.filter { orgDaysFit(it) }.toSet())
        assertEquals(true, orgDaysFit(null))
    }
}
