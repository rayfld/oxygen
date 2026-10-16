package app.oxygen.navigation
import kotlinx.coroutines.flow.MutableStateFlow
object PendingNav {
    val conversationId = MutableStateFlow<String?>(null)
    val sharedText = MutableStateFlow<String?>(null)
    val sharedImage = MutableStateFlow<android.net.Uri?>(null)
    val oauthResult = MutableStateFlow<String?>(null)
}
