package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.RoomCards
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * [PLAZA-07][PLAZA-08][PLAZA-09][PLAZA-10] 1.26 묶음 AF (결정 D79) from the app's own files to the real company server
 * (server/company-worker on Node with SQLite): the first provider's version with its pay is new information and shows at
 * once; another phone sees the shifts only until it joins (applying is joining), then reads only what others changed and
 * takes one value; the one-time yes shares by itself and opens the company. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaAFAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val store = PayStore(context)
    private val today = LocalDate.of(2026, 10, 19)

    @After fun close() {
        db.close()
        runBlocking { store.save(null) }
    }

    private suspend fun rotation(): ScheduleRepository {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val t = schedule.snapshot.first().shiftTypes
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day, day, night, night, null, null), today), today)
        return schedule
    }

    private suspend fun pay(base: Long, night: Double?) = store.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY,
        ValueTimeline(listOf(DatedValue(today, base))), rules = CompanyRules(nightPremium = night)), keepUndo = false)

    @Test fun theFirstProviderJoinsAndOthersSeeThePayOnlyAfterJoining() = runBlocking {
        val schedule = rotation()
        val share = CompanyShare(schedule, store, settings, today = { today })
        pay(3_000_000, 0.5)
        val first = share.exportForServer()
        assertFalse("no amount leaves the phone", first.toString().contains("3000000"))
        withServer { base ->
            val a = CompanyClient(base)
            val sent = a.report("af-app-device-000001", "참여상사", "", first, "main", auto = true)
            // 제보 1 · 확인 중 at once, and new information since its pay came with it.
            assertEquals("pending", sent.status)
            assertTrue(sent.first)
            assertTrue(sent.newInfo)
            assertNotNull(sent.reward)
            assertTrue(sent.member!!.joined)
            assertEquals(1, a.terms().firstMonths)

            // Another phone, not a member: found by name, shifts and order only, "급여 방식 있음".
            val bDevice = "af-app-device-000002"
            val b = CompanyClient(base, device = { bDevice })
            val id = b.search("참여상").single().id
            val outside = b.company(id)
            assertFalse(outside.member)
            val s = outside.setups.single()
            assertTrue(s.payHidden)
            assertTrue(s.hasPay)
            assertFalse(JSONObject(s.text).has("pay"))
            assertTrue(s.lines.none { (label, _) -> label == "야간" })
            assertNull(b.room(bDevice, id))
            // An app before 1.26 (no X-Device) gets the same, gracefully.
            assertTrue(CompanyClient(base).company(id).setups.single().payHidden)

            // Applying is joining: then the pay way comes, and the room.
            assertTrue(b.join(bDevice, id, "main").joined)
            val inside = b.company(id)
            assertTrue(inside.member)
            assertTrue(JSONObject(inside.setups.single().text).has("pay"))
            val room = b.room(bDevice, id)!!
            assertTrue(room.first)
            assertTrue(room.others.isEmpty())

            // A third phone sends 야간 60%: the member sees that one value as a card and takes it alone.
            pay(2_800_000, 0.6)
            val c = CompanyClient(base)
            val other = c.report("af-app-device-000003", "참여상사", "", share.exportForServer(), "main", auto = true)
            assertFalse(other.first)
            assertFalse("only the first with pay is new information", other.newInfo)
            pay(2_500_000, 0.5)
            val mine = share.exportForServer().toString()
            val again = b.room(bDevice, id)!!
            val versions = (again.others + listOfNotNull(again.base?.takeIf { !again.mine })).map { RoomCards.Version(it.setup, it.text, maxOf(it.senders, it.persons)) }
            val card = RoomCards.cards(mine, versions).single { it.key == "rate:night" }
            assertEquals("야간 50% → 60%로 바꾼 사람 1명", card.text)
            assertTrue(share.takeOne(versions.first { it.id == card.from }.text, card.key))
            val now = store.profile.value!!
            assertEquals(0.6, now.rules.nightPremium!!, 1e-9)
            assertEquals("the user's own amount stays", 2_500_000L, now.base.on(today).value)

            // One company per calendar for 30 days, two companies per phone.
            val d = CompanyClient(base)
            val x2 = d.report("af-app-device-000004", "둘째상사", "", share.exportForServer(), "main", auto = false).companyId
            val x3 = d.report("af-app-device-000004", "셋째상사", "", share.exportForServer(), "cal2", auto = false).companyId
            assertEquals("member_wait", b.join(bDevice, x2, "main").error)
            assertTrue(b.join(bDevice, x2, "cal2").joined)
            assertEquals("member_limit", b.join(bDevice, x3, "cal3").error)
        }
    }

    @Test fun aCycleOnlyFirstSubmissionIsFoundAtOnceAndEarnsNothing() = runBlocking {
        store.save(null)
        val cycle = CompanyShare(rotation(), store, settings, today = { today }).exportForServer()
        assertFalse(cycle.has("pay"))
        withServer { base ->
            val api = CompanyClient(base)
            val sent = api.report("af-app-device-000011", "순서만상사", "생산팀", cycle, "main", auto = false)
            assertTrue(sent.first)
            assertFalse(sent.newInfo)
            assertNull(sent.reward)
            val hit = CompanyClient(base).search("순서만").single()
            val setup = CompanyClient(base, device = { "af-app-device-000012" }).company(hit.id).setups.single()
            assertFalse(setup.payHidden)
            assertFalse(setup.hasPay)
            assertTrue(setup.lines.any { (label, _) -> label.startsWith("순서") })
        }
    }

    @Test fun theOneTimeYesSharesByItselfOnceADayAndOpensTheCompany() = runBlocking {
        val c = context.appContainer
        c.scheduleRepository.ensureDefaults()
        val t = c.scheduleRepository.snapshot.first().shiftTypes
        c.scheduleRepository.setFirstPattern(WorkPattern.Rotation(today, listOf(t.first { it.name == "주간" }.id, null), today), today)
        c.companyStore.setApplied(null)
        c.companyStore.setDraft(CompanyDraft("자동나눔상사", "총무팀"))
        c.companyStore.setAutoShare(null)
        c.companyStore.autoBase = null
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 3_000_000))),
            rules = CompanyRules(nightPremium = 0.5)), keepUndo = false)
        val before = c.companyApi
        withServer { base ->
            try {
                c.companyApi = CompanyClient(base, device = { c.companyStore.device })
                // [PLAZA-34] 1.27 AI: the first save for a known company asks about the whole setup.
                assertNull(c.companyStore.autoShare.value)
                assertTrue(c.autoShare.saved())
                val ask = withTimeout(10_000) { while (c.autoShare.ask.value == null) delay(20); c.autoShare.ask.value!! }
                assertEquals("자동나눔상사", ask.company)
                assertEquals(1, ask.rewardMonths)
                assertTrue(ask.first)
                // 나눌게요 with 다음부터 묻지 않고 저절로 나누기: remembered, sent now, the company's 광장 opens.
                c.autoShare.approve(always = true)
                val opened = withTimeout(15_000) { while (c.autoShare.opened.value == null) delay(20); c.autoShare.opened.value!! }
                c.autoShare.openedDone()
                assertEquals(true, c.companyStore.autoShare.value)
                assertEquals(opened, c.companyApi.search("자동나눔상사").single().id)
                assertEquals(opened, c.companyStore.member.value?.first)
                assertFalse(c.companyStore.autoDirty)
                // The same values saved again: nothing waits and nothing is said.
                c.autoShare.check()
                assertFalse(c.autoShare.waiting)
                assertNull(c.autoShare.shared.value)
                // A real change waits for its day, with one line and 되돌리기; flushing sends nothing yet.
                c.payStore.save(c.payStore.profile.value!!.copy(rules = CompanyRules(nightPremium = 0.6)), keepUndo = false)
                c.autoShare.check()
                assertTrue(c.autoShare.waiting)
                val line = c.autoShare.shared.value!!
                assertTrue(line.queued)
                assertEquals("야간 50% → 60%", line.line)
                assertTrue(c.autoShare.flush())
                assertTrue(c.autoShare.waiting)
                assertTrue(OutboxJob.waiting(c) >= 1)
                // 되돌리기 takes it off the queue.
                c.autoShare.undo()
                assertFalse(c.autoShare.waiting)
                // 설정's switch off asks again each time; 다시 묻지 않기 stops it.
                c.autoShare.set(null)
                assertTrue(c.autoShare.saved())
                c.autoShare.set(false)
                assertFalse(c.autoShare.waiting)
                assertFalse(c.autoShare.saved())
            } finally {
                c.companyApi = before
                c.companyStore.setDraft(null)
                c.companyStore.setMember(null)
                c.companyStore.setAutoShare(null)
                c.companyStore.autoBase = null
                c.autoShare.sharedDone()
                c.payStore.save(null)
            }
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
