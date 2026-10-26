package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * AG 1.27 [INVITE-10][INVITE-09][INVITE-13] 친구 초대 recognised by itself, from the app's own code to the real company
 * server (server/company-worker on Node with SQLite) and back (사용자 리뷰 2026-10-25: "애초에 초대로 다운을 받는것을 자동으로
 * 인식하게 하지 못하는지?"). The inviter's link hands Play the code; the friend's phone reads Play's referrer while its first
 * setup is still going (nothing sent), the last setup button tells the inviter (결정 AG-D2) with no card on the calendar
 * to find, a phone without internet then sends it by itself later, and seven days on by the server's clock the
 * inviter reads '1/5명' and '다음 프로까지 4명'. A code by hand is not offered once the referrer brought one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InviteAutoAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun clock() = Today.reset()

    private class Phone(val invites: Invites, var setupDone: Boolean, var url: String)

    private fun phone(url: String, name: String, device: String, androidId: String, referrer: () -> String? = { null }): Phone {
        lateinit var p: Phone
        val invites = Invites(
            context, InviteStore(context, "-ag-$name"), { InviteClient(p.url) }, { device }, { PhoneMark.hash(androidId) },
            setupDone = { p.setupDone }, installedAt = { Today.instant().minus(Duration.ofHours(1)) }, referrer = { ReferrerSource { referrer() } },
        )
        p = Phone(invites, setupDone = true, url = url)
        return p
    }

    @Test fun aFriendWhoCameByTheLinkCountsWithoutFindingACard() = withServer { url ->
        Today.use(Clock.systemUTC())
        val inviter = phone(url, "a", "test-device-ag-inviter-01", "android-ag-a")
        val made = inviter.invites.makeLink()
        val page = get(made.link!!)
        val store = Regex("""https://play\.google\.com/store/apps/details\?id=com\.bamnat\.app&referrer=([^"&]+)""").find(page)
        assertNotNull(page, store)
        val referrer = URLDecoder.decode(store!!.groupValues[1], "UTF-8")
        // [INVITE-13] The messenger's own browser goes to the Play app with the same referrer.
        assertTrue(page.contains("intent://details?id=com.bamnat.app&referrer=" + java.net.URLEncoder.encode(referrer, "UTF-8").replace("+", "%20")))

        // The friend's phone, still in its first setup: Play's referrer is read on the phone, nothing is sent.
        val friend = phone(url, "b", "test-device-ag-friend-001", "android-ag-b") { referrer }
        friend.setupDone = false
        assertEquals(made.code, friend.invites.peek())
        assertEquals(0, inviter.invites.refresh()!!.waiting)
        // [INVITE-10] 결정 AG-D3: a link's code is kept; no other code can be typed over it.
        friend.setupDone = true
        assertFalse(friend.invites.mayType())
        friend.setupDone = false
        // The last setup button, '저장하고 초대한 분께 알리기': sent at once, before the calendar is ever drawn.
        val told = friend.invites.agree(made.code!!)
        assertEquals("waiting" to 7, told?.status to told?.daysLeft)
        assertNull("no calendar card to find", friend.invites.offer())
        assertEquals("waiting", friend.invites.relay()?.answer?.status)
        var counts = inviter.invites.refresh()!!
        assertEquals("0/5명 · 1명은 사용 기간 채우는 중", InviteRules.progress(counts.progress, counts.terms.need, counts.waiting))

        // [INVITE-13] A second friend agreed with no internet: it waits on the phone and goes by itself next time.
        val second = phone("http://127.0.0.1:1", "c", "test-device-ag-friend-002", "android-ag-c") { referrer }
        second.setupDone = false
        assertEquals(made.code, second.invites.peek())
        assertNull(second.invites.agree(made.code!!))
        assertTrue(second.invites.store.current.agreed)
        assertNull(second.invites.store.current.reportedCode)
        second.url = url
        second.setupDone = true
        second.invites.sync()
        assertEquals("waiting", second.invites.store.current.answer?.status)
        assertFalse(second.invites.store.current.agreed)
        counts = inviter.invites.refresh()!!
        assertEquals(2, counts.waiting)

        // Seven days on, by the server's clock and the phone's: the first friend opens again and counts.
        moveServerClock(url, days = 7)
        Today.use(Clock.offset(Clock.systemUTC(), Duration.ofDays(7)))
        friend.setupDone = true
        friend.invites.sync()
        assertEquals("qualified", friend.invites.store.current.answer?.status)
        counts = inviter.invites.refresh()!!
        assertEquals("1/5명 · 1명은 사용 기간 채우는 중", InviteRules.progress(counts.progress, counts.terms.need, counts.waiting))
        assertEquals("다음 프로까지 4명", InviteRules.toNext(counts.progress, counts.terms.need))
        // [INVITE-09] One friend, however long it uses the app, gives no month.
        assertEquals(0, counts.given)
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun moveServerClock(url: String, days: Int) {
        val conn = URL("$url/__test/clock?days=$days").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        assertEquals(204, conn.responseCode)
        conn.disconnect()
    }

    /** Runs server/company-worker on Node with SQLite and its test clock for [block]. Skipped where Node is missing. */
    private fun withServer(block: suspend (String) -> Unit) = runBlocking {
        val dir = File("../server/company-worker")
        assumeTrue(dir.resolve("test/local-server.mjs").exists())
        assumeTrue(runCatching { ProcessBuilder("node", "--version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        val port = ServerSocket(0).use { it.localPort }
        val node = ProcessBuilder("node", "test/local-server.mjs", port.toString(), "--test-clock").directory(dir).redirectErrorStream(true).start()
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
