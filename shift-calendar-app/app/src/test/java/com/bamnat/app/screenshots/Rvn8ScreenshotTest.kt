package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.LeadLine
import com.bamnat.app.ui.company.CompanyFindViewModel
import com.bamnat.app.ui.company.NewerWayLine
import com.bamnat.app.ui.company.PlazaStarter
import com.bamnat.app.ui.company.SameWorkRows
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.work.WorkStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-24] 갈래 N 8차 화면 사진: 검토 I10ac-02 '내 근무 순서와 같은 곳', I10ac-04 '고친 판이 나왔어요 · 바꾸면 이번 달 +12,300원',
 * PC-211 현장·알바 광장 시작 꾸러미, T22c-04·I10ac-03 공개 범위 창(현장 공종·가게 업종, 근무가 끝나면 한 줄 묻기), I22cb-02 도움말 '같은 근무
 * 분들의 답'. 8차 전에는 없던 줄이라 앞 사진은 없고(PR 본문), 뒤 사진만 찍어요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn8ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun page(body: @androidx.compose.runtime.Composable () -> Unit): Screen.() -> Unit = {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) { body() }
        }
        settle()
        waitFor(300)
    }

    /** 회사 찾기 맨 위: before a name is typed, one company that has the same order and two public rows. */
    private fun same(): Screen.() -> Unit = page {
        val hit = CompanyHit("c1", "(주)예시정밀", null, 12, 0, users = 31,
            lead = LeadLine("s1", "생산팀", true, listOf("D", "D", "N", "N", null, null), null, emptySet(), true, 9, 0))
        val rows = PublicCycles.all.take(2)
        SameWorkRows(CompanyFindViewModel.SameWork(listOf(hit), rows), onCompany = {}, onRow = {})
    }

    /** 급여 방식 카드의 새 판 줄: with what the newer version would make of this month. */
    private fun newer(): Screen.() -> Unit = page {
        SoftCard { Column(Modifier.padding(16.dp)) { NewerWayLine(error = null, onTake = {}, diff = com.bamnat.core.time.Today.month() to 12_300L) } }
    }

    private fun starter(style: WorkStyle): Screen.() -> Unit = {
        c.settingsRepository.update { it.copy(workStyle = style) }
        page { PlazaStarter(empty = true, roomName = if (style == WorkStyle.FIELD) "현장 광장" else "알바 광장", workingNow = null, style = style, onStart = {}) }()
    }

    private fun tag(style: WorkStyle, room: String): Screen.() -> Unit = {
        companyOn()
        c.settingsRepository.update { it.copy(workStyle = style) }
        content { com.bamnat.app.ui.company.PlazaTagScopeDialog(c, room, onDismiss = {}) }
        settle()
        waitFor(400)
    }

    /** 문의 › 도움말: the 알람 answer opened, with the 답 모음 pick on that subject under it. */
    private fun help(): Screen.() -> Unit = {
        val real = companyOn()
        inquiriesOn()
        plazaPeopleOn(real)
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val base = real.companyApi
        real.companyApi = object : com.bamnat.app.company.CompanyApi by base {
            override suspend fun plazaPicks(room: String) = listOf(
                com.bamnat.app.company.PlazaPick("k1", "알람이 늦게 울릴 때 이렇게 했어요", null, "근무 알람이 늦게 울려요",
                    "배터리 최적화에서 밤낮을 빼고, 알람 점검에서 정확한 알람 권한을 켰더니 그 뒤로 제시간에 울려요.", "alarm"))
        }
        val vm = com.bamnat.app.ui.settings.InquiryViewModel(c)
        content { com.bamnat.app.ui.settings.InquiryScreen(vm, onBack = {}) }
        settle()
        tapText("알람이 안 울리거나 늦게 울려요")
        settle()
        waitFor(600)
        scrolledTo("같은 근무 분들의 답")
        settle()
    }

    @Test fun sameLight() = shoot("rvn_140_same_work_folded", FOLDED, same())
    @Test fun sameDarkBig() = shoot("rvn_141_same_work_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, same())
    @Test fun sameWide() = shoot("rvn_142_same_work_unfolded", UNFOLDED, same())
    @Test fun newerLight() = shoot("rvn_143_newer_diff_folded", FOLDED, newer())
    @Test fun newerDarkBig() = shoot("rvn_144_newer_diff_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, newer())
    @Test fun starterFieldLight() = shoot("rvn_145_starter_field_folded", FOLDED, starter(WorkStyle.FIELD))
    @Test fun starterFieldDarkBig() = shoot("rvn_146_starter_field_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, starter(WorkStyle.FIELD))
    @Test fun starterHourlyLight() = shoot("rvn_147_starter_hourly_folded", FOLDED, starter(WorkStyle.HOURLY))
    @Test fun starterHourlyDarkBig() = shoot("rvn_148_starter_hourly_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, starter(WorkStyle.HOURLY))
    @Test fun tagFieldLight() = shoot("rvn_149_tag_field_folded", FOLDED, tag(WorkStyle.FIELD, StylePlaza.FIELD))
    @Test fun tagFieldDarkBig() = shoot("rvn_150_tag_field_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, tag(WorkStyle.FIELD, StylePlaza.FIELD))
    @Test fun tagShopLight() = shoot("rvn_151_tag_shop_folded", FOLDED, tag(WorkStyle.BUSINESS, StylePlaza.SHOP))
    @Test fun tagShopWide() = shoot("rvn_152_tag_shop_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, tag(WorkStyle.BUSINESS, StylePlaza.SHOP))
    @Test fun helpLight() = shoot("rvn_153_help_picks_folded", FOLDED, help())
    @Test fun helpDarkBig() = shoot("rvn_154_help_picks_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, help())
}
