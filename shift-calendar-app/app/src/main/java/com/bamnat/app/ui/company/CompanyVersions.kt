package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.SetupChanges
import com.bamnat.app.company.SharedSetup
import com.bamnat.core.plaza.DeptGroups

/**
 * [PLZD-06][PLZD-07][PLZD-08] 1.30 묶음 BD (사용자 2026-10-27: "같은 회사의 여러개의 정보들이 있으면 도대체 뭐가 맞는지 보기도
 * 힘들고 설정도 어렵다"): a company page by department. Each department shows its 대표 판 as the one card to read and use; the
 * other versions of it fold under one line and say only what they change against the 대표, in the server's words.
 */
data class DeptSection(val dept: String, val lead: SharedSetup, val others: List<SharedSetup>) {
    val title: String get() = dept.ifEmpty { "회사 전체" }
}

/** The page's versions by department (회사 전체 first, then the most stood behind), via core [DeptGroups]. */
fun deptSections(page: CompanyPage): List<DeptSection> {
    val byId = page.setups.associateBy { it.id }
    return DeptGroups.of(page.setups.map(com.bamnat.app.company.SendGuard::item)).map { g ->
        DeptSection(g.dept, byId.getValue(g.lead.id), g.others.map { byId.getValue(it.id) })
    }
}

/**
 * [PLZD-08] 내가 쓰는 판, above everything: which version this calendar uses, its kind of work in one line, and whether it is
 * still its department's 대표. Nothing to choose when it is; a compare when another leads now.
 */
@Composable
fun MyVersionBand(using: SharedSetup, lead: SharedSetup?, onCompareLead: (() -> Unit)?) {
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("내가 쓰는 판 · ${using.dept.ifEmpty { "회사 전체" }}", style = MaterialTheme.typography.titleSmall)
            }
            DeptGroups.summary(using.text)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val isLead = lead == null || lead.id == using.id
            Text(if (isLead) "이 부서 대표 판이에요. 바뀌면 여기서 알려 드려요."
                else "이 부서 대표 판은 다른 판이에요. 그대로 두셔도 되고, 비교해 보고 바꿔도 돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isLead && onCompareLead != null) TextButton(onClick = onCompareLead) { Text("대표 판과 비교") }
        }
    }
}

/** "같은 부서 다른 판 2개 보기": the other versions of a department, folded until asked for. */
@Composable
fun OthersFold(count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "같은 부서 다른 판 접기" else "같은 부서 다른 판 ${count}개 보기", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * [PLZD-07] One folded version: what it changes against its 대표 (the server's [SharedSetup.changes]; an older server's page
 * falls back to [diff], SetupDiff's "다른 점: …"), who stands behind it, and its two ways on: compare, or use it.
 */
@Composable
fun OtherVersionCard(
    s: SharedSetup,
    diff: String?,
    using: AppliedSetup?,
    onUse: () -> Unit,
    onCompare: (() -> Unit)?,
    onTalk: (() -> Unit)?,
) {
    SoftCard(highlighted = using != null) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다른 판", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                when {
                    s.doubted -> StatusChip("달라요가 많아요", Tone.WAIT)
                    s.adopted -> StatusChip("채택됨${s.version?.let { " v$it" } ?: ""}", Tone.GOOD)
                    else -> StatusChip(if (s.senders <= 1) "제보 1 · 확인 중" else "확인 중", Tone.WAIT)
                }
            }
            val ch = s.changes
            if (ch != null) ChangesView(ch) else diff?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            TrustLine(s.trust, s.agree, s.persons, s.bad, s.adopted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                onCompare?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("대표 판과 비교") } }
                if (s.adopted) Button(onClick = onUse, modifier = Modifier.weight(1f)) { Text(if (using != null) "다시 넣기" else "이 설정 쓰기") }
                else FilledTonalButton(onClick = onUse, modifier = Modifier.weight(1f)) { Text("확인 중이지만 써 보기") }
            }
            onTalk?.let { TextButton(onClick = it, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (s.posts > 0) "토론 ${s.posts}개 보기" else "토론 열기") } }
        }
    }
}

/**
 * [PLZD-07] What a version changes, as the server says it: the one line, then each fact that moved with the words that went
 * struck through and the words that came in bold, then whole facts added or taken out.
 */
@Composable
fun ChangesView(ch: SetupChanges, max: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (ch.summary.isNotBlank()) Text(ch.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary)
        val scheme = MaterialTheme.colorScheme
        ch.changed.take(max).forEach { f ->
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append("${f.label}  ") }
                if (f.tokens.isEmpty()) append("${f.before} → ${f.after}")
                else f.tokens.forEachIndexed { i, t ->
                    when {
                        t.deleted -> {
                            withStyle(SpanStyle(color = scheme.error, textDecoration = TextDecoration.LineThrough)) { append(t.text) }
                            // A word that went and the one that came are read apart.
                            if (f.tokens.getOrNull(i + 1)?.added == true) append(" ")
                        }
                        t.added -> withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold)) { append(t.text) }
                        else -> append(t.text)
                    }
                }
            }, style = MaterialTheme.typography.bodySmall)
        }
        val rest = (ch.changed.size - max).coerceAtLeast(0)
        ch.added.take(2).forEach { (label, text) -> Text("+ $label: $text", style = MaterialTheme.typography.bodySmall, color = scheme.primary) }
        ch.removed.take(2).forEach { (label, text) -> Text("− $label: $text", style = MaterialTheme.typography.bodySmall, color = scheme.error) }
        val more = rest + (ch.added.size - 2).coerceAtLeast(0) + (ch.removed.size - 2).coerceAtLeast(0)
        if (more > 0) Text("외 ${more}가지", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
