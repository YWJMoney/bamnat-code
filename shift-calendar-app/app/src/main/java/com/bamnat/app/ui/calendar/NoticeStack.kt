package com.bamnat.app.ui.calendar

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BadgedBox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay

/**
 * [CAL-50] 상-4: what the calendar has to say above the month (a question, an alarm that will not ring, records to check, 공휴일 근무,
 * the month's recap, a tip, what the app did today …) never stacks over the month, so it is on the first screen even on a 320×568 phone
 * or a phone on its side.
 *
 * 2.0.0 [RVP-45][RVP-01][RVP-43] 검토 UIX-01·U13-02·I13b-01·PC-55·HQ-49 (달력이 화면 절반 아래에서 시작하고, 달력 위 띠들의 모양이
 * 저마다 달라요. 사용자 11-12 20:38·20:40 KST "알림이 있다면 … 표식으로 알려 주고 전체 지움 등을 넣어서 비울 수 있게", "달력 위에 뜨는
 * 것은 꼭 봐야 하는 것들로만"): above the month there is one band at most, and only for what has to be seen now ([Notice.urgent]: an
 * alarm that may not ring, taking back what was just put in, an agreement the app waits for, the empty calendar's first step and the
 * moment the first setup filled it), in the one shape of a nudge ([NudgeCard]: one line, the meaning in the icon's colour, one button,
 * an X). Everything else waits in the 알림함: the bell ([NoticeBell]) beside 공유 on the calendar head's row, with its count; the bell
 * opens a sheet ([NoticeSheet]) with what people did first ([InboxSection.PEOPLE], rows other features put with [Inbox.post]), then what
 * the app says, then what it suggests, each with 닫기 and 다시 보지 않기, and 전체 지움 at the top.
 *
 * 2.0.0 [RVP-45] 검토 PC-81 (스튜디오에서 입힌 뒤 달력으로 오면 띠 두 개가 1~2초 늦게 나타나 달력이 327px 밀리고, 그때 누른 칸이 안
 * 열림): the band's place is decided while the calendar opens ([SETTLE_MS]). A notice with something to say after that goes to the bell,
 * so the month never moves under a finger; a band already up may give its place to the next one, in the same place.
 *
 * Every notice is composed, so each keeps deciding for itself whether it has something to say (most read their own stores); one that draws
 * nothing is 0 high and is not counted. They are measured, not placed, and their words leave the accessibility tree: the band and the
 * sheet's rows say the notice's [Notice.line]. A notice that records being seen (a tip, a question) reads [LocalNoticeFolded], so it
 * counts as shown only when opened whole in the sheet.
 */
