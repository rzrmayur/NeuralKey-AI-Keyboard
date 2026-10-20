package com.supakeyboard.neuralkey.ime.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.compose.runtime.Immutable
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sqrt

@Immutable
data class VoiceModelOption(
    val id: String,
    val name: String,
    val badge: String,
    val isFree: Boolean,
    val usesTranscriptionEndpoint: Boolean = false
)

/**
 * Curated catalog of OpenRouter models that support Voice / Audio input
 * (`/api/v1/audio/transcriptions` and multimodal `input_audio` chat completions),
 * plus speech deduplication and hardware 16kHz PCM WAV recording.
 */
object VoiceAudioEngine {

    const val DEFAULT_VOICE_MODEL_ID = "elevenlabs/scribe-v2"

    val CURATED_VOICE_MODELS: List<VoiceModelOption> = listOf(
        VoiceModelOption(
            id = "elevenlabs/scribe-v2",
            name = "ElevenLabs: Scribe v2",
            badge = "Scribe v2 STT",
            isFree = false,
            usesTranscriptionEndpoint = true
        ),
        VoiceModelOption(
            id = "mistralai/voxtral-mini-transcribe",
            name = "Mistral: Voxtral Mini Transcribe",
            badge = "Voxtral STT",
            isFree = false,
            usesTranscriptionEndpoint = true
        ),
        VoiceModelOption(
            id = "mistralai/voxtral-small-24b-2507",
            name = "Mistral: Voxtral Small 24B",
            badge = "Voxtral Audio LLM",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "google/gemini-2.5-flash",
            name = "Google: Gemini 2.5 Flash",
            badge = "Audio + AI",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "google/gemini-2.5-flash-lite",
            name = "Google: Gemini 2.5 Flash Lite",
            badge = "Ultra Fast Audio",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "google/gemini-3-flash-preview",
            name = "Google: Gemini 3 Flash",
            badge = "Next-Gen Audio",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "openai/gpt-audio-mini",
            name = "OpenAI: GPT Audio Mini",
            badge = "GPT Voice",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "openai/gpt-audio",
            name = "OpenAI: GPT Audio",
            badge = "GPT Flagship Voice",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "openai/whisper-large-v3-turbo",
            name = "OpenAI: Whisper Large v3 Turbo",
            badge = "Whisper STT",
            isFree = false,
            usesTranscriptionEndpoint = true
        ),
        VoiceModelOption(
            id = "qwen/qwen3.8-omni-flash",
            name = "Qwen: Qwen3.8 Omni Flash",
            badge = "Omni Audio",
            isFree = false,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
            name = "NVIDIA: Nemotron 3 Omni (Free)",
            badge = "FREE Audio",
            isFree = true,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "thinkingmachines/inkling:free",
            name = "Thinking Machines: Inkling (Free)",
            badge = "FREE Audio",
            isFree = true,
            usesTranscriptionEndpoint = false
        ),
        VoiceModelOption(
            id = "thinkingmachines/inkling-small:free",
            name = "Thinking Machines: Inkling Small (Free)",
            badge = "FREE Audio",
            isFree = true,
            usesTranscriptionEndpoint = false
        )
    )

    fun isDedicatedTranscriptionModel(modelId: String): Boolean {
        val lower = modelId.lowercase(Locale.ROOT)
        return lower.contains("scribe") ||
            lower.contains("whisper") ||
            lower.contains("transcribe")
    }

    fun isVoiceCapableModelId(modelId: String, modality: String = ""): Boolean {
        val lowerId = modelId.lowercase(Locale.ROOT)
        val lowerMod = modality.lowercase(Locale.ROOT)
        if (lowerId.endsWith(":batch")) return false
        return lowerMod.contains("audio") ||
            lowerId.contains("scribe") ||
            lowerId.contains("voxtral") ||
            lowerId.contains("whisper") ||
            lowerId.contains("gpt-audio") ||
            lowerId.contains("gpt-4o-audio") ||
            lowerId.contains("omni") ||
            lowerId.contains("inkling") ||
            lowerId.contains("mimo") ||
            lowerId.contains("gemini-2.5-flash") ||
            lowerId.contains("gemini-2.5-pro") ||
            lowerId.contains("gemini-3") ||
            lowerId.contains("gemini-4")
    }

