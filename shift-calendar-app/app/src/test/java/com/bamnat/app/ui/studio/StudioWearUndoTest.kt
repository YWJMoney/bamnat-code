package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * 2.0.0 [RVM-48] 검토 STU-03 (조건 10): 꾸민 뒤 '내 앱에 입히기' → '되돌리기'를 누르면 편집기 초안도 입히기 전 휴대폰 모양으로 바뀌고 지난
 * 단계도 입히는 순간 비워져, 수십 번 고친 것을 통째로 잃었어요. 이제 휴대폰만 돌아가고 만들던 모양과 지난 단계는 남아요.
 */
@RunWith(AndroidJUnit4::class)
class StudioWearUndoTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun settle(until: () -> Boolean) = repeat(60) { if (!until()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }

    @Test fun takingTheWearBackLeavesWhatIWasMaking() {
        val phone = c.settingsRepository.current.calTheme
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(calTheme = "sun=FF1565C0") }
        vm.change { it.copy(calTheme = "sun=FF2E7D32") }
        vm.wear()
        settle { vm.state.value.wornNow }
        assertEquals("입혔어요", "sun=FF2E7D32", c.settingsRepository.current.calTheme)
        assertEquals("지난 단계가 남아요", 2, vm.state.value.past.size)
        assertTrue(vm.state.value.canUndoWear)
        vm.undoWear()
        settle { !vm.state.value.wornNow }
        assertEquals("휴대폰은 입히기 전으로", phone, c.settingsRepository.current.calTheme)
        assertEquals("만들던 모양은 그대로", "sun=FF2E7D32", vm.state.value.draft.calTheme)
        assertTrue("바꾼 것으로 보여요(나갈 때 묻고 자동 저장)", vm.state.value.changed)
        vm.undo()
        assertEquals("한 단계 뒤로도 가요", "sun=FF1565C0", vm.state.value.draft.calTheme)
    }

    /**
     * 2.0.0 [RVM-52] 검토 STU-05 (조건 10): 내가 꾸민 모양을 입힌 뒤(되돌리기 A) 내 테마를 그대로 입히면, 되돌리기가 그 테마 바로 전이 아니라
     * 처음 꾸미기 전 모양 A로 두 단계 돌아갔어요. 이제 마지막 입히기만 되돌리고, 그 전에 입힌 내 모양은 남아요.
     */
    @Test fun takingBackAThemeWornAfterMyLookGoesBackOneStep() {
        val start = c.settingsRepository.current.calTheme
        // 내 테마 '파랑' (서랍에 내 것으로).
        c.settingsRepository.update { it.copy(calTheme = "sun=FF1565C0") }
        val blue = kotlinx.coroutines.runBlocking { c.wardrobe.myLookRecipe("파랑") }
        assertTrue(c.wardrobe.saveLook(blue))
        c.settingsRepository.update { it.copy(calTheme = start) }
        val vm = StudioViewModel(c)
        idle()
        // ① 내가 꾸민 모양(초록)을 입혀요.
        vm.change { it.copy(calTheme = "sun=FF2E7D32") }
        vm.wear()
        settle { vm.state.value.wornNow }
        assertEquals("sun=FF2E7D32", c.settingsRepository.current.calTheme)
        // ② 되돌리지 않고 내 테마 '파랑'을 그대로 입혀요.
        vm.startFrom(c.wardrobe.lease.value.history.first { it.own && it.title == "파랑" })
        settle { vm.state.value.draft.calTheme == "sun=FF1565C0" }
        vm.wear()
        settle { vm.state.value.wornNow }
        assertEquals("sun=FF1565C0", c.settingsRepository.current.calTheme)
        assertEquals("파랑", c.wardrobe.wornTitle())
        assertTrue("되돌리기는 마지막 입히기 것", vm.state.value.canUndoWear)
        assertEquals("달력의 '입히기 전 모양으로'도 ① 것은 남지 않아요", null, com.bamnat.app.data.LookUndo.read(c.appContext))
        vm.undoWear()
        settle { !vm.state.value.wornNow && c.settingsRepository.current.calTheme != "sun=FF1565C0" }
        assertEquals("① 바로 뒤 모양(초록)으로, 처음 모양까지 두 단계가 아니에요", "sun=FF2E7D32", c.settingsRepository.current.calTheme)
        assertEquals("서랍도 '파랑'을 입은 중이 아니에요", null, c.wardrobe.wornTitle())
    }
}
