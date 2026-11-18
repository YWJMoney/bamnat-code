package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.share.SharedPay
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.app.share.ShareOptions
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.DailySales
import com.bamnat.core.time.Today
import com.bamnat.core.work.MoneyShare
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 검토 갈래 G 6차, 디자인 품질 조건 사진: 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 * [RVG-35] 검토 HQ-64 (조건 13): a link is called by what it carries ([com.bamnat.core.work.linkWord]: 매출 → 영업일, 일감 → 일정,
 * else 근무표), the same in the sender's hint, the receiving app's cards and list, and the web page.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvgPc3ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** A share server that only takes the link (no page is read back here). */
    private object Taking : ShareApi {
        override val enabled = true
        override suspend fun create(payload: JSONObject) = ShareLinkState("Sh3dEfGh5JkLmNoPqRsTuv",
            "https://bamnat-share.example.workers.dev/s/Sh3dEfGh5JkLmNoPqRsTuv", "t", Today.instant().plus(java.time.Duration.ofDays(30)),
            payload.optString("title"))
        override suspend fun update(state: ShareLinkState, payload: JSONObject) = state
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String) = throw ShareLinkException("not_found")
    }

    /**
     * HQ-64, the sender: the hint names the link by what '링크로 보내기' sends. That way sends no money, so a shop's quick link is its
     * 근무표 as the receiver and the web page see it (매출 goes only from 자세히 정하기); a field worker's carries the 일정, so 일정.
     */
    private fun sender(style: WorkStyle, name: String): Screen.() -> Unit = {
        c.settingsRepository.update { it.copy(workStyle = style) }
        runBlocking { c.calendars.money(com.bamnat.app.data.PersonalCalendar.MAIN).save(DailySales(Today.date(), 820_000, 120_000)) }
        c.shareLinks.clear()
        c.shareLinks.typedName = null
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = Taking, onOpenReceived = {}) }
        settle()
        if (name.isNotEmpty()) typeInto("내 이름 (선택)", name)
    }
    private fun shopSender(): Screen.() -> Unit = {
        sender(WorkStyle.BUSINESS, "민지").invoke(this)
        assertTrue(appears("받는 분 화면에 ‘민지의 근무표’로 보여요", exact = false))
        assertTrue(gone("영업일’로", exact = false))
    }
    @Test fun shopSenderFoldLight() = shoot("rvg35_share_shop_quick_fold_light_100", FOLDED, shopSender())
    private fun fieldSender(): Screen.() -> Unit = {
        sender(WorkStyle.FIELD, "").invoke(this)
        assertTrue(appears("적지 않으면 받는 분 화면에 ‘받은 일정’으로 보여요", exact = false))
    }
    @Test fun fieldSenderFoldLight() = shoot("rvg35_share_field_blank_fold_light_100", FOLDED, fieldSender())
    @Test fun fieldSenderOpenDarkHuge() = shoot("rvg35_share_field_blank_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, fieldSender())

    /** 민지's shop link: 영업 10~22, a 휴무 a week, with this month's 매출. */
    private val shopId = "Sp4dEfGh5JkLmNoPqRsTuv"
    private fun shopShare(): ReceivedShare {
        val open = ShiftType(1, "영업", "영", 0xFFEF6C00, LocalTime.of(10, 0), LocalTime.of(22, 0))
        val off = ShiftType(2, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
        val from = today.withDayOfMonth(1)
        val snap = com.bamnat.app.data.ScheduleSnapshot(listOf(open, off), WorkPattern.Rotation(from, listOf(1L, 1L, 1L, 1L, 1L, 1L, 2L)), emptyList())
        val month = YearMonth.from(today)
        val body = ShareLinkPayload.build(snap, from, "민지", 30, ShareOptions(pay = true), pay = listOf(SharedPay(month, 9_120_000, "매출")),
            payKind = MoneyShare.SALES)
        body.remove("ttlDays")
        return ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
    }

    /** HQ-64, the receiver: the list and the 내 달력에 넣기 card call it 민지의 영업일 / ‘민지’ 영업일, as the sender was told. */
    private fun shopReceived(wide: Boolean = false): Screen.() -> Unit = {
        com.bamnat.app.ui.components.ExplainSeen.reset(c.appContext)
        val cc = receivedOn(minji = false)
        shares.links[shopId] = shopShare()
        cc.received.put(shopId, shares.links.getValue(shopId))
        settle()
        val cal = cc.received.get(shopId)!!
        assertEquals("민지의 영업일", cal.fullName)
        assertEquals("‘민지’ 영업일", cal.roster)
        cc.received.view(shopId)
        settle()
        val across = if (wide) 0.75f else 0.5f
        reveal("일 내 달력에 넣기", exact = false, across = across)
        // [RVG-36] 검토 HQ-74 (조건 4): what happens after 넣기 is one line + ⓘ from the first look (at 200% it was ten grey lines); the
        // words behind ⓘ keep the link's word ([shopNote]). (조건 6) The 영업 the user has no 근무 for is a 주의 line, not red words.
        assertTrue(appears("바뀌면 다시 받아요", exact = true))
        assertTrue(gone("넣은 뒤 보낸 분이", exact = false))
        assertTrue(appears("내 근무에 없는 '영업'이 있는 날은 그대로 둬요", exact = false))
        assertTrue(scrolledTo("이 영업일 신고하기", exact = true))
    }
    @Test fun shopReceivedFoldLight() = shoot("rvg35_received_shop_fold_light_100", FOLDED, shopReceived())
    @Test fun shopReceivedFoldDarkHuge() = shoot("rvg35_received_shop_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopReceived())
    @Test fun shopReceivedOpenLight() = shoot("rvg35_received_shop_open_light_100", UNFOLDED, shopReceived(wide = true))

    /**
     * [RVG-36] 검토 HQ-74 (조건 13): ⓘ opened, the words say the link's 영업일 and, to someone paid by the hour or the month, 이번 달
     * 급여 (not 월급: 알바 is paid by the hour); to a 가게, nothing of pay (a 가게 has no 급여 설정 to point to).
     */
    private fun shopNote(style: WorkStyle): Screen.() -> Unit = {
        c.settingsRepository.update { it.copy(workStyle = style) }
        shopReceived().invoke(this)
        tapText("설명 보기", exact = true)
        assertTrue(appears("넣은 뒤 보낸 분이 영업일을 고치면", exact = false))
        assertTrue(appears("이 영업일이 바뀌면", exact = false))
        assertTrue(gone("월급", exact = false))
        if (style.usesPayEngine) assertTrue(appears("이번 달 급여가 얼마인지", exact = false))
        else assertTrue(gone("급여가 얼마인지", exact = false) && gone("급여 설정", exact = false))
    }
    @Test fun shopNoteHourlyFoldLight() = shoot("rvg36_received_shop_note_hourly_fold_light_100", FOLDED, shopNote(WorkStyle.HOURLY))
    @Test fun shopNoteOwnerFoldDarkHuge() = shoot("rvg36_received_shop_note_owner_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopNote(WorkStyle.BUSINESS))

    /** The list the calendar's name ▾ opens: the face is read out as 민지의 영업일. */
    @Test fun shopSwitcherFoldDarkHuge() = shoot("rvg35_received_shop_switcher_fold_dark_200", FOLDED_NIGHT, fontScale = 2f) {
        val cc = receivedOn(minji = false)
        shares.links[shopId] = shopShare()
        cc.received.put(shopId, shares.links.getValue(shopId))
        settle()
        tapText("달력 바꾸기, 지금 내 달력", exact = true)
        assertTrue(appears("어느 달력을 볼까요?", exact = true))
        assertTrue(appears("민지의 영업일", exact = false))
        assertTrue(appears("함께 받은 것: 매출", exact = false))
    }

    /** HQ-64, before it is kept: the link's card says 민지의 영업일, and that a change of the 영업일 comes on each opening. */
    private fun shopPreview(): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        shares.links[shopId] = shopShare()
        val vm = com.bamnat.app.ui.calendar.ReceivedViewModel(cc)
        content { com.bamnat.app.ui.calendar.AddReceivedSheet(vm, shopId, Today.date(), {}, {}) }
        settle()
        assertTrue(appears("민지의 영업일 받기", exact = true))
        assertTrue(appears("민지의 영업일", exact = true))
        reveal("읽기 전용이에요.", exact = false)
        assertTrue(appears("보낸 분이 영업일을 고치면 열 때마다 새로 받아요.", exact = false))
    }
    @Test fun shopPreviewFoldLight() = shoot("rvg35_received_shop_preview_fold_light_100", FOLDED, shopPreview())
    @Test fun shopPreviewFoldDarkHuge() = shoot("rvg35_received_shop_preview_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopPreview())

    /** HQ-64 on a new phone opened from the link: the first card names it as the web page did. */
    private fun shopArrives(): Screen.() -> Unit = {
        content {
            com.bamnat.app.ui.onboarding.FirstStyleScreen(onPick = {}, onSkip = {}, onLater = {}, linkWaiting = true, linkTitle = "민지",
                linkWord = "영업일", onLinkStart = {}, onLinkTheirs = {})
        }
        settle()
        assertTrue(appears("‘민지’ 영업일이 도착했어요", exact = true))
        assertTrue(appears("누구 영업일인지 알려 주세요.", exact = true))
    }
    @Test fun shopArrivesFoldLight() = shoot("rvg35_first_shop_link_fold_light_100", FOLDED, shopArrives())
    @Test fun shopArrivesFoldDarkHuge() = shoot("rvg35_first_shop_link_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopArrives())

    // ---- 갈래 G 9차 [RVG-38] 검토 HQ-64 B안 (D RVD-32): the sender's word, from 처리방침 3.14 ----

    /** 민지's shop link with its days only (no 매출), as a 2.0.0 app sends it from 3.14 ([said] 영업일) or before ([said] null). */
    private val daysId = "Sd4dEfGh5JkLmNoPqRsTuv"
    private fun shopDaysShare(said: String?): ReceivedShare {
        val open = ShiftType(1, "영업", "영", 0xFFEF6C00, LocalTime.of(10, 0), LocalTime.of(22, 0))
        val off = ShiftType(2, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
        val from = today.withDayOfMonth(1)
        val snap = com.bamnat.app.data.ScheduleSnapshot(listOf(open, off), WorkPattern.Rotation(from, listOf(1L, 1L, 1L, 1L, 1L, 1L, 2L)), emptyList())
        val body = ShareLinkPayload.build(snap, from, "민지", 30, ShareOptions(), word = said)
        body.remove("ttlDays")
        return ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
    }

    /**
     * PC-170 in the app: a shop that sent its days only was 민지의 근무표 (the link could not tell); with the word its app sends from 3.14
     * it is 민지의 영업일 everywhere, as the web page says. Without it (a link from before, an old server) it stays 근무표, unchanged.
     */
    private fun shopDaysReceived(said: String?): Screen.() -> Unit = {
        com.bamnat.app.ui.components.ExplainSeen.reset(c.appContext)
        val cc = receivedOn(minji = false)
        shares.links[daysId] = shopDaysShare(said)
        cc.received.put(daysId, shares.links.getValue(daysId))
        settle()
        val word = said ?: "근무표"
        val cal = cc.received.get(daysId)!!
        assertEquals("민지의 $word", cal.fullName)
        assertEquals("‘민지’ $word", cal.roster)
        cc.received.view(daysId)
        settle()
        reveal("일 내 달력에 넣기", exact = false, across = 0.5f)
        assertTrue(scrolledTo("이 $word 신고하기", exact = true))
    }
    @Test fun shopDaysWordFoldLight() = shoot("rvg38_received_shop_days_word_fold_light_100", FOLDED, shopDaysReceived("영업일"))
    @Test fun shopDaysWordFoldDarkHuge() = shoot("rvg38_received_shop_days_word_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopDaysReceived("영업일"))
    @Test fun shopDaysOldFoldLight() = shoot("rvg38_received_shop_days_old_fold_light_100", FOLDED, shopDaysReceived(null))
    @Test fun shopDaysOldFoldDarkHuge() = shoot("rvg38_received_shop_days_old_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopDaysReceived(null))

    /**
     * An investor's link (its app says 달력 from 3.14): the card before it is kept reads 민지의 달력, what came 달력만, and its change line
     * too. Without the word ([said] null: a link from before) it reads as it did, 민지의 근무표 and 근무와 휴가만.
     */
    private val investId = "Iv4dEfGh5JkLmNoPqRsTuv"
    private fun investPreview(said: String? = "달력"): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val buy = ShiftType(1, "매수", "매", 0xFF3D7DD8, null, null)
        val from = today.withDayOfMonth(1)
        val snap = com.bamnat.app.data.ScheduleSnapshot(listOf(buy), WorkPattern.Rotation(from, listOf(1L, null, null, 1L, null, null, null)), emptyList())
        val body = ShareLinkPayload.build(snap, from, "민지", 30, ShareOptions(), word = said).apply { remove("ttlDays") }
        shares.links[investId] = ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
        val vm = com.bamnat.app.ui.calendar.ReceivedViewModel(cc)
        content { com.bamnat.app.ui.calendar.AddReceivedSheet(vm, investId, Today.date(), {}, {}) }
        settle()
        val word = said ?: "근무표"
        assertTrue(appears("민지의 $word 받기", exact = true))
        // (조건 13) what came, in the link's word: not 근무와 휴가 for an investor's 달력.
        assertTrue(appears(if (said == null) "근무와 휴가만 받았어요" else "달력만 받았어요", exact = true))
        reveal("읽기 전용이에요.", exact = false)
        assertTrue(appears("보낸 분이 $word${com.bamnat.app.ui.Fmt.obj(word)} 고치면 열 때마다 새로 받아요.", exact = false))
    }
    @Test fun investPreviewFoldLight() = shoot("rvg38_received_invest_preview_fold_light_100", FOLDED, investPreview())
    @Test fun investPreviewFoldDarkHuge() = shoot("rvg38_received_invest_preview_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, investPreview())
    @Test fun investPreviewOldFoldLight() = shoot("rvg38_received_invest_preview_old_fold_light_100", FOLDED, investPreview(null))
    @Test fun investPreviewOldFoldDarkHuge() = shoot("rvg38_received_invest_preview_old_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, investPreview(null))

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