@Composable
internal fun NoticeStack(
    notices: List<Notice>,
    modifier: Modifier = Modifier,
    /** How many bands may be open: 1, or 0 on a phone on its side where only the bell fits. */
    open: Int = 1,
    state: NoticeStackState = rememberNoticeStackState(),
) {
    val context = LocalContext.current
    val inbox = remember { Inbox.get(context) }
    val muted by inbox.muted.collectAsStateWithLifecycle()
    val suggestOff by inbox.suggestOff.collectAsStateWithLifecycle()
    val posted by inbox.rows.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        delay(SETTLE_MS)
        state.settled = true
    }
    // HQ-50: What the user said no to for good, and every 권유 while 권유 끄기 is on, is not even composed.
    val allowed = notices.filter { n -> n.urgent || (n.kind !in muted && !(n.section == InboxSection.SUGGEST && suggestOff)) }
    // Which notices said something the last time they were measured, in order; the band is the first urgent one the user did not put away.
    val present = allowed.filter { (state.heights[it.key] ?: 0) > 0 }
    val waiting = present.filter { it.key !in state.snoozed && it.key !in state.answered }
    val urgent = waiting.filter { it.urgent }
    val band: Notice? = when {
        open <= 0 -> null
        // PC-81: Once settled, a closed place stays closed: what comes later is in the bell.
        state.settled && !state.slotOpen -> null
        // 갈래 P 3/3 (L의 '앱 잠금 쉼'은 화면이 앞에 온 뒤에야 말해서 알람 줄에 띠를 내줬어요): until it settles the first in the order
        // has it; then the band up keeps its place, so what is under the finger does not change.
        !state.settled -> urgent.firstOrNull()
        else -> urgent.firstOrNull { it.key == state.bandKey } ?: urgent.firstOrNull()
    }
    // HQ-50: 권유 one at a time: the first of the calendar's, else the one another feature put.
    val suggestion = waiting.firstOrNull { it.section == InboxSection.SUGGEST }
    val rows = waiting.filter { it.section != InboxSection.SUGGEST || it === suggestion }
    val postedRows = posted.filter { it.kind !in muted && !(it.section == InboxSection.SUGGEST && (suggestOff || suggestion != null)) }
    SideEffect {
        // A notice answered in the sheet whose own copy here has not heard yet (it reads its store when the app comes back) waits until
        // that copy says nothing too.
        if (state.answered.isNotEmpty()) state.answered.filter { (state.heights[it] ?: 0) == 0 }.takeIf { it.isNotEmpty() }?.let { state.answered = state.answered - it.toSet() }
        state.bandKey = band?.key
        if (!state.settled || band == null) state.slotOpen = band != null
        // The bell's number is what its sheet holds (the band's notice too), so '3' on the bell is '알림 3' when it opens.
        state.inBell = rows.size + postedRows.size
        state.present = present.map { it.key }
    }
    androidx.compose.ui.layout.Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            allowed.forEach { n ->
                androidx.compose.runtime.key(n.key) {
                    Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                        CompositionLocalProvider(LocalNoticeFolded provides true) { n.content() }
                    }
                }
            }
            if (band != null) androidx.compose.runtime.key("band") { NoticeBand(band, state) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val parts = measurables.map { it.measure(loose) }
        parts.forEachIndexed { i, p ->
            if (i >= allowed.size) return@forEachIndexed
            val key = allowed[i].key
            if (state.heights[key] != p.height) state.heights[key] = p.height
        }
        // The band is placed only while its notice still draws something in this very pass (CAL-26: never a frame with all of them).
        val bandPart = parts.getOrNull(allowed.size)?.takeIf { band != null && (state.heights[band.key] ?: 0) > 0 }
        val width = constraints.maxWidth.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: parts.maxOfOrNull { it.width } ?: 0
        val height = bandPart?.height ?: 0
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            bandPart?.place(0, 0)
        }
    }
    if (state.sheetOpen) NoticeSheet(rows, postedRows, state, inbox)
}

