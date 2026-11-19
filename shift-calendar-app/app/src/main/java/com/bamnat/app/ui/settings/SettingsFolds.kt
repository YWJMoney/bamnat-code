package com.bamnat.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.PhoneDayView
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.BamnatMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVO-21] 검토 SET-08·U11a-18 ('화면과 글자 한 쪽에 45줄, 소제목 없이'): a group of a long settings page, folded to its name and one
 * line of what is in it until it is opened. It opens by itself when 설정 찾기 points at one of its [rows], so a found row is never
 * hidden in a fold; [requester] lets the page's 바로 가기 chips scroll to it.
 */
@Composable
internal fun FoldGroup(
    title: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    requester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(title = null, modifier = modifier.bringIntoViewRequester(requester)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "접기" else "펼치기", onClick = onToggle)
                .semantics { heading(); stateDescription = if (open) "펼쳐짐" else "접힘" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (!open) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val ms = BamnatMotion.open()
        AnimatedVisibility(open, enter = expandVertically(BamnatMotion.spec(ms)) + fadeIn(BamnatMotion.spec(ms)),
            exit = shrinkVertically(BamnatMotion.spec(ms)) + fadeOut(BamnatMotion.spec(ms))) {
            Column(Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

/** One group of a folded page: its key (kept across a recreated screen), name, summary and the rows 설정 찾기 can point at in it. */
internal class Fold(val key: String, val title: String, val chip: String, val summary: String, val rows: Set<String>)

/**
 * 2.0.0 [RVO-21] 검토 U11a-18 '맨 위에 묶음으로 바로 가기 칩': a chip per group; one opens its group and brings it on screen. A chip is
 * picked while its group is open, so the row also says what is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FoldChips(folds: List<Fold>, open: Set<String>, requesters: Map<String, BringIntoViewRequester>, onOpen: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val wait = BamnatMotion.open().toLong()
    Column(Modifier.fillMaxWidth()) {
        Text("묶음으로 바로 가기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folds.forEach { f ->
                val on = f.key in open
                com.bamnat.app.ui.components.FilterChip(on, onClick = {
                    if (on) onOpen(f.key, false)
                    else {
                        onOpen(f.key, true)
                        scope.launch { delay(wait); requesters[f.key]?.bringIntoView() }
                    }
                }, label = { Text(f.chip) })
            }
        }
    }
}

/** The groups a found row asks to be open (none when nothing is pointed at, or the row is outside every group). */
internal fun foldsFor(focus: SettingsFocus?, folds: List<Fold>): Set<String> =
    focus?.row?.let { row -> folds.filter { row in it.rows }.map { it.key }.toSet() }.orEmpty()

/**
 * 2.0.0 [RVO-21] 검토 U11a-19·SET-08-2 ('날짜를 누르면'·'한 번 눌러 고르기'·'달력 아래 하루 카드' 세 설정이 얽혀 어느 것이 이기는지 화면에서
 * 알 수 없어요): what a tap on a date does on a phone and the folded cover screen, one choice. Each writes the three settings together, in
 * the order the calendar reads them ([com.bamnat.app.ui.calendar.CalendarScreen]: 그 주만 > 아래 카드 > 한 번 눌러 고르기 > 바로 열기),
 * so what is chosen is what happens. Unfolded, the day is always beside the calendar.
 */
internal enum class DayTap(val label: String, val says: String) {
    OPEN("바로 열기", "누르면 그 날의 하루 화면이 바로 크게 열려요"),
    CARD("아래 카드 먼저", "누르면 달력 아래 작은 카드에 먼저 보이고, 한 번 더 누르면 크게 열려요"),
    PICK("한 번 눌러 고르기", "처음 누르면 테두리만 생기고, 같은 날을 한 번 더 누르면 열려요. 잘못 눌러 열리는 일이 줄어요"),
    WEEK("그 주만 보기", "누르면 달력이 그 주만 남고, 아래에 그 날이 펼쳐져요");

    fun applied(s: AppSettings): AppSettings = when (this) {
        OPEN -> s.copy(phoneDayView = PhoneDayView.SHEET, showDayCard = false, tapSelectFirst = false)
        CARD -> s.copy(phoneDayView = PhoneDayView.SHEET, showDayCard = true, tapSelectFirst = false)
        PICK -> s.copy(phoneDayView = PhoneDayView.SHEET, showDayCard = false, tapSelectFirst = true)
        WEEK -> s.copy(phoneDayView = PhoneDayView.WEEK)
    }

    companion object {
        fun of(s: AppSettings): DayTap = when {
            s.phoneDayView == PhoneDayView.WEEK -> WEEK
            s.showDayCard -> CARD
            s.tapSelectFirst -> PICK
            else -> OPEN
        }
    }
}

/**
 * 2.0.0 [RVO-21] 검토 U11a-19 '작은 움직이는 미리 보기': a week of seven cells, a tap on the fourth and what follows, drawn again and again
 * in the app's one opening length ([BamnatMotion.open]). With 움직임 끄기 or the phone's 애니메이션 삭제 it is the last picture, still.
 */
@Composable
internal fun DayTapPreview(tap: DayTap, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val step = BamnatMotion.open()
    // 0 → the finger comes down, 1 → the first result, 2 → (고르기) the second tap's result. Still: the end.
    val p = remember(tap) { Animatable(if (step == 0) 2f else 0f) }
    val ease = BamnatMotion.spec<Float>(step)
    LaunchedEffect(tap, step) {
        if (step == 0) { p.snapTo(2f); return@LaunchedEffect }
        while (true) {
            p.snapTo(0f)
            delay(step * 2L)
            p.animateTo(1f, ease)
            delay(step * 3L)
            if (tap == DayTap.PICK || tap == DayTap.CARD) {
                p.animateTo(2f, ease)
                delay(step * 3L)
            } else p.snapTo(2f)
            delay(step * 3L)
        }
    }
    val cell = scheme.surfaceContainerHigh
    val mark = scheme.primary
    val sheet = scheme.secondaryContainer
    Canvas(modifier.widthIn(max = 280.dp).fillMaxWidth().height(96.dp).semantics { contentDescription = "미리 보기: ${tap.says}" }) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * 6) / 7f
        val h = 18.dp.toPx()
        val r = CornerRadius(4.dp.toPx())
        val v = p.value
        val first = v.coerceIn(0f, 1f)
        val second = (v - 1f).coerceIn(0f, 1f)
        // The month: three weeks, the middle one holding the tapped day. 그 주만 fades the other two and lifts that week to the top.
        val fold = if (tap == DayTap.WEEK) first else 0f
        val weekY = (1f - fold) * (h + gap)
        for (row in 0..2) {
            val alpha = if (row == 1) 1f else 1f - fold
            if (alpha <= 0f) continue
            val y = if (row == 1) weekY else row * (h + gap)
            for (col in 0..6) drawRoundRect(cell, topLeft = Offset(col * (w + gap), y), size = Size(w, h), cornerRadius = r, alpha = alpha)
        }
        val tx = 3 * (w + gap)
        val ty = weekY
        // The finger: a soft dot as the day is pressed (the second press for 고르기·카드).
        val twice = tap == DayTap.PICK || tap == DayTap.CARD
        val press = if (v <= 1f) first else if (twice) second else 1f
        if (press in 0.05f..0.95f) drawCircle(mark, radius = h * 0.7f, center = Offset(tx + w / 2, ty + h / 2), alpha = 0.35f)
        // The day picked.
        if (first > 0f) drawRoundRect(mark, topLeft = Offset(tx, ty), size = Size(w, h), cornerRadius = r, style = Stroke(2.dp.toPx()), alpha = first)
        val below = if (tap == DayTap.WEEK) weekY + h + gap else 3 * (h + gap)
        val big = CornerRadius(12.dp.toPx())
        fun sheetUp(t: Float) {
            if (t > 0f) drawRoundRect(sheet, topLeft = Offset(0f, size.height - (size.height - h) * t), size = Size(size.width, size.height), cornerRadius = big)
        }
        when (tap) {
            DayTap.OPEN -> sheetUp(first)
            DayTap.WEEK -> if (first > 0f) drawRoundRect(sheet, topLeft = Offset(0f, below), size = Size(size.width, (size.height - below) * first), cornerRadius = r)
            DayTap.CARD -> {
                if (first > 0f) drawRoundRect(sheet, topLeft = Offset(0f, below), size = Size(size.width, (size.height - below).coerceAtMost(h * 1.6f)),
                    cornerRadius = r, alpha = first * (1f - second))
                sheetUp(second)
            }
            DayTap.PICK -> sheetUp(second)
        }
    }
}

/**
 * 2.0.0 [RVO-21] '날짜를 누르면' one choice ([DayTap]) with its moving picture: in 설정 › 화면과 글자 › 누르기 and in the calendar's
 * 카드 배치 options alike (같은 구조), so the two never say different things.
 */
@Composable
internal fun DayTapRow(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val now = DayTap.of(settings)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceRow(DAY_TAP_ROW, now.says + ". 펼친 화면에서는 늘 달력 옆에 보여요",
            DayTap.entries.map { it to it.label }, now) { t -> onChange { s -> t.applied(s) } }
        DayTapPreview(now, Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(2.dp))
    }
}

internal const val DAY_TAP_ROW = "날짜를 누르면"
