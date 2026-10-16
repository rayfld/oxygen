package app.oxygen.core.network
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
@Serializable
data class ApiErrorEnvelopeDto(val error: ApiErrorInfoDto? = null)
@Serializable
data class ApiErrorInfoDto(
    val code: JsonElement? = null,
    val message: String? = null,
    val metadata: JsonObject? = null,
)
@Serializable
data class StreamChunkDto(
    val id: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val choices: List<StreamChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
    val error: ApiErrorInfoDto? = null,
)
@Serializable
data class StreamChoiceDto(
    val index: Int = 0,
    val delta: DeltaDto? = null,
    val message: DeltaDto? = null,
    val finish_reason: String? = null,
    val native_finish_reason: String? = null,
    val error: ApiErrorInfoDto? = null,
)
@Serializable
data class DeltaDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
    val reasoning_details: List<ReasoningDetailDto>? = null,
    val tool_calls: List<ToolCallDeltaDto>? = null,
    val annotations: List<JsonObject>? = null,
    val images: List<JsonObject>? = null,
)
@Serializable
data class ReasoningDetailDto(
    val type: String? = null,
    val text: String? = null,
    val summary: String? = null,
    val data: JsonElement? = null,
    val id: String? = null,
    val format: String? = null,
    val index: Int? = null,
)
@Serializable
data class ToolCallDeltaDto(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionDeltaDto? = null,
)
@Serializable
data class FunctionDeltaDto(
    val name: String? = null,
    val arguments: String? = null,
)
@Serializable
data class UsageDto(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null,
    val cost: Double? = null,
    val is_byok: Boolean? = null,
    val prompt_tokens_details: PromptTokensDetailsDto? = null,
    val completion_tokens_details: CompletionTokensDetailsDto? = null,
)
@Serializable
data class PromptTokensDetailsDto(
    val cached_tokens: Int? = null,
    val cache_write_tokens: Int? = null,
    val audio_tokens: Int? = null,
)
@Serializable
data class CompletionTokensDetailsDto(
    val reasoning_tokens: Int? = null,
    val audio_tokens: Int? = null,
)
@Serializable
data class ChatRequestDto(
    val model: String? = null,
    val models: List<String>? = null,
    val route: String? = null,
    val messages: List<RequestMessageDto>,
    val stream: Boolean,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val min_p: Double? = null,
    val top_a: Double? = null,
    val frequency_penalty: Double? = null,
    val presence_penalty: Double? = null,
    val repetition_penalty: Double? = null,
    val seed: Long? = null,
    val stop: List<String>? = null,
    val logprobs: Boolean? = null,
    val top_logprobs: Int? = null,
    val response_format: JsonElement? = null,
    val tools: JsonElement? = null,
    val tool_choice: JsonElement? = null,
    val max_tool_calls: Int? = null,
    val reasoning: JsonObject? = null,
    val plugins: List<JsonObject>? = null,
    val provider: JsonObject? = null,
    val transforms: List<String>? = null,
    val user: String? = null,
    val usage: JsonObject? = null,
)
@Serializable
data class RequestMessageDto(
    val role: String,
    val content: JsonElement,
    val name: String? = null,
    val tool_call_id: String? = null,
    val tool_calls: List<JsonObject>? = null,
)
@Serializable
data class ModelsEnvelopeDto(val data: List<ModelDto> = emptyList())
@Serializable
data class ModelDto(
    val id: String,
    val name: String? = null,
    val canonical_slug: String? = null,
    val description: String? = null,
    val created: Long? = null,
    val context_length: Int? = null,
    val architecture: ArchitectureDto? = null,
    val pricing: PricingDto? = null,
    val supported_parameters: List<String>? = null,
    val top_provider: TopProviderDto? = null,
    val per_request_limits: JsonObject? = null,
)
@Serializable
data class ArchitectureDto(
    val modality: String? = null,
    val input_modalities: List<String>? = null,
    val output_modalities: List<String>? = null,
    val tokenizer: String? = null,
)
@Serializable
data class PricingDto(
    val prompt: String? = null,
    val completion: String? = null,
    val request: String? = null,
    val image: String? = null,
    val web_search: String? = null,
    val internal_reasoning: String? = null,
    val input_cache_read: String? = null,
    val input_cache_write: String? = null,
)
@Serializable
data class TopProviderDto(
    val context_length: Int? = null,
    val max_completion_tokens: Int? = null,
    val is_moderated: Boolean? = null,
)
@Serializable
data class EndpointsEnvelopeDto(val data: EndpointsDataDto? = null)
@Serializable
data class EndpointsDataDto(
    val id: String? = null,
    val name: String? = null,
    val endpoints: List<EndpointDto> = emptyList(),
)
@Serializable
data class EndpointDto(
    val name: String? = null,
    val provider_name: String? = null,
    val context_length: Int? = null,
    val pricing: PricingDto? = null,
    val quantization: String? = null,
    val max_completion_tokens: Int? = null,
    val supported_parameters: List<String>? = null,
    val uptime_last_30m: Double? = null,
    val throughput_last_30m: Double? = null,
    val latency_last_30m: Double? = null,
    val supports_implicit_caching: Boolean? = null,
    val status: Int? = null,
)
@Serializable
data class ProvidersEnvelopeDto(val data: List<ProviderDto> = emptyList())
@Serializable
data class ProviderDto(
    val name: String? = null,
    val slug: String? = null,
    val privacy_policy_url: String? = null,
    val status_page_url: String? = null,
)
@Serializable
data class CreditsEnvelopeDto(val data: CreditsDto? = null)
@Serializable
data class CreditsDto(
    val total_credits: Double = 0.0,
    val total_usage: Double = 0.0,
)
@Serializable
data class KeyEnvelopeDto(val data: KeyDto? = null)
@Serializable
data class KeyDto(
    val label: String? = null,
    val usage: Double? = null,
    val limit: Double? = null,
    val limit_remaining: Double? = null,
    val is_free_tier: Boolean? = null,
    val is_provisioning_key: Boolean? = null,
    val rate_limit: RateLimitDto? = null,
)
@Serializable
data class RateLimitDto(
    val requests: Int? = null,
    val interval: Int? = null,
)
@Serializable
data class GenerationEnvelopeDto(val data: GenerationDto? = null)
@Serializable
data class GenerationDto(
    val id: String? = null,
    val model: String? = null,
    val provider_name: String? = null,
    val total_cost: Double? = null,
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val latency: Double? = null,
    val generation_time: Double? = null,
)
@Serializable
data class OAuthExchangeRequest(
    val code: String,
    val code_verifier: String,
    val code_challenge_method: String,
)
@Serializable
data class OAuthExchangeResponse(val key: String? = null)
@Serializable
data class RankingsEnvelopeDto(val data: List<RankingRowDto> = emptyList())
@Serializable
data class RankingRowDto(
    val date: String? = null,
    val model_permaslug: String? = null,
    val total_tokens: String? = null,
)
