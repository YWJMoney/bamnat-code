package com.bamnat.app.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.calendar.partNames
import com.bamnat.app.ui.calendar.showsText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.39 G5-N4 계획한 잠 시간 in a link: the receiver reads only sound pairs and says one line. */
@RunWith(AndroidJUnit4::class)
class SleepPlanLinkTest {
    @Test fun onlySoundPairsAreRead() {
        val h = 3_600_000L
        val from = 1_800_000_000_000L
        val o = JSONObject("""{"title":"민지","types":[],"days":[],"sleepPlan":[
            {"from":$from,"to":${from + 7 * h}},
            {"from":${from + 20 * h},"to":${from + 19 * h}},
            {"from":${from + 30 * h},"to":${from + 50 * h}},
            {"from":${from + 40 * h},"to":${from + 47 * h},"where":"x"}]}""")
        val s = SharedSchedule.parse(o)
        assertEquals(listOf(SleepPlanWindow(from, from + 7 * h), SleepPlanWindow(from + 40 * h, from + 47 * h)), s.sleepPlan)
        // No plan in an older link: nothing said.
        assertTrue(SharedSchedule.parse(JSONObject("""{"title":"","types":[],"days":[]}""")).sleepPlan.isEmpty())
        assertEquals(null, com.bamnat.app.ui.calendar.sleepPlanLine(emptyList()))
    }

    /** PC c0ca3d9f: the sheet's '보내는 것' and the receiver's '함께 받은 것' name the sleep rows that went. */
    @Test fun bothSidesNameTheSleepRows() {
        assertEquals(listOf("계획한 잠 시간"), ShareOptions(sleepPlan = true).partNames())
        assertEquals(listOf("지금 자는 중(잘 때만)"), ShareOptions(sleepNow = true).partNames())
        val from = 1_800_000_000_000L
        val s = SharedSchedule.parse(JSONObject("""{"title":"","types":[],"days":[],"sleepPlan":[{"from":$from,"to":${from + 3_600_000}}]}"""))
        assertEquals("계획한 잠 시간", s.showsText())
        assertEquals(null, SharedSchedule.parse(JSONObject("""{"title":"","types":[],"days":[]}""")).showsText())
    }
}
