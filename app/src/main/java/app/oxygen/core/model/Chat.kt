package app.oxygen.core.model
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
@Serializable
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
@Serializable
enum class MessageStatus { COMPLETE, STREAMING, INTERRUPTED, ERROR }
@Serializable
data class Attachment(
    val id: String,
    val kind: Kind,
    val uri: String,
    val mimeType: String,
    val name: String?,
) {
    @Serializable
    enum class Kind { IMAGE, PDF }
}
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)
@Serializable
data class Citation(
    val url: String,
    val title: String?,
    val content: String?,
)
@Serializable
data class TraceStep(
    val kind: Kind,
    val text: String = "",
    val query: String? = null,
    val urls: List<String> = emptyList(),
) {
    @Serializable
    enum class Kind { THINKING, WEB_SEARCH }
    companion object {
        fun thinking(text: String) = TraceStep(Kind.THINKING, text = text)
        fun webSearch(query: String?, urls: List<String>) = TraceStep(Kind.WEB_SEARCH, query = query, urls = urls)
    }
}
@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val parentId: String?,
    val role: Role,
    val content: String,
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val trace: List<TraceStep> = emptyList(),
    val modelId: String? = null,
    val provider: String? = null,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val error: String? = null,
    val finishReason: String? = null,
    val generationId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val costUsd: Double? = null,
    val createdAt: Long,
    val latencyMs: Long? = null,
) {
    val totalTokens: Int? get() = when {
        promptTokens == null && completionTokens == null -> null
        else -> (promptTokens ?: 0) + (completionTokens ?: 0)
    }
}
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val titleIsAuto: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String,
    val systemPrompt: String?,
    val params: ChatParams,
    val currentLeafId: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val ephemeral: Boolean = false,
)
@Serializable
data class ProviderPrefs(
    val allowFallbacks: Boolean? = null,
    val sort: String? = null,
    val dataCollection: String? = null,
    val zdr: Boolean? = null,
    val requireParameters: Boolean? = null,
    val only: List<String> = emptyList(),
    val ignore: List<String> = emptyList(),
    val order: List<String> = emptyList(),
    val quantizations: List<String> = emptyList(),
    val maxPricePrompt: Double? = null,
    val maxPriceCompletion: Double? = null,
) {
    val isEmpty: Boolean
        get() = allowFallbacks == null && sort == null && dataCollection == null && zdr == null &&
            requireParameters == null && only.isEmpty() && ignore.isEmpty() && order.isEmpty() &&
            quantizations.isEmpty() && maxPricePrompt == null && maxPriceCompletion == null
}
@Serializable
data class ChatParams(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val seed: Long? = null,
    val stop: List<String>? = null,
    val logprobs: Boolean? = null,
    val topLogprobs: Int? = null,
    val reasoningEffort: String? = null,
    val reasoningMaxTokens: Int? = null,
    val reasoningExclude: Boolean? = null,
    val tools: Map<String, String> = emptyMap(),
    val serverTools: List<String> = emptyList(),
    val maxToolCalls: Int? = null,
    val contextStrategy: String? = null,
    val contextWindow: Int? = null,
    val webSearch: Boolean? = null,
    val webSearchMaxResults: Int? = null,
    val webSearchPrompt: String? = null,
    val responseHealing: Boolean? = null,
    val fileParser: Boolean? = null,
    val middleOut: Boolean? = null,
    val responseFormat: String? = null,
    val jsonSchema: String? = null,
    val imageResolution: String? = null,
    val imageAspectRatio: String? = null,
    val imageQuality: String? = null,
    val imageOutputFormat: String? = null,
    val imageBackground: String? = null,
    val imageCount: Int? = null,
    val fallbackModels: List<String> = emptyList(),
    val provider: ProviderPrefs = ProviderPrefs(),
    val extraBody: String? = null,
) {
}
@Serializable
data class ChatModel(
    val id: String,
    val name: String,
    val author: String,
    val description: String?,
    val contextLength: Int?,
    val createdAt: Long,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val supportedParams: Set<String>,
    val promptPrice: Double?,
    val completionPrice: Double?,
    val cacheReadPrice: Double?,
    val cacheWritePrice: Double?,
    val isFree: Boolean,
    val popularityScore: Long? = null,
    val canonicalSlug: String? = null,
    val imageGeneration: Boolean = false,
    val imageParams: JsonObject? = null,
) {
    val slug: String get() = id.substringAfter('/')
    val isImageOnly: Boolean get() = outputModalities.isNotEmpty() && "text" !in outputModalities
    fun imageOptions(name: String): List<String> =
        imageParams?.get(name)?.let { spec ->
            (spec as? JsonObject)?.get("values")?.let { values ->
                (values as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
        } ?: emptyList()
    fun imageRange(name: String): IntRange? {
        val spec = imageParams?.get(name) as? JsonObject ?: return null
        val min = (spec["min"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return null
        val max = (spec["max"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return null
        return min..max
    }
    fun supportsImageParam(name: String): Boolean = imageParams?.containsKey(name) == true
    val shortName: String
        get() {
            val cleaned = name.replace(NAME_PREFIX_REGEX, "").trim()
            return cleaned.ifBlank { slug }
        }
    val hasVision: Boolean get() = "image" in inputModalities
    val hasAudioIn: Boolean get() = "audio" in inputModalities
    val hasTools: Boolean get() = "tools" in supportedParams
    val hasReasoning: Boolean
        get() = "reasoning" in supportedParams || "include_reasoning" in supportedParams ||
            shortName.contains("reason", ignoreCase = true) || shortName.contains("think", ignoreCase = true)
    val hasImageOut: Boolean get() = "image" in outputModalities
    companion object {
        private val NAME_PREFIX_REGEX = Regex("^[\\p{L}\\p{N} .&'()_-]{2,30}:\\s+")
    }
}
data class ProviderInfo(
    val slug: String,
    val name: String,
)
data class ModelEndpoint(
    val providerSlug: String,
    val providerName: String,
    val quantization: String?,
    val contextLength: Int?,
    val promptPrice: Double?,
    val completionPrice: Double?,
    val latencyMs: Double?,
    val throughput: Double?,
    val uptime: Double?,
    val status: Int?,
)
data class Credits(
    val totalCredits: Double,
    val totalUsage: Double,
) {
    val remaining: Double get() = totalCredits - totalUsage
}
data class KeyInfo(
    val label: String?,
    val usage: Double?,
    val limit: Double?,
    val limitRemaining: Double?,
    val isFreeTier: Boolean,
    val rateLimitRequests: Int?,
    val rateLimitIntervalSeconds: Int?,
)
data class GenStats(
    val id: String,
    val model: String?,
    val provider: String?,
    val cost: Double?,
    val promptTokens: Int?,
    val completionTokens: Int?,
)
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int,
    val reasoningTokens: Int,
    val cost: Double?,
)
sealed interface ChatEvent {
    data class Meta(val generationId: String?) : ChatEvent
    data class Text(val delta: String) : ChatEvent
    data class Reasoning(val delta: String) : ChatEvent
    data class ToolCalls(val deltas: List<app.oxygen.core.network.ToolCallDeltaDto>) : ChatEvent
    data class Images(val urls: List<String>) : ChatEvent
    data class Citations(val items: List<Citation>) : ChatEvent
    data object KeepAlive : ChatEvent
    data class Usage(
        val usage: TokenUsage,
        val model: String?,
        val provider: String?,
    ) : ChatEvent
    data class Done(
        val finishReason: String?,
        val nativeFinishReason: String?,
        val model: String?,
        val provider: String?,
    ) : ChatEvent
    data class Failed(val code: Int?, val message: String) : ChatEvent
}
