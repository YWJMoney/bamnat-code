package com.bamnat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.MoneyRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.money.Budgets
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.JobPayment
import com.bamnat.core.money.MoneyBudget
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.Receivable
import com.bamnat.core.money.ReceivableKind
import com.bamnat.core.money.UpcomingPayments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 (DB 36, 갈래 K) [RVK-46]: the ledger's version. A phone of 1.38 (32), 1.39 (33) and 2.0.0's 35 arrives with every row meaning what it
 * meant — repeating lines go nowhere set, budgets are every month's, categories look as usual, receivables are money owed to the user,
 * jobs got only 선금·잔금 — and then keeps a 반복 이체's '어디로', '이 달만' budgets, a category's icon and color, 줄 돈 and a job's payments,
 * through a backup and back. An older app reading that backup never takes 줄 돈 for 받을 돈 nor one month's budget for every month's.
 */
@RunWith(AndroidJUnit4::class)
class MoneyDb36Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun versions32To35ArriveMeaningTheSameAndKeepWhatIsNew() = runBlocking {
        for (version in listOf(32, 33, 35)) {
            val name = "money36-from-$version.db"
            val restoredName = "money36-restored-$version.db"
            listOf(name, restoredName).forEach(context::deleteDatabase)
            val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
            createTables(file, version).use { sql ->
                sql.execSQL("INSERT INTO money_accounts (id, name, type, last4, payDay, closeDay, payFromId, opening, color, sortOrder, hidden, remind) " +
                    "VALUES (1, '월급 통장', 'BANK', '', NULL, NULL, NULL, 1000000, 0, 0, 0, 1), (2, '적금', 'BANK', '', NULL, NULL, NULL, 0, 0, 1, 0, 1)")
                sql.execSQL("INSERT INTO fixed_costs (id, name, amount, day, kind, category, remind, repeat, monthOfYear, accountId, auto, startEpochDay, endEpochDay) " +
                    "VALUES (1, '적금', 300000, 25, 'TRANSFER', '저축', 1, 'MONTH', 1, 1, 0, NULL, NULL)")
                sql.execSQL("INSERT INTO money_budgets (category, amount) VALUES ('', 1500000), ('식비', 400000)")
                sql.execSQL("INSERT INTO money_categories (kind, name, position, hidden) VALUES ('EXPENSE', '운동', 0, 0)")
                sql.execSQL("INSERT INTO receivables (id, who, title, amount, epochDay, dueEpochDay, kind, note, countAsIncome, closedEpochDay, createdAt) " +
                    "VALUES (1, '가나다화학', '잔금', 300000, 20760, NULL, 'SALE', '', 0, NULL, 1)")
                sql.execSQL("INSERT INTO jobs (id, epochDay, title, client, place, startMinute, endMinute, amount, deposit, depositPaid, balancePaid, remindMinutes, note) " +
                    "VALUES (1, 20741, '욕실 타일', '가나다', '현장', 480, 1020, 3000000, 500000, 1, 0, NULL, '')")
                sql.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
            try {
                db.shiftTypeDao().insert(DAY.toEntity())
                val money = MoneyRepository(db)
                // What it was.
                val fixed = money.allFixed().single()
                assertNull("$version: 반복 이체는 어디로가 없던 그대로", fixed.toAccountId)
                assertNull(UpcomingPayments.entry(fixed, LocalDate.of(2026, 11, 25)).toAccountId)
                assertEquals("$version: 예산은 모두 매달", listOf(MoneyBudget("", 1_500_000), MoneyBudget("식비", 400_000)), money.allBudgets().sortedBy { it.category })
                assertEquals(CategoryEdit(MoneyKind.EXPENSE, "운동", 0, false, icon = "", color = 0), money.allCategories().single())
                val book = money.book.first()
                assertEquals("$version: 받을 돈 그대로", 1, book.receivables.size)
                assertTrue("$version: 줄 돈은 없음", book.payables.isEmpty())
                val job = money.allJobs().single()
                assertEquals("$version: 선금만 받은 일감", 500_000L, job.received)
                assertTrue(job.payments.isEmpty())

                // What 36 keeps.
                money.save(fixed.copy(toAccountId = 2))
                assertEquals(2L, money.allFixed().single().toAccountId)
                assertEquals("적금으로 가는 이체", 2L, UpcomingPayments.entry(money.allFixed().single(), LocalDate.of(2026, 11, 25)).toAccountId)
                val holiday = YearMonth.of(2027, 2)
                money.setBudget("식비", 600_000, holiday)
                money.setBudget("카페", 0, holiday)
                val budgets = money.allBudgets()
                assertEquals(600_000L, Budgets.forMonth(budgets, holiday).single { it.category == "식비" }.amount)
                assertEquals("다른 달은 매달 예산", 400_000L, Budgets.forMonth(budgets, YearMonth.of(2027, 3)).single { it.category == "식비" }.amount)
                money.saveCategories(MoneyKind.EXPENSE, listOf(CategoryEdit(MoneyKind.EXPENSE, "운동", 0, false, icon = "fitness", color = 3)))
                assertEquals("fitness" to 3, money.allCategories().single().let { it.icon to it.color })
                val payable = money.save(Receivable(who = "가나다 자재상", title = "타일 30박스", amount = 450_000, date = LocalDate.of(2026, 11, 2),
                    kind = ReceivableKind.SUPPLY, countAsIncome = false, toPay = true))
                money.receive(payable, 150_000, LocalDate.of(2026, 11, 10))
                money.receive(job, JobPayment(date = LocalDate.of(2026, 11, 5), amount = 1_000_000, label = "중도금"))
                val after = money.book.first()
                assertEquals("줄 돈은 받을 돈에 섞이지 않음", 1, after.receivables.size)
                assertEquals(300_000L, after.payables.single().left)
                assertEquals(1_500_000L, after.jobs.single().received)
                assertEquals("받을 돈에는 일감 남은 돈만", listOf(300_000L, 1_500_000L).sorted(),
                    com.bamnat.core.money.Receivables.owed(after.receivables, after.jobs, LocalDate.of(2026, 12, 1)).map { it.left }.sorted())

                // A backup and back, the same.
                val settings = SettingsRepository(context)
                val backup = BackupManager(db, settings).export()
                val json = JSONObject(backup)
                assertEquals("옛 앱이 읽는 예산은 매달 것만", 2, json.getJSONArray("budgets").length())
                assertEquals(2, json.getJSONArray("monthBudgets").length())
                assertEquals("옛 앱이 읽는 받을 돈에 줄 돈 없음", 1, json.getJSONArray("owed").length())
                assertEquals(1, json.getJSONArray("owedOut").length())
                assertEquals(1, json.getJSONArray("owedOutPaid").length())
                assertEquals(1, json.getJSONArray("jobPaid").length())
                val restored = Room.databaseBuilder(context, AppDatabase::class.java, restoredName).allowMainThreadQueries().build()
                try {
                    val again = BackupManager(restored, settings).run { restore(backup); export() }
                    assertEquals("$version: 백업 왕복", strip(backup), strip(again))
                    val back = MoneyRepository(restored).book.first()
                    assertEquals(300_000L, back.payables.single().left)
                    assertTrue(back.payables.single().toPay)
                    assertEquals(1_500_000L, back.jobs.single().received)
                    assertEquals(2L, back.fixed.single().toAccountId)
                    assertEquals(600_000L, Budgets.forMonth(back.budgets, holiday).single { it.category == "식비" }.amount)
                    assertEquals("fitness", back.categories.single().icon)
                } finally {
                    restored.close()
                }
            } finally {
                db.close()
                listOf(name, restoredName).forEach(context::deleteDatabase)
            }
        }
    }

