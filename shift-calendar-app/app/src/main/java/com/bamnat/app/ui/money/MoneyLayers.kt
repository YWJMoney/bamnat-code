package com.bamnat.app.ui.money

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    // PC 확인 ('앞으로의 돈' 카드는 '오늘' 층에, '앞으로' 층에는 곧 나갈 돈뿐이라 헷갈림): the third layer is named for what is in it.
    TOP(null), TODAY("오늘"), MONTH("이번 달"), AHEAD("나갈 돈"), CHECK("점검"),
}

internal val MoneyPart.layer: MoneyLayer
    get() = when (this) {
        MoneyPart.HERO -> MoneyLayer.TOP
        MoneyPart.INBOX, MoneyPart.NEW, MoneyPart.ASK, MoneyPart.STORY, MoneyPart.AHEAD, MoneyPart.RECENT -> MoneyLayer.TODAY
        MoneyPart.PACE, MoneyPart.BUDGET, MoneyPart.ENVELOPES, MoneyPart.ACCOUNTS, MoneyPart.INCOME, MoneyPart.TAX,
        MoneyPart.WORK, MoneyPart.OWED, MoneyPart.ALL -> MoneyLayer.MONTH
        MoneyPart.UPCOMING, MoneyPart.CARDBILL, MoneyPart.INSTALL -> MoneyLayer.AHEAD
        MoneyPart.FOUND, MoneyPart.SUBS, MoneyPart.CARD25, MoneyPart.OTHER, MoneyPart.REPEAT, MoneyPart.SHIFTSPEND,
        MoneyPart.SALESSPLIT, MoneyPart.FREELANCE, MoneyPart.AUTO, MoneyPart.ASKME -> MoneyLayer.CHECK
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
            color = MaterialTheme.colorScheme.primary)
        // PC 확인 (점검 머리 '펼치기'를 돈 적기 단추가 가림): 펼치기 sits right after the name, away from the corner the button floats in.
        if (onToggle != null) {
            Text(" · " + (if (open) "접기" else "펼치기"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}

/**
 * 1.37 [LEDH-32] 복귀 반김: back after a week or more without a line, no blame and no catch-up list — the days away, what came in by
 * itself meanwhile, and that writing only the big ones is fine. Gone once a line is written, or for now with 넘기기.
 */
@Composable
internal fun WelcomeBackCard(book: com.bamnat.core.money.MoneyBook, today: java.time.LocalDate, onAdd: () -> Unit) {
    var skipped by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val away = androidx.compose.runtime.remember(book, today) { com.bamnat.core.money.LedgerSmarts.away(book.entries, today) } ?: return
    if (skipped) return
    com.bamnat.app.ui.components.SectionCard(title = "${away.days}일 만이에요") {
        Text(
            (if (away.auto > 0) "그동안 결제 알림·반복 지출로 ${away.auto}건이 저절로 적혔어요. " else "") +
                "빠진 날을 다 채우지 않아도 돼요. 기억나는 큰 것만 적고 오늘부터 이어 가요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            androidx.compose.material3.TextButton(onClick = onAdd) { Text("돈 적기") }
            androidx.compose.material3.TextButton(onClick = { skipped = true }) { Text("넘기기") }
        }
    }
}

/** 1.37 [LEDH-33] 가계부에 묻기: a chip per question, the answer worked out on the phone from the book. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AskLedgerCard(book: com.bamnat.core.money.MoneyBook, today: java.time.LocalDate) {
    if (book.entries.none { it.kind == com.bamnat.core.money.MoneyKind.EXPENSE }) return
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    com.bamnat.app.ui.components.SectionCard(title = "가계부에 묻기") {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            com.bamnat.core.money.LedgerAsk.Question.entries.forEach { q ->
                com.bamnat.app.ui.components.FilterChip(selected = asked == q.name, onClick = { asked = if (asked == q.name) null else q.name }, label = { Text(q.label) })
            }
        }
        val q = asked?.let { n -> com.bamnat.core.money.LedgerAsk.Question.entries.firstOrNull { it.name == n } }
        if (q != null) {
            val answer = androidx.compose.runtime.remember(book, today, q) { com.bamnat.core.money.LedgerAsk.answer(q, book.entries, today) }
            Text(com.bamnat.app.ui.Fmt.hide(answer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
        } else Text("궁금한 걸 누르면 적은 기록으로 바로 답해요. 휴대폰 안에서만 셈해요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 1.38 [LEDH-31] 혼자 하는 챌린지: the run going on as a row of stamps (지킴 ●, 놓침 ×, 아직 ○), its streak, and 그만두기. With [picking]
 * the four to choose from (야간 도시락 only with a roster). No reward (결정 6).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChallengeCard(
    run: com.bamnat.core.money.Challenges.Run?, book: com.bamnat.core.money.MoneyBook, moves: List<com.bamnat.core.money.EnvelopeMove>,
    today: java.time.LocalDate, isNight: ((java.time.LocalDate) -> Boolean)?, picking: Boolean,
    onStart: (com.bamnat.core.money.Challenges.Kind) -> Unit, onStop: () -> Unit, onClosePicker: () -> Unit,
) {
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    if (picking) {
        com.bamnat.app.ui.components.SectionCard(title = "도전 고르기") {
            Text("하루마다 도장을 찍어요. 보상은 없고, 휴대폰 안에서만 세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.bamnat.core.money.Challenges.Kind.entries.filter { it != com.bamnat.core.money.Challenges.Kind.NIGHT_LUNCHBOX || isNight != null }.forEach { k ->
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onStart(k) }.padding(vertical = 8.dp)) {
                    Text(k.label, style = MaterialTheme.typography.titleSmall)
                    Text(k.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            androidx.compose.material3.TextButton(onClick = onClosePicker) { Text("닫기") }
        }
        return
    }
    run ?: return
    val s = androidx.compose.runtime.remember(run, book, moves, today) {
        com.bamnat.core.money.Challenges.status(run, book.entries, moves, today) { d -> isNight?.invoke(d) == true }
    }
    com.bamnat.app.ui.components.SectionCard(title = run.kind.label) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics { contentDescription = "도장 ${s.kept}개, ${s.counted}일 중" }) {
            s.stamps.forEach { st ->
                val (mark, color) = when (st) {
                    com.bamnat.core.money.Challenges.Stamp.KEPT -> "●" to cal.income
                    com.bamnat.core.money.Challenges.Stamp.MISSED -> "×" to cal.expense
                    com.bamnat.core.money.Challenges.Stamp.LATER -> "○" to MaterialTheme.colorScheme.outline
                    com.bamnat.core.money.Challenges.Stamp.NOT_COUNTED -> "·" to MaterialTheme.colorScheme.outlineVariant
                }
                Text(mark, color = color, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(com.bamnat.core.money.Challenges.words(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        androidx.compose.material3.TextButton(onClick = onStop) { Text(if (s.done) "닫기" else "그만두기") }
    }
}
