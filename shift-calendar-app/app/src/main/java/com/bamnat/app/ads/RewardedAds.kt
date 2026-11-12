package com.bamnat.app.ads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bamnat.core.tier.FeatureLease
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.bamnat.core.time.Today

/**
 * [MON-13] 1.36 839 묶음 7일권 (사용자 2026-10-31 수익 2판 B안): the rewarded ad as one of the ways in the 프로 sheet and lock.
 * [feature]'s bundle ([FeatureLease.Bundle]) opens for 7 days after one ad watched to the end, and also when the ad does not come
 * (no fill, too slow, offline, the switch off): nobody is kept from a feature by an ad. Two ads a day in all; a bundle with days
 * left shows its days instead; three 'no's hide its ad for 30 days. An always-on 프로 feature (no bundle) shows nothing here.
 * In the alarm check's 등급 미리 보기 no ad is ever loaded: the button shows, cannot be pressed and says why.
 */
@Composable
fun LeaseAdOption(feature: com.bamnat.app.tier.Feature?, gate: Gate, modifier: Modifier = Modifier, onOpened: () -> Unit = {}) {
    if (feature == null || gate.tier >= Tier.PRO) return
    val small = MaterialTheme.typography.labelSmall
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val bundle = gate.bundle(feature) ?: run {
        // 1.36 PC B1: say why there is no ad, rather than leave a way out of the sheet missing.
        Text("‘${feature.label}’${com.bamnat.core.text.Josa.eunneun(feature.label)} 광고로 열 수 없어요. 프로에서 늘 써요.", style = small, color = dim, textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth())
        return
    }
    val label = "광고 보고 ${bundle.label} 7일 열기"
    val note = "광고 한 편을 끝까지 보면 7일 열려요(${bundle.gives}). 광고가 안 와도 그냥 열려요. 하루 두 편까지예요(밤 12시에 다시 세요)."
    if (gate.preview) {
        Column(modifier) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(label) }
            Text("$note 미리 보기에서는 광고가 나오지 않아요.", style = small, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    val c = LocalContext.current.appContainer
    val now = Today.instant()
    val today = Today.date()
    val leases = gate.leases
    val left = leases.daysLeft(bundle, now)
    // 1.36 PC 재검토 (근무표 PDF를 처음 열었는데 '처음 7일 무료' 대신 결제 창): the bundle's free first week, first and on its own,
    // before any ad. One tap starts it; the toast says until when, and the feature is open on the next tap.
    if (gate.canTry(feature)) {
        val context = LocalContext.current
        Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            com.bamnat.app.ui.components.Button(onClick = {
                if (com.bamnat.app.ui.pro.tryFeature(feature)) {
                    val end = Today.instant().plus(java.time.Duration.ofDays(FeatureLease.FIRST_DAYS))
                    // Short: it sat over the card it had just opened (PC).
                    android.widget.Toast.makeText(context, "${bundle.label} 처음 7일 무료로 열었어요 · ${com.bamnat.app.ui.pro.untilWords(end)}까지",
                        android.widget.Toast.LENGTH_SHORT).show()
                    onOpened()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("처음 7일 무료로 열기") }
            Text("${bundle.label} 묶음(${bundle.gives})을 처음 쓰는 날부터 7일 광고 없이 써요. 그 뒤에는 광고 한 편으로 7일씩 열려요.",
                style = small, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    when {
        left != null -> Text("${bundle.label} 묶음이 열려 있어요 · ${left}일 남았어요", style = small, color = dim, textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth())
        // 1.36.1 (사용자 2026-11-04 "광고 제한에 걸리면 기능도 다음날 사용가능하게, 00시 기준"): closing the sheet now is not a 'no'
        // (ProOfferHost counts one only while the ad can be watched), and the button comes back at 00:00 (FeatureGate's clock).
        FeatureLease.capped(leases, today) -> Text("오늘 광고 두 편을 다 봤어요. 밤 12시가 지나면 다시 광고로 열 수 있어요.", style = small, color = dim,
            textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
        !FeatureLease.canWatch(leases, bundle, now, today) -> Text(
            "세 번 닫으셔서 ${bundle.label} 광고는 쉬고 있어요" + (leases.quietUntil[bundle]?.let { " · ${com.bamnat.app.ui.pro.untilWords(it)}부터 다시 보여요" } ?: ""),
            style = small, color = dim, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
        else -> {
            val context = LocalContext.current
            // 1.36 PC Y14 (광고가 안 와서 그냥 열렸을 때 아무 말이 없어요): every way it opens says so in one line, with the day it ends.
            fun opened(why: FeatureLease.Why, reason: String?, lead: String) {
                val from = leases.until[bundle]?.takeIf { Today.instant() < it } ?: Today.instant()
                c.featureGate.grant(bundle, why, reason)
                val end = from.plus(java.time.Duration.ofDays(FeatureLease.AD_DAYS))
                val until = com.bamnat.app.ui.pro.untilWords(end)
                android.widget.Toast.makeText(context, if (lead.isEmpty()) "${bundle.label} 7일 열렸어요 · ${until}까지" else "$lead · ${bundle.label} ${until}까지",
                    android.widget.Toast.LENGTH_LONG).show()
                onOpened()
            }
            ThemeAdButton(label, note,
                onReward = { opened(FeatureLease.Why.AD, null, "") },
                onQuit = {},
                onOffline = { opened(FeatureLease.Why.NO_AD, "offline", "인터넷이 없어 광고 없이 열었어요") },
                onNoAd = { why -> opened(FeatureLease.Why.NO_AD, why, "광고가 오지 않아 그냥 열었어요") },
                modifier = modifier,
                // The bundle counts its own grant (FeatureLease.granted) and says 'two today' above.
                shared = false)
        }
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
    modifier: Modifier = Modifier,
    /**
     * 1.36.1 광고 하루 두 편 (약관 "잠긴 기능이나 테마에서 고를 때만 … 하루 두 편까지"): a theme's ad shares the bundles' two a day.
     * Past them it is not offered until tomorrow, and one watched to the end is counted. The bundles' own button passes false.
     */
    shared: Boolean = true,
    /** When today's two are used: a visible way to leave the ask for tomorrow (the 30-day 연장 dialog), or null. */
    onCappedLater: (() -> Unit)? = null) {
    val c = LocalContext.current.appContainer
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    // The `ads` row (FlagCatalog.ADS) under the same rule as the day pass; the wardrobe never asks for an ad when it is off.
    val adsRow = rows[com.bamnat.core.flags.FlagCatalog.ADS]?.on == true
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val allowed = AdRules.rewardedOffer(adsRow, gate.tier >= Tier.PRO, AdIds.rewarded.isNotBlank(), null, Today.date())
    if (shared && adsRow && gate.tier < Tier.PRO && FeatureLease.capped(gate.leases, Today.date())) {
        Column(modifier) {
            Text("오늘 광고 두 편을 다 봤어요. 밤 12시가 지나면 다시 할 수 있어요. 프로는 광고 없이 바로 돼요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            onCappedLater?.let { later ->
                androidx.compose.material3.TextButton(onClick = later, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("내일 다시 물어보기") }
            }
        }
        return
    }
    val rewarded: () -> Unit = { if (shared) c.featureGate.countThemeAd(); onReward() }

    fun play(activity: android.app.Activity) {
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
                        override fun onAdDismissedFullScreenContent() { com.bamnat.app.review.InAppReview.hush(); if (earned) rewarded() else onQuit() }
                        override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { onNoAd("show_failed") }
                    }
                    com.bamnat.app.review.InAppReview.hush() // 1.39 [SPREAD-06] no review sheet right after an ad
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
    }

    Column(modifier) {
        OutlinedButton(
            onClick = {
                val activity = context.findActivity()
                if (!adsRow) { onNoAd("flag_off"); return@OutlinedButton }
                if (!allowed || activity == null || c.ads.consent.value == false) { onNoAd("not_ready"); return@OutlinedButton }
                loading = true
                if (c.ads.ready.value) { play(activity); return@OutlinedButton }
                // 1.35 [J135-18] Tapped right after the app opened, the ads SDK may still be starting: wait for it (as long as a slow ad
                // may take) instead of giving the days with no ad at once. Only a phone that may not have ads at all skips the wait.
                scope.launch {
                    val up = withTimeoutOrNull(AD_WAIT_MS) { c.ads.ready.first { it } } != null
                    if (up) play(activity) else { loading = false; onNoAd("not_ready") }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (loading) "광고를 준비하는 중…" else label) }
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
    }
}

/** [FREE-10] How long an ad may take to come before it counts as 시간 초과. */
private const val AD_WAIT_MS = 15_000L
