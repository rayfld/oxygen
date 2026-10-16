package app.oxygen.core.network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class SseParserTest {
    private fun drain(parser: SseParser, lines: List<String>): List<String> {
        val out = ArrayList<String>()
        for (line in lines) {
            when (val event = parser.feed(line)) {
                is SseParser.Out.Message -> out += event.data
                SseParser.Out.Comment -> out += "COMMENT"
                null -> Unit
            }
        }
        return out
    }
    @Test
    fun `single data line dispatches on blank line`() {
        val parser = SseParser()
        val events = drain(parser, listOf("data: {\"a\":1}", ""))
        assertEquals(listOf("{\"a\":1}"), events)
    }
    @Test
    fun `comment keepalive is surfaced and ignored for data`() {
        val parser = SseParser()
        val events = drain(parser, listOf(": OPENROUTER PROCESSING", "", "data: hello", ""))
        assertEquals(listOf("COMMENT", "hello"), events)
    }
    @Test
    fun `blank line without data is not an event`() {
        val parser = SseParser()
        val events = drain(parser, listOf("", "", ": ping", ""))
        assertEquals(listOf("COMMENT"), events)
    }
    @Test
    fun `multiple data lines join with newline`() {
        val parser = SseParser()
        val events = drain(parser, listOf("data: line1", "data: line2", ""))
        assertEquals(listOf("line1\nline2"), events)
    }
    @Test
    fun `done marker arrives as data`() {
        val parser = SseParser()
        val events = drain(parser, listOf("data: [DONE]", ""))
        assertEquals(listOf("[DONE]"), events)
    }
    @Test
    fun `json split across chunks is reassembled by the caller`() {
        val parser = SseParser()
        assertNull(parser.feed("data: {\"partial\""))
        val events = drain(parser, listOf(""))
        assertEquals(listOf("{\"partial\""), events)
    }
    @Test
    fun `data without colon consumes whole line`() {
        val parser = SseParser()
        val events = drain(parser, listOf("data", ""))
        assertEquals(listOf(""), events)
    }
    @Test
    fun `unknown fields are ignored`() {
        val parser = SseParser()
        val events = drain(parser, listOf("event: message", "id: 42", "retry: 100", "data: payload", ""))
        assertTrue(events.contains("payload"))
    }
}
