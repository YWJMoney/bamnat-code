package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.TeamRoster
import com.bamnat.core.setup.TeamAnchors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [CYC-06] 1.39 "몇 조예요?" from a coworker's file: the sender's 모든 조 보기 goes in the file with each 조's day, so a coworker
 * on any 조 picks it by name; a file without them (or with one 조) reads as before.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanyShareTeamsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 11, 7)

    @After fun close() { db.close() }

    private suspend fun share(roster: TeamRoster?): CompanyShare {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val t = schedule.snapshot.first().shiftTypes
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        // 주주야야휴휴휴휴 from 11/3 for my 조.
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day, day, night, night, null, null, null, null), LocalDate.of(2026, 11, 3)), today)
        return CompanyShare(schedule, PayStore(context), SettingsRepository(context), today = { today }, teamRoster = { roster })
    }

    @Test fun everyTeamGoesWithItsDayAndMineByName() = runBlocking {
        val roster = TeamRoster(listOf(TeamRoster.Team("A조", 0, true), TeamRoster.Team("B조", 2), TeamRoster.Team("C조", 4), TeamRoster.Team("D조", 6)))
        val text = share(roster).export("밤낮전자", withAmounts = false)
        val rotation = JSONObject(text).getJSONObject("rotation")
        assertEquals("A조", rotation.getString("team"))
        val teams = CompanyShare.teamsOf(text)
        assertEquals(listOf("A조" to "2026-11-03", "B조" to "2026-11-05", "C조" to "2026-11-07", "D조" to "2026-11-09"),
            teams.map { it.name to it.anchor.toString() })
        // C조 today is on the first day of the order; picking it gives C조 as mine in the coworker's 모든 조 보기.
        val c = TeamAnchors.pickable(teams, 8).first { it.first == "C조" }.second
        assertEquals(0, TeamAnchors.stepOn(c, today, 8))
        assertEquals("C조", TeamAnchors.roster(teams, c, 8)!!.mine.name)
        // The box typed by the sender wins over the roster's name.
        assertEquals("1팀", JSONObject(share(roster).export("", false, team = "1팀")).getJSONObject("rotation").getString("team"))
    }

    @Test fun aFileWithoutTeamsReadsAsBefore() = runBlocking {
        val plain = share(null).export("밤낮전자", withAmounts = false)
        assertFalse(JSONObject(plain).getJSONObject("rotation").has("teams"))
        assertFalse(JSONObject(plain).getJSONObject("rotation").has("team"))
        assertTrue(CompanyShare.teamsOf(plain).isEmpty())
        val one = share(TeamRoster(listOf(TeamRoster.Team("A조", 0, true)))).export("", false)
        assertTrue(CompanyShare.teamsOf(one).isEmpty())
        assertTrue(CompanyShare.teamsOf("not json").isEmpty())
    }
}
