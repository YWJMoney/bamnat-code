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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
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
                     /** [RVG-15] The button that leads (filled), from the kind of link; null: both alike. */
                     linkLean: com.bamnat.app.ui.setup.LinkStart.Owner? = null,
                     /** [RVG-15] The title of a link just kept as someone else's: said once over the question, where it went. */
                     theirsKept: String? = null,
                     /** [RVG-15] Someone else's, but the link could not be read: the calendar offers it as a 받은 달력 after the setup. */
                     theirsLater: Boolean = false) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp).semantics { paneTitle = FIRST_QUESTION },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // [SETUP-17] 1.37 PC 270fc15e: the very first screen had no way to put it off; the file start sat at the bottom.
                if (onLater != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("파일로 시작") }
                    TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text("나중에 할게요") }
                }
                // [FIT-03] U23: the user's own words first, the kinds of work under them.
                // U 1.32 [UX132-05] 글자 크기 first, so everything after is read at the size chosen (설정 › 화면과 글자에서 다시).
                if (textStep != null) {
                    Text("글자 크기", style = MaterialTheme.typography.titleSmall)
                    com.bamnat.app.ui.settings.TextSizePicker(textStep, onTextStep)
                }
                if (linkWaiting) androidx.compose.material3.Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(linkTitle?.let { "‘$it’ 근무표를 받았어요" } ?: "받은 근무표 링크가 있어요",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        // [RVG-15] 검토 TYPE-04: asked before anything is filled, so a 가족·친구's 근무 never becomes the user's own calendar.
                        Text("누구 근무표인가요? " + when {
                            linkLoading -> ""
                            onLinkStart != null -> "내 것이면 근무 이름과 시간, 근무한 날을 그대로 가져와 바로 시작해요. "
                            else -> "내 것이면 매달 받는 근무표로 시작하고, 첫 설정을 마친 뒤 달력에서 이 근무표를 다시 열어요. "
                        } + "가족·동료 것이면 내 달력 옆에 받은 달력으로 두고, 내 일은 아래에서 따로 골라요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (linkLoading) Text("근무표를 불러오는 중…", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        val mine: () -> Unit = { onLinkStart?.invoke() ?: onPick(FirstStyle(com.bamnat.core.work.WorkStyle.SHIFT, roster = true)) }
                        val theirs: () -> Unit = { onLinkTheirs?.invoke() }
                        val enabled = !linkLoading
                        LinkOwnerButton("내 근무표예요", linkLean == com.bamnat.app.ui.setup.LinkStart.Owner.MINE, linkLean == null, enabled, mine)
                        LinkOwnerButton("가족·동료 것이에요", linkLean == com.bamnat.app.ui.setup.LinkStart.Owner.THEIRS, linkLean == null,
                            enabled && onLinkTheirs != null, theirs)
                    }
                }
                // [RVG-15] Kept as someone else's: where it went, and that the question below is the user's own work.
                if (!linkWaiting && (theirsKept != null || theirsLater)) androidx.compose.material3.Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (theirsKept != null) "‘$theirsKept’ 근무표는 받은 달력으로 넣었어요" else "받은 근무표는 첫 설정 뒤에 넣어요",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(if (theirsKept != null) "내 달력과 따로 있어요. 첫 설정을 마치면 달력 이름을 눌러 바꿔 볼 수 있어요. 이제 내 일을 골라 주세요."
                            else "지금은 근무표를 읽지 못했어요. 첫 설정을 마치면 달력에서 받은 달력으로 넣을지 물어요. 이제 내 일을 골라 주세요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                StepHeader(FIRST_QUESTION, "적거나 고른 일에 맞는 기능만 켜고, 다음 질문도 그 일에 맞춰 물어요. 적은 말은 이 휴대폰에만 있어요.")
                // [FLAG-12][FLAG-13] A hidden kind of work is not offered here (the owner's phone shows it marked).
                val shown = com.bamnat.app.ui.flags.typeChoices()
                JobAsk(onPick = { style, roster -> onPick(FirstStyle(style, roster)) }, shown = shown)
                STYLE_CHOICES.filter { shown.shows(it.style) }.forEach { c ->
                    ChoiceCard(title = c.title, subtitle = shown.mark(c.style, c.subtitle.substringBefore(" · ")), icon = c.icon, selected = false,
                        onClick = { onPick(FirstStyle(c.style, c.roster)) })
                }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("백업이나 회사 파일로 시작할게요") }
            }
        }
    }
}

/** [RVG-15] One of the two answers to '누구 근무표인가요?': [lead] filled, [even] both alike (tonal), else outlined. */
@Composable
private fun LinkOwnerButton(text: String, lead: Boolean, even: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    when {
        lead -> androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = m) { Text(text) }
        even -> androidx.compose.material3.FilledTonalButton(onClick = onClick, enabled = enabled, modifier = m) { Text(text) }
        else -> androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, modifier = m) { Text(text) }
    }
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
