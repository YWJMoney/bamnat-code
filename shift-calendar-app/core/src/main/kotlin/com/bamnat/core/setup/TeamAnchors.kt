package com.bamnat.core.setup

import com.bamnat.core.plaza.CycleTeam
import java.time.LocalDate

/**
 * [CYC-06] 1.38: "몇 조예요?" in the first setup. A public row may name its teams (1조·A조…) in the order they follow each other
 * through the cycle, with the day each is on the order's first day. When only the first team's day is known, the others follow it
 * by an equal share of the cycle (4조2교대 주야비휴: a day each; 8일 4조: two days each), as teams of one rota do. Without a day for
 * any team, or with a cycle the teams do not share equally, nothing is guessed: today's shift is asked as before.
 */
object TeamAnchors {
    /** The day team [index] of [teams] is on the first day of a cycle [length] days long; null when it cannot be told. */
    fun anchorOf(teams: List<CycleTeam>, index: Int, length: Int): LocalDate? {
        val team = teams.getOrNull(index) ?: return null
        team.anchor?.let { return it }
        if (length <= 0 || teams.size < 2 || length % teams.size != 0) return null
        val first = teams.first().anchor ?: return null
        return first.plusDays(index.toLong() * (length / teams.size))
    }

    /** The teams that can be picked: each with its day; empty when none can be told. */
    fun pickable(teams: List<CycleTeam>, length: Int): List<Pair<String, LocalDate>> =
        teams.indices.mapNotNull { i -> anchorOf(teams, i, length)?.let { teams[i].name to it } }
}
