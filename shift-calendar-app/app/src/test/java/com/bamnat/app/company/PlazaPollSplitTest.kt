package com.bamnat.app.company

import com.bamnat.core.pay.WorkForm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.39 [PLZC-31] 근무형태별 투표 결과 as the server sends them; [PLZC-21] 오늘 출근 in the head line. */
@RunWith(RobolectricTestRunner::class)
class PlazaPollSplitTest {
    @Test
    fun `PLZC-31 groups by 근무형태, others together, an unknown form left out`() {
        val p = pollOf(JSONObject("""{"options":["바로 자요","좀 있다 자요"],"counts":[5,5],"mine":0,
            "byForm":[{"f":"FOUR_TEAM","counts":[4,1]},{"f":"MOON_SHIFT","counts":[3,3]},{"f":null,"counts":[1,4]}]}"""))
        assertEquals(listOf(WorkForm.FOUR_TEAM, null), p.byForm.map { it.form })
        assertEquals(0 to 80, p.byForm[0].lead())
        assertEquals(1 to 80, p.byForm[1].lead())
        assertEquals(emptyList<PollSplit>(), pollOf(JSONObject("""{"options":["a","b"],"counts":[0,0]}""")).byForm)
        assertNull(PollSplit(null, listOf(0, 0)).lead())
    }

    @Test
    fun `PLZC-21 today's taps and mine`() {
        val c = checkinOf(JSONObject("""{"counts":{"day":1,"night":3},"mine":"night"}"""))!!
        assertEquals(mapOf("day" to 1, "night" to 3), c.counts)
        assertEquals("night", c.mine)
        assertNull(checkinOf(JSONObject("""{"counts":{"go":0,"rain":0}}"""))!!.mine)
        assertNull(checkinOf(JSONObject("{}")))
    }
}
