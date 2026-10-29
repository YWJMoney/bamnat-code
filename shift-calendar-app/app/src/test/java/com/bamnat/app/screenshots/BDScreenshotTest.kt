package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyDraft
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.ReportStatus
import com.bamnat.app.company.RuleVersion
import com.bamnat.app.company.SentReport
import com.bamnat.app.company.SetupChanges
import com.bamnat.app.company.SharedSetup
import com.bamnat.app.company.VoteResult
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyReportScreen
import com.bamnat.app.ui.company.CompanyReportViewModel
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PLZD-01]~[PLZD-08] 1.30 묶음 BD's screenshots, numbers 1360~1379 (사용자 2026-10-27: "광장에 이전 버젼과 동일한 내용이면 다시
 * 올리지 못하게 … 이미 해당 규칙이 존재해요, 규칙 적용해 드릴까요? … 2교대인데 3교대를 넣어서 … 비정상 사용을 금지" and "같은 회사의
 * 여러개의 정보들이 있으면 도대체 뭐가 맞는지 보기도 힘들고 설정도 어렵다"): 내 설정 나누기 meeting the same rule (used in one tap,
 * nothing sent), another shape (only under another department's name), other values (달라진 점만 제안하기 first) and a version sent
 * today (바꿔서 보내기); a held version's panel; the company page by department with the others folded and what they change;
 * 내가 쓰는 판; the history in marked words; and a change saved that the plaza already has asking nothing. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BDScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val NAME = "(주)예시정밀"
    }

    /** The made-up server with the company page this test sets, and what the phone did on it. */
    private inner class Plaza(private val base: FakeCompanyApi) : CompanyApi by base {
        var setups: List<SharedSetup>? = null
        var held = false
        var changes: SetupChanges? = null
        var historyChanges: SetupChanges? = null
        val used = mutableListOf<String>()
        val voted = mutableListOf<Pair<String, Boolean>>()

        override suspend fun company(id: String): CompanyPage {
            val p = base.company(id)
            return setups?.let { p.copy(setups = it) } ?: p.copy(setups = p.setups.map { if (it.id == "s3") it.copy(changes = changes) else it })
        }

        override suspend fun use(device: String, company: String, setup: String, on: Boolean): Int = use(device, company, setup, on, "main")
        override suspend fun use(device: String, company: String, setup: String, on: Boolean, cal: String): Int {
            if (on) used += setup
            return 1
        }

        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult {
            voted += setup to ok
            return base.vote(device, company, setup, ok, verified, why)
        }

        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus {
            val s = base.report(device, company, dept, verified, note, setup, ask)
            return if (!held) s else s.copy(reasonCode = "shape", reason = "근무 모양이 이 부서 대표 판과 달라 개발자가 확인한 뒤 보여요. 다른 부서라면 부서 이름을 넣어 다시 보내 주세요")
        }

        // [J133-93] The form now starts with the calendar's job, so a send names its job plaza; it is the same send here.
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, job: String,
            region: String, topic: Boolean): ReportStatus = report(device, company, dept, verified, note, setup, false)

        override suspend fun history(company: String, setup: String): List<RuleVersion> =
            base.history(company, setup).mapIndexed { i, v -> if (i == 0) v.copy(changes = historyChanges) else v }
    }

    private lateinit var plaza: Plaza

    /** The server on, this calendar on a 2교대 order of six days with no pay; its file as it would go (no pay). */
    private fun on(): String {
        runBlocking {
            val repo = c.scheduleRepository
            repo.ensureDefaults()
            val t = repo.shiftTypes.first()
            val day = t.first { it.name == "주간" }.id
            val night = t.first { it.name == "야간" }.id
            repo.setFirstPattern(WorkPattern.Rotation(today.minusDays(2), listOf(day, day, night, night, null, null)))
            c.payStore.save(null, keepUndo = false)
        }
        companyOn()
        plaza = Plaza(company)
        c.companyApi = plaza
        return runBlocking { c.companyShare.exportForServer(cycleOnly = true).toString() }
    }

    private fun shared(id: String, text: String, dept: String = "생산팀", adopted: Boolean = true) =
        SharedSetup(id, adopted, 5, 3, 0, 3, dept, if (adopted) 2 else null, text, emptyList(), trust = 75, agree = 100, lead = adopted)

    /** [mine] with the coworkers' pay way: the plaza's version of the same rule. */
    private fun withPay(mine: String) = JSONObject(mine).put("pay", JSONObject(company.file).get("pay")).toString()

    /** [mine] made 3교대 over nine days with its own 오후: another 사업장's order. */
    private fun threeShifts(mine: String): String {
        val o = JSONObject(mine)
        o.getJSONObject("rotation").put("steps", JSONArray(listOf("주간", "주간", "오후", "오후", "야간", "야간", null, null, null)))
        return o.toString()
    }

    private fun Screen.report(): CompanyReportViewModel {
        c.companyStore.setDraft(CompanyDraft(NAME, "생산팀"))
        val vm = CompanyReportViewModel(c)
        content { CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}, onOpenCompany = {}) }
        settle()
        return vm
    }

    private fun Screen.send() {
        tapText("보내기", exact = true)
        settle()
    }

    // ---- [PLZD-01] 같은 규칙: 이미 있어요, 적용해 드릴까요? ----
    @Test fun theSameRuleIsOfferedNotSent() = shoot("1360_bd_same_ask", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", withPay(mine)))
        report()
        send()
        assertTrue(appears("이미 같은 방식이 있어요! 🎉", exact = true))
        // [FIN130-01] The pay way goes in with the 기본급 on the pay screen, never before it.
        assertTrue(appears("이 규칙의 급여 계산 방식은 급여 화면에서 기본급을 넣으면 같이 들어가요. 그 전에는 내 급여가 그대로예요.", exact = true))
        assertTrue(appears("맞았다고 알리고 이 방식 쓰기", exact = true))
        assertTrue(company.reports.isEmpty())
    }

    @Test fun theSameRuleIsUsedInOneTap() = shoot("1361_bd_same_used", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", withPay(mine)))
        report()
        send()
        tapText("맞았다고 알리고 이 방식 쓰기", exact = true)
        reveal("이 규칙을 쓰고 있어요", exact = true)
        // [FIN130-01] No pay yet: the pay screen makes it from the way (its amounts), the way in use once that is saved.
        assertTrue(appears("급여 계산 방식은 급여 화면에서 기본급을 넣으면 들어가요", exact = false))
        // Nothing new went up: the calendar uses the plaza's rule and it counted one more 맞아요.
        assertTrue(company.reports.isEmpty())
        assertEquals("s1", c.companyStore.applied.value?.setup)
        assertEquals(true, c.companyStore.applied.value?.vote)
        assertEquals(listOf("s1"), plaza.used)
        assertEquals(listOf("s1" to true), plaza.voted)
        assertEquals("c1", c.companyStore.member.value?.first)
        assertNull(c.companyStore.draft.value)
        assertNull(c.payWays.way.value)
        assertNotNull(c.companyStore.pendingPay)
        assertTrue(c.payWays.decided)
    }

    @Test fun theSameRuleAskHugeText() = shoot("1362_bd_same_huge_text", SMALL_PHONE, fontScale = 2f) {
        val mine = on()
        plaza.setups = listOf(shared("s1", mine))
        report()
        send()
        reveal("맞았다고 알리고 이 방식 쓰기", exact = true)
        // My own work, no pay on either: nothing more to take.
        assertTrue(gone("급여 계산 방식도 같이 넣어요", exact = false))
    }

    // ---- [PLZD-02] 모양이 다름: 부서 이름 없이는 못 올려요 ----
    @Test fun anotherShapeAsksForAnotherName() = shoot("1363_bd_shape_ask", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", threeShifts(mine)))
        report()
        send()
        assertTrue(appears("근무 모양이 달라요", exact = true))
        assertTrue(appears("대표 판은 3교대 · 9일 순서인데, 내 달력은 2교대 · 6일 순서예요", exact = false))
        typeInto("부서·공장 이름", "생산 팀")
        assertTrue(appears("대표 판과 다른 이름을 적어 주세요", exact = true))
        assertTrue(company.reports.isEmpty())
    }

    @Test fun anotherShapeGoesUnderItsOwnName() = shoot("1364_bd_shape_own_name", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", threeShifts(mine)))
        report()
        send()
        typeInto("부서·공장 이름", "온산공장")
        tapText("이 이름으로 보내기", exact = true)
        reveal("보냈어요! 고마워요", exact = true)
        assertEquals(1, company.reports.size)
        assertEquals("온산공장", c.companyStore.sent.value.first().dept)
    }

    @Test fun anotherShapeDark() = shoot("1365_bd_shape_dark", FOLDED_NIGHT) {
        val mine = on()
        plaza.setups = listOf(shared("s1", threeShifts(mine)))
        report()
        send()
        reveal("대표 판 보기", exact = true)
    }

    // ---- [PLZD-03] 다른 점: 달라진 점만 제안하기 ----
    @Test fun otherValuesAreProposedFirst() = shoot("1366_bd_differ_ask", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", SetupEdit.of(withPay(mine))!!.shiftTime("주간", "06:30", "18:30", 60).text))
        report()
        send()
        assertTrue(appears("대표 판과 다른 점 1가지", exact = true))
        assertTrue(appears("달라진 점만 제안하기", exact = true))
        assertTrue(appears("새 판으로 보낼게요", exact = true))
    }

    @Test fun theDifferenceGoesAsAProposal() = shoot("1367_bd_differ_proposed", PHONE_393) {
        val mine = on()
        plaza.setups = listOf(shared("s1", SetupEdit.of(withPay(mine))!!.shiftTime("주간", "06:30", "18:30", 60).text))
        report()
        typeInto("동료에게 한마디 (선택)", "10월부터 주간이 바뀌었어요")
        send()
        tapText("달라진 점만 제안하기", exact = true)
        tapText("제안 보내기", exact = true)
        reveal("제안을 보냈어요", exact = true)
        val (body, lines, setup) = company.posts.last()
        assertEquals("10월부터 주간이 바뀌었어요", body)
        assertEquals(1, lines.size)
        // The 대표 as I have it: its pay stays, my 주간 goes in.
        assertNotNull(setup)
        assertTrue(setup!!.has("pay"))
        assertTrue(company.reports.isEmpty())
    }

    @Test fun orAsANewVersion() = shoot("1368_bd_differ_new_version", PHONE) {
        val mine = on()
        plaza.setups = listOf(shared("s1", SetupEdit.of(withPay(mine))!!.shiftTime("주간", "06:30", "18:30", 60).text))
        report()
        send()
        tapText("새 판으로 보낼게요", exact = true)
        reveal("보냈어요! 고마워요", exact = true)
        assertEquals(1, company.reports.size)
    }

    // ---- [PLZD-05] 하루 한 판 ----
    @Test fun aVersionSentTodayIsReplaced() = shoot("1369_bd_resent", PHONE_393) {
        on()
        c.companyStore.addSent(SentReport("r0", "가나다전자", "A팀", false, Today.instant().minusSeconds(3600)))
        c.companyStore.setDraft(CompanyDraft("가나다전자", "B팀"))
        val vm = CompanyReportViewModel(c)
        content { CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        settle()
        send()
        assertTrue(appears("오늘 보낸 판이 있어요", exact = true))
        assertTrue(appears("먼저 보낸 판은 이것으로 바뀌어요", exact = false))
        assertTrue(company.reports.isEmpty())
        tapText("바꿔서 보내기", exact = true)
        reveal("보냈어요! 고마워요", exact = true)
        assertEquals(1, company.reports.size)
    }

    // ---- [PLZD-04] 서버가 붙잡은 판 ----
    @Test fun aHeldVersionSaysTheDeveloperLooksFirst() = shoot("1370_bd_held_sent", PHONE_393) {
        on()
        plaza.held = true
        c.companyStore.setDraft(CompanyDraft("가나다전자", ""))
        val vm = CompanyReportViewModel(c)
        content { CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        settle()
        send()
        reveal("보냈어요. 개발자가 먼저 볼게요", exact = true)
        assertTrue(appears("모양이 달라 확인 중", exact = true))
    }

    // ---- [PLZD-06][PLZD-07][PLZD-08] 회사 화면: 부서마다 대표 판 하나 ----
    private fun Screen.detail(): CompanyDetailViewModel {
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        return vm
    }

    private val moved = SetupChanges("주간 08:00~20:00 → 08:00~19:00", listOf(SetupChanges.Fact("근무·주간", "08:00~20:00 휴게 60분",
        "08:00~19:00 휴게 60분", listOf(SetupChanges.Token("08:00~", "same"), SetupChanges.Token("20:00", "del"),
            SetupChanges.Token("19:00", "add"), SetupChanges.Token(" 휴게 60분", "same")))))

    @Test fun eachDepartmentShowsOneLead() = shoot("1371_bd_company_folded", FOLDED) {
        on()
        company.twins = true
        detail()
        assertTrue(appears("대표 판 v2", exact = true))
        reveal("같은 부서 다른 판 2개 보기", exact = true)
        // The others are folded: no second 생산팀 card until asked.
        assertTrue(gone("대표 판과 비교", exact = true))
    }

    @Test fun theFoldedOnesSayWhatTheyChange() = shoot("1372_bd_company_changes", FOLDED_NIGHT) {
        on()
        company.twins = true
        plaza.changes = moved
        detail()
        tapText("같은 부서 다른 판 2개 보기", exact = true)
        reveal("주간 08:00~20:00 → 08:00~19:00", exact = true)
        assertTrue(appears("대표 판과 비교", exact = true))
    }

    @Test fun theVersionInUseIsSaidFirst() = shoot("1373_bd_my_version", PHONE_393) {
        on()
        company.twins = true
        c.companyStore.setApplied(AppliedSetup("c1", NAME, "생산팀", "s3", null, false, Today.instant()))
        detail()
        assertTrue(appears("내가 쓰는 판 · 생산팀", exact = true))
        assertTrue(appears("이 부서 대표 판은 다른 판이에요", exact = false))
        // The one in use is open; only the other one is folded.
        reveal("같은 부서 다른 판 1개 보기", exact = true)
    }

    @Test fun theHistorySaysTheWordsThatChanged() = shoot("1374_bd_history_tokens", PHONE_393) {
        on()
        plaza.historyChanges = moved
        detail()
        tapText("바뀐 기록", exact = true)
        reveal("주간 08:00~20:00 → 08:00~19:00", exact = true)
    }

    @Test fun companyFoldedHugeText() = shoot("1375_bd_company_huge_text", SMALL_PHONE, fontScale = 2f) {
        on()
        company.twins = true
        plaza.changes = moved
        detail()
        tapText("같은 부서 다른 판 2개 보기", exact = true)
        reveal("대표 판과 비교", exact = true)
    }

    // ---- [PLZD-03] 저절로 나누기도 같은 규칙·다른 모양은 묻지 않아요 ----
    @Test fun aChangeThePlazaHasAsksNothing() = shoot("1376_bd_auto_share_same", PHONE) {
        val mine = on()
        c.companyStore.setApplied(AppliedSetup("c1", NAME, "생산팀", "s1", 2, true, Today.instant()))
        c.companyStore.setAutoShare(null)
        c.companyStore.autoBase = "{}"
        plaza.setups = listOf(shared("s1", withPay(mine)))
        runBlocking { c.autoShare.check() }
        assertNull(c.autoShare.ask.value)
        assertEquals(runBlocking { c.companyShare.exportForServer().toString() }, c.companyStore.autoBase)
        // Another shape is not asked either; other values are.
        c.companyStore.autoBase = "{}"
        plaza.setups = listOf(shared("s1", threeShifts(mine)))
        c.companySearch.forgetPage("c1")
        runBlocking { c.autoShare.check() }
        assertNull(c.autoShare.ask.value)
        c.companyStore.autoBase = "{}"
        plaza.setups = listOf(shared("s1", SetupEdit.of(mine)!!.shiftTime("주간", "06:30", "18:30", 60).text))
        c.companySearch.forgetPage("c1")
        runBlocking { c.autoShare.check() }
        assertNotNull(c.autoShare.ask.value)
        c.autoShare.decline()
    }
}
