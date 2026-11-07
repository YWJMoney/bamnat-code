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
}
