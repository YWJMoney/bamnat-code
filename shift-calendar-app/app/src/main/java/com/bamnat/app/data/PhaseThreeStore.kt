package com.bamnat.app.data

import androidx.room.withTransaction
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.PayrollLineEntity
import com.bamnat.app.data.db.PayrollMonthEntity
import com.bamnat.app.data.db.StaffEntity
import com.bamnat.app.data.db.StaffWorkEntity
import com.bamnat.app.data.db.TeamCellEntity
import com.bamnat.app.data.db.TeamEntity
import com.bamnat.app.data.db.TeamMemberEntity
import com.bamnat.app.data.db.TeamMonthEntity
import com.bamnat.app.data.db.WorkplaceEntity
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.LedgerCsv
import com.bamnat.core.boss.LedgerLine
import com.bamnat.core.boss.StaffAllowance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.CellSource
import com.bamnat.core.team.MonthState
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamMonth
import com.bamnat.core.team.TeamShift
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.38 [P3-01][TEAM-09][BOSS-01] Phase 3's tables (DB 27) as the core's models: the 리더 팀 근무표 and the 사장님 급여 대장. Everything
 * stays on this phone; the screens that use it are off until their flag (`team.leader`, `boss.payroll`).
 */
class PhaseThreeStore(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val teams get() = db.teamDao()
    private val pay get() = db.payrollDao()

    // ---- 팀 ----

    val teamList: Flow<List<Team>> = teams.observeTeams().map { l -> l.map { it.toModel() } }

    fun members(teamId: String): Flow<List<TeamMember>> = teams.observeMembers(teamId).map { l -> l.map { it.toModel() } }

    fun board(teamId: String, month: YearMonth): Flow<TeamBoard> =
        combine(members(teamId), teams.observeCells(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())) { m, c ->
            TeamBoard.of(month, m, c.map { it.toModel() })
        }

    fun month(teamId: String, month: YearMonth): Flow<Pair<TeamMonth, Map<Pair<String, LocalDate>, Long>>> =
        teams.observeMonth(teamId, month.toString()).map { e ->
            (e?.toModel() ?: TeamMonth(teamId, month)) to (e?.confirmed?.let(::decodeConfirmed) ?: emptyMap())
        }

    suspend fun boardNow(teamId: String, month: YearMonth): TeamBoard = TeamBoard.of(month,
        teams.members().filter { it.teamId == teamId }.map { it.toModel() },
        teams.cells(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { it.toModel() })

    suspend fun saveTeam(t: Team) = teams.upsertTeam(t.toEntity())

    suspend fun saveMembers(list: List<TeamMember>) = teams.upsertMembers(list.map { it.toEntity() })

    suspend fun removeMember(id: String) = db.withTransaction { teams.deleteMemberCells(id); teams.deleteMemberWishes(id); teams.deleteMember(id) }

    /** [TEAM-19] The month's 희망 휴무. */
    fun wishes(teamId: String, month: YearMonth): Flow<List<com.bamnat.core.team.TeamWish>> =
        teams.observeWishes(teamId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { l ->
            l.mapNotNull { e -> runCatching { com.bamnat.core.team.TeamWish(e.memberId, LocalDate.ofEpochDay(e.epochDay), com.bamnat.core.team.WishKind.valueOf(e.kind)) }.getOrNull() }
        }

    /** [TEAM-19] Wishes in: one a member and day, the newest wins. [via] is LEADER or TEXT. */
    suspend fun addWishes(teamId: String, list: List<com.bamnat.core.team.TeamWish>, via: String) =
        teams.upsertWishes(list.map { com.bamnat.app.data.db.TeamWishEntity(teamId, it.memberId, it.date.toEpochDay(), it.kind.name, clock(), via) })

    suspend fun removeWish(memberId: String, date: LocalDate) = teams.deleteWish(memberId, date.toEpochDay())

    suspend fun deleteTeam(id: String) = teams.deleteTeam(id)

    /** [TEAM-11] The board as it is now, the whole month in one write (so 되돌리기 never leaves half a step). */
    suspend fun saveBoard(teamId: String, b: TeamBoard) =
        teams.replaceMonth(teamId, b.month.atDay(1).toEpochDay(), b.month.atEndOfMonth().toEpochDay(), b.cells.values.map { it.toEntity(teamId) })

    /** [TEAM-13] 확정: the month CONFIRMED one version up, and the board as it was confirmed, for 바뀐 칸 next time. */
    suspend fun confirm(teamId: String, b: TeamBoard, was: TeamMonth): TeamMonth {
        val m = was.copy(state = MonthState.CONFIRMED, version = was.version + 1, confirmedAt = clock())
        teams.upsertMonth(TeamMonthEntity(teamId, b.month.toString(), m.state.name, m.version, m.confirmedAt, encodeConfirmed(b)))
        return m
    }

    // ---- 사업장 ----

    val workplaces: Flow<List<Workplace>> = pay.observeWorkplaces().map { l -> l.map { it.toModel() } }

    fun staff(placeId: String): Flow<List<StaffMember>> = pay.observeStaff(placeId).map { l -> l.map { it.toModel() } }

    fun work(placeId: String, month: YearMonth): Flow<List<StaffWork>> =
        pay.observeWork(placeId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { l -> l.mapNotNull { it.toModel() } }

    fun payrollMonth(placeId: String, month: YearMonth): Flow<PayrollMonthEntity?> = pay.observeMonth(placeId, month.toString())

    suspend fun saveWorkplace(p: Workplace) = pay.upsertWorkplace(p.toEntity(pay.workplaces().firstOrNull { it.id == p.id }?.createdAt ?: clock()))

    suspend fun saveStaff(s: StaffMember, placeId: String, sort: Int = 0) = pay.upsertStaff(s.toEntity(placeId, sort))

    suspend fun deleteStaff(id: String) = pay.deleteStaff(id)

    /** [BOSS-03] A day that went differently; null puts the day back to the usual week. */
    suspend fun setWork(staffId: String, date: LocalDate, work: StaffWork?) {
        if (work == null) pay.deleteWork(staffId, date.toEpochDay()) else pay.upsertWork(work.toEntity())
    }

    /** [BOSS-04] 대장 닫기: each line as it is now (the 임금대장 row), kept even when the rules move on. */
    suspend fun close(placeId: String, month: YearMonth, lines: List<LedgerLine>, rateVersion: String) = pay.close(
        PayrollMonthEntity(placeId, month.toString(), "CLOSED", rateVersion, clock()),
        lines.map { l ->
            PayrollLineEntity(placeId, month.toString(), l.staff.id, l.gross,
                items = JSONArray(LedgerCsv.cells(l)).toString(),
                method = JSONArray().apply { l.lines.forEach { put(JSONObject().put("label", it.label).put("amount", it.amount).put("formula", it.formula)) } }.toString(),
                deduct = "", net = null, paidAt = null, slipSentAt = null)
        },
    )

    /** [BOSS-10] 지급 체크·명세서 보냄, per staff member of a month; a month not closed keeps a line of its own for it. */
    fun lines(placeId: String, month: YearMonth): Flow<List<PayrollLineEntity>> = pay.observeLines(placeId, month.toString())

    suspend fun mark(placeId: String, month: YearMonth, line: LedgerLine, paid: Boolean? = null, sent: Boolean? = null) = db.withTransaction {
        val now = clock()
        val was = pay.linesOf(placeId, month.toString()).firstOrNull { it.staffId == line.staff.id }
            ?: PayrollLineEntity(placeId, month.toString(), line.staff.id, line.gross, JSONArray(LedgerCsv.cells(line)).toString(), "[]", "", null, null, null)
        pay.upsertLines(listOf(was.copy(
            paidAt = when (paid) { null -> was.paidAt; true -> was.paidAt ?: now; false -> null },
            slipSentAt = when (sent) { null -> was.slipSentAt; true -> now; false -> null })))
    }

    suspend fun reopen(placeId: String, month: YearMonth, rateVersion: String) =
        pay.upsertMonth(PayrollMonthEntity(placeId, month.toString(), "OPEN", rateVersion, null))

    /** [BOSS-06] SHOP-11 직원 인건비 into a 사업장 once, when there is none yet. The old roster stays where it is. */
    suspend fun adoptRoster(roster: com.bamnat.core.work.StaffRoster, placeId: String): Workplace? = db.withTransaction {
        if (pay.workplaces().isNotEmpty() || roster.staff.isEmpty()) return@withTransaction null
        val (place, staff) = com.bamnat.core.boss.PayrollLedger.fromRoster(roster, placeId)
        pay.upsertWorkplace(place.toEntity(clock()))
        staff.forEachIndexed { i, s -> pay.upsertStaff(s.toEntity(place.id, i)) }
        place
    }

    companion object {
        // ---- 팀 rows ----

        fun TeamEntity.toModel() = Team(id, name, leaderName, decodeShifts(shifts), createdAt, archived, decodeNeed(need))
        fun Team.toEntity() = TeamEntity(id, name, leaderName, encodeShifts(shifts), encodeNeed(need), createdAt, archived)
        fun TeamMemberEntity.toModel() = TeamMember(id, teamId, name, role, runCatching { Skill.valueOf(skill) }.getOrDefault(Skill.REG),
            colorArgb, sort, linkId, linkToken, active)
        fun TeamMember.toEntity() = TeamMemberEntity(id, teamId, name, role, skill.name, colorArgb, sort, linkId, linkToken, active)
        fun TeamCellEntity.toModel() = TeamCell(memberId, LocalDate.ofEpochDay(epochDay), shiftId,
            runCatching { CellSource.valueOf(source) }.getOrDefault(CellSource.MANUAL), locked)
        fun TeamCell.toEntity(teamId: String) = TeamCellEntity(teamId, memberId, date.toEpochDay(), shiftId, source.name, locked)
        fun TeamMonthEntity.toModel() = TeamMonth(teamId, YearMonth.parse(month),
            runCatching { MonthState.valueOf(state) }.getOrDefault(MonthState.DRAFT), version, confirmedAt)

        fun encodeShifts(l: List<TeamShift>): String = JSONArray().apply {
            l.forEach { s ->
                put(JSONObject().put("id", s.id).put("label", s.label).put("name", s.name).put("color", s.colorArgb).put("off", s.isOff)
                    .putOpt("start", s.start?.toString()).putOpt("end", s.end?.toString()))
            }
        }.toString()

        fun decodeShifts(text: String): List<TeamShift> = runCatching {
            JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.map { o ->
                TeamShift(o.getLong("id"), o.optString("label"), o.optString("name"), o.optLong("color"), o.optBoolean("off"),
                    o.optString("start").takeIf { it.isNotEmpty() }?.let(LocalTime::parse), o.optString("end").takeIf { it.isNotEmpty() }?.let(LocalTime::parse))
            }
        }.getOrDefault(emptyList())

        fun encodeNeed(m: Map<Long, Int>): String = if (m.isEmpty()) "" else JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }.toString()
        fun decodeNeed(t: String): Map<Long, Int> = if (t.isBlank()) emptyMap() else runCatching {
            JSONObject(t).let { o -> o.keys().asSequence().associate { it.toLong() to o.getInt(it) } }
        }.getOrDefault(emptyMap())

        fun encodeConfirmed(b: TeamBoard): String = JSONObject().apply {
            b.cells.values.forEach { c -> put("${c.memberId}:${c.date.toEpochDay()}", c.shiftId) }
        }.toString()

        fun decodeConfirmed(t: String): Map<Pair<String, LocalDate>, Long> = if (t.isBlank()) emptyMap() else runCatching {
            JSONObject(t).let { o ->
                o.keys().asSequence().mapNotNull { k ->
                    val i = k.lastIndexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                    (k.substring(0, i) to LocalDate.ofEpochDay(k.substring(i + 1).toLong())) to o.getLong(k)
                }.toMap()
            }
        }.getOrDefault(emptyMap())

        // ---- 사업장 rows ----

        fun WorkplaceEntity.toModel() = Workplace(id, name, fivePlus, payday, legalBreaks, DayOfWeek.of(weekStart.coerceIn(1, 7)), teamId)
        fun Workplace.toEntity(createdAt: Long) = WorkplaceEntity(id, name, fivePlus, payday, legalBreaks, weekStart.value, teamId, createdAt)

        fun StaffEntity.toModel() = StaffMember(
            id = id, name = name, form = runCatching { WageForm.valueOf(form) }.getOrDefault(WageForm.HOUR), base = base,
            allowances = decodeAllowances(allowances), hiredOn = hiredOn?.let(LocalDate::ofEpochDay), leftOn = leftOn?.let(LocalDate::ofEpochDay),
            birth = birth?.let(LocalDate::ofEpochDay), job = job, insurance = decodeInsurance(insurance), weeklyHolidayPay = weeklyHolidayPay,
            weekly = decodeWeekly(weekly), memberId = memberId,
        )

        fun StaffMember.toEntity(placeId: String, sort: Int) = StaffEntity(
            id, placeId, name, form.name, base, encodeAllowances(allowances), hiredOn?.toEpochDay(), leftOn?.toEpochDay(), birth?.toEpochDay(),
            job, encodeInsurance(insurance), weeklyHolidayPay, encodeWeekly(weekly), memberId, sort,
        )

        fun StaffWorkEntity.toModel(): StaffWork? = runCatching {
            StaffWork(staffId, LocalDate.ofEpochDay(epochDay), WorkKind.valueOf(kind),
                startMinute?.let { LocalTime.ofSecondOfDay(it * 60L % 86_400) }, endMinute?.let { LocalTime.ofSecondOfDay(it * 60L % 86_400) })
        }.getOrNull()

        fun StaffWork.toEntity() = StaffWorkEntity(staffId, date.toEpochDay(), kind.name, start?.let { it.hour * 60 + it.minute }, end?.let { it.hour * 60 + it.minute })

        fun encodeAllowances(l: List<StaffAllowance>) = JSONArray().apply {
            l.forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("perDay", it.perDay)) }
        }.toString()

        fun decodeAllowances(t: String): List<StaffAllowance> = runCatching {
            JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .map { StaffAllowance(it.optString("name"), it.optLong("amount").coerceAtLeast(0), it.optBoolean("perDay")) }
        }.getOrDefault(emptyList())

        fun encodeInsurance(i: Insurance) = JSONObject().put("pension", i.pension).put("health", i.health).put("employment", i.employment).put("tax", i.tax.name).toString()
        fun decodeInsurance(t: String): Insurance = runCatching {
            JSONObject(t).let { Insurance(it.optBoolean("pension"), it.optBoolean("health"), it.optBoolean("employment"),
                runCatching { com.bamnat.core.boss.StaffTax.valueOf(it.optString("tax", "WAGE")) }.getOrDefault(com.bamnat.core.boss.StaffTax.WAGE)) }
        }.getOrDefault(Insurance())

        fun encodeWeekly(m: Map<DayOfWeek, StaffShift>) = JSONObject().apply {
            m.forEach { (d, s) -> put(d.value.toString(), JSONArray().put(s.start.hour * 60 + s.start.minute).put(s.end.hour * 60 + s.end.minute)) }
        }.toString()

        fun decodeWeekly(t: String): Map<DayOfWeek, StaffShift> = runCatching {
            JSONObject(t).let { o ->
                o.keys().asSequence().mapNotNull { k ->
                    val a = o.optJSONArray(k) ?: return@mapNotNull null
                    val d = k.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
                    DayOfWeek.of(d) to StaffShift(LocalTime.of(a.getInt(0) / 60 % 24, a.getInt(0) % 60), LocalTime.of(a.getInt(1) / 60 % 24, a.getInt(1) % 60))
                }.toMap()
            }
        }.getOrDefault(emptyMap())
    }
}
