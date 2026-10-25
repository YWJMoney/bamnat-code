package com.bamnat.app.ui.setup

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.SetupDraftCodec
import com.bamnat.app.imports.RosterScanner
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

/**
 * [SETUP-15][READ-05] 간호사 첫 설정 → 듀티표가 있어요 → 사진으로, end to end (1.25 순번 371, 사용자 2026-10-25 "간호사 첫
 * 설정 … 듀티표 동작 안함"): a photo of the ward's November roster, tilted as phones take it, asks whose line it is, its
 * 연차 and a code the setup does not know go in with the setup, and the calendar has every day of the month after saving.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetupRosterPhotoTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val now = LocalDateTime.of(2026, 10, 25, 10, 0)
    private val nov = YearMonth.of(2026, 11)
    private val draft: SetupDraft get() = c.setupDrafts.draft.value!!

    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
    }

    @Before fun defaults() {
        waitFor("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
    }

    @After fun noFake() { RosterScanner.fake = null }

    private val names = listOf("김간호", "이간호", "박간호")
    private val pattern = listOf("D", "D", "E", "E", "N", "N", "O", "O")
    private fun code(p: Int, d: Int) = when {
        p == 1 && d == 12 -> "연차"
        p == 1 && d == 20 -> "교육"
        else -> pattern[(d + p * 3) % pattern.size]
    }

    private fun photo(deg: Double): List<RosterWord> {
        val a = Math.toRadians(deg)
        val words = mutableListOf<RosterWord>()
        fun add(t: String, x: Int, y: Int, w: Int) {
            val cx = x + w / 2.0; val cy = y + 18.0
            val rx = cx * cos(a) - cy * sin(a); val ry = cx * sin(a) + cy * cos(a)
            words += RosterWord(t, (rx - w / 2).toInt(), (ry - 18).toInt(), (rx + w / 2).toInt(), (ry + 18).toInt(), angle = deg.toFloat())
        }
        add("11월", 500, 0, 70); add("근무표", 580, 0, 110)
        for (d in 1..30) add("$d", 260 + (d - 1) * 80, 90, 20 * "$d".length)
        names.forEachIndexed { i, n ->
            add(n, 80, 160 + i * 64, 110)
            for (d in 1..30) add(code(i, d), 260 + (d - 1) * 80, 160 + i * 64, if (code(i, d).length > 1) 56 else 22)
        }
        return words
    }

    @Test fun aRosterPhotoAtSetupFillsTheWholeMonth() = runBlocking {
        RosterScanner.fake = { photo(3.0) }
        val vm = SetupViewModel(c, now = { now })
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        assertEquals(SetupStep.ORDER, draft.step)
        vm.readRosterPhoto(app, Uri.parse("content://test/roster.jpg"))
        waitFor("the photo read") { vm.roster.value != null && !vm.rosterReading.value }
        // Three lines: asked, nothing painted yet.
        assertEquals(names, vm.roster.value!!.rows.map { it.label })
        assertTrue(draft.painted.isEmpty())
        vm.useRosterRow(1)
        assertEquals(nov, vm.paintMonth.value)
        assertEquals(28, draft.painted.size)
        assertEquals(LeaveKind.ANNUAL, draft.leaves[nov.atDay(12)]?.kind)
        // 교육 is not one of the bundle's kinds: asked here, then painted as the answer.
        val ask = vm.rosterAsks.value.single()
        assertEquals("교육", ask.first.raw)
        val day = draft.types.first { it.name == "데이" }
        vm.answerRosterCode("교육", DutyCell.Shift(day.id))
        assertEquals(day.id, draft.painted[nov.atDay(20)])
        // The leave survives the draft kept on the phone.
        assertEquals(draft.leaves, SetupDraftCodec.decode(SetupDraftCodec.encode(draft)).leaves)
        vm.next()
        vm.next()
        vm.save()
        waitFor("the save") { c.settingsRepository.current.workStyle != null && c.setupDrafts.draft.value == null }
        val snap = c.scheduleRepository.snapshot.first()
        val byName = snap.shiftTypes.associateBy { it.name }
        (1..30).forEach { d ->
            val r = snap.resolver.resolve(nov.atDay(d))
            when (val want = code(1, d)) {
                "연차" -> assertNotNull("day $d leave", r.entry?.leave)
                "교육" -> assertEquals("day $d", "데이", r.shiftType?.name)
                "O" -> assertEquals("day $d", byName.values.first { it.isOff }.id, r.shiftType?.id)
                else -> assertEquals("day $d", mapOf("D" to "데이", "E" to "이브닝", "N" to "나이트").getValue(want), r.shiftType?.name)
            }
        }
    }
}
