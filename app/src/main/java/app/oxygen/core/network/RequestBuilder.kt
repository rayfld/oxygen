package app.oxygen.core.network
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.Role
import app.oxygen.core.tools.Tools
import app.oxygen.core.util.json
import app.oxygen.core.util.parseJsonElementOrNull
import app.oxygen.core.util.parseJsonObjectOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
object RequestBuilder {
    fun build(
        chain: List<ChatMessage>,
        systemPrompt: String?,
        modelId: String,
        params: ChatParams,
        attachmentData: Map<String, String> = emptyMap(),
        memories: List<String> = emptyList(),
        includeTools: Boolean = true,
        stream: Boolean = true,
    ): JsonObject {
        val systemContent = buildString {
            if (!systemPrompt.isNullOrBlank()) append(systemPrompt.trim())
            if (memories.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("User memory:\n")
                memories.forEach { append("- ").append(it).append('\n') }
            }
        }.trim()
        val messages = buildList {
            if (systemContent.isNotEmpty()) {
                add(RequestMessageDto(role = "system", content = JsonPrimitive(systemContent)))
            }
            for (message in chain) {
                when (message.role) {
                    Role.SYSTEM -> add(RequestMessageDto(role = "system", content = JsonPrimitive(message.content)))
                    Role.USER -> add(userMessage(message, attachmentData))
                    Role.ASSISTANT -> add(assistantMessage(message))
                    Role.TOOL -> add(
                        RequestMessageDto(
                            role = "tool",
                            content = JsonPrimitive(message.content),
                            tool_call_id = message.toolCallId,
                        ),
                    )
                }
            }
        }
        val useFallbacks = params.fallbackModels.isNotEmpty()
        val dto = ChatRequestDto(
            model = if (useFallbacks) null else modelId,
            models = if (useFallbacks) listOf(modelId) + params.fallbackModels else null,
            messages = messages,
            stream = stream,
            max_tokens = params.maxTokens,
            temperature = params.temperature,
            top_p = params.topP,
            top_k = params.topK,
            min_p = params.minP,
            top_a = params.topA,
            frequency_penalty = params.frequencyPenalty,
            presence_penalty = params.presencePenalty,
            repetition_penalty = params.repetitionPenalty,
            seed = params.seed,
            stop = params.stop?.takeIf { it.isNotEmpty() },
            logprobs = params.logprobs,
            top_logprobs = params.topLogprobs,
            response_format = buildResponseFormat(params),
            tools = if (includeTools) buildTools(params) else null,
            max_tool_calls = buildMaxToolCalls(params, includeTools),
            reasoning = buildReasoning(params),
            plugins = buildPlugins(params),
            provider = buildProvider(params),
            transforms = if (params.middleOut == true || params.contextStrategy == "midout") listOf("middle-out") else null,
        )
        var body = json.encodeToJsonElement(ChatRequestDto.serializer(), dto) as JsonObject
        params.extraBody?.let { extra ->
            parseJsonObjectOrNull(extra)?.let { body = body.deepMergeLocal(it) }
        }
        return body
    }
    private fun JsonObject.deepMergeLocal(overlay: JsonObject): JsonObject {
        val result = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>(this)
        for ((key, value) in overlay) {
            val existing = result[key]
            result[key] = if (existing is JsonObject && value is JsonObject) {
                existing.deepMergeLocal(value)
            } else {
                value
            }
        }
        return JsonObject(result)
    }
    private fun userMessage(message: ChatMessage, attachmentData: Map<String, String>): RequestMessageDto {
        if (message.attachments.isEmpty()) {
            return RequestMessageDto(role = "user", content = JsonPrimitive(message.content))
        }
        val parts = buildJsonArray {
            if (message.content.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", message.content)
                })
            }
            for (attachment in message.attachments) {
                val dataUrl = attachmentData[attachment.id] ?: continue
                when (attachment.kind) {
                    app.oxygen.core.model.Attachment.Kind.IMAGE -> add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", dataUrl) })
                    })
                    app.oxygen.core.model.Attachment.Kind.PDF -> add(buildJsonObject {
                        put("type", "file")
                        put(
                            "file",
                            buildJsonObject {
                                put("filename", attachment.name ?: "document.pdf")
                                put("file_data", dataUrl)
                            },
                        )
                    })
                }
            }
        }
        return RequestMessageDto(role = "user", content = parts)
    }
    private fun assistantMessage(message: ChatMessage): RequestMessageDto {
        val content: kotlinx.serialization.json.JsonElement =
            if (message.content.isBlank()) JsonNull else JsonPrimitive(message.content)
        val toolCalls = message.toolCalls.takeIf { it.isNotEmpty() }?.map { call ->
            buildJsonObject {
                put("id", call.id)
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", call.name)
                        put("arguments", call.arguments)
                    },
                )
            }
        }
        return RequestMessageDto(role = "assistant", content = content, tool_calls = toolCalls)
    }
    private fun buildReasoning(params: ChatParams): JsonObject? {
        val effort = params.reasoningEffort
        val maxTokens = params.reasoningMaxTokens
        val exclude = params.reasoningExclude
        if (effort == null && maxTokens == null && exclude == null) return null
        return buildJsonObject {
            effort?.let { put("effort", it) }
            maxTokens?.let { put("max_tokens", it) }
            exclude?.let { put("exclude", it) }
        }
    }
    private fun buildPlugins(params: ChatParams): List<JsonObject>? {
        val plugins = buildList {
            if (params.webSearch == true && params.serverTools.isEmpty() && params.tools.isEmpty()) {
                add(buildJsonObject {
                    put("id", "web")
                    params.webSearchMaxResults?.let { put("max_results", it) }
                    params.webSearchPrompt?.takeIf { it.isNotBlank() }?.let { put("search_prompt", it) }
                })
            }
            if (params.fileParser == true) add(buildJsonObject { put("id", "file-parser") })
            if (params.responseHealing == true) add(buildJsonObject { put("id", "response-healing") })
        }
        return plugins.takeIf { it.isNotEmpty() }
    }
    private fun buildMaxToolCalls(params: ChatParams, includeTools: Boolean): Int? {
        if (!includeTools) return null
        val hasServerTools = params.tools.any { it.value == Tools.EXEC_SERVER } || params.serverTools.isNotEmpty()
        if (!hasServerTools) return null
        return params.maxToolCalls?.coerceIn(1, 30)
    }
    private fun buildTools(params: ChatParams): JsonElement? {
        val array = buildJsonArray {
            Tools.ALL.forEach { spec ->
                val executor = params.tools[spec.id]
                    ?: if (spec.serverId != null && spec.serverId in params.serverTools) {
                        Tools.EXEC_SERVER
                    } else {
                        null
                    }
                when (executor) {
                    Tools.EXEC_SERVER -> {
                        val serverId = spec.serverId ?: return@forEach
                        add(buildJsonObject {
                            put("type", serverId)
                            if (spec.id == "web_search") {
                                val maxResults = params.webSearchMaxResults
                                val prompt = params.webSearchPrompt?.takeIf { it.isNotBlank() }
                                if (maxResults != null || prompt != null) {
                                    put(
                                        "parameters",
                                        buildJsonObject {
                                            maxResults?.let { put("max_results", it) }
                                            prompt?.let { put("search_prompt", it) }
                                        },
                                    )
                                }
                            }
                        })
                    }
                    Tools.EXEC_LOCAL -> {
                        if (!spec.local) return@forEach
                        add(buildJsonObject {
                            put("type", "function")
                            put(
                                "function",
                                buildJsonObject {
                                    put("name", spec.id)
                                    put("description", spec.description)
                                    put("parameters", spec.parameters)
                                },
                            )
                        })
                    }
                    else -> Unit
                }
            }
        }
        return array.takeIf { it.isNotEmpty() }
    }
    private fun buildProvider(params: ChatParams): JsonObject? {
        val p = params.provider
        if (p.isEmpty) return null
        return buildJsonObject {
            p.order.takeIf { it.isNotEmpty() }?.let { put("order", JsonArray(it.map { s -> JsonPrimitive(s) })) }
            p.only.takeIf { it.isNotEmpty() }?.let { put("only", JsonArray(it.map { s -> JsonPrimitive(s) })) }
            p.ignore.takeIf { it.isNotEmpty() }?.let { put("ignore", JsonArray(it.map { s -> JsonPrimitive(s) })) }
            p.quantizations.takeIf { it.isNotEmpty() }?.let { put("quantizations", JsonArray(it.map { s -> JsonPrimitive(s) })) }
            p.allowFallbacks?.let { put("allow_fallbacks", it) }
            p.sort?.let { put("sort", it) }
            p.dataCollection?.let { put("data_collection", it) }
            p.zdr?.let { put("zdr", it) }
            p.requireParameters?.let { put("require_parameters", it) }
            if (p.maxPricePrompt != null || p.maxPriceCompletion != null) {
                put(
                    "max_price",
                    buildJsonObject {
                        p.maxPricePrompt?.let { put("prompt", it) }
                        p.maxPriceCompletion?.let { put("completion", it) }
                    },
                )
            }
        }
    }
    private fun buildResponseFormat(params: ChatParams): kotlinx.serialization.json.JsonElement? {
        return when (params.responseFormat) {
            "json_object" -> buildJsonObject { put("type", "json_object") }
            "json_schema" -> {
                val schema = parseJsonElementOrNull(params.jsonSchema)
                val schemaJson: JsonObject = when {
                    schema is JsonObject && schema.containsKey("schema") -> schema
                    schema is JsonObject -> buildJsonObject {
                        put("name", "oxygen_response")
                        put("strict", true)
                        put("schema", schema)
                    }
                    else -> return buildJsonObject { put("type", "json_object") }
                }
                buildJsonObject {
                    put("type", "json_schema")
                    put("json_schema", schemaJson)
                }
            }
            else -> null
        }
    }
}
