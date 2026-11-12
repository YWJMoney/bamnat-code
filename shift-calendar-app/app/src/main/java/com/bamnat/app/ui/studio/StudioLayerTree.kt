package com.bamnat.app.ui.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.BlueprintParts
import com.bamnat.core.recipe.Decor

/**
 * [LAY-09] 1.36 스튜디오 곳 줄 (누락 점검 N6, 2차 5-4 '자주 쓰는 곳 먼저'): the row of 곳 chips shows the six most used — how often each
 * was opened, kept on the phone — plus the one open now, in the studio's own order so a chip does not jump about, and '더 보기' for the rest.
 * Before anything is used, 달력·앱 전체·기능·가계부·메모·통계 come first.
 */
internal object AreaRank {
    const val SHOWN = 6
    /** Where a new studio starts: the first is the most wanted. */
    val START = listOf("CALENDAR", "ALL", "FEATURES", "MONEY", "MEMO", "STATS")

    /** The chips to show, in [order]: the [SHOWN] most used (ties by [START], then [order]) and [current]. */
    fun shown(order: List<String>, uses: Map<String, Int>, current: String, n: Int = SHOWN): List<String> {
        val rank = order.sortedWith(compareByDescending<String> { uses[it] ?: 0 }
            .thenBy { START.indexOf(it).let { i -> if (i < 0) START.size else i } }
            .thenBy { order.indexOf(it) })
        val keep = rank.take(n).toSet() + current
        return order.filter { it in keep }
    }

    /** The prefs line that keeps the counts: "CALENDAR:12,MEMO:3". */
    fun decode(s: String?): Map<String, Int> = s.orEmpty().split(',').mapNotNull { e ->
        val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        v.toIntOrNull()?.let { k to it }
    }.toMap()

    fun encode(m: Map<String, Int>): String = m.entries.filter { it.value > 0 }.joinToString(",") { "${it.key}:${it.value}" }

    /** One more use of [area]; counts are halved past 1000 so an old habit gives way to a new one. */
    fun used(m: Map<String, Int>, area: String): Map<String, Int> {
        val next = m + (area to (m[area] ?: 0) + 1)
        return if ((next[area] ?: 0) > 1000) next.mapValues { it.value / 2 } else next
    }
}

/** One row of the 층 나무. */
internal data class TreeRow(val depth: Int, val label: String, val sub: String?, val part: String? = null, val decor: Int? = null, val heading: Boolean = false)

/**
 * [LAY-09] 층 나무 (누락 점검 N6, 고급 보기): every layer of the open 곳 at once — its parts as the 부품 설계도 nests them (● for one that has
 * a look of its own) and, under each place, the 꾸밈 층 items back to front. A row opens its part in 부품 or its item in 꾸밈 층's 고치기.
 */
internal fun treeRows(partIds: List<String>, parts: Blueprint, decor: Decor, places: List<String>): List<TreeRow> = buildList {
    if (partIds.isNotEmpty()) {
        add(TreeRow(0, "부품", null, heading = true))
        val touched = parts.lines.mapNotNull { it["id"] as? String }.toSet()
        partIds.forEach { p ->
            var d = 1
            var up = BlueprintParts.parentOf(p)
            while (up != null && up in partIds) { d++; up = BlueprintParts.parentOf(up) }
            val states = parts.lines.filter { it["id"] == p }.mapNotNull { it["on"] as? String }
            add(TreeRow(d, (if (p in touched) "● " else "") + (BlueprintParts.LABELS[p] ?: p),
                states.takeIf { it.isNotEmpty() }?.joinToString(" · ") { STATE_WORDS[it] ?: it }, part = p))
        }
    }
    if (places.isNotEmpty()) {
        add(TreeRow(0, "꾸밈 층", null, heading = true))
        places.forEach { at ->
            val items = decor.items.withIndex().filter { it.value.at == at }
            add(TreeRow(1, Decor.anchorLabel(at), if (items.isEmpty()) "비어 있어요" else "${items.size}개"))
            items.reversed().forEach { (i, it) ->
                val flags = listOfNotNull(
                    it.on?.let { o -> Decor.onLabel(o) }, "숨김".takeIf { _ -> it.hide }, "잠금".takeIf { _ -> it.lock },
                    "묶음 ${it.group}".takeIf { _ -> it.group != 0 }, "날짜 위".takeIf { _ -> it.over },
                )
                add(TreeRow(2, it.label, flags.takeIf { f -> f.isNotEmpty() }?.joinToString(" · "), decor = i))
            }
        }
    }
}

@Composable
internal fun LayerTree(rows: List<TreeRow>, onPart: (String) -> Unit, onDecor: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(true) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Text("층 나무", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "접기" else "펼치기") }
            }
            if (open) rows.forEach { r ->
                val go: (() -> Unit)? = r.part?.let { p -> { onPart(p) } } ?: r.decor?.let { i -> { onDecor(i) } }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (go != null) 48.dp else 32.dp)
                        .then(if (go != null) Modifier.clickable(role = Role.Button, onClickLabel = "고치기", onClick = go) else Modifier)
                        .padding(start = (12 + r.depth * 16).dp, end = 12.dp)
                        .then(if (r.heading) Modifier.semantics { heading() } else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.label, style = if (r.heading) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (r.heading) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    r.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                    if (go != null) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
