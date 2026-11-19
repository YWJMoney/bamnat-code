package com.bamnat.app.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.inbox.Inbox
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVL-78] (검토 I12c-02 '받는 쪽이 끊기지 않는 공유') A received link about to stop says so with the message to the sender one press
 * away; a link of the sender's that stopped by itself is not forgotten without a word.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RvlShareHealthTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val now: Instant = Today.instant()
    private val today: LocalDate = Today.date()

    private fun body(title: String, days: List<LocalDate>) = JSONObject()
        .put("title", title)
        .put("types", JSONArray().put(JSONObject().put("id", 1).put("name", "주간").put("short", "주").put("color", "#1E88E5")))
        .put("days", JSONArray().apply { days.forEach { put(JSONObject().put("date", it.toString()).put("type", 1)) } })

    private fun cal(title: String = "민지", ends: Duration = Duration.ofDays(30), last: Long = 40, gone: Boolean = false,
                    removed: String? = null, updated: Instant? = now.minus(Duration.ofDays(3))) =
        ReceivedCalendar("Mj7dEfGh5JkLmNoPqRsTuv", title, ReceivedCalendar.COLORS[0], now, now, updated, now.plus(ends),
            if (gone) null else body(title, (0..last).map { today.plusDays(it) }), removedReason = removed)

    @Test fun aLinkInGoodHealthSaysNothing() {
        assertEquals(LinkHealth.Kind.OK, LinkHealth.of(cal(), now, today).kind)
    }

    @Test fun aLinkEndingWithinTwoDaysAsksTheSenderToKeepItOpen() {
        val h = LinkHealth.of(cal(ends = Duration.ofHours(30)), now, today)
        assertEquals(LinkHealth.Kind.ENDS_SOON, h.kind)
        assertTrue(h.line!!, h.line.startsWith("이 링크는"))
        assertEquals("연장 부탁하기", h.action)
        assertTrue(h.ask!!, h.ask.contains("‘민지’ 근무표") && h.ask.contains("기간 늘리기"))
        // Three days out the sender's own reminder is still to come (프로 keeps it open by itself then): not yet.
        assertEquals(LinkHealth.Kind.OK, LinkHealth.of(cal(ends = Duration.ofDays(3)), now, today).kind)
    }

    @Test fun daysRunningOutWithNothingAfterAskForTheNextOne() {
        val h = LinkHealth.of(cal(last = 3), now, today)
        assertEquals(LinkHealth.Kind.RUNS_OUT, h.kind)
        val last = today.plusDays(3)
        assertEquals("${last.monthValue}월 ${last.dayOfMonth}일 뒤로는 아직 없어요", h.line)
        assertTrue(h.ask!!, h.ask.contains("‘민지’ 근무표가 ${last.monthValue}월 ${last.dayOfMonth}일까지만"))
        // The stamp is the last day: closed for this one, the next roster's own end shows again.
        assertEquals("last:$last", h.stamp)
    }

    @Test fun aGoneLinkAsksForANewOneButNotOneTheDeveloperTookDown() {
        val gone = LinkHealth.of(cal(gone = true), now, today)
        assertEquals(LinkHealth.Kind.GONE, gone.kind)
        assertTrue(gone.ask!!.contains("새 링크"))
        assertNull(LinkHealth.of(cal(gone = true, removed = "운영 규칙"), now, today).ask)
        // A link without a name is 받은 근무표 in the message, never ‘’.
        assertTrue(LinkHealth.of(cal(title = "", gone = true), now, today).ask!!.contains("받은 근무표 링크"))
    }

    @Test fun theLineSaysWhenTheSenderLastChangedIt() {
        val line = LinkHealth.line(cal(updated = now), today)
        assertTrue(line, line.startsWith("보낸 분이 마지막으로 고친 날 오늘"))
        assertTrue(line, line.contains("까지 볼 수 있어요"))
        assertTrue(!LinkHealth.line(cal(updated = null), today).contains("고친 날"))
    }

    // The sender's side.

    private class GoneApi : ShareApi {
        override val enabled = true
        override suspend fun create(payload: JSONObject) = error("not used")
        override suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState = throw ShareLinkException("not_found")
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String) = error("not used")
    }

    private fun link(expires: Instant = now.plus(Duration.ofDays(20))) =
        ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://share.example/s/Ab3dEfGh5JkLmNoPqRsTuv", "token", expires, "민지", 30, YearMonth.from(today))

    @Before fun setUp() {
        c.shareLinks.clear()
        c.shareLinks.auto = true
        Inbox.get(app).clearAll()
        runBlocking {
            c.scheduleRepository.ensureDefaults()
            val day = c.scheduleRepository.shiftTypes.first().first { !it.isOff }.id
            c.scheduleRepository.setOverride(today.plusDays(1), ShiftOverride.Shift(day))
        }
    }

    @After fun tearDown() { c.shareLinks.clear(); Inbox.get(app).clearAll() }

    @Test fun aLinkTheServerNoLongerHasIsSaidInTheSheetAndTheInboxOnce() = runBlocking {
        c.shareApi = GoneApi()
        c.shareLinks.save(link())
        assertEquals(ShareSync.Result.Gone, ShareSync(c).sync())
        assertNull(c.shareLinks.link.value)
        assertNotNull("the share sheet says it ended", c.shareLinks.endedAt.value)
        val rows = Inbox.get(app).rows.value.filter { it.kind == ShareExpiryReminder.ENDED_KIND }
        assertEquals(1, rows.size)
        assertTrue(rows.single().title, rows.single().title.endsWith("끝났어요"))
        // Asked again (the next change, the app opened again): not put twice.
        ShareSync(c).sync()
        assertEquals(1, Inbox.get(app).rows.value.count { it.kind == ShareExpiryReminder.ENDED_KIND })
        // A new link mends it.
        c.shareLinks.save(link())
        assertNull(c.shareLinks.endedAt.value)
    }

    @Test fun aLinkThatRanOutByItsDateIsSaidToo() = runBlocking {
        c.shareLinks.save(link(expires = now.minus(Duration.ofHours(1))))
        val store = ShareLinkStore(app)
        assertNull(store.link.value)
        assertEquals(now.minus(Duration.ofHours(1)), store.endedAt.value)
    }

    @Test fun aLinkTheSharerCutIsNotSaidToHaveEnded() = runBlocking {
        c.shareLinks.save(link())
        c.shareLinks.clear()
        assertNull(c.shareLinks.endedAt.value)
        assertNull(ShareLinkStore(app).endedAt.value)
    }
}
