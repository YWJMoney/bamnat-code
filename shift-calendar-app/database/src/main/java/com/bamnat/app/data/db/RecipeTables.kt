package com.bamnat.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * [RCP-25] A recipe put on this phone (1.34 나눔 판), version 24: what it was, which pieces went on, the user's own
 * values from just before ([before], a JSON snapshot per piece) so 되돌리기 can put them back within 7 days.
 * Records (근무 기록, 가계부, 메모) are never in a snapshot: a recipe does not touch them.
 */
@Entity(tableName = "applied_recipes", indices = [Index("code")])
data class AppliedRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The 8-letter code, or "" for a recipe read from a file or one the user made and tried on themselves. */
    val code: String,
    /** The version number on the plaza (0 when it has none) and the SHA-256 of the body that went on. */
    val version: Int,
    val hash: String,
    val title: String,
    /** The calendar it went on ([com.bamnat.app.data.PersonalCalendar] id). */
    val calendarId: String,
    /** The written recipe (canonical JSON) and the values the user filled in for its 입력 칸, as JSON. */
    val body: String,
    val inputs: String,
    /** The piece keys that went on, comma separated, and the snapshot of what each replaced. */
    val pieces: String,
    val before: String,
    val appliedAt: Long,
    /** When 되돌리기 was used; null while the recipe is on. */
    val undoneAt: Long?,
    /** When the user took it off another way (a later recipe on the same pieces, or 지우기); null while on. */
    val replacedAt: Long?,
    /** The 7-day mark was sent to the plaza (적용 뒤 7일 유지), so it is sent once. */
    val keptSent: Boolean,
)

/** [RCP-17] A recipe being made, kept after every step so nothing is lost when the sheet closes; version 24. */
@Entity(tableName = "recipe_drafts")
data class RecipeDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The recipe as made so far, in the recipe format (it may not pass the checks yet). */
    val body: String,
    /** The step the maker was on, 1..5. */
    val step: Int,
    /** The code of the user's own recipe this becomes a new version of; null for a new recipe. */
    val code: String?,
    /** 광장 (public), 링크로만 (link) or 나만 (private). */
    val scope: String,
    val updatedAt: Long,
)

@Dao
interface RecipeDao {
    @Query("SELECT * FROM applied_recipes ORDER BY appliedAt DESC, id DESC")
    fun observeApplied(): Flow<List<AppliedRecipeEntity>>

    @Query("SELECT * FROM applied_recipes ORDER BY appliedAt DESC, id DESC")
    suspend fun applied(): List<AppliedRecipeEntity>

    @Query("SELECT * FROM applied_recipes WHERE id = :id")
    suspend fun appliedById(id: Long): AppliedRecipeEntity?

    @Insert
    suspend fun insertApplied(e: AppliedRecipeEntity): Long

    @Upsert
    suspend fun upsertApplied(e: AppliedRecipeEntity)

    @Query("DELETE FROM applied_recipes WHERE id = :id")
    suspend fun deleteApplied(id: Long)

    /** Rows older than [before] that are no longer on: kept a while for 나눔 판 기록, then dropped. */
    @Query("DELETE FROM applied_recipes WHERE appliedAt < :before AND (undoneAt IS NOT NULL OR replacedAt IS NOT NULL)")
    suspend fun pruneApplied(before: Long)

    @Query("DELETE FROM applied_recipes")
    suspend fun clearApplied()

    @Query("SELECT * FROM recipe_drafts ORDER BY updatedAt DESC, id DESC")
    fun observeDrafts(): Flow<List<RecipeDraftEntity>>

    @Query("SELECT * FROM recipe_drafts ORDER BY updatedAt DESC, id DESC")
    suspend fun drafts(): List<RecipeDraftEntity>

    @Insert
    suspend fun insertDraft(e: RecipeDraftEntity): Long

    @Upsert
    suspend fun upsertDraft(e: RecipeDraftEntity)

    @Query("DELETE FROM recipe_drafts WHERE id = :id")
    suspend fun deleteDraft(id: Long)

    @Query("DELETE FROM recipe_drafts")
    suspend fun clearDrafts()
}
