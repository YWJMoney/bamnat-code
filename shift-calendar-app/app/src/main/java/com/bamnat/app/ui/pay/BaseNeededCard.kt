package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import java.time.YearMonth

/**
 * [PAYG-04] 1.30 묶음 BA (사용자 2026-10-27 "기본급 없이 닫으면 결과 화면에 예상 급여 대신 '기본급만 넣으면 끝나요'(0원이나
 * 이상한 금액이 보이지 않음)"): the one card for a pay that has its rules but no 기본급 of its own, wherever it shows — the month's
 * pay in place of its total (never 0원 or a sum of premiums on nothing), and the easy setup's last page. The base goes in right
 * here; [onSave] gets it with the 통상시급's hours when the user said them (null keeps the setup's).
 */
@Composable
internal fun BaseNeededCard(
    profile: PayProfile,
    month: YearMonth,
    onSave: (Long, Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** The button's words: "예상 급여 보기" on the pay, "이대로 계산" where it ends a setup. */
    action: String = "예상 급여 보기",
    /** [PAYG-08] The company's 통상시급 hours, when a company's way is in use. */
    companyHours: Int? = null,
    companyName: String? = null,
) {
    var base by rememberSaveable { mutableStateOf<Long?>(null) }
    var hours by rememberSaveable { mutableStateOf<Int?>(null) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${month.monthValue}월 예상 급여", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("${profile.wageType.baseName()}만 넣으면 끝나요", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.semantics { heading() })
            Text("계산 방식은 다 들어 있어요. ${profile.wageType.baseName()}을 넣으면 이번 달 예상 급여가 바로 보여요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                BaseFields(profile.wageType, base, { base = it }, hours, { hours = it }, profile, month, Modifier.padding(12.dp),
                    companyHours = companyHours, companyName = companyName)
            }
            Button(onClick = { base?.takeIf(BaseAsk::ready)?.let { onSave(it, hours) } }, enabled = BaseAsk.ready(base),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(action) }
        }
    }
}
