package com.bamnat.core.team

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterRow
import java.time.LocalDate
import java.time.YearMonth

/**
 * [TEAM-09][P3-02] 1.38 Phase 3 M24 L1 (설계 2-1, 순번 482~488): a 리더 (병동 책임·수간호사·조장) draws the team's month on their own
 * phone, confirms it, and each member gets a link with only their own days. The team lives on the leader's phone (설계 1-1 원칙 1,
 * D11): no server keeps it; a member's link is the 근무표 링크 that exists today (`/api/share`, 설계 1-4).
 *
 * The kinds of day are the leader's own calendar's ([TeamShift] mirrors their ShiftType), so D·E·N·O read the same on both sides.
 */
data class Team(
    val id: String,
    val name: String,
    val leaderName: String = "",
    /** The kinds of day the board paints with, in the order of the paint bar. */
    val shifts: List<TeamShift> = emptyList(),
    val createdAt: Long = 0,
    val archived: Boolean = false,
    /** [TEAM-15] People each kind of day needs, by [TeamShift.id] ("D 3명"); the board marks a day short of it. Empty: no check. */
    val need: Map<Long, Int> = emptyMap(),
    /**
     * 1.38 같이 쉬는 사람 (경쟁 앱 조사 P3-L-11): each member's link also names who is off on their days off. Off until the leader
     * turns it on, since it puts teammates' names in each link.
     */
    val showTogether: Boolean = false,
    /** 1.38 [P3-L-14] 신청 받기 now open or last run (DB 30 `team.ask`); null before the first. */
    val ask: AskRound? = null,
    /** 1.38 [P3-X-01] 결원 메우기 asks still open or just settled (DB 30 `team.calls`). */
    val calls: List<TeamCall> = emptyList(),
    /** 1.38 [P3-X-02] 바꾸기 요청 links of a confirmed month, null when none is open. */
    val swap: SwapRound? = null,
)

/** One kind of day on the board, copied from the leader's calendar when the team is made (and updated with it). */
data class TeamShift(
    val id: Long,
    val label: String,
    val name: String,
    val colorArgb: Long,
    val isOff: Boolean,
    val start: java.time.LocalTime? = null,
    val end: java.time.LocalTime? = null,
)

/** 신규·일반·책임 (설계 1-3 `skill`): for the 책임자 수 check of L2 and the list's badge. */
enum class Skill(val label: String) { NEW("신규"), REG("일반"), CHARGE("책임") }

data class TeamMember(
    val id: String,
    val teamId: String,
    /** The name the leader writes (설계 1-3 `displayName`): only on the leader's phone and in that member's own link. */
    val name: String,
    val role: String = "",
    val skill: Skill = Skill.REG,
    val colorArgb: Long? = null,
    val sort: Int = 0,
    /** The member's own 근무표 링크 once made at 확정 (설계 2-1 129); its token is kept as any 공유 링크's (D29). */
    val linkId: String? = null,
    val linkToken: String? = null,
    val active: Boolean = true,
)

enum class CellSource { MANUAL, COPY, PHOTO, AUTO }

/** One person's one day: a kind of day of [Team.shifts]; no cell means not drawn yet. */
data class TeamCell(val memberId: String, val date: LocalDate, val shiftId: Long, val source: CellSource = CellSource.MANUAL, val locked: Boolean = false)

enum class MonthState(val label: String) { DRAFT("짜는 중"), CONFIRMED("확정") }

data class TeamMonth(val teamId: String, val month: YearMonth, val state: MonthState = MonthState.DRAFT, val version: Int = 0, val confirmedAt: Long? = null)

/**
 * [TEAM-10] The month on the board: every cell of every member, kept by (member, date). Each change gives a new board (it never
 * changes in place), so [TeamEditor] can take any step back.
 */
data class TeamBoard(val month: YearMonth, val members: List<TeamMember>, val cells: Map<Pair<String, LocalDate>, TeamCell> = emptyMap()) {
    val days: List<LocalDate> get() = (1..month.lengthOfMonth()).map(month::atDay)

    fun cell(memberId: String, date: LocalDate): TeamCell? = cells[memberId to date]

