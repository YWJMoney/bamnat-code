package com.bamnat.app.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.36 [SEC-04] 보안 점검 M1: a picture's size decides before it is opened. */
class SafeBitmapsTest {
    @Test fun ordinaryPicturesOpenWhole() {
        assertEquals(1, SafeBitmaps.sampleFor(512, 512))
        assertEquals(1, SafeBitmaps.sampleFor(511, 1080))
    }

    @Test fun largePhotosOpenSmaller() {
        assertEquals(2, SafeBitmaps.sampleFor(4000, 3000))
        assertEquals(4, SafeBitmaps.sampleFor(8000, 6000))
    }

    @Test fun aPictureNoAppMakesIsRefused() {
        assertNull("16383 × 16383: about a gigabyte", SafeBitmaps.sampleFor(16383, 16383))
        assertNull(SafeBitmaps.sampleFor(0, 10))
        assertNull(SafeBitmaps.sampleFor(-1, -1))
    }

    @Test fun aPickedPhotoIsNeverRefusedOnlyMadeSmaller() {
        assertEquals(8, SafeBitmaps.sampleFor(16383, 16383, refuseOver = Int.MAX_VALUE))
    }
}
