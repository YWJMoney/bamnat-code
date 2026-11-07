package com.bamnat.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.bamnat.core.guide.Place
import com.bamnat.core.layout.TabSlots
import com.bamnat.core.work.WorkStyle

/**
 * [NAV2-01] 1.39 A screen that can fill a place of the bottom bar: the six tabs, and the screens people go to often that used to wait
 * inside a tab or settings (잠, 연차, 받을 돈…). [kind] groups them in the picker; [fits] says whether the calendar's kind of work uses it
 * (shown first; the rest wait under '다른 일의 화면', never hidden, as the feature list does).
 */
internal data class TabPick(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val kind: String,
    val fits: Boolean = true,
)

internal object TabPicks {
    const val KIND_TABS = "기본 화면"
    const val KIND_WORK = "근무와 돈"
    const val KIND_LIFE = "잠과 하루"
    const val KIND_TOGETHER = "함께"
    const val KIND_APP = "앱"

    private fun f(p: Place) = TabSlots.FEATURE + p.name
    const val STUDIO = TabSlots.EXTRA + "studio"
    const val MANAGE = TabSlots.EXTRA + "manage"

    /** The feature screens that can be a place, in the picker's order, with the kinds of work they are for (null: all). */
    private data class Feature(val place: Place, val label: String, val icon: ImageVector, val kind: String, val styles: Set<WorkStyle>? = null)

    private val PAID = setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY)
    private val FEATURES = listOf(
        Feature(Place.PAY, "급여", Icons.Outlined.Payments, KIND_WORK, PAID),
        Feature(Place.LEAVE, "연차", Icons.Outlined.BeachAccess, KIND_WORK, setOf(WorkStyle.SHIFT, WorkStyle.DAY)),
        Feature(Place.OWED, "받을 돈", Icons.AutoMirrored.Outlined.ReceiptLong, KIND_WORK),
        Feature(Place.YEAR, "한 해 보기", Icons.Outlined.CalendarViewMonth, KIND_WORK),
        Feature(Place.COMBINED, "모든 달력", Icons.Outlined.Layers, KIND_WORK),
        Feature(Place.PATTERN, "근무 주기", Icons.Outlined.EventRepeat, KIND_WORK, setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY, WorkStyle.BUSINESS)),
        Feature(Place.COMMUTE, "출퇴근", Icons.AutoMirrored.Outlined.DirectionsWalk, KIND_WORK, PAID),
        Feature(Place.SLEEP, "잠", Icons.Outlined.Bedtime, KIND_LIFE),
        Feature(Place.HEALTH_REPORT, "건강 리포트", Icons.Outlined.Favorite, KIND_LIFE, setOf(WorkStyle.SHIFT)),
        Feature(Place.JOURNAL, "밤낮이 한 일", Icons.Outlined.History, KIND_LIFE),
        Feature(Place.TEAMS, "같이 보는 근무", Icons.Outlined.Groups, KIND_TOGETHER, setOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY)),
        Feature(Place.RECIPES, "장터·나눔 판", Icons.AutoMirrored.Outlined.LibraryBooks, KIND_TOGETHER),
        Feature(Place.DIAGNOSTICS, "알람 점검", Icons.Outlined.AlarmOn, KIND_APP),
        Feature(Place.FEATURES, "기능 켜고 끄기", Icons.Outlined.ToggleOn, KIND_APP),
        Feature(Place.PRO, "프로", Icons.Outlined.WorkspacePremium, KIND_APP),
        Feature(Place.PROFILE, "내 정보", Icons.Outlined.AccountCircle, KIND_APP),
        Feature(Place.INQUIRY, "문의하기", Icons.AutoMirrored.Outlined.HelpOutline, KIND_APP),
    )

    /** Every key a place may hold in this version (what [TabSlots.decode] keeps). */
    val known: (String) -> Boolean = { k ->
        TopLevel.entries.any { it.route == k } || k == STUDIO || k == MANAGE ||
            (k.startsWith(TabSlots.FEATURE) && FEATURES.any { f(it.place) == k })
    }

    /** What the picker offers on the calendar of [style]; 관리 화면 only while 팀 리더 or 사장님 is on. */
    fun all(style: WorkStyle?, manageOn: Boolean): List<TabPick> =
        TopLevel.entries.map { top(it, style) } +
            FEATURES.map { feature(it, style) } +
            TabPick(STUDIO, "스튜디오", Icons.Outlined.Palette, kind = KIND_TOGETHER) +
            listOfNotNull(TabPick(MANAGE, "팀·가게 관리", Icons.Outlined.SupervisorAccount, kind = KIND_TOGETHER).takeIf { manageOn })

    /** The screen of [key], or null when this version does not know it. */
    fun of(key: String, style: WorkStyle?): TabPick? = when {
        key == STUDIO -> TabPick(STUDIO, "스튜디오", Icons.Outlined.Palette, kind = KIND_TOGETHER)
        key == MANAGE -> TabPick(MANAGE, "팀·가게 관리", Icons.Outlined.SupervisorAccount, kind = KIND_TOGETHER)
        key.startsWith(TabSlots.FEATURE) -> FEATURES.firstOrNull { f(it.place) == key }?.let { feature(it, style) }
        else -> TopLevel.entries.firstOrNull { it.route == key }?.let { top(it, style) }
    }

    fun topOf(key: String): TopLevel? = TopLevel.entries.firstOrNull { it.route == key }

    fun placeOf(key: String): Place? = if (!key.startsWith(TabSlots.FEATURE)) null
        else runCatching { Place.valueOf(key.removePrefix(TabSlots.FEATURE)) }.getOrNull()

    private fun top(t: TopLevel, style: WorkStyle?) = TabPick(t.route, t.labelFor(style), t.icon, t.selectedIcon, KIND_TABS)

    private fun feature(x: Feature, style: WorkStyle?) =
        TabPick(f(x.place), x.label, x.icon, kind = x.kind, fits = style == null || x.styles == null || style in x.styles)

    /** The icon a 묶음 shows when none of its screens is on screen. */
    val groupIcon: ImageVector = Icons.Outlined.AutoAwesome
    val groupIconOther: ImageVector = Icons.Outlined.DateRange
    val timeline: ImageVector = Icons.Outlined.ViewTimeline
}
