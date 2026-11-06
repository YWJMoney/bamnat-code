package com.bamnat.core.sleep

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 1.38 [SLEEP-04] 잠드는 소리, made by code rather than played from recordings: nothing to download, nothing licensed,
 * and the app does not grow. Each sound is a small filter over random numbers, so a night of it is the same few
 * kilobytes of code. The idea (a mixer of steady sounds with a timer) came from public store pages and reviews
 * (사용자 관찰/공개 정보); the sounds and the way they are made are our own.
 */
enum class Noise(val key: String, val label: String) {
    WHITE("white", "백색 소음"),
    PINK("pink", "분홍 소음"),
    BROWN("brown", "갈색 소음"),
    RAIN("rain", "빗소리"),
    FAN("fan", "선풍기"),
    WAVES("waves", "파도");

    companion object {
        fun of(key: String): Noise? = entries.firstOrNull { it.key == key }
    }
}

/** One mix: how loud each sound is, 0..1. Read from and written to settings as "rain:0.6,brown:0.3". */
data class NoiseMix(val levels: Map<Noise, Float>) {
    val isSilent: Boolean get() = levels.values.all { it <= 0f }
    val playing: List<Noise> get() = Noise.entries.filter { (levels[it] ?: 0f) > 0f }

    fun with(noise: Noise, level: Float): NoiseMix = NoiseMix(levels + (noise to level.coerceIn(0f, 1f)))

    fun encode(): String = Noise.entries.filter { (levels[it] ?: 0f) > 0f }.joinToString(",") { "${it.key}:${"%.2f".format(java.util.Locale.ROOT, levels[it])}" }

    companion object {
        /** A first mix that suits sleeping by day: brown under rain. */
        val DEFAULT = NoiseMix(mapOf(Noise.BROWN to 0.5f, Noise.RAIN to 0.35f))

        fun decode(text: String?): NoiseMix {
            if (text.isNullOrBlank()) return DEFAULT
            val map = text.split(',').mapNotNull { part ->
                val kv = part.split(':')
                val n = Noise.of(kv.getOrNull(0)?.trim() ?: return@mapNotNull null) ?: return@mapNotNull null
                val v = kv.getOrNull(1)?.trim()?.toFloatOrNull()?.coerceIn(0f, 1f) ?: return@mapNotNull null
                n to v
            }.toMap()
            return if (map.isEmpty()) DEFAULT else NoiseMix(map)
        }
    }
}

/**
 * Makes the samples, -1..1, a block at a time. Not thread safe: one player owns one synth. The same [seed] gives the
 * same sound, which is what the tests lean on.
 */
class NoiseSynth(val sampleRate: Int = SAMPLE_RATE, seed: Long = 0x5EED) {
    private val rnd = java.util.Random(seed)

    // Paul Kellet's economy pink filter, run on white noise.
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private fun pink(w: Double): Double {
        b0 = 0.99765 * b0 + w * 0.0990460
        b1 = 0.96300 * b1 + w * 0.2965164
        b2 = 0.57000 * b2 + w * 1.0526913
        return (b0 + b1 + b2 + w * 0.1848) * 0.11
    }

    // A leaky sum of white noise: most of its loudness sits low.
    private var brownState = 0.0
    private fun brown(w: Double): Double {
        brownState = (brownState + w * 0.02) * 0.998
        return brownState * 1.3
    }

    // Rain: a soft pink bed and drops that start at random and die away in a few milliseconds.
    private var dropLevel = 0.0
    private var dropDecay = 0.0
    private var rainHp = 0.0
    private fun rain(w: Double): Double {
        if (rnd.nextDouble() < DROPS_PER_SECOND / sampleRate) {
            dropLevel = 0.25 + rnd.nextDouble() * 0.55
            dropDecay = exp(-1.0 / (sampleRate * (0.002 + rnd.nextDouble() * 0.006)))
        }
        val hp = w - rainHp
        rainHp = w
        val drop = hp * dropLevel
        dropLevel *= dropDecay
        return 0.55 * rainPinkBed(w) + 0.7 * drop
    }
    private var bedB0 = 0.0; private var bedB1 = 0.0; private var bedB2 = 0.0
    private fun rainPinkBed(w: Double): Double {
        bedB0 = 0.99765 * bedB0 + w * 0.0990460
        bedB1 = 0.96300 * bedB1 + w * 0.2965164
        bedB2 = 0.57000 * bedB2 + w * 1.0526913
        return (bedB0 + bedB1 + bedB2 + w * 0.1848) * 0.11
    }

