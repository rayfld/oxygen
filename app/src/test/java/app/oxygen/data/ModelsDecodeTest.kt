package app.oxygen.data
import app.oxygen.core.network.ModelsEnvelopeDto
import app.oxygen.core.util.json
import org.junit.Assert.assertTrue
import org.junit.Test
class ModelsDecodeTest {
    private fun sample(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("models_sample.json")) {
            "models_sample.json missing from test resources"
        }.bufferedReader().readText()
    @Test
    fun `typed dto decode succeeds on the real payload`() {
        val text = sample()
        val dtos = json.decodeFromString(ModelsEnvelopeDto.serializer(), text).data
        assertTrue("expected hundreds of models", dtos.size > 300)
    }
    @Test
    fun `typed mapping produces usable models`() {
        val text = sample()
        val dtos = json.decodeFromString(ModelsEnvelopeDto.serializer(), text).data
        val mapped = dtos.mapNotNull { it.toDomain() }
        assertTrue("expected hundreds of mapped models", mapped.size > 300)
        assertTrue("ids should be non blank", mapped.all { it.id.isNotBlank() })
        assertTrue("some models should carry prices", mapped.any { it.promptPrice != null })
        assertTrue("some models should support tools", mapped.any { it.hasTools })
    }
    @Test
    fun `lenient decode path also yields models`() {
        val models = decodeModels(sample())
        assertTrue("expected hundreds of models", models.size > 300)
        assertTrue(models.any { it.promptPrice != null })
        assertTrue(models.any { it.contextLength != null })
    }
}
