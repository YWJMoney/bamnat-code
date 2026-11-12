package com.bamnat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.BadgedBox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** [NAV2-02] The 나 button's name, which TalkBack reads and the tests find. */
internal const val ME_BUTTON = "나 · 설정과 다른 화면"
/** [NAV2-03] What a 묶음's list and the 나 menu call the way to the bar's editor. */
internal const val EDIT_BAR = "아래 메뉴 고치기"
internal const val EDIT_GROUP = "이 묶음 고치기"

/**
 * [NAV2-02] 1.39 What the 나 button at the top of every tab offers: 설정 and every tab no place of the bar holds (so none is
 * ever out of reach), all the features, and the bar's editor. [show] is false when the bar holds everything.
 */
@Immutable
internal data class MeState(
    val show: Boolean = false,
    /** New answers to the user's questions (설정's badge, moved here with it). */
    val unread: Int = 0,
    /** The screens to list, by key, with their names and icons. */
    val missing: List<TabPick> = emptyList(),
    val open: (String) -> Unit = {},
    val editBar: () -> Unit = {},
    /** Beside the rail (an unfolded phone, a tablet) the button sits at the top of the rail, not in each screen's top row. */
    val inRail: Boolean = false,
)

internal val LocalMe = compositionLocalOf { MeState() }

/** [NAV2-02] The round 나 at the end of a tab's top row; nothing when the bar already holds every tab (and 잠, 2.0.0 [RVIB-16]). */
@Composable
internal fun MeButton(rail: Boolean = false) {
    val me = LocalMe.current
    if (!me.show || me.inRail != rail) return
    var open by remember { mutableStateOf(false) }
    val marks = me.unread
    Box {
        IconButton(onClick = { open = true }) {
            if (marks > 0) BadgedBox(badge = {
                IconCountBadge(marks, "문의에 새 답 ${me.unread}개", cap = 9)
            }) {
                Icon(Icons.Outlined.AccountCircle, ME_BUTTON)
            } else Icon(Icons.Outlined.AccountCircle, ME_BUTTON)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            me.missing.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label + if (p.key == com.bamnat.core.layout.TabOrder.SETTINGS && me.unread > 0) " · 새 답 ${me.unread}" else "") },
                    leadingIcon = { Icon(p.icon, null) },
                    onClick = { open = false; me.open(p.key) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                // 1.39 PC NAV2 2차: it opens the list where features are turned on and off, so it says so.
                text = { Text("기능 목록·켜고 끄기") },
                leadingIcon = { Icon(TabPicks.groupIcon, null) },
                onClick = { open = false; me.open(TabSlotsUi.ALL_FEATURES) },
            )
            DropdownMenuItem(
                text = { Text(EDIT_BAR) },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { open = false; me.editBar() },
            )
        }
    }
}

/** [NAV2-02] The screen on show by its route, for checks of where a screen that is no place of the bar (설정 from 나) went. */
internal object ShownRoute {
    @Volatile var route: String? = null
}

internal object TabSlotsUi {
    /** The feature list (설정 › 기능 켜고 끄기), what 기능 목록·켜고 끄기 opens. */
    val ALL_FEATURES = com.bamnat.core.layout.TabSlots.FEATURE + com.bamnat.core.guide.Place.FEATURES.name
}

/** [NAV2-01] A place's icon, with 설정's count of new answers or 마을's dot when the place holds them. */
@Composable
internal fun SlotIcon(icon: ImageVector, badge: Int, dot: Boolean, dotSaid: String = "광장 내 글에 새 답", group: Int = 0) {
    when {
        // 1.39 PC NAV2 (묶음인지 모르겠어요): a 묶음 shows how many screens it holds, quietly, when it has no news.
        group > 1 && badge <= 0 && !dot -> BadgedBox(badge = {
            androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text("$group")
            }
        }) { Icon(icon, contentDescription = null) }
        badge > 0 -> BadgedBox(badge = { IconCountBadge(badge, "문의에 새 답 ${badge}개", cap = 9) }) { Icon(icon, contentDescription = null) }
        dot -> BadgedBox(badge = { androidx.compose.material3.Badge(Modifier.semantics { contentDescription = dotSaid }) }) { Icon(icon, contentDescription = null) }
        else -> Icon(icon, contentDescription = null)
    }
}

/**
 * [NAV2-01] The list a 묶음 opens under its place: each screen in it (the one on show checked), then the way to change the
 * 묶음. One tap goes there.
 */
@Composable
internal fun GroupMenu(expanded: Boolean, members: List<TabPick>, current: String?, onPick: (String) -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit,
    beside: Boolean = false) {
    // 1.39 3차 PC (펼친 화면 1812x2176: 옆 막대 묶음 칸을 누르면 목록이 막대 위를 덮어요): on the side rail the list opens beside the rail, level
    // with the icon, instead of under it. The menu is anchored on the 24 dp icon, which sits [RAIL_ICON_START] in from the rail's edge.
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        offset = if (beside) androidx.compose.ui.unit.DpOffset(RAIL_WIDTH - RAIL_ICON_START + 8.dp, (-24).dp) else androidx.compose.ui.unit.DpOffset.Zero) {
        members.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.label) },
                leadingIcon = { Icon(if (p.key == current) p.selectedIcon else p.icon, null) },
                trailingIcon = { if (p.key == current) Icon(Icons.Outlined.Check, "지금 화면", tint = MaterialTheme.colorScheme.primary) },
                onClick = { onDismiss(); onPick(p.key) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(EDIT_GROUP) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { onDismiss(); onEdit() })
    }
}

/** The Material side rail's width and where its item's icon starts in it ((80 - 24) / 2). */
private val RAIL_WIDTH = 80.dp
private val RAIL_ICON_START = 28.dp

/** [NAV2-03] Asks for the bar's editor from anywhere (설정 › 화면과 글자's row); the app's root opens it. */
internal object BarEditLinks {
    private val _asks = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val asks: kotlinx.coroutines.flow.SharedFlow<Unit> = _asks
    fun ask() { _asks.tryEmit(Unit) }
}

/**
 * [NAV2-01] Settings with the bar [made]: a bar of tabs only is kept as the tab order (the places cleared), anything else as
 * the places, saved only when it is not the tab order's own bar.
 */
internal fun com.bamnat.app.data.AppSettings.withBar(made: List<com.bamnat.core.layout.TabSlots.Slot>): com.bamnat.app.data.AppSettings {
    val order = com.bamnat.core.layout.TabSlots.asOrder(made)
    return if (order != null) copy(tabSlots = "", tabOrder = com.bamnat.core.layout.TabOrder.toSave(order))
    else copy(tabSlots = com.bamnat.core.layout.TabSlots.toSave(made, tabOrder))
}
