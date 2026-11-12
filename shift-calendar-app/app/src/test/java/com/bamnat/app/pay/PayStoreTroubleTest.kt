package com.bamnat.app.pay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.pay.PayStore.Trouble
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * 2.0.0 [RVJ-25] 검토 PAYU-11·PAYC-21: 급여 설정 파일이 깨졌을 때 말없이 '설정 없음'이 되지 않고, 다음 저장이 깨진 파일을 덮어쓰지 않고,
 * 저장이 안 된 것도 알려요.
 */
@RunWith(AndroidJUnit4::class)
class PayStoreTroubleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.filesDir, "pay-trouble-test").apply { deleteRecursively() }
    private val file = File(dir, "profile.json")
    private val bak = File(dir, "profile.json.bak")
    private val bad = File(dir, "profile.json.bad")

    private fun pay(won: Long) = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), won))))
    private fun base(p: PayProfile?) = p?.base?.entries?.single()?.value

    @After fun clean() { dir.deleteRecursively() }

    /** The file as it was before the last save comes back, the broken one is kept aside, and the line waits across a restart until read. */
    @Test fun brokenFileComesBackFromTheCopyBeforeTheLastSave() = runBlocking {
        PayStore(context, dir).apply { save(pay(3_000_000L)); save(pay(3_200_000L)) }
        assertTrue("저장 앞의 파일이 남아요", bak.exists())
        file.writeText("{\"occupation\":")

        val again = PayStore(context, dir)
        assertEquals(Trouble.RESTORED, again.trouble.value)
        assertEquals("바로 앞 설정", 3_000_000L, base(again.profile.value))
        assertEquals("깨진 파일은 따로 둬요", "{\"occupation\":", bad.readText())
        assertEquals("되살린 설정이 파일에 다시 있어요", 3_000_000L, base(PayCodec.decode(org.json.JSONObject(file.readText()))))

        val later = PayStore(context, dir)
        assertEquals("읽기 전에는 앱을 다시 켜도 알려요", Trouble.RESTORED, later.trouble.value)
        later.troubleSeen()
        assertEquals(Trouble.NONE, later.trouble.value)
        assertEquals(Trouble.NONE, PayStore(context, dir).trouble.value)
    }

    /** Without the copy (the file broke on its first save's turn) the newest of the 7 days of changes is what there is. */
    @Test fun brokenFileComesBackFromTheChangesKept() = runBlocking {
        PayStore(context, dir).apply { save(pay(3_000_000L)); save(pay(3_200_000L)) }
        bak.delete()
        file.writeText("not json")

        val again = PayStore(context, dir)
        assertEquals(Trouble.RESTORED, again.trouble.value)
        assertEquals(3_000_000L, base(again.profile.value))
    }

    /** Nothing to bring back: it says so, keeps the broken file aside, and a new setup does not write over it. */
    @Test fun brokenFileWithNothingBehindIsSaidAndKept() = runBlocking {
        dir.mkdirs()
        file.writeText("\u0000\u0000\u0000")

        val store = PayStore(context, dir)
        assertEquals(Trouble.LOST, store.trouble.value)
        assertNull(store.profile.value)
        assertEquals("\u0000\u0000\u0000", bad.readText())
        assertEquals("앱을 다시 켜도 읽기 전에는 알려요", Trouble.LOST, PayStore(context, dir).trouble.value)

        store.save(pay(2_500_000L))
        assertEquals("다시 설정하면 줄이 사라져요", Trouble.NONE, store.trouble.value)
        assertEquals("깨진 파일은 그대로 남아요", "\u0000\u0000\u0000", bad.readText())
        assertFalse("깨진 파일이 '저장 앞의 파일'이 되지 않아요", bak.exists())
        val again = PayStore(context, dir)
        assertEquals(Trouble.NONE, again.trouble.value)
        assertEquals(2_500_000L, base(again.profile.value))
    }

    /** A save that cannot reach the storage keeps the change on the screen, says so, and 다시 저장 writes it. */
    @Test fun failedSaveIsSaidAndCanBeTriedAgain() = runBlocking {
        val store = PayStore(context, dir)
        store.save(pay(3_000_000L))
        // The folder is gone and a file stands where it was: nothing can be written into it.
        dir.deleteRecursively()
        dir.writeText("x")

        store.save(pay(3_300_000L))
        assertEquals(Trouble.NOT_SAVED, store.trouble.value)
        assertEquals("화면의 값은 그대로", 3_300_000L, base(store.profile.value))
        store.troubleSeen()
        assertEquals("저장될 때까지 남아요", Trouble.NOT_SAVED, store.trouble.value)

        dir.delete()
        store.retrySave()
        assertEquals(Trouble.NONE, store.trouble.value)
        assertEquals(3_300_000L, base(PayStore(context, dir).profile.value))
    }

    /** One entry of the timeline whose date cannot be read does not take the whole setup with it (PAYC-21). */
    @Test fun oneUnreadableEntryIsLeftOut() {
        val o = PayCodec.encode(PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(
            DatedValue(LocalDate.of(2025, 1, 1), 2_800_000L), DatedValue(LocalDate.of(2026, 1, 1), 3_000_000L)))))
        o.getJSONObject("base").getJSONArray("entries").getJSONObject(0).put("from", "2025-13-40")
        assertEquals(listOf(3_000_000L), PayCodec.decode(o).base.entries.map { it.value })
    }

    @Test fun aGoodFileReadsAsBefore() = runBlocking {
        PayStore(context, dir).save(pay(3_000_000L))
        val again = PayStore(context, dir)
        assertEquals(Trouble.NONE, again.trouble.value)
        assertEquals(3_000_000L, base(again.profile.value))
        assertFalse(bad.exists())
    }
}
