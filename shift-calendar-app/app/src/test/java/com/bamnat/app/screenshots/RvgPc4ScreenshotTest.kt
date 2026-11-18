package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.app.share.ShareOptions
import com.bamnat.app.share.SharedPay
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.time.Today
import com.bamnat.core.work.MoneyShare
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 검토 갈래 G 10차, 디자인 품질 조건 사진: 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 * - [RVG-39] PC-174: a shop's 영업일 link is no roster, so no 근무표 점검 card reads it as one.
 * - [RVG-40] PC-175: a shop's 달력 › 도구 and share sheet in its own words.
 * - [RVG-41] HQ-110: the received day card from 150%: face and mark on top, the time whole under them.
 *
 * [AFTER] false takes the same screens on the code before these changes (the '앞' photos); the checks of what changed are left out then.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvgPc4ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** HQ-110: 민지's roster on today (주간 07:00~19:00, 잔업) beside the user's own (주간 08:00 – 14:00 · 반차), as Q1's photo had it. */
    private fun dayCard(wide: Boolean): Screen.() -> Unit = {
        val cc = receivedOn()
        settle()
        cc.received.view(minjiId)
        settle()
        reveal("잔업 (", exact = false, across = if (wide) 0.75f else 0.5f)
        if (AFTER) assertTrue(appears("07:00⁠~⁠19:00", exact = false))
    }
    @Test fun dayOpenLightHuge() = shoot("rvg41_received_day_open_light_200", UNFOLDED, fontScale = 2f, dayCard(wide = true))
    @Test fun dayOpenDarkHuge() = shoot("rvg41_received_day_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, dayCard(wide = true))
    @Test fun dayOpenLight() = shoot("rvg41_received_day_open_light_100", UNFOLDED, dayCard(wide = true))
    @Test fun dayFoldLightHuge() = shoot("rvg41_received_day_fold_light_200", FOLDED, fontScale = 2f, dayCard(wide = false))
    @Test fun dayFoldDarkMid() = shoot("rvg41_received_day_fold_dark_150", FOLDED_NIGHT, fontScale = 1.5f, dayCard(wide = false))
    @Test fun dayFoldLight() = shoot("rvg41_received_day_fold_light_100", FOLDED, dayCard(wide = false))

    /** HQ-110's other way, for comparing only: the unfolded screen at 200% in one column (the day card under the month). */
    @Test fun dayOneColumnOpenLightHuge() = shoot("rvg41_compare_one_column_open_light_200", UNFOLDED, fontScale = 2f) {
        val cc = receivedOn()
        settle()
        val vm = com.bamnat.app.ui.calendar.ReceivedViewModel(cc)
        val cal = cc.received.get(minjiId)!!
        val mine = runBlocking { cc.scheduleRepository.snapshot.first() }
        content {
            com.bamnat.app.ui.calendar.ReceivedCalendarView(vm, cal, mine, java.time.DayOfWeek.SUNDAY, wide = false, today = Today.date(),
                startMonth = YearMonth.from(Today.date()), identityBar = {})
        }
        settle()
    }

    /** PC-174: 민지's shop is open every day of the month and sent its 매출 (the link is 민지의 영업일). */
    private val shopId = "Sx4dEfGh5JkLmNoPqRsTuv"
    private fun everyDayShop(): ReceivedShare {
        val open = ShiftType(1, "영업", "영", 0xFFEF6C00, LocalTime.of(10, 0), LocalTime.of(22, 0))
        val from = today.withDayOfMonth(1)
        val snap = com.bamnat.app.data.ScheduleSnapshot(listOf(open), WorkPattern.Rotation(from, listOf(1L)), emptyList())
        val body = ShareLinkPayload.build(snap, from, "민지", 40, ShareOptions(pay = true),
            pay = listOf(SharedPay(YearMonth.from(today), 9_120_000, "매출")), payKind = MoneyShare.SALES)
        body.remove("ttlDays")
        return ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
    }
    private fun shopDuty(): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        shares.links[shopId] = everyDayShop()
        cc.received.put(shopId, shares.links.getValue(shopId))
        settle()
        cc.received.view(shopId)
        settle()
        reveal("받은 달력은 이 휴대폰에만 있어요", exact = false)
        if (AFTER) assertTrue(gone("근무표 점검", exact = false))
    }
    @Test fun shopDutyFoldLight() = shoot("rvg39_received_shop_duty_fold_light_100", FOLDED, shopDuty())
    @Test fun shopDutyFoldDarkHuge() = shoot("rvg39_received_shop_duty_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopDuty())

    /** PC-175: a shop's 도구 says 다른 사람 달력 받기, as its calendar list does. */
    private fun shopTools(): Screen.() -> Unit = fresh(WorkStyle.BUSINESS) {
        tapText("도구")
        if (AFTER) assertTrue(appears("다른 사람 달력 받기", exact = true))
    }
    @Test fun shopToolsFoldLight() = shoot("rvg40_tools_shop_fold_light_100", FOLDED, shopTools())
    @Test fun shopToolsFoldDarkHuge() = shoot("rvg40_tools_shop_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopTools())

    /** PC-175: 자세히 정하기 of a shop's or an investor's link, down to 캘린더 구독 주소. */
    private fun shareDetail(style: WorkStyle): Screen.() -> Unit = {
        c.settingsRepository.update { it.copy(workStyle = style) }
        c.shareLinks.clear()
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = shares, onOpenReceived = {}) }
        settle()
        tapText("자세히 정하기", prefix = true)
        reveal("캘린더 구독 주소", exact = false)
        if (AFTER) {
            val days = if (style == WorkStyle.BUSINESS) "영업일이" else "달력이"
            assertTrue(appears("이 링크의 $days 따라 바뀌어요", exact = false))
            if (style == WorkStyle.INVEST) assertTrue(gone("급여", exact = true))
        }
    }
    @Test fun shopShareFoldLight() = shoot("rvg40_share_detail_shop_fold_light_100", FOLDED, shareDetail(WorkStyle.BUSINESS))
    @Test fun shopShareFoldDarkHuge() = shoot("rvg40_share_detail_shop_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shareDetail(WorkStyle.BUSINESS))
    @Test fun investShareFoldLight() = shoot("rvg40_share_detail_invest_fold_light_100", FOLDED, shareDetail(WorkStyle.INVEST))
    @Test fun investShareOpenDarkHuge() = shoot("rvg40_share_detail_invest_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, shareDetail(WorkStyle.INVEST))

    companion object {
        /** False only while the '앞' photos are taken on the code before 10차. */
        const val AFTER = true
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
