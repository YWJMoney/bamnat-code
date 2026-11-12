package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.data.CommutePrefs
import com.bamnat.app.data.GuideStore
import com.bamnat.app.ui.settings.SearchText
import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Off
import com.bamnat.core.guide.Place
import com.bamnat.core.work.WorkStyle

/**
 * [GUIDE-07] The feature list searched by name, other words and what it does, the same way settings are
 * ("주휴" finds 주휴수당 주별 확인, "비교" finds 두 알바 비교하기). Features of the calendar's own kind of
 * work come first when they fit as well.
 */
internal object FeatureSearch {
    fun search(query: String, style: WorkStyle? = null, limit: Int = 4, features: List<AppFeature> = FeatureRegistry.shown,
               hidden: Set<WorkStyle> = emptySet()): List<AppFeature> {
        // [J133-06] A feature only for kinds of work hidden here is not offered.
        val shown = features.filter { f -> f.styles.isEmpty() || !hidden.containsAll(f.styles) }
        val raw = shown.associateWith { f -> SearchText.score(query, f.name, f.keywords, f.what + " " + f.where) }
        // 1.39 8차 PC (설정에서 '잠'으로 찾으면 수면 기록이 안 나와요): its parts (잠드는 소리, 잠 자동으로 잡기…) start with the word and
        // took the three places; a feature whose parts match leads them, so the screen they are on is found first.
        val parents = raw.filterValues { it > 0 }.keys.mapNotNull { it.under }.toSet()
        return SearchText.best(shown, limit) { f ->
            val s = raw.getValue(f).let { if (it > 0 && f.id in parents) it + PARENT_LEAD else it }
            when {
                s <= 0 || style == null -> s
                // Another kind of work's feature still shows, after this calendar's own.
                !f.fits(style) -> (s / 2).coerceAtLeast(1)
                f.styles.isNotEmpty() -> s + 1
                else -> s
            }
        }
    }

    /** What a feature whose parts also match is lifted by: above a part that starts with the word (40 against 25 + this). */
    private const val PARENT_LEAD = 20

    /** [GUIDE-07] What a 의견 starts with when the search found nothing: the words and the kind of work. */
    fun ideaText(query: String, style: WorkStyle?): String =
        "찾던 기능: ${query.trim()}\n일하는 방식: ${(style ?: WorkStyle.SHIFT).label}\n\n"
}

/** The calendar on screen and its kind of work. */
internal fun AppContainer.guideCalendar(): Pair<String, WorkStyle?> {
    val id = calendars.active.value
    return id to calendars.styleOf(id, settingsRepository.current.workStyle)
}

/**
 * [GUIDE-07] A feature the search found: what it does, where it is, whether it is off for this calendar
 * (with 켜기), and 바로 가기. A feature of another kind of work says which calendar it is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureHelpRow(feature: AppFeature, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    // Read again when a switch changes, here or elsewhere.
    val guide by remember { GuideStore.get(context) }.state.collectAsStateWithLifecycle()
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val calendars by c.calendars.calendars.collectAsStateWithLifecycle()
    val active by c.calendars.active.collectAsStateWithLifecycle()
    val commute by CommutePrefs.get(context).state.collectAsStateWithLifecycle()
    // A switch turned on in the phone's settings (결제 알림 읽기): read again on coming back.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val style = c.calendars.styleOf(active, settings.workStyle)
    val fits = feature.fits(style)
    val on = remember(guide, settings, calendars, active, commute, resumed, feature.id) { FeatureSwitches.isOn(c, feature, active) }
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Explore, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(feature.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                // [TIER-06] A pro feature says so in the search too.
                if (com.bamnat.core.guide.ProCatalog.isPro(feature.id)) {
                    val gate by c.featureGate.state.collectAsStateWithLifecycle()
                    com.bamnat.app.ui.components.ProBadge(locked = com.bamnat.app.ui.pro.proFeatureOf(feature.id)?.let(gate::locked) == true)
                }
            }
            Text(feature.what, style = MaterialTheme.typography.bodyMedium)
            // 2.0.0 [RVH-25] 검토 WORK-12·REST-17: the way in this calendar's own tab names.
            Text("어디: ${FeatureRegistry.whereFor(feature, style)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            val note = when {
                !fits -> "${feature.styles.joinToString("·") { it.label }} 달력에서 쓰는 기능이에요. 달력 이름을 눌러 그 달력으로 바꾸거나 새로 만들어요."
                feature.canTurnOff && !on && feature.off == Off.ELSEWHERE -> "지금 꺼져 있어요. 켜면 바로 써요."
                feature.canTurnOff && !on -> "이 달력에서 꺼 두었어요. 켜면 다시 보여요."
                else -> null
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (fits) scheme.tertiary else scheme.onSurfaceVariant) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fits && feature.canTurnOff && !on) {
                    FilledTonalButton(onClick = { FeatureSwitches.set(c, feature, active, true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("켜기") }
                }
                if (fits) {
                    OutlinedButton(onClick = { FeatureLinks.open(feature) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("바로 가기") }
                } else {
                    OutlinedButton(onClick = { FeatureLinks.open(FeatureRoute(Place.CALENDARS)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("달력 목록") }
                }
            }
        }
    }
}
