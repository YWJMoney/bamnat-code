package com.bamnat.core.team

import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.TeamCheck.asType
import java.time.LocalDate
import java.time.YearMonth

/** 1.38 [P3-04][TEAM-19] 희망 휴무 (순번 491, 설계 1-3 `team_wish`): what a member asked for one day. */
/**
 * What a member asks for a day. 1.38 병동 신청표 (사용자 2026-11-06, 종이 "off·연차·오전·D·N"): 연차, 반차, 교육, and one kind of
 * work ([TeamWish.shiftId]) besides 쉬기·일하기·나이트 빼기. [rests]: the day is asked off, so it counts toward the day's 상한.
 */
enum class WishKind(val label: String, val short: String, val rests: Boolean) {
    OFF("쉬고 싶어요", "오프", true), WORK("일하고 싶어요", "근무", false), AVOID_NIGHT("나이트 빼 주세요", "N빼기", false),
    LEAVE("연차", "연차", true), HALF("반차", "반차", false), EDU("교육", "교육", true), SHIFT("이 근무 원해요", "희망", false);

    /** How it is kept in a row: the name, and the kind of work after a colon for [SHIFT] ("SHIFT:3"). */
    fun code(shiftId: Long?): String = if (this == SHIFT && shiftId != null) "$name:$shiftId" else name

    companion object {
        /** [code] back; null for a kind this version does not know (a newer app's row is skipped, not misread). */
        fun parse(code: String): Pair<WishKind, Long?>? {
            val k = entries.firstOrNull { it.name == code.substringBefore(':') } ?: return null
            return k to code.substringAfter(':', "").toLongOrNull()
        }
    }
}

/**
 * [shiftId]: the kind of work a [WishKind.SHIFT] asks for. [at]: when it was asked (서버 시각, 선착순의 근거). [note]: the member's
 * one line for the month ("NN off 원해요").
 */
data class TeamWish(
    val memberId: String, val date: LocalDate, val kind: WishKind = WishKind.OFF,
    val shiftId: Long? = null, val at: Long? = null, val note: String? = null,
)

/**
 * [TEAM-19] 희망 휴무 without a server (설계 L2 "링크·QR·파일" of which the 파일·글 way needs no 처리방침 3.3): a member's app writes
 * a line the member sends by messenger, and the leader pastes what came. "[밤낮 희망] 11월 3 7 15 쉬고 / 20 나이트 빼고".
 * A leader's own note works too: "민지 3, 7, 15" — a name of the team then the days.
 */
object TeamWishText {
    const val TAG = "[밤낮 희망]"

    fun write(month: YearMonth, wishes: List<TeamWish>, name: String = ""): String = buildString {
        append(TAG); if (name.isNotBlank()) append(' ').append(name)
        append(' ').append(month.monthValue).append("월")
        WishKind.entries.filter { it in WORD }.forEach { k ->
            val days = wishes.filter { it.kind == k && YearMonth.from(it.date) == month }.map { it.date.dayOfMonth }.sorted()
            if (days.isNotEmpty()) append(' ').append(days.joinToString(" ")).append(' ').append(WORD.getValue(k)).append(" /")
        }
    }.trimEnd(' ', '/')

    private val WORD = mapOf(WishKind.OFF to "쉬고", WishKind.WORK to "일하고", WishKind.AVOID_NIGHT to "나이트 빼고",
        WishKind.LEAVE to "연차", WishKind.HALF to "반차", WishKind.EDU to "교육")

    /**
     * Each line of [text] for the member whose name it holds (the longest name found wins, so "민지2" is not "민지"); a line
     * without a team name goes to [fallback] when given. Days outside [month] or past its end are left out.
     */
    fun read(text: String, month: YearMonth, members: List<TeamMember>, fallback: String? = null): List<TeamWish> {
        val out = LinkedHashSet<TeamWish>()
        for (raw in text.lines()) {
            val line = raw.replace(TAG, " ").trim()
            if (line.isEmpty()) continue
            val who = members.filter { it.name.isNotBlank() && line.contains(it.name) }.maxByOrNull { it.name.length }?.id ?: fallback ?: continue
            val body = members.firstOrNull { it.id == who }?.name?.let { line.replace(it, " ") } ?: line
            // Numbers wait until a kind word says what they are ("3 7 쉬고 20 나이트 빼고"); numbers left at the end are 쉬는 날.
            val pending = ArrayList<Int>()
            fun flush(k: WishKind) { pending.filter { it in 1..month.lengthOfMonth() }.forEach { out += TeamWish(who, month.atDay(it), k) }; pending.clear() }
            Regex("""(\d{1,2})\s*월|(\d{1,2})|나이트\s*빼|야간\s*빼|일하고|근무하고|쉬고|휴무|오프|연차|반차|교육|(?i:off)""").findAll(body).forEach { m ->
                when {
                    m.groupValues[1].isNotEmpty() -> Unit
                    m.groupValues[2].isNotEmpty() -> pending += m.groupValues[2].toInt()
                    m.value == "연차" -> flush(WishKind.LEAVE)
                    m.value == "반차" -> flush(WishKind.HALF)
                    m.value == "교육" -> flush(WishKind.EDU)
                    m.value.startsWith("나이트") || m.value.startsWith("야간") -> flush(WishKind.AVOID_NIGHT)
                    m.value.startsWith("일하") || m.value.startsWith("근무") -> flush(WishKind.WORK)
                    else -> flush(WishKind.OFF)
                }
            }
            flush(WishKind.OFF)
        }
        return out.toList()
    }
}

