package com.bamnat.app.ui.calendar

import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVH-42] 검토 PC-29·30: which of the head's decorations stand in its row as buttons, and what they say. */
class HeadQuickTest {
    private val water = LogicButton("물 한 잔", "💧", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0), givenId = "water")
    private val focus = LogicButton("집중 시작", "🎧", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "집중", by = 1.0), givenId = "focus")
    private val buttons = listOf(water, focus).associateBy { it.id }

    private fun chip(text: String, tap: String, at: String = Decor.HEAD) =
        DecorItem(DecorKind.TEXT, text, at = at, x = 850, y = 690, w = 104, h = 26, fill = 0xFF7CF0FF, ink = 0xFF04121A, r = 13, over = true, tap = tap)

    @Test fun aWorksHeadChipIsAButtonWithItsWordsAndTheButtonsName() {
        val q = HeadQuick.of(Decor(listOf(chip("물 +1잔", "water")))) { buttons[it] }.single()
        assertEquals("water", q.id)
        assertEquals("물 +1잔", q.text)
        assertEquals("💧", q.face)
        assertEquals("물 +1잔, 물 한 잔", q.said)
        assertEquals(0xFF7CF0FF, q.fill)
    }

    @Test fun onlyTheHeadsPressingDecorationsOnceEachAndNoneWhoseButtonIsGone() {
        val layer = Decor(listOf(
            DecorItem(DecorKind.SHAPE, "circle", at = Decor.HEAD, x = 100, y = 500),
            chip("물 +1잔", "water"),
            chip("물 한 잔 더", "water"),
            chip("집중", "focus", at = Decor.CELL),
            chip("없는 단추", "gone"),
            chip("집중 시작", "focus"),
        ))
        assertEquals(listOf("water", "focus"), HeadQuick.of(layer) { buttons[it] }.map { it.id })
    }

    @Test fun anEmojiOrAShapeTakesTheButtonsOwnNameAndItsFirstLetterWithoutAnEmoji() {
        val plain = LogicButton("커피", null, ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "커피", by = 1.0), givenId = "coffee")
        val layer = Decor(listOf(
            DecorItem(DecorKind.EMOJI, "☕", at = Decor.HEAD, tap = "coffee"),
        ))
        val q = HeadQuick.of(layer) { if (it == "coffee") plain else null }.single()
        assertEquals("커피", q.text)
        assertEquals("☕", q.face)
        assertEquals("커피", q.said)
        val shape = HeadQuick.of(Decor(listOf(DecorItem(DecorKind.SHAPE, "star", at = Decor.HEAD, tap = "coffee")))) { if (it == "coffee") plain else null }.single()
        assertEquals("커", shape.face)
    }
    @Test fun theHeadsRoomIsDecidedInOnePlaceAndWhatElseSharesTheRowTakesItsPart() {
        assertEquals(HeadQuickRoom.NONE, HeadQuickRoom.of(0, 707f, 1f, narrow = false))
        // A folded phone: one round button, 도구 an icon.
        HeadQuickRoom.of(2, 369f, 1f, narrow = true).let { assertTrue(it.round); assertTrue(it.tight); assertEquals(1, it.shown) }
        // Unfolded: two with their words; with large text, one round one.
        assertEquals(HeadQuickRoom(2, round = false, tight = false), HeadQuickRoom.of(3, 707f, 1f, narrow = false))
        HeadQuickRoom.of(3, 707f, 2f, narrow = false).let { assertTrue(it.round); assertTrue(it.tight) }
        // A tablet: at most ROW_MAX, never more than there are.
        assertEquals(HeadQuick.ROW_MAX, HeadQuickRoom.of(5, 1200f, 1f, narrow = false).shown)
        assertEquals(1, HeadQuickRoom.of(1, 1200f, 1f, narrow = false).words)
        // Something else in the row (알림함) takes its width from the same sum.
        assertEquals(1, HeadQuickRoom.of(3, 707f, 1f, narrow = false, reserved = 100f).words)
    }
}
