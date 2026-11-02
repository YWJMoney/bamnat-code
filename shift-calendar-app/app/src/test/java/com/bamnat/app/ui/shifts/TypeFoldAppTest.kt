package com.bamnat.app.ui.shifts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** 1.36 PC 재검토: 알바 → 현장 asks once, and 접어 두기 puts the three 알바 kinds away. */
@RunWith(AndroidJUnit4::class)
class TypeFoldAppTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @Test fun foldingPutsThe알바KindsAway() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        repo.addHourlyTypes()
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.FIELD)
        val ask = TypeFoldAsk.asking.value
        assertNotNull(ask)
        assertEquals(3, ask!!.types.size)
        c.foldedTypes.fold(ask.calendar, ask.types.map { it.id })
        assertEquals(ask.types.map { it.id }.toSet(), c.foldedTypes.state.value[PersonalCalendar.MAIN])
        val vm = ShiftsViewModel(c, PersonalCalendar.MAIN)
        assertEquals(ask.types.map { it.id }.toSet(), vm.folded.first())
        // Back to 알바: out again by itself.
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.HOURLY)
        assertEquals(null, c.foldedTypes.state.value[PersonalCalendar.MAIN])
    }
}
