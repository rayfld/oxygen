package app.oxygen.data
import app.oxygen.core.db.OxygenDatabase
import app.oxygen.core.db.PresetEntity
import app.oxygen.core.model.ChatParams
import app.oxygen.core.util.json
import app.oxygen.core.util.newId
import app.oxygen.core.util.now
import app.oxygen.core.util.parseJsonObjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
@Serializable
data class Preset(
    val id: String,
    val name: String,
    val systemPrompt: String?,
    val modelId: String?,
    val params: ChatParams,
    val createdAt: Long,
    val updatedAt: Long,
)
class PresetsRepository(private val db: OxygenDatabase) {
    private val dao = db.presets()
    fun observeAll(): Flow<List<Preset>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun get(id: String): Preset? = dao.get(id)?.toDomain()
    suspend fun save(preset: Preset): Preset {
        val entity = PresetEntity(
            id = preset.id.ifBlank { newId() },
            name = preset.name,
            systemPrompt = preset.systemPrompt,
            modelId = preset.modelId,
            paramsJson = json.encodeToString(ChatParams.serializer(), preset.params),
            createdAt = preset.createdAt,
            updatedAt = now(),
        )
        dao.upsert(entity)
        return entity.toDomain()
    }
    suspend fun delete(id: String) = dao.delete(id)
}
private fun PresetEntity.toDomain(): Preset = Preset(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    modelId = modelId,
    params = parseJsonObjectOrNull(paramsJson)?.let { obj ->
        runCatching { json.decodeFromJsonElement(ChatParams.serializer(), obj) }.getOrNull()
    } ?: ChatParams(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
