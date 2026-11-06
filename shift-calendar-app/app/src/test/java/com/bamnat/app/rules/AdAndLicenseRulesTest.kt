package com.bamnat.app.rules

import com.bamnat.app.ui.settings.OPEN_SOURCE_NOTICES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [REL-02][MON-07] The ad rules held by a test: 1.36 838 no banner at all, a rewarded ad only when the user asks
 * for it, never at launch, full screen on its own, as native ads or on the alarm. Both follow [com.bamnat.core.tier.AdRules].
 * [OPS-23] Every library in the build has an entry in 오픈소스 라이선스.
 */
class AdAndLicenseRulesTest {
    // Gradle runs unit tests in the module directory.
    private val main = File("src/main")
    private val sources by lazy { main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList() }
    private fun read(path: String) = File(main, "java/com/bamnat/app/$path").readText()

    @Test fun noAdFormatOtherThanTheAskedForReward() {
        val banned = Regex("""\b(InterstitialAd|RewardedInterstitialAd|AppOpenAd|NativeAd|AdManagerInterstitialAd|AdView|AdSize)\b""")
        val hits = sources.filter { banned.containsMatchIn(it.readText()) }.map { it.path }
        assertEquals("띠·전면·앱 열기·네이티브 광고는 쓰지 않아요", emptyList<String>(), hits)
        val rewarded = sources.filter { Regex("""\bRewardedAd\b""").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("보상형 광고는 고를 때 누르는 광고 단추(RewardedAds.kt)에서만", listOf("RewardedAds.kt"), rewarded)
    }

    @Test fun thereIsNoBanner() {
        assertFalse("띠 광고 자리는 없어요", File(main, "java/com/bamnat/app/ui/components/AdSlot.kt").exists())
        assertFalse(sources.any { Regex("""AdSlot\(""").containsMatchIn(it.readText()) })
        assertFalse(File("build.gradle.kts").readText().contains("ADMOB_BANNER"))
        assertFalse("1.38 E6: 띠 광고 단위 값도 남기지 않아요", File("../gradle.properties").readText().contains("admobBanner"))
        val alarmScreens = File(main, "java/com/bamnat/app/alarm").walkTopDown().filter { it.isFile }
        assertTrue("알람 화면에는 광고가 없어요", alarmScreens.none { it.readText().contains("com.google.android.gms.ads") })
    }

    /** 1.38 출시 점검 E1: a debug build asks only for Google's test unit, whatever gradle.properties holds (AdMob 무효 트래픽). */
    @Test fun aDebugBuildUsesTheTestUnit() {
        assertEquals(com.bamnat.app.BuildConfig.DEBUG, com.bamnat.app.ads.AdIds.rewarded == "ca-app-pub-3940256099942544/5224354917")
        val ads = File(main, "java/com/bamnat/app/ads/Ads.kt").readText()
        assertTrue(ads.contains("if (BuildConfig.DEBUG) TEST_REWARDED else BuildConfig.ADMOB_REWARDED"))
    }

    @Test fun theAdsSdkIsOnlyInItsOwnPlaces() {
        val users = sources.filter { it.extension == "kt" && it.readText().contains("com.google.android.gms.ads") }.map { it.name }.sorted()
        assertEquals(listOf("Ads.kt", "RewardedAds.kt"), users)
    }

    @Test fun adsFollowTheRules() {
        assertTrue("보상형은 AdRules.rewardedOffer를 따라요", read("ads/RewardedAds.kt").contains("AdRules.rewardedOffer("))
        // [FLAG-09][FLAG-18] Nothing while the `ads` row is off (it replaced the 정식 출시 switch), and nothing for 프로:
        // the SDK and consent start only then.
        val activity = read("MainActivity.kt").lines()
        val at = activity.indexOfFirst { it.contains("ads.start(") }
        val start = activity[at - 1] + activity[at]
        assertTrue(start, start.contains("AdRules.sdkMayStart(") && start.contains("FlagCatalog.ADS") && start.contains("Feature.NO_ADS"))
        // The rewarded ad reads the row, never the old switch.
        for (f in listOf("ads/RewardedAds.kt")) {
            assertTrue(f, read(f).contains("FlagCatalog.ADS") && !read(f).contains("billingLive"))
        }
        // The row waits for the billing conditions (3.1 시행·보류·Play 승인·정기 결제) and Google's consent path.
        val ads = com.bamnat.core.flags.FlagCatalog.ADS_FLAG
        assertEquals(com.bamnat.core.flags.Risk.LEGAL, ads.risk)
        assertFalse(ads.default)
        assertTrue(ads.conditions.containsAll(com.bamnat.core.flags.FlagCatalog.BILLING_FLAG.conditions))
        assertTrue(ads.conditions.any { it is com.bamnat.core.flags.Condition.Consent && it.key == "ads" })
        // [SRV-07] Nor in the first three days after install: the consent form waits for the first possible ad.
        assertTrue(start, start.contains("installedAt") && start.contains("adQuietDays"))
        val installed = java.time.Instant.parse("2027-03-01T00:00:00Z")
        assertFalse(com.bamnat.core.tier.AdRules.sdkMayStart(true, false, installed, installed.plusSeconds(86_400L * 3 - 1)))
        assertTrue(com.bamnat.core.tier.AdRules.sdkMayStart(true, false, installed, installed.plusSeconds(86_400L * 3)))
        assertFalse(com.bamnat.core.tier.AdRules.sdkMayStart(false, false, installed, installed.plusSeconds(86_400L * 30)))
        assertFalse(com.bamnat.core.tier.AdRules.sdkMayStart(true, true, installed, installed.plusSeconds(86_400L * 30)))
        // The SDK's own measurement waits for MobileAds.initialize, so nothing goes before the switch.
        assertTrue(File(main, "AndroidManifest.xml").readText().contains("com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT"))
        // Google's test units are for debug builds only; a release without ids has no ads at all.
        val ids = read("ads/Ads.kt")
        assertTrue(ids.contains("ifBlank { if (BuildConfig.DEBUG) TEST_REWARDED else \"\" }"))
        // Such a release leaves out the advertising ID permissions the SDK brings (no 광고 ID declaration needed).
        val noAdId = File("src/noAdId/AndroidManifest.xml").readText()
        for (p in listOf("com.google.android.gms.permission.AD_ID", "android.permission.ACCESS_ADSERVICES_AD_ID")) {
            assertTrue(p, noAdId.contains("<uses-permission android:name=\"$p\" tools:node=\"remove\" />"))
        }
        assertTrue(File("build.gradle.kts").readText()
            .contains("if (!adUnitsGiven) variant.sources.manifests.addStaticManifestFile(\"src/noAdId/AndroidManifest.xml\")"))
    }

    @Test fun everyBundledLibraryIsInTheNotices() {
        val build = File("build.gradle.kts").readText() + File("../core/build.gradle.kts").readText()
        val groups = Regex("""implementation\("([a-z0-9.\-]+):""").findAll(build).map { it.groupValues[1] }
            .filter { it != "com.bamnat" }.toSet()
        val covered = mapOf(
            "androidx.compose.ui" to "Jetpack Compose", "androidx.compose.foundation" to "Jetpack Compose",
            "androidx.compose.material3" to "Jetpack Compose", "androidx.compose.material" to "Jetpack Compose",
            "androidx.compose" to "Jetpack Compose",
            "androidx.core" to "AndroidX Core", "androidx.activity" to "AndroidX Core", "androidx.lifecycle" to "AndroidX Core",
            "androidx.navigation" to "AndroidX Core", "androidx.profileinstaller" to "AndroidX Core", "androidx.window" to "AndroidX Core",
            "androidx.work" to "AndroidX Core", // [NXT-01] 1.29 AU: WorkManager named directly (before, only through the ads SDK)
            "androidx.glance" to "Jetpack Glance", "androidx.room" to "AndroidX Room",
            "androidx.health.connect" to "Health Connect", "org.jetbrains.kotlinx" to "Kotlin",
            "com.google.mlkit" to "ML Kit", "com.google.android.play" to "Google Play",
            "com.google.android.gms" to "Google Play 서비스",
            "com.android.billingclient" to "Google Play 결제 라이브러리",
            "com.google.android.ump" to "Google 사용자 메시지 플랫폼",
            "com.android.installreferrer" to "Google Play 설치 리퍼러",
            "com.google.firebase" to "Firebase 클라우드 메시징", // 1.36 [PUSH-01]
        )
        for (g in groups) {
            val name = covered[g] ?: error("오픈소스 라이선스 목록에 '$g'을(를) 더해 주세요 (OpenSourceLicenses.kt)")
            assertTrue("$g → $name", OPEN_SOURCE_NOTICES.any { it.name.startsWith(name) })
        }
    }
}
