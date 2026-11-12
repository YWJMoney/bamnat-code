package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.share.SharedSchedule
import com.bamnat.app.ui.setup.LinkStart
import com.bamnat.core.model.ShiftIcon
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 PC 2차(ac6c21ce) 갈래 G, 디자인 품질 조건 사진: 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 * [RVG-24] PC-92 실패 글, [RVG-30] PC-97 적던 이름, [RVG-27] SN-13 빼고 보낸 것(공유 창·달력 위), [RVG-28] PC-94 휴게 없는 링크 요약,
 * [RVG-32] PC-93 이름 없는 링크, [RVG-31] PC-95 넣기 되돌리기.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvgPc2ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /**
     * A share server that turns a link down: with [always] every time (a part the phone cannot leave out, e.g. too_large), else only
     * an emoji 근무 그림 the way a 1.34~1.39 server did (type_icon).
     */
    private class Refusing(val always: String? = null) : ShareApi {
        override val enabled = true
        private fun check(p: JSONObject) {
            always?.let { throw ShareLinkException(it) }
            val types = p.optJSONArray("types") ?: return
            for (i in 0 until types.length()) if (types.getJSONObject(i).optString("icon").startsWith("e:")) throw ShareLinkException("type_icon")
        }
        override suspend fun create(payload: JSONObject): ShareLinkState {
            check(payload)
            return ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://bamnat-share.example.workers.dev/s/Ab3dEfGh5JkLmNoPqRsTuv", "t",
                Today.instant().plus(java.time.Duration.ofDays(30)), payload.optString("title"))
        }
        override suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState { check(payload); return state }
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String) = throw ShareLinkException("not_found")
    }

    private fun Screen.sheet(client: ShareApi) {
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = client, onOpenReceived = {}) }
        settle()
    }

    /** PC-92 + PC-97: the server turned the link down; the line is in people's words and the name typed stays. */
    private fun refused(): Screen.() -> Unit = {
        c.shareLinks.clear()
        c.shareLinks.typedName = null
        sheet(Refusing(always = "too_large"))
        typeInto("내 이름 (선택)", "민지")
        tapText("링크로 보내기", exact = true)
        assertTrue(appears("링크를 만들지 못했어요.", exact = false))
        assertTrue("no server code on screen", gone("too_large", exact = false))
        assertTrue(gone("(type_icon)", exact = false))
        assertEquals("민지", c.shareLinks.typedName)
    }
    @Test fun refusedFoldLight() = shoot("rvg24_share_refused_fold_light_100", FOLDED, refused())
    @Test fun refusedFoldDarkHuge() = shoot("rvg24_share_refused_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, refused())

    /** PC-97: closed and opened again after the failure, the name is still there (before, the link went as '이름 없는 근무표'). */
    @Test fun nameStaysAfterReopen() = shoot("rvg30_share_name_kept_fold_light_100", FOLDED) {
        refused().invoke(this)
        // Closed (nothing on screen), then opened again: a new sheet, the failure gone, the name still there.
        content { }
        settle()
        assertTrue(shownDialogs().isEmpty())
        sheet(Refusing(always = "too_large"))
        assertTrue(gone("링크를 만들지 못했어요", exact = false))
        assertTrue(appears("받는 분 화면에 ‘민지의", exact = false))
    }

    /** SN-13 / P-13: an emoji 근무 그림 on an old share server: the link is made without it, and the sheet says what was left out. */
    private fun leftOut(): Screen.() -> Unit = {
        c.shareLinks.clear()
        runBlocking {
            val day = c.scheduleRepository.shiftTypes.first().first { !it.isOff }
            c.scheduleRepository.saveShiftType(day.copy(icon = ShiftIcon.emojiKey("☀️")))
        }
        sheet(Refusing())
        tapText("링크로 보내기", exact = true)
        assertTrue(appears("빼고 보냈어요", exact = false))
        assertTrue(gone("링크를 만들지 못했어요", exact = false))
        assertTrue(c.shareLinks.link.value != null)
    }
    @Test fun leftOutFoldLight() = shoot("rvg27_share_left_out_fold_light_100", FOLDED, leftOut())
    @Test fun leftOutFoldDarkHuge() = shoot("rvg27_share_left_out_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, leftOut())

    /**
     * SN-13: the automatic update left the 그림 out; said once (안내 카드 한 모양, 조건 3), with its X. 2.0.0 [RVP-43] HQ-49 (총괄 11-12):
     * something to know, not to do now, so a row of the 알림함's 안내, '근무표 링크 안내', not over the month.
     */
    private fun calendarNotice(): Screen.() -> Unit = {
        c.shareLinks.save(ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://bamnat-share.example.workers.dev/s/Ab3dEfGh5JkLmNoPqRsTuv", "t",
            Today.instant().plus(java.time.Duration.ofDays(30)), "민지"))
        c.shareLinks.setDropped(com.bamnat.app.share.ShareLinkPayload.withoutRefused(JSONObject("""{"types":[{"icon":"e:☀️"}]}"""), "type_icon")!!.second)
        settle()
        tapText("알림 ", prefix = true, scrolls = 0)
        assertTrue(appears("근무표 링크 안내", exact = true))
        assertTrue(appears("빼고 보냈어요", exact = false))
    }
    @Test fun calendarNoticeFoldLight() = shoot("rvg27_calendar_left_out_fold_light_100", FOLDED, calendarNotice())
    @Test fun calendarNoticeFoldDarkHuge() = shoot("rvg27_calendar_left_out_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, calendarNotice())
    @Test fun calendarNoticeOpenDarkHuge() = shoot("rvg27_calendar_left_out_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, calendarNotice())
    @Test fun calendarNoticeCloses() = shoot("rvg27_calendar_left_out_closed_fold_light_100", FOLDED) {
        calendarNotice().invoke(this)
        tapText("닫기: 근무표 링크 안내", exact = true, scrolls = 0)
        assertTrue(gone("빼고 보냈어요", exact = false))
        assertEquals(null, c.shareLinks.dropped.value)
    }

    /**
     * 검토 S7-3 (갈래 R 의견 3): the automatic update turned down twice running for something it cannot leave out; one line, its X.
     * 갈래 P 3/3 ([RVP-43]): the link not updating is to be seen, so it is the band over the month with 공유 (the state is there when
     * the app opens, as after an update in the background; one that comes later waits in the 알림함, PC-81).
     */
    private fun refusedBefore() {
        c.shareLinks.save(ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://bamnat-share.example.workers.dev/s/Ab3dEfGh5JkLmNoPqRsTuv", "t",
            Today.instant().plus(java.time.Duration.ofDays(30)), "민지"))
        c.shareLinks.setDropped(com.bamnat.app.share.ShareLinkPayload.REFUSED_AGAIN)
    }
    private fun calendarRefused(): Screen.() -> Unit = {
        settle()
        assertTrue(appears("바뀐 근무가 링크에 안 갔어요", exact = false))
        assertTrue(appears("공유", exact = true))
    }
    @Test fun calendarRefusedFoldLight() { refusedBefore(); shoot("rvg27_calendar_refused_again_fold_light_100", FOLDED, calendarRefused()) }
    @Test fun calendarRefusedFoldDarkHuge() { refusedBefore(); shoot("rvg27_calendar_refused_again_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, calendarRefused()) }

    /** A link sent without a name by an app before 2.0.0 (no breaks): 민지's days, no title, no rest. */
    private fun oldNoName(): JSONObject = JSONObject(minjiShare().schedule.toString()).apply {
        put("title", "")
        val types = getJSONArray("types")
        for (i in 0 until types.length()) { types.getJSONObject(i).remove("rest"); types.getJSONObject(i).remove("restStart") }
    }
    private val noNameId = "Nn4dEfGh5JkLmNoPqRsTuv"

    /** PC-93 + PC-94: '받은 근무표를 내 근무로 시작할까요?' and, under '근무 이름', that the breaks were not in the link. */
    private fun noNameSummary(scrolled: Boolean): Screen.() -> Unit = {
        val body = oldNoName()
        val share = ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
        LinkStart.keepMine(c.received, noNameId, share)
        c.setupDrafts.save(LinkStart.draft(SharedSchedule.parse(body), today)!!)
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c)
        content { com.bamnat.app.ui.setup.SetupHost(vm, onNotMine = {}) }
        settle()
        assertTrue(appears("받은 근무표를 내 근무로 시작할까요?", exact = true))
        assertTrue(gone("‘받은’", exact = false))
        assertTrue(vm.linkRestUnknown())
        if (scrolled) reveal("받은 근무표에 쉬는 시간이 없어요", exact = false)
    }
    @Test fun noNameSummaryFoldLight() = shoot("rvg28_link_summary_fold_light_100", FOLDED, noNameSummary(scrolled = false))
    @Test fun noNameSummaryFoldDarkHuge() = shoot("rvg28_link_summary_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, noNameSummary(scrolled = false))
    @Test fun noNameSummaryFoldLightRest() = shoot("rvg28_link_summary_rest_fold_light_100", FOLDED, noNameSummary(scrolled = true))
    @Test fun noNameSummaryFoldDarkHugeRest() = shoot("rvg28_link_summary_rest_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, noNameSummary(scrolled = true))
    @Test fun noNameSummaryOpenDarkHuge() = shoot("rvg28_link_summary_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, noNameSummary(scrolled = true))

    /** PC-93: a received link without a name beside mine: the chip says 받은 표 whole, never '이름 없'. */
    private fun noNameReceived(): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val body = JSONObject(minjiShare().schedule.toString()).apply { put("title", "") }
        val share = ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
        shares.links[noNameId] = share
        cc.received.put(noNameId, share)
        settle()
        cc.received.view(noNameId)
        settle()
        assertTrue(appears("받은 표", exact = false))
        assertTrue(gone("이름 없", exact = false))
    }
    @Test fun noNameReceivedFoldLight() = shoot("rvg32_received_no_name_fold_light_100", FOLDED, noNameReceived())
    @Test fun noNameReceivedFoldDarkHuge() = shoot("rvg32_received_no_name_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, noNameReceived())

    /**
     * PC-95 (조건 10): '…일 내 달력에 넣기' then 되돌리기 on the received link: the card is back as it was, the same days offered (before,
     * they stayed as '직접 바꾼 날' and the card said 0일).
     */
    private fun putInUndone(wide: Boolean = false): Screen.() -> Unit = {
        noNameReceived().invoke(this)
        val across = if (wide) 0.75f else 0.5f
        reveal("일 내 달력에 넣기", exact = false, across = across)
        tapText("일 내 달력에 넣기", exact = false, across = across)
        assertTrue(appears("일 넣었어요", exact = false))
        assertTrue(c.received.get(noNameId)!!.adopted)
        tapText("되돌리기", exact = true, across = across)
        assertFalse("not followed any more", c.received.get(noNameId)!!.adopted)
        reveal("일 내 달력에 넣기", exact = false, across = across)
        assertTrue(gone("직접 바꾼 날은 빼고 넣어요", exact = false))
        assertTrue(gone("직접 바꾼 날도 넣기", exact = false))
        assertTrue(gone("0일 내 달력에 넣기", exact = false))
    }
    @Test fun putInUndoneFoldLight() = shoot("rvg31_put_in_undone_fold_light_100", FOLDED, putInUndone())
    @Test fun putInUndoneFoldDarkHuge() = shoot("rvg31_put_in_undone_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, putInUndone())
    @Test fun putInUndoneOpenLight() = shoot("rvg31_put_in_undone_open_light_100", UNFOLDED, putInUndone(wide = true))

    /** HQ-55 (조건 4·13): '내 이름' left empty says what the link really shows ('받은 근무표'), never the example name's. */
    private fun blankName(): Screen.() -> Unit = {
        c.shareLinks.clear()
        c.shareLinks.typedName = null
        sheet(Refusing())
        assertTrue(appears("적지 않으면 받는 분 화면에 ‘받은 근무표’로 보여요", exact = false))
        assertTrue(gone("‘민지의", exact = false))
        typeInto("내 이름 (선택)", "민지")
        assertTrue(appears("받는 분 화면에 ‘민지의 근무표’로 보여요", exact = false))
        typeInto("내 이름 (선택)", "")
        assertTrue(appears("적지 않으면", exact = false))
    }
    @Test fun blankNameFoldLight() = shoot("rvg33_share_blank_name_fold_light_100", FOLDED, blankName())
    @Test fun blankNameFoldDarkHuge() = shoot("rvg33_share_blank_name_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, blankName())

    /** HQ-55, the same structure: the picture's title field, left empty, says the title the picture really gets. */
    @Test fun blankPictureNameFoldLight() = shoot("rvg33_picture_blank_name_fold_light_100", FOLDED) {
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ImageShareSheet(snap, Today.month(), java.time.DayOfWeek.SUNDAY, emptyList(), {}, {}) }
        settle()
        reveal("제목에 넣을 이름 (선택)", exact = true)
        assertTrue(appears("적지 않으면 ‘${Today.month().year}년 ${Today.month().monthValue}월", exact = false))
        assertTrue(gone("‘민지의", exact = false))
    }

    /**
     * HQ-55 for 현장: a named link takes 으로 after 일정. [RVG-35] 검토 HQ-64: the quick link carries the 일정, so a nameless one is
     * '받은 일정' in the receiving app and on the web page, and the hint says so (was '받은 근무표').
     */
    @Test fun blankNameFieldFoldDarkHuge() = shoot("rvg33_share_blank_name_field_fold_dark_200", FOLDED_NIGHT, fontScale = 2f) {
        c.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.FIELD) }
        c.shareLinks.clear()
        c.shareLinks.typedName = null
        sheet(Refusing())
        assertTrue(appears("적지 않으면 받는 분 화면에 ‘받은 일정’으로 보여요", exact = false))
        typeInto("내 이름 (선택)", "민지")
        assertTrue(appears("받는 분 화면에 ‘민지의 일정’으로 보여요", exact = false))
    }

    /** The picture's title for 투자: '달력' takes 으로. */
    @Test fun blankPictureNameInvestFoldLight() = shoot("rvg33_picture_blank_name_invest_fold_light_100", FOLDED) {
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ImageShareSheet(snap, Today.month(), java.time.DayOfWeek.SUNDAY, emptyList(), {}, {},
            style = com.bamnat.core.work.WorkStyle.INVEST) }
        settle()
        reveal("제목에 넣을 이름 (선택)", exact = true)
        assertTrue(appears("월 달력’으로 보여요", exact = false))
    }

    /**
     * HQ-55 (조건 4): the received link's '내 달력에 넣기' card on a later visit: the days in one line, what happens after 넣기 in one
     * line with ⓘ ([com.bamnat.app.ui.components.Explain]); before, it filled a 200% folded screen like a page.
     */
    private fun putInCard(wide: Boolean = false): Screen.() -> Unit = {
        // A later visit: the 'seen this visit' set outlives a test in the same JVM, so it starts over first.
        com.bamnat.app.ui.components.ExplainSeen.reset(c.appContext)
        com.bamnat.app.ui.components.ExplainSeen.saw(c.appContext, "adopt:after")
        noNameReceived().invoke(this)
        val across = if (wide) 0.75f else 0.5f
        reveal("일 내 달력에 넣기", exact = false, across = across)
        assertTrue(appears("바뀌면 다시 받아요", exact = true))
        assertTrue(gone("6시간 안에 받아서", exact = false))
        assertTrue(gone("급여 설정을 하면", exact = false))
    }
    @Test fun putInCardFoldLight() = shoot("rvg33_put_in_card_fold_light_100", FOLDED, putInCard())
    @Test fun putInCardFoldDarkHuge() = shoot("rvg33_put_in_card_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, putInCard())
    @Test fun putInCardOpenLight() = shoot("rvg33_put_in_card_open_light_100", UNFOLDED, putInCard(wide = true))

    /**
     * PC-132 ([RVG-34]): a member who started from the 팀 리더's link sees, at the calendar's head, a face that says 팀 근무표; with
     * large text there is no room for the words, so the face alone ('팀'), read out as the link's full name. Since the head is one row
     * (갈래 P 3/3, [RVP-45]) a phone has no faces beside the calendar chip, folded or open (the open screen's head is the month's
     * pane): there the link is the chip's list's line '팀 근무표 · …' ([memberSwitcherFoldDarkHuge]), and no bare face is left to read
     * as '3'. A tablet's wide head keeps the 팀 face longest (HeadRoomTest.aTeamLinkStaysLongestOnTheOpenScreen).
     */
    private val teamId = "Tm3dEfGh5JkLmNoPqRsTuv"
    private fun memberHead(face: Boolean, words: Boolean): Screen.() -> Unit = {
        c.shareApi = shares
        val body = JSONObject(minjiShare().schedule.toString()).apply { put("title", "3병동 김간호"); put("team", true) }
        val share = ReceivedShare(body, Today.instant().plus(java.time.Duration.ofDays(30)), Today.instant())
        shares.links[teamId] = share
        LinkStart.keepMine(c.received, teamId, share)
        settle()
        if (face) assertTrue(appears("3병동 김간호의 근무표 보기", exact = true)) else assertTrue(gone("3병동 김간호의 근무표 보기", exact = true))
        assertEquals("팀", c.received.get(teamId)!!.initial)
        if (words) assertTrue(appears("팀 근무표", exact = true)) else assertTrue(gone("팀 근무표", exact = true))
    }
    @Test fun memberHeadFoldLight() = shoot("rvg34_member_head_fold_light_100", FOLDED, memberHead(face = false, words = false))
    @Test fun memberHeadFoldBig() = shoot("rvg34_member_head_fold_light_130", FOLDED, fontScale = 1.3f, memberHead(face = false, words = false))
    @Test fun memberHeadFoldDarkHuge() = shoot("rvg34_member_head_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, memberHead(face = false, words = false))
    @Test fun memberHeadOpenDarkHuge() = shoot("rvg34_member_head_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, memberHead(face = false, words = false))
    @Test fun memberHeadOpenLight() = shoot("rvg34_member_head_open_light_100", UNFOLDED) {
        memberHead(face = false, words = false).invoke(this)
        tapText("달력 바꾸기, 지금 내 달력", exact = true)
        assertTrue(appears("팀 근무표 · ", exact = false))
    }
    /** The list the calendar's name ▾ opens says it is the 팀 근무표. */
    @Test fun memberSwitcherFoldDarkHuge() = shoot("rvg34_member_switcher_fold_dark_200", FOLDED_NIGHT, fontScale = 2f) {
        memberHead(face = false, words = false).invoke(this)
        tapText("달력 바꾸기, 지금 내 달력", exact = true)
        assertTrue(appears("어느 달력을 볼까요?", exact = true))
        assertTrue(appears("팀 근무표 · ", exact = false))
    }

    /**
     * [RVG-34] With no 팀 face in a phone's head, the 알림함 (not the month) says once where the link is; its line opens the chip's list.
     */
    private fun memberInbox(open: Boolean): Screen.() -> Unit = {
        memberHead(face = false, words = false).invoke(this)
        val inbox = com.bamnat.app.inbox.Inbox.get(c.appContext)
        assertEquals("one line, on this phone once", 1, inbox.rows.value.count { it.kind == com.bamnat.app.ui.calendar.TeamLinkNote.KIND })
        assertTrue(gone(com.bamnat.app.ui.calendar.TeamLinkNote.LINE, exact = false))
        tapText("알림 ", prefix = true, scrolls = 0)
        settle()
        assertTrue(appears(com.bamnat.app.ui.calendar.TeamLinkNote.LINE, exact = false))
        if (open) {
            tapText(com.bamnat.app.ui.calendar.TeamLinkNote.LINE, exact = false, scrolls = 0)
            settle()
            assertTrue(appears("어느 달력을 볼까요?", exact = true))
            assertTrue(appears("팀 근무표 · ", exact = false))
        }
    }
    @Test fun memberInboxFoldLight() = shoot("rvg34_member_inbox_fold_light_100", FOLDED, memberInbox(open = false))
    @Test fun memberInboxFoldDarkHuge() = shoot("rvg34_member_inbox_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, memberInbox(open = false))
    @Test fun memberInboxOpenLight() = shoot("rvg34_member_inbox_open_light_100", UNFOLDED, memberInbox(open = false))
    @Test fun memberInboxTapFoldDarkHuge() = shoot("rvg34_member_inbox_tap_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, memberInbox(open = true))

    /** [RVG-34] Said once: cleared from the 알림함, the line does not come back on the next visit to the calendar. */
    @Test fun memberInboxOnce() = shoot("rvg34_member_inbox_once_fold_light_100", FOLDED) {
        memberHead(face = false, words = false).invoke(this)
        val inbox = com.bamnat.app.inbox.Inbox.get(c.appContext)
        inbox.clearAll()
        com.bamnat.app.ui.calendar.TeamLinkNote.once(c.appContext, teamId)
        assertEquals(0, inbox.rows.value.count { it.kind == com.bamnat.app.ui.calendar.TeamLinkNote.KIND })
    }

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
