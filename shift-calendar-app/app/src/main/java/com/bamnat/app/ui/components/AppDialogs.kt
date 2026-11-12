package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/*
 * 1.36 PC 재검토 (대화 상자에 앱 글자 크기가 안 먹어요): a dialog is a window of its own, and a new window measures with the phone's
 * density, so the app's 글자 크기 (설정 › 화면과 글자, laid over the phone's in AppTheme) was lost inside every 창. These stand in for
 * Material's and Compose's with the same arguments and hand the app's density, taken where the dialog is opened, back to what is drawn
 * in it. The app imports these instead of androidx.compose.material3.AlertDialog / DatePickerDialog / DropdownMenu and
 * androidx.compose.ui.window.Dialog (AppDialogsTest keeps it that way). A menu is a popup window, the same.
 */

/** [slot] drawn with [density] (the app's, from outside the dialog's window). */
private fun keep(density: Density, slot: @Composable () -> Unit): @Composable () -> Unit =
    { CompositionLocalProvider(LocalDensity provides density, slot) }

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val d = LocalDensity.current
    StaysUnderTheLock()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = keep(d, confirmButton),
        modifier = modifier,
        dismissButton = dismissButton?.let { keep(d, it) },
        icon = icon?.let { keep(d, it) },
        title = title?.let { keep(d, it) },
        text = text?.let { keep(d, it) },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

@Composable
fun Dialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {
    val d = LocalDensity.current
    StaysUnderTheLock()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties, content = keep(d, content))
}

/**
 * 1.39.1 [R391-10] (검토 UIX-41) A 창 or 시트 is a window over the activity: drawn inside the activity, 앱 잠금's cover stayed under
 * it, and 급여 대장 or 돈 적기 left open showed over the cover. The cover is a window of its own now ([LockWindow]); one opened
 * while locked (a notice's 돈 적기) tells it to come back on top.
 */
@Composable
internal fun StaysUnderTheLock() {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.bamnat.app.AppLock.windowOpened() }
}

/** 1.39.1 [R391-10] 앱 잠금's cover as a full-screen window over every 창 and 시트; [onBack] leaves the app. */
@Composable
fun LockWindow(onBack: () -> Unit, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
        content = keep(d, content),
    )
}

/** The current context with a Korean locale, for Material parts that word themselves (the date picker). */
@Composable
private fun koreanContext(): android.content.Context {
    val base = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(base, config) {
        if (config.locales[0]?.language == "ko") base
        else object : android.content.ContextWrapper(base) {
            private val res = base.createConfigurationContext(android.content.res.Configuration(config).apply { setLocale(java.util.Locale.KOREA) }).resources
            override fun getResources(): android.content.res.Resources = res
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val d = LocalDensity.current
    StaysUnderTheLock()
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = keep(d, confirmButton),
        modifier = modifier,
        dismissButton = dismissButton?.let { keep(d, it) },
        shape = shape,
        tonalElevation = tonalElevation,
        colors = colors,
        properties = properties,
    ) {
        // 1.38 PC 11차: Material's dialog puts content in a Box, so a row above the picker (YearJump) was drawn under it.
        // 1.39 PC 5차: on a phone set to English the picker said "Select date"·"January 2000" in an app that is Korean
        // everywhere else; the picker reads its words from the context's resources and its month names from the
        // configuration's locale, so both are Korean here (localeFilters keeps only ko, which Material ships).
        val ko = koreanContext()
        CompositionLocalProvider(
            LocalDensity provides d,
            androidx.compose.ui.platform.LocalContext provides ko,
            androidx.compose.ui.platform.LocalConfiguration provides ko.resources.configuration,
        ) { androidx.compose.foundation.layout.Column { content() } }
    }
}

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    // PC 확인 1.36 (두 폰 '응답 없음', 2026-11-03): every calendar cell, list row and chip carries its own menu, and
    // material3's menu builds a transition state and a scroll state even while closed; a month page of closed menus was
    // the main thread's work when Android called the app stuck. A menu is built only once it has been opened (and kept
    // after, so it can still fade out).
    val opened = remember { BooleanArray(1) }
    if (expanded) opened[0] = true
    if (!opened[0]) return
    val d = LocalDensity.current
    androidx.compose.material3.DropdownMenu(expanded, onDismissRequest, modifier, offset, scrollState ?: rememberScrollState(), properties) {
        CompositionLocalProvider(LocalDensity provides d) { content() }
    }
}

/**
 * 1.38 PC 10차 (53): 입사일처럼 지난 해를 고를 때 달을 몇십 번 넘기지 않게, 올해·1년 전…을 한 번에 펼쳐요.
 * It moves what the picker shows to the same month in that year, and the day already picked to the same day of that year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearJump(state: androidx.compose.material3.DatePickerState, back: Int = 5) {
    val now = com.bamnat.core.time.Today.date()
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        (0..back).forEach { y ->
            val shown = java.time.LocalDate.ofEpochDay(state.displayedMonthMillis / 86_400_000L).year
            val year = now.year - y
            FilterChip(selected = shown == year, onClick = {
                val month = java.time.LocalDate.ofEpochDay(state.displayedMonthMillis / 86_400_000L).withYear(year).withDayOfMonth(1)
                state.displayedMonthMillis = month.toEpochDay() * 86_400_000L
                // 1.39 G2 PC 4bd5d745: the day picked moves to that year too. Only the calendar moved, so '고르기' kept this year's date
                // (입사일 2026.11.7 for 2025.11.7) and the ledger and 신고 일정 followed the wrong year.
                state.selectedDateMillis?.let { java.time.LocalDate.ofEpochDay(it / 86_400_000L) }?.let { picked ->
                    val moved = picked.withYear(year).toEpochDay() * 86_400_000L
                    if (state.selectableDates.isSelectableDate(moved)) state.selectedDateMillis = moved
                }
            }, label = { androidx.compose.material3.Text(if (y == 0) "올해" else "${y}년 전 ($year)") })
        }
    }
}
