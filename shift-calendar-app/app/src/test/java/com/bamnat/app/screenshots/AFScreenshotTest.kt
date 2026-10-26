package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.Membership
import com.bamnat.app.company.ReportStatus
import com.bamnat.app.company.RoomPage
import com.bamnat.app.company.RoomVersion
import com.bamnat.app.ui.company.AutoShareHost
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.settings.AutoShareRow
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [PLAZA-07]~[PLAZA-10] 1.26 묶음 AF's screenshots, numbers 1060~1069 (결정 D79): a company seen from outside (shifts
 * only, 급여 방식 있음), applying as joining and its refusal, the member's room with 제보 1 · 확인 중, 고친 점 cards and
 * 이 값 쓰기, 안정, the one-time ask and the 설정 switch — on a fold, a small phone, at 200% and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AFScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }

    /** The made-up server of [ScreenshotBase], with companies to join and rooms to read. */
    private inner class Members(private val base: FakeCompanyApi) : CompanyApi by base {
        var member = false
        var limited = false
        var joinError: String? = null
        var first = false
        var stable = false
        var others: List<RoomVersion> = emptyList()
        var joins = 0
        var autoSent = 0

        override suspend fun company(id: String): CompanyPage {
            val p = base.company(id)
            if (member) return p.copy(member = true, setups = p.setups.map { it.copy(stable = stable && it.adopted) })
            // Outside: shift times and order only, and that a pay way is there.
            return p.copy(member = false, limited = limited, setups = p.setups.map { s ->
                val pay = s.hasPay
                s.copy(text = JSONObject(s.text).apply { remove("pay") }.toString(),
                    lines = s.lines.filter { (l, _) -> l == "근무" || l.startsWith("순서") || l == "연장·휴무일 근무" }, payHidden = pay)
            })
        }

        override suspend fun join(device: String, company: String, cal: String): Membership {
            joins++
            joinError?.let { return Membership(false, company, it) }
            member = true
            return Membership(true, company)
        }

        override suspend fun room(device: String, company: String): RoomPage? = if (!member) null else RoomPage(
            company, "(주)예시정밀",
            base = RoomVersion("s1", !first, "생산팀", if (first) null else 2, base.file, if (first) 1 else 3, if (first) 1 else 3, 2, 4),
            mine = true, first = first, others = others, stable = stable, stableUses = if (stable) 4 else 1,
        )

        override suspend fun report(device: String, company: String, dept: String, setup: JSONObject, cal: String, auto: Boolean): ReportStatus {
            if (auto) autoSent++
            return base.report(device, company, dept, false, "", setup).copy(companyId = "c1", first = true, newInfo = true,
                member = Membership(true, "c1"))
        }

        override suspend fun terms() = com.bamnat.app.company.CompanyTerms(3, 3, firstMonths = 1)
    }

    private fun members(): Members = Members(company).also { m ->
        companyOn()
        c.companyApi = m
    }

    /** The user's own pay: 기본급 2,500,000, 야간 50%. */
    private fun ownPay() = runBlocking {
        c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(today, 2_500_000))),
            rules = CompanyRules(nightPremium = 0.5)), keepUndo = false)
    }

    private fun mine(): String = runBlocking { c.companyShare.exportForServer().toString() }

    private fun Screen.detail(m: Members): CompanyDetailViewModel {
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        return vm
    }

    // ---- [PLAZA-08] 참여 전: 근무만 ----
    @Test fun outsideTheCompanyOnlyShiftsShow() = shoot("1060_af_outside", FOLDED) {
        val m = members()
        // A phone that read too much today sees the shifts only, and is told why.
        m.limited = true
        detail(m)
        assertTrue(shows("오늘은 많이 봐서 근무 시각·순서만 보여요", exact = false))
        m.limited = false
        detail(m)
        assertTrue(shows("급여 방식 있음 · 확인 3명 · 쓰면 보여요", exact = true))
        assertTrue(shows("급여 계산 방식은 이 회사 설정을 쓰는(참여한) 분께만 보여요", exact = false))
        assertFalse(shows("급여 계산 방식 보기", exact = false))
        assertFalse(shows("우리 회사 광장", exact = true))
    }
    @Test fun theSwitchStopsIt() = shoot("1061_af_switch", SMALL_PHONE) {
        members()
        c.companyStore.setMember("c1", "(주)예시정밀")
        c.autoShare.set(true)
        assertTrue(c.autoShare.waiting)
        content { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { AutoShareRow() } }
        settle()
        assertTrue(shows("(주)예시정밀 광장에 고쳐 올려요", exact = false))
        tapText("회사 광장에 저절로 나누기", exact = true)
        settle()
        // [PLAZA-34] 1.27 AI: off is 바꿀 때마다 묻기 again, not never.
        assertNull(c.companyStore.autoShare.value)
        assertFalse(c.autoShare.waiting)
    }

    // ---- [PLAZA-07] 적용 = 참여 ----
    @Test fun applyingJoinsAndOpensTheRoom() = shoot("1062_af_apply_joins", FOLDED) {
        val m = members()
        m.first = true
        detail(m)
        reveal("이 설정 쓰기", exact = true)
        tapText("이 설정 쓰기", exact = true)
        if (shows("이 순서로 쓸게요", exact = true)) tapText("이 순서로 쓸게요", exact = true)
        settle()
        assertEquals(1, m.joins)
        assertEquals("c1", c.companyStore.member.value?.first)
        assertEquals("s1", c.companyStore.applied.value?.setup)
        // The pay way came with it: the file applied had it.
        assertTrue(JSONObject(c.companyStore.pendingPay ?: runBlocking { m.company("c1") }.setups.first().text).has("pay"))
    }
    @Test fun aSecondCompanyWithin30DaysIsSaidNo() = shoot("1063_af_join_wait", PHONE) {
        val m = members()
        m.joinError = "member_wait"
        detail(m)
        reveal("이 설정 쓰기", exact = true)
        tapText("이 설정 쓰기", exact = true)
        if (shows("이 순서로 쓸게요", exact = true)) tapText("이 순서로 쓸게요", exact = true)
        settle()
        assertTrue(shows("회사를 바꾼 지 30일이 지나면", exact = false))
        assertNull(c.companyStore.applied.value)
    }

    // ---- [PLAZA-09][PLAZA-10] 회사 방 ----
    @Test fun theFirstProvidersVersionShowsAtOnce() = shoot("1064_af_room_first", FOLDED) {
        val m = members()
        m.member = true
        m.first = true
        detail(m)
        assertTrue(shows("우리 회사 광장", exact = true))
        assertTrue(shows("처음 알려 준 판이에요", exact = false))
        assertTrue(shows("제보 1 · 확인 중", exact = true))
    }
    @Test fun onlyWhatOthersChangedShowsAsCards() = shoot("1065_af_room_cards", UNFOLDED) {
        ownPay()
        val m = members()
        m.member = true
        val own = mine()
        m.others = listOf(
            RoomVersion("s7", false, "생산팀", null, SetupEdit.of(own)!!.rate("night", 0.6).text, 3, 3, 1, 3),
            RoomVersion("s8", false, "생산팀", null, SetupEdit.of(own)!!.shiftTime("주간", "08:00", "19:00", 60).text, 1, 1, 0, 1),
        )
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        detail(m)
        assertTrue(shows("야간 50% → 60%로 바꾼 사람 3명", exact = true))
        assertTrue(shows("바꾼 사람 1명", exact = false))
    }
    @Test fun oneValueIsTakenAndTheAmountsStay() = shoot("1066_af_take_value", FOLDED) {
        ownPay()
        val m = members()
        m.member = true
        m.others = listOf(RoomVersion("s7", false, "생산팀", null, SetupEdit.of(mine())!!.rate("night", 0.6).text, 3, 3, 1, 3))
        detail(m)
        tapText("이 값 쓰기", exact = true)
        settle()
        val p = c.payStore.profile.value!!
        assertEquals(0.6, p.rules.nightPremium!!, 1e-9)
        assertEquals(2_500_000L, p.base.on(today).value)
        assertTrue(shows("야간을 바꿨어요. 내 금액은 그대로예요", exact = true))
        assertTrue(shows("내 설정과 다르게 쓰는 분이 아직 없어요.", exact = true))
    }
    @Test fun aSettledCompanyStopsNudging() = shoot("1067_af_room_stable", FOLDED_NIGHT) {
        val m = members()
        m.member = true
        m.stable = true
        detail(m)
        assertTrue(shows("이제 바뀐 값 알림은 보내지 않아요", exact = false))
        assertTrue(shows("안정", exact = true))
    }

    // ---- [PLAZA-07] 한 번 묻기와 설정 스위치 ----
    /** The ask on screen, raised the way a save raises it. */
    private fun Screen.ask(open: (String) -> Unit = {}) {
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        content { AutoShareHost(c, open) }
        assertTrue(c.autoShare.saved())
        repeat(100) { if (c.autoShare.ask.value == null) { Thread.sleep(20); settle() } }
        settle()
    }

    @Test fun theOneTimeAskSaysWhatGoesAndWhatNot() = shoot("1068_af_ask", SMALL_PHONE, 2f) {
        members()
        ask()
        assertTrue(shows("회사 광장에 나눌까요?", exact = true))
        assertTrue(shows("같은 회사 사람들이 쓰게 근무 순서·계산 방식(금액·이름 빼고)을 나눌까요?", exact = true))
        assertEquals(1, c.autoShare.ask.value?.rewardMonths)
        // [PLAZA-34] 1.27 AI: 이번엔 안 할게요 · 다시 묻지 않기 in place of the one no.
        assertTrue(shows("이번엔 안 할게요", exact = true))
        reveal("다시 묻지 않기", exact = true)
        assertNull(c.companyStore.autoShare.value)
    }
    @Test fun yesSharesAndOpensTheCompany() = shoot("1069_af_ask_yes", FOLDED) {
        val m = members()
        var opened: String? = null
        ask { opened = it }
        assertTrue(shows("(주)예시정밀 광장에 나눠요", exact = false))
        assertTrue(shows("처음 알려 주는 급여 방식이면 프로 1개월을 드려요.", exact = true))
        tapText("나눌게요", exact = true)
        repeat(100) { if (opened == null) { Thread.sleep(20); settle() } }
        // [PLAZA-34] 1.27 AI: one yes is for this time; the next change asks again with what changed.
        assertNull(c.companyStore.autoShare.value)
        assertEquals(1, m.autoSent)
        assertEquals("c1", opened)
        // Where yes leads: the company's 광장, as a member.
        m.member = true
        m.first = true
        detail(m)
        assertTrue(shows("우리 회사 광장", exact = true))
    }
}
