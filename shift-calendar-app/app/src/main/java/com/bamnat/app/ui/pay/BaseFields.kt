package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.WageType
import java.time.YearMonth

/**
 * [PAYF-06] 1.29 묶음 AP (사용자 2026-10-27 "사용자가 기본급 입력하고 통상시수가 있다면 입력한 후"): the one field every way into
 * the pay ends on — 월 기본급 (or 시급·일당), never skipped — and, folded under it and never required, what the 통상시급 is
 * counted on: the 월 소정근로시간 (209시간 …) or the 통상시급 itself, from which the hours are worked out.
 *
 * [hours] null leaves the setup's hours as they are. [profile] is what the 통상시급 is worked out on (its 통상 수당 count).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BaseFields(
    wage: WageType,
    base: Long?,
    onBase: (Long?) -> Unit,
    hours: Int?,
    onHours: (Int?) -> Unit,
    profile: PayProfile?,
    month: YearMonth,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoneyField(
            when (wage) { WageType.MONTHLY -> "월 기본급"; WageType.HOURLY -> "시급"; WageType.DAILY -> "일당" },
            base, onChange = onBase,
            placeholder = when (wage) { WageType.MONTHLY -> "명세서 ‘기본급’ 줄의 금액"; WageType.HOURLY -> "계약서의 시급"; WageType.DAILY -> "하루 받는 금액" },
        )
        if (base == null || base <= 0) {
            Text("회사 방식은 기본급으로 계산해요. ${wage.baseName()}만 넣으면 이번 달 예상 급여가 바로 보여요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (wage != WageType.MONTHLY) return@Column
        var open by rememberSaveable { mutableStateOf(hours != null) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(open, role = Role.Switch) { open = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("통상시급 기준 시간도 알아요 (선택)", style = MaterialTheme.typography.bodyLarge)
                Text(hours?.let { "월 ${it}시간으로 계산해요" } ?: "모르면 그대로 두세요. 보통 209시간이에요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (!open) return@Column
        Text("명세서나 계약서의 ‘통상시급 = 기본급 ÷ ○○시간’의 ○○이에요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BaseAsk.HOURS_CHOICES.forEach { h -> FilterChip(selected = hours == h, onClick = { onHours(if (hours == h) null else h) }, label = { Text("${h}시간") }) }
        }
        MonthlyHoursStepper(hours ?: profile?.monthlyHours ?: BaseAsk.HOURS_CHOICES.first(), onChange = onHours)
        // The 통상시급 itself, when that is the number the payslip shows: the hours follow from it.
        var hourly by rememberSaveable { mutableStateOf<Long?>(null) }
        MoneyField("통상시급을 알면 (선택)", hourly, onChange = { v ->
            hourly = v
            val b = base ?: return@MoneyField
            val p = profile ?: return@MoneyField
            if (v != null) BaseAsk.hoursFromHourly(p.copy(wageType = WageType.MONTHLY), month, b, v)?.let(onHours)
        }, placeholder = "명세서의 통상시급")
        val h = hourly
        if (h != null && (base == null || profile == null || BaseAsk.hoursFromHourly(profile.copy(wageType = WageType.MONTHLY), month, base, h) == null)) {
            Text(if (base == null) "기본급을 먼저 넣으면 시간을 맞춰 드려요." else "이 통상시급으로는 맞는 시간이 없어요. 시간을 직접 골라 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
