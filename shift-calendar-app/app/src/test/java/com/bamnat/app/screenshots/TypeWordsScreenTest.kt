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
    private fun screens(style: WorkStyle): List<Pair<String, Screen.() -> Unit>> = listOf(
        "달력" to { tab(0, wide = false) },
        "설정" to { tab(SETTINGS_TAB, wide = false) },
        "설정 › ${SettingsPage.WORK.titleFor(style)}" to { open(Place.SETTINGS_WORK) },
        "설정 › ${SettingsPage.ALARM.titleFor(style)}" to { open(Place.SETTINGS_ALARM) },
        "설정 › ${SettingsPage.NOTICES.titleFor(style)}" to { open(Place.SETTINGS_NOTICES) },
    )

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
        /** 2.0.0 처음 잰 것: 고치면 지워요. */
        const val KNOWN = """
        T30-80|설정 › NOTICES|전공의|DAY,HOURLY,FIELD,BUSINESS,INVEST
        T30-80|설정 › NOTICES|연속 근무|BUSINESS,INVEST
        T30-80|설정 › NOTICES|근무|INVEST
        T30-82|설정 › NOTICES|52시간|HOURLY
        T30-76|설정 › NOTICES|퇴근|FIELD,BUSINESS,INVEST
        T30-78|설정 › NOTICES|근무표|FIELD,BUSINESS,INVEST
        T30-78|설정 › NOTICES|회사|INVEST
        T30-83|설정 › NOTICES|연차|FIELD,BUSINESS,INVEST
        T30-92|설정 › NOTICES|근무별|FIELD,BUSINESS,INVEST
        T30-100|설정 › NOTICES|쉬는 날|BUSINESS
        T30-100|설정|쉬는 날|BUSINESS
        T30-100|달력|쉬는 날|BUSINESS
        T30-85|설정 › ALARM|근무마다|FIELD,BUSINESS,INVEST
        T30-85|설정 › ALARM|근무별|FIELD,BUSINESS,INVEST
        T30-99|설정 › ALARM|근무 알람|FIELD,BUSINESS,INVEST
        T30-84|설정 › ALARM|근무|INVEST
        T30-97|설정 › WORK|근무|INVEST
        """
    }
}
