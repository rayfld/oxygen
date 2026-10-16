package app.oxygen.data
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.oxygen.core.model.ChatParams
import app.oxygen.core.security.SecureStore
import app.oxygen.core.util.json
import app.oxygen.core.util.newId
import app.oxygen.core.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oxygen_settings")
@Serializable
data class ApiKeyMeta(
    val id: String,
    val label: String,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
)
@Serializable
data class AppSettings(
    val themeMode: String = "system",
    val oled: Boolean = false,
    val accentId: String = "neutral",
    val keys: List<ApiKeyMeta> = emptyList(),
    val activeKeyId: String? = null,
    val defaultModelId: String = "openrouter/auto",
    val titleModelId: String = "",
    val defaultParams: ChatParams = ChatParams(),
    val defaultSystemPrompt: String = "",
    val messageStyle: String = "bubble",
    val showUsageFooter: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val lastModelIds: List<String> = emptyList(),
    val favoriteModelIds: List<String> = emptyList(),
    val memories: List<String> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val chatFontScale: Float = 1f,
)
class SettingsRepository(
    private val context: Context,
    private val secure: SecureStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val state: StateFlow<AppSettings> = context.dataStore.data
        .map { prefs -> decode(prefs[SETTINGS_KEY]) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())
    suspend fun current(): AppSettings = state.value
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[SETTINGS_KEY])
            prefs[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }
    fun secretFor(id: String): String? = secure.get("key_$id")
    suspend fun activeKeySecret(): String? {
        val settings = state.value
        val id = settings.activeKeyId ?: settings.keys.firstOrNull()?.id ?: return null
        return secretFor(id)
    }
    suspend fun addKey(label: String, secret: String): String {
        val id = newId()
        secure.put("key_$id", secret.trim())
        update { settings ->
            settings.copy(
                keys = settings.keys + ApiKeyMeta(id = id, label = label.trim().ifBlank { "OpenRouter key" }, createdAt = now()),
                activeKeyId = settings.activeKeyId ?: id,
            )
        }
        return id
    }
    suspend fun removeKey(id: String) {
        secure.remove("key_$id")
        update { settings ->
            val remaining = settings.keys.filterNot { it.id == id }
            settings.copy(
                keys = remaining,
                activeKeyId = when {
                    settings.activeKeyId != id -> settings.activeKeyId
                    else -> remaining.firstOrNull()?.id
                },
            )
        }
    }
    suspend fun setActiveKey(id: String) {
        update { it.copy(activeKeyId = id) }
        context.dataStore.edit { prefs ->
            val current = decode(prefs[SETTINGS_KEY])
            prefs[SETTINGS_KEY] = json.encodeToString(
                AppSettings.serializer(),
                current.copy(keys = current.keys.map { if (it.id == id) it.copy(lastUsedAt = now()) else it }),
            )
        }
    }
    suspend fun setThemeMode(mode: String) = update { it.copy(themeMode = mode) }
    suspend fun setOled(enabled: Boolean) = update { it.copy(oled = enabled) }
    suspend fun setAccent(id: String) = update { it.copy(accentId = id) }
    suspend fun addMemory(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        update { it.copy(memories = (it.memories + trimmed).distinct()) }
    }
    suspend fun removeMemory(text: String) = update { it.copy(memories = it.memories - text) }
    suspend fun setChatFontScale(scale: Float) = update { it.copy(chatFontScale = scale) }
    suspend fun setDraft(conversationId: String, text: String) {
        update { settings ->
            val drafts = settings.drafts.toMutableMap()
            if (text.isBlank()) drafts.remove(conversationId) else drafts[conversationId] = text
            settings.copy(drafts = drafts)
        }
    }
    suspend fun setDefaultModel(modelId: String) = update { it.copy(defaultModelId = modelId) }
    suspend fun setTitleModel(modelId: String) = update { it.copy(titleModelId = modelId) }
    suspend fun setDefaultParams(params: ChatParams) = update { it.copy(defaultParams = params) }
    suspend fun setDefaultSystemPrompt(prompt: String) = update { it.copy(defaultSystemPrompt = prompt) }
    suspend fun setMessageStyle(style: String) = update { it.copy(messageStyle = style) }
    suspend fun setShowUsageFooter(enabled: Boolean) = update { it.copy(showUsageFooter = enabled) }
    suspend fun setHaptics(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }
    suspend fun toggleFavoriteModel(modelId: String) = update { settings ->
        settings.copy(
            favoriteModelIds = if (modelId in settings.favoriteModelIds) {
                settings.favoriteModelIds - modelId
            } else {
                settings.favoriteModelIds + modelId
            },
        )
    }
    suspend fun markModelUsed(modelId: String) = update { settings ->
        settings.copy(lastModelIds = (listOf(modelId) + settings.lastModelIds.filterNot { it == modelId }).take(8))
    }
    private fun decode(raw: String?): AppSettings =
        if (raw.isNullOrBlank()) AppSettings() else runCatching {
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrElse { AppSettings() }
    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("settings_json")
    }
}
