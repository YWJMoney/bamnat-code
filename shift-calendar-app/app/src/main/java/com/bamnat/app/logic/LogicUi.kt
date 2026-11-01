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
    val logic = remember(settings.logic) { Logic.decode(settings.logic) }
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
                        runCatching { LogicRunner.run(c, b.act, date, f.scope(date), b.label) }
                    }
                },
                label = { Text(listOfNotNull(b.emoji, b.label).joinToString(" ")) },
                modifier = Modifier.semantics { contentDescription = "${b.label} 단추, 누르면 ${b.act.say()}" },
            )
        }
    }
}

/** The line after a button or rule acted, with 되돌리기 when it wrote data (메모·체크·가계부). */
@Composable
fun LogicMessageHost(c: AppContainer, snackbar: SnackbarHostState) {
    val message by LogicRunner.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        LogicRunner.consumeMessage()
        val canUndo = LogicStore(c.appContext).undo != null
        val r = snackbar.showSnackbar(m, actionLabel = if (canUndo) "되돌리기" else null, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed && canUndo) {
            snackbar.showSnackbar(if (LogicRunner.undo(c)) "되돌렸어요" else "되돌릴 것이 없어요")
        }
    }
}
