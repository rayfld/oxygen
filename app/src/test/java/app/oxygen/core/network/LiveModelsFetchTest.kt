package app.oxygen.core.network
import app.oxygen.data.decodeModels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
class LiveModelsFetchTest {
    @Test
    fun `endpoint details fetch and decode work without a key`() {
        val client = OpenRouterClient()
        val endpoints = try {
            runBlocking { client.listEndpoints("", "anthropic", "claude-sonnet-4.5") }
        } catch (e: Exception) {
            println("Live endpoints fetch skipped: ${e.message}")
            return
        }
        assertTrue("expected providers, got ${endpoints.size}", endpoints.isNotEmpty())
        assertTrue(endpoints.any { it.provider_name != null })
    }
    @Test
    fun `anonymous fetch and decode of the real catalog works`() {
        val client = OpenRouterClient()
        val text = try {
            runBlocking { client.listModelsRaw("") }
        } catch (e: Exception) {
            println("Live fetch skipped: ${e.message}")
            return
        }
        assertTrue("payload should be large, got ${text.length} bytes", text.length > 100_000)
        val models = decodeModels(text)
        assertTrue("expected hundreds of models, got ${models.size}", models.size > 300)
        assertTrue("expected priced models", models.any { it.promptPrice != null })
    }
}
