package com.bamnat.core

import com.bamnat.core.plaza.CycleTeam
import com.bamnat.core.setup.TeamAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.TeamRoster
import java.time.LocalDate

/** [CYC-06] "몇 조예요?": the day each team starts the cycle. */
class TeamAnchorsTest {
    private val jan5 = LocalDate.of(2026, 1, 5)

    @Test fun `the first team's day puts the others an equal share of the cycle after it`() {
        val teams = listOf(CycleTeam("1조", jan5), CycleTeam("2조"), CycleTeam("3조"), CycleTeam("4조"))
        // 주야비휴: a day each.
        assertEquals(jan5.plusDays(1), TeamAnchors.anchorOf(teams, 1, 4))
        assertEquals(jan5.plusDays(3), TeamAnchors.anchorOf(teams, 3, 4))
        // 8 days, 4 teams: two days each.
        assertEquals(jan5.plusDays(4), TeamAnchors.anchorOf(teams, 2, 8))
    }

    @Test fun `a team's own day wins, and nothing is guessed without one`() {
        val own = listOf(CycleTeam("A조", jan5), CycleTeam("B조", jan5.plusDays(5)))
        assertEquals(jan5.plusDays(5), TeamAnchors.anchorOf(own, 1, 4))
        assertNull(TeamAnchors.anchorOf(listOf(CycleTeam("1조"), CycleTeam("2조")), 1, 4))
        // Three teams cannot share a 4-day cycle equally.
        assertNull(TeamAnchors.anchorOf(listOf(CycleTeam("1조", jan5), CycleTeam("2조"), CycleTeam("3조")), 1, 4))
        assertTrue(TeamAnchors.pickable(listOf(CycleTeam("1조"), CycleTeam("2조")), 4).isEmpty())
        assertEquals(listOf("1조" to jan5, "2조" to jan5.plusDays(2)), TeamAnchors.pickable(listOf(CycleTeam("1조", jan5), CycleTeam("2조")), 4))
    }

    @Test fun `a sender's teams go with the file and come back as the same rota for any team picked`() {
        // 주주야야휴휴휴휴, 4조: A조 (mine) first, B·C·D two days apart each, as 모든 조 보기 keeps them.
        val steps = listOf(1L, 1L, 2L, 2L, null, null, null, null)
        val sender = TeamRoster(listOf(TeamRoster.Team("A조", 0, mine = true), TeamRoster.Team("B조", 2), TeamRoster.Team("C조", 4), TeamRoster.Team("D조", 6)))
        val teams = TeamAnchors.ofRoster(sender, jan5, 8)
        assertEquals(listOf("A조" to jan5, "B조" to jan5.plusDays(2), "C조" to jan5.plusDays(4), "D조" to jan5.plusDays(6)), teams.map { it.name to it.anchor })
        // A coworker picks C조: their order starts on C조's day, and every team works what the sender's phone says it works.
        val mine = TeamAnchors.roster(teams, jan5.plusDays(4), 8)!!
        assertEquals("C조", mine.mine.name)
        val theirs = WorkPattern.Rotation(startDate = jan5, steps = steps, anchorDate = jan5.plusDays(4))
        val senders = WorkPattern.Rotation(startDate = jan5, steps = steps, anchorDate = jan5)
        (0L until 16L).map(jan5::plusDays).forEach { day ->
            sender.teams.forEach { t ->
                assertEquals("${t.name} $day", sender.shiftOn(senders, t, day), mine.shiftOn(theirs, mine.teams.first { it.name == t.name }, day))
            }
        }
        assertEquals(4, TeamAnchors.stepOn(jan5, jan5.plusDays(12), 8))
    }

    @Test fun `nothing goes for one team, two teams on one day, or an answer that is none of them`() {
        assertTrue(TeamAnchors.ofRoster(TeamRoster(listOf(TeamRoster.Team("A조", 0, true))), jan5, 8).isEmpty())
        assertTrue(TeamAnchors.ofRoster(TeamRoster(listOf(TeamRoster.Team("A조", 0, true), TeamRoster.Team("B조", 8))), jan5, 8).isEmpty())
        val teams = listOf(CycleTeam("1조", jan5), CycleTeam("2조", jan5.plusDays(2)))
        assertNull(TeamAnchors.roster(teams, jan5.plusDays(1), 4))
        assertEquals(listOf(2, 0), TeamAnchors.roster(teams, jan5.plusDays(2), 4)!!.teams.map { it.lag })
    }

    /** 1.39 PC c0ca3d9f: days off kept as a 휴무·비번 kind split like blank days (4조 2교대 → 4, 3조 2교대 → 3). */
    @Test fun daysOffAsAKindStillSplit() {
        val (d, n, o, b) = listOf(1L, 2L, 9L, 8L)
        assertEquals(null, com.bamnat.core.schedule.TeamRoster.teamCount(listOf(d, d, n, n, o, o, o, o)))
        assertEquals(4, com.bamnat.core.schedule.TeamRoster.teamCount(listOf(d, d, n, n, o, o, o, o), setOf(o)))
        assertEquals(3, com.bamnat.core.schedule.TeamRoster.teamCount(listOf(d, d, n, n, b, o), setOf(o, b)))
        val r = com.bamnat.core.model.WorkPattern.Rotation(java.time.LocalDate.of(2026, 11, 7), listOf(d, d, n, n, b, o))
        assertEquals(listOf(0, 2, 4), com.bamnat.core.schedule.TeamRoster.suggest(r, off = setOf(o, b)).teams.map { it.lag })
        assertEquals(null, com.bamnat.core.schedule.TeamRoster.teamCount(listOf(d, d, o), setOf(o)))
    }
}
