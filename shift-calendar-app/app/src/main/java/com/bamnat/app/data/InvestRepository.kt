package com.bamnat.app.data

import androidx.room.withTransaction
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.InvestSettingEntity
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.core.invest.AccountType
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestAlarm
import com.bamnat.core.invest.InvestBook
import com.bamnat.core.invest.InvestEvent
import com.bamnat.core.invest.InvestSettings
import com.bamnat.core.invest.JournalEntry
import com.bamnat.core.invest.Quote
import com.bamnat.core.invest.ReturnMethod
import com.bamnat.core.invest.Snapshot
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeAlerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.WeakHashMap
import com.bamnat.core.time.Today

/**
 * [INV-01..INV-21] One investor calendar's records, in that calendar's own database: the main
 * calendar's (bamnat.db) or an added one's (calendar_<id>.db). Everything stays on the phone.
 */
class InvestRepository(private val db: AppDatabase, private val clock: () -> Long = Today::millis) {
    private val dao = db.investDao()

    private val records = combine(
        dao.observeAccounts(), dao.observeTrades(), dao.observeCash(), dao.observeQuotes(), dao.observeJournal(),
    ) { a, t, c, q, j ->
        InvestBook(a.map { it.toModel() }, t.map { it.toModel() }, c.map { it.toModel() }, q.map { it.toModel() }) to j
    }

    /** [STAB-01] Rows become the book off the main thread. */
    val book: Flow<InvestBook> = combine(
        records, dao.observeEvents(), dao.observeAlarms(), dao.observeSnapshots(), dao.observeSettings(),
    ) { (b, j), e, al, s, st ->
        val rows = st.associate { it.key to it.value }
        val settings = SettingsCodec.decode(rows)
        // [INV-33] Each account's market fees sit beside the settings.
        b.copy(accounts = b.accounts.map { a -> rows[InvestAccount.feesKey(a.id)]?.let { a.copy(marketFees = InvestAccount.decodeFees(it)) } ?: a },
            journal = j.map { it.toModel() }, events = e.map { it.toModel() }, alarms = al.map { it.toModel() },
            snapshots = s.map { it.toModel() }, settings = settings)
    }.flowOn(Dispatchers.Default)

    val alarms: Flow<List<InvestAlarm>> = dao.observeAlarms().map { l -> l.map { it.toModel() } }
    val events: Flow<List<InvestEvent>> = dao.observeEvents().map { l -> l.map { it.toModel() } }

    suspend fun now(): InvestBook = book.first()

    /** [INV-02] The account a first record goes into when the user has made none: "일반 계좌". */
    suspend fun ensureAccount(): Long = db.withTransaction {
        dao.allAccounts().firstOrNull { !it.hidden }?.id ?: dao.upsertAccount(InvestAccount(name = AccountType.GENERAL.label).toEntity())
    }

    suspend fun saveAccount(a: InvestAccount): Long = db.withTransaction {
        val id = dao.upsertAccount(a.toEntity()).let { if (a.id != 0L) a.id else it }
        // [INV-33] Its market fees, blank when none.
        dao.upsertSetting(InvestSettingEntity(InvestAccount.feesKey(id), InvestAccount.encodeFees(a.marketFees)))
        id
    }

    /** An account with records is hidden, so its history stays in the totals; an empty one goes. */
    suspend fun deleteAccount(id: Long) = db.withTransaction {
        val used = dao.allTrades().any { it.accountId == id } || dao.allCash().any { it.accountId == id }
        if (used) dao.allAccounts().firstOrNull { it.id == id }?.let { dao.upsertAccount(it.copy(hidden = true)) } else dao.deleteAccount(id)
    }

    suspend fun saveTrade(t: Trade): Long {
        val id = dao.upsertTrade(t.toEntity(clock())).let { if (t.id != 0L) t.id else it }
        setLastAccount(t.accountId)
        return id
    }

    suspend fun deleteTrade(id: Long) = dao.deleteTrade(id)

    suspend fun saveCash(c: CashFlow): Long {
        val id = dao.upsertCash(c.toEntity(clock())).let { if (c.id != 0L) c.id else it }
        setLastAccount(c.accountId)
        return id
    }

