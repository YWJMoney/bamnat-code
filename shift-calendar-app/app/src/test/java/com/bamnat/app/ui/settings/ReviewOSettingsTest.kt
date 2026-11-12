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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** 2.0.0 [RVO-16] 검토 SET-09: 앱 잠금은 휴대폰 잠금으로 확인한 뒤에 켜고 끄고, 보낸 정보 모두 지우기는 지난번 결과를 줄 아래에. */
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
