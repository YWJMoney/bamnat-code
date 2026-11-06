package com.bamnat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime

/**
 * Builds a database exactly as version 1 of the app wrote it, then opens it with the current
 * version. Room checks every table against the entities on open, so a migration that leaves a
 * column or index different from a fresh install fails here instead of on users' phones.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun version1DataSurvivesTheUpgrade() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        createVersion1(context.getDatabasePath(name).apply { parentFile?.mkdirs() })

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val types = db.shiftTypeDao().getAll().map { it.toModel() }
            assertEquals(listOf("주간"), types.map { it.name })
            // The old single alarm (90 minutes before) carries over as the first of the list.
            assertEquals(listOf(AlarmSpec(90)), types.single().alarms)
            assertEquals(true, types.single().alarmEnabled)
            assertNull(types.single().icon)

            // Version 12: the day's 2 hours of 잔업 are a piece of extra work; the day held nothing else.
            val day = assembleDays(db.dayEntryDao().getAll(), db.dayExtraDao().getAll()).single()
            assertEquals(listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120)), day.extras)
            assertTrue(db.dayEntryDao().getAll().isEmpty())
            assertNull(day.customAlarms)

            val pattern = db.patternDao().getAll().single().toModel() as WorkPattern.Rotation
            assertEquals(listOf(1L, 1L, null, null), pattern.steps)
            assertEquals(pattern.startDate, pattern.anchorDate)
            assertNull(day.leave)

            val memo = db.memoDao().getAll().single().toModel()
            assertEquals("인수인계", memo.title)
            assertNull(memo.deletedAtMillis)

            // Version 7 tables start empty and take rows [WORK-06].
            val money = db.moneyDao()
            assertEquals(0, money.allEntries().size)
            val id = money.insertJob(com.bamnat.core.money.Job(date = java.time.LocalDate.of(2026, 11, 3), title = "욕실", amount = 900_000).toEntity())
            assertEquals("욕실", money.job(id)?.toModel()?.title)
            // Version 9: a line read from a payment text keeps its place and time [LEDG-02].
            val line = com.bamnat.core.money.MoneyEntry(date = java.time.LocalDate.of(2026, 11, 3), kind = com.bamnat.core.money.MoneyKind.EXPENSE,
                amount = 4_500, category = "카페", merchant = "스타벅스", time = java.time.LocalTime.of(8, 5), reviewed = false)
            money.insertEntry(line.toEntity())
            val read = money.allEntries().single().toModel()
            assertEquals("스타벅스", read.merchant)
            assertEquals(java.time.LocalTime.of(8, 5), read.time)
            assertEquals(false, read.reviewed)
            money.insertFixed(com.bamnat.core.money.FixedCost(name = "월세", amount = 500_000, day = 25).toEntity())
            assertEquals("월세", money.allFixed().single().toModel().name)
            // Version 10: each day keeps a log of what was changed [MKT-08].
            val changes = db.dayChangeDao()
            changes.insert(DayChangeEntity(epochDay = 20000, atMillis = 5, text = "잔업 2시간"))
            assertEquals(listOf("잔업 2시간"), changes.observe(20000).first().map { it.text })
            // Version 11: the sleep log [SLP-06].
            val sleep = db.sleepDao()
            val sleepId = sleep.insert(SleepLogEntity(startMillis = 1_000, endMillis = null))
            sleep.finish(sleepId, 9_000)
            assertEquals(9_000L, sleep.all().single().endMillis)
            // Version 14: money owed, paid back in parts [RECV-01], and events brought in from the calendar [IMPORT-01].
            val owed = db.receivableDao()
            val owedId = owed.insert(com.bamnat.core.money.Receivable(who = "가나다화학", amount = 300_000, date = java.time.LocalDate.of(2026, 11, 3),
                due = java.time.LocalDate.of(2026, 11, 30)).toEntity())
            owed.insertPayment(com.bamnat.core.money.ReceivablePayment(receivableId = owedId, date = java.time.LocalDate.of(2026, 11, 10), amount = 100_000).toEntity())
            val back = owed.all().single().toModel(owed.allPayments())
            assertEquals(200_000L, back.left)
            assertEquals(java.time.LocalDate.of(2026, 11, 30), back.due)
            db.importedEventDao().upsert(ImportedEventEntity("7@2026-11-03T19:00", 20760, null, 1))
            assertEquals(listOf("7@2026-11-03T19:00"), db.importedEventDao().keys())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** [EXTRA-05] T17: every kind of version 11 extra work lands as pieces, and nothing is counted twice. */
    @Test
    fun version11ExtraWorkBecomesPieces() = runBlocking {
        val name = "migration-11.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 11)
        // 주간 09–18, 야간 22–07 (휴게 1시간 02:00부터), 휴무.
        sql.execSQL("INSERT INTO shift_types (id, name, label, colorArgb, startMinute, endMinute, breakMinutes, isOff, alarmEnabled, alarmOffsetMinutes, sortOrder, alarmOffsets, breakStartMinute) VALUES " +
            "(1, '주간', '주', 0, 540, 1080, 60, 0, 0, 60, 0, '', 720), (2, '야간', '야', 0, 1320, 420, 60, 0, 0, 60, 1, '', 120), (3, '휴무', '휴', 0, NULL, NULL, 0, 1, 0, 60, 2, '', NULL)")
        fun day(epochDay: Int, values: String) = sql.execSQL(
            "INSERT INTO day_entries (epochDay, overrideMode, overrideShiftTypeId, customStartMinute, customEndMinute, customBreakMinutes, " +
                "overtimeMinutes, holidayWorkMinutes, alarmSkipped, customAlarms, extraWork, leave, secondShiftTypeId) VALUES ($epochDay, $values)",
        )
        day(20000, "0, NULL, NULL, NULL, NULL, 120, 0, 0, NULL, 1, 0, NULL")   // 잔업 2시간
        day(20001, "0, NULL, NULL, NULL, NULL, 0, 480, 0, NULL, 2, 0, NULL")   // 쉬는 날 특근 8시간
        day(20002, "0, NULL, NULL, NULL, NULL, 120, 0, 0, NULL, 3, 0, 2")      // 대근 칩 2시간 + 두 번째 근무 야간
        day(20003, "2, 2, NULL, NULL, NULL, 0, 0, 0, NULL, 3, 0, NULL")        // 근무를 야간으로 바꾸고 대근 표시만
        day(20004, "0, NULL, NULL, NULL, NULL, 60, 0, 1, NULL, 0, 0, NULL")    // 알람 끈 날의 1시간
        day(20005, "1, NULL, NULL, NULL, NULL, 0, 0, 0, NULL, 0, 0, NULL")     // 쉬는 날로 바꾼 날
        sql.version = 11
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val days = assembleDays(db.dayEntryDao().getAll(), db.dayExtraDao().getAll()).associateBy { it.date.toEpochDay() }
            assertEquals(listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120)), days.getValue(20000).extras)
            assertEquals(listOf(ExtraEntry(ExtraKind.SPECIAL_ID, minutes = 480)), days.getValue(20001).extras)
            // The second shift keeps its own hours; the chip's 2 hours next to it are asked about.
            assertEquals(
                listOf(
                    ExtraEntry(ExtraKind.COVER_ID, LocalTime.of(22, 0), LocalTime.of(7, 0), 60, LocalTime.of(2, 0), coveredShiftId = 2),
                    ExtraEntry(ExtraKind.COVER_ID, minutes = 120, needsReview = true),
                ),
                days.getValue(20002).extras,
            )
            // A mark with no hours stays visible and is asked about; the shift change stays.
            assertEquals(ShiftOverride.Shift(2), days.getValue(20003).override)
            assertEquals(listOf(ExtraEntry(ExtraKind.COVER_ID, needsReview = true)), days.getValue(20003).extras)
            assertTrue(days.getValue(20004).alarmSkipped)
            assertEquals(listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 60)), days.getValue(20004).extras)
            assertEquals(ShiftOverride.Off, days.getValue(20005).override)
            assertTrue(days.getValue(20005).extras.isEmpty())
            assertEquals(2, db.dayExtraDao().observeNeedsReview().first())
            // The old columns are empty from now on, and days that held only extra work have no row.
            db.dayEntryDao().getAll().forEach { e ->
                assertEquals(0, e.overtimeMinutes + e.holidayWorkMinutes + e.extraWork)
                assertNull(e.secondShiftTypeId)
            }
            assertEquals(setOf(20003L, 20004L, 20005L), db.dayEntryDao().getAll().map { it.epochDay }.toSet())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** [SCHED-01] Version 12 memos stay plain memos, and can become schedules afterwards. */
    @Test
    fun version12MemosStayPlainAndCanBecomeSchedules() = runBlocking {
        val name = "migration-12.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 12)
        sql.execSQL(
            "INSERT INTO memos (id, epochDay, title, body, checklist, isChecklist, colorIndex, pinned, createdAt, updatedAt, deletedAt) " +
                "VALUES (1, 20000, '치과', '내일 오후 3시', '', 0, 2, 1, 1, 2, NULL)",
        )
        sql.version = 12
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val memo = db.memoDao().get(1)!!.toModel()
            assertEquals("내일 오후 3시", memo.body)
            assertEquals(2, memo.colorIndex)
            assertTrue(memo.pinned)
            assertNull(memo.reminder)
            assertNull(memo.reminderAsked)
            val at = java.time.LocalDateTime.of(2026, 10, 19, 15, 0)
            db.memoDao().update(memo.copy(reminder = MemoReminder(at, ring = true, notify = false), reminderAsked = at.toString()).toEntity())
            val stored = db.memoDao().get(1)!!.toModel()
            assertEquals(MemoReminder(at, ring = true, notify = false), stored.reminder)
            assertEquals("2026-10-19T15:00", stored.reminderAsked)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** [RECV-01][IMPORT-01] Version 13 data stays as it was; the new tables arrive empty. */
    @Test
    fun version13KeepsEverythingAndAddsOwed() = runBlocking {
        val name = "migration-13.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 13)
        sql.execSQL("INSERT INTO jobs (id, epochDay, title, client, place, startMinute, endMinute, amount, deposit, depositPaid, balancePaid, " +
            "remindMinutes, note, endEpochDay, costs, withholding) VALUES (1, 20760, '도배', '준호', '', NULL, NULL, 500000, 0, 0, 0, NULL, '', NULL, 0, 0)")
        sql.execSQL("INSERT INTO sleep_logs (id, startMillis, endMillis) VALUES (1, 1000, 9000)")
        sql.version = 13
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals("도배", db.moneyDao().allJobs().single().title)
            assertEquals(9_000L, db.sleepDao().all().single().endMillis)
            assertTrue(db.receivableDao().all().isEmpty())
            assertTrue(db.importedEventDao().keys().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * [LEDG-06][LEDG-08][LEDG-09] Version 14 lines and fixed costs stay as they were and read as
     * before (no account, monthly, written by hand); a card payment read with "3개월 할부" in its note
     * gets its installments; the new tables arrive empty and work.
     */
    @Test
    fun version14LedgerKeepsItsLinesAndGetsAccounts() = runBlocking {
        val name = "migration-14.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 14)
        sql.execSQL(
            "INSERT INTO money_entries (id, epochDay, kind, amount, category, note, source, createdAt, merchant, minuteOfDay, cardLast4, reviewed) " +
                "VALUES (1, 20745, 'EXPENSE', 120000, '쇼핑', '3개월 할부', 'NOTIFICATION', 1, '라마바전자', 600, '1234', 1)",
        )
        sql.execSQL(
            "INSERT INTO money_entries (id, epochDay, kind, amount, category, note, source, createdAt, merchant, minuteOfDay, cardLast4, reviewed) " +
                "VALUES (2, 20746, 'EXPENSE', 9000, '식비', '', 'MANUAL', 2, '', NULL, '', 1)",
        )
        sql.execSQL("INSERT INTO fixed_costs (id, name, amount, day, kind, category, remind) VALUES (1, '월세', 500000, 25, 'EXPENSE', '주거', 1)")
        sql.version = 14
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val dao = db.moneyDao()
            val lines = dao.allEntries().map { it.toModel() }.sortedBy { it.id }
            assertEquals(listOf(3, 1), lines.map { it.installments })
            assertEquals("라마바전자", lines[0].merchant)
            assertEquals("1234", lines[0].cardLast4)
            assertTrue(lines.all { it.accountId == null && it.tags.isEmpty() && it.parts.isEmpty() && it.memo.isEmpty() && it.fixedId == null })
            val rent = dao.allFixed().single().toModel()
            assertEquals(com.bamnat.core.money.Repeat.MONTH, rent.repeat)
            assertEquals(false, rent.auto)
            assertNull(rent.start)
            assertTrue(dao.allAccounts().isEmpty())
            assertTrue(dao.allBudgets().isEmpty())
            assertTrue(dao.allCategories().isEmpty())

            // The new parts are written and read back whole.
            val card = com.bamnat.core.money.MoneyAccount(name = "신한 Deep", type = com.bamnat.core.money.AccountType.CREDIT, last4 = "1234", payDay = 14)
            val cardId = dao.insertAccount(card.toEntity())
            assertEquals(card.copy(id = cardId), dao.allAccounts().single().toModel())
            val split = lines[1].copy(
                accountId = cardId, memo = "민지랑", tags = listOf("여행", "회식"),
                parts = listOf(com.bamnat.core.money.EntryPart("카페", 3_000)), installments = 1, fixedId = 1,
            )
            dao.restoreEntry(split.toEntity(5))
            assertEquals(split, dao.entry(2)!!.toModel())
            dao.upsertBudget(com.bamnat.core.money.MoneyBudget("식비", 300_000).toEntity())
            assertEquals(300_000L, dao.allBudgets().single().amount)
            dao.insertCategories(listOf(com.bamnat.core.money.CategoryEdit(com.bamnat.core.money.MoneyKind.EXPENSE, "운동", 0).toEntity()))
            assertEquals("운동", dao.allCategories().single().name)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** The tables of schema [version] exactly as Room wrote them, empty. */
    /** [NOTE-01] Version 15 memos keep an empty calendar list; [MEMO-01] that now means 내 달력's alone. */
    @Test
    fun version15MemosStayOnEveryCalendar() = runBlocking {
        val name = "migration-15.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 15)
        sql.execSQL(
            "INSERT INTO memos (id, epochDay, title, body, checklist, isChecklist, colorIndex, pinned, createdAt, updatedAt) " +
                "VALUES (1, 20000, '인수인계', '본문', '', 0, 0, 0, 1, 2)",
        )
        sql.version = 15
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val memo = db.memoDao().get(1)!!.toModel()
            assertTrue(memo.calendars.isEmpty())
            assertTrue(memo.showsOn("main") && !memo.showsOn("abc"))
            db.memoDao().update(memo.copy(calendars = setOf("abc", "main")).toEntity())
            val again = db.memoDao().get(1)!!.toModel()
            assertEquals(setOf("abc", "main"), again.calendars)
            assertTrue(again.isShared())
            assertTrue(!again.copy(calendars = setOf("abc")).showsOn("main"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** [HRLY-01][FLD-03][SHOP-01] Rows from version 16 come through with nothing added: no attendance, jobs confirmed, sales as a total. */
    @Test
    fun version16RowsKeepTheirMeaning() = runBlocking {
        val name = "migration-16.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 16)
        sql.execSQL("INSERT INTO jobs (id, epochDay, title, client, place, amount, deposit, depositPaid, balancePaid, note, endEpochDay, costs, withholding) " +
            "VALUES (1, 20000, '욕실', '가나다', '', 500000, 0, 0, 0, '', NULL, 0, 0)")
        sql.execSQL("INSERT INTO daily_sales (epochDay, sales, costs, note) VALUES (20000, 300000, 50000, '')")
        sql.version = 16
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val job = db.moneyDao().allJobs().single().toModel()
            assertEquals(com.bamnat.core.money.JobStatus.CONFIRMED, job.status)
            assertEquals(null, job.units)
            val sales = db.moneyDao().allSales().single().toModel()
            assertEquals(300_000L, sales.sales)
            assertEquals(0L, sales.byMethod)
            assertTrue(!sales.cardSettled && sales.cashCounted == null)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * [INV-01] Version 17 rows come through version 18 exactly as they were (attendance, a swap, a job's
     * 공수 and status, a shop's card sales, a line's proof, a repeating memo); the investor tables arrive
     * empty and take rows.
     */
    @Test
    fun version17RowsKeepTheirMeaning() = runBlocking {
        val name = "migration-17.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 17)
        sql.execSQL("INSERT INTO day_entries (epochDay, overrideMode, overtimeMinutes, holidayWorkMinutes, alarmSkipped, attendance, swapWith) " +
            "VALUES (20740, 0, 0, 0, 0, 2, '민지')")
        sql.execSQL("INSERT INTO jobs (id, epochDay, title, client, place, amount, deposit, depositPaid, balancePaid, note, status, units, dueEpochDay) " +
            "VALUES (1, 20741, '타일', '가나다', '', 300000, 0, 0, 0, '', 2, 1.5, 20760)")
        sql.execSQL("INSERT INTO daily_sales (epochDay, sales, costs, note, card, cash, cashCounted, cardSettled) VALUES (20742, 500000, 0, '', 400000, 100000, 98000, 1)")
        sql.execSQL("INSERT INTO money_entries (id, epochDay, kind, amount, category, note, source, createdAt, proof) " +
            "VALUES (1, 20743, 'EXPENSE', 33000, '자재비', '', 'MANUAL', 1, 'CARD')")
        sql.execSQL("INSERT INTO memos (id, epochDay, title, body, checklist, isChecklist, colorIndex, pinned, createdAt, updatedAt, repeat) " +
            "VALUES (1, 20744, '월세', '', '', 0, 0, 0, 1, 1, 'M25')")
        sql.version = 17
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val day = db.dayEntryDao().getAll().single()
            assertEquals(2, day.attendance)
            assertEquals("민지", day.swapWith)
            val job = db.moneyDao().allJobs().single()
            assertEquals(2, job.status)
            assertEquals(1.5, job.units!!, 1e-9)
            assertEquals(20760L, job.dueEpochDay)
            val sales = db.moneyDao().allSales().single()
            assertEquals(400_000L, sales.card)
            assertEquals(98_000L, sales.cashCounted)
            assertTrue(sales.cardSettled)
            assertEquals("CARD", db.moneyDao().allEntries().single().proof)
            assertEquals("M25", db.memoDao().getAll().single().repeat)
            val invest = db.investDao()
            assertTrue(invest.allAccounts().isEmpty() && invest.allTrades().isEmpty() && invest.allCash().isEmpty() && invest.allAlarms().isEmpty())
            val id = invest.upsertAccount(com.bamnat.core.invest.InvestAccount(name = "일반 계좌").toEntity())
            invest.upsertTrade(com.bamnat.core.invest.Trade(accountId = id, date = java.time.LocalDate.ofEpochDay(20745), symbol = "005930",
                side = com.bamnat.core.invest.Side.BUY, quantity = 1.0, price = 70_000.0).toEntity())
            assertEquals("005930", invest.allTrades().single().toModel().key)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * [LEAVE-01] Version 18 rows come through version 19 as they were (a day's leave, a piece of extra work, an
     * investor account); the 연차 원장 arrives empty and takes rows, and old extra work is paid in money, not 보상휴가.
     */
    @Test
    fun version18RowsKeepTheirMeaning() = runBlocking {
        val name = "migration-18.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 18)
        sql.execSQL("INSERT INTO day_entries (epochDay, overrideMode, overtimeMinutes, holidayWorkMinutes, alarmSkipped, attendance, swapWith) " +
            "VALUES (20740, 0, 0, 0, 0, 2, '민지')")
        sql.execSQL("INSERT INTO day_extras (id, epochDay, kindId, startMinute, endMinute, breakMinutes, minutes, needsReview, position) " +
            "VALUES (1, 20741, 2, 1080, 1260, 0, 180, 0, 0)")
        sql.execSQL("INSERT INTO invest_accounts (id, name, type, broker, feeRate, sortOrder, hidden) VALUES (1, '일반 계좌', 'GENERAL', '', NULL, 0, 0)")
        sql.version = 18
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals("민지", db.dayEntryDao().getAll().single().swapWith)
            val extra = db.dayExtraDao().getAll().single()
            assertEquals(180, extra.minutes)
            assertTrue(!extra.compLeave)
            assertEquals("일반 계좌", db.investDao().allAccounts().single().name)
            val ledger = db.leaveLedgerDao()
            assertTrue(ledger.all().isEmpty())
            val start = java.time.LocalDate.of(2026, 1, 1)
            ledger.insert(com.bamnat.core.leave.LedgerEntry(kind = com.bamnat.core.leave.EntryKind.CARRY, date = start, days = 2.5,
                expires = start.plusYears(1).minusDays(1), note = "이월").toEntity(1))
            val row = ledger.all().single().toModel()!!
            assertEquals(com.bamnat.core.leave.EntryKind.CARRY, row.kind)
            assertEquals(2.5, row.days, 1e-9)
            assertEquals(java.time.LocalDate.of(2026, 12, 31), row.expires)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * 1.38 [P3-01] A version 25 phone goes up through 26 (가계부) to 27 (Phase 3) with its data as it was, and the new 팀·사업장
     * tables start empty and take rows: a team with its cells and a 사업장 with a staff member and a changed day.
     */
    @Test
    fun version25GoesUpThrough26To27() = runBlocking {
        val name = "migration-25-27.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 25)
        sql.execSQL("INSERT INTO money_accounts (id, name, type, last4, payDay, closeDay, payFromId, opening, color, sortOrder, hidden, remind) " +
            "VALUES (1, '신한 Deep', 'CREDIT', '1234', 14, NULL, NULL, 0, 0, 0, 0, 1)")
        sql.version = 25
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(AppDatabase.MIGRATIONS.last().endVersion, db.openHelper.writableDatabase.version) // 27, and on (28)
            assertEquals("신한 Deep", db.moneyDao().allAccounts().single().name)
            assertTrue(db.teamDao().teams().isEmpty() && db.payrollDao().workplaces().isEmpty())
            db.teamDao().upsertTeam(TeamEntity("t", "7병동 A팀", "", "[]", "", 1, false))
            db.teamDao().upsertMembers(listOf(TeamMemberEntity("m", "t", "민지", "책임", "CHARGE", null, 0, null, null, true)))
            db.teamDao().replaceMonth("t", 20759, 20788, listOf(TeamCellEntity("t", "m", 20760, 1, "MANUAL", false)))
            assertEquals(1L, db.teamDao().cells("t", 20759, 20788).single().shiftId)
            db.payrollDao().upsertWorkplace(WorkplaceEntity("p", "우리 카페", true, 10, true, 1, null, 1))
            assertEquals("29판 계산 단위 기본은 1분", 1, db.payrollDao().workplaces().single().minuteUnit)
            db.payrollDao().upsertStaff(StaffEntity("s", "p", "준호", "HOUR", 10_320, "[]", null, null, null, "", "{}", true, "{}", null, 0))
            db.payrollDao().upsertWork(StaffWorkEntity("s", 20760, "ABSENT", null, null))
            assertEquals("ABSENT", db.payrollDao().work().single().kind)
            assertEquals("29판 출퇴근 링크는 처음엔 없음", null, db.payrollDao().staff().single().clockId)
            db.payrollDao().insertPunches(listOf(StaffPunchEntity("s", 1_000L, "IN"), StaffPunchEntity("s", 1_000L, "IN")))
            assertEquals("같은 기록은 한 번", 1, db.payrollDao().punches().size)
            db.payrollDao().deleteStaff("s")
            assertTrue("직원을 지우면 출퇴근 기록도", db.payrollDao().punches().isEmpty())
            assertTrue(db.payrollDao().work().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * 1.38 [LEDH-25][LEDH-27] A version 25 account comes through version 26 (and on, through 1.38 Phase 3's 27) as it was:
     * 이자 없음, 매달 갚는 돈 없음, 실적 구간 없음. A loan and a card's 실적 구간 then save and read back.
     */
    @Test
    fun version25AccountsKeepTheirMeaning() = runBlocking {
        val name = "migration-25.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 25)
        sql.execSQL("INSERT INTO money_accounts (id, name, type, last4, payDay, closeDay, payFromId, opening, color, sortOrder, hidden, remind) " +
            "VALUES (1, '신한 Deep', 'CREDIT', '1234', 14, NULL, NULL, 0, 0, 0, 0, 1)")
        sql.execSQL("INSERT INTO envelopes (id, name, target, dueEpochDay, kind, sharePercent, emoji, sortOrder, closed, createdEpochDay) " +
            "VALUES (1, '여행', 1200000, NULL, 0, 0, '', 0, 0, NULL)")
        sql.version = 25
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val card = db.moneyDao().allAccounts().single().toModel()
            assertEquals(14, card.payDay)
            assertEquals(0.0, card.rate, 0.0)
            assertEquals(0L, card.monthlyPay)
            assertTrue(card.perfTiers.isEmpty() && card.perfSkip.isEmpty())
            assertEquals("여행", db.moneyDao().allEnvelopes().single().name)
            db.moneyDao().insertAccount(card.copy(id = 2, name = "전세 대출", type = com.bamnat.core.money.AccountType.OTHER,
                opening = -50_000_000, rate = 3.9, monthlyPay = 400_000).toEntity())
            db.moneyDao().insertAccount(card.copy(id = 3, perfTiers = listOf(500_000, 300_000), perfSkip = listOf("세금")).toEntity())
            val back = db.moneyDao().allAccounts().map { it.toModel() }.associateBy { it.id }
            assertEquals(3.9, back.getValue(2).rate, 1e-9)
            assertEquals(400_000L, back.getValue(2).monthlyPay)
            assertEquals(listOf(300_000L, 500_000L), back.getValue(3).perfTiers)
            assertEquals(listOf("세금"), back.getValue(3).perfSkip)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /**
     * 1.38 갈래 2 (DB 28판, 이 날만 빼기): a version 27 repeating memo comes through as it was, with no day taken off; a day
     * taken off then saves, reads back and keeps the memo off that day only.
     */
    @Test
    fun version27MemosKeepTheirMeaning() = runBlocking {
        val name = "migration-27.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sql = createTables(file, 27)
        sql.execSQL("INSERT INTO memos (id, epochDay, title, body, checklist, isChecklist, colorIndex, pinned, createdAt, updatedAt, " +
            "deletedAt, remindAt, remindRing, remindNotify, remindAsked, calendars, repeat) " +
            "VALUES (1, 20760, '분리수거', '', '', 0, 0, 0, 1, 1, NULL, NULL, 0, 0, NULL, '', 'W2')")
        sql.version = 27
        sql.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val memo = db.memoDao().getAll().single().toModel()
            assertEquals("W2", memo.repeat)
            assertTrue(memo.skips.isEmpty())
            val start = java.time.LocalDate.ofEpochDay(20760)
            val off = start.plusWeeks(1)
            db.memoDao().update(memo.copy(skips = setOf(off)).toEntity())
            val back = db.memoDao().getAll().single().toModel()
            assertEquals(setOf(off), back.skips)
            assertEquals(listOf(start, start.plusWeeks(2)), com.bamnat.core.memo.MemoRepeat.datesIn(back, start, start.plusWeeks(2)))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createTables(file: File, version: Int): SQLiteDatabase {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        return sql
    }

    private fun createVersion1(file: File) {
        val schema = JSONObject(File(SCHEMA_DIR, "1.json").readText()).getJSONObject("database")
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
        sql.execSQL(
            "INSERT INTO shift_types (id, name, label, colorArgb, startMinute, endMinute, breakMinutes, isOff, " +
                "alarmEnabled, alarmOffsetMinutes, alarmSoundUri, sortOrder) " +
                "VALUES (1, '주간', '주', 4280391411, 480, 1200, 60, 0, 1, 90, NULL, 0)",
        )
        sql.execSQL(
            "INSERT INTO day_entries (epochDay, overrideMode, overrideShiftTypeId, customStartMinute, customEndMinute, " +
                "customBreakMinutes, overtimeMinutes, holidayWorkMinutes, alarmSkipped) " +
                "VALUES (20000, 0, NULL, NULL, NULL, NULL, 120, 0, 0)",
        )
        sql.execSQL("INSERT INTO pattern (id, kind, startEpochDay, endEpochDay, steps) VALUES (1, 'ROTATION', 20000, NULL, '1,1,,')")
        sql.execSQL(
            "INSERT INTO memos (id, epochDay, title, body, checklist, isChecklist, colorIndex, pinned, createdAt, updatedAt) " +
                "VALUES (1, 20000, '인수인계', '본문', '', 0, 0, 0, 1, 2)",
        )
        sql.version = 1
        sql.close()
    }

    private companion object {
        val SCHEMA_DIR = File("../database/schemas/com.bamnat.app.data.db.AppDatabase")
    }
}