/** The band: the notice's one line in the shape every nudge has; a tap on it opens the 알림함 on that notice. */
@Composable
private fun NoticeBand(n: Notice, state: NoticeStackState) {
    val context = LocalContext.current
    NudgeCard(
        text = n.line ?: n.label ?: "알림이 있어요",
        tone = n.tone,
        detail = n.detail,
        action = if (n.button) n.action ?: "보기" else null,
        onAction = n.onAction ?: { state.openSheet(n.key) },
        onClose = n.onClose ?: { state.snooze(context, n.key) },
        closeLabel = if (n.onClose == null) "알림함에 넣기" else "이 안내 닫기",
        onClick = { state.openSheet(n.key) },
        kani = n.kani,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * [RVP-43] I13b-01·HQ-49 알림함: what people did with the user's things first, then what the app says, what it suggests last; each row
 * one line with 닫기 and, under ⋮, 다시 보지 않기; a tap goes where it says, or opens the notice whole in place (a question, a card with
 * its own answers). 전체 지움 at the top, taken back once.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheet(notices: List<Notice>, posted: List<InboxRow>, state: NoticeStackState, inbox: Inbox) {
    val context = LocalContext.current
    val total = notices.size + posted.size
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = { state.sheetOpen = false }) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (total == 0) "알림" else "알림 $total", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            if (total > 0) TextButton(onClick = {
                val gone = inbox.clearAll()
                val shut = notices.map { it.key }
                shut.forEach { NoticeSnooze.put(context, it) }
                state.snoozed = state.snoozed + shut
                // 조건 10: taken back once; 조건 16: what stays, in a person's words.
                AppSnack.show("알림을 모두 지웠어요. 글·작품에 달린 댓글은 그대로예요", undo = {
                    inbox.putBack(gone)
                    shut.forEach { NoticeSnooze.remove(context, it) }
                    state.snoozed = state.snoozed - shut.toSet()
                })
            }) { Text("전체 지움") }
        }
        if (total == 0) {
            com.bamnat.app.ui.components.EmptyState("새 알림이 없어요", "누가 내 글·작품에 반응하거나 알릴 것이 생기면 여기에 모아 둘게요",
                mood = KaniMood.Basic, modifier = Modifier.padding(bottom = 24.dp))
            return@AppSheet
        }
        var openKey by rememberSaveable { mutableStateOf(state.sheetFocus) }
        val people = posted.filter { it.section == InboxSection.PEOPLE }
        val said = posted.filter { it.section == InboxSection.NOTICE }
        val ours = notices.filter { it.section != InboxSection.SUGGEST }
        val suggest = notices.filter { it.section == InboxSection.SUGGEST }
        val suggestPosted = posted.filter { it.section == InboxSection.SUGGEST }
        val focusAt = remember {
            // The notice the band was tapped on, in view.
            val before = people.size + (if (people.isEmpty()) 0 else 1) + 1
            ours.indexOfFirst { it.key == state.sheetFocus }.takeIf { it >= 0 }?.let { it + before + said.size } ?: 0
        }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = focusAt)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (people.isNotEmpty()) {
                item(key = "h:people") { SectionHead("사람들 반응") }
                items(people, key = { "p:" + it.key }) { r -> PostedRow(r, inbox, state) }
            }
            if (said.isNotEmpty() || ours.isNotEmpty()) {
                item(key = "h:notice") { SectionHead("안내") }
                items(said, key = { "p:" + it.key }) { r -> PostedRow(r, inbox, state) }
                items(ours, key = { "n:" + it.key }) { n ->
                    NoticeRow(n, opened = openKey == n.key, onOpen = { openKey = if (openKey == n.key) null else n.key }, state = state, inbox = inbox)
                }
            }
            if (suggest.isNotEmpty() || suggestPosted.isNotEmpty()) {
                item(key = "h:suggest") { SectionHead("권유") }
                items(suggestPosted, key = { "p:" + it.key }) { r -> PostedRow(r, inbox, state) }
                items(suggest, key = { "n:" + it.key }) { n ->
                    NoticeRow(n, opened = openKey == n.key, onOpen = { openKey = if (openKey == n.key) null else n.key }, state = state, inbox = inbox)
                }
            }
        }
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

/** A row another feature put: a tap goes there, X takes it away, ⋮ 다시 보지 않기 turns its kind off. */
@Composable
private fun PostedRow(r: InboxRow, inbox: Inbox, state: NoticeStackState) {
    InboxLine(
        icon = when (r.section) { InboxSection.PEOPLE -> Icons.Outlined.Forum; InboxSection.NOTICE -> Icons.Outlined.Info; InboxSection.SUGGEST -> Icons.Outlined.Lightbulb },
        tint = MaterialTheme.colorScheme.primary,
        title = r.title, line = r.line, time = com.bamnat.app.ui.studio.ago(r.at),
        onClick = { state.sheetOpen = false; r.open(); inbox.remove(r.key) },
        onClose = {
            inbox.remove(r.key)
            AppSnack.show("알림을 지웠어요", undo = { inbox.putBack(listOf(r)) })
        },
        onMute = {
            inbox.mute(r.kind, r.title)
            AppSnack.show("이런 알림은 다시 보이지 않아요. 기능 켜고 끄기에서 다시 켤 수 있어요", undo = { inbox.unmute(r.kind) })
        },
    )
}

