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
}
