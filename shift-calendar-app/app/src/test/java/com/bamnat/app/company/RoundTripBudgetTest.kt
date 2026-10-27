package com.bamnat.app.company

import com.bamnat.core.plaza.CompanyMatch
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.ui.company.CompanyBoardViewModel
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.plaza.StylePlaza
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SPD-18] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다 … 아주 중요한 문제"): how many requests each way the
 * user reads the plaza makes, with a server that counts them, against a budget, so a change that adds a request (or reads a
 * screen twice, as 1.29's 광장 did) fails here. The 1.29 counts printed beside are from its code (R2 1장): the same ways, the
 * same server. A screen the phone kept shows before any answer: the fake server holds its answer until the test lets it go.
 * Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoundTripBudgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private lateinit var before: CompanyApi
    private val now = Instant.parse("2026-10-27T03:00:00Z")

    /** The 1.30 server (one request for a page with its room and talk, the whole list, the page with a join) or [old] one. */
    private inner class Server(val old: Boolean = false) : CompanyApi {
        val calls = CopyOnWriteArrayList<String>()
        var member = false
        var gate: CompletableDeferred<Unit>? = null
        var setup = "{}"
        override val enabled = true

        private fun page(id: String, asMember: Boolean) = CompanyPage(id, "가상화학$id", emptyList(), listOf(
            SharedSetup("0123456789abcdef", true, 5, 3, 0, 3, "", 1, setup, listOf("순서 3일" to "주간 야간 휴무"), payHidden = !asMember)), member = asMember)
        private fun room(id: String) = RoomPage(id, "가상화학$id", RoomVersion("0123456789abcdef", true, "", 1, setup, 3, 1, 4, 4), true, false, emptyList(), false)
        private val talk = Talk(listOf(TalkPost("p1", 1, null, false, "주간이 8시에 시작해요", now, false)))

        override suspend fun search(query: String): List<CompanyHit> { calls += "search"; return emptyList() }
        override suspend fun allCompanies(etag: String?): CompanyList? {
            if (old) throw CompanyException("not_found")
            calls += "all"
            return if (etag == "\"e1\"") null else CompanyList((1..300).map {
                val n = "가상화학$it"
                KnownCompany(CompanyHit("c%011d".format(it), n, null, 1, 0, users = it), emptyList(), CompanyMatch.spoken(n), "")
            }, "\"e1\"")
        }
        override suspend fun companyBundle(id: String, device: String?): CompanyBundle {
            if (old) throw CompanyException(CompanyBundle.NONE)
            calls += "bundle"
            gate?.await()
            val m = member && device != null
            return CompanyBundle(page(id, m), if (m) room(id) else null, if (m) talk else null)
        }
        override suspend fun company(id: String): CompanyPage { calls += "company"; gate?.await(); return page(id, member) }
        override suspend fun room(device: String, company: String): RoomPage? { calls += "room"; return if (member) room(company) else null }
        override suspend fun talk(device: String, company: String, setup: String): Talk { calls += "talk"; return talk }
        override suspend fun join(device: String, company: String, cal: String): Membership {
            calls += "join"
            member = true
            return Membership(true, company, page = if (old) null else page(company, true))
        }
        override suspend fun use(device: String, company: String, setup: String, on: Boolean, cal: String): Int { calls += "use"; return 1 }
        override suspend fun terms(): CompanyTerms { calls += "terms"; return CompanyTerms(3, 3) }
        override suspend fun plazas(): List<PlazaJob> { calls += "plazas"; return listOf(PlazaJob("factory", "제조·생산", 1, 1, 0)) }
        override suspend fun board(adopted: Boolean, from: Int, q: String, job: String, region: String, sort: String): Board {
            calls += "board"
            return Board((1..5).map { BoardItem("c%011d".format(200 + it), "가상화학${200 + it}", "s$it", "", adopted, 1, 1, 0, 3, 33, 100, 0, null, now, "주간", "순서 3일", false) }, null)
        }
        override suspend fun post(device: String, company: String, setup: String, body: String): String { calls += "post"; gate?.await(); return "p9" }
        override suspend fun post(device: String, company: String, setup: String, body: String, parent: String?, kind: String,
            change: List<ChangeLine>, proposal: JSONObject?): String { calls += "post"; gate?.await(); return "p9" }
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus =
            throw CompanyException("offline")
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?) = VoteResult(3, 3, 0, 3, true)

        /** Requests other than reading ahead (which runs on its own, apart from what the user waits for). */
        fun count(vararg kinds: String) = calls.count { it in kinds }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun until(what: String, check: () -> Boolean) {
        repeat(250) {
            if (check()) return
            idle()
            Thread.sleep(20)
        }
        assertTrue(what, check())
    }

    private fun server(old: Boolean = false): Server = Server(old).also { s ->
        c.companyApi = s
        runBlocking {
            c.companySearch.cache.load()
            // What the screens start as they open (the whole list, read ahead) is done before counting.
            c.warmCompanies()
            c.companySearch.warmed()
        }
        s.calls.clear()
    }

    private fun report(way: String, before: String, after: Int) = println("[AP1] $way: 1.29 $before → 1.30 ${after}요청")

    @Before fun start() {
        before = c.companyApi
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
    }

    @After fun end() {
        c.companyApi = before
        c.companyStore.setApplied(null)
        c.companyStore.setMember(null)
    }

    @Test fun typingANameWithTheWholeListAsksNothing() = runBlocking {
        val s = server()
        val search = CompanySearch({ s }, null)
        search.warm(saver = false)
        assertEquals(1, s.count("all"))
        s.calls.clear()
        for (q in listOf("가상", "가상화", "가상화학12")) {
            val steps = search.states(q, pauseMs = 0).first { it.stage == CompanySearch.Stage.DONE }
            assertTrue(steps.hits.isNotEmpty())
        }
        assertEquals("three letters typed, nothing asked", 0, s.calls.size)
        // A name the list does not have is asked once, in case the company came after it.
        search.states("없는회사", pauseMs = 0).first { it.stage == CompanySearch.Stage.DONE }
        assertEquals(1, s.count("search"))
        report("회사 검색 3글자(전체 목록 받은 뒤)", "3요청(글자 멈출 때마다)", 0)
        // Ten minutes on, the list is asked again with its ETag: the same list, nothing comes back, and it is fresh again.
        var clock = System.currentTimeMillis()
        val later = CompanySearch({ s }, null, now = { clock })
        later.warm(saver = false)
        later.warm(saver = false)
        clock += CompanySearch.ALL_FRESH_MS + 1
        assertTrue(!later.peek("가상화학7").enough)
        later.warm(saver = false)
        assertEquals("first read, then once with its ETag after ten minutes", 2, s.count("all"))
        assertTrue(later.peek("가상화학7").enough)
        Unit
    }

    /** The app's own client against SP1's answers: one request for a member's page, room and talk; the page with a join. */
    @Test fun theClientReadsAPageWithItsRoomAndTalkInOneRequest() = runBlocking {
        TinyCompanyServer(listOf("가상화학", "가상물류")).use { s ->
            val client = CompanyClient(s.base)
            val b = client.bundle(s.id(0), "member")
            assertEquals(1, s.requests.get())
            assertTrue(s.seen.single().path.endsWith("?with=room,talk"))
            assertTrue(b.page.member)
            assertNotNull(b.room)
            assertEquals("주간이 8시에 시작해요", b.talk!!.posts.single().body)
            // Read ahead as anyone: no X-Device, the page alone.
            val anyone = client.bundle(s.id(1), null)
            assertNull(s.seen.last().headers["x-device"])
            assertNull(anyone.room)
            // Joining brings the page as the member reads it.
            val m = client.join("member", s.id(1), "main")
            assertEquals("page", JSONObject(s.seen.last().body).getString("with"))
            assertTrue(m.page!!.member)
            assertEquals("page, page, join with its page", 3, s.requests.get())
        }
        // A server before 1.30: the page without them, so the room and talk are asked beside each other.
        TinyCompanyServer(listOf("가상화학"), old = true).use { s ->
            val b = CompanyClient(s.base).bundle(s.id(0), "member")
            assertEquals(3, s.requests.get())
            assertNotNull(b.room)
            assertNotNull(b.talk)
            assertNull(CompanyClient(s.base).join("member", s.id(0), "main").page)
        }
    }

    @Test fun aCompanySeenBeforeOpensAtOnceAndIsReadAgainWithOneRequest() {
        val s = server()
        s.member = true
        val id = "c00000000101"
        c.companySearch.cache.forget(id)
        // The first time: one request for the page, its room and talk (1.29: the page, then the room twice).
        val first = CompanyDetailViewModel(c, id)
        until("page read") { first.state.value is CompanyDetailViewModel.State.Loaded && first.room.value != null }
        assertEquals(listOf("bundle"), s.calls.toList())
        report("회사 화면(참여자, 처음)", "3요청(페이지·방·방 한 번 더)", s.calls.size)
        // Again (another run): the page and room are on screen before the server answers; still one request.
        s.calls.clear()
        s.gate = CompletableDeferred()
        val again = CompanyDetailViewModel(c, id)
        assertTrue("shown from the phone, no spinner", again.state.value is CompanyDetailViewModel.State.Loaded)
        assertTrue(again.refreshing.value)
        s.gate!!.complete(Unit)
        until("read again") { !again.refreshing.value && again.room.value != null }
        assertEquals(listOf("bundle"), s.calls.toList())
        assertNotNull(again.room.value)
        report("회사 화면(참여자, 다시)", "3요청·스피너", s.calls.size)
    }

    @Test fun aServerBefore130IsAskedForTheRoomAndTalkBeside() {
        val s = server(old = true)
        s.member = true
        val id = "c00000000102"
        c.companySearch.cache.forget(id)
        val vm = CompanyDetailViewModel(c, id)
        until("page read") { vm.state.value is CompanyDetailViewModel.State.Loaded && vm.room.value != null }
        assertEquals(setOf("company", "room", "talk"), s.calls.toSet())
        assertEquals(3, s.calls.size)
    }

    @Test fun thePlazaOpensWithOneRequestAndATalkOpenedAndClosedWithoutWritingWithOne() {
        val s = server()
        s.member = true
        val id = "c00000000103"
        c.companySearch.cache.forget(id)
        c.companyStore.setMember(id, "가상화학103")
        val vm = PlazaHomeViewModel(c)
        assertEquals(StylePlaza.Top.COMPANY_ROOM, vm.plan.value.top)
        until("plaza read") { vm.state.value.talk != null && vm.state.value.room != null }
        assertEquals("one request, not 1.29's four (talk and room, twice)", listOf("bundle"), s.calls.toList())
        report("회사 정보 광장 열기", "4요청(토론·방 × 두 번)", s.calls.size)

        s.calls.clear()
        vm.openTalk()
        // The talk shows at once from the page it came with, and is read again once.
        assertNotNull(vm.talk.open.value?.talk)
        until("talk read") { vm.talk.open.value?.refreshing == false }
        vm.talk.close()
        vm.sheetClosed()
        idle()
        assertEquals(listOf("talk"), s.calls.toList())
        report("자유 토의 열고 닫기(글 안 씀)", "3요청(토론 + 닫으면 광장 전체)", s.calls.size)

        // Written: the post shows at once; closing takes the talk the sheet read after it, nothing more.
        s.calls.clear()
        vm.openTalk()
        var sent: String? = "not yet"
        s.gate = CompletableDeferred()
        vm.talk.send("다음 주부터 야간이 9시예요") { sent = it }
        assertTrue("on screen before the server took it", vm.talk.open.value!!.talk!!.posts.any { it.body == "다음 주부터 야간이 9시예요" })
        s.gate!!.complete(Unit)
        until("sent") { sent == null && s.count("talk") == 2 }
        vm.talk.close()
        vm.sheetClosed()
        idle()
        assertEquals(listOf("talk", "post", "talk"), s.calls.toList())
        assertEquals(vm.talk.last, vm.state.value.talk)
    }

    @Test fun usingASetupGoesOnAfterOneRequest() {
        val s = server()
        runBlocking {
            c.scheduleRepository.ensureDefaults()
            val types = c.scheduleRepository.shiftTypes.first().associateBy { it.name }
            c.scheduleRepository.savePattern(WorkPattern.Rotation(LocalDate.now().minusDays(3), listOf("주간", "야간", "휴무").map { types.getValue(it).id }))
            s.setup = c.companyShare.exportForServer(true).toString()
        }
        val id = "c00000000104"
        c.companySearch.cache.forget(id)
        val vm = CompanyDetailViewModel(c, id)
        until("page read") { vm.state.value is CompanyDetailViewModel.State.Loaded }
        s.calls.clear()
        val page = (vm.state.value as CompanyDetailViewModel.State.Loaded).page
        var doneAfter = -1
        var failed: String? = null
        // '쓰고 있어요' (use) goes on its own; what the next screen waits for is the rest.
        vm.apply(page, page.setups.first(), null, withPay = false, done = { doneAfter = s.calls.count { it != "use" } }, failed = { failed = it })
        until("applied") { doneAfter >= 0 || failed != null }
        assertNull(failed)
        assertEquals("the next screen after the join alone (1.29: join, page, room)", 1, doneAfter)
        until("the rest behind") { s.count("use") == 1 && s.count("room") == 1 }
        assertEquals(0, s.count("company"))
        report("이 설정 쓰기(참여 전) 다음 화면까지", "3요청 차례로(참여·페이지·방)", doneAfter)
    }

    @Test fun theRulePlazaOpensWithItsTwoPagesAndKeepsTheRestForADay() {
        val s = server()
        c.companySearch.cache.keepTerms(CompanyTerms(3, 3))
        c.companySearch.cache.keepPlazas(listOf(PlazaJob("factory", "제조·생산", 1, 1, 0)))
        val vm = CompanyBoardViewModel(c)
        until("both tabs") { vm.voting.value.items.isNotEmpty() && vm.chosen.value.items.isNotEmpty() }
        idle()
        assertEquals(2, s.count("board"))
        assertEquals(0, s.count("plazas", "terms"))
        report("규칙 광장 열기", "4요청(두 탭·광장 목록·약관)", s.count("board", "plazas", "terms"))
        // Opened again: both first pages from the phone at once, while they are read again.
        val again = CompanyBoardViewModel(c)
        assertTrue(again.voting.value.items.isNotEmpty())
        // What is read ahead for a tap is at most the shown tab's first three, as anyone (never this phone's install).
        until("read ahead") { s.count("bundle") >= 1 }
        assertTrue(s.count("bundle") <= CompanySearch.PREFETCH)
    }
}
