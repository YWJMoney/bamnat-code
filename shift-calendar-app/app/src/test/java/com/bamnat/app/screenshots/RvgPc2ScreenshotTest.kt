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

    /** SN-13: the automatic update left the 그림 out; said once above the calendar (안내 카드 한 모양, 조건 3), with its X. */
    private fun calendarNotice(): Screen.() -> Unit = {
        c.shareLinks.save(ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://bamnat-share.example.workers.dev/s/Ab3dEfGh5JkLmNoPqRsTuv", "t",
            Today.instant().plus(java.time.Duration.ofDays(30)), "민지"))
        c.shareLinks.setDropped(com.bamnat.app.share.ShareLinkPayload.withoutRefused(JSONObject("""{"types":[{"icon":"e:☀️"}]}"""), "type_icon")!!.second)
        settle()
        assertTrue(appears("빼고 보냈어요", exact = false))
    }
    @Test fun calendarNoticeFoldLight() = shoot("rvg27_calendar_left_out_fold_light_100", FOLDED, calendarNotice())
    @Test fun calendarNoticeFoldDarkHuge() = shoot("rvg27_calendar_left_out_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, calendarNotice())
    @Test fun calendarNoticeOpenDarkHuge() = shoot("rvg27_calendar_left_out_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, calendarNotice())
    @Test fun calendarNoticeCloses() = shoot("rvg27_calendar_left_out_closed_fold_light_100", FOLDED) {
        calendarNotice().invoke(this)
        tapText("링크 안내 닫기", exact = true)
        assertTrue(gone("빼고 보냈어요", exact = false))
        assertEquals(null, c.shareLinks.dropped.value)
    }

    /** 검토 S7-3 (갈래 R 의견 3): the automatic update turned down twice running for something it cannot leave out; one line, its X. */
    private fun calendarRefused(): Screen.() -> Unit = {
        c.shareLinks.save(ShareLinkState("Ab3dEfGh5JkLmNoPqRsTuv", "https://bamnat-share.example.workers.dev/s/Ab3dEfGh5JkLmNoPqRsTuv", "t",
            Today.instant().plus(java.time.Duration.ofDays(30)), "민지"))
        c.shareLinks.setDropped(com.bamnat.app.share.ShareLinkPayload.REFUSED_AGAIN)
        settle()
        assertTrue(appears("바뀐 근무가 링크에 안 갔어요", exact = false))
    }
    @Test fun calendarRefusedFoldLight() = shoot("rvg27_calendar_refused_again_fold_light_100", FOLDED, calendarRefused())
    @Test fun calendarRefusedFoldDarkHuge() = shoot("rvg27_calendar_refused_again_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, calendarRefused())

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

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
