package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.Board
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyBundle
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyList
import com.bamnat.app.company.KnownCompany
import com.bamnat.app.company.LeadLine
import com.bamnat.app.company.NetTimings
import com.bamnat.app.company.RoomPage
import com.bamnat.app.company.RoomVersion
import com.bamnat.app.company.Talk
import com.bamnat.app.ui.company.CompanyBoardScreen
import com.bamnat.app.ui.company.CompanyBoardViewModel
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyInlineResults
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.app.ui.diag.SpeedCheckCard
import com.bamnat.core.plaza.CompanyMatch
import com.bamnat.core.plaza.StylePlaza
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SPD-11]~[SPD-19] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다 … 아주 중요한 문제"): a screen seen once opens
 * at once from the phone with a thin line on top while it is read again; no connection keeps it with "최근 본 내용이에요"; the
 * whole list of companies finds a name as it is typed without asking; a post shows at once; 자가 점검 says how fast it was.
 * Shots 1450~1469, made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AP1ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val names = listOf("(주)예시정밀", "예시화학", "예시물류", "예시제철", "예시전자", "예시중공업", "예시정유", "가나다화학", "라마바전자")

    /** The made-up server as 1.30's: the whole list, a page with its room and talk in one answer; [gate] holds its answers, [offline] has none. */
    private inner class Fast(private val base: FakeCompanyApi) : CompanyApi by base {
        var gate: CompletableDeferred<Unit>? = null
        var offline = false
        val asked = mutableListOf<String>()

        override suspend fun allCompanies(etag: String?): CompanyList = CompanyList(names.mapIndexed { i, n ->
            KnownCompany(CompanyHit("c${i + 1}", n, null, 1, 0, users = 30 - i,
                lead = LeadLine("s$i", "", true, listOf("주간", "주간", null, null, "야간", "야간", null, null), null, setOf("휴무"), pay = i % 2 == 0, ok = 5, bad = 0)),
                emptyList(), CompanyMatch.spoken(n), "")
        }, "\"e1\"")

        override suspend fun search(query: String): List<CompanyHit> { asked += "search"; return base.search(query) }

        override suspend fun companyBundle(id: String, device: String?): CompanyBundle {
            asked += "bundle"
            gate?.await()
            if (offline) throw CompanyException("offline")
            val member = device != null && c.companyStore.member.value?.first == id
            val page = base.company(id).copy(member = member)
            return CompanyBundle(page, if (member) room(id) else null, if (member) base.talk(device!!, id, StylePlaza.ROOM) else null)
        }

        override suspend fun talk(device: String, company: String, setup: String): Talk {
            gate?.await()
            if (offline) throw CompanyException("offline")
            return base.talk(device, company, setup)
        }

        override suspend fun board(adopted: Boolean, from: Int, q: String, job: String, region: String, sort: String): Board {
            gate?.await()
            if (offline) throw CompanyException("offline")
            return base.board(adopted, from, q, job, region, sort)
        }

        override suspend fun post(device: String, company: String, setup: String, body: String): String {
            gate?.await()
            return base.post(device, company, setup, body)
        }

        override suspend fun post(device: String, company: String, setup: String, body: String, parent: String?, kind: String,
            change: List<com.bamnat.app.company.ChangeLine>, proposal: org.json.JSONObject?): String {
            gate?.await()
            return base.post(device, company, setup, body, parent, kind, change, proposal)
        }

        override suspend fun room(device: String, company: String): RoomPage = room(company)

        private fun room(id: String) = RoomPage(id, "(주)예시정밀",
            base = RoomVersion("s1", true, "생산팀", 2, base.file, 3, 3, 4, 4), mine = false, first = false, others = emptyList(), stable = false)
    }

    private fun fast(): Fast = Fast(company).also { f ->
        companyOn()
        c.companyApi = f
        runBlocking { c.companySearch.warm(saver = false) }
    }

    /** What the phone kept from the last run: [id]'s page, read as it is now, then the run ends. */
    private fun seen(f: Fast, id: String = "c1") = runBlocking {
        c.companySearch.cache.keepBundle(f.companyBundle(id, c.companyStore.device))
        f.asked.clear()
    }

    private fun Screen.detail(): CompanyDetailViewModel {
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        return vm
    }

    private fun Screen.home(): PlazaHomeViewModel {
        val vm = PlazaHomeViewModel(c)
        content { PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        return vm
    }

    // ---- [SPD-11] 회사 찾기: the whole list on the phone ----
    @Test fun aNameIsFoundAsItIsTypedWithoutAsking() = shoot("1450_ap1_find_whole_list", PHONE) {
        val f = fast()
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                CompanyInlineResults("예시", onServer = { }, onPick = { _, _ -> })
            }
        }
        settle()
        // The server's order: starting with it, shorter, then by name.
        assertTrue(appears("예시물류", exact = true))
        assertTrue(appears("예시화학", exact = true))
        assertTrue("no spinner, nothing asked", gone("찾는 중", exact = false))
        assertEquals(emptyList<String>(), f.asked.filter { it == "search" })
    }

    @Test fun theOtherSpellingAndFirstLettersToo() = shoot("1451_ap1_find_choseong", PHONE) {
        val f = fast()
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                CompanyInlineResults("ㄱㄴㄷ", onServer = { }, onPick = { _, _ -> })
            }
        }
        settle()
        assertTrue(appears("가나다화학", exact = true))
        assertEquals(emptyList<String>(), f.asked.filter { it == "search" })
    }

    // ---- [SPD-13] 회사 화면: seen before, at once ----
    @Test fun aCompanySeenBeforeShowsAtOnceWithAThinLine() = shoot("1452_ap1_detail_cached_refreshing", PHONE) {
        val f = fast()
        seen(f)
        f.gate = CompletableDeferred()
        val vm = detail()
        assertTrue("on screen from the phone, no spinner", vm.state.value is CompanyDetailViewModel.State.Loaded)
        assertTrue(vm.refreshing.value)
        assertTrue(appears("(주)예시정밀", exact = false))
    }

    @Test fun noConnectionKeepsWhatWasSeen() = shoot("1453_ap1_detail_stale", PHONE) {
        val f = fast()
        seen(f)
        f.offline = true
        val vm = detail()
        assertTrue(vm.state.value is CompanyDetailViewModel.State.Loaded)
        assertTrue(vm.stale.value)
        reveal("최근 본 내용이에요. 인터넷이 되면 새로 불러와요.", exact = true)
        assertTrue(appears("다시 시도", exact = true))
    }

    @Test fun aCompanyNeverSeenStillShowsItsSpinnerOnce() = shoot("1454_ap1_detail_first_time", PHONE) {
        val f = fast()
        f.gate = CompletableDeferred()
        val vm = detail()
        assertTrue(vm.state.value is CompanyDetailViewModel.State.Loading)
        assertEquals(listOf("bundle"), f.asked)
    }

    // ---- [SPD-13][SPD-14] 회사 정보 광장 ----
    @Test fun thePlazaSeenBeforeShowsAtOnce() = shoot("1455_ap1_plaza_cached", PHONE) {
        val f = fast()
        c.companyStore.setMember("c1", "(주)예시정밀")
        seen(f)
        f.gate = CompletableDeferred()
        val vm = home()
        assertTrue("talk from the phone", vm.state.value.talk != null)
        assertTrue(vm.state.value.refreshing)
    }

    @Test fun thePlazaWithoutAConnection() = shoot("1456_ap1_plaza_stale", PHONE) {
        val f = fast()
        c.companyStore.setMember("c1", "(주)예시정밀")
        seen(f)
        f.offline = true
        val vm = home()
        assertTrue(vm.state.value.stale)
        assertTrue(appears("최근 본 내용이에요", exact = false))
    }

    // ---- [SPD-16] a post shows at once ----
    @Test fun aPostShowsBeforeTheServerTookIt() = shoot("1457_ap1_talk_sending", PHONE) {
        val f = fast()
        c.companyStore.setMember("c1", "(주)예시정밀")
        val vm = home()
        vm.openTalk()
        settle()
        f.gate = CompletableDeferred()
        vm.talk.send("다음 주부터 야간이 9시예요") { }
        settle()
        assertTrue(vm.talk.open.value!!.talk!!.posts.any { it.body == "다음 주부터 야간이 9시예요" })
        reveal("다음 주부터 야간이 9시예요", exact = true)
        assertTrue(appears("보내는 중", exact = false))
    }

    // ---- [SPD-13] 규칙 광장 ----
    @Test fun theBoardSeenBeforeWithoutAConnection() = shoot("1458_ap1_board_stale", PHONE) {
        val f = fast()
        CompanyBoardViewModel(c)
        settle()
        f.offline = true
        val vm = CompanyBoardViewModel(c)
        content { CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}) }
        settle()
        assertTrue(appears("최근 본 내용이에요", exact = false))
    }

    // ---- [SPD-17] 자가 점검: 조회 속도 ----
    @Test fun theSpeedCard() = shoot("1459_ap1_speed_card", PHONE) {
        NetTimings.clear()
        listOf(120L, 180L, 240L, 300L, 90L, 2_900L).forEach { NetTimings.record("GET", "/api/board", it, NetTimings.Result.OK) }
        NetTimings.record("GET", "/api/companies/all", 400, NetTimings.Result.NOT_MODIFIED)
        NetTimings.record("GET", "/api/terms", 6_000, NetTimings.Result.OFFLINE)
        repeat(7) { NetTimings.cached(true) }
        repeat(2) { NetTimings.cached(false) }
        NetTimings.end(NetTimings.Wait.COMPANY, System.nanoTime() - 30_000_000)
        NetTimings.end(NetTimings.Wait.PLAZA, System.nanoTime() - 45_000_000)
        val s = NetTimings.summary()
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { SpeedCheckCard(s) }
        }
        settle()
        assertTrue(appears("조회 속도", exact = true))
        assertTrue(appears("기다리지 않고 바로 보인 화면: 77% (9번 중 7번)", exact = true))
        NetTimings.clear()
    }

    @Test fun theSpeedCardAtNight() = shoot("1460_ap1_speed_card_night", "w360dp-h780dp-port-night-450dpi") {
        NetTimings.clear()
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { SpeedCheckCard() }
        }
        settle()
        assertTrue(appears("아직 잰 것이 없어요. 회사 찾기나 광장을 열면 재요.", exact = true))
    }

    @Test fun theStaleLineAtLargeText() = shoot("1461_ap1_detail_stale_large", SMALL_PHONE, 2f) {
        val f = fast()
        seen(f)
        f.offline = true
        detail()
        reveal("다시 시도", exact = true)
    }
}
