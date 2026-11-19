package com.bamnat.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.logic.LogicRunner
import com.bamnat.app.logic.LogicStore
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.NoticeOverlap
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.bamnat.app.ui.theme.color

/**
 * 1.39 [NTC-04] 알림 한눈에 (사용자 2026-11-08 "줄이거나 추가를 할 수 있게 가지고 있거나 넣을 수 있는 것들 리스트들도 보여 줬으면"):
 * every notice this phone sends — the app's own and my 알림 rules — in two lists, 켜 둔 것 and 더 켤 수 있는 것, each with its switch.
 * Two that say the same thing are marked so one can go. Shown in 설정 › 알림과 잠 and in the 넣기 창 of a 규칙·단추 work.
 *
 * 2.0.0 [RVO-22] 검토 SET-04 ('한눈에'인데 10개뿐, 같은 스위치가 이름을 달리해 한 쪽에 두 번, 판에서 켜면 권한도 채널 꺼짐도 보지 않아
 * 켜 둔 알림에 올라가도 오지 않음): this is the one list of the app's notices. Each says its name as 설정 › 알림과 잠 says it (one name),
 * what it does in full (the row shortens it as it is learned), the channel it comes on, and which kinds of work have it; the page
 * draws its switches from here and the board turns them as the page does ([NoticeBoard.turn]): it asks for notices when they are off
 * and says so on the row when the phone keeps that channel quiet.
 */
object NoticeBoard {
    /**
     * One of the app's own notices. [label] is the row's name everywhere (설정 찾기 finds it by it); [does] what it does, in the kind of
     * work's words; [channel] the one it comes on (null: it shows in the app, not as a notice); [onlyChannel] a notice with no switch of its
     * own in the app, only the phone's (문의 답): its switch opens that channel; [after] what must follow a change (alarms set again).
     */
    data class Own(val key: String, val label: (WorkStyle?) -> String, val does: (WorkStyle?) -> String, val get: (AppSettings) -> Boolean,
                   val set: (AppSettings, Boolean) -> AppSettings, val shows: (WorkStyle?) -> Boolean, val channel: String? = null,
                   val onlyChannel: Boolean = false, val after: ((AppContainer) -> Unit)? = null)

    private fun planned(s: WorkStyle?) = s?.usesPayEngine != false
    private fun nights(s: WorkStyle?) = s == null || s == WorkStyle.SHIFT
    private fun not(vararg no: WorkStyle) = { s: WorkStyle? -> s == null || s !in no }
    private fun alarmWord(s: WorkStyle?) = com.bamnat.app.ui.shifts.alarmNoun(s)

