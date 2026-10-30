package com.bamnat.app.ads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.review.InAppReview.findActivity
import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.Tier
import com.bamnat.core.tier.AdRules
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.time.LocalDate
import com.bamnat.core.time.Today

/**
 * AG 1.27 [MON-12] The rewarded ad as one of the ways in the 프로 sheet and lock: [DayPassButton] under its own rules, or,
 * in the alarm check's 등급 미리 보기 (no ad is ever loaded there), the same button that cannot be pressed and says why.
 */
@Composable
fun DayPassOption(gate: Gate, modifier: Modifier = Modifier) {
    if (gate.preview && gate.tier < Tier.PRO) {
        Column(modifier) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("광고 보고 오늘 하루 열기") }
            Text(
                "광고 한 편을 끝까지 보면 오늘 밤 12시까지 프로 기능이 열려요. 하루 한 번이에요. 미리 보기에서는 광고가 나오지 않아요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    DayPassButton(gate, modifier)
}

/**
 * [MON-07] The one rewarded ad (결정 1: D), and only when the user taps for it: what it gives is said
 * before it plays (AdMob 보상형 정책). Watched to the end, the pro features open until midnight, once
 * a day. This is the only file that may use a rewarded ad (AdAndLicenseRulesTest).
 */
@Composable
fun DayPassButton(gate: Gate, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val config by c.remoteConfig.config.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val ready by c.ads.ready.collectAsStateWithLifecycle()
    // [FLAG-09] The `ads` row in place of the 정식 출시 switch.
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val today = Today.date()
    val offer = ready && AdIds.rewarded.isNotBlank() && !gate.preview &&
        AdRules.rewardedOffer(rows[com.bamnat.core.flags.FlagCatalog.ADS]?.on == true, gate.tier >= Tier.PRO, true, pro.dayPass, today)
    if (!offer) return
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(
            onClick = {
                val activity = context.findActivity() ?: return@OutlinedButton
                loading = true
                failed = false
                RewardedAd.load(activity, AdIds.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        loading = false
                        ad.show(activity) {
                            c.proStore.dayPass(Today.date())
                            c.featureGate.refresh()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                        failed = true
                    }
                })
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "광고를 불러오는 중…" else "광고 보고 오늘 하루 열기") }
        Text(
            if (failed) "광고를 불러오지 못했어요. 잠시 뒤 다시 해 주세요."
            else "광고 한 편을 끝까지 보면 오늘 밤 12시까지 프로 기능이 열려요. 하루 한 번이에요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * [FREE-10] 1.35 테마 연장·입기 광고 (사용자 2026-10-30 16:00 "보상은 광고를 온전히 다 볼 때만"): what it gives is said on the button
 * before it plays. Only the SDK's reward callback calls [onReward]; closed before the end, the app left or the ad not finished calls
 * [onQuit] and gives nothing. No internet calls [onOffline] (the ask waits, no days given). No ad after one more try, too slow, or
 * failing to show calls [onNoAd] with the reason ([com.bamnat.core.recipe.ThemeLease.REASONS]), which the wardrobe counts.
 * Shown only under AdRules.rewardedOffer's switch and never for 프로 (the wardrobe asks nothing of them).
 */
@Composable
fun ThemeAdButton(label: String, note: String, onReward: () -> Unit, onQuit: () -> Unit, onOffline: () -> Unit, onNoAd: (String) -> Unit,
    modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    // The `ads` row (FlagCatalog.ADS) under the same rule as the day pass; the wardrobe never asks for an ad when it is off.
    val adsRow = rows[com.bamnat.core.flags.FlagCatalog.ADS]?.on == true
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val allowed = AdRules.rewardedOffer(adsRow, gate.tier >= Tier.PRO, AdIds.rewarded.isNotBlank(), null, Today.date())
    Column(modifier) {
        OutlinedButton(
            onClick = {
                val activity = context.findActivity()
                if (!adsRow) { onNoAd("flag_off"); return@OutlinedButton }
                if (!allowed || activity == null || !c.ads.ready.value) { onNoAd("not_ready"); return@OutlinedButton }
                loading = true
                var tries = 0
                var done = false
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val slow = Runnable { if (!done) { done = true; loading = false; onNoAd("timeout") } }
                handler.postDelayed(slow, AD_WAIT_MS)
                fun load() {
                    tries++
                    RewardedAd.load(activity, AdIds.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            if (done) return
                            done = true
                            handler.removeCallbacks(slow)
                            loading = false
                            var earned = false
                            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() { if (earned) onReward() else onQuit() }
                                override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { onNoAd("show_failed") }
                            }
                            ad.show(activity) { earned = true }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (done) return
                            when (error.code) {
                                AdRequest.ERROR_CODE_NETWORK_ERROR -> { done = true; handler.removeCallbacks(slow); loading = false; onOffline() }
                                AdRequest.ERROR_CODE_NO_FILL, AdRequest.ERROR_CODE_MEDIATION_NO_FILL ->
                                    if (tries < 2) load() else { done = true; handler.removeCallbacks(slow); loading = false; onNoAd("no_fill") }
                                else -> { done = true; handler.removeCallbacks(slow); loading = false; onNoAd("other") }
                            }
                        }
                    })
                }
                load()
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "광고를 불러오는 중…" else label) }
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
    }
}

/** [FREE-10] How long an ad may take to come before it counts as 시간 초과. */
private const val AD_WAIT_MS = 15_000L
