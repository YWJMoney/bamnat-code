package com.bamnat.app.ui.pay.rulesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.ui.company.CompanyWayText
import com.bamnat.app.ui.pay.Hint
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

/** [PHOTO-30] The one button under the result: its words, and whether it can be pressed. */
data class SheetAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * [RULE-ED-02] 1.30 AW: the company's rules written as 칸 on a screen of their own (촬영이 안 되는 회사, 광장의 '고쳐서 제안하기'): the 통상시급
 * card, the 칸, '칸 더하기', and under them, always in view, what the month comes to ([preview], worked out a moment after each change,
 * off the main thread) with the one button. [onDone] gets the sheet; nothing is saved here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheetEditor(
    title: String,
    state: RuleSheetState,
    host: RuleSheetHost,
    onDismiss: () -> Unit,
    onDone: (RuleSheet) -> Unit,
    preview: (suspend (List<RuleChange>) -> SheetPreview?)? = null,
    doneLabel: String = "적용",
    plaza: CompanyWayText? = null,
    showHourly: Boolean = true,
) {
    val shown = rememberSheetPreview(state.sheet, preview)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") } },
                    )
                },
                bottomBar = {
                    SheetPreviewBar(shown.value, shown.working, SheetAction(doneLabel, enabled = state.sheet.toChanges().isNotEmpty()) { onDone(state.sheet) })
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Hint("명세서에 있는 수당·더 받는 근무·빠지는 돈을 칸으로 하나씩 더해요. 사진이 없어도 돼요.")
                        if (showHourly) HourlyCard(shown.value?.profile ?: host.profile, host.month, state.sheet.common, state::terms, plaza = plaza)
                        RuleSheetCells(state, host, empty = "아직 칸이 없어요. 아래 ‘칸 더하기’에서 자주 쓰는 수당을 고르면 금액만 넣으면 돼요.")
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** The sheet's month, worked out [DEBOUNCE_MS] after the last change; [working] while it is. */
class ShownPreview(val value: SheetPreview?, val working: Boolean)

@Composable
internal fun rememberSheetPreview(sheet: RuleSheet, preview: (suspend (List<RuleChange>) -> SheetPreview?)?): ShownPreview {
    var last by androidx.compose.runtime.remember { mutableStateOf<SheetPreview?>(null) }
    val working by produceState(preview != null, sheet, preview) {
        val p = preview ?: run { value = false; return@produceState }
        value = true
        delay(DEBOUNCE_MS)
        last = try { p(sheet.toChanges()) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        value = false
    }
    return ShownPreview(last, working)
}

internal const val DEBOUNCE_MS = 250L

/**
 * [PHOTO-30][RULE-ED-06][PHOTO-34] Under the result, fixed: what the month comes to with the 칸 as they are now (with a payslip: what
 * it said and how far apart), the changes to look at before they are applied, and the one button.
 */
@Composable
internal fun SheetPreviewBar(preview: SheetPreview?, working: Boolean, action: SheetAction, fallback: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerHigh, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                when {
                    preview != null && preview.gross != null -> {
                        Text("${preview.month.monthValue}월 명세서 ${won(preview.gross)} · 앱 계산 ${won(preview.total)}", style = MaterialTheme.typography.titleSmall)
                        val gap = preview.gap ?: 0
                        Text(if (preview.close) "거의 같아요 (차이 ${won(abs(gap))})" else "차이 ${if (gap > 0) "+" else "−"}${won(abs(gap))} · 칸을 더하거나 고치면 바로 다시 계산해요",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = if (preview.close) colors.primary else colors.tertiary)
                    }
                    preview != null -> {
                        Text("${preview.month.monthValue}월 규칙으로 계산", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text(won(preview.total) + (preview.hourly?.takeIf { it > 0 }?.let { " · 통상시급 ${won(it)}" } ?: ""),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                    }
                    working -> Text("계산하는 중", style = MaterialTheme.typography.titleSmall)
                    fallback != null -> Text(fallback, style = MaterialTheme.typography.titleSmall)
                }
            }
            val changes = preview?.changes.orEmpty()
            if (changes.isNotEmpty()) {
                TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "바뀌는 것 접기" else "바뀌는 것 ${changes.size}개 보기") }
                if (open) Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    changes.forEach { Row { Text("•  "); Text(it, style = MaterialTheme.typography.bodySmall) } }
                    preview?.hints.orEmpty().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                }
            }
            Button(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(action.label) }
        }
    }
}
