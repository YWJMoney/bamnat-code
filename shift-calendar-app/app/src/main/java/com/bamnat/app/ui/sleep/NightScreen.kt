package com.bamnat.app.ui.sleep

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
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
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bamnat.app.ui.parts.fillColor
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
import com.bamnat.core.sleep.NightClockShift
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

    /** 1.39 G5-M1: the master switch turned on here, where it was found off, without a trip to the alarm settings. */
    fun turnOnAlarms() {
        container.settingsRepository.update { it.copy(alarmsEnabled = true) }
        // PC 확인 f6b585f8: where the master switch was turned, for 알람 점검.
        container.telemetry.event("alarms_switch", "on night")
    }

    fun fellAsleep() { container.appScope.launch { container.sleepRepository.fellAsleep() } }

    fun wokeUp(done: () -> Unit) {
        container.appScope.launch {
            val slept = container.sleepRepository.wokeUp()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                // 1.39.1 [P391-02] 9차 PC(사무직, 1분 잠): '깼어요' opened the sleep log with nothing new to see. Under an hour there is no 아침 한 장,
                // so say what happened to it.
                val m = slept?.end?.let { java.time.Duration.between(slept.start, it).toMinutes() }
                if (m != null && m < com.bamnat.core.sleep.SleepMorning.MIN_MINUTES) android.widget.Toast.makeText(container.appContext,
                    "${m.coerceAtLeast(1)}분 잠은 짧아서 아침 한 장 없이 기록에만 남겨요", android.widget.Toast.LENGTH_LONG).show()
                done()
            }
        }
    }
}

/** How dim the night screen keeps the window (0..1 of the phone's range), only while it is shown. */
private const val NIGHT_BRIGHTNESS = 0.08f