    suspend fun deleteCash(id: Long) = dao.deleteCash(id)

    /** [INV-04] Several prices typed at once; a blank one removes that holding's price. */
    suspend fun saveQuotes(quotes: List<Quote>, removed: List<String> = emptyList()) = db.withTransaction {
        quotes.forEach { dao.upsertQuote(it.toEntity()) }
        removed.forEach { dao.deleteQuote(it) }
    }

    suspend fun saveJournal(j: JournalEntry) = if (j.isEmpty) dao.deleteJournal(j.date.toEpochDay()) else dao.upsertJournal(j.toEntity(clock()))

    suspend fun saveEvent(e: InvestEvent): Long = dao.upsertEvent(e.toEntity()).let { if (e.id != 0L) e.id else it }
    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    suspend fun saveAlarm(a: InvestAlarm): Long = dao.upsertAlarm(a.toEntity(clock())).let { if (a.id != 0L) a.id else it }
    suspend fun deleteAlarm(id: Long) = dao.deleteAlarm(id)

    /** [INV-21] Keeps today's value, replacing an earlier one from the same day. */
    suspend fun saveSnapshot(s: Snapshot) = dao.upsertSnapshot(s.toEntity())

    suspend fun saveSettings(change: (InvestSettings) -> InvestSettings) = db.withTransaction {
        val before = SettingsCodec.decode(dao.allSettings().associate { it.key to it.value })
        val after = change(before)
        SettingsCodec.encode(after).forEach { (k, v) -> dao.upsertSetting(InvestSettingEntity(k, v)) }
    }

    // [INV-32] Trades found in notifications, waiting for 확인 (결정 D37), in the settings table (결정 D40).
    val pendingAlerts: Flow<List<TradeAlerts.Pending>> =
        dao.observeSettings().map { l -> TradeAlerts.decode(l.firstOrNull { it.key == TradeAlerts.KEY }?.value) }
    val alertAuto: Flow<Boolean> = dao.observeSettings().map { l -> l.any { it.key == TradeAlerts.AUTO_KEY && it.value == "true" } }

    suspend fun alertAutoNow(): Boolean = dao.allSettings().any { it.key == TradeAlerts.AUTO_KEY && it.value == "true" }
    suspend fun setAlertAuto(on: Boolean) = dao.upsertSetting(InvestSettingEntity(TradeAlerts.AUTO_KEY, on.toString()))

    /** False when the same fill is waiting already. */
    suspend fun addAlert(p: TradeAlerts.Pending): Boolean = db.withTransaction {
        val next = TradeAlerts.add(pendingNow(), p) ?: return@withTransaction false
        dao.upsertSetting(InvestSettingEntity(TradeAlerts.KEY, TradeAlerts.encode(next)))
        true
    }

    suspend fun dropAlert(at: Long) = db.withTransaction {
        dao.upsertSetting(InvestSettingEntity(TradeAlerts.KEY, TradeAlerts.encode(pendingNow().filter { it.at != at })))
    }

    /** [INV-40] 409 C: prices read from a 체결 내역 capture go on their waiting lines, which still wait for 확인 (결정 D37); fills with no line wait as new ones. */
    suspend fun fillAlerts(matches: List<com.bamnat.core.invest.FillMatcher.Match>, extra: List<TradeAlerts.Pending>) = db.withTransaction {
        var list = pendingNow()
        matches.forEach { m -> list = TradeAlerts.fill(list, m.alert.at, m.price, m.amount) }
        extra.forEach { p -> TradeAlerts.add(list, p)?.let { list = it } }
        dao.upsertSetting(InvestSettingEntity(TradeAlerts.KEY, TradeAlerts.encode(list)))
    }

    suspend fun alertsNow(): List<TradeAlerts.Pending> = pendingNow()

    suspend fun restoreAlerts(list: List<TradeAlerts.Pending>) = dao.upsertSetting(InvestSettingEntity(TradeAlerts.KEY, TradeAlerts.encode(list)))

