package com.bamnat.core.layout

import com.bamnat.core.layout.TabSlots.Group
import com.bamnat.core.layout.TabSlots.One
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NAV2-01] 아래 5칸 채우기와 묶음 탭. */
class TabSlotsTest {
    private val known: (String) -> Boolean = { it in TabOrder.DEFAULT || it.startsWith(TabSlots.FEATURE) || it == "x:studio" }

    @Test fun aPhoneThatNeverChangedGetsFiveTabsAndSettingsGoesToTheMeButton() {
        val bar = TabSlots.resolve("", emptyList(), known)
        assertEquals(listOf("calendar", "money", "memos", "stats", "plaza").map(::One), bar)
        assertEquals(listOf("settings"), TabSlots.missing(bar))
        assertEquals("calendar", TabSlots.home(bar))
    }

    @Test fun aSavedTabOrderIsKeptAndSettingsFirstStaysHome() {
        val moved = TabSlots.fromOrder(listOf("memos", "calendar", "money", "stats", "plaza", "settings"))
        assertEquals("memos", TabSlots.home(moved))
        assertEquals(5, moved.size)
        val settingsFirst = TabSlots.fromOrder(listOf("settings", "calendar", "money", "memos", "stats", "plaza"))
        assertEquals("settings", TabSlots.home(settingsFirst))
        assertEquals(listOf("plaza"), TabSlots.missing(settingsFirst))
    }

    @Test fun aGroupRoundTripsAndDropsWhatThisVersionDoesNotKnow() {
        val bar = listOf(One("calendar"), Group("잠·쉼", listOf("f:SLEEP", "f:LEAVE", "zz")), One("memos"))
        val text = TabSlots.encode(bar)
        assertEquals(listOf(One("calendar"), Group("잠·쉼", listOf("f:SLEEP", "f:LEAVE")), One("memos")), TabSlots.decode(text, known))
    }

    @Test fun aScreenIsInOnePlaceOnlyAndAGroupOfOneIsThatScreen() {
        val got = TabSlots.decode("calendar;g|모음|calendar+f:SLEEP;memos;memos", known)
        assertEquals(listOf(One("calendar"), One("f:SLEEP"), One("memos")), got)
    }

    @Test fun atMostFivePlacesAndANameWithoutTheSplitters() {
        // 1.39 [NAV2-07] Seven are kept (an unfolded phone's rail); a phone bar shows five.
        val got = TabSlots.decode("calendar;money;memos;stats;plaza;settings;f:SLEEP;f:PAY", known)
        assertEquals(7, got.size)
        assertEquals(5, TabSlots.onBar(got, wide = false).size)
        assertEquals(5, TabSlots.addTo(TabSlots.decode("calendar;money;memos;stats;plaza", known), 5, "settings").size)
        assertEquals("가계부모음", TabSlots.cleanName("가계부;|모음+"))
        assertEquals("모음", (TabSlots.decode("calendar;g||money+memos", known)[1] as Group).name)
    }

    @Test fun nothingKnownLeftFallsBackToTheTabOrder() {
        assertEquals(TabSlots.fromOrder(emptyList()), TabSlots.resolve("zz;g|x|yy+qq", emptyList(), known))
    }

    @Test fun addingToAPlaceMakesAGroupAndTakesTheScreenFromWhereItWas() {
        val bar = TabSlots.fromOrder(emptyList())
        val made = TabSlots.addTo(bar, 4, "settings", "나")
        assertEquals(Group("나", listOf("plaza", "settings")), made[4])
        // memos moved into the 묶음 leaves its own place.
        val moved = TabSlots.addTo(made, 4, "memos")
        assertEquals(4, moved.size)
        assertEquals(Group("나", listOf("plaza", "settings", "memos")), moved[3])
        // Past the end: a new place, while there is room.
        val more = TabSlots.addTo(moved, 9, "f:SLEEP")
        assertEquals(One("f:SLEEP"), more.last())
        assertEquals(more, TabSlots.addTo(more, 9, "x:studio"))
    }

