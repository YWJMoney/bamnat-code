package com.bamnat.app.company

import android.net.http.NetworkException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.share.netWords
import com.bamnat.app.ui.components.NetWords
import com.bamnat.app.ui.diag.SpeedCheck
import com.bamnat.app.ui.setup.LinkStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * 2.0.0 [RVN-23] (PC-187, 높음): Google Play services' Cronet is gone (its module update killed the app on screen). The phone's own
 * HttpEngine from Android 14, the old connection before it or when the engine is not there, and the three words for a failure
 * (연결 없음 · 서버 바쁨 · 시간 넘김) the same whichever way the request went.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NetEngineTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    /** A failure as the phone's HttpEngine gives it. */
    private fun engineError(code: Int): NetworkException = object : NetworkException("net::ERR_$code", null) {
        override fun getErrorCode() = code
        override fun isImmediatelyRetryable() = false
    }

    @Test fun nothingOfPlayServicesCronetIsLeft() {
        assertFalse(File("build.gradle.kts").readText().contains("\"com.google.android.gms:play-services-cronet"))
        val users = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().let { it.contains("org.chromium.net") || it.contains("com.google.android.gms.net") } }.toList()
        assertEquals(emptyList<File>(), users)
    }

    @Test fun withoutTheEngineTheOldConnectionIsUsed() {
        NetEngine.start(app)
        assertFalse(NetEngine.fast)
        val conn = NetEngine.open(URL("http://127.0.0.1:9/api/hello"))
        assertTrue(conn is HttpURLConnection)
        assertFalse(conn.javaClass.name, conn.javaClass.name.contains("chromium", ignoreCase = true))
        assertFalse("before Android 14 there is none", NetEngine.available(33))
        assertTrue(NetEngine.available(34))
    }

    @Test fun aTimeRunningOutIsSaidAsSlowEitherWay() {
        assertTrue(NetEngine.timedOut(SocketTimeoutException("read timed out")))
        assertTrue(NetEngine.timedOut(engineError(NetworkException.ERROR_TIMED_OUT)))
        assertTrue(NetEngine.timedOut(engineError(NetworkException.ERROR_CONNECTION_TIMED_OUT)))
        assertFalse(NetEngine.timedOut(engineError(NetworkException.ERROR_HOSTNAME_NOT_RESOLVED)))
        assertFalse(NetEngine.timedOut(IOException("x")))
        assertEquals(setOf(NetworkException.ERROR_TIMED_OUT, NetworkException.ERROR_CONNECTION_TIMED_OUT), NetEngine.TIMED_OUT)
        // The three words, as the screens say them.
        assertTrue(netWords(engineError(NetworkException.ERROR_CONNECTION_TIMED_OUT), "못 받았어요.").endsWith(NetWords.SLOW))
        assertTrue(netWords(SocketTimeoutException(), "못 받았어요.").endsWith(NetWords.SLOW))
        assertTrue(netWords(engineError(NetworkException.ERROR_INTERNET_DISCONNECTED), "못 받았어요.").endsWith(NetWords.OFFLINE))
        assertTrue(netWords(IOException("x"), "못 받았어요.").endsWith(NetWords.OFFLINE))
        assertTrue(LinkStart.problem(engineError(NetworkException.ERROR_TIMED_OUT)).startsWith("근무표를 읽는 데 시간이 너무 걸렸어요"))
    }

    @Test fun whatNeverReachedTheServerMayGoToTheSpare() {
        listOf(NetworkException.ERROR_HOSTNAME_NOT_RESOLVED, NetworkException.ERROR_INTERNET_DISCONNECTED,
            NetworkException.ERROR_CONNECTION_REFUSED, NetworkException.ERROR_ADDRESS_UNREACHABLE).forEach {
            assertTrue("$it", ServerAddress.neverArrived(engineError(it)))
        }
        assertEquals(setOf(1, 2, 7, 9), NetEngine.NEVER_ARRIVED)
        // A request that may have arrived is never sent twice.
        listOf(NetworkException.ERROR_CONNECTION_RESET, NetworkException.ERROR_CONNECTION_CLOSED, NetworkException.ERROR_TIMED_OUT,
            NetworkException.ERROR_NETWORK_CHANGED).forEach {
            assertFalse("$it", ServerAddress.neverArrived(engineError(it)))
        }
    }

    /**
     * The other modules Google Play services loads into the app (DynamiteModule in the libraries, 2.0.0 총괄 ②): the ads SDK's
     * (com.google.android.gms.ads.dynamite, with its measurement module) and ML Kit's Korean text reading
     * (com.google.mlkit.dynamite.text.korean, only while a photo is read). Location, wearable, FCM, billing and Play's review,
     * update and integrity talk to another app instead and load nothing. A new Play services library is added here on purpose.
     */
    @Test fun theGoogleModulesInTheAppAreKnown() {
        val gms = Regex("\"com\\.google\\.android\\.gms:([a-z0-9-]+):").findAll(File("build.gradle.kts").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("play-services-ads", "play-services-mlkit-text-recognition-korean", "play-services-mlkit-document-scanner",
            "play-services-location", "play-services-wearable"), gms)
    }

    /** The ads module comes only with a press on an ad's button, never when the app opens. */
    @Test fun theAdsSdkStartsOnlyWithAPress() {
        val ads = File("src/main/java/com/bamnat/app/ads/Ads.kt").readText()
        val start = ads.substringAfter("fun start(activity: Activity)").substringBefore("fun prepare(")
        assertFalse(start, start.contains("startSdk(") || start.contains("MobileAds."))
        assertEquals(1, Regex("MobileAds\\.initialize\\(").findAll(ads).count())
        assertTrue(ads.substringAfter("private fun settled(").substringBefore("private fun startSdk(").contains("&& wanted"))
        assertTrue(ads.substringAfter("fun showPrivacyOptions(").substringBefore("private fun settled(").contains("if (wanted) startSdk("))
        val callers = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("ads.prepare(") }.map { it.name }.toList()
        assertEquals(listOf("RewardedAds.kt"), callers)
        val press = File("src/main/java/com/bamnat/app/ads/RewardedAds.kt").readText().substringAfter("OutlinedButton(\n            onClick = {")
        assertTrue(press.substringBefore("enabled = !loading").contains("c.ads.prepare(activity)"))
    }

    @Test fun theSelfCheckSaysWhichWay() {
        assertEquals("연결 방식: 휴대폰에 든 빠른 연결", SpeedCheck.way(fast = true, available = true))
        assertEquals("연결 방식: 기본 연결", SpeedCheck.way(fast = false, available = true))
        assertEquals("연결 방식: 기본 연결 · 빠른 연결은 안드로이드 14부터", SpeedCheck.way(fast = false, available = false))
    }
}
