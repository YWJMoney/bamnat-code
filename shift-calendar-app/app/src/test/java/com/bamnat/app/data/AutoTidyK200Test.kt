package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVK-13][RVK-14] (검토 뿌리 7, MONEY-43): nothing the app leaves out on its own goes without a word, and a second payment
 * of the same amount is a line of its own. Card texts are made up.
 */
@RunWith(AndroidJUnit4::class)
class AutoTidyK200Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val posted = LocalDate.of(2026, 10, 15).atTime(13, 21).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private var now = posted
    private val box = AutoTidy(null) { now }
    private val seen = CaptureSeen(null) { now }
    private val repo = MoneyRepository(db, clock = { now }, tidy = { box }, seen = { seen })

    @After fun close() = db.close()

    @Test fun twoPaymentsOfTheSameAmountFromOneAppAreTwoLines() = runBlocking {
        // 코인노래방 1,000원 두 번: two texts of the same app, a minute apart.
        assertEquals(MoneyRepository.Outcome.SAVED, repo.capture("신한카드(1234)승인 1,000원 10/15 13:20 코인노래방", "신한카드", posted))
        now += 60_000
        assertEquals(MoneyRepository.Outcome.SAVED, repo.capture("신한카드(1234)승인 1,000원 10/15 13:21 코인노래방", "신한카드", now))
        assertEquals(2, repo.entries.first().size)
        assertTrue(box.items.value.isEmpty())
    }

    @Test fun theSamePaymentToldByAnotherAppIsKeptToPutBack() = runBlocking {
        box.register(MoneyRepository.KIND_LEFT_OUT) { repo.putBackLeftOut(it.payload) }
        repo.capture("신한카드(1234)승인 12,500원 10/15 13:20 스타벅스", "신한카드", posted)
        now += 180_000
        assertEquals(MoneyRepository.Outcome.DUPLICATE, repo.capture("신한카드(1234)승인 12,500원 10/15 13:20 스타벅스", "신한 SOL페이", now))
        assertEquals(1, repo.entries.first().size)
        val item = box.items.value.single()
        assertEquals(AutoTidy.AREA_MONEY, item.area)
        assertTrue(item.title.contains("12,500원"))
        assertEquals(MoneyRepository.WHY_SAME, item.why)
        // 되살리기: a line of its own, marked new, and gone from the list.
        assertTrue(box.restore(item.id))
        val lines = repo.entries.first()
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.amount == 12_500L })
        assertTrue(box.items.value.isEmpty())
    }

    @Test fun whatWasRightIsLetGoAndOldOnesGoAfter30Days() = runBlocking {
        val a = box.add(AutoTidy.AREA_MONEY, "main", "스타벅스 4,500원", "같은 결제로 봤어요", MoneyRepository.KIND_LEFT_OUT, "{}")
        box.add(AutoTidy.AREA_INVEST, "main", "삼성전자 매수", "이미 적혔어요", "invest.fill", "{}")
        box.forget(a.id)
        assertEquals(listOf("삼성전자 매수"), box.items.value.map { it.title })
        // A way back that is not there yet, or one that fails: it stays listed.
        assertFalse(box.restore(box.items.value.single().id))
        assertEquals(1, box.items.value.size)
        now += AutoTidy.KEEP_MILLIS + 1
        box.purge()
        assertTrue(box.items.value.isEmpty())
    }

    @Test fun keptOnThePhoneAcrossARestart() = runBlocking {
        val file = java.io.File(context.noBackupFilesDir, "auto_tidy_test.json").apply { delete() }
        AutoTidy(file) { now }.add(AutoTidy.AREA_MONEY, "main", "택시 12,000원", "같은 결제로 봤어요", MoneyRepository.KIND_LEFT_OUT, "{\"a\":1}")
        val again = AutoTidy(file) { now }.items.value.single()
        assertEquals("택시 12,000원", again.title)
        assertEquals("{\"a\":1}", again.payload)
        file.delete()
        Unit
    }

    @Test fun theSameMessageAgainIsReadOnce() {
        // A chat app's stacked notification brings its earlier message back with each new one.
        assertTrue(seen.firstTime("com.kakao.talk|m100", "신한카드 승인 1,000원"))
        assertFalse(seen.firstTime("com.kakao.talk|m100", "신한카드 승인 1,000원"))
        // Another message of the same text (a second payment) is read.
        assertTrue(seen.firstTime("com.kakao.talk|m160", "신한카드 승인 1,000원"))
        // Two days on, it is forgotten (the text itself is never kept).
        now += CaptureSeen.KEEP_MILLIS + 1
        assertTrue(seen.firstTime("com.kakao.talk|m100", "신한카드 승인 1,000원"))
    }
}