    @Test fun replacingAndRemovingKeepAtLeastOnePlace() {
        val bar = TabSlots.fromOrder(emptyList())
        val r = TabSlots.replace(bar, 2, "money")
        assertEquals(4, r.size)
        assertEquals(One("money"), r[1])
        var one = listOf<TabSlots.Slot>(One("calendar"))
        one = TabSlots.remove(one, 0)
        assertEquals(1, one.size)
    }

    @Test fun takingAScreenOutOfAGroupOfTwoLeavesTheOther() {
        val bar = listOf(One("calendar"), Group("모음", listOf("money", "f:OWED")))
        assertEquals(listOf(One("calendar"), One("money")), TabSlots.without(bar, "f:OWED"))
    }

    @Test fun dragOrderRenameAndMemberMoves() {
        val bar = listOf(One("calendar"), Group("돈", listOf("money", "f:OWED", "f:PAY")), One("memos"))
        val keys = bar.map(TabSlots::key).reversed()
        assertEquals(bar.reversed(), TabSlots.ordered(bar, keys))
        assertEquals("돈과일", (TabSlots.rename(bar, 1, "돈과일")[1] as Group).name)
        assertEquals(listOf("f:PAY", "money", "f:OWED"), (TabSlots.moveMember(bar, 1, 2, 0)[1] as Group).members)
        assertEquals(listOf(One("memos"), bar[0], bar[1]), TabSlots.move(bar, 2, 0))
    }

    @Test fun theBarFromTheOrderIsNotSaved() {
        val bar = TabSlots.fromOrder(listOf("money"))
        assertEquals("", TabSlots.toSave(bar, listOf("money")))
        assertTrue(TabSlots.toSave(TabSlots.move(bar, 0, 1), listOf("money")).isNotEmpty())
        assertFalse(TabSlots.shows(bar, "settings"))
    }

    @Test fun aBarOfTabsOnlyIsKeptAsTheTabOrder() {
        assertEquals(listOf("memos", "calendar", "money", "stats", "plaza", "settings"),
            TabSlots.asOrder(listOf("memos", "calendar", "money", "stats", "plaza").map(::One)))
        assertEquals(listOf("settings", "calendar", "money", "memos", "stats", "plaza"),
            TabSlots.asOrder(listOf("settings", "calendar", "money", "memos", "stats").map(::One)))
        // Four tabs, another screen or a 묶음 need the places saved.
        assertEquals(null, TabSlots.asOrder(listOf("calendar", "money", "memos", "stats").map(::One)))
        assertEquals(null, TabSlots.asOrder(listOf(One("calendar"), One("f:SLEEP"))))
        assertEquals(null, TabSlots.asOrder(listOf(One("calendar"), Group("돈", listOf("money", "stats")))))
    }

    /** 1.39 [NAV2-07] Beside an unfolded phone's rail two more places; folded, their screens wait by the 나 button. */
    @Test fun anUnfoldedRailHoldsSevenAndFoldedTheRestWaitByMe() {
        val five = TabSlots.decode("calendar;money;memos;stats;plaza", known)
        val six = TabSlots.addTo(five, 5, "f:SLEEP", max = TabSlots.MAX_WIDE)
        val seven = TabSlots.addTo(six, 6, "settings", max = TabSlots.MAX_WIDE)
        assertEquals(7, seven.size)
        assertEquals("no eighth", seven, TabSlots.addTo(seven, 7, "f:PAY", max = TabSlots.MAX_WIDE))
        assertEquals(seven, TabSlots.decode(TabSlots.encode(seven), known))
        assertEquals(7, TabSlots.onBar(seven, wide = true).size)
        assertEquals(listOf("f:SLEEP", "settings"), TabSlots.offBar(seven, wide = false))
        assertEquals(emptyList<String>(), TabSlots.offBar(seven, wide = true))
        // Folded, 설정 in the seventh place is not on the bar: the 나 button lists it.
        assertTrue(TabOrder.SETTINGS in TabSlots.missing(TabSlots.onBar(seven, wide = false)))
        assertFalse(TabOrder.SETTINGS in TabSlots.missing(TabSlots.onBar(seven, wide = true)))
    }
}
