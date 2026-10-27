package com.bamnat.app.company

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.FirstSetup
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.company.CompanyReportViewModel
import com.bamnat.app.ui.company.TalkHolder
import com.bamnat.app.ui.company.companyWayText
import com.bamnat.app.ui.company.proposalTrial
import com.bamnat.app.ui.pay.payFromCompany
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HourlySource
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Collections

/**
 * [FIN130-01]~[FIN130-08] 1.30 마무리: where the bundles meet. 이 규칙 쓰기 with the plaza's pay way goes through the 기본급 as
 * every way into the pay does (BA's PendingWay); a coworkers' setup on a pay already set up waits for it too; the 통상시급 hours
 * say whether they are the 광장's or the user's; a plaza opened from an added calendar works on that calendar (its trial, its
 * '내 급여도', its new items); the company's way is read from the page kept on the phone; and the page kept is read again once
 * this phone changed something on it. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class Fin130Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private lateinit var before: CompanyApi
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val today = Today.date()
    private val month = Today.month()
    private var pageText: String = ""

    @Before fun start() = runBlocking {
        before = c.companyApi
        c.companyApi = Api(calls) { pageText }
        c.calendars.added().forEach { c.calendars.delete(it.id) }
        clearMain()
        calls.clear()
    }

    @After fun end() = runBlocking {
        c.calendars.added().forEach { c.calendars.delete(it.id) }
        clearMain()
        c.companyApi = before
    }

    private suspend fun clearMain() {
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
        c.companyStore.setDraft(null)
        c.companyStore.pendingPay = null
        c.payWays.use(null)
        c.payWays.decided = false
        c.payWays.askBase(false)
        c.payStore.save(null)
        c.companySearch.forgetPage(COMPANY)
        val ways = c.companyOf(PersonalCalendar.MAIN).pendingWays
        ways.cancel()
        ways.forgetDropped()
        ways.hoursAsked()
    }

    /** A company's file with its pay part (180 hours a month, 야간 50%), as the server gives it: no amount in it. */
    private suspend fun companyFile(): String {
        c.scheduleRepository.ensureDefaults()
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 3_100_000))),
            rules = CompanyRules(nightPremium = 0.5), monthlyHours = 180), keepUndo = false)
        val text = c.companyShare.exportForServer().toString()
        c.payStore.save(null)
        assertTrue(JSONObject(text).has("pay"))
        pageText = text
        return text
    }

    /** The user's own pay: 209 hours from the payslip, 야간 30%. */
    private fun mine(base: Long = 2_500_000) = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, base))),
        rules = CompanyRules(nightPremium = 0.3), monthlyHours = 209, hoursSource = HourlySource.SLIP)

    private fun setup(text: String) = SharedSetup("s1", true, 3, 3, 0, 3, "생산팀", 1, text, emptyList())
    private fun page(text: String) = CompanyPage(COMPANY, NAME, emptyList(), listOf(setup(text)), member = true)

    private suspend fun waitFor(what: String, check: () -> Boolean) {
        runCatching { withTimeout(5_000) { while (!check()) delay(20) } }
        assertTrue(what, check())
    }

    /** Runs what waits on the main thread (a view model's work) until [check] holds. */
    private fun mainUntil(what: String, check: () -> Boolean) {
        repeat(250) {
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
            runBlocking { delay(20) }
        }
        assertTrue(what, check())
    }

    // ---- [FIN130-01] 이 규칙 쓰기 with the plaza's pay way ----

    @Test fun theSameRulesPayWaitsForTheBaseOnAPayWithAmounts() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        val had = c.payStore.profile.value
        c.companySearch.keepPage(page(text))
        val main = c.companyOf(PersonalCalendar.MAIN)
        val r = SendGuard(c).useSame(SendGuard.Same(page(text), setup(text), withPay = true))
        assertNotNull(r)
        assertTrue("the pay screen is where it goes in", r!!.rules)
        assertFalse(r.pendingPay)
        // Nothing of the company's pay before the 기본급, nor the way marked in use.
        assertEquals(had, c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        val w = main.pendingWays.pending.value
        assertEquals(PendingWay("c1", NAME, "생산팀", "s1", text, PendingWay.Source.APPLY), w)
        assertTrue(c.payWays.askBase.value)
        // The rest as before: the rule in use, 맞아요, joined.
        assertEquals("s1", c.companyStore.applied.value?.setup)
        assertEquals(COMPANY, c.companyStore.member.value?.first)
        assertTrue(calls.contains("vote:s1:true"))
        // [FIN130-06] The page kept from before this 맞아요 is read again next time.
        assertNull(c.companySearch.page(COMPANY))
        // The 기본급 puts it all in at once.
        assertTrue(main.commit(c, w!!, 2_600_000, null, month))
        val p = c.payStore.profile.value!!
        assertEquals(0.5, p.rules.nightPremium!!, 0.0)
        assertEquals("s1", c.payWays.way.value?.setup)
        // [FIN130-04] The company's hours went in as the 광장's.
        assertEquals(180, p.monthlyHours)
        assertEquals(HourlySource.PLAZA, p.hoursSource)
    }

    @Test fun theSameRulesPayWithNoPayYetGoesToTheAmountsScreen() = runBlocking {
        val text = companyFile()
        val r = SendGuard(c).useSame(SendGuard.Same(page(text), setup(text), withPay = true))
        assertTrue(r!!.pendingPay)
        assertEquals(text, c.companyStore.pendingPay)
        assertTrue("the amounts screen does not ask 쓸까요 again", c.payWays.decided)
        // In use only once the amounts are saved (PendingWays.keptWith).
        assertNull(c.payWays.way.value)
        assertNull(c.payStore.profile.value)
        assertNull(c.companyOf(PersonalCalendar.MAIN).pendingWays.pending.value)
    }

    @Test fun theSameRuleWithoutPayTouchesNoPay() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        val had = c.payStore.profile.value
        assertNull(SendGuard(c).useSame(SendGuard.Same(page(text), setup(text), withPay = false)))
        assertEquals(had, c.payStore.profile.value)
        assertNull(c.companyOf(PersonalCalendar.MAIN).pendingWays.pending.value)
        assertFalse(c.payWays.askBase.value)
    }

    // ---- [FIN130-04] where the 통상시급 hours came from ----

    @Test fun hoursOtherThanTheCompanysAreTheUsersOwn() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        val main = c.companyOf(PersonalCalendar.MAIN)
        val w = PendingWay(COMPANY, NAME, "생산팀", "s1", text, PendingWay.Source.APPLY)
        main.pendingWays.offer(w)
        assertTrue(main.commit(c, w, 2_600_000, 209, month))
        assertEquals(209, c.payStore.profile.value?.monthlyHours)
        assertEquals(HourlySource.TYPED, c.payStore.profile.value?.hoursSource)
        // The company's own picked again: the 광장's.
        main.pendingWays.offer(w)
        assertTrue(main.commit(c, w, 2_600_000, 180, month))
        assertEquals(HourlySource.PLAZA, c.payStore.profile.value?.hoursSource)
    }

    @Test fun takingOneValueKeepsWhereTheHoursCameFrom() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        assertTrue(c.companyShare.takeOne(text, "rate:night"))
        val p = c.payStore.profile.value!!
        assertEquals(0.5, p.rules.nightPremium!!, 0.0)
        assertEquals(209, p.monthlyHours)
        assertEquals(HourlySource.SLIP, p.hoursSource)
        assertTrue(c.companyShare.takeOne(text, "place:hours"))
        assertEquals(180, c.payStore.profile.value?.monthlyHours)
        assertEquals(HourlySource.PLAZA, c.payStore.profile.value?.hoursSource)
    }

    @Test fun aCompanyFilesHoursAreThePlazas() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        c.companyShare.applyRules(text)
        assertEquals(180, c.payStore.profile.value?.monthlyHours)
        assertEquals(HourlySource.PLAZA, c.payStore.profile.value?.hoursSource)
        // A file that says no hours keeps the user's and where they came from.
        c.payStore.save(mine(), keepUndo = false)
        c.companyShare.applyRules(JSONObject(text).apply { getJSONObject("pay").remove("monthlyHours") }.toString())
        assertEquals(209, c.payStore.profile.value?.monthlyHours)
        assertEquals(HourlySource.SLIP, c.payStore.profile.value?.hoursSource)
    }

    // ---- [FIN130-02][FIN130-03][FIN130-08] an added calendar's plaza is that calendar's ----

    @Test fun anAddedCalendarsProposalTrialAndPayAreItsOwn() = runBlocking {
        val text = companyFile()
        val id = c.calendars.begin("둘째").also { c.calendars.finishSetup(it, WorkStyle.SHIFT) }
        c.calendars.repository(id).ensureDefaults()
        val other = c.companyOf(id)
        other.pay.save(mine(), keepUndo = false)
        c.companySearch.keepPage(page(text))
        val proposal = JSONObject(text).apply { getJSONObject("pay").getJSONObject("rules").put("night", 0.6) }.toString()
        // 내 달력 has no pay; the added calendar's plaza compares the added calendar's month.
        val t = proposalTrial(c, other, COMPANY, "s1", proposal, month)
        assertFalse(t.note.orEmpty(), t.note.orEmpty().contains("급여 설정이 없어"))
        val m = proposalTrial(c, c.companyOf(PersonalCalendar.MAIN), COMPANY, "s1", proposal, month)
        assertTrue(m.note.orEmpty().contains("급여 설정이 없어"))
        // [FIN130-05] The version came from the page kept on the phone.
        assertTrue(calls.none { it.startsWith("company:") })

        // [FIN130-02] 내 급여도 이 값으로 on that calendar's share changes that calendar's pay only.
        c.payStore.save(mine(), keepUndo = false)
        assertTrue(other.share.takeEdited(text, proposal) > 0)
        assertEquals(0.6, other.pay.profile.value!!.rules.nightPremium!!, 0.0)
        assertEquals(0.3, c.payStore.profile.value!!.rules.nightPremium!!, 0.0)

        // [FIN130-08] A new item starts as the user's own only where that calendar's pay is a company's.
        assertFalse(payFromCompany(c, id))
        assertFalse(payFromCompany(c, PersonalCalendar.MAIN))
        other.payWays.use(PayWay(COMPANY, NAME, "생산팀", "s1"))
        assertTrue(payFromCompany(c, id))
        assertFalse(payFromCompany(c, PersonalCalendar.MAIN))
    }

    // ---- [FIN130-05] the company's way from the page kept on the phone ----

    @Test fun theCompanysWayIsReadOnceAndKept() = runBlocking {
        val text = companyFile()
        val way = PayWay(COMPANY, NAME, "생산팀", "s1")
        assertEquals(text, companyWayText(c, way)?.text)
        assertEquals(listOf("company:c1"), calls.filter { it.startsWith("company:") })
        assertEquals(180, companyWayText(c, way)?.hours)
        assertEquals("the second read is the kept page", 1, calls.count { it.startsWith("company:") })
        // A kept page whose pay this phone was not shown is read again.
        c.companySearch.keepPage(page(text).copy(setups = listOf(setup(text).copy(payHidden = true))))
        assertEquals(text, companyWayText(c, way)?.text)
        assertEquals(2, calls.count { it.startsWith("company:") })
    }

    // ---- [FIN130-06] the page kept is read again after this phone's own change ----

    @Test fun aProposalSentForgetsTheKeptPage() = runBlocking {
        val text = companyFile()
        c.companySearch.keepPage(page(text))
        val holder = TalkHolder(c, CoroutineScope(Dispatchers.Unconfined))
        holder.show(COMPANY, "s1", NAME)
        var done: String? = "waiting"
        holder.send("야간이 60%예요", kind = "proposal", change = listOf(ChangeLine("rate", "야간 50%", "야간 60%", "")), setupText = text) { done = it }
        waitFor("sent") { done == null }
        assertTrue(calls.contains("post:proposal"))
        assertNull(c.companySearch.page(COMPANY))
    }

    @Test fun aReportSentForgetsTheKeptPage() {
        runBlocking { c.scheduleRepository.ensureDefaults() }
        c.companySearch.keepPage(page("{}"))
        val vm = CompanyReportViewModel(c)
        vm.send(NAME, "생산팀", verified = false, note = "", checked = true)
        mainUntil("sent") { vm.sending.value is CompanyReportViewModel.Sending.Sent }
        assertNull(c.companySearch.page(COMPANY))
    }

    // ---- [FIN130-07] a coworkers' setup with its pay on a pay already set up ----

    @Test fun aSetupsPayOnAPayAlreadySetUpWaitsForTheBase() = runBlocking {
        val text = companyFile()
        c.payStore.save(mine(), keepUndo = false)
        val had = c.payStore.profile.value
        val main = c.companyOf(PersonalCalendar.MAIN)
        val draft = SetupDraft(style = WorkStyle.SHIFT, source = SetupSource(SetupSource.Kind.SERVER, text, id = COMPANY, setupId = "s1",
            name = NAME, dept = "생산팀", version = 1, adopted = true))
        FirstSetup.keepCompany(main, draft)
        assertEquals(had, c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        assertNull(c.companyStore.pendingPay)
        assertEquals(PendingWay(COMPANY, NAME, "생산팀", "s1", text, PendingWay.Source.FIRST_SETUP), main.pendingWays.pending.value)
        assertTrue(c.payWays.askBase.value)
        assertEquals("s1", c.companyStore.applied.value?.setup)
        // Closed without its 기본급: nothing of it went in.
        main.cancelPending(c)
        assertEquals(had, c.payStore.profile.value)

        // With no pay yet, as before: the amounts screen makes the pay from it, the way already chosen.
        clearMain()
        FirstSetup.keepCompany(main, draft)
        assertEquals(text, c.companyStore.pendingPay)
        assertEquals("s1", c.payWays.way.value?.setup)
        assertTrue(c.payWays.decided)
        assertNull(main.pendingWays.pending.value)
    }

    private companion object {
        const val COMPANY = "c1"
        const val NAME = "(주)예시정밀"
    }

    private class Api(private val calls: MutableList<String>, private val text: () -> String) : CompanyApi {
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage {
            calls += "company:$id"
            return CompanyPage(id, NAME, emptyList(), listOf(SharedSetup("s1", true, 3, 3, 0, 3, "생산팀", 1, text(), emptyList())), member = true)
        }
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus {
            calls += "report:$company"
            return ReportStatus("r1", "pending", false, COMPANY, company, dept, "s2", verified, 1, 1, 3, false, Instant.parse("2026-10-27T00:00:00Z"),
                null, null, null, null, 0)
        }
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult {
            calls += "vote:$setup:$ok"
            return VoteResult(3, 3, 0, 3, true)
        }
        override suspend fun use(device: String, company: String, setup: String, on: Boolean, cal: String): Int {
            calls += "use:$company:$setup:$on:$cal"; return 1
        }
        override suspend fun post(device: String, company: String, setup: String, body: String, parent: String?, kind: String,
            change: List<ChangeLine>, proposal: JSONObject?): String {
            calls += "post:$kind"
            return "p1"
        }
    }
}
