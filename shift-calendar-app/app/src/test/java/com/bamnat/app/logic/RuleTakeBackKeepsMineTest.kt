package com.bamnat.app.logic

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVM-47] 검토 STU-14 (조건 10): '규칙이 쓴 것 모두 되돌리기'가 ① 규칙이 더한 줄을 내가 고쳤으면 메모 전체를 규칙 전 글로 되돌려
 * 그 뒤에 쓴 글을 지우고, ② 규칙이 만든 메모에 내가 채운 내용째 지우고, ③ 체크 목록은 자리 번호로 돌려 엉뚱한 항목을 체크했어요.
 */
@RunWith(AndroidJUnit4::class)
class RuleTakeBackKeepsMineTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val day get() = Today.date()
    private suspend fun run(act: LogicAct) = LogicRunner.run(c, act, day, LogicRunner.facts(c).scope(day), "물 규칙", quiet = true, trusted = true)
    private suspend fun memo(id: Long) = c.memoRepository.get(id)

    @Test fun aLineIChangedLeavesTheMemoAsIWroteIt() = runBlocking {
        val m = c.memoRepository.save(Memo(date = day, title = "오늘", body = "아침", calendars = setOf(c.calendars.active.value)))
        run(LogicAct(LogicVerb.MEMO_LINE, "물 3잔"))
        assertEquals("아침\n물 3잔", memo(m.id)!!.body)
        c.memoRepository.save(memo(m.id)!!.copy(body = "아침\n물 3잔 마심\n점심은 김밥"))
        LogicRunner.undoAll(c)
        assertEquals("고친 줄과 그 뒤 글이 그대로", "아침\n물 3잔 마심\n점심은 김밥", memo(m.id)!!.body)
        assertEquals(1, LogicRunner.lastKept)
    }

    @Test fun onlyTheRulesLineGoesWhenIWroteMoreAfterIt() = runBlocking {
        val m = c.memoRepository.save(Memo(date = day, title = "오늘", body = "아침", calendars = setOf(c.calendars.active.value)))
        run(LogicAct(LogicVerb.MEMO_LINE, "물 3잔"))
        c.memoRepository.save(memo(m.id)!!.copy(body = memo(m.id)!!.body + "\n점심은 김밥"))
        LogicRunner.undoAll(c)
        assertEquals("아침\n점심은 김밥", memo(m.id)!!.body)
        assertEquals(0, LogicRunner.lastKept)
    }

    @Test fun aMemoTheRuleMadeThatIFilledStays() = runBlocking {
        run(LogicAct(LogicVerb.NEW_MEMO, "운동 기록"))
        val made = c.memoRepository.memos.first().single { it.date == day && !it.inTrash }
        c.memoRepository.save(made.copy(body = "스쿼트 30개"))
        run(LogicAct(LogicVerb.NEW_MEMO, "물 기록"))
        val untouched = c.memoRepository.memos.first().single { it.date == day && !it.inTrash && it.id != made.id }
        LogicRunner.undoAll(c)
        assertNotNull("내가 채운 메모는 남아요", memo(made.id)?.takeIf { !it.inTrash })
        assertNull("손대지 않은 메모는 빠져요", memo(untouched.id)?.takeIf { !it.inTrash })
        assertEquals(1, LogicRunner.lastKept)
    }

    @Test fun ticksComeBackByTheirWords() = runBlocking {
        val m = c.memoRepository.save(Memo(date = day, title = "점검", calendars = setOf(c.calendars.active.value), isChecklist = true,
            checklist = listOf(ChecklistItem("약품", checked = true), ChecklistItem("인수인계", checked = false), ChecklistItem("서명", checked = true))))
        run(LogicAct(LogicVerb.CHECK_RESET, ""))
        assertEquals(listOf(false, false, false), memo(m.id)!!.checklist.map { it.checked })
        // I put a new item first and tick 인수인계.
        c.memoRepository.save(memo(m.id)!!.let { it.copy(checklist = listOf(ChecklistItem("물품")) + it.checklist.map { x -> if (x.text == "인수인계") x.copy(checked = true) else x }) })
        LogicRunner.undoAll(c)
        assertEquals(mapOf("물품" to false, "약품" to true, "인수인계" to true, "서명" to true), memo(m.id)!!.checklist.associate { it.text to it.checked })
    }
}
