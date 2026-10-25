package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.QuickDecimalChips
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.HolidayWork

/**
 * [HOLI-01][HOLI-03] 빨간날에 일하면: the four ways companies pay a shift on a 공휴일, as cards, and for 특근·대근처럼 the
 * 1회 금액 of each kind of shift on the roster (모닝·이브닝·나이트·당직). The same picker serves the pay setup and 회사 규칙.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HolidayWorkPicker(rules: CompanyRules, shiftTypes: List<ShiftType>, onChange: (CompanyRules) -> Unit, modifier: Modifier = Modifier) {
    val mode = rules.holidayWork
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HolidayWork.entries.forEach { m ->
            AnswerCard(m.label, m.detail, mode == m) { if (mode != m) onChange(rules.withHolidayWork(m)) }
        }
        if (mode == HolidayWork.AS_EXTRA) {
            Label("무엇처럼 셈하나요")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ExtraKind.SPECIAL_ID, ExtraKind.COVER_ID).forEach { k ->
                    FilterChip(selected = rules.publicHolidayAs == k, onClick = { onChange(rules.withHolidayWork(HolidayWork.AS_EXTRA, k)) },
                        label = { Text(rules.extraName(k)) })
                }
            }
            val working = shiftTypes.filter { !it.isOff && it.hasTimes }
            if (working.isNotEmpty()) {
                Label("빨간날 근무 1회 금액")
                working.forEach { t ->
                    QuickDecimalChips(
                        value = rules.holidayShiftPay(t.id).toDouble(),
                        onChange = { onChange(rules.withHolidayShiftPay(t.id, it.toLong())) },
                        name = "빨간날 ${t.name}",
                        quick = listOf(50_000.0, 80_000.0, 100_000.0),
                        step = 10_000.0, range = 0.0..2_000_000.0, start = 0.0,
                        format = { if (it <= 0) "법대로" else "%,d원".format(it.toLong()) }, valueWidth = 120.dp,
                    )
                }
            }
            Hint("밤샘 근무는 시작한 날로 셈해요(빨간날 나이트 1회). 야간(22~06시) 수당은 근무 종류대로 따로 붙어요. " +
                "금액을 ‘법대로’로 두면 그 근무는 휴일 가산으로 계산해요.")
        }
        if (mode == HolidayWork.PREMIUM) {
            Toggle("밤샘 근무는 시작한 날로 휴일 판단", "끄면 빨간날 0시~24시에 일한 시간만 휴일이에요(빨간날 나이트 23~07은 1시간). " +
                "회사가 근무 시작일로 정하면 켜 주세요", rules.holidayByShiftStart) { onChange(rules.copy(holidayByShiftStart = it)) }
        }
        Text("빨간날에 휴가를 쓰면 근무한 날로 인정되고 그날 몫은 한 번만 받아요. 일하지 않았으니 야간·휴일 가산은 붙지 않아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
