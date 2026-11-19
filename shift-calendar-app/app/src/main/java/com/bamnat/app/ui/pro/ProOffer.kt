package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.ads.LeaseAdOption
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AG 1.27 [MON-12][INVITE-11] What a tap on a locked 프로 feature opens (사용자 리뷰 2026-10-25: "프로가 아닌사람들이 프로기능을
 * 클릭항때 광보보기와 친구초대로 프로 사용권 얻기 보기를 보여줘서 초대를 권유해야해"): one sheet with the three ways, 프로 itself
 * (체험), '광고 보고 <묶음> 7일 열기' (839, the rewarded ad's own rules, RewardedAds.kt) and '친구를 초대하면 무료로 써요'. Before
 * the 정식 출시 switch nothing is locked, so it never opens; the alarm check's 등급 미리 보기 shows it.
 */
object ProOffer {
    /** The feature asked for, or [ANY] for a tap that does not name one; null when the sheet is closed. */
    private val _asking = MutableStateFlow<Feature?>(null)
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    val asking: StateFlow<Feature?> = _asking.asStateFlow()

    /** [MON-13] The app's gate, set by [com.bamnat.app.AppContainer], for [tryFeature]. */
    @Volatile var gate: com.bamnat.app.tier.FeatureGate? = null

    fun show(feature: Feature? = null) {
        _asking.value = feature
        _open.value = true
    }

    fun close() {
        _open.value = false
        _asking.value = null
    }
}

/** AG 1.27 [MON-12] The sheet, once in the app frame (AppRoot). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProOfferHost(c: AppContainer) {
    val open by ProOffer.open.collectAsStateWithLifecycle()
    if (!open) return
    val feature by ProOffer.asking.collectAsStateWithLifecycle()
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    // [MON-13] 839: closed without an ad or 프로 counts as a 'no' for the bundle's ad (three hide it for 30 days).
    val grantsAtOpen = remember(feature) { gate.leases.grants.size }
    val dismiss = {
        val b = feature?.let { gate.bundle(it) }
        val offered = b != null && gate.live && !gate.preview && gate.tier < com.bamnat.app.tier.Tier.PRO &&
            com.bamnat.core.tier.FeatureLease.canWatch(gate.leases, b, com.bamnat.core.time.Today.instant(), com.bamnat.core.time.Today.date())
        if (offered && gate.leases.grants.size == grantsAtOpen) c.featureGate.decline(b!!)
        ProOffer.close()
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ProOfferContent(c, feature, gate, onPro = { ProOffer.close(); ProPaywall.show(plans = true) })
    }
}

@Composable
internal fun ProOfferContent(c: AppContainer, feature: Feature?, gate: Gate, onPro: () -> Unit) {
    var said by remember { mutableStateOf<String?>(null) }
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 2.0.0 [RVO-29] HQ-152 (조건 12): the feature's name and '프로 기능이에요' on two lines with no dot between, so at 글자 200%
        // a dot is never left at a line's end; read out as one heading.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                feature?.let { Text("‘${it.label}’", style = MaterialTheme.typography.titleLarge) }
                Text("프로 기능이에요", style = if (feature == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (feature == null) androidx.compose.ui.graphics.Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 1.39 G2 PC ef175fbf: "이렇게 쓸 수 있어요." ended with nothing after it; the lines below are the ways to open it.
        Text("아래 방법으로 열 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 1.36 PC 재검토: while the bundle's free first week is there, it is the one blue button on top and 프로 is a text button.
        if (feature != null && gate.canTry(feature)) {
            LeaseAdOption(feature, gate, onOpened = ProOffer::close)
            androidx.compose.material3.TextButton(onClick = onPro, modifier = Modifier.fillMaxWidth()) { Text(proButtonText()) }
        } else {
            Button(onClick = onPro, modifier = Modifier.fillMaxWidth()) { Text(proButtonText()) }
            LeaseAdOption(feature, gate, onOpened = ProOffer::close)
        }
        if (c.inviteApi.enabled) {
            HorizontalDivider()
            // 2.0.0 [RVO-13] (HQ-48) 프로 화면 맨 위 초대 카드와 같은 이름과 한 줄 약속.
            Text(INVITE_FREE_TITLE, style = MaterialTheme.typography.titleMedium)
            Text("${com.bamnat.core.tier.InviteRules.promise(state.rules)}. 링크로 깐 친구는 앱이 저절로 알아봐요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.view?.let { v -> com.bamnat.core.tier.InviteRules.status(v.given, v.pending, v.waiting) }?.let {
                com.bamnat.app.ui.components.DotText(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            // 2.0.0 [RVO-13] 총괄 사진 검토 HQ-70: in the dark the 옅은 채움 weighed as much as '처음 7일 무료로 열기' (조건 2); a line here.
            InviteShareAction(c, onMessage = { said = it }, makeLabel = "친구 초대 링크 만들기", outlined = true)
        }
        Spacer(Modifier.width(1.dp).padding(bottom = 12.dp))
    }
}
