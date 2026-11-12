package com.bamnat.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * 2.0.0 [RVI-54] (검토 ALM-27) A shift changed twice in a row: the widgets shared one reading for a refresh ([RVI-05]), so the second
 * change's refresh must read again and draw the second, never the first again.
 */
@RunWith(AndroidJUnit4::class)
class WidgetTwoChangesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun theSecondChangeIsDrawnNotTheFirst() = runBlocking<Unit> {
        val repo = context.appContainer.scheduleRepository
        val today = Today.date()
        val day = repo.saveShiftType(ShiftType(id = 0, name = "주간", label = "주", colorArgb = 0xFF2F80ED, start = LocalTime.of(9, 0), end = LocalTime.of(18, 0)))
        val night = repo.saveShiftType(ShiftType(id = 0, name = "야간", label = "야", colorArgb = 0xFF5B3CC4, start = LocalTime.of(21, 0), end = LocalTime.of(7, 0)))

        suspend fun drawn(): String? = WidgetData.load(context).resolver.resolve(today).shiftType?.name
        suspend fun refreshUntil(name: String) = withTimeout(5_000) {
            while (true) {
                WidgetData.invalidate() // what each refresh does first
                if (drawn() == name) break
                delay(20)
            }
        }

        repo.setOverride(today, ShiftOverride.Shift(day))
        refreshUntil("주간")
        repo.setOverride(today, ShiftOverride.Shift(night))
        // Widgets drawn in the same refresh share its reading: the first change, until the second's refresh starts.
        assertEquals("주간", drawn())
        refreshUntil("야간")
        assertEquals("야간", drawn())
    }
}
