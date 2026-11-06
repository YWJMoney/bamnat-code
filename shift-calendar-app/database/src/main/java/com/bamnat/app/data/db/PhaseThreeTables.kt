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
    /** 1.38 (DB 29) 같이 쉬는 사람: each member's link also names who is off with them (the leader turns it on). */
    @androidx.room.ColumnInfo(defaultValue = "0") val showTogether: Boolean = false,
    /** 1.38 (DB 30) [P3-L-14] 신청 받기: the round as JSON ([com.bamnat.app.data.PhaseThreeStore.encodeAsk]); "" for none. */
    @androidx.room.ColumnInfo(defaultValue = "") val ask: String = "",
    /** 1.38 (DB 30) [P3-X-01] 결원 메우기 asks as JSON; "" for none. */
    @androidx.room.ColumnInfo(defaultValue = "") val calls: String = "",
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
/** 1.38 [TEAM-19] 희망 휴무 (설계 1-3 `team_wish`): what a member asked for a day, typed by the leader or pasted from a member's line. */
@Entity(tableName = "team_wish", primaryKeys = ["memberId", "epochDay"], indices = [Index(value = ["teamId", "epochDay"])])
data class TeamWishEntity(
    val teamId: String,
    val memberId: String,
    val epochDay: Long,
    /** OFF, WORK or AVOID_NIGHT; 1.38 also LEAVE, HALF, EDU and SHIFT:<id> ([com.bamnat.core.team.WishKind.code]). */
    val kind: String,
    val receivedAt: Long,
    /** How it came: LEADER (typed), TEXT (a pasted line), 1.38 LINK (a member's 신청 링크; [receivedAt] is when they asked). */
    val via: String,
)

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
    /** 1.38 (DB 29) 계산 단위: each day's work counted up to this many minutes (1·10·30·60). */
    @androidx.room.ColumnInfo(defaultValue = "1") val minuteUnit: Int = 1,
    /** 1.38 (DB 30) [P3-X-01] 대타 요청, the same JSON as [TeamEntity.calls]. */
    @androidx.room.ColumnInfo(defaultValue = "") val calls: String = "",
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
    /** 1.38 (DB 29) [P3-B-11] 출퇴근 링크 on the share server, and its key (sealed backups only, like any 고치는 열쇠). */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val clockId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val clockToken: String? = null,
)

