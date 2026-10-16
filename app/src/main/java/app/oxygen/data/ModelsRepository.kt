package app.oxygen.data
import android.content.Context
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ModelEndpoint
import app.oxygen.core.model.ProviderInfo
import app.oxygen.core.network.EndpointDto
import app.oxygen.core.network.EndpointsEnvelopeDto
import app.oxygen.core.network.ModelDto
import app.oxygen.core.network.ModelsEnvelopeDto
import app.oxygen.core.network.OpenRouterClient
import app.oxygen.core.util.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import app.oxygen.core.util.arr
import app.oxygen.core.util.dbl
import app.oxygen.core.util.int
import app.oxygen.core.util.long
import app.oxygen.core.util.obj
import app.oxygen.core.util.str
import java.io.File
class ModelsRepository(
    private val context: Context,
    private val client: OpenRouterClient,
) {
    private val cacheFile = File(context.filesDir, "models_cache.json")
    private val mutex = Mutex()
    @Volatile private var loaded: List<ChatModel>? = null
    @Volatile private var fetchedAt: Long = 0L
    private val _catalogFlow = MutableStateFlow<List<ChatModel>>(emptyList())
    val catalogFlow: StateFlow<List<ChatModel>> = _catalogFlow.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private val endpointCache = mutableMapOf<String, Pair<Long, List<ModelEndpoint>>>()
    private var providerCache: Pair<Long, List<ProviderInfo>>? = null
    val models: List<ChatModel> get() = loaded ?: emptyList()
    val isLoaded: Boolean get() = loaded != null
    suspend fun ensureLoaded(apiKey: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded == null) {
                loaded = readCache() ?: emptyList()
                fetchedAt = cacheFile.lastModified()
            }
            _catalogFlow.value = models
            val stale = System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS
            if ((stale || models.isEmpty()) && !_loading.value) {
                _loading.value = true
                try {
                    runCatching { refreshLocked(apiKey.orEmpty()) }
                        .onSuccess { _lastError.value = null }
                        .onFailure { _lastError.value = it.message ?: it::class.simpleName ?: "Refresh failed" }
                } finally {
                    _loading.value = false
                }
            }
        }
    }
    suspend fun retry(apiKey: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _loading.value = true
            _lastError.value = null
            try {
                runCatching { refreshLocked(apiKey.orEmpty()) }
                    .onFailure { _lastError.value = it.message ?: it::class.simpleName ?: "Refresh failed" }
            } finally {
                _loading.value = false
            }
        }
    }
    suspend fun refresh(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { refreshLocked(apiKey) }
        }
    }
    private suspend fun refreshLocked(apiKey: String) {
        val text = client.listModelsRaw(apiKey)
        var mapped = decodeModels(text)
        if (mapped.isEmpty()) {
            throw IllegalStateException("Model list came back empty")
        }
        if (apiKey.isNotBlank()) {
            val scores = runCatching { fetchPopularityScores(apiKey) }.getOrDefault(emptyMap())
            if (scores.isNotEmpty()) {
                mapped = mapped.map { model ->
                    val idKey = model.id.removePrefix("~")
                    val score = scores[idKey]
                        ?: model.canonicalSlug?.let { scores[it.removePrefix("~")] }
                        ?: scores[idKey.substringBefore(':')]
                    if (score != null) model.copy(popularityScore = score) else model
                }
            }
        }
        loaded = mapped
        fetchedAt = System.currentTimeMillis()
        _catalogFlow.value = mapped
        writeCache(mapped)
    }
    private suspend fun fetchPopularityScores(apiKey: String): Map<String, Long> {
        val rows = client.getRankingsDaily(apiKey)
        if (rows.isEmpty()) return emptyMap()
        val dates = rows.mapNotNull { it.date }.distinct().sorted().takeLast(7).toSet()
        if (dates.isEmpty()) return emptyMap()
        val scores = HashMap<String, Long>()
        rows.forEach { row ->
            if (row.date !in dates) return@forEach
            val slug = row.model_permaslug ?: return@forEach
            if (slug == "other") return@forEach
            val tokens = row.total_tokens?.toLongOrNull() ?: return@forEach
            val keys = LinkedHashSet<String>()
            keys.add(slug)
            keys.add(slug.substringBefore(':').removePrefix("~"))
            if (DATE_SUFFIX.containsMatchIn(slug)) keys.add(slug.substringBeforeLast('-'))
            keys.forEach { key -> scores[key] = (scores[key] ?: 0L) + tokens }
        }
        return scores
    }
    fun byId(id: String): ChatModel = models.firstOrNull { it.id == id }
        ?: ChatModel(
            id = id,
            name = id,
            author = id.substringBefore('/'),
            description = null,
            contextLength = null,
            createdAt = 0L,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            supportedParams = emptySet(),
            promptPrice = null,
            completionPrice = null,
            cacheReadPrice = null,
            cacheWritePrice = null,
            isFree = id.endsWith(":free"),
        )
    suspend fun endpoints(apiKey: String, modelId: String): Result<List<ModelEndpoint>> {
        endpointCache[modelId]?.let { (at, cached) ->
            if (System.currentTimeMillis() - at < ENDPOINT_TTL_MS) return Result.success(cached)
        }
        val author = modelId.substringBefore('/').removePrefix("~")
        val slug = modelId.substringAfter('/').substringBefore(':')
        return runCatching {
            decodeEndpoints(client.listEndpointsRaw(apiKey, author, slug))
        }.onSuccess { result ->
            endpointCache[modelId] = System.currentTimeMillis() to result
        }
    }
    suspend fun providers(apiKey: String): Result<List<ProviderInfo>> {
        providerCache?.let { (at, cached) ->
            if (System.currentTimeMillis() - at < ENDPOINT_TTL_MS) return Result.success(cached)
        }
        return runCatching { client.listProviders(apiKey) }
            .map { list ->
                list.mapNotNull { dto ->
                    val slug = dto.slug ?: dto.name?.lowercase()?.replace(" ", "-") ?: return@mapNotNull null
                    ProviderInfo(slug = slug, name = dto.name ?: slug)
                }
            }
            .onSuccess { result ->
                providerCache = System.currentTimeMillis() to result
            }
    }
    private fun readCache(): List<ChatModel>? = runCatching {
        if (!cacheFile.exists()) return null
        json.decodeFromString(ListSerializer(ChatModel.serializer()), cacheFile.readText())
    }.getOrNull()
    private fun writeCache(models: List<ChatModel>) {
        runCatching {
            cacheFile.writeText(json.encodeToString(ListSerializer(ChatModel.serializer()), models))
        }
    }
    private companion object {
        const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        const val ENDPOINT_TTL_MS = 10 * 60 * 1000L
        val DATE_SUFFIX = Regex("-\\d{8}$")
    }
}
internal fun decodeModels(text: String): List<ChatModel> {
    runCatching {
        json.decodeFromString(ModelsEnvelopeDto.serializer(), text).data
    }.onSuccess { dtos ->
        if (dtos.isNotEmpty()) {
            val mapped = dtos.mapNotNull { it.toDomain() }
            if (mapped.isNotEmpty()) return mapped
        }
    }
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
    val array = root["data"]?.arr() ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element.obj() ?: return@mapNotNull null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val pricing = obj.obj("pricing")
        val architecture = obj.obj("architecture")
        val topProvider = obj.obj("top_provider")
        val prompt = pricing.str("prompt")?.toDoubleOrNull()
        val completion = pricing.str("completion")?.toDoubleOrNull()
        ChatModel(
            id = id,
            name = obj.str("name")?.takeIf { it.isNotBlank() } ?: id,
            author = id.substringBefore('/'),
            description = obj.str("description"),
            contextLength = topProvider.int("context_length") ?: obj.int("context_length"),
            createdAt = obj["created"].long() ?: 0L,
            inputModalities = architecture.arr("input_modalities")
                ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: listOf("text"),
            outputModalities = architecture.arr("output_modalities")
                ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: listOf("text"),
            supportedParams = obj.arr("supported_parameters")
                ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet() ?: emptySet(),
            promptPrice = prompt,
            completionPrice = completion,
            cacheReadPrice = pricing.str("input_cache_read")?.toDoubleOrNull(),
            cacheWritePrice = pricing.str("input_cache_write")?.toDoubleOrNull(),
            isFree = prompt == 0.0 && completion == 0.0,
        )
    }
}
internal fun decodeEndpoints(text: String): List<ModelEndpoint> {
    runCatching {
        json.decodeFromString(EndpointsEnvelopeDto.serializer(), text).data?.endpoints
    }.onSuccess { dtos ->
        if (dtos != null && dtos.isNotEmpty()) return dtos.map { it.toEndpoint() }
    }
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
    val array = root.obj("data")?.arr("endpoints") ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element.obj() ?: return@mapNotNull null
        val pricing = obj.obj("pricing")
        ModelEndpoint(
            providerSlug = (obj.str("provider_name") ?: obj.str("name") ?: "unknown").lowercase().replace(" ", "-"),
            providerName = obj.str("provider_name") ?: obj.str("name") ?: "Unknown",
            quantization = obj.str("quantization"),
            contextLength = obj.int("context_length"),
            promptPrice = pricing.str("prompt")?.toDoubleOrNull(),
            completionPrice = pricing.str("completion")?.toDoubleOrNull(),
            latencyMs = obj.dbl("latency_last_30m") ?: obj.str("latency_last_30m")?.toDoubleOrNull(),
            throughput = obj.dbl("throughput_last_30m") ?: obj.str("throughput_last_30m")?.toDoubleOrNull(),
            uptime = obj.dbl("uptime_last_30m") ?: obj.str("uptime_last_30m")?.toDoubleOrNull(),
            status = obj.int("status"),
        )
    }
}
private fun EndpointDto.toEndpoint(): ModelEndpoint = ModelEndpoint(
    providerSlug = provider_name?.lowercase()?.replace(" ", "-") ?: name.orEmpty(),
    providerName = provider_name ?: name ?: "Unknown",
    quantization = quantization,
    contextLength = context_length,
    promptPrice = pricing?.prompt?.toDoubleOrNull(),
    completionPrice = pricing?.completion?.toDoubleOrNull(),
    latencyMs = latency_last_30m,
    throughput = throughput_last_30m,
    uptime = uptime_last_30m,
    status = status,
)
fun ModelDto.toDomain(): ChatModel? {
    val output = architecture?.output_modalities ?: listOf("text")
    val promptPrice = pricing?.prompt?.toDoubleOrNull()
    val completionPrice = pricing?.completion?.toDoubleOrNull()
    return ChatModel(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: id,
        author = id.substringBefore('/'),
        description = description,
        contextLength = top_provider?.context_length ?: context_length,
        createdAt = created ?: 0L,
        inputModalities = architecture?.input_modalities ?: listOf("text"),
        outputModalities = output,
        supportedParams = (supported_parameters ?: emptyList()).toSet(),
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        cacheReadPrice = pricing?.input_cache_read?.toDoubleOrNull(),
        cacheWritePrice = pricing?.input_cache_write?.toDoubleOrNull(),
        isFree = promptPrice == 0.0 && completionPrice == 0.0,
    )
}
