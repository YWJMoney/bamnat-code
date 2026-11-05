package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.plaza.IndustryGroups
import com.bamnat.core.plaza.OrderState
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles

/**
 * [EASE-06][CYC-07] 1.38 (오늘근무 분석 2026-11-05: 목록에 있는 곳은 검색 없이 3번 안에): the public rows looked through as people
 * know their work, 업종 → 회사·기관 → 사업장·직무, without typing a name. A company with one row is picked on the second tap; one
 * with several (철도 역무·승무, 공장마다) shows them on a third. Whose order the row spells (or the usual one for a count such as
 * 6근2휴) goes on into the setup at once ([onPick]); a row with no order opens its sheet to pick one ([onNeedsOrder]).
 *
 * The groups are core's [IndustryGroups] (갈래 3, CYC-09), so a new 업종 word in the public file needs no change here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleBrowser(
    onPick: (PublicCycle, String) -> Unit,
    onNeedsOrder: (PublicCycle) -> Unit,
    modifier: Modifier = Modifier,
    rows: List<PublicCycle> = PublicCycles.all,
) {
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var company by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(rows) { IndustryGroups.counts(rows) }
    val group = IndustryGroups.byKey(groupKey)
    val take = { c: PublicCycle -> c.setupCodes()?.let { onPick(c, it) } ?: onNeedsOrder(c) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (group == null) {
            Text("업종으로 찾기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text("이름을 몰라도 돼요. 업종 → 회사 → 직무를 누르면 시간과 순서까지 채워요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEach { (g, n) ->
                    FilterChip(selected = false, onClick = { groupKey = g.key; company = null }, label = { Text("${g.name} $n") },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            return@Column
        }
        val inGroup = remember(rows, group.key) { rows.filter { it.group.key == group.key } }
        val byCompany = remember(inGroup) { inGroup.groupBy { it.company }.toSortedMap() }
        val sites = company?.let { byCompany[it] }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (sites != null) company = null else groupKey = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (sites != null) group.name else "업종 다시 고르기")
            }
        }
        if (sites == null) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(group.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            byCompany.forEach { (name, list) ->
                BrowseRow(
                    title = name,
                    line = if (list.size == 1) orderLine(list.first()) + list.first().site.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
                    else "사업장·직무 ${list.size}곳",
                    onClick = { if (list.size == 1) take(list.first()) else company = name },
                )
            }
        } else {
            Text(company.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("사업장이나 직무를 골라 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sites.forEach { c -> BrowseRow(title = c.site.ifEmpty { c.formName }, line = orderLine(c), onClick = { take(c) }) }
        }
        Text("${PublicCycles.BADGE} · 기사·채용 공고·노무 사이트에서 모은 근무주기예요. 회사가 확인한 정보는 아니어서 다음 화면에서 맞춰 봐요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [EASE-06] What a public row brings into the first setup without its sheet: its own order, or the usual one for a count it
 * gave (the times' screen says so and offers the others); none for a roster. Null when the row has no order at all, which is
 * picked on the sheet among the usual ones of its 근무형태.
 */
fun PublicCycle.setupCodes(): String? = when {
    roster -> ""
    orderState == OrderState.NONE || codes.isEmpty() -> null
    else -> codes
}

@Composable
private fun BrowseRow(title: String, line: String, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