/** One of the calendar's notices: a tap goes where its button goes, or opens it whole here (a question answered in place). */
@Composable
private fun NoticeRow(n: Notice, opened: Boolean, onOpen: () -> Unit, state: NoticeStackState, inbox: Inbox) {
    val context = LocalContext.current
    val expandable = !(n.onAction != null && n.button)
    Column(Modifier.fillMaxWidth()) {
        InboxLine(
            icon = when (n.tone) {
                Tone.Plain -> if (n.section == InboxSection.SUGGEST) Icons.Outlined.Lightbulb else Icons.Outlined.Info
                Tone.Good -> Icons.Outlined.CheckCircle
                Tone.Caution, Tone.Danger -> Icons.Outlined.WarningAmber
            },
            tint = if (n.tone == Tone.Plain) MaterialTheme.colorScheme.primary else n.tone.color(),
            // Open, the notice says its words itself under the row (the line was said twice, 갈래 L 카드 사진).
            title = n.label ?: n.line.orEmpty(), line = n.line?.takeIf { it != n.label && !(opened && expandable) },
            onClick = {
                val go = n.onAction
                if (go != null && n.button) { state.sheetOpen = false; go() } else onOpen()
            },
            onClose = {
                val answer = n.onClose
                if (answer != null) answer() else state.snooze(context, n.key, said = "닫았어요. 내일 다시 볼 수 있어요")
            },
            // An alarm that may not ring, an agreement the app waits for: never turned off from here.
            onMute = if (n.urgent) null else ({
                inbox.mute(n.kind, n.label ?: n.line.orEmpty())
                AppSnack.show("이런 알림은 다시 보이지 않아요. 기능 켜고 끄기에서 다시 켤 수 있어요", undo = { inbox.unmute(n.kind) })
            }),
            expanded = if (expandable) opened else null,
            go = n.action?.takeIf { n.onAction != null && n.button },
        )
        // Answered or closed in here (its card drew something, then nothing), the row goes with it, also before the calendar's copy of
        // the notice hears of it (조건 3: 답한 뒤에 크게 남지 않아요).
        var drew by remember(n.key) { mutableStateOf(false) }
        if (opened && expandable) Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().onSizeChanged { if (it.height > 0) drew = true else if (drew) state.answered = state.answered + n.key }) {
                CompositionLocalProvider(LocalNoticeFolded provides false) { n.content() }
            }
        }
    }
}

/** The row's shape: the meaning in the icon's colour, the title and one line, when, X and ⋮. */
@Composable
private fun InboxLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    line: String?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onMute: (() -> Unit)?,
    time: String? = null,
    /** Null for a row that goes somewhere; else whether its notice is open under it. */
    expanded: Boolean? = null,
    /** What a tap on the row does, said under its words ('허용', '입히기 전으로'): the row is the button, 48dp and more. */
    go: String? = null,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics {
        if (expanded != null) contentDescription = title + (line?.let { ", $it" } ?: "") + if (expanded) ", 펼침" else ", 눌러서 펼치기"
    }) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                // The line and when, in one quiet line (two lines a row, 조건 4).
                val quiet = listOfNotNull(line?.takeIf { it.isNotBlank() }, time).joinToString(" · ")
                if (quiet.isNotEmpty()) Text(quiet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (go != null) Text(go, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기: $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (onMute != null) Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "더 보기: $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("이런 알림 다시 보지 않기") }, onClick = { menu = false; onMute() })
                }
            }
        }
    }
}

/**
 * [RVP-43] HQ-49 The bell on the calendar head's row, beside 공유: always there (the 알림함 is a place), with how many wait besides the
 * band as a number, no mark when none. TalkBack reads "알림 2개".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeBell(state: NoticeStackState, modifier: Modifier = Modifier) {
    val count = state.inBell
    val said = if (count > 0) "알림 ${count}개" else "알림함"
    IconButton(onClick = { state.openSheet(null) }, modifier = modifier) {
        // The same mark as 나's (its size kept, so with big letters it stays inside the button and is not cut, 조건 12); the mark is
        // what TalkBack reads, once.
        if (count > 0) BadgedBox(badge = { com.bamnat.app.ui.IconCountBadge(count, said, cap = 9) }) {
            Icon(Icons.Outlined.Notifications, null)
        } else Icon(Icons.Outlined.Notifications, said)
    }
}

/**
 * [CAL-50] One notice of the calendar: its place in the order is its priority. [content] is the notice whole, as the 알림함 shows it
 * opened (and draws nothing when it has nothing to say); [line] is what the band and the row say, in one line, with [action] (else
 * '보기', which opens the sheet on it; none when not [button]). [onClose] answers it for good; without one, X puts it away until
 * tomorrow.
 *
 * 2.0.0 [RVP-43] HQ-49: only an [urgent] notice can be the band over the month; the rest are rows of the 알림함 in their [section].
 * [kind] is what '다시 보지 않기' turns off (by default the key without what follows ':').
 */
