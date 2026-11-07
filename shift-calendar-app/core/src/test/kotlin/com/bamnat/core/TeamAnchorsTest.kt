package com.bamnat.core

import com.bamnat.core.plaza.CycleTeam
import com.bamnat.core.setup.TeamAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
