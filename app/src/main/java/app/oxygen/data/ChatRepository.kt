package app.oxygen.data
import app.oxygen.core.db.ConversationEntity
import app.oxygen.core.db.FtsHit
import app.oxygen.core.db.MessageEntity
import app.oxygen.core.db.MessageFtsEntity
import app.oxygen.core.db.OxygenDatabase
import app.oxygen.core.model.Attachment
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.Citation
import app.oxygen.core.model.Conversation
import app.oxygen.core.model.MessageStatus
import app.oxygen.core.model.Role
import app.oxygen.core.model.ToolCall
import app.oxygen.core.util.json
import app.oxygen.core.util.newId
import app.oxygen.core.util.now
import app.oxygen.core.util.parseJsonObjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
data class SearchHit(
    val conversationId: String,
    val messageId: String,
    val snippet: String,
)
class ChatRepository(private val db: OxygenDatabase) {
    private val conversations = db.conversations()
    private val messages = db.messages()
    private val fts = db.fts()
    private val generations = db.generations()
    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeAll()
    fun observeSnippets() = conversations.observeSnippets()
    fun observeConversation(id: String): Flow<ConversationEntity?> = conversations.observe(id)
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = messages.observeByConversation(conversationId)
    suspend fun createConversation(
        modelId: String,
        systemPrompt: String?,
        params: ChatParams,
        ephemeral: Boolean = false,
    ): Conversation {
        val timestamp = now()
        val entity = ConversationEntity(
            id = newId(),
            title = if (ephemeral) "Temporary chat" else "New chat",
            titleIsAuto = !ephemeral,
            createdAt = timestamp,
            updatedAt = timestamp,
            modelId = modelId,
            systemPrompt = systemPrompt,
            paramsJson = json.encodeToString(ChatParams.serializer(), params),
            ephemeral = ephemeral,
        )
        conversations.upsert(entity)
        return entity.toDomain()
    }
    suspend fun getConversation(id: String): Conversation? = conversations.get(id)?.toDomain()
    suspend fun upsertConversation(conversation: Conversation) {
        conversations.upsert(conversation.toEntity())
    }
    suspend fun duplicateConversation(id: String): String? {
        val original = conversations.get(id)?.toDomain() ?: return null
        val timestamp = now()
        val copy = original.copy(
            id = newId(),
            title = original.title + " copy",
            titleIsAuto = false,
            createdAt = timestamp,
            updatedAt = timestamp,
            pinned = false,
            ephemeral = false,
        )
        conversations.upsert(copy.toEntity())
        val all = messages.allByConversation(id)
        val idMap = HashMap<String, String>()
        for (entity in all) idMap[entity.id] = newId()
        for (entity in all) {
            val copied = entity.copy(
                id = idMap[entity.id]!!,
                conversationId = copy.id,
                parentId = entity.parentId?.let { idMap[it] },
            )
            messages.upsert(copied)
            if (copied.content.isNotBlank()) {
                runCatching {
                    fts.insert(
                        MessageFtsEntity(
                            messageId = copied.id,
                            conversationId = copy.id,
                            content = copied.content,
                        ),
                    )
                }
            }
        }
        conversations.updateLeaf(copy.id, original.currentLeafId?.let { idMap[it] })
        return copy.id
    }
    suspend fun forkConversation(conversationId: String, upToMessageId: String): String? {
        val original = conversations.get(conversationId)?.toDomain() ?: return null
        val all = messages.allByConversation(conversationId).associateBy { it.id }
        val chain = ArrayList<MessageEntity>()
        var cursor: String? = upToMessageId
        var guard = 0
        while (cursor != null && guard < 10_000) {
            val entity = all[cursor] ?: break
            chain += entity
            cursor = entity.parentId
            guard++
        }
        chain.reverse()
        if (chain.isEmpty()) return null
        val timestamp = now()
        val copy = original.copy(
            id = newId(),
            createdAt = timestamp,
            updatedAt = timestamp,
            pinned = false,
            ephemeral = false,
            titleIsAuto = false,
        )
        conversations.upsert(copy.toEntity())
        val idMap = HashMap<String, String>()
        chain.forEach { idMap[it.id] = newId() }
        var lastId: String? = null
        for (entity in chain) {
            val copied = entity.copy(
                id = idMap[entity.id]!!,
                conversationId = copy.id,
                parentId = entity.parentId?.let { idMap[it] },
            )
            messages.upsert(copied)
            if (copied.content.isNotBlank()) {
                runCatching {
                    fts.insert(
                        MessageFtsEntity(
                            messageId = copied.id,
                            conversationId = copy.id,
                            content = copied.content,
                        ),
                    )
                }
            }
            lastId = copied.id
        }
        conversations.updateLeaf(copy.id, lastId)
        return copy.id
    }
    suspend fun snapshotConversation(id: String): Pair<Conversation, List<ChatMessage>>? {
        val conversation = getConversation(id) ?: return null
        return conversation to allMessages(id)
    }
    suspend fun restoreConversation(snapshot: Pair<Conversation, List<ChatMessage>>) {
        val (conversation, msgs) = snapshot
        conversations.upsert(conversation.toEntity())
        msgs.forEach { message -> upsertMessage(message, indexForSearch = message.content.isNotBlank()) }
    }
    suspend fun deleteAllEphemeral() {
        runCatching { conversations.ephemeralIds() }
            .getOrDefault(emptyList())
            .forEach { deleteConversation(it) }
    }
    suspend fun saveEphemeral(id: String) {
        conversations.clearEphemeral(id)
        val conversation = getConversation(id) ?: return
        if (conversation.title == "Temporary chat") {
            conversations.updateTitle(id, "New chat", auto = true)
        }
    }
    suspend fun conversationEntity(id: String): ConversationEntity? = conversations.get(id)
    suspend fun setTitle(id: String, title: String, auto: Boolean) = conversations.updateTitle(id, title, auto)
    suspend fun touch(id: String) = conversations.touch(id, now())
    suspend fun setLeaf(id: String, leafId: String?) = conversations.updateLeaf(id, leafId)
    suspend fun setModel(id: String, modelId: String) = conversations.updateModel(id, modelId)
    suspend fun setSystemPrompt(id: String, prompt: String?) = conversations.updateSystemPrompt(id, prompt)
    suspend fun setParams(id: String, params: ChatParams) =
        conversations.updateParams(id, json.encodeToString(ChatParams.serializer(), params))
    suspend fun setPinned(id: String, pinned: Boolean) = conversations.updatePinned(id, pinned)
    suspend fun deleteConversation(id: String) {
        conversations.deleteMessages(id)
        conversations.deleteFts(id)
        conversations.delete(id)
    }
    suspend fun conversationCost(id: String): Double? = generations.sumForConversation(id)
    suspend fun countUserMessages(id: String): Int = messages.countUserMessages(id)
    suspend fun firstUserContent(id: String): String? = messages.firstUserContent(id)
    suspend fun getMessage(id: String): ChatMessage? = messages.get(id)?.toDomain()
    suspend fun allMessages(conversationId: String): List<ChatMessage> =
        messages.allByConversation(conversationId).map { it.toDomain() }
    suspend fun upsertMessage(message: ChatMessage, indexForSearch: Boolean = false) {
        messages.upsert(message.toEntity())
        if (indexForSearch) {
            if (message.content.isNotBlank()) {
                fts.insert(
                    MessageFtsEntity(
                        messageId = message.id,
                        conversationId = message.conversationId,
                        content = message.content,
                    ),
                )
            }
        }
    }
    suspend fun checkpointMessage(messageId: String, content: String, reasoning: String?, streaming: Boolean) {
        messages.checkpoint(messageId, content, reasoning, if (streaming) "streaming" else "complete")
    }
    suspend fun setMessageStatus(messageId: String, status: MessageStatus, error: String? = null) {
        messages.get(messageId)?.let { entity ->
            messages.upsert(entity.copy(status = status.name.lowercase(), error = error))
        }
    }
    suspend fun siblings(conversationId: String, message: ChatMessage): List<ChatMessage> =
        messages.siblingsOf(conversationId, message.parentId).map { it.toDomain() }
    suspend fun deepestLeaf(conversationId: String, fromId: String): String {
        var current = fromId
        while (true) {
            val children = messages.childrenOf(current).filter { it.role != Role.SYSTEM.name.lowercase() }
            if (children.isEmpty()) return current
            current = children.maxByOrNull { it.createdAt }?.id ?: return current
        }
    }
    suspend fun chain(conversationId: String, leafId: String?): List<ChatMessage> {
        if (leafId == null) return emptyList()
        val all = messages.allByConversation(conversationId).associateBy { it.id }
        val out = ArrayList<ChatMessage>()
        var cursor: String? = leafId
        var guard = 0
        while (cursor != null && guard < 10_000) {
            val entity = all[cursor] ?: break
            val domain = entity.toDomain()
            val isPlaceholder = domain.role == Role.ASSISTANT &&
                domain.content.isBlank() &&
                domain.toolCalls.isEmpty() &&
                domain.status != MessageStatus.COMPLETE
            if (!isPlaceholder) out += domain
            cursor = entity.parentId
            guard++
        }
        out.reverse()
        return out
    }
    suspend fun deleteSubtree(conversationId: String, messageId: String) {
        val toDelete = ArrayList<String>()
        val queue = ArrayDeque<String>().apply { add(messageId) }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            toDelete += id
            messages.childrenOf(id).forEach { queue.add(it.id) }
        }
        for (id in toDelete) {
            fts.deleteByMessage(id)
            messages.delete(id)
        }
        val conversation = conversations.get(conversationId) ?: return
        if (conversation.currentLeafId in toDelete) {
            val deleted = toDelete.toSet()
            val parent = messages.get(messageId)?.parentId
            conversations.updateLeaf(conversationId, if (parent in deleted) null else parent)
        }
    }
    suspend fun markInterrupted() = messages.markStreamingInterrupted()
    suspend fun search(query: String): List<SearchHit> {
        val terms = query.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
            .map { it + "*" }
        if (terms.isEmpty()) return emptyList()
        val matchQuery = terms.joinToString(" ")
        val hits = try {
            fts.search(matchQuery)
        } catch (_: Exception) {
            emptyList()
        }
        return hits.map { hit ->
            SearchHit(
                conversationId = hit.conversationId,
                messageId = hit.messageId,
                snippet = snippetFor(hit, query),
            )
        }
    }
    private fun snippetFor(hit: FtsHit, query: String): String {
        val content = hit.content.replace('\n', ' ')
        val needle = query.split(Regex("[^\\p{L}\\p{N}]+")).firstOrNull { it.isNotBlank() } ?: return content.take(120)
        val idx = content.indexOf(needle, ignoreCase = true)
        if (idx <= 0) return content.take(120)
        val start = (idx - 40).coerceAtLeast(0)
        val end = (idx + needle.length + 80).coerceAtMost(content.length)
        return (if (start > 0) "…" else "") + content.substring(start, end) + (if (end < content.length) "…" else "")
    }
    suspend fun addGeneration(entity: app.oxygen.core.db.GenerationEntity) = generations.insert(entity)
    suspend fun clearUsageLedger() = generations.clear()
}
fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    titleIsAuto = titleIsAuto,
    createdAt = createdAt,
    updatedAt = updatedAt,
    modelId = modelId,
    systemPrompt = systemPrompt,
    params = parseJsonObjectOrNull(paramsJson)?.let { obj ->
        runCatching { json.decodeFromJsonElement(ChatParams.serializer(), obj) }.getOrNull()
    } ?: ChatParams(),
    currentLeafId = currentLeafId,
    pinned = pinned,
    archived = archived,
    ephemeral = ephemeral,
)
fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    titleIsAuto = titleIsAuto,
    createdAt = createdAt,
    updatedAt = updatedAt,
    modelId = modelId,
    systemPrompt = systemPrompt,
    paramsJson = json.encodeToString(ChatParams.serializer(), params),
    currentLeafId = currentLeafId,
    pinned = pinned,
    archived = archived,
    ephemeral = ephemeral,
)
fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    parentId = parentId,
    role = runCatching { Role.valueOf(role.uppercase()) }.getOrElse { Role.USER },
    content = content,
    reasoning = reasoning,
    toolCalls = decodeList<ToolCall>(toolCallsJson),
    toolCallId = toolCallId,
    attachments = decodeList<Attachment>(attachmentsJson),
    citations = decodeList<Citation>(citationsJson),
    modelId = modelId,
    provider = provider,
    status = runCatching { MessageStatus.valueOf(status.uppercase()) }.getOrElse { MessageStatus.COMPLETE },
    error = error,
    finishReason = finishReason,
    generationId = generationId,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    cachedTokens = cachedTokens,
    reasoningTokens = reasoningTokens,
    costUsd = costUsd,
    createdAt = createdAt,
    latencyMs = latencyMs,
)
fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    parentId = parentId,
    role = role.name.lowercase(),
    content = content,
    reasoning = reasoning,
    toolCallsJson = encodeList(toolCalls),
    toolCallId = toolCallId,
    attachmentsJson = encodeList(attachments),
    citationsJson = encodeList(citations),
    modelId = modelId,
    provider = provider,
    status = status.name.lowercase(),
    error = error,
    finishReason = finishReason,
    generationId = generationId,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    cachedTokens = cachedTokens,
    reasoningTokens = reasoningTokens,
    costUsd = costUsd,
    createdAt = createdAt,
    latencyMs = latencyMs,
)
private inline fun <reified T> decodeList(text: String?): List<T> {
    if (text.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<T>>(text) }.getOrElse { emptyList() }
}
private inline fun <reified T> encodeList(list: List<T>): String? =
    if (list.isEmpty()) null else json.encodeToString(list)
