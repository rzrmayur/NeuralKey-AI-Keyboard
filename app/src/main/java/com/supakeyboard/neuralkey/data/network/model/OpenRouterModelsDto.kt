package com.supakeyboard.neuralkey.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(
    @SerialName("data")
    val data: List<OpenRouterModelDto> = emptyList()
)

@Serializable
data class OpenRouterModelDto(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("architecture")
    val architecture: ArchitectureDto? = null,
    @SerialName("pricing")
    val pricing: PricingDto? = null,
    @SerialName("top_provider")
    val topProvider: TopProviderDto? = null
) {
    /**
     * Cost per 1M prompt tokens in USD.
     * OpenRouter gives pricing as price per single token in decimal string (e.g. "0.000001").
     */
    val promptCostPerMillion: Double
        get() {
            val perToken = pricing?.prompt?.toDoubleOrNull() ?: 0.0
            return perToken * 1_000_000.0
        }

    /**
     * Cost per 1M completion tokens in USD.
     */
    val completionCostPerMillion: Double
        get() {
            val perToken = pricing?.completion?.toDoubleOrNull() ?: 0.0
            return perToken * 1_000_000.0
        }

    /**
     * Model is considered free if prompt and completion costs are explicitly zero or pricing is zero.
     */
    val isFree: Boolean
        get() {
            val promptZero = pricing?.prompt == null || pricing.prompt == "0" || (pricing.prompt.toDoubleOrNull() ?: 0.0) == 0.0
            val completionZero = pricing?.completion == null || pricing.completion == "0" || (pricing.completion.toDoubleOrNull() ?: 0.0) == 0.0
            return promptZero && completionZero
        }

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
}

@Serializable
data class ArchitectureDto(
    @SerialName("modality")
    val modality: String? = null,
    @SerialName("tokenizer")
    val tokenizer: String? = null,
    @SerialName("instruct_type")
    val instructType: String? = null
)

@Serializable
data class PricingDto(
    @SerialName("prompt")
    val prompt: String? = null,
    @SerialName("completion")
    val completion: String? = null,
    @SerialName("request")
    val request: String? = null,
    @SerialName("image")
    val image: String? = null
)

@Serializable
data class TopProviderDto(
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean? = null
)
