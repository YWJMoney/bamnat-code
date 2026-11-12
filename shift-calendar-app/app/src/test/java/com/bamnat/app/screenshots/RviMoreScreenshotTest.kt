package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 I, more of the screens it touches (총괄 #453: 수면·밤 화면·메모 빈 화면·휴지통·새 메모 고르기·짧은 잠), each in the
 * eight looks of the design quality table, as [RviScreenshotTest] does for the memo editor and the alarm settings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RviMoreScreenshotTest : ScreenshotBase() {
    private class Look(val tag: String, val qualifiers: String, val font: Float, val wide: Boolean)

    private val looks = listOf(
        Look("l100f", FOLDED, 1f, false), Look("l200f", FOLDED, 2f, false),
        Look("d100f", FOLDED_NIGHT, 1f, false), Look("d200f", FOLDED_NIGHT, 2f, false),
        Look("l100u", UNFOLDED, 1f, true), Look("l200u", UNFOLDED, 2f, true),
        Look("d100u", UNFOLDED_NIGHT, 1f, true), Look("d200u", UNFOLDED_NIGHT, 2f, true),
    )

    private fun eight(name: String, actions: Screen.(wide: Boolean) -> Unit) {
        for (l in looks) shoot("rvi_${name}_${l.tag}", l.qualifiers, l.font) { actions(l.wide) }
    }

    private val c get() = app.container

    /** A week of nights around 주간·야간, as the store shots have. */
    private fun sleepWeek() = runBlocking {
        // The app (and its data) lasts the whole test, eight shots: the week goes in once.
        if (c.sleepRepository.recent(days = 30).first().isNotEmpty()) return@runBlocking
        listOf(7 to (23 to 30 to (6 to 20)), 6 to (23 to 50 to (6 to 30)), 5 to (9 to 10 to (15 to 40)), 4 to (9 to 0 to (16 to 10)),
            3 to (22 to 40 to (7 to 30)), 2 to (23 to 20 to (6 to 25)), 1 to (23 to 40 to (6 to 15))).forEach { (back, t) ->
            val (from, to) = t
            val (fh, fm) = from
            val (th, tm) = to
            val wake = today.minusDays(back.toLong() - 1)
            val start = if (fh >= 18) wake.minusDays(1).atTime(fh, fm) else wake.atTime(fh, fm)
            c.sleepRepository.add(start, wake.atTime(th, tm))
        }
    }

    private fun Screen.sleepScreen(wide: Boolean) {
        sleepWeek()
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = wide, onBack = {}) }
        settle()
    }

    /** No general memos, and the calendar's kind of work set to [style]: the 일반 메모 tab's empty screen. */
    private fun Screen.emptyGeneral(style: com.bamnat.core.work.WorkStyle, wide: Boolean) {
        runBlocking {
            c.settingsRepository.update { it.copy(workStyle = style) }
            c.memoRepository.memos.first().filter { it.date == null }.forEach { c.memoRepository.delete(it) }
        }
        settle()
        tab(2, wide)
        tapText("일반 메모", prefix = true)
    }

    // [RVI-58] 접은 폭은 '오늘·패턴·기록' 세 부분, [RVI-60] 깨우는 구간은 재지 않을 때 한 줄.
    @Test fun sleepToday() = eight("sleep_today") { wide -> sleepScreen(wide) }
    @Test fun sleepPattern() = eight("sleep_pattern") { wide -> sleepScreen(wide); if (!wide) tapText("패턴", exact = true, scrolls = 0) }

    // [RVI-59] 재는 중: '늦어도 07:30에 멈춰요'와 '측정 끝', [RVI-69] 잠드는 소리 '켜기'는 테두리. 채운 단추는 '깼어요' 하나.
    @Test fun nightScreen() = try {
        eight("night") { _ ->
            runBlocking { c.sleepRepository.fellAsleep() }
            val stop = today.plusDays(1).atTime(7, 30).atZone(com.bamnat.core.time.Today.zone()).toInstant().toEpochMilli()
            com.bamnat.app.sleep.SleepRecordService.showRecording(com.bamnat.app.sleep.SleepRecordService.Recording(com.bamnat.core.time.Today.millis(), 3, stop))
            val vm = com.bamnat.app.ui.sleep.NightViewModel(c)
            content { com.bamnat.app.ui.sleep.NightScreen(vm, {}, {}) }
            settle()
            assertTrue(appears("늦어도 오전 7:30에 멈춰요") || appears("늦어도 07:30에 멈춰요"))
            assertTrue(appears("측정 끝", exact = true))
        }
    } finally {
        // After the shots: the recording has to be on screen when each is taken.
        com.bamnat.app.sleep.SleepRecordService.showRecording(null)
    }

    // [RVI-70] 빈 화면 부품(칸이 + 한 줄), 일반 메모 예시는 유형마다.
    @Test fun memoEmptyShop() = eight("memo_empty_shop") { wide -> emptyGeneral(com.bamnat.core.work.WorkStyle.BUSINESS, wide) }
    @Test fun memoEmptyInvest() = eight("memo_empty_invest") { wide -> emptyGeneral(com.bamnat.core.work.WorkStyle.INVEST, wide) }

    // [RVI-72] 휴지통 메모를 눌러 읽기 전용 창.
    @Test fun memoTrashRead() = eight("memo_trash") { wide ->
        tab(2, wide)
        tapText("휴지통", prefix = true)
        tapText("지난달 회식 장소")
    }

    // [RVI-61] '새 메모'를 길게 누르면 메모·체크리스트 고르기 (TalkBack의 길게 누르기 동작과 같은 길).
    @Test fun memoNewChoose() = eight("memo_new_choose") { wide ->
        tab(2, wide)
        val fab = nodes().first { n ->
            n.config.contains(SemanticsActions.OnLongClick) &&
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.endsWith("새 메모") }
        }
        fab.config[SemanticsActions.OnLongClick].action!!.invoke()
        settle()
        assertTrue(inPopup("체크리스트") && inPopup("메모"))
    }

    // [RVI-71] 짧은 잠 뒤 '깼어요': 달력과 수면 화면이 같은 말.
    @Test fun shortSleepCalendar() = eight("short_sleep_calendar") { _ ->
        runBlocking { c.sleepRepository.fellAsleep() }
        settle()
        // 오늘의 흐름 says 자는 중 with 깼어요; over the month it is one line until opened.
        if (!appears("깼어요", exact = true, ms = 500)) runCatching { tapAction("오늘의 흐름 펼치기") }
        tapText("깼어요", exact = true)
        assertTrue(appears("1분 잤어요", exact = false))
    }
    @Test fun shortSleepSleep() = eight("short_sleep_sleep") { wide ->
        runBlocking { c.sleepRepository.fellAsleep() }
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = wide, onBack = {}) }
        settle()
        tapText("깼어요", exact = true)
        // [RVI-87] The night look ends with the sleep; the line and its 되돌리기 stay.
        assertTrue(appears("1분 잤어요", exact = false))
        assertTrue(appears("되돌리기", exact = true))
    }

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
