package com.bamnat.core.model

import com.bamnat.core.recipe.RecipeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 2.0.0 [RVD-28] SD-08 (스튜디오 제작 오류 조사 뿌리 R6, 설계 S7-1): the servers' 근무 종류 규칙 files are made from [ShiftRules], as the flag
 * book is (FlagCatalogExportTest), so the share server, the company server and the 장터 take what the app sends. The files are checked
 * in; this test fails while they are old. To write them again:
 *
 *     BAMNAT_WRITE_SHIFT_RULES=1 ./gradlew -p core test --tests '*ShiftRulesExportTest*' --rerun
 *
 * then `npm test` in server/company-worker and server/share-worker.
 */
class ShiftRulesExportTest {
    @Test fun theServersCopiesOfTheRulesAreCurrent() {
        val want = ShiftRulesExport.render()
        for (file in serverFiles()) {
            if (System.getenv("BAMNAT_WRITE_SHIFT_RULES") == "1") file.writeText(want)
            val have = if (file.exists()) file.readText() else ""
            assertEquals("${file.path} is older than core ShiftRules. Write it again with: " +
                "BAMNAT_WRITE_SHIFT_RULES=1 ./gradlew -p core test --tests '*ShiftRulesExportTest*' --rerun " +
                "(then `npm test` in server/company-worker and server/share-worker)", want, have)
        }
    }

    @Test fun theRenderedRulesAreWhatTheServersRead() {
        val js = ShiftRulesExport.render()
        for (name in listOf("SHIFT_RULES", "SHIFT_RULE_SAMPLES")) assertTrue(name, js.contains("export const $name = "))
        // Both bundlers (tools/bundle.mjs) turn `export const` into `const`; nothing else may be exported or imported.
        assertTrue(js.lines().none { it.startsWith("import ") || (it.startsWith("export ") && !it.startsWith("export const ")) })
        // Only ASCII: what the servers read is what this file says, whatever an editor does to invisible joiners.
        assertTrue(js.lines().filterNot { it.startsWith("//") || it.startsWith("/**") }.all { l -> l.all { it.code in 0x20..0x7E } })
    }

    @Test fun lettersAreWhatGraphemesCounts() {
        val parts = listOf("가", "a", "1", "#", "́", "⃣", "️", "‍", "👩", "⚕", "🏿",
            "🇰", "🇷", "🏴", "󠁧", "󠁿", "☀", "🌙", " ")
        val r = Random(28)
        repeat(20_000) {
            val s = buildString { repeat(r.nextInt(0, 9)) { append(parts[r.nextInt(parts.size)]) } }
            val l = ShiftRules.letters(s)
            assertEquals(s, RecipeText.graphemes(s), l.size)
            if (l.isNotEmpty()) assertEquals(s, s, l.joinToString(""))
        }
        for (s in SAMPLES) assertEquals(s, RecipeText.graphemes(s), ShiftRules.letters(s).size)
    }

    @Test fun aNameOrALabelIsCutAtALetter() {
        assertEquals("주간", ShiftRules.fitLabel("주간"))
        assertEquals("주간", ShiftRules.fitLabel("주간근무"))
        assertEquals("👩‍⚕️👍🏿", ShiftRules.fitLabel("👩‍⚕️👍🏿🌙"))
        assertEquals("🇰🇷🇯🇵", ShiftRules.fitLabel("🇰🇷🇯🇵🌙"))
        // Two kisses are two letters but 30 code units: the first only.
        assertEquals("🧑🏻‍❤️‍💋‍🧑🏼", ShiftRules.fitLabel("🧑🏻‍❤️‍💋‍🧑🏼🧑🏻‍❤️‍💋‍🧑🏼"))
        assertTrue(ShiftRules.labelFits("👩‍⚕️👍🏿"))
        assertFalse(ShiftRules.labelFits("👨‍👩‍👧‍👦👨‍👩‍👧‍👦"))
        assertEquals("가".repeat(20), ShiftRules.fitName("가".repeat(25)))
        assertEquals("👩‍⚕️".repeat(16), ShiftRules.fitName("👩‍⚕️".repeat(17)))
        assertFalse(ShiftRules.nameFits(" "))
        assertEquals(listOf("name", "label", "rest"),
            ShiftRules.problems(ShiftType(1, "가".repeat(21), "주간근무", 0xFF000000, null, null, breakMinutes = 721)))
        assertEquals(emptyList<String>(), ShiftRules.problems(ShiftType(1, "주간", "👩‍⚕️", 0xFF1E88E5, null, null, breakMinutes = 60)))
    }

    @Test fun everyDrawnIconAndTheNumbersTheAppUses() {
        for (k in ShiftRules.ICON_KEYS) assertTrue(k, Regex(ShiftRules.ICON_KEY).matches(k))
        assertTrue(ShiftRules.ICON_EMOJI.startsWith("^" + ShiftIcon.EMOJI))
        assertEquals(30, ShiftRules.GAP_MIN)
        assertEquals(30, ShiftRules.PART_MIN)
        // A 작품's 근무 꾸미기 is held to the same letters (PieceCatalog SHIFT_LOOK).
        val look = com.bamnat.core.recipe.PieceKind.SHIFT_LOOK.fields.single() as com.bamnat.core.recipe.Field.Items
        val texts = look.fields.filterIsInstance<com.bamnat.core.recipe.Field.Text>().associateBy { it.key }
        assertEquals(ShiftRules.NAME_LETTERS, texts.getValue("name").max)
        assertEquals(ShiftRules.LABEL_LETTERS, texts.getValue("label").max)
    }

