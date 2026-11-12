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
) {
    /** How many are in the row; the rest are in 도구. */
    val shown: Int get() = if (round) 1 else words

    companion object {
        val NONE = HeadQuickRoom(0, round = false, tight = false)

        /**
         * The room for [count] buttons in a head [widthDp] wide at [fontScale] (capped at 2), beside the month with its arrows (176dp
         * and 110dp a text step), 오늘 (58dp a step), the head's padding (34dp) and 도구 (48dp a step), less [reserved] dp that something
         * else in the row takes. A button with its words is about 28dp + 72dp a step; at most [HeadQuick.ROW_MAX].
         */
        fun of(count: Int, widthDp: Float, fontScale: Float, narrow: Boolean, reserved: Float = 0f): HeadQuickRoom {
            if (count <= 0) return NONE
            val fs = fontScale.coerceAtMost(2f)
            val free = widthDp - reserved - (176f + 110f * fs + 58f * fs + 34f + 48f * fs)
            val words = (free / (28f + 72f * fs)).toInt().coerceIn(0, minOf(HeadQuick.ROW_MAX, count))
            val round = words == 0
            return HeadQuickRoom(words, round, tight = round && (narrow || fs >= 1.3f))
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

/**
 * 2.0.0 [RVP-45] 검토 UIX-01·U13-01·PC-55 (달력 위가 화면 절반: 달력 칩 줄 + 달 줄, 두 줄에 단추 모양이 다섯 가지) and HQ-29·HQ-22 (작품을
 * 입은 접은 폭·글자 200%에서 '내 달력' 칩이 '내 달…', 큰 달 제목이 '2026'으로 작게 줄어듦): the calendar's head is one row on every width
 * and text size: the calendar chip, ‹ 11월 ▾ ›, the work's 바로 단추, 오늘, 공유, the bell (알림함), 도구 and 나. What does not fit is never
 * cut or squeezed: it turns into its icon or waits in 도구 / the calendar chip's list, in this order: the other calendars' faces and
 * 같이 보기 (in the chip's list), 공유's word, the calendar's name (the chip keeps its face, read by name), the month's year, 공유 (in
 * 도구), 오늘's word, 도구's word, the arrows (the month swipes, and TalkBack has 이전 달·다음 달 on the month), and last the face that goes
 * back to the user's own calendar from someone else's.
 *
 * Widths are worked out from the text size the way [HeadQuickRoom] does (H's 3묶음), and the work's 바로 단추 take what is left, so
 * there is one place that decides the row.
 */
internal data class HeadRoom(
    /** The calendar chip says the calendar's name; else its face and ▾ only. */
    val name: Boolean,
    /** Other calendars' faces beside the chip, one tap to switch (a wide screen). */
    val faces: Int,
    /** 같이 보기 in words beside the chip (a wide screen, before another calendar sits there). */
    val together: Boolean,
    /** 공유 on the row: [WORDS], [ICON], or 0 (in 도구 only). */
    val share: Int,
    val todayWords: Boolean,
    val toolsWords: Boolean,
    /** The month without this year's number. */
    val shortTitle: Boolean,
    val arrows: Boolean,
    /** The room the work's 바로 단추 take. */
    val quick: HeadQuickRoom,
    /** Even with everything folded the row is too narrow for the month at its size: it steps down one size (조건 12). */
    val stepDown: Boolean = false,
    /**
     * Someone else's calendar is on screen and the way back to the user's own is a face beside the chip, one tap (the old bar's
     * '내 달력으로'); the last thing to fold, as going back is what that screen is left for.
     */
    val home: Boolean = false,
) {
    companion object {
        const val WORDS = 2
        const val ICON = 1
        private const val BUTTON = 48f
        private const val PAD = 8f
        /** The first steps of the fold (faces, 같이 보기, 공유's word, the calendar's name), which the work's word buttons go before. */
        private const val SOFT_STEPS = 4

        /**
         * The row for a head [widthDp] wide at [fontScale] (capped at 2) whose month is [titleSp] big: [quick] 바로 단추, the bell
         * when [bell], 나 when [me], [others] other calendars, 같이 보기 when [together], 공유 when [share], [nameChars] letters
         * in the calendar's name, and the way back to the user's own calendar when [home] (someone else's is on screen).
         */
        fun of(
            widthDp: Float, fontScale: Float, titleSp: Float, quick: Int = 0, bell: Boolean = false, me: Boolean = true,
            others: Int = 0, together: Boolean = false, share: Boolean = true, nameChars: Int = 3, home: Boolean = false,
        ): HeadRoom {
            val fs = fontScale.coerceAtMost(2f)
            val wide = widthDp >= 600f
            var r = HeadRoom(
                name = wide, faces = if (wide) minOf(others, 2) else 0, together = wide && together && others == 0,
                share = if (share) WORDS else 0, todayWords = true, toolsWords = true, shortTitle = false, arrows = true, quick = HeadQuickRoom.NONE,
                home = home,
            )
            fun need(x: HeadRoom, sp: Float = titleSp): Float {
                var w = PAD
                w += 52f + (if (x.name) 6f + 14f * fs * nameChars else 0f) // the chip: face, name, ▾
                w += x.faces * (42f + 48f * fs)
                // Among the faces on a wide screen; its own round face where they fold.
                if (x.home && x.faces == 0) w += BUTTON
                if (x.together) w += 44f + 56f * fs
                if (x.arrows) w += 2 * BUTTON
                w += sp * fs * (if (x.shortTitle) 2.15f else 4.65f) + 32f // the month and ▾
                w += if (x.todayWords) maxOf(58f, 24f + 28f * fs) else BUTTON
                w += when (x.share) { WORDS -> maxOf(58f, 52f + 28f * fs); ICON -> BUTTON; else -> 0f }
                if (bell) w += BUTTON
                w += if (x.toolsWords) 46f + 32f * fs else BUTTON
                if (me) w += BUTTON
                return w
            }
            val steps: List<(HeadRoom) -> HeadRoom> = listOf(
                { it.copy(faces = 0) }, { it.copy(together = false) }, { it.copy(share = minOf(it.share, ICON)) }, { it.copy(name = false) },
                { it.copy(shortTitle = true) }, { it.copy(share = 0) }, { it.copy(todayWords = false) }, { it.copy(toolsWords = false) },
                { it.copy(arrows = false) }, { it.copy(home = false) },
            )
            // [RVH-42] PC-29: the work's own buttons with their words are worth more than the other calendars' faces, 같이 보기, 공유's
            // word and the calendar's name; when one fits with its words once those are folded, the row stops there.
            val wordButton = 28f + 72f * fs
            val start = r
            var i = 0
            if (quick > 0) {
                while (need(r) + wordButton > widthDp && i < SOFT_STEPS) r = steps[i++](r)
                if (need(r) + wordButton > widthDp) {
                    r = start
                    i = 0
                }
            }
            // Else room for one round 바로 단추 at least, before the work's buttons share what is left.
            val least = if (quick > 0 && need(r) + wordButton > widthDp) BUTTON else if (quick > 0) wordButton else 0f
            while (need(r) + least > widthDp && i < steps.size) r = steps[i++](r)
            val stepDown = need(r) + minOf(least, if (quick > 0) BUTTON else 0f) > widthDp
            if (quick <= 0) return r.copy(stepDown = stepDown)
            val free = widthDp - need(r)
            val words = (free / (28f + 72f * fs)).toInt().coerceIn(0, minOf(HeadQuick.ROW_MAX, quick))
            return r.copy(quick = HeadQuickRoom(words, round = words == 0, tight = !r.toolsWords), stepDown = stepDown)
        }
    }
}
