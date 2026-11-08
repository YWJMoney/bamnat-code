package com.bamnat.core

import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamTimes
import com.bamnat.core.team.TeamToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 1.39 G6 [SETUP-18]: a team made while the 리더's first setup waits keeps its kinds of day but asks for the times once. */
class TeamTimesTest {
    private val sample = listOf(
        TeamShift(1, "D", "주간", 0xFF2D8FD0, false, LocalTime.of(9, 0), LocalTime.of(18, 0)),
        TeamShift(2, "N", "야간", 0xFF7E57C2, false, LocalTime.of(21, 0), LocalTime.of(9, 0)),
        TeamShift(3, "O", "휴무", 0xFF9E9E9E, true),
    )

    @Test fun theAppsSampleTimesDoNotGoToTheTeam() {
        val blank = TeamTimes.blank(sample)
        assertEquals(listOf("D", "N", "O"), blank.map { it.label })
        assertTrue(blank.all { it.start == null && it.end == null })
        assertEquals(listOf(1L, 2L), TeamTimes.missing(Team("t", "3병동", shifts = blank)).map { it.id })
    }

    @Test fun timesPickedOnceAreTheTeams() {
        val team = Team("t", "3병동", shifts = TeamTimes.blank(sample))
        val set = TeamTimes.set(team, mapOf(1L to (LocalTime.of(7, 0) to LocalTime.of(15, 0)), 2L to (LocalTime.of(22, 0) to LocalTime.of(7, 0))))
        assertTrue(TeamTimes.missing(set).isEmpty())
        assertEquals(LocalTime.of(7, 0), set.shifts.first { it.id == 1L }.start)
        assertNull(set.shifts.first { it.id == 3L }.start)
    }

    @Test fun aTeamWithItsOwnTimesIsNotAsked() {
        assertTrue(TeamTimes.missing(Team("t", "3병동", shifts = sample)).isEmpty())
    }

    /** 1.39 G6 발굴: the usual times by name, 3교대 with an evening kind, else 2교대. */
    @Test fun usualTimesAreSuggestedByName() {
        val three = TeamTimes.blank(listOf(
            TeamShift(1, "D", "주간", 0, false), TeamShift(2, "E", "오후", 0, false), TeamShift(3, "N", "야간", 0, false), TeamShift(4, "O", "휴무", 0, true)))
        val s = TeamTimes.suggest(three)
        assertEquals(LocalTime.of(7, 0) to LocalTime.of(15, 0), s[1L])
        assertEquals(LocalTime.of(15, 0) to LocalTime.of(23, 0), s[2L])
        assertEquals(LocalTime.of(23, 0) to LocalTime.of(7, 0), s[3L])
        assertNull(s[4L])
        val two = TeamTimes.suggest(TeamTimes.blank(sample))
        assertEquals(LocalTime.of(7, 0) to LocalTime.of(19, 0), two[1L])
        assertEquals(LocalTime.of(19, 0) to LocalTime.of(7, 0), two[2L])
    }

    /** 1.39 [SETUP-21] PC 5차 리더 폰: the team's times go to the leader's own kinds of the same name or sort; one set by hand is asked. */
    @Test fun theLeadersOwnCalendarTakesTheTeamsTimes() {
        fun t(h: Int) = LocalTime.of(h, 0)
        val defaults = listOf(
            com.bamnat.core.model.ShiftType(0, "주간", "주", 0, t(9), t(18)),
            com.bamnat.core.model.ShiftType(0, "야간", "야", 0, t(22), t(7)),
        )
        val team = Team("t", "7병동", shifts = listOf(
            TeamShift(11, "주", "주", 0, false, t(7), t(15)),
            TeamShift(12, "오", "오", 0, false, t(15), t(23)),
            TeamShift(13, "야", "야", 0, false, t(23), t(7)),
            TeamShift(14, "휴", "휴무", 0, true),
        ))
        val mine = listOf(
            com.bamnat.core.model.ShiftType(1, "주간", "주", 0, t(9), t(18)),
            com.bamnat.core.model.ShiftType(2, "야간", "야", 0, t(22), t(7)),
            com.bamnat.core.model.ShiftType(3, "휴무", "휴", 0, null, null, isOff = true),
        )
        val changes = TeamTimes.mine(team, mine, defaults)
        assertEquals(listOf(1L, 2L), changes.map { it.type.id })
        assertEquals(t(7) to t(15), changes[0].start to changes[0].end)
        assertEquals(t(23) to t(7), changes[1].start to changes[1].end)
        assertTrue("the app's own times change without asking", changes.none { it.ask })

        // 주간 changed by hand to 08–17: asked. A kind already at the team's time is left alone.
        val byHand = listOf(mine[0].copy(start = t(8), end = t(17)), mine[1].copy(start = t(23), end = t(7)), mine[2])
        val asked = TeamTimes.mine(team, byHand, defaults)
        assertEquals(listOf(1L), asked.map { it.type.id })
        assertTrue(asked.single().ask)

        // The same id wins over the name; two of the same sort on one side are not guessed.
        val twoDays = listOf(mine[0], mine[0].copy(id = 4, name = "주간2", label = "주2"))
        assertTrue(TeamTimes.mine(team, twoDays, defaults).isEmpty())
        val byId = Team("t", "7병동", shifts = listOf(TeamShift(4, "D", "데이", 0, false, t(6), t(14))))
        assertEquals(listOf(4L), TeamTimes.mine(byId, twoDays, defaults).map { it.type.id })
    }
}
