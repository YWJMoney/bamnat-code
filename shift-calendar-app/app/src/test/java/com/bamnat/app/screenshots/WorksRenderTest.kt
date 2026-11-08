package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.recipe.LookImages
import com.bamnat.app.ui.recipe.MarketShots
import com.bamnat.app.ui.recipe.ShotPhone
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * [MKW-02] 1.39 출시 작품 제작용 그려 보기 (CI에서는 건너뜀): `build/works-render.json`(작품 배열, `node docs/recipes/market-139/make/render.mjs`가
 * 씀)이 있을 때만, 작품마다 장터 넘겨 보기 순서(달력 → 가계부 → 메모 → 급여 → 위젯 → 알람)의 여섯 얼굴을 한 장에 그려
 * `build/screenshots/works/<번호>.png`로 남겨요. 밤낮 재료 그림은 `tools/materials/out`에서 폰 저장소로 옮겨 실제처럼 보여요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WorksRenderTest : ScreenshotBase() {
    private val c get() = app.container

    private fun materials() {
        val out = File("../tools/materials/out")
        val list = File(out, "목록.json").takeIf { it.isFile } ?: return
        val items = JSONObject(list.readText()).getJSONArray("items")
        val dir = LookImages.dir(c.appContext).apply { mkdirs() }
        for (i in 0 until items.length()) {
            val m = items.getJSONObject(i)
            val src = File(out, m.getString("file"))
            if (src.isFile) src.copyTo(File(dir, m.getString("id") + ".webp"), overwrite = true)
        }
    }

    @Test fun render() {
        val file = File("build/works-render.json").takeIf { it.isFile } ?: return
        val works = JSONArray(file.readText())
        for (i in 0 until works.length()) {
            val o = works.getJSONObject(i)
            val name = o.optString("name", "w$i")
            val r = RecipeCodec.read(o.getJSONObject("recipe").toString())
            val style = o.optString("style").takeIf { it.isNotEmpty() }?.let { WorkStyle.valueOf(it) } ?: r.styles.firstOrNull()
            val faces: List<Pair<String, MarketShots.Face>> = o.optString("faces", "calendar,money,memos,pay,widget,alarm").split(',').map { f ->
                f to when (f) {
                    "pay" -> MarketShots.Face.Pay
                    "widget" -> MarketShots.Face.Widget
                    "alarm" -> MarketShots.Face.Alarm
                    "rules" -> MarketShots.Face.Rules
                    "money" -> MarketShots.Face.Tab(TabOrder.MONEY)
                    "memos" -> MarketShots.Face.Tab(TabOrder.MEMOS)
                    else -> MarketShots.Face.Tab(TabOrder.CALENDAR)
                }
            }
            val cols = minOf(3, faces.size)
            val rows = (faces.size + cols - 1) / cols
            shoot("works/$name", "w${cols * 350 + 10}dp-h${rows * 570 + 40}dp-land-mdpi") {
                materials()
                content {
                    Column(Modifier.fillMaxSize().background(Color(0xFF2A2A30)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${r.title} · ${style?.name ?: "모두"}", color = Color.White, fontSize = 18.sp)
                        faces.chunked(cols).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                line.forEach { (_, face) ->
                                    Box(Modifier.size(MarketShots.PHONE_W.dp, MarketShots.PHONE_H.dp)) { ShotPhone(face, r, style, 1f) }
                                }
                            }
                        }
                    }
                }
                settle()
                settle()
            }
        }
    }
}
