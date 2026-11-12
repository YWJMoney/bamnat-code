package com.bamnat.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVP-45] 검토 UIX-01·U13-01·HQ-22·HQ-29·PC-29: the calendar's head is one row on every width and text size, decided in one place
 * ([HeadRoom]). What does not fit turns into its icon or waits in 도구 and the calendar chip's list, in one order; the month is never
 * shrunk (a work's big month steps down one size), the chip's name is never cut (its face stays), and the work's 바로 단추 take what is
 * left without covering 도구 or 나.
 */
class HeadRoomTest {
    @Test fun anUnfoldedPhoneKeepsTheWholeRowInWords() {
        val r = HeadRoom.of(707f, 1f, 24f, bell = true, nameChars = 3)
        assertTrue(r.name)
        assertEquals(HeadRoom.WORDS, r.share)
        assertTrue(r.todayWords && r.toolsWords && r.arrows)
        assertFalse(r.shortTitle || r.stepDown)
    }

    @Test fun bigLettersOnAnUnfoldedPhoneFoldTheWordsFirstAndKeepTheArrows() {
        val r = HeadRoom.of(707f, 2f, 24f, bell = true, nameChars = 3)
        assertTrue("‹ 11월 ›는 넓은 화면에서 끝까지 남아요", r.arrows)
        assertFalse("칩은 이름 대신 얼굴로", r.name)
        assertEquals(HeadRoom.ICON, r.share)
        assertTrue(r.shortTitle)
    }

    @Test fun aFoldedPhoneAtTwiceTheTextKeepsTheMonthWholeAndEverythingElseAsIcons() {
        val r = HeadRoom.of(369f, 2f, 16f, bell = true, nameChars = 3)
        assertFalse("HQ-22: 달은 줄이지 않아요", r.stepDown)
        assertTrue(r.shortTitle)
        assertFalse("HQ-29: '내 달…'로 자르지 않고 얼굴만", r.name)
        assertEquals("공유는 도구 안에", 0, r.share)
        assertFalse(r.todayWords || r.toolsWords)
    }

    /** [RVG-34] PC-132: a 팀 리더's link stays beside the chip after the other faces fold (open screen); a phone has it in the chip's list. */
    @Test fun aTeamLinkStaysLongestOnTheOpenScreen() {
        val open = HeadRoom.of(707f, 1f, 24f, bell = true, others = 2, team = true, nameChars = 3)
        assertTrue("팀 근무표 얼굴은 남아요: $open", open.team > 0)
        assertTrue("다른 얼굴은 하나까지: $open", open.faces <= 1)
        assertTrue("200%에서도 얼굴은 남아요", HeadRoom.of(707f, 2f, 24f, bell = true, others = 1, team = true).team == HeadRoom.ICON)
        assertEquals("접은 폭에서는 칩의 목록에", 0, HeadRoom.of(369f, 1f, 16f, bell = true, others = 1, team = true).team)
        assertEquals("팀 링크가 없으면 없어요", 0, HeadRoom.of(707f, 1f, 24f, bell = true, others = 1).team)
    }

    @Test fun aWorksBigMonthStepsDownOneSizeInsteadOfShrinking() {
        assertTrue("HQ-22: 28sp 달 제목은 접은 폭 200%에서 한 단계 낮춰요", HeadRoom.of(369f, 2f, 28f, bell = true).stepDown)
        assertFalse(HeadRoom.of(369f, 2f, 16f, bell = true).stepDown)
        assertFalse(HeadRoom.of(707f, 1f, 28f, bell = true).stepDown)
    }

    @Test fun theWorksButtonsTakeWhatIsLeftAndNeverPushOutToolsOrMe() {
        HeadRoom.of(707f, 1f, 24f, quick = 3, bell = true).quick.let { q ->
            assertFalse(q.round)
            assertTrue("넓은 화면에는 글자 단추가 들어가요: $q", q.shown >= 1)
        }
        HeadRoom.of(369f, 2f, 16f, quick = 2, bell = true).let { r ->
            assertTrue("PC-29: 접은 폭 200%에는 동그란 단추 하나, 나머지는 도구에", r.quick.round)
            assertEquals(1, r.quick.shown)
            assertTrue(r.quick.tight)
        }
        assertEquals(HeadQuickRoom.NONE, HeadRoom.of(707f, 1f, 24f, quick = 0).quick)
    }

    @Test fun aWideScreenShowsTheOtherCalendarsBesideTheChip() {
        val r = HeadRoom.of(1024f, 1f, 24f, bell = true, others = 2, nameChars = 3)
        assertEquals(2, r.faces)
        val phone = HeadRoom.of(412f, 1f, 22f, bell = true, others = 2, together = true, nameChars = 3)
        assertEquals("폰에서는 달력 칩의 목록에", 0, phone.faces)
        assertFalse(phone.together)
    }

    /** 총괄 HQ-59: a 360dp phone keeps the month at its size at 100% and 200% (no step down, the year goes). */
    @Test fun a360PhoneKeepsTheMonthAtItsSize() {
        for (fs in listOf(1f, 2f)) HeadRoom.of(360f, fs, 16f, bell = true, nameChars = 3).let { r ->
            assertFalse("$fs: 달은 줄이지 않아요", r.stepDown)
            assertTrue(r.shortTitle)
        }
    }
}
