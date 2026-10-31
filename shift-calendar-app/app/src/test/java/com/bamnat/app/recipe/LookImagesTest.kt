package com.bamnat.app.recipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.recipe.LookImages.Slot
import com.bamnat.core.recipe.PhotoLook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * [J135-05] F-61: a photo picked for each of the four places, framed and kept, is a file under files/looks the look can
 * point at, at the place's shape.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LookImagesTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun photo(w: Int, h: Int): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(40, 120, 200))
        val f = File(app.cacheDir, "pick-$w-$h.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    @Test fun eachPlaceKeepsTheFramedPhoto() = runBlocking {
        val uri = photo(3000, 4000)
        for (slot in Slot.entries) {
            val id = LookImages.import(app, uri, slot, RectF(0.1f, 0.1f, 0.9f, 0.6f))
            assertNotNull("$slot", id)
            assertTrue(PhotoLook.isId(id))
            val f = LookImages.file(app, id!!)
            assertTrue("$slot file", f.isFile)
            val b = BitmapFactory.decodeFile(f.path)
            assertNotNull(b)
            assertTrue(f.length() <= LookImages.MAX_BYTES)
            val look = PhotoLook.decode(PhotoLook(bg = id).encode())
            assertEquals(id, look.bg)
        }
    }

    @Test fun theMiddleWhenNoFrame() = runBlocking {
        val id = LookImages.import(app, photo(800, 600), Slot.BG)
        assertNotNull(id)
        assertTrue(LookImages.has(app, id))
    }
}
