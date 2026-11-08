package com.bamnat.app.ui.pro

import com.bamnat.app.ui.theme.washSurface
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.bamnat.app.ads.LeaseAdOption
import com.bamnat.app.appContainer
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.settings.SettingsGo
import com.bamnat.app.ui.settings.SettingsLinks

/**
 * [MON-04] A pro feature for someone without 프로: drawn with the user's own numbers, then blurred
 * (Android 12+; a cover below it), with a lock, the way to try 프로 and, for a bundled feature, a rewarded ad for 7 days (839).
 * Nothing under it can be tapped or read out. With 프로, or before the launch, it is just [content].
 */
@Composable
fun ProLock(feature: Feature, gate: Gate, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (gate.allows(feature)) {
        // 1.36 PC 재검토 (7일 열림 알림은 짧아 끝 날짜를 읽기 빠듯): opened for 7 days, the card itself says until when.
        val until = gate.trialUntil(feature)?.takeIf { gate.tier < feature.minTier }
        if (until == null) {
            Box(modifier) { content() }
            return
        }
        Column(modifier) {
            content()
            Text(
                "${gate.bundle(feature)?.label?.let { "$it 묶음 · " }.orEmpty()}${untilWords(java.time.Instant.ofEpochMilli(until))}까지 열려 있어요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        return
    }
    Box(modifier.heightIn(min = MIN_HEIGHT)) {
        Box(Modifier.then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp) else Modifier).clearAndSetSemantics { }) { content() }
        Box(
            Modifier.matchParentSize()
                .background(MaterialTheme.colorScheme.washSurface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.35f else 0.9f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "프로 알아보기") { openPro(feature) },
        )
        LockPanel(feature, gate, Modifier.align(Alignment.Center).padding(12.dp))
    }
}

@Composable
private fun LockPanel(feature: Feature, gate: Gate, modifier: Modifier) {
    Surface(modifier.widthIn(max = 360.dp), shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(feature.label, style = MaterialTheme.typography.titleSmall)
            }
            Text("프로에서 쓸 수 있어요. 흐리게 보이는 건 내 기록으로 만든 화면이에요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (gate.canTry(feature)) {
                LeaseAdOption(feature, gate)
                androidx.compose.material3.TextButton(onClick = { ProPaywall.show(plans = true) }, modifier = Modifier.fillMaxWidth()) { Text(proButtonText()) }
            } else {
                Button(onClick = { ProPaywall.show(plans = true) }, modifier = Modifier.fillMaxWidth()) { Text(proButtonText()) }
                LeaseAdOption(feature, gate)
            }
            // AG 1.27 [MON-12][INVITE-11] The third way, as in the sheet.
            if (LocalContext.current.appContainer.inviteApi.enabled) {
                OutlinedButton(onClick = { openPro(feature) }, modifier = Modifier.fillMaxWidth()) { Text("친구 초대로 프로 이용권 받기") }
            }
        }
    }
}

/** "1개월 무료로 써 보기" while Play offers this account the trial, otherwise "프로 알아보기". */
@Composable
internal fun proButtonText(): String {
    val plans = com.bamnat.app.billing.rememberPlans()
    return if (plans.any { it.trial && it.yearly }) "1개월 무료로 써 보기" else "프로 알아보기"
}

/**
 * A tap on a locked 프로 feature. AG 1.27 [MON-12] (결정 AG-D4): the sheet with 프로, the rewarded ad and 친구 초대
 * ([ProOffer]), not the 프로 screen at once.
 */
fun openPro() = ProOffer.show()

fun openPro(feature: Feature) = ProOffer.show(feature)

/** Straight to the 프로 screen: its own buttons, 프로 둘러보기, the alarm check. */
fun goPro() = SettingsLinks.go(SettingsGo.PRO)

private val MIN_HEIGHT = 200.dp

/**
 * [MON-13] 1.36: a tap on a trial feature (명세서 금액 비교하기, 세후 예상). Open when 프로 or its free week is on; a first use while it
 * is locked starts the week and opens it; false when it asks for 프로 (the caller opens [openPro]).
 */
fun tryFeature(feature: Feature): Boolean = ProOffer.gate?.tryOrAsk(feature) ?: true

/**
 * [MON-13] Under a trial feature: while its week runs, until when it is free and that 프로 keeps it; before its first use, that the
 * first use opens a week. Nothing for 프로 or before the launch.
 */
@Composable
fun TrialNote(feature: Feature, gate: com.bamnat.app.tier.Gate, modifier: Modifier = Modifier) {
    val until = gate.trialUntil(feature)
    val text = when {
        until != null && gate.tier < feature.minTier -> {
            // 1.36 PC B2·B6: how it was opened, and the end with its hour (a week from 3 p.m. ends at 3 p.m., not at the day's end).
            val how = gate.bundle(feature)?.let { b -> gate.leases.grants.lastOrNull { it.bundle == b }?.why }
            val opened = when (how) {
                com.bamnat.core.tier.FeatureLease.Why.AD -> "광고로 열었어요"
                com.bamnat.core.tier.FeatureLease.Why.NO_AD -> "광고가 안 와서 그냥 열었어요"
                com.bamnat.core.tier.FeatureLease.Why.BACK -> "다시 오셔서 7일 열었어요"
                else -> "처음 7일은 무료예요"
            }
            "$opened · ${untilWords(java.time.Instant.ofEpochMilli(until))}까지. 그 뒤에는 광고 한 편으로 7일 더 열거나 프로로 계속 써요"
        }
        gate.canTry(feature) -> "처음 쓰는 날부터 일주일은 무료예요"
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** 1.36 PC B6: "12월 16일 오후 3시" (and the minutes when not on the hour), in the phone's zone. */
fun untilWords(at: java.time.Instant): String {
    val t = at.atZone(com.bamnat.core.time.Today.zone())
    val h = t.hour % 12
    val half = if (t.hour < 12) "오전" else "오후"
    return "${t.monthValue}월 ${t.dayOfMonth}일 $half ${if (h == 0) 12 else h}시" + (if (t.minute > 0) " ${t.minute}분" else "")
}
