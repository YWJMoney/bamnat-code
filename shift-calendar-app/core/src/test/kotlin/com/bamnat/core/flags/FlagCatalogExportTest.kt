package com.bamnat.core.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [FLAG-34] 1.28 통합: the company server's flag book is made from [FlagCatalog], so the developer app (names, what it
 * does, why it is off, basis, risk, default, conditions, sunset) and the phone's 진단 screen say the same, and the
 * conditions the server shows are the ones the phone ships with. The file is checked in; this test fails while it is
 * old. To write it again:
 *
 *     BAMNAT_WRITE_FLAG_CATALOG=1 ./gradlew -p core test --tests '*FlagCatalogExportTest*' --rerun
 */
class FlagCatalogExportTest {
    @Test fun theServersCopyOfTheBookIsCurrent() {
        val file = serverFile()
        val want = FlagCatalogExport.render()
        if (System.getenv("BAMNAT_WRITE_FLAG_CATALOG") == "1") file.writeText(want)
        val have = if (file.exists()) file.readText() else ""
        assertEquals("${file.path} is older than core FlagCatalog. Write it again with: " +
            "BAMNAT_WRITE_FLAG_CATALOG=1 ./gradlew -p core test --tests '*FlagCatalogExportTest*' --rerun " +
            "(then `npm test` in server/company-worker)", want, have)
    }

    @Test fun theRenderedBookIsWhatTheServerReads() {
        val js = FlagCatalogExport.render()
        for (f in FlagCatalog.ALL) assertTrue(f.key, js.contains("\"key\":\"${f.key}\""))
        for (name in listOf("CORE_FLAGS", "CORE_FREE", "CORE_POLICY", "CORE_NAMES")) assertTrue(name, js.contains("export const $name = "))
        // The bundler (tools/bundle.mjs) turns `export const` into `const`; nothing else may be exported or imported.
        assertTrue(js.lines().none { it.startsWith("import ") || (it.startsWith("export ") && !it.startsWith("export const ")) })
    }

    private fun serverFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "server/company-worker/src")
            if (f.isDirectory) return File(f, "flag-catalog.js")
            dir = dir.parentFile
        }
        error("server/company-worker/src not found above ${File("").absolutePath}")
    }
}

/** [FLAG-34] The book as an ES module the worker imports (JSON values, one row a line, keys in a fixed order). */
object FlagCatalogExport {
    fun render(): String = buildString {
        appendLine("// [FLAG-34] 만든 파일이에요. 손으로 고치지 마세요.")
        appendLine("// 원본: shift-calendar-app/core/src/main/kotlin/com/bamnat/core/flags/FlagCatalog.kt (+ PolicyCalendar.SEED, FlagEval의 조건 이름)")
        appendLine("// 다시 만들기: BAMNAT_WRITE_FLAG_CATALOG=1 ./gradlew -p core test --tests '*FlagCatalogExportTest*' --rerun")
        appendLine("// 낡으면 core FlagCatalogExportTest가 실패해요. src/index.js가 읽고, tools/bundle.mjs가 worker.js 안에 넣어요.")
        appendLine()
        appendLine("/** Every row of the phone's book, in its order. */")
        appendLine("export const CORE_FLAGS = [")
        FlagCatalog.ALL.forEach { appendLine("  ${json(flag(it))},") }
        appendLine("];")
        appendLine()
        val sample = FlagCatalog.free(FlagCatalog.FREE_PREFIX + "X_FEATURE")!!
        appendLine("/** free.<FEATURE>: made on demand; {feature} is the feature's name. */")
        appendLine("export const CORE_FREE = ${json(linkedMapOf(
            "pattern" to "^free\\.(" + FlagCatalog.FREE_FEATURE.removePrefix("^").removeSuffix("$") + ")$",
            "slot" to FlagCatalog.FREE_SLOT,
            "name" to FlagCatalog.FREE_NAME, "what" to FlagCatalog.FREE_WHAT, "whyOff" to FlagCatalog.FREE_WHY,
        ) + (flag(sample) - listOf("key", "name", "what", "whyOff")))};")
        appendLine()
        appendLine("/** 처리방침 dates the app ships with (PolicyCalendar.SEED); the server's calendar must agree on these versions. */")
        appendLine("export const CORE_POLICY = [")
        PolicyCalendar.SEED.versions.forEach {
            appendLine("  ${json(linkedMapOf("version" to it.version, "posted" to it.posted.toString(), "effective" to it.effective.toString(), "heldUntil" to it.heldUntil?.toString()))},")
        }
        appendLine("];")
        appendLine()
        appendLine("/** How the phone names what a condition waits on (FlagEval); the developer app's evaluator must use the same. */")
        appendLine("export const CORE_NAMES = ${json(linkedMapOf("consent" to FlagEval.CONSENTS, "profile" to FlagEval.PROFILE, "manual" to FlagEval.MANUAL, "work" to FlagEval.TYPE_WORDS))};")
    }

    private fun flag(f: Flag): Map<String, Any?> = linkedMapOf(
        "key" to f.key, "group" to f.group.key, "name" to f.name, "what" to f.what, "whyOff" to f.whyOff,
        "basis" to linkedMapOf("policy" to f.basis.policy, "decision" to f.basis.decision, "req" to f.basis.requirement, "note" to f.basis.note).filterValues { it != null },
        "risk" to f.risk.name, "default" to f.default, "conditions" to f.conditions.map(::condition),
        "sunset" to f.sunset, "owner" to f.owner, "since" to f.since, "minApp" to f.minApp, "maxApp" to f.maxApp,
    )

    /** The condition JSON of AL-D1, as the server's checkConditions writes it back (unknownMeets only when true). */
    fun condition(c: Condition): Map<String, Any?> = when (c) {
        is Condition.Date -> if (c.policy != null) linkedMapOf("type" to "date", "policy" to c.policy) else linkedMapOf("type" to "date", "from" to c.from.toString())
        is Condition.Policy -> linkedMapOf("type" to "policy", "version" to c.version)
        is Condition.Consent -> linkedMapOf<String, Any?>("type" to "consent", "key" to c.key).apply { if (c.unknownMeets) put("unknownMeets", true) }
        is Condition.Profile -> linkedMapOf("type" to "profile", "key" to c.key)
        is Condition.App -> linkedMapOf("type" to "app", "min" to c.min, "max" to c.max)
        is Condition.WorkType -> linkedMapOf("type" to "workType", "in" to c.types.sorted())
        is Condition.Rollout -> linkedMapOf("type" to "rollout", "percent" to c.percent)
        is Condition.Manual -> linkedMapOf("type" to "manual", "key" to c.key)
        Condition.Admin -> linkedMapOf("type" to "admin")
        is Condition.AnyOf -> linkedMapOf("type" to "any", "of" to c.any.map(::condition))
    }

    fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (ch in v) when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch < ' ' || ch == ' ' || ch == ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
            append('"')
        }
        is Boolean, is Int, is Long -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> json(k.toString()) + ":" + json(x) }
        is Collection<*> -> v.joinToString(",", "[", "]") { json(it) }
        else -> error("cannot write ${v::class}")
    }
}
