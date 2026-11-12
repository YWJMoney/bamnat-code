package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.LookUndo
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.core.recipe.ShiftLooks
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVP-45] 검토 PC-81 (스튜디오에서 입힌 뒤 달력으로 오면 띠 두 개가 1~2초 늦게 나타나 달력이 327px 밀리고, 그때 누른 칸이 안 열림):
 * the look the studio just put on ('입히기 전으로', PC 재검토 N3 1.36) and the look of the day's shift ([STU-37]) were lines over every
 * tab, drawn once the look had gone on, so they came after the month. On the calendar they are notices now: the band's place is decided
 * as the calendar opens ([NoticeStack]), and the look just put on is said from the moment it is kept, not from when it shows.
 */
internal class LookUndoNote(
    val line: String,
    val undo: () -> Unit,
    val close: () -> Unit,
    /**
     * [RVM-17] PC-57 (띠가 계속 남아 달력이 한 줄 내려가요, 갈래 M): over the month on the first visit to the calendar after 입히기 only;
     * leaving it marks the band seen, and for the rest of the day it is a row of the 알림함 (and '입히기 전 모양으로' in the studio). It
     * stays while that visit lasts, so the month does not move up under a finger (PC-81).
     */
    val band: Boolean,
)

/** For a day after the studio put a look on: the look from before, one tap away; null otherwise (and in safe mode, where looks rest). */
@Composable
internal fun rememberLookUndo(): LookUndoNote? {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // PC 20차: in safe mode the looks rest, so '입힌 모양이에요' would say the opposite of what shows.
    val safe by com.bamnat.app.diag.StartGuard.safe.collectAsStateWithLifecycle()
    val kept = remember(settings, tick) { LookUndo.read(c.appContext) }
    val seenBefore = remember(kept?.second) { LookUndo.seen(c.appContext) }
    androidx.compose.runtime.DisposableEffect(kept?.second) {
        onDispose { if (kept != null && LookUndo.read(c.appContext) != null) LookUndo.markSeen(c.appContext) }
    }
    if (safe || kept == null) return null
    // Shown while the look on is the one put on (not changed back another way); PC-81: and from the moment it was kept, while the
    // studio's look is still going on, so the band has its place as the calendar opens.
    val fresh = Today.millis() - kept.second in 0 until LOOK_FRESH_MS
    if (!fresh && c.recipes.pieces.lookSnapshot() == kept.first) return null
    return LookUndoNote(
        line = "스튜디오에서 입힌 모양 · ${com.bamnat.app.ui.studio.ago(kept.second)}",
        undo = {
            c.recipes.pieces.restoreLook(kept.first)
            LookUndo.clear(c.appContext)
            scope.launch { runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(c.appContext) } }
            tick++
        },
        close = {
            LookUndo.clear(c.appContext)
            tick++
        },
        band = !seenBefore,
    )
}

/** The notice whole, as the bell's sheet shows it. */
@Composable
internal fun LookUndoLine(note: LookUndoNote) {
    NudgeCard(
        note.line, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), icon = Icons.Outlined.Palette,
        detail = "하루 동안 입히기 전 모양으로 돌아갈 수 있어요.",
        action = "입히기 전으로", onAction = note.undo, onClose = note.close, closeLabel = "입힌 모양 안내 닫기",
    )
}

/** [STU-37] Which 근무에 따라 look is on; [close] hides it until the mode changes (on every tab). */
internal class ShiftLookNote(val night: Boolean, val close: () -> Unit) {
    val line: String get() = if (night) "야간 근무 중이라 야간 모양이에요" else "쉬는 날이라 쉬는 날 모양이에요"
}

@Composable
internal fun rememberShiftLookNote(): ShiftLookNote? {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val looks = remember(settings.shiftLooks) { ShiftLooks.decode(settings.shiftLooks) }
    val mode = com.bamnat.app.ui.theme.rememberShiftLookMode(looks, c)
    if (mode == ShiftLooks.Mode.NORMAL || ShiftLookShut.mode == mode.name) return null
    return ShiftLookNote(mode == ShiftLooks.Mode.NIGHT) { ShiftLookShut.mode = mode.name }
}

@Composable
internal fun ShiftLookLine(note: ShiftLookNote) {
    NudgeCard(
        note.line, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        icon = if (note.night) Icons.Outlined.DarkMode else Icons.Outlined.Spa,
        onClose = note.close, closeLabel = "모양 안내 닫기",
    )
}

/** The 근무에 따라 mode whose line was closed, kept while the app runs (the calendar's notice and the other tabs' line share it). */
internal object ShiftLookShut {
    var mode: String? by mutableStateOf(null)
}

/** PC-81: How long after the studio kept the look before it the undo is said even while the new look is still going on. */
internal const val LOOK_FRESH_MS = 30_000L
