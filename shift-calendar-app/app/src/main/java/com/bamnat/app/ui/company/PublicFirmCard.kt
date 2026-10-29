package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.core.plaza.PublicFirmWords
import com.bamnat.core.plaza.PublicFirms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/*
 * [J133-88] 1.33 (사용자 2026-10-29 15:48 "초기 데이터 확보가 어렵다는 한계 ... 정보가 있을법한곳을 전부 찾아서"): where the 광장 has no
 * pay way for a company yet, what the public record says of it, so the search is never empty-handed: 국민연금 가입 사업장 (members, the
 * average reported income, 5인 이상). Said as the record's, with its month and source, never as a coworker's.
 */

/** Answers kept for the app's life, by the name as the server reads it; a failed read is not kept. */
private object PublicFirmCache {
    val kept = ConcurrentHashMap<String, PublicFirms>()
}

/** [J133-88] The record for [name], kept once read; null offline, on a server before 1.33, or when it is not there now. */
internal suspend fun publicFirmsFor(c: AppContainer, name: String): PublicFirms? {
    val key = PublicFirmWords.norm(name)
    if (key.length < 2 || !c.companyApi.enabled) return null
    PublicFirmCache.kept[key]?.let { return it }
    return withContext(Dispatchers.IO) { runCatching { c.companyApi.publicFirms(name) }.getOrNull() }?.also { PublicFirmCache.kept[key] = it }
}

/**
 * [J133-88] Up to [max] workplaces of the record named like [name], each with where it is, its members and average; nothing when
 * the record has none. [onlyExact]: the one workplace that is the name itself (a company page), never its look-alikes.
 */
@Composable
fun PublicFirmCard(name: String, modifier: Modifier = Modifier, max: Int = 3, onlyExact: Boolean = false) {
    val c = LocalContext.current.appContainer
    val found by produceState<PublicFirms?>(null, name) { value = publicFirmsFor(c, name) }
    val f = found ?: return
    val firms = if (onlyExact) listOfNotNull(PublicFirmWords.exact(f, name)) else f.firms.take(max)
    // [J133-89] A public institution's own 보수규정, before its 국민연금 line.
    f.rules.forEach { set -> PublicRulesCard(set, f.rulesSource, modifier) }
    if (firms.isEmpty()) return
    SoftCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("공개 자료 · 국민연금 가입 사업장 (${PublicFirmWords.month(f.asOf)})", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            firms.forEach { firm ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(firm.name, style = MaterialTheme.typography.titleSmall)
                    PublicFirmWords.where(firm).takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(PublicFirmWords.facts(firm) + if (PublicFirmWords.fiveOrMore(firm)) " · 5인 이상" else "",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("5인 이상이면 연장·야간·휴일에 1.5배 가산이 붙어요. " + PublicFirmWords.NOTE,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [J133-89] 1.33: a public institution's 보수규정 in words (항목 · 계산 · 금액, from its own page), and what is one number for
 * everyone taken into the user's pay one at a time (이 값 쓰기, as the 광장's): the 통상시급 hours and a 매월 정액 paid to all. A line the
 * table could not read for sure says so and is never taken.
 */
@Composable
fun PublicRulesCard(set: com.bamnat.core.plaza.PublicRuleSet, source: String, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val company = companyScope(c)
    val profile by company.pay.profile.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var all by androidx.compose.runtime.saveable.rememberSaveable(set.name) { androidx.compose.runtime.mutableStateOf(false) }
    val taken = androidx.compose.runtime.remember(set.name) { androidx.compose.runtime.mutableStateListOf<String>() }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    SoftCard(modifier, highlighted = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("공개 규정 · ${set.name} ${set.rule}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text("${set.effective.ifEmpty { "시행일 모름" }} 시행 · ${source.ifEmpty { "공공기관 보수규정" }}. 같은 회사 분들이 올린 정보가 아니고, 직급·부서마다 다를 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lines = if (all) set.lines else set.lines.take(SHOWN_LINES)
            lines.forEach { l ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(l.item, style = MaterialTheme.typography.titleSmall)
                    Text(listOf(l.calc, l.amount).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (l.unsure) Text("원문 표를 다 읽지 못한 줄이에요. 원문으로 확인해 주세요.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (set.lines.size > SHOWN_LINES) androidx.compose.material3.TextButton(onClick = { all = !all }) {
                Text(if (all) "접기" else "${set.lines.size}줄 모두 보기")
            }
            if (set.url.startsWith("https://")) androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri(set.url) } }) { Text("원문 보기") }
            // 이 값 쓰기: only onto a pay that is set up, one value at a time, as the 광장's.
            val takes = listOfNotNull(set.hours?.let { "place:hours" to "통상시급 기준 ${it}시간" }) +
                set.takes.map { n -> "item:$n" to (set.lines.firstOrNull { it.calc.startsWith(n) || it.item.startsWith(n) }?.amount?.let { "$n $it" } ?: n) }
            if (takes.isNotEmpty()) {
                if (profile == null) Text("급여 설정을 마치면 아래 값을 내 급여에 하나씩 넣을 수 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    takes.forEach { (key, label) ->
                        val done = key in taken
                        com.bamnat.app.ui.components.FilterChip(selected = done, onClick = {
                            if (done) return@FilterChip
                            scope.launch {
                                val ok = runCatching { company.share.takeOne(set.setup, key) }.getOrDefault(false)
                                if (ok) { taken += key; c.telemetry.event("public_rules", "take") }
                            }
                        }, label = { Text(if (done) "$label 넣었어요" else "$label 넣기") })
                    }
                }
            }
        }
    }
}

private const val SHOWN_LINES = 6