    /**
     * Merges our curated voice/STT models with any audio-capable models discovered in OpenRouter's live catalog.
     */
    fun buildAvailableVoiceModels(catalogModels: List<OpenRouterModelEntity>): List<VoiceModelOption> {
        val curatedIds = CURATED_VOICE_MODELS.map { it.id }.toSet()
        val dynamicAudioModels = catalogModels
            .filter { it.id !in curatedIds && isVoiceCapableModelId(it.id, it.modality) }
            .map { entity ->
                VoiceModelOption(
                    id = entity.id,
                    name = entity.name,
                    badge = if (entity.isFree) "FREE Audio" else "Audio LLM",
                    isFree = entity.isFree,
                    usesTranscriptionEndpoint = isDedicatedTranscriptionModel(entity.id)
                )
            }
        return CURATED_VOICE_MODELS + dynamicAudioModels
    }

    /**
     * Cleans repetitive words, consecutive n-gram phrase stutters, and cumulative SpeechRecognizer
     * duplications that occur on mobile devices.
     *
     * Examples:
     * - "hello hello world" -> "hello world"
     * - "how are you how are you" -> "how are you"
     * - "I think we should I think we should ship today" -> "I think we should ship today"
     */
    fun cleanRepetitiveSpeech(raw: String): String {
        val normalizedWhitespace = raw.trim().replace(Regex("\\s+"), " ")
        if (normalizedWhitespace.isEmpty()) return ""

        var tokens = normalizedWhitespace.split(" ")
        if (tokens.size <= 1) return normalizedWhitespace

        // Pass 1: Collapse consecutive repeated N-gram phrases (from N=6 down to N=1)
        for (ngramSize in 6 downTo 1) {
            tokens = collapseConsecutiveNgrams(tokens, ngramSize)
        }

        // Pass 2: Final single-word pass in case phrase collapsing exposed adjacent identical words
        tokens = collapseConsecutiveNgrams(tokens, 1)

        return tokens.joinToString(" ").trim()
    }

    private fun collapseConsecutiveNgrams(tokens: List<String>, n: Int): List<String> {
        if (tokens.size < n * 2) return tokens
        val result = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            if (result.size >= n && i + n <= tokens.size) {
                var matchesPrevious = true
                for (k in 0 until n) {
                    val prevWord = normalizeTokenForCompare(result[result.size - n + k])
                    val currWord = normalizeTokenForCompare(tokens[i + k])
                    if (prevWord.isEmpty() || prevWord != currWord) {
                        matchesPrevious = false
                        break
                    }
                }
                if (matchesPrevious) {
                    i += n
                    continue
                }
            }
            result.add(tokens[i])
            i++
        }
        return result
    }

    private fun normalizeTokenForCompare(token: String): String {
        return token.lowercase(Locale.ROOT).trim(',', '.', '!', '?', ';', ':')
    }

    /**
     * Merges a newly finalized speech segment into [existingCommitted] without duplicating
     * cumulative partial transcripts or overlapping boundary words.
     */
    fun mergeRecognizedSegment(existingCommitted: String, newFinalSegment: String): String {
        val cleanExisting = cleanRepetitiveSpeech(existingCommitted)
        val cleanNew = cleanRepetitiveSpeech(newFinalSegment)
        if (cleanNew.isEmpty()) return cleanExisting
        if (cleanExisting.isEmpty()) return cleanNew

        val lowerExisting = cleanExisting.lowercase(Locale.ROOT)
        val lowerNew = cleanNew.lowercase(Locale.ROOT)

        // Case 1: OEM recognizer returned the entire cumulative utterance again
        if (lowerNew.startsWith(lowerExisting)) {
            return cleanNew
        }
        // Case 2: New segment is already contained at the end of existing text
        if (lowerExisting.endsWith(lowerNew)) {
            return cleanExisting
        }

        // Case 3: Check word-level suffix/prefix overlap (up to 8 words)
        val existingWords = cleanExisting.split(" ")
        val newWords = cleanNew.split(" ")
        val maxOverlap = minOf(existingWords.size, newWords.size, 8)
        for (overlap in maxOverlap downTo 1) {
            val suffix = existingWords.subList(existingWords.size - overlap, existingWords.size)
                .map { normalizeTokenForCompare(it) }
            val prefix = newWords.subList(0, overlap)
                .map { normalizeTokenForCompare(it) }
            if (suffix == prefix) {
                val combined = existingWords + newWords.drop(overlap)
                return cleanRepetitiveSpeech(combined.joinToString(" "))
            }
        }

        return cleanRepetitiveSpeech("$cleanExisting $cleanNew")
    }

    /**
     * Encodes 16-bit mono PCM byte array into a valid 44-byte RIFF WAV container and returns Base64.
     */
    fun encodePcmToWavBase64(pcmBytes: ByteArray, sampleRate: Int = 16000): String {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalDataLen = pcmBytes.size + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.size)
        }.array()

        val wavBytes = ByteArray(44 + pcmBytes.size)
        System.arraycopy(header, 0, wavBytes, 0, 44)
        System.arraycopy(pcmBytes, 0, wavBytes, 44, pcmBytes.size)
        return runCatching { Base64.encodeToString(wavBytes, Base64.NO_WRAP) }
            .getOrNull()
            ?: java.util.Base64.getEncoder().encodeToString(wavBytes)
    }
}

