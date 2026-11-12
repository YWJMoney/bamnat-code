package com.bamnat.core

import com.bamnat.core.memo.ChecklistCodec
import com.bamnat.core.memo.ChecklistEdit
import com.bamnat.core.memo.ChecklistItem
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 [RVIB-02] (검토 I07ab-03) 하위 할 일과 우선순위: a line's star, time and note. */
class ChecklistStarTest {
    @Test fun aLineKeepsItsStarTimeAndNote() {
        val items = listOf(
            ChecklistItem("약 받기", starred = true, time = LocalTime.of(15, 0), note = "처방전\n챙기기"),
            ChecklistItem("장보기\u001F우유", checked = true, starred = true),
            ChecklistItem("택배", note = "경비실 \\ 앞"),
            ChecklistItem("그냥 줄"),
        )
        val back = ChecklistCodec.decode(ChecklistCodec.encode(items))
        assertEquals(items, back)
        // A list written before 2.0.0 reads as it did.
        assertEquals(listOf(ChecklistItem("유니폼", true), ChecklistItem("명찰", false)), ChecklistCodec.decode("1유니폼\n0명찰"))
        // A line with nothing new is stored as before, so an older copy of the app still reads it.
        assertEquals("0그냥 줄", ChecklistCodec.encode(listOf(ChecklistItem("그냥 줄"))))
    }

    @Test fun aStarredLineGoesUpAfterTheStarsBeforeIt() {
        val list = listOf(ChecklistItem("가", checked = true), ChecklistItem("나", starred = true), ChecklistItem("다"), ChecklistItem("라"))
        assertEquals(listOf("가", "나", "라", "다"), ChecklistEdit.star(list, 3, true).map { it.text })
        assertEquals(true, ChecklistEdit.star(list, 3, true)[2].starred)
        // Unstarred, a line stays where it is.
        assertEquals(listOf("가", "나", "다", "라"), ChecklistEdit.star(list, 1, false).map { it.text })
        assertEquals(false, ChecklistEdit.star(list, 1, false)[1].starred)
        // With no star yet, it goes before the first open line.
        val plain = listOf(ChecklistItem("가", checked = true), ChecklistItem("나"), ChecklistItem("다"))
        assertEquals(listOf("가", "다", "나"), ChecklistEdit.star(plain, 2, true).map { it.text })
    }

    @Test fun asWordsNothingIsLost() {
        val list = listOf(ChecklistItem("약 받기", starred = true, time = LocalTime.of(15, 0), note = "처방전"), ChecklistItem("끝난 것", checked = true))
        val words = ChecklistEdit.toText(list)
        assertEquals("★ 약 받기 · 15:00 · 처방전\n☑ 끝난 것", words)
        val again = ChecklistEdit.fromText(words)
        assertEquals(true, again[0].starred)
        assertEquals("약 받기 · 15:00 · 처방전", again[0].text)
        assertEquals(true, again[1].checked)
        assertEquals("처방전 챙기기", ChecklistEdit.note(list, 0, "처방전\n챙기기")[0].note)
        assertEquals(null, ChecklistEdit.time(list, 0, null)[0].time)
    }
}
