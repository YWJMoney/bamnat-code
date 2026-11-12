package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.app.ui.work.ShopKeeper
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.work.ShopRules
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek

/**
 * 2.0.0 검토 갈래 H 1묶음의 앞·뒤 화면 사진(디자인 품질 조건 확인): 가게의 하루 화면 순서·달력 모양 예시·1년 달력·공유, 알바의 기능 켜고 끄기
 * '어디'. 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%)으로 그려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewHShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private val PAGE_SIDE = 0.75f

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh_${name}_$look", q, scale, style(style) { go() })
    }

    private fun open(route: FeatureRoute) {
        FeatureLinks.open(route)
        settle()
    }

    // 2.0.0 [RVO-02] (HQ-01) 편 폭·큰 글자에서 설정 왼쪽 목록이 폭의 절반까지 넓어져, 가운데를 밀면 목록이 움직여요: 오른쪽 쪽을 밀어요.
    @Test fun shopDayOrder() = all("1_shop_day_order", WorkStyle.BUSINESS) {
        open(FeatureRoute(Place.SETTINGS_CALENDAR))
        tapText("하루 화면 순서", exact = true, across = PAGE_SIDE)
    }

    @Test fun shopLookSample() = all("2_shop_look_sample", WorkStyle.BUSINESS) {
        open(FeatureRoute(Place.SETTINGS_CALENDAR))
        reveal("칸 높이", across = PAGE_SIDE)
    }

    // 총괄 사진 검토(11-12): the sample days are a shift worker's (주·야·휴 chips on a shop's year); a shop's own days instead.
    @Test fun shopYear() = all("3_shop_year", WorkStyle.BUSINESS) { shopDays(); open(FeatureRoute(Place.YEAR)) }

    @Test fun shopShare() = all("4_shop_share", WorkStyle.BUSINESS) { shopDays(); open(FeatureRoute(Place.SHARE)) }

    /** A shop's days as its first setup leaves them (영업, 매주 월요일 휴무), with none of the sample shifts. */
    private fun Screen.shopDays() {
        runBlocking {
            val c = app.container
            val repo = c.scheduleRepository
            repo.clearPattern()
            repo.snapshot.first().entries.forEach { repo.resetDay(it.date) }
            ShopKeeper.apply(c, PersonalCalendar.MAIN, ShopRules(closedWeekdays = setOf(DayOfWeek.MONDAY)))
        }
        settle()
    }

    @Test fun partTimeFeatureWay() = all("5_parttime_features", WorkStyle.HOURLY) {
        open(FeatureRoute(Place.FEATURES))
        reveal("어디: ", exact = false)
    }
}
