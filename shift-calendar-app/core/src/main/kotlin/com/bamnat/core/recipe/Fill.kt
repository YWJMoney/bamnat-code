package com.bamnat.core.recipe

import kotlin.math.roundToInt

/**
 * [MAT-01] 1.36 채우기 (스튜디오 완전 자유도 867, 2차 조사 5-4 "제공할 수 있는 최대", 사용자 2026-11-01 "채우기(그라데이션·무늬·질감 등)
 * 제공 가능한 최대"): what fills a part — 단색, 선형·방사형·원뿔형 그라데이션, 무늬, 질감, 유리, 움직이는 셰이더 — as a value the app's own
 * painter reads (결정 D106: values only, no picture address, no code; a shader is one the app ships, the maker gives only colours and speed).
 *
 * Written in one short word with only `. , @` and letters and digits, so it sits inside every older look line (`|`, `/`, `~`, `;`, `=`,
 * `:` stay theirs) and inside branch A's part blueprint style slot unchanged:
 * - `so.FF112233` 단색 (alpha kept)
 * - `lg.135.FF112233,FF445566@60,FF778899` 선형: angle 0..359 (0 위→아래, 45 ↘, 90 왼→오른, 135 ↗, 180 아래→위), 2..5 stops, each colour with an optional place 0..100
 * - `rg.50.40.80.<stops>` 방사형: centre x·y 0..100 %, radius 10..150 % of the shorter side
 * - `sg.50.50.0.<stops>` 원뿔형: centre and the turn 0..359 where it starts
 * - `pt.dots.FF000000.00000000.100.0` 무늬: kind ([PATTERNS]), ink, ground, size 50..300 %, angle 0..359
 * - `tx.paper.FFF5EBDD.40` 질감: kind ([TEXTURES]), tint, strength 0..100
 * - `gl.66FFFFFF.16` 유리: tint (its alpha is how much shows through), blur 0..40 dp (Android 12+; under it the tint alone)
 * - `sh.aurora.FF1A237E,FF00BFA5,FFFF4081.3` 셰이더: kind ([SHADERS]), 2..3 colours, speed 0..10 (0 still; Android 13+, under it a still
 *   gradient of the colours)
 *
 * The server reads the same words (server/company-worker/src/recipes.js `fillProblem`); `docs/recipe-cases.json` "fills" holds the two together.
 */
sealed interface Fill {
    fun encode(): String

    /** One colour that stands for the fill, for reading text on it (WCAG) and for older screens that draw one colour. */
    val average: Long

    data class Solid(val color: Long) : Fill {
        override fun encode() = "so." + hex(color)
        override val average get() = color
    }

    /** A colour and where it sits along the gradient (0..100), or null to share the room evenly. */
    data class Stop(val color: Long, val at: Int? = null) {
        fun encode() = hex(color) + (at?.let { "@$it" } ?: "")
    }

    sealed interface Gradient : Fill {
        val stops: List<Stop>
        override val average: Long get() = mix(stops.map { it.color })

        /** Each stop's place 0..1: given ones as they are, the rest spread evenly between their neighbours; always rising. */
        fun places(): List<Float> {
            val n = stops.size
            val raw = stops.mapIndexed { i, s -> s.at?.div(100f) ?: if (i == 0) 0f else if (i == n - 1) 1f else Float.NaN }.toMutableList()
            var i = 0
            while (i < n) {
                if (!raw[i].isNaN()) { i++; continue }
                val a = i - 1
                var b = i
                while (raw[b].isNaN()) b++
                for (k in i until b) raw[k] = raw[a] + (raw[b] - raw[a]) * (k - a) / (b - a)
                i = b
            }
            for (k in 1 until n) if (raw[k] < raw[k - 1]) raw[k] = raw[k - 1]
            return raw
        }
    }

    data class Linear(val angle: Int, override val stops: List<Stop>) : Gradient {
        override fun encode() = "lg.$angle." + stops.joinToString(",") { it.encode() }
    }

    data class Radial(val cx: Int, val cy: Int, val radius: Int, override val stops: List<Stop>) : Gradient {
        override fun encode() = "rg.$cx.$cy.$radius." + stops.joinToString(",") { it.encode() }
    }

    data class Sweep(val cx: Int, val cy: Int, val turn: Int, override val stops: List<Stop>) : Gradient {
        override fun encode() = "sg.$cx.$cy.$turn." + stops.joinToString(",") { it.encode() }
    }

    data class Pattern(val kind: String, val ink: Long, val ground: Long, val size: Int = 100, val angle: Int = 0) : Fill {
        override fun encode() = "pt.$kind.${hex(ink)}.${hex(ground)}.$size.$angle"
        /** A pattern's ink covers about a fifth of it. */
        override val average get() = mixWeighted(listOf(ground to 0.8, ink to 0.2))
    }

