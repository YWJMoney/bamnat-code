package com.bamnat.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.theme.BamnatMotion
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.core.design.DayStory
import com.bamnat.core.design.StoryItem
import com.bamnat.core.design.StoryKind
import com.bamnat.core.design.StoryLineItem
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.38 [DSG-05] One thing in a story line; the same as core's [StoryItem] (tone: "good", "caution", "danger" or null). */
typealias StoryEvent = StoryItem

/** A story item's [StoryItem.tone] name as a [Tone]. */
fun storyTone(name: String?): Tone = when (name) { "good" -> Tone.Good; "caution" -> Tone.Caution; "danger" -> Tone.Danger; else -> Tone.Plain }

/**
 * 1.38 [DSG-05] 이야기 줄 (흐름·디자인 계획 4장): a day (or a night's sleep, or a team's day) as a vertical time line with
 * a card for each thing, a "지금" mark where now falls, and a tap that opens a card's detail in place. A night shift
 * that crosses midnight is one card with its own times, "어제 19:00–07:00" ([DayStory]). [shiftColor] colours a shift's dot.
 */
@Composable
fun StoryLine(
    day: LocalDate,
    events: List<StoryEvent>,
    now: LocalDateTime?,
    onOpen: (StoryEvent) -> Unit = {},
    shiftColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val lines = remember(day, events) { DayStory.arrange(day, events) }
    val today = now?.takeIf { it.toLocalDate() == day }
    val nowAt = today?.let { DayStory.nowAfter(lines, it) }
    Column(modifier.fillMaxWidth()) {
        if (nowAt == -1) NowMark()
        lines.forEachIndexed { i, line ->
            // 1.38 PC 12.2: a thing still going on says so on its own card, and "지금" is not drawn under it as if it were over.
            val going = today != null && DayStory.ongoing(line, today)
            StoryRow(line, last = i == lines.lastIndex, shiftColor = shiftColor, onOpen = onOpen, going = going)
            if (nowAt == i && !going) NowMark()
        }
    }
}

@Composable
private fun StoryRow(line: StoryLineItem, last: Boolean, shiftColor: Color, onOpen: (StoryEvent) -> Unit, going: Boolean = false) {
    val e = line.item
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    val dot = when (e.kind) {
        StoryKind.Shift -> shiftColor
        StoryKind.Sleep, StoryKind.SleepPlan -> Color(0xFF6F7BD8)
        else -> storyTone(e.tone).takeIf { it != Tone.Plain }?.color() ?: MaterialTheme.colorScheme.outline
    }
    // The thing's own times, with the day said when it is not this one: "어제 19:00–07:00", "19:00–내일 07:00".
    val time = buildString {
        if (line.fromYesterday) append("어제 ")
        append(hm(e.start))
        e.end?.let { append("–"); if (line.intoTomorrow) append("내일 "); append(hm(it)) }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 1.38 PC 12차 유형별 빈틈 9: last night's shift began yesterday, so its own time with "어제" over it, not the day's 00:00.
        // PC 12.3 빈틈 6: at 200% text '06:00' broke into '06:/00'. The column grows with the text, one line always.
        Column(Modifier.width(timeColumn()).padding(top = 12.dp)) {
            if (line.fromYesterday) Text("어제", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false)
            Text(hm(if (line.fromYesterday) e.start else line.from), style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 14.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.padding(top = 12.dp).size(12.dp).background(dot, CircleShape))
        }
        val dashed = e.kind == StoryKind.SleepPlan
        Column(
            Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp).heightIn(min = 48.dp)
                .background(if (dashed) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (open) "접기" else "펼치기") { open = !open; if (open) onOpen(e) }
                .animateContentSize(BamnatMotion.spec(BamnatMotion.open()))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(goingLabel(e.kind).takeIf { going }, time, e.title).joinToString(", ")
                    stateDescription = if (open) "펼침" else "접힘"
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (going) Text(goingLabel(e.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(e.title, style = MaterialTheme.typography.titleSmall, color = storyTone(e.tone).color())
            Text(time, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open && !e.detail.isNullOrEmpty()) Text(e.detail!!, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The times' column: 48dp, wider as the phone's text grows so 'HH:MM' stays on one line, the same on every row. */
@Composable
private fun timeColumn(): androidx.compose.ui.unit.Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) { maxOf(48.dp, 42.sp.toDp()) }

@Composable
private fun NowMark() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("지금", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(timeColumn()),
            maxLines = 1, softWrap = false)
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        Spacer(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.primary))
    }
}

private fun goingLabel(kind: StoryKind) = when (kind) {
    StoryKind.Shift -> "지금 근무 중"
    StoryKind.Sleep, StoryKind.SleepPlan -> "지금 자는 중"
    else -> "지금"
}

private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
