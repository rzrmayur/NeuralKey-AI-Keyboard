package com.supakeyboard.neuralkey.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeyAuthResponse(
    @SerialName("data")
    val data: KeyDataDto
)

@Serializable
data class KeyDataDto(
    @SerialName("label")
    val label: String? = null,
    @SerialName("usage")
    val usage: Double = 0.0,
    @SerialName("limit")
    val limit: Double? = null,
    @SerialName("is_free_tier")
    val isFreeTier: Boolean = false,
    @SerialName("rate_limit")
    val rateLimit: RateLimitDto? = null
) {
    val remainingCredits: Double?
        get() = limit?.let { it - usage }

    val formattedUsage: String
        get() = String.format("$%.4f", usage)

    val formattedLimit: String
        get() = limit?.let { String.format("$%.2f", it) } ?: "Unlimited"
}

@Serializable
data class RateLimitDto(
    @SerialName("requests")
    val requests: Int? = null,
    @SerialName("interval")
    val interval: String? = null
)
