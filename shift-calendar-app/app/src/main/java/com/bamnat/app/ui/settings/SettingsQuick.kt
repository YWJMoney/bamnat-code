package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 2.0.0 [RVO-21] A setting settings can show and put back by itself: its row's name (as 설정 찾기 finds it), how to read it and how to
 * take it from another [AppSettings]. [on] is set for a plain switch, which 설정 찾기 then turns on and off in its result
 * (검토 U11a-03·I11aa-02: 찾기 → 들어가기 → 스위치 세 번이 한 번). Only settings whose row does nothing more than save the value are here
 * (no permission, no widget to redraw), so the result's switch and the row's are the same.
 */
internal class Watched(
    val row: String,
    val read: (AppSettings) -> Any?,
    val put: (into: AppSettings, from: AppSettings) -> AppSettings,
    val on: ((AppSettings, Boolean) -> AppSettings)? = null,
    val say: (AppSettings) -> String = { s -> read(s)?.let { v -> if (v is Boolean) (if (v) "켬" else "끔") else v.toString() }.orEmpty() },
)

internal object WatchedSettings {
    private fun switch(row: String, read: (AppSettings) -> Boolean, write: (AppSettings, Boolean) -> AppSettings) =
        Watched(row, read, { into, from -> write(into, read(from)) }, write)

    val all: List<Watched> = listOf(
        switch("촘촘하게", { it.compact }) { s, v -> s.copy(compact = v) },
        switch("오전/오후로 보기", { it.clock12 }) { s, v -> s.copy(clock12 = v) },
        switch("칸이 보이기", { it.showKani }) { s, v -> s.copy(showKani = v) },
        switch("하루 이야기 줄", { it.dayStory }) { s, v -> s.copy(dayStory = v) },
        switch("하루 다이얼", { it.dayDial }) { s, v -> s.copy(dayDial = v) },
        switch("날짜 밑에 그날 쓴 돈", { it.spendInCells }) { s, v -> s.copy(spendInCells = v) },
        switch(SPEND_HEAT_ROW, { it.spendHeat }) { s, v -> s.copy(spendHeat = v) },
        switch("달력만 보기", { it.calendarOnly }) { s, v -> s.copy(calendarOnly = v, viewAsked = true) },
        switch("휴대폰 배경색에 맞추기", { it.dynamicColor }) { s, v -> s.copy(dynamicColor = v) },
        switch("금액 가리기", { it.hideAmounts }) { s, v -> s.copy(hideAmounts = v) },
        switch("자는 동안 미루기", { it.quietWhileAsleep }) { s, v -> s.copy(quietWhileAsleep = v) },
        switch("광장 시세와 내 단가 견주기", { it.rateHint }) { s, v -> s.copy(rateHint = v) },
        // Choices: put back as they were, not turned from a result.
        Watched("글자 크기", { it.textStep }, { into, from -> into.copy(textStep = from.textStep) }),
        Watched("테마", { it.themeMode }, { into, from -> into.copy(themeMode = from.themeMode) }, say = { s ->
            when (s.themeMode) { ThemeMode.SYSTEM -> "휴대폰 따라"; ThemeMode.LIGHT -> "밝게"; ThemeMode.DARK -> "어둡게"; ThemeMode.NIGHT_SHIFT -> "밤에 어둡게" }
        }),
        Watched("한 주의 시작", { it.weekStart }, { into, from -> into.copy(weekStart = from.weekStart) },
            say = { s -> if (s.weekStart == java.time.DayOfWeek.MONDAY) "월요일" else "일요일" }),
        Watched(DAY_TAP_ROW, { DayTap.of(it) }, { into, from -> DayTap.of(from).applied(into) }, say = { s -> DayTap.of(s).label }),
        Watched(LONG_PRESS_ROW, { it.longPress }, { into, from -> into.copy(longPress = from.longPress) }, say = { s ->
            when (s.longPress) { "memo" -> "바로 메모"; "open" -> "하루 열기"; else -> "빠른 메뉴" }
        }),
        Watched("메모 보기", { it.calendarMemos to it.memoInCells }, { into, from -> into.copy(calendarMemos = from.calendarMemos, memoInCells = from.memoInCells) },
            say = { s -> when { s.memoInCells -> "날짜 밑 메모"; s.calendarMemos -> "달력 아래 목록"; else -> "기본" } }),
        Watched("날짜 칸 아래", { it.showLunar to it.showHoursInCells }, { into, from -> into.copy(showLunar = from.showLunar, showHoursInCells = from.showHoursInCells) },
            say = { s -> when { s.showLunar && s.showHoursInCells -> "둘 다"; s.showLunar -> "음력"; s.showHoursInCells -> "시간"; else -> "비움" } }),
        Watched("하루 시작 시각", { it.dayStartMinutes }, { into, from -> into.copy(dayStartMinutes = from.dayStartMinutes) },
            say = { s -> if (s.dayStartMinutes == 0) "자정" else com.bamnat.app.ui.Fmt.time(java.time.LocalTime.of(s.dayStartMinutes / 60, s.dayStartMinutes % 60)) }),
        Watched(MOTION_OFF_ROW, { com.bamnat.core.recipe.Motion.decode(it.motion).off },
            { into, from -> into.copy(motion = com.bamnat.core.recipe.Motion.decode(into.motion).copy(off = com.bamnat.core.recipe.Motion.decode(from.motion).off).encode()) },
            { s, v -> s.copy(motion = com.bamnat.core.recipe.Motion.decode(s.motion).copy(off = v).encode()) }),
    )

