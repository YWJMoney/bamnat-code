package com.bamnat.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.39.1 [R391-03] 검토 ALM-02: a running Glance session only recomposes on update (about 45초), so a widget that read its
 * data once in provideGlance drew the first of two quick changes. Every widget that reads [WidgetData] reads it again for
 * each stamp ([widgetData]), and the refresher stamps instead of a bare updateAll.
 */
class WidgetStampRulesTest {
    private val dir = File("src/main/java/com/bamnat/app/widget")

    @Test fun everyWidgetOfTheScheduleReadsItsDataAgainForEachStamp() {
        val widgets = dir.listFiles()!!.filter { it.extension == "kt" }.flatMap { f ->
            Regex("""class (\w+) : GlanceAppWidget\(\) \{(.*?)\n}""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())
                .map { it.groupValues[1] to it.groupValues[2] }.toList()
        }
        val readers = widgets.filter { (_, body) -> "WidgetData.load(context)" in body }
        assertTrue("오늘·주·달·모든 조·다음 근무: ${readers.map { it.first }}", readers.size >= 5)
        for ((name, body) in readers) {
            assertTrue("$name reads its data again inside the composition", "widgetData(context, first)" in body)
            assertTrue("$name keeps the stamp in its state", "PreferencesGlanceStateDefinition" in body)
        }
        val refresher = File(dir, "WidgetRefresher.kt").readText()
        assertFalse("the refresher stamps, not a bare updateAll", ".updateAll(" in refresher)
        for ((name, _) in readers) assertTrue("$name is stamped", "stampAndUpdate(app, $name())" in refresher)
    }
}
