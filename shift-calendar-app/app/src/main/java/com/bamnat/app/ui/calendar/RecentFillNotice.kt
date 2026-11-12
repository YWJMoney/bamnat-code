package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.RecentFill
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.theme.Tone
import kotlinx.coroutines.delay

/**
 * 2.0.0 [RVL-18] (검토 U07b-11·SCAN-13) A month put in at once (한 달 근무 넣기, 근태·캘린더 가져오기, 이번 달 전부 넣기), said over the
 * calendar with 되돌리기 for [RecentFill.WINDOW_MINUTES] minutes, the same for all of them, after their screen and snackbar are gone.
 * Taken back, it says once what it did (and the days it left alone, changed again since). Draws nothing otherwise.
 */
@Composable
fun RecentFillNotice(calendarId: String, modifier: Modifier = Modifier) {
    val entry by RecentFill.entry.collectAsStateWithLifecycle()
    val closed by RecentFill.closed.collectAsStateWithLifecycle()
    val said by RecentFill.said.collectAsStateWithLifecycle()
    // Read again when its time is up, so the band goes by itself.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(entry) {
        entry?.let { delay(RecentFill.left(it) + 1_000) }
        tick++
    }
    val e = remember(entry, closed, tick) { RecentFill.current(calendarId)?.takeIf { it !== closed } }
    val place = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    when {
        e != null -> NudgeCard(
            e.text, place,
            icon = Icons.AutoMirrored.Outlined.Undo,
            detail = "${RecentFill.WINDOW_MINUTES}분 동안 여기서 되돌릴 수 있어요",
            action = "되돌리기",
            onAction = { RecentFill.undo(e) },
            onClose = { RecentFill.close(e) },
        )
        said != null -> NudgeCard(said!!, place, tone = Tone.Good, onClose = RecentFill::heard)
    }
}
