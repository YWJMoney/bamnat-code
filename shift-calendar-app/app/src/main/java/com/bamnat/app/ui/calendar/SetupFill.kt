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
internal fun SetupFillCard(moment: SetupFill.Moment, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (LocalShowKani.current) {
                Kani(KaniMood.Done, size = 48.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(numberWithUnit(moment.line.head, 0.7f), style = MaterialTheme.typography.titleLarge.merge(NumberStyle).copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                val rest = moment.line.restAt(com.bamnat.core.time.Today.dateTime())
                if (rest.isNotEmpty()) Text(rest, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(onClick = SetupFill::close) { Icon(Icons.Filled.Close, "채워진 달력 안내 닫기", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
