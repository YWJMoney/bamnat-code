package com.bamnat.app.nxt

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Tier
import com.bamnat.core.flags.ProFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * [NXT-01] 1.29 AU (AO D8): the two start-up providers the libraries bring (ML Kit's MlKitInitProvider, and the ads
 * SDK's WorkManager initializer through androidx.startup) are out of the merged manifest; WorkManager still starts when
 * asked (BamnatApp is its Configuration.Provider, [NXT-11] with a job id range away from the app's own jobs), and every
 * place that reads a photo with ML Kit starts ML Kit first. [NXT-03] The 프로 feature names are core's one table.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NxtStartupProvidersTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @Test fun mlKitsProviderIsNotInTheMergedManifest() {
        @Suppress("DEPRECATION")
        val providers = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertTrue(providers.map { it.name }.toString(), providers.none { it.name == "com.google.mlkit.common.internal.MlKitInitProvider" })
    }

    @Test fun workManagersInitializerIsNotStartedByAndroidxStartup() {
        @Suppress("DEPRECATION")
        val startup = runCatching {
            app.packageManager.getProviderInfo(ComponentName(app, "androidx.startup.InitializationProvider"), PackageManager.GET_META_DATA)
        }.getOrNull()
        // The provider stays for the other libraries' initializers; WorkManager's line is gone from it.
        assertFalse(startup?.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true)
    }

    @Test fun workManagerStartsOnFirstUseWithItsOwnJobIds() {
        val config = (app as androidx.work.Configuration.Provider).workManagerConfiguration
        assertEquals(100_000, config.minJobSchedulerId)
        assertEquals(199_999, config.maxJobSchedulerId)
        // The app's own JobService ids (AutoBackupJob, PlazaNewsJob, OutboxJob) are outside that range.
        val main = File("src/main/java/com/bamnat/app")
        val ids = main.walkTopDown().filter { it.isFile && it.name.endsWith("Job.kt") }
            .flatMap { Regex("""const val \w*ID = (\d+)""").findAll(it.readText()).map { m -> m.groupValues[1].toInt() } }.toList()
        assertTrue(ids.toString(), ids.size >= 4 && ids.none { it in config.minJobSchedulerId..config.maxJobSchedulerId })
        // Without the androidx.startup line, the first getInstance builds it from the Configuration.Provider.
        assertNotNull(androidx.work.WorkManager.getInstance(app))
    }

    @Test fun everyPlaceThatReadsAPhotoWithMlKitStartsItFirst() {
        val users = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> USES.any { f.readText().contains(it) } }.toList()
        assertTrue("OcrModel and DocScanner use ML Kit", users.map { it.name }.containsAll(listOf("OcrModel.kt", "DocScanner.kt")))
        val missing = users.filterNot { it.readText().contains("MlKitStart.ensure(") }.map { it.path }
        assertEquals("ML Kit을 쓰는 곳은 MlKitStart.ensure를 먼저 불러요 (MlKitInitProvider를 뺐어요)", emptyList<String>(), missing)
        // The manifest keeps the removal, with the reason beside it.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("com.google.mlkit.common.internal.MlKitInitProvider") && manifest.contains("tools:node=\"remove\""))
    }

    @Test fun proFeatureNamesAreCoresTable() {
        val pro = Feature.entries.filter { it.minTier == Tier.PRO }
        assertEquals(ProFeatures.NAMES.keys.toList(), pro.map { it.name })
        for (f in pro) assertEquals(ProFeatures.NAMES.getValue(f.name), f.label)
        assertEquals("팀 근무표 만들기", Feature.TEAM_ROSTER.label)
    }

    private companion object {
        /** ML Kit entry points that need its context: the text recognizer and the document scanner. */
        val USES = listOf("TextRecognition.getClient", "GmsDocumentScanning.getClient", "com.google.mlkit.vision.barcode",
            "com.google.mlkit.vision.face", "com.google.mlkit.vision.label")
    }
}