/**
 * Hardware 16kHz 16-bit Mono PCM recorder that captures crystal-clear voice audio
 * directly into a RIFF WAV payload for OpenRouter Scribe v2 / Voxtral / Gemini / Whisper.
 */
class WavAudioRecorder(
    private val sampleRate: Int = 16000,
    private val onAmplitudeChanged: (Float) -> Unit = {}
) {
    @Volatile
    var isRecording = false
        private set
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val pcmOutput = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: return false

        synchronized(pcmOutput) {
            pcmOutput.reset()
        }
        audioRecord = recorder
        isRecording = true

        runCatching { recorder.startRecording() }.onFailure {
            isRecording = false
            runCatching { recorder.release() }
            audioRecord = null
            return false
        }

        recordingThread = thread(start = true, name = "NeuralKeyWavRecorder") {
            val shortBuf = ShortArray(1024)
            val byteBuf = ByteBuffer.allocate(shortBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (isRecording) {
                val read = runCatching { recorder.read(shortBuf, 0, shortBuf.size) }.getOrDefault(-1)
                if (read > 0) {
                    var sumSquares = 0.0
                    byteBuf.clear()
                    for (i in 0 until read) {
                        val sample = shortBuf[i]
                        byteBuf.putShort(sample)
                        val norm = sample / 32768.0
                        sumSquares += norm * norm
                    }
                    synchronized(pcmOutput) {
                        // Cap at ~60 seconds (16000 * 2 * 60 = 1.92MB) to keep memory bounded
                        if (pcmOutput.size() < 1_920_000) {
                            pcmOutput.write(byteBuf.array(), 0, read * 2)
                        }
                    }
                    val rms = sqrt(sumSquares / read).toFloat()
                    val normalizedLevel = (rms * 4.5f).coerceIn(0.05f, 1.0f)
                    onAmplitudeChanged(normalizedLevel)
                }
            }
        }
        return true
    }

    fun stopAndEncodeWavBase64(): String? {
        if (!isRecording) return null
        isRecording = false
        runCatching { recordingThread?.join(400) }
        recordingThread = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        onAmplitudeChanged(0f)

        val pcmBytes = synchronized(pcmOutput) { pcmOutput.toByteArray() }
        // Require at least ~0.15s of audio (4800 bytes at 16kHz 16-bit mono)
        if (pcmBytes.size < 4800) return null
        return VoiceAudioEngine.encodePcmToWavBase64(pcmBytes, sampleRate)
    }

    fun cancel() {
        isRecording = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        recordingThread = null
        synchronized(pcmOutput) { pcmOutput.reset() }
    }
}
