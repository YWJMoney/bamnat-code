package com.bamnat.app.company

import com.bamnat.core.plaza.CareStanding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVN-22] 검토 I10bc-01: the 마을 돌봄 standing last read is shown at once and asked again only after five minutes, kept
 * for the one server it came from (another test's fake never sees it).
 */
class CareKeptTest {
    private val api = CompanyClient("http://127.0.0.1:9")
    private val other = CompanyClient("http://127.0.0.1:9")

    @After fun forget() = CareKept.clear()

    @Test fun aStandingIsFreshForFiveMinutesForItsOwnServer() {
        val s = CareStanding(limitAppeal = "none")
        CareKept.keep(api, s, now = 1_000L)
        assertEquals(s, CareKept.of(api))
        assertTrue(CareKept.fresh(api, now = 1_000L + CareKept.FRESH_MS - 1))
        assertFalse("five minutes on, asked again", CareKept.fresh(api, now = 1_000L + CareKept.FRESH_MS))
        assertFalse("a clock set back asks again", CareKept.fresh(api, now = 999L))
        assertNull("another server's is not shown", CareKept.of(other))
        assertFalse(CareKept.fresh(other, now = 1_000L))
        CareKept.clear()
        assertNull(CareKept.of(api))
    }
}
