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
import com.bamnat.app.ui.company.proposalBody
import com.bamnat.app.ui.company.proposalChange
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.core.plaza.SetupEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * [PLAZA-01][PLAZA-02][PLAZA-05] 1.25 묶음 X from the app's own files to the real company server (server/company-worker on
 * Node with SQLite) and back to another phone: a weekday company's days travel, a 다른 점 말하기 proposal goes up with
 * the edited file and its lines, and the history's versions come back with their files. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaXAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val store = PayStore(context)
    /** A Monday. */
    private val today = LocalDate.of(2026, 10, 19)

    @After fun close() {
        db.close()
        runBlocking { store.save(null) }
    }

    /** 주5일 평일 근무 (월~금 주간, 토·일 쉼), as a weekday worker's calendar has it. */
    private suspend fun weekdaySetup(): ScheduleRepository {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val day = schedule.snapshot.first().shiftTypes.first { it.name == "주간" }.id
        schedule.setFirstPattern(WorkPattern.Weekly.fromSteps(today, listOf(day, day, day, day, day, null, null)), today)
        return schedule
    }

    @Test fun weeklyDaysTravelToTheServerAndOntoAnotherPhone() = runBlocking {
        val sender = CompanyShare(weekdaySetup(), store, settings, today = { today })
        val file = sender.exportForServer()
        assertFalse("a weekday company has no rotation", file.has("rotation"))
        assertEquals(listOf("주간", "주간", "주간", "주간", "주간", null, null),
            (0 until 7).map { i -> file.getJSONArray("weekly").let { if (it.isNull(i)) null else it.getString(i) } })
        withServer { base ->
            val api = CompanyClient(base)
            assertEquals("pending", api.report("plaza-x-device-0001", "평일상사", "총무팀", false, "", file).status)
            val page = api.company(api.search("평일상사").single().id)
            val setup = page.setups.single()
            assertTrue(setup.lines.any { (label, text) -> label == "요일별 근무" && "토 쉼" in text })
            // Another phone takes it from today: Monday to Friday 주간, the weekend off.
            val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            try {
                val theirs = ScheduleRepository(db2).also { it.ensureDefaults() }
                CompanyShare(theirs, store, settings, today = { today }).apply(setup.text, rotation = true)
                val r = theirs.snapshot.first().resolver
                assertEquals("주간", r.resolve(today).shiftType?.name)
                assertEquals(DayOfWeek.SATURDAY, today.plusDays(5).dayOfWeek)
                assertNull(r.resolve(today.plusDays(5)).shiftType?.takeIf { !it.isOff })
                assertEquals("주간", r.resolve(today.plusDays(7)).shiftType?.name)
            } finally {
                db2.close()
            }
        }
    }

    @Test fun aDifferenceIsProposedWithTheEditedFile() = runBlocking {
        val share = CompanyShare(weekdaySetup(), store, settings, today = { today })
        withServer { base ->
            val api = CompanyClient(base)
            api.report("plaza-x-device-0011", "다른점상사", "", false, "", share.exportForServer())
            val page = api.company(api.search("다른점상사").single().id)
            val base0 = page.setups.single()
            // 다른 점 말하기: 주간 ends an hour earlier and Saturday is worked.
            val edit = SetupEdit.of(base0.text)!!.let { e -> e.shiftTime("주간", e.shifts.first { it.name == "주간" }.start ?: "09:00", "17:00", 60) }
                .let { it.weekly(it.weekly!!.toMutableList().also { w -> w[5] = "주간" }) }
            val rows = SetupCompare.rows(base0.text, edit.text)
            val lines = proposalChange(rows)
            assertEquals(listOf("shift", "order"), lines.map { it.field })
            api.post("plaza-x-device-0012", page.id, base0.id, proposalBody(rows, ""), null, "proposal", lines, JSONObject(edit.text))
            val talk = api.talk("plaza-x-device-0012", page.id, base0.id)
            val post = talk.posts.single()
            assertEquals("proposal", post.kind)
            assertEquals("다른 점 2가지: 주간 · 요일별 근무", post.body)
            assertEquals(lines.map { it.want }, post.change.map { it.want })
            // The proposed file is its own rule set beside the first, as people vote it in.
            val proposal = post.proposal
            assertNotNull(proposal)
            val again = api.company(page.id).setups.first { it.id == proposal!!.setup }
            assertEquals("17:00", JSONObject(again.text).getJSONArray("shiftTypes").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("name") == "주간" }.getString("end")
            })
            assertTrue(SetupCompare.rows(base0.text, again.text).count { !it.same } == 2)
        }
    }

    /** Runs server/company-worker on Node with SQLite for [block], given its address. Skipped where Node is missing. */
    private suspend fun withServer(block: suspend (String) -> Unit) {
        val dir = File("../server/company-worker")
        assumeTrue(dir.resolve("test/local-server.mjs").exists())
        assumeTrue(runCatching { ProcessBuilder("node", "--version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        val port = ServerSocket(0).use { it.localPort }
        val node = ProcessBuilder("node", "test/local-server.mjs", port.toString()).directory(dir).redirectErrorStream(true).start()
        try {
            val out = node.inputStream.bufferedReader()
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < 20_000) {
                val line = out.readLine() ?: break
                if ("company worker on" in line) break
            }
            block("http://127.0.0.1:$port")
        } finally {
            node.destroy()
            node.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
