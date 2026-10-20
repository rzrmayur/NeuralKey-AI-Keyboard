package com.supakeyboard.neuralkey.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPromptDao {

    @Query("SELECT * FROM user_prompts ORDER BY orderIndex ASC")
    fun getAllPrompts(): Flow<List<UserPromptEntity>>

    @Query("SELECT * FROM user_prompts WHERE id = :id LIMIT 1")
    suspend fun getPromptById(id: Long): UserPromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: UserPromptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompts(prompts: List<UserPromptEntity>)

    @Update
    suspend fun updatePrompt(prompt: UserPromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: UserPromptEntity)

    @Query("DELETE FROM user_prompts WHERE id = :id")
    suspend fun deletePromptById(id: Long)

    @Query("SELECT COUNT(*) FROM user_prompts")
    suspend fun getPromptCount(): Int
}
