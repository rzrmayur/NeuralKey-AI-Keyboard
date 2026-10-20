package com.supakeyboard.neuralkey.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    @SerialName("model")
    val model: String? = null,
    @SerialName("models")
    val models: List<String>? = null,
    @SerialName("messages")
    val messages: List<ChatMessageDto>,
    @SerialName("stream")
    val stream: Boolean = true,
    @SerialName("provider")
    val provider: ProviderRoutingDto? = ProviderRoutingDto(sort = "latency", allowFallbacks = true),
    @SerialName("temperature")
    val temperature: Double? = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

@Serializable
data class ChatMessageDto(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: String
)

@Serializable
data class ProviderRoutingDto(
    @SerialName("sort")
    val sort: String = "latency",
    @SerialName("allow_fallbacks")
    val allowFallbacks: Boolean = true
)

@Serializable
data class ChatCompletionChunkDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("model")
    val model: String? = null,
    @SerialName("choices")
    val choices: List<ChunkChoiceDto> = emptyList()
)

@Serializable
data class ChunkChoiceDto(
    @SerialName("index")
    val index: Int? = null,
    @SerialName("delta")
    val delta: ChunkDeltaDto? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChunkDeltaDto(
    @SerialName("content")
    val content: String? = null,
    @SerialName("role")
    val role: String? = null
)
