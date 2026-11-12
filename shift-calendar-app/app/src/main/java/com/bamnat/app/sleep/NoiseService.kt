package com.bamnat.app.sleep

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.sleep.NoiseMix
import com.bamnat.core.sleep.NoiseSynth
import com.bamnat.core.sleep.NoiseTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.38 [SLEEP-04] 잠드는 소리: the sound is made here as it plays ([NoiseSynth], no files), on the media stream, so its
 * volume is the phone's media volume and never the alarm's. With a timer it fades over the last 90 seconds and stops.
 * Another app's sound (a call, a video) takes over and this one stops, as a sleep sound should.
 */
class NoiseService : Service() {
    /** What is playing now, for the sleep screen and the flow card; null when quiet. */
    data class Playing(val mix: NoiseMix, val minutes: Int, val startedAt: Long)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var focus: AudioFocusRequest? = null
    @Volatile private var paused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        val settings = appContainer.settingsRepository.current
        val mix = intent?.getStringExtra(EXTRA_MIX)?.let(NoiseMix::decode) ?: NoiseMix.decode(settings.noiseMix.ifBlank { null })
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, -1)?.takeIf { it >= 0 } ?: settings.noiseTimerMinutes
        ServiceCompat.startForeground(this, Notifications.NOISE_ID, notice(minutes), TYPE)
        play(mix, minutes)
        return START_NOT_STICKY
    }

    private fun play(mix: NoiseMix, minutes: Int) {
        running = false
        thread?.join(500)
        val audio = getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        if (Build.VERSION.SDK_INT >= 26) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                // 2.0.0 [RVI-51] (검토 ALM-26) A call or an alarm takes the sound for a while: it waits (it played on under them),
                // and comes back after; a lasting loss stops it.
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> stopAll()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> paused = true
                        AudioManager.AUDIOFOCUS_GAIN -> paused = false
                    }
                }
                .build().also { audio.requestAudioFocus(it) }
        }
        val rate = NoiseSynth.SAMPLE_RATE
        val chunk = rate / 10
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, chunk * 4)).setTransferMode(AudioTrack.MODE_STREAM).build()
        val total = minutes * 60_000L
        val started = SystemClock.elapsedRealtime()
        _playing.value = Playing(mix, minutes, com.bamnat.core.time.Today.millis())
        running = true
        thread = Thread({
            val synth = NoiseSynth(rate, seed = started)
            val out = ShortArray(chunk)
            val scratch = FloatArray(chunk)
            // A gentle start, so the first second does not startle.
            var fadeIn = 0f
            runCatching {
                track.play()
                while (running) {
                    if (paused) { Thread.sleep(100); continue }
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val g = NoiseTimer.gain(elapsed, total)
                    if (g <= 0f) break
                    fadeIn = minOf(1f, fadeIn + 0.05f)
                    synth.render16(mix, out, g * fadeIn, scratch)
                    track.write(out, 0, out.size)
                }
            }
            runCatching { track.stop() }
            track.release()
            if (running) ContextCompat.getMainExecutor(this).execute { stopAll() }
        }, "bamnat-noise").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun stopAll() {
        running = false
        _playing.value = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        _playing.value = null
        super.onDestroy()
    }

    private fun notice(minutes: Int) = NotificationCompat.Builder(this, Notifications.DAY_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setSilent(true)
        .setOngoing(true)
        .setContentTitle("잠드는 소리")
        .setContentText(if (minutes > 0) "${NoiseTimer.label(minutes)} 뒤 서서히 꺼져요" else "끌 때까지 계속 나요")
        .setContentIntent(Notifications.open(this, NoticeLinks.Target.SLEEP, 3406))
        .addAction(0, "끄기", PendingIntent.getService(this, 3407, Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        const val ACTION_STOP = "com.bamnat.app.sleep.NOISE_STOP"
        const val EXTRA_MIX = "mix"
        const val EXTRA_MINUTES = "minutes"
        private val TYPE = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

        private val _playing = MutableStateFlow<Playing?>(null)
        val playing: StateFlow<Playing?> = _playing.asStateFlow()

        /** Starts the sound (the saved mix and timer unless given), or changes what is playing. */
        fun start(context: Context, mix: NoiseMix? = null, minutes: Int? = null) {
            val i = Intent(context, NoiseService::class.java)
            mix?.let { i.putExtra(EXTRA_MIX, it.encode()) }
            minutes?.let { i.putExtra(EXTRA_MINUTES, it) }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            if (_playing.value == null) return
            context.startService(Intent(context, NoiseService::class.java).setAction(ACTION_STOP))
        }

        /** The bedtime notice's "소리 켜기" ([SLEEP-05]): the saved mix and timer. */
        fun startIntent(context: Context, requestCode: Int): PendingIntent =
            if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, requestCode, Intent(context, NoiseService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            else PendingIntent.getService(context, requestCode, Intent(context, NoiseService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