/**
 * [TEAM-20] L3 자동 채우기 (순번 495·496, 설계 2-1 [135]): only the empty cells, never a locked or painted one, so the leader's
 * hand always wins. Each day each kind of [Team.need] is filled by whoever can take it without breaking the rules ([DutyCheck]
 * over the member's whole month with the team's [RestRules]: 연속 근무, 나이트 뒤 쉼, 11시간, 월 나이트), 주 52시간 (근로기준법
 * 제53조) or a 희망 휴무, and of those the one with the fewest hours so far, then the fewest of that kind (공정성: 쉬는 날이 고르게).
 * What is left becomes the team's off day. A day that still lacks people is told ([Plan.short]) rather than forced: the rules
 * and the 52 hours come before the head count.
 * 1.38 PC 2차 (4a5c3828): the check used to look three days either side only, so a 24일 연속·주 84시간 row went in as "부족 없음".
 * [plans] tries a few orders and keeps the safest, then the fullest, for "여러 안 중 고르기". Pure Kotlin, 30명×31일 in about a second.
 */
object TeamAuto {
    data class Plan(
        val board: TeamBoard, val filled: Int, val short: Map<LocalDate, Map<Long, Int>>, val spread: Int, val wishesKept: Int, val wishesMissed: Int,
        /** 주 52시간을 넘는 주 (사람·주 하나에 1). */
        val overWeeks: Int = 0,
        /** The other rule signs ([TeamCheck.Kind.RULE]): 연속 근무, 휴식, 나이트 뒤 쉼. */
        val ruleSigns: Int = 0,
        /** The longest run of work days anyone has. */
        val maxRun: Int = 0,
        /** Hours of the month, most against least. */
        val hourSpread: Int = 0,
    ) {
        val danger: Int get() = overWeeks + ruleSigns
        val shortPeople: Int get() = short.values.sumOf { it.values.sum() }
    }

    private fun weekOf(d: LocalDate): LocalDate = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    /** The kind of day a resting wish is painted with: the team's own 연차·교육 day when it has one, else its off day. */
    fun restShift(team: Team, kind: WishKind): TeamShift? {
        val offs = team.shifts.filter { it.isOff }
        val word = when (kind) { WishKind.LEAVE -> "연차"; WishKind.EDU -> "교육"; else -> null }
        return word?.let { w -> offs.firstOrNull { it.label.contains(w) || it.name.contains(w) } } ?: offs.firstOrNull()
    }

