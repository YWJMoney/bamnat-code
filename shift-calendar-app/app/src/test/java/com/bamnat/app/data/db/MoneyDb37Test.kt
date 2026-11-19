package com.bamnat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.MoneyRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.CategoryRole
import com.bamnat.core.money.EntryPart
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 (DB 37, 갈래 K) [RVK-60] 검토 I06ab-01: 분류를 고정 id로. A phone of 1.38 (32), 2.0.0's 35 and 36 arrives with every line, split part,
 * repeating line and category keeping its name and getting the id that name stands for; nothing moves. Then 급여 renamed 월급 is still pay
 * once, a line read as '급여' lands on 월급, 카드값 renamed is still a card bill, and the ids go through a backup and back. A backup from before
 * 37 gets its ids when it comes in, and an older app reading a 37 backup sees the split parts it knew.
 */
@RunWith(AndroidJUnit4::class)
class MoneyDb37Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 25)

    @Test
    fun versions32To36ArriveWithTheIdsTheirNamesStandFor() = runBlocking {
        for (version in listOf(32, 35, 36)) {
            val name = "money37-from-$version.db"
            val restoredName = "money37-restored-$version.db"
            listOf(name, restoredName).forEach(context::deleteDatabase)
            val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
            createTables(file, version).use { sql ->
                val d = day.toEpochDay()
                sql.execSQL("INSERT INTO money_entries (id, epochDay, kind, amount, category, note, source, createdAt) VALUES " +
                    "(1, $d, 'INCOME', 2800000, '급여', '', 'MANUAL', 1), (2, $d, 'EXPENSE', 12000, '외식', '', 'MANUAL', 2), " +
                    "(4, $d, 'TRANSFER', 300000, '카드값', '', 'MANUAL', 4)")
                sql.execSQL("INSERT INTO money_entries (id, epochDay, kind, amount, category, note, source, createdAt, parts) VALUES " +
                    "(3, $d, 'EXPENSE', 4500, '카페', '', 'MANUAL', 3, '3000\t카페\n1500\t편의점')")
                sql.execSQL("INSERT INTO fixed_costs (id, name, amount, day, kind, category, remind) VALUES (1, '월급', 2800000, 25, 'INCOME', '급여', 1)")
                sql.execSQL("INSERT INTO money_categories (kind, name, position, hidden) VALUES ('EXPENSE', '외식', 0, 0), ('EXPENSE', '카페', 1, 0)")
                sql.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
            try {
                db.shiftTypeDao().insert(DAY.toEntity())
                val money = MoneyRepository(db)
                // What it was: every name as it was, each with its id.
                val lines = money.allEntries().associateBy { it.id }
                assertEquals("$version", listOf("급여", "외식", "카페", "카드값"), listOf(1L, 2L, 3L, 4L).map { lines.getValue(it).category })
                assertEquals(listOf("b.i.급여", "n.x.외식", "b.x.카페", "b.t.카드값"), listOf(1L, 2L, 3L, 4L).map { lines.getValue(it).categoryId })
                assertEquals("쪼갠 줄도 id", listOf(EntryPart("카페", 3000, "b.x.카페"), EntryPart("편의점", 1500, "b.x.편의점")), lines.getValue(3).parts)
                assertEquals("b.i.급여", money.allFixed().single().categoryId)
                assertEquals(listOf("n.x.외식", "b.x.카페"), money.allCategories().sortedBy { it.position }.map { it.id })
                assertEquals("$version: 예상 대신 받은 급여", 2_800_000L, money.book.first().month(YearMonth.from(day), payEstimate = 3_000_000).payCounted)

                // 급여 renamed 월급, as 분류 고치기 saves it: the lines follow, the sums still read it as pay, and 급여 is not offered again.
                money.renameCategories(MoneyKind.INCOME, listOf("급여" to "월급"))
                money.saveCategories(MoneyKind.INCOME, listOf(CategoryEdit(MoneyKind.INCOME, "월급", 0, id = CategoryRole.SALARY.id)))
                var book = money.book.first()
                assertEquals("월급", book.entries.single { it.id == 1L }.category)
                assertEquals("월급", book.fixed.single().category)
                assertEquals(2_800_000L, book.month(YearMonth.from(day), payEstimate = 3_000_000).payCounted)
                assertEquals(0L, book.month(YearMonth.from(day), payEstimate = 3_000_000).otherIncome)
                assertEquals(1, book.categoriesOf(MoneyKind.INCOME).count { it == "월급" })
                assertFalse("급여" in book.categoriesOf(MoneyKind.INCOME))
                // A line read from a 입금 알림 as '급여' lands on 월급.
                val read = money.save(MoneyEntry(date = day.plusDays(1), kind = MoneyKind.INCOME, amount = 50_000, category = "급여"))
                assertEquals("월급", read.category)
                assertEquals(CategoryRole.SALARY.id, read.categoryId)
                // 카드값 renamed: still a card bill by its id.
                money.renameCategories(MoneyKind.TRANSFER, listOf("카드값" to "카드 대금"))
                money.saveCategories(MoneyKind.TRANSFER, listOf(CategoryEdit(MoneyKind.TRANSFER, "카드 대금", 0, id = CategoryRole.CARD.id)))
                book = money.book.first()
                assertTrue(book.entries.single { it.id == 4L }.let { it.category == "카드 대금" && it.isRole(CategoryRole.CARD) })
                // A line moved to another category in the sheet takes that one's id.
                val moved = money.save(book.entries.single { it.id == 2L }.copy(category = "카페"))
                assertEquals("b.x.카페", moved.categoryId)
                // A renamed category by id only (no name list): its lines follow.
                money.saveCategories(MoneyKind.EXPENSE, listOf(CategoryEdit(MoneyKind.EXPENSE, "커피", 0, id = "b.x.카페")))
                assertEquals(setOf("커피"), money.book.first().entries.filter { it.kind == MoneyKind.EXPENSE }.map { it.category }.toSet())

                // A backup and back, the same; the parts text an older app reads is as it was.
                val settings = SettingsRepository(context)
                val backup = BackupManager(db, settings).export()
                val lineJson = JSONObject(backup).getJSONArray("money").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.single { it.getLong("id") == 3L }
                assertEquals("3000\t커피\n1500\t편의점", lineJson.getString("parts"))
                assertEquals("b.x.카페\nb.x.편의점", lineJson.getString("partIds"))
                val restored = Room.databaseBuilder(context, AppDatabase::class.java, restoredName).allowMainThreadQueries().build()
                try {
                    val again = BackupManager(restored, settings).run { restore(backup); export() }
                    assertEquals("$version: 백업 왕복", strip(backup), strip(again))
                    val back = MoneyRepository(restored).book.first()
                    assertEquals(CategoryRole.SALARY.id, back.entries.single { it.id == 1L }.categoryId)
                    assertEquals("받은 급여 둘(월급 + 읽은 급여)", 2_850_000L, back.month(YearMonth.from(day), payEstimate = 3_000_000).payCounted)
                } finally {
                    restored.close()
                }
            } finally {
                db.close()
                listOf(name, restoredName).forEach(context::deleteDatabase)
            }
        }
    }

    /** A backup made before 37 (no ids at all) gets the ids its names stand for when it comes in: its user's own names included. */
    @Test
    fun aBackupFromBeforeGetsItsIds() = runBlocking {
        val name = "money37-old-backup.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            db.shiftTypeDao().insert(DAY.toEntity())
            val money = MoneyRepository(db)
            money.saveCategories(MoneyKind.EXPENSE, listOf(CategoryEdit(MoneyKind.EXPENSE, "외식", 0)))
            money.save(MoneyEntry(date = day, kind = MoneyKind.INCOME, amount = 2_000_000, category = "급여"))
            money.save(MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = 9_000, category = "외식",
                parts = listOf(EntryPart("외식", 6_000), EntryPart("카페", 3_000))))
            val manager = BackupManager(db, SettingsRepository(context))
            val old = JSONObject(manager.export()).apply {
                listOf("money", "fixed", "categories").forEach { key ->
                    val a = getJSONArray(key)
                    for (i in 0 until a.length()) a.getJSONObject(i).apply { listOf("categoryId", "partIds", "uid", "role", "std", "scope").forEach { remove(it) } }
                }
            }
            assertFalse(old.toString().contains("b.i.급여"))
            manager.restore(old.toString())
            val book = money.book.first()
            assertEquals(CategoryRole.SALARY.id, book.entries.single { it.kind == MoneyKind.INCOME }.categoryId)
            val split = book.entries.single { it.kind == MoneyKind.EXPENSE }
            assertEquals("n.x.외식", split.categoryId)
            assertEquals(listOf("n.x.외식", "b.x.카페"), split.parts.map { it.id })
            assertEquals("n.x.외식", book.categories.single().id)
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
        val DAY = com.bamnat.core.model.ShiftType(1, "주간", "주", 0xFF1C7ED6, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0))
        val SCHEMA_DIR = File("../database/schemas/com.bamnat.app.data.db.AppDatabase")
    }
}
