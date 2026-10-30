package com.bamnat.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 1.35 [J135-02] (사용자 2026-10-30 "입력하는 부분에서도 1,000,000으로 보이게 … 숫자를 입력하는 모든 부분"): a number box shows its
 * whole part in groups of three (1,000,000 · 12,345.67 · -2,500) while it is typed. Only how it looks: the box still holds the bare
 * digits, so what is saved and read back is unchanged, and the cursor moves over the commas as if they were not there.
 */
object GroupedNumber : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val from = if (s.startsWith('-')) 1 else 0
        var end = from
        while (end < s.length && s[end].isDigit()) end++
        if (end - from <= 3) return TransformedText(text, OffsetMapping.Identity)
        val out = StringBuilder(s.length + (end - from) / 3)
        // Where each place of the typed text lands, and back: a comma goes back to the digit after it.
        val toShown = IntArray(s.length + 1)
        val toTyped = ArrayList<Int>(s.length + 8)
        for (k in s.indices) {
            // The cursor before a digit stays before its comma (1|,234), as the 가계부 금액 칸 always did (LEDG-01).
            toShown[k] = out.length
            if (k in (from + 1) until end && (end - k) % 3 == 0) {
                out.append(',')
                toTyped.add(k)
            }
            out.append(s[k])
            toTyped.add(k)
        }
        toShown[s.length] = out.length
        toTyped.add(s.length)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, s.length)]
            override fun transformedToOriginal(offset: Int): Int = toTyped[offset.coerceIn(0, toTyped.lastIndex)]
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}
