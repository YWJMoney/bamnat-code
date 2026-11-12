package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** 2.0.0 [RVS-13] 검토 STU-02·STU-07·U09a-13: what is being made keeps where it came from, is kept at once, and a step back is a change. */
@RunWith(AndroidJUnit4::class)
class StudioDraftKeepTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun settle(until: () -> Boolean) = repeat(60) { if (!until()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); Thread.sleep(30) } }

    private val theirs = RecipeCodec.write(Recipe("다른 분 작품", styles = WorkStyle.entries.toSet(),
        pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFFC62828L)))))

    /** STU-02: a draft of someone else's work, opened again with 이어서 하기, is still theirs (출처·30일·광고). */
    @Test fun aResumedDraftKeepsWhereItCameFrom() {
        val vm = StudioViewModel(c)
        idle()
        vm.startRecipe(theirs, "THERS234", null)
        settle { vm.state.value.parentCode == "THERS234" }
        vm.change { it.copy(calTheme = "sun=FF1565C0") }
        settle { vm.state.value.draftId != 0L }
        val id = vm.state.value.draftId
        assertTrue("자동 저장", id != 0L)
        val body = runBlocking { c.database.recipeDao().drafts().first { it.id == id }.body }
        assertEquals("초안이 출처를 담아요", "THERS234", RecipeCodec.readDraft(body).parent)
        val again = StudioViewModel(c)
        idle()
        again.resume(id)
        settle { again.state.value.draftId == id }
        assertEquals("이어서 하기도 그분 작품", "THERS234", again.state.value.parentCode)
    }

    /** STU-07: after 저장, 되돌리기 makes it not saved again (so leaving asks, and the 자동 저장 row stays). */
    @Test fun undoAfterSavingIsAChange() {
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(calTheme = "sun=FF1565C0") }
        vm.change { it.copy(calTheme = "sun=FF2E7D32") }
        vm.save("되돌리기 시험", toPlaza = false)
        settle { vm.state.value.kept }
        assertTrue(vm.state.value.kept)
        vm.undo()
        assertFalse("되돌린 뒤엔 저장된 그대로가 아니에요", vm.state.value.kept)
        vm.redo()
        assertFalse(vm.state.value.kept)
    }

    /** U09a-13: kept at once on the way out (not 1.2초 later), and after the app was closed under it, it opens by itself. */
    @Test fun keptAtOnceAndOpensAgainAfterTheAppWasClosed() {
        val vm = StudioViewModel(c)
        idle()
        vm.change { it.copy(calTheme = "sun=FF6A1B9A") }
        vm.keepNow()
        settle { vm.state.value.draftId != 0L }
        val id = vm.state.value.draftId
        assertTrue("바로 저장", id != 0L)
        // Android let the app go; the screen kept the draft's number (rememberSaveable) and a new model starts.
        val back = StudioViewModel(c)
        idle()
        assertTrue(back.cameBack(id))
        settle { back.state.value.draftId == id }
        assertEquals(id, back.state.value.draftId)
        assertTrue(back.state.value.draft.calTheme, back.state.value.draft.calTheme.contains("6A1B9A", ignoreCase = true))
        assertFalse("한 번만", back.cameBack(id))
    }
}
