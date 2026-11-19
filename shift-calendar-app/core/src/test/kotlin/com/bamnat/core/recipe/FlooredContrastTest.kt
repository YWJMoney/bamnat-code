package com.bamnat.core.recipe

import com.bamnat.core.layout.Contrast
import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-01] 조합 시험기 ②의 남은 단언(검토 SD-06): 본문 대비 4.5:1. Every 장터 work the server holds (1,034) and the 가장자리 시험
 * 작품 E1~E12, as a light and as a dark screen draw them ([Blueprint.forDark] then [Blueprint.floored]): a part that paints its own
 * background keeps its letters at 4.5:1 (3:1 for a cell's small lines and big letters) on that background. Where nothing under the
 * letters is known (a photo, a 칸 꾸미기 piece, only the screen) [PartFloor] does not guess, so the check does not either.
 */
class FlooredContrastTest {
    private val files = listOf("market-136", "market-138", "market-138-refresh", "market-138-studio", "market-139", "edge-200")
    private val light = 0xFFFFFBFEL
    private val dark = 0xFF121212L

    @Suppress("UNCHECKED_CAST")
    private fun partsOf(file: String): List<Pair<String, List<Map<String, Any?>>>> {
        val f = File("../docs/recipes/$file.json")
        if (!f.exists()) return emptyList()
        return (Json.parse(f.readText()) as List<Map<String, Any?>>).mapNotNull { w ->
            val r = w["recipe"] as Map<String, Any?>
            val parts = (r["pieces"] as List<Map<String, Any?>>).firstOrNull { it["kind"] == "PARTS" || it["kind"] == "parts" }
            (parts?.get("parts") as? List<Map<String, Any?>>)?.let { (r["title"] as? String ?: "?") to it }
        }
    }

    @Test fun lettersReadOnTheirOwnBackgroundWhereverTheyAreFloored() {
        val bad = ArrayList<String>()
        var checked = 0
        var works = 0
        for (file in files) for ((title, lines) in partsOf(file)) {
            works++
            val plain = Blueprint(lines)
            for ((name, screen) in listOf("밝은" to light, "어두운" to dark)) {
                val drawn = plain.forDark(screen == dark).floored(screen)
                for (id in lines.mapNotNull { it["id"] as? String }.distinct()) {
                    val s = drawn.style(id)
                    val ink = s.ink ?: continue
                    // Only a part whose own fill is strong paints what its letters stand on; the rest stands on something not known here.
                    if (PartFloor.fillStrength(s) < 0.95 || s.paint != null) continue
                    val fill = (s.fill ?: continue) or 0xFF000000L
                    val size = s.size ?: 14.0
                    val big = size >= 24 || (size >= 18.5 && (s.weight ?: 400) >= 700)
                    val min = if (PartFloor.small(id) || big) PartFloor.SMALL else PartFloor.BODY
                    checked++
                    val ratio = Contrast.ratio(ink or 0xFF000000L, fill)
                    if (ratio + 0.01 < min) bad += "$file '$title' $name $id: ${"%.2f".format(ratio)}:1 < $min"
                }
            }
        }
        assertTrue("작품 ${works}개를 읽었어요", works >= 800)
        assertTrue("검사한 글자 ${checked}개는 너무 적어요", checked >= 200)
        assertTrue("바닥선이 못 지킨 글자 ${bad.size}곳:\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
    }
}