    // Fan: low rumble, a little hum from the motor and a slow swell of the blades.
    private var fanLow = 0.0
    private var fanLow2 = 0.0
    private fun fan(w: Double, t: Double): Double {
        fanLow = 0.92 * fanLow + 0.08 * w
        fanLow2 = 0.92 * fanLow2 + 0.08 * fanLow
        val swell = 1.0 + 0.08 * sin(2 * PI * 0.6 * t)
        return (fanLow2 * 2.5 + 0.03 * sin(2 * PI * 110.0 * t)) * swell
    }

    // Waves: the sea comes in about every 9 seconds and draws back.
    private var waveB0 = 0.0; private var waveB1 = 0.0; private var waveB2 = 0.0
    private var waveBrown = 0.0
    private fun waves(w: Double, t: Double): Double {
        waveB0 = 0.99765 * waveB0 + w * 0.0990460
        waveB1 = 0.96300 * waveB1 + w * 0.2965164
        waveB2 = 0.57000 * waveB2 + w * 1.0526913
        val p = (waveB0 + waveB1 + waveB2 + w * 0.1848) * 0.11
        waveBrown = (waveBrown + w * 0.02) * 0.998
        val phase = sin(2 * PI * t / WAVE_SECONDS)
        val swell = 0.15 + 0.85 * (0.5 + 0.5 * phase).let { it * it }
        return p * swell + waveBrown * 1.5 * 0.4
    }

    private var sample = 0L

    /** Fills [out] with the next samples of [mix], each sound at its level, scaled by [gain]. */
    fun render(mix: NoiseMix, out: FloatArray, gain: Float = 1f) {
        val lw = mix.levels[Noise.WHITE] ?: 0f
        val lp = mix.levels[Noise.PINK] ?: 0f
        val lb = mix.levels[Noise.BROWN] ?: 0f
        val lr = mix.levels[Noise.RAIN] ?: 0f
        val lf = mix.levels[Noise.FAN] ?: 0f
        val lv = mix.levels[Noise.WAVES] ?: 0f
        for (i in out.indices) {
            val t = sample.toDouble() / sampleRate
            sample++
            var v = 0.0
            // One random draw per sound keeps each one independent of which others are on.
            val w1 = rnd.nextDouble() * 2 - 1
            val w2 = rnd.nextDouble() * 2 - 1
            val w3 = rnd.nextDouble() * 2 - 1
            val w4 = rnd.nextDouble() * 2 - 1
            val w5 = rnd.nextDouble() * 2 - 1
            val w6 = rnd.nextDouble() * 2 - 1
            val pk = pink(w2)
            val br = brown(w3)
            val rn = rain(w4)
            val fn = fan(w5, t)
            val wv = waves(w6, t)
            v += lw * w1 * 0.4 + lp * pk + lb * br + lr * rn + lf * fn + lv * wv
            out[i] = (v * gain * MASTER).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    /** Fills [out] with 16-bit samples, for an AudioTrack. */
    fun render16(mix: NoiseMix, out: ShortArray, gain: Float = 1f, scratch: FloatArray = FloatArray(out.size)) {
        render(mix, scratch, gain)
        for (i in out.indices) out[i] = (scratch[i] * Short.MAX_VALUE).toInt().toShort()
    }

    companion object {
        const val SAMPLE_RATE = 22_050
        const val DROPS_PER_SECOND = 140.0
        const val WAVE_SECONDS = 9.0
        const val MASTER = 0.6
    }
}

/**
 * [SLEEP-04] The timer: the sound plays at full level until the last [fadeMillis], then fades to nothing, so the end
 * does not wake the user it put to sleep. [totalMillis] 0 plays until stopped.
 */
object NoiseTimer {
    const val FADE_MILLIS = 90_000L
    val CHOICES_MINUTES = listOf(15, 30, 60, 120, 0)

    fun gain(elapsedMillis: Long, totalMillis: Long, fadeMillis: Long = FADE_MILLIS): Float {
        if (totalMillis <= 0) return 1f
        val left = totalMillis - elapsedMillis
        if (left <= 0) return 0f
        val fade = fadeMillis.coerceAtMost(totalMillis)
        if (left >= fade) return 1f
        val x = left.toDouble() / fade
        return (x * x).toFloat()
    }

    fun label(minutes: Int): String = when {
        minutes <= 0 -> "계속"
        minutes % 60 == 0 -> "${minutes / 60}시간"
        else -> "${minutes}분"
    }
}
