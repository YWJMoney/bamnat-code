package com.bamnat.app.ui.company

import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVN-21] (갈래 I가 찾음): a plaza or company screen that says where in settings a thing is names the page as it is on screen.
 * The way is read from 기능 찾기's list, and no line of these screens writes a page by hand that is not there.
 */
class SettingsWayTest {
    @Test fun theNewsWayNamesThePageOfNoticesAndSleep() {
        (WorkStyle.entries + listOf<WorkStyle?>(null)).forEach { s ->
            assertEquals("설정 › 알림과 잠 › 밤낮 소식", settingsWay("plaza.news", s))
        }
    }

    @Test fun thePayPageIsNamedForTheKindOfWork() {
        assertEquals("설정 › 급여와 연차 › 광장 시세와 내 단가 견주기", settingsWay("pay.rateHint", WorkStyle.SHIFT))
        assertEquals("설정 › 시급과 주휴수당 › 광장 시세와 내 단가 견주기", settingsWay("pay.rateHint", WorkStyle.HOURLY))
        assertEquals("설정 › ${SettingsPage.PAY.titleFor(WorkStyle.FIELD)} › 광장 시세와 내 단가 견주기", settingsWay("pay.rateHint", WorkStyle.FIELD))
        assertEquals("설정 › 개인정보·앱 정보 › 회사 광장에 저절로 나누기", settingsWay("plaza.autoShare", WorkStyle.DAY))
        assertEquals("설정 › 급여와 연차 › 회사 찾기", companyFindWay(WorkStyle.SHIFT))
    }

    @Test fun noLineOfThePlazaOrCompanyNamesASettingsPageThatIsNotThere() {
        val pages = (WorkStyle.entries + listOf<WorkStyle?>(null)).flatMap { s -> SettingsPage.entries.map { it.titleFor(s) } }.toSet() +
            "도움말과 문의"
        val way = Regex("설정 ([›>]) ([^›\"]+)")
        val wrong = listOf("ui/company", "company", "ui/village").flatMap { d ->
            File("src/main/java/com/bamnat/app/$d").walk().filter { it.extension == "kt" }.flatMap { f ->
                f.readLines().withIndex().filter { (_, l) -> l.trimStart().let { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") } }
                    .flatMap { (i, l) ->
                        Regex("\"([^\"]*)\"").findAll(l).flatMap { s -> way.findAll(s.groupValues[1]) }
                            // A page put in from a value (\${…}) is the helpers' own, checked above.
                            .filter { m -> !m.groupValues[2].startsWith("$") }
                            .filter { m -> m.groupValues[1] != "›" || pages.none { m.groupValues[2].trim().startsWith(it) } }
                            .map { m -> "${f.name}:${i + 1} ${m.value}" }
                    }
            }.toList()
        }
        assertTrue("설정 쪽 이름이 실제와 달라요: $wrong", wrong.isEmpty())
    }
}
