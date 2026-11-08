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
        // 1.38 [P3-L-14] 신청 받기 round, its key, and a request a member made by link (근무 희망 D).
        store.saveTeam(Team("t1", "3병동", "수간호사", shifts, need = mapOf(1L to 2), ask = com.bamnat.core.team.AskRound("R1", "atok", month,
            com.bamnat.core.team.AskRules(closesAt = 5_000L, holidays = setOf(month.atDay(25))), linkedMapOf("m1" to "https://s.example/a/K1", "m2" to "https://s.example/a/K2"),
            notes = mapOf("m1" to "NN 뒤 off"), seen = setOf("m1"), openedAt = 1L, syncedAt = 2L),
            // 1.38 [P3-X-01] 결원 메우기 요청과 그 답
            calls = listOf(com.bamnat.core.team.TeamCall("C1", "ktok", com.bamnat.core.team.TeamCover.Gap("m1", month.atDay(7), 1), listOf("m2"),
                listOf("https://s.example/v/V1"), answers = mapOf("m2" to true), seen = setOf("m2"), createdAt = 3L, expiresAt = 4L)),
            // 1.38 [P3-X-02] 바꾸기 요청
            swap = com.bamnat.core.team.SwapRound("S1", "stok", month, listOf("m1", "m2"), listOf("https://s.example/w/W1", "https://s.example/w/W2"),
                listOf(com.bamnat.core.team.TeamSwap.Ask("m1", "m2", month.atDay(9), com.bamnat.core.team.TeamSwap.State.YES, 5L)), openedAt = 6L)))
        store.putLinkWishes("t1", month, listOf(com.bamnat.core.team.TeamWish("m1", month.atDay(9), com.bamnat.core.team.WishKind.SHIFT, shiftId = 1, at = 1_234L)))
        store.saveMembers(listOf(TeamMember("m1", "t1", "김가나", "RN"), TeamMember("m2", "t1", "이다라", linkId = "L1", linkToken = "tok")))
        store.saveBoard("t1", store.boardNow("t1", month).paint("m1", day, 1).paint("m2", day, 4))
        store.saveWorkplace(Workplace("w1", "가나다카페", fivePlus = false, payday = 10, minuteUnit = 30, industry = "retail_food",
            calls = listOf(com.bamnat.core.team.TeamCall("B1", "btok", com.bamnat.core.team.TeamCover.Gap("s1", month.atDay(7), 0), listOf("s1"), listOf("https://s.example/v/V2")))))
        store.saveStaff(StaffMember("s1", "박마바", base = 10_320, job = "홀", dayRates = mapOf(DayOfWeek.SATURDAY to 12_000L), weekly = mapOf(DayOfWeek.MONDAY to com.bamnat.core.work.StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))),
            leaves = listOf(com.bamnat.core.boss.StaffLeave(month.atDay(20)))), "w1")
        store.setWork("s1", day, StaffWork("s1", day, WorkKind.LATE, LocalTime.of(10, 0), LocalTime.of(18, 0)))
        // 1.38 [P3-B-11] 출퇴근 링크와 기록
        store.saveStaff(store.staff("w1").first().single().copy(clockId = "C1", clockToken = "ctok"), "w1")
        // 1.38 [P3-X-06] 근로계약서: one signed before, one waiting on its link. An edit of the row leaves it alone.
        val signed = com.bamnat.core.boss.StaffContract.Sent("근로계약서", listOf(com.bamnat.core.boss.StaffContract.Item("임금", "시급 10,320원\n지급일 10일")), 1L, signedAt = 2L, signedName = "박마바")
        store.updateContract("s1") { signed.replacedBy(com.bamnat.core.boss.StaffContract.Sent("근로계약서", signed.items, 3L, "K1", "ktok2", "https://s.example/k/K", 9L)) }
        store.saveStaff(store.staff("w1").first().single().copy(job = "주방", contract = null), "w1")
        assertEquals("직원 정보를 고쳐도 계약서는 그대로", "ktok2", store.staff("w1").first().single().contract?.token)
        store.addPunches("s1", listOf(com.bamnat.core.boss.StaffPunch("s1", java.time.Instant.parse("2026-11-03T01:02:00Z"), true)))
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
            assertEquals("신청 받기 열쇠도 그대로(암호 백업)", "atok", again.teamList.first().single().ask?.token)
            assertEquals("결원 요청 열쇠도 그대로(암호 백업)", "ktok", again.teamList.first().single().calls.single().token)
            assertEquals(mapOf("m2" to true), again.teamList.first().single().calls.single().answers)
            assertEquals("바꾸기 요청도 그대로", store.teamList.first().single().swap, again.teamList.first().single().swap)
            assertEquals("stok", again.teamList.first().single().swap?.token)
            assertEquals("링크로 받은 근무 희망도 그대로", store.wishes("t1", month).first(), again.wishes("t1", month).first())
            assertEquals(com.bamnat.core.team.TeamWish("m1", month.atDay(9), com.bamnat.core.team.WishKind.SHIFT, shiftId = 1, at = 1_234L), again.wishes("t1", month).first().single())
            assertEquals(store.members("t1").first(), again.members("t1").first())
            assertEquals(store.boardNow("t1", month).cells, again.boardNow("t1", month).cells)
            assertEquals(store.workplaces.first(), again.workplaces.first())
            assertEquals("계산 단위도 그대로", 30, again.workplaces.first().single().minuteUnit)
            assertEquals("1.39 산재 업종도 그대로", "retail_food", again.workplaces.first().single().industry)
            assertEquals("대타 요청도 그대로", "btok", again.workplaces.first().single().calls.single().token)
            assertEquals("휴직 기간도 그대로", listOf(com.bamnat.core.boss.StaffLeave(month.atDay(20))), again.staff("w1").first().single().leaves)
            assertEquals("요일별 시급도 그대로", mapOf(DayOfWeek.SATURDAY to 12_000L), again.staff("w1").first().single().dayRates)
            assertEquals(store.staff("w1").first(), again.staff("w1").first())
            assertEquals(store.work("w1", month).first(), again.work("w1", month).first())
            assertEquals("출퇴근 기록도 그대로", store.punches("w1", month).first(), again.punches("w1", month).first())
            assertEquals(1, again.punches("w1", month).first().size)
            assertEquals("ctok", again.staff("w1").first().single().clockToken)
            val contract = again.staff("w1").first().single().contract
            assertEquals("계약서와 지난 서명도 그대로", store.staff("w1").first().single().contract, contract)
            assertEquals(listOf(2L), contract?.past?.map { it.signedAt })
            assertEquals("시급 10,320원\n지급일 10일", contract?.items?.single()?.body)
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
        // 1.38 보안 검토: 암호 없는 백업에는 링크 고치는 열쇠가 없고, 복원해도 이 휴대폰의 열쇠는 그대로예요.
        assertFalse(JSONObject(plain).getJSONObject(PhaseThreeBackup.TEAMS).toString().contains("\"tok\""))
        assertFalse("신청 받기 열쇠도 암호 없는 백업에는 없어요", JSONObject(plain).getJSONObject(PhaseThreeBackup.TEAMS).toString().contains("atok"))
        assertEquals("팀을 지운 뒤라 신청 열쇠도 없어요", null, store.teamList.first().single().ask?.token)
        assertEquals("R1", store.teamList.first().single().ask?.id)
        assertFalse("결원 요청 열쇠도 암호 없는 백업에는 없어요", JSONObject(plain).getJSONObject(PhaseThreeBackup.TEAMS).toString().contains("ktok"))
        assertEquals("C1", store.teamList.first().single().calls.single().id)
        assertEquals(null, store.teamList.first().single().calls.single().token)
        assertFalse("바꾸기 열쇠도 암호 없는 백업에는 없어요", JSONObject(plain).getJSONObject(PhaseThreeBackup.TEAMS).toString().contains("stok"))
        assertEquals(null, store.teamList.first().single().swap?.token)
        // 팀을 지운 뒤라 열쇠가 없어요: 링크는 '새로 만들기'로 다시 받아요. 팀이 남아 있으면 이 휴대폰 열쇠를 그대로 둬요.
        assertEquals(null, store.members("t1").first().single { it.id == "m2" }.linkToken)
        store.saveMembers(store.members("t1").first().map { if (it.id == "m2") it.copy(linkToken = "tok") else it })
        store.saveTeam(store.teamList.first().single().let { it.copy(ask = it.ask?.copy(token = "atok"), calls = it.calls.map { k -> k.copy(token = "ktok") }, swap = it.swap?.copy(token = "stok")) })
        backups.restore(plain)
        assertEquals("tok", store.members("t1").first().single { it.id == "m2" }.linkToken)
        assertEquals("남아 있는 신청 열쇠는 그대로", "atok", store.teamList.first().single().ask?.token)
        assertEquals("남아 있는 결원 요청 열쇠도 그대로", "ktok", store.teamList.first().single().calls.single().token)
        assertEquals("남아 있는 바꾸기 열쇠도 그대로", "stok", store.teamList.first().single().swap?.token)
        assertTrue(JSONObject(backups.exportSealed()).getJSONObject(PhaseThreeBackup.TEAMS).toString().contains("\"tok\""))
    }
}
