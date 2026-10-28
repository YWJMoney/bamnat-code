package com.bamnat.app.ui.pay

import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * U 1.32 [UX132-03] (사용자 2026-10-28 "근무·급여 › 예상 급여 › 급여 계산 … 조정하는 기능을 찾기가 아주 어려워"): the pay
 * screen in four tabs, so what is changed is no longer the 16번째 card under the result.
 */
enum class PayTab(val label: String) {
    RESULT("결과"), VALUES("내 급여 값"), RULES("회사 규칙"), TOOLS("도구"),
}

/**
 * U 1.32 [UX132-02] Where a 고치기 chip, a search result or a feature link opens the pay screen: a tab, and for some the
 * value's own dialog at once.
 */
enum class PayFocus(val key: String, val label: String, val tab: PayTab) {
    BASE("base", "기본급", PayTab.VALUES),
    ITEMS("items", "수당", PayTab.VALUES),
    HOURS("hours", "근무시간", PayTab.RULES),
    PERIOD("period", "월급날·정산", PayTab.RULES),
    RULES("rules", "회사 규칙", PayTab.RULES),
    TOOLS("tools", "도구", PayTab.TOOLS),
    ;

    companion object {
        fun of(key: String?): PayFocus? = entries.firstOrNull { it.key == key }
        /** The chips on 근무·급여's pay card. */
        val CHIPS = listOf(BASE, ITEMS, HOURS, PERIOD, RULES)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PayTabRow(tab: PayTab, onTab: (PayTab) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp, modifier = Modifier) {
        PayTab.entries.forEach { t ->
            Tab(selected = t == tab, onClick = { onTab(t) }, text = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}
