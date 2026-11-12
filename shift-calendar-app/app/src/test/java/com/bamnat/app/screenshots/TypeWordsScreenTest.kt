package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 검토 뿌리 고침 5번(갈래 H가 가진 시험, 도약 38번 '낱말 검사 0건'): 교대가 아닌 다섯 유형으로 앱을 열어 달력, 설정, 설정 › 알람·알림과 잠·
 * 내 근무를 그리고, 화면 글에 그 유형에 맞지 않는 말([TypeWords])이 있는지 훑어요. 사용자가 넣은 이름(근무 종류, 메모)은 빼요.
 *
 * 쓰는 법(다른 갈래도 같이 써요):
 * - 새 화면 글에 새는 말이 생기면 실패해요: 그 유형의 말로 바꾸거나 그 유형에서 숨겨요. 맞는 말이면 [TypeWords.allowed]에 구절을 더해요.
 * - 고쳐서 없어진 줄은 [KNOWN]에서 지워요(남아 있으면 '고쳐졌어요'로 실패). 그래서 목록은 줄어드는 쪽으로만 가요.
 * - 더 훑을 화면은 [screens]에 한 줄로 더해요. 줄 모양: `추적 번호|화면|낱말|유형들`.
 * - `-Pbamnat.typeWordsDump=true`면 찾은 것을 build/type-words-<유형>.txt에 적어요(목록을 새로 만들 때).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TypeWordsScreenTest : ScreenshotBase() {
    @Test fun dayWork() = sweep(WorkStyle.DAY)
    @Test fun partTime() = sweep(WorkStyle.HOURLY)
    @Test fun fieldWork() = sweep(WorkStyle.FIELD)
    @Test fun shop() = sweep(WorkStyle.BUSINESS)
    @Test fun invest() = sweep(WorkStyle.INVEST)

    /** The screens swept, by name: how to reach each one. */
    private fun screens(style: WorkStyle): List<Pair<String, Screen.() -> Unit>> = listOf<Pair<String, Screen.() -> Unit>>(
        "달력" to { tab(0, wide = false) },
        "설정" to { tab(SETTINGS_TAB, wide = false) },
        "설정 › ${SettingsPage.WORK.titleFor(style)}" to { open(Place.SETTINGS_WORK) },
        "설정 › ${SettingsPage.ALARM.titleFor(style)}" to { open(Place.SETTINGS_ALARM) },
        "설정 › ${SettingsPage.NOTICES.titleFor(style)}" to { open(Place.SETTINGS_NOTICES) },
    ) + listOfNotNull(
        // 2.0.0 [RVH-53] HQ-27: 근무 주기 (출근 요일·일정 순서) of the kinds that have one.
        ("근무 주기" to pattern).takeIf { style in setOf(WorkStyle.DAY, WorkStyle.HOURLY, WorkStyle.FIELD) },
    )

    private val pattern: Screen.() -> Unit = { open(Place.PATTERN) }

    private fun open(place: Place) {
        FeatureLinks.open(FeatureRoute(place))
        settle()
    }

    private fun sweep(style: WorkStyle) {
        val found = sortedSetOf<String>()
        val seen = sortedSetOf<String>()
        shoot("9300_typewords_${style.name.lowercase()}", PHONE, fresh(style) {
            val own = ownNames()
            for ((name, go) in screens(style)) {
                go()
                for (line in nodes(merged = false).flatMap { it.texts() }.distinct()) for (w in TypeWords.leaks(style, line, own)) {
                    found += "${screenKey(name)}|$w"
                    seen += "${screenKey(name)}|$w\t${line.replace('\n', ' ').take(90)}"
                }
            }
        })
        if (System.getProperty("bamnat.typeWordsDump") == "true") File("build/type-words-${style.name}.txt").writeText(seen.joinToString("\n"))
        val known = KNOWN.trimIndent().lines().filter { it.isNotBlank() }.mapNotNull { l ->
            val (_, screen, word, styles) = l.split('|')
            "$screen|$word".takeIf { style.name in styles.split(',') }
        }.toSortedSet()
        assertEquals("$style 새로 샌 말(그 유형의 말로 바꾸거나 숨겨요)", emptyList<String>(), (found - known).toList())
        assertEquals("$style 고쳐졌어요: KNOWN에서 지워 주세요", emptyList<String>(), (known - found).toList())
    }

    /**
     * 2.0.0 [RVH-53] HQ-27 (조건 13, 사용자 2026-10-29 '알바·파트타임 유형에서 회사 검색은 의미가 없다'): 근무 주기's 회사 이름으로
     * 근무주기 찾기 is where the work has a company: 교대·일근 always, an 알바 once it joined one, 현장 never.
     */
    @Test fun companySearchOnlyWhereTheWorkHasACompany() {
        val find = "회사 이름으로 근무주기 찾기"
        fun shows(style: WorkStyle, joined: Boolean = false): Boolean {
            var seen = false
            shoot("9301_typewords_company_${style.name.lowercase()}${if (joined) "_joined" else ""}", PHONE, fresh(style) {
                if (joined) {
                    app.container.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("c1", "(주)예시정밀", "매장", "s1", 1, true,
                        com.bamnat.core.time.Today.instant()))
                    app.container.companyStore.setMember("c1", "(주)예시정밀")
                }
                open(Place.PATTERN)
                seen = appears(find, exact = true)
            })
            return seen
        }
        assertEquals(listOf(true, true, false, true, false), listOf(shows(WorkStyle.SHIFT), shows(WorkStyle.DAY), shows(WorkStyle.HOURLY),
            shows(WorkStyle.HOURLY, joined = true), shows(WorkStyle.FIELD, joined = true)))
    }

    /** A screen's name without the page title that differs by kind ('내 근무' is '내 가게'), so one line serves every kind. */
    private fun screenKey(name: String): String = SettingsPage.entries.fold(name) { n, p ->
        WorkStyle.entries.fold(n) { m, s -> m.replace(" › ${p.titleFor(s)}", " › ${p.name}") }
    }

    /** The user's own names on this phone (근무 종류, 메모, 달력): theirs, not the app's words. */
    private fun ownNames(): List<String> = runBlocking {
        val c = app.container
        // The extra work the sample days hold (a 야간 대근 from [seed]) is the user's record, not a word of the screen.
        val snap = c.scheduleRepository.snapshot.first()
        val types = snap.shiftTypes.flatMap { listOf(it.name, it.label) } +
            snap.entries.flatMap { e -> e.extras.map { snap.kinds.of(it.kindId).name } }
        val memos = c.memoRepository.memos.first().flatMap { m -> listOf(m.title, m.body) + m.body.lines() + m.checklist.map { it.text } }
        (types + memos + c.calendars.calendars.value.map { it.name }).filter { it.isNotBlank() }.distinct()
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text)

    private companion object {
        /** 2.0.0 처음 잰 17줄은 모두 고쳤어요([RVH-41]). 새로 새는 말은 여기에 넣지 말고 그 유형의 말로 고쳐요. */
        const val KNOWN = """
        """
    }
}
