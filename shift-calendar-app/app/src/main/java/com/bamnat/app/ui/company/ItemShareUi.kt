package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer

/*
 * [ITEM-06] 1.30 묶음 BC: the allowances whose amount a company's way already holds (회사 공통 정액, 기본급의 %), shown read only
 * where the way is applied, so the user types only the ones that differ by person. CompanyPayScreen (묶음 BA) calls it.
 */

/** Read-only lines "식대 · 회사 공통 · 매달 · 200,000원" for the common allowances of the company file [text]; nothing when none. */
@Composable
fun CommonItemLines(text: String, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val items = remember(text) { c.companyShare.commonItems(text) }
    if (items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("회사가 정한 수당 (따로 넣지 않아도 돼요)", style = MaterialTheme.typography.labelLarge)
        items.forEach { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(i.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(i.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
