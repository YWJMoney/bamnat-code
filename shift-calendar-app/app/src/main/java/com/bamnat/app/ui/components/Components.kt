package com.bamnat.app.ui.components

import com.bamnat.core.model.ShiftIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.app.ui.theme.MemoPaletteDark
import com.bamnat.app.ui.theme.MemoPaletteLight
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.ShiftType
import java.time.LocalTime
import androidx.compose.ui.semantics.Role

fun Long.asColor(): Color = Color(this.toInt())

/**
 * [SCALE-01] The width of a label column beside its value ("6/12 (토)", "기본급"), grown with the phone's
 * text size so the label does not break into a tower at 150%, but never past [MAX_LABEL_SHARE] of the row,
 * so the value beside it keeps its room on a small phone.
 */
fun Modifier.labelWidth(width: Dp): Modifier = layout { measurable, constraints ->
    val grown = (width * fontScale.coerceIn(1f, MAX_LABEL_GROWTH)).roundToPx()
    val cap = if (constraints.hasBoundedWidth) (constraints.maxWidth * MAX_LABEL_SHARE).toInt() else grown
    val w = maxOf(width.roundToPx(), minOf(grown, cap)).coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private const val MAX_LABEL_GROWTH = 1.7f
private const val MAX_LABEL_SHARE = 0.42f

private val DarkInk = Color(0xFF151927)

/**
 * [VIS-01] Readable text color on top of [background]: whichever of white and the app's dark ink
 * has the higher contrast (WCAG), so 오후 #F2994A or 대근 #0E9F6E get dark letters, not faint white.
 */
fun contentColorOn(background: Color): Color {
    val argb = background.toArgb()
    // PC 확인 1.36 (두 폰 '응답 없음'): every day cell asks this for its 근무 배지 on each draw, with a handful of colours;
    // the answer is kept per colour so a month page does not work out the same contrast forty times.
    InkCache[argb]?.let { return it }
    // [WEAR-02] The same rule as the widgets and the watch, in core.
    val ink = if (com.bamnat.core.layout.Contrast.inkOn(argb.toLong() and 0xFFFFFFFFL, DarkInk.toArgb().toLong() and 0xFFFFFFFFL) == com.bamnat.core.layout.Contrast.WHITE) Color.White else DarkInk
    if (InkCache.size > 512) InkCache.clear()
    InkCache[argb] = ink
    return ink
}

private val InkCache = java.util.concurrent.ConcurrentHashMap<Int, Color>()

/** [DAY-02] Inside the day's groups sections drop their own card, so a group never holds a box in a box. */
val LocalFlatSections = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.let { s ->
    // 1.39 [MKW-02] 그려 보기(밝은 테마의 메모가 남색 카드에 검은 글씨): 꾸밈이 화면 밑에 깔리면 ScreenSkin이 surface를 투명으로 두어
    // 투명(밝기 0)을 어둡다고 읽었어요. 투명하면 그 위 글자색(onSurface)으로 바탕이 밝은지 어두운지 가려요.
    if (s.surface.alpha >= 0.5f) s.surface.luminance() < 0.5f else s.onSurface.luminance() > 0.5f
}

@Composable
fun memoBackground(index: Int): Color {
    val palette = if (isDarkSurface()) MemoPaletteDark else MemoPaletteLight
    val c = palette.getOrElse(index) { Color.Unspecified }
    return if (c == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerLow else c
}

/** [BLU-05] 1.36 부품 '근무 표시'(cal.cell.mark)의 모서리: the shape a badge takes inside a cell whose blueprint sets one. */
val LocalBadgeShape = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.graphics.Shape?> { null }

/** Rounded pill with the shift's color and label, the core visual of the calendar. */
@Composable
fun ShiftBadge(
    type: ShiftType?,
    modifier: Modifier = Modifier,
    text: String? = null,
    size: Dp = 22.dp,
    outlined: Boolean = false,
    showIcon: Boolean = true,
) {
    // PC 확인 C10 1.36 (아주 어두운 사진 위 '휴' 칩이 회색 바탕에 묻혀요): on a 꾸밈 사진 an outlined badge stands on a ground of its own,
    // and its line and letters turn to the ink of that ground when their own colour would not show on it.
    val onPhoto = if (outlined) com.bamnat.app.ui.decor.LocalOnPhoto.current?.copy(alpha = 1f) else null
    val own = type?.colorArgb?.asColor() ?: MaterialTheme.colorScheme.outline
    val color = if (onPhoto == null) own else {
        fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL
        if (com.bamnat.core.layout.Contrast.ratio(argb(own.copy(alpha = 1f)), argb(onPhoto)) >= 3.0) own else contentColorOn(onPhoto)
    }
    val label = text ?: type?.label ?: "휴"
    val shape = LocalBadgeShape.current ?: RoundedCornerShape(size / 3)
    // The icon only goes with the type's own label, not with stand-in text like "휴" [FEAT-01].
    val icon = if (showIcon && (text == null || text == type?.label)) ShiftIcon.of(type?.icon) else null
    // [RCP-08] 1.34: an emoji the user (or a recipe) picked goes where the drawn icon would.
    val emoji = if (showIcon && (text == null || text == type?.label)) ShiftIcon.emoji(type?.icon) else null
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (outlined) Modifier.then(if (onPhoto != null) Modifier.background(onPhoto, shape) else Modifier).border(1.5.dp, color, shape)
                else Modifier.background(color, shape),
            )
            .widthIn(min = size)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (outlined) color else contentColorOn(color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            // [RCX-30] 1.35 그림 꾸미기: the kind of day's own picture goes first.
            val pictured = showIcon && (text == null || text == type?.label) &&
                type?.id?.let { com.bamnat.app.ui.theme.LocalShiftIcons.current[it] } != null
            if (pictured && com.bamnat.app.ui.theme.ShiftPicture(type?.id, size * 0.62f)) {
                Spacer(Modifier.width(2.dp))
            } else if (icon != null) {
                Icon(icon.vector, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
                Spacer(Modifier.width(1.dp))
            } else if (emoji != null) {
                Text(emoji, fontSize = with(density) { (size * 0.48f).toSp() }, lineHeight = (BADGE_LINE / (size * 0.48f)).em, maxLines = 1)
                Spacer(Modifier.width(1.dp))
            }
            val textSize = size * 0.52f
            Text(
                text = label,
                color = fg,
                // [VIS-01] Sized to the badge, so a big system font cannot push it out of the cell.
                fontSize = with(density) { textSize.toSp() },
                // [VIS-02] The line too: the theme's 24sp line grew with the font and made the badge taller.
                // Counted from the text itself (em), as Android 14 scales big sp less than small ones.
                lineHeight = (BADGE_LINE / textSize).em,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** The badge's line, as tall as the theme's body line at the normal font size. */
private val BADGE_LINE = 24.dp

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Card section with a title, used on detail and settings screens. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [GUIDE-06] A card of a feature that can be off carries ⋮ › 이 기능 끄기; cards inside it do not.
    val feature = com.bamnat.app.ui.guide.LocalCardFeature.current
    if (feature != null) {
        androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.guide.LocalCardFeature provides null) {
            SectionCard(title, modifier, trailing = {
                trailing?.invoke()
                com.bamnat.app.ui.guide.FeatureMenuButton(feature)
            }, content = content)
        }
        return
    }
    if (LocalFlatSections.current) {
        Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (title != null || trailing != null) {
                // [BIGTXT-01] 1.39 A big font puts the button under the title instead of squeezing the title.
                SideOrBelow(Modifier.fillMaxWidth(), minStart = 80.dp, start = {
                    Text(title.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }, end = { trailing?.invoke() })
                Spacer(Modifier.padding(top = 4.dp))
            }
            content()
        }
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        // U 1.32 [UX132-06] 촘촘하게 (기본) trims the card's top and bottom; the sides stay 16dp for the finger.
        Column(Modifier.padding(horizontal = 16.dp, vertical = com.bamnat.app.ui.theme.LocalSpacing.current.inner)) {
            if (title != null || trailing != null) {
                // [BIGTXT-01] 1.39 A big font puts the button under the title instead of squeezing the title.
                SideOrBelow(Modifier.fillMaxWidth(), minStart = 80.dp, start = {
                    if (title != null) Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }, end = { trailing?.invoke() })
                Spacer(Modifier.padding(top = 10.dp))
            }
            content()
        }
    }
}

/** Number tile for hour totals. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
    /**
     * 2.0.0 [RVP-13] (검토 HQ-54, 조건 6) What the number means: [warning] alone is [Tone.Caution] (a thing to look at: 받을 돈, 마감 전);
     * a number gone wrong (넘김·밀림·법 위반) says [Tone.Danger]. The tile keeps the quiet ground: the mark, the edge and the number
     * carry the colour.
     */
    tone: Tone = if (warning) Tone.Caution else Tone.Plain,
    // 2.0.0 [RVK-25] (검토 MONEY-69, 조건 6) A caption that asks for care ('9일 전 가격') is written in the caution colour while
    // the tile and its number keep their own colours.
    captionTone: Tone = Tone.Plain,
) {
    val marked = tone == Tone.Caution || tone == Tone.Danger
    val ink = tone.color()
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (marked) ink.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marked) {
                    Icon(Icons.Filled.WarningAmber, if (tone == Tone.Danger) "위험" else "주의", tint = ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                // Three tiles share the narrow folded screen, so text shrinks instead of being cut off.
                BasicText(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp),
                )
            }
            // [UX-08] At a big font the value and the caption take two lines at a size that can be read, instead of
            // shrinking to 9sp on one (notes/1.22-285/E.md 4장, F.md 4장 3번).
            val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            BasicText(
                value,
                style = MaterialTheme.typography.titleLarge.merge(NumberStyle).copy(
                    color = if (marked) ink else MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = if (big) 2 else 1,
                autoSize = TextAutoSize.StepBased(minFontSize = if (big) 16.sp else 12.sp, maxFontSize = 22.sp),
            )
            if (caption != null) {
                BasicText(
                    caption,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (captionTone == Tone.Plain) MaterialTheme.colorScheme.onSurfaceVariant else captionTone.color(),
                    ),
                    // [VIS-05] Three lines at a big font: a half-width tile on a 320dp phone cut a caption's last line.
                    // 2.0.0 [RVP-11] (조건 4·12) The caption no longer shrinks to 9sp to stay on one line ('22~06시에 걸침 · 대근 2번
                    // 포함' on a 393dp phone): from 10sp down it takes a second line.
                    maxLines = if (big) 3 else 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = if (big) 11.sp else 10.sp, maxFontSize = 11.sp),
                )
            }
        }
    }
}