    val OWN: List<Own> = listOf(
        // [ALRM-27] 알람 지킴이: what could keep an alarm quiet, said hours before (설정 › 알람에도 같은 줄).
        Own("alarmGuard", { GUARD_ROW }, { "알람 ${com.bamnat.app.alarm.AlarmGuard.LEAD_MILLIS / 3_600_000}시간 전에 소리 0·알림 꺼짐·방해 금지처럼 알람을 막는 것이 있으면 미리 알려요" },
            { it.alarmGuard }, { s, on -> s.copy(alarmGuard = on) }, { true }, Notifications.STATUS_CHANNEL_ID,
            after = { c -> c.appScope.launch { runCatching { c.rescheduleAlarms() } } }),
        // [ALRM-21] 곧 울릴 알람: on is the 5 minutes it starts with (설정 › 알람 › 울리는 방식에서 3·10분으로).
        Own("alarmSoon", { ALARM_SOON_ROW }, { "휴대폰을 쓰고 있을 때 알람이 곧 울리면 화면 위에 작게 알려요. 거기서 이번 알람만 끌 수 있어요" },
            { it.alarmSoonMinutes > 0 }, { s, on -> s.copy(alarmSoonMinutes = if (on) s.alarmSoonMinutes.takeIf { it > 0 } ?: 5 else 0) }, { true },
            Notifications.STATUS_CHANNEL_ID),
        Own("todayShiftNotice", { "잠금 화면 오늘 ${com.bamnat.app.ui.shifts.lockNoun(it)}" },
            { val w = com.bamnat.app.ui.shifts.lockNoun(it); "오늘과 내일 $w${com.bamnat.app.ui.Fmt.obj(w)} 잠금 화면과 알림창에 늘 보여 줘요. 소리는 나지 않아요" },
            { it.todayShiftNotice }, { s, on -> s.copy(todayShiftNotice = on) }, { true }, Notifications.TODAY_CHANNEL_ID),
        Own("briefing", { "하루 브리핑" }, { if (nights(it)) "근무 전에 커피·낮잠·잘 시간을 소리 없이 한 번 알려 줘요" else "출근 전에 오늘 근무와 잘 시간을 소리 없이 한 번 알려 줘요" },
            { it.briefing }, { s, on -> s.copy(briefing = on) }, ::planned, Notifications.DAY_CHANNEL_ID),
        // 2.0.0 [RVH-41] 검토 T30-82: 52시간 is not a part-timer's.
        Own("afterAlarmCard", { "알람 뒤 오늘 카드" }, { "근무 알람을 끄면 오늘 근무, 이번 주 시간${if (it == WorkStyle.HOURLY) "" else "(52시간까지 남은 시간)"}, 내일 근무를 알림 하나로 보여 줘요. 소리 없이 3시간 뒤 사라져요" },
            { it.afterAlarmCard }, { s, on -> s.copy(afterAlarmCard = on) }, ::planned, Notifications.DAY_CHANNEL_ID),
        Own("bedtimeNotice", { "잘 준비" }, { "근무 전날 밤, 잘 시간 30분 전에 소리 없이 알려 줘요" },
            { it.bedtimeNotice }, { s, on -> s.copy(bedtimeNotice = on) }, ::planned, Notifications.DAY_CHANNEL_ID),
        Own("sleepingNotice", { "자는 중 표시" }, { "‘잠들었어요’를 누르면 알림창에 잔 시간과 다음 알람이 보이고, ‘깼어요’로 끝내요" },
            { it.sleepingNotice }, { s, on -> s.copy(sleepingNotice = on) }, { true }, Notifications.SLEEPING_CHANNEL_ID),
        Own("commuteNotice", { "퇴근길 안전" }, { "야간이 끝날 때 한 번 소리로 알려 줘요. 10분 넘게 늦어지면 운전 중일 수 있어 보내지 않아요" },
            { it.commuteNotice }, { s, on -> s.copy(commuteNotice = on) }, { planned(it) && nights(it) }, Notifications.COMMUTE_CHANNEL_ID),
        Own("leaveTimeNotice", { LEAVE_TIME_ROW }, { "근무가 끝나는 시각에 소리 없이 알려 주고, 더 일하고 나갈 때 ‘지금 퇴근’을 누르면 잔업을 그 시각까지 적어요" },
            { it.leaveTimeNotice }, { s, on -> s.copy(leaveTimeNotice = on) }, { it == WorkStyle.DAY }, Notifications.DAY_CHANNEL_ID),
        // 2.0.0 [RVH-41] 검토 T30-76: the same rule said for every kind (자정을 넘겨 끝나면).
        Own("dayCloseNotice", { DAY_CLOSE_ROW }, { "근무가 끝나고 30분 뒤(쉬는 날은 저녁 9시)에 소리 없이 오늘 쓴 돈과 정리할 것을 알려 줘요. 자정을 넘겨 끝나면 다음 날 아침이에요" },
            { it.dayCloseNotice }, { s, on -> s.copy(dayCloseNotice = on) }, not(WorkStyle.INVEST, WorkStyle.BUSINESS), Notifications.MONEY_CHANNEL_ID),
        Own("shiftPreviewNotice", { SHIFT_PREVIEW_ROW }, { "근무 날에 어느 분류에 평소보다 뚜렷이 더 쓰면, 그 근무 전날 저녁에 한 줄로 알려 줘요. 일주일에 한 번까지예요" },
            { it.shiftPreviewNotice }, { s, on -> s.copy(shiftPreviewNotice = on) },
            { it == WorkStyle.SHIFT || it == WorkStyle.DAY || it == WorkStyle.HOURLY }, Notifications.MONEY_CHANNEL_ID),
        Own("moneyNotices", { "나갈 돈·받을 돈" }, { "고정비는 3일 전과 당일에, 못 받은 돈은 다음 날과 7일 뒤에 알려 줘요. 같은 때 온 것은 하나로 묶어요" },
            { it.moneyNotices }, { s, on -> s.copy(moneyNotices = on) }, { true }, Notifications.MONEY_CHANNEL_ID),
        // D 1.32 [PAYA-05]
        Own("payDepositNotice", { "급여 입금 비교" }, { "은행 알림으로 급여가 들어온 걸 읽으면 예상 급여와 바로 비교해 알려 줘요(결제 알림 읽기를 켠 경우)" },
            { it.payDepositNotice }, { s, on -> s.copy(payDepositNotice = on) }, ::planned, Notifications.MONEY_CHANNEL_ID),
        // [J133-77] 연차 for work with a company.
        Own("leaveNotice", { "연차 사용 촉진 알림" }, { "연차가 없어지기 6개월 전·2개월 전에 남는 날을 알려 줘요(연차 화면에서도 끌 수 있어요)" },
            { it.leaveNotice }, { s, on -> s.copy(leaveNotice = on) }, { it?.usesCompany != false }, Notifications.LEAVE_CHANNEL_ID),
        // 1.39 [PAYX-62] 알바만, and in the app (급여 화면 알바 카드), not as a notice.
        Own("weekHoursNudge", { com.bamnat.core.pay.WeekHoursNudge.ROW },
            { "이번 주 근무가 15시간 바로 아래·위면 급여 화면 알바 카드에 한 줄로 알려 줘요. 주휴수당은 한 주 15시간부터예요(근로기준법 제18조 제3항)" },
            { it.weekHoursNudge }, { s, on -> s.copy(weekHoursNudge = on) }, { it == WorkStyle.HOURLY }),
        // 2.0.0 [RVIB-05] (검토 I07ac-02) 메모·할 일: 근무 중 알림 모으기 for work with hours, 지난 할 일 for a calendar with days off (not 투자).
        Own("memoHoldAtWork", { MEMO_HOLD_ROW }, { "울리지 않는 메모 알림은 근무가 끝날 때 한 장으로 모아 와요 · 울리는 알람은 제때 울려요" },
            { it.memoHoldAtWork }, { s, on -> s.copy(memoHoldAtWork = on) }, ::planned, Notifications.MEMO_CHANNEL_ID),
        Own("memoRestCard", { MEMO_REST_ROW }, { "못 끝낸 할 일을 다음 쉬는 날 아침에 소리 없이 한 장으로 보여 줘요" },
            { it.memoRestCard }, { s, on -> s.copy(memoRestCard = on) }, not(WorkStyle.INVEST), Notifications.MEMO_CHANNEL_ID),
        // 2.0.0 [RVH-41] 검토 T30-78: what is received is a 근무표 only where there is one.
        Own("plazaNews", { "밤낮 소식" }, { "받은 ${if (planned(it)) "근무표" else "달력"}·광장·마을 장터에 새 소식이 있으면 하루 한 번 묶어서 소리 없이 알려 줘요" },
            { it.plazaNews }, { s, on -> s.copy(plazaNews = on) }, { true }, Notifications.PLAZA_CHANNEL_ID),
        // 1.36 [PUSH-01] 문의 답: no switch in the app, only the phone's channel (소리와 함께 바로).
        Own("inquiryReply", { INQUIRY_REPLY_ROW }, { "답이 오면 소리와 함께 바로 알려 줘요. 소리·끄기는 휴대폰 설정에서 정해요" },
            { true }, { s, _ -> s }, { true }, Notifications.INQUIRY_CHANNEL_ID, onlyChannel = true),
    )

