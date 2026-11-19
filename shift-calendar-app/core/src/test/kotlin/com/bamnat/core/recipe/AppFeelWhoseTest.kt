package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-15] 검토 SN-04·SD-05·SI-03 (사용자 2026-11-12 'A + B 혼합'): a work's 앱 동작 is split by whose each choice is. Its 꾸밈
 * (강조색·움직임) goes on unless turned off; 밝기 and the receiver's own settings are shown, off.
 */
class AppFeelWhoseTest {
    @Test fun everyChoiceOfThePieceHasAnOwnerAndAPlaceInTheList() {
        val keys = PieceKind.APP_FEEL.fields.map { it.key }
        // A field added to 앱 동작 later must be placed too, or it would never be shown in the 넣기 창 (and never go on).
        assertEquals(keys.toSet(), AppFeel.ORDER.toSet())
        assertEquals(AppFeel.ORDER.size, AppFeel.ORDER.distinct().size)
        assertEquals(setOf("accent", "motion"), keys.filter { AppFeel.whose(it) == AppFeel.Whose.LOOK }.toSet())
        assertEquals(setOf("theme"), keys.filter { AppFeel.whose(it) == AppFeel.Whose.BRIGHT }.toSet())
        // 글자 크기·누르기·시계·급여 기간 and the phone's own colours are the receiver's.
        listOf("textStep", "compact", "clock12", "tap", "hold", "dayOrder", "flow", "rest", "payPeriod", "dynamic").forEach {
            assertEquals(it, AppFeel.Whose.MINE, AppFeel.whose(it))
        }
    }

    @Test fun onlyTheLookGoesOnUnasked() {
        // One of the 251 장터 works: 밝게, its 강조색, 글자 크기 보통 (a 1.3배 reader's letters got smaller) and its 움직임.
        val work = mapOf("theme" to "light", "accent" to 0xFF3366CCL, "textStep" to 1L, "clock12" to true, "payPeriod" to true, "motion" to "td:glow")
        assertEquals(mapOf("accent" to 0xFF3366CCL, "motion" to "td:glow"), AppFeel.lookOnly(work))
        // A receiver who turned 움직임 off keeps it off unless they turn the line on.
        assertEquals(mapOf("accent" to 0xFF3366CCL), AppFeel.lookOnly(work, motionOffNow = true))
        assertFalse(AppFeel.onAtFirst("theme"))
        assertFalse(AppFeel.onAtFirst("textStep"))
        assertTrue(AppFeel.onAtFirst("accent", motionOffNow = true))
        // What the 넣기 창 turned on, and nothing else.
        assertEquals(mapOf("theme" to "light", "accent" to 0xFF3366CCL), AppFeel.only(work, setOf("theme", "accent")))
        assertTrue(AppFeel.only(work, emptySet()).isEmpty())
    }

    @Test fun aMotionIsSaidInAFewWords() {
        assertEquals("기본 움직임", Motion().said())
        assertEquals("움직이지 않기", Motion(today = "glow", off = true).said())
        assertEquals("달 넘김 책장 넘기기 · 오늘 칸 은은한 빛", Motion.decode("tn:flip|td:glow").said())
        assertEquals("오늘 칸 숨쉬기 · 빠르게", Motion.decode("td:pulse|sp:f").said())
    }
}
