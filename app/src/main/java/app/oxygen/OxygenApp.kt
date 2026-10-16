package app.oxygen
import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.oxygen.core.db.OxygenDatabase
import app.oxygen.core.network.OpenRouterClient
import app.oxygen.core.security.SecureStore
import app.oxygen.data.AccountRepository
import app.oxygen.data.AttachmentStore
import app.oxygen.data.ChatRepository
import app.oxygen.data.GenerationManager
import app.oxygen.data.Foreground
import app.oxygen.data.ModelsRepository
import app.oxygen.data.OAuthSignIn
import app.oxygen.data.PresetsRepository
import app.oxygen.data.SettingsRepository
import app.oxygen.data.UsageRepository
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
class OxygenApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        app.oxygen.core.util.CrashLog.install(this)
        graph = Graph(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    Foreground.isForeground.value = true
                }
                override fun onStop(owner: LifecycleOwner) {
                    Foreground.isForeground.value = false
                }
            },
        )
        graph.scope.launch {
            graph.generationManager.recoverInterrupted()
        }
    }
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
    companion object {
        lateinit var graph: Graph
            private set
    }
}
class Graph(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: OxygenDatabase by lazy { OxygenDatabase.build(appContext) }
    val client: OpenRouterClient by lazy { OpenRouterClient() }
    val secureStore: SecureStore by lazy { SecureStore(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext, secureStore) }
    val chat: ChatRepository by lazy { ChatRepository(database) }
    val models: ModelsRepository by lazy { ModelsRepository(appContext, client) }
    val usage: UsageRepository by lazy { UsageRepository(database) }
    val presets: PresetsRepository by lazy { PresetsRepository(database) }
    val account: AccountRepository by lazy { AccountRepository(client, settings) }
    val oauthSignIn: OAuthSignIn by lazy { OAuthSignIn(appContext, client, settings, account) }
    val attachments: AttachmentStore by lazy { AttachmentStore(appContext) }
    val generationManager: GenerationManager by lazy {
        GenerationManager(appContext, chat, client, settings, attachments, account, models)
    }
}
