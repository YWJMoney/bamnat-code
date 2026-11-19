package com.bamnat.app.company

import com.bamnat.app.ui.settings.HelpFaq
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2.0.0 [RVN-24] 검토 I22cb-02: a 답 모음 pick carries what it is about, and 도움말 on that subject finds it. The subjects are the
 * server's (PICK_TOPICS in plaza-picks.js), and every tag the map reads is one a 도움말 answer has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlazaPickTopicsTest {
    @Test fun aPickKeepsAKnownSubjectOnly() {
        val got = plazaPicksOf(JSONObject("""{"items":[
            {"id":"k1","title":"알람이 늦을 때","question":"알람이 늦어요","answer":"배터리 최적화를 꺼 보세요","topic":"alarm"},
            {"id":"k2","title":"모르는 주제","question":"?","answer":"답","topic":"weather"},
            {"id":"k3","title":"주제 없음","question":"?","answer":"답"}]}"""))
        assertEquals(listOf("alarm", null, null), got.map { it.topic })
    }

    @Test fun helpAnswersFindTheirSubject() {
        assertEquals("alarm", PlazaPickTopics.of("알람"))
        assertEquals("pay", PlazaPickTopics.of("급여"))
        assertEquals("leave", PlazaPickTopics.of("연차"))
        assertEquals("sleep", PlazaPickTopics.of("수면"))
        assertEquals("roster", PlazaPickTopics.of("근무 주기"))
        assertEquals("money", PlazaPickTopics.of("가계부"))
        assertEquals(null, PlazaPickTopics.of("백업"))
        // 끄는 법 is about turning features off, not alarms.
        assertEquals(null, PlazaPickTopics.of("끄는 법"))
        val tags = HelpFaq.topics().map { it.tag }.toSet()
        val mapped = listOf("알람", "급여", "추가 근무", "실수령", "선택근무", "공무원 급여", "공수", "연차", "희망 오프",
            "수면", "근무 주기", "한 달 근무", "연속 근무", "근무표 점검", "가계부", "돈", "영수증", "연말정산", "소득 종류")
        assertTrue("every mapped tag is a 도움말 tag: ${mapped - tags}", (mapped - tags).isEmpty())
        assertTrue(mapped.all { PlazaPickTopics.of(it) != null })
    }

    @Test fun theSubjectsAreTheServers() {
        val js = File("../server/company-worker/src/plaza-picks.js").readText()
        val line = Regex("""PICK_TOPICS = \{([^}]*)\}""").find(js)!!.groupValues[1]
        val keys = Regex("""(\w+):""").findAll(line).map { it.groupValues[1] }.toSet()
        assertEquals(keys, PlazaPickTopics.KEYS)
    }
}
