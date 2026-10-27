package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * [PAYG-01] 1.30 묶음 BA (사용자 2026-10-27 "'이 급여 방식 쓰기' → 기본급 칸 … 나중에 클릭하면 적용이 된 상태로 페이지를 빠져나간다",
 * "기본급 없이 닫으면 … 창이 닫히면서 바로 적용이 되므로"): a company's pay way chosen changes nothing until it goes in with its
 * 기본급, all at once; closed before that, nothing changed; and one calendar's never touches another's.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PendingWayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private lateinit var before: CompanyApi
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val today = Today.date()

    @Before fun start() = runBlocking {
        before = c.companyApi
        c.companyApi = Uses(calls)
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
        c.payStore.save(null)
        val ways = c.companyOf(PersonalCalendar.MAIN).pendingWays
        ways.cancel()
        ways.forgetDropped()
        ways.hoursAsked()
    }

    /** A company's file with its pay part (180 hours a month), as the server gives it: no amount in it. */
    private suspend fun companyFile(): String {
        c.scheduleRepository.ensureDefaults()
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 3_100_000))),
            rules = CompanyRules(nightPremium = 0.5), monthlyHours = 180), keepUndo = false)
        val text = c.companyShare.exportForServer().toString()
        c.payStore.save(null)
        assertTrue(JSONObject(text).has("pay"))
        assertFalse(text.contains("3100000"))
        return text
    }

    private fun way(text: String, source: PendingWay.Source = PendingWay.Source.FIND) =
        PendingWay("c1", "(주)예시정밀", "생산팀", "s1", text, source)

    private suspend fun waitFor(what: String, check: () -> Boolean) {
        runCatching { withTimeout(5_000) { while (!check()) delay(20) } }
        assertTrue(what, check())
    }

    @Test fun chosenChangesNothingAndClosedLeavesNothing() = runBlocking {
        val main = c.companyOf(PersonalCalendar.MAIN)
        val w = way(companyFile())
        main.pendingWays.offer(w)
        assertEquals(w, main.pendingWays.pending.value)
        // Nothing of the pay, the way in use, the company's name or the server's '쓰고 있어요'.
        assertNull(c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        assertNull(c.companyStore.draft.value)
        assertNull(c.companyStore.pendingPay)
        assertTrue(calls.isEmpty())
        // No 기본급, no going in.
        assertFalse(main.commit(c, w, 0, null, Today.month()))
        assertNull(c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        // Closed: nothing changed, the way kept only for 다시 넣기.
        main.cancelPending(c)
        assertNull(main.pendingWays.pending.value)
        assertEquals(w, main.pendingWays.dropped.value)
        assertNull(c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        assertTrue(calls.isEmpty())
        main.pendingWays.again()
        assertEquals(w, main.pendingWays.pending.value)
        assertNull(main.pendingWays.dropped.value)
    }

    @Test fun aLateCloseOfAnotherWayLeavesTheNewOne() = runBlocking {
        val main = c.companyOf(PersonalCalendar.MAIN)
        val text = companyFile()
        val old = way(text)
        val new = old.copy(setup = "s2")
        main.pendingWays.offer(new)
        main.cancelPending(c, old)
        assertEquals(new, main.pendingWays.pending.value)
    }

    @Test fun committedGoesInAllAtOnceWithTheBase() = runBlocking {
        val main = c.companyOf(PersonalCalendar.MAIN)
        val w = way(companyFile())
        assertEquals(180, w.hours)
        main.pendingWays.offer(w)
        val month = Today.month()
        assertTrue(main.commit(c, w, 2_800_000, null, month))
        val p = assertNotNullOf(c.payStore.profile.value)
        assertEquals(2_800_000L, BaseAsk.known(p, month))
        assertFalse(BaseAsk.needed(p, month))
        assertEquals(0.5, p.rules.nightPremium!!, 0.0)
        assertEquals(PayWay("c1", "(주)예시정밀", "생산팀", "s1"), c.payWays.way.value?.copy(vote = null, why = null))
        assertEquals("(주)예시정밀", c.companyStore.draft.value?.company)
        assertNull(main.pendingWays.pending.value)
        assertNull(main.pendingWays.dropped.value)
        assertNull("the company's own hours are not offered back", main.pendingWays.hoursAsk.value)
        waitFor("the server hears it is used") { calls.contains("use:c1:s1:true:main") }
    }

    @Test fun otherHoursAreOfferedBackAsAnOpinion() = runBlocking {
        val main = c.companyOf(PersonalCalendar.MAIN)
        val w = way(companyFile(), PendingWay.Source.APPLY)
        main.pendingWays.offer(w)
        assertTrue(main.commit(c, w, 2_800_000, 209, Today.month()))
        assertEquals(209, c.payStore.profile.value?.monthlyHours)
        assertEquals(w to 209, main.pendingWays.hoursAsk.value)
        // A cycle put in with the way said '쓰고 있어요' already.
        delay(200)
        assertTrue(calls.none { it.startsWith("use:") })
    }

    @Test fun twoCalendarsNeverTouchEachOther() = runBlocking {
        val text = companyFile()
        val id = c.calendars.begin("둘째").also { c.calendars.finishSetup(it, WorkStyle.SHIFT) }
        c.calendars.repository(id).ensureDefaults()
        val main = c.companyOf(PersonalCalendar.MAIN)
        val other = c.companyOf(id)
        val w = way(text)
        other.pendingWays.offer(w)
        assertNull("the main calendar has none waiting", main.pendingWays.pending.value)
        assertTrue(other.commit(c, w, 3_000_000, null, Today.month()))
        assertEquals(3_000_000L, BaseAsk.known(other.pay.profile.value, Today.month()))
        assertEquals("s1", other.payWays.way.value?.setup)
        // The main calendar's pay and way are as they were.
        assertNull(c.payStore.profile.value)
        assertNull(c.payWays.way.value)
        assertNull(c.companyStore.draft.value)
        // And the other way round: closing on the main calendar leaves the other's line alone.
        other.pendingWays.offer(w.copy(setup = "s2"))
        main.cancelPending(c)
        assertEquals("s2", other.pendingWays.pending.value?.setup)
        waitFor("the server hears it for that calendar") { calls.any { it.startsWith("use:c1:s1:true:") && !it.endsWith(":main") } }
    }

    private fun <T> assertNotNullOf(v: T?): T { assertNotNull(v); return v!! }

    private class Uses(private val calls: MutableList<String>) : CompanyApi {
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
