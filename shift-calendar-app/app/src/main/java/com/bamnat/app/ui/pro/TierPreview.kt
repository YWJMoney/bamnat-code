package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.BuildConfig
import com.bamnat.app.billing.PassOffer
import com.bamnat.app.billing.Plan
import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.components.SectionCard

/**
 * 2.0.0 [RVO-01] (PC-54: 로컬 확인 빌드는 정식 출시 전이라 프로 관문이 열려 있어, 프로가 아닐 때 모습과 '하루 써 보기'를 볼 길이 없었어요.
 * 미리 보기는 알람 점검 깊은 곳에만 있어 PC 확인이 찾지 못했어요): 등급 미리 보기를 프로 화면 맨 위에도 둬요. 알람 점검의 것과 같은 한 부품이라
 * 둘이 늘 같아요.
 *
 * - 보이는 곳: 디버그 빌드에만([com.bamnat.app.tier.FeatureGate]가 미리 보기를 받아 주는 곳과 같아요). 서버 깃발·관리자 휴대폰·숨은 손짓으로
 *   열리는 길은 없어요. 모든 길이 상수 `BuildConfig.DEBUG`로 막혀 있어 R8이 출시 빌드에서 이 카드와 예시 요금제 글귀를 지우고,
 *   `tools/release_dex_check.py`가 출시 APK의 dex에 이 글귀가 없는지 봐요.
 * - 무료: 정식 출시 뒤 프로가 없는 사람의 모습(잠긴 기능, 7일 맛보기, 기능 작품의 '하루 써 보기', 요금제 창).
 * - 리더: 팀·직원 요금제를 모두 가진 모습.
 */
fun tierPreviewShown(debug: Boolean = BuildConfig.DEBUG): Boolean = BuildConfig.DEBUG && debug

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TierPreviewCard(c: AppContainer, gate: Gate, showNow: Boolean = true) {
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val preview = settings.previewTier
    SectionCard(title = TIER_PREVIEW_TITLE) {
        Text(
            if (gate.live && !gate.preview) "정식 출시를 해서 실제 등급으로 보여요. 미리 보기는 개발용 빌드에서만 돼요."
            else "무료를 고르면 정식 출시 뒤 프로가 없는 분의 모습이에요. 결제는 되지 않아요.",
            style = MaterialTheme.typography.bodyMedium,
        )
        // On the 프로 screen its own 지금 card is right under this one.
        if (showNow) Text("지금: ${gate.access.say(com.bamnat.core.time.Today.zone())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.bamnat.app.ui.components.FilterChip(selected = preview == null, onClick = { setPreview(c, null) }, label = { Text("실제") })
            listOf(Tier.FREE to "무료", Tier.PRO to "프로", Tier.LEADER to "팀·직원 요금제").forEach { (t, words) ->
                com.bamnat.app.ui.components.FilterChip(selected = preview == t, onClick = { setPreview(c, t) }, label = { Text(words) })
            }
        }
        // Debug builds only: the free weeks and each work's 하루 써 보기 from the start, to look at them again (two text buttons at most).
        if (BuildConfig.DEBUG) {
            var said by rememberSaveable { mutableStateOf<String?>(null) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { c.featureGate.resetTrials(); said = "일주일 무료 맛보기를 처음부터 다시 써요" }) { Text("맛보기 처음부터") }
                TextButton(onClick = { com.bamnat.app.logic.FxAccess.forgetTries(c); said = "작품마다 하루 써 보기를 다시 할 수 있어요" }) { Text("하루 써 보기 처음부터") }
            }
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

internal const val TIER_PREVIEW_TITLE = "출시 뒤 화면 미리 보기 · 개발용"

private fun setPreview(c: AppContainer, t: Tier?) {
    c.settingsRepository.update { it.copy(previewTier = t) }
    c.telemetry.event("tier_preview", t?.name ?: "real")
}

/**
 * [RVO-01] 미리 보기에서 Play가 요금제를 아직 주지 않았을 때(로컬 빌드는 Play 결제에 붙지 않아요) 요금제 창 모양을 볼 수 있게, 미리 보기에만 쓰는
 * 예시 요금제예요. 단추를 눌러도 결제하지 않아요([PlanRows]가 미리 보기를 봐요). 가격 글자에 '예시'가 붙어 실제 값으로 읽히지 않아요.
 */
// 출시 dex 점검(tools/release_dex_check.py)이 찾은 것: 맨 위 값(listOf)은 파일의 정적 초기화에 남아 R8이 지우지 못했어요. 함수로 두면
// BuildConfig.DEBUG 안에서만 불려 출시 빌드에서 함께 지워져요.
internal fun previewPlans(): List<Plan> = listOf(
    Plan(com.bamnat.app.billing.ProductIds.MONTHLY, "4,990원(예시)", 4_990_000_000, "KRW", "P1M", trial = false, offerToken = ""),
    Plan(com.bamnat.app.billing.ProductIds.YEARLY, "39,000원(예시)", 39_000_000_000, "KRW", "P1Y", trial = true, offerToken = ""),
)
internal fun previewPass(): PassOffer = PassOffer("45,000원(예시)", 45_000_000_000, "KRW")

/** [RVO-01] The plans a screen shows: Play's, or in a preview without them the example ones. */
internal fun shownPlans(gate: Gate, plans: List<Plan>): List<Plan> = if (BuildConfig.DEBUG && gate.preview && plans.isEmpty()) previewPlans() else plans

/** [RVO-01] A buy button in a preview says so and never opens Play (debug builds only, as the preview). */
internal fun previewNoPay(gate: Gate): Boolean = BuildConfig.DEBUG && gate.preview

/** [RVO-01] The 1년 이용권 row: Play's offer, or in a preview the example one. */
internal fun shownPass(gate: Gate, pass: PassOffer?): PassOffer? = pass ?: if (BuildConfig.DEBUG && gate.preview) previewPass() else null

internal const val PREVIEW_NO_PAY = "미리 보기라 결제는 되지 않아요"
