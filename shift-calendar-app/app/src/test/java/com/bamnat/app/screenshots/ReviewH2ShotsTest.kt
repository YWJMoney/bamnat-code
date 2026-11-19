package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CommutePrefs
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.work.WorkStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 2묶음의 앞·뒤 화면 사진(디자인 품질 조건 확인): 가게의 요일별 영업시간([RVH-36]), 출퇴근 자동 기록의 '다음 출근 때
 * 정하기'와 잔업 여유([RVH-34]·[RVH-35]), 알바의 연차([RVH-38]), 현장의 새로 생긴 것([RVH-40])·알람 설정([RVH-41]), 알바의 출근 요일
 * 화면([RVH-29])과 근무 설정([RVH-41]). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%)으로 그려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH2ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh_${name}_$look", q, scale, style(style) { go() })
    }

    private fun open(place: Place) {
        FeatureLinks.open(FeatureRoute(place))
        settle()
    }

    @Test fun shopHours() = all("6_shop_hours", WorkStyle.BUSINESS) { open(Place.SHOP_DAYS) }

    @Test fun commuteOff() = all("7_commute_off", WorkStyle.SHIFT) { open(Place.COMMUTE) }

    @Test fun commuteNext() = all("8_commute_next", WorkStyle.DAY) {
        CommutePrefs.get(app).apply { setPlaceNext(true); setEnabled(true) }
        open(Place.COMMUTE)
        reveal("잔업", exact = true)
    }

    @Test fun partTimeLeave() = all("9_parttime_leave", WorkStyle.HOURLY) { open(Place.LEAVE) }

    @Test fun fieldWhatsNew() = all("10_field_whats_new", WorkStyle.FIELD) {
        content { com.bamnat.app.ui.guide.WhatsNewDialog(com.bamnat.core.guide.WhatsNew.releases.take(1), {}, {}, {}, style = WorkStyle.FIELD) }
    }

    @Test fun fieldAlarmSettings() = all("11_field_alarm_settings", WorkStyle.FIELD) { open(Place.SETTINGS_ALARM) }

    @Test fun partTimePattern() = all("12_parttime_pattern", WorkStyle.HOURLY) { open(Place.PATTERN) }

    @Test fun partTimeWorkSettings() = all("13_parttime_work_settings", WorkStyle.HOURLY) { open(Place.SETTINGS_WORK) }
}
