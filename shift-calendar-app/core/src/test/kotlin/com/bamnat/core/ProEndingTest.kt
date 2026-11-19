package com.bamnat.core

import com.bamnat.core.tier.PlaySub
import com.bamnat.core.tier.ProEnding
import com.bamnat.core.tier.ProSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 2.0.0 [RVO-14] (검토 I11bb-02·PRO-29·U11b-10, 총괄 11-12): 프로가 저절로 끝나기 전날 저녁 한 번, 이 휴대폰이 쓰는 것만. */
class ProEndingTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val launch = Instant.parse("2026-11-11T15:17:42.082Z") // 11-12 00:17 KST
    private val now = Instant.parse("2026-12-01T03:00:00Z")
    private val installedBefore = Instant.parse("2026-11-01T00:00:00Z")
    private fun kst(text: String) = java.time.LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test fun theThanksMonthEndsOnItsDay() {
        val s = ProSources(billingLive = true, installedAt = installedBefore, launchedAt = launch, thanksMonths = 1)
        assertEquals(kst("2026-12-12T00:17:42.082"), ProEnding.end(s, now, zone))
        assertNull("a phone installed after the launch has nothing to end", ProEnding.end(s.copy(installedAt = launch.plusSeconds(60)), now, zone))
    }

    @Test fun oneProAfterAnotherEndsWhenNoneIsLeft() {
        val pass = kst("2026-12-20T09:00")
        val s = ProSources(billingLive = true, passUntil = pass, installedAt = installedBefore, launchedAt = launch, thanksMonths = 1)
        assertEquals("보상 이용권 runs past the 감사 기간", pass, ProEnding.end(s, now, zone))
        val early = kst("2026-12-05T09:00")
        assertEquals("보상 이용권 ends first, then the 감사 기간", kst("2026-12-12T00:17:42.082"), ProEnding.end(s.copy(passUntil = early), now, zone))
        // A 3.1 hold ends at the start of its day and the launched phone's own 프로 follows.
        val held = s.copy(billingLive = false, heldUntil = kst("2026-11-24T00:00"), passUntil = null)
        assertEquals(kst("2026-12-12T00:17:42.082"), ProEnding.end(held, kst("2026-11-20T12:00"), zone))
        assertEquals("a held phone with no 감사 기간 ends with the hold", kst("2026-11-24T00:00"),
            ProEnding.end(held.copy(installedAt = null), kst("2026-11-20T12:00"), zone))
    }

    @Test fun aProThatRenewsOrIsNotThePhonesOwnIsNotTold() {
        val sub = PlaySub(PlaySub.State.ACTIVE, expiry = kst("2026-12-20T09:00"), trial = false, autoRenew = true, purchasedAt = launch)
        assertNull(ProEnding.end(ProSources(billingLive = true, subscription = sub), now, zone))
        assertEquals("turned off on Play: it ends at its expiry", kst("2026-12-20T09:00"),
            ProEnding.end(ProSources(billingLive = true, subscription = sub.copy(autoRenew = false, verified = true)), now, zone))
        assertNull("PRO-04: an expiry the server has not checked is the phone's 3-day hold",
            ProEnding.end(ProSources(billingLive = true, subscription = sub.copy(autoRenew = false)), now, zone))
        assertNull("나눠 받은 프로 follows the buyer's subscription", ProEnding.end(ProSources(billingLive = true, sharedUntil = kst("2026-12-20T09:00")), now, zone))
        assertNull("하루 이용권 is the day the user chose", ProEnding.end(ProSources(billingLive = true, dayPassUntil = kst("2026-12-02T00:00")), now, zone))
        assertNull("결제가 열리지 않은 휴대폰", ProEnding.end(ProSources(billingLive = false), now, zone))
        assertNull("no 프로", ProEnding.end(ProSources(billingLive = true), now, zone))
    }

    @Test fun theNoticeComesTheEveningBefore() {
        val end = kst("2026-12-12T00:17:42.082")
        assertEquals("00:17 is the night before's: the evening of 12-10", kst("2026-12-10T20:00"), ProEnding.noticeAt(end, now, zone))
        assertEquals(kst("2026-12-19T20:00"), ProEnding.noticeAt(kst("2026-12-20T09:00"), now, zone))
        val late = kst("2026-12-11T23:00")
        assertEquals("opened after the evening: now", late, ProEnding.noticeAt(end, late, zone))
        assertNull("after the end, nothing", ProEnding.noticeAt(end, end, zone))
    }

    @Test fun itSaysOnlyWhatThisPhoneUsesAndWhatStays() {
        assertTrue(ProEnding.stops(ProEnding.Use()).isEmpty())
        val stops = ProEnding.stops(ProEnding.Use(phoneCalendar = true, autoBackup = true))
        assertEquals(listOf(ProEnding.Stop.PHONE_CALENDAR, ProEnding.Stop.AUTO_BACKUP), stops)
        val (title, text) = ProEnding.words(stops, kst("2026-12-12T00:17:42.082"), kst("2026-12-10T20:00"), zone)
        assertEquals("프로를 내일(12월 11일)까지 써요", title)
        assertTrue(text, text.contains("휴대폰 캘린더의 근무 일정을 지워요(앱 안 근무는 그대로예요)"))
        assertTrue(text, text.contains("다시 프로가 되면 다시 켜져요"))
        assertFalse("no mission when it is not set", text.contains("미션"))
        assertEquals("프로를 오늘(12월 11일)까지 써요", ProEnding.words(stops, kst("2026-12-12T00:17:42.082"), kst("2026-12-11T21:00"), zone).first)
        val (mTitle, mText) = ProEnding.missionWords(kst("2026-12-03T07:30"), kst("2026-12-02T20:00"), zone)
        assertEquals("끄기 미션이 내일(12월 3일) 오전 7:30에 끝나요", mTitle)
        assertTrue(mText, mText.contains("광고 한 편으로 7일 더"))
        assertEquals("오늘(12월 11일)", ProEnding.dayWords(LocalDate.of(2026, 12, 11), LocalDate.of(2026, 12, 11)))
        assertEquals("a pass ending in the afternoon names its time", "프로가 2일 뒤(12월 20일) 오후 3:05에 끝나요",
            ProEnding.words(stops, kst("2026-12-20T15:05"), kst("2026-12-18T20:00"), zone).first)
    }
}