    fun row(memberId: String): List<TeamCell?> = days.map { cell(memberId, it) }

    /** How many cells of the month are still empty, for "빈칸 12" above the board. */
    val empty: Int get() = members.filter { it.active }.sumOf { m -> days.count { cell(m.id, it) == null } }

    fun paint(memberId: String, date: LocalDate, shiftId: Long?, source: CellSource = CellSource.MANUAL): TeamBoard {
        if (date.let { YearMonth.from(it) } != month) return this
        val key = memberId to date
        val old = cells[key]
        if (old?.locked == true) return this
        return copy(cells = if (shiftId == null) cells - key else cells + (key to TeamCell(memberId, date, shiftId, source)))
    }

    /** [TEAM-11] 순서 붙여넣기: [codes] from [from] on, one a day (null skips a day, as the 한 달 키패드's » does). */
    fun paste(memberId: String, from: LocalDate, codes: List<Long?>): TeamBoard =
        codes.foldIndexed(this) { i, b, c -> if (c == null) b else b.paint(memberId, from.plusDays(i.toLong()), c) }

    /** [TEAM-11] 맞바꾸기: two members trade [date] (설계 2-1 화면 "맞바꾸기"); a locked cell stays. */
    fun swap(a: String, b: String, date: LocalDate): TeamBoard {
        val ca = cell(a, date)
        val cb = cell(b, date)
        if (ca?.locked == true || cb?.locked == true) return this
        return paint(a, date, cb?.shiftId).paint(b, date, ca?.shiftId)
    }

    fun lock(memberId: String, date: LocalDate, locked: Boolean): TeamBoard {
        val c = cell(memberId, date) ?: return this
        return copy(cells = cells + ((memberId to date) to c.copy(locked = locked)))
    }

    /** How many people each kind of day has on [date] (설계 2-1 "인원 D3 E3 N2"). */
    fun count(date: LocalDate): Map<Long, Int> = members.filter { it.active }.mapNotNull { cell(it.id, date)?.shiftId }.groupingBy { it }.eachCount()

    /** [TEAM-15] Days short of [Team.need]: date → (kind → how many missing). */
    fun short(need: Map<Long, Int>): Map<LocalDate, Map<Long, Int>> = if (need.isEmpty()) emptyMap() else days.associateWith { d ->
        val c = count(d)
        need.mapNotNull { (k, n) -> (n - (c[k] ?: 0)).takeIf { it > 0 }?.let { k to it } }.toMap()
    }.filterValues { it.isNotEmpty() }

    /** One member's days as their own calendar would hold them: what goes in their link (설계 2-1 "그 사람 근무만"). */
    fun daysOf(memberId: String): Map<LocalDate, Long> = days.mapNotNull { d -> cell(memberId, d)?.let { d to it.shiftId } }.toMap()

    /**
     * 1.38 같이 쉬는 사람: on each of [memberId]'s days off, the other active members off that day, in the team's order. [isOff]
     * says which kinds are days off.
     */
    fun offTogether(memberId: String, isOff: (Long) -> Boolean): Map<LocalDate, List<String>> = days.mapNotNull { d ->
        val mine = cell(memberId, d)?.shiftId ?: return@mapNotNull null
        if (!isOff(mine)) return@mapNotNull null
        val with = members.filter { it.active && it.id != memberId && cell(it.id, d)?.shiftId?.let(isOff) == true }.map { it.name }
        if (with.isEmpty()) null else d to with
    }.toMap()

    /** [TEAM-13] The cells that differ from [other] (the confirmed board): what a 다시 확정 tells, and what L2 will notify. */
    fun changesFrom(other: TeamBoard): List<Triple<String, LocalDate, Pair<Long?, Long?>>> =
        (cells.keys + other.cells.keys).distinct().mapNotNull { k ->
            val now = cells[k]?.shiftId
            val was = other.cells[k]?.shiftId
            if (now == was) null else Triple(k.first, k.second, was to now)
        }.sortedWith(compareBy({ it.second }, { it.first }))

    companion object {
        fun of(month: YearMonth, members: List<TeamMember>, cells: Collection<TeamCell>): TeamBoard =
            TeamBoard(month, members.sortedBy { it.sort }, cells.filter { YearMonth.from(it.date) == month }.associateBy { it.memberId to it.date })
    }
}

