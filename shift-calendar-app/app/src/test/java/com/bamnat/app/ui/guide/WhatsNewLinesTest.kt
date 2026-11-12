package com.bamnat.app.ui.guide

import com.bamnat.app.ui.settings.FORGET_ALL_ROW
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.guide.WhatsNew
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RVH-40] 검토 WORK-14 새로 생긴 것의 줄이 화면에 있는 이름 그대로인지: 이름을 바꾸면 이 시험이 잡아요(1.39.0 줄이 '설정 › 개인정보의 모두
 * 지우기'라고 했는데 실제 줄은 '개인정보·앱 정보 › 이 휴대폰이 보낸 정보 모두 지우기'였어요).
 */
class WhatsNewLinesTest {
    @Test fun theForgetAllLineNamesTheRealRow() {
        val line = WhatsNew.releases.first { it.versionName == "1.39.0" }.lines.first { "공유 링크까지" in it.text }.text
        assertTrue(line, FORGET_ALL_ROW in line)
        assertTrue(line, SettingsPage.PRIVACY.title in line)
    }
}