    /** The switch a found row can turn from its result, if it is one. */
    fun switchOf(row: String): Watched? = all.firstOrNull { it.row == row && it.on != null }
}

/**
 * 2.0.0 [RVO-21] 검토 I11aa-03 ('방금 바꾼 것을 되돌리려는 일이 가장 흔해요'): the last three settings changed on the settings screens,
 * each with the value before its first change, kept while the app is open. Changing one back takes it off the list.
 */
internal object RecentSettings {
    class Change(val watched: Watched, val before: AppSettings, val now: String)

    private val _changes = MutableStateFlow<List<Change>>(emptyList())
    val changes: StateFlow<List<Change>> = _changes.asStateFlow()
    /** Whose settings the list is of (the app's settings store): another one (a new app, a test) starts it empty. */
    @Volatile private var owner: Any? = null

    fun of(owner: Any?): List<Change> = if (owner === this.owner) _changes.value else emptyList()

    fun note(owner: Any, old: AppSettings, new: AppSettings) {
        if (owner !== this.owner) { this.owner = owner; _changes.value = emptyList() }
        if (old == new) return
        var list = _changes.value
        for (w in WatchedSettings.all) {
            if (w.read(old) == w.read(new)) continue
            val first = list.firstOrNull { it.watched.row == w.row }
            val before = first?.before ?: old
            list = list.filterNot { it.watched.row == w.row }
            if (w.read(before) != w.read(new)) list = listOf(Change(w, before, w.say(new))) + list
        }
        _changes.value = list.take(MAX)
    }

    /** Puts [c]'s setting back as it was ([AppSettings] now, with that one value from before). */
    fun undo(c: Change, current: AppSettings): AppSettings = c.watched.put(current, c.before)

    fun clear() { _changes.value = emptyList() }

    const val MAX = 3
}

/** 2.0.0 [RVO-21] 설정 첫 화면의 '방금 바꾼 것': up to three lines, each with its value now and 되돌리기. */
@Composable
internal fun RecentChangesCard(owner: Any, onUndo: (RecentSettings.Change) -> Unit, modifier: Modifier = Modifier) {
    val all by RecentSettings.changes.collectAsState()
    val changes = if (all.isEmpty()) all else RecentSettings.of(owner)
    if (changes.isEmpty()) return
    val style = LocalSettingsStyle.current
    SectionCard(title = "방금 바꾼 것", modifier = modifier) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            changes.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${com.bamnat.core.work.TypeWords.say(style, c.watched.row)} · ${c.now}", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp))
                    TextButton(onClick = { onUndo(c) }) { Text("되돌리기") }
                }
            }
        }
    }
}
