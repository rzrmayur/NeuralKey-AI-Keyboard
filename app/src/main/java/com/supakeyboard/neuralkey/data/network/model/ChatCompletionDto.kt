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

/**
 * Multimodal request supporting OpenRouter's `input_audio` content blocks
 * for audio-capable models (e.g., google/gemini-2.5-flash, google/gemini-2.0-flash-001, openai/gpt-4o-audio-preview).
 */
@Serializable
data class MultimodalChatCompletionRequest(
    @SerialName("models")
    val models: List<String>,
    @SerialName("messages")
    val messages: List<MultimodalChatMessageDto>,
    @SerialName("stream")
    val stream: Boolean = true,
    @SerialName("provider")
    val provider: ProviderRoutingDto? = ProviderRoutingDto(sort = "latency", allowFallbacks = true),
    @SerialName("temperature")
    val temperature: Double? = 0.4
)

@Serializable
data class MultimodalChatMessageDto(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: List<MultimodalContentPartDto>
)

@Serializable
data class MultimodalContentPartDto(
    @SerialName("type")
    val type: String,
    @SerialName("text")
    val text: String? = null,
    @SerialName("input_audio")
    val inputAudio: InputAudioPayloadDto? = null
)

@Serializable
data class InputAudioPayloadDto(
    @SerialName("data")
    val data: String,
    @SerialName("format")
    val format: String = "wav"
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
    val choices: List<ChunkChoiceDto> = emptyList(),
    @SerialName("error")
    val error: OpenRouterErrorDetailDto? = null
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

@Serializable
data class OpenRouterErrorResponseDto(
    @SerialName("error")
    val error: OpenRouterErrorDetailDto? = null
)

@Serializable
data class OpenRouterErrorDetailDto(
    @SerialName("code")
    val code: Int? = null,
    @SerialName("message")
    val message: String? = null
)
