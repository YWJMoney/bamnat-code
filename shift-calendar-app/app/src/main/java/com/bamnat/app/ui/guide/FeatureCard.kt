package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.Tune
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.GuideStore
import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.Place

/** [GUIDE-06] The feature a card belongs to, for the ⋮ that [com.bamnat.app.ui.components.SectionCard] adds. */
data class CardFeature(val feature: AppFeature, val calendar: String)

val LocalCardFeature = compositionLocalOf<CardFeature?> { null }

/**
 * [GUIDE-05][GUIDE-06] A card of a feature that can be turned off: nothing at all while it is off for
 * [calendar], otherwise [content] with ⋮ › 이 기능 끄기 on its card. Seeing it counts as using it [GUIDE-04].
 */
@Composable
fun FeatureCard(id: String, calendar: String, name: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { GuideStore.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    val feature = FeatureRegistry.get(id)
    if (feature == null) {
        content()
        return
    }
    if (state.isOff(feature, calendar)) return
    // A card with a tip of its own keeps it for its moment (급여일, 15시간) until it is used there.
    if (feature.moment == null) LaunchedEffect(id) { store.used(id) }
    // PC ef116bf4 (알바 카드의 화면 읽기 이름이 '월급날 비교 더 보기'): a card named in the calendar's own words is read by that name.
    CompositionLocalProvider(LocalCardFeature provides CardFeature(name?.let { feature.copy(name = it) } ?: feature, calendar)) { content() }
}

/**
 * [GUIDE-01] A tool inside a card (두 알바 비교하기, 견적서, 직원 근무표) opened from the checklist, a tip or
 * the help search: [onOpen] once, when the card is on screen. Opening it counts as using [featureId].
 */
@Composable
fun FocusedTool(key: String, featureId: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val focus by FeatureLinks.focus.collectAsStateWithLifecycle()
    LaunchedEffect(focus) {
        if (focus == key && FeatureLinks.take(key)) {
            GuideStore.get(context).used(featureId)
            onOpen()
        }
    }
}

/** True while [id] is off for [calendar] with the app's own switch, for what is not a card (a banner, a line). */
@Composable
fun featureHidden(id: String, calendar: String): Boolean {
    val context = LocalContext.current
    val state by remember { GuideStore.get(context) }.state.collectAsStateWithLifecycle()
    return FeatureRegistry.get(id)?.let { state.isOff(it, calendar) } ?: false
}

/** [GUIDE-06] ⋮ on a card: 이 기능 끄기, and the screen with every switch. */
@Composable
fun FeatureMenuButton(card: CardFeature, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) { // 2.0.0 [RVP-10] 48dp (검토 UIX-10)
            Icon(Icons.Filled.MoreVert, "‘${card.feature.name}’ 더 보기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // 1.39 발굴 [GUIDE-06][GUIDE-21] G2 PC 5c4ad849 (가게 '직원 인건비' ⋮ › 이 기능 끄기가 기록을 지우는 것처럼 보임):
            // the item says the records stay and where it comes back, before it is tapped.
            DropdownMenuItem(
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text("이 기능 끄기")
                        Text("화면에서만 숨겨요. 적은 기록은 그대로 남아요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.ToggleOff, null) },
                onClick = {
                    open = false
                    turnOff(context.appContainer, card.feature, card.calendar)
                },
            )
            DropdownMenuItem(
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text("기능 켜고 끄기")
                        Text("끈 기능은 여기서 다시 켜요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                onClick = { open = false; FeatureLinks.openSwitches() },
            )
        }
    }
}

/** [GUIDE-06] Off, then said that the records stay and where it comes back. */
fun turnOff(c: com.bamnat.app.AppContainer, feature: AppFeature, calendar: String) {
    if (FeatureSwitches.set(c, feature, calendar, false)) FeatureLinks.saidOff(feature.name, keepsRecords = true)
}

/**
 * [GUIDE-04] Screens that hold a feature count as using it when they open: the guide's checklist ticks and
 * the tip stops. Only on this phone.
 */
object GuideUse {
    private val SCREENS = mapOf(
        "pay" to Place.PAY, "pattern" to Place.PATTERN, "shift_types" to Place.SHIFT_TYPES, "extra_kinds" to Place.EXTRA_KINDS,
        "calendars" to Place.CALENDARS, "calendar_edit" to Place.CALENDAR_EDIT, "shop_days" to Place.SHOP_DAYS,
        "combined" to Place.COMBINED, "owed" to Place.OWED, "sleep" to Place.SLEEP, "duty_roster" to Place.DUTY_ROSTER,
        "health_report" to Place.HEALTH_REPORT, "teams" to Place.TEAMS, "year" to Place.YEAR, "attendance_import" to Place.ATTENDANCE_IMPORT, "commute" to Place.COMMUTE,
        "calendar_import" to Place.CALENDAR_IMPORT, "diagnostics" to Place.DIAGNOSTICS,
    )

    /** A tool in a card or a tab ([com.bamnat.core.guide.FeatureRoute.focus]) was opened from the guide. */
    fun focus(store: GuideStore, key: String) {
        FeatureRegistry.all.filter { it.route.focus == key }.forEach { store.used(it.id) }
    }

    /** [screen]: a route without its arguments ("pay", "shop_days"). */
    fun screen(store: GuideStore, screen: String) {
        val place = SCREENS[screen] ?: return
        FeatureRegistry.all.filter { it.route.place == place && it.route.focus == null }.forEach { store.used(it.id) }
    }
}
