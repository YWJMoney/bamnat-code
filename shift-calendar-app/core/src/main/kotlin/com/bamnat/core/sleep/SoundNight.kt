package com.bamnat.core.sleep

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 1.38 [SLEEP-06][SLEEP-07] 정밀 측정, the part that hears: everything here runs on the phone, on 30 ms frames of the
 * microphone, and keeps only times, kinds and loudness (and short clips the app may keep, never sent).
 *
 * The first version sorts sounds by plain rules (loudness above the room, how long, how low, how noisy, how they repeat),
 * not by a model; a downloadable classifier can replace [SoundRules] later behind the same [SoundEvent].
 */
enum class SoundKind(val key: String, val label: String) {
    SNORE("snore", "코골이"),
    TALK("talk", "잠꼬대"),
    COUGH("cough", "기침"),
    GRIND("grind", "이갈이"),
    MOVE("move", "뒤척임"),
    OTHER("other", "다른 소리");

    companion object {
        fun of(key: String?): SoundKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** What one 30 ms frame says: how loud (dBFS), how much is low (under ~500 Hz, 0..1), how noisy (zero crossings, 0..1). */
data class SoundFrame(val db: Float, val low: Float, val zcr: Float)

/** One heard sound, from [startMillis] to [endMillis] after the measuring began. */
data class SoundEvent(val startMillis: Long, val endMillis: Long, val kind: SoundKind, val peakOverRoom: Float) {
    val millis: Long get() = endMillis - startMillis
}

object SoundFrames {
    const val FRAME_MILLIS = 30L

    /** Features of one frame of 16-bit samples. [lowState] carries the low-pass filter between frames. */
    fun of(samples: ShortArray, count: Int = samples.size, lowState: FloatArray = FloatArray(1), sampleRate: Int = 16_000): SoundFrame {
        if (count == 0) return SoundFrame(-96f, 0f, 0f)
        // One-pole low pass near 500 Hz.
        val a = (2 * Math.PI * 500 / sampleRate).toFloat().let { it / (it + 1f) }
        var sum = 0.0; var lowSum = 0.0; var crossings = 0
        var lp = lowState[0]
        var prev = samples[0]
        for (i in 0 until count) {
            val x = samples[i] / 32768f
            sum += x * x
            lp += a * (x - lp)
            lowSum += lp * lp
            if (i > 0 && (samples[i] >= 0) != (prev >= 0)) crossings++
            prev = samples[i]
        }
        lowState[0] = lp
        val rms = sqrt(sum / count)
        val db = if (rms <= 1e-6) -96f else (20 * log10(rms)).toFloat()
        return SoundFrame(db, if (sum <= 0) 0f else (lowSum / sum).toFloat().coerceIn(0f, 1f), crossings.toFloat() / count)
    }
}

/**
 * Streams frames into [SoundEvent]s. The room's own level follows slowly (a fan, our own sleep sound, rain), so only what
 * stands out of it is heard; a quiet room costs one comparison a frame ([quiet]), which is where the battery is saved.
 */
class SoundDetector(private val onEvent: (SoundEvent) -> Unit) {
    private var room = -60f
    private var t = 0L
    private var start = -1L
    private var lastLoud = -1L
    private var peak = 0f
    private val frames = ArrayList<SoundFrame>()
    private val recent = ArrayDeque<SoundEvent>()

    /** True while nothing stands out: the caller may keep only cheap work going. */
    val quiet: Boolean get() = start < 0

    /** The room's level now (dBFS), for the summary. */
    val roomDb: Float get() = room

    fun push(f: SoundFrame) {
        val over = f.db - room
        if (over >= SoundRules.START_DB) {
            if (start < 0) { start = t; peak = 0f; frames.clear() }
            lastLoud = t
        } else if (start < 0) {
            // Follow the room, faster down than up so one loud night does not deafen the rest.
            room += if (f.db < room) (f.db - room) * 0.05f else (f.db - room) * 0.002f
        }
        if (start >= 0) {
            frames += f
            peak = max(peak, over)
            if (t - lastLoud > SoundRules.HANG_MILLIS || t - start > SoundRules.MAX_EVENT_MILLIS) close()
        }
        t += SoundFrames.FRAME_MILLIS
    }

    /** Ends what is open, at the end of the measuring. */
    fun finish() { if (start >= 0) close() }

    private fun close() {
        val from = start
        val end = lastLoud + SoundFrames.FRAME_MILLIS
        val loud = frames.filter { it.db - room >= SoundRules.START_DB - 3 }
        val kind = SoundRules.kind(end - from, peak, loud, recent.toList(), from)
        // Ten seconds without a pause is no event but the room now (a fan turned on, our own sleep sound, rain).
        if (end - from >= SoundRules.MAX_EVENT_MILLIS) room = frames.map { it.db }.sorted()[frames.size / 2] - 1f
        start = -1
        frames.clear()
        if (kind != null) emit(SoundEvent(from, end, kind, peak))
    }

    private fun emit(e: SoundEvent) {
        recent.addLast(e)
        while (recent.size > 8) recent.removeFirst()
        onEvent(e)
    }
}

/** The plain rules that name a sound. Numbers are first guesses, tuned on the PC emulator and real nights. */
object SoundRules {
    /** A sound starts when it is this much over the room. */
    const val START_DB = 10f
    /** And ends after this long under it. */
    const val HANG_MILLIS = 300L
    /** Longer than this is cut into the next event. */
    const val MAX_EVENT_MILLIS = 10_000L

    fun kind(millis: Long, peak: Float, loud: List<SoundFrame>, before: List<SoundEvent>, at: Long): SoundKind? {
        if (loud.isEmpty() || millis < 120) return null
        val low = loud.map { it.low }.average().toFloat()
        val zcr = loud.map { it.zcr }.average().toFloat()
        val spread = loud.maxOf { it.db } - loud.minOf { it.db }
        val repeats = { k: SoundKind?, within: Long ->
            before.any { (k == null || it.kind == k) && at - it.endMillis in 0..within }
        }
        return when {
            // Low, smooth and a breath long; a second like it within a few breaths makes it snoring.
            low > 0.6f && zcr < 0.08f && millis in 300..3_500 -> SoundKind.SNORE
            // Short, loud and broad.
            millis in 120..900 && peak >= 18f && low < 0.55f && zcr >= 0.08f ->
                if (millis < 300 && repeats(SoundKind.GRIND, 1_500) || millis < 250 && zcr > 0.25f) SoundKind.GRIND else SoundKind.COUGH
            // Voiced and uneven: syllables.
            millis in 500..10_000 && low in 0.25f..0.7f && zcr in 0.04f..0.2f && spread >= 8f -> SoundKind.TALK
            // Soft and noisy: sheets and turning over.
            peak < 18f && zcr >= 0.12f -> SoundKind.MOVE
            else -> SoundKind.OTHER
        }
    }
}

/**
 * Which clips to keep from a night: spread over the hours (not all from one loud hour), loudest of each kind first, at most
 * [perHour] an hour and [max] a night. The counts and times of every event are kept apart from this.
 */
object ClipPicker {
    fun pick(events: List<SoundEvent>, hourMillis: Long = 3_600_000, perHour: Int = 3, max: Int = 30): List<SoundEvent> =
        events.filter { it.kind != SoundKind.MOVE && it.kind != SoundKind.OTHER }
            .groupBy { it.startMillis / hourMillis }
            .flatMap { (_, inHour) ->
                // One of each kind first, then the loudest.
                val byKind = inHour.groupBy { it.kind }.values.map { l -> l.maxBy { it.peakOverRoom } }.sortedByDescending { it.peakOverRoom }
                (byKind + inHour.sortedByDescending { it.peakOverRoom }).distinct().take(perHour)
            }
            .sortedByDescending { it.peakOverRoom }
            .take(max)
            .sortedBy { it.startMillis }

    /** "들어 볼 소리 3개" on the morning card: different kinds first, the loudest of each. */
    fun listen(events: List<SoundEvent>, n: Int = 3): List<SoundEvent> {
        val withClips = events.filter { it.kind != SoundKind.MOVE && it.kind != SoundKind.OTHER }
        val firsts = withClips.groupBy { it.kind }.values.map { l -> l.maxBy { it.peakOverRoom } }.sortedByDescending { it.peakOverRoom }
        return (firsts + withClips.sortedByDescending { it.peakOverRoom }).distinct().take(n)
    }
}

/** The night in numbers for the morning card: no score, counts and minutes the user can check against the clips. */
data class SoundSummary(val counts: Map<SoundKind, Int>, val snoreMinutes: Long, val asleepAfterMillis: Long?) {
    fun line(): String {
        val parts = SoundKind.entries.filter { it != SoundKind.OTHER && (counts[it] ?: 0) > 0 }.map { k ->
            if (k == SoundKind.SNORE) "코골이 ${snoreMinutes}분" else "${k.label} ${counts[k]}번"
        }
        return parts.joinToString(" · ").ifEmpty { "조용한 밤이었어요" }
    }

    companion object {
        /** Snoring counted as minutes: events closer than 2 minutes are one stretch. */
        fun of(events: List<SoundEvent>, asleepAfterMillis: Long? = null): SoundSummary {
            val counts = events.groupingBy { it.kind }.eachCount()
            var minutes = 0L
            var from = -1L; var to = -1L
            for (e in events.filter { it.kind == SoundKind.SNORE }.sortedBy { it.startMillis }) {
                if (from >= 0 && e.startMillis - to <= 120_000) to = e.endMillis
                else { if (from >= 0) minutes += (to - from); from = e.startMillis; to = e.endMillis }
            }
            if (from >= 0) minutes += (to - from)
            return SoundSummary(counts, (minutes + 59_999) / 60_000, asleepAfterMillis)
        }
    }
}

/**
 * [SLEEP-06] 측정 시작 뒤 실제 잠든 때: the start of the first [quietMinutes] with no movement (phone still and no rustle)
 * after the start. Per minute: true when something moved. Null while it never settled.
 */
object SleepOnset {
    fun minuteAsleep(moved: List<Boolean>, quietMinutes: Int = 15): Int? {
        var run = 0
        moved.forEachIndexed { i, m ->
            if (m) run = 0 else if (++run >= quietMinutes) return i - quietMinutes + 1
        }
        return null
    }
}
