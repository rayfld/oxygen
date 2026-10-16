package app.oxygen.core.util
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}
fun parseJsonObjectOrNull(text: String?): JsonObject? {
    if (text.isNullOrBlank()) return null
    return try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
fun parseJsonElementOrNull(text: String?): JsonElement? {
    if (text.isNullOrBlank()) return null
    return try {
        json.parseToJsonElement(text)
    } catch (_: Exception) {
        null
    }
}
fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNullSafe()
private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content
fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull
fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonObject?.str(key: String): String? = this?.get(key).str()
fun JsonObject?.int(key: String): Int? = this?.get(key).int()
fun JsonObject?.dbl(key: String): Double? = this?.get(key).dbl()
fun JsonObject?.bool(key: String): Boolean? = this?.get(key).bool()
fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).obj()
fun JsonObject?.arr(key: String): JsonArray? = this?.get(key).arr()
fun JsonObject.pretty(): String = try {
    json.encodeToString(JsonElement.serializer(), this)
} catch (_: Exception) {
    toString()
}
fun prettyJsonOrOriginal(text: String?): String {
    val el = parseJsonElementOrNull(text) ?: return text ?: ""
    return try {
        json.encodeToString(JsonElement.serializer(), el)
    } catch (_: Exception) {
        text ?: ""
    }
}
fun JsonObject.deepMerge(overlay: JsonObject): JsonObject {
    val result = LinkedHashMap<String, JsonElement>(this)
    for ((key, value) in overlay) {
        val existing = result[key]
        result[key] = if (existing is JsonObject && value is JsonObject) {
            existing.deepMerge(value)
        } else {
            value
        }
    }
    return JsonObject(result)
}
fun buildObj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
fun JsonObject.withoutNulls(): JsonObject =
    JsonObject(entries.filter { it.value !is JsonNull }.associate { it.key to it.value })
fun JsonObject.putIfNotNull(key: String, value: String?): JsonObject =
    if (value == null) this else JsonObject(this + (key to JsonPrimitive(value)))
fun JsonObject.putIfNotNull(key: String, value: Double?): JsonObject =
    if (value == null) this else JsonObject(this + (key to JsonPrimitive(value)))
fun JsonObject.putIfNotNull(key: String, value: Int?): JsonObject =
    if (value == null) this else JsonObject(this + (key to JsonPrimitive(value)))