@Composable
fun NightScreen(
    vm: NightViewModel, onClose: () -> Unit, onAlarms: () -> Unit,
    /** 1.39 사용자 2026-11-08 (수면 기록 찾기 쉽게): where 깼어요 goes, the sleep log with last night's 아침 한 장; [onClose] if not given. */
    onWoke: () -> Unit = onClose,
) {
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
    // 1.39 G5-N3 폴드 탁상 시계: half open like a laptop on the bedside, the clock above the hinge and the buttons below; while
    // charging the screen stays on as a bedside clock, the clock moving a few dp each minute so the screen does not keep it.
    val tabletop = com.bamnat.app.ui.LocalTabletop.current
    val charging = rememberPluggedIn()
    val clockOn = tabletop != null && charging
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, clockOn) {
        view.keepScreenOn = clockOn
        onDispose { view.keepScreenOn = false }
    }
    val o = open
    val status: @Composable () -> Unit = {
        Text(
            when {
                o != null -> (if (o.start.toLocalDate() == now.toLocalDate()) "" else "어제 ") + "${Fmt.time(o.start.toLocalTime())}부터 자는 중"
                else -> "잠들 때 ‘잠들었어요’를 누르면 잔 시간을 적어요"
            } + (recording?.let { " · 소리 재는 중" } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
    // 1.39 [FLOW-21] 스튜디오에서 꾸민 밤 화면: 바탕(사진·채우기), 날짜·시계·내일 줄 글자, 카드, 단추. A blueprint without night lines
    // leaves the screen as it was; what a look leaves out stays the warm dark.
    val ground = nightStyle("night")
    val dateStyle = nightStyle("night.date")
    val clockStyle = nightStyle("night.clock")
    val cardStyle = nightStyle("night.card")
    val buttonStyle = nightStyle("night.button")
    val sleepButton: @Composable () -> Unit = {
        val buttonShape = com.bamnat.app.ui.parts.PartMods.shapeOf(buttonStyle, androidx.compose.material3.ButtonDefaults.shape)
        val buttonColors = buttonStyle.fillColor?.let { c ->
            androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c,
                contentColor = buttonStyle.ink?.let { Color(it.toInt()) } ?: (if (c.luminance() > 0.45f) Color.Black else Color.White))
        } ?: androidx.compose.material3.ButtonDefaults.buttonColors()
        val buttonText = com.bamnat.app.ui.parts.PartMods.text(buttonStyle.copy(ink = null), MaterialTheme.typography.titleMedium)
        if (o != null) Button(onClick = { vm.wokeUp(onWoke) }, shape = buttonShape, colors = buttonColors, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("깼어요", style = buttonText)
        } else Button(onClick = vm::fellAsleep, shape = buttonShape, colors = buttonColors, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("잠들었어요", style = buttonText)
        }
    }
    val soundCard: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(cardStyle, MaterialTheme.shapes.large))) {
            SleepSoundCard(settings.noiseMix, settings.noiseTimerMinutes, vm::saveSound)
        }
    }
    val note: @Composable (String) -> Unit = { text ->
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    // Always dark: it is the screen for a dark room (밤 색 끔 keeps the rest of the app in its own colours).
    com.bamnat.app.ui.theme.NightSurface(true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (tabletop != null) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                var areaTop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().then(com.bamnat.app.ui.parts.PartMods.box(ground.copy(pad = null, padX = null, padY = null), androidx.compose.ui.graphics.RectangleShape)).safeDrawingPadding()
                    .onGloballyPositioned { areaTop = it.positionInWindow().y.toInt() }) {
                    val upper = with(density) { tabletop.upperHeight(areaTop, constraints.maxHeight).toDp() }
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(upper)) {
                            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Filled.Close, "밤 화면 닫기") }
                            val (dx, dy) = if (clockOn) NightClockShift.of(now.minute) else 0 to 0
                            Column(Modifier.align(Alignment.Center).offset(dx.dp, dy.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.dayTitle(now.toLocalDate()), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = com.bamnat.app.ui.parts.PartMods.text(dateStyle, MaterialTheme.typography.titleMedium))
                                Text(Fmt.time(now.toLocalTime()), color = MaterialTheme.colorScheme.onSurface,
                                    style = com.bamnat.app.ui.parts.PartMods.text(clockStyle, NumberStyle.copy(fontSize = 96.sp, fontWeight = FontWeight.Light)),
                                    modifier = Modifier.then(com.bamnat.app.ui.parts.PartMods.box(clockStyle, MaterialTheme.shapes.large)).semantics { heading() })
                                line?.let { l -> NightLineRow(l, onAlarms, vm::turnOnAlarms, switchOff = !settings.alarmsEnabled) }
                            }
                        }
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            status()
                            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                sleepButton()
                                soundCard()
                                note(if (clockOn) "충전 중이라 화면을 켜 둬요. 시계는 화면에 자국이 남지 않게 조금씩 자리를 옮겨요. 근무 알람은 이 화면과 상관없이 울려요."
                                    else "충전기에 꽂으면 화면이 꺼지지 않는 탁상 시계가 돼요. 근무 알람은 이 화면과 상관없이 울려요.")
                            }
                        }
                    }
                }
                return@Surface
            }
            Column(Modifier.fillMaxSize().then(com.bamnat.app.ui.parts.PartMods.box(ground.copy(pad = null, padX = null, padY = null), androidx.compose.ui.graphics.RectangleShape))
                .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // A visible way out (CLAUDE.md 3항: 보이는 닫기가 없는 창은 문제).
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "밤 화면 닫기") }
                }
                Spacer(Modifier.height(24.dp))
                Text(Fmt.dayTitle(now.toLocalDate()), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = com.bamnat.app.ui.parts.PartMods.text(dateStyle, MaterialTheme.typography.titleMedium))
                Text(Fmt.time(now.toLocalTime()), color = MaterialTheme.colorScheme.onSurface,
                    style = com.bamnat.app.ui.parts.PartMods.text(clockStyle, NumberStyle.copy(fontSize = 76.sp, fontWeight = FontWeight.Light)),
                    modifier = Modifier.then(com.bamnat.app.ui.parts.PartMods.box(clockStyle, MaterialTheme.shapes.large)).semantics { heading() })
                line?.let { l -> NightLineRow(l, onAlarms, vm::turnOnAlarms, switchOff = !settings.alarmsEnabled) }
                if (com.bamnat.app.ui.components.LocalShowKani.current) {
                    Spacer(Modifier.height(16.dp))
                    com.bamnat.app.ui.components.Kani(com.bamnat.app.ui.components.KaniMood.Sleepy, night = true, size = 72.dp)
                }
                Spacer(Modifier.height(12.dp))
                status()
                Spacer(Modifier.height(20.dp))
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    soundCard()
                    sleepButton()
                    note("화면이 꺼지는 시간은 휴대폰 설정 그대로예요. 근무 알람은 이 화면과 상관없이 울려요.")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 1.39 G5-N3: whether a charger is in (charging or already full), following plugging in and out while shown. */
