package com.bamnat.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.setup.ChoiceCard
import com.bamnat.app.ui.setup.JobAsk
import com.bamnat.app.ui.setup.STYLE_CHOICES
import com.bamnat.app.ui.setup.StepHeader
import com.bamnat.core.work.WorkStyle

/**
 * [SETUP-15] The very first screen of a new phone: how the user works, before what the app turns on is
 * shown (점검 보고서 중-9: a weekday or part-time worker was told about coffee, naps and the way home after
 * nights first). The answer picks the next screen's notices and starts the first setup with it, so the
 * question is not asked twice. The same cards as the setup's first question.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FirstStyleScreen(onPick: (FirstStyle) -> Unit, onSkip: () -> Unit, textStep: Int? = null, onTextStep: (Int) -> Unit = {},
                     /** [SETUP-17] '나중에 할게요': an example calendar to look around first; the first write asks again. */
                     onLater: (() -> Unit)? = null,
                     /** 1.38 PC 11차: the app was opened from a shared 근무표 link on a new phone; say it waits for the calendar. */
                     linkWaiting: Boolean = false, linkTitle: String? = null,
                     /** 1.38 PC 12.1: the setup filled from the link (its kinds, times and days); null while it cannot be read. */
                     onLinkStart: (() -> Unit)? = null,
                     /** [RVG-15] '가족·동료 것이에요': the link kept beside as a 받은 달력, the user's own work asked below. */
                     onLinkTheirs: (() -> Unit)? = null,
                     /** [RVG-17] The link is still being read: the buttons wait and say so (they changed under the finger before). */
                     linkLoading: Boolean = false,
                     /** [RVG-17] Why the link could not be read, said in place of the line; null when it was (or is being) read. */
                     linkProblem: String? = null,
                     /** [RVG-15] The one filled button of the two ([com.bamnat.app.ui.setup.LinkStart.lean]). */
                     linkLean: com.bamnat.app.ui.setup.LinkStart.Owner = com.bamnat.app.ui.setup.LinkStart.Owner.THEIRS,
                     /** [RVG-15] The card's X: neither answer now; the calendar offers the link after the setup, as before 1.38. */
                     onLinkClose: (() -> Unit)? = null,
                     /** [RVG-15] The title of a link just kept as someone else's: said once over the question, where it went. */
                     theirsKept: String? = null,
                     /** [RVG-15] Someone else's, but the link could not be read: the calendar offers it as a 받은 달력 after the setup. */
                     theirsLater: Boolean = false,
                     /** [RVG-15] The X of the card that says where it went. */
                     onTheirsClose: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp).semantics { paneTitle = FIRST_QUESTION },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // [SETUP-17] 1.37 PC 270fc15e: the very first screen had no way to put it off; the file start sat at the bottom.
                // [RVG-15] 디자인 품질 조건 12: at 200% text a label goes to the next line whole ('나중에 / 할게요' before).
                if (onLater != null) androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("파일로 시작") }
                    TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text("나중에 할게요") }
                }
                // [FIT-03] U23: the user's own words first, the kinds of work under them.
                // U 1.32 [UX132-05] 글자 크기 first, so everything after is read at the size chosen (설정 › 화면과 글자에서 다시).
                if (textStep != null) {
                    Text("글자 크기", style = MaterialTheme.typography.titleSmall)
                    com.bamnat.app.ui.settings.TextSizePicker(textStep, onTextStep)
                }
                // [RVG-15] 검토 TYPE-04 (사용자 결정 2026-11-12 '두 단추로 묻기'): whose 근무표 it is, asked before anything is filled,
                // so a 가족·친구's 근무 never becomes the user's own calendar. One filled button ([linkLean]), one line, details under ⓘ.
                if (linkWaiting) LinkCard(
                    title = linkTitle?.let { "‘$it’ 근무표가 도착했어요" } ?: "받은 근무표가 있어요",
                    line = linkProblem ?: if (linkLoading) "근무표를 불러오는 중…" else "누구 근무표인지 알려 주세요.",
                    more = if (onLinkStart != null) "내 근무표예요: 근무 이름과 시간, 근무한 날을 그대로 가져와 한 번에 시작해요. 가족·동료 것이에요: 내 달력 옆 받은 달력에 두고 보기만 해요. 내 달력과 알람은 바뀌지 않아요."
                        else "내 근무표예요: 매달 받는 근무표로 시작하고, 첫 설정을 마치면 달력에서 이 근무표를 다시 열어요. 가족·동료 것이에요: 첫 설정 뒤 받은 달력으로 넣을지 물어요. 내 달력과 알람은 바뀌지 않아요.",
                    mood = com.bamnat.app.ui.components.KaniMood.Point, live = linkLoading || linkProblem != null,
                    onClose = onLinkClose, closeLabel = "받은 근무표 카드 닫기, 첫 설정 뒤에 다시 물어요",
                ) {
                    val enabled = !linkLoading
                    val mineLeads = linkLean == com.bamnat.app.ui.setup.LinkStart.Owner.MINE
                    LinkOwnerButton("내 근무표예요", lead = mineLeads, enabled = enabled) {
                        onLinkStart?.invoke() ?: onPick(FirstStyle(com.bamnat.core.work.WorkStyle.SHIFT, roster = true))
                    }
                    LinkOwnerButton("가족·동료 것이에요", lead = !mineLeads, enabled = enabled && onLinkTheirs != null) { onLinkTheirs?.invoke() }
                }
                // [RVG-15] Kept as someone else's: one sentence of where it went (디자인 품질 조건 3, answered → one line). The big
                // question right under it already asks for the user's own work; ⓘ says where to see the kept one after the setup.
                if (!linkWaiting && (theirsKept != null || theirsLater)) LinkCard(
                    title = if (theirsKept != null) "‘$theirsKept’ 근무표는 받은 달력에 넣었어요" else "받은 근무표는 첫 설정 뒤에 넣어요",
                    line = null,
                    more = if (theirsKept != null) "첫 설정을 마치면 달력 이름을 눌러 ‘$theirsKept’ 근무표로 바꿔 봐요. 내 달력과 알람은 그대로이고, 보낸 분이 고치면 받은 달력에만 보여요."
                        else "지금은 근무표를 읽지 못했어요. 첫 설정을 마치면 달력에서 받은 달력으로 넣을지 물어요.",
                    mood = com.bamnat.app.ui.components.KaniMood.Done, live = true,
                    onClose = onTheirsClose, closeLabel = "받은 달력 안내 닫기",
                )
                StepHeader(FIRST_QUESTION, "적거나 고른 일에 맞는 기능만 켜고, 다음 질문도 그 일에 맞춰 물어요. 적은 말은 이 휴대폰에만 있어요.")
                // [FLAG-12][FLAG-13] A hidden kind of work is not offered here (the owner's phone shows it marked).
                val shown = com.bamnat.app.ui.flags.typeChoices()
                JobAsk(onPick = { style, roster -> onPick(FirstStyle(style, roster)) }, shown = shown)
                STYLE_CHOICES.filter { shown.shows(it.style) }.forEach { c ->
                    ChoiceCard(title = c.title, subtitle = shown.mark(c.style, c.subtitle.substringBefore(" · ")), icon = c.icon, selected = false,
                        onClick = { onPick(FirstStyle(c.style, c.roster)) })
                }
                // [RVG-15] 디자인 품질 조건 2 (글자 단추 2개까지): with '파일로 시작' at the top, the same start is not offered again here.
                if (onLater == null) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("백업이나 회사 파일로 시작할게요") }
            }
        }
    }
}

