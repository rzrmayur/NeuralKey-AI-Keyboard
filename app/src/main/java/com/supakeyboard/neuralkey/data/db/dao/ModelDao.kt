package com.supakeyboard.neuralkey.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<OpenRouterModelEntity>)

    @Query("SELECT * FROM models ORDER BY isPinned DESC, isFree DESC, name ASC")
    fun getAllModels(): Flow<List<OpenRouterModelEntity>>

    @Query("SELECT * FROM models WHERE isFree = 1 ORDER BY isPinned DESC, name ASC")
    fun getFreeModels(): Flow<List<OpenRouterModelEntity>>

    @Query("SELECT * FROM models WHERE isPinned = 1 ORDER BY name ASC")
    fun getPinnedModels(): Flow<List<OpenRouterModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    fun getModelById(id: String): Flow<OpenRouterModelEntity?>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelByIdSync(id: String): OpenRouterModelEntity?

    @Query("SELECT id FROM models WHERE isPinned = 1")
    suspend fun getPinnedModelIds(): List<String>

    @Query("UPDATE models SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean)

    @Query("""
        SELECT * FROM models 
        WHERE (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, isFree DESC, name ASC
    """)
    fun searchModels(query: String): Flow<List<OpenRouterModelEntity>>

    @Query("""
        SELECT * FROM models 
        WHERE isFree = 1 
          AND (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, name ASC
    """)
    fun searchFreeModels(query: String): Flow<List<OpenRouterModelEntity>>

    @Query("""
        SELECT models.* FROM models
        JOIN models_fts ON models.rowid = models_fts.rowid
        WHERE models_fts MATCH :ftsQuery
        ORDER BY models.isPinned DESC, models.isFree DESC, models.name ASC
    """)
    fun searchModelsFts(ftsQuery: String): Flow<List<OpenRouterModelEntity>>

    @Query("SELECT COUNT(*) FROM models")
    suspend fun getModelCount(): Int

    @Query("SELECT COALESCE(MAX(lastUpdated), 0) FROM models")
    suspend fun getModelAgeMs(): Long

    @Query("DELETE FROM models")
    suspend fun clearAllModels()

    @Transaction
    suspend fun replaceModelsPreservingPins(newModels: List<OpenRouterModelEntity>) {
        val currentlyPinned = getPinnedModelIds().toSet()
        val modelsToInsert = newModels.map { model ->
            if (currentlyPinned.contains(model.id)) {
                model.copy(isPinned = true)
            } else {
                model
            }
        }
        insertModels(modelsToInsert)
    }

    companion object {
        /**
         * Sanitizes a raw user query for FTS5 compatibility.
         * Removes special FTS5 characters and appends prefix-matching wildcards.
         */
        fun sanitizeFtsQuery(rawQuery: String): String {
            val sanitized = rawQuery.trim()
                .replace("\"", "")
                .replace("*", "")
                .replace("(", "")
                .replace(")", "")
            if (sanitized.isEmpty()) return sanitized
            // FTS5 prefix matching: append * to each term
            return sanitized.split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" ") { "$it*" }
        }
    }
}
