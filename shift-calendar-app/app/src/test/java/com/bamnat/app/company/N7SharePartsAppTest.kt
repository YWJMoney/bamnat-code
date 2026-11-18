package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.company.heldLine
import com.bamnat.app.ui.company.heldWhen
import com.bamnat.app.ui.company.sharedLine
import com.bamnat.core.plaza.SharePart
import com.bamnat.core.plaza.ShareWay
import com.bamnat.core.plaza.ShareWays
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVN-22] 검토 I10bc-03 (COMP-44·46): one part goes to the plaza without the others ([ShareFile]), the phone keeps each part's way
 * and reads a phone from before as it said ([CompanyStore.shareWays]), and what goes by itself waits on the phone for the next morning
 * ([AutoShare.nextMorning]), said with when ([heldWhen]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class N7SharePartsAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A plaza file: 주간 07~19, 야간 19~07, 휴무, an order of six, 야간 50% and one kind of extra work. */
    private fun file(day: String = "07:00", night: Double = 0.5, steps: List<String?> = listOf("주간", "주간", "야간", "야간", null, null),
        extra: Boolean = true, moning: Boolean = false, v: Int = 2): String = JSONObject()
        .put("format", "bamnat-company").put("v", v)
        .put("shiftTypes", JSONArray().apply {
            put(JSONObject().put("name", "주간").put("label", "주").put("start", day).put("end", "19:00").put("break", 60).put("off", false))
            put(JSONObject().put("name", "야간").put("label", "야").put("start", "19:00").put("end", "07:00").put("break", 60).put("off", false))
            put(JSONObject().put("name", "휴무").put("label", "휴").put("break", 0).put("off", true))
            if (moning) put(JSONObject().put("name", "모닝").put("label", "모").put("start", "05:00").put("end", "13:00").put("break", 30).put("off", false))
        })
        .put("rotation", JSONObject().put("anchor", "2026-11-01").put("steps", JSONArray(steps)))
        .put("pay", JSONObject().put("rules", JSONObject().put("night", night)))
        .apply { if (extra) put("extraKinds", JSONArray().put(JSONObject().put("name", "교육").put("rate", 1.0))) }
        .toString()

    private fun shift(f: String, name: String): JSONObject =
        JSONObject(f).getJSONArray("shiftTypes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.first { it.getString("name") == name }
    private fun night(f: String): Double = JSONObject(f).getJSONObject("pay").getJSONObject("rules").getDouble("night")
    private fun steps(f: String): Int = JSONObject(f).getJSONObject("rotation").getJSONArray("steps").length()

    @Test fun onePartGoesWithTheRestAsThePlazaHasThem() {
        val has = file()
        val now = file(day = "08:00", night = 0.6, steps = listOf("주간", "야간", null), extra = false, moning = true, v = 3)
        // Every part: the file as it is now.
        assertEquals(now, ShareFile.compose(now, has, SharePart.entries.toSet()))
        // The pay way alone: its rate and kinds of extra work from now, the shifts, hours and order as the plaza has them.
        val pay = ShareFile.compose(now, has, setOf(SharePart.PAY))!!
        assertEquals(0.6, night(pay), 0.0)
        assertFalse(JSONObject(pay).has("extraKinds"))
        assertEquals("07:00", shift(pay, "주간").getString("start"))
        assertEquals(6, steps(pay))
        assertEquals(3, JSONObject(pay).getJSONArray("shiftTypes").length())
        // The hours alone: the new hours of a shift the plaza has, its order and pay way as they were; a new shift is not the hours'.
        val times = ShareFile.compose(now, has, setOf(SharePart.TIMES))!!
        assertEquals("08:00", shift(times, "주간").getString("start"))
        assertEquals(6, steps(times))
        assertEquals(0.5, night(times), 0.0)
        assertTrue(JSONObject(times).has("extraKinds"))
        assertEquals(3, JSONObject(times).getJSONArray("shiftTypes").length())
        // The cycle alone: the shifts and order from now, a shift the plaza has keeps its hours there, a new one comes with its own.
        val cycle = ShareFile.compose(now, has, setOf(SharePart.CYCLE))!!
        assertEquals(3, steps(cycle))
        assertEquals(4, JSONObject(cycle).getJSONArray("shiftTypes").length())
        assertEquals("07:00", shift(cycle, "주간").getString("start"))
        assertEquals("05:00", shift(cycle, "모닝").getString("start"))
        assertEquals(0.5, night(cycle), 0.0)
        // The version is the newer of the two.
        assertEquals(3, JSONObject(pay).getInt("v"))
    }

    @Test fun aPlazaWithNothingTakesNoPartAlone() {
        val now = file(night = 0.6)
        assertNull(ShareFile.compose(now, null, setOf(SharePart.PAY)))
        assertNull(ShareFile.compose(now, null, setOf(SharePart.TIMES, SharePart.PAY)))
        // With the cycle: the shifts with their hours; a pay way not taken stays out.
        val cycle = ShareFile.compose(now, null, setOf(SharePart.CYCLE, SharePart.TIMES))!!
        assertFalse(JSONObject(cycle).has("pay"))
        assertFalse(JSONObject(cycle).has("extraKinds"))
        assertEquals("07:00", shift(cycle, "주간").getString("start"))
    }

    @Test fun aPhoneFromBeforeKeepsWhatItSaid() {
        fun prefs(vararg kv: Pair<String, Any>) = context.getSharedPreferences("n7-ways-${System.nanoTime()}", Context.MODE_PRIVATE).also { p ->
            p.edit().apply { kv.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as String) } }.commit()
        }
        assertEquals(ShareWays(), CompanyStore(prefs(), null).shareWays.value)
        assertEquals(ShareWays(ShareWay.AUTO, ShareWay.AUTO, ShareWay.ASK), CompanyStore(prefs("auto_share" to true), null).shareWays.value)
        assertEquals(ShareWays(ShareWay.AUTO, ShareWay.AUTO, ShareWay.AUTO), CompanyStore(prefs("auto_share" to true, "auto_pay" to true), null).shareWays.value)
        assertEquals(ShareWays(ShareWay.NEVER, ShareWay.NEVER, ShareWay.NEVER), CompanyStore(prefs("auto_share" to false), null).shareWays.value)
        // A change that waited for its day is held for the parts that go by themselves; a 나눌게요 that waited for a connection, all asked.
        val dirty = CompanyStore(prefs("auto_share" to true, "auto_dirty" to true), null)
        assertEquals(setOf(SharePart.CYCLE, SharePart.TIMES), dirty.heldParts)
        assertTrue(dirty.autoDirty)
        val once = CompanyStore(prefs("auto_once" to true), null)
        assertEquals(SharePart.entries.toSet(), once.heldParts)
        assertTrue(once.autoOnce)
        // Chosen again, it is kept in its own words and the old ones go.
        val p = prefs("auto_share" to true, "auto_pay" to true)
        val store = CompanyStore(p, null)
        store.setShareWays(ShareWays(ShareWay.ASK, ShareWay.AUTO, ShareWay.NEVER))
        assertFalse(p.contains("auto_share"))
        assertFalse(p.contains("auto_pay"))
        assertEquals(ShareWays(ShareWay.ASK, ShareWay.AUTO, ShareWay.NEVER), CompanyStore(p, null).shareWays.value)
        assertNull(store.autoShare.value)
        assertFalse(store.autoPay.value)
    }

    @Test fun whatWentIsWhatThePlazaHas() {
        val p = context.getSharedPreferences("n7-has-${System.nanoTime()}", Context.MODE_PRIVATE)
        val store = CompanyStore(p, null)
        // Asked and declined only: the plaza has nothing yet.
        store.autoBase = file()
        assertNull(store.plazaHas)
        // A phone from before that sent once: what it sent last.
        store.autoSentAt = 1L
        assertEquals(file(), store.plazaHas)
        store.markShared(file(night = 0.6))
        assertEquals(file(night = 0.6), store.plazaHas)
        assertEquals(file(night = 0.6), store.autoBase)
        // 모두 지우기: the plaza has nothing of this phone, 저절로 parts are asked again, 안 함 stays, nothing is held.
        store.setShareWays(ShareWays(ShareWay.AUTO, ShareWay.NEVER, ShareWay.AUTO))
        store.hold(setOf(SharePart.PAY), "야간 50% → 60%", 1L)
        store.forgetSent()
        assertEquals(ShareWays(ShareWay.ASK, ShareWay.NEVER, ShareWay.ASK), store.shareWays.value)
        assertTrue(store.heldParts.isEmpty())
        assertFalse(store.held.value)
    }

    @Test fun heldGoesTheNextMorningAndSaysWhen() {
        val zone = ZoneId.of("Asia/Seoul")
        val evening = ZonedDateTime.of(2026, 11, 20, 22, 30, 0, 0, zone)
        val tomorrow9 = ZonedDateTime.of(2026, 11, 21, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(tomorrow9, AutoShare.nextMorning(evening))
        // Even in the small hours: the next day's morning, never sooner.
        assertEquals(tomorrow9, AutoShare.nextMorning(ZonedDateTime.of(2026, 11, 20, 1, 0, 0, 0, zone)))
        assertEquals("내일 오전에", heldWhen(tomorrow9, evening))
        assertEquals("오늘 오전에", heldWhen(tomorrow9, ZonedDateTime.of(2026, 11, 21, 2, 0, 0, 0, zone)))
        assertEquals("11월 22일 오전에", heldWhen(ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), evening))
        assertEquals("내일 오전에 예시상사 광장에 나눠요 · 야간 50% → 60%",
            heldLine(AutoShare.Held("예시상사", "야간 50% → 60%", com.bamnat.core.time.Today.millis() + AutoShare.DAY, "main")))
        // The one line after a change was held, and after a 나눌게요 without a connection.
        assertTrue(sharedLine(AutoShare.Shared("예시상사", "야간 50% → 60%", queued = true, until = com.bamnat.core.time.Today.millis() + AutoShare.DAY))
            .startsWith("내일 오전에 예시상사 광장에 나눠요"))
        assertEquals("인터넷이 연결되면 예시상사 광장에 나눠요 · 야간 50% → 60%",
            sharedLine(AutoShare.Shared("예시상사", "야간 50% → 60%", queued = true, offline = true)))
    }
}
