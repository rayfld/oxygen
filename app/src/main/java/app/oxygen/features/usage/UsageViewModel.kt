package app.oxygen.features.usage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.oxygen.OxygenApp
import app.oxygen.core.db.DayAggregate
import app.oxygen.core.db.ModelAggregate
import app.oxygen.core.model.ChatModel
import app.oxygen.data.UsageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
class UsageViewModel : ViewModel() {
    private val graph = OxygenApp.graph
    data class UiState(
        val summary: UsageSummary? = null,
        val byModel: List<ModelAggregate> = emptyList(),
        val daily: List<DayAggregate> = emptyList(),
        val loaded: Boolean = false,
    )
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val keyInfo = graph.account.keyInfo
    val credits = graph.account.credits
    val refreshing = graph.account.refreshing
    init {
        refresh()
    }
    fun refresh() {
        viewModelScope.launch {
            val summary = graph.usage.summary()
            val byModel = graph.usage.byModel()
            val daily = graph.usage.daily(30)
            _uiState.update { it.copy(summary = summary, byModel = byModel, daily = daily, loaded = true) }
            graph.account.refresh()
        }
    }
    fun modelById(id: String): ChatModel = graph.models.byId(id)
}
