package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.TodayShiftNotice
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.logic.LogicStore
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.NoticeOverlap
import com.bamnat.core.formula.RuleTrigger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.39 [NTC-03] 사용자 2026-11-08 (장터 규칙·단추 '오늘 요약 한 줄'과 잠금 화면 오늘 근무가 함께 떠요): putting a rule in that says what is
 * on already asks; 기존 것 끄고 이걸로 turns the old one off and 되돌리기 on again, 둘 다 두기 leaves it.
 */
@RunWith(AndroidJUnit4::class)
class NoticeOverlapPutInTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val summary = LogicRule("오늘 요약", RuleTrigger.OPEN, act = LogicAct(LogicVerb.NOTIFY, "오늘 {근무} {근무시작}~{근무끝} · 이달 쓴 돈 {원(이달지출)}"))
    private val values = Logic(rules = listOf(summary)).toValues()

    @Test fun replaceTurnsTheLockScreenNoticeOffAndUndoBringsItBack() = runBlocking {
        c.settingsRepository.update { it.copy(todayShiftNotice = true, logic = "") }
        val hits = c.recipes.pieces.overlaps(values)
        assertEquals(listOf(NoticeOverlap.BuiltIn.TODAY), hits.map { it.builtIn })
        val side = c.recipes.pieces.compare(com.bamnat.core.recipe.PieceKind.LOGIC, values, PersonalCalendar.MAIN, RecipePieces.Mode.REPLACE)
        assertTrue(side.any { it.what == "잠금 화면 오늘 근무 알림" && it.then.startsWith("끔") })
        val before = c.recipes.pieces.apply(com.bamnat.core.recipe.PieceKind.LOGIC, values, PersonalCalendar.MAIN, "NTC00001", RecipePieces.Mode.REPLACE)!!
        assertFalse(c.settingsRepository.current.todayShiftNotice)
        assertTrue(Logic.decode(c.settingsRepository.current.logic).rules.any { it.name == "오늘 요약" })
        c.recipes.pieces.restore(com.bamnat.core.recipe.PieceKind.LOGIC, before, PersonalCalendar.MAIN)
        assertTrue(c.settingsRepository.current.todayShiftNotice)
    }

    @Test fun bothKeepsTheNoticeOn() = runBlocking {
        c.settingsRepository.update { it.copy(todayShiftNotice = true, logic = "") }
        c.recipes.pieces.apply(com.bamnat.core.recipe.PieceKind.LOGIC, values, PersonalCalendar.MAIN, "NTC00002", RecipePieces.Mode.BOTH)
        assertTrue(c.settingsRepository.current.todayShiftNotice)
        // In now: nothing new overlaps any more.
        assertTrue(c.recipes.pieces.overlaps(values).isEmpty())
    }

    @Test fun aTwinRuleOfMineIsSwitchedOffAndBack() = runBlocking {
        val mine = LogicRule("앱 열면 오늘 요약", RuleTrigger.OPEN, act = LogicAct(LogicVerb.NOTIFY, "{월}월 {일}일 · 이달 쓴 돈 {원(이달지출)}"))
        c.settingsRepository.update { it.copy(todayShiftNotice = false, logic = Logic.encode(Logic(rules = listOf(mine)))) }
        val store = LogicStore(app)
        store.setOff(mine.key, false)
        val before = c.recipes.pieces.apply(com.bamnat.core.recipe.PieceKind.LOGIC, values, PersonalCalendar.MAIN, "NTC00003", RecipePieces.Mode.REPLACE)!!
        assertTrue(store.isOff(mine.key))
        c.recipes.pieces.restore(com.bamnat.core.recipe.PieceKind.LOGIC, before, PersonalCalendar.MAIN)
        assertFalse(store.isOff(mine.key))
    }

    @Test fun nothingFoldedWithoutTheNotice() = runBlocking {
        c.settingsRepository.update { it.copy(todayShiftNotice = false) }
        // No 근무표 and the notice off: the rule keeps its own notice.
        assertFalse(TodayShiftNotice.fold(app, "오늘 요약", "이달 쓴 돈 0원"))
    }
}