internal class Notice(
    val key: String,
    val label: String? = null,
    val line: String? = null,
    /** A second, quieter line under [line] (one at most, 56dp still). */
    val detail: String? = null,
    val tone: Tone = Tone.Plain,
    val action: String? = null,
    val onAction: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val kani: KaniMood? = null,
    /** False for a band that only says something (a tap on it still opens the sheet): no '보기' button. */
    val button: Boolean = true,
    /** Has to be seen now: may be the band over the month (and is never turned off with 다시 보지 않기). */
    val urgent: Boolean = false,
    val section: InboxSection = InboxSection.NOTICE,
    kind: String? = null,
    val content: @Composable () -> Unit,
) {
    val kind: String = kind ?: ("calendar." + key.substringBefore(':'))
}

/** [CAL-50] How high each notice drew itself, which one is the band, and what is in the bell. */
internal class NoticeStackState(sheet: Boolean = false, snoozed: Set<String> = emptySet()) {
    val heights = mutableStateMapOf<String, Int>()
    /** Notices with something to say right now. */
    val count: Int get() = heights.values.count { it > 0 }
    /** [RVP-43] How many wait in the bell (the 알림함's rows but the band). */
    var inBell by mutableIntStateOf(0)
        internal set
    internal var present: List<String> by mutableStateOf(emptyList())
    internal var bandKey: String? by mutableStateOf(null)
    /** PC-81: Whether the band's place is open; fixed once [settled]. */
    internal var slotOpen by mutableStateOf(false)
    internal var settled by mutableStateOf(false)
    internal var snoozed: Set<String> by mutableStateOf(snoozed)
    /** Notices answered or closed inside the sheet, until the calendar's own copy says nothing too. */
    internal var answered: Set<String> by mutableStateOf(emptySet())
    var sheetOpen by mutableStateOf(sheet)
    internal var sheetFocus: String? by mutableStateOf(null)

    fun openSheet(key: String?) {
        sheetFocus = key
        sheetOpen = true
    }

    internal fun snooze(context: Context, key: String, said: String = "알림함(종)에 넣어 뒀어요. 내일 다시 띄워 드릴게요") {
        NoticeSnooze.put(context, key)
        snoozed = snoozed + key
        // 조건 10: the X is taken back here; 조건 16: where it went, said in a person's words.
        AppSnack.show(said, undo = {
            NoticeSnooze.remove(context, key)
            snoozed = snoozed - key
        })
    }
}

@Composable
internal fun rememberNoticeStackState(): NoticeStackState {
    val context = LocalContext.current
    return rememberSaveable(saver = androidx.compose.runtime.saveable.Saver({ it.sheetOpen }, { NoticeStackState(it, NoticeSnooze.today(context)) })) {
        NoticeStackState(false, NoticeSnooze.today(context))
    }
}

/** [CAL-50] True inside a notice measured for the band and the bell, not seen whole (the sheet shows it whole). */
internal val LocalNoticeFolded = compositionLocalOf { false }

/** PC-81: How long the calendar has to settle which band it shows, from opening. */
internal const val SETTLE_MS = 700L

/** [RVP-43] Notices put away with their X, until the end of the day (kept on the phone only). */
internal object NoticeSnooze {
    private const val PREFS = "notice_snooze"

    fun today(context: Context): Set<String> {
        val day = Today.date().toEpochDay()
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.filter { (_, v) -> (v as? Long) == day }.keys
    }

    fun put(context: Context, key: String) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = Today.date().toEpochDay()
        p.edit {
            // Yesterday's keys go, so the file stays small.
            p.all.filter { (_, v) -> (v as? Long) != day }.keys.forEach { remove(it) }
            putLong(key, day)
        }
    }

    fun remove(context: Context, key: String) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(key) }
}