/** 1.38 (DB 29) [P3-B-11] One press of 출근 or 퇴근 on a staff member's 출퇴근 링크, at the server's time. A 기록 the boss puts in; never pay by itself. */
@Entity(tableName = "staff_punch", primaryKeys = ["staffId", "at"])
data class StaffPunchEntity(
    val staffId: String,
    /** Epoch milliseconds. */
    val at: Long,
    /** IN or OUT. */
    val kind: String,
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

    @Query("SELECT * FROM team WHERE id = :id")
    suspend fun teamById(id: String): TeamEntity?

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
        deleteTeamCells(id); deleteTeamWishes(id); deleteTeamMembers(id); deleteTeamMonths(id); deleteTeamRow(id)
    }

    @Query("DELETE FROM team_cell WHERE teamId = :id") suspend fun deleteTeamCells(id: String)
    @Query("DELETE FROM team_member WHERE teamId = :id") suspend fun deleteTeamMembers(id: String)
    @Query("DELETE FROM team_month WHERE teamId = :id") suspend fun deleteTeamMonths(id: String)
    @Query("DELETE FROM team WHERE id = :id") suspend fun deleteTeamRow(id: String)

    @Query("SELECT * FROM team_wish WHERE teamId = :teamId AND epochDay BETWEEN :from AND :to")
    fun observeWishes(teamId: String, from: Long, to: Long): Flow<List<TeamWishEntity>>

    @Query("SELECT * FROM team_wish") suspend fun wishes(): List<TeamWishEntity>
    @Upsert suspend fun upsertWishes(list: List<TeamWishEntity>)
    @Query("DELETE FROM team_wish WHERE memberId = :memberId AND epochDay = :epochDay") suspend fun deleteWish(memberId: String, epochDay: Long)
    @Query("DELETE FROM team_wish WHERE teamId = :id") suspend fun deleteTeamWishes(id: String)
    @Query("DELETE FROM team_wish WHERE memberId = :id") suspend fun deleteMemberWishes(id: String)
    /** 1.38 [P3-L-14] The requests a round brought before, so a request a member took back goes too. */
    @Query("DELETE FROM team_wish WHERE teamId = :teamId AND epochDay BETWEEN :from AND :to AND via = :via")
    suspend fun deleteWishesVia(teamId: String, from: Long, to: Long, via: String)

    /** Backup: every team row goes back in one step. */
    @Transaction
    suspend fun replaceAll(teams: List<TeamEntity>, members: List<TeamMemberEntity>, cells: List<TeamCellEntity>, months: List<TeamMonthEntity>,
                           wishes: List<TeamWishEntity> = emptyList()) {
        wipeCells(); wipeMembers(); wipeMonths(); wipeWishes(); wipeTeams()
        if (wishes.isNotEmpty()) upsertWishes(wishes)
        teams.forEach { upsertTeam(it) }
        if (members.isNotEmpty()) upsertMembers(members)
        if (cells.isNotEmpty()) upsertCells(cells)
        months.forEach { upsertMonth(it) }
    }

    @Query("DELETE FROM team_cell") suspend fun wipeCells()
    @Query("DELETE FROM team_member") suspend fun wipeMembers()
    @Query("DELETE FROM team_month") suspend fun wipeMonths()
    @Query("DELETE FROM team_wish") suspend fun wipeWishes()
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

    @Query("SELECT * FROM payroll_line WHERE workplaceId = :workplaceId AND month = :month")
    suspend fun linesOf(workplaceId: String, month: String): List<PayrollLineEntity>

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
        // 1.38 [BOSS-10] 지급했어요·명세서 보냄 stay through a second 닫기.
        val was = linesOf(month.workplaceId, month.month).associateBy { it.staffId }
        deleteLines(month.workplaceId, month.month)
        if (lines.isNotEmpty()) upsertLines(lines.map { l -> was[l.staffId]?.let { w -> l.copy(paidAt = l.paidAt ?: w.paidAt, slipSentAt = l.slipSentAt ?: w.slipSentAt) } ?: l })
        upsertMonth(month)
    }

    @Query("SELECT p.* FROM staff_punch p JOIN staff s ON s.id = p.staffId WHERE s.workplaceId = :workplaceId AND p.at BETWEEN :from AND :to ORDER BY p.at")
    fun observePunches(workplaceId: String, from: Long, to: Long): Flow<List<StaffPunchEntity>>
    @Query("SELECT * FROM staff_punch ORDER BY staffId, at") suspend fun punches(): List<StaffPunchEntity>
    @Query("UPDATE staff SET clockId = NULL, clockToken = NULL WHERE id = :id") suspend fun forgetClock(id: String)
    @Query("SELECT MAX(at) FROM staff_punch WHERE staffId = :staffId") suspend fun lastPunch(staffId: String): Long?
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insertPunches(e: List<StaffPunchEntity>): List<Long>

    @Transaction
    suspend fun deleteStaff(id: String) { deleteStaffWork(id); deleteStaffPunches(id); deleteStaffRow(id) }
    @Query("DELETE FROM staff_punch WHERE staffId = :id") suspend fun deleteStaffPunches(id: String)
    @Query("DELETE FROM staff_work WHERE staffId = :id") suspend fun deleteStaffWork(id: String)
    @Query("DELETE FROM staff WHERE id = :id") suspend fun deleteStaffRow(id: String)

    @Transaction
    suspend fun replaceAll(places: List<WorkplaceEntity>, staff: List<StaffEntity>, work: List<StaffWorkEntity>, months: List<PayrollMonthEntity>, lines: List<PayrollLineEntity>,
                           punches: List<StaffPunchEntity> = emptyList()) {
        wipeLines(); wipeMonths(); wipeWork(); wipePunches(); wipeStaff(); wipePlaces()
        places.forEach { upsertWorkplace(it) }
        staff.forEach { upsertStaff(it) }
        work.forEach { upsertWork(it) }
        months.forEach { upsertMonth(it) }
        if (lines.isNotEmpty()) upsertLines(lines)
        if (punches.isNotEmpty()) insertPunches(punches)
    }

    @Query("DELETE FROM staff_punch") suspend fun wipePunches()

    @Query("DELETE FROM payroll_line") suspend fun wipeLines()
    @Query("DELETE FROM payroll_month") suspend fun wipeMonths()
    @Query("DELETE FROM staff_work") suspend fun wipeWork()
    @Query("DELETE FROM staff") suspend fun wipeStaff()
    @Query("DELETE FROM workplace") suspend fun wipePlaces()
}
