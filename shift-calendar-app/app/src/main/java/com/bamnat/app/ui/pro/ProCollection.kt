package com.bamnat.app.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.bamnat.core.tier.ProPitch
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
 * 2.0.0 [RVO-05] (검토 U11b-02, PRO-30) 이 유형에 먼저 보일 프로 기능: [ProPitch.first]의 다섯, 그다음 이 유형에 맞는 나머지, 끝에 다른 일에
 * 쓰는 것. 모두 [ProCatalog]에서 오니 프로 기능이 빠질 수 없어요(PRO-12).
 */
internal fun proOrder(style: WorkStyle?): Pair<List<AppFeature>, List<AppFeature>> {
    val (mine, others) = ProCatalog.of(style).partition { it.fits(style) }
    val first = ProPitch.first(style)
    return mine.sortedBy { f -> first.indexOf(f.id).let { if (it < 0) first.size else it } } to others
}

/** 2.0.0 [RVO-05] 접었을 때 보이는 기능 수. */
internal const val PRO_FIRST = 5

/**
 * [TIER-07] 1.25 묶음 Y (U18): 설정 › 프로 › 프로 기능 모아보기. Every pro feature in one card, the ones that
 * suit the calendar on screen first, each with its 프로 badge, how it stands and a tap that opens it where it
 * lives ([FeatureLinks]); the rest under '다른 일에 쓰는 프로 기능'.
 *
 * 2.0.0 [RVO-05] (검토 U11b-02 '같은 내용을 세 번': 기능 모아보기·프로가 더해 주는 것·무료와 프로) 세 카드를 이 한 장으로. 이 유형의 다섯 줄만
 * 보이고 나머지는 '모두 보기'([startFolded]면 처음부터 모두 접혀요: 위 '프로 시작하기'가 다섯 줄을 이미 말했을 때). 아래에는 이 유형이 늘 무료로
 * 쓰는 것([ProPitch.freeAlways]), "프로 기능은 여기 모은 것이 전부" 한 줄, 광고 약속과 믿음 한 줄.
 */
@Composable
fun ProCollectionCard(gate: Gate, settings: AppSettings, startFolded: Boolean = false, onOpen: (AppFeature) -> Unit = { FeatureLinks.open(it) }) {
    val style: WorkStyle? = settings.workStyle
    val (mine, others) = proOrder(style)
    val total = mine.size + others.size
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) mine else if (startFolded) emptyList() else mine.take(PRO_FIRST)
    SectionCard(title = "프로 기능 모아보기", trailing = { ProBadge() }) {
        Text("누르면 그 기능으로 바로 가요. 앱 곳곳에도 ‘프로’ 표시가 붙어 있어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        shown.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider()
            ProFeatureRow(f, proStateWords(f, gate, settings), onOpen)
        }
        if (all && others.isNotEmpty()) {
            Text("다른 일에 쓰는 프로 기능", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            others.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                ProFeatureRow(f, proStateWords(f, gate, settings), onOpen)
            }
        }
        val more = total - shown.size
        TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (all) "접기 ▴" else if (shown.isEmpty()) "프로 기능 ${total}개 모두 보기 ▾" else "모두 보기 · ${more}개 더 ▾")
        }
        Text("늘 무료", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        ProPitch.freeAlways(style).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(proAllSaid(total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
        // G 1.32 [WTYP-01] The ad promise, in words. 1.38 [EASE-33] the other promises, in the body size (PC e6dc244a).
        Text(com.bamnat.core.tier.AdRules.PROMISE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Text(TRUST_LINE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 2.0.0 [RVO-05] (검토 PRO-12, 결정 13 추천) 옛 "여기 없는 기능은 모두 무료예요"가 빠진 프로 기능 넷 때문에 틀렸던 것: 목록이 전부라는 말. */
internal fun proAllSaid(total: Int): String = "프로 기능은 여기 모은 ${total}개와 광고 없음이 전부예요. 나머지는 모두 무료예요."

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