/**
 * A thing on this screen that needs the user: the one notice-card shape ([NudgeCard], 조건 3) with its mark in [tone]'s colour.
 * 2.0.0 [RVP-13] (검토 HQ-54): [Tone.Caution] for a thing to set or check (권한, 알림, 시세 없음); [Tone.Danger] only when something
 * has gone wrong (상한 넘김, 법 위반, 울리지 않을 알람).
 */
@Composable
fun WarningBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    /** [SLP-15] One button that fixes it, at the end of the banner. */
    action: String? = null,
    onAction: () -> Unit = {},
    tone: Tone = Tone.Caution,
) {
    NudgeCard(title, modifier, tone = tone, detail = message, action = action, onAction = onAction)
}

/**
 * − value + control for durations. [UX-01] The value sits in a fixed-width box, so 1시간 and
 * 1시간 30분 never move the buttons; holding − or + keeps stepping. Tapping the value allows exact
 * input to the minute.
 */
@Composable
fun MinutesStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 30,
    max: Int = 24 * 60,
    dialogTitle: String = "시간 입력",
    /** [UX-08] How the value reads ("1시간 30분" by default; a limit in hours can say "52시간"). */
    format: (Int) -> String = HoursFormat::korean,
) {
    var editing by remember { mutableStateOf(false) }
    // Shown value runs ahead of the database so quick repeated taps all count.
    var shown by remember { mutableStateOf(minutes) }
    // [STAB-01] Steps are written once the taps pause, not on every step of a hold (eleven a second):
    // each write redrew the calendar and recalculated pay until taps stopped answering.
    var unsaved by remember { mutableStateOf<Int?>(null) }
    val save by androidx.compose.runtime.rememberUpdatedState(onChange)
    androidx.compose.runtime.LaunchedEffect(minutes) { if (unsaved == null) shown = minutes }
    androidx.compose.runtime.LaunchedEffect(unsaved) {
        val v = unsaved ?: return@LaunchedEffect
        kotlinx.coroutines.delay(STEPPER_SAVE_DELAY_MILLIS)
        unsaved = null
        save(v)
    }
    // Leaving the screen right after a step still keeps it.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { unsaved?.let { save(it) } } }
    val set: (Int) -> Unit = { v ->
        shown = v
        unsaved = v
    }
    val haptics = LocalHapticFeedback.current
    // [VIS-02] A light tick per step, and a firmer one where it cannot go further.
    val stepTo: (Int) -> Unit = { v ->
        haptics.performHapticFeedback(if (v == 0 || v == max) HapticFeedbackType.Reject else HapticFeedbackType.SegmentFrequentTick)
        set(v)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RepeatIconButton(onStep = { stepTo((shown - step).coerceAtLeast(0)) }, enabled = shown > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "$dialogTitle ${HoursFormat.korean(step)} 줄이기")
        }
        val style = MaterialTheme.typography.titleMedium.merge(NumberStyle)
        Box(
            Modifier
                .width(STEPPER_VALUE_WIDTH)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = dialogTitle) { editing = true }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                format(shown),
                style = style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize),
            )
        }
        RepeatIconButton(onStep = { stepTo((shown + step).coerceAtMost(max)) }, enabled = shown < max) {
            Icon(Icons.Filled.Add, contentDescription = "$dialogTitle ${HoursFormat.korean(step)} 늘리기")
        }
    }
    if (editing) {
        DurationDialog(
            title = dialogTitle,
            initialMinutes = shown,
            maxMinutes = max,
            onDismiss = { editing = false },
            onConfirm = {
                shown = it
                unsaved = null
                onChange(it)
                editing = false
            },
        )
    }
}