    fun fill(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), seed: Int = 0, prev: TeamBoard? = null,
             past: Map<String, TeamLedger.Past> = emptyMap(), next: TeamBoard? = null): Plan {
        val types = team.shifts.associateBy { it.id }
        val asTypes = types.mapValues { it.value.asType() }
        val off = team.shifts.firstOrNull { it.isOff }
        val work = team.shifts.filter { !it.isOff }
        val active = board.members.filter { it.active }.let { l -> if (seed == 0) l else l.shuffled(java.util.Random(seed.toLong())) }
        val wish = wishes.associateBy { it.memberId to it.date }
        var b = board
        val startEmpty = board.empty
        // Wishes for a day off (오프·연차·교육) land first, where the cell is still empty.
        wishes.filter { it.kind.rests }.forEach { w ->
            val s = restShift(team, w.kind) ?: return@forEach
            if (b.cell(w.memberId, w.date) == null) b = b.paint(w.memberId, w.date, s.id, CellSource.AUTO)
        }
        val count = HashMap<Pair<String, Long>, Int>()
        val minutes = HashMap<String, Int>()
        val weekends = HashMap<String, Int>()
        val week = HashMap<Pair<String, LocalDate>, Int>()
        fun note(m: String, d: LocalDate, id: Long) {
            count.merge(m to id, 1, Int::plus)
            if (types[id]?.isOff == false && d.dayOfWeek.value >= 6) weekends.merge(m, 1, Int::plus)
            types[id]?.let { val min = TeamCheck.minutesOf(it); minutes.merge(m, min, Int::plus); week.merge(m to weekOf(d), min, Int::plus) }
        }
        b.cells.values.forEach { note(it.memberId, it.date, it.shiftId) }
        // 2.0.0 [RVJ-30] (검토 PAYU-08) The days of last month and next month in the weeks this month shares with them are hours
        // of those weeks already; only the week's sum takes them (the month's own fairness stays the month's).
        val shared = setOf(weekOf(board.days.first()), weekOf(board.days.last()))
        listOfNotNull(prev, next).forEach { o ->
            o.cells.values.forEach { c -> if (weekOf(c.date) in shared) types[c.shiftId]?.let { week.merge(c.memberId to weekOf(c.date), TeamCheck.minutesOf(it), Int::plus) } }
        }
        fun row(trial: TeamBoard, m: String) = board.days.map { it to trial.cell(m, it)?.shiftId?.let(asTypes::get) }
        val carried = board.members.associate { it.id to TeamCheck.carry(prev, it.id, asTypes) }
        // The member's signs now, kept while their row does not change, so each try is checked against what it adds.
        // The text too, so a run already too long that grows ("7일 연속" → "8일 연속") counts as new.
        val before = HashMap<String, Set<String>>()
        fun signs(trial: TeamBoard, m: String) = TeamCheck.withCarry(carried[m].orEmpty(), row(trial, m), rules).map { "${it.kind} ${it.date} ${it.text}" }.toSet()
        fun breaks(m: TeamMember, d: LocalDate, id: Long, trial: TeamBoard): Boolean {
            val w = wish[m.id to d]
            val t = types.getValue(id)
            if (w?.kind?.rests == true) return true
            if (w?.kind == WishKind.AVOID_NIGHT && DutyCheck.isNight(t.asType())) return true
            if (rules.weekLimit && (week[m.id to weekOf(d)] ?: 0) + TeamCheck.minutesOf(t) > TeamCheck.WEEK_LIMIT_MINUTES) return true
            val had = before.getOrPut(m.id) { signs(trial, m.id) }
            return (signs(trial.paint(m.id, d, id, CellSource.AUTO), m.id) - had).isNotEmpty()
        }
        // 1.38 PC 6db1056b (나이트 15번 넷·0번 둘, 네 안이 같음): one score for who takes a kind — the hours so far, each of that kind
        // already taken as a half shift more (so nights and evenings go round), weekends worked the same, and per 안 [seed] a small
        // different nudge so each 안 is really another way. (Nights first each day was tried: it spread nights but left 38 short, not 16.)
        val jitter = java.util.Random(seed * 7919L + 1)
        val nudge = active.associate { it.id to if (seed == 0) 0.0 else jitter.nextDouble() * 6.0 }
        fun score(m: TeamMember, d: LocalDate, s: TeamShift): Double {
            val hours = (minutes[m.id] ?: 0) / 60.0
            val kind = (count[m.id to s.id] ?: 0) * (TeamCheck.minutesOf(s) / 60.0) * (if (DutyCheck.isNight(s.asType())) 1.5 else 0.5)
            val weekend = if (d.dayOfWeek.value >= 6) (weekends[m.id] ?: 0) * 6.0 else 0.0
            // 1.38 [P3-X-04] 지난 달들: more nights or weekends than the team so far weighs the same as that many more this month.
            val owed = past[m.id]?.let { p ->
                (if (DutyCheck.isNight(s.asType())) p.nights * (TeamCheck.minutesOf(s) / 60.0) * 1.5 else 0.0) + (if (d.dayOfWeek.value >= 6) p.weekends * 6.0 else 0.0)
            } ?: 0.0
            return hours + kind + weekend + owed + (nudge[m.id] ?: 0.0)
        }
        val order = work
        for (d in board.days) {
            for (s in order) {
                val need = team.need[s.id] ?: continue
                var have = active.count { b.cell(it.id, d)?.shiftId == s.id }
                while (have < need) {
                    val pick = active.filter { b.cell(it.id, d) == null && !breaks(it, d, s.id, b) }
                        .sortedWith(compareBy<TeamMember>({ wish[it.id to d].let { w -> if (w?.kind == WishKind.WORK || (w?.kind == WishKind.SHIFT && w.shiftId == s.id)) 0 else 1 } }, { score(it, d, s) }))
                        .firstOrNull() ?: break
                    b = b.paint(pick.id, d, s.id, CellSource.AUTO); note(pick.id, d, s.id); before.remove(pick.id); have++
                }
            }
            if (off != null) active.forEach { m -> if (b.cell(m.id, d) == null) { b = b.paint(m.id, d, off.id, CellSource.AUTO); before.remove(m.id) } }
        }
        return measure(team, board, b, startEmpty, wishes, rules, prev, next)
    }

    /** Whether [w] is kept by the cell [c] (null: nothing painted there). 반차 asks nothing of the board, so it is always kept. */
    fun kept(team: Team, w: TeamWish, c: TeamShift?): Boolean = when (w.kind) {
        WishKind.OFF -> c?.isOff == true
        WishKind.LEAVE, WishKind.EDU -> c?.isOff == true
        WishKind.WORK -> c != null && !c.isOff
        WishKind.AVOID_NIGHT -> c != null && !DutyCheck.isNight(c.asType())
        WishKind.SHIFT -> c != null && c.id == w.shiftId
        WishKind.HALF -> true
    }

    /** How a filled board stands: short days, wishes, and the dangers the 안 line shows before the leader picks it. */
    fun measure(team: Team, start: TeamBoard, b: TeamBoard, startEmpty: Int, wishes: List<TeamWish>, rules: RestRules, prev: TeamBoard? = null,
                next: TeamBoard? = null): Plan {
        val types = team.shifts.associateBy { it.id }
        val active = b.members.filter { it.active }
        val nights = active.map { m -> b.days.count { d -> b.cell(m.id, d)?.shiftId?.let(types::get)?.let { DutyCheck.isNight(it.asType()) } == true } }
        val kept = wishes.count { w -> kept(team, w, b.cell(w.memberId, w.date)?.shiftId?.let(types::get)) }
        val signs = TeamCheck.check(team, b, rules, prev, next)
        val runs = active.map { m ->
            var best = 0; var cur = 0
            b.days.forEach { d -> if (b.cell(m.id, d)?.shiftId?.let(types::get)?.isOff == false) { cur++; best = maxOf(best, cur) } else cur = 0 }
            best
        }
        val hours = active.map { m -> b.days.sumOf { d -> b.cell(m.id, d)?.shiftId?.let(types::get)?.let(TeamCheck::minutesOf) ?: 0 } / 60 }
        return Plan(b, startEmpty - b.empty, b.short(team.need), (nights.maxOrNull() ?: 0) - (nights.minOrNull() ?: 0), kept, wishes.size - kept,
            overWeeks = signs.count { it.kind == TeamCheck.Kind.WEEK_HOURS }, ruleSigns = signs.count { it.kind == TeamCheck.Kind.RULE },
            maxRun = runs.maxOrNull() ?: 0, hourSpread = (hours.maxOrNull() ?: 0) - (hours.minOrNull() ?: 0))
    }

    /**
     * [TEAM-20] A few orders, best first: no danger (52시간·연속·휴식) first, then the fewest short, wishes kept, the evenest hours
     * and nights. A plan only adds what the board's own signs did not have, so a leader's painted danger counts in every 안 alike.
     */
    fun plans(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), tries: Int = 8, keep: Int = 4, prev: TeamBoard? = null,
              past: Map<String, TeamLedger.Past> = emptyMap(), next: TeamBoard? = null): List<Plan> =
        (0 until tries).map { fill(team, board, wishes, rules, it, prev, past, next) }
            .sortedWith(compareBy({ it.danger }, { it.shortPeople }, { -it.wishesKept }, { it.hourSpread + it.spread * 4 }))
            // 1.38 PC 6db1056b: 안 that read the same on their line are one choice, not four.
            .distinctBy { listOf(it.danger, it.shortPeople, it.short.size, it.wishesKept, it.hourSpread, it.spread, it.maxRun) }
            .take(keep)

    /**
     * The hours the month asks of each person on average: [Team.need] × each kind's hours over the active members, a week.
     * Above 52 the need cannot be met inside the law with these people; close to it, short days are likely. [RVJ-29] Hours
     * worked, the 휴게 taken off, as the 52시간 sign counts them.
     */
    fun weekHoursNeeded(team: Team, board: TeamBoard): Double {
        val people = board.members.count { it.active }
        if (people == 0) return 0.0
        val perDay = team.shifts.filter { !it.isOff }.sumOf { (team.need[it.id] ?: 0) * TeamCheck.minutesOf(it) }
        return perDay * 7.0 / people / 60.0
    }
}
