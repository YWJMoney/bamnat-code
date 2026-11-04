package com.bamnat.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.work.Job
import com.bamnat.core.work.JobDictionary
import com.bamnat.core.work.WorkStyle

/** [FIT-03] The card a job leads to, as the kind-of-work cards below name it. */
internal fun choiceFor(job: Job): StyleChoice =
    STYLE_CHOICES.firstOrNull { it.style == job.style && (job.style != WorkStyle.SHIFT || it.roster == job.roster) } ?: STYLE_CHOICES.first()

/**
 * [FIT-03] 1.25 묶음 Y (U23): "어떤 일을 하고 계신가요?" in the user's own words, with the jobs most people
 * type as chips. The words are matched on the phone ([JobDictionary]) to the kind of work it starts with
 * (one tap); a job it does not know says so, and the kind-of-work cards under it stay as they were.
 * Nothing typed here is kept or sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JobAsk(
    onPick: (WorkStyle, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** [FLAG-12] The kinds this list may lead to: a job of a hidden kind is not offered (the owner's phone marks it). */
    shown: com.bamnat.app.ui.flags.TypeShown = com.bamnat.app.ui.flags.TypeShown.ALL,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val matches = remember(text, shown) { JobDictionary.suggest(text, limit = 6).filter { shown.shows(it.job.style) }.take(3) }
    // [J133-77] 1.33 (유형별 점검: 투자를 숨긴 빌드에서 '주식'을 치면 '맞는 일을 찾지 못했어요'만): a kind not open yet is said so.
    val waiting = remember(text, shown) { JobDictionary.suggest(text, limit = 6).map { it.job.style }.firstOrNull { !shown.shows(it) } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(30) },
            label = { Text("하는 일") },
            placeholder = { Text(if (shown.shows(WorkStyle.INVEST)) "예: 간호사, 편의점 알바, 택배, 주식" else "예: 간호사, 편의점 알바, 택배, 식당") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JobDictionary.common.filter { n -> JobDictionary.byName(n)?.style?.let(shown::shows) != false }.forEach { name -> FilterChip(selected = false, onClick = { text = name }, label = { Text(name) }) }
            }
        }
        if (text.trim().length >= 2) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matches.isEmpty() && waiting != null) {
                    Text("‘${waiting.label}’ 달력은 준비 중이라 아직 고를 수 없어요. 열리면 앱 업데이트 소식으로 알려 드려요. 지금은 아래에서 다른 일하는 방식을 골라 주세요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (matches.isEmpty()) {
                    Text("‘${text.trim()}’에 맞는 일을 찾지 못했어요. 아래에서 가장 비슷한 일하는 방식을 골라 주세요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("이렇게 시작할까요?", style = MaterialTheme.typography.titleSmall)
                    matches.forEach { m ->
                        val choice = choiceFor(m.job)
                        ChoiceCard(
                            // 빌드 10 S13 ('아르바이트 · 아르바이트 · 시급'): a word already in the kind's name is said once.
                            title = (listOf(m.job.name) + choice.title.split(" · ")).distinct().joinToString(" · "),
                            subtitle = shown.mark(m.job.style, "누르면 이 일에 맞는 달력과 기능으로 다음 질문을 이어 가요"),
                            icon = choice.icon,
                            selected = false,
                            onClick = { focus.clearFocus(); onPick(m.job.style, m.job.roster) },
                        )
                    }
                }
            }
        }
        Text("또는 일하는 방식으로 고르기", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    }
}
