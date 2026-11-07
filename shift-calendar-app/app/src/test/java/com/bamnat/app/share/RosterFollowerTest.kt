package com.bamnat.app.share

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.39 [TEAM-22] D9: a 리더's change of the team's roster reaches the member's own days (and so the 근무 알람) without the received
 * calendar being opened; a link put in once asks first; a day changed by hand stays; 되돌리기 puts each day back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterFollowerTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val api = Api()
    private val id = "Ab3dEfGh5JkLmNoPqRsTuv"
    private val day = LocalDate.of(2026, 12, 3)
    private var day1 = 0L
    private var night = 0L

    private class Api : ShareApi {
        override val enabled = true
        var share: ReceivedShare? = null
        override suspend fun create(payload: JSONObject) = error("not used")
        override suspend fun update(state: ShareLinkState, payload: JSONObject) = error("not used")
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String): ReceivedShare = share ?: throw ShareLinkException("not_found")
        override suspend fun readIfChanged(id: String, etag: String?): ReceivedShare? = read(id).takeIf { it.etag != etag }
        var quiet = 0
        override suspend fun readQuietly(id: String, etag: String?): ReceivedShare? { quiet++; return readIfChanged(id, etag) }
    }

    /** The 리더's link: 12/3 and 12/4 with D or N. */
    private fun link(d3: String, d4: String, tag: String) = ReceivedShare(
        JSONObject("""{"v":2,"title":"간호2팀","types":[{"id":1,"name":"데이","short":"D","color":"#1E88E5","time":"07:00-15:00"},
            {"id":2,"name":"나이트","short":"N","color":"#3949AB","time":"22:00-07:00"}],
            "days":[{"date":"2026-12-03","type":$d3},{"date":"2026-12-04","type":$d4}]}"""),
        Instant.now().plus(Duration.ofDays(60)), Instant.now(), tag,
    )

    @Before fun setUp() = runBlocking<Unit> {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        Today.fix(day.minusDays(2).atTime(9, 0).atZone(ZoneId.systemDefault()))
        c.shareApi = api
        day1 = c.scheduleRepository.saveShiftType(ShiftType(0, "데이", "D", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(15, 0)))
        night = c.scheduleRepository.saveShiftType(ShiftType(0, "나이트", "N", 0xFF3949AB, LocalTime.of(22, 0), LocalTime.of(7, 0)))
        // The member's month as the link first had it (데이 both days).
        c.scheduleRepository.setOverride(day, ShiftOverride.Shift(day1))
        c.scheduleRepository.setOverride(day.plusDays(1), ShiftOverride.Shift(day1))
        api.share = link("1", "1", "t1")
        c.received.put(id, api.share!!)
    }

    @After fun tearDown() {
        c.received.remove(id)
        Today.reset()
    }

    private suspend fun override(d: LocalDate) = c.scheduleRepository.snapshot.first().entries.firstOrNull { it.date == d }?.override

    /** 1.39 G2: the 6-hourly check is not the member looking, so it asks without setting the 리더's 봤어요; opening the link does. */
    @Test fun theBackgroundCheckDoesNotCountAsSeen() = runBlocking<Unit> {
        c.received.put(id, api.share!!, mine = true)
        api.share = link("2", "1", "t2")
        RosterFollower.checkAll(c)
        assertEquals(1, api.quiet)
        assertEquals(ShiftOverride.Shift(night), override(day))
        api.share = link("2", "2", "t3")
        RosterFollower.refresh(c, id)
        assertEquals(1, api.quiet)
    }

    @Test fun theLinkTheWorkComesFromGoesInAndCanBeTakenBack() = runBlocking<Unit> {
        c.received.put(id, api.share!!, mine = true)
        assertEquals(RosterFollower.Mode.AUTO, RosterFollower.mode(c.received.get(id)!!))
        // The 리더 makes 12/3 a 나이트.
        api.share = link("2", "1", "t2")
        assertTrue(RosterFollower.refresh(c, id))
        assertEquals(ShiftOverride.Shift(night), override(day))
        assertEquals(ShiftOverride.Shift(day1), override(day.plusDays(1)))
        val note = c.received.get(id)!!.note!!
        assertEquals(FollowNote.DONE, note.kind)
        assertTrue(note.lines.single(), note.lines.single().startsWith("12/3 D → N"))
        assertTrue(RosterFollower.words(c.received.get(id)!!, note).second.contains("근무 알람도"))
        // 되돌리기: the day as it was.
        RosterFollower.takeBack(c, id)
        assertEquals(ShiftOverride.Shift(day1), override(day))
        assertNull(c.received.get(id)!!.note)
    }

    @Test fun aLinkPutInOnceAsksAndADayChangedByHandStays() = runBlocking<Unit> {
        c.received.markAdopted(id)
        assertEquals(RosterFollower.Mode.ASK, RosterFollower.mode(c.received.get(id)!!))
        // The member made 12/4 a 나이트 by hand; the 리더 then changes both days.
        c.scheduleRepository.setOverride(day.plusDays(1), ShiftOverride.Shift(night))
        api.share = link("2", "2", "t2")
        RosterFollower.refresh(c, id)
        // Asked, nothing written yet.
        assertEquals(ShiftOverride.Shift(day1), override(day))
        val note = c.received.get(id)!!.note!!
        assertEquals(FollowNote.ASK, note.kind)
        assertEquals(setOf(day), note.apply.keys)
        assertTrue("12/4 is the member's own (and already says N): not asked", note.kept.isEmpty())
        RosterFollower.putIn(c, id)
        assertEquals(ShiftOverride.Shift(night), override(day))
        assertEquals(FollowNote.DONE, c.received.get(id)!!.note!!.kind)
    }

    @Test fun aHandChangeIsKeptAndSaid() = runBlocking<Unit> {
        c.received.put(id, api.share!!, mine = true)
        // By hand: 12/4 → 나이트. The 리더 moves 12/4 to 휴무 (no shift).
        c.scheduleRepository.setOverride(day.plusDays(1), ShiftOverride.Shift(night))
        api.share = link("1", "null", "t2")
        RosterFollower.refresh(c, id)
        assertEquals(ShiftOverride.Shift(night), override(day.plusDays(1)))
        assertEquals(listOf(day.plusDays(1)), c.received.get(id)!!.note!!.kept)
    }

    @Test fun offAndUnfollowedLinksChangeNothing() = runBlocking<Unit> {
        api.share = link("2", "1", "t2")
        RosterFollower.refresh(c, id) // neither the setup's link nor put in: only the copy moves
        assertEquals(ShiftOverride.Shift(day1), override(day))
        assertNull(c.received.get(id)!!.note)
        c.received.put(id, api.share!!, mine = true)
        c.received.setFollow(id, RosterFollower.Mode.OFF.key)
        api.share = link("1", "2", "t3")
        RosterFollower.refresh(c, id)
        assertEquals(ShiftOverride.Shift(day1), override(day.plusDays(1)))
    }

    @Test fun aCutLinkLeavesTheDaysAndSaysSo() = runBlocking<Unit> {
        c.received.put(id, api.share!!, mine = true)
        api.share = null
        RosterFollower.refresh(c, id)
        assertTrue(c.received.get(id)!!.gone)
        assertEquals(FollowNote.GONE, c.received.get(id)!!.note!!.kind)
        assertEquals(ShiftOverride.Shift(day1), override(day))
    }

    @Test fun theNoteAndModeOutliveARestart() {
        c.received.put(id, api.share!!, mine = true)
        c.received.setFollow(id, RosterFollower.Mode.ASK.key)
        c.received.setNote(id, FollowNote(FollowNote.ASK, Instant.parse("2026-12-01T00:00:00Z"), listOf("12/3 D → N"),
            apply = mapOf(day to "s7"), kept = listOf(day.plusDays(1))))
        val file = java.io.File(app.noBackupFilesDir, "received_calendars_follow_test.json")
        val a = ReceivedCalendarStore(file, writer = { it.run() })
        a.put(id, api.share!!, mine = true)
        a.markAdopted(id)
        a.setFollow(id, "ask")
        a.setNote(id, c.received.get(id)!!.note)
        val b = ReceivedCalendarStore(file, writer = { it.run() }).get(id)!!
        assertTrue(b.mine && b.adopted)
        assertEquals("ask", b.follow)
        assertEquals(c.received.get(id)!!.note, b.note)
        file.delete()
        assertEquals(ShiftOverride.Shift(7), RosterFollower.override("s7"))
        assertEquals("o", RosterFollower.code(ShiftOverride.Off))
        assertEquals(ShiftOverride.None, RosterFollower.override(RosterFollower.code(ShiftOverride.None)))
    }

    /** PC a483ca15: the new copy came in another way first (opening the link again); the next look still puts the change in. */
    @Test fun aCopyBroughtInAnotherWayIsStillFollowed() = runBlocking<Unit> {
        c.received.put(id, api.share!!, mine = true)
        api.share = link("2", "1", "t2")
        c.received.put(id, api.share!!) // e.g. ReceivedViewModel.add: the copy moves, my days not yet
        assertEquals(ShiftOverride.Shift(day1), override(day))
        assertTrue(RosterFollower.refresh(c, id)) // the server says nothing changed (same tag)
        assertEquals(ShiftOverride.Shift(night), override(day))
        assertEquals(FollowNote.DONE, c.received.get(id)!!.note!!.kind)
        // Once matched, another look does nothing more.
        RosterFollower.close(c, id)
        RosterFollower.refresh(c, id)
        assertNull(c.received.get(id)!!.note)
    }

    /** The setup filled the days from an older read than the copy it kept: the difference is put in. */
    @Test fun theSetupsOwnReadIsTheBasis() = runBlocking<Unit> {
        val filledFrom = api.share!!.schedule
        c.received.remove(id)
        api.share = link("2", "1", "t2")
        c.received.put(id, api.share!!, mine = true, basis = filledFrom)
        RosterFollower.refresh(c, id)
        assertEquals(ShiftOverride.Shift(night), override(day))
    }

    /** PC a483ca15: '직접 바꾼 날이라 그대로 뒀어요 · 직접 바꾼 12/2은 그대로 뒀어요' (twice, and 은). */
    @Test fun aKeptDayIsSaidOnce() {
        val cal = c.received.get(id)!!
        val (_, text) = RosterFollower.words(cal, FollowNote(FollowNote.ASK, Instant.now(), emptyList(), kept = listOf(day.minusDays(1))))
        assertEquals("직접 바꾼 날이라 그대로 뒀어요: 12/2", text)
        val (_, done) = RosterFollower.words(cal, FollowNote(FollowNote.DONE, Instant.now(), listOf("12/3 D → N"), kept = listOf(day.plusDays(1))))
        assertTrue(done, done.contains("직접 바꿔서 그대로 둔 날: 12/4") && !done.contains("은 그대로"))
    }

    /** Two changes asked one after the other are asked together. */
    @Test fun anUnansweredAskKeepsItsDays() = runBlocking<Unit> {
        c.received.markAdopted(id)
        api.share = link("2", "1", "t2")
        RosterFollower.refresh(c, id)
        api.share = link("2", "2", "t3")
        RosterFollower.refresh(c, id)
        val note = c.received.get(id)!!.note!!
        assertEquals(setOf(day, day.plusDays(1)), note.apply.keys)
        assertEquals(2, note.lines.size)
    }

}