    /**
     * The ones [style]'s pages have. 2.0.0 [RVO-22] One left on from another kind of work (연차 알림 on a 현장 calendar) is not shown: the
     * page hides its switch too, and its words are another kind's (조건 13, 유형 낱말 검사).
     */
    @Suppress("UNUSED_PARAMETER")
    fun shown(settings: AppSettings, style: WorkStyle?): List<Own> = OWN.filter { it.shows(style) }

    /** My rules that send an 알림. */
    fun noticeRules(s: AppSettings): List<LogicRule> = Logic.decode(s.logic).rules.filter { r -> r.acts.any { it.verb == LogicVerb.NOTIFY } }

    /** Turns my rule [r] on or off, as the studio's switch does. */
    fun setRule(c: AppContainer, r: LogicRule, on: Boolean) {
        LogicStore(c.appContext).setOff(r.key, !on)
        LogicRunner.rulesChanged(c)
    }
}

/** 2.0.0 [RVO-22] 문의 답 알림: the inquiry channel's row (설정 › 알림과 잠 and the board). */
internal const val INQUIRY_REPLY_ROW = "문의 답 알림"

/**
 * 2.0.0 [RVO-22] 검토 SET-04-4: what keeps a notice that is on from coming, or null. Notices off for the app, or its channel off on the
 * phone; [canPost] and [offChannels] are read once a visit (and again on coming back from the phone's settings).
 */