/** [TEAM-11] 되돌리기·다시 하기 over [TeamBoard] steps, as the 한 달 근무 키패드 has them. At most [LIMIT] steps are kept. */
class TeamEditor(start: TeamBoard) {
    private val back = ArrayDeque<TeamBoard>()
    private val ahead = ArrayDeque<TeamBoard>()
    var board: TeamBoard = start
        private set

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = ahead.isNotEmpty()

    fun apply(change: (TeamBoard) -> TeamBoard): TeamBoard {
        val next = change(board)
        if (next == board) return board
        back.addLast(board)
        while (back.size > LIMIT) back.removeFirst()
        ahead.clear()
        board = next
        return board
    }

    fun undo(): TeamBoard { if (back.isNotEmpty()) { ahead.addLast(board); board = back.removeLast() }; return board }
    fun redo(): TeamBoard { if (ahead.isNotEmpty()) { back.addLast(board); board = ahead.removeLast() }; return board }

    companion object { const val LIMIT = 200 }
}

/** [TEAM-11] Filling a new month from the one before. */
object TeamCopy {
    /**
     * 지난달 복사 (설계 2-1): each member keeps going in their own rhythm. When a member's last month repeats with a cycle of
     * [MAX_CYCLE] days or less (주주야야비휴, 2222…), the new month continues it from where it stopped; otherwise the same
     * weekday of the matching week is copied (a 일근 team's 월~금). Only empty, unlocked cells are filled.
     */
    fun nextMonth(last: TeamBoard, into: TeamBoard): TeamBoard = into.members.fold(into) { b, m ->
        val prev = last.days.map { last.cell(m.id, it)?.shiftId }
        val cycle = cycleOf(prev)
        b.days.fold(b) { acc, d ->
            if (acc.cell(m.id, d) != null) return@fold acc
            val code = if (cycle != null) {
                val offset = java.time.temporal.ChronoUnit.DAYS.between(last.days.last(), d).toInt()
                cycle[(prev.size - 1 + offset) % cycle.size]
            } else {
                // The same weekday, counted back by whole weeks into last month.
                generateSequence(d.minusWeeks(1)) { it.minusWeeks(1) }.take(6).firstOrNull { YearMonth.from(it) == last.month }
                    ?.let { last.cell(m.id, it)?.shiftId }
            }
            if (code == null) acc else acc.paint(m.id, d, code, CellSource.COPY)
        }
    }

    const val MAX_CYCLE = 14
    /** At least this many days of a row must be drawn to trust its cycle. */
    const val MIN_DRAWN = 10

    /** The shortest cycle that the row repeats (as step 0 = day 1), or null when it repeats none of [MAX_CYCLE] or less. */
    fun cycleOf(row: List<Long?>): List<Long>? {
        val drawn = row.count { it != null }
        if (drawn < MIN_DRAWN || row.any { it == null }) return null
        for (n in 1..MAX_CYCLE) {
            if (n * 2 > row.size) break
            if (row.indices.all { row[it] == row[it % n] }) return row.take(n).map { it!! }
        }
        return null
    }
}

/** [TEAM-09] 팀원 붙여넣기 (설계 2-1 "한 줄에 한 명, 붙여 넣어도 돼요"). */
object TeamPaste {
    /** Words after a name that are a job, not part of it ("민지 책임", "김준호 RN"). */
    val ROLES = setOf("책임", "수간호사", "주임", "조장", "반장", "팀장", "파트장", "선임", "신규", "RN", "CN", "HN", "PN", "AN", "간호사", "조무사", "보호사")

    data class Person(val name: String, val role: String, val skill: Skill)

