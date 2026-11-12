package com.bamnat.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.EntryPart
import com.bamnat.core.money.FixedCost
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyBudget
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.money.Repeat
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import com.bamnat.core.time.Today

/** [LEDG-01] Household ledger lines; version 7, place/time/card/reviewed in version 9. */
@Entity(tableName = "money_entries", indices = [Index("epochDay")])
data class MoneyEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** [MoneyKind.name]. */
    val kind: String,
    val amount: Long,
    val category: String,
    val note: String,
    /** [MoneySource.name]. */
    val source: String,
    val createdAt: Long,
    /** Version 9: place, time and card read from a payment text, and whether the user has seen it [LEDG-02]. */
    @ColumnInfo(defaultValue = "''") val merchant: String = "",
    @ColumnInfo(defaultValue = "NULL") val minuteOfDay: Int? = null,
    @ColumnInfo(defaultValue = "''") val cardLast4: String = "",
    @ColumnInfo(defaultValue = "1") val reviewed: Boolean = true,
    /** Version 15: account and transfer target [LEDG-08], memo, tags and split parts [LEDG-11], installments, repeating line [LEDG-09]. */
    @ColumnInfo(defaultValue = "NULL") val accountId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val toAccountId: Long? = null,
    @ColumnInfo(defaultValue = "''") val memo: String = "",
    /** Tags joined by commas. */
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    /** Split parts, one "amount<TAB>category" a line; empty for one category. */
    @ColumnInfo(defaultValue = "''") val parts: String = "",
    @ColumnInfo(defaultValue = "1") val installments: Int = 1,
    @ColumnInfo(defaultValue = "NULL") val fixedId: Long? = null,
    /** Version 17 [FLD-08]. */
    @ColumnInfo(defaultValue = "''") val proof: String = "",
    @ColumnInfo(defaultValue = "''") val receipt: String = "",
    /** Version 22 [LEDX-17]: 유이자 할부's yearly fee in percent; 0 for 무이자. */
    @ColumnInfo(defaultValue = "0") val installmentRate: Double = 0.0,
)

/** [LEDG-05] Money leaving on a set day each month; version 9. [LEDG-09] Any repeating line since version 15. */
@Entity(tableName = "fixed_costs")
data class FixedCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val day: Int,
    val kind: String,
    val category: String,
    val remind: Boolean,
    /** [com.bamnat.core.money.Repeat.name]. */
    @ColumnInfo(defaultValue = "'MONTH'") val repeat: String = "MONTH",
    @ColumnInfo(defaultValue = "1") val monthOfYear: Int = 1,
    @ColumnInfo(defaultValue = "NULL") val accountId: Long? = null,
    @ColumnInfo(defaultValue = "0") val auto: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val startEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val endEpochDay: Long? = null,
    /** Version 36 (2.0.0 [RVK-46] 검토 MONEY-07): where a repeating 이체 goes (적금·대출 상환·내 다른 계좌); null for spending or income. */
    @ColumnInfo(defaultValue = "NULL") val toAccountId: Long? = null,
)

/** [LEDG-08] Cash, bank accounts, cards and pay balances; version 15. */
@Entity(tableName = "money_accounts")
data class MoneyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [AccountType.name]. */
    val type: String,
    val last4: String,
    val payDay: Int?,
    val closeDay: Int?,
    val payFromId: Long?,
    val opening: Long,
    val color: Int,
    val sortOrder: Int,
    val hidden: Boolean,
    val remind: Boolean,
    /** Version 26 (1.38 [LEDH-25]): 대출·빌린 돈's yearly interest in percent and the amount paid each month. */
    @ColumnInfo(defaultValue = "0") val rate: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val monthlyPay: Long = 0,
    /** Version 26 (1.38 [LEDH-27]): a card's 전월 실적 구간 ("300000,500000") and the categories it does not count. */
    @ColumnInfo(defaultValue = "''") val perfTiers: String = "",
    @ColumnInfo(defaultValue = "''") val perfSkip: String = "",
)

/**
 * [LEDG-06] Monthly budgets; "" is the whole month's. Version 15. Version 36 (2.0.0 [RVK-46] 검토 I06aa-05): [month] as yyyyMM for a
 * budget of that month only ('이 달만'), 0 for every month (every budget of 35 is every month's, as it was).
 */
