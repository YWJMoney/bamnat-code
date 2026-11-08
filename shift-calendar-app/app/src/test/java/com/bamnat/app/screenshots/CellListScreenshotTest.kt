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

    // ---- [CELL-02] 테마를 입어도 칸에 적은 것이 그대로 (사용자 2026-11-07) ----
    private fun wear(style: WorkStyle) = runBlocking {
        // A theme of looks only (the first one also changes the calendar's 판 to a list, which is its own point).
        val theme = BuiltInThemes.of(style).first { r -> r.pieces.none { it.kind == com.bamnat.core.recipe.PieceKind.TAB_VIEW } }
        c.wardrobe.wear(theme, "builtin:${theme.title}", own = false)
    }
    @Test fun fieldThemeFolded() = shoot("1404_cell_list_field_theme_folded", FOLDED) { field(); wear(WorkStyle.FIELD) }
    @Test fun fieldThemeUnfolded() = shoot("1405_cell_list_field_theme_unfolded", UNFOLDED) { field(); wear(WorkStyle.FIELD) }
    @Test fun fieldThemeBigFont() = shoot("1406_cell_list_field_theme_folded_200", FOLDED, 2f) { field(); wear(WorkStyle.FIELD) }
    @Test fun fieldThemeNight() = shoot("1407_cell_list_field_theme_folded_dark", "w369dp-h905dp-port-night-420dpi") { field(); wear(WorkStyle.FIELD) }
    @Test fun shiftThemeMemos() = shoot("1408_cell_list_shift_theme_unfolded", UNFOLDED) {
        field()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, memoInCells = true) }
        wear(WorkStyle.SHIFT)
    }
    @Test fun shopThemeMemos() = shoot("1409_cell_list_shop_theme_folded", FOLDED) {
        field()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.BUSINESS, memoInCells = true) }
        wear(WorkStyle.BUSINESS)
    }
    @Test fun fieldThemeOff() = shoot("1410_cell_list_field_theme_off_folded", FOLDED) { field("off"); wear(WorkStyle.FIELD) }
    @Test fun tryOnMine() = shoot("1411_cell_list_try_on_mine_folded", FOLDED) {
        field()
        val r = BuiltInThemes.of(WorkStyle.FIELD).first { r -> r.pieces.none { it.kind == com.bamnat.core.recipe.PieceKind.TAB_VIEW } }
        content { TryOnScreen(r, WorkStyle.FIELD, onDismiss = {}, onKeep = { true }) { Text("입기") } }
    }

    // ---- [CELL-03] PC a483ca15: 사무직 메모는 보통 높이 칸에 한 줄, 긴 메모 끝 '…' (빈 달의 낮은 칸은 CellListOnTest) ----
    @Test fun officeMemos() = shoot("1412_cell_list_office_memos_folded", FOLDED) {
        field()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY) }
    }
    @Test fun officeMemosUnfolded() = shoot("1413_cell_list_office_memos_unfolded", UNFOLDED) {
        field()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY) }
    }

    // ---- [BIGTXT-01] 큰 글자 ----
    @Test fun dayPanelBigFont() = shoot("1399_bigtext_day_panel_unfolded_200", UNFOLDED, 2f) {
        field()
        settle()
        tapText(today.withDayOfMonth(4).dayOfMonth.toString(), exact = true)
    }
    @Test fun dayStoryBigFont() = shoot("1402_bigtext_day_story_unfolded_200", UNFOLDED, 2f) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        settle()
        reveal("하루 이야기", exact = true, across = 0.8f)
    }
    @Test fun dayStoryFoldedBigFont() = shoot("1403_bigtext_day_story_folded_200", FOLDED, 2f) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        settle()
        tapText(today.dayOfMonth.toString(), exact = true)
        settle()
        reveal("하루 이야기", exact = true)
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
