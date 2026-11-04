package com.bamnat.core.recipe

import com.bamnat.core.formula.Value
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * [RUL-02] A formula's answer as a part's style field takes it (`{"fx":…}` in 부품 꾸미기): a colour from a word ("빨강"), "#RRGGBB" or a
 * number; a number kept in the field's range; a choice only when it is one of the field's; 켜기·끄기 from 참·거짓. 없음 or an answer the
 * field cannot take is null: the field stays as the lines under it drew it.
 */
object FormulaValues {
    fun forField(key: String, v: Value): Any? {
        if (v is Value.None) return null
        val f = BlueprintParts.STYLE_FIELDS.firstOrNull { it.key == key } ?: return null
        return when (f) {
            is Field.Color -> color(v)
            is Field.Num -> v.number?.let { n ->
                val m = 10.0.pow(f.decimals)
                val c = n.coerceIn(f.lo, f.hi)
                if (f.decimals == 0) c.roundToLong() else (c * m).roundToLong() / m
            }
            is Field.Pick -> v.text.takeIf { it in f.values } ?: PICK_WORDS[v.text]?.takeIf { it in f.values }
            is Field.Flag -> v.truthy
            is Field.Text -> v.text.takeIf { it.isNotEmpty() && f.check?.invoke(it) == null }
            else -> null
        }
    }

    /** The colour a formula means, or null. */
    fun color(v: Value): Long? = when (v) {
        is Value.Num -> v.v.toLong().let { n -> if (n in 0..0xFFFFFFL) n or 0xFF000000L else n.takeIf { it in 0..0xFFFFFFFFL } }
        else -> colorOf(v.text)
    }

    fun colorOf(s: String): Long? {
        val t = s.trim()
        if (t.startsWith("#")) {
            val h = t.substring(1)
            return when (h.length) {
                6 -> h.toLongOrNull(16)?.let { it or 0xFF000000L }
                8 -> h.toLongOrNull(16)
                else -> null
            }
        }
        return COLORS[t.replace(" ", "")]
    }

    /** The colour words a formula may give (and the editor lists), light-screen values. */
    val COLORS: Map<String, Long> = linkedMapOf(
        "빨강" to 0xFFE53935L, "주황" to 0xFFFB8C00L, "노랑" to 0xFFFDD835L, "연두" to 0xFF9CCC65L, "초록" to 0xFF43A047L,
        "청록" to 0xFF00897BL, "하늘" to 0xFF4FC3F7L, "파랑" to 0xFF1E88E5L, "남색" to 0xFF3949ABL, "보라" to 0xFF8E24AAL,
        "분홍" to 0xFFEC407AL, "갈색" to 0xFF8D6E63L, "회색" to 0xFF9E9E9EL, "검정" to 0xFF212121L, "하양" to 0xFFFFFFFFL,
        "연한빨강" to 0xFFFFCDD2L, "연한주황" to 0xFFFFE0B2L, "연한노랑" to 0xFFFFF9C4L, "연한초록" to 0xFFC8E6C9L,
        "연한파랑" to 0xFFBBDEFBL, "연한보라" to 0xFFE1BEE7L, "연한분홍" to 0xFFF8BBD0L, "연한회색" to 0xFFEEEEEEL,
        "red" to 0xFFE53935L, "orange" to 0xFFFB8C00L, "yellow" to 0xFFFDD835L, "green" to 0xFF43A047L, "blue" to 0xFF1E88E5L,
        "purple" to 0xFF8E24AAL, "pink" to 0xFFEC407AL, "gray" to 0xFF9E9E9EL, "black" to 0xFF212121L, "white" to 0xFFFFFFFFL,
    )

    /** Korean words for the choices a formula may give ("실선" → solid). */
    val PICK_WORDS: Map<String, String> = mapOf(
        "없음" to "none", "실선" to "solid", "점선" to "dot", "파선" to "dash", "두줄" to "double",
        "가로" to "row", "세로" to "col", "겹침" to "stack", "처음" to "start", "가운데" to "center", "끝" to "end", "늘이기" to "stretch",
        "왼쪽" to "start", "오른쪽" to "end",
    )
}
