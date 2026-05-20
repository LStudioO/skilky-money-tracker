package com.vstorchevyi.skilky.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire shapes for Ollama's `/api/chat` endpoint. Kept internal to the
 * `:server.ai` package: nothing outside this package should know whether
 * the AI backend is Ollama, llama.cpp, or anything else.
 */
@Serializable
internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val format: JsonObject? = null,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
    /**
     * How long Ollama keeps the model loaded after this request finishes.
     * `"30m"` keeps it warm for half an hour; `"-1"` keeps it forever.
     * Default in Ollama is 5 minutes — too short for our usage cadence,
     * the next parse pays a cold-start.
     */
    @kotlinx.serialization.SerialName("keep_alive")
    val keepAlive: String? = null,
)

/**
 * Per-request sampling overrides for Ollama's `/api/chat`. Defaults
 * match Google's recommended Gemma 4 configuration
 * (https://ollama.com/library/gemma4): temperature 1.0, top_p 0.95,
 * top_k 64. Other models may want different values, but the schema is
 * shared so passing them is harmless.
 */
@Serializable
internal data class OllamaOptions(
    val temperature: Double? = null,
    @kotlinx.serialization.SerialName("top_p")
    val topP: Double? = null,
    @kotlinx.serialization.SerialName("top_k")
    val topK: Int? = null,
)

/**
 * [images] doubles as the multimodal-input slot per Ollama's chat API:
 * each entry is a base64-encoded blob, and Ollama dispatches it as an
 * image or as audio by inspecting the magic bytes (RIFF/WAVE = WAV
 * audio, otherwise image). The audio path requires WAV 16kHz mono.
 * Modal ordering is implicit — Ollama processes everything in [images]
 * before [content], so the upstream caller does not need to interleave.
 */
@Serializable
internal data class OllamaMessage(
    val role: String,
    val content: String,
    val images: List<String>? = null,
)

@Serializable
internal data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean,
)
