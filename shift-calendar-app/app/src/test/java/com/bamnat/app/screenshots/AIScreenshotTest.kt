package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyClient
import com.bamnat.app.company.RoomPage
import com.bamnat.app.company.RoomVersion
import com.bamnat.app.company.Talk
import com.bamnat.app.company.TalkPost
import com.bamnat.app.ui.company.AutoShareHost
import com.bamnat.app.ui.company.CompanyBoardScreen
import com.bamnat.app.ui.company.CompanyBoardViewModel
import com.bamnat.app.ui.company.CompanyReportScreen
import com.bamnat.app.ui.company.CompanyReportViewModel
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.app.ui.company.PlazaRoomLine
import com.bamnat.app.ui.settings.AutoShareRow
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipCheck
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.ServerSocket
import java.time.Instant
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * [PLAZA-30]~[PLAZA-35] 1.27 묶음 AI's screenshots, numbers 1120~1139: the 명세서 tick only with a matched month (and its
 * shortcut), the whole way from a public cycle's card to the company's page on the real company server (442), the company's
 * 정보 광장 (자유 토의, 근무·급여 정보, 고친 점) and its ways in, the plazas of each kind of work, and the ask with what changed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AIScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val gsCaltex get() = PublicCycles.all.first { it.company == "GS칼텍스" }

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    /** The made-up server of [ScreenshotBase], with rooms whose posts are kept. */
    private inner class Rooms(private val base: FakeCompanyApi) : CompanyApi by base {
        val posts = mutableMapOf<String, MutableList<TalkPost>>()
        var others: List<RoomVersion> = emptyList()
        private val now = Instant.now()

        fun seed(room: String, vararg lines: Pair<Int, String>) {
            posts[room] = lines.mapIndexed { i, (who, body) ->
                TalkPost("$room-$i", who, null, false, body, now.minusSeconds(3600L * (lines.size - i)), who == 1,
                    parent = if (i == 2) "$room-1" else null)
            }.toMutableList()
        }

        override suspend fun talk(device: String, company: String, setup: String): Talk =
            if (setup == StylePlaza.ROOM) Talk(posts[company].orEmpty(), leftPosts = 10 - posts[company].orEmpty().count { it.mine })
            else base.talk(device, company, setup)

        override suspend fun post(device: String, company: String, setup: String, body: String): String =
            post(device, company, setup, body, null, "talk", emptyList(), null)

        override suspend fun post(device: String, company: String, setup: String, body: String, parent: String?, kind: String,
            change: List<com.bamnat.app.company.ChangeLine>, proposal: org.json.JSONObject?): String {
            if (setup != StylePlaza.ROOM) return base.post(device, company, setup, body, parent, kind, change, proposal)
            val list = posts.getOrPut(company) { mutableListOf() }
            list += TalkPost("$company-${list.size}", 1, null, false, body, Instant.now(), true, parent = parent, kind = kind)
            return list.last().id
        }

        override suspend fun room(device: String, company: String): RoomPage = RoomPage(company, "(주)예시정밀",
            base = RoomVersion("s1", true, "생산팀", 2, base.file, 3, 3, 4, 4), mine = false, first = false, others = others, stable = false)
    }

    private fun rooms(): Rooms = Rooms(company).also { r ->
        companyOn()
        c.companyApi = r
    }

    private fun ownPay(verified: PayslipCheck? = null) = runBlocking {
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 2_500_000))),
            rules = CompanyRules(nightPremium = 0.5), verified = verified), keepUndo = false)
    }

    private fun mine(): String = runBlocking { c.companyShare.exportForServer().toString() }

    private fun style(s: WorkStyle?) = c.settingsRepository.update { it.copy(workStyle = s) }

    private fun Screen.home(): PlazaHomeViewModel {
        val vm = PlazaHomeViewModel(c)
        content { PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        return vm
    }

    // ---- 441 [PLAZA-30] 명세서 tick ----
    @Test fun noMatchedMonthNoTick() = shoot("1120_ai_tick_off", PHONE_393) {
        companyOn()
        ownPay()
        var matched = false
        val vm = CompanyReportViewModel(c)
        content { CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}, onMatch = { matched = true }) }
        settle()
        reveal("급여 명세서와 맞춰 봤어요", exact = true)
        assertTrue(appears("이 달력에서 명세서로 맞춰 본 기록이 없어요", exact = false))
        tapText("명세서로 맞춰 보기", exact = true)
        assertTrue(matched)
        assertTrue(c.pendingMatch)
        c.pendingMatch = false
    }
    @Test fun aMatchedMonthOpensTheTick() = shoot("1121_ai_tick_on", PHONE_393) {
        companyOn()
        ownPay(PayslipCheck(YearMonth.now().minusMonths(1), 2_800_000, 2_801_000, 0))
        val vm = CompanyReportViewModel(c)
        content { CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}, onMatch = {}) }
        settle()
        reveal("급여 명세서와 맞춰 봤어요", exact = true)
        assertTrue(appears("명세서로 맞춰 본 기록이 있어요 · 일치", exact = false))
        assertTrue(gone("명세서로 맞춰 보기", exact = true))
    }

    // ---- 442 [PLAZA-30] the whole way on the real company server ----
    /** The public cycle of GS칼텍스 on the calendar with its pay, the company server on Node. */
    private fun Screen.flow(base: String) {
        c.companyApi = CompanyClient(base, device = { c.companyStore.device })
        runBlocking { c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile().copy(rules = companyRules), keepUndo = false) }
        runBlocking { com.bamnat.app.company.PublicCycleFiles.apply(c, gsCaltex, "DDOONNOO", 0) }
        settle()
        if (!shows("‘GS칼텍스’ 근무주기를 광장에 올려 주실래요?", exact = true)) tapText("근무주기 다음 단계")
        reveal("‘GS칼텍스’ 근무주기를 광장에 올려 주실래요?", exact = true)
        // One ask: the card's own, and no second one from 저절로 나누기 on top.
        assertNull(c.autoShare.ask.value)
        assertFalse(c.autoShare.saved())
    }

    @Test fun flowCard() = shoot("1122_ai_flow_card", PHONE_393) { server { base -> flow(base) } }
    @Test fun flowForm() = shoot("1123_ai_flow_form", PHONE_393) {
        server { base ->
            flow(base)
            tapText("올리기", exact = true)
            assertTrue(appears("공개 자료 확인·고침 · GS칼텍스", exact = true))
        }
    }
    @Test fun flowSent() = shoot("1124_ai_flow_sent", PHONE_393) {
        server { base ->
            flow(base)
            tapText("올리기", exact = true)
            tapText("보내기", exact = true)
            reveal("‘GS칼텍스’ 광장 보기", exact = true)
            val u = c.companyStore.publicUse.value!!
            assertNotNull(u.sentId)
            // Sending joined it: 광장 opens to its 정보 광장, and what went is the base of the next ask.
            assertEquals(u.companyId, c.companyStore.member.value?.first)
            assertNotNull(c.companyStore.autoBase)
        }
    }
    @Test fun flowCompany() = shoot("1125_ai_flow_company", PHONE_393) {
        server { base ->
            flow(base)
            tapText("올리기", exact = true)
            tapText("보내기", exact = true)
            tapText("‘GS칼텍스’ 광장 보기", exact = true)
            reveal("GS칼텍스", exact = false)
            assertTrue(appears("우리 회사 광장", exact = true) || shows("제보 1 · 확인 중", exact = true))
        }
    }
    @Test fun flowRoom() = shoot("1126_ai_flow_room", PHONE_393) {
        server { base ->
            flow(base)
            tapText("올리기", exact = true)
            tapText("보내기", exact = true)
            reveal("‘GS칼텍스’ 광장 보기", exact = true)
            // The same phone opens 광장: its company's 정보 광장, where it writes the first post on the server.
            val vm = home()
            reveal("‘GS칼텍스’ 정보 광장", exact = true)
            vm.openTalk()
            settle()
            typeInto("같은 회사 분들께 하고 싶은 말을 적어 주세요", "야간 끝나고 통근버스 몇 시에 와요?")
            tapText("보내기", exact = true)
            // The plaza's rules, once before the first post.
            if (shows("동의하고 쓰기", exact = true)) tapText("동의하고 쓰기", exact = true)
            reveal("야간 끝나고 통근버스 몇 시에 와요?", exact = true)
            val room = c.companyStore.member.value!!.first
            assertEquals(1, runBlocking { c.companyApi.talk(c.companyStore.device, room, StylePlaza.ROOM) }.posts.size)
        }
    }

    // ---- 443 [PLAZA-32] 회사 정보 광장 ----
    private fun member() {
        c.companyStore.setMember("c1", "(주)예시정밀")
        c.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
    }

    @Test fun theCompanysRoom() = shoot("1127_ai_room", FOLDED) {
        ownPay()
        val r = rooms()
        member()
        r.others = listOf(RoomVersion("s7", false, "생산팀", null, SetupEdit.of(mine())!!.rate("night", 0.6).text, 3, 3, 1, 3))
        r.seed("c1", 2 to "이번 달 특근 공지 보셨어요? 토요일 두 번이래요", 3 to "야간 끝나고 통근버스 몇 시에 와요?", 1 to "6시 40분에 와요")
        home()
        assertTrue(appears("‘(주)예시정밀’ 정보 광장", exact = true))
        reveal("근무·급여 정보", exact = true)
        reveal("야간 50% → 60%로 바꾼 사람 3명", exact = false)
        reveal("글 보기·쓰기", exact = true)
        assertTrue(appears("댓글 1개", exact = true))
    }
    @Test fun theRoomsTalk() = shoot("1128_ai_room_talk", PHONE) {
        val r = rooms()
        member()
        r.seed("c1", 2 to "이번 달 특근 공지 보셨어요? 토요일 두 번이래요", 3 to "야간 끝나고 통근버스 몇 시에 와요?", 1 to "6시 40분에 와요")
        val vm = home()
        vm.openTalk()
        settle()
        assertTrue(appears("자유 토의", exact = true))
        // A room has no rule to change: no 고치자는 제안.
        assertTrue(gone("고치자는 제안", exact = true))
        typeInto("같은 회사 분들께 하고 싶은 말을 적어 주세요", "주차장 자리 요즘 넉넉해요?")
        tapText("보내기", exact = true)
        if (shows("동의하고 쓰기", exact = true)) tapText("동의하고 쓰기", exact = true)
        reveal("주차장 자리 요즘 넉넉해요?", exact = true)
        assertEquals(4, r.posts.getValue("c1").size)
    }
    @Test fun applyingOpensTheRoomWithALine() = shoot("1129_ai_room_after_apply", PHONE_393_NIGHT) {
        rooms()
        member()
        c.pendingPlazaNote = "‘(주)예시정밀’ 설정을 넣었어요 · 5일 순서. 이제 같은 회사 분들끼리 이야기하는 정보 광장이에요"
        home()
        assertTrue(appears("이제 같은 회사 분들끼리 이야기하는 정보 광장이에요", exact = false))
        assertNull(c.pendingPlazaNote)
    }
    @Test fun thePlazaTopLeadsToACompany() = shoot("1130_ai_board_find", PHONE) {
        companyOn()
        var found = false
        val vm = CompanyBoardViewModel(c)
        content { CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onFind = { found = true }) }
        settle()
        assertTrue(appears("회사를 고르면 그 회사 분들끼리 이야기하는 ‘회사 정보 광장’이 열려요", exact = true))
        tapText("회사 찾기", exact = true)
        assertTrue(found)
    }
    @Test fun theCalendarLeadsToTheRoom() = shoot("1131_ai_calendar_line", SMALL_PHONE) {
        companyOn()
        var opened = false
        content { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { PlazaRoomLine(onOpen = { opened = true }) } }
        settle()
        assertTrue(gone("정보 광장", exact = false))
        member()
        settle()
        tapText("‘(주)예시정밀’ 정보 광장 · 자유 토의와 고친 점", exact = true)
        assertTrue(opened)
    }

    // ---- 444 [PLAZA-33] 유형별 광장 ----
    @Test fun theFieldPlaza() = shoot("1132_ai_style_field", PHONE) {
        val r = rooms()
        style(WorkStyle.FIELD)
        r.seed(StylePlaza.FIELD, 2 to "인천 쪽 타일 단가 요즘 어때요?", 3 to "다음 주 비 온다던데 일정 미루셨어요?")
        home()
        assertTrue(appears("현장 광장", exact = true))
        assertTrue(appears("지역·직종 일당 시세", exact = true))
        assertTrue(appears("현장 이야기", exact = true))
    }
    @Test fun theShopPlaza() = shoot("1133_ai_style_shop", PHONE) {
        val r = rooms()
        style(WorkStyle.BUSINESS)
        r.seed(StylePlaza.SHOP, 2 to "평일 점심 손님이 지난달보다 10% 늘었어요", 3 to "배달앱 리뷰 이벤트 효과 있었어요?")
        home()
        assertTrue(appears("가게 광장", exact = true))
        assertTrue(appears("영업·매출 팁", exact = true))
        assertTrue(gone("시세 보기·올리기", exact = true))
    }
    @Test fun theHourlyPlaza() = shoot("1134_ai_style_hourly", SMALL_PHONE, 2f) {
        rooms()
        style(WorkStyle.HOURLY)
        home()
        assertTrue(appears("알바 광장", exact = true))
        reveal("지역 시급 시세", exact = true)
    }
    @Test fun theInvestPlazaTakesNoTips() = shoot("1135_ai_style_invest", PHONE_393_NIGHT) {
        val r = rooms()
        style(WorkStyle.INVEST)
        r.seed(StylePlaza.INVEST, 2 to "분할 매수할 때 몇 번에 나눠 사세요?", 3 to "매매 일지 쓰는 방법 공유해요")
        // Even in a company: 투자 이야기 is a kind's own.
        member()
        home()
        assertTrue(appears("투자 이야기", exact = true))
        assertTrue(appears("종목 추천·매수 권유·리딩방 안내 글은 가려요", exact = false))
    }

    // ---- 445 [PLAZA-34] 바뀐 내용을 나눌까요 ----
    @Test fun aChangeIsAskedWithWhatChanged() = shoot("1136_ai_change_ask", PHONE, 1.3f) {
        rooms()
        ownPay()
        member()
        c.companyStore.setAutoShare(null)
        c.companyStore.autoBase = mine()
        runBlocking { c.payStore.save(c.payStore.profile.value!!.copy(rules = CompanyRules(nightPremium = 0.6)), keepUndo = false) }
        content { AutoShareHost(c, onOpen = {}) }
        runBlocking { c.autoShare.check() }
        settle()
        assertTrue(appears("바뀐 내용을 회사 광장에 나눌까요?", exact = true))
        assertTrue(appears("• 야간 50% → 60%", exact = true))
        assertTrue(appears("다음부터 묻지 않고 저절로 나누기 (하루 한 번)", exact = true))
        assertTrue(appears("다시 묻지 않기", exact = true))
        // The same values saved again ask nothing.
        tapText("이번엔 안 할게요", exact = true)
        settle()
        runBlocking { c.autoShare.check() }
        assertNull(c.autoShare.ask.value)
    }
    @Test fun byItselfItSaysSoInOneLine() = shoot("1137_ai_shared_line", PHONE) {
        rooms()
        ownPay()
        member()
        c.companyStore.setAutoShare(true)
        c.companyStore.autoBase = mine()
        c.companyStore.autoSentAt = Today.millis()
        runBlocking { c.payStore.save(c.payStore.profile.value!!.copy(rules = CompanyRules(nightPremium = 0.6)), keepUndo = false) }
        content {
            val bar = remember { SnackbarHostState() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AutoShareHost(c, onOpen = {}, snackbar = bar)
                SnackbarHost(bar, Modifier.align(Alignment.BottomCenter))
            }
        }
        runBlocking { c.autoShare.check() }
        settle()
        assertTrue(appears("(주)예시정밀 광장에 내일 나눠요(하루 한 번) · 야간 50% → 60%", exact = true))
        assertTrue(c.autoShare.waiting)
        tapText("되돌리기", exact = true)
        settle()
        assertFalse(c.autoShare.waiting)
    }
    @Test fun theSettingAsksOrSharesOrNot() = shoot("1138_ai_setting", SMALL_PHONE) {
        companyOn()
        member()
        c.companyStore.setAutoShare(false)
        content { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { AutoShareRow() } }
        settle()
        assertTrue(appears("‘다시 묻지 않기’로 두셨어요", exact = false))
        tapText("바꿀 때마다 묻기로 되돌리기", exact = true)
        settle()
        assertNull(c.companyStore.autoShare.value)
        assertTrue(appears("바꿀 때마다 ‘(주)예시정밀 광장에 나눌까요?’ 하고 물어요", exact = false))
    }

    /** The company server on Node (server/company-worker), as PlazaAIAppTest runs it. */
    private fun server(block: (String) -> Unit) {
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
