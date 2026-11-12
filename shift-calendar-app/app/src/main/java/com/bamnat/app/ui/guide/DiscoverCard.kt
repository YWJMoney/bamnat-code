package com.bamnat.app.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.DiscoverStore
import com.bamnat.app.data.GuideStore
import com.bamnat.app.ui.components.DropdownMenu
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.core.formula.NoticeOverlap
import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.Discover
import com.bamnat.core.guide.DiscoverAnswer
import com.bamnat.core.guide.DiscoverFacts
import com.bamnat.core.guide.DiscoverNeed
import com.bamnat.core.guide.DiscoverPicker
import com.bamnat.core.guide.DiscoverState
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GuideChecklist
import com.bamnat.core.guide.Off
import com.bamnat.core.guide.Place
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * 1.40 [DISC-01] 안 써 본 기능 가끔 알려 주기 (사용자 2026-11-11: 위트 있고, 디자인이 뛰어나고, 너무 불편하지 않고, 좋은 빈도로): the last card
 * above the month, one feature at a time, at most one in 7 days and rarer when it is not answered (core [DiscoverPicker]). It never
 * comes on a day another card above the month has something to say ([othersUp]), and never as a phone notice or a widget line.
 *
 * It waits [SETTLE_MS] before it shows, so the other cards (some read their stores first) are measured and it can step aside.
 * After an answer it folds to one line with 되돌리기 for [UNDO_MS].
 */
