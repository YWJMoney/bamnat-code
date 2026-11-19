package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.recipe.RecipeCodec
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [PC-62][PC-33] 장터 작품을 입은 앱 (PC 통합 1차): 딥워크 데스크의 검은 탭 막대에서 고른 탭 이름이 보이는지, 야간 회복
 * 스테이션의 달력 칸(점 표시, 야간 칸의 시간, 쉬는 날 바탕)이 어떻게 보이는지. 작품은 저장소의 1.39 장터 글 그대로예요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvmWorkLookScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun wearWork(title: String) = runBlocking {
        val works = JSONArray(File("../docs/recipes/market-139.json").readText())
        val w = (0 until works.length()).map { works.getJSONObject(it) }.first { it.getJSONObject("recipe").getString("title") == title }
        c.wardrobe.wear(RecipeCodec.read(w.getJSONObject("recipe").toString()), "TESTWORK", own = false)
    }

    @Test fun deepWorkTabs() = shoot("rvm_pc62_deepwork_tabs_folded", FOLDED) { wearWork("딥워크 데스크") }
    @Test fun nightRecoveryCells() = shoot("rvm_pc33_night_recovery_folded", FOLDED) { wearWork("야간 회복 스테이션") }
}
