package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.time.Today
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * [SPAT-07][SPAT-08][PLAZA-14][PLAZA-15][PLAZA-16] 1.27 묶음 AH (사용자 2026-10-25) from the app's own files to the real
 * company server (server/company-worker on Node with SQLite): a cycle picked from a search is asked about once and becomes
 * the company's 대표 주기; the same order is one more 맞아요; another order asks for the 사업장 and leads there; 맞아요·달라요
 * come back with where it differs; a deleted cycle lets the company go; the pay way is found and used. Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaAHAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private val today = LocalDate.of(2026, 10, 26)
    private lateinit var before: CompanyApi

    @Before fun start() {
        before = c.companyApi
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
        c.companyStore.setPublicUse(null)
        c.companyStore.setDraft(null)
        c.companyStore.pendingPay = null
        c.leadShare.close()
    }

    @After fun end() {
        c.companyApi = before
        c.leadShare.close()
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
        c.companyStore.setPublicUse(null)
        c.companyStore.setDraft(null)
        c.companyStore.pendingPay = null
        runBlocking { c.payStore.save(null) }
    }

    /** A public row of 4조 2교대 kind and another whose order is not the same, both as company files. */
    private fun twoOrders(): Pair<String, String> {
        val rows = PublicCycles.all.filter { it.codes.isNotEmpty() && !it.roster && !it.weekly }
        val a = rows.first()
        val b = rows.first { r -> !com.bamnat.core.plaza.CycleMatch.same(order(a, a.codes), order(r, r.codes)) && r.codes.length != a.codes.length }
        return com.bamnat.app.company.PublicCycleFiles.text(a, a.codes) to com.bamnat.app.company.PublicCycleFiles.text(b, b.codes)
    }

    private fun order(cycle: PublicCycle, codes: String) = LeadShare.order(JSONObject(com.bamnat.app.company.PublicCycleFiles.text(cycle, codes)))!!

    /** The same file begun on another day of its order (another 조). */
    private fun rotated(file: String): String {
        val o = JSONObject(file)
        val steps = o.getJSONObject("rotation").getJSONArray("steps")
        val list = (0 until steps.length()).map { steps.get(it) }
        val turned = list.drop(2) + list.take(2)
        o.getJSONObject("rotation").put("steps", org.json.JSONArray(turned))
        return o.toString()
    }

    private suspend fun step(): LeadShare.Step = withTimeout(15_000) {
        while (c.leadShare.step.value.let { it == null || it is LeadShare.Step.Working }) delay(20)
        c.leadShare.step.value!!
    }

    @Test fun aPickedCycleBecomesTheLeadTheSameOrderAgreesAnotherAsksTheSite() = runBlocking {
        val (fileA, fileB) = twoOrders()
        withServer { base ->
            c.companyApi = CompanyClient(base, device = { c.companyStore.device })
            // Asked once, and yes makes it the company's 대표 주기 when it has none.
            c.leadShare.offer(LeadShare.Pick("대표상사", fileA))
            assertTrue(c.leadShare.step.value is LeadShare.Step.Ask)
            c.leadShare.yes()
            val made = step() as LeadShare.Step.Made
            assertEquals("대표상사", made.name)
            assertEquals(true, c.companyStore.autoShare.value)
            assertEquals(made.companyId, c.companyStore.applied.value?.companyId)
            assertNotNull("the public row's invitation stops", c.companyStore.sent.value.firstOrNull())

            // Another phone typing the name sees it under the field with who stands behind it (AH-I4).
            val other = CompanyClient(base, device = { "ah-app-device-000002" })
            val hit = other.search("대표상").single()
            assertTrue(hit.users >= 1)
            val lead = assertNotNullOf(hit.lead)
            assertEquals("", lead.dept)
            assertFalse(lead.doubted)
            assertTrue(lead.steps!!.isNotEmpty())
            // The 초성 search lists it the same way.
            assertEquals(hit.id, other.search("ㄷㅍㅅㅅ").single().id)

            // The same order begun on another day is not a second version: one 맞아요 (AH-I2, AH-D5).
            c.leadShare.close()
            assertEquals(LeadShare.Step.Agreed::class, c.leadShare.decide(LeadShare.Pick("대표상사", rotated(fileA)), null)::class)
            val page = other.company(hit.id)
            assertEquals(1, page.setups.size)
            assertTrue(page.setups.single().lead)
            assertTrue(page.setups.single().opinion.ok >= 1)

            // Another order: where the user works first, then it leads there as '<회사> · <사업장>'.
            val ask = c.leadShare.decide(LeadShare.Pick("대표상사", fileB), null) as LeadShare.Step.NeedSite
            assertEquals("대표상사", ask.name)
            assertTrue(ask.lead.isNotEmpty())
            val site = c.leadShare.decide(LeadShare.Pick("대표상사", fileB), "여수공장") as LeadShare.Step.Made
            assertEquals("대표상사 · 여수공장", site.name)
            val after = other.company(hit.id)
            assertEquals(setOf("", "여수공장"), after.setups.filter { it.lead }.map { it.dept }.toSet())
            assertEquals(2, other.search("대표상사").single().sites)
        }
    }

    @Test fun noKeepsItOnThisCalendarAndAskingTwiceIsNot() = runBlocking {
        val (fileA, _) = twoOrders()
        withServer { base ->
            c.companyApi = CompanyClient(base, device = { c.companyStore.device })
            c.companyStore.setPublicUse(PublicUse("x", "거절상사", "4-2", "DDNNOO", false, Today.millis()))
            c.leadShare.offer(LeadShare.Pick("거절상사", fileA))
            c.leadShare.no()
            assertNull(c.leadShare.step.value)
            assertEquals(false, c.companyStore.autoShare.value)
            assertTrue("the share card does not ask the same again", c.companyStore.publicUse.value!!.inviteNever)
            assertTrue(c.companyApi.search("거절상사").isEmpty())
            // The plaza's own one-time question was answered with it (AH-D6).
            assertFalse(c.autoShare.saved())
        }
    }

    @Test fun opinionsComeBackWithWhereAndOneAnswerPerPhone() = runBlocking {
        val (fileA, _) = twoOrders()
        withServer { base ->
            val first = CompanyClient(base, device = { "ah-app-device-000010" })
            val sent = first.report("ah-app-device-000010", "의견상사", "", LeadShare.serverSetup(fileA), "main", auto = false)
            val voter = "ah-app-device-000011"
            val b = CompanyClient(base, device = { voter })
            b.vote(voter, sent.companyId, sent.setup, ok = false, why = "order")
            var s = b.company(sent.companyId).setups.single()
            assertEquals(1, s.opinion.bad)
            assertEquals(1, s.opinion.why["order"])
            assertEquals("order", s.opinion.mine)
            // Changed, not added: still one answer from this phone.
            b.vote(voter, sent.companyId, sent.setup, ok = true)
            s = b.company(sent.companyId).setups.single()
            assertEquals(0, s.opinion.bad)
            assertEquals("ok", s.opinion.mine)
            assertEquals("맞아요 ${s.opinion.ok} · 달라요 0", com.bamnat.app.ui.company.opinionLine(s.opinion))
        }
    }

    @Test fun aDeletedCycleLetsTheCompanyGoAndAnAddedCalendarTakesItsOwn() = runBlocking {
        val (fileA, _) = twoOrders()
        withServer { base ->
            c.companyApi = CompanyClient(base, device = { c.companyStore.device })
            c.companyStore.setAutoShare(true)
            c.leadShare.offer(LeadShare.Pick("삭제상사", fileA))
            val made = step() as LeadShare.Step.Made
            assertNotNull(c.companyStore.applied.value)
            assertEquals(made.companyId, c.companyStore.member.value?.first)
            c.companyStore.setPublicUse(PublicUse("x", "삭제상사", "4-2", "DDNNOO", false, Today.millis()))

            // 패턴 삭제: the main calendar is as the first time (AH-D3).
            c.leadShare.forget(PersonalCalendar.MAIN)
            assertNull(c.companyStore.applied.value)
            assertNull(c.companyStore.member.value)
            assertNull(c.companyStore.publicUse.value)
            assertNull(c.companyStore.draft.value)
            assertNull(c.leadShare.step.value)

            // An added calendar's pick goes into its own schedule; the main calendar's company memory is untouched.
            val row = PublicCycles.all.first { it.codes.isNotEmpty() && !it.roster && !it.weekly }
            val id = c.calendars.begin("둘째").also { c.calendars.finishSetup(it, com.bamnat.core.work.WorkStyle.SHIFT) }
            c.calendars.repository(id).ensureDefaults()
            com.bamnat.app.company.PublicCycleFiles.apply(c, row, row.codes, 0, id)
            assertTrue(c.calendars.repository(id).snapshot.first().patterns.isNotEmpty())
            assertNull("main remembers nothing of it", c.companyStore.publicUse.value)
            c.leadShare.offer(LeadShare.Pick(row.company, com.bamnat.app.company.PublicCycleFiles.text(row, row.codes), id))
            step()
            assertNull("an added calendar's share is not the main one's setup", c.companyStore.applied.value)
            c.leadShare.forget(id)
            assertNull(c.leadShare.step.value)
        }
    }

    @Test fun thePayWayIsFoundSaidOnlyAsThereAndUsedAfterJoining() = runBlocking {
        withServer { base ->
            // One phone shares its cycle and pay way (no amount leaves it).
            c.scheduleRepository.ensureDefaults()
            val t = c.scheduleRepository.snapshot.first().shiftTypes
            c.scheduleRepository.setFirstPattern(WorkPattern.Rotation(today, listOf(t.first { it.name == "주간" }.id, null), today), today)
            c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 3_100_000))),
                rules = CompanyRules(nightPremium = 0.5)), keepUndo = false)
            val share = CompanyShare(c.scheduleRepository, c.payStore, c.settingsRepository, today = { today })
            val first = CompanyClient(base, device = { "ah-app-device-000020" })
            first.report("ah-app-device-000020", "급여상사", "", share.exportForServer(), "main", auto = false)
            c.payStore.save(null)

            c.companyApi = CompanyClient(base, device = { c.companyStore.device })
            val hit = c.companyApi.search("급여상사").single()
            // 결정 D79: before using it, only that there is one.
            assertTrue(hit.lead!!.pay)
            assertTrue(c.companyApi.company(hit.id).setups.single().payHidden)
            val used = PayFind(c).use(hit)
            assertEquals(PayFind.Used.Amounts, used)
            assertEquals(hit.id, c.companyStore.member.value?.first)
            val pending = JSONObject(c.companyStore.pendingPay!!)
            assertTrue(pending.has("pay"))
            assertFalse(pending.toString().contains("3100000"))
            assertEquals("급여상사", c.companyStore.draft.value?.company)

            // None: the easy setup under the name, and saving it asks the plaza's one question.
            c.companyStore.pendingPay = null
            c.companyStore.setDraft(null)
            PayFind(c).none("없는급여상사")
            assertEquals("없는급여상사", c.companyStore.draft.value?.company)
            assertTrue(c.autoShare.saved())
        }
    }

    private fun <T> assertNotNullOf(v: T?): T { assertNotNull(v); return v!! }

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