@Composable
private fun rememberPluggedIn(): Boolean {
    val context = LocalContext.current
    return produceState(initialValue = pluggedIn(context), context) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) {
                value = i.action == android.content.Intent.ACTION_POWER_CONNECTED
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, r, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_POWER_CONNECTED)
            addAction(android.content.Intent.ACTION_POWER_DISCONNECTED)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitDispose { runCatching { context.unregisterReceiver(r) } }
    }.value
}

private fun pluggedIn(context: Context): Boolean =
    (context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

/**
 * "내일 07:00 주간 · 알람 06:00 켜짐"; with no alarm set, said in the caution colour with the way to the alarms. 1.39 G5-M1: when
 * it is the app's own switch that is off, one tap turns it on right here (the line then says the alarm), the rest of the way
 * (알람 확인하기) still there for a shift with no alarm.
 */
@Composable
private fun NightLineRow(l: NightLine.Line, onAlarms: () -> Unit, onTurnOn: () -> Unit, switchOff: Boolean) {
    // 1.39 [FLOW-21] 밤 화면 꾸미기's line letters; an alarm that is off keeps the caution colour whatever the look says (믿음).
    val lineStyle = nightStyle("night.line")
    val tint = if (l.alarmOn) lineStyle.ink?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.onSurface else com.bamnat.app.ui.theme.Tone.Caution.color()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(if (l.alarmOn) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(" ${l.text}", style = com.bamnat.app.ui.parts.PartMods.text(lineStyle.copy(ink = null), MaterialTheme.typography.titleMedium),
            textAlign = TextAlign.Center, color = tint)
    }
    if (!l.alarmOn) Row(horizontalArrangement = Arrangement.Center) {
        if (switchOff) Button(onClick = onTurnOn, modifier = Modifier.heightIn(min = 48.dp)) { Text("알람 켜기") }
        TextButton(onClick = onAlarms, modifier = Modifier.heightIn(min = 48.dp)) { Text("알람 확인하기") }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 1.39 [FLOW-21] 밤 화면·아침 한 장 꾸밈 is one of 1.39's new looks (프로, core FxPro.NEW_LOOKS "night"/"night."): with no 프로 and no
 * 하루 써 보기 the screen is drawn in its own warm dark; the look's lines stay in settings and come back with 프로 (쉬기만, 지우지 않음).
 */
@Composable
internal fun nightStyle(id: String): com.bamnat.core.recipe.PartStyle =
    if (nightLooksOn()) com.bamnat.app.ui.parts.partStyle(id) else com.bamnat.core.recipe.PartStyle()

/**
 * Whether 밤 화면 꾸밈 is drawn. Until the FX 프로 문 (PR #316, `FxAccess.on(c)`: 프로 or 하루 써 보기) is in the 1.39 integration this
 * follows the 정식 출시 switch only (before it every phone has 프로); the one line here becomes `FxAccess.on(c)` when both are merged.
 */
@Composable
private fun nightLooksOn(): Boolean {
    val c = (LocalContext.current.applicationContext as? com.bamnat.app.BamnatApp)?.container ?: return true
    // Read as state (lint StateFlowValueCalledInComposition): a change of 정식 출시 or 프로 draws the screen again.
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    return !gate.live || gate.tier >= com.bamnat.app.tier.Tier.PRO
}
