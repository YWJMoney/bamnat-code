package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.Tone

/**
 * 2.0.0 [RVK-29] (검토 HQ-44, 디자인 품질 조건 1·4 '설명은 한 줄 요약과 ⓘ') How a card's figures were counted, said without filling
 * the card: one line ([summary]) with ⓘ in the card, the sentences ([notes]) in a sheet that the line opens. The line is the app's
 * [ToneLine] (안내 ⓘ, the whole line a 48dp place to press), the sheet is [AppSheet] with its X. A card keeps one of these at most,
 * so it has one ⓘ; with no [notes] nothing is drawn. Unlike [Explain] (the same words every visit, shrinking as they are learned),
 * these say this month's amounts and show only while there is something to say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesLine(summary: String, title: String, notes: List<String>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    ToneLine(summary, modifier, tone = Tone.Plain, onClick = { open = true })
    // Opened all the way: at 글자 200% the sentences are longer than half a screen, and a half-open sheet hid the last of them.
    if (open) AppSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            notes.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