    /** A backup made before 36 (no new keys) restores as it did: no 줄 돈, no job payments, every budget every month's. */
    @Test
    fun aBackupFromBeforeRestoresAsBefore() = runBlocking {
        val name = "money36-old-backup.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            db.shiftTypeDao().insert(DAY.toEntity())
            val money = MoneyRepository(db)
            // Something already on the phone that the backup replaces.
            money.save(Receivable(who = "자재상", amount = 10_000, date = LocalDate.of(2026, 11, 1), kind = ReceivableKind.SUPPLY, toPay = true))
            val settings = SettingsRepository(context)
            val manager = BackupManager(db, settings)
            val old = JSONObject(manager.export()).apply {
                listOf("monthBudgets", "owedOut", "owedOutPaid", "jobPaid").forEach { remove(it) }
                put("budgets", org.json.JSONArray().put(JSONObject().put("category", "식비").put("amount", 300_000)))
                put("owed", org.json.JSONArray().put(JSONObject().put("id", 7).put("who", "가나다").put("title", "").put("amount", 50_000).put("day", 20760)
                    .put("kind", "SALE").put("note", "").put("income", false).put("created", 1)))
                put("owedPaid", org.json.JSONArray())
            }
            manager.restore(old.toString())
            val book = money.book.first()
            assertTrue("옛 백업엔 줄 돈이 없으니 폰의 것도 백업대로 비움", book.payables.isEmpty())
            assertEquals(listOf(7L), book.receivables.map { it.id })
            assertFalse(book.receivables.single().toPay)
            assertEquals(listOf(MoneyBudget("식비", 300_000)), book.budgets)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun strip(json: String) = JSONObject(json).apply { remove("exportedAt") }.toString(2)

    private fun createTables(file: File, version: Int): SQLiteDatabase {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        return sql
    }

    private companion object {
        /** A backup needs a kind of day. */
        val DAY = com.bamnat.core.model.ShiftType(1, "주간", "주", 0xFF1C7ED6, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0))
        val SCHEMA_DIR = File("../database/schemas/com.bamnat.app.data.db.AppDatabase")
    }
}
