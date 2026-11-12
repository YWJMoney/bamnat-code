package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.PieceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVS-09] 검토 SN-12(3): a 배치 piece always carries a 탭 순서, so wearing one put a phone's own first tab (where the app opens)
 * back to 달력 even when the maker never touched the tabs. Only an order the maker changed is put on, and the phone's own first tab stays first.
 */
@RunWith(AndroidJUnit4::class)
class LayoutTabOrderTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val mine = listOf(TabOrder.MONEY, TabOrder.CALENDAR, TabOrder.MEMOS, TabOrder.STATS, TabOrder.PLAZA, TabOrder.SETTINGS)

    @Test fun theUsualOrderLeavesMine() = runBlocking {
        val draft = c.settingsRepository.current.copy(tabOrder = mine)
        val after = c.recipes.pieces.onDraft(draft, PieceKind.LAYOUT, mapOf("tabs" to TabOrder.DEFAULT))
        assertEquals("만든 사람이 탭을 건드리지 않았으면 내 순서 그대로", mine, after.tabOrder)
    }

    @Test fun theirOrderKeepsMyFirstTab() = runBlocking {
        val theirs = listOf(TabOrder.STATS, TabOrder.CALENDAR, TabOrder.MONEY, TabOrder.MEMOS, TabOrder.PLAZA, TabOrder.SETTINGS)
        val draft = c.settingsRepository.current.copy(tabOrder = mine)
        val after = TabOrder.of(c.recipes.pieces.onDraft(draft, PieceKind.LAYOUT, mapOf("tabs" to theirs)).tabOrder)
        assertEquals("앱을 열면 나오는 첫 탭은 내 것", TabOrder.MONEY, after.first())
        assertEquals("나머지는 작품 순서", theirs - TabOrder.MONEY, after.drop(1))
    }
}
