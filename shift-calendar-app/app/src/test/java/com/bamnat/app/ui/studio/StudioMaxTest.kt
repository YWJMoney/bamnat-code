package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.AppSettings
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ThemeSeed
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** [STU-30][STU-32][STU-33][STU-34] 1.36 스튜디오 극상 1차: 한 색으로 앱 전체, 섞기는 한 걸음, 스타일 복사, 자동 저장과 이어서 하기. */
@RunWith(AndroidJUnit4::class)
class StudioMaxTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val withScreens = AppSettings().copy(screenLooks = ScreenLooks()
        .with("money", ScreenLook(bg = 0xFFFFF8E1, ink = 0xFF333333, photo = "0123456789abcdef"))
        .with("memo", ScreenLook(card = 0xFFE8F5D0)).encode())

    @Test fun oneColourMakesTheWholeApp() {
        val s = ThemeSeed.scheme(0xFFE91E63L, ThemeSeed.Variant.VIVID)
        val d = StudioColors.withScheme(withScreens, s, allScreens = true)
        val looks = ScreenLooks.decode(d.screenLooks)
        val all = looks.looks[ScreenLooks.ALL]!!
        assertEquals(s.bg, all.bg); assertEquals(s.card, all.card); assertEquals(s.ink, all.ink); assertEquals(s.accent, all.accent)
        assertEquals("가계부 사진은 그대로", "0123456789abcdef", looks.looks["money"]?.photo)
        assertNull("가계부 색은 앱 전체를 따라요", looks.looks["money"]?.bg)
        assertNull(looks.looks["memo"])
        assertEquals(s.bg, looks.of("memo").bg)
        val cal = CalendarTheme.decode(d.calTheme)
        assertEquals(s.today, cal.today); assertEquals(s.sat, cal.sat); assertEquals(s.sun, cal.sun); assertNull(cal.holiday)

        val keep = StudioColors.withScheme(withScreens, s, allScreens = false)
        assertEquals("끄면 따로 고른 색은 그대로", 0xFFFFF8E1L or 0xFF000000L, ScreenLooks.decode(keep.screenLooks).looks["money"]?.bg)
        assertEquals(listOf("money", "memo").toSet(), StudioColors.ownColours(ScreenLooks.decode(withScreens.screenLooks)).toSet())
    }

    @Test fun copyToAllPasteAndFollow() {
        val d = StudioColors.toAllScreens(withScreens, "money")
        val looks = ScreenLooks.decode(d.screenLooks)
        assertEquals(0xFFFFF8E1L or 0xFF000000L, looks.looks[ScreenLooks.ALL]?.bg)
        assertEquals(0xFFFFF8E1L or 0xFF000000L, looks.of("memo").bg)
        assertTrue(StudioColors.ownColours(looks).isEmpty())

        val pasted = StudioColors.paste(withScreens, "calendar", ScreenLooks.decode(withScreens.screenLooks).of("money"))
        assertEquals(0xFF333333L or 0xFF000000L, ScreenLooks.decode(pasted.screenLooks).looks["calendar"]?.ink)
        val plaza = StudioColors.paste(withScreens, ScreenLooks.PLAZA, ScreenLook(bg = 0xFF112233, ink = 0xFFFFFFFF))
        assertNull("마을은 배경만", ScreenLooks.decode(plaza.screenLooks).looks[ScreenLooks.PLAZA]?.ink)

        val cleared = StudioColors.clearOwn(withScreens, "money")
        assertNull(ScreenLooks.decode(cleared.screenLooks).looks["money"]?.bg)
        assertEquals("사진은 남아요", "0123456789abcdef", ScreenLooks.decode(cleared.screenLooks).looks["money"]?.photo)
    }

    @Test fun picksInARowAreOneStep() {
        val vm = StudioViewModel(c)
        idle()
        val start = vm.state.value.draft
        listOf(0, 1, 2).forEach { m -> vm.changeMerged("앱 전체 · 한 색") { StudioColors.withScheme(it, ThemeSeed.shuffle(0xFF27AE60L, ThemeSeed.Variant.SOFT, false, m), true) } }
        idle()
        assertEquals("섞기 세 번이 기록 한 줄", 1, vm.state.value.past.size)
        assertEquals(ThemeSeed.shuffle(0xFF27AE60L, ThemeSeed.Variant.SOFT, false, 2).accent,
            ScreenLooks.decode(vm.state.value.draft.screenLooks).looks[ScreenLooks.ALL]?.accent)
        vm.change { it.copy(calTheme = "cell=fill") }
        vm.changeMerged("앱 전체 · 한 색") { StudioColors.withScheme(it, ThemeSeed.scheme(0xFF2F5BEAL), true) }
        assertEquals("다른 고침 뒤에는 새 걸음", 3, vm.state.value.past.size)
        vm.undo(); vm.undo(); vm.undo()
        assertEquals("되돌리기 세 번이면 처음", start, vm.state.value.draft)
        // Text always reads on what one colour makes.
        val all = ScreenLooks.decode(StudioColors.withScheme(start, ThemeSeed.scheme(0xFFF2C94CL, ThemeSeed.Variant.VIVID, dark = true), true).screenLooks).looks[ScreenLooks.ALL]!!
        assertTrue(Palette.contrast(all.ink!!, all.bg!!) >= 4.5)
    }

    @Test fun draftIsKeptAndComesBack() {
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(calTheme = "sat=FF1565C0") }
        var id = 0L
        repeat(40) {
            if (id == 0L) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); Thread.sleep(50); id = vm.state.value.draftId }
        }
        assertTrue("바꾸고 잠시 뒤 만들던 레시피로 남아요", id != 0L)
        val row = runBlocking { c.database.recipeDao().drafts().firstOrNull { it.id == id } }
        assertNotNull(row)
        assertEquals("스튜디오에서 꾸미던 중", 1, row!!.step)

        val again = StudioViewModel(c)
        idle()
        var offered: Long? = null
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { again.resumable.collect { r -> r?.let { offered = it.id } } }
        repeat(40) { if (offered == null) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }
        job.cancel()
        assertEquals("다음에 열면 이어서 하기", id, offered)
        again.resume(id)
        repeat(40) { if (again.state.value.draftId != id) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }
        assertEquals(id, again.state.value.draftId)
        assertEquals(0xFF1565C0L, CalendarTheme.decode(again.state.value.draft.calTheme).sat)
    }
}
