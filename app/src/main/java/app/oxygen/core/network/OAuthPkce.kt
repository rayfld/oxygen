package app.oxygen.core.network
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
object OAuthPkce {
    const val CALLBACK_URL = "oxygen://auth"
    fun newVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    fun newState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    fun authUrl(
        challenge: String,
        state: String,
        callbackUrl: String = CALLBACK_URL,
        keyLabel: String = "Oxygen",
    ): String {
        return buildString {
            append("https://openrouter.ai/auth")
            append("?callback_url=").append(android.net.Uri.encode(callbackUrl))
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&state=").append(state)
            append("&key_label=").append(android.net.Uri.encode(keyLabel))
        }
    }
}
