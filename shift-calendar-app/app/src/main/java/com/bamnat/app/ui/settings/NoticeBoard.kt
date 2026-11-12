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

/**
 * 1.39 [NTC-04] 알림 한눈에 (사용자 2026-11-08 "줄이거나 추가를 할 수 있게 가지고 있거나 넣을 수 있는 것들 리스트들도 보여 줬으면"):
 * every notice this phone sends — the app's own and my 알림 rules — in two lists, 켜 둔 것 and 더 켤 수 있는 것, each with its switch.
 * Two that say the same thing are marked so one can go. Shown in 설정 › 알림과 잠 and in the 넣기 창 of a 규칙·단추 work.
 */
object NoticeBoard {
    /** One of the app's own notices: its setting, as 설정 › 알림과 잠 shows it, for the work types that see it there. */
    data class Own(val key: String, val label: (WorkStyle?) -> String, val does: String, val get: (AppSettings) -> Boolean,
                   val set: (AppSettings, Boolean) -> AppSettings, val shows: (WorkStyle?) -> Boolean)

    private fun planned(s: WorkStyle?) = s?.usesPayEngine != false
    private fun not(vararg no: WorkStyle) = { s: WorkStyle? -> s == null || s !in no }

    val OWN: List<Own> = listOf(
        Own("todayShiftNotice", { "잠금 화면 오늘 ${com.bamnat.app.ui.shifts.lockNoun(it)}" }, "오늘·내일을 잠금 화면에 늘", { it.todayShiftNotice },
            { s, on -> s.copy(todayShiftNotice = on) }, { true }),
        Own("briefing", { "하루 브리핑" }, "근무 전에 한 번 소리 없이", { it.briefing }, { s, on -> s.copy(briefing = on) }, ::planned),
        Own("afterAlarmCard", { "알람 뒤 오늘 카드" }, "알람을 끄면 오늘·이번 주·내일", { it.afterAlarmCard }, { s, on -> s.copy(afterAlarmCard = on) }, ::planned),
        Own("bedtimeNotice", { "잘 준비" }, "근무 전날 잘 시간 30분 전", { it.bedtimeNotice }, { s, on -> s.copy(bedtimeNotice = on) }, ::planned),
        Own("commuteNotice", { "퇴근길 안전" }, "야간이 끝날 때 한 번", { it.commuteNotice }, { s, on -> s.copy(commuteNotice = on) },
            { planned(it) && (it == null || it == WorkStyle.SHIFT) }),
        Own("leaveTimeNotice", { "퇴근 시각 알림" }, "근무가 끝나는 시각에", { it.leaveTimeNotice }, { s, on -> s.copy(leaveTimeNotice = on) },
            { it == WorkStyle.DAY }),
        Own("dayCloseNotice", { "하루 마감" }, "근무 끝 30분 뒤 오늘 쓴 돈", { it.dayCloseNotice }, { s, on -> s.copy(dayCloseNotice = on) },
            not(WorkStyle.INVEST, WorkStyle.BUSINESS)),
        Own("shiftPreviewNotice", { "근무 전 한 줄 예고" }, "근무 전날 저녁, 더 쓰는 분류", { it.shiftPreviewNotice }, { s, on -> s.copy(shiftPreviewNotice = on) },
            { it == WorkStyle.SHIFT || it == WorkStyle.DAY || it == WorkStyle.HOURLY }),
        Own("moneyNotices", { "나갈 돈·받을 돈" }, "고정비 3일 전·당일", { it.moneyNotices }, { s, on -> s.copy(moneyNotices = on) }, { true }),
        Own("plazaNews", { "밤낮 소식" }, "하루 한 번 묶어서", { it.plazaNews }, { s, on -> s.copy(plazaNews = on) }, { true }),
    )

    /** My rules that send an 알림. */
    fun noticeRules(s: AppSettings): List<LogicRule> = Logic.decode(s.logic).rules.filter { r -> r.acts.any { it.verb == LogicVerb.NOTIFY } }

    /** Turns my rule [r] on or off, as the studio's switch does. */
    fun setRule(c: AppContainer, r: LogicRule, on: Boolean) {
        LogicStore(c.appContext).setOff(r.key, !on)
        LogicRunner.rulesChanged(c)
    }
}

