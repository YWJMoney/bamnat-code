package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.memo.Memo
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 I 2묶음 (검토 03 알람·위젯·수면·워치, 07a 메모·할 일, PC-75·83·89): the screens it touches, in the eight looks of the
 * design quality table (밝은·어두운 × 글자 100%·200% × 접은 폭·편 폭). Only taps and seeded data, and a step the screen before the
 * change does not have is skipped, so the same file shoots the screens before the change too (the PR's 앞·뒤 사진).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvibScreenshotTest : ScreenshotBase() {
    private class Look(val tag: String, val qualifiers: String, val font: Float, val wide: Boolean)

    private val looks = listOf(
        Look("l100f", FOLDED, 1f, false), Look("l200f", FOLDED, 2f, false),
        Look("d100f", FOLDED_NIGHT, 1f, false), Look("d200f", FOLDED_NIGHT, 2f, false),
        Look("l100u", UNFOLDED, 1f, true), Look("l200u", UNFOLDED, 2f, true),
        Look("d100u", UNFOLDED_NIGHT, 1f, true), Look("d200u", UNFOLDED_NIGHT, 2f, true),
    )

    private fun eight(name: String, actions: Screen.(wide: Boolean) -> Unit) {
        for (l in looks) shoot("rvib_${name}_${l.tag}", l.qualifiers, l.font) { actions(l.wide) }
    }

    private val c get() = app.container

    /** The first of [words] the screen shows, tapped: the same button kept other words before the change. */
    private fun Screen.tapFirst(vararg words: String) {
        val word = words.firstOrNull { shows(it) } ?: words.first()
        tapText(word, prefix = true)
    }

    /**
     * As a person finds a memo in a long list: by the search box, which brings it to the top, then a tap on its title (the
     * search box holds the same word, and a card's middle can be a checklist line that ticks).
     */
    private fun Screen.openMemo(search: String, line: String, @Suppress("UNUSED_PARAMETER") wide: Boolean) {
        typeInto("제목, 내용, #태그 검색", search)
        tapLowest(line, merged = false)
    }

    // [RVI-77] 맨 위 '오늘' 묶음, [RVI-74] 카드 왼쪽 동그라미로 끝내기, [RVI-80] '오래된 할 일 N개' 한 줄.
    @Test fun memoTab() = eight("memo_tab") { wide -> tab(2, wide) }

    // [RVIB-02] 체크리스트 줄을 누르면 '별표 달고 위로'·'시각'·'메모', 별표 단 줄은 맨 위로.
    @Test fun checklistLine() = eight("checklist_line") { wide ->
        tab(2, wide)
        openMemo("준비물", "준비물", wide)
        tapText("도시락", exact = true)
        if (shows("별표 달고 위로")) tapText("별표 달고 위로")
    }

    // [RVIB-08] '매달 25일 월세 50만 원' 한 줄 제안: 반복 메모 + 가계부 고정 지출을 한 번에.
    @Test fun moneyLine() = eight("money_line") { wide ->
        runBlocking {
            if (c.memoRepository.memos.first().none { it.title == "이번 달 집세" }) {
                c.memoRepository.save(Memo(title = "이번 달 집세", body = "매달 25일 월세 50만 원 이체하기"))
            }
        }
        settle()
        tab(2, wide)
        // A memo without a day sits under 일반 메모.
        tapText("일반 메모", prefix = true)
        openMemo("집세", "이번 달 집세", wide)
    }

    // [RVI-73] 반복을 고르는 곳 하나: 여러 요일·격주·둘째 수요일·N일마다.
    @Test fun repeatPicker() = eight("repeat_picker") { wide ->
        tab(2, wide)
        openMemo("건강검진", "건강검진 오전 9시", wide)
        tapFirst("반복 없음", "반복")
    }

    // [RVIB-04] 알림 창의 '근무에 붙여 매번'(출근 1시간 전·퇴근 30분 뒤·야간 끝나고 일어나면), [RVI-79] 처음 시각은 퇴근 뒤.
    @Test fun scheduleWork() = eight("schedule_work") { wide -> scheduleFor(null, wide) }

    // [RVIB-04] 유형마다 제 말: 가게 '영업 시간에 붙여 매번', 투자 '장 시간에 붙여 매번', 현장 '일정에 붙여 매번'.
    @Test fun scheduleWorkKinds() {
        for (style in listOf(WorkStyle.BUSINESS, WorkStyle.INVEST, WorkStyle.FIELD)) {
            shoot("rvib_schedule_work_${style.name.lowercase()}_l100f", FOLDED, 1f) { scheduleFor(style, false) }
        }
    }

    private fun Screen.scheduleFor(style: WorkStyle?, wide: Boolean) {
        if (style != null) runBlocking { c.settingsRepository.update { it.copy(workStyle = style) } }
        settle()
        tab(2, wide)
        openMemo("건강검진", "건강검진 오전 9시", wide)
        // Before the change the offer's button read '알림 넣기, …'; after it, the editor's '알림 넣기' opens the same window.
        if (shows("알림 넣기, ")) tapText("알림 넣기, ", prefix = true) else tapText("알림 넣기", exact = true)
        val heading = listOf("근무에 붙여 매번", "영업 시간에 붙여 매번", "장 시간에 붙여 매번", "일정에 붙여 매번").firstOrNull { scrolledTo(it, exact = true) }
        if (heading != null) reveal(heading, exact = true)
    }

    // [RVIB-06] (PC-83) 알람 추가 '시작 기준'에서 '1시간 전'(PC는 '1시간 30분 전'도)을 누르면 줄 글·바퀴가 함께 움직임.
    @Test fun alarmOffset() = eight("alarm_offset") { wide ->
        settingsPage("내 근무", wide)
        tapText("근무 종류와 시간", exact = true, across = if (wide) 0.75f else 0.5f)
        tapText("주간", exact = true, across = if (wide) 0.75f else 0.5f)
        reveal("알람 추가", across = if (wide) 0.75f else 0.5f)
        tapText("알람 추가", across = if (wide) 0.75f else 0.5f)
        tapText("시작 기준", exact = true)
        tapText("1시간 전", exact = true)
    }

    // [RVIB-07] (PC-89) 수면 기록: '날마다 잠'(이번 주 띠)이 맨 위.
    @Test fun sleepRecord() = eight("sleep_record") { wide ->
        runBlocking {
            if (c.sleepRepository.recent(days = 30).first().isEmpty()) {
                listOf(7 to (23 to 30), 6 to (23 to 50), 5 to (9 to 10), 4 to (9 to 0), 3 to (22 to 40), 2 to (23 to 20), 1 to (23 to 40)).forEach { (back, t) ->
                    val wake = today.minusDays(back.toLong() - 1)
                    val start = if (t.first >= 18) wake.minusDays(1).atTime(t.first, t.second) else wake.atTime(t.first, t.second)
                    c.sleepRepository.add(start, if (t.first >= 18) wake.atTime(6, 20) else wake.atTime(15, 40))
                }
            }
        }
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = wide, onBack = {}) }
        settle()
        if (!wide) tapText("기록", exact = true, scrolls = 0)
    }

    // [RVIB-11] (PC-75, ★ 출근 알람) 쉬는 날이 된 날의 '알람을 끌까요?': 그날이 원래 무슨 근무였는지.
    @Test fun restAlarmAsk() = eight("rest_alarm") { _ ->
        // Each look starts from the cycle's day: the look before left it 휴무, and a tap on 휴무 again asks nothing.
        runBlocking {
            c.settingsRepository.update { it.copy(alarmsEnabled = true) }
            c.scheduleRepository.resetDay(today.plusDays(1))
        }
        settle()
        openDay(today.plusDays(1))
        tapText("휴무", exact = true)
    }

    // [RVI-86] 알람 점검의 이번 주 알람, [RVI-92] 마지막 시험 결과, [RVI-91] 설정 › 알람의 예행 연습 줄.
    @Test fun alarmCheck() = eight("alarm_check") { wide ->
        settingsPage("알람", wide)
        tapText("알람 점검", across = if (wide) 0.75f else 0.5f)
    }

    // [RVIB-05] 설정 › 알림과 잠 › 메모·할 일: 근무 중 메모 알림 모으기·쉬는 날 아침 지난 할 일, [RVI-96] 충전하면 잠 잡기.
    @Test fun noticeRows() = eight("notice_rows") { wide ->
        settingsPage("알림과 잠", wide)
        reveal("메모·할 일", exact = true, across = if (wide) 0.75f else 0.5f)
    }

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
