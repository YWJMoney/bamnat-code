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
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.model.LeaveKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 2.0.0 [RVL-80] (검토 I07bc-02, 처리방침 3.14 문장표 9) 회사 설정 보내기 carries the roster's codes only while the
 * `share.duty_codes` flag is on: the code and the company's kind of work, 20 at most, never to the company server; a coworker's
 * phone keeps them for its rosters without changing a code its user taught.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanyShareDutyCodesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sender = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val receiver = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 12, 21)

    @After fun close() { sender.close(); receiver.close() }

    private suspend fun typesOf(schedule: ScheduleRepository) = schedule.ensureDefaults().let { schedule.snapshot.first().shiftTypes }

    private suspend fun sendingShare(on: Boolean, book: (Long, Long) -> Map<String, DutyCell>): CompanyShare {
        val schedule = ScheduleRepository(sender)
        val t = typesOf(schedule)
        val codes = book(t.first { it.name == "주간" }.id, t.first { it.name == "야간" }.id)
        return CompanyShare(schedule, PayStore(context), SettingsRepository(context), today = { today },
            dutyCodes = { codes }, sendCodes = { on })
    }

    private fun mine(day: Long, night: Long) = mapOf(
        "N2" to DutyCell.Shift(night), "D" to DutyCell.Shift(day), "OF" to DutyCell.Off,
        // Leave and hours stay on the phone: they are not the company's kinds of work.
        "병가" to DutyCell.Leave(LeaveKind.PAID), "4H" to DutyCell.Hours(null, 240),
    )

    @Test fun offTheFileCarriesNoCodes() = runBlocking {
        val text = sendingShare(on = false, ::mine).export("밤낮전자", withAmounts = false)
        assertFalse(JSONObject(text).has("dutyCodes"))
        assertEquals(0, sendingShare(on = false, ::mine).summarize(text).dutyCodes)
    }

    @Test fun onTheFileCarriesCodeAndKindOnlyAndNeverToTheServer() = runBlocking {
        val share = sendingShare(on = true, ::mine)
        val text = share.export("밤낮전자", withAmounts = false)
        val codes = JSONObject(text).getJSONArray("dutyCodes")
        val got = (0 until codes.length()).map { codes.getJSONObject(it) }
        assertEquals(listOf("D", "N2", "OF"), got.map { it.getString("code") })
        assertEquals("주간", got[0].getString("shift"))
        assertEquals("야간", got[1].getString("shift"))
        assertTrue(got[2].getBoolean("off"))
        // Only the code and the kind: nothing else rides along.
        got.forEach { o -> assertTrue(o.keys().asSequence().toSet() in setOf(setOf("code", "shift"), setOf("code", "off"))) }
        assertEquals(3, share.summarize(text).dutyCodes)
        assertFalse(share.exportForServer().has("dutyCodes"))
    }

    @Test fun twentyCodesAtMost() = runBlocking {
        val share = sendingShare(on = true) { day, _ -> (1..30).associate { "D$it" to DutyCell.Shift(day) } }
        val text = share.export("밤낮전자", withAmounts = false)
        assertEquals(CompanyShare.MAX_CODES, JSONObject(text).getJSONArray("dutyCodes").length())
        assertEquals(CompanyShare.MAX_CODES, share.summarize(text).dutyCodes)
    }

    @Test fun aCoworkerKeepsTheCodesButNotOverTheirOwn() = runBlocking {
        val text = sendingShare(on = true, ::mine).export("밤낮전자", withAmounts = false)
        val schedule = ScheduleRepository(receiver)
        val t = typesOf(schedule)
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        // The coworker already taught D as 야간 on their ward; that stays.
        val book = mutableMapOf<String, DutyCell>(DutyCodes.norm("D") to DutyCell.Shift(night))
        val share = CompanyShare(schedule, PayStore(context), SettingsRepository(context), today = { today },
            dutyCodes = { book }, learnCode = { code, cell -> book[DutyCodes.norm(code)] = cell })
        share.apply(text)
        assertEquals(DutyCell.Shift(night), book[DutyCodes.norm("D")])
        assertEquals(DutyCell.Shift(night), book[DutyCodes.norm("N2")])
        assertEquals(DutyCell.Off, book[DutyCodes.norm("OF")])
        assertEquals(3, book.size)
        // The kinds are matched by name on this phone, whatever ids the sender's phone gave them.
        assertTrue(book.values.none { it is DutyCell.Shift && it.shiftTypeId !in setOf(day, night) })
    }
}
