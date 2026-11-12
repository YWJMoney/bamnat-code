package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.AlarmReceiver
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.invest.invest
import com.bamnat.app.ui.Fmt
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.pay.PayCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 2.0.0 [RVR-40] 검토 I60c-03: PC 확인을 대신하는 '누르는' 시험 묶음. 근무 유형마다 사람이 하는 차례 그대로 화면을 눌러요:
 * 첫 설정 → 달력 → 하루 근무 바꾸기 → 그날 알람 시각 바꾸기(시간 바퀴) → 급여 설정·보기 → 근무를 또 바꾸면 급여도 따라와요.
 * 단계마다 '화면에 보이는 값 = 저장된 값'을 봐요(알람은 실제로 예약된 시각까지). 시간 바퀴·알람 창처럼 보이는 값과 저장 값이
 * 어긋나는 문제를 PR에서 잡으려는 것이라, 값은 저장소·계산기에서 따로 읽어 화면 글자와 맞춰요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UserFlowScreenshotTest : ScreenshotBase() {
    private val scope get() = app.container.scopeOf(PersonalCalendar.MAIN)
    private fun snap(): ScheduleSnapshot = runBlocking { scope.schedule.snapshot.first() }
    private val month get() = YearMonth.from(today)
    private val seoul = ZoneId.of("Asia/Seoul")
    private val bottom = listOf("저장하고 달력 보기", "회사 근무표와 같아요", "네, 맞아요", "네, 시간이 맞아요", "건너뛰고 직접 정할게요", "다음")

    // ---- what the screen shows ----

    private fun Screen.words(): List<String> = nodes().filter { it.boundsInWindow.width > 1f }.flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    /** The calendar cell of [d] as it is read out: "10월 14일 (수), 야간 11시간, …". */
    private fun Screen.cell(d: LocalDate): String {
        val head = Fmt.dayTitle(d) + ","
        return words().firstOrNull { it.startsWith(head) } ?: error("달력에 ${Fmt.dayTitle(d)} 칸이 안 보여요")
    }

    /** The first amount in won shown after [label] (the label's own line, then the lines after it). */
    private fun Screen.wonAfter(label: String): Long {
        val w = words()
        val at = w.indexOfFirst { it == label }.takeIf { it >= 0 } ?: error("‘$label’이 안 보여요: ${w.take(40)}")
        val money = Regex("""^([0-9,]+)원$""")
        val shown = w.drop(at + 1).take(4).firstNotNullOfOrNull { money.find(it.trim())?.groupValues?.get(1) } ?: error("‘$label’ 뒤에 금액이 없어요: ${w.drop(at).take(6)}")
        return shown.replace(",", "").toLong()
    }

    /** 을/를 같은 받침 가리기: "야간으로", "휴무로" ("ㄹ" 받침도 로). */
    private fun ro(name: String): String {
        val last = name.last()
        if (last !in '가'..'힣') return "${name}로"
        val final = (last - '가') % 28
        return if (final == 0 || final == 8) "${name}로" else "${name}으로"
    }

    // ---- what is stored ----

    private fun shiftOn(d: LocalDate): String? = snap().resolver.resolve(d).shiftType?.name

    /** The shift alarms the phone has really booked for [d], as wall-clock times in Seoul. */
    private fun bookedOn(d: LocalDate): List<LocalTime> =
        shadowOf(app.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms
            .filter { shadowOf(it.operation).savedIntent.action == AlarmReceiver.ACTION_SHIFT_ALARM }
            .map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAtTime), seoul) }
            .filter { it.toLocalDate() == d }.map { it.toLocalTime() }

    /** The month's pay worked out again from what is stored (the profile the setup saved and the days as they are). */
    private fun payStored(): Long {
        val profile = scope.pay.profile.value
        assertNotNull("급여 설정이 저장됐어요", profile)
        return PayCalculator().calculate(profile!!, month, snap().resolver::resolve).total
    }

    private fun clear(d: LocalDate) = runBlocking { scope.schedule.restoreDay(d, null) }

    /** A new phone has no day written yet: the shared screenshots' seeded days (휴가, 대근, 바꾼 근무) are taken out first. */
    private fun noDaysWritten() = runBlocking { snap().entries.forEach { scope.schedule.restoreDay(it.date, null) } }

    // ---- steps a person takes ----

    /** The setup's bottom button, again and again, until the calendar is there. */
    private fun Screen.finishSetup() {
        repeat(8) {
            val next = bottom.firstOrNull { shows(it, exact = true) && enabled(it) } ?: return
            tapText(next, exact = true)
            settle()
        }
    }

    /** Opens [d] and changes its work to [to] with the day sheet's chips; checks the sheet, the store and the cell agree. */
    private fun Screen.changeDay(d: LocalDate, to: String) {
        openDay(d)
        assertTrue("${Fmt.dayTitle(d)}이 열려요", appears(Fmt.dayTitle(d), exact = true))
        tapText("${ro(to)} 바꾸기", exact = true)
        settle()
        // A working day turned off asks once what its alarm does: this person turns it off, and nothing rings then.
        if (shows("알람을 끌까요?", exact = true)) {
            tapText("알람 끄기", exact = true)
            settle()
            assertTrue("쉬는 날로 바꾸고 끈 알람은 저장돼요", snap().resolver.entryOn(d)?.alarmSkipped == true)
            assertTrue("그날은 휴대폰에 알람이 잡히지 않아요: ${bookedOn(d)}", bookedOn(d).isEmpty())
        }
        assertTrue("고른 칩이 '$to'로 보여요: ${words().take(24)}", appears("$to, 이날 골라 둔 것", exact = true))
        // Back to the order's own shift keeps no change for the day (the order says it); anything else is the day's own.
        val own = snap().resolver.entryOn(d)?.override
        assertTrue("바꾼 날이 저장돼요: $own", own != null && own != ShiftOverride.None || snap().resolver.patternShiftIdOn(d)?.let { id -> snap().shiftTypes.first { it.id == id }.name } == to)
        assertEquals("저장된 근무가 화면과 같아요", to, shiftOn(d) ?: "휴무")
        tapText("닫기", exact = true)
        settle()
        assertTrue("달력 칸도 '$to'예요: ${cell(d)}", to in cell(d))
    }

    /**
     * Opens [d]'s alarm, turns its first alarm (or a new one) 10 minutes later on the time wheel, and saves: the wheel's line,
     * the alarm window, the day sheet, the stored day and the alarm the phone really booked all say the same time.
     */
    private fun Screen.alarmLater(d: LocalDate): LocalTime {
        openDay(d)
        if (!shows("이 날만 알람 정하기", exact = true) && !shows("이 날 알람 고치기", exact = true)) tapText("알람", exact = true)
        tapText(if (shows("이 날 알람 고치기", exact = true)) "이 날 알람 고치기" else "이 날만 알람 정하기", exact = true)
        assertTrue("알람 창이 열려요", appears("${Fmt.dayTitle(d)} 알람", exact = true))
        val first = words().firstOrNull { Regex("""^\d\d:\d\d 알람$""").matches(it) }?.removeSuffix(" 알람")
        if (first != null) tapText(first, exact = true) else tapText("알람 추가", exact = true)
        tapText("+10분", exact = true)
        settle()
        val change = words().firstNotNullOfOrNull { Regex("""(\d\d:\d\d) → (\d\d:\d\d)""").find(it) }
            ?: error("시간 바퀴가 바뀐 시각을 말해요: ${words()}")
        val (from, to) = change.destructured
        if (first != null) assertEquals("바퀴는 창에 보이던 시각에서 시작해요", first, from)
        tapText("완료", exact = true) // the wheel
        settle()
        assertTrue("알람 창에 ${to}", shows("$to 알람", exact = true))
        tapText("완료", exact = true) // the day's alarms
        settle()
        val at = LocalTime.parse(to)
        assertTrue("하루 화면에 '${to}에 울려요'", appears("${to}에 울려요 · 직접 정함", exact = true))
        val stored = snap().resolver.entryOn(d)?.customAlarms.orEmpty().filter { it.enabled }.map { it.time }
        assertTrue("저장된 알람에 ${to}: $stored", at in stored)
        assertTrue("휴대폰에 실제로 ${to} 알람이 잡혀요: ${bookedOn(d)}", at in bookedOn(d))
        tapText("닫기", exact = true)
        settle()
        return at
    }

    /** 근무·급여 › 급여 계산하기, the type's suggestion, [basePay], the summary's 'as it is': returns the pay the summary showed. */
    private fun Screen.setUpPay(payTab: String, basePay: String, question: String = "월 기본급이 얼마예요?", field: String = "월 기본급"): Long {
        tapText(payTab, exact = true)
        settle()
        tapText("근무표로 급여 계산하기", exact = true)
        if (appears("직접 만들게요", exact = true)) tapText("직접 만들게요", exact = true)
        var typed = false
        repeat(8) {
            settle()
            if (!typed && shows(question, exact = true)) { typeInto(field, basePay); typed = true }
            if (shows("이대로 계산", exact = true)) {
                val preview = wonAfter("10월 예상 (세전)")
                tapText("이대로 계산", exact = true)
                settle()
                return preview
            }
            tapText("다음", exact = true)
        }
        error("급여 설정이 끝나지 않아요: ${words().take(30)}")
    }

    // ---- the flows ----

    /** 교대 · 순서가 돌아가요: 4조 2교대, 오늘 야간 둘째 날. */
    @Test fun shiftWorker() = shoot("2010_flow_shift", FOLDED, fresh(null) {
        noDaysWritten()
        setupToToday()
        finishSetup()
        // 달력: the setup's answer (오늘은 야간) is what is stored and what the cell says.
        assertEquals("오늘은 고른 대로 야간", "야간", shiftOn(today))
        assertTrue(cell(today), "야간" in cell(today))
        assertTrue("둘째 날 다음 날도 순서대로: ${cell(today.plusDays(1))}", shiftOn(today.plusDays(1))!! in cell(today.plusDays(1)))

        // 근무 바꾸기: a day three days on, whatever the order gives, to another shift.
        val d = today.plusDays(3)
        clear(d)
        val was = shiftOn(d) ?: "휴무"
        val to = listOf("주간", "야간").first { it != was }
        changeDay(d, to)

        // 알람 시각 바꾸기: today's 야간 alarm, the next one the phone rings.
        alarmLater(today)

        // 급여 보기: the pay the setup's last page showed is the tab's, and the store's.
        val shown = setUpPay("근무·급여", "2500000")
        assertEquals("설정 마지막 장의 예상 급여 = 저장된 설정으로 셈한 급여", payStored(), shown)
        assertEquals("급여 화면의 예상 급여도 같아요", shown, wonAfter("10월 예상 급여 (세전)"))

        // 근무를 하나 더 바꾸면 급여가 따라와요.
        back(); settle()
        tapText("달력", exact = true); settle()
        val e = today.plusDays(5)
        clear(e)
        changeDay(e, if ((shiftOn(e) ?: "휴무") == "휴무") "주간" else "휴무")
        val after = payStored()
        assertNotEquals("근무를 바꾸면 급여가 바뀌어요", shown, after)
        tapText("근무·급여", exact = true); settle()
        assertEquals("근무·급여의 예상 급여가 바뀐 근무로 다시 셈한 값이에요", after, wonAfter("예상 급여"))
    })

    /** 평일 근무: 월~금 09–18. */
    @Test fun dayWorker() = shoot("2011_flow_day", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("평일 근무", exact = true)
        finishSetup()
        assertEquals("수요일(오늘)은 일근", "일근", shiftOn(today))
        assertTrue(cell(today), "일근" in cell(today))
        val saturday = today.plusDays(3)
        assertEquals("토요일은 쉬어요", null, shiftOn(saturday))
        assertTrue(cell(saturday), "쉬는 날" in cell(saturday))

        val d = today.plusDays(1)
        clear(d)
        changeDay(d, "휴무")
        changeDay(d, "일근")
        alarmLater(d)

        val shown = setUpPay("근무·급여", "2500000")
        assertEquals(payStored(), shown)
        assertEquals(shown, wonAfter("10월 예상 급여 (세전)"))
    })

    /** 교대 · 근무표가 매달 나와요(병원 3교대): 이번 달을 손으로 칠해요. */
    @Test fun rosterNurse() = shoot("2012_flow_roster", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("교대 근무 · 근무표가 매달 나와요", exact = true)
        tapText("건너뛰고 직접 정할게요", exact = true)
        tapText("병원 3교대", exact = true)
        tapText("네, 시간이 맞아요", exact = true)
        tapText("나이트 칠하기", exact = true)
        tapText(Fmt.dayTitle(today), prefix = true)
        tapText(Fmt.dayTitle(today.plusDays(1)), prefix = true)
        tapText("데이 칠하기", exact = true)
        tapText(Fmt.dayTitle(today.plusDays(3)), prefix = true)
        finishSetup()
        // 칠한 칸 = 저장된 근무 = 달력 칸.
        assertEquals("나이트", shiftOn(today)); assertEquals("나이트", shiftOn(today.plusDays(1)))
        assertEquals("칠하지 않은 날은 비어요", null, shiftOn(today.plusDays(2)))
        assertEquals("데이", shiftOn(today.plusDays(3)))
        assertTrue(cell(today.plusDays(1)), "나이트" in cell(today.plusDays(1)))
        assertTrue(cell(today.plusDays(3)), "데이" in cell(today.plusDays(3)))

        changeDay(today.plusDays(2), "이브닝")
        alarmLater(today)

        val shown = setUpPay("근무·급여", "2800000")
        assertEquals(payStored(), shown)
        assertEquals(shown, wonAfter("10월 예상 급여 (세전)"))
    })

    /** 아르바이트 · 시급: 월요일 오전 알바. */
    @Test fun hourlyWorker() = shoot("2013_flow_hourly", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("아르바이트 · 시급", exact = true)
        tapText("네, 정해져 있어요", exact = true)
        tapText("월요일 오전 알바 쉼", exact = true)
        assertTrue("고른 칸이 '일해요'로 바뀌어요", appears("월요일 오전 알바 일해요", exact = true))
        finishSetup()
        val monday = today.plusDays(5)
        assertEquals("오늘(수)은 쉬어요", null, shiftOn(today))
        assertEquals("월요일은 오전 알바", "오전 알바", shiftOn(monday))
        assertTrue(cell(monday), "오전 알바" in cell(monday))

        changeDay(monday, "오후 알바")
        alarmLater(monday)

        val shown = setUpPay("출근·시급", "10320", question = "시급이 얼마예요?", field = "시급")
        assertEquals(payStored(), shown)
        assertEquals(shown, wonAfter("10월 예상 급여 (세전)"))
    })

    /** 현장 · 프리랜서: 건별 일감 하나. */
    @Test fun fieldWorker() = shoot("2014_flow_field", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("현장 · 프리랜서", exact = true)
        settle()
        assertTrue("고르면 바로 달력", cell(today).isNotEmpty())
        assertTrue("현장 달력에는 교대 칩이 없어요", snap().patterns.isEmpty())
        val d = today.plusDays(1)
        openDay(d)
        tapText("일감 적기", exact = true)
        tapText("건별형", exact = true)
        typeInto("일 내용 (예: 욕실 타일)", "욕실 타일 시공")
        typeInto("받을 금액 (전체)", "1200000")
        tapText("저장", exact = true)
        settle()
        val job = runBlocking { scope.money.jobs.first() }.singleOrNull { it.date == d && it.title == "욕실 타일 시공" }
        assertNotNull("일감이 저장돼요", job)
        assertEquals("저장된 금액 = 적은 금액", 1_200_000L, job!!.amount)
        assertTrue("하루 화면에 일감과 금액: ${words().take(30)}", appears("욕실 타일 시공", exact = true) && shows("1,200,000원", exact = true))
        tapText("닫기", exact = true)
        settle()
        assertTrue("달력 칸이 그 일감을 말해요: ${cell(d)}", "1,200,000원" in cell(d))
    })

    /** 자영업: 하루 마감(카드·현금). */
    @Test fun shopOwner() = shoot("2015_flow_shop", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("자영업", exact = true)
        settle()
        val d = today.plusDays(1)
        assertEquals("가게는 매일 영업으로 시작해요", "영업", shiftOn(d))
        openDay(d)
        tapText("매출 적기", exact = true)
        assertTrue("마감 창이 열려요", appears("${Fmt.dayTitle(d)} 마감", exact = true))
        typeInto("카드", "800000")
        typeInto("현금", "200000")
        assertTrue("합계가 저절로: ${words().take(30)}", words().any { it == "매출 합계 1,000,000원" })
        tapText("저장", exact = true)
        settle()
        val day = runBlocking { scope.money.sales.first() }.singleOrNull { it.date == d }
        assertNotNull("마감이 저장돼요", day)
        assertEquals("저장된 매출 = 화면의 합계", 1_000_000L, day!!.sales)
        assertEquals(800_000L, day.card); assertEquals(200_000L, day.cash)
        tapText("닫기", exact = true)
        settle()
        assertTrue("달력 칸에 그날 매출: ${cell(d)}", "1,000,000원" in cell(d))
        changeDay(today.plusDays(2), "휴무")
    })

    /** 투자 · 주식·코인: 매수 한 건. */
    @Test fun investor() = shoot("2016_flow_invest", FOLDED, fresh(null) {
        noDaysWritten()
        tapText("투자 · 주식·코인", exact = true)
        settle()
        val d = today.plusDays(1)
        openDay(d)
        assertTrue("투자 하루에는 근무 칩이 없어요", !shows("휴무로 바꾸기", exact = true))
        tapText("매수", exact = true)
        typeInto("종목명", "밤낮전자")
        typeInto("수량", "10")
        typeInto("단가", "50000")
        assertTrue("내는 돈이 수수료까지: ${words().filter { "500" in it }}", words().any { it.startsWith("내는 돈 500,250원") })
        tapText("매수 저장", exact = true)
        settle()
        val trade = runBlocking { app.container.invest(PersonalCalendar.MAIN).book.first() }.trades.singleOrNull { it.date == d }
        assertNotNull("매수가 저장돼요", trade)
        assertEquals(10.0, trade!!.quantity, 0.0); assertEquals(50_000.0, trade.price, 0.0); assertEquals(250.0, trade.fee, 0.5)
        assertTrue("하루 화면에 저장된 그대로: ${words().take(40)}", appears("10주 × 50,000원", exact = true) && shows("500,250원", exact = true))
    })
}
