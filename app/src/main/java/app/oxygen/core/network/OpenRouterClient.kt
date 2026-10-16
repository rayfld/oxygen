package app.oxygen.core.network
import app.oxygen.core.model.Citation
import app.oxygen.core.model.ChatEvent
import app.oxygen.core.model.TokenUsage
import app.oxygen.core.util.arr
import app.oxygen.core.util.int
import app.oxygen.core.util.json
import app.oxygen.core.util.obj
import app.oxygen.core.util.parseJsonObjectOrNull
import app.oxygen.core.util.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
class ApiException(
    message: String,
    val code: Int,
    val detail: String? = null,
) : Exception(message)
fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> when (code) {
        401 -> "Invalid API key. Check it in Settings → OpenRouter account."
        402 -> "Insufficient credits on your OpenRouter account."
        403 -> "This key is not allowed to make this request."
        429 -> "Rate limited by OpenRouter. Try again shortly."
        502, 503 -> "The provider had a problem serving this model. Try again or switch models."
        else -> message ?: "Request failed ($code)"
    }
    is IOException -> "Network error. Check your connection and try again."
    else -> message ?: "Something went wrong"
}
class OpenRouterClient(
    baseUrl: String = "https://openrouter.ai/api/v1",
    httpClient: OkHttpClient = defaultHttpClient,
) {
    private val base = baseUrl.trimEnd('/')
    private val http = httpClient
    private val jsonBody = "application/json; charset=utf-8".toMediaType()
    companion object {
        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private fun request(url: String, apiKey: String, method: String, body: JsonObject? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("X-OpenRouter-Title", "Oxygen")
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        if (body != null) {
            builder.method(method, body.toString().toRequestBody(jsonBody))
        } else {
            builder.method(method, null)
        }
        return builder.build()
    }
    private suspend fun executeJson(apiKey: String, url: String, method: String = "GET", body: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val call = http.newCall(request(url, apiKey, method, body))
            try {
                call.execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw apiExceptionFor(response.code, text)
                    text
                }
            } catch (e: IOException) {
                if (!currentCoroutineContext().isActive) throw CancellationException()
                throw e
            }
        }
    suspend fun listModels(apiKey: String): List<ModelDto> =
        json.decodeFromString(ModelsEnvelopeDto.serializer(), executeJson(apiKey, "$base/models")).data
    suspend fun listModelsRaw(apiKey: String): String = executeJson(apiKey, "$base/models")
    suspend fun listEndpointsRaw(apiKey: String, author: String, slug: String): String =
        executeJson(apiKey, "$base/models/$author/$slug/endpoints")
    suspend fun getRankingsDaily(apiKey: String): List<RankingRowDto> =
        json.decodeFromString(
            RankingsEnvelopeDto.serializer(),
            executeJson(apiKey, "$base/datasets/rankings-daily"),
        ).data
    suspend fun listEndpoints(apiKey: String, author: String, slug: String): List<EndpointDto> {
        val text = executeJson(apiKey, "$base/models/$author/$slug/endpoints")
        return json.decodeFromString(EndpointsEnvelopeDto.serializer(), text).data?.endpoints ?: emptyList()
    }
    suspend fun listProviders(apiKey: String): List<ProviderDto> =
        json.decodeFromString(ProvidersEnvelopeDto.serializer(), executeJson(apiKey, "$base/providers")).data
    suspend fun getCredits(apiKey: String): CreditsDto? =
        json.decodeFromString(CreditsEnvelopeDto.serializer(), executeJson(apiKey, "$base/credits")).data
    suspend fun getKeyInfo(apiKey: String): KeyDto? =
        json.decodeFromString(KeyEnvelopeDto.serializer(), executeJson(apiKey, "$base/key")).data
    suspend fun getGeneration(apiKey: String, generationId: String): GenerationDto? =
        json.decodeFromString(
            GenerationEnvelopeDto.serializer(),
            executeJson(apiKey, "$base/generation?id=$generationId"),
        ).data
    suspend fun chatCompletion(apiKey: String, body: JsonObject): StreamChunkDto {
        val text = executeJson(apiKey, "$base/chat/completions", method = "POST", body = body)
        return json.decodeFromString(StreamChunkDto.serializer(), text)
    }
    suspend fun completeText(apiKey: String, body: JsonObject): String {
        val response = chatCompletion(apiKey, body)
        return response.choices.firstOrNull()?.message?.content.orEmpty()
    }
    suspend fun exchangeOAuthCode(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val payload = OAuthExchangeRequest(code = code, code_verifier = verifier, code_challenge_method = "S256")
        val body = json.encodeToJsonElement(OAuthExchangeRequest.serializer(), payload) as JsonObject
        val call = http.newCall(
            Request.Builder()
                .url("$base/auth/keys")
                .post(body.toString().toRequestBody(jsonBody))
                .build(),
        )
        call.execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiExceptionFor(response.code, text)
            val key = json.decodeFromString(OAuthExchangeResponse.serializer(), text).key
            key ?: throw ApiException("OpenRouter did not return an API key", 500)
        }
    }
    fun streamChat(apiKey: String, body: JsonObject): Flow<ChatEvent> = flow {
        val request = request("$base/chat/completions", apiKey, "POST", body)
        val call = http.newCall(request)
        var terminated = false
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw apiExceptionFor(response.code, text)
            }
            val generationId = response.header("X-Generation-Id")
            emit(ChatEvent.Meta(generationId))
            val source = response.body?.source() ?: throw ApiException("Empty response body", 500)
            val parser = SseParser()
            var lastModel: String? = null
            var lastProvider: String? = null
            var lastFinish: String? = null
            var lastNativeFinish: String? = null
            while (currentCoroutineContext().isActive) {
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    throw ApiException("Connection lost while streaming", 0, e.message)
                } ?: break
                when (val out = parser.feed(line)) {
                    null -> Unit
                    SseParser.Out.Comment -> emit(ChatEvent.KeepAlive)
                    is SseParser.Out.Message -> {
                        val data = out.data
                        if (data == "[DONE]") {
                            terminated = true
                            break
                        }
                        val chunk = try {
                            json.decodeFromString(StreamChunkDto.serializer(), data)
                        } catch (_: Exception) {
                            continue
                        }
                        lastModel = chunk.model ?: lastModel
                        lastProvider = chunk.provider ?: lastProvider
                        chunk.error?.let { err ->
                            emit(ChatEvent.Failed(err.code.int(), err.message ?: "Stream failed"))
                        }
                        for (choice in chunk.choices) {
                            choice.error?.let { err ->
                                emit(ChatEvent.Failed(err.code.int(), err.message ?: "Stream failed"))
                            }
                            val delta = choice.delta
                            if (delta != null) {
                                delta.content?.takeIf { it.isNotEmpty() }?.let { emit(ChatEvent.Text(it)) }
                                val reasoning = delta.reasoning?.takeIf { it.isNotEmpty() }
                                if (reasoning != null) {
                                    emit(ChatEvent.Reasoning(reasoning))
                                } else {
                                    val detailText = delta.reasoning_details
                                        ?.mapNotNull { d -> (d.text ?: d.summary)?.takeIf { it.isNotEmpty() } }
                                        ?.joinToString("")
                                        .orEmpty()
                                    if (detailText.isNotEmpty()) emit(ChatEvent.Reasoning(detailText))
                                }
                                delta.tool_calls?.takeIf { it.isNotEmpty() }?.let { emit(ChatEvent.ToolCalls(it)) }
                                delta.images?.takeIf { it.isNotEmpty() }?.let { images ->
                                    val urls = images.mapNotNull { img ->
                                        img.obj("image_url")?.str("url")
                                            ?: img.str("url")
                                            ?: img.str("b64_json")?.let { "data:image/png;base64,$it" }
                                    }
                                    if (urls.isNotEmpty()) emit(ChatEvent.Images(urls))
                                }
                                delta.annotations?.takeIf { it.isNotEmpty() }?.let { anns ->
                                    val citations = parseCitations(anns)
                                    if (citations.isNotEmpty()) emit(ChatEvent.Citations(citations))
                                }
                            }
                            if (choice.finish_reason != null) lastFinish = choice.finish_reason
                            if (choice.native_finish_reason != null) lastNativeFinish = choice.native_finish_reason
                        }
                        chunk.usage?.let { u ->
                            emit(
                                ChatEvent.Usage(
                                    usage = TokenUsage(
                                        promptTokens = u.prompt_tokens ?: 0,
                                        completionTokens = u.completion_tokens ?: 0,
                                        totalTokens = u.total_tokens ?: ((u.prompt_tokens ?: 0) + (u.completion_tokens ?: 0)),
                                        cachedTokens = u.prompt_tokens_details?.cached_tokens ?: 0,
                                        reasoningTokens = u.completion_tokens_details?.reasoning_tokens ?: 0,
                                        cost = u.cost,
                                    ),
                                    model = lastModel,
                                    provider = lastProvider,
                                ),
                            )
                        }
                    }
                }
            }
            emit(
                ChatEvent.Done(
                    finishReason = lastFinish,
                    nativeFinishReason = lastNativeFinish,
                    model = lastModel,
                    provider = lastProvider,
                ),
            )
            if (!terminated) {
            }
        } catch (e: IOException) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            throw ApiException("Connection lost while streaming", 0, e.message)
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
fun apiExceptionFor(httpCode: Int, bodyText: String): ApiException {
    val envelope = try {
        json.decodeFromString(ApiErrorEnvelopeDto.serializer(), bodyText)
    } catch (_: Exception) {
        null
    }
    val info = envelope?.error
    val code = info?.code.int() ?: httpCode
    val message = info?.message ?: "HTTP $httpCode"
    val meta = info?.metadata?.let { m ->
        parseJsonObjectOrNull(m.toString())?.get("raw")?.str()
    }
    return ApiException(message, code, meta)
}
private fun parseCitations(raw: List<JsonObject>): List<Citation> {
    val out = ArrayList<Citation>(raw.size)
    for (item in raw) {
        val body = item.obj("url_citation") ?: item
        val url = body.str("url") ?: continue
        val title = body.str("title")
        val content = body.str("content")
        out += Citation(url = url, title = title, content = content)
    }
    return out
}
fun parseCitationsLoose(raw: JsonObject?): List<Citation> {
    val arr = raw?.get("annotations")?.arr() ?: return emptyList()
    val out = ArrayList<Citation>()
    for (el in arr) {
        val obj = el.obj() ?: continue
        val body = obj.obj("url_citation") ?: obj
        val url = body.str("url") ?: continue
        out += Citation(url = url, title = body.str("title"), content = body.str("content"))
    }
    return out
}
