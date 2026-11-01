package com.bamnat.app.logic

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * [RUL-04] PC 1차 확인 (규칙이 안 울림): '야간 1시간 전 알림' saved at 17:50 on a night day is registered for 18:00 that same day,
 * exact; at 18:00 it posts on 내 규칙 알림 and the rule says what happened; a time missed while the phone was off is said, not run.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LogicTimedRuleTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 13)
    private val rule = LogicRule("야간 전", RuleTrigger.START, min = -60, role = "night", act = LogicAct(LogicVerb.NOTIFY, "{근무} 한 시간 전"))

    private fun at(t: LocalDateTime) = Today.fix(t.atZone(zone))
    private fun settle() { repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(50) } }

    @Before fun setUp() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        val repo = c.scheduleRepository
        val night = repo.saveShiftType(ShiftType(0, "야간", "야", 0xFF3949AB, LocalTime.of(19, 0), LocalTime.of(7, 0)))
        repo.setOverride(day, ShiftOverride.Shift(night))
        repo.setOverride(day.plusDays(1), ShiftOverride.Shift(night))
        at(day.atTime(17, 50))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(rules = listOf(rule)))) }
    }

    @After fun tearDown() = Today.reset()

    @Test fun nextIsTodayAtSixAndRegisteredThen() = runBlocking {
        val facts = LogicRunner.facts(c)
        assertEquals(day.atTime(18, 0), LogicRunner.nextTime(rule, Today.dateTime(), facts)?.first)
        LogicRunner.rulesChanged(c)
        settle()
        val am = shadowOf(app.getSystemService(AlarmManager::class.java))
        val next = am.scheduledAlarms.maxByOrNull { it.triggerAtTime }
        assertNotNull(next)
        assertEquals(day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(), LogicStore(app).scheduled?.let { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() })
        assertEquals("다음: 11/13(금) 18:00", nextLine(rule, facts))
    }

    @Test fun atSixItPostsAndSaysSo() = runBlocking {
        LogicRunner.rulesChanged(c)
        settle()
        at(day.atTime(18, 0, 20))
        LogicRunner.fire(c)
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val posted = nm.allNotifications.firstOrNull { it.channelId == Notifications.RULES_CHANNEL_ID }
        assertNotNull("rule notice", posted)
        assertTrue(LogicStore(app).lastResult(rule.key)!!.endsWith("했어요"))
        assertEquals(1, LogicStore(app).runs(rule.name))
        // The same time does not post twice.
        LogicRunner.fire(c)
        assertEquals(1, LogicStore(app).runs(rule.name))
    }

    @Test fun aTimeMissedWhileOffIsSaidNotRun() = runBlocking {
        LogicRunner.rulesChanged(c)
        settle()
        at(day.atTime(21, 30))
        LogicRunner.fire(c)
        assertEquals(0, LogicStore(app).runs(rule.name))
        assertTrue(LogicStore(app).lastResult(rule.key)!!.contains("놓쳤어요"))
    }
}
