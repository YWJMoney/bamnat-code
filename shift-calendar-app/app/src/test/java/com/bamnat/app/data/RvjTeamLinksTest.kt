package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.app.ui.team.defaultCloseDay
import com.bamnat.core.team.MonthState
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamMonth
import com.bamnat.core.team.TeamShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 2.0.0 갈래 J [RVJ-102] 검토 PAYU-09·PAYU-10·PAYU-18: what a team link is made of, and kept:
 * - a link is made anew only when the server says it is gone, never for a busy server or no connection;
 * - the board confirmed before the last 확정 is kept beside it, so sending the same 판 again says what the last 확정 changed;
 * - the first 마감 of 신청 받기 is never a time that has gone by.
 */
@RunWith(AndroidJUnit4::class)
class RvjTeamLinksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = PhaseThreeStore(db) { 1L }
    private val month = YearMonth.of(2026, 11)

    @After fun close() = db.close()

    @Test fun onlyAGoneLinkIsMadeAnew() {
        assertTrue(ShareLinkPayload.gone(ShareLinkException("not_found")))
        assertTrue(ShareLinkPayload.gone(ShareLinkException("forbidden")))
        assertFalse("a busy server is not a gone link", ShareLinkPayload.gone(ShareLinkException("rate_limited")))
        assertFalse(ShareLinkPayload.gone(ShareLinkException("server")))
        assertFalse(ShareLinkPayload.gone(ShareLinkException("bad_response")))
        assertFalse("no connection is not a gone link", ShareLinkPayload.gone(java.net.UnknownHostException("x")))
    }

    @Test fun theBoardBeforeTheLastConfirmIsKept() = runBlocking {
        com.bamnat.app.data.ScheduleRepository(db).ensureDefaults()
        val shifts = listOf(TeamShift(1, "D", "데이", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(15, 0)), TeamShift(2, "E", "이브닝", 0xFF4CAF50, false, LocalTime.of(15, 0), LocalTime.of(23, 0)))
        store.saveTeam(Team("t1", "3병동", "수간호사", shifts))
        store.saveMembers(listOf(TeamMember("m1", "t1", "김가나"), TeamMember("m2", "t1", "이다라")))
        val d = month.atDay(3)
        val first = store.boardNow("t1", month).paint("m1", d, 1).paint("m2", d, 2)
        val m1 = store.confirm("t1", first, TeamMonth("t1", month))
        assertEquals(1, m1.version)
        assertTrue("the first 확정 has nothing before it", store.confirmedBefore("t1", month).isEmpty())
        val second = first.paint("m1", d, 2).paint("m2", d, 1)
        val m2 = store.confirm("t1", second, m1)
        assertEquals(2, m2.version)
        // What the last 확정 is set against: the first board.
        assertEquals(1L, store.confirmedBefore("t1", month)["m1" to d])
        assertEquals(2L, store.confirmedBefore("t1", month)["m2" to d])
        // The board as last confirmed reads as before (the key of the one before it is no cell).
        val now = store.month("t1", month).first()
        assertEquals(2L, now.second["m1" to d])
        assertEquals(1L, now.second["m2" to d])
        assertEquals(2, now.second.size)
        // A third 확정 moves the window on: the second board is what it is set against.
        val third = second.paint("m1", d, 1)
        store.confirm("t1", third, m2)
        assertEquals(2L, store.confirmedBefore("t1", month)["m1" to d])
    }

    @Test fun theFirstCloseIsNeverATimeThatHasGoneBy() {
        val seoul = ZoneId.of("Asia/Seoul")
        fun at(y: Int, m: Int, d: Int, h: Int) = LocalDate.of(y, m, d).atTime(h, 0).atZone(seoul).toInstant().toEpochMilli()
        // Next month's 신청: a week ahead at 18시.
        assertEquals(LocalDate.of(2026, 10, 21), defaultCloseDay(YearMonth.of(2026, 11), LocalDate.of(2026, 10, 14), 18, at(2026, 10, 14, 10)))
        // The month is near or begun and today's 18시 has gone by: tomorrow.
        assertEquals(LocalDate.of(2026, 10, 15), defaultCloseDay(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 14), 18, at(2026, 10, 14, 19)))
        // Before today's 18시 it is today.
        assertEquals(LocalDate.of(2026, 10, 14), defaultCloseDay(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 14), 18, at(2026, 10, 14, 10)))
    }
}
