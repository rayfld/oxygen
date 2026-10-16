package app.oxygen.features.chat
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
class TtsController(context: Context) {
    private var ready = false
    private var pending: String? = null
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
                pending?.let { text ->
                    pending = null
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oxygen")
                }
            }
        }
    }
    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oxygen")
    }
    fun stop() {
        pending = null
        runCatching { tts?.stop() }
    }
    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
