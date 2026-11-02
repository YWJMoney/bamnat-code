package com.bamnat.app.ui.studio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.ui.recipe.RecipeMakeViewModel
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.PieceKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** PC 확인 C7 1.36 (올리기 4/4에 '사진 빼고 올리기'가 없어요): a 꾸밈 사진 sent from 스튜디오 reaches 올리기 as a photo. */
@RunWith(AndroidJUnit4::class)
class DecorUploadTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun decorPhotoReachesTheCheckStep() {
        val vm = StudioViewModel(c)
        idle()
        val photo = DecorItem(DecorKind.PHOTO, "0123456789abcdef", "bg.calendar", w = 1000, h = 1000, full = true)
        vm.change { it.copy(decor = Decor(listOf(photo)).encode()) }
        idle()
        val (body, _) = vm.upload()
        val make = RecipeMakeViewModel(c, from = body)
        idle(); Thread.sleep(500); idle()
        val r = make.state.value.recipe
        assertTrue(r.pieces.any { it.kind == PieceKind.DECOR })
        assertTrue("사진 빼고 올리기", LookParts.hasPhotos(r))
    }

    /** PC 확인 C10 1.36 (기능별 탭으로 얹은 사진 작품은 사진이 안 와요): 얹기 puts the 꾸밈 사진 on the phone's 꾸밈 층 too. */
    @Test fun overlaidTabKeepsItsPhoto() {
        val photo = DecorItem(DecorKind.PHOTO, "0123456789abcdef", "bg.calendar", w = 1000, h = 1000, full = true)
        val r = com.bamnat.core.recipe.Recipe("탭", styles = com.bamnat.core.work.WorkStyle.entries.toSet(),
            pieces = listOf(com.bamnat.core.recipe.Piece(PieceKind.DECOR, Decor(listOf(photo)).values())))
        assertTrue(com.bamnat.core.recipe.LookParts.onlyTab(r) != null)
        val laid = kotlinx.coroutines.runBlocking { c.wardrobe.overlay(r, "X4CX2M2Z") }
        idle()
        assertTrue(laid)
        val on = Decor.decode(c.settingsRepository.current.decor)
        assertTrue(on.items.toString(), on.items.any { it.kind == DecorKind.PHOTO && it.glyph == "0123456789abcdef" })
    }
}