    private fun serverFiles(): List<File> {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val server = File(dir, "server")
            if (File(server, "company-worker/src").isDirectory && File(server, "share-worker/src").isDirectory) {
                return listOf(File(server, "company-worker/src/shift-rules.js"), File(server, "share-worker/src/shift-rules.js"))
            }
            dir = dir.parentFile
        }
        error("server/company-worker/src and server/share-worker/src not found above ${File("").absolutePath}")
    }

    companion object {
        /** Names and 근무 글자 the servers' letters() must split and cut as core does (exported as SHIFT_RULE_SAMPLES). */
        val SAMPLES = listOf(
            "주", "주간", "DAY1", "주간근무", "가나다라마바사아", "가나다라마바사아자", "👩‍⚕️", "👩‍⚕️👍🏿", "👩‍⚕️👍🏿🌙", "🇰🇷🌙", "🇰🇷🇯🇵🌙",
            "1️⃣2️⃣", "1️⃣2️⃣3️⃣", "#️⃣", "🧑🏻‍❤️‍💋‍🧑🏼", "🧑🏻‍❤️‍💋‍🧑🏼🧑🏻‍❤️‍💋‍🧑🏼", "🏳️‍⚧️", "🏴󠁧󠁢󠁳󠁣󠁴󠁿",
            "👨‍👩‍👧‍👦👨‍👩‍👧‍👦", "éa", "éa", "́가", "☀", "❤️🌙", "야간 근무 A조",
            "가".repeat(20), "가".repeat(21), "가".repeat(40), "가".repeat(41), "A".repeat(20), "A".repeat(21),
            "👩‍⚕️".repeat(9), "👩‍⚕️".repeat(16), "👩‍⚕️".repeat(17), "🧑🏻‍❤️‍💋‍🧑🏼".repeat(6),
        )
    }
}

/** [RVD-28] The rules as an ES module both servers import (JSON values, ASCII only, keys in a fixed order). */
object ShiftRulesExport {
    fun render(): String = buildString {
        appendLine("// [RVD-28] 만든 파일이에요. 손으로 고치지 마세요.")
        appendLine("// 원본: shift-calendar-app/core/src/main/kotlin/com/bamnat/core/model/ShiftRules.kt (근무 종류 한 규칙표, SD-08)")
        appendLine("// 다시 만들기: BAMNAT_WRITE_SHIFT_RULES=1 ./gradlew -p core test --tests '*ShiftRulesExportTest*' --rerun")
        appendLine("// 낡으면 core ShiftRulesExportTest가 실패해요. 회사 서버(company-setup.js, 장터 recipes.js)와 공유 서버(index.js)가 같은 파일을 읽어요.")
        appendLine()
        appendLine("/** What a kind of day may carry (core ShiftRules); each server takes all of it. before: what it took whole before 2.0.0. */")
        appendLine("export const SHIFT_RULES = ${json(rules())};")
        appendLine()
        appendLine("/** Names and 근무 글자 as core splits and cuts them; the servers' tests hold their letters() to these. */")
        appendLine("export const SHIFT_RULE_SAMPLES = [")
        ShiftRulesExportTest.SAMPLES.forEach { s ->
            appendLine("  ${json(linkedMapOf("s" to s, "letters" to ShiftRules.letters(s), "name" to ShiftRules.fitName(s), "nameFits" to ShiftRules.nameFits(s),
                "label" to ShiftRules.fitLabel(s), "labelFits" to ShiftRules.labelFits(s)))},")
        }
        appendLine("];")
    }

    private fun rules(): Map<String, Any?> = linkedMapOf(
        "types" to ShiftRules.TYPES,
        "name" to linkedMapOf("letters" to ShiftRules.NAME_LETTERS, "units" to ShiftRules.NAME_UNITS, "before" to ShiftRules.NAME_BEFORE),
        "label" to linkedMapOf("letters" to ShiftRules.LABEL_LETTERS, "units" to ShiftRules.LABEL_UNITS, "before" to ShiftRules.LABEL_BEFORE),
        "color" to linkedMapOf("max" to ShiftRules.COLOR_MAX, "default" to ShiftRules.DEFAULT_COLOR),
        "icon" to linkedMapOf("keys" to ShiftRules.ICON_KEYS, "key" to ShiftRules.ICON_KEY, "prefix" to ShiftIcon.EMOJI, "emoji" to ShiftRules.ICON_EMOJI,
            "emojiCodePoints" to ShiftRules.EMOJI_CODE_POINTS),
        "rest" to linkedMapOf("max" to ShiftRules.REST_MAX),
        "gap" to linkedMapOf("min" to ShiftRules.GAP_MIN, "part" to ShiftRules.PART_MIN),
    )

    fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (ch in v) when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch.code in 0x20..0x7E -> append(ch)
                else -> append("\\u%04x".format(ch.code))
            }
            append('"')
        }
        is Boolean, is Int, is Long -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> json(k.toString()) + ":" + json(x) }
        is Collection<*> -> v.joinToString(",", "[", "]") { json(it) }
        else -> error("cannot write ${v::class}")
    }
}
