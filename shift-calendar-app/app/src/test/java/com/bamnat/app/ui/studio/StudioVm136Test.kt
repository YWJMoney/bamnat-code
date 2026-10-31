package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.Ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** [STU-20][STU-21] 1.36 스튜디오 하나로: 기능 go with the look, every step is in the 기록 목록, and amounts go up as 받는 분 값. */
@RunWith(AndroidJUnit4::class)
class StudioVm136Test {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun historyHoldsLookAndFeaturesAndGoesBackToAStep() {
        val vm = StudioViewModel(c)
        idle()
        vm.where = "달력 · 오늘·주말 색"
        vm.change { it.copy(calTheme = "sat=FF1565C0") }
        vm.setExtra(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "day", "before" to listOf(60L))), "snooze" to 5L))
        idle()
        val s = vm.state.value
        assertEquals(listOf("달력 · 오늘·주말 색", PieceKind.ALARMS.label), s.past.map { it.label })
        assertTrue(s.changed)
        vm.undo()
        assertNull("되돌리기는 기능도 함께", vm.state.value.extras[PieceKind.ALARMS])
        vm.redo()
        assertTrue(vm.state.value.extras.containsKey(PieceKind.ALARMS))
        vm.backTo(0)
        val back = vm.state.value
        assertTrue(back.past.isEmpty())
        assertFalse(back.extras.containsKey(PieceKind.ALARMS))
        assertEquals("다시 하기로 두 걸음 되살려요", 2, back.future.size)
    }

    @Test fun uploadCarriesFeaturesWithAmountsAsInputs() {
        val vm = StudioViewModel(c)
        idle()
        vm.setExtra(PieceKind.HOURLY_RULES, mapOf("wage" to 12_000L, "weeklyPay" to true))
        idle()
        val (body, _) = vm.upload()
        // The name is given in 올리기 (이름·표지); read here with one.
        val r = RecipeCodec.read(body.replace("\"title\":\"\"", "\"title\":\"시험\""))
        val hourly = r.pieces.first { it.kind == PieceKind.HOURLY_RULES }
        assertTrue("내 시급은 나가지 않고 받는 분 값으로", hourly.values["wage"] is Ref.Input)
        assertTrue(r.inputs.isNotEmpty())
    }
}
