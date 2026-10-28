package com.bamnat.app.ui.pay.rulesheet

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.company.CompanyWayText
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.pay.Hint
import com.bamnat.app.ui.pay.Label
import com.bamnat.app.ui.pay.MoneyField
import com.bamnat.app.ui.pay.MonthlyHoursStepper
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.CommonTerms
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HourlyFacts
import com.bamnat.core.pay.HourlySource
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * [HOURLY-04][PHOTO-33] 1.30 AW: the 통상시급 card, apart from the 칸 (사용자 2026-10-27 "통상시급의 뜻을 잘 모르겠다. 통상시급이 개인정보라면
 * 기본급의 %로 통상시급이 결정되는지 물어보든지, 명세서와 규칙 기반으로 자동 적용" · "통상시급 수정 및 자동 적용 방식 도입").
 *
 * - What it is, in a line: 시간당 기본 단가, what 연장·야간·휴일 are counted on.
 * - Its value now and where it came from (명세서·회사 규칙·광장·직접, [PayProfile.ordinarySource]·[PayProfile.hoursSource]).
 * - What a photo or payslip showed, one tap to use ("명세서로 보면 통상임금 기준 180시간이에요 [이 값 쓰기]").
 * - The 기준 시간 chips, the company's first when the 광장 has it (1.30 BA [com.bamnat.app.ui.pay.MonthlyHoursChips]; a different value is sent as 의견, 묶음 BC).
 * - Not known: "통상임금이 기본급의 몇 %인가요?" (기본급만 100%), or the 통상시급 typed. Anything the user picks is [HourlySource.TYPED].
 *
 * [profile] is the setup the sheet goes onto (its 기본급 and 통상 수당); [slipBase] the 기본급 a payslip read showed, used when the setup
 * has none yet. [terms] are applied here to show what they come to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HourlyCard(
    profile: PayProfile?,
    month: YearMonth,
    terms: CommonTerms,
    onTerms: (CommonTerms) -> Unit,
    modifier: Modifier = Modifier,
    plaza: CompanyWayText? = null,
    slipBase: Long? = null,
) {
    val shown = remember(profile, terms, slipBase, month) {
        profile?.let { p ->
            val based = slipBase?.takeIf { it > 0 && (p.base.on(month).value <= 0 || p.wageType == WageType.MONTHLY) }
                ?.let { b -> p.copy(wageType = WageType.MONTHLY, base = ValueTimeline(listOf(DatedValue(month.atDay(1), b)))) } ?: p
            RuleText.apply(based, terms.toChanges(), month)
        }
    }
    val view = shown?.let { HourlyFacts.of(it, month) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("통상시급", style = MaterialTheme.typography.titleMedium)
                Text("시간당 기본 단가", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                view?.source?.let { SourceTag(it) }
            }
            Text("연장·야간·휴일 수당을 셀 때 쓰는 1시간 값이에요. 기본급과 매달 같은 수당을 기준 시간으로 나눠요.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            when {
                view != null -> {
                    Text(won(view.hourly), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                    Text(if (view.set) "${sourceWords(view.source)} 넣은 값이에요" else view.formula, style = MaterialTheme.typography.bodyMedium)
                }
                shown != null && shown.wageType != WageType.MONTHLY -> Text("시급·일당은 그 금액이 통상시급이에요.", style = MaterialTheme.typography.bodyMedium)
                else -> Text("기본급을 넣으면 바로 보여요.", style = MaterialTheme.typography.bodyMedium)
            }

            // What a photo or payslip showed: one tap to use, or to stop using.
            terms.offers.forEach { f -> Offer(f, terms.taken(f), onUse = { onTerms(terms.take(f)) }, onDrop = { onTerms(terms.drop(f)) }) }

            if (shown == null || shown.wageType == WageType.MONTHLY) {
                HorizontalDivider()
                val hours = terms.hours ?: shown?.monthlyHours ?: BaseAsk.HOURS_CHOICES.first()
                Label("기준 시간 (한 달을 몇 시간으로 치나요?)")
                // [HOURLY-04] 1.30 BA's chips (회사 규칙 n시간 first), as on the pay screen; a value of its own on the stepper.
                val company = plaza?.hours
                com.bamnat.app.ui.pay.MonthlyHoursChips(hours, onHours = { h ->
                    onTerms(if (h == null) terms.copy(hours = null, hoursSource = null, hoursWhy = "")
                        else terms.withHours(h, if (h == company) HourlySource.PLAZA else HourlySource.TYPED))
                }, companyHours = company, companyName = plaza?.way?.company)
                MonthlyHoursStepper(hours, onChange = { onTerms(terms.withHours(it, HourlySource.TYPED)) })
                // Hours other than the company's: one tap sends them to the 광장 as an opinion (묶음 BC).
                if (plaza != null && company != null && hours != company) {
                    var proposing by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { proposing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("${hours}시간으로 같은 회사에 의견 보내기") }
                    if (proposing) com.bamnat.app.ui.company.HoursProposalSheet(plaza, hours,
                        from = terms.hoursSource?.takeIf { it != HourlySource.TYPED }?.label ?: "직접") { proposing = false }
                }
                Hint("명세서·계약서의 ‘통상시급 = 기본급 ÷ ○○시간’의 ○○이에요. 모르면 그대로 두세요.")
                PercentAsk(view, shown, month, terms, onTerms)
            }
        }
    }
}

/** "명세서에서", "직접", for the line under the value. */
private fun sourceWords(s: HourlySource?): String = when (s) {
    null -> "직접"
    HourlySource.TYPED -> "직접"
    else -> "${s.label}에서"
}

@Composable
private fun SourceTag(s: HourlySource) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(if (s == HourlySource.TYPED) "직접 넣음" else "${s.label}에서", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** [HOURLY-01][PHOTO-33] One value read: what it says, how it was worked out, and [이 값 쓰기] or that it is used. */
@Composable
private fun Offer(f: RuleFinding, taken: Boolean, onUse: () -> Unit, onDrop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (taken) colors.primaryContainer else colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Text(f.change.summary, style = MaterialTheme.typography.titleSmall, color = if (taken) colors.onPrimaryContainer else colors.onSurface)
            if (f.sourceLine.isNotBlank()) Text(f.sourceLine, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (taken) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            if (f.confidence < 0.6 && !taken) Text("확실하지 않아요. 명세서 한 장 더 있으면 확실해져요.", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taken) {
                    Icon(Icons.Filled.CheckCircle, null, tint = colors.primary, modifier = Modifier.padding(end = 4.dp))
                    Text("이 값으로 계산해요", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDrop, modifier = Modifier.heightIn(min = 48.dp)) { Text("안 쓰기") }
                } else FilledTonalButton(onClick = onUse, modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp)) { Text("이 값 쓰기") }
            }
        }
    }
}

