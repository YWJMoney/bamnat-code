package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PayWay
import com.bamnat.app.ui.company.CommonItemLines
import com.bamnat.app.ui.company.CompanyHoursLine
import com.bamnat.app.ui.company.CompanyWayText
import com.bamnat.app.ui.company.DiffEditorSheet
import com.bamnat.app.ui.company.SetupEditor
import com.bamnat.app.ui.company.Trial
import com.bamnat.app.ui.company.TrialLine
import com.bamnat.app.ui.company.TrialSheet
import com.bamnat.app.ui.pay.ItemKind
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.PayslipCheck
import com.bamnat.core.pay.SlipProof
import com.bamnat.core.plaza.ProposalDraft
import com.bamnat.core.plaza.SetupEdit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [ITEM-05][ITEM-06][ITEM-07][ITEM-08][ITEM-10][PROP-02][PROP-03] 1.30 묶음 BC's screenshots, numbers 1340~1359: who sets an
 * allowance's amount (회사 공통·사람마다·%) when making one; the proposal form's allowances (고치기·빼기·더하기) and 통상시급
 * 기준 시간; the company's hours with 의견 보내기; the hours proposal made ready; 이 제안으로 내 월급 계산; the company's
 * common allowances as read-only lines; at 320 wide, 200% text and dark. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BCScreenshotTest : ScreenshotBase() {
    /** A made-up company's way as the 광장 keeps it: 180시간, 식대 회사 공통, 위험예지 통상임금의 10%, 자격수당 사람마다. */
    private val wayText = """{"app":"com.bamnat.app","format":"bamnat-company","v":4,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":4281352429,"start":"07:00","end":"19:00","break":60,"off":false},
          {"name":"야간","label":"야","color":4285291751,"start":"19:00","end":"07:00","break":60,"off":false},
          {"name":"휴무","label":"휴","color":4288585374,"break":0,"off":true}],
        "rotation":{"anchor":"2026-10-01","steps":["주간","주간","야간","야간",null,null]},
        "pay":{"fiveOrMore":true,"monthlyHours":180,"holidayMode":"FULL","withAmounts":false,"rules":{"night":0.5,"restDay":"LAW"},
          "items":[{"id":"c1","name":"식대","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[],"share":"FIXED","fixedAmount":200000},
            {"id":"c2","name":"위험예지수당","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[],"share":"PERCENT","percent":0.1,"percentOf":"ORDINARY","ordinary":true},
            {"id":"c3","name":"자격수당","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[],"ordinary":true}]}}"""

    private val way = PayWay("c-made-up", "한빛화학", "생산팀", "s1")

    // ---- 수당 만들기: 금액은 누가 정하나요? [ITEM-05] ----

    private fun monthlyItem(scroll: Boolean = true, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        itemBuilder(ItemKind.MONTHLY).invoke(this)
        settle()
        typeInto("이름 (선택)", "식대")
        settle()
        // At 320 wide and 200% text the dialog's own scroll is short of it; the top of the form is the picture there.
        if (scroll) reveal("금액은 누가 정하나요?", exact = false)
        then()
    }

    @Test fun itemWho() = shoot("1340_bc_item_who", FOLDED, monthlyItem {
        assertTrue(sees(ItemShare.FIXED.label))
        assertTrue(sees(ItemShare.PERCENT.label))
    })
    @Test fun itemPercent() = shoot("1341_bc_item_percent", FOLDED, monthlyItem {
        tapText(ItemShare.PERCENT.label, exact = true)
        settle()
        reveal("기본급2·위험예지처럼", exact = false)
    })
    @Test fun itemWhoSmallBig() = shoot("1342_bc_item_who_320_200", SMALL_PHONE, 2f, monthlyItem(scroll = false))

    // ---- 고쳐서 제안하기의 수당·시간 [ITEM-09][ITEM-10] ----

    private fun editor(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            var e by remember { mutableStateOf(SetupEdit.of(wayText)!!) }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SetupEditor(e, emptySet(), amounts = true, baseHours = 180) { e = it }
            }
        }
        settle()
        then()
    }

    @Test fun editorItems() = shoot("1343_bc_editor_items", FOLDED, editor {
        reveal("위험예지수당", exact = true)
        assertTrue(sees("통상임금의"))
    })
    @Test fun editorAddItem() = shoot("1344_bc_editor_add_item", FOLDED, editor {
        tapText("+ 근속수당", exact = true)
        settle()
        reveal("근속수당", exact = true)
    })
    @Test fun editorHours() = shoot("1345_bc_editor_hours", FOLDED, editor {
        reveal("통상시급 기준 시간 (한 달)", exact = false)
    })
    @Test fun editorSmallBig() = shoot("1346_bc_editor_320_200", SMALL_PHONE, 2f, editor())
    @Test fun editorDark() = shoot("1347_bc_editor_dark", FOLDED_NIGHT, editor { reveal("식대", exact = true) })

    // ---- 회사 규칙 시간과 의견 보내기 [ITEM-07][ITEM-08] ----

    private fun hours(mine: Int, then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content {
            Column(Modifier.padding(16.dp)) {
                CompanyHoursLine(CompanyWayText(way, wayText), mine) {}
                CommonItemLines(wayText, Modifier.padding(top = 16.dp))
            }
        }
        settle()
        then()
    }

    @Test fun companyHoursSame() = shoot("1348_bc_company_hours", FOLDED, hours(180) {
        assertTrue(sees("회사 규칙 180시간"))
        assertTrue(sees("회사 공통 · 매달 · 200,000원"))
    })
    @Test fun companyHoursDiffer() = shoot("1349_bc_company_hours_differ", FOLDED, hours(209) {
        assertTrue(sees("209시간으로 의견 보내기"))
    })

    private fun hoursDraft(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        val draft = ProposalDraft.hours(wayText, 209)!!
        content { DiffEditorSheet("한빛화학 · 생산팀", wayText, null, busy = false, error = null, onSend = { _, _, _ -> }, onDismiss = {}, draft = draft, alsoMine = true) }
        settle()
        then()
    }

    @Test fun hoursProposal() = shoot("1350_bc_hours_proposal", FOLDED, hoursDraft {
        assertTrue(sees("통상시급 기준 시간이 209시간으로 나와요"))
    })
    @Test fun hoursProposalAlsoMine() = shoot("1351_bc_hours_proposal_also_mine", FOLDED, hoursDraft {
        reveal("내 급여도 이 값으로 계산해요", exact = false)
    })

    // ---- 이 제안으로 내 월급 계산 [PROP-02] ----

    private val month = YearMonth.of(2026, 9)
    private val trial = Trial(wayText, month, now = 3_412_000, then = 3_455_000, lines = listOf(
        TrialLine("위험예지수당", 0, 268_000), TrialLine("자격수당", 225_000, 0), TrialLine("야간 근로 가산", 412_000, 412_000),
    ))
    private val slip = PayslipCheck(month, 3_460_000, 3_412_000, 0, SlipProof.MATCH)

    private fun proposalTrial(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        content { TrialSheet(trial, onClose = {}, onVote = {}, proposal = true, slip = slip) }
        settle()
        then()
    }

    @Test fun proposalTrialSheet() = shoot("1352_bc_proposal_trial", FOLDED, proposalTrial {
        assertTrue(sees("이 제안이 명세서에 더 가까워요"))
    })
    @Test fun proposalTrialSmallBig() = shoot("1353_bc_proposal_trial_320_200", SMALL_PHONE, 2f, proposalTrial())
    @Test fun proposalTrialDark() = shoot("1354_bc_proposal_trial_dark", FOLDED_NIGHT, proposalTrial())

    private fun Screen.sees(text: String): Boolean = runCatching { reveal(text, exact = false); true }.getOrDefault(false)

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
