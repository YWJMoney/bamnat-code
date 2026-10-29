package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * U 1.32 [UX132-03] (사용자 2026-10-28 "근무·급여 › 예상 급여 › 급여 계산 … 조정하는 기능을 찾기가 아주 어려워"): the pay
 * screen in four tabs, so what is changed is no longer the 16번째 card under the result.
 */
enum class PayTab(val label: String) {
    RESULT("결과"), VALUES("내 급여 값"), RULES("회사 규칙"), TOOLS("도구"),
    ;

    /** [J133-71] 1.33: 알바's rules are 근무지 규칙, not 회사 규칙. */
    fun labelFor(style: com.bamnat.core.work.WorkStyle?): String = if (this == RULES) "${style?.employer ?: "회사"} 규칙" else label
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

/**
 * U 1.32 [UX132-05] Four tabs share the width, so none slides off a narrow phone. When a quarter of the width is too
 * small for the text size (a 320dp phone at 아주 크게), the four become chips that wrap onto a second row instead.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PayTabRow(tab: PayTab, onTab: (PayTab) -> Unit, style: com.bamnat.core.work.WorkStyle? = null) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        if (maxWidth / 4 < 64.dp * scale) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                PayTab.entries.forEach { t ->
                    com.bamnat.app.ui.components.FilterChip(selected = t == tab, onClick = { onTab(t) }, label = { Text(t.labelFor(style)) })
                }
            }
        } else {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                PayTab.entries.forEach { t ->
                    // The content form of Tab grows with its text (the text form is a fixed 48dp and cuts a second line).
                    Tab(selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(t.labelFor(style), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}
