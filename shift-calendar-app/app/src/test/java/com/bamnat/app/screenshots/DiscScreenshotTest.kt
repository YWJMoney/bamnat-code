package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.DiscoverCardBody
import com.bamnat.core.guide.DiscoverCatalog
import com.bamnat.core.guide.FeatureRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.40 [DISC-01] 안 써 본 기능 알려 주기 카드 (사용자 2026-11-11 '위트 있고 디자인이 뛰어나야'): 교대·가게·투자 카드 세 장을 폰, 밤 색, 폴드
 * 접힘에 큰 글자로 그려요. 단추가 잘리지 않고, 제목이 머리말이고, 색은 테마에서만 와요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DiscScreenshotTest : ScreenshotBase() {
    private val cards = listOf("alarm.check", "shop.cardDue", "invest.journal")

    private fun Screen.three() {
        content {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.forEach { id ->
                        DiscoverCardBody(FeatureRegistry.get(id)!!, DiscoverCatalog.of(id)!!, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        settle()
    }

    @Test fun threeKindsOfWork() = shoot("1560_disc01_cards", PHONE) {
        three()
        assertTrue(appears("알람, 진짜 울릴까 불안한 밤엔", exact = true))
        assertTrue(appears("이런 기능도 있어요"))
        assertTrue(appears("달력에서 보기", exact = true))
        assertTrue(appears("나중에", exact = true))
    }

    @Test fun atNight() = shoot("1561_disc01_night", "w360dp-h780dp-port-night-450dpi") { three() }

    @Test fun foldedWithBigText() = shoot("1562_disc01_folded_big", FOLDED, 1.5f) {
        three()
        assertTrue(appears("그때 왜 샀더라", exact = true))
        assertTrue(appears("적어 보기", exact = true))
    }
}