internal fun noticeBlocked(o: NoticeBoard.Own, settings: AppSettings, canPost: Boolean, offChannels: Set<String>): String? = when {
    o.channel == null -> null
    o.onlyChannel -> if (!canPost || o.channel in offChannels) "휴대폰 설정에서 꺼져 있어 오지 않아요" else null
    !o.get(settings) -> null
    !canPost -> "알림이 꺼져 있어 오지 않아요"
    o.channel in offChannels -> "휴대폰 설정에서 꺼져 있어 오지 않아요"
    else -> null
}

/** The channels the board and the page look at, those the phone keeps quiet (none while notices are off as a whole). */
internal fun offChannels(context: android.content.Context, canPost: Boolean): Set<String> =
    if (!canPost) emptySet() else NoticeBoard.OWN.mapNotNull { it.channel }.distinct().filter { !Notifications.channelOn(context, it) }.toSet()

/**
 * 1.39 [NTC-04] The two lists, folded to one line until opened ("알림 한눈에 · 켜 둔 5개 · 더 켤 수 있는 4개 · 겹침 1").
 * 2.0.0 [RVO-22] 검토 SET-04·U11a-05 ('판이 전부를 담고'): every notice of [NoticeBoard.OWN] the kind of work has, and each switch turns
 * as the page's does: a rule saying the same is asked about first, notices off are asked for, the alarms are set again after 알람
 * 지킴이, and a notice the phone keeps quiet says so on its row with the way to fix it. [refresh] goes up when the screen comes back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoticeBoardCard(startOpen: Boolean = false, modifier: Modifier = Modifier,
                    /** 9차 PC 확인(하): inside a card already titled '알림 한눈에' the line says only the counts. */ titled: Boolean = true,
                    refresh: Int = 0) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    // Found by 설정 찾기: opened at once.
    val pointed = LocalSettingsFocus.current?.row == "알림 한눈에"
    var open by androidx.compose.runtime.saveable.rememberSaveable(pointed) { mutableStateOf(startOpen || pointed) }
    var asking by remember { mutableStateOf<Pair<NoticeOverlap.BuiltIn, List<LogicRule>>?>(null) }
    val store = remember { LogicStore(c.appContext) }
    val style = settings.workStyle
    val askNotifications = com.bamnat.app.ui.components.rememberNotificationAsk()
    val canPost = remember(refresh, tick) { Notifications.canPost(context) }
    val quiet = remember(refresh, tick) { offChannels(context, canPost) }
    val rules = remember(settings.logic, tick) { NoticeBoard.noticeRules(settings) }
    val ruleOn: (LogicRule) -> Boolean = { !store.isOff(it.key) }
    val overlaps = remember(settings, tick) {
        NoticeOverlap.now(Logic.decode(settings.logic), c.recipes.pieces.noticesOn(settings), ruleOn)
    }
    val own = NoticeBoard.shown(settings, style)
    val onOwn = own.filter { it.get(settings) }
    val offOwn = own.filter { !it.get(settings) }
    val onRules = rules.filter(ruleOn)
    val offRules = rules.filterNot(ruleOn)
    val blocked = onOwn.count { noticeBlocked(it, settings, canPost, quiet) != null }
    fun overlapOf(r: LogicRule) = overlaps.firstOrNull { it.rule.key == r.key }
    fun set(o: NoticeBoard.Own, on: Boolean) {
        c.settingsRepository.update { o.set(it, on) }
        o.after?.invoke(c)
        if (on && o.channel != null && !canPost) askNotifications()
    }
    fun turn(o: NoticeBoard.Own, on: Boolean) {
        if (o.onlyChannel) { com.bamnat.app.ui.components.SystemSettings.channel(context, o.channel!!); return }
        val b = NoticeOverlap.BuiltIn.of(o.key)
        val repeating = if (on && b != null) NoticeOverlap.repeating(Logic.decode(settings.logic), b, ruleOn) else emptyList()
        if (repeating.isNotEmpty()) asking = b!! to repeating else set(o, on)
    }
    Column(modifier.focusMark("알림 한눈에").fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (titled) Text("알림 한눈에", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull("켜 둔 ${onOwn.size + onRules.size}개", "더 켤 수 있는 ${offOwn.size + offRules.size}개",
                    "오지 않는 것 $blocked".takeIf { blocked > 0 }, "겹침 ${overlaps.size}".takeIf { overlaps.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    // 조건 6: a quiet notice or an overlap is to be looked at, not a danger.
                    color = if (overlaps.isNotEmpty() || blocked > 0) com.bamnat.app.ui.theme.Tone.Caution.color() else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [RVM-13] 총괄 #462 (넣기 창에 글자 단추가 넷: '펼치기'도 단추처럼 읽혀요): the whole row opens it; a chevron says which way.
            androidx.compose.material3.Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "알림 한눈에 접기" else "알림 한눈에 펼치기", tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!open) return@Column
        @Composable fun OwnRow(o: NoticeBoard.Own, on: Boolean, extra: String = "") {
            val why = noticeBlocked(o, settings, canPost, quiet)
            SwitchRow(o.label(style), o.does(style) + extra, if (o.onlyChannel) why == null else on,
                warning = why, fixLabel = if (!canPost && !o.onlyChannel) "허용" else "켜기",
                onFix = { if (!canPost && !o.onlyChannel) askNotifications() else o.channel?.let { com.bamnat.app.ui.components.SystemSettings.channel(context, it) }; tick++ }) { turn(o, it) }
        }
        Text("켜 둔 알림", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        if (onOwn.isEmpty() && onRules.isEmpty()) Text("없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onOwn.forEach { o ->
            val b = NoticeOverlap.BuiltIn.of(o.key)
            val with = overlaps.filter { it.builtIn != null && it.builtIn == b }
            OwnRow(o, true, with.joinToString("") { h ->
                if (b == NoticeOverlap.BuiltIn.TODAY) " · ‘${h.rule.name}’ 규칙과 겹쳐 하나로 합쳐 보여요" else " · ‘${h.rule.name}’ 규칙과 겹쳐요"
            })
        }
        onRules.forEach { r ->
            val h = overlapOf(r)
            SwitchRow("규칙 ‘${r.name}’", com.bamnat.core.formula.FormulaText.blanks(r.say()) + (h?.let { " · 겹침: " + (it.builtIn?.label ?: "‘${it.other?.name}’") } ?: ""), true,
                warning = h?.let { if (it.builtIn == NoticeOverlap.BuiltIn.TODAY) null else "같은 때 같은 것을 알려요. 하나를 끄면 깔끔해요" }) {
                NoticeBoard.setRule(c, r, it); tick++
            }
        }
        Text("더 켤 수 있는 알림", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        if (offOwn.isEmpty() && offRules.isEmpty()) Text("모두 켜 두었어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        offOwn.forEach { o -> OwnRow(o, false) }
        offRules.forEach { r -> SwitchRow("규칙 ‘${r.name}’", com.bamnat.core.formula.FormulaText.blanks(r.say()), false) { NoticeBoard.setRule(c, r, it); tick++ } }
        Text("새 알림 규칙은 마을 › 장터 › 도구 › 자동 규칙에서 더 고를 수 있어요. 규칙은 스튜디오 › 규칙·단추에서 고치거나 지워요",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    asking?.let { (b, repeating) ->
        TurnOnAsk(b, repeating, onDone = { offRules ->
            asking = null
            offRules?.let { off ->
                off.forEach { NoticeBoard.setRule(c, it, false) }
                set(NoticeBoard.OWN.first { it.key == b.key }, true)
                tick++
            }
        })
    }
}

/**
 * 1.39 [NTC-03] Turning an app notice on while a rule of mine says the same: 기존 설정을 바꿀까요? [onDone] gets the rules to switch
 * off (the notice is then turned on), an empty list to keep both, or null to leave it as it was.
 */
@Composable
fun TurnOnAsk(b: NoticeOverlap.BuiltIn, repeating: List<LogicRule>, onDone: (List<LogicRule>?) -> Unit) {
    val names = repeating.joinToString(", ") { "‘${it.name}’" }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("기존 설정을 바꿀까요?") },
        text = {
            Text("$names 규칙이 ${b.quoted}과 같은 말을 해요. 규칙을 끄면 알림이 하나만 와요." +
                if (b == NoticeOverlap.BuiltIn.TODAY) " 둘 다 두면 규칙의 나머지 말(이달 쓴 돈 등)은 잠금 화면 알림 한 곳에 합쳐 보여요" else " 둘 다 두면 알림이 두 번 와요")
        },
        confirmButton = { TextButton(onClick = { onDone(repeating) }) { Text("규칙 끄고 켜기") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onDone(null) }) { Text("닫기") }
                TextButton(onClick = { onDone(emptyList()) }) { Text(if (b == NoticeOverlap.BuiltIn.TODAY) "합쳐서 켜기" else "둘 다 켜기") }
            }
        },
    )
}

/**
 * 1.39 [NTC-03] 넣기 전 '기존 설정을 바꿀까요?': the 알림 of a 규칙·단추 work that repeat what is on here, with 기존 것 끄기 ([RecipePieces.Mode.REPLACE])
 * or 둘 다(하나로) 두기 ([RecipePieces.Mode.BOTH]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlapAsk(overlaps: List<NoticeOverlap.Hit>, mode: RecipePieces.Mode, onMode: (RecipePieces.Mode) -> Unit) {
    if (overlaps.isEmpty()) return
    val today = overlaps.any { it.builtIn == NoticeOverlap.BuiltIn.TODAY }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // [RVM-13] 조건 6: a question, not a danger (넣기 창의 바뀌는 것 요약 아래).
        Text("기존 설정을 바꿀까요?", style = MaterialTheme.typography.labelLarge)
        overlaps.forEach { Text("• " + it.say(), style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.app.ui.components.FilterChip(mode == RecipePieces.Mode.REPLACE, { onMode(RecipePieces.Mode.REPLACE) }, label = { Text("기존 것 끄고 이걸로") })
            com.bamnat.app.ui.components.FilterChip(mode == RecipePieces.Mode.BOTH, { onMode(RecipePieces.Mode.BOTH) },
                label = { Text(if (today) "둘 다 켜고 하나로 합치기" else "둘 다 두기") })
        }
        Text(when {
            mode == RecipePieces.Mode.REPLACE -> "겹치는 " + overlaps.joinToString(", ") { h -> h.builtIn?.quoted ?: "‘${h.other?.name}’ 규칙" } +
                "을 꺼요. 7일 안에 되돌리면 다시 켜져요"
            today -> "잠금 화면 ‘오늘 근무’ 알림 하나에 이 규칙의 나머지 말을 한 줄로 더해요. 알림은 하나만 와요"
            else -> "알림이 둘 다 와요. 나중에 설정 › 알림과 잠 › 알림 한눈에서 하나를 끌 수 있어요"
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
