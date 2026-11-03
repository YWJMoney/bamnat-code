package com.bamnat.app.logic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 1.36 [SEC-12] 보안 점검 M4·M5: what rules wrote is kept for '규칙이 쓴 것' and for share links to leave out. */
@RunWith(RobolectricTestRunner::class)
class LogicWrittenTest {
    @Test fun writesAreKeptAndMarked() {
        val store = LogicStore(RuntimeEnvironment.getApplication())
        store.clearWritten()
        store.wrote("월급날 저축", JSONObject().put("money", 7L).put("cal", "main").put("say", "가계부에 넣기"))
        store.wrote("하루 메모", JSONObject().put("memo", 41L).put("say", "메모 만들기"))
        store.wrote("지출 한 줄", JSONObject().put("line", 42L).put("body", "점심").put("text", "이달 지출 120,000원").put("say", "한 줄 더하기"))
        val all = store.written()
        assertEquals(listOf("월급날 저축", "하루 메모", "지출 한 줄"), all.map { it.getString("who") })
        assertNotNull(store.undo)
        assertTrue(store.madeMemo(41L))
        assertFalse(store.madeMemo(42L))
        assertEquals(setOf("이달 지출 120,000원"), store.madeLines(42L))
        store.forgetWritten(all.last())
        assertEquals(2, store.written().size)
        store.clearWritten()
        assertEquals(0, store.written().size)
    }

    @Test fun atMostSixtyAreKept() {
        val store = LogicStore(RuntimeEnvironment.getApplication())
        store.clearWritten()
        repeat(LogicStore.WRITTEN_MAX + 10) { store.wrote("r$it", JSONObject().put("money", it.toLong()).put("say", "x")) }
        assertEquals(LogicStore.WRITTEN_MAX, store.written().size)
        assertEquals("r69", store.written().last().getString("who"))
    }
}