/**
 * [HOURLY-04] When the 통상시급 is not known: "통상임금이 기본급의 몇 %인가요?" (the company's, not the person's) worked out on the
 * 기준 시간 at once, or the 통상시급 itself typed. 100% is 기본급 alone: the set value is taken off and it is worked out again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PercentAsk(view: HourlyFacts.View?, shown: PayProfile?, month: YearMonth, terms: CommonTerms, onTerms: (CommonTerms) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(open, role = Role.Switch) { open = it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("통상시급을 모르겠어요", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
    }
    if (!open) return
    val base = view?.base ?: BaseAsk.known(shown, month)
    val hours = terms.hours ?: shown?.monthlyHours ?: BaseAsk.HOURS_CHOICES.first()
    var pct by rememberSaveable { mutableStateOf((view?.percentOfBase ?: 100).toDouble()) }
    Text("통상임금이 기본급의 몇 %인가요?", style = MaterialTheme.typography.titleSmall)
    Hint("기본급만이면 100%예요. 매달 같은 수당이나 상여가 들어가면 더 커요. 같은 회사면 모두 같은 값이라 동료에게 물어봐도 돼요.")
    com.bamnat.app.ui.components.QuickDecimalChips(
        value = pct, onChange = { pct = it }, name = "기본급의 몇 %", quick = HourlyFacts.PERCENT_CHOICES.map { it.toDouble() },
        step = 5.0, range = 100.0..400.0, unit = "%",
    )
    if (base == null || base <= 0) {
        Hint("기본급을 먼저 넣으면 통상시급을 계산해 드려요.")
    } else {
        val hourly = HourlyFacts.fromPercent(base, pct, hours)
        Text("= 기본급 ${won(base)} × ${pct.roundToLong()}% ÷ ${hours}시간 ≈ ${won(hourly.roundToLong())}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
        Button(onClick = { onTerms(terms.withHourly(if (pct <= 100.0) 0.0 else hourly)); open = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("이 값으로 계산하기")
        }
    }
    var typed by rememberSaveable { mutableStateOf<Long?>(null) }
    MoneyField("통상시급을 알면 직접 (선택)", typed, onChange = { v -> typed = v; if (v != null && v > 0) onTerms(terms.withHourly(v.toDouble())) },
        placeholder = "명세서의 통상시급")
}
