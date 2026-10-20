package com.supakeyboard.neuralkey.data.db.entity

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.supakeyboard.neuralkey.data.network.model.OpenRouterModelDto

@Entity(tableName = "models")
@Stable
data class OpenRouterModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int,
    val modality: String,
    val promptPricePerToken: String,
    val completionPricePerToken: String,
    val promptCostPerMillion: Double,
    val completionCostPerMillion: Double,
    val isFree: Boolean,
    val isPinned: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val formattedCost: String
        get() {
            return if (isFree) {
                "Free"
            } else {
                val inputStr = String.format("%.2f", promptCostPerMillion)
                val outputStr = String.format("%.2f", completionCostPerMillion)
                "$$inputStr / $$outputStr per 1M"
            }
        }

    val formattedContextLength: String
        get() {
            return if (contextLength >= 1000) {
                "${contextLength / 1000}k"
            } else {
                "$contextLength"
            }
        }

    companion object {
        fun fromDto(dto: OpenRouterModelDto, isPinned: Boolean = false): OpenRouterModelEntity {
            return OpenRouterModelEntity(
                id = dto.id,
                name = dto.name,
                description = dto.description ?: "",
                contextLength = dto.contextLength ?: 0,
                modality = dto.architecture?.modality ?: "text->text",
                promptPricePerToken = dto.pricing?.prompt ?: "0",
                completionPricePerToken = dto.pricing?.completion ?: "0",
                promptCostPerMillion = dto.promptCostPerMillion,
                completionCostPerMillion = dto.completionCostPerMillion,
                isFree = dto.isFree,
                isPinned = isPinned,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
