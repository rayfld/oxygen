package app.oxygen.data
import android.content.Context
import app.oxygen.core.db.GenerationEntity
import app.oxygen.core.model.ChatEvent
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.Citation
import app.oxygen.core.model.Conversation
import app.oxygen.core.model.MessageStatus
import app.oxygen.core.model.Role
import app.oxygen.core.model.TokenUsage
import app.oxygen.core.model.ToolCall
import app.oxygen.core.model.TraceStep
import app.oxygen.core.network.ApiException
import app.oxygen.core.network.OpenRouterClient
import app.oxygen.core.network.RequestBuilder
import app.oxygen.core.network.ToolCallDeltaDto
import app.oxygen.core.network.friendlyMessage
import app.oxygen.core.tools.LocalToolExecutor
import app.oxygen.core.tools.Tools
import app.oxygen.core.util.arr
import app.oxygen.core.util.dbl
import app.oxygen.core.util.int
import app.oxygen.core.util.newId
import app.oxygen.core.util.now
import app.oxygen.core.util.obj
import app.oxygen.core.util.parseJsonObjectOrNull
import app.oxygen.core.util.str
import app.oxygen.service.GenerationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
class GenerationManager(
    private val context: Context,
    private val chat: ChatRepository,
    private val client: OpenRouterClient,
    private val settings: SettingsRepository,
    private val attachments: AttachmentStore,
    private val account: AccountRepository,
    private val models: ModelsRepository,
) {
    enum class Phase { CONNECTING, WAITING, REASONING, STREAMING, ERROR }
    data class UiToolCall(
        val id: String,
        val name: String,
        val arguments: String,
    )
    data class StreamState(
        val conversationId: String,
        val messageId: String,
        val modelId: String,
        val content: String = "",
        val reasoning: String = "",
        val toolCalls: List<UiToolCall> = emptyList(),
        val citations: List<Citation> = emptyList(),
        val trace: List<TraceStep> = emptyList(),
        val images: List<String> = emptyList(),
        val imageGeneration: Boolean = false,
        val phase: Phase = Phase.CONNECTING,
        val error: String? = null,
        val startedAt: Long = now(),
        val firstTokenAt: Long? = null,
        val usage: TokenUsage? = null,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _streams = MutableStateFlow<Map<String, StreamState>>(emptyMap())
    val streams: StateFlow<Map<String, StreamState>> = _streams.asStateFlow()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val accumulators = ConcurrentHashMap<String, StreamAccumulator>()
    private val MAX_TOOL_ITERATIONS = 4
    init {
        scope.launch {
            Foreground.isForeground.collect { foreground ->
                if (_streams.value.isEmpty()) return@collect
                if (foreground) {
                    GenerationService.stop(context)
                } else {
                    GenerationService.start(context, _streams.value.keys.firstOrNull())
                }
            }
        }
    }
    fun isStreaming(conversationId: String): Boolean = _streams.value.containsKey(conversationId)
    val hasActiveStreams: Boolean get() = _streams.value.isNotEmpty()
    suspend fun recoverInterrupted() {
        runCatching { chat.markInterrupted() }
        runCatching { chat.deleteAllEphemeral() }
    }
    suspend fun sendMessage(conversationId: String, text: String, pending: List<app.oxygen.core.model.Attachment>) {
        if (isStreaming(conversationId)) return
        if (text.isBlank() && pending.isEmpty()) return
        val conversation = chat.getConversation(conversationId) ?: return
        val userMessage = ChatMessage(
            id = newId(),
            conversationId = conversationId,
            parentId = conversation.currentLeafId,
            role = Role.USER,
            content = text.trim(),
            attachments = pending,
            createdAt = now(),
        )
        chat.upsertMessage(userMessage, indexForSearch = userMessage.content.isNotBlank())
        val assistant = ChatMessage(
            id = newId(),
            conversationId = conversationId,
            parentId = userMessage.id,
            role = Role.ASSISTANT,
            content = "",
            modelId = conversation.modelId,
            status = MessageStatus.STREAMING,
            createdAt = now(),
        )
        chat.upsertMessage(assistant)
        chat.setLeaf(conversationId, assistant.id)
        chat.touch(conversationId)
        startStream(conversation, assistant.id, leafForChain = userMessage.id)
    }
    suspend fun regenerate(conversationId: String, assistantMessageId: String, modelOverride: String? = null) {
        if (isStreaming(conversationId)) return
        val conversation = chat.getConversation(conversationId) ?: return
        val target = chat.getMessage(assistantMessageId) ?: return
        if (target.role != Role.ASSISTANT) return
        val assistant = ChatMessage(
            id = newId(),
            conversationId = conversationId,
            parentId = target.parentId,
            role = Role.ASSISTANT,
            content = "",
            modelId = modelOverride ?: conversation.modelId,
            status = MessageStatus.STREAMING,
            createdAt = now(),
        )
        chat.upsertMessage(assistant)
        chat.setLeaf(conversationId, assistant.id)
        startStream(conversation, assistant.id, leafForChain = target.parentId)
    }
    suspend fun editUserMessage(conversationId: String, userMessageId: String, newText: String) {
        if (isStreaming(conversationId)) return
        val conversation = chat.getConversation(conversationId) ?: return
        val target = chat.getMessage(userMessageId) ?: return
        if (target.role != Role.USER) return
        val edited = target.copy(
            id = newId(),
            parentId = target.parentId,
            content = newText.trim(),
            createdAt = now(),
        )
        chat.upsertMessage(edited, indexForSearch = edited.content.isNotBlank())
        val assistant = ChatMessage(
            id = newId(),
            conversationId = conversationId,
            parentId = edited.id,
            role = Role.ASSISTANT,
            content = "",
            modelId = conversation.modelId,
            status = MessageStatus.STREAMING,
            createdAt = now(),
        )
        chat.upsertMessage(assistant)
        chat.setLeaf(conversationId, assistant.id)
        startStream(conversation, assistant.id, leafForChain = edited.id)
    }
    suspend fun continueGeneration(conversationId: String, assistantMessageId: String) {
        if (isStreaming(conversationId)) return
        val conversation = chat.getConversation(conversationId) ?: return
        val target = chat.getMessage(assistantMessageId) ?: return
        if (target.role != Role.ASSISTANT || target.content.isBlank()) return
        val assistant = target.copy(
            id = newId(),
            parentId = target.id,
            content = "",
            reasoning = null,
            toolCalls = emptyList(),
            citations = emptyList(),
            status = MessageStatus.STREAMING,
            error = null,
            finishReason = null,
            generationId = null,
            promptTokens = null,
            completionTokens = null,
            cachedTokens = null,
            reasoningTokens = null,
            costUsd = null,
            latencyMs = null,
            createdAt = now(),
        )
        chat.upsertMessage(assistant)
        chat.setLeaf(conversationId, assistant.id)
        startStream(conversation, assistant.id, leafForChain = target.id)
    }
    fun stop(conversationId: String) {
        jobs[conversationId]?.cancel()
    }
    private suspend fun startStream(conversation: Conversation, assistantMessageId: String, leafForChain: String?) {
        val apiKey = settings.activeKeySecret()
        if (apiKey == null) {
            chat.setMessageStatus(
                assistantMessageId,
                MessageStatus.ERROR,
                "No API key yet. Add one in Settings.",
            )
            return
        }
        val accumulator = StreamAccumulator(conversation.id, assistantMessageId)
        accumulator.modelIdOverride = chat.getMessage(assistantMessageId)?.modelId
        accumulator.leafForChain = leafForChain
        val effectiveModel = accumulator.modelIdOverride ?: conversation.modelId
        val isImageModel = models.byId(effectiveModel).imageGeneration
        accumulator.imageGeneration = isImageModel
        accumulators[conversation.id] = accumulator
        publish(conversation, accumulator, Phase.CONNECTING)
        settings.markModelUsed(effectiveModel)
        val job = scope.launch {
            if (isImageModel) {
                runImageGeneration(conversation, accumulator, apiKey, effectiveModel)
            } else {
                runStream(conversation, accumulator, apiKey, buildBody(conversation, accumulator, effectiveModel))
            }
        }
        jobs[conversation.id] = job
    }
    private suspend fun runImageGeneration(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        apiKey: String,
        modelId: String,
    ) {
        val userMessage = chat.chain(conversation.id, accumulator.leafForChain)
            .lastOrNull { it.role == Role.USER }
        val prompt = userMessage?.content?.trim().orEmpty()
        if (prompt.isBlank()) {
            finalizeImageError(conversation, accumulator, "Describe the image you want first.")
            return
        }
        val references = userMessage?.attachments
            ?.filter { it.kind == app.oxygen.core.model.Attachment.Kind.IMAGE }
            ?.mapNotNull { attachments.toDataUrl(it) }
            .orEmpty()
        try {
            val body = RequestBuilder.buildImage(modelId, prompt, conversation.params, references)
            val raw = client.generateImage(apiKey, body)
            val root = parseJsonObjectOrNull(raw)
            val images = root?.arr("data") ?: emptyList()
            val saved = ArrayList<app.oxygen.core.model.Attachment>()
            for (element in images) {
                val obj = element.obj() ?: continue
                val b64 = obj.str("b64_json")?.takeIf { it.isNotBlank() } ?: continue
                val mime = obj.str("media_type")?.takeIf { it.isNotBlank() } ?: "image/png"
                attachments.attachmentFromUrl("data:$mime;base64,$b64")?.let { saved += it }
            }
            val usage = root?.obj("usage")
            val cost = usage?.dbl("cost")
            if (saved.isEmpty()) {
                finalizeImageError(conversation, accumulator, "The model did not return an image. Try again or pick another model.")
                return
            }
            withContext(NonCancellable) {
                val existing = chat.getMessage(accumulator.messageId) ?: placeholder(conversation, accumulator)
                val finalized = existing.copy(
                    content = "",
                    attachments = (existing.attachments) + saved,
                    modelId = modelId,
                    status = MessageStatus.COMPLETE,
                    error = null,
                    costUsd = cost,
                    promptTokens = usage?.int("prompt_tokens"),
                    completionTokens = usage?.int("completion_tokens"),
                    latencyMs = now() - accumulator.startedAt,
                )
                chat.upsertMessage(finalized)
                chat.addGeneration(
                    GenerationEntity(
                        id = newId(),
                        generationId = null,
                        conversationId = conversation.id,
                        messageId = finalized.id,
                        modelId = modelId,
                        provider = null,
                        costUsd = cost,
                        promptTokens = usage?.int("prompt_tokens"),
                        completionTokens = usage?.int("completion_tokens"),
                        cachedTokens = null,
                        reasoningTokens = null,
                        latencyMs = now() - accumulator.startedAt,
                        ttftMs = null,
                        createdAt = now(),
                    ),
                )
                chat.touch(conversation.id)
                finishUi(conversation.id)
                runCatching { account.refresh() }
                notifyFinished(conversation)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val existing = chat.getMessage(accumulator.messageId) ?: placeholder(conversation, accumulator)
                chat.upsertMessage(
                    existing.copy(status = MessageStatus.INTERRUPTED, modelId = modelId, latencyMs = now() - accumulator.startedAt),
                )
                chat.touch(conversation.id)
                finishUi(conversation.id)
            }
            throw e
        } catch (e: Exception) {
            finalizeImageError(conversation, accumulator, e.friendlyMessage())
        }
    }
    private suspend fun finalizeImageError(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        message: String,
    ) = withContext(NonCancellable) {
        val existing = chat.getMessage(accumulator.messageId) ?: placeholder(conversation, accumulator)
        chat.upsertMessage(
            existing.copy(
                status = MessageStatus.ERROR,
                error = message,
                latencyMs = now() - accumulator.startedAt,
            ),
        )
        chat.touch(conversation.id)
        finishUi(conversation.id)
        notifyFinished(conversation)
    }
    private suspend fun buildBody(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        modelId: String,
    ): kotlinx.serialization.json.JsonObject {
        var chain = chat.chain(conversation.id, accumulator.leafForChain)
        if (conversation.params.contextStrategy == "window") {
            val window = (conversation.params.contextWindow ?: 20).coerceAtLeast(2)
            if (chain.size > window) {
                var start = chain.size - window
                while (start < chain.size && chain[start].role == Role.TOOL) start++
                chain = chain.subList(start, chain.size)
            }
        }
        val attachmentData = HashMap<String, String>()
        chain.flatMap { it.attachments }.forEach { attachment ->
            attachments.toDataUrl(attachment)?.let { attachmentData[attachment.id] = it }
        }
        val supportsText = models.byId(modelId).let { "text" in it.outputModalities }
        return RequestBuilder.build(
            chain = chain,
            systemPrompt = conversation.systemPrompt,
            modelId = modelId,
            params = conversation.params,
            attachmentData = attachmentData,
            memories = settings.current().memories,
            includeTools = supportsText,
            stream = true,
        )
    }
    private suspend fun runStream(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        apiKey: String,
        initialBody: kotlinx.serialization.json.JsonObject,
    ) {
        var body = initialBody
        var failure: Throwable? = null
        var attempt = 0
        val localToolIds = conversation.params.tools
            .filterValues { it == Tools.EXEC_LOCAL }
            .keys
        while (true) {
            failure = null
            accumulator.startTurn()
            publish(conversation, accumulator, Phase.CONNECTING, force = true)
            try {
                client.streamChat(apiKey, body).collect { event ->
                    when (event) {
                        is ChatEvent.Meta -> accumulator.generationId = event.generationId
                        is ChatEvent.KeepAlive -> {
                            if (accumulator.content.isEmpty() && accumulator.reasoning.isEmpty()) {
                                publish(conversation, accumulator, Phase.WAITING)
                            }
                        }
                        is ChatEvent.Text -> {
                            if (accumulator.firstTokenAt == null) accumulator.firstTokenAt = now()
                            accumulator.content.append(event.delta)
                            publish(conversation, accumulator, Phase.STREAMING)
                        }
                        is ChatEvent.Reasoning -> {
                            if (accumulator.firstTokenAt == null) accumulator.firstTokenAt = now()
                            accumulator.reasoning.append(event.delta)
                            accumulator.appendThinking(event.delta)
                            publish(conversation, accumulator, Phase.REASONING)
                        }
                        is ChatEvent.ToolCalls -> {
                            mergeToolDeltas(accumulator, event.deltas)
                            publish(conversation, accumulator, Phase.STREAMING)
                        }
                        is ChatEvent.Citations -> {
                            event.items.forEach { accumulator.citations[it.url] = it }
                            accumulator.recordWebSearch(event.items.map { it.url })
                            publish(conversation, accumulator, Phase.STREAMING, force = true)
                        }
                        is ChatEvent.Images -> {
                            event.urls.forEach { accumulator.images.add(it) }
                            publish(conversation, accumulator, Phase.STREAMING, force = true)
                        }
                        is ChatEvent.Usage -> {
                            accumulator.usage = event.usage
                            event.model?.let { accumulator.model = it }
                            event.provider?.let { accumulator.provider = it }
                        }
                        is ChatEvent.Done -> {
                            accumulator.finishReason = event.finishReason
                            accumulator.nativeFinishReason = event.nativeFinishReason
                            event.model?.let { accumulator.model = it }
                            event.provider?.let { accumulator.provider = it }
                        }
                        is ChatEvent.Failed -> {
                            failure = ApiException(event.message, event.code ?: 0)
                        }
                    }
                }
            } catch (e: Exception) {
                failure = e
            }
            if (failure != null) {
                val problemNow = failure
                val early = accumulator.content.isEmpty() && accumulator.reasoning.isEmpty() &&
                    accumulator.toolCalls.isEmpty() && accumulator.iteration == 0
                val retryable = problemNow is java.io.IOException ||
                    (problemNow is ApiException && (problemNow.code == 0 || problemNow.code >= 500))
                if (attempt < 1 && early && retryable) {
                    attempt++
                    delay(1200)
                    continue
                }
                break
            }
            val calls = accumulator.toolCalls.values.toList()
            val localCalls = calls.filter { it.name != null && it.name in localToolIds }
            val canRunLocalTools = accumulator.finishReason == "tool_calls" &&
                calls.isNotEmpty() &&
                localCalls.size == calls.size &&
                accumulator.iteration < MAX_TOOL_ITERATIONS
            if (!canRunLocalTools) break
            accumulator.iteration++
            val results = localCalls.map { call ->
                call to LocalToolExecutor.execute(call.name!!, call.arguments.toString())
            }
            buildToolTurnRows(conversation, accumulator, results)
            publish(conversation, accumulator, Phase.CONNECTING, force = true)
            body = buildBody(conversation, accumulator, accumulator.modelIdOverride ?: conversation.modelId)
        }
        val problem = failure
        try {
            if (problem is CancellationException) {
                finalizeInterrupted(conversation, accumulator)
                throw problem
            }
            if (problem != null) {
                finalizeError(conversation, accumulator, problem)
            } else {
                finalizeSuccess(conversation, accumulator)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { finishUi(conversation.id) }
        }
    }
    private suspend fun buildToolTurnRows(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        results: List<Pair<ToolCallAccumulator, String>>,
    ) {
        val turnMessage = chat.getMessage(accumulator.messageId)
        val toolCalls = accumulator.toolCalls.values.map { entry ->
            ToolCall(
                id = entry.id ?: newId(),
                name = entry.name?.takeIf { it.isNotBlank() } ?: "tool",
                arguments = entry.arguments.toString(),
            )
        }
        if (turnMessage != null) {
            chat.upsertMessage(
                turnMessage.copy(
                    content = accumulator.content.toString(),
                    reasoning = accumulator.reasoning.toString().takeIf { it.isNotBlank() },
                    toolCalls = toolCalls,
                    citations = accumulator.citations.values.toList(),
                    trace = accumulator.trace.toList(),
                    status = MessageStatus.COMPLETE,
                    finishReason = accumulator.finishReason,
                    modelId = accumulator.model ?: accumulator.modelIdOverride ?: conversation.modelId,
                    provider = accumulator.provider,
                    latencyMs = now() - accumulator.startedAt,
                ),
                indexForSearch = accumulator.content.isNotBlank(),
            )
        }
        var parent = accumulator.messageId
        var timestamp = now()
        for ((call, output) in results) {
            val toolRow = ChatMessage(
                id = newId(),
                conversationId = conversation.id,
                parentId = parent,
                role = Role.TOOL,
                content = output,
                toolCallId = call.id,
                createdAt = timestamp++,
            )
            chat.upsertMessage(toolRow)
            parent = toolRow.id
        }
        accumulator.leafForChain = parent
        val nextAssistant = ChatMessage(
            id = newId(),
            conversationId = conversation.id,
            parentId = parent,
            role = Role.ASSISTANT,
            content = "",
            modelId = accumulator.modelIdOverride ?: conversation.modelId,
            status = MessageStatus.STREAMING,
            createdAt = now(),
        )
        chat.upsertMessage(nextAssistant)
        chat.setLeaf(conversation.id, nextAssistant.id)
        accumulator.messageId = nextAssistant.id
    }
    private fun mergeToolDeltas(accumulator: StreamAccumulator, deltas: List<ToolCallDeltaDto>) {
        for (delta in deltas) {
            val entry = accumulator.toolCalls.getOrPut(delta.index) { ToolCallAccumulator() }
            delta.id?.let { entry.id = it }
            delta.function?.name?.let { name ->
                entry.name = (entry.name ?: "") + name
            }
            delta.function?.arguments?.let { entry.arguments.append(it) }
        }
    }
    private suspend fun finalizeSuccess(conversation: Conversation, accumulator: StreamAccumulator) =
        withContext(NonCancellable) {
            val content = accumulator.content.toString()
            val toolCalls = accumulator.toolCalls.values.map { entry ->
                ToolCall(
                    id = entry.id ?: newId(),
                    name = entry.name?.takeIf { it.isNotBlank() } ?: "tool",
                    arguments = entry.arguments.toString(),
                )
            }
            val generatedAttachments = accumulator.images.mapNotNull { url -> attachments.attachmentFromUrl(url) }
            val hasContent = content.isNotBlank() || toolCalls.isNotEmpty() || generatedAttachments.isNotEmpty()
            val latency = now() - accumulator.startedAt
            val existing = chat.getMessage(accumulator.messageId)
            val finalized = (existing ?: placeholder(conversation, accumulator)).copy(
                content = content,
                reasoning = accumulator.reasoning.toString().takeIf { it.isNotBlank() },
                toolCalls = toolCalls,
                citations = accumulator.citations.values.toList(),
                trace = accumulator.trace.toList(),
                attachments = (existing?.attachments ?: emptyList()) + generatedAttachments,
                modelId = accumulator.model ?: accumulator.modelIdOverride ?: conversation.modelId,
                provider = accumulator.provider,
                status = if (hasContent) MessageStatus.COMPLETE else MessageStatus.ERROR,
                error = if (hasContent) null else "The model returned an empty response. Try again or pick another model.",
                finishReason = accumulator.finishReason,
                generationId = accumulator.generationId,
                promptTokens = accumulator.usage?.promptTokens,
                completionTokens = accumulator.usage?.completionTokens,
                cachedTokens = accumulator.usage?.cachedTokens,
                reasoningTokens = accumulator.usage?.reasoningTokens,
                costUsd = accumulator.usage?.cost,
                latencyMs = latency,
            )
            chat.upsertMessage(finalized, indexForSearch = content.isNotBlank())
            if (hasContent) {
                chat.addGeneration(
                    GenerationEntity(
                        id = newId(),
                        generationId = accumulator.generationId,
                        conversationId = conversation.id,
                        messageId = finalized.id,
                        modelId = finalized.modelId ?: conversation.modelId,
                        provider = accumulator.provider,
                        costUsd = accumulator.usage?.cost,
                        promptTokens = accumulator.usage?.promptTokens,
                        completionTokens = accumulator.usage?.completionTokens,
                        cachedTokens = accumulator.usage?.cachedTokens,
                        reasoningTokens = accumulator.usage?.reasoningTokens,
                        latencyMs = latency,
                        ttftMs = accumulator.firstTokenAt?.minus(accumulator.startedAt),
                        createdAt = now(),
                    ),
                )
            }
            chat.touch(conversation.id)
            maybeGenerateTitle(conversation)
            finishUi(conversation.id)
            runCatching { account.refresh() }
            notifyFinished(conversation)
        }
    private suspend fun finalizeError(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        failure: Throwable,
    ) = withContext(NonCancellable) {
        val content = accumulator.content.toString()
        val hasPartial = content.isNotBlank()
        val existing = chat.getMessage(accumulator.messageId)
        val finalized = (existing ?: placeholder(conversation, accumulator)).copy(
            content = content,
            reasoning = accumulator.reasoning.toString().takeIf { it.isNotBlank() },
            citations = accumulator.citations.values.toList(),
            trace = accumulator.trace.toList(),
            modelId = accumulator.model ?: accumulator.modelIdOverride ?: conversation.modelId,
            provider = accumulator.provider,
            status = if (hasPartial) MessageStatus.INTERRUPTED else MessageStatus.ERROR,
            error = failure.friendlyMessage(),
            finishReason = accumulator.finishReason,
            generationId = accumulator.generationId,
            latencyMs = now() - accumulator.startedAt,
        )
        chat.upsertMessage(finalized, indexForSearch = hasPartial)
        chat.touch(conversation.id)
        finishUi(conversation.id)
        notifyFinished(conversation)
    }
    private suspend fun finalizeInterrupted(conversation: Conversation, accumulator: StreamAccumulator) =
        withContext(NonCancellable) {
            val content = accumulator.content.toString()
            val existing = chat.getMessage(accumulator.messageId)
            val finalized = (existing ?: placeholder(conversation, accumulator)).copy(
                content = content,
                reasoning = accumulator.reasoning.toString().takeIf { it.isNotBlank() },
                citations = accumulator.citations.values.toList(),
                trace = accumulator.trace.toList(),
                modelId = accumulator.model ?: accumulator.modelIdOverride ?: conversation.modelId,
                provider = accumulator.provider,
                status = MessageStatus.INTERRUPTED,
                error = null,
                finishReason = "stopped",
                generationId = accumulator.generationId,
                latencyMs = now() - accumulator.startedAt,
            )
            chat.upsertMessage(finalized, indexForSearch = content.isNotBlank())
            chat.touch(conversation.id)
            finishUi(conversation.id)
        }
    private fun placeholder(conversation: Conversation, accumulator: StreamAccumulator) = ChatMessage(
        id = accumulator.messageId,
        conversationId = conversation.id,
        parentId = null,
        role = Role.ASSISTANT,
        content = "",
        modelId = accumulator.modelIdOverride ?: conversation.modelId,
        createdAt = now(),
    )
    private suspend fun finishUi(conversationId: String) {
        accumulators.remove(conversationId)
        jobs.remove(conversationId)
        _streams.update { it - conversationId }
        if (_streams.value.isEmpty()) {
            GenerationService.stop(context)
        }
    }
    private fun notifyFinished(conversation: Conversation) {
        if (!Foreground.isForeground.value) {
            GenerationService.stop(context)
            GenerationService.postReadyNotification(context, conversation.id, conversation.title)
        }
    }
    suspend fun generateTitleFor(conversationId: String) {
        val conversation = chat.getConversation(conversationId) ?: return
        runCatching { maybeGenerateTitle(conversation, force = true) }
    }
    private suspend fun maybeGenerateTitle(conversation: Conversation, force: Boolean = false) {
        if (!conversation.titleIsAuto && !force) return
        if (!force && chat.countUserMessages(conversation.id) != 1) return
        val first = chat.firstUserContent(conversation.id) ?: return
        val apiKey = settings.activeKeySecret() ?: return
        val settingsSnapshot = settings.current()
        val preferred = settingsSnapshot.titleModelId.ifBlank { conversation.modelId }
        val titleModel = if ("text" in models.byId(preferred).outputModalities) preferred else "openrouter/auto"
        val generated = runCatching {
            val body = RequestBuilder.build(
                chain = listOf(
                    ChatMessage(
                        id = "title-request",
                        conversationId = conversation.id,
                        parentId = null,
                        role = Role.USER,
                        content = "Create a short title (2 to 6 words) for a chat that starts with this message. " +
                            "Reply with only the title, no quotes, no punctuation at the end.\n\n" + first.take(1200),
                        createdAt = now(),
                    ),
                ),
                systemPrompt = "You name conversations concisely.",
                modelId = titleModel,
                params = ChatParams(
                    maxTokens = 96,
                    temperature = 0.4,
                    reasoningExclude = true,
                    extraBody = "{\"reasoning\":{\"enabled\":false}}",
                ),
                stream = false,
            )
            client.completeText(apiKey, body)
        }.getOrNull()
            ?.trim()
            ?.trim('"', '\'', ' ', '.', ':', '*', '#')
            ?.replace('\n', ' ')
            ?.take(80)
        val title = generated?.takeIf { it.length >= 2 } ?: fallbackTitle(first)
        chat.setTitle(conversation.id, title, auto = true)
    }
    private fun fallbackTitle(first: String): String {
        val plain = first
            .replace(Regex("[#*`>\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = plain.split(' ').take(6).joinToString(" ")
        return words.take(48).trim().replaceFirstChar { it.uppercaseChar() }.ifBlank { "New chat" }
    }
    private suspend fun publish(
        conversation: Conversation,
        accumulator: StreamAccumulator,
        phase: Phase,
        force: Boolean = false,
    ) {
        val timestamp = now()
        val phaseChanged = phase != accumulator.publishedPhase
        if (!force && !phaseChanged && timestamp - accumulator.lastFlushAt < 33) return
        accumulator.lastFlushAt = timestamp
        accumulator.publishedPhase = phase
        _streams.update { current ->
            current + (
                conversation.id to StreamState(
                    conversationId = conversation.id,
                    messageId = accumulator.messageId,
                    modelId = accumulator.modelIdOverride ?: conversation.modelId,
                    content = accumulator.content.toString(),
                    reasoning = accumulator.reasoning.toString(),
                    toolCalls = accumulator.toolCalls.values.map { entry ->
                        UiToolCall(
                            id = entry.id ?: "tool-${entry.hashCode()}",
                            name = entry.name?.takeIf { it.isNotBlank() } ?: "tool",
                            arguments = entry.arguments.toString(),
                        )
                    },
                    citations = accumulator.citations.values.toList(),
                    trace = accumulator.trace.toList(),
                    images = accumulator.images.toList(),
                    imageGeneration = accumulator.imageGeneration,
                    phase = phase,
                    startedAt = accumulator.startedAt,
                    firstTokenAt = accumulator.firstTokenAt,
                    usage = accumulator.usage,
                )
            )
        }
        if (timestamp - accumulator.lastDbAt > 1500) {
            accumulator.lastDbAt = timestamp
            runCatching {
                chat.checkpointMessage(
                    accumulator.messageId,
                    accumulator.content.toString(),
                    accumulator.reasoning.toString().takeIf { it.isNotBlank() },
                    streaming = true,
                )
            }
        }
    }
    private class ToolCallAccumulator {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
    private inner class StreamAccumulator(
        val conversationId: String,
        var messageId: String,
    ) {
        var modelIdOverride: String? = null
        var imageGeneration = false
        var iteration = 0
        var leafForChain: String? = null
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = LinkedHashMap<Int, ToolCallAccumulator>()
        val citations = LinkedHashMap<String, Citation>()
        val trace = ArrayList<TraceStep>()
        val images = LinkedHashSet<String>()
        fun appendThinking(delta: String) {
            val last = trace.lastOrNull()
            if (last != null && last.kind == TraceStep.Kind.THINKING) {
                trace[trace.lastIndex] = last.copy(text = last.text + delta)
            } else {
                trace += TraceStep.thinking(delta)
            }
        }
        fun recordWebSearch(urls: List<String>) {
            val clean = urls.filter { it.isNotBlank() }.distinct()
            if (clean.isEmpty()) return
            val last = trace.lastOrNull()
            if (last != null && last.kind == TraceStep.Kind.WEB_SEARCH) {
                trace[trace.lastIndex] = last.copy(urls = (last.urls + clean).distinct())
            } else {
                trace += TraceStep.webSearch(query = null, urls = clean)
            }
        }
        fun startTurn() {
            content.setLength(0)
            reasoning.setLength(0)
            toolCalls.clear()
            trace.clear()
            firstTokenAt = null
            finishReason = null
            nativeFinishReason = null
            publishedPhase = null
        }
        val startedAt: Long = now()
        var firstTokenAt: Long? = null
        var generationId: String? = null
        var model: String? = null
        var provider: String? = null
        var usage: TokenUsage? = null
        var finishReason: String? = null
        var nativeFinishReason: String? = null
        var lastFlushAt: Long = 0L
        var lastDbAt: Long = 0L
        var publishedPhase: Phase? = null
    }
}
object Foreground {
    val isForeground = MutableStateFlow(true)
}