@Entity(tableName = "money_budgets", primaryKeys = ["category", "month"])
data class MoneyBudgetEntity(
    val category: String,
    val amount: Long,
    @ColumnInfo(defaultValue = "0") val month: Int = 0,
)

/** 1.37 [LEDH-11][LEDH-13] 모으기·세금 봉투. Version 25. */
@Entity(tableName = "envelopes")
data class EnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val dueEpochDay: Long?,
    val kind: Int,
    val sharePercent: Int,
    val emoji: String,
    val sortOrder: Int,
    val closed: Boolean,
    val createdEpochDay: Long?,
)

/** 1.37 [LEDH-11] Money put in or taken out of an envelope. Version 25. */
@Entity(tableName = "envelope_moves", indices = [Index("envelopeId")])
data class EnvelopeMoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val envelopeId: Long,
    val epochDay: Long,
    val amount: Long,
    val note: String,
    val entryId: Long?,
)

/** [LEDG-14] Categories the user added, hid or moved, per kind of line. Version 15. */
@Entity(tableName = "money_categories", primaryKeys = ["kind", "name"])
data class MoneyCategoryEntity(
    val kind: String,
    val name: String,
    val position: Int,
    val hidden: Boolean,
    /** Version 36 (2.0.0 [RVK-46] 검토 U06a-15·I06aa-09): the icon and color the user picked ([com.bamnat.core.money.CategoryEdit]); "" and 0 for the usual. */
    @ColumnInfo(defaultValue = "''") val icon: String = "",
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
)

/** [LEDG-04] The category the user gave a place; it wins over the built-in dictionary. Version 9. */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey val merchant: String,
    val category: String,
)

/** [WORK-02] Booked jobs for field and freelance work; version 7. */
@Entity(tableName = "jobs", indices = [Index("epochDay")])
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val title: String,
    val client: String,
    val place: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val amount: Long,
    val deposit: Long,
    val depositPaid: Boolean,
    val balancePaid: Boolean,
    val remindMinutes: Int?,
    val note: String,
    /** Version 8: last day of a job over several days, its costs and 3.3% withholding. */
    @ColumnInfo(defaultValue = "NULL") val endEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "0") val costs: Long = 0,
    @ColumnInfo(defaultValue = "0") val withholding: Boolean = false,
    /** Version 17: [com.bamnat.core.money.JobStatus.code] [FLD-03], 공수 [FLD-02], due day [FLD-09]. */
    @ColumnInfo(defaultValue = "1") val status: Int = 1,
    @ColumnInfo(defaultValue = "NULL") val units: Double? = null,
    @ColumnInfo(defaultValue = "NULL") val dueEpochDay: Long? = null,
)

/**
 * Version 36 (2.0.0 [RVK-46] 검토 MONEY-29·T06a-01·I06ab-04): money that came in for a job besides 선금·잔금 — 계약금·중도금·나눠 받은 돈 —
 * each with its day. Every job of 35 has none: what it received is its 선금·잔금 as before.
 */
@Entity(tableName = "job_payments", indices = [Index("jobId")])
data class JobPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val epochDay: Long,
    val amount: Long,
    /** What it was ('계약금', '중도금', ''). */
    val label: String,
    val note: String,
)

