package com.bamnat.app.ui.company

import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.TalkPost
import com.bamnat.core.plaza.IndustryGroups
import com.bamnat.core.plaza.PlazaTag
import com.bamnat.core.plaza.PlazaTags
import com.bamnat.core.plaza.StylePlaza
import androidx.compose.ui.semantics.Role

/**
 * 1.39 [PLZC] 광장을 사람 먼저로 (사용자 2026-11-07 "광장은 급여를 나누는 것 말고는 하는 것이 전혀 없어", 설계 `계획/1.39-광장-커뮤니티-설계.md`):
 * the plaza's own page is its talk, newest first, with who wrote it (근무 태그), lenses to pick by, 나도요 and older posts; the
 * sheet stays for answers and writing. Only while plaza.people is on (처리방침 3.12); before then 광장 is as it was.
 */
internal fun plazaPeopleOn(c: AppContainer): Boolean = c.flags.on(com.bamnat.core.flags.FlagCatalog.PLAZA_PEOPLE)

/** [PLZC-10] 근무 태그 under a writer's name: 📅 교대 · 4조 2교대 · 밤낮 7개월 · 🌙 야간 근무 중. A tap says where it comes from. */
@Composable
internal fun PlazaTagLine(tag: PlazaTag, modifier: Modifier = Modifier) {
    var why by remember { mutableStateOf(false) }
    if (why) AlertDialog(
        onDismissRequest = { why = false },
        title = { Text("근무 태그") },
        text = { Text("그분 달력의 근무 설정에서 저절로 붙어요. 직접 적을 수 없어요. 인증은 아니지만, 그 근무로 달력을 쓰고 있다는 표시예요. " +
            "‘밤낮 n개월’은 서버가 이 휴대폰을 처음 본 때부터 센 값이에요.") },
        confirmButton = { TextButton(onClick = { why = false }) { Text("닫기") } },
    )
    val words = "📅 " + tag.line() + (tag.now?.let { " · ${it.mark} ${it.words}" } ?: "")
    Text(words, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.clickable(onClickLabel = "근무 태그 설명") { why = true }.padding(vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = tag.speech() })
}

