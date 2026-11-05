package com.bamnat.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * 1.38 [P3-01] Version 27 (Phase 3 설계 1-3, 판 번호는 1.38 계획 7장: 가계부 26 다음): new tables only, every older row keeps its
 * meaning. The features that use them ship off (`team.leader`, `boss.payroll`); an empty table costs nothing.
 * Lists inside a row (근무 종류, 평소 주, 수당, 4대보험) are JSON text the app writes ([com.bamnat.app.data.PhaseThreeStore]).
 */

/** [TEAM-09] A team the leader draws on this phone (설계 1-3 `team`). */
@Entity(tableName = "team")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val leaderName: String,
    /** The kinds of day the board paints with, as JSON (copied from the leader's calendar). */
    val shifts: String,
    /** [TEAM-15] People needed per kind of day, as JSON `{"1":3}`; "" for no check. */
    val need: String,
    val createdAt: Long,
    val archived: Boolean,
)

@Entity(tableName = "team_member", indices = [Index("teamId")])
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    val teamId: String,
    val name: String,
    val role: String,
    val skill: String,
    val colorArgb: Long?,
    val sort: Int,
    /** [TEAM-13] The member's own 근무표 링크, as any 공유 링크 keeps it (D29). */
    val linkId: String?,
    val linkToken: String?,
    val active: Boolean,
)

@Entity(tableName = "team_cell", primaryKeys = ["memberId", "epochDay"], indices = [Index(value = ["teamId", "epochDay"])])
data class TeamCellEntity(
    val teamId: String,
    val memberId: String,
    val epochDay: Long,
    val shiftId: Long,
    val source: String,
    val locked: Boolean,
)

/** [TEAM-13] A month's state; [confirmed] is the board as last confirmed (JSON `memberId:epochDay=shiftId`), for 바뀐 칸. */
@Entity(tableName = "team_month", primaryKeys = ["teamId", "month"])
data class TeamMonthEntity(
    val teamId: String,
    /** "2026-11". */
    val month: String,
    val state: String,
    val version: Int,
    val confirmedAt: Long?,
    val confirmed: String,
)

/** [BOSS-01] 사업장 (설계 1-3 `workplace`): kept out of a backup that has no password. */
@Entity(tableName = "workplace")
data class WorkplaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fivePlus: Boolean?,
    val payday: Int?,
    val legalBreaks: Boolean,
    /** 1 = Monday … 7 = Sunday. */
    val weekStart: Int,
    val teamId: String?,
    val createdAt: Long,
)

@Entity(tableName = "staff", indices = [Index("workplaceId")])
data class StaffEntity(
    @PrimaryKey val id: String,
    val workplaceId: String,
    val name: String,
    /** HOUR, MONTH or DAY. */
    val form: String,
    val base: Long,
    val allowances: String,
    val hiredOn: Long?,
    val leftOn: Long?,
    val birth: Long?,
    val job: String,
    val insurance: String,
    val weeklyHolidayPay: Boolean,
    /** The usual week as JSON `{"1":[540,1080]}` (weekday → start and end minute). */
    val weekly: String,
    val memberId: String?,
    val sort: Int,
)

/** [BOSS-03] Only the days that went differently from the usual week (설계 139). */
@Entity(tableName = "staff_work", primaryKeys = ["staffId", "epochDay"])
data class StaffWorkEntity(
    val staffId: String,
    val epochDay: Long,
    val kind: String,
    val startMinute: Int?,
    val endMinute: Int?,
)

/** [BOSS-04] A month's ledger state: OPEN while it is worked out, CLOSED once the boss closes it (the lines below are kept as they were). */
@Entity(tableName = "payroll_month", primaryKeys = ["workplaceId", "month"])
data class PayrollMonthEntity(
    val workplaceId: String,
    val month: String,
    val state: String,
    val rateVersion: String,
    val closedAt: Long?,
)

