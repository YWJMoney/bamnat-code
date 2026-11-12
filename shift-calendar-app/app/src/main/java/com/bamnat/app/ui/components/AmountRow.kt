package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.bamnat.app.ui.theme.NumberStyle

/**
 * 2.0.0 [RVJ-21] 이름 · (칩) · 금액 한 줄 (검토 HQ-07, 디자인 품질 조건 12). 글자 130% 이상(갈래 P의 NudgeCard와 같은 값)이거나
 * 이름이 칩·금액 옆에 한 줄로 다 들어가지 않으면 두 줄로 나눠요. 첫 줄은 이름(줄을 바꿔도 자르지 않음), 둘째 줄은 칩 + 금액(금액은
 * 오른쪽 끝, 한 줄, 글자를 줄이지 않음. 칩과 금액이 한 줄에 안 들어가면 금액이 그 아래 줄). 다 들어가면 이름 · 칩이 왼쪽, 금액이
 * 오른쪽인 한 줄이에요. 폭 기준을 dp로 박지 않은 까닭: 접은 폭 369dp의 카드 안 줄은 305dp쯤인데 100%에서는 한 줄에 잘 들어가요.
 * 예전 급여 항목별 내역은 200%에서 칩과 금액이 이름 칸을 밀어 '연장근/로'처럼 낱말 가운데서 끊겼어요.
 *
 * [leading](급여 줄의 종류 색 점처럼)·칩·금액은 이름 첫 줄의 가운데에 맞춰요: 이름이 두 줄이 되거나 [detail]이 붙어도 덩어리 가운데로
 * 내려가지 않아요. 읽어 주기는 줄 하나로 묶어 '연장근로, 추정, 152,000원' 순서예요([detail]은 그 뒤). 같은 구조(이름·칩·금액)는 이
 * 부품을 써요: 갈래 P가 공통 부품 표(docs/design/공통-부품.md)에 올리고 PC-11의 다른 화면에도 붙여요.
 */
@Composable
fun AmountRow(
    name: String,
    amount: String,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    nameColor: Color = Color.Unspecified,
    amountStyle: TextStyle = MaterialTheme.typography.titleSmall,
    amountColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    chip: (@Composable () -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
) {
    val big = LocalDensity.current.fontScale >= TWO_LINES_FROM
    val firstLineSp = if (nameStyle.lineHeight.isSpecified) nameStyle.lineHeight else nameStyle.fontSize * 1.4f
    Layout(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        // In reading order. An empty slot is an empty box (0 × 0), so the five are always there to measure.
        content = {
            Box { leading?.invoke() }
            Text(name, style = nameStyle, color = nameColor)
            Box { chip?.invoke() }
            Text(amount, style = amountStyle.merge(NumberStyle), color = amountColor, maxLines = 1, softWrap = false)
            Box { detail?.invoke() }
        },
    ) { measurables, constraints ->
        val any = Constraints(maxWidth = constraints.maxWidth)
        val lead = measurables[0].measure(any)
        val chipBox = measurables[2].measure(any)
        val money = measurables[3].measure(any)
        val left = lead.width + if (lead.width > 0) LEAD_GAP.roundToPx() else 0
        // Without a width to fill (never on a screen's column) it is as wide as one line of it needs.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val chipGap = if (chipBox.width > 0) CHIP_GAP.roundToPx() else 0
        val right = AMOUNT_GAP.roundToPx() + money.width
        // One line only while the name fits on it whole beside the chip and the amount. A fixed width does not say that: on a
        // 369dp fold the row inside its card is about 305dp, where '휴일근로 (8시간 이내) · 추정 · 519,155원' fits at 100%.
        val fits = left + measurables[1].maxIntrinsicWidth(Constraints.Infinity) + chipGap + chipBox.width + right <= width
        val two = big || !fits
        val firstLine = firstLineSp.roundToPx()
        fun room(taken: Int) = if (width == Constraints.Infinity) Constraints() else Constraints(maxWidth = (width - taken).coerceAtLeast(0))

        if (!two) {
            val label = measurables[1].measure(room(left + chipGap + chipBox.width + right))
            val more = measurables[4].measure(room(left + right))
            // The first line is as tall as its tallest piece (a chip is taller than the name's line) and each sits in its middle.
            val line = maxOf(firstLine, lead.height, chipBox.height, money.height)
            val labelY = (line - firstLine) / 2
            val top = maxOf(line, labelY + label.height)
            val w = if (width == Constraints.Infinity) left + label.width + chipGap + chipBox.width + right else width
            layout(w, top + more.height) {
                lead.placeRelative(0, (line - lead.height) / 2)
                label.placeRelative(left, labelY)
                chipBox.placeRelative(left + label.width + chipGap, (line - chipBox.height) / 2)
                money.placeRelative(w - money.width, (line - money.height) / 2)
                more.placeRelative(left, top)
            }
        } else {
            val label = measurables[1].measure(room(left))
            val more = measurables[4].measure(room(left))
            val line = maxOf(firstLine, lead.height)
            val labelY = (line - firstLine) / 2
            val top = maxOf(line, labelY + label.height) + LINE_GAP.roundToPx()
            val w = if (width == Constraints.Infinity) left + maxOf(label.width, chipBox.width + AMOUNT_GAP.roundToPx() + money.width) else width
            // The chip and the amount share the second line; an amount too long for that goes under the chip, still at the right.
            val apart = chipBox.width > 0 && left + chipBox.width + AMOUNT_GAP.roundToPx() + money.width > w
            val second = if (apart) chipBox.height + LINE_GAP.roundToPx() + money.height else maxOf(chipBox.height, money.height)
            layout(w, top + second + more.height) {
                lead.placeRelative(0, (line - lead.height) / 2)
                label.placeRelative(left, labelY)
                if (apart) {
                    chipBox.placeRelative(left, top)
                    money.placeRelative(w - money.width, top + chipBox.height + LINE_GAP.roundToPx())
                } else {
                    chipBox.placeRelative(left, top + (second - chipBox.height) / 2)
                    money.placeRelative(w - money.width, top + (second - money.height) / 2)
                }
                more.placeRelative(left, top + second)
            }
        }
    }
}

/** At this text size and up the name gets the whole first line (P의 NudgeCard와 같은 값). */
const val TWO_LINES_FROM = 1.3f
private val LEAD_GAP = 10.dp
private val CHIP_GAP = 6.dp
private val AMOUNT_GAP = 8.dp
private val LINE_GAP = 2.dp