/**
 * 1.39 [NTC-04] The two lists, folded to one line until opened ("알림 한눈에 · 켜 둔 5개 · 더 켤 수 있는 4개 · 겹침 1").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoticeBoardCard(startOpen: Boolean = false, modifier: Modifier = Modifier,
                    /** 9차 PC 확인(하): inside a card already titled '알림 한눈에' the line says only the counts. */ titled: Boolean = true) {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    // Found by 설정 찾기: opened at once.
    val pointed = LocalSettingsFocus.current?.row == "알림 한눈에"
    var open by remember(pointed) { mutableStateOf(startOpen || pointed) }
    var asking by remember { mutableStateOf<Pair<NoticeOverlap.BuiltIn, List<LogicRule>>?>(null) }
    val store = remember { LogicStore(c.appContext) }
    val style = settings.workStyle
    val rules = remember(settings.logic, tick) { NoticeBoard.noticeRules(settings) }
    val ruleOn: (LogicRule) -> Boolean = { !store.isOff(it.key) }
    val overlaps = remember(settings, tick) {
        NoticeOverlap.now(Logic.decode(settings.logic), c.recipes.pieces.noticesOn(settings), ruleOn)
    }
    val own = NoticeBoard.OWN.filter { it.shows(style) || it.get(settings) }
    val onOwn = own.filter { it.get(settings) }
    val offOwn = own.filter { !it.get(settings) }
    val onRules = rules.filter(ruleOn)
    val offRules = rules.filterNot(ruleOn)
    fun overlapOf(r: LogicRule) = overlaps.firstOrNull { it.rule.key == r.key }
    fun turn(o: NoticeBoard.Own, on: Boolean) {
        val b = NoticeOverlap.BuiltIn.of(o.key)
        val repeating = if (on && b != null) NoticeOverlap.repeating(Logic.decode(settings.logic), b, ruleOn) else emptyList()
        if (repeating.isNotEmpty()) asking = b!! to repeating else c.settingsRepository.update { o.set(it, on) }
    }
    Column(modifier.focusMark("알림 한눈에").fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (titled) Text("알림 한눈에", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull("켜 둔 ${onOwn.size + onRules.size}개", "더 켤 수 있는 ${offOwn.size + offRules.size}개",
                    "겹침 ${overlaps.size}".takeIf { overlaps.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overlaps.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "접기" else "펼치기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (!open) return@Column
        Text("켜 둔 알림", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        if (onOwn.isEmpty() && onRules.isEmpty()) Text("없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onOwn.forEach { o ->
            val b = NoticeOverlap.BuiltIn.of(o.key)
            val with = overlaps.filter { it.builtIn != null && it.builtIn == b }
            SwitchRow(o.label(style), o.does + with.joinToString("") { h ->
                if (b == NoticeOverlap.BuiltIn.TODAY) " · ‘${h.rule.name}’ 규칙과 겹쳐 하나로 합쳐 보여요" else " · ‘${h.rule.name}’ 규칙과 겹쳐요"
            }, true) { turn(o, it) }
        }
        onRules.forEach { r ->
            val h = overlapOf(r)
            SwitchRow("규칙 ‘${r.name}’", r.say() + (h?.let { " · 겹침: " + (it.builtIn?.label ?: "‘${it.other?.name}’") } ?: ""), true,
                warning = h?.let { if (it.builtIn == NoticeOverlap.BuiltIn.TODAY) null else "같은 때 같은 것을 알려요. 하나를 끄면 깔끔해요" }) {
                NoticeBoard.setRule(c, r, it); tick++
            }
        }
        Text("더 켤 수 있는 알림", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        if (offOwn.isEmpty() && offRules.isEmpty()) Text("모두 켜 두었어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        offOwn.forEach { o -> SwitchRow(o.label(style), o.does, false) { turn(o, it) } }
        offRules.forEach { r -> SwitchRow("규칙 ‘${r.name}’", r.say(), false) { NoticeBoard.setRule(c, r, it); tick++ } }
        Text("새 알림 규칙은 마을 › 장터 › 도구 › 자동 규칙에서 더 고를 수 있어요. 규칙은 스튜디오 › 규칙·단추에서 고치거나 지워요",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    asking?.let { (b, repeating) ->
        TurnOnAsk(b, repeating, onDone = { offRules ->
            asking = null
            offRules?.let { off ->
                off.forEach { NoticeBoard.setRule(c, it, false) }
                c.settingsRepository.update { s -> NoticeBoard.OWN.first { it.key == b.key }.set(s, true) }
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
            Text("$names 규칙이 ‘${b.label}’ 알림과 같은 말을 해요. 규칙을 끄면 알림이 하나만 와요." +
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
            mode == RecipePieces.Mode.REPLACE -> "겹치는 " + overlaps.joinToString(", ") { h -> h.builtIn?.let { "‘${it.label}’ 알림" } ?: "‘${h.other?.name}’ 규칙" } +
                "을 꺼요. 7일 안에 되돌리면 다시 켜져요"
            today -> "잠금 화면 ‘오늘 근무’ 알림 하나에 이 규칙의 나머지 말을 한 줄로 더해요. 알림은 하나만 와요"
            else -> "알림이 둘 다 와요. 나중에 설정 › 알림과 잠 › 알림 한눈에서 하나를 끌 수 있어요"
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
