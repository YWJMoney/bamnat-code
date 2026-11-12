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
