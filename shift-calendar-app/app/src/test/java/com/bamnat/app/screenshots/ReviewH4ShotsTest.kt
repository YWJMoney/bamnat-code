package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 4묶음(총괄 #451 사진 검토에서 찾은 것)의 앞·뒤 화면 사진: 날짜 창 칩 줄([RVH-49] HQ-16), 모든 조 200%(HQ-17),
 * 알바의 추가 근무 말([RVH-50] HQ-18), 큰 달 제목 작품의 접은 폭 머리([RVH-52] HQ-22), 알바 출근 요일의 회사 찾기([RVH-53] HQ-27), 현장 일감 줄 배지([RVH-56] HQ-36),
 * 받은 근무표에서 그대로 둔 날의 '자동으로 정리한 것'([RVH-57] CAL-05).
 * 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%)으로 그려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH4ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh4_${name}_$look", q, scale, style(style) { go() })
    }

    /** HQ-16: the day pane beside the month (편 폭) with its chips of work and extra work. */
    @Test fun dayChips() = all("21_day_chips", WorkStyle.SHIFT) {}

    /** HQ-17: 모든 조 with every team's card. */
    @Test fun allTeams() = all("22_all_teams", WorkStyle.SHIFT) {
        tapText("모든 조", exact = true)
        settle()
        assertTrue(appears("모든 조", exact = true))
    }

    /** HQ-18: an 알바's 내 근무 says its extra work in its own words. */
    @Test fun partTimeWords() = all("23_parttime_extra_words", WorkStyle.HOURLY) {
        com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.SETTINGS_WORK))
        settle()
        reveal("연장·휴무일 근무 지정", exact = true)
    }

    /**
     * HQ-22: '야간 회복 스테이션' as the market has it (docs/recipes/market-139.json): month title orbit 26 · 글자 사이 2 · 굵기 800 ·
     * 그림자, the head padded 12·24, and its 바로 단추 '물 +1잔'.
     */
    @Test fun bigTitle() = all("24_big_title", WorkStyle.SHIFT) {
        wearWork("야간 회복 스테이션")
        settle()
    }

    /** HQ-27: an 알바 with no company: 출근 요일 has no 회사 이름으로 근무주기 찾기. */
    @Test fun partTimePattern() = all("26_parttime_pattern", WorkStyle.HOURLY) {
        com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.PATTERN))
        settle()
    }

    /** HQ-22: '딥워크 데스크' (일근) as the market has it: month title 30 · 굵기 700 and its 바로 단추 '집중 시작'. */
    @Test fun bigTitleAlone() = all("25_big_title_desk", WorkStyle.DAY) {
        wearWork("딥워크 데스크")
        settle()
    }

    /**
     * 2.0.0 [RVH-56] HQ-36: a 현장 day's job under the calendar, the amount under the job at a big font; the '일' badge sits on the
     * job's first line.
     */
    @Test fun fieldJobLine() = all("27_field_job_line", WorkStyle.FIELD) {
        runBlocking {
            app.container.moneyRepository.save(com.bamnat.core.money.Job(date = today, title = "욕실 타일 시공", client = "수원인테리어",
                place = "영통 아파트", start = java.time.LocalTime.of(9, 0), end = java.time.LocalTime.of(18, 0), amount = 420_000))
        }
        settle()
        reveal("욕실 타일 시공")
    }

    /**
     * 2.0.0 [RVH-57] 민지's link is my own roster; she moved a day I had changed by hand, so it stayed as I made it. On the link's
     * '이 근무표가 바뀌면' a line '자동으로 정리한 것 1건 보기' (none before: the day was only named in the note over the calendar),
     * and its list in the calendar's words. [kept] false draws the row as it was before.
     */
    private fun keptDay(kept: Boolean, sheet: Boolean): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val share = minjiShare()
        shares.links[minjiId] = share
        cc.received.put(minjiId, share, mine = true, basis = share.schedule)
        if (kept) runBlocking {
            val types = cc.scheduleRepository.snapshot.first().shiftTypes
            val day = today.plusDays(3)
            val plan = com.bamnat.core.schedule.RosterFollow.Plan(emptyMap(), listOf(day), emptyList(),
                keptTo = mapOf(day to com.bamnat.core.model.ShiftOverride.Shift(types.first { !it.isOff }.id)))
            com.bamnat.app.share.RosterFollower.listKept(cc, cc.received.get(minjiId)!!, plan, types)
        }
        settle()
        cc.received.view(minjiId)
        settle()
        // Unfolded, the card is in the right-hand column.
        val across = if (app.resources.configuration.screenWidthDp >= 600) 0.75f else 0.5f
        reveal("이 근무표가 바뀌면", exact = true, across = across)
        if (kept) reveal("자동으로 정리한 것 1건 보기", exact = true, across = across)
        if (sheet) {
            tapText("자동으로 정리한 것 1건 보기", exact = true, across = across)
            settle()
            assertTrue(appears("근무표대로", exact = true))
            assertTrue(appears("그대로 둘게요", exact = true))
        }
    }

    @Test fun keptRowBefore() = all("28_roster_kept_row_before", WorkStyle.SHIFT, keptDay(kept = false, sheet = false))
    @Test fun keptRow() = all("28_roster_kept_row", WorkStyle.SHIFT, keptDay(kept = true, sheet = false))
    @Test fun keptSheet() = all("29_roster_kept_sheet", WorkStyle.SHIFT, keptDay(kept = true, sheet = true))

    /** Puts on the whole of the market work [title], every piece, as 장터 › 적용 does. */
    private fun wearWork(title: String) = runBlocking {
        val all = org.json.JSONArray(java.io.File("../docs/recipes/market-139.json").readText())
        val r = (0 until all.length()).map { RecipeCodec.read(all.getJSONObject(it).getJSONObject("recipe").toString()) }.first { it.title == title }
        app.container.recipes.apply(r, "", 0, PersonalCalendar.MAIN, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())
    }
}
