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
import androidx.compose.material3.Button
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
import com.bamnat.app.ads.DayPassOption
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AG 1.27 [MON-12][INVITE-11] What a tap on a locked 프로 feature opens (사용자 리뷰 2026-10-25: "프로가 아닌사람들이 프로기능을
 * 클릭항때 광보보기와 친구초대로 프로 사용권 얻기 보기를 보여줘서 초대를 권유해야해"): one sheet with the three ways, 프로 itself
 * (체험), '광고 보고 오늘 하루 열기' (the rewarded ad's own rules, RewardedAds.kt) and '친구 초대로 프로 이용권 받기'. Before
 * the 정식 출시 switch nothing is locked, so it never opens; the alarm check's 등급 미리 보기 shows it.
 */
object ProOffer {
    /** The feature asked for, or [ANY] for a tap that does not name one; null when the sheet is closed. */
    private val _asking = MutableStateFlow<Feature?>(null)
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    val asking: StateFlow<Feature?> = _asking.asStateFlow()

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
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = ProOffer::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ProOfferContent(c, feature, gate, onPro = { ProOffer.close(); goPro() })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(feature?.let { "‘${it.label}’ · 프로 기능이에요" } ?: "프로 기능이에요", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        Text("세 가지 중에 골라 쓸 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onPro, modifier = Modifier.fillMaxWidth()) { Text(proButtonText()) }
        DayPassOption(gate)
        if (c.inviteApi.enabled) {
            HorizontalDivider()
            Text("친구 초대로 프로 이용권 받기", style = MaterialTheme.typography.titleMedium)
            Text("${state.rules.words()}. 링크로 깐 친구는 앱이 저절로 알아봐요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            InviteProgressLines(c)
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            InviteShareAction(c, onMessage = { said = it }, makeLabel = "친구 초대 링크 만들기", tonal = true)
        }
        Spacer(Modifier.width(1.dp).padding(bottom = 12.dp))
    }
}
