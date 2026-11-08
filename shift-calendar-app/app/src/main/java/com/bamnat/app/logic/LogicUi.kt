package com.bamnat.app.logic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Formula
import com.bamnat.core.formula.Logic
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [RUL-03] 1.36 883 단추: the buttons a recipe or the studio put at [place], for [date]. A button whose 보이기 조건 is false that day
 * hides. One press does its one safe thing; what it wrote can be taken back from the line that follows ([LogicMessageHost]).
 */
@Composable
fun LogicButtons(place: ButtonPlace, date: LocalDate, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle(c.settingsRepository.current)
    // 1.39 [FX-01] What runs now (without 프로 a 기능 장치 button rests); [ButtonPlace.outside] ones are never chips.
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val taste by FxAccess.taste(c).collectAsStateWithLifecycle()
    val logic = remember(settings.logic, gate, taste) { LogicRunner.logic(c) }
    val buttons = logic.buttons.filter { it.place == place }
    if (buttons.isEmpty()) return
    val provided = LocalDayFacts.current
    val facts by produceState(provided, provided, settings.logic, date) { if (value == null) value = runCatching { LogicRunner.facts(c) }.getOrNull() }
    val scope = rememberCoroutineScope()
    val shown = buttons.filter { b -> b.show.isEmpty() || facts?.let { Formula.eval(b.show, it.scope(date))?.truthy == true } == true }
    if (shown.isEmpty()) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (b in shown) {
            AssistChip(
                onClick = {
                    scope.launch {
                        val f = facts ?: LogicRunner.facts(c)
                        runCatching { LogicRunner.pressButton(c, b, date, f.scope(date)) }
                    }
                },
                label = { Text(listOfNotNull(b.emoji, b.label).joinToString(" ")) },
                modifier = Modifier.semantics { contentDescription = "${b.label} 단추, 누르면 ${com.bamnat.app.ui.recipe.sample(b.act.say())}" },
            )
        }
    }
}

/** The line after a button or rule acted, with 되돌리기 when it wrote data (메모·체크·가계부). */
@Composable
fun LogicMessageHost(c: AppContainer, snackbar: SnackbarHostState) {
    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
        LogicRunner.onScreen = true
        onStopOrDispose { LogicRunner.onScreen = false }
    }
    // PC D16 (낮): one collector for the whole time. Keyed on the message, taking it (null) restarted the effect and cancelled the
    // snackbar it had just asked for, so a line like '안전 모드라 … 쉬어요' never showed, however often the button was pressed.
    LaunchedEffect(Unit) {
        LogicRunner.message.collect { m ->
            if (m == null) return@collect
            LogicRunner.consumeMessage()
            val resting = com.bamnat.app.diag.StartGuard.safe.value
            val canUndo = !resting && LogicStore(c.appContext).undo != null
            val action = when { resting -> "평소대로"; canUndo -> "되돌리기"; else -> null }
            // 1.37 PC 5차: 되돌리기 줄이 너무 빨리 사라졌어요. 되돌릴 게 있으면 길게.
            val r = snackbar.showSnackbar(m, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) when {
                resting -> c.wakeAfterSafe()
                canUndo -> snackbar.showSnackbar(if (LogicRunner.undo(c)) "되돌렸어요" else "되돌릴 것이 없어요")
            }
        }
    }
}


/**
 * [RUL-05] 1.36 메모 속 수식: a memo with {수식} in it shows what it reads as on its day, and that a 틀 made from it fills them in when
 * used (사용자 2026-11-01 "메모와 달력을 연동", "가계부와 연동").
 */
@androidx.compose.runtime.Composable
fun MemoFormulaPreview(memo: com.bamnat.core.memo.Memo, modifier: Modifier = Modifier) {
    val text = if (memo.isChecklist) memo.checklist.joinToString("\n") { it.text } else memo.text
    val src = listOf(memo.title, text).filter { '{' in it }.joinToString("\n")
    if (src.isEmpty()) return
    val facts = LocalDayFacts.current ?: rememberAppDayFacts() ?: return
    val day = memo.date ?: com.bamnat.core.time.Today.date()
    val shown = androidx.compose.runtime.remember(src, facts, day) {
        val scope = facts.scope(day)
        src.lines().map { com.bamnat.core.formula.FormulaText.fill(it, scope) }.joinToString("\n").takeIf { it != src }
    } ?: return
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.material3.Text("수식이 들어 있어요 · ${day.monthValue}/${day.dayOfMonth} 값으로 읽으면", style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        androidx.compose.material3.Text(shown.take(300), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Text("이 메모를 틀로 저장하면 쓸 때마다 그날 값으로 채워져요", style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
