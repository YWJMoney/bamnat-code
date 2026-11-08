package com.bamnat.app.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.data.db.SleepEventEntity
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.sleep.ClipPicker
import com.bamnat.core.sleep.SleepOnset
import com.bamnat.core.sleep.SoundDetector
import com.bamnat.core.sleep.SoundEvent
import com.bamnat.core.sleep.SoundFrames
import com.bamnat.core.sleep.SoundKind
import com.bamnat.core.sleep.SoundSummary
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.bamnat.core.sleep.RecordEnd
import com.bamnat.app.ui.Fmt
import java.io.File
import java.io.RandomAccessFile
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 1.38 [SLEEP-06][SLEEP-07] 정밀 측정: started only by the user (a tap on the sleep screen or the bedtime notice, so Android
 * allows the microphone), it hears the night on the phone and keeps times, kinds and a few short clips. Nothing is sent.
 *
 * - Quiet rooms cost little: each 30 ms frame is one loudness number until something stands out of the room.
 * - The phone's stillness (accelerometer, once a few seconds) and soft rustles find when the user really fell asleep.
 * - While another app plays sound (a video, music) nothing is counted; our own sleep sound becomes the room's level.
 * - Clips: spread over the hours, at most 3 an hour and 30 a night, 8 kHz, deleted after 7 days unless kept.
 */
