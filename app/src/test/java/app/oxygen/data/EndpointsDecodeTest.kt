package app.oxygen.data
import app.oxygen.core.network.EndpointsEnvelopeDto
import app.oxygen.core.util.json
import org.junit.Assert.assertTrue
import org.junit.Test
class EndpointsDecodeTest {
    private fun sample(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("endpoints_sample.json")) {
            "endpoints_sample.json missing from test resources"
        }.bufferedReader().readText()
    @Test
    fun `typed dto decode succeeds on the real endpoints payload`() {
        val text = sample()
        val dtos = json.decodeFromString(EndpointsEnvelopeDto.serializer(), text).data?.endpoints
        assertTrue("expected providers", dtos != null && dtos.isNotEmpty())
    }
    @Test
    fun `lenient decode yields providers with prices`() {
        val endpoints = decodeEndpoints(sample())
        assertTrue("expected providers, got ${endpoints.size}", endpoints.isNotEmpty())
        assertTrue("expected priced providers", endpoints.any { it.promptPrice != null })
        assertTrue("expected named providers", endpoints.all { it.providerName.isNotBlank() })
    }
}