/** Same width for every value, "0분" to "23시간 59분". */
private val STEPPER_VALUE_WIDTH = 104.dp

private const val STEPPER_SAVE_DELAY_MILLIS = 350L

/** A − or + button that steps once on a tap and keeps stepping while held. [UX-08] Shared with CountStepper. */
@Composable
internal fun RepeatIconButton(onStep: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val step by androidx.compose.runtime.rememberUpdatedState(onStep)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A hold already stepped; the release must not add one more.
    var repeated by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        // [JOBDAYS-01] 1.39 (PC a483ca15: 작업 기간 +를 두 번 눌렀는데 60일): a tap is one step; only a real hold repeats, slowly for
        // the first steps and quicker after, so a press a slow phone holds a moment too long adds a step or two, not dozens.
        kotlinx.coroutines.delay(550)
        var n = 0
        while (true) {
            repeated = true
            step()
            kotlinx.coroutines.delay(if (n++ < 5) 220 else 90)
        }
    }
    FilledTonalIconButton(
        onClick = { if (!repeated) step(); repeated = false },
        enabled = enabled,
        interactionSource = interaction,
    ) { content() }
}

/** [TIME-01] Hours and minutes on the same up-and-down wheels as clock times, instead of typing. */
@Composable
fun DurationDialog(
    title: String,
    initialMinutes: Int,
    maxMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val start = initialMinutes.coerceIn(0, maxMinutes)
    var hours by remember { mutableStateOf(start / 60) }
    var mins by remember { mutableStateOf(start % 60) }
    val total = hours * 60 + mins
    val valid = total <= maxMinutes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (valid) HoursFormat.korean(total) else "${HoursFormat.korean(maxMinutes)}까지 정할 수 있어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Box(Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT * 5), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(WHEEL_ROW_HEIGHT)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WheelHalf(count = maxMinutes / 60 + 1, value = hours, jump = 0, label = "시간", onValue = { hours = it }, cyclic = false)
                        WheelHalf(count = 60, value = mins, jump = 0, label = "분", onValue = { mins = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }, enabled = valid) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [J133-75][J133-77] A drag of the list puts the keyboard away, so the results under it and the tabs at the bottom can be
 * reached (중간 빌드 확인: 회사 찾기, 설정 찾기). Put it before the list's verticalScroll.
 */
@Composable
fun rememberHideKeyboardOnDrag(): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    return androidx.compose.runtime.remember(keyboard) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource):
                androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
}
