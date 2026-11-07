package com.bamnat.app.ui.sleep

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.color
import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.sleep.NightLine
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 1.39 [FLOW-13] 밤 화면 (흐름·디자인 계획 3-6): what opens with 잠들었어요 or 측정 시작. A big clock, the one line a shift worker
 * checks before sleeping ("내일 07:00 주간 · 알람 06:00 켜짐", [NightLine]: the calendar sets the alarm, this only confirms it),
 * 잠드는 소리, and 깼어요. In the warm dark [com.bamnat.app.ui.theme.NightPalette] with the screen dimmed inside the app; the
 * phone's own screen timeout is left as it is.
 */
class NightViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings

    val open: StateFlow<SleepEntry?> = container.sleepRepository.recent(days = 2).map { log -> log.firstOrNull { it.end == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val minutes = flow {
        while (true) {
            emit(Today.dateTime())
            delay(60_000L - Today.millis() % 60_000L)
        }
    }

    val line: StateFlow<NightLine.Line?> = combine(container.scheduleRepository.live, settings, minutes) { snapshot, s, now ->
        runCatching {
            val today = now.toLocalDate()
            val work = SleepSchedule.of(snapshot.resolver, today, today.plusDays(6), alarmsOn = false).work.firstOrNull { it.from.isAfter(now) }
            val alarm = if (s.alarmsEnabled) AlarmPlanner.nextAlarm(snapshot.resolver, now)?.triggerAt else null
            NightLine.of(now, work, alarm, s.alarmsEnabled, shifts = s.workStyle?.usesPayEngine != false)
        }.getOrNull()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveSound(mix: com.bamnat.core.sleep.NoiseMix, minutes: Int) {
        container.settingsRepository.update { it.copy(noiseMix = mix.encode(), noiseTimerMinutes = minutes) }
    }

    fun fellAsleep() { container.appScope.launch { container.sleepRepository.fellAsleep() } }

    fun wokeUp(done: () -> Unit) {
        container.appScope.launch {
            container.sleepRepository.wokeUp()
            kotlinx.coroutines.withContext(Dispatchers.Main) { done() }
        }
    }
}

/** How dim the night screen keeps the window (0..1 of the phone's range), only while it is shown. */
private const val NIGHT_BRIGHTNESS = 0.08f

@Composable
fun NightScreen(vm: NightViewModel, onClose: () -> Unit, onAlarms: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val open by vm.open.collectAsStateWithLifecycle()
    val line by vm.line.collectAsStateWithLifecycle()
    val recording by com.bamnat.app.sleep.SleepRecordService.recording.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The window dimmed while this screen is up, and back on leaving (the bar icons follow NightSurface).
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val before = window?.attributes?.screenBrightness
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = NIGHT_BRIGHTNESS } }
        onDispose {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before ?: -1f } }
        }
    }
    val now by produceState(Today.dateTime()) {
        while (true) {
            value = Today.dateTime()
            delay(60_000L - Today.millis() % 60_000L)
        }
    }
    // Always dark: it is the screen for a dark room (밤 색 끔 keeps the rest of the app in its own colours).
    com.bamnat.app.ui.theme.NightSurface(true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // A visible way out (CLAUDE.md 3항: 보이는 닫기가 없는 창은 문제).
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "밤 화면 닫기") }
                }
                Spacer(Modifier.height(24.dp))
                Text(Fmt.dayTitle(now.toLocalDate()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.time(now.toLocalTime()), fontSize = 76.sp, fontWeight = FontWeight.Light, style = NumberStyle,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                line?.let { l -> NightLineRow(l, onAlarms) }
                if (com.bamnat.app.ui.components.LocalShowKani.current) {
                    Spacer(Modifier.height(16.dp))
                    com.bamnat.app.ui.components.Kani(com.bamnat.app.ui.components.KaniMood.Sleepy, night = true, size = 72.dp)
                }
                Spacer(Modifier.height(12.dp))
                val o = open
                Text(
                    when {
                        o != null -> (if (o.start.toLocalDate() == now.toLocalDate()) "" else "어제 ") + "${Fmt.time(o.start.toLocalTime())}부터 자는 중"
                        else -> "잠들 때 ‘잠들었어요’를 누르면 잔 시간을 적어요"
                    } + (recording?.let { " · 소리 재는 중" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepSoundCard(settings.noiseMix, settings.noiseTimerMinutes, vm::saveSound)
                    if (o != null) Button(onClick = { vm.wokeUp(onClose) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("깼어요", style = MaterialTheme.typography.titleMedium)
                    } else Button(onClick = vm::fellAsleep, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("잠들었어요", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("화면이 꺼지는 시간은 휴대폰 설정 그대로예요. 근무 알람은 이 화면과 상관없이 울려요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** "내일 07:00 주간 · 알람 06:00 켜짐"; with no alarm set, said in the caution colour with the way to the alarms. */
@Composable
private fun NightLineRow(l: NightLine.Line, onAlarms: () -> Unit) {
    val tint = if (l.alarmOn) MaterialTheme.colorScheme.onSurface else com.bamnat.app.ui.theme.Tone.Caution.color()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(if (l.alarmOn) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(" ${l.text}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = tint)
    }
    if (!l.alarmOn) TextButton(onClick = onAlarms, modifier = Modifier.heightIn(min = 48.dp)) { Text("알람 확인하기") }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
