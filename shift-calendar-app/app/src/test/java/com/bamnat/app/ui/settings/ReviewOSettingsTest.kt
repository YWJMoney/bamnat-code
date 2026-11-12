package com.bamnat.app.ui.settings

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.AppLock
import com.bamnat.app.company.ForgetCounts
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.company.ForgetLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVO-16] 검토 SET-09: 앱 잠금은 휴대폰 잠금으로 확인한 뒤에 켜고 끄고, 보낸 정보 모두 지우기는 지난번 결과를 줄 아래에.
 * [RVO-17] 검토 SET-11: 백업 암호의 짧아요, 지워진 백업 파일.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewOSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")

    @After fun clean() {
        context.getSharedPreferences("forget_all", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
        AppLock.release()
    }

    @Test fun theForgetRowSaysWhenItWasLastAskedAndHowItWent() {
        assertNull("never asked: the row says what it does", ForgetLog.last(context))
        assertEquals("지웠어요", ForgetCounts(posts = 2, inquiries = 1).short())
        assertEquals("지웠어요", ForgetCounts(profile = true).short())
        assertEquals("서버에 지울 것이 없었어요", ForgetCounts(autoShareOff = true).short())
        assertEquals("공유 링크 1개는 연결되면 마저 지워요", ForgetCounts(links = 2, linksLeft = 1).short())

        Fmt.clock12 = false
        ForgetLog.record(context, ZonedDateTime.of(2026, 11, 12, 21, 30, 0, 0, zone).toInstant(), ForgetLog.OFFLINE)
        assertEquals("마지막 요청 11월 12일 21:30 · 인터넷이 없어 지우지 못했어요", ForgetLog.line(ForgetLog.last(context)!!, zone))
        ForgetLog.record(context, ZonedDateTime.of(2026, 11, 13, 8, 5, 0, 0, zone).toInstant(), ForgetCounts(posts = 1).short())
        assertEquals("the newest request wins", "마지막 요청 11월 13일 08:05 · 지웠어요", ForgetLog.line(ForgetLog.last(context)!!, zone))
    }

    /** 2.0.0 [RVO-17] 검토 SET-11·I11aa-05: 4~7자는 '짧아요'와 함께 되고, 8자부터 좋아요. */
    @Test fun aShortBackupPasswordIsAllowedWithAWord() {
        assertNull(passwordWords(""))
        assertEquals("4자 이상 넣어 주세요", passwordWords("abc"))
        assertEquals("짧아요 · 8자 이상이면 더 안전해요", passwordWords("night5"))
        assertEquals("좋아요", passwordWords("night-shift"))
        assertEquals(4, MIN_BACKUP_PASSWORD)
    }

    /** 2.0.0 [RVO-17] 검토 U11a-09: a backup whose file is gone is known before it is opened. */
    @Test fun aGoneBackupIsKnownBeforeOpening() {
        assertFalse(BackupPlace.exists(context, android.net.Uri.parse("content://com.example.gone/1")))
    }

    /** 2.0.0 [RVO-18] 검토 PRO-14·U11b-09: an open without an ad counts too, so the day's limit says two opens, not two ads watched. */
    @Test fun theDaysLimitSaysOpensNotAdsWatched() {
        assertTrue(com.bamnat.app.ads.CAPPED_WORDS, com.bamnat.app.ads.CAPPED_WORDS.contains("두 번 열었어요") && !com.bamnat.app.ads.CAPPED_WORDS.contains("봤"))
        assertEquals("광고가 늦어요. 조금만요", com.bamnat.app.ads.AD_LATE_WORDS)
        // PRO-02: the first quiet days start no ads SDK, so the button opens at once instead of waiting for it.
        val installed = java.time.Instant.parse("2026-11-10T00:00:00Z")
        assertFalse(com.bamnat.core.tier.AdRules.sdkMayStart(true, false, installed, installed.plus(java.time.Duration.ofDays(1)), 3))
        assertTrue(com.bamnat.core.tier.AdRules.sdkMayStart(true, false, installed, installed.plus(java.time.Duration.ofDays(4)), 3))
    }

    @Test fun withoutThePhonesLockTheSwitchDoesNotAsk() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var ok = 0
        var asked = 0
        AppLock.check(activity, "앱 잠금 켜기", onOk = { ok++ }, confirm = { asked++ })
        assertEquals("no lock on the phone: nothing to ask, on at once", 1, ok)
        assertEquals(0, asked)
    }

    /** Before Android 11 the phone's own lock screen is asked for, and the switch waits for its answer. */
    @Config(sdk = [29])
    @Test fun withThePhonesLockItAsksFirst() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        var ok = 0
        var asked: android.content.Intent? = null
        AppLock.check(activity, "앱 잠금 끄기", onOk = { ok++ }, confirm = { asked = it })
        assertEquals("not changed before the phone's lock says yes", 0, ok)
        assertTrue("the phone's lock screen is asked for: $asked", asked?.action == "android.app.action.CONFIRM_DEVICE_CREDENTIAL")
    }
}
