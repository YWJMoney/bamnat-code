package com.bamnat.app.ui.village

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.PublicQa
import com.bamnat.app.ui.company.SoftCard

/**
 * 1.38 [EASE-31] (오늘근무 분석 P13: 공개 Q&A에서 사용자끼리 알람·위젯 문제를 묻고 답해요, 공개 관찰): the answers the developer gives
 * most often, picked in the admin app (자주 쓰는 답 › 마을에 공개할 묻는 말), as one line in 마을 › 광장 that opens them all. Found
 * before asking, so nobody waits a day for an answer that was already written. Nothing shows when none is picked or offline.
 */
@Composable
fun PublicQaLine(c: AppContainer, modifier: Modifier = Modifier) {
    if (!c.inquiryApi.enabled) return
    val items by produceState<List<PublicQa>>(emptyList()) { value = runCatching { c.inquiryApi.qa() }.getOrDefault(emptyList()) }
    if (items.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    SoftCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "밤낮 Q&A 열기") { open = true }.heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text("❓ 밤낮 Q&A ${items.size}개", style = MaterialTheme.typography.titleSmall)
                Text(items.take(2).joinToString(" · ") { it.question }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            TextButton(onClick = { open = true }) { Text("보기") }
        }
    }
    if (open) PublicQaSheet(items, onAsk = {
        open = false
        com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "밤낮 Q&A"))
    }, onDismiss = { open = false })
}

/** Every Q&A, a tap opening its answer, with a search over both and 문의하기 at the end for what is not there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicQaSheet(items: List<PublicQa>, onAsk: () -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    val shown = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { it.question.contains(q, ignoreCase = true) || it.answer.contains(q, ignoreCase = true) }
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("밤낮 Q&A", style = MaterialTheme.typography.titleLarge)
                    Text("자주 받는 질문에 개발자가 직접 쓴 답이에요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (items.size > 4) OutlinedTextField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("찾기 (예: 위젯, 알람)") })
                }
            }
            items(shown, key = { it.id }) { qa ->
                val isOpen = opened == qa.id
                Column(Modifier.fillMaxWidth().animateContentSize()
                    .clickable(role = Role.Button, onClickLabel = if (isOpen) "답 접기" else "답 보기") { opened = if (isOpen) null else qa.id }
                    .semantics { stateDescription = if (isOpen) "펼침" else "접힘" }
                    .padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (qa.topicName.isNotBlank()) Text(qa.topicName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Q. ${qa.question}", style = MaterialTheme.typography.titleSmall)
                    if (isOpen) Text(qa.answer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (shown.isEmpty()) Text("‘${query.trim()}’에 맞는 답이 아직 없어요", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onAsk, modifier = Modifier.heightIn(min = 48.dp)) { Text("여기 없으면 문의하기") }
                }
            }
        }
    }
}
