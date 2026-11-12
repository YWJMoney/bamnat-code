package com.bamnat.app.imports

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.bamnat.app.ui.imports.mineOf
import com.bamnat.app.ui.imports.photoPlace
import com.bamnat.app.ui.imports.photoTap
import com.bamnat.core.imports.RosterPhotoRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 2.0.0 [RVL-35] (검토 SCAN-19·U07b-03) The photo made bigger with two fingers, ⊕ or a double tap: where it may be moved to, and a
 * tap on it read in the photo's own pixels. [RVL-36] (검토 SCAN-25) the user's line on the photo found by the whole name.
 */
class RosterPhotoPlaceTest {
    private val box = IntSize(400, 600)

    @Test fun aPhotoShorterThanTheBoxStaysInTheMiddle() {
        // 1000 × 500 shown 400 wide is 200 high: centred in the 600, whatever the finger did.
        assertEquals(Offset(0f, 200f), photoPlace(Offset(50f, -90f), 1f, box, 1000, 500))
        // Twice as big (800 × 400): it moves sideways up to its edges, and still sits in the middle up and down.
        assertEquals(Offset(-400f, 100f), photoPlace(Offset(-900f, 0f), 2f, box, 1000, 500))
        assertEquals(Offset(0f, 100f), photoPlace(Offset(30f, 0f), 2f, box, 1000, 500))
    }

    @Test fun aTallPhotoMovesUpAndDownToItsEdges() {
        // 1000 × 2000 at 400 wide is 800 high: it goes from 0 up to -200, no further.
        assertEquals(Offset(0f, -200f), photoPlace(Offset(0f, -500f), 1f, box, 1000, 2000))
        assertEquals(Offset(0f, 0f), photoPlace(Offset(0f, 40f), 1f, box, 1000, 2000))
    }

    @Test fun aTapIsReadInThePhotosPixels() {
        // Shown at 0.4 of the picture, words read at 2× it, made 3× bigger and moved 120 left: (180, 90) on screen is
        // (180 + 120, 90) ÷ 3 ÷ 0.4 × 2 in the words' pixels.
        val fit = 0.4f
        assertEquals(500 to 150, photoTap(Offset(180f, 90f), Offset(-120f, 0f), 3f, fit, 2f))
        // At 1× nothing moved: the same as before zooming.
        assertEquals(1000 to 500, photoTap(Offset(200f, 100f), Offset.Zero, 1f, fit, 2f))
    }

    private fun line(text: String, top: Int) = RosterPhotoRead.Line(text, RosterPhotoRead.Kind.DUTIES, 0, top, 900, top + 40)

    @Test fun myLineIsTheOneWithMyWholeName() {
        val lines = listOf(line("김간호 D D E E N", 100), line("김간 N N O O D", 150), line("박수진 O D D E E", 200))
        // '김간' found '김간호' above before: the whole name now.
        assertSame(lines[1], mineOf(lines, "김간", 0))
        assertSame(lines[0], mineOf(lines, "김간호", 1))
        // A name read as words apart.
        val apart = listOf(line("이 수 진 D E", 100), line("박 하늘 N N", 150))
        assertSame(apart[1], mineOf(apart, "박하늘", 0))
        // A twin's "(2)" is the second line of that name; no name found is the rank, as before.
        val twins = listOf(line("김민지 D D", 100), line("박수진 E E", 150), line("김민지 N N", 200))
        assertSame(twins[2], mineOf(twins, "김민지 (2)", 0))
        assertSame(twins[0], mineOf(twins, "김민지", 2))
        assertSame(twins[1], mineOf(twins, "최유리", 1))
        assertSame(twins[2], mineOf(twins, "", 2))
    }
}
