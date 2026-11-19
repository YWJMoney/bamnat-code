package com.bamnat.app.ui.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVJ-69] (총괄 HQ-108, 디자인 품질 조건 2·8) 팀 근무표의 이달 도구: every stage fills at most one button, shows two 글자 단추 at
 * most beside it, keeps 확정 one tap away, and '더 보기' holds every tool in 받기·채우기·보기 whatever the stage.
 */
class TeamToolsTest {
    private fun t(label: String) = TeamTool(label) {}
    private val confirmWords = "확정하고 팀원 링크 보내기"
    private fun tools(confirm: String? = confirmWords, swap: Boolean = true) = TeamTools(
        ask = t("근무 신청 받기"), wishes = t("신청·희망 3"), swap = if (swap) t("바꾸기 요청 받기") else null,
        fill = t("빈칸 자동 채우기"), copy = t("지난달 이어 칠하기"), import = t("파일·사진에서 가져오기"), need = t("근무별 인원"),
        fair = t("공정성 표"), members = t("팀원"), confirm = confirm?.let(::t),
    )
    private val all = listOf("근무 신청 받기", "신청·희망 3", "바꾸기 요청 받기", "빈칸 자동 채우기", "지난달 이어 칠하기", "파일·사진에서 가져오기",
        "근무별 인원", "공정성 표", "팀원")

    @Test fun oneFilledTwoBesideAndEveryToolInMore() {
        TeamStage.entries.forEach { st ->
            val l = tools().layout(st)
            assertTrue("$st: 글자 단추는 둘까지", l.beside.size <= 2)
            assertTrue("$st: 채운 단추가 옆에 또 서지 않아요", l.filled == null || l.beside.none { it === l.filled })
            assertEquals(listOf("받기", "채우기", "보기"), l.groups.map { it.first })
            assertEquals("$st: '더 보기'는 늘 같은 아홉", all.toSet(), l.groups.flatMap { it.second }.map { it.label }.toSet())
            assertTrue("$st: 확정은 늘 한 번에", l.filled?.label == confirmWords || l.beside.any { it.label == confirmWords })
        }
    }

    @Test fun theStageFillsItsOwnButton() {
        assertEquals("빈칸 자동 채우기", tools().layout(TeamStage.FILL).filled?.label)
        assertEquals(confirmWords, tools().layout(TeamStage.CONFIRM).filled?.label)
        assertEquals("바뀐 칸 2개로 다시 확정", tools("바뀐 칸 2개로 다시 확정").layout(TeamStage.RECONFIRM).filled?.label)
        listOf(TeamStage.REVIEW, TeamStage.ASKING, TeamStage.SENT).forEach { assertNull("$it: 그 단계의 단추는 다른 카드에", tools().layout(it).filled) }
        assertEquals(listOf("지난달 이어 칠하기", confirmWords), tools().layout(TeamStage.FILL).beside.map { it.label })
        assertEquals(listOf("바꾸기 요청 받기", "링크 다시 보내기"), tools("링크 다시 보내기").layout(TeamStage.SENT).beside.map { it.label })
        // A 바꾸기 round already open: 공정성 표 takes its place.
        assertEquals(listOf("공정성 표", "링크 다시 보내기"), tools("링크 다시 보내기", swap = false).layout(TeamStage.SENT).beside.map { it.label })
    }

    /** New links to make (확정 hidden for the 봤어요 line's button): the bar keeps its two and '더 보기'. */
    @Test fun withoutConfirmTheBarStillStands() {
        val l = tools(confirm = null).layout(TeamStage.SENT)
        assertNull(l.filled)
        assertEquals(listOf("바꾸기 요청 받기"), l.beside.map { it.label })
    }
}
