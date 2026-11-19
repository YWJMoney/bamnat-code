package com.bamnat.app.ui.pay

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.WageType
import kotlin.math.roundToLong

/** [PAYS-03] How the user is paid, in a word: "월급제", "연봉제", "시급제", "일당제". */
internal fun PayProfile.wageLabel(): String = when (wageType) {
    WageType.MONTHLY -> if (annual != null) "연봉제" else "월급제"
    WageType.HOURLY -> "시급제"
    WageType.DAILY -> "일당제"
}

/**
 * 2.0.0 [RVJ-67] 총괄 PC-156 (회사 설정을 넣은 폰의 급여 머리 줄이 '직접 설정 · 월급제'): which setup the pay is worked out with, for the pay
 * screen's head. A company's pay way in use reads by its name and ' 방식' ('가나다화학', ' 방식'); otherwise the job (병원 간호사, 직접
 * 설정) and nothing after it. [PayWhoLine] puts the [wageLabel] after them.
 */
internal fun PayProfile.whoName(way: com.bamnat.app.company.PayWay?): Pair<String, String> =
    way?.company?.takeIf { it.isNotBlank() }?.let { it to " 방식" } ?: (occupation.label to "")

/**
 * 2.0.0 [RVJ-91] (총괄 HQ-134, 조건 12·4) The pay head's '가나다화학 방식 · 월급제'. On a folded phone the line broke between
 * '가나다화학' and '방식': the name and its ' 방식' are now one piece that never breaks (a long company name ends in … before ' 방식'; the
 * whole name is in the sentence above 급여 설정 고치기), and when that and the wage do not fit on one line the wage word goes under
 * them, without the '·'. Read out as one line, the way it was written.
 */
@androidx.compose.runtime.Composable
internal fun PayWhoLine(
    name: Pair<String, String>, wage: String, part: com.bamnat.core.recipe.PartStyle, style: androidx.compose.ui.text.TextStyle,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val said = "${name.first}${name.second} · $wage"
    // Where a cut name's … ends: its box is as wide as it may be, and ' 방식' goes right after the …, not at the box's far side.
    val nameEnd = androidx.compose.runtime.remember { floatArrayOf(-1f) }
    androidx.compose.ui.layout.Layout(
        content = {
            com.bamnat.app.ui.parts.PartText(name.first, part, style, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                onTextLayout = { nameEnd[0] = if (it.lineCount > 0) it.getLineRight(0) else -1f })
            com.bamnat.app.ui.parts.PartText(name.second, part, style, maxLines = 1)
            com.bamnat.app.ui.parts.PartText(" · ", part, style, maxLines = 1)
            com.bamnat.app.ui.parts.PartText(wage, part, style, maxLines = 1)
        },
        modifier = modifier.clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(said) },
    ) { measurables, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val tail = measurables[1].measure(loose)
        val dot = measurables[2].measure(loose)
        val w = measurables[3].measure(loose)
        val n = measurables[0].measure(loose.copy(maxWidth = (c.maxWidth - tail.width).coerceAtLeast(0)))
        val nameRight = nameEnd[0].takeIf { it >= 0f }?.let { kotlin.math.ceil(it).toInt().coerceAtMost(n.width) } ?: n.width
        val nameWidth = nameRight + tail.width
        val one = nameWidth + dot.width + w.width <= c.maxWidth
        val width = if (one) nameWidth + dot.width + w.width else maxOf(nameWidth, w.width)
        val top = maxOf(n.height, tail.height)
        val height = if (one) maxOf(top, dot.height, w.height) else top + w.height
        layout(width.coerceIn(c.minWidth, c.maxWidth), height.coerceIn(c.minHeight, c.maxHeight)) {
            n.place(0, 0)
            tail.place(nameRight, 0)
            if (one) {
                dot.place(nameWidth, 0)
                w.place(nameWidth + dot.width, 0)
            } else w.place(0, top)
        }
    }
}

/** [PAYS-03] What [PayProfile.base] is: "기본급", "시급", "일당". */
internal fun WageType.baseName(): String = when (this) {
    WageType.MONTHLY -> "기본급"
    WageType.HOURLY -> "시급"
    WageType.DAILY -> "일당"
}

/** [PAYS-03] The hourly rate the base comes to: the 시급 itself, or 일당 ÷ 1일 소정근로시간. */
internal fun PayProfile.baseHourly(base: Long): Long = when (wageType) {
    WageType.MONTHLY -> 0
    WageType.HOURLY -> base
    WageType.DAILY -> (base * 60.0 / dailyHours.coerceIn(1, 8 * 60)).roundToLong()
}

/** "312만" for won amounts in a range, to one decimal under 100만: "8.3만". */
internal fun manwon(v: Long): String {
    if (com.bamnat.app.ui.Fmt.masked) return "•••"
    val m = v / 10_000.0
    return when {
        kotlin.math.abs(m) >= 100 -> "%,d만".format(m.roundToLong())
        kotlin.math.abs(m) >= 1 -> ("%.1f".format(m)).removeSuffix(".0") + "만"
        else -> "%,d".format(v)
    }
}

/** "약 312만~341만 원", or one figure when [low] and [high] meet. */
internal fun rangeText(low: Long, high: Long): String =
    if (low == high) won(low) else "약 ${manwon(low)}~${manwon(high)} 원"
