package com.bamnat.app.logic

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.ZoneId

/**
 * 누락 점검 N8: '날짜를 길게 누르면' 규칙 (화면을 여는 것이면 빠른 설정 대신), and a 꾸밈 층 단추 run by its id
 * ([LogicRunner.press], what 갈래 C's 꾸밈 `tap` calls); a button whose 보이기 조건 is false that day does not run.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LogicHoldPressTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val day = LocalDate.of(2026, 11, 14) // 토
    private fun settle() { repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(50) } }
    private val ping = LogicButton("알려 줘", "🔔", ButtonPlace.DECOR, LogicAct(LogicVerb.NOTIFY, "꾸밈 단추"), givenId = "deco1")
    private val weekday = LogicButton("평일만", null, ButtonPlace.DECOR, LogicAct(LogicVerb.NOTIFY, "평일"), show = "아님 주말", givenId = "deco2")

    @Before fun setUp() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        Today.fix(day.atTime(10, 0).atZone(ZoneId.systemDefault()))
        val hold = LogicRule("길게 메모장", RuleTrigger.HOLD, act = LogicAct(LogicVerb.SCREEN, "memo"))
        val holdNotify = LogicRule("길게 알림", RuleTrigger.HOLD, cond = "주말", act = LogicAct(LogicVerb.NOTIFY, "주말 길게"))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(listOf(ping, weekday), listOf(hold, holdNotify)))) }
    }

    @After fun tearDown() {
        Today.reset()
        // The 길게 누르면 rule opened 메모장 through NoticeLinks, an app-wide object: not carried into the next test's first screen.
        settle()
        com.bamnat.app.NoticeLinks.consume(com.bamnat.app.NoticeLinks.Target.MEMOS)
    }

    @Test fun idsKeepAndRoundTrip() {
        val l = Logic.decode(c.settingsRepository.current.logic)
        assertEquals(listOf("deco1", "deco2"), l.buttons.map { it.id })
        assertEquals(ping.label, l.button("deco1")?.label)
        assertEquals(LogicButton.idOf("평일만"), LogicButton("평일만", act = LogicAct(LogicVerb.NOTIFY, "x")).id)
    }

    @Test fun holdOpensInsteadOfQuickMenu() = runBlocking {
        val facts = LogicRunner.facts(c)
        assertTrue("a screen rule opens, so no quick menu", LogicRunner.onHold(c, day, facts))
        settle()
        assertFalse("no facts yet: the quick menu", LogicRunner.onHold(c, day, null))
    }

    @Test fun pressRunsByIdAndRespectsShow() = runBlocking {
        val facts = LogicRunner.facts(c)
        assertTrue(LogicRunner.press(c, "deco1", day, facts))
        settle()
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertTrue(nm.allNotifications.any { it.extras.getCharSequence("android.text")?.contains("꾸밈 단추") == true })
        assertFalse("평일만 on a Saturday", LogicRunner.press(c, "deco2", day, facts))
        assertFalse("no such button", LogicRunner.press(c, "nope", day, facts))
    }
}