/** [BOSS-04] One person's closed month as it was paid: 임금대장 is written at each payment (근로기준법 제48조 제1항). */
@Entity(tableName = "payroll_line", primaryKeys = ["workplaceId", "month", "staffId"])
data class PayrollLineEntity(
    val workplaceId: String,
    val month: String,
    val staffId: String,
    val gross: Long,
    /** The 임금대장 row (시행령 제27조 items) as JSON, so a closed month reads the same after the rules move on. */
    val items: String,
    val method: String,
    val deduct: String,
    val net: Long?,
    val paidAt: Long?,
    val slipSentAt: Long?,
)

@Dao
interface TeamDao {
    @Query("SELECT * FROM team WHERE archived = 0 ORDER BY createdAt")
    fun observeTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM team ORDER BY createdAt")
    suspend fun teams(): List<TeamEntity>

    @Query("SELECT * FROM team_member WHERE teamId = :teamId ORDER BY sort")
    fun observeMembers(teamId: String): Flow<List<TeamMemberEntity>>

    @Query("SELECT * FROM team_member ORDER BY teamId, sort")
    suspend fun members(): List<TeamMemberEntity>

    @Query("SELECT * FROM team_cell WHERE teamId = :teamId AND epochDay BETWEEN :from AND :to")
    fun observeCells(teamId: String, from: Long, to: Long): Flow<List<TeamCellEntity>>

    @Query("SELECT * FROM team_cell WHERE teamId = :teamId AND epochDay BETWEEN :from AND :to")
    suspend fun cells(teamId: String, from: Long, to: Long): List<TeamCellEntity>

    @Query("SELECT * FROM team_cell")
    suspend fun allCells(): List<TeamCellEntity>

    @Query("SELECT * FROM team_month WHERE teamId = :teamId AND month = :month")
    fun observeMonth(teamId: String, month: String): Flow<TeamMonthEntity?>

    @Query("SELECT * FROM team_month")
    suspend fun months(): List<TeamMonthEntity>

    @Upsert suspend fun upsertTeam(e: TeamEntity)
    @Upsert suspend fun upsertMembers(e: List<TeamMemberEntity>)
    @Upsert suspend fun upsertMonth(e: TeamMonthEntity)
    @Upsert suspend fun upsertCells(e: List<TeamCellEntity>)

    @Query("DELETE FROM team_cell WHERE teamId = :teamId AND epochDay BETWEEN :from AND :to")
    suspend fun clearCells(teamId: String, from: Long, to: Long)

    /** The month's cells as one write: what the board shows is what is kept, so 되돌리기 never leaves half a step. */
    @Transaction
    suspend fun replaceMonth(teamId: String, from: Long, to: Long, cells: List<TeamCellEntity>) {
        clearCells(teamId, from, to)
        if (cells.isNotEmpty()) upsertCells(cells)
    }

    @Query("DELETE FROM team_member WHERE id = :id")
    suspend fun deleteMember(id: String)

    @Query("DELETE FROM team_cell WHERE memberId = :memberId")
    suspend fun deleteMemberCells(memberId: String)

    @Transaction
    suspend fun deleteTeam(id: String) {
        deleteTeamCells(id); deleteTeamMembers(id); deleteTeamMonths(id); deleteTeamRow(id)
    }

    @Query("DELETE FROM team_cell WHERE teamId = :id") suspend fun deleteTeamCells(id: String)
    @Query("DELETE FROM team_member WHERE teamId = :id") suspend fun deleteTeamMembers(id: String)
    @Query("DELETE FROM team_month WHERE teamId = :id") suspend fun deleteTeamMonths(id: String)
    @Query("DELETE FROM team WHERE id = :id") suspend fun deleteTeamRow(id: String)

    /** Backup: every team row goes back in one step. */
    @Transaction
    suspend fun replaceAll(teams: List<TeamEntity>, members: List<TeamMemberEntity>, cells: List<TeamCellEntity>, months: List<TeamMonthEntity>) {
        wipeCells(); wipeMembers(); wipeMonths(); wipeTeams()
        teams.forEach { upsertTeam(it) }
        if (members.isNotEmpty()) upsertMembers(members)
        if (cells.isNotEmpty()) upsertCells(cells)
        months.forEach { upsertMonth(it) }
    }

    @Query("DELETE FROM team_cell") suspend fun wipeCells()
    @Query("DELETE FROM team_member") suspend fun wipeMembers()
    @Query("DELETE FROM team_month") suspend fun wipeMonths()
    @Query("DELETE FROM team") suspend fun wipeTeams()
}

