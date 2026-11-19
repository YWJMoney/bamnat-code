package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.components.OfflineLine
import com.bamnat.app.ui.components.SkeletonRows
import com.bamnat.app.ui.components.Tab
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 묶음 2 (검토 I13a-02·UIX-06, 조건 7·12·14): 공통 부품 세 가지를 한 장에. 탭 줄(고른 탭만 파랑, 나머지는 회색), 인터넷 없음 한 줄(글자 200%에서
 * '다시 하기'가 글 아래로), 기다리는 뼈대 줄('불러오는 중' 한 번).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP7CommonPartsShotsTest : ScreenshotBase() {
    private fun parts(): Screen.() -> Unit = {
        content {
            Column(Modifier.padding(16.dp)) {
                androidx.compose.material3.TabRow(selectedTabIndex = 1) {
                    listOf("장터", "공방", "내 글").forEachIndexed { i, t -> Tab(selected = i == 1, onClick = {}, text = { Text(t) }) }
                }
                OfflineLine(Modifier.padding(top = 16.dp), onRetry = {})
                SkeletonRows(modifier = Modifier.padding(top = 16.dp))
            }
        }
        settle()
        assertTrue("인터넷 없음 한 줄과 다시 하기", appears("다시 하기", exact = true))
        assertTrue("고르지 않은 탭도 읽혀요", shows("공방", exact = true))
        assertTrue("뼈대 줄은 '불러오는 중'으로 한 번 읽혀요", nodes().count { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("불러오는 중") == true } == 1)
    }

    @Test fun partsLight() = shoot("rvp7_parts_folded_light", FOLDED, parts())
    @Test fun partsDark200() = shoot("rvp7_parts_folded_dark_200", ReviewP7TeamStepsTest.DARK, 2f, parts())
}