/** [PLZC-15][PLZC-16] Beside a writer's name: 🌱 첫 글, and 도움 n (answers picked as 도움 됐어요). */
@Composable
internal fun WriterMarks(p: TalkPost) {
    if (p.first) { Spacer(Modifier.width(4.dp)); StatusChip("🌱 첫 글", Tone.GOOD) }
    if (p.helped > 0) {
        Spacer(Modifier.width(4.dp))
        Text("도움 ${p.helped}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.semantics { contentDescription = "답이 도움 된 횟수 ${p.helped}" })
    }
}

/** [PLZC-04] 나도요: one reaction on a room's post (비공감 stays for a company's rule talk only). The picked one again takes it back. */
@Composable
internal fun MeToo(count: Int, mine: Boolean?, onVote: (Boolean?) -> Unit) {
    val picked = mine == true
    TextButton(onClick = { onVote(if (picked) null else true) }, modifier = Modifier.heightIn(min = 48.dp)
        .semantics { contentDescription = if (picked) "나도요 $count, 눌렀어요" else "나도요 $count" }) {
        Text("🙋 나도요 $count", style = MaterialTheme.typography.labelMedium, fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [PLZC-10] 3-3 공개 범위: what my posts show, kept on this phone. */
@Composable
internal fun PlazaTagScopeDialog(c: AppContainer, room: String?, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(c.plazaTags.scope()) }
    val style = room?.let(StylePlaza::styleOf)
    val preview by produceState<PlazaTag?>(null, s) {
        val t = room?.let { c.plazaTags.tagFor(it) }
        value = t?.copy(months = if (s.months) 0 else null)
    }
    var industryOpen by remember { mutableStateOf(false) }
    // 1.39 [PLZC-41] 광장 알림 두 가지, saved with the rest.
    var pushAnswers by remember { mutableStateOf(com.bamnat.app.company.PlazaPush.answers(c.appContext)) }
    var pushSame by remember { mutableStateOf(com.bamnat.app.company.PlazaPush.same(c.appContext)) }
    val watchRoom = remember { com.bamnat.app.company.PlazaPush.watchRoom(c) }
    val watchForm by produceState<String?>(null, s.form) {
        value = if (!s.form) null else watchRoom?.let { runCatching { c.plazaTags.tagFor(it)?.form?.label }.getOrNull() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("광장에 보이는 내 근무 태그") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("달력의 근무 설정에서 저절로 정해져요. 직접 적을 수는 없고, 무엇을 보일지만 골라요. 바꾸면 새 글부터 바뀌어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                preview?.let { SoftCard { Column(Modifier.padding(12.dp)) {
                    Text("이렇게 보여요", style = MaterialTheme.typography.labelMedium)
                    PlazaTagLine(it)
                } } }
                ScopeRow("근무형태 (예: 4조 2교대)", s.form) { s = s.copy(form = it) }
                ScopeRow("밤낮 쓴 개월 수", s.months) { s = s.copy(months = it) }
                if (style == null || style in PlazaTags.NOW_STYLES) ScopeRow("쓸 때 근무 중이면 표시", s.now) { s = s.copy(now = it) }
                if (style == null || style in PlazaTags.INDUSTRY_STYLES) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("업종", style = MaterialTheme.typography.bodyMedium)
                            Text(s.industry?.let { IndustryGroups.byKey(it)?.name } ?: "보이지 않음 (회사를 좁힐 수 있어 처음엔 꺼져 있어요)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { industryOpen = !industryOpen }) { Text(if (industryOpen) "접기" else "고르기") }
                    }
                    if (industryOpen) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.bamnat.app.ui.components.FilterChip(selected = s.industry == null, onClick = { s = s.copy(industry = null) }, label = { Text("보이지 않음") })
                        PlazaTags.industries().forEach { g ->
                            com.bamnat.app.ui.components.FilterChip(selected = s.industry == g.key, onClick = { s = s.copy(industry = g.key) }, label = { Text(g.name) })
                        }
                    }
                }
                Text("유형(교대·알바 등)은 늘 보여요. 회사 정보 광장에는 태그가 붙지 않아요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("알림", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ScopeRow("내 질문에 답이 오면 바로 알림", pushAnswers) { pushAnswers = it }
                if (watchRoom != null) {
                    ScopeRow(watchForm?.let { "나와 같은 근무($it)에 새 질문이 오면 알림" } ?: "나와 같은 근무에 새 질문이 오면 알림",
                        pushSame && watchForm != null) { if (watchForm != null) pushSame = it }
                    if (watchForm == null) Text("근무형태가 태그에 보일 때 쓸 수 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (c.settingsRepository.current.plazaNews) "밤낮 소식 알림 하나로 와요. 자는 시간·근무 중에는 모아 두었다 알려요."
                    else "설정 › 알림 › 밤낮 소식이 꺼져 있어 지금은 오지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = {
            c.plazaTags.setScope(s)
            com.bamnat.app.company.PlazaPush.setAnswers(c.appContext, pushAnswers)
            com.bamnat.app.company.PlazaPush.setSame(c.appContext, pushSame && watchForm != null)
            c.appScope.launch {
                runCatching { com.bamnat.app.company.PushPhone.sync(c) }
                com.bamnat.app.company.PlazaPush.syncWatch(c)
            }
            onDismiss()
        }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ScopeRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(onClickLabel = label) { onChange(!on) }) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** The lenses of a room's talk (one room, many ways to look). */
internal object Lens {
    const val ALL = "all"
    const val SAME = "same"
    const val INDUSTRY = "industry"
    const val WORKING = "working"
    const val WAITING = "waiting"
    const val QUESTION = "question"
    const val TIP = "tip"
    const val SHOW = "show"
    /** 1.39 [PLZC-32] 야간 라운지: while my calendar says I am on a night shift, the posts of others awake at work tonight. */
    const val LOUNGE = "lounge"
}

/** [PLZC-32] How far back 야간 라운지 looks. */
private const val LOUNGE_HOURS = 12L

/** Whether [p] (a top post) answers to [lens], with my tag for 나와 같은 근무 and 같은 업종, and the answers under it. */
internal fun inLens(p: TalkPost, lens: String, mine: PlazaTag?, answers: List<TalkPost>): Boolean = when (lens) {
    Lens.SAME -> mine?.form != null && p.tag?.form == mine.form
    Lens.INDUSTRY -> mine?.industry != null && p.tag?.industry == mine.industry
    Lens.WORKING -> p.tag?.now != null
    Lens.LOUNGE -> p.tag?.now == PlazaTag.Now.NIGHT && p.at.isAfter(com.bamnat.core.time.Today.instant().minusSeconds(LOUNGE_HOURS * 3600))
    // No answer yet from anyone but the one who asked (참여자 N is the same writer within a talk; unnumbered counts as another).
    Lens.WAITING -> p.kind == "question" && answers.none { a -> if (p.mine) !a.mine else a.mine || a.who == 0 || a.who != p.who }
    Lens.QUESTION, Lens.TIP, Lens.SHOW -> p.kind == lens
    else -> true
}

/** [PLZC-20] 근무 카드 under a post: a small box with this month's numbers from the writer's calendar. */
@Composable
internal fun WorkCardLine(card: com.bamnat.core.plaza.WorkCard, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = card.spoken() }) {
        Text("📅 " + card.line(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** [PLZC-21] What a 오늘 출근 kind says: on its button, and with its count. */
internal fun checkinWords(kind: String): Pair<String, String> = when (kind) {
    "night" -> "오늘 야간 출근 ✊" to "오늘 야간 출근"
    "day" -> "오늘 주간 출근 ✊" to "오늘 주간 출근"
    "go" -> "오늘 현장 나가요 ✊" to "오늘 현장 나간 분"
    "rain" -> "비로 공쳤어요 ☔" to "오늘 비로 공친 분"
    else -> kind to kind
}

/**
 * [PLZC-21] 오늘 출근 한 번 누르기: one tap, no post. In 교대 광장 the button is my shift today from my calendar (none on a day off,
 * the counts still shown); in 현장 광장 나가요 or 공쳤어요. Once tapped: 'n명 · 함께 버텨요' and 취소.
 */
@Composable
internal fun CheckinRow(room: String?, checkin: com.bamnat.app.company.Checkin, today: PlazaTag.Now?, onTap: (String?) -> Unit) {
    val kinds = when (room) {
        StylePlaza.FIELD -> listOf("go", "rain")
        else -> listOfNotNull(today?.let { if (it == PlazaTag.Now.NIGHT) "night" else "day" })
    }
    val mine = checkin.mine
    val counted = checkin.counts.filterValues { it > 0 }
    if (mine == null && kinds.isEmpty() && counted.isEmpty()) return
    SoftCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mine != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${checkinWords(mine).second} ${checkin.counts[mine] ?: 1}명 · " + if (mine == "rain") "푹 쉬어요" else "함께 버텨요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onTap(null) }) { Text("취소") }
                }
            } else if (kinds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k ->
                        androidx.compose.material3.OutlinedButton(onClick = { onTap(k) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(checkinWords(k).first)
                        }
                    }
                }
            }
            val others = counted.filterKeys { it != mine }
            if (others.isNotEmpty()) Text(others.entries.joinToString(" · ") { (k, n) -> "${checkinWords(k).second} ${n}명" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [PLZC-13] 시간 맞춤 한 줄 from my own calendar (on this phone only): at work at night, just off a night, or a day off.
 * Null when it has nothing to say. 1.39 [PLZC-60] (사용자 2026-11-08 윗부분 공간): said as the 글 쓰기 box's own words, not a line of its own.
 */
internal fun timeLine(now: PlazaTag.Now?, offAfterNight: Boolean, dayOff: Boolean): Pair<String, String>? = when {
    now == PlazaTag.Now.NIGHT -> "🌙 야간 중이시네요 · 깨어 있는 분들께 한 줄 남겨요" to Lens.LOUNGE
    now == PlazaTag.Now.DAY -> "근무 중이시네요 · 쉬는 시간에 한 줄 남겨요" to Lens.ALL
    offAfterNight -> "야간 끝나셨어요? 오늘 밤 어땠는지 한 줄 남겨요" to Lens.ALL
    dayOff -> "쉬는 날이에요 · 이번 주 이야기를 한 줄 남겨요" to Lens.ALL
    else -> null
}

/**
 * [PLZC-01] The room's talk as the page itself: newest first, a card per post with name, 근무 태그, words, 나도요 and answers;
 * a tap opens it in the sheet (answers and writing). [older] the pages read with 더 불러오기.
 */
@Composable
internal fun PlazaFeed(
    c: AppContainer,
    vm: PlazaHomeViewModel,
    plan: StylePlaza.Plan,
    talk: com.bamnat.app.company.Talk?,
    error: String?,
    older: List<TalkPost>,
    olderMore: Boolean,
    loadingOlder: Boolean,
) {
    val styleRoom = plan.room?.startsWith("style-") == true
    var lens by rememberSaveable(plan.room) { mutableStateOf(Lens.ALL) }
    val mine = vm.mine.collectAsStateWithLifecycle().value
    // My tag (and so the lenses that need one), and the 시간 맞춤 line, worked out from my calendar.
    // 1.39 [PLZC-60] (사용자 2026-11-08 "위에 공간을 불필요하게 차지"): the 시간 맞춤 줄·이번 주 주제·글 쓰기 are one 글 쓰기 box; 오늘 글 n is
    // on the head line ([PlazaHead]), 답 기다리는·근무 중 counts are on their own lens chips; 내 태그 is in the head's menu and the sheet.
    // 2.0.0 [RVN-06] COMP-01[U10a-01] 신고·안 보기·지우기 right on a card, 지난 글 too (before, only in the sheet, which never had them).
    var flagging by remember { mutableStateOf<TalkPost?>(null) }
    var hiding by remember { mutableStateOf<TalkPost?>(null) }
    var deleting by remember { mutableStateOf<TalkPost?>(null) }
    FlagPostDialog(flagging, invest = plan.room == StylePlaza.INVEST, onDismiss = { flagging = null }) { p, reason ->
        vm.talk.flag(p.id, reason) { e -> com.bamnat.app.ui.PutOnNotice.say(e ?: FLAGGED) }
    }
    HideWriterDialog(hiding, onDismiss = { hiding = null }) { p ->
        vm.talk.hide(p)
        vm.load()
        com.bamnat.app.ui.PutOnNotice.say("${p.nick ?: "참여자 ${p.who}"}님 글을 안 보기로 했어요 · 마을 › 팔로잉 › 차단에서 다시 볼 수 있어요")
    }
    deleting?.let { p ->
        com.bamnat.app.ui.components.ConfirmDialog(title = "이 글을 지울까요?", message = "지운 글은 되돌릴 수 없어요.", confirmLabel = "지우기",
            onDismiss = { deleting = null }, onConfirm = { deleting = null; vm.talk.delete(p.id) { vm.load() } }, destructive = true)
    }
    val menu = FeedMenu(onFlag = { flagging = it }, onHide = { hiding = it }, onDelete = { deleting = it }, room = plan.room, roomName = plan.roomTitle)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        talk?.pulse?.checkin?.let { CheckinRow(plan.room, it, vm.todayShift.collectAsStateWithLifecycle().value, vm::checkin) }
        PlazaComposer(vm.timeLine.collectAsStateWithLifecycle().value?.first ?: plan.hint.ifBlank { "이 광장 분들과 나누고 싶은 이야기를 적어 주세요" },
            topic = talk?.topic, onWrite = { vm.openTalk() })
        // [PLZC-32] On a night shift the plaza opens as 야간 라운지 once (a tap on 전체 stays).
        val night = styleRoom && mine?.now == PlazaTag.Now.NIGHT
        var loungeOpened by rememberSaveable(plan.room) { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(night) {
            if (night && !loungeOpened) { loungeOpened = true; if (lens == Lens.ALL) lens = Lens.LOUNGE }
            if (!night && lens == Lens.LOUNGE) lens = Lens.ALL
        }
        val lenses = buildList {
            if (night) add(Lens.LOUNGE to "🌙 야간 라운지")
            add(Lens.ALL to "전체")
            if (styleRoom && mine?.form != null) add(Lens.SAME to "나와 같은 근무 · ${mine.form!!.label}")
            if (styleRoom && mine?.industry != null) add(Lens.INDUSTRY to "같은 업종 · ${IndustryGroups.byKey(mine.industry)?.name}")
            val pulse = talk?.pulse
            if (styleRoom && plan.room?.let(StylePlaza::styleOf) in PlazaTags.NOW_STYLES)
                add(Lens.WORKING to "지금 근무 중" + (pulse?.working?.takeIf { it > 0 }?.let { " ${it}명" } ?: ""))
            add(Lens.WAITING to "답 기다리는 질문" + (pulse?.waiting?.takeIf { it > 0 }?.let { " $it" } ?: ""))
            add(Lens.QUESTION to "질문"); add(Lens.TIP to "팁"); add(Lens.SHOW to "자랑")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lenses.forEach { (k, label) -> com.bamnat.app.ui.components.FilterChip(selected = lens == k, onClick = { lens = k }, label = { Text(label) }) }
        }
        when {
            talk == null && error != null -> Text(error, style = MaterialTheme.typography.bodyMedium)
            talk == null -> Text("불러오는 중이에요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val all = (talk.posts + older).distinctBy { it.id }
                val tops = all.filter { it.parent == null }.sortedByDescending { it.at }
                val answersOf = all.filter { it.parent != null }.groupBy { it.parent }
                // [PLZC-05] A question without an answer for a day comes up once more, on top of 전체.
                val dayAgo = com.bamnat.core.time.Today.instant().minusSeconds(24 * 3600)
                val lifted = if (lens != Lens.ALL) null else tops.lastOrNull { q ->
                    !q.mine && q.at.isBefore(dayAgo) && inLens(q, Lens.WAITING, mine, answersOf[q.id].orEmpty())
                }
                val shown = tops.filter { it.id != lifted?.id && inLens(it, lens, mine, answersOf[it.id].orEmpty()) }
                lifted?.let { q ->
                    Text("🙋 하루 넘게 답을 기다리는 질문이에요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.runtime.key("lifted-" + q.id) {
                        FeedCard(q, answersOf[q.id].orEmpty(), menu,
                            onOpen = { vm.openTalk(q.id) }, onVote = { ok -> vm.vote(q.id, ok) }, onReply = { vm.openTalk(q.id, reply = true) })
                    }
                }
                if (tops.isEmpty()) Text("아직 글이 없어요. 처음 이야기를 꺼내 주세요.", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp))
                else if (shown.isEmpty() && lifted == null) Text(when (lens) {
                    Lens.WAITING -> "답을 기다리는 질문이 없어요"
                    Lens.LOUNGE -> "오늘 밤 라운지는 아직 조용해요. 야간 중인 분들께 먼저 한 줄 남겨 보세요"
                    else -> "이렇게 고른 글이 아직 없어요"
                },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                shown.forEach { p -> androidx.compose.runtime.key(p.id) {
                    FeedCard(p, answersOf[p.id].orEmpty(), menu,
                        onOpen = { vm.openTalk(p.id) }, onVote = { ok -> vm.vote(p.id, ok) }, onReply = { vm.openTalk(p.id, reply = true) })
                } }
                if (talk.more && (older.isEmpty() || olderMore)) OutlinedButton(onClick = vm::more, enabled = !loadingOlder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (loadingOlder) "불러오는 중…" else "지난 글 더 보기") }
            }
        }
    }
}

/** 2.0.0 [RVN-06] What a card's ⋮ does; [room]/[roomName] keep a post (간직하기) as the sheet does. */
internal class FeedMenu(val onFlag: (TalkPost) -> Unit, val onHide: (TalkPost) -> Unit, val onDelete: (TalkPost) -> Unit,
    val room: String?, val roomName: String)

/**
 * One post on the plaza's page. 2.0.0 [RVN-06]: every card opens the sheet on its own post (a 지난 글 too: the sheet reads its thread,
 * COMP-01); ⋮ has 신고·안 보기·간직·지우기 right here; the head is the name and time with ⋮ always at the end, the marks on a line of
 * their own (COMP-08: a long name and chips pushed ⋮ out); a post with a 개인 정보 신고 is folded until tapped and my post hidden from
 * others says so with 이의 제기 (COMP-02).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FeedCard(p: TalkPost, answers: List<TalkPost>, menu: FeedMenu, onOpen: () -> Unit, onVote: (Boolean?) -> Unit,
    /** PC 4차 c0ca3d9f: '답 남기기'·'환영 답 남기기' open the sheet with the answer box on this post. */
    onReply: () -> Unit = onOpen) {
    var reveal by rememberSaveable(p.id) { mutableStateOf(false) }
    val folded = p.review && p.hidden == null && !p.mine && !reveal
    val name = p.nick ?: if (p.who > 0) "참여자 ${p.who}" else "참여자"
    SoftCard {
        Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (folded) "신고된 글 펼쳐 보기" else "이 글 보기") { if (folded) reveal = true else onOpen() }
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 2.0.0 [RVN-06] COMP-08: name and time share what is left, so the ⋮ stands at the same end on every card.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    val maker = p.maker
                    // 1.39 [PLZC-40] the name in its mark's color, a tap to the 프로필.
                    com.bamnat.app.ui.village.MakerName(if (p.mine) "나 ($name)" else name, p.mark,
                        onProfile = maker?.takeIf { !p.mine }?.let { m -> { com.bamnat.app.recipe.RecipeLinks.openMaker(m) } },
                        modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    Text(" · " + ago(p.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                FeedMenuButton(p, menu)
            }
            val kindChip = when (p.kind) {
                "question" -> (if (p.poll != null) "투표" else "질문") to Tone.QUIET
                "tip" -> "팁" to Tone.GOOD
                "show" -> "자랑" to Tone.WAIT
                else -> null
            }
            if (p.first || p.helped > 0 || kindChip != null) androidx.compose.foundation.layout.FlowRow(Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                kindChip?.let { (t, tone) -> StatusChip(t, tone) }
                if (p.first) StatusChip("🌱 첫 글", Tone.GOOD)
                if (p.helped > 0) Text("도움 ${p.helped}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.align(Alignment.CenterVertically).semantics { contentDescription = "답이 도움 된 횟수 ${p.helped}" })
            }
            Column(Modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                p.tag?.let { PlazaTagLine(it) }
                if (folded) {
                    // COMP-02 Until an operator looks, the words stay folded on 광장's first page as in the sheet.
                    Text("개인 정보가 있다는 신고로 운영자가 보고 있어요 · 눌러서 보기", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(p.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        color = if (p.hidden != null || p.review) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    p.card?.let { WorkCardLine(it) }
                    p.attach?.let { (_, title) -> Text("🎨 $title", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
                p.hidden?.let { why ->
                    // COMP-02 My post others no longer see: said on the card too, with 이의 제기.
                    Text("다른 분께는 가려졌어요 · $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (p.mine) TextButton(onClick = { com.bamnat.app.ui.settings.InquiryLinks.ask(appealStart(p.id)) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("이의 제기") }
                }
            }
            // 2.0.0 [RVN-06] 디자인 품질 12: at 200% '아직 답이 없어요 · 답 남기기' stood beside 나도요 a word a line; it now goes under it.
            if (p.hidden == null) androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                if (!p.mine) Box(Modifier.align(Alignment.CenterVertically)) { MeToo(p.agree, p.myVote, onVote) }
                else Text("🙋 나도요 ${p.agree}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp))
                val helped = p.answer != null
                Text(when {
                    answers.isEmpty() && p.first && !p.mine -> "첫 글이에요 · 환영 답 남기기"
                    answers.isEmpty() && p.kind == "question" -> "아직 답이 없어요 · 답 남기기"
                    answers.isEmpty() -> "답 남기기"
                    helped -> "답 ${answers.size} · 도움 된 답 있음"
                    else -> "답 ${answers.size}"
                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { if (answers.isEmpty()) onReply() else onOpen() }
                        .padding(vertical = 12.dp, horizontal = 8.dp))
            }
        }
    }
}

/** [RVN-06] The card's ⋮: 간직하기, 신고하기 or 지우기 (mine), 이 사람 글 안 보기, 닫기 — as in the sheet. */
@Composable
private fun FeedMenuButton(p: TalkPost, menu: FeedMenu) {
    var open by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val saved = remember(menu.room) { menu.room?.let { com.bamnat.app.company.SavedPosts.of(context) } }
    val keptList by (saved?.items ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsStateWithLifecycle()
    if (p.id.startsWith("local-")) return
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, "${if (p.mine) "내 글" else "이 글"} 더 보기: 신고·안 보기·간직")
        }
        com.bamnat.app.ui.components.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (saved != null && menu.room != null && p.hidden == null) {
                val kept = keptList.any { it.id == p.id }
                androidx.compose.material3.DropdownMenuItem(text = { Text(if (kept) "간직 빼기" else "간직하기") }, onClick = {
                    open = false
                    if (kept) saved.remove(p.id) else saved.save(p, menu.room, menu.roomName)
                    com.bamnat.app.ui.PutOnNotice.say(if (kept) "간직한 글에서 뺐어요" else "간직했어요 · 마을 › 공방 › 간직한 글 · 내 글에서 다시 봐요")
                })
            }
            if (p.mine) androidx.compose.material3.DropdownMenuItem(text = { Text("지우기") }, onClick = { open = false; menu.onDelete(p) })
            else androidx.compose.material3.DropdownMenuItem(text = { Text("신고하기") }, onClick = { open = false; menu.onFlag(p) })
            if (!p.mine && p.author != null) androidx.compose.material3.DropdownMenuItem(text = { Text("이 사람 글 안 보기") }, onClick = { open = false; menu.onHide(p) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("닫기", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { open = false })
        }
    }
}

/**
 * 1.39 [PLZC-60] (사용자 2026-11-08 "화면을 보면 위에 공간이 … 불필요하게 공간을 차지"): the plaza's whole head as one line: its name
 * (in 마을, where there is no top bar), 오늘 글 n, the one door that is the plaza's own business (회사 정보 · 시세 · 선배 답), and a menu with
 * the rest (회사 규칙 광장, 우리 회사 찾기, 내 태그·알림, 지킬 것). Before, these were six rows above the first post.
 */
@Composable
internal fun PlazaHead(
    title: String?,
    plan: StylePlaza.Plan,
    today: Int?,
    picks: Int,
    onPicks: () -> Unit,
    onCompany: () -> Unit,
    onShare: () -> Unit,
    onRates: () -> Unit,
    onAll: (() -> Unit)?,
    onFind: (() -> Unit)?,
    onTag: (() -> Unit)?,
) {
    val company = plan.top == StylePlaza.Top.COMPANY_ROOM
    // The one door shown: a company's 정보, else 시세 (알바·현장 광장's own business), else 선배 답 when there are some.
    val main: Pair<String, () -> Unit>? = when {
        company -> "📋 회사 정보" to onCompany
        plan.rates != null -> "💰 시세" to onRates
        picks > 0 -> "📚 선배 답 $picks" to onPicks
        else -> null
    }
    val menu = plazaMenu(plan, picks, onPicks, onCompany, onShare, onRates, onAll, onFind, onTag)
    var menuOpen by remember { mutableStateOf(false) }
    var rulesOpen by remember { mutableStateOf(false) }
    if (rulesOpen) AlertDialog(
        onDismissRequest = { rulesOpen = false },
        title = { Text("이 광장에서 지킬 것") },
        text = { Text(plan.note) },
        confirmButton = { TextButton(onClick = { rulesOpen = false }) { Text("닫기") } },
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() })
            }
            today?.let {
                Text((if (title != null) " · " else "") + "오늘 글 $it", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        main?.let { (label, go) -> TextButton(onClick = go) { Text(label, maxLines = 1) } }
        androidx.compose.foundation.layout.Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert,
                    "광장 메뉴: 회사 규칙 광장, 내 근무 태그 등")
            }
            com.bamnat.app.ui.components.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu.forEach { (label, go) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; go() })
                }
                if (plan.note.isNotBlank()) androidx.compose.material3.DropdownMenuItem(text = { Text("ℹ️ 이 광장에서 지킬 것") },
                    onClick = { menuOpen = false; rulesOpen = true })
            }
        }
    }
}

/** [PLZC-60] What the head's menu holds, in order: every door the plaza had above its posts, and 내 태그·알림. */
internal fun plazaMenu(plan: StylePlaza.Plan, picks: Int, onPicks: () -> Unit, onCompany: () -> Unit, onShare: () -> Unit, onRates: () -> Unit,
    onAll: (() -> Unit)?, onFind: (() -> Unit)?, onTag: (() -> Unit)?): List<Pair<String, () -> Unit>> = buildList {
    val company = plan.top == StylePlaza.Top.COMPANY_ROOM
    if (picks > 0) add("📚 선배 답 모음 $picks" to onPicks)
    if (company) { add("📋 근무·급여 정보·고친 점" to onCompany); add("📤 내 설정 보내기" to onShare) }
    plan.rates?.let { r -> add("💰 ${r.title}" to onRates) }
    onAll?.let { add((if (company) "🏢 다른 회사 규칙" else "📏 회사 규칙 광장") to it) }
    onFind?.let { add("🔎 우리 회사 찾기" to it) }
    onTag?.let { add("🏷 내 근무 태그·광장 알림" to it) }
}

/**
 * 1.39 [PLZC-60] 글 쓰기 as one box: what to write now ([timeLine] from my calendar, else the plaza's own hint) and 이번 주 주제, a tap
 * opens the sheet. Before, the 시간 맞춤 줄, the 글 쓰기 button and the 주제 were three rows.
 */
@Composable
internal fun PlazaComposer(prompt: String, topic: String?, onWrite: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onWrite, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) {
            contentDescription = "글 쓰기. $prompt" + (topic?.let { ". 이번 주 주제 $it" } ?: "")
        }) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                topic?.let { Text("이번 주 주제 · $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large) {
                Text("글 쓰기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}
