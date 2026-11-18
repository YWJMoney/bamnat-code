package com.bamnat.app.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.ui.components.NetWords
import com.bamnat.app.ui.setup.LinkStart
import com.bamnat.core.model.ShiftIcon
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.Duration
import java.time.LocalTime

/**
 * 2.0.0 검토 PC 2차(ac6c21ce) 갈래 G: [RVG-24] PC-92 실패 글에 서버 코드 없음, [RVG-27] SN-13 서버 거절과 끊김 나누기 (P-13 재현),
 * [RVG-28] PC-94 휴게가 링크로 가고 옴, [RVG-30] PC-97 적던 이름, [RVG-31] PC-95 넣기 되돌리기, [RVG-32] PC-93 이름 없는 링크 말.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RvgSharePc2Test {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val server = Server(oldRules = true)

    /**
     * A share server that checks a 근무 그림 the way 1.34~1.39's did (an icon name only, so 'e:☀️' is turned down with type_icon) or
     * the way 2.0.0's does ([oldRules] false); [down] fails every call the way a busy or refusing server does.
     */
    private class Server(var oldRules: Boolean) : ShareApi {
        override val enabled = true
        val got = mutableListOf<JSONObject>()
        var down: Exception? = null
        /** Every call, the turned-down ones too. */
        var calls = 0
        /** A server that will not take a break (as one before RVD-23 might read it), after the 그림 check. */
        var refusesRest = false
        private fun check(p: JSONObject) {
            calls++
            down?.let { throw it }
            val types = p.optJSONArray("types") ?: return
            for (i in 0 until types.length()) {
                val icon = types.getJSONObject(i).optString("icon", "")
                if (oldRules && icon.isNotEmpty() && !Regex("^[a-z0-9]{1,16}$").matches(icon)) throw ShareLinkException("type_icon")
            }
            for (i in 0 until types.length()) if (refusesRest && types.getJSONObject(i).has("rest")) throw ShareLinkException("type_rest")
        }
        override suspend fun create(payload: JSONObject): ShareLinkState {
            check(payload); got += payload
            return ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://share.example/s/Ab3dEfGh5JkLmNoPqRsTuv", "token",
                Today.instant().plus(Duration.ofDays(30)), payload.optString("title"), 30, Today.month())
        }
        override suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState {
            check(payload); got += payload
            return state.copy(title = payload.optString("title"))
        }
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String) = error("not used")
    }

    private fun link() = ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://share.example/s/Ab3dEfGh5JkLmNoPqRsTuv", "token",
        Today.instant().plus(Duration.ofDays(20)), "민지", 30, Today.month())

    @Before fun setUp() {
        c.shareApi = server
        c.shareLinks.clear()
        c.shareLinks.auto = true
        runBlocking {
            c.scheduleRepository.ensureDefaults()
            val day = c.scheduleRepository.shiftTypes.first().first { !it.isOff }.id
            c.scheduleRepository.setOverride(Today.date().plusDays(1), ShiftOverride.Shift(day))
        }
    }

    @After fun tearDown() {
        com.bamnat.core.time.Today.reset()
        c.shareLinks.clear()
        c.shareLinks.typedName = null
    }

    private suspend fun dayType() = c.scheduleRepository.shiftTypes.first().first { !it.isOff }

    private fun JSONObject.icons() = (0 until getJSONArray("types").length()).mapNotNull { getJSONArray("types").getJSONObject(it).optString("icon").ifEmpty { null } }

    /**
     * 검토 보고서 9장 P-13 / SN-13: a link made, the 근무 given an emoji 그림 (a 작품's or the 근무 종류 editor's), a day changed. Before,
     * the old share server turned the change down with type_icon, the phone took that for no internet, and the share card said
     * '인터넷이 되면 저절로 보내요' for ever while the family saw the old 근무. Now the change goes without the 그림, said once.
     */
    @Test fun anEmojiPictureOnAnOldServerGoesWithoutItAndTheChangeArrives() = runBlocking {
        c.shareLinks.save(link())
        c.scheduleRepository.saveShiftType(dayType().copy(icon = ShiftIcon.emojiKey("☀️")))
        c.scheduleRepository.setOverride(Today.date().plusDays(2), ShiftOverride.Shift(dayType().id))
        val sync = ShareSync(c)
        assertEquals(ShareSync.Result.Sent, sync.sync())
        val went = server.got.single()
        assertTrue("the 그림 the old server refuses stayed home", went.icons().none { it.startsWith("e:") })
        val dates = (0 until went.getJSONArray("days").length()).map { went.getJSONArray("days").getJSONObject(it).getString("date") }
        assertTrue("the changed day reached the link", Today.date().plusDays(2).toString() in dates)
        assertFalse("not waiting as if offline", c.shareLinks.unsent.value)
        assertFalse(c.shareLinks.refused.value)
        val said = c.shareLinks.dropped.value
        assertNotNull("said once above the calendar", said)
        assertTrue(said!!, "☀️" in said && "빼고 보냈어요" in said)
        // Not tried again and again: nothing changed since.
        assertEquals(ShareSync.Result.Same, sync.sync())
        assertEquals(1, server.got.size)
        // Closed by the sender.
        c.shareLinks.setDropped(null)
        assertNull(c.shareLinks.dropped.value)

        // The 2.0.0 share server (RVD-22, 46d91ace) takes the 그림. The same day the 그림 is still left out at once (one call, not
        // turned down again, nothing said again); a day on the whole is tried again and the next change carries it, nothing said.
        server.oldRules = false
        c.scheduleRepository.setOverride(Today.date().plusDays(3), ShiftOverride.Shift(dayType().id))
        val before = server.calls
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertEquals(before + 1, server.calls)
        assertTrue(server.got.last().icons().none { it.startsWith("e:") })
        Today.use(java.time.Clock.offset(Today.clock, Duration.ofDays(1).plusMinutes(1)))
        c.scheduleRepository.setOverride(Today.date().plusDays(4), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertEquals(listOf("e:☀️"), server.got.last().icons().filter { it.startsWith("e:") })
        assertNull(c.shareLinks.dropped.value)
        assertTrue("the server took everything: nothing is left out any more", c.shareLinks.leaveOut().isEmpty())
    }

    /**
     * 갈래 R 의견 1: two parts the server turns down (an emoji 그림 on an old server and a break it will not read) both go without, one at
     * a time, and the 근무 still arrives; the sender gets one line for both, not two cards.
     */
    @Test fun twoPartsTurnedDownBothGoWithoutAndAreSaidInOneLine() = runBlocking {
        c.shareLinks.save(link())
        server.refusesRest = true
        c.scheduleRepository.saveShiftType(dayType().copy(icon = ShiftIcon.emojiKey("☀️"), breakMinutes = 60))
        c.scheduleRepository.setOverride(Today.date().plusDays(2), ShiftOverride.Shift(dayType().id))
        val sync = ShareSync(c)
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertEquals("whole, without the 그림, without the break too", 3, server.calls)
        val went = server.got.single()
        assertTrue(went.icons().none { it.startsWith("e:") })
        assertTrue((0 until went.getJSONArray("types").length()).none { went.getJSONArray("types").getJSONObject(it).has("rest") })
        assertEquals("근무 그림 ☀️·휴게 시간은 링크에 못 실어 빼고 보냈어요.", c.shareLinks.dropped.value)
        assertEquals(setOf("type_icon", "type_rest"), c.shareLinks.leaveOut())
        assertFalse(c.shareLinks.unsent.value)
    }

    /**
     * 갈래 R 의견 2: once a part was turned down, every later change goes without it in one call; the line closed by the sender does not
     * come back for each change. A sheet that made the link said it there, so it is not said again above the calendar.
     */
    @Test fun aPartTurnedDownGoesWithoutAtOnceAfterwardsAndIsNotSaidAgain() = runBlocking {
        c.shareLinks.save(link())
        c.scheduleRepository.saveShiftType(dayType().copy(icon = ShiftIcon.emojiKey("☀️")))
        val sync = ShareSync(c)
        c.scheduleRepository.setOverride(Today.date().plusDays(2), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertEquals(2, server.calls)
        c.shareLinks.setDropped(null)
        for (day in 3..5) {
            c.scheduleRepository.setOverride(Today.date().plusDays(day.toLong()), ShiftOverride.Shift(dayType().id))
            val before = server.calls
            assertEquals(ShareSync.Result.Sent, sync.sync())
            assertEquals("one call for the change", before + 1, server.calls)
            assertNull("closed stays closed", c.shareLinks.dropped.value)
        }
        // A link made again from the sheet: said in the sheet, not above the calendar as well.
        c.shareLinks.clear()
        val (_, words) = server.createLeaving(JSONObject("""{"v":2,"title":"민지","types":[{"id":1,"name":"주간","icon":"e:☀️"}],"days":[]}"""), c.shareLinks)
        assertEquals("근무 그림 ☀️는 링크에 못 실어 빼고 보냈어요.", words)
        c.shareLinks.save(link())
        c.scheduleRepository.setOverride(Today.date().plusDays(6), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertNull(c.shareLinks.dropped.value)
    }

    /**
     * 검토 S7-3 (갈래 R 의견 3): the automatic update turned down for something it cannot leave out is said once above the calendar the
     * second time running, for someone who never opens the share sheet; not the first time, and not again the third.
     */
    @Test fun theSameRefusalTwiceRunningIsSaidOnceAboveTheCalendar() = runBlocking {
        c.shareLinks.save(link())
        val sync = ShareSync(c)
        server.down = ShareLinkException("title")
        assertEquals(ShareSync.Result.Refused, sync.sync())
        assertNull("once may be a passing thing", c.shareLinks.dropped.value)
        c.scheduleRepository.setOverride(Today.date().plusDays(2), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Refused, sync.sync())
        assertEquals(ShareLinkPayload.REFUSED_AGAIN, c.shareLinks.dropped.value)
        c.shareLinks.setDropped(null)
        c.scheduleRepository.setOverride(Today.date().plusDays(3), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Refused, sync.sync())
        assertNull("said once", c.shareLinks.dropped.value)
        // Sent at last: the count starts again.
        server.down = null
        assertEquals(ShareSync.Result.Sent, sync.sync())
        server.down = ShareLinkException("title")
        c.scheduleRepository.setOverride(Today.date().plusDays(4), ShiftOverride.Shift(dayType().id))
        assertEquals(ShareSync.Result.Refused, sync.sync())
        assertNull(c.shareLinks.dropped.value)
        // By hand (the sheet's 다시 보내기) it is said in the sheet, not counted.
        assertEquals(ShareSync.Result.Refused, sync.resend(c.shareLinks.key))
        assertNull(c.shareLinks.dropped.value)
    }

    /** SN-13: a refusal the phone cannot mend is said as refused, not offline; busy and no internet are still tried again. */
    @Test fun aRefusalIsNotOfflineAndBusyIsNotARefusal() = runBlocking {
        c.shareLinks.save(link())
        val sync = ShareSync(c)
        server.down = ShareLinkException("title")
        assertEquals(ShareSync.Result.Refused, sync.sync())
        assertTrue(c.shareLinks.refused.value)
        assertTrue(c.shareLinks.unsent.value)
        assertTrue(c.shareLinks.link.value != null)
        for (busy in listOf("rate_limited", "paused", "server", "http_429", "http_503", "bad_response")) {
            server.down = ShareLinkException(busy)
            assertEquals(busy, ShareSync.Result.Offline, sync.sync())
            assertFalse(busy, ShareLinkPayload.refused(ShareLinkException(busy)))
        }
        server.down = java.io.IOException("no network")
        assertEquals(ShareSync.Result.Offline, sync.sync())
        server.down = null
        assertEquals(ShareSync.Result.Sent, sync.sync())
        assertFalse("sent: the refusal is over", c.shareLinks.refused.value)
        assertFalse(c.shareLinks.unsent.value)
    }

    /** SN-13 / SN-14: what can be left out is; what cannot gives null (said as refused). */
    @Test fun onlyTheRefusedPartIsLeftOut() {
        val p = JSONObject("""{"v":2,"title":"민지","types":[
            {"id":1,"name":"주간","short":"주","color":"#2196F3","time":"07:00-19:00","rest":60,"icon":"e:☀️"},
            {"id":2,"name":"야간","short":"야","color":"#3F51B5","time":"19:00-07:00","icon":"moon"}],
            "days":[],"look":"{}","kit":"{}"}""")
        val (icon, iconWords) = ShareLinkPayload.withoutRefused(p, "type_icon")!!
        assertEquals(listOf("moon"), icon.icons())
        assertTrue(iconWords, "☀️" in iconWords)
        val (rest, _) = ShareLinkPayload.withoutRefused(p, "type_rest")!!
        assertFalse(rest.getJSONArray("types").getJSONObject(0).has("rest"))
        assertFalse(ShareLinkPayload.withoutRefused(p, "look")!!.first.has("look"))
        assertTrue(ShareLinkPayload.withoutRefused(p, "look")!!.first.has("kit"))
        assertFalse(ShareLinkPayload.withoutRefused(p, "kit")!!.first.has("kit"))
        assertNull(ShareLinkPayload.withoutRefused(p, "title"))
        assertNull("no emoji 그림 to leave out", ShareLinkPayload.withoutRefused(icon, "type_icon"))
        assertEquals("e:☀️", p.getJSONArray("types").getJSONObject(0).getString("icon"))
    }

    /** PC-92 (조건 7·16): a failure says 연결 없음 · 서버 바쁨 · 시간 넘김, never '(type_icon)'; the code goes only into a question. */
    @Test fun failuresAreSaidWithoutTheServersCode() {
        val first = "링크를 만들지 못했어요."
        val refused = netWords(ShareLinkException("type_icon"), first)
        assertFalse(refused, "type_icon" in refused || "(" in refused)
        assertEquals("$first ${NetWords.BUSY}", refused)
        assertEquals("$first ${NetWords.SLOW}", netWords(java.net.SocketTimeoutException(), first))
        assertEquals("$first ${NetWords.OFFLINE}", netWords(java.io.IOException(), first))
        assertEquals("type_icon", failureCode(ShareLinkException("type_icon")))
    }

    /** PC-94: a break goes with its 근무 within the share server's rules (RVD-23), and comes back as the same hours (RVD23-1). */
    @Test fun theBreakGoesAndComesBackAsTheSameHours() = runBlocking {
        val types = c.scheduleRepository.shiftTypes.first().filter { !it.isOff }
        val day = types[0].copy(start = LocalTime.of(7, 0), end = LocalTime.of(19, 0), breakMinutes = 60, breakStart = LocalTime.of(12, 0))
        val night = types[1].copy(start = LocalTime.of(19, 0), end = LocalTime.of(7, 0), breakMinutes = 60, breakStart = LocalTime.of(6, 30))
        c.scheduleRepository.saveShiftType(day)
        c.scheduleRepository.saveShiftType(night)
        c.scheduleRepository.setOverride(Today.date().plusDays(2), ShiftOverride.Shift(night.id))
        c.shareLinks.save(link())
        assertEquals(ShareSync.Result.Sent, ShareSync(c).sync())
        val sent = server.got.single().getJSONArray("types").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val d = sent.first { it.getLong("id") == day.id }
        assertEquals(60, d.getInt("rest"))
        assertEquals("12:00", d.getString("restStart"))
        val n = sent.first { it.getLong("id") == night.id }
        assertEquals(60, n.getInt("rest"))
        assertFalse("06:30 + 60분 runs past 07:00: the start stays home, as the server would refuse it", n.has("restStart"))
        // Back on the receiving phone: the same 11 hours (검토 RVD23-1), not 12.
        val got = SharedSchedule.parse(server.got.single()).types.first { it.id == day.id }
        assertEquals(60, got.rest)
        assertEquals(11 * 60, got.asShiftType().workMinutes)
        assertEquals(LocalTime.of(12, 0), got.asShiftType().breakStart)
        assertFalse(got.restUnknown)
        // A link of an app before 2.0.0 (no rest) is said as unknown; a stray value past 720 is not read.
        val old = SharedSchedule.parse(JSONObject("""{"title":"민지","types":[{"id":1,"name":"주간","short":"주","color":"#2196F3","time":"07:00-19:00"},
            {"id":2,"name":"야간","short":"야","color":"#3F51B5","time":"19:00-07:00","rest":900}],"days":[]}"""))
        assertTrue(old.types.all { it.restUnknown })
        assertEquals(12 * 60, old.types[0].asShiftType().workMinutes)
    }

    /** PC-94 with 2.0.0 하루 두 구간 (RVH-44): the gap goes and comes back, and a break counts the gap as the server does. */
    @Test fun aTwoPartDayComesBackInTwoParts() = runBlocking {
        val t = c.scheduleRepository.shiftTypes.first().first { !it.isOff }
        val split = t.copy(start = LocalTime.of(9, 0), end = LocalTime.of(21, 0), gapStart = LocalTime.of(13, 0), gapMinutes = 240, breakMinutes = 30)
        c.scheduleRepository.saveShiftType(split)
        c.shareLinks.save(link())
        assertEquals(ShareSync.Result.Sent, ShareSync(c).sync())
        val sent = server.got.single().getJSONArray("types").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.getLong("id") == t.id }
        assertEquals("13:00-17:00", sent.getString("gap"))
        assertEquals(30, sent.getInt("rest"))
        val got = SharedSchedule.parse(server.got.single()).types.first { it.id == t.id }.asShiftType()
        assertTrue(got.hasGap)
        assertEquals(LocalTime.of(13, 0), got.gapStart)
        assertEquals(240, got.gapMinutes)
        assertEquals(30, got.breakMinutes)
        // A gap that does not fit the day is no gap on the receiving side.
        val odd = SharedType(1, "주간", "주", 0xFF2196F3, "09:00-12:00", false, null, rest = 0, gap = "11:50-12:30").asShiftType()
        assertFalse(odd.hasGap)
        assertEquals(0, odd.gapMinutes)
    }

    /** PC-94: a break as long as the day or longer does not go (the server would refuse the whole link with type_rest). */
    @Test fun aBreakTheServerWouldRefuseStaysHome() = runBlocking {
        val t = c.scheduleRepository.shiftTypes.first().first { !it.isOff }
        c.scheduleRepository.saveShiftType(t.copy(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0), breakMinutes = 600))
        c.shareLinks.save(link())
        assertEquals(ShareSync.Result.Sent, ShareSync(c).sync())
        val sent = server.got.single().getJSONArray("types").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.getLong("id") == t.id }
        assertFalse(sent.has("rest"))
        assertTrue(sent.has("time"))
    }

    /** PC-97 (조건 10): '내 이름' as typed stays for the next try and after the sheet closes, and through a cut link. */
    @Test fun theTypedNameStays() {
        c.shareLinks.typedName = "민지네"
        assertEquals("민지네", ShareLinkStore(app).typedName)
        c.shareLinks.clear()
        assertEquals("민지네", c.shareLinks.typedName)
        c.shareLinks.typedName = "가".repeat(40)
        assertEquals(30, c.shareLinks.typedName!!.length)
    }

    /** PC-93 (조건 4·16): a link sent without a name reads '받은 근무표', never ‘받은’ 근무표, 이름 없음님 or a cut '이름 없'. */
    @Test fun aLinkWithoutANameIsTheReceivedRoster() {
        assertEquals("받은 근무표", LinkStart.roster(null))
        assertEquals("받은 근무표", LinkStart.roster(""))
        assertEquals("받은 근무표", LinkStart.roster(LinkStart.NO_NAME))
        assertEquals("‘민지’ 근무표", LinkStart.roster(" 민지 "))
        val cal = ReceivedCalendar("Ab3dEfGh5JkLmNoPqRsTuv", "", 0xFF2196F3, Today.instant(), Today.instant(), null, null,
            JSONObject("""{"title":"","types":[],"days":[]}"""), null)
        assertEquals("받은 표", cal.name)
        assertEquals("받은 근무표", cal.roster)
        assertFalse("이름 없음" in cal.name)
    }

    /**
     * PC-95 (조건 10): '9일 내 달력에 넣기' then 되돌리기 is back to before: the days as they were, the link not followed, and the same
     * days offered again (before, they stayed as '직접 바꾼 날' and the card offered 0일).
     */
    @Test fun undoingPutInGoesBackToBeforeOnly() = runBlocking {
        val day = dayType()
        val date = Today.date().plusDays(4)
        val schedule = JSONObject("""{"title":"민지","types":[{"id":1,"name":"${day.name}","short":"${day.label}","color":"#2196F3"}],
            "days":[{"date":"$date","type":1}]}""")
        val id = "Pc95LinkAb3dEfGh5JkLmN"
        c.received.put(id, ReceivedShare(schedule, Today.instant().plus(Duration.ofDays(30)), null, null))
        val before = c.received.get(id)!!
        val dayBefore = c.scheduleRepository.entryNow(date)
        val undo = PutIn.apply(c, id, mapOf(date to ShiftOverride.Shift(day.id)))
        assertEquals(1, undo.days)
        assertTrue(c.received.get(id)!!.adopted)
        assertEquals(ShiftOverride.Shift(day.id), c.scheduleRepository.entryNow(date)?.override)

        undo.back()
        assertEquals(dayBefore?.override, c.scheduleRepository.entryNow(date)?.override)
        val after = c.received.get(id)!!
        assertEquals(before.adopted, after.adopted)
        assertEquals(before.mine, after.mine)
        assertEquals(before.follow, after.follow)
        assertEquals(before.basis?.toString(), after.basis?.toString())
        // Can be put in again, the same day.
        val again = PutIn.apply(c, id, mapOf(date to ShiftOverride.Shift(day.id)))
        assertEquals(1, again.days)
        again.back()
        c.received.remove(id)
    }
}
