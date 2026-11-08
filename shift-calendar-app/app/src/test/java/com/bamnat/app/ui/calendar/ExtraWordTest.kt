package com.bamnat.app.ui.calendar

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.39 PC 6차: 일근's 칸 창 says 야근 on the chip and on the 추가 근무 line alike ([J133-27]). */
class ExtraWordTest {
    private val overtime = com.bamnat.core.model.ExtraKinds.DEFAULTS.first { it.id == ExtraKind.OVERTIME_ID }
    private val special = com.bamnat.core.model.ExtraKinds.DEFAULTS.first { it.id == ExtraKind.SPECIAL_ID }

    @Test fun weekdayWorkerSaysYagunForTheBuiltInOvertimeOnly() {
        assertEquals("야근", ExtraText.kindName(overtime, WorkStyle.DAY))
        assertEquals("잔업", ExtraText.kindName(overtime, WorkStyle.SHIFT))
        assertEquals("잔업", ExtraText.kindName(overtime, null))
        assertEquals(special.name, ExtraText.kindName(special, WorkStyle.DAY))
        assertEquals("야근 +2시간", ExtraText.word("잔업 +2시간", ExtraKind.OVERTIME_ID, WorkStyle.DAY))
    }
}
