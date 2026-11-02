package com.bamnat.app.company

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.TimeZone

/** 1.36 [LIM-10] 신고 누적 쓰기 제한: nothing is said without a restriction; with one, until when and how many today. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WriteLimitTest {
    @Test fun saidOnlyWhileRestricted() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            assertNull(WriteLimit.of(JSONObject("""{"posts":[]}""")))
            val l = WriteLimit.of(JSONObject("""{"limit":{"until":"2026-11-08T03:00:00Z","perDay":5,"left":3}}"""))!!
            assertEquals(WriteLimit(Instant.parse("2026-11-08T03:00:00Z"), 5), l)
            assertEquals("신고가 쌓여 11월 8일까지 하루 5번만 쓸 수 있어요 · 오늘 3번 더", l.line(3))
            assertEquals("신고가 쌓여 11월 8일까지 하루 5번만 쓸 수 있어요. 오늘은 다 썼어요", l.line(0))
            // PC 확인 보안 6차 (운영자가 가렸는데 '신고가 쌓여'): who hid what, as the server says.
            val op = WriteLimit.of(JSONObject("""{"limit":{"until":"2026-11-08T03:00:00Z","perDay":5,"by":"operator","n":2,"kinds":["recipe"]}}"""))!!
            assertEquals("운영자가 작품 2개를 가려 11월 8일까지 하루 5번만 쓸 수 있어요", op.line(null))
            val both = WriteLimit.of(JSONObject("""{"limit":{"until":"2026-11-08T03:00:00Z","perDay":2,"by":"both","n":3,"kinds":["post","recipe"]}}"""))!!
            assertEquals("신고와 운영자 확인으로 글·작품 3개가 가려져 11월 8일까지 하루 2번만 쓸 수 있어요", both.line(null))
            assertEquals("운영자가 글 2개를 가려", WriteLimit(l.until, 5, "operator", 2, listOf("post", "talk")).cause())
            assertEquals("운영자가 프로필을 가려", WriteLimit(l.until, 5, "operator", 2, listOf("bio", "profile")).cause())
            assertEquals("신고가 쌓여", WriteLimit(l.until, 5, "reports", 2, listOf("post")).cause())
        } finally {
            TimeZone.setDefault(zone)
        }
    }
}
