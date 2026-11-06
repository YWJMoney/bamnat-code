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
                     onLinkStart: (() -> Unit)? = null) {
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
                        Text(if (onLinkStart != null) "근무 이름과 시간, 근무한 날을 이 근무표에서 그대로 가져와요. 다음 화면에서 이름만 확인하면 시작해요. 다른 일이면 아래에서 골라요."
                            else "매달 받는 근무표로 바로 시작하면, 첫 설정을 마친 뒤 달력에서 이 근무표를 열어 내 달력에 넣을지 물어봐요. 다른 일이면 아래에서 골라요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        androidx.compose.material3.Button(onClick = { onLinkStart?.invoke() ?: onPick(FirstStyle(com.bamnat.core.work.WorkStyle.SHIFT, roster = true)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (onLinkStart != null) "이 근무표로 시작" else "근무표가 매달 나와요 · 이걸로 시작") }
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