@Composable
internal fun DiscoverNotice(
    calendar: String,
    style: WorkStyle?,
    /** Another card above the month is up now (read again just before it shows). */
    othersUp: () -> Boolean,
    /** The 시작 단계 or the first-step card is up. */
    startUp: Boolean,
    ready: (DiscoverNeed) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = context.appContainer
    val store = remember { DiscoverStore.get(context) }
    val guideStore = remember { GuideStore.get(context) }
    val s by store.state.collectAsStateWithLifecycle()
    val guide by guideStore.state.collectAsStateWithLifecycle()
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val today = Today.date()
    val folded = com.bamnat.app.ui.calendar.LocalNoticeFolded.current
    LaunchedEffect(today) { store.settle(today) }

    // Facts that take a file read: the last update of the app and the last stop.
    var updatedOn by remember { mutableStateOf<LocalDate?>(null) }
    var crashedOn by remember { mutableStateOf<LocalDate?>(null) }
    var read by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            updatedOn = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (info.lastUpdateTime > info.firstInstallTime) Instant.ofEpochMilli(info.lastUpdateTime).atZone(Today.zone()).toLocalDate() else null
            }.getOrNull()
            crashedOn = runCatching {
                com.bamnat.app.diag.CrashLog.notes(context).firstOrNull()?.name?.removePrefix("crash-")?.removeSuffix(".txt")?.toLongOrNull()
                    ?.let { Instant.ofEpochMilli(it).atZone(Today.zone()).toLocalDate() }
            }.getOrNull()
        }
        read = true
    }
    if (!read) return

    val checklistUp = remember(guide, calendar, style, today) {
        val listOff = FeatureRegistry.get("guide.checklist")?.let { guide.isOff(it, calendar) } ?: false
        !listOff && GuideChecklist.visible(guide.checklists[calendar], style, today, guide::isUsed) { guide.isOff(it, calendar) } != null
    }
    val logicStore = remember { com.bamnat.app.logic.LogicStore(c.appContext) }
    // [DISC-05] Already in use: its own switch is on, or the notice it would turn on is said already (the app's or a rule of mine).
    val alreadyOn: (AppFeature) -> Boolean = { f ->
        (f.off == Off.ELSEWHERE && f.id in ON_MEANS_USED && FeatureSwitches.isOn(c, f, calendar)) ||
            (f.id == "data.backup" && c.autoBackup.state.value.on) ||
            DiscoverCatalogNotice.of(f.id)?.let { b ->
                NoticeOverlap.repeating(com.bamnat.core.formula.Logic.decode(settings.logic), b) { !logicStore.isOff(it.key) }.isNotEmpty()
            } == true
    }
    val othersNow by rememberUpdatedState(othersUp)
    fun facts(others: Boolean) = DiscoverFacts(
        now = Today.dateTime(), installedOn = c.installedAt?.atZone(Today.zone())?.toLocalDate(), setupDone = settings.setupDone,
        guideUp = startUp || checklistUp, othersUp = others, quietToday = c.firstSetup.quietToday(),
        updatedOn = updatedOn, crashedOn = crashedOn, tipToday = guide.tipDay == today && guide.tipId != null,
    )
    val pick = remember(s, guide, calendar, style, today, startUp, checklistUp, settings.setupDone) {
        DiscoverPicker.pick(s, guide, calendar, style, facts(others = false), ready, alreadyOn)
    }
    // Shown only once the other cards had their moment to say something, and only when it is seen (not folded away).
    var settled by remember(pick?.feature?.id) { mutableStateOf(pick?.isNew == false) }
    LaunchedEffect(pick?.feature?.id, folded) {
        val p = pick ?: return@LaunchedEffect
        if (!p.isNew) { settled = true; return@LaunchedEffect }
        delay(SETTLE_MS)
        if (folded || othersNow()) return@LaunchedEffect
        store.shown(p.feature.id, today)
        c.telemetry.event("disc_shown")
        settled = true
    }
    var after by remember { mutableStateOf<After?>(null) }
    LaunchedEffect(after) { if (after != null) { delay(UNDO_MS); after = null } }

    fun answer(a: DiscoverAnswer, feature: AppFeature, say: String?) {
        val before = store.current
        val wasUsed = feature.id in guideStore.current.used
        val wasNever = feature.id in guideStore.current.never
        when (a) {
            DiscoverAnswer.USED -> guideStore.used(feature.id)
            DiscoverAnswer.NEVER -> guideStore.never(feature.id)
            else -> Unit
        }
        store.answer(a, today)
        if (a == DiscoverAnswer.LATER) c.telemetry.event("disc_later")
        if (say != null) after = After(say) {
            store.undo(before)
            guideStore.unmark(feature.id, used = !wasUsed && a == DiscoverAnswer.USED, never = !wasNever && a == DiscoverAnswer.NEVER)
        }
    }

    val motion = !com.bamnat.app.ui.theme.systemAnimationsOff(context)
    Column(modifier.fillMaxWidth()) {
        after?.let { a -> AfterLine(a) { a.undo(); after = null } }
        val p = pick
        AnimatedVisibility(
            visible = p != null && settled && after == null,
            enter = if (motion) fadeIn() + expandVertically() else EnterTransition.None,
            exit = if (motion) fadeOut() + shrinkVertically() else ExitTransition.None,
        ) {
            if (p == null) return@AnimatedVisibility
            DiscoverCardBody(
                feature = p.feature, line = p.line,
                onTry = {
                    store.answer(DiscoverAnswer.OPENED, today)
                    guideStore.used(p.feature.id)
                    c.telemetry.event("disc_try")
                    FeatureLinks.open(p.feature)
                },
                onLater = { answer(DiscoverAnswer.LATER, p.feature, "좋아요, 나중에 한 번 더 알려 드릴게요") },
                onUsed = { answer(DiscoverAnswer.USED, p.feature, "벌써 쓰고 계셨군요. 다음엔 다른 걸 알려 드릴게요") },
                onNever = { answer(DiscoverAnswer.NEVER, p.feature, "이 기능은 다시 알려 드리지 않을게요") },
                onFewer = { answer(DiscoverAnswer.FEWER, p.feature, "알겠어요, 이런 안내는 더 뜸하게 할게요") },
                onOff = {
                    FeatureRegistry.get(DiscoverPicker.SWITCH)?.let { sw ->
                        val before = store.current
                        store.answer(DiscoverAnswer.IGNORED, today)
                        guideStore.setOn(sw, calendar, false)
                        c.telemetry.event("disc_off")
                        FeatureLinks.saidOff(sw.name)
                        after = After("이런 안내를 껐어요. 다시 켜는 곳: 설정 › 기능 켜고 끄기") {
                            guideStore.setOn(sw, calendar, true); store.undo(before)
                        }
                    }
                },
                onBrowse = { InquiryLinks.ask(InquiryStart(about = "기능 찾기", help = true)) },
            )
        }
    }
}

