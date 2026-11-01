package com.bamnat.app.ui.decor

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.ui.studio.StudioViewModel
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.recipe.PieceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** [LAY-04][LAY-05] 1.36 꾸밈 층 in the studio: one drag is one step to undo, and the layer goes out in the recipe. */
@RunWith(AndroidJUnit4::class)
class DecorStudioTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private val coin = DecorItem(DecorKind.EMOJI, "💰", at = Decor.CELL, on = "d25")
    private val title = DecorItem(DecorKind.TEXT, "이번 달도 힘내기", at = Decor.HEAD, y = 300, w = 160, h = 28)

    @Test fun aDragIsOneStepAndUndoTakesItBack() {
        val vm = StudioViewModel(c)
        idle()
        vm.where = "꾸밈 넣기"
        vm.change { it.copy(decor = Decor(listOf(coin, title)).encode()) }
        // One drag sends many frames; they are one 되돌리기 step.
        (1..12).forEach { i -> vm.changeMerged("꾸밈 옮기기") { it.copy(decor = Decor(listOf(coin.copy(x = 500 + i * 10), title)).encode()) } }
        idle()
        assertEquals(listOf("꾸밈 넣기", "꾸밈 옮기기"), vm.state.value.past.map { it.label })
        assertEquals(620, Decor.decode(vm.state.value.draft.decor).items.first().x)
        vm.undo()
        idle()
        assertEquals(500, Decor.decode(vm.state.value.draft.decor).items.first().x)
    }

    @Test fun theLayerGoesOutInTheRecipe() = runBlocking {
        val s = c.settingsRepository.current.copy(decor = Decor(listOf(coin, title)).encode())
        val values = RecipePieces(c).export(PieceKind.DECOR, com.bamnat.app.data.PersonalCalendar.MAIN, s)
        assertNotNull(values)
        val back = Decor.of(values!!)
        assertEquals(listOf("💰", "이번 달도 힘내기"), back.items.map { it.glyph })
        assertEquals("d25", back.items.first().on)
    }
}
