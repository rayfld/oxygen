package app.oxygen.features.conversations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.Conversation
import app.oxygen.data.SearchHit
import app.oxygen.data.toDomain
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class ConversationsViewModel : ViewModel() {
    private val graph = OxygenApp.graph
    data class Item(
        val conversation: Conversation,
        val snippet: String?,
        val model: ChatModel?,
    )
    data class SearchItem(
        val conversationId: String,
        val title: String,
        val snippet: String,
    )
    data class UiState(
        val items: List<Item> = emptyList(),
        val loading: Boolean = true,
    )
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _searchResults = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchResults: StateFlow<List<SearchItem>> = _searchResults.asStateFlow()
    val uiState: StateFlow<UiState> = combine(
        graph.chat.observeConversations(),
        graph.chat.observeSnippets(),
        graph.settings.state,
    ) { conversations, snippets, _ ->
        val snippetByConversation = snippets.associateBy { it.conversationId }
        UiState(
            items = conversations.map { entity ->
                val conversation = entity.toDomain()
                Item(
                    conversation = conversation,
                    snippet = snippetByConversation[entity.id]?.content?.replace('\n', ' ')?.take(140),
                    model = graph.models.byId(conversation.modelId),
                )
            },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
    init {
        viewModelScope.launch {
            graph.models.ensureLoaded(graph.settings.activeKeySecret())
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _query.debounce(220).collect { query ->
                if (query.isBlank()) {
                    _searchResults.value = emptyList()
                    return@collect
                }
                val titles = graph.chat.observeConversations().first().associate { it.id to it.title }
                _searchResults.value = graph.chat.search(query).map { hit: SearchHit ->
                    SearchItem(
                        conversationId = hit.conversationId,
                        title = titles[hit.conversationId] ?: "Chat",
                        snippet = hit.snippet,
                    )
                }
            }
        }
    }
    fun setQuery(value: String) {
        _query.value = value
    }
    suspend fun createChat(ephemeral: Boolean = false): String {
        val settings = graph.settings.current()
        return graph.chat.createConversation(
            modelId = settings.defaultModelId,
            systemPrompt = settings.defaultSystemPrompt.takeIf { it.isNotBlank() },
            params = settings.defaultParams,
            ephemeral = ephemeral,
        ).id
    }
    fun delete(conversationId: String) {
        viewModelScope.launch {
            graph.generationManager.stop(conversationId)
            graph.chat.deleteConversation(conversationId)
        }
    }
    fun rename(conversationId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { graph.chat.setTitle(conversationId, title.trim(), auto = false) }
    }
    fun togglePinned(conversationId: String, pinned: Boolean) {
        viewModelScope.launch { graph.chat.setPinned(conversationId, pinned) }
    }
}
