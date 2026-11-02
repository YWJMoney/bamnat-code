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

    @Test fun dailyIgnoresLeftOverMinutes() = runBlocking {
        // PC D9: 근무 시작(1시간 전)에서 '매일 07:00'으로 바꾼 규칙이 '다음: 06:00'으로 보였어요.
        val daily = com.bamnat.core.formula.LogicRule("체크 비우기", com.bamnat.core.formula.RuleTrigger.DAILY, min = -60, at = "07:00",
            act = com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.CHECK_RESET, ""))
        assertEquals(day.plusDays(1).atTime(7, 0), LogicRunner.nextTime(daily, Today.dateTime(), LogicRunner.facts(c))?.first)
    }

    @Test fun clockMovedBackRunsWhenTheTimeComesAgain() = runBlocking {
        // PC D12 (중): 시계를 앞으로 옮겨 '놓쳤어요'가 된 07:00 규칙이, 시계를 06:59로 되돌린 뒤 07:00이 와도 돌지 않았어요.
        val daily = LogicRule("아침", RuleTrigger.DAILY, at = "07:00", act = LogicAct(LogicVerb.NOTIFY, "아침"))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(rules = listOf(daily)))) }
        val d = day.plusDays(1)
        at(d.atTime(6, 0)); LogicRunner.rulesChanged(c); settle()
        val store = LogicStore(app)
        at(d.atTime(10, 0)); LogicRunner.fire(c)
        assertTrue(store.missed(daily.key))
        at(d.atTime(6, 50)); LogicRunner.fire(c)
        assertEquals("되돌린 시계 뒤의 '놓쳤어요'는 지워요", null, store.lastResult(daily.key))
        at(d.atTime(7, 0, 30)); LogicRunner.fire(c)
        assertEquals(d.atTime(7, 0).toString(), store.firedAt(daily.key))
        assertTrue(store.lastResult(daily.key)!!.startsWith("${d.monthValue}/${d.dayOfMonth} 07:00 · 했어요"))
        // PC D13 (낮): 하루를 건너뛰어 다음 날 09:00으로 가면 그날 07:00은 '놓쳤어요'로 (아무 표시 없이 사라지지 않게).
        val d2 = d.plusDays(1)
        at(d2.atTime(9, 0)); LogicRunner.fire(c)
        assertEquals(d2.atTime(7, 0).toString(), store.firedAt(daily.key))
        assertTrue(store.missed(daily.key))
        // 되돌린 시계 앞의 한 번만 세요 (10:00에 놓친 것은 돈 것이 아니에요).
        assertEquals(1, store.runs(daily.name))
    }

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
