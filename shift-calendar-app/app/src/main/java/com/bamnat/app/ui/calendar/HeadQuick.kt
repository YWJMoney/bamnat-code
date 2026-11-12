package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.decor.readableOn
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorKind

/**
 * 2.0.0 [RVH-42] 검토 PC-29·PC-30 (통합 1차 PC: 작품의 '물 +1잔'이 달력 머리의 '도구'·'나' 위에 그려지고, 보이는 것보다 넓게 누름을
 * 가져갔어요. 화면 글자 읽기에도 잡히지 않았어요): a work's 바로 단추 on the calendar's head ([Decor.HEAD] decorations that press a
 * 규칙 단추) is a real button in the head's own row, before 오늘, not a picture laid over the head's buttons. It keeps the work's
 * colours, is read by name, and takes only its own place. The head stays one line (조건 1).
 */
internal data class HeadQuick(
    /** The 규칙 단추 it presses ([LogicButton.id]). */
    val id: String,
    /** What the work wrote on it ("물 +1잔"), or the button's name. */
    val text: String,
    /** The button's emoji, shown alone where the head has no room for [text]. */
    val emoji: String,
    /** The button's own name ("물 한 잔"), for TalkBack. */
    val name: String,
    val fill: Long?,
    val ink: Long?,
) {
    /** What TalkBack reads: the words on it, and the button's name when it says more. */
    val said: String get() = if (name == text || name.isBlank()) text else "$text, $name"

    /** The face of the round button: the emoji, or the first letter of the words. */
    val face: String get() = emoji.ifBlank { text.take(1) }

    companion object {
        /** At most this many in the head's row on a wide screen; the others are in 도구. */
        const val ROW_MAX = 3

        /**
         * The head's 바로 단추 of [layer], once each, in the work's order. One whose button does not run here ([button] is null: it is
         * gone from the work, or it is a 프로 button the phone cannot use) is left out, so no dead button stands on the head.
         */
        fun of(layer: Decor, button: (String) -> LogicButton?): List<HeadQuick> =
            layer.on(Decor.HEAD).filter { it.tap != null }.distinctBy { it.tap }.mapNotNull { item ->
                val b = button(item.tap!!) ?: return@mapNotNull null
                val text = (if (item.kind == DecorKind.TEXT) item.glyph else b.label).trim().ifEmpty { b.label }
                val emoji = b.emoji.orEmpty().ifBlank { if (item.kind == DecorKind.EMOJI) item.glyph else "" }
                HeadQuick(item.tap!!, text, emoji, b.label, item.fill, item.ink)
            }
    }
}

/**
 * 2.0.0 [RVH-42] How much of the head's one row the work's 바로 단추 take: the one place that decides it, so what else shares the row
 * (갈래 P 3/3: 알림함의 숫자) asks here with its own width in [reserved] instead of adding another sum beside it.
 */
internal data class HeadQuickRoom(
    /** How many stand in the row with their words ([HeadQuickButton]). */
    val words: Int,
    /** None fits with its words: the first is a round button with its emoji ([HeadQuickRound]), the month drops its year, 오늘 is an icon. */
    val round: Boolean,
    /** [round] on a narrow phone or with large text: 도구 is an icon too. */
    val tight: Boolean,
    /**
     * 2.0.0 [RVH-52] 검토 HQ-22: the round button leaves the month room to read at the head's own size. When it does not (a work's
     * padded head on a folded phone, 글자 200%), the button waits in 도구 with the others: the month is never squeezed for it (조건 12).
     */
    val roundFits: Boolean = true,
) {
    /** How many are in the row; the rest are in 도구. */
    val shown: Int get() = if (round) (if (roundFits) 1 else 0) else words

    companion object {
        val NONE = HeadQuickRoom(0, round = false, tight = false)

        /**
         * The room for [count] buttons in a head [widthDp] wide at [fontScale] (capped at 2), beside the month with its arrows (176dp
         * and 110dp a text step), 오늘 (58dp a step), the head's padding (34dp) and 도구 (48dp a step), less [reserved] dp that something
         * else in the row takes. A button with its words is about 28dp + 72dp a step; at most [HeadQuick.ROW_MAX]. 2.0.0 [RVH-52] HQ-22:
         * a work's month [titleGrow] times the app's size takes that much more of the row; [reserved] holds the work's head box too
         * (바깥 간격·안 여백). The round button stands only where the month ([titleMin] dp: this month at the head's own size) still fits
         * beside the arrows and ▾ (136dp with the row's sides), the round button, 오늘 and 나 (48dp each, [me]) and 도구 (48dp as an icon,
         * else 64dp + 30dp a text step).
         */
        fun of(
            count: Int, widthDp: Float, fontScale: Float, narrow: Boolean, reserved: Float = 0f, titleGrow: Float = 1f,
            titleMin: Float = 0f, me: Boolean = true,
        ): HeadQuickRoom {
            if (count <= 0) return NONE
            val fs = fontScale.coerceAtMost(2f)
            val free = widthDp - reserved - (176f + 110f * fs * titleGrow.coerceIn(1f, 2f) + 58f * fs + 34f + 48f * fs)
            val words = (free / (28f + 72f * fs)).toInt().coerceIn(0, minOf(HeadQuick.ROW_MAX, count))
            val round = words == 0
            val tight = round && (narrow || fs >= 1.3f)
            val left = widthDp - reserved - (136f + 48f * (if (me) 3 else 2) + (if (tight) 48f else 64f + 30f * fs))
            return HeadQuickRoom(words, round, tight, roundFits = !round || left >= titleMin)
        }
    }
}

/** The work's own colours for [q]'s button, its words kept readable on its fill (4.5:1, 조건 14). */
@Composable
private fun HeadQuick.colors(): Pair<Color, Color> {
    val fill = fill?.let { Color(it.toInt()).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.secondaryContainer
    val ink = ink?.let { Color(it.toInt()).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.onSecondaryContainer
    return fill to readableOn(ink, listOf(fill), target = 4.5)
}

/** [q] with its words, as wide as they are (a wide screen's head). */
@Composable
internal fun HeadQuickButton(q: HeadQuick, onPress: (String) -> Unit, modifier: Modifier = Modifier) {
    val (fill, ink) = q.colors()
    FilledTonalButton(
        onClick = { onPress(q.id) },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = fill, contentColor = ink),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.heightIn(min = 40.dp).padding(end = 4.dp).semantics { contentDescription = q.said },
    ) {
        Text(listOf(q.emoji, q.text).filter { it.isNotBlank() }.joinToString(" "), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** [q] as a round button with its emoji (a phone's head, where the words do not fit beside the month, 오늘, 도구 and 나). */
@Composable
internal fun HeadQuickRound(q: HeadQuick, onPress: (String) -> Unit, modifier: Modifier = Modifier) {
    val (fill, ink) = q.colors()
    FilledTonalIconButton(
        onClick = { onPress(q.id) },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = fill, contentColor = ink),
        modifier = modifier.semantics { contentDescription = q.said },
    ) {
        Text(q.face, maxLines = 1, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
    }
}
