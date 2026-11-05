package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.38 [P3-01] The 팀 근무표 goes in every backup; 사업장·직원·급여 only in one sealed with a password
 * (순번 138 "직원 정보는 암호를 걸어야 들어가요"), and a backup without them leaves the phone's 대장 as it is.
 */
@RunWith(AndroidJUnit4::class)
class PhaseThreeBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = PhaseThreeStore(db) { 1L }
    private val month = YearMonth.of(2026, 11)
    private val day = month.atDay(3)

    @After fun close() = db.close()

    private suspend fun fill() {
        ScheduleRepository(db).ensureDefaults()
        val shifts = listOf(TeamShift(1, "D", "데이", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(15, 0)), TeamShift(4, "O", "오프", 0xFF9E9E9E, true))
        store.saveTeam(Team("t1", "3병동", "수간호사", shifts, need = mapOf(1L to 2)))
        store.saveMembers(listOf(TeamMember("m1", "t1", "김가나", "RN"), TeamMember("m2", "t1", "이다라", linkId = "L1", linkToken = "tok")))
        store.saveBoard("t1", store.boardNow("t1", month).paint("m1", day, 1).paint("m2", day, 4))
        store.saveWorkplace(Workplace("w1", "가나다카페", fivePlus = false, payday = 10))
        store.saveStaff(StaffMember("s1", "박마바", base = 10_320, job = "홀", weekly = mapOf(DayOfWeek.MONDAY to com.bamnat.core.work.StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))), "w1")
        store.setWork("s1", day, StaffWork("s1", day, WorkKind.LATE, LocalTime.of(10, 0), LocalTime.of(18, 0)))
    }

    @Test fun teamsGoInEveryBackupStaffOnlyWhenSealed() = runBlocking {
        fill()
        val backups = BackupManager(db, SettingsRepository(context))
        val plain = JSONObject(backups.export())
        assertTrue(plain.has(PhaseThreeBackup.TEAMS))
        assertFalse("직원 정보는 암호를 걸어야 들어가요", plain.has(PhaseThreeBackup.WORKPLACES))
        assertTrue(JSONObject(backups.exportSealed()).has(PhaseThreeBackup.WORKPLACES))
    }

    @Test fun sealedRoundTripKeepsEveryRow() = runBlocking {
        fill()
        val backups = BackupManager(db, SettingsRepository(context))
        val sealed = backups.exportSealed()
        val other = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            BackupManager(other, SettingsRepository(context)).restore(sealed)
            val again = PhaseThreeStore(other)
            assertEquals(store.teamList.first(), again.teamList.first())
            assertEquals(store.members("t1").first(), again.members("t1").first())
            assertEquals(store.boardNow("t1", month).cells, again.boardNow("t1", month).cells)
            assertEquals(store.workplaces.first(), again.workplaces.first())
            assertEquals(store.staff("w1").first(), again.staff("w1").first())
            assertEquals(store.work("w1", month).first(), again.work("w1", month).first())
        } finally {
            other.close()
        }
    }

    @Test fun anUnsealedBackupLeavesTheLedgerAlone() = runBlocking {
        fill()
        val backups = BackupManager(db, SettingsRepository(context))
        val plain = backups.export()
        store.deleteTeam("t1")
        backups.restore(plain)
        assertEquals(listOf("3병동"), store.teamList.first().map { it.name })
        assertEquals(listOf("박마바"), store.staff("w1").first().map { it.name })
        assertEquals(TeamCell("m1", day, 1), store.boardNow("t1", month).cell("m1", day))
    }
}
