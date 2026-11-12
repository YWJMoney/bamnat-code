package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 2.0.0 갈래 N 믿음 줄: 이 설정 쓰기는 바뀌는 것을 먼저 보여 주고 되돌릴 수 있어요(COMP-11). 모두 지우기 뒤 저절로 나누기는 묻기로
 * 돌아가고(COMP-43), 근무주기 동의는 급여 방식까지 넓어지지 않으며(COMP-44), 거두지 못한 되돌리기는 기다렸다 보내요(COMP-46).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class N200CompanyTrustTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 11, 20)

    @After fun close() { db.close() }

    private suspend fun calendar(): Pair<ScheduleRepository, CompanyShare> {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val t = schedule.snapshot.first().shiftTypes
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        val off = t.first { it.isOff }.id
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day, night, off, off), anchorDate = today), today)
        return schedule to CompanyShare(schedule, PayStore(context), SettingsRepository(context), today = { today })
    }

    /** A coworker's file: 주간 an hour earlier, a 모닝 of their own, and a 6-day order. */
    private suspend fun theirs(share: CompanyShare): String {
        val o = JSONObject(share.export("밤낮전자", withAmounts = false))
        val types = o.getJSONArray("shiftTypes")
        for (i in 0 until types.length()) {
            val t = types.getJSONObject(i)
            if (t.getString("name") == "주간") t.put("start", "06:00").put("end", "14:00")
        }
        types.put(JSONObject().put("name", "모닝").put("label", "모").put("start", "05:00").put("end", "13:00").put("break", 30).put("off", false))
        val off = (0 until types.length()).map { types.getJSONObject(it) }.first { it.optBoolean("off") }.getString("name")
        o.put("rotation", JSONObject().put("steps", JSONArray(listOf("모닝", "주간", "야간", off, off, off))))
        return o.toString()
    }

    @Test fun applyShowsWhatChangesAndUndoPutsTheCalendarBack() = runBlocking {
        val (schedule, share) = calendar()
        val before = schedule.snapshot.first()
        val text = theirs(share)
        val preview = share.preview(text)
        assertEquals(listOf("주간 09:00~18:00 (휴게 60분) → 06:00~14:00 (휴게 60분)"), preview.changed)
        assertEquals(listOf("모닝 05:00~13:00 (휴게 30분)"), preview.added)
        assertEquals(6, preview.cycle)
        assertFalse(preview.weekly)
        // Applied, then put back: the times, the order and the kinds of day as they were.
        val was = share.before()
        assertTrue(was.hadOrder)
        val applied = share.apply(text, rotation = true, todayStep = 0)
        assertEquals(6, applied.cycleDays)
        assertTrue(schedule.snapshot.first().shiftTypes.any { it.name == "모닝" })
        share.undo(was)
        val after = schedule.snapshot.first()
        assertEquals(before.patterns, after.patterns)
        assertEquals(before.shiftTypes.map { Triple(it.name, it.start, it.end) }, after.shiftTypes.map { Triple(it.name, it.start, it.end) })
        assertFalse(after.shiftTypes.any { it.name == "모닝" })
    }

    @Test fun aKindAddedThenUsedOnADayStaysOnUndo() = runBlocking {
        val (schedule, share) = calendar()
        val was = share.before()
        share.apply(theirs(share), rotation = true, todayStep = 0)
        val morning = schedule.snapshot.first().shiftTypes.first { it.name == "모닝" }
        // Put on a day by hand before 되돌리기: that day is the user's, so the kind stays.
        schedule.setOverride(today.plusDays(10), com.bamnat.core.model.ShiftOverride.Shift(morning.id))
        share.undo(was)
        assertTrue(schedule.snapshot.first().shiftTypes.any { it.name == "모닝" })
    }

    @Test fun forgetAllTurnsAutoShareBackToAskAndDropsWhatWaits() {
        val prefs = context.getSharedPreferences("n200-trust-${System.nanoTime()}", Context.MODE_PRIVATE)
        val store = CompanyStore(prefs, null)
        store.setAutoShare(true)
        store.setAutoPay(true)
        store.autoDirty = true
        store.takeBackLater("r-1", waiting = true)
        store.forgetSent()
        assertNull(store.autoShare.value)
        assertFalse(store.autoPay.value)
        assertFalse(store.autoDirty)
        assertTrue(store.takeBackLater.isEmpty())
        // A 나누지 않기 the user chose stays as it is.
        store.setAutoShare(false)
        store.forgetSent()
        assertEquals(false, store.autoShare.value)
    }

    @Test fun aCycleYesIsNotAPayYes() {
        val prefs = context.getSharedPreferences("n200-pay-${System.nanoTime()}", Context.MODE_PRIVATE)
        val store = CompanyStore(prefs, null)
        // A yes from 근무주기 나누기 (LeadShare) sets 저절로 나누기 alone.
        store.setAutoShare(true)
        assertFalse(store.autoPay.value)
        store.setAutoPay(true)
        // Back to 묻기: the pay yes goes with it, and is not there when the cycle's yes comes again.
        store.setAutoShare(null)
        assertFalse(store.autoPay.value)
        store.setAutoShare(true)
        assertFalse(CompanyStore(prefs, null).autoPay.value)
    }

    @Test fun takeBacksWaitOnThePhoneUntilDone() {
        val prefs = context.getSharedPreferences("n200-back-${System.nanoTime()}", Context.MODE_PRIVATE)
        val store = CompanyStore(prefs, null)
        store.takeBackLater("r-1", waiting = true)
        store.takeBackLater("r-2", waiting = true)
        assertEquals(setOf("r-1", "r-2"), CompanyStore(prefs, null).takeBackLater)
        store.takeBackLater("r-1", waiting = false)
        assertEquals(setOf("r-2"), store.takeBackLater)
    }

    @Test fun forgetWordsSayWhatWentOff() {
        assertEquals("토론 글 2를 지웠어요. 광장 바로 알림과 저절로 나누기도 껐어요. 설정에서 다시 켤 수 있어요",
            ForgetCounts(posts = 2, alertsOff = true, autoShareOff = true).words())
        assertEquals("서버에 지울 것이 없었어요. 저절로 나누기도 껐어요. 설정에서 다시 켤 수 있어요", ForgetCounts(autoShareOff = true).words())
        assertEquals("서버에 지울 것이 없었어요", ForgetCounts().words())
    }
}
