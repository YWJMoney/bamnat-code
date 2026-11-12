package com.bamnat.app.ui.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.Kani
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.LocalShowKani
import com.bamnat.app.ui.components.numberWithUnit
import com.bamnat.app.ui.theme.BamnatMotion
import com.bamnat.app.ui.theme.NumberStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.core.setup.FillMoment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * 1.39 [FLOW-03] 첫 설정 끝 '한 달이 채워지는 순간' (흐름·디자인 계획 3-1): the setup just saved leaves its one line here; the
 * calendar it went into fills from today in 0.8 s ([BamnatMotion.fill], nothing moves with 움직임 끄기 or 애니메이션 삭제) with the line
 * over the month and 칸이 '해냈어요'. No waiting: the days are already saved, nothing pretends to work. Kept in memory only, so
 * the next start of the app opens on the plain calendar.
 */
object SetupFill {
    data class Moment(val calendar: String, val line: FillMoment.Line)

    private val _now = MutableStateFlow<Moment?>(null)
    val now: StateFlow<Moment?> = _now

    /** The moment whose filling already ran: a month turned back to keeps its days, it does not fill again. */
    private var played: Moment? = null

    fun set(moment: Moment?) {
        _now.value = moment
    }

    fun close() {
        _now.value = null
    }

    internal fun play(m: Moment): Boolean = (played !== m).also { played = m }
}

/**
 * The share of the fill shown, 0 to 1, read while drawing (no recomposition per frame); null when nothing fills on [calendar].
 */
@Composable
internal fun rememberSetupFill(moment: SetupFill.Moment?, calendar: String, ready: Boolean = true): (() -> Float)? {
    val fill = remember { Animatable(1f) }
    val ms = BamnatMotion.fill()
    val mine = moment?.takeIf { it.calendar == calendar }
    // Started once the saved days are read, so the fill is of the month itself, not of an empty grid.
    LaunchedEffect(mine, ready) {
        if (mine != null && ready && SetupFill.play(mine) && ms > 0) {
            fill.snapTo(0f)
            fill.animateTo(1f, tween(ms, easing = LinearEasing))
        }
    }
    return if (mine == null) null else ({ fill.value })
}

/** When a cell of the grid comes in: days before [today] at once, then one after another to the grid's last day. */
internal fun fillShare(date: LocalDate, today: LocalDate, last: LocalDate): Float {
    if (date.isBefore(today)) return 0f
    val span = (last.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(1)
    return (date.toEpochDay() - today.toEpochDay()).toFloat() / span
}

/** The cell's alpha at [progress] of the fill: each day comes in over an eighth of it. */
internal fun fillAlpha(progress: Float, share: Float): Float = ((progress - share) * 8f).coerceIn(0f, 1f)

/** The line over the month while it fills: "이번 달 야간 8번" big, the rest under it, 칸이 '해냈어요' beside them. */
@Composable
internal fun SetupFillCard(moment: SetupFill.Moment, modifier: Modifier = Modifier, snap: com.bamnat.app.data.ScheduleSnapshot? = null) {
    // 2.0.0 [RVIB-17] (PC-129, with PC-36) The line was counted once, from the setup's days: a roster that came in by itself (리더가
    // 내일을 휴무로), a day changed by hand, 되돌리기 or a 작품 worn left '쉬는 날 12일 · 첫 알람 내일 06:00' as it was. It is counted
    // again from the calendar as it is now, each time the calendar changes; the first alarm is the one the phone has set (below).
    val now = com.bamnat.core.time.Today.dateTime()
    val live = androidx.compose.runtime.remember(snap, moment) {
        snap?.takeIf { !it.isEmpty }?.let { s ->
            runCatching {
                com.bamnat.core.setup.FillMoment.of(s.resolver.resolveRange(now.toLocalDate(), now.toLocalDate().plusDays(70)), now.toLocalDate(), moment.line.alarm)
            }.getOrNull()
        }
    }
    val line = live ?: moment.line
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (LocalShowKani.current) {
                Kani(KaniMood.Done, size = 48.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(numberWithUnit(line.head, 0.7f), style = MaterialTheme.typography.titleLarge.merge(NumberStyle).copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface)
                // 2.0.0 [RVI-67] (PC-36) The first alarm as it is set now, as 설정 › 알람 says it, not the setup's guess.
                val set = if (moment.calendar != com.bamnat.app.data.PersonalCalendar.MAIN) null else
                    androidx.compose.ui.platform.LocalContext.current.appContainer.alarmScheduler.upcoming.collectAsStateWithLifecycle().value
                val rest = if (moment.calendar != com.bamnat.app.data.PersonalCalendar.MAIN) line.restAt(now)
                    else line.restWith(set?.let { com.bamnat.app.alarm.AlarmScheduler.millisToLocal(it.triggerAtMillis) }, now)
                if (rest.isNotEmpty()) Text(rest, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = SetupFill::close) { Icon(Icons.Filled.Close, "채워진 달력 안내 닫기", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
