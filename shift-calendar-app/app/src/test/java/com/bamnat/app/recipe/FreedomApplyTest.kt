package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.MemoTemplateStore
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.calendar.CalendarPart
import com.bamnat.app.ui.calendar.calendarAbove
import com.bamnat.app.ui.calendar.calendarParts
import com.bamnat.app.ui.calendar.withPartMoved
import com.bamnat.core.memo.MemoTemplate
import com.bamnat.core.memo.TemplateAsk
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 1.35 꾸미기 자유도 on the phone: [CVW-01] 달력 모양 and [CVW-07][CVW-08][CVW-09] 앱 동작 put on by a recipe and taken back,
 * [CVW-06] a card moved across the month, [CVW-10] a memo template's 입력 칸 kept and asked.
 */
@RunWith(AndroidJUnit4::class)
class FreedomApplyTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    @Test fun calendarViewAndAppFeelPutOnThenTakenBack() = runBlocking {
        val before = c.settingsRepository.current
        val r = Recipe(title = "메모 중심 다이어리", styles = setOf(WorkStyle.SHIFT), pieces = listOf(
            Piece(PieceKind.CAL_VIEW, mapOf("size" to "huge", "badge" to "line", "date" to "left", "grid" to "line", "memo" to 4L, "order" to listOf("memo", "holiday"))),
            Piece(PieceKind.APP_FEEL, mapOf("theme" to "dark", "accent" to 0xFF00897BL, "hold" to "memo", "compact" to true)),
        ))
        val picks = r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        val id = c.recipes.apply(r, "FREE2345", 1, PersonalCalendar.MAIN, picks, emptyMap())
        assertNotNull(id)
        val s = c.settingsRepository.current
        val v = CalendarView.decode(s.calView)
        assertEquals(listOf("huge", "line", "left", "line"), listOf(v.size, v.badge, v.date, v.grid))
        assertEquals(4, v.memo)
        assertEquals(listOf("memo", "holiday"), v.order)
        assertTrue("메모 줄이 있으면 칸에 메모가 켜져요", s.memoInCells)
        assertEquals(0xFF00897BL, s.accentColor)
        assertEquals("memo", s.longPress)
        assertTrue(s.compact)

        // The user changes the accent after; 되돌리기 keeps it and puts the rest back.
        c.settingsRepository.update { it.copy(accentColor = 0xFFE53935L) }
        assertTrue(c.recipes.undo(id!!))
        val back = c.settingsRepository.current
        assertEquals(before.calView, back.calView)
        assertEquals(before.longPress, back.longPress)
        assertEquals(before.compact, back.compact)
        assertEquals(before.memoInCells, back.memoInCells)
        assertEquals(0xFFE53935L, back.accentColor)
    }

    @Test fun exportReadsWhatTheUserSet() = runBlocking {
        c.settingsRepository.update { it.copy(calView = CalendarView(size = "tall", memo = 2).encode(), memoInCells = true, longPress = "open", accentColor = 0xFF3949ABL) }
        val view = c.recipes.pieces.export(PieceKind.CAL_VIEW, PersonalCalendar.MAIN)!!
        assertEquals("tall", view["size"])
        assertEquals(2L, (view["memo"] as Number).toLong())
        val feel = c.recipes.pieces.export(PieceKind.APP_FEEL, PersonalCalendar.MAIN)!!
        assertEquals("open", feel["hold"])
        assertEquals(0xFF3949ABL, (feel["accent"] as Number).toLong())
    }

    @Test fun aCardMovesAcrossTheMonth() {
        var s = AppSettings()
        val above = { x: AppSettings -> calendarAbove(x.calendarParts().map { it.part }) }
        assertFalse("처음엔 이번 달 메모가 달력 아래", above(s)(CalendarPart.MEMOS))
        repeat(12) { if (!above(s)(CalendarPart.MEMOS)) s = s.withPartMoved(CalendarPart.MEMOS, -1) }
        assertTrue("위로 옮기면 달력을 건너 위로", above(s)(CalendarPart.MEMOS))
        assertTrue(CalendarPart.MONTH.id in s.calendarLayout)
        repeat(12) { if (above(s)(CalendarPart.MEMOS)) s = s.withPartMoved(CalendarPart.MEMOS, 1) }
        assertFalse(above(s)(CalendarPart.MEMOS))
    }

    @Test fun aTemplatesAsksAreKeptAndAsked() {
        val store = MemoTemplateStore(app)
        val t = MemoTemplate("t-cond", "컨디션 일기", asks = listOf(
            TemplateAsk("컨디션", "star"), TemplateAsk("잠", "num"), TemplateAsk("기분", "pick", listOf("좋음", "보통", "나쁨")),
        ))
        store.add(t)
        assertEquals(t.asks, MemoTemplateStore(app).items.value.first { it.id == "t-cond" }.asks)
        val day = LocalDate.of(2026, 11, 2)
        val memo = t.start(day, day, answers = mapOf("컨디션" to "4", "잠" to "6", "기분" to "좋음"))
        assertTrue(memo.body.contains("컨디션: ★★★★☆"))
        assertTrue(memo.body.contains("잠: 6"))
        assertTrue(memo.body.contains("기분: 좋음"))
    }
}