    /** [INV-40] The capture guide has been shown once. */
    val fillGuideSeen: Flow<Boolean> = dao.observeSettings().map { l -> l.any { it.key == FILL_GUIDE_KEY && it.value == "true" } }
    suspend fun setFillGuideSeen() = dao.upsertSetting(InvestSettingEntity(FILL_GUIDE_KEY, "true"))

    /**
     * [INV-44] 1.27 AK: 캡처로 채우기를 묻지 않고 바로 넣기 (this investor calendar's own setting, in its invest_settings,
     * so a backup carries it with [InvestBackup]).
     * [INVA-02] 1.29 AR (사용자 2026-10-27 "캡쳐로 놓기하면 자동으로 인식해서 적용되게"): on unless the user chose
     * '넣기 전에 묻기' ("false" stored); before 1.29 it was off until the user turned it on, which is why captures kept asking.
     */
    val fillTrust: Flow<Boolean> = dao.observeSettings().map { l -> trusted(l) }
    suspend fun fillTrustNow(): Boolean = trusted(dao.allSettings())
    suspend fun setFillTrust(on: Boolean) = dao.upsertSetting(InvestSettingEntity(FILL_TRUST_KEY, on.toString()))
    private fun trusted(rows: List<InvestSettingEntity>) = rows.none { it.key == FILL_TRUST_KEY && it.value == "false" }

    /** [INVA-02] The first capture put in without a question says once where to have it ask instead. */
    suspend fun fillAutoToldNow(): Boolean = dao.allSettings().any { it.key == FILL_AUTO_TOLD_KEY && it.value == "true" }
    suspend fun setFillAutoTold() = dao.upsertSetting(InvestSettingEntity(FILL_AUTO_TOLD_KEY, "true"))

    /** [INV-43] What a capture put in and changed, to take back with 되돌리기. */
    data class CaptureApplied(val written: List<Trade>, val fixedBefore: List<Trade>, val alertsBefore: List<TradeAlerts.Pending>)

    /**
     * [INV-43] 1.27 AK (결정 AK-D3): the rows checked on the capture sheet are written as trades at once (fees suggested as
     * for a found trade), their waiting alerts taken off the list; [fixes] replace written trades (quantity, price, minute).
     */
    suspend fun applyCapture(lines: List<TradeAlerts.Pending>, fixes: List<Trade>): CaptureApplied {
        val book = now()
        val alertsBefore = pendingNow()
        val account = book.settings.lastAccountId?.takeIf { book.account(it)?.hidden == false } ?: ensureAccount()
        val written = lines.map { p ->
            val costs = com.bamnat.core.invest.InvestFees.suggest(p.market, p.side, p.quantity, p.price, book.account(account), book.settings)
            val t = p.toTrade(account, book.settings.usdKrw).copy(fee = costs.fee, tax = costs.tax)
            t.copy(id = saveTrade(t))
        }
        val before = fixes.mapNotNull { f -> book.trades.firstOrNull { it.id == f.id }?.also { saveTrade(f) } }
        val taken = lines.map { it.at }.toSet()
        if (alertsBefore.any { it.at in taken }) restoreAlerts(alertsBefore.filter { it.at !in taken })
        return CaptureApplied(written, before, alertsBefore)
    }

    /** [INV-43] 되돌리기: the written trades go, the changed ones come back, the waiting list is as it was. */
    suspend fun undoCapture(a: CaptureApplied) {
        a.written.forEach { deleteTrade(it.id) }
        a.fixedBefore.forEach { saveTrade(it) }
        restoreAlerts(a.alertsBefore)
    }

    private suspend fun pendingNow() = TradeAlerts.decode(dao.allSettings().firstOrNull { it.key == TradeAlerts.KEY }?.value)

