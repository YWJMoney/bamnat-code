package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.diag.DiagnosticsScreen
import com.bamnat.app.ui.diag.DiagnosticsViewModel
import com.bamnat.app.ui.memo.MemoPhotoFiles
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoPhotos
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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
    @org.junit.Before fun onlyWhenAsked() = org.junit.Assume.assumeTrue(System.getenv("BAMNAT_STORE_SHOTS") == "1")

    @Test fun calendar() = shoot("store_1_calendar", STORE) {}

    /** The same month with 잠금 화면 알람 already allowed, as on a phone set up once. */
    @Config(sdk = [33])
    @Test fun calendarAllowed() = shoot("store_1b_calendar", STORE) {}

    @Test fun shiftAlarms() = shoot("store_4b_shift_alarms", STORE) {
        settingsPage("내 근무")
        tapText("근무 종류와 시간", exact = true)
        tapText("주간", exact = true)
        reveal("알람 추가")
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
        content { com.bamnat.app.ui.sleep.SleepScreen(com.bamnat.app.ui.sleep.SleepViewModel(c), wide = false, onBack = {}) }
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
}
