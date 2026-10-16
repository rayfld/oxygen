package app.oxygen.core.network
class SseParser {
    sealed interface Out {
        data class Message(val data: String) : Out
        data object Comment : Out
    }
    private val data = StringBuilder()
    private var sawData = false
    fun feed(line: String): Out? {
        if (line.isEmpty()) {
            if (!sawData) return null
            val message = data.toString()
            data.setLength(0)
            sawData = false
            return Out.Message(message)
        }
        if (line.startsWith(":")) return Out.Comment
        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        val value = if (colon == -1) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "data" -> {
                if (sawData) data.append('\n')
                data.append(value)
                sawData = true
            }
            else -> Unit
        }
        return null
    }
    fun reset() {
        data.setLength(0)
        sawData = false
    }
}
