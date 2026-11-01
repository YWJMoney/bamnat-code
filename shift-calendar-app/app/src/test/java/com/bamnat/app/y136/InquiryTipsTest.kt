package com.bamnat.app.y136

import com.bamnat.app.ui.settings.HelpFaq
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36 PC Y20: the answers offered under a question being written fit its words, or none is offered. */
class InquiryTipsTest {
    @Test fun aQuestionWithNoMatchingWordsGetsNoUnrelatedAnswer() {
        for (text in listOf("앱이 이상해요", "보상", "화면이 좀 이상하게 보여요")) {
            val tips = HelpFaq.forQuestion(text)
            assertTrue("$text → ${tips.map { it.question }}", tips.none { it.id == "pattern" })
        }
    }

    @Test fun anAnswerSharingOneWordOnlyIsNotOffered() {
        // PC 31b695ed: '뒤로 가기를 누르면 어디로 가나요?' came for this one.
        assertTrue(HelpFaq.forQuestion("앱이 이상해요 화면이 가끔 멈춰요").none { "뒤로" in it.question })
        val tips = HelpFaq.forQuestion("화면이 이상해요")
        assertTrue(tips.map { it.question }.toString(), tips.isEmpty())
        assertTrue(HelpFaq.forQuestion("알람이 안 울려요").isNotEmpty())
    }

    @Test fun aQuestionAboutTheRotationStillGetsIt() {
        assertTrue(HelpFaq.forQuestion("교대 순서를 바꾸고 싶어요").any { it.id == "pattern" })
    }
}