class SleepRecordService : Service(), SensorEventListener {
    /** [endsAt]: when it stops by itself ([RecordEnd]), once known. */
    data class Recording(val startedAt: Long, val events: Int, val endsAt: Long? = null)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var startedAt = 0L
    private var batteryAtStart = -1
    private var sensors: SensorManager? = null
    private val moved = ArrayList<Boolean>()
    @Volatile private var movedThisMinute = false
    /** 1.38 [SLEEP-08] When the phone or the sheets last moved, or a word was said, for 깨우는 구간. */
    @Volatile private var lastMovedAt: Long? = null
    private var lastAccel: FloatArray? = null
    private var wake: android.os.PowerManager.WakeLock? = null
    @Volatile private var endsAt: Long? = null
    /** When the open sleep began (잠들었어요 may have come before 측정 시작), for the notice's clock. */
    @Volatile private var sleepFrom: Long? = null
    /** The next alarm as the night screen says it (PC 5c4ad849: the notice had none). */
    @Volatile private var nextAlarm: LocalDateTime? = null
    /** False when it stopped by the clock alone: the user may still be asleep, so the sleep stays open. */
    @Volatile private var closeSleep = true
    private var watch: kotlinx.coroutines.Job? = null
    /** 1.39 G5-B2: the phone turning off ends the night where it stands, saved, with the sleep left open. */
    private var shutdown: android.content.BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (!running) { done(); return START_NOT_STICKY }
            finishNight()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        if (!allowed(this)) { stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(this, Notifications.SLEEP_RECORD_ID, notice(), TYPE)
        begin()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun begin() {
        startedAt = Today.millis()
        batteryAtStart = battery()
        // The night, at most 14 hours: the microphone has to keep reading with the screen off.
        wake = getSystemService(android.os.PowerManager::class.java)?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "bamnat:sleep-record")
            ?.apply { setReferenceCounted(false); acquire(14 * 60 * 60 * 1000L) }
        running = true
        _recording.value = Recording(startedAt, 0)
        // 1.39 G5-B2: until this night is saved, it is "measuring" on disk too, so a night cut off (the phone turned off, the
        // battery ran out, the app was closed) is said next time rather than found missing.
        prefs(this).edit().putLong(K_RUNNING, startedAt).remove(K_CUT).apply()
        shutdown = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                prefs(context).edit().putLong(K_CUT, Today.millis()).commit()
                closeSleep = false
                finishNight()
            }
        }.also { ContextCompat.registerReceiver(this, it, android.content.IntentFilter(Intent.ACTION_SHUTDOWN), ContextCompat.RECEIVER_NOT_EXPORTED) }
        // 잠들었어요 as well: the night is in the log from now; the start moves to when the phone settled.
        appContainer.appScope.launch { appContainer.sleepRepository.fellAsleep() }
        watch = appContainer.appScope.launch { watchTheMorning() }
        sensors = getSystemService(SensorManager::class.java)?.also { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, 3_000_000, 10_000_000) }
        }
        val rate = 16_000
        val frame = (rate * SoundFrames.FRAME_MILLIS / 1000).toInt()
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), frame * 8))
        val audio = getSystemService(AudioManager::class.java)
        val ring = ShortArray(rate * RING_SECONDS)
        val events = ArrayList<SoundEvent>()
        val clips = HashMap<SoundEvent, File>()
        var written = 0L
        thread = Thread({
            val buf = ShortArray(frame)
            val low = FloatArray(1)
            val detector = SoundDetector { e ->
                // Another app's sound is not the night's.
                if (audio?.isMusicActive == true && NoiseService.playing.value == null) return@SoundDetector
                events += e
                if (e.kind == SoundKind.MOVE || e.kind == SoundKind.TALK) { movedThisMinute = true; lastMovedAt = Today.millis(); SmartWake.check(this, lastMovedAt) }
                _recording.value = Recording(startedAt, events.count { it.kind != SoundKind.MOVE && it.kind != SoundKind.OTHER }, endsAt)
                keepClip(e, events, clips, ring, written, rate)
            }
            runCatching {
                record.startRecording()
                var minuteAt = 0L
                while (running) {
                    val n = record.read(buf, 0, frame)
                    if (n <= 0) continue
                    for (i in 0 until n) ring[((written + i) % ring.size).toInt()] = buf[i]
                    written += n
                    detector.push(SoundFrames.of(buf, n, low, rate))
                    val elapsed = written * 1000 / rate
                    if (elapsed - minuteAt >= 60_000) {
                        minuteAt = elapsed
                        synchronized(moved) { moved += movedThisMinute }
                        movedThisMinute = false
                    }
                }
                detector.finish()
            }
            runCatching { record.stop() }
            record.release()
            saving = true
            val night = events.toList()
            val kept = clips.toMap()
            appContainer.appScope.launch { save(night, kept) }
        }, "bamnat-sleep-record").apply { start() }
    }

    /** Keeps this sound's clip if it is among the hour's best; drops the one it pushes out. */
    private fun keepClip(e: SoundEvent, all: List<SoundEvent>, clips: HashMap<SoundEvent, File>, ring: ShortArray, written: Long, rate: Int) {
        val keep = ClipPicker.pick(all).toSet()
        clips.keys.filter { it !in keep }.forEach { old -> clips.remove(old)?.delete() }
        if (e !in keep) return
        val endSample = written
        val wanted = ((e.millis + 2_000) * rate / 1000).coerceAtMost(ring.size.toLong() - rate)
        val from = (endSample - wanted).coerceAtLeast(0)
        val dir = File(filesDir, CLIP_DIR).apply { mkdirs() }
        val file = File(dir, "${startedAt + e.startMillis}-${e.kind.key}.wav")
        runCatching { writeWav(file, ring, from, endSample, rate) }.onSuccess { clips[e] = file }
    }

    /**
     * 1.39 [SLEEP-06] The microphone never runs into the day: it stops when 깼어요 closes the sleep (from any screen or
     * the notice), and by [RecordEnd] when nothing comes. Turning the alarm off stops it from [AlarmService].
     */
    private suspend fun watchTheMorning() {
        val c = appContainer
        val start = local(startedAt)
        val end = runCatching {
            val s = c.settingsRepository.settings.value
            val resolver = c.scheduleRepository.snapshot.first().resolver
            val now = start
            val alarm = if (s.alarmsEnabled) com.bamnat.core.alarm.AlarmPlanner.nextAlarm(resolver, now)?.triggerAt else null
            nextAlarm = alarm
            val plan = com.bamnat.core.sleep.SleepSchedule.of(resolver, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1),
                s.alarmsEnabled, s.sleepTargetMinutes)
            RecordEnd.at(start, alarm, RecordEnd.plannedWake(start, plan.sleeps), s.sleepTargetMinutes)
        }.getOrElse { start.plusHours(RecordEnd.MAX_HOURS) }
        val endMillis = end.atZone(Today.zone()).toInstant().toEpochMilli()
        endsAt = endMillis
        sleepFrom = runCatching { c.sleepRepository.openNow()?.start?.atZone(Today.zone())?.toInstant()?.toEpochMilli() }.getOrNull()
        _recording.value?.let { _recording.value = it.copy(endsAt = endMillis) }
        ContextCompat.getMainExecutor(this).execute { if (running) runCatching {
            getSystemService(android.app.NotificationManager::class.java)?.notify(Notifications.SLEEP_RECORD_ID, notice())
        } }
        var sawOpen = false
        while (running) {
            delay(30_000L)
            if (!running) return
            val open = c.sleepRepository.openNow()
            if (open != null) sawOpen = true
            val awake = sawOpen && open == null
            val late = Today.millis() >= endMillis
            if (awake || late) {
                closeSleep = awake
                ContextCompat.getMainExecutor(this).execute { if (running) finishNight() }
                return
            }
        }
    }

    private fun finishNight() {
        running = false
        watch?.cancel()
        sensors?.unregisterListener(this)
        thread?.let { t -> Thread { t.join(3_000); ContextCompat.getMainExecutor(this).execute { done() } }.start() } ?: done()
    }

    private fun done() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        _recording.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun save(events: List<SoundEvent>, clips: Map<SoundEvent, File>) = try { saveNight(events, clips) } finally {
        saving = false
        _saved.value = Today.millis()
    }

    private suspend fun saveNight(events: List<SoundEvent>, clips: Map<SoundEvent, File>) {
        val c = appContainer
        c.sleepRepository.addEvents(events.map { e ->
            SleepEventEntity(startMillis = startedAt + e.startMillis, endMillis = startedAt + e.endMillis, kind = e.kind.key,
                level = e.peakOverRoom, file = clips[e]?.name)
        })
        val onset = synchronized(moved) { SleepOnset.minuteAsleep(moved) }
        // 깼어요, with the start moved to when the phone settled. Stopped by the clock alone, the sleep stays open
        // (the user may still be asleep); 깼어요 already tapped, the sleep it closed is the night's.
        val sleep = if (closeSleep) c.sleepRepository.wokeUp() ?: c.sleepRepository.recent(1).first().firstOrNull()
            else c.sleepRepository.openNow()
        if (sleep != null && onset != null && onset > 0) {
            val start = local(startedAt).plusMinutes(onset.toLong())
            if ((sleep.end == null || start.isBefore(sleep.end)) && start.isBefore(Today.dateTime()) &&
                abs(Duration.between(sleep.start, local(startedAt)).toMinutes()) < 5)
                c.sleepRepository.edit(sleep.id, start, sleep.end)
        }
        val used = if (batteryAtStart >= 0) (batteryAtStart - battery()).coerceAtLeast(0) else -1
        val summary = SoundSummary.of(events, onset?.let { it * 60_000L })
        val p = prefs(this)
        val cut = p.getLong(K_CUT, 0L) >= startedAt
        p.edit().putLong(K_LAST_START, startedAt).putLong(K_LAST_END, Today.millis())
            .putInt(K_LAST_BATTERY, used).putString(K_LAST_LINE, summary.line()).putBoolean(K_LAST_CUT, cut)
            .remove(K_RUNNING).remove(K_CUT).apply()
        c.sleepRepository.dropOldClips(KEEP_DAYS) { name -> File(File(filesDir, CLIP_DIR), name).delete() }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val last = lastAccel
        if (last != null) {
            val d = sqrt((v[0] - last[0]) * (v[0] - last[0]) + (v[1] - last[1]) * (v[1] - last[1]) + (v[2] - last[2]) * (v[2] - last[2]))
            if (d > MOVE_THRESHOLD) { movedThisMinute = true; lastMovedAt = Today.millis(); SmartWake.check(this, lastMovedAt) }
        }
        lastAccel = v.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        shutdown?.let { runCatching { unregisterReceiver(it) } }
        shutdown = null
        running = false
        watch?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        sensors?.unregisterListener(this)
        _recording.value = null
        super.onDestroy()
    }

    private fun battery(): Int = getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    /**
     * 1.39 [SLEEP-06] One card for the night (PC 1.38 최종 사용성 6: 자는 중 and this were stacked, '측정 끝' two
     * expands away and beside '깼어요'): while measuring this is the 자는 중 notice, with the clock since falling asleep,
     * 깼어요 (ends the sleep and the measuring) and 측정만 끝 (the sleep goes on, and 자는 중 comes back).
     */
    private fun notice(): android.app.Notification {
        val from = sleepFrom ?: startedAt.takeIf { it > 0 } ?: Today.millis()
        val now = Today.dateTime()
        val alarm = nextAlarm?.takeIf { it.isAfter(now) }?.let { "다음 알람 ${com.bamnat.core.sleep.NightLine.day(now, it)} ${Fmt.time(it.toLocalTime())}" }
        val end = endsAt?.let { "측정은 늦어도 ${Fmt.time(local(it).toLocalTime())}에 저절로 끝나요" }
        val text = listOfNotNull(alarm, end).joinToString(" · ").ifEmpty { "휴대폰 안에서만 듣고, 아무 데도 보내지 않아요" }
        return NotificationCompat.Builder(this, Notifications.SLEEPING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(Notifications.SLEEPING_REC_GROUP).setGroupSummary(true)
            // PC 5차: a colored service notice is one Android 16 never folds into the app's bundle.
            .setColorized(true).setColor(0xFF1A237E.toInt())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setContentTitle("${Fmt.time(local(from).toLocalTime())}부터 자는 중 · 소리 재는 중")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(from)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setContentIntent(Notifications.open(this, NoticeLinks.Target.NIGHT, 3409))
            .addAction(SleepActions.awake(this))
            .addAction(0, "측정만 끝", PendingIntent.getService(this, 3410, Intent(this, SleepRecordService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.bamnat.app.sleep.RECORD_STOP"
        const val CLIP_DIR = "sleep_clips"
        const val KEEP_DAYS = 7L
        private const val RING_SECONDS = 12
        private const val MOVE_THRESHOLD = 0.6f
        private const val PREFS = "sleep_record"
        private const val K_LAST_START = "last_start"
        private const val K_LAST_END = "last_end"
        private const val K_LAST_BATTERY = "last_battery"
        private const val K_LAST_LINE = "last_line"
        private const val K_LAST_CUT = "last_cut"
        /** 1.39 G5-B2: the start of a night not saved yet, and when the phone turned off during one. */
        private const val K_RUNNING = "running_start"
        private const val K_CUT = "cut_at"
        /** A night cut off longer ago than this is not news any more. */
        private const val CUT_SHOWN_MILLIS = 24 * 60 * 60 * 1000L
        private val TYPE = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0

        private val _recording = MutableStateFlow<Recording?>(null)
        val recording: StateFlow<Recording?> = _recording.asStateFlow()

        /** Between the microphone stopping and the night written down; a night cut off is not said meanwhile. */
        @Volatile private var saving = false
        private val _saved = MutableStateFlow(0L)
        /** When the last night was written down (the sleep screen reads [lastNight] again then, not before). */
        val saved: StateFlow<Long> = _saved.asStateFlow()

        /**
         * 1.39 G5-B2: the start of a night that was measuring and never got saved (the phone or the app was stopped
         * before it could), when not measuring now; null otherwise.
         */
        fun cutOff(context: Context): LocalDateTime? {
            if (_recording.value != null || saving) return null
            val at = prefs(context).getLong(K_RUNNING, 0L).takeIf { it > 0 && Today.millis() - it < CUT_SHOWN_MILLIS } ?: return null
            return local(at)
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun permitted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** The flag open (처리방침 새 판 뒤) and the microphone given. */
        fun allowed(context: Context): Boolean = context.appContainer.flags.on(FlagCatalog.SLEEP_SOUND) && permitted(context)

        /** 1.38 [SLEEP-05] The payload of the bedtime notice's 측정 시작, which opens the app and starts there. */
        const val FROM_NOTICE = "record"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, SleepRecordService::class.java))

        fun stop(context: Context) {
            if (_recording.value == null) return
            context.startService(Intent(context, SleepRecordService::class.java).setAction(ACTION_STOP))
        }

        /** The last measured night, for the morning card: when, the line, and the battery it took (-1 unknown). */
        data class LastNight(val start: LocalDateTime, val end: LocalDateTime, val line: String, val battery: Int,
            /** 1.39 G5-B2: the phone turned off, so the night ends there. */
            val cut: Boolean = false)

        fun lastNight(context: Context): LastNight? {
            val p = prefs(context)
            val start = p.getLong(K_LAST_START, 0L).takeIf { it > 0 } ?: return null
            return LastNight(local(start), local(p.getLong(K_LAST_END, start)), p.getString(K_LAST_LINE, "").orEmpty(), p.getInt(K_LAST_BATTERY, -1),
                p.getBoolean(K_LAST_CUT, false))
        }

        fun clip(context: Context, name: String): File = File(File(context.filesDir, CLIP_DIR), name)

        private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Today.zone())

        /** 8 kHz mono WAV from the 16 kHz ring (every second sample, after a 2-tap average). */
        internal fun writeWav(file: File, ring: ShortArray, from: Long, to: Long, rate: Int) {
            val out = ShortArray(((to - from) / 2).toInt())
            for (i in out.indices) {
                val a = ring[((from + 2 * i) % ring.size).toInt()].toInt()
                val b = ring[((from + 2 * i + 1) % ring.size).toInt()].toInt()
                out[i] = ((a + b) / 2).toShort()
            }
            val outRate = rate / 2
            RandomAccessFile(file, "rw").use { f ->
                f.setLength(0)
                val data = out.size * 2
                fun i32(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff); f.write(v shr 16 and 0xff); f.write(v shr 24 and 0xff) }
                fun i16(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff) }
                f.writeBytes("RIFF"); i32(36 + data); f.writeBytes("WAVE")
                f.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(outRate); i32(outRate * 2); i16(2); i16(16)
                f.writeBytes("data"); i32(data)
                val bytes = ByteArray(data)
                out.forEachIndexed { i, s -> bytes[2 * i] = (s.toInt() and 0xff).toByte(); bytes[2 * i + 1] = (s.toInt() shr 8 and 0xff).toByte() }
                f.write(bytes)
            }
        }
    }
}
