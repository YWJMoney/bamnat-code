package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.WayPicker
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.pay.MoneyField
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.SlipGlance
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WayPick
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import java.time.YearMonth

/** [PAYP-18] The words of 명세서로 맞추기, in one place for the card, the sheet and the tests. */
object WayPickWords {
    const val CARD_TITLE = "지난달 명세서로 맞추기"
    const val CARD_TEXT = "명세서의 지급 총액과 기본급만 넣으면, 같은 회사 급여 방식 가운데 내 명세서와 가장 맞는 것을 골라 넣어요."
    const val PRIVATE = "계산 방식은 보이지 않고, 명세서 숫자는 휴대폰 밖으로 나가지 않아요."
    const val WHOLE_MONTH = "한 달을 다 일한 달의 명세서로 해 주세요. 그 달 잔업·특근을 달력에 넣지 않았으면 그만큼 차이가 나요."
    const val USE = "이 방식 넣기"
}

/**
 * [PAYP-18] Whether 명세서로 맞추기 fits this pay: a company's shared way for a monthly pay (교대·일반 직장인), or a first pay. 알바·일당
 * (the 기본급 on a payslip is not the 시급), 가게·사장님·투자 (no company way) never see it.
 */
fun wayPickFits(shared: Boolean, wage: WageType?, server: Boolean): Boolean = shared && server && (wage == null || wage == WageType.MONTHLY)

/** The card over the 기본급 in [PendingDone]: one tap to the sheet. */
@Composable
internal fun WayPickCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(WayPickWords.CARD_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(WayPickWords.CARD_TEXT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("명세서로 맞추기") }
        }
    }
}