/**
 * [RVG-15] The first screen's card about a received 근무표, in the shape of the setup's other cards ([SetupFill]'s fill card): 칸이,
 * the title and X on one row, the one line with ⓘ under it (디자인 품질 조건 3·4), the details folded, [actions] full width. The line
 * runs the card's whole width, so at 200% text it wraps in a few lines rather than a narrow column beside 칸이.
 */
@Composable
private fun LinkCard(title: String, line: String?, more: String, mood: com.bamnat.app.ui.components.KaniMood, live: Boolean,
                     onClose: (() -> Unit)?, closeLabel: String, actions: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null) {
    val openState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val open = openState.value
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = if (actions != null) 16.dp else if (line == null && !open) 4.dp else 8.dp)) {
            val info: @Composable () -> Unit = {
                androidx.compose.material3.IconButton(onClick = { openState.value = !open }) {
                    androidx.compose.material3.Icon(Icons.Outlined.Info, if (open) "자세한 설명 접기" else "자세한 설명 보기", tint = on)
                }
            }
            val liveIf = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (com.bamnat.app.ui.components.LocalShowKani.current) {
                    com.bamnat.app.ui.components.Kani(mood, night = com.bamnat.app.ui.components.isDarkSurface(), size = 40.dp,
                        modifier = Modifier.padding(end = 12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, color = on, modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    .semantics { heading() }.then(if (line == null) liveIf else Modifier))
                // An answered card is one row: 칸이, the sentence, ⓘ and X.
                if (line == null) info()
                if (onClose != null) androidx.compose.material3.IconButton(onClick = onClose) {
                    androidx.compose.material3.Icon(Icons.Filled.Close, closeLabel, tint = on)
                }
            }
            if (line != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = on, modifier = Modifier.weight(1f).then(liveIf))
                info()
            }
            if (open) Text(more, style = MaterialTheme.typography.bodySmall, color = on, modifier = Modifier.padding(end = 12.dp, bottom = 4.dp))
            if (actions != null) Column(Modifier.padding(top = 8.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** [RVG-15] One of the two answers to '누구 근무표인가요?': [lead] is the screen's one filled button, the other outlined. */
@Composable
private fun LinkOwnerButton(text: String, lead: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    if (lead) androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = m) { Text(text) }
    else androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, modifier = m,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))) { Text(text) }
}

/** [FIT-03] The first screen's question (1.25: was "무슨 일 하세요?"). */
const val FIRST_QUESTION = "어떤 일을 하고 계신가요?"

/** [SETUP-15] The answer to "무슨 일 하세요?", kept as a word across a turned phone or a process restart. */
data class FirstStyle(val style: WorkStyle, val roster: Boolean) {
    val key: String get() = style.name + if (roster) ROSTER else ""

    companion object {
        private const val ROSTER = "_ROSTER"
        /** [SETUP-17] '백업이나 회사 파일로 시작할게요': no kind of work, the file chooser opens at once. */
        const val FILE = "FILE"
        /** [SETUP-17] '나중에 할게요' on the first screen: the example calendars to look around. */
        const val BROWSE = "BROWSE"
        fun of(key: String?): FirstStyle? {
            val k = key ?: return null
            val style = WorkStyle.entries.firstOrNull { it.name == k.removeSuffix(ROSTER) } ?: return null
            return FirstStyle(style, k.endsWith(ROSTER) && style == WorkStyle.SHIFT)
        }
    }
}
