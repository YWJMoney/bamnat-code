package com.bamnat.core.setup

import com.bamnat.core.plaza.CycleTeam
import com.bamnat.core.schedule.TeamRoster
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

    /**
     * [CYC-06] 1.39: the teams a sender set up in 모든 조 보기 ([TeamRoster], each team [TeamRoster.Team.lag] days behind theirs),
     * as days each starts the order whose first day is [anchor] (the sender's own). A team [lag] days behind works today what
     * the sender's worked [lag] days ago, so its first day is [lag] days later. Nothing goes for one team or a roster that does
     * not fit a [size]-day order: those are the sender's guesses, not a rota.
     */
    fun ofRoster(roster: TeamRoster, anchor: LocalDate, size: Int): List<CycleTeam> {
        if (size <= 0 || roster.teams.size < 2) return emptyList()
        val fixed = roster.anchored(size)
        if (fixed.teams.map { Math.floorMod(it.lag, size) }.toSet().size != fixed.teams.size) return emptyList()
        return fixed.teams.map { CycleTeam(it.name, anchor.plusDays(Math.floorMod(it.lag, size).toLong())) }
    }

    /**
     * [CYC-06] 1.39: the 모든 조 보기 a picked team gives, so the teams are there without setting them again. [mine] is the day the
     * user's order starts (their answer); the team on that day is marked mine and the others keep their distance from it.
     * Null when not every team has a day, two share one, or the answer is none of them (오늘 근무로 답한 다른 순서).
     */
    fun roster(teams: List<CycleTeam>, mine: LocalDate, size: Int): TeamRoster? {
        if (size <= 0 || teams.size < 2 || teams.size > TeamRoster.MAX_TEAMS) return null
        val days = teams.indices.map { anchorOf(teams, it, size) ?: return null }
        val lags = days.map { Math.floorMod(ChronoUnit.DAYS.between(mine, it), size.toLong()).toInt() }
        if (lags.toSet().size != lags.size || 0 !in lags) return null
        return TeamRoster(teams.mapIndexed { i, t -> TeamRoster.Team(t.name.take(12), lags[i], mine = lags[i] == 0) })
    }

    /** [CYC-06] Where in a [size]-day order starting [anchor] the day [date] is (0 the first). */
    fun stepOn(anchor: LocalDate, date: LocalDate, size: Int): Int = Math.floorMod(ChronoUnit.DAYS.between(anchor, date), size.toLong()).toInt()
}
