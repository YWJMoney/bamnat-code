package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.TopLevel
import com.bamnat.core.layout.TabOrder

/** [NAV-07] 1.26 AD: the row's name, which settings search and help open. */
const val TAB_ORDER_ROW = "첫 화면·메뉴 순서"

/** [NAV-07] What the row says now: the screen the app opens on, then the tabs in order. */
internal fun tabOrderLine(settings: AppSettings): String {
    val tabs = TopLevel.ordered(settings.tabOrder).map { it.labelFor(settings.workStyle) }
    return "앱을 켜면 ${tabs.first()} · " + tabs.joinToString(", ") + ". 아래 메뉴를 길게 눌러도 바꿔요"
}

/** [NAV-07] 설정 › 화면과 글자 › 첫 화면·메뉴 순서, the way to the order without a long press (GestureAlternativesTest). */
@Composable
internal fun TabOrderRow(settings: AppSettings, onChange: (List<String>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    NavRow(TAB_ORDER_ROW, tabOrderLine(settings)) { open = true }
    if (open) TabOrderDialog(settings, onChange, onDismiss = { open = false })
}

/**
 * [NAV-07] The tabs in order, each moved up or down with its arrows (and TalkBack's actions); the bar changes at once
 * behind the dialog. The top one is the app's first screen (D82). 처음 순서로 puts 달력 first again.
 */
@Composable
internal fun TabOrderDialog(settings: AppSettings, onChange: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val tabs = TopLevel.ordered(settings.tabOrder)
    val keys = tabs.map { it.route }
    val haptics = LocalHapticFeedback.current
    val move: (String, Int) -> Unit = { key, by ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(TabOrder.toSave(TabOrder.step(keys, key, by)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TAB_ORDER_ROW) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "맨 위 메뉴가 앱을 켤 때 처음 보이는 화면이고, 아래 메뉴도 이 순서로 놓여요. 아래 메뉴를 길게 누른 채 끌어도 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                tabs.forEachIndexed { i, tab ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TabOrderLine(i, tabs.size, tab, tab.labelFor(settings.workStyle), onUp = { move(tab.route, -1) }, onDown = { move(tab.route, 1) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
        dismissButton = {
            TextButton(onClick = { onChange(emptyList()) }, enabled = settings.tabOrder.isNotEmpty()) { Text("처음 순서로") }
        },
    )
}

@Composable
private fun TabOrderLine(index: Int, count: Int, tab: TopLevel, label: String, onUp: () -> Unit, onDown: () -> Unit) {
    val first = index == 0
    val last = index == count - 1
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            // One stop for TalkBack: "1, 메모, 첫 화면", with 위로·아래로 as actions.
            .semantics(mergeDescendants = true) {
                customActions = listOfNotNull(
                    CustomAccessibilityAction("위로") { onUp(); true }.takeIf { !first },
                    CustomAccessibilityAction("아래로") { onDown(); true }.takeIf { !last },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Icon(tab.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (first) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("첫 화면", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        IconButton(onClick = onUp, enabled = !first) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "$label 위로") }
        IconButton(onClick = onDown, enabled = !last) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "$label 아래로") }
    }
}
