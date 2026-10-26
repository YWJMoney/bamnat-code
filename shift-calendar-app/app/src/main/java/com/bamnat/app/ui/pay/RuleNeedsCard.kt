package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.TimeRange
import com.bamnat.app.ui.components.TimeRangeDialog
import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.Unknowns
import java.time.LocalTime

/**
 * [PAYS-17] 1.27 순번 448 (사용자 "규칙만 놓은 사람은 시수와 기본급등 필수 사항은 직접 입력해야해 … 바로 입력할수 있게 안내
 * 제안"): "이것만 넣으면 정확해져요" over the month's pay, one line per value still empty or guessed ([RuleOnlyNeeds]) with a
 * button straight to it; a line goes once its value is in, the card once all are. Without a payslip matched yet, one
 * more line says what a payslip adds (실제 입금액·기본급 대조), never that one is needed.
 */
@Composable
internal fun RuleNeedsCard(needs: List<RuleOnlyNeeds.Need>, withSlip: Boolean, onFix: (RuleOnlyNeeds.Need) -> Unit) {
    if (needs.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (needs.size == 1) "이것만 넣으면 정확해져요" else "이것 ${needs.size}개만 넣으면 정확해져요",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            needs.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(n.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(n.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    // Several "넣기" on one card: each says which value it puts in (TalkBack).
                    FilledTonalButton(onClick = { onFix(n) }, modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { onClick(label = "${n.label} 넣기") { onFix(n); true } }) { Text("넣기") }
                }
            }
            if (!withSlip) Text("명세서는 없어도 돼요. 넣으면 실제 입금액·기본급과 맞춰 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** [PAYS-17] 월 소정근로시간(시수), the one stepper; saving marks it said ([RuleOnlyNeeds.settled]). */
@Composable
internal fun MonthlyHoursDialog(hours: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var h by remember { mutableStateOf(hours) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("월 소정근로시간(시수)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("명세서나 근로계약서의 ‘통상시급 = 기본급 ÷ ○○시간’에서 ○○이에요. 주 40시간이면 보통 209시간이에요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonthlyHoursStepper(h, { h = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(209, 226, 243).forEach { v -> OutlinedButton(onClick = { h = v }) { Text("${v}시간") } }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(h) }) { Text("넣기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** [PAYS-17] 직원 5명 이상인지: yes or no, and the guess mark goes. */
@Composable
internal fun FiveOrMoreDialog(onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회사 직원이 5명 이상인가요?") },
        text = {
            Text("5명 미만 회사는 연장·야간·휴일 근무에 50%를 더 주지 않아도 돼요(근로기준법 제11조·시행령 별표 1). 아르바이트·파트타이머도 셈에 들어가요.",
                style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = { Button(onClick = { onSave(true) }) { Text("5명 이상이에요") } },
        dismissButton = { TextButton(onClick = { onSave(false) }) { Text("5명 미만이에요") } },
    )
}

/** [PAYS-17] A kind of work's times, from the pay screen: its pay and night premium count from them. */
@Composable
internal fun ShiftTimesDialog(type: ShiftType, onDismiss: () -> Unit, onSave: (TimeRange) -> Unit) {
    TimeRangeDialog(
        title = "‘${type.name}’ 근무 시간",
        initial = TimeRange(type.start ?: LocalTime.of(9, 0), type.end ?: LocalTime.of(18, 0), type.breakMinutes.takeIf { it > 0 } ?: 60),
        onDismiss = onDismiss, onConfirm = onSave, confirmLabel = "넣기",
    )
}

/** [PAYS-17] [p] with 5인 이상 said. */
internal fun fiveOrMoreSaid(p: PayProfile, yes: Boolean): PayProfile =
    p.copy(fiveOrMoreEmployees = yes, estimatedFields = p.estimatedFields - Unknowns.FIVE_OR_MORE)

/** [PAYS-17] The needs of [profile]'s pay in [month], from the kinds of work on the calendar in its pay period. */
internal fun payNeeds(profile: PayProfile, snapshot: com.bamnat.app.data.ScheduleSnapshot, month: java.time.YearMonth): List<RuleOnlyNeeds.Need> {
    val from = profile.period.start(month)
    val to = profile.period.end(month)
    val worked = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
        .mapNotNull { snapshot.resolver.resolve(it).shiftType }.distinctBy { it.id }.toList()
    return RuleOnlyNeeds.of(profile, month, worked)
}

/**
 * [PAYS-19] 1.27 순번 449: 명세서와 맞춰 보기, right under the month's total. Two ways, named apart (사진 841 had
 * '명세서로 맞춰 보기' and '사진으로 회사 방식 맞추기' side by side at the bottom): 금액 비교하기 puts a payslip's amounts
 * against the app's line by line (프로); 계산 방식 찾기 reads a payslip or the company's rules page and puts the
 * company's way in (free, no payslip needed).
 */
@Composable
internal fun SlipMatchCard(profile: PayProfile, gate: com.bamnat.app.tier.Gate, onCompare: () -> Unit, onFind: () -> Unit) {
    com.bamnat.app.ui.components.SectionCard(title = "명세서와 맞춰 보기") {
        val v = profile.verified
        Text(
            if (v != null) "${v.month.year}년 ${v.month.monthValue}월 명세서와 맞춰 봤어요. 새 명세서가 오면 또 맞춰 볼 수 있어요."
            else "명세서가 있으면 실제 입금액·기본급과 비교해요. 없으면 회사 계산 방법(취업규칙·수당표) 사진만으로도 계산 방식을 찾아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // One under the other: side by side the 프로 mark was cut on a 393dp phone (1.27 사진 1149).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("금액 비교하기", maxLines = 1)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 4.dp))
                com.bamnat.app.ui.money.ProPill(locked = gate.locked(com.bamnat.app.tier.Feature.PAYSLIP_MATCH)) // [TIER-06]
            }
            OutlinedButton(onClick = onFind, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("계산 방식 찾기", maxLines = 1) }
        }
    }
}

/** [PAYS-20] The value behind a line of the month's pay that can be fixed from it: the base, or the allowance it is. */
internal fun lineFix(line: com.bamnat.core.pay.PayLine, profile: PayProfile): ValueTarget? = when (line.kind) {
    com.bamnat.core.pay.LineKind.BASE -> ValueTarget.Base
    com.bamnat.core.pay.LineKind.ALLOWANCE -> (profile.items.firstOrNull { it.name == line.label }
        ?: profile.items.filter { line.label.startsWith(it.name) }.maxByOrNull { it.name.length })?.let { ValueTarget.Item(it.id) }
    else -> null
}

/** [PAYS-20] Lines worked out from hours and rates: fixed in the company's rules (가산율, 대근 금액). */
internal val RATE_LINES = setOf(
    com.bamnat.core.pay.LineKind.OVERTIME, com.bamnat.core.pay.LineKind.NIGHT, com.bamnat.core.pay.LineKind.HOLIDAY,
    com.bamnat.core.pay.LineKind.EXTRA, com.bamnat.core.pay.LineKind.EXTRA_HOURS,
)
