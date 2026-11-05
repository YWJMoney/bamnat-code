package com.bamnat.app.g6

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.Inquiry
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.company.InquiryMessage
import com.bamnat.app.company.InquiryStore
import com.bamnat.app.company.compareVersion
import com.bamnat.app.data.GuideStore
import com.bamnat.app.share.MonthImage
import com.bamnat.core.memo.Memo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** 1.38 갈래6: the small rules behind 사진 보내기 [EASE-28], 고친 문의 감사 [EASE-32] and 출시일에 켜지는 새 기능 줄. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class Branch6Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun aMemosLeadingEmojiIsItsStickerAndTheWordsStay() {
        assertEquals("🎂", MonthImage.stickerOf("🎂 엄마 생일"))
        assertEquals("✈️", MonthImage.stickerOf("  ✈️제주"))
        assertNull(MonthImage.stickerOf("병원 예약"))
        assertNull(MonthImage.stickerOf(""))
        val m = YearMonth.of(2026, 11)
        val notes = MonthImage.notes(listOf(
            Memo(id = 1, date = LocalDate.of(2026, 11, 3), title = "🎂 엄마 생일"),
            Memo(id = 2, date = LocalDate.of(2026, 12, 3), title = "다음 달"),
            Memo(id = 3, date = LocalDate.of(2026, 11, 9), body = "\n치과 3시\n둘째 줄"),
        ), m)
        assertEquals(setOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)), notes.keys)
        assertEquals("🎂", notes[LocalDate.of(2026, 11, 3)]!!.sticker)
        assertEquals("치과 3시", notes[LocalDate.of(2026, 11, 9)]!!.memo)
        // The safe default: memo words off, stickers on.
        val look = MonthImage.Look()
        assertTrue(!look.memos && look.stickers && look.holidays && look.legend)
    }

    @Test fun onlyReleasedFixesUpToThisVersionAreThankedAndOnce() {
        assertTrue(compareVersion("1.37.0", "1.38.0") < 0)
        assertTrue(compareVersion("1.38.0", "1.38") == 0)
        assertTrue(compareVersion("1.38.1", "1.38.0") > 0)
        val store = InquiryStore(ctx)
        val at = Instant.parse("2026-11-01T00:00:00Z")
        fun ask(id: String, stage: String, version: String?, text: String) = Inquiry(id, InquiryKind.entries.first(), "answered", at, at,
            listOf(InquiryMessage(false, text, at), InquiryMessage(true, "고쳤어요", at)), stage = stage, version = version)
        val list = listOf(
            ask("a", "released", "1.37.0", "\n알람이 늦게 울려요\n자세히"),
            ask("b", "released", "1.39.0", "아직 안 나온 판"),
            ask("c", "building", "1.38.0", "만드는 중"),
            ask("d", "released", "1.38.0", "가".repeat(60)),
        )
        val got = store.fixedNow(list, "1.38.0")
        assertEquals(listOf("a", "d"), got.map { it.first })
        assertEquals("알람이 늦게 울려요", got[0].second)
        assertEquals(40, got[1].second.length)
        store.markThanked(got.map { it.first })
        assertEquals(emptyList<Pair<String, String>>(), store.fixedNow(list, "1.38.0"))
    }

    @Test fun aSwitchedLineIsToldOnceToAnUpdatedPhoneAndNeverToANewOne() {
        val key = com.bamnat.core.guide.WhatsNew.switched.first().first
        var on = false
        val store = GuideStore.get(ctx)
        // An updated phone, before the date: nothing, and later once.
        assertEquals(0, store.switchedNew({ on && it == key }, freshInstall = false).size)
        on = true
        assertEquals(1, store.switchedNew({ on && it == key }, freshInstall = false).size)
        store.switchedSeen(listOf(key))
        assertEquals(0, store.switchedNew({ on && it == key }, freshInstall = false).size)
    }
}
