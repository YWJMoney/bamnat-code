package com.bamnat.app.rules

import com.bamnat.app.tier.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.38 [GROW-01][GROW-02][GROW-03][EASE-02] Promises held by a test so a later change cannot quietly break them.
 * - GROW-03: widgets and the basic alarm (ring, snooze, per-shift times) stay free; only 확실한 알람 (missions,
 *   wake check) is 프로.
 * - GROW-02: AdMob is the only ad network; no mediation adapter or other ad SDK in the build.
 * - GROW-01: the promise sentence the user reads still says what the rules do.
 * - EASE-02: the tab the user moved first is the one the app opens on, from a cold start and after time away.
 */
class FreePromiseRulesTest {
    // Gradle runs unit tests in the module directory.
    private val main = File("src/main/java/com/bamnat/app")
    private fun kt(dir: String) = File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun widgetsHaveNoProLock() {
        val gated = kt("widget").filter { f ->
            val t = f.readText()
            t.contains("FeatureGate") || t.contains("featureGate") || Regex("""\bFeature\.[A-Z_]+""").containsMatchIn(t)
        }.map { it.name }
        assertEquals("위젯은 무료예요(프로 잠금 없음)", emptyList<String>(), gated)
        val names = Feature.entries.map { it.name }
        assertTrue("위젯을 파는 프로 기능 줄이 없어요", names.none { it.contains("WIDGET") })
    }

    @Test fun theBasicAlarmIsFree() {
        // The alarm package reads the gate nowhere; what 프로 adds reaches it only as BootAlarms' `sure` flag.
        val uses = kt("alarm").flatMap { f ->
            Regex("""\bFeature\.([A-Z_]+)""").findAll(f.readText().lines().filterNot { it.trimStart().startsWith("/**") || it.trimStart().startsWith("*") }.joinToString("\n"))
                .map { f.name + ":" + it.groupValues[1] }.toList()
        }
        assertEquals("알람 코드는 프로 기능을 직접 묻지 않아요", emptyList<String>(), uses)
        val alarmFeatures = Feature.entries.filter { it.name.contains("ALARM") }.map { it.name }
        assertEquals("알람 쪽 프로 기능은 확실한 알람(미션·일어났는지 확인) 하나", listOf("SURE_ALARM"), alarmFeatures)
        // Without 프로 the ring settings keep sound, vibration, snooze and ring time; only the mission and check go.
        val boot = File(main, "alarm/BootAlarms.kt").readText()
        assertTrue(boot.contains("mission = if (sure)") && boot.contains("wakeCheckMinutes = if (sure)"))
        assertTrue(boot.contains("s.defaultAlarmSound, s.vibrate, s.gradualVolume, s.snoozeMinutes, s.ringMinutes,"))
    }

    @Test fun admobIsTheOnlyAdNetwork() {
        val deps = File("build.gradle.kts").readLines().map { it.trim() }.filter { it.startsWith("implementation(") }
        val ads = deps.filter { d ->
            listOf("ads", "mediation", "applovin", "unity3d", "ironsource", "facebook", "vungle", "inmobi", "mintegral", "pangle", "chartboost", "adcolony", "fyber")
                .any { Regex("""[:.\-]$it\b""", RegexOption.IGNORE_CASE).containsMatchIn(d) }
        }
        assertEquals("광고는 AdMob 하나(중개 어댑터 없음)",
            listOf("implementation(\"com.google.android.gms:play-services-ads:25.2.0\")"), ads.map { it.substringBefore(" //") })
    }

    @Test fun thePromiseStillSaysWhatTheRulesDo() {
        val p = com.bamnat.core.tier.AdRules.PROMISE
        for (w in listOf("고를 때만", "하루 두 편", "소리 없이", "달력·알람·메모·설정에는 광고가 없어요", "프로에는 광고가 없어요")) {
            assertTrue(w, p.contains(w))
        }
        assertTrue(com.bamnat.core.tier.AdRules.MUTED)
    }

    @Test fun theFirstTabIsWhereTheAppOpens() {
        val root = File(main, "ui/AppRoot.kt").readText()
        // Cold start: the NavHost starts on the first saved tab.
        assertTrue(root.contains("val savedTabs = remember(settingsNow.tabOrder) { TopLevel.ordered(settingsNow.tabOrder) }"))
        assertTrue(root.contains("val home = savedTabs.first()"))
        assertTrue(root.contains("val startTab = rememberSaveable { home.route }"))
        assertTrue(root.contains("start = startTab,") && root.contains("startDestination = start,"))
        // Back after time away (or a new day): the first tab as saved now, not the calendar, unless a notice said where.
        assertTrue(root.contains("val homeRoute = TopLevel.ordered(container.settingsRepository.current.tabOrder).first().route"))
        assertTrue(root.contains("if (NoticeLinks.target.value == null && current != homeRoute"))
        // The order a backup carries comes back with it.
        val backup = File(main, "data/BackupManager.kt").readText()
        assertTrue(backup.contains(".put(\"tabOrder\", s.tabOrder.joinToString(\",\"))"))
        assertTrue(backup.contains("tabOrder = o.optStringOrNull(\"tabOrder\")?.let(::decodeGroupOrder) ?: s.tabOrder"))
        // Every kind of work sees every tab, so the first one is always there (none can be hidden).
        for (moved in com.bamnat.core.layout.TabOrder.DEFAULT) {
            val order = com.bamnat.core.layout.TabOrder.move(com.bamnat.core.layout.TabOrder.DEFAULT, moved, 0)
            val saved = com.bamnat.core.layout.TabOrder.toSave(order)
            assertEquals(moved, com.bamnat.core.layout.TabOrder.home(saved))
            assertEquals(moved, com.bamnat.core.layout.TabOrder.home(com.bamnat.app.data.decodeGroupOrder(saved.joinToString(","))))
        }
    }
}
