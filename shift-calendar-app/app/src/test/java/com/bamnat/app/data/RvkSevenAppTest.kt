package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.core.money.Receivable
import com.bamnat.core.money.ReceivableKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 2.0.0 갈래 K 7차 [RVK-42]: a 받은 기록 taken back can be undone with its income line (검토 MONEY-10·U06a-19), and the newest
 * 되돌리기 is the one that shows, not one waiting behind the 8 s of another (MONEY-14·U06a-14).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RvkSevenAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val money = MoneyRepository(db)
    private val day = LocalDate.of(2026, 11, 3)

    @After fun close() {
        db.close()
        AppSnack.resetForTest()
    }

    /** Before, '취소' on a 받은 기록 took the payment and its income line away with no 되돌리기. */
    @Test fun aPaymentTakenBackComesBackWithItsIncomeLine() = runBlocking {
        val r = money.save(Receivable(who = "예시정밀", title = "3일치 일당", amount = 450_000, date = day, kind = ReceivableKind.WORK))
        val paid = money.receive(r, 150_000, day.plusDays(5))!!
        val back = money.takeBack(paid)
        assertEquals(450_000L, money.book.first().receivables.single().left)
        assertTrue(money.entries.first().isEmpty())
        back()
        assertEquals("the payment is back", 300_000L, money.book.first().receivables.single().left)
        val line = money.entries.first().single()
        assertEquals(150_000L, line.amount)
        assertEquals("the same income line, not a new one", paid.entryId, line.id)
    }

    /** A payment that wrote no income line (a loan coming back) comes back alone. */
    @Test fun aPaymentWithoutAnIncomeLineComesBackAlone() = runBlocking {
        val r = money.save(Receivable(who = "동생", title = "빌려준 돈", amount = 300_000, date = day, kind = ReceivableKind.LOAN, countAsIncome = false))
        val paid = money.receive(r, 100_000, day.plusDays(2))!!
        assertNull(paid.entryId)
        val back = money.takeBack(paid)
        assertEquals(300_000L, money.book.first().receivables.single().left)
        back()
        assertEquals(200_000L, money.book.first().receivables.single().left)
        assertTrue(money.entries.first().isEmpty())
    }

    /** 지우고 바로 또 지우면, the 되돌리기 on show is the last one's; a '바꾸기' waits its turn as any line with a button. */
    @Test fun theNewestUndoShowsFirstAndAnAskWaitsItsTurn() {
        AppSnack.resetForTest { 0L }
        AppSnack.post(UndoMessage("‘김밥천국’ 기록을 지웠어요") {}) {}
        AppSnack.post(UndoMessage("같은 곳 기록 3건도 ‘카페’(으)로 바꿀까요?", actionLabel = "바꾸기") {}) {}
        AppSnack.post(UndoMessage("‘스타벅스’ 기록을 지웠어요") {}) {}
        AppSnack.post(UndoMessage("예산을 저장했어요", undo = null)) {}
        val shown = generateSequence { AppSnack.queue.pickNext(0)?.also { AppSnack.queue.finish(it, false) } }.toList()
        assertEquals(
            listOf("‘스타벅스’ 기록을 지웠어요", "‘김밥천국’ 기록을 지웠어요", "같은 곳 기록 3건도 ‘카페’(으)로 바꿀까요?", "예산을 저장했어요"),
            shown.map { it.message },
        )
        assertEquals(listOf(AppSnack.Kind.UNDO, AppSnack.Kind.UNDO, AppSnack.Kind.INFO, AppSnack.Kind.INFO), shown.map { it.kind })
        assertEquals(listOf("되돌리기", "되돌리기", "바꾸기", null), shown.map { it.action })
        assertEquals("a 되돌리기 shows 8 s", AppSnack.UNDO_MILLIS, shown.first().millis)
    }
}
