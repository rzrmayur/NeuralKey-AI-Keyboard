package com.supakeyboard.neuralkey.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "models_fts")
@Fts4(contentEntity = OpenRouterModelEntity::class)
data class OpenRouterModelFtsEntity(
    val name: String,
    val description: String
)