@Dao
interface PayrollDao {
    @Query("SELECT * FROM workplace ORDER BY createdAt")
    fun observeWorkplaces(): Flow<List<WorkplaceEntity>>

    @Query("SELECT * FROM workplace ORDER BY createdAt")
    suspend fun workplaces(): List<WorkplaceEntity>

    @Query("SELECT * FROM staff WHERE workplaceId = :workplaceId ORDER BY sort, name")
    fun observeStaff(workplaceId: String): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff ORDER BY workplaceId, sort")
    suspend fun staff(): List<StaffEntity>

    @Query("SELECT w.* FROM staff_work w JOIN staff s ON s.id = w.staffId WHERE s.workplaceId = :workplaceId AND w.epochDay BETWEEN :from AND :to")
    fun observeWork(workplaceId: String, from: Long, to: Long): Flow<List<StaffWorkEntity>>

    @Query("SELECT * FROM staff_work")
    suspend fun work(): List<StaffWorkEntity>

    @Query("SELECT * FROM payroll_month WHERE workplaceId = :workplaceId AND month = :month")
    fun observeMonth(workplaceId: String, month: String): Flow<PayrollMonthEntity?>

    @Query("SELECT * FROM payroll_line WHERE workplaceId = :workplaceId AND month = :month")
    fun observeLines(workplaceId: String, month: String): Flow<List<PayrollLineEntity>>

    @Query("SELECT * FROM payroll_month") suspend fun months(): List<PayrollMonthEntity>
    @Query("SELECT * FROM payroll_line") suspend fun lines(): List<PayrollLineEntity>

    @Upsert suspend fun upsertWorkplace(e: WorkplaceEntity)
    @Upsert suspend fun upsertStaff(e: StaffEntity)
    @Upsert suspend fun upsertWork(e: StaffWorkEntity)
    @Upsert suspend fun upsertMonth(e: PayrollMonthEntity)
    @Upsert suspend fun upsertLines(e: List<PayrollLineEntity>)

    @Query("DELETE FROM staff_work WHERE staffId = :staffId AND epochDay = :epochDay")
    suspend fun deleteWork(staffId: String, epochDay: Long)

    @Query("DELETE FROM payroll_line WHERE workplaceId = :workplaceId AND month = :month")
    suspend fun deleteLines(workplaceId: String, month: String)

    /** 대장 닫기: the month's lines as they are now, and the month CLOSED, in one step. */
    @Transaction
    suspend fun close(month: PayrollMonthEntity, lines: List<PayrollLineEntity>) {
        deleteLines(month.workplaceId, month.month)
        if (lines.isNotEmpty()) upsertLines(lines)
        upsertMonth(month)
    }

    @Transaction
    suspend fun deleteStaff(id: String) { deleteStaffWork(id); deleteStaffRow(id) }
    @Query("DELETE FROM staff_work WHERE staffId = :id") suspend fun deleteStaffWork(id: String)
    @Query("DELETE FROM staff WHERE id = :id") suspend fun deleteStaffRow(id: String)

    @Transaction
    suspend fun replaceAll(places: List<WorkplaceEntity>, staff: List<StaffEntity>, work: List<StaffWorkEntity>, months: List<PayrollMonthEntity>, lines: List<PayrollLineEntity>) {
        wipeLines(); wipeMonths(); wipeWork(); wipeStaff(); wipePlaces()
        places.forEach { upsertWorkplace(it) }
        staff.forEach { upsertStaff(it) }
        work.forEach { upsertWork(it) }
        months.forEach { upsertMonth(it) }
        if (lines.isNotEmpty()) upsertLines(lines)
    }

    @Query("DELETE FROM payroll_line") suspend fun wipeLines()
    @Query("DELETE FROM payroll_month") suspend fun wipeMonths()
    @Query("DELETE FROM staff_work") suspend fun wipeWork()
    @Query("DELETE FROM staff") suspend fun wipeStaff()
    @Query("DELETE FROM workplace") suspend fun wipePlaces()
}
