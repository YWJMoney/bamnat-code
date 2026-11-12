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
}
