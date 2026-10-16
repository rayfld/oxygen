package app.oxygen.features.settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ChatParams
import app.oxygen.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class SettingsViewModel : ViewModel() {
    private val graph = OxygenApp.graph
    val settings: StateFlow<AppSettings> = graph.settings.state
    val keyInfo = graph.account.keyInfo
    val credits = graph.account.credits
    val refreshing = graph.account.refreshing
    private val _catalog = MutableStateFlow<List<ChatModel>>(emptyList())
    val catalog: StateFlow<List<ChatModel>> = _catalog.asStateFlow()
    init {
        reload()
    }
    fun reload() {
        viewModelScope.launch {
            graph.account.refresh()
            graph.models.ensureLoaded(graph.settings.activeKeySecret())
            _catalog.value = graph.models.models
        }
    }
    fun addKey(label: String, secret: String) {
        viewModelScope.launch {
            graph.settings.addKey(label, secret)
            graph.account.refresh()
        }
    }
    fun removeKey(id: String) {
        viewModelScope.launch { graph.settings.removeKey(id) }
    }
    fun setActiveKey(id: String) {
        viewModelScope.launch {
            graph.settings.setActiveKey(id)
            graph.account.refresh()
        }
    }
    fun setTheme(mode: String) {
        viewModelScope.launch { graph.settings.setThemeMode(mode) }
    }
    fun setOled(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setOled(enabled) }
    }
    fun setAccent(id: String) {
        viewModelScope.launch { graph.settings.setAccent(id) }
    }
    fun addMemory(text: String) {
        viewModelScope.launch { graph.settings.addMemory(text) }
    }
    fun removeMemory(text: String) {
        viewModelScope.launch { graph.settings.removeMemory(text) }
    }
    fun setMessageStyle(style: String) {
        viewModelScope.launch { graph.settings.setMessageStyle(style) }
    }
    fun setChatFontScale(scale: Float) {
        viewModelScope.launch { graph.settings.setChatFontScale(scale) }
    }
    fun setDefaultModel(modelId: String) {
        viewModelScope.launch { graph.settings.setDefaultModel(modelId) }
    }
    fun setTitleModel(modelId: String) {
        viewModelScope.launch { graph.settings.setTitleModel(modelId) }
    }
    fun setDefaultParams(params: ChatParams) {
        viewModelScope.launch { graph.settings.setDefaultParams(params) }
    }
    fun setDefaultSystemPrompt(prompt: String) {
        viewModelScope.launch { graph.settings.setDefaultSystemPrompt(prompt) }
    }
    fun setShowUsageFooter(enabled: Boolean) {
        viewModelScope.launch { graph.settings.setShowUsageFooter(enabled) }
    }
}
