package com.bamnat.app.screenshots

import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.Inquiry
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.company.InquiryMessage
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.RecipePost
import com.bamnat.app.recipe.RecipeRequest
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.core.work.WorkStyle
import java.time.Duration
import java.time.Instant

/**
 * 2.0.0 갈래 Q1 PR 3c: what [Rvq3cScreenshotTest] gives the screens. The 'before' sheet was drawn with this file's older twin (the same
 * names, without what the 4c·4e servers send: 다시 물었어요, 고친 판 한 줄, minApp, 만드는 중 D+n, 확인 묻기, 쪽 넘김, 지난 이야기), so the
 * test itself is the same for both.
 */
internal object Rvq3c {
    const val AFTER = true

    private fun ago(now: Instant, h: Long) = now.minus(Duration.ofHours(h))

    /** 내 문의: one written to again after the answer (with two photos), one whose fix is out in a newer 판. */
    fun inquiries(now: Instant): List<Inquiry> = listOf(
        Inquiry("q5", InquiryKind.BUG, "open", ago(now, 72), ago(now, 1), listOf(
            InquiryMessage(false, "[알람] 새벽 근무 날 알람이 안 울려요. 다시 놓아도 그래요.", ago(now, 72)),
            InquiryMessage(true, "불편을 드려 죄송해요. 알람을 한 번 껐다 켜 주시겠어요? 밤낮 운영팀", ago(now, 50)),
            InquiryMessage(false, "껐다 켜도 그대로예요. 설정 화면이랑 달력 사진 붙여요.", ago(now, 1)),
        ), photos = 2, stage = "checking", reopened = true),
        Inquiry("q4", InquiryKind.BUG, "answered", ago(now, 240), ago(now, 20), listOf(
            InquiryMessage(false, "[달력] 달력 칸을 누르면 다음 달 날짜가 열려요.", ago(now, 240)),
            InquiryMessage(true, "알려 주셔서 고마워요. 고친 업데이트가 나왔어요. 밤낮 운영팀", ago(now, 20)),
        ), stage = "released", version = "2.0.0",
            fixedLine = "2.0.0에서 고쳤어요. 앱을 2.0.0 이상으로 업데이트한 뒤 한 번 봐 주세요."),
    )

    /** What a work made for 1.39 answers this older app. */
    fun newer(): CompanyException = CompanyException("update", minApp = 53)

    /** 요청 판: one being made for five days, one finished and waiting for me (I asked it). */
    fun requests(now: Instant): List<RecipeRequest> = listOf(
        RecipeRequest("r2", WorkStyle.SHIFT, "4조 2교대 야간이 한눈에 보이는 테마가 있으면 좋겠어요", 7, "done", "NGT2K4M7", ago(now, 300),
            mine = true, taking = false, liked = false, checking = true, askCheck = true, checkBy = now.plus(Duration.ofDays(6))),
        RecipeRequest("r1", WorkStyle.SHIFT, "주말 야간 수당을 따로 보여 주는 급여 칸", 12, "making", null, ago(now, 400),
            mine = false, taking = false, liked = true, makingSince = ago(now, 5 * 24), reopenAt = now.plus(Duration.ofDays(9))),
    )

    /** A maker with 75 works: the first 60 came, more on the server. */
    fun maker(p: MakerPage): MakerPage = p.copy(themeTotal = 52, tabTotal = 23, more = true, pageNo = 0)

    /** A busy work's talk: the newest 300 came (here 14), older on the server. */
    fun talk(posts: List<RecipePost>): RecipeTalk = RecipeTalk(posts, null, more = true)
}
