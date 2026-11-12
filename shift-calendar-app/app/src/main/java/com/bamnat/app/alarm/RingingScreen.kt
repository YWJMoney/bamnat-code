package com.bamnat.app.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Coral = Color(0xFFFF6B5E)
private val Calm = Color(0xFF8FA8FF)

/**
 * [ALRM-23] 1.35 the ringing screen. The sky behind follows the hour (night, dawn, day), rings pulse out from the alarm
 * like sound, and the time stands large. One handle does both: slide it right to turn the alarm off, left for 다시 울림,
 * with a tick under the finger when it is far enough and a spring back when let go early; a slide never happens from a
 * brush in a pocket. Each side can also be tapped, and a screen reader offers both as actions. 오늘 남은 알람 모두 끄기
 * stays below. During a call ([inCall]) a line says the sound waits for the call.
 */
@Composable
internal fun RingingScreen(
    title: String,
    subtitle: String,
    snoozeMinutes: Int,
    dismissLabel: String = "알람 끄기",
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    /** [ALRM-09] 오늘 남은 알람 모두 끄기; none hides the button (previews). */
    onAllOff: (() -> Unit)? = null,
    /** [ALRM-08] What the volume and power buttons do. */
    hint: String? = null,
    /** [ALRM-22] A call is on: the sound waits for it. */
    inCall: Boolean = false,
    /** [EASE-20] The day's shift, big on its own colour; null for an alarm without a shift (memo, test, 일어났나요?). */
    shift: RingShift? = null,
    /** [EASE-19] 끄고 오늘 보기: off, then the app on today. Null hides it (with a mission, or previews). */
    onToday: (() -> Unit)? = null,
    /** 1.39 [FLOW-15] 깨우는 단계 1..3 ([AlarmService.wakeStage]); 0 shows no dots. */
    stage: Int = 0,
) {
    val now by produceState(Today.dateTime()) {
        while (true) {
            value = Today.dateTime()
            delay(1_000)
        }
    }
    val context = LocalContext.current
    // Motion off in the phone's settings keeps one still ring; so does a test, whose clock an endless animation would run on.
    val still = remember {
        android.os.Build.FINGERPRINT == "robolectric" ||
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val sky = skyFor(now.hour)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(sky)),
    ) {
        val sideways = maxHeight < 480.dp && maxWidth > maxHeight
        Pulse(still, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            val info: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.dayTitle(now.toLocalDate()),
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Fmt.time(now.toLocalTime()),
                        color = Color.White,
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Light,
                        style = NumberStyle,
                    )
                    // 1.39 [FLOW-15] 깨우는 단계: three dots under the one big number, and which step it is now.
                    if (stage in 1..3) WakeSteps(stage)
                    Spacer(Modifier.height(8.dp))
                    if (shift != null) ShiftBlock(shift)
                    else Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                    Text(subtitle, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center)
                    if (inCall) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("통화 중이라 소리를 멈췄어요. 끝나면 다시 울려요", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (hint != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(hint, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                Column(m.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SlideToAnswer(snoozeMinutes, dismissLabel, onSnooze, onDismiss)
                    Text("가운데를 밀어요 · 오른쪽 끄기, 왼쪽 ${snoozeMinutes}분 뒤", color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    // [EASE-19] Turning it off ends there (the app does not open); this is for when the user wants the day.
                    // PC 267560dd: it is the one most want after the slider, so it gets the outlined button.
                    if (onToday != null) {
                        OutlinedButton(
                            onClick = onToday,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) { Text("끄고 오늘 보기", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                    }
                    if (onAllOff != null) {
                        // [ALRM-09] Up, out of bed: nothing more today. Tomorrow's alarms stay; 다시 켜기 waits on the notice.
                        // PC 267560dd: it silences the rest of the day, so the first tap only asks; a second within a few seconds does it.
                        var asking by remember { mutableStateOf(false) }
                        LaunchedEffect(asking) { if (asking) { delay(ALL_OFF_ASK_MILLIS); asking = false } }
                        androidx.compose.material3.TextButton(
                            onClick = { if (asking) onAllOff() else asking = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                        ) {
                            Text(allOffLabel(asking), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                                fontWeight = if (asking) FontWeight.SemiBold else null)
                        }
                    }
                }
            }
            if (sideways) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    info(Modifier.weight(1f))
                    controls(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    info(Modifier.weight(1f).fillMaxWidth().padding(top = 40.dp))
                    Spacer(Modifier.height(16.dp))
                    controls(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private const val ALL_OFF_ASK_MILLIS = 5_000L

/** 1.39 [FLOW-15] The three steps a ring wakes by, as the screen says them. */
internal val WAKE_STEPS = listOf("진동", "소리 작게", "소리 크게")

/** 1.39 [FLOW-15] 진동 → 소리 작게 → 소리 크게 as three dots, the ones done filled, with "지금 2단계 · 소리 작게". */
@Composable
private fun WakeSteps(stage: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..3) Box(Modifier.size(if (i == stage) 12.dp else 9.dp)
                .background(Color.White.copy(alpha = if (i <= stage) 0.95f else 0.3f), RoundedCornerShape(50)))
        }
        Text("지금 ${stage}단계 · ${WAKE_STEPS[stage - 1]}", color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

/** [ALRM-09] The 모두 끄기 button, before and after the first tap. */
internal fun allOffLabel(asking: Boolean) =
    if (asking) "한 번 더 누르면 오늘 남은 알람이 모두 꺼져요" else "오늘 남은 알람 모두 끄기"

/** [EASE-20] What the ringing screen shows big: "오늘 근무" over "주간" on the shift's colour. */
data class RingShift(val day: String, val name: String, val color: Color)

/** [EASE-20] The shift [ring] is for, said from [today]: 오늘, 내일 (an alarm the evening before), 어제 (a nap after a 야간). */
internal fun ringShift(ring: RingRequest, today: java.time.LocalDate): RingShift? {
    if (ring.shiftName.isBlank() || ring.label != null || ring.recheck) return null
    // 2.0.0 [RVG-09] 검토 TYPE-01: '오늘 영업' on a shop's day.
    val noun = com.bamnat.core.alarm.AlarmWords.noun(ring.shiftName)
    val day = when (ring.shiftDate) {
        today -> "오늘 $noun"
        today.plusDays(1) -> "내일 $noun"
        today.minusDays(1) -> "어제 시작한 $noun"
        else -> "${ring.shiftDate.monthValue}월 ${ring.shiftDate.dayOfMonth}일 $noun"
    }
    val color = if (ring.shiftColor == 0L) Color(0xFF3D5AFE) else Color(ring.shiftColor.toInt()).copy(alpha = 1f)
    return RingShift(day, ring.shiftName, color)
}

/** [EASE-20] The shift's name large on its colour, in white or near-black, whichever reads on it. */
@Composable
private fun ShiftBlock(shift: RingShift) {
    val ink = if (shift.color.luminance() > 0.45f) Color(0xFF14161F) else Color.White
    Surface(
        color = shift.color,
        contentColor = ink,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.widthIn(min = 200.dp, max = 420.dp).fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shift.day, style = MaterialTheme.typography.titleMedium, color = ink.copy(alpha = 0.8f))
            Text(shift.name, fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 50.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** The sky at [hour]: deep night, a dawn that warms at the bottom, an evening dusk, or a clear day. */
private fun skyFor(hour: Int): List<Color> = when (hour) {
    in 4..6 -> listOf(Color(0xFF1B2257), Color(0xFF4A2F6B), Color(0xFF8C4A5E))
    in 7..16 -> listOf(Color(0xFF1F3C88), Color(0xFF2A5298), Color(0xFF16224D))
    in 17..19 -> listOf(Color(0xFF2B1E5A), Color(0xFF5B2A63), Color(0xFF1A1438))
    else -> listOf(Color(0xFF0F1433), Color(0xFF1D2B6B), Color(0xFF0B0E1E))
}

/** Rings that spread from the middle of the screen and fade, three at a time; one still ring when motion is off. */
@Composable
private fun Pulse(still: Boolean, modifier: Modifier) {
    val phase = if (still) 0.35f else {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    }
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height * 0.36f)
        val max = minOf(size.width, size.height) * 0.62f
        for (i in 0 until if (still) 1 else 3) {
            val p = (phase + i / 3f) % 1f
            drawCircle(Color.White.copy(alpha = 0.16f * (1f - p)), radius = max * (0.25f + 0.75f * p), center = center,
                style = Stroke(width = 2.dp.toPx() + 6.dp.toPx() * (1f - p)))
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center, max * 0.45f), max * 0.45f, center)
    }
}

/**
 * The handle: right past [THRESHOLD] of the way turns the alarm off, left snoozes. The track warms toward coral on the
 * right and cools on the left as it moves.
 */
@Composable
private fun SlideToAnswer(snoozeMinutes: Int, dismissLabel: String, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .semantics {
                contentDescription = "알람 조절: 오른쪽으로 밀면 끄기, 왼쪽으로 밀면 ${snoozeMinutes}분 뒤 다시 울림"
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) { onDismiss(); true },
                    CustomAccessibilityAction("${snoozeMinutes}분 뒤 다시 울림") { onSnooze(); true },
                )
            },
    ) {
        val thumb = 68.dp
        val reach = with(density) { ((maxWidth - thumb) / 2).toPx() }.coerceAtLeast(1f)
        val f = (offset.value / reach).coerceIn(-1f, 1f)
        val track = when {
            f > 0f -> lerp(Color.White.copy(alpha = 0.12f), Coral.copy(alpha = 0.55f), f)
            f < 0f -> lerp(Color.White.copy(alpha = 0.12f), Calm.copy(alpha = 0.45f), -f)
            else -> Color.White.copy(alpha = 0.12f)
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(40.dp)).background(track))
        // Each end is a target too: a tap does what the slide would.
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp))
                    .clickable(onClickLabel = "${snoozeMinutes}분 뒤 다시 울림") { if (!done) { done = true; onSnooze() } }
                    .padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Snooze, null, tint = Color.White.copy(alpha = 0.6f + 0.4f * maxOf(0f, -f)))
                Spacer(Modifier.size(6.dp))
                Text("${snoozeMinutes}분 뒤", color = Color.White.copy(alpha = 0.75f + 0.25f * maxOf(0f, -f)), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.size(thumb))
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp))
                    .clickable(onClickLabel = dismissLabel) { if (!done) { done = true; onDismiss() } }
                    .padding(end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(if (dismissLabel.length > 6) "끄기" else dismissLabel.removePrefix("알람 "),
                    color = Color.White.copy(alpha = 0.75f + 0.25f * maxOf(0f, f)), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(6.dp))
                Icon(Icons.Filled.AlarmOff, null, tint = Color.White.copy(alpha = 0.6f + 0.4f * maxOf(0f, f)))
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(lerp(Color.White, if (f >= 0f) Coral else Calm, abs(f)))
                .pointerInput(reach) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val end = offset.value / reach
                            scope.launch {
                                when {
                                    done -> Unit
                                    end >= THRESHOLD -> { done = true; offset.animateTo(reach, tween(120)); onDismiss() }
                                    end <= -THRESHOLD -> { done = true; offset.animateTo(-reach, tween(120)); onSnooze() }
                                    else -> { armed = 0; offset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
                                }
                            }
                        },
                        onDragCancel = { armed = 0; scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.55f)) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + amount).coerceIn(-reach, reach))
                            val side = when {
                                offset.value / reach >= THRESHOLD -> 1
                                offset.value / reach <= -THRESHOLD -> -1
                                else -> 0
                            }
                            if (side != armed) {
                                armed = side
                                if (side != 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Alarm, null, tint = if (abs(f) > 0.4f) Color.White else Color(0xFF1D2B6B), modifier = Modifier.size(30.dp))
        }
    }
}

/** How far toward an end the handle must go. */
private const val THRESHOLD = 0.7f
