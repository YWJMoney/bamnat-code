package com.bamnat.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.alarm.Missions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime
import com.bamnat.core.time.Today

/**
 * Full-screen ringing screen, shown over the lock screen. [PROV-06] With a mission, 알람 끄기 (here, on
 * the notification or in the app) opens it first; after [Missions.GIVE_UP_AFTER] it can be skipped.
 */
class AlarmActivity : ComponentActivity() {
    private val solving = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_MISSION, false)) solving.value = true
        if (intent.getBooleanExtra(EXTRA_ALL_OFF, false)) AlarmService.allOffAfterMission(true)
    }

    override fun onStart() {
        super.onStart()
        AlarmService.screenShown()
    }

    override fun onStop() {
        AlarmService.screenHidden()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) AlarmService.screenGone()
        super.onDestroy()
    }

    /** [ALRM-08] 소리 줄이기·키우기 while ringing: what 버튼으로 알람 끄기 says; with 아무것도 안 함 the volume moves as usual. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode in AlarmButtons.VOLUME_KEYS && AlarmService.ringing.value != null &&
            BootAlarms.ringSettings(this).buttons != AlarmButtons.Action.NONE) {
            if (event.repeatCount == 0) AlarmService.button(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode in AlarmButtons.VOLUME_KEYS && BootAlarms.ringSettings(this).buttons != AlarmButtons.Action.NONE) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.getBooleanExtra(EXTRA_MISSION, false) == true) solving.value = true
        if (intent?.getBooleanExtra(EXTRA_ALL_OFF, false) == true) AlarmService.allOffAfterMission(true)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val snoozeMinutes = BootAlarms.ringSettings(this).snoozeMinutes
        val buttons = BootAlarms.ringSettings(this).buttons
        // [ALRM-04] 증명 사진's fingerprints; none before the first unlock after a restart, and then it is sums.
        val photos = runCatching { PhotoReferences(this).all.value }.getOrDefault(emptyList())

        // [FREE-05] 1.35 화면 색·배경's 알람 화면: colours and a background only; read where the phone is unlocked, else the app's own.
        val skins = runCatching {
            val um = getSystemService(android.os.UserManager::class.java)
            if (um?.isUserUnlocked != false) com.bamnat.core.recipe.ScreenLooks.decode(
                getSharedPreferences("settings", MODE_PRIVATE).getString("screen_looks", null),
            ) else null
        }.getOrNull() ?: com.bamnat.core.recipe.ScreenLooks()

        setContent {
            BamnatTheme(themeMode = ThemeMode.DARK) {
              androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.theme.LocalScreenLooks provides skins) {
              com.bamnat.app.ui.theme.ScreenSkin(com.bamnat.app.ui.theme.SCREEN_ALARM, androidx.compose.ui.Modifier.fillMaxSize(), bars = true) {
                val ring by AlarmService.ringing.collectAsStateWithLifecycle()
                // Leave as soon as the alarm stops, whether from here, the notification or the timeout.
                LaunchedEffect(ring) {
                    if (ring == null) finish()
                }
                // Back must not silently leave an alarm ringing; it snoozes instead.
                BackHandler {
                    AlarmService.snooze(this@AlarmActivity)
                }
                val current = ring
                val mission = current?.let { BootAlarms.ringSettings(this@AlarmActivity).missionFor(it) } ?: AlarmMission.NONE
                val inMission by solving.collectAsStateWithLifecycle()
                val since by AlarmService.ringingSince.collectAsStateWithLifecycle()
                val quiet by AlarmService.quiet.collectAsStateWithLifecycle()
                val allOffWaits by AlarmService.allOffAfterMission.collectAsStateWithLifecycle()
                val inCall by AlarmService.inCall.collectAsStateWithLifecycle()
                if (current != null && inMission && mission != AlarmMission.NONE) {
                    MissionScreen(
                        mission = mission,
                        since = since ?: Today.millis(),
                        snoozeMinutes = snoozeMinutes,
                        onSnooze = { AlarmService.snooze(this@AlarmActivity) },
                        onDone = { how -> AlarmService.dismiss(this@AlarmActivity, how) },
                        references = photos.map { it.hash },
                        place = photos.lastOrNull()?.place.orEmpty(),
                        note = missionNote(buttons, quiet, allOffWaits),
                    )
                } else if (current != null) {
                    RingingScreen(
                        title = if (current.recheck) current.title else current.label ?: if (current.shiftName.isBlank()) "알람" else "${current.shiftName} 근무",
                        subtitle = AlarmService.describe(current),
                        snoozeMinutes = snoozeMinutes,
                        dismissLabel = if (mission == AlarmMission.NONE) "알람 끄기" else "알람 끄기 · ${mission.label}",
                        onSnooze = { AlarmService.snooze(this@AlarmActivity) },
                        onDismiss = {
                            if (mission == AlarmMission.NONE) AlarmService.dismiss(this@AlarmActivity) else solving.value = true
                        },
                        // [ALRM-09] With a mission it opens the mission first, and turns today off once that is done.
                        onAllOff = {
                            if (mission == AlarmMission.NONE) AlarmService.allOff(this@AlarmActivity)
                            else { AlarmService.allOffAfterMission(true); solving.value = true }
                        },
                        hint = AlarmButtons.hint(buttons, mission != AlarmMission.NONE),
                        inCall = inCall,
                    )
                }
              }
              }
            }
        }
    }

    companion object {
        /** [PROV-06] Open on the mission rather than the ringing screen. */
        const val EXTRA_MISSION = "com.bamnat.app.alarm.extra.MISSION"
        /** [ALRM-09] 오늘 모두 끄기 from the notice of a mission alarm: it waits for the mission. */
        const val EXTRA_ALL_OFF = "com.bamnat.app.alarm.extra.ALL_OFF"
    }
}

/** [ALRM-08][ALRM-09] The line above a mission: what the buttons did or do, and 오늘 모두 끄기 waiting for it. */
internal fun missionNote(buttons: AlarmButtons.Action, quiet: Boolean, allOffWaits: Boolean): String? = listOfNotNull(
    when {
        quiet -> "버튼을 눌러 소리를 ${AlarmButtons.QUIET_SECONDS}초 줄였어요"
        // The mission's end is said once, by the line below.
        allOffWaits && buttons == AlarmButtons.Action.DISMISS -> "소리·전원 버튼은 소리만 ${AlarmButtons.QUIET_SECONDS}초 줄여요"
        else -> AlarmButtons.hint(buttons, mission = true)
    },
    if (allOffWaits) "미션을 마치면 오늘 남은 알람이 모두 꺼져요" else null,
).joinToString("\n").ifEmpty { null }
