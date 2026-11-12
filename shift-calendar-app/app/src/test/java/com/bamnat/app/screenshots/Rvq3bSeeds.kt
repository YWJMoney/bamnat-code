package com.bamnat.app.screenshots

import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.SeenOk
import com.bamnat.app.share.ShareApi
import java.time.Instant

/**
 * 2.0.0 갈래 Q1 PR 3b: what [Rvq3bScreenshotTest] gives the screens. The 'before' sheet was drawn with this file's older twin (the same
 * names, without 확인·지난 판·소개 확인 중), so the test itself is the same for both.
 */
internal object Rvq3b {
    /** The leader asks 확인했어요 on this link. */
    fun watched(s: ReceivedShare): ReceivedShare = s.copy(watch = true)

    /** The share server's 열어 봄 and 확인 for each link id. */
    fun seen(base: ShareApi, opened: Map<String, Instant?>, ok: Map<String, Instant?>): ShareApi = object : ShareApi by base {
        override suspend fun seen(id: String, token: String) = opened[id]
        override suspend fun seenOk(id: String, token: String) = SeenOk(opened[id], ok[id], okKnown = true)
    }

    fun mine(items: List<Pair<RecipeItem, Triple<String, String, String?>>>, past: Map<String, Int>) = MyRecipes(0, 0, items, past = past)

    /** My own page: the 소개 the operator hid, changed, waiting for the operator. */
    fun checking(p: MakerPage): MakerPage = p.copy(bioChecking = true)

    const val AFTER = true

    /** The kind the 결제·환불 shot opens 문의 with. */
    val BILLING: com.bamnat.app.company.InquiryKind? = com.bamnat.app.company.InquiryKind.BILLING

    /** What each shot scrolls to. */
    const val LEADER = "판 확인"
    const val RECEIVED = "다 보셨는지 궁금해해요"
}
