package com.bamnat.app.data

import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import androidx.room.withTransaction
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.CategoryRuleEntity
import com.bamnat.app.data.db.decodeParts
import com.bamnat.app.data.db.encodeParts
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.FixedCost
import com.bamnat.core.money.LedgerChecks
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyAccounts
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyBudget
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.money.PaymentParser
import com.bamnat.core.money.Receivable
import com.bamnat.core.money.ReceivablePayment
import com.bamnat.core.money.Receivables
import com.bamnat.core.money.Recurring
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/** 1.32 [LEDX-15][LEDX-18] How 결제 알림 reading is tuned to the user; all of it stays on the phone. */
data class CaptureTune(
    val myNames: List<String> = emptyList(),
    val abroadFee: Double = com.bamnat.core.money.LedgerSmarts.ABROAD_FEE,
    val rates: Map<String, Double> = emptyMap(),
)

/** A notification text the app kept because it could not write it down alone [LEDG-02]. */
data class Capture(val id: Long, val postedAt: Long, val app: String, val text: String, val reason: String)

/**
 * Ledger lines, booked jobs, shop sales, fixed costs and learned categories
 * [LEDG-01][WORK-02][WORK-03][LEDG-04][LEDG-05]. Everything stays on the phone.
 */
class MoneyRepository(
    private val db: AppDatabase,
    private val kept: CaptureStore = CaptureStore(null),
    private val clock: () -> Long = Today::millis,
    /** [TYPE-08] The calendar's kind of work, for its built-in categories. */
    private val style: Flow<com.bamnat.core.work.WorkStyle?> = kotlinx.coroutines.flow.flowOf(null),
    /** [COMN-04] 확인하고 적기: lines not yet confirmed stay out of the book's totals. */
    private val confirmFirst: Flow<java.time.LocalDate?> = kotlinx.coroutines.flow.flowOf(null),
    /** [LEDG-09] How far each self-writing line was caught up, so deleted lines stay deleted. */
    private val marks: RecurringMarks = RecurringMarks(null),
    /** 1.32 [LEDX-15][LEDX-18] The user's own names, 해외 수수료 and rates (결제 알림 › 더 정확하게 적기). */
    private val tune: () -> CaptureTune = { CaptureTune() },
    /** 1.32 [LEDX-21] What each day was on this calendar (주간, 야간, 쉬는 날), for the learner; read when it learns. */
    private val dayLabel: suspend () -> (LocalDate) -> String? = { { null } },
    /** 2.0.0 [RVK-13] This ledger's calendar, for what '자동으로 정리한 것' keeps. */
    private val calendarId: String = PersonalCalendar.MAIN,
    /** 2.0.0 [RVK-13] Where a payment left out or joined is kept to put back; null keeps nothing (tests). */
    private val tidy: () -> AutoTidy? = { null },
    /** 2.0.0 [RVK-14] Which app's text each line came from, so the same card's second payment is not taken for the first. */
    private val seen: () -> CaptureSeen? = { null },
) {
    private val dao = db.moneyDao()

    val entries: Flow<List<MoneyEntry>> = dao.observeEntries().map { list -> list.map { it.toModel() } }.flowOn(Dispatchers.Default)
    val jobs: Flow<List<Job>> = dao.observeJobs().map { list -> list.map { it.toModel() } }
    val sales: Flow<List<DailySales>> = dao.observeSales().map { list -> list.map { it.toModel() } }
    val fixed: Flow<List<FixedCost>> = dao.observeFixed().map { list -> list.map { it.toModel() } }
    val rules: Flow<Map<String, String>> = dao.observeRules().map { list -> list.associate { it.merchant to it.category } }
    val captures: Flow<List<Capture>> = kept.captures
    private val owedDao = db.receivableDao()
    /** [RECV-01] Money others owe the user, each with what came back for it. */
    val receivables: Flow<List<Receivable>> = combine(owedDao.observe(), owedDao.observePayments()) { list, paid ->
        val byId = paid.groupBy { it.receivableId }
        list.map { it.toModel(byId[it.id].orEmpty()) }
    }
    /** [LEDG-08] Cash, bank accounts and cards, in the user's order. */
    val accounts: Flow<List<MoneyAccount>> = dao.observeAccounts().map { list -> list.map { it.toModel() } }
    /** [LEDG-06] Monthly budgets. */
    val budgets: Flow<List<MoneyBudget>> = dao.observeBudgets().map { list -> list.map { it.toModel() } }
    /** [LEDG-14] Categories the user added, hid or moved. */
    val categories: Flow<List<CategoryEdit>> = dao.observeCategories().map { list -> list.mapNotNull { it.toModel() } }
    private val plans: Flow<Triple<List<MoneyAccount>, List<MoneyBudget>, List<CategoryEdit>>> =
        combine(accounts, budgets, categories) { a, b, c -> Triple(a, b, c) }
    /** [STAB-01] Every ledger row is turned into a line off the main thread. */
    val book: Flow<MoneyBook> = combine(
        combine(entries, jobs, sales, fixed, receivables) { e, j, s, f, r -> MoneyBook(e, j, s, f, r) }, plans,
    ) { b, (a, bu, c) -> b.copy(accounts = a, budgets = bu, categories = c) }
        .combine(style.distinctUntilChanged()) { b, st -> b.copy(style = st) }
        .combine(confirmFirst.distinctUntilChanged()) { b, from -> b.holdUnconfirmed(from) }.flowOn(Dispatchers.Default)

    /** [SHOP-04] The calendar's kind of work now, for the tax deadlines that go with it. */
    suspend fun currentStyle(): com.bamnat.core.work.WorkStyle? = style.firstOrNull()

    /** [COMN-04] From when 확인하고 적기 holds lines back now, for a book read outside [book]. */
    suspend fun holdFrom(): java.time.LocalDate? = confirmFirst.firstOrNull()

    /**
     * Inserts a new line or replaces the one with the same id; returns it with its id. When the user
     * moves a place to another category, that place keeps the new category from now on [LEDG-04].
     */
    suspend fun save(entry: MoneyEntry): MoneyEntry {
        saves++
        if (entry.id == 0L) return entry.copy(id = dao.insertEntry(entry.toEntity(clock())))
        val before = dao.entry(entry.id)?.toModel()
        if (before != null && entry.merchant.isNotBlank() && entry.kind == MoneyKind.EXPENSE && before.category != entry.category) {
            dao.upsertRule(CategoryRuleEntity(entry.merchant, entry.category))
        }
        // 2.0.0 [RVK-39] (검토 MONEY-42) A bank 출금 the app read as spending, told back to be the user's own money moving: that receiver
        // stays a move from now on (a transfer category in the rule keeps [PaymentParser.withdrawSpending] from calling it spending).
        if (before != null && entry.merchant.isNotBlank() && entry.kind == MoneyKind.TRANSFER && before.kind == MoneyKind.EXPENSE &&
            com.bamnat.core.money.LedgerSmarts.fromWithdraw(before)) {
            dao.upsertRule(CategoryRuleEntity(entry.merchant, entry.category))
        }
        return entry.also { dao.restoreEntry(it.toEntity(clock())) }
    }

    /**
     * [LEDG-18] Other spending lines from the same place as [entry] still in [from], the category it
     * just left, so one change can carry over to them when the user says so.
     */
    suspend fun samePlace(entry: MoneyEntry, from: String): List<MoneyEntry> {
        if (entry.merchant.isBlank() || entry.kind != MoneyKind.EXPENSE || from == entry.category) return emptyList()
        return dao.allEntries().map { it.toModel() }.filter {
            it.id != entry.id && it.kind == MoneyKind.EXPENSE && it.category == from && it.parts.isEmpty() &&
                PaymentParser.samePlace(it.merchant, entry.merchant)
        }
    }

    suspend fun delete(entry: MoneyEntry) = dao.deleteEntry(entry.id)

    /** 2.0.0 [RVH-51] PC-72 Lines written by hand from [fromMillis] until [untilMillis] (not read from a payment text), with when. */
    suspend fun writtenBetween(fromMillis: Long, untilMillis: Long): List<Pair<MoneyEntry, Long>> =
        dao.allEntries().filter { it.createdAt in fromMillis until untilMillis && it.source == MoneySource.MANUAL.name }.map { it.toModel() to it.createdAt }

    /** [LEDG-07] Several lines changed at once (고르기), saved as they are. */
    suspend fun saveAll(list: List<MoneyEntry>) {
        if (list.isNotEmpty()) dao.restoreEntries(list.map { it.toEntity(clock()) })
    }

    /** [LEDG-07] Several lines removed at once; [saveAll] with the same lines puts them back. */
    suspend fun deleteAll(list: List<MoneyEntry>) {
        if (list.isNotEmpty()) dao.deleteEntries(list.map { it.id })
    }

    suspend fun allAccounts(): List<MoneyAccount> = dao.allAccounts().map { it.toModel() }

    suspend fun allBudgets(): List<MoneyBudget> = dao.allBudgets().map { it.toModel() }

    /** 1.37 [LEDH-11][LEDH-13] 봉투 and the money in them, in the user's order. */
    val envelopes: Flow<Pair<List<com.bamnat.core.money.Envelope>, List<com.bamnat.core.money.EnvelopeMove>>> =
        combine(dao.observeEnvelopes(), dao.observeEnvelopeMoves()) { e, m -> e.map { it.toModel() } to m.map { it.toModel() } }.flowOn(Dispatchers.Default)

    suspend fun saveEnvelope(e: com.bamnat.core.money.Envelope): Long = dao.upsertEnvelope(e.toEntity()).let { if (it > 0) it else e.id }

    /** Removes an envelope and its moves; [restoreEnvelope] with what [envelopeWithMoves] gave puts them back. */
    suspend fun deleteEnvelope(id: Long) = db.withTransaction { dao.deleteMovesOf(id); dao.deleteEnvelope(id) }

    suspend fun envelopeWithMoves(id: Long) = dao.allEnvelopes().firstOrNull { it.id == id }?.toModel() to dao.allEnvelopeMoves().filter { it.envelopeId == id }.map { it.toModel() }

    suspend fun restoreEnvelope(e: com.bamnat.core.money.Envelope, moves: List<com.bamnat.core.money.EnvelopeMove>) = db.withTransaction {
        dao.upsertEnvelope(e.toEntity()); moves.forEach { dao.insertEnvelopeMove(it.toEntity()) }
    }

    suspend fun moveEnvelope(m: com.bamnat.core.money.EnvelopeMove): Long = dao.insertEnvelopeMove(m.toEntity())

    suspend fun deleteEnvelopeMove(id: Long) = dao.deleteEnvelopeMove(id)

    suspend fun allCategories(): List<CategoryEdit> = dao.allCategories().mapNotNull { it.toModel() }

    /** [LEDG-08] Adds an account at the end of the list, or saves the one with the same id. */
    suspend fun save(account: MoneyAccount): MoneyAccount {
        val clean = account.copy(name = account.name.trim().take(MoneyAccount.MAX_NAME), last4 = account.last4.filter { it.isDigit() }.takeLast(4))
        if (clean.id != 0L) return clean.also { dao.restoreAccount(it.toEntity()) }
        val order = (dao.allAccounts().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return clean.copy(order = order).let { it.copy(id = dao.insertAccount(it.toEntity())) }
    }

    /**
     * [LEDG-08] Removes an account. One with lines is put away instead, so its old lines keep their
     * name and balances stay right; returns true when it was really removed. Cards that paid from a
     * removed account, and repeating lines paid from it, no longer point at it.
     */
    suspend fun remove(account: MoneyAccount): Boolean = db.withTransaction {
        if (dao.linesOfAccount(account.id) > 0) {
            dao.restoreAccount(account.copy(hidden = true).toEntity())
            return@withTransaction false
        }
        dao.deleteAccount(account.id)
        dao.allAccounts().filter { it.payFromId == account.id }.forEach { dao.restoreAccount(it.copy(payFromId = null)) }
        dao.unlinkFixedFromAccount(account.id)
        true
    }

    /** [LEDG-08] Saves the user's order of accounts. */
    suspend fun reorder(accounts: List<MoneyAccount>) =
        accounts.forEachIndexed { i, a -> if (a.order != i) dao.restoreAccount(a.copy(order = i).toEntity()) }

    /** [LEDG-06] Sets a monthly budget; 0 or less removes it. */
    suspend fun setBudget(category: String, amount: Long) =
        if (amount <= 0) dao.deleteBudget(category) else dao.upsertBudget(MoneyBudget(category, amount).toEntity())

    /** [LEDG-14] Saves the user's categories for [kind] in their order; null names are dropped. */
    suspend fun saveCategories(kind: MoneyKind, list: List<CategoryEdit>) {
        dao.clearCategories(kind.name)
        dao.insertCategories(list.filter { it.kind == kind && it.name.isNotBlank() }.map { it.toEntity() })
    }

    /**
     * [LEDG-14] Renames a category everywhere it is used: lines (split parts too), repeating
     * lines, learned places and its budget, so nothing is left under the old name. Renamed onto a
     * category that has a budget, that budget stays.
     */
    suspend fun renameCategory(kind: MoneyKind, from: String, to: String) {
        renameCategories(kind, listOf(from to to))
    }

    /**
     * 2.0.0 [RVK-09] (검토 MONEY-02) Renames given together, in one transaction and through names nobody uses
     * ([MoneyCategories.renameSteps]), so a chain or a swap never merges two categories' lines. [RVK-08] The categories the sums
     * read by name are not renamed. Returns what was renamed (from → to), for 되돌리기.
     */
    suspend fun renameCategories(kind: MoneyKind, renames: List<Pair<String, String>>): List<Pair<String, String>> {
        val steps = MoneyCategories.renameSteps(renames)
        if (steps.isEmpty()) return emptyList()
        db.withTransaction { steps.forEach { (from, to) -> renameCategoryNow(kind, from, to) } }
        val half = steps.size / 2
        return steps.take(half).mapIndexed { i, (from, _) -> from to steps[half + i].second }
    }

    private suspend fun renameCategoryNow(kind: MoneyKind, from: String, name: String) {
        dao.renameInEntries(kind.name, from, name)
        dao.renameInFixed(kind.name, from, name)
        if (kind == MoneyKind.EXPENSE) {
            dao.renameInRules(from, name)
            val budgets = dao.allBudgets()
            budgets.firstOrNull { it.category == from }?.let { b ->
                dao.deleteBudget(from)
                if (budgets.none { it.category == name }) dao.upsertBudget(b.copy(category = name))
            }
            val split = dao.entriesWithPart(from).filter { it.kind == kind.name }.mapNotNull { e ->
                val parts = decodeParts(e.parts)
                if (parts.none { it.category == from }) null
                else e.copy(parts = encodeParts(parts.map { if (it.category == from) it.copy(category = name) else it }))
            }
            if (split.isNotEmpty()) dao.restoreEntries(split)
        }
        val mine = dao.allCategories().filter { it.kind == kind.name }
        if (mine.isNotEmpty()) {
            dao.clearCategories(kind.name)
            dao.insertCategories(mine.map { if (it.name == from) it.copy(name = name) else it }.distinctBy { it.name })
        }
    }

    private val recurringLock = Mutex()

    /**
     * [LEDG-09] Writes down the repeating lines set to write themselves that came due up to
     * [today], once each, and returns them. Run when the money tab opens and when an alarm fires.
     */
    suspend fun catchUpRecurring(today: LocalDate): List<MoneyEntry> = recurringLock.withLock {
        val costs = allFixed().filter { it.auto }
        if (costs.isEmpty()) return emptyList()
        val since = today.minusDays(Recurring.CATCH_UP_DAYS + 366)
        val lines = dao.entriesFrom(since.toEpochDay()).map { it.toModel() }
        val written = Recurring.due(costs, lines, today, marks.all()).map { save(it) }
        // 2.0.0 [RVK-41] (검토 MONEY-59) A mark past today (the clock went ahead and back) comes back to today too.
        marks.put(costs.filter { c -> marks.all()[c.id]?.let { it != today } != false }.associate { it.id to today })
        written
    }

    suspend fun markAllReviewed() = dao.markAllReviewed()

    /** [COMN-04] Lines from 확인 대기함 confirmed: they count from now on. */
    suspend fun confirm(list: List<MoneyEntry>) = saveAll(list.filter { !it.reviewed }.map { it.copy(reviewed = true) })

    suspend fun save(job: Job): Job =
        if (job.id == 0L) job.copy(id = dao.insertJob(job.toEntity())) else job.also { dao.restoreJob(it.toEntity()) }

    suspend fun job(id: Long): Job? = dao.job(id)?.toModel()

    suspend fun delete(job: Job) = dao.deleteJob(job.id)

    /** A day with nothing left in it is removed rather than kept as zeros. */
    suspend fun save(sales: DailySales) =
        if (sales.sales == 0L && sales.costs == 0L && sales.note.isBlank()) dao.deleteSales(sales.date.toEpochDay())
        else dao.upsertSales(sales.toEntity())

    suspend fun allJobs(): List<Job> = dao.allJobs().map { it.toModel() }

    suspend fun allFixed(): List<FixedCost> = dao.allFixed().map { it.toModel() }

    suspend fun allEntries(): List<MoneyEntry> = dao.allEntries().map { it.toModel() }

    suspend fun allReceivables(): List<Receivable> {
        val byId = owedDao.allPayments().groupBy { it.receivableId }
        return owedDao.all().map { it.toModel(byId[it.id].orEmpty()) }
    }

    /** [RECV-01] Saves what is owed; its payments are kept as they are. */
    suspend fun save(r: Receivable): Receivable =
        if (r.id == 0L) r.copy(id = owedDao.insert(r.toEntity(clock()))) else r.also { owedDao.restore(it.toEntity(clock())) }

    /** [RECV-01] Removes a receivable with its payments and the income lines they wrote. */
    suspend fun delete(r: Receivable) {
        owedDao.paymentsOf(r.id).forEach { p -> p.entryId?.let { dao.deleteEntry(it) } }
        owedDao.deletePaymentsOf(r.id)
        owedDao.delete(r.id)
    }

    /** [RECV-01] Puts a receivable back as it was, payments and income lines included (undo). */
    suspend fun restore(r: Receivable, lines: List<MoneyEntry>) {
        owedDao.restore(r.toEntity(clock()))
        lines.forEach { dao.restoreEntry(it.toEntity(clock())) }
        r.payments.forEach { owedDao.restorePayment(it.copy(receivableId = r.id).toEntity()) }
    }

    /** The income lines a receivable's payments wrote, to keep for an undo. */
    suspend fun linesOf(r: Receivable): List<MoneyEntry> = r.payments.mapNotNull { p -> p.entryId?.let { dao.entry(it)?.toModel() } }

    /**
     * [RECV-01] Money that came back: at most what is left, with an income line when the receivable
     * counts as income. Returns the payment as saved, or null when nothing was left to receive.
     */
    suspend fun receive(r: Receivable, amount: Long, date: LocalDate): ReceivablePayment? {
        val paid = Receivables.clampPayment(r, amount)
        if (paid <= 0) return null
        val entry = Receivables.incomeEntry(r, paid, date)?.let { save(it) }
        val p = ReceivablePayment(receivableId = r.id, date = date, amount = paid, entryId = entry?.id)
        return p.copy(id = owedDao.insertPayment(p.toEntity()))
    }

    /** [RECV-01] Takes a payment back, with its income line. */
    suspend fun delete(p: ReceivablePayment) {
        p.entryId?.let { dao.deleteEntry(it) }
        owedDao.deletePayment(p.id)
    }

    /**
     * 2.0.0 [RVK-42] (검토 MONEY-10) [delete], keeping what it takes: the returned step puts the payment and its income line back as
     * they were (되돌리기).
     */
    suspend fun takeBack(p: ReceivablePayment): suspend () -> Unit {
        val line = p.entryId?.let { dao.entry(it)?.toModel() }
        delete(p)
        return {
            line?.let { dao.restoreEntry(it.toEntity(clock())) }
            owedDao.restorePayment(p.toEntity())
        }
    }

    suspend fun clearSales(date: LocalDate) = dao.deleteSales(date.toEpochDay())

    suspend fun save(cost: FixedCost): FixedCost {
        val saved = if (cost.id == 0L) cost.copy(id = dao.insertFixed(cost.toEntity())) else cost.also { dao.restoreFixed(it.toEntity()) }
        // [LEDG-09] A new line (its id may be a deleted one's) catches up from its first day; an older one
        // set to write itself, from its first day or today, once.
        if (saved.auto && (cost.id == 0L || saved.id !in marks.all())) {
            marks.put(mapOf(saved.id to Recurring.startMark(saved, Instant.ofEpochMilli(clock()).atZone(Today.zone()).toLocalDate())))
        }
        return saved
    }

    suspend fun delete(cost: FixedCost) = dao.deleteFixed(cost.id)

    /** [LEDG-21] A rule the user wrote or changed on the rules page; [merchant] may be a "이 말이 들어가면" word. */
    suspend fun setRule(merchant: String, category: String) {
        if (merchant.isNotBlank()) dao.upsertRule(CategoryRuleEntity(merchant, category))
    }

    /** [LEDG-21] Forgets a rule; places it covered go back to the built-in guess. */
    suspend fun deleteRule(merchant: String) = dao.deleteRule(merchant)

    suspend fun rules(): Map<String, String> = dao.allRules().associate { it.merchant to it.category }

    /** Lines from the last month, enough to find a duplicate or the payment a cancellation undoes. */
    suspend fun recent(date: LocalDate): List<MoneyEntry> = dao.entriesFrom(date.minusDays(31).toEpochDay()).map { it.toModel() }

    enum class Outcome { SAVED, DUPLICATE, KEPT, IGNORED }

    /**
     * [LEDG-02] One payment notification. A clear one becomes a ledger line marked unseen; a second
     * copy of the same payment is dropped; one the parser cannot finish is kept for the user (30 days).
     * Texts that are not payments are never stored.
     */
    /**
     * [elsewhere] gives the same days' lines of the other calendars [CALS-04]: the card text can go to one
     * calendar and the pay-app push for the same payment to another, and it is still one payment.
     */
    suspend fun capture(
        text: String, app: String, postedAt: Long, fromMessenger: Boolean = false,
        elsewhere: suspend (LocalDate) -> List<MoneyEntry> = { emptyList() },
    ): Outcome = captureLock.withLock {
        // One at a time: the card text and the pay-app push for one payment often land together,
        // and both must not pass the duplicate check before either is saved.
        kept.purge(postedAt - KEEP_MILLIS)
        if (!PaymentParser.looksFinancial(text)) return Outcome.IGNORED
        // [LEDG-20] Ads, bills to come and limit notices carry amounts too; they are not kept either.
        if (PaymentParser.notice(text) != null) return Outcome.IGNORED
        val arrived = LocalDateTime.ofInstant(Instant.ofEpochMilli(postedAt), Today.zone())
        // 2.0.0 [RVK-39] (검토 MONEY-42) A bank 출금 to a name of the user's 반복 지출 is that spending.
        val spending = allFixed().filter { it.kind == MoneyKind.EXPENSE && it.name.isNotBlank() }.associate { it.name to it.category }
        val result = PaymentParser.parse(text, arrived, rules(), spending)
        val parsed = (result as? PaymentParser.Result.Ok)?.parsed
            // Without a time in the text, the time it arrived keeps two same-amount payments apart.
            ?.let { if (it.time == null) it.copy(time = arrived.toLocalTime().withSecond(0).withNano(0)) else it }
        // A chat message ("어제 5,000원 입금했어") is not a bank text: from a messenger only texts that
        // name a card or bank count, and nothing else from it is ever kept.
        if (fromMessenger && (parsed == null || (parsed.issuer == null && parsed.last4 == null))) return Outcome.IGNORED
        // E 1.32 [LEDX-08] Paid abroad without a won figure: written down at once at a rough rate with the usual fee (임시),
        // to be fixed from the card's bill, instead of waiting in the list of texts to finish.
        val tuned = tune()
        // 1.32 [LEDX-18] With the user's own rate and 해외 수수료 when set.
        val abroad = parsed?.takeIf { it.needsWon }?.foreign?.let { f -> com.bamnat.core.money.LedgerSmarts.abroad(f.currency, f.amount, tuned.rates, tuned.abroadFee) }
        if (parsed != null && abroad != null && (parsed.merchant.isNotBlank() || parsed.issuer != null)) {
            val day = parsed.date ?: arrived.toLocalDate()
            val e = PaymentParser.toEntry(parsed.copy(amount = abroad.won), arrived.toLocalDate(), MoneySource.NOTIFICATION) ?: return Outcome.IGNORED
            val line = e.copy(note = listOf(e.note, abroad.note).filter { it.isNotBlank() }.joinToString(" · "), reviewed = false,
                accountId = MoneyAccounts.match(e.cardLast4, allAccounts()))
            PaymentParser.findDuplicate(parsed.copy(amount = abroad.won), day, recent(day))?.takeIf { !otherPayment(it, app, text) }?.let {
                leftOut(line, WHY_SAME)
                return Outcome.DUPLICATE
            }
            // Saved first: a phone without the reader's memory still writes the line.
            val saved = save(line)
            seen()?.wrote(CaptureSeen.lineKey(saved), app, text)
            return Outcome.SAVED
        }
        if (parsed == null || !parsed.confident) {
            if (kept.has(text)) return Outcome.DUPLICATE
            val reason = when {
                parsed?.needsWon == true -> "해외 결제예요. 원화 금액을 적어 주세요"
                result is PaymentParser.Result.Fail -> result.reason
                else -> "어디에 쓴 돈인지 못 읽었어요"
            }
            if (result is PaymentParser.Result.Fail && result.reason.contains("거절")) return Outcome.IGNORED
            kept.add(postedAt, app, text, reason)
            return Outcome.KEPT
        }
        val day = parsed.date ?: arrived.toLocalDate()
        val recent = recent(day)
        // 2.0.0 [RVK-14] (검토 MONEY-43): a line of the same amount, card and time from this same app's other text is another
        // payment (코인노래방 1,000원 두 번), not a second copy. [RVK-13] One taken for a copy is kept to put back, not dropped.
        val copyOf = PaymentParser.findDuplicate(parsed, day, recent)?.takeIf { !otherPayment(it, app, text) }
            ?: PaymentParser.findDuplicate(parsed, day, elsewhere(day))?.takeIf { !otherPayment(it, app, text) }
        if (copyOf != null) {
            PaymentParser.toEntry(parsed, arrived.toLocalDate(), MoneySource.NOTIFICATION)?.let { leftOut(it.copy(reviewed = false), WHY_SAME) }
            return Outcome.DUPLICATE
        }
        // E 1.32 [LEDX-03] A refund finds its payment even when it is a part of it (부분취소) or a little off (abroad).
        val refundOf = if (parsed.type == PaymentParser.Type.CANCEL) parsed.amount?.let {
            com.bamnat.core.money.LedgerSmarts.refundTarget(it, parsed.merchant, parsed.last4, day, recent, foreign = parsed.foreign != null)
        } ?: PaymentParser.findCancelTarget(parsed, day, recent) else null
        // E 1.32 [LEDX-05] A place the dictionary does not know goes where the user's own lines say, when the phone is sure.
        val learned = if (parsed.category == "기타" && parsed.type == PaymentParser.Type.APPROVAL) {
            val probe = MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = parsed.amount ?: 0, category = "", merchant = parsed.merchant,
                time = parsed.time, cardLast4 = parsed.last4.orEmpty(), note = parsed.via?.let { "$it 결제" }.orEmpty())
            learner().guess(probe, 1).firstOrNull()?.takeIf { it.share >= com.bamnat.core.money.LedgerSmarts.Learner.SURE }?.category
        } else null
        val category = refundOf?.category ?: learned ?: parsed.category
        val accounts = allAccounts()
        val entry = PaymentParser.toEntry(parsed, arrived.toLocalDate(), MoneySource.NOTIFICATION, category)
            // [LEDG-08] A card the user added picks up its payments by the last four digits.
            ?.let { e -> e.copy(accountId = MoneyAccounts.match(e.cardLast4, accounts)) }
            // 1.32 [LEDX-16] With what earlier part refunds already gave back, so the note says what is left.
            ?.let { e -> if (refundOf != null) e.copy(note = listOf(e.note.split(" · ").filter { it != "결제 취소" }.joinToString(" · "),
                com.bamnat.core.money.LedgerSmarts.refundNote(parsed.amount ?: 0, refundOf, LedgerChecks.refundedBefore(refundOf, recent))).filter { it.isNotBlank() }.joinToString(" · ")) else e }
            // 1.32 [LEDX-16] A refund whose payment is not found waits as 환불 받은 돈 for the user to pick it.
            ?.let { e -> if (refundOf == null && parsed.type == PaymentParser.Type.CANCEL) e.copy(note = listOf(e.note, LedgerChecks.UNMATCHED_REFUND).filter { it.isNotBlank() }.joinToString(" · ")) else e }
            ?.let { e -> if (com.bamnat.core.money.LedgerSmarts.looksDcc(text)) e.copy(memo = com.bamnat.core.money.LedgerSmarts.DCC_NOTE) else e }
            // 1.32 [LEDX-20] A top-up goes onto the pay balance the user keeps as an account (페이·충전).
            ?.let { e -> if (parsed.charge) e.copy(toAccountId = payAccount(parsed.merchant, accounts)) else e }
            // 1.32 [LEDX-14] A 카드값 withdrawal says how it compares with what the card's lines add up to.
            ?.let { e -> LedgerChecks.billCheck(e, accounts, allEntries())?.let { b ->
                e.copy(toAccountId = e.toAccountId ?: b.card.id, category = "카드값",
                    note = listOf(e.note, LedgerChecks.billNote(b)).filter { it.isNotBlank() }.joinToString(" · "))
            } ?: e }
            ?: return Outcome.SAVED
        // E 1.32 [LEDX-02] The same payment told by the card and the bank (or a pay app and its card) is one line: the card's.
        val near = recent + elsewhere(day)
        com.bamnat.core.money.LedgerSmarts.crossDuplicate(entry, near)?.let { other ->
            val keep = com.bamnat.core.money.LedgerSmarts.keepOf(other, entry)
            val mine = recent.any { it.id == other.id }
            // 1.32 [LEDX-13] The line kept says the pay app the money went through (경로) and the card it was paid with.
            if (keep !== other && mine) save(LedgerChecks.withRoute(entry, other).copy(id = other.id, reviewed = other.reviewed, memo = other.memo, tags = other.tags))
            else if (mine) LedgerChecks.withRoute(other, entry).takeIf { it != other }?.let { save(it) }
            // [RVK-13] The text not kept as its own line, to put back if the two were two payments after all.
            leftOut(if (keep !== other && mine) other.copy(id = 0, reviewed = false) else entry.copy(reviewed = false), WHY_TWO_SENDERS)
            return Outcome.DUPLICATE
        }
        // E 1.32 [LEDX-01] A deposit and a withdrawal of the same amount between two of the user's own accounts are one move.
        // [RVK-12] (MONEY-60) Not when both name someone else; [RVK-13] and a join can be taken apart again from '자동으로 정리한 것'.
        com.bamnat.core.money.LedgerSmarts.ownTransfer(entry, recent, tuned.myNames)?.let { other ->
            val joined = com.bamnat.core.money.LedgerSmarts.joinTransfer(other, entry)
            val saved = if (joined.id == 0L) { dao.deleteEntry(other.id); save(joined) } else save(joined)
            joinedKept(saved, other, entry)
            return Outcome.SAVED
        }
        // 1.32 [LEDX-15] Money the user sent themself from another bank: a move into this account, not 수입.
        if (LedgerChecks.fromMyself(entry, tuned.myNames)) { save(LedgerChecks.asOwnMove(entry)); return Outcome.SAVED }
        val saved = save(entry)
        seen()?.wrote(CaptureSeen.lineKey(saved), app, text)
        return Outcome.SAVED
    }

    /** [RVK-14] True when [line] came from [app]'s other text: then [text] is a payment of its own. */
    private fun otherPayment(line: MoneyEntry, app: String, text: String): Boolean =
        seen()?.sameAppOtherText(CaptureSeen.lineKey(line), app, text) == true

    /** [RVK-13] A payment the app did not write as a line of its own, kept 30 days with why, to put back with one tap. */
    private suspend fun leftOut(e: MoneyEntry, why: String) {
        val box = tidy() ?: return
        runCatching {
            box.add(AutoTidy.AREA_MONEY, calendarId, "${e.title} ${wonText(e.amount)}", why, KIND_LEFT_OUT,
                org.json.JSONObject().put("calendar", calendarId).put("entry", MoneyEntryJson.encode(e.copy(id = 0))).toString())
        }
    }

    /** [RVK-13] Two lines joined as one move between the user's accounts: how to take them apart again. */
    private suspend fun joinedKept(joined: MoneyEntry, before: MoneyEntry, added: MoneyEntry) {
        val box = tidy() ?: return
        runCatching {
            box.add(AutoTidy.AREA_MONEY, calendarId, "${wonText(joined.amount)} 내 계좌끼리 이체", WHY_JOINED, KIND_JOINED,
                org.json.JSONObject().put("calendar", calendarId).put("delete", org.json.JSONArray(listOf(joined.id)))
                    .put("put", org.json.JSONArray(listOf(MoneyEntryJson.encode(before))))
                    .put("add", org.json.JSONArray(listOf(MoneyEntryJson.encode(added.copy(id = 0, reviewed = false))))).toString())
        }
    }

    /** [RVK-13] Puts back what [leftOut] kept: a line of its own, marked new. */
    suspend fun putBackLeftOut(payload: String): Boolean {
        val o = org.json.JSONObject(payload)
        save(MoneyEntryJson.decode(o.getJSONObject("entry")).copy(id = 0, reviewed = false))
        return true
    }

    /** [RVK-13] Takes apart what [joinedKept] joined: the joined line goes, the two lines come back as they were. */
    suspend fun putBackJoined(payload: String): Boolean {
        val o = org.json.JSONObject(payload)
        db.withTransaction {
            val gone = o.optJSONArray("delete")
            dao.deleteEntries((0 until (gone?.length() ?: 0)).map { gone!!.getLong(it) })
            val put = o.optJSONArray("put")
            val back = (0 until (put?.length() ?: 0)).map { MoneyEntryJson.decode(put!!.getJSONObject(it)) }
            if (back.isNotEmpty()) dao.restoreEntries(back.map { it.toEntity(clock()) })
        }
        val add = o.optJSONArray("add")
        (0 until (add?.length() ?: 0)).forEach { save(MoneyEntryJson.decode(add!!.getJSONObject(it)).copy(id = 0)) }
        return true
    }

    private fun wonText(amount: Long): String = "%,d원".format(kotlin.math.abs(amount))

    /** 1.32 [LEDX-20] The 페이·충전 account a top-up at [name] (카카오페이, 티머니) goes on, when the user keeps one. */
    private fun payAccount(name: String, accounts: List<MoneyAccount>): Long? {
        val pays = accounts.filter { it.type == com.bamnat.core.money.AccountType.PAY && !it.hidden }
        val key = name.replace(" ", "")
        return pays.firstOrNull { a -> a.name.replace(" ", "").let { n -> n.isNotEmpty() && key.isNotEmpty() && (n.contains(key) || key.contains(n)) } }?.id
            ?: pays.singleOrNull()?.id
    }

    /** E 1.32 [LEDX-05] Learned from this calendar's lines, again after [LEARN_EVERY] saves. */
    @Volatile private var learned: Pair<Int, com.bamnat.core.money.LedgerSmarts.Learner>? = null
    private var saves = 0

    suspend fun learner(): com.bamnat.core.money.LedgerSmarts.Learner {
        learned?.takeIf { saves - it.first < LEARN_EVERY }?.let { return it.second }
        // 1.32 [LEDX-21] With what each day was on the calendar; a calendar that cannot be read teaches without it.
        val label = runCatching { dayLabel() }.getOrElse { { _: LocalDate -> null } }
        return com.bamnat.core.money.LedgerSmarts.Learner.of(allEntries(), label).also { learned = saves to it }
    }

    suspend fun dismissCapture(id: Long) = kept.delete(id)

    suspend fun clearCaptures() = kept.clear()

    private val captureLock = Mutex()

    /** Clears kept texts past their 30 days even when no new notification comes. */
    suspend fun purgeCaptures(now: Long = Today.millis()) = kept.purge(now - KEEP_MILLIS)

    companion object {
        const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val LEARN_EVERY = 20

        /** [RVK-13] The ways back '자동으로 정리한 것' uses for the ledger. */
        const val KIND_LEFT_OUT = "money.left_out"
        const val KIND_JOINED = "money.joined"
        const val WHY_SAME = "같은 금액·카드·시각의 결제가 이미 있어 같은 결제로 보고 한 번만 적었어요"
        const val WHY_TWO_SENDERS = "카드와 은행(또는 페이)이 같은 결제를 함께 알려 와 한 줄로 적었어요"
        const val WHY_JOINED = "같은 금액이 내 두 계좌에서 나가고 들어와 이체 한 줄로 합쳤어요"
    }
}
