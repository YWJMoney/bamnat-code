package com.bamnat.app.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.TabPicks
import com.bamnat.app.ui.withBar
import com.bamnat.core.layout.TabSlots
import com.bamnat.core.work.WorkStyle

/**
 * 1.39 G2 통합 5차 PC (식당 사장 폰: 아래 칸에 '관리'가 없어 나 › 아래 메뉴 고치기로 5번 칸을 바꿔서야 급여 대장에 닿음): a shop's
 * calendar, while 사장님 급여 대장 is on and 관리 is on no place of the bar, says once where the 급여 대장 is and puts 관리 on the bar in
 * one press. The bar stays the user's (NAV2-01): nothing is moved until they press, and × hides it for good.
 */
internal object BossLedgerLine {
    const val KEY = "boss_ledger_bar"

    /** Shown on [style]'s calendar when the flag is on and the bar has no 관리 yet. */
    fun wanted(style: WorkStyle?, payrollOn: Boolean, slots: List<TabSlots.Slot>): Boolean =
        style == WorkStyle.BUSINESS && payrollOn && !TabSlots.shows(slots, TabPicks.MANAGE)

    /**
     * [slots] with 관리 as a place of its own: a new place while the phone bar has room, else in place of the last one (what the PC
     * did by hand; that screen stays under the 나 button, [TabSlots.missing]).
     */
    fun added(slots: List<TabSlots.Slot>): List<TabSlots.Slot> {
        val more = TabSlots.addTo(slots, slots.size, TabPicks.MANAGE)
        return if (TabSlots.shows(more, TabPicks.MANAGE)) more else TabSlots.replace(slots, minOf(slots.lastIndex, TabSlots.MAX - 1), TabPicks.MANAGE)
    }
}

@Composable
internal fun BossLedgerNotice(settings: AppSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hints = remember { com.bamnat.app.ui.calendar.HintStore(context) }
    var shown by remember { mutableStateOf(!hints.done(BossLedgerLine.KEY)) }
    if (!shown) return
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("직원이 있으면 급여 대장을 써 보세요", style = MaterialTheme.typography.bodyMedium)
                Text("평소 근무로 한 달 급여·주휴·4대보험과 명세서까지 계산해요. 아래 칸 '관리'에 있어요.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val app = context.appContainer
                    val slots = TabSlots.resolve(settings.tabSlots, settings.tabOrder, TabPicks.known)
                    app.settingsRepository.update { it.withBar(BossLedgerLine.added(slots)) }
                    hints.markDone(BossLedgerLine.KEY); shown = false
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("아래 칸에 '관리' 넣기") }
            }
            IconButton(onClick = { hints.markDone(BossLedgerLine.KEY); shown = false }) { Icon(Icons.Filled.Close, "안내 닫기") }
        }
    }
}
