package com.bamnat.core.recipe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-04] 판 호환 자동표 (검토 SD-03·SN-07·I09ba-04): the server's table of which app first reads each name
 * (`server/company-worker/src/parts-since.js`) is made from [RecipeVocab]: the names each released app knew
 * (`docs/recipes/vocab/<versionCode>.json`, taken from that release's own core by `tools/vocab_snapshot.py`) and this app's. It fails while the
 * file is old. To write it again:
 *
 *     BAMNAT_WRITE_PARTS_SINCE=1 ./gradlew -p core test --tests '*PartsSinceExportTest*' --rerun
 *
 * then `npm test` in server/company-worker. On the day a version ships, keep its names (BAMNAT_WRITE_VOCAB=1, same command), so the
 * next version's new names are told apart from them.
 */
class PartsSinceExportTest {
    private val vocabDir = File("../docs/recipes/vocab")

    private val currentCode: Int = File("../gradle.properties").readLines()
        .first { it.startsWith("bamnat.versionCode=") }.substringAfter('=').trim().toInt()

    private fun kept(): Map<Int, Map<String, Set<String>>> =
        (vocabDir.listFiles { f -> f.name.matches(Regex("\\d+\\.json")) } ?: emptyArray())
            .map { RecipeVocab.decode(it.readText()) }.filter { it.first < currentCode }.toMap()

    private fun serverFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "server/company-worker/src")
            if (f.isDirectory) return File(f, "parts-since.js")
            dir = dir.parentFile
        }
        error("server/company-worker/src not found above ${File("").absolutePath}")
    }

    @Test fun theServersTableIsCurrent() {
        if (System.getenv("BAMNAT_WRITE_VOCAB") == "1") File(vocabDir, "$currentCode.json").writeText(RecipeVocab.encode(currentCode, RecipeVocab.now()))
        val file = serverFile()
        val want = render(RecipeVocab.since(kept(), currentCode, RecipeVocab.now()))
        if (System.getenv("BAMNAT_WRITE_PARTS_SINCE") == "1") file.writeText(want)
        assertEquals(
            "${file.path} is older than core. Write it again with: BAMNAT_WRITE_PARTS_SINCE=1 ./gradlew -p core test " +
                "--tests '*PartsSinceExportTest*' --rerun (then `npm test` in server/company-worker)",
            want, if (file.exists()) file.readText() else "",
        )
    }

    /** Every released app from 1.36.0 (the first that read 부품) to the last is kept, each as the release's own core read it. */
    @Test fun everyReleasedAppIsKept() {
        val have = kept().keys
        for (v in 49 until currentCode) assertTrue("docs/recipes/vocab/$v.json (tools/vocab_snapshot.py)", v in have)
        // 1.39's own names (night.*, 근무 입력 상태) are in its file and not in 1.37's: the files are each release's own.
        val k = kept()
        assertTrue("night.clock" in k.getValue(53).getValue("ids") && "night.clock" !in k.getValue(51).getValue("ids"))
        assertTrue("memo.page" in k.getValue(52).getValue("ids") && "memo.page" !in k.getValue(51).getValue("ids"))
    }

    /**
     * A name an older app knew is still known here: otherwise a work made there (and kept on someone's phone or on the 장터) would be
     * refused by this app. A name is never taken away; a part that goes stays readable and is drawn as nothing.
     */
    @Test fun nothingAnOlderAppKnewIsForgotten() {
        assertEquals(emptyList<String>(), RecipeVocab.dropped(kept(), RecipeVocab.now()))
    }

    /** The table still says what the server's tests hold it to (RVD-24): 1.38's memo.page*, 1.39's night.* and 근무 입력 상태. */
    @Test fun theTableHasWhatTheHandTableHad() {
        val s = RecipeVocab.since(kept(), currentCode, RecipeVocab.now())
        for (id in listOf("memo.page", "memo.page.rule", "memo.photo")) assertEquals(id, 52, s.getValue("ids")[id])
        for (id in listOf("night", "night.date", "night.clock", "night.line", "night.card", "night.button", "night.morning", "widget.task")) {
            assertEquals(id, 53, s.getValue("ids")[id])
        }
        for (st in listOf("late", "done", "leave", "extra", "cover", "edited")) assertEquals(st, 53, s.getValue("states")[st])
        for (d in listOf("leave", "extra", "cover", "edited")) assertEquals(d, 53, s.getValue("decor")[d])
        assertEquals(null, s.getValue("ids")["cal.cell"])
        // Every 꾸밈 날 a part state names is tried (RecipeVocab.DECOR_TRIES), so a new one is not missed.
        for (st in BlueprintParts.STATES) if (Decor.onProblem(st) == null) assertTrue(st, st in RecipeVocab.DECOR_TRIES)
    }

    /** One module the worker imports: `export const` only (tools/bundle.mjs), each category one map, a version's names on a line. */
    @Test fun theRenderedTableIsWhatTheServerReads() {
        val js = render(RecipeVocab.since(kept(), currentCode, RecipeVocab.now()))
        assertTrue(js.lines().none { it.startsWith("import ") || (it.startsWith("export ") && !it.startsWith("export const ")) })
        for (k in RecipeVocab.KINDS) assertTrue(k, js.contains("\n  $k: {"))
    }

    private fun render(since: Map<String, Map<String, Int>>): String = buildString {
        appendLine("// 2.0.0 [RVS-04] 만든 파일이에요. 손으로 고치지 마세요.")
        appendLine("// 원본: core RecipeVocab(지금 앱이 아는 이름) + docs/recipes/vocab/<versionCode>.json(출시판마다 그 판 core가 알던 이름, tools/vocab_snapshot.py).")
        appendLine("// 다시 만들기: BAMNAT_WRITE_PARTS_SINCE=1 ./gradlew -p core test --tests '*PartsSinceExportTest*' --rerun (낡으면 그 시험이 실패해요)")
        appendLine("//")
        appendLine("// [RVD-24] SN-07 (스튜디오 제작 오류 조사, 설계 S3): 판 호환표. Every name an older app's RecipeCodec does not know makes it refuse the")
        appendLine("// whole recipe, so a maker's recipe that uses one is listed only to apps at least that new (recipes.js partsMinApp → min_app).")
        appendLine("// Each map: name → the first versionCode that reads it (50 = 1.36.1, 51 = 1.37.0, 52 = 1.38.0, 53 = 1.39.0, 54 = 2.0.0). A name not listed")
        appendLine("// is read by every app from 1.36.0 (49).")
        appendLine("//   ids     a 부품 줄's id                                   (parts.parts[].id)")
        appendLine("//   states  a 부품 줄's state                                (parts.parts[].on)")
        appendLine("//   decor   a 꾸밈 항목's day                                (decor.items[].on, an item with at)")
        appendLine("//   fonts   a font id                                        (any font field, and the ff:·ft: pieces of looks·screens)")
        appendLine("//   motions one motion piece, as written                     (motion: \"tn:slide|td:pulse\" → \"tn:slide\", \"td:pulse\"; a part's motion)")
        appendLine("//   fills   a fill's kind: pt.<무늬>·tx.<질감>·sh.<셰이더>, else its first word (so·lg·rg·sg·gl)  (paint·linePaint, fl: pieces)")
        appendLine("//   pieces  a piece's kind; an older app leaves out a kind it does not know (the rest of the work still opens)")
        appendLine("//   fields  <kind>.<field>[.<field of an item>]: an older app drops a field it does not know, and then the work fails its hash")
        appendLine("//   picks   <kind>.<field>…=<choice>: an older app refuses the whole work on a choice it does not know")
        appendLine("export const PARTS_SINCE = {")
        for (k in RecipeVocab.KINDS) {
            val m = since[k].orEmpty()
            if (m.isEmpty()) { appendLine("  $k: {},"); continue }
            appendLine("  $k: {")
            m.entries.groupBy { it.value }.toSortedMap().forEach { (_, e) ->
                e.chunked(6).forEach { line -> appendLine("    " + line.joinToString(" ") { "${RecipeCodec.canonical(it.key)}: ${it.value}," }) }
            }
            appendLine("  },")
        }
        appendLine("};")
    }
}
