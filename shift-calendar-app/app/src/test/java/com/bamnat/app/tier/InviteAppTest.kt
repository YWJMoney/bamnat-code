package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.tier.InviteTerms
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * [INVITE-02][INVITE-03][INVITE-04][INVITE-05][INVITE-08] 친구 초대 from the app's own code to the real company server
 * (server/company-worker on Node with SQLite) and back: the developer app sets the rules (2 friends, 7 days), the
 * inviter's phone reads them, makes a link, the link's page hands Play the code as the install referrer, two
 * friends' phones read it after their first setup, report it, open again seven days on by the server's clock, and
 * the inviter's progress, 프로 passes and counts show the month. The same phone installed again does not count twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InviteAppTest {
    private companion object {
        const val PASSWORD = "local-invite-test-only"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun clock() = Today.reset()

    private fun phone(url: String, name: String, device: String, androidId: String, referrer: () -> String? = { null }) = Invites(
        context, InviteStore(context, "-$name"), { InviteClient(url) }, { device }, { PhoneMark.hash(androidId) },
        setupDone = { true }, installedAt = { Today.instant().minus(Duration.ofHours(1)) }, referrer = { ReferrerSource { referrer() } },
    )

    @Test fun twoFriendsWhoUseTheAppSevenDaysGiveTheInviterAMonth() = withServer { url ->
        Today.use(Clock.systemUTC())
        val inviter = phone(url, "a", "test-device-inviter-0001", "android-a")
        // The developer app's 설정 › 친구 초대, applied by the server and read by the app before anything is sent.
        setRules(url, """{"friends":2,"days":7}""")
        inviter.refresh()
        // 2.0.0 [RVD-21] Until 처리방침 3.13 is in the calendar the year's cap is not in force (약관 1.3·처리방침 3.12 promise none),
        // and the server says so: 0, which the card words as '상한 없음'.
        assertEquals(InviteTerms(need = 2, days = 7, months = 1, capMonths = 0), inviter.store.current.rules)
        // The owner saves 3.13 in 기능 관리 › 일정 as posted a month and a half back, so its 30-day date has passed.
        // [RVD-15] Now the server says the year's cap: 12 months of rewards, all kinds together.
        setPolicy(url, "3.13", posted = LocalDate.now(ZoneOffset.UTC).minusDays(45).toString())
        inviter.refresh()
        assertEquals(InviteTerms(need = 2, days = 7, months = 1, capMonths = 12), inviter.store.current.rules)
        val made = inviter.makeLink()
        val code = made.code!!
        assertEquals("$url/i/$code", made.link)

        // The link's page sends Android on to Play with the code as the install referrer.
        val page = get(made.link!!)
        val store = Regex("""https://play\.google\.com/store/apps/details\?id=com\.bamnat\.app&referrer=([^"&]+)""").find(page)
        assertNotNull(page, store)
        val referrer = URLDecoder.decode(store!!.groupValues[1], "UTF-8")
        assertEquals(code, InviteRules.fromReferrer(referrer))

        // The friend's phone, after its first setup: the referrer is read and waits for 알리기.
        val friend = phone(url, "b", "test-device-friend-00001", "android-b") { referrer }
        friend.sync()
        assertEquals(code, friend.store.current.found)
        val told = friend.report(friend.offer()!!, "referrer")
        assertEquals("waiting" to 7, told.status to told.daysLeft)
        val second = phone(url, "e", "test-device-friend-00003", "android-e") { referrer }
        second.sync()
        second.report(second.offer()!!, "referrer")
        // The same day nothing more is sent, and nothing counts.
        friend.sync()
        var counts = inviter.refresh()!!
        assertEquals(0 to 2, counts.friends to counts.waiting)
        assertEquals("0/2명 · 2명은 사용 기간 채우는 중", InviteRules.progress(counts.progress, counts.terms.need, counts.waiting))

        // Six days on is not yet the period.
        moveServerClock(url, days = 6)
        Today.use(Clock.offset(Clock.systemUTC(), Duration.ofDays(6)))
        friend.sync()
        assertEquals("waiting" to 1, friend.store.current.answer?.status to friend.store.current.answer?.daysLeft)
        // Seven days on, by the server's clock and the phone's: the first friend counts toward the next month.
        moveServerClock(url, days = 1)
        Today.use(Clock.offset(Clock.systemUTC(), Duration.ofDays(7)))
        friend.sync()
        assertEquals("qualified", friend.store.current.answer?.status)
        counts = inviter.refresh()!!
        assertEquals("1/2명 · 1명은 사용 기간 채우는 중", InviteRules.progress(counts.progress, counts.terms.need, counts.waiting))
        // The second one makes two: the month, which waits for the operator (2.0.0 [RVD-14], 사용자 결정 2026-11-12 '확인 뒤 지급').
        second.sync()
        assertEquals("credited", second.store.current.answer?.status)
        counts = inviter.refresh()!!
        assertEquals(InviteRules.summary(2, 0), InviteRules.summary(counts.friends, counts.given))
        assertEquals(1, counts.pending)
        assertEquals("0/2명", InviteRules.progress(counts.progress, counts.terms.need, counts.waiting))
        var passes = TierClient(url).passes("test-device-inviter-0001")
        assertEquals(emptyList<Pair<Int, String>>(), passes.items.map { it.months to it.why })
        assertEquals(listOf(1 to "invite"), passes.pending.map { it.months to it.why })
        // The developer app's 보상 › 지급 대기: the admin looks and pays it.
        val cookie = signIn(url)
        val owed = Regex(""""id":"(i[A-Za-z0-9]{20,24})"""").find(get("$url/api/admin/rewards?status=owed", cookie))!!.groupValues[1]
        post("$url/api/admin/rewards/$owed", """{"status":"paid"}""", cookie).disconnect()
        // Now the month is one of the inviter's reward passes, named as an invite, waiting for the launch.
        passes = TierClient(url).passes("test-device-inviter-0001")
        assertEquals(listOf(1 to "invite"), passes.items.map { it.months to it.why })
        assertEquals(1, passes.waitingMonths)
        assertEquals(1, inviter.refresh()!!.given)

        // The friend installs again (a new install id) on the same phone: it does not count twice.
        val again = phone(url, "c", "test-device-friend-00002", "android-b") { referrer }
        again.sync()
        val answer = again.report(again.offer()!!, "referrer")
        assertEquals("refused" to "phone", answer.status to answer.reason)
        // Nor does the inviter's own phone.
        val self = phone(url, "d", "test-device-inviter-0002", "android-a") { referrer }
        self.sync()
        assertEquals("self", self.report(code, "typed").reason)
        assertEquals(2, inviter.refresh()!!.friends)
    }

    /** Signs in to the developer app on the local server, as its 설정 screen does, and saves the invite rules. */
    private fun setRules(url: String, body: String) {
        val saved = post("$url/api/admin/settings/invite", body, signIn(url))
        assertEquals(200, saved.responseCode)
        saved.disconnect()
    }

    /** The owner's 기능 관리 › 일정 › 처리방침 일정 on the local server: [version] posted and in force on [posted]. */
    private fun setPolicy(url: String, version: String, posted: String) {
        val body = """{"version":"$version","posted":"$posted","effective":"$posted","heldUntil":null,"unfavourable":true,"why":"시험: 처리방침 $version 일정"}"""
        post("$url/api/admin/policy", body, signIn(url)).disconnect()
    }

    private fun post(url: String, body: String, cookie: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-admin", "1")
        cookie?.let { conn.setRequestProperty("cookie", it) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        assertEquals(url, 200, conn.responseCode)
        return conn
    }

    /** The developer app's sign-in on the local server: its session cookie. */
    private fun signIn(url: String): String {
        val login = post("$url/api/admin/login", """{"password":"$PASSWORD"}""", null)
        return login.getHeaderField("set-cookie")!!.substringBefore(';').also { login.disconnect() }
    }

    private fun get(url: String, cookie: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        cookie?.let { conn.setRequestProperty("cookie", it); conn.setRequestProperty("x-admin", "1") }
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
        val node = ProcessBuilder("node", "test/local-server.mjs", port.toString(), "--test-clock").directory(dir).redirectErrorStream(true)
            // A made-up password for this throwaway local server only, so the test can use the developer app's settings.
            .apply { environment()["ADMIN_PASSWORD"] = PASSWORD }.start()
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
        assertTrue(true)
    }
}
