package app.oxygen.data
import app.oxygen.core.model.Credits
import app.oxygen.core.model.KeyInfo
import app.oxygen.core.network.OpenRouterClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
class AccountRepository(
    private val client: OpenRouterClient,
    private val settings: SettingsRepository,
) {
    private val _keyInfo = MutableStateFlow<KeyInfo?>(null)
    val keyInfo: StateFlow<KeyInfo?> = _keyInfo.asStateFlow()
    private val _credits = MutableStateFlow<Credits?>(null)
    val credits: StateFlow<Credits?> = _credits.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    suspend fun refresh() {
        val key = settings.activeKeySecret() ?: run {
            _keyInfo.value = null
            _credits.value = null
            return
        }
        _refreshing.value = true
        try {
            runCatching { client.getKeyInfo(key) }
                .onSuccess { dto ->
                    _keyInfo.value = dto?.let {
                        KeyInfo(
                            label = it.label,
                            usage = it.usage,
                            limit = it.limit,
                            limitRemaining = it.limit_remaining,
                            isFreeTier = it.is_free_tier == true,
                            rateLimitRequests = it.rate_limit?.requests,
                            rateLimitIntervalSeconds = it.rate_limit?.interval,
                        )
                    }
                    _lastError.value = null
                }
                .onFailure { e ->
                    _lastError.value = e.message
                }
            runCatching { client.getCredits(key) }
                .onSuccess { dto ->
                    _credits.value = dto?.let { Credits(it.total_credits, it.total_usage) }
                }
        } finally {
            _refreshing.value = false
        }
    }
    suspend fun validateKey(secret: String): Boolean = runCatching {
        val probe = client.getKeyInfo(secret)
        probe != null
    }.getOrElse { false }
}
