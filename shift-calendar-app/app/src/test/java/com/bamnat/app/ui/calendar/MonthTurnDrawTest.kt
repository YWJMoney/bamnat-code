package com.bamnat.app.ui.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.theme.monthTurn
import com.bamnat.app.ui.theme.monthTurnShift
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [PC-28] The other half of [MonthTurnTapTest]: with the finger no longer following the turn, the turn must still look as it
 * did. A quarter of the way to the next month, 스르륵 shows both months in the middle (one fading over the other), 움직이지 않기 only the
 * month being left, 밀기 the two side by side, and 겹쳐 오기 keeps the coming month out until later.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class MonthTurnDrawTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val here = Color(0xFFFF0000)
    private val next = Color(0xFF0000FF)
    private val before = Color(0xFF00FF00)

    private class Rgb(val r: Int, val g: Int, val b: Int) {
        fun near(o: Rgb) = abs(r - o.r) < 12 && abs(g - o.g) < 12 && abs(b - o.b) < 12
        override fun toString() = "($r, $g, $b)"
    }

    /** The colours at the left, the middle and the right of the pager, a quarter of the way from this month to the next. */
    private fun quarterTurned(turn: String?): Triple<Rgb, Rgb, Rgb> {
        var pager: PagerState? = null
        lateinit var seen: Triple<Rgb, Rgb, Rgb>
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent {
                    val state = rememberPagerState(initialPage = 5) { 10 }
                    pager = state
                    Box(Modifier.fillMaxWidth().height(200.dp).background(Color.White)) {
                        HorizontalPager(state, Modifier.fillMaxSize().testTag("months"), beyondViewportPageCount = 1, key = { it }) { page ->
                            Box(Modifier.monthTurn(turn) { (state.currentPage - page) + state.currentPageOffsetFraction }.fillMaxSize()
                                .background(when (page) { 5 -> here; 6 -> next; else -> before }))
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.runOnUiThread { runBlocking { pager!!.scrollToPage(5, pageOffsetFraction = 0.25f) } }
            compose.waitForIdle()
            val box = compose.onNodeWithTag("months").fetchSemanticsNode().boundsInWindow
            s.onActivity { a ->
                val view = a.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bmp))
                fun at(across: Float): Rgb {
                    val c = bmp.getPixel((box.left + box.width * across).toInt(), box.center.y.toInt())
                    return Rgb(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
                }
                seen = Triple(at(0.1f), at(0.5f), at(0.9f))
            }
        }
        return seen
    }

    @Test fun eachTurnStillLooksLikeItself() {
        val red = Rgb(255, 0, 0); val blue = Rgb(0, 0, 255); val white = Rgb(255, 255, 255)
        // 밀기: the pager's own, this month sliding out to the left, the next coming in at the right.
        quarterTurned(null).let { (l, m, r) -> assertTrue("밀기 $l $m $r", l.near(red) && m.near(red) && r.near(blue)) }
        // 스르륵: both in the middle, this month three quarters there under a quarter of the next — the same colour edge to edge.
        quarterTurned("fade").let { (l, m, r) ->
            assertTrue("스르륵은 양쪽 끝이 같은 색 $l $m $r", l.near(m) && m.near(r))
            assertTrue("스르륵은 두 달이 섞여 보여요 $m", m.r in 150..230 && m.b in 60..150 && m.g < 90)
        }
        // 움직이지 않기: only the month being left until halfway, in the middle.
        quarterTurned("none").let { (l, m, r) -> assertTrue("움직이지 않기 $l $m $r", l.near(red) && m.near(red) && r.near(red)) }
        // 책장 넘기기: this month turning about its middle, the next not yet seen.
        quarterTurned("flip").let { (l, m, r) -> assertTrue("책장 넘기기 $l $m $r", m.near(red) && !r.near(blue) && !l.near(blue)) }
        // 겹쳐 오기: this month slides away, the next still outside at a quarter (it comes from twice as far).
        quarterTurned("stack").let { (l, m, r) -> assertTrue("겹쳐 오기 $l $m $r", l.near(red) && m.near(red) && r.near(white)) }
    }

    @Test fun onlyThreeTurnsUndoThePagersSlide() {
        for (o in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
            for (t in listOf("fade", "flip", "none")) assertEquals(o, monthTurnShift(t, o), 0f)
            for (t in listOf(null, "slide", "zoom")) assertEquals(0f, monthTurnShift(t, o), 0f)
        }
        assertEquals(0.75f, monthTurnShift("stack", -0.75f), 0f)
        assertEquals(0f, monthTurnShift("stack", 0.75f), 0f)
    }
}