    /**
     * One person a line; numbers and bullets in front ("1. 민지", "- 준호") and a role after the name go apart. A name written
     * twice gets "2" after it the second time, so two 민지 never share a row.
     */
    fun people(text: String): List<Person> {
        val seen = HashMap<String, Int>()
        return text.lines().mapNotNull { raw ->
            // 1.38 PC 1차 ("지우 (주임)"): a role in brackets or after a slash is a role too; a ★ after a name means 책임.
            val star = raw.contains('★')
            val line = raw.replace(Regex("""^\s*(\d+[.)]|[-•·*])\s*"""), "").replace('★', ' ')
                .replace(Regex("""[()\[\]{}/,]"""), " ").trim().replace(Regex("""\s+"""), " ")
            if (line.isEmpty()) return@mapNotNull null
            val words = line.split(" ") + if (star && line.split(" ").none { it == "책임" }) listOf("책임") else emptyList()
            val role = words.drop(1).filter { it in ROLES || it.uppercase() in ROLES }.joinToString(" ")
            val name = words.filterIndexed { i, w -> i == 0 || (w !in ROLES && w.uppercase() !in ROLES) }.joinToString(" ").take(NAME_MAX)
            val skill = when {
                role.contains("신규") -> Skill.NEW
                role.isNotEmpty() && role != "간호사" && role != "RN" -> Skill.CHARGE
                else -> Skill.REG
            }
            val n = (seen[name] ?: 0) + 1
            seen[name] = n
            Person(if (n == 1) name else "$name$n", role, skill)
        }.take(MAX_MEMBERS)
    }

    const val NAME_MAX = 20
    /** 설계 2-1 "팀 5~30명"; L1 keeps 60 rows at most so a big 병동 still fits. */
    const val MAX_MEMBERS = 60
}

/**
 * [TEAM-12] 종이 표 사진으로 시작 (순번 485): the rows a photo or pasted text was read into ([com.bamnat.core.imports.DutyRoster], the
 * same 합의 읽기 the 한 달 근무 넣기 uses) become members and cells. A row without a name gets "이름 없음 n" for the leader to fix.
 */
object TeamImport {
    data class Result(val people: List<TeamPaste.Person>, val cells: Map<Int, Map<LocalDate, Long>>, val unsure: Map<Int, Set<LocalDate>>)

    fun fromRows(rows: List<RosterRow>, month: YearMonth, offId: Long?): Result {
        val people = mutableListOf<TeamPaste.Person>()
        val cells = HashMap<Int, Map<LocalDate, Long>>()
        val unsure = HashMap<Int, Set<LocalDate>>()
        val named = TeamPaste.people(rows.mapIndexed { i, r -> r.label.ifBlank { "이름 없음 ${i + 1}" } }.joinToString("\n"))
        rows.forEachIndexed { i, r ->
            people += named.getOrElse(i) { TeamPaste.Person("이름 없음 ${i + 1}", "", Skill.REG) }
            cells[i] = r.cells.filterKeys { it in 1..month.lengthOfMonth() }.mapNotNull { (day, c) ->
                val id = when (c) {
                    is DutyCell.Shift -> c.shiftTypeId
                    DutyCell.Off -> offId
                    else -> null
                } ?: return@mapNotNull null
                month.atDay(day) to id
            }.toMap()
            unsure[i] = r.doubtful(month.lengthOfMonth()).map(month::atDay).toSet()
        }
        return Result(people, cells, unsure)
    }
}

/** [TEAM-14] 두 팀 겹침 알림 (설계 2-1 130): the same name on two of the leader's teams, working on the same day in both. */
object TeamOverlap {
    data class Clash(val name: String, val date: LocalDate, val teams: List<String>)

    fun clashes(boards: Map<Team, TeamBoard>, offIds: Set<Long>): List<Clash> {
        val byName = boards.flatMap { (t, b) -> b.members.filter { it.active }.map { m -> Triple(norm(m.name), t, m to b) } }.groupBy { it.first }
        return byName.filterValues { list -> list.map { it.second.id }.distinct().size > 1 }.flatMap { (_, list) ->
            val days = list.flatMap { (_, _, mb) -> mb.second.daysOf(mb.first.id).filterValues { it !in offIds }.keys }.groupingBy { it }.eachCount()
            days.filterValues { it > 1 }.keys.sorted().map { d ->
                Clash(list.first().third.first.name, d, list.filter { (_, _, mb) -> mb.second.daysOf(mb.first.id)[d]?.let { it !in offIds } == true }.map { it.second.name })
            }
        }
    }

    private fun norm(name: String) = name.replace(" ", "").lowercase()
}
