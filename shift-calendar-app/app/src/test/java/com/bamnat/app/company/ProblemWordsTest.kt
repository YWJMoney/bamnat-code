package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.company.companyProblem
import com.bamnat.app.ui.components.NetWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVN-13] 검토 COMP-52: no server word becomes "서버에 문제가 생겼어요" any more. A busy server says to wait (the app's one 바쁨
 * line), what the user can fix says how, and a word this app does not know says the update mends it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProblemWordsTest {
    @Test fun eachWordSaysWhatToDo() {
        val busy = NetWords.BUSY.removeSuffix(".")
        listOf("server", "busy", "no_database", "http_500", "http_503").forEach { assertEquals(it, busy, companyProblem(it)) }
        assertTrue(companyProblem("bad_response").contains("와이파이 로그인"))
        assertEquals(companyProblem("not_found"), companyProblem("http_404"))
        assertEquals(companyProblem("too_large"), companyProblem("http_413"))
        assertEquals("내가 올린 것에는 할 수 없어요", companyProblem("own"))
        assertTrue(companyProblem("taken").contains("마을 이름"))
        listOf("http_400", "http_418", "a_word_from_2_1", "kind", "room").forEach { word ->
            assertTrue(word, companyProblem(word).contains("업데이트"))
        }
        listOf("server", "http_400", "bad_response", "own", "anything").forEach { assertFalse(it, companyProblem(it).contains("서버에 문제")) }
        // The refusals that carried their own words keep them.
        assertTrue(companyProblem(CompanyException("rate_limited", null, 120, "device")).contains("2분쯤 뒤"))
    }
}
