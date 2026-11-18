package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.diag.DiagnosticsScreen
import com.bamnat.app.ui.diag.DiagnosticsViewModel
import com.bamnat.app.ui.memo.MemoPhotoFiles
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoPhotos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime

/**
 * 1.38 Play 스토어 스크린샷의 원본 화면(사용자 2026-11-07 "만들어서 제공해줘"). 모두 시험용 기록(가짜 이름·금액)이고,
 * 한 기기 크기로 찍어 `build/screenshots/store_*.png`에 남겨요. 홍보 장면(제목·휴대폰 틀)은 `tools/store-shots/`로 얹어요.
 * 한 번에 여러 개를 돌리면 Robolectric 그림 엔진이 멈출 수 있어(JniConstants FloatBuffer) 하나씩 돌려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StoreScreenshotTest : ScreenshotBase() {
    private companion object {
        /** A common 20:9 phone: 1081×2400. */
        const val STORE = "w393dp-h873dp-port-440dpi"
    }

    private val c get() = app.container

    /** A tool, not a check: only when asked (BAMNAT_STORE_SHOTS=1), so the full suite never mixes its SDK 33 shot in. */
    @org.junit.Before fun onlyWhenAsked() = org.junit.Assume.assumeTrue("가게 사진은 BAMNAT_STORE_SHOTS=1일 때만 찍어요", System.getenv("BAMNAT_STORE_SHOTS") == "1")

    @Test fun calendar() = shoot("store_1a_calendar", STORE) {} // 2.0.0 [RVR-11] not store_1_calendar: CalendarScreenshotTest takes that key shot

    /** The same month with 잠금 화면 알람 already allowed, as on a phone set up once. */
    @Config(sdk = [33])
    @Test fun calendarAllowed() = shoot("store_1b_calendar", STORE) {}

    @Test fun shiftAlarms() = shoot("store_4b_shift_alarms", STORE) {
        settingsPage("내 근무")
        tapText("근무 종류와 시간", exact = true)
        tapText("주간", exact = true)
        reveal("알람 추가")
    }

    @Test fun payReal() = shoot("store_6e_pay_real", STORE, payScreen(com.bamnat.app.tier.Tier.PRO, withProfile = true,
        adjust = { it.copy(base = com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(java.time.LocalDate.of(2020, 1, 1), 1_900_000L)))) }))

    @Test fun payrollNoSales() = shoot("store_9b_payroll", STORE) {
        runBlocking { c.moneyRepository.clearSales(today); c.moneyRepository.clearSales(today.minusDays(1)) }
        content { com.bamnat.app.ui.work.PayrollScreen(roster, java.time.YearMonth.from(today)) {} }
        settle()
        // As a boss a month in: 계약서 signed by link, 5명 미만 answered, 김알바 paid.
        runBlocking {
            val store = c.phaseThree
            val place = store.workplaces.first { it.isNotEmpty() }.first()
            store.staff(place.id).first { it.isNotEmpty() }.forEach { m ->
                store.updateContract(m.id) { com.bamnat.core.boss.StaffContract.Sent("근로계약서", emptyList(), sentAt = 1L, signedName = m.name, signedAt = 2L) }
            }
        }
        settle()
        tapText("5명 미만", exact = true)
        tapText("지급했어요", exact = true)
    }

    @Test fun payResult() = shoot("store_6c_pay_result", STORE, payScreen(com.bamnat.app.tier.Tier.PRO, withProfile = true))

    @Test fun payTab() = shoot("store_6d_pay_tab", STORE) {
        runBlocking {
            val vm = com.bamnat.app.ui.pay.PayViewModel(c)
            c.payStore.save(vm.sampleProfile().copy(rules = companyRules), keepUndo = false)
        }
        settle()
        tab(3, wide = false)
    }

    @Test fun firstSetup() = shoot("store_2_setup", STORE) {
        val id = c.calendars.begin("내 근무")
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c, calendarId = id)
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
    }

    @Test fun alarmChecks() = shoot("store_4_alarm_checks", STORE) {
        c.alarmScheduler.scheduleTest()
        val vm = DiagnosticsViewModel(c)
        content { DiagnosticsScreen(vm, onBack = {}) }
    }

    @Test fun sleep() = shoot("store_5_sleep", STORE) {
        sleep7()
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = false, onBack = {}) }
    }

    @Config(sdk = [33])
    @Test fun calendarFlow() = shoot("store_1c_flow", STORE) {
        tapText("근무 중", prefix = true)
    }

    @Test fun shiftKinds() = shoot("store_4c_shift_kinds", STORE) {
        settingsPage("내 근무")
        tapText("근무 종류와 시간", exact = true)
    }

    @Test fun sleepLower() = shoot("store_5b_sleep_lower", STORE) {
        sleep7()
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = false, onBack = {}) }
        settle()
        scrollUp(700); scrollUp(700)
    }

    @Test fun payroll() = shoot("store_9_payroll", STORE) {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, java.time.YearMonth.from(today)) {} }
    }

    private val roster = com.bamnat.core.work.StaffRoster(listOf(
            com.bamnat.core.work.Staff("s1", "김알바", 10_320, shifts = mapOf(java.time.DayOfWeek.MONDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0)), java.time.DayOfWeek.WEDNESDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0)), java.time.DayOfWeek.FRIDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0)))),
            com.bamnat.core.work.Staff("s2", "이주말", 11_000, shifts = mapOf(java.time.DayOfWeek.SATURDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(22, 0)), java.time.DayOfWeek.SUNDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(22, 0)))),
            com.bamnat.core.work.Staff("s3", "박야간", 10_500, shifts = mapOf(java.time.DayOfWeek.TUESDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(20, 0), java.time.LocalTime.of(2, 0)), java.time.DayOfWeek.THURSDAY to com.bamnat.core.work.StaffShift(java.time.LocalTime.of(20, 0), java.time.LocalTime.of(2, 0)))),
        ))

    private fun sleep7() {
        runBlocking {
            val today = today
            // A week around 주간·야간: nights after day shifts, mornings after night shifts.
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
    }

    @Test fun pay() = shoot("store_6_pay", STORE) {
        tab(3, wide = false)
    }

    @Test fun money() = shoot("store_6b_money", STORE) {
        tab(1, wide = false)
    }

    @Test fun memos() = shoot("store_8_memos", STORE) {
        runBlocking {
            val name = "store-photo-1.jpg"
            val dir = MemoPhotoFiles.dir(app).apply { mkdirs() }
            File(dir, name).outputStream().use { s -> javaClass.getResourceAsStream("/store/walk.jpg")!!.use { it.copyTo(s) } }
            c.memoRepository.save(Memo(date = today, title = "주말 산책", pinned = true, colorIndex = 2,
                body = MemoPhotos.line(MemoPhotos.Photo(name, 45, MemoPhotos.Align.LEFT, "")) + "\n야간 끝나고 공원 한 바퀴\n다음 쉬는 날 또 가기"))
        }
        tab(2, wide = false)
    }

    @Test fun team() = shoot("store_7_team", STORE) {
        runBlocking {
            val store = c.phaseThree
            val shifts = listOf(
                com.bamnat.core.team.TeamShift(1, "D", "주간", 0xFF4F8BD6, false, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(15, 0)),
                com.bamnat.core.team.TeamShift(2, "E", "저녁", 0xFFE39B3B, false, java.time.LocalTime.of(15, 0), java.time.LocalTime.of(23, 0)),
                com.bamnat.core.team.TeamShift(3, "N", "야간", 0xFF5E4AA8, false, java.time.LocalTime.of(23, 0), java.time.LocalTime.of(7, 0)),
                com.bamnat.core.team.TeamShift(4, "O", "휴무", 0xFF9AA3AD, true),
            )
            store.saveTeam(com.bamnat.core.team.Team("t1", "3병동", "민지", shifts, 1L, need = mapOf(1L to 2, 2L to 2, 3L to 2)))
            val names = listOf("민지", "준호", "서연", "지훈", "하은", "도윤", "수아", "예준")
            val members = names.mapIndexed { i, n ->
                com.bamnat.core.team.TeamMember("m$i", "t1", n, skill = if (i < 2) com.bamnat.core.team.Skill.CHARGE else com.bamnat.core.team.Skill.REG, sort = i)
            }
            store.saveMembers(members)
            val month = java.time.YearMonth.from(today).plusMonths(1)
            val rot = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L, 4L)
            val cells = members.flatMapIndexed { i, m ->
                (1..month.lengthOfMonth()).map { d -> val date = month.atDay(d); (m.id to date) to com.bamnat.core.team.TeamCell(m.id, date, rot[(d + i) % rot.size]) }
            }.toMap()
            store.saveBoard("t1", com.bamnat.core.team.TeamBoard(month, members, cells))
        }
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
    }

    // ---- 1.39 스토어 사진 2차(사용자 2026-11-08: 1.39 개발 완료 뒤 1.39 화면으로 7장) ----

    /** 09 현장: a field worker's month, each day's 일감 and its pay in the cell. */
    @Config(sdk = [33])
    @Test fun field() = shoot("store_09_field", STORE) { field0() }

    private fun field0() {
        runBlocking {
            c.scheduleRepository.clearPattern()
            c.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.FIELD, cellList = "auto") }
            val money = c.moneyRepository
            val m = today.withDayOfMonth(1)
            fun d(n: Int) = m.plusDays(n.toLong() - 1)
            suspend fun job(day: Int, title: String, client: String, amount: Long, end: Int? = null) =
                money.save(com.bamnat.core.money.Job(date = d(day), endDate = end?.let(::d), title = title, client = client, amount = amount))
            job(1, "철근 조립", "OO건설 2공구", 230_000); job(2, "철근 조립", "OO건설 2공구", 230_000)
            job(5, "거푸집", "OO빌라 신축", 250_000, end = 7); job(8, "타일", "OO아파트 103동", 200_000)
            job(9, "타일", "OO아파트 103동", 200_000); job(10, "줄눈 AS", "부영 505동", 30_000)
            job(12, "철거", "OO상가", 220_000); job(13, "철거", "OO상가", 220_000)
            job(14, "도배", "OO빌라 302호", 180_000); job(16, "현장 실측", "OO빌라", 0)
            money.save(com.bamnat.core.money.Job(date = d(20), title = "타일 시공", client = "김OO 고객", amount = 1_200_000, deposit = 300_000, depositPaid = true))
            c.memoRepository.save(Memo(date = d(15), title = "일당 입금 확인"))
        }
    }

    /** 09 현장, below the month: what it adds up to. */
    @Config(sdk = [33])
    @Test fun fieldSum() = shoot("store_09b_field_sum", STORE) {
        field0()
        settle()
        scrollUp(700); scrollUp(700)
    }

    /** 10 가족: 아빠's link as the family keeps it, with 지금 자는 중 after his night. */
    @Test fun family() = shoot("store_10_family", STORE) {
        family0()
        tapText("아빠의 근무표 보기", exact = true)
        reveal("자는 중", across = 0.5f)
    }

    private fun family0() {
        val cc = receivedOn(minji = false)
        val day = com.bamnat.core.model.ShiftType(1, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0), icon = "sun")
        val night = com.bamnat.core.model.ShiftType(2, "야간", "야", 0xFF5E35B1, LocalTime.of(19, 0), LocalTime.of(7, 0), icon = "moon")
        val off = com.bamnat.core.model.ShiftType(3, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
        // Yesterday 야간, today 비번: index 5 yesterday, 6 today.
        val pattern = com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(6), listOf(1L, 1L, 3L, 3L, 2L, 2L, 3L, 3L))
        val snap = com.bamnat.app.data.ScheduleSnapshot(listOf(day, night, off), pattern, emptyList())
        val memos = mapOf(today.plusDays(4) to "할머니 생신", today.plusDays(9) to "가족 여행 · 강릉")
        val body = com.bamnat.app.share.ShareLinkPayload.build(snap, today.withDayOfMonth(1), "아빠", 30,
            com.bamnat.app.share.ShareOptions(leaveKinds = true, memos = true, sleepNow = true), memos, emptyList())
        body.remove("ttlDays")
        val now = com.bamnat.core.time.Today.millis()
        body.put("sleepNow", org.json.JSONObject().put("since", now - (2 * 60 + 20) * 60_000L).put("wakeAt", now + (6 * 60) * 60_000L))
        val share = com.bamnat.app.share.ReceivedShare(body, com.bamnat.core.time.Today.instant().plus(java.time.Duration.ofDays(30)), com.bamnat.core.time.Today.instant())
        val id = "Fa3dEfGh5JkLmNoPqRsTuv"
        shares.links[id] = share
        cc.received.put(id, share)
        settle()
    }

    /** 10 가족, the top of 아빠's calendar. */
    @Test fun familyTop() = shoot("store_10b_family_top", STORE) {
        family0()
        tapText("아빠의 근무표 보기", exact = true)
    }

    /** 11 공휴일: 2027년 2월, 설날 연휴 on the month. */
    @Config(sdk = [33])
    @Test fun holiday() = shoot("store_11_holiday", STORE) {
        repeat(4) { stepMonth("다음 달") }
    }

    /** 11 공휴일: the pay items, down to 휴일. */
    @Test fun holidayPay() = shoot("store_11b_holiday_pay", STORE) {
        payScreen(com.bamnat.app.tier.Tier.PRO, withProfile = true,
            adjust = { it.copy(base = com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(java.time.LocalDate.of(2020, 1, 1), 1_900_000L)))) })(this)
        reveal("휴일")
    }

    /** 12 연차: what is left and when it goes. */
    @Test fun leave() = shoot("store_12_leave", STORE, leaveScreen())

    /** 13 수면 */
    @Test fun sleep2() = shoot("store_13_sleep", STORE) {
        sleep7()
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = false, onBack = {}) }
    }

    /** 13 수면, its 패턴 (2.0.0 [RVI-58]: the folded screen is in three parts, 오늘 · 패턴 · 기록). */
    @Test fun sleep2Lower() = shoot("store_13b_sleep_lower", STORE) {
        sleep7()
        val vm = com.bamnat.app.ui.sleep.SleepViewModel(c)
        content { com.bamnat.app.ui.sleep.SleepScreen(vm, wide = false, onBack = {}) }
        settle()
        tapText("패턴", exact = true, scrolls = 0)
    }

    /** 14 가계부 */
    @Test fun ledgerTab() = shoot("store_14_ledger", STORE) {
        tab(1, wide = false)
    }

    /** 15 메모: the board with a photo memo. */
    @Test fun memoBoard() = shoot("store_15_memo_board", STORE) {
        photoMemo()
        tab(2, wide = false)
        // [RVIB-20] On a phone 보기 and 정렬 are one chip (목록 · 날짜순 ▾) that lists them.
        if (!shows("보드", exact = true)) tapText("보기 목록", prefix = true, scrolls = 0)
        tapText("보드", exact = true, scrolls = 0)
    }

    @Test fun memoList() = shoot("store_15b_memo_list", STORE) {
        photoMemo()
        tab(2, wide = false)
    }

    private fun photoMemo() = runBlocking {
        val name = "store-photo-1.jpg"
        val dir = MemoPhotoFiles.dir(app).apply { mkdirs() }
        File(dir, name).outputStream().use { s -> javaClass.getResourceAsStream("/store/walk.jpg")!!.use { it.copyTo(s) } }
        c.memoRepository.save(Memo(date = today, title = "주말 산책", pinned = true, colorIndex = 2,
            body = MemoPhotos.line(MemoPhotos.Photo(name, 45, MemoPhotos.Align.LEFT, "")) + "\n야간 끝나고 공원 한 바퀴\n다음 쉬는 날 또 가기"))
    }
}
