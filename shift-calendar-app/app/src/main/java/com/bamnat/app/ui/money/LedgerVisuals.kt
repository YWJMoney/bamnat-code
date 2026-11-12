package com.bamnat.app.ui.money

import androidx.compose.foundation.Canvas
import com.bamnat.app.ui.theme.bamnat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WorkOutline
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LedgerColors
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.Reports
import kotlin.math.abs
import androidx.compose.ui.semantics.Role
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/** [LEDG-15] A picture for each built-in category, so a list reads at a glance; the user's own get a label. */
fun categoryIcon(name: String): ImageVector = when (name) {
    "식비" -> Icons.Outlined.Restaurant
    "카페" -> Icons.Outlined.LocalCafe
    "편의점" -> Icons.Outlined.Storefront
    "배달" -> Icons.Outlined.DeliveryDining
    "장보기·쇼핑" -> Icons.Outlined.ShoppingCart
    "교통" -> Icons.Outlined.DirectionsBus
    "자동차" -> Icons.Outlined.DirectionsCar
    "의료" -> Icons.Outlined.LocalHospital
    "통신" -> Icons.Outlined.PhoneAndroid
    "공과금" -> Icons.Outlined.Receipt
    "구독" -> Icons.Outlined.Subscriptions
    "주거" -> Icons.Outlined.Home
    "경조사" -> Icons.Outlined.CardGiftcard
    "교육" -> Icons.Outlined.School
    "여가" -> Icons.Outlined.SportsEsports
    "생활" -> Icons.Outlined.CleaningServices
    "의류·미용" -> Icons.Outlined.Checkroom
    "보험" -> Icons.Outlined.HealthAndSafety
    "반려동물" -> Icons.Outlined.Pets
    "육아" -> Icons.Outlined.ChildCare
    MoneyCategories.INTEREST, "대출이자" -> Icons.Outlined.RequestQuote
    MoneyCategories.SALARY -> Icons.Outlined.Payments
    "부수입" -> Icons.Outlined.WorkOutline
    "용돈" -> Icons.Outlined.VolunteerActivism
    "상여" -> Icons.Outlined.EmojiEvents
    "환급" -> Icons.Outlined.Replay
    "이자", "저축" -> Icons.Outlined.Savings
    "판매" -> Icons.Outlined.Sell
    "카드값" -> Icons.Outlined.CreditCard
    "내 계좌" -> Icons.Outlined.SwapHoriz
    "투자" -> Icons.AutoMirrored.Outlined.TrendingUp
    "대출 상환" -> Icons.Outlined.AccountBalance
    Reports.JOB_COSTS -> Icons.Outlined.Construction
    MoneyCategories.OTHER -> Icons.Outlined.MoreHoriz
    else -> Icons.AutoMirrored.Outlined.Label
}

/** [LEDG-08] Each kind of account's picture. */
fun accountIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Outlined.LocalAtm
    AccountType.BANK -> Icons.Outlined.AccountBalance
    AccountType.CHECK, AccountType.CREDIT -> Icons.Outlined.CreditCard
    AccountType.PAY -> Icons.Outlined.AccountBalanceWallet
    AccountType.OTHER -> Icons.Outlined.Savings
    AccountType.LOAN -> Icons.Outlined.RequestQuote
}

/** Every category's place in the palette: spending first, then income and transfers, so each keeps its color. */
// 1.38: 이자·금융 goes last so the colors the other categories already had stay theirs.
private val CATEGORY_ORDER = (MoneyCategories.expense - MoneyCategories.INTEREST) + MoneyCategories.income + MoneyCategories.transfer + MoneyCategories.INTEREST

@Composable
@ReadOnlyComposable
fun categoryColor(name: String): Color = if (name == Reports.JOB_COSTS) LedgerColors.at(5) else LedgerColors.of(name, CATEGORY_ORDER)

@Composable
@ReadOnlyComposable
fun accountColor(a: MoneyAccount): Color = if (a.color != 0) Color(a.color) else LedgerColors.at(a.type.ordinal * 2 + 1)

