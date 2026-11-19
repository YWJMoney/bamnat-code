package com.bamnat.app.screenshots

import org.json.JSONArray
import java.io.File

/**
 * 2.0.0 [RVR-21] (검토 SD-07) 장터 작품 전체: docs/recipes 폴더 .json 파일의 작품 배열(market-*·showcase·starter·theme, 갈래 S의 edge-200)을
 * tools/market_works.py와 같은 순서·같은 이름으로. 이름은 "<파일 이름>#<1부터 센 번호>", 가장자리 작품은 제목 앞에서 "edge:E1".
 * 두 곳이 어긋나면 tools/market_shots.py compare가 실패해요(모두 찍은 사진의 이름을 market_works.py와 대조).
 */
internal object MarketWorks {
    data class Work(val key: String, val title: String, val edge: Boolean, val body: String)

    private val EDGE = Regex("^시험\\s*(E\\d+)(?!\\d)")

    fun all(dir: File = File("../docs/recipes")): List<Work> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }.flatMap { f ->
            val stem = f.name.removeSuffix(".json")
            val text = f.readText()
            if (!text.trimStart().startsWith("[")) return@flatMap emptyList()
            val a = JSONArray(text)
            (0 until a.length()).map { i ->
                val r = a.getJSONObject(i).getJSONObject("recipe")
                val title = r.optString("title")
                val m = EDGE.find(title)
                val key = if (m != null && stem.startsWith("edge")) "edge:${m.groupValues[1]}" else "$stem#${i + 1}"
                Work(key, title, key.startsWith("edge:"), r.toString())
            }
        }
}
