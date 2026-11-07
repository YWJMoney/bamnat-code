package com.bamnat.app.screenshots

import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.recipe.TryOnScreen
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatus
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/**
 * [CELL-01][BIGTXT-01] 1.39 (사용자 사진 넷): a field worker's month with each day's jobs and memos a line each, folded, open and at a big
 * font; a shift worker who shows memos in the cells; the plain cell when it is turned off; and the screens that broke with a big font
 * (하루 패널의 '메모 자세히 쓰기'·'일정 시간도 적기', 일감 금액 줄, 입어 보기).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CellListScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun field(list: String = "auto") = runBlocking {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.FIELD, cellList = list) }
        val money = c.moneyRepository
        val m = today.withDayOfMonth(1)
        fun d(n: Int) = m.plusDays(n.toLong() - 1)
        money.save(Job(date = d(2), title = "욕실 철거", client = "수원인테리어", amount = 420_000))
        money.save(Job(date = d(2), title = "실측", client = "스윈이", amount = 0, start = LocalTime.of(10, 0)))
        money.save(Job(date = d(4), title = "페인트", client = "디자인H 여실장님", amount = 420_000, start = LocalTime.of(9, 0)))
        money.save(Job(date = d(4), title = "도배", client = "큰시숙님 생신 준비", amount = 0, status = JobStatus.TENTATIVE))
        money.save(Job(date = d(6), endDate = d(8), title = "페인트", client = "함양한샘", amount = 550_000))
        money.save(Job(date = d(9), title = "타일", client = "서울병원 10시30분 예약", amount = 0))
        money.save(Job(date = d(13), title = "줄눈", client = "부영505동 1901호 AS", amount = 30_000))
        money.save(Job(date = d(13), title = "실리콘", client = "한달 재구매", amount = 0, status = JobStatus.CANCELLED))
        val memos = c.memoRepository
        memos.save(Memo(date = d(2), title = "10시 MRI 9시30분까지 병원"))
        memos.save(Memo(date = d(5), title = "엄마 MRI 11시"))
        memos.save(Memo(date = d(5), title = "엄마 제일병원"))
        memos.save(Memo(date = d(5), title = "치과 10시30분"))
        memos.save(Memo(date = d(11), title = "여행 출발"))
        memos.save(Memo(date = d(20), title = "치과 10시30분"))
        memos.save(Memo(date = d(27), title = "입금 확인 45만"))
    }

    // ---- [CELL-01] 칸에 일감·메모를 줄마다 ----
    @Test fun fieldFolded() = shoot("1390_cell_list_field_folded", FOLDED) { field() }
    @Test fun fieldUnfolded() = shoot("1391_cell_list_field_unfolded", UNFOLDED) { field() }
    @Test fun fieldFoldedBigFont() = shoot("1392_cell_list_field_folded_200", FOLDED, 2f) { field() }
    @Test fun fieldUnfoldedBigFont() = shoot("1393_cell_list_field_unfolded_150", UNFOLDED, 1.5f) { field() }
    @Test fun fieldFoldedNight() = shoot("1394_cell_list_field_folded_dark", "w369dp-h905dp-port-night-420dpi") { field() }
    @Test fun fieldSmallPhone() = shoot("1395_cell_list_field_small", SMALL_PHONE) { field() }
    @Test fun fieldOff() = shoot("1396_cell_list_field_off_unfolded_150", UNFOLDED, 1.5f) { field("off") }
    @Test fun shiftMemos() = shoot("1397_cell_list_shift_memos_unfolded", UNFOLDED) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, memoInCells = true) }
    }
    @Test fun shiftDefault() = shoot("1398_cell_list_shift_plain_folded", FOLDED) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
    }

    // ---- [BIGTXT-01] 큰 글자 ----
    @Test fun dayPanelBigFont() = shoot("1399_bigtext_day_panel_unfolded_200", UNFOLDED, 2f) {
        field()
        settle()
        tapText(today.withDayOfMonth(4).dayOfMonth.toString(), exact = true)
    }
    @Test fun tryOnBigFont() = shoot("1400_bigtext_try_on_folded_200", FOLDED, 2f) {
        val r = BuiltInThemes.of(WorkStyle.FIELD).first()
        content { TryOnScreen(r, WorkStyle.FIELD, onDismiss = {}, onKeep = { true }) { Text("입기") } }
    }
    @Test fun tryOnUnfolded() = shoot("1401_bigtext_try_on_unfolded", UNFOLDED) {
        val r = BuiltInThemes.of(WorkStyle.FIELD).first()
        content { TryOnScreen(r, WorkStyle.FIELD, onDismiss = {}, onKeep = { true }) { Text("입기") } }
    }
}
