package com.bamnat.app.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.components.ProBadge
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.ProCatalog
import com.bamnat.core.work.WorkStyle

/** [TIER-07] The tier row [id] needs, as the app names it; null for a name this version does not sell. */
fun proFeatureOf(id: String): Feature? = ProCatalog.tierOf(id)?.let { n -> Feature.entries.firstOrNull { it.name == n } }

/** [TIER-07] How a pro feature stands on this phone: 잠김, or usable, with its own switch's state when it has one. */
fun proStateWords(feature: AppFeature, gate: Gate, settings: AppSettings): String {
    val tier = proFeatureOf(feature.id)
    if (tier != null && gate.locked(tier)) return "잠김 · 프로에서 열려요"
    val on = when (feature.id) {
        "alarm.mission", "alarm.missionPhoto" -> settings.alarmMission != "none" || settings.shiftMissions.isNotEmpty()
        "calendar.phone" -> settings.phoneCalendars.isNotEmpty()
        else -> null
    }
    val free = if (!gate.live) "정식 출시 전이라 지금 무료" else "쓸 수 있어요"
    return when (on) {
        true -> "$free · 켜 둠"
        false -> "$free · 꺼 둠"
        null -> free
    }
}

/**
 * [TIER-07] 1.25 묶음 Y (U18): 설정 › 프로 › 프로 기능 모아보기. Every pro feature in one card, the ones that
 * suit the calendar on screen first, each with its 프로 badge, how it stands and a tap that opens it where it
 * lives ([FeatureLinks]); the rest under '다른 일에 쓰는 프로 기능'.
 */
@Composable
fun ProCollectionCard(gate: Gate, settings: AppSettings, onOpen: (AppFeature) -> Unit = { FeatureLinks.open(it) }) {
    val style: WorkStyle? = settings.workStyle
    val all = ProCatalog.of(style)
    val (mine, others) = all.partition { it.fits(style) }
    SectionCard(title = "프로 기능 모아보기", trailing = { ProBadge() }) {
        Text("프로 기능은 앱 곳곳에서 ‘프로’ 표시가 붙어 있어요. 여기서 한눈에 보고 누르면 그 기능으로 바로 가요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        mine.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider()
            ProFeatureRow(f, proStateWords(f, gate, settings), onOpen)
        }
        if (others.isNotEmpty()) {
            Text("다른 일에 쓰는 프로 기능", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            others.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                ProFeatureRow(f, proStateWords(f, gate, settings), onOpen)
            }
        }
    }
}

@Composable
private fun ProFeatureRow(f: AppFeature, state: String, onOpen: (AppFeature) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "${f.name} 열기") { onOpen(f) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge)
            Text(f.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(state, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}
