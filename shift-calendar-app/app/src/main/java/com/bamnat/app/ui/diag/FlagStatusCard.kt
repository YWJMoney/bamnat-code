package com.bamnat.app.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.flags.Check
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagSource
import com.bamnat.core.flags.FlagState
import com.bamnat.core.flags.Risk

/** [FLAG-14] The title of the list, also the report's section. */
const val FLAG_SECTION = "기능 상태"

/**
 * [FLAG-14] 1.28 AL: every dark-merge row on this phone — on or off, the one sentence why, where the answer came from
 * (앱 조건 / 서버 강제 / 관리자 미리 보기 / 앱 판) and its checklist (✓ 채움 · ○ 남음 · ? 모름) — so support can ask for one
 * screenshot. [FLAG-15] An admin phone gets a preview switch per row (판단 3단계, this phone only).
 */
@Composable
fun FlagStatusCard(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val states by c.flags.states.collectAsStateWithLifecycle()
    val previews by c.flagPreviews.keys.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val admin = remember(pro) { c.phoneFacts().admin }
    var open by remember { mutableStateOf(false) }
    SectionCard(title = FLAG_SECTION, modifier = modifier) {
        val on = states.count { it.on }
        Text("켜짐 $on · 꺼짐 ${states.size - on}. 기능이 왜 켜졌거나 꺼졌는지 보여 줘요. 문의할 때 이 부분 화면 사진을 보내 주시면 돼요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The rows that wait or were turned off first: they are what a question is usually about.
        val sorted = remember(states) { states.sortedBy { if (it.on && it.source != FlagSource.FORCE_OFF) 1 else 0 } }
        val shown = if (open) sorted else sorted.filter { !it.on || it.source == FlagSource.FORCE_ON || it.source == FlagSource.ADMIN_PREVIEW }
        shown.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            FlagRow(s, admin = admin, previewing = s.key in previews) { c.flagPreviews.set(s.key, it) }
        }
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (open) "꺼진 것만 보기" else "켜진 것도 모두 보기 (${states.size}개)")
        }
    }
}

@Composable
private fun FlagRow(s: FlagState, admin: Boolean, previewing: Boolean, onPreview: (Boolean) -> Unit) {
    val flag = FlagCatalog.of(s.key)
    val scheme = MaterialTheme.colorScheme
    val onWords = if (s.on) "켜짐" else "꺼짐"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "${flag?.name ?: s.key} $onWords, ${s.source.words}. ${s.reason}"
    }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(flag?.name ?: s.key, style = MaterialTheme.typography.titleSmall)
                Text(s.key + (if (flag?.risk == Risk.LEGAL) " · 법적" else ""), style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
            }
            Text("$onWords · ${s.source.words}", style = MaterialTheme.typography.labelLarge,
                color = if (s.on) scheme.primary else scheme.error)
        }
        Text(s.reason, style = MaterialTheme.typography.bodySmall)
        for (k in s.checks) {
            Text("${k.status.mark} ${k.label}" + (k.detail?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
                color = when (k.status) { Check.Status.MET -> scheme.onSurface; Check.Status.UNMET -> scheme.error; Check.Status.UNKNOWN -> scheme.onSurfaceVariant })
        }
    }
    if (admin) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("관리자 미리 보기", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = previewing, onCheckedChange = onPreview,
                modifier = Modifier.semantics { contentDescription = "${flag?.name ?: s.key} 관리자 미리 보기" })
        }
    }
}

/** [FLAG-14] The same list as text, for the report the user copies or sends. */
fun flagReportLines(c: AppContainer): List<String> = c.flags.states.value.map { s ->
    val checks = s.checks.joinToString(" / ") { "${it.status.mark} ${it.label}" }
    "${s.key}: ${if (s.on) "켜짐" else "꺼짐"} (${s.source.words}) ${s.reason}" + if (checks.isEmpty()) "" else " [$checks]"
}.ifEmpty { listOf("없음") }