    data class Texture(val kind: String, val tint: Long, val strength: Int = 40) : Fill {
        override fun encode() = "tx.$kind.${hex(tint)}.$strength"
        override val average get() = tint
    }

    data class Glass(val tint: Long, val blur: Int = 16) : Fill {
        override fun encode() = "gl.${hex(tint)}.$blur"
        override val average get() = tint
    }

    data class Shader(val kind: String, val colors: List<Long>, val speed: Int = 3) : Fill {
        override fun encode() = "sh.$kind." + colors.joinToString(",") { hex(it) } + ".$speed"
        override val average get() = mix(colors)
        /** What older phones (under Android 13) and 움직임 끄기 draw: a still diagonal gradient of the same colours. */
        fun still(): Linear = Linear(135, colors.map { Stop(it) })
    }

    companion object {
        /** 무늬 (5-4): the app draws each as a small tile repeated. */
        val PATTERNS = listOf(
            "dots", "bigdots", "stripes", "thin", "check", "grid", "wave", "zigzag", "heart", "star", "cross", "diamond", "brick",
            "scale", "plaid", "confetti",
        )
        val PATTERN_NAMES = mapOf(
            "dots" to "점", "bigdots" to "큰 점", "stripes" to "줄무늬", "thin" to "가는 줄", "check" to "체크", "grid" to "격자",
            "wave" to "물결", "zigzag" to "지그재그", "heart" to "하트", "star" to "별", "cross" to "십자", "diamond" to "마름모",
            "brick" to "벽돌", "scale" to "비늘", "plaid" to "타탄", "confetti" to "색종이",
        )

        /** 질감: the app makes each tile itself from a fixed seed (no picture in the app or the recipe). */
        val TEXTURES = listOf("paper", "linen", "noise", "grain", "canvas", "kraft", "felt", "concrete")
        val TEXTURE_NAMES = mapOf(
            "paper" to "종이", "linen" to "리넨", "noise" to "노이즈", "grain" to "필름 그레인", "canvas" to "캔버스", "kraft" to "크라프트지",
            "felt" to "펠트", "concrete" to "콘크리트",
        )

        /** 셰이더: shipped in the app (AGSL); the maker picks one and its colours and speed, nothing else. */
        val SHADERS = listOf("aurora", "mesh", "ripple", "plasma", "flow", "sunset")
        val SHADER_NAMES = mapOf(
            "aurora" to "오로라", "mesh" to "메시 그라데이션", "ripple" to "물결", "plasma" to "플라스마", "flow" to "흐르는 색", "sunset" to "노을",
        )

        const val MAX_STOPS = 5
        const val MAX_LEN = 80

        private val HEX = "[0-9A-F]{8}"
        private val DEG = "(3[0-5][0-9]|[12]?[0-9]?[0-9])"
        private val PCT = "(100|[1-9]?[0-9])"
        private val STOP = "$HEX(@$PCT)?"
        private val STOPS = "$STOP(,$STOP){1,4}"
        private val SIZE = "(300|[12][0-9][0-9]|[5-9][0-9])"
        private val RAD = "(150|1[0-4][0-9]|[1-9][0-9])"
        private val BLUR = "(40|[1-3]?[0-9])"
        private val SPEED = "(10|[0-9])"

        /** The whole grammar in one: the server's `FILL` is the same pattern. */
        val GRAMMAR = Regex(
            "^(so\\.$HEX|lg\\.$DEG\\.$STOPS|rg\\.$PCT\\.$PCT\\.$RAD\\.$STOPS|sg\\.$PCT\\.$PCT\\.$DEG\\.$STOPS|" +
                "pt\\.(${PATTERNS.joinToString("|")})\\.$HEX\\.$HEX\\.$SIZE\\.$DEG|tx\\.(${TEXTURES.joinToString("|")})\\.$HEX\\.$PCT|" +
                "gl\\.$HEX\\.$BLUR|sh\\.(${SHADERS.joinToString("|")})\\.$HEX(,$HEX){1,2}\\.$SPEED)$",
        )

        /** Null passes, else "fill". */
        fun problem(s: String): String? = if (s.length <= MAX_LEN && GRAMMAR.matches(s)) null else "fill"

        /** The fill [s] writes, or null when it is not one (an older app, a typo: the part is the app's own). */
        fun decode(s: String?): Fill? {
            if (s == null || problem(s) != null) return null
            val p = s.split('.')
            fun stops(x: String) = x.split(',').map { st -> Stop(color(st.substringBefore('@')), st.substringAfter('@', "").toIntOrNull()) }
            return when (p[0]) {
                "so" -> Solid(color(p[1]))
                "lg" -> Linear(p[1].toInt(), stops(p[2]))
                "rg" -> Radial(p[1].toInt(), p[2].toInt(), p[3].toInt(), stops(p[4]))
                "sg" -> Sweep(p[1].toInt(), p[2].toInt(), p[3].toInt(), stops(p[4]))
                "pt" -> Pattern(p[1], color(p[2]), color(p[3]), p[4].toInt(), p[5].toInt())
                "tx" -> Texture(p[1], color(p[2]), p[3].toInt())
                "gl" -> Glass(color(p[1]), p[2].toInt())
                "sh" -> Shader(p[1], p[2].split(',').map(::color), p[3].toInt())
                else -> null
            }
        }

        /** Starting fills the picker offers (Canva·파워포인트처럼 고르고 나서 고치기). */
        val PRESETS: List<Pair<String, Fill>> = listOf(
            "새벽" to Linear(180, listOf(Stop(0xFF1A237EL), Stop(0xFF5C6BC0L), Stop(0xFFFFCCBCL))),
            "복숭아" to Linear(135, listOf(Stop(0xFFFFE0B2L), Stop(0xFFF8BBD0L))),
            "바다" to Linear(160, listOf(Stop(0xFF006064L), Stop(0xFF4DD0E1L))),
            "숲" to Linear(200, listOf(Stop(0xFF1B5E20L), Stop(0xFFA5D6A7L))),
            "밤하늘" to Radial(50, 20, 120, listOf(Stop(0xFF3949ABL), Stop(0xFF0D1333L))),
            "햇살" to Radial(80, 10, 100, listOf(Stop(0xFFFFF59DL), Stop(0xFFFFFFFFL))),
            "무지개" to Sweep(50, 50, 0, listOf(Stop(0xFFE53935L), Stop(0xFFFDD835L), Stop(0xFF43A047L), Stop(0xFF1E88E5L), Stop(0xFF8E24AAL))),
            "물방울" to Pattern("dots", 0x33000000L, 0xFFFFFFFFL),
            "체크" to Pattern("check", 0x1F3F51B5L, 0xFFFFFFFFL, 160),
            "모눈" to Pattern("grid", 0x26000000L, 0xFFFFFDF5L),
            "하트" to Pattern("heart", 0x40E91E63L, 0xFFFFF0F5L),
            "종이" to Texture("paper", 0xFFF7F1E3L, 45),
            "리넨" to Texture("linen", 0xFFEDE7DCL, 50),
            "유리" to Glass(0x66FFFFFFL, 18),
            "오로라" to Shader("aurora", listOf(0xFF0D47A1L, 0xFF00BFA5L, 0xFF7C4DFFL), 3),
        )

        /** [MAT-01] 근무와 함께 (S8): 밤하늘 for a night, the daytime one otherwise. */
        fun forRole(role: String?, day: Fill?, night: Fill?): Fill? = if (role == "night" && night != null) night else day

        internal fun hex(c: Long) = (c and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        private fun color(s: String): Long = s.toLong(16)

        /** The plain average of [cs], alpha too. */
        fun mix(cs: List<Long>): Long = mixWeighted(cs.map { it to 1.0 })

        private fun mixWeighted(cs: List<Pair<Long, Double>>): Long {
            val total = cs.sumOf { it.second }.takeIf { it > 0 } ?: return 0L
            fun ch(shift: Int) = (cs.sumOf { ((it.first shr shift) and 0xFF) * it.second } / total).roundToInt().coerceIn(0, 255).toLong()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/**
 * [MAT-02] 1.36 선 (868, 1차 제안 4 "선 두께 0.5~4dp, 투명도"): a part's edge — its shape ([STYLES]), width in tenths of a dp (5..40),
 * colour and how strong it is (0..100). Written "solid.15.FF000000.80".
 */
data class Stroke(val style: String = "solid", val width10: Int = 10, val color: Long = 0xFF000000L, val alpha: Int = 100) {
    val widthDp: Float get() = width10 / 10f

    fun encode() = "$style.$width10.${Fill.hex(color)}.$alpha"

    companion object {
        val STYLES = listOf("solid", "dash", "dot", "double")
        val STYLE_NAMES = mapOf("solid" to "실선", "dash" to "긴 점선", "dot" to "점선", "double" to "이중선")
        val GRAMMAR = Regex("^(solid|dash|dot|double)\\.([5-9]|[1-3][0-9]|40)\\.[0-9A-F]{8}\\.(100|[1-9]?[0-9])$")

        fun problem(s: String): String? = if (GRAMMAR.matches(s)) null else "stroke"

        fun decode(s: String?): Stroke? {
            if (s == null || problem(s) != null) return null
            val p = s.split('.')
            return Stroke(p[0], p[1].toInt(), p[2].toLong(16), p[3].toInt())
        }
    }
}
