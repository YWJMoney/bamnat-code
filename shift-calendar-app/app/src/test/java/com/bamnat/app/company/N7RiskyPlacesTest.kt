package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.ui.company.forgetEverythingSent
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.plaza.MakerMark
import com.bamnat.core.plaza.SharePart
import com.bamnat.core.plaza.ShareWay
import com.bamnat.core.plaza.ShareWays
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
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 2.0.0 [RVN-22] 검토 COMP-61: one test for each risky place the review found without one. A new run asks for the whole list with
 * the tags kept on the phone (COMP-45, "새 클라이언트 + 저장된 태그"); a 나눌게요 and a 되돌리기 without a connection (COMP-46·47); a
 * kind of work's talk kept and read back with every sign it showed (COMP-57); a question waiting on the phone keeps what it was
 * (COMP-53); and what 모두 지우기 leaves. ('답 → 읽음 → 답' is [com.bamnat.app.review.InquiryAnswerNoticeTest].) Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class N7RiskyPlacesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer

    // ---- The whole list across runs (COMP-45) --------------------------------------------------------------------------------

    /** A company server for the two parts of the whole list, with their tags, answering 304 to a tag it still has. */
    private class ListServer {
        val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        @Volatile var namesTag = "\"n1\""
        @Volatile var countsTag = "\"c1\""
        @Volatile var users = 3
        /** "names If-None-Match" for each request, in the order they came. */
        val asked = CopyOnWriteArrayList<String>()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val s = runCatching { socket.accept() }.getOrNull() ?: break
                    runCatching { s.use(::answer) }
                }
            }
        }

        private fun answer(s: java.net.Socket) {
            val reader = s.getInputStream().bufferedReader()
            val path = reader.readLine().orEmpty().split(' ').getOrElse(1) { "" }
            var tag: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("If-None-Match:", ignoreCase = true)) tag = line.substringAfter(":").trim()
            }
            val part = if (path.startsWith("/api/companies/all")) "names" else if (path.startsWith("/api/companies/counts")) "counts" else "?"
            asked += "$part ${tag ?: "-"}"
            val (etag, body) = when (part) {
                "names" -> namesTag to """{"part":"names","companies":[{"id":"c00000000001","name":"가상화학"},{"id":"c00000000002","name":"가상물류"}]}"""
                "counts" -> countsTag to """{"counts":[{"id":"c00000000001","users":$users},{"id":"c00000000002","users":1}]}"""
                else -> null to """{"error":"not_found"}"""
            }
            val out = s.getOutputStream()
            if (etag != null && tag == etag) {
                out.write("HTTP/1.1 304 Not Modified\r\nETag: $etag\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } else {
                val bytes = body.toByteArray()
                val code = if (etag == null) "404 Not Found" else "200 OK"
                out.write(("HTTP/1.1 $code\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
                    (etag?.let { "ETag: $it\r\n" } ?: "") + "Connection: close\r\n\r\n").toByteArray())
                out.write(bytes)
            }
            out.flush()
        }

        val base get() = "http://127.0.0.1:${socket.localPort}"
    }

    @Test fun aNewRunAsksForTheWholeListWithTheTagsKeptOnThePhone() = runBlocking {
        val server = ListServer()
        val dir = Files.createTempDirectory("n7-kept").toFile()
        try {
            // The first run: both parts come whole, and are kept with their tags.
            val first = CompanyClient(server.base, keepDir = dir).allCompanies(null)!!
            assertEquals(listOf("가상화학" to 3, "가상물류" to 1), first.companies.map { it.hit.name to it.hit.users })
            val tag = first.etag!!
            // A new run (a new client, nothing in memory) with the tag the search kept: both parts are asked with their tags and
            // a 304 for both is "nothing new", the list not sent again.
            server.asked.clear()
            assertNull(CompanyClient(server.base, keepDir = dir).allCompanies(tag))
            assertEquals(setOf("names \"n1\"", "counts \"c1\""), server.asked.toSet())
            // Only the counts changed: they come, and the names kept on the phone are joined with them.
            server.countsTag = "\"c2\""
            server.users = 7
            server.asked.clear()
            val again = CompanyClient(server.base, keepDir = dir).allCompanies(tag)!!
            assertEquals(listOf("가상화학" to 7, "가상물류" to 1), again.companies.map { it.hit.name to it.hit.users })
            assertTrue(again.etag!!.endsWith("\"c2\""))
            assertEquals(setOf("names \"n1\"", "counts \"c1\""), server.asked.toSet())
            // Without a place to keep them (before COMP-45), a new run had no names to join a 304 with: it asked for them whole.
            server.asked.clear()
            CompanyClient(server.base).allCompanies(again.etag)
            assertTrue(server.asked.toString(), "names -" in server.asked)
        } finally {
            server.socket.close()
            dir.deleteRecursively()
        }
    }

    // ---- 나눌게요 and 되돌리기 without a connection (COMP-46·47) -------------------------------------------------------------------

    /** The company server as far as a share goes: [down] for no connection, a report id per send. */
    private class ShareServer : CompanyApi {
        @Volatile var down = false
        val reports = CopyOnWriteArrayList<JSONObject>()
        val takenBack = CopyOnWriteArrayList<String>()
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage = throw CompanyException("offline")
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus {
            if (down) throw CompanyException("offline")
            reports += setup
            return ReportStatus("r${reports.size}", "pending", false, "c1", company, dept, "s${reports.size}", false, 1, 1, 3, false,
                Instant.parse("2026-11-18T00:00:00Z"), null, null, null, null, 0)
        }
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) {
            if (down) throw CompanyException("offline")
            takenBack += id
        }
        override suspend fun forgetMine(device: String): Int = 3
    }

    private lateinit var before: CompanyApi

    @Before fun keep() { before = c.companyApi }

    @After fun put() {
        c.companyApi = before
        c.companyStore.setApplied(null)
        runBlocking { c.payStore.save(null) }
    }

    private suspend fun night(rate: Double) =
        c.payStore.save(c.payStore.profile.value!!.copy(rules = CompanyRules(nightPremium = rate)), keepUndo = false)

    /** A 교대 calendar of (주)예시정밀 whose pay way (야간 50%) the plaza has; each part is asked. */
    private suspend fun shared(server: ShareServer) {
        c.companyApi = server
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(Today.date(), 2_500_000))),
            rules = CompanyRules(nightPremium = 0.5)), keepUndo = false)
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        c.companyStore.setShareWays(ShareWays(ShareWay.ASK, ShareWay.ASK, ShareWay.ASK))
        val now = c.companyShare.exportForServer().toString()
        c.companyStore.autoBase = now
        c.companyStore.markShared(now)
    }

    private suspend fun asked(): AutoShare.Ask {
        c.autoShare.check()
        return withTimeout(5_000) { while (c.autoShare.ask.value == null) delay(10); c.autoShare.ask.value!! }
    }

    private suspend fun said(): AutoShare.Shared = withTimeout(5_000) { while (c.autoShare.shared.value == null) delay(10); c.autoShare.shared.value!! }

    private fun nightOf(file: String?) = JSONObject(file!!).getJSONObject("pay").getJSONObject("rules").getDouble("night")

    @Test fun aYesWithoutAConnectionWaitsOnThePhoneAndItsCancelNeedsNoServer() = runBlocking {
        val server = ShareServer()
        shared(server)
        night(0.6)
        val ask = asked()
        assertEquals(setOf(SharePart.PAY), ask.parts)
        server.down = true
        c.autoShare.approve()
        val line = said()
        // Kept, said to go once online (not '나눴어요'), and held with nothing about the morning.
        assertTrue(line.queued)
        assertTrue(line.offline)
        assertEquals(0L, line.until)
        assertEquals(setOf(SharePart.PAY), c.companyStore.heldParts)
        assertEquals(0.5, nightOf(c.companyStore.plazaHas), 0.0)
        assertTrue(server.reports.isEmpty())
        // 취소 while still offline: nothing goes, nothing is held, and the same values are not asked about again.
        assertEquals(AutoShare.Undone.DROPPED, c.autoShare.undo())
        assertTrue(c.companyStore.heldParts.isEmpty())
        server.down = false
        assertTrue(c.autoShare.flush())
        assertTrue(server.reports.isEmpty())
        c.autoShare.check()
        delay(100)
        assertNull(c.autoShare.ask.value)
    }

    @Test fun aTakeBackWithoutAConnectionWaitsAndGoesOnceOnline() = runBlocking {
        val server = ShareServer()
        shared(server)
        night(0.6)
        asked()
        c.autoShare.approve()
        val line = said()
        assertFalse(line.queued)
        assertEquals(0.6, nightOf(c.companyStore.plazaHas), 0.0)
        assertEquals(1, server.reports.size)
        // 되돌리기 with no connection: the line says it waits, and the plaza is counted as having what it had before.
        server.down = true
        assertEquals(AutoShare.Undone.WAITING, c.autoShare.undo())
        assertEquals(setOf("r1"), c.companyStore.takeBackLater)
        assertEquals(0.5, nightOf(c.companyStore.plazaHas), 0.0)
        assertFalse(c.autoShare.takeBackWaiting())
        // Online: 다시 하기 (or the outbox job) takes it off, and nothing waits.
        server.down = false
        assertTrue(c.autoShare.takeBackWaiting())
        assertEquals(listOf("r1"), server.takenBack)
        assertTrue(c.companyStore.takeBackLater.isEmpty())
    }

    @Test fun afterForgettingEverythingNothingWaitsAndAutoIsAskedAgain() = runBlocking {
        val server = ShareServer()
        shared(server)
        c.companyStore.setShareWays(ShareWays(ShareWay.AUTO, ShareWay.NEVER, ShareWay.AUTO))
        c.companyStore.hold(setOf(SharePart.PAY), "야간 50% → 60%", Today.millis() + AutoShare.DAY)
        val counts = forgetEverythingSent(c)!!
        assertEquals(3, counts.reports)
        assertTrue("the result says 저절로 나누기 went back to 묻기", counts.autoShareOff)
        assertEquals(ShareWays(ShareWay.ASK, ShareWay.NEVER, ShareWay.ASK), c.companyStore.shareWays.value)
        assertTrue(c.companyStore.heldParts.isEmpty())
        assertFalse(c.companyStore.held.value)
        assertNull("the plaza has nothing of this phone", c.companyStore.plazaHas)
        assertFalse(c.inquiryStore.asked)
    }

    // ---- A kind of work's talk kept and read again (COMP-57) -------------------------------------------------------------------

    @Test fun aKindOfWorksTalkComesBackWithEverySignItShowed() = runBlocking {
        val at = Instant.parse("2026-11-18T03:00:00Z")
        val talk = Talk(listOf(
            TalkPost("q1", 1, null, false, "야간 끝나고 바로 자요?", at, false, kind = "question", maker = "야간요정",
                poll = Poll(listOf("바로 자요", "조금 있다 자요"), listOf(6, 4), mine = 1,
                    byForm = listOf(PollSplit(WorkForm.TWO_SHIFT, listOf(4, 1)), PollSplit(null, listOf(2, 3)))),
                first = true, helped = 2, mark = MakerMark.MONTH),
        ), leftPosts = 2, limit = WriteLimit(at.plusSeconds(86_400), 3, "flag", 2, listOf("talk")), topic = "이번 주: 야간 뒤 잠",
            more = true, pulse = Pulse(5, 1, 12, Checkin(mapOf("day" to 3, "night" to 2), "night")))
        val dir = Files.createTempDirectory("n7-talk").toFile()
        try {
            val f = File(dir, "plaza_cache.json")
            val first = PageCache(f, now = { at.toEpochMilli() })
            first.load()
            first.keepTalk("style-shift", "room", talk)
            first.flush()
            val next = PageCache(f, now = { at.toEpochMilli() })
            next.load()
            assertEquals(talk, next.talk("style-shift", "room")!!.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---- A question waiting on the phone (COMP-53) -------------------------------------------------------------------------------

    @Test fun aQuestionWaitingOnThePhoneKeepsAllItWas() {
        val q = QueuedInquiry("request-id-0000009", InquiryKind.STUDIO, "달력에 별 모양 스티커가 있으면 좋겠어요",
            InquiryInfo("2.0.0", "Pixel", "15", diag = "알람 켜짐", public = false, studio = "SHIFT"),
            Instant.parse("2026-11-18T00:00:00Z"), refused = "phone")
        assertEquals(q, QueuedInquiry.of(JSONObject(q.toJson().toString())))
        assertNotNull(QueuedInquiry.of(q.toJson()))
    }

    /** COMP-54: a question the server refused for what it says waits to be fixed; 지금 보내기 sends the rest and says so. */
    @Test fun aRefusedQuestionIsNotSentAgainAndTheSendSaysWhatWent() = runBlocking {
        val sent = CopyOnWriteArrayList<String>()
        val before = c.inquiryApi
        c.inquiryApi = object : InquiryApi by com.bamnat.app.screenshots.ScreenshotBase.FakeInquiryApi() {
            override suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo, requestId: String): Inquiry {
                sent += requestId
                return Inquiry(requestId, kind, "open", Today.instant(), Today.instant(), listOf(InquiryMessage(false, text, Today.instant())))
            }
        }
        try {
            val info = InquiryInfo("2.0.0", "Pixel", "15")
            c.inquiryStore.queue(QueuedInquiry("request-id-0000001", InquiryKind.BUG, "알람이 늦게 울려요", info, Today.instant()))
            c.inquiryStore.queue(QueuedInquiry("request-id-0000002", InquiryKind.BUG, "010-1234-5678로 연락 주세요", info, Today.instant()))
            c.inquiryStore.refused("request-id-0000002", "phone")
            val r = InquiryOutbox.flushSaying(c)
            assertEquals(OutboxResult(1, 1, 0), r)
            assertEquals(listOf("request-id-0000001"), sent)
            assertEquals("보낸 문의 1개 · 고쳐서 보낼 문의 1개: 아래 까닭을 보고 고쳐 주세요", r.words)
            assertEquals(listOf("request-id-0000002"), c.inquiryStore.outbox.value.map { it.requestId })
            // Tried again, it still waits for the user and nothing is sent.
            assertEquals(OutboxResult(0, 1, 0), InquiryOutbox.flushSaying(c))
            assertEquals(1, sent.size)
        } finally {
            c.inquiryApi = before
            c.inquiryStore.unqueue("request-id-0000002")
        }
    }

    /** COMP-59: a vote carries the kind of work and 근무형태 only, never the months, 업종 or 근무 중 of the tag. */
    @Test fun aVoteCarriesOnlyTheKindAndTheForm() = runBlocking {
        val client = CompanyClient("")
        client.roomTag = { JSONObject().put("s", "SHIFT").put("f", "TWO_SHIFT").put("m", 14).put("i", "factory").put("now", true) }
        val tag = client.voteTag("style-shift")!!
        assertEquals(setOf("s", "f"), tag.keys().asSequence().toSet())
        assertEquals("TWO_SHIFT", tag.getString("f"))
        assertNull("a company's room has no tag", client.voteTag("c00000000001"))
        client.roomTag = { JSONObject().put("s", "SHIFT").put("m", 14) }
        assertNull("without a 근무형태 nothing goes", client.voteTag("style-shift"))
    }

    /** T10b-05: the whole list of companies is read ahead (and asked of the digest) only when a calendar is offered company things. */
    @Test fun onlyACalendarOfferedCompaniesReadsTheirList() {
        c.companyApi = ShareServer()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        assertTrue(c.companyCalendars())
        assertTrue("companies" in PlazaNewsJob.digestParts(c, plazaOn = false))
        for (style in listOf(WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST)) {
            c.settingsRepository.update { it.copy(workStyle = style) }
            assertFalse(style.name, c.companyCalendars())
            assertFalse(style.name, "companies" in PlazaNewsJob.digestParts(c, plazaOn = false))
        }
    }
}
