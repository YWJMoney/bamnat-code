package com.bamnat.core.recipe

/**
 * [MAT-03] 1.36 사진 조정·블렌드 (869, 2차 제안 18, Photoshop·Procreate): how a picture is drawn — 밝기·대비·채도 -100..100, 흑백, 색 입히기
 * (colour and how much 0..100), 흐림 0..25 dp (Android 12+), and how it meets what is under it ([BLENDS]). The picture itself is never
 * changed; the app draws it through a colour matrix. Written "b20,c-10,s30,g1,tFF5C6BC0,a40,u8,mmultiply" (each part once, any order).
 */
data class PhotoAdjust(
    val bright: Int = 0,
    val contrast: Int = 0,
    val saturate: Int = 0,
    val gray: Boolean = false,
    val tint: Long? = null,
    val tintAmount: Int = 0,
    val blur: Int = 0,
    val blend: String = "normal",
) {
    val isDefault: Boolean get() = this == PhotoAdjust()

    fun encode(): String = listOfNotNull(
        "b$bright".takeIf { bright != 0 }, "c$contrast".takeIf { contrast != 0 }, "s$saturate".takeIf { saturate != 0 }, "g1".takeIf { gray },
        tint?.let { "t" + Fill.hex(it) }, "a$tintAmount".takeIf { tint != null && tintAmount != 0 }, "u$blur".takeIf { blur != 0 },
        "m$blend".takeIf { blend != "normal" },
    ).joinToString(",")

    /**
     * The 4×5 colour matrix (Android's ColorMatrix order, row by row) that draws this adjustment: saturation (흑백 = 0), then contrast
     * about the middle grey, then brightness, then the tint mixed in by [tintAmount].
     */
    fun matrix(): FloatArray {
        val s = if (gray) 0f else 1f + saturate / 100f
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        val sat = floatArrayOf(
            lr * (1 - s) + s, lg * (1 - s), lb * (1 - s), 0f, 0f,
            lr * (1 - s), lg * (1 - s) + s, lb * (1 - s), 0f, 0f,
            lr * (1 - s), lg * (1 - s), lb * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val c = 1f + contrast / 100f
        val off = 128f * (1 - c) + bright * 1.28f
        val out = FloatArray(20)
        for (r in 0 until 3) {
            for (k in 0 until 3) out[r * 5 + k] = sat[r * 5 + k] * c
            out[r * 5 + 4] = off
        }
        out[18] = 1f
        val t = tint
        if (t != null && tintAmount > 0) {
            val a = tintAmount / 100f
            val ch = floatArrayOf(((t shr 16) and 0xFF).toFloat(), ((t shr 8) and 0xFF).toFloat(), (t and 0xFF).toFloat())
            for (r in 0 until 3) {
                for (k in 0 until 3) out[r * 5 + k] *= (1 - a)
                out[r * 5 + 4] = out[r * 5 + 4] * (1 - a) + ch[r] * a
            }
        }
        return out
    }

    companion object {
        /** normal plus Android's BlendMode names the app maps one to one. */
        val BLENDS = listOf("normal", "multiply", "screen", "overlay", "darken", "lighten", "softlight", "color", "luminosity")
        val BLEND_NAMES = mapOf(
            "normal" to "보통", "multiply" to "곱하기", "screen" to "스크린", "overlay" to "오버레이", "darken" to "어둡게", "lighten" to "밝게",
            "softlight" to "부드러운 빛", "color" to "색상", "luminosity" to "광도",
        )
        private val N100 = "(-?(100|[1-9]?[0-9]))"
        private val PART = Regex("^(b$N100|c$N100|s$N100|g1|t[0-9A-F]{8}|a(100|[1-9]?[0-9])|u(2[0-5]|1?[0-9])|m(${BLENDS.joinToString("|")}))$")

        fun problem(s: String): String? {
            if (s.isEmpty()) return null
            val parts = s.split(',')
            if (!parts.all { PART.matches(it) }) return "adjust"
            val keys = parts.map { it[0] }
            return if (keys.size != keys.distinct().size) "adjust" else null
        }

        fun decode(s: String?): PhotoAdjust {
            if (s.isNullOrEmpty() || problem(s) != null) return PhotoAdjust()
            val m = s.split(',').associate { it[0] to it.substring(1) }
            return PhotoAdjust(
                bright = m['b']?.toInt() ?: 0, contrast = m['c']?.toInt() ?: 0, saturate = m['s']?.toInt() ?: 0, gray = m['g'] == "1",
                tint = m['t']?.toLong(16), tintAmount = m['a']?.toInt() ?: 0, blur = m['u']?.toInt() ?: 0, blend = m['m'] ?: "normal",
            )
        }

        val PRESETS: List<Pair<String, PhotoAdjust>> = listOf(
            "원본" to PhotoAdjust(),
            "흑백" to PhotoAdjust(gray = true, contrast = 10),
            "빈티지" to PhotoAdjust(saturate = -35, contrast = -10, tint = 0xFFC8A165L, tintAmount = 25),
            "밤" to PhotoAdjust(bright = -25, saturate = -20, tint = 0xFF1A237EL, tintAmount = 30),
            "맑게" to PhotoAdjust(bright = 10, contrast = 15, saturate = 20),
            "부드럽게" to PhotoAdjust(blur = 8, bright = 8),
            "파스텔" to PhotoAdjust(bright = 20, contrast = -25, saturate = -15),
        )
    }
}

/**
 * [MAT-04] 1.36 사진 틀 모양 (870, 2차 제안 17, Canva 프레임): the shape a picture is cut to — 둥근 네모, 원, 별, 물방울, 하트, 육각형, 아치,
 * 구름, 꽃, 표, or the letters of a word ("t" + up to four letters' code points, "t-bc24-b0ae"). The app draws each from its own path.
 */
object PhotoFrame {
    val SHAPES = listOf("rect", "round", "circle", "star", "drop", "heart", "hexagon", "arch", "cloud", "flower", "ticket")
    val NAMES = mapOf(
        "rect" to "네모", "round" to "둥근 네모", "circle" to "원", "star" to "별", "drop" to "물방울", "heart" to "하트", "hexagon" to "육각형",
        "arch" to "아치", "cloud" to "구름", "flower" to "꽃", "ticket" to "표",
    )
    private val GRAMMAR = Regex("^(${SHAPES.joinToString("|")}|t(-[0-9a-f]{2,5}){1,4})$")

    fun problem(s: String): String? = if (s.isEmpty() || GRAMMAR.matches(s)) null else "frame"

    /** The word a letters frame cuts, or null for a shape. */
    fun letters(s: String?): String? = s?.takeIf { it.startsWith("t-") && problem(it) == null }
        ?.let { w -> runCatching { w.removePrefix("t-").split('-').map { it.toInt(16) }.let { String(it.toIntArray(), 0, it.size) } }.getOrNull() }

    /** A letters frame for [word] (its first four letters, spaces left out), or null when nothing is left. */
    fun ofLetters(word: String): String? = word.filterNot { it.isWhitespace() }.codePoints().toArray().take(4)
        .takeIf { it.isNotEmpty() }?.joinToString("-", prefix = "t-") { it.toString(16) }
}
