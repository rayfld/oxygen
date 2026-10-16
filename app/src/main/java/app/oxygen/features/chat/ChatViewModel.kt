package app.oxygen.features.chat
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oxygen.OxygenApp
import app.oxygen.core.model.Attachment
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.Conversation
import app.oxygen.core.model.MessageStatus
import app.oxygen.core.model.Role
import app.oxygen.core.model.ToolCall
import app.oxygen.core.model.TraceStep
import app.oxygen.data.AppSettings
import app.oxygen.data.GenerationManager
import app.oxygen.data.toDomain
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
class ChatViewModel(
    private val conversationId: String,
) : ViewModel() {
    private val graph = OxygenApp.graph
    data class UiMessage(
        val message: ChatMessage,
        val siblingIndex: Int,
        val siblingCount: Int,
        val siblingIds: List<String>,
    )
    data class UiState(
        val conversation: Conversation? = null,
        val items: List<ChatListItem> = emptyList(),
        val messages: List<UiMessage> = emptyList(),
        val streaming: GenerationManager.StreamState? = null,
        val currentModel: ChatModel? = null,
        val hasKey: Boolean = false,
        val settings: AppSettings = AppSettings(),
        val loaded: Boolean = false,
    )
    sealed interface ChatListItem {
        data class Single(val ui: UiMessage) : ChatListItem
        data class Work(
            val id: String,
            val durationMs: Long,
            val hasTools: Boolean,
            val stopped: Boolean,
            val steps: List<WorkStep>,
            val finalUi: UiMessage?,
        ) : ChatListItem
    }
    sealed interface WorkStep {
        data class Thinking(val text: String) : WorkStep
        data class Say(val text: String) : WorkStep
        data class Tool(val call: ToolCall, val result: String?) : WorkStep
    }
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()
    private val _catalog = MutableStateFlow<List<ChatModel>>(emptyList())
    val catalog: StateFlow<List<ChatModel>> = _catalog.asStateFlow()
    val uiState: StateFlow<UiState> = combine(
        graph.chat.observeConversation(conversationId),
        graph.chat.observeMessages(conversationId),
        graph.generationManager.streams,
        graph.settings.state,
    ) { entity, messageEntities, streams, settings ->
        val conversation = entity?.toDomain()
        val all = messageEntities.map { it.toDomain() }
        val byId = all.associateBy { it.id }
        val childrenByParent = all.filter { it.role != Role.SYSTEM }.groupBy { it.parentId }
        val chain = ArrayList<ChatMessage>()
        var cursor = conversation?.currentLeafId
        var guard = 0
        while (cursor != null && guard < 10_000) {
            val message = byId[cursor] ?: break
            chain += message
            cursor = message.parentId
            guard++
        }
        chain.reverse()
        val visible = chain.filter { it.status != MessageStatus.STREAMING }
        val uiMessages = visible.map { message ->
            val siblings = (childrenByParent[message.parentId] ?: listOf(message))
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
            UiMessage(
                message = message,
                siblingIndex = siblings.indexOfFirst { it.id == message.id }.coerceAtLeast(0),
                siblingCount = siblings.size,
                siblingIds = siblings.map { it.id },
            )
        }
        UiState(
            conversation = conversation,
            items = buildWorkItems(visible, uiMessages),
            messages = uiMessages,
            streaming = streams[conversationId]?.takeIf { candidate ->
                messageEntities.none { it.id == candidate.messageId && it.status != "streaming" }
            },
            currentModel = conversation?.let { graph.models.byId(it.modelId) },
            hasKey = settings.keys.isNotEmpty(),
            settings = settings,
            loaded = entity != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
    private fun buildWorkItems(
        visibleChain: List<ChatMessage>,
        uiMessages: List<UiMessage>,
    ): List<ChatListItem> {
        val uiById = uiMessages.associateBy { it.message.id }
        val items = ArrayList<ChatListItem>()
        val buffer = ArrayList<ChatMessage>()
        fun flush() {
            if (buffer.isEmpty()) return
            val steps = ArrayList<WorkStep>()
            val results = HashMap<String, String>()
            buffer.filter { it.role == Role.TOOL }.forEach { tool ->
                tool.toolCallId?.let { results[it] = tool.content }
            }
            val allCallIds = buffer.flatMap { message -> message.toolCalls.map { it.id } }.toSet()
            var finalMessage: ChatMessage? = null
            for (message in buffer) {
                if (message.role == Role.ASSISTANT && message.content.isNotBlank()) finalMessage = message
            }
            var hasTools = false
            for (message in buffer) {
                if (message.role == Role.ASSISTANT) {
                    if (message.trace.isNotEmpty()) {
                        message.trace.forEachIndexed { index, step ->
                            when (step.kind) {
                                TraceStep.Kind.THINKING ->
                                    if (step.text.isNotBlank()) steps += WorkStep.Thinking(step.text)
                                TraceStep.Kind.WEB_SEARCH -> {
                                    hasTools = true
                                    steps += WorkStep.Tool(
                                        call = ToolCall(
                                            id = "web-${message.id}-$index",
                                            name = "web_search",
                                            arguments = step.query?.takeIf { it.isNotBlank() }?.let { query ->
                                                buildJsonObject { put("query", query) }.toString()
                                            }.orEmpty(),
                                        ),
                                        result = step.urls.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                                    )
                                }
                            }
                        }
                    } else if (!message.reasoning.isNullOrBlank()) {
                        steps += WorkStep.Thinking(message.reasoning)
                    }
                    if (message != finalMessage && message.content.isNotBlank()) steps += WorkStep.Say(message.content)
                    message.toolCalls.forEach { call ->
                        hasTools = true
                        steps += WorkStep.Tool(call, results[call.id])
                    }
                }
            }
            buffer.filter { it.role == Role.TOOL && it.toolCallId !in allCallIds }.forEach { tool ->
                steps += WorkStep.Tool(ToolCall(tool.toolCallId ?: tool.id, "tool result", ""), tool.content)
            }
            if (steps.isEmpty()) {
                buffer.forEach { message -> uiById[message.id]?.let { items += ChatListItem.Single(it) } }
            } else {
                val last = buffer.last()
                val end = last.createdAt + (last.latencyMs ?: 0L)
                val duration = (end - buffer.first().createdAt).coerceAtLeast(600L)
                val stopped = buffer.any { it.status == MessageStatus.INTERRUPTED || it.finishReason == "stopped" }
                items += ChatListItem.Work(
                    id = buffer.first().id,
                    durationMs = duration,
                    hasTools = hasTools,
                    stopped = stopped,
                    steps = steps,
                    finalUi = finalMessage?.let { uiById[it.id] },
                )
            }
            buffer.clear()
        }
        for (message in visibleChain) {
            if (message.role == Role.USER) {
                flush()
                uiById[message.id]?.let { items += ChatListItem.Single(it) }
            } else {
                buffer += message
            }
        }
        flush()
        return items
    }
    init {
        viewModelScope.launch {
            _input.value = graph.settings.current().drafts[conversationId].orEmpty()
            graph.models.ensureLoaded(graph.settings.activeKeySecret())
            _catalog.value = graph.models.models
        }
    }
    fun persistDraft() {
        draftSaveJob?.cancel()
        val text = _input.value
        graph.scope.launch { graph.settings.setDraft(conversationId, text) }
    }
    fun regenerateTitle() {
        viewModelScope.launch { graph.generationManager.generateTitleFor(conversationId) }
    }
    private var draftSaveJob: Job? = null
    fun setInput(value: String) {
        _input.value = value
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(2_000)
            graph.settings.setDraft(conversationId, value)
        }
    }
    fun send(overrideText: String? = null) {
        val text = overrideText ?: _input.value
        val pending = if (overrideText == null) _attachments.value else emptyList()
        if (text.isBlank() && pending.isEmpty()) return
        if (uiState.value.streaming != null) return
        viewModelScope.launch {
            if (overrideText == null) {
                _input.value = ""
                _attachments.value = emptyList()
            }
            graph.generationManager.sendMessage(conversationId, text, pending)
        }
    }
    fun stop() = graph.generationManager.stop(conversationId)
    fun regenerate(messageId: String, modelId: String? = null) {
        viewModelScope.launch { graph.generationManager.regenerate(conversationId, messageId, modelId) }
    }
    fun editMessage(messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch { graph.generationManager.editUserMessage(conversationId, messageId, newText) }
    }
    fun continueMessage(messageId: String) {
        viewModelScope.launch { graph.generationManager.continueGeneration(conversationId, messageId) }
    }
    fun deleteMessage(messageId: String) {
        viewModelScope.launch { graph.chat.deleteSubtree(conversationId, messageId) }
    }
    fun switchBranch(siblingId: String) {
        viewModelScope.launch {
            val leaf = graph.chat.deepestLeaf(conversationId, siblingId)
            graph.chat.setLeaf(conversationId, leaf)
        }
    }
    fun setModel(modelId: String) {
        viewModelScope.launch { graph.chat.setModel(conversationId, modelId) }
    }
    fun setParams(params: ChatParams) {
        viewModelScope.launch { graph.chat.setParams(conversationId, params) }
    }
    fun setSystemPrompt(prompt: String?) {
        viewModelScope.launch { graph.chat.setSystemPrompt(conversationId, prompt?.takeIf { it.isNotBlank() }) }
    }
    fun rename(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { graph.chat.setTitle(conversationId, title.trim(), auto = false) }
    }
    fun deleteConversation(onDone: () -> Unit) {
        viewModelScope.launch {
            graph.generationManager.stop(conversationId)
            graph.chat.deleteConversation(conversationId)
            onDone()
        }
    }
    fun forkFrom(messageId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            graph.chat.forkConversation(conversationId, messageId)?.let(onCreated)
        }
    }
    fun saveTemporaryChat() {
        viewModelScope.launch {
            graph.chat.saveEphemeral(conversationId)
            graph.generationManager.generateTitleFor(conversationId)
        }
    }
    fun addImage(uri: Uri) {
        viewModelScope.launch {
            graph.attachments.importImage(uri)?.let { attachment ->
                _attachments.update { list -> list + attachment }
            }
        }
    }
    fun addPdf(uri: Uri) {
        viewModelScope.launch {
            graph.attachments.importPdf(uri)?.let { attachment ->
                _attachments.update { list -> list + attachment }
            }
        }
    }
    fun removeAttachment(id: String) {
        val attachment = _attachments.value.firstOrNull { it.id == id } ?: return
        _attachments.update { list -> list.filterNot { it.id == id } }
        graph.attachments.delete(attachment)
    }
}
