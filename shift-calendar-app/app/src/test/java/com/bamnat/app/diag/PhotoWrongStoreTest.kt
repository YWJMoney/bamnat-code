package com.bamnat.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bamnat.app.company.CompanyException
import com.bamnat.core.stats.PhotoScreen
import com.bamnat.core.stats.PhotoWrongReason
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [PHOTO-09] 1.29 AQ: '사진이 달라요' presses wait on the phone, go only when allowed, and carry counts only. */
@RunWith(RobolectricTestRunner::class)
class PhotoWrongStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun store(post: (String, JSONObject) -> Unit) = PhotoWrongStore(context, baseUrl = "https://example.test", post = post).also {
        context.getSharedPreferences("photo_wrong", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun pressesWaitUntilAllowedAndCarryCountsOnly() = runBlocking {
        val sent = mutableListOf<JSONObject>()
        val s = store { _, body -> sent += body }
        s.record(PhotoScreen.PAYSLIP, PhotoWrongReason.NUMBERS)
        s.record(PhotoScreen.PAYSLIP, PhotoWrongReason.NUMBERS)
        s.record(PhotoScreen.RULES, PhotoWrongReason.MISSING)
        assertEquals(3, s.waiting)
        // Not allowed yet (처리방침 3.3): nothing goes, the presses stay.
        assertTrue(s.flush(allowed = false))
        assertEquals(0, sent.size)
        assertEquals(3, s.waiting)
        assertTrue(s.flush(allowed = true))
        assertEquals(1, sent.size)
        assertEquals(0, s.waiting)
        val body = sent.single().toString()
        // Counts by day, screen and reason with the app's version: no text, no picture.
        assertTrue(body.contains("payslip") && body.contains("rules"))
        assertFalse(body.contains("image") || body.contains("photo\":") || body.contains("text\""))
    }

    @Test fun offlinePressesStayForLater() = runBlocking {
        var tries = 0
        val s = store { _, _ -> tries++; throw CompanyException("offline") }
        s.record(PhotoScreen.ROSTER, PhotoWrongReason.NAMES)
        assertFalse(s.flush(allowed = true))
        assertEquals(1, tries)
        assertEquals(1, s.waiting)
    }
}