    /** [INV-32] A waiting alert written as a trade into the last used open account, fees suggested, and taken off the list. */
    suspend fun confirmAlert(p: TradeAlerts.Pending): Trade {
        val book = now()
        val account = book.settings.lastAccountId?.takeIf { book.account(it)?.hidden == false } ?: ensureAccount()
        val costs = com.bamnat.core.invest.InvestFees.suggest(p.market, p.side, p.quantity, p.price, book.account(account), book.settings)
        val t = p.toTrade(account, book.settings.usdKrw).copy(fee = costs.fee, tax = costs.tax)
        val id = saveTrade(t)
        dropAlert(p.at)
        return t.copy(id = id)
    }

    private suspend fun setLastAccount(id: Long) {
        dao.upsertSetting(InvestSettingEntity(SettingsCodec.LAST_ACCOUNT, id.toString()))
    }

    /** [INV-11] Settings as key and text, so a key this version does not know is simply kept aside. */
    object SettingsCodec {
        const val LAST_ACCOUNT = "lastAccount"

        fun encode(s: InvestSettings): Map<String, String> = mapOf(
            "goalAmount" to (s.goalAmount?.toString() ?: ""),
            "goalReturn" to (s.goalReturnPercent?.toString() ?: ""),
            "goalDate" to (s.goalDate?.toString() ?: ""),
            "monthlyAdd" to (s.monthlyAdd?.toString() ?: ""),
            // One rule per line; a rule never holds a line break.
            "principles" to s.principles.joinToString("\n") { it.replace('\n', ' ') },
            "usdKrw" to s.usdKrw.toString(),
            "isaLimit" to s.isaLimit.toString(),
            "returnMethod" to s.returnMethod.name,
            "hideAmounts" to s.hideAmounts.toString(),
            // [INV-34] Absent means on.
            "monthSummary" to s.monthSummary.toString(),
            LAST_ACCOUNT to (s.lastAccountId?.toString() ?: ""),
            // [INV-38] 기본 수수료, kept so every trade uses it.
            "buyFee" to s.buyFeeRate.toString(),
            "sellFee" to s.sellFeeRate.toString(),
        )

        fun decode(m: Map<String, String>): InvestSettings {
            val d = InvestSettings()
            return InvestSettings(
                goalAmount = m["goalAmount"]?.toLongOrNull()?.takeIf { it > 0 },
                goalReturnPercent = m["goalReturn"]?.toDoubleOrNull()?.takeIf { it > 0 },
                goalDate = m["goalDate"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                monthlyAdd = m["monthlyAdd"]?.toLongOrNull()?.takeIf { it > 0 },
                principles = m["principles"]?.let { p -> p.split('\n').map { it.trim() }.filter { it.isNotEmpty() } } ?: d.principles,
                usdKrw = m["usdKrw"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: d.usdKrw,
                isaLimit = m["isaLimit"]?.toLongOrNull()?.takeIf { it >= 0 } ?: d.isaLimit,
                returnMethod = ReturnMethod.entries.firstOrNull { it.name == m["returnMethod"] } ?: d.returnMethod,
                hideAmounts = m["hideAmounts"] == "true",
                monthSummary = m["monthSummary"] != "false",
                lastAccountId = m[LAST_ACCOUNT]?.toLongOrNull(),
                buyFeeRate = m["buyFee"]?.toDoubleOrNull()?.takeIf { it in 0.0..5.0 } ?: d.buyFeeRate,
                sellFeeRate = m["sellFee"]?.toDoubleOrNull()?.takeIf { it in 0.0..5.0 } ?: d.sellFeeRate,
            )
        }
    }

    companion object {
        /** [INV-40] The 체결 내역 capture guide was shown once. */
        const val FILL_GUIDE_KEY = "fillGuide"
        /** [INV-44] 1.27 AK: "true" puts a capture's sure new rows in without asking. */
        const val FILL_TRUST_KEY = "fillTrust"
        /** [INVA-02] 1.29 AR: "true" once the first quiet capture said where to turn asking back on. */
        const val FILL_AUTO_TOLD_KEY = "fillAutoTold"
        private val opened = WeakHashMap<AppDatabase, InvestRepository>()

        /** One repository per open database, so every screen of a calendar reads the same flows. */
        @Synchronized
        fun of(db: AppDatabase): InvestRepository = opened.getOrPut(db) { InvestRepository(db) }
    }
}
