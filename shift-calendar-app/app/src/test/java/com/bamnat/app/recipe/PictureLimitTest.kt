package com.bamnat.app.recipe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.company.CompanyException
import com.bamnat.app.ui.recipe.PictureLimit
import com.bamnat.app.ui.recipe.serverPictures
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [RVM-30] SN-15 (스튜디오 제작 오류 조사): a work whose pictures pass the day's 12 went up never — every 다시 올리기 sent them
 * all again and the words said 'N시간 뒤에 다시'. Now the limit met on the way says how many of how many went, and the second try
 * goes on: the server gives the same picture's id back without counting it (D #471 RVD-27), so the work goes up whole.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PictureLimitTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private lateinit var before: RecipeApi

    /** The server as D #471 made it: [perDay] new pictures a day, the same picture again is its id back and not counted. */
    private inner class Server(var perDay: Int) : RecipeApi by before {
        val kept = mutableMapOf<String, String>()
        var today = 0
        override suspend fun image(device: String, webp: ByteArray): String {
            val sum = webp.contentHashCode().toString()
            kept[sum]?.let { return it }
            if (today >= perDay) throw CompanyException("rate_limited", retryAfter = 20 * 3_600, why = "device", limit = 12)
            today++
            return "%016x".format(0xabc000L + kept.size).also { kept[sum] = it }
        }
    }

    @Before fun start() { before = c.recipeApi }
    @After fun end() { c.recipeApi = before }

    private fun picture(n: Int, slot: LookImages.Slot): String {
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(20 * n, 120, 200 - 10 * n))
        val f = File(c.appContext.cacheDir, "pick-$n.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return runBlocking { LookImages.import(c.appContext, Uri.fromFile(f), slot) }!!
    }

    @Test fun theDayLimitSaysHowFarItGotAndTheNextTryGoesOn() = runBlocking {
        val bg = picture(1, LookImages.Slot.BG)
        val head = picture(2, LookImages.Slot.HEAD)
        val widget = picture(3, LookImages.Slot.WIDGET)
        val work = Recipe("사진 셋", "", "", setOf(WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.PHOTO_LOOK, mapOf("bg" to bg, "head" to head, "widget" to widget))))
        val server = Server(perDay = 2)
        c.recipeApi = server
        val limit = try { serverPictures(c, work); fail("하루 두 장에서 멈춰요"); error("") } catch (e: PictureLimit) { e }
        assertEquals(listOf(2, 3, 12), listOf(limit.sent, limit.total, limit.limit))
        val said = limit.say()
        assertTrue(said, said.startsWith("오늘 올릴 수 있는 사진 12장을 다 썼어요. 사진 3장 중 2장은 올라갔고, "))
        assertTrue(said, said.endsWith("에 다시 올리면 남은 사진부터 이어서 올라가요"))
        assertTrue("'N시간 뒤에 다시'만 말하지 않아요: $said", "남은 사진부터" in said)
        // The next day: the two already up are not counted again, so the third goes and the work names the server's three.
        server.today = 0
        server.perDay = 1
        val out = serverPictures(c, work).pieces.single().values
        assertEquals(3, server.kept.size)
        assertEquals(server.kept.values.toSet(), setOf(out["bg"], out["head"], out["widget"]))
    }

    /** 스튜디오 '저장'(장터 판도 바꾸기) says the same, with its own verb. */
    @Test fun theStudioSaysSaveAgain() {
        val said = PictureLimit(5, 14, 12, 3_600).say("저장하면")
        assertTrue(said, said.contains("사진 14장 중 5장은 올라갔고, ") && said.endsWith("에 다시 저장하면 남은 사진부터 이어서 올라가요"))
    }
}
