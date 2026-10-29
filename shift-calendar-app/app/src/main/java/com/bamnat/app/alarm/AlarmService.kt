package com.bamnat.app.alarm

import kotlinx.coroutines.launch
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.diag.DiagEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.format.DateTimeFormatter
import com.bamnat.core.time.Today

/**
 * Rings the alarm: plays the chosen sound on the alarm stream, vibrates, and shows a
 * full-screen notification. Runs as a foreground service so it keeps ringing with the screen off.
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    /** [STAB-01] The sound being prepared, and which ring it belongs to, so a late answer is ignored. */
    private var preparing: MediaPlayer? = null
    private var soundAttempt = 0
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var volume = 1f
    /** [ALRM-08] A button pressed on a mission alarm: quiet until then (epoch ms), the mission stays. */
    private var quietUntil = 0L
    /** [ALRM-08] The power button, seen as the screen going off; registered only while ringing. */
    private var screenOff: android.content.BroadcastReceiver? = null
    /** [WEAR-06] 1.31 N2: the watch was told this ring started, so it is told when it stops. */
    private var watchRung = false
    /** [WEAR-06] 워치 먼저: the phone's own sound and vibration, held back while the watch rings. */
    private var phoneLater: Runnable? = null

    private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ring = RingRequest.fromIntent(intent)
                if (ring != null && _ringing.value == null && AlarmsOff.silences(this, ring)) {
                    // [ALRM-09] 오늘 남은 알람 모두 끄기: anything that still comes today stays silent.
                    if (!BootAlarms.isLocked(this)) appContainer.telemetry.event("alarm_silenced", if (ring.recheck) "recheck" else if (ring.isSnooze) "snooze" else "alarm")
                    startInForeground(null)
                    finish()
                } else if (ring == null) {
                    // startForegroundService() requires startForeground() even when there is nothing to do.
                    startInForeground(null)
                    finish()
                } else {
                    startRinging(ring)
                }
            }
            ACTION_SNOOZE -> {
                val ring = _ringing.value
                if (ring != null) {
                    val minutes = BootAlarms.ringSettings(this).snoozeMinutes
                    if (BootAlarms.isLocked(this)) {
                        // [REL-10] Still locked after a reboot: the snooze lives only in the device-protected copy.
                        val at = Today.millis() + minutes.coerceAtLeast(1) * 60_000L
                        val snoozed = ring.copy(isSnooze = true)
                        AlarmScheduler.setAlarm(this, getSystemService(android.app.AlarmManager::class.java), at, AlarmScheduler.snoozeIntent(this, snoozed))
                        BootAlarms.saveSnooze(this, snoozed, at)
                        Notifications.postSnoozeWaiting(this, snoozed, at)
                    } else {
                        appContainer.alarmScheduler.scheduleSnooze(ring, minutes)
                        appContainer.telemetry.event(DiagEvents.ALARM_SNOOZED)
                    }
                }
                finish()
            }
            ACTION_ALL_OFF -> {
                // [ALRM-09] 오늘 남은 알람 모두 끄기. With a mission it opens the mission and waits for it.
                val ring = _ringing.value
                if (ring != null && BootAlarms.ringSettings(this).missionFor(ring) != AlarmMission.NONE) {
                    _allOffAfterMission.value = true
                    runCatching { startActivity(missionActivityIntent(this, allOff = true)) }
                } else {
                    if (ring != null && !BootAlarms.isLocked(this)) appContainer.telemetry.event(DiagEvents.ALARM_DISMISSED, listOfNotNull("all_off", offDetail(ring)).joinToString(" "))
                    AlarmsOff.turnOff(this, ring)
                    finish()
                }
            }
            ACTION_BUTTON -> pressed()
            else -> {
                // ACTION_DISMISS, or a restart with no intent
                val ring = _ringing.value
                val how = intent?.getStringExtra(EXTRA_HOW)?.takeIf { it.isNotEmpty() }
                // [ALRM-09] 오늘 모두 끄기 on a mission alarm, now that the mission is done (or was given up after 5 minutes).
                val allOff = intent?.action == ACTION_DISMISS && ring != null && _allOffAfterMission.value && how != null
                if (intent?.action == ACTION_DISMISS && ring != null && !BootAlarms.isLocked(this)) {
                    appContainer.telemetry.event(DiagEvents.ALARM_DISMISSED, listOfNotNull(how, if (allOff) "all_off" else null, offDetail(ring)).joinToString(" "))
                    // [PROV-07] A few minutes on, 일어났나요?; once per alarm, never for a memo's schedule.
                    val minutes = BootAlarms.ringSettings(this).wakeCheckMinutes
                    if (minutes > 0 && !ring.recheck && ring.label == null && !allOff && !AlarmsOff.isOff(this)) WakeCheck.schedule(this, ring, minutes)
                    // [ALRM-16] 1.31 N3: the day at a glance, after a shift alarm (not a memo's schedule).
                    if (ring.label == null && !ring.recheck) AfterAlarmCard.postSoon(this)
                }
                if (allOff) AlarmsOff.turnOff(this, ring)
                finish()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * [ALRM-08] A volume or power button while ringing (결정 D78): 끄기 by default, 다시 울림 or nothing by the
     * setting; with a mission only the sound goes quiet for a while and the mission stays.
     */
    private fun pressed() {
        val ring = _ringing.value ?: return
        val settings = BootAlarms.ringSettings(this)
        val mission = settings.missionFor(ring) != AlarmMission.NONE
        val outcome = AlarmButtons.outcome(settings.buttons, mission)
        if (!BootAlarms.isLocked(this)) appContainer.telemetry.event("alarm_button", outcome.name.lowercase())
        when (outcome) {
            AlarmButtons.Outcome.DISMISS -> onStartCommand(Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_HOW, "button"), 0, 0)
            AlarmButtons.Outcome.SNOOZE -> onStartCommand(Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), 0, 0)
            AlarmButtons.Outcome.QUIET -> quietBriefly()
            AlarmButtons.Outcome.NOTHING -> Unit
        }
    }

    /** [ALRM-08] The sound almost off and no vibration for [AlarmButtons.QUIET_SECONDS], then back as it was. */
    private fun quietBriefly() {
        quietUntil = Today.millis() + AlarmButtons.QUIET_SECONDS * 1_000L
        _quiet.value = true
        runCatching { player?.setVolume(QUIET_VOLUME, QUIET_VOLUME) }
        vibrator?.cancel()
        vibrator = null
        handler.removeCallbacks(endQuiet)
        handler.postDelayed(endQuiet, AlarmButtons.QUIET_SECONDS * 1_000L)
    }

    private val endQuiet = Runnable {
        quietUntil = 0L
        _quiet.value = false
        if (_ringing.value == null) return@Runnable
        runCatching { player?.setVolume(volume, volume) }
        val settings = BootAlarms.ringSettings(this)
        if (settings.vibrate || player == null) startVibration()
    }

    private fun listenForPowerButton() {
        if (screenOff != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF && AlarmButtons.screenOffIsPress(screenShownAt, screenHiddenAt, Today.millis())) pressed()
            }
        }
        // A system broadcast: not exported to other apps either way.
        runCatching { ContextCompat.registerReceiver(this, receiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onSuccess { screenOff = receiver }
    }

    private fun startRinging(ring: RingRequest) {
        stopOutputs()
        phoneLater?.let(handler::removeCallbacks)
        phoneLater = null
        // [PROV-06] The mission's 5 minutes count from the first ring, not from a ring that replaced it.
        if (_ringing.value == null) _since.value = Today.millis()
        _ringing.value = ring
        if (!ring.recheck && !BootAlarms.isLocked(this)) WakeCheck.cancel(this)
        startInForeground(ring)

        val settings = BootAlarms.ringSettings(this)
        // [WEAR-06] 1.31 N2: the watch rings too, or first. A mission alarm always rings on the phone (the watch can't do the mission).
        val watch = if (BootAlarms.isLocked(this)) AppSettings.WATCH_ALARM_OFF else appContainer.settingsRepository.current.watchAlarm
        val watchFirst = watch == AppSettings.WATCH_ALARM_WATCH && settings.missionFor(ring) == AlarmMission.NONE
        if (watch != AppSettings.WATCH_ALARM_OFF) {
            watchRung = true
            val later = Runnable { phoneLater = null; if (_ringing.value === ring) phoneOutputs(ring) }
            if (watchFirst) {
                phoneLater = later
                handler.postDelayed(later, WATCH_FIRST_MILLIS)
            }
            appContainer.appScope.launch {
                val reached = com.bamnat.app.watch.WatchRing.ring(applicationContext, describe(ring))
                // No watch took it: the phone rings now, without waiting the minute.
                if (!reached && watchFirst) handler.post { if (phoneLater === later) { handler.removeCallbacks(later); later.run() } }
            }
        }
        if (!watchFirst) phoneOutputs(ring)
        listenForPowerButton()

        handler.postDelayed({
            if (!BootAlarms.isLocked(this)) appContainer.telemetry.event(DiagEvents.ALARM_DISMISSED, listOfNotNull("auto_stop", offDetail(ring)).joinToString(" "))
            finish()
        }, settings.ringMinutes.coerceIn(1, 60) * 60_000L)
    }

    /** The phone's own sound and vibration for [ring]. */
    private fun phoneOutputs(ring: RingRequest) {
        val settings = BootAlarms.ringSettings(this)
        val sound = ring.soundUri ?: settings.defaultSound
        if (sound != AppSettings.SILENT_SOUND) {
            requestFocus()
            playSound(candidateUris(sound), settings.gradualVolume)
        }
        if (settings.vibrate || sound == AppSettings.SILENT_SOUND) startVibration()
    }

    /**
     * [ALRM-03] For 알람 제안: which shift's alarm this was and when it was due, snoozes included (the due time comes
     * from the shift, not from the snooze). Hand-set, memo and 일어났나요? alarms say nothing.
     */
    private fun offDetail(ring: RingRequest): String? {
        if (ring.shiftTypeId <= 0 || ring.custom || ring.label != null || ring.recheck) return null
        val due = ring.shiftDate.atTime(ring.shiftStart).minusMinutes(ring.offsetMinutes.toLong())
            .atZone(Today.zone()).toInstant().toEpochMilli()
        return com.bamnat.core.alarm.AlarmSuggest.offDetail(ring.shiftTypeId, due, ring.offsetMinutes)
    }

    private fun startInForeground(ring: RingRequest?) {
        val notification = buildNotification(ring)
        try {
            ServiceCompat.startForeground(this, Notifications.ALARM_NOTIFICATION_ID, notification, FOREGROUND_TYPE)
        } catch (e: RuntimeException) {
            // Only exact alarms may start a foreground service from the background. If exact alarms
            // were revoked, still show the alarm notification and ring for as long as the system allows.
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33
            ) {
                NotificationManagerCompat.from(this).notify(Notifications.ALARM_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(ring: RingRequest?): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            this, REQ_FULL_SCREEN,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeMinutes = BootAlarms.ringSettings(this).snoozeMinutes
        val builder = NotificationCompat.Builder(this, Notifications.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
        if (ring == null) {
            return builder.setContentTitle(getString(R.string.app_name)).build()
        }
        val mission = BootAlarms.ringSettings(this).missionFor(ring) != AlarmMission.NONE
        return builder
            .setContentTitle(ring.title)
            .setContentText(describe(ring))
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            // [REL-12] Both buttons work from the lock screen without unlocking the phone.
            .addAction(lockScreenAction("${snoozeMinutes}분 뒤 다시 울림", ACTION_SNOOZE, REQ_SNOOZE))
            // [PROV-06] With a mission, 끄기 opens it over the lock screen instead of turning the alarm off.
            .addAction(
                if (!mission) lockScreenAction("끄기", ACTION_DISMISS, REQ_DISMISS)
                else NotificationCompat.Action.Builder(0, "끄기", missionIntent(this)).setAuthenticationRequired(false).build(),
            )
            // [ALRM-07][ALRM-09] All three work from a small notice too, when Android shows no ringing screen
            // (the phone in use, or 전체 화면 알림 off). With a mission 오늘 모두 끄기 waits for it.
            .addAction(
                if (!mission) lockScreenAction("오늘 모두 끄기", ACTION_ALL_OFF, REQ_ALL_OFF)
                else NotificationCompat.Action.Builder(0, "오늘 모두 끄기", allOffMissionIntent(this)).setAuthenticationRequired(false).build(),
            )
            // Swiping the notification away must not leave the alarm ringing with no controls.
            .setDeleteIntent(serviceIntent(this, ACTION_SNOOZE, REQ_DELETE))
            .build()
    }

    /** Chosen sound first, then the system alarm, ringtone and notification sounds. */
    private fun candidateUris(sound: String?): List<Uri> =
        soundCandidates(sound, RingtoneManager.getActualDefaultRingtoneUri(this@AlarmService, RingtoneManager.TYPE_ALARM))

    /**
     * [STAB-01] Plays the first of [candidates] that works. Each is prepared in the background
     * (prepareAsync): preparing a sound on a card, in the cloud or no longer there could hold the main
     * thread for seconds, and the app froze if it was open when the alarm rang. A sound that fails or
     * takes too long gives way to the next one, and with none left the phone vibrates.
     */
    private fun playSound(candidates: List<Uri>, gradual: Boolean, attempt: Int = ++soundAttempt) {
        if (attempt != soundAttempt) return
        val uri = candidates.firstOrNull() ?: run {
            // No playable sound at all: make sure the user still notices.
            startVibration()
            return
        }
        val mp = MediaPlayer()
        var done = false
        fun next() {
            if (done) return
            done = true
            if (preparing === mp) preparing = null
            runCatching { mp.release() }
            playSound(candidates.drop(1), gradual, attempt)
        }
        val asked = runCatching {
            mp.setAudioAttributes(alarmAttributes)
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setOnPreparedListener { ready ->
                if (done || attempt != soundAttempt || preparing !== ready) return@setOnPreparedListener
                val started = runCatching {
                    volume = if (gradual) GRADUAL_START else 1f
                    ready.setVolume(volume, volume)
                    ready.start()
                }.isSuccess
                if (started) {
                    done = true
                    preparing = null
                    player = ready
                    if (gradual) handler.postDelayed(volumeRamp, RAMP_STEP_MILLIS)
                } else {
                    next()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                if (preparing === mp) next()
                // The sound broke off while ringing: the alarm still goes on, by vibration.
                else if (player === mp) startVibration()
                true
            }
            preparing = mp
            mp.prepareAsync()
            handler.postDelayed({ if (preparing === mp && attempt == soundAttempt) next() }, PREPARE_TIMEOUT_MILLIS)
        }.isSuccess
        if (!asked) next()
    }

    private val volumeRamp = object : Runnable {
        override fun run() {
            val mp = player ?: return
            volume = (volume + RAMP_INCREMENT).coerceAtMost(1f)
            if (quietUntil == 0L) runCatching { mp.setVolume(volume, volume) }
            if (volume < 1f) handler.postDelayed(this, RAMP_STEP_MILLIS)
        }
    }

    private fun startVibration() {
        if (vibrator != null || quietUntil > Today.millis()) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        if (!v.hasVibrator()) return
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0), alarmAttributes)
        vibrator = v
    }

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(request)
        focusRequest = request
    }

    private fun stopOutputs() {
        handler.removeCallbacksAndMessages(null)
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            mp.release()
        }
        player = null
        soundAttempt++
        preparing?.let { runCatching { it.release() } }
        preparing = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focusRequest = null
        quietUntil = 0L
        _quiet.value = false
        screenOff?.let { runCatching { unregisterReceiver(it) } }
        screenOff = null
    }

    private fun finish() {
        val rang = _ringing.value
        stopOutputs()
        phoneLater?.let(handler::removeCallbacks)
        phoneLater = null
        if (watchRung) {
            watchRung = false
            appContainer.appScope.launch { com.bamnat.app.watch.WatchRing.stop(applicationContext) }
        }
        // [ALRM-07] Android 14+ with 전체 화면 알림 off shows only a small notice; say so once, with the way to allow it.
        if (rang != null && screenShownAt == 0L && !Notifications.canUseFullScreen(this)) Notifications.postFullScreenOff(this)
        _allOffAfterMission.value = false
        _ringing.value = null
        _since.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(Notifications.ALARM_NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        stopOutputs()
        _ringing.value = null
        _since.value = null
        super.onDestroy()
    }

    companion object {
        /**
         * The sounds to try, in order: the shift's or the app's own, then this phone's alarm sound and the system's.
         * 1.32 [WTYP-13] 조사 대조 W10: a sound file from the old phone that is not on this one gives way to these, never silence.
         */
        internal fun soundCandidates(sound: String?, phoneAlarm: Uri?): List<Uri> = buildList {
            sound?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull()?.let(::add) }
            phoneAlarm?.let(::add)
            add(Settings.System.DEFAULT_ALARM_ALERT_URI)
            add(Settings.System.DEFAULT_RINGTONE_URI)
            add(Settings.System.DEFAULT_NOTIFICATION_URI)
        }.distinct()

        const val ACTION_START = "com.bamnat.app.alarm.START"
        const val ACTION_DISMISS = "com.bamnat.app.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.bamnat.app.alarm.SNOOZE"
        /** [ALRM-09] 오늘 남은 알람 모두 끄기. */
        const val ACTION_ALL_OFF = "com.bamnat.app.alarm.ALL_OFF"
        /** [ALRM-08] A volume or power button. */
        const val ACTION_BUTTON = "com.bamnat.app.alarm.BUTTON"
        /** [PROV-06] How it was turned off, for the log: "mission", "skip" or none. */
        private const val EXTRA_HOW = "com.bamnat.app.alarm.extra.HOW"

        /**
         * [OPS-10] The alarm-app type on Android 14+, open to apps holding USE_EXACT_ALARM; older
         * versions need no type. No mediaPlayback, so Play asks for no media playback declaration.
         */
        val FOREGROUND_TYPE: Int
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0

        private const val REQ_FULL_SCREEN = 2001
        private const val REQ_SNOOZE = 2002
        private const val REQ_DISMISS = 2003
        private const val REQ_DELETE = 2004
        private const val REQ_MISSION = 2005
        private const val REQ_ALL_OFF = 2006
        private const val REQ_ALL_OFF_MISSION = 2007
        private const val QUIET_VOLUME = 0.05f
        private const val GRADUAL_START = 0.15f
        private const val RAMP_INCREMENT = 0.085f
        private const val RAMP_STEP_MILLIS = 2_000L
        /** [STAB-01] A sound not ready by then gives way to the next one. */
        private const val PREPARE_TIMEOUT_MILLIS = 5_000L

        private val _ringing = MutableStateFlow<RingRequest?>(null)
        /** The alarm currently ringing, observed by [AlarmActivity]. */
        val ringing: StateFlow<RingRequest?> = _ringing.asStateFlow()

        private val _since = MutableStateFlow<Long?>(null)
        /** [PROV-06] When the ringing began (epoch ms), for the mission's time limit. */
        val ringingSince: StateFlow<Long?> = _since.asStateFlow()

        private val _quiet = MutableStateFlow(false)
        /** [ALRM-08] A button quieted a mission alarm for a while. */
        val quiet: StateFlow<Boolean> = _quiet.asStateFlow()

        private val _allOffAfterMission = MutableStateFlow(false)
        /** [ALRM-09] 오늘 모두 끄기 was asked on a mission alarm: it happens when the mission is done. */
        val allOffAfterMission: StateFlow<Boolean> = _allOffAfterMission.asStateFlow()

        /** [ALRM-08] When the ringing screen was last in front and last left it (epoch ms, 0 never). */
        @Volatile var screenShownAt = 0L
            private set
        @Volatile var screenHiddenAt = 0L
            private set

        fun screenShown(now: Long = Today.millis()) { screenShownAt = now }
        fun screenHidden(now: Long = Today.millis()) { screenHiddenAt = now }
        /** The ringing screen is gone: the next ring starts from nothing. */
        fun screenGone() { screenShownAt = 0L; screenHiddenAt = 0L }

        /** [PROV-06] The ringing screen, opened on the mission. */
        fun missionIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, REQ_MISSION, missionActivityIntent(context, allOff = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** [ALRM-09] The mission, with 오늘 모두 끄기 waiting for it. */
        fun allOffMissionIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, REQ_ALL_OFF_MISSION, missionActivityIntent(context, allOff = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun missionActivityIntent(context: Context, allOff: Boolean): Intent =
            Intent(context, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_MISSION, true)
                .putExtra(AlarmActivity.EXTRA_ALL_OFF, allOff)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** [ALRM-09] Marks 오늘 모두 끄기 as waiting for the mission (the screen opened with it). */
        fun allOffAfterMission(on: Boolean) { _allOffAfterMission.value = on }

        /** [ALRM-09] 오늘 남은 알람 모두 끄기, from the ringing screen. */
        fun allOff(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_ALL_OFF))
        }

        /** [ALRM-08] A volume key on the ringing screen. */
        fun button(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_BUTTON))
        }

        /** [PROV-06] 끄기 from the app's own banner: the mission when there is one. */
        fun askToDismiss(context: Context) {
            val ring = _ringing.value ?: return
            if (BootAlarms.ringSettings(context).missionFor(ring) == AlarmMission.NONE) dismiss(context)
            else context.startActivity(Intent(context, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_MISSION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }

        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

        fun describe(ring: RingRequest): String {
            val start = ring.shiftStart.format(timeFormat)
            val work = ring.shiftName.isNotBlank()
            return when {
                // [SCHED-01] A memo's schedule: when it is.
                // [PROV-07] 일어났나요? had no answer.
                ring.recheck -> "끈 뒤 답이 없어 다시 울려요"
                ring.label != null && ring.isSnooze -> "$start 일정 · 다시 울림"
                ring.label != null -> "$start 일정"
                ring.isSnooze && work -> "$start 근무 시작 · 다시 울림"
                ring.isSnooze -> "다시 울림"
                ring.custom && work -> "$start 근무 시작 · 이 날 알람"
                ring.custom -> "이 날 알람"
                else -> "$start 근무 시작 · ${ring.offsetMinutes}분 전 알람"
            }
        }

        fun start(context: Context, ring: RingRequest) {
            val intent = ring.putInto(Intent(context, AlarmService::class.java).setAction(ACTION_START))
            ContextCompat.startForegroundService(context, intent)
        }

        fun dismiss(context: Context, how: String? = null) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_HOW, how))
        }

        fun snooze(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }

        /**
         * [WEAR-06] 1.31 N2: 끄기 or 다시 울림 pressed on the watch. 끄기 on a mission alarm works as a button press does
         * (the sound goes quiet for a while and the mission stays), so the watch never skips the mission.
         */
        fun fromWatch(context: Context, snooze: Boolean) {
            val ring = _ringing.value ?: return
            when {
                snooze -> snooze(context)
                BootAlarms.ringSettings(context).missionFor(ring) != AlarmMission.NONE -> button(context)
                else -> dismiss(context, "watch")
            }
        }

        /** [WEAR-06] 워치 먼저: how long the phone waits for the watch's answer before it rings itself. */
        const val WATCH_FIRST_MILLIS = 60_000L

        /** A notification button that never asks to unlock first (Android 12+ can, per action). */
        private fun Context.lockScreenAction(title: String, action: String, requestCode: Int): NotificationCompat.Action =
            NotificationCompat.Action.Builder(0, title, actionIntent(this, action, requestCode))
                .setAuthenticationRequired(false)
                .build()

        /** Through [AlarmActionReceiver]: a broadcast always reaches the app from the lock screen. */
        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, AlarmActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
