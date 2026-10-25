package com.bamnat.app.data

import androidx.room.withTransaction
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.TaxSettingEntity
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxSettings
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.util.WeakHashMap

/**
 * [TAX-01][TAX-04][TAX-06] One calendar's 월급 관측 and tax answers, in that calendar's own database (version 21).
 * Everything stays on the phone.
 */
class TaxRepository(private val db: AppDatabase, private val clock: () -> Long = Today::millis) {
    private val dao = db.taxDao()

    val observations: Flow<List<PayObservation>> = dao.observeObservations().map { l -> l.mapNotNull { it.toModel() } }
    val settings: Flow<TaxSettings> = dao.observeSettings().map { l -> TaxSettings.decode(l.associate { it.key to it.value }) }

    suspend fun allObservations(): List<PayObservation> = dao.observations().mapNotNull { it.toModel() }
    suspend fun currentSettings(): TaxSettings = settings.first()

    /** [TAX-04] A typed or read month; replaces that month's earlier typed one of the same kind. */
    suspend fun save(o: PayObservation): Long = db.withTransaction {
        val same = dao.observations().mapNotNull { it.toModel() }
            .firstOrNull { it.id != o.id && it.payMonth == o.payMonth && it.kind == o.kind && it.source != PayObservation.Source.DEPOSIT && o.source != PayObservation.Source.DEPOSIT }
        val id = same?.id ?: o.id
        dao.saveObservation(o.copy(id = id, createdAt = if (o.createdAt == 0L) clock() else o.createdAt).toEntity())
    }

    suspend fun delete(id: Long) = dao.deleteObservation(id)

    /** [TAX-01] The deposit in [entry] as [payMonth]'s pay: one observation per ledger line, updated when the line changes. */
    suspend fun observeDeposit(entry: MoneyEntry, payMonth: YearMonth): PayObservation {
        val before = dao.observationForEntry(entry.id)?.toModel()
        val o = PayObservation(
            id = before?.id ?: 0, payMonth = payMonth, net = entry.shareOf(MoneyCategories.SALARY).takeIf { it > 0 } ?: entry.amount,
            gross = before?.gross, date = entry.date, source = PayObservation.Source.DEPOSIT, entryId = entry.id,
            createdAt = before?.createdAt ?: clock(),
        )
        return o.copy(id = dao.saveObservation(o.toEntity()))
    }

    suspend fun saveSettings(change: (TaxSettings) -> TaxSettings) = db.withTransaction {
        val next = change(currentSettings())
        dao.upsertSettings(next.encode().map { (k, v) -> TaxSettingEntity(k, v) })
    }

    companion object {
        private val opened = WeakHashMap<AppDatabase, TaxRepository>()

        /** One repository per open database, so every screen of a calendar reads the same flows. */
        @Synchronized
        fun of(db: AppDatabase): TaxRepository = opened.getOrPut(db) { TaxRepository(db) }
    }
}