/** [DISC-05] The app's own notice a card's feature turns on, if any. */
private object DiscoverCatalogNotice {
    fun of(id: String): NoticeOverlap.BuiltIn? = com.bamnat.core.guide.DiscoverCatalog.of(id)?.notice
}

/** Features of [Off.ELSEWHERE] whose switch on means they are in use already. */
private val ON_MEANS_USED = setOf("lock.today", "day.commute", "today.flow", "money.auto")

private const val SETTLE_MS = 1_200L
private const val UNDO_MS = 5_000L

private class After(val say: String, val undo: () -> Unit)

@Composable
private fun AfterLine(a: After, onUndo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = scheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(a.say, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text("되돌리기") }
    }
}

/**
 * The card: a small picture of where the feature lives, '이런 기능도 있어요', the title with the wit, one line of what it does, and two
 * buttons. Colours come from the theme only, so a 테마 or the night colours dress it like the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverCardBody(
    feature: AppFeature,
    line: Discover,
    onTry: () -> Unit,
    onLater: () -> Unit,
    onUsed: () -> Unit,
    onNever: () -> Unit,
    onFewer: () -> Unit,
    onOff: () -> Unit,
    onBrowse: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(placeIcon(feature.route.place), null, Modifier.size(22.dp), tint = scheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 1.dp)) {
                    Text("이런 기능도 있어요", style = MaterialTheme.typography.labelMedium, color = scheme.tertiary)
                    Text(line.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() })
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "이 안내 더 보기", tint = scheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuLine("이미 써요", "다음엔 다른 걸 알려 드려요") { menu = false; onUsed() }
                        MenuLine("이 기능은 안 볼래요", null) { menu = false; onNever() }
                        MenuLine("이런 안내 줄이기", "다음 안내까지 두 배로 기다려요") { menu = false; onFewer() }
                        MenuLine("이런 안내 그만 보기", "설정 › 기능 켜고 끄기에서 다시 켜요") { menu = false; onOff() }
                        MenuLine("기능 전부 둘러보기", null) { menu = false; onBrowse() }
                    }
                }
            }
            Text(line.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, end = 12.dp, top = 4.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(end = 8.dp, top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onLater) { Text("나중에") }
                FilledTonalButton(onClick = onTry) { Text(line.action) }
            }
        }
    }
}

@Composable
private fun MenuLine(title: String, sub: String?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onClick,
    )
}

/** A small picture of where a feature lives (no new image files: the app size budget is tight). */
private fun placeIcon(place: Place): ImageVector = when (place) {
    Place.DIAGNOSTICS, Place.SETTINGS_ALARM -> Icons.Outlined.Alarm
    Place.SLEEP -> Icons.Outlined.Bedtime
    Place.PAY, Place.MONEY, Place.SETTINGS_PAY -> Icons.Outlined.Payments
    Place.STATS -> Icons.Outlined.Insights
    Place.SHARE -> Icons.Outlined.Share
    Place.SETTINGS_DATA -> Icons.Outlined.Backup
    Place.MEMOS -> Icons.Outlined.EditNote
    Place.TODAY -> Icons.Outlined.Today
    Place.LEAVE -> Icons.Outlined.BeachAccess
    Place.YEAR, Place.CALENDAR -> Icons.Outlined.CalendarMonth
    Place.OWED -> Icons.Outlined.Handshake
    Place.SHOP_DAYS -> Icons.Outlined.Storefront
    Place.ATTENDANCE_IMPORT -> Icons.Outlined.TableChart
    Place.COMMUTE -> Icons.Outlined.Commute
    Place.HEALTH_REPORT -> Icons.Outlined.MonitorHeart
    Place.SETTINGS_NOTICES -> Icons.Outlined.NotificationsActive
    else -> Icons.Outlined.Lightbulb
}

@Suppress("unused") private fun keep(s: DiscoverState) = s
