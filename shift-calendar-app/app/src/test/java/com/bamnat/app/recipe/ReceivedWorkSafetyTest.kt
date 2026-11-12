package com.bamnat.app.recipe

import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.logic.LogicRunner
import com.bamnat.app.logic.LogicStore
import com.bamnat.app.ui.decor.pressRect
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.recipe.AlarmStrength
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.app.data.AutoTidy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 받은 작품의 안전 (검토 09a·09b), 앱 몫: what a work brings is said before it goes in, and what it cannot be seen doing it cannot do.
 * [RVM-22] RCP-05 알람 묶음의 진동·점점 크게, [RVM-23] STU-23 뒤 단계의 쓰기 · RCP-17·STU-19 칸을 덮는 꾸밈 단추 · STU-18 없는 단추 ·
 * STU-12 끝없이 도는 규칙, [RVM-24] STU-13 받은 규칙 알림의 출처, [RVM-26] RCP-08 서랍 30개 한도가 밀어낸 테마.
 */
@RunWith(AndroidJUnit4::class)
class ReceivedWorkSafetyTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    @Test fun anAlarmPieceSaysEveryWayItChangesTheRing() = runBlocking {
        val cur = c.settingsRepository.current
        val snooze = cur.snoozeMinutes % 30 + 1
        val lines = RecipePieces(c).preview(PieceKind.ALARMS,
            mapOf("snooze" to snooze, "ring" to cur.ringMinutes, "gradual" to !cur.gradualVolume, "vibrate" to !cur.vibrate), PersonalCalendar.MAIN)
            .filter { !it.skipped }.map { it.text }
        assertTrue(lines.toString(), "다시 울림 ${cur.snoozeMinutes}분 → ${snooze}분" in lines)
        assertFalse("울림 길이는 그대로라 말하지 않아요: $lines", lines.any { it.startsWith("울림 길이") })
        assertTrue("점점 크게: $lines", lines.any { "점점" in it || "큰 소리" in it })
        assertTrue("진동: $lines", lines.any { "진동" in it })
    }

    @Test fun whatMakesMyAlarmWeakerIsMarkedAndKeptOut() = runBlocking {
        c.settingsRepository.update { it.copy(vibrate = true, ringMinutes = 5) }
        val changes = RecipePieces(c).preview(PieceKind.ALARMS, mapOf("vibrate" to false, "ring" to 1L), PersonalCalendar.MAIN)
        assertTrue(changes.toString(), changes.filter { "진동" in it.text || "울림 길이" in it.text }.all { it.weak })
        val now = RecipePieces(c).alarmNow()
        assertEquals(AlarmStrength.Now(5, true, c.settingsRepository.current.gradualVolume), now)
        assertTrue(AlarmStrength.keepStrong(mapOf("vibrate" to false, "ring" to 1L), now).isEmpty())
    }

    @Test fun aRuleWhoseLaterStepWritesIsMarked() = runBlocking {
        val rule = LogicRule("알림 뒤 저축", RuleTrigger.DAILY, at = "09:00", act = LogicAct(LogicVerb.NOTIFY, "저축해요"),
            more = listOf(LogicAct(LogicVerb.ADD_MONEY, "저축", 10_000)))
        val lines = RecipePieces(c).preview(PieceKind.LOGIC, Logic(rules = listOf(rule)).toValues(), PersonalCalendar.MAIN).map { it.text }
        val line = lines.first { "알림 뒤 저축" in it }
        assertTrue(line, line.startsWith("⚠ "))
        assertTrue(line, line.endsWith("(꺼진 채로 들어와요)"))
    }

    @Test fun aDecorationTakesAtMostHalfThePlace() {
        val density = 2f
        // A date cell about 48 × 56 dp: the 48 dp tap place would be all of it, so the sticker's own size is used.
        val cell = Rect(0f, 0f, 96f, 112f)
        val small = DecorItem(DecorKind.EMOJI, "⭐", Decor.CELL, w = 20, h = 20, tap = "b0001")
        assertEquals(40f, pressRect(small, cell, density)!!.width, 0.01f)
        assertNull("칸을 거의 덮는 꾸밈은 누르지 않아요", pressRect(small.copy(w = 44, h = 52), cell, density))
        // On a wide part (the calendar head) the easy 48 dp place stays.
        val head = Rect(0f, 0f, 720f, 112f)
        assertEquals(96f, pressRect(small, head, density)!!.width, 0.01f)
    }

    @Test fun aButtonThatIsNotThereLetsTheDateTapGoOn() {
        assertFalse(LogicRunner.pressDecor(c, "zzzz", java.time.LocalDate.of(2026, 11, 12)))
    }

    @Test fun aRuleRestsAfterTooManyRunsAndNoticesAreHeldPerHour() {
        val store = LogicStore(app)
        val who = "시험 고리 ${System.nanoTime()}" // real clock: a name no other test shares
        assertFalse(store.tooOften(who))
        repeat(LogicRunner.DAY_MAX) { store.ran(who) }
        assertTrue("하루 ${LogicRunner.DAY_MAX}번이면 내일까지 쉬어요", store.tooOften(who))
        repeat(LogicRunner.HOUR_NOTICES) { assertTrue(store.notice(who)) }
        assertFalse("한 시간에 ${LogicRunner.HOUR_NOTICES}번까지", store.notice(who))
    }

    @Test fun aReceivedRulesNoticeSaysWhichWork() {
        assertEquals("작품 ‘야간 회복 스테이션’의 규칙", LogicRunner.sourceLine("야간 회복 스테이션"))
        assertEquals("작품 ‘${"가".repeat(24)}’의 규칙", LogicRunner.sourceLine("가".repeat(40)))
        assertEquals("받은 규칙 · ‘보안 경고’", LogicRunner.receivedTitle("보안 경고"))
        assertTrue(LogicRunner.foldedLine("야간 회복", "물 3잔").startsWith("작품 ‘야간 회복’ · "))
    }

    @Test fun aThemeTheLimitLetGoComesBack() = runBlocking {
        val prefs = app.getSharedPreferences("theme_wardrobe", 0)
        val was = prefs.getString("lease", null)
        try {
            // 입었던 테마: 남의 1 (newest) … 남의 30 (oldest), and one of mine at the end.
            val others = (1..ThemeLease.HISTORY).map { ThemeLease.Wear("남의 $it", "C$it", "", 0, 0) }
            val mine = ThemeLease.Wear("내 테마", "", "", 0, 0, own = true)
            prefs.edit().putString("lease", ThemeLease.encode(ThemeLease(history = others + mine))).commit()
            c.wardrobe.reload()
            assertTrue(c.wardrobe.putBack(ThemeLease.encode(ThemeLease(history = listOf(ThemeLease.Wear("새 테마", "NEW", "", 0, 0))))))
            val h = c.wardrobe.lease.value.history
            assertTrue("내 테마는 남아요", h.any { it.key == mine.key })
            assertFalse("가장 오래 전에 입은 남의 테마가 빠져요", h.any { it.code == "C30" })
            val item = withTimeout(5_000) {
                c.autoTidy.items.first { l -> l.any { it.kind == ThemeWardrobe.KIND_PUSHED && it.title == "남의 30" } }
            }.first { it.kind == ThemeWardrobe.KIND_PUSHED && it.title == "남의 30" }
            assertEquals(AutoTidy.AREA_RECIPE, item.area)
            assertTrue(item.why, "30개" in item.why)
            assertTrue("서랍 아래 정리함은 테마의 말로", com.bamnat.app.ui.money.tidyAbout(item.area).startsWith("입었던 테마가"))
            assertTrue("되돌리기", c.autoTidy.restore(item.id))
            assertTrue(c.wardrobe.lease.value.history.any { it.code == "C30" })
            assertTrue(c.wardrobe.lease.value.history.any { it.key == mine.key })
        } finally {
            prefs.edit().putString("lease", was).commit()
            c.wardrobe.reload()
        }
    }
}