/**
 * [PAYP-18] The payslip's numbers (typed, or read from a photo or text with [com.bamnat.app.ui.pay.PayslipFillDialog]), then the
 * company's versions tried against it, the closest picked. [onUse] puts the picked one in with the payslip's 기본급 and the
 * allowances it named; the ways' values are never shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WayPickSheet(
    c: AppContainer,
    company: com.bamnat.app.company.CompanyScope,
    way: PendingWay,
    onUse: (WayPicker.Option, WayPick.Slip) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(Today.month().minusMonths(1)) }
    var gross by rememberSaveable { mutableStateOf<Long?>(null) }
    var base by rememberSaveable { mutableStateOf<Long?>(null) }
    val parts = remember { mutableStateMapOf<SlipGlance.Part, Long>() }
    var allowances by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var more by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WayPicker.Result?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    val clear = { result = null; chosen = null }
    if (reading) {
        com.bamnat.app.ui.pay.PayslipFillDialog(title = "명세서 읽기", onDismiss = { reading = false }) { read ->
            reading = false
            val slip = read.payslip() ?: return@PayslipFillDialog
            read.month?.let { month = it }
            gross = slip.gross
            base = slip.base ?: base
            parts.clear()
            parts.putAll(WayPick.Slip.of(month, slip).parts)
            allowances = slip.allowances
            more = parts.isNotEmpty()
            clear()
            c.telemetry.event("pay_pick", "slip_read")
        }
        return
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(WayPickWords.CARD_TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("‘${way.company}’ 급여 방식 가운데 ${month.monthValue}월 명세서와 가장 맞는 것을 골라요. ${WayPickWords.PRIVATE}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MonthChips(month) { month = it; clear() }
            OutlinedButton(onClick = { reading = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("명세서 사진이나 글로 채우기") }
            MoneyField("지급 총액 (세전)", gross, onChange = { gross = it; clear() }, placeholder = "명세서의 ‘지급 합계’ 또는 ‘총 지급액’")
            MoneyField("기본급", base, onChange = { base = it; clear() }, placeholder = "명세서의 기본급 줄")
            if (allowances.isNotEmpty()) Text("명세서에서 읽은 수당 ${allowances.size}개도 같이 맞춰 봐요: ${allowances.keys.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!more) TextButton(onClick = { more = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("연장·야간·휴일 금액도 넣기 (더 정확해요)") }
            else SlipGlance.Part.entries.filter { it != SlipGlance.Part.ALLOWANCE }.forEach { p ->
                MoneyField(p.label, parts[p], onChange = { v -> if (v == null) parts.remove(p) else parts[p] = v; clear() },
                    placeholder = "명세서의 ${p.label} 줄, 모르면 비워 두세요")
            }
            Text(WayPickWords.WHOLE_MONTH, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val r = result
            if (r == null) Button(
                onClick = {
                    val slip = WayPick.Slip(month, gross ?: return@Button, base ?: return@Button, parts.toMap(), allowances)
                    busy = true
                    scope.launch {
                        result = try {
                            WayPicker(c, company).pick(way.companyId, way.dept, slip, mapOf(way.setup to way.text))
                        } catch (e: CompanyException) {
                            WayPicker.Result.Failed(companyProblem(e))
                        }
                        chosen = (result as? WayPicker.Result.Picked)?.options?.firstOrNull()?.tried?.id
                        busy = false
                    }
                },
                enabled = !busy && (gross ?: 0) > 0 && (base ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("가장 맞는 방식 찾기")
            }
            else PickResult(r, WayPick.Slip(month, gross ?: 0, base, parts.toMap(), allowances), chosen, { chosen = it }) { o ->
                onUse(o, WayPick.Slip(month, gross ?: 0, base, parts.toMap(), allowances))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("닫기") }
        }
    }
}

/** Last month first (the payslip most have), and the two before it. */
@Composable
private fun MonthChips(month: YearMonth, onPick: (YearMonth) -> Unit) {
    val last = remember { Today.month().minusMonths(1) }
    val months = remember(month) { (listOf(last, last.minusMonths(1), last.minusMonths(2)) + month).distinct().sortedDescending() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        months.forEach { m ->
            com.bamnat.app.ui.components.FilterChip(selected = m == month, onClick = { onPick(m) }, label = { Text("${m.monthValue}월 명세서") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun PickResult(
    r: WayPicker.Result,
    slip: WayPick.Slip,
    chosen: String?,
    onChoose: (String) -> Unit,
    onUse: (WayPicker.Option) -> Unit,
) {
    when (r) {
        WayPicker.Result.None -> Text("이 회사에는 아직 맞춰 볼 급여 방식이 없어요.", style = MaterialTheme.typography.bodyLarge)
        is WayPicker.Result.Failed -> Text(r.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        is WayPicker.Result.Picked -> {
            val best = r.options.first()
            val far = WayPick.far(best.tried, slip)
            Text(WayPick.line(best.tried, r.options.size, slip) { won(it) }, style = MaterialTheme.typography.titleMedium,
                color = if (far) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (r.options.size > 1 || far) r.options.forEach { o ->
                val gap = if (o.tried.gap == 0L) "차이 없음" else "${won(o.tried.gap)} 차이"
                val where = o.tried.glance.differs.takeIf { it.isNotEmpty() }?.joinToString("·") { it.label }?.let { " · ${it}에서 차이" }.orEmpty()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = o.tried.id == chosen, role = Role.RadioButton, onClick = { onChoose(o.tried.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = o.tried.id == chosen, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(o.label + if (o == best && !far) " · 가장 맞아요" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(gap + where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val pick = r.options.firstOrNull { it.tried.id == chosen } ?: best
            if (pick.tried.unknown.isNotEmpty()) Text(
                "사람마다 다른 수당 ${pick.tried.unknown.size}개(${pick.tried.unknown.joinToString(", ")})는 명세서에서 찾지 못해 빼고 셌어요. 넣은 뒤 급여 화면에서 금액을 넣을 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.skipped > 0) Text("받지 못한 방식 ${r.skipped}개는 빼고 맞춰 봤어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (far) Text("명세서 숫자를 다시 보거나, 기본급만 넣어 지금 방식으로 시작해도 돼요.", style = MaterialTheme.typography.bodyMedium)
            if (far) OutlinedButton(onClick = { onUse(pick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("그래도 이 방식 넣기") }
            else Button(onClick = { onUse(pick) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(WayPickWords.USE) }
        }
    }
}
