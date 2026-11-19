package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.Fmt
import com.bamnat.core.money.EnvelopeMove
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MonthStory
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.37 [LEDH-16] 지난달 돈 이야기: in the first week of a month, last month's first card on the summary, free; the whole
 * story (일한 것·쓴 돈·모은 돈·다음 달 한 가지) one card at a time with the 7-day pass or 프로 (결정 5). Can be put away
 * for the month, and turned off in 카드 배치.
 */
@Composable
internal fun MonthStoryCard(book: MoneyBook, moves: List<EnvelopeMove>, work: MonthWork?, today: LocalDate, gate: Gate, calendarId: String = "") {
    if (!MonthStory.due(today)) return
    val month = YearMonth.from(today).minusMonths(1)
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("month_story", android.content.Context.MODE_PRIVATE) }
    // 2.0.0 [RVK-42] (검토 MONEY-26·T06a-12) 이번 달 닫기 is per calendar: closed on 본업, 부업·가게's story still comes. The one kept before
    // for every calendar stays with the main one.
    val key = "closed_for:$calendarId"
    var closedFor by remember(calendarId) {
        mutableStateOf(prefs.getString(key, null) ?: prefs.getString("closed_for", "").orEmpty().takeIf {
            calendarId == com.bamnat.app.data.PersonalCalendar.MAIN || calendarId.isEmpty()
        }.orEmpty())
    }
    if (closedFor == month.toString()) return
    val story = remember(book, moves, work, month) {
        MonthStory.of(month, book.month(month), book.month(month.minusMonths(1)), moves,
            work?.minutes, work?.days, work?.nights, work?.net)
    }
    if (story.earned == 0L && story.spent == 0L) return
    val cards = remember(story) { story.cards() }
    var open by remember { mutableStateOf(false) }
    SectionCard(title = "${month.monthValue}월 돈 이야기", trailing = { TextButton(onClick = { closedFor = month.toString(); prefs.edit().putString(key, closedFor).apply() }) { Text("이번 달 닫기") } }) {
        StoryPage(cards.first())
        TextButton(onClick = { open = true }) { Text("이야기 ${cards.size}장 넘겨 보기") }
    }
    if (open) {
        var page by rememberSaveable { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("${month.monthValue}월 돈 이야기 ${page + 1}/${cards.size}") },
            text = {
                if (page == 0) StoryPage(cards[0]) else com.bamnat.app.ui.pro.ProLock(Feature.MONEY_STATS, gate) { StoryPage(cards[page]) }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text("이전") }
                    if (page < cards.lastIndex) TextButton(onClick = { page++ }) { Text("다음") } else TextButton(onClick = { open = false }) { Text("닫기") }
                }
            },
            dismissButton = { if (page < cards.lastIndex) TextButton(onClick = { open = false }) { Text("닫기") } },
        )
    }
}

@Composable
private fun StoryPage(c: MonthStory.Card) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(c.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.hide(c.big), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        c.lines.forEach { Text(Fmt.hide(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
