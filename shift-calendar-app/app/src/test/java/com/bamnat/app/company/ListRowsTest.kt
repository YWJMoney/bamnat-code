package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVN-13] 검토 COMP-49: one row of a server list the app cannot read (a newer server's field of another kind, a time it cannot
 * parse, a row that is not an object) is left out, and the rest of 회사 화면·규칙 광장·토론·내 글 still shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ListRowsTest {
    @Test fun aCompanyPageKeepsItsOtherSetupsWhenOneCannotBeRead() {
        val page = companyPage(JSONObject("""{"id":"c1","name":"가나다화학","setups":[
            {"id":"s1","status":"adopted","setup":{"v":1}},
            {"id":"s2","status":"pending","setup":"not an object"},
            "a row that is not one",
            {"id":"s3","status":"pending","setup":{"v":1},"version":"two"}
        ]}"""))
        assertEquals(listOf("s1"), page.setups.map { it.id })
    }

    @Test fun theBoardAndATalkLeaveOutOnlyTheRowsTheyCannotRead() {
        val board = boardOf(JSONObject("""{"items":[
            {"id":"s1","company":{"id":"c1","name":"가"},"at":"2026-11-12T00:00:00Z"},
            {"id":"s2","company":{"id":"c2","name":"나"},"at":"어제"},
            {"id":"s3","company":{"id":"c3"}}
        ],"next":20}"""))
        assertEquals(listOf("s1"), board.items.map { it.setup })
        assertEquals(20, board.next)
        val talk = talkOf(JSONObject("""{"posts":[
            {"id":"p1","body":"안녕하세요","at":"2026-11-12T00:00:00Z"},
            {"body":"id가 없어요","at":"2026-11-12T00:00:00Z"},
            {"id":"p3","body":"시간이 이상해요","at":"곧"},
            {"id":"p4","body":"잘 읽혀요","at":"2026-11-12T01:00:00Z"}
        ]}"""))
        assertEquals(listOf("p1", "p4"), talk.posts.map { it.id })
    }

    @Test fun myPostsAndVillageSearchLeaveOutOnlyTheBrokenRow() {
        val mine = myPostsOf(JSONObject("""{"items":[
            {"id":"m1","room":"SHIFT","body":"하나","at":"2026-11-12T00:00:00Z"},
            {"id":"m2","room":"SHIFT","body":"둘","at":42},
            {"id":"m3","room":"SHIFT","body":"셋","at":"2026-11-12T02:00:00Z"}
        ]}"""))
        assertEquals(listOf("m1", "m3"), mine.map { it.id })
        val found = villageFoundOf(JSONObject("""{"posts":[{"id":"f1","at":"2026-11-12T00:00:00Z"},{"id":"f2"}],
            "requests":[{"id":"r1","body":"부탁"},{"body":"id 없음"}]}"""))
        assertEquals(listOf("f1"), found.posts.map { it.id })
        assertEquals(listOf("r1"), found.requests.map { it.id })
    }
}
