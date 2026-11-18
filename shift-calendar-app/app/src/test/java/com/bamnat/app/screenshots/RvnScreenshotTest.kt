package com.bamnat.app.screenshots

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.abs

/**
 * 2.0.0 갈래 N (회사·광장 앱): the screens its app PR touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after sheet
 * (디자인 품질 조건 16가지). Written against what both the old and new screens show (the same taps), so the same test draws the 'before'
 * on the commit before the change; [RvnFeedScreenshotTest] draws the 광장 cards, new in this PR.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvnScreenshotTest : ScreenshotBase() {
    /** The terms a shot set (3차 roomMoneyOn) go with it. */
    @org.junit.After fun clearTerms() = rvnTerms(null)

    /** 임금표 with a 일당 of 150,000원: '내 급여에 넣기' on 형틀목수 (가운데 250,000원). Before: it changed the pay at once. */
    private fun rateApply(): Screen.() -> Unit = {
        val c = companyOn()
        runBlocking {
            c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile().copy(wageType = WageType.DAILY,
                base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 150_000)))), keepUndo = false)
        }
        val vm = com.bamnat.app.ui.company.RatesViewModel(c)
        content { com.bamnat.app.ui.company.RatesScreen(vm, onBack = {}) }
        settle()
        tapText("내 급여에 넣기", exact = true)
        settle()
    }

    /** 이 설정 쓰기 over the calendar's own order: the pay question, then the rows with what changes. */
    private fun setupChanges(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(companyOn(), "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, ready = true, openPhasesFor = "s1") }
        settle()
        if (shows("근무주기만 넣을게요", exact = true)) tapText("근무주기만 넣을게요", exact = true)
        settle()
        reveal("이 순서로 쓸게요", exact = true)
    }

    /** 내가 보낸 정보: 거두기 on an adopted setup. */
    private fun takeBackAdopted(): Screen.() -> Unit = {
        val c = companyOn()
        val r1 = company.mine.first { it.status == "adopted" }
        c.companyStore.addSent(com.bamnat.app.company.SentReport(r1.id, r1.company, r1.dept, r1.ask, r1.createdAt))
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanySentScreen(vm, onBack = {}, onShare = {}) }
        settle()
        appears("채택", exact = false)
        tapText("거두기", exact = true)
        settle()
    }

    /** The talk on a rule set, its ⋮ open, then 신고하기. */
    private fun talkFlag(open: Boolean): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}) }
        vm.talk.show("c-v1", "s-v1", "가나다화학 · 품질팀")
        settle()
        if (open) {
            check(appears("가나다화학")) { "토론 창이 열리지 않았어요" }
            // 품질지킴이's post (not mine: mine offers 지우기, not 신고하기), its ⋮ found by its row.
            // Scrolled by the list's own action: at 200% a drag can start on the scrim above the sheet and close it.
            repeat(8) {
                if (nodes().any { n -> n.rvnSaid().any { "품질지킴이" in it } && n.boundsInWindow.height > 1f }) return@repeat
                val list = nodes().firstOrNull { n ->
                    val r = n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    r != null && r.value() < r.maxValue() && n.config.contains(SemanticsActions.ScrollBy)
                } ?: return@repeat
                list.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 600f)
                settle()
            }
            val name = nodes().first { n -> n.rvnSaid().any { "품질지킴이" in it } }.boundsInWindow.center.y
            val more = nodes().filter { n -> "더 보기" in n.rvnSaid() && n.config.contains(SemanticsActions.OnClick) }
                .minBy { abs(it.boundsInWindow.center.y - name) }
            more.config[SemanticsActions.OnClick].action!!.invoke()
            settle()
            // The ⋮ menu is a DropdownMenu, a popup window the shared helpers do not read: its 신고하기 is pressed in that window.
            rvnPopupNode("신고하기").config[SemanticsActions.OnClick].action!!.invoke()
            settle()
            check(appears("이 글을 신고할까요?", exact = true)) { "신고 창이 열리지 않았어요" }
        }
    }

    /** HQ-38 (총괄 11-12): a 광장 방's talk (이야기·질문·팁·자랑) with its write box at the bottom, as rvn_10 is for a rule's talk. */
    private fun roomTalk(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}) }
        vm.talk.show("style-shift", com.bamnat.core.plaza.StylePlaza.ROOM, "교대 광장")
        settle()
        check(appears("교대 광장")) { "광장 방 토론이 열리지 않았어요" }
    }

    /** HQ-38 (디자인 조건 1·12): at 200% on a folded screen the write box with its '쓸 글' keeps to the sheet's last quarter. */
    private fun Screen.writeBoxInLastQuarter() {
        val all = nodes()
        val bottom = all.maxOf { it.boundsInWindow.bottom }
        val top = all.filter { n -> n.rvnSaid().any { "쓸 글" in it } }.minOf { it.boundsInWindow.top }
        check(bottom - top <= bottom * 0.25f) { "쓸 글 칸이 화면의 ${((bottom - top) / bottom * 100).toInt()}%예요" }
    }

    /** 총괄 11-12: 임금표 at its end, the last card's 맞아요·달라요 clear of the floating '시세 알려 주기'. */
    private fun ratesEnd(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.RatesViewModel(companyOn())
        content { com.bamnat.app.ui.company.RatesScreen(vm, onBack = {}) }
        settle()
        repeat(16) {
            val list = nodes().firstOrNull { n ->
                val r = n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                r != null && r.value() < r.maxValue() && n.config.contains(SemanticsActions.ScrollBy)
            } ?: return@repeat
            list.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1500f)
            settle()
        }
    }

    /**
     * 2.0.0 2차 COMP-05·COMP-15: 회사 규칙 목록을 끝까지 내린 모습. 이름 찾기 칸은 위에 붙어 있고, 끝은 '여기까지예요'(전에는 아무 말 없이 멈췄어요).
     * Same taps on the commit before, for the 'before'.
     */
    private fun boardEnd(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}) }
        settle()
        repeat(16) {
            val list = nodes().firstOrNull { n ->
                val r = n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                r != null && r.value() < r.maxValue() && n.config.contains(SemanticsActions.ScrollBy)
            } ?: return@repeat
            list.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1500f)
            settle()
        }
    }

    /** 2.0.0 2차 HQ-51: 설정 › 알림과 잠의 '알림함에 올 반응' 한 줄을 펼친 모습. 그 전 판에는 이 줄이 없어 '밤낮 소식'까지만 보여요. */
    private fun reactionSwitches(): Screen.() -> Unit = {
        app.container.settingsRepository.update { it.copy(plazaNews = true) }
        settingsPage("알림과 잠")
        reveal("밤낮 소식", exact = true)
        if (shows("알림함에 올 반응", exact = true) || appears("알림함에 올 반응", exact = true, ms = 500)) {
            reveal("알림함에 올 반응", exact = true)
            tapText("알림함에 올 반응", exact = true)
            settle()
            // The head stays in sight with the first switches under it (revealing the last line cut the head at the top).
            reveal("광장의 내 글", exact = true)
        }
    }

    /**
     * 2.0.0 3차 COMP-12: 이 설정 쓰기 on a calendar held by ‘가나상사’ (12월 10일까지). Now asked before an order is picked; before, the
     * same taps went on to 이 순서로 쓸게요 and ended in a red band.
     */
    private fun held(fails: String? = null): Screen.() -> Unit = {
        val c = companyOn()
        val until = LocalDate.of(2026, 12, 10).atTime(12, 0).atZone(com.bamnat.core.time.Today.zone()).toInstant()
        company.held = com.bamnat.app.company.Membership(false, "c1", "member_wait", until = until, undo = true, held = "가나상사")
        company.undoFails = fails
        c.companyStore.setMember("c9", "가나상사")
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, ready = true, openPhasesFor = "s1") }
        settle()
        check(appears("정해져 있어요")) { "잡힌 달력을 묻는 창이 뜨지 않았어요" }
        if (fails != null) {
            // '잘못 골랐어요' › 풀고 이어서 넣기, refused (once a year used): the card keeps the reason and offers 문의하기.
            tapText("잘못 골랐어요", exact = true)
            settle()
            tapText("풀고 이어서 넣기", exact = true)
            settle()
            check(appears("문의하기", exact = true)) { "기다림 카드가 뜨지 않았어요" }
        }
    }

    /** 2.0.0 3차 COMP-14: a 광장 방 with 방 글에 금액 받기 on: the line on writing keeps only 이름·연락처. */
    private fun roomMoneyOn(): Screen.() -> Unit = {
        company.roomMoney = true
        rvnTerms(com.bamnat.app.company.CompanyTerms(3, 3, roomMoney = true))
        roomTalk()()
    }

    @Test fun heldLight() = shoot("rvn_33_held_folded", FOLDED, held())
    @Test fun heldDarkBig() = shoot("rvn_34_held_folded_dark_200", DARK, 2f, held())
    @Test fun heldWide() = shoot("rvn_35_held_unfolded", UNFOLDED, held())
    @Test fun heldUsedLight() = shoot("rvn_36_held_used_folded", FOLDED, held("undo_used"))
    @Test fun heldUsedDarkBig() = shoot("rvn_37_held_used_folded_dark_200", DARK, 2f, held("undo_used"))
    @Test fun heldUsedWide() = shoot("rvn_38_held_used_unfolded_dark", DARK_WIDE, held("undo_used"))
    @Test fun roomMoneyLight() = shoot("rvn_39_room_money_folded", FOLDED, roomMoneyOn())
    @Test fun roomMoneyDarkBig() = shoot("rvn_40_room_money_folded_dark_200", DARK, 2f, roomMoneyOn())
    @Test fun roomMoneyWide() = shoot("rvn_41_room_money_unfolded", UNFOLDED, roomMoneyOn())

    @Test fun rateApplyLight() = shoot("rvn_01_rate_apply_folded", FOLDED, rateApply())
    @Test fun rateApplyDarkBig() = shoot("rvn_02_rate_apply_folded_dark_200", DARK, 2f, rateApply())
    @Test fun rateApplyWide() = shoot("rvn_03_rate_apply_unfolded", UNFOLDED, rateApply())
    @Test fun setupChangesLight() = shoot("rvn_04_setup_changes_folded", FOLDED, setupChanges())
    @Test fun setupChangesDarkBig() = shoot("rvn_05_setup_changes_folded_dark_200", DARK, 2f, setupChanges())
    @Test fun setupChangesWide() = shoot("rvn_06_setup_changes_unfolded_dark", DARK_WIDE, setupChanges())
    @Test fun takeBackLight() = shoot("rvn_07_take_back_adopted_folded", FOLDED, takeBackAdopted())
    @Test fun takeBackDarkBig() = shoot("rvn_08_take_back_adopted_folded_dark_200", DARK, 2f, takeBackAdopted())
    @Test fun talkLight() = shoot("rvn_09_talk_folded", FOLDED, talkFlag(false))
    @Test fun talkDarkBig() = shoot("rvn_10_talk_folded_dark_200", DARK, 2f) { talkFlag(false)(); writeBoxInLastQuarter() }
    @Test fun talkWide() = shoot("rvn_11_talk_unfolded", UNFOLDED, talkFlag(false))
    @Test fun flagLight() = shoot("rvn_12_flag_folded", FOLDED, talkFlag(true))
    @Test fun flagDarkBig() = shoot("rvn_13_flag_folded_dark_200", DARK, 2f, talkFlag(true))
    @Test fun talkBigLight() = shoot("rvn_14_talk_folded_200", FOLDED, 2f, talkFlag(false))
    @Test fun roomTalkLight() = shoot("rvn_15_room_talk_folded", FOLDED, roomTalk())
    @Test fun roomTalkDarkBig() = shoot("rvn_16_room_talk_folded_dark_200", DARK, 2f) { roomTalk()(); writeBoxInLastQuarter() }
    @Test fun roomTalkWideBig() = shoot("rvn_17_room_talk_unfolded_200", UNFOLDED, 2f, roomTalk())
    @Test fun ratesEndLight() = shoot("rvn_25_rates_end_folded", FOLDED, ratesEnd())
    @Test fun ratesEndDarkBig() = shoot("rvn_26_rates_end_folded_dark_200", DARK, 2f, ratesEnd())
    @Test fun boardEndLight() = shoot("rvn_27_board_end_folded", FOLDED, boardEnd())
    @Test fun boardEndDarkBig() = shoot("rvn_28_board_end_folded_dark_200", DARK, 2f, boardEnd())
    @Test fun boardEndWide() = shoot("rvn_29_board_end_unfolded", UNFOLDED, boardEnd())
    @Test fun reactionSwitchesLight() = shoot("rvn_30_reaction_switches_folded", FOLDED, reactionSwitches())
    @Test fun reactionSwitchesDarkBig() = shoot("rvn_31_reaction_switches_folded_dark_200", DARK, 2f, reactionSwitches())
    @Test fun reactionSwitchesWide() = shoot("rvn_32_reaction_switches_unfolded_dark", DARK_WIDE, reactionSwitches())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DARK_WIDE = "w707dp-h823dp-port-night-420dpi"
    }
}

