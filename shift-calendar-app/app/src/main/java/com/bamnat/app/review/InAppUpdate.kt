package com.bamnat.app.review

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.39 [LOOP-13] Google Play 인앱 업데이트 (사용자 결정 11-07). The server says from which version on (`/api/config` `update`):
 * below `urgentMin` (an alarm that did not ring, data lost) Play's full screen update comes first; below `flexibleMin`
 * the update downloads behind the app, asked once per Play version, and a line offers 다시 시작 when it is ready.
 * With nothing set (0) nothing is asked, so the two-week updates never nag. Not from Play (a debug build): silent.
 */
object InAppUpdate {
    enum class Mode { IMMEDIATE, FLEXIBLE }

    /** Pure, for tests: which update this version needs. */
    fun due(code: Int, urgentMin: Int, flexibleMin: Int): Mode? = when {
        urgentMin > 0 && code < urgentMin -> Mode.IMMEDIATE
        flexibleMin > 0 && code < flexibleMin -> Mode.FLEXIBLE
        else -> null
    }

    /** A flexible update was asked for this Play version already (once per version, not every start). */
    fun askedFor(context: Context, available: Int): Boolean = prefs(context).getInt(KEY, 0) >= available

    private val _ready = MutableStateFlow(false)
    /** The flexible download finished: the app offers 다시 시작. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var manager: AppUpdateManager? = null
    private val listener = InstallStateUpdatedListener { s -> if (s.installStatus() == InstallStatus.DOWNLOADED) _ready.value = true }

    /** On every start: resumes an urgent update left half way, finds a finished download, or asks once if [mode] says so. */
    fun check(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>, mode: Mode?) {
        runCatching {
            val m = manager ?: AppUpdateManagerFactory.create(context.applicationContext).also { manager = it; it.registerListener(listener) }
            m.appUpdateInfo.addOnSuccessListener { info ->
                runCatching {
                    when {
                        info.installStatus() == InstallStatus.DOWNLOADED -> _ready.value = true
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                            m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
                        info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || mode == null -> Unit
                        mode == Mode.IMMEDIATE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                            m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && !askedFor(context, info.availableVersionCode()) -> {
                            prefs(context).edit().putInt(KEY, info.availableVersionCode()).apply()
                            m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                        }
                    }
                }
            }
        }
    }

    /** 다시 시작: Play installs the downloaded version and opens the app again. */
    fun restart() {
        _ready.value = false
        runCatching { manager?.completeUpdate() }
    }

    /** 나중에: asked again on the next start (the download stays). */
    fun later() { _ready.value = false }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("in_app_update", Context.MODE_PRIVATE)
    private const val KEY = "flexible_asked"
}
