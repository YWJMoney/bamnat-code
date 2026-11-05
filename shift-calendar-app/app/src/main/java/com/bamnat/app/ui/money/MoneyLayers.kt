package com.bamnat.app.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1.37 [LEDH-20] 요약 세 층: the summary's cards asked in the order a person asks — 오늘 (정리함·오늘 쓸 돈), 이번 달 (예산·봉투·흐름),
 * 앞으로 (나갈 돈·카드값·할부) — and the rest in one 점검 group, folded until opened. 이번 달 합계 stays on top without a heading.
 * The user's order holds inside each layer.
 */
internal enum class MoneyLayer(val title: String?) {
    TOP(null), TODAY("오늘"), MONTH("이번 달"), AHEAD("앞으로"), CHECK("점검"),
}

internal val MoneyPart.layer: MoneyLayer
    get() = when (this) {
        MoneyPart.HERO -> MoneyLayer.TOP
        MoneyPart.INBOX, MoneyPart.NEW, MoneyPart.ASK, MoneyPart.STORY, MoneyPart.AHEAD, MoneyPart.RECENT -> MoneyLayer.TODAY
        MoneyPart.PACE, MoneyPart.BUDGET, MoneyPart.ENVELOPES, MoneyPart.ACCOUNTS, MoneyPart.INCOME, MoneyPart.TAX,
        MoneyPart.WORK, MoneyPart.OWED, MoneyPart.ALL -> MoneyLayer.MONTH
        MoneyPart.UPCOMING, MoneyPart.CARDBILL, MoneyPart.INSTALL -> MoneyLayer.AHEAD
        MoneyPart.FOUND, MoneyPart.SUBS, MoneyPart.CARD25, MoneyPart.OTHER, MoneyPart.REPEAT, MoneyPart.SHIFTSPEND,
        MoneyPart.SALESSPLIT, MoneyPart.FREELANCE, MoneyPart.AUTO -> MoneyLayer.CHECK
    }

/** [parts] in layer order, the user's order kept inside each layer. */
internal fun <T> byLayer(parts: List<T>, part: (T) -> MoneyPart): List<T> = parts.sortedBy { part(it).layer.ordinal }

/**
 * The cards of [parts] under their layers' headings. A layer whose cards all have nothing to say shows no heading;
 * 점검 shows its heading with how many cards are in it, and the cards only when [checkOpen].
 */
@Composable
internal fun MoneyLayered(parts: List<MoneyPart>, gap: Dp, checkOpen: Boolean, onCheck: (Boolean) -> Unit, draw: @Composable (MoneyPart) -> Unit) {
    val sorted = byLayer(parts) { it }
    MoneyLayer.entries.forEach { layer ->
        val mine = sorted.filter { it.layer == layer }
        if (mine.isEmpty()) return@forEach
        if (layer.title == null) mine.forEach { draw(it) }
        else LayerGroup(layer.title, gap, open = layer != MoneyLayer.CHECK || checkOpen,
            onToggle = if (layer == MoneyLayer.CHECK) ({ onCheck(!checkOpen) }) else null, cards = mine.map { p -> p.id to @Composable { draw(p) } })
    }
}

@Composable
private fun LayerGroup(title: String, gap: Dp, open: Boolean, onToggle: (() -> Unit)?, cards: List<Pair<String, @Composable () -> Unit>>) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { c ->
        val loose = c.copy(minHeight = 0)
        // Cards that have nothing to say draw nothing; only the others count.
        val each = cards.map { (id, card) -> subcompose("card-$id", card).map { it.measure(loose) }.filter { it.height > 0 } }
        val body = each.flatten()
        if (body.isEmpty()) return@SubcomposeLayout layout(c.minWidth, 0) {}
        val count = each.count { it.isNotEmpty() }
        val head = subcompose("head") { LayerHeading(title, count.takeIf { onToggle != null }, open, onToggle) }.map { it.measure(loose) }
        val space = gap.roundToPx()
        val shown = if (open) body else emptyList()
        val height = head.sumOf { it.height } + shown.sumOf { it.height + space }
        layout(c.maxWidth, height) {
            var y = 0
            head.forEach { it.placeRelative(0, y); y += it.height }
            shown.forEach { y += space; it.placeRelative(0, y); y += it.height }
        }
    }
}

@Composable
private fun LayerHeading(title: String, count: Int?, open: Boolean, onToggle: (() -> Unit)?) {
    val m = if (onToggle == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = if (open) "접기" else "펼치기") { onToggle() }
    Row(m.fillMaxWidth().padding(top = 4.dp, bottom = 0.dp, start = 4.dp).then(if (onToggle != null) Modifier.padding(vertical = 8.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title + (count?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (onToggle != null) {
            Text(if (open) "접기" else "펼치기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
