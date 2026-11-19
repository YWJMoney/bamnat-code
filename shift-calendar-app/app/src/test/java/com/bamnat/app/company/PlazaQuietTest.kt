package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.0.0 [RVN-24] 검토 I22ca-03·U22c-07: turning '내 질문에 답이 오면 바로 알림' off tells the server (one line) while this phone keeps a push
 * token there, so an answer no longer wakes it; on again takes the line away; nothing is asked again while nothing changed, and a phone
 * without a token gives the server nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaQuietTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer

    private class QuietServer : CompanyApi {
        val calls = CopyOnWriteArrayList<Boolean>()
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage = throw CompanyException("offline")
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus =
            throw CompanyException("offline")
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult =
            throw CompanyException("offline")
        override suspend fun plazaQuiet(device: String, off: Boolean) {
            calls += off
        }
    }

    private lateinit var before: CompanyApi
    private val server = QuietServer()

    @Before fun put() {
        before = c.companyApi
        c.companyApi = server
        PlazaPush.setAnswers(context, true)
    }

    @After fun back() {
        c.companyApi = before
        PlazaPush.setAnswers(context, true)
        runBlocking { PlazaPush.syncQuiet(c, tokenGiven = false) }
    }

    @Test fun offTellsTheServerOnceAndOnTakesItBack() = runBlocking {
        PlazaPush.syncQuiet(c, tokenGiven = true)
        assertEquals("on, as it starts: nothing to tell", emptyList<Boolean>(), server.calls.toList())
        PlazaPush.setAnswers(context, false)
        PlazaPush.syncQuiet(c, tokenGiven = true)
        PlazaPush.syncQuiet(c, tokenGiven = true)
        assertEquals(listOf(true), server.calls.toList())
        PlazaPush.setAnswers(context, true)
        PlazaPush.syncQuiet(c, tokenGiven = true)
        assertEquals(listOf(true, false), server.calls.toList())
    }

    @Test fun withoutATokenTheServerIsToldNothing() = runBlocking {
        PlazaPush.setAnswers(context, false)
        PlazaPush.syncQuiet(c, tokenGiven = false)
        assertEquals(emptyList<Boolean>(), server.calls.toList())
    }

    /** 2.0.0 [RVN-24] 검토 I22cc-02: a new question is a gentle ask on my day off (or the morning after a night), the plain news otherwise. */
    @Test fun aNewQuestionIsAnAskOnADayOff() {
        val tag = com.bamnat.core.plaza.PlazaTag(com.bamnat.core.work.WorkStyle.SHIFT)
        assertEquals(PlazaPush.ASK_LINE, PlazaPush.questionLine(PlazaTagSource.MyDay(tag, dayOff = true)))
        assertEquals(PlazaPush.ASK_LINE, PlazaPush.questionLine(PlazaTagSource.MyDay(tag, offAfterNight = true)))
        assertEquals(PlazaPush.QUESTION_LINE, PlazaPush.questionLine(PlazaTagSource.MyDay(tag)))
        assertEquals(PlazaPush.QUESTION_LINE, PlazaPush.questionLine(null))
    }
}
