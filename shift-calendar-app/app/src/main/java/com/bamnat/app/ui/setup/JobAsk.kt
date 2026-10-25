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
internal fun JobAsk(onPick: (WorkStyle, Boolean) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val matches = remember(text) { JobDictionary.suggest(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(30) },
            label = { Text("하는 일") },
            placeholder = { Text("예: 간호사, 편의점 알바, 택배, 주식") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JobDictionary.common.forEach { name -> FilterChip(selected = false, onClick = { text = name }, label = { Text(name) }) }
            }
        }
        if (text.trim().length >= 2) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matches.isEmpty()) {
                    Text("‘${text.trim()}’에 맞는 일을 찾지 못했어요. 아래에서 가장 비슷한 일하는 방식을 골라 주세요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("이렇게 시작할까요?", style = MaterialTheme.typography.titleSmall)
                    matches.forEach { m ->
                        val choice = choiceFor(m.job)
                        ChoiceCard(
                            title = "${m.job.name} · ${choice.title}",
                            subtitle = "누르면 이 일에 맞는 달력과 기능으로 다음 질문을 이어 가요",
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
