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
 *
 * [PAYG-02] 1.30 묶음 BA: the field is given empty where a company's way goes in; the setup's own base is [keepBase], one chip
 * to pick ("지금 기본급 2,090,000원 그대로 쓰기"), never typed in for the user. [PAYG-08] [companyHours] the 통상시급 hours the
 * company's way says (LG화학 180, [com.bamnat.app.data.CompanyShare.hoursOf]): shown open, first, as the company's, with whose
 * they are ([companyName]); other hours are the user's to pick, and are offered back as an opinion once the way is in.
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
    companyHours: Int? = null,
    companyName: String? = null,
    keepBase: Long? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoneyField(
            when (wage) { WageType.MONTHLY -> "월 기본급"; WageType.HOURLY -> "시급"; WageType.DAILY -> "일당" },
            base, onChange = onBase,
            placeholder = when (wage) { WageType.MONTHLY -> "명세서 ‘기본급’ 줄의 금액"; WageType.HOURLY -> "계약서의 시급"; WageType.DAILY -> "하루 받는 금액" },
        )
        // [PAYG-02] The base the setup has, one tap away and never put in by itself.
        if (keepBase != null && base != keepBase) {
            FilterChip(selected = false, onClick = { onBase(keepBase) }, label = { Text("지금 ${wage.baseName()} ${won(keepBase)} 그대로 쓰기") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        if (base == null || base <= 0) {
            // [J133-05] 1.33: a part-timer is told 시급, never 기본급.
            Text(if (wage == WageType.MONTHLY) "회사 방식은 기본급으로 계산해요. 기본급만 넣으면 이번 달 예상 급여가 바로 보여요."
                else "${wage.baseName()}만 넣으면 이번 달 예상 급여가 바로 보여요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (wage != WageType.MONTHLY) return@Column
        val company = companyHours?.takeIf { it in BaseAsk.HOURS_RANGE }
        // [PAYG-08] The company's hours are shown, not folded away: they are what the pay is counted on.
        var open by rememberSaveable { mutableStateOf(hours != null || company != null) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(open, role = Role.Switch) { open = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (company != null) {
                    Text("통상시급 기준 시간: " + if (hours == null || hours == company) "회사 규칙 ${company}시간" else "월 ${hours}시간",
                        style = MaterialTheme.typography.bodyLarge)
                    // Said once, under the chips, while it is the company's own.
                    if (hours != null && hours != company) Text("회사 규칙은 ${company}시간이에요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("통상시급 기준 시간도 알아요 (선택)", style = MaterialTheme.typography.bodyLarge)
                    Text(hours?.let { "월 ${it}시간으로 계산해요" } ?: "모르면 그대로 두세요. 보통 209시간이에요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (!open) return@Column
        Text("명세서나 계약서의 ‘통상시급 = 기본급 ÷ ○○시간’의 ○○이에요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonthlyHoursChips(hours, onHours, companyHours = company, companyName = companyName)
        MonthlyHoursStepper(hours ?: company ?: profile?.monthlyHours ?: BaseAsk.HOURS_CHOICES.first(), onChange = onHours)
        // The 통상시급 itself, when that is the number the payslip shows: the hours follow from it.
        var hourly by rememberSaveable { mutableStateOf<Long?>(null) }
        MoneyField("통상시급을 알면 (선택)", hourly, onChange = { v ->
            hourly = v
            val b = base ?: return@MoneyField
            val p = profile ?: return@MoneyField
            if (v != null) BaseAsk.hoursFromHourly(p.copy(wageType = WageType.MONTHLY), month, b, v)?.let(onHours)
        }, placeholder = "명세서의 ‘통상시급’ 또는 ‘시급’ 줄")
        val h = hourly
        if (h != null && (base == null || profile == null || BaseAsk.hoursFromHourly(profile.copy(wageType = WageType.MONTHLY), month, base, h) == null)) {
            Text(if (base == null) "기본급을 먼저 넣으면 시간을 맞춰 드려요." else "이 통상시급으로는 맞는 시간이 없어요. 시간을 직접 골라 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * [PAYG-08] 1.30 묶음 BA: the 통상시급 기준 시간 chips, as one piece for every screen that asks them (the 기본급 field here, and
 * 묶음 AW's 통상시급 card): the company's own first as "회사 규칙 180시간" ([companyHours], picked while [hours] is null or the
 * same), then 209·226·243. A tap on another chip picks it, a tap on the one picked lets it go (null: the setup's own stays).
 * When the user's hours differ from the company's, one line says the company's and that they can be sent back as an opinion;
 * with [companyName] the company's line says whose they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyHoursChips(
    hours: Int?,
    onHours: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    companyHours: Int? = null,
    companyName: String? = null,
    /** [FIN130-09] The usual hours after the company's (회사 규칙 설정 also offers 180, as the payslip match tries it). */
    choices: List<Int> = BaseAsk.HOURS_CHOICES,
    /** [FIN130-09] Hours other than the company's sent as an opinion now (회사 규칙 설정); null says it is asked after they go in. */
    onPropose: ((Int) -> Unit)? = null,
) {
    val company = companyHours?.takeIf { it in BaseAsk.HOURS_RANGE }
    val shown = hours ?: company
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (listOfNotNull(company) + choices).distinct().forEach { h ->
                val own = h == company
                FilterChip(
                    selected = shown == h,
                    onClick = { onHours(if (own) h else if (hours == h) null else h) },
                    label = { Text(if (own) "회사 규칙 ${h}시간" else "${h}시간") },
                )
            }
        }
        if (company != null) {
            val differs = BaseAsk.hoursToPropose(hours, company)
            Text(
                if (differs == null) "${companyName?.let { "‘$it’ " } ?: "같은 회사 "}분들이 맞춘 값이에요. 명세서와 다르면 고쳐 주세요."
                else if (onPropose != null) "회사 규칙은 ${company}시간이에요. ${differs}시간이 맞다면 같은 회사 분들께 의견으로 알려 주세요."
                else "회사 규칙은 ${company}시간이에요. ${differs}시간으로 계산하고, 넣은 뒤 같은 회사 분들께 의견으로 보낼지 여쭤볼게요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (differs != null && onPropose != null) {
                androidx.compose.material3.TextButton(onClick = { onPropose(differs) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${differs}시간으로 의견 보내기")
                }
            }
        }
    }
}
