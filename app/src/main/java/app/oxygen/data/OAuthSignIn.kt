package app.oxygen.data
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.oxygen.core.network.ApiException
import app.oxygen.core.network.LoopbackOAuthServer
import app.oxygen.core.network.OAuthPkce
import app.oxygen.core.network.OpenRouterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class OAuthSignIn(
    private val context: Context,
    private val client: OpenRouterClient,
    private val settings: SettingsRepository,
    private val account: AccountRepository,
) {
    private var server: LoopbackOAuthServer? = null
    private var verifier: String? = null
    fun start(): Boolean = runCatching {
        cancel()
        val loopback = LoopbackOAuthServer()
        val port = loopback.start()
        val codeVerifier = OAuthPkce.newVerifier()
        val state = OAuthPkce.newState()
        server = loopback
        verifier = codeVerifier
        val callback = "http://localhost:$port/callback"
        val url = OAuthPkce.authUrl(
            challenge = OAuthPkce.challenge(codeVerifier),
            state = state,
            callbackUrl = callback,
        )
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
        true
    }.getOrElse { false }
    suspend fun await(): String {
        val loopback = server ?: throw ApiException("Sign-in was not started", 0)
        val outcome = withContext(Dispatchers.IO) { loopback.await() }
        server = null
        val code = outcome.code ?: run {
            verifier = null
            throw ApiException(outcome.error ?: "Sign-in was cancelled", 0)
        }
        val codeVerifier = verifier ?: throw ApiException("Sign-in expired, try again", 403)
        verifier = null
        val key = client.exchangeOAuthCode(code, codeVerifier)
        settings.addKey("OpenRouter", key)
        runCatching { account.refresh() }
        return key
    }
    fun cancel() {
        server?.close()
        server = null
        verifier = null
    }
}
