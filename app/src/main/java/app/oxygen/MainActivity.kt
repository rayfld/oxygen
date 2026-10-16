package app.oxygen
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import app.oxygen.core.network.friendlyMessage
import app.oxygen.navigation.OxygenNav
import app.oxygen.navigation.PendingNav
import app.oxygen.navigation.Routes
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxygenTheme
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            OxygenAppContent()
        }
        maybeRequestNotifications()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    @Composable
    private fun OxygenAppContent() {
        val settings by OxygenApp.graph.settings.state.collectAsStateWithLifecycle()
        OxygenTheme(
            themeMode = settings.themeMode,
            oled = settings.oled,
            accentId = settings.accentId,
            fontScale = settings.chatFontScale,
        ) {
            val navController = rememberNavController()
            LaunchedEffect(Unit) {
                PendingNav.conversationId.collect { conversationId ->
                    if (conversationId != null) {
                        PendingNav.conversationId.value = null
                        navController.navigate(Routes.chat(conversationId))
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Ox.colors.background)) {
                OxygenNav(navController)
            }
        }
    }
    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    PendingNav.sharedText.value = text
                }
            }
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
                if (uri != null) {
                    PendingNav.sharedImage.value = uri
                }
            }
            intent.data?.let { it.scheme == "oxygen" && it.host == "auth" } == true -> {
                val code = intent.data?.getQueryParameter("code")
                if (code.isNullOrBlank()) {
                    PendingNav.oauthResult.value = "Sign-in was cancelled"
                    return
                }
                lifecycleScope.launch {
                    val verifier = OxygenApp.graph.secureStore.get("oauth_verifier")
                    OxygenApp.graph.secureStore.remove("oauth_verifier")
                    if (verifier == null) {
                        PendingNav.oauthResult.value = "Sign-in expired, try again"
                        return@launch
                    }
                    runCatching {
                        OxygenApp.graph.client.exchangeOAuthCode(code, verifier)
                    }
                        .onSuccess { key ->
                            OxygenApp.graph.settings.addKey("OpenRouter", key)
                            OxygenApp.graph.account.refresh()
                            PendingNav.oauthResult.value = "Signed in with OpenRouter"
                        }
                        .onFailure { error ->
                            PendingNav.oauthResult.value = error.friendlyMessage()
                        }
                }
            }
            intent.hasExtra(EXTRA_CONVERSATION_ID) -> {
                intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { conversationId ->
                    PendingNav.conversationId.value = conversationId
                }
            }
        }
    }
    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
