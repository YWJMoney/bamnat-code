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
 * 2.0.0 [RVL-18] (검토 U07b-11·SCAN-13) The month put in at once on [calendarId] (한 달 근무 넣기, 근태·캘린더 가져오기, 이번 달 전부 넣기)
 * that can still be taken back, for [RecentFill.WINDOW_MINUTES] minutes, the same for all of them, after their screen and snackbar are
 * gone; null once its time is up or its band was put away. Read again when the time is up, so the band goes by itself.
 */
@Composable
fun rememberRecentFill(calendarId: String): RecentFill.Entry? {
    val entry by RecentFill.entry.collectAsStateWithLifecycle()
    val closed by RecentFill.closed.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(entry) {
        entry?.let { delay(RecentFill.left(it) + 1_000) }
        tick++
    }
    return remember(entry, closed, tick, calendarId) { RecentFill.current(calendarId)?.takeIf { it !== closed } }
}

/**
 * 2.0.0 [RVL-18] The fill [e] as a card: the band over the calendar says its one line with 되돌리기 (갈래 P 3/3 [RVP-43] HQ-49, the
 * band is drawn from the notice's line, so the '10분' line is here only, in the card opened whole in the 알림함).
 */
@Composable
fun RecentFillNotice(e: RecentFill.Entry, modifier: Modifier = Modifier) {
    NudgeCard(
        e.text, modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        icon = Icons.AutoMirrored.Outlined.Undo,
        detail = "${RecentFill.WINDOW_MINUTES}분 동안 여기서 되돌릴 수 있어요",
        action = "되돌리기",
        onAction = { RecentFill.undo(e) },
        onClose = { RecentFill.close(e) },
    )
}

/** 2.0.0 [RVL-18] Taken back from the calendar, what the undo did (and the days it left alone, changed again since), once. */
@Composable
fun RecentFillSaid(modifier: Modifier = Modifier) {
    val said by RecentFill.said.collectAsStateWithLifecycle()
    said?.let { NudgeCard(it, modifier.padding(horizontal = 16.dp, vertical = 4.dp), tone = Tone.Good, onClose = RecentFill::heard) }
}