/** What a node says: its words and its content description. */
internal fun SemanticsNode.rvnSaid(): List<String> =
    config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

/**
 * A pressable node showing [text] in an open popup window (a DropdownMenu): the shared helpers read the activity and its dialogs only,
 * so a ⋮ menu's item is found here, in the window manager's own list, and pressed by its click action.
 */
internal fun rvnPopupNode(text: String): SemanticsNode {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(global.getMethod("getInstance").invoke(null)) as List<*>
    fun rootOf(v: View): ViewRootForTest? {
        if (v is ViewRootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) rootOf(v.getChildAt(i))?.let { return it }
        return null
    }
    return views.filterIsInstance<View>().asReversed().firstNotNullOfOrNull { v ->
        rootOf(v)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = true)
            ?.firstOrNull { n -> n.rvnSaid().any { it == text } && n.config.contains(SemanticsActions.OnClick) }
    } ?: error("팝업에서 ‘$text’를 찾지 못했어요")
}

/** 2.0.0 3차: the terms a run read (the app keeps them once a run), set for a shot and taken back by the next read. */
internal fun rvnTerms(t: com.bamnat.app.company.CompanyTerms?) {
    val k = Class.forName("com.bamnat.app.ui.company.TermsCache")
    k.getDeclaredField("value").apply { isAccessible = true }.set(null, t)
}
