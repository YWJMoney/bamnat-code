package com.bamnat.app.recipe

/**
 * 1.39 [MKD-45] 테마 분위기 (V7, 사용자 2026-11-07 대량 제작 "테마는 50개정도"): 사람은 "어두운 거", "파스텔"로 찾아요. 목록이 보내는 테마 색
 * ([ThemeSwatch])으로 이 폰에서 셈해요. 서버는 바꾸지 않아요. 한 테마가 여러 분위기일 수 있어요(어두운 + 선명한).
 */
object MarketMood {
    enum class Mood(val label: String) { LIGHT("밝은"), DARK("어두운"), PASTEL("파스텔"), VIVID("선명한"), CALM("차분한") }

    fun of(look: ThemeSwatch?): Set<Mood> {
        if (look == null || look.example) return emptySet()
        val out = mutableSetOf<Mood>()
        look.bg?.let { if (luminance(it) < 0.3) out += Mood.DARK else if (luminance(it) > 0.6) out += Mood.LIGHT }
        val colors = (look.days.mapNotNull { it.color } + listOfNotNull(look.accent, look.today)).map(::hsl)
        if (colors.isEmpty()) return out
        val s = colors.map { it.second }.average()
        val l = colors.map { it.third }.average()
        when {
            l >= 0.7 && s >= 0.2 -> out += Mood.PASTEL
            s >= 0.6 && l in 0.3..0.68 -> out += Mood.VIVID
            s < 0.35 -> out += Mood.CALM
        }
        return out
    }

    private fun channels(c: Long): Triple<Double, Double, Double> =
        Triple(((c shr 16) and 0xFF) / 255.0, ((c shr 8) and 0xFF) / 255.0, (c and 0xFF) / 255.0)

    private fun luminance(c: Long): Double {
        fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        val (r, g, b) = channels(c)
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /** Hue (unused), saturation and lightness, 0..1. */
    private fun hsl(c: Long): Triple<Double, Double, Double> {
        val (r, g, b) = channels(c)
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val l = (max + min) / 2
        val s = if (max == min) 0.0 else (max - min) / (1 - Math.abs(2 * l - 1))
        return Triple(0.0, s.coerceIn(0.0, 1.0), l)
    }
}
