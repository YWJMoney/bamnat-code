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
import com.bamnat.core.tax.IncomeKind
import com.bamnat.core.tax.PayObservation
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * [TAX-01][TAX-04] Version 21 (1.25 W): pay as it really came, one row per pay month and kind of income. A deposit
 * the ledger saw keeps its line's id, so the same deposit is one observation however often it is seen.
 */
@Entity(tableName = "pay_observations", indices = [Index("payMonth")])
data class PayObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy × 100 + month. */
    val payMonth: Int,
    val net: Long,
    val gross: Long? = null,
    val epochDay: Long,
    /** [PayObservation.Source.name]. */
    val source: String,
    /** [IncomeKind.name]. */
    val kind: String,
    val entryId: Long? = null,
    val hours: Double? = null,
    val days: Int? = null,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
)

fun PayObservationEntity.toModel(): PayObservation? {
    val month = runCatching { YearMonth.of(payMonth / 100, payMonth % 100) }.getOrNull() ?: return null
    return PayObservation(
        id = id, payMonth = month, net = net, gross = gross, date = LocalDate.ofEpochDay(epochDay),
        source = PayObservation.Source.entries.firstOrNull { it.name == source } ?: PayObservation.Source.TYPED,
        kind = IncomeKind.entries.firstOrNull { it.name == kind } ?: IncomeKind.SALARY,
        entryId = entryId, hours = hours, days = days, note = note, createdAt = createdAt,
    )
}

fun PayObservation.toEntity() = PayObservationEntity(
    id = id, payMonth = payMonth.year * 100 + payMonth.monthValue, net = net, gross = gross, epochDay = date.toEpochDay(),
    source = source.name, kind = kind.name, entryId = entryId, hours = hours, days = days, note = note.trim().take(200), createdAt = createdAt,
)

/** [TAX-06] Version 21: the user's tax answers (사업자 과세 유형, 업종, 경비율 …) as key and text, like invest_settings. */
@Entity(tableName = "tax_settings")
data class TaxSettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface TaxDao {
    @Query("SELECT * FROM pay_observations ORDER BY payMonth, id")
    fun observeObservations(): Flow<List<PayObservationEntity>>

    @Query("SELECT * FROM pay_observations ORDER BY payMonth, id")
    suspend fun observations(): List<PayObservationEntity>

    @Query("SELECT * FROM pay_observations WHERE entryId = :entryId LIMIT 1")
    suspend fun observationForEntry(entryId: Long): PayObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveObservation(row: PayObservationEntity): Long

    @Insert
    suspend fun insertObservations(rows: List<PayObservationEntity>)

    @Query("DELETE FROM pay_observations WHERE id = :id")
    suspend fun deleteObservation(id: Long)

    @Query("DELETE FROM pay_observations")
    suspend fun clearObservations()

    @Query("SELECT * FROM tax_settings")
    fun observeSettings(): Flow<List<TaxSettingEntity>>

    @Query("SELECT * FROM tax_settings")
    suspend fun settings(): List<TaxSettingEntity>

    @Upsert
    suspend fun upsertSettings(rows: List<TaxSettingEntity>)

    @Insert
    suspend fun restoreSettings(rows: List<TaxSettingEntity>)

    @Query("DELETE FROM tax_settings")
    suspend fun clearSettings()
}
