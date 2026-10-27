package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.CalendarStorage
import com.bamnat.app.data.FirstSetup
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.Collections

/**
 * [CALI-01]~[CALI-08] 1.30 묶음 BB (사용자 2026-10-27: "다른 달력으로 회사를 선택하면 전체로 적용이 되고 있다. 달력은 개별적으로
 * 적용이 되게 해야 한다", "내가 보낸 규칙도 해당 달력에 맞춰 보여져야 한다. 온전한 독립 달력이다"): each calendar's company is its
 * own. 내 달력's is the very objects and files of before; an added calendar's is two small files in its folder that go with
 * it (deleted, backed up, restored), and the server hears of its going only when no other calendar is in that company.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanyScopeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private lateinit var before: CompanyApi
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before fun start() {
        before = c.companyApi
        c.companyApi = Recorder(calls)
        c.calendars.added().forEach { c.calendars.delete(it.id) }
        clearMain()
        calls.clear()
    }

    @After fun end() {
        c.calendars.added().forEach { c.calendars.delete(it.id) }
        clearMain()
        c.companyApi = before
    }

    private fun clearMain() {
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
        c.companyStore.setDraft(null)
        c.companyStore.setPublicUse(null)
        c.companyStore.pendingPay = null
        c.payWays.use(null)
    }

    private fun added(name: String): String = c.calendars.begin(name).also { c.calendars.finishSetup(it, WorkStyle.SHIFT) }

    private fun applied(id: String, name: String, setup: String = "s-$id") =
        AppliedSetup(id, name, "", setup, 1, true, Instant.parse("2026-10-27T00:00:00Z"))

    private suspend fun waitFor(what: String, check: () -> Boolean) {
        runCatching { withTimeout(5_000) { while (!check()) delay(20) } }
        assertTrue(what, check())
    }

    @Test fun theMainCalendarsCompanyIsTheAppsOwnObjects() {
        val main = c.companyOf(PersonalCalendar.MAIN)
        assertTrue(main.isMain)
        assertSame(c.companyStore, main.store)
        assertSame(c.payWays, main.payWays)
        assertSame(c.autoShare, main.autoShare)
        assertSame(c.companyShare, main.share)
        assertSame(c.payStore, main.pay)
        assertEquals("main", main.serverCal)
        // A calendar that is not (or no longer) there is 내 달력's, as CalendarBook.repository does.
        assertSame(main, c.companyOf("nothere12345"))
        assertEquals(listOf(main), c.companyScopes())
    }

    @Test fun aPhoneFromBeforeReadsItsCompanyAsItWas() {
        // 1.29's own preferences file, written as it was: read the same, nothing moved.
        c.companyStore.setApplied(applied("c-old", "옛회사"))
        val raw = context.getSharedPreferences("company", Context.MODE_PRIVATE).getString("applied", null)
        assertNotNull(raw)
        c.companyStore.reload()
        assertEquals("옛회사", c.companyOf(PersonalCalendar.MAIN).store.applied.value?.company)
        assertEquals("옛회사", c.companyOf(PersonalCalendar.MAIN).companyName())
    }

    @Test fun twoCalendarsChooseTheirCompaniesApart() {
        val b = added("둘째")
        val second = c.companyOf(b)
        assertFalse(second.isMain)
        assertSame(second, c.companyOf(b))
        assertEquals(b, second.serverCal)
        // The phone's install id is one for every calendar.
        assertEquals(c.companyStore.device, second.store.device)

        second.store.setApplied(applied("c-b", "병원B"))
        second.payWays.use(PayWay("c-b", "병원B", "", "s-c-b"))
        assertNull("내 달력은 그대로", c.companyStore.applied.value)
        assertNull(c.payWays.way.value)

        c.companyStore.setApplied(applied("c-a", "공장A"))
        assertEquals("병원B", second.store.applied.value?.company)
        assertEquals("공장A", c.companyStore.applied.value?.company)
        assertEquals(listOf("공장A", "병원B"), c.companyScopes().map { it.companyName() })
        // Its own files, in its own folder.
        val dir = CalendarStorage.filesOf(context, b)
        CompanyScopeStorage.prefs(context, b, CompanyScopeStorage.COMPANY).edit().commit()
        assertTrue(File(dir, CompanyScopeStorage.COMPANY).exists())
        assertTrue(File(dir, CompanyScopeStorage.COMPANY).readText().contains("병원B"))
        // Sending is the phone's one choice; what was sent is each calendar's.
        second.store.addSent(SentReport("r-b", "병원B", "", false, Instant.now()))
        assertTrue(c.companyStore.sent.value.none { it.id == "r-b" })
    }

    @Test fun aCompanyPickedInAnAddedCalendarsSetupIsThatCalendars() = runBlocking {
        val b = added("둘째")
        val text = JSONObject().put("company", "병원B").put("pay", JSONObject()).toString()
        val draft = SetupDraft(style = WorkStyle.SHIFT,
            source = SetupSource(SetupSource.Kind.SERVER, text, id = "c-b", setupId = "s1", name = "병원B", version = 2, adopted = true))
        var used: Pair<String, String>? = null
        FirstSetup.keepCompany(c.companyOf(b), draft) { s, a -> used = s.calendarId to a.setup }
        val second = c.companyOf(b)
        assertEquals("병원B", second.store.applied.value?.company)
        // The pay asks no more for the company already chosen.
        assertEquals("c-b", second.payWays.way.value?.companyId)
        assertEquals(b to "s1", used)
        assertNull("내 달력은 그대로", c.companyStore.applied.value)
        assertNull(c.payWays.way.value)
    }

    @Test fun aCalendarGoneLeavesItsCompanyUnlessAnotherIsInIt() = runBlocking {
        val b = added("둘째")
        val cal = added("셋째")
        c.companyOf(b).store.setApplied(applied("c-b", "병원B", "s-b"))
        c.companyOf(b).store.setMember("c-b", "병원B")
        c.companyOf(cal).store.setMember("c-x", "같은회사")
        c.companyStore.setMember("c-x", "같은회사")
        val dir = CalendarStorage.filesOf(context, b)

        c.calendars.delete(b)
        waitFor("떠남: $calls") { "leave:c-b" in calls }
        assertTrue(calls.toString(), "use:c-b:s-b:false:$b" in calls)
        assertFalse(File(dir, CompanyScopeStorage.COMPANY).exists())
        assertSame(c.companyOf(PersonalCalendar.MAIN), c.companyOf(b))

        calls.clear()
        c.calendars.delete(cal)
        delay(300)
        assertTrue("내 달력이 같은 회사에 있어요: $calls", calls.none { it.startsWith("leave") })
        assertEquals("c-x", c.companyStore.member.value?.first)
    }

    @Test fun anAddedCalendarsCompanyGoesInTheBackupAndComesBack() = runBlocking {
        val b = added("둘째")
        val second = c.companyOf(b)
        second.store.setApplied(applied("c-b", "병원B"))
        second.store.setMember("c-b", "병원B")
        second.payWays.use(PayWay("c-b", "병원B", "", "s-c-b"))
        val file = c.backupManager.export()
        val saved = JSONObject(file).toString()
        assertTrue(saved.contains("calendarCompany"))

        second.store.setApplied(applied("c-z", "다른곳"))
        second.payWays.use(null)
        c.backupManager.restore(file)

        val back = c.companyOf(b)
        assertEquals("병원B", back.store.applied.value?.company)
        assertEquals("c-b", back.payWays.way.value?.companyId)
        assertNull("내 달력은 그대로", c.companyStore.applied.value)
    }

    /** Records what the server is told; everything else is refused. */
    private class Recorder(private val calls: MutableList<String>) : CompanyApi {
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage = throw CompanyException("offline")
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus =
            throw CompanyException("offline")
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult =
            throw CompanyException("offline")
        override suspend fun use(device: String, company: String, setup: String, on: Boolean, cal: String): Int {
            calls += "use:$company:$setup:$on:$cal"; return 1
        }
        override suspend fun leave(device: String, company: String) { calls += "leave:$company" }
    }
}
