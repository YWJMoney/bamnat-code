package com.bamnat.app.team

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.SeenOk
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.team.TeamReceiveSync.LinkSeen
import com.bamnat.core.team.TeamMember
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * 1.39 G2 (PC a483ca15 '0/0명 · 모두 봤어요', '계속 0/2'): a link the server no longer has is said as gone with a way to send a new one,
 * never counted as seen, and no internet is not "모두 봤어요" either.
 * 2.0.0 [RVQ-32] 검토 U22-01: 봤어요 is 열어 봄 (a person opened it, never a messenger's preview), and when the share server says 확인
 * the line reads '확인 3/8명 · 열어 봄 5/8명'.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TeamSeenTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val at = Instant.parse("2026-12-01T09:00:00Z")

    private open class Api(val answers: Map<String, () -> Instant?>) : ShareApi {
        override val enabled = true
        override suspend fun create(payload: JSONObject) = error("not used")
        override suspend fun update(state: ShareLinkState, payload: JSONObject) = error("not used")
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String): ReceivedShare = error("not used")
        override suspend fun seen(id: String, token: String): Instant? = answers.getValue(id)()
    }

    private fun m(id: String, name: String, link: String? = "L$id") = TeamMember(id, "t", name, linkId = link, linkToken = link?.let { "k" })

    @Test fun `each link says seen, not yet, gone or not reached`() = runBlocking {
        c.shareApi = Api(mapOf("La" to { at }, "Lb" to { null }, "Lc" to { throw ShareLinkException("not_found") },
            "Ld" to { throw ShareLinkException("forbidden") }, "Le" to { throw IOException("offline") }))
        val got = TeamReceiveSync.seen(c, listOf(m("a", "민지"), m("b", "수아"), m("c", "지호"), m("d", "하늘"), m("e", "도윤"), m("f", "링크 없음", null)))
        assertEquals(mapOf("a" to LinkSeen.At(at), "b" to LinkSeen.Not, "c" to LinkSeen.Gone, "d" to LinkSeen.Gone, "e" to LinkSeen.Unknown), got)
    }

    @Test fun `every link gone is not 0 of 0 all seen`() {
        val people = listOf(m("a", "민지"), m("b", "수아"))
        val line = TeamReceiveSync.seenLine(3, people, mapOf("a" to LinkSeen.Gone, "b" to LinkSeen.Gone))
        assertFalse(line.text, line.text.contains("모두"))
        assertFalse(line.text, line.text.contains("0/0"))
        assertTrue(line.text, line.text.startsWith("링크가 없어요: 민지, 수아"))
        assertEquals(listOf("a", "b"), line.gone.map { it.id })
    }

    @Test fun `some seen, some not yet, one gone`() {
        val people = listOf(m("a", "민지"), m("b", "수아"), m("c", "지호"))
        val line = TeamReceiveSync.seenLine(2, people, mapOf("a" to LinkSeen.At(at), "b" to LinkSeen.Not, "c" to LinkSeen.Gone))
        assertEquals("2판 링크를 열어 본 사람 1/2명 · 아직 안 연 사람: 수아\n링크가 없어요: 지호 · 기간이 지났거나 지워졌어요. 새 링크를 보내 주세요", line.text)
        assertEquals(listOf("b"), line.notSeen.map { it.id })
    }

    @Test fun `all seen says so, and no internet says to try again`() {
        val people = listOf(m("a", "민지"), m("b", "수아"))
        assertEquals("1판 링크를 열어 본 사람 2/2명 · 모두 열어 봤어요",
            TeamReceiveSync.seenLine(1, people, mapOf("a" to LinkSeen.At(at), "b" to LinkSeen.At(at))).text)
        val off = TeamReceiveSync.seenLine(1, people, mapOf("a" to LinkSeen.Unknown, "b" to LinkSeen.Unknown))
        assertTrue(off.text, off.text.startsWith("열어 본 사람을 읽지 못했어요"))
        assertEquals("1판 링크를 열어 본 사람 1/1명\n못 읽은 1명",
            TeamReceiveSync.seenLine(1, people, mapOf("a" to LinkSeen.At(at), "b" to LinkSeen.Unknown)).text)
    }

    @Test fun `the share server's 확인 comes with each link`() = runBlocking {
        val ok = Instant.parse("2026-12-01T10:00:00Z")
        c.shareApi = object : Api(emptyMap()) {
            override suspend fun seenOk(id: String, token: String) = when (id) {
                "La" -> SeenOk(at, ok, okKnown = true)
                "Lb" -> SeenOk(at, null, okKnown = true)
                else -> SeenOk(null, null, okKnown = true)
            }
        }
        val got = TeamReceiveSync.seen(c, listOf(m("a", "민지"), m("b", "수아"), m("c", "지호")))
        assertEquals(mapOf("a" to LinkSeen.At(at, ok, true), "b" to LinkSeen.At(at, null, true), "c" to LinkSeen.Not), got)
    }

    @Test fun `확인 and 열어 봄 side by side, all confirmed says so`() {
        val ok = Instant.parse("2026-12-01T10:00:00Z")
        val people = listOf(m("a", "민지"), m("b", "수아"), m("c", "지호"))
        val line = TeamReceiveSync.seenLine(4, people, mapOf("a" to LinkSeen.At(at, ok, true), "b" to LinkSeen.At(at, null, true), "c" to LinkSeen.Not))
        assertEquals("4판 확인 1/3명 · 열어 봄 2/3명 · 아직 안 연 사람: 지호", line.text)
        // I22b-01 '앱은 확인만 크게': the 확인 count is its own big line, the rest under it.
        assertEquals("4판 확인 1/3명", line.big)
        assertEquals("열어 봄 2/3명 · 아직 안 연 사람: 지호", line.rest)
        val old = TeamReceiveSync.seenLine(4, people, mapOf("a" to LinkSeen.At(at), "b" to LinkSeen.Not, "c" to LinkSeen.Not))
        assertEquals(null, old.big)
        assertEquals(old.text, old.rest)
        // Everyone opened, not everyone pressed 확인했어요: no '모두' yet.
        assertEquals("4판 확인 1/2명 · 열어 봄 2/2명",
            TeamReceiveSync.seenLine(4, people.take(2), mapOf("a" to LinkSeen.At(at, ok, true), "b" to LinkSeen.At(at, null, true))).text)
        assertEquals("4판 확인 2/2명 · 열어 봄 2/2명 · 모두 확인했어요",
            TeamReceiveSync.seenLine(4, people.take(2), mapOf("a" to LinkSeen.At(at, ok, true), "b" to LinkSeen.At(at, ok, true))).text)
    }
}
