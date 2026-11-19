package com.bamnat.app.ui.settings

import com.bamnat.core.tier.InviteTerms
import com.bamnat.core.tier.WorkPlanTable
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 갈래 J 15차 (검토 05c 사장님·가게 도구·도움말 색인): 설정 찾기가 꼭 맞는 것과 비슷한 것을 나누고 ([RVJ-93] BOSS-70·U05c-10),
 * 관리 화면과 사장님 기능을 사장님 말로 찾고 ([RVJ-93] BOSS-68·BOSS-75 ⑤), 도움말이 화면에 없는 말을 따옴표로 적지 않고 ([RVJ-93]
 * BOSS-72·BOSS-75 ④), 사장님·리더·1.39 문답이 깃발과 유형을 따라 보이는지 ([RVJ-94] BOSS-73·T05c-12·I05ca-05).
 */
class RvjHelpSearchTest {
    private val allOn = HelpExtras(HelpExtras.FLAGS.toSet())

    // ---- BOSS-75 ④ · BOSS-72: 도움말의 ‘따옴표 말’은 화면에 있는 말 ----

    /** What the app can draw: its Kotlin strings (comments taken out, HelpFaq itself left out) and strings.xml. */
    private val screenText: String by lazy {
        val files = listOf(File("src/main/java"), File("../core/src/main/kotlin")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "HelpFaq.kt" }.toList()
        }
        assertTrue("the sources are found from ${File(".").absolutePath}", files.size > 100)
        files.joinToString("\n") { stripComments(it.readText()) } + "\n" + File("src/main/res/values/strings.xml").readText()
    }

    private fun stripComments(s: String): String =
        s.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("""(^|\s)//[^\n]*"""), "$1")

    /** Strings with a value put in ("광고 보고 ${bundle.label} 7일 열기"), as patterns a quoted word may fill. */
    private val templates: List<Regex> by lazy {
        Regex(""""((?:[^"\\\n]|\\.)*)"""").findAll(screenText).map { it.groupValues[1] }.filter { '$' in it }.mapNotNull { lit ->
            val parts = lit.split(Regex("""\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_.]*"""))
            if (parts.joinToString("").replace(Regex("""\s"""), "").length < 4) null
            else Regex(parts.joinToString(".+?") { Regex.escape(it) })
        }.toList()
    }

    /** Words an answer quotes as what people type, not what a screen says. */
    private val typed = setOf("내일 3시", "다음 야간 때 알려 줘", "내일 저녁 6시 치과")
    private val typedDate = Regex("""^(\d+/\d+|다음주 [월화수목금토일]+) .+""")

    /** Shapes with a number or a name in them, and the screen that draws each. */
    private val shapes = mapOf(
        "급여 방식 있음 · 확인 n명" to "CompanyFindScreen: 급여 방식 있음 · 확인 \${…}명",
        "이 달력에 안 맞아 숨긴 기능 n개 · 더 보기" to "PageEditSheet.FitMore: \$title \${others.size}개 · 더 보기",
        "이달 시간외 41/57" to "CalendarScreen: 이달 시간외 \${it.text}",
    )

    private fun onScreen(word: String): Boolean =
        word in screenText || templates.any { it.matches(word) } || word in typed || typedDate.matches(word) || word in shapes

    @Test fun everyQuotedWordInTheAnswersIsOnAScreen() {
        val missing = sortedSetOf<String>()
        val styles = listOf<WorkStyle?>(null) + WorkStyle.entries
        for (style in styles) for (t in HelpFaq.topics(true, InviteTerms.DEFAULT, allOn).forStyle(style)) {
            for (text in listOfNotNull(t.question, t.answer, t.action?.first)) {
                for (m in Regex("‘([^’]+)’").findAll(text)) {
                    val word = m.groupValues[1]
                    if (!onScreen(word)) missing += "${style ?: "-"} ${t.id}: ‘$word’"
                }
            }
        }
        File("build/help-quoted-words.txt").writeText(missing.joinToString("\n"))
        assertEquals("${missing.size}개 (build/help-quoted-words.txt)", emptyList<String>(), missing.toList())
        // The shapes are still what the answers say (an answer that drops one leaves no stale exception here).
        val said = HelpFaq.topics(true, InviteTerms.DEFAULT, allOn).joinToString("\n") { it.answer + it.question }
        for (s in shapes.keys) assertTrue("‘$s’ is still quoted", "‘$s’" in said)
    }

    /** BOSS-72 (낡은 답 여섯 곳): the ones not already kept by ReviewOSettingsTest.theHelpAnswersAreUpToDate. */
    @Test fun theOldAnswersSayWhatTheScreensSayNow() {
        val topics = HelpFaq.topics(true, InviteTerms.DEFAULT, allOn)
        val update = topics.single { it.id == "recipeUpdate" }.answer
        assertTrue(update, "‘바뀐 점 보고 반영’" in update)
        assertFalse(update, "새 판 보기" in update)
        val stack = topics.single { it.id == "noticeStack" }.answer
        assertTrue(stack, "알림함" in stack && "‘전체 지움’" in stack)
        assertFalse(stack, "알림 접기" in stack)
        // T05c-11: a shop's 카드 수수료 answer names its own settings page.
        val cash = HelpFaq.topics(true, InviteTerms.DEFAULT).forStyle(WorkStyle.BUSINESS).single { it.id == "shopCash" }.answer
        assertTrue(cash, "설정 › 내 가게 ›" in cash)
        assertFalse(cash, "내 근무 ›" in cash)
    }

    // ---- BOSS-73 · T05c-12 · I05ca-05: 사장님·리더·1.39 문답 ----

    @Test fun bossAndLeaderAnswersWaitForTheirFlagsAndKinds() {
        val off = HelpFaq.topics(true, InviteTerms.DEFAULT)
        assertTrue("no answer about a feature that is off", off.none { it.flag != null })
        val on = HelpFaq.topics(true, InviteTerms.DEFAULT, allOn)
        val boss = on.filter { it.tag == "사장님" }
        val leader = on.filter { it.tag == "팀 리더" }
        assertTrue("${boss.size}", boss.size >= 7)
        assertTrue("${leader.size}", leader.size >= 4)
        assertTrue(on.map { it.id }.containsAll(listOf("followRoster", "cellLines", "dayStart", "taskQuick")))
        // Only one flag on: only its answers.
        val bossOnly = HelpFaq.topics(true, InviteTerms.DEFAULT, HelpExtras(setOf(com.bamnat.core.flags.FlagCatalog.BOSS_PAYROLL)))
        assertTrue(bossOnly.any { it.tag == "사장님" })
        assertFalse(bossOnly.any { it.tag == "팀 리더" })
        // The kinds of work the features are offered to (FeatureRegistry boss.payroll, team.leader).
        val bossKinds = com.bamnat.core.guide.FeatureRegistry.get("boss.payroll")!!.styles
        val leaderKinds = com.bamnat.core.guide.FeatureRegistry.get("team.leader")!!.styles
        for (style in WorkStyle.entries) {
            val shown = on.forStyle(style)
            assertEquals("$style 사장님", style in bossKinds, shown.any { it.tag == "사장님" })
            assertEquals("$style 리더", style in leaderKinds, shown.any { it.tag == "팀 리더" })
        }
        // A shop's first answers: its own, then the 급여 대장.
        assertTrue(on.forStyle(WorkStyle.BUSINESS).take(4).any { it.id == "bossPayroll" })
        // Each kind's words, and short.
        for (style in WorkStyle.entries) for (t in on.forStyle(style)) {
            for (text in listOfNotNull(t.question, t.answer, t.action?.first)) {
                assertEquals("$style ${t.id}: $text", emptyList<String>(), TypeWords.leaks(style, text))
            }
            assertTrue(t.id, t.answer.length <= 220)
        }
        assertEquals(on.size, on.map { it.id }.toSet().size)
        // A 현장's row is named as its 화면과 글자 names it; a shop has no 야간.
        assertTrue(CELL_LIST_ROW in on.forStyle(WorkStyle.FIELD).single { it.id == "cellLines" }.answer)
        assertFalse("야간" in on.forStyle(WorkStyle.BUSINESS).single { it.id == "dayStart" }.answer)
        // The free counts are the server's plans.
        val plans = WorkPlanTable(freeStaff = 5, freeTeamMonths = 2)
        val told = HelpFaq.topics(true, null, HelpExtras(allOn.flags, plans))
        assertTrue("직원 5명까지 무료" in told.single { it.id == "bossPlan" }.answer)
        assertTrue("2달 확정까지" in told.single { it.id == "leaderPlan" }.answer)
        assertTrue("첫 달 확정까지" in on.single { it.id == "leaderPlan" }.answer)
    }

    /** BOSS-75 ⑤: a 사장님 who asks in a 사장님's words gets the 사장님's answer first. */
    @Test fun aBossAsksInABossesWords() {
        val shop = HelpFaq.topics(true, InviteTerms.DEFAULT, allOn).forStyle(WorkStyle.BUSINESS)
        assertEquals("bossContract", HelpFaq.search("근로계약서", shop).first().id)
        assertEquals("bossCover", HelpFaq.search("대타", shop).first().id)
        assertEquals("bossInsurance", HelpFaq.search("4대보험", shop).first().id)
        assertEquals("bossFilings", HelpFaq.search("원천세", shop).first().id)
        val shift = HelpFaq.topics(true, InviteTerms.DEFAULT, allOn).forStyle(WorkStyle.SHIFT)
        assertEquals("leaderWishes", HelpFaq.search("희망 휴무", shift).first().id)
    }

    // ---- BOSS-68 · BOSS-75 ⑤: 관리 화면을 찾기로 ----

    @Test fun theManageScreenIsFoundOnlyWhileItIsThere() {
        val without = SettingsIndex.entries(style = WorkStyle.BUSINESS)
        assertTrue(SettingsIndex.search("관리", without).none { (it.place as? SettingPlace.Screen)?.go == SettingsGo.MANAGE })
        val with = SettingsIndex.entries(style = WorkStyle.BUSINESS, manage = "가게 관리")
        for (q in listOf("관리", "사장님", "직원 관리", "급여 대장", "가게 관리")) {
            val first = SettingsIndex.search(q, with).first()
            assertEquals(q, SettingsGo.MANAGE, (first.place as? SettingPlace.Screen)?.go)
            assertEquals(q, "가게 관리", first.title)
        }
        val team = SettingsIndex.entries(style = WorkStyle.SHIFT, manage = "팀 관리")
        assertEquals("팀 관리", SettingsIndex.search("리더", team).first().title)
    }

    // ---- BOSS-70 · U05c-10: 꼭 맞는 것과 비슷한 것 ----

    @Test fun oneWordOfTwoIsOnlyNear() {
        // Two words, both in the name or keywords: sure.
        assertTrue(SearchText.fit("알람 소리", "알람음", listOf("소리")).sure)
        // The whole query in a keyword: sure. A keyword in the query and every word found: sure ('알람이' is 알람).
        assertTrue(SearchText.fit("다크", "테마", listOf("다크 모드")).sure)
        assertTrue(SearchText.fit("알람이", "알람음", listOf("알람")).sure)
        // One word of two: near, though it is found.
        val one = SearchText.fit("근로계약서 대타", "대타 찾기", listOf("대타"))
        assertTrue(one.score > 0)
        assertFalse(one.sure)
        // Words only in the text around it: near.
        val other = SearchText.fit("배터리 최적화", "알람 점검", emptyList(), "배터리 최적화를 끄면 알람이 늦지 않아요")
        assertFalse(other.sure)
        // A one-letter word counts as the name's first letter or a whole keyword, not as any letter of a name.
        assertEquals(0, SearchText.fit("달", "기념일 알림", listOf("기념일")).score)
        assertTrue(SearchText.fit("달", "달력 색", emptyList()).score > 0)
    }

    @Test fun nearFitsWaitApartThreeAtMost() {
        val items = listOf("대타 찾기", "대타는 주휴에서 빼기", "대타 요청", "대타 기록", "대타 알림")
        val split = SearchText.split(items, 12) { SearchText.fit("근로계약서 대타", it, emptyList()) }
        assertTrue(split.sure.isEmpty())
        assertEquals(SearchText.NEAR, split.near.size)
        // Something sure: the near ones not far behind it, still apart.
        val mixed = SearchText.split(listOf("근로계약서 대타 찾기", "대타 찾기"), 12) { SearchText.fit("근로계약서 대타", it, emptyList()) }
        assertEquals(listOf("근로계약서 대타 찾기"), mixed.sure)
        assertTrue(mixed.near.all { it !in mixed.sure })
        // Settings: a two-word query of which one word fits says '꼭 맞는 설정은 없어요' and shows the near ones apart.
        val found = SettingsIndex.found("근로계약서 알람음")
        assertTrue(found.sure.isEmpty())
        assertTrue(found.near.size in 1..SearchText.NEAR)
        assertTrue(SettingsIndex.found("알람음").sure.isNotEmpty())
    }
}
