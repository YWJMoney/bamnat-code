package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.memo.Memo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 2.0.0 [RVI-56] (검토 U07a-14) 미루기·끝내기 from a memo's notice is not an edit: the memo keeps its 고친 시각, so '최근 수정순'
 * does not jump it to the top. Writing in it does move it.
 */
@RunWith(AndroidJUnit4::class)
class MemoNoticeOrderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private var now = 1_000L
    private val memos = MemoRepository(db, clock = { now })

    @After fun close() = db.close()

    @Test fun aNoticeActionKeepsTheOrder() = runBlocking {
        val first = memos.save(Memo(title = "택배 찾기", date = LocalDate.of(2026, 11, 12)))
        now = 2_000L
        memos.save(Memo(title = "장보기"))
        now = 3_000L
        val done = memos.save(first.withDone(true, first.date, now), touch = false)
        assertEquals("끝내기 keeps the time it was last written", 1_000L, done.updatedAtMillis)
        assertEquals(1_000L, memos.get(first.id)!!.updatedAtMillis)
        now = 4_000L
        assertEquals("writing in it moves it", 4_000L, memos.save(done.copy(body = "경비실")).updatedAtMillis)
    }
}