/** [WORK-03] One row per day a shop recorded; version 7. */
@Entity(tableName = "daily_sales")
data class DailySalesEntity(
    @PrimaryKey val epochDay: Long,
    val sales: Long,
    val costs: Long,
    val note: String,
    /** Version 17: by payment method [SHOP-01], cash counted [SHOP-03], card sales paid in [SHOP-02]. */
    @ColumnInfo(defaultValue = "0") val card: Long = 0,
    @ColumnInfo(defaultValue = "0") val cash: Long = 0,
    @ColumnInfo(defaultValue = "0") val transfer: Long = 0,
    @ColumnInfo(defaultValue = "0") val delivery: Long = 0,
    @ColumnInfo(defaultValue = "NULL") val cashCounted: Long? = null,
    @ColumnInfo(defaultValue = "0") val cardSettled: Boolean = false,
    /** Version 22 [TAXP-14]: of [cash], how much a 현금영수증 was issued for. */
    @ColumnInfo(defaultValue = "0") val cashReceipt: Long = 0,
    /** Version 23 [J133-29]: of [costs], how much came with a proof that carries VAT (세금계산서·카드·현금영수증). */
    @ColumnInfo(defaultValue = "0") val costsProven: Long = 0,
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM money_entries ORDER BY epochDay DESC, id DESC")
    fun observeEntries(): Flow<List<MoneyEntryEntity>>

    @Query("SELECT * FROM money_entries")
    suspend fun allEntries(): List<MoneyEntryEntity>

    @Insert
    suspend fun insertEntry(e: MoneyEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreEntry(e: MoneyEntryEntity)

    @Query("DELETE FROM money_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM jobs ORDER BY epochDay, startMinute, id")
    fun observeJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs")
    suspend fun allJobs(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun job(id: Long): JobEntity?

    @Insert
    suspend fun insertJob(e: JobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreJob(e: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)

    // 2.0.0 [RVK-46] (DB 36) A job's payments besides 선금·잔금.
    @Query("SELECT * FROM job_payments ORDER BY epochDay, id")
    fun observeJobPayments(): Flow<List<JobPaymentEntity>>

    @Query("SELECT * FROM job_payments ORDER BY epochDay, id")
    suspend fun allJobPayments(): List<JobPaymentEntity>

    @Query("SELECT * FROM job_payments WHERE jobId = :jobId ORDER BY epochDay, id")
    suspend fun jobPaymentsOf(jobId: Long): List<JobPaymentEntity>

    @Insert
    suspend fun insertJobPayment(e: JobPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreJobPayment(e: JobPaymentEntity)

    @Query("DELETE FROM job_payments WHERE id = :id")
    suspend fun deleteJobPayment(id: Long)

    @Query("DELETE FROM job_payments WHERE jobId = :jobId")
    suspend fun deleteJobPaymentsOf(jobId: Long)

    @Query("DELETE FROM job_payments")
    suspend fun clearJobPayments()

    @Query("SELECT * FROM daily_sales ORDER BY epochDay")
    fun observeSales(): Flow<List<DailySalesEntity>>

    @Query("SELECT * FROM daily_sales")
    suspend fun allSales(): List<DailySalesEntity>

    @Upsert
    suspend fun upsertSales(e: DailySalesEntity)

    @Query("DELETE FROM daily_sales WHERE epochDay = :epochDay")
    suspend fun deleteSales(epochDay: Long)

    @Query("DELETE FROM money_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM jobs")
    suspend fun clearJobs()

    @Query("DELETE FROM daily_sales")
    suspend fun clearSales()

    @Query("SELECT * FROM money_entries WHERE id = :id")
    suspend fun entry(id: Long): MoneyEntryEntity?

    @Query("SELECT * FROM money_entries WHERE epochDay >= :fromDay")
    suspend fun entriesFrom(fromDay: Long): List<MoneyEntryEntity>

    @Query("UPDATE money_entries SET reviewed = 1 WHERE reviewed = 0")
    suspend fun markAllReviewed()

    @Query("SELECT * FROM fixed_costs ORDER BY day, id")
    fun observeFixed(): Flow<List<FixedCostEntity>>

    @Query("SELECT * FROM fixed_costs")
    suspend fun allFixed(): List<FixedCostEntity>

    @Insert
    suspend fun insertFixed(e: FixedCostEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFixed(e: FixedCostEntity)

    @Query("DELETE FROM fixed_costs WHERE id = :id")
    suspend fun deleteFixed(id: Long)

    @Query("DELETE FROM fixed_costs")
    suspend fun clearFixed()

    @Query("SELECT * FROM category_rules")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun allRules(): List<CategoryRuleEntity>

    @Upsert
    suspend fun upsertRule(e: CategoryRuleEntity)

    @Query("DELETE FROM category_rules")
    suspend fun clearRules()

    @Query("DELETE FROM category_rules WHERE merchant = :merchant")
    suspend fun deleteRule(merchant: String)

    // [LEDG-07] Several lines at once, for the list's 고르기 and its undo.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreEntries(list: List<MoneyEntryEntity>)

    @Query("DELETE FROM money_entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<Long>)

    // [LEDG-08] Accounts.
    @Query("SELECT * FROM money_accounts ORDER BY sortOrder, id")
    fun observeAccounts(): Flow<List<MoneyAccountEntity>>

    @Query("SELECT * FROM money_accounts")
    suspend fun allAccounts(): List<MoneyAccountEntity>

    @Insert
    suspend fun insertAccount(e: MoneyAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAccount(e: MoneyAccountEntity)

    @Query("DELETE FROM money_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM money_accounts")
    suspend fun clearAccounts()

    @Query("SELECT COUNT(*) FROM money_entries WHERE accountId = :id OR toAccountId = :id")
    suspend fun linesOfAccount(id: Long): Int

    /** [LEDG-08] Repeating lines paid from a removed account no longer point at it. */
    @Query("UPDATE fixed_costs SET accountId = NULL WHERE accountId = :id")
    suspend fun unlinkFixedFromAccount(id: Long)

    /** 2.0.0 [RVK-46] A repeating 이체 into a removed account goes nowhere set, as before it had a '어디로'. */
    @Query("UPDATE fixed_costs SET toAccountId = NULL WHERE toAccountId = :id")
    suspend fun unlinkFixedToAccount(id: Long)

    // 1.37 [LEDH-11] 봉투.
    @Query("SELECT * FROM envelopes ORDER BY sortOrder, id")
    fun observeEnvelopes(): Flow<List<EnvelopeEntity>>

    @Query("SELECT * FROM envelopes ORDER BY sortOrder, id")
    suspend fun allEnvelopes(): List<EnvelopeEntity>

    @Upsert
    suspend fun upsertEnvelope(e: EnvelopeEntity): Long

    @Query("DELETE FROM envelopes WHERE id = :id")
    suspend fun deleteEnvelope(id: Long)

    @Query("SELECT * FROM envelope_moves ORDER BY epochDay, id")
    fun observeEnvelopeMoves(): Flow<List<EnvelopeMoveEntity>>

    @Query("SELECT * FROM envelope_moves ORDER BY epochDay, id")
    suspend fun allEnvelopeMoves(): List<EnvelopeMoveEntity>

    @Insert
    suspend fun insertEnvelopeMove(e: EnvelopeMoveEntity): Long

    @Query("DELETE FROM envelope_moves WHERE id = :id")
    suspend fun deleteEnvelopeMove(id: Long)

    @Query("DELETE FROM envelope_moves WHERE envelopeId = :envelopeId")
    suspend fun deleteMovesOf(envelopeId: Long)

    @Query("DELETE FROM envelopes")
    suspend fun clearEnvelopes()

    @Query("DELETE FROM envelope_moves")
    suspend fun clearEnvelopeMoves()

    // [LEDG-06] Budgets.
    @Query("SELECT * FROM money_budgets")
    fun observeBudgets(): Flow<List<MoneyBudgetEntity>>

    @Query("SELECT * FROM money_budgets")
    suspend fun allBudgets(): List<MoneyBudgetEntity>

    @Upsert
    suspend fun upsertBudget(e: MoneyBudgetEntity)

    /** Every month's and every '이 달만' budget of [category]. */
    @Query("DELETE FROM money_budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    /** 2.0.0 [RVK-46] One budget: every month's ([month] 0) or one month's (yyyyMM). */
    @Query("DELETE FROM money_budgets WHERE category = :category AND month = :month")
    suspend fun deleteBudget(category: String, month: Int)

    @Query("DELETE FROM money_budgets")
    suspend fun clearBudgets()

    // [LEDG-14] The user's categories.
    @Query("SELECT * FROM money_categories ORDER BY kind, position")
    fun observeCategories(): Flow<List<MoneyCategoryEntity>>

    @Query("SELECT * FROM money_categories")
    suspend fun allCategories(): List<MoneyCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(list: List<MoneyCategoryEntity>)

    @Query("DELETE FROM money_categories WHERE kind = :kind")
    suspend fun clearCategories(kind: String)

    @Query("DELETE FROM money_categories")
    suspend fun clearAllCategories()

    /** [LEDG-14] A renamed category takes its lines, budget, learned places and repeating lines with it. */
    @Query("UPDATE money_entries SET category = :to WHERE kind = :kind AND category = :from")
    suspend fun renameInEntries(kind: String, from: String, to: String)

    @Query("UPDATE fixed_costs SET category = :to WHERE kind = :kind AND category = :from")
    suspend fun renameInFixed(kind: String, from: String, to: String)

    @Query("UPDATE category_rules SET category = :to WHERE category = :from")
    suspend fun renameInRules(from: String, to: String)

    @Query("SELECT * FROM money_entries WHERE parts LIKE '%' || :category || '%'")
    suspend fun entriesWithPart(category: String): List<MoneyEntryEntity>
}

fun MoneyEntryEntity.toModel() = MoneyEntry(
    id = id,
    date = LocalDate.ofEpochDay(epochDay),
    kind = runCatching { MoneyKind.valueOf(kind) }.getOrDefault(MoneyKind.EXPENSE),
    amount = amount,
    category = category,
    note = note,
    source = runCatching { MoneySource.valueOf(source) }.getOrDefault(MoneySource.MANUAL),
    merchant = merchant,
    time = minuteOfDay?.let { it.toLocalTime() },
    cardLast4 = cardLast4,
    reviewed = reviewed,
    accountId = accountId,
    toAccountId = toAccountId,
    memo = memo,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    parts = decodeParts(parts),
    installments = installments.coerceAtLeast(1),
    fixedId = fixedId,
    proof = proof,
    receipt = receipt,
    installmentRate = installmentRate.coerceIn(0.0, MoneyEntry.MAX_INSTALLMENT_RATE),
)

fun MoneyEntry.toEntity(createdAt: Long = Today.millis()) = MoneyEntryEntity(
    id = id, epochDay = date.toEpochDay(), kind = kind.name, amount = amount, category = category, note = note,
    source = source.name, createdAt = createdAt, merchant = merchant, minuteOfDay = time?.let { it.hour * 60 + it.minute },
    cardLast4 = cardLast4, reviewed = reviewed,
    accountId = accountId, toAccountId = toAccountId, memo = memo, tags = tags.joinToString(","),
    parts = encodeParts(parts), installments = installments.coerceAtLeast(1), fixedId = fixedId,
    proof = proof, receipt = receipt, installmentRate = installmentRate.coerceIn(0.0, MoneyEntry.MAX_INSTALLMENT_RATE),
)

/** [LEDG-11] Split parts as "amount<TAB>category" lines; categories never hold a tab ([MoneyCategories.clean]). */
fun encodeParts(parts: List<EntryPart>): String = parts.joinToString("\n") { "${it.amount}\t${it.category}" }

fun decodeParts(text: String): List<EntryPart> = if (text.isBlank()) emptyList() else text.lines().mapNotNull { line ->
    val tab = line.indexOf('\t')
    if (tab <= 0) return@mapNotNull null
    val amount = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
    val category = line.substring(tab + 1).trim()
    if (category.isEmpty()) null else EntryPart(category, amount)
}

fun FixedCostEntity.toModel() = FixedCost(
    id = id, name = name, amount = amount, day = day,
    kind = runCatching { MoneyKind.valueOf(kind) }.getOrDefault(MoneyKind.EXPENSE), category = category, remind = remind,
    repeat = runCatching { Repeat.valueOf(repeat) }.getOrDefault(Repeat.MONTH), monthOfYear = monthOfYear.coerceIn(1, 12),
    accountId = accountId, auto = auto,
    start = startEpochDay?.let(LocalDate::ofEpochDay), end = endEpochDay?.let(LocalDate::ofEpochDay),
    toAccountId = toAccountId?.takeIf { kind == MoneyKind.TRANSFER.name && it != accountId },
)

fun FixedCost.toEntity() = FixedCostEntity(
    id = id, name = name, amount = amount, day = day, kind = kind.name, category = category, remind = remind,
    repeat = repeat.name, monthOfYear = monthOfYear, accountId = accountId, auto = auto,
    startEpochDay = start?.toEpochDay(), endEpochDay = end?.toEpochDay(),
    toAccountId = toAccountId?.takeIf { kind == MoneyKind.TRANSFER && it != accountId },
)

fun MoneyAccountEntity.toModel() = MoneyAccount(
    id = id, name = name, type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.OTHER), last4 = last4,
    payDay = payDay, closeDay = closeDay, payFromId = payFromId, opening = opening, color = color, order = sortOrder,
    hidden = hidden, remind = remind, rate = rate, monthlyPay = monthlyPay,
    perfTiers = perfTiers.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }.distinct().sorted(),
    perfSkip = perfSkip.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
)

fun MoneyAccount.toEntity() = MoneyAccountEntity(
    id = id, name = name, type = type.name, last4 = last4, payDay = payDay, closeDay = closeDay, payFromId = payFromId,
    opening = opening, color = color, sortOrder = order, hidden = hidden, remind = remind, rate = rate, monthlyPay = monthlyPay,
    perfTiers = perfTiers.joinToString(","), perfSkip = perfSkip.joinToString(","),
)

fun MoneyBudgetEntity.toModel() = MoneyBudget(category, amount, MoneyBudget.monthOf(month))

fun MoneyBudget.toEntity() = MoneyBudgetEntity(category, amount, MoneyBudget.code(month))

fun MoneyCategoryEntity.toModel(): CategoryEdit? =
    runCatching { MoneyKind.valueOf(kind) }.getOrNull()?.let { CategoryEdit(it, name, position, hidden, icon, color) }

fun CategoryEdit.toEntity() = MoneyCategoryEntity(kind.name, name, position, hidden, icon, color)

fun JobEntity.toModel(payments: List<JobPaymentEntity> = emptyList()) = Job(
    id = id, date = LocalDate.ofEpochDay(epochDay), title = title, client = client, place = place,
    start = startMinute?.let { it.toLocalTime() }, end = endMinute?.let { it.toLocalTime() },
    amount = amount, deposit = deposit, depositPaid = depositPaid, balancePaid = balancePaid,
    remindMinutes = remindMinutes, note = note,
    endDate = endEpochDay?.let(LocalDate::ofEpochDay), costs = costs, withholding = withholding,
    status = com.bamnat.core.money.JobStatus.fromCode(status), units = units?.takeIf { it > 0 },
    due = dueEpochDay?.let(LocalDate::ofEpochDay),
    payments = payments.map { it.toModel() }.sortedWith(compareBy({ it.date }, { it.id })),
)

fun JobPaymentEntity.toModel() = com.bamnat.core.money.JobPayment(id, jobId, LocalDate.ofEpochDay(epochDay), amount, label, note)

fun com.bamnat.core.money.JobPayment.toEntity() =
    JobPaymentEntity(id, jobId, date.toEpochDay(), amount.coerceAtLeast(0), label.trim().take(com.bamnat.core.money.JobPayment.MAX_LABEL), note.trim())

fun Job.toEntity() = JobEntity(
    id = id, epochDay = date.toEpochDay(), title = title, client = client, place = place,
    startMinute = start?.let { it.hour * 60 + it.minute }, endMinute = end?.let { it.hour * 60 + it.minute },
    amount = amount, deposit = deposit, depositPaid = depositPaid, balancePaid = balancePaid,
    remindMinutes = remindMinutes, note = note,
    endEpochDay = endDate?.takeIf { it.isAfter(date) }?.toEpochDay(), costs = costs, withholding = withholding,
    status = status.code, units = units?.takeIf { it > 0 }, dueEpochDay = due?.toEpochDay(),
)

fun DailySalesEntity.toModel() = DailySales(
    LocalDate.ofEpochDay(epochDay), sales, costs, note,
    card = card, cash = cash, transfer = transfer, delivery = delivery, cashCounted = cashCounted, cardSettled = cardSettled,
    cashReceipt = cashReceipt.coerceAtLeast(0), costsProven = costsProven.coerceIn(0, costs.coerceAtLeast(0)),
)

fun DailySales.toEntity() = DailySalesEntity(
    date.toEpochDay(), sales, costs, note,
    card = card, cash = cash, transfer = transfer, delivery = delivery, cashCounted = cashCounted, cardSettled = cardSettled,
    cashReceipt = cashReceipt.coerceAtLeast(0), costsProven = costsProven.coerceIn(0, costs.coerceAtLeast(0)),
)

fun EnvelopeEntity.toModel() = com.bamnat.core.money.Envelope(
    id = id, name = name, target = target, due = dueEpochDay?.let(LocalDate::ofEpochDay), kind = com.bamnat.core.money.EnvelopeKind.fromCode(kind),
    sharePercent = sharePercent, emoji = emoji, order = sortOrder, closed = closed, created = createdEpochDay?.let(LocalDate::ofEpochDay),
)

fun com.bamnat.core.money.Envelope.toEntity() = EnvelopeEntity(
    id, name.take(com.bamnat.core.money.Envelope.MAX_NAME), target.coerceAtLeast(0), due?.toEpochDay(), kind.code,
    sharePercent.coerceIn(0, 100), emoji, order, closed, created?.toEpochDay(),
)

fun EnvelopeMoveEntity.toModel() = com.bamnat.core.money.EnvelopeMove(id, envelopeId, LocalDate.ofEpochDay(epochDay), amount, note, entryId)

fun com.bamnat.core.money.EnvelopeMove.toEntity() = EnvelopeMoveEntity(id, envelopeId, date.toEpochDay(), amount, note, entryId)
