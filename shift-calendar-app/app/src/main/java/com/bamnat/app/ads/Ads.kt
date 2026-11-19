package com.bamnat.app.ads

import android.app.Activity
import android.content.Context
import com.bamnat.app.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [MON-07] The AdMob ad unit id from gradle.properties (bamnat.admobRewarded; a public value the owner gives). Blank in a
 * release build means no ads at all. A debug build always uses Google's own test unit, even when gradle.properties holds the
 * real one (1.38 출시 점검 E1: a debug build on a test phone must never ask for or click a real ad, AdMob 무효 트래픽). 1.36 838 (사용자 2026-10-31 "띠 광고는
 * 없애는게 좋을거 같아"): there is no banner; the only ad is the rewarded one the user chooses.
 */
object AdIds {
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    val rewarded: String = if (BuildConfig.DEBUG) TEST_REWARDED else BuildConfig.ADMOB_REWARDED
    val configured: Boolean get() = rewarded.isNotBlank()
}

/**
 * [MON-07] Google's consent (UMP) and the ads SDK, started only when an ad may show at all: [FLAG-09] with the `ads` row on, for a phone without 프로, in a build with ad ids. Where the law asks (EEA, UK and
 * the like) Google's own form asks first; 설정 › 개인정보·앱 정보 › 광고 개인정보 선택 opens it again.
 *
 * 2.0.0 [RVN-23] (PC-187): the consent form still settles when the app comes to the front ([start]), but the ads SDK itself starts
 * only when someone presses an ad's button ([prepare]). The SDK runs from a module Google Play services loads into the app
 * (com.google.android.gms.ads.dynamite, the same kind as the Cronet module whose update killed the app on screen): a phone that
 * never asks for an ad never carries it, so a ringing alarm or a night's sleep record is not tied to it.
 */
class Ads(private val scope: CoroutineScope) {
    private val _ready = MutableStateFlow(false)
    /** Consent is settled and the SDK is up: ads may be asked for. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _privacyOptions = MutableStateFlow(false)
    /** Google says this phone's region needs a way back to the consent form. */
    val privacyOptions: StateFlow<Boolean> = _privacyOptions.asStateFlow()
    private val _consent = MutableStateFlow<Boolean?>(null)
    /**
     * [FLAG-09] Google's consent as the form left it: null before it was asked on this run, false when ads may not be
     * requested. The `ads` row's consent condition (AL-D5): not asked yet counts, so the form can be shown.
     */
    val consent: StateFlow<Boolean?> = _consent.asStateFlow()
    @Volatile private var asked = false
    @Volatile private var started = false
    /** 2.0.0 [RVN-23] An ad's button was pressed on this run: the SDK may start as soon as consent allows. */
    @Volatile private var wanted = false

    /** Once per run, from the activity coming to the front: Google's consent only, not the SDK ([prepare]). */
    fun start(activity: Activity) {
        if (asked || !AdIds.configured) return
        asked = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { settled(activity, info) }
        }, { settled(activity, info) })
        // A choice made on an earlier run counts at once.
        if (info.canRequestAds()) _consent.value = true
    }

    /**
     * 2.0.0 [RVN-23] An ad's button was pressed: start the SDK now if consent allows (or as soon as the form settles), and
     * [ready] says when an ad may be asked for. Before this, nothing of the ads SDK is in the app.
     */
    fun prepare(context: Context) {
        wanted = true
        if (UserMessagingPlatform.getConsentInformation(context).canRequestAds()) startSdk(context.applicationContext)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            _consent.value = info.canRequestAds()
            if (!info.canRequestAds()) _ready.value = false else if (wanted) startSdk(activity.applicationContext)
        }
    }

    private fun settled(context: Context, info: ConsentInformation) {
        _privacyOptions.value = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _consent.value = info.canRequestAds()
        if (info.canRequestAds() && wanted) startSdk(context.applicationContext)
    }

    private fun startSdk(context: Context) {
        if (started) return
        started = true
        // Google asks for this off the main thread.
        // G 1.32 [WTYP-01] Every ad starts without sound (AdRules.MUTED).
        scope.launch {
            // [ADAGE-01] Play's audience is 13 and over (사용자 2026-11-04 13:07): no ad above teen content, kept with whatever else
            // the configuration already says (test devices and the like).
            runCatching { MobileAds.setRequestConfiguration(AdContent.teenSafe(MobileAds.getRequestConfiguration())) }
            MobileAds.initialize(context) {
                if (com.bamnat.core.tier.AdRules.MUTED) runCatching { MobileAds.setAppMuted(true); MobileAds.setAppVolume(0f) }
                _ready.value = true
            }
        }
    }
}

/** [ADAGE-01] 1.37: the ad content ceiling for an app whose Play audience includes 13~17. */
object AdContent {
    const val MAX_RATING: String = RequestConfiguration.MAX_AD_CONTENT_RATING_T

    /** [current] with its rating capped at T; everything else in it is kept. */
    fun teenSafe(current: RequestConfiguration): RequestConfiguration =
        current.toBuilder().setMaxAdContentRating(MAX_RATING).build()
}
