package com.bamnat.app.rules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.NoticeLinks
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.DraftStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2.0.0 갈래 P 묶음 2: 검토 줄 가운데 시험이 없던 것을 지키는 시험 (UIX-51 돈 줄임 억 단위, UIX-48 알림이 실어 온 값, UIX-45 적던 글 7일,
 * UIX-06 탭은 앱의 것 하나).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewP7EvidenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After fun off() {
        Fmt.masked = false
        NoticeLinks.consume(NoticeLinks.Target.DAY)
        NoticeLinks.takeHanded(NoticeLinks.Target.DAY)
    }

    /** UIX-51 [RVP-28]: 억 단위도 만 단위처럼 내림, 199,999,999는 2억이 아니라 1.9억. */
    @Test fun billionsAreCutDownNotUp() {
        assertEquals(listOf("1.9억", "1억", "1.2억", "12.3억", "-1.9억"),
            listOf(199_999_999L, 100_000_000, 123_456_789, 1_234_999_999, -199_999_999).map(Fmt::wonShort))
    }

    /** UIX-48 [RVP-26]: 한 번 읽고 나면 실어 온 값이 남지 않아요(다음 화면이 옛 날짜에서 시작하던 것). */
    @Test fun aNoticesValuesGoWithItWhenItIsDone() {
        NoticeLinks.open(NoticeLinks.Target.DAY, calendar = "c1", payload = "2026-05-01")
        assertEquals("2026-05-01", NoticeLinks.payload.value)
        NoticeLinks.consume(NoticeLinks.Target.TODAY)
        assertEquals("다른 길을 끝내도 이 길의 값은 그대로", "2026-05-01", NoticeLinks.payload.value)
        NoticeLinks.consume(NoticeLinks.Target.DAY)
        assertNull(NoticeLinks.target.value)
        assertNull(NoticeLinks.payload.value)
        assertNull(NoticeLinks.calendar.value)
    }

    @Test fun aHandedValueIsTakenOnce() {
        NoticeLinks.hand(NoticeLinks.Target.DAY, "2026-05-02")
        assertEquals("2026-05-02", NoticeLinks.takeHanded(NoticeLinks.Target.DAY))
        assertNull(NoticeLinks.takeHanded(NoticeLinks.Target.DAY))
    }

    /** UIX-45 [RVP-24]: 적던 글은 이 폰에 7일, 그 뒤엔 저절로 지워지고, 빈 글을 쓰면 지워져요. */
    @Test fun aDraftIsKeptSevenDaysThenGoes() {
        val day = 86_400_000L
        DraftStore.write(context, "memo:1", "야간 끝나고 병원", now = 1_000L)
        assertEquals("야간 끝나고 병원", DraftStore.read(context, "memo:1", now = 1_000L + 6 * day))
        assertEquals("", DraftStore.read(context, "memo:1", now = 1_000L + 8 * day))
        DraftStore.write(context, "memo:2", "적는 중", now = 1_000L)
        DraftStore.write(context, "memo:2", "  ", now = 2_000L)
        assertEquals("", DraftStore.read(context, "memo:2", now = 3_000L))
    }

    /** UIX-06 [RVP-02]: 탭은 앱의 `Tab`(고르지 않은 탭은 회색) 하나, Material 것을 직접 부르는 곳은 AppTabs.kt뿐이에요. */
    @Test fun onlyAppTabsCallsMaterialsTab() {
        val root = File("src/main/java/com/bamnat/app")
        val direct = Regex("""import androidx\.compose\.material3\.Tab$|material3\.Tab\(""", RegexOption.MULTILINE)
        val bad = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "AppTabs.kt" && direct.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("고르지 않은 탭을 회색으로 하려면 com.bamnat.app.ui.components.Tab을 써요: $bad", bad.isEmpty())
    }
}
