package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.AppSettings
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ShiftLooks
import com.bamnat.core.recipe.ThemeSeed
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.toArgb
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** [STU-35][STU-37][STU-39][STU-40] 1.36 스튜디오 극상 2·3차: 느낌으로 시작, 근무에 따라, 잠그기, 내 색, 이어서 하기 뒤 되돌리기. */
@RunWith(AndroidJUnit4::class)
class StudioMax2Test {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun settle(until: () -> Boolean) = repeat(40) { if (!until()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }

    @Test fun aFeelIsOneStepFromThePlainLook() {
        val vm = StudioViewModel(c)
        idle()
        val (_, scheme) = feelStarts(0xFF27AE60L, Feel.NIGHT)[0]
        assertTrue("밤처럼은 어두운 화면", scheme.dark)
        vm.startFeel { feelDraft(it, scheme, 2) }
        idle()
        val d = vm.state.value.draft
        assertEquals(scheme.bg, ScreenLooks.decode(d.screenLooks).looks[ScreenLooks.ALL]?.bg)
        assertEquals("스티커 칸", "circle", CalendarView.decode(d.calView).looks.single().dateShape)
        assertEquals("느낌으로 시작은 한 걸음", 1, vm.state.value.past.size)
        assertEquals("느낌으로 시작", vm.state.value.past.single().label)
        vm.undo()
        assertEquals(c.recipes.pieces.plainLook(c.settingsRepository.current), vm.state.value.draft)
        // Every feel and start reads.
        Feel.entries.forEach { f -> feelStarts(0xFFF2C94CL, f).forEach { (_, s) -> assertTrue(Palette.contrast(s.ink, s.bg) >= 4.5) } }
    }

    @Test fun aLockedTabKeepsItsLook() {
        val start = AppSettings().copy(calTheme = CalendarTheme(sat = 0xFF1565C0).encode(),
            screenLooks = ScreenLooks().with("money", ScreenLook(photo = "0123456789abcdef")).encode())
        val next = StudioColors.withScheme(start, ThemeSeed.scheme(0xFFE91E63L, ThemeSeed.Variant.VIVID, dark = true), allScreens = true)
        val held = StudioColors.hold(TabOrder.CALENDAR, StudioColors.hold(TabOrder.MONEY, next, start), start)
        assertEquals("달력 색 그대로", start.calTheme, held.calTheme)
        val looks = ScreenLooks.decode(held.screenLooks)
        // The money screen had the app's own colours (none set): it is held at the app's own background, not the new one.
        assertEquals("가계부는 예전 바탕", com.bamnat.app.ui.theme.LightColors.background.let { androidx.compose.ui.graphics.Color(it.value) }
            .let { (it.toArgb().toLong() and 0xFFFFFFFFL) }, looks.of("money").bg)
        assertEquals("가계부 그림 그대로", "0123456789abcdef", looks.of("money").photo)
        assertNotEquals("잠그지 않은 메모는 새 색", ScreenLooks.decode(start.screenLooks).of("memo").bg, looks.of("memo").bg)

        val vm = StudioViewModel(c)
        idle()
        vm.locked = setOf(TabOrder.CALENDAR)
        val before = vm.state.value.draft.calTheme
        vm.changeMerged("앱 전체 · 한 색") { StudioColors.withScheme(it, ThemeSeed.scheme(0xFF2F5BEAL), true) }
        idle()
        assertEquals("잠근 달력은 한 색에도 그대로", before, vm.state.value.draft.calTheme)
        assertEquals("다음에 열어도 잠겨 있어요", setOf(TabOrder.CALENDAR), StudioViewModel(c).locked)
        vm.locked = emptySet()
    }

    @Test fun shiftLooksFollowTheWork() {
        assertEquals(emptyList<ShiftLooks.Mode>(), shiftModes(WorkStyle.INVEST))
        assertEquals(listOf(ShiftLooks.Mode.OFF), shiftModes(WorkStyle.BUSINESS))
        assertTrue(ShiftLooks.Mode.NIGHT in shiftModes(WorkStyle.SHIFT))
        val look = shiftLookOf(ThemeSeed.scheme(0xFF3949ABL, ThemeSeed.Variant.SOFT, dark = true))
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(shiftLooks = ShiftLooks(night = look).encode()) }
        vm.previewAs(ShiftLooks.Mode.NIGHT)
        settle { vm.state.value.modePieces != null }
        assertTrue("야간 미리 보기는 그 모양", vm.state.value.modePieces != null)
        vm.previewAs(ShiftLooks.Mode.NORMAL)
        settle { vm.state.value.modePieces == null }
        assertEquals(null, vm.state.value.modePieces)
    }

    @Test fun pinnedColoursComeBack() {
        val vm = StudioViewModel(c)
        idle()
        vm.pinColor(0xFF112233L)
        vm.pinColor(0xFFE91E63L)
        settle { StudioViewModel.myColorsOf(c.settingsRepository.current.myColors).size == 2 }
        assertEquals(listOf(0xFFE91E63L, 0xFF112233L), StudioViewModel.myColorsOf(c.settingsRepository.current.myColors))
        vm.pinColor(0xFF112233L)
        settle { StudioViewModel.myColorsOf(c.settingsRepository.current.myColors).size == 1 }
        assertEquals("다시 누르면 빠져요", listOf(0xFFE91E63L), StudioViewModel.myColorsOf(c.settingsRepository.current.myColors))
        runBlocking { c.settingsRepository.update { it.copy(myColors = "") } }
    }

    @Test fun resumedDraftKeepsUndo() {
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(calTheme = "sun=FFC62828") }
        var id = 0L
        repeat(40) { if (id == 0L) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); Thread.sleep(50); id = vm.state.value.draftId } }
        assertTrue(id != 0L)
        val again = StudioViewModel(c)
        idle()
        again.resume(id)
        settle { again.state.value.draftId == id && again.state.value.past.isNotEmpty() }
        assertTrue("이어 온 뒤에도 바꾼 곳이 있어요", again.state.value.changed)
        again.undo()
        assertEquals("되돌리면 지금 휴대폰 모양", c.settingsRepository.current.calTheme, again.state.value.draft.calTheme)
    }

    @Test fun theLookBeforeIsKeptForADay() {
        val ctx = ApplicationProvider.getApplicationContext<BamnatApp>()
        com.bamnat.app.data.LookUndo.keep(ctx, mapOf("calTheme" to "sat=FF1565C0"), at = 1_000L)
        assertEquals(mapOf("calTheme" to "sat=FF1565C0"), com.bamnat.app.data.LookUndo.read(ctx, now = 1_000L + 3_600_000L)?.first)
        assertEquals("하루 지나면 없어요", null, com.bamnat.app.data.LookUndo.read(ctx, now = 1_000L + com.bamnat.app.data.LookUndo.KEEP_MS))
        com.bamnat.app.data.LookUndo.clear(ctx)
        assertEquals(null, com.bamnat.app.data.LookUndo.read(ctx, now = 2_000L))
    }
}
