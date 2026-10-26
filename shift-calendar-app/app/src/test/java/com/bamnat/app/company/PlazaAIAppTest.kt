package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipCheck
import com.bamnat.core.pay.SlipProof
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.SetupFacts
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * [PLAZA-30][PLAZA-32][PLAZA-33][PLAZA-34] 1.27 묶음 AI from the app's own files to the real company server
 * (server/company-worker on Node): a 명세서 tick goes only with a matched month; a company's 정보 광장 is read and written by
 * its members only, another member sees a post at once; a changed setup is asked about with what changed and goes up as a new
 * version, the same values saved again ask nothing. Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaAIAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val store = PayStore(context)
    private val today = LocalDate.of(2026, 10, 19)

    @After fun close() {
        db.close()
        runBlocking { store.save(null) }
    }

    private suspend fun rotation(): ScheduleRepository {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val t = schedule.snapshot.first().shiftTypes
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day, day, night, night, null, null), today), today)
        return schedule
    }

    private fun profile(night: Double, verified: PayslipCheck? = null) = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY,
        ValueTimeline(listOf(DatedValue(today, 3_000_000))), rules = CompanyRules(nightPremium = night), verified = verified)

    @Test fun aTickGoesOnlyWithAMatchedMonthAndTheMonthIsKeptWithHowItWasMatched() = runBlocking {
        val share = CompanyShare(rotation(), store, settings, today = { today })
        // 명세서로 회사 방식 맞추기 is kept as such, so the tick knows its 1% rule.
        val month = YearMonth.now().minusMonths(1)
        store.save(profile(0.5, PayslipCheck(month, 3_000_000, 3_020_000, 0, SlipProof.RULES)), keepUndo = false)
        assertEquals(SlipProof.RULES, PayStore(context).profile.value!!.verified!!.source)
        val proof = SlipProof.of(store.profile.value!!.verified, YearMonth.now())!!
        assertTrue(proof.counts)
        withServer { base ->
            // No matched month: the tick does not go, whatever the form said.
            val none = CompanyClient(base, proof = { null })
                .report("ai-app-device-000001", "근거정밀", "", true, "", share.exportForServer())
            assertFalse(none.verified)
            assertEquals(1, none.points)
            // A matched month: 명세서 확인, 2 points; only the month and yes went.
            val yes = CompanyClient(base, proof = { proof })
                .report("ai-app-device-000002", "근거정밀", "2팀", true, "", share.exportForServer())
            assertTrue(yes.verified)
            assertEquals(2, yes.points)
            // A month too far from the payslip counts as none.
            val gap = SlipProof.of(PayslipCheck(month, 3_000_000, 3_200_000, 0), YearMonth.now())!!
            assertFalse(gap.counts)
            val far = CompanyClient(base, proof = { gap }).report("ai-app-device-000003", "근거정밀", "3팀", true, "", share.exportForServer())
            assertFalse(far.verified)
        }
    }

    @Test fun aCompanysRoomIsForItsMembersAndAnotherMemberSeesAPostAtOnce() = runBlocking {
        val share = CompanyShare(rotation(), store, settings, today = { today })
        store.save(profile(0.5), keepUndo = false)
        withServer { base ->
            val aDevice = "ai-app-device-000011"
            val bDevice = "ai-app-device-000012"
            val a = CompanyClient(base, device = { aDevice })
            val b = CompanyClient(base, device = { bDevice })
            // Phone A sends its setup: it joined, and 광장 opens to its company's 정보 광장.
            val sent = a.report(aDevice, "토의정밀", "", share.exportForServer(), "main", auto = false)
            assertTrue(sent.member!!.joined)
            val plan = StylePlaza.plan(WorkStyle.SHIFT, sent.companyId to sent.company)
            assertEquals("‘토의정밀’ 정보 광장", plan.title)
            assertEquals(sent.companyId, plan.room)
            assertTrue(a.talk(aDevice, plan.room!!, StylePlaza.ROOM).posts.isEmpty())
            val first = a.post(aDevice, plan.room!!, StylePlaza.ROOM, "야간 끝나고 통근버스 몇 시에 와요?", null, "question", emptyList(), null)
            // Phone B has not joined: neither read nor write.
            try {
                b.talk(bDevice, plan.room!!, StylePlaza.ROOM)
                fail("a phone that did not join reads the room")
            } catch (e: CompanyException) {
                assertEquals("member", e.code)
            }
            // 이 설정 쓰기 is joining: then A's post is there, under 참여자 1, and B answers it.
            assertTrue(b.join(bDevice, sent.companyId, "main").joined)
            val seen = b.talk(bDevice, plan.room!!, StylePlaza.ROOM)
            assertEquals(listOf("야간 끝나고 통근버스 몇 시에 와요?"), seen.posts.map { it.body })
            assertEquals("question", seen.posts.single().kind)
            assertEquals(10, seen.leftPosts)
            b.post(bDevice, plan.room!!, StylePlaza.ROOM, "6시 40분에 와요", first, "talk", emptyList(), null)
            val back = a.talk(aDevice, plan.room!!, StylePlaza.ROOM).posts
            assertEquals(first, back.last().parent)
            assertTrue(back.first().mine)
            // The plaza of a kind of work is anyone's with the app; 투자 이야기 takes no 종목 추천.
            b.post(bDevice, StylePlaza.FIELD, StylePlaza.ROOM, "인천 쪽 타일 단가 요즘 어때요?")
            assertEquals(1, a.talk(aDevice, StylePlaza.FIELD, StylePlaza.ROOM).posts.size)
            try {
                a.post(aDevice, StylePlaza.INVEST, StylePlaza.ROOM, "급등주 추천 드려요 오픈채팅으로 오세요")
                fail("a stock tip went up")
            } catch (e: CompanyException) {
                assertEquals("invest_advice", e.code)
            }
        }
    }

    @Test fun aChangedSetupIsAskedWithWhatChangedAndGoesUpAsANewVersion() = runBlocking {
        val c = context.appContainer
        c.scheduleRepository.ensureDefaults()
        val t = c.scheduleRepository.snapshot.first().shiftTypes
        c.scheduleRepository.setFirstPattern(WorkPattern.Rotation(today, listOf(t.first { it.name == "주간" }.id, null), today), today)
        c.payStore.save(profile(0.5), keepUndo = false)
        c.companyStore.setApplied(null)
        c.companyStore.setDraft(CompanyDraft("바뀜정밀", ""))
        c.companyStore.setPublicUse(null)
        c.companyStore.setAutoShare(null)
        c.companyStore.autoBase = null
        val before = c.companyApi
        withServer { base ->
            try {
                c.companyApi = CompanyClient(base, device = { c.companyStore.device })
                // The first time: the whole setup, sent on 나눌게요.
                c.autoShare.check()
                assertTrue(c.autoShare.ask.value!!.first)
                c.autoShare.approve()
                val id = withTimeout(15_000) { while (c.autoShare.opened.value == null) delay(20); c.autoShare.opened.value!! }
                c.autoShare.openedDone()
                assertNull(c.companyStore.autoShare.value)
                // The same values saved again ask nothing.
                c.autoShare.check()
                assertNull(c.autoShare.ask.value)
                // 야간 가산 50% → 60%: asked with that line.
                c.payStore.save(profile(0.6), keepUndo = false)
                c.autoShare.check()
                val ask = c.autoShare.ask.value!!
                assertFalse(ask.first)
                assertEquals(listOf("야간 50% → 60%"), ask.lines)
                // 나눌게요 sends it now: a new version on the server, 야간 60%, and the one line with 되돌리기.
                c.autoShare.approve()
                val line = withTimeout(15_000) { while (c.autoShare.shared.value == null) delay(20); c.autoShare.shared.value!! }
                assertFalse(line.queued)
                assertEquals("야간 50% → 60%", line.line)
                // The server keeps one version per phone and department: this phone's is now the changed one.
                val night = suspend { CompanyClient(base, device = { c.companyStore.device }).company(id).setups
                    .flatMap { s -> SetupFacts.read(s.text).filter { it.key == "rate:night" }.map { it.value } } }
                assertEquals(listOf("60%"), night())
                // 이번엔 안 할게요 on the next change keeps it off the server, and the same values are not asked again.
                c.autoShare.sharedDone()
                c.payStore.save(profile(0.7), keepUndo = false)
                c.autoShare.check()
                c.autoShare.decline()
                c.autoShare.check()
                assertNull(c.autoShare.ask.value)
                assertEquals(listOf("60%"), night())
            } finally {
                c.companyApi = before
                c.companyStore.setDraft(null)
                c.companyStore.setMember(null)
                c.companyStore.setAutoShare(null)
                c.companyStore.autoBase = null
                c.autoShare.sharedDone()
                c.payStore.save(null)
            }
        }
    }

    private suspend fun withServer(block: suspend (String) -> Unit) {
        val dir = File("../server/company-worker")
        assumeTrue(dir.resolve("test/local-server.mjs").exists())
        assumeTrue(runCatching { ProcessBuilder("node", "--version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        val port = ServerSocket(0).use { it.localPort }
        val node = ProcessBuilder("node", "test/local-server.mjs", port.toString()).directory(dir).redirectErrorStream(true).start()
        try {
            val out = node.inputStream.bufferedReader()
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < 20_000) {
                val line = out.readLine() ?: break
                if ("company worker on" in line) break
            }
            block("http://127.0.0.1:$port")
        } finally {
            node.destroy()
            node.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