/**
 * [LEDG-15] An amount on one line that shrinks to fit instead of being cut, for big numbers in
 * narrow places and at large text sizes.
 */
/** [LEDG-16] Room under a page's last line for the 돈 적기 button: its height and both margins. */
val FabClearance: Dp = 88.dp

@Composable
fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, fontWeight: FontWeight? = null) {
    BasicText(
        text, modifier,
        style = style.merge(NumberStyle).copy(color = color, fontWeight = fontWeight ?: style.fontWeight),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
    )
}

/** A round tinted badge with a picture in it, as tall as a two-line row. */
@Composable
fun IconAvatar(icon: ImageVector, tint: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    // 7차 통합 347b7c37 PC 제안 (사진 바탕에서 보라·노랑 아이콘이 노을과 섞여요): on a picture the circle first gets the opaque veil.
    val ground = if (com.bamnat.app.ui.theme.PhotoGround.on()) com.bamnat.app.ui.theme.PhotoGround.solid(MaterialTheme.colorScheme.surface) else Color.Transparent
    Box(modifier.size(size).clip(CircleShape).background(ground).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun CategoryAvatar(category: String, size: Dp = 40.dp) = IconAvatar(categoryIcon(category), categoryColor(category), size)

/** [TIER-05][LEDG-15] "프로" beside a pro feature; [TIER-06] since 1.25 the app's one [com.bamnat.app.ui.components.ProBadge]. */
@Composable
fun ProPill(modifier: Modifier = Modifier, locked: Boolean = false) = com.bamnat.app.ui.components.ProBadge(modifier, locked)

/** [LEDG-15] What an empty part of the ledger says, with the one thing to do next. */
@Composable
fun LedgerEmpty(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {},
    /** 1.38 [DSG-09] Nothing written yet (not a filter that hides lines): 칸이 shows the way instead of the icon, unless it is turned off. */
    kani: com.bamnat.app.ui.components.KaniMood? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (kani != null && com.bamnat.app.ui.components.LocalShowKani.current) com.bamnat.app.ui.components.Kani(kani,
            night = com.bamnat.app.ui.components.isDarkSurface(), size = 72.dp)
        else IconAvatar(icon, MaterialTheme.colorScheme.primary, size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** "+12,000원", "-4,500원" or "12,000원" for a transfer; a cancelled payment (negative spending) is money back. */
fun signedWon(e: MoneyEntry): String = when {
    e.kind == MoneyKind.EXPENSE && e.amount < 0 -> "+" + Fmt.won(abs(e.amount))
    e.kind == MoneyKind.EXPENSE -> "-" + Fmt.won(e.amount)
    e.kind == MoneyKind.INCOME -> "+" + Fmt.won(e.amount)
    else -> Fmt.won(e.amount)
}

@Composable
fun amountColor(e: MoneyEntry): Color {
    val (income, expense) = moneyInks()
    return when {
        e.kind == MoneyKind.EXPENSE && e.amount < 0 -> income
        e.kind == MoneyKind.EXPENSE -> expense
        e.kind == MoneyKind.INCOME -> income
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** [FREE-06] 수입 and 지출 in 가계부 모양's own colours, each kept readable on the page (4.5:1), else the app's own. */
@Composable
fun moneyInks(): Pair<Color, Color> {
    val cal = LocalCalendarColors.current
    val look = com.bamnat.app.ui.theme.LocalMoneyLook.current
    val page = MaterialTheme.colorScheme.surface.takeIf { it.alpha > 0.5f } ?: MaterialTheme.colorScheme.surfaceContainerLow
    fun readable(c: Long?, own: Color): Color = c?.let { Color((it or 0xFF000000L).toInt()) }
        ?.takeIf { com.bamnat.core.recipe.Palette.contrast((it.toArgb().toLong() and 0xFFFFFFFFL), (page.toArgb().toLong() and 0xFFFFFFFFL)) >= 4.5 } ?: own
    return readable(look.income, cal.income) to readable(look.expense, cal.expense)
}

/**
 * [LEDG-15] A short amount for charts and captions that stays close to the real one: "1.5만",
 * "64.2만", "123만", "4.5천". (The calendar's [Fmt.wonShort] cuts 15,000 to "1만", too far off here.)
 */
fun wonBrief(amount: Long): String {
    if (com.bamnat.app.ui.Fmt.masked) return "•••" // 1.37 PC 5차: 통계의 만 단위도 가려요.
    val a = abs(amount)
    val sign = if (amount < 0) "-" else ""
    fun tenths(t: Long, unit: String) = "${t / 10}" + (if (t % 10 != 0L) ".${t % 10}" else "") + unit
    // 2.0.0 [RVK-43] (검토 MONEY-23) Rounding that reaches the next unit says it in that unit: 9,980원 is "1만" (was "10천"),
    // 99,996,000원 is "1억" (was "10000만").
    return sign + when {
        a >= 100_000_000 -> tenths((a + 5_000_000) / 10_000_000, "억")
        a >= 1_000_000 -> ((a + 5_000) / 10_000).let { if (it >= 10_000) "1억" else "${it}만" }
        a >= 10_000 -> tenths((a + 500) / 1_000, "만")
        a >= 1_000 -> ((a + 50) / 100).let { if (it >= 100) "1만" else tenths(it, "천") }
        else -> "$a"
    }
}

/** "12% 더" / "8% 덜" against [before]; null when there is nothing to compare. */
fun changeText(now: Long, before: Long, more: String = "더", less: String = "덜"): String? {
    if (before <= 0) return null
    val diff = now - before
    if (diff == 0L) return "같아요"
    val pct = (abs(diff) * 100 + before / 2) / before
    return if (diff > 0) "$pct% $more" else "$pct% $less"
}

/** A rounded track filled to [fraction]; a mark at [marker] (0..1) shows where the month is. */
@Composable
fun LedgerTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, marker: Float? = null) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(Modifier.fillMaxHeight().width(maxWidth * fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2)).background(color))
        if (marker != null && marker in 0f..1f) {
            Box(
                Modifier.padding(start = (maxWidth * marker - 1.dp).coerceAtLeast(0.dp)).width(2.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
            )
        }
    }
}

/** One slice of a [Donut]. */
data class Slice(val label: String, val amount: Long, val color: Color)

/**
 * [LEDG-10] Where the money went, as a ring with a small gap between slices; the total sits in the
 * middle. A screen reader hears the biggest slices instead of the picture.
 */
@Composable
fun Donut(slices: List<Slice>, center: String, caption: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val total = slices.sumOf { it.amount.coerceAtLeast(0) }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val said = slices.take(4).joinToString(", ") { "${it.label} ${(it.amount * 100 / total)}%" }
    Box(modifier.size(size).semantics { contentDescription = "$caption $center. $said" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.13f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val gap = if (slices.count { it.amount > 0 } > 1) 1.6f else 0f
            var start = -90f
            for (s in slices) {
                if (s.amount <= 0) continue
                val sweep = 360f * s.amount / total
                if (sweep - gap > 0.4f) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = size * 0.66f)) {
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(center, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * [LEDG-10] Spending added up day by day: this month (solid, shaded) against the month before
 * (dashed), with the budget as a line when there is one.
 */
@Composable
fun PaceChart(current: List<Long>, previous: List<Long>, days: Int, budget: Long?, description: String, modifier: Modifier = Modifier) {
    val cal = LocalCalendarColors.current
    val line = cal.expense
    val before = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val warn = MaterialTheme.bamnat.caution
    val top = maxOf(current.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, budget ?: 0).coerceAtLeast(1)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row {
            Text(wonBrief(top), style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(132.dp).padding(vertical = 6.dp)) {
            val w = size.width
            val h = size.height
            fun x(day: Int) = if (days <= 1) 0f else w * (day - 1) / (days - 1)
            fun y(v: Long) = h - h * (v.toFloat() / top)
            // 2.0.0 [RVK-43] (검토 MONEY-24) Lines in dp: in px they were half as thick on a 폴드's screen as on a small phone.
            for (k in 0..2) drawLine(grid, Offset(0f, h * k / 2), Offset(w, h * k / 2), strokeWidth = 0.5.dp.toPx())
            if (budget != null && budget > 0) {
                drawLine(warn, Offset(0f, y(budget)), Offset(w, y(budget)), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            if (previous.size > 1) {
                val p = Path()
                previous.take(days).forEachIndexed { i, v -> if (i == 0) p.moveTo(x(1), y(v)) else p.lineTo(x(i + 1), y(v)) }
                drawPath(p, before, style = Stroke(1.25.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())), join = StrokeJoin.Round))
            }
            if (current.isNotEmpty()) {
                val p = Path()
                current.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(1), y(v)) else p.lineTo(x(i + 1), y(v)) }
                val area = Path().apply {
                    addPath(p)
                    lineTo(x(current.size), h)
                    lineTo(x(1), h)
                    close()
                }
                drawPath(area, line.copy(alpha = 0.12f))
                drawPath(p, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(line, 3.dp.toPx(), Offset(x(current.size), y(current.last())))
            }
        }
        Row {
            Text("1일", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${(days + 1) / 2}일", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${days}일", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

/** A key under a chart: a short line or dot in [color] and what it stands for. */
@Composable
fun LegendKey(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [LEDG-10] Pairs of bars, money in and out, one pair per column; the picked column is strong and
 * the rest faded, as on the stats tab. A tap picks a column.
 */
@Composable
fun PairBars(
    labels: List<String>,
    income: List<Long>,
    spent: List<Long>,
    selected: Int,
    onSelect: (Int) -> Unit,
    descriptions: List<String>,
    modifier: Modifier = Modifier,
) {
    val cal = LocalCalendarColors.current
    val top = maxOf(income.maxOrNull() ?: 0, spent.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(i) }
                    .semantics(mergeDescendants = true) { contentDescription = descriptions.getOrElse(i) { label } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.weight(1f).widthIn(max = 24.dp).fillMaxWidth().padding(top = 6.dp, start = 1.dp, end = 1.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    listOf(income.getOrElse(i) { 0 } to cal.income, spent.getOrElse(i) { 0 } to cal.expense).forEach { (v, c) ->
                        Box(
                            Modifier.weight(1f)
                                .fillMaxHeight((v.toFloat() / top).coerceIn(if (v > 0) 0.02f else 0f, 1f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (on) c else c.copy(alpha = 0.4f)),
                        )
                    }
                }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/** [LEDG-10] Single bars with their amount above, for the days of the week. */
@Composable
fun ValueBars(labels: List<String>, values: List<Long>, color: Color, highlight: Int?, modifier: Modifier = Modifier) {
    val top = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, label ->
            val v = values.getOrElse(i) { 0 }
            val on = i == highlight
            Column(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$label ${Fmt.won(v)}" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) wonBrief(v) else "", style = MaterialTheme.typography.labelSmall.merge(NumberStyle), maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.widthIn(max = 28.dp).fillMaxWidth().fillMaxHeight((v.toFloat() / top).coerceIn(if (v > 0) 0.03f else 0f, 1f))
                            .clip(MaterialTheme.shapes.extraSmall).background(if (on) color else color.copy(alpha = 0.4f)),
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp),
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A labelled amount with a thin bar under it, for lists of categories, accounts and tags. */
@Composable
fun ShareRow(
    title: String,
    amount: Long,
    fraction: Float,
    color: Color,
    leading: (@Composable () -> Unit)? = null,
    caption: String? = null,
    trailingNote: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (caption != null) {
                        Text("  $caption", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(Fmt.won(amount), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedgerTrack(fraction, color, Modifier.weight(1f), height = 6.dp)
                if (trailingNote != null) {
                    Text(trailingNote, style = MaterialTheme.typography.labelSmall.merge(NumberStyle), color = trailingColor,
                        modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
        }
    }
}
