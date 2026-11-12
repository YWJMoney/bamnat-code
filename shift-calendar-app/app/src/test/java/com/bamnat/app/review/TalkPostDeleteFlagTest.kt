package com.bamnat.app.review

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.recipe.RecipePost
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.recipe.TALK_FLAG_REASONS
import com.bamnat.app.ui.recipe.talkTops
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * [RVF-03] 개발 전 검토 MKT-08: 작품 '이야기'에 올린 내 글은 지울 수 없고, 남의 글은 신고할 수 없었어요. 서버의 글 지우기·신고 길
 * (`DELETE /api/posts/:id`, `POST /api/posts/:id/flag`)이 이야기 글에도 맞으니 앱이 그 길을 불러요.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TalkPostDeleteFlagTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private lateinit var before: CompanyApi
    private val calls = mutableListOf<String>()

    @Before fun start() {
        before = c.companyApi
        c.companyApi = object : CompanyApi by before {
            override suspend fun deletePost(device: String, id: String) { calls += "delete $id" }
            override suspend fun flag(device: String, id: String, reason: String) { calls += "flag $id $reason" }
        }
    }

    @After fun end() { c.companyApi = before }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun myPostIsDeletedAndOthersAreReported() {
        val vm = RecipeDetailViewModel(c, "WORK2345")
        idle()
        vm.deletePost("p1")
        idle()
        vm.flagPost("p2", "personal")
        idle()
        Thread.sleep(200); idle()
        assertEquals(listOf("delete p1", "flag p2 personal"), calls)
        assertEquals("신고했어요. 운영자가 살펴봐요", vm.state.value.message)
        // The reasons are the server's own (FLAGS), as on 광장.
        assertEquals(setOf("abuse", "personal", "spam", "false"), TALK_FLAG_REASONS.keys)
    }

    @Test fun aReplyStaysWhenItsPostIsGone() {
        fun post(id: String, parent: String?) = RecipePost(id, 1, null, "글 $id", Instant.EPOCH, "question", parent, mine = false, maker = false,
            usingDays = null, likes = 0, dislikes = 0)
        val posts = listOf(post("a", null), post("r1", "a"), post("r2", "gone"))
        assertEquals(listOf("a", "r2"), talkTops(posts).map { it.id })
    }
}
