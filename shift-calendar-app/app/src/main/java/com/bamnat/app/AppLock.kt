package com.bamnat.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [LOCK-01] 앱 잠금: with it on, the app opens behind a cover until the phone's own lock (fingerprint,
 * face, PIN or pattern) is passed, on a cold start and after [AWAY_MILLIS] away. Alarms, the widget
 * and notices are not covered: an alarm must ring whoever holds the phone.
 */
object AppLock {
    /** Away this long (a call, a look at a message is shorter) and the app locks again. */
    const val AWAY_MILLIS = 60_000L

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * 1.39.1 [R391-10] (검토 UIX-41) Bumped when a 창 or 시트 opens while locked: each is a window of its own over the activity,
     * so the cover (a window too, [com.bamnat.app.ui.LockCover]) is put back on top of it.
     */
    private val _opened = MutableStateFlow(0)
    val opened: StateFlow<Int> = _opened.asStateFlow()

    /** A 창·시트 window was just added; only matters while locked. */
    fun windowOpened() {
        if (_locked.value) _opened.value++
    }

    /** For tests: the cover as after a minute away. */
    internal fun lockNow() {
        _locked.value = true
    }

    private var stoppedAt = 0L
    private var started = false

    /** The phone has a lock of its own; without one there is nothing to ask, so the switch is not offered. */
    fun available(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * 2.0.0 [RVL-09] (검토 DATA-11) Away long enough while a screen the app opened (a file picker, a permission page) was in front: the
     * lock waits only for its result to come in ([onResume]), it no longer stays open however long the phone was left.
     */
    private var lockAfterResult = false

    fun onStart(context: Context, on: Boolean, awaitingResult: Boolean) {
        val away = stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= AWAY_MILLIS
        if (on && available(context) && (!started || away && !awaitingResult)) _locked.value = true
        lockAfterResult = on && available(context) && started && away && awaitingResult
        if (!on) _locked.value = false
        started = true
        stoppedAt = 0L
    }

    /** 2.0.0 [RVL-09] Android hands the screen's result in before this: now the app locks as it would have on coming back. */
    fun onResume() {
        if (lockAfterResult) _locked.value = true
        lockAfterResult = false
    }

    /**
     * 2.0.0 [RVL-09] (검토 DATA-11·U12-08) 앱 잠금 is on but the phone has no lock of its own any more (it was taken off in the phone's
     * settings): there is nothing to ask, so the app opens as it is. Said over the calendar instead of in silence.
     */
    fun resting(context: Context, on: Boolean): Boolean = on && !available(context)

    fun onStop(changingConfigurations: Boolean) {
        if (!changingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    /**
     * [SRV-08] P9: while 앱 잠금 is on the window is FLAG_SECURE, so the recents screen, screenshots and
     * screen sharing show nothing of the calendar, pay or memos.
     */
    fun secure(window: android.view.Window, on: Boolean) {
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * 2.0.0 [RVL-09] (검토 DATA-11) With 금액 가리기 on (and 앱 잠금 off), the recents screen shows no picture of the app either (Android 13+):
     * pay and money hidden on screen were still there in the recents list. Screenshots stay allowed: the user shares the calendar on
     * purpose, and the amounts in it are hidden already.
     */
    fun hideRecents(activity: Activity, on: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!on)
    }

    /** Turned off in settings: no cover from now on. */
    fun release() {
        _locked.value = false
    }

    /**
     * Asks for the phone's lock. Android 11 and later show the fingerprint or face with the PIN as the
     * way around; before that the phone's own confirm screen, through [confirm] and [onConfirmed].
     */
    fun ask(activity: Activity, confirm: (Intent) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                .setTitle("밤낮 잠금 풀기")
                .setSubtitle("휴대폰 잠금과 같은 방법으로 풀어요")
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    _locked.value = false
                }
            })
        } else {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent("밤낮 잠금 풀기", "휴대폰 잠금과 같은 방법으로 풀어요")
            if (intent == null) _locked.value = false else confirm(intent)
        }
    }

    fun onConfirmed(ok: Boolean) {
        if (ok) _locked.value = false
    }
}
