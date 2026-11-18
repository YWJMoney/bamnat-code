package com.bamnat.app.ui.calendar

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareContent
import com.bamnat.app.share.ShareExpiryReminder
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.app.share.ShareLinkKey
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.share.feedUrl
import com.bamnat.app.share.createLeaving
import com.bamnat.app.share.updateLeaving
import com.bamnat.app.share.ShareOptions
import com.bamnat.app.share.SharedPay
import com.bamnat.app.ui.Fmt
import com.bamnat.core.work.MoneyShare
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.bamnat.core.time.Today

/**
 * [MKT-09] Picture or link. A link shows about three months from the month on screen, read only,
 * and expires by itself; the phone keeps one link and can refresh it with the latest schedule or cut it.
 * [SHR-01] The sharer picks what goes with the shifts (leave kinds, extra work, memos, pay), all off
 * at first, and sees what leaves the phone before the link is made (privacy policy 1.6).
 * [SHR-02] A received link can be opened here too, to keep it beside the user's own calendar.
 * [SHR-11] The money part follows how the user works: 급여 for employees, 일감 수입 and the day's jobs
 * for field work, 매출 for a shop. It can be ticked whenever there is a figure to send, with or
 * without a pay calculation, and the sheet shows the figures before they leave the phone.
 * [SHR-12] A link follows the calendar by itself ([com.bamnat.app.share.ShareSync]) unless the sharer
 * turns that off, keeps its end date when it does, and can be kept open longer from here or the notice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    onImage: () -> Unit,
    onDismiss: () -> Unit,
    client: ShareApi = LocalContext.current.appContainer.shareApi,
    /** [MCAL-03] The calendar on screen; each of the user's calendars has a link of its own. */
    calendar: com.bamnat.app.data.PersonalCalendar? = null,
    /** [PROV-04] Which of the calendar's links the sheet opens on, from 1. */
    startSlot: Int = 1,
    preview: ShareLinkState? = null,
    /** [SHR-02] Opens the sheet that takes a received link; null hides that part. */
    onOpenReceived: (() -> Unit)? = null,
    /** 1.36 C19: open on every choice at once (screenshots of the full sheet). */
    detailedFirst: Boolean = false,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    // [MCAL-03][MCAL-06] An added calendar shares as its own kind of work, with its own pay, jobs or sales.
    val calendarId = calendar?.id ?: com.bamnat.app.data.PersonalCalendar.MAIN
    val added = calendarId != com.bamnat.app.data.PersonalCalendar.MAIN
    // [PROV-04] 프로 keeps up to five links per calendar, each to whom and for as long as the sharer likes.
    val gate by container.featureGate.state.collectAsState()
    val moreLinks = gate.allows(com.bamnat.app.tier.Feature.MORE_LINKS)
    val feedOpen = gate.allows(com.bamnat.app.tier.Feature.ICS_FEED)
    val links = ShareLinkKey.all(calendarId).map { container.shareLinksOf(it).link.collectAsState().value }
    var slot by remember { mutableIntStateOf(startSlot.coerceIn(1, ShareLinkKey.MAX)) }
    val store = remember(slot) { container.shareLinksOf(ShareLinkKey.of(calendarId, slot)) }
    val stored by store.link.collectAsState()
    val unsent by store.unsent.collectAsState()
    val link = preview ?: stored
    // A second link starts with the name the first one went out with. [RVG-30] 검토 PC-97: with no link yet, the name typed before
    // (a try that failed, or the sheet closed), on this link or the calendar's first.
    var title by remember(store) {
        mutableStateOf(link?.title?.takeIf { it.isNotBlank() } ?: store.typedName
            ?: container.shareLinksOf(ShareLinkKey.of(calendarId, 1)).typedName ?: links.firstNotNullOfOrNull { it }?.title.orEmpty())
    }
    fun typed(name: String) { if (name.length <= 30) { title = name; store.typedName = name } }
    var ttl by remember(store) { mutableIntStateOf(link?.ttlDays?.let { ShareLinkPayload.allowedTtl(it, moreLinks) } ?: 30) }
    // 1.36 사용자 결정(공유 링크 기간): with 프로 a link stays open while 프로 lasts, so there is no period to pick.
    val keeps = gate.keepsLinks
    if (keeps) ttl = ShareLinkPayload.PRO_KEEP_DAYS
    var auto by remember(store) { mutableStateOf(store.auto) }
    var saved by remember(store) { mutableStateOf(store.options()) }
    var options by remember(store) { mutableStateOf(saved) }
    var feed by remember(store) { mutableStateOf(store.feed) }
    var busy by remember { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    // [RVG-24] What the server said, for the question only (the line on screen is in people's words).
    var errorCode by remember(store) { mutableStateOf<String?>(null) }
    // [RVG-27] 검토 SN-13: what went out without a part the server would not take, said under the button (not an error).
    var leftOut by remember(store) { mutableStateOf<String?>(null) }
    // [PROV-04] Without 프로 a second link is kept until its end date but not kept open longer.
    val extraLocked = slot > ShareLinkKey.FREE && !moreLinks
    val profile by container.calendars.pay(calendarId).profile.collectAsState()
    val settings by remember(calendarId) { container.scopeOf(calendarId).settings }.collectAsState(container.scopeOf(calendarId).currentSettings())
    val style = settings.workStyle
    val kind = MoneyShare.of(style)
    // Employees' parts: leave kinds and extra work mean nothing to a shop or a freelancer's calendar.
    val employee = style?.usesPayEngine != false
    val field = kind == MoneyShare.JOBS
    // 2.0.0 [RVH-20] 검토 CAL-25: the days sent, in the calendar's words, said the same in '보여 줄 것' and '보내는 것'.
    val daysPart = if (employee) "근무" else if (field) "일한 날" else com.bamnat.core.work.scheduleWord(style)
    // [SHR-11] The figures a link would carry, worked out here so the row can show them before anything is sent.
    val money by produceState<List<SharedPay>?>(null, kind, month, snapshot, profile) {
        value = orElse(emptyList()) { ShareContent.money(container, snapshot, month.atDay(1), kind, calendarId) }
    }
    val hasPay = money?.isNotEmpty() == true
    // [SHR-20][J133-71] 1.33: the 동료 way's words and setup only for work with a company; the setup in the link from 3.7's date.
    val companyWork = remember(calendarId) { container.companyFits(calendarId, needsServer = false) }
    val setupOpen = remember(calendarId) { ShareContent.setupInLink(container, calendarId) }
    // 1.37 [SHR-31][SHR-32] 내 테마·근무 설정 in the link from 처리방침 3.11's date; before, the rows say the date once it is set.
    val lookOpen = remember { ShareContent.lookInLink(container) }
    // 1.38 [SLEEP-11] 지금 자는 중, for the main calendar's link while `sleep.share` is on.
    val sleepOpen = remember(calendarId) { calendarId == com.bamnat.app.data.PersonalCalendar.MAIN && ShareContent.sleepInLink(container) }
    // 1.39 G5-N4 계획한 잠 시간, for the main calendar's link from 처리방침 3.12's date (`sleep.plan_share`); off until ticked.
    val planOpen = remember(calendarId) { calendarId == com.bamnat.app.data.PersonalCalendar.MAIN && ShareContent.sleepPlanInLink(container) }
    val kitFits = remember(style) { com.bamnat.core.recipe.ShareKit.kitFits(style) }
    val lookFrom = remember {
        // 1.38: 3.11's date is not set until 1.38's release (a far placeholder), so no date is said until the owner sets it.
        container.remoteConfig.config.value.calendar.of(com.bamnat.core.flags.FlagCatalog.SHARE_LOOK_POLICY)?.effective
            ?.takeIf { it < com.bamnat.core.time.Today.date().plusYears(1) }?.let { "${it.monthValue}월 ${it.dayOfMonth}일" }
    }
    val setupFrom = remember {
        container.remoteConfig.config.value.calendar.of(com.bamnat.core.ops.PolicyConsent.VERSION)?.effective?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "정식 출시일"
    }

    fun run(action: suspend () -> Unit) {
        busy = true; error = null; leftOut = null
        scope.launch {
            try { action() } catch (e: Exception) { error = messageFor(e, options); errorCode = com.bamnat.app.share.failureCode(e) } finally { busy = false }
        }
    }
    val chosen = {
        options.copy(
            pay = options.pay && hasPay, jobs = options.jobs && field,
            leaveKinds = options.leaveKinds && employee, extras = options.extras && employee,
            setup = options.setup && setupOpen && employee && companyWork,
            look = options.look && lookOpen, kit = options.kit && lookOpen && kitFits,
            sleepNow = options.sleepNow && sleepOpen,
            sleepPlan = options.sleepPlan && planOpen,
        )
    }
    // [J133-30] 1.33 (실사용 테스트 "공유 링크가 오늘이 아니라 보던 달부터 시작해요"): a link starts this month, unless a month
    // further ahead is on screen.
    val linkMonth = YearMonth.from(Today.date()).let { now -> if (month.isAfter(now.plusMonths(1))) month else now }
    suspend fun payload(ttlDays: Int? = ttl) = ShareContent.payload(container, snapshot, linkMonth.atDay(1), title, ttlDays, chosen(), calendarId, feed)
    /** [SHR-12] Keeps what was sent, so the automatic update sends only what changes after it. */
    fun keep(made: ShareLinkState, sent: org.json.JSONObject) {
        store.saveOptions(chosen()); saved = chosen()
        store.save(made.copy(from = linkMonth), ShareLinkPayload.fingerprint(sent))
        store.sendResult(Today.instant())
    }
    // [SHR-11] A calendar with no shifts in these months (a shop, field work, hours not yet written) has
    // nothing to show until something is ticked; say so instead of letting the server refuse it.
    val empty by produceState(false, chosen(), month, snapshot, money) {
        value = orElse(false) { !ShareLinkPayload.hasContent(payload()) }
    }

    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Scrolls so a folded cover screen with a large font still reaches the buttons.
        Column(
            // [J133-79] 빌드 5: with the keyboard up a drag typed letters into the name (글라이드), and the keyboard hid
            // 링크 만들어 보내기. A drag now puts the keyboard away first, and the sheet keeps clear of it.
            Modifier.nestedScroll(com.bamnat.app.ui.components.rememberHideKeyboardOnDrag()).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(com.bamnat.core.work.shareTitle(style), style = MaterialTheme.typography.titleLarge) // [J133-77]
            if (added && calendar != null) {
                // [MCAL-03] Which calendar this link is for, and that the others keep their own.
                Text("${calendar.name} 달력만 보내요. 다른 달력은 각자 링크를 따로 만들어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 1.36 PC C19 (가족에게 이번 달만 보내고 싶은데 이미지·PDF·링크·받는 사람·보여 줄 것·이름·기간… 끝까지 내려야 '링크 만들어
            // 보내기'): with no link yet, the two things most people open this for, big, and the rest behind '자세히 정하기'.
            var detailed by androidx.compose.runtime.saveable.rememberSaveable(slot) { mutableStateOf(detailedFirst || link != null || preview != null || slot > 1) }
            // [RVG-35] What '링크로 보내기' here sends: the 가족 way without memos or money (its hint names the link by it).
            val quickSends = ShareOptions.forAudience(com.bamnat.app.share.ShareAudience.FAMILY, employee, field).copy(memos = false, pay = false)
            if (!detailed) {
                // PC 확인 20ab0254 (링크를 만들 때 이름을 안 물어 '이름 없는 근무표'가 돼요): the name goes with the quick link too.
                val quickKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                OutlinedTextField(value = title, onValueChange = ::typed, singleLine = true,
                    label = { Text("내 이름 (선택)") }, placeholder = { Text("예: 민지") },
                    // 2.0.0 [RVG-33] 검토 HQ-55: left empty, the link shows as '받은 근무표' ([RVG-32]), not as the example name's.
                    // [RVG-35] 검토 HQ-64: in the word the receiving app and the web page will use, from what the button below
                    // sends ([quickSends], [com.bamnat.core.work.linkWord]: 일감 → 일정; no money goes this way, so a shop's link is
                    // its 근무표 as the receiver sees it); 로/으로 by the word (일정으로).
                    supportingText = { Text(com.bamnat.core.work.linkWord(quickSends.shows, kind).let { w ->
                        val said = title.trim().let { n -> if (n.isEmpty()) "받은 $w" else "${n}의 $w" }
                        (if (title.isBlank()) "적지 않으면 " else "") + "받는 분 화면에 ‘$said’${com.bamnat.app.ui.Fmt.ro(w)} 보여요"
                    }) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { quickKeyboard?.hide() }))
                Button(onClick = { onImage(); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${month.monthValue}월 사진으로 보내기")
                }
                val quickPaused = container.flags.paused(com.bamnat.core.ops.ServerSwitch.SHARE_CREATE)
                OutlinedButton(enabled = !busy && !quickPaused, onClick = {
                    options = quickSends
                    run {
                        val sent = payload()
                        val (made, left) = client.createLeaving(sent, store)
                        keep(made, sent)
                        leftOut = left
                        container.telemetry.event("share_link_create", "quick_family")
                        com.bamnat.app.data.GuideStore.get(container.appContext).used("share.link") // 1.40 [DISC-03]
                        send(context, made, style)
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Link, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("링크로 보내기")
                    }
                }
                // [RVG-24][RVG-27] 검토 PC-92·SN-13: what came of the press, right under the button it answers. At the bottom of the sheet
                // it was under the fold at 200% (조건 7: 저장·실패는 말로, where it is seen).
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                leftOut?.let { LeftOutLine(it) }
                // 2.0.0 [RVH-37] #446 품질 표 조건 4 (공유 시트 안내가 100%에서 4줄, 200%에서 8줄): one line with ⓘ for the rest. The line keeps
                // what a person must know before a link is made (anyone with the address sees it, first, so a line cut at 200% keeps it;
                // then the months and how long), so it never goes down to ⓘ alone (처리방침 1.6 1·5항 안내는 ⓘ 안에 그대로).
                val word = com.bamnat.core.work.scheduleWord(style)
                val open = if (keeps) "프로를 쓰는 동안 열려요" else "${ShareLinkPayload.ttlLabel(ttl)} 열려요"
                com.bamnat.app.ui.components.Explain(
                    key = "share.link.note",
                    text = "링크는 ${linkMonth.monthValue}월부터 3개월 ${word}만 담아요(메모·${shareLeftOut(style)} 빼고). $open." + "\n" + shareServerNotice(style),
                    summary = "받은 누구나 봐요 · 3개월 $word · $open",
                    iconOnly = false,
                )
                if (quickPaused) Text(com.bamnat.core.ops.ServerSwitch.pausedText(com.bamnat.core.ops.ServerSwitch.SHARE_CREATE),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { detailed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("자세히 정하기 · PDF, 받는 사람, 보여 줄 것, 기간")
                }
            }
            if (detailed) {
            OutlinedButton(onClick = { onImage(); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("이 달 이미지로 보내기")
            }
            // [PROV-05] 근무표 PDF (프로): the month on screen or its year, on A4 to print or keep.
            val pdfOpen = container.featureGate.state.collectAsState().value.allows(com.bamnat.app.tier.Feature.ROSTER_PDF)
            fun rosterPdf(year: Boolean) {
                if (!pdfOpen && !com.bamnat.app.ui.pro.tryFeature(com.bamnat.app.tier.Feature.ROSTER_PDF)) { com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.ROSTER_PDF); return }
                val name = when {
                    title.isNotBlank() -> "${title.trim()}의 ${com.bamnat.core.work.scheduleWord(style)}"
                    added && calendar != null -> "${calendar.name} ${com.bamnat.core.work.scheduleWord(style)}"
                    else -> com.bamnat.core.work.scheduleWord(style)
                }
                scope.launch {
                    runCatching {
                        val today = Today.date()
                        val doc = if (year) com.bamnat.core.report.RosterReport.year(month.year, snapshot.resolver::resolve, name, today)
                        else com.bamnat.core.report.RosterReport.month(month, snapshot.resolver::resolve, name, today)
                        com.bamnat.app.report.ReportShare.pdf(context, doc,
                            if (year) com.bamnat.core.report.RosterReport.fileName(month.year) else com.bamnat.core.report.RosterReport.fileName(month))
                        container.telemetry.event("roster_pdf", if (year) "year" else "month")
                    }.onFailure { error = "PDF를 만들지 못했어요" }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { rosterPdf(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${month.monthValue}월 PDF")
                }
                OutlinedButton(onClick = { rosterPdf(true) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${month.year}년 PDF")
                }
                com.bamnat.app.ui.money.ProPill(locked = !pdfOpen) // [TIER-06]
            }
            // 1.36 PC Y14: how and until when 인쇄 is open stays under the buttons, not only in a toast that goes.
            com.bamnat.app.ui.pro.TrialNote(com.bamnat.app.tier.Feature.ROSTER_PDF, container.featureGate.state.collectAsState().value)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("링크로 보내기", style = MaterialTheme.typography.titleMedium)
            }
            // 빌드 10 (시계를 옮긴 뒤 '12월부터'인데 받은 건 11/2~1/29): a link already made says the months it carries.
            Text(
                "받는 사람은 ${((stored?.from) ?: linkMonth).monthValue}월부터 3개월 ${com.bamnat.core.work.scheduleWord(style).let { it + com.bamnat.app.ui.Fmt.obj(it) }} 브라우저로 봐요. 밤낮 앱이 있으면 자기 달력 옆에 두고 바꿔 가며 보고, 같이 쉬는 날도 바로 알아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // [SHR-20] 1.33 (계획 690): who it is for, first. Each way sets what goes at once; the boxes below still change it.
            Text("누구에게 보내요?", style = MaterialTheme.typography.titleSmall)
            AudienceRows(options.audience, companyWork, setupOpen, style) { a ->
                // 1.37: 내 테마·근무 설정 are the sharer's own picks, whoever the link is for.
                options = ShareOptions.forAudience(a, employee, field).copy(memos = false, pay = false, look = options.look, kit = options.kit)
            }
            // [PROV-04] The calendar's links side by side once there is one, with a new one for 프로.
            if (preview == null && links.any { it != null }) {
                LinkChips(links, slot, moreLinks, onPick = { slot = it }, onNew = {
                    if (moreLinks) links.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { slot = it + 1 }
                    else com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.MORE_LINKS)
                })
            }
            // A link already made comes first: sending it again is what most people open this for.
            val made = link
            if (made != null) {
                // [SHR-12] Closing soon is said in the accent colour, with the way to keep it open below.
                val soon = ShareExpiryReminder.closingSoon(made)
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (soon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text("만든 링크: ${made.url}\n" + (if (keeps && auto) "프로를 쓰는 동안 계속 열려요."
                        else "${expiryText(made)}까지 열려요." + if (soon) " 곧 닫혀요. 아래 ‘기간 늘리기’로 더 열어 둘 수 있어요." else ""),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
                Button(enabled = !busy, onClick = { send(context, made, style) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("이 링크 다시 보내기") }
                // [SHR-20] Before 3.7, or when the box is off: the company setup as a file the sharer passes on, through no server.
                if (options.audience == com.bamnat.app.share.ShareAudience.COWORKER && employee && companyWork && !(setupOpen && saved.setup)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch { sendSetupFile(context, container, calendarId) { error = it } }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("회사 설정 파일도 보내기") }
                }
            }

            Text("보여 줄 것", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // [J133-87] 1.33 빌드 6: a shop's first row is its 영업일, a field worker's its 일정 days, in their words.
                    PartRow(daysPart,
                        if (employee) "이름·색·시간, 늘 들어가요" else "적은 날만, 이름·색·시간", checked = true, enabled = false) {}
                    if (field) {
                        PartRow("일정", "날마다 일감 이름과 시간, ${ShareLinkPayload.JOBS_PER_DAY}개까지. 고객·장소·금액은 빼요", options.jobs) { options = options.copy(jobs = it) }
                    }
                    if (employee) {
                        PartRow("휴가 종류", "끄면 연차·반차 말고는 '휴가'로만 보여요", options.leaveKinds) { options = options.copy(leaveKinds = it) }
                        PartRow("연장·휴무일 근무", "이름과 시간, 날마다 ${ShareLinkPayload.EXTRAS_PER_DAY}개까지. 끄면 대근만 근무로 보여요", options.extras) { options = options.copy(extras = it) }
                    }
                    PartRow("메모", "날짜마다 첫 줄, ${ShareLinkPayload.MEMO_CHARS}자까지", options.memos) { options = options.copy(memos = it) }
                    // 1.36 PC C20 (켜면 금액이 그대로 나감): turning the money on asks once, saying the receiver sees the amounts.
                    var askMoney by remember { mutableStateOf(false) }
                    PartRow(kind.label, moneyHint(kind, money), options.pay && hasPay, enabled = hasPay) { on ->
                        if (on) askMoney = true else options = options.copy(pay = false)
                    }
                    if (askMoney) com.bamnat.app.ui.components.AlertDialog(
                        onDismissRequest = { askMoney = false },
                        title = { Text("${kind.label} 금액도 보낼까요?") },
                        text = { Text("받는 사람에게 ${kind.label} 금액이 그대로 보여요. 링크를 가진 사람은 누구나 볼 수 있어요.") },
                        confirmButton = { TextButton(onClick = { askMoney = false; options = options.copy(pay = true) }) { Text("금액도 보내기") } },
                        dismissButton = { TextButton(onClick = { askMoney = false }) { Text("빼고 보내기") } },
                    )
                    // [SHR-20] 1.33 (계획 691·694): a coworker's link with the company setup, from 처리방침 3.7's date.
                    if (options.audience == com.bamnat.app.share.ShareAudience.COWORKER && employee && companyWork) {
                        PartRow("회사 설정",
                            if (setupOpen) "근무 주기·근무 시간·조·잔업 종류·급여 규칙·수당 이름. 기본급·입사일·연차·금액은 빼요"
                            else "${setupFrom}부터 링크에 함께 담겨요. 지금은 링크를 만든 뒤 ‘회사 설정 파일도 보내기’로 보내 주세요",
                            options.setup && setupOpen, enabled = setupOpen) { options = options.copy(setup = it) }
                    }
                    // 1.37 [SHR-31][SHR-32] 묶음 E: the look and the 근무 설정, for the receiver to try on and pick from in the app.
                    if (lookOpen || lookFrom != null) {
                        // 2.0.0 [RVG-29] 검토 SN-14: a theme too big for a link is said before it is ticked (it used to go without it).
                        val lookBig by produceState(false, lookOpen) { value = lookOpen && ShareContent.lookTooBig(container) }
                        PartRow("내 테마",
                            when {
                                !lookOpen -> "${lookFrom}부터 링크에 함께 담을 수 있어요"
                                lookBig -> "이 테마는 커서 링크에 못 실어요. 장터에 올리면 장터 링크로 보낼 수 있어요"
                                else -> "달력·메모·가계부 모양. 사진은 빼요. 받는 분이 앱에서 입어 보고 골라 입어요"
                            },
                            options.look && lookOpen && !lookBig, enabled = lookOpen && !lookBig) { options = options.copy(look = it) }
                        if (kitFits) PartRow("근무 설정",
                            if (lookOpen) "근무 이름·색, 근무 전 알람 시각" + (if (!added && style in com.bamnat.core.recipe.PieceKind.COMPANY.styles && !chosen().sendsSetup) ", 근무 주기" else "") +
                                ". 급여 규칙·기본급·금액은 빼요. 받는 분이 골라 넣어요"
                            else "${lookFrom}부터 링크에 함께 담을 수 있어요",
                            options.kit && lookOpen, enabled = lookOpen) { options = options.copy(kit = it) }
                    }
                    if (sleepOpen) PartRow("지금 자는 중",
                        "자는 동안만 ‘몇 시부터 자는 중 · 몇 시에 일어나요’ 한 줄. 잠 기록·소리는 보내지 않아요. 받는 분 앱에서만 보여요",
                        options.sleepNow) { options = options.copy(sleepNow = it) }
                    if (planOpen) PartRow("계획한 잠 시간",
                        "근무에 맞춰 앱이 계획한 잘 시간(예: 오늘 09:00~16:00) 2주치. 실제로 잔 기록·소리는 보내지 않아요. 받는 분 앱에서만 보여요",
                        options.sleepPlan) { options = options.copy(sleepPlan = it) }
                }
            }
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            OutlinedTextField(value = title, onValueChange = ::typed, singleLine = true,
                label = { Text("이름 (선택, 예: 민지)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { keyboard?.hide() }))
            // [SHR-12] With a link made, the period is what 기간 늘리기 adds from today.
            // 1.36: 프로 keeps links open by itself; receivers see them free either way.
            if (keeps) Text("프로를 쓰는 동안 링크가 닫히지 않아요. 프로가 끝나면 그날부터 90일까지 열리고, 그 뒤로는 기간을 직접 늘려요. " +
                "받는 사람은 프로 없이도 무료로 봐요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 1.36 [PSH-01] 프로 나눔 1인: a buyer may also give 프로 itself to one person.
            if (gate.boughtPro) TextButton(onClick = { com.bamnat.app.ui.pro.openPro() }) { Text("한 사람에게 프로 나눠 주기") }
            else Text(if (link == null) "열어 둘 기간" else "기간 늘리기", style = MaterialTheme.typography.labelLarge)
            if (!keeps) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                ShareLinkPayload.TTL_DAYS.forEach { d ->
                    // [PROV-04] 180일 and 1년 are 프로's.
                    val locked = d in ShareLinkPayload.PRO_TTL_DAYS && !moreLinks
                    FilterChip(selected = ttl == d, onClick = { if (locked) com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.MORE_LINKS) else ttl = d },
                        label = { Text(ShareLinkPayload.ttlLabel(d)) })
                }
                com.bamnat.app.ui.money.ProPill(Modifier.align(Alignment.CenterVertically), locked = !moreLinks) // [TIER-06]
            }
            // [SHR-12] On by default: whoever got the link sees the calendar as it is now.
            PartRow("고치면 링크도 바로 바꾸기", (if (added) "이 달력을" else "내 달력을") + " 고치면 몇 초 뒤 링크에도 들어가요. 받은 사람 앱은 다음에 열 때 새로 받아요", auto) {
                auto = it; store.auto = it
                if (it) scope.launch { container.shareSync.sync(store.calendarId) }
            }
            // [FEAT-15] The same days as a calendar feed, for Google, iPhone or Outlook calendars on a computer.
            FeedRow(
                on = feed && feedOpen, open = feedOpen, busy = busy, url = link?.takeIf { feed && feedOpen }?.feedUrl(),
                onChange = { on ->
                    if (!feedOpen) { com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.ICS_FEED); return@FeedRow }
                    feed = on; store.feed = on
                    // A link already made takes it at once; a new one goes out with it.
                    if (link != null && preview == null) run {
                        when (container.shareSync.resend(store.key)) {
                            com.bamnat.app.share.ShareSync.Result.Offline -> {
                                feed = !on; store.feed = !on
                                error = "인터넷 연결을 확인하고 다시 해 주세요"
                            }
                            else -> container.telemetry.event("share_feed", if (on) "on" else "off")
                        }
                    }
                },
                onCopy = { url -> copy(context, url) },
            )
            // [MKT-09][OPS-13][SHR-01] Said before the data leaves the phone, in the policy's words, with what was ticked.
            Text(
                // [FEAT-15] The feed also sends the phone's time zone name (처리방침 2.6, 1항).
                "보내는 것: " + (listOf(daysPart) + chosen().partNames(kind) + listOfNotNull("회사 설정(금액 없이)".takeIf { chosen().sendsSetup },
                    "내 테마(사진 빼고)".takeIf { chosen().look }, "근무 설정(급여 빼고)".takeIf { chosen().kit },
                    "휴대폰 시간대 이름".takeIf { feed && feedOpen }))
                    .joinToString(", ") + "\n" + shareServerNotice(style),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (empty) {
                Text(emptyHint(kind, field), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            val current = link
            // [SRV-04] The developer can pause new links for a while; links already made keep working.
            val createPaused = current == null && container.flags.paused(com.bamnat.core.ops.ServerSwitch.SHARE_CREATE)
            if (createPaused) {
                Text(com.bamnat.core.ops.ServerSwitch.pausedText(com.bamnat.core.ops.ServerSwitch.SHARE_CREATE),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (current == null) {
                Button(enabled = !busy && !empty && !createPaused, onClick = {
                    if (extraLocked) { com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.MORE_LINKS); return@Button }
                    run {
                        val sent = payload()
                        val (made, left) = client.createLeaving(sent, store)
                        keep(made, sent)
                        leftOut = left
                        container.telemetry.event("share_link_create", chosen().shows.joinToString(",").ifEmpty { "shifts" } + if (slot > 1) ",slot$slot" else "")
                        com.bamnat.app.data.GuideStore.get(container.appContext).used("share.link") // 1.40 [DISC-03]
                        send(context, made, style)
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("링크로 보내기")
                }
            } else {
                // [SHR-13] When the link last got the calendar, and a change still waiting for the internet.
                val refused by store.refused.collectAsState()
                SentLine(store.sentAt, unsent, busy, refused) { run { container.shareSync.resend(store.key) } }
                if (chosen() != saved || title.trim() != current.title.trim()) {
                    Text("고른 항목이나 이름이 바뀌었어요. ‘지금 ${com.bamnat.core.work.scheduleWordRo(style)} 고치기’를 누르면 링크에 들어가요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                // [SHR-12] Sent the way the automatic update sends it: the end date stays.
                if (!auto || chosen() != saved || title.trim() != current.title.trim()) {
                    OutlinedButton(enabled = !busy && !empty, onClick = {
                        run {
                            val sent = payload(null)
                            val (made, left) = client.updateLeaving(current, sent, store)
                            keep(made, sent)
                            leftOut = left
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("지금 ${com.bamnat.core.work.scheduleWordRo(style)} 고치기")
                    }
                }
                // 1.36: with 프로 and 고치면 바로 바꾸기 on the link is kept open by itself.
                if (!keeps || !auto) OutlinedButton(enabled = !busy && !empty, onClick = {
                    if (extraLocked) { com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.MORE_LINKS); return@OutlinedButton }
                    run {
                        val sent = payload(ttl)
                        val (made, left) = client.updateLeaving(current, sent, store)
                        keep(made, sent)
                        leftOut = left
                        container.telemetry.event("share_link_extend", "sheet")
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("오늘부터 ${ShareLinkPayload.ttlLabel(ttl)} 열어 두기")
                    if (extraLocked) { Spacer(Modifier.width(8.dp)); com.bamnat.app.ui.money.ProPill() }
                }
                // [J133-30] Cutting it takes the schedule away from everyone who has the link: asked once.
                var cutting by remember { mutableStateOf(false) }
                if (cutting) com.bamnat.app.ui.components.AlertDialog(
                    onDismissRequest = { cutting = false },
                    title = { Text("링크를 끊을까요?") },
                    text = { Text("받은 사람 앱과 브라우저에서도 이 ${com.bamnat.core.work.scheduleWord(style)}${if (style == com.bamnat.core.work.WorkStyle.FIELD || style == com.bamnat.core.work.WorkStyle.BUSINESS || style == com.bamnat.core.work.WorkStyle.INVEST) "이" else "가"} 바로 사라지고, 같은 링크는 다시 쓸 수 없어요.") },
                    confirmButton = { TextButton(onClick = { cutting = false; run { client.delete(current); store.clear() } }) {
                        Text("끊기", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { cutting = false }) { Text("취소") } },
                )
                TextButton(enabled = !busy, onClick = { cutting = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (slot > 1) "이 링크 끊기" else "링크 끊기", color = MaterialTheme.colorScheme.error)
                }
            }
            leftOut?.let { LeftOutLine(it) }
            error?.let { why ->
                Text(why, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                // [UX-05] An error that does not go away is one tap from a question, with what the sheet said.
                TextButton(onClick = {
                    onDismiss()
                    com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(
                        about = "근무표 공유", kind = com.bamnat.app.company.InquiryKind.BUG,
                        text = "‘$why’라고 나와요." + (errorCode?.let { " ($it)" } ?: "") + " "))
                }) { Text("계속 안 되면 문의하기") }
            }
            } // detailed
            if (onOpenReceived != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // [UX-03] The other way round: a coworker's schedule beside the user's own.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "받기", onClick = onOpenReceived)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // [RVH-20] A shop, a field worker or an investor takes someone's calendar, not a 동료's 근무표.
                        Text(if (employee) "동료 근무표도 받아 볼까요?" else "다른 사람 달력도 받아 볼까요?", style = MaterialTheme.typography.titleSmall)
                        Text("받은 링크를 넣으면 내 달력과 바꿔 가며 보고, 같이 쉬는 날도 보여요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("받기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** [SHR-20] 1.33 (계획 690): 같은 회사 동료 / 가족·연인 / 다른 회사 친구, one line each with what it sends. */
@Composable
private fun AudienceRows(picked: com.bamnat.app.share.ShareAudience?, companyWork: Boolean, setupOpen: Boolean,
                         style: com.bamnat.core.work.WorkStyle?, onPick: (com.bamnat.app.share.ShareAudience) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 4.dp).selectableGroup()) {
            com.bamnat.app.share.ShareAudience.entries.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = a == picked, role = Role.RadioButton, onClick = { onPick(a) })
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = a == picked, onClick = null, modifier = Modifier.padding(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label(companyWork), style = MaterialTheme.typography.bodyLarge)
                        Text(a.hint(companyWork, setupOpen, style), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** [SHR-20] The company setup as a file (금액 없이) through the share sheet, the way 설정 › 급여 › 회사 설정 보내기 sends it. */
private suspend fun sendSetupFile(context: Context, c: com.bamnat.app.AppContainer, calendar: String, onError: (String) -> Unit) {
    val name = ShareContent.companyName(c, calendar)
    val text = ShareContent.setupFile(c, calendar) ?: return onError("회사 설정 파일을 만들지 못했어요")
    runCatching { com.bamnat.app.data.CompanyShare.send(context, name, text) }.onFailure { onError("회사 설정 파일을 만들지 못했어요") }
}

/** [RVG-27] 검토 SN-13: the link went, without a part the server would not take; said in the 안내 카드's one shape (조건 3). */
@Composable
private fun LeftOutLine(words: String) {
    com.bamnat.app.ui.components.NudgeCard(words, tone = com.bamnat.app.ui.theme.Tone.Caution)
}

/** [SHR-13] 마지막으로 보낸 때, and 보내지 못한 변경 with a button that sends it now. */
@Composable
internal fun SentLine(sentAt: java.time.Instant?, unsent: Boolean, busy: Boolean, refused: Boolean = false, onSend: () -> Unit) {
    if (unsent) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // 2.0.0 [RVG-27] 검토 SN-13: turned down by the server is not 'waiting for the internet' (it never went by itself).
                Text(if (refused) "서버가 이 변경을 받지 않았어요. 다시 보내 보고, 계속 안 되면 문의해 주세요."
                    else "보내지 못한 변경이 있어요. 인터넷이 되면 저절로 보내요.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = onSend, modifier = Modifier.heightIn(min = 48.dp)) { Text("지금 보내기") }
            }
        }
    }
    sentAt?.let {
        Text("마지막으로 보낸 때 " + it.atZone(Today.zone()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm")),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [PROV-04] 링크 1…5 for the links made, and 새 링크 while one is free; 새 링크 is 프로's. */
@Composable
private fun LinkChips(links: List<ShareLinkState?>, slot: Int, open: Boolean, onPick: (Int) -> Unit, onNew: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            links.forEachIndexed { i, l ->
                val n = i + 1
                // An empty slot shows only while it is the one being made.
                if (l != null || n == slot) FilterChip(selected = n == slot, onClick = { onPick(n) }, label = { Text("링크 $n") })
            }
            val free = links.withIndex().any { (i, l) -> l == null && i + 1 != slot }
            if (free) FilterChip(selected = false, onClick = onNew, label = { Text("+ 새 링크") },
                trailingIcon = if (open) null else ({ com.bamnat.app.ui.money.ProPill(locked = true) })) // [TIER-06] 여러 링크의 '프로'는 기간 줄에 늘 보여요
        }
        Text(
            if (open) "보낼 사람마다 링크를 따로 만들면 보여 줄 것과 기간을 따로 정하고, 하나만 끊을 수도 있어요. 달력마다 ${ShareLinkKey.MAX}개까지예요."
            else "링크를 사람마다 따로 두는 건 프로에서 돼요(달력마다 ${ShareLinkKey.MAX}개까지).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * [FEAT-15] 캘린더 구독 주소 (프로): the link's days as an .ics feed. Google Calendar takes one only on a
 * computer (URL로 추가) and reads it again every few hours to a day, so the row says both.
 */
@Composable
private fun FeedRow(on: Boolean, open: Boolean, busy: Boolean, url: String?, onChange: (Boolean) -> Unit, onCopy: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = on, enabled = !busy, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("캘린더 구독 주소", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(6.dp)); com.bamnat.app.ui.money.ProPill(locked = !open) // [TIER-06]
                }
                Text("구글·아이폰·아웃룩 캘린더에 구독으로 넣으면 이 링크의 근무가 따라 바뀌어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = null, enabled = !busy)
        }
        if (url != null) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(url, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onCopy(url) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("구독 주소 복사") }
                    Text(FEED_GUIDE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (on) {
            Text("링크를 만들면 구독 주소가 여기 나와요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [FEAT-15] Where the address goes, as Google's help says (2026-10-22), and how soon a change shows. */
internal const val FEED_GUIDE =
    "구글 캘린더: 컴퓨터에서 calendar.google.com › 다른 캘린더 + › URL로 추가에 붙여 넣어요. 휴대폰 구글 캘린더 앱에서는 추가할 수 없어요. " +
        "바뀐 근무는 구글이 다시 읽을 때(몇 시간~하루) 보여요.\n" +
        "아이폰·맥: 설정 › 캘린더 › 계정 › 구독 캘린더 추가. 아웃룩: 캘린더 추가 › 웹에서 구독.\n" +
        "주소를 아는 사람은 누구나 볼 수 있어요. 링크를 끊으면 구독도 끊겨요."

private fun copy(context: Context, text: String) {
    runCatching {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("캘린더 구독 주소", text))
    }
    // Android 13 and later say it themselves.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, "구독 주소를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PartRow(label: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled || checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [SHR-01] The ticked parts in the words the sheet uses; [SHR-11] the money in the user's own kind. */
internal fun ShareOptions.partNames(kind: MoneyShare = MoneyShare.PAY): List<String> = listOfNotNull(
    "일정".takeIf { jobs },
    "휴가 종류".takeIf { leaveKinds },
    "연장·휴무일 근무".takeIf { extras },
    "메모".takeIf { memos },
    kind.label.takeIf { pay },
    // 1.39 PC c0ca3d9f: the sleep rows were sent but '보내는 것' said 근무 only.
    "지금 자는 중(잘 때만)".takeIf { sleepNow },
    "계획한 잠 시간".takeIf { sleepPlan },
)

/** A figure worked out for the sheet, or [fallback] when the data cannot be read; leaving the sheet still cancels it. */
private suspend fun <T> orElse(fallback: T, block: suspend () -> T): T = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    fallback
}

/** [SHR-11] What the money row would send, month by month, or where the figure comes from when there is none yet. */
internal fun moneyHint(kind: MoneyShare, money: List<SharedPay>?): String = when {
    money == null -> "금액을 확인하는 중이에요"
    money.isNotEmpty() -> money.joinToString(" · ") { "${it.month.monthValue}월 ${Fmt.won(it.amount)}" } + "\n" + when (kind) {
        MoneyShare.PAY -> "달마다 금액 하나만, 내역은 빼요"
        MoneyShare.JOBS -> "달마다 일감 금액 합계(3.3% 뗀 뒤)만, 고객·장소는 빼요"
        MoneyShare.SALES -> "달마다 매출 합계만, 경비와 날마다 금액은 빼요"
    }
    else -> when (kind) {
        MoneyShare.PAY -> "급여 계산을 설정하거나 가계부 탭에 받은 급여를 적으면 고를 수 있어요"
        MoneyShare.JOBS -> "이 기간 일감에 금액을 적으면 고를 수 있어요"
        MoneyShare.SALES -> "이 기간 매출을 적으면 고를 수 있어요. 달력에서 날짜를 눌러 적어요"
    }
}

/** [SHR-11] Why the link button waits: these months have nothing to show yet. */
internal fun emptyHint(kind: MoneyShare, field: Boolean): String = when {
    // 2.0.0 [RVH-20] 검토 낱말 검사: a field calendar has 일정, a shop 영업일, not 근무.
    field -> "이 기간에는 일정이 없어요. ‘일정’이나 ‘${kind.label}’을 고르면 링크를 만들 수 있어요."
    kind == MoneyShare.SALES -> "이 기간에는 영업일이 없어요. ‘${kind.label}’이나 ‘메모’를 고르면 링크를 만들 수 있어요."
    // 빌드 10 (알바: 일한 날을 아직 안 칠했는데 무엇을 할지 몰라요): what to do first, in the calendar's words.
    else -> "이 기간에는 보낼 근무가 없어요. 달력에서 일한 날을 먼저 칠하거나, 보여 줄 것을 골라 주세요."
}

/** What leaves the phone with a link, where it is kept, and who can open it. Matches privacy policy 1.6, sections 1 and 5. */
internal const val SHARE_SERVER_NOTICE =
    "링크를 만들면 적은 이름과 근무표, 고른 항목이 공유 서버(Cloudflare, 미국)에 저장되고, 주소를 받은 사람은 누구나 볼 수 있어요. 원하지 않으면 이미지로 보내 주세요."

/** 2.0.0 [RVH-20] 검토 화면 사진(가게 공유): [SHARE_SERVER_NOTICE] in the calendar's word for its days (영업일, 일정). */
internal fun shareServerNotice(style: com.bamnat.core.work.WorkStyle?): String =
    SHARE_SERVER_NOTICE.replace("근무표", com.bamnat.core.work.scheduleWord(style))

/** 2.0.0 [RVH-20] 총괄 사진 검토(가게 공유 '메모·급여 빼고'): what a link leaves out, in the calendar's own money word. */
internal fun shareLeftOut(style: com.bamnat.core.work.WorkStyle?): String = when (style) {
    com.bamnat.core.work.WorkStyle.INVEST -> "투자 기록"
    else -> com.bamnat.core.work.MoneyShare.of(style).label
}

private fun expiryText(s: ShareLinkState): String =
    s.expiresAt.atZone(Today.zone()).toLocalDate().format(DateTimeFormatter.ofPattern("M월 d일"))

private fun messageFor(e: Exception, options: ShareOptions): String = when (e.message) {
    "rate_limited" -> com.bamnat.app.company.RateLimits.text("링크 만들기", 3_600, "device")
    // 1.39 [CARE-09] 7차 PC: a title with a phone number etc. (1.39 server), in the line's own words.
    "contact" -> com.bamnat.app.share.linkProblem(e) ?: com.bamnat.core.plaza.TeamLinkText.contact(null)
    "not_found", "forbidden" -> "이 링크는 이미 지워졌어요. 링크 끊기를 누른 뒤 새로 만들어 주세요."
    // A server from before 1.10 takes only shifts and leave.
    // [SHR-11] A server from before 1.13 takes no 일정 and no link without shifts.
    "days" -> "이 기간에는 보낼 근무가 없어요. 보여 줄 것을 골라 주세요."
    "version", "shows" -> if (options.any) "지금은 근무와 휴가만 보낼 수 있어요. 고른 항목을 끄고 다시 해 주세요." else com.bamnat.app.share.netWords(e, LINK_NOT_MADE)
    // 2.0.0 [RVG-24] 검토 PC-92: no server code on screen ('(type_icon)'); it goes with '계속 안 되면 문의하기' instead.
    else -> if (e is java.io.IOException) com.bamnat.app.share.netWords(e, LINK_NOT_MADE) else "$LINK_NOT_MADE 다시 해 주세요."
}

private const val LINK_NOT_MADE = "링크를 만들지 못했어요."

private fun send(context: Context, s: ShareLinkState, style: com.bamnat.core.work.WorkStyle? = null) {
    val word = com.bamnat.core.work.scheduleWord(style)
    val text = (if (s.title.isNotBlank()) "${s.title}의 $word" else word) + "\n${s.url}"
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, "$word 링크 보내기"))
}
