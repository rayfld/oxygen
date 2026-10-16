package app.oxygen.features.presets
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.data.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class PresetsViewModel : ViewModel() {
    private val graph = OxygenApp.graph
    val presets: StateFlow<List<Preset>> = graph.presets.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _catalog = MutableStateFlow<List<ChatModel>>(emptyList())
    val catalog: StateFlow<List<ChatModel>> = _catalog.asStateFlow()
    init {
        viewModelScope.launch {
            graph.models.ensureLoaded(graph.settings.activeKeySecret())
            _catalog.value = graph.models.models
        }
    }
    fun save(preset: Preset) {
        viewModelScope.launch { graph.presets.save(preset) }
    }
    fun delete(id: String) {
        viewModelScope.launch { graph.presets.delete(id) }
    }
    suspend fun startChat(preset: Preset): String {
        val settings = graph.settings.current()
        return graph.chat.createConversation(
            modelId = preset.modelId ?: settings.defaultModelId,
            systemPrompt = preset.systemPrompt?.takeIf { it.isNotBlank() }
                ?: settings.defaultSystemPrompt.takeIf { it.isNotBlank() },
            params = preset.params,
        ).id
    }
}
