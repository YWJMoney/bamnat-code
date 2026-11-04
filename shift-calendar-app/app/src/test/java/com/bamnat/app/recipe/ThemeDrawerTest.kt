package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** [DRW-01][DRW-02][DRW-03] 1.37 내 서랍: what shows for each filter, 묶음 kept by key, and 입기·벗기 taken back by 되돌리기. */
@RunWith(AndroidJUnit4::class)
class ThemeDrawerTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    private val items = listOf(
        DrawerItem("theme:own:겨울 밤", DrawerKind.MINE, "겨울 밤", 3_000, folder = "겨울"),
        DrawerItem("draft:4", DrawerKind.MINE, "가을 달력", 5_000, words = "만들던"),
        DrawerItem("got:ABCD2345", DrawerKind.GOT, "야간 알람 모음", 4_000, words = "알람"),
        DrawerItem("theme:WXYZ6789", DrawerKind.WORN, "나무 달력", 1_000, folder = "겨울"),
        DrawerItem("part:9", DrawerKind.PART, "‘나무 달력’의 칸 색", 2_000, words = "나무 달력"),
    )

    @Test fun eachFilterShowsItsOwn() {
        val all = Drawer.shown(items, null, null, "", Drawer.Sort.RECENT)
        assertEquals(listOf("draft:4", "got:ABCD2345", "theme:own:겨울 밤", "part:9", "theme:WXYZ6789"), all.map { it.key })
        assertEquals(listOf("theme:own:겨울 밤", "draft:4"), Drawer.shown(items, DrawerKind.MINE, null, "", Drawer.Sort.NAME).map { it.key }.reversed())
        assertEquals(listOf("theme:own:겨울 밤", "theme:WXYZ6789"), Drawer.shown(items, null, "겨울", "", Drawer.Sort.RECENT).map { it.key })
        assertEquals(listOf("draft:4", "got:ABCD2345", "part:9"), Drawer.shown(items, null, Drawer.NO_FOLDER, "", Drawer.Sort.RECENT).map { it.key })
        // 찾기 reads the name, what it holds, where it came from and its 묶음, spaces aside.
        assertEquals(listOf("part:9", "theme:WXYZ6789"), Drawer.shown(items, null, null, "나무달력", Drawer.Sort.RECENT).map { it.key })
        assertEquals(listOf("got:ABCD2345"), Drawer.shown(items, null, null, "알람", Drawer.Sort.RECENT).map { it.key })
        assertEquals(2, Drawer.shown(items, null, null, "겨울", Drawer.Sort.RECENT).size)
        assertTrue(Drawer.shown(items, DrawerKind.PART, "겨울", "", Drawer.Sort.RECENT).isEmpty())
        // 이름순 is 가나다, from the first letter (a quote mark aside).
        assertEquals(listOf("가을 달력", "겨울 밤", "나무 달력", "‘나무 달력’의 칸 색", "야간 알람 모음"), Drawer.shown(items, null, null, "", Drawer.Sort.NAME).map { it.title })
        assertEquals(listOf(null to 5, DrawerKind.MINE to 2, DrawerKind.GOT to 1, DrawerKind.WORN to 1, DrawerKind.PART to 1), Drawer.counts(items))
        assertEquals(listOf("겨울"), Drawer.folders(items))
    }

    @Test fun foldersAreKeptByKeyAndRenamed() {
        c.wardrobe.setFolder("part:1", "출근용")
        c.wardrobe.setFolder("part:2", "출근용")
        c.wardrobe.setFolder("part:3", "  아주아주아주아주긴묶음이름이에요  ")
        assertEquals("출근용", c.wardrobe.folders.value["part:1"])
        assertEquals(ThemeWardrobe.FOLDER_NAME, c.wardrobe.folders.value["part:3"]!!.length)
        c.wardrobe.renameFolder("출근용", "평일")
        assertEquals("평일", c.wardrobe.folders.value["part:2"])
        c.wardrobe.renameFolder("평일", "")
        assertNull(c.wardrobe.folders.value["part:1"])
        c.wardrobe.setFolder("part:3", null)
        assertTrue(c.wardrobe.folders.value.isEmpty())
    }

    @Test fun wearingIsTakenBackWhole() = runBlocking {
        val look = c.recipes.pieces.lookSnapshot()
        val theme = BuiltInThemes.of(WorkStyle.SHIFT).first()
        val m = c.wardrobe.mark()
        assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
        assertEquals(theme.title, c.wardrobe.lease.value.worn?.title)
        assertNotEquals(look, c.recipes.pieces.lookSnapshot())
        val used = c.wardrobe.lease.value.freeUsed
        c.wardrobe.undoTo(m)
        assertNull(c.wardrobe.lease.value.worn)
        assertEquals(look, c.recipes.pieces.lookSnapshot())
        // A free wear stays counted, so 입기 → 되돌리기 → 입기 hands out no extra ones.
        assertEquals(used, c.wardrobe.lease.value.freeUsed)
        // The 받은 레시피 row the 입기 wrote is taken back too.
        assertTrue(c.recipes.inUse().none { it.title == theme.title })

        // 벗기 → 되돌리기 puts the theme back on.
        assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
        val worn = c.recipes.pieces.lookSnapshot()
        val m2 = c.wardrobe.mark()
        c.wardrobe.takeOff()
        assertNull(c.wardrobe.lease.value.worn)
        c.wardrobe.undoTo(m2)
        assertEquals(theme.title, c.wardrobe.lease.value.worn?.title)
        assertEquals(worn, c.recipes.pieces.lookSnapshot())
        c.wardrobe.takeOff()
    }
}
