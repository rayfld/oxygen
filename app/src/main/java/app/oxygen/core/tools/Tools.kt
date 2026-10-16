package app.oxygen.core.tools
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
object Tools {
    const val EXEC_LOCAL = "local"
    const val EXEC_SERVER = "server"
    const val WEB_SEARCH_ID = "web_search"
    data class Spec(
        val id: String,
        val label: String,
        val description: String,
        val parameters: JsonObject,
        val local: Boolean,
        val serverId: String? = null,
        val note: String? = null,
    ) {
        val supportsLocal: Boolean get() = local
        val supportsServer: Boolean get() = serverId != null
    }
    private fun schema(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject(build)
    private val emptyParams = schema { put("type", "object") }
    private fun stringParam(name: String, description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }
    val ALL: List<Spec> = listOf(
        Spec(
            id = "datetime",
            label = "Date and time",
            description = "Current local date and time.",
            parameters = emptyParams,
            local = true,
            serverId = "openrouter:datetime",
        ),
        Spec(
            id = "web_search",
            label = "Web search",
            description = "Search the web for current information.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("query", stringParam("query", "Search query")) })
            },
            local = false,
            serverId = "openrouter:web_search",
        ),
        Spec(
            id = "web_fetch",
            label = "Fetch URL",
            description = "Read a web page's text.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("url", stringParam("url", "Page URL")) })
            },
            local = false,
            serverId = "openrouter:web_fetch",
        ),
        Spec(
            id = "image_generation",
            label = "Image generation",
            description = "Generate an image from a prompt.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("prompt", stringParam("prompt", "Image description")) })
            },
            local = false,
            serverId = "openrouter:image_generation",
        ),
        Spec(
            id = "fusion",
            label = "Fusion panel",
            description = "Ask a panel of models, then synthesize.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("prompt", stringParam("prompt", "Question for the panel")) })
            },
            local = false,
            serverId = "openrouter:fusion",
        ),
        Spec(
            id = "advisor",
            label = "Advisor",
            description = "Consult a stronger model for guidance.",
            parameters = emptyParams,
            local = false,
            serverId = "openrouter:advisor",
        ),
        Spec(
            id = "subagent",
            label = "Subagent",
            description = "Delegate a small task to a faster model.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("task", stringParam("task", "Task to delegate")) })
            },
            local = false,
            serverId = "openrouter:subagent",
        ),
        Spec(
            id = "search_models",
            label = "Search models",
            description = "Search the OpenRouter model catalog.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("query", stringParam("query", "What to look for")) })
            },
            local = false,
            serverId = "openrouter:experimental__search_models",
        ),
        Spec(
            id = "shell",
            label = "Sandbox shell",
            description = "Run commands in OpenRouter's hosted sandbox.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("command", stringParam("command", "Command to run")) })
            },
            local = false,
            serverId = "openrouter:shell",
            note = "Responses and Messages APIs only",
        ),
        Spec(
            id = "bash",
            label = "Bash (Anthropic)",
            description = "Anthropic-style bash tool with sandboxed execution.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("command", stringParam("command", "Command to run")) })
            },
            local = false,
            serverId = "openrouter:bash",
            note = "Messages API only",
        ),
        Spec(
            id = "calculator",
            label = "Calculator",
            description = "Evaluate a math expression: + - * / ^ %, parentheses, sqrt, sin, cos, tan, log, ln, abs, round, floor, ceil, min, max, pi, e.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("expression", stringParam("expression", "Expression to evaluate")) })
                put("required", JsonArray(listOf(JsonPrimitive("expression"))))
            },
            local = true,
        ),
        Spec(
            id = "random",
            label = "Random",
            description = "Random number, coin flip, dice roll, or pick from a list.",
            parameters = schema {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "kind",
                            buildJsonObject {
                                put("type", "string")
                                put("enum", JsonArray(listOf(JsonPrimitive("number"), JsonPrimitive("coin"), JsonPrimitive("dice"), JsonPrimitive("pick"))))
                            },
                        )
                        put("min", buildJsonObject { put("type", "number") })
                        put("max", buildJsonObject { put("type", "number") })
                        put("sides", buildJsonObject { put("type", "integer") })
                        put(
                            "options",
                            buildJsonObject {
                                put("type", "array")
                                put("items", buildJsonObject { put("type", "string") })
                            },
                        )
                    },
                )
            },
            local = true,
        ),
        Spec(
            id = "uuid",
            label = "UUID",
            description = "Generate a random UUID v4.",
            parameters = emptyParams,
            local = true,
        ),
        Spec(
            id = "base64",
            label = "Base64",
            description = "Encode or decode base64 text.",
            parameters = schema {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "mode",
                            buildJsonObject {
                                put("type", "string")
                                put("enum", JsonArray(listOf(JsonPrimitive("encode"), JsonPrimitive("decode"))))
                            },
                        )
                        put("text", stringParam("text", "Text to encode or decode"))
                    },
                )
            },
            local = true,
        ),
        Spec(
            id = "hash",
            label = "Hash",
            description = "Hash text with SHA-256 or MD5.",
            parameters = schema {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "algorithm",
                            buildJsonObject {
                                put("type", "string")
                                put("enum", JsonArray(listOf(JsonPrimitive("sha256"), JsonPrimitive("md5"))))
                            },
                        )
                        put("text", stringParam("text", "Text to hash"))
                    },
                )
            },
            local = true,
        ),
        Spec(
            id = "convert",
            label = "Unit conversion",
            description = "Convert length (m, km, cm, mm, mi, yd, ft, in), mass (kg, g, mg, lb, oz), temperature (c, f, k), or data (b, kb, mb, gb, tb).",
            parameters = schema {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("value", buildJsonObject { put("type", "number") })
                        put("from", stringParam("from", "Source unit"))
                        put("to", stringParam("to", "Target unit"))
                    },
                )
                put("required", JsonArray(listOf(JsonPrimitive("value"), JsonPrimitive("from"), JsonPrimitive("to"))))
            },
            local = true,
        ),
        Spec(
            id = "text_stats",
            label = "Text stats",
            description = "Count characters, words, lines, and sentences.",
            parameters = schema {
                put("type", "object")
                put("properties", buildJsonObject { put("text", stringParam("text", "Text to analyze")) })
            },
            local = true,
        ),
        Spec(
            id = "days_between",
            label = "Days between",
            description = "Days between two dates in YYYY-MM-DD form.",
            parameters = schema {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("start", stringParam("start", "Start date"))
                        put("end", stringParam("end", "End date"))
                    },
                )
                put("required", JsonArray(listOf(JsonPrimitive("start"), JsonPrimitive("end"))))
            },
            local = true,
        ),
    )
    fun byId(id: String): Spec? = ALL.firstOrNull { it.id == id }
}
