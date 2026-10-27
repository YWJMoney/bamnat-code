package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayCodec
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.SetupEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * [ITEM-02][ITEM-03][ITEM-06][ITEM-07][PROP-02][PROP-03] 1.30 묶음 BC on the app's own files: the allowance kinds saved and
 * read (older files as 사람마다), the company file that goes to the 광장 (결정 D102: 회사 공통 정액만, 나만 받는 수당은 안 감,
 * v4 only with the new kinds), a way applied with the user's 기본급 in one save, and a proposal tried on the user's own month.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanyShareItemsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val store = PayStore(context)
    private val today = LocalDate.of(2026, 10, 19)
    private val month = YearMonth.of(2026, 10)

    @After fun close() {
        db.close()
        runBlocking { store.save(null) }
    }

    private suspend fun schedule(): ScheduleRepository {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val t = schedule.snapshot.first().shiftTypes
        val day = t.first { it.name == "주간" }.id
        val night = t.first { it.name == "야간" }.id
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day, day, night, night, null, null), today), today)
        return schedule
    }

    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), v)))
    private val meals = PayItem("m", "식대", wage(200_000), Cadence.MONTHLY, share = ItemShare.FIXED)
    private val risk = PayItem("r", "위험예지수당", ValueTimeline(), Cadence.MONTHLY, share = ItemShare.PERCENT, percent = 0.1,
        percentOf = BonusBasis.ORDINARY, ordinary = true)
    private val license = PayItem("l", "자격수당", wage(50_000), Cadence.MONTHLY, ordinary = true)
    private val own = PayItem("o", "내 차량 수당", wage(100_000), Cadence.MONTHLY, mine = true)
    private fun profile(vararg items: PayItem) =
        PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, wage(3_000_000), items = items.toList())

    private fun items(file: JSONObject) = file.getJSONObject("pay").getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test fun theKindsAreSavedAndAnOlderFileReadsAsPersonal() {
        listOf(meals.copy(fixedAmount = 180_000), risk, license, own).forEach { i ->
            assertEquals(i, PayCodec.itemFrom(PayCodec.itemJson(i)))
        }
        // 사람마다 writes nothing new: a 1.29 file and a 1.30 one are the same bytes for it.
        val json = PayCodec.itemJson(license)
        assertFalse(json.has("share") || json.has("percent") || json.has("fixedAmount") || json.has("mine"))
        // An older or newer kind, or a percent past 500%, falls back to what the app can pay.
        val odd = PayCodec.itemFrom(PayCodec.itemJson(risk).put("share", "SOMETHING_NEW").put("percent", 99.0))
        assertEquals(ItemShare.PERSONAL, odd.share)
        assertNull(odd.percent)
    }

    @Test fun theCompanyFileCarriesOnlyTheCompanysAmountAndNeverTheUsersOwn() = runBlocking {
        store.save(profile(meals, risk, license, own), keepUndo = false)
        val file = CompanyShare(schedule(), store, settings, today = { today }).exportForServer()
        assertEquals(CompanyShare.VERSION, file.getInt("v"))
        val by = items(file).associateBy { it.getString("name") }
        assertEquals(setOf("식대", "위험예지수당", "자격수당"), by.keys)
        assertEquals("FIXED", by.getValue("식대").getString("share"))
        assertEquals(200_000L, by.getValue("식대").getLong("fixedAmount"))
        assertEquals(0, by.getValue("식대").getJSONObject("amount").getJSONArray("entries").length())
        assertEquals("PERCENT", by.getValue("위험예지수당").getString("share"))
        assertEquals(0.1, by.getValue("위험예지수당").getDouble("percent"), 1e-9)
        assertEquals("ORDINARY", by.getValue("위험예지수당").getString("percentOf"))
        assertFalse(by.getValue("자격수당").has("fixedAmount"))
        assertEquals(0, by.getValue("자격수당").getJSONObject("amount").getJSONArray("entries").length())
        assertFalse(file.toString().contains("50000") || file.toString().contains("100000"))
    }

    @Test fun withTheFlagOffTheCompanysAmountStaysOnThePhone() = runBlocking {
        store.save(profile(meals, license), keepUndo = false)
        val file = CompanyShare(schedule(), store, settings, today = { today }, fixedAmounts = { false }).exportForServer()
        val meal = items(file).single { it.getString("name") == "식대" }
        assertEquals("FIXED", meal.getString("share"))
        assertFalse(meal.has("fixedAmount"))
        assertFalse(file.toString().contains("200000"))
    }

    @Test fun aFileWithoutTheNewKindsStaysReadableByOlderApps() = runBlocking {
        store.save(profile(license, own), keepUndo = false)
        val file = CompanyShare(schedule(), store, settings, today = { today }).exportForServer()
        assertTrue(file.getInt("v") < CompanyShare.VERSION)
        assertEquals(listOf("자격수당"), items(file).map { it.getString("name") })
    }

    @Test fun aWayIsAppliedWithTheUsersBaseInOneSaveAndAsksOnlyThePersonalAmounts() = runBlocking {
        store.save(profile(meals, risk, license), keepUndo = false)
        val share = CompanyShare(schedule(), store, settings, today = { today })
        val text = share.export("", withAmounts = false)
        store.save(null)
        assertEquals(listOf("자격수당"), share.amountNeeds(text).map { it.label })
        assertEquals(listOf("식대", "위험예지수당"), share.commonItems(text).map { it.name })
        assertFalse(share.applyRulesWith(text, 0, null, month))
        assertNull(store.profile.value)
        val hours = SetupEdit.of(text)!!.monthlyHours(180).text
        assertEquals(180, CompanyShare.hoursIn(hours))
        assertEquals(180, share.hoursOf(hours))
        assertNull(CompanyShare.hoursIn("{}"))
        assertTrue(share.applyRulesWith(hours, 2_800_000, 182, month))
        val p = store.profile.value!!
        assertEquals(2_800_000L, p.base.on(month).value)
        assertEquals(182, p.monthlyHours)
        val unknown = p.estimatedFields.filter { it.startsWith("item:") }
        assertEquals(listOf(Unknowns.item(p.items.single { it.name == "자격수당" }.id)), unknown)
        assertEquals(200_000L, p.items.single { it.name == "식대" }.fixedAmount)
    }

    @Test fun aProposalIsTriedOnTheUsersOwnMonthAndTakenWhenAsked() = runBlocking {
        store.save(profile(meals, license, own), keepUndo = false)
        val share = CompanyShare(schedule(), store, settings, today = { today })
        val base = share.export("", withAmounts = false)
        // The proposal takes 자격수당 out and pays 위험예지수당 as 통상임금의 10%.
        val proposal = SetupEdit.of(base)!!.removeItem("자격수당").addItem("위험예지수당").text
        val mine = share.trial(proposal, base)!!
        assertEquals(listOf("식대", "내 차량 수당", "위험예지수당"), mine.items.map { it.name })
        assertTrue(mine.items.single { it.name == "위험예지수당" }.isPercent)
        // The user's own allowance stays; nothing is saved.
        assertEquals(3, store.profile.value!!.items.size)
        // 내 급여도 이 값으로: the hours the user corrected go on their own setup.
        val edited = SetupEdit.of(base)!!.monthlyHours(180).text
        assertEquals(1, share.takeEdited(base, edited))
        assertEquals(180, store.profile.value!!.monthlyHours)
    }
}
